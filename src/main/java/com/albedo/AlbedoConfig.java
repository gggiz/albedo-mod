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

    // Follow mode
    public static final double TELEPORT_DISTANCE = 15.0;
    public static final double FOLLOW_DISTANCE = 6.0;
    public static final float SWEEP_DAMAGE = 16.0f;
    public static final float THRUST_DAMAGE = 22.0f;
    public static final float WAVE_DAMAGE = 12.0f;
    public static final float JUDGMENT_DAMAGE = 25.0f;
}
