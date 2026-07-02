package org.huss.socialsaas.literature.dto.request;

public record XlsxBookImportRequest(
        String importPath,
        String sheetName
) {

    public static XlsxBookImportRequest empty() {
        return new XlsxBookImportRequest(null, null);
    }
}

