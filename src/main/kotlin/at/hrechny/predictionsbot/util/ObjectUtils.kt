package at.hrechny.predictionsbot.util

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Predicate

object ObjectUtils {
    @JvmStatic
    fun <T> distinctByKey(keyExtractor: Function<in T, *>): Predicate<T> {
        val seen = ConcurrentHashMap<Any, Boolean>()
        return Predicate { value -> seen.putIfAbsent(keyExtractor.apply(value), true) == null }
    }
}
