create table if not exists order_details
(
    order_id           bigint primary key references orders (id),
    jurisdictions      bigint[]       not null,
    composite_tax_rate numeric(8, 5)  not null,
    tax_amount         numeric(12, 2) not null,
    total_amount       numeric(12, 2) not null
);
