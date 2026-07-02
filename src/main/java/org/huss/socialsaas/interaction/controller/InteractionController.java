package org.huss.socialsaas.interaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.response.ApiResponse;
import org.huss.socialsaas.interaction.dto.request.InteractionCreateRequest;
import org.huss.socialsaas.interaction.dto.response.InteractionResponse;
import org.huss.socialsaas.interaction.service.InteractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/interactions")
public class InteractionController {

    private static final String USER_ID_HEADER = "X-USER-ID";

    private final InteractionService interactionService;

    @PostMapping
    public ResponseEntity<ApiResponse<InteractionResponse>> createInteraction(
            @RequestHeader(USER_ID_HEADER) Long userId,
            @Valid @RequestBody InteractionCreateRequest request
    ) {
        InteractionResponse response = interactionService.createInteraction(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("상호작용 이벤트를 저장했습니다.", response));
    }
}

