package com.albedo.network;

import com.albedo.AlbedoMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Client→Server: 将 Litematica 投影中的方块数据发送给服务端。
 */
public record BuildDataPayload(int bossId, BlockPos origin, int sx, int sy, int sz,
                               List<BlockEntry> blocks) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuildDataPayload> TYPE =
            new CustomPacketPayload.Type<>(AlbedoMod.id("build_data"));

    public record BlockEntry(short rx, short ry, short rz, int stateId) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildDataPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), BuildDataPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(bossId);
        buf.writeBlockPos(origin);
        buf.writeVarInt(sx);
        buf.writeVarInt(sy);
        buf.writeVarInt(sz);
        buf.writeVarInt(blocks.size());
        for (BlockEntry e : blocks) {
            buf.writeShort(e.rx);
            buf.writeShort(e.ry);
            buf.writeShort(e.rz);
            buf.writeVarInt(e.stateId);
        }
    }

    private static BuildDataPayload read(RegistryFriendlyByteBuf buf) {
        int bossId = buf.readVarInt();
        BlockPos origin = buf.readBlockPos();
        int sx = buf.readVarInt();
        int sy = buf.readVarInt();
        int sz = buf.readVarInt();
        int count = buf.readVarInt();
        List<BlockEntry> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(new BlockEntry(buf.readShort(), buf.readShort(), buf.readShort(), buf.readVarInt()));
        }
        return new BuildDataPayload(bossId, origin, sx, sy, sz, blocks);
    }

    @Override
    public CustomPacketPayload.Type<BuildDataPayload> type() {
        return TYPE;
    }
}
