package com.example.roomacoustic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import androidx.navigation.NavType
import androidx.navigation.navArgument

import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.screens.RoomScreen
import com.example.roomacoustic.screens.SplashScreen
import com.example.roomacoustic.ui.theme.RoomacousticTheme
import com.example.roomacoustic.screens.chat.ChatMode
import com.example.roomacoustic.screens.chat.ChatScreen


// ▼ 측정/탐지 화면들
import com.example.roomacoustic.screens.measure.CameraGuideScreen
import com.example.roomacoustic.screens.measure.MeasureWidthScreen
import com.example.roomacoustic.screens.measure.MeasureDepthScreen
import com.example.roomacoustic.screens.measure.MeasureHeightScreen
import com.example.roomacoustic.screens.measure.DetectSpeakerScreen
import com.example.roomacoustic.screens.measure.RenderScreen
import com.example.roomacoustic.screens.measure.RoomAnalysisScreen   // ✅ 추가
import com.example.roomacoustic.screens.measure.TestGuideScreen
import com.example.roomacoustic.screens.measure.KeepTestScreen
import com.example.roomacoustic.screens.measure.AnalysisScreen

import com.example.roomacoustic.screens.result.ResultRenderScreen
import com.example.roomacoustic.screens.result.ResultAnalysisScreen
import com.example.roomacoustic.viewmodel.RoomViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
fun AppRoot() {
    RoomacousticTheme {
        val nav = rememberNavController()
        val vm: RoomViewModel = viewModel()

        NavHost(navController = nav, startDestination = Screen.Splash.route) {
            composable(Screen.Splash.route) { SplashScreen(nav) }
            composable(Screen.Room.route)   { RoomScreen(nav, vm) }

            /* ② ChatScreens ------------------------------------------------ */
            composable(
                route = Screen.NewChat.route,
                arguments = listOf(navArgument("roomId") { type = NavType.IntType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments!!.getInt("roomId")

                ChatScreen(
                    nav = nav,
                    roomId = roomId,
                    roomVm = vm,
                    mode = ChatMode.NEW          // ✅ 새 대화 모드
                )
            }

            composable(
                route = Screen.ExChat.route,
                arguments = listOf(navArgument("roomId") { type = NavType.IntType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments!!.getInt("roomId")

                ChatScreen(
                    nav = nav,
                    roomId = roomId,
                    roomVm = vm,
                    mode = ChatMode.CONTINUE     // ✅ 기존 대화 이어가기 모드
                )
            }

            // ── 측정 플로우 서브그래프 ──
            navigation(
                startDestination = Screen.CameraGuide.route, // ✅ 카메라 가이드부터
                route = Screen.MeasureGraph.route
            ) {
                // 0) 카메라 사용 가능 여부 안내/분기
                composable(Screen.CameraGuide.route) {
                    CameraGuideScreen(nav = nav, vm = vm)
                }

                // 1) 치수 측정 단계
                composable(Screen.MeasureWidth.route)  { MeasureWidthScreen(nav, vm) }
                composable(Screen.MeasureDepth.route)  { MeasureDepthScreen(nav, vm) }
                composable(Screen.MeasureHeight.route) { MeasureHeightScreen(nav, vm) }

                // 2) 스피커 탐지
                composable(Screen.DetectSpeaker.route) { DetectSpeakerScreen(nav, vm) }

                // 3) Render (카메라/탐지 유무와 무관하게 진입 가능)
                composable(
                    route = "${Screen.Render.route}?detected={detected}",
                    arguments = listOf(
                        navArgument("detected") {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { backStackEntry ->
                    val detected = backStackEntry.arguments?.getBoolean("detected") ?: false
                    RenderScreen(nav = nav, vm = vm, detected = detected)
                }
                composable(Screen.Render.route) {
                    RenderScreen(nav = nav, vm = vm, detected = false)
                }

                // 4) 새 화면: 평면도 뷰 + 청취 위치 선택 + 규칙 평가
                composable(Screen.RoomAnalysis.route) { RoomAnalysisScreen(nav, vm) }

                // 5) 녹음 가이드 및 진행
                composable(Screen.TestGuide.route) { TestGuideScreen(nav, vm) }
                composable(Screen.KeepTest.route)  { KeepTestScreen(nav, vm) }

                // (별도) 분석
                composable(
                    route = Screen.Analysis.route,
                    arguments = listOf(navArgument("roomId") { type = NavType.IntType })
                ) {
                    AnalysisScreen(nav, vm)
                }
            }

            // ── 결과 요약 (서브그래프 밖) ──
            composable(
                route = Screen.ResultRender.route,
                arguments = listOf(navArgument("roomId") { type = NavType.IntType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments!!.getInt("roomId")
                ResultRenderScreen(nav = nav, vm = vm, roomId = roomId)
            }
            composable(
                route = Screen.ResultAnalysis.route,
                arguments = listOf(navArgument("roomId") { type = NavType.IntType })
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments!!.getInt("roomId")
                ResultAnalysisScreen(nav = nav, vm = vm, roomId = roomId)
            }
        }
    }
}
