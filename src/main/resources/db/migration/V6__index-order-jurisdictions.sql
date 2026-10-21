create index if not exists idx_order_details_jurisdictions on order_details using GIN (jurisdictions);
