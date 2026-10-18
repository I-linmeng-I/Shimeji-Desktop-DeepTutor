package com.group_finity.mascot.chat;

import com.group_finity.mascot.Main;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * The colours shared by the chat input window and the speech bubble.
 * <p>
 * The default look is a dark panel with a white border, matching the reference design. The DeepTutor tab of the
 * settings window can switch to a light look, which simply swaps the background and foreground colours.
 *
 * @see ChatBubble
 */
public final class ChatTheme {
    private static final Color DARK_BACKGROUND = new Color(0x1B1F2B);
    private static final Color DARK_FOREGROUND = Color.WHITE;

    private static final Color LIGHT_BACKGROUND = Color.WHITE;
    private static final Color LIGHT_FOREGROUND = new Color(0x16181D);

    private ChatTheme() {
    }

    /**
     * Checks whether the light look is selected in the settings.
     *
     * @return {@code true} if the light look should be used
     */
    public static boolean isLight() {
        Main main = Main.getInstance();
        return main != null && main.getSettings().chatLightMode;
    }

    /**
     * Gets the fill colour of chat panels.
     *
     * @return the background colour of the chat input window and the speech bubble
     */
    public static Color background() {
        return isLight() ? LIGHT_BACKGROUND : DARK_BACKGROUND;
    }

    /**
     * Gets the colour of the text inside chat panels.
     *
     * @return the text colour of the chat input window and the speech bubble
     */
    public static Color foreground() {
        return isLight() ? LIGHT_FOREGROUND : DARK_FOREGROUND;
    }

    /**
     * Gets the colour of the outline of chat panels.
     *
     * @return the border colour of the chat input window and the speech bubble
     */
    public static Color border() {
        return foreground();
    }

    /**
     * Gets the colour of icons drawn inside chat panels, such as the attach, send and close buttons.
     * <p>
     * Every icon uses the same colour, so a single set of SVG files works for both looks: the icon is recoloured
     * with this value when it is drawn, no matter what colour the SVG file itself uses.
     *
     * @return the icon colour of the chat input window and the speech bubble
     */
    public static Color icon() {
        return foreground();
    }

    /**
     * Gets the fill colour of an attachment chip.
     *
     * @return the background colour of attachment chips
     */
    public static Color chipBackground() {
        return isLight() ? new Color(0xF1F2F5) : new Color(0x262B3A);
    }

    /**
     * Gets the outline colour of an attachment chip.
     *
     * @return the border colour of attachment chips
     */
    public static Color chipBorder() {
        return isLight() ? new Color(0xC9CDD6) : new Color(0x4A5369);
    }

    /**
     * Recolours the opaque pixels of an icon, so the icons shipped with an image set stay visible on both the
     * dark and the light look.
     *
     * @param source the icon to recolour
     * @param color the colour to paint the icon with
     * @return a recoloured copy of the icon
     */
    public static BufferedImage tint(final BufferedImage source, final Color color) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int rgb = color.getRGB() & 0x00FFFFFF;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha != 0) {
                    result.setRGB(x, y, (alpha << 24) | rgb);
                }
            }
        }
        return result;
    }
}
