package org.restless.iwkbackoffice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.NonNull;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.domain.response.OrdersImportResponse;
import org.restless.iwkbackoffice.domain.tax.CalculatedOrderTaxes;
import org.restless.iwkbackoffice.domain.tax.JurisdictionDetails;
import org.restless.iwkbackoffice.domain.tax.JurisdictionType;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
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
    private final OrderTaxCalculator orderTaxCalculator;

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
            String source = file.getOriginalFilename();

            for (CSVRecord record : parser) {
                buffer.add(toOrder(record, source));

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

        Map<Long, OrderEntity> ordersById = batch.stream()
                .collect(Collectors.toMap(OrderEntity::getId, order -> order));
        Map<OrderEntity, List<JurisdictionDetails>> orderJurisdictions = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            Long orderId = rs.getObject("order_id", Long.class);
            OrderEntity order = ordersById.get(orderId);
            JurisdictionDetails jurisdictionDetails = mapJurisdictionDetails(rs);
            orderJurisdictions.computeIfAbsent(order, ignored -> new ArrayList<>()).add(jurisdictionDetails);
        }, params.toArray());

        List<OrderEntity> ordersToPersist = new ArrayList<>(orderJurisdictions.keySet());

        int insertedOrders = Arrays.stream(jdbcTemplate.batchUpdate(
                "INSERT INTO orders (id,longitude,latitude,ts,subtotal,source) VALUES (?,?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                        OrderEntity order = ordersToPersist.get(i);
                        ps.setLong(1, order.getId());
                        ps.setDouble(2, order.getLongitude());
                        ps.setDouble(3, order.getLatitude());
                        ps.setTimestamp(4, Timestamp.valueOf(order.getTs()));
                        ps.setBigDecimal(5, order.getSubtotal());
                        ps.setString(6, order.getSource());
                    }

                    @Override
                    public int getBatchSize() {
                        return ordersToPersist.size();
                    }
                }
        )).sum();

        jdbcTemplate.batchUpdate(
                "INSERT INTO order_details (order_id,jurisdictions,composite_tax_rate,tax_amount,total_amount) VALUES (?,?,?,?,?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                        OrderEntity order = ordersToPersist.get(i);
                        List<JurisdictionDetails> jurisdictionDetails = orderJurisdictions.get(order);
                        CalculatedOrderTaxes taxes = orderTaxCalculator.calculate(order.getSubtotal(), jurisdictionDetails);

                        ps.setLong(1, order.getId());
                        ps.setArray(2, ps.getConnection().createArrayOf("bigint", taxes.jurisdictionIds().toArray(Long[]::new)));
                        ps.setBigDecimal(3, taxes.compositeTaxRate());
                        ps.setBigDecimal(4, taxes.taxAmount());
                        ps.setBigDecimal(5, taxes.totalAmount());
                    }

                    @Override
                    public int getBatchSize() {
                        return ordersToPersist.size();
                    }
                }
        );

        batch.clear();
        return insertedOrders;
    }

    private OrderEntity toOrder(CSVRecord r, String source) {
        Double longitude = parseDoubleRequired(r.get(1), "longitude");
        Double latitude = parseDoubleRequired(r.get(2), "latitude");
        LocalDateTime ts = parseTimestampRequired(r.get(3), "timestamp");
        BigDecimal subtotal = parseBigDecimalRequired(r.get(4), "subTotal");
        return new OrderEntity(longitude, latitude, ts, subtotal, source);
    }

    private JurisdictionDetails mapJurisdictionDetails(java.sql.ResultSet rs) throws SQLException {
        return new JurisdictionDetails(
                rs.getObject("id", Long.class),
                JurisdictionType.valueOf(rs.getString("type")),
                rs.getBigDecimal("state_rate"),
                rs.getBigDecimal("county_rate"),
                rs.getBigDecimal("city_rate"),
                rs.getBigDecimal("special_rate")
        );
    }
}
