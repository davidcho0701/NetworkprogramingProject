import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Prop Hunt 2D - GameServer
 * - 레이캐스트 사격(BULLET/RAY) 지원
 * - HIDING: 술래는 배경/오브젝트만, 플레이어 안 보임
 */
public class GameServer {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 10;

    // 월드 / 게임 상수
    private static final int HIDE_TIME_MS = 20000;
    private static final int WORLD_W = 2000;
    private static final int WORLD_H = 1200;
    private static final double PLAYER_RADIUS = 24; // 충돌 반경(px)
    private static final double OBJ_RADIUS = 22;    // 오브젝트 충돌 반경
    private static final double RAY_STEP = 8;       // 레이캐스트 step
    private static final double RAY_MAX = 1200;     // 최대 사거리

    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, PlayerData> players = new ConcurrentHashMap<>();
    private final Set<String> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, ObjectInfo> hiddenObjects = new ConcurrentHashMap<>();

    private final Random rand = new Random();
    private GameState gameState = GameState.WAITING;

    private String seekerId = null;
    private String lastSeekerId = null; // ★ 직전 술래 기억해서 연속으로 안 뽑히게

    private Theme currentTheme = Theme.SCHOOL;
    private String[] currentObjectPool = new String[0];
    private Timer disguiseTimer;

    // 맵 선택 관련
    // key = playerName, value = "SCHOOL"/"CITY"/"CONSTRUCTION"
    private final Map<String, String> playerMapSelections = new ConcurrentHashMap<>();
    private boolean allPlayersSelected = false;

    enum GameState {
        WAITING, HIDING, PLAYING, ENDED
    }

    enum Theme {
        SCHOOL, CONSTRUCTION, CITY
    }

    static class PlayerData {
        String id, name;
        int hp = 100;
        boolean isSeeker = false;
        boolean alive = true;
        String disguise = null;
        double x = 100, y = 100;

        PlayerData(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class ObjectInfo {
        String type;
        double x, y;
        boolean isPlayer;
        String playerId;

        ObjectInfo(String type, double x, double y, boolean isPlayer, String pid) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.isPlayer = isPlayer;
            this.playerId = pid;
        }
    }

    public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("🎮 서버 시작: " + PORT);

            while (true) {
                Socket s = serverSocket.accept();
                ClientHandler ch = new ClientHandler(s, this);
                clients.add(ch);
                new Thread(ch).start();
                System.out.println("✅ 연결: " + ch.clientId);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ================== 게임 시작/초기화 ==================

    /**
     * ★ 직전 술래를 제외하고 다음 술래를 공정하게 선택
     */
    private String selectNextSeekerFair() {
        if (clients.isEmpty()) return null;
        if (clients.size() == 1) {
            lastSeekerId = clients.get(0).clientId;
            return lastSeekerId;
        }

        // 직전 술래를 제외한 후보 리스트
        List<ClientHandler> candidates = new ArrayList<>();
        for (ClientHandler ch : clients) {
            if (lastSeekerId == null || !ch.clientId.equals(lastSeekerId)) {
                candidates.add(ch);
            }
        }

        // 만약 직전 술래만 남았거나, 필터링 결과가 비었다면 전체에서 다시 랜덤
        if (candidates.isEmpty()) {
            candidates.addAll(clients);
        }

        ClientHandler picked = candidates.get(rand.nextInt(candidates.size()));
        lastSeekerId = picked.clientId;
        return picked.clientId;
    }

    /**
     * 실제 게임 시작
     */
    private synchronized void startGame() {
        if (clients.size() < 2) {
            broadcast("SYSTEM:최소 2명 이상이어야 게임을 시작할 수 있습니다.");
            return;
        }

        gameState = GameState.HIDING;
        alivePlayers.clear();
        hiddenObjects.clear();

        // ★ 술래 선정 - 직전 술래 제외하고 뽑기
        seekerId = selectNextSeekerFair();
        if (seekerId == null) {
            broadcast("SYSTEM:술래를 선택할 수 없습니다.");
            return;
        }

        // 테마/오브젝트 풀
        String[] objects;
        switch (currentTheme) {
            case CONSTRUCTION -> objects = new String[]{
                    "BOX", "CIRCLEBOX", "CON", "TIRE", "BRICK", "FENCE"
            };
            case CITY -> objects = new String[]{
                    "CON", "OLDMAN", "BLUEMAN", "BLUE_CAR_H", "BLUE_CAR_V", "LIGHT",
                    "RED_CAR_H", "RED_CAR_V", "TIRE", "TRASH", "WALKMAN", "WALKWOMAN", "WOMAN"
            };
            default -> objects = new String[]{
                    "CHAIR", "TABLE", "BROWNCLEANER", "FIRESTOP", "TRASH", "WHITECLEANER"
            };
        }
        currentObjectPool = objects;

        // 플레이어 초기화
        for (ClientHandler c : clients) {
            PlayerData p = players.get(c.clientId);
            if (p == null) continue;

            p.hp = 100;
            p.alive = true;

            if (c.clientId.equals(seekerId)) {
                p.isSeeker = true;
                p.disguise = null;
                p.x = WORLD_W / 2.0;
                p.y = WORLD_H / 2.0;
            } else {
                p.isSeeker = false;
                p.disguise = objects[rand.nextInt(objects.length)];
                p.x = 300 + rand.nextInt(WORLD_W - 600);
                p.y = 200 + rand.nextInt(WORLD_H - 400);
                alivePlayers.add(p.id);
            }
        }

        // ★ 난잡함 줄인 맵 오브젝트 배치
        placeMapObjects(currentTheme);

        // 클라이언트에게 게임 시작 알림
        broadcast("GAME_START:HIDING:" + seekerId + ":" + currentTheme.name());
        sendInitialMapState();

        // 개별 역할 통지
        for (ClientHandler c : clients) {
            PlayerData p = players.get(c.clientId);
            if (p == null) continue;
            if (p.isSeeker) {
                c.send("ROLE:SEEKER:" + p.x + ":" + p.y);
            } else {
                c.send("ROLE:HIDER:" + p.disguise + ":" + p.x + ":" + p.y);
            }
        }

        // Hiding 끝나면 PLAYING 전환
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                gameState = GameState.PLAYING;
                broadcast("GAME_PLAYING");
                sendGameState();

                // 도망자 자동 변장 타이머
                if (disguiseTimer != null) {
                    try {
                        disguiseTimer.cancel();
                    } catch (Exception ignored) {
                    }
                }
                disguiseTimer = new Timer();
                disguiseTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        if (gameState != GameState.PLAYING) return;
                        for (PlayerData p : players.values()) {
                            if (!p.isSeeker && p.alive && currentObjectPool.length > 0) {
                                p.disguise = currentObjectPool[rand.nextInt(currentObjectPool.length)];
                            }
                        }
                        sendGameState();
                    }
                }, 10000, 10000);
            }
        }, HIDE_TIME_MS);
    }

    /**
     * ★ 맵별 랜덤 객체 배치 - 그리드 기반 + 최대 개수 제한으로 난잡함 줄이기
     */
    private void placeMapObjects(Theme theme) {
        hiddenObjects.clear();
        int objId = 0;
        final int MAX_OBJECTS = 45;   // 전체 최대 오브젝트 수

        String[] objectPool;
        switch (theme) {
            case CONSTRUCTION -> objectPool = new String[]{
                    "BOX", "CIRCLEBOX", "CON", "TIRE", "BRICK", "FENCE"
            };
            case CITY -> objectPool = new String[]{
                    "CON", "OLDMAN", "BLUEMAN", "BLUE_CAR_H", "BLUE_CAR_V", "LIGHT",
                    "RED_CAR_H", "RED_CAR_V", "TIRE", "TRASH", "WALKMAN", "WALKWOMAN", "WOMAN"
            };
            default -> objectPool = new String[]{
                    "CHAIR", "TABLE", "BROWNCLEANER", "FIRESTOP", "TRASH", "WHITECLEANER"
            };
        }

        // 그리드 기반 배치 (조금 널널하게)
        int gridCols = 8; // 가로 셀 수
        int gridRows = 5; // 세로 셀 수
        int cellWidth = WORLD_W / gridCols;
        int cellHeight = WORLD_H / gridRows;
        int margin = 80; // 셀 안쪽 여백

        for (int row = 0; row < gridRows && objId < MAX_OBJECTS; row++) {
            for (int col = 0; col < gridCols && objId < MAX_OBJECTS; col++) {
                // 각 셀마다 60% 확률로 1개 배치
                if (rand.nextDouble() < 0.6) {
                    int cellX = col * cellWidth + margin;
                    int cellY = row * cellHeight + margin;
                    int maxX = (col + 1) * cellWidth - margin;
                    int maxY = (row + 1) * cellHeight - margin;

                    if (maxX > cellX && maxY > cellY) {
                        double x = cellX + rand.nextInt(maxX - cellX);
                        double y = cellY + rand.nextInt(maxY - cellY);
                        String objType = objectPool[rand.nextInt(objectPool.length)];
                        hiddenObjects.put("OBJ_" + (objId++),
                                new ObjectInfo(objType, x, y, false, null));
                    }
                }

                // 약간 더 풍성하게 보이기 위한 추가 배치(20% 확률) - 여전히 최대 수 체크
                if (objId >= MAX_OBJECTS) break;
                if (rand.nextDouble() < 0.2) {
                    int cellX = col * cellWidth + margin;
                    int cellY = row * cellHeight + margin;
                    int maxX = (col + 1) * cellWidth - margin;
                    int maxY = (row + 1) * cellHeight - margin;

                    if (maxX > cellX && maxY > cellY) {
                        double x = cellX + rand.nextInt(maxX - cellX);
                        double y = cellY + rand.nextInt(maxY - cellY);
                        String objType = objectPool[rand.nextInt(objectPool.length)];
                        hiddenObjects.put("OBJ_" + (objId++),
                                new ObjectInfo(objType, x, y, false, null));
                    }
                }
            }
        }

        System.out.println("[SERVER] " + theme + " 맵 객체 " + hiddenObjects.size() + "개 랜덤 배치 완료");
    }

    private void sendInitialMapState() {
        StringBuilder sb = new StringBuilder("INITIAL_MAP:");
        hiddenObjects.forEach((id, o) ->
                sb.append(id).append(",")
                        .append(o.type).append(",")
                        .append(o.x).append(",")
                        .append(o.y).append(";"));
        broadcast(sb.toString());
        System.out.println("[SERVER] INITIAL_MAP sent, objects=" + hiddenObjects.size());
    }

    private void sendGameState() {
        StringBuilder sb = new StringBuilder("GAME_STATE:");
        sb.append(gameState.name()).append(":");

        // 플레이어들
        for (PlayerData p : players.values()) {
            sb.append(p.id).append(",")
                    .append(p.name).append(",")
                    .append(p.isSeeker).append(",")
                    .append(p.alive).append(",")
                    .append(p.hp).append(",")
                    .append(p.x).append(",")
                    .append(p.y).append(",")
                    .append(p.disguise == null ? "NONE" : p.disguise)
                    .append(";");
        }

        sb.append(":");

        // 오브젝트들
        hiddenObjects.forEach((id, o) ->
                sb.append(id).append(",")
                        .append(o.type).append(",")
                        .append(o.x).append(",")
                        .append(o.y).append(";"));

        broadcast(sb.toString());
    }

    private void broadcast(String msg) {
        for (ClientHandler c : clients) {
            c.send(msg);
        }
    }

    // ================== 맵 선택 처리 ==================

    private synchronized void handleMapSelection(String playerId, String mapName) {
        PlayerData player = players.get(playerId);
        if (player == null) return;

        playerMapSelections.put(player.name, mapName);
        broadcastMapSelections();
        checkAllPlayersSelected();
    }

    private void broadcastMapSelections() {
        StringBuilder msg = new StringBuilder("MAP_SELECTIONS");
        for (Map.Entry<String, String> entry : playerMapSelections.entrySet()) {
            msg.append(":").append(entry.getKey())
                    .append(":").append(entry.getValue());
        }
        broadcast(msg.toString());
    }

    private void checkAllPlayersSelected() {
        if (playerMapSelections.size() >= players.size() && players.size() >= 2) {
            allPlayersSelected = true;
            broadcast("ALL_SELECTED");

            // 가장 많이 선택된 맵으로 결정
            Map<String, Integer> mapCounts = new HashMap<>();
            for (String map : playerMapSelections.values()) {
                mapCounts.put(map, mapCounts.getOrDefault(map, 0) + 1);
            }

            String selectedMap = mapCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("SCHOOL");

            currentTheme = Theme.valueOf(selectedMap);

            // 5초 뒤 실제 게임 시작
            Timer startTimer = new Timer();
            startTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    startGame();
                }
            }, 5000);
        }
    }

    // ================== 레이캐스트 사격 처리 ==================

    private void handleShootRay(String shooterId, double sx, double sy, double dx, double dy) {
        PlayerData shooter = players.get(shooterId);
        if (shooter == null || !shooter.isSeeker || gameState != GameState.PLAYING) return;

        double len = Math.hypot(dx, dy);
        if (len < 1e-6) return;
        dx /= len;
        dy /= len;

        double t = 0.0;
        String hitType = "NONE";
        String hitId = null;
        double hitX = sx, hitY = sy;

        // 레이 전진하며 충돌 체크
        while (t <= RAY_MAX) {
            double px = sx + dx * t;
            double py = sy + dy * t;

            // 플레이어 먼저 체크
            for (PlayerData p : players.values()) {
                if (!p.alive || p.id.equals(shooterId)) continue;
                double dist = Math.hypot(p.x - px, p.y - py);
                if (dist <= PLAYER_RADIUS) {
                    hitType = "PLAYER";
                    hitId = p.id;
                    hitX = px;
                    hitY = py;
                    t = RAY_MAX + 1;
                    break;
                }
            }
            if ("PLAYER".equals(hitType)) break;

            // 오브젝트 체크
            for (Map.Entry<String, ObjectInfo> e : hiddenObjects.entrySet()) {
                ObjectInfo o = e.getValue();
                double dist = Math.hypot(o.x - px, o.y - py);
                if (dist <= OBJ_RADIUS) {
                    hitType = "OBJ";
                    hitId = e.getKey();
                    hitX = px;
                    hitY = py;
                    t = RAY_MAX + 1;
                    break;
                }
            }
            t += RAY_STEP;
        }

        double ex = ("NONE".equals(hitType)) ? (sx + dx * RAY_MAX) : hitX;
        double ey = ("NONE".equals(hitType)) ? (sy + dy * RAY_MAX) : hitY;

        // 총알 궤적 브로드캐스트
        broadcast("BULLET:" + sx + ":" + sy + ":" + ex + ":" + ey);

        if ("PLAYER".equals(hitType)) {
            PlayerData victim = players.get(hitId);
            if (victim != null && victim.alive) {
                // 플레이어에게 50 데미지
                victim.hp = Math.max(0, victim.hp - 50);
                broadcast("HIT:PLAYER:" + victim.id + ":" + victim.x + ":" + victim.y);

                if (victim.hp <= 0) {
                    victim.alive = false;
                    alivePlayers.remove(victim.id);
                    broadcast("PLAYER_HIT:" + victim.id + ":" + victim.name);
                }
            }
            sendGameState();
            checkGameEnd();
        } else if ("OBJ".equals(hitType)) {
            // 오브젝트에 맞춤 → 술래 HP -10
            shooter.hp = Math.max(0, shooter.hp - 10);
            broadcast("WRONG_SHOT:" + shooterId + ":" + shooter.hp);
            broadcast("HIT:OBJ:" + hitId + ":" + ex + ":" + ey);

            if (shooter.hp <= 0) {
                broadcast("SEEKER_DIED:" + shooterId);
                endGame(false);
                return;
            }
            sendGameState();
        }
    }

    private void checkGameEnd() {
        if (gameState != GameState.PLAYING) return;
        if (alivePlayers.isEmpty()) {
            endGame(true);
        }
    }

    private void endGame(boolean seekerWon) {
        gameState = GameState.ENDED;
        if (disguiseTimer != null) {
            try {
                disguiseTimer.cancel();
            } catch (Exception ignored) {
            }
            disguiseTimer = null;
        }

        if (seekerWon) {
            PlayerData seeker = players.get(seekerId);
            String name = (seeker != null ? seeker.name : "술래");
            broadcast("GAME_END:SEEKER_WIN:" + name);
        } else {
            broadcast("GAME_END:HIDERS_WIN");
        }

        // 5초 뒤 리셋
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                resetGame();
                broadcast("GAME_RESET");
            }
        }, 5000);
    }

    private synchronized void resetGame() {
        gameState = GameState.WAITING;
        seekerId = null;
        alivePlayers.clear();
        hiddenObjects.clear();
        playerMapSelections.clear();
        allPlayersSelected = false;

        // 플레이어 상태 초기화 (연결 유지)
        for (PlayerData p : players.values()) {
            p.hp = 100;
            p.alive = true;
            p.isSeeker = false;
            p.disguise = null;
            p.x = 100;
            p.y = 100;
        }
    }

    // ================== 클라이언트 핸들러 ==================

    static class ClientHandler implements Runnable {
        private final GameServer server;
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;

        final String clientId = "PLAYER_" + UUID.randomUUID().toString().substring(0, 8);

        ClientHandler(Socket s, GameServer server) {
            this.socket = s;
            this.server = server;
        }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String msg;
                while ((msg = in.readLine()) != null) {
                    handle(msg.trim());
                }
            } catch (IOException e) {
                System.out.println("⚠️ 연결 종료: " + clientId);
            } finally {
                cleanup();
            }
        }

        private void handle(String message) {
            if (message.isEmpty()) return;
            String[] parts = message.split(":", 2);
            String cmd = parts[0];

            switch (cmd) {
                case "JOIN" -> {
                    String name = (parts.length > 1 ? parts[1] : "Player");
                    server.players.put(clientId, new PlayerData(clientId, name));
                    send("JOINED:" + clientId);
                    server.broadcast("PLAYER_LIST:" + getPlayerList());
                    server.broadcast("SYSTEM:" + name + "님이 입장했습니다.");
                }
                case "SELECT_MAP" -> {
                    if (server.gameState == GameState.WAITING && parts.length > 1) {
                        server.handleMapSelection(clientId, parts[1]);
                    }
                }
                case "START_GAME" -> {
                    if (server.gameState == GameState.WAITING && server.allPlayersSelected) {
                        server.startGame();
                    }
                }
                case "MOVE" -> {
                    if (parts.length < 2) break;
                    String[] xy = parts[1].split(":");
                    if (xy.length < 2) break;

                    PlayerData p = server.players.get(clientId);
                    if (p == null || !p.alive) break;

                    // HIDING 동안 술래는 중앙 고정
                    if (server.gameState == GameState.HIDING && p.isSeeker) {
                        p.x = WORLD_W / 2.0;
                        p.y = WORLD_H / 2.0;
                    } else {
                        double newX = clamp(Double.parseDouble(xy[0]), 40, WORLD_W - 40);
                        double newY = clamp(Double.parseDouble(xy[1]), 40, WORLD_H - 40);
                        p.x = newX;
                        p.y = newY;
                    }

                    // 본인에게 에코
                    send("PLAYER_MOVE:" + clientId + ":" + p.x + ":" + p.y);

                    // 게임 중에는 모두에게 브로드캐스트
                    if (server.gameState == GameState.PLAYING) {
                        server.broadcast("PLAYER_MOVE:" + clientId + ":" + p.x + ":" + p.y);
                    }
                }
                case "SHOOT_RAY" -> {
                    if (parts.length < 2) break;
                    String[] d = parts[1].split(":");
                    if (d.length < 4) break;
                    double sx = Double.parseDouble(d[0]);
                    double sy = Double.parseDouble(d[1]);
                    double dx = Double.parseDouble(d[2]);
                    double dy = Double.parseDouble(d[3]);
                    server.handleShootRay(clientId, sx, sy, dx, dy);
                }
                case "CHAT" -> {
                    if (parts.length < 2) break;
                    PlayerData p = server.players.get(clientId);
                    String name = (p != null ? p.name : "Unknown");
                    String text = parts[1];
                    server.broadcast("CHAT:" + name + ": " + text);
                }
            }
        }

        private String getPlayerList() {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (PlayerData p : server.players.values()) {
                if (!first) sb.append(",");
                sb.append(p.name);
                first = false;
            }
            return sb.toString();
        }

        void send(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }

        private void cleanup() {
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {
            }

            // 서버 측 상태 정리
            server.clients.remove(this);
            PlayerData p = server.players.remove(clientId);
            if (p != null) {
                server.alivePlayers.remove(clientId);
                server.broadcast("PLAYER_LIST:" + getPlayerList());
                server.broadcast("SYSTEM:" + p.name + "님이 퇴장했습니다.");
            }
        }

        private static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
