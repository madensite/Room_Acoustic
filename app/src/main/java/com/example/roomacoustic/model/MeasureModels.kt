package com.example.roomacoustic.model

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = kotlin.math.sqrt(this.dot(this).toDouble()).toFloat()
    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else this
    }
}

/* 🔹 추가: 상면(Top-Down) 평가/선택용 2D 좌표 (x, z) */
data class Vec2(val x: Float, val z: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, z + o.z)
    operator fun minus(o: Vec2) = Vec2(x - o.x, z - o.z)
    operator fun times(s: Float) = Vec2(x * s, z * s)
    fun length() = kotlin.math.sqrt((x * x + z * z).toDouble()).toFloat()
}

// 청취자 위치(상면 투영, 단위 m)
data class Listener2D(val x: Float, val z: Float)

// 스피커-청취자 배치 품질 간단 평가
data class LayoutEval(
    val avgDist: Float?,          // 평균 거리 (m)
    val lDist: Float?,            // 좌 스피커 거리 (m) - 2ch일 때
    val rDist: Float?,            // 우 스피커 거리 (m) - 2ch일 때
    val distanceDelta: Float?,    // |L-R| (m)
    val toeInDeg: Float?,         // 스피커→청취자 각도 권장치(°)  (간단 추정)
    val sweetSpotScore: Float,    // 0~100
    val notes: List<String>       // 개선 제안 메시지
)



enum class MeasurePickStep(val label: String) {
    PickXMin("X- (왼쪽 벽을 탭)"),
    PickXMax("X+ (오른쪽 벽을 탭)"),
    PickZMin("Z- (앞쪽/가까운 벽)"),
    PickZMax("Z+ (뒤쪽/먼 벽)"),
    PickYFloor("Y- (바닥)"),
    PickYCeil("Y+ (천장)"),
    Review("검토"),
    Done("완료")
}

data class AxisFrame(
    val origin: Vec3,
    val vx: Vec3,   // X 단위벡터
    val vy: Vec3,   // Y 단위벡터(Up)
    val vz: Vec3    // Z 단위벡터
)

data class Measure3DResult(
    val frame: AxisFrame,
    val width: Float,   // X
    val depth: Float,   // Z
    val height: Float   // Y
)

data class PickedPoints(
    val xMin: Vec3? = null,
    val xMax: Vec3? = null,
    val zMin: Vec3? = null,
    val zMax: Vec3? = null,
    val yFloor: Vec3? = null,
    val yCeil: Vec3? = null,
) {
    fun isComplete() = xMin != null && xMax != null && zMin != null && zMax != null && yFloor != null && yCeil != null
}

data class MeasureValidation(val ok: Boolean, val reason: String? = null) {
    companion object {
        fun ok() = MeasureValidation(true, null)
        fun fail(reason: String) = MeasureValidation(false, reason)
    }
}
