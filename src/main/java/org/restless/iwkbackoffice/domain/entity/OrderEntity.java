package org.restless.iwkbackoffice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders")
public class OrderEntity {
    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    public OrderEntity(Double longitude, Double latitude, LocalDateTime ts, BigDecimal subtotal) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.ts = ts;
        this.subtotal = subtotal;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
