package org.restless.iwkbackoffice.domain.response;

public record OrderResponse(
        Long id,
        String timestamp,
        Double latitude,
        Double longitude,
        Double subTotal,
        Double taxRate,
        Double taxAmount,
        Double totalAmount
) {
}
