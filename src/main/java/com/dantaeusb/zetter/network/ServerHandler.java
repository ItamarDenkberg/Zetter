package com.dantaeusb.zetter.network;

import com.dantaeusb.zetter.Zetter;
import com.dantaeusb.zetter.canvastracker.CanvasServerTracker;
import com.dantaeusb.zetter.canvastracker.CanvasTrackerCapability;
import com.dantaeusb.zetter.container.ArtistTableContainer;
import com.dantaeusb.zetter.core.ModNetwork;
import com.dantaeusb.zetter.container.EaselContainer;
import com.dantaeusb.zetter.network.packet.*;
import com.dantaeusb.zetter.storage.AbstractCanvasData;
import com.dantaeusb.zetter.storage.CanvasData;
import com.dantaeusb.zetter.storage.DummyCanvasData;
import com.dantaeusb.zetter.storage.PaintingData;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.function.Supplier;

public class ServerHandler {
    /**
     * Called when a message is received of the appropriate type.
     * CALLED BY THE NETWORK THREAD, NOT THE SERVER THREAD
     * @param message The message
     */
    public static void handleFrameBuffer(final CPaintingFrameBufferPacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.SERVER) {
            Zetter.LOG.warn("CPaintingUpdatePacket received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        final ServerPlayerEntity sendingPlayer = ctx.getSender();
        if (sendingPlayer == null) {
            Zetter.LOG.warn("EntityPlayerMP was null when CPaintingUpdatePacket was received");
        }

        ctx.enqueueWork(() -> processFrameBuffer(packetIn, sendingPlayer));
    }

    /**
     * Update canvas on server-side and send update to other tracking players
     * @param packetIn
     * @param sendingPlayer
     *
     * @todo: send changes to TE, not container, as it's created per player
     */
    public static void processFrameBuffer(final CPaintingFrameBufferPacket packetIn, ServerPlayerEntity sendingPlayer) {
        if (sendingPlayer.openContainer instanceof EaselContainer) {
            EaselContainer paintingContainer = (EaselContainer)sendingPlayer.openContainer;

            paintingContainer.processFrameBufferServer(packetIn.getFrameBuffer(), sendingPlayer.getUniqueID());

            /**
             * Keep it there, but it's not really useful since it's easier to sync whole canvas and keep recent
             * changes on top
             */

            /*for (PlayerEntity playerEntity : paintingContainer.getTileEntityReference().getPlayersUsing()) {
                if (playerEntity.equals(sendingPlayer)) {
                    // No need to send it back
                    continue;
                }

                SPaintingFrameBufferPacket packet = new SPaintingFrameBufferPacket(packetIn.getFrameBuffer());
                ModNetwork.simpleChannel.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) playerEntity), packet);
            }*/
        }
    }

    /**
     * Called when a message is received of the appropriate type.
     * CALLED BY THE NETWORK THREAD, NOT THE SERVER THREAD
     *
     * Apparently we can't create just Dummy Canvas Data instances -- world would not recognize the
     * save data when it's another class! So we need to provide expected type in message as well.
     * Though it seems unnecessary, this actually adds some level of type-safety about it.
     *
     * @param message The message
     */
    public static void handleRequestSync(final CCanvasRequestPacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.SERVER) {
            Zetter.LOG.warn("CRequestSyncPacket received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        final ServerPlayerEntity sendingPlayer = ctx.getSender();
        if (sendingPlayer == null) {
            Zetter.LOG.warn("EntityPlayerMP was null when CRequestSyncPacket was received");
        }

        ctx.enqueueWork(() -> processRequestSync(packetIn, sendingPlayer));
    }

    public static void processRequestSync(final CCanvasRequestPacket packetIn, ServerPlayerEntity sendingPlayer) {
        // Get overworld world instance
        MinecraftServer server = sendingPlayer.getServerWorld().getServer();
        World world = server.func_241755_D_();
        CanvasServerTracker canvasTracker = (CanvasServerTracker) world.getCapability(CanvasTrackerCapability.CAPABILITY_CANVAS_TRACKER).orElse(null);

        Zetter.LOG.info("Got request to sync canvas " + packetIn.getCanvasName());

        if (canvasTracker == null) {
            Zetter.LOG.error("Cannot find world canvas capability");
            return;
        }

        // Notify canvas manager that player is tracking canvas from no ow
        canvasTracker.trackCanvas(sendingPlayer.getUniqueID(), packetIn.getCanvasName());

        AbstractCanvasData canvasData;

        if (packetIn.getCanvasType() == AbstractCanvasData.Type.CANVAS) {
            canvasData = canvasTracker.getCanvasData(packetIn.getCanvasName(), CanvasData.class);
        } else if (packetIn.getCanvasType() == AbstractCanvasData.Type.PAINTING) {
            canvasData = canvasTracker.getCanvasData(packetIn.getCanvasName(), PaintingData.class);
        } else {
            canvasData = canvasTracker.getCanvasData(packetIn.getCanvasName(), DummyCanvasData.class);
        }

        if (canvasData == null) {
            Zetter.LOG.error("Player " + sendingPlayer + " requested non-existent canvas: " + packetIn.getCanvasName());
            return;
        }

        if (canvasData instanceof PaintingData) {
            SPaintingSyncMessage paintingSyncMessage = new SPaintingSyncMessage((PaintingData) canvasData, System.currentTimeMillis());

            ModNetwork.simpleChannel.send(PacketDistributor.PLAYER.with(() -> sendingPlayer), paintingSyncMessage);
        } else {
            SCanvasSyncMessage canvasSyncMessage = new SCanvasSyncMessage(canvasData, System.currentTimeMillis());

            ModNetwork.simpleChannel.send(PacketDistributor.PLAYER.with(() -> sendingPlayer), canvasSyncMessage);
        }
    }

    /**
     * Called when a message is received of the appropriate type.
     * CALLED BY THE NETWORK THREAD, NOT THE SERVER THREAD
     * @param message The message
     */
    public static void handleUnloadRequest(final CCanvasUnloadRequestPacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.SERVER) {
            Zetter.LOG.warn("CRequestSyncPacket received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        final ServerPlayerEntity sendingPlayer = ctx.getSender();
        if (sendingPlayer == null) {
            Zetter.LOG.warn("EntityPlayerMP was null when CRequestSyncPacket was received");
        }

        ctx.enqueueWork(() -> processUnloadRequest(packetIn, sendingPlayer));
    }

    /**
     * @todo: Think about removing this
     * Not sure if it's needed, this can cause condition when canvas is unloaded while
     * other players would like to track it. Unloading on back-end should happen
     * by requests timeout and I believe this should work properly already
     *
     * @param packetIn
     * @param sendingPlayer
     */
    public static void processUnloadRequest(final CCanvasUnloadRequestPacket packetIn, ServerPlayerEntity sendingPlayer) {
        // Get overworld world instance
        MinecraftServer server = sendingPlayer.getServerWorld().getServer();
        World world = server.func_241755_D_();
        CanvasServerTracker canvasTracker = (CanvasServerTracker) world.getCapability(CanvasTrackerCapability.CAPABILITY_CANVAS_TRACKER).orElse(null);

        Zetter.LOG.info("Got request to unload canvas " + packetIn.getCanvasName());

        if (canvasTracker == null) {
            Zetter.LOG.error("Cannot find world canvas capability");
            return;
        }

        canvasTracker.stopTrackingCanvas(sendingPlayer.getUniqueID(), packetIn.getCanvasName());
    }

    /**
     * Called when a message is received of the appropriate type.
     * CALLED BY THE NETWORK THREAD, NOT THE SERVER THREAD
     * @param message The message
     */
    public static void handlePaletteUpdate(final CPaletteUpdatePacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.SERVER) {
            Zetter.LOG.warn("PaletteUpdatePacket received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        final ServerPlayerEntity sendingPlayer = ctx.getSender();
        if (sendingPlayer == null) {
            Zetter.LOG.warn("EntityPlayerMP was null when PaletteUpdatePacket was received");
        }

        ctx.enqueueWork(() -> processPaletteUpdate(packetIn, sendingPlayer));
    }

    public static void processPaletteUpdate(final CPaletteUpdatePacket packetIn, ServerPlayerEntity sendingPlayer) {
        if (sendingPlayer.openContainer instanceof EaselContainer) {
            EaselContainer paintingContainer = (EaselContainer)sendingPlayer.openContainer;
            paintingContainer.setPaletteColor(packetIn.getSlotIndex(), packetIn.getColor());
        }
    }

    /**
     * Called when a message is received of the appropriate type.
     * CALLED BY THE NETWORK THREAD, NOT THE SERVER THREAD
     * @param message The message
     */
    public static void handleCreatePainting(final CUpdatePaintingPacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.SERVER) {
            Zetter.LOG.warn("CCreatePaintingPacket received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        final ServerPlayerEntity sendingPlayer = ctx.getSender();
        if (sendingPlayer == null) {
            Zetter.LOG.warn("EntityPlayerMP was null when CCreatePaintingPacket was received");
        }

        ctx.enqueueWork(() -> processCreatePainting(packetIn, sendingPlayer));
    }

    public static void processCreatePainting(final CUpdatePaintingPacket packetIn, ServerPlayerEntity sendingPlayer) {
        if (sendingPlayer.openContainer instanceof ArtistTableContainer) {
            ArtistTableContainer artistTableContainer = (ArtistTableContainer)sendingPlayer.openContainer;
            artistTableContainer.updatePaintingName(packetIn.getPaintingName());
        }
    }


    /**
     * Called when a message is received of the appropriate type.
     * CALLED BY THE NETWORK THREAD, NOT THE SERVER THREAD
     * @param message The message
     */
    public static void handleBucketTool(final CCanvasBucketToolPacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.SERVER) {
            Zetter.LOG.warn("CCanvasBucketToolPacket received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        final ServerPlayerEntity sendingPlayer = ctx.getSender();
        if (sendingPlayer == null) {
            Zetter.LOG.warn("EntityPlayerMP was null when CCanvasBucketToolPacket was received");
        }

        ctx.enqueueWork(() -> processBucketTool(packetIn, sendingPlayer));
    }

    public static void processBucketTool(final CCanvasBucketToolPacket packetIn, ServerPlayerEntity sendingPlayer) {
        if (sendingPlayer.openContainer instanceof EaselContainer) {
            EaselContainer easelContainer = (EaselContainer)sendingPlayer.openContainer;
            easelContainer.processBucketToolServer(packetIn.position, packetIn.color);
        }
    }

    public static boolean isThisProtocolAcceptedByServer(String protocolVersion) {
        return ModNetwork.MESSAGE_PROTOCOL_VERSION.equals(protocolVersion);
    }
}
