package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Taken alive. Andy (2026-11-19): "if you find yourself downed near barbarians they should enslave you".
 * <p>
 * A player down within {@link #NEAR} of a barbarian camp's people ({@link Captors#barbarian}: its raiders, its chief, its
 * camp folk) is theirs to take, so long as a camp stands to take them to ({@link Captors#campOf}). From then the camp's
 * people leave them lying: they don't take them for a target and their blows don't land ({@link Downed#holdsFire}), and
 * Powers' executions pass them over ({@link #spares}). After {@link #BIND_DELAY} the nearest of them walks over, stoops and
 * binds them ({@link #BIND_TICKS}), and they're knocked out cold; in the black ({@link #DARK_TICKS}) they're carried off
 * and wake in that camp's chains as one left for dead by them does ({@link Captors#take}: stripped, gear worn or buried,
 * shackled by the life story's chains, kept by {@link CaptiveConduct}). A captor hauls them up as for any beaten captive.
 * <p>
 * Not a death: no death roll, no wake used up. One who gets up before they're bound (brought round, or rising by
 * themselves) is fair game again, and one already in chains, lying left for dead, or put to death by their captors
 * ({@link CaptiveConduct#doomed}) is left to what already handles them. Only barbarians: orcs, goblins, pirates and the like
 * still finish the downed, and still take one they leave for dead.
 */
public final class TakenAlive {
	/** How near (blocks) a barbarian must be to the downed player to claim them. */
	static final double NEAR = 16.0;
	/** How far (blocks) the one who comes to bind them may be. */
	static final double BIND_RANGE = 32.0, BIND_REACH = 1.8;
	/** How long (ticks) they lie before one comes over, and how long the binding takes at their side. */
	static final int BIND_DELAY = 40, BIND_TICKS = 60;
	/** How long (ticks) they're out cold before they're carried off, and how long in all (they come to in the camp). */
	static final int DARK_TICKS = 60, OUT_TICKS = 140;
	/** How often (ticks) the downed are looked over for barbarians near by. */
	static final int LOOK_EVERY = 10;
	/** How long (ticks) a downed player with no camp to be taken to is let be before they're asked about again. */
	static final long NO_CAMP_TICKS = 200L;

	/** One downed and claimed: since when (game time), who binds them, how long at it, and when the black began. */
	private static final class Claim {
		final long since;
		@Nullable Mob binder;
		int ticks;
		long darkSince = -1L;

		Claim(long since) {
			this.since = since;
		}
	}

	private static final Map<UUID, Claim> CLAIMS = new HashMap<>();
	/** Downed players with no camp to be taken to → the game time to ask again. */
	private static final Map<UUID, Long> NO_CAMP = new HashMap<>();

	private TakenAlive() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TakenAlive::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CLAIMS.clear();
			NO_CAMP.clear();
		});
	}

	/**
	 * Whether {@code mob} leaves the downed {@code body} for its camp to take: a barbarian, and a downed player a camp can take
	 * (claimed now if not yet). Asked by {@link Downed#holdsFire} (targets and blows) and Powers' executions.
	 */
	public static boolean spares(Mob mob, net.minecraft.world.entity.LivingEntity body) {
		return body instanceof ServerPlayer player && Captors.barbarian(mob) && claim(player, mob) != null;
	}

	/** {@code player}'s claim, made now by {@code by}'s camp (or the nearest) if they can be taken; null if they can't. */
	private static @Nullable Claim claim(ServerPlayer player, Mob by) {
		Claim claim = CLAIMS.get(player.getUUID());
		if (claim != null) {
			return claim;
		}
		if (!takeable(player)) {
			return null;
		}
		long now = player.level().getGameTime();
		Long retry = NO_CAMP.get(player.getUUID());
		if (retry != null && now < retry) {
			return null;
		}
		Faction camp = Captors.campOf(player.level().getServer(), by, player.blockPosition());
		if (camp == null || camp.fallen() || camp.seat() == null) {
			NO_CAMP.put(player.getUUID(), now + NO_CAMP_TICKS);
			return null;
		}
		NO_CAMP.remove(player.getUUID());
		claim = new Claim(now);
		CLAIMS.put(player.getUUID(), claim);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.taken_alive.claimed").withStyle(ChatFormatting.RED));
		return claim;
	}

	/** Down, alive and in the game, not already in chains or lying left for dead (those have their own ends). */
	private static boolean takeable(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative() && Downed.isDowned(player) && !StoryHard.shackled(player)
			&& !PowersCompat.waking(player);
	}

	/** Barbarians on their feet who could come for {@code player}: alive, thinking, not downed, on the same ground. */
	private static boolean able(Mob m, ServerPlayer player) {
		return m.isAlive() && !m.isNoAi() && !m.isBaby() && !m.isPassenger() && !Downed.isDowned(m) && m.level() == player.level()
			&& Captors.barbarian(m);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (CLAIMS.containsKey(player.getUUID()) || player.level().getGameTime() % LOOK_EVERY != 0L || !takeable(player)) {
				continue;
			}
			// (a barbarian near by, who sees them, claims them for the camp)
			Mob near = nearest(player, NEAR, m -> able(m, player) && m.hasLineOfSight(player));
			if (near != null) {
				claim(player, near);
			}
		}
		if (CLAIMS.isEmpty()) {
			NO_CAMP.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
			return;
		}
		for (Iterator<Map.Entry<UUID, Claim>> it = CLAIMS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Claim> entry = it.next();
			Claim claim = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			boolean dark = claim.darkSince >= 0L;
			if (player == null || !player.isAlive() || StoryHard.shackled(player) || (!dark && !takeable(player))) {
				// (got up, died, logged out, or already held: the claim lapses)
				it.remove();
				stand(claim);
				continue;
			}
			if (step(player, claim)) {
				it.remove();
				stand(claim);
			}
		}
	}

	/** One tick of taking {@code player}; true once it's over (taken, or it came to nothing). */
	private static boolean step(ServerPlayer player, Claim claim) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (claim.darkSince >= 0L) {
			if (now - claim.darkSince < DARK_TICKS) {
				return false;
			}
			carryOff(player, claim);
			return true;
		}
		if (now - claim.since < BIND_DELAY || player.isPassenger() || PowersCompat.isCarried(player)) {
			return false;
		}
		Mob binder = claim.binder;
		if (binder != null && (!able(binder, player) || binder.distanceToSqr(player) > BIND_RANGE * BIND_RANGE * 1.5)) {
			stand(claim);
			binder = null;
		}
		if (binder == null) {
			if (now % LOOK_EVERY != 0L) {
				return false;
			}
			binder = nearest(player, BIND_RANGE, m -> able(m, player) && CLAIMS.values().stream().noneMatch(c -> c.binder == m));
			if (binder == null) {
				return false;
			}
			claim.binder = binder;
			claim.ticks = 0;
		}
		if (binder.getTarget() == player) {
			binder.setTarget(null);
		}
		if (binder.distanceToSqr(player) > BIND_REACH * BIND_REACH) {
			binder.setShiftKeyDown(false);
			if (player.tickCount % 10 == 0) {
				binder.getNavigation().moveTo(player, 1.0);
			}
			return false;
		}
		binder.getNavigation().stop();
		binder.getLookControl().setLookAt(player.getX(), player.getY() + 0.2, player.getZ());
		binder.setShiftKeyDown(true);
		if (++claim.ticks % 20 == 0) {
			binder.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.8F, 0.7F);
		}
		if (claim.ticks < BIND_TICKS) {
			return false;
		}
		// (bound: a blow to the head, and the black)
		binder.setShiftKeyDown(false);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.taken_alive.bound").withStyle(ChatFormatting.DARK_RED));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
		if (!PowersCompat.knockOut(level, player, OUT_TICKS)) {
			// (without Powers' black: taken where they lie)
			carryOff(player, claim);
			return true;
		}
		claim.darkSince = now;
		return false;
	}

	/** In the black: chained in the camp, stripped as one left for dead by them is. */
	private static void carryOff(ServerPlayer player, Claim claim) {
		Mob by = claim.binder;
		GlobalPos at = Captors.take(player, by);
		if (at == null) {
			JaymosKingdoms.LOGGER.info("{} was bound, but no camp stood to take them to", player.getScoreboardName());
			return;
		}
		ServerLevel to = player.level().getServer().getLevel(at.dimension());
		if (to == null) {
			return;
		}
		Vec3 spot = Vec3.atBottomCenterOf(at.pos());
		player.teleportTo(to, spot.x, spot.y, spot.z, Set.<net.minecraft.world.entity.Relative>of(), player.getRandom().nextFloat() * 360.0F, 0.0F, true);
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		JaymosKingdoms.LOGGER.info("{} was taken alive by barbarians", player.getScoreboardName());
	}

	/** The binder stands up again. */
	private static void stand(Claim claim) {
		if (claim.binder != null) {
			claim.binder.setShiftKeyDown(false);
			claim.binder = null;
		}
	}

	/** The nearest mob within {@code range} of {@code player} that {@code which} allows, or null. */
	private static @Nullable Mob nearest(ServerPlayer player, double range, java.util.function.Predicate<Mob> which) {
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range), which)) {
			double d = m.distanceToSqr(player);
			if (d < bestDistance) {
				best = m;
				bestDistance = d;
			}
		}
		return best;
	}
}
