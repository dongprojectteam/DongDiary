# DongDiary (동다이어리) 📔

DongDiary는 소중한 일상을 기록하고, 과거의 오늘을 추억할 수 있는 개인용 일기 앱입니다. 사진 첨부 기능과 구글 드라이브 클라우드 백업을 지원하여 당신의 기록을 안전하게 보관합니다.

## ✨ 주요 기능

- **일기 작성 및 수정**: 텍스트와 여러 장의 사진을 곁들인 일기를 작성할 수 있습니다.
- **캘린더 뷰**: 달력 형태로 작성된 일기 현황을 한눈에 파악하고 지난 기록을 쉽게 찾을 수 있습니다.
- **과거의 오늘**: 오늘 날짜를 기준으로 지난 연도의 같은 날 작성했던 일기를 함께 보여주어 추억을 되새길 수 있게 도와줍니다.
- **클라우드 백업 및 복원**: 구글 드라이브(Google Drive API)를 연동하여 기기를 변경하더라도 소중한 일기를 잃어버리지 않게 백업하고 복원할 수 있습니다.
- **자동 백업**: 설정에서 앱 종료 시 자동 백업 기능을 켜서 데이터를 안전하게 유지할 수 있습니다.

## 🛠 기술 스택

- **Platform**: Android
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Data Management**: 
  - [DataStore (Preferences)](https://developer.android.com/topic/libraries/architecture/datastore): 앱 설정 정보 저장
  - [Kotlinx Serialization (JSON)](https://github.com/Kotlin/kotlinx.serialization): 일기 데이터 직렬화 및 로컬 저장
- **Network & Cloud**:
  - [Google Identity (Google Login)](https://developers.google.com/identity): 구글 계정 연동
  - [Google Drive API v3](https://developers.google.com/drive): 클라우드 백업 서비스
- **Image Handling**:
  - [Coil](https://coil-kt.github.io/coil/compose/): Compose용 이미지 로딩 라이브러리

## 🚀 시작하기

### 1. 사전 요구 사항
- Android Studio Ladybug (2024.2.1) 이상 권장
- Android SDK 24 (Nougat) 이상 기기 및 에뮬레이터

### 2. 프로젝트 설정 (Secrets)
보안을 위해 앱 서명 정보 및 API 클라이언트 정보는 별도로 관리됩니다.

- **local.properties**: 루트 디렉토리에 다음 정보를 추가해야 빌드 및 배포가 가능합니다.
  ```properties
  RELEASE_STORE_FILE=key/release-key.jks
  RELEASE_STORE_PASSWORD={당신의 비밀번호}
  RELEASE_KEY_ALIAS={당신의 키 별칭}
  RELEASE_KEY_PASSWORD={당신의 키 비밀번호}
  ```

### 3. 빌드 및 테스트
- 프로젝트를 로드한 후 `npm run dev` (Gradle Sync) 과정을 거쳐 실행합니다.

## 📄 라이선스
저 개인이 재미와 학습을 위해 만든 것으로 그 누구나 사용하셔도 상관 없습니다. 아아키텍처 이런건 젼허 없습니다 ㅎㅎ

---
Created by [doptsw]
