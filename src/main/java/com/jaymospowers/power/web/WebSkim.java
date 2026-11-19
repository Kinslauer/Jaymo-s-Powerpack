package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Skim the water: let go of a low swing fast over a lake or a river and you land on your feet on the surface and keep
 * going, skimming across it for {@link #SKIM_TICKS} (a bar under the crosshair shows how long you have left). The water
 * holds you up the way it holds Overclock's runners (see {@code WebSkimMixin}). Jump hops you off it; Sneak lets you sink;
 * running out of bar, of speed, or of water ends it. It takes a fresh swing to start again. The client keeps your speed up
 * while it lasts ({@code WebSkimClient}).
 */
public final class WebSkim {
	// ================================================================================================================
	// Tuning
	/** How long a skim lasts (ticks). */
	public static final int SKIM_TICKS = 80;
	/** The fastest (blocks/tick along the water) you may come off the web to start one, and the slowest it keeps going at. */
	public static final double ENTRY_SPEED = 0.55, KEEP_SPEED = 0.2;
	/** Feet this far above the water (blocks) or less, and no deeper than {@link #ENTRY_DEPTH} below, make it a low swing. */
	static final double ENTRY_HEIGHT = 1.2, ENTRY_DEPTH = 0.4;
	/** The web let go within this many ticks. */
	static final int ENTRY_WINDOW = 30;
	/** Ticks of too little speed, or no water below, that end it. */
	static final int SLOW_TICKS = 10, DRY_TICKS = 6;
	// ================================================================================================================

	/** When the skim ends (game time) and how long it was (ticks), for the bar. Synced to its owner, which also decides the water holds it up. */
	public record Skim(long until, int total) {
		public static final StreamCodec<ByteBuf, Skim> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Skim::until, ByteBufCodecs.VAR_INT, Skim::total, Skim::new);
	}

	public static final AttachmentType<Skim> SKIM = AttachmentRegistry.create(JaymosPowers.id("web_skim"),
		builder -> builder.syncWith(Skim.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private static final class Live {
		Vec3 lastPos;
		int slow;
		int dry;
		int splash;
	}

	private static final Map<ServerPlayer, Live> LIVE = new WeakHashMap<>();

	private WebSkim() {
	}

	/** Whether this player is skimming right now (both sides; the water holds them up). */
	public static boolean skimming(Player player) {
		Skim skim = player.getAttached(SKIM);
		return skim != null && player.level().getGameTime() < skim.until();
	}

	/** Every tick, for a web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.computeIfAbsent(player, p -> new Live());
		Vec3 pos = player.position();
		Vec3 moved = live.lastPos == null ? Vec3.ZERO : pos.subtract(live.lastPos);
		live.lastPos = pos;
		double speed = moved.horizontalDistance();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (!player.isAlive() || player.isSpectator()) {
			end(player);
			return;
		}
		if (player.getAttached(SKIM) == null) {
			if (!player.onGround() && !player.isInWater() && speed >= ENTRY_SPEED && WebAirStrikes.released(player, ENTRY_WINDOW)
				&& !player.isShiftKeyDown() && !player.isPassenger() && !player.getAbilities().flying && !player.isFallFlying()) {
				double height = height(level, player);
				if (height > -ENTRY_DEPTH && height <= ENTRY_HEIGHT) {
					live.slow = 0;
					live.dry = 0;
					player.setAttached(SKIM, new Skim(now + SKIM_TICKS, SKIM_TICKS));
					player.resetFallDistance(); // the water held you up: the fall onto it doesn't count
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.8F, 1.3F);
					level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY(), player.getZ(), 20, 0.4, 0.1, 0.4, 0.2);
				}
			}
			return;
		}
		// skimming: over when the bar runs out, on a sneak, on a hop, in the water, or out of speed or water
		boolean hop = player.getLastClientInput().jump();
		if (!skimming(player) || player.isShiftKeyDown() || hop || player.isInWater() || player.isPassenger()) {
			end(player);
			return;
		}
		live.slow = speed < KEEP_SPEED ? live.slow + 1 : 0;
		double height = height(level, player);
		live.dry = height > -ENTRY_DEPTH && height < 0.6 ? 0 : live.dry + 1;
		if (live.slow >= SLOW_TICKS || live.dry >= DRY_TICKS) {
			end(player);
			return;
		}
		if (++live.splash % 2 == 0) {
			level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.05, player.getZ(), 4, 0.3, 0.02, 0.3, 0.1);
		}
	}

	private static void end(ServerPlayer player) {
		if (player.getAttached(SKIM) != null) {
			player.removeAttached(SKIM);
		}
		Live live = LIVE.get(player);
		if (live != null) {
			live.slow = 0;
			live.dry = 0;
		}
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player);
		player.removeAttached(SKIM);
	}

	/** How far the feet are above the water's surface below them (blocks; none within 2 blocks: a big number). */
	static double height(ServerLevel level, Player player) {
		BlockPos feet = player.blockPosition();
		for (int dy = 0; dy >= -2; dy--) {
			BlockPos at = feet.offset(0, dy, 0);
			FluidState fluid = level.getFluidState(at);
			if (fluid.is(FluidTags.WATER) && level.getFluidState(at.above()).isEmpty()) {
				return player.getY() - (at.getY() + fluid.getHeight(level, at));
			}
		}
		return 99.0;
	}
}
