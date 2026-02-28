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

with source_json as (select pg_read_file('/docker-entrypoint-initdb.d/jurisdictions.json')::jsonb as doc),
     jurisdictions as (select jsonb_array_elements(doc -> 'jurisdictions') as jurisdiction from source_json),
     normalized_jurisdictions as (select jurisdiction,
                                         case
                                             when jurisdiction -> 'geometry' ->> 'type' = 'Polygon'
                                                 and
                                                  jsonb_typeof(jurisdiction -> 'geometry' -> 'coordinates' -> 0 -> 0 -> 0) =
                                                  'array'
                                                 then jsonb_set(jurisdiction -> 'geometry', '{type}',
                                                                '"MultiPolygon"'::jsonb)
                                             else jurisdiction -> 'geometry'
                                             end as geometry_json
                                  from jurisdictions)
insert
into tax_jurisdictions (name, type, state_rate, county_rate, city_rate, special_rate, coordinates)
select jurisdiction ->> 'name'                                              as jname,
       jurisdiction ->> 'type'                                              as jtype,
       (jurisdiction ->> 'state_rate')::numeric(6, 5)                       as jstate_rate,
       (jurisdiction ->> 'county_rate')::numeric(6, 5)                      as jcounty_rate,
       (jurisdiction ->> 'city_rate')::numeric(6, 5)                        as jcity_rate,
       (jurisdiction ->> 'special_rate')::numeric(6, 5)                     as jspecial_rate,
       st_multi(st_force2d(st_setsrid(st_geomfromgeojson(geometry_json::text),
                                      4326)))::geometry(MultiPolygon, 4326) as jcoordinates
from normalized_jurisdictions
on conflict (id) do update
    set name         = excluded.name,
        type         = excluded.type,
        state_rate   = excluded.state_rate,
        county_rate  = excluded.county_rate,
        city_rate    = excluded.city_rate,
        special_rate = excluded.special_rate,
        coordinates  = excluded.coordinates;

create index if not exists idx_tax_jurisdictions_geom on tax_jurisdictions using GIST (coordinates);
