-- Create cards table
CREATE TABLE cards (
                       id UUID PRIMARY KEY NOT NULL,
                       cardholder_name VARCHAR(255) NOT NULL,
                       balance DECIMAL(19, 2) NOT NULL,
                       status VARCHAR(50) NOT NULL,
                       created_at TIMESTAMP NOT NULL,
                       version BIGINT NOT NULL DEFAULT 0,
                       CONSTRAINT check_balance_non_negative CHECK (balance >= 0)
);

-- Create transactions table
CREATE TABLE transactions (
                              id UUID PRIMARY KEY NOT NULL,
                              card_id UUID NOT NULL,
                              type VARCHAR(50) NOT NULL,
                              amount DECIMAL(19, 2) NOT NULL,
                              status VARCHAR(50) NOT NULL,
                              idempotency_key VARCHAR(255),
                              created_at TIMESTAMP NOT NULL,
                              failure_reason VARCHAR(1024),
                              CONSTRAINT fk_transactions_card_id FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE
);

-- Create unique constraint on (card_id, type, idempotency_key) for idempotency
-- This prevents duplicate (card_id, type, idempotency_key) combinations entirely
CREATE UNIQUE INDEX uk_transactions_idempotency
    ON transactions(card_id, type, idempotency_key);

-- Create index on (card_id, created_at DESC) for efficient transaction history retrieval
CREATE INDEX idx_transactions_card_history
    ON transactions(card_id, created_at DESC);

-- Create index on idempotency_key for fast lookup during financial operations
CREATE INDEX idx_transactions_idempotency_lookup
    ON transactions(card_id, type, idempotency_key);