package at.hrechny.predictionsbot.config

import jakarta.inject.Singleton
import java.text.MessageFormat
import java.util.Locale
import java.util.MissingResourceException
import java.util.ResourceBundle

@Singleton
class MessageResolver {
    fun getMessage(code: String, args: Array<Any>?, locale: Locale?): String {
        val resolvedLocale = locale ?: Locale.forLanguageTag("ru")
        val pattern = try {
            ResourceBundle.getBundle("messages", resolvedLocale).getString(code)
        } catch (exception: MissingResourceException) {
            code
        }

        if (args == null || args.isEmpty()) {
            return pattern
        }
        return MessageFormat(pattern, resolvedLocale).format(args)
    }

    fun forLocale(locale: Locale?): LocalizedMessages = LocalizedMessages(this, locale)
}
