package org.restless.iwkbackoffice.domain.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderCreateRequest(
        @NotNull Double longitude,
        @NotNull Double latitude,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal subTotal
) {
}
