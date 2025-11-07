package com.example.roomacoustic.navigation

sealed class Screen(val route: String) {

    /* ─────────  기존 화면  ───────── */
    object Splash : Screen("splash")
    object Room   : Screen("room")

    /* ─────────  챗봇 ───────── */
    object NewChat : Screen("newChat/{roomId}")
    object ExChat  : Screen("exChat/{roomId}")

    /* ─────────  ★ 측정 플로우(서브그래프) ★  ───────── */
    /** 서브그래프 진입 가상 라우트 */
    object MeasureGraph : Screen("measureGraph")

    /** 🔹 카메라 안내/분기 */
    object CameraGuide : Screen("cameraGuide")

    /** 단계별 측정 */
    object MeasureWidth   : Screen("measureWidth")     // ① 폭
    object MeasureDepth   : Screen("measureDepth")     // ② 깊이
    object MeasureHeight  : Screen("measureHeight")    // ③ 높이
    object DetectSpeaker  : Screen("detectSpeaker")    // ④ 스피커 탐지

    /** 측정 플로우 내부 후속 화면 */
    object Render         : Screen("render")           // 중간 3D 시각화
    object RoomAnalysis   : Screen("roomAnalysis")     // ✅ Top-down 평면 분석 & 청취 위치 선택
    object TestGuide      : Screen("testGuide")        // 녹음 가이드
    object KeepTest       : Screen("keepTest")         // 녹음 진행

    /** (기존) 분석: 측정 플로우와 독립적으로 이미 사용 중 */
    object Analysis  : Screen("analysis/{roomId}") {
        fun of(roomId: Int) = "analysis/$roomId"
    }

    /* ─────────  ★ 결과 전용: 서브그래프 밖 Top-level 라우트 ★  ───────── */
    /** ① 방 크기 + 스피커 위치 3D 요약 화면 */
    object ResultRender : Screen("resultRender/{roomId}") {
        fun of(roomId: Int) = "resultRender/$roomId"
    }

    /** ② 녹음(스펙트로그램 + RT60/C50/C80) 요약 화면 */
    object ResultAnalysis : Screen("resultAnalysis/{roomId}") {
        fun of(roomId: Int) = "resultAnalysis/$roomId"
    }
}
