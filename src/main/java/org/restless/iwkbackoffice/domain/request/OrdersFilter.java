package org.restless.iwkbackoffice.domain.request;

import org.restless.iwkbackoffice.util.Range;

import java.time.LocalDate;

public record OrdersFilter(
        String query,
        Range<LocalDate> date,
        Range<Double> subTotal,
        Range<Double> taxAmount,
        Range<Double> taxRate,
        Range<Double> totalAmount
) {
}
