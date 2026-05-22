package at.hrechny.predictionsbot.util

import java.util.regex.Pattern
import org.apache.commons.lang3.RandomStringUtils

object NameUtils {
    private const val ALLOWED_SYMBOLS = "[^\\t\\n]"
    private val namePattern: Pattern = Pattern.compile("^$ALLOWED_SYMBOLS{2,20}$")

    @JvmStatic
    fun isValid(name: String?): Boolean = name != null && namePattern.matcher(name).matches()

    @JvmStatic
    fun formatName(name: String?): String {
        val nameBuilder = StringBuilder()
        if (name != null) {
            for (char in name.toCharArray()) {
                if (char.toString().matches(Regex(ALLOWED_SYMBOLS))) {
                    nameBuilder.append(char)
                }
            }
        }

        val formattedName = nameBuilder.toString().trim()
        return if (isValid(formattedName)) {
            formattedName
        } else {
            RandomStringUtils.secure().nextAlphanumeric(6)
        }
    }
}
