import java.io.*;
import java.net.Socket;

/**
 * 개별 클라이언트 연결을 처리하는 클래스
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final String clientId;
    private final GameServerModular server;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = true;

    public ClientHandler(Socket socket, String clientId, GameServerModular server) {
        this.socket = socket;
        this.clientId = clientId;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // 연결 확인 메시지 전송
            sendMessage(NetworkProtocol.MSG_CONNECTED + ":" + clientId);

            String inputLine;
            while ((inputLine = in.readLine()) != null && connected) {
                handleMessage(inputLine.trim());
            }
        } catch (IOException e) {
            System.err.println("클라이언트 " + clientId + " 오류: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * 클라이언트로부터 받은 메시지 처리
     */
    private void handleMessage(String message) {
        if (message.isEmpty())
            return;

        String[] parts = NetworkProtocol.parseMessage(message);
        if (parts.length == 0)
            return;

        String command = parts[0];

        switch (command) {
            case NetworkProtocol.CMD_JOIN -> handleJoin(parts);
            case NetworkProtocol.CMD_START -> handleStart();
            case NetworkProtocol.CMD_MOVE -> handleMove(parts);
            case NetworkProtocol.CMD_SHOOT -> handleShoot(parts);
            case NetworkProtocol.CMD_CHAT -> handleChat(parts);
            case NetworkProtocol.CMD_CHANGE_DISGUISE -> handleDisguiseChange();
            case NetworkProtocol.CMD_DISCONNECT -> handleDisconnect();
            default -> System.err.println("알 수 없는 명령: " + command);
        }
    }

    /**
     * 플레이어 입장 처리
     */
    private void handleJoin(String[] parts) {
        if (parts.length < 2)
            return;

        String playerName = parts[1];
        server.addPlayer(clientId, playerName);
        System.out.println("플레이어 입장: " + playerName + " (" + clientId + ")");
    }

    /**
     * 게임 시작 처리
     */
    private void handleStart() {
        server.startGame(clientId);
    }

    /**
     * 플레이어 이동 처리
     */
    private void handleMove(String[] parts) {
        if (parts.length < 3)
            return;

        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            server.handlePlayerMove(clientId, x, y);
        } catch (NumberFormatException e) {
            System.err.println("잘못된 이동 데이터: " + String.join(":", parts));
        }
    }

    /**
     * 사격 처리
     */
    private void handleShoot(String[] parts) {
        if (parts.length < 5)
            return;

        try {
            double startX = Double.parseDouble(parts[1]);
            double startY = Double.parseDouble(parts[2]);
            double dirX = Double.parseDouble(parts[3]);
            double dirY = Double.parseDouble(parts[4]);
            server.handleShoot(clientId, startX, startY, dirX, dirY);
        } catch (NumberFormatException e) {
            System.err.println("잘못된 사격 데이터: " + String.join(":", parts));
        }
    }

    /**
     * 채팅 처리
     */
    private void handleChat(String[] parts) {
        if (parts.length < 2)
            return;

        String message = parts[1];
        server.broadcastChat(clientId, message);
    }

    /**
     * 변장 변경 처리
     */
    private void handleDisguiseChange() {
        server.handleDisguiseChange(clientId);
    }

    /**
     * 연결 해제 처리
     */
    private void handleDisconnect() {
        connected = false;
    }

    /**
     * 클라이언트에게 메시지 전송
     */
    public void sendMessage(String message) {
        if (out != null && connected) {
            out.println(message);
        }
    }

    /**
     * 연결 상태 확인
     */
    public boolean isConnected() {
        return connected && !socket.isClosed();
    }

    /**
     * 리소스 정리
     */
    private void cleanup() {
        connected = false;
        server.removePlayer(clientId);

        try {
            if (in != null)
                in.close();
            if (out != null)
                out.close();
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            System.err.println("리소스 정리 오류: " + e.getMessage());
        }

        System.out.println("클라이언트 연결 해제: " + clientId);
    }

    /**
     * 클라이언트 ID 반환
     */
    public String getClientId() {
        return clientId;
    }
}