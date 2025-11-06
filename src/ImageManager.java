import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 이미지 로딩 및 관리를 담당하는 클래스
 */
public class ImageManager {
    private static ImageManager instance;
    private final Map<String, BufferedImage> images = new HashMap<>();

    private ImageManager() {
    }

    public static ImageManager getInstance() {
        if (instance == null) {
            instance = new ImageManager();
        }
        return instance;
    }

    /**
     * 모든 게임 이미지를 로드
     */
    public void loadAllImages() {
        loadImage("BG_TILE", "/assets/Background.png");
        loadImage("SEEKER", "/assets/Man.png");
        loadImage("BOX", "/assets/Box.png");
        loadImage("CHAIR", "/assets/Chair.png");
        loadImage("BARREL", "/assets/CircleBox.png");
        loadImage("CONE", "/assets/Con.png");
        loadImage("TABLE", "/assets/Table.png");
        loadImage("TIRE", "/assets/Tier.png");
    }

    /**
     * 단일 이미지 로드
     */
    private void loadImage(String key, String path) {
        try {
            BufferedImage img = ImageIO.read(getClass().getResourceAsStream(path));
            if (img != null) {
                images.put(key, img);
                System.out.println("✅ IMG " + key + " <- " + path);
            } else {
                System.err.println("❌ IMG " + key + " not found: " + path);
            }
        } catch (IOException e) {
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
     * 오브젝트 타입에 따른 이미지 가져오기
     */
    public BufferedImage getObjectImage(String objectType) {
        if (objectType == null)
            return getImage("BOX");

        return switch (objectType.toUpperCase()) {
            case "BOX" -> getImage("BOX");
            case "CHAIR" -> getImage("CHAIR");
            case "BARREL" -> getImage("BARREL");
            case "CONE" -> getImage("CONE");
            case "TABLE" -> getImage("TABLE");
            case "TIRE" -> getImage("TIRE");
            default -> getImage("BOX");
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
        String[] requiredImages = { "BG_TILE", "SEEKER", "BOX", "CHAIR", "BARREL", "CONE", "TABLE", "TIRE" };
        for (String key : requiredImages) {
            if (!isImageLoaded(key)) {
                return false;
            }
        }
        return true;
    }
}