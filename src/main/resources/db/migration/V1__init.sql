create table if not exists orders
(
    id        bigserial primary key,
    longitude double precision not null,
    latitude  double precision not null,
    ts        timestamp        not null,
    subtotal  numeric(12, 2)   not null
);
