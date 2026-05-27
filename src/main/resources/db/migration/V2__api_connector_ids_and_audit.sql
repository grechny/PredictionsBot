CREATE TABLE public.api_connector_ids (
    id uuid NOT NULL,
    connector_code character varying(255) NOT NULL,
    entity_type character varying(255) NOT NULL,
    connector_entity_id character varying(255) NOT NULL,
    internal_id uuid NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

ALTER TABLE ONLY public.api_connector_ids
    ADD CONSTRAINT api_connector_ids_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.api_connector_ids
    ADD CONSTRAINT uk_api_connector_ids_connector_entity
    UNIQUE (connector_code, entity_type, connector_entity_id);

ALTER TABLE ONLY public.api_connector_ids
    ADD CONSTRAINT uk_api_connector_ids_connector_internal
    UNIQUE (connector_code, entity_type, internal_id);

CREATE INDEX idx_api_connector_ids_entity_internal
    ON public.api_connector_ids (entity_type, internal_id);

INSERT INTO public.api_connector_ids (
    id,
    connector_code,
    entity_type,
    connector_entity_id,
    internal_id,
    created_at,
    updated_at
)
SELECT
    md5('api-football:COMPETITION:' || id::text)::uuid,
    'api-football',
    'COMPETITION',
    api_football_id::text,
    id,
    now(),
    now()
FROM public.competitions
WHERE api_football_id IS NOT NULL
ON CONFLICT (connector_code, entity_type, connector_entity_id)
DO UPDATE SET
    internal_id = EXCLUDED.internal_id,
    updated_at = EXCLUDED.updated_at;

INSERT INTO public.api_connector_ids (
    id,
    connector_code,
    entity_type,
    connector_entity_id,
    internal_id,
    created_at,
    updated_at
)
SELECT
    md5('api-football:TEAM:' || id::text)::uuid,
    'api-football',
    'TEAM',
    api_football_id::text,
    id,
    now(),
    now()
FROM public.teams
WHERE api_football_id IS NOT NULL
ON CONFLICT (connector_code, entity_type, connector_entity_id)
DO UPDATE SET
    internal_id = EXCLUDED.internal_id,
    updated_at = EXCLUDED.updated_at;

INSERT INTO public.api_connector_ids (
    id,
    connector_code,
    entity_type,
    connector_entity_id,
    internal_id,
    created_at,
    updated_at
)
SELECT
    md5('api-football:MATCH:' || id::text)::uuid,
    'api-football',
    'MATCH',
    api_football_id::text,
    id,
    now(),
    now()
FROM public.matches
WHERE api_football_id IS NOT NULL
ON CONFLICT (connector_code, entity_type, connector_entity_id)
DO UPDATE SET
    internal_id = EXCLUDED.internal_id,
    updated_at = EXCLUDED.updated_at;

ALTER TABLE ONLY public.competitions
    DROP CONSTRAINT IF EXISTS uk_fdsp87te9x778u4cev6oklobt;

ALTER TABLE ONLY public.teams
    DROP CONSTRAINT IF EXISTS idx_api_football_id;

ALTER TABLE ONLY public.matches
    DROP CONSTRAINT IF EXISTS uk_6g3otqqtwpqd4tfqnlgpjhm38;

ALTER TABLE public.competitions
    DROP COLUMN api_football_id;

ALTER TABLE public.teams
    DROP COLUMN api_football_id;

ALTER TABLE public.matches
    DROP COLUMN api_football_id;

ALTER TABLE public.rounds
    DROP COLUMN api_football_id;

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
