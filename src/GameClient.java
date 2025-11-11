import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prop Hunt 2D - GameClient (Swing)
 * - 이미지 스프라이트/배경 타일
 * - 연속 이동(60FPS) + 빠른 속도
 * - 레이캐스트 사격(스페이스): 바라보는 방향으로 총알, 궤적/히트 이펙트 렌더
 * - 미니맵(우상단)
 * - 카메라=플레이어 중앙
 */
public class GameClient extends JFrame {

    enum GameState {
        WAITING, HIDING, PLAYING, ENDED
    }

    static class PlayerData {
        String id, name;
        int hp = 100;
        boolean isSeeker = false, alive = true;
        double x = 120, y = 120;
        String disguise;
    }

    static class ObjectInfo {
        String type;
        double x, y;

        ObjectInfo(String t, double x, double y) {
            this.type = t;
            this.x = x;
            this.y = y;
        }
    }

    // 네트워크
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String serverHost = "localhost";
    private int serverPort = 12345;

    // GUI
    private GamePanel gamePanel;
    private JTextArea chatArea, playerListArea;
    private JTextField chatInput;
    private JButton startBtn;
    private JLabel statusLabel;
    private JPanel gameEndOverlay; // 게임 종료 오버레이 패널

    // 맵 선택 관련 GUI
    private JButton cityBtn, constructionBtn, schoolBtn;
    private JPanel mapSelectionPanel;
    private JTextArea mapStatusArea;

    // 상태
    private String myClientId, myName;
    private boolean isSeeker = false, isAlive = true;
    private GameState currentState = GameState.WAITING;
    private String currentTheme = "SCHOOL";

    // 맵 선택 관련 상태
    private String mySelectedMap = null;
    private final Map<String, String> playerMapSelections = new ConcurrentHashMap<>();
    private boolean allPlayersSelected = false;
    private int readyCountdown = -1;

    // 월드/카메라
    private final int worldW = 2000, worldH = 1200;
    private double camX = 0, camY = 0;

    // 게임 데이터
    private final Map<String, PlayerData> players = new ConcurrentHashMap<>();
    private final Map<String, ObjectInfo> objects = new ConcurrentHashMap<>();
    private final List<ObjectInfo> initialMapObjects = new ArrayList<>();
    private final Map<String, Image> imageCache = new HashMap<>();

    // 입력/이동
    private boolean kUp, kDown, kLeft, kRight;
    private double faceDX = 0, faceDY = -1; // 바라보는 방향(초기 위쪽)
    // 속도는 플레이어 타입에 따라 동적으로 결정됨
    private int mouseX = -1, mouseY = -1; // 화면 기준 마우스 좌표

    // 새로운 기능들
    private int countdownSeconds = 0;
    private boolean showCountdown = false;
    // private long gameStartTime = 0; // HIDING 구간 고정 처리로 미사용
    // 수동 변장 변경 기능 제거에 따라 미사용 필드 정리

    // 게임 상수들
    // private static final int SEEKER_FREEZE_TIME_MS = 20000; // HIDING 고정으로 미사용
    // private static final int DISGUISE_CHANGE_INTERVAL_MS = 10000; // 수동 변장 삭제로
    // 미사용
    private static final double SEEKER_MOVE_SPEED = 10.0;
    private static final double HIDER_MOVE_SPEED = 6.0;
    private static final double WALL_THICKNESS = 50.0;
    private static final double MIN_X = WALL_THICKNESS;
    private static final double MAX_X = 2000 - WALL_THICKNESS;
    private static final double MIN_Y = WALL_THICKNESS;
    private static final double MAX_Y = 1200 - WALL_THICKNESS;

    // 변장 주기 카운트다운/스냅샷
    private long lastDisguiseChangeMs = 0L;
    private final Map<String, String> lastDisguiseMap = new HashMap<>();
    private javax.swing.Timer uiRefreshTimer;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameClient().setVisible(true));
    }

    public GameClient() {
        setupGUI();
        // ImageManager를 통해 기본 테마 이미지 로드
        ImageManager.getInstance().loadAllImages();
        loadImagesFromManager();
        connect();
        setupInput();
        startMoveLoop();
        setupCursor();
        // UI 주기적 갱신(카운트다운 등)
        uiRefreshTimer = new javax.swing.Timer(500, e -> {
            if (currentState == GameState.PLAYING)
                gamePanel.repaint();
        });
        uiRefreshTimer.start();
    }

    /**
     * 커서 설정 (술래일 때 조준선)
     */
    private void setupCursor() {
        // 게임패널에 마우스 리스너 추가해서 술래일 때 조준선 커서 적용
        gamePanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                updateCursor();
            }
        });
    }

    private void updateCursor() {
        if (isSeeker && isAlive && currentState == GameState.PLAYING) {
            gamePanel.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            gamePanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private void setupGUI() {
        setTitle("Prop Hunt 2D");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLayout(new BorderLayout());

        // 상단 상태바
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setPreferredSize(new Dimension(0, 42));
        top.setBackground(new Color(20, 25, 35));
        statusLabel = new JLabel("� 숨을 장소를 선택하세요...");
        statusLabel.setForeground(new Color(220, 230, 255));
        statusLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 18));
        top.add(statusLabel);
        add(top, BorderLayout.NORTH);

        // 중앙: 맵 선택 패널과 게임판을 전환
        gamePanel = new GamePanel();

        // 맵 선택 패널 생성
        mapSelectionPanel = createMapSelectionPanel();

        // 처음에는 맵 선택 패널 표시
        add(mapSelectionPanel, BorderLayout.CENTER);

        // 우측 사이드
        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(320, 0));
        right.setBackground(new Color(25, 25, 28));

        // 접속 클라이언트 현황
        playerListArea = new JTextArea("👥 숨는 사람들:\n");
        playerListArea.setEditable(false);
        playerListArea.setBackground(new Color(20, 25, 35));
        playerListArea.setForeground(new Color(220, 230, 255));
        playerListArea.setFont(new Font("Consolas", Font.BOLD, 13));
        playerListArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(80, 100, 140), 2),
                        "� 은신 중인 플레이어",
                        0, 0,
                        new Font("Malgun Gothic", Font.BOLD, 13),
                        new Color(200, 220, 255)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JScrollPane playerScroll = new JScrollPane(playerListArea);
        playerScroll.setPreferredSize(new Dimension(320, 150));

        // 맵 선택 현황
        mapStatusArea = new JTextArea("🗺️ 선택된 은신처:\n");
        mapStatusArea.setEditable(false);
        mapStatusArea.setBackground(new Color(15, 20, 30));
        mapStatusArea.setForeground(new Color(150, 200, 255));
        mapStatusArea.setFont(new Font("Consolas", Font.BOLD, 12));
        mapStatusArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(60, 100, 160), 2),
                        "🌙 은신처 현황",
                        0, 0,
                        new Font("Malgun Gothic", Font.BOLD, 13),
                        new Color(180, 200, 240)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        JScrollPane mapScroll = new JScrollPane(mapStatusArea);
        mapScroll.setPreferredSize(new Dimension(320, 130));
        mapScroll.getViewport().setBackground(new Color(15, 20, 30));

        chatArea = new JTextArea("� === 은밀한 대화 === �\n");
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(12, 18, 28));
        chatArea.setForeground(new Color(180, 220, 180));
        chatArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        chatArea.setLineWrap(true);
        chatArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(40, 120, 60), 2),
                        "채팅창",
                        0, 0,
                        new Font("Malgun Gothic", Font.BOLD, 13),
                        new Color(150, 220, 150)),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.getViewport().setBackground(new Color(12, 18, 28));

        // 우측 패널 구성
        JPanel rightTop = new JPanel(new BorderLayout());
        rightTop.add(playerScroll, BorderLayout.NORTH);
        rightTop.add(mapScroll, BorderLayout.CENTER);

        right.add(rightTop, BorderLayout.NORTH);
        right.add(chatScroll, BorderLayout.CENTER);

        // 시작 버튼은 모든 플레이어가 선택 완료 후 표시
        startBtn = new JButton("🔦 숨바꼭질 시작 🔦");
        startBtn.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        startBtn.setBackground(new Color(180, 40, 40));
        startBtn.setForeground(Color.WHITE);
        startBtn.setFocusPainted(false);
        startBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 180, 80), 3),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(60, 20, 20), 1),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12))));
        startBtn.addActionListener(e -> out.println("START_GAME"));
        startBtn.setVisible(false); // 처음에는 숨김
        right.add(startBtn, BorderLayout.SOUTH);

        add(right, BorderLayout.EAST);

        // 하단 채팅 입력
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setPreferredSize(new Dimension(0, 38));
        chatInput = new JTextField();
        chatInput.addActionListener(e -> sendChat());
        bottom.add(chatInput, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // 포커스 유지
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                gamePanel.requestFocusInWindow();
            }
        });
        gamePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                gamePanel.requestFocusInWindow();
            }
        });
    }

    /**
     * 맵 선택 패널 생성
     */
    private JPanel createMapSelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        // 숨바꼭질 분위기: 어두운 밤하늘 느낌
        panel.setBackground(new Color(10, 15, 25));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(40, 60, 90), 3),
                BorderFactory.createEmptyBorder(30, 30, 30, 30)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);

        // 게임 타이틀과 장식 - 숨바꼭질 테마
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel gameTitle = new JLabel("🔦 PROP HUNT 2D 🔦");
        gameTitle.setForeground(new Color(255, 230, 100)); // 손전등 빛 색상
        gameTitle.setFont(new Font("Malgun Gothic", Font.BOLD, 40));
        gameTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("🌙 숨을 장소를 선택하세요 🌙");
        titleLabel.setForeground(new Color(180, 200, 255));
        titleLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 26));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        titlePanel.add(gameTitle);
        titlePanel.add(Box.createVerticalStrut(12));
        titlePanel.add(titleLabel);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        panel.add(titlePanel, gbc);

        // 안내 텍스트를 숨바꼭질 분위기로
        JLabel infoLabel = new JLabel("👻 모두 준비되면 숨바꼭질이 시작됩니다... 👻");
        infoLabel.setForeground(new Color(160, 160, 180));
        infoLabel.setFont(new Font("Malgun Gothic", Font.ITALIC, 15));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        panel.add(infoLabel, gbc);

        // 맵 선택 버튼들
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        // City 버튼 - 미래도시 테마
        cityBtn = createMapButton("🏙️ CITY", "도심의 그림자", new Color(20, 100, 180));
        cityBtn.addActionListener(e -> selectMap("CITY"));
        gbc.gridx = 0;
        panel.add(cityBtn, gbc);

        // Construction 버튼 - 건설현장 테마
        constructionBtn = createMapButton("🏗️ SITE", "공사장의 어둠", new Color(180, 100, 20));
        constructionBtn.addActionListener(e -> selectMap("CONSTRUCTION"));
        gbc.gridx = 1;
        panel.add(constructionBtn, gbc);

        // School 버튼 - 학교 테마
        schoolBtn = createMapButton("🏫 SCHOOL", "학교의 적막", new Color(20, 120, 60));
        schoolBtn.addActionListener(e -> selectMap("SCHOOL"));
        gbc.gridx = 2;
        panel.add(schoolBtn, gbc);

        return panel;
    }

    /**
     * 맵 선택 버튼 생성
     */
    private JButton createMapButton(String title, String subtitle, Color color) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setPreferredSize(new Dimension(280, 260));

        // 어두운 숨바꼭질 분위기
        button.setBackground(color.darker());
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);

        // 미스터리한 테두리 스타일
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 120), 2),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(color.brighter().brighter(), 1),
                        BorderFactory.createEmptyBorder(20, 15, 20, 15))));

        // 버튼 내용
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // 장식용 아이콘/구분선 - 숨바꼭질 테마
        JLabel decorLine1 = new JLabel("· · · · · · · · ·");
        decorLine1.setForeground(new Color(200, 200, 220, 150));
        decorLine1.setFont(new Font("Monospaced", Font.BOLD, 16));
        decorLine1.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setForeground(new Color(255, 255, 255));
        titleLbl.setFont(new Font("Malgun Gothic", Font.BOLD, 20));
        titleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitleLbl = new JLabel(subtitle);
        subtitleLbl.setForeground(new Color(180, 200, 220));
        subtitleLbl.setFont(new Font("Malgun Gothic", Font.ITALIC, 14));
        subtitleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel decorLine2 = new JLabel("· · · · · · · · ·");
        decorLine2.setForeground(new Color(200, 200, 220, 150));
        decorLine2.setFont(new Font("Monospaced", Font.BOLD, 16));
        decorLine2.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 숨바꼭질 관련 상태 텍스트
        JLabel statusLbl = new JLabel("[ 숨을 준비 완료 ]");
        statusLbl.setForeground(new Color(150, 230, 150, 200));
        statusLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        statusLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 분위기 아이콘
        JLabel iconLbl = new JLabel("👁️");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(Box.createVerticalGlue());
        content.add(iconLbl);
        content.add(Box.createVerticalStrut(8));
        content.add(decorLine1);
        content.add(Box.createVerticalStrut(12));
        content.add(titleLbl);
        content.add(Box.createVerticalStrut(6));
        content.add(subtitleLbl);
        content.add(Box.createVerticalStrut(12));
        content.add(statusLbl);
        content.add(Box.createVerticalStrut(6));
        content.add(decorLine2);
        content.add(Box.createVerticalGlue());

        button.add(content, BorderLayout.CENTER);

        // 미스터리한 호버 효과
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(color);
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255, 255, 150), 3),
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(color.brighter().brighter(), 1),
                                BorderFactory.createEmptyBorder(20, 15, 20, 15))));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(color.darker());
                button.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(80, 80, 120), 2),
                        BorderFactory.createCompoundBorder(
                                BorderFactory.createLineBorder(color.brighter().brighter(), 1),
                                BorderFactory.createEmptyBorder(20, 15, 20, 15))));
            }
        });

        return button;
    }

    /**
     * 맵 선택 처리
     */
    private void selectMap(String mapName) {
        if (mySelectedMap != null) {
            return; // 이미 선택함
        }

        mySelectedMap = mapName;

        // 선택된 버튼 표시 업데이트
        updateMapButtonStates();

        // 서버에 선택 전송
        out.println("SELECT_MAP:" + mapName);

        statusLabel.setText("🎮 " + getMapDisplayName(mapName) + " 선택 완료 - 다른 플레이어 대기 중...");
    }

    /**
     * 맵 버튼 상태 업데이트
     */
    private void updateMapButtonStates() {
        cityBtn.setEnabled(mySelectedMap == null);
        constructionBtn.setEnabled(mySelectedMap == null);
        schoolBtn.setEnabled(mySelectedMap == null);

        // 선택된 버튼 강조
        if ("CITY".equals(mySelectedMap)) {
            cityBtn.setBackground(new Color(100, 150, 200));
            cityBtn.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
        } else if ("CONSTRUCTION".equals(mySelectedMap)) {
            constructionBtn.setBackground(new Color(235, 163, 93));
            constructionBtn.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
        } else if ("SCHOOL".equals(mySelectedMap)) {
            schoolBtn.setBackground(new Color(90, 199, 143));
            schoolBtn.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 3));
        }
    }

    /**
     * 맵 표시 이름 반환
     */
    private String getMapDisplayName(String mapName) {
        return switch (mapName) {
            case "CITY" -> "도시";
            case "CONSTRUCTION" -> "공사장";
            case "SCHOOL" -> "학교";
            default -> mapName;
        };
    }

    /**
     * 맵 선택 현황 업데이트
     */
    private void updateMapSelections(String message) {
        // MAP_SELECTIONS:player1:CITY:player2:SCHOOL:...
        String[] parts = message.split(":");
        playerMapSelections.clear();

        for (int i = 1; i < parts.length; i += 2) {
            if (i + 1 < parts.length) {
                String playerName = parts[i];
                String selectedMap = parts[i + 1];
                playerMapSelections.put(playerName, selectedMap);
            }
        }

        updateMapStatusDisplay();
    }

    /**
     * 맵 선택 현황 표시 업데이트
     */
    private void updateMapStatusDisplay() {
        StringBuilder sb = new StringBuilder("🗺️ 맵 선택 현황:\n");

        if (playerMapSelections.isEmpty()) {
            sb.append("아직 선택한 플레이어가 없습니다.\n");
        } else {
            for (Map.Entry<String, String> entry : playerMapSelections.entrySet()) {
                String playerName = entry.getKey();
                String mapName = entry.getValue();
                String displayName = getMapDisplayName(mapName);

                if (myName.equals(playerName)) {
                    sb.append("✅ ").append(playerName).append(" → ").append(displayName).append(" (나)\n");
                } else {
                    sb.append("✅ ").append(playerName).append(" → ").append(displayName).append("\n");
                }
            }
        }

        mapStatusArea.setText(sb.toString());
    }

    /**
     * 5초 카운트다운 시작
     */
    private void startReadyCountdown() {
        javax.swing.Timer countdownTimer = new javax.swing.Timer(1000, null);
        countdownTimer.addActionListener(e -> {
            readyCountdown--;
            if (readyCountdown > 0) {
                statusLabel.setText("🎮 게임 시작까지 " + readyCountdown + "초...");
            } else {
                ((javax.swing.Timer) e.getSource()).stop();
                // 맵 선택 패널에서 게임 패널로 전환
                switchToGamePanel();
            }
        });

        readyCountdown = 5;
        countdownTimer.start();
    }

    /**
     * 게임 패널로 전환
     */
    private void switchToGamePanel() {
        // 중앙 패널을 맵 선택에서 게임 패널로 교체
        remove(mapSelectionPanel);
        add(gamePanel, BorderLayout.CENTER);
        revalidate();
        repaint();

        // 포커스를 게임 패널로 이동
        gamePanel.requestFocusInWindow();
    }

    // ===== 사망/게임 종료 테마 다이얼로그 =====
    private void showRoleDialog(boolean seeker) {
        final JDialog dialog = new JDialog(this, "알림", true);
        dialog.setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(seeker ? new Color(180, 60, 60) : new Color(60, 120, 180), 3),
                BorderFactory.createLineBorder(new Color(15, 20, 30), 2)));
        root.setBackground(new Color(12, 18, 26));

        JPanel banner = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 고급스러운 그라데이션
                if (seeker) {
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(100, 30, 30),
                            getWidth(), getHeight(), new Color(60, 15, 15));
                    g2.setPaint(gp);
                } else {
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(25, 50, 85),
                            getWidth(), getHeight(), new Color(15, 30, 55));
                    g2.setPaint(gp);
                }
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 장식 효과
                g2.setColor(seeker ? new Color(255, 100, 100, 40) : new Color(100, 150, 255, 40));
                g2.fillOval(-50, -50, 200, 200);
                g2.fillOval(getWidth() - 150, getHeight() - 150, 200, 200);

                g2.dispose();
            }
        };
        banner.setPreferredSize(new Dimension(580, 190));
        banner.setOpaque(false);
        banner.setLayout(new GridBagLayout());

        String icon = seeker ? "🔦" : "👻";
        String titleText = seeker ? icon + " 당신은 술래입니다! " + icon : icon + " 당신은 숨는 사람입니다! " + icon;
        String lines = seeker ? "⏰ 20초 후 움직일 수 있습니다\n⌨️ WASD: 이동 | SPACE: 사격" : "⌨️ WASD로 이동하여 숨으세요!";

        JLabel title = new JLabel(titleText);
        title.setForeground(new Color(250, 255, 255));
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 26));

        JTextArea desc = new JTextArea(lines);
        desc.setEditable(false);
        desc.setOpaque(false);
        desc.setForeground(new Color(220, 230, 245));
        desc.setFont(new Font("Malgun Gothic", Font.PLAIN, 15));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(10));
        titleBox.add(desc);
        banner.add(titleBox);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 14));
        bottom.setBackground(new Color(12, 18, 26));

        JButton ok = new JButton("✓ 준비 완료");
        ok.setBackground(seeker ? new Color(140, 50, 50) : new Color(50, 100, 150));
        ok.setForeground(Color.WHITE);
        ok.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        ok.setFocusPainted(false);
        ok.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(seeker ? new Color(180, 80, 80) : new Color(80, 140, 200), 2),
                BorderFactory.createEmptyBorder(10, 30, 10, 30)));

        ok.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                ok.setBackground(seeker ? new Color(160, 60, 60) : new Color(60, 120, 170));
            }

            public void mouseExited(MouseEvent e) {
                ok.setBackground(seeker ? new Color(140, 50, 50) : new Color(50, 100, 150));
            }
        });

        bottom.add(ok);

        root.add(banner, BorderLayout.NORTH);
        root.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        ok.addActionListener(e -> dialog.dispose());
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showDeathDialog() {
        final JDialog dialog = new JDialog(this, "발각됨", true);
        dialog.setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 80, 80), 3),
                BorderFactory.createLineBorder(new Color(15, 18, 24), 2)));
        root.setBackground(new Color(12, 15, 20));

        JPanel banner = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gp = new GradientPaint(
                        0, 0, new Color(90, 25, 25),
                        getWidth(), getHeight(), new Color(50, 15, 15));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 붉은 경고 효과
                g2.setColor(new Color(255, 80, 80, 60));
                g2.fillOval(-30, -30, 150, 150);
                g2.fillOval(getWidth() - 120, getHeight() - 120, 150, 150);

                g2.dispose();
            }
        };
        banner.setPreferredSize(new Dimension(540, 180));
        banner.setOpaque(false);
        banner.setLayout(new GridBagLayout());

        JLabel title = new JLabel("💀 발각되었습니다! 💀");
        title.setForeground(new Color(255, 230, 230));
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 28));

        JLabel subtitle = new JLabel("👁️ 관전 모드로 전환됩니다");
        subtitle.setForeground(new Color(240, 210, 210));
        subtitle.setFont(new Font("Malgun Gothic", Font.PLAIN, 15));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(10));
        titleBox.add(subtitle);
        banner.add(titleBox);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 14));
        bottom.setBackground(new Color(12, 15, 20));

        JButton ok = new JButton("✓ 확인");
        ok.setBackground(new Color(160, 60, 60));
        ok.setForeground(Color.WHITE);
        ok.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        ok.setFocusPainted(false);
        ok.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 100, 100), 2),
                BorderFactory.createEmptyBorder(10, 30, 10, 30)));

        ok.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                ok.setBackground(new Color(180, 70, 70));
            }

            public void mouseExited(MouseEvent e) {
                ok.setBackground(new Color(160, 60, 60));
            }
        });

        bottom.add(ok);

        root.add(banner, BorderLayout.NORTH);
        root.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        ok.addActionListener(e -> dialog.dispose());
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showGameEndDialog(boolean seekerWin, String seekerName) {
        // 기존 오버레이가 있으면 제거
        if (gameEndOverlay != null) {
            gamePanel.remove(gameEndOverlay);
            gameEndOverlay = null;
        }

        // 게임 패널을 JLayeredPane처럼 사용하기 위해 null layout 설정
        gamePanel.setLayout(null);

        // 오버레이 패널 생성 (반투명 배경)
        gameEndOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 반투명 어두운 배경
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.dispose();
            }
        };
        gameEndOverlay.setOpaque(false);
        gameEndOverlay.setBounds(0, 0, gamePanel.getWidth(), gamePanel.getHeight());
        gameEndOverlay.setLayout(new GridBagLayout());

        // 승리 패널 생성
        JPanel victoryPanel = new JPanel(new BorderLayout());
        victoryPanel.setPreferredSize(new Dimension(600, 350));
        victoryPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(seekerWin ? new Color(180, 80, 80) : new Color(80, 140, 200), 4),
                BorderFactory.createLineBorder(new Color(15, 18, 24), 3)));
        victoryPanel.setBackground(new Color(12, 15, 20));

        // 배너 패널
        JPanel banner = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (seekerWin) {
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(90, 30, 30),
                            getWidth(), getHeight(), new Color(60, 20, 20));
                    g2.setPaint(gp);
                } else {
                    GradientPaint gp = new GradientPaint(
                            0, 0, new Color(30, 60, 100),
                            getWidth(), getHeight(), new Color(20, 40, 70));
                    g2.setPaint(gp);
                }
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 승리 효과
                g2.setColor(seekerWin ? new Color(255, 100, 100, 50) : new Color(100, 180, 255, 50));
                for (int i = 0; i < 3; i++) {
                    g2.fillOval(50 + i * 150, 20 + i * 30, 120, 120);
                }

                g2.dispose();
            }
        };
        banner.setPreferredSize(new Dimension(600, 220));
        banner.setOpaque(false);
        banner.setLayout(new GridBagLayout());

        String titleText = seekerWin ? "🏆 술래 승! 🏆" : "🏆 도망자 승! 🏆";
        String subText = seekerWin ? (seekerName != null ? ("🔦 술래 " + seekerName + "님의 완벽한 수색!") : "🔦 술래의 승리!")
                : "👻 도망자들의 완벽한 은신!";

        JLabel title = new JLabel(titleText);
        title.setForeground(new Color(250, 255, 255));
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 40));

        JLabel subtitle = new JLabel(subText);
        subtitle.setForeground(new Color(220, 230, 245));
        subtitle.setFont(new Font("Malgun Gothic", Font.PLAIN, 18));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(15));
        titleBox.add(subtitle);
        banner.add(titleBox);

        // 하단 버튼 패널
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 20));
        bottom.setBackground(new Color(12, 15, 20));

        JButton restartBtn = new JButton("🔄 다시하기");
        restartBtn.setBackground(seekerWin ? new Color(140, 60, 60) : new Color(60, 120, 160));
        restartBtn.setForeground(Color.WHITE);
        restartBtn.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        restartBtn.setFocusPainted(false);
        restartBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(seekerWin ? new Color(180, 100, 100) : new Color(100, 160, 220), 2),
                BorderFactory.createEmptyBorder(12, 40, 12, 40)));

        restartBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                restartBtn.setBackground(seekerWin ? new Color(160, 70, 70) : new Color(70, 140, 180));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                restartBtn.setBackground(seekerWin ? new Color(140, 60, 60) : new Color(60, 120, 160));
            }
        });

        restartBtn.addActionListener(e -> {
            // 오버레이 제거
            gamePanel.remove(gameEndOverlay);
            gameEndOverlay = null;
            gamePanel.setLayout(null); // layout 유지
            gamePanel.revalidate();
            gamePanel.repaint();

            // 게임 종료 후 다시 맵 선택 화면으로
            resetToMapSelection();
        });

        bottom.add(restartBtn);

        victoryPanel.add(banner, BorderLayout.CENTER);
        victoryPanel.add(bottom, BorderLayout.SOUTH);

        // 오버레이에 승리 패널 추가
        gameEndOverlay.add(victoryPanel);

        // 게임 패널에 오버레이 추가
        gamePanel.add(gameEndOverlay);
        gamePanel.setComponentZOrder(gameEndOverlay, 0); // 최상위로

        gamePanel.revalidate();
        gamePanel.repaint();
    }

    /**
     * 게임 종료 후 맵 선택 화면으로 리셋
     */
    private void resetToMapSelection() {
        // 게임 상태 초기화
        currentState = GameState.WAITING;
        mySelectedMap = null;
        playerMapSelections.clear();
        allPlayersSelected = false;
        readyCountdown = -1;
        isSeeker = false;
        isAlive = true;

        // 게임 데이터 초기화
        players.clear();
        objects.clear();
        initialMapObjects.clear();

        // UI 초기화
        statusLabel.setText("🌙 숨을 장소를 선택하세요...");
        mapStatusArea.setText("🗺️ 선택된 은신처:\n");

        // 맵 선택 버튼 활성화 및 초기화
        if (cityBtn != null) {
            cityBtn.setEnabled(true);
            cityBtn.setBackground(new Color(20, 100, 180).darker());
            cityBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(80, 80, 120), 2),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(20, 100, 180).brighter().brighter(), 1),
                            BorderFactory.createEmptyBorder(20, 15, 20, 15))));
        }
        if (constructionBtn != null) {
            constructionBtn.setEnabled(true);
            constructionBtn.setBackground(new Color(180, 100, 20).darker());
            constructionBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(80, 80, 120), 2),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(180, 100, 20).brighter().brighter(), 1),
                            BorderFactory.createEmptyBorder(20, 15, 20, 15))));
        }
        if (schoolBtn != null) {
            schoolBtn.setEnabled(true);
            schoolBtn.setBackground(new Color(20, 120, 60).darker());
            schoolBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(80, 80, 120), 2),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(20, 120, 60).brighter().brighter(), 1),
                            BorderFactory.createEmptyBorder(20, 15, 20, 15))));
        }

        // 게임 패널에서 맵 선택 패널로 전환
        remove(gamePanel);
        add(mapSelectionPanel, BorderLayout.CENTER);
        revalidate();
        repaint();

        // 서버에 준비 상태 전송 (필요시)
        // out.println("READY_FOR_NEXT_GAME");
    }

    // ===== 네트워크 =====
    private void connect() {
        myName = showNameDialog();
        if (myName == null || myName.trim().isEmpty()) {
            System.exit(0);
        }

        try {
            socket = new Socket(serverHost, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println("JOIN:" + myName);

            new Thread(() -> {
                try {
                    String m;
                    while ((m = in.readLine()) != null) {
                        String mm = m;
                        SwingUtilities.invokeLater(() -> process(mm));
                    }
                } catch (IOException ex) {
                    if (!socket.isClosed())
                        SwingUtilities.invokeLater(
                                () -> JOptionPane.showMessageDialog(this, "서버 연결 종료", "오류", JOptionPane.ERROR_MESSAGE));
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버 연결 실패", "오류", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    // ===== 커스텀 닉네임 입력 다이얼로그 =====
    private String showNameDialog() {
        final JDialog dialog = new JDialog(this, "게임 시작", true);
        dialog.setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 120, 150), 3),
                BorderFactory.createLineBorder(new Color(20, 25, 35), 2)));
        root.setBackground(new Color(15, 20, 30));

        // 상단 배너 - 고급스러운 그라데이션
        JPanel banner = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 고급스러운 다중 그라데이션
                GradientPaint gp1 = new GradientPaint(
                        0, 0, new Color(25, 35, 50),
                        0, getHeight() / 2, new Color(15, 25, 40));
                g2.setPaint(gp1);
                g2.fillRect(0, 0, getWidth(), getHeight() / 2);

                GradientPaint gp2 = new GradientPaint(
                        0, getHeight() / 2, new Color(15, 25, 40),
                        0, getHeight(), new Color(10, 18, 30));
                g2.setPaint(gp2);
                g2.fillRect(0, getHeight() / 2, getWidth(), getHeight() / 2);

                // 장식 라인
                g2.setColor(new Color(100, 150, 200, 80));
                g2.setStroke(new BasicStroke(2));
                g2.drawLine(40, getHeight() - 15, getWidth() - 40, getHeight() - 15);

                // 배경 아이콘 (투명하게)
                Image seeker = imageCache.get("SEEKER");
                if (seeker != null) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
                    g2.drawImage(seeker, getWidth() - 150, 10, 130, 170, null);
                    g2.setComposite(AlphaComposite.SrcOver);
                }
                g2.dispose();
            }
        };
        banner.setPreferredSize(new Dimension(560, 200));
        banner.setOpaque(false);
        banner.setLayout(new GridBagLayout());

        // 타이틀 - 더 세련된 스타일
        JLabel title = new JLabel("🔦 PROP HUNT 2D");
        title.setForeground(new Color(240, 250, 255));
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 32));

        JLabel subtitle = new JLabel("당신의 정체를 숨기세요...");
        subtitle.setForeground(new Color(160, 180, 210));
        subtitle.setFont(new Font("Malgun Gothic", Font.ITALIC, 15));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(8));
        titleBox.add(subtitle);
        banner.add(titleBox);

        // 중앙 입력부 - 고급스러운 디자인
        JPanel center = new JPanel();
        center.setBackground(new Color(15, 20, 30));
        center.setBorder(BorderFactory.createEmptyBorder(20, 30, 12, 30));
        center.setLayout(new BorderLayout(10, 14));

        // 닉네임 입력 필드 - 더 세련된 스타일
        JTextField nameField = new JTextField();
        nameField.setFont(new Font("Malgun Gothic", Font.BOLD, 17));
        nameField.setForeground(new Color(240, 245, 255));
        nameField.setBackground(new Color(25, 32, 45));
        nameField.setCaretColor(new Color(150, 200, 255));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(80, 110, 150), 2),
                        BorderFactory.createLineBorder(new Color(40, 50, 65), 1)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        nameField.setColumns(18);

        String[] samples = { "🦊 ShadowFox", "💺 SilentChair", "🛢️ HiddenBarrel", "🚧 SneakyCone", "📦 GhostBox" };
        JLabel hint = new JLabel("💡 예시: " + samples[new Random().nextInt(samples.length)]);
        hint.setForeground(new Color(130, 150, 180));
        hint.setFont(new Font("Malgun Gothic", Font.ITALIC, 13));

        // 서버 주소 입력 (host[:port]) - 고급스러운 스타일
        JTextField serverField = new JTextField(serverHost + ":" + serverPort);
        serverField.setFont(new Font("Consolas", Font.PLAIN, 14));
        serverField.setForeground(new Color(220, 230, 240));
        serverField.setBackground(new Color(25, 32, 45));
        serverField.setCaretColor(new Color(150, 200, 255));
        serverField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(70, 90, 120), 2),
                        BorderFactory.createLineBorder(new Color(35, 45, 60), 1)),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JPanel fields = new JPanel();
        fields.setOpaque(false);
        fields.setLayout(new GridLayout(2, 1, 0, 12));

        JPanel nameRow = new JPanel(new BorderLayout(10, 0));
        nameRow.setOpaque(false);
        JLabel nameLabel = new JLabel("🎭 닉네임");
        nameLabel.setForeground(new Color(180, 200, 230));
        nameLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        nameRow.add(nameLabel, BorderLayout.WEST);
        nameRow.add(nameField, BorderLayout.CENTER);

        JPanel hostRow = new JPanel(new BorderLayout(10, 0));
        hostRow.setOpaque(false);
        JLabel serverLabel = new JLabel("🌐 서버");
        serverLabel.setForeground(new Color(180, 200, 230));
        serverLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        hostRow.add(serverLabel, BorderLayout.WEST);
        hostRow.add(serverField, BorderLayout.CENTER);

        fields.add(nameRow);
        fields.add(hostRow);

        JPanel centerTop = new JPanel(new BorderLayout(0, 8));
        centerTop.setOpaque(false);
        centerTop.add(hint, BorderLayout.NORTH);
        centerTop.add(fields, BorderLayout.CENTER);

        center.add(centerTop, BorderLayout.CENTER);

        // 하단 버튼/가이드 - 고급스러운 디자인
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(new Color(15, 20, 30));
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(50, 70, 100)),
                BorderFactory.createEmptyBorder(16, 30, 20, 30)));

        JLabel controls = new JLabel("⌨️ WASD: 이동 | SPACE: 사격 (술래 전용)");
        controls.setForeground(new Color(130, 150, 180));
        controls.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btns.setOpaque(false);

        JButton cancel = new JButton("✖ 종료");
        JButton ok = new JButton("▶ 게임 시작");

        cancel.setBackground(new Color(60, 70, 85));
        cancel.setForeground(new Color(220, 230, 240));
        cancel.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        cancel.setFocusPainted(false);
        cancel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 90, 110), 1),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));

        ok.setBackground(new Color(40, 140, 80));
        ok.setForeground(Color.WHITE);
        ok.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        ok.setFocusPainted(false);
        ok.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 180, 100), 1),
                BorderFactory.createEmptyBorder(10, 24, 10, 24)));
        ok.setEnabled(false);

        // 호버 효과
        ok.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (ok.isEnabled()) {
                    ok.setBackground(new Color(50, 160, 90));
                }
            }

            public void mouseExited(MouseEvent e) {
                ok.setBackground(new Color(40, 140, 80));
            }
        });

        cancel.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                cancel.setBackground(new Color(70, 80, 95));
            }

            public void mouseExited(MouseEvent e) {
                cancel.setBackground(new Color(60, 70, 85));
            }
        });

        btns.add(cancel);
        btns.add(ok);

        bottom.add(controls, BorderLayout.WEST);
        bottom.add(btns, BorderLayout.EAST);

        root.add(banner, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        // 이벤트
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void upd() {
                ok.setEnabled(nameField.getText().trim().length() >= 1);
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                upd();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                upd();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                upd();
            }
        });
        ok.addActionListener(e -> dialog.dispose());
        cancel.addActionListener(e -> {
            nameField.setText("");
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(ok);

        // 위치/표시
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        String name = nameField.getText().trim();
        // 서버 주소 파싱
        String addr = serverField.getText().trim();
        if (!addr.isEmpty()) {
            try {
                String host = addr;
                int port = 12345;
                int idx = addr.lastIndexOf(":");
                if (idx > 0 && idx < addr.length() - 1) {
                    host = addr.substring(0, idx);
                    port = Integer.parseInt(addr.substring(idx + 1));
                }
                serverHost = host.isEmpty() ? "localhost" : host;
                serverPort = port;
            } catch (Exception ignored) {
                serverHost = "localhost";
                serverPort = 12345;
            }
        }
        return name.isEmpty() ? null : name;
    }

    // ===== 메시지 처리 =====
    private void process(String message) {
        String[] p = message.split(":", 2);
        String cmd = p[0];
        switch (cmd) {
            case "JOINED" -> {
                myClientId = p[1];
                setTitle("Prop Hunt 2D - " + myName + " (ID: " + myClientId + ")");
                PlayerData me = players.getOrDefault(myClientId, new PlayerData());
                me.id = myClientId;
                me.name = myName;
                players.put(myClientId, me);
            }
            case "PLAYER_LIST" -> {
                playerListArea.setText("👥 접속 클라이언트:\n");
                if (p.length > 1 && !p[1].isEmpty()) {
                    for (String n : p[1].split(",")) {
                        if (!n.isEmpty())
                            playerListArea.append(" • " + n + "\n");
                    }
                }
            }
            case "MAP_SELECTIONS" -> {
                // MAP_SELECTIONS:player1:CITY:player2:SCHOOL:...
                updateMapSelections(message);
            }
            case "ALL_SELECTED" -> {
                // 모든 플레이어가 선택 완료
                allPlayersSelected = true;
                statusLabel.setText("🎮 모든 플레이어 선택 완료! 5초 후 게임 시작...");
                startReadyCountdown();
            }
            case "READY_COUNTDOWN" -> {
                // READY_COUNTDOWN:3
                readyCountdown = Integer.parseInt(p[1]);
                statusLabel.setText("🎮 게임 시작까지 " + readyCountdown + "초...");
            }
            case "SYSTEM", "CHAT" -> {
                chatArea.append(p[1] + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            }
            case "GAME_START" -> {
                String[] a = message.split(":");
                String seeker = a[2];
                if (a.length > 3) {
                    String newTheme = a[3];
                    if (!currentTheme.equals(newTheme)) {
                        currentTheme = newTheme;
                        loadThemeImages(currentTheme); // 테마 변경 시 이미지 다시 로드
                    }
                }
                isSeeker = myClientId != null && myClientId.equals(seeker);
                isAlive = true;
                currentState = GameState.HIDING;

                PlayerData me = players.computeIfAbsent(myClientId, id -> new PlayerData());
                me.id = myClientId;
                me.name = myName;
                me.alive = true;
                me.isSeeker = isSeeker;

                // 게임 시작 시간 기록 제거 (술래는 HIDING 동안 이동 불가 정책)

                // 모든 플레이어에게 카운트다운 표시
                countdownSeconds = 20;
                showCountdown = true;
                startCountdownTimer();

                if (isSeeker) {
                    statusLabel.setText("🔴 술래 - 20초 후 시작!");
                    showRoleDialog(true);
                } else {
                    statusLabel.setText("🟦 숨는 사람 - 20초 안에 숨기!");
                    showRoleDialog(false);
                }
                startBtn.setEnabled(false);
            }
            case "INITIAL_MAP" -> {
                initialMapObjects.clear();
                if (p.length > 1) {
                    String[] arr = p[1].split(";");
                    for (String s : arr) {
                        if (s.isEmpty())
                            continue;
                        String[] info = s.split(",");
                        initialMapObjects.add(new ObjectInfo(info[1],
                                Double.parseDouble(info[2]), Double.parseDouble(info[3])));
                    }
                }
                System.out.println("[CLIENT] INITIAL_MAP loaded: " + initialMapObjects.size());
            }
            case "ROLE" -> {
                String[] r = p[1].split(":");
                if (r[0].equals("SEEKER")) {
                    isSeeker = true;
                    PlayerData me = players.computeIfAbsent(myClientId, id -> new PlayerData());
                    me.isSeeker = true;
                    me.alive = true;
                    if (r.length >= 3) {
                        me.x = Double.parseDouble(r[1]);
                        me.y = Double.parseDouble(r[2]);
                    }
                } else {
                    isSeeker = false;
                    PlayerData me = players.computeIfAbsent(myClientId, id -> new PlayerData());
                    me.isSeeker = false;
                    me.alive = true;
                    if (r.length >= 4) {
                        me.disguise = r[1];
                        me.x = Double.parseDouble(r[2]);
                        me.y = Double.parseDouble(r[3]);
                    }
                }
                updateCameraToMe();
            }
            case "GAME_PLAYING" -> {
                currentState = GameState.PLAYING;
                statusLabel.setText(isSeeker ? "🔫 술래 - 숨은 사람을 찾으세요!" : "🤫 조용히 숨어 있기!");
                // 변장 사이클 카운트다운 시작
                lastDisguiseChangeMs = System.currentTimeMillis();
                lastDisguiseMap.clear();
                for (PlayerData pd : players.values())
                    if (!pd.isSeeker)
                        lastDisguiseMap.put(pd.id, pd.disguise);
            }
            case "GAME_STATE" -> parseGameState(message);
            case "PLAYER_MOVE" -> updatePlayerPos(p[1]);
            case "PLAYER_HIT" -> {
                String[] h = p[1].split(":");
                String id = h[0], name = h[1];
                chatArea.append("💀 " + name + "님이 발각되었습니다!\n");
                PlayerData pp = players.get(id);
                if (pp != null)
                    pp.alive = false;
                if (myClientId != null && myClientId.equals(id)) {
                    isAlive = false;
                    statusLabel.setText("💀 사망 - 관전 모드");
                    showDeathDialog();
                }
            }
            case "WRONG_SHOT" -> {
                String[] s = p[1].split(":");
                String shooter = s[0];
                int newHp = Integer.parseInt(s[1]);
                PlayerData me = players.get(shooter);
                if (me != null)
                    me.hp = newHp;
                if (isSeeker && myClientId != null && myClientId.equals(shooter))
                    chatArea.append("❌ 가짜 사물! HP: " + newHp + "\n");
            }
            case "BULLET" -> {
                // BULLET:sx:sy:ex:ey
                String[] b = p[1].split(":");
                double sx = Double.parseDouble(b[0]), sy = Double.parseDouble(b[1]);
                double ex = Double.parseDouble(b[2]), ey = Double.parseDouble(b[3]);
                gamePanel.spawnBulletTrail(sx, sy, ex, ey);
            }
            case "HIT" -> {
                // HIT:TYPE:id:x:y
                String[] h = p[1].split(":");
                double hx = Double.parseDouble(h[2]);
                double hy = Double.parseDouble(h[3]);
                gamePanel.spawnHitEffect(hx, hy);
            }
            case "SEEKER_DIED" -> chatArea.append("🎉 술래의 HP가 0이 되었습니다!\n");
            case "GAME_END" -> {
                currentState = GameState.ENDED;
                String r = p[1];
                if (r.startsWith("SEEKER_WIN")) {
                    // GAME_END:SEEKER_WIN:술래이름
                    String seekerName = (p.length >= 3) ? p[2] : "술래";
                    statusLabel.setText("🏆 게임 종료 - 술래 승리!");
                    SwingUtilities.invokeLater(() -> showGameEndDialog(true, seekerName));
                } else if (r.equals("HIDERS_WIN")) {
                    // GAME_END:HIDERS_WIN
                    statusLabel.setText("🏆 게임 종료 - 도망자 승리!");
                    SwingUtilities.invokeLater(() -> showGameEndDialog(false, null));
                }
            }
            case "GAME_RESET" -> {
                currentState = GameState.WAITING;
                statusLabel.setText("🎮 대기 중...");
                players.clear();
                objects.clear();
                initialMapObjects.clear();
                startBtn.setEnabled(true);
                isSeeker = false;
                isAlive = true;
            }
        }
        gamePanel.repaint();
    }

    private void parseGameState(String msg) {
        String[] a = msg.split(":", 4);
        if (a.length < 4)
            return;
        currentState = GameState.valueOf(a[1]);

        // 이전 변장 상태 보관
        Map<String, String> prevDisguise = new HashMap<>(lastDisguiseMap);

        players.clear();
        objects.clear();
        for (String s : a[2].split(";")) {
            if (s.isEmpty())
                continue;
            String[] d = s.split(",");
            PlayerData p = new PlayerData();
            p.id = d[0];
            p.name = d[1];
            p.isSeeker = Boolean.parseBoolean(d[2]);
            p.alive = Boolean.parseBoolean(d[3]);
            p.hp = Integer.parseInt(d[4]);
            p.x = Double.parseDouble(d[5]);
            p.y = Double.parseDouble(d[6]);
            p.disguise = d[7].equals("NONE") ? null : d[7];
            players.put(p.id, p);
            if (myClientId != null && p.id.equals(myClientId)) {
                isSeeker = p.isSeeker;
                isAlive = p.alive;
            }
            if (!p.isSeeker) {
                lastDisguiseMap.put(p.id, p.disguise);
            }
        }
        for (String s : a[3].split(";")) {
            if (s.isEmpty())
                continue;
            String[] d = s.split(",");
            objects.put(d[0], new ObjectInfo(d[1], Double.parseDouble(d[2]), Double.parseDouble(d[3])));
        }
        updateCameraToMe();

        // 변장 변경 감지 후 카운트다운 리셋
        if (currentState == GameState.PLAYING) {
            boolean changed = false;
            for (Map.Entry<String, String> e : lastDisguiseMap.entrySet()) {
                String id = e.getKey();
                String cur = e.getValue();
                String prev = prevDisguise.get(id);
                if (!Objects.equals(cur, prev)) {
                    changed = true;
                    break;
                }
            }
            if (changed)
                lastDisguiseChangeMs = System.currentTimeMillis();
        }
    }

    private void updatePlayerPos(String data) {
        String[] a = data.split(":");
        PlayerData p = players.get(a[0]);
        if (p != null) {
            p.x = Double.parseDouble(a[1]);
            p.y = Double.parseDouble(a[2]);
            if (myClientId != null && p.id.equals(myClientId))
                updateCameraToMe();
        }
    }

    private void sendChat() {
        String m = chatInput.getText().trim();
        if (!m.isEmpty())
            out.println("CHAT:" + m);
        chatInput.setText("");
    }

    // ===== 입력/이동/사격 =====
    private void setupInput() {
        gamePanel.setFocusable(true);
        gamePanel.requestFocusInWindow();

        InputMap im = gamePanel.getInputMap(JPanel.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = gamePanel.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, false), "W_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true), "W_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "S_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "S_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false), "A_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "A_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "D_P");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "D_R");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "SHOOT");

        am.put("W_P", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kUp = true;
            }
        });
        am.put("W_R", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kUp = false;
            }
        });
        am.put("S_P", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kDown = true;
            }
        });
        am.put("S_R", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kDown = false;
            }
        });
        am.put("A_P", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kLeft = true;
            }
        });
        am.put("A_R", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kLeft = false;
            }
        });
        am.put("D_P", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kRight = true;
            }
        });
        am.put("D_R", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                kRight = false;
            }
        });

        am.put("SHOOT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!(isSeeker && isAlive && currentState == GameState.PLAYING))
                    return;
                PlayerData me = players.get(myClientId);
                if (me == null)
                    return;
                // 마우스 조준: 화면좌표 -> 월드좌표 변환 후 방향 벡터 계산
                double mx = mouseX >= 0 ? (mouseX + camX) : (me.x + faceDX);
                double my = mouseY >= 0 ? (mouseY + camY) : (me.y + faceDY);
                double dx = mx - me.x;
                double dy = my - me.y;
                double len = Math.hypot(dx, dy);
                if (len < 1e-6) {
                    dx = 0;
                    dy = -1; // 기본 위 방향
                } else {
                    dx /= len;
                    dy /= len;
                }
                out.println("SHOOT_RAY:" + me.x + ":" + me.y + ":" + dx + ":" + dy);
            }
        });

        // E키 변장 변경 기능 제거
    }

    private void startMoveLoop() {
        new javax.swing.Timer(16, e -> {
            if (!isAlive)
                return;
            if (!(currentState == GameState.HIDING || currentState == GameState.PLAYING))
                return;
            if (myClientId == null)
                return;

            PlayerData me = players.get(myClientId);
            if (me == null)
                return;

            // 술래는 HIDING 동안(초반 20초) 이동 불가
            if (isSeeker && currentState == GameState.HIDING) {
                return;
            }

            double dx = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
            double dy = (kDown ? 1 : 0) - (kUp ? 1 : 0);
            if (dx != 0 || dy != 0) {
                double n = Math.hypot(dx, dy);
                dx /= n;
                dy /= n;

                // 플레이어 타입에 따른 속도 적용
                double speed = isSeeker ? SEEKER_MOVE_SPEED : HIDER_MOVE_SPEED;

                // 벽 충돌 방지를 위한 경계 확인
                double newX = clamp(me.x + dx * speed, MIN_X, MAX_X);
                double newY = clamp(me.y + dy * speed, MIN_Y, MAX_Y);

                me.x = newX;
                me.y = newY;
                // 바라보는 방향 갱신
                faceDX = dx;
                faceDY = dy;
                out.println("MOVE:" + me.x + ":" + me.y);
                updateCameraToMe();
                gamePanel.repaint();
            }
        }).start();
    }

    // ===== 카운트다운 타이머 =====
    private void startCountdownTimer() {
        new javax.swing.Timer(1000, e -> {
            if (countdownSeconds > 0) {
                countdownSeconds--;
                if (isSeeker) {
                    statusLabel.setText("🔴 술래 - " + countdownSeconds + "초 후 시작!");
                } else {
                    statusLabel.setText("🟦 숨는 사람 - " + countdownSeconds + "초 남음!");
                }
                gamePanel.repaint();
            } else {
                ((javax.swing.Timer) e.getSource()).stop();
                showCountdown = false;
                if (isSeeker) {
                    statusLabel.setText("🔴 술래 - 게임 시작!");
                    // 술래 움직임 허용 시작(표시만 업데이트, 로직은 타이머 체크로 제어)
                } else {
                    statusLabel.setText("🟦 숨는 사람 - 숨어라!");
                }
                gamePanel.repaint();
            }
        }).start();
    }

    // ===== 카메라 =====
    private void updateCameraToMe() {
        PlayerData me = players.get(myClientId);
        if (me == null)
            return;
        double vw = gamePanel.getWidth();
        double vh = gamePanel.getHeight();
        camX = clamp(me.x - vw / 2, 0, worldW - vw);
        camY = clamp(me.y - vh / 2, 0, worldH - vh);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ===== 이미지 로딩 =====
    /**
     * ImageManager에서 이미지를 로드하여 캐시에 복사
     */
    private void loadImagesFromManager() {
        ImageManager imgMgr = ImageManager.getInstance();

        // 기본 이미지들 복사
        copyImageFromManager("BG_TILE", imgMgr);
        copyImageFromManager("SEEKER", imgMgr);

        // 현재 테마의 모든 오브젝트 이미지 복사
        String[] objectTypes = imgMgr.getCurrentThemeObjectTypes();
        for (String type : objectTypes) {
            copyImageFromManager(type, imgMgr);
        }
    }

    /**
     * 테마별 이미지 로드
     */
    private void loadThemeImages(String theme) {
        // 테마명 변환 (서버에서 오는 테마명을 폴더명으로 매핑)
        String folderName = switch (theme.toUpperCase()) {
            case "CITY" -> "City";
            case "CONSTRUCTION" -> "Construction_site";
            case "SCHOOL" -> "School";
            default -> "School";
        };

        ImageManager imgMgr = ImageManager.getInstance();
        imgMgr.loadThemeImages(folderName);
        loadImagesFromManager(); // 캐시 업데이트

        System.out.println("테마 변경: " + theme + " -> " + folderName);
    }

    /**
     * ImageManager에서 imageCache로 이미지 복사
     */
    private void copyImageFromManager(String key, ImageManager imgMgr) {
        BufferedImage img = imgMgr.getImage(key);
        if (img != null) {
            imageCache.put(key, img);
        }
    }

    @Deprecated
    private void loadImages() {
        loadImage("BG_TILE", "/assets/Background.png", "resources/assets/Background.png", "assets/Background.png");
        loadImage("SEEKER", "/assets/Man.png", "resources/assets/Man.png", "assets/Man.png");
        loadImage("BOX", "/assets/Box.png", "resources/assets/Box.png", "assets/Box.png");
        loadImage("CHAIR", "/assets/Chair.png", "resources/assets/Chair.png", "assets/Chair.png");
        loadImage("BARREL", "/assets/CircleBox.png", "resources/assets/CircleBox.png", "assets/CircleBox.png");
        loadImage("CONE", "/assets/Con.png", "resources/assets/Con.png", "assets/Con.png");
        loadImage("TABLE", "/assets/Table.png", "resources/assets/Table.png", "assets/Table.png");
        loadImage("TIRE", "/assets/Tier.png", "resources/assets/Tier.png", "assets/Tier.png");
    }

    private void loadImage(String key, String... paths) {
        for (String p : paths) {
            try {
                BufferedImage bi = null;
                if (p.startsWith("/")) {
                    try (InputStream is = GameClient.class.getResourceAsStream(p)) {
                        if (is != null)
                            bi = ImageIO.read(is);
                    }
                } else {
                    File f = new File(p);
                    if (f.exists())
                        bi = ImageIO.read(f);
                }
                if (bi != null) {
                    imageCache.put(key, bi);
                    System.out.println("✅ IMG " + key + " <- " + p);
                    return;
                }
            } catch (Exception ignore) {
            }
        }
        System.out.println("❌ IMG FAIL " + key);
    }

    // ====== GamePanel ======
    class GamePanel extends JPanel {
        // 이펙트
        private final List<BulletTrail> trails = new ArrayList<>();
        private final List<HitEffect> hits = new ArrayList<>();

        class BulletTrail {
            double sx, sy, ex, ey;
            int life = 12;
        }

        class HitEffect {
            double x, y;
            int life = 12;
        }

        GamePanel() {
            setBackground(new Color(20, 20, 22));
            new javax.swing.Timer(50, e -> {
                trails.removeIf(t -> --t.life <= 0);
                hits.removeIf(h -> --h.life <= 0);
                repaint();
            }).start();

            // 리사이즈 시 오버레이 크기 조정
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (gameEndOverlay != null) {
                        gameEndOverlay.setBounds(0, 0, getWidth(), getHeight());
                    }
                }
            });
        }

        void spawnBulletTrail(double sx, double sy, double ex, double ey) {
            BulletTrail t = new BulletTrail();
            t.sx = sx;
            t.sy = sy;
            t.ex = ex;
            t.ey = ey;
            trails.add(t);
        }

        void spawnHitEffect(double x, double y) {
            HitEffect h = new HitEffect();
            h.x = x;
            h.y = y;
            hits.add(h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawBackground(g2);

            if (currentState == GameState.HIDING) {
                // 초기 오브젝트
                for (ObjectInfo o : initialMapObjects)
                    drawObject(g2, o.type, o.x, o.y, false, null);
                // 숨는사람만 자기 자신 표시
                if (!isSeeker && myClientId != null) {
                    PlayerData me = players.get(myClientId);
                    if (me != null)
                        drawObject(g2, me.disguise == null ? "BOX" : me.disguise, me.x, me.y, true, myName);
                }
                drawMiniMap(g2);
                return;
            }

            // 오브젝트
            for (ObjectInfo o : objects.values())
                drawObject(g2, o.type, o.x, o.y, false, null);

            // 플레이어
            for (PlayerData p : players.values()) {
                if (!p.alive)
                    continue;
                if (p.isSeeker)
                    drawSeeker(g2, p);
                else {
                    if (isSeeker && myClientId != null && !p.id.equals(myClientId)) {
                        drawObject(g2, p.disguise == null ? "BOX" : p.disguise, p.x, p.y, false, null);
                    } else {
                        drawObject(g2, p.disguise == null ? "BOX" : p.disguise, p.x, p.y, true, p.name);
                    }
                }
            }

            // 총알 궤적
            for (BulletTrail t : trails) {
                int sx = (int) Math.round(t.sx - camX);
                int sy = (int) Math.round(t.sy - camY);
                int ex = (int) Math.round(t.ex - camX);
                int ey = (int) Math.round(t.ey - camY);
                g2.setStroke(new BasicStroke(2));
                g2.setColor(new Color(255, 50, 50, 200));
                g2.drawLine(sx, sy, ex, ey);
            }
            // 히트 이펙트
            for (HitEffect h : hits) {
                int x = (int) Math.round(h.x - camX);
                int y = (int) Math.round(h.y - camY);
                g2.setColor(new Color(255, 220, 60, h.life * 20));
                g2.fillOval(x - 12, y - 12, 24, 24);
                g2.setColor(new Color(255, 120, 0, h.life * 20));
                g2.drawOval(x - 16, y - 16, 32, 32);
            }

            drawUI(g2);
            drawMiniMap(g2);
        }

        private void drawBackground(Graphics2D g) {
            Image bgImage = imageCache.get("BG_TILE");
            if (bgImage != null) {
                // 전체 화면을 배경 이미지로 채움 (격자 무늬 없이)
                g.drawImage(bgImage, (int) -camX, (int) -camY, worldW, worldH, null);
            } else {
                // 기본 배경색
                g.setColor(new Color(60, 90, 70));
                g.fillRect((int) -camX, (int) -camY, worldW, worldH);
            }

            // 벽 테두리 그리기
            g.setColor(new Color(40, 40, 40));
            g.setStroke(new BasicStroke(4));
            g.drawRect((int) -camX, (int) -camY, worldW, worldH);

            // 벽 영역 표시 (진한 색상)
            g.setColor(new Color(30, 30, 30, 100));
            // 상단 벽
            g.fillRect((int) -camX, (int) -camY, worldW, (int) WALL_THICKNESS);
            // 하단 벽
            g.fillRect((int) -camX, (int) (worldH - WALL_THICKNESS - camY), worldW,
                    (int) WALL_THICKNESS);
            // 좌측 벽
            g.fillRect((int) -camX, (int) -camY, (int) WALL_THICKNESS, worldH);
            // 우측 벽
            g.fillRect((int) (worldW - WALL_THICKNESS - camX), (int) -camY,
                    (int) WALL_THICKNESS, worldH);
        }

        private void drawSeeker(Graphics2D g, PlayerData p) {
            int x = (int) Math.round(p.x - camX);
            int y = (int) Math.round(p.y - camY);
            Image seeker = imageCache.get("SEEKER");
            if (seeker != null)
                g.drawImage(seeker, x - 24, y - 32, 48, 64, null);
            else {
                g.setColor(new Color(220, 50, 50));
                g.fillOval(x - 20, y - 30, 40, 50);
                g.setColor(Color.BLACK);
                g.drawOval(x - 20, y - 30, 40, 50);
            }
            // 이름/HP
            g.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            String info = p.name + " [HP:" + p.hp + "]";
            int w = g.getFontMetrics().stringWidth(info);
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(x - w / 2 - 4, y - 52, w + 8, 18, 6, 6);
            g.setColor(Color.WHITE);
            g.drawString(info, x - w / 2, y - 38);

            // 조준 십자선 (내가 술래일 때만)
            if (myClientId != null && p.id.equals(myClientId) && isSeeker && isAlive
                    && currentState == GameState.PLAYING) {
                int cx = (mouseX >= 0 ? mouseX : getWidth() / 2);
                int cy = (mouseY >= 0 ? mouseY : getHeight() / 2);
                g.setColor(new Color(255, 0, 0, 180));
                g.setStroke(new BasicStroke(2));
                g.drawLine(cx - 20, cy, cx - 6, cy);
                g.drawLine(cx + 6, cy, cx + 20, cy);
                g.drawLine(cx, cy - 20, cx, cy - 6);
                g.drawLine(cx, cy + 6, cx, cy + 20);
                g.drawOval(cx - 3, cy - 3, 6, 6);
            }
        }

        private void drawObject(Graphics2D g, String type, double wx, double wy, boolean isPlayer, String name) {
            if (type == null)
                type = "BOX";
            if ("CRATE".equals(type))
                type = "BOX";
            // 일부 테마 전용 타입(PLANT/LAMP/BOOK 등)에 대한 가시성 보장용 매핑
            if ("PLANT".equals(type) || "LAMP".equals(type) || "BOOK".equals(type)) {
                type = "BOX"; // 스프라이트가 없는 경우 BOX로 대체 렌더링
            }
            int x = (int) Math.round(wx - camX);
            int y = (int) Math.round(wy - camY);

            if (isPlayer && name != null) {
                g.setColor(new Color(100, 255, 100, 100));
                g.fillOval(x - 28, y - 28, 56, 56);
            }

            Image spr = imageCache.get(type);
            if (spr != null) {
                g.drawImage(spr, x - 24, y - 24, 48, 48, null);
            } else {
                // 폴백 간단도형
                switch (type) {
                    case "BOX" -> {
                        g.setColor(new Color(160, 82, 45));
                        g.fillRect(x - 22, y - 22, 44, 44);
                    }
                    case "BARREL" -> {
                        g.setColor(Color.GRAY);
                        g.fillOval(x - 22, y - 28, 44, 56);
                    }
                    case "CONE" -> {
                        g.setColor(new Color(255, 140, 0));
                        int[] xp = { x, x - 20, x + 20 };
                        int[] yp = { y - 30, y + 20, y + 20 };
                        g.fillPolygon(xp, yp, 3);
                    }
                    case "TIRE" -> {
                        g.setColor(Color.BLACK);
                        g.fillOval(x - 24, y - 24, 48, 48);
                        g.setColor(Color.DARK_GRAY);
                        g.fillOval(x - 12, y - 12, 24, 24);
                    }
                    case "TABLE" -> {
                        g.setColor(new Color(150, 80, 40));
                        g.fillRect(x - 35, y - 8, 70, 12);
                    }
                    case "CHAIR" -> {
                        g.setColor(new Color(139, 69, 19));
                        g.fillRect(x - 18, y - 8, 36, 8);
                    }
                    default -> {
                        // 알 수 없는 타입 기본 사각형 표시(가시성 확보)
                        g.setColor(new Color(120, 120, 120));
                        g.fillRect(x - 20, y - 20, 40, 40);
                    }
                }
            }

            if (isPlayer && name != null) {
                g.setFont(new Font("Malgun Gothic", Font.BOLD, 11));
                int w = g.getFontMetrics().stringWidth(name);
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRoundRect(x - w / 2 - 3, y - 40, w + 6, 14, 6, 6);
                g.setColor(new Color(0, 170, 0));
                g.drawString(name, x - w / 2, y - 29);
            }
        }

        private void drawUI(Graphics2D g) {
            if (myClientId == null)
                return;
            PlayerData me = players.get(myClientId);
            if (me == null)
                return;

            if (isSeeker && isAlive) {
                // HP 바
                int W = 220, H = 28;
                int X = 20, Y = 20;
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRoundRect(X - 5, Y - 5, W + 10, H + 10, 10, 10);
                g.setColor(new Color(60, 60, 60));
                g.fillRect(X, Y, W, H);
                int w = (int) (W * me.hp / 100.0);
                g.setColor(new Color(76, 175, 80));
                g.fillRect(X, Y, w, H);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
                g.drawString("HP: " + me.hp + "/100", X + 10, Y + 19);

                // 남은 생존자
                long alive = players.values().stream().filter(p -> !p.isSeeker && p.alive).count();
                int bx = getWidth() / 2 - 80, by = 20;
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRoundRect(bx - 10, by - 5, 160, 30, 10, 10);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
                g.drawString("🎯 남은 생존자: " + alive, bx, by + 16);
            } else if (!isSeeker && isAlive) {
                // 도망자(내 클라이언트 전용) HP 표시: 좌측 상단 소형 바
                int W = 160, H = 20;
                int X = 20, Y = 20;
                g.setColor(new Color(0, 0, 0, 140));
                g.fillRoundRect(X - 5, Y - 5, W + 10, H + 10, 10, 10);
                g.setColor(new Color(60, 60, 60));
                g.fillRect(X, Y, W, H);
                int w = (int) (W * me.hp / 100.0);
                g.setColor(new Color(66, 165, 245));
                g.fillRect(X, Y, w, H);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
                g.drawString("HP: " + me.hp + "/100", X + 8, Y + 15);
            }

            // 카운트다운 표시 (모든 플레이어)
            if (showCountdown && countdownSeconds > 0) {
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;

                // 애니메이션 효과를 위한 스케일 계산 (숫자가 바뀔 때 커졌다 작아짐)
                long currentTime = System.currentTimeMillis();
                double scale = 1.0;
                if (countdownSeconds <= 3) {
                    // 마지막 3초는 펄스 효과
                    scale = 1.0 + 0.3 * Math.sin(currentTime * 0.01);
                }

                // 배경 (플레이어 타입에 따라 색상 다르게)
                if (isSeeker) {
                    g.setColor(new Color(220, 50, 50, 180)); // 빨간색 (술래)
                } else {
                    g.setColor(new Color(50, 120, 220, 180)); // 파란색 (도둑)
                }
                int bgSize = (int) (200 * scale);
                g.fillRoundRect(cx - bgSize / 2, cy - 80, bgSize, 160, 20, 20);

                // 테두리 효과
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(3));
                g.drawRoundRect(cx - bgSize / 2, cy - 80, bgSize, 160, 20, 20);

                // 카운트다운 숫자 (큰 폰트)
                int fontSize = (int) (72 * scale);
                g.setFont(new Font("Malgun Gothic", Font.BOLD, fontSize));
                String countText = String.valueOf(countdownSeconds);

                // 숫자 색상 (마지막 3초는 빨간색으로 경고)
                if (countdownSeconds <= 3) {
                    g.setColor(new Color(255, 100, 100));
                } else {
                    g.setColor(Color.WHITE);
                }

                int textWidth = g.getFontMetrics().stringWidth(countText);
                g.drawString(countText, cx - textWidth / 2, cy + fontSize / 3);

                // 안내 텍스트 (플레이어 타입에 따라 다른 메시지)
                g.setFont(new Font("Malgun Gothic", Font.BOLD, 18));
                g.setColor(Color.WHITE);
                String infoText;
                if (isSeeker) {
                    infoText = "술래는 잠시 기다려주세요";
                } else {
                    infoText = "빨리 숨어주세요!";
                }
                int infoWidth = g.getFontMetrics().stringWidth(infoText);
                g.drawString(infoText, cx - infoWidth / 2, cy + 60);

                // 진행률 바 (20초에서 0초까지)
                int barWidth = 300;
                int barHeight = 8;
                int progress = (20 - countdownSeconds) * barWidth / 20;

                // 배경 바
                g.setColor(new Color(80, 80, 80));
                g.fillRoundRect(cx - barWidth / 2, cy + 80, barWidth, barHeight, 4, 4);

                // 진행률 바 (색상 변화)
                Color progressColor;
                if (countdownSeconds > 10) {
                    progressColor = new Color(100, 200, 100); // 초록
                } else if (countdownSeconds > 5) {
                    progressColor = new Color(255, 200, 100); // 주황
                } else {
                    progressColor = new Color(255, 100, 100); // 빨강
                }
                g.setColor(progressColor);
                g.fillRoundRect(cx - barWidth / 2, cy + 80, progress, barHeight, 4, 4);
            }

            // 변장 변경 카운트다운 (양측 패널 모두 표시)
            if (currentState == GameState.PLAYING && lastDisguiseChangeMs > 0) {
                long now = System.currentTimeMillis();
                long elapsed = (now - lastDisguiseChangeMs) / 1000;
                int remain = (int) Math.max(0, 10 - elapsed);
                String text = "변장 변경까지 " + remain + "초";
                int cx = getWidth() / 2;
                int y = 16;
                g.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
                int tw = g.getFontMetrics().stringWidth(text);
                g.setColor(new Color(0, 0, 0, 140));
                g.fillRoundRect(cx - tw / 2 - 10, y - 12, tw + 20, 24, 10, 10);
                g.setColor(isSeeker ? new Color(255, 120, 120) : new Color(120, 180, 255));
                g.drawString(text, cx - tw / 2, y + 4);
            }

            // 하단 조작법
            if (currentState == GameState.PLAYING) {
                g.setColor(new Color(255, 255, 255, 160));
                g.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
                String controls = isSeeker ? "WASD: 이동 | SPACE: 사격" : "WASD: 이동";
                g.drawString(controls, 20, getHeight() - 18);
            }
        }

        private void drawMiniMap(Graphics2D g) {
            int pad = 10, mmW = 220, mmH = 160;
            int x = getWidth() - mmW - pad, y = pad;

            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(x - 6, y - 6, mmW + 12, mmH + 12, 10, 10);
            g.setColor(new Color(25, 25, 28));
            g.fillRect(x, y, mmW, mmH);

            double sx = mmW / (double) worldW, sy = mmH / (double) worldH;

            // 내 위치만 표시
            if (myClientId != null) {
                PlayerData me = players.get(myClientId);
                if (me != null && me.alive) {
                    int px = x + (int) Math.round(me.x * sx);
                    int py = y + (int) Math.round(me.y * sy);
                    g.setColor(isSeeker ? new Color(230, 60, 60) : new Color(60, 200, 90));
                    g.fillOval(px - 3, py - 3, 6, 6);
                    g.setColor(Color.WHITE);
                    g.drawOval(px - 4, py - 4, 8, 8);
                }
            }

            // 현재 뷰포트
            int vw = (int) Math.round(getWidth() * sx);
            int vh = (int) Math.round(getHeight() * sy);
            int vx = x + (int) Math.round(camX * sx);
            int vy = y + (int) Math.round(camY * sy);
            g.setColor(Color.WHITE);
            g.drawRect(vx, vy, vw, vh);
        }
    }
}
