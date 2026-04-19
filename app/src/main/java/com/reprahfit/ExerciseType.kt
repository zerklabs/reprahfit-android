package com.reprahfit

import androidx.health.connect.client.records.ExerciseSessionRecord

enum class ExerciseType(val healthConnectType: Int, val labelResId: Int) {
    BIKING(
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        R.string.exercise_type_biking
    ),
    STATIONARY(
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
        R.string.exercise_type_stationary
    );
}
