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

## Prerequisites

Service is written in **Java 17** and prepared to be built and deployed as a docker image.

Current setup requires the next environment:

- **Vault** with Token authentication as a config server to store properties and passwords.
- **Postgres** as a database to store matches, players and predictions.

Since the service uses [API Football](https://www.api-football.com/) provider to get information
about Football Leagues, matches and results, the account should be created there and set in configuration.

The application uses [Telegram Web App](https://core.telegram.org/bots/webapps) feature which
requires valid **https** connection to the bot endpoints with valid and properly configured CA certificate
(most clients will not accept self-signed certificates)

## Properties

### Environment properties
| Property    | Description                 | Example                             |
|-------------|-----------------------------|-------------------------------------|
| VAULT_URI   | Vault URL                   | http://localhost:8200               |
| VAULT_TOKEN | Authorization service token | hvs.CAESIJRM-T1q5lEjIWux1Tjx-VGqAYJ |

### Application properties
| Property                            | Description                                                    | Example                                         |
|-------------------------------------|----------------------------------------------------------------|-------------------------------------------------|
| application.url                     | URL of application                                             | https://predictionsbot.example.com              |
| secrets.adminKey                    | UUID key for admin endpoints                                   | 00000000-0000-0000-0000-000000000000            |
| secrets.telegramKey                 | UUID key for application endpoints                             | 12345678-1234-1234-1234-1234567890ab            |
| telegram.token                      | Telegram Bot authentication token                              | 123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11       |
| spring.datasource.url               | DB url                                                         | jdbc:postgresql://127.0.0.1:5432/predictionsbot |
| spring.datasource.username          | DB username                                                    | postgres                                        |
| spring.datasource.password          | DB password                                                    | postgres                                        |
| connectors.api-football.url         | Api-Football api URL                                           | https://v3.football.api-sports.io/              |
| connectors.api-football.apiKey      | Api-Football api key                                           | demshd0c6f1cc61ab603p152db1jsn22cf              |
| connectors.api-football.dayStarts   | Time in UTC to start billing day                               | 22:00                                           |
| connectors.api-football.maxAttempts | Maximum of successful requests per billing day                 | 100                                             |
| connectors.api-football.minInterval | Minimal interval between identical requests (cache) in seconds | 60                                              |
