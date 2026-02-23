package org.restless.iwkbackoffice.domain.response;

public record OrdersImportResponse(long processed, boolean success, String errorMessage) {
}
