package com.dantaeusb.zetter.client.gui;

import com.dantaeusb.zetter.client.gui.painting.*;
import com.dantaeusb.zetter.container.EaselContainer;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.IGuiEventListener;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class PaintingScreen extends ContainerScreen<EaselContainer> implements IContainerListener {
    // This is the resource location for the background image
    private static final ResourceLocation PAINTING_RESOURCE = new ResourceLocation("zetter", "textures/gui/painting.png");

    private float sinceLastTick = .0f;

    private ToolsWidget toolsWidget;
    private CanvasWidget canvasWidget;
    private PaletteWidget paletteWidget;
    private ColorCodeWidget colorCodeWidget;
    private SlidersWidget slidersWidget;

    public PaintingScreen(EaselContainer paintingContainer, PlayerInventory playerInventory, ITextComponent title) {
        super(paintingContainer, playerInventory, title);

        this.xSize = 176;
        this.ySize = 185;
    }

    @Override
    protected void init() {
        super.init();

        final int CANVAS_POSITION_X = 48;
        final int CANVAS_POSITION_Y = 9;

        final int TOOLS_POSITION_X = 7;
        final int TOOLS_POSITION_Y = 8;

        final int PALETTE_POSITION_X = 147;
        final int PALETTE_POSITION_Y = 9;

        final int SLIDER_POSITION_X = 13;
        final int SLIDER_OFFSET_Y = 117;

        final int TEXTBOX_POSITION_X = 47;
        final int TEXTBOX_POSITION_Y = 95;

        this.canvasWidget = new CanvasWidget(this, this.getGuiLeft() + CANVAS_POSITION_X, this.getGuiTop() + CANVAS_POSITION_Y);
        this.paletteWidget = new PaletteWidget(this, this.getGuiLeft() + PALETTE_POSITION_X, this.getGuiTop() + PALETTE_POSITION_Y);
        this.toolsWidget = new ToolsWidget(this, this.getGuiLeft() + TOOLS_POSITION_X, this.getGuiTop() + TOOLS_POSITION_Y);
        this.slidersWidget = new SlidersWidget(this, this.getGuiLeft() + SLIDER_POSITION_X, this.getGuiTop() + SLIDER_OFFSET_Y);
        this.colorCodeWidget = new ColorCodeWidget(this, this.getGuiLeft() + TEXTBOX_POSITION_X, this.getGuiTop() + TEXTBOX_POSITION_Y);

        this.colorCodeWidget.initFields();

        this.children.add(this.canvasWidget);
        this.children.add(this.paletteWidget);
        this.children.add(this.toolsWidget);
        this.children.add(this.slidersWidget);
        this.children.add(this.colorCodeWidget);

        this.getContainer().setFirstLoadNotification(this::firstLoadUpdate);

        this.minecraft.keyboardListener.enableRepeatEvents(true);

        this.container.addListener(this);
    }

    public void onClose() {
        super.onClose();
    }

    /**
     * Expose some methods for widgets
     */

    public void addChildren(IGuiEventListener eventListener) {
        this.children.add(eventListener);
    }

    public FontRenderer getFont() {
        return this.font;
    }

    public boolean isCanvasAvailable() {
        return this.container.isCanvasAvailable();
    }

    public boolean isPaletteAvailable() {
        return this.container.isPaletteAvailable();
    }

    public int getColorAt(int pixelIndex) {
        return this.container.getCanvasData().getColorAt(pixelIndex);
    }

    public int getColorAt(int pixelX, int pixelY) {
        return this.container.getCanvasData().getColorAt(pixelX, pixelY);
    }

    public int getCurrentColor() {
        return this.getPaletteColor(this.paletteWidget.getCurrentPaletteSlot());
    }

    public int getPaletteColor(int slot) {
        return this.container.getPaletteColor(slot);
    }

    public void firstLoadUpdate() {
        this.updateSlidersWithCurrentColor();
    }

    public void updateCurrentPaletteColor(int color) {
        this.container.setPaletteColor(this.paletteWidget.getCurrentPaletteSlot(), color);
        this.slidersWidget.updateSlidersWithCurrentColor();
        // Triggers eternal loop
        //this.colorCodeWidget.updateColorValue(color);
    }

    public void pushPaletteUpdateColor() {
        this.container.sendPaletteUpdatePacket(this.paletteWidget.getCurrentPaletteSlot(), this.getCurrentColor());
    }

    public void updateSlidersWithCurrentColor() {
        this.slidersWidget.updateSlidersWithCurrentColor();
    }

    public void useTool(int canvasX, int canvasY) {
        switch (this.toolsWidget.getCurrentTool()) {
            case PENCIL:
                this.getContainer().writePixelOnCanvasClientSide(canvasX, canvasY, this.getCurrentColor(), this.playerInventory.player.getUniqueID());
                break;
            case EYEDROPPER:
                this.getContainer().eyedropper(this.paletteWidget.getCurrentPaletteSlot(), canvasX, canvasY);
                this.updateSlidersWithCurrentColor();
                break;
            case BUCKET:
                this.getContainer().bucket(canvasX, canvasY, this.getCurrentColor());
                break;
        }
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        this.renderHoveredTooltip(matrixStack, mouseX, mouseY);
    }

    @Override
    public void tick() {
        super.tick();

        this.getContainer().tick();
        this.colorCodeWidget.tick();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(MatrixStack matrixStack, float partialTicks, int x, int y) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bindTexture(PAINTING_RESOURCE);

        this.blit(matrixStack, this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);

        this.toolsWidget.render(matrixStack);
        this.canvasWidget.render(matrixStack);
        this.paletteWidget.render(matrixStack);
        this.slidersWidget.render(matrixStack);
        this.colorCodeWidget.render(matrixStack, x, y, partialTicks);
    }

    /**
     * @param matrixStack
     * @param mouseX
     * @param mouseY
     */
    @Override
    protected void drawGuiContainerForegroundLayer(MatrixStack matrixStack, int mouseX, int mouseY) {
        // Do not draw titles to save some extra space
    }

    /**
     * Cancel closing screen when pressing "E", handle input properly
     * @param keyCode
     * @param scanCode
     * @param modifiers
     * @return
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.minecraft.player.closeScreen();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Unfortunately this event is not passed to children
     * @param mouseX
     * @param mouseY
     * @param button
     * @param dragX
     * @param dragY
     * @return
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.canvasWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        this.slidersWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * This one not passed when out of widget bounds but we need to track this event to release slider/pencil
     * @param mouseX
     * @param mouseY
     * @param button
     * @param dragX
     * @param dragY
     * @return
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.canvasWidget.mouseReleased(mouseX, mouseY, button);
        this.slidersWidget.mouseReleased(mouseX, mouseY, button);

        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Helpers
     */

    // Returns true if the given x,y coordinates are within the given rectangle
    public static boolean isInRect(int x, int y, int xSize, int ySize, final int mouseX, final int mouseY){
        return ((mouseX >= x && mouseX <= x+xSize) && (mouseY >= y && mouseY <= y+ySize));
    }

    /**
     * update the crafting window inventory with the items in the list
     */
    public void sendAllContents(Container containerToSend, NonNullList<ItemStack> itemsList) {
    }

    /**
     * Sends two ints to the client-side Container. Used for furnace burning time, smelting progress, brewing progress,
     * and enchanting level. Normally the first int identifies which variable to update, and the second contains the new
     * value. Both are truncated to shorts in non-local SMP.
     */
    public void sendWindowProperty(Container containerIn, int varToUpdate, int newValue) {
    }

    /**
     * Sends the contents of an inventory slot to the client-side Container. This doesn't have to match the actual
     * contents of that slot.
     */
    public void sendSlotContents(Container containerToSend, int slotInd, ItemStack stack) {
    }
}
