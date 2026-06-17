package com.albedo.entity.ai;

import com.albedo.AlbedoConfig;
import com.albedo.entity.AxolotlMage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class AxolotlMissileGoal extends Goal {
    private final AxolotlMage mage;
    private int tick = 0;
    private int orbitTick = 0;
    private int finaleTick = 0;
    private Vec3 missileDir = Vec3.ZERO;
    private Vec3 missilePos = Vec3.ZERO;
    private LivingEntity orbitTarget = null;
    private Vec3 orbitCenter = Vec3.ZERO;
    private Axolotl missileAxolotl = null;
    private final List<Axolotl> orbitAxolotls = new ArrayList<>();

    private static final int WARMUP_TICKS = 12;
    private static final int MAX_TRAVEL_TICKS = 20;
    private static final int ORBIT_TICKS = 60;
    private static final int FINALE_TICKS = 12;
    private static final double MISSILE_SPEED = 1.2;
    private static final double HIT_RANGE = 2.0;
    private static final double ORBIT_RADIUS = 2.5;

    public AxolotlMissileGoal(AxolotlMage mage) {
        this.mage = mage;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mage.getTarget();
        return target != null
                && target.isAlive()
                && mage.getState() == 0
                && !mage.isCasting()
                && mage.distanceTo(target) >= 3.0f
                && mage.distanceTo(target) <= 20.0f
                && !mage.isOnCooldown("axolotl_missile");
    }

    @Override
    public void start() {
        tick = 0;
        orbitTick = 0;
        finaleTick = 0;
        orbitTarget = null;
        missileAxolotl = null;
        orbitAxolotls.clear();
        mage.setCasting(true);
        mage.setState(1);
        mage.setCooldown("axolotl_missile", AlbedoConfig.AXOLOTL_MISSILE_COOLDOWN);
        mage.playSound(SoundEvents.AXOLOTL_ATTACK, 0.8f, 0.7f);
        mage.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 0.6f, 1.0f);

        LivingEntity target = mage.getTarget();
        if (target != null) {
            missileDir = target.position().add(0, target.getBbHeight() * 0.5, 0)
                    .subtract(mage.position().add(0, 1.5, 0)).normalize();
        }

        // Spawn the missile axolotl
        if (mage.level() instanceof ServerLevel level) {
            Axolotl axo = EntityType.AXOLOTL.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (axo != null) {
                axo.setNoAi(true);
                axo.setInvulnerable(true);
                axo.setSilent(true);
                axo.setBaby(false);
                Vec3 start = mage.position().add(0, 1.5, 0);
                axo.teleportTo(start.x, start.y, start.z);
                axo.setYRot((float) Math.toDegrees(Math.atan2(-missileDir.x, missileDir.z)));
                level.addFreshEntity(axo);
                missileAxolotl = axo;
            }
        }
    }

    @Override
    public void tick() {
        tick++;

        if (tick <= WARMUP_TICKS) {
            tickWarmup();
        } else if (orbitTarget == null) {
            tickTravel();
        } else if (orbitTick < ORBIT_TICKS) {
            tickOrbit();
        } else if (finaleTick < FINALE_TICKS) {
            tickFinale();
        }
    }

    private void tickWarmup() {
        LivingEntity target = mage.getTarget();
        if (target != null) {
            missileDir = target.position().add(0, target.getBbHeight() * 0.5, 0)
                    .subtract(mage.position().add(0, 1.5, 0)).normalize();
            mage.getLookControl().setLookAt(target, 30f, 30f);
        }

        if (!(mage.level() instanceof ServerLevel level)) return;

        Vec3 hand = mage.position().add(0, 1.5, 0).add(missileDir.scale(1.0));
        for (int i = 0; i < 3; i++) {
            double angle = (tick * 10 + i * 120) * Math.PI / 180;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    hand.x + Math.cos(angle) * 0.3,
                    hand.y + Math.sin(tick * 0.3) * 0.2,
                    hand.z + Math.sin(angle) * 0.3,
                    1, 0.05, 0.05, 0.05, 0.02);
        }
        level.sendParticles(ParticleTypes.BUBBLE_POP,
                hand.x, hand.y + 0.3, hand.z,
                1, 0.1, 0.05, 0.1, 0.02);

        if (tick % 6 == 0) {
            mage.playSound(SoundEvents.AXOLOTL_IDLE_AIR, 0.2f, 1.8f + mage.getRandom().nextFloat() * 0.4f);
        }
    }

    private void tickTravel() {
        int travelTick = tick - WARMUP_TICKS;

        if (travelTick > MAX_TRAVEL_TICKS) {
            cleanupMissile();
            return;
        }

        double dist = travelTick * MISSILE_SPEED;
        missilePos = mage.position().add(0, 1.5, 0).add(missileDir.scale(dist));

        if (!(mage.level() instanceof ServerLevel level)) return;

        // Move the missile axolotl
        if (missileAxolotl != null && missileAxolotl.isAlive()) {
            missileAxolotl.teleportTo(missilePos.x, missilePos.y, missilePos.z);
            missileAxolotl.setDeltaMovement(missileDir.scale(MISSILE_SPEED));
            float yaw = (float) Math.toDegrees(Math.atan2(-missileDir.x, missileDir.z));
            missileAxolotl.setYRot(yaw);
        }

        // Water trail behind missile
        level.sendParticles(ParticleTypes.FALLING_WATER,
                missilePos.x - missileDir.x * 0.8, missilePos.y, missilePos.z - missileDir.z * 0.8,
                2, 0.1, 0.05, 0.1, 0);
        level.sendParticles(ParticleTypes.BUBBLE,
                missilePos.x, missilePos.y + 0.2, missilePos.z,
                1, 0.15, 0.05, 0.15, 0.01);

        // Hit detection
        level.getEntities(mage,
                AABB.ofSize(missilePos, HIT_RANGE, HIT_RANGE, HIT_RANGE),
                e -> e instanceof LivingEntity
                        && e != mage
                        && !(e instanceof AxolotlMage)
                        && !(e instanceof com.albedo.entity.AlbedoBoss)
                        && !(e instanceof Axolotl)
        ).stream().findFirst().ifPresent(entity -> {
            if (entity instanceof LivingEntity le) {
                orbitTarget = le;
                orbitCenter = le.position();
                orbitTick = 0;

                // Remove missile, spawn 2 orbiting axolotls
                cleanupMissile();

                if (level instanceof ServerLevel sl) {
                    for (int i = 0; i < 2; i++) {
                        Axolotl axo = EntityType.AXOLOTL.create(sl, EntitySpawnReason.MOB_SUMMONED);
                        if (axo != null) {
                            axo.setNoAi(true);
                            axo.setInvulnerable(true);
                            axo.setSilent(true);
                            axo.setBaby(false);
                            double angle = i * Math.PI;
                            axo.teleportTo(
                                    le.getX() + Math.cos(angle) * ORBIT_RADIUS,
                                    le.getY() + 0.5,
                                    le.getZ() + Math.sin(angle) * ORBIT_RADIUS
                            );
                            sl.addFreshEntity(axo);
                            orbitAxolotls.add(axo);
                        }
                    }
                }

                // Hit effects
                level.sendParticles(ParticleTypes.SPLASH,
                        missilePos.x, missilePos.y, missilePos.z,
                        12, 0.5, 0.5, 0.5, 0.1);
                level.sendParticles(ParticleTypes.BUBBLE_POP,
                        missilePos.x, missilePos.y + 0.5, missilePos.z,
                        8, 0.4, 0.3, 0.4, 0.08);
                mage.playSound(SoundEvents.AXOLOTL_HURT, 0.6f, 0.8f);
                mage.playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.5f, 0.6f);
            }
        });
    }

    private void tickOrbit() {
        orbitTick++;

        if (orbitTarget != null && orbitTarget.isAlive()) {
            orbitCenter = orbitTarget.position();
        }

        double baseAngle = orbitTick * 12 * Math.PI / 180;

        // Move orbiting axolotls
        for (int i = 0; i < orbitAxolotls.size(); i++) {
            Axolotl axo = orbitAxolotls.get(i);
            if (axo != null && axo.isAlive()) {
                double angle = baseAngle + i * Math.PI;
                double ox = orbitCenter.x + Math.cos(angle) * ORBIT_RADIUS;
                double oy = orbitCenter.y + 0.5 + Math.sin(orbitTick * 0.2 + i) * 0.5;
                double oz = orbitCenter.z + Math.sin(angle) * ORBIT_RADIUS;
                axo.teleportTo(ox, oy, oz);
                float yaw = (float) Math.toDegrees(angle + Math.PI / 2);
                axo.setYRot(yaw);
            }
        }

        if (!(mage.level() instanceof ServerLevel level)) return;

        // Orbiting ring bubbles
        for (int i = 0; i < 6; i++) {
            double ringAngle = (orbitTick * 8 + i * 60) * Math.PI / 180;
            level.sendParticles(ParticleTypes.BUBBLE,
                    orbitCenter.x + Math.cos(ringAngle) * ORBIT_RADIUS * 0.7,
                    orbitCenter.y + 0.3,
                    orbitCenter.z + Math.sin(ringAngle) * ORBIT_RADIUS * 0.7,
                    1, 0.05, 0.05, 0.05, 0.01);
        }

        if (orbitTick % 20 == 0) {
            mage.playSound(SoundEvents.AXOLOTL_IDLE_WATER, 0.2f, 1.5f + mage.getRandom().nextFloat() * 0.5f);
        }

        // Speed up if target died
        if (orbitTarget != null && !orbitTarget.isAlive() && orbitTick > 20) {
            orbitTick = ORBIT_TICKS;
        }
    }

    private void tickFinale() {
        finaleTick++;

        if (!(mage.level() instanceof ServerLevel level)) return;

        if (finaleTick == 1) {
            // Remove orbiting axolotls
            for (Axolotl axo : orbitAxolotls) {
                if (axo != null && axo.isAlive()) {
                    axo.discard();
                }
            }
            orbitAxolotls.clear();

            mage.playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.5f);
            mage.playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.7f, 0.4f);
        }

        // Sky-piercing water geyser — shoots upward 25+ blocks
        double pillarHeight = 25.0 * Math.min(1.0, finaleTick / (FINALE_TICKS * 0.4));
        double vx = 0;
        double vy = 1.8;
        double vz = 0;

        // Dense core water jet blasting upward
        for (int y = 0; y < pillarHeight * 2; y++) {
            double py = orbitCenter.y + y * 0.65;
            int count = y < pillarHeight * 0.5 ? 5 : 3;
            double spread = 0.15 + y * 0.015;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    orbitCenter.x + mage.getRandom().nextGaussian() * spread,
                    py,
                    orbitCenter.z + mage.getRandom().nextGaussian() * spread,
                    count, vx, vy * 0.5, vz, 0.02);
        }

        // Outer spray column
        for (int y = 0; y < pillarHeight; y++) {
            double py = orbitCenter.y + y * 0.7;
            double spread = 0.5 + y * 0.03;
            level.sendParticles(ParticleTypes.FALLING_WATER,
                    orbitCenter.x + mage.getRandom().nextGaussian() * spread,
                    py,
                    orbitCenter.z + mage.getRandom().nextGaussian() * spread,
                    2, vx, vy * 0.7, vz, 0.01);
        }

        // Bubbles racing up the core
        for (int y = 0; y < pillarHeight * 1.5; y++) {
            double py = orbitCenter.y + y * 0.55;
            level.sendParticles(ParticleTypes.BUBBLE_POP,
                    orbitCenter.x + mage.getRandom().nextGaussian() * 0.2,
                    py,
                    orbitCenter.z + mage.getRandom().nextGaussian() * 0.2,
                    2, 0, 1.5, 0, 0.03);
        }

        // Massive splash burst at the top
        double topY = orbitCenter.y + pillarHeight;
        level.sendParticles(ParticleTypes.SPLASH,
                orbitCenter.x, topY, orbitCenter.z,
                20, 1.5, 0.8, 1.5, 0.15);
        level.sendParticles(ParticleTypes.FALLING_WATER,
                orbitCenter.x, topY, orbitCenter.z,
                15, 2.0, 0.5, 2.0, 0.08);

        // Expanding water ring
        double ringR = finaleTick * 0.5;
        for (int i = 0; i < 16; i++) {
            double a = i * 22.5 * Math.PI / 180;
            level.sendParticles(ParticleTypes.SPLASH,
                    orbitCenter.x + Math.cos(a) * ringR,
                    orbitCenter.y + 0.1,
                    orbitCenter.z + Math.sin(a) * ringR,
                    1, 0.1, 0.05, 0.1, 0.02);
        }

        // Bubble burst
        level.sendParticles(ParticleTypes.BUBBLE_POP,
                orbitCenter.x, orbitCenter.y + 1.5, orbitCenter.z,
                4, 0.8, 0.5, 0.8, 0.05);

        if (finaleTick == 1) {
            // Damage primary target
            if (orbitTarget != null && orbitTarget.isAlive()) {
                orbitTarget.hurt(mage.damageSources().magic(),
                        AlbedoConfig.AXOLOTL_MISSILE_DAMAGE);
            }
            // Splash damage
            level.getEntities(mage,
                    AABB.ofSize(orbitCenter, 6.0, 4.0, 6.0),
                    e -> e instanceof LivingEntity
                            && e != mage
                            && e != orbitTarget
                            && !(e instanceof AxolotlMage)
                            && !(e instanceof com.albedo.entity.AlbedoBoss)
                            && !(e instanceof Axolotl)
            ).forEach(entity -> {
                entity.hurt(mage.damageSources().magic(),
                        AlbedoConfig.AXOLOTL_MISSILE_SPLASH_DAMAGE);
            });

            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    orbitCenter.x, orbitCenter.y + 1, orbitCenter.z,
                    2, 1.5, 1.0, 1.5, 0.5);
        }
    }

    private void cleanupMissile() {
        if (missileAxolotl != null && missileAxolotl.isAlive()) {
            missileAxolotl.discard();
        }
        missileAxolotl = null;
    }

    @Override
    public boolean canContinueToUse() {
        if (tick <= WARMUP_TICKS) return true;
        if (orbitTarget == null && tick - WARMUP_TICKS <= MAX_TRAVEL_TICKS) return true;
        if (orbitTarget != null && orbitTick < ORBIT_TICKS) return true;
        if (finaleTick < FINALE_TICKS) return true;
        return false;
    }

    @Override
    public void stop() {
        cleanupMissile();
        for (Axolotl axo : orbitAxolotls) {
            if (axo != null && axo.isAlive()) {
                axo.discard();
            }
        }
        orbitAxolotls.clear();
        mage.setCasting(false);
        mage.setState(0);
        tick = 0;
        orbitTick = 0;
        finaleTick = 0;
        orbitTarget = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
