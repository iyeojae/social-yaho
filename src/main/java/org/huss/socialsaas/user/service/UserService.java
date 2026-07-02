package org.huss.socialsaas.user.service;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.exception.BusinessException;
import org.huss.socialsaas.global.exception.ErrorCode;
import org.huss.socialsaas.preference.service.OnboardingSurveyAnalysisService;
import org.huss.socialsaas.preference.service.UserPreferenceService;
import org.huss.socialsaas.user.dto.request.CreateUserRequest;
import org.huss.socialsaas.user.dto.request.UpdateUserRequest;
import org.huss.socialsaas.user.dto.response.UserProfileResponse;
import org.huss.socialsaas.user.entity.User;
import org.huss.socialsaas.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPreferenceService userPreferenceService;
    private final OnboardingSurveyAnalysisService onboardingSurveyAnalysisService;

    @Transactional
    public UserProfileResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User savedUser = userRepository.save(User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        ));

        List<String> inferredGenreCodes = onboardingSurveyAnalysisService.inferGenreCodes(
                request.preferredGenreCodes(),
                request.storyPreferenceAnswer(),
                request.recentFavoriteContentAnswer()
        );
        userPreferenceService.initializePreferencesFromSurvey(
                savedUser,
                request.preferredGenreCodes(),
                inferredGenreCodes
        );

        return UserProfileResponse.from(savedUser);
    }

    public UserProfileResponse getMyProfile(Long userId) {
        return UserProfileResponse.from(getUser(userId));
    }

    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UpdateUserRequest request) {
        User user = getUser(userId);
        user.updateNickname(request.nickname());
        return UserProfileResponse.from(user);
    }

    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
