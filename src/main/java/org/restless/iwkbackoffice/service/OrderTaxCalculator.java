package org.restless.iwkbackoffice.service;

import org.restless.iwkbackoffice.domain.tax.CalculatedOrderTaxes;
import org.restless.iwkbackoffice.domain.tax.JurisdictionDetails;
import org.restless.iwkbackoffice.domain.tax.JurisdictionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Component
public class OrderTaxCalculator {
    private static final Comparator<JurisdictionDetails> PRIORITY_ORDER =
            Comparator.comparing(details -> details.type() != JurisdictionType.CITY);

    public CalculatedOrderTaxes calculate(BigDecimal subTotal, List<JurisdictionDetails> jurisdictions) {
        List<JurisdictionDetails> prioritizedJurisdictions = jurisdictions.stream()
                .sorted(PRIORITY_ORDER)
                .toList();

        JurisdictionDetails selectedJurisdiction = prioritizedJurisdictions.getFirst();
        BigDecimal compositeTaxRate = selectedJurisdiction.stateRate()
                .add(selectedJurisdiction.countyRate())
                .add(selectedJurisdiction.cityRate())
                .add(selectedJurisdiction.specialRate());
        BigDecimal taxAmount = subTotal
                .multiply(compositeTaxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new CalculatedOrderTaxes(
                prioritizedJurisdictions.stream().map(JurisdictionDetails::id).toList(),
                compositeTaxRate,
                taxAmount,
                subTotal.add(taxAmount)
        );
    }
}
