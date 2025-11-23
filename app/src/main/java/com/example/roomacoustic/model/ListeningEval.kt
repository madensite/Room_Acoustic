package com.example.roomacoustic.model

data class ListeningMetric(
    val name: String,
    val score: Int,
    val detail: String
)

data class ListeningEval(
    val total: Int,                // 0~100 총점
    val metrics: List<ListeningMetric>,
    val notes: List<String>        // 조언/권고 전체 텍스트
)
