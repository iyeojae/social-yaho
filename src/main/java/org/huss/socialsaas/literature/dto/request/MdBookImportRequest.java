package org.huss.socialsaas.literature.dto.request;

public record MdBookImportRequest(
        String importPath
) {

    public static MdBookImportRequest empty() {
        return new MdBookImportRequest(null);
    }
}


