package com.albedo;

import com.albedo.chat.AlbedoChatManager;
import com.albedo.entity.AlbedoBoss;
import com.albedo.entity.AxolotlMage;
import com.albedo.item.AlbedoItems;
import com.albedo.network.BuildDataPayload;
import com.albedo.sound.AlbedoSounds;
import net.fabricmc.api.ModInitializer;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlbedoMod implements ModInitializer {
    public static final String MOD_ID = "albedo";
    public static final Logger LOGGER = LoggerFactory.getLogger("Albedo");

    public static final EntityType<AlbedoBoss> ALBEDO = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceKey.create(Registries.ENTITY_TYPE, id("albedo")).identifier(),
            EntityType.Builder.of(AlbedoBoss::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.3f)
                    .eyeHeight(2.05f)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("albedo")))
    );

    public static final EntityType<AxolotlMage> AXOLOTL_MAGE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceKey.create(Registries.ENTITY_TYPE, id("axolotl_mage")).identifier(),
            EntityType.Builder.of(AxolotlMage::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.75f)
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("axolotl_mage")))
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Albedo - Guardian Overseer initializing...");
        AlbedoItems.init();
        AlbedoSounds.init();
        FabricDefaultAttributeRegistry.register(ALBEDO, AlbedoBoss.createAttributes());
        FabricDefaultAttributeRegistry.register(AXOLOTL_MAGE, AxolotlMage.createAttributes());

        BiomeModifications.addSpawn(
                BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_OCEAN),
                MobCategory.CREATURE, AXOLOTL_MAGE, 10, 1, 2);
        BiomeModifications.addSpawn(
                BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_RIVER),
                MobCategory.CREATURE, AXOLOTL_MAGE, 8, 1, 2);
        BiomeModifications.addSpawn(
                BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_BEACH),
                MobCategory.CREATURE, AXOLOTL_MAGE, 5, 1, 1);
        BiomeModifications.addSpawn(
                BiomeSelectors.tag(net.minecraft.tags.BiomeTags.HAS_SWAMP_HUT),
                MobCategory.CREATURE, AXOLOTL_MAGE, 6, 1, 2);
        BiomeModifications.addSpawn(
                BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_JUNGLE),
                MobCategory.CREATURE, AXOLOTL_MAGE, 3, 1, 1);

        AlbedoChatManager.loadConfig(FabricLoader.getInstance().getConfigDir());
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                AlbedoChatManager.onPlayerChat(sender, message.signedContent()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("albedokey")
                        .then(net.minecraft.commands.Commands.argument("key", StringArgumentType.string())
                                .executes(ctx -> {
                                    String key = StringArgumentType.getString(ctx, "key");
                                    AlbedoChatManager.setKey(key);
                                    ctx.getSource().sendSystemMessage(
                                            Component.literal("§5[雅儿贝德] §aAPI Key 已保存。"));
                                    return 1;
                                }))
                        .executes(ctx -> {
                            boolean on = AlbedoChatManager.isEnabled();
                            ctx.getSource().sendSystemMessage(
                                    Component.literal(on ? "§5[雅儿贝德] §aAI 聊天已启用。" : "§5[雅儿贝德] §cAI 聊天未启用，使用 /albedokey <key> 设置 API Key。"));
                            return 1;
                        })
                ));

        // 注册网络包：客户端 → 服务端 发送投影数据
        PayloadTypeRegistry.serverboundPlay().register(BuildDataPayload.TYPE, BuildDataPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BuildDataPayload.TYPE, (BuildDataPayload payload, ServerPlayNetworking.Context context) -> {
            ServerLevel level = (ServerLevel) context.player().level();
            Entity entity = level.getEntity(payload.bossId());
            if (entity instanceof AlbedoBoss boss && boss.isBuilding()) {
                boss.receiveBuildData(payload);
                LOGGER.info("收到建造数据: {} 个方块, 原点={}", payload.blocks().size(), payload.origin());
            } else {
                LOGGER.warn("收到建造数据但boss不在建造状态: entity={}, isBuilding={}",
                        entity != null, entity instanceof AlbedoBoss b && b.isBuilding());
            }
        });

        LOGGER.info("Albedo boss ready.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
