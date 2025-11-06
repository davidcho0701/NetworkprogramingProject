import java.util.*;

/**
 * 게임 로직 처리를 담당하는 클래스
 */
public class GameLogic {
    private final Random random = new Random();

    /**
     * 초기 맵 오브젝트들을 생성
     */
    public List<GameData.ObjectInfo> generateInitialMap() {
        List<GameData.ObjectInfo> objects = new ArrayList<>();
        String[] types = GameConstants.OBJECT_TYPES;

        // 48개의 랜덤 오브젝트 생성
        for (int i = 0; i < 48; i++) {
            String type = types[random.nextInt(types.length)];
            double x = 80 + random.nextDouble() * (GameConstants.WORLD_WIDTH - 160);
            double y = 80 + random.nextDouble() * (GameConstants.WORLD_HEIGHT - 160);
            objects.add(new GameData.ObjectInfo(type, x, y));
        }

        return objects;
    }

    /**
     * 술래를 랜덤하게 선택
     */
    public String selectRandomSeeker(Map<String, GameData.PlayerData> players) {
        if (players.isEmpty())
            return null;

        List<String> playerIds = new ArrayList<>(players.keySet());
        return playerIds.get(random.nextInt(playerIds.size()));
    }

    /**
     * 레이캐스팅을 수행하여 히트 검사
     */
    public RaycastResult performRaycast(double startX, double startY, double dirX, double dirY,
            Map<String, GameData.PlayerData> players,
            Map<String, GameData.ObjectInfo> objects) {

        double step = GameConstants.RAY_STEP;
        double maxDistance = GameConstants.RAY_MAX_DISTANCE;

        for (double dist = 0; dist < maxDistance; dist += step) {
            double x = startX + dirX * dist;
            double y = startY + dirY * dist;

            // 맵 경계 확인
            if (x < 0 || x > GameConstants.WORLD_WIDTH ||
                    y < 0 || y > GameConstants.WORLD_HEIGHT) {
                return new RaycastResult(x, y, null, null, false);
            }

            // 플레이어와의 충돌 확인
            for (GameData.PlayerData player : players.values()) {
                if (!player.alive || player.isSeeker)
                    continue;

                double dx = x - player.x;
                double dy = y - player.y;
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance <= GameConstants.PLAYER_RADIUS) {
                    return new RaycastResult(x, y, player.id, null, true);
                }
            }

            // 오브젝트와의 충돌 확인
            for (GameData.ObjectInfo obj : objects.values()) {
                double dx = x - obj.x;
                double dy = y - obj.y;
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance <= GameConstants.OBJECT_RADIUS) {
                    return new RaycastResult(x, y, null, obj.type, false);
                }
            }
        }

        // 최대 사거리에 도달
        double endX = startX + dirX * maxDistance;
        double endY = startY + dirY * maxDistance;
        return new RaycastResult(endX, endY, null, null, false);
    }

    /**
     * 플레이어가 죽었는지 확인하고 게임 종료 조건 체크
     */
    public GameEndResult checkGameEnd(Map<String, GameData.PlayerData> players) {
        long aliveHiders = players.values().stream()
                .filter(p -> !p.isSeeker && p.alive)
                .count();

        if (aliveHiders == 0) {
            return new GameEndResult(true, "술래 승리! 모든 숨는 사람을 찾았습니다!");
        }

        // 여기에 시간 제한 로직도 추가할 수 있음
        return new GameEndResult(false, null);
    }

    /**
     * 플레이어 위치가 유효한지 확인 (벽 충돌 방지)
     */
    public boolean isValidPosition(double x, double y) {
        return x >= GameConstants.MIN_X && x <= GameConstants.MAX_X &&
                y >= GameConstants.MIN_Y && y <= GameConstants.MAX_Y;
    }

    /**
     * 술래가 움직일 수 있는지 확인 (freeze 시간 체크)
     */
    public boolean canSeekerMove(long gameStartTime) {
        return System.currentTimeMillis() - gameStartTime >= GameConstants.SEEKER_FREEZE_TIME_MS;
    }

    /**
     * 도둑이 변장을 바꿀 수 있는지 확인 (10초 간격)
     */
    public boolean canChangeDisguise(GameData.PlayerData player) {
        long currentTime = System.currentTimeMillis();
        return currentTime - player.lastDisguiseChangeTime >= GameConstants.DISGUISE_CHANGE_INTERVAL_MS;
    }

    /**
     * 랜덤 변장 선택 (MAN 제외)
     */
    public String getRandomDisguise() {
        String[] types = GameConstants.DISGUISE_TYPES;
        return types[random.nextInt(types.length)];
    }

    /**
     * 플레이어 속도 계산 (술래와 도둑 구분)
     */
    public double getPlayerSpeed(boolean isSeeker) {
        return isSeeker ? GameConstants.SEEKER_MOVE_SPEED : GameConstants.HIDER_MOVE_SPEED;
    }

    /**
     * 두 점 사이의 거리 계산
     */
    public double calculateDistance(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 레이캐스팅 결과
     */
    public static class RaycastResult {
        public final double hitX, hitY;
        public final String hitPlayerId;
        public final String hitObjectType;
        public final boolean hitPlayer;

        public RaycastResult(double hitX, double hitY, String hitPlayerId,
                String hitObjectType, boolean hitPlayer) {
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitPlayerId = hitPlayerId;
            this.hitObjectType = hitObjectType;
            this.hitPlayer = hitPlayer;
        }
    }

    /**
     * 게임 종료 결과
     */
    public static class GameEndResult {
        public final boolean isGameOver;
        public final String message;

        public GameEndResult(boolean isGameOver, String message) {
            this.isGameOver = isGameOver;
            this.message = message;
        }
    }
}