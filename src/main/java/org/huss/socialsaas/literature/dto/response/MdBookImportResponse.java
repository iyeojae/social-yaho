package org.huss.socialsaas.literature.dto.response;

import java.time.Instant;
import java.util.List;

public record MdBookImportResponse(
        String importPath,
        int totalFiles,
        int createdCount,
        int updatedCount,
        Instant importedAt,
        List<ImportedBook> books
) {

    public record ImportedBook(
            Long id,
            String sourceBookId,
            String title,
            List<String> genreCodes,
            String status
    ) {
    }
}


