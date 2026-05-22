package at.hrechny.predictionsbot.util

import us.dustinj.timezonemap.TimeZoneMap

object TimeZoneUtils {
    private val timeZoneMap: TimeZoneMap = TimeZoneMap.forEverywhere()

    @JvmStatic
    fun getTimeZone(latitude: Float, longitude: Float): String? {
        val timeZone = timeZoneMap.getOverlappingTimeZone(latitude.toDouble(), longitude.toDouble())
        return timeZone?.zoneId
    }
}
