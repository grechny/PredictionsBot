package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class Fixture {
    var fixture: FixtureData? = null
    var league: LeagueData? = null
    var teams: TeamsData? = null
    var goals: Score? = null
    var score: ScoreDetail? = null
}
