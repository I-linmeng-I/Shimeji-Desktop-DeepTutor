package com.group_finity.mascot.chat;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.BehaviorExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A floating text input window that appears next to a mascot.
 * <p>
 * Pressing Enter sends the message; Shift+Enter inserts a line break.
 * This class only collects text; it does not send anything anywhere yet.
 *
 * @see ChatSkin
 */
public class InputWindow extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(InputWindow.class);

    /**
     * The skins of the image sets whose chat windows have already been opened.
     */
    private static final Map<String, ChatSkin> skins = new HashMap<>();

    /**
     * The chat windows that are currently open, keyed by the mascot they belong to.
     */
    private static final Map<Mascot, InputWindow> windows = new HashMap<>();

    private final Mascot mascot;
    private final ChatSkin skin;

    /**
     * The behavior that was active when this window was opened.
     */
    private final Behavior releasedBehavior;

    /**
     * The mouse event that opened this window.
     */
    private final MouseEvent releaseEvent;

    private final JTextArea txtMessage = new JTextArea();

    /**
     * The panel that draws the background and lays out the text field and buttons.
     */
    private class InputPanel extends JPanel {
        private InputPanel() {
            setOpaque(false);
            setLayout(new BorderLayout());

            // The text area is deliberately opaque: a transparent text component inside a translucent
            // top-level window makes the IME draw its composition string onto an alpha surface, which
            // shows up as horizontal garbage stripes while typing Chinese.
            txtMessage.setOpaque(true);
            txtMessage.setBackground(UIManager.getColor("Panel.background"));
            txtMessage.setForeground(UIManager.getColor("Panel.foreground"));
            txtMessage.setCaretColor(UIManager.getColor("Panel.foreground"));
            txtMessage.setBorder(BorderFactory.createEmptyBorder());
            txtMessage.setLineWrap(true);
            txtMessage.setWrapStyleWord(true);
            txtMessage.setFont(txtMessage.getFont().deriveFont(
                    (float) Main.getInstance().getSettings().chatFontSize));
            txtMessage.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    resizeToFitText();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    resizeToFitText();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    resizeToFitText();
                }
            });

            // Enter sends the message; Shift+Enter inserts a line break.
            // Shift+Enter has no default binding in a JTextArea, so it has to be bound explicitly.
            txtMessage.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send");
            txtMessage.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                    javax.swing.text.DefaultEditorKit.insertBreakAction);
            txtMessage.getActionMap().put("send", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    send();
                }
            });

            // No scroll pane here: it forces the IME composition string through an extra transparent
            // viewport, which is another source of the garbled rendering. The window grows instead.
            JPanel pnlLeftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            pnlLeftButtons.setOpaque(false);
            JPanel pnlRightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            pnlRightButtons.setOpaque(false);
            for (ChatSkin.Button button : skin.getButtons()) {
                JButton result = createButton(button);
                if (button.leftAligned()) {
                    pnlLeftButtons.add(result);
                } else {
                    pnlRightButtons.add(result);
                }
            }

            // The buttons sit in a row below the text field, so they stay at the bottom when the field grows
            JPanel pnlButtons = new JPanel(new BorderLayout());
            pnlButtons.setOpaque(false);
            pnlButtons.add(pnlLeftButtons, BorderLayout.WEST);
            pnlButtons.add(pnlRightButtons, BorderLayout.EAST);

            Insets padding = skin.getPadding();
            JPanel pnlContent = new JPanel(new BorderLayout(0, 4));
            pnlContent.setOpaque(false);
            pnlContent.setBorder(BorderFactory.createEmptyBorder(padding.top, padding.left,
                    padding.bottom, padding.right));
            pnlContent.add(txtMessage, BorderLayout.CENTER);
            pnlContent.add(pnlButtons, BorderLayout.SOUTH);
            add(pnlContent, BorderLayout.CENTER);
        }
        private JButton createButton(ChatSkin.Button button) {
            JButton result = new JButton(new ImageIcon(button.image()));
            result.setPreferredSize(button.size());
            result.setContentAreaFilled(false);
            result.setBorderPainted(false);
            result.setFocusable(false);
            result.setToolTipText(Main.getInstance().getLanguageBundle().getString(button.name() + "Tooltip"));
            if ("Send".equals(button.name())) {
                result.addActionListener(e -> send());
            }
            return result;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth() - 1;
                int height = getHeight() - 1;
                int arc = skin.getCornerRadius();

                g2.setColor(UIManager.getColor("Panel.background"));
                g2.fillRoundRect(0, 0, width, height, arc, arc);

                g2.setStroke(new BasicStroke(skin.getBorderWidth()));
                g2.setColor(UIManager.getColor("Component.borderColor"));
                g2.drawRoundRect(0, 0, width, height, arc, arc);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private InputWindow(final Mascot mascot, final ChatSkin skin, final Behavior releasedBehavior,
                        final MouseEvent releaseEvent) {
        // An undecorated modeless dialog, unlike a JWindow, can be activated by the window manager,
        // which is required for the text field to receive keyboard input
        super((Frame) null, false);
        this.mascot = mascot;
        this.skin = skin;
        this.releasedBehavior = releasedBehavior;
        this.releaseEvent = releaseEvent;

        setUndecorated(true);
        // The window surface itself must stay opaque. Per-pixel transparency makes the IME draw its
        // composition string onto an alpha surface, which the compositor then samples more than once,
        // producing the garbage stripes seen while typing Chinese. Rounded corners are cut out with
        // setShape instead, which keeps the surface opaque.
        setBackground(UIManager.getColor("Panel.background"));
        setAlwaysOnTop(true);
        // A lightweight tooltip would be clipped by this small window, so show tooltips in their own window
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        add(new InputPanel());
        setSize(Main.getInstance().getSettings().chatWidth, skin.getHeight());
        updateShape();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateShape();
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                txtMessage.requestFocusInWindow();
            }
        });
    }

    /**
     * Cuts the window down to a rounded rectangle, so the opaque surface still looks like a rounded panel.
     */
    private void updateShape() {
        int arc = skin.getCornerRadius();
        setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
    }

    /**
     * Shows the input window next to the specified mascot, or closes it if it is already open.
     * The mascot is frozen until the window is closed.
     *
     * @param mascot the mascot to place the window next to
     * @param releasedBehavior the behavior that was active when the mascot was clicked
     * @param releaseEvent the mouse event that opened this window
     */
    public static void toggleNextTo(final Mascot mascot, final Behavior releasedBehavior, final MouseEvent releaseEvent) {
        InputWindow openWindow = windows.get(mascot);
        if (openWindow != null) {
            openWindow.dispose();
            return;
        }

        ChatSkin skin = skins.computeIfAbsent(mascot.getImageSet(), imageSet -> {
            try {
                return ChatSkin.load(Main.IMAGE_DIRECTORY.resolve(imageSet));
            } catch (IOException | ParserConfigurationException | SAXException e) {
                throw new RuntimeException(e);
            }
        });

        InputWindow window = new InputWindow(mascot, skin, releasedBehavior, releaseEvent);
        window.positionNextTo();
        windows.put(mascot, window);
        // Freeze the mascot where it is, so it does not fall while the user is typing
        mascot.setPaused(true);
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                windows.remove(mascot);
                mascot.setPaused(false);
                if (releasedBehavior != null && releaseEvent != null) {
                    try {
                        releasedBehavior.mouseReleased(releaseEvent);
                    } catch (final BehaviorExecutionException ex) {
                        log.error("Failed to release mascot \"{}\" after closing its chat window", mascot, ex);
                    }
                }
            }
        });
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
        window.txtMessage.requestFocusInWindow();
    }

    /**
     * Checks whether the input window of the specified mascot is currently open.
     *
     * @param mascot the mascot to check
     * @return {@code true} if the chat input window of the specified mascot is open
     */
    public static boolean isOpenFor(final Mascot mascot) {
        return windows.containsKey(mascot);
    }

    /**
     * Closes the input window of the specified mascot, if it is open.
     *
     * @param mascot the mascot whose input window should be closed
     */
    public static void closeFor(final Mascot mascot) {
        InputWindow window = windows.get(mascot);
        if (window != null) {
            window.dispose();
        }
    }

    /**
     * Sends the message in the text field. The text is only logged until the DeepTutor client is wired up.
     */
    private void send() {
        String message = txtMessage.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        log.info("Sending message: {}", message);
        txtMessage.setText("");
    }

    /**
     * Grows or shrinks this window to fit the text field.
     */
    private void resizeToFitText() {
        int lineHeight = txtMessage.getFontMetrics(txtMessage.getFont()).getHeight();
        int height = skin.getHeight() + Math.max(0, txtMessage.getLineCount() - 1) * lineHeight;
        if (height != getHeight()) {
            setSize(getWidth(), height);
            positionNextTo();
        }
    }
    private void positionNextTo() {
        Rectangle mascotBounds = mascot.getBounds();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getScreenDevices()[0].getDefaultConfiguration().getBounds();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            if (bounds.contains(mascotBounds.getCenterX(), mascotBounds.getCenterY())) {
                screen = bounds;
                break;
            }
        }

        // Prefer the space above the mascot, and fall back to below if there is not enough room
        int x = mascotBounds.x + mascotBounds.width / 2 - getWidth() / 2;
        int y = mascotBounds.y - getHeight() - 8;
        if (y < screen.y) {
            y = mascotBounds.y + mascotBounds.height + 8;
        }

        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - getWidth()));
        setLocation(x, y);
    }
}
