package com.example.roomacoustic.screens.measure

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.viewmodel.RoomViewModel

@Composable
fun MeasureWidthScreen(nav: NavController, vm: RoomViewModel) =
    TwoPointMeasureScreen(
        nav = nav,
        title = "폭 측정 (왼쪽 벽 ↔ 오른쪽 벽)",
        labelKey = "폭",
        nextRoute = Screen.MeasureDepth.route, // 혹은 다음 화면
        onSave = { dist, p1, p2 ->
            vm.addLabeledMeasure("폭", dist)
            vm.setWidthPoints(p1, p2)
        }
    )