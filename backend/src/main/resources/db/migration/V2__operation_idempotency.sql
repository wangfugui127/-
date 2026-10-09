ALTER TABLE operation_log ADD UNIQUE KEY uk_operation_request (actor_id, action, request_key);
