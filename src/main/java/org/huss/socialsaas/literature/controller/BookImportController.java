package org.huss.socialsaas.literature.controller;

import lombok.RequiredArgsConstructor;
import org.huss.socialsaas.global.response.ApiResponse;
import org.huss.socialsaas.literature.dto.request.MdBookImportRequest;
import org.huss.socialsaas.literature.dto.request.XlsxBookImportRequest;
import org.huss.socialsaas.literature.dto.response.MdBookImportResponse;
import org.huss.socialsaas.literature.dto.response.XlsxBookImportResponse;
import org.huss.socialsaas.literature.service.MdBookImportService;
import org.huss.socialsaas.literature.service.XlsxBookImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/books")
public class BookImportController {

    private final MdBookImportService mdBookImportService;
    private final XlsxBookImportService xlsxBookImportService;

    @PostMapping("/import-md")
    public ResponseEntity<ApiResponse<MdBookImportResponse>> importMarkdownBooks(
            @RequestBody(required = false) MdBookImportRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "마크다운 도서 import를 완료했습니다.",
                mdBookImportService.importBooks(request)
        ));
    }

    @PostMapping("/import-xlsx")
    public ResponseEntity<ApiResponse<XlsxBookImportResponse>> importXlsxBooks(
            @RequestBody(required = false) XlsxBookImportRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "엑셀 도서 메타데이터 import를 완료했습니다.",
                xlsxBookImportService.importBooks(request)
        ));
    }
}


