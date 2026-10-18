package com.group_finity.mascot.chat;

import com.group_finity.mascot.Mascot;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps track of the bubbles of every mascot and stacks them above or below it.
 * <p>
 * A mascot shows at most {@value #MAX_BUBBLES} bubbles at once, oldest first, the newest sitting closest to the
 * mascot. This class only owns that bookkeeping; the bubbles themselves know how to draw and place a window.
 *
 * @see ChatBubble
 */
final class BubbleStack {
    /**
     * The maximum number of bubbles shown for a single mascot at the same time.
     */
    static final int MAX_BUBBLES = 2;

    /**
     * The gap between the tail tip and the mascot, and the gap to the screen edges, in pixels.
     */
    private static final int SCREEN_MARGIN = 6;

    /**
     * The gap between two stacked bubbles, in pixels.
     */
    private static final int BUBBLE_GAP = 8;

    /**
     * The bubbles of each mascot, oldest first. The last element is the one closest to the mascot.
     */
    private static final Map<Mascot, List<ChatBubble>> bubbles = new HashMap<>();

    /**
     * The input boxes that are currently open, keyed by the mascot they belong to.
     */
    private static final Map<Mascot, ChatBubble> inputBubbles = new HashMap<>();

    private BubbleStack() {
    }

    static boolean hasInput(final Mascot mascot) {
        return inputBubbles.containsKey(mascot);
    }

    static ChatBubble inputOf(final Mascot mascot) {
        return inputBubbles.get(mascot);
    }

    static void setInput(final Mascot mascot, final ChatBubble bubble) {
        inputBubbles.put(mascot, bubble);
    }

    static void clearInput(final Mascot mascot, final ChatBubble bubble) {
        inputBubbles.remove(mascot, bubble);
    }

    /**
     * Registers a bubble for a mascot, closing the oldest ones so the limit is never exceeded.
     *
     * @param mascot the mascot the bubble belongs to
     * @param bubble the bubble to add
     */
    static void add(final Mascot mascot, final ChatBubble bubble) {
        List<ChatBubble> list = bubbles.computeIfAbsent(mascot, key -> new ArrayList<>());
        while (list.size() >= MAX_BUBBLES) {
            list.remove(0).dispose();
        }
        list.add(bubble);
    }

    /**
     * Replaces a registered bubble with another one, keeping its place in the stack.
     * The replacement is appended if the original was not registered.
     *
     * @param mascot the mascot the bubble belongs to
     * @param original the bubble to replace
     * @param replacement the bubble that takes its place
     */
    static void replace(final Mascot mascot, final ChatBubble original, final ChatBubble replacement) {
        List<ChatBubble> list = bubbles.get(mascot);
        int index = list == null ? -1 : list.indexOf(original);
        if (index >= 0) {
            list.set(index, replacement);
        } else {
            bubbles.computeIfAbsent(mascot, key -> new ArrayList<>()).add(replacement);
        }
    }

    static void remove(final Mascot mascot, final ChatBubble bubble) {
        List<ChatBubble> list = bubbles.get(mascot);
        if (list == null) {
            return;
        }
        list.remove(bubble);
        if (list.isEmpty()) {
            bubbles.remove(mascot);
        }
    }

    /**
     * Stacks and moves all bubbles of the specified mascot.
     * <p>
     * The newest bubble sits closest to the mascot. The whole stack is placed above the mascot when there is
     * enough room, and below it otherwise.
     */
    static void repositionAll(final Mascot mascot) {
        List<ChatBubble> list = bubbles.get(mascot);
        if (list == null || list.isEmpty()) {
            return;
        }
        Rectangle target = mascot.getBounds();
        Rectangle screen = screenOf(target);
        int anchorX = target.x + target.width / 2;

        // Work out where every bubble would go above the mascot, newest nearest
        int[] above = new int[list.size()];
        int bottom = target.y - SCREEN_MARGIN;
        for (int i = list.size() - 1; i >= 0; i--) {
            ChatBubble bubble = list.get(i);
            above[i] = bottom - bubble.getHeight();
            bottom = above[i] - BUBBLE_GAP;
        }

        if (above[0] >= screen.y) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).place(anchorX, above[i], false, screen);
            }
        } else {
            int top = target.y + target.height + SCREEN_MARGIN;
            for (int i = list.size() - 1; i >= 0; i--) {
                ChatBubble bubble = list.get(i);
                bubble.place(anchorX, top, true, screen);
                top += bubble.getHeight() + BUBBLE_GAP;
            }
        }
    }

    /**
     * Finds the screen that contains the specified rectangle.
     */
    private static Rectangle screenOf(final Rectangle target) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getScreenDevices()[0].getDefaultConfiguration().getBounds();
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            if (bounds.contains(target.getCenterX(), target.getCenterY())) {
                return bounds;
            }
        }
        return screen;
    }
}
