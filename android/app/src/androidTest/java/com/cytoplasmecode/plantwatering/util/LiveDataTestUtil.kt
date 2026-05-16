package com.cytoplasmecode.plantwatering.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Blocks until [LiveData] emits at least one value, then returns it.
 * Throws [TimeoutException] if no value arrives within [time] [timeUnit].
 */
fun <T> LiveData<T>.getOrAwaitValue(
    time: Long = 2,
    timeUnit: TimeUnit = TimeUnit.SECONDS
): T {
    var result: T? = null
    val latch = CountDownLatch(1)

    val observer = object : Observer<T> {
        override fun onChanged(value: T) {
            result = value
            latch.countDown()
            removeObserver(this)
        }
    }

    observeForever(observer)

    if (!latch.await(time, timeUnit)) {
        removeObserver(observer)
        throw TimeoutException("LiveData did not emit a value within $time $timeUnit")
    }

    @Suppress("UNCHECKED_CAST")
    return result as T
}
