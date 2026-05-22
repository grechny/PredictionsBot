package at.hrechny.predictionsbot.config

import java.util.Locale

class LocalizedMessages(
    private val messageResolver: MessageResolver,
    private val locale: Locale?,
) {
    fun message(code: String): String = messageResolver.getMessage(code, null, locale)
}
