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
 * <p>1.21.1 to 1.21.5: GuiGraphics whose pose() is a PoseStack.</p>
 */
final class PoseScale {

    private PoseScale() {}

    static void push(net.minecraft.client.gui.GuiGraphics graphics, float scale, int originX, int originY) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(originX, originY, 0);
        pose.scale(scale, scale, 1);
        pose.translate(-originX, -originY, 0);
    }

    static void pop(net.minecraft.client.gui.GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    /**
     * Lifts what comes next above everything drawn so far.
     *
     * <p>Nothing else here touches the pose, so the whole screen draws at z 0
     * and an overlay tangles with what it is meant to cover however late it is
     * drawn. Raising the z is vanilla's own answer -- it is what renderTooltip
     * does before drawing over the inventory -- and it is undone by
     * {@link #dropLayer}.</p>
     *
     * <p>From 1.21.6 the game grew nextStratum() for exactly this, which needs
     * no undoing; the pair keeps the same shape at the call site.</p>
     */
    static void nextLayer(net.minecraft.client.gui.GuiGraphics graphics) {
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
    }

    static void dropLayer(net.minecraft.client.gui.GuiGraphics graphics) {
        graphics.pose().popPose();
    }
}
