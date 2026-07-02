package org.huss.socialsaas.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "도서를 찾을 수 없습니다."),
    BOOK_IMPORT_PATH_NOT_FOUND(HttpStatus.BAD_REQUEST, "BOOK_IMPORT_PATH_NOT_FOUND", "도서 마크다운 경로를 찾을 수 없습니다."),
    BOOK_IMPORT_GENRE_MISSING(HttpStatus.BAD_REQUEST, "BOOK_IMPORT_GENRE_MISSING", "도서 마크다운의 장르 메타데이터가 비어 있습니다."),
    BOOK_IMPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BOOK_IMPORT_FAILED", "도서 마크다운 import에 실패했습니다."),
    BOOK_IMPORT_XLSX_PATH_NOT_FOUND(HttpStatus.BAD_REQUEST, "BOOK_IMPORT_XLSX_PATH_NOT_FOUND", "도서 엑셀 경로를 찾을 수 없습니다."),
    BOOK_IMPORT_XLSX_INVALID_HEADER(HttpStatus.BAD_REQUEST, "BOOK_IMPORT_XLSX_INVALID_HEADER", "도서 엑셀 헤더 형식이 올바르지 않습니다."),
    BOOK_IMPORT_XLSX_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "BOOK_IMPORT_XLSX_FAILED", "도서 엑셀 import에 실패했습니다."),
    INVALID_INTERACTION_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_INTERACTION_REQUEST", "상호작용 요청이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "잘못된 요청입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}





