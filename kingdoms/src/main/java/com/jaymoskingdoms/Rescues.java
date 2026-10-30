package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.368) Andy: "villagers should say something about you picking them up if theyre downed, or if you save them from an
 * attack or otherwise prevent them harm." What a player does for one of a settlement's people while they lie downed or
 * are under attack, heard from Jaymo's Powers (its listener lists, {@link PowersCompat#listen}), is said aloud on the spot
 * (a speech bubble, warm, plain or gruff as their temperament is) and remembered ({@link Recollections}), so they bring it
 * up after, MCA's menu opens on it and "Ask how they are" is about it ({@link SharedHistory}):
 * <ul>
 * <li>Lifted while downed (or snatched up with a monster on them): a word as you pick them up; set down after a real
 * carry ({@link #CARRY_LEAST} blocks, or {@link #CARRY_TICKS}), they remember being carried out ({@link Recollections.Kind#CARRIED}).
 * Thrown down instead, they remember that ({@link Recollections.Kind#THREW}).</li>
 * <li>Got back up by your revive or smelling salts ({@link Recollections.Kind#GOT_UP}).</li>
 * <li>Their wounds dressed by your hand ({@link Recollections.Kind#TENDED}; spoken at most every {@link #TENDED_GAP}).</li>
 * <li>A monster that was after them struck by you, and a moment later going for someone else (you) or downed itself
 * ({@link Recollections.Kind#SHIELDED}). Killed outright is {@code Deeds}' saved life, as before.</li>
 * </ul>
 */
public final class Rescues {
	/** A carry counts as carrying them out when it went this far (blocks), or lasted this long (ticks). */
	static final double CARRY_LEAST = 5.0;
	static final long CARRY_TICKS = 100L;
	/** A non-downed one snatched up counts as a rescue with a monster this near going for them (blocks). */
	static final double DANGER_RANGE = 16.0;
	/** How soon a struck monster is looked at again (ticks), how near its quarry it must have been (blocks), and the least gap between two of these for the same pair (ticks). */
	static final long SHIELD_CHECK = 40L, SHIELD_GAP = 1200L;
	static final double SHIELD_NEAR = 10.0;
	/** The least gap between two spoken thanks for dressings, same pair (ticks). */
	static final long TENDED_GAP = 2400L;
	/** Lines per moment per tone. */
	static final int LINES = 3;

	private record Lift(UUID player, Vec3 from, long at) {
	}

	private record Shield(Mob attacker, LivingEntity victim, UUID player, long due) {
	}

	/** One being carried by a player for us → whence and since when. */
	private static final Map<LivingEntity, Lift> LIFTED = new WeakHashMap<>();
	/** Struck monsters, to see whether they let their quarry be. */
	private static final List<Shield> SHIELDS = new ArrayList<>();
	/** "player/them/what" → when it was last spoken of (game time). */
	private static final Map<String, Long> SPOKEN = new HashMap<>();

	private Rescues() {
	}

	static void register() {
		PowersCompat.<Entity>listen("com.jaymospowers.core.Carry", "PICKED_UP_LISTENERS", Rescues::pickedUp);
		PowersCompat.<Entity>listen("com.jaymospowers.core.Carry", "SET_DOWN_LISTENERS", (player, body) -> letGo(player, body, false));
		PowersCompat.<Entity>listen("com.jaymospowers.core.Carry", "THROWN_LISTENERS", (player, body) -> letGo(player, body, true));
		PowersCompat.<LivingEntity>listen("com.jaymospowers.core.wounds.Downed", "BROUGHT_ROUND_LISTENERS", Rescues::broughtRound);
		PowersCompat.<LivingEntity>listen("com.jaymospowers.core.wounds.Treatment", "TENDED_LISTENERS", Rescues::tended);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (damage > 0.0F && source.getEntity() instanceof ServerPlayer player && entity instanceof Mob attacker) {
				struck(player, attacker);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Rescues::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LIFTED.clear();
			SHIELDS.clear();
			SPOKEN.clear();
		});
	}

	// ---- what happened -----------------------------------------------------------------------------------------------

	private static void pickedUp(ServerPlayer player, Entity body) {
		if (!(body instanceof LivingEntity who) || !Recollections.rememberer(who) || !(Downed.isDowned(who) || inDanger(who))) {
			return;
		}
		LIFTED.put(who, new Lift(player.getUUID(), who.position(), who.level().getGameTime()));
		say(player, who, "lifted", null);
	}

	private static void letGo(ServerPlayer player, Entity body, boolean thrown) {
		if (!(body instanceof LivingEntity who)) {
			return;
		}
		Lift lift = LIFTED.remove(who);
		if (lift == null || !lift.player().equals(player.getUUID()) || !who.isAlive()) {
			return;
		}
		if (thrown) {
			Recollections.remember(who, player, Recollections.Kind.THREW, Component.empty());
			say(player, who, "thrown", null);
			return;
		}
		if (who.position().distanceTo(lift.from()) < CARRY_LEAST && who.level().getGameTime() - lift.at() < CARRY_TICKS) {
			// (picked up and put straight back: nothing to speak of)
			return;
		}
		Recollections.remember(who, player, Recollections.Kind.CARRIED, Component.empty());
		say(player, who, "set_down", null);
	}

	private static void broughtRound(ServerPlayer player, LivingEntity who) {
		if (!Recollections.rememberer(who) || !who.isAlive()) {
			return;
		}
		LIFTED.remove(who);
		Recollections.remember(who, player, Recollections.Kind.GOT_UP, Component.empty());
		say(player, who, "got_up", null);
	}

	private static void tended(ServerPlayer player, LivingEntity who) {
		if (!Recollections.rememberer(who) || !who.isAlive()) {
			return;
		}
		Recollections.remember(who, player, Recollections.Kind.TENDED, Component.empty());
		if (fresh(player, who, "tended", TENDED_GAP)) {
			say(player, who, "tended", null);
		}
	}

	/** {@code player} struck {@code attacker}: if it was going for one of ours, see in a moment whether it let them be. */
	private static void struck(ServerPlayer player, Mob attacker) {
		LivingEntity victim = attacker.getTarget();
		if (victim == null || victim == player || !Recollections.rememberer(victim) || Recollections.rememberer(attacker) || Deeds.friendly(attacker)
			|| !victim.isAlive() || Downed.isDowned(victim) || attacker.distanceTo(victim) > SHIELD_NEAR) {
			return;
		}
		for (Shield s : SHIELDS) {
			if (s.attacker() == attacker && s.victim() == victim) {
				return;
			}
		}
		SHIELDS.add(new Shield(attacker, victim, player.getUUID(), attacker.level().getGameTime() + SHIELD_CHECK));
	}

	private static void tick(MinecraftServer server) {
		if (SHIELDS.isEmpty()) {
			return;
		}
		SHIELDS.removeIf(s -> {
			if (s.attacker().level().getGameTime() < s.due()) {
				return false;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(s.player());
			Mob attacker = s.attacker();
			LivingEntity victim = s.victim();
			// (killed is a life saved, Deeds' to speak of; still after them, or they went down anyway, isn't a rescue)
			if (player != null && attacker.isAlive() && !attacker.isRemoved() && victim.isAlive() && !Downed.isDowned(victim)
				&& (attacker.getTarget() != victim || Downed.isDowned(attacker)) && victim.level() == player.level()
				&& victim.distanceTo(player) <= Deeds.SAVED_RANGE && fresh(player, victim, "shielded", SHIELD_GAP)) {
				Recollections.remember(victim, player, Recollections.Kind.SHIELDED, attacker.getName().copy());
				say(player, victim, "shielded", attacker.getName());
			}
			return true;
		});
	}

	// ---- saying it ---------------------------------------------------------------------------------------------------

	/** {@code who}, not downed, has a monster this near going for them. */
	private static boolean inDanger(LivingEntity who) {
		return !who.level().getEntitiesOfClass(Mob.class, who.getBoundingBox().inflate(DANGER_RANGE),
			m -> m instanceof Enemy && m.isAlive() && m.getTarget() == who).isEmpty();
	}

	/** Not spoken of between these two for {@code gap} ticks (and now it is). */
	private static boolean fresh(ServerPlayer player, LivingEntity who, String what, long gap) {
		long now = who.level().getGameTime();
		String key = player.getUUID() + "/" + who.getUUID() + "/" + what;
		Long last = SPOKEN.get(key);
		if (last != null && now - last < gap) {
			return false;
		}
		if (SPOKEN.size() > 512) {
			SPOKEN.values().removeIf(t -> now - t > SHIELD_GAP + TENDED_GAP);
		}
		SPOKEN.put(key, now);
		return true;
	}

	/** {@code who} says the {@code moment}'s line to {@code player}, in their temperament's tone ({@code what}: the monster, if any). */
	private static void say(ServerPlayer player, LivingEntity who, String moment, @Nullable Component what) {
		if (!(who.level() instanceof ServerLevel level)) {
			return;
		}
		String tone = SharedHistory.toneNoGreed(who).key();
		Component name = Recollections.nameTo(player, who);
		Component line = Component.translatable("rescue.jaymoskingdoms." + moment + "." + tone + "." + who.getRandom().nextInt(LINES), name,
			what == null ? Component.empty() : what);
		Speech.tell(level, who, player, line, Speech.WHITE);
	}
}
