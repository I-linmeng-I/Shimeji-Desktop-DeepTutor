package com.group_finity.mascot.chat;

import com.group_finity.mascot.Main;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The row of attachments above the text field of a chat panel.
 * <p>
 * Every chip is drawn by a single component, so the rounded outline, the name, the right-hand slot and the remove
 * mark can never be clipped by a layout manager. A chip shows the file name (shortened with an ellipsis when it
 * does not fit), then either the thumbnail of an image or the upper-case extension, then a cross that removes it.
 *
 * @see ChatBubble
 */
final class AttachmentStrip extends JPanel {
    /**
     * A file or pasted image that will be sent with the message.
     *
     * @param name the file name without its extension
     * @param extension the extension in lower case, or an empty string if the name has none
     * @param thumbnail the small preview of an image attachment, or {@code null} for other files
     */
    record Attachment(String name, String extension, Image thumbnail) {
        /**
         * Gets the attachment name including its extension, for logs and tooltips.
         */
        String fullName() {
            return extension.isEmpty() ? name : name + "." + extension;
        }
    }

    private static final int CHIP_HEIGHT = 24;
    private static final int CHIP_ICON_SIZE = 14;
    private static final int CHIP_PADDING = 7;
    private static final int CHIP_GAP = 6;
    private static final int CHIP_REMOVE_WIDTH = 12;
    private static final int CHIP_ARC = 8;
    private static final int CHIP_MIN_WIDTH = 64;
    private static final int CHIP_ROW_GAP = 4;

    /**
     * The number of chips that must fit on a single row, at least.
     */
    private static final int CHIPS_PER_ROW = 3;

    private final List<Attachment> attachments = new ArrayList<>();
    private final Font font;

    /**
     * The width this strip may use, which decides how wide a single chip can be.
     */
    private int availableWidth = 300;

    /**
     * Called after the attachments changed, so the owner can resize the panel to fit.
     */
    private Runnable onChange = () -> {
    };

    private int pastedImageCount;

    AttachmentStrip(final Font font) {
        super(new FlowLayout(FlowLayout.LEFT, CHIP_GAP, 0));
        this.font = font;
        setOpaque(false);
        setVisible(false);
    }

    void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /**
     * Tells the strip how much horizontal room it has, which changes the width of a single chip.
     */
    void setAvailableWidth(final int availableWidth) {
        this.availableWidth = Math.max(90, availableWidth);
    }

    List<Attachment> attachments() {
        return attachments;
    }

    boolean isEmpty() {
        return attachments.isEmpty();
    }

    /**
     * Adds the files that are readable as regular files.
     *
     * @param files the files to attach
     * @return {@code true} if at least one file was added
     */
    boolean addFiles(final List<File> files) {
        boolean changed = false;
        for (File file : files) {
            if (file.isFile()) {
                attachments.add(toAttachment(file));
                changed = true;
            }
        }
        if (changed) {
            rebuild();
        }
        return changed;
    }

    /**
     * Attaches the files or the image on the clipboard.
     *
     * @return {@code true} if the clipboard held something that could be attached
     */
    boolean addFromClipboard() {
        Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                List<File> files = new ArrayList<>();
                for (Object item : (Iterable<?>) clipboard.getData(DataFlavor.javaFileListFlavor)) {
                    if (item instanceof File file) {
                        files.add(file);
                    }
                }
                if (addFiles(files)) {
                    return true;
                }
            }
            if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                Object data = clipboard.getData(DataFlavor.imageFlavor);
                if (data instanceof Image image) {
                    addPastedImage(image);
                    return true;
                }
            }
        } catch (UnsupportedFlavorException | IOException | IllegalStateException e) {
            // Nothing attachable, so the caller falls back to a normal text paste
        }
        return false;
    }

    /**
     * Gets the height the strip needs, including the extra rows that its chips wrap onto.
     */
    int preferredHeight() {
        if (attachments.isEmpty()) {
            return 0;
        }
        int rows = 1;
        int used = 0;
        for (Attachment attachment : attachments) {
            int width = chipWidth(attachment);
            if (used > 0 && used + width > availableWidth) {
                rows++;
                used = 0;
            }
            used += width + CHIP_GAP;
        }
        return rows * CHIP_HEIGHT + (rows - 1) * CHIP_ROW_GAP;
    }

    private void addPastedImage(final Image image) {
        String name = Main.getInstance().getLanguageBundle().getString("PastedImage") + " " + (++pastedImageCount);
        attachments.add(new Attachment(name, "", thumbnail(image)));
        rebuild();
    }

    private void rebuild() {
        removeAll();
        for (Attachment attachment : attachments) {
            add(new ChipView(attachment));
        }
        setVisible(!attachments.isEmpty());
        revalidate();
        repaint();
        onChange.run();
    }

    /**
     * Gets the widest a chip may be so that at least {@value #CHIPS_PER_ROW} of them fit on one row.
     * FlowLayout inserts a gap at both ends of a row, so one extra gap is reserved here as well.
     */
    private int slotWidth() {
        return Math.max(CHIP_MIN_WIDTH,
                (availableWidth - CHIP_GAP * CHIPS_PER_ROW) / CHIPS_PER_ROW);
    }

    /**
     * Gets the width a chip needs for its name, its right-hand slot and its remove mark.
     */
    private int chipWidth(final Attachment attachment) {
        FontMetrics metrics = getFontMetrics(font);
        int slot = attachment.thumbnail() != null ? CHIP_ICON_SIZE
                : attachment.extension().isEmpty() ? 0
                : metrics.stringWidth(attachment.extension().toUpperCase(Locale.ROOT)) + 4;
        int needed = CHIP_PADDING + metrics.stringWidth(attachment.name()) + 4 + slot + 4
                + CHIP_REMOVE_WIDTH + CHIP_PADDING;
        return Math.min(slotWidth(), Math.max(CHIP_MIN_WIDTH, needed));
    }

    /**
     * Shortens a string with an ellipsis so it fits in the specified width.
     */
    private static String ellipsis(final String text, final FontMetrics metrics, final int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (metrics.stringWidth(text.substring(0, middle) + "\u2026") <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, low) + "\u2026";
    }

    private static Attachment toAttachment(final File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return new Attachment(base, extension, isImage(extension) ? readThumbnail(file) : null);
    }

    private static boolean isImage(final String extension) {
        return switch (extension) {
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> true;
            default -> false;
        };
    }

    /**
     * Reads a file into a small square thumbnail, or returns {@code null} if it cannot be decoded.
     */
    private static Image readThumbnail(final File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            return image == null ? null : thumbnail(image);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Scales an image down to a square thumbnail, preserving its aspect ratio.
     */
    private static Image thumbnail(final Image source) {
        BufferedImage result = new BufferedImage(CHIP_ICON_SIZE, CHIP_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = source.getWidth(null);
            int height = source.getHeight(null);
            if (width <= 0 || height <= 0) {
                return result;
            }
            double scale = Math.min((double) CHIP_ICON_SIZE / width, (double) CHIP_ICON_SIZE / height);
            int scaledWidth = Math.max(1, (int) Math.round(width * scale));
            int scaledHeight = Math.max(1, (int) Math.round(height * scale));
            g2.drawImage(source, (CHIP_ICON_SIZE - scaledWidth) / 2, (CHIP_ICON_SIZE - scaledHeight) / 2,
                    scaledWidth, scaledHeight, null);
        } finally {
            g2.dispose();
        }
        return result;
    }

    /**
     * A single chip. Everything is painted here, so the look is identical at any font size and theme.
     */
    private final class ChipView extends JComponent {
        private final Attachment attachment;

        private ChipView(final Attachment attachment) {
            this.attachment = attachment;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(attachment.fullName());
            setPreferredSize(new Dimension(chipWidth(attachment), CHIP_HEIGHT));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent e) {
                    if (e.getX() >= getWidth() - CHIP_PADDING - CHIP_REMOVE_WIDTH) {
                        attachments.remove(attachment);
                        rebuild();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(final Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Inset the shape by half a pixel so the one-pixel outline is drawn fully inside the chip
                RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f,
                        getWidth() - 1f, getHeight() - 1f, CHIP_ARC, CHIP_ARC);
                g2.setColor(ChatTheme.chipBackground());
                g2.fill(shape);
                g2.setColor(ChatTheme.chipBorder());
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);

                g2.setFont(font);
                FontMetrics metrics = g2.getFontMetrics();
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();

                int right = getWidth() - CHIP_PADDING;

                // The remove mark sits at the far right, mirroring the close button of the panel
                int removeLeft = right - CHIP_REMOVE_WIDTH;
                String cross = "\u00D7";
                g2.setColor(ChatTheme.icon());
                g2.drawString(cross, removeLeft + (CHIP_REMOVE_WIDTH - metrics.stringWidth(cross)) / 2,
                        baseline);
                right = removeLeft - 4;

                // The right slot holds either the thumbnail of an image or the upper-case extension
                if (attachment.thumbnail() != null) {
                    right -= CHIP_ICON_SIZE;
                    g2.drawImage(attachment.thumbnail(), right, (getHeight() - CHIP_ICON_SIZE) / 2, null);
                    right -= 4;
                } else if (!attachment.extension().isEmpty()) {
                    String extension = attachment.extension().toUpperCase(Locale.ROOT);
                    int extensionWidth = metrics.stringWidth(extension);
                    g2.setColor(ChatTheme.foreground());
                    g2.drawString(extension, right - extensionWidth, baseline);
                    right -= extensionWidth + 4;
                }

                // The name takes whatever is left, shortened if it does not fit
                int nameLeft = CHIP_PADDING;
                int nameWidth = Math.max(0, right - nameLeft);
                g2.setColor(ChatTheme.foreground());
                Shape oldClip = g2.getClip();
                g2.clipRect(nameLeft, 0, nameWidth, getHeight());
                g2.drawString(ellipsis(attachment.name(), metrics, nameWidth), nameLeft, baseline);
                g2.setClip(oldClip);
            } finally {
                g2.dispose();
            }
        }
    }
}
