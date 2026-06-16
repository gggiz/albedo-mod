package com.albedo;

import com.albedo.entity.AlbedoBoss;
import com.albedo.network.BuildDataPayload;
import com.albedo.util.LitematicaBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlbedoClientMod implements ClientModInitializer {
    private static final Map<Integer, Integer> RETRY_COOLDOWN = new HashMap<>();
    private static final int RETRY_INTERVAL = 60; // 失败后每3秒重试
    private static int logTick = 0;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AlbedoMod.ALBEDO, com.albedo.entity.AlbedoRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) return;

            logTick++;
            for (var entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof AlbedoBoss boss)) continue;

                int bossId = boss.getId();
                int stateOrdinal = boss.getFollowStateOrdinal();

                if (logTick % 200 == 0) {
                    AlbedoMod.LOGGER.info("客户端检测到Boss#{}: followStateOrdinal={}, buildProgress={}",
                            bossId, stateOrdinal, boss.getBuildProgress());
                }

                if (stateOrdinal != 3) {
                    RETRY_COOLDOWN.remove(bossId);
                    continue;
                }

                // 服务器已收到数据，不需要再发
                if (boss.getBuildProgress() != 0) continue;

                // 失败后冷却重试，避免刷屏
                int cd = RETRY_COOLDOWN.getOrDefault(bossId, 0);
                if (cd > 0) {
                    RETRY_COOLDOWN.put(bossId, cd - 1);
                    continue;
                }

                // 读取 Litematica 投影数据
                AlbedoMod.LOGGER.info("检测到boss#{}进入建造模式，尝试读取投影...", bossId);
                if (!LitematicaBridge.isAvailable()) {
                    AlbedoMod.LOGGER.warn("Litematica未安装或未加载，无法建造");
                    RETRY_COOLDOWN.put(bossId, RETRY_INTERVAL);
                    continue;
                }

                Level schematicWorld = LitematicaBridge.getSchematicWorld();
                if (schematicWorld == null) {
                    AlbedoMod.LOGGER.warn("无法获取投影世界，请确认已加载投影");
                    RETRY_COOLDOWN.put(bossId, RETRY_INTERVAL);
                    continue;
                }

                AABB bounds = LitematicaBridge.getPlacementBounds();
                if (bounds == null) {
                    AlbedoMod.LOGGER.warn("无法获取投影边界，请确认已选择投影");
                    RETRY_COOLDOWN.put(bossId, RETRY_INTERVAL);
                    continue;
                }

                int minX = (int) bounds.minX;
                int minY = (int) bounds.minY;
                int minZ = (int) bounds.minZ;
                int maxX = (int) bounds.maxX;
                int maxY = (int) bounds.maxY;
                int maxZ = (int) bounds.maxZ;

                int sx = maxX - minX;
                int sy = maxY - minY;
                int sz = maxZ - minZ;

                long volume = (long) sx * sy * sz;
                if (volume > 200000) {
                    AlbedoMod.LOGGER.warn("投影体积过大 ({} 方块)，跳过建造", volume);
                    RETRY_COOLDOWN.put(bossId, RETRY_INTERVAL);
                    continue;
                }

                List<BuildDataPayload.BlockEntry> blocks = new ArrayList<>();
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        for (int x = minX; x < maxX; x++) {
                            cursor.set(x, y, z);
                            BlockState state = schematicWorld.getBlockState(cursor);
                            if (!state.isAir()) {
                                int stateId = Block.getId(state);
                                blocks.add(new BuildDataPayload.BlockEntry(
                                        (short) (x - minX), (short) (y - minY), (short) (z - minZ),
                                        stateId));
                            }
                        }
                    }
                }

                if (blocks.isEmpty()) {
                    AlbedoMod.LOGGER.warn("投影中没有非空气方块，跳过建造");
                    RETRY_COOLDOWN.put(bossId, RETRY_INTERVAL);
                    continue;
                }

                if (!ClientPlayNetworking.canSend(BuildDataPayload.TYPE)) {
                    AlbedoMod.LOGGER.error("无法发送建造数据包：PayLoadType 未在客户端注册！");
                    RETRY_COOLDOWN.put(bossId, RETRY_INTERVAL);
                    continue;
                }

                BuildDataPayload payload = new BuildDataPayload(
                        bossId, new BlockPos(minX, minY, minZ), sx, sy, sz, blocks);
                ClientPlayNetworking.send(payload);
                AlbedoMod.LOGGER.info("已发送建造数据: {} 个方块 → boss #{}", blocks.size(), bossId);
            }
        });
    }
}
