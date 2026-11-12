package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/**
 * Flow, Darktide's 0-{@link #MAX} resource: it rises in water and in the dark, at least {@link #NIGHT_FLOW} a second
 * anywhere at night, and from parries and roll evasions; anywhere else it still trickles in at {@link #BASE_PER_SECOND},
 * and nothing drains it but what it's spent on. It's the only thing Darktide's moves cost (their cooldowns are a
 * second or so), and it's kept through a relog ({@link DarktideState.Save#flow}). Named {@code DarktideFlow} in code so
 * it's never confused with Chronokinesis' Time Flow.
 * <p>
 * The tide ({@link #tide}): from {@link #HIGH_TIDE} Flow it's high tide and moves reach {@link #HIGH_TIDE_REACH}× as
 * far; at {@link #MAX} it's spring tide and the next Black Tide, Crescent or Undertow is its big version.
 */
public final class DarktideFlow {
	public static final int MAX = 100;
	/** Gains per second: fully submerged; waist-deep or standing in rain. */
	static final float SUBMERGED_PER_SECOND = 4.0F;
	static final float SHALLOW_PER_SECOND = 2.0F;
	/** How deep "waist-deep" is (blocks of water at the feet). */
	static final double WAIST_DEEP = 0.6;
	/** Gains per second in the dark: block light at most {@link #DARK_BLOCK_LIGHT} and no sky light; more at light 0. */
	static final float DARK_PER_SECOND = 2.0F;
	static final float PITCH_DARK_PER_SECOND = 3.0F;
	static final int DARK_BLOCK_LIGHT = 4;
	/** At night (anywhere, in a dimension with a day): at least this much a second, or the day's gains if they're more. */
	static final float NIGHT_FLOW = 3.0F;
	/** Anywhere, whatever the light: at least this much a second. */
	static final float BASE_PER_SECOND = 1.0F;
	/** The tide: ebb, high (from {@link #HIGH_TIDE} Flow), spring (a full bar). */
	public static final int EBB = 0, HIGH = 1, SPRING = 2;
	public static final int HIGH_TIDE = 50;
	/** High tide and above: how much further moves reach. */
	static final double HIGH_TIDE_REACH = 1.25;
	/** At home in water: this much faster on foot in water or rain; Dolphin's Grace while swimming. */
	static final double WET_SPEED = 0.2;
	private static final Identifier WET_SPEED_ID = JaymosPowers.id("darktide_wet_speed");
	/** Night: from dusk to dawn on the dimension's clock (ticks into the day). */
	public static final long NIGHT_START = 12000L, NIGHT_END = 23500L;
	/** (0.15.31) In winter night comes this much sooner and lasts this much past {@link #NIGHT_END} (ticks). */
	static final long WINTER_DUSK_SOONER = 1500L, WINTER_DAWN_LATER = 1000L;

	private DarktideFlow() {
	}

	/** Night in a dimension with a day (the Nether and the End never have one). Both sides. */
	public static boolean night(Level level) {
		if (level.dimensionType().hasFixedTime()) {
			return false;
		}
		long time = Math.floorMod(level.getDefaultClockTime(), 24000L);
		// (0.15.31) winter's nights are longer (Kitchen's seasons): dusk comes sooner and dawn later
		if (com.jaymospowers.core.Seasons.of(level) == com.jaymospowers.core.Seasons.Season.WINTER) {
			return time >= NIGHT_START - WINTER_DUSK_SOONER || time < NIGHT_END + WINTER_DAWN_LATER - 24000L;
		}
		return time >= NIGHT_START && time < NIGHT_END;
	}

	/** Effective light at {@code pos}: block light and sky light as the time of day dims it. */
	static int skyLight(Level level, BlockPos pos) {
		return Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
	}

	static boolean dark(Level level, BlockPos pos) {
		return level.getBrightness(LightLayer.BLOCK, pos) <= DARK_BLOCK_LIGHT && skyLight(level, pos) <= 0;
	}

	/** Where Undertow may surface: water, or a shadow (no more than {@link #DARK_BLOCK_LIGHT} light of either kind). */
	static boolean shadow(Level level, BlockPos pos) {
		return level.getBrightness(LightLayer.BLOCK, pos) <= DARK_BLOCK_LIGHT && skyLight(level, pos) <= DARK_BLOCK_LIGHT;
	}

	/** This tick's change in Flow for {@code player}. */
	static float tickGain(ServerPlayer player, float flow) {
		Level level = player.level();
		BlockPos pos = BlockPos.containing(player.getEyePosition());
		float water = 0.0F;
		if (player.isUnderWater()) {
			water = SUBMERGED_PER_SECOND;
		} else if (player.isInWater() && player.getFluidHeight(FluidTags.WATER) >= WAIST_DEEP || player.isInWaterOrRain() && !player.isInWater()) {
			water = SHALLOW_PER_SECOND;
		}
		float dark = 0.0F;
		if (dark(level, pos)) {
			dark = level.getBrightness(LightLayer.BLOCK, pos) <= 0 ? PITCH_DARK_PER_SECOND : DARK_PER_SECOND;
		}
		float gain = Math.max(water, dark);
		if (night(level)) {
			gain = Math.max(NIGHT_FLOW, gain);
		}
		return Math.max(BASE_PER_SECOND, gain) / 20.0F;
	}

	/** The tide for this much Flow: {@link #EBB}, {@link #HIGH} or {@link #SPRING}. Both sides. */
	public static int tide(float flow) {
		return flow >= MAX - 0.5F ? SPRING : flow >= HIGH_TIDE ? HIGH : EBB;
	}

	/** {@code reach} as far as it goes at the player's tide. */
	static double reach(ServerPlayer player, double reach) {
		return tide(DarktidePower.live(player).flow) >= HIGH ? reach * HIGH_TIDE_REACH : reach;
	}

	/** Wet: standing or swimming in water, or out in the rain. */
	static boolean wet(ServerPlayer player) {
		return player.isInWaterOrRain();
	}

	/** At home in water: quicker on foot while wet, and a dolphin's speed while swimming. */
	static void tickWet(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean wet = wet(player);
		if (wet != speed.hasModifier(WET_SPEED_ID)) {
			if (wet) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(WET_SPEED_ID, WET_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else {
				speed.removeModifier(WET_SPEED_ID);
			}
		}
		if (player.isInWater() && player.tickCount % 20 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 40, 0, true, false, false));
		}
	}

	static void dry(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(WET_SPEED_ID);
		}
	}
}
