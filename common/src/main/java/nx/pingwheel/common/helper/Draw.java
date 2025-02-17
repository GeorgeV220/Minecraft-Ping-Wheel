package nx.pingwheel.common.helper;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec2f;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static nx.pingwheel.common.ClientGlobal.Game;
import static nx.pingwheel.common.ClientGlobal.PING_TEXTURE_ID;
import static nx.pingwheel.common.resource.ResourceReloadListener.hasCustomTexture;

public class Draw {
    private static final int WHITE = ColorHelper.Argb.getArgb(255, 255, 255, 255);
    private static final int SHADOW_BLACK = ColorHelper.Argb.getArgb(64, 0, 0, 0);
    private static final int LIGHT_VALUE_MAX = 15728880;

    private Draw() {
    }

    public static void renderLabels(DrawContext ctx, List<String> texts, Vec2f baseOffset) {
        var matrices = ctx.getMatrices();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);

            var textMetrics = new Vec2f(
                    Game.textRenderer.getWidth(text),
                    Game.textRenderer.fontHeight
            );
            var textOffset = textMetrics.multiply(-0.5f).add(new Vec2f(0f, textMetrics.y * -1.5f * (baseOffset.y + i)));

            matrices.push();
            matrices.translate(textOffset.x, textOffset.y, 0);
            ctx.fill(-2, -2, (int) textMetrics.x + 1, (int) textMetrics.y, SHADOW_BLACK);
            ctx.drawText(Game.textRenderer, text, 0, 0, WHITE, false);
            matrices.pop();
        }
    }

    public static void renderPing(DrawContext ctx, ItemStack itemStack, boolean drawItemIcon) {
        if (itemStack != null && drawItemIcon) {
            Draw.renderGuiItemModel(ctx.getMatrices(), itemStack);
        } else if (hasCustomTexture()) {
            renderCustomPingIcon(ctx);
        } else {
            renderDefaultPingIcon(ctx);
        }
    }

    public static void renderGuiItemModel(MatrixStack matrices, ItemStack itemStack) {
        var model = Game.getItemRenderer().getModel(itemStack, null, null, 0);

        Game.getTextureManager()
                .getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                .setFilter(false, false);

        RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        var matrixStack = RenderSystem.getModelViewStack();
        matrixStack.push();
        matrixStack.multiplyPositionMatrix(matrices.peek().getPositionMatrix());
        matrixStack.translate(0f, 0f, -0.5f);
        matrixStack.scale(1f, -1f, 1f);
        matrixStack.scale(10f, 10f, 0.5f);
        RenderSystem.applyModelViewMatrix();

        var immediate = Game.getBufferBuilders().getEntityVertexConsumers();
        var bl = !model.isSideLit();
        if (bl) {
            DiffuseLighting.disableGuiDepthLighting();
        }

        var matrixStackDummy = new MatrixStack();
        Game.getItemRenderer().renderItem(
                itemStack,
                ModelTransformationMode.GUI,
                false,
                matrixStackDummy,
                immediate,
                LIGHT_VALUE_MAX,
                OverlayTexture.DEFAULT_UV,
                model
        );
        immediate.draw();
        RenderSystem.enableDepthTest();

        if (bl) {
            DiffuseLighting.enableGuiDepthLighting();
        }

        matrixStack.pop();
        RenderSystem.applyModelViewMatrix();
    }

    public static void renderCustomPingIcon(DrawContext ctx) {
        final var size = 12;
        final var offset = size / -2;

        RenderSystem.enableBlend();
        ctx.drawTexture(
                PING_TEXTURE_ID,
                offset,
                offset,
                0,
                0,
                0,
                size,
                size,
                size,
                size
        );
        RenderSystem.disableBlend();
    }

    public static void renderDefaultPingIcon(DrawContext ctx) {
        var matrices = ctx.getMatrices();

        matrices.push();
        MathUtils.rotateZ(matrices, (float) (Math.PI / 4f));
        matrices.translate(-2.5, -2.5, 0);
        ctx.fill(0, 0, 5, 5, WHITE);
        matrices.pop();
    }

    public static void renderArrow(MatrixStack m, boolean antialias) {
        if (antialias) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        }

        var bufferBuilder = Tessellator.getInstance().getBuffer();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        var mat = m.peek().getPositionMatrix();
        bufferBuilder.vertex(mat, 5f, 0f, 0f).color(1f, 1f, 1f, 1f).next();
        bufferBuilder.vertex(mat, -5f, -5f, 0f).color(1f, 1f, 1f, 1f).next();
        bufferBuilder.vertex(mat, -3f, 0f, 0f).color(1f, 1f, 1f, 1f).next();
        bufferBuilder.vertex(mat, -5f, 5f, 0f).color(1f, 1f, 1f, 1f).next();
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
    }
}
