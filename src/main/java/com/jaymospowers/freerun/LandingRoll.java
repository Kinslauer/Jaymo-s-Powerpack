package com.jaymospowers.freerun;

import com.jaymospowers.stamina.Dodge;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * (0.15.67) Sure Footing (Andy: "your roll negates fall damage if done while landing, give it a graceful window"): a
 * dodge ({@link Dodge}) made as you land takes all the fall damage away. The window is wide both ways:
 * <ul>
 * <li>early: the dodge key pressed in the air up to {@link #EARLY_TICKS} before touchdown is held by the client
 * ({@code DodgeClient}) and rolls you the moment you land;</li>
 * <li>late: a Sure Footing player's fall damage is held back {@link #LATE_TICKS} (plus {@link #SLACK_TICKS} for the
 * network) after touchdown; a dodge in that time cancels it, otherwise it lands as it would have, a blink late.</li>
 * </ul>
 * The roll is the ordinary dodge, so it costs its feathers; with none left there is no roll and the fall hurts.
 */
public final class LandingRoll {
	// ===== the knobs ================================================================================================
	/** Client: a dodge pressed this many ticks (0.5 s) before landing still rolls you as you land. */
	public static final int EARLY_TICKS = 10;
	/** Server: a dodge this many ticks (0.3 s) after landing still takes the fall damage away. */
	public static final int LATE_TICKS = 6;
	/** Server: extra ticks for the client's dodge to arrive. */
	static final int SLACK_TICKS = 4;
	// ================================================================================================================

	private record Pending(DamageSource source, float amount, long at) {
	}

	/** Server: fall damage held back, by player. */
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	/** Server: game time of each player's last dodge. */
	private static final Map<UUID, Long> ROLLED = new HashMap<>();
	/** True while held-back damage is dealt, so it isn't held back again. */
	private static boolean dealing;

	private LandingRoll() {
	}

	/** After {@link Freerun}'s own fall rule (Leap of Faith), so a soft landing is never held back. */
	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (dealing || !(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL) || Freerun.perk(player, Freerun.Perk.SURE_FOOTING) == 0) {
				return true;
			}
			long now = player.level().getGameTime();
			Long rolled = ROLLED.get(player.getUUID());
			if (rolled != null && now - rolled <= SLACK_TICKS) {
				// the roll got here first
				rolled(player);
				return false;
			}
			PENDING.put(player.getUUID(), new Pending(source, amount, now));
			return false;
		});
		Dodge.onDodge((player, direction) -> {
			long now = player.level().getGameTime();
			ROLLED.put(player.getUUID(), now);
			Pending pending = PENDING.remove(player.getUUID());
			if (pending != null) {
				rolled(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(LandingRoll::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// leaving doesn't dodge the fall
			Pending pending = PENDING.remove(handler.player.getUUID());
			if (pending != null) {
				deal(handler.player, pending);
			}
			ROLLED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			PENDING.clear();
			ROLLED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Pending> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			if (player.level().getGameTime() - entry.getValue().at() > LATE_TICKS + SLACK_TICKS) {
				it.remove();
				deal(player, entry.getValue());
			}
		}
	}

	private static void deal(ServerPlayer player, Pending pending) {
		if (!player.isAlive()) {
			return;
		}
		dealing = true;
		try {
			player.hurtServer(player.level(), pending.source(), pending.amount());
		} finally {
			dealing = false;
		}
	}

	/** The fall rolled off: a thud, a puff of dust, and a little Stealth experience. */
	private static void rolled(ServerPlayer player) {
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.PLAYERS, 0.8F, 0.9F);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.4, 0.05, 0.4, 0.03);
		player.addTag(Freerun.XP_TAG);
	}
}
