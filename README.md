# Assumptions

- The system is intended to operate in NY state only. Different timezones aren't supported.
- An `id` column in csv file is ignored, orders are stored using the database-generated ID.
- Tax jurisdiction geo data is seeded by Flyway from the bundled `jurisdictions.json` classpath resource, not by PostgreSQL reading a mounted file.
