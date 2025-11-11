/**
 * 게임 상태 관리를 위한 유틸리티 클래스
 */
public class GameStateManager {

    public enum GameState {
        WAITING, HIDING, PLAYING, ENDED
    }

    /**
     * 맵 이름을 표시 이름으로 변환
     */
    public static String getMapDisplayName(String mapName) {
        return switch (mapName) {
            case "CITY" -> "도시";
            case "CONSTRUCTION" -> "공사장";
            case "SCHOOL" -> "학교";
            default -> mapName;
        };
    }

    /**
     * 테마명을 폴더명으로 변환
     */
    public static String themeToFolderName(String theme) {
        return switch (theme.toUpperCase()) {
            case "CITY" -> "City";
            case "CONSTRUCTION" -> "Construction_site";
            case "SCHOOL" -> "School";
            default -> "School";
        };
    }

    /**
     * 플레이어 데이터 클래스
     */
    public static class PlayerData {
        public String id, name;
        public int hp = 100;
        public boolean isSeeker = false, alive = true;
        public double x = 120, y = 120;
        public String disguise;

        public PlayerData() {
        }

        public PlayerData(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /**
     * 오브젝트 정보 클래스
     */
    public static class ObjectInfo {
        public String type;
        public double x, y;

        public ObjectInfo(String type, double x, double y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }
}
