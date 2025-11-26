<h1 align="center">📱 Room Acoustic</h1>
<p align="center"><i>룸 어쿠스틱 환경 조성을 위한 스마트 어플리케이션</i></p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white"/>
  <img src="https://img.shields.io/badge/ARCore-4285F4?style=for-the-badge&logo=google&logoColor=white"/>
  <img src="https://img.shields.io/badge/YOLOv8-FF5252?style=for-the-badge&logo=OpenCV&logoColor=white"/>
  <img src="https://img.shields.io/badge/OpenAI-412991?style=for-the-badge&logo=openai&logoColor=white"/>
</p>

---

## 🎯 프로젝트 개요

`Room Acoustic`은 **YOLOv8**, **Google ARCore**, **OpenAI API** 등의 최신 기술을 활용하여 사용자의 실내 공간을 정밀하게 분석하고, 최적의 스피커 배치 및 음향 환경을 조성할 수 있도록 돕는 스마트 어플리케이션입니다. 이 앱은 사용자가 자신의 공간을 이해하고, 더 나은 청취 환경을 만들 수 있도록 직관적인 측정 및 분석 도구를 제공합니다.

---

## ⚙️ 주요 기능

| 기능 | 설명 |
|---|---|
| **방 관리 및 측정 기록** | 사용자가 여러 방을 생성, 관리하고 각 방에 대한 측정 및 대화 기록을 저장합니다. |
| **AR 기반 정밀 방 크기 측정** | Google ARCore를 활용하여 방의 폭, 깊이, 높이를 단계별로 측정합니다. `RAW_DEPTH_ONLY` 모드를 사용하여 정확한 3D 공간 데이터를 확보합니다. |
| **YOLOv8 기반 실시간 스피커 탐지** | 학습된 YOLOv8 모델(TensorFlow Lite)을 통해 스마트폰 카메라 화면에서 실시간으로 스피커를 인식하고, ARCore의 Depth API 및 Hit Test를 활용하여 3D 공간 내 스피커의 위치를 추적 및 시각화합니다. |
| **3D 공간 시각화 및 배치 시뮬레이션** | 측정된 방 크기와 탐지된 스피커 위치를 기반으로 OpenGL ES 2.0을 사용하여 3D 공간을 렌더링합니다. 사용자는 3D 뷰를 자유롭게 회전하고 확대/축소하여 스피커 배치를 시각적으로 확인하고 최적화할 수 있습니다. |
| **AI 챗봇을 통한 음향 컨설팅** | OpenAI API (GPT-4o-mini) 기반 챗봇이 측정된 방 데이터와 스피커 배치 정보를 바탕으로 사용자에게 맞춤형 음향 분석 피드백 및 개선 방안을 제공합니다. |
| **사운드 테스트 및 분석 가이드** | 실내 음향 테스트를 위한 가이드를 제공하고, 스윕 신호 재생 및 동시 녹음을 통해 음향 데이터를 수집합니다. 녹음된 데이터는 스펙트로그램으로 시각화되며, Peak/RMS 레벨 등의 정보를 분석하여 사용자에게 유용한 정보를 전달합니다. |

---

## 🚀 기술 스택

| 구분 | 기술 | 상세 설명 |
|---|---|---|
| **언어** | Kotlin (Android), Python (YOLOv8 모델 학습 및 변환) | 안드로이드 앱 개발은 Kotlin, 딥러닝 모델 학습 및 변환은 Python을 사용합니다. |
| **UI 프레임워크** | Jetpack Compose | Google의 최신 선언형 UI 툴킷으로, 반응성이 뛰어나고 간결한 UI를 구축합니다. |
| **아키텍처** | MVVM (Model-View-ViewModel) | 데이터 바인딩 및 UI 로직 분리를 통해 코드의 유지보수성과 테스트 용이성을 높입니다. |
| **내비게이션** | Jetpack Navigation Compose | 단일 Activity 아키텍처에서 화면 간의 안전하고 효율적인 이동을 관리합니다. |
| **AR (증강현실)** | Google ARCore (ARSceneView, Depth API, Plane Detection) | 현실 공간의 크기 측정, 3D 공간 데이터 확보, 가상 객체 렌더링에 활용됩니다. `RAW_DEPTH_ONLY` 모드를 통해 깊이 정보를 얻습니다. |
| **AI / ML** | YOLOv8 (TensorFlow Lite), OpenAI GPT (GPT-4o-mini) | YOLOv8 모델은 `best_int8.tflite` 파일을 통해 스피커 객체를 실시간으로 탐지합니다. OpenAI GPT는 AI 챗봇을 통한 음향 컨설팅을 제공합니다. |
| **카메라 제어** | CameraX | 카메라 미리보기 화면 표시 및 이미지 프레임 실시간 분석(스피커 탐지)에 사용됩니다. |
| **데이터베이스** | Room Persistence Library | 측정된 방 정보, 대화 내용, 스피커 위치 등 앱의 로컬 데이터를 안전하게 저장하고 관리합니다. |
| **API 통신** | Retrofit, Gson | OpenAI API와 같은 RESTful API 통신을 효율적으로 처리하며, JSON 데이터를 객체로 자동 변환합니다. |
| **3D 렌더링** | OpenGL ES 2.0 | 측정된 방과 스피커 위치를 3D로 시각화하여 보여주는 데 사용됩니다. `GLSurfaceView`와 커스텀 렌더러를 통해 구현됩니다. |
| **비동기 처리** | Kotlin Coroutines, Flow | 비동기 작업을 효율적으로 관리하고, 데이터 스트림을 처리하여 반응형 프로그래밍을 지원합니다. |
| **오디오 처리** | AudioTrack, AudioRecord | 스윕 신호 재생 및 마이크를 통한 동시 녹음을 구현하여 음향 측정 기능을 제공합니다. |
| **유틸리티** | RenderScript, ByteBuffer | YUV 이미지를 RGB 비트맵으로 변환하거나, 깊이 이미지에서 픽셀 값을 추출하는 등 저수준 이미지/데이터 처리에 활용됩니다. |

---

## 🛠️ 프로젝트 구조 및 주요 모듈

RoomAcoustic 프로젝트는 기능별로 모듈화되어 있으며, 각 모듈은 특정 역할을 수행합니다.

### 1. `MainActivity.kt`
앱의 유일한 `ComponentActivity`이자 모든 Compose 화면의 호스트입니다. `NavHost`를 통해 Jetpack Navigation Compose 그래프를 정의하고, `RoomViewModel`을 앱 전반에 걸쳐 공유되는 ViewModel로 제공합니다.

### 2. `navigation/Screen.kt`
앱 내의 모든 화면 라우트를 `sealed class`로 정의하여 타입 안전성을 확보하고 내비게이션 경로를 중앙에서 관리합니다. 스플래시, 메인, 채팅, 그리고 측정 플로우(폭, 깊이, 높이, 스피커 탐지, 렌더링, 테스트 가이드, 음향 측정, 분석) 등 다양한 화면 경로를 포함합니다.

### 3. `screens` 패키지 (UI 및 화면 로직)

*   **`SplashScreen.kt`**: 앱 시작 시 로고를 표시하고 `RoomScreen`으로 자동 전환됩니다.
*   **`RoomScreen.kt`**:
    *   앱의 핵심 허브로, 사용자가 생성한 방 목록을 관리하고 표시합니다.
    *   `RoomViewModel`을 통해 방 데이터를 구독하고, 플로팅 액션 버튼(FAB) 및 방 목록 아이템 상호작용을 처리합니다.
    *   YOLO 모델의 초기 지연을 줄이기 위한 `Detector` 예열 로직이 포함되어 있습니다.
*   **`chat/ChatScreen.kt`**:
    *   OpenAI API 기반 AI 챗봇과의 대화 화면입니다.
    *   `PromptLoader`로 `chat_system.txt`, `chat_bootstrap.txt`, `chat_user_wrapper.txt`, `prompt004.txt`를 불러와 컨텍스트 JSON과 함께 GPT 요청을 구성하며, `ChatViewModel`을 통해 메시지를 주고받습니다.
    *   `LazyColumn`과 `windowInsetsPadding`을 활용하여 사용자 친화적인 채팅 UI를 제공합니다.
*   **`measure` 패키지 (측정 플로우)**:
    *   **`TwoPointMeasureScreen.kt`**: 폭, 깊이, 높이 측정에 재사용되는 AR 기반 두 점 측정 화면입니다. ARCore의 `RAW_DEPTH_ONLY` 모드를 활용하여 두 지점 간의 거리를 측정하고 시각화합니다.
    *   **`DetectSpeakerScreen.kt`**:
        *   **Phase.DETECT**: CameraX와 YOLOv8 모델을 사용하여 2D 화면 내 스피커를 탐지하고 `OverlayView`에 바운딩 박스를 그립니다.
        *   **Phase.MAP**: ARCore 세션으로 전환하여 AR 프레임에서 스피커를 재탐지하고, Depth API 또는 Hit Test를 통해 3D 공간 좌표를 추정합니다. `SimpleTracker`를 사용하여 스피커를 추적하고 `RoomViewModel`에 저장합니다.
    *   **`RenderScreen.kt`**: 측정된 방 크기와 스피커 위치를 OpenGL ES 2.0 기반의 3D 뷰포트(`GLRoomRenderer`)로 시각화합니다. 사용자 제스처를 통해 3D 뷰를 조작할 수 있습니다.
    *   **`TestGuideScreen.kt`**: 음향 측정 전 사용자 가이드를 제공합니다.
    *   **`KeepTestScreen.kt`**: `DuplexMeasurer`를 사용하여 스윕 신호 재생 및 동시 녹음을 수행하고, 녹음 결과를 표시하며 데이터베이스에 저장합니다.
    *   **`AnalysisScreen.kt`**: 녹음된 음향 데이터의 스펙트로그램을 시각화하고, Peak/RMS 레벨 등 분석 결과를 표시합니다.
*   **`result` 패키지 (결과 조회 플로우)**:
    *   **`ResultRenderScreen.kt`**: 데이터베이스에 저장된 방 크기와 스피커 위치를 불러와 최종 3D 모델을 렌더링합니다.
    *   **`ResultAnalysisScreen.kt`**: 저장된 녹음 파일을 불러와 스펙트로그램과 RT60 등 상세 음향 지표를 시각화합니다.

### 4. `api` 패키지
*   **`OpenAIApi.kt`**: Retrofit 인터페이스로, OpenAI의 `/v1/chat/completions` 엔드포인트에 대한 API 호출을 정의합니다.

### 5. `model` 패키지
*   앱 내에서 사용되는 다양한 데이터 구조를 정의하는 데이터 클래스들입니다. `ChatMessage`, `GPTRequest`, `GPTResponse`, `Message` 등 API 통신 및 UI 상태 관리에 필요한 모델들을 포함합니다. `Vec3`, `Measure3DResult`, `Speaker3D` 등 3D 측정 및 스피커 관련 모델도 정의합니다.

### 6. `data` 패키지 (Room Database)
*   **`AppDatabase.kt`**: Room 데이터베이스의 추상 클래스입니다.
*   **`RoomDao.kt`, `MeasureDao.kt`, `RecordingDao.kt`, `SpeakerDao.kt`, `ListeningEvalDao.kt`**: 각 엔티티(`RoomEntity`, `MeasureEntity`, `RecordingEntity`, `SpeakerEntity`, `ListeningEvalEntity`)에 대한 데이터 접근 객체(DAO) 인터페이스를 정의합니다.
*   **`RoomEntity.kt`, `MeasureEntity.kt`, `RecordingEntity.kt`, `SpeakerEntity.kt`, `ListeningEvalEntity.kt`**: 로컬 데이터베이스에 저장될 데이터 모델을 정의합니다.

### 7. `repo` 패키지 (Repository Pattern)
*   **`RoomRepository.kt`**: `RoomDao`를 통해 방 데이터에 대한 비즈니스 로직을 처리합니다.
*   **`AnalysisRepository.kt`**: `RecordingDao`, `MeasureDao`, `SpeakerDao`를 통해 측정 및 스피커 데이터에 대한 로직을 처리합니다.
    *   Repository 패턴을 사용하여 데이터 소스(Room DB)와 ViewModel 간의 추상화 계층을 제공합니다.

### 8. `viewmodel` 패키지 (ViewModel)
*   **`RoomViewModel.kt`**: 앱의 거의 모든 상태를 관리하는 중앙 ViewModel입니다. `RoomRepository` 및 `AnalysisRepository`와 상호작용하며, `StateFlow` 및 `mutableStateListOf`를 통해 UI 상태를 관리하고 업데이트합니다.
*   **`ChatViewModel.kt`**: 채팅 화면의 상태와 OpenAI API 통신 로직을 관리합니다.

### 9. `yolo` 패키지
*   **`Detector.kt`**: YOLOv8 TFLite 모델을 사용하여 객체 탐지를 수행하는 핵심 클래스입니다. GPU 가속, 전처리, 추론, NMS(Non-Maximum Suppression) 후처리 로직을 포함합니다.
*   **`OverlayView.kt`**: 탐지된 바운딩 박스를 카메라 미리보기 위에 그리는 커스텀 뷰입니다.

### 10. `tracker` 패키지
*   **`SimpleTracker.kt`**: 3D 공간에서 스피커 객체를 추적하고 고유 ID를 할당하는 간단한 트래커입니다.

### 11. `util` 패키지
*   `AngleUtils`, `AudioViz`, `CameraPermissionGate`, `DepthUtil`, `GeometryUtil`, `ImageUtils`, `MeasureDisplayFormatter`, `PromptLoader`, `RetrofitClient`, `SpeakerShotCache`, `ViewAnimation`, `YuvToRgbConverter` 등 다양한 유틸리티 함수 및 클래스를 포함하여 특정 기능을 지원합니다.
*    특히, `AcousticAnalysis` 는 녹음된 음원으로부터 잔향 시간(RT60)과 명료도(C50/C80)를 계산하는 핵심 신호 처리 알고리즘을 포함합니다.
---

## ⚙️ 시작하기 (Getting Started)

프로젝트를 빌드하고 실행하기 위한 기본적인 설정 안내입니다.

### 1. OpenAI API 키 설정
AI 챗봇 기능을 사용하려면 OpenAI API 키가 필요합니다. 프로젝트 루트 디렉토리(`roomacoustic/`)에 `local.properties` 파일을 생성하고, 다음 형식으로 API 키를 추가하세요.

```properties
OPENAI_API_KEY="YOUR_API_KEY_HERE"
```
`YOUR_API_KEY_HERE` 부분을 실제 OpenAI API 키로 교체해야 합니다.

### 2. 권한 허용
앱은 다음 권한을 필요로 합니다. 앱 실행 시 권한 요청 대화 상자가 표시됩니다.
*   **카메라 (`android.permission.CAMERA`)**: AR 기능 및 스피커 탐지
*   **인터넷 (`android.permission.INTERNET`)**: AI 챗봇(OpenAI) 통신
*   **녹음 (`android.permission.RECORD_AUDIO`)**: 음향 측정 기능

---

## 📅 진행 상황 및 향후 계획

### ✅ 완료된 작업
*   YOLOv8 모델 적용 및 실시간 스피커 탐지 기능 구현.
*   ARCore 기반 방 크기 측정 기능 구현.
*   **Room DB를 사용한 측정 데이터(크기, 스피커, 녹음) 영구 저장 기능 구현.**
*   **저장된 데이터를 불러와 시각화하는 결과 조회 화면 (`ResultRender`, `ResultAnalysis`) 구현.**
*   **음향 데이터 기반 전문 분석 지표(RT60, C50, C80) 계산 및 시각화 기능 구현.**
*   OpenAI API 기반 초기 챗봇 기능 구현.

### ⚠️ 진행 중 / 개선 필요
*   **챗봇 프롬프트 정교화**: 저장된 RT60, 방 크기 등 구체적인 데이터를 프롬프트에 포함시켜 더 깊이 있는 답변을 유도해야 합니다.
*   **AR 측정 정밀도 및 사용자 경험 개선**: 다양한 환경에서의 안정적인 AR 측정 및 직관적인 UI/UX 개선.

### 🧪 앞으로의 계획
*   **고급 사운드 분석 기능 개발**: 주파수 대역별 RT60 분석, 주파수 응답 그래프 등 고급 시각화 기능 구현.
*   **측정 데이터 기반 맞춤형 솔루션 제안**: 측정된 데이터를 종합하여 사용자에게 최적의 스피커 배치, 흡음재/확산재 배치 등 구체적인 룸 어쿠스틱 개선 솔루션을 제안하는 기능 개발.

---

## 🛠️ 리팩토링 내역

### 25.11.04
*   **UI 구조 개선**: 3D 렌더링 뷰(`RoomViewport3DGL`)를 재사용 가능한 컴포넌트로 분리하여 코드 중복을 제거하고 유지보수성을 향상시켰습니다.
*   **UI 버그 수정**: `WindowInsets.safeDrawing`을 모든 화면의 최상단에 적용하여, 시스템 바에 의해 UI가 가려지는 문제를 해결하고, 메인 화면의 FAB(플로팅 액션 버튼)에 부드러운 확장 애니메이션을 추가했습니다. 또한, 방 이름 생성/수정 시 명확한 오류 피드백을 제공하도록 다이얼로그를 개선했습니다.

### 25.11.05
*   **내비게이션 안정성 강화**: 결과 조회 화면에서 '뒤로 가기' 시 화면 흐름이 깨지지 않도록 내비게이션 로직을 수정했습니다.
*   **코드 구조 리팩토링**: AR 스피커 추적 시 `associateAndUpsert` 함수를 새로 도입하여, EMA 필터를 적용하였습니다. 이를 통해 스피커 위치 값의 떨림(jitter) 현상을 줄여 추적 안정성을 높였습니다.

### 25.11.07
*   **신규 화면 대거 추가**: `CameraGuideScreen`, `RoomAnalysisScreen`, `ResultAnalysisScreen`, `ResultRenderScreen`을 추가하여 사용자 경험과 분석 기능을 대폭 강화했습니다.
*   **수동 스피커 등록 기능**: `RenderScreen`에서 사용자가 직접 스피커 위치를 등록하고 편집할 수 있는 기능을 추가하여 측정 유연성을 높였습니다.
*   **네비게이션 흐름 개선**: 측정 시작 시 `CameraGuideScreen`을 통해 자동/수동 측정 옵션을 제공하고, `RoomAnalysisScreen`을 측정 플로우에 통합하여 분석 단계를 강화하는 등 전체적인 화면 흐름을 개선했습니다.

### 25.11.25
*   **챗봇 프롬프트 자산/로딩**: `chat_system.txt`, `chat_bootstrap.txt`, `chat_user_wrapper.txt`, `prompt004.txt`를 조합해 컨텍스트 JSON과 함께 GPT 요청을 구성하도록 변경했습니다.
*   **네비게이션 시작/경로**: 측정 플로우의 시작점을 `CameraGuideScreen`으로 명시하고, `RenderScreen`의 `detected` 기본값 분기 처리를 추가했습니다.
*   **청취 위치 평가 저장**: `RoomAnalysisScreen`에서 탑다운 캔버스, 4종 메트릭, 스피커 이동 제안을 안내하고 확인 시 ListeningEval을 DB(`listening_eval`)에 저장합니다.
*   **DB 스키마 확장**: `AppDatabase`를 version 3으로 올리고 `ListeningEvalEntity/Dao`를 추가했으며, 레포지토리/뷰모델에서 평가 CRUD를 처리하도록 반영했습니다.



## 📌 참고 이미지 (추후 추가 예정)

> 📸 YOLO 탐지 결과, ARCore 측정 화면, 챗봇 UI, 3D 렌더링 화면 등 추가 예정