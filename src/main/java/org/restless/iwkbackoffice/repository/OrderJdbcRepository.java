package org.restless.iwkbackoffice.repository;

import lombok.RequiredArgsConstructor;
import org.restless.iwkbackoffice.domain.entity.OrderEntity;
import org.restless.iwkbackoffice.domain.request.OrdersFilter;
import org.restless.iwkbackoffice.domain.response.FilterLimits;
import org.restless.iwkbackoffice.domain.response.OrderDetailsResponse;
import org.restless.iwkbackoffice.domain.response.OrderLocation;
import org.restless.iwkbackoffice.domain.response.OrderResponse;
import org.restless.iwkbackoffice.domain.response.PageData;
import org.restless.iwkbackoffice.domain.tax.CalculatedOrderTaxes;
import org.restless.iwkbackoffice.domain.tax.JurisdictionDetails;
import org.restless.iwkbackoffice.domain.tax.JurisdictionType;
import org.restless.iwkbackoffice.exception.BadRequestException;
import org.restless.iwkbackoffice.service.OrderTaxCalculator;
import org.restless.iwkbackoffice.util.Range;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderJdbcRepository {
    private final JdbcTemplate jdbcTemplate;
    private final OrderTaxCalculator orderTaxCalculator;

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

        List<JurisdictionDetails> jurisdictionDetails = new ArrayList<>();
        jdbcTemplate.query(sql, rs -> {
            jurisdictionDetails.add(mapJurisdictionDetails(rs));
        }, order.getId(), order.getLongitude(), order.getLatitude());

        if (jurisdictionDetails.isEmpty()) {
            throw new BadRequestException("Order was placed outside of NY state");
        }

        jdbcTemplate.update(
                "INSERT INTO orders (id,longitude,latitude,ts,subtotal,source) VALUES (?,?,?,?,?,?)",
                order.getId(),
                order.getLongitude(),
                order.getLatitude(),
                Timestamp.valueOf(order.getTs()),
                order.getSubtotal(),
                order.getSource()
        );

        CalculatedOrderTaxes taxes = orderTaxCalculator.calculate(order.getSubtotal(), jurisdictionDetails);

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO order_details (order_id,jurisdictions,composite_tax_rate,tax_amount,total_amount) VALUES (?,?,?,?,?)"
            );
            ps.setLong(1, order.getId());
            ps.setArray(2, connection.createArrayOf("bigint", taxes.jurisdictionIds().toArray(Long[]::new)));
            ps.setBigDecimal(3, taxes.compositeTaxRate());
            ps.setBigDecimal(4, taxes.taxAmount());
            ps.setBigDecimal(5, taxes.totalAmount());
            return ps;
        });

        return order.getId();
    }

    public FilterLimits getFilterLimits() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(MIN(o.subtotal), -1)            AS minsubtotal,
                               COALESCE(MAX(o.subtotal), -1)            AS maxsubtotal,
                               COALESCE(MIN(od.tax_amount), -1)         AS mintaxamount,
                               COALESCE(MAX(od.tax_amount), -1)         AS maxtaxamount,
                               COALESCE(MIN(od.composite_tax_rate), -1) AS mintaxrate,
                               COALESCE(MAX(od.composite_tax_rate), -1) AS maxtaxrate,
                               COALESCE(MIN(od.total_amount), -1)       AS mintotalamount,
                               COALESCE(MAX(od.total_amount), -1)       AS maxtotalamount
                        FROM orders o
                        JOIN order_details od ON o.id = od.order_id
                        """,
                (rs, rowNum) -> new FilterLimits(
                        List.of(rs.getBigDecimal("minsubtotal").doubleValue(), rs.getBigDecimal("maxsubtotal").doubleValue()),
                        List.of(rs.getBigDecimal("mintaxamount").doubleValue(), rs.getBigDecimal("maxtaxamount").doubleValue()),
                        List.of(rs.getBigDecimal("mintaxrate").doubleValue(), rs.getBigDecimal("maxtaxrate").doubleValue()),
                        List.of(rs.getBigDecimal("mintotalamount").doubleValue(), rs.getBigDecimal("maxtotalamount").doubleValue())
                )
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
                               o.source,
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
                        rs.getBigDecimal("special_rate").doubleValue(),
                        rs.getString("source")
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
            if (row.specialRate() != null && row.specialRate() != 0.0) {
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
                special,
                prioritizedRow.source()
        );
    }

    public List<OrderLocation> getOrdersLocations() {
        return jdbcTemplate.query(
                """
                        SELECT o.id,
                               o.longitude,
                               o.latitude
                        FROM orders o
                        """,
                (rs, rowNum) -> new OrderLocation(
                        rs.getLong("id"),
                        rs.getDouble("longitude"),
                        rs.getDouble("latitude")
                )
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

        if (filter.date().from() != null) {
            baseQuery.append("\n  AND o.ts >= ?");
            params.add(Timestamp.valueOf(filter.date().from().atStartOfDay()));
        }

        if (filter.date().to() != null) {
            baseQuery.append("\n  AND o.ts <= ?");
            params.add(Timestamp.valueOf(filter.date().to().atTime(LocalTime.MAX)));
        }

        appendBetween(baseQuery, params, "o.subtotal", filter.subTotal());
        appendBetween(baseQuery, params, "od.tax_amount", filter.taxAmount());
        appendBetween(baseQuery, params, "od.composite_tax_rate", filter.taxRate());
        appendBetween(baseQuery, params, "od.total_amount", filter.totalAmount());
        baseQuery.append("\n");

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

        return new PageData<>(data, totalElements == null ? 0 : totalElements);
    }

    private Object[] appendPagination(List<Object> params, int limit, int offset) {
        List<Object> paginatedParams = new ArrayList<>(params);
        paginatedParams.add(limit);
        paginatedParams.add(offset);
        return paginatedParams.toArray();
    }

    private void appendBetween(StringBuilder query, List<Object> params, String column, Range<BigDecimal> range) {
        if (range.from() == null || range.to() == null) {
            return;
        }
        query.append("\n  AND ").append(column).append(" BETWEEN ? AND ?");
        params.add(range.from());
        params.add(range.to());
    }

    private String formatTimestamp(Timestamp timestamp) {
        LocalDateTime value = timestamp.toLocalDateTime();
        return ORDER_TIMESTAMP_FORMAT.format(value);
    }

    private JurisdictionDetails mapJurisdictionDetails(ResultSet rs) throws SQLException {
        return new JurisdictionDetails(
                rs.getObject("id", Long.class),
                JurisdictionType.valueOf(rs.getString("type")),
                rs.getBigDecimal("state_rate"),
                rs.getBigDecimal("county_rate"),
                rs.getBigDecimal("city_rate"),
                rs.getBigDecimal("special_rate")
        );
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
            Double specialRate,
            String source
    ) {
    }
}
