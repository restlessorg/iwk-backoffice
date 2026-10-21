alter table orders
    add column if not exists source varchar(255) default null;
