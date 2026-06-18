package com.albedo.entity;

import com.albedo.AlbedoConfig;
import com.albedo.AlbedoMod;
import com.albedo.entity.ai.*;
import com.albedo.network.BuildDataPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.albedo.item.AlbedoItems;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AlbedoBoss extends Monster {
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AlbedoBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_STATE =
            SynchedEntityData.defineId(AlbedoBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BUILD_PROGRESS =
            SynchedEntityData.defineId(AlbedoBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FOLLOW_STATE =
            SynchedEntityData.defineId(AlbedoBoss.class, EntityDataSerializers.INT);

    private enum FollowState { FOLLOW, PATROL, SIT, BUILD }

    private final ServerBossEvent bossBar;
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private UUID ownerUUID;
    private FollowState followState = FollowState.FOLLOW;
    private Vec3 patrolCenter = Vec3.ZERO;
    private boolean stateRestored = false;

    // 从客户端接收的建造计划
    public record BuildPlan(BlockPos pos, BlockState state) {}
    private List<BuildPlan> buildPlan = null;
    private BlockPos buildOrigin = null;
    private int buildSx, buildSy, buildSz;
    private int buildPlaced = 0;

    public AlbedoBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.bossBar = new ServerBossEvent(
                java.util.UUID.randomUUID(),
                this.getDisplayName(),
                BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AlbedoItems.HELL_ABYSS));
        this.xpReward = 10000;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            if (source.getDirectEntity() != null) {
                level.sendParticles(ParticleTypes.CRIT,
                        source.getDirectEntity().getX(), source.getDirectEntity().getY(), source.getDirectEntity().getZ(),
                        5, 0.2, 0.2, 0.2, 0.1);
                source.getDirectEntity().discard();
            }
            return true;
        }
        return super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(MobEffects.WITHER) || effect.is(MobEffects.POISON) || effect.is(MobEffects.WEAKNESS)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, AlbedoConfig.MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, AlbedoConfig.BASE_ATTACK)
                .add(Attributes.ARMOR, AlbedoConfig.ARMOR)
                .add(Attributes.MOVEMENT_SPEED, AlbedoConfig.MOVEMENT_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, AlbedoConfig.KNOCKBACK_RESIST)
                .add(Attributes.FOLLOW_RANGE, AlbedoConfig.FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_ATTACK_STATE, 0);
        builder.define(DATA_BUILD_PROGRESS, 0);
        builder.define(DATA_FOLLOW_STATE, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Attack goals (priority 1-4: phase-gated via canUse)
        this.goalSelector.addGoal(1, new SweepingStrikeGoal(this));
        this.goalSelector.addGoal(2, new HellThrustGoal(this));
        this.goalSelector.addGoal(3, new AbyssWaveGoal(this));
        this.goalSelector.addGoal(4, new FinalJudgmentGoal(this));
        this.goalSelector.addGoal(4, new ShadowCloneGoal(this));

        // Melee chase (lowest priority among attacks - fires when skills are on cooldown)
        this.goalSelector.addGoal(5, new AlbedoMeleeAttackGoal(this));
        // 建造模式
        this.goalSelector.addGoal(5, new StructureBuildGoal(this));

        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Player.class, AlbedoBoss.class) {
            @Override
            public boolean canUse() {
                if (((AlbedoBoss) this.mob).isBuilding()) return false;
                return super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                (living, level) -> !(living instanceof Player) && !(living instanceof AlbedoBoss)
                        && !(living instanceof AxolotlMage)
                        && living.getType() != EntityType.CAT && living.getType() != EntityType.WOLF
                        && !living.entityTags().contains("albedo_clone")
                        && (living instanceof Monster
                            || living.getType() == EntityType.VILLAGER
                            || living.getType() == EntityType.IRON_GOLEM)) {
            @Override
            public boolean canUse() {
                if (((AlbedoBoss) this.mob).isBuilding()) return false;
                return super.canUse();
            }
        });
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.CONSUME;

        if (getOwnerUUID().isEmpty()) {
            setOwnerUUID(player.getUUID());
            followState = FollowState.FOLLOW;
            setTarget(null);
            this.setPersistenceRequired();
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§5雅儿贝德 §a跟随中"));
            return InteractionResult.SUCCESS;
        }

        if (!getOwnerUUID().get().equals(player.getUUID())) return InteractionResult.PASS;

        AlbedoMod.LOGGER.info("Boss#{} 收到交互, 当前状态={}, 交互玩家={}",
                getId(), followState, player.getName().getString());

        switch (followState) {
            case FOLLOW -> {
                followState = FollowState.PATROL;
                patrolCenter = player.position();
                setTarget(null);
                clearBuildPlan();
                AlbedoMod.LOGGER.info("Boss#{} FOLLOW→PATROL", getId());
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§5雅儿贝德 §e巡逻中"));
            }
            case PATROL -> {
                followState = FollowState.SIT;
                setTarget(null);
                getNavigation().stop();
                clearBuildPlan();
                AlbedoMod.LOGGER.info("Boss#{} PATROL→SIT", getId());
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§5雅儿贝德 §7待机中"));
            }
            case SIT -> {
                followState = FollowState.BUILD;
                setTarget(null);
                setBuildProgress(0);
                AlbedoMod.LOGGER.info("Boss#{} SIT→BUILD", getId());
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§5雅儿贝德 §d建造模式 §7(读取投影中...)"));
            }
            case BUILD -> {
                followState = FollowState.FOLLOW;
                setTarget(null);
                setBuildProgress(0);
                clearBuildPlan();
                AlbedoMod.LOGGER.info("Boss#{} BUILD→FOLLOW", getId());
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§5雅儿贝德 §a跟随中"));
            }
        }
        AlbedoMod.LOGGER.info("Boss#{} 状态变更完成, 新状态={}", getId(), followState);
        entityData.set(DATA_FOLLOW_STATE, followState.ordinal());
        persistState();
        return InteractionResult.SUCCESS;
    }

    public Optional<UUID> getOwnerUUID() {
        return Optional.ofNullable(ownerUUID);
    }

    public void setOwnerUUID(UUID uuid) {
        this.ownerUUID = uuid;
        persistState();
    }

    public boolean isSitting() {
        return followState == FollowState.SIT;
    }

    public String getFollowStateName() {
        return switch (followState) {
            case FOLLOW -> "FOLLOW";
            case PATROL -> "PATROL";
            case SIT -> "SIT";
            case BUILD -> "BUILD";
        };
    }

    public int getBuildProgress() {
        return entityData.get(DATA_BUILD_PROGRESS);
    }

    public void setBuildProgress(int progress) {
        entityData.set(DATA_BUILD_PROGRESS, progress);
    }

    public int getFollowStateOrdinal() {
        return entityData.get(DATA_FOLLOW_STATE);
    }

    public boolean isBuilding() {
        return followState == FollowState.BUILD;
    }

    public void receiveBuildData(BuildDataPayload payload) {
        if (hasBuildPlan()) {
            AlbedoMod.LOGGER.warn("Boss已有建造计划，忽略重复数据");
            return;
        }
        this.buildOrigin = payload.origin();
        this.buildSx = payload.sx();
        this.buildSy = payload.sy();
        this.buildSz = payload.sz();
        this.buildPlan = new ArrayList<>(payload.blocks().size());
        for (var e : payload.blocks()) {
            BlockPos pos = payload.origin().offset(e.rx(), e.ry(), e.rz());
            BlockState state = Block.stateById(e.stateId());
            this.buildPlan.add(new BuildPlan(pos, state));
        }
    }

    public List<BuildPlan> getBuildPlan() { return buildPlan; }
    public BlockPos getBuildOrigin() { return buildOrigin; }
    public int getBuildSx() { return buildSx; }
    public int getBuildSy() { return buildSy; }
    public int getBuildSz() { return buildSz; }
    public boolean hasBuildPlan() { return buildPlan != null && !buildPlan.isEmpty(); }
    public int getBuildPlaced() { return buildPlaced; }
    public void setBuildPlaced(int placed) { this.buildPlaced = placed; }
    public int getBuildTotal() { return buildPlan != null ? buildPlan.size() : 0; }

    public void clearBuildPlan() {
        buildPlan = null;
        buildOrigin = null;
        buildPlaced = 0;
    }

    public LivingEntity getOwner() {
        if (ownerUUID == null) return null;
        return level().getPlayerByUUID(ownerUUID);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        this.fallDistance = 0;

        if (!level().isClientSide() && !isClone()) {
            updatePhase();
            spawnAmbientParticles();
            spawnWingParticles();
        }
    }

    private void updatePhase() {
        float hpRatio = getHealth() / getMaxHealth();
        int currentPhase = getPhase();
        int newPhase = hpRatio > 0.62f ? 1 : hpRatio > 0.32f ? 2 : 3;

        if (newPhase != currentPhase) {
            setPhase(newPhase);
            if (newPhase == 3) {
                getAttribute(Attributes.MOVEMENT_SPEED)
                        .setBaseValue(AlbedoConfig.MOVEMENT_SPEED * 1.4);
            }
            playSound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.5f, 1.0f);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                        getX(), getY() + 1.5, getZ(),
                        20, 1.0, 1.0, 1.0, 0.3);
            }
        }
    }

    private void spawnAmbientParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        // Rotating portal ring
        if (tickCount % 2 == 0) {
            double r = 0.6;
            for (int i = 0; i < 3; i++) {
                double angle = (tickCount * 5 + i * 120) * Math.PI / 180;
                serverLevel.sendParticles(ParticleTypes.PORTAL,
                        getX() + Math.cos(angle) * r,
                        getY() + 0.2,
                        getZ() + Math.sin(angle) * r,
                        1, 0, 0, 0, 0);
            }
        }

        // Phase-dependent aura
        if (getPhase() >= 2 && tickCount % 4 == 0) {
            double auraR = 0.9;
            for (int i = 0; i < 2; i++) {
                double angle = (tickCount * 3 + i * 180) * Math.PI / 180;
                serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        getX() + Math.cos(angle) * auraR,
                        getY() + 1.0 + Math.sin(tickCount * 0.1) * 0.3,
                        getZ() + Math.sin(angle) * auraR,
                        1, 0, 0, 0, 0);
            }
        }

        // P3 dark smoke aura
        if (getPhase() >= 3 && tickCount % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = (tickCount * 2 + i * 90) * Math.PI / 180;
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX() + Math.cos(angle) * 0.7,
                        getY() + 0.1,
                        getZ() + Math.sin(angle) * 0.7,
                        1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    private void spawnWingParticles() {
        if (!(level() instanceof ServerLevel serverLevel) || tickCount % 2 != 0) return;

        // MC body yaw: forward = (-sinYaw, 0, cosYaw)
        double yawRad = Math.toRadians(getYRot());
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        // Backward (behind entity)
        double backX = sinYaw;
        double backZ = -cosYaw;
        // Right (perpendicular)
        double rightX = cosYaw;
        double rightZ = sinYaw;

        double baseY = getY() + 1.05;
        int density = getPhase() >= 3 ? 12 : 8;

        for (int side = -1; side <= 1; side += 2) {
            for (int row = 0; row < density; row++) {
                double t = (double) row / density;
                double sideReach = side * (0.25 + t * 1.0);
                double upReach = t * 1.5;
                double backReach = 0.2 + t * 0.9;

                double px = getX() + backX * 0.3 + rightX * sideReach + backX * backReach;
                double pz = getZ() + backZ * 0.3 + rightZ * sideReach + backZ * backReach;

                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        px, baseY + upReach, pz,
                        1, 0, 0, 0, 0);
                if (getPhase() >= 3) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL,
                            px, baseY + upReach, pz,
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    // --- Boss bar ---

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (!entityTags().contains("albedo_clone")) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (!entityTags().contains("albedo_clone")) {
            this.bossBar.removePlayer(player);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel serverLevel) {
        super.customServerAiStep(serverLevel);

        // Clone auto-despawn after 8 seconds
        if (entityTags().contains("albedo_clone")) {
            if (tickCount >= 160) {
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                            getX(), getY() + 1, getZ(),
                            8, 0.3, 0.3, 0.3, 0.02);
                }
                discard();
                return;
            }
            return; // clones don't get boss bar or cooldown ticks
        }

        // Owner follow/sit/patrol behavior
        LivingEntity owner = getOwner();
        if (owner != null && owner.isAlive()) {
            if (followState == FollowState.SIT) {
                getNavigation().stop();
                setTarget(null);
                setDeltaMovement(Vec3.ZERO);
                xxa = 0;
                zza = 0;
            } else if (followState == FollowState.BUILD) {
                // 建造模式：不传送、不索敌、不打怪，由建造 AI 全权控制
                setTarget(null);
            } else if (followState == FollowState.PATROL) {
                LivingEntity target = getTarget();
                if (target == null || !target.isAlive()) {
                    LivingEntity nearby = level().getEntitiesOfClass(LivingEntity.class,
                            getBoundingBox().inflate(12),
                            e -> e != this && !(e instanceof Player)
                                    && !(e instanceof AlbedoBoss)
                                    && e.getType() != EntityType.CAT
                                    && e.getType() != EntityType.WOLF
                                    && !e.entityTags().contains("albedo_clone")
                                    && e.isAlive()
                                    && hasLineOfSight(e)
                    ).stream().findFirst().orElse(null);
                    if (nearby != null) {
                        setTarget(nearby);
                    } else if (distanceToSqr(patrolCenter) > 64.0) {
                        getNavigation().moveTo(patrolCenter.x, patrolCenter.y, patrolCenter.z, 1.0);
                    } else if (getNavigation().isDone() && getRandom().nextInt(40) == 0) {
                        double dx = patrolCenter.x + (getRandom().nextDouble() - 0.5) * 16;
                        double dz = patrolCenter.z + (getRandom().nextDouble() - 0.5) * 16;
                        getNavigation().moveTo(dx, patrolCenter.y, dz, 0.8);
                    }
                } else if (target.distanceToSqr(patrolCenter) > 256.0) {
                    setTarget(null);
                    getNavigation().moveTo(patrolCenter.x, patrolCenter.y, patrolCenter.z, 1.2);
                }
            } else {
                // 跟随模式：距离过远立即传送
                double dist = distanceTo(owner);
                if (dist > AlbedoConfig.TELEPORT_DISTANCE) {
                    setTarget(null);
                    setAttackState(0);
                    getNavigation().stop();
                    setDeltaMovement(Vec3.ZERO);
                    teleportTo(owner.getX(), owner.getY(), owner.getZ());
                    if (level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.PORTAL,
                                getX(), getY() + 1, getZ(),
                                15, 0.5, 0.5, 0.5, 0.1);
                    }
                    return;
                }

                LivingEntity target = getTarget();
                LivingEntity ownerTarget = owner.getLastHurtMob();
                if (ownerTarget != null && ownerTarget.isAlive()
                        && !(ownerTarget instanceof Player)
                        && !(ownerTarget instanceof AlbedoBoss)
                        && ownerTarget.getType() != EntityType.CAT
                        && ownerTarget.getType() != EntityType.WOLF
                        && !ownerTarget.entityTags().contains("albedo_clone")) {
                    if (target == null || !target.isAlive() || target.distanceTo(owner) > 20) {
                        setTarget(ownerTarget);
                    }
                } else if (target == null || !target.isAlive()) {
                    if (dist > 6.0) {
                        getNavigation().moveTo(owner, 1.2);
                    }
                }
            }
        }

        // 从实体标签恢复主人信息（重启/NBT加载后）
        if (!stateRestored) {
            stateRestored = true;
            restoreState();
            if (ownerUUID != null) {
                this.setPersistenceRequired();
            }
            this.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 3, false, false));
            this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1, 0, false, false));
        }

        // Flight: only fly when chasing a target above
        LivingEntity target = getTarget();
        boolean shouldFly = target != null && target.getY() > getY() + 2.0;
        setNoGravity(shouldFly);
        if (shouldFly && target != null) {
            double dy = target.getY() - getY();
            if (dy > 0) {
                setDeltaMovement(getDeltaMovement().add(0, Math.min(dy * 0.08, 0.4), 0));
            }
            // Horizontal movement toward target while flying
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            double hDist = Math.sqrt(dx * dx + dz * dz);
            if (hDist > 0.01) {
                double speed = 0.25;
                setDeltaMovement(getDeltaMovement().add(
                        dx / hDist * speed, 0, dz / hDist * speed));
            }
        }

        // Absorption V every 20 seconds
        if (tickCount > 1 && tickCount % 400 == 0) {
            this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 500, 4, false, false));
        }

        this.bossBar.setProgress(getHealth() / getMaxHealth());
        if (isBuilding() && !hasBuildPlan()) {
            this.bossBar.setName(getDisplayName().copy()
                    .append(" §8[§7等待投影数据...§8]"));
        } else if (isBuilding() && hasBuildPlan()) {
            this.bossBar.setName(getDisplayName().copy()
                    .append(" §8[§d建造中 " + getBuildProgress() + "% §e" + getBuildPlaced() + "/" + getBuildTotal() + "§8]"));
        } else {
            this.bossBar.setName(getDisplayName().copy()
                    .append(" §8[§5P" + getPhase() + "§8]"));
        }
        // Tick cooldowns
        cooldowns.replaceAll((k, v) -> v > 0 ? v - 1 : 0);

        // 定期输出状态日志用于调试
        if (tickCount % 200 == 0) {
            AlbedoMod.LOGGER.info("Boss#{} 状态: followState={}, isBuilding={}, hasBuildPlan={}, target={}",
                    getId(), followState, isBuilding(), hasBuildPlan(),
                    getTarget() != null ? getTarget().getName().getString() : "null");
        }
    }

    public boolean isClone() {
        return entityTags().contains("albedo_clone");
    }

    public boolean isOnCooldown(String skill) {
        return cooldowns.getOrDefault(skill, 0) > 0;
    }

    public void setCooldown(String skill, int ticks) {
        cooldowns.put(skill, ticks);
    }

    // --- Getters/Setters ---

    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    public void setPhase(int phase) {
        entityData.set(DATA_PHASE, phase);
    }

    public int getAttackState() {
        return entityData.get(DATA_ATTACK_STATE);
    }

    public void setAttackState(int state) {
        entityData.set(DATA_ATTACK_STATE, state);
    }

    // --- Sound overrides ---

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return net.minecraft.sounds.SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return net.minecraft.sounds.SoundEvents.WITHER_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return net.minecraft.sounds.SoundEvents.WITHER_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200; // every 10 seconds
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isNoGravity() && isEffectiveAi()) {
            moveRelative(getSpeed(), travelVector);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.91));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (isClone()) {
            discard();
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    getX(), getY() + 1.5, getZ(),
                    5, 2.0, 2.0, 2.0, 0.5);
        }
        super.die(source);
    }

    private static final String TAG_OWNER = "albedo_owner";
    private static final String TAG_STATE = "albedo_state";
    private static final String TAG_PATROL = "albedo_patrol";

    private void persistState() {
        // 清除旧标签
        entityTags().removeIf(t -> t.startsWith("albedo_"));
        if (ownerUUID != null) {
            addTag(TAG_OWNER + ":" + ownerUUID);
        }
        addTag(TAG_STATE + ":" + followState.name());
        addTag(TAG_PATROL + ":" + patrolCenter.x + "," + patrolCenter.y + "," + patrolCenter.z);
    }

    private void restoreState() {
        for (String tag : entityTags()) {
            if (tag.startsWith(TAG_OWNER + ":")) {
                try { ownerUUID = UUID.fromString(tag.substring(TAG_OWNER.length() + 1)); } catch (Exception ignored) {}
            } else if (tag.startsWith(TAG_STATE + ":")) {
                try { followState = FollowState.valueOf(tag.substring(TAG_STATE.length() + 1)); } catch (Exception ignored) {}
            } else if (tag.startsWith(TAG_PATROL + ":")) {
                String[] parts = tag.substring(TAG_PATROL.length() + 1).split(",");
                try { patrolCenter = new Vec3(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])); } catch (Exception ignored) {}
            }
        }
        entityData.set(DATA_FOLLOW_STATE, followState.ordinal());
    }

}
