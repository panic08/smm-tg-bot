CREATE TABLE IF NOT EXISTS users_table(
    id SERIAL PRIMARY KEY,
    telegram_chat_id BIGINT NOT NULL,
    balance DOUBLE PRECISION NOT NULL,
    role VARCHAR(255) NOT NULL,
    privilege VARCHAR(255) NOT NULL,
    registered_at BIGINT NOT NULL
);