package com.group_finity.mascot.chat;

import com.group_finity.mascot.Main;
import com.group_finity.mascot.Mascot;
import com.group_finity.mascot.behavior.Behavior;
import com.group_finity.mascot.behavior.BehaviorExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Area;
import java.io.File;
import java.util.List;

/**
 * A floating panel next to a mascot. The very same panel is used for typing and for showing a reply:
 * <p>
 * <b>Input state</b> — an editable text field with the attach and send buttons. Pressing Enter turns it into a reply.
 * <br>
 * <b>Output state</b> — a read-only bubble that shows what was said, follows the mascot around, and never takes the
 * keyboard focus.
 * <p>
 * Both states share the same look: a rounded panel with an outline and a tail pointing at the mascot.
 * A mascot shows at most {@link BubbleStack#MAX_BUBBLES} bubbles at once; summoning a new input box closes the
 * oldest one.
 *
 * @see ChatSkin
 * @see ChatTheme
 */
public class ChatBubble extends JDialog {
    private static final Logger log = LoggerFactory.getLogger(ChatBubble.class);

    /**
     * How often the bubbles re-check the position of their mascot, in milliseconds.
     * This is deliberately far below the interval of the mascot's own ticks, so a bubble never trails behind.
     */
    private static final int FOLLOW_INTERVAL_MS = 16;

    /**
     * The fallback size of the close button, in pixels, used when the skin has no close icon.
     */
    private static final int CLOSE_SIZE = 26;

    /**
     * The gap between the outline and the close button, and above the attachment chips, in pixels.
     */
    private static final int HEADER_TOP_INSET = 8;

    /**
     * The gap between the header row and the text field, in pixels.
     */
    private static final int HEADER_BOTTOM_GAP = 6;

    /**
     * The space reserved above a reply for the close button, in pixels.
     */
    private static final int TEXT_TOP_EXTRA = 16;

    private final Mascot mascot;
    private final ChatSkin skin;

    /**
     * Whether this bubble is the editable input box rather than a read-only reply.
     */
    private final boolean editing;

    /**
     * The behavior that was active when the input box was opened, resumed when it is closed or sent.
     */
    private Behavior releasedBehavior;

    /**
     * The mouse event that opened the input box.
     */
    private MouseEvent releaseEvent;

    /**
     * Whether the mascot has already been released, so it is only released once.
     */
    private boolean released;

    private final JTextArea txtMessage = new JTextArea();
    private final JButton btnClose = new JButton();
    private final JPanel pnlContent = new JPanel(new BorderLayout(0, 4));
    private JPanel pnlButtons;
    private final Timer followTimer;

    /**
     * The attachment chips, shown above the text field next to the close button.
     */
    private final AttachmentStrip chips;

    /**
     * The side length of the close button, taken from the skin so a larger icon is not clipped.
     */
    private final int closeSize;

    /**
     * The position applied last, so an idle mascot does not cause a window move on every follow tick.
     */
    private int lastPlacedX = Integer.MIN_VALUE;
    private int lastPlacedY = Integer.MIN_VALUE;

    /**
     * The horizontal centre of the tail, relative to the left edge of the window.
     */
    private int tailCenterX;

    /**
     * Whether the tail points up, i.e. the bubble sits below the mascot.
     */
    private boolean tailUp;

    private int appliedTailCenterX = -1;
    private boolean appliedTailUp;
    private int appliedWidth = -1;
    private int appliedHeight = -1;

    /**
     * The panel that draws the rounded panel and the tail, and lays out the text field and buttons.
     */
    private class BubblePanel extends JPanel {
        private BubblePanel() {
            setOpaque(false);
            setLayout(null);
            add(pnlContent);
            add(chips);
            add(btnClose);
        }

        @Override
        public void doLayout() {
            int tailHeight = skin.getBubble().tailHeight();
            int bodyTop = tailUp ? tailHeight : 0;
            int bodyHeight = Math.max(1, getHeight() - tailHeight);
            pnlContent.setBounds(0, bodyTop, getWidth(), bodyHeight);

            Insets padding = skin.getPadding();
            int edge = 2 + skin.getBorderWidth();
            int headerTop = bodyTop + edge + HEADER_TOP_INSET;

            // The attachment chips share the header row with the close button, and stop short of it.
            // The width is set first because it decides how many chips fit on one row.
            int chipsWidth = Math.max(0, getWidth() - 2 * edge - padding.left - padding.right
                    - closeSize - 6);
            chips.setAvailableWidth(chipsWidth);
            chips.setBounds(edge + padding.left, headerTop, chipsWidth, chips.preferredHeight());

            btnClose.setBounds(getWidth() - edge - padding.right - closeSize, headerTop, closeSize, closeSize);
        }

        @Override
        protected void paintComponent(final Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // The body and the tail are merged into one area first, so the outline runs all the way around
                // the tail as well, with no seam across its base.
                Area panel = BubbleShape.panel(getWidth(), getHeight(), skin.getBubble(), tailCenterX, tailUp,
                        skin.getBorderWidth() / 2f);

                g2.setColor(ChatTheme.background());
                g2.fill(panel);

                g2.setColor(ChatTheme.border());
                g2.setStroke(new BasicStroke(skin.getBorderWidth()));
                g2.draw(panel);
            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    private ChatBubble(final Mascot mascot, final ChatSkin skin, final boolean editing,
                       final Behavior releasedBehavior, final MouseEvent releaseEvent) {
        // An undecorated modeless dialog, unlike a JWindow, can be activated by the window manager,
        // which is required for the text field to receive keyboard input
        super((Frame) null, false);
        this.mascot = mascot;
        this.skin = skin;
        this.editing = editing;
        this.releasedBehavior = releasedBehavior;
        this.releaseEvent = releaseEvent;

        setUndecorated(true);
        // The window surface itself must stay opaque. Per-pixel transparency makes the IME draw its
        // composition string onto an alpha surface, which the compositor then samples more than once,
        // producing the garbage stripes seen while typing Chinese. Everything outside the panel is cut
        // away with setShape instead, which keeps the surface opaque.
        setBackground(ChatTheme.background());
        setAlwaysOnTop(true);
        if (!editing) {
            // A reply never needs the keyboard, so it should never take the focus away from the user
            setFocusableWindowState(false);
        }
        // A lightweight tooltip would be clipped by this small window, so show tooltips in their own window
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);

        txtMessage.setOpaque(true);
        txtMessage.setBackground(ChatTheme.background());
        txtMessage.setForeground(ChatTheme.foreground());
        txtMessage.setCaretColor(ChatTheme.foreground());
        txtMessage.setBorder(BorderFactory.createEmptyBorder());
        txtMessage.setLineWrap(true);
        txtMessage.setWrapStyleWord(true);
        txtMessage.setFont(txtMessage.getFont().deriveFont(
                editing ? Font.PLAIN : Font.BOLD,
                (float) Main.getInstance().getSettings().chatFontSize + (editing ? 0f : 2f)));
        Font chipFont = (getFont() != null ? getFont() : new JLabel().getFont())
                .deriveFont(Math.max(10f, (float) Main.getInstance().getSettings().chatFontSize - 3f));
        chips = new AttachmentStrip(chipFont);
        chips.setOnChange(this::onAttachmentsChanged);

        if (editing) {
            setupInput();
        } else {
            txtMessage.setEditable(false);
            txtMessage.setFocusable(false);
        }

        pnlContent.setOpaque(false);
        pnlContent.add(txtMessage, BorderLayout.CENTER);
        if (pnlButtons != null) {
            pnlContent.add(pnlButtons, BorderLayout.SOUTH);
        }
        updateContentBorder();

        ChatSkin.Button closeButton = skin.findButton("Close");
        closeSize = closeButton == null ? CLOSE_SIZE
                : Math.max(16, Math.max(closeButton.size().width, closeButton.size().height));
        if (closeButton != null && closeButton.image() != null) {
            btnClose.setIcon(new ImageIcon(ChatTheme.tint(closeButton.image(), ChatTheme.icon())));
        } else {
            btnClose.setText("\u00D7");
            btnClose.setFont(btnClose.getFont().deriveFont(Font.PLAIN, 17f));
        }
        btnClose.setForeground(ChatTheme.icon());
        btnClose.setContentAreaFilled(false);
        btnClose.setBorderPainted(false);
        btnClose.setFocusable(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.setToolTipText(Main.getInstance().getLanguageBundle().getString("Close"));
        btnClose.addActionListener(e -> dispose());

        setContentPane(new BubblePanel());

        followTimer = new Timer(FOLLOW_INTERVAL_MS, e -> {
            if (mascot.getManager() == null) {
                dispose();
                return;
            }
            BubbleStack.repositionAll(mascot);
        });
        followTimer.setRepeats(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                followTimer.stop();
                BubbleStack.remove(mascot, ChatBubble.this);
                BubbleStack.clearInput(mascot, ChatBubble.this);
                if (editing) {
                    releaseMascot();
                }
                BubbleStack.repositionAll(mascot);
            }
        });

        if (editing) {
            resizeToFitText();
        }
    }

    /**
     * Wires up the editable text field of an input box: Enter sends, Shift+Enter inserts a line break.
     */
    private void setupInput() {
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

        // Shift+Enter has no default binding in a JTextArea, so it has to be bound explicitly
        txtMessage.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send");
        txtMessage.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                javax.swing.text.DefaultEditorKit.insertBreakAction);
        txtMessage.getActionMap().put("send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                send();
            }
        });

        // Ctrl+V first looks for files or an image on the clipboard, and only falls back to pasting text
        txtMessage.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "attachPaste");
        txtMessage.getActionMap().put("attachPaste", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteFromClipboard();
            }
        });

        // No scroll pane here: it forces the IME composition string through an extra transparent viewport,
        // which is another source of the garbled rendering. The window grows instead.
        JPanel pnlLeftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pnlLeftButtons.setOpaque(false);
        JPanel pnlRightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pnlRightButtons.setOpaque(false);
        for (ChatSkin.Button button : skin.getButtons()) {
            // The close button is laid out separately in the top-right corner, so it has no place in these rows
            if ("Close".equals(button.name())) {
                continue;
            }
            JButton result = createButton(button);
            if (button.leftAligned()) {
                pnlLeftButtons.add(result);
            } else {
                pnlRightButtons.add(result);
            }
        }

        pnlButtons = new JPanel(new BorderLayout());
        pnlButtons.setOpaque(false);
        pnlButtons.add(pnlLeftButtons, BorderLayout.WEST);
        pnlButtons.add(pnlRightButtons, BorderLayout.EAST);
    }

    private JButton createButton(final ChatSkin.Button button) {
        JButton result = button.image() == null ? new JButton(button.name())
                : new JButton(new ImageIcon(ChatTheme.tint(button.image(), ChatTheme.icon())));
        result.setPreferredSize(button.size());
        result.setContentAreaFilled(false);
        result.setBorderPainted(false);
        result.setFocusable(false);
        result.setToolTipText(Main.getInstance().getLanguageBundle().getString(button.name() + "Tooltip"));
        if ("Send".equals(button.name())) {
            result.addActionListener(e -> send());
        } else if ("Attach".equals(button.name())) {
            result.addActionListener(e -> chooseFiles());
        }
        return result;
    }

    /**
     * Opens the system file dialog and adds every selected file as an attachment.
     */
    private void chooseFiles() {
        List<File> files = FileDialogs.chooseFiles(this,
                Main.getInstance().getLanguageBundle().getString("AttachTooltip"));
        chips.addFiles(files);
        txtMessage.requestFocusInWindow();
    }

    /**
     * Attaches the files or image on the clipboard, falling back to a normal text paste.
     */
    private void pasteFromClipboard() {
        if (chips.addFromClipboard()) {
            return;
        }
        Action paste = txtMessage.getActionMap().get(javax.swing.text.DefaultEditorKit.pasteAction);
        if (paste != null) {
            paste.actionPerformed(new ActionEvent(txtMessage, ActionEvent.ACTION_PERFORMED, "paste"));
        }
    }

    /**
     * Reflows the panel after an attachment was added or removed.
     */
    private void onAttachmentsChanged() {
        updateContentBorder();
        if (getContentPane() instanceof BubblePanel panel) {
            panel.doLayout();
        }
        resizeToFitText();
    }

    /**
     * Sets the padding of the content, which also keeps the text clear of the outline, the close button and the
     * attachment chips.
     */
    private void updateContentBorder() {
        Insets padding = skin.getPadding();
        int edge = 2 + skin.getBorderWidth();
        pnlContent.setBorder(BorderFactory.createEmptyBorder(edge + padding.top + headerHeight(),
                edge + padding.left, edge + padding.bottom, edge + padding.right));
    }

    /**
     * Gets the height of the header row, which holds the close button and the attachment chips.
     */
    private int headerHeight() {
        if (!editing) {
            return TEXT_TOP_EXTRA;
        }
        return HEADER_TOP_INSET + Math.max(closeSize, chips.preferredHeight()) + HEADER_BOTTOM_GAP;
    }

    /**
     * Shows the reply in this bubble, then shrinks it to fit.
     */
    private void setMessage(final String message) {
        txtMessage.setText(message);
        txtMessage.setCaretPosition(0);
        sizeToFitMessage(message);
    }

    /**
     * Opens an input box next to the specified mascot, closing the oldest bubble if there are too many.
     * The mascot is frozen until the box is closed or the message is sent.
     *
     * @param mascot the mascot to place the box next to
     * @param releasedBehavior the behavior that was active when the mascot was clicked
     * @param releaseEvent the mouse event that opened the box
     */
    public static void toggleInputNextTo(final Mascot mascot, final Behavior releasedBehavior,
                                         final MouseEvent releaseEvent) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> toggleInputNextTo(mascot, releasedBehavior, releaseEvent));
            return;
        }
        if (isInputOpenFor(mascot)) {
            closeInputFor(mascot);
            return;
        }

        ChatBubble bubble = new ChatBubble(mascot, ChatSkin.forImageSet(mascot.getImageSet()), true,
                releasedBehavior, releaseEvent);
        BubbleStack.add(mascot, bubble);
        BubbleStack.setInput(mascot, bubble);
        mascot.setPaused(true);

        BubbleStack.repositionAll(mascot);
        bubble.setVisible(true);
        bubble.toFront();
        bubble.requestFocus();
        bubble.txtMessage.requestFocusInWindow();
    }

    /**
     * Checks whether the input box of the specified mascot is currently open.
     *
     * @param mascot the mascot to check
     * @return {@code true} if the input box of the specified mascot is open
     */
    public static boolean isInputOpenFor(final Mascot mascot) {
        return BubbleStack.hasInput(mascot);
    }

    /**
     * Closes the input box of the specified mascot, if it is open.
     *
     * @param mascot the mascot whose input box should be closed
     */
    public static void closeInputFor(final Mascot mascot) {
        ChatBubble bubble = BubbleStack.inputOf(mascot);
        if (bubble != null) {
            bubble.dispose();
        }
    }

    /**
     * Sends the message in the text field.
     * <p>
     * The input box becomes a reply bubble that echoes the message, and the mascot is released so it can move
     * around again. The DeepTutor client is not wired up yet; replace the echo with the real streamed reply later.
     */
    private void send() {
        String message = txtMessage.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        if (!chips.isEmpty()) {
            log.info("Attachments: {}", chips.attachments().stream()
                    .map(AttachmentStrip.Attachment::fullName)
                    .toList());
        }
        log.info("Sending message: {}", message);
        txtMessage.setText("");

        // The mascot must not stay frozen while it is showing the reply
        releaseMascot();

        ChatBubble reply = new ChatBubble(mascot, skin, false, null, null);
        reply.setMessage(message);

        BubbleStack.replace(mascot, this, reply);
        BubbleStack.clearInput(mascot, this);

        // Disposing the dialog is what gives the keyboard focus back to whatever the user was doing,
        // which a non-focusable window can not do on its own
        dispose();

        BubbleStack.repositionAll(mascot);
        reply.setVisible(true);
        reply.toFront();
        reply.followTimer.start();
    }

    /**
     * Unfreezes the mascot and resumes the behavior that was interrupted when the input box was opened.
     */
    private void releaseMascot() {
        if (released) {
            return;
        }
        released = true;
        mascot.setPaused(false);
        if (releasedBehavior != null && releaseEvent != null) {
            try {
                releasedBehavior.mouseReleased(releaseEvent);
            } catch (final BehaviorExecutionException e) {
                log.error("Failed to release mascot \"{}\" after its chat bubble was closed", mascot, e);
            }
        }
        releasedBehavior = null;
        releaseEvent = null;
    }

    /**
     * Grows or shrinks an input box to fit its text field.
     */
    private void resizeToFitText() {
        if (!editing) {
            return;
        }
        int lineHeight = txtMessage.getFontMetrics(txtMessage.getFont()).getHeight();
        int extraLines = Math.max(0, txtMessage.getLineCount() - 1);
        int height = skin.getHeight() + extraHeight() + headerHeight() + extraLines * lineHeight;
        if (height != getHeight() || getWidth() != Main.getInstance().getSettings().chatWidth) {
            setSize(Main.getInstance().getSettings().chatWidth, height);
            BubbleStack.repositionAll(mascot);
        }
    }

    /**
     * Gets the height that a panel needs on top of the skin height for the tail and the outline.
     */
    private int extraHeight() {
        return 2 * (2 + skin.getBorderWidth()) + skin.getBubble().tailHeight();
    }

    /**
     * Measures the reply text and resizes the bubble so it fits, up to the maximum width defined by the skin.
     */
    private void sizeToFitMessage(final String message) {
        Insets padding = skin.getPadding();
        FontMetrics metrics = txtMessage.getFontMetrics(txtMessage.getFont());

        // Measure the widest explicit line first, so short replies produce a narrow bubble instead of always
        // being stretched to the maximum width
        int widestLine = 0;
        for (String line : message.split("\n", -1)) {
            widestLine = Math.max(widestLine, metrics.stringWidth(line));
        }

        int edge = 2 + skin.getBorderWidth();
        int inset = 2 * edge;
        int maxTextWidth = Math.max(40, skin.getBubble().maxWidth() - padding.left - padding.right - inset);
        int textWidth = Math.min(maxTextWidth, widestLine + 2);
        txtMessage.setSize(textWidth, Integer.MAX_VALUE);
        int textHeight = txtMessage.getPreferredSize().height;

        int width = Math.max(90, textWidth + padding.left + padding.right + inset);
        int bodyHeight = padding.top + padding.bottom + headerHeight() + textHeight + inset;
        setSize(width, bodyHeight + skin.getBubble().tailHeight() + 2);
        BubbleStack.repositionAll(mascot);
    }

    /**
     * Moves this bubble to the specified position and points its tail at the mascot.
     * This is called by {@link BubbleStack} for every window of a mascot at once.
     */
    void place(final int anchorX, final int y, final boolean tailUp, final Rectangle screen) {
        this.tailUp = tailUp;
        int x = Math.max(screen.x, Math.min(anchorX - getWidth() / 2, screen.x + screen.width - getWidth()));
        if (x != lastPlacedX || y != lastPlacedY) {
            setLocation(x, y);
            lastPlacedX = x;
            lastPlacedY = y;
        }

        int halfTail = skin.getBubble().tailWidth() / 2;
        int radius = skin.getBubble().cornerRadius();
        tailCenterX = Math.max(radius + halfTail,
                Math.min(anchorX - x, getWidth() - radius - halfTail));

        updateShape();
    }

    /**
     * Cuts the window down to the bubble shape, so the area outside the outline stays transparent.
     */
    private void updateShape() {
        if (tailCenterX == appliedTailCenterX && tailUp == appliedTailUp
                && getWidth() == appliedWidth && getHeight() == appliedHeight) {
            return;
        }
        appliedTailCenterX = tailCenterX;
        appliedTailUp = tailUp;
        appliedWidth = getWidth();
        appliedHeight = getHeight();
        setShape(BubbleShape.panel(getWidth(), getHeight(), skin.getBubble(), tailCenterX, tailUp, 0f));
        getContentPane().doLayout();
        repaint();
    }
}
