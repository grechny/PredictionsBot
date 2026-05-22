package at.hrechny.predictionsbot.connector.apifootball.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class FixtureStatusEnum(
    @get:JsonValue val value: String,
    val description: String,
) {
    TBD("TBD", "Time To Be Defined"),
    NS("NS", "Not Started"),
    _1H("1H", "First Half, Kick Off"),
    HT("HT", "Halftime"),
    _2H("2H", "Second Half, 2nd Half Started"),
    ET("ET", "Extra Time"),
    P("P", "Penalty In Progress"),
    FT("FT", "Match Finished"),
    AET("AET", "Match Finished After Extra Time"),
    PEN("PEN", "Match Finished After Penalty"),
    BT("BT", "Break Time (in Extra Time)"),
    SUSP("SUSP", "Match Suspended"),
    INT("INT", "Match Interrupted"),
    PST("PST", "Match Postponed"),
    CANC("CANC", "Match Cancelled"),
    ABD("ABD", "Match Abandoned"),
    AWD("AWD", "Technical Loss"),
    WO("WO", "WalkOver"),
    LIVE("LIVE", "In Progress"),
    ;

    override fun toString(): String = value

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): FixtureStatusEnum =
            entries.firstOrNull { status -> status.value == value } ?: TBD
    }
}
