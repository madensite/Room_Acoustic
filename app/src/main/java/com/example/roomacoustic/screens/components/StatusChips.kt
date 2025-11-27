package com.example.roomacoustic.screens.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusChips(hasMeasure: Boolean, hasChat: Boolean) {
    // 1. 원하는 어두운 톤의 색상을 정의합니다.
    val DarkRed = Color(0xFFB71C1C)   // 차분하고 진한 빨강
    val DarkGreen = Color(0xFF2E7D32) // 형광기가 빠진 짙은 초록 (Forest Green)

    Column {
        AssistChip(
            onClick = {},
            label = { Text("측정") },
            colors = if (hasMeasure)
                AssistChipDefaults.assistChipColors(
                    containerColor = DarkRed, // 수정된 색상 적용
                    labelColor = Color.White
                )
            else AssistChipDefaults.assistChipColors(
                labelColor = Color.Gray
            )
        )
        Spacer(Modifier.height(4.dp))
        AssistChip(
            onClick = {},
            label = { Text("대화") },
            colors = if (hasChat)
                AssistChipDefaults.assistChipColors(
                    containerColor = DarkGreen, // 수정된 색상 적용
                    labelColor = Color.White
                )
            else AssistChipDefaults.assistChipColors(
                labelColor = Color.Gray
            )
        )
    }
}