# Prop Hunt 2D - 모듈화 구조

## 📋 개요

기존 GameClient.java의 거대한 코드베이스(2200+ 라인)를 기능별로 모듈화하여 유지보수성과 재사용성을 향상시켰습니다.

## 🏗️ 모듈 구조

### 1. **UIComponentFactory.java**

UI 컴포넌트 생성을 담당하는 팩토리 클래스

**주요 기능:**

- `createStyledButton()` - 스타일이 적용된 버튼 생성
- `createBorderedPanel()` - 테두리가 있는 패널 생성
- `createStyledTextArea()` - 스타일이 적용된 텍스트 영역 생성
- `createGradientBanner()` - 그라데이션 배너 패널 생성
- `createMapButton()` - 숨바꼭질 테마의 맵 선택 버튼 생성

**사용 예시:**

```java
JButton cityBtn = UIComponentFactory.createMapButton("🏙️ CITY", "도심의 그림자", new Color(20, 100, 180));
```

---

### 2. **DialogManager.java**

게임 다이얼로그 관리 클래스

**주요 기능:**

- `showRoleDialog()` - 역할 알림 다이얼로그 (술래/숨는 사람)
- `showDeathDialog()` - 사망 알림 다이얼로그

**사용 예시:**

```java
DialogManager.showRoleDialog(this, true);  // 술래 역할 표시
DialogManager.showDeathDialog(this);        // 사망 알림
```

---

### 3. **GameStateManager.java**

게임 상태 관리 유틸리티 클래스

**주요 기능:**

- `GameState` enum - 게임 상태 정의 (WAITING, HIDING, PLAYING, ENDED)
- `getMapDisplayName()` - 맵 이름을 한글로 변환
- `themeToFolderName()` - 테마명을 폴더명으로 변환
- `PlayerData` - 플레이어 데이터 클래스
- `ObjectInfo` - 오브젝트 정보 클래스

**사용 예시:**

```java
String displayName = GameStateManager.getMapDisplayName("CITY");  // "도시"
GameStateManager.PlayerData player = new GameStateManager.PlayerData("id1", "Player1");
```

---

### 4. **RenderUtils.java**

렌더링 유틸리티 클래스

**주요 기능:**

- `clamp()` - 값을 범위 내로 제한
- `drawOverlay()` - 반투명 오버레이 그리기
- `drawGradientBackground()` - 그라데이션 배경 그리기
- `drawVictoryCircles()` - 승리 효과 원 그리기
- `drawMiniMap()` - 미니맵 그리기
- `drawCountdown()` - 카운트다운 텍스트 그리기
- `drawHealthBar()` - HP 바 그리기

**사용 예시:**

```java
RenderUtils.drawMiniMap(g2, panelWidth, panelHeight, worldW, worldH, camX, camY);
RenderUtils.drawHealthBar(g2, x, y, 100, 10, currentHp, maxHp, Color.GREEN);
```

---

### 5. **NetworkClient.java**

네트워크 통신 담당 클래스

**주요 기능:**

- `connect()` - 서버 연결
- `startReceiving()` - 메시지 수신 스레드 시작
- `sendMessage()` - 일반 메시지 전송
- `selectMap()` - 맵 선택 메시지 전송
- `sendChat()` - 채팅 메시지 전송
- `sendMove()` - 플레이어 이동 전송
- `sendShoot()` - 사격 메시지 전송
- `requestGameStart()` - 게임 시작 요청
- `disconnect()` - 연결 종료

**사용 예시:**

```java
NetworkClient network = new NetworkClient();
network.connect("localhost", 12345, "Player1");
network.startReceiving(message -> handleMessage(message));
network.sendChat("안녕하세요!");
```

---

## 🎯 모듈화의 장점

### 1. **코드 재사용성 향상**

- UI 컴포넌트를 여러 곳에서 일관되게 재사용 가능
- 다이얼로그 생성 로직을 한 곳에서 관리

### 2. **유지보수성 개선**

- 기능별로 분리되어 코드 수정이 용이
- 각 모듈의 책임이 명확함

### 3. **테스트 용이성**

- 각 모듈을 독립적으로 테스트 가능
- 단위 테스트 작성이 쉬워짐

### 4. **확장성**

- 새로운 기능 추가 시 적절한 모듈에만 코드 추가
- 다른 프로젝트에서도 모듈 재사용 가능

---

## 📦 기존 코드와의 호환성

**중요:** 기존 GameClient.java는 **전혀 수정되지 않았습니다!**

- 모든 모듈은 독립적인 클래스로 생성됨
- 필요 시 GameClient에서 모듈을 호출하여 사용 가능
- 점진적으로 리팩토링 가능

---

## 🔄 향후 리팩토링 계획

1. **Phase 1 (완료)** ✅

   - 독립적인 헬퍼 클래스 생성
   - 기존 코드 유지

2. **Phase 2 (선택사항)**

   - GameClient에서 모듈 활용
   - 중복 코드 제거

3. **Phase 3 (선택사항)**
   - MVC 패턴 적용
   - GameModel, GameView, GameController 분리

---

## 📝 컴파일 및 실행

```bash
# 모듈 컴파일
cd src
javac UIComponentFactory.java DialogManager.java GameStateManager.java RenderUtils.java NetworkClient.java

# 전체 프로젝트 컴파일
javac *.java

# 서버 실행
java -cp . GameServer

# 클라이언트 실행
java -cp . GameClient
```

---

## 👥 기여

모듈화 작업은 기존 기능을 해치지 않으면서 코드 품질을 향상시키는 것을 목표로 합니다.
새로운 기능 추가 시 적절한 모듈을 활용해주세요!
