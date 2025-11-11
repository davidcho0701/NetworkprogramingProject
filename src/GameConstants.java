/**
 * 게임 전체에서 사용되는 상수들을 정의
 */
public class GameConstants {
    // 네트워크 설정
    public static final int SERVER_PORT = 12345;
    public static final int MAX_PLAYERS = 10;

    // 게임 월드 설정
    public static final int WORLD_WIDTH = 2000;
    public static final int WORLD_HEIGHT = 1200;
    public static final double PLAYER_RADIUS = 24.0;
    public static final double OBJECT_RADIUS = 22.0;

    // 게임 타이밍
    public static final int HIDE_TIME_MS = 20000;
    public static final int SEEKER_FREEZE_TIME_MS = 20000; // 술래 움직임 불가 시간
    public static final int MOVE_TIMER_DELAY = 16; // 60FPS
    public static final int NETWORK_TIMER_DELAY = 50;
    public static final int DISGUISE_CHANGE_INTERVAL_MS = 10000; // 10초마다 변장 가능

    // 레이캐스팅 설정
    public static final double RAY_STEP = 8.0;
    public static final double RAY_MAX_DISTANCE = 1200.0;

    // 플레이어 설정
    public static final double SEEKER_MOVE_SPEED = 10.0; // 술래는 더 빠름
    public static final double HIDER_MOVE_SPEED = 6.0; // 도둑은 더 느림
    public static final int PLAYER_MAX_HP = 100;

    // 맵 경계 (벽)
    public static final double WALL_THICKNESS = 50.0;
    public static final double MIN_X = WALL_THICKNESS;
    public static final double MAX_X = WORLD_WIDTH - WALL_THICKNESS;
    public static final double MIN_Y = WALL_THICKNESS;
    public static final double MAX_Y = WORLD_HEIGHT - WALL_THICKNESS;

    // UI 설정
    public static final int MINIMAP_WIDTH = 220;
    public static final int MINIMAP_HEIGHT = 160;
    public static final int UI_PADDING = 10;

    // 게임 상태
    public enum GameState {
        WAITING, HIDING, PLAYING, ENDED
    }

    // 테마
    public enum Theme {
        SCHOOL, CONSTRUCTION, CITY
    }

    // 테마별 오브젝트 타입들
    public static final String[] CITY_OBJECTS = {
            "CON", "TIRE", "BLUE_CAR_H", "BLUE_CAR_V", "RED_CAR_H", "RED_CAR_V",
            "LIGHT", "BLUEMAN", "OLDMAN", "WALKMAN", "WALKWOMAN"
    };

    public static final String[] CONSTRUCTION_OBJECTS = {
            "BOX", "CIRCLEBOX", "CON", "BRICK", "FENCE", "TIRE"
    };

    public static final String[] SCHOOL_OBJECTS = {
            "CHAIR", "TABLE", "BROWNCLEANER", "FIRESTOP", "SET", "TRASH", "WHITECLEANER"
    };

    // 기본 오브젝트 타입들 (호환성을 위해 유지)
    public static final String[] OBJECT_TYPES = SCHOOL_OBJECTS;

    // 기본 변장 가능한 오브젝트들 (호환성을 위해 유지)
    public static final String[] DISGUISE_TYPES = SCHOOL_OBJECTS;

    /**
     * 테마에 따른 오브젝트 타입 배열 반환
     */
    public static String[] getObjectsByTheme(Theme theme) {
        return switch (theme) {
            case CITY -> CITY_OBJECTS;
            case CONSTRUCTION -> CONSTRUCTION_OBJECTS;
            case SCHOOL -> SCHOOL_OBJECTS;
        };
    }
}