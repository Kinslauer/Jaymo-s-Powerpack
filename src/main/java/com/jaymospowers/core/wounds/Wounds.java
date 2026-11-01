package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
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
 * ({@link #hearts}): the first {@link #HEART_SPILL_FROM} of a part's own pool is bruising and costs no hearts, past that
 * its hearts drain faster, so a part at zero has cost its whole share. Health still adds up to the parts, only through
 * that curve. Meals and food bring the deep wounds back to the bruise line; the bruises mend by themselves
 * ({@link #BRUISE_MEND_TICKS}); and both run at {@link #UNPATCHED_REGEN} of their speed until every wound past the bruise
 * line is dressed or splinted and nothing bleeds, festers or sits broken unset ({@link #patchedUp}). Treatment is the
 * way back.
 */
public final class Wounds {
	/** Smaller than this many health points is nothing. */
	static final float EPS = 1.0E-4F;
	/** A death something cancelled ({@link Downed}, Xenoform reverts): head and body get at least this fraction back. */
	public static final float MIN_VITAL_AFTER_SAVE = 0.1F;
	/** A health drop nobody routed (some other mod's setHealth) never takes head or body below this fraction: it never kills. */
	public static final float MIN_VITAL_UNROUTED = 0.05F;
	/** Players have their parts brought in line with their health this often (ticks), so the HUD follows healing. */
	public static final int PLAYER_RECONCILE_TICKS = 10;
	/** A fraction this full counts as whole. */
	private static final float WHOLE = 0.999F;
	/** (0.14.1) A zombie's or skeleton's chance, when its body (not its head) gives out, to fight on: then only the head kills it. */
	public static final float UNDEAD_FIGHT_ON = 0.6F;
	/** (0.14.1) The scoreboard tag on an undead fighting on with its body gone. */
	public static final String FIGHTS_ON_TAG = "jaymospowers_fights_on";
	/** (0.15.94) Share of a part's pool a hit has to take before it costs hearts (the bruising before it is the part's alone). */
	public static final float HEART_SPILL_FROM = 0.3F;
	/** (0.15.94) Natural healing (meals, food, bruises mending) runs at this share of its speed until you're patched up ({@link #patchedUp}). */
	public static final float UNPATCHED_REGEN = 0.25F;
	/** (0.15.94) Ticks a part takes to mend a full bruise (from {@link #HEART_SPILL_FROM} down back to whole), patched up. */
	public static final int BRUISE_MEND_TICKS = 3600;
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

	/** (0.15.94) A hit already taken from the parts as it landed ({@link #takeHit}), waiting for the after-damage event. */
	private record Routed(DamageSource source, @Nullable BodyPart landed, float[] was, float[] now, float loss) {
	}

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
					mendBruises(player, patched);
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
		// (0.17.183) Kingdoms' Will pages: No Getting Up, No Mercy, Grim Hand, hushes, Will Over Flesh
		WillSkill.register();
		// (0.14.2) phase 3: broken limbs
		Breaks.register();
		// (0.14.4) phase 4: cuts and festering, the medieval kit, pain and the poppy
		Bleeding.register();
		// (0.17.54) the Potion of Cleansing: festering cured
		Cleansing.register();
		MedicineItems.register();
		// (0.19.24) herbal remedies' lasting effects, rag washing and boiling
		Herbal.register();
		RagWashing.register();
		Treatment.register();
		// (0.15.99) kneeling at someone's side to treat them
		Tending.register();
		SelfRevive.register();
		// (0.19.20) hostiles walk up and finish a downed player off, filmed for them
		MobExecutions.register();
		// (0.15.82) up from the downed, but battered
		Battered.register();
		// (0.18.71) tripped by a bolas: a leg broken, face down on the ground a while
		Tripped.register();
		// (0.15.98) finishing the downed off, one move for each kind of weapon
		com.jaymospowers.core.finisher.Finishers.register();
		Pain.register();
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
		float max = entity.getMaxHealth();
		float[] points = load(entity, plan);
		reconcile(points, plan, max, entity.getHealth(), Breaks.caps(entity, plan));
		float cap = plan.share(index) * max;
		return cap <= 0.0F ? 1.0F : Mth.clamp(points[index] / cap, 0.0F, 1.0F);
	}

	/** {@code part}'s health in points, and its most. */
	public static float points(LivingEntity entity, BodyPart part) {
		BodyPlan plan = BodyPlan.of(entity);
		int index = plan == null ? -1 : plan.index(part);
		return index < 0 ? 0.0F : fraction(entity, part) * cap(entity, plan, index);
	}

	public static float cap(LivingEntity entity, BodyPlan plan, int index) {
		return plan.share(index) * entity.getMaxHealth();
	}

	/**
	 * Wounds {@code part} by {@code amount} (health points): lowers the creature's health by what the parts could
	 * give, spilling over as a hit would. Head or body at zero kills (by {@code by}, if any).
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
		float max = entity.getMaxHealth();
		float health = entity.getHealth();
		float[] points = load(entity, plan);
		reconcile(points, plan, max, health, Breaks.caps(entity, plan));
		// (0.14.2) health the broken parts couldn't hold isn't there to lose
		health = Math.min(health, hearts(points, plan, max));
		float[] was = points.clone();
		float before = hearts(points, plan, max);
		HitRouting.damage(points, plan, part, amount);
		// (0.15.94) only what goes past the bruising costs hearts
		float removed = before - hearts(points, plan, max);
		// (not to zero: a dead creature has to die through its events)
		entity.setHealth(Math.max(0.01F, health - removed));
		store(entity, plan, points);
		// (0.14.2) a limb this took to zero breaks
		markBreaks(entity, plan, was, points, by);
		if (lethal(entity, points, plan)) {
			finish(level, entity, plan, by, type);
		}
	}

	/**
	 * (0.14.4) Heals {@code part} itself by up to {@code amount} (a dressing): its health and the creature's go up together,
	 * no further than the part may heal ({@link Breaks#caps}). Returns what it took.
	 */
	public static float healPart(LivingEntity entity, BodyPart part, float amount) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || !(entity.level() instanceof ServerLevel) || !entity.isAlive() || amount <= 0.0F || Downed.isDowned(entity)) {
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
		float max = entity.getMaxHealth();
		float[] caps = Breaks.caps(entity, plan);
		float[] points = load(entity, plan);
		reconcile(points, plan, max, entity.getHealth(), caps);
		float health = Math.min(entity.getHealth(), hearts(points, plan, max));
		float most = plan.share(index) * max * (caps == null ? 1.0F : caps[index]);
		float add = Math.min(amount, Math.max(0.0F, most - points[index]));
		if (add <= EPS) {
			return 0.0F;
		}
		float before = hearts(points, plan, max);
		points[index] += add;
		// (0.15.94) the hearts come back as the part climbs to the bruise line; a bruise's own healing costs none
		entity.setHealth(Math.min(max, health + hearts(points, plan, max) - before));
		store(entity, plan, points);
		return add;
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
			float max = entity.getMaxHealth();
			float health = entity.getHealth();
			float[] points = load(entity, plan);
			reconcile(points, plan, max, health, Breaks.caps(entity, plan));
			health = Math.min(health, hearts(points, plan, max));
			float before = hearts(points, plan, max);
			points[index] = 0.0F;
			float lost = before - hearts(points, plan, max);
			// (a limb alone never kills: what's left is the rest of the parts)
			entity.setHealth(Math.max(0.01F, health - lost));
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
		return overkill && Dismember.severOnOverkill(victim, limb, source);
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
		BodyPart aimed = Dismember.cutAim(victim);
		BodyPart landed;
		if (aimed != null && plan.index(aimed) >= 0) {
			HitRouting.damage(points, plan, aimed, loss);
			landed = aimed;
		} else {
			landed = HitRouting.route(victim, plan, blow.source(), points, victim.getMaxHealth(), loss);
		}
		sever(victim, plan, blow.source(), landed, was, points, loss, victim.getMaxHealth(), loss);
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
	}

	/**
	 * (0.18.73) Creative heals everything: switching into it, and every reconcile while in it. Every part whole,
	 * every break, cut and fever gone, severed limbs grown back, a bolas trip undone (the downed get up in
	 * {@link Downed}'s own tick).
	 */
	static void creativeHeal(ServerPlayer player) {
		if (Dismember.missing(player) != 0) {
			Dismember.regrow(player, true);
		}
		Tripped.clear(player);
		if (player.getAttached(PARTS) != null || Breaks.broken(player) != 0 || Bleeding.bleeding(player) != 0
			|| Bleeding.festering(player) != 0 || Battered.is(player) || player.entityTags().contains(FIGHTS_ON_TAG)
			|| player.getHealth() < player.getMaxHealth()) {
			healAll(player);
		}
	}

	/** Brings the parts in line with the health (healing fills the most hurt part, an unrouted loss comes off the limbs first). */
	public static void reconcile(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		Float known = entity.getAttached(PARTS_MAX);
		if (entity.getAttached(PARTS) == null && (entity.getHealth() < entity.getMaxHealth()
			|| known == null || known >= entity.getMaxHealth())) {
			// no parts saved: a gap below the max (e.g. after a raised max) is no unrouted loss, so no phantom wounds
			return;
		}
		float[] points = load(entity, plan);
		float refused = reconcile(points, plan, entity.getMaxHealth(), entity.getHealth(), Breaks.caps(entity, plan));
		store(entity, plan, points);
		if (refused > EPS && entity.isAlive()) {
			// (0.14.2) healing the broken and severed parts can't take: health stays what the parts hold
			entity.setHealth(Math.max(0.01F, hearts(points, plan, entity.getMaxHealth())));
		}
	}

	/**
	 * (0.14.1) After a death was cancelled ({@link Downed}): the parts follow the health, and head and body hold on to at
	 * least {@link #MIN_VITAL_AFTER_SAVE} of themselves.
	 */
	public static void settle(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		float max = entity.getMaxHealth();
		float[] points = load(entity, plan);
		reconcile(points, plan, max, entity.getHealth(), Breaks.caps(entity, plan));
		ensureVital(points, plan, max);
		store(entity, plan, points);
	}

	/**
	 * (0.15.94) Patched up: nothing bleeding or festering, no break left unset, and every part hurt past the bruise line
	 * ({@link #HEART_SPILL_FROM}) wearing a dressing or a splint. Until then natural healing runs at {@link #UNPATCHED_REGEN}.
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
		float max = entity.getMaxHealth();
		float[] points = load(entity, plan);
		for (int i = 0; i < points.length; i++) {
			BodyPart part = plan.part(i);
			float cap = plan.share(i) * max;
			if (cap > 0.0F && points[i] < (1.0F - HEART_SPILL_FROM) * cap - EPS && !Breaks.missing(entity, part)
				&& (dressed & Breaks.bit(part)) == 0) {
				return false;
			}
		}
		return true;
	}

	/** (0.15.94) Bruised parts (above the bruise line, not broken, cut or festering) mend a little, every {@link #PLAYER_RECONCILE_TICKS}. */
	private static void mendBruises(LivingEntity entity, boolean patched) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE || entity.getAttached(PARTS) == null || Downed.isDowned(entity)) {
			return;
		}
		float max = entity.getMaxHealth();
		float[] points = load(entity, plan);
		float[] caps = Breaks.caps(entity, plan);
		float step = HEART_SPILL_FROM * PLAYER_RECONCILE_TICKS / (float) BRUISE_MEND_TICKS * (patched ? 1.0F : UNPATCHED_REGEN);
		boolean changed = false;
		for (int i = 0; i < points.length; i++) {
			float cap = plan.share(i) * max;
			float most = cap * (caps == null ? 1.0F : caps[i]);
			if (cap > 0.0F && points[i] >= (1.0F - HEART_SPILL_FROM) * cap - EPS && points[i] < most - EPS) {
				points[i] = Math.min(most, points[i] + step * cap);
				changed = true;
			}
		}
		if (changed) {
			store(entity, plan, points);
		}
	}

	// ---- hits -------------------------------------------------------------------------------------------------------------

	/**
	 * (0.15.94) A hit as vanilla is about to take it from the health ({@code WoundsHurtMixin}, around {@code setHealth} in
	 * {@code actuallyHurt}): {@code amount} (after armour and absorption) lands on the parts first, and the health it
	 * leaves is what the parts' hearts come to ({@link #hearts}), not {@code health - amount}. Returns the health to set.
	 * The rest of the blow (breaks, cuts, severing, death of wounds) follows in {@link #onDamage}.
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
		float max = victim.getMaxHealth();
		float before = victim.getHealth();
		Routed hit = route(victim, plan, source, before, amount);
		ROUTED.put(victim, hit);
		// (never above what vanilla had: the hit only ever takes)
		return Math.min(before, hearts(hit.now(), plan, max));
	}

	/** (0.15.94) Takes {@code loss} from the parts as they stand at {@code health}, where {@code source} lands it; stores them. */
	private static Routed route(LivingEntity victim, BodyPlan plan, DamageSource source, float health, float loss) {
		float max = victim.getMaxHealth();
		float[] points = load(victim, plan);
		reconcile(points, plan, max, health, Breaks.caps(victim, plan));
		float[] was = points.clone();
		// (0.14.2) a power's own cut (Dismember.cut) lands where it was aimed
		BodyPart aimed = Dismember.cutAim(victim);
		BodyPart landed;
		if (aimed != null && plan.index(aimed) >= 0) {
			HitRouting.damage(points, plan, aimed, loss);
			landed = aimed;
		} else {
			landed = HitRouting.route(victim, plan, source, points, max, loss);
		}
		store(victim, plan, points);
		return new Routed(source, landed, was, points, loss);
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
		float max = victim.getMaxHealth();
		float health = victim.getHealth();
		float[] caps = Breaks.caps(victim, plan);
		if (Downed.isDowned(victim)) {
			// (0.14.1) down: the parts only follow the health (head and body kept), so only the health running out ends them
			float[] points = load(victim, plan);
			reconcile(points, plan, max, health, caps);
			ensureVital(points, plan, max);
			store(victim, plan, points);
			return;
		}
		if (hit == null || hit.source() != source) {
			// (0.15.94) a hit the mixin never saw (an entity that takes its health its own way): vanilla took it from the
			// hearts, so it's routed now and the hearts the bruising didn't cost are given back
			float before = Math.min(max, health + taken);
			hit = route(victim, plan, source, before, before - health);
			victim.setHealth(Math.max(0.01F, Math.min(before, hearts(hit.now(), plan, max))));
		}
		float[] was = hit.was();
		float[] points = hit.now();
		float loss = hit.loss();
		BodyPart landed = hit.landed();
		if (hearts(points, plan, max) < victim.getHealth() - EPS && caps != null) {
			// (0.14.2) health the broken and severed parts couldn't hold (healed in since the last look) is refused
			victim.setHealth(Math.max(0.01F, hearts(points, plan, max)));
		}
		Entity by = source.getEntity();
		// (0.14.2) overkill: a blade on a limb already broken, or past the head's zero, may cut it off
		if (!blocked && sever(victim, plan, source, landed, was, points, loss, max, loss)
			&& (landed == BodyPart.HEAD || !victim.isAlive())) {
			// (a severed head is Dismember's to end: death, a blind stagger, or an undead fighting on)
			return;
		}
		// (0.14.2) a limb this hit took to zero breaks (a fall's breaks heal by themselves: no priest for a hard landing)
		markBreaks(victim, plan, was, points, by, source.is(DamageTypeTags.IS_FALL));
		// (0.14.4) a blade, a shot or claws that cut deep enough opens a bleeding cut
		if (!blocked && victim.isAlive()) {
			Bleeding.onHit(victim, plan, source, landed, was, points);
		}
		if (victim.isAlive() && lethal(victim, points, plan)) {
			finish(level, victim, plan, by, null);
		}
	}

	/** Head or body is gone: the creature dies of its wounds, through its death events (so {@link Downed} can down them instead). */
	private static void finish(ServerLevel level, LivingEntity victim, BodyPlan plan, @Nullable Entity by, @Nullable ResourceKey<DamageType> type) {
		if (FINISHING.contains(victim)) {
			return;
		}
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type == null ? WOUNDS_DAMAGE : type), by);
		FINISHING.add(victim);
		try {
			victim.setInvulnerableTime(0);
			victim.hurtServer(level, source, Float.MAX_VALUE);
		} finally {
			FINISHING.remove(victim);
		}
		if (victim.isAlive() && victim.getHealth() > 0.0F) {
			// something cancelled the death: the parts follow the health, and head and body get a little back
			float max = victim.getMaxHealth();
			float[] points = load(victim, plan);
			reconcile(points, plan, max, victim.getHealth(), Breaks.caps(victim, plan));
			ensureVital(points, plan, max);
			store(victim, plan, points);
		}
	}

	// ---- the parts' arithmetic --------------------------------------------------------------------------------------------

	/** The parts in health points. A creature with no attachment is whole. */
	static float[] load(LivingEntity entity, BodyPlan plan) {
		float max = entity.getMaxHealth();
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
		float max = entity.getMaxHealth();
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
	}

	/**
	 * (0.15.94) The hearts a part's {@code points} (of its {@code cap}) are worth: all of its share while only bruised (down
	 * to {@link #HEART_SPILL_FROM} taken), then falling to nothing as the part does.
	 */
	static float heart(float points, float cap) {
		return cap <= 0.0F ? 0.0F : Mth.clamp(points / (1.0F - HEART_SPILL_FROM), 0.0F, cap);
	}

	/** (0.15.94) A part's points for {@code hearts} of its {@code cap}; whole hearts leave a bruise as it was ({@code points}), up to the bruise line. */
	static float unheart(float hearts, float cap, float points) {
		if (cap <= 0.0F) {
			return 0.0F;
		}
		float line = (1.0F - HEART_SPILL_FROM) * cap;
		return hearts >= cap - EPS ? Mth.clamp(points, line, cap) : Math.max(0.0F, hearts) * (1.0F - HEART_SPILL_FROM);
	}

	/** (0.15.94) The health {@code points} come to: each part's {@link #heart}. */
	static float hearts(float[] points, BodyPlan plan, float max) {
		float total = 0.0F;
		for (int i = 0; i < points.length; i++) {
			total += heart(points[i], plan.share(i) * max);
		}
		return total;
	}

	static float sum(float[] points) {
		float total = 0.0F;
		for (float point : points) {
			total += point;
		}
		return total;
	}

	/**
	 * (0.14.1) Whether these parts kill: head or body at zero, except a zombie or skeleton whose body gave out and that
	 * fights on ({@link #UNDEAD_FIGHT_ON}, rolled once; then only its head counts).
	 */
	static boolean lethal(LivingEntity entity, float[] points, BodyPlan plan) {
		if (!vitalGone(points, plan)) {
			return false;
		}
		int head = plan.index(BodyPart.HEAD);
		if (head >= 0 && Dismember.headless(entity) && entity.getType().builtInRegistryHolder().is(Dismember.HEADLESS_UNDEAD)) {
			// (0.14.2) it never needed its head (Dismember): only its body counts
			int body = plan.index(BodyPart.BODY);
			return body >= 0 && points[body] <= EPS;
		}
		if (head < 0 || points[head] <= EPS || !(entity instanceof Zombie || entity.getType().builtInRegistryHolder().is(EntityTypeTags.SKELETONS))) {
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

	/** Head or body at zero. */
	static boolean vitalGone(float[] points, BodyPlan plan) {
		for (int i = 0; i < points.length; i++) {
			if (plan.part(i).vital() && points[i] <= EPS) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Makes the parts' hearts ({@link #hearts}) add up to {@code health}: a gain goes to the most hurt part first, filling it
	 * before the next; a loss nobody routed comes off the limbs, then the body, then the head, and never takes head or body
	 * to zero. (0.14.2) A gain fills each part only up to its fraction in {@code caps} ({@link Breaks#caps}: a broken part
	 * to {@link Breaks#BROKEN_CAP}, a severed one not at all; null: no caps). Returns the gain nothing could take.
	 * (0.15.94) Worked on the hearts: a gain brings a part back up to the bruise line, never into it (bruises mend on their
	 * own, {@link #mendBruises}); a part still bruised keeps its bruise.
	 */
	static float reconcile(float[] points, BodyPlan plan, float max, float health, float @Nullable [] caps) {
		float[] hearts = new float[points.length];
		float[] most = new float[points.length];
		float[] floor = new float[points.length];
		for (int i = 0; i < points.length; i++) {
			float cap = plan.share(i) * max;
			hearts[i] = heart(points[i], cap);
			most[i] = heart(caps == null ? cap : cap * caps[i], cap);
			floor[i] = plan.part(i).vital() ? heart(MIN_VITAL_UNROUTED * cap, cap) : 0.0F;
		}
		float refused = reconcileHearts(hearts, plan, max, health, most, floor);
		for (int i = 0; i < points.length; i++) {
			points[i] = unheart(hearts[i], plan.share(i) * max, points[i]);
		}
		return refused;
	}

	/** (0.15.94) {@link #reconcile}'s arithmetic, on each part's hearts ({@code most}: as far as each may heal; {@code floor}: what an unrouted loss leaves). */
	private static float reconcileHearts(float[] points, BodyPlan plan, float max, float health, float[] mostOf, float[] floorOf) {
		float diff = Mth.clamp(health, 0.0F, max) - sum(points);
		if (diff > EPS) {
			float gain = diff;
			while (gain > EPS) {
				int worst = -1;
				float lowest = Float.MAX_VALUE;
				for (int i = 0; i < points.length; i++) {
					float cap = plan.share(i) * max;
					if (cap > 0.0F && points[i] < mostOf[i] - EPS && points[i] / cap < lowest) {
						lowest = points[i] / cap;
						worst = i;
					}
				}
				if (worst < 0) {
					return gain;
				}
				float add = Math.min(gain, mostOf[worst] - points[worst]);
				points[worst] += add;
				gain -= add;
			}
		} else if (diff < -EPS) {
			float loss = -diff;
			// limbs first, then the body, then the head
			for (int pass = 0; pass < 3 && loss > EPS; pass++) {
				for (int i = 0; i < points.length && loss > EPS; i++) {
					BodyPart part = plan.part(i);
					boolean mine = pass == 0 ? !part.vital() : pass == 1 ? part == BodyPart.BODY : part == BodyPart.HEAD;
					if (!mine) {
						continue;
					}
					float take = Math.min(loss, Math.max(0.0F, points[i] - floorOf[i]));
					points[i] -= take;
					loss -= take;
				}
			}
			// health below the floors: the parts still have to add up (this never kills, only hits and wounds do)
			for (int i = 0; i < points.length && loss > EPS; i++) {
				if (plan.part(i).vital()) {
					float take = Math.min(loss, points[i]);
					points[i] -= take;
					loss -= take;
				}
			}
		}
		return 0.0F;
	}

	/**
	 * Head and body hold at least {@link #MIN_VITAL_AFTER_SAVE} of themselves, the rest taken from the other parts (the
	 * largest first). (0.15.94) Moved as hearts, so the hearts still add up to the health.
	 */
	private static void ensureVital(float[] points, BodyPlan plan, float max) {
		float[] hearts = new float[points.length];
		for (int i = 0; i < points.length; i++) {
			hearts[i] = heart(points[i], plan.share(i) * max);
		}
		ensureVitalHearts(hearts, plan, max);
		for (int i = 0; i < points.length; i++) {
			points[i] = unheart(hearts[i], plan.share(i) * max, points[i]);
		}
	}

	private static void ensureVitalHearts(float[] points, BodyPlan plan, float max) {
		for (int i = 0; i < points.length; i++) {
			if (!plan.part(i).vital()) {
				continue;
			}
			float want = heart(MIN_VITAL_AFTER_SAVE * plan.share(i) * max, plan.share(i) * max);
			float need = want - points[i];
			while (need > EPS) {
				int donor = -1;
				float most = EPS;
				for (int j = 0; j < points.length; j++) {
					float spare = points[j] - (plan.part(j).vital() ? heart(MIN_VITAL_AFTER_SAVE * plan.share(j) * max, plan.share(j) * max) : 0.0F);
					if (j != i && spare > most) {
						most = spare;
						donor = j;
					}
				}
				if (donor < 0) {
					break;
				}
				float move = Math.min(need, most);
				points[donor] -= move;
				points[i] += move;
				need -= move;
			}
		}
	}
}
