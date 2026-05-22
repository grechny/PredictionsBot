CREATE SCHEMA IF NOT EXISTS public;

CREATE TABLE public.audit (
    id uuid NOT NULL,
    api_key character varying(255),
    api_provider character varying(255),
    request_date timestamp without time zone,
    request_uri character varying(255),
    success boolean
);

CREATE TABLE public.competitions (
    id uuid NOT NULL,
    api_football_id bigint,
    name character varying(255)
);

CREATE TABLE public.leagues (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    admin_user_id bigint NOT NULL
);

CREATE TABLE public.leagues_competitions (
    league_entity_id uuid NOT NULL,
    competitions_id uuid NOT NULL
);

CREATE TABLE public.leagues_users (
    league_id uuid NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.matches (
    id uuid NOT NULL,
    api_football_id bigint,
    away_team_score integer,
    home_team_score integer,
    start_time timestamp without time zone,
    status character varying(255),
    away_team_id uuid NOT NULL,
    home_team_id uuid NOT NULL,
    round_id uuid
);

CREATE TABLE public.predictions (
    id uuid NOT NULL,
    double boolean,
    prediction_away numeric(1, 0),
    prediction_home numeric(1, 0),
    updated_at timestamp without time zone,
    match_id uuid NOT NULL,
    user_id bigint NOT NULL
);

CREATE TABLE public.rounds (
    id uuid NOT NULL,
    order_number integer,
    api_football_id character varying(255),
    type character varying(255),
    season_id uuid
);

CREATE TABLE public.seasons (
    id uuid NOT NULL,
    active boolean,
    year character varying(255),
    competition_id uuid NOT NULL
);

CREATE TABLE public.teams (
    id uuid NOT NULL,
    api_football_id bigint,
    name character varying(255),
    logo_url character varying(255)
);

CREATE TABLE public.users (
    id bigint NOT NULL,
    language character varying(255),
    timezone character varying(255),
    username character varying(255),
    active boolean,
    initial_language character varying(255)
);

CREATE TABLE public.users_competitions (
    user_entity_id bigint NOT NULL,
    competitions_id uuid NOT NULL
);

ALTER TABLE ONLY public.audit
    ADD CONSTRAINT audit_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.competitions
    ADD CONSTRAINT competitions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT idx_api_football_id UNIQUE (api_football_id);

ALTER TABLE ONLY public.leagues
    ADD CONSTRAINT leagues_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.leagues_users
    ADD CONSTRAINT leagues_users_pkey PRIMARY KEY (league_id, user_id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT matches_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.predictions
    ADD CONSTRAINT predictions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.rounds
    ADD CONSTRAINT rounds_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.seasons
    ADD CONSTRAINT seasons_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.seasons
    ADD CONSTRAINT uk7t42a5e8td6hvoqif91mtmcon UNIQUE (competition_id, year);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT uk_6g3otqqtwpqd4tfqnlgpjhm38 UNIQUE (api_football_id);

ALTER TABLE ONLY public.competitions
    ADD CONSTRAINT uk_fdsp87te9x778u4cev6oklobt UNIQUE (api_football_id);

ALTER TABLE ONLY public.leagues
    ADD CONSTRAINT uk_ip3mfd1fg0kl2jqep6fvp76ly UNIQUE (name);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk2e8erbfecb0tjtq9iudg36bxu FOREIGN KEY (away_team_id) REFERENCES public.teams(id);

ALTER TABLE ONLY public.leagues_users
    ADD CONSTRAINT fk4rlka6axup7np02g8pf8dbcnm FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.predictions
    ADD CONSTRAINT fk5ehjwkl57ibsn56fjmwj892ju FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.predictions
    ADD CONSTRAINT fk5gyk6l61eh61hmb9u1mr6hd7v FOREIGN KEY (match_id) REFERENCES public.matches(id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT fk8k68nekawp47js52dq8720voe FOREIGN KEY (home_team_id) REFERENCES public.teams(id);

ALTER TABLE ONLY public.users_competitions
    ADD CONSTRAINT fkaiydhm1oby6hn1nvppktskagt FOREIGN KEY (user_entity_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.users_competitions
    ADD CONSTRAINT fkgk4rs51dk0786vuef2amwsfod FOREIGN KEY (competitions_id) REFERENCES public.competitions(id);

ALTER TABLE ONLY public.leagues
    ADD CONSTRAINT fkhe6je2s9iqfku3k1rwqv11qb2 FOREIGN KEY (admin_user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.leagues_competitions
    ADD CONSTRAINT fkj42j928xr2uq9jo4gwsehq4t3 FOREIGN KEY (competitions_id) REFERENCES public.competitions(id);

ALTER TABLE ONLY public.seasons
    ADD CONSTRAINT fkpsjscwib1cgixjh1cwfrcejd2 FOREIGN KEY (competition_id) REFERENCES public.competitions(id);

ALTER TABLE ONLY public.leagues_competitions
    ADD CONSTRAINT fkpyii61bhoe71xdjdlm2ubk0e1 FOREIGN KEY (league_entity_id) REFERENCES public.leagues(id);

ALTER TABLE ONLY public.leagues_users
    ADD CONSTRAINT fksrhey85405rfde1too8o3m5wd FOREIGN KEY (league_id) REFERENCES public.leagues(id);

ALTER TABLE ONLY public.matches
    ADD CONSTRAINT matches_round_id_fkey FOREIGN KEY (round_id) REFERENCES public.rounds(id);

ALTER TABLE ONLY public.rounds
    ADD CONSTRAINT rounds_season_id_fkey FOREIGN KEY (season_id) REFERENCES public.seasons(id);
