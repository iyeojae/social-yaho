package org.huss.socialsaas.literature.dto.response;

import java.time.Instant;
import java.util.List;

public record XlsxBookImportResponse(
        String importPath,
        String sheetName,
        int totalRows,
        int importedRows,
        int createdCount,
        int updatedCount,
        Instant importedAt,
        List<ImportedBook> books
) {

    public record ImportedBook(
            Long id,
            String sourceBookId,
            String title,
            String translatedLanguage,
            List<String> genreCodes,
            String status
    ) {
    }
}

