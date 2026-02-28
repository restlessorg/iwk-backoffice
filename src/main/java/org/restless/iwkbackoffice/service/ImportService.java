package org.restless.iwkbackoffice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.NonNull;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.domain.response.OrdersImportResponse;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.restless.iwkbackoffice.util.ParsingUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {
    private final JdbcTemplate jdbcTemplate;

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT
            .builder()
            .setTrim(true)
            .setHeader("id", "longitude", "latitude", "timestamp", "subTotal")
            .setSkipHeaderRecord(true)
            .get();

    private static final int BATCH_SIZE = 500;

    @Transactional
    public OrdersImportResponse importOrders(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Unable to import orders, file is empty");
            return new OrdersImportResponse(0, 0, 0, false, "File is empty");
        }

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                CSVParser parser = CSVParser.builder().setReader(reader).setFormat(FORMAT).get()
        ) {
            long processed = 0;
            long saved = 0;
            List<OrderEntity> buffer = new ArrayList<>(BATCH_SIZE);

            for (CSVRecord record : parser) {
                buffer.add(toOrder(record));

                if (buffer.size() >= BATCH_SIZE) {
                    processed += buffer.size();
                    saved += saveBatch(buffer);
                }
            }

            if (!buffer.isEmpty()) {
                processed += buffer.size();
                saved += saveBatch(buffer);
            }

            long rejected = processed - saved;
            log.info("Processed orders: {}, saved: {}, rejected: {}", processed, saved, rejected);
            return new OrdersImportResponse(processed, saved, rejected, true, null);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to import orders", e);
            return new OrdersImportResponse(0, 0, 0, false, "Failed to import orders");
        }
    }

    private long saveBatch(List<OrderEntity> batch) {
        List<Long> ids = jdbcTemplate.queryForList("SELECT nextval('orders_id_seq') FROM generate_series(1, ?)", Long.class, batch.size());

        for (int i = 0; i < batch.size(); i++) {
            batch.get(i).setId(ids.get(i));
        }

        List<String> sqlParts = new ArrayList<>(batch.size());
        List<Object> params = new ArrayList<>(batch.size() * 3);
        batch.forEach(order -> {
            sqlParts.add("(?,?,?)");
            params.add(order.getId());
            params.add(order.getLongitude());
            params.add(order.getLatitude());
        });

        String sql = """
                WITH input_rows(order_id, longitude, latitude) AS (
                    VALUES %s
                )
                SELECT i.order_id, j.*
                FROM input_rows i
                JOIN tax_jurisdictions j
                  ON j.coordinates && ST_SetSRID(ST_MakePoint(i.longitude, i.latitude), 4326)
                 AND ST_Covers(j.coordinates, ST_SetSRID(ST_MakePoint(i.longitude, i.latitude), 4326))
                """.formatted(String.join(",", sqlParts));

        Map<OrderEntity, List<JurisdictionDetails>> orderJurisdictions = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Long orderId = rs.getObject("order_id", Long.class);
            Long jurisdictionId = rs.getObject("id", Long.class);
            JurisdictionType jurisdictionType = JurisdictionType.valueOf(rs.getString("type"));
            BigDecimal stateRate = rs.getBigDecimal("state_rate");
            BigDecimal countyRate = rs.getBigDecimal("county_rate");
            BigDecimal cityRate = rs.getBigDecimal("city_rate");
            BigDecimal specialRate = rs.getBigDecimal("special_rate");

            OrderEntity order = batch.stream()
                    .filter(o -> o.getId().equals(orderId))
                    .findAny()
                    .get();
            JurisdictionDetails jurisdictionDetails = new JurisdictionDetails(jurisdictionId, jurisdictionType, stateRate, countyRate, cityRate, specialRate);
            orderJurisdictions.computeIfAbsent(order, ignored -> new ArrayList<>()).add(jurisdictionDetails);
        }, params.toArray());

        Map<OrderEntity, List<JurisdictionDetails>> oj = orderJurisdictions.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        List<OrderEntity> ord = new ArrayList<>(oj.keySet());

        int insertedOrders = Arrays.stream(jdbcTemplate.batchUpdate(
                "INSERT INTO orders (id,longitude,latitude,ts,subtotal) VALUES (?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                        OrderEntity order = ord.get(i);
                        ps.setLong(1, order.getId());
                        ps.setDouble(2, order.getLongitude());
                        ps.setDouble(3, order.getLatitude());
                        ps.setTimestamp(4, Timestamp.valueOf(order.getTs()));
                        ps.setBigDecimal(5, order.getSubtotal());
                    }

                    @Override
                    public int getBatchSize() {
                        return ord.size();
                    }
                }
        )).sum();

        jdbcTemplate.batchUpdate(
                "INSERT INTO order_details (order_id,jurisdictions,composite_tax_rate,tax_amount,total_amount) VALUES (?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                        OrderEntity order = ord.get(i);
                        List<JurisdictionDetails> jurisdictionDetails = orderJurisdictions.get(order);
                        Long[] jIds = jurisdictionDetails.stream()
                                .map(JurisdictionDetails::id)
                                .toArray(Long[]::new);
                        jurisdictionDetails.sort((o1, o2) -> {
                            if (o1.type() == o2.type()) {
                                return 0;
                            }
                            if (o1.type() == JurisdictionType.CITY) {
                                return -1;
                            }
                            return 1;
                        });
                        JurisdictionDetails prioritizedJurisdiction = jurisdictionDetails.getFirst();

                        BigDecimal compositeTaxRate = prioritizedJurisdiction.stateRate
                                .add(prioritizedJurisdiction.countyRate)
                                .add(prioritizedJurisdiction.cityRate)
                                .add(prioritizedJurisdiction.specialRate);

                        BigDecimal taxAmount = order.getSubtotal()
                                .multiply(compositeTaxRate)
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                        BigDecimal totalAmount = order.getSubtotal().add(taxAmount);

                        ps.setLong(1, order.getId());
                        ps.setArray(2, ps.getConnection().createArrayOf("bigint", jIds));
                        ps.setBigDecimal(3, compositeTaxRate);
                        ps.setBigDecimal(4, taxAmount);
                        ps.setBigDecimal(5, totalAmount);
                    }

                    @Override
                    public int getBatchSize() {
                        return ord.size();
                    }
                }
        );

        batch.clear();
        return insertedOrders;
    }

    private OrderEntity toOrder(CSVRecord r) {
        Double longitude = parseDoubleRequired(r.get(1), "longitude");
        Double latitude = parseDoubleRequired(r.get(2), "latitude");
        LocalDateTime ts = parseTimestampRequired(r.get(3), "timestamp");
        BigDecimal subtotal = parseBigDecimalRequired(r.get(4), "subTotal");
        return new OrderEntity(longitude, latitude, ts, subtotal);
    }

    public record JurisdictionDetails(
            Long id, JurisdictionType type, BigDecimal stateRate, BigDecimal countyRate, BigDecimal cityRate,
            BigDecimal specialRate
    ) {
    }

    public enum JurisdictionType {
        COUNTY, CITY
    }
}
