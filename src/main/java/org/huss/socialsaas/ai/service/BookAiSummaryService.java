package org.huss.socialsaas.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.huss.socialsaas.ai.entity.BookAiTag;
import org.huss.socialsaas.ai.repository.BookAiTagRepository;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookAiSummaryService {

    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("\\([^()]*\\)");
    private static final Pattern HANGUL_PATTERN = Pattern.compile("[가-힣]");

    private static final String SYSTEM_PROMPT = """
            You are a Korean literature metadata assistant.
            This website is for international users, so write the book introduction in English only.
            Write a concise English book introduction in 2-4 sentences.
            Use only the metadata provided by the user.
            Do not invent plot details, awards, historical facts, or publication facts that are not clearly inferable from the metadata.
            If metadata is limited, describe the likely literary context conservatively and explicitly avoid overclaiming.
            You may mention original titles or author names in their source language inside parentheses when helpful, but the explanation itself must remain English.
            Never insert standalone Hangul words into otherwise English sentences.
            If you include Korean text, only place it inside parentheses immediately following the relevant romanized or translated title/author name.
            Output plain English text only.
            """;

    private final BookAiTagRepository bookAiTagRepository;

    @Value("${app.ai.openai.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.openai.api-key:}")
    private String apiKey;

    @Value("${app.ai.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${app.ai.openai.model:gpt-4o-mini}")
    private String model;

    @Value("${app.ai.openai.summary-temperature:0.4}")
    private double temperature;

    // Callers (e.g. LiteratureService detail lookup) run in a readOnly
    // transaction; open a fresh writable one so the summary can be saved.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> getOrGenerateSummary(LiteratureWork literatureWork) {
        Optional<BookAiTag> existing = bookAiTagRepository.findByLiteratureWorkId(literatureWork.getId());
        if (existing.isPresent()) {
            String savedSummary = sanitizeSummary(normalize(existing.get().getLlmSummary()));
            if (savedSummary != null && shouldReuseSummary(savedSummary)) {
                if (!savedSummary.equals(existing.get().getLlmSummary())) {
                    existing.get().updateLlmSummary(savedSummary, Instant.now());
                }
                return Optional.of(savedSummary);
            }
        }

        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            String generatedSummary = sanitizeSummary(normalize(requestSummaryFromOpenAi(literatureWork)));
            if (generatedSummary == null) {
                return Optional.empty();
            }

            if (existing.isPresent()) {
                existing.get().updateLlmSummary(generatedSummary, Instant.now());
            } else {
                bookAiTagRepository.save(BookAiTag.create(
                        literatureWork,
                        generatedSummary,
                        null,
                        null,
                        Instant.now()
                ));
            }
            return Optional.of(generatedSummary);
        } catch (Exception exception) {
            log.warn("OpenAI summary generation failed for bookId={}", literatureWork.getId(), exception);
            return Optional.empty();
        }
    }

    private String requestSummaryFromOpenAi(LiteratureWork literatureWork) {
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        ChatCompletionResponse response = client.post()
                .uri("/chat/completions")
                .body(new ChatCompletionRequest(
                        model,
                        List.of(
                                new ChatMessage("system", SYSTEM_PROMPT),
                                new ChatMessage("user", buildUserPrompt(literatureWork))
                        ),
                        temperature
                ))
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }

        ChatMessage message = response.choices().get(0).message();
        if (message == null) {
            return null;
        }
        return message.content();
    }

    private String buildUserPrompt(LiteratureWork literatureWork) {
        return """
                Please write an English book introduction using only the metadata below.

                - Title: %s
                - Original Title: %s
                - Author: %s
                - Author (Korean): %s
                - Translated Language: %s
                - Country: %s
                - Publisher: %s
                - Published Year: %s
                - Genres: %s
                - Existing Description: %s

                Requirements:
                1) Keep it concise in 2-4 sentences.
                2) Do not invent plot, awards, or historical facts that are not directly inferable from the metadata.
                3) Focus on literary context, translation context, and why an international reader might care.
                4) Output plain English text only.
                """.formatted(
                safe(literatureWork.getTitle()),
                safe(literatureWork.getOriginalTitle()),
                safe(literatureWork.getAuthorName()),
                safe(literatureWork.getAuthorNameKorean()),
                safe(literatureWork.getTranslatedLanguage()),
                safe(literatureWork.getCountry()),
                safe(literatureWork.getPublisher()),
                literatureWork.getPublishedYear() == null ? "정보 없음" : literatureWork.getPublishedYear().toString(),
                safe(String.join(", ", literatureWork.getGenreMappingsView().stream()
                        .map(mapping -> mapping.getGenre().getName())
                        .filter(name -> name != null && !name.isBlank())
                        .toList())),
                safe(literatureWork.getDescription())
        );
    }

    private String safe(String value) {
        String normalized = normalize(value);
        return normalized == null ? "Not available" : normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private boolean shouldReuseSummary(String savedSummary) {
        return !containsDisallowedHangul(savedSummary);
    }

    private String sanitizeSummary(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        StringBuilder sanitized = new StringBuilder();
        Matcher matcher = PARENTHESIS_PATTERN.matcher(normalized);
        int lastIndex = 0;
        while (matcher.find()) {
            sanitized.append(removeHangulOutsideParentheses(normalized.substring(lastIndex, matcher.start())));
            sanitized.append(cleanParentheticalChunk(matcher.group()));
            lastIndex = matcher.end();
        }
        sanitized.append(removeHangulOutsideParentheses(normalized.substring(lastIndex)));

        return cleanupWhitespaceAndPunctuation(sanitized.toString());
    }

    private String removeHangulOutsideParentheses(String value) {
        return value.replaceAll("[가-힣]+", " ");
    }

    private String cleanParentheticalChunk(String value) {
        String inner = value.substring(1, value.length() - 1).trim();
        if (inner.isBlank()) {
            return "";
        }
        if (containsHangul(inner)) {
            return "(" + inner + ")";
        }
        return "(" + cleanupWhitespaceAndPunctuation(inner) + ")";
    }

    private String cleanupWhitespaceAndPunctuation(String value) {
        String cleaned = value
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.;:!?])", "$1")
                .replaceAll("([(])\\s+", "$1")
                .replaceAll("\\s+([)])", "$1")
                .replaceAll("([,.;:!?]){2,}", "$1")
                .trim();

        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean containsDisallowedHangul(String value) {
        if (value == null) {
            return false;
        }

        String withoutParentheses = PARENTHESIS_PATTERN.matcher(value).replaceAll(" ");
        return containsHangul(withoutParentheses);
    }

    private boolean containsHangul(String value) {
        return value != null && HANGUL_PATTERN.matcher(value).find();
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            double temperature
    ) {
    }

    private record ChatMessage(
            String role,
            String content
    ) {
    }

    private record ChatCompletionResponse(
            List<Choice> choices
    ) {
        private record Choice(
                ChatMessage message
        ) {
        }
    }
}

