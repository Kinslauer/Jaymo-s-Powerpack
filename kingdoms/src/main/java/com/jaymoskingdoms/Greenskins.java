package com.jaymoskingdoms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.292) Andy (2026-10-28), retiring the folk: goblins and orcs as MCA villagers too, "but they still need to be
 * monstrous with their proportions, Orcs are bigger, goblins smaller, both green and repulsive with variations".
 * <ul>
 * <li>Each is a brood of its own ({@link Heritage.Kind#GOBLIN}, {@link Heritage.Kind#ORC}): born only to its own, never a
 * player's, never woken. A goblin is a short, stringy thing ({@link BodySizes#GOBLIN} on top of {@link Goblins#SCALE}),
 * an orc drawn taller and broader than any man ({@link BodySizes#ORC}; MCA keeps its box a man's size, so it still
 * fits a door), and the king and warchief bigger yet by their own scale.</li>
 * <li>Their green is their own ({@link #skin}): a goblin's lime to bog green, an orc's grey-green, every one a shade of
 * its own, and each brood's two other looks ({@link HeritageVariants}: a goblin's Boggart and Cavegrub, an orc's Ashskin
 * and Bloodtusk) tint it their way. On it (client {@code GreenskinLooks}): a goblin's long ears and hooked nose or
 * bat ears and a needle nose, an orc's tusks and heavy brow or an underbite; warts, wrinkles and blotches, an orc's
 * scars and war paint, in four ways each.</li>
 * <li>A village's or camp's goblins, slingers, grunts and rangers at home, and its king or warchief on his throne, hand
 * over to MCA villagers in their place ({@link #mayConvert}, {@code McaBridge.convertGreenskin}): MCA guards (a bowman
 * an archer), with their gear and toughness, a goblin's or an orc's name, enlisted to the camp in the same role. They
 * fight through MCA's guard brain ({@code McaCompat.foe}) for the same prey; an orc's blood rage and war cries, and the
 * warchief's ground slam, run on the villager's body ({@link #step}); the king and warchief keep their boss bars and
 * their rally, and their deaths still clear the camp ({@link CampChiefs}). An orc camp's shaman is a villager in office
 * ({@link Casters}). War bands sent out raiding stay folk.</li>
 * </ul>
 */
public final class Greenskins {
	/** A goblin's green from palest to deepest, and an orc's; how far a shade leans yellow or blue. */
	static final int GOBLIN_PALE = 0x9CC25A, GOBLIN_DEEP = 0x587A30, ORC_PALE = 0x8C9A70, ORC_DEEP = 0x46553A;
	static final int LEAN = 18;
	private Greenskins() {
	}

	// ---- who they are -------------------------------------------------------------------------------------------------

	/** {@code role} is one an MCA villager takes here: a village's goblins, slingers and king, a camp's grunts, rangers and warchief (its shaman is {@link Casters}'). */
	public static boolean isRole(int role) {
		return Goblins.isGoblin(role) || role == Orcs.GRUNT || role == Orcs.RANGER || role == Orcs.WARCHIEF;
	}

	/** The brood a goblin's or an orc's role is born to (an orc shaman's too), or null. */
	static Heritage.@Nullable Kind brood(int role) {
		return Goblins.isGoblin(role) ? Heritage.Kind.GOBLIN : Orcs.isOrc(role) ? Heritage.Kind.ORC : null;
	}

	/** {@code e}'s brood (a goblin or an orc by blood), or null. */
	public static Heritage.@Nullable Kind of(Entity e) {
		Heritage.Kind kind = VillagerHeritage.of(e);
		return kind != null && kind.brood ? kind : null;
	}

	/** The camp's leader: a goblin king, an orc warchief ({@link CampChiefs}). */
	static boolean chief(int role) {
		return role == Goblins.KING || role == Orcs.WARCHIEF;
	}

	/** It shoots: a slinger, a ranger (an MCA archer). */
	static boolean ranged(int role) {
		return Goblins.ranged(role) || Orcs.ranged(role);
	}

	/** A goblin's or an orc's own name, the same every time for {@code seed}. */
	static String name(Heritage.Kind kind, long seed) {
		return kind == Heritage.Kind.ORC ? Orcs.commonName(seed) : Goblins.commonName(seed);
	}

	/** {@code folk} (one of a goblin village's or an orc camp's, at home and at peace) is ready to hand over to an MCA villager. */
	static boolean mayConvert(ServerLevel level, Folk folk) {
		if (!McaCompat.LOADED || !isRole(folk.role()) || !folk.task().isEmpty() || folk.getTarget() != null || folk.isPassenger()
			|| Downed.isDowned(folk) || Assault.attacker(folk) || Raids.isRaider(folk) || level.dimension() != Level.OVERWORLD) {
			return false;
		}
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, folk.factionId());
		return camp != null && !camp.fallen() && camp.type() == FactionType.BARBARIAN
			&& (Goblins.isGoblin(folk.role()) ? Camps.goblin(server, camp.id()) : Camps.orc(server, camp.id()));
	}

	/** What one of {@code camp}'s villager goblins or orcs ({@code role}) goes for: as its folk did. */
	static boolean prey(int role, int camp, LivingEntity target) {
		return Goblins.isGoblin(role) ? Goblins.prey(camp, target) : Orcs.prey(target);
	}

	// ---- their skin -----------------------------------------------------------------------------------------------------

	/** {@code e}'s skin (opaque ARGB): its own shade of its brood's green, tinted by its look; 0 for anyone not a goblin or an orc. */
	public static int skin(Entity e) {
		Heritage.Kind kind = of(e);
		if (kind == null) {
			return 0;
		}
		// a shade of its own, most a middling one
		long bits = e.getUUID().getMostSignificantBits() ^ e.getUUID().getLeastSignificantBits() * 0x9E3779B97F4A7C15L;
		float a = (bits & 0xFFFF) / 65535.0F, b = (bits >>> 16 & 0xFFFF) / 65535.0F;
		float shade = (a + b) / 2.0F, lean = (bits >>> 32 & 0xFF) / 255.0F - 0.5F;
		boolean orc = kind == Heritage.Kind.ORC;
		int pale = orc ? ORC_PALE : GOBLIN_PALE, deep = orc ? ORC_DEEP : GOBLIN_DEEP;
		int r = Mth.clamp(Math.round(Mth.lerp(shade, ARGB.red(pale), ARGB.red(deep)) + lean * LEAN), 0, 255);
		int g = Math.round(Mth.lerp(shade, ARGB.green(pale), ARGB.green(deep)));
		int bl = Mth.clamp(Math.round(Mth.lerp(shade, ARGB.blue(pale), ARGB.blue(deep)) - lean * LEAN), 0, 255);
		int green = ARGB.color(255, r, g, bl);
		HeritageVariants.Variant v = HeritageVariants.of(e);
		return v == null || v.tintAmount <= 0.0F ? green : HeritageVariants.blend(green, v.tint, v.tintAmount);
	}

	// ---- their day ----------------------------------------------------------------------------------------------------

	/** One of a camp's villager goblins or orcs ({@code Workers.step}, its brain runs after): an orc's rage, cries and slam. */
	static void step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (!mob.isSleeping() && !Downed.isDowned(mob)) {
			Orcs.tick(level, mob, w.role(), w.factionId());
		}
	}

	/** One of them died ({@code Workers.died}): a goblin now and then leaves its mask (a king's or warchief's death is {@link CampChiefs}'). */
	static void died(ServerLevel level, Worker w, DamageSource source) {
		PathfinderMob mob = w.mob();
		if (!(mob instanceof Folk) && Goblins.isGoblin(w.role()) && !chief(w.role())) {
			Goblins.died(level, mob, w.role(), source);
		}
	}
}
