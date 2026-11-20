package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Taken while out cold. Andy (2026-11-19), of a faint's black: "a screen like this where you go unconscious should reopen
 * your eyes enslaved or whatever else happened instead of a death roll".
 * <p>
 * A player who blacks out without dying (a faint, {@link Fainting}; knocked out cold, {@link Downed#knockOut}; passed out
 * lying downed) with captors, a town's guards who want them, or spiders round them is taken in the black: once
 * {@link #TAKE_AFTER} into it, {@link Mortality#takenBy} asks who has them, and they open their eyes there instead of
 * where they fell (in chains, in the cells, wrapped in a lair), with the wake's line read on the black as a death's wake
 * does. Nobody who'd take them near, and they come round where they lie as before. Not a death: no roll, no injury,
 * nothing used up.
 * <p>
 * Not a scene's black (one with its own line: a death turned away, a turning, the descent below), not one carried in
 * someone's arms, and not creative or spectating.
 */
public final class TakenOut {
	/** How far into the black (ticks) those round them take them. */
	static final int TAKE_AFTER = 40;
	/** How often (ticks) players are looked over. */
	static final int EVERY = 5;

	/** Players in a black → the game time it began. */
	private static final Map<UUID, Long> OUT_SINCE = new HashMap<>();
	/** Players whose black has been asked about already (once a black). */
	private static final Set<UUID> ASKED = new HashSet<>();

	private TakenOut() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TakenOut::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OUT_SINCE.clear();
			ASKED.clear();
		});
	}

	/** Whether {@code player} lies blacked out of their own accord: fainted, knocked out cold, or passed out downed. */
	static boolean blackedOut(ServerPlayer player) {
		if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.hasAttached(Mortality.WAKE_LINE)
			|| player.hasAttached(Mortality.REBORNING) || Mortality.waking(player) || Mortality.justWoke(player) || Carry.isCarried(player)) {
			return false;
		}
		return Fainting.out(player) || Downed.isDowned(player) && Downed.passedOut(player);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (!blackedOut(player)) {
				OUT_SINCE.remove(id);
				ASKED.remove(id);
				continue;
			}
			long now = player.level().getGameTime();
			long since = OUT_SINCE.computeIfAbsent(id, k -> now);
			if (now - since < TAKE_AFTER || !ASKED.add(id)) {
				continue;
			}
			Mortality.Taken taken = Mortality.takenBy(player, null);
			if (taken != null) {
				take(player, taken);
			}
		}
		OUT_SINCE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		ASKED.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** In the black: put where they were taken, and out cold a while longer there with the wake's line, then they come round. */
	private static void take(ServerPlayer player, Mortality.Taken taken) {
		ServerLevel to = player.level().getServer().getLevel(taken.at().dimension());
		if (to == null) {
			return;
		}
		BlockPos at = taken.at().pos();
		Vec3 spot = Vec3.atBottomCenterOf(at);
		player.stopRiding();
		player.teleportTo(to, spot.x, spot.y, spot.z, Set.<net.minecraft.world.entity.Relative>of(), player.getRandom().nextFloat() * 360.0F, 0.0F, true);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		if ("lair".equals(taken.line())) {
			com.jaymospowers.power.web.Webbed.wrap(player, Mortality.LAIR_WEB_TICKS, com.jaymospowers.power.web.Webbed.MOST);
		}
		// (cells and captors keep them from bleeding out where they wake)
		Mortality.tendTaken(player, taken.line());
		// (a faint gives way to the knock-out's black; one down stays down and comes round still down)
		String woke = "message.jaymospowers.mortality.wake." + taken.line();
		player.setAttached(Mortality.WAKE_LINE, woke);
		Downed.knockOut(to, player, Mortality.OUT_AFTER, false, woke, false);
		to.playSound(null, at, SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.8F);
		JaymosPowers.LOGGER.info("{} was taken while out cold ({}) to {}", player.getScoreboardName(), taken.line(), at.toShortString());
	}
}
