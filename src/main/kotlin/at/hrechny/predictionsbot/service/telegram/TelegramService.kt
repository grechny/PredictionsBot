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
import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.TelegramException
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.WebAppInfo
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.KeyboardButton
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendDocument
import com.pengrad.telegrambot.request.SendMessage
import io.micronaut.context.annotation.Value
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

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
    private lateinit var telegramBot: TelegramBot

    @PostConstruct
    open fun init() {
        telegramBot = TelegramBot(botToken)
    }

    open fun setUpListener(updatesListener: UpdatesListener) {
        telegramBot.setUpdatesListener(updatesListener)
    }

    open fun sendMessage(userId: Long?, message: String?) {
        log.debug("Sending message to the user {}: {}", userId, message)
        sendMessage(SendMessage(userId!!, message!!).parseMode(ParseMode.HTML), userId)
    }

    @Transactional
    open fun startBot(user: User) {
        val username: String?
        try {
            userService.getUser(user.id())
            sendHelp(user)
            return
        } catch (exception: NotFoundException) {
            log.info("No active user found with id {}. New user will be created", user.id())
        }

        username = if (StringUtils.isBlank(user.username())) {
            if (StringUtils.isNotBlank(user.firstName())) user.firstName() else user.lastName()
        } else {
            user.username()
        }
        userService.createUser(user.id(), username, user.languageCode())
        val message = buildGreetingMessage(user, username)
        sendMessage(message, user.id())
        sendHelp(user)
        sendReport(user, "report.user.created")
    }

    open fun sendActivateMessage(user: User) {
        val message = SendMessage(user.id(), messageResolver.getMessage("non_activated", null, Locale.forLanguageTag(user.languageCode())))
        sendMessage(message, user.id())
    }

    open fun sendHelp(user: User) {
        val sendMessage = SendMessage(user.id(), messageResolver.getMessage("help", null, getLocale(user))).parseMode(ParseMode.HTML)
        sendMessage(sendMessage, user.id())
    }

    open fun sendTimezoneMessage(user: User) {
        val locale = getLocale(user)
        val locationButton = KeyboardButton(messageResolver.getMessage("buttons.location", null, locale))
        locationButton.requestLocation(true)

        val message = SendMessage(user.id(), messageResolver.getMessage("start.location.change", null, locale))
        message.replyMarkup(ReplyKeyboardMarkup(locationButton).resizeKeyboard(true))
        sendMessage(message, user.id())
    }

    open fun sendPredictions(user: User) {
        val buttonsArray = getPredictionButtons(user.id())
        if (buttonsArray.isEmpty()) {
            val userEntity = userService.getUser(user.id())
            if (userEntity.competitions.isEmpty()) {
                sendCompetitions(user)
            } else {
                val message = messageResolver.getMessage("no_competitions", null, getLocale(user))
                val sendMessage = SendMessage(user.id(), message)
                sendMessage.replyMarkup(ReplyKeyboardRemove())
                sendMessage(sendMessage, user.id())
            }
        } else {
            val message = messageResolver.getMessage("predictions", null, getLocale(user))
            val sendMessage = SendMessage(user.id(), message)
            sendMessage.replyMarkup(ReplyKeyboardMarkup(*buttonsArray).resizeKeyboard(true))
            sendMessage(sendMessage, user.id())
        }
    }

    open fun sendResults(user: User) {
        val message = "<pre>${messageResolver.getMessage("results.competitions", null, getLocale(user))}</pre>"
        val sendMessage = SendMessage(user.id(), message).parseMode(ParseMode.HTML)
        sendMessage.replyMarkup(InlineKeyboardMarkup(*getCompetitionButtonsMatrix(user.id())))
        sendMessage(sendMessage, user.id())
    }

    open fun sendResults(user: User, messageId: Int) {
        val message = "<pre>${messageResolver.getMessage("results.competitions", null, getLocale(user))}</pre>"
        val editMessageText = EditMessageText(user.id(), messageId, message).parseMode(ParseMode.HTML)
        editMessageText.replyMarkup(InlineKeyboardMarkup(*getCompetitionButtonsMatrix(user.id())))
        editMessage(editMessageText, user.id())
    }

    open fun sendResults(user: User, seasonId: UUID, messageId: Int) {
        val locale = getLocale(user)
        val inlineKeyboardMatrix = ArrayList<Array<InlineKeyboardButton>>()
        val seasonEntity = competitionService.getSeason(seasonId)
        val competition = seasonEntity.competition!!
        val results = predictionService.getResults(seasonId)
        val maxLength = results.size.toString().length +
            results.mapNotNull { result -> result.user?.name }.maxOfOrNull(String::length).let { it ?: 0 }

        val detailsButton = InlineKeyboardButton(messageResolver.getMessage("results.details", null, locale))
        detailsButton.webApp(WebAppInfo(buildGeneralUrl(user.id(), competition.id, seasonId, "results")))
        inlineKeyboardMatrix.add(arrayOf(detailsButton))

        val backButton = InlineKeyboardButton(messageResolver.getMessage("results.details.back_button", null, locale))
        backButton.callbackData("/seasons ${competition.id}")
        inlineKeyboardMatrix.add(arrayOf(backButton))

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

        val editMessageText = EditMessageText(user.id(), messageId, message.toString())
        editMessageText.replyMarkup(InlineKeyboardMarkup(*inlineKeyboardMatrix.toTypedArray()))
        editMessage(editMessageText.parseMode(ParseMode.HTML), user.id())
    }

    open fun sendResultsBySeasons(user: User, competitionId: UUID, messageId: Int) {
        val inlineKeyboardButtons = ArrayList<InlineKeyboardButton>()
        val competition = competitionService.getCompetition(competitionId)
        competitionService.getSeasons(competitionId).forEach { season ->
            val inlineKeyboardButton = InlineKeyboardButton(season.year.toString())
            inlineKeyboardButton.callbackData("/season ${season.id}")
            inlineKeyboardButtons.add(inlineKeyboardButton)
        }

        val backButton = InlineKeyboardButton(messageResolver.getMessage("results.seasons.back_button", null, getLocale(user)))
        backButton.callbackData("/results")

        val inlineKeyboardMatrix = convertToMatrix(inlineKeyboardButtons, 4).toMutableList()
        inlineKeyboardMatrix.add(arrayOf(backButton))

        val message = "<pre>${competition.name}\n\n${messageResolver.getMessage("results.seasons", null, getLocale(user))}</pre>"
        val editMessageText = EditMessageText(user.id(), messageId, message).parseMode(ParseMode.HTML)
        editMessageText.replyMarkup(InlineKeyboardMarkup(*inlineKeyboardMatrix.toTypedArray()))
        editMessage(editMessageText, user.id())
    }

    open fun sendLeagues(user: User) {
        val locale = getLocale(user)
        val leagueButton = InlineKeyboardButton(messageResolver.getMessage("leagues.button", null, locale))
        leagueButton.webApp(WebAppInfo(buildGeneralUrl(user.id(), null, null, "leagues")))

        val sendMessage = SendMessage(user.id(), messageResolver.getMessage("leagues", null, locale))
        sendMessage.replyMarkup(InlineKeyboardMarkup(leagueButton))
        sendMessage(sendMessage, user.id())
    }

    open fun sendCompetition(competitionId: UUID) {
        val competition = competitionService.getCompetition(competitionId)
        userService.getUsers().forEach { user ->
            val locale = user.language ?: Locale.forLanguageTag("ru")
            val sendMessage = SendMessage(
                user.id!!,
                messageResolver.getMessage("competitions.new", arrayOf<Any>(competition.name!!), locale),
            )
            sendMessage.replyMarkup(InlineKeyboardMarkup(InlineKeyboardButton(competition.name).callbackData("/competition $competitionId")))
            sendMessage.parseMode(ParseMode.HTML)
            sendMessage(sendMessage, user.id)

            if (competition.isActive()) {
                pushUpdate(user.id, messageResolver.getMessage("push.update", null, locale), true)
            }
        }
    }

    open fun sendCompetition(user: User, messageId: Int, competitionId: UUID) {
        val locale = getLocale(user)
        val userEntity = userService.getUser(user.id())
        val competition = competitionService.getCompetition(competitionId)
        val activated = userEntity.competitions.any { competitionEntity -> competitionEntity.id == competitionId }
        val button = InlineKeyboardButton((if (activated) "✅ " else "") + competition.name)
        button.callbackData("/competition ${competition.id}")

        val editMessage = EditMessageText(
            user.id(),
            messageId,
            messageResolver.getMessage("competitions.new", arrayOf<Any>(competition.name!!), getLocale(user)),
        )
        editMessage.replyMarkup(InlineKeyboardMarkup(button))
        editMessage.parseMode(ParseMode.HTML)
        editMessage(editMessage, user.id())

        if (competition.isActive()) {
            pushUpdate(user.id(), messageResolver.getMessage("push.update", null, locale), true)
        }
    }

    open fun sendCompetitions(user: User) {
        val sendMessage = SendMessage(user.id(), messageResolver.getMessage("competitions", null, getLocale(user)))
        sendMessage.replyMarkup(InlineKeyboardMarkup(*getCompetitions(user)))
        sendMessage.parseMode(ParseMode.HTML)
        sendMessage(sendMessage, user.id())
    }

    open fun sendCompetitions(user: User, messageId: Int, competitionId: UUID) {
        val editMessage = EditMessageText(user.id(), messageId, messageResolver.getMessage("competitions", null, getLocale(user)))
        editMessage.replyMarkup(InlineKeyboardMarkup(*getCompetitions(user)))
        editMessage.parseMode(ParseMode.HTML)
        editMessage(editMessage, user.id())

        val competition = competitionService.getCompetition(competitionId)
        if (competition.isActive()) {
            pushUpdate(user.id(), messageResolver.getMessage("push.update", null, getLocale(user)), true)
        }
    }

    open fun sendLanguageMessage(user: User) {
        val locale = getLocale(user)
        val ruLanguageButton = InlineKeyboardButton("Русский")
        ruLanguageButton.callbackData("/language ru")

        val enLanguageButton = InlineKeyboardButton("English")
        enLanguageButton.callbackData("/language en")

        val systemLanguageButton = InlineKeyboardButton(messageResolver.getMessage("language.system", null, locale))
        systemLanguageButton.callbackData("/language system")

        val sendMessage = SendMessage(user.id(), messageResolver.getMessage("language", null, locale))
        sendMessage.replyMarkup(InlineKeyboardMarkup(systemLanguageButton, enLanguageButton, ruLanguageButton))
        sendMessage(sendMessage, user.id())
    }

    open fun stopBot(user: User) {
        sendMessage(SendMessage(user.id(), messageResolver.getMessage("stop", null, getLocale(user))), user.id())
    }

    open fun sendLanguageConfirmation(user: User) {
        sendMessage(SendMessage(user.id(), messageResolver.getMessage("change_success", null, getLocale(user))), user.id())
    }

    open fun sendUpdateLocationConfirmation(user: User, zoneId: String?) {
        val locale = getLocale(user)
        val message = if (StringUtils.isNotBlank(zoneId)) {
            messageResolver.getMessage("start.location", arrayOf<Any>(zoneId!!), locale)
        } else {
            messageResolver.getMessage("start.location.error", null, locale)
        }

        val sendMessage = SendMessage(user.id(), message)
        sendMessage.replyMarkup(ReplyKeyboardRemove())
        sendMessage(sendMessage, user.id())
    }

    open fun sendUsernameConfirmation(user: User, username: String?) {
        val locale = getLocale(user)
        val message = if (StringUtils.isBlank(username)) {
            messageResolver.getMessage("username.error", null, locale)
        } else {
            messageResolver.getMessage("username.success", arrayOf<Any>(username!!), locale)
        }

        sendMessage(SendMessage(user.id(), message), user.id())
    }

    open fun pushUpdate(competitionId: UUID) {
        userService.getUsers(competitionId).forEach { user ->
            val locale = user.language ?: Locale.forLanguageTag("ru")
            pushUpdate(user.id, messageResolver.getMessage("push.update", null, locale), true)
        }
    }

    open fun pushUpdate(userId: Long?, message: String?, updateCompetitionList: Boolean) {
        log.info("Sending push update to user {}", userId)
        val sendMessage = SendMessage(userId!!, message!!)
        sendMessage.parseMode(ParseMode.HTML)

        if (updateCompetitionList) {
            val buttonsArray = getPredictionButtons(userId)
            if (buttonsArray.isNotEmpty()) {
                sendMessage.replyMarkup(ReplyKeyboardMarkup(*buttonsArray).resizeKeyboard(true))
            } else {
                sendMessage.replyMarkup(ReplyKeyboardRemove())
            }
        }

        sendMessage(sendMessage, userId)
    }

    private fun getPredictionButtons(userId: Long?): Array<Array<KeyboardButton>> {
        val buttons = ArrayList<List<KeyboardButton>>()
        userService.getUser(userId!!).competitions.forEach { competition ->
            if (competition.seasons.any(SeasonEntity::isActive)) {
                val buttonsRow = ArrayList<KeyboardButton>()
                val predictionsKeyboardButton = KeyboardButton(competition.name)
                predictionsKeyboardButton.webAppInfo(WebAppInfo(buildGeneralUrl(userId, competition.id, null, "predictions")))
                buttonsRow.add(predictionsKeyboardButton)

                val resultsKeyboardButton = KeyboardButton("\uD83C\uDFC6")
                resultsKeyboardButton.webAppInfo(WebAppInfo(buildGeneralUrl(userId, competition.id, null, "results")))
                buttonsRow.add(resultsKeyboardButton)
                buttons.add(buttonsRow)
            }
        }

        return Array(buttons.size) { index -> buttons[index].toTypedArray() }
    }

    private fun getCompetitionButtonsMatrix(userId: Long): Array<Array<InlineKeyboardButton>> {
        val inlineKeyboardButtons = ArrayList<InlineKeyboardButton>()
        userService.getUser(userId).competitions.forEach { competition ->
            val inlineKeyboardButton = InlineKeyboardButton(competition.name)
            inlineKeyboardButton.callbackData("/seasons ${competition.id}")
            inlineKeyboardButtons.add(inlineKeyboardButton)
        }

        return convertToMatrix(inlineKeyboardButtons, 2)
    }

    private fun getCompetitions(user: User): Array<Array<InlineKeyboardButton>> {
        val userEntity = userService.getUser(user.id())
        val competitions = competitionService.getCompetitions()
        val competitionList = ArrayList<InlineKeyboardButton>()
        competitions.forEach { competition ->
            val activated = userEntity.competitions.any { competitionEntity -> competitionEntity.id == competition.id }
            val button = InlineKeyboardButton((if (activated) "✅ " else "") + competition.name)
            button.callbackData("/competitions ${competition.id}")
            competitionList.add(button)
        }
        return convertToMatrix(competitionList, 2)
    }

    private fun buildGreetingMessage(user: User, username: String?): SendMessage {
        val locale = getLocale(user)
        val locationButton = KeyboardButton(messageResolver.getMessage("buttons.location", null, locale))
        locationButton.requestLocation(true)

        val message = SendMessage(user.id(), messageResolver.getMessage("start.greeting", arrayOf<Any>(username!!), locale))
        return message.replyMarkup(ReplyKeyboardMarkup(locationButton).resizeKeyboard(true))
    }

    private fun getLocale(user: User): Locale {
        val userEntity = userService.getUser(user.id())
        return userEntity.language ?: Locale.forLanguageTag(user.languageCode())
    }

    private fun getLocale(userEntity: UserEntity): Locale = userEntity.language ?: userEntity.initialLanguage!!

    private fun sendMessage(message: SendMessage, userId: Long?) {
        val response = telegramBot.execute(message)
        if (response.isOk) {
            log.info("Message {} has been successfully sent", response.message().messageId())
        } else {
            log.error("Message was not send: [{}] {}", response.errorCode(), response.description())
            if (response.errorCode() == 403) {
                userService.deactivate(userId!!)
            } else {
                throw TelegramException("Unable to send message", response)
            }
        }
    }

    private fun editMessage(editMessage: EditMessageText, userId: Long) {
        val response = telegramBot.execute(editMessage)
        if (response.isOk) {
            log.info("Message has been successfully updated")
        } else {
            log.error("Message was not updated: [{}] {}", response.errorCode(), response.description())
            if (response.errorCode() == 403) {
                userService.deactivate(userId)
            } else {
                throw TelegramException("Unable to send message", response)
            }
        }
    }

    open fun sendErrorReport(exception: Exception) {
        if (StringUtils.isBlank(reportUserId)) {
            log.info("No user specified to send error report")
            return
        }

        val reportUser = userService.getUser(reportUserId.toLong())
        val sendDocument = SendDocument(reportUserId, FileUtils.buildPdfDocument(exception))
        sendDocument.fileName(exception.javaClass.simpleName + ".pdf")
        sendDocument.caption(messageResolver.getMessage("error", null, getLocale(reportUser)) + ": " + exception.message)
        val response = telegramBot.execute(sendDocument)
        if (response.isOk) {
            log.info("Error report document {} has been successfully sent", response.message().messageId())
        } else {
            log.error("Error report document was not send: [{}] {}", response.errorCode(), response.description())
            throw TelegramException("Unable to send error report", response)
        }
    }

    private fun sendReport(user: User, reportCode: String) {
        if (StringUtils.isBlank(reportUserId)) {
            log.info("No user specified to send error report")
            return
        }
        val reportUser = userService.getUser(reportUserId.toLong())
        val reportMessage = SendMessage(
            reportUser.id!!,
            messageResolver.getMessage(reportCode, arrayOf<Any>(user.id().toString()), getLocale(reportUser)),
        )
        sendMessage(reportMessage, reportUser.id)
    }

    private fun convertToMatrix(buttons: ArrayList<InlineKeyboardButton>, maxColumns: Int): Array<Array<InlineKeyboardButton>> {
        val rows = (buttons.size + maxColumns - 1) / maxColumns
        val iterator = buttons.iterator()
        return Array(rows) {
            val columns = ArrayList<InlineKeyboardButton>()
            for (j in 0 until maxColumns) {
                if (iterator.hasNext()) {
                    columns.add(iterator.next())
                }
            }
            columns.toTypedArray()
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

    private companion object {
        val log = LoggerFactory.getLogger(TelegramService::class.java)
    }
}
