package org.restless.iwkbackoffice.domain.tax;

import java.math.BigDecimal;
import java.util.List;

public record CalculatedOrderTaxes(
        List<Long> jurisdictionIds,
        BigDecimal compositeTaxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {
}
