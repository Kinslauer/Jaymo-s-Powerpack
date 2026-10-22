package com.jaymospowers.client;

import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Seasons;
import com.jaymospowers.power.wind.WindPower;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * (0.15.31) A Sylvan carries a little of the season about them where there are seasons ({@link Seasons}): petals in
 * spring, fireflies on summer nights, turning leaves in autumn, snowflakes in winter, now and then. A Sylvan Wind user
 * whirls it: a ring of it spinning round them, thick while they fly (autumn's gusts of leaves, winter's blizzard);
 * (0.15.54) not spring's petals, though. (0.15.75) Only Sylvans (Jaymo's Kingdoms' heritage, read by reflection like
 * {@code ZFighterKingdoms}); it was every power user before. Without Kingdoms nobody has it.
 * Drawn on each client, no packets.
 */
final class SeasonAura {
	/** Only players this close get it drawn. */
	private static final double VIEW_DISTANCE = 32.0;
	/** The chance a tick of a mote about any power user. */
	private static final float MOTE_CHANCE = 0.06F;
	/** A Wind user's whirl: motes a tick standing and flying, the radius and how fast it turns. */
	private static final float WIND_STILL = 0.3F, WIND_FLYING = 2.0F;
	private static final double WIND_RADIUS = 1.2, WIND_SPIN = 0.3;
	/** Moving at least this fast (blocks a tick, squared) off the ground counts as flying. */
	private static final double FLYING_SPEED_SQR = 0.09;
	/** Kingdoms' synced heritage attachment (Heritage.HERITAGE), or null without Kingdoms. */
	private static final AttachmentType<String> HERITAGE = heritage();

	private SeasonAura() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isSpectator() || player.isInvisible() || !player.isAlive() || !sylvan(player)
				|| player.distanceToSqr(minecraft.player) > VIEW_DISTANCE * VIEW_DISTANCE) {
				continue;
			}
			Seasons.Season season = Seasons.at(player);
			if (season == null) {
				continue;
			}
			ParticleOptions mote = mote(season, level, random);
			if (mote == null) {
				continue;
			}
			if (Powers.has(player, WindPower.INSTANCE)) {
				// (0.15.54) no blossom petals in a Wind user's whirl: spring's wind carries none
				if (season != Seasons.Season.SPRING) {
					whirl(level, player, mote, random);
				}
			} else if (random.nextFloat() < MOTE_CHANCE) {
				level.addParticle(mote, player.getX() + (random.nextDouble() - 0.5) * 2.0, player.getY() + 1.6 + random.nextDouble() * 0.8,
					player.getZ() + (random.nextDouble() - 0.5) * 2.0, 0.0, -0.02, 0.0);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static AttachmentType<String> heritage() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			return null;
		}
		try {
			return (AttachmentType<String>)Class.forName("com.jaymoskingdoms.Heritage").getField("HERITAGE").get(null);
		} catch (ReflectiveOperationException | ClassCastException e) {
			return null;
		}
	}

	private static boolean sylvan(Player player) {
		return HERITAGE != null && "sylvan".equals(player.getAttached(HERITAGE));
	}

	/** The season's mote, or null when it has none now (summer by day). */
	private static ParticleOptions mote(Seasons.Season season, ClientLevel level, RandomSource random) {
		return switch (season) {
			case SPRING -> ParticleTypes.CHERRY_LEAVES;
			case SUMMER -> level.isBrightOutside() ? null : ParticleTypes.FIREFLY;
			case AUTUMN -> switch (random.nextInt(3)) {
				case 0 -> ParticleTypes.RED_POPLAR_LEAVES;
				case 1 -> ParticleTypes.ORANGE_POPLAR_LEAVES;
				default -> ParticleTypes.YELLOW_POPLAR_LEAVES;
			};
			case WINTER -> ParticleTypes.SNOWFLAKE;
		};
	}

	private static void whirl(ClientLevel level, AbstractClientPlayer player, ParticleOptions mote, RandomSource random) {
		boolean flying = !player.onGround() && player.getDeltaMovement().lengthSqr() >= FLYING_SPEED_SQR;
		float count = flying ? WIND_FLYING : WIND_STILL;
		int n = (int)count + (random.nextFloat() < count - (int)count ? 1 : 0);
		double base = (player.tickCount + random.nextDouble()) * WIND_SPIN;
		for (int i = 0; i < n; i++) {
			double a = base + i * Math.PI;
			double r = WIND_RADIUS * (0.7 + random.nextDouble() * 0.5);
			double y = player.getY() + random.nextDouble() * player.getBbHeight();
			level.addParticle(mote, player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r,
				-Math.sin(a) * 0.15, 0.02, Math.cos(a) * 0.15);
		}
	}
}
