package com.mateof24.gui;

/**
 * The scale transform, on the axis that actually tracks it.
 *
 * <p>{@link Painter} needs one call that touches the pose stack, and the pose
 * stack changed shape at 1.21.6 — {@code PoseStack.pushPose} with three axes
 * became {@code Matrix3x2fStack.pushMatrix} with two. That boundary does not
 * line up with the screen flavour, which tracks input instead and spans
 * 1.21.1 to 1.21.6 in one file. Putting it here, where {@code clientVer}
 * already splits on exactly this, is what keeps both correct.</p>
 *
 * <p>26.2: GuiGraphicsExtractor, Matrix3x2fStack.</p>
 */
final class PoseScale {

    private PoseScale() {}

    static void push(net.minecraft.client.gui.GuiGraphicsExtractor graphics, float scale, int originX, int originY) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(originX, originY);
        pose.scale(scale, scale);
        pose.translate(-originX, -originY);
    }

    static void pop(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    /**
     * Puts everything queued so far on the screen, so what is drawn next is
     * genuinely on top of it.
     *
     * <p>Needed because text and fills go through different render types and
     * the text pass is drawn last: a label queued early lands over a box
     * filled late. This is flush() up to 1.21.5 and nextStratum() from
     * 1.21.6 — the same boundary the pose stack moved on.</p>
     */
    static void nextLayer(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        graphics.nextStratum();
    }

    static void dropLayer(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
    }
}
