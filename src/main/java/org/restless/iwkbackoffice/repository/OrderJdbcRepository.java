package org.restless.iwkbackoffice.repository;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.domain.response.FilterLimits;
import org.restless.iwkbackoffice.domain.response.OrderDetailsResponse;
import org.restless.iwkbackoffice.domain.response.OrderResponse;
import org.restless.iwkbackoffice.domain.response.PageData;
import org.restless.iwkbackoffice.service.ImportService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Repository
@RequiredArgsConstructor
public class OrderJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final String COUNTRY_NAME = "USA";
    private static final String STATE_NAME = "New York";
    private static final String MCTD_NAME = "MCTD (Metropolitan Commuter Transportation District)";
    private static final DateTimeFormatter ORDER_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");

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

    public FilterLimits getFilterLimits() {
        AtomicReference<BigDecimal> minsubtotal = new AtomicReference<>();
        AtomicReference<BigDecimal> maxsubtotal = new AtomicReference<>();
        AtomicReference<BigDecimal> mintaxamount = new AtomicReference<>();
        AtomicReference<BigDecimal> maxtaxamount = new AtomicReference<>();
        AtomicReference<BigDecimal> mintaxrate = new AtomicReference<>();
        AtomicReference<BigDecimal> maxtaxrate = new AtomicReference<>();
        AtomicReference<BigDecimal> mintotalamount = new AtomicReference<>();
        AtomicReference<BigDecimal> maxtotalamount = new AtomicReference<>();
        jdbcTemplate.query("select coalesce(min(o.subtotal), -1)            as minsubtotal," +
                "       coalesce(max(o.subtotal), -1)            as maxsubtotal," +
                "       coalesce(min(od.tax_amount), -1)         as mintaxamount," +
                "       coalesce(max(od.tax_amount), -1)         as maxtaxamount," +
                "       coalesce(min(od.composite_tax_rate), -1) as mintaxrate," +
                "       coalesce(max(od.composite_tax_rate), -1) as maxtaxrate," +
                "       coalesce(min(od.total_amount), -1)       as mintotalamount," +
                "       coalesce(max(od.total_amount), -1)       as maxtotalamount" +
                " from orders o" +
                "         join order_details od on o.id = od.order_id", rs -> {
            minsubtotal.set(rs.getBigDecimal("minsubtotal"));
            maxsubtotal.set(rs.getBigDecimal("maxsubtotal"));
            mintaxamount.set(rs.getBigDecimal("mintaxamount"));
            maxtaxamount.set(rs.getBigDecimal("maxtaxamount"));
            mintaxrate.set(rs.getBigDecimal("mintaxrate"));
            maxtaxrate.set(rs.getBigDecimal("maxtaxrate"));
            mintotalamount.set(rs.getBigDecimal("mintotalamount"));
            maxtotalamount.set(rs.getBigDecimal("maxtotalamount"));
        });
        return new FilterLimits(
                List.of(minsubtotal.get().doubleValue(), maxsubtotal.get().doubleValue()),
                List.of(mintaxamount.get().doubleValue(), maxtaxamount.get().doubleValue()),
                List.of(mintaxrate.get().doubleValue(), maxtaxrate.get().doubleValue()),
                List.of(mintotalamount.get().doubleValue(), maxtotalamount.get().doubleValue())
        );
    }

    public OrderDetailsResponse getById(Long id) {
        List<OrderDetailsRow> rows = jdbcTemplate.query(
                """
                        SELECT o.id,
                               o.ts,
                               o.longitude,
                               o.latitude,
                               o.subtotal,
                               od.total_amount,
                               od.composite_tax_rate,
                               od.tax_amount,
                               j.type,
                               j.name,
                               j.state_rate,
                               j.county_rate,
                               j.city_rate,
                               j.special_rate
                        FROM orders o
                        JOIN order_details od ON o.id = od.order_id
                        JOIN tax_jurisdictions j ON j.id = ANY (od.jurisdictions)
                        WHERE o.id = ?
                        ORDER BY CASE WHEN j.type = 'CITY' THEN 0 ELSE 1 END, j.id
                        """,
                (rs, rowNum) -> new OrderDetailsRow(
                        rs.getLong("id"),
                        formatTimestamp(rs.getTimestamp("ts")),
                        rs.getDouble("longitude"),
                        rs.getDouble("latitude"),
                        rs.getBigDecimal("subtotal").doubleValue(),
                        rs.getBigDecimal("total_amount").doubleValue(),
                        rs.getBigDecimal("composite_tax_rate").doubleValue(),
                        rs.getBigDecimal("tax_amount").doubleValue(),
                        rs.getString("type"),
                        rs.getString("name"),
                        rs.getBigDecimal("state_rate").doubleValue(),
                        rs.getBigDecimal("county_rate").doubleValue(),
                        rs.getBigDecimal("city_rate").doubleValue(),
                        rs.getBigDecimal("special_rate").doubleValue()
                ),
                id
        );

        if (rows.isEmpty()) {
            return null;
        }

        OrderDetailsRow prioritizedRow = rows.getFirst();
        String county = null;
        String city = null;
        String special = null;
        for (OrderDetailsRow row : rows) {
            if ("COUNTY".equals(row.type())) {
                county = row.name();
            }
            if ("CITY".equals(row.type())) {
                city = row.name();
            }
            if (row.specialRate != null && row.specialRate != 0.0) {
                special = MCTD_NAME;
            }
        }

        return new OrderDetailsResponse(
                prioritizedRow.id(),
                prioritizedRow.timestamp(),
                prioritizedRow.longitude(),
                prioritizedRow.latitude(),
                prioritizedRow.subTotal(),
                prioritizedRow.totalAmount(),
                prioritizedRow.taxRate(),
                prioritizedRow.stateRate(),
                prioritizedRow.countyRate(),
                prioritizedRow.cityRate(),
                prioritizedRow.specialRate(),
                prioritizedRow.taxAmount(),
                COUNTRY_NAME,
                STATE_NAME,
                county,
                city,
                special
        );
    }

    public PageData<OrderResponse> findOrders(OrdersFilter filter, int page, int perPage) {
        int safePerPage = Math.max(perPage, 1);
        int safePage = Math.max(page, 0);
        int offset = safePage * safePerPage;

        String normalizedQuery = filter.query() == null ? "" : filter.query().trim();
        String queryPattern = "%" + normalizedQuery + "%";
        StringBuilder baseQuery = new StringBuilder("""
                FROM orders o
                JOIN order_details od ON o.id = od.order_id
                WHERE (? = '' OR CAST(o.id AS TEXT) ILIKE ?)
                """);

        List<Object> params = new ArrayList<>();
        params.add(normalizedQuery);
        params.add(queryPattern);

        if (filter.date().getFrom() != null) {
            baseQuery.append("\n  AND o.ts >= ?");
            params.add(Timestamp.valueOf(filter.date().getFrom().atStartOfDay()));
        }

        if (filter.date().getTo() != null) {
            baseQuery.append("\n  AND o.ts <= ?");
            params.add(Timestamp.valueOf(filter.date().getTo().atTime(LocalTime.MAX)));
        }

        baseQuery.append("""
                
                  AND o.subtotal BETWEEN ? AND ?
                  AND od.tax_amount BETWEEN ? AND ?
                  AND od.composite_tax_rate BETWEEN ? AND ?
                  AND od.total_amount BETWEEN ? AND ?
                """);

        params.add(BigDecimal.valueOf(filter.subTotal().getFrom()));
        params.add(BigDecimal.valueOf(filter.subTotal().getTo()));
        params.add(BigDecimal.valueOf(filter.taxAmount().getFrom()));
        params.add(BigDecimal.valueOf(filter.taxAmount().getTo()));
        params.add(BigDecimal.valueOf(filter.taxRate().getFrom()));
        params.add(BigDecimal.valueOf(filter.taxRate().getTo()));
        params.add(BigDecimal.valueOf(filter.totalAmount().getFrom()));
        params.add(BigDecimal.valueOf(filter.totalAmount().getTo()));

        Integer totalElements = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + baseQuery,
                Integer.class,
                params.toArray()
        );

        List<OrderResponse> data = jdbcTemplate.query(
                """
                        SELECT o.id,
                               o.ts,
                               o.latitude,
                               o.longitude,
                               o.subtotal,
                               od.composite_tax_rate,
                               od.tax_amount,
                               od.total_amount
                        """ + baseQuery + """
                        ORDER BY o.id
                        LIMIT ? OFFSET ?
                        """,
                (rs, rowNum) -> new OrderResponse(
                        rs.getLong("id"),
                        formatTimestamp(rs.getTimestamp("ts")),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getBigDecimal("subtotal").doubleValue(),
                        rs.getBigDecimal("composite_tax_rate").doubleValue(),
                        rs.getBigDecimal("tax_amount").doubleValue(),
                        rs.getBigDecimal("total_amount").doubleValue()
                ),
                appendPagination(params, safePerPage, offset)
        );

        PageData<OrderResponse> result = new PageData<>();
        result.setData(data);
        result.setTotalElements(totalElements == null ? 0 : totalElements);
        return result;
    }

    private Object[] appendPagination(List<Object> params, int limit, int offset) {
        List<Object> paginatedParams = new ArrayList<>(params);
        paginatedParams.add(limit);
        paginatedParams.add(offset);
        return paginatedParams.toArray();
    }

    private String formatTimestamp(Timestamp timestamp) {
        LocalDateTime value = timestamp.toLocalDateTime();
        return ORDER_TIMESTAMP_FORMAT.format(value);
    }

    private record OrderDetailsRow(
            Long id,
            String timestamp,
            Double longitude,
            Double latitude,
            Double subTotal,
            Double totalAmount,
            Double taxRate,
            Double taxAmount,
            String type,
            String name,
            Double stateRate,
            Double countyRate,
            Double cityRate,
            Double specialRate
    ) {
    }
}
