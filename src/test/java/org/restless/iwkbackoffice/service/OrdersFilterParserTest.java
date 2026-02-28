package org.restless.iwkbackoffice.service;

import org.junit.jupiter.api.Test;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdersFilterParserTest {
    private final OrdersFilterParser parser = new OrdersFilterParser();

    @Test
    void parsesExpectedFilterShape() {
        OrdersFilter filter = parser.parse(
                " 42 ",
                "2026-01-01,2026-01-31",
                "1.00,10.00",
                "0.10,1.50",
                "4.00,8.50",
                "1.10,11.50"
        );

        assertThat(filter.query()).isEqualTo("42");
        assertThat(filter.date().from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filter.date().to()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(filter.subTotal().from()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(filter.totalAmount().to()).isEqualByComparingTo(new BigDecimal("11.50"));
    }

    @Test
    void rejectsMalformedNumericRanges() {
        assertThatThrownBy(() -> parser.parse("", ",", "1", "0,1", "0,1", "0,1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid subTotal range, expected 'from,to'");
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> parser.validatePagination(-1, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("page must be greater than or equal to 0");
    }
}
