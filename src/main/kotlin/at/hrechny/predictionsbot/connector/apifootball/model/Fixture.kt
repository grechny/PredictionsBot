package at.hrechny.predictionsbot.connector.apifootball.model

class Fixture {
    var fixture: FixtureData? = null
    var league: LeagueData? = null
    var teams: TeamsData? = null
    var goals: Score? = null
    var score: ScoreDetail? = null
}
