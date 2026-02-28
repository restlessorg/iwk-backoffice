create extension if not exists postgis;

create table if not exists tax_jurisdictions
(
    id           bigserial primary key,
    name         varchar(255),
    type         varchar(255),
    state_rate   numeric(6, 5),
    county_rate  numeric(6, 5),
    city_rate    numeric(6, 5),
    special_rate numeric(6, 5),
    coordinates  geometry(MultiPolygon, 4326)
);

create index if not exists idx_tax_jurisdictions_geom on tax_jurisdictions using GIST (coordinates);
