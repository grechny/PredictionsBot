CREATE TABLE public.api_connector_ids (
    id uuid NOT NULL,
    connector_code character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    connector_entity_id character varying(255) NOT NULL,
    scope_key character varying(255) NOT NULL,
    internal_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE ONLY public.api_connector_ids
    ADD CONSTRAINT api_connector_ids_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.api_connector_ids
    ADD CONSTRAINT uk_api_connector_ids_connector_entity_scope
    UNIQUE (connector_code, entity_type, connector_entity_id, scope_key);

CREATE INDEX idx_api_connector_ids_entity_internal
    ON public.api_connector_ids (entity_type, internal_id);

ALTER TABLE public.audit
    DROP COLUMN api_key;

ALTER TABLE public.audit
    RENAME COLUMN api_provider TO connector_name;

ALTER TABLE public.audit
    ALTER COLUMN request_uri TYPE character varying(2048);

ALTER TABLE public.audit
    ADD COLUMN failure_reason text;

UPDATE public.audit
SET connector_name = 'api-football'
WHERE connector_name = 'API_FOOTBALL';

CREATE INDEX idx_audit_connector_name_request_date
    ON public.audit (connector_name, request_date);
