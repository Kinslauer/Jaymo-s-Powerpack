package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The speed tunnel: a blur ({@link ChronoMomentum#BLUR}) that runs at a wall goes through it, and the way it went stays
 * open for {@link #OPEN_TICKS} ticks for anyone to follow. No block is touched: the tunnel is a straight line from where
 * the runner stood to the first clear spot past the wall (at most {@link #MAX_LENGTH} blocks on), and a player standing in
 * it is {@link #CARRIED}, which the phase's own collision rule reads ({@link ChronoPhase#phasing}): no block stops them
 * but the floor under their feet and what nothing can break. Step out of the line inside the wall, or be in it when it
 * closes, and you are put out at its nearer mouth.
 */
public final class ChronoTunnel {
	// ---- tuning ----
	/** How near (blocks) the wall must be to open, the longest tunnel, how far from its line still counts as in it. */
	static final double PROBE = 1.0, MAX_LENGTH = 10.0, WIDTH = 1.3;
	/** How long it stays open (ticks), and the wait before the same runner opens another. */
	static final int OPEN_TICKS = 160, COOLDOWN = 60;

	/** On a player standing in an open tunnel, for both sides' collision. */
	public static final AttachmentType<Boolean> CARRIED = AttachmentRegistry.create(JaymosPowers.id("chrono_tunnel_carried"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private record Tunnel(ServerLevel level, Vec3 from, Vec3 to, long until) {
		/** How far {@code at} is from the tunnel's line (a little past each mouth counts). */
		double off(Vec3 at) {
			Vec3 along = this.to.subtract(this.from);
			double length = along.length();
			Vec3 dir = along.scale(1.0 / length);
			double t = Math.max(-0.6, Math.min(length + 0.6, at.subtract(this.from).dot(dir)));
			return at.distanceTo(this.from.add(dir.scale(t)));
		}

		Vec3 mouth(Vec3 at) {
			return at.distanceToSqr(this.from) <= at.distanceToSqr(this.to) ? this.from : this.to;
		}
	}

	private static final List<Tunnel> TUNNELS = new ArrayList<>();
	private static final Map<UUID, Tunnel> INSIDE = new HashMap<>();
	private static final Map<UUID, Integer> READY = new HashMap<>();

	private ChronoTunnel() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ChronoTunnel::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			out(handler.player);
			READY.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				out(player);
			}
			TUNNELS.clear();
			READY.clear();
		});
	}

	/** A blur is sprinting: if a wall stands in its way and there is clear ground past it, the tunnel opens. */
	static void run(ServerPlayer player, ServerLevel level, int color) {
		if (READY.getOrDefault(player.getUUID(), 0) > player.tickCount || ChronoPhase.phasing(player)) {
			return;
		}
		Vec3 dir = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		AABB ahead = player.getBoundingBox().move(dir.scale(PROBE));
		// a wall, not a step: it stops the body a block higher up too
		if (level.noCollision(player, ahead) || level.noCollision(player, ahead.move(0.0, 1.1, 0.0))) {
			return;
		}
		Vec3 from = player.position(), to = null;
		for (double d = PROBE + 0.5; d <= MAX_LENGTH; d += 0.5) {
			Vec3 spot = from.add(dir.scale(d));
			BlockPos feet = BlockPos.containing(spot);
			// what nothing can break stops a tunnel
			if (level.getBlockState(feet).getDestroySpeed(level, feet) < 0.0F || level.getBlockState(feet.above()).getDestroySpeed(level, feet.above()) < 0.0F
				|| !level.getWorldBorder().isWithinBounds(spot.x, spot.z)) {
				break;
			}
			if (ChronoPhase.clear(level, player, spot)) {
				to = spot;
				break;
			}
		}
		// no way through here: do not ask again at once
		READY.put(player.getUUID(), player.tickCount + (to == null ? 10 : COOLDOWN));
		if (to == null) {
			return;
		}
		Tunnel tunnel = new Tunnel(level, from, to, level.getGameTime() + OPEN_TICKS);
		TUNNELS.add(tunnel);
		// the runner is in it from this tick, so the wall does not stop them first
		INSIDE.put(player.getUUID(), tunnel);
		player.setAttached(CARRIED, true);
		Vec3 up = new Vec3(0.0, 1.0, 0.0);
		MagicFx.ring(level, from.add(up).add(dir.scale(PROBE * 0.5)), dir, 1.3F, color, OPEN_TICKS);
		MagicFx.ring(level, to.add(up), dir, 1.3F, color, OPEN_TICKS);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
	}

	private static void tick(MinecraftServer server) {
		if (TUNNELS.isEmpty() && INSIDE.isEmpty()) {
			return;
		}
		TUNNELS.removeIf(t -> t.level.getGameTime() >= t.until);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Tunnel in = null;
			if (player.isAlive() && !player.isSpectator()) {
				for (Tunnel t : TUNNELS) {
					if (t.level == player.level() && t.off(player.position()) <= WIDTH) {
						in = t;
						break;
					}
				}
			}
			if (in == null) {
				out(player);
				continue;
			}
			INSIDE.put(player.getUUID(), in);
			if (player.getAttached(CARRIED) == null) {
				player.setAttached(CARRIED, true);
			}
			if (player.tickCount % 3 == 0) {
				in.level.sendParticles(MagicFx.CRACKLE, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.6, 0.3, 0.05);
			}
		}
	}

	/** {@code player} is no longer in a tunnel: the walls are walls again, and one caught in a wall is put at the nearer mouth. */
	private static void out(ServerPlayer player) {
		Tunnel was = INSIDE.remove(player.getUUID());
		if (player.getAttached(CARRIED) != null) {
			player.removeAttached(CARRIED);
		}
		if (was == null || was.level != player.level() || ChronoPhase.phasing(player) || ChronoPhase.clear(was.level, player, player.position())) {
			return;
		}
		Vec3 mouth = was.mouth(player.position());
		player.teleportTo(was.level, mouth.x, mouth.y, mouth.z, Set.of(), player.getYRot(), player.getXRot(), false);
	}
}
