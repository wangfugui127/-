-- Dedicated per-user creation gate avoids holding mall_user exclusively while waiting
-- for products: cart inserts also reference mall_user through a foreign key.
CREATE TABLE order_creation_lock (
  user_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_creation_lock_user FOREIGN KEY (user_id) REFERENCES mall_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;
