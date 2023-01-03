ALTER TABLE matches
    ADD column round_id uuid,
    ADD FOREIGN KEY (round_id) REFERENCES rounds(id);
UPDATE matches set round_id = (
    SELECT rounds.id
    FROM rounds
             INNER join seasons_api_football_rounds ON seasons_api_football_rounds.api_football_rounds_id = rounds.id
    WHERE seasons_api_football_rounds.season_entity_id = matches.season_id
      AND rounds.order_number = matches.round
    LIMIT 1
);

ALTER TABLE matches DROP CONSTRAINT fk3ehtbdjnpjhowp2023c45kh8r;
ALTER TABLE matches DROP COLUMN season_id;
ALTER TABLE matches DROP COLUMN round;

ALTER TABLE rounds
    RENAME COLUMN api_football_round_name TO api_football_id;

ALTER TABLE rounds
    ADD COLUMN type varchar,
    ADD COLUMN season_id uuid,
    ADD FOREIGN KEY (season_id) REFERENCES seasons(id);
UPDATE rounds set season_id = (
    SELECT seasons_api_football_rounds.season_entity_id
    FROM seasons_api_football_rounds
    WHERE seasons_api_football_rounds.api_football_rounds_id = rounds.id
);
UPDATE rounds
SET type =
        case
            when rounds.order_number = 0 then 'QUALIFYING'
            when rounds.api_football_id like 'Regular Season%' then 'SEASON'
            when rounds.api_football_id like 'Group%' then 'GROUP_STAGE'
            when rounds.api_football_id like 'Round of 16' then 'ROUND_OF_16'
            when rounds.api_football_id like 'Quarter-finals' then 'QUARTER_FINAL'
            when rounds.api_football_id like 'Semi-finals' then 'SEMI_FINAL'
            when rounds.api_football_id like '3rd Place Final' then 'THIRD_PLACE_FINAL'
            when rounds.api_football_id like 'Final' then 'FINAL'
            END;

DROP TABLE seasons_api_football_rounds;
