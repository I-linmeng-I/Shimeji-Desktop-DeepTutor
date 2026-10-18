package com.group_finity.mascot.chat;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Builds the outline of a chat bubble: a rounded body merged with a triangular tail.
 * <p>
 * Merging the two shapes before filling and stroking means the outline runs all the way around the tail as well,
 * with no seam across its base, and the same shape can be used to cut the window with {@code setShape}.
 */
final class BubbleShape {
    private BubbleShape() {
    }

    /**
     * Builds the merged shape of the body and the tail.
     *
     * @param width the width of the window
     * @param height the height of the window
     * @param spec the geometry declared by the skin
     * @param tailCenterX the horizontal centre of the tail, relative to the left edge
     * @param tailUp whether the tail points up, i.e. the bubble sits below the mascot
     * @param inset how far the outline is pushed into the window, so the stroke is not clipped
     * @return the merged shape of the body and the tail
     */
    static Area panel(final int width, final int height, final ChatSkin.Bubble spec,
                      final int tailCenterX, final boolean tailUp, final float inset) {
        int bodyTop = tailUp ? spec.tailHeight() : 0;
        int bodyHeight = height - spec.tailHeight();

        Area panel = new Area(new RoundRectangle2D.Float(inset, bodyTop + inset,
                Math.max(1f, width - 2 * inset), Math.max(1f, bodyHeight - 2 * inset),
                spec.cornerRadius(), spec.cornerRadius()));
        panel.add(new Area(tail(width, height, spec, tailCenterX, tailUp, inset)));
        return panel;
    }

    private static Path2D tail(final int width, final int height, final ChatSkin.Bubble spec,
                               final int tailCenterX, final boolean tailUp, final float inset) {
        int halfWidth = spec.tailWidth() / 2;
        int bodyEdge = tailUp ? spec.tailHeight() : height - spec.tailHeight();
        int tip = tailUp ? 0 : height;
        int baseY = bodyEdge + (tailUp ? (int) inset : -(int) inset);
        int tipY = tip + (tailUp ? (int) inset : -(int) inset);
        int center = Math.max(halfWidth, Math.min(tailCenterX, width - halfWidth));

        Path2D path = new Path2D.Float();
        path.moveTo(center - halfWidth, baseY);
        path.lineTo(center, tipY);
        path.lineTo(center + halfWidth, baseY);
        path.closePath();
        return path;
    }
}
