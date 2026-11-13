package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.SpellEngineCompat;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Andy (2026-11-13): "the double axe spin should make you pass out if you hold it too long". The spin is the RPG Series'
 * Whirlwind (Spell Engine's {@code rpg_series:whirlwind}), the held weapon skill of the double axe and of every other big
 * axe (battleaxe, war axe, great axe, minotaur axe: Kingdoms' {@code WeaponSkills}). It channels for up to 8 seconds.
 * <ul>
 * <li><b>Getting dizzy:</b> every tick spent spinning adds one to the spinner's dizziness; every tick not spinning takes
 * one off again.</li>
 * <li><b>Warning signs:</b> past {@link #WARN} (3 seconds of spin) the view sways and rolls, the steering wobbles and a
 * woozy warble plays in the spinner's own ears, all growing until {@link #LIMIT} (drawn by the client's
 * {@code SpinDizzyHud}).</li>
 * <li><b>Out cold:</b> still spinning at {@link #LIMIT} (6 seconds), the spin stops and they collapse out cold for
 * {@link #OUT_TICKS} through {@link Downed#knockOut}, the same knock-out as a thrown rock or a collapse from exhaustion
 * (a short one: a few presses of sneak bring them round sooner).</li>
 * <li><b>Coming round:</b> the dizziness holds at its worst while they're down, then wears off over the next few seconds
 * as usual, so they get up still swaying, and spinning again straight away puts them back down fast.</li>
 * </ul>
 * Players only: Spell Engine runs held casts only for players (and spell summons), so no villager, guard or mob ever holds
 * the spin. Not in creative or spectator. No text but the knock-out's own line.
 */
public final class SpinDizzy {
	/** Dizziness (ticks of spin) from which the warning signs start, and at which a spinner collapses. */
	public static final int WARN = 60, LIMIT = 120;
	/** Out cold this long after collapsing (ticks). */
	static final int OUT_TICKS = 200;
	/** Dizziness gained a tick spinning, and lost a tick not spinning (once up again). */
	static final int GAIN = 1, RECOVER = 1;
	/** The client is told at least every this many points of change (and always at 0 and at the limit). */
	static final int SYNC_STEP = 4;

	/** How dizzy a player is (0 to {@link #LIMIT}), synced to them for the sway. Not kept. */
	public static final AttachmentType<Integer> DIZZY = AttachmentRegistry.create(JaymosPowers.id("spin_dizzy"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Server: dizziness by player (the synced attachment follows it in steps). */
	private static final Map<UUID, Integer> LEVEL = new HashMap<>();

	private SpinDizzy() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpinDizzy::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LEVEL.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LEVEL.clear());
	}

	/** How woozy {@code player} is (0: not at all, up to {@link #WARN}; 1: at the limit). Either side (the client's is synced). */
	public static float woozy(Player player) {
		Integer level = player.getAttached(DIZZY);
		return level == null ? 0.0F : Mth.clamp((level - WARN) / (float) (LIMIT - WARN), 0.0F, 1.0F);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			int was = LEVEL.getOrDefault(id, 0);
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				set(player, id, 0);
				continue;
			}
			boolean spinning = SpellEngineCompat.spinning(player);
			if (spinning && was + GAIN >= LIMIT && !Downed.isDowned(player)) {
				collapse(player, id);
				continue;
			}
			int now;
			if (spinning) {
				now = Math.min(LIMIT, was + GAIN);
			} else if (Downed.isDowned(player)) {
				// still reeling while they lie there: it only starts to wear off once they're up
				now = was;
			} else {
				now = Math.max(0, was - RECOVER);
			}
			set(player, id, now);
		}
	}

	/** Spun too long: the spin stops and down they go, out cold. */
	private static void collapse(ServerPlayer player, UUID id) {
		SpellEngineCompat.stopSpin(player);
		set(player, id, LIMIT);
		Downed.knockOut((ServerLevel) player.level(), player, OUT_TICKS, false, "message.jaymospowers.spin_dizzy.collapse");
	}

	private static void set(ServerPlayer player, UUID id, int level) {
		if (level <= 0) {
			LEVEL.remove(id);
		} else {
			LEVEL.put(id, level);
		}
		Integer synced = player.getAttached(DIZZY);
		int shown = synced == null ? 0 : synced;
		if (shown == level) {
			return;
		}
		if (level <= 0) {
			player.removeAttached(DIZZY);
		} else if (level >= LIMIT || Math.abs(level - shown) >= SYNC_STEP) {
			player.setAttached(DIZZY, level);
		}
	}
}
