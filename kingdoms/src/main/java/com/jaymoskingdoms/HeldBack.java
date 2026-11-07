package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.570) Andy (2026-11-05), after a thrown rock struck one of a town's people by accident, the town's fighters flung him
 * across the street and executed him on the spot: "if that many guards are there they will force one another to do the
 * just thing, so as not to attract bad attention". Three parts:
 * <ul>
 * <li><b>Held to the law</b> ({@link #spares}, asked by Powers' executions through {@code PowersCompat}): a town's own
 * people (its guards and archers, its sworn, its villagers and civilians) never execute a downed player while another of
 * them stands within {@link #WATCHED} blocks, and never over a small thing. Only a hotheaded or spiteful one, alone, may
 * finish off someone the town counts a real enemy ({@link #serious}: an outlaw there, hated, or at war with it). A
 * village's own iron golem never does, as before.</li>
 * <li><b>Taken in, not cut down</b> ({@link #assaulted}): a player who struck one of a town's people and lies downed within
 * {@link #TAKE_RANGE} of one of its guards in the next {@link #ASSAULT_TICKS} is taken in: the fight is called off and
 * they are got up. A kingdom or fiefdom (which keeps cells under its seat, {@link Busts}) locks them up for
 * {@link #JAIL_TICKS}; elsewhere they pay a fine ({@link #FINE}) or, without the coin, are let off with a warning.</li>
 * <li><b>No catapults</b> ({@link #STRUCK}): a blow from one of a town's people (or any villager or folk fighter but an
 * orc or a ruler) throws a player no harder than {@link #THROW_MOST} along the ground and {@link #LIFT_MOST} up (a downed
 * one, {@link #DOWNED_THROW}), however many shoves and form pushes land in that same tick. Several guards' blows, each
 * with its own push on top of the knockback, had stacked into a throw of some fourteen blocks.</li>
 * </ul>
 * Nothing is saved.
 */
final class HeldBack {
	// ---- tuning ----
	/** Another of the town's people this near holds an executioner back (blocks). */
	static final double WATCHED = 16.0;
	/** How long after a blow on one of a town's people its guards may take the downed player in for it (ticks). */
	static final long ASSAULT_TICKS = 20L * 120;
	/** How near a guard must be to a downed player to take them in (blocks). */
	static final double TAKE_RANGE = 6.0;
	/** A realm with cells locks a downed assailant up this long (ticks): shorter than a contraband sentence. */
	static final long JAIL_TICKS = 20L * 60 * 2;
	/** Where there are no cells: the fine for a blow on one of a town's people, in emeralds' worth of coin. */
	static final long FINE = 6;
	/** How far everyone's called off the player when they're taken in (blocks). */
	static final double CALL_OFF = 32.0;
	/** The most a town person's blow (and everything else that tick) throws a player: along the ground, up; a downed one. */
	static final double THROW_MOST = 0.6, LIFT_MOST = 0.4, DOWNED_THROW = 0.25, DOWNED_LIFT = 0.2;
	/** Checked this often (ticks). */
	static final int CHECK = 10;
	/** Lines each (lang {@code held_back.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;
	static final double SPEECH = 24.0;

	/** Player → {realm id, until} of their last blow on one of its people. */
	private static final Map<UUID, long[]> ASSAULTS = new HashMap<>();
	/** Players a town person struck this tick (their throw is capped at the end of it). */
	private static final Set<UUID> STRUCK = new HashSet<>();

	private HeldBack() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && source.getEntity() instanceof Mob mob && thrower(mob)) {
				STRUCK.add(player.getUUID());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(HeldBack::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ASSAULTS.clear();
			STRUCK.clear();
		});
	}

	// ---- who -----------------------------------------------------------------------------------------------------------

	/** One of a town's own people: a guard or archer, a sworn fighter, a grown villager, or a settled faction's folk (no orcs, goblins, barbarians or rulers). */
	static boolean townsperson(LivingEntity e) {
		if (!e.isAlive() || e.isBaby()) {
			return false;
		}
		if (Downed.isGuard(e) || HavenGrowth.sworn(e) || e instanceof Villager) {
			return true;
		}
		if (!(e instanceof Folk folk) || folk.leader() || Orcs.isOrc(folk) || Goblins.isGoblin(folk) || folk.style() == FactionType.BARBARIAN) {
			return false;
		}
		MinecraftServer server = folk.level().getServer();
		Faction f = server == null ? null : Factions.get(server, folk.factionId());
		return f != null && !f.fallen() && f.type() != FactionType.BARBARIAN;
	}

	/** Whose blows are capped: a town's people, and any villager or folk fighter but an orc or a ruler. */
	private static boolean thrower(Mob mob) {
		return townsperson(mob) || mob instanceof Folk folk && !folk.leader() && !Orcs.isOrc(folk);
	}

	/** The realm {@code e} answers to: a folk's own, else the law where it stands; null for none. */
	private static @Nullable Faction realmOf(ServerLevel level, LivingEntity e) {
		if (e instanceof Worker w && w.factionId() >= 0) {
			Faction f = Factions.get(level.getServer(), w.factionId());
			if (f != null && !f.fallen()) {
				return f;
			}
		}
		return Theft.lawAt(level, e.blockPosition());
	}

	// ---- held to the law ---------------------------------------------------------------------------------------------

	/** (From Powers' executions) whether {@code mob} leaves downed {@code victim} be rather than execute them. */
	static boolean spares(Mob mob, LivingEntity victim) {
		if (mob instanceof IronGolem golem) {
			return !golem.isPlayerCreated();
		}
		if (!townsperson(mob) || !(victim instanceof ServerPlayer player) || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		if (Unmasking.awaits(player)) {
			// (0.7.473) a masked player downed in town: the guards see who it is before anything else
			return true;
		}
		Temperaments.Temperament t = Temperaments.of(mob);
		if (t != Temperaments.Temperament.HOTHEADED && t != Temperaments.Temperament.SPITEFUL) {
			// righteous: they arrest
			return true;
		}
		// a hothead alone, over something that matters to the town: the only one who'd do it
		return watched(level, mob) || !serious(level, mob, player);
	}

	/** Another of the town's people on their feet near {@code mob}, to hold it to the law. */
	private static boolean watched(ServerLevel level, Mob mob) {
		AABB box = mob.getBoundingBox().inflate(WATCHED);
		return !level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != mob && townsperson(e) && !Downed.isDowned(e) && !e.isSleeping() && e.distanceTo(mob) <= WATCHED).isEmpty();
	}

	/** Whether {@code player} is a real enemy of the town {@code mob} serves: outlawed, hated or at war. A blow or a theft isn't. */
	private static boolean serious(ServerLevel level, Mob mob, ServerPlayer player) {
		Faction f = realmOf(level, mob);
		if (f == null) {
			return false;
		}
		return Theft.outlaw(player, f.id()) || Reputation.get(player, f.id()) <= Reputation.HOSTILE || Wars.enemy(player, f);
	}

	// ---- taken in ----------------------------------------------------------------------------------------------------

	/** (From {@link Retaliation}) {@code player} struck one of {@code f}'s people, and it was seen. */
	static void assaulted(ServerPlayer player, Faction f, long now) {
		ASSAULTS.put(player.getUUID(), new long[] {f.id(), now + ASSAULT_TICKS});
	}

	private static void tick(MinecraftServer server) {
		if (!STRUCK.isEmpty()) {
			for (UUID id : STRUCK) {
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player != null && player.isAlive()) {
					cap(player);
				}
			}
			STRUCK.clear();
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() == level && player.isAlive() && Downed.isDowned(player)) {
				standDown(level, player);
			}
		}
		if (ASSAULTS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, long[]>> it = ASSAULTS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Faction f = Factions.get(server, (int) e.getValue()[0]);
			if (player == null || f == null || now > e.getValue()[1]) {
				it.remove();
				continue;
			}
			if (player.level() != level || !player.isAlive() || !Downed.isDowned(player) || Unmasking.awaits(player)) {
				continue;
			}
			PathfinderMob guard = guardNear(level, f, player);
			if (guard != null) {
				it.remove();
				takeIn(level, player, f, guard);
			}
		}
	}

	/**
	 * (0.7.571) A downed player is not shot or cut at by the town's people who'd spare them ({@link #spares}): they stop,
	 * and its guards walk over to take them ({@link Busts}, {@link #assaulted}). Andy, broken out of the cells: downed in
	 * the street, then shot dead where he lay.
	 */
	private static void standDown(ServerLevel level, ServerPlayer player) {
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CALL_OFF), m -> m.isAlive() && townsperson(m))) {
			boolean after = m.getTarget() == player || m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
				&& m.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player;
			if (!after || !spares(m, player)) {
				continue;
			}
			m.setTarget(null);
			if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
			if (Downed.isGuard(m) && m.distanceTo(player) > 2.5) {
				m.getNavigation().moveTo(player, 1.0);
			}
		}
	}

	/** The nearest of {@code f}'s guards (or sworn) on their feet within {@link #TAKE_RANGE} of {@code player}. */
	private static @Nullable PathfinderMob guardNear(ServerLevel level, Faction f, ServerPlayer player) {
		PathfinderMob best = null;
		for (Worker w : Workers.near(level, player.getBoundingBox().inflate(TAKE_RANGE), w -> w.factionId() == f.id())) {
			PathfinderMob mob = w.mob();
			if (!mob.isAlive() || Downed.isDowned(mob) || mob.isSleeping() || !(Downed.isGuard(mob) || HavenGrowth.sworn(mob))) {
				continue;
			}
			if (best == null || mob.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = mob;
			}
		}
		return best;
	}

	/** The fight's called off and {@code player} is got up: to the cells where the realm has them, else a fine (or a warning). */
	private static void takeIn(ServerLevel level, ServerPlayer player, Faction f, PathfinderMob guard) {
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CALL_OFF), m -> m.isAlive())) {
			if (m.getTarget() == player) {
				m.setTarget(null);
			}
			// (as Rulers.target: a brain without the memory throws on getMemory)
			if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
				&& m.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player) {
				m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
		}
		// got up to answer for it
		player.addTag(Downed.REVIVE_TAG);
		player.addTag(Downed.BANDAGED_TAG);
		guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (f.type().isRealm() && !Busts.jailed(player)) {
			// (Andy: "if they have a jail, you go there") a kingdom or fiefdom keeps cells under its seat (Busts'): a short stay
			Underworld.Den d = Underworld.den(level.getServer(), f.id());
			BlockPos cell = Speakeasy.cell(level, f, d == null ? null : d.cell().orElse(null));
			if (d != null) {
				Underworld.putCell(level.getServer(), d, cell);
			}
			Speakeasy.cellDoor(level, cell);
			long now = level.getGameTime();
			player.setAttached(Busts.JAILED, new Busts.Jail(f.id(), cell, now + JAIL_TICKS));
			Speech.say(level, guard, Component.translatable("held_back.jaymoskingdoms.cells." + level.getRandom().nextInt(LINES)), Speech.RED, SPEECH);
			Vec3 c = Vec3.atBottomCenterOf(cell);
			player.teleportTo(level, c.x, c.y, c.z, Set.of(), player.getYRot(), 0.0F, false);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0F, 0.7F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.held_back.jailed", f.name(), JAIL_TICKS / 1200L)
				.withStyle(ChatFormatting.GOLD));
			return;
		}
		long fine = Money.worth(FINE);
		boolean paid = Money.charge(player, f, fine);
		String key = paid ? "fine" : "warning";
		Speech.say(level, guard, Component.translatable("held_back.jaymoskingdoms." + key + "." + level.getRandom().nextInt(LINES)),
			paid ? Speech.YELLOW : Speech.WHITE, SPEECH);
		level.playSound(null, guard.getX(), guard.getY(), guard.getZ(), paid ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.9F);
		player.sendSystemMessage((paid ? Component.translatable("message.jaymoskingdoms.held_back.fined", f.name(), Money.text(f, fine))
			: Component.translatable("message.jaymoskingdoms.held_back.warned", f.name())).withStyle(ChatFormatting.GOLD));
	}

	// ---- no catapults ------------------------------------------------------------------------------------------------

	/** {@code player}'s throw this tick held to what one blow should do. */
	private static void cap(ServerPlayer player) {
		boolean down = Downed.isDowned(player);
		double most = down ? DOWNED_THROW : THROW_MOST, lift = down ? DOWNED_LIFT : LIFT_MOST;
		Vec3 v = player.getDeltaMovement();
		double flat = Math.sqrt(v.x * v.x + v.z * v.z);
		double scale = flat > most ? most / flat : 1.0;
		if (scale >= 1.0 && v.y <= lift) {
			return;
		}
		player.setDeltaMovement(v.x * scale, Math.min(v.y, lift), v.z * scale);
		player.needsSync = true;
	}
}
