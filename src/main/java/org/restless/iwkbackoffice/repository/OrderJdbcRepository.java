package org.restless.iwkbackoffice.repository;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.service.ImportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Long createOrder(OrderEntity order) {
        Long id = jdbcTemplate.queryForObject("SELECT nextval('orders_id_seq')", Long.class);

        order.setId(id);

        String sql = """
                WITH input_rows(order_id, longitude, latitude) AS (
                    VALUES (?,?,?)
                )
                SELECT i.order_id, j.*
                FROM input_rows i
                JOIN tax_jurisdictions j
                 ON j.coordinates && ST_SetSRID(ST_MakePoint(i.longitude, i.latitude), 4326)
                 AND ST_Covers(j.coordinates, ST_SetSRID(ST_MakePoint(i.longitude, i.latitude), 4326))
                """;

        List<ImportService.JurisdictionDetails> jurisdictionDetails = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            Long jurisdictionId = rs.getObject("id", Long.class);
            ImportService.JurisdictionType jurisdictionType = ImportService.JurisdictionType.valueOf(rs.getString("type"));
            BigDecimal stateRate = rs.getBigDecimal("state_rate");
            BigDecimal countyRate = rs.getBigDecimal("county_rate");
            BigDecimal cityRate = rs.getBigDecimal("city_rate");
            BigDecimal specialRate = rs.getBigDecimal("special_rate");
            jurisdictionDetails.add(new ImportService.JurisdictionDetails(
                    jurisdictionId,
                    jurisdictionType,
                    stateRate,
                    countyRate,
                    cityRate,
                    specialRate
            ));
        }, order.getId(), order.getLongitude(), order.getLatitude());

        if (jurisdictionDetails.isEmpty()) {
            throw new IllegalStateException("No tax jurisdiction found for order coordinates");
        }

        jdbcTemplate.update(
                "INSERT INTO orders (id,longitude,latitude,ts,subtotal) VALUES (?,?,?,?,?)",
                order.getId(),
                order.getLongitude(),
                order.getLatitude(),
                Timestamp.valueOf(order.getTs()),
                order.getSubtotal()
        );

        List<ImportService.JurisdictionDetails> sortedJurisdictionDetails = new ArrayList<>(jurisdictionDetails);
        sortedJurisdictionDetails.sort((o1, o2) -> {
            if (o1.type() == o2.type()) {
                return 0;
            }
            if (o1.type() == ImportService.JurisdictionType.CITY) {
                return -1;
            }
            return 1;
        });

        ImportService.JurisdictionDetails prioritizedJurisdiction = sortedJurisdictionDetails.getFirst();
        BigDecimal compositeTaxRate = prioritizedJurisdiction.stateRate()
                .add(prioritizedJurisdiction.countyRate())
                .add(prioritizedJurisdiction.cityRate())
                .add(prioritizedJurisdiction.specialRate());
        BigDecimal taxAmount = order.getSubtotal()
                .multiply(compositeTaxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = order.getSubtotal().add(taxAmount);
        Long[] jurisdictionIds = sortedJurisdictionDetails.stream()
                .map(ImportService.JurisdictionDetails::id)
                .toArray(Long[]::new);

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO order_details (order_id,jurisdictions,composite_tax_rate,tax_amount,total_amount) VALUES (?,?,?,?,?)"
            );
            ps.setLong(1, order.getId());
            ps.setArray(2, connection.createArrayOf("bigint", jurisdictionIds));
            ps.setBigDecimal(3, compositeTaxRate);
            ps.setBigDecimal(4, taxAmount);
            ps.setBigDecimal(5, totalAmount);
            return ps;
        });

        return order.getId();
    }
}
