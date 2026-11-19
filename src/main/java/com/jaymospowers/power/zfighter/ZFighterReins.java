package com.jaymospowers.power.zfighter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pass the reins. In a fusion of two (the dance, the earrings, the arm bands) the one steering can hand the body over whenever
 * he likes: Z, then C again (the fusion dance's own two keys, which mean nothing to a fighter already fused). The fusion then
 * stays as it is, only the other steers; the one who now steers can hand it back the same way. The halfway swap only happens if
 * nobody has passed the reins by then (a swap of any kind ends it).
 * <p>
 * The one inside asks to split with Z (the key does nothing for a spectator otherwise). The one steering sees the ask for
 * {@link #ASK_TICKS}; Z, then C within that time agrees and the fusion ends at once. With no ask pending, the same Z, C is the
 * hand-over.
 */
public final class ZFighterReins {
	/** How long an ask to split waits for the one steering to agree. */
	static final int ASK_TICKS = 200;
	/** Least ticks between two hand-overs (so a mashed key doesn't flicker the body between them). */
	static final int PASS_COOLDOWN = 40;
	/** Least ticks between two asks. */
	static final int ASK_COOLDOWN = 40;

	/** Who is asking to split (keyed by the one steering): when. */
	private static final Map<UUID, Long> ASKS = new HashMap<>();
	private static final Map<UUID, Long> LAST_PASS = new HashMap<>();

	private ZFighterReins() {
	}

	static void init() {
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ASKS.remove(handler.player.getUUID());
			LAST_PASS.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ASKS.clear();
			LAST_PASS.clear();
		});
	}

	/**
	 * Z pressed by {@code inside}, who may be watching from inside a fusion of two: he asks to split. Returns whether he was
	 * watching one (so the press goes no further).
	 */
	public static boolean insidePressedZ(ServerPlayer inside) {
		UUID leaderId = ZFighterFusion.leaderOf(inside);
		if (leaderId == null) {
			return false;
		}
		MinecraftServer server = inside.level().getServer();
		ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
		ZFighterFusion.Fused fused = leader == null ? null : ZFighterFusion.of(leader);
		if (fused == null || !ZFighterFusion.twoWay(fused)) {
			return false;
		}
		long now = inside.level().getGameTime();
		Long asked = ASKS.get(leaderId);
		if (asked != null && now - asked < ASK_COOLDOWN) {
			return true;
		}
		ASKS.put(leaderId, now);
		inside.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_reins_asked", leader.getDisplayName()).withStyle(ChatFormatting.AQUA));
		leader.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_reins_split_ask", inside.getDisplayName())
			.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/**
	 * Z then C by {@code leader}, who steers a fusion: agrees to a split if one is asked, otherwise passes the reins. Returns
	 * whether it did either (false: not a fusion of two, so the dance's own rules go on).
	 */
	static boolean dance(ServerPlayer leader, long now) {
		ZFighterFusion.Fused fused = ZFighterFusion.of(leader);
		if (fused == null || !ZFighterFusion.twoWay(fused)) {
			return false;
		}
		MinecraftServer server = leader.level().getServer();
		ServerPlayer inside = server.getPlayerList().getPlayer(fused.partner());
		if (inside == null) {
			return false;
		}
		Long asked = ASKS.remove(leader.getUUID());
		if (asked != null && now - asked <= ASK_TICKS) {
			ZFighterFusion.defuse(server, leader.getUUID());
			return true;
		}
		Long last = LAST_PASS.get(leader.getUUID());
		if (last != null && now - last < PASS_COOLDOWN) {
			return true;
		}
		ZFighterFusion.passReins(leader, inside, fused);
		LAST_PASS.put(inside.getUUID(), now);
		return true;
	}
}
