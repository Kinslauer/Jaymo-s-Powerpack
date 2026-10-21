package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Heritage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.137) Zephyrus, the west wind: the third of the five wind dragons ({@link WindDragon}). A low, broad, amphibious
 * dragon of the swamps and the mangroves, half sunk in the water: wild, it perches in the water (not on a spire), lying
 * so low that only its head and back show, and when a player comes near it bursts out of the water into a swoop. It
 * swims as well as it flies: it breathes underwater, the water doesn't push it about, it's quick in it, and ridden in it
 * it doesn't sink: it swims where the rider looks. Its breath is acid, spat in gobs that poison and leave a toxic
 * puddle ({@link DragonAcid}; it never changes blocks). Its favourite is tropical fish; Verdant and Tideborn players win
 * it over twice as fast.
 */
public class ZephyrusDragon extends WindDragon {
	// ---- body ----
	static final double HEALTH = 190.0, ARMOR = 8.0, TOUGHNESS = 2.0, SPEED = 0.22, BITE = 10.0;
	/** Its riders' seat: height (the model's back top is about 1.06 blocks), forward and back; where its mouth is. */
	static final double SEAT_UP = 1.2, SEAT_AHEAD = 0.25, SEAT_BEHIND = -0.55;
	static final double MOUTH_AHEAD_GROUND = 2.6, MOUTH_UP_GROUND = 0.9, MOUTH_AHEAD_AIR = 2.7, MOUTH_UP_AIR = 0.9;
	/** How far its acid reaches (blocks): further than fire. */
	static final double ACID_RANGE = DragonAcid.RANGE;

	// ---- the water ----
	/** Paddling on its own: the share of its speed it pushes with, and how much way it keeps a tick (sideways, up and down). */
	static final float PADDLE = 0.35F;
	static final double PADDLE_DRAG = 0.9, PADDLE_DRAG_Y = 0.85;
	/** Driven (ridden, or wild and flying): it keeps its way as it does in the air ({@code DragonsClient} undoes this drag). */
	static final double DRIVEN_DRAG = 0.91, DRIVEN_DRAG_Y = 0.98;
	/** Ridden in water, a sink slower than this (the glide's) is cancelled: it floats where it is; a dive still sinks. */
	static final double SINK_FREE = 0.03;
	/** Floating: how much of it is under (blocks of water over its feet), how hard the water lifts it back toward that. */
	static final double FLOAT_DEPTH = 0.85, LIFT_GAIN = 0.08, LIFT_MAX = 0.06, SINK_MAX = 0.03;

	// ---- perching in the water ----
	/** How far from home it looks for a spot (blocks), and how many it tries. */
	static final int WATER_SEARCH = 24, WATER_TRIES = 32;
	/** Perched, its feet this far under the surface (so head and back show), how hard it eases there, its bob. */
	static final double LIE_DEPTH = 0.95, LIE_EASE = 0.2, LIE_BOB = 0.05;
	/** The water's surface in a block: how far up a full block of water reaches. */
	static final double SURFACE_FILL = 0.9;

	private final DragonAcid acid = new DragonAcid();

	public ZephyrusDragon(EntityType<? extends ZephyrusDragon> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.TROPICAL_FISH);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.VERDANT) || Heritage.is(player, Heritage.Kind.TIDEBORN) ? BOND_FACTOR : 1.0F;
	}

	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		// (acid never changes blocks: griefing doesn't matter)
		this.acid.breathe(level, this, mouth, aim, this.tickCount);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && this.level() instanceof ServerLevel level) {
			// the gobs fly whether it's breathing or not
			this.acid.step(level, this);
		}
	}

	@Override
	protected double breathRange() {
		return ACID_RANGE;
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return flying ? MOUTH_AHEAD_AIR : MOUTH_AHEAD_GROUND;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return flying ? MOUTH_UP_AIR : MOUTH_UP_GROUND;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, SEAT_UP, index == 0 ? SEAT_AHEAD : SEAT_BEHIND);
	}

	/** An ambusher from the water: it doesn't roar before it dives. */
	@Override
	protected boolean roarsBeforeSwoop() {
		return false;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.GREEN;
	}

	/** Its own acid and puddles can't poison it. */
	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
	}

	// ---- the water ----------------------------------------------------------------------------------------------------

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	/** Currents and the like don't push it about. */
	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	/** In the water it swims (the model draws it so): a rider's flight in water is still a swim, not a flight. */
	@Override
	public boolean airborne() {
		return super.airborne() && !this.isInWater();
	}

	/**
	 * Swimming. Driven (a rider steering it, or a wild one flying) it moves as it does in the air, without the water's
	 * drag, so W swims where the rider looks at the pace it flies, and it doesn't sink. On its own it paddles along where
	 * its path leads, quick, and floats.
	 */
	@Override
	public void travel(Vec3 input) {
		if (!this.isInWater()) {
			super.travel(input);
			return;
		}
		Vec3 v = this.getDeltaMovement();
		if (this.isNoGravity()) {
			if (this.getControllingPassenger() != null && v.y < 0.0 && v.y > -SINK_FREE) {
				v = new Vec3(v.x, 0.0, v.z);
				this.setDeltaMovement(v);
			}
			this.move(MoverType.SELF, v);
			Vec3 now = this.getDeltaMovement();
			this.setDeltaMovement(now.x * DRIVEN_DRAG, now.y * DRIVEN_DRAG_Y, now.z * DRIVEN_DRAG);
			return;
		}
		if (input.lengthSqr() > 1.0E-7) {
			this.moveRelative(this.getSpeed() * PADDLE, input);
		}
		if (this.isTamed() || this.wild != Wild.PERCHED) {
			// it floats with its back out; (perched, perchedTick holds its depth)
			double lift = Mth.clamp((this.getFluidHeight(FluidTags.WATER) - FLOAT_DEPTH) * LIFT_GAIN, -SINK_MAX, LIFT_MAX);
			this.setDeltaMovement(this.getDeltaMovement().add(0.0, lift, 0.0));
		}
		this.move(MoverType.SELF, this.getDeltaMovement());
		Vec3 now = this.getDeltaMovement();
		this.setDeltaMovement(now.x * PADDLE_DRAG, now.y * PADDLE_DRAG_Y, now.z * PADDLE_DRAG);
	}

	// ---- perching in the water ------------------------------------------------------------------------------------------

	/** A perch is a water surface: still water over water, with air above. */
	@Override
	protected boolean perchable(ServerLevel level, BlockPos top) {
		return level.getFluidState(top).is(FluidTags.WATER) && level.getFluidState(top.below()).is(FluidTags.WATER) && room(level, top);
	}

	/** The water surface nearest its home (not the tallest spot: water has no spires). */
	@Override
	protected @Nullable BlockPos findPerch(ServerLevel level) {
		Vec3 centre = this.home == null ? this.position() : this.home;
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < WATER_TRIES; i++) {
			int x = Mth.floor(centre.x) + this.random.nextInt(WATER_SEARCH * 2 + 1) - WATER_SEARCH;
			int z = Mth.floor(centre.z) + this.random.nextInt(WATER_SEARCH * 2 + 1) - WATER_SEARCH;
			if (!level.isLoaded(new BlockPos(x, 0, z))) {
				continue;
			}
			// (the heightmap counts water as solid: this is the top water block)
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
			if (!perchable(level, top)) {
				continue;
			}
			double distance = centre.distanceToSqr(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = top;
			}
		}
		return best;
	}

	/** Perched, it lies low: its feet under the surface, only its head and back showing, bobbing a little. */
	@Override
	protected void perchedTick(ServerLevel level) {
		double surface = waterSurface(level);
		if (Double.isNaN(surface)) {
			return;
		}
		double want = surface - LIE_DEPTH + Mth.sin(this.tickCount * 0.06F) * LIE_BOB;
		Vec3 v = this.getDeltaMovement();
		this.setDeltaMovement(v.x * 0.6, Mth.clamp((want - this.getY()) * LIE_EASE, -0.15, 0.15), v.z * 0.6);
		if (this.tickCount % 30 == 0) {
			Vec3 mouth = this.mouth();
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, mouth.x, surface, mouth.z, 3, 0.3, 0.05, 0.3, 0.02);
		}
	}

	/** The height of the water's surface where it is, or NaN if there's none about. */
	private double waterSurface(ServerLevel level) {
		BlockPos.MutableBlockPos at = this.blockPosition().mutable();
		for (int i = 0; i < 8 && !level.getFluidState(at).is(FluidTags.WATER); i++) {
			at.move(0, -1, 0);
		}
		if (!level.getFluidState(at).is(FluidTags.WATER)) {
			return Double.NaN;
		}
		for (int i = 0; i < 8 && level.getFluidState(at.above()).is(FluidTags.WATER); i++) {
			at.move(0, 1, 0);
		}
		return at.getY() + SURFACE_FILL;
	}
}
