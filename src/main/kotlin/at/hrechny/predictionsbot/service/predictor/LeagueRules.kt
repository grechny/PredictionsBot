package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.exception.InputValidationException
import at.hrechny.predictionsbot.exception.LimitExceededException
import at.hrechny.predictionsbot.util.NameUtils
import jakarta.inject.Singleton
import org.apache.commons.lang3.StringUtils

@Singleton
class LeagueRules {

    fun validateName(leagueName: String?) {
        if (StringUtils.isBlank(leagueName)) {
            throw InputValidationException("League name cannot be empty")
        }

        if (!NameUtils.isValid(leagueName)) {
            throw InputValidationException("League name should be 3-20 characters long and contain only valid characters")
        }
    }

    fun ensureLeagueLimit(leagueCount: Int) {
        if (leagueCount > MAX_LEAGUES_PER_USER) {
            throw LimitExceededException("User is already a member of another league")
        }
    }

    fun ensureLeagueAdmin(admin: Boolean) {
        if (!admin) {
            throw InputValidationException("User is not the admin of this league and cannot update it")
        }
    }

    private companion object {
        const val MAX_LEAGUES_PER_USER = 10
    }
}
