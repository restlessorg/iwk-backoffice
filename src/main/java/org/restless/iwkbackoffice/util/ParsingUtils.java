package org.restless.iwkbackoffice.util;

import lombok.experimental.UtilityClass;
import org.restless.iwkbackoffice.exception.BadRequestException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

@UtilityClass
public class ParsingUtils {
    private static final DateTimeFormatter TS_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd HH:mm:ss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                    .optionalEnd()
                    .toFormatter();

    public static Double parseDoubleRequired(String raw, String field) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new BadRequestException("Missing required field: " + field);
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid " + field + ": " + raw);
        }
    }

    public static LocalDateTime parseTimestampRequired(String raw, String field) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new BadRequestException("Missing required field: " + field);

        try {
            return LocalDateTime.parse(s, TS_FORMATTER);
        } catch (Exception e) {
            throw new BadRequestException(
                    "Invalid " + field + ": " + raw + " (expected 'yyyy-MM-dd HH:mm:ss[.fffffffff]')"
            );
        }
    }

    public static BigDecimal parseBigDecimalRequired(String raw, String field) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) throw new BadRequestException("Missing required field: " + field);
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid " + field + ": " + raw);
        }
    }
}
