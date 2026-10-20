package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
	private static int tickCounter;

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
				}
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> WoundsCommand.register(dispatcher));
		// (0.14.1) phase 2: the downed
		Downed.register();
		// (0.14.2) phase 3: broken limbs
		Breaks.register();
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
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || !(entity.level() instanceof ServerLevel level) || !entity.isAlive() || amount <= 0.0F || Downed.isDowned(entity)) {
			return;
		}
		float max = entity.getMaxHealth();
		float health = entity.getHealth();
		float[] points = load(entity, plan);
		reconcile(points, plan, max, health, Breaks.caps(entity, plan));
		// (0.14.2) health the broken parts couldn't hold isn't there to lose
		health = Math.min(health, sum(points));
		float[] was = points.clone();
		float before = sum(points);
		HitRouting.damage(points, plan, part, amount);
		float removed = before - sum(points);
		// (not to zero: a dead creature has to die through its events)
		entity.setHealth(Math.max(0.01F, health - removed));
		store(entity, plan, points);
		// (0.14.2) a limb this took to zero breaks
		markBreaks(entity, plan, was, points, by);
		if (lethal(entity, points, plan)) {
			finish(level, entity, plan, by);
		}
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
			health = Math.min(health, sum(points));
			float lost = points[index];
			points[index] = 0.0F;
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
	 * (0.14.2) A blow that killed outright: where it landed is worked out on a copy of the parts (the dead keep theirs as
	 * they were), and a blade's overkill may still take that part off, onto the corpse.
	 */
	private static void killingBlow(LivingEntity victim, DamageSource source, float taken, boolean blocked) {
		BodyPlan plan = BodyPlan.of(victim);
		if (blocked || plan == null || plan == BodyPlan.SIMPLE || victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		float max = victim.getMaxHealth();
		float health = Math.max(0.0F, victim.getHealth());
		float[] points = load(victim, plan);
		reconcile(points, plan, max, Math.min(max, health + taken), Breaks.caps(victim, plan));
		float[] was = points.clone();
		float loss = sum(points) - health;
		if (loss <= EPS) {
			return;
		}
		BodyPart aimed = Dismember.cutAim(victim);
		BodyPart landed;
		if (aimed != null && plan.index(aimed) >= 0) {
			HitRouting.damage(points, plan, aimed, loss);
			landed = aimed;
		} else {
			landed = HitRouting.route(victim, plan, source, points, max, loss);
		}
		sever(victim, plan, source, landed, was, points, loss, max, taken);
	}

	/** Full health, every part whole. */
	public static void healAll(LivingEntity entity) {
		// (0.14.2) and every break gone with it
		Breaks.clearAll(entity);
		entity.setHealth(entity.getMaxHealth());
		entity.removeAttached(PARTS);
		entity.removeAttached(PARTS_MAX);
		entity.removeTag(FIGHTS_ON_TAG);
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
			entity.setHealth(Math.max(0.01F, sum(points)));
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

	// ---- hits -------------------------------------------------------------------------------------------------------------

	private static void onDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (taken > 0.0F && !FINISHING.contains(victim) && victim.level() instanceof ServerLevel && (!victim.isAlive() || victim.getHealth() <= 0.0F)) {
			// (0.14.2) a killing blow can still take a head or a limb off, onto the corpse (as Dismember's own roll did)
			killingBlow(victim, source, taken, blocked);
			return;
		}
		if (taken <= 0.0F || FINISHING.contains(victim) || !(victim.level() instanceof ServerLevel level) || !victim.isAlive()
			|| victim.getHealth() <= 0.0F) {
			return;
		}
		BodyPlan plan = BodyPlan.of(victim);
		if (plan == null || plan == BodyPlan.SIMPLE || victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		float max = victim.getMaxHealth();
		float health = victim.getHealth();
		float[] points = load(victim, plan);
		float[] caps = Breaks.caps(victim, plan);
		if (Downed.isDowned(victim)) {
			// (0.14.1) down: the parts only follow the health (head and body kept), so only the health running out ends them
			reconcile(points, plan, max, health, caps);
			ensureVital(points, plan, max);
			store(victim, plan, points);
			return;
		}
		// what the parts held before the hit, then the real loss (armour, absorption and other mods' changes already in it)
		reconcile(points, plan, max, Math.min(max, health + taken), caps);
		float[] was = points.clone();
		float loss = sum(points) - health;
		BodyPart landed = null;
		if (loss > EPS) {
			// (0.14.2) a power's own cut (Dismember.cut) lands where it was aimed
			BodyPart aimed = Dismember.cutAim(victim);
			if (aimed != null && plan.index(aimed) >= 0) {
				HitRouting.damage(points, plan, aimed, loss);
				landed = aimed;
			} else {
				landed = HitRouting.route(victim, plan, source, points, max, loss);
			}
		} else {
			reconcile(points, plan, max, health, caps);
		}
		store(victim, plan, points);
		if (sum(points) < health - EPS && caps != null) {
			// (0.14.2) health the broken and severed parts couldn't hold (healed in since the last look) is refused
			victim.setHealth(Math.max(0.01F, sum(points)));
		}
		Entity by = source.getEntity();
		// (0.14.2) overkill: a blade on a limb already broken, or past the head's zero, may cut it off
		if (!blocked && sever(victim, plan, source, landed, was, points, loss, max, taken)
			&& (landed == BodyPart.HEAD || !victim.isAlive())) {
			// (a severed head is Dismember's to end: death, a blind stagger, or an undead fighting on)
			return;
		}
		// (0.14.2) a limb this hit took to zero breaks (a fall's breaks heal by themselves: no priest for a hard landing)
		markBreaks(victim, plan, was, points, by, source.is(DamageTypeTags.IS_FALL));
		if (victim.isAlive() && lethal(victim, points, plan)) {
			finish(level, victim, plan, by);
		}
	}

	/** Head or body is gone: the creature dies of its wounds, through its death events (so {@link Downed} can down them instead). */
	private static void finish(ServerLevel level, LivingEntity victim, BodyPlan plan, @Nullable Entity by) {
		if (FINISHING.contains(victim)) {
			return;
		}
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(WOUNDS_DAMAGE), by);
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
	 * Makes the parts add up to {@code health}: a gain goes to the most hurt part first, filling it before the next;
	 * a loss nobody routed comes off the limbs, then the body, then the head, and never takes head or body to zero.
	 * (0.14.2) A gain fills each part only up to its fraction in {@code caps} ({@link Breaks#caps}: a broken part to
	 * {@link Breaks#BROKEN_CAP}, a severed one not at all; null: no caps). Returns the gain nothing could take.
	 */
	static float reconcile(float[] points, BodyPlan plan, float max, float health, float @Nullable [] caps) {
		float diff = Mth.clamp(health, 0.0F, max) - sum(points);
		if (diff > EPS) {
			float gain = diff;
			while (gain > EPS) {
				int worst = -1;
				float lowest = Float.MAX_VALUE;
				for (int i = 0; i < points.length; i++) {
					float cap = plan.share(i) * max;
					float most = caps == null ? cap : cap * caps[i];
					if (cap > 0.0F && points[i] < most - EPS && points[i] / cap < lowest) {
						lowest = points[i] / cap;
						worst = i;
					}
				}
				if (worst < 0) {
					return gain;
				}
				float most = plan.share(worst) * max * (caps == null ? 1.0F : caps[worst]);
				float add = Math.min(gain, most - points[worst]);
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
					float floor = part.vital() ? MIN_VITAL_UNROUTED * plan.share(i) * max : 0.0F;
					float take = Math.min(loss, Math.max(0.0F, points[i] - floor));
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

	/** Head and body hold at least {@link #MIN_VITAL_AFTER_SAVE} of themselves, the rest taken from the other parts (the largest first). */
	private static void ensureVital(float[] points, BodyPlan plan, float max) {
		for (int i = 0; i < points.length; i++) {
			if (!plan.part(i).vital()) {
				continue;
			}
			float want = MIN_VITAL_AFTER_SAVE * plan.share(i) * max;
			float need = want - points[i];
			while (need > EPS) {
				int donor = -1;
				float most = EPS;
				for (int j = 0; j < points.length; j++) {
					float spare = points[j] - (plan.part(j).vital() ? MIN_VITAL_AFTER_SAVE * plan.share(j) * max : 0.0F);
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
