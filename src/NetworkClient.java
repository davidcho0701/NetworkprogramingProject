import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * 네트워크 통신을 담당하는 클래스
 * 서버와의 연결 및 메시지 송수신 처리
 */
public class NetworkClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread receiverThread;
    private Consumer<String> messageHandler;

    /**
     * 서버에 연결
     */
    public boolean connect(String host, int port, String playerName) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // JOIN 메시지 전송
            sendMessage("JOIN:" + playerName);

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 메시지 수신 스레드 시작
     */
    public void startReceiving(Consumer<String> handler) {
        this.messageHandler = handler;

        receiverThread = new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    final String msg = message;
                    if (messageHandler != null) {
                        // UI 스레드에서 처리되도록 메시지 전달
                        messageHandler.accept(msg);
                    }
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    System.err.println("서버 연결 종료: " + e.getMessage());
                }
            }
        });
        receiverThread.start();
    }

    /**
     * 서버로 메시지 전송
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * 맵 선택 메시지 전송
     */
    public void selectMap(String mapName) {
        sendMessage("SELECT_MAP:" + mapName);
    }

    /**
     * 채팅 메시지 전송
     */
    public void sendChat(String message) {
        sendMessage("CHAT:" + message);
    }

    /**
     * 플레이어 이동 전송
     */
    public void sendMove(double x, double y, double faceDX, double faceDY) {
        sendMessage(String.format("MOVE:%.2f:%.2f:%.2f:%.2f", x, y, faceDX, faceDY));
    }

    /**
     * 사격 메시지 전송
     */
    public void sendShoot(double x, double y, double dirX, double dirY) {
        sendMessage(String.format("SHOOT:%.2f:%.2f:%.2f:%.2f", x, y, dirX, dirY));
    }

    /**
     * 게임 시작 요청
     */
    public void requestGameStart() {
        sendMessage("START_GAME");
    }

    /**
     * 연결 종료
     */
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (receiverThread != null && receiverThread.isAlive()) {
                receiverThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 연결 상태 확인
     */
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }
}
