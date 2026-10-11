-- Install the composite index first: it also supports the existing order foreign key.
ALTER TABLE payment_record ADD UNIQUE KEY uk_payment_request (order_id, request_key);
ALTER TABLE payment_record DROP INDEX uk_payment_order;
