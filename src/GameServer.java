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

    private static final int HIDE_TIME_MS = 20000;
    private static final int WORLD_W = 2000;
    private static final int WORLD_H = 1200;
    private static final double PLAYER_RADIUS = 24; // 충돌 반경(px)
    private static final double OBJ_RADIUS = 22; // 오브젝트 충돌 반경
    private static final double RAY_STEP = 8; // 레이캐스트 보행(step)
    private static final double RAY_MAX = 1200; // 최대 사거리

    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Map<String, PlayerData> players = new ConcurrentHashMap<>();
    private final Set<String> alivePlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, ObjectInfo> hiddenObjects = new ConcurrentHashMap<>();

    private final Random rand = new Random();
    private GameState gameState = GameState.WAITING;
    private String seekerId = null;
    private Theme currentTheme = Theme.SCHOOL;
    private String[] currentObjectPool = new String[0];
    private Timer disguiseTimer;

    enum GameState {
        WAITING, HIDING, PLAYING, ENDED
    }

    enum Theme {
        SCHOOL, CONSTRUCTION, MILITARY
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

    // ====== 게임 시작 ======
    private synchronized void startGame() {
        if (clients.size() < 2) {
            broadcast("SYSTEM:최소 2명 필요");
            return;
        }
        gameState = GameState.HIDING;
        alivePlayers.clear();
        hiddenObjects.clear();

        // 술래 선정
        seekerId = clients.get(rand.nextInt(clients.size())).clientId;

        // 테마/오브젝트 풀
        String[] objects;
        Theme[] themes = Theme.values();
        currentTheme = themes[rand.nextInt(themes.length)];
        switch (currentTheme) {
            case CONSTRUCTION -> objects = new String[] { "BOX", "BARREL", "CONE", "TIRE", "CRATE", "TABLE" };
            case MILITARY -> objects = new String[] { "CRATE", "BOX", "TIRE", "BARREL", "CHAIR", "TABLE" };
            default ->
                objects = new String[] { "BOX", "CHAIR", "TABLE", "PLANT", "LAMP", "BOOK", "BARREL", "TIRE", "CONE" };
        }
        currentObjectPool = objects;

        // 플레이어 초기화
        for (ClientHandler c : clients) {
            PlayerData p = players.get(c.clientId);
            if (p == null)
                continue;
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

        // 가짜 오브젝트 배치
        int fakeCount = 45;
        for (int i = 0; i < fakeCount; i++) {
            String t = objects[rand.nextInt(objects.length)];
            double x = 80 + rand.nextInt(WORLD_W - 160);
            double y = 80 + rand.nextInt(WORLD_H - 160);
            hiddenObjects.put("OBJ_" + i, new ObjectInfo(t, x, y, false, null));
        }

        broadcast("GAME_START:HIDING:" + seekerId + ":" + currentTheme.name());
        sendInitialMapState();
        // 개별 역할 통지
        for (ClientHandler c : clients) {
            PlayerData p = players.get(c.clientId);
            if (p == null)
                continue;
            if (p.isSeeker)
                c.send("ROLE:SEEKER:" + p.x + ":" + p.y);
            else
                c.send("ROLE:HIDER:" + p.disguise + ":" + p.x + ":" + p.y);
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                gameState = GameState.PLAYING;
                broadcast("GAME_PLAYING");
                sendGameState();

                // 도망자 변장 주기적 변경 타이머(10초)
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
                        if (gameState != GameState.PLAYING)
                            return;
                        for (PlayerData p : players.values()) {
                            if (!p.isSeeker && p.alive) {
                                // 랜덤 변장 변경 (MAN 제외: 풀에 없음)
                                if (currentObjectPool.length > 0) {
                                    p.disguise = currentObjectPool[rand.nextInt(currentObjectPool.length)];
                                }
                            }
                        }
                        sendGameState();
                    }
                }, 10000, 10000);
            }
        }, HIDE_TIME_MS);
    }

    private void sendInitialMapState() {
        StringBuilder sb = new StringBuilder("INITIAL_MAP:");
        hiddenObjects.forEach((id, o) -> sb.append(id).append(",").append(o.type).append(",")
                .append(o.x).append(",").append(o.y).append(";"));
        broadcast(sb.toString());
        System.out.println("[SERVER] INITIAL_MAP sent, objects=" + hiddenObjects.size());
    }

    private void sendGameState() {
        StringBuilder sb = new StringBuilder("GAME_STATE:");
        sb.append(gameState.name()).append(":");
        for (PlayerData p : players.values()) {
            sb.append(p.id).append(",").append(p.name).append(",")
                    .append(p.isSeeker).append(",").append(p.alive).append(",")
                    .append(p.hp).append(",").append(p.x).append(",").append(p.y).append(",")
                    .append(p.disguise == null ? "NONE" : p.disguise).append(";");
        }
        sb.append(":");
        hiddenObjects.forEach((id, o) -> sb.append(id).append(",").append(o.type).append(",")
                .append(o.x).append(",").append(o.y).append(";"));
        broadcast(sb.toString());
    }

    private void broadcast(String msg) {
        for (ClientHandler c : clients)
            c.send(msg);
    }

    // ====== 레이캐스트 사격 처리 ======
    private void handleShootRay(String shooterId, double sx, double sy, double dx, double dy) {
        PlayerData shooter = players.get(shooterId);
        if (shooter == null || !shooter.isSeeker || gameState != GameState.PLAYING)
            return;

        // 정규화
        double len = Math.hypot(dx, dy);
        if (len < 1e-6)
            return;
        dx /= len;
        dy /= len;

        double t = 0.0;
        String hitType = "NONE";
        String hitId = null;
        double hitX = sx, hitY = sy;

        // 후보(플레이어/오브젝트)와의 첫 충돌을 찾기 위해 샘플링
        while (t <= RAY_MAX) {
            double px = sx + dx * t;
            double py = sy + dy * t;

            // 플레이어 먼저
            for (PlayerData p : players.values()) {
                if (!p.alive || p.id.equals(shooterId))
                    continue;
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
            if ("PLAYER".equals(hitType))
                break;

            // 오브젝트
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

        double ex = (hitType.equals("NONE")) ? (sx + dx * RAY_MAX) : hitX;
        double ey = (hitType.equals("NONE")) ? (sy + dy * RAY_MAX) : hitY;

        // 총알 궤적 브로드캐스트
        broadcast("BULLET:" + sx + ":" + sy + ":" + ex + ":" + ey);

        if ("PLAYER".equals(hitType)) {
            PlayerData victim = players.get(hitId);
            if (victim != null && victim.alive) {
                // 플레이어에게 50 데미지 적용 (두 번 맞으면 발각)
                victim.hp = Math.max(0, victim.hp - 50);
                // 히트 이펙트는 항상 전송
                broadcast("HIT:PLAYER:" + victim.id + ":" + victim.x + ":" + victim.y);

                // 체력이 0이 되면 사망 처리 및 알림
                if (victim.hp <= 0) {
                    victim.alive = false;
                    alivePlayers.remove(victim.id);
                    // 기존 클라이언트 호환: 사망 시에만 PLAYER_HIT 전송(=죽음 처리)
                    broadcast("PLAYER_HIT:" + victim.id + ":" + victim.name);
                }
            }
            sendGameState();
            checkGameEnd();
        } else if ("OBJ".equals(hitType)) {
            // 오브젝트 맞춤: 술래 HP -10
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
        if (gameState != GameState.PLAYING)
            return;
        if (alivePlayers.isEmpty())
            endGame(true);
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
        if (seekerWon)
            broadcast("GAME_END:SEEKER_WIN:" + players.get(seekerId).name);
        else
            broadcast("GAME_END:HIDERS_WIN");
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                broadcast("GAME_RESET");
                gameState = GameState.WAITING;
            }
        }, 5000);
    }

    // ====== 클라이언트 핸들러 ======
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
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String msg;
                while ((msg = in.readLine()) != null)
                    handle(msg);
            } catch (IOException e) {
                System.out.println("⚠️ 연결 종료: " + clientId);
            } finally {
                cleanup();
            }
        }

        private void handle(String message) {
            String[] parts = message.split(":", 2);
            String cmd = parts[0];

            switch (cmd) {
                case "JOIN" -> {
                    String name = parts[1];
                    server.players.put(clientId, new PlayerData(clientId, name));
                    send("JOINED:" + clientId);
                    server.broadcast("PLAYER_LIST:" + getPlayerList());
                    server.broadcast("SYSTEM:" + name + "님이 입장했습니다.");
                }
                case "START_GAME" -> {
                    if (server.gameState == GameState.WAITING)
                        server.startGame();
                }
                case "MOVE" -> {
                    String[] xy = parts[1].split(":");
                    PlayerData p = server.players.get(clientId);
                    if (p == null)
                        break;
                    // HIDING 동안 술래는 중앙 고정
                    if (server.gameState == GameState.HIDING && p.isSeeker) {
                        p.x = WORLD_W / 2.0;
                        p.y = WORLD_H / 2.0;
                    } else {
                        p.x = clamp(Double.parseDouble(xy[0]), 40, WORLD_W - 40);
                        p.y = clamp(Double.parseDouble(xy[1]), 40, WORLD_H - 40);
                    }
                    // 본인에게는 즉시 에코
                    send("PLAYER_MOVE:" + clientId + ":" + p.x + ":" + p.y);

                    if (server.gameState == GameState.PLAYING) {
                        server.broadcast("PLAYER_MOVE:" + clientId + ":" + p.x + ":" + p.y);
                    } else if (server.gameState == GameState.HIDING) {
                        // 숨는사람끼리만 공유
                        for (ClientHandler c : server.clients) {
                            PlayerData tp = server.players.get(c.clientId);
                            if (tp != null && !tp.isSeeker)
                                c.send("PLAYER_MOVE:" + clientId + ":" + p.x + ":" + p.y);
                        }
                    }
                }
                case "SHOOT_RAY" -> {
                    // SHOOT_RAY:sx:sy:dx:dy
                    String[] a = parts[1].split(":");
                    double sx = Double.parseDouble(a[0]);
                    double sy = Double.parseDouble(a[1]);
                    double dx = Double.parseDouble(a[2]);
                    double dy = Double.parseDouble(a[3]);
                    server.handleShootRay(clientId, sx, sy, dx, dy);
                }
                case "CHAT" -> {
                    PlayerData s = server.players.get(clientId);
                    if (s != null)
                        server.broadcast("CHAT:" + s.name + ":" + parts[1]);
                }
            }
        }

        private String getPlayerList() {
            StringBuilder sb = new StringBuilder();
            for (PlayerData p : server.players.values())
                sb.append(p.name).append(",");
            return sb.toString();
        }

        void send(String m) {
            if (out != null)
                out.println(m);
        }

        private void cleanup() {
            server.clients.remove(this);
            PlayerData p = server.players.remove(clientId);
            if (p != null) {
                server.broadcast("SYSTEM:" + p.name + "님이 퇴장했습니다.");
                server.broadcast("PLAYER_LIST:" + getPlayerList());
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
