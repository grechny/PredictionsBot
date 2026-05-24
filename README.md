# Prediction Telegram Bot

The bot allows you to make predictions on the results of football matches.
The prediction can be made at any time before the start of the match.
Depending on the results of the match, points are awarded for full-time (extra time does not count)
according to the following principle:

- 2 points if the winner is guessed;
- 3 points if the winner and goal difference are guessed;
- 5 points for the exact score of the match.

In addition, the results of one of the matches of the round can be doubled.

Viewing the results is possible both for the season and for each round separately.
Result table contains the results of all players together with the predictions
for each individual match that has begun or ended.

## Run Requirements

The service runs on **Java 25** with **Kotlin** and **Micronaut**.

Required services:

- **Vault** with token authentication.
- **Postgres** for matches, players and predictions.

Since the service uses [API Football](https://www.api-football.com/) provider to get information
about Football Leagues, matches and results, the account should be created there and set in configuration.

The application uses [Telegram Web App](https://core.telegram.org/bots/webapps) feature which
requires valid **https** connection to the bot endpoints with valid and properly configured CA certificate
(most clients will not accept self-signed certificates)

## Properties

### Environment Properties

Set these as environment variables:

| Property                    | Description                                                            | Example                    |
|-----------------------------|------------------------------------------------------------------------|----------------------------|
| VAULT_HOST                  | Vault host without protocol                                            | localhost                  |
| VAULT_PORT                  | Vault port                                                             | 8200                       |
| VAULT_SECRET                | Vault KV path under `secret/`                                          | PredictionsBot             |
| VAULT_TOKEN                 | Authorization service token. Takes precedence over `VAULT_TOKEN_FILE`. | `<vault-token>`            |
| VAULT_TOKEN_FILE            | Path to a file containing the authorization service token              | `/run/secrets/vault_token` |
| VAULT_CONFIG_IMPORT_ENABLED | Enables Docker entrypoint Vault config import                          | true                       |
| VAULT_CONNECT_TIMEOUT       | Vault config import connect timeout                                    | 5s                         |
| VAULT_READ_TIMEOUT          | Vault config import read timeout                                       | 10s                        |
| VAULT_RETRY_ATTEMPTS        | Vault config import retry attempts                                     | 1                          |
| VAULT_RETRY_DELAY           | Vault config import retry delay                                        | 1s                         |

The Docker entrypoint reads `VAULT_TOKEN_FILE`, exports `VAULT_TOKEN`, and writes a generated
Micronaut config file referenced by `MICRONAUT_CONFIG_FILES` for Micronaut's built-in Vault importer.
The generated file uses the scalar `micronaut.config.import=vault://...` form.
Vault import is enabled by default. Set `VAULT_CONFIG_IMPORT_ENABLED=false` to disable it.
Vault host defaults to `localhost`, port defaults to `8200`, and secret path defaults to `PredictionsBot`.
The entrypoint reads the token as root, then starts the application as the non-root `spring` user.
This allows `0600` root-owned token files mounted from Docker Compose secrets.
Datasource URL, username and password are not stored in `application.yaml`; they must be loaded from Vault or supplied as Micronaut datasource properties.

### Docker Compose

Use Docker Compose secrets for the Vault token file:

```yaml
services:
  predictions-bot:
    image: ahrechny/predictionsbot:next
    network_mode: bridge
    environment:
      VAULT_HOST: "172.17.0.3"
      VAULT_SECRET: PredictionsBot
      VAULT_TOKEN_FILE: /run/secrets/vault_token
    secrets:
      - vault_token

secrets:
  vault_token:
    file: ./secrets/vault_token
```

`network_mode: bridge` puts the application container on Docker's built-in `bridge` network.
Use it when Vault and Postgres are already running on that network and are addressed by
`172.17.x.x` container IPs. Without this, Docker Compose creates a separate project network,
and containers on that network may not be able to reach containers on the built-in bridge.

### Vault Properties

Store these application properties in Vault as JSON:
Use the `secret/<VAULT_SECRET>` KV v2 path. By default this is `secret/PredictionsBot`.

| Property                            | Description                                                    | Example                                         |
|-------------------------------------|----------------------------------------------------------------|-------------------------------------------------|
| application.url                     | URL of application                                             | https://predictionsbot.example.com              |
| secrets.adminKey                    | UUID key for admin endpoints                                   | 00000000-0000-0000-0000-000000000000            |
| secrets.telegramKey                 | UUID key for application endpoints                             | 12345678-1234-1234-1234-1234567890ab            |
| telegram.token                      | Telegram Bot authentication token                              | `<telegram-bot-token>`                          |
| telegram.reportTo                   | Telegram User ID to whom the error reports will be sent        | 12345678                                        |
| datasources.default.url             | DB URL                                                         | jdbc:postgresql://127.0.0.1:5432/predictionsbot |
| datasources.default.username        | DB username                                                    | postgres                                        |
| datasources.default.password        | DB password                                                    | postgres                                        |
| connectors.api-football.url         | Api-Football api URL                                           | https://v3.football.api-sports.io/              |
| connectors.api-football.apiKey      | Api-Football api key                                           | `<api-football-key>`                            |
| connectors.api-football.dayStarts   | Time in UTC to start billing day                               | 22:00                                           |
| connectors.api-football.maxAttempts | Maximum of successful requests per billing day                 | 100                                             |
| connectors.api-football.minInterval | Minimal interval between identical requests (cache) in seconds | 60                                              |
