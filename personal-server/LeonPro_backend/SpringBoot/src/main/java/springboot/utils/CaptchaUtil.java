package springboot.utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;
public class CaptchaUtil {
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    // 生成验证码文本
    public static String generateCaptchaText(int length) {
        StringBuilder captchaText = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            captchaText.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return captchaText.toString();
    }
    // 生成验证码图片
    public static BufferedImage generateCaptchaImage(String captchaText) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(Color.BLACK);
//        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.setFont(new Font("Liberation Sans", Font.BOLD, 24));
        g.drawString(captchaText, 10, 30);
        g.dispose();
        return image;
    }
    // 将图片转换为 Base64
    public static String convertImageToBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("图片转换失败", e);
        }
    }
}
