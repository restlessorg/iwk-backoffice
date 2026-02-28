package org.restless.iwkbackoffice.domain.tax;

import java.math.BigDecimal;

public record JurisdictionDetails(
        Long id,
        JurisdictionType type,
        BigDecimal stateRate,
        BigDecimal countyRate,
        BigDecimal cityRate,
        BigDecimal specialRate
) {
}
