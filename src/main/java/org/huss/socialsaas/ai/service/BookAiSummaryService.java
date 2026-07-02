package org.huss.socialsaas.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.huss.socialsaas.ai.entity.BookAiTag;
import org.huss.socialsaas.ai.repository.BookAiTagRepository;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookAiSummaryService {

    private static final String SYSTEM_PROMPT = """
            You are a Korean literature metadata assistant.
            Write a concise Korean book introduction in 2-4 sentences.
            Use only the metadata provided by the user.
            Do not invent plot details, awards, historical facts, or publication facts that are not clearly inferable from the metadata.
            If metadata is limited, describe the likely literary context conservatively and explicitly avoid overclaiming.
            Output plain Korean text only.
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

    @Transactional
    public Optional<String> getOrGenerateSummary(LiteratureWork literatureWork) {
        Optional<BookAiTag> existing = bookAiTagRepository.findByLiteratureWorkId(literatureWork.getId());
        if (existing.isPresent()) {
            String savedSummary = normalize(existing.get().getLlmSummary());
            if (savedSummary != null) {
                return Optional.of(savedSummary);
            }
        }

        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            String generatedSummary = normalize(requestSummaryFromOpenAi(literatureWork));
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
                아래 도서 메타데이터만 참고해서 한국어 책 소개를 작성해주세요.
                
                - 제목: %s
                - 원제: %s
                - 저자: %s
                - 저자(한글): %s
                - 번역 언어: %s
                - 국가: %s
                - 출판사: %s
                - 출간 연도: %s
                - 장르: %s
                - 기존 설명: %s
                
                요구사항:
                1) 2~4문장으로 간결하게 작성
                2) 메타데이터에서 직접 알 수 없는 줄거리/수상/시대적 사실은 단정하지 말 것
                3) 작품 성격, 번역/문학적 맥락, 독자 포인트 위주로 소개
                4) 출력은 순수 한국어 문장만 반환
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
        return normalized == null ? "정보 없음" : normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
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

