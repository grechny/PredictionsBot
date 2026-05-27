package at.hrechny.predictionsbot.database.model

enum class RoundType(
    val displayName: String?,
) {
    QUALIFYING(null),
    SEASON("\$round"),
    GROUP_STAGE("\$round"),
    ROUND_OF_32("1 / 16"),
    ROUND_OF_32_RETURN("1 / 16"),
    ROUND_OF_16("1 / 8"),
    ROUND_OF_16_RETURN("1 / 8"),
    QUARTER_FINAL("1 / 4"),
    QUARTER_FINAL_RETURN("1 / 4"),
    SEMI_FINAL("1 / 2"),
    SEMI_FINAL_RETURN("1 / 2"),
    THIRD_PLACE_FINAL("\uD83E\uDD49"),
    FINAL("\uD83C\uDFC6");

    fun getName(): String? = displayName
}
