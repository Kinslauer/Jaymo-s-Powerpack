package com.jaymoskitchen.ragdoll;

import com.jaymoskitchen.JaymosKitchen;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.0) Living players knocked limp: a crash, a blast, a cart running them over, a hard landing. Their own client
 * simulates the ragdoll; the server relays it and keeps the last one (a limp player who dies leaves that body).
 */
public final class LivingRagdolls {
	/** A limp player whose client sends no pose for this long (ticks) is let up. */
	private static final int STALE_TICKS = 60;
	/** A dead player's last body is kept this long (ticks) for the corpse, then forgotten. */
	private static final int DEATH_KEEP_TICKS = 20;

	/** On a player lying limp (synced to everyone, not saved): everyone draws them as the relayed ragdoll. */
	public static final AttachmentType<Boolean> LIMP = AttachmentRegistry.create(JaymosKitchen.id("limp"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL.cast(), AttachmentSyncPredicate.all()));

	/** Server: what we know of each limp player. */
	private static final class Entry {
		RagdollSnapshot snapshot;
		long lastPose;
		int deadFor;
	}

	private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();
	/** Anything that says no (a power that has hold of the player). */
	private static final List<Predicate<Player>> VETOES = new CopyOnWriteArrayList<>();
	/** Client only: whether the local player is limp right now, set by the client (so common code needn't touch client classes). */
	private static volatile @Nullable Predicate<Player> local;

	private LivingRagdolls() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(RagdollNet.Limp.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (payload.on()) {
				if (player.isAlive() && !player.isSpectator() && !vetoed(player)) {
					player.setAttached(LIMP, true);
					Entry entry = ENTRIES.computeIfAbsent(player.getUUID(), id -> new Entry());
					entry.lastPose = player.level().getGameTime();
				}
			} else {
				player.removeAttached(LIMP);
				// a player who died limp keeps their body for the corpse
				if (player.isAlive()) {
					ENTRIES.remove(player.getUUID());
				}
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RagdollNet.LimpPose.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!isLimp(player)) {
				return;
			}
			Entry entry = ENTRIES.computeIfAbsent(player.getUUID(), id -> new Entry());
			entry.snapshot = payload.snapshot();
			entry.lastPose = player.level().getGameTime();
			RagdollNet.sendAround(player, payload.snapshot(), player);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Map.Entry<UUID, Entry>> it = ENTRIES.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Entry> e = it.next();
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player == null) {
					it.remove();
				} else if (!player.isAlive()) {
					player.removeAttached(LIMP);
					if (++e.getValue().deadFor > DEATH_KEEP_TICKS) {
						it.remove();
					}
				} else if (player.level().getGameTime() - e.getValue().lastPose > STALE_TICKS) {
					player.removeAttached(LIMP);
					it.remove();
				} else if (vetoed(player)) {
					// a power took hold of them: they're let up (their client sees the attachment go)
					player.removeAttached(LIMP);
					it.remove();
				}
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			newPlayer.removeAttached(LIMP);
			ENTRIES.remove(oldPlayer.getUUID());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			handler.player.removeAttached(LIMP);
			ENTRIES.remove(handler.player.getUUID());
		});
	}

	/** True while {@code entity} is a player lying limp. */
	public static boolean isLimp(Entity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		if (entity.getAttached(LIMP) != null) {
			return true;
		}
		Predicate<Player> hook = local;
		return hook != null && hook.test(player);
	}

	/** The body {@code player} lies in now if they're limp (a copy, at rest), else null. */
	public static RagdollBody lastBody(ServerPlayer player) {
		Entry entry = ENTRIES.get(player.getUUID());
		return entry == null || entry.snapshot == null ? null : entry.snapshot.body();
	}

	/** Knocks {@code player} limp, flinging them this fast (blocks a tick): their client takes it from here. */
	public static void knock(ServerPlayer player, double vx, double vy, double vz) {
		if (player.isAlive() && !player.isSpectator() && !vetoed(player)) {
			ServerPlayNetworking.send(player, new RagdollNet.Knock((float) vx, (float) vy, (float) vz));
		}
	}

	/** Another mod's say: any veto that returns true keeps that player from going limp (and lets them up if they are). */
	public static void addVeto(Predicate<Player> veto) {
		VETOES.add(veto);
	}

	public static boolean vetoed(Player player) {
		for (Predicate<Player> veto : VETOES) {
			try {
				if (veto.test(player)) {
					return true;
				}
			} catch (RuntimeException e) {
				// a broken veto doesn't hold anyone up
			}
		}
		return false;
	}

	/** Client: how the local player's own limp state reaches common code (see {@link #isLimp}). */
	public static void setLocal(@Nullable Predicate<Player> hook) {
		local = hook;
	}
}
