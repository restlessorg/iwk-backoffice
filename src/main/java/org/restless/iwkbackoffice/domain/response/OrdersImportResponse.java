package org.restless.iwkbackoffice.domain.response;

public record OrdersImportResponse(long processed, long saved, long rejected, boolean success, String errorMessage) {
}
