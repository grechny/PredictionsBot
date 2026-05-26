CREATE TABLE public.provider_external_ids (
    id uuid NOT NULL,
    provider_code character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    external_id character varying(255) NOT NULL,
    scope_key character varying(255) NOT NULL,
    internal_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE ONLY public.provider_external_ids
    ADD CONSTRAINT provider_external_ids_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.provider_external_ids
    ADD CONSTRAINT uk_provider_external_ids_provider_entity_external_scope
    UNIQUE (provider_code, entity_type, external_id, scope_key);

CREATE INDEX idx_provider_external_ids_entity_internal
    ON public.provider_external_ids (entity_type, internal_id);
