package com.example.roomacoustic.screens.components

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.roomacoustic.model.Vec3
import kotlin.math.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** RenderScreen.kt 에 있던 RoomSize 를 외부로 */
data class RoomSize(val w: Float, val d: Float, val h: Float)

/** RenderScreen.kt 에 있던 RoomViewport3DGL 을 외부로 */
@Composable
fun RoomViewport3DGL(
    room: RoomSize,
    speakersLocal: List<Vec3>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var yaw by rememberSaveable { mutableStateOf(30f) }
    var pitch by rememberSaveable { mutableStateOf(20f) }
    var zoom by rememberSaveable { mutableStateOf(1.0f) }

    AndroidView(
        modifier = modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, zoomChange, _ ->
                yaw += pan.x * 0.3f
                pitch = (pitch + (-pan.y * 0.3f)).coerceIn(-80f, 80f)
                zoom = (zoom * zoomChange).coerceIn(0.5f, 3.0f)
            }
        },
        factory = {
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                val r = GLRoomRenderer()
                setRenderer(r)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                tag = r
            }
        },
        update = { view ->
            val r = view.tag as GLRoomRenderer
            r.setRoom(room)
            r.setSpeakers(speakersLocal)
            r.setOrbit(yaw, pitch, zoom)
            view.requestRender()
        }
    )
}

/** RenderScreen.kt 에 있던 private class 를 public 으로 그대로 이동 */
class GLRoomRenderer : GLSurfaceView.Renderer {

    private val vsh = """
        attribute vec3 aPos;
        uniform mat4 uMVP;
        uniform float uPointSize;
        void main(){
            gl_Position = uMVP * vec4(aPos, 1.0);
            gl_PointSize = uPointSize;
        }
    """.trimIndent()

    private val fsh = """
        precision mediump float;
        uniform vec4 uColor;
        void main(){ gl_FragColor = uColor; }
    """.trimIndent()

    private var prog = 0
    private var aPos = 0
    private var uMVP = 0
    private var uColor = 0
    private var uPointSize = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    @Volatile private var room: RoomSize? = null
    @Volatile private var speakers: FloatArray = floatArrayOf()

    @Volatile private var yaw = 30f
    @Volatile private var pitch = 20f
    @Volatile private var zoom = 1.0f

    private var roomTriangles = floatArrayOf()
    private var roomEdges = floatArrayOf()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        prog = linkProgram(vsh, fsh)
        aPos = GLES20.glGetAttribLocation(prog, "aPos")
        uMVP = GLES20.glGetUniformLocation(prog, "uMVP")
        uColor = GLES20.glGetUniformLocation(prog, "uColor")
        uPointSize = GLES20.glGetUniformLocation(prog, "uPointSize")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / max(height, 1)
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.05f, 100f)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val room = room ?: return
        ensureRoomGeometry(room)

        val radius = 0.5f * sqrt(room.w*room.w + room.h*room.h + room.d*room.d)
        val camDist = (radius * 2.2f) / zoom

        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
        val cx = (camDist * cos(pitchRad) * sin(yawRad))
        val cy = (camDist * sin(pitchRad))
        val cz = (camDist * cos(pitchRad) * cos(yawRad))

        Matrix.setLookAtM(
            view, 0,
            cx, cy, cz,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
        Matrix.setIdentityM(model, 0)

        GLES20.glUseProgram(prog)

        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mvp, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)

        // ── (A) 반투명 면: 깊이 '읽기만', 쓰기 끔 ──
        GLES20.glDepthMask(false)                       // ★ 깊이 쓰기 OFF
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, roomTriangles.toBuffer())
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 0.18f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, roomTriangles.size / 3)

        // ── (B) 선/점: 깊이 쓰기 켜고 정상 렌더 ──
        GLES20.glDepthMask(true)                        // ★ 깊이 쓰기 ON

        // 선
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, roomEdges.toBuffer())
        GLES20.glUniform4f(uColor, 1f, 1f, 1f, 0.65f)
        GLES20.glLineWidth(2f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, roomEdges.size / 3)

        // 점(스피커)
        if (speakers.isNotEmpty()) {
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, speakers.toBuffer())
            GLES20.glUniform4f(uColor, 1f, 0.6f, 0.2f, 1f)
            GLES20.glUniform1f(uPointSize, 18f)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, speakers.size / 3)
        }

        GLES20.glDisableVertexAttribArray(aPos)
    }

    fun setRoom(room: RoomSize) { this.room = room }

    fun setSpeakers(list: List<Vec3>) {
        val r = room ?: return
        val cx = r.w * 0.5f
        val cy = r.h * 0.5f
        val cz = r.d * 0.5f
        val arr = FloatArray(list.size * 3)
        list.forEachIndexed { i, p ->
            arr[i*3 + 0] = p.x - cx
            arr[i*3 + 1] = p.y - cy
            arr[i*3 + 2] = p.z - cz
        }
        this.speakers = arr
    }

    fun setOrbit(yaw: Float, pitch: Float, zoom: Float) {
        this.yaw = yaw; this.pitch = pitch; this.zoom = zoom
    }

    private var lastRoomForGeom: RoomSize? = null

    private fun ensureRoomGeometry(room: RoomSize) {
        if (lastRoomForGeom == room && roomTriangles.isNotEmpty()) return
        lastRoomForGeom = room

        if (roomTriangles.isNotEmpty()) return
        val hx = room.w * 0.5f
        val hy = room.h * 0.5f
        val hz = room.d * 0.5f

        val v000 = floatArrayOf(-hx, -hy, -hz)
        val v100 = floatArrayOf( hx, -hy, -hz)
        val v110 = floatArrayOf( hx,  hy, -hz)
        val v010 = floatArrayOf(-hx,  hy, -hz)
        val v001 = floatArrayOf(-hx, -hy,  hz)
        val v101 = floatArrayOf( hx, -hy,  hz)
        val v111 = floatArrayOf( hx,  hy,  hz)
        val v011 = floatArrayOf(-hx,  hy,  hz)

        roomTriangles = floatArrayOf(
            *v000, *v100, *v110,   *v000, *v110, *v010,
            *v001, *v101, *v111,   *v001, *v111, *v011,
            *v000, *v001, *v011,   *v000, *v011, *v010,
            *v100, *v101, *v111,   *v100, *v111, *v110,
            *v000, *v100, *v101,   *v000, *v101, *v001,
            *v010, *v110, *v111,   *v010, *v111, *v011
        )

        roomEdges = floatArrayOf(
            *v000, *v100,  *v100, *v101,  *v101, *v001,  *v001, *v000,
            *v010, *v110,  *v110, *v111,  *v111, *v011,  *v011, *v010,
            *v000, *v010,  *v100, *v110,  *v101, *v111,  *v001, *v011
        )
    }

    private fun linkProgram(vSrc: String, fSrc: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetShaderInfoLog(s)
                GLES20.glDeleteShader(s)
                throw RuntimeException("Shader compile error: $log")
            }
            return s
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Program link error: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
    }
}

/** RenderScreen.kt 에서 쓰던 확장 함수도 같이 옮깁니다. */
private fun FloatArray.toBuffer(): java.nio.FloatBuffer =
    java.nio.ByteBuffer.allocateDirect(this.size * 4)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(this@toBuffer); position(0) }
