import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import javax.imageio.ImageIO;

public class IconConverter {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java IconConverter <input.png> <output.ico>");
            return;
        }

        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        BufferedImage original = ImageIO.read(inputFile);

        // Resize to exactly 256x256 for a perfect Windows native ICO
        int size = 256;
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, size, size, null);
        g.dispose();

        // Write the resized PNG to memory
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", pngBytes);
        byte[] imageData = pngBytes.toByteArray();

        // Manually write the 22-byte standard ICO header
        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            // ICONDIR (6 bytes)
            out.write(new byte[] { 0, 0 }); // Reserved
            out.write(new byte[] { 1, 0 }); // Type (1 = ICO)
            out.write(new byte[] { 1, 0 }); // Number of images (1)

            // ICONDIRENTRY (16 bytes)
            out.write(0); // Width (0 means 256)
            out.write(0); // Height (0 means 256)
            out.write(0); // Color palette
            out.write(0); // Reserved
            out.write(new byte[] { 1, 0 }); // Color planes (1)
            out.write(new byte[] { 32, 0 }); // Bits per pixel (32)

            // Image size (4 bytes, little-endian)
            int imageSize = imageData.length;
            out.write(imageSize & 0xFF);
            out.write((imageSize >> 8) & 0xFF);
            out.write((imageSize >> 16) & 0xFF);
            out.write((imageSize >> 24) & 0xFF);

            // Image offset (4 bytes, little-endian, always 22)
            out.write(22 & 0xFF);
            out.write(0);
            out.write(0);
            out.write(0);

            // Write raw PNG bytes
            out.write(imageData);
        }

        System.out.println("Successfully generated 256x256 ICO file!");
    }
}
