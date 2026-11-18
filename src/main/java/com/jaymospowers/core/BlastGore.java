package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.NoOneShot;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.67) Andy: "explosions (dependent on proximity) have a higher chance to gib mobs and players on death and
 * dismember survivors." How close a body was to a blast ({@link #closeness}: 1 at its centre, 0 at the edge of its reach,
 * weighed by how big the blast is, a TNT's or more counting in full) sets two chances:
 * <ul>
 * <li><b>Gibbed</b>, killed by it ({@link #gibChance}): the body is blown apart. A person's head and every limb fly off
 * ({@link Dismember#blastOff}; a skeleton's shatter), and Jaymo's Kitchen bursts what's left of a mob into chunks (it
 * reads {@link #GIB_TAG}; {@link #NO_GIB_TAG} keeps the body whole). A player leaves only their torso as a corpse, and
 * comes back with just the parts they had lost before it (a blast that tears you apart isn't a lasting wound).</li>
 * <li><b>Dismembered</b>, surviving it, standing or downed ({@link #limbChance}): an arm or a leg comes off, two now and
 * then. Never the head: that's a killing.</li>
 * </ul>
 * The rules stay: a person a blast would kill goes down instead unless it gibs them (as a beheading or a halving ends
 * them, {@code Downed}); Rubber-folk it gibs lose every limb but keep their head and go down, to mend with a swallow in
 * time as when cut in half ({@link Dismember#split}); beheading still kills them. Their limbs come off easier, as on a
 * blade ({@link Dismember#RUBBER_SEVER_SCALE}), and Will's Grim Hand counts for whoever is behind the blast.
 * <p>A WeaponMod blunderbuss's shot ({@link #blunderShot}) at close range tears like a blast, its closeness by how near the
 * shooter stood ({@link #SHOT_REACH}), all its pellets counting as one hit ({@link #SHOT_TICKS}). Most often it takes a
 * limb, from the living or the dead ({@link #SHOT_LIMB_SCALE}); it blows a body apart only when that one shot killed it
 * from full health ({@link #ONE_SHOT_HEALTH}).
 */
public final class BlastGore {
	/** A blast this big (radius; TNT's is 4) counts in full; a smaller one's closeness is scaled by the root of its share. */
	public static final float FULL_RADIUS = 4.0F;
	/** Gibbed on death: none at or below this closeness, rising evenly to {@link #GIB_MAX} at the centre. */
	public static final float GIB_FROM = 0.25F, GIB_MAX = 0.95F;
	/** A survivor loses a limb: none at or below this closeness, rising evenly to {@link #LIMB_MAX} at the centre. */
	public static final float LIMB_FROM = 0.3F, LIMB_MAX = 0.5F;
	/** After one limb, a second goes at this share of the chance. */
	public static final float SECOND_LIMB = 0.4F;
	/** A body killed but not gibbed keeps this share of a survivor's chance to lose a limb onto the corpse. */
	public static final float CORPSE_LIMB = 0.6F;
	/** A blunderbuss shot's closeness: 1 with the shooter on top of the body, 0 this far away (blocks) or more. */
	public static final double SHOT_REACH = 6.0;
	/** A blunderbuss shot's pellets landing on a body within this many ticks of the first are the one shot. */
	public static final int SHOT_TICKS = 4;
	/** A shot takes a limb this much more readily than a blast (at most certain); the corpse's lesser share doesn't apply. */
	public static final float SHOT_LIMB_SCALE = 2.0F;
	/** A shot gibs only a body it killed from at least this share of its health (from full, give or take). */
	public static final float ONE_SHOT_HEALTH = 0.99F;
	/** WeaponMod's blunderbuss shot. */
	private static final Identifier BLUNDER_SHOT = Identifier.fromNamespaceAndPath("weaponmod", "shot");
	/** With no blast to measure from (another mod's blast damage), this much damage counts as point-blank. */
	public static final float FALLBACK_FULL_DAMAGE = 40.0F;
	/** Kingdoms' Stoneborn heritage tag: Andy, "noone but stoneborn are exempt" (from being blown apart; they still lose limbs). */
	public static final String STONE_TAG = "jaymoskingdoms_heritage_stoneborn";
	/** The scoreboard tags Jaymo's Kitchen reads on a body dying of a blast: blown apart, or left whole. */
	public static final String GIB_TAG = "jaymoskitchen.gib", NO_GIB_TAG = "jaymoskitchen.no_gib";

	private static final Identifier PHASE = JaymosPowers.id("blast_gib");
	private static final int[] LIMBS = {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG};

	/** A vanilla blast going off ({@link #begin}/{@link #end}, by BlastGoreMixin): its centre and radius, innermost last. */
	private record Blast(Vec3 centre, float radius) {
	}

	private static final Deque<Blast> GOING_OFF = new ArrayDeque<>();
	/** A body's gib already rolled for the blow that's killing it (game time, and whether it's gibbed). */
	private static final Map<LivingEntity, long[]> ROLLED = new WeakHashMap<>();
	/** A body under a blunderbuss shot: the tick its first pellet landed, its health before it, and whether a limb was rolled. */
	private static final Map<LivingEntity, long[]> SHOTS = new WeakHashMap<>();
	/** A player blown apart → the parts they'd lost before it (all they come back without). */
	private static final Map<UUID, Integer> BEFORE = new HashMap<>();

	private BlastGore() {
	}

	public static void init() {
		// after Downed's last word: the death goes through, so the body may be blown apart (Downed rolls its own first)
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Event.DEFAULT_PHASE, PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(com.jaymospowers.core.wounds.Downed.lastPhase(), PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(PHASE, (entity, source, amount) -> {
			if ((blast(source) || blunderShot(source)) && entity.level() instanceof ServerLevel) {
				boolean gib = gibs(entity, source, amount);
				entity.removeTag(gib ? NO_GIB_TAG : GIB_TAG);
				entity.addTag(gib ? GIB_TAG : NO_GIB_TAG);
			} else {
				// (Kitchen takes a gib tag on any death as a shot's, so none is left over from a death that didn't happen)
				entity.removeTag(GIB_TAG);
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if ((blast(source) || blunderShot(source)) && entity.level() instanceof ServerLevel) {
				died(entity, source);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (blunderShot(source) && entity.level() instanceof ServerLevel) {
				shot(entity);
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && (blast(source) || blunderShot(source)) && entity.isAlive() && entity.level() instanceof ServerLevel) {
				survived(entity, source, taken);
			}
		});
		// (a blast that threw mid-way leaves nothing behind for the next)
		ServerTickEvents.END_SERVER_TICK.register(server -> GOING_OFF.clear());
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			newPlayer.removeTag(GIB_TAG);
			newPlayer.removeTag(NO_GIB_TAG);
			Integer before = BEFORE.remove(newPlayer.getUUID());
			if (before != null && !alive) {
				// (Dismember has just carried the stumps over: those the blast made grow back)
				Dismember.restore(newPlayer, before & ~Dismember.HEAD);
			}
		});
	}

	// ---- the blast going off ---------------------------------------------------------------------------------------------

	/** A vanilla explosion starts hurting things (BlastGoreMixin). */
	public static void begin(ServerExplosion explosion) {
		GOING_OFF.push(new Blast(explosion.center(), explosion.radius()));
	}

	/** ...and is done. */
	public static void end() {
		GOING_OFF.poll();
	}

	public static boolean blast(DamageSource source) {
		return source.is(DamageTypeTags.IS_EXPLOSION);
	}

	/** A WeaponMod blunderbuss's shot, fired by someone (the closeness is measured from them). */
	public static boolean blunderShot(DamageSource source) {
		return source.getDirectEntity() != null && source.getEntity() != null
			&& BLUNDER_SHOT.equals(BuiltInRegistries.ENTITY_TYPE.getKey(source.getDirectEntity().getType()));
	}

	/** The blunderbuss shot {@code victim} is under: begun by this pellet if none landed in the last {@link #SHOT_TICKS}. */
	private static long[] shot(LivingEntity victim) {
		long now = victim.level().getGameTime();
		long[] shot = SHOTS.get(victim);
		if (shot == null || now - shot[0] > SHOT_TICKS) {
			shot = new long[] {now, Float.floatToIntBits(victim.getHealth()), 0};
			SHOTS.put(victim, shot);
		}
		return shot;
	}

	/** Whether the shot {@code victim} is under found them at full health (so it killed them with the one shot). */
	private static boolean fromFull(LivingEntity victim) {
		long[] shot = shot(victim);
		return Float.intBitsToFloat((int) shot[1]) >= victim.getMaxHealth() * ONE_SHOT_HEALTH;
	}

	/** Whether this shot may still take a limb from {@code victim} (once per shot), and it now has. */
	private static boolean limbRoll(LivingEntity victim) {
		long[] shot = shot(victim);
		boolean first = shot[2] == 0;
		shot[2] = 1;
		return first;
	}

	/**
	 * How close {@code victim} was to the blast hurting it, 0 to 1: one less its distance over the blast's reach (twice its
	 * radius, as vanilla measures it), times the root of the blast's size over {@link #FULL_RADIUS} (at most 1). With no
	 * vanilla blast going off, the damage stands in for it ({@link #FALLBACK_FULL_DAMAGE}).
	 */
	public static float closeness(LivingEntity victim, DamageSource source, float amount) {
		if (blunderShot(source)) {
			return (float) Mth.clamp(1.0 - Math.sqrt(victim.distanceToSqr(source.getEntity())) / SHOT_REACH, 0.0, 1.0);
		}
		Blast blast = GOING_OFF.peek();
		if (blast == null) {
			return Mth.clamp(amount / FALLBACK_FULL_DAMAGE, 0.0F, 1.0F);
		}
		double reach = Math.max(0.01, blast.radius() * 2.0);
		float near = (float) Mth.clamp(1.0 - Math.sqrt(victim.distanceToSqr(blast.centre())) / reach, 0.0, 1.0);
		return near * (float) Math.sqrt(Math.min(1.0F, blast.radius() / FULL_RADIUS));
	}

	/** The blast's centre, if a vanilla one is going off (the way parts fly from). */
	private static @Nullable Vec3 centre(DamageSource source) {
		Blast blast = GOING_OFF.peek();
		return blast != null ? blast.centre() : source.getSourcePosition();
	}

	/** The chance a body killed this close ({@link #closeness}) is blown apart. */
	public static float gibChance(float closeness) {
		return GIB_MAX * Mth.clamp((closeness - GIB_FROM) / (1.0F - GIB_FROM), 0.0F, 1.0F);
	}

	/** The chance a survivor this close loses a limb (before Rubber-folk and Grim Hand). */
	public static float limbChance(float closeness) {
		return LIMB_MAX * Mth.clamp((closeness - LIMB_FROM) / (1.0F - LIMB_FROM), 0.0F, 1.0F);
	}

	// ---- dying of it -----------------------------------------------------------------------------------------------------

	/**
	 * Whether the blast killing {@code entity} blows it apart: rolled once per killing blow (Downed asks first, to let the
	 * gibbed die rather than go down; then the death itself). Creative and spectating players never are.
	 */
	public static boolean gibs(LivingEntity entity, DamageSource source, float amount) {
		// (a player at full health goes down instead of dying to the one blow: NoOneShot)
		if (NoOneShot.spared(entity, source)) {
			return false;
		}
		if (!blast(source) && !blunderShot(source) || entity instanceof Player p && (p.isCreative() || p.isSpectator()) || stone(entity)) {
			return false;
		}
		long now = entity.level().getGameTime();
		long[] rolled = ROLLED.get(entity);
		if (rolled != null && rolled[0] == now) {
			return rolled[1] != 0;
		}
		float closeness = closeness(entity, source, amount);
		boolean gib = (!blunderShot(source) || fromFull(entity)) && entity.getRandom().nextFloat() < gibChance(closeness);
		ROLLED.put(entity, new long[] {now, gib ? 1 : 0, Float.floatToIntBits(closeness)});
		return gib;
	}

	/**
	 * (Downed, on a blast that gibs a Rubber-folk) every limb comes off, the head stays: they go down to mend, as when cut in
	 * half. True if anything came off (else they're whole of limb already, and a gib leaves nothing to save).
	 */
	public static boolean tearLimbs(LivingEntity entity, DamageSource source) {
		LivingEntity by = source.getEntity() instanceof LivingEntity living ? living : null;
		Vec3 from = centre(source);
		boolean any = false;
		for (int limb : LIMBS) {
			any |= Dismember.blastOff(entity, limb, by, from, true);
		}
		return any;
	}

	/** Whether {@code entity} is Stoneborn (never blown apart). */
	public static boolean stone(LivingEntity entity) {
		return entity.entityTags().contains(STONE_TAG);
	}

	/** A body just died of a blast: blown apart if it was rolled to be, else maybe a limb off onto the corpse. */
	private static void died(LivingEntity entity, DamageSource source) {
		long[] rolled = ROLLED.remove(entity);
		boolean gib = rolled != null && rolled[1] != 0;
		float closeness = rolled != null ? Float.intBitsToFloat((int) rolled[2]) : closeness(entity, source, 0.0F);
		if (!Dismember.canLoseParts(entity) || entity instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		LivingEntity by = source.getEntity() instanceof LivingEntity living ? living : null;
		Vec3 from = centre(source);
		if (gib) {
			if (entity instanceof ServerPlayer player) {
				BEFORE.put(player.getUUID(), Dismember.missing(player));
			}
			for (int part : Dismember.PARTS) {
				Dismember.blastOff(entity, part, by, from, true);
			}
			return;
		}
		boolean shot = blunderShot(source);
		if (shot && !limbRoll(entity)) {
			return;
		}
		if (entity.getRandom().nextFloat() < (shot ? 1.0F : CORPSE_LIMB) * chance(entity, by, closeness, source)) {
			loseLimb(entity, by, from);
		}
	}

	// ---- living through it -----------------------------------------------------------------------------------------------

	/** A body that lived through a blast (standing, or just downed by it) may lose an arm or a leg, now and then two. */
	private static void survived(LivingEntity entity, DamageSource source, float taken) {
		boolean shot = blunderShot(source);
		// (a shot's pellets each hit light: the shot counts as one blow, rolled on its first pellet)
		if (!shot && taken < Dismember.MIN_DAMAGE || !Dismember.canLoseParts(entity) || entity instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		if (shot && !limbRoll(entity)) {
			return;
		}
		long[] rolled = ROLLED.get(entity);
		if (rolled != null && rolled[0] == entity.level().getGameTime() && rolled[1] != 0) {
			// a Rubber-folk the blast tore apart already (Downed): nothing more to take
			return;
		}
		LivingEntity by = source.getEntity() instanceof LivingEntity living ? living : null;
		Vec3 from = centre(source);
		float chance = chance(entity, by, closeness(entity, source, taken), source);
		if (entity.getRandom().nextFloat() >= chance) {
			return;
		}
		if (loseLimb(entity, by, from) && entity.isAlive() && entity.getRandom().nextFloat() < chance * SECOND_LIMB) {
			loseLimb(entity, by, from);
		}
	}

	/** A survivor's (or a corpse's) chance at this closeness, raised for a shot, for Rubber-folk and by Grim Hand, at most certain. */
	private static float chance(LivingEntity entity, @Nullable LivingEntity by, float closeness, DamageSource source) {
		float chance = limbChance(closeness);
		if (blunderShot(source)) {
			chance *= SHOT_LIMB_SCALE;
		}
		if (Dismember.rubber(entity)) {
			chance *= Dismember.RUBBER_SEVER_SCALE;
		}
		chance *= com.jaymospowers.core.wounds.WillSkill.severScale(by, entity);
		// small bodies lose limbs to a blast more often too
		chance *= Dismember.smallScale(entity);
		return Math.min(1.0F, chance);
	}

	/** One limb it still has, at random, comes off. True if one did. */
	private static boolean loseLimb(LivingEntity entity, @Nullable LivingEntity by, @Nullable Vec3 from) {
		int mask = Dismember.missing(entity);
		int left = 0;
		for (int limb : LIMBS) {
			if ((mask & limb) == 0) {
				left++;
			}
		}
		if (left == 0) {
			return false;
		}
		int pick = entity.getRandom().nextInt(left);
		for (int limb : LIMBS) {
			if ((mask & limb) == 0 && pick-- == 0) {
				return Dismember.blastOff(entity, limb, by, from, false);
			}
		}
		return false;
	}
}
