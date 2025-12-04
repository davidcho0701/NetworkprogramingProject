import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 모듈화된 게임 서버 - 메인 클래스
 */
public class GameServerModular {
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, GameData.PlayerData> players = new ConcurrentHashMap<>();
    private final Set<String> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, GameData.ObjectInfo> hiddenObjects = new ConcurrentHashMap<>();

    private final GameLogic gameLogic = new GameLogic();
    private final Random random = new Random();

    private GameConstants.GameState gameState = GameConstants.GameState.WAITING;
    private String seekerId = null;
    private GameConstants.Theme currentTheme = GameConstants.Theme.SCHOOL;
    private List<GameData.ObjectInfo> initialMapObjects;
    private long gameStartTime = 0; // 게임 시작 시간
    private Timer gameTimer = null; // PLAYING 시계용 타이머

    public static void main(String[] args) {
        new GameServerModular().start();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(GameConstants.SERVER_PORT);
            System.out.println("🎮 서버 시작: " + GameConstants.SERVER_PORT);

            // 초기 맵 생성
            initialMapObjects = gameLogic.generateInitialMap();

            // 클라이언트 연결 수락 루프
            while (true) {
                Socket clientSocket = serverSocket.accept();
                if (clients.size() >= GameConstants.MAX_PLAYERS) {
                    clientSocket.close();
                    continue;
                }

                String clientId = "PLAYER_" + Integer.toHexString(random.nextInt());
                ClientHandler handler = new ClientHandler(clientSocket, clientId, this);
                clients.add(handler);
                new Thread(handler).start();

                System.out.println("✅ 새 클라: " + clientId);
                sendInitialMapToClient(handler);
            }
        } catch (IOException e) {
            System.err.println("서버 오류: " + e.getMessage());
        }
    }

    /**
     * 클라이언트에게 초기 맵 전송
     */
    private void sendInitialMapToClient(ClientHandler client) {
        StringBuilder mapData = new StringBuilder(NetworkProtocol.MSG_INITIAL_MAP);
        for (GameData.ObjectInfo obj : initialMapObjects) {
            mapData.append(":").append(NetworkProtocol.encodeObjectInfo(obj));
        }
        client.sendMessage(mapData.toString());
    }

    /**
     * 게임 시작
     */
    public synchronized void startGame(String initiatorId) {
        if (gameState != GameConstants.GameState.WAITING || players.size() < 2)
            return;

        seekerId = gameLogic.selectRandomSeeker(players);
        gameState = GameConstants.GameState.HIDING;

        gameStartTime = System.currentTimeMillis();

        // 모든 플레이어 초기화
        for (GameData.PlayerData player : players.values()) {
            player.isSeeker = player.id.equals(seekerId);
            player.alive = true;
            player.hp = GameConstants.PLAYER_MAX_HP;
            player.disguise = null;
            player.canMove = !player.isSeeker; // 술래는 처음에 움직일 수 없음
            player.lastDisguiseChangeTime = gameStartTime;
        }

        alivePlayers.clear();
        alivePlayers.addAll(players.keySet());

        broadcast(NetworkProtocol.MSG_GAME_START + ":" + seekerId + ":" +
                GameConstants.HIDE_TIME_MS + ":" + currentTheme);

        // 숨기 시간 후 게임 시작
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameServerModular.this) {
                    if (gameState == GameConstants.GameState.HIDING) {
                        gameState = GameConstants.GameState.PLAYING;
                        broadcast(NetworkProtocol.MSG_GAME_STATE + ":PLAYING");
                        
                        // PLAYING 상태에서 1분 제한 시간 타이머 시작
                        startGameTimeLimit();
                    }
                }
            }
        }, GameConstants.HIDE_TIME_MS);
    }

    /**
     * 플레이어 이동 처리
     */
    public void handlePlayerMove(String playerId, double x, double y) {
        GameData.PlayerData player = players.get(playerId);
        if (player == null || !gameLogic.isValidPosition(x, y))
            return;

        // 술래 freeze 시간 체크
        if (player.isSeeker && !gameLogic.canSeekerMove(gameStartTime)) {
            return; // 술래는 아직 움직일 수 없음
        }

        if (player.canMove) {
            player.x = x;
            player.y = y;
            broadcast(NetworkProtocol.MSG_PLAYER_MOVE + ":" + playerId + ":" + x + ":" + y);
        }
    }

    /**
     * 변장 변경 처리
     */
    public void handleDisguiseChange(String playerId) {
        GameData.PlayerData player = players.get(playerId);
        if (player == null || player.isSeeker || !player.alive)
            return;

        if (gameLogic.canChangeDisguise(player)) {
            player.disguise = gameLogic.getRandomDisguise();
            player.lastDisguiseChangeTime = System.currentTimeMillis();
            broadcast("DISGUISE_CHANGE:" + playerId + ":" + player.disguise);
        }
    }

    /**
     * 사격 처리
     */
    public void handleShoot(String shooterId, double startX, double startY, double dirX, double dirY) {
        GameData.PlayerData shooter = players.get(shooterId);
        if (shooter == null || !shooter.isSeeker || !shooter.alive)
            return;
        if (gameState != GameConstants.GameState.PLAYING)
            return;

        GameLogic.RaycastResult result = gameLogic.performRaycast(
                startX, startY, dirX, dirY, players, hiddenObjects);

        // 총알 궤적 브로드캐스트
        broadcast(NetworkProtocol.MSG_BULLET + ":" + startX + ":" + startY + ":" +
                result.hitX + ":" + result.hitY);

        if (result.hitPlayer && result.hitPlayerId != null) {
            // 플레이어 명중
            handlePlayerHit(result.hitPlayerId);
        } else if (result.hitObjectType != null) {
            // 가짜 오브젝트 명중 - 술래 HP 감소
            shooter.hp = Math.max(0, shooter.hp - 20);
            broadcast(NetworkProtocol.MSG_DAMAGE + ":" + shooterId + ":" + shooter.hp);
        }
    }

    /**
     * 플레이어 명중 처리
     */
    private void handlePlayerHit(String playerId) {
        GameData.PlayerData player = players.get(playerId);
        if (player != null && player.alive) {
            player.alive = false;
            alivePlayers.remove(playerId);
            broadcast(NetworkProtocol.MSG_PLAYER_DEATH + ":" + playerId);

            // 게임 종료 확인
            GameLogic.GameEndResult result = gameLogic.checkGameEnd(players);
            if (result.isGameOver) {
                endGame(result.message);
            }
        }
    }

    /**
     * 게임 종료
     */
    private void endGame(String message) {
        // 타이머가 돌고 있으면 취소
        if (gameTimer != null) {
            try {
                gameTimer.cancel();
            } catch (Exception ignored) {}
            gameTimer = null;
        }

        gameState = GameConstants.GameState.ENDED;
        broadcast(NetworkProtocol.MSG_GAME_END + ":" + message);

        // 5초 후 대기 상태로 복귀
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameServerModular.this) {
                    gameState = GameConstants.GameState.WAITING;
                    broadcast(NetworkProtocol.MSG_GAME_STATE + ":WAITING");
                }
            }
        }, 5000);
    }

    /**
     * 게임 시간 제한(1분) 타이머 시작
     * PLAYING 상태에서 1분 내에 술래가 모든 도망자를 잡지 못하면 도망자 승리
     */
    private void startGameTimeLimit() {
        final long GAME_TIME_LIMIT_MS = 60000; // 1분(60초)

        // 이전 타이머가 있다면 취소
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }

        gameTimer = new Timer();
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameServerModular.this) {
                    if (gameState != GameConstants.GameState.PLAYING) {
                        return;
                    }

                    long elapsed = System.currentTimeMillis() - gameStartTime;
                    long remainingMs = GAME_TIME_LIMIT_MS - elapsed;
                    int remainingSec = (int) Math.max(0, (remainingMs + 999) / 1000);

                    // 브로드캐스트로 클라이언트에 남은 초 전송
                    broadcast(NetworkProtocol.MSG_COUNTDOWN + ":" + remainingSec);

                    if (remainingMs <= 0) {
                        // 시간 초과: 도망자 승리
                        gameTimer.cancel();
                        gameTimer = null;
                        endGame("HIDERS_WIN");
                        System.out.println("⏱️ 1분 경과 - 도망자 승리!");
                    }
                }
            }
        }, 0, 1000);
    }
    public void broadcast(String message) {
        clients.removeIf(client -> !client.isConnected());
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    /**
     * 플레이어 추가
     */
    public void addPlayer(String playerId, String playerName) {
        GameData.PlayerData player = new GameData.PlayerData(playerId, playerName);
        players.put(playerId, player);

        // 플레이어 목록 업데이트 브로드캐스트
        broadcastPlayerList();
    }

    /**
     * 플레이어 제거
     */
    public void removePlayer(String playerId) {
        players.remove(playerId);
        alivePlayers.remove(playerId);
        broadcastPlayerList();

        // 게임 중이고 술래가 나간 경우 게임 종료
        if (playerId.equals(seekerId) && gameState == GameConstants.GameState.PLAYING) {
            endGame("술래가 나갔습니다. 숨는 사람 승리!");
        }
    }

    /**
     * 플레이어 목록 브로드캐스트
     */
    private void broadcastPlayerList() {
        StringBuilder playerList = new StringBuilder(NetworkProtocol.MSG_PLAYER_UPDATE);
        for (GameData.PlayerData player : players.values()) {
            playerList.append(":").append(NetworkProtocol.encodePlayerData(player));
        }
        broadcast(playerList.toString());
    }

    /**
     * 채팅 메시지 브로드캐스트
     */
    public void broadcastChat(String senderId, String message) {
        GameData.PlayerData sender = players.get(senderId);
        String senderName = sender != null ? sender.name : "Unknown";
        broadcast(NetworkProtocol.MSG_CHAT + ":" + senderName + ":" + message);
    }

    // Getter 메서드들
    public GameConstants.GameState getGameState() {
        return gameState;
    }

    public Map<String, GameData.PlayerData> getPlayers() {
        return players;
    }
}