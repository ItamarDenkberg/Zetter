package com.dantaeusb.zetter.network;

import com.dantaeusb.zetter.Zetter;
import com.dantaeusb.zetter.canvastracker.CanvasTrackerCapability;
import com.dantaeusb.zetter.canvastracker.ICanvasTracker;
import com.dantaeusb.zetter.container.ArtistTableContainer;
import com.dantaeusb.zetter.container.EaselContainer;
import com.dantaeusb.zetter.core.Helper;
import com.dantaeusb.zetter.core.ModNetwork;
import com.dantaeusb.zetter.network.packet.SCanvasSyncMessage;
import com.dantaeusb.zetter.network.packet.SEaselCanvasChangePacket;
import com.dantaeusb.zetter.network.packet.SPaintingSyncMessage;
import com.dantaeusb.zetter.storage.AbstractCanvasData;
import com.dantaeusb.zetter.storage.CanvasData;
import com.dantaeusb.zetter.storage.PaintingData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class ClientHandler {
    /**
     * Handle sync packet on client
     *
     * @param packetIn
     * @param ctxSupplier
     */
    public static void handleCanvasSync(final SCanvasSyncMessage packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.CLIENT) {
            Zetter.LOG.warn("SCanvasSyncMessage received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        Optional<ClientWorld> clientWorld = LogicalSidedProvider.CLIENTWORLD.get(sideReceived);
        if (!clientWorld.isPresent()) {
            Zetter.LOG.warn("SCanvasSyncMessage context could not provide a ClientWorld.");
            return;
        }

        ctx.enqueueWork(() -> processCanvasSync(packetIn, clientWorld.get()));
    }

    public static void processCanvasSync(final SCanvasSyncMessage packetIn, ClientWorld world) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        AbstractCanvasData canvasData = packetIn.getCanvasData();

        if (
                player.openContainer instanceof EaselContainer
                && ((EaselContainer) player.openContainer).isCanvasAvailable()
        ) {
            // If it's the same canvas player is editing
            if (canvasData.getName().equals(((EaselContainer) player.openContainer).getCanvasData().getName())) {
                // Pushing changes that were added after sync packet was created
                // @todo: remove cast
                ((EaselContainer) player.openContainer).processSync((CanvasData) canvasData, packetIn.getTimestamp());
            }
        }

        if  (player.openContainer instanceof ArtistTableContainer) {
            // If player's combining canvases
            // @todo: not sure if needed

            ((ArtistTableContainer) player.openContainer).updateCanvasCombination();
        }

        // Get overworld world instance
        ICanvasTracker canvasTracker = world.getCapability(CanvasTrackerCapability.CAPABILITY_CANVAS_TRACKER).orElse(null);

        if (canvasTracker == null) {
            Zetter.LOG.error("Cannot find world canvas capability");
            return;
        }

        canvasTracker.registerCanvasData(canvasData);
    }

    public static void handlePaintingSync(final SPaintingSyncMessage packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.CLIENT) {
            Zetter.LOG.warn("SCanvasSyncMessage received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        Optional<ClientWorld> clientWorld = LogicalSidedProvider.CLIENTWORLD.get(sideReceived);
        if (!clientWorld.isPresent()) {
            Zetter.LOG.warn("SCanvasSyncMessage context could not provide a ClientWorld.");
            return;
        }

        ctx.enqueueWork(() -> processPaintingSync(packetIn, clientWorld.get()));
    }

    public static void processPaintingSync(final SPaintingSyncMessage packetIn, ClientWorld world) {
        PaintingData canvasData = packetIn.getPaintingData();

        ICanvasTracker canvasTracker = Helper.getWorldCanvasTracker(world);

        if (canvasTracker == null) {
            Zetter.LOG.error("Cannot find world canvas capability");
            return;
        }

        canvasTracker.registerCanvasData(canvasData);
    }

    public static void handleEaselCanvasUpdate(final SEaselCanvasChangePacket packetIn, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        LogicalSide sideReceived = ctx.getDirection().getReceptionSide();
        ctx.setPacketHandled(true);

        if (sideReceived != LogicalSide.CLIENT) {
            Zetter.LOG.warn("SEaselCanvasChangePacket received on wrong side:" + ctx.getDirection().getReceptionSide());
            return;
        }

        Optional<ClientWorld> clientWorld = LogicalSidedProvider.CLIENTWORLD.get(sideReceived);
        if (!clientWorld.isPresent()) {
            Zetter.LOG.warn("SEaselCanvasChangePacket context could not provide a ClientWorld.");
            return;
        }

        ctx.enqueueWork(() -> processEaselCanvasUpdate(packetIn, clientWorld.get()));
    }

    public static void processEaselCanvasUpdate(final SEaselCanvasChangePacket packetIn, ClientWorld world) {
        ClientPlayerEntity player = Minecraft.getInstance().player;

        if (player.openContainer instanceof EaselContainer) {
            ((EaselContainer) player.openContainer).handleCanvasChange(packetIn.getItem());
        }
    }

    public static boolean isThisProtocolAcceptedByClient(String protocolVersion) {
        return ModNetwork.MESSAGE_PROTOCOL_VERSION.equals(protocolVersion);
    }
}
