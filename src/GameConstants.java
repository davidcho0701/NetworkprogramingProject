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
        SCHOOL, CONSTRUCTION, MILITARY
    }

    // 오브젝트 타입들 (MAN 제외)
    public static final String[] OBJECT_TYPES = {
            "BOX", "CHAIR", "BARREL", "CONE", "TABLE", "TIRE"
    };

    // 변장 가능한 오브젝트들 (MAN 제외)
    public static final String[] DISGUISE_TYPES = {
            "BOX", "CHAIR", "BARREL", "CONE", "TABLE", "TIRE"
    };
}