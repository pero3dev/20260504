-- A5 follow-up¹⁵: User lifecycle 列追加。
--
-- 既存の users 行は全て ACTIVE 扱い(default で埋める)。 deactivated_at は nullable。
-- DEACTIVATED user は AuthenticateService / ExchangeFederatedTokenService が
-- 認証失敗 (AuthenticationFailedException) で弾く。 既発行 access token は TTL 切れまで有効。

ALTER TABLE users
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DEACTIVATED'));

ALTER TABLE users
    ADD COLUMN deactivated_at TIMESTAMPTZ;

CREATE INDEX users_status_idx ON users (status);
