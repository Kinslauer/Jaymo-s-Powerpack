package com.jaymospowers.freerun.moves;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The fast swim (after ParCool's): swimming flat out (vanilla's sprint swim) for {@link #DELAY} ticks breaks into a
 * faster swim, a butterfly stroke with a dolphin kick ({@code SwimStrokePose}) and a trail of bubbles. The client
 * pulls the body along ({@code RunMovesClient}, {@link #PUSH} more each tick the way you look); here it starts and
 * ends, and every {@link #DRAIN_TICKS} it costs a feather. It ends when you stop sprint-swimming or run out of feathers.
 */
public final class FastSwim {
	// ===== the knobs ================================================================================================
	/** Ticks of unbroken sprint swimming before the fast swim. */
	public static final int DELAY = 40;
	/** Extra pull the way you look (blocks a tick, each tick), and the fastest it pulls you to (blocks a tick). */
	public static final double PUSH = 0.012, MAX_SPEED = 0.55;
	/** A feather (half-feather) every this many ticks. */
	public static final int DRAIN_TICKS = 30;
	// ================================================================================================================

	/** On a player in a fast swim (synced to everyone, for the stroke). */
	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(JaymosPowers.id("fast_swim"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Map<UUID, Integer> SWUM = new HashMap<>();

	private FastSwim() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FastSwim::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SWUM.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SWUM.clear());
	}

	/** Whether {@code player} is in a fast swim (any side). */
	public static boolean active(Player player) {
		return player.hasAttached(ACTIVE);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean swimming = player.isAlive() && player.isSwimming() && player.isSprinting() && player.isInWater() && !player.isPassenger();
			if (!swimming) {
				if (SWUM.remove(player.getUUID()) != null || active(player)) {
					player.removeAttached(ACTIVE);
				}
				continue;
			}
			int swum = SWUM.merge(player.getUUID(), 1, Integer::sum);
			if (swum < DELAY) {
				continue;
			}
			if ((swum - DELAY) % DRAIN_TICKS == 0 && !Feathers.spend(player, 1)) {
				player.removeAttached(ACTIVE);
				SWUM.put(player.getUUID(), 0);
				continue;
			}
			if (!active(player)) {
				player.setAttached(ACTIVE, true);
			}
			if (swum % 3 == 0) {
				Vec3 back = player.getLookAngle().scale(-0.8);
				player.level().sendParticles(ParticleTypes.BUBBLE, player.getX() + back.x, player.getY() + 0.3 + back.y, player.getZ() + back.z, 3, 0.2, 0.15, 0.2, 0.02);
			}
		}
	}
}
