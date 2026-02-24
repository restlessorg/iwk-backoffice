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
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.restless.iwkbackoffice.util.ParsingUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService implements ImportService {
    private final JdbcTemplate jdbcTemplate;

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT
            .builder()
            .setTrim(true)
            .setHeader("id", "longitude", "latitude", "timestamp", "subtotal")
            .setSkipHeaderRecord(true)
            .get();

    private static final int BATCH_SIZE = 500;

    @Override
    public OrdersImportResponse importOrders(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Unable to import orders, file is empty");
            return new OrdersImportResponse(0, false, "File is empty");
        }

        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                CSVParser parser = CSVParser.builder().setReader(reader).setFormat(FORMAT).get()
        ) {
            long processed = 0;
            List<OrderEntity> buffer = new ArrayList<>(BATCH_SIZE);

            for (CSVRecord record : parser) {
                OrderEntity entity = toOrder(record);
                buffer.add(entity);

                if (buffer.size() >= BATCH_SIZE) {
                    processed += batchInsert(buffer);
                }
            }

            if (!buffer.isEmpty()) {
                processed += batchInsert(buffer);
            }

            return new OrdersImportResponse(processed, true, null);
        } catch (IOException e) {
            log.error("Failed to access file", e);
            return new OrdersImportResponse(0, false, "Failed to access file");
        }
    }

    private long batchInsert(List<OrderEntity> batch) {
        int[] inserted = jdbcTemplate.batchUpdate(
                "INSERT INTO orders (longitude, latitude, ts, subtotal) VALUES (?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                        OrderEntity o = batch.get(i);
                        ps.setDouble(1, o.getLongitude());
                        ps.setDouble(2, o.getLatitude());
                        ps.setObject(3, o.getTs());
                        ps.setBigDecimal(4, o.getSubtotal());
                    }

                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                }
        );
        batch.clear();
        return Arrays.stream(inserted).count();
    }

    private OrderEntity toOrder(CSVRecord r) {
        Double longitude = parseDoubleRequired(r.get(1), "longitude");
        Double latitude = parseDoubleRequired(r.get(2), "latitude");
        LocalDateTime ts = parseTimestampRequired(r.get(3), "timestamp");
        BigDecimal subtotal = parseBigDecimalRequired(r.get(4), "subtotal");
        return new OrderEntity(longitude, latitude, ts, subtotal);
    }
}
