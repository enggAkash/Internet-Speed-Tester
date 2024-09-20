package com.engineerakash.internetspeedchecker.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.selects.select
import java.text.DecimalFormat

/**
 * @return: 1.100000 -> 1.1, 1.3489 -> 1.34
 */
fun Double.uptoFixDecimalIfDecimalValueIsZero(decimalPoints: Int = 2): String {
    var tempDecimalPoints = decimalPoints

    if (tempDecimalPoints < 1) {
        tempDecimalPoints = 1
        return this.toInt().toString()
    }

    var decimalPointStr = ""
    for (i in 0 until tempDecimalPoints)
        decimalPointStr += "#"

    val df = DecimalFormat("#.$decimalPointStr")
    df.minimumFractionDigits = 0
    df.maximumFractionDigits = tempDecimalPoints
    return df.format(this)
}

/**
 * @return: 1.100000 -> 1.10, 1.3489 -> 1.34
 **/
fun Double.toFixDecimal(decimalPoints: Int = 2): String {
    var tempDecimalPoints = decimalPoints

    if (tempDecimalPoints < 1) {
        tempDecimalPoints = 1
        return this.toInt().toString()
    }

    var decimalPointStr = ""
    for (i in 0 until tempDecimalPoints)
        decimalPointStr += "#"

    val df = DecimalFormat("#.$decimalPointStr")
    df.minimumFractionDigits = tempDecimalPoints
    df.maximumFractionDigits = tempDecimalPoints
    return df.format(this)
}

@ExperimentalCoroutinesApi
inline fun <reified T> Flow<T>.throttleLatestKotlin(periodMillis: Long): Flow<T> {
    require(periodMillis > 0) { "period should be positive" }

    return channelFlow {
        val done = Any()
        val values = produce(capacity = Channel.CONFLATED) {
            collect { value ->
                try {
                    send(value)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        var lastValue: Any? = null
        val ticker = Ticker(periodMillis)
        while (lastValue !== done) {
            select<Unit> {
                values.onReceiveCatching {
                    if (it == null) {
                        ticker.cancel()
                        lastValue = done
                    } else {
                        lastValue = it
                        if (!ticker.isStarted) {
                            ticker.start(this@channelFlow)
                        }
                    }

                }

                ticker.getTicker().onReceive {
                    if (lastValue !== null) {
                        val value = lastValue
                        lastValue = null
                        try {
                            send((value as ChannelResult<T>).getOrThrow())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        ticker.stop()
                    }
                }
            }
        }
    }
}