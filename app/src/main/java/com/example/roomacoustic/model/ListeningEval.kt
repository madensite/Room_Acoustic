package com.example.roomacoustic.model

data class ListeningMetric(
    val name: String,
    val score: Int,
    val detail: String
)

data class ListeningEval(
    val total: Int,
    val metrics: List<ListeningMetric>,
    val notes: List<String>,
    val listener: Vec2? = null
)