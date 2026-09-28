package springboot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallpaperStorageTest {

    private static byte[] encode(String format, int w, int h, int type) throws Exception {
        BufferedImage img = new BufferedImage(w, h, type);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, bos), "encoder for " + format);
        return bos.toByteArray();
    }

    private static WallpaperStorage storage() throws Exception {
        WallpaperStorage s = new WallpaperStorage();
        java.lang.reflect.Field f = WallpaperStorage.class.getDeclaredField("thumbWidth");
        f.setAccessible(true);
        f.setInt(s, 640);
        return s;
    }

    @Test
    void decodesPngAndScalesThumbKeepingAspect() throws Exception {
        WallpaperStorage.Decoded d = storage().decode(encode("png", 3840, 2160, BufferedImage.TYPE_INT_ARGB));
        assertEquals("png", d.ext);
        assertEquals(3840, d.width);
        assertEquals(2160, d.height);
        assertEquals(640, d.thumb.getWidth());
        assertEquals(360, d.thumb.getHeight());
        assertTrue(WallpaperStorage.toJpeg(d.thumb, 0.85f).length > 0);
    }

    @Test
    void smallImageIsNotUpscaled() throws Exception {
        WallpaperStorage.Decoded d = storage().decode(encode("jpg", 300, 600, BufferedImage.TYPE_INT_RGB));
        assertEquals("jpg", d.ext);
        assertEquals(300, d.thumb.getWidth());
        assertEquals(600, d.thumb.getHeight());
    }

    @Test
    void decodesGifAndBmp() throws Exception {
        assertEquals("gif", storage().decode(encode("gif", 1000, 500, BufferedImage.TYPE_BYTE_INDEXED)).ext);
        assertEquals("bmp", storage().decode(encode("bmp", 1000, 500, BufferedImage.TYPE_INT_RGB)).ext);
    }

    @Test
    void rejectsNonImage() throws Exception {
        WallpaperStorage s = storage();
        assertThrows(IllegalArgumentException.class, () -> s.decode("<html>not an image</html>".getBytes()));
    }

    @Test
    void webpReaderIsRegistered() {
        assertTrue(ImageIO.getImageReadersByFormatName("webp").hasNext(), "TwelveMonkeys webp reader");
    }

    private static void set(Object o, String field, Object v) throws Exception {
        java.lang.reflect.Field f = WallpaperStorage.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(o, v);
    }

    @Test
    void storeUsesServerSideNamesAndRejectsBadInput(@TempDir Path dir) throws Exception {
        WallpaperStorage s = storage();
        set(s, "storageDir", dir.toString());
        set(s, "urlPrefix", "/uploads/wallpaper");
        set(s, "redirectPrefix", "/prod-api/");
        set(s, "maxFileSize", DataSize.ofMegabytes(20));
        byte[] png = encode("png", 1200, 800, BufferedImage.TYPE_INT_RGB);

        WallpaperStorage.StoredImage st = s.store("g1", new MockMultipartFile("file", "../../evil.png", "image/png", png));
        assertTrue(st.getFilePath().matches("^g1/[0-9a-f]{32}\\.png$"), st.getFilePath());
        assertTrue(st.getThumbPath().matches("^g1/[0-9a-f]{32}_thumb\\.jpg$"), st.getThumbPath());
        assertTrue(Files.isRegularFile(dir.resolve(st.getFilePath())));
        assertTrue(Files.isRegularFile(dir.resolve(st.getThumbPath())));
        assertEquals(1200, st.getWidth());
        assertEquals(800, st.getHeight());
        assertEquals("/uploads/wallpaper/" + st.getFilePath(), s.url(st.getFilePath()));
        assertEquals("/prod-api/uploads/wallpaper/" + st.getFilePath(), s.redirectUrl(st.getFilePath()));

        assertThrows(IllegalArgumentException.class,
                () -> s.store("g1", new MockMultipartFile("file", "a.txt", "text/plain", png)));
        assertThrows(IllegalArgumentException.class,
                () -> s.store("g1", new MockMultipartFile("file", "a.png", "image/png", "<html>".getBytes())));
        assertThrows(IllegalArgumentException.class,
                () -> s.store("../x", new MockMultipartFile("file", "a.png", "image/png", png)));
        set(s, "maxFileSize", DataSize.ofBytes(10));
        assertThrows(IllegalArgumentException.class,
                () -> s.store("g1", new MockMultipartFile("file", "a.png", "image/png", png)));
    }
}
