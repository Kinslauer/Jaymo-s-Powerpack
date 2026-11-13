package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Counterspell (fifth edition's reaction, Andy's pick H4): answer a foe's spell as it is cast, in a timing window, and snuff
 * it. A caster of the camps and the courts does not loose a spell at once: they raise their staff and a sigil of their
 * colour turns at their feet for {@link #WINDUP} ticks (the tell), and only then does it go. In that window:
 * <ul>
 * <li>a Mage who knows Counterspell (the staff, a scroll, or the Magic key) or a Sorcerer (their J, or the Magic key), in
 * sight of the caster, snuffs it ({@link #react}) at some cost in mana, and may fail;</li>
 * <li>a caster of the other side near enough does the same of their own accord: a court wizard snuffs a shaman's ring of
 * fire as it is cast, and a shaman, a necromancer or a blood witch snuffs the wizard's bolt ({@link #opposed}).</li>
 * </ul>
 * A snuffed caster is thrown off for a moment and cannot begin another at once. Counterspell wants only a gesture: it is
 * cast through a gag and in silence ({@link SpellParts}).
 */
public final class Counterspell {
	/** How long (ticks) a cast takes to land: the window to answer in. */
	static final int WINDUP = 18;
	/** A rival caster considers answering after this many ticks of the window, once. */
	static final int REACTS_AFTER = 5;
	/** How far (blocks) a rival caster sees a cast to answer it, and a player's counter reaches. */
	static final double RIVAL_RANGE = 24.0, PLAYER_RANGE = 36.0;
	/** The chance a rival caster answers, by who they are; a player's, before power (scroll 1, Mage's staff 1.75). */
	static final float WIZARD_ANSWERS = 0.6F, OTHER_ANSWERS = 0.4F;
	static final float PLAYER_CHANCE = 0.6F, PLAYER_PER_POWER = 0.25F, PLAYER_MOST = 0.92F;
	/** A rival caster answers no more often than this (ticks); a snuffed caster waits this long. */
	static final int RIVAL_GAP = 20 * 8, THROWN_OFF = 20 * 3;
	/** A player's counter waits this long (ticks) before the next. */
	static final int PLAYER_GAP = 30;
	/** The sigil colours of each kind of caster. */
	static final int NECROMANCER_COLOUR = 0x7A1FA2, SHAMAN_COLOUR = 0xFF8A2A, WIZARD_COLOUR = 0x66CCFF, WITCH_COLOUR = 0xA01020;

	/** A cast on its way: who, when it lands, and what happens then. */
	private static final class Open {
		final PathfinderMob mob;
		final long begun, due;
		final Runnable cast;
		final int colour;
		final Set<UUID> asked = new HashSet<>();
		boolean snuffed;

		Open(PathfinderMob mob, long begun, Runnable cast, int colour) {
			this.mob = mob;
			this.begun = begun;
			this.due = begun + WINDUP;
			this.cast = cast;
			this.colour = colour;
		}
	}

	private static final Map<UUID, Open> OPEN = new HashMap<>();
	/** Until when (game time) each rival caster rests from answering, and each snuffed caster from casting. */
	private static final Map<UUID, Long> RESTS = new HashMap<>(), THROWN = new HashMap<>();
	private static final Map<UUID, Long> PLAYER_READY = new HashMap<>();

	private Counterspell() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Counterspell::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			OPEN.clear();
			RESTS.clear();
			THROWN.clear();
			PLAYER_READY.clear();
		});
	}

	// ---- a caster's window ------------------------------------------------------------------------------------------------------

	/**
	 * {@code caster} begins {@code cast}: it will go in {@link #WINDUP} ticks unless snuffed. False if the caster is thrown
	 * off or mid-spell already (nothing is begun).
	 */
	static boolean wind(Sorcery.Caster caster, Runnable cast) {
		PathfinderMob mob = caster.mob();
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (OPEN.containsKey(mob.getUUID()) || THROWN.getOrDefault(mob.getUUID(), 0L) > now) {
			return false;
		}
		int colour = colourOf(caster.role());
		OPEN.put(mob.getUUID(), new Open(mob, now, cast, colour));
		mob.getNavigation().stop();
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0F, 1.2F);
		Fx.sigil(level, mob.position().add(0.0, 0.05, 0.0), 1.4F, colour, WINDUP + 4);
		return true;
	}

	static int colourOf(int role) {
		return switch (role) {
			case Sorcery.NECROMANCER -> NECROMANCER_COLOUR;
			case Sorcery.WIZARD -> WIZARD_COLOUR;
			case Sorcery.BLOOD_WITCH -> WITCH_COLOUR;
			default -> SHAMAN_COLOUR;
		};
	}

	/** Whether {@code mob} has a spell in its window just now (the tell is showing). */
	public static boolean casting(LivingEntity mob) {
		return OPEN.containsKey(mob.getUUID());
	}

	/** Whether two casters are on opposite sides: a camp's against a kingdom's, or one set on the other. */
	static boolean opposed(PathfinderMob a, PathfinderMob b) {
		if (a == b) {
			return false;
		}
		if (a.getTarget() == b || b.getTarget() == a) {
			return true;
		}
		return Casters.campCaster(a) != Casters.campCaster(b) && !(Casters.campCaster(a) && a instanceof Folk && b instanceof Folk fb && fb.sameSide((Folk) a));
	}

	private static void tick(MinecraftServer server) {
		if (OPEN.isEmpty()) {
			return;
		}
		for (Open open : List.copyOf(OPEN.values())) {
			PathfinderMob mob = open.mob;
			if (!mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				OPEN.remove(mob.getUUID());
				continue;
			}
			long now = level.getGameTime();
			if (!open.snuffed && now - open.begun >= REACTS_AFTER) {
				answerByRival(level, open, now);
			}
			if ((now - open.begun) % 6L == 0L && now < open.due && !open.snuffed) {
				level.sendParticles(Fx.rune(open.colour, 1.1F), mob.getX(), mob.getY() + 2.2, mob.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
				mob.getLookControl().setLookAt(mob.getTarget() != null ? mob.getTarget() : mob);
			}
			if (now < open.due) {
				continue;
			}
			OPEN.remove(mob.getUUID());
			if (open.snuffed) {
				continue;
			}
			if (!SpellParts.canSpeak(mob) || Will.hushed(mob)) {
				fizzle(level, mob, open.colour);
				continue;
			}
			open.cast.run();
		}
	}

	private static void answerByRival(ServerLevel level, Open open, long now) {
		PathfinderMob mob = open.mob;
		for (PathfinderMob rival : level.getEntitiesOfClass(PathfinderMob.class, mob.getBoundingBox().inflate(RIVAL_RANGE),
			m -> m instanceof Worker w && Sorcery.isCaster(w.role()) && m.isAlive() && opposed(mob, m))) {
			UUID id = rival.getUUID();
			if (open.asked.contains(id) || OPEN.containsKey(id) || RESTS.getOrDefault(id, 0L) > now || !rival.getSensing().hasLineOfSight(mob)
				|| !SpellParts.handFree(rival, null) || Will.hushed(rival)) {
				continue;
			}
			open.asked.add(id);
			float chance = ((Worker) rival).role() == Sorcery.WIZARD ? WIZARD_ANSWERS : OTHER_ANSWERS;
			if (rival.getRandom().nextFloat() < chance) {
				RESTS.put(id, now + RIVAL_GAP);
				snuff(level, open, rival);
				return;
			}
		}
	}

	/** {@code by} (a caster, or a player) snuffs the spell in {@code open}: a beam across, the sigil shattering. */
	private static void snuff(ServerLevel level, Open open, LivingEntity by) {
		open.snuffed = true;
		PathfinderMob mob = open.mob;
		THROWN.put(mob.getUUID(), level.getGameTime() + THROWN_OFF);
		Vec3 target = mob.getBoundingBox().getCenter();
		Fx.beam(level, by.getEyePosition().add(0.0, -0.3, 0.0), target, Sorcery.BEAM_RADIUS * 1.5F, 0xE4DCFF, Sorcery.BEAM_TICKS);
		Fx.blast(level, target, 1.8F, 0xE4DCFF);
		Fx.ring(level, mob.position(), 2.0F, open.colour);
		level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY(1.0), mob.getZ(), 14, 0.3, 0.4, 0.3, 0.03);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.4F, 0.7F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0F, 1.5F);
		Mana.ripple(by, Mana.WILL);
	}

	private static void fizzle(ServerLevel level, PathfinderMob mob, int colour) {
		level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY(1.0), mob.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 1.4F);
	}

	// ---- the player's answer ----------------------------------------------------------------------------------------------------

	/**
	 * {@code player} answers the cast nearest their crosshair that they can see: it is snuffed unless the roll goes against
	 * them. False if there is no cast to answer (nothing is spent).
	 */
	public static boolean react(ServerLevel level, Player player, float power) {
		long now = level.getGameTime();
		if (PLAYER_READY.getOrDefault(player.getUUID(), 0L) > now) {
			return false;
		}
		Open best = null;
		double bestDot = 0.5;
		Vec3 look = player.getLookAngle();
		for (Open open : OPEN.values()) {
			PathfinderMob mob = open.mob;
			if (open.snuffed || mob.level() != level || mob.distanceToSqr(player) > PLAYER_RANGE * PLAYER_RANGE || !player.hasLineOfSight(mob) || !foeOf(player, mob)) {
				continue;
			}
			double dot = mob.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize().dot(look);
			if (dot > bestDot) {
				best = open;
				bestDot = dot;
			}
		}
		if (best == null) {
			return false;
		}
		PLAYER_READY.put(player.getUUID(), now + PLAYER_GAP);
		float chance = Math.min(PLAYER_MOST, PLAYER_CHANCE + PLAYER_PER_POWER * (power - 1.0F));
		if (player.getRandom().nextFloat() < chance) {
			snuff(level, best, player);
		} else {
			// too slow, or the wrong gesture: a flicker, and the spell goes on
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(1.0), player.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);
		}
		return true;
	}

	/** Whether {@code mob}'s spell is a foe's to {@code player}: set on them or theirs, a camp's caster, or a monster. */
	private static boolean foeOf(Player player, PathfinderMob mob) {
		LivingEntity target = mob.getTarget();
		return target == player || target != null && player.isAlliedTo(target) || Casters.campCaster(mob) || mob instanceof Enemy;
	}

	/**
	 * The Magic key and a Sorcerer's J: answer the cast in sight if the player knows how and can pay. False if there is
	 * none to answer (the key then does something else).
	 */
	public static boolean key(ServerPlayer player, ServerLevel level) {
		if (!NewSpells.knows(player, "counterspell") || OPEN.isEmpty()) {
			return false;
		}
		if (!SpellParts.allow(player, "counterspell", null)) {
			return true;
		}
		// is there anything in the window to answer? (look before paying)
		boolean any = false;
		for (Open open : OPEN.values()) {
			if (!open.snuffed && open.mob.level() == level && open.mob.distanceToSqr(player) <= PLAYER_RANGE * PLAYER_RANGE && player.hasLineOfSight(open.mob) && foeOf(player, open.mob)) {
				any = true;
				break;
			}
		}
		if (!any) {
			return false;
		}
		if (!Mana.cast(player, "counterspell")) {
			return true;
		}
		float power = Classes.is(player, Classes.PlayerClass.MAGE) ? Spellcraft.MAGE_POWER : Spellcraft.ATTUNED_POWER;
		react(level, player, power);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Spellcraft.afterCast(player, level, "counterspell");
		return true;
	}

	/** An open cast of a caster near {@code mob}, if any (for the dispel of a rival's held spell, nothing is needed). */
	static @Nullable PathfinderMob caster(UUID id) {
		Open o = OPEN.get(id);
		return o == null ? null : o.mob;
	}
}
