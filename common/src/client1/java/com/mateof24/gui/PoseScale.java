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
}
