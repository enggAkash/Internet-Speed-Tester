package com.engineerakash.internetspeedchecker.util

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