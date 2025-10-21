# 룸 어쿠스틱 측정 앱: 프로젝트 요약

## 1. 프로젝트 개요

이 애플리케이션은 사용자의 방 환경을 분석하여 최적의 사운드 환경을 조성할 수 있도록 돕는 **룸 어쿠스틱 측정 및 분석 도구**입니다.

주요 기능은 다음과 같습니다.

*   **AR 기반 공간 측정:** ARCore 기술을 사용하여 방의 너비, 깊이, 높이를 정밀하게 측정합니다.
*   **실시간 스피커 탐지:** 딥러닝 모델(YOLOv8)을 사용하여 카메라 화면에서 실시간으로 스피커를 찾아내고 3D 공간에 위치를 매핑합니다.
*   **AI 챗봇 상담:** 측정된 데이터를 기반으로, 더 나은 사운드 환경을 만들기 위한 방법을 AI 챗봇(OpenAI)과 상담할 수 있습니다.
*   **측정 데이터 관리:** 여러 개의 방 정보를 저장하고, 언제든지 다시 확인하거나 재측정, 또는 대화 내용을 이어갈 수 있습니다.

## 2. 사용된 핵심 기술

이 프로젝트는 다음과 같은 최신 기술들을 활용하여 제작되었습니다.

*   **UI (사용자 인터페이스):**
    *   **Jetpack Compose:** Google의 최신 UI 개발 도구입니다. 더 적은 코드로 깔끔하고 반응성이 뛰어난 화면을 만들 수 있습니다.
*   **AR (증강현실):**
    *   **Google ARCore:** `ARSceneView`를 통해 현실 공간에 가상 객체를 렌더링하고, `Depth API`와 `Plane Detection`을 사용하여 공간의 크기와 구조를 파악합니다.
*   **AI (인공지능):**
    *   **TensorFlow Lite & YOLOv8:** 스피커를 탐지하기 위한 딥러닝 모델입니다. `best_int8.tflite` 라는 모델 파일을 사용하여, 스마트폰 카메라 화면에서 실시간으로 스피커 객체를 찾아냅니다.
    *   **OpenAI (GPT-4o-mini):** AI 챗봇 기능에 사용됩니다. 측정된 방 데이터를 기반으로 사용자에게 맞춤형 음향 컨설팅을 제공합니다.
*   **카메라 제어:**
    *   **CameraX:** 카메라 미리보기 화면을 표시하고, 이미지 프레임을 실시간으로 분석(스피커 탐지)하는 데 사용되는 라이브러리입니다.
*   **데이터베이스:**
    *   **Room:** 측정된 방 정보(이름, 측정 완료 여부, 대화 내용 등)를 스마트폰 내부에 안전하게 저장하고 관리하기 위해 사용됩니다.
*   **네트워크 통신:**
    *   **Retrofit:** OpenAI 서버와 통신하여 GPT 모델의 답변을 받아오는 역할을 합니다.
*   **3D 렌더링:**
    *   **OpenGL ES 2.0:** 측정된 방과 스피커 위치를 3D로 시각화하여 보여주는 데 사용됩니다.

## 3. 앱 권한

이 앱은 핵심 기능을 제공하기 위해 다음과 같은 권한을 사용자에게 요청합니다.

*   **카메라 (`android.permission.CAMERA`):** AR 기능과 스피커를 탐지하기 위해 필수적입니다.
*   **인터넷 (`android.permission.INTERNET`):** AI 챗봇(OpenAI)과 통신하기 위해 필요합니다.
*   **녹음 (`android.permission.RECORD_AUDIO`):** 향후 방의 울림(잔향)을 측정하는 기능을 위해 포함되어 있습니다.

## 4. 화면 및 로직 상세 분석

### 4.1. `MainActivity.kt`
앱의 유일한 Activity이자 모든 화면의 진입점입니다.
- **프로세스:** `onCreate`에서 `setContent`를 통해 `AppRoot` 컴포저블을 설정합니다. `AppRoot` 내에서 `NavHost`를 생성하여 앱의 전체 내비게이션 그래프를 정의합니다. `RoomViewModel`을 최상위에서 `viewModel()`로 생성하여 여러 화면이 동일한 ViewModel 인스턴스를 공유하도록 합니다. (Activity-Scoped ViewModel)

### 4.2. `SplashScreen.kt`
앱 실행 시 가장 먼저 보이는 로고 화면입니다.
- **프로세스:** `LaunchedEffect(Unit)`을 사용하여 화면이 처음 렌더링될 때 코루틴을 실행합니다. `delay(1000)`을 통해 1초간 대기한 후, `nav.navigate(Screen.Room.route)`를 호출하여 메인 화면으로 이동합니다. 이때 `popUpTo(Screen.Splash.route) { inclusive = true }` 옵션을 주어 스플래시 화면을 백스택에서 완전히 제거함으로써, 사용자가 뒤로가기 버튼으로 스플래시 화면으로 돌아오는 것을 방지합니다.

### 4.3. `RoomScreen.kt` (메인 화면)
저장된 모든 '방'의 목록을 보여주고 관리를 위한 시작점 역할을 합니다.
- **프로세스:**
    1.  **데이터 로딩:** `RoomViewModel`의 `rooms` StateFlow를 `collectAsState()`로 구독하여 데이터베이스의 방 목록 변경사항을 실시간으로 UI에 반영합니다.
    2.  **YOLO 모델 예열:** `LaunchedEffect(Unit)` 내에서 `Dispatchers.IO`를 사용, 백그라운드 스레드에서 `Detector`를 미리 초기화하고 `warmUp()`을 호출합니다. 이는 첫 측정 시작 시 TFLite 모델 로딩 및 GPU 셰이더 컴파일로 인한 지연(Jank)을 최소화하기 위한 최적화 작업입니다.
    3.  **UI 상태 관리:** `remember { mutableStateOf(...) }`를 사용하여 다이얼로그나 BottomSheet의 표시 여부(`showCreate`, `tappedRoom` 등)를 관리합니다.
    4.  **사용자 상호작용:**
        *   **플로팅 액션 버튼(FAB):** 클릭 시 `fabExpanded` 상태를 토글하여 '새 방 만들기', '모든 방 삭제' 미니 FAB을 애니메이션과 함께 보여줍니다.
        *   **방 목록 아이템:** `LazyColumn`의 각 아이템에는 `combinedClickable`이 적용되어 있습니다.
            *   **짧은 클릭 (`onClick`):** `tappedRoom` 상태를 업데이트하여 `ModalBottomSheet`를 띄웁니다. 이 시트에서는 방의 측정/대화 상태에 따라 '측정 시작', '새 대화' 등의 주된 작업을 제안합니다.
            *   **긴 클릭 (`onLongClick`):** `longPressedRoom` 상태를 업데이트하여 다른 `ModalBottomSheet`를 띄웁니다. 여기서는 '이름 변경', '재측정', '삭제' 등 편집/파괴적인 작업을 제안합니다.
    5.  **화면 이동 및 데이터 조작:** 각 메뉴 아이템 클릭 시 `nav.navigate(...)`로 다른 화면으로 이동하거나, `vm.addRoom(...)`, `vm.delete(...)` 등 ViewModel의 함수를 호출하여 데이터를 조작합니다.

### 4.4. `measure` 패키지 (측정 플로우)

#### `TwoPointMeasureScreen.kt`
폭, 깊이, 높이를 측정하기 위해 재사용되는 화면입니다.
- **프로세스:**
    1.  **AR 환경 설정:** `ARSceneView`를 생성하고 `RAW_DEPTH_ONLY` 모드를 활성화하여 깊이 정보를 얻을 수 있도록 설정합니다.
    2.  **탭 이벤트 처리:** `pointerInput`의 `detectTapGestures`로 사용자의 탭 위치(Offset)를 감지하여 `tapQueue`에 추가합니다. UI 이벤트와 AR 프레임 처리를 분리하기 위한 중간 큐입니다.
    3.  **AR 프레임 루프 (`onSessionUpdated`):**
        *   매 프레임마다 `tapQueue`에 이벤트가 있는지 확인하고, 있다면 `hitTestOrDepth()` 유틸리티를 호출하여 탭한 2D 화면 좌표에 해당하는 3D 공간 좌표를 얻습니다.
        *   **첫 번째 탭:** `firstPoint` 상태에 3D 좌표를 저장합니다.
        *   **두 번째 탭:** 새로 얻은 3D 좌표와 `firstPoint` 사이의 유클리드 거리를 계산(`distanceMeters`)하고, `AlertDialog`를 띄워 사용자에게 측정된 거리를 보여주고 저장할지 묻습니다.
        *   **실시간 거리 표시:** 매 프레임마다 화면 중앙(`viewW/2f`, `viewH/2f`)의 3D 좌표(`hoverPoint`)를 계산하여 `firstPoint`와의 실시간 거리를 화면에 텍스트로 업데이트합니다.
        *   **3D->2D 좌표 변환:** `worldToScreen()` 유틸리티를 사용하여 3D 좌표(`firstPoint`, `hoverPoint`)를 다시 2D 화면 좌표로 변환하고, `Canvas`에 점과 선을 그려 측정 과정을 시각적으로 안내합니다.

#### `DetectSpeakerScreen.kt`
스피커를 탐지하고 3D 공간에 위치시키는, 이 앱에서 가장 복잡한 화면 중 하나입니다.
- **프로세스:** `Phase`라는 `enum`을 통해 두 단계로 나뉘어 작동합니다.
    1.  **`Phase.DETECT` (2D 스피커 탐지):**
        *   **기술:** **CameraX**를 사용하여 카메라 미리보기를 구현합니다.
        *   **흐름:** `ImageAnalysis` UseCase를 설정하고, `setAnalyzer`를 통해 매 카메라 프레임을 백그라운드 스레드에서 받습니다. 받은 `ImageProxy`는 `YuvToRgbConverter`를 통해 RGB `Bitmap`으로 변환됩니다. 이 비트맵은 YOLO 모델의 입력 크기에 맞게 리사이즈된 후 `detector.detect()`에 전달됩니다.
        *   **결과:** `Detector`는 `DetectorListener` 콜백을 통해 탐지된 `BoundingBox` 목록을 반환하고, 이는 `OverlayView`에 전달되어 미리보기 화면 위에 초록색 사각형을 그립니다.
        *   **전환:** 사용자가 'AR 매핑으로 진행' 버튼을 누르면, CameraX 관련 리소스(Provider, Executor, Detector)를 안전하게 종료하고 `phase` 상태를 `Phase.MAP`으로 변경합니다.
    2.  **`Phase.MAP` (3D 위치 매핑):**
        *   **기술:** **ARCore** (`ARSceneView`)를 사용하여 AR 세션을 시작합니다.
        *   **흐름:** `onSessionUpdated` 프레임 루프 안에서 다음을 반복합니다.
            *   성능을 위해 N 프레임마다 한 번씩만 탐지를 수행합니다.
            *   AR 프레임에서 CPU 이미지를 얻어(`acquireCameraImage`) 비트맵으로 변환합니다. **(중요: 이 단계에서는 GPU 충돌을 피하기 위해 CPU에서 작동하는 새 `Detector` 인스턴스를 사용합니다.)**
            *   CPU `Detector`로 스피커의 2D 위치를 다시 탐지합니다.
            *   탐지된 각 스피커의 2D 중심점에 대해, 3D 좌표를 얻기 위해 다음 순서로 폴백(fallback) 전략을 사용합니다.
                1.  **Depth API (`sampleDepthMeters`):** `acquireDepthImage16Bits`로 얻은 깊이 이미지에서 해당 픽셀의 깊이 값(미터)을 직접 읽습니다. 가장 선호되는 방법입니다.
                2.  **Hit Test (`hitTest`):** 깊이 값을 얻지 못하면, ARCore가 인식한 평면(벽, 바닥 등)을 향해 광선을 쏘아 교차점을 찾습니다.
                3.  **크기 기반 추정:** 위 두 방법이 모두 실패하면, '카메라 초점거리', '미리 입력된 실제 스피커 폭(cm)', '이미지상 스피커의 픽셀 폭' 세 가지 정보를 이용해 삼각법으로 거리를 추정합니다. `거리 ≈ (초점거리 * 실제너비) / 픽셀너비`
            *   최종적으로 얻은 3D 좌표는 `SimpleTracker`를 통해 고유 ID를 부여받고, `RoomViewModel`의 `upsertSpeaker` 함수를 통해 상태 리스트에 저장/업데이트됩니다.

#### `RenderScreen.kt`
모든 측정 결과를 종합하여 3D로 시각화하는 화면입니다.
- **프로세스:**
    1.  **데이터 수집:** `RoomViewModel`로부터 방의 크기(`labeledMeasures` 또는 수동 입력값)와 3D 스피커 좌표 리스트(`speakers`)를 가져옵니다.
    2.  **3D 뷰 설정:** `AndroidView`를 사용하여 `GLSurfaceView`를 Compose 계층에 통합합니다. `GLRoomRenderer`라는 커스텀 렌더러를 설정합니다.
    3.  **사용자 입력 처리:** `pointerInput`과 `detectTransformGestures`를 사용하여 사용자의 드래그(회전), 핀치(줌) 제스처를 감지합니다. 감지된 값은 `yaw`, `pitch`, `zoom` 상태 변수를 업데이트합니다.
    4.  **렌더링:** `AndroidView`의 `update` 콜백에서 변경된 `yaw`, `pitch`, `zoom` 값과 방 크기, 스피커 데이터를 `GLRoomRenderer`에 전달하고 `requestRender()`를 호출하여 화면을 다시 그리도록 요청합니다.
    5.  **`GLRoomRenderer` 내부 로직:**
        *   전달받은 `yaw`, `pitch`, `zoom` 값으로 `Matrix.setLookAtM`을 사용하여 카메라(View) 매트릭스를 계산합니다. (오빗 컨트롤)
        *   방 크기로 직육면체의 꼭짓점 버퍼를 생성합니다.
        *   GLES20 셰이더를 사용하여 반투명한 방(면)과 불투명한 테두리(선), 그리고 스피커(점)를 그립니다.

### 4.5. `ChatScreen.kt`
AI 챗봇과 대화하는 화면입니다.
- **프로세스:**
    1.  **프롬프트 로딩:** `remember` 블록 안에서 `PromptLoader` 유틸리티를 사용하여 `assets` 폴더의 `prompt001.txt` 파일을 한 번만 읽어와 시스템 프롬프트로 사용합니다.
    2.  **대화 내용 표시:** `ChatViewModel`의 `messages` StateFlow를 구독하여 대화 목록을 `LazyColumn`으로 표시합니다. `reverseLayout = true`와 `LaunchedEffect`를 이용해 항상 최신 메시지가 하단에 보이도록 하고 자동으로 스크롤합니다.
    3.  **메시지 전송:** 사용자가 입력창에 텍스트를 입력하고 전송 버튼을 누르면, `ChatViewModel`의 `sendPrompt` 함수가 호출됩니다. 이때 시스템 프롬프트와 사용자 메시지가 함께 전달됩니다.
    4.  **키보드 처리:** `windowInsetsPadding(WindowInsets.ime)`를 사용하여 키보드가 올라올 때 입력창이 가려지지 않고 자연스럽게 위로 밀려 올라가도록 처리합니다.
    5.  **ViewModel 로직 (`ChatViewModel`):** `sendPrompt` 함수는 Retrofit을 사용하여 OpenAI API(`v1/chat/completions`)에 비동기 요청을 보냅니다. 성공적으로 응답을 받으면 `choices`에서 답변 텍스트를 추출하여 `_messages` StateFlow에 "gpt" 발신자로 추가합니다.

### 4.6. 데이터 및 상태 관리 (`RoomViewModel.kt`)
앱의 거의 모든 상태를 관리하는 중앙 허브 역할을 합니다.
- **역할:**
    *   `RoomRepository`를 통해 데이터베이스와 상호작용하며 `rooms` 목록을 UI에 제공합니다.
    *   `currentRoomId`를 통해 현재 사용자가 어떤 방에 대해 작업 중인지 추적합니다.
    *   `labeledMeasures` (`StateFlow<List<LabeledMeasure>>`): `TwoPointMeasureScreen`에서 측정한 '폭', '깊이', '높이' 값을 라벨과 함께 저장합니다.
    *   `speakers` (`mutableStateListOf<Speaker3D>`): `DetectSpeakerScreen`에서 최종적으로 확정된 3D 스피커 좌표 목록을 저장합니다. Compose가 리스트의 변경을 직접 감지할 수 있도록 `mutableStateListOf`를 사용합니다.
    *   `upsertSpeaker`, `pruneSpeakers`: 스피커 목록을 추가/갱신하거나, 너무 오랫동안 보이지 않는 스피커를 제거하는 로직을 제공합니다.
    *   `clearMeasureAndSpeakers`: 재측정 시 기존 측정 데이터와 스피커 목록을 깨끗하게 초기화하는 유틸리티 함수를 제공합니다.


## 6. 빌드 및 설정

*   **API 키:** AI 챗봇 기능을 사용하려면 프로젝트 루트에 `local.properties` 파일을 만들고, 그 안에 `OPENAI_API_KEY="YOUR_API_KEY"` 형식으로 자신의 OpenAI API 키를 추가해야 합니다.
*   **의존성:** `app/build.gradle.kts` 파일에 ARCore, CameraX, TensorFlow Lite, Jetpack Compose, Room, Retrofit 등 핵심 라이브러리 의존성이 정의되어 있습니다.
