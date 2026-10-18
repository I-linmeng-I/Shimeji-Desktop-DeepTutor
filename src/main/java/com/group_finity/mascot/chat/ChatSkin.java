package com.group_finity.mascot.chat;

import com.group_finity.mascot.config.Entry;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The geometry and icons of the chat input window, read from {@code chat/chat.xml} inside an image set's folder.
 * Colours are not part of the skin; they are taken from the current look and feel at paint time
 * so the window follows the theme.
 *
 * @see ChatBubble
 */
public final class ChatSkin {
    /**
     * A button drawn on the input window, such as the send button.
     *
     * @param name the name of the button, as written in the skin file
     * @param image the icon of the button
     * @param size the size at which the icon is drawn
     * @param leftAligned whether the button is drawn on the left side of the text field instead of the right side
     */
    public record Button(String name, BufferedImage image, Dimension size, boolean leftAligned) {
    }

    /**
     * The geometry of a speech bubble.
     *
     * @param maxWidth the maximum width of the bubble, including padding
     * @param cornerRadius the radius of the rounded corners
     * @param borderWidth the width of the outline
     * @param padding the space between the outline and the text
     * @param tailWidth the width of the tail that points at the mascot
     * @param tailHeight the height of the tail that points at the mascot
     */
    public record Bubble(int maxWidth, int cornerRadius, int borderWidth, Insets padding,
                         int tailWidth, int tailHeight) {
        /**
         * The geometry used when an image set does not define a {@code Bubble} entry.
         */
        public static final Bubble DEFAULT = new Bubble(340, 18, 2, new Insets(10, 16, 10, 16), 22, 10);
    }

    private final int width;
    private final int height;
    private final int cornerRadius;
    private final int borderWidth;
    private final Insets padding;
    private final Bubble bubble;
    private final List<Button> buttons = new ArrayList<>();

    /**
     * The skin used when an image set ships no usable {@code chat.xml}, so a broken skin can never take the chat
     * panel down with it.
     */
    private static final ChatSkin FALLBACK = new ChatSkin();

    /**
     * Creates the fallback skin, which has no buttons and uses the default geometry.
     */
    private ChatSkin() {
        width = 360;
        height = 60;
        cornerRadius = 14;
        borderWidth = 2;
        padding = new Insets(8, 10, 8, 10);
        bubble = Bubble.DEFAULT;
    }

    private ChatSkin(final Entry input, final Entry bubble, final Path imageSetDirectory) throws IOException {
        width = Integer.parseInt(input.getAttribute("Width"));
        height = Integer.parseInt(input.getAttribute("Height"));
        cornerRadius = Integer.parseInt(input.getAttribute("CornerRadius"));
        borderWidth = Integer.parseInt(input.getAttribute("BorderWidth"));

        String[] paddingValues = input.getAttribute("Padding").split(",");
        padding = new Insets(Integer.parseInt(paddingValues[0]), Integer.parseInt(paddingValues[1]),
                Integer.parseInt(paddingValues[2]), Integer.parseInt(paddingValues[3]));

        this.bubble = bubble == null ? Bubble.DEFAULT : new Bubble(
                Integer.parseInt(bubble.getAttribute("MaxWidth")),
                Integer.parseInt(bubble.getAttribute("CornerRadius")),
                Integer.parseInt(bubble.getAttribute("BorderWidth")),
                parseInsets(bubble.getAttribute("Padding")),
                Integer.parseInt(bubble.getAttribute("TailWidth")),
                Integer.parseInt(bubble.getAttribute("TailHeight")));

        for (final Entry button : input.selectChildren("Button")) {
            String[] sizeValues = button.getAttribute("Size").split(",");
            Dimension size = new Dimension(Integer.parseInt(sizeValues[0]), Integer.parseInt(sizeValues[1]));

            Path imagePath = imageSetDirectory.resolve(button.getAttribute("Image").substring(1));
            buttons.add(new Button(button.getAttribute("Name"), ChatIcons.load(imagePath, size.width, size.height),
                    size, "Left".equalsIgnoreCase(button.getAttribute("Align"))));
        }
    }

    private static Insets parseInsets(final String value) {
        String[] values = value.split(",");
        return new Insets(Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                Integer.parseInt(values[2]), Integer.parseInt(values[3]));
    }

    /**
     * Loads the skin of the specified image set.
     *
     * @param imageSetDirectory the folder of the image set whose skin should be loaded
     * @return the loaded skin
     * @throws IOException if an error occurs when reading the skin file or its images
     * @throws ParserConfigurationException if an error occurs when creating the XML parser
     * @throws SAXException if an error occurs when parsing the skin file
     */
    public static ChatSkin load(final Path imageSetDirectory) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setIgnoringComments(true);
        DocumentBuilder builder = builderFactory.newDocumentBuilder();

        final Entry root;
        try (InputStream input = Files.newInputStream(imageSetDirectory.resolve("chat/chat.xml"))) {
            root = new Entry(builder.parse(input).getDocumentElement());
        }
        List<Entry> bubbles = root.selectChildren("Bubble");
        return new ChatSkin(root.selectChildren("Input").getFirst(), bubbles.isEmpty() ? null : bubbles.getFirst(),
                imageSetDirectory);
    }

    /**
     * Gets the skin that is used when an image set has no usable skin file.
     *
     * @return the fallback skin
     */
    public static ChatSkin fallback() {
        return FALLBACK;
    }

    /**
     * The skins of the image sets that have already been loaded.
     */
    private static final java.util.Map<String, ChatSkin> CACHE = new java.util.HashMap<>();

    /**
     * Loads, and then reuses, the skin of an image set. A broken skin file never throws; the fallback skin is used
     * instead so the chat panel keeps working.
     *
     * @param imageSet the folder name of the image set
     * @return the skin of the image set, or {@link #fallback()} if it could not be loaded
     */
    public static ChatSkin forImageSet(final String imageSet) {
        return CACHE.computeIfAbsent(imageSet, key -> {
            try {
                return load(com.group_finity.mascot.Main.IMAGE_DIRECTORY.resolve(key));
            } catch (IOException | ParserConfigurationException | SAXException | RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(ChatSkin.class)
                        .warn("Failed to load the chat skin of image set \"{}\", using the default skin", key, ex);
                return FALLBACK;
            }
        });
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCornerRadius() {
        return cornerRadius;
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    public Insets getPadding() {
        return padding;
    }

    /**
     * Gets the geometry of the speech bubble of this image set.
     *
     * @return the bubble geometry, or {@link Bubble#DEFAULT} if the image set does not define one
     */
    public Bubble getBubble() {
        return bubble;
    }

    /**
     * Finds a button by name.
     *
     * @param name the name of the button, as written in the skin file
     * @return the button with the specified name, or {@code null} if the skin has no such button
     */
    public Button findButton(final String name) {
        for (Button button : buttons) {
            if (button.name().equals(name)) {
                return button;
            }
        }
        return null;
    }

    public List<Button> getButtons() {
        return buttons;
    }
}
