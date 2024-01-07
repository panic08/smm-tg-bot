CREATE TABLE IF NOT EXISTS replenishments_table(
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    timestamp BIGINT NOT NULL,

    FOREIGN KEY (user_id) REFERENCES users_table (id)
);