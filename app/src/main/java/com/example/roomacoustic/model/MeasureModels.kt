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
) {
    /** AR world → Room local */
    fun worldToLocal(p: Vec3): Vec3 {
        val d = p - origin
        return Vec3(
            d.dot(vx),
            d.dot(vy),
            d.dot(vz)
        )
    }

    /** Room local → AR world */
    fun localToWorld(p: Vec3): Vec3 =
        origin + vx * p.x + vy * p.y + vz * p.z
}


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

/**
 * (폭/깊이/높이) 세 화면에서 얻은 6점(PickedPoints)을 바탕으로
 * - Room 좌표계(AxisFrame)
 * - width/depth/height
 * 를 계산한다.
 *
 * 여기서 xMin/xMax, zMin/zMax, yFloor/yCeil 은
 * 실제 "최소/최대"라는 의미보다
 * "같은 축의 두 점(첫 번째/두 번째)" 정도로만 쓰인다.
 */
fun PickedPoints.toMeasure3DResultOrNull(): Measure3DResult? {
    val xMin = xMin ?: return null
    val xMax = xMax ?: return null
    val zMin = zMin ?: return null
    val zMax = zMax ?: return null
    val yFloor = yFloor ?: return null
    val yCeil = yCeil ?: return null

    // 1) 기본 축 벡터(월드 기준)
    val vxRaw = xMax - xMin          // 좌↔우
    val vzRaw = zMax - zMin          // 앞↔뒤
    val vyRaw = yCeil - yFloor       // 바닥↔천장

    if (vxRaw.length() < 1e-3f || vzRaw.length() < 1e-3f || vyRaw.length() < 1e-3f) {
        return null
    }

    val vx = vxRaw.normalized()

    // Z축: X에 대해 직교화
    val vzTmp = vzRaw - vx * vzRaw.dot(vx)
    if (vzTmp.length() < 1e-3f) return null
    val vz = vzTmp.normalized()

    // Y축: 우선 vyRaw를 직교화 해보고, 너무 작으면 cross로 대체
    var vyTmp = vyRaw - vx * vyRaw.dot(vx) - vz * vyRaw.dot(vz)
    if (vyTmp.length() < 1e-3f) {
        vyTmp = vx.cross(vz)
    }
    val vy = vyTmp.normalized()

    // 2) 각 축 길이 (m)
    val width  = (xMax - xMin).length()
    val depth  = (zMax - zMin).length()
    val height = (yCeil - yFloor).length()

    // 3) origin 계산
    // xMin을 기준점으로 잡고, zMin, yFloor가 로컬축에서 z=0, y=0이 되도록 평행이동
    val O0 = xMin

    fun toLocal0(p: Vec3): Vec3 {
        val d = p - O0
        return Vec3(
            d.dot(vx),
            d.dot(vy),
            d.dot(vz)
        )
    }

    val localZMin0 = toLocal0(zMin)
    val localYFloor0 = toLocal0(yFloor)

    val deltaY = localYFloor0.y
    val deltaZ = localZMin0.z

    val origin = O0 + vy * deltaY + vz * deltaZ

    val frame = AxisFrame(
        origin = origin,
        vx = vx,
        vy = vy,
        vz = vz
    )

    return Measure3DResult(
        frame = frame,
        width = width,
        depth = depth,
        height = height
    )
}