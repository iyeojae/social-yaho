package org.huss.socialsaas.interaction.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.exception.BusinessException;
import org.huss.socialsaas.global.exception.ErrorCode;
import org.huss.socialsaas.interaction.dto.request.InteractionCreateRequest;
import org.huss.socialsaas.interaction.dto.response.InteractionResponse;
import org.huss.socialsaas.interaction.entity.InteractionType;
import org.huss.socialsaas.interaction.entity.UserInteractionEvent;
import org.huss.socialsaas.interaction.repository.UserInteractionEventRepository;
import org.huss.socialsaas.literature.entity.LiteratureWork;
import org.huss.socialsaas.literature.repository.LiteratureWorkRepository;
import org.huss.socialsaas.preference.service.UserPreferenceService;
import org.huss.socialsaas.recommendation.service.RecommendationCacheService;
import org.huss.socialsaas.user.entity.User;
import org.huss.socialsaas.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InteractionService {

    private final UserService userService;
    private final LiteratureWorkRepository literatureWorkRepository;
    private final UserInteractionEventRepository userInteractionEventRepository;
    private final UserPreferenceService userPreferenceService;
    private final RecommendationCacheService recommendationCacheService;

    @Transactional
    public InteractionResponse createInteraction(Long userId, InteractionCreateRequest request) {
        validateRequest(request);

        User user = userService.getUser(userId);
        LiteratureWork literatureWork = literatureWorkRepository.findDetailById(request.bookId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        UserInteractionEvent event = userInteractionEventRepository.save(UserInteractionEvent.create(
                user,
                literatureWork,
                request.interactionType(),
                request.viewDurationSeconds(),
                request.progressPercent(),
                normalize(request.sourceScreen())
        ));

        userPreferenceService.reflectInteraction(user, literatureWork, request.interactionType());
        recommendationCacheService.evictUserFeed(userId);

        return InteractionResponse.from(event);
    }

    private void validateRequest(InteractionCreateRequest request) {
        InteractionType type = request.interactionType();

        if ((type == InteractionType.VIEW || type == InteractionType.READ_START || type == InteractionType.READ_COMPLETE)
                && request.progressPercent() == null && request.viewDurationSeconds() == null) {
            throw new BusinessException(ErrorCode.INVALID_INTERACTION_REQUEST);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

