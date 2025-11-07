# RoomAcoustic 프로젝트 상세 분석

이 문서는 RoomAcoustic 안드로이드 애플리케이션의 전반적인 구조, 코드 흐름, 사용된 기술 및 라이브러리, 그리고 향후 계획을 상세히 기술합니다.

## 1. 전체 앱 흐름 및 화면 전환 (Screen Flow)

RoomAcoustic 앱은 Jetpack Compose 기반의 단일 Activity (`MainActivity`) 아키텍처를 따르며, Jetpack Navigation Compose를 사용하여 화면 간의 이동을 관리합니다. 앱의 주요 흐름은 다음과 같습니다.

1.  **스플래시 화면 (`SplashScreen.kt`)**:
    *   앱 실행 시 가장 먼저 표시되는 화면입니다.
    *   `LaunchedEffect`를 사용하여 1초간 대기한 후, `RoomScreen`으로 자동 이동합니다.
    *   이때 `popUpTo(Screen.Splash.route) { inclusive = true }` 옵션을 사용하여 스플래시 화면을 백스택에서 제거하여 사용자가 뒤로 가기 버튼으로 돌아올 수 없도록 합니다.

2.  **메인 화면 (`RoomScreen.kt`)**:
    *   앱의 핵심 허브 역할을 하며, 사용자가 생성한 방 목록을 보여줍니다.
    *   `RoomViewModel`을 통해 데이터베이스에 저장된 `RoomEntity` 목록을 실시간으로 구독하여 UI에 반영합니다.
    *   **YOLO 모델 예열**: `LaunchedEffect` 내에서 `Detector`를 초기화하고 `warmUp()`을 호출하여 첫 측정 시 발생할 수 있는 지연을 최소화합니다.
    *   **방 생성/관리**: 플로팅 액션 버튼(FAB)을 통해 새 방을 추가하거나 모든 방을 삭제할 수 있습니다.
    *   **방 목록 상호작용**:
        *   **짧게 클릭**: `ModalBottomSheet`를 띄워 해당 방의 측정 상태에 따라 "측정 시작" 또는 "측정 결과 보기", "새 대화" 또는 "기존 대화 이어가기" 옵션을 제공합니다.
        *   **길게 클릭**: 다른 `ModalBottomSheet`를 띄워 "방 이름 바꾸기", "재측정하기", "대화 초기화", "삭제"와 같은 편집/관리 옵션을 제공합니다.
    *   **화면 전환**:
        *   "측정 시작" 또는 "재측정하기" 선택 시 `Screen.MeasureGraph.route` (측정 플로우 서브그래프)로 이동합니다.
        *   "새 대화" 또는 "기존 대화 이어가기" 선택 시 `Screen.NewChat.route` 또는 `Screen.ExChat.route` (채팅 화면)으로 이동합니다.
        *   "측정 결과 보기" 선택 시 `Screen.Render.route` (3D 렌더링 화면)으로 이동합니다.

3.  **측정 플로우 (서브그래프 `Screen.MeasureGraph`)**:
    *   `MainActivity.kt`의 `NavHost` 내에 `navigation` 블록으로 정의된 서브그래프입니다.
    *   **순서**: `MeasureWidthScreen` -> `MeasureDepthScreen` -> `MeasureHeightScreen` -> `DetectSpeakerScreen` -> `RenderScreen` -> `TestGuideScreen` -> `KeepTestScreen` -> `AnalysisScreen`
    *   **`TwoPointMeasureScreen.kt` (폭, 깊이, 높이 측정)**:
        *   `MeasureWidthScreen`, `MeasureDepthScreen`, `MeasureHeightScreen`은 `TwoPointMeasureScreen` 컴포저블을 재사용합니다.
        *   ARCore의 `RAW_DEPTH_ONLY` 모드를 사용하여 두 지점 간의 거리를 측정합니다.
        *   사용자가 화면을 탭하여 첫 번째 점과 두 번째 점을 지정하면 거리를 계산하고 저장 여부를 묻는 다이얼로그를 표시합니다.
        *   측정 완료 후 `nextRoute`에 지정된 다음 측정 화면으로 이동합니다.
    *   **`DetectSpeakerScreen.kt` (스피커 탐지)**:
        *   **Phase.DETECT**: CameraX를 사용하여 카메라 미리보기를 표시하고, YOLOv8 모델로 2D 화면 내 스피커를 탐지합니다. 탐지된 스피커의 바운딩 박스를 `OverlayView`에 그립니다.
        *   **Phase.MAP**: "AR 매핑으로 진행" 버튼 클릭 시 ARCore 세션으로 전환됩니다. AR 프레임에서 다시 스피커를 탐지하고, Depth API 또는 Hit Test를 사용하여 3D 공간 좌표를 추정합니다. `SimpleTracker`를 통해 스피커에 ID를 부여하고 `RoomViewModel`에 저장합니다.
        *   탐지 및 매핑 완료 후 `RenderScreen`으로 이동합니다.
    *   **`RenderScreen.kt` (3D 시각화)**:
        *   측정된 방 크기와 탐지된 스피커의 3D 위치를 OpenGL ES 2.0을 사용하여 3D로 시각화합니다.
        *   사용자는 드래그 및 핀치 제스처로 3D 뷰를 회전하고 확대/축소할 수 있습니다.
        *   "다음" 버튼 클릭 시 `TestGuideScreen`으로 이동합니다.
    *   **`TestGuideScreen.kt` (테스트 가이드)**:
        *   음향 측정을 위한 가이드라인을 사용자에게 제공합니다.
        *   "테스트 시작" 버튼 클릭 시 `KeepTestScreen`으로 이동합니다.
    *   **`KeepTestScreen.kt` (음향 측정 진행)**:
        *   `DuplexMeasurer`를 사용하여 스윕 신호를 재생하고 동시에 마이크로 녹음합니다.
        *   녹음 완료 후 Peak/RMS 레벨과 녹음 파일 경로를 표시합니다.
        *   "분석으로 이동" 버튼 클릭 시 `AnalysisScreen`으로 이동합니다.
    *   **`AnalysisScreen.kt` (분석 결과)**:
        *   녹음된 WAV 파일의 스펙트로그램을 계산하여 이미지로 표시합니다.
        *   녹음 파일 재생 기능과 함께 Peak, RMS, 길이 등의 정보를 보여줍니다.
        *   측정된 방 크기와 저장된 스피커 개수 정보도 함께 표시합니다.

4.  **채팅 화면 (`ChatScreen.kt`)**:
    *   `RoomScreen`에서 "새 대화" 또는 "기존 대화 이어가기" 선택 시 진입합니다.
    *   `PromptLoader`를 통해 `assets/prompt/prompt001.txt`에서 시스템 프롬프트를 로드합니다.
    *   `ChatViewModel`을 통해 OpenAI API와 통신하여 AI 챗봇과 대화합니다.
    *   사용자 메시지와 GPT 응답을 `LazyColumn`으로 표시하며, 최신 메시지가 항상 하단에 보이도록 자동 스크롤됩니다.

## 2. 상세 코드 및 라이브러리 정보

### 2.1. `build.gradle.kts` (모듈: `:app`)

*   **플러그인**: `com.android.application`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.compose`, `com.google.devtools.ksp` (Room 컴파일러용)
*   **SDK 버전**: `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`
*   **BuildConfig**: `OPENAI_API_KEY`를 `local.properties`에서 읽어와 `BuildConfig` 필드로 주입합니다.
*   **NDK**: `abiFilters`로 `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`를 설정하여 다양한 아키텍처를 지원합니다.
*   **Packaging Options**: `.so` 라이브러리 충돌(`libarcore_sdk_c.so`, `libfilament.so` 등) 및 `META-INF` 리소스 충돌을 `pickFirsts`로 해결합니다.
*   **Java/Kotlin 버전**: `JavaVersion.VERSION_17`, `jvmTarget = "17"`
*   **Build Features**: `compose = true`, `viewBinding = true`, `buildConfig = true`
*   **주요 의존성**:
    *   **Jetpack Compose**: `androidx.compose.material3`, `androidx.compose.material:material-icons-extended`, `androidx.activity:activity-compose`, `androidx.compose.ui:ui-tooling-preview` 등 UI 구축에 필요한 핵심 라이브러리.
    *   **Jetpack Navigation**: `androidx.navigation:navigation-compose`를 사용하여 화면 내비게이션을 구현합니다.
    *   **Kotlin Coroutines**: `kotlinx-coroutines-core`, `kotlinx-coroutines-android`, `kotlinx-coroutines-jdk8`를 사용하여 비동기 작업을 처리합니다.
    *   **Lifecycle**: `androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.lifecycle:lifecycle-viewmodel-ktx`를 사용하여 ViewModel 및 생명주기 관리를 합니다.
    *   **Room Persistence Library**: `room-runtime`, `room-ktx`, `room-compiler`를 사용하여 로컬 데이터베이스를 구축합니다.
    *   **TensorFlow Lite**: `org.tensorflow:tensorflow-lite`, `tensorflow-lite-support`, `tensorflow-lite-gpu` 등 YOLOv8 모델 추론에 필요한 라이브러리.
    *   **CameraX**: `androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-extensions`를 사용하여 카메라 미리보기 및 이미지 분석을 구현합니다.
    *   **Retrofit**: `com.squareup.retrofit2:retrofit`, `converter-gson`을 사용하여 OpenAI API와 같은 RESTful API 통신을 처리합니다.
    *   **ARCore**: `com.google.ar:core`, `io.github.sceneview:sceneview`, `arsceneview`를 사용하여 증강현실 기능(공간 측정, 3D 매핑)을 구현합니다.
    *   **Coil**: `io.coil-kt:coil-compose` 이미지 로딩 라이브러리.

### 2.2. `AndroidManifest.xml`

*   **권한**:
    *   `android.permission.RECORD_AUDIO`: 음향 측정 및 녹음 기능에 필요합니다.
    *   `android.permission.MODIFY_AUDIO_SETTINGS`: 오디오 설정 변경에 필요합니다.
    *   `android.permission.CAMERA`: AR 기능 및 스피커 탐지에 필수적입니다.
    *   `android.permission.INTERNET`: OpenAI API 통신에 필요합니다.
*   **Features**:
    *   `android.hardware.camera.ar`: ARCore 사용을 명시합니다.
    *   `android.glEsVersion="0x00030000"`: OpenGL ES 3.0 이상을 요구합니다.
*   **Activity**: `MainActivity`가 앱의 유일한 진입점이며, `android.intent.action.MAIN` 및 `android.intent.category.LAUNCHER` 필터를 가집니다.
*   **Meta-data**: `com.google.ar.core` 메타데이터를 `required`로 설정하여 ARCore가 필수임을 나타냅니다.

### 2.3. `MainActivity.kt`

*   **역할**: 앱의 유일한 `ComponentActivity`이자 모든 Compose 화면의 호스트입니다.
*   **프로세스**:
    *   `onCreate`에서 `enableEdgeToEdge()`를 호출하여 전체 화면 UI를 활성화합니다.
    *   `setContent { AppRoot() }`를 통해 Compose UI 트리의 루트인 `AppRoot` 컴포저블을 설정합니다.
    *   `AppRoot` 내에서 `rememberNavController()`로 `NavController`를 생성하고, `RoomViewModel`을 `viewModel()`로 생성하여 앱 전반에 걸쳐 공유되는 ViewModel 인스턴스를 제공합니다 (Activity-Scoped ViewModel).
    *   `NavHost`를 사용하여 `Screen.Splash.route`를 시작 지점으로 하는 내비게이션 그래프를 정의합니다.
    *   `composable` 함수를 통해 각 `Screen` 라우트에 해당하는 컴포저블을 연결합니다.
    *   측정 플로우는 `navigation` 블록을 사용하여 `Screen.MeasureGraph.route`를 시작점으로 하는 서브그래프로 구성됩니다.

### 2.4. `navigation/Screen.kt`

*   **역할**: 앱 내의 모든 화면 라우트를 `sealed class`로 정의하여 타입 안전성을 확보하고 내비게이션 경로를 중앙에서 관리합니다.
*   **주요 라우트**:
    *   `Splash`, `Room` (메인 화면)
    *   `NewChat`, `ExChat` (채팅 화면, `roomId` 인자 포함)
    *   `MeasureGraph` (측정 플로우의 서브그래프 진입점)
    *   `MeasureWidth`, `MeasureDepth`, `MeasureHeight` (각각 폭, 깊이, 높이 측정 화면)
    *   `DetectSpeaker` (스피커 탐지 화면)
    *   `Render` (3D 시각화 화면, `detected` 인자 포함)
    *   `TestGuide`, `KeepTest` (음향 측정 가이드 및 진행 화면)
    *   `Analysis` (분석 결과 화면, `roomId` 인자 포함)

### 2.5. `screens/SplashScreen.kt`

*   **역할**: 앱 시작 시 로고와 앱 이름을 표시하는 간단한 스플래시 화면입니다.
*   **프로세스**:
    *   `LaunchedEffect(Unit)`를 사용하여 화면이 처음 렌더링될 때 코루틴을 실행합니다.
    *   `delay(1_000)`로 1초간 대기합니다.
    *   `nav.navigate(Screen.Room.route)`를 호출하여 `RoomScreen`으로 이동하며, `popUpTo(Screen.Splash.route) { inclusive = true }`를 통해 스플래시 화면을 백스택에서 제거합니다.
    *   `Image` 컴포저블을 사용하여 `R.drawable.speaker` 이미지를 표시합니다.

### 2.6. `screens/RoomScreen.kt`

*   **역할**: 사용자가 생성한 방 목록을 관리하고, 각 방에 대한 측정 및 대화 기능을 시작하는 메인 화면입니다.
*   **주요 컴포넌트 및 로직**:
    *   **YOLO 모델 예열**: `LaunchedEffect(Unit)` 내에서 `Dispatchers.IO` 스레드에서 `Detector`를 초기화하고 `warmUp()`을 호출합니다. 이는 TFLite 모델 로딩 및 GPU 셰이더 컴파일로 인한 초기 지연을 방지하기 위한 최적화입니다.
    *   **방 목록 표시**: `vm.rooms.collectAsState()`를 통해 `RoomViewModel`에서 방 목록을 `StateFlow`로 구독하여 `LazyColumn`에 표시합니다.
    *   **플로팅 액션 버튼 (FAB)**:
        *   `fabExpanded` 상태에 따라 "새 방 만들기"와 "모든 방 삭제" 미니 FAB이 애니메이션과 함께 나타나거나 사라집니다.
        *   `animateIntAsState`를 사용하여 Y 오프셋 애니메이션을 구현하고, `zIndex`를 조절하여 메인 FAB 뒤에 미니 FAB이 위치하도록 합니다.
    *   **방 목록 아이템 상호작용**:
        *   `combinedClickable`을 사용하여 짧게 클릭(`onClick`)과 길게 클릭(`onLongClick`)을 구분합니다.
        *   **짧게 클릭**: `tappedRoom` 상태를 업데이트하여 `ModalBottomSheet`를 표시합니다. 이 시트에서는 방의 `hasMeasure` 및 `hasChat` 상태에 따라 "측정 시작", "측정 결과 보기", "새 대화", "기존 대화 이어가기" 옵션을 제공합니다.
        *   **길게 클릭**: `longPressedRoom` 상태를 업데이트하여 다른 `ModalBottomSheet`를 표시합니다. 이 시트에서는 "방 이름 바꾸기", "재측정하기", "대화 초기화", "삭제" 옵션을 제공합니다.
    *   **다이얼로그**:
        *   `EditRoomDialog`: 새 방 생성 및 방 이름 변경 시 사용됩니다. 이름 중복 검사 로직이 포함되어 있습니다.
        *   `AlertDialog`: 방 삭제 및 모든 방 삭제 시 확인 메시지를 표시합니다.
    *   **`StatusChips`**: `hasMeasure` 및 `hasChat` 상태에 따라 측정 및 대화 완료 여부를 시각적으로 표시하는 컴포넌트입니다.

### 2.7. `screens/chat/ChatScreen.kt`

*   **역할**: OpenAI API 기반 AI 챗봇과 대화하는 화면입니다.
*   **주요 컴포넌트 및 로직**:
    *   **시스템 프롬프트 로딩**: `remember` 블록 내에서 `PromptLoader.load(context)`를 사용하여 `assets/prompt/prompt001.txt` 파일에서 시스템 프롬프트를 한 번만 읽어와 사용합니다.
    *   **메시지 표시**: `vm.messages.collectAsState()`를 통해 `ChatViewModel`에서 대화 목록을 `StateFlow`로 구독하여 `LazyColumn`에 표시합니다. `reverseLayout = true`와 `LaunchedEffect`를 사용하여 최신 메시지가 항상 하단에 보이도록 하고 자동 스크롤을 구현합니다.
    *   **입력창 및 전송**: `BasicTextField`를 사용하여 사용자 메시지 입력창을 구현하고, 전송 버튼 클릭 시 `ChatViewModel.sendPrompt()` 함수를 호출합니다.
    *   **키보드 처리**: `windowInsetsPadding(WindowInsets.ime)`를 사용하여 키보드가 올라올 때 입력창이 가려지지 않고 자연스럽게 위로 밀려 올라가도록 처리합니다.
    *   **`ChatBubble` 컴포저블**: 사용자 메시지와 GPT 메시지를 구분하여 다른 배경색과 정렬로 표시합니다.

### 2.8. `api/OpenAIApi.kt` 및 `util/RetrofitClient.kt`

*   **`OpenAIApi.kt`**: Retrofit 인터페이스로, OpenAI의 `/v1/chat/completions` 엔드포인트에 `POST` 요청을 보내는 `sendPrompt` 함수를 정의합니다. `Authorization` 헤더와 `GPTRequest` 바디를 사용합니다.
*   **`RetrofitClient.kt`**: `Retrofit.Builder`를 사용하여 `https://api.openai.com/`를 기본 URL로 설정하고 `GsonConverterFactory`를 추가하여 JSON 직렬화/역직렬화를 처리합니다. `lazy` 위임을 사용하여 `OpenAIApi` 서비스 인스턴스를 지연 초기화합니다.

### 2.9. `model` 패키지 (`ChatMessage.kt`, `GPTRequest.kt`, `GPTResponse.kt`, `Message.kt`, `MeasureModels.kt`, `Speaker3D.kt`)

*   **역할**: 앱 내에서 사용되는 데이터 구조를 정의하는 데이터 클래스들입니다.
*   **`ChatMessage.kt`**: 채팅 화면에서 메시지 발신자("user" 또는 "gpt")와 내용을 담는 클래스.
*   **`GPTRequest.kt`**: OpenAI API에 전송할 요청 본문(모델, 메시지 목록, 온도)을 정의하는 클래스.
*   **`GPTResponse.kt`**: OpenAI API 응답을 파싱하기 위한 클래스 (선택지 목록 포함).
*   **`Message.kt`**: GPT 요청/응답에서 메시지의 역할(role)과 내용을 담는 클래스.
*   **`MeasureModels.kt`**: 3D 벡터(`Vec3`), 측정 단계(`MeasurePickStep`), 축 프레임(`AxisFrame`), 3D 측정 결과(`Measure3DResult`), 선택된 점(`PickedPoints`), 측정 유효성 검사(`MeasureValidation`) 등 측정 관련 데이터 모델을 정의합니다.
*   **`Speaker3D.kt`**: YOLO와 Depth API를 통해 얻은 단일 스피커의 3D 좌표(`worldPos`), 추적 ID(`id`), 마지막 감지 시간(`lastSeenNs`)을 저장하는 클래스.

### 2.10. `data` 패키지 (Room Database)

*   **`AppDatabase.kt`**: `RoomDatabase`를 상속받는 추상 클래스로, `RoomEntity`, `MeasureEntity`, `RecordingEntity`, `SpeakerEntity`를 관리합니다. `version = 1`, `exportSchema = false`로 설정되어 있습니다. 싱글톤 패턴으로 인스턴스를 제공합니다.
*   **`RoomDao.kt`**: `RoomEntity`에 대한 CRUD(Create, Read, Update, Delete) 작업을 정의하는 DAO(Data Access Object) 인터페이스. `getAll()`, `getById()`, `insert()`, `update()`, `delete()`, `deleteAll()` 함수를 포함합니다.
*   **`MeasureDao.kt`**: `MeasureEntity`에 대한 DAO 인터페이스. `insert()`, `latestByRoom()` 함수를 포함합니다.
*   **`RecordingDao.kt`**: `RecordingEntity`에 대한 DAO 인터페이스. `insert()`, `latestByRoom()`, `listByRoom()` 함수를 포함합니다.
*   **`SpeakerDao.kt`**: `SpeakerEntity`에 대한 DAO 인터페이스. `insertAll()`, `deleteByRoom()`, `listByRoom()` 함수를 포함합니다.
*   **`RoomEntity.kt`**: 방 정보를 저장하는 데이터 클래스. `id`, `title`, `hasMeasure`, `measureUpdatedAt`, `hasChat`, `lastChatPreview`, `chatUpdatedAt` 필드를 가집니다.
*   **`MeasureEntity.kt`**: 방의 측정값(폭, 깊이, 높이)을 저장하는 데이터 클래스. `roomId`를 외래 키로 가집니다.
*   **`RecordingEntity.kt`**: 음향 측정 녹음 파일 정보를 저장하는 데이터 클래스. `roomId`를 외래 키로 가집니다.
*   **`SpeakerEntity.kt`**: 스피커의 3D 위치(`x`, `y`, `z`)를 저장하는 데이터 클래스. `roomId`를 외래 키로 가집니다.

### 2.11. `repo` 패키지 (`RoomRepository.kt`, `AnalysisRepository.kt`)

*   **`RoomRepository.kt`**: `RoomDao`를 사용하여 `RoomEntity` 데이터에 대한 비즈니스 로직을 처리합니다. 방 추가, 이름 변경, 측정/대화 상태 업데이트, 삭제 등의 기능을 제공합니다. `Dispatchers.IO`를 사용하여 DB 작업을 백그라운드 스레드에서 수행합니다.
*   **`AnalysisRepository.kt`**: `RecordingDao`, `MeasureDao`, `SpeakerDao`를 사용하여 음향 측정 녹음, 방 크기 측정, 스피커 위치 데이터에 대한 저장 및 조회 기능을 제공합니다.

### 2.12. `viewmodel` 패키지 (`RoomViewModel.kt`, `ChatViewModel.kt`)

*   **`RoomViewModel.kt`**: 앱의 거의 모든 상태를 관리하는 중앙 허브 ViewModel입니다.
    *   `RoomRepository`와 `AnalysisRepository`를 통해 데이터베이스와 상호작용합니다.
    *   `rooms`, `currentRoomId`, `labeledMeasures`, `speakers` 등 다양한 `StateFlow` 및 `mutableStateListOf`를 사용하여 UI 상태를 관리합니다.
    *   `upsertSpeaker`, `pruneSpeakers` 함수를 통해 실시간 스피커 목록을 갱신하고 오래된 스피커를 제거합니다.
    *   `saveRecordingForCurrentRoom`, `saveMeasureForCurrentRoom`, `saveSpeakersSnapshot` 함수를 통해 현재 선택된 방에 측정 데이터를 저장합니다.
    *   `_speakersVersion` `StateFlow`를 사용하여 `speakers` 리스트 변경 시 Compose UI를 재구성하도록 트리거합니다.
*   **`ChatViewModel.kt`**: 채팅 화면의 상태와 로직을 관리합니다.
    *   `_messages` `MutableStateFlow`를 통해 대화 메시지 목록을 관리합니다.
    *   `sendPrompt` 함수는 `RetrofitClient.api`를 사용하여 OpenAI API에 요청을 보내고, 응답을 받아 `_messages`를 업데이트합니다. `BuildConfig.OPENAI_API_KEY`를 사용하여 API 키를 안전하게 전달합니다.

### 2.13. `screens/measure/TwoPointMeasureScreen.kt` (및 `MeasureWidthScreen`, `MeasureDepthScreen`, `MeasureHeightScreen`)

*   **역할**: ARCore를 사용하여 두 지점 간의 거리를 측정하는 범용 화면입니다. 폭, 깊이, 높이 측정에 재사용됩니다.
*   **주요 컴포넌트 및 로직**:
    *   **AR 환경 설정**: `ARSceneView`를 사용하며, `configureSession`에서 `Config.DepthMode.RAW_DEPTH_ONLY` 및 `Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL`을 설정하여 깊이 정보와 평면 감지를 활성화합니다.
    *   **탭 이벤트 처리**: `pointerInput`의 `detectTapGestures`를 사용하여 사용자의 탭 위치를 `tapQueue`에 추가합니다.
    *   **AR 프레임 루프 (`sceneView.onSessionUpdated`)**:
        *   매 프레임마다 `tapQueue`에 있는 탭 이벤트를 처리합니다.
        *   `hitTestOrDepth` 유틸리티 함수를 사용하여 탭한 2D 화면 좌표에 해당하는 3D 공간 좌표(`Vec3`)를 얻습니다.
        *   첫 번째 탭은 `firstPoint`에, 두 번째 탭은 `lastDist`에 거리를 계산하여 저장하고 `AlertDialog`를 표시합니다.
        *   **실시간 거리 표시**: 화면 중앙의 3D 좌표(`hoverPoint`)와 `firstPoint` 사이의 실시간 거리를 계산하여 UI에 업데이트합니다.
        *   **3D 시각화**: `worldToScreen` 유틸리티를 사용하여 3D 좌표를 2D 화면 좌표로 변환하고, `Canvas`에 점과 선을 그려 측정 과정을 시각적으로 안내합니다.
    *   **`MeasureCommon.kt`**: `distanceMeters`, `hitTestOrDepth`, `worldToScreen`과 같은 AR 측정 관련 유틸리티 함수를 제공합니다. `hitTestOrDepth`는 `hitTest`를 우선하고 실패 시 `RawDepthImage`를 통한 역투영을 시도합니다.

### 2.14. `screens/measure/DetectSpeakerScreen.kt`

*   **역할**: YOLOv8 모델과 ARCore를 결합하여 스피커를 탐지하고 3D 공간에 위치를 매핑하는 핵심 화면입니다.
*   **주요 컴포넌트 및 로직**:
    *   **`Phase` Enum**: `DETECT` (2D 탐지)와 `MAP` (3D 매핑) 두 단계로 나뉩니다.
    *   **Phase.DETECT (2D 스피커 탐지)**:
        *   **CameraX**: `PreviewView`와 `ImageAnalysis` UseCase를 사용하여 카메라 미리보기를 표시하고 실시간으로 이미지 프레임을 분석합니다.
        *   **`Detector`**: `ImageAnalysis`의 `setAnalyzer`를 통해 받은 `ImageProxy`를 `YuvToRgbConverter`로 RGB `Bitmap`으로 변환한 후, `Detector.detect()` 함수에 전달하여 스피커를 탐지합니다.
        *   **`OverlayView`**: 탐지된 `BoundingBox` 목록을 받아 카메라 미리보기 위에 초록색 사각형으로 스피커를 표시합니다.
        *   **중복 제거**: `dedupBoxesByIoU` 함수를 사용하여 IoU(Intersection over Union) 기반으로 중복되는 바운딩 박스를 제거하고, 신뢰도 높은 최대 2개의 스피커만 `pendingDetections`에 저장합니다.
        *   "AR 매핑으로 진행" 버튼 클릭 시 CameraX 리소스를 안전하게 해제하고 `phase`를 `Phase.MAP`으로 전환합니다.
    *   **Phase.MAP (3D 위치 매핑)**:
        *   **ARCore (`ARSceneView`)**: AR 세션을 시작하고 `Config.DepthMode.AUTOMATIC` 및 `Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL`을 설정합니다.
        *   **AR 프레임 루프 (`sceneView.onSessionUpdated`)**:
            *   `frame.camera.trackingState`를 확인하여 AR 추적 상태를 사용자에게 안내합니다.
            *   **기준 좌표계 설정**: `vm.measure3DResult`가 null일 경우, 현재 카메라의 `Pose`를 기반으로 `AxisFrame`을 한 번 설정합니다.
            *   **스피커 3D 위치 추정**: `pendingDetections`에 있는 각 2D 스피커 후보에 대해 다음 폴백 전략을 사용하여 3D 월드 좌표를 얻습니다.
                1.  **Depth API**: `frame.acquireDepthImage16Bits()`를 통해 깊이 이미지를 얻고, `sampleDepthWindowMeters` 함수로 해당 픽셀의 깊이 값(미터)을 직접 읽습니다.
                2.  **Hit Test**: 깊이 값을 얻지 못하면 `hitTestOrDepth` 함수를 사용하여 ARCore가 인식한 평면과의 교차점을 찾습니다.
                3.  **크기 기반 추정 (현재 코드에서는 미사용/보관)**: 카메라 초점거리, 실제 스피커 폭, 이미지상 픽셀 폭을 이용한 삼각법 추정.
            *   **`SimpleTracker`**: 얻은 3D 좌표에 `SimpleTracker.assignId()`를 통해 고유 ID를 부여하고, `RoomViewModel.upsertSpeaker()`를 통해 실시간 스피커 목록을 업데이트합니다.
            *   `vm.pruneSpeakers()`를 호출하여 일정 시간 동안 감지되지 않은 스피커를 목록에서 제거합니다.
        *   **UI**: 스피커 폭(cm)을 수동으로 입력할 수 있는 `OutlinedTextField`와 "렌더로 이동", "다시 탐지" 버튼을 제공합니다.
    *   **유틸리티**: `sampleDepthMeters`, `sampleDepthWindow`, `viewToImagePixels`, `getIntrinsics`, `camRayDirFromPixel`, `rotateByPose`, `toV3`, `backprojectToWorld`, `iou`, `dedupBoxesByIoU` 등 AR 및 YOLO 관련 복잡한 수학/기하학 유틸리티 함수들이 포함되어 있습니다.

### 2.15. `screens/measure/RenderScreen.kt`

*   **역할**: 측정된 방 크기와 탐지된 스피커 위치를 3D로 시각화하여 보여주는 화면입니다.
*   **주요 컴포넌트 및 로직**:
    *   **데이터 수집**: `RoomViewModel`로부터 `labeledMeasures` (방 크기), `measure3DResult` (기준 좌표계), `speakers` (스피커 3D 위치)를 구독합니다.
    *   **방 크기 추론/입력**: `inferRoomSizeFromLabels` 함수를 사용하여 `labeledMeasures`에서 방 크기를 자동으로 추론하거나, `RoomSizeInputDialog`를 통해 사용자가 직접 방 크기를 입력할 수 있도록 합니다.
    *   **스피커 좌표 변환**: 월드 좌표계의 스피커 위치(`speakers`)를 방의 로컬 좌표계로 변환(`toLocal`)하고, 근접 중복 제거(`dedupByDistance`) 및 방 중심으로 자동 정렬(`autoCenterToRoom`)하여 `speakersLocal` 리스트를 생성합니다.
    *   **3D 뷰포트 (`RoomViewport3DGL`)**:
        *   `AndroidView`를 사용하여 `GLSurfaceView`를 Compose 계층에 통합합니다.
        *   `GLRoomRenderer`라는 커스텀 OpenGL ES 2.0 렌더러를 설정합니다.
        *   `pointerInput`의 `detectTransformGestures`를 사용하여 사용자의 드래그(회전) 및 핀치(줌) 제스처를 감지하고, `yaw`, `pitch`, `zoom` 상태 변수를 업데이트합니다.
        *   `update` 콜백에서 변경된 `yaw`, `pitch`, `zoom` 값과 방 크기, 스피커 데이터를 `GLRoomRenderer`에 전달하고 `requestRender()`를 호출하여 화면을 다시 그립니다.
    *   **`GLRoomRenderer`**:
        *   OpenGL ES 2.0 셰이더를 사용하여 3D 방(반투명 면, 불투명 테두리)과 스피커(점)를 렌더링합니다.
        *   `Matrix.setLookAtM`을 사용하여 `yaw`, `pitch`, `zoom` 값에 따라 카메라(View) 매트릭스를 계산하여 오빗 컨트롤을 구현합니다.
        *   방 크기에 따라 직육면체 버텍스 데이터를 생성합니다.
    *   **상세정보 모달**: "상세정보" 버튼 클릭 시 측정값, 좌표계 정보, 스피커 로컬 좌표 등을 표시하는 `AlertDialog`를 띄웁니다.

### 2.16. `screens/measure/TestGuideScreen.kt`

*   **역할**: 음향 측정을 시작하기 전 사용자에게 가이드라인을 제공하는 화면입니다.
*   **주요 내용**: 스마트폰 위치, 주변 소음, 볼륨 설정, 측정 과정 등에 대한 안내 텍스트를 포함합니다.
*   "테스트 시작" 버튼 클릭 시 `KeepTestScreen`으로 이동합니다.

### 2.17. `screens/measure/KeepTestScreen.kt`

*   **역할**: 실제 음향 측정을 수행하고 결과를 표시하는 화면입니다.
*   **주요 컴포넌트 및 로직**:
    *   **권한 확인**: `RECORD_AUDIO` 권한이 없을 경우 `ActivityCompat.requestPermissions`를 통해 권한 요청을 수행합니다.
    *   **`DuplexMeasurer` 실행**: "측정 시작" 버튼 클릭 시 `DuplexMeasurer.runOnce()` 함수를 호출하여 스윕 신호 재생 및 동시 녹음을 수행합니다.
    *   **결과 표시**: 측정 완료 후 `peakDbfs`, `rmsDbfs` 값과 녹음된 WAV 파일 경로를 표시합니다.
    *   **데이터 저장**: `vm.setMeasure(roomId, true)`를 호출하여 현재 방의 측정 완료 상태를 업데이트하고, `vm.saveRecordingForCurrentRoom()`을 통해 녹음 파일 정보를 데이터베이스에 저장합니다.
    *   "분석으로 이동" 버튼 클릭 시 `AnalysisScreen`으로 이동합니다.

### 2.18. `screens/measure/AnalysisScreen.kt`

*   **역할**: `KeepTestScreen`에서 녹음된 음향 데이터를 분석하고 시각화하는 화면입니다.
*   **주요 컴포넌트 및 로직**:
    *   **데이터 수집**: `RoomViewModel`로부터 `latestRecording` (최신 녹음 정보), `latestMeasure` (최신 방 측정값), `savedSpeakers` (저장된 스피커 목록)를 구독합니다.
    *   **스펙트로그램 생성**: `LaunchedEffect(wavPath)` 내에서 `readMono16Wav`로 WAV 파일을 읽고, `computeSpectrogram`으로 스펙트로그램 데이터를 계산한 후, `spectrogramToBitmap`으로 비트맵 이미지를 생성하여 `Image` 컴포저블로 표시합니다.
    *   **녹음 파일 재생**: "녹음 파일 재생" 버튼 클릭 시 `playWav` 유틸리티 함수를 사용하여 녹음된 파일을 재생합니다.
    *   **측정 결과 표시**: Peak, RMS, 녹음 길이, 샘플레이트, 샘플 수 등의 정보를 표시합니다.
    *   **방 정보 표시**: 현재 방의 ID, 측정된 폭/깊이/높이, 스피커 개수 정보를 표시합니다.
    *   "방 선택으로" 버튼 클릭 시 `RoomScreen`으로 이동하며, 측정 서브그래프를 백스택에서 제거합니다.

### 2.19. `yolo` 패키지 (`BoundingBox.kt`, `Constants.kt`, `Detector.kt`, `MetaData.kt`, `OverlayView.kt`)

*   **`Detector.kt`**: YOLOv8 TFLite 모델을 사용하여 객체 탐지를 수행하는 핵심 클래스입니다.
    *   **초기화**: `TensorFlow Lite Interpreter`를 초기화하고, `GpuDelegate`를 사용하여 GPU 가속을 시도합니다. 모델의 입력/출력 텐서 형태를 파악하고, `MetaData` 또는 `label.txt`에서 라벨을 로드합니다.
    *   **`restart(useGpu: Boolean)`**: GPU/CPU 사용 여부를 토글하여 Interpreter를 재시작합니다.
    *   **`detect(squareBmp: Bitmap, origW: Int, origH: Int)`**: 입력 비트맵을 전처리(`ImageProcessor` 사용), 추론(`interpreter.run`), 후처리(`postProcess` 및 `nms`) 과정을 거쳐 `BoundingBox` 목록을 반환합니다.
    *   **`warmUp()`**: 첫 추론 시 발생하는 지연을 줄이기 위해 더미 이미지로 한 번 추론을 수행합니다.
    *   **`postProcess`**: YOLO 모델의 원시 출력을 파싱하여 `BoundingBox` 객체로 변환하고, `CONFIDENCE_THRESHOLD` 미만의 결과를 필터링합니다.
    *   **`nms` (Non-Maximum Suppression)**: `IOU_THRESHOLD`를 사용하여 중복되는 바운딩 박스를 제거하고 가장 신뢰도 높은 박스만 남깁니다.
    *   `DetectorListener` 인터페이스를 통해 탐지 결과를 콜백으로 전달합니다.
*   **`BoundingBox.kt`**: 탐지된 객체의 바운딩 박스 정보를 담는 데이터 클래스 (좌표, 중심, 너비, 높이, 신뢰도, 클래스 ID, 클래스 이름).
*   **`Constants.kt`**: YOLO 모델 파일 경로(`best_int8.tflite`) 및 라벨 파일 경로(`label.txt`)를 정의합니다.
*   **`MetaData.kt`**: TFLite 모델의 메타데이터 또는 외부 라벨 파일에서 클래스 이름을 추출하는 유틸리티를 제공합니다.
*   **`OverlayView.kt`**: `Detector`에서 탐지된 `BoundingBox` 목록을 받아 카메라 미리보기 위에 시각적으로 그리는 커스텀 `View`입니다.

### 2.20. `tracker/SimpleTracker.kt`

*   **역할**: 3D 공간에서 스피커 객체를 추적하고 고유 ID를 할당하는 간단한 트래커입니다.
*   **프로세스**:
    *   `prevPos` 맵에 이전에 감지된 스피커의 ID와 3D 위치를 저장합니다.
    *   `assignId(pos: FloatArray)` 함수는 새로운 3D 위치가 들어오면, `MERGE_DIST` (0.20m) 이내의 기존 스피커가 있는지 확인합니다.
    *   거리가 임계값 이내인 기존 스피커가 있으면 해당 ID를 재사용하고 위치를 갱신합니다.
    *   없으면 `nextId`를 증가시켜 새로운 ID를 할당하고 `prevPos`에 추가합니다.

### 2.21. `util` 패키지 (다양한 유틸리티)

*   **`AngleUtils.kt`**: ARCore `Pose`에서 yaw 각도를 계산하고, 두 각도 간의 차이를 계산하는 유틸리티.
*   **`AudioViz.kt`**: WAV 파일 읽기(`readMono16Wav`), 스펙트로그램 계산(`computeSpectrogram`), 스펙트로그램을 비트맵으로 변환(`spectrogramToBitmap`), WAV 파일 재생(`playWav`) 기능을 제공하는 음향 시각화 유틸리티.
*   **`CameraPermissionGate.kt`**: 카메라 권한이 없을 경우 요청하고, 권한이 허용될 때만 콘텐츠를 표시하는 Compose 컴포저블.
*   **`DepthUtil.kt`**: `DEPTH16` 이미지를 사용하여 2D 바운딩 박스 중심점을 3D 월드 좌표로 변환하는 유틸리티.
*   **`GeometryUtil.kt`**: `PickedPoints`를 기반으로 `AxisFrame` 및 방 크기를 계산하고, 측정값의 유효성을 검사하는 기하학 유틸리티.
*   **`ImageUtils.kt`**: `Bitmap`을 안드로이드 `Pictures/` 디렉토리에 JPEG 형식으로 저장하고 `Uri`를 반환하는 유틸리티.
*   **`MeasureDisplayFormatter.kt`**: 측정된 길이를 미터 또는 피트/인치 형식으로 포맷하는 `sealed interface` 및 구현체.
*   **`PromptLoader.kt`**: `assets` 폴더의 텍스트 파일(예: `prompt001.txt`)을 읽어오는 유틸리티.
*   **`SpeakerShotCache.kt`**: 스피커 탐지 시 촬영된 이미지의 `Uri`를 임시로 캐시하는 `ConcurrentHashMap` 기반의 싱글톤 객체.
*   **`ViewAnimation.kt`**: `View`에 플립 애니메이션을 적용하는 확장 함수.
*   **`YuvToRgbConverter.kt`**: CameraX의 `Image` 객체(YUV_420_888 형식)를 RGB `Bitmap`으로 효율적으로 변환하는 유틸리티.

### 2.22. `ui.theme` 패키지 (`Color.kt`, `Theme.kt`, `Type.kt`)

*   **역할**: Jetpack Compose 앱의 Material Design 테마를 정의합니다.
*   **`Color.kt`**: 앱에서 사용되는 색상 팔레트를 정의합니다.
*   **`Theme.kt`**: `RoomacousticTheme` 컴포저블을 통해 `MaterialTheme`을 설정합니다. 다크 모드 및 Android 12+ 기기에서의 동적 색상(Dynamic Color)을 지원합니다.
*   **`Type.kt`**: 앱에서 사용되는 텍스트 스타일(`Typography`)을 정의합니다.

## 3. 남은 기능 및 향후 계획

`PROJECT_SUMMARY.md` 및 `README.md` 파일을 참조하여 현재 진행 중이거나 향후 구현될 기능들은 다음과 같습니다.

### 3.1. 진행 중 / 개선 필요 기능

*   **챗봇 프롬프트 정교화**:
    *   더 자연스러운 대화 흐름, 정확한 정보 제공 및 오류 감소를 위한 프롬프트 엔지니어링이 진행 중입니다.
    *   사용자 질의에 대한 심층적인 이해와 전문적인 음향 지식을 결합하여, 사람과 유사한 톤으로 일관되고 유용한 응답을 제공하도록 개선이 필요합니다.
*   **사운드 분석 기능 고도화**:
    *   스마트폰 마이크를 활용한 실내 녹음 및 전문적인 음향 분석 알고리즘 통합 작업이 진행 중입니다.
    *   현재는 스펙트로그램 시각화 및 Peak/RMS 레벨 표시 정도이지만, 잔향 시간(RT60), 주파수 응답 등 상세한 음향 특성을 분석하고 시각적으로 표현하는 기능이 필요합니다.
*   **AR 측정 정밀도 및 사용자 경험 개선**:
    *   다양한 환경에서의 안정적인 AR 측정 및 직관적인 UI/UX 개선이 필요합니다.
    *   특히 `DetectSpeakerScreen`의 3D 매핑 과정에서 스피커 위치 추정의 정확도를 높이고, 사용자에게 더 명확한 안내를 제공해야 합니다.

### 3.2. 앞으로의 계획

*   **챗봇 프롬프트 최적화**:
    *   사용자 질의에 대한 심층적인 이해와 전문적인 음향 지식을 결합하여, 사람과 유사한 톤으로 일관되고 유용한 응답을 제공하도록 개선할 예정입니다.
*   **고급 사운드 분석 기능 개발**:
    *   스마트폰 내장 마이크를 통해 수집된 실내 녹음 데이터를 기반으로 잔향 시간(RT60), 주파수 응답 등 상세한 음향 특성을 분석하고, 이를 시각적으로 표현하는 기능 구현을 목표로 합니다.
*   **측정 데이터 기반 맞춤형 솔루션 제안**:
    *   측정된 방의 물리적 특성과 음향 분석 결과를 종합하여, 사용자에게 최적의 스피커 배치, 흡음재/확산재 배치 등 구체적인 룸 어쿠스틱 개선 솔루션을 제안하는 기능 개발을 계획하고 있습니다.

---
## 4. 리팩토링 내역

### 4.1 25.11.04. 리팩토링

#### 주요 기능 리팩토링 및 구조 개선

-   **데이터 영구 저장 (Room 데이터베이스 도입)**
    -   기존: 측정 데이터(방 크기, 스피커 위치 등)가 앱 실행 중에만 임시로 유지되었습니다.
    -   변경: Room 데이터베이스를 도입하여 모든 측정 데이터를 영구적으로 저장하도록 변경했습니다.
    -   이를 위해 `MeasureEntity`, `SpeakerEntity`, `RecordingEntity` 등 새로운 데이터 테이블과 이를 관리하는 `AnalysisRepository`가 추가되었습니다.

-   **'결과 조회' 전용 화면 추가**
    -   기존: 측정 플로우의 마지막 단계에서만 결과를 확인할 수 있었습니다.
    -   변경: 메인 화면에서 이미 측정이 완료된 방의 결과를 언제든지 다시 볼 수 있는 '결과 조회' 플로우가 추가되었습니다.
    -   `ResultRenderScreen.kt` (3D 결과)와 `ResultAnalysisScreen.kt` (음향 분석 결과) 화면이 새로 생성되었습니다.

-   **전문 음향 분석 기능 추가**
    -   `AcousticAnalysis.kt` 유틸리티가 추가되어, 녹음된 음향으로부터 다음과 같은 전문 지표를 계산하는 기능이 구현되었습니다.
        -   **RT60 (잔향 시간)**: 공간의 울림 정도
        -   **C50 / C80 (음성/음악 명료도)**: 소리의 선명도
    -   이 값들은 분석 화면에 표시되어 사용자에게 더 깊이 있는 정보를 제공합니다.

-   **코드 구조 개선 (UI 컴포넌트 분리)**
    -   여러 화면에서 중복되던 3D 뷰포트 코드를 `RoomViewport3DGL.kt` 라는 재사용 가능한 컴포넌트로 분리하여 코드의 유지보수성과 효율성을 높였습니다.

#### UI 버그 수정

-   **시스템 바(상단 상태바, 하단 내비게이션 바) UI 겹침 문제 해결**
    -   문제: 앱 콘텐츠가 시스템 바 뒤로 그려져 일부 UI가 가려지는 현상.
    -   해결: `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`를 11개의 모든 화면 최상단 UI 요소에 적용하여, 콘텐츠가 시스템 UI를 피해 '안전 영역'에만 그려지도록 수정했습니다.
    -   수정된 파일: `SplashScreen.kt`, `RoomScreen.kt`, `ChatScreen.kt`, `TwoPointMeasureScreen.kt`, `DetectSpeakerScreen.kt`, `RenderScreen.kt`, `TestGuideScreen.kt`, `KeepTestScreen.kt`, `AnalysisScreen.kt`, `ResultRenderScreen.kt`, `ResultAnalysisScreen.kt`

### 4.2 25.11.05. 리팩토링

#### 코드 구조 리팩토링
- **AR 추적 로직 안정화**: `RoomViewModel` 내 스피커 추적 로직을 개선했습니다. `associateAndUpsert` 함수를 새로 도입하여, 새로운 AR 프레임에서 탐지된 스피커 위치를 기존에 추적하던 스피커와 연관시킬 때 EMA(지수이동평균) 필터를 적용하도록 변경했습니다. 이를 통해 스피커 위치 값의 떨림(jitter) 현상을 줄여 추적 안정성을 높였습니다.

### 4.3 25.11.07. 리팩토링

#### 1. 신규 화면 추가 및 기능 구현

*   **`RoomAnalysisScreen` (2D 평면 분석 및 청취자 위치 최적화)**
    *   AR로 측정된 공간과 스피커 위치를 2D 평면도(Top-down View)로 시각화하는 새로운 분석 화면을 추가했습니다.
    *   사용자는 이 화면에서 직접 '청취자 위치'를 드래그하여 배치할 수 있습니다.
    *   스피커와 청취자 위치를 기반으로 **실시간 음향 배치 평가**를 수행합니다. (예: 스피커-청취자 간 정삼각형 구조, 벽과의 거리 등) 평가 결과는 점수와 상세 지표로 제공되어 최적의 청취 환경 구성을 도와줍니다.

*   **`RenderScreen` 내 수동 스피커 위치 등록 기능**
    *   3D 공간을 보여주는 `RenderScreen`에서 YOLO 객체 탐지가 실패하거나 부정확할 경우를 대비해, 사용자가 직접 스피커 위치를 추가하고 조정할 수 있는 기능을 구현했습니다.
    *   이를 통해 AR 측정 없이도 수동으로 방과 스피커를 완벽하게 구성할 수 있는 워크플로우를 완성했습니다.

*   **`ResultRenderScreen` & `ResultAnalysisScreen` (결과 화면 분리)**
    *   측정이 완료된 항목에 대해 결과를 명확히 분리하여 보여주는 두 개의 독립된 화면을 추가했습니다.
    *   **`ResultRenderScreen`**: 저장된 방의 3D 모델과 스피커 배치를 시각적으로 확인합니다.
    *   **`ResultAnalysisScreen`**: 측정된 임펄스 응답(IR) 파형, 계산된 음향 지표(RT60, C50, C80) 등 상세한 음향 분석 데이터를 제공합니다.

#### 2. 네비게이션 및 사용자 경험(UX) 개선

*   **`CameraGuideScreen` (측정 시작 가이드)**
    *   측정 프로세스 시작점에 새로운 가이드 화면을 추가했습니다.
    *   이 화면에서 사용자는 **AR 카메라를 이용한 자동 측정**을 진행할지, 또는 **직접 수치를 입력하는 수동 측정**으로 바로 넘어갈지 선택할 수 있어 사용자 편의성을 크게 향상시켰습니다.

*   **개선된 측정 흐름**
    *   새로운 화면들이 추가됨에 따라, 앱의 핵심 측정 흐름이 `CameraGuide` -> `DetectSpeaker`(스피커 탐지) -> `Render`(3D 확인 및 수동 편집) -> `RoomAnalysis`(2D 분석 및 청취자 배치) -> `TestGuide`(음향 테스트) 순으로 명확하게 재구성되었습니다.

#### 3. 핵심 로직 수정 및 안정성 향상

*   **스피커 추적 알고리즘 개선**
    *   AR 측정 중 스피커 위치를 추적할 때, EMA(지수이동평균) 필터를 적용하여 탐지된 좌표의 떨림이나 순간적인 오류를 보정하고, 더 안정적이고 부드럽게 위치가 업데이트되도록 로직을 수정했습니다.

*   **UI 안정성 및 버그 수정**
    *   앱 전반의 여러 화면에서 시스템 UI(상태 바 등)가 컨텐츠를 가리는 문제를 해결하기 위해 `WindowInsets`을 적용하여 레이아웃을 수정했습니다.
