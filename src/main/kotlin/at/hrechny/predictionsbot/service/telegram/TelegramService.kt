package at.hrechny.predictionsbot.service.telegram

import at.hrechny.predictionsbot.config.MessageResolver
import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.service.predictor.CompetitionService
import at.hrechny.predictionsbot.service.predictor.PredictionService
import at.hrechny.predictionsbot.service.predictor.UserService
import at.hrechny.predictionsbot.util.FileUtils
import at.hrechny.predictionsbot.util.HashUtils
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.telegramError
import com.github.kotlintelegrambot.dispatcher.handlers.Handler
import com.github.kotlintelegrambot.network.Response as TelegramApiResponse
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.KeyboardReplyMarkup
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ReplyKeyboardRemove
import com.github.kotlintelegrambot.entities.ReplyMarkup
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.KeyboardButton
import com.github.kotlintelegrambot.entities.keyboard.WebAppInfo
import com.github.kotlintelegrambot.types.TelegramBotResult
import io.micronaut.context.annotation.Value
import io.micronaut.transaction.annotation.Transactional
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.util.Locale
import java.util.UUID
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import retrofit2.Response as RetrofitResponse

@Singleton
@Transactional
open class TelegramService(
    private val messageResolver: MessageResolver,
    private val predictionService: PredictionService,
    private val competitionService: CompetitionService,
    private val userService: UserService,
    private val hashUtils: HashUtils,
    @param:Value("\${telegram.token}")
    private val botToken: String,
    @param:Value("\${telegram.reportTo}")
    private val reportUserId: String,
    @param:Value("\${application.url}")
    private val applicationUrl: String,
) {
    private lateinit var telegramBot: Bot

    @PostConstruct
    open fun init() {
        telegramBot = bot { token = botToken }
    }

    open fun setUpListener(updatesListener: TelegramUpdateListener) {
        telegramBot = bot {
            token = botToken
            dispatch {
                addHandler(TelegramUpdateHandler(updatesListener))
                telegramError {
                    log.error("Telegram polling error [{}]: {}", error.getType(), error.getErrorMessage())
                }
            }
        }
        log.info("Starting Telegram long polling")
        telegramBot.startPolling()
        log.info("Telegram long polling started")
    }

    open fun sendMessage(userId: Long?, message: String?) {
        log.debug("Sending message to the user {}: {}", userId, message)
        sendTelegramMessage(userId!!, message!!, ParseMode.HTML)
    }

    @Transactional
    open fun startBot(user: User) {
        val username: String?
        try {
            userService.getUser(user.id)
            sendHelp(user)
            return
        } catch (exception: NotFoundException) {
            log.info("No active user found with id {}. New user will be created", user.id)
        }

        username = if (StringUtils.isBlank(user.username)) {
            if (StringUtils.isNotBlank(user.firstName)) user.firstName else user.lastName
        } else {
            user.username
        }
        userService.createUser(user.id, username, user.languageCode)
        val message = buildGreetingMessage(user, username)
        sendTelegramMessage(user.id, message, replyMarkup = locationKeyboard(user))
        sendHelp(user)
        sendReport(user, "report.user.created")
    }

    open fun sendActivateMessage(user: User) {
        sendTelegramMessage(
            user.id,
            messageResolver.getMessage("non_activated", null, Locale.forLanguageTag(user.languageCode ?: "ru")),
        )
    }

    open fun sendHelp(user: User) {
        sendTelegramMessage(user.id, messageResolver.getMessage("help", null, getLocale(user)), ParseMode.HTML)
    }

    open fun sendTimezoneMessage(user: User) {
        sendTelegramMessage(
            user.id,
            messageResolver.getMessage("start.location.change", null, getLocale(user)),
            replyMarkup = locationKeyboard(user),
        )
    }

    open fun sendPredictions(user: User) {
        val buttons = getPredictionButtons(user.id)
        if (buttons.isEmpty()) {
            val userEntity = userService.getUser(user.id)
            if (userEntity.competitions.isEmpty()) {
                sendCompetitions(user)
            } else {
                sendTelegramMessage(
                    user.id,
                    messageResolver.getMessage("no_competitions", null, getLocale(user)),
                    replyMarkup = ReplyKeyboardRemove(),
                )
            }
        } else {
            sendTelegramMessage(
                user.id,
                messageResolver.getMessage("predictions", null, getLocale(user)),
                replyMarkup = KeyboardReplyMarkup(buttons, resizeKeyboard = true),
            )
        }
    }

    open fun sendResults(user: User) {
        val message = "<pre>${messageResolver.getMessage("results.competitions", null, getLocale(user))}</pre>"
        sendTelegramMessage(
            user.id,
            message,
            ParseMode.HTML,
            inlineKeyboard(getCompetitionButtonsMatrix(user.id)),
        )
    }

    open fun sendResults(user: User, messageId: Int) {
        val message = "<pre>${messageResolver.getMessage("results.competitions", null, getLocale(user))}</pre>"
        editTelegramMessage(
            user.id,
            messageId,
            message,
            ParseMode.HTML,
            inlineKeyboard(getCompetitionButtonsMatrix(user.id)),
        )
    }

    open fun sendResults(user: User, seasonId: UUID, messageId: Int) {
        val locale = getLocale(user)
        val rows = ArrayList<List<InlineKeyboardButton>>()
        val seasonEntity = competitionService.getSeason(seasonId)
        val competition = seasonEntity.competition!!
        val results = predictionService.getResults(seasonId)
        val maxLength = results.size.toString().length +
            results.mapNotNull { result -> result.user?.name }.maxOfOrNull(String::length).let { it ?: 0 }

        rows.add(
            listOf(
                InlineKeyboardButton.WebApp(
                    text = messageResolver.getMessage("results.details", null, locale),
                    webApp = WebAppInfo(buildGeneralUrl(user.id, competition.id, seasonId, "results")),
                ),
            ),
        )
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = messageResolver.getMessage("results.details.back_button", null, locale),
                    callbackData = "/seasons ${competition.id}",
                ),
            ),
        )

        val message = StringBuilder()
        message.append("<pre>")
        message.append(competition.name).append(" ").append(seasonEntity.year).append('\n').append('\n')
        for (i in 1..results.size) {
            val resultEntry = results[i - 1]
            val nameAndOrder = "$i. ${resultEntry.user!!.name}"
            message.append(nameAndOrder)
            message.append(StringUtils.repeat(" ", maxLength + 4 - nameAndOrder.length))
            message.append(resultEntry.totalSum).append('\n')
        }
        message.append("</pre>")

        editTelegramMessage(user.id, messageId, message.toString(), ParseMode.HTML, inlineKeyboard(rows))
    }

    open fun sendResultsBySeasons(user: User, competitionId: UUID, messageId: Int) {
        val inlineKeyboardButtons = ArrayList<InlineKeyboardButton>()
        val competition = competitionService.getCompetition(competitionId)
        competitionService.getSeasons(competitionId).forEach { season ->
            inlineKeyboardButtons.add(
                InlineKeyboardButton.CallbackData(season.year.toString(), "/season ${season.id}"),
            )
        }

        val rows = convertToMatrix(inlineKeyboardButtons, 4).toMutableList()
        rows.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    messageResolver.getMessage("results.seasons.back_button", null, getLocale(user)),
                    "/results",
                ),
            ),
        )

        val message = "<pre>${competition.name}\n\n${messageResolver.getMessage("results.seasons", null, getLocale(user))}</pre>"
        editTelegramMessage(user.id, messageId, message, ParseMode.HTML, inlineKeyboard(rows))
    }

    open fun sendLeagues(user: User) {
        val locale = getLocale(user)
        sendTelegramMessage(
            user.id,
            messageResolver.getMessage("leagues", null, locale),
            replyMarkup = inlineKeyboard(
                listOf(
                    listOf(
                        InlineKeyboardButton.WebApp(
                            text = messageResolver.getMessage("leagues.button", null, locale),
                            webApp = WebAppInfo(buildGeneralUrl(user.id, null, null, "leagues")),
                        ),
                    ),
                ),
            ),
        )
    }

    open fun sendCompetition(competitionId: UUID) {
        val competition = competitionService.getCompetition(competitionId)
        val competitionName = competition.name!!
        userService.getUsers().forEach { user ->
            val locale = user.language ?: Locale.forLanguageTag("ru")
            sendTelegramMessage(
                user.id!!,
                messageResolver.getMessage("competitions.new", arrayOf<Any>(competitionName), locale),
                ParseMode.HTML,
                inlineKeyboard(
                    listOf(
                        listOf(
                            InlineKeyboardButton.CallbackData(competitionName, "/competition $competitionId"),
                        ),
                    ),
                ),
            )

            if (competition.isActive()) {
                pushUpdate(user.id, messageResolver.getMessage("push.update", null, locale), true)
            }
        }
    }

    open fun sendCompetition(user: User, messageId: Int, competitionId: UUID) {
        val locale = getLocale(user)
        val userEntity = userService.getUser(user.id)
        val competition = competitionService.getCompetition(competitionId)
        val activated = userEntity.competitions.any { competitionEntity -> competitionEntity.id == competitionId }
        val button = InlineKeyboardButton.CallbackData(
            (if (activated) "✅ " else "") + competition.name,
            "/competition ${competition.id}",
        )

        editTelegramMessage(
            user.id,
            messageId,
            messageResolver.getMessage("competitions.new", arrayOf<Any>(competition.name!!), getLocale(user)),
            ParseMode.HTML,
            inlineKeyboard(listOf(listOf(button))),
        )

        if (competition.isActive()) {
            pushUpdate(user.id, messageResolver.getMessage("push.update", null, locale), true)
        }
    }

    open fun sendCompetitions(user: User) {
        sendTelegramMessage(
            user.id,
            messageResolver.getMessage("competitions", null, getLocale(user)),
            ParseMode.HTML,
            inlineKeyboard(getCompetitions(user)),
        )
    }

    open fun sendCompetitions(user: User, messageId: Int, competitionId: UUID) {
        editTelegramMessage(
            user.id,
            messageId,
            messageResolver.getMessage("competitions", null, getLocale(user)),
            ParseMode.HTML,
            inlineKeyboard(getCompetitions(user)),
        )

        val competition = competitionService.getCompetition(competitionId)
        if (competition.isActive()) {
            pushUpdate(user.id, messageResolver.getMessage("push.update", null, getLocale(user)), true)
        }
    }

    open fun sendLanguageMessage(user: User) {
        val locale = getLocale(user)
        val rows = listOf(
            listOf(
                InlineKeyboardButton.CallbackData(messageResolver.getMessage("language.system", null, locale), "/language system"),
                InlineKeyboardButton.CallbackData("English", "/language en"),
                InlineKeyboardButton.CallbackData("Русский", "/language ru"),
            ),
        )

        sendTelegramMessage(
            user.id,
            messageResolver.getMessage("language", null, locale),
            replyMarkup = inlineKeyboard(rows),
        )
    }

    open fun stopBot(user: User) {
        sendTelegramMessage(user.id, messageResolver.getMessage("stop", null, getLocale(user)))
    }

    open fun sendLanguageConfirmation(user: User) {
        sendTelegramMessage(user.id, messageResolver.getMessage("change_success", null, getLocale(user)))
    }

    open fun sendUpdateLocationConfirmation(user: User, zoneId: String?) {
        val locale = getLocale(user)
        val message = if (StringUtils.isNotBlank(zoneId)) {
            messageResolver.getMessage("start.location", arrayOf<Any>(zoneId!!), locale)
        } else {
            messageResolver.getMessage("start.location.error", null, locale)
        }

        sendTelegramMessage(user.id, message, replyMarkup = ReplyKeyboardRemove())
    }

    open fun sendUsernameConfirmation(user: User, username: String?) {
        val locale = getLocale(user)
        val message = if (StringUtils.isBlank(username)) {
            messageResolver.getMessage("username.error", null, locale)
        } else {
            messageResolver.getMessage("username.success", arrayOf<Any>(username!!), locale)
        }

        sendTelegramMessage(user.id, message)
    }

    open fun sendUsernameInfo(user: User) {
        val userEntity = userService.getUser(user.id)
        val locale = getLocale(userEntity)
        val currentUsername = StringUtils.defaultIfBlank(
            userEntity.username,
            messageResolver.getMessage("username.not_set", null, locale),
        )!!
        sendTelegramMessage(
            user.id,
            messageResolver.getMessage("username.current", arrayOf<Any>(currentUsername), locale),
            ParseMode.HTML,
        )
    }

    open fun pushUpdate(competitionId: UUID) {
        userService.getUsers(competitionId).forEach { user ->
            val locale = user.language ?: Locale.forLanguageTag("ru")
            pushUpdate(user.id, messageResolver.getMessage("push.update", null, locale), true)
        }
    }

    open fun pushUpdate(userId: Long?, message: String?, updateCompetitionList: Boolean) {
        log.info("Sending push update to user {}", userId)

        val replyMarkup = if (updateCompetitionList) {
            val buttons = getPredictionButtons(userId)
            if (buttons.isNotEmpty()) {
                KeyboardReplyMarkup(buttons, resizeKeyboard = true)
            } else {
                ReplyKeyboardRemove()
            }
        } else {
            null
        }

        sendTelegramMessage(userId!!, message!!, ParseMode.HTML, replyMarkup)
    }

    open fun sendErrorReport(exception: Exception) {
        if (StringUtils.isBlank(reportUserId)) {
            log.info("No user specified to send error report")
            return
        }

        val reportUser = userService.getUser(reportUserId.toLong())
        val fileName = exception.javaClass.simpleName + ".pdf"
        val response = telegramBot.sendDocument(
            chatId = ChatId.fromId(reportUserId.toLong()),
            document = TelegramFile.ByByteArray(FileUtils.buildPdfDocument(exception), fileName),
            caption = messageResolver.getMessage("error", null, getLocale(reportUser)) + ": " + exception.message,
        )
        handleRequiredTelegramCallResult(
            response,
            "Error report document was not sent",
            "Unable to send error report",
        ) { message ->
            log.info("Error report document {} has been successfully sent", message.messageId)
        }
    }

    private fun getPredictionButtons(userId: Long?): List<List<KeyboardButton>> {
        val buttons = ArrayList<List<KeyboardButton>>()
        userService.getUser(userId!!).competitions.forEach { competition ->
            if (competition.seasons.any(SeasonEntity::isActive)) {
                val competitionName = competition.name!!
                buttons.add(
                    listOf(
                        KeyboardButton(
                            text = competitionName,
                            webApp = WebAppInfo(buildGeneralUrl(userId, competition.id, null, "predictions")),
                        ),
                        KeyboardButton(
                            text = "\uD83C\uDFC6",
                            webApp = WebAppInfo(buildGeneralUrl(userId, competition.id, null, "results")),
                        ),
                    ),
                )
            }
        }

        return buttons
    }

    private fun getCompetitionButtonsMatrix(userId: Long): List<List<InlineKeyboardButton>> {
        val inlineKeyboardButtons = ArrayList<InlineKeyboardButton>()
        userService.getUser(userId).competitions.forEach { competition ->
            inlineKeyboardButtons.add(
                InlineKeyboardButton.CallbackData(competition.name!!, "/seasons ${competition.id}"),
            )
        }

        return convertToMatrix(inlineKeyboardButtons, 2)
    }

    private fun getCompetitions(user: User): List<List<InlineKeyboardButton>> {
        val userEntity = userService.getUser(user.id)
        val competitions = competitionService.getCompetitions()
        val competitionList = ArrayList<InlineKeyboardButton>()
        competitions.forEach { competition ->
            val activated = userEntity.competitions.any { competitionEntity -> competitionEntity.id == competition.id }
            competitionList.add(
                InlineKeyboardButton.CallbackData(
                    (if (activated) "✅ " else "") + competition.name!!,
                    "/competitions ${competition.id}",
                ),
            )
        }
        return convertToMatrix(competitionList, 2)
    }

    private fun buildGreetingMessage(user: User, username: String?): String =
        messageResolver.getMessage("start.greeting", arrayOf<Any>(username!!), getLocale(user))

    private fun locationKeyboard(user: User): KeyboardReplyMarkup =
        KeyboardReplyMarkup(
            KeyboardButton(
                text = messageResolver.getMessage("buttons.location", null, getLocale(user)),
                requestLocation = true,
            ),
            resizeKeyboard = true,
        )

    private fun getLocale(user: User): Locale {
        val userEntity = userService.getUser(user.id)
        return userEntity.language ?: Locale.forLanguageTag(user.languageCode ?: "ru")
    }

    private fun getLocale(userEntity: UserEntity): Locale = userEntity.language ?: userEntity.initialLanguage!!

    private fun sendTelegramMessage(
        userId: Long,
        text: String,
        parseMode: ParseMode? = null,
        replyMarkup: ReplyMarkup? = null,
    ) {
        val response = telegramBot.sendMessage(
            chatId = ChatId.fromId(userId),
            text = text,
            parseMode = parseMode,
            replyMarkup = replyMarkup,
        )
        handleTelegramResult(response, userId, "Message was not sent", "Unable to send message") { message ->
            log.info("Message {} has been successfully sent", message.messageId)
        }
    }

    private fun editTelegramMessage(
        userId: Long,
        messageId: Int,
        text: String,
        parseMode: ParseMode? = null,
        replyMarkup: ReplyMarkup? = null,
    ) {
        val response = telegramBot.editMessageText(
            chatId = ChatId.fromId(userId),
            messageId = messageId.toLong(),
            text = text,
            parseMode = parseMode,
            replyMarkup = replyMarkup,
        )
        handleTelegramCallResult(response, userId, "Message was not updated", "Unable to send message") {
            log.info("Message has been successfully updated")
        }
    }

    private fun sendReport(user: User, reportCode: String) {
        if (StringUtils.isBlank(reportUserId)) {
            log.info("No user specified to send error report")
            return
        }
        val reportUser = userService.getUser(reportUserId.toLong())
        sendTelegramMessage(
            reportUser.id!!,
            messageResolver.getMessage(reportCode, arrayOf<Any>(user.id.toString()), getLocale(reportUser)),
        )
    }

    private fun inlineKeyboard(rows: List<List<InlineKeyboardButton>>): InlineKeyboardMarkup =
        InlineKeyboardMarkup.create(*rows.toTypedArray())

    private fun convertToMatrix(buttons: List<InlineKeyboardButton>, maxColumns: Int): List<List<InlineKeyboardButton>> {
        val rows = (buttons.size + maxColumns - 1) / maxColumns
        val iterator = buttons.iterator()
        return List(rows) {
            val columns = ArrayList<InlineKeyboardButton>()
            for (j in 0 until maxColumns) {
                if (iterator.hasNext()) {
                    columns.add(iterator.next())
                }
            }
            columns
        }
    }

    private fun buildGeneralUrl(userId: Long?, competitionId: UUID?, seasonId: UUID?, key: String): String {
        var url = "$applicationUrl/webapp/${hashUtils.getHash(userId.toString())}/users/$userId/$key"
        if (competitionId != null) {
            url += "?competitionId=$competitionId"
            if (seasonId != null) {
                url += "&seasonId=$seasonId"
            }
        }
        return url
    }

    private fun <T : Any> handleTelegramResult(
        response: TelegramBotResult<T>,
        userId: Long,
        errorLogMessage: String,
        exceptionMessage: String,
        onSuccess: (T) -> Unit,
    ) {
        response.fold(
            ifSuccess = onSuccess,
            ifError = { error ->
                log.error("{}: {}", errorLogMessage, describeTelegramError(error))
                if (isForbidden(error)) {
                    userService.deactivate(userId)
                } else {
                    throw IllegalStateException("$exceptionMessage: ${describeTelegramError(error)}")
                }
            },
        )
    }

    private fun <T : Any> handleTelegramCallResult(
        response: Pair<RetrofitResponse<TelegramApiResponse<T>?>?, Exception?>,
        userId: Long,
        errorLogMessage: String,
        exceptionMessage: String,
        onSuccess: (T) -> Unit,
    ) {
        val result = extractTelegramCallResult(response, errorLogMessage)
        if (result.value != null) {
            onSuccess(result.value)
            return
        }

        if (result.errorCode == 403) {
            userService.deactivate(userId)
        } else {
            throw IllegalStateException("$exceptionMessage: ${result.description}")
        }
    }

    private fun <T : Any> handleRequiredTelegramCallResult(
        response: Pair<RetrofitResponse<TelegramApiResponse<T>?>?, Exception?>,
        errorLogMessage: String,
        exceptionMessage: String,
        onSuccess: (T) -> Unit,
    ) {
        val result = extractTelegramCallResult(response, errorLogMessage)
        if (result.value != null) {
            onSuccess(result.value)
            return
        }

        throw IllegalStateException("$exceptionMessage: ${result.description}")
    }

    private fun <T : Any> extractTelegramCallResult(
        response: Pair<RetrofitResponse<TelegramApiResponse<T>?>?, Exception?>,
        errorLogMessage: String,
    ): TelegramCallResult<T> {
        val httpResponse = response.first
        val body = httpResponse?.body()
        if (httpResponse?.isSuccessful == true && body?.ok == true && body.result != null) {
            return TelegramCallResult(body.result, null, "")
        }

        val error = describeTelegramCallError(httpResponse, response.second)
        log.error("{}: {}", errorLogMessage, error.description)
        return TelegramCallResult(null, error.errorCode, error.description)
    }

    private fun describeTelegramCallError(
        response: RetrofitResponse<*>?,
        exception: Exception?,
    ): TelegramCallError {
        val body = response?.body()
        if (body is TelegramApiResponse<*> && body.errorCode != null) {
            return TelegramCallError(body.errorCode, "[${body.errorCode}] ${body.errorDescription}")
        }

        val parsedError = parseTelegramErrorBody(response)
        if (parsedError != null) {
            return parsedError
        }

        val statusCode = response?.code()
        val statusMessage = response?.message()
        val description = when {
            exception != null -> exception.message ?: exception.javaClass.simpleName
            statusCode != null -> "[$statusCode] $statusMessage"
            else -> "Unknown Telegram API error"
        }
        return TelegramCallError(null, description)
    }

    private fun parseTelegramErrorBody(response: RetrofitResponse<*>?): TelegramCallError? {
        val errorBody = response?.errorBody()?.string() ?: return null
        return try {
            val node = errorObjectMapper.readTree(errorBody)
            val errorCode = node.get("error_code")?.asInt()
            val description = node.get("description")?.asText()
            if (errorCode == null && description == null) {
                null
            } else {
                TelegramCallError(errorCode, "[${errorCode ?: response.code()}] ${description ?: response.message()}")
            }
        } catch (exception: Exception) {
            TelegramCallError(response.code(), "[${response.code()}] ${response.message()}")
        }
    }

    private fun isForbidden(error: TelegramBotResult.Error): Boolean =
        error is TelegramBotResult.Error.TelegramApi && error.errorCode == 403

    private fun describeTelegramError(error: TelegramBotResult.Error): String =
        when (error) {
            is TelegramBotResult.Error.TelegramApi -> "[${error.errorCode}] ${error.description}"
            is TelegramBotResult.Error.HttpError -> "[${error.httpCode}] ${error.description}"
            is TelegramBotResult.Error.InvalidResponse -> "[${error.httpCode}] ${error.httpStatusMessage}"
            is TelegramBotResult.Error.Unknown -> error.exception.message ?: error.exception.javaClass.simpleName
        }

    private class TelegramUpdateHandler(
        private val updatesListener: TelegramUpdateListener,
    ) : Handler {
        override fun checkUpdate(update: Update): Boolean = true

        override suspend fun handleUpdate(bot: Bot, update: Update) {
            updatesListener.process(listOf(update))
        }
    }

    private data class TelegramCallResult<T : Any>(
        val value: T?,
        val errorCode: Int?,
        val description: String,
    )

    private data class TelegramCallError(
        val errorCode: Int?,
        val description: String,
    )

    private companion object {
        val log = LoggerFactory.getLogger(TelegramService::class.java)
        val errorObjectMapper = ObjectMapper()
    }
}
