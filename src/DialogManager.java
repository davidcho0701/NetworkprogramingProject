import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 게임의 다이얼로그(역할, 사망, 게임 종료 등)를 관리하는 클래스
 */
public class DialogManager {

    /**
     * 역할 알림 다이얼로그 (술래/숨는 사람)
     */
    public static void showRoleDialog(JFrame parent, boolean isSeeker) {
        final JDialog dialog = new JDialog(parent, "알림", true);
        dialog.setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isSeeker ? new Color(180, 60, 60) : new Color(60, 120, 180), 3),
                BorderFactory.createLineBorder(new Color(15, 20, 30), 2)));
        root.setBackground(new Color(12, 18, 26));

        // 배너
        JPanel banner = createRoleBanner(isSeeker);
        banner.setPreferredSize(new Dimension(580, 190));
        banner.setLayout(new GridBagLayout());

        String icon = isSeeker ? "🔦" : "👻";
        String titleText = isSeeker ? icon + " 당신은 술래입니다! " + icon : icon + " 당신은 숨는 사람입니다! " + icon;
        String lines = isSeeker ? "⏰ 20초 후 움직일 수 있습니다\n⌨️ WASD: 이동 | SPACE: 사격" : "⌨️ WASD로 이동하여 숨으세요!";

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

        // 버튼
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 14));
        bottom.setBackground(new Color(12, 18, 26));

        JButton ok = new JButton("✓ 준비 완료");
        ok.setBackground(isSeeker ? new Color(140, 50, 50) : new Color(50, 100, 150));
        ok.setForeground(Color.WHITE);
        ok.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        ok.setFocusPainted(false);
        ok.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isSeeker ? new Color(180, 80, 80) : new Color(80, 140, 200), 2),
                BorderFactory.createEmptyBorder(10, 30, 10, 30)));

        ok.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                ok.setBackground(isSeeker ? new Color(160, 60, 60) : new Color(60, 120, 170));
            }

            public void mouseExited(MouseEvent e) {
                ok.setBackground(isSeeker ? new Color(140, 50, 50) : new Color(50, 100, 150));
            }
        });

        bottom.add(ok);

        root.add(banner, BorderLayout.NORTH);
        root.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        ok.addActionListener(e -> dialog.dispose());
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /**
     * 사망 알림 다이얼로그
     */
    public static void showDeathDialog(JFrame parent) {
        final JDialog dialog = new JDialog(parent, "발각됨", true);
        dialog.setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 80, 80), 3),
                BorderFactory.createLineBorder(new Color(15, 18, 24), 2)));
        root.setBackground(new Color(12, 15, 20));

        JPanel banner = createDeathBanner();
        banner.setPreferredSize(new Dimension(540, 180));
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
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // 헬퍼 메서드들

    private static JPanel createRoleBanner(boolean isSeeker) {
        return new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gp;
                if (isSeeker) {
                    gp = new GradientPaint(0, 0, new Color(100, 30, 30),
                            getWidth(), getHeight(), new Color(60, 15, 15));
                } else {
                    gp = new GradientPaint(0, 0, new Color(25, 50, 85),
                            getWidth(), getHeight(), new Color(15, 30, 55));
                }
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 장식 효과
                g2.setColor(isSeeker ? new Color(255, 100, 100, 40) : new Color(100, 150, 255, 40));
                g2.fillOval(-50, -50, 200, 200);
                g2.fillOval(getWidth() - 150, getHeight() - 150, 200, 200);

                g2.dispose();
            }
        };
    }

    private static JPanel createDeathBanner() {
        return new JPanel() {
            @Override
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
    }
}
