# IWK Backoffice

Backend service for importing, storing, and querying taxable orders in New York State only.

The application is built with Spring Boot, uses PostgreSQL + PostGIS for spatial lookups, and calculates order tax totals from bundled jurisdiction geometry data loaded during Flyway migrations.

All current business rules assume New York State coverage only, and timestamps are interpreted as New York local time.

## Links

[Demo URL](https://iwk-backoffice.vercel.app)

[Frontend repository](https://github.com/BadOfficer/iwk-backoffice-client)

## What This Service Does

This service supports a backoffice workflow around order ingestion and tax inspection:

- Creates individual orders from API requests
- Imports orders from CSV files in batches
- Resolves tax jurisdiction coverage from longitude/latitude coordinates
- Calculates composite tax rate, tax amount, and total amount
- Exposes paginated order search with numeric/date filters
- Returns order details and map coordinates for UI consumption
- Seeds jurisdiction geometry data from the bundled `jurisdictions.json` resource

## Technology Stack

- Java 25
- Spring Boot 4.0.3
- Spring Web MVC
- Spring Data JDBC
- Flyway
- PostgreSQL
- PostGIS
- Apache Commons CSV
- Gradle Kotlin DSL
- Docker / Docker Compose

## Project Structure

```text
.
├── src/main/java/org/restless/iwkbackoffice
│   ├── config/                 # CORS configuration
│   ├── domain/                 # Request, response, entity, and tax models
│   ├── exception/              # Custom exceptions
│   ├── repository/             # JDBC-based persistence and querying
│   ├── resource/               # REST controllers and exception handlers
│   ├── service/                # Import, filtering, tax, and order logic
│   └── util/                   # Parsing helpers and range type
├── src/main/java/db/migration   # Java-based Flyway migration for geo import
├── src/main/resources
│   ├── application.yaml         # Application and datasource config
│   ├── db/migration             # SQL Flyway migrations
│   └── jurisdictions.json       # Bundled tax jurisdiction geo data
├── src/test                     # Unit / slice tests
├── Dockerfile
├── docker-compose.yml
├── .env.example
└── build.gradle.kts
```

## Runtime Requirements

To run locally without Docker for the app process, you need:

- JDK 25
- A PostgreSQL instance with the PostGIS extension available
- Network access from the app to the database

To run the database locally via Docker Compose, you need:

- Docker
- Docker Compose

## Configuration

The application loads configuration from `application.yaml` and imports environment variables from a local `.env` file via:

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

### Required Environment Variables

These variables are used to build the datasource URL:

```env
PG_HOST=
PG_PORT=
PG_DB=
PG_DBUSER=
PG_DBPASS=
```

### Optional Environment Variables

```env
FRONTEND_URL=http://localhost:5173
```

`FRONTEND_URL` controls the single allowed CORS origin. If omitted, the application defaults to `http://localhost:5173`.

### Example Local `.env`

```env
PG_HOST=localhost
PG_PORT=5432
PG_DB=backoffice_db
PG_DBUSER=backoffice_user
PG_DBPASS=change_me
FRONTEND_URL=http://localhost:5173
```

## Local Development

### 1. Start PostgreSQL + PostGIS

The repository includes a Compose file for local database bootstrapping:

```bash
docker compose up -d
```

This starts:

- `postgres` using `postgis/postgis:15-3.3`
- container port `5432` mapped to `${PG_PORT}` from `.env`
- a named volume `pg_data` for persistence

### 2. Verify or Create `.env`

Copy `.env.example` to `.env` and fill in real values if needed:

```bash
cp .env.example .env
```

At minimum, `.env` must include the PostgreSQL connection variables. For browser-based frontend integration, also set `FRONTEND_URL`.

### 3. Run the Application

```bash
./gradlew bootRun
```

The service will:

- connect to PostgreSQL
- run Flyway migrations automatically
- ensure PostGIS is enabled
- create tables
- import bundled jurisdiction geometry data if the `tax_jurisdictions` table is empty

### 4. Run Tests

```bash
./gradlew test
```

## Docker

### Build the Application Image

```bash
docker build -t iwk-backoffice .
```

The `Dockerfile` uses a multi-stage build:

1. Builds the Spring Boot jar with Gradle in an Eclipse Temurin JDK 25 image
2. Copies the jar into a smaller runtime image
3. Runs the service as a non-root `spring` user

### Run the Container

```bash
docker run --rm \
  -p 8080:8080 \
  --env-file .env \
  -e FRONTEND_URL=http://localhost:5173 \
  iwk-backoffice
```

The app expects the database described by `.env` to already be reachable.

## Database Initialization

Flyway applies these migrations on startup:

### `V1__init.sql`

Creates the `orders` table:

- `id`
- `longitude`
- `latitude`
- `ts`
- `subtotal`

### `V2__import-geo-data.sql`

- enables the PostGIS extension
- creates the `tax_jurisdictions` table
- creates a GiST index on jurisdiction geometry

### `V3__create-order-details-table.sql`

Creates `order_details`, which stores:

- linked `order_id`
- matched jurisdiction ids
- composite tax rate
- tax amount
- total amount

### `V4__ImportGeoData.java`

Loads `src/main/resources/jurisdictions.json` and inserts jurisdiction records if none already exist.

Important details:

- data is seeded by Flyway in Java, not by mounting files into PostgreSQL
- polygon geometries are normalized to multipolygons when needed
- startup import is skipped if `tax_jurisdictions` already contains rows

## Tax Calculation Rules

Tax calculation is performed in `OrderTaxCalculator`.

Current behavior:

- all matching jurisdictions for a point are loaded
- city jurisdictions are prioritized ahead of non-city records
- the first prioritized jurisdiction determines the effective tax rates used
- composite tax rate is calculated as:

```text
state_rate + county_rate + city_rate + special_rate
```

- tax amount is calculated as:

```text
subTotal * compositeTaxRate / 100
```

- tax amount is rounded to 2 decimal places using `HALF_UP`
- total amount is:

```text
subTotal + taxAmount
```

## Important Functional Assumptions

These assumptions are currently built into the codebase:

- The system is intended to operate only for orders inside New York State.
- Orders outside New York State are out of scope for this service.
- Coordinates outside covered NY tax jurisdictions are rejected for single-order creation.
- Timestamps are interpreted as New York local time.
- Different time zones are not supported.
- The application uses server-local `LocalDateTime.now()` when creating an order through the API.
- CSV `id` values are ignored; database-generated ids are used instead.
- Jurisdiction data comes from the bundled `jurisdictions.json` classpath resource.
- CORS allows exactly one configured frontend origin.

## API Overview

Base path: `/orders`

There is no authentication or authorization layer in the current codebase.

### 1. Import Orders from CSV

`POST /orders/import`

Consumes:

- `multipart/form-data`

Expected form field:

- `file`

Response shape:

```json
{
  "processed": 1000,
  "saved": 975,
  "rejected": 25,
  "success": true,
  "errorMessage": null
}
```

Behavior notes:

- CSV rows are parsed in batches of 500
- rows outside New York State coverage are ignored and import continues for the remaining rows
- rows that do not match any jurisdiction never reach persistence
- if the file is empty, the response is successful at the HTTP level but returns `success: false`
- if parsing or import fails, the response returns `success: false` with a generic message

#### CSV Format

The importer expects a header row with these columns:

```csv
id,longitude,latitude,timestamp,subTotal
```

Example:

```csv
id,longitude,latitude,timestamp,subTotal
1,-73.9857,40.7484,2026-01-15T10:30:00,100.00
2,-73.9352,40.7306,2026-01-16T12:45:00,55.25
```

Parsing rules:

- `id` is ignored
- `longitude` is required and must parse as a number
- `latitude` is required and must parse as a number
- `timestamp` is required and must parse into a `LocalDateTime`
- `timestamp` values are interpreted as New York local time
- `subTotal` is required and must parse as a decimal number

Example request:

```bash
curl -X POST http://localhost:8080/orders/import \
  -F "file=@orders.csv"
```

### 2. Create a Single Order

`POST /orders`

Consumes:

- `application/json`

Request body:

```json
{
  "longitude": -73.9857,
  "latitude": 40.7484,
  "subTotal": 100.00
}
```

Validation rules:

- `longitude` is required
- `latitude` is required
- `subTotal` is required
- `subTotal` must be greater than `0.0`
- `longitude` must be between `-180` and `180`
- `latitude` must be between `-90` and `90`
- coordinates must map to a covered NY jurisdiction

Success response:

```json
{
  "id": 123
}
```

Example request:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "longitude": -73.9857,
    "latitude": 40.7484,
    "subTotal": 100.00
  }'
```

### 3. Get Filter Limits

`GET /orders/filterlimits`

Returns min/max values for frontend filter controls.

Example response:

```json
{
  "subTotal": [1.0, 999.99],
  "taxAmount": [0.08, 88.12],
  "taxRate": [4.0, 8.875],
  "totalAmount": [1.08, 1088.11]
}
```

If no rows exist, the repository currently returns `-1` sentinel values for each bound.

Example request:

```bash
curl http://localhost:8080/orders/filterlimits
```

### 4. Search Orders

`GET /orders`

Required query parameters:

- `page`
- `perPage`
- `query`
- `date`
- `subTotal`
- `taxAmount`
- `taxRate`
- `totalAmount`

Example request:

```bash
curl "http://localhost:8080/orders?page=0&perPage=20&query=&date=2026-01-01,2026-01-31&subTotal=0.01,1000.00&taxAmount=0.00,100.00&taxRate=0.00,10.00&totalAmount=0.01,1100.00"
```

Response shape:

```json
{
  "data": [
    {
      "id": 123,
      "timestamp": "01-15-2026 10:30:00",
      "latitude": 40.7484,
      "longitude": -73.9857,
      "subTotal": 100.0,
      "taxRate": 8.875,
      "taxAmount": 8.88,
      "totalAmount": 108.88
    }
  ],
  "totalElements": 1
}
```

#### Filter Semantics

`query`

- trimmed string
- matched against `CAST(o.id AS TEXT) ILIKE ?`
- an empty string disables id filtering

`date`

- must be in the format `from,to`
- each bound uses `YYYY-MM-DD`
- lower bound may be blank
- upper bound may be blank

Examples:

- `2026-01-01,2026-01-31`
- `2026-01-01,`
- `,2026-01-31`

`subTotal`, `taxAmount`, `taxRate`, `totalAmount`

- each must be in the format `from,to`
- both numeric bounds are required
- values are parsed as decimals

Examples:

- `0.01,100.00`
- `4.00,8.88`

Pagination rules:

- `page >= 0`
- `perPage >= 1`

### 5. Get Order Details

`GET /orders/{id}`

Example response:

```json
{
  "id": 123,
  "timestamp": "01-15-2026 10:30:00",
  "longitude": -73.9857,
  "latitude": 40.7484,
  "subTotal": 100.0,
  "totalAmount": 108.88,
  "taxRate": 8.875,
  "stateRate": 4.0,
  "countyRate": 1.5,
  "cityRate": 4.0,
  "specialRate": 0.375,
  "taxAmount": 8.88,
  "country": "USA",
  "state": "New York",
  "county": "New York",
  "city": "New York",
  "special": "MCTD (Metropolitan Commuter Transportation District)"
}
```

Implementation notes:

- country is hard-coded to `USA`
- state is hard-coded to `New York`
- special jurisdiction name is derived from a hard-coded label when `specialRate` is non-zero
- city rows are ordered first when building the response

Example request:

```bash
curl http://localhost:8080/orders/123
```

### 6. Get Order Map Coordinates

`GET /orders/map`

Example response:

```json
[
  {
    "id": 123,
    "longitude": -73.9857,
    "latitude": 40.7484
  }
]
```

Example request:

```bash
curl http://localhost:8080/orders/map
```

## Error Handling

The service returns JSON error responses from a global exception handler.

Response shape:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "longitude must be between -180 and 180"
}
```

Current mapped cases:

- `400 Bad Request`
  - invalid custom business rules
  - invalid bean validation payloads
  - invalid or missing request parameters
- `404 Not Found`
  - order id not found
- `500 Internal Server Error`
  - unhandled exceptions

Example `400` causes:

- malformed range filters
- missing required query parameters
- invalid coordinate bounds
- order created outside NY coverage
- invalid JSON request body fields

## CORS

CORS is configured globally for all routes.

Allowed origin:

- value of `app.frontend-url`
- defaults to `http://localhost:5173`

Allowed methods:

- `GET`
- `POST`
- `PUT`
- `DELETE`
- `OPTIONS`

Credentials are enabled.

## Testing

The current test suite includes:

- tax calculation behavior
- filter parsing behavior
- application context startup

Main test files:

- `src/test/java/org/restless/iwkbackoffice/service/OrderTaxCalculatorTest.java`
- `src/test/java/org/restless/iwkbackoffice/service/OrdersFilterParserTest.java`
- `src/test/java/org/restless/iwkbackoffice/IwkBackofficeApplicationTests.java`

## Operational Notes

- Spatial matching uses `ST_Covers` against jurisdiction geometries in EPSG:4326.
- Import batching uses sequence preallocation and JDBC batch inserts.
- During CSV import, rows outside New York State coverage are skipped and the rest of the import continues.
- The order listing endpoint sorts by `o.id`.
- Order timestamps are formatted as `MM-dd-yyyy HH:mm:ss` in API responses.
- Single-order creation stores the current server timestamp; imported orders store the CSV timestamp interpreted as New York local time.

## Known Limitations

- No authentication or authorization
- No update or delete endpoints for orders
- No health endpoint documented in this repository
- No OpenAPI / Swagger definition
- Search `query` only matches order id text
- Numeric filters require both bounds
- Import error reporting is aggregate, not row-level
- Timezone-aware timestamps are not used; the system assumes New York local time semantics
- The application logic is specific to New York tax jurisdiction coverage

## Useful Commands

Start database:

```bash
docker compose up -d
```

Stop database:

```bash
docker compose down
```

Run app:

```bash
./gradlew bootRun
```

Run tests:

```bash
./gradlew test
```

Build jar:

```bash
./gradlew bootJar
```
