ALTER TABLE public.audit
    DROP COLUMN api_key;

ALTER TABLE public.audit
    RENAME COLUMN api_provider TO connector_code;

ALTER TABLE public.audit
    ALTER COLUMN request_uri TYPE character varying(2048);

ALTER TABLE public.audit
    ADD COLUMN failure_reason text;

ALTER TABLE public.audit
    ADD COLUMN quota_snapshot text;

CREATE INDEX idx_audit_connector_code_request_date
    ON public.audit (connector_code, request_date);
