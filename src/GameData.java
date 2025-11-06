/**
 * 게임에서 사용되는 데이터 클래스들
 */
public class GameData {

    /**
     * 플레이어 데이터
     */
    public static class PlayerData {
        public String id;
        public String name;
        public int hp = GameConstants.PLAYER_MAX_HP;
        public boolean isSeeker = false;
        public boolean alive = true;
        public String disguise = null;
        public double x = 100;
        public double y = 100;
        public boolean canMove = true; // 움직임 가능 여부 (술래 freeze용)
        public long lastDisguiseChangeTime = 0; // 마지막 변장 변경 시간

        public PlayerData() {
        }

        public PlayerData(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("Player[%s:%s, HP:%d, Seeker:%b, Alive:%b, Pos:(%.1f,%.1f)]",
                    id, name, hp, isSeeker, alive, x, y);
        }
    }

    /**
     * 오브젝트 정보
     */
    public static class ObjectInfo {
        public String type;
        public double x;
        public double y;
        public boolean isPlayer = false;

        public ObjectInfo() {
        }

        public ObjectInfo(String type, double x, double y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }

        public ObjectInfo(String type, double x, double y, boolean isPlayer) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.isPlayer = isPlayer;
        }

        @Override
        public String toString() {
            return String.format("Object[%s at (%.1f,%.1f), Player:%b]", type, x, y, isPlayer);
        }
    }

    /**
     * 총알 궤적 효과
     */
    public static class BulletTrail {
        public double sx, sy; // 시작점
        public double ex, ey; // 끝점
        public int life = 15; // 생명주기 (프레임)

        public BulletTrail(double sx, double sy, double ex, double ey) {
            this.sx = sx;
            this.sy = sy;
            this.ex = ex;
            this.ey = ey;
        }

        public boolean update() {
            life--;
            return life > 0;
        }
    }

    /**
     * 히트 효과
     */
    public static class HitEffect {
        public double x, y;
        public int life = 20;

        public HitEffect(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public boolean update() {
            life--;
            return life > 0;
        }
    }
}