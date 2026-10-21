package org.restless.iwkbackoffice.service;

import org.junit.jupiter.api.Test;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.domain.request.OrdersSort;
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
                "1.10,11.50",
                " 12, 40,12 "
        );

        assertThat(filter.query()).isEqualTo("42");
        assertThat(filter.date().from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(filter.date().to()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(filter.subTotal().from()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(filter.totalAmount().to()).isEqualByComparingTo(new BigDecimal("11.50"));
        assertThat(filter.jurisdictionIds()).containsExactly(12L, 40L);
    }

    @Test
    void treatsMissingFiltersAsUnbounded() {
        OrdersFilter filter = parser.parse(null, null, null, null, null, null, null);

        assertThat(filter.query()).isEmpty();
        assertThat(filter.date().from()).isNull();
        assertThat(filter.date().to()).isNull();
        assertThat(filter.subTotal().from()).isNull();
        assertThat(filter.taxAmount().to()).isNull();
        assertThat(filter.taxRate().from()).isNull();
        assertThat(filter.totalAmount().to()).isNull();
        assertThat(filter.jurisdictionIds()).isEmpty();
    }

    @Test
    void rejectsMalformedNumericRanges() {
        assertThatThrownBy(() -> parser.parse("", ",", "1", "0,1", "0,1", "0,1", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid subTotal range, expected 'from,to'");
    }

    @Test
    void rejectsInvalidJurisdictionIds() {
        assertThatThrownBy(() -> parser.parse(null, null, null, null, null, null, "12,abc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid jurisdiction id: abc");
        assertThatThrownBy(() -> parser.parse(null, null, null, null, null, null, "12,"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid jurisdiction id: ");
        assertThatThrownBy(() -> parser.parse(null, null, null, null, null, null, "0"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid jurisdiction id: 0");
    }

    @Test
    void defaultsSortToIdAscending() {
        assertThat(parser.parseSort(null)).isEqualTo(OrdersSort.DEFAULT);
        assertThat(parser.parseSort(" ")).isEqualTo(OrdersSort.DEFAULT);
    }

    @Test
    void parsesSortFieldAndDirection() {
        assertThat(parser.parseSort("totalAmount,desc"))
                .isEqualTo(new OrdersSort(OrdersSort.Field.TOTAL_AMOUNT, OrdersSort.Direction.DESC));
        assertThat(parser.parseSort("timestamp"))
                .isEqualTo(new OrdersSort(OrdersSort.Field.TIMESTAMP, OrdersSort.Direction.ASC));
        assertThat(parser.parseSort("subtotal,ASC"))
                .isEqualTo(new OrdersSort(OrdersSort.Field.SUB_TOTAL, OrdersSort.Direction.ASC));
    }

    @Test
    void rejectsUnknownSortFieldOrDirection() {
        assertThatThrownBy(() -> parser.parseSort("latitude,asc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid sort field: latitude");
        assertThatThrownBy(() -> parser.parseSort("id,up"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid sort direction: up, expected 'asc' or 'desc'");
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatThrownBy(() -> parser.validatePagination(-1, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("page must be greater than or equal to 0");
    }
}
