package com.dantaeusb.zetter.client.gui.artisttable;

import com.dantaeusb.zetter.canvastracker.ICanvasTracker;
import com.dantaeusb.zetter.client.gui.ArtistTableScreen;
import com.dantaeusb.zetter.client.gui.PaintingScreen;
import com.dantaeusb.zetter.client.gui.painting.AbstractPaintingWidget;
import com.dantaeusb.zetter.client.renderer.CanvasRenderer;
import com.dantaeusb.zetter.container.ArtistTableContainer;
import com.dantaeusb.zetter.core.Helper;
import com.dantaeusb.zetter.core.ModItems;
import com.dantaeusb.zetter.item.CanvasItem;
import com.dantaeusb.zetter.storage.AbstractCanvasData;
import com.dantaeusb.zetter.storage.CanvasData;
import com.dantaeusb.zetter.storage.DummyCanvasData;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.IRenderable;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Tuple;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class CombinedCanvasWidget extends AbstractArtistTableWidget implements IRenderable, IGuiEventListener {
    private static final int size = Helper.getBasicResolution().getNumeric();

    public CombinedCanvasWidget(ArtistTableScreen parentScreen, int x, int y) {
        super(parentScreen, x, y, size, size, new TranslationTextComponent("container.zetter.painting.canvas"));
    }

    public void render(MatrixStack matrixStack) {
        matrixStack.push();
        matrixStack.translate(this.x, this.y, 1.0F);

        DummyCanvasData canvasData = this.parentScreen.getContainer().getCanvasCombination().canvasData;

        this.drawCanvas(matrixStack, canvasData);

        matrixStack.pop();
    }

    private void drawCanvas(MatrixStack matrixStack, @Nullable DummyCanvasData canvasData) {
        if (canvasData != null) {
            int scale = getScale(canvasData);
            Tuple<Integer, Integer> displacement = getDisplacement(canvasData, scale);

            matrixStack.push();
            matrixStack.translate(displacement.getA(), displacement.getB(), 1.0F);
            matrixStack.scale(scale, scale, 1.0F);

            IRenderTypeBuffer.Impl renderTypeBufferImpl = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
            CanvasRenderer.getInstance().renderCanvas(matrixStack, renderTypeBufferImpl, canvasData, 15728880);
            renderTypeBufferImpl.finish();

            matrixStack.pop();
        }
    }

    public static Tuple<Integer, Integer> getDisplacement(@Nullable DummyCanvasData canvasData, int scale) {
        if (canvasData != null) {
            int xSize = (canvasData.getWidth() / Helper.getResolution().getNumeric()) * Helper.getBasicResolution().getNumeric() * scale;
            int ySize = (canvasData.getHeight() / Helper.getResolution().getNumeric()) * Helper.getBasicResolution().getNumeric() * scale;

            int width = Helper.getBasicResolution().getNumeric() * ArtistTableContainer.CANVAS_COLUMN_COUNT;
            int height = Helper.getBasicResolution().getNumeric() * ArtistTableContainer.CANVAS_ROW_COUNT;

            return new Tuple<>((width - xSize) / 2, (height - ySize) / 2);
        }

        return new Tuple<>(0, 0);
    }

    public static int getScale(@Nullable DummyCanvasData canvasData) {
        if (canvasData != null) {
            int xScale = ArtistTableContainer.CANVAS_COLUMN_COUNT / (canvasData.getWidth() / Helper.getResolution().getNumeric());
            int yScale = ArtistTableContainer.CANVAS_ROW_COUNT / (canvasData.getHeight() / Helper.getResolution().getNumeric());

            return Math.min(xScale, yScale);
        }

        return 1;
    }

    @Nullable
    public static CanvasData getCanvasData(World world, String canvasName) {
        ICanvasTracker canvasTracker = Helper.getWorldCanvasTracker(world);

        if (canvasTracker == null) {
            return null;
        }

        return canvasTracker.getCanvasData(canvasName, CanvasData.class);
    }
}
