package org.restless.iwkbackoffice.domain.response;

public record OrderDetailsResponse(
        Long id,
        String timestamp,
        Double longitude,
        Double latitude,
        Double subTotal,
        Double totalAmount,
        Double taxRate,
        Double stateRate,
        Double countyRate,
        Double cityRate,
        Double specialRate,
        Double taxAmount,
        String country,
        String state,
        String county,
        String city,
        String special,
        String source
) {
}
