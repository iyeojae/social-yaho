package org.huss.socialsaas.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "유효한 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하로 입력해주세요.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 100, message = "닉네임은 100자 이하로 입력해주세요.")
        String nickname,

        @Size(max = 5, message = "선호 장르는 최대 5개까지 선택할 수 있습니다.")
        List<@NotBlank(message = "선호 장르 코드는 비어 있을 수 없습니다.") String> preferredGenreCodes
) {
}
