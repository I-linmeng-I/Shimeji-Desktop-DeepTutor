package com.group_finity.mascot.chat;

import com.group_finity.mascot.config.Entry;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
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
 * @see InputWindow
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

    private final int width;
    private final int height;
    private final int cornerRadius;
    private final int borderWidth;
    private final Insets padding;
    private final List<Button> buttons = new ArrayList<>();

    private ChatSkin(final Entry input, final Path imageSetDirectory) throws IOException {
        width = Integer.parseInt(input.getAttribute("Width"));
        height = Integer.parseInt(input.getAttribute("Height"));
        cornerRadius = Integer.parseInt(input.getAttribute("CornerRadius"));
        borderWidth = Integer.parseInt(input.getAttribute("BorderWidth"));

        String[] paddingValues = input.getAttribute("Padding").split(",");
        padding = new Insets(Integer.parseInt(paddingValues[0]), Integer.parseInt(paddingValues[1]),
                Integer.parseInt(paddingValues[2]), Integer.parseInt(paddingValues[3]));

        for (final Entry button : input.selectChildren("Button")) {
            String[] sizeValues = button.getAttribute("Size").split(",");

            Path imagePath = imageSetDirectory.resolve(button.getAttribute("Image").substring(1));
            final BufferedImage image;
            try (InputStream inputStream = Files.newInputStream(imagePath)) {
                image = ImageIO.read(inputStream);
            }

            buttons.add(new Button(button.getAttribute("Name"), image,
                    new Dimension(Integer.parseInt(sizeValues[0]), Integer.parseInt(sizeValues[1])),
                    "Left".equalsIgnoreCase(button.getAttribute("Align"))));
        }
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
        return new ChatSkin(root.selectChildren("Input").getFirst(), imageSetDirectory);
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

    public List<Button> getButtons() {
        return buttons;
    }
}
