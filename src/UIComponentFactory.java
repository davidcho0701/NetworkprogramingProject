import javax.swing.*;
import java.awt.*;

/**
 * UI 컴포넌트 생성을 담당하는 팩토리 클래스
 * GameClient의 UI 생성 로직을 모듈화
 */
public class UIComponentFactory {

    /**
     * 고급스러운 버튼 생성
     */
    public static JButton createStyledButton(String text, Color bgColor, Color fgColor, Font font) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setFont(font);
        button.setFocusPainted(false);
        return button;
    }

    /**
     * 테두리가 있는 패널 생성
     */
    public static JPanel createBorderedPanel(Color borderColor, int borderThickness, Color bgColor) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createLineBorder(borderColor, borderThickness));
        panel.setBackground(bgColor);
        return panel;
    }

    /**
     * 스타일이 적용된 텍스트 영역 생성
     */
    public static JTextArea createStyledTextArea(String initialText, Color bgColor, Color fgColor, Font font) {
        JTextArea textArea = new JTextArea(initialText);
        textArea.setEditable(false);
        textArea.setBackground(bgColor);
        textArea.setForeground(fgColor);
        textArea.setFont(font);
        return textArea;
    }

    /**
     * 그라데이션 배너 패널 생성
     */
    public static JPanel createGradientBanner(Color startColor, Color endColor, int width, int height) {
        JPanel banner = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gp = new GradientPaint(
                        0, 0, startColor,
                        getWidth(), getHeight(), endColor);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        banner.setPreferredSize(new Dimension(width, height));
        banner.setOpaque(false);
        return banner;
    }

    /**
     * 숨바꼭질 테마의 맵 버튼 생성
     */
    public static JButton createMapButton(String title, String subtitle, Color themeColor) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setPreferredSize(new Dimension(280, 260));
        button.setBackground(themeColor.darker());
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);

        // 미스터리한 테두리
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 120), 2),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(themeColor.brighter().brighter(), 1),
                        BorderFactory.createEmptyBorder(20, 15, 20, 15))));

        // 버튼 내용 구성
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel iconLbl = new JLabel("👁️");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        iconLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

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

        JLabel statusLbl = new JLabel("[ 숨을 준비 완료 ]");
        statusLbl.setForeground(new Color(150, 230, 150, 200));
        statusLbl.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        statusLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

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

        return button;
    }
}
