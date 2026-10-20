package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Wounds, phase 1: every living thing's health is spread over the parts of its {@link BodyPlan}. Health stays real
 * (vanilla {@code getHealth()} is the truth); the {@link #PARTS} attachment is how it is distributed, as each part's
 * fraction (0-1) of its own share of the most health. A hit takes its loss from the part it lands on
 * ({@link HitRouting}), spilling into the next when a part runs out; head or body at zero kills. Healing of any kind
 * fills the most hurt part first. No debuffs and no downed state yet.
 */
public final class Wounds {
	/** Smaller than this many health points is nothing. */
	static final float EPS = 1.0E-4F;
	/** A death something cancelled (Kingdoms downs its folk, Xenoform reverts): head and body get at least this fraction back. */
	public static final float MIN_VITAL_AFTER_SAVE = 0.1F;
	/** A health drop nobody routed (some other mod's setHealth) never takes head or body below this fraction: it never kills. */
	public static final float MIN_VITAL_UNROUTED = 0.05F;
	/** Players have their parts brought in line with their health this often (ticks), so the HUD follows healing. */
	public static final int PLAYER_RECONCILE_TICKS = 10;
	/** A fraction this full counts as whole. */
	private static final float WHOLE = 0.999F;
	public static final ResourceKey<DamageType> WOUNDS_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("wounds"));

	/** Each part's fraction of its share of the most health, in the order of the creature's {@link BodyPlan}. Saved, synced to the creature itself; absent when every part is whole. Not kept through death. */
	public static final AttachmentType<List<Float>> PARTS = AttachmentRegistry.create(JaymosPowers.id("wounds"),
		builder -> builder.persistent(Codec.FLOAT.listOf())
			.syncWith(ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** Creatures whose wounds are killing them just now: our own lethal blow doesn't route again. */
	private static final Set<LivingEntity> FINISHING = Collections.newSetFromMap(new WeakHashMap<>());
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
		reconcile(points, plan, max, entity.getHealth());
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
		if (plan == null || !(entity.level() instanceof ServerLevel level) || !entity.isAlive() || amount <= 0.0F || kingdomsDowned(entity)) {
			return;
		}
		float max = entity.getMaxHealth();
		float health = entity.getHealth();
		float[] points = load(entity, plan);
		reconcile(points, plan, max, health);
		float before = sum(points);
		HitRouting.damage(points, plan, part, amount);
		float removed = before - sum(points);
		// (not to zero: a dead creature has to die through its events)
		entity.setHealth(Math.max(0.01F, health - removed));
		store(entity, plan, points);
		if (vitalGone(points, plan)) {
			finish(level, entity, plan, by);
		}
	}

	/** Full health, every part whole. */
	public static void healAll(LivingEntity entity) {
		entity.setHealth(entity.getMaxHealth());
		entity.removeAttached(PARTS);
	}

	/** Brings the parts in line with the health (healing fills the most hurt part, an unrouted loss comes off the limbs first). */
	public static void reconcile(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		if (entity.getAttached(PARTS) == null && entity.getHealth() >= entity.getMaxHealth()) {
			return;
		}
		float[] points = load(entity, plan);
		reconcile(points, plan, entity.getMaxHealth(), entity.getHealth());
		store(entity, plan, points);
	}

	private static boolean downedLooked;
	private static @Nullable AttachmentType<?> kingdomsDowned;

	/** Kingdoms' downed guards bleed out on their own health: Wounds leaves them alone (looked up by name; Kingdoms is optional). */
	private static boolean kingdomsDowned(LivingEntity entity) {
		if (!downedLooked) {
			downedLooked = true;
			try {
				kingdomsDowned = (AttachmentType<?>) Class.forName("com.jaymoskingdoms.Downed").getField("DOWNED").get(null);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				kingdomsDowned = null;
			}
		}
		return kingdomsDowned != null && entity.getAttached(kingdomsDowned) != null;
	}

	// ---- hits -------------------------------------------------------------------------------------------------------------

	private static void onDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
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
		// what the parts held before the hit, then the real loss (armour, absorption and other mods' changes already in it)
		reconcile(points, plan, max, Math.min(max, health + taken));
		float loss = sum(points) - health;
		if (loss > EPS) {
			HitRouting.route(victim, plan, source, points, max, loss);
		} else {
			reconcile(points, plan, max, health);
		}
		store(victim, plan, points);
		if (vitalGone(points, plan)) {
			finish(level, victim, plan, source.getEntity());
		}
	}

	/** Head or body is gone: the creature dies of its wounds, through its death events (so Kingdoms still downs its folk). */
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
			reconcile(points, plan, max, victim.getHealth());
			ensureVital(points, plan, max);
			store(victim, plan, points);
		}
	}

	// ---- the parts' arithmetic --------------------------------------------------------------------------------------------

	/** The parts in health points. A creature with no attachment is whole. */
	static float[] load(LivingEntity entity, BodyPlan plan) {
		float max = entity.getMaxHealth();
		List<Float> fractions = entity.getAttached(PARTS);
		float[] points = new float[plan.size()];
		for (int i = 0; i < points.length; i++) {
			float fraction = fractions != null && fractions.size() == points.length ? Mth.clamp(fractions.get(i), 0.0F, 1.0F) : 1.0F;
			points[i] = fraction * plan.share(i) * max;
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
	 */
	static void reconcile(float[] points, BodyPlan plan, float max, float health) {
		float diff = Mth.clamp(health, 0.0F, max) - sum(points);
		if (diff > EPS) {
			float gain = diff;
			while (gain > EPS) {
				int worst = -1;
				float lowest = Float.MAX_VALUE;
				for (int i = 0; i < points.length; i++) {
					float cap = plan.share(i) * max;
					if (cap > 0.0F && points[i] < cap - EPS && points[i] / cap < lowest) {
						lowest = points[i] / cap;
						worst = i;
					}
				}
				if (worst < 0) {
					return;
				}
				float add = Math.min(gain, plan.share(worst) * max - points[worst]);
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
		}
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
