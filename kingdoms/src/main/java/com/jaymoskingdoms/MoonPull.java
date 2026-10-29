package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.323) The moon's pull among friends (the werewolf's side of the vampire's heartbeat, {@link BloodFever#HEARTBEAT}).
 * On the full moon's night and the two nights before it, a cursed werewolf player hears every mortal player near them
 * (within {@link #RANGE} blocks) as a pulse: the nearest one in their sight (inside {@link #SEEN_ANGLE} degrees of where
 * they look, nothing in between) sets their {@link #HEARTBEAT}, which the client turns into a slow, deep beat and a golden
 * pulse at the screen's edges ({@code WolfScreen}). While any mortal is that near, the wildness ({@link FeralCurse#FERAL})
 * creeps up, a little faster the closer they are.
 */
public final class MoonPull {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** A mortal player this near (blocks) is heard. */
	static final double RANGE = 150.0;
	/** One in their sight: within this half-angle of where they look (degrees). */
	static final float SEEN_ANGLE = 40.0F;
	/** The wildness gained each second at the range's edge and face to face. */
	static final float CREEP = 0.003F, CREEP_CLOSE = 0.02F;

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/**
	 * How loud the nearest mortal player in their sight beats for them (0 none, 1 face to face): synced to the werewolf alone.
	 * Not kept.
	 */
	public static final AttachmentType<Float> HEARTBEAT = AttachmentRegistry.create(JaymosKingdoms.id("moon_heartbeat"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	private MoonPull() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MoonPull::tick);
	}

	/** The heartbeat in their sight (0-1), on either side. */
	public static float heartbeat(net.minecraft.world.entity.player.Player player) {
		Float f = player.getAttached(HEARTBEAT);
		return f == null ? 0.0F : f;
	}

	/** The full moon's night, or one of the two before it. */
	static boolean pulling(ServerLevel overworld, long clock) {
		if (!Vampires.night(clock)) {
			return false;
		}
		if (Lycans.fullMoon(overworld, clock)) {
			return true;
		}
		MoonPhase phase = overworld.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO);
		return phase == MoonPhase.WAXING_GIBBOUS || phase == MoonPhase.FIRST_QUARTER;
	}

	// ---- every second --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		boolean pulling = pulling(overworld, overworld.getOverworldClockTime());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 11) {
				continue;
			}
			if (!pulling || !FeralCurse.stillCursed(player) || !player.isAlive() || player.isSpectator() || player.isCreative()) {
				if (player.hasAttached(HEARTBEAT)) {
					player.removeAttached(HEARTBEAT);
				}
				continue;
			}
			among(player);
		}
	}

	/** The nearest mortal player makes the wildness creep; the nearest one in sight sets the heartbeat. */
	private static void among(ServerPlayer player) {
		double nearest = Double.MAX_VALUE;
		float seen = 0.0F;
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double cos = Math.cos(Math.toRadians(SEEN_ANGLE));
		for (ServerPlayer other : player.level().players()) {
			if (other == player || !Vampires.mortal(other) || other.isInvisible()) {
				continue;
			}
			double d = other.distanceTo(player);
			if (d > RANGE) {
				continue;
			}
			nearest = Math.min(nearest, d);
			float close = (float)(1.0 - d / RANGE);
			if (close <= seen) {
				continue;
			}
			Vec3 to = other.getEyePosition().subtract(eye);
			Vec3 chest = other.position().add(0.0, other.getBbHeight() * 0.6, 0.0);
			if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) >= cos && (sees(player, eye, other.getEyePosition()) || sees(player, eye, chest))) {
				seen = close;
			}
		}
		float was = heartbeat(player);
		if (seen <= 0.0F) {
			if (player.hasAttached(HEARTBEAT)) {
				player.removeAttached(HEARTBEAT);
			}
		} else if (Math.abs(seen - was) > 0.01F) {
			player.setAttached(HEARTBEAT, seen);
		}
		if (nearest <= RANGE) {
			FeralCurse.drift(player, Mth.lerp((float)(1.0 - nearest / RANGE), CREEP, CREEP_CLOSE));
		}
	}

	private static boolean sees(ServerPlayer player, Vec3 from, Vec3 to) {
		return player.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
	}
}
