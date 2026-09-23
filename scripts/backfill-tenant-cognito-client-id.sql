-- P0 #3 JWT → Tenant Binding: backfill cognito_client_id BEFORE deploying enforcement.
-- Fail-closed: unmapped (NULL) tenants receive HTTP 403 once binding is live.
--
-- Rollout order:
--   1) Ensure column exists (Hibernate ddl-auto=update or ALTER below)
--   2) Backfill EVERY tenant row
--   3) Run verification — must return ZERO rows
--   4) Deploy application build that enforces TenantClientBindingService

-- Optional if column not yet present:
-- ALTER TABLE tenant ADD COLUMN cognito_client_id VARCHAR(255) NULL;
-- CREATE UNIQUE INDEX uk_tenant_cognito_client_id ON tenant (cognito_client_id);

-- Replace placeholders with real Cognito M2M app client IDs (one unique client per tenant).
UPDATE tenant SET cognito_client_id = 'COGNITO_CLIENT_ID_FOR_TENANT_A' WHERE tenant_code = 'TENANT_A';
UPDATE tenant SET cognito_client_id = 'COGNITO_CLIENT_ID_FOR_TENANT_B' WHERE tenant_code = 'TENANT_B';
-- UPDATE tenant SET cognito_client_id = '...' WHERE tenant_code = '...';

-- Verification: must be empty before enabling enforcement
SELECT id, tenant_code, name, status, cognito_client_id
FROM tenant
WHERE cognito_client_id IS NULL OR cognito_client_id = '';

-- Optional: confirm unique mapping
SELECT cognito_client_id, COUNT(*) AS tenant_count
FROM tenant
WHERE cognito_client_id IS NOT NULL
GROUP BY cognito_client_id
HAVING COUNT(*) > 1;
