package org.restless.iwkbackoffice.domain.response;

import java.util.List;

public record FilterLimits(
        List<Double> subTotal,
        List<Double> taxAmount,
        List<Double> taxRate,
        List<Double> totalAmount
) {
}
