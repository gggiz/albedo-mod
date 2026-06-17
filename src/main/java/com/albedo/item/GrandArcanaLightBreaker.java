package com.albedo.item;

import com.albedo.sound.AlbedoSounds;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.component.DataComponents;

import java.util.List;

public class GrandArcanaLightBreaker extends Item {

    private static final int CHARGE_TICKS = 360;
    private static final int BEAM_DURATION = 100;
    private static final float BEAM_RANGE = 60.0f;
    private static final float BEAM_DAMAGE = 30.0f;
    private static final float BEAM_RADIUS = 1.2f;
    private static final double STAR_OUTER = 5.5;
    private static final double STAR_INNER = 2.1;
    private static final double MID_RING = 4.2;
    private static final int COOLDOWN_TICKS = 600;

    // Pink #FF4D88
    private static final int PINK_COLOR = 0xFF4D88;
    // Dimmer pinks for outer layers
    private static final int PINK_L2 = 0xFF3370;
    private static final int PINK_L3 = 0xCC1A52;
    // Dark purple #33004D
    private static final int DARK_PURPLE_COLOR = 0x33004D;

    public GrandArcanaLightBreaker(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.albedo.grand_arcana_light_breaker");
    }

    // ---- NBT helpers ----

    private static CompoundTag getTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void updateTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static int getCharge(ItemStack stack) {
        return getTag(stack).getInt("AlbedoCharge").orElse(0);
    }

    private static void setCharge(ItemStack stack, int charge) {
        CompoundTag tag = getTag(stack);
        tag.putInt("AlbedoCharge", charge);
        updateTag(stack, tag);
    }

    private static int getBeamTicks(ItemStack stack) {
        return getTag(stack).getInt("AlbedoBeam").orElse(0);
    }

    private static void setBeamTicks(ItemStack stack, int ticks) {
        CompoundTag tag = getTag(stack);
        tag.putInt("AlbedoBeam", ticks);
        updateTag(stack, tag);
    }

    private static Vec3 getBeamDir(ItemStack stack) {
        CompoundTag tag = getTag(stack);
        if (!tag.contains("AlbedoBeamDirX")) return Vec3.ZERO;
        return new Vec3(
                tag.getDouble("AlbedoBeamDirX").orElse(0.0),
                tag.getDouble("AlbedoBeamDirY").orElse(0.0),
                tag.getDouble("AlbedoBeamDirZ").orElse(0.0));
    }

    private static void setBeamDir(ItemStack stack, Vec3 dir) {
        CompoundTag tag = getTag(stack);
        tag.putDouble("AlbedoBeamDirX", dir.x);
        tag.putDouble("AlbedoBeamDirY", dir.y);
        tag.putDouble("AlbedoBeamDirZ", dir.z);
        updateTag(stack, tag);
    }

    // ---- Item use lifecycle ----

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (getBeamTicks(stack) > 0) return InteractionResult.FAIL;
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;

        if (!level.isClientSide()) {
            setCharge(stack, 0);
            setBeamTicks(stack, 0);

            ServerLevel sl = (ServerLevel) level;
            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    AlbedoSounds.GRAND_ARCANA_CHANT,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide()) {
            spawnCircleParticles(entity, stack);
            return;
        }

        ServerLevel sl = (ServerLevel) level;
        int charge = getCharge(stack);
        charge++;
        setCharge(stack, charge);

        // Resistance during chant (every 4s)
        if (charge % 80 == 0) {
            entity.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 120, 1));
        }

        // Ground shockwave (every 3s)
        if (charge % 60 == 0) {
            spawnGroundShockwave(sl, entity, charge / 60);
        }

        if (charge >= CHARGE_TICKS) {
            entity.stopUsingItem();
            fireBeam(sl, entity, stack);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseTicks) {
        if (level.isClientSide()) return false;

        int charge = getCharge(stack);
        if (charge >= CHARGE_TICKS) {
            fireBeam((ServerLevel) level, entity, stack);
            return false;
        }

        setCharge(stack, 0);
        return false;
    }

    private void fireBeam(ServerLevel level, LivingEntity entity, ItemStack stack) {
        setCharge(stack, 0);
        setBeamTicks(stack, BEAM_DURATION);
        setBeamDir(stack, entity.getLookAngle());

        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.2f, 0.8f);

        // Knockback cone in front of caster
        Vec3 look = entity.getLookAngle();
        Vec3 eye = entity.getEyePosition();
        AABB knockBox = new AABB(eye, eye.add(look.scale(8.0))).inflate(4.0);
        List<LivingEntity> knockTargets = level.getEntitiesOfClass(LivingEntity.class, knockBox,
                e -> e != entity && e.isAlive());
        for (LivingEntity target : knockTargets) {
            Vec3 toTarget = target.position().subtract(entity.position()).normalize();
            double dot = toTarget.dot(look);
            if (dot > 0.3) {
                target.knockback(1.5, -look.x, -look.z);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            }
        }

        if (entity instanceof Player player) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 3));
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 1));
            player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        }
    }

    // ---- Beam processing ----

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;

        int beamTicks = getBeamTicks(stack);
        if (beamTicks <= 0) return;
        if (!(entity instanceof LivingEntity caster)) return;

        processBeam(level, caster, stack, beamTicks);
        setBeamTicks(stack, beamTicks - 1);

        // Regen after beam ends
        if (beamTicks == 1) {
            caster.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 4));
        }
    }

    private void processBeam(ServerLevel level, LivingEntity caster, ItemStack stack, int remainingTicks) {
        Vec3 beamDir = caster.getLookAngle();
        Vec3 start = caster.getEyePosition().add(beamDir.scale(1.5));
        Vec3 visualEnd = start.add(beamDir.scale(BEAM_RANGE));

        // Block hit detection (for explosion only)
        ClipContext ctx = new ClipContext(start, visualEnd, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster);
        BlockHitResult blockHit = level.clip(ctx);
        boolean hitBlock = blockHit.getType() == HitResult.Type.BLOCK;
        Vec3 hitPos = hitBlock ? blockHit.getLocation() : null;

        // Beam particles — full 60 blocks always
        // Perpendicular vectors for beam shell
        Vec3 beamRight = beamDir.cross(new Vec3(0, 1, 0));
        if (beamRight.lengthSqr() < 1e-10) beamRight = new Vec3(1, 0, 0);
        beamRight = beamRight.normalize();
        Vec3 beamUp = beamRight.cross(beamDir).normalize();

        int particleCount = (int) (BEAM_RANGE * 3);
        for (int i = 0; i < particleCount; i++) {
            double t = (double) i / particleCount;
            Vec3 pos = start.add(beamDir.scale(BEAM_RANGE * t));
            double ox = (level.getRandom().nextDouble() - 0.5) * 0.4;
            double oy = (level.getRandom().nextDouble() - 0.5) * 0.4;
            double oz = (level.getRandom().nextDouble() - 0.5) * 0.4;

            // Dark purple core
            level.sendParticles(new DustParticleOptions(DARK_PURPLE_COLOR, 3.5f), true, false,
                    pos.x + ox, pos.y + oy, pos.z + oz,
                    1, 0, 0, 0, 0);

            // Center END_ROD spine
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, true, false,
                        pos.x, pos.y, pos.z,
                        1, 0, 0, 0, 0);
            }

            // Outer END_ROD shell ring (6 per ring)
            for (int r = 0; r < 6; r++) {
                double angle = r * Math.PI * 2 / 6 + t * 15;
                Vec3 ringPos = pos.add(beamRight.scale(Math.cos(angle) * 1.0))
                        .add(beamUp.scale(Math.sin(angle) * 1.0));
                level.sendParticles(ParticleTypes.END_ROD, true, false,
                        ringPos.x, ringPos.y, ringPos.z,
                        1, 0, 0, 0, 0);
            }

            // Portal trail
            if (i % 5 == 0) {
                level.sendParticles(ParticleTypes.PORTAL, true, false,
                        pos.x + ox * 3, pos.y + oy * 3, pos.z + oz * 3,
                        1, 0, 0, 0, 0.05);
            }
        }

        // Damage entities along full beam
        AABB beamBox = new AABB(start, visualEnd).inflate(BEAM_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, beamBox,
                e -> e != caster && e.isAlive());

        for (LivingEntity target : targets) {
            Vec3 closestPoint = closestPointOnLine(start, visualEnd, target.getEyePosition());
            if (closestPoint.distanceTo(target.getEyePosition()) < BEAM_RADIUS + target.getBbWidth()) {
                // Bypass invincibility frames
                resetInvulnerableTime(target);
                target.hurt(caster.damageSources().magic(), BEAM_DAMAGE);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));

                level.sendParticles(ParticleTypes.ENCHANTED_HIT, true, false,
                        target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                        5, 0.4, 0.4, 0.4, 0.1);
            }
        }

        // ---- Magic circle persists during beam (server-side, lighter) ----
        spawnCircleServer(level, caster, stack, remainingTicks);

        // ---- Portal ring at beam end (full 60 blocks) ----
        Vec3 ringRight = beamDir.cross(new Vec3(0, 1, 0));
        if (ringRight.lengthSqr() < 1e-10) {
            ringRight = beamDir.cross(new Vec3(1, 0, 0));
        }
        ringRight = ringRight.normalize();
        Vec3 ringUp = ringRight.cross(beamDir).normalize();
        int ringParticles = 30;
        for (int i = 0; i < ringParticles; i++) {
            double angle = i * Math.PI * 2 / ringParticles;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 ringPos = visualEnd.add(ringRight.scale(cos * 2.0)).add(ringUp.scale(sin * 2.0));
            level.sendParticles(ParticleTypes.PORTAL, true, false,
                    ringPos.x, ringPos.y, ringPos.z,
                    1, 0.1, 0.1, 0.1, 0.02);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, true, false,
                        ringPos.x, ringPos.y, ringPos.z,
                        1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, false,
                visualEnd.x, visualEnd.y, visualEnd.z,
                3, 1.0, 1.0, 1.0, 0.01);

        // Skyward END_ROD pillar at beam end (always)
        {
            Vec3 hit = hitBlock ? hitPos : visualEnd;

            // Base flash
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false,
                    hit.x, hit.y + 0.1, hit.z,
                    3, 0.8, 0.3, 0.8, 0.5);
            level.sendParticles(new DustParticleOptions(DARK_PURPLE_COLOR, 8.0f), true, false,
                    hit.x, hit.y + 0.5, hit.z,
                    20, 3.0, 0.5, 3.0, 0.5);

            // Core END_ROD pillar shooting upward: ~90 blocks high
            int pillarLayers = 150;
            double pillarStep = 0.6;
            for (int h = 0; h < pillarLayers; h++) {
                double y = hit.y + h * pillarStep;
                double spread = 0.5 + h * 0.05;
                // Dense END_ROD beams shooting straight up
                level.sendParticles(ParticleTypes.END_ROD, true, false,
                        hit.x + (level.getRandom().nextDouble() - 0.5) * spread * 2,
                        y + (level.getRandom().nextDouble() - 0.5) * 1.2,
                        hit.z + (level.getRandom().nextDouble() - 0.5) * spread * 2,
                        8, 0, 1.5, 0, 0.02);
                // Purple dust mixed in the core
                level.sendParticles(new DustParticleOptions(DARK_PURPLE_COLOR, 5.0f), true, false,
                        hit.x + (level.getRandom().nextDouble() - 0.5) * spread * 1.5,
                        y + (level.getRandom().nextDouble() - 0.5) * 0.8,
                        hit.z + (level.getRandom().nextDouble() - 0.5) * spread * 1.5,
                        3, 0, 0.5, 0, 0.01);
                // Portal swirl outside
                if (h % 2 == 0) {
                    double angle = h * 0.5;
                    double sw = spread + 2.0;
                    level.sendParticles(ParticleTypes.PORTAL, true, false,
                            hit.x + Math.cos(angle) * sw,
                            y,
                            hit.z + Math.sin(angle) * sw,
                            1, 0, 0.08, 0, 0.01);
                }
            }

            // END_ROD crown burst at top
            double topY = hit.y + pillarLayers * pillarStep;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false,
                    hit.x, topY, hit.z,
                    1, 0.5, 0.5, 0.5, 0.5);
            for (int i = 0; i < 40; i++) {
                double ax = (level.getRandom().nextDouble() - 0.5) * 6;
                double ay = level.getRandom().nextDouble() * 4;
                double az = (level.getRandom().nextDouble() - 0.5) * 6;
                level.sendParticles(ParticleTypes.END_ROD, true, false,
                        hit.x + ax, topY + ay, hit.z + az,
                        1, 0, 0.6, 0, 0.02);
            }
            // Portal ring at top
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20;
                level.sendParticles(ParticleTypes.PORTAL, true, false,
                        hit.x + Math.cos(a) * 4,
                        topY + Math.sin(a) * 0.5,
                        hit.z + Math.sin(a) * 4,
                        1, 0.1, 0.1, 0.1, 0.02);
            }

            // Fire scorch on ground
            for (int f = 0; f < 12; f++) {
                level.sendParticles(ParticleTypes.FLAME, true, false,
                        hit.x + (level.getRandom().nextDouble() - 0.5) * 5,
                        hit.y + 0.1,
                        hit.z + (level.getRandom().nextDouble() - 0.5) * 5,
                        1, 0, 0.02, 0, 0.01);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false,
                        hit.x + (level.getRandom().nextDouble() - 0.5) * 5,
                        hit.y + 0.5,
                        hit.z + (level.getRandom().nextDouble() - 0.5) * 5,
                        1, 0, 0.05, 0, 0.01);
            }

            // Block hit splash
            if (hitBlock) {
                Vec3 normal = blockHit.getLocation().subtract(
                        blockHit.getBlockPos().getCenter()).normalize();
                for (int s = 0; s < 30; s++) {
                    Vec3 splashDir = new Vec3(
                            (level.getRandom().nextDouble() - 0.5) * 2 + normal.x,
                            level.getRandom().nextDouble() * 2,
                            (level.getRandom().nextDouble() - 0.5) * 2 + normal.z
                    ).normalize();
                    level.sendParticles(new DustParticleOptions(DARK_PURPLE_COLOR, 3.0f), true, false,
                            hitPos.x, hitPos.y, hitPos.z,
                            1, splashDir.x * 1.5, splashDir.y * 1.5, splashDir.z * 1.5, 0.03);
                    level.sendParticles(ParticleTypes.END_ROD, true, false,
                            hitPos.x, hitPos.y, hitPos.z,
                            1, splashDir.x * 1.0, splashDir.y * 1.0, splashDir.z * 1.0, 0.02);
                }
            }

            if (remainingTicks % 20 == 0) {
                level.playSound(null, hit.x, hit.y, hit.z,
                        net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                        SoundSource.PLAYERS, 1.0f, 0.6f);
            }
        }

        if (remainingTicks % 25 == 0) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.5f, 0.6f);
        }
    }

    // ---- Server-side pentagram drawing (during beam, lighter) ----

    private void spawnCircleServer(ServerLevel level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        Vec3 look = entity.getLookAngle();
        Vec3 eye = entity.getEyePosition();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1e-10) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(look).normalize();

        double rot1 = (remainingTicks * 0.07) % (Math.PI * 2);
        double rot2 = (remainingTicks * -0.05 + Math.PI) % (Math.PI * 2);

        // Layer 1
        Vec3 c1 = eye.add(look.scale(4.5));
        drawPentagramServer(level, c1, right, up, 5.5, 0.0, PINK_COLOR);
        Vec3 s1 = c1.add(right.scale(Math.cos(rot1) * 5.5)).add(up.scale(Math.sin(rot1) * 5.5));
        level.sendParticles(new DustParticleOptions(PINK_COLOR, 4.0f), true, false, s1.x, s1.y, s1.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, true, false, s1.x, s1.y, s1.z, 1, 0, 0, 0, 0);

        // Layer 2
        Vec3 c2 = eye.add(look.scale(6.5));
        drawHexagramServer(level, c2, right, up, 8.5, Math.PI / 12, PINK_L2);

        // Layer 3
        Vec3 c3 = eye.add(look.scale(8.5));
        drawHeartServer(level, c3, right, up, 11.5, PINK_L3);

        // Center
        level.sendParticles(new DustParticleOptions(PINK_COLOR, 3.5f), true, false, c1.x, c1.y, c1.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, true, false, c1.x, c1.y, c1.z, 1, 0, 0, 0, 0);
    }

    private void drawPentagramServer(ServerLevel level, Vec3 c, Vec3 r, Vec3 u, double size, double off, int color) {
        Vec3[] pts = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double a = Math.PI / 2 + off + i * 2.0 * Math.PI / 5.0;
            pts[i] = c.add(r.scale(Math.cos(a) * size)).add(u.scale(Math.sin(a) * size));
        }
        int[] star = {0, 2, 4, 1, 3, 0};
        for (int i = 0; i < 5; i++) {
            drawLineServer(level, pts[star[i]], pts[star[i + 1]], color, 10);
        }
        drawCircleServer(level, c, r, u, color, size, 24);
        for (int i = 0; i < 5; i++) {
            level.sendParticles(new DustParticleOptions(color, 1.8f), true, false, pts[i].x, pts[i].y, pts[i].z, 1, 0, 0, 0, 0);
        }
    }

    private void drawHexagramServer(ServerLevel level, Vec3 c, Vec3 r, Vec3 u, double size, double off, int color) {
        Vec3[] pts = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double a = off + i * Math.PI / 3.0;
            pts[i] = c.add(r.scale(Math.cos(a) * size)).add(u.scale(Math.sin(a) * size));
        }
        for (int i = 0; i < 3; i++) {
            drawLineServer(level, pts[i * 2], pts[(i * 2 + 2) % 6], color, 10);
            drawLineServer(level, pts[i * 2 + 1], pts[(i * 2 + 3) % 6], color, 10);
        }
        drawCircleServer(level, c, r, u, color, size, 30);
        for (int i = 0; i < 6; i++) {
            level.sendParticles(new DustParticleOptions(color, 1.5f), true, false, pts[i].x, pts[i].y, pts[i].z, 1, 0, 0, 0, 0);
        }
    }

    private void drawHeartServer(ServerLevel level, Vec3 c, Vec3 r, Vec3 u, double size, int color) {
        double sc = size / 17.0;
        int segs = 40;
        for (int i = 0; i <= segs; i++) {
            double t = i * Math.PI * 2 / segs;
            double st = Math.sin(t);
            double hx = 16.0 * st * st * st;
            double hy = 13.0 * Math.cos(t) - 5.0 * Math.cos(2 * t) - 2.0 * Math.cos(3 * t) - Math.cos(4 * t);
            Vec3 p = c.add(r.scale(hx * sc)).add(u.scale(hy * sc));
            level.sendParticles(new DustParticleOptions(color, 1.2f), true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        // Key points glow
        double[][] kps = {{0, -17}, {-11, 10}, {11, 10}};
        for (double[] kp : kps) {
            Vec3 kpp = c.add(r.scale(kp[0] * sc)).add(u.scale(kp[1] * sc));
            level.sendParticles(new DustParticleOptions(color, 2.0f), true, false, kpp.x, kpp.y, kpp.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, true, false, kpp.x, kpp.y, kpp.z, 1, 0, 0, 0, 0);
        }
    }

    private void drawLineServer(ServerLevel level, Vec3 from, Vec3 to, int color, int segments) {
        Vec3 delta = to.subtract(from);
        for (int i = 0; i <= segments; i++) {
            Vec3 pos = from.add(delta.scale((double) i / segments));
            level.sendParticles(new DustParticleOptions(color, 1.0f), true, false, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }

    private void drawCircleServer(ServerLevel level, Vec3 c, Vec3 r, Vec3 u, int color, double radius, int segments) {
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments;
            Vec3 p = c.add(r.scale(Math.cos(a) * radius)).add(u.scale(Math.sin(a) * radius));
            level.sendParticles(new DustParticleOptions(color, 1.0f), true, false, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    // ---- Closest point on line segment ----

    private static final java.lang.reflect.Field INVULNERABLE_TIME_FIELD;

    static {
        java.lang.reflect.Field f = null;
        try {
            f = LivingEntity.class.getDeclaredField("invulnerableTime");
            f.setAccessible(true);
        } catch (NoSuchFieldException ignored) {}
        INVULNERABLE_TIME_FIELD = f;
    }

    private static void resetInvulnerableTime(LivingEntity target) {
        if (INVULNERABLE_TIME_FIELD != null) {
            try {
                INVULNERABLE_TIME_FIELD.setInt(target, 0);
            } catch (IllegalAccessException ignored) {}
        }
    }

    private static Vec3 closestPointOnLine(Vec3 lineStart, Vec3 lineEnd, Vec3 point) {
        Vec3 dir = lineEnd.subtract(lineStart);
        double lenSq = dir.lengthSqr();
        if (lenSq < 1e-10) return lineStart;

        double t = point.subtract(lineStart).dot(dir) / lenSq;
        t = Math.max(0, Math.min(1, t));
        return lineStart.add(dir.scale(t));
    }

    // ---- Pink pentagram particles (client-side) ----

    private static double formationScale(int charge) {
        if (charge >= 30) return 1.0;
        double t = charge / 30.0;
        return 1.0 - Math.pow(1.0 - t, 3);
    }

    private void spawnCircleParticles(LivingEntity entity, ItemStack stack) {
        Level level = entity.level();
        Vec3 look = entity.getLookAngle();
        Vec3 eye = entity.getEyePosition();
        int charge = getCharge(stack);
        double s = formationScale(charge);
        if (s < 0.02) return;

        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        if (right.lengthSqr() < 1e-10) {
            right = new Vec3(1, 0, 0);
        }
        Vec3 up = right.cross(look).normalize();

        // Layer 1 (innermost): pentagram
        Vec3 center1 = eye.add(look.scale(3.0 + 1.5 * s));
        drawPentagramLayer(level, center1, right, up, 5.5, 0.0, PINK_COLOR, s);

        // Layer 2 (middle): hexagram (Star of David)
        Vec3 center2 = eye.add(look.scale(4.5 + 2.0 * s));
        drawHexagramLayer(level, center2, right, up, 8.5, Math.PI / 12, PINK_L2, s);

        // Layer 3 (outermost): heart
        Vec3 center3 = eye.add(look.scale(6.0 + 2.5 * s));
        drawHeartLayer(level, center3, right, up, 11.5, PINK_L3, s);

        // Sparks
        double rot1 = (charge * 0.07) % (Math.PI * 2);
        double rot2 = (charge * -0.05 + Math.PI) % (Math.PI * 2);
        Vec3 spark1 = center1.add(right.scale(Math.cos(rot1) * 5.5 * s))
                .add(up.scale(Math.sin(rot1) * 5.5 * s));
        level.addParticle(new DustParticleOptions(PINK_COLOR, (float)(4.0 * s)),
                spark1.x, spark1.y, spark1.z, 0, 0, 0);
        level.addParticle(ParticleTypes.END_ROD,
                spark1.x, spark1.y, spark1.z, 0, 0, 0);

        Vec3 spark2 = center3.add(right.scale(Math.cos(rot2) * 11.5 * s))
                .add(up.scale(Math.sin(rot2) * 11.5 * s));
        level.addParticle(new DustParticleOptions(PINK_COLOR, (float)(2.5 * s)),
                spark2.x, spark2.y, spark2.z, 0, 0, 0);

        // Center glow
        level.addParticle(new DustParticleOptions(PINK_COLOR, (float)(3.5 * s)),
                center1.x, center1.y, center1.z, 0, 0, 0);
        level.addParticle(ParticleTypes.END_ROD,
                center1.x, center1.y, center1.z, 0, 0, 0);
    }

    // ---- Layer 1: Pentagram ----

    private void drawPentagramLayer(Level level, Vec3 center, Vec3 right, Vec3 up,
                                     double outerSize, double rotationOffset, int color,
                                     double s) {
        double outerR = outerSize * s;
        double innerR = outerSize * 0.382 * s;
        double midR = outerSize * 0.76 * s;

        Vec3[] outer = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI / 2 + rotationOffset + i * 2.0 * Math.PI / 5.0;
            outer[i] = center.add(right.scale(Math.cos(angle) * outerR))
                    .add(up.scale(Math.sin(angle) * outerR));
        }

        Vec3[] inner = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI / 2 + rotationOffset + Math.PI / 5 + i * 2.0 * Math.PI / 5.0;
            inner[i] = center.add(right.scale(Math.cos(angle) * innerR))
                    .add(up.scale(Math.sin(angle) * innerR));
        }

        Vec3[] mid = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double angle = Math.PI / 2 + rotationOffset + i * 2.0 * Math.PI / 5.0;
            mid[i] = center.add(right.scale(Math.cos(angle) * midR))
                    .add(up.scale(Math.sin(angle) * midR));
        }

        int[] star = {0, 2, 4, 1, 3, 0};

        drawCircle(level, center, right, up, color, outerR, (int)(40 * s + 1));
        for (int i = 0; i < 5; i++) {
            drawParticleLine(level, outer[star[i]], outer[star[i + 1]], color, 1.2f, (int)(14 * s + 1));
        }
        for (int i = 0; i < 5; i++) {
            drawParticleLine(level, inner[star[i]], inner[star[i + 1]], color, 1.0f, (int)(8 * s + 1));
        }
        for (int i = 0; i < 5; i++) {
            drawParticleLine(level, inner[i], inner[(i + 1) % 5], color, 0.8f, (int)(6 * s + 1));
        }
        for (int i = 0; i < 5; i++) {
            drawParticleLine(level, mid[i], mid[(i + 1) % 5], color, 0.7f, (int)(8 * s + 1));
        }
        for (int i = 0; i < 5; i++) {
            drawParticleLine(level, center, outer[i], color, 0.8f, (int)(6 * s + 1));
        }
        for (int i = 0; i < 5; i++) {
            drawParticleLine(level, mid[i], inner[i], color, 0.7f, (int)(4 * s + 1));
            drawParticleLine(level, mid[i], inner[(i + 1) % 5], color, 0.7f, (int)(4 * s + 1));
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(new DustParticleOptions(color, 1.8f),
                    outer[i].x, outer[i].y, outer[i].z, 0, 0, 0);
            level.addParticle(ParticleTypes.END_ROD,
                    outer[i].x, outer[i].y, outer[i].z, 0, 0, 0);
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(new DustParticleOptions(color, 1.2f),
                    inner[i].x, inner[i].y, inner[i].z, 0, 0, 0);
        }
        for (int i = 0; i < 10; i++) {
            double ang = rotationOffset + i * Math.PI * 2 / 10;
            Vec3 p = center.add(right.scale(Math.cos(ang) * midR))
                    .add(up.scale(Math.sin(ang) * midR));
            level.addParticle(new DustParticleOptions(color, 1.0f), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    // ---- Layer 2: Hexagram (Star of David) ----

    private void drawHexagramLayer(Level level, Vec3 center, Vec3 right, Vec3 up,
                                    double outerSize, double rotationOffset, int color,
                                    double s) {
        double outerR = outerSize * s;
        double innerR = outerSize * 0.5 * s;
        double midR = outerSize * 0.78 * s;

        // 6 outer vertices
        Vec3[] outer = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double angle = rotationOffset + i * Math.PI / 3.0;
            outer[i] = center.add(right.scale(Math.cos(angle) * outerR))
                    .add(up.scale(Math.sin(angle) * outerR));
        }

        // 6 inner vertices (rotated 30°)
        Vec3[] inner = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double angle = rotationOffset + Math.PI / 6 + i * Math.PI / 3.0;
            inner[i] = center.add(right.scale(Math.cos(angle) * innerR))
                    .add(up.scale(Math.sin(angle) * innerR));
        }

        // 6 mid vertices
        Vec3[] mid = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double angle = rotationOffset + i * Math.PI / 3.0;
            mid[i] = center.add(right.scale(Math.cos(angle) * midR))
                    .add(up.scale(Math.sin(angle) * midR));
        }

        // Outer circle
        drawCircle(level, center, right, up, color, outerR, (int)(45 * s + 1));

        // Triangle 1 (even vertices): 0→2→4→0
        for (int i = 0; i < 3; i++) {
            drawParticleLine(level, outer[i * 2], outer[(i * 2 + 2) % 6], color, 1.2f, (int)(14 * s + 1));
        }
        // Triangle 2 (odd vertices): 1→3→5→1
        for (int i = 0; i < 3; i++) {
            drawParticleLine(level, outer[i * 2 + 1], outer[(i * 2 + 3) % 6], color, 1.2f, (int)(14 * s + 1));
        }

        // Inner hexagon
        for (int i = 0; i < 6; i++) {
            drawParticleLine(level, inner[i], inner[(i + 1) % 6], color, 0.9f, (int)(7 * s + 1));
        }

        // Mid hexagon
        for (int i = 0; i < 6; i++) {
            drawParticleLine(level, mid[i], mid[(i + 1) % 6], color, 0.7f, (int)(9 * s + 1));
        }

        // Center rays
        for (int i = 0; i < 6; i++) {
            drawParticleLine(level, center, outer[i], color, 0.8f, (int)(7 * s + 1));
        }

        // Inner star (connecting inner vertices in hexagram pattern)
        for (int i = 0; i < 3; i++) {
            drawParticleLine(level, inner[i * 2], inner[(i * 2 + 2) % 6], color, 0.8f, (int)(6 * s + 1));
            drawParticleLine(level, inner[i * 2 + 1], inner[(i * 2 + 3) % 6], color, 0.8f, (int)(6 * s + 1));
        }

        // Outer vertex glows
        for (int i = 0; i < 6; i++) {
            level.addParticle(new DustParticleOptions(color, 1.8f),
                    outer[i].x, outer[i].y, outer[i].z, 0, 0, 0);
            level.addParticle(ParticleTypes.END_ROD,
                    outer[i].x, outer[i].y, outer[i].z, 0, 0, 0);
        }

        // Mid accent dots
        for (int i = 0; i < 12; i++) {
            double ang = rotationOffset + i * Math.PI / 6;
            Vec3 p = center.add(right.scale(Math.cos(ang) * midR))
                    .add(up.scale(Math.sin(ang) * midR));
            level.addParticle(new DustParticleOptions(color, 1.0f), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    // ---- Layer 3: Heart ----

    private void drawHeartLayer(Level level, Vec3 center, Vec3 right, Vec3 up,
                                 double outerSize, int color, double s) {
        double scale = outerSize * s / 17.0;
        int segments = (int)(80 * s + 1);

        Vec3 prev = null;
        for (int i = 0; i <= segments; i++) {
            double t = i * Math.PI * 2 / segments;
            double sinT = Math.sin(t);
            double hx = 16.0 * sinT * sinT * sinT;
            double hy = 13.0 * Math.cos(t) - 5.0 * Math.cos(2 * t)
                    - 2.0 * Math.cos(3 * t) - Math.cos(4 * t);
            Vec3 pos = center.add(right.scale(hx * scale)).add(up.scale(hy * scale));
            level.addParticle(new DustParticleOptions(color, 1.4f),
                    pos.x, pos.y, pos.z, 0, 0, 0);

            if (prev != null && i % 2 == 0) {
                // Inner fill rays toward center
                Vec3 midPt = pos.add(prev).scale(0.5);
                Vec3 toCenter = center.subtract(midPt);
                Vec3 innerPt = midPt.add(toCenter.scale(0.3));
                level.addParticle(new DustParticleOptions(color, 0.7f),
                        innerPt.x, innerPt.y, innerPt.z, 0, 0, 0);
            }
            prev = pos;
        }

        // Heart glow at top lobes and tip
        // Top-left lobe peak ≈ t=1.0 (x≈-11, y≈10), top-right ≈ t=2.14 (x≈11, y≈10)
        // Bottom tip at t=0 (x=0, y=-17)
        double[][] keyPoints = {
                {0, -17},       // bottom tip
                {-11, 10},      // left lobe
                {11, 10},       // right lobe
        };
        for (double[] kp : keyPoints) {
            Vec3 kpPos = center.add(right.scale(kp[0] * scale)).add(up.scale(kp[1] * scale));
            level.addParticle(new DustParticleOptions(color, 2.2f),
                    kpPos.x, kpPos.y, kpPos.z, 0, 0, 0);
            level.addParticle(ParticleTypes.END_ROD,
                    kpPos.x, kpPos.y, kpPos.z, 0, 0, 0);
        }

        // Center dot of the heart
        Vec3 hCenter = center.add(up.scale(2.0 * scale));
        level.addParticle(new DustParticleOptions(color, 1.8f),
                hCenter.x, hCenter.y, hCenter.z, 0, 0, 0);
    }

    private void drawCircle(Level level, Vec3 center, Vec3 right, Vec3 up, int color, double radius, int segments) {
        for (int i = 0; i < segments; i++) {
            double angle = i * Math.PI * 2 / segments;
            Vec3 pos = center.add(right.scale(Math.cos(angle) * radius))
                    .add(up.scale(Math.sin(angle) * radius));
            level.addParticle(new DustParticleOptions(color, 1.0f), pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    private void drawParticleLine(Level level, Vec3 from, Vec3 to, int color, float size, int segments) {
        Vec3 delta = to.subtract(from);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3 pos = from.add(delta.scale(t));
            level.addParticle(new DustParticleOptions(color, size), pos.x, pos.y, pos.z, 0, 0, 0);
        }
    }

    // ---- Ground shockwave during chant ----

    private void spawnGroundShockwave(ServerLevel level, LivingEntity entity, int wave) {
        Vec3 ground = new Vec3(entity.getX(), entity.getY() + 0.05, entity.getZ());
        double radius = 4.0 + wave * 2.0;
        int rings = 3;
        for (int r = 0; r < rings; r++) {
            double rScale = (r + 1.0) / rings;
            double rSize = radius * rScale;
            int segs = (int) (rSize * 10);
            for (int i = 0; i < segs; i++) {
                double angle = i * Math.PI * 2 / segs;
                double x = Math.cos(angle) * rSize;
                double z = Math.sin(angle) * rSize;
                level.sendParticles(new DustParticleOptions(PINK_COLOR, 2.0f - r * 0.5f), true, false,
                        ground.x + x, ground.y, ground.z + z,
                        1, 0, 0, 0, 0);
            }
        }
        // Central burst
        level.sendParticles(ParticleTypes.END_ROD, true, false,
                ground.x, ground.y + 0.3, ground.z,
                8, 0.5, 0.2, 0.5, 0.02);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        return stack;
    }
}
