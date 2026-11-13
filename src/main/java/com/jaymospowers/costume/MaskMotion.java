package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The hands at the face while a mask comes off or goes on (Andy, 2026-11-12, on the mask key: "they all need an animation
 * removing the mask too"). Every mask the key flips (a costume's head piece or hood, a hooded cloak's hood, a Rig Helm's
 * faceplate, the symbiote's mask, a school mask) waits for the hands to reach the face ({@link #REACH}) before it comes
 * off or goes on, and the hands come away with it ({@link #TICKS} in all). The start ({@link #MASKING}) is sent to
 * everyone; the client lays the pose ({@code MaskPose}).
 */
public final class MaskMotion {
	/** The whole motion, and how far into it the mask actually comes off or goes on (ticks). */
	public static final int TICKS = 14, REACH = 6;

	/** On a player: when their hands went up to their mask (game time). Sent to everyone; not saved. */
	public static final AttachmentType<Long> MASKING = AttachmentRegistry.create(JaymosPowers.id("masking"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private record Pending(ServerPlayer player, long at, Runnable swap) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private MaskMotion() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MaskMotion::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	/** {@code player}'s hands go to their face; {@code swap} runs when they get there. False if a motion is already under way. */
	public static boolean begin(ServerPlayer player, Runnable swap) {
		if (PENDING.containsKey(player.getUUID())) {
			return false;
		}
		long now = player.level().getGameTime();
		player.setAttached(MASKING, now);
		PENDING.put(player.getUUID(), new Pending(player, now + REACH, swap));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Pending> it = PENDING.values().iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			ServerPlayer player = p.player();
			if (player.isRemoved() || !player.isAlive()) {
				it.remove();
				player.removeAttached(MASKING);
				continue;
			}
			long now = player.level().getGameTime();
			if (now >= p.at()) {
				it.remove();
				p.swap().run();
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Long at = player.getAttached(MASKING);
			if (at != null && player.level().getGameTime() - at >= TICKS) {
				player.removeAttached(MASKING);
			}
		}
	}
}
