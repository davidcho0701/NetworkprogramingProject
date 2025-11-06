/**
 * 네트워크 프로토콜 관련 유틸리티
 */
public class NetworkProtocol {

    // 프로토콜 명령어
    public static final String CMD_JOIN = "JOIN";
    public static final String CMD_START = "START";
    public static final String CMD_MOVE = "MOVE";
    public static final String CMD_SHOOT = "SHOOT";
    public static final String CMD_CHAT = "CHAT";
    public static final String CMD_DISCONNECT = "DISCONNECT";
    public static final String CMD_CHANGE_DISGUISE = "CHANGE_DISGUISE";

    // 서버 -> 클라이언트 메시지
    public static final String MSG_CONNECTED = "CONNECTED";
    public static final String MSG_GAME_START = "GAME_START";
    public static final String MSG_GAME_STATE = "GAME_STATE";
    public static final String MSG_PLAYER_UPDATE = "PLAYER_UPDATE";
    public static final String MSG_PLAYER_MOVE = "PLAYER_MOVE";
    public static final String MSG_PLAYER_DEATH = "PLAYER_DEATH";
    public static final String MSG_DAMAGE = "DAMAGE";
    public static final String MSG_BULLET = "BULLET";
    public static final String MSG_GAME_END = "GAME_END";
    public static final String MSG_CHAT = "CHAT";
    public static final String MSG_INITIAL_MAP = "INITIAL_MAP";
    public static final String MSG_OBJECTS = "OBJECTS";
    public static final String MSG_COUNTDOWN = "COUNTDOWN";
    public static final String MSG_DISGUISE_CHANGE = "DISGUISE_CHANGE";
    public static final String MSG_SEEKER_FREEZE = "SEEKER_FREEZE";

    /**
     * 메시지를 파싱하여 명령어와 인자로 분리
     */
    public static String[] parseMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new String[0];
        }
        return message.trim().split(":", -1);
    }

    /**
     * 플레이어 데이터를 문자열로 인코딩
     */
    public static String encodePlayerData(GameData.PlayerData player) {
        return String.format("%s:%s:%d:%b:%b:%.2f:%.2f:%s",
                player.id,
                player.name,
                player.hp,
                player.isSeeker,
                player.alive,
                player.x,
                player.y,
                player.disguise == null ? "NONE" : player.disguise);
    }

    /**
     * 문자열에서 플레이어 데이터로 디코딩
     */
    public static GameData.PlayerData decodePlayerData(String[] data) {
        if (data.length < 8)
            return null;

        GameData.PlayerData player = new GameData.PlayerData();
        try {
            player.id = data[0];
            player.name = data[1];
            player.hp = Integer.parseInt(data[2]);
            player.isSeeker = Boolean.parseBoolean(data[3]);
            player.alive = Boolean.parseBoolean(data[4]);
            player.x = Double.parseDouble(data[5]);
            player.y = Double.parseDouble(data[6]);
            player.disguise = data[7].equals("NONE") ? null : data[7];
        } catch (Exception e) {
            System.err.println("플레이어 데이터 디코딩 오류: " + e.getMessage());
            return null;
        }
        return player;
    }

    /**
     * 오브젝트 정보를 문자열로 인코딩
     */
    public static String encodeObjectInfo(GameData.ObjectInfo obj) {
        return String.format("%s:%.2f:%.2f", obj.type, obj.x, obj.y);
    }

    /**
     * 문자열에서 오브젝트 정보로 디코딩
     */
    public static GameData.ObjectInfo decodeObjectInfo(String[] data) {
        if (data.length < 3)
            return null;

        try {
            return new GameData.ObjectInfo(
                    data[0],
                    Double.parseDouble(data[1]),
                    Double.parseDouble(data[2]));
        } catch (Exception e) {
            System.err.println("오브젝트 데이터 디코딩 오류: " + e.getMessage());
            return null;
        }
    }
}