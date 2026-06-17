package com.albedo.entity;

import com.albedo.AlbedoConfig;
import com.albedo.entity.ai.AxolotlMissileGoal;
import com.albedo.entity.ai.ChaseTargetGoal;
import com.albedo.entity.ai.WaterBoltGoal;
import com.albedo.entity.ai.WaterFieldGoal;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public class AxolotlMage extends Monster {
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(AxolotlMage.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CASTING =
            SynchedEntityData.defineId(AxolotlMage.class, EntityDataSerializers.BOOLEAN);

    private static final int STATE_IDLE = 0;
    private static final int STATE_CASTING = 1;
    private static final int STATE_HEALING = 2;

    private final Map<String, Integer> cooldowns = new HashMap<>();

    public AxolotlMage(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.WATER, 0.0f);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, AlbedoConfig.AXOLOTL_MAGE_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, AlbedoConfig.AXOLOTL_MAGE_ATTACK)
                .add(Attributes.ARMOR, AlbedoConfig.AXOLOTL_MAGE_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, AlbedoConfig.AXOLOTL_MAGE_SPEED)
                .add(Attributes.FOLLOW_RANGE, AlbedoConfig.AXOLOTL_MAGE_FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, STATE_IDLE);
        builder.define(DATA_CASTING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this) {
            @Override
            public boolean canUse() {
                return super.canUse() && !isCasting();
            }
        });
        this.goalSelector.addGoal(1, new AxolotlMissileGoal(this));
        this.goalSelector.addGoal(2, new WaterFieldGoal(this));
        this.goalSelector.addGoal(3, new WaterBoltGoal(this));
        this.goalSelector.addGoal(4, new ChaseTargetGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, true,
                (living, level) -> !(living instanceof AxolotlMage)
                        && !(living instanceof AlbedoBoss)));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!level().isClientSide()) {
            tickCooldowns();
            applyBuffAura();
            autoHeal();
            checkPlayDead();
            spawnAmbientParticles();
            spawnMagicCircle();
            spawnWaterTrail();
            spawnIdleRipple();
        }
    }

    // ===== Buff Aura =====
    private int buffTimer = 0;

    private void applyBuffAura() {
        buffTimer++;
        if (buffTimer < AlbedoConfig.AXOLOTL_MAGE_BUFF_INTERVAL) return;
        buffTimer = 0;

        // Don't buff while in combat
        if (getTarget() != null) return;

        AABB aura = getBoundingBox().inflate(AlbedoConfig.AXOLOTL_MAGE_BUFF_RADIUS);
        level().getEntitiesOfClass(Player.class, aura, p -> p.isAlive() && !p.isSpectator())
                .forEach(player -> {
                    player.addEffect(new MobEffectInstance(
                            MobEffects.WATER_BREATHING, 400, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(
                            MobEffects.DOLPHINS_GRACE, 200, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(
                            MobEffects.NIGHT_VISION, 400, 0, false, false, true));
                    player.addEffect(new MobEffectInstance(
                            MobEffects.REGENERATION, 100, 0, false, false, true));
                });
    }

    // ===== Auto Heal =====
    private int healTimer = 0;

    private void autoHeal() {
        healTimer++;
        if (healTimer < AlbedoConfig.AXOLOTL_MAGE_HEAL_INTERVAL) return;
        healTimer = 0;

        float hpRatio = getHealth() / getMaxHealth();
        if (hpRatio >= 0.9f && getTarget() == null) return; // Don't heal if healthy and not in combat

        setState(STATE_HEALING);

        // Self heal + water ripple
        if (getHealth() < getMaxHealth()) {
            heal(AlbedoConfig.AXOLOTL_MAGE_HEAL_AMOUNT);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART,
                        getX(), getY() + getBbHeight() + 0.5, getZ(),
                        3, 0.3, 0.2, 0.3, 0.05);
                // Healing water ripple
                for (int i = 0; i < 8; i++) {
                    double angle = i * 45 * Math.PI / 180;
                    sl.sendParticles(ParticleTypes.BUBBLE_POP,
                            getX() + Math.cos(angle) * 1.0,
                            getY() + 0.5,
                            getZ() + Math.sin(angle) * 1.0,
                            2, 0.2, 0.1, 0.2, 0.03);
                }
            }
        }

        // Heal nearby friendly entities
        AABB area = getBoundingBox().inflate(AlbedoConfig.AXOLOTL_MAGE_BUFF_RADIUS);
        level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e.isAlive()
                        && (e instanceof Player
                            || e instanceof AxolotlMage
                            || e instanceof AlbedoBoss
                            || (e instanceof TamableAnimal t && t.isTame()))
        ).forEach(entity -> {
            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(AlbedoConfig.AXOLOTL_MAGE_HEAL_AMOUNT);
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HEART,
                            entity.getX(), entity.getY() + entity.getBbHeight() + 0.5, entity.getZ(),
                            2, 0.2, 0.1, 0.2, 0.05);
                }
            }
        });

        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 1.2f + random.nextFloat() * 0.4f);

        setState(STATE_IDLE);
    }

    // ===== Ambient Particles =====
    private void spawnAmbientParticles() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (tickCount % 3 != 0) return;

        // Water droplets spiraling around body
        double yOff = getBbHeight() * 0.6;
        for (int i = 0; i < 2; i++) {
            double angle = (tickCount * 4 + i * 180) * Math.PI / 180;
            double r = 0.5;
            sl.sendParticles(ParticleTypes.FALLING_WATER,
                    getX() + Math.cos(angle) * r,
                    getY() + yOff + Math.sin(tickCount * 0.15) * 0.4,
                    getZ() + Math.sin(angle) * r,
                    1, 0, 0, 0, 0);
        }

        // Axolotl gill particles — pink/purple wisps around head
        for (int side = -1; side <= 1; side += 2) {
            double gillX = getX() + side * 0.35;
            double gillY = getY() + 1.6;
            double gillZ = getZ();
            sl.sendParticles(ParticleTypes.DUST_PLUME,
                    gillX, gillY + Math.sin(tickCount * 0.2) * 0.15, gillZ,
                    1, 0.08, 0.08, 0.08, 0);
        }

        // Underwater bubble effect
        if (isInWater() && tickCount % 5 == 0) {
            sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                    getX(), getY() + 0.5, getZ(),
                    1, 0.2, 0.1, 0.2, 0.02);
        }
    }

    // ===== Water Trail =====
    private void spawnWaterTrail() {
        if (!(level() instanceof ServerLevel sl)) return;
        // Only spawn trail when moving
        if (getDeltaMovement().horizontalDistance() < 0.05) return;
        if (tickCount % 2 != 0) return;

        for (int i = -1; i <= 1; i += 2) {
            sl.sendParticles(ParticleTypes.FALLING_WATER,
                    getX() + i * 0.25, getY() + 0.1, getZ(),
                    1, 0.05, 0, 0.05, 0);
        }
        sl.sendParticles(ParticleTypes.BUBBLE_POP,
                getX(), getY() + 0.15, getZ(),
                1, 0.1, 0.05, 0.1, 0.01);
    }

    // ===== Idle Water Ripple =====
    private void spawnIdleRipple() {
        if (!(level() instanceof ServerLevel sl)) return;
        if (getTarget() != null) return; // Don't ripple in combat
        if (tickCount % 60 != 0) return;

        for (int i = 0; i < 12; i++) {
            double angle = i * 30 * Math.PI / 180;
            sl.sendParticles(ParticleTypes.FALLING_WATER,
                    getX() + Math.cos(angle) * 1.2,
                    getY() + 0.05,
                    getZ() + Math.sin(angle) * 1.2,
                    1, 0, 0.02, 0, 0);
        }
    }

    // ===== Death Water Burst =====
    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) {
            // Expanding water rings
            for (int ring = 0; ring < 4; ring++) {
                double r = ring * 1.5 + 0.5;
                for (int i = 0; i < 24; i++) {
                    double angle = i * 15 * Math.PI / 180;
                    sl.sendParticles(ParticleTypes.SPLASH,
                            getX() + Math.cos(angle) * r,
                            getY() + 0.1 + ring * 0.3,
                            getZ() + Math.sin(angle) * r,
                            1, 0, 0, 0, 0);
                }
            }
            // Bubble burst
            sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                    getX(), getY() + 1, getZ(),
                    20, 1.5, 1.0, 1.5, 0.1);
            // Pink axolotl farewell
            sl.sendParticles(ParticleTypes.WITCH,
                    getX(), getY() + 1.5, getZ(),
                    15, 0.8, 0.6, 0.8, 0.05);
            // Heart burst
            sl.sendParticles(ParticleTypes.HEART,
                    getX(), getY() + getBbHeight(), getZ(),
                    8, 0.5, 0.3, 0.5, 0.08);
        }
        playSound(SoundEvents.AXOLOTL_DEATH, 1.0f, 0.7f);
        super.die(source);
    }

    // ===== Magic Circle =====
    private int castSoundTimer = 0;

    private void spawnMagicCircle() {
        if (!isCasting()) return;
        if (!(level() instanceof ServerLevel sl)) return;

        // Rotating ground circle — 3 rings
        int rings = 3;
        for (int r = 0; r < rings; r++) {
            double radius = 1.2 + r * 0.6;
            int dots = 16 + r * 8;
            double spinSpeed = (r % 2 == 0) ? 3.0 : -2.5;
            for (int i = 0; i < dots; i++) {
                double angle = (tickCount * spinSpeed + i * 360.0 / dots) * Math.PI / 180;
                double px = getX() + Math.cos(angle) * radius;
                double pz = getZ() + Math.sin(angle) * radius;
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        px, getY() + 0.05, pz,
                        1, 0, 0, 0, 0);
            }
        }

        // Inner runic ring — PORTAL
        for (int i = 0; i < 8; i++) {
            double angle = (tickCount * -2 + i * 45) * Math.PI / 180;
            sl.sendParticles(ParticleTypes.PORTAL,
                    getX() + Math.cos(angle) * 1.0,
                    getY() + 0.08,
                    getZ() + Math.sin(angle) * 1.0,
                    1, 0, 0, 0, 0);
        }

        // Axolotl gill wisps — pink particles floating up from head sides
        for (int side = -1; side <= 1; side += 2) {
            double gx = getX() + side * 0.4;
            double gy = getY() + 1.55 + Math.sin(tickCount * 0.25) * 0.2;
            double gz = getZ();
            sl.sendParticles(ParticleTypes.WITCH,
                    gx, gy, gz,
                    1, 0.1, 0.08, 0.1, 0.01);
        }

        // Rising bubbles from circle edge
        for (int i = 0; i < 4; i++) {
            double angle = (tickCount * 5 + i * 90) * Math.PI / 180;
            sl.sendParticles(ParticleTypes.BUBBLE_POP,
                    getX() + Math.cos(angle) * 2.0,
                    getY() + 0.3 + Math.sin(tickCount * 0.3 + i) * 0.5,
                    getZ() + Math.sin(angle) * 2.0,
                    1, 0.1, 0.05, 0.1, 0.03);
        }

        // Axolotl chirp while casting
        castSoundTimer++;
        if (castSoundTimer >= 20) {
            castSoundTimer = 0;
            playSound(SoundEvents.AXOLOTL_IDLE_AIR, 0.15f, 1.5f + random.nextFloat() * 0.5f);
        }
    }

    // ===== Play Dead (Axolotl style) =====
    private boolean playDeadTriggered = false;

    private void checkPlayDead() {
        if (playDeadTriggered) return;
        float hpRatio = getHealth() / getMaxHealth();
        if (hpRatio < 0.3f) {
            playDeadTriggered = true;
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1, false, false, true));
            addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0, false, false, true));
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART,
                        getX(), getY() + getBbHeight() + 0.5, getZ(),
                        10, 0.4, 0.3, 0.4, 0.08);
            }
            playSound(SoundEvents.AXOLOTL_HURT, 1.0f, 0.5f);
        }
    }

    // ===== Combat state handling =====
    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(target);
        if (target != null && getState() == STATE_IDLE) {
            playSound(SoundEvents.AXOLOTL_SPLASH, 0.6f, 1.0f);
            // Combat entry splash
            if (level() instanceof ServerLevel sl) {
                for (int i = 0; i < 3; i++) {
                    double angle = (i * 120) * Math.PI / 180;
                    sl.sendParticles(ParticleTypes.SPLASH,
                            getX() + Math.cos(angle) * 0.8,
                            getY() + 0.3,
                            getZ() + Math.sin(angle) * 0.8,
                            3, 0.2, 0.1, 0.2, 0.05);
                }
                sl.sendParticles(ParticleTypes.WITCH,
                        getX(), getY() + 1.5, getZ(),
                        5, 0.3, 0.2, 0.3, 0.02);
            }
        }
    }

    // ===== Water affinity =====
    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void baseTick() {
        super.baseTick();

        // Faster movement in water
        if (isInWater() && isEffectiveAi()) {
            setDeltaMovement(getDeltaMovement().scale(1.03));
        }
    }

    // ===== Sound overrides =====
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AXOLOTL_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AXOLOTL_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AXOLOTL_IDLE_WATER;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    // ===== Cooldown system =====
    private void tickCooldowns() {
        cooldowns.replaceAll((k, v) -> v > 0 ? v - 1 : 0);
    }

    public boolean isOnCooldown(String skill) {
        return cooldowns.getOrDefault(skill, 0) > 0;
    }

    public void setCooldown(String skill, int ticks) {
        cooldowns.put(skill, ticks);
    }

    // ===== Getters/Setters =====
    public int getState() {
        return entityData.get(DATA_STATE);
    }

    public void setState(int state) {
        entityData.set(DATA_STATE, state);
    }

    public boolean isCasting() {
        return entityData.get(DATA_CASTING);
    }

    public void setCasting(boolean casting) {
        entityData.set(DATA_CASTING, casting);
    }

}
