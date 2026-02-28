package org.restless.iwkbackoffice.domain.request;

import org.restless.iwkbackoffice.util.Range;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrdersFilter(
        String query,
        Range<LocalDate> date,
        Range<BigDecimal> subTotal,
        Range<BigDecimal> taxAmount,
        Range<BigDecimal> taxRate,
        Range<BigDecimal> totalAmount
) {
}
