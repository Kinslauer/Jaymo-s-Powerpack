package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Ship;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.395) Stare Down by who you stare at. Andy (2026-10-30): "Will staredown has context sensitive interactions with
 * npcs and enemies, like making a captain jump ship, or a weak poor king submit the seat". {@link Will#stare} still
 * finds the gaze and holds it; what breaks, and how, is here.
 *
 * <p><b>The great</b> (a ship's captain at her wheel, a ruler on the throne) are stared at even when they're no foe of
 * yours, and the gaze is held longer ({@link #ticks}). They don't break because they're weaker: they weigh your Will
 * ({@link #will}: your Will pages, your world tier, Second Breath) against their nerve ({@link #nerve}: their crew or
 * guards about them, their realm's riches, their temperament, their wounds). Yours must be the greater.
 * <ul>
 * <li><b>A captain jumps ship:</b> he goes over the side and swims for it, quit of her for good (an MCA crewman is
 * discharged, a villager of wherever he washes up). She has no hand at her helm, and half the weaker of her crew go
 * over after him. A realm's ship (navy, merchant, whaler) thinks the worse of you.</li>
 * <li><b>A weak, poor ruler yields the seat:</b> stared down in their own hall (by the throne) with their realm poor,
 * they give you the crown, as a usurper would take it but with nobody slain; they stay on as your steward. The realm's
 * allies think the worse of you, half as much as of a usurper. A ruler already a player's steward, or a player who wears
 * a crown, can't: a weak ruler of a realm that isn't poor pays you to go instead, from the treasury, once a day.</li>
 * </ul>
 * If their nerve holds, they say so and can't be stared at again for a while ({@link #REFUSED_TICKS}).
 *
 * <p><b>Foes</b> (weaker, as ever) break by what they are: one of a ship's crew goes over the side, a rider is thrown and
 * the mount bolts, a caster's spell dies in their throat (hushed, {@link Will#hush}), and a bandit or anyone with a
 * bounty on them throws down their arms and kneels to be taken in (knocked out, with Jaymo's Powers). Anyone else drops
 * their weapon or runs, as before.
 */
final class Staredowns {
	/** How long the great must be stared at (ticks): a captain, a ruler. */
	static final int CAPTAIN_STARE_TICKS = 80, RULER_STARE_TICKS = 120;
	/** How long one whose nerve held can't be stared at again (ticks). */
	static final int REFUSED_TICKS = 6000;
	/** Your Will: one for each rank of these pages, this much for each world tier past Hearth, and Second Breath's. */
	static final int WILL_PER_TIER = 1, WILL_SURGE = 2, WILL_ALIGNED = 1;
	/** A captain's nerve: his own, one more for every this many able hands aboard, less on a sinking ship or under half health. */
	static final int CAPTAIN_NERVE = 2, HANDS_PER_NERVE = 3, SINKING_NERVE = -3, HURT_NERVE = -1;
	/** A ruler's nerve: their own, one for each guard this near them, this much for each step of their realm's wealth, less for a lord. */
	static final int RULER_NERVE = 3, WEALTH_NERVE = 3, LORD_NERVE = -1;
	static final double GUARD_REACH = 16.0;
	/** Temperament's sway on nerve. */
	static final int BOLD_NERVE = 2, DUTIFUL_NERVE = 1, TIMID_NERVE = -2, GLOOMY_NERVE = -1;
	/** A ruler yields only in their own hall: this near the throne, as a usurper must be. */
	static final double HALL_RANGE = Crowns.USURP_RANGE;
	/** What a yielded crown costs you with the realm's allies; a captain stared off a realm's ship, with the realm. */
	static final int REP_YIELD_ALLIES = Crowns.REP_USURP_ALLIES / 2, REP_CAPTAIN = -60;
	/** Tribute: this share of the treasury, at most this much, never leaving it under this; once a day a realm; what it costs you there. */
	static final float TRIBUTE_SHARE = 0.25F;
	static final int TRIBUTE_MOST = 64, REP_TRIBUTE = -80;
	static final long TRIBUTE_KEPT = 10L, TRIBUTE_TICKS = 24000L;
	/** The odds each weaker hand aboard goes over the side after their captain. */
	static final float CREW_FOLLOW_CHANCE = 0.5F;
	/** How hard one going over the side is thrown out (blocks/tick), and up. */
	static final double OVERBOARD_PUSH = 0.6, OVERBOARD_LIFT = 0.45;
	/** How long a knelt outlaw stays down (ticks), and how long a thrown rider's mount bolts. */
	static final int KNEEL_TICKS = 200, BOLT_TICKS = 100;
	/** How far their words carry (blocks). */
	static final double SPEECH_RANGE = 24.0;
	/** Lines each has (lang keys 0..n-1). */
	static final int LINES = 3;

	/** The great whose nerve held: against whom, until when. */
	private static final Map<Mob, Map<UUID, Long>> REFUSED = new WeakHashMap<>();
	/** Realm → when it may next be made to pay. */
	private static final Map<Integer, Long> TRIBUTE_READY = new HashMap<>();

	private Staredowns() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REFUSED.clear();
			TRIBUTE_READY.clear();
		});
	}

	// ---- who ----------------------------------------------------------------------------------------------------------

	/** {@code mob} is one of the great {@code sp} may stare down, foe or not: a captain at her wheel, a ruler on a throne. */
	static boolean great(ServerPlayer sp, Mob mob) {
		if (refused(sp, mob)) {
			return false;
		}
		if (Captains.starable(mob)) {
			// (0.7.432) a downed captain: to spare or to shame
			return true;
		}
		return captainOf(mob) != null || ruled(sp, mob) != null;
	}

	/** How long {@code sp} must hold their gaze on {@code mob} (before Second Breath). */
	static int ticks(ServerPlayer sp, Mob mob) {
		if (captainOf(mob) != null) {
			return CAPTAIN_STARE_TICKS;
		}
		return ruled(sp, mob) != null ? RULER_STARE_TICKS : Will.STARE_TICKS;
	}

	/** The ship {@code mob} captains (crewed, him at her wheel), or null. */
	private static @Nullable Ship captainOf(Mob mob) {
		return mob.getVehicle() instanceof Ship ship && ship.crew != null && mob.getUUID().equals(ship.crew.captain) ? ship : null;
	}

	/** The realm {@code mob} rules (a kingdom or a fiefdom, standing), not one of {@code sp}'s own crown, or null. */
	private static @Nullable Faction ruled(ServerPlayer sp, Mob mob) {
		if (!Rulers.is(mob)) {
			return null;
		}
		MinecraftServer server = sp.level().getServer();
		Faction f = Factions.get(server, Rulers.faction(mob));
		return f == null || !f.type().isRealm() || f.fallen() || Crowns.rules(server, f, sp.getUUID()) ? null : f;
	}

	private static boolean refused(ServerPlayer sp, Mob mob) {
		Map<UUID, Long> by = REFUSED.get(mob);
		Long until = by == null ? null : by.get(sp.getUUID());
		return until != null && sp.level().getGameTime() < until;
	}

	// ---- Will against nerve -------------------------------------------------------------------------------------------

	/** {@code sp}'s Will: their Will pages' ranks, their world tier, a Warrior's name, Second Breath, trophies and a crown worn. */
	static int will(ServerPlayer sp) {
		int will = Will.rank(sp, Skills.Perk.DREAD_PRESENCE) + Will.rank(sp, Skills.Perk.STARE_DOWN) + Will.rank(sp, Skills.Perk.BROKEN_MORALE)
			+ Will.rank(sp, Skills.Perk.NAME_THAT_CARRIES) + Will.rank(sp, Skills.Perk.LAST_LOOK);
		will += WorldTiers.of(sp).ordinal() * WILL_PER_TIER;
		if (Renown.alignment(Skills.of(sp)) == Skills.Pillar.STRENGTH) {
			will += WILL_ALIGNED;
		}
		// (0.7.435) trophies and a crown worn
		will += Spoils.will(sp);
		return Will.surging(sp) ? will + WILL_SURGE : will;
	}

	/** How {@code e}'s temperament sways their nerve. */
	private static int temper(LivingEntity e) {
		Temperaments.Temperament t = Temperaments.of(e);
		if (t == null) {
			return 0;
		}
		return switch (t) {
			case BRAVE, PROUD, HOTHEADED -> BOLD_NERVE;
			case DUTIFUL, DEVOUT -> DUTIFUL_NERVE;
			case TIMID -> TIMID_NERVE;
			case GLOOMY -> GLOOMY_NERVE;
			default -> 0;
		};
	}

	private static int hurt(LivingEntity e) {
		return e.getHealth() < e.getMaxHealth() * 0.5F ? HURT_NERVE : 0;
	}

	/** A captain's nerve: his own, his able hands aboard, his ship afloat or sinking, his wounds and temper. */
	static int nerve(Mob captain, Ship ship) {
		int hands = 0;
		for (LivingEntity hand : ship.crewAboard()) {
			if (hand != captain && !Downed.isDowned(hand)) {
				hands++;
			}
		}
		return CAPTAIN_NERVE + hands / HANDS_PER_NERVE + (ship.foundering() ? SINKING_NERVE : 0) + hurt(captain) + temper(captain);
	}

	/** A ruler's nerve: their own, their guards about them, their realm's riches, their wounds and temper. */
	static int nerve(ServerLevel level, Mob ruler, Faction f) {
		int guards = Rulers.guards(level, f, ruler.getBoundingBox().inflate(GUARD_REACH));
		int wealth = Wealth.of(level.getServer(), f).ordinal() * WEALTH_NERVE;
		return RULER_NERVE + guards + wealth + (Rulers.role(ruler) == Folk.LORD ? LORD_NERVE : 0) + hurt(ruler) + temper(ruler);
	}

	// ---- what breaks --------------------------------------------------------------------------------------------------

	/**
	 * {@code sp} has held their gaze on {@code seen} long enough: true if who they are decided what happened (the great
	 * always; a foe aboard, in the saddle, casting or outlawed), false for a plain foe to drop its weapon and run.
	 */
	static boolean broke(ServerLevel level, ServerPlayer sp, Mob seen, long now) {
		if (Captains.starable(seen)) {
			// (0.7.432) a downed captain, spared (an empty hand) or shamed
			Captains.stared(level, sp, seen, now);
			return true;
		}
		Ship ship = captainOf(seen);
		if (ship != null) {
			captain(level, sp, seen, ship, now);
			return true;
		}
		Faction f = ruled(sp, seen);
		if (f != null) {
			ruler(level, sp, seen, f, now);
			return true;
		}
		if (seen.getVehicle() instanceof Ship aboard && aboard.crew != null) {
			overboard(level, seen, aboard);
			Will.flee(seen, sp, now, Will.FLEE_TICKS);
			say(level, seen, "crewman");
			tell(sp, "overboard", seen);
			return true;
		}
		if (Will.caster(seen)) {
			// (the spell dies; they drop their weapon or run as anyone does)
			Will.hush(level, seen);
			level.sendParticles(ParticleTypes.SMOKE, seen.getX(), seen.getY(0.9), seen.getZ(), 8, 0.2, 0.1, 0.2, 0.01);
			Will.cowed(level, sp, seen, now);
			tell(sp, "hushed", seen);
			return true;
		}
		if (seen.getVehicle() instanceof Mob mount) {
			seen.stopRiding();
			seen.setDeltaMovement(seen.getDeltaMovement().add(0.0, 0.3, 0.0));
			Will.flee(mount, sp, now, BOLT_TICKS);
			level.playSound(null, mount.getX(), mount.getY(), mount.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 1.0F);
			Will.cowed(level, sp, seen, now);
			tell(sp, "thrown", seen);
			return true;
		}
		if ((Bandits.wears(seen) || Bounties.wanted(level.getServer(), seen)) && PowersCompat.knockOut(level, seen, KNEEL_TICKS)) {
			seen.setTarget(null);
			say(level, seen, "outlaw");
			tell(sp, "kneels", seen);
			return true;
		}
		return false;
	}

	/** {@code sp} stares down {@code captain} at {@code ship}'s wheel. */
	private static void captain(ServerLevel level, ServerPlayer sp, Mob captain, Ship ship, long now) {
		Crew crew = ship.crew;
		if (crew == null) {
			return;
		}
		if (!Will.weaker(sp, captain) || will(sp) <= nerve(captain, ship)) {
			holds(level, sp, captain, "captain");
			return;
		}
		crew.captain = null;
		overboard(level, captain, ship);
		Will.flee(captain, sp, now, Will.FLEE_TICKS * 2);
		say(level, captain, "captain");
		RandomSource random = level.getRandom();
		for (LivingEntity hand : List.copyOf(ship.crewAboard())) {
			if (hand instanceof Mob m && !Downed.isDowned(m) && Will.weaker(sp, m) && random.nextFloat() < CREW_FOLLOW_CHANCE) {
				overboard(level, m, ship);
				Will.flee(m, sp, now, Will.FLEE_TICKS);
			} else if (hand instanceof Mob m) {
				m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Will.MORALE_TICKS, 0), sp);
			}
		}
		if (crew.side != Crew.Side.PIRATE) {
			Faction f = Factions.get(level.getServer(), crew.faction);
			if (f != null) {
				Reputation.change(sp, f, REP_CAPTAIN);
			}
		}
		sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stare.captain", captain.getDisplayName(), crew.name).withStyle(ChatFormatting.GOLD));
	}

	/** {@code sp} stares down {@code ruler}, who rules {@code f}. */
	private static void ruler(ServerLevel level, ServerPlayer sp, Mob ruler, Faction f, long now) {
		MinecraftServer server = level.getServer();
		if (!Will.weaker(sp, ruler) || will(sp) <= nerve(level, ruler, f)) {
			holds(level, sp, ruler, "ruler");
			return;
		}
		boolean poor = Wealth.of(server, f) == Wealth.Tier.POOR;
		boolean hall = level == Factions.level(server) && ruler.blockPosition().closerThan(Crowns.throne(server, f), HALL_RANGE);
		if (poor && hall && Crowns.sovereign(server, f) == null && Crowns.crownOf(server, sp.getUUID()) == null) {
			yields(level, sp, ruler, f);
			return;
		}
		// a crown not theirs to give, or a realm with riches to spare: they pay you to go
		long ready = TRIBUTE_READY.getOrDefault(f.id(), 0L);
		long pay = Math.min(TRIBUTE_MOST, (long) (f.treasury() * TRIBUTE_SHARE));
		pay = Math.min(pay, f.treasury() - TRIBUTE_KEPT);
		if (ready > now || pay <= 0) {
			holds(level, sp, ruler, "ruler");
			return;
		}
		long paid = pay;
		TRIBUTE_READY.put(f.id(), now + TRIBUTE_TICKS);
		Factions.update(server, f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - paid)));
		ItemStack emeralds = new ItemStack(Items.EMERALD, (int) paid);
		if (!sp.getInventory().add(emeralds) && !emeralds.isEmpty()) {
			sp.spawnAtLocation(level, emeralds);
		}
		Reputation.change(sp, f, REP_TRIBUTE);
		say(level, ruler, "tribute");
		sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stare.tribute", ruler.getDisplayName(), paid, f.name()).withStyle(ChatFormatting.GOLD));
	}

	/** {@code ruler} gives {@code sp} {@code f}'s crown, and stays on as their steward. */
	private static void yields(ServerLevel level, ServerPlayer sp, Mob ruler, Faction f) {
		MinecraftServer server = level.getServer();
		say(level, ruler, "yields");
		Crowns.crown(server, f, sp.getUUID());
		for (Faction other : Factions.all(server)) {
			if (other.id() != f.id() && other.type().isRealm() && !other.fallen() && Relations.between(other, f) == Relation.ALLIED) {
				Reputation.change(sp, other, REP_YIELD_ALLIES);
			}
		}
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, ruler.getX(), ruler.getY(1.0) + 0.3, ruler.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.stare.yielded", sp.getDisplayName(), f.name())
			.withStyle(ChatFormatting.GOLD));
		sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crown.yours", f.name()).withStyle(ChatFormatting.GOLD));
	}

	/** {@code who}'s nerve held against {@code sp}: they say so, and won't be stared at again for a while. */
	private static void holds(ServerLevel level, ServerPlayer sp, Mob who, String kind) {
		REFUSED.computeIfAbsent(who, m -> new HashMap<>()).put(sp.getUUID(), level.getGameTime() + REFUSED_TICKS);
		say(level, who, kind + "_holds");
		tell(sp, "holds", who);
	}

	/** {@code mob} goes over {@code ship}'s side: off her for good (an MCA crewman discharged), thrown out over the water. */
	private static void overboard(ServerLevel level, Mob mob, Ship ship) {
		Crew crew = ship.crew;
		if (crew != null) {
			crew.walks.remove(mob.getUUID());
			crew.gunners.values().removeIf(mob.getUUID()::equals);
			if (mob.getUUID().equals(crew.captain)) {
				crew.captain = null;
			}
		}
		mob.stopRiding();
		mob.removeAttached(Ship.SEAT);
		if (Crewmen.tagged(mob)) {
			mob.removeTag(Crewmen.TAG);
			if (crew != null) {
				mob.removeTag(Crewmen.sideTag(crew.side));
			}
			if (mob instanceof Enlisted enlisted) {
				enlisted.discharge();
			}
		}
		Vec3 out = mob.position().subtract(ship.position()).multiply(1.0, 0.0, 1.0);
		out = out.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, ship.getYRot() + 90.0F) : out.normalize();
		mob.setDeltaMovement(out.x * OVERBOARD_PUSH, OVERBOARD_LIFT, out.z * OVERBOARD_PUSH);
		mob.syncVelocity = true;
		if (mob instanceof PathfinderMob pm) {
			pm.getNavigation().stop();
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.NEUTRAL, 0.8F, 1.0F);
		if (mob instanceof Worker w) {
			Standing.update(level, w);
		}
	}

	private static void say(ServerLevel level, Entity speaker, String kind) {
		Speech.say(level, speaker, Component.translatable("speech.jaymoskingdoms.stare." + kind + "." + level.getRandom().nextInt(LINES)),
			Speech.YELLOW, SPEECH_RANGE);
	}

	private static void tell(ServerPlayer sp, String kind, Entity who) {
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.stare." + kind, who.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
	}
}
