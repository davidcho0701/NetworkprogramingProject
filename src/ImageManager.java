import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 이미지 로딩 및 관리를 담당하는 클래스
 * 테마별 이미지(City, Construction_site, School) 지원
 */
public class ImageManager {
    private static ImageManager instance;
    private final Map<String, BufferedImage> images = new HashMap<>();
    private String currentTheme = "School"; // 기본 테마

    private ImageManager() {
    }

    public static ImageManager getInstance() {
        if (instance == null) {
            instance = new ImageManager();
        }
        return instance;
    }

    /**
     * 모든 게임 이미지를 로드 (기본 테마)
     */
    public void loadAllImages() {
        loadThemeImages("School");
    }

    /**
     * 특정 테마의 이미지들을 로드
     */
    public void loadThemeImages(String theme) {
        currentTheme = theme;
        images.clear(); // 기존 이미지 클리어

        String themePath = "/assets/" + theme + "/";

        // 배경과 술래 이미지 로드
        loadImage("BG_TILE", themePath + "background.png");
        loadImage("SEEKER", themePath + "Tagger.png");

        // 테마별 오브젝트 이미지 로드
        switch (theme) {
            case "City":
                loadImage("CON", themePath + "Con.png");
                loadImage("OLDMAN", themePath + "OldMan.png");
                loadImage("BLUEMAN", themePath + "blueMan.png");
                loadImage("BLUE_CAR_H", themePath + "blue_car_horizontal.png");
                loadImage("BLUE_CAR_V", themePath + "blue_car_verticle.png");
                loadImage("LIGHT", themePath + "light.png");
                loadImage("RED_CAR_H", themePath + "red_car_horizontal 2.png");
                loadImage("RED_CAR_V", themePath + "red_car_verticle.png");
                loadImage("TIRE", themePath + "tire.png");
                loadImage("TRASH", themePath + "trash.png");
                loadImage("WALKMAN", themePath + "walkman.png");
                loadImage("WALKWOMAN", themePath + "walkwoman.png");
                loadImage("WOMAN", themePath + "woman.png");
                break;

            case "Construction_site":
                loadImage("BOX", themePath + "Box.png");
                loadImage("CIRCLEBOX", themePath + "CircleBox.png");
                loadImage("CON", themePath + "Con.png");
                loadImage("TIRE", themePath + "Tire.png");
                loadImage("BRICK", themePath + "brick.png");
                loadImage("FENCE", themePath + "fence.png");
                break;

            case "School":
            default:
                loadImage("CHAIR", themePath + "Chair.png");
                loadImage("DESK", themePath + "Desk.png");
                loadImage("BROWNCLEANER", themePath + "browncleaner.png");
                loadImage("FIRESTOP", themePath + "firestop.png");
                loadImage("TRASH", themePath + "trash.png");
                loadImage("WHITECLEANER", themePath + "whitecleaner.png");
                break;
        }
    }

    /**
     * 단일 이미지 로드
     */
    private void loadImage(String key, String path) {
        try {
            // 다양한 경로에서 이미지 로드 시도
            BufferedImage img = null;

            // 1. 클래스패스에서 로드 시도
            try (var is = getClass().getResourceAsStream(path)) {
                if (is != null) {
                    img = ImageIO.read(is);
                }
            } catch (Exception e) {
                // 무시하고 다음 방법 시도
            }

            // 2. 파일 시스템에서 로드 시도 (상대 경로)
            if (img == null) {
                try {
                    java.io.File file = new java.io.File("resources" + path);
                    if (file.exists()) {
                        img = ImageIO.read(file);
                    }
                } catch (Exception e) {
                    // 무시하고 다음 방법 시도
                }
            }

            // 3. 절대 경로로 시도
            if (img == null) {
                try {
                    String absolutePath = "/Users/choseongbeen/Downloads/NetworkprogramingProject-main/resources"
                            + path;
                    java.io.File file = new java.io.File(absolutePath);
                    if (file.exists()) {
                        img = ImageIO.read(file);
                    }
                } catch (Exception e) {
                    // 마지막 시도도 실패
                }
            }

            if (img != null) {
                images.put(key, img);
                System.out.println("✅ IMG " + key + " <- " + path);
            } else {
                System.err.println("❌ IMG " + key + " not found: " + path);
                // 기본 이미지로 빈 이미지 생성
                img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = img.createGraphics();
                g2.setColor(java.awt.Color.GRAY);
                g2.fillRect(0, 0, 48, 48);
                g2.setColor(java.awt.Color.BLACK);
                g2.drawRect(0, 0, 47, 47);
                g2.dispose();
                images.put(key, img);
            }
        } catch (Exception e) {
            System.err.println("❌ IMG 로드 실패 " + key + ": " + e.getMessage());
        }
    }

    /**
     * 이미지 가져오기
     */
    public BufferedImage getImage(String key) {
        return images.get(key);
    }

    /**
     * 오브젝트 타입에 따른 이미지 가져오기 (테마별)
     */
    public BufferedImage getObjectImage(String objectType) {
        if (objectType == null) {
            // 테마별 기본 오브젝트 반환
            return switch (currentTheme) {
                case "City" -> getImage("CON");
                case "Construction_site" -> getImage("BOX");
                default -> getImage("CHAIR");
            };
        }

        // 테마에 관계없이 공통으로 사용되는 오브젝트들
        BufferedImage img = getImage(objectType.toUpperCase());
        if (img != null) {
            return img;
        }

        // 기본값 반환
        return switch (currentTheme) {
            case "City" -> getImage("CON");
            case "Construction_site" -> getImage("BOX");
            default -> getImage("CHAIR");
        };
    }

    /**
     * 배경 이미지 가져오기
     */
    public BufferedImage getBackgroundImage() {
        return getImage("BG_TILE");
    }

    /**
     * 술래(플레이어) 이미지 가져오기
     */
    public BufferedImage getSeekerImage() {
        return getImage("SEEKER");
    }

    /**
     * 이미지가 로드되었는지 확인
     */
    public boolean isImageLoaded(String key) {
        return images.containsKey(key) && images.get(key) != null;
    }

    /**
     * 로드된 이미지 개수 반환
     */
    public int getLoadedImageCount() {
        return images.size();
    }

    /**
     * 모든 이미지가 로드되었는지 확인
     */
    public boolean allImagesLoaded() {
        // 기본적으로 배경과 술래 이미지는 필수
        return isImageLoaded("BG_TILE") && isImageLoaded("SEEKER");
    }

    /**
     * 현재 테마 반환
     */
    public String getCurrentTheme() {
        return currentTheme;
    }

    /**
     * 테마별 오브젝트 타입 배열 반환
     */
    public String[] getThemeObjectTypes(String theme) {
        return switch (theme) {
            case "City" -> new String[] { "CON", "OLDMAN", "BLUEMAN", "BLUE_CAR_H", "BLUE_CAR_V", "LIGHT",
                    "RED_CAR_H", "RED_CAR_V", "TIRE", "TRASH", "WALKMAN", "WALKWOMAN", "WOMAN" };
            case "Construction_site" -> new String[] { "BOX", "CIRCLEBOX", "CON", "TIRE", "BRICK", "FENCE" };
            case "School" ->
                new String[] { "CHAIR", "DESK", "BROWNCLEANER", "FIRESTOP", "TRASH", "WHITECLEANER" };
            default -> new String[] { "CHAIR", "DESK" };
        };
    }

    /**
     * 현재 테마의 오브젝트 타입 배열 반환
     */
    public String[] getCurrentThemeObjectTypes() {
        return getThemeObjectTypes(currentTheme);
    }
}