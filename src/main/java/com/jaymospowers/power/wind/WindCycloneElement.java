package com.jaymospowers.power.wind;

import com.jaymospowers.core.Flames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What the Cyclone has drawn up into itself. The funnel sucks up what's under and around it: a fire tornado over lava
 * or flames (the fire it would have blown out feeds it instead), a waterspout over water, a blizzard over snow and ice
 * (or in falling snow), a sandstorm over sand, and a charged cyclone under a thunderstorm. What it carries rubs off on
 * everything in orbit: the fire tornado sets them alight, the waterspout soaks them (putting them out, and hurting
 * what water hurts), the blizzard freezes and slows them, the sandstorm blinds them, and the storm shocks them. Burning,
 * frozen, sandy or soaked creatures it catches feed their element in too. The strongest element around wins, and the
 * funnel changes over if another takes over (fire meeting water goes up in steam first). Nothing it carries harms you
 * in the eye of it.
 */
public enum WindCycloneElement {
	NONE, FIRE, WATER, SNOW, SAND, STORM;

	/** How often the funnel takes stock of what's around it (ticks), and how far down it reaches (blocks). */
	static final int SAMPLE_EVERY = 10, SAMPLE_DEPTH = 2;
	/** How many blocks' worth of an element it takes for the funnel to draw it up, and to switch to another. */
	static final int TAKE_AT = 8, SWITCH_MARGIN = 6;
	/** What a creature in orbit is worth toward its element (burning, frozen, a husk's sand, a soaked one). */
	static final int CREATURE_WEIGHT = 6;
	/** A thunderstorm overhead, and falling snow or rain, toward the storm, the blizzard and the waterspout. */
	static final int THUNDER_WEIGHT = 30, SNOWFALL_WEIGHT = 10, RAIN_WEIGHT = 2;
	/** The fire tornado: how long it sets things alight (seconds). */
	static final int BURN_SECONDS = 4;
	/** The waterspout: extra harm to what water hurts (endermen, blazes, striders...). */
	static final float SOAK_DAMAGE = 2.0F;
	/** The blizzard: freezing added per hit (ticks; powder snow freezes fully at 140), and the slowness (ticks, level). */
	static final int FREEZE_TICKS = 50, CHILL_TICKS = 60, CHILL_LEVEL = 1;
	/** The sandstorm: blindness per hit (ticks), and the grit's extra cut. */
	static final int BLIND_TICKS = 50;
	static final float GRIT_DAMAGE = 1.0F;

	public static WindCycloneElement byId(int id) {
		WindCycloneElement[] all = values();
		return id >= 0 && id < all.length ? all[id] : NONE;
	}

	/** The lang key of the Cyclone's name with this element in it. */
	public String labelKey() {
		return this == NONE ? "hud.jaymospowers.wind.cyclone" : "hud.jaymospowers.wind.cyclone." + this.name().toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * What the funnel draws up now: each element's weight from the blocks in its footprint (and just below), the sky,
	 * and the creatures in orbit; the strongest from {@link #TAKE_AT}. {@code current} holds on unless another beats it
	 * by {@link #SWITCH_MARGIN}.
	 */
	static WindCycloneElement sample(ServerPlayer player, ServerLevel level, double radius, Iterable<LivingEntity> orbiting, WindCycloneElement current) {
		int[] weight = new int[values().length];
		BlockPos feet = player.blockPosition();
		int r = Mth.ceil(Math.min(radius, WindDouse.MAX_RADIUS));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int dy = -SAMPLE_DEPTH; dy <= SAMPLE_DEPTH; dy++) {
					pos.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
					WindCycloneElement e = of(level.getBlockState(pos), level, pos);
					weight[e.ordinal()]++;
				}
			}
		}
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, feet);
		boolean open = top.getY() <= feet.getY() + 1;
		if (open && level.isThundering()) {
			weight[STORM.ordinal()] += THUNDER_WEIGHT;
		} else if (open && level.isRaining()) {
			if (level.getBiome(feet).value().getPrecipitationAt(feet, level.getSeaLevel()) == Biome.Precipitation.SNOW) {
				weight[SNOW.ordinal()] += SNOWFALL_WEIGHT;
			} else {
				weight[WATER.ordinal()] += RAIN_WEIGHT;
			}
		}
		for (LivingEntity creature : orbiting) {
			weight[carried(creature).ordinal()] += CREATURE_WEIGHT;
		}
		weight[NONE.ordinal()] = 0;
		WindCycloneElement best = NONE;
		for (WindCycloneElement e : values()) {
			if (e != NONE && weight[e.ordinal()] >= TAKE_AT && weight[e.ordinal()] > weight[best.ordinal()]) {
				best = e;
			}
		}
		if (current != NONE && best != current && weight[current.ordinal()] + SWITCH_MARGIN > weight[best.ordinal()]) {
			return current;
		}
		return best == NONE ? current : best;
	}

	private static WindCycloneElement of(BlockState state, ServerLevel level, BlockPos pos) {
		if (state.getFluidState().is(FluidTags.LAVA) || state.getBlock() instanceof BaseFireBlock || state.is(Blocks.MAGMA_BLOCK)
			|| state.is(BlockTags.CAMPFIRES) && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
			return FIRE;
		}
		if (state.getFluidState().is(FluidTags.WATER)) {
			return WATER;
		}
		if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(BlockTags.ICE)) {
			return SNOW;
		}
		if (state.is(BlockTags.SAND)) {
			return SAND;
		}
		return NONE;
	}

	/** The element a creature in orbit brings in with it, if any. */
	private static WindCycloneElement carried(LivingEntity creature) {
		if (creature.isOnFire() || creature.getType() == EntityTypes.BLAZE || creature.getType() == EntityTypes.MAGMA_CUBE) {
			return FIRE;
		}
		if (creature.isFullyFrozen() || creature.getType() == EntityTypes.STRAY || creature.getType() == EntityTypes.SNOW_GOLEM
			|| creature.getType() == EntityTypes.POLAR_BEAR) {
			return SNOW;
		}
		if (creature.getType() == EntityTypes.HUSK) {
			return SAND;
		}
		if (creature.isInWater() || creature.getType() == EntityTypes.DROWNED || creature.getType() == EntityTypes.SQUID
			|| creature.getType() == EntityTypes.GLOW_SQUID || creature.getType() == EntityTypes.GUARDIAN) {
			return WATER;
		}
		return NONE;
	}

	/** The funnel changes over to this element: a burst of it (steam where water quenches fire, or fire meets water). */
	void announce(ServerPlayer player, ServerLevel level, WindCycloneElement from, double height) {
		double x = player.getX(), y = player.getY() + height * 0.4, z = player.getZ();
		if (from == FIRE && this == WATER || from == WATER && this == FIRE) {
			level.sendParticles(ParticleTypes.CLOUD, x, y, z, 60, 1.5, height * 0.3, 1.5, 0.05);
			level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2F, 0.6F);
		}
		switch (this) {
			case FIRE -> level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.5F);
			case WATER -> level.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2F, 0.6F);
			case SNOW -> level.playSound(null, x, y, z, SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
			case SAND -> level.playSound(null, x, y, z, SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
			case STORM -> level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.7F, 1.4F);
			default -> {
			}
		}
	}

	/** This element's particle in the funnel's rings (in place of some of the cloud), or null for none. */
	ParticleOptions particle(ServerLevel level) {
		return switch (this) {
			case FIRE -> ParticleTypes.FLAME;
			case WATER -> ParticleTypes.SPLASH;
			case SNOW -> ParticleTypes.SNOWFLAKE;
			case SAND -> new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());
			case STORM -> ParticleTypes.ELECTRIC_SPARK;
			case NONE -> null;
		};
	}

	/** Extras up the column each tick: smoke and embers, spray, snow, grit, static. */
	void funnelExtras(ServerPlayer player, ServerLevel level, long age, double radius, double height) {
		if (this == NONE) {
			return;
		}
		double x0 = player.getX(), y0 = player.getY(), z0 = player.getZ();
		for (int i = 0; i < 4; i++) {
			double h = player.getRandom().nextDouble() * height;
			double b = player.getRandom().nextDouble() * Math.PI * 2.0;
			double r = Mth.lerp(h / height, radius * WindCyclone.FOOT_SHARE, radius);
			double x = x0 + Math.cos(b) * r, y = y0 + h, z = z0 + Math.sin(b) * r;
			switch (this) {
				case FIRE -> {
					level.sendParticles(ParticleTypes.FLAME, x, y, z, 0, -Math.sin(b) * 0.3, 0.1, Math.cos(b) * 0.3, 1.0);
					level.sendParticles(i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.LAVA, x, y, z, 1, 0.2, 0.2, 0.2, 0.01);
				}
				case WATER -> {
					level.sendParticles(ParticleTypes.FALLING_WATER, x, y, z, 2, 0.3, 0.3, 0.3, 0.0);
					level.sendParticles(ParticleTypes.BUBBLE_POP, x, y, z, 1, 0.2, 0.2, 0.2, 0.05);
				}
				case SNOW -> {
					level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 0, -Math.sin(b) * 0.35, 0.05, Math.cos(b) * 0.35, 1.0);
					level.sendParticles(ParticleTypes.ITEM_SNOWBALL, x, y, z, 1, 0.3, 0.3, 0.3, 0.05);
				}
				case SAND -> level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), x, y, z, 3, 0.4, 0.4, 0.4,
					0.1);
				case STORM -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.2, 0.2, 0.2, 0.2);
				default -> {
				}
			}
		}
		if (age % 20 == 0) {
			switch (this) {
				case FIRE -> level.playSound(null, x0, y0 + height * 0.5, z0, SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 1.0F, 0.6F);
				case WATER -> level.playSound(null, x0, y0 + height * 0.5, z0, SoundEvents.WEATHER_RAIN, SoundSource.PLAYERS, 0.8F, 0.8F);
				case SNOW -> level.playSound(null, x0, y0 + height * 0.5, z0, SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.0F, 0.5F);
				case SAND -> level.playSound(null, x0, y0 + height * 0.5, z0, SoundEvents.SAND_STEP, SoundSource.PLAYERS, 1.0F, 0.5F);
				case STORM -> {
					if (age % 60 == 0) {
						level.playSound(null, x0, y0 + height * 0.5, z0, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.5F, 1.6F);
					}
				}
				default -> {
				}
			}
		}
	}

	/** Added to the wind's hit: the sandstorm's grit, the waterspout's soaking of what water hurts. */
	float bonus(LivingEntity target) {
		return switch (this) {
			case SAND -> GRIT_DAMAGE;
			case WATER -> target.isSensitiveToWater() ? SOAK_DAMAGE : 0.0F;
			default -> 0.0F;
		};
	}

	/** On each hit of the wind: what the element does to a creature in orbit. */
	void batter(ServerPlayer player, ServerLevel level, LivingEntity target) {
		double y = target.getY() + target.getBbHeight() * 0.5;
		switch (this) {
			case FIRE -> {
				Flames.touch(level, target, BURN_SECONDS, player);
				level.sendParticles(ParticleTypes.FLAME, target.getX(), y, target.getZ(), 8, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4,
					target.getBbWidth() * 0.5, 0.02);
			}
			case WATER -> {
				target.clearFire();
				level.sendParticles(ParticleTypes.SPLASH, target.getX(), y, target.getZ(), 12, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4,
					target.getBbWidth() * 0.5, 0.1);
			}
			case SNOW -> {
				if (target.canFreeze()) {
					target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + FREEZE_TICKS));
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, CHILL_LEVEL), player);
				level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), y, target.getZ(), 10, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4,
					target.getBbWidth() * 0.5, 0.05);
			}
			case SAND -> target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0), player);
			default -> {
			}
		}
	}
}
