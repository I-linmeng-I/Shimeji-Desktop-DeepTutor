package com.group_finity.mascot.chat;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Loads the icons used by the chat panel.
 * <p>
 * Both bitmaps and SVGs are accepted, so an image set can ship crisp icons that stay sharp at any size and at
 * any scaling factor. An SVG is rasterised at exactly the size the skin asks for, which is why the skin declares
 * an explicit size for every button.
 *
 * @see ChatSkin
 */
final class ChatIcons {
    private static final Logger log = LoggerFactory.getLogger(ChatIcons.class);

    /**
     * How many times larger than the target size an SVG is rasterised before it is scaled down. Rendering at a
     * higher resolution and then averaging gives noticeably smoother edges than rasterising straight at the
     * target size.
     */
    private static final int SUPERSAMPLE = 4;

    private ChatIcons() {
    }

    /**
     * Loads an icon and scales it to the requested size.
     *
     * @param path the file to load
     * @param width the width to draw the icon at
     * @param height the height to draw the icon at
     * @return the loaded icon, or {@code null} if the file is missing or cannot be read
     */
    static BufferedImage load(final Path path, final int width, final int height) {
        if (!Files.isRegularFile(path)) {
            log.warn("Chat icon \"{}\" does not exist", path);
            return null;
        }

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        try (InputStream input = Files.newInputStream(path)) {
            if (name.endsWith(".svg")) {
                return renderSvg(input, width, height);
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                log.warn("Chat icon \"{}\" is not a readable image", path);
                return null;
            }
            return scale(image, width, height);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to load chat icon \"{}\"", path, e);
            return null;
        }
    }

    /**
     * Rasterises an SVG at the requested size.
     */
    private static BufferedImage renderSvg(final InputStream input, final int width, final int height) {
        SVGDocument document = new SVGLoader().load(input);
        if (document == null) {
            return null;
        }

        int scale = SUPERSAMPLE;
        BufferedImage large = new BufferedImage(Math.max(1, width * scale), Math.max(1, height * scale),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = large.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            document.render(new JLabel(), g2, new ViewBox(width * scale, height * scale));
        } finally {
            g2.dispose();
        }
        return scale(large, width, height);
    }

    /**
     * Scales an image to the requested size with smooth interpolation.
     */
    private static BufferedImage scale(final BufferedImage source, final int width, final int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        BufferedImage result = new BufferedImage(Math.max(1, width), Math.max(1, height),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(source, 0, 0, result.getWidth(), result.getHeight(), null);
        } finally {
            g2.dispose();
        }
        return result;
    }
}
