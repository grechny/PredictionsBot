package at.hrechny.predictionsbot.service.telegram

import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.model.Prediction
import at.hrechny.predictionsbot.service.predictor.PredictionService
import at.hrechny.predictionsbot.service.predictor.UserService
import at.hrechny.predictionsbot.util.TimeZoneUtils
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.User
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.util.UUID
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

@Singleton
open class MessageListener(
    private val telegramService: TelegramService,
    private val predictionService: PredictionService,
    private val userService: UserService,
) : UpdatesListener {
    private lateinit var objectMapper: ObjectMapper

    @PostConstruct
    fun init() {
        initObjectMapper()
        telegramService.setUpListener(this)
    }

    override fun process(updates: List<Update>): Int {
        log.debug("Processing Bot updates: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updates))
        for (updateMessage in updates) {
            try {
                if (updateMessage.callbackQuery() != null) {
                    processCallbackQuery(updateMessage.callbackQuery())
                    continue
                }

                val message = updateMessage.message() ?: updateMessage.editedMessage()
                if (message == null) {
                    continue
                }

                if (message.location() != null) {
                    updateLocation(message)
                } else if (message.webAppData() != null) {
                    savePredictions(message)
                } else if (message.text() != null) {
                    processMessageText(message)
                } else {
                    log.warn("Got unexpected update: {}", updateMessage.updateId())
                }
            } catch (exception: Exception) {
                log.error("Unable to process an update {}", updateMessage.updateId(), exception)
                telegramService.sendErrorReport(exception)
            }
        }

        return UpdatesListener.CONFIRMED_UPDATES_ALL
    }

    private fun processMessageText(message: Message) {
        val user = message.from()
        try {
            when (message.text()) {
                "/start" -> telegramService.startBot(user)
                "/predictions" -> telegramService.sendPredictions(user)
                "/results" -> telegramService.sendResults(user)
                "/leagues" -> telegramService.sendLeagues(user)
                "/competitions" -> telegramService.sendCompetitions(user)
                "/timezone" -> telegramService.sendTimezoneMessage(user)
                "/language" -> telegramService.sendLanguageMessage(user)
                "/help" -> telegramService.sendHelp(user)
                "/stop" -> stopBot(user)
                else -> {
                    if (message.text().startsWith("/username ")) {
                        updateUsername(user, message.text().substring(10))
                    } else if (message.text().startsWith("/language ")) {
                        updateLanguage(user, message.text().substring(10))
                    } else {
                        log.warn("Got the message which won't be processed: {}", message.text())
                    }
                }
            }
        } catch (notFoundException: NotFoundException) {
            log.warn("Active user {} is not found. Trying to send a message", user.id())
            telegramService.sendActivateMessage(user)
        }
    }

    @Suppress("DEPRECATION")
    private fun processCallbackQuery(callbackQuery: CallbackQuery) {
        val messageId = callbackQuery.message().messageId()
        val data = callbackQuery.data()
        if (data.startsWith("/language ")) {
            updateLanguage(callbackQuery.from(), data.substring(10))
        } else if (data.startsWith("/results")) {
            telegramService.sendResults(callbackQuery.from(), messageId)
        } else if (data.startsWith("/seasons ")) {
            val competitionId = UUID.fromString(data.substring(9))
            telegramService.sendResultsBySeasons(callbackQuery.from(), competitionId, messageId)
        } else if (data.startsWith("/season ")) {
            val seasonId = UUID.fromString(data.substring(8))
            telegramService.sendResults(callbackQuery.from(), seasonId, messageId)
        } else if (data.startsWith("/competition ")) {
            val competitionId = UUID.fromString(data.substring(13))
            userService.updateCompetitions(callbackQuery.from().id(), competitionId)
            telegramService.sendCompetition(callbackQuery.from(), messageId, competitionId)
        } else if (data.startsWith("/competitions ")) {
            val competitionId = UUID.fromString(data.substring(14))
            userService.updateCompetitions(callbackQuery.from().id(), competitionId)
            telegramService.sendCompetitions(callbackQuery.from(), messageId, competitionId)
        }
    }

    private fun savePredictions(message: Message) {
        val predictions = objectMapper.readValue(
            message.webAppData().data(),
            object : TypeReference<List<Prediction>>() {},
        )
        predictionService.savePredictions(message.from().id(), predictions)
    }

    private fun updateLocation(message: Message) {
        val user = message.from()
        val location = message.location()
        val zoneId = TimeZoneUtils.getTimeZone(location.latitude(), location.longitude())
        if (zoneId != null) {
            userService.updateTimeZone(user.id(), zoneId)
        }

        telegramService.sendUpdateLocationConfirmation(user, zoneId)
    }

    private fun updateUsername(user: User, rawUsername: String) {
        var username: String? = StringUtils.normalizeSpace(rawUsername)
        if (username!!.length >= 3 && username.length <= 20) {
            userService.updateUsername(user.id(), username)
        } else {
            log.warn("Username has not meet length criteria")
            username = null
        }

        telegramService.sendUsernameConfirmation(user, username)
    }

    private fun updateLanguage(user: User, rawLanguage: String?) {
        var language = rawLanguage
        if (StringUtils.isBlank(language) || language.equals("system", ignoreCase = true)) {
            language = null
        }

        userService.updateLanguage(user.id(), language)
        telegramService.sendLanguageConfirmation(user)
    }

    private fun stopBot(user: User) {
        telegramService.stopBot(user)
        userService.deactivate(user.id())
    }

    private fun initObjectMapper() {
        objectMapper = ObjectMapper()
        objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE)
        objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private companion object {
        val log = LoggerFactory.getLogger(MessageListener::class.java)
    }
}
