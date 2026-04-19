package com.reprahfit

data class SpeedSample(
    val timestampMillis: Long,
    val speedMps: Double
)

data class RoutePoint(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?
)

data class RideSamples(
    val speedSamples: List<SpeedSample>,
    val routePoints: List<RoutePoint>
)
