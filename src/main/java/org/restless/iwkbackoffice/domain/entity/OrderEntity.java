package org.restless.iwkbackoffice.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {
    private Long id;

    private Double longitude;

    private Double latitude;

    private LocalDateTime ts;

    private BigDecimal subtotal;

    private String source;

    public OrderEntity(Double longitude, Double latitude, LocalDateTime ts, BigDecimal subtotal, String source) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.ts = ts;
        this.subtotal = subtotal;
        this.source = source;
    }
}
