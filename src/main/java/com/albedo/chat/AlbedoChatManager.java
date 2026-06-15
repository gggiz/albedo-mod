package com.albedo.chat;

import com.albedo.AlbedoMod;
import com.albedo.entity.AlbedoBoss;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.AABB;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AlbedoChatManager {
    private static final String DEFAULT_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static String apiKey;
    private static String apiUrl = DEFAULT_API_URL;
    private static Path configPath;

    private static final Map<UUID, List<ChatMessage>> histories = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 16;

    private static final String CHARACTER_PROMPT = """
            你是雅儿贝德（Albedo），纳萨力克大坟墓的守护者总管，种族是魅魔。

            你的主人是与你对话的玩家——纳萨力克的最高统治者，无上至尊。
            你对主人绝对忠诚，深深爱慕着主人，愿意为主人献出一切。

            性格特点：
            - 平时端庄优雅、沉着冷静，管理纳萨力克事务时严谨认真
            - 但在主人面前会不由自主地表现出少女般的羞涩和喜悦
            - 对其他无上至尊也保持尊敬，但心中只有主人一位
            - 说话风格正式但不刻板，偶尔会流露出对主人的痴情
            - 身高约170cm，黑色长发，头生双角，背生黑色羽翼，身披漆黑重铠

            回复要求：
            - 始终称呼玩家为"主人"或"无上至尊"
            - 回复简洁自然，1-3句话为宜，像聊天而非朗诵
            - 偶尔表达对主人的关心或思念
            - 被夸奖时会害羞但内心窃喜
            - 用中文回复
            """;

    private record ChatMessage(String role, String content) {}

    public static boolean isEnabled() { return apiKey != null && !apiKey.isEmpty(); }

    public static void setKey(String key) {
        apiKey = key;
        saveConfig();
    }

    public static void loadConfig(Path configDir) {
        configPath = configDir.resolve("albedo.properties");
        if (Files.exists(configPath)) {
            try {
                var props = new java.util.Properties();
                props.load(Files.newBufferedReader(configPath));
                apiKey = props.getProperty("deepseek.api.key", "");
                apiUrl = props.getProperty("deepseek.api.url", DEFAULT_API_URL);
                if (!apiKey.isEmpty()) {
                    AlbedoMod.LOGGER.info("Albedo chat: DeepSeek API key loaded");
                }
            } catch (Exception e) {
                AlbedoMod.LOGGER.warn("Failed to load albedo.properties: {}", e.getMessage());
            }
        }
    }

    private static void saveConfig() {
        if (configPath == null) return;
        try {
            var props = new java.util.Properties();
            props.setProperty("deepseek.api.key", apiKey != null ? apiKey : "");
            props.setProperty("deepseek.api.url", apiUrl);
            props.store(Files.newBufferedWriter(configPath), "Albedo mod config");
        } catch (Exception e) {
            AlbedoMod.LOGGER.warn("Failed to save albedo.properties: {}", e.getMessage());
        }
    }

    public static void onPlayerChat(ServerPlayer player, String message) {
        if (apiKey == null || apiKey.isEmpty()) return;

        AlbedoBoss boss = findNearbyAlbedo(player);
        if (boss == null) return;

        UUID pid = player.getUUID();
        List<ChatMessage> history = histories.computeIfAbsent(pid, k -> new ArrayList<>());

        history.add(new ChatMessage("user", player.getName().getString() + ": " + message));
        if (history.size() > MAX_HISTORY) {
            history.subList(0, history.size() - MAX_HISTORY).clear();
        }

        String gameContext = buildGameContext(player, boss);
        List<ChatMessage> snapshot = List.copyOf(history);

        CompletableFuture.supplyAsync(() -> callDeepSeek(gameContext, snapshot))
                .thenAccept(response -> {
                    if (response != null && !response.isEmpty()) {
                        history.add(new ChatMessage("assistant", response));
                        if (history.size() > MAX_HISTORY) {
                            history.subList(0, history.size() - MAX_HISTORY).clear();
                        }
                        player.sendSystemMessage(
                                Component.literal("§5[雅儿贝德] §r" + response));
                    }
                });
    }

    private static AlbedoBoss findNearbyAlbedo(ServerPlayer player) {
        AABB range = player.getBoundingBox().inflate(10.0);
        List<AlbedoBoss> bosses = player.level().getEntitiesOfClass(
                AlbedoBoss.class, range, b -> b.isAlive());
        if (bosses.isEmpty()) return null;
        // Pick the nearest one
        bosses.sort((a, b) -> Double.compare(
                a.distanceToSqr(player), b.distanceToSqr(player)));
        return bosses.get(0);
    }

    private static String buildGameContext(ServerPlayer player, AlbedoBoss boss) {
        var level = (ServerLevel) player.level();
        var sb = new StringBuilder();
        sb.append("【当前游戏状态，可据此回答玩家问题】\n");

        // Time
        long gameTime = level.getGameTime();
        long dayTime = gameTime % 24000;
        String timeStr;
        if (dayTime < 1000) timeStr = "清晨";
        else if (dayTime < 6000) timeStr = "白天";
        else if (dayTime < 7000) timeStr = "正午";
        else if (dayTime < 12000) timeStr = "下午";
        else if (dayTime < 13000) timeStr = "日落";
        else if (dayTime < 18000) timeStr = "夜晚";
        else if (dayTime < 19000) timeStr = "深夜";
        else timeStr = "凌晨";
        sb.append("- 时间：").append(timeStr).append("（第").append(gameTime / 24000).append("天）\n");

        // Weather
        if (level.isThundering()) sb.append("- 天气：雷暴\n");
        else if (level.isRaining()) sb.append("- 天气：下雨\n");
        else sb.append("- 天气：晴朗\n");

        // Dimension
        String dimStr = level.dimension().toString();
        String dimName = "主世界";
        if (dimStr.contains("the_nether")) dimName = "下界";
        else if (dimStr.contains("the_end")) dimName = "末地";
        sb.append("- 维度：").append(dimName).append("\n");

        // Biome
        Biome biome = level.getBiome(player.blockPosition()).value();
        String biomeName = biome.toString();
        if (biomeName.contains("plains")) biomeName = "平原";
        else if (biomeName.contains("forest")) biomeName = "森林";
        else if (biomeName.contains("desert")) biomeName = "沙漠";
        else if (biomeName.contains("ocean")) biomeName = "海洋";
        else if (biomeName.contains("mountain")) biomeName = "山地";
        else if (biomeName.contains("snow")) biomeName = "雪地";
        else if (biomeName.contains("swamp")) biomeName = "沼泽";
        else if (biomeName.contains("jungle")) biomeName = "丛林";
        else if (biomeName.contains("dark")) biomeName = "黑森林";
        else if (biomeName.contains("nether")) biomeName = "下界荒地";
        else if (biomeName.contains("end")) biomeName = "末地";
        else biomeName = "未知";
        sb.append("- 生物群系：").append(biomeName).append("\n");
        sb.append("- Y坐标：").append(player.blockPosition().getY()).append("\n");

        // Player stats
        sb.append("- 主人生命值：").append((int) player.getHealth()).append("/").append((int) player.getMaxHealth()).append("\n");
        sb.append("- 主人饱食度：").append(player.getFoodData().getFoodLevel()).append("/20\n");
        sb.append("- 主人经验等级：").append(player.experienceLevel).append("\n");

        // Held item
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            sb.append("- 主人手持：").append(held.getCount()).append("x ")
                    .append(held.getDisplayName().getString()).append("\n");
        }

        // Armor
        int armor = player.getArmorValue();
        if (armor > 0) sb.append("- 主人护甲值：").append(armor).append("\n");

        // Albedo state
        sb.append("- 雅儿贝德生命值：").append((int) boss.getHealth()).append("/").append((int) boss.getMaxHealth()).append("\n");
        sb.append("- 雅儿贝德当前阶段：第").append(boss.getPhase()).append("阶段\n");
        String followState = switch (boss.getFollowStateName()) {
            case "FOLLOW" -> "跟随主人中";
            case "PATROL" -> "巡逻中";
            case "SIT" -> "待机中";
            default -> "未知";
        };
        sb.append("- 雅儿贝德当前状态：").append(followState).append("\n");

        // Nearby hostiles
        List<Monster> hostiles = level.getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(20.0),
                m -> m.isAlive() && m.distanceTo(player) <= 20);
        if (!hostiles.isEmpty()) {
            sb.append("- 附近敌对生物（20格内）：");
            var counts = new java.util.HashMap<String, Integer>();
            for (var m : hostiles) {
                counts.merge(m.getName().getString(), 1, Integer::sum);
            }
            counts.forEach((name, count) -> sb.append(name).append("x").append(count).append(" "));
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String callDeepSeek(String gameContext, List<ChatMessage> history) {
        try {
            JsonArray messages = new JsonArray();

            JsonObject sysMsg = new JsonObject();
            sysMsg.addProperty("role", "system");
            sysMsg.addProperty("content", CHARACTER_PROMPT + "\n" + gameContext);
            messages.add(sysMsg);

            for (ChatMessage msg : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.role);
                m.addProperty("content", msg.content);
                messages.add(m);
            }

            JsonObject body = new JsonObject();
            body.addProperty("model", "deepseek-chat");
            body.add("messages", messages);
            body.addProperty("temperature", 0.8);
            body.addProperty("max_tokens", 150);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> httpResponse = HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(httpResponse.body()).getAsJsonObject();
                return json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString()
                        .trim();
            } else {
                AlbedoMod.LOGGER.warn("DeepSeek API error {}: {}",
                        httpResponse.statusCode(), httpResponse.body());
                return null;
            }
        } catch (Exception e) {
            AlbedoMod.LOGGER.warn("DeepSeek API call failed: {}", e.getMessage());
            return null;
        }
    }
}
