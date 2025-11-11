import java.awt.*;

/**
 * 게임 렌더링 유틸리티 클래스
 * 그리기 관련 헬퍼 메서드 제공
 */
public class RenderUtils {

    /**
     * 값을 범위 내로 제한
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 반투명 오버레이 그리기
     */
    public static void drawOverlay(Graphics2D g2, int width, int height, int alpha) {
        g2.setColor(new Color(0, 0, 0, alpha));
        g2.fillRect(0, 0, width, height);
    }

    /**
     * 그라데이션 배경 그리기
     */
    public static void drawGradientBackground(Graphics2D g2, Color startColor, Color endColor, int width, int height) {
        GradientPaint gp = new GradientPaint(0, 0, startColor, width, height, endColor);
        g2.setPaint(gp);
        g2.fillRect(0, 0, width, height);
    }

    /**
     * 승리 효과 원 그리기
     */
    public static void drawVictoryCircles(Graphics2D g2, Color color, int count) {
        g2.setColor(color);
        for (int i = 0; i < count; i++) {
            g2.fillOval(50 + i * 150, 20 + i * 30, 120, 120);
        }
    }

    /**
     * 미니맵 그리기
     */
    public static void drawMiniMap(Graphics2D g2, int panelWidth, int panelHeight,
            int worldW, int worldH, double camX, double camY) {
        int mmW = 200, mmH = 120;
        int mmX = panelWidth - mmW - 10;
        int mmY = 10;

        // 미니맵 배경
        g2.setColor(new Color(30, 30, 35, 200));
        g2.fillRect(mmX, mmY, mmW, mmH);
        g2.setColor(new Color(100, 150, 200));
        g2.drawRect(mmX, mmY, mmW, mmH);

        // 현재 뷰포트 표시
        int vx = (int) ((camX / worldW) * mmW) + mmX;
        int vy = (int) ((camY / worldH) * mmH) + mmY;
        int vw = (int) ((panelWidth / (double) worldW) * mmW);
        int vh = (int) ((panelHeight / (double) worldH) * mmH);
        g2.setColor(new Color(255, 255, 100, 120));
        g2.fillRect(vx, vy, vw, vh);
    }

    /**
     * 카운트다운 텍스트 그리기
     */
    public static void drawCountdown(Graphics2D g2, int seconds, int panelWidth, int panelHeight) {
        String text = "게임 시작까지: " + seconds + "초";
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(panelWidth / 2 - 150, 30, 300, 60, 15, 15);

        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Malgun Gothic", Font.BOLD, 28));
        FontMetrics fm = g2.getFontMetrics();
        int textW = fm.stringWidth(text);
        g2.drawString(text, panelWidth / 2 - textW / 2, 68);
    }

    /**
     * HP 바 그리기
     */
    public static void drawHealthBar(Graphics2D g2, int x, int y, int width, int height,
            int currentHp, int maxHp, Color barColor) {
        // 배경
        g2.setColor(new Color(60, 60, 60));
        g2.fillRect(x, y, width, height);

        // HP 바
        int hpWidth = (int) ((currentHp / (double) maxHp) * width);
        g2.setColor(barColor);
        g2.fillRect(x, y, hpWidth, height);

        // 테두리
        g2.setColor(Color.WHITE);
        g2.drawRect(x, y, width, height);
    }
}
