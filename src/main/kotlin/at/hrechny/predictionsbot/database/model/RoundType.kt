package at.hrechny.predictionsbot.database.model

import at.hrechny.predictionsbot.exception.NotFoundException

enum class RoundType(
    val displayName: String?,
    val aliasNames: List<String>,
) {
    QUALIFYING(null, listOf("Preliminary Round", ".*Qualifying.*", "Relegation Round", ".*Play-offs.*")),
    SEASON("\$round", listOf("Regular Season.*")),
    GROUP_STAGE("\$round", listOf("Group.*", "League Stage.*")),
    ROUND_OF_32("1 / 16", listOf("Round of 32", "Knockout Round Play-offs")),
    ROUND_OF_32_RETURN("1 / 16", listOf("Round of 32")),
    ROUND_OF_16("1 / 8", listOf("Round of 16")),
    ROUND_OF_16_RETURN("1 / 8", listOf("Round of 16", "8th Finals")),
    QUARTER_FINAL("1 / 4", listOf("Quarter-finals")),
    QUARTER_FINAL_RETURN("1 / 4", listOf("Quarter-finals")),
    SEMI_FINAL("1 / 2", listOf("Semi-finals")),
    SEMI_FINAL_RETURN("1 / 2", listOf("Semi-finals")),
    THIRD_PLACE_FINAL("\uD83E\uDD49", listOf("3rd Place Final")),
    FINAL("\uD83C\uDFC6", listOf("Final"));

    fun getName(): String? = displayName

    companion object {
        @JvmStatic
        fun getByAlias(aliasName: String): List<RoundType> {
            if (aliasName == "Knockout Round Play-offs") {
                return listOf(ROUND_OF_32)
            }

            val roundTypes = entries.filter { roundType ->
                roundType.aliasNames.any { pattern -> Regex(pattern).matches(aliasName) }
            }

            if (roundTypes.isEmpty()) {
                throw NotFoundException("No round type found by alias name $aliasName")
            }

            return roundTypes
        }
    }
}
