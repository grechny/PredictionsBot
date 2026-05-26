ALTER TABLE public.audit
    DROP COLUMN api_key;

ALTER TABLE public.audit
    RENAME COLUMN api_provider TO connector_name;

ALTER TABLE public.audit
    ALTER COLUMN request_uri TYPE character varying(2048);

ALTER TABLE public.audit
    ADD COLUMN failure_reason text;

CREATE INDEX idx_audit_connector_name_request_date
    ON public.audit (connector_name, request_date);
