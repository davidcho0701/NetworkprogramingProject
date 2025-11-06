import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.PrintWriter;
import java.util.Map;

/**
 * 입력 처리를 담당하는 클래스
 */
public class InputHandler {
    private boolean keyUp = false;
    private boolean keyDown = false;
    private boolean keyLeft = false;
    private boolean keyRight = false;

    private double faceDX = 1, faceDY = 0; // 바라보는 방향
    private Timer moveTimer;

    // 게임 상태 참조
    private Map<String, GameData.PlayerData> players;
    private String myClientId;
    private boolean isAlive;
    private boolean isSeeker;
    private GameConstants.GameState currentState;
    private PrintWriter networkOut;

    // 콜백 인터페이스
    public interface MoveCallback {
        void onPlayerMove(double newX, double newY);

        void updateCamera();

        void repaintGame();
    }

    private MoveCallback moveCallback;

    public InputHandler(Map<String, GameData.PlayerData> players, PrintWriter networkOut) {
        this.players = players;
        this.networkOut = networkOut;
    }

    /**
     * 키보드 입력 설정
     */
    public void setupInput(JPanel gamePanel) {
        gamePanel.setFocusable(true);
        gamePanel.requestFocusInWindow();

        InputMap inputMap = gamePanel.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = gamePanel.getActionMap();

        // 키 바인딩 설정
        setupKeyBindings(inputMap, actionMap);

        // 이동 타이머 시작
        startMoveTimer();
    }

    private void setupKeyBindings(InputMap inputMap, ActionMap actionMap) {
        // 키 입력 매핑
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false), "W_PRESS");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true), "W_RELEASE");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "S_PRESS");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "S_RELEASE");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false), "A_PRESS");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "A_RELEASE");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "D_PRESS");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "D_RELEASE");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "SHOOT");

        // 액션 매핑
        actionMap.put("W_PRESS", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyUp = true;
            }
        });
        actionMap.put("W_RELEASE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyUp = false;
            }
        });
        actionMap.put("S_PRESS", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyDown = true;
            }
        });
        actionMap.put("S_RELEASE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyDown = false;
            }
        });
        actionMap.put("A_PRESS", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeft = true;
            }
        });
        actionMap.put("A_RELEASE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyLeft = false;
            }
        });
        actionMap.put("D_PRESS", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRight = true;
            }
        });
        actionMap.put("D_RELEASE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                keyRight = false;
            }
        });
        actionMap.put("SHOOT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleShoot();
            }
        });
    }

    /**
     * 이동 타이머 시작
     */
    private void startMoveTimer() {
        moveTimer = new Timer(GameConstants.MOVE_TIMER_DELAY, e -> {
            if (!isAlive || myClientId == null)
                return;
            if (!(currentState == GameConstants.GameState.HIDING ||
                    currentState == GameConstants.GameState.PLAYING))
                return;

            GameData.PlayerData me = players.get(myClientId);
            if (me == null)
                return;

            double dx = (keyRight ? 1 : 0) - (keyLeft ? 1 : 0);
            double dy = (keyDown ? 1 : 0) - (keyUp ? 1 : 0);

            if (dx != 0 || dy != 0) {
                // 방향 정규화
                double length = Math.sqrt(dx * dx + dy * dy);
                dx /= length;
                dy /= length;

                // 새 위치 계산
                double speed = isSeeker ? GameConstants.SEEKER_MOVE_SPEED : GameConstants.HIDER_MOVE_SPEED;
                double newX = clamp(me.x + dx * speed, GameConstants.MIN_X, GameConstants.MAX_X);
                double newY = clamp(me.y + dy * speed, GameConstants.MIN_Y, GameConstants.MAX_Y);

                // 위치 업데이트
                me.x = newX;
                me.y = newY;

                // 바라보는 방향 업데이트
                faceDX = dx;
                faceDY = dy;

                // 서버에 이동 전송
                networkOut.println("MOVE:" + newX + ":" + newY);

                // 콜백 호출
                if (moveCallback != null) {
                    moveCallback.onPlayerMove(newX, newY);
                    moveCallback.updateCamera();
                    moveCallback.repaintGame();
                }
            }
        });
        moveTimer.start();
    }

    /**
     * 사격 처리
     */
    private void handleShoot() {
        if (!isSeeker || !isAlive || currentState != GameConstants.GameState.PLAYING)
            return;
        if (myClientId == null)
            return;

        GameData.PlayerData me = players.get(myClientId);
        if (me == null)
            return;

        // 서버에 사격 명령 전송
        networkOut.println("SHOOT:" + me.x + ":" + me.y + ":" + faceDX + ":" + faceDY);
    }

    /**
     * 값을 범위 내로 제한
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Getter/Setter 메서드들
    public void setMoveCallback(MoveCallback callback) {
        this.moveCallback = callback;
    }

    public void setGameState(String myClientId, boolean isAlive, boolean isSeeker,
            GameConstants.GameState currentState) {
        this.myClientId = myClientId;
        this.isAlive = isAlive;
        this.isSeeker = isSeeker;
        this.currentState = currentState;
    }

    public double getFaceDX() {
        return faceDX;
    }

    public double getFaceDY() {
        return faceDY;
    }

    /**
     * 리소스 정리
     */
    public void cleanup() {
        if (moveTimer != null) {
            moveTimer.stop();
        }
    }
}