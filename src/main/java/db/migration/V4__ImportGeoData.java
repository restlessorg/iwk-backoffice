package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public class V4__ImportGeoData extends BaseJavaMigration {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (hasJurisdictions(connection)) {
            return;
        }

        JsonNode jurisdictions = loadJurisdictions();
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into tax_jurisdictions (
                    name,
                    type,
                    state_rate,
                    county_rate,
                    city_rate,
                    special_rate,
                    coordinates
                ) values (
                    ?, ?, ?, ?, ?, ?,
                    st_multi(st_force2d(st_setsrid(st_geomfromgeojson(?), 4326)))::geometry(MultiPolygon, 4326)
                )
                """)) {
            for (JsonNode jurisdiction : jurisdictions) {
                statement.setString(1, textValue(jurisdiction, "name"));
                statement.setString(2, textValue(jurisdiction, "type"));
                setNullableNumeric(statement, 3, decimalValue(jurisdiction, "state_rate"));
                setNullableNumeric(statement, 4, decimalValue(jurisdiction, "county_rate"));
                setNullableNumeric(statement, 5, decimalValue(jurisdiction, "city_rate"));
                setNullableNumeric(statement, 6, decimalValue(jurisdiction, "special_rate"));
                statement.setString(7, normalizeGeometry(jurisdiction.path("geometry")).toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static boolean hasJurisdictions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select exists(select 1 from tax_jurisdictions)");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getBoolean(1);
        }
    }

    private static JsonNode loadJurisdictions() throws IOException {
        try (InputStream stream = V4__ImportGeoData.class.getClassLoader().getResourceAsStream("jurisdictions.json")) {
            if (stream == null) {
                throw new IOException("Missing jurisdictions.json classpath resource");
            }
            return OBJECT_MAPPER.readTree(stream).path("jurisdictions");
        }
    }

    private static String textValue(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? null : field.asText();
    }

    private static BigDecimal decimalValue(JsonNode node, String fieldName) {
        String value = textValue(node, fieldName);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private static void setNullableNumeric(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
            return;
        }
        statement.setBigDecimal(index, value);
    }

    private static JsonNode normalizeGeometry(JsonNode geometry) {
        if (!"Polygon".equals(geometry.path("type").asText())) {
            return geometry;
        }

        JsonNode firstCoordinate = geometry.path("coordinates").path(0).path(0).path(0);
        if (!firstCoordinate.isArray()) {
            return geometry;
        }

        JsonNode normalized = geometry.deepCopy();
        ((ObjectNode) normalized).put("type", "MultiPolygon");
        return normalized;
    }
}
