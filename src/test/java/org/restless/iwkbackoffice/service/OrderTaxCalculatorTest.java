package org.restless.iwkbackoffice.service;

import org.junit.jupiter.api.Test;
import org.restless.iwkbackoffice.domain.tax.CalculatedOrderTaxes;
import org.restless.iwkbackoffice.domain.tax.JurisdictionDetails;
import org.restless.iwkbackoffice.domain.tax.JurisdictionType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTaxCalculatorTest {
    private final OrderTaxCalculator calculator = new OrderTaxCalculator();

    @Test
    void prefersCityJurisdictionWhenComputingCompositeTax() {
        CalculatedOrderTaxes result = calculator.calculate(
                new BigDecimal("100.00"),
                List.of(
                        new JurisdictionDetails(2L, JurisdictionType.COUNTY, new BigDecimal("4.00"), new BigDecimal("1.50"), BigDecimal.ZERO, BigDecimal.ZERO),
                        new JurisdictionDetails(1L, JurisdictionType.CITY, new BigDecimal("4.00"), new BigDecimal("1.50"), new BigDecimal("0.50"), new BigDecimal("0.38"))
                )
        );

        assertThat(result.jurisdictionIds()).containsExactly(1L, 2L);
        assertThat(result.compositeTaxRate()).isEqualByComparingTo("6.38");
        assertThat(result.taxAmount()).isEqualByComparingTo("6.38");
        assertThat(result.totalAmount()).isEqualByComparingTo("106.38");
    }

    @Test
    void roundsTaxAmountHalfUpToTwoDecimalPlaces() {
        CalculatedOrderTaxes result = calculator.calculate(
                new BigDecimal("10.00"),
                List.of(
                        new JurisdictionDetails(5L, JurisdictionType.COUNTY, new BigDecimal("8.375"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                )
        );

        assertThat(result.taxAmount()).isEqualByComparingTo("0.84");
        assertThat(result.totalAmount()).isEqualByComparingTo("10.84");
    }
}
