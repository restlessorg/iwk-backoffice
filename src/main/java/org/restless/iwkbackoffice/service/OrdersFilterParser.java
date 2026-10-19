package org.restless.iwkbackoffice.service;

import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.exception.BadRequestException;
import org.restless.iwkbackoffice.util.Range;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class OrdersFilterParser {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public OrdersFilter parse(
            String query,
            String date,
            String subTotal,
            String taxAmount,
            String taxRate,
            String totalAmount
    ) {
        return new OrdersFilter(
                normalizeQuery(query),
                parseDateRange(date, "date"),
                parseDecimalRange(subTotal, "subTotal"),
                parseDecimalRange(taxAmount, "taxAmount"),
                parseDecimalRange(taxRate, "taxRate"),
                parseDecimalRange(totalAmount, "totalAmount")
        );
    }

    public void validatePagination(int page, int perPage) {
        if (page < 0) {
            throw new BadRequestException("page must be greater than or equal to 0");
        }
        if (perPage < 1) {
            throw new BadRequestException("perPage must be greater than or equal to 1");
        }
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private Range<LocalDate> parseDateRange(String raw, String field) {
        if (raw == null) {
            return new Range<>(null, null);
        }

        String[] bounds = splitRange(raw, field);
        return new Range<>(parseDateBound(bounds[0], field), parseDateBound(bounds[1], field));
    }

    private Range<BigDecimal> parseDecimalRange(String raw, String field) {
        if (raw == null) {
            return new Range<>(null, null);
        }

        String[] bounds = splitRange(raw, field);
        return new Range<>(parseDecimalBound(bounds[0], field), parseDecimalBound(bounds[1], field));
    }

    private String[] splitRange(String raw, String field) {
        String[] bounds = raw.split(",", -1);
        if (bounds.length != 2) {
            throw new BadRequestException("Invalid " + field + " range, expected 'from,to'");
        }
        return bounds;
    }

    private LocalDate parseDateBound(String raw, String field) {
        if (raw.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(raw, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("Invalid " + field + " bound: " + raw);
        }
    }

    private BigDecimal parseDecimalBound(String raw, String field) {
        if (raw.isBlank()) {
            throw new BadRequestException("Both bounds are required for " + field);
        }

        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Invalid " + field + " bound: " + raw);
        }
    }
}
