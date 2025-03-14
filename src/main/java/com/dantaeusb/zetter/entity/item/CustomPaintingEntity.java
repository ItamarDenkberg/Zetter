package com.dantaeusb.zetter.entity.item;

import com.dantaeusb.zetter.core.Helper;
import com.dantaeusb.zetter.core.ModEntities;
import com.dantaeusb.zetter.core.ModItems;
import com.dantaeusb.zetter.item.FrameItem;
import com.dantaeusb.zetter.storage.PaintingData;
import com.google.common.collect.Maps;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Pose;
import net.minecraft.entity.item.HangingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.NetworkHooks;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;

public class CustomPaintingEntity extends HangingEntity implements IEntityAdditionalSpawnData {
    public static final String NBT_TAG_FACING = "Facing";
    public static final String NBT_TAG_PAINTING_CODE = "PaintingCode";
    public static final String NBT_TAG_BLOCK_SIZE = "BlockSize";
    public static final String NBT_TAG_MATERIAL = "Material";
    public static final String NBT_TAG_HAS_PLATE = "HasPlate";

    protected String canvasCode;

    /**
     * This data is derivative from canvas data and duplicates width/height attributes
     * However as canvas controlled by capability and loaded asynchronously we
     * need this data in order to place entity properly and pre-render by adding
     * this data to spawn packet
     */
    protected int blockWidth;
    protected int blockHeight;

    protected boolean hasPlate;

    protected Materials material;

    public CustomPaintingEntity(EntityType<? extends CustomPaintingEntity> type, World world) {
        super(type, world);
    }

    public CustomPaintingEntity(World world, BlockPos pos, Direction facing, Materials material, boolean hasPlate, String canvasCode, int[] blockSize) {
        super(ModEntities.CUSTOM_PAINTING_ENTITY, world, pos);

        this.material = material;
        this.hasPlate = hasPlate;

        this.canvasCode = canvasCode;

        this.blockWidth = blockSize[0];
        this.blockHeight = blockSize[1];

        this.updateFacingWithBoundingBox(facing);
    }

    public String getCanvasCode() {
        if (this.canvasCode == null) {
            return Helper.FALLBACK_CANVAS_CODE;
        }

        return this.canvasCode;
    }

    public int getBlockWidth() {
        return this.blockWidth;
    }

    public int getBlockHeight() {
        return this.blockHeight;
    }

    public Materials getMaterial() {
        return this.material;
    }

    public boolean hasPlate() {
        return this.hasPlate;
    }

    protected float getEyeHeight(Pose poseIn, EntitySize sizeIn) {
        return 0.0F;
    }

    /**
     * Updates facing and bounding box based on it
     */
    protected void updateFacingWithBoundingBox(Direction facingDirectionIn) {
        Validate.notNull(facingDirectionIn);
        this.facingDirection = facingDirectionIn;
        if (facingDirectionIn.getAxis().isHorizontal()) {
            this.rotationPitch = 0.0F;
            this.rotationYaw = (float)(this.facingDirection.getHorizontalIndex() * 90);
        } else {
            this.rotationPitch = (float)(-90 * facingDirectionIn.getAxisDirection().getOffset());
            this.rotationYaw = 0.0F;
        }

        this.prevRotationPitch = this.rotationPitch;
        this.prevRotationYaw = this.rotationYaw;
        this.updateBoundingBox();
    }

    /**
     * Updates the entity bounding box based on current facing
     */
    protected void updateBoundingBox() {
        if (this.facingDirection != null) {
            double xCenter = (double)this.hangingPosition.getX() + 0.5D;
            double yCenter = (double)this.hangingPosition.getY() + 0.5D;
            double zCenter = (double)this.hangingPosition.getZ() + 0.5D;

            double thicknessOffset = 0.5D - (1.0D / 32.0D);

            double hCenterOffset = this.offs(this.getWidthPixels());
            double vCenterOffset = this.offs(this.getHeightPixels());

            xCenter = xCenter - (double)this.facingDirection.getXOffset() * thicknessOffset;
            zCenter = zCenter - (double)this.facingDirection.getZOffset() * thicknessOffset;

            yCenter = yCenter + vCenterOffset;

            Direction direction = this.facingDirection.rotateYCCW();

            xCenter = xCenter + hCenterOffset * (double)direction.getXOffset();
            zCenter = zCenter + hCenterOffset * (double)direction.getZOffset();

            this.setRawPosition(xCenter, yCenter, zCenter);

            double xWidth = this.getWidthPixels();
            double yHeight = this.getHeightPixels();
            double zWidth = this.getWidthPixels();

            if (this.facingDirection.getAxis() == Direction.Axis.Z) {
                zWidth = 1.0D;
            } else {
                xWidth = 1.0D;
            }

            xWidth = xWidth / 16.0D / 2.0D;
            yHeight = yHeight / 16.0D / 2.0D;
            zWidth = zWidth / 16.0D / 2.0D;

            this.setBoundingBox(new AxisAlignedBB(
                xCenter - xWidth, yCenter - yHeight, zCenter - zWidth,
                xCenter + xWidth, yCenter + yHeight, zCenter + zWidth
            ));
        }
    }

    /**
     * Offset from hanging block position to geometrical center of the picture
     * if pixel size is twice as resolution, like 32, 64
     * [][]*[][]
     * that means that block edge is geometrical center. If it's not, like 48 or 16,
     * we have to move center to the middle of the block
     * [][*][]
     * but because center is adjusted by 0.5 by default, we're returning
     * the opposite
     */
    private double offs(int pixelSize) {
        return pixelSize % (Helper.getResolution().getNumeric() * 2) == 0 ? 0.5D : 0.0D;
    }

    public double[] getRenderOffset() {
        final double xOffset = this.blockWidth / 2.0D;
        final double yOffset = this.blockHeight / 2.0D;

        return new double[]{xOffset, yOffset};
    }

    @Override
    public ActionResultType processInitialInteract(PlayerEntity player, Hand hand) {
        if (!this.hasPlate()) {
            return ActionResultType.PASS;
        }

        if (!player.getEntityWorld().isRemote()) {
            return ActionResultType.CONSUME;
        }

        PaintingData paintingData = Helper.getWorldCanvasTracker(this.world).getCanvasData(this.canvasCode, PaintingData.class);

        String paintingName = paintingData.getPaintingName();
        String authorName = paintingData.getAuthorName();

        if (StringUtils.isNullOrEmpty(paintingName)) {
            paintingName = new TranslationTextComponent("item.zetter.painting.unnamed").getString();
        }

        if (StringUtils.isNullOrEmpty(authorName)) {
            authorName = new TranslationTextComponent("item.zetter.painting.unknown").getString();
        }

        player.sendStatusMessage(
            new TranslationTextComponent("item.zetter.customPaintingByAuthor", paintingName, authorName),
            true
        );

        return ActionResultType.CONSUME;
    }

    public void writeAdditional(CompoundNBT compound) {
        compound.putByte(NBT_TAG_FACING, (byte)this.facingDirection.getHorizontalIndex());
        compound.putString(NBT_TAG_PAINTING_CODE, this.canvasCode);
        compound.putIntArray(NBT_TAG_BLOCK_SIZE, new int[]{this.blockWidth, this.blockHeight});
        compound.putString(NBT_TAG_MATERIAL, this.material.toString());
        compound.putBoolean(NBT_TAG_HAS_PLATE, this.hasPlate);

        super.writeAdditional(compound);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    public void readAdditional(CompoundNBT compound) {
        this.facingDirection = Direction.byHorizontalIndex(compound.getByte(NBT_TAG_FACING));
        this.canvasCode = compound.getString(NBT_TAG_PAINTING_CODE);

        if (compound.contains(NBT_TAG_BLOCK_SIZE)) {
            int[] blockSize = compound.getIntArray(NBT_TAG_BLOCK_SIZE);
            this.blockWidth = blockSize[0];
            this.blockHeight = blockSize[1];
        }

        if (compound.contains(NBT_TAG_MATERIAL)) {
            this.material = Materials.fromString(compound.getString(NBT_TAG_MATERIAL));
        } else {
            // @todo: replace to OAK on release
            this.material = Materials.DARK_OAK;
        }

        if (compound.contains(NBT_TAG_HAS_PLATE)) {
            this.hasPlate = compound.getBoolean(NBT_TAG_HAS_PLATE);
        } else {
            // @todo: remove on release?
            this.hasPlate = false;
        }

        super.readAdditional(compound);
        this.updateFacingWithBoundingBox(this.facingDirection);
    }

    public void writeSpawnData(PacketBuffer buffer) {
        buffer.writeBlockPos(this.hangingPosition);
        buffer.writeByte((byte)this.facingDirection.getHorizontalIndex());

        buffer.writeString(this.canvasCode, 64);

        buffer.writeInt(this.blockWidth);
        buffer.writeInt(this.blockHeight);

        buffer.writeString(this.material.toString(), 64);
        buffer.writeBoolean(this.hasPlate);
    }

    public void readSpawnData(PacketBuffer buffer) {
        this.hangingPosition = buffer.readBlockPos();
        this.facingDirection = Direction.byHorizontalIndex(buffer.readByte());

        this.canvasCode = buffer.readString(64);

        this.blockWidth = buffer.readInt();
        this.blockHeight = buffer.readInt();

        this.material = Materials.fromString(buffer.readString(64));
        this.hasPlate = buffer.readBoolean();

        this.updateFacingWithBoundingBox(this.facingDirection);
    }

    /**
     * Multiplying by 16 because extended class does not expect to support
     * Multiple image resolutions
     * @return
     */
    public int getWidthPixels() {
        return this.blockWidth * Helper.getResolution().getNumeric();
    }

    public int getHeightPixels() {
        return this.blockHeight * Helper.getResolution().getNumeric();
    }

    /**
     * Checks if the entity is in range to render.
     */
    @OnlyIn(Dist.CLIENT)
    public boolean isInRangeToRenderDist(double distance) {
        double d0 = 16.0D;
        d0 = d0 * 64.0D * getRenderDistanceWeight();
        return distance < d0 * d0;
    }

    /**
     * Called when this entity is broken. Entity parameter may be null.
     */
    public void onBroken(@Nullable Entity brokenEntity) {
        // @todo: remove item if canvas code is set to fallback code
        if (this.world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
            this.playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1.0F, 1.0F);

            ItemStack canvasStack = new ItemStack(ModItems.FRAMES.get(Helper.getFrameKey(this.material, this.hasPlate)));

            PaintingData paintingData = Helper.getWorldCanvasTracker(this.world).getCanvasData(this.canvasCode, PaintingData.class);

            FrameItem.setPaintingData(canvasStack, paintingData);
            FrameItem.setBlockSize(canvasStack, new int[]{this.blockWidth, this.blockHeight});

            this.entityDropItem(canvasStack);
        }
    }

    /**
     * Sets the location and Yaw/Pitch of an entity in the world
     * Do not re-center bounding box
     * Copied from PaintingEntity
     */
    public void setLocationAndAngles(double x, double y, double z, float yaw, float pitch) {
        this.setPosition(x, y, z);
    }

    /**
     * Sets a target for the client to interpolate towards over the next few ticks
     * Copied from PaintingEntity
     */
    @OnlyIn(Dist.CLIENT)
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        BlockPos blockpos = this.hangingPosition.add(x - this.getPosX(), y - this.getPosY(), z - this.getPosZ());
        this.setPosition(blockpos.getX(), blockpos.getY(), blockpos.getZ());
    }

    public void playPlaceSound() {
        this.playSound(SoundEvents.ENTITY_PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Nonnull
    @Override
    public IPacket<?> createSpawnPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public enum Materials {
        ACACIA("acacia"),
        BIRCH("birch"),
        DARK_OAK("dark_oak"),
        JUNGLE("jungle"),
        OAK("oak"),
        SPRUCE("spruce"),
        CRIMSON("crimson"),
        WARPED("warped");

        private static final Map<String, Materials> LOOKUP = Maps.uniqueIndex(
                Arrays.asList(Materials.values()),
                Materials::toString
        );

        private final String text;

        Materials(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }

        @Nullable
        public static Materials fromString(String stringValue) {
            return LOOKUP.get(stringValue);
        }
    }
}
