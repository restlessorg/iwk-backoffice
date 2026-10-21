package org.restless.iwkbackoffice.domain.request;

import java.util.Arrays;
import java.util.Optional;

public record OrdersSort(Field field, Direction direction) {
    public static final OrdersSort DEFAULT = new OrdersSort(Field.ID, Direction.ASC);

    public enum Field {
        ID("id"),
        TIMESTAMP("timestamp"),
        SUB_TOTAL("subTotal"),
        TAX_RATE("taxRate"),
        TAX_AMOUNT("taxAmount"),
        TOTAL_AMOUNT("totalAmount");

        private final String apiName;

        Field(String apiName) {
            this.apiName = apiName;
        }

        public String apiName() {
            return apiName;
        }

        public static Optional<Field> fromApiName(String raw) {
            return Arrays.stream(values())
                    .filter(field -> field.apiName.equalsIgnoreCase(raw))
                    .findFirst();
        }
    }

    public enum Direction {
        ASC,
        DESC
    }
}
