package at.hrechny.predictionsbot.service.telegram

import at.hrechny.predictionsbot.controller.model.prediction.PredictionRequestDto
import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.service.predictor.PredictionService
import at.hrechny.predictionsbot.service.predictor.UserService
import at.hrechny.predictionsbot.util.TimeZoneUtils
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.kotlintelegrambot.entities.CallbackQuery
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.Update
import com.github.kotlintelegrambot.entities.User
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import java.util.UUID
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

@Singleton
@Context
class MessageListener(
    private val telegramService: TelegramService,
    private val predictionService: PredictionService,
    private val userService: UserService,
    @param:Value("\${telegram.polling.enabled:true}")
    private val pollingEnabled: Boolean,
) : TelegramUpdateListener {
    private lateinit var objectMapper: ObjectMapper

    @PostConstruct
    fun init() {
        initObjectMapper()
        if (!pollingEnabled) {
            log.info("Telegram message listener polling is disabled")
            return
        }
        log.info("Starting Telegram message listener")
        telegramService.setUpListener(this)
    }

    override fun process(updates: List<Update>): Int {
        log.debug("Processing Bot updates: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updates))
        for (updateMessage in updates) {
            try {
                val callbackQuery = updateMessage.callbackQuery
                if (callbackQuery != null) {
                    processCallbackQuery(callbackQuery)
                    continue
                }

                val message = updateMessage.message ?: updateMessage.editedMessage
                if (message == null) {
                    continue
                }

                if (message.location != null) {
                    updateLocation(message)
                } else if (message.webAppData != null) {
                    savePredictions(message)
                } else if (message.text != null) {
                    processMessageText(message)
                } else {
                    log.warn("Got unexpected update: {}", updateMessage.updateId)
                }
            } catch (exception: Exception) {
                log.error("Unable to process an update {}", updateMessage.updateId, exception)
                telegramService.sendErrorReport(exception)
            }
        }

        return TelegramUpdateListener.CONFIRMED_UPDATES_ALL
    }

    private fun processMessageText(message: Message) {
        val user = message.from ?: return
        val text = message.text ?: return
        try {
            when (text) {
                "/start" -> telegramService.startBot(user)
                "/predictions" -> telegramService.sendPredictions(user)
                "/results" -> telegramService.sendResults(user)
                "/leagues" -> telegramService.sendLeagues(user)
                "/competitions" -> telegramService.sendCompetitions(user)
                "/timezone" -> telegramService.sendTimezoneMessage(user)
                "/language" -> telegramService.sendLanguageMessage(user)
                "/username" -> telegramService.sendUsernameInfo(user)
                "/help" -> telegramService.sendHelp(user)
                "/stop" -> stopBot(user)
                else -> {
                    if (text.startsWith("/username ")) {
                        updateUsername(user, text.substring(10))
                    } else if (text.startsWith("/language ")) {
                        updateLanguage(user, text.substring(10))
                    } else {
                        log.warn("Got the message which won't be processed: {}", text)
                    }
                }
            }
        } catch (notFoundException: NotFoundException) {
            log.warn("Active user {} is not found. Trying to send a message", user.id)
            telegramService.sendActivateMessage(user)
        }
    }

    private fun processCallbackQuery(callbackQuery: CallbackQuery) {
        val messageId = callbackQuery.message?.messageId?.toInt() ?: return
        val data = callbackQuery.data
        if (data.startsWith("/language ")) {
            updateLanguage(callbackQuery.from, data.substring(10))
        } else if (data.startsWith("/results")) {
            telegramService.sendResults(callbackQuery.from, messageId)
        } else if (data.startsWith("/seasons ")) {
            val competitionId = UUID.fromString(data.substring(9))
            telegramService.sendResultsBySeasons(callbackQuery.from, competitionId, messageId)
        } else if (data.startsWith("/season ")) {
            val seasonId = UUID.fromString(data.substring(8))
            telegramService.sendResults(callbackQuery.from, seasonId, messageId)
        } else if (data.startsWith("/competition ")) {
            val competitionId = UUID.fromString(data.substring(13))
            userService.updateCompetitions(callbackQuery.from.id, competitionId)
            telegramService.sendCompetition(callbackQuery.from, messageId, competitionId)
        } else if (data.startsWith("/competitions ")) {
            val competitionId = UUID.fromString(data.substring(14))
            userService.updateCompetitions(callbackQuery.from.id, competitionId)
            telegramService.sendCompetitions(callbackQuery.from, messageId, competitionId)
        }
    }

    private fun savePredictions(message: Message) {
        val predictions = objectMapper.readValue(
            message.webAppData!!.data,
            object : TypeReference<List<PredictionRequestDto>>() {},
        )
        predictionService.savePredictions(message.from!!.id, predictions)
    }

    private fun updateLocation(message: Message) {
        val user = message.from!!
        val location = message.location!!
        val zoneId = TimeZoneUtils.getTimeZone(location.latitude, location.longitude)
        if (zoneId != null) {
            userService.updateTimeZone(user.id, zoneId)
        }

        telegramService.sendUpdateLocationConfirmation(user, zoneId)
    }

    private fun updateUsername(user: User, rawUsername: String) {
        var username: String? = StringUtils.normalizeSpace(rawUsername)
        if (username!!.length >= 3 && username.length <= 20) {
            userService.updateUsername(user.id, username)
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

        userService.updateLanguage(user.id, language)
        telegramService.sendLanguageConfirmation(user)
    }

    private fun stopBot(user: User) {
        telegramService.stopBot(user)
        userService.deactivate(user.id)
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
