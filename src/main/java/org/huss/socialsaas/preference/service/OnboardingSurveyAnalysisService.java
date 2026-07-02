package org.huss.socialsaas.preference.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.huss.socialsaas.literature.entity.Genre;
import org.huss.socialsaas.literature.repository.GenreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnboardingSurveyAnalysisService {

    private static final int MAX_GENRE_CODES = 5;
    private static final Pattern GENRE_CODE_PATTERN = Pattern.compile("\"genreCodes\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern QUOTED_VALUE_PATTERN = Pattern.compile("\"(.*?)\"");

    private static final String SYSTEM_PROMPT = """
            You analyze onboarding survey answers for an international literature platform.
            The goal is to infer which existing genre codes best fit the user's emotional and narrative preferences.
            Return only JSON in this exact shape:
            {"genreCodes":["CODE1","CODE2","CODE3"]}
            Rules:
            - Use only genre codes from the provided genre catalog.
            - Return at most 5 genre codes.
            - Prefer broad but meaningful matches.
            - If the answers are vague, return the strongest likely genres rather than inventing new codes.
            - Output must be valid JSON only.
            """;

    private final GenreRepository genreRepository;

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

    public List<String> inferGenreCodes(
            List<String> selectedGenreCodes,
            String storyPreferenceAnswer,
            String recentFavoriteContentAnswer
    ) {
        if (hasMeaningfulText(storyPreferenceAnswer) || hasMeaningfulText(recentFavoriteContentAnswer)) {
            Set<String> inferred = new LinkedHashSet<>(
                    inferByGptOrFallback(selectedGenreCodes, storyPreferenceAnswer, recentFavoriteContentAnswer)
            );
            inferred.removeAll(normalizeExistingGenreCodes(selectedGenreCodes));
            return inferred.stream().limit(MAX_GENRE_CODES).toList();
        }

        return List.of();
    }

    private List<String> inferByGptOrFallback(
            List<String> selectedGenreCodes,
            String storyPreferenceAnswer,
            String recentFavoriteContentAnswer
    ) {
        try {
            if (enabled && apiKey != null && !apiKey.isBlank()) {
                List<String> byGpt = requestGenreCodesFromOpenAi(selectedGenreCodes, storyPreferenceAnswer, recentFavoriteContentAnswer);
                if (!byGpt.isEmpty()) {
                    return byGpt;
                }
            }
        } catch (Exception exception) {
            log.warn("Onboarding survey genre inference via OpenAI failed. Falling back to heuristics.", exception);
        }

        return inferGenreCodesByFallback(storyPreferenceAnswer, recentFavoriteContentAnswer);
    }

    private List<String> requestGenreCodesFromOpenAi(
            List<String> selectedGenreCodes,
            String storyPreferenceAnswer,
            String recentFavoriteContentAnswer
    ) {
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        String genreCatalog = genreRepository.findAll().stream()
                .map(genre -> "%s | %s | %s".formatted(
                        safe(genre.getCode()),
                        safe(genre.getName()),
                        safe(genre.getDescription())
                ))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No genres available");
        String selectedGenres = String.join(", ", normalizeExistingGenreCodes(selectedGenreCodes));

        ChatCompletionResponse response = client.post()
                .uri("/chat/completions")
                .body(new ChatCompletionRequest(
                        model,
                        List.of(
                                new ChatMessage("system", SYSTEM_PROMPT),
                                new ChatMessage("user", buildUserPrompt(genreCatalog, selectedGenres, storyPreferenceAnswer, recentFavoriteContentAnswer))
                        ),
                        temperature
                ))
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return List.of();
        }

        ChatMessage message = response.choices().get(0).message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            return List.of();
        }

        return extractGenreCodesFromJson(message.content());
    }

    private String buildUserPrompt(
            String genreCatalog,
            String selectedGenres,
            String storyPreferenceAnswer,
            String recentFavoriteContentAnswer
    ) {
        return """
                Available genre catalog:
                %s

                Onboarding survey answers:
                1) Selected genres: %s
                2) What kind of story pulls at your heart the most?: %s
                3) Tell us about a recent book, film, or drama that stayed with you: %s

                Infer the best matching genre codes from the catalog.
                Return JSON only.
                """.formatted(
                genreCatalog,
                safe(selectedGenres),
                safe(storyPreferenceAnswer),
                safe(recentFavoriteContentAnswer)
        );
    }

    private List<String> extractGenreCodesFromJson(String rawContent) {
        Matcher arrayMatcher = GENRE_CODE_PATTERN.matcher(rawContent);
        if (!arrayMatcher.find()) {
            return List.of();
        }

        String arrayBody = arrayMatcher.group(1);
        Matcher valueMatcher = QUOTED_VALUE_PATTERN.matcher(arrayBody);
        List<String> extracted = new ArrayList<>();
        while (valueMatcher.find()) {
            extracted.add(valueMatcher.group(1));
        }
        return normalizeExistingGenreCodes(extracted);
    }

    private List<String> inferGenreCodesByFallback(String storyPreferenceAnswer, String recentFavoriteContentAnswer) {
        String combined = (safe(storyPreferenceAnswer) + " " + safe(recentFavoriteContentAnswer)).toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>();

        if (containsAny(combined, "healing", "comfort", "warm", "friendship", "family")) {
            candidates.addAll(matchGenreCodes("HEALING", "ESSAY", "LITERATURE"));
        }
        if (containsAny(combined, "love", "romance", "relationship", "heartbreak")) {
            candidates.addAll(matchGenreCodes("ROMANCE", "CLASSIC", "NOVEL"));
        }
        if (containsAny(combined, "mystery", "crime", "secret", "suspense")) {
            candidates.addAll(matchGenreCodes("MYSTERY", "THRILLER", "FICTION"));
        }
        if (containsAny(combined, "history", "war", "society", "politics", "human")) {
            candidates.addAll(matchGenreCodes("HISTORY", "REPORTAGE", "LITERATURE"));
        }
        if (containsAny(combined, "poem", "poetry", "lyrical", "emotion")) {
            candidates.addAll(matchGenreCodes("POETRY", "ESSAY"));
        }
        if (containsAny(combined, "legend", "folk", "fairy", "myth")) {
            candidates.addAll(matchGenreCodes("FOLKTALE", "MYTH", "CLASSIC"));
        }
        if (containsAny(combined, "fantasy", "dream", "magic", "symbolic")) {
            candidates.addAll(matchGenreCodes("FANTASY", "FICTION", "CLASSIC"));
        }

        if (candidates.isEmpty()) {
            candidates.addAll(matchGenreCodes("LITERATURE", "CLASSIC", "FICTION", "POETRY"));
        }

        return normalizeExistingGenreCodes(candidates);
    }

    private List<String> matchGenreCodes(String... keywords) {
        List<Genre> genres = genreRepository.findAll();
        Set<String> matched = new LinkedHashSet<>();

        for (String keyword : keywords) {
            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
            genres.stream()
                    .filter(genre -> containsGenreKeyword(genre, normalizedKeyword))
                    .map(Genre::getCode)
                    .forEach(matched::add);
        }

        return matched.stream().toList();
    }

    private boolean containsGenreKeyword(Genre genre, String keyword) {
        return safe(genre.getCode()).toLowerCase(Locale.ROOT).contains(keyword)
                || safe(genre.getName()).toLowerCase(Locale.ROOT).contains(keyword)
                || safe(genre.getDescription()).toLowerCase(Locale.ROOT).contains(keyword);
    }

    private List<String> normalizeExistingGenreCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            genreRepository.findByCodeIgnoreCase(code.trim())
                    .map(Genre::getCode)
                    .ifPresent(normalized::add);
            if (normalized.size() >= MAX_GENRE_CODES) {
                break;
            }
        }
        return normalized.stream().toList();
    }

    private boolean hasMeaningfulText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "Not available";
        }
        return value.trim();
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




