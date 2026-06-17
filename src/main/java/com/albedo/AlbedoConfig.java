package com.albedo;

public class AlbedoConfig {
    // Boss stats
    public static final double MAX_HEALTH = 400.0;
    public static final double BASE_ATTACK = 18.0;
    public static final double ARMOR = 40.0;
    public static final double MOVEMENT_SPEED = 0.32;
    public static final double KNOCKBACK_RESIST = 1.0;
    public static final int FOLLOW_RANGE = 48;

    // Phase thresholds
    public static final double PHASE2_HP = 250.0;
    public static final double PHASE3_HP = 130.0;

    // Attack cooldowns (ticks)
    public static final int SWEEP_COOLDOWN = 50;      // ~2.5s
    public static final int THRUST_COOLDOWN = 50;     // ~2.5s
    public static final int WAVE_COOLDOWN = 22;       // ~1.1s
    public static final int CLONE_COOLDOWN = 50;      // ~2.5s
    public static final int JUDGMENT_COOLDOWN = 60;   // ~3s

    // Build mode
    public static final int BUILD_COOLDOWN = 100;        // 建造冷却 (ticks)
    public static final int BUILD_BLOCKS_PER_TICK = 3;   // 每 tick 放置方块数
    public static final int BUILD_WARMUP = 20;            // 准备阶段 ticks
    public static final int BUILD_RANGE = 40;             // 建造时搜寻投影的范围

    // Follow mode
    public static final double TELEPORT_DISTANCE = 15.0;
    public static final double FOLLOW_DISTANCE = 6.0;
    public static final float SWEEP_DAMAGE = 16.0f;
    public static final float THRUST_DAMAGE = 22.0f;
    public static final float WAVE_DAMAGE = 12.0f;
    public static final float JUDGMENT_DAMAGE = 25.0f;

    // Axolotl Mage
    public static final double AXOLOTL_MAGE_HEALTH = 60.0;
    public static final double AXOLOTL_MAGE_ATTACK = 6.0;
    public static final double AXOLOTL_MAGE_ARMOR = 8.0;
    public static final double AXOLOTL_MAGE_SPEED = 0.28;
    public static final int AXOLOTL_MAGE_FOLLOW_RANGE = 32;
    public static final int AXOLOTL_MAGE_BUFF_INTERVAL = 60;
    public static final int AXOLOTL_MAGE_BUFF_RADIUS = 16;
    public static final int AXOLOTL_MAGE_HEAL_INTERVAL = 80;
    public static final float AXOLOTL_MAGE_HEAL_AMOUNT = 4.0f;
    public static final int WATER_BOLT_COOLDOWN = 20;
    public static final float WATER_BOLT_DAMAGE = 8.0f;
    public static final int WATER_FIELD_COOLDOWN = 80;
    public static final float WATER_FIELD_DAMAGE = 8.0f;
    public static final int AXOLOTL_MISSILE_COOLDOWN = 200;
    public static final float AXOLOTL_MISSILE_DAMAGE = 18.0f;
    public static final float AXOLOTL_MISSILE_SPLASH_DAMAGE = 8.0f;
}
