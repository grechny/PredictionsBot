ALTER TABLE public.provider_external_ids
    RENAME TO api_connector_ids;

ALTER TABLE public.api_connector_ids
    RENAME COLUMN provider_code TO connector_code;

ALTER TABLE public.api_connector_ids
    RENAME COLUMN external_id TO connector_entity_id;

ALTER TABLE ONLY public.api_connector_ids
    RENAME CONSTRAINT provider_external_ids_pkey TO api_connector_ids_pkey;

ALTER TABLE ONLY public.api_connector_ids
    RENAME CONSTRAINT uk_provider_external_ids_provider_entity_external_scope TO uk_api_connector_ids_connector_entity_scope;

ALTER INDEX public.idx_provider_external_ids_entity_internal
    RENAME TO idx_api_connector_ids_entity_internal;
