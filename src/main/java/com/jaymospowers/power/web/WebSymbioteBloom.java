package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The bloom (patch 4 of the 1.1.3 picks). The big body's head splits into a crown of petal tendrils when it is
 * <b>cornered</b> ({@link #CORNER_FOES} foes or more on you within {@link WebSymbioteArm#FOE_RANGE} and your health at or
 * under {@link #CORNER_HEALTH} of full), <b>starving</b> (satiety at or under {@link #STARVING}) or <b>panicking</b>
 * (stress at or over {@link #PANIC}). The server decides ({@link #tick}) and syncs {@link #BLOOM} to everyone; the client
 * draws the petals on the big head ({@code WebBloom}). It holds for at least {@link #HOLD} ticks and closes once the cause
 * has passed, resting {@link #REST} ticks before it opens again.
 */
public final class WebSymbioteBloom {
	/** Foes that make a corner, and how low your health (a share of full) must be to be cornered. */
	public static final int CORNER_FOES = 3;
	public static final float CORNER_HEALTH = 0.4F;
	/** Satiety at or under this is starving; stress at or over this is panic. */
	public static final int STARVING = 8, PANIC = 85;
	/** It stays open at least this long, and the closing takes this long on the client; then it rests this long. */
	public static final int HOLD = 240, CLOSE = 14, REST = 200;
	static final int EVERY = 10;
	static final int LINES = 3;

	/**
	 * The bloom as the client reads it, synced to everyone: the game time it opened (positive), or minus the game time it
	 * began to close (negative), absent when it is shut. Not saved.
	 */
	public static final AttachmentType<Long> BLOOM = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_bloom"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private static final Map<UUID, Long> RESTS_UNTIL = new HashMap<>();

	private WebSymbioteBloom() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % EVERY == 0) {
					tick(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RESTS_UNTIL.remove(handler.player.getUUID()));
	}

	/** The bloom is open (or opening) on this player: the client's crown, and anything that wants to know. */
	public static boolean open(Player player) {
		Long at = player.getAttached(BLOOM);
		return at != null && at > 0L;
	}

	/** What brought it on ({@code corner}, {@code starve}, {@code panic}), or null. */
	private static String cause(ServerPlayer player, WebSymbioteState state) {
		if (WebSymbioteArm.foes(player, WebSymbioteArm.FOE_RANGE).size() >= CORNER_FOES && player.getHealth() <= player.getMaxHealth() * CORNER_HEALTH) {
			return "corner";
		}
		if (state.satiety() <= STARVING) {
			return "starve";
		}
		if (state.stress() >= PANIC) {
			return "panic";
		}
		return null;
	}

	private static void tick(ServerPlayer player) {
		Long at = player.getAttached(BLOOM);
		WebSymbioteState state = WebSymbiote.state(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		boolean big = state.bonded() && WebSymbiote.bulked(player) && player.isAlive();
		if (at != null && at < 0L) {
			// closing: gone once the client has had time to fold it away
			if (now + at >= CLOSE) {
				player.removeAttached(BLOOM);
			}
			return;
		}
		String cause = big ? cause(player, state) : null;
		if (at == null) {
			Long rest = RESTS_UNTIL.get(player.getUUID());
			if (cause != null && (rest == null || now >= rest)) {
				player.setAttached(BLOOM, now);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.6F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 0.6F);
				WebSymbiote.whisper(player, "bloom." + cause + "." + (1 + player.getRandom().nextInt(LINES)));
			}
			return;
		}
		if (!big || cause == null && now - at >= HOLD) {
			player.setAttached(BLOOM, -now);
			RESTS_UNTIL.put(player.getUUID(), now + REST);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.7F, 0.6F);
		}
	}
}
