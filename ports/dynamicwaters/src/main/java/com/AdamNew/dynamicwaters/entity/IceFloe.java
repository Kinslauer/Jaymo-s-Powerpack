package com.AdamNew.dynamicwaters.entity;

import com.AdamNew.dynamicwaters.HydrologyManager;
import com.AdamNew.dynamicwaters.terrain.WatershedStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class IceFloe extends Entity {
    private static final int LIFETIME = 20 * 60 * 3;

    public static final double ABOVE_WATER = 0.1;

    private static final double TOP_STEP = 0.006;

    private static final double DRIFT = 0.006;

    private static final int DRY_LIMIT = 20 * 10;

    public static final float SMALLEST = 0.35f;
    public static final float LARGEST = 1.1f;

    private static final int FLOW_EVERY = 4;
    private static final int CLIMATE_EVERY = 20;

    private static final int SPREAD_EVERY = 5;
    private static final double SPREAD = 0.005;
    private static final double SPREAD_GAP = 0.05;

    private static final int CRUSHED = 40;
    private static final int JAMMED = 25;

    private static final int AT_ICE = 60;

    private static final double NEAR_MARGIN = 1.0;

    private static final double STOPPED = 1.0E-4;

    private static final double FROZEN = Double.NEGATIVE_INFINITY;

    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(IceFloe.class, EntityDataSerializers.FLOAT);

    private int age;
    private int lifetime;
    private int dry;
    private boolean warm;
    private Vec3 flow = Vec3.ZERO;
    private double spreadX;
    private double spreadZ;

    private int touching;

    private java.util.List<IceFloe> near = java.util.List.of();

    private boolean atIce;

    public IceFloe(EntityType<? extends IceFloe> type, Level level) {
        super(type, level);
        this.lifetime = LIFETIME * 2 / 3 + level.getRandom().nextInt(LIFETIME * 2 / 3);
        if (!level.isClientSide()) setSize(randomSize(level.getRandom()));
    }

    public static float randomSize(RandomSource random) {
        final float r = random.nextFloat();
        return SMALLEST + (LARGEST - SMALLEST) * r * r;
    }

    public static float thickness(float size) {
        return 0.14f + 0.16f * size;
    }

    public static double reach(float size) {
        return size * 0.7;
    }

    public static double keepApart(float a, float b) {
        return reach(a) + reach(b) + SPREAD_GAP;
    }

    public float size() {
        return entityData.get(SIZE);
    }

    public void setSize(float size) {
        entityData.set(SIZE, Mth.clamp(size, SMALLEST, LARGEST));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 0.7f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (SIZE.equals(key)) refreshDimensions();
        super.onSyncedDataUpdated(key);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        final float size = size();
        return EntityDimensions.scalable(size, thickness(size));
    }

    @Override
    public void tick() {
        final long t0 = System.nanoTime();
        super.tick();
        final Level level = level();
        final float size = size();
        Vec3 v = getDeltaMovement();
        final double surface = surface(level);
        if (surface == FROZEN) {
            if (!level.isClientSide()) breakUp();
            return;
        }
        if (!Double.isNaN(surface)) {
            final double target = surface + ABOVE_WATER + (getId() & 7) * TOP_STEP - thickness(size);
            v = new Vec3(v.x * 0.92, (target - getY()) * 0.25 + v.y * 0.5, v.z * 0.92);
            if ((tickCount + getId()) % FLOW_EVERY == 0) {
                flow = HydrologyManager.getRiverFlow(getX(), Mth.floor(surface - 0.5), getZ(), level);
            }
            if ((tickCount + getId()) % SPREAD_EVERY == 0) spreadOut(size);

            final double drift = DRIFT / (1 + touching);
            v = offIce(holdBack(v.add(flow.x * drift + spreadX, 0.0, flow.z * drift + spreadZ), size), size, surface);
            dry = 0;
        } else {
            v = new Vec3(v.x * 0.98, v.y - 0.04, v.z * 0.98);
            dry++;
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (level.isClientSide()) return;
        if ((tickCount + getId()) % CLIMATE_EVERY == 0) {
            warm = !level.getBiome(blockPosition()).value().coldEnoughToSnow(blockPosition(), com.AdamNew.dynamicwaters.HydrologyManager.getSeaLevel());
        }

        age += warm ? 10 : 1;
        if (age > lifetime * (0.5 + 0.5 * size) || dry > DRY_LIMIT) breakUp();
        WatershedStats.iceFloeTicks.increment();
        WatershedStats.iceFloeNanos.add(System.nanoTime() - t0);
    }

    private Vec3 holdBack(Vec3 v, float size) {
        double x = v.x, z = v.z;
        for (IceFloe other : near) {
            if (other.isRemoved()) continue;
            final double dx = other.getX() - getX(), dz = other.getZ() - getZ();
            final double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 1.0E-3 || d >= keepApart(size, other.size())) continue;
            final double ux = dx / d, uz = dz / d;
            final Vec3 theirs = other.getDeltaMovement();
            final double closing = (x - theirs.x) * ux + (z - theirs.z) * uz;
            if (closing <= 0.0) continue;
            x -= closing * 0.5 * ux;
            z -= closing * 0.5 * uz;
        }
        return new Vec3(x, v.y, z);
    }

    private Vec3 offIce(Vec3 v, float size, double surface) {
        final Level level = level();
        final int y = Mth.floor(surface - 0.05);
        final double out = reach(size) + 0.05;
        double x = v.x, z = v.z;
        atIce = false;
        if (x != 0.0 && level.getBlockState(BlockPos.containing(getX() + Math.signum(x) * out, y, getZ())).is(BlockTags.ICE)) {
            x = 0.0;
            atIce = true;
        }
        if (z != 0.0 && level.getBlockState(BlockPos.containing(getX(), y, getZ() + Math.signum(z) * out)).is(BlockTags.ICE)) {
            z = 0.0;
            atIce = true;
        }
        return atIce ? new Vec3(x, v.y, z) : v;
    }

    private void spreadOut(float size) {
        spreadX = 0.0;
        spreadZ = 0.0;
        touching = 0;
        final double reach = keepApart(size, LARGEST) + NEAR_MARGIN;
        boolean crowded = false, overlaps = false;
        final java.util.List<IceFloe> around = level().getEntitiesOfClass(IceFloe.class, getBoundingBox().inflate(reach, 0.5, reach));
        around.remove(this);
        near = around;
        for (IceFloe other : around) {
            final double dx = getX() - other.getX(), dz = getZ() - other.getZ();
            final double keep = keepApart(size, other.size());
            final double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= keep) continue;
            touching++;
            overlaps = true;
            if (d < keep * 0.5) crowded = true;

            final double angle = getId() * 2.4;
            final double ux = d > 1.0E-3 ? dx / d : Math.cos(angle), uz = d > 1.0E-3 ? dz / d : Math.sin(angle);
            final double push = SPREAD * (1.0 - d / keep);
            spreadX += ux * push;
            spreadZ += uz * push;
        }
        if (!level().isClientSide()) {
            WatershedStats.iceFloeLooks.increment();
            if (overlaps) WatershedStats.iceFloeOverlaps.increment();
            if (crowded) {
                WatershedStats.iceFloeCrowded.increment();
                age += CRUSHED;
            }
            if (touching > 0 && getDeltaMovement().horizontalDistanceSqr() < STOPPED) age += JAMMED;
            if (atIce) age += AT_ICE;
        }
    }

    private void breakUp() {
        if (level() instanceof ServerLevel server) {
            final float size = size();
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), getX(),
                    getY() + thickness(size), getZ(), 4 + (int) (size * 16.0f), size * 0.3, 0.05, size * 0.3, 0.0);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.BLOCKS,
                    0.25f + 0.45f * size, 1.6f - 0.5f * size);
        }
        discard();
    }

    private double surface(Level level) {
        final BlockPos at = BlockPos.containing(getX(), getY() + 0.5, getZ());
        for (int dy = 1; dy >= -2; dy--) {
            final BlockPos p = at.above(dy);
            if (level.getBlockState(p).is(BlockTags.ICE)) return FROZEN;
            final FluidState fluid = level.getFluidState(p);
            if (!fluid.is(FluidTags.WATER)) continue;
            if (fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING)) return Double.NaN;
            return p.getY() + fluid.getHeight(level, p);
        }
        return Double.NaN;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source)) return false;
        if (!isRemoved()) breakUp();
        return true;
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return !isInvulnerableToBase(source);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        age = input.getIntOr("Age", 0);
        input.read("Size", com.mojang.serialization.Codec.FLOAT).ifPresent(this::setSize);
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("Age", age);
        output.putFloat("Size", size());
    }
}
