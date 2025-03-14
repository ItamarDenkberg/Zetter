package com.dantaeusb.zetter.network.packet;

import com.dantaeusb.zetter.Zetter;
import com.dantaeusb.zetter.storage.AbstractCanvasData;
import net.minecraft.network.PacketBuffer;

public class CUpdatePaintingPacket {
    private int windowId;
    private String paintingName;
    private AbstractCanvasData canvasData;

    public CUpdatePaintingPacket() {

    }

    public CUpdatePaintingPacket(int windowId, String paintingName, AbstractCanvasData canvasData) {
        this.windowId = windowId;
        this.paintingName = paintingName;
        this.canvasData = canvasData;
    }

    /**
     * Reads the raw packet data from the data stream.
     */
    public static CUpdatePaintingPacket readPacketData(PacketBuffer buf) {
        CUpdatePaintingPacket packet = new CUpdatePaintingPacket();

        try {
            packet.windowId = buf.readByte();
            packet.paintingName = buf.readString(32767);
            packet.canvasData = CanvasContainer.readPacketCanvasData(buf);
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            Zetter.LOG.warn("Exception while reading CCreatePaintingPacket: " + e);
            return packet;
        }

        return packet;
    }

    /**
     * Writes the raw packet data to the data stream.
     */
    public void writePacketData(PacketBuffer buf) {
        buf.writeByte(this.windowId);
        buf.writeString(this.paintingName);
        CanvasContainer.writePacketCanvasData(buf, this.canvasData);
    }

    public int getWindowId() {
        return this.windowId;
    }

    public String getPaintingName() {
        return this.paintingName;
    }

    public AbstractCanvasData getCanvasData() {
        return this.canvasData;
    }
}