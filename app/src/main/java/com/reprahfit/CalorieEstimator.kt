package com.reprahfit

import kotlin.math.roundToInt

enum class Sex { Male, Female }

// Recumbent bikes are more aerodynamically efficient than upright,
// requiring roughly 15% less effort at the same speed.
private const val RECUMBENT_CORRECTION = 0.85

fun estimateOutdoorCalories(
    weightPounds: Double,
    hours: Double,
    averageSpeedMph: Double,
    averageHeartRate: Int? = null,
    age: Int? = null,
    sex: Sex? = null
): Int {
    if (averageHeartRate != null && age != null && sex != null && averageHeartRate > 0) {
        return estimateFromHeartRate(
            weightPounds = weightPounds,
            minutes = hours * 60.0,
            heartRate = averageHeartRate,
            age = age,
            sex = sex
        )
    }
    return estimateFromMet(weightPounds, hours, averageSpeedMph)
}

// Keytel et al. (2005) HR-based calorie formula
private fun estimateFromHeartRate(
    weightPounds: Double,
    minutes: Double,
    heartRate: Int,
    age: Int,
    sex: Sex
): Int {
    val weightKg = weightPounds.coerceAtLeast(0.0) * 0.453592
    val hr = heartRate.toDouble()
    val calPerMin = when (sex) {
        Sex.Male ->
            (-55.0969 + 0.6309 * hr + 0.1988 * weightKg + 0.2017 * age.toDouble()) / 4.184
        Sex.Female ->
            (-20.4022 + 0.4472 * hr + 0.1263 * weightKg + 0.074 * age.toDouble()) / 4.184
    }
    return (calPerMin.coerceAtLeast(0.0) * minutes.coerceAtLeast(0.0)).roundToInt()
}

private fun estimateFromMet(
    weightPounds: Double,
    hours: Double,
    averageSpeedMph: Double
): Int {
    val met = when {
        averageSpeedMph < 1.0 -> 0.0
        averageSpeedMph < 10.0 -> 4.0
        averageSpeedMph < 12.0 -> 6.8
        averageSpeedMph < 14.0 -> 8.0
        averageSpeedMph < 16.0 -> 10.0
        averageSpeedMph < 20.0 -> 12.0
        else -> 15.8
    }

    val weightKg = weightPounds.coerceAtLeast(0.0) * 0.453592
    return (met * RECUMBENT_CORRECTION * weightKg * hours.coerceAtLeast(0.0)).roundToInt()
}
