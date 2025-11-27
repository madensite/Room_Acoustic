package com.example.roomacoustic.screens.measure

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.viewmodel.RoomViewModel

@Composable
fun MeasureDepthScreen(nav: NavController, vm: RoomViewModel) =
    TwoPointMeasureScreen(
        nav = nav,
        title = "깊이 측정 (앞 벽 ↔ 뒤 벽)",
        labelKey = "깊이",
        nextRoute = Screen.MeasureHeight.route,
        onSave = { dist, p1, p2 ->
            vm.addLabeledMeasure("깊이", dist)   // 기존 UI용 라벨 저장
            vm.setDepthPoints(p1, p2)           // 🔥 좌표계용 두 점 저장
        }
    )
