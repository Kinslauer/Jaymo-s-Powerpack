package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.LockOnRegions;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Wounds, phase 1: every living thing's health is spread over the parts of its {@link BodyPlan}. Health stays real
 * (vanilla {@code getHealth()} is the truth); the {@link #PARTS} attachment is how it is distributed, as each part's
 * fraction (0-1) of its own share of the most health. A hit takes its loss from the part it lands on
 * ({@link HitRouting}), spilling into the next when a part runs out; head or body at zero kills. Healing of any kind
 * fills the most hurt part first. No debuffs yet.
 * <p>
 * (0.14.1) Phase 2: the death head or body brings is where {@link Downed} steps in (players, villagers, Kingdoms' folk
 * and tamed animals go down instead). While someone is down, Wounds leaves their parts to follow their health: only
 * their health running out ends them. Zombies and skeletons whose body gives out fight on ({@link #UNDEAD_FIGHT_ON});
 * then only the head kills them.
 * <p>
 * (0.14.2) Phase 3: a limb that a routed hit takes to zero is broken ({@link Breaks}: an arm or a leg; a foot sprained, a
 * wing grounded) and, until it is set, never heals past {@link Breaks#BROKEN_CAP} (a sprained foot heals freely); a
 * severed one never heals at all.
 * Health that the capped parts can't hold is refused (vanilla health is held to the parts' total). A hit with a blade on
 * an already broken limb, or one that takes the head past zero, for more than {@link Dismember#SEVER_OVERKILL} of that
 * part cuts it off ({@link Dismember#severOnOverkill}).
 * <p>
 * (0.15.94) Wounds first, hearts after. Andy (2026-10-22): "everything should go through the wounding system first ... if
 * a certain threshold (30%) damage to your limb goes through, then it should affect your hearts". Every hit lands on the
 * parts before vanilla takes any health ({@code WoundsHurtMixin}); the hearts are worked out from the parts
 * (until the survival picks below): the first 30% of a part's own pool is bruising and costs no hearts, past that
 * its hearts drain faster, so a part at zero has cost its whole share. Health still adds up to the parts, only through
 * that curve. Meals and food bring the deep wounds back to the bruise line; the bruises mend by themselves
 * (three minutes); and both run at {@link #UNPATCHED_REGEN} of their speed until every wound past the bruise
 * line is dressed or splinted and nothing bleeds, festers or sits broken unset ({@link #patchedUp}). Treatment is the
 * way back.
 * <p>
 * Survival picks (Andy, 2026-11-22, after Legendary Survival Overhaul's ideas; none of its code): every part has its own
 * pool on top of the health, apart from it ({@link BodyPlan}: a humanoid's head 8, body 12, arms 8, legs 12, feet 8 on 20
 * health). This replaces the bruise curve above: a hit takes its damage from the health as vanilla does and the same from
 * the part it lands on; healing the health no longer heals the parts. The parts mend by themselves while fed
 * ({@link #PART_MEND_TICKS}, a quarter as fast until patched up, {@link #PATCH_FROM} now being the share a part may be
 * missing before it wants a dressing), by medicine and by a night's sleep ({@link PartStrain}), and every point a part
 * heals costs {@link #FOOD_PER_POINT} food exhaustion. Downing (Andy: "the only time you arent downed is if a hit does
 * enough damage to your head while you are standing, even a blacked torso should down you first"): health at zero, or a
 * hit that blacks out the head or body, downs; only a located blow that blacks out the head of one not already down kills
 * outright ({@link #headKilled}), and even that downs one it found at full health ({@link NoOneShot}). Damage with no
 * place to land (fire, magic, poison, drowning, a blast) wears every pool by the share it takes off the health.
 */
public final class Wounds {
	/** Smaller than this many health points is nothing. */
	static final float EPS = 1.0E-4F;
	/** A death something cancelled ({@link Downed}, Xenoform reverts): head and body get at least this fraction back. */
	public static final float MIN_VITAL_AFTER_SAVE = 0.1F;
	/** Players have their parts brought in line with their health this often (ticks), so the HUD follows healing. */
	public static final int PLAYER_RECONCILE_TICKS = 10;
	/** A fraction this full counts as whole. */
	private static final float WHOLE = 0.999F;
	/** (0.14.1) A zombie's or skeleton's chance, when its body (not its head) gives out, to fight on: then only the head kills it. */
	public static final float UNDEAD_FIGHT_ON = 0.6F;
	/** (0.14.1) The scoreboard tag on an undead fighting on with its body gone. */
	public static final String FIGHTS_ON_TAG = "jaymospowers_fights_on";
	/** A part missing more than this share of its pool wants a dressing or a splint before you count as patched up ({@link #patchedUp}). */
	public static final float PATCH_FROM = 0.3F;
	/** (0.15.94) Natural healing (meals, food, the parts mending) runs at this share of its speed until you're patched up ({@link #patchedUp}). */
	public static final float UNPATCHED_REGEN = 0.25F;
	/** (Survival picks) Ticks a part takes to mend from nothing to whole by itself, patched up and fed. */
	public static final int PART_MEND_TICKS = 12000;
	/** (Survival picks) The parts mend by themselves only on at least this much food (hunger points). */
	public static final int MEND_FOOD_MIN = 7;
	/** (Survival picks, lso-food-cost) Food exhaustion each health point a part heals costs (4 is a hunger point). */
	public static final float FOOD_PER_POINT = 0.5F;
	/** (0.15.94) The scoreboard tag on a player not patched up yet (Jaymo's Kitchen reads it to slow its meals' healing). */
	public static final String UNPATCHED_TAG = "jaymospowers_unpatched";
	public static final ResourceKey<DamageType> WOUNDS_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("wounds"));

	/** Each part's fraction of its share of the most health, in the order of the creature's {@link BodyPlan}. Saved, synced to the creature itself; absent when every part is whole. Not kept through death. */
	public static final AttachmentType<List<Float>> PARTS = AttachmentRegistry.create(JaymosPowers.id("wounds"),
		builder -> builder.persistent(Codec.FLOAT.listOf())
			.syncWith(ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** The max health the stored fractions were taken against (server side only), so a raised max is not read as a loss. */
	public static final AttachmentType<Float> PARTS_MAX = AttachmentRegistry.create(JaymosPowers.id("wounds_max"),
		builder -> builder.persistent(Codec.FLOAT));

	/** Creatures whose wounds are killing them just now (or {@link Downed}'s finishing off): our own lethal blow doesn't route again. */
	static final Set<LivingEntity> FINISHING = Collections.newSetFromMap(new WeakHashMap<>());

	/** (0.14.2) A killing blow as its death was let through: the blow, its amount, the parts before it, and the game time. */
	private record Killing(DamageSource source, float amount, float[] points, long time) {
	}

	/** (0.14.2) Killing blows waiting for their death to go through (looked at in the after-death event, then dropped). */
	private static final Map<LivingEntity, Killing> KILLING = new WeakHashMap<>();
	/** (0.14.2) The killing blow is noted last of all, after {@link Downed}'s late phase: only deaths nothing saves. */
	private static final Identifier KILLING_PHASE = JaymosPowers.id("wounds_killing_blow");
	private static int tickCounter;

	/** (0.15.94) A hit already taken from the parts as it landed ({@link #takeHit}), waiting for the after-damage event. {@code full}: it found them at full health. */
	private record Routed(DamageSource source, @Nullable BodyPart landed, float[] was, float[] now, float loss, boolean aimed, boolean full) {
	}

	/** (Survival picks) Creatures being finished by a blow that blacked out their head while they stood: no downing ({@link Downed#spared}). */
	private static final Set<LivingEntity> HEAD_KILLS = Collections.newSetFromMap(new WeakHashMap<>());

	/** (0.15.94) Hits {@link #takeHit} routed, picked up by {@link #onDamage} in the same blow. */
	private static final Map<LivingEntity, Routed> ROUTED = new WeakHashMap<>();

	private Wounds() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Wounds::onDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter < PLAYER_RECONCILE_TICKS) {
				return;
			}
			tickCounter = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
					reconcile(player);
					// (0.15.94) bruises mend, and Kitchen's meals learn whether you're patched up
					boolean patched = patchedUp(player);
					if (patched) {
						player.removeTag(UNPATCHED_TAG);
					} else {
						player.addTag(UNPATCHED_TAG);
					}
					mendParts(player, patched);
					// (survival picks) the 20% debuffs, broken hearts and a night's sleep
					PartStrain.tick(player);
				} else {
					player.removeTag(UNPATCHED_TAG);
					if (player.isAlive() && player.isCreative()) {
						creativeHeal(player);
					}
				}
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WoundsCommand.register(dispatcher));
		// (0.14.1) phase 2: the downed
		Downed.register();
		Stubborn.register();
		LastHold.register();
		// (Clouds and Bits) a blow that would kill from full health downs instead, never gibs
		NoOneShot.register();
		// (0.17.183) Kingdoms' Will pages: No Getting Up, No Mercy, Grim Hand, hushes, Will Over Flesh
		WillSkill.register();
		// (0.14.2) phase 3: broken limbs
		Breaks.register();
		// (0.20.110) low and hit: broken limbs don't hold you back for a while
		Adrenaline.register();
		Crutches.register();
		// (0.20.111) passing out from hunger or thirst
		Fainting.register();
		// fifth edition: steps of exhaustion, and zombies that get back up
		Exhaustion.register();
		// held the big axes' spin too long: dizzy, then out cold
		SpinDizzy.register();
		UndeadFortitude.register();
		// (0.14.4) phase 4: cuts and festering, the medieval kit, pain and the poppy
		Bleeding.register();
		// bleeding, broken bones and lost limbs shown like potions
		WoundSigns.register();
		WoundMarks.register();
		// (0.17.54) the Potion of Cleansing: festering cured
		Cleansing.register();
		MedicineItems.register();
		// (0.19.24) herbal remedies' lasting effects, rag washing and boiling
		Herbal.register();
		RagWashing.register();
		// (0.21.39) held jobs' bars kept between holds
		com.jaymospowers.core.HoldProgress.register();
		Treatment.register();
		// (0.15.99) kneeling at someone's side to treat them
		Tending.register();
		SelfRevive.register();
		HealStruggle.register();
		// (0.20.26) death by cause: final, or left for dead with an injury that fits (after the downed state, which it follows)
		Mortality.register();
		// (Andy, 2026-11-19) blacked out with captors, guards or spiders round: they open their eyes where they were taken
		TakenOut.register();
		// Andy's picks E3 and E5: a minute for a reviver before a final death, and Limbo beside Reincarnate
		Revival.register();
		Limbo.register();
		// (0.19.20) hostiles walk up and finish a downed player off, filmed for them
		MobExecutions.register();
		// (0.15.82) up from the downed, but battered
		Battered.register();
		// (0.18.71) tripped by a bolas: a leg broken, face down on the ground a while
		Tripped.register();
		// (0.15.98) finishing the downed off, one move for each kind of weapon
		com.jaymospowers.core.finisher.Finishers.register();
		Pain.register();
		// (survival picks) a night's sleep heals every part
		PartStrain.register();
		// (0.19.24) rest by a campfire, soaking in a hot spring
		Resting.register();
		HotSprings.register();
		// (0.14.2) a killing blow can still take a head or a limb off, onto the corpse (as Dismember's own roll did)
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Downed.LAST, KILLING_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(KILLING_PHASE, (entity, source, amount) -> {
			noteKilling(entity, source, amount);
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Killing blow = KILLING.remove(entity);
			if (blow != null && !FINISHING.contains(entity) && entity.level().getGameTime() == blow.time()) {
				killingBlow(entity, blow);
			}
		});
	}

	// ---- public API -------------------------------------------------------------------------------------------------------

	/** The plan {@code entity}'s health is spread by, or null if it keeps plain vanilla health. */
	public static @Nullable BodyPlan plan(LivingEntity entity) {
		return BodyPlan.of(entity);
	}

	/** How whole {@code part} is (0-1); 1 if the creature has no such part. */
	public static float fraction(LivingEntity entity, BodyPart part) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null) {
			return 1.0F;
		}
		if (plan == BodyPlan.SIMPLE) {
			return entity.getHealth() / Math.max(0.01F, entity.getMaxHealth());
		}
		int index = plan.index(part);
		if (index < 0) {
			return 1.0F;
		}
		float[] points = load(entity, plan);
		float cap = cap(entity, plan, index);
		return cap <= 0.0F ? 1.0F : Mth.clamp(points[index] / cap, 0.0F, 1.0F);
	}

	/**
	 * (Survival picks) The most health the pools are measured against: the most health, without what broken hearts
	 * ({@link PartStrain}) take off it, so a part going black doesn't shrink every other pool with it.
	 */
	public static float poolMax(LivingEntity entity) {
		return PartStrain.poolMax(entity);
	}

	/** {@code part}'s health in points, and its most. */
	public static float points(LivingEntity entity, BodyPart part) {
		BodyPlan plan = BodyPlan.of(entity);
		int index = plan == null ? -1 : plan.index(part);
		return index < 0 ? 0.0F : fraction(entity, part) * cap(entity, plan, index);
	}

	/**
	 * Whether healing {@code entity}'s health would take. (Survival picks) The health heals apart from the parts now, so
	 * this is only whether it is below its most.
	 */
	public static boolean canHealMore(LivingEntity entity) {
		return entity.getHealth() < entity.getMaxHealth();
	}

	/** Part {@code index}'s whole pool, in health points. */
	public static float cap(LivingEntity entity, BodyPlan plan, int index) {
		return plan.share(index) * poolMax(entity);
	}

	/**
	 * Wounds {@code part} by {@code amount} (health points): its pool takes it (spilling over as a hit would) and the
	 * health takes it too. Health at zero, or head or body blacked out by it, downs or kills (by {@code by}, if any); it is
	 * no blow to the head, so it never kills outright.
	 */
	public static void hurtPart(LivingEntity entity, BodyPart part, float amount, @Nullable Entity by) {
		hurtPart(entity, part, amount, by, null);
	}

	/** (0.14.4) As above; if it kills, it's by {@code type} (null: {@link #WOUNDS_DAMAGE}; bleeding's own, {@link Bleeding#BLEEDING_DAMAGE}). */
	public static void hurtPart(LivingEntity entity, BodyPart part, float amount, @Nullable Entity by, @Nullable ResourceKey<DamageType> type) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || !(entity.level() instanceof ServerLevel level) || !entity.isAlive() || amount <= 0.0F || Downed.isDowned(entity)) {
			return;
		}
		float health = entity.getHealth();
		boolean out = health - amount <= EPS;
		// (not to zero: a dead creature has to die through its events)
		entity.setHealth(Math.max(0.01F, health - amount));
		if (plan == BodyPlan.SIMPLE) {
			if (out) {
				finish(level, entity, plan, by, type, false);
			}
			return;
		}
		float[] points = load(entity, plan);
		float[] was = points.clone();
		HitRouting.damage(points, plan, part, amount);
		store(entity, plan, points);
		// (0.14.2) a limb this took to zero breaks
		markBreaks(entity, plan, was, points, by);
		if (out || blacked(entity, was, points, plan)) {
			finish(level, entity, plan, by, type, false);
		}
	}

	/**
	 * (0.14.4) Heals {@code part} itself by up to {@code amount} (a dressing): its health and the creature's go up together,
	 * no further than the part may heal ({@link Breaks#caps}). Returns what it took.
	 */
	public static float healPart(LivingEntity entity, BodyPart part, float amount) {
		return healPart(entity, part, amount, true);
	}

	/** As {@link #healPart(LivingEntity, BodyPart, float)}; {@code slow} false when the caller already applied the cut once (Mending). */
	static float healPart(LivingEntity entity, BodyPart part, float amount, boolean slow) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || !(entity.level() instanceof ServerLevel) || !entity.isAlive() || amount <= 0.0F || Downed.isDowned(entity)) {
			return 0.0F;
		}
		// (0.20.14) the Hearthkeeper's trial and scar on one who turned from it (Jaymo's Kingdoms): every part heal is slower
		if (slow) {
			amount *= 1.0F - MedicineSkill.blessing(entity, "wounds_slow");
		}
		if (amount <= 0.0F) {
			return 0.0F;
		}
		if (plan == BodyPlan.SIMPLE) {
			float before = entity.getHealth();
			entity.setHealth(Math.min(entity.getMaxHealth(), before + amount));
			return entity.getHealth() - before;
		}
		int index = plan.index(part);
		if (index < 0) {
			return 0.0F;
		}
		float[] caps = Breaks.caps(entity, plan);
		float[] points = load(entity, plan);
		float most = cap(entity, plan, index) * (caps == null ? 1.0F : caps[index]);
		float add = Math.min(amount, Math.max(0.0F, most - points[index]));
		if (add <= EPS) {
			return 0.0F;
		}
		points[index] += add;
		// (survival picks) medicine heals the health with the part, each point its share (every pool healed from nothing
		// is the whole health back, so healing every part never heals the health several times over); it costs food
		entity.setHealth(Math.min(entity.getMaxHealth(), entity.getHealth() + add / plan.total()));
		store(entity, plan, points);
		payFood(entity, add);
		return add;
	}

	/**
	 * (Survival picks) {@code entity}'s parts mend by {@code share} of each pool (only the pools, not the health), no part
	 * past what it may heal to ({@link Breaks#caps}), and the food it costs is paid. Returns the points mended in all.
	 */
	static float mendEvery(LivingEntity entity, float share) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE || entity.getAttached(PARTS) == null || share <= 0.0F) {
			return 0.0F;
		}
		float[] points = load(entity, plan);
		float[] caps = Breaks.caps(entity, plan);
		float mended = 0.0F;
		for (int i = 0; i < points.length; i++) {
			float cap = cap(entity, plan, i);
			float most = cap * (caps == null ? 1.0F : caps[i]);
			if (cap > 0.0F && points[i] < most - EPS) {
				float add = Math.min(most - points[i], share * cap);
				points[i] += add;
				mended += add;
			}
		}
		if (mended > EPS) {
			store(entity, plan, points);
			payFood(entity, mended);
		}
		return mended;
	}

	/** (Survival picks, lso-food-cost) Healing {@code points} of the parts costs a player {@link #FOOD_PER_POINT} exhaustion each. */
	private static void payFood(LivingEntity entity, float points) {
		if (entity instanceof Player player && !player.isCreative() && points > 0.0F) {
			player.causeFoodExhaustion(points * FOOD_PER_POINT);
		}
	}

	/**
	 * (0.21.49) A downed body resting ({@link DownedStruggle}) mends {@code part} by up to {@code amount}, to no more than
	 * {@code share} of it and what its break allows ({@link Breaks#caps}). Only the parts: a downed one's health is their
	 * downed health, so it's left alone (they rise with what the parts hold, {@link #restedHealth}). Returns what it took.
	 */
	static float restPart(LivingEntity entity, BodyPart part, float amount, float share) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE || !(entity.level() instanceof ServerLevel) || !entity.isAlive() || amount <= 0.0F) {
			return 0.0F;
		}
		amount *= 1.0F - MedicineSkill.blessing(entity, "wounds_slow");
		int index = plan.index(part);
		if (index < 0 || amount <= 0.0F) {
			return 0.0F;
		}
		float[] caps = Breaks.caps(entity, plan);
		float[] points = load(entity, plan);
		float most = cap(entity, plan, index) * Math.min(share, caps == null ? 1.0F : caps[index]);
		float add = Math.min(amount, Math.max(0.0F, most - points[index]));
		if (add <= EPS) {
			return 0.0F;
		}
		points[index] += add;
		store(entity, plan, points);
		return add;
	}

	/** (0.21.49) The health a rested downed player rises with: (survival picks) as much of their most as their pools hold together. */
	static float restedHealth(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return 0.0F;
		}
		float[] points = load(entity, plan);
		float held = 0.0F, whole = 0.0F;
		for (int i = 0; i < points.length; i++) {
			held += points[i];
			whole += cap(entity, plan, i);
		}
		return whole <= 0.0F ? 0.0F : entity.getMaxHealth() * held / whole;
	}

	/**
	 * (0.14.2) Breaks {@code part} (an arm, leg, foot or wing; Fractures' blunt blow, a power's leg strike): what it has
	 * left is lost (health and all), and it is marked broken ({@link Breaks}). True if it wasn't broken already.
	 */
	public static boolean breakPart(LivingEntity entity, BodyPart part, @Nullable Entity by) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE || part.vital() || !(entity.level() instanceof ServerLevel) || !entity.isAlive()) {
			return false;
		}
		int index = plan.index(part);
		if (index < 0) {
			return false;
		}
		if (!Downed.isDowned(entity)) {
			// (survival picks) its pool is emptied; the health is the blow's to take, so it stays as it is
			float[] points = load(entity, plan);
			points[index] = 0.0F;
			store(entity, plan, points);
		}
		return Breaks.mark(entity, part, by);
	}

	/** (0.14.2) Sets {@code part}'s break: it heals normally from now on, and the break goes once it's above {@link Breaks#BROKEN_CAP}. True if it needed setting. */
	public static boolean setPart(LivingEntity entity, BodyPart part) {
		return Breaks.setPart(entity, part);
	}

	/** (0.14.2) Sets every break (a priest, a respawn, a Hermit Bean, Verdant's healing, Bloom, Zephyr's Breath). True if any needed setting. */
	public static boolean mendAll(LivingEntity entity) {
		return Breaks.mendAll(entity);
	}

	/** (0.17.164) Every break gone at once, the parts left to heal as they are (the symbiote knitting you whole). */
	public static void knitBones(LivingEntity entity) {
		Breaks.clearAll(entity);
	}

	/** (0.14.2) The broken parts, a bit per {@link BodyPart#ordinal()} (both sides). */
	public static int broken(Entity entity) {
		return Breaks.broken(entity);
	}

	public static boolean isBroken(Entity entity, BodyPart part) {
		return Breaks.isBroken(entity, part);
	}

	/** (0.14.2) The broken arms and legs as {@link Dismember} bits (both sides: drawn hanging limp). */
	public static int brokenLimbs(Entity entity) {
		return Breaks.limbs(entity);
	}

	/** (0.14.2) Every limb that went from something to zero between {@code was} and {@code now} is broken. */
	private static void markBreaks(LivingEntity entity, BodyPlan plan, float[] was, float[] now, @Nullable Entity by) {
		markBreaks(entity, plan, was, now, by, false);
	}

	/** (0.14.2) As above; {@code healing}: the breaks come set (a fall's). */
	private static void markBreaks(LivingEntity entity, BodyPlan plan, float[] was, float[] now, @Nullable Entity by, boolean healing) {
		for (int i = 0; i < now.length; i++) {
			BodyPart part = plan.part(i);
			if (!part.vital() && was[i] > EPS && now[i] <= EPS) {
				Breaks.mark(entity, part, by, healing);
			}
		}
	}

	/**
	 * (0.14.2) The overkill test on the part a blow {@code landed} on ({@code was} before it, {@code now} after, {@code loss}
	 * routed): a limb already broken, or the head taken past zero, by more than {@link Dismember#SEVER_OVERKILL} of it; then
	 * Dismember rolls ({@link Dismember#severOnOverkill}). True if the part came off.
	 */
	private static boolean sever(LivingEntity victim, BodyPlan plan, DamageSource source, @Nullable BodyPart landed, float[] was, float[] now,
		float loss, float max, float taken) {
		if (landed == null || taken < Dismember.MIN_DAMAGE || landed == BodyPart.LEFT_FOOT || landed == BodyPart.RIGHT_FOOT) {
			return false;
		}
		int index = plan.index(landed);
		int limb = Breaks.limb(landed);
		if (index < 0 || limb == 0) {
			return false;
		}
		float most = Dismember.SEVER_OVERKILL * plan.share(index) * max;
		boolean overkill = landed == BodyPart.HEAD
			? was[index] > EPS && now[index] <= EPS && loss - was[index] > most
			: !landed.vital() && Breaks.isBroken(victim, landed) && loss > most;
		return overkill && Dismember.severOnOverkill(victim, limb, source, taken);
	}

	/**
	 * (0.14.2) A blow that killed outright ({@link #KILLING}, noted as the death was let through): where it landed is
	 * worked out on the parts as they were before it (the dead keep theirs), and a blade's overkill may still take that
	 * part off, onto the corpse. Fabric's after-damage event never sees a killing blow, so this runs after the death.
	 */
	private static void killingBlow(LivingEntity victim, Killing blow) {
		BodyPlan plan = BodyPlan.of(victim);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		float[] points = blow.points().clone();
		float[] was = blow.points().clone();
		float loss = blow.amount();
		if (loss <= EPS) {
			return;
		}
		// (Andy, 2026-11-16) its lethal hit was already rolled as it went down (Downed): nothing more comes off
		if (Dismember.lethalRolled(victim)) {
			return;
		}
		BodyPart aimed = Dismember.cutAim(victim);
		BodyPart landed;
		if (aimed != null && plan.index(aimed) >= 0) {
			HitRouting.damage(points, plan, aimed, loss);
			landed = aimed;
		} else {
			landed = HitRouting.route(victim, plan, blow.source(), points, poolMax(victim), loss);
		}
		// (0.20.61) a heavy chop to the torso may cut the body in half (Downed already tried it on the downable)
		if (Dismember.halve(victim, blow.source(), loss, landed == BodyPart.BODY)) {
			return;
		}
		// (Andy, 2026-11-16) a humanoid's killing hit takes the part it landed on, a head half the time (no overkill roll)
		if (Dismember.lethalHit(victim, blow.source())) {
			Dismember.lethalBlow(victim, blow.source(), landed);
			return;
		}
		sever(victim, plan, blow.source(), landed, was, points, loss, poolMax(victim), loss);
	}

	/** (0.14.2) Notes a death about to go through: the blow, how hard, and the parts as they were, when (game time). */
	private static void noteKilling(LivingEntity victim, DamageSource source, float amount) {
		BodyPlan plan = BodyPlan.of(victim);
		if (FINISHING.contains(victim) || amount <= 0.0F || source.is(Dismember.DECAPITATION) || source.is(WOUNDS_DAMAGE) || source.is(Bleeding.BLEEDING_DAMAGE) || plan == null || plan == BodyPlan.SIMPLE || !(victim.level() instanceof ServerLevel level)
			|| victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		// (0.15.94) a blow the mixin already took from the parts is judged on the parts as they were before it
		Routed hit = ROUTED.remove(victim);
		float[] before = hit != null && hit.source() == source ? hit.was() : load(victim, plan);
		KILLING.put(victim, new Killing(source, amount, before, level.getGameTime()));
	}

	/** Full health, every part whole. */
	public static void healAll(LivingEntity entity) {
		// (0.14.2) and every break gone with it
		Breaks.clearAll(entity);
		// (0.14.4) and every cut and fever
		Bleeding.clearAll(entity);
		// (0.15.82) and the battering from getting up
		Battered.clear(entity);
		entity.setHealth(entity.getMaxHealth());
		entity.removeAttached(PARTS);
		entity.removeAttached(PARTS_MAX);
		entity.removeTag(FIGHTS_ON_TAG);
		// (survival picks) and the broken hearts with them
		BodyPlan plan = BodyPlan.of(entity);
		if (plan != null) {
			PartStrain.brokenHearts(entity, plan);
		}
		entity.setHealth(entity.getMaxHealth());
	}

	/**
	 * (0.18.73) Creative heals everything: switching into it, and every reconcile while in it. Every part whole,
	 * every break, cut and fever gone, severed limbs grown back, a bolas trip undone (the downed get up in
	 * {@link Downed}'s own tick).
	 */
	static void creativeHeal(ServerPlayer player) {
		// a limb wearing a prosthetic stays as it is: creative never pops a fitted wooden, iron or Rig limb off
		int fitted = com.jaymospowers.core.Prosthetics.fitted(player);
		if ((Dismember.missing(player) & ~fitted) != 0) {
			Dismember.regrow(player, true, fitted);
		}
		Tripped.clear(player);
		if (player.getAttached(PARTS) != null || Breaks.broken(player) != 0 || Bleeding.bleeding(player) != 0
			|| Bleeding.festering(player) != 0 || Battered.is(player) || player.entityTags().contains(FIGHTS_ON_TAG)
			|| player.getHealth() < player.getMaxHealth()) {
			healAll(player);
		}
	}

	/**
	 * (0.20.50) Every wound undone, as creative mode heals (Chrono's rewind out of death): breaks, cuts, fevers, severed
	 * limbs, a bolas trip. Health is the caller's to set after.
	 */
	public static void undoAll(ServerPlayer player) {
		creativeHeal(player);
	}

	/**
	 * (Survival picks) The parts keep their own pools apart from the health now, so ordinary healing (food, potions)
	 * doesn't move them. This is for the callers that set the health on purpose (a revive, getting up, a rewind, a
	 * lifesaver): every part below the health's share of its most is lifted to it (as far as it may heal,
	 * {@link Breaks#caps}); none is ever lowered. A full heal is {@link #healAll}.
	 */
	public static void reconcile(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE || entity.getAttached(PARTS) == null) {
			return;
		}
		float share = Mth.clamp(entity.getHealth() / Math.max(0.01F, entity.getMaxHealth()), 0.0F, 1.0F);
		float[] caps = Breaks.caps(entity, plan);
		float[] points = load(entity, plan);
		for (int i = 0; i < points.length; i++) {
			float most = cap(entity, plan, i) * Math.min(share, caps == null ? 1.0F : caps[i]);
			points[i] = Math.max(points[i], most);
		}
		store(entity, plan, points);
	}

	/**
	 * (0.14.1) After a death was cancelled ({@link Downed}): head and body hold on to at least {@link #MIN_VITAL_AFTER_SAVE}
	 * of themselves, so nobody is up (or down) with a blacked-out head or body.
	 */
	public static void settle(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		float[] points = load(entity, plan);
		ensureVital(entity, points, plan);
		store(entity, plan, points);
	}

	/**
	 * (0.15.94) Patched up: nothing bleeding or festering, no break left unset, and every part missing more than
	 * {@link #PATCH_FROM} of its pool wearing a dressing or a splint. Until then natural healing runs at {@link #UNPATCHED_REGEN}.
	 */
	public static boolean patchedUp(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return true;
		}
		if ((Bleeding.bleeding(entity) | Bleeding.festering(entity) | Breaks.unset(entity)) != 0) {
			return false;
		}
		if (entity.getAttached(PARTS) == null) {
			return true;
		}
		int dressed = Bleeding.wrapped(entity) | Breaks.splinted(entity);
		float[] points = load(entity, plan);
		for (int i = 0; i < points.length; i++) {
			BodyPart part = plan.part(i);
			float cap = cap(entity, plan, i);
			if (cap > 0.0F && points[i] < (1.0F - PATCH_FROM) * cap - EPS && !Breaks.missing(entity, part)
				&& (dressed & Breaks.bit(part)) == 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * (Survival picks) Every {@link #PLAYER_RECONCILE_TICKS}, a fed player's parts mend a little by themselves (a whole
	 * part in {@link #PART_MEND_TICKS}, at {@link #UNPATCHED_REGEN} of that until patched up), paying food for it.
	 */
	private static void mendParts(ServerPlayer player, boolean patched) {
		if (Downed.isDowned(player) || player.getFoodData().getFoodLevel() < MEND_FOOD_MIN) {
			return;
		}
		mendEvery(player, PLAYER_RECONCILE_TICKS / (float) PART_MEND_TICKS * (patched ? 1.0F : UNPATCHED_REGEN));
	}

	/** (Survival picks) Whether {@code entity} is being finished by a blow that blacked out its head while it stood (so it isn't downed). */
	public static boolean headKilled(LivingEntity entity) {
		return HEAD_KILLS.contains(entity);
	}

	// ---- hits -------------------------------------------------------------------------------------------------------------

	/**
	 * (0.15.94) A hit as vanilla is about to take it from the health ({@code WoundsHurtMixin}, around {@code setHealth} in
	 * {@code actuallyHurt}): {@code amount} (after armour and absorption) lands on the parts too. (Survival picks) The health
	 * takes the hit as vanilla does, made harder by a headshot ({@link HitRouting#HEADSHOT}) and by the 20% debuffs
	 * ({@link PartStrain#hitScale}). Returns the health to set. The rest of the blow (breaks, cuts, severing, downing)
	 * follows in {@link #onDamage}.
	 */
	public static float takeHit(LivingEntity victim, DamageSource source, float amount, float vanilla) {
		if (amount <= EPS || FINISHING.contains(victim) || !(victim.level() instanceof ServerLevel) || !victim.isAlive()
			|| victim.getHealth() <= 0.0F || Downed.isDowned(victim) || source.is(WOUNDS_DAMAGE)) {
			return vanilla;
		}
		BodyPlan plan = BodyPlan.of(victim);
		if (plan == null || plan == BodyPlan.SIMPLE || victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return vanilla;
		}
		float before = victim.getHealth();
		float scale = PartStrain.hitScale(victim, source);
		Routed hit = route(victim, plan, source, before, amount * scale);
		ROUTED.put(victim, hit);
		if (HitRouting.headshot(victim, source, hit.landed())) {
			scale *= HitRouting.HEADSHOT;
		}
		// (never above what vanilla had: the hit only ever takes)
		return Math.min(before, before - amount * scale);
	}

	/** The part {@code source}'s hit on {@code victim} landed on, while it lands (before its after-damage), or null if not known. */
	static @Nullable BodyPart landed(LivingEntity victim, DamageSource source) {
		Routed hit = ROUTED.get(victim);
		return hit != null && hit.source() == source ? hit.landed() : null;
	}

	/** (0.15.94) Takes {@code loss} from the parts where {@code source} lands it; stores them. {@code health}: theirs as the blow began. */
	private static Routed route(LivingEntity victim, BodyPlan plan, DamageSource source, float health, float loss) {
		float max = poolMax(victim);
		// (asked before the parts are stored: broken hearts may lower the most health)
		boolean full = health >= victim.getMaxHealth() - NoOneShot.SLACK;
		float[] points = load(victim, plan);
		float[] was = points.clone();
		// (0.14.2) a power's own cut (Dismember.cut) lands where it was aimed
		BodyPart aimed = Dismember.cutAim(victim);
		BodyPart landed;
		boolean locked = false;
		if (aimed != null && plan.index(aimed) >= 0) {
			HitRouting.damage(points, plan, aimed, loss);
			landed = aimed;
		} else {
			// a player locked onto it aims at a region (LockOnRegions): an aimed head hit bites harder
			BodyPart lock = HitRouting.lockAimed(victim, plan, source);
			if (lock != null) {
				locked = true;
				loss = LockOnRegions.scale(lock, loss);
			}
			landed = HitRouting.route(victim, plan, source, points, max, loss);
		}
		store(victim, plan, points);
		return new Routed(source, landed, was, points, loss, locked, full);
	}

	private static void onDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		Routed hit = ROUTED.remove(victim);
		if (taken <= 0.0F && hit == null || FINISHING.contains(victim) || !(victim.level() instanceof ServerLevel level) || !victim.isAlive()
			|| victim.getHealth() <= 0.0F) {
			return;
		}
		BodyPlan plan = BodyPlan.of(victim);
		if (plan == null || plan == BodyPlan.SIMPLE || victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		if (Downed.isDowned(victim)) {
			// (0.14.1) down: the hits are the health's alone (head and body kept), so only the health running out ends them
			settle(victim);
			return;
		}
		if (hit == null || hit.source() != source) {
			// (0.15.94) a hit the mixin never saw (an entity that takes its health its own way): vanilla took it from the
			// health, so the parts take it now
			hit = route(victim, plan, source, victim.getHealth() + taken, taken);
		}
		float max = poolMax(victim);
		float[] was = hit.was();
		float[] points = hit.now();
		float loss = hit.loss();
		BodyPart landed = hit.landed();
		Entity by = source.getEntity();
		// (0.20.61) a zombie cut in half at the waist by a heavy chop crawls on as its upper half
		if (!blocked && Dismember.zombie(victim) && Dismember.halve(victim, source, loss, landed == BodyPart.BODY)) {
			return;
		}
		// (0.14.2) overkill: a blade on a limb already broken, or past the head's zero, may cut it off
		if (!blocked && sever(victim, plan, source, landed, was, points, loss, max, loss)
			&& (landed == BodyPart.HEAD || !victim.isAlive())) {
			// (a severed head is Dismember's to end: death, a blind stagger, or an undead fighting on)
			return;
		}
		// (0.14.2) a limb this hit took to zero breaks (a fall's breaks heal by themselves: no priest for a hard landing)
		markBreaks(victim, plan, was, points, by, source.is(DamageTypeTags.IS_FALL));
		// the last of its legs gone black: a giant can't stand (Giants), the Great Ape goes down
		if (!blocked) {
			Giants.afterHit(victim, plan, was, points);
		}
		// a heavy blow to a bare head may take an eye
		if (!blocked && landed == BodyPart.HEAD && victim instanceof ServerPlayer struck && victim.isAlive()) {
			com.jaymospowers.power.blindness.Blind.struck(struck, source, loss);
		}
		// (0.14.4) a blade, a shot or claws that cut deep enough opens a bleeding cut
		if (!blocked && victim.isAlive()) {
			Bleeding.onHit(victim, plan, source, landed, was, points);
			// a lock-on aimed leg slows, an aimed arm weakens
			if (hit.aimed() && landed != null) {
				LockOnRegions.onAimedHit(victim, source, landed, loss);
			}
			// a mark of what made it, where it landed
			WoundMarks.onHit(victim, plan, source, landed, loss);
		}
		if (victim.isAlive() && blacked(victim, was, points, plan)) {
			// (survival picks) only a blow that landed on the head and blacked it out kills outright, and not from full health
			int head = plan.index(BodyPart.HEAD);
			boolean headKill = landed == BodyPart.HEAD && head >= 0 && was[head] > EPS && points[head] <= EPS && !hit.full();
			finish(level, victim, plan, by, null, headKill);
		}
	}

	/**
	 * Head or body went black: the creature goes down, through its death events ({@link Downed} downs the downable; the rest
	 * die). {@code headKill}: a blow to the head of one standing, which ends them outright ({@link #headKilled}).
	 */
	private static void finish(ServerLevel level, LivingEntity victim, BodyPlan plan, @Nullable Entity by, @Nullable ResourceKey<DamageType> type,
		boolean headKill) {
		if (FINISHING.contains(victim)) {
			return;
		}
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type == null ? WOUNDS_DAMAGE : type), by);
		FINISHING.add(victim);
		if (headKill) {
			HEAD_KILLS.add(victim);
		}
		try {
			victim.setInvulnerableTime(0);
			victim.hurtServer(level, source, Float.MAX_VALUE);
		} finally {
			FINISHING.remove(victim);
			HEAD_KILLS.remove(victim);
		}
		if (victim.isAlive() && victim.getHealth() > 0.0F) {
			// something cancelled the death: head and body get a little back
			settle(victim);
		}
	}

	// ---- the parts' arithmetic --------------------------------------------------------------------------------------------

	/** The parts in health points. A creature with no attachment is whole. */
	static float[] load(LivingEntity entity, BodyPlan plan) {
		float max = poolMax(entity);
		// a raised max is added capacity, not a loss: the fractions still mean their old max
		Float known = entity.getAttached(PARTS_MAX);
		float base = known != null && known > 0.0F && known < max ? known : max;
		List<Float> fractions = entity.getAttached(PARTS);
		float[] points = new float[plan.size()];
		for (int i = 0; i < points.length; i++) {
			float fraction = fractions != null && fractions.size() == points.length ? Mth.clamp(fractions.get(i), 0.0F, 1.0F) : 1.0F;
			points[i] = fraction * plan.share(i) * base;
		}
		return points;
	}

	/** Saves the parts as fractions; whole, the attachment goes. (A simple body keeps none: its health is all it is.) */
	static void store(LivingEntity entity, BodyPlan plan, float[] points) {
		if (plan == BodyPlan.SIMPLE) {
			return;
		}
		float max = poolMax(entity);
		List<Float> fractions = new ArrayList<>(points.length);
		boolean whole = true;
		for (int i = 0; i < points.length; i++) {
			float cap = plan.share(i) * max;
			float fraction = cap <= 0.0F ? 1.0F : Mth.clamp(points[i] / cap, 0.0F, 1.0F);
			whole &= fraction >= WHOLE;
			fractions.add(fraction);
		}
		Float knownMax = entity.getAttached(PARTS_MAX);
		if (knownMax == null || knownMax != max) {
			entity.setAttached(PARTS_MAX, max);
		}
		List<Float> old = entity.getAttached(PARTS);
		if (whole) {
			if (old != null) {
				entity.removeAttached(PARTS);
			}
		} else if (!fractions.equals(old)) {
			entity.setAttached(PARTS, List.copyOf(fractions));
		}
		// (survival picks) every part at zero takes a share off the most health
		PartStrain.brokenHearts(entity, plan);
	}

	/**
	 * (Survival picks) Whether this hit ({@code was} before it, {@code now} after) blacked out the head or the body, which
	 * downs (or kills what can't be downed). A zombie or skeleton whose body gave out may fight on instead
	 * ({@link #UNDEAD_FIGHT_ON}, rolled once; then only its head counts), and one that never needed its head counts only its
	 * body. Only the going black counts, so one up again with a part still at nothing isn't felled by the next scratch.
	 */
	static boolean blacked(LivingEntity entity, float[] was, float[] now, BodyPlan plan) {
		int head = plan.index(BodyPart.HEAD);
		int body = plan.index(BodyPart.BODY);
		boolean headOut = head >= 0 && was[head] > EPS && now[head] <= EPS;
		boolean bodyOut = body >= 0 && was[body] > EPS && now[body] <= EPS;
		if (!headOut && !bodyOut) {
			return false;
		}
		if (head >= 0 && Dismember.headless(entity) && entity.getType().builtInRegistryHolder().is(Dismember.HEADLESS_UNDEAD)) {
			// (0.14.2) it never needed its head (Dismember): only its body counts
			return bodyOut;
		}
		if (headOut || !(entity instanceof Zombie || entity.getType().builtInRegistryHolder().is(EntityTypeTags.SKELETONS))) {
			return true;
		}
		if (entity.entityTags().contains(FIGHTS_ON_TAG)) {
			return false;
		}
		if (entity.getRandom().nextFloat() < UNDEAD_FIGHT_ON) {
			entity.addTag(FIGHTS_ON_TAG);
			return false;
		}
		return true;
	}

	/** Head and body hold at least {@link #MIN_VITAL_AFTER_SAVE} of their pools. */
	private static void ensureVital(LivingEntity entity, float[] points, BodyPlan plan) {
		for (int i = 0; i < points.length; i++) {
			if (plan.part(i).vital()) {
				points[i] = Math.max(points[i], MIN_VITAL_AFTER_SAVE * cap(entity, plan, i));
			}
		}
	}
}
