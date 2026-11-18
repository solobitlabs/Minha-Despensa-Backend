-- ==============================================================================
-- Minha Despensa Backend — Admin Console, Global Product Media & Audit V4
-- ==============================================================================

ALTER TABLE global_products
    ADD COLUMN IF NOT EXISTS image_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_global_products_is_verified ON global_products(is_verified);

CREATE TABLE IF NOT EXISTS global_product_media (
    ean VARCHAR(32) PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    image_bytes BYTEA NOT NULL,
    updated_at BIGINT NOT NULL
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS blocked_reason VARCHAR(255);

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    admin_username VARCHAR(80) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    details TEXT,
    client_ip VARCHAR(64),
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created_at ON admin_audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_target ON admin_audit_logs(target_type, target_id);
