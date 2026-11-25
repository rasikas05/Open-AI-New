-- Production DDL for Bedrock prompt-cache metrics on request_logs.
-- Run explicitly in production; do not rely solely on hibernate ddl-auto=update.
-- Columns are nullable so missing usage details are distinguishable from confirmed zeros
-- when paired with cache_metrics_present = 0/NULL vs 1.

ALTER TABLE request_logs
    ADD COLUMN cache_write_tokens INT NULL COMMENT 'Bedrock usage.prompt_tokens_details.cache_write_tokens';

ALTER TABLE request_logs
    ADD COLUMN cached_tokens INT NULL COMMENT 'Bedrock usage.prompt_tokens_details.cached_tokens';

ALTER TABLE request_logs
    ADD COLUMN cache_metrics_present BIT(1) NULL COMMENT '1 when prompt_tokens_details was present on the LLM response';
