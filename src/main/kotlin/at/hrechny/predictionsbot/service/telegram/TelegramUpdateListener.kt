package at.hrechny.predictionsbot.service.telegram

import com.github.kotlintelegrambot.entities.Update

interface TelegramUpdateListener {
    fun process(updates: List<Update>): Int

    companion object {
        const val CONFIRMED_UPDATES_ALL: Int = -1
    }
}
