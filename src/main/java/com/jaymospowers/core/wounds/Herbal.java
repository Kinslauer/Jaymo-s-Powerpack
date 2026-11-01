package com.jaymospowers.core.wounds;

import com.jaymospowers.frost.Freezing;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.19.24) The herbal remedies' lasting effects, all medieval (docs/Healing-Plan.md): what a knitbone poultice, a burn
 * salve, a garlic poultice, bone broth, chamomile tea and posset leave working on a body after {@link Treatment} or a
 * drink has done the first part.
 * <ul>
 * <li><b>Knitbone:</b> the part mends a little at once and then {@link #KNIT_PER_SECOND} extra for {@link #KNIT_TICKS},
 * so a splinted break knits about twice as fast (it still heals only as far as {@link Breaks#caps} allows).</li>
 * <li><b>Burn salve:</b> fire out, frost eased, and {@link #SALVE_HEAL} healed into the part over {@link #SALVE_TICKS}.</li>
 * <li><b>Garlic:</b> cleans like honey ({@link Bleeding#clean(LivingEntity, BodyPart, int)}) and the part can't turn dirty
 * or fester for {@link #GARLIC_WARD_TICKS}.</li>
 * <li><b>Bone broth:</b> a little healing over the worst parts, and a festering fever is cooled for
 * {@link #BROTH_COOL_TICKS} ({@link #feverCooled}).</li>
 * <li><b>Chamomile tea:</b> the drinker's next full sleep heals every part ({@link #SLEEP_PART_HEAL} twice over).</li>
 * </ul>
 * Not saved (like a dressing): a reload ends them, except the calm-sleep flag, which is a scoreboard tag.
 */
public final class Herbal {
	/** (0.19.24) Knitbone: heals the part this much at once (health points), then this much more every second, for this long (ticks). */
	public static final float KNIT_NOW = 1.0F, KNIT_PER_SECOND = 0.1F;
	public static final int KNIT_TICKS = 12000;
	/** (0.19.24) Burn salve: heals the part this much (health points) over this long (ticks). */
	public static final float SALVE_HEAL = 4.0F;
	public static final int SALVE_TICKS = 400;
	/** (0.19.24) A part cleaned with garlic can't fester for this long (ticks). */
	public static final int GARLIC_WARD_TICKS = 12000;
	/** (0.19.24) Bone broth: heals this much (health points) over the worst parts, and cools a fever this long (ticks). */
	public static final float BROTH_HEAL = 3.0F;
	public static final int BROTH_COOL_TICKS = 6000;
	/** (0.19.24) Posset: heals this much (health points) over the worst parts. */
	public static final float POSSET_HEAL = 2.0F;
	/** (0.19.24) Chamomile tea: weaker than willow-bark's, this share of its length. */
	public static final float CHAMOMILE_PAIN_SHARE = 0.5F;
	/**
	 * (0.19.24) What a full sleep heals into every hurt part (health points); chamomile's calm sleep doubles it. Powers heals
	 * nothing in a plain sleep itself: a bed or cot that does (Kingdoms') asks {@link #sleepHeal}.
	 */
	public static final float SLEEP_PART_HEAL = 1.0F;
	public static final int CALM_SLEEP_MULTIPLIER = 2;
	/** (0.19.24) Self-care (a sleep) never takes a part past this fraction. */
	public static final float SLEEP_CAP = 0.6F;
	/** (0.19.24) The scoreboard tag of a player whose next sleep is calm. */
	public static final String CALM_SLEEP_TAG = "jaymospowers_calm_sleep";
	/** (0.19.24) Herbal work runs this often (ticks). */
	static final int EVERY = 20;

	private static final int PARTS = BodyPart.values().length;
	/** Everyone with a knitbone or salve working. */
	private static final Set<LivingEntity> TRACKED = new HashSet<>();
	/** Until when each part knits faster (game time, by ordinal). */
	private static final Map<LivingEntity, long[]> KNIT = new WeakHashMap<>();
	/** A salve's heal left, and its heal per {@link #EVERY}, by ordinal. */
	private static final Map<LivingEntity, float[][]> SALVE = new WeakHashMap<>();
	/** Until when a fever is cooled (game time). */
	private static final Map<LivingEntity, Long> COOLED = new WeakHashMap<>();

	private Herbal() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Herbal::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TRACKED.clear();
			KNIT.clear();
			SALVE.clear();
			COOLED.clear();
		});
	}

	// ---- knitbone ---------------------------------------------------------------------------------------------------------

	/** Whether a knitbone poultice can do anything for {@code part}: broken, still there, and not already knitting fast. */
	static boolean knittable(LivingEntity body, BodyPart part) {
		return Breaks.isBroken(body, part) && !Breaks.missing(body, part) && !knitting(body, part);
	}

	/** Whether {@code part} is knitting faster now. */
	public static boolean knitting(LivingEntity body, BodyPart part) {
		long[] until = KNIT.get(body);
		return until != null && until[part.ordinal()] > body.level().getGameTime();
	}

	/** A knitbone poultice on {@code part}: a little healing now, and faster knitting for {@link #KNIT_TICKS}. */
	static void knit(LivingEntity body, BodyPart part) {
		Wounds.healPart(body, part, KNIT_NOW);
		KNIT.computeIfAbsent(body, e -> new long[PARTS])[part.ordinal()] = body.level().getGameTime() + KNIT_TICKS;
		TRACKED.add(body);
	}

	// ---- burn salve -------------------------------------------------------------------------------------------------------

	/** Whether a burn salve can do anything for {@code part}: it is hurt, or the body is burning or frozen. */
	static boolean salvable(LivingEntity body, BodyPart part) {
		return !Breaks.missing(body, part) && (Wounds.fraction(body, part) < 0.999F || body.isOnFire() || body.getTicksFrozen() > 0);
	}

	/** A burn salve on {@code part}: the fire out, the frost eased, and the part healing over {@link #SALVE_TICKS}. */
	static void salve(LivingEntity body, BodyPart part) {
		body.clearFire();
		body.setTicksFrozen(0);
		body.removeEffect(Freezing.FREEZING);
		float[][] salve = SALVE.computeIfAbsent(body, e -> new float[2][PARTS]);
		int i = part.ordinal();
		salve[0][i] = SALVE_HEAL;
		salve[1][i] = SALVE_HEAL * EVERY / Math.max(EVERY, SALVE_TICKS);
		TRACKED.add(body);
	}

	// ---- fever and sleep --------------------------------------------------------------------------------------------------

	/** Bone broth cools a fever for {@link #BROTH_COOL_TICKS}: {@link Bleeding}'s fever neither weakens nor hurts meanwhile. */
	static void coolFever(LivingEntity body) {
		COOLED.put(body, body.level().getGameTime() + BROTH_COOL_TICKS);
		body.removeEffect(MobEffects.WEAKNESS);
	}

	/** Whether {@code body}'s fever is cooled now (server side). */
	public static boolean feverCooled(LivingEntity body) {
		Long until = COOLED.get(body);
		return until != null && until > body.level().getGameTime();
	}

	/** Bone broth drunk: a small heal over the worst parts, and the fever cooled. */
	static void broth(LivingEntity body) {
		Mending.mend(body, BROTH_HEAL);
		if (Bleeding.festering(body) != 0) {
			coolFever(body);
		}
	}

	/** Posset drunk: nausea and poison cured, a small heal. */
	static void posset(LivingEntity body) {
		body.removeEffect(MobEffects.NAUSEA);
		body.removeEffect(MobEffects.POISON);
		Mending.mend(body, POSSET_HEAL);
	}

	/** Chamomile tea drunk: calm sleep to come, and the pain eased a little ({@link Pain#tea(LivingEntity, float)}). */
	static void chamomile(LivingEntity body) {
		Pain.tea(body, CHAMOMILE_PAIN_SHARE);
		if (body instanceof ServerPlayer player) {
			player.addTag(CALM_SLEEP_TAG);
		}
	}

	/** Whether {@code player}'s next sleep is calm (chamomile). */
	public static boolean calmSleep(Player player) {
		return player.entityTags().contains(CALM_SLEEP_TAG);
	}

	/**
	 * (0.19.24) What a sleep heals into each hurt part (health points) for {@code player}: {@link #SLEEP_PART_HEAL}, doubled
	 * by a calm sleep. Call it once per sleep; it uses up the calm. For a bed or cot that heals (Kingdoms', by reflection).
	 */
	public static float sleepHeal(Player player) {
		boolean calm = player.entityTags().contains(CALM_SLEEP_TAG);
		if (calm) {
			player.removeTag(CALM_SLEEP_TAG);
		}
		return SLEEP_PART_HEAL * (calm ? CALM_SLEEP_MULTIPLIER : 1);
	}

	/** Heals {@code amount} into every hurt part of {@code body}, none past {@link #SLEEP_CAP}. True if any took it. */
	static boolean healEach(LivingEntity body, float amount) {
		BodyPlan plan = BodyPlan.of(body);
		if (plan == null) {
			return false;
		}
		boolean any = false;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (!Breaks.missing(body, part) && Wounds.fraction(body, part) < SLEEP_CAP) {
				any |= Wounds.healPart(body, part, amount) > 0.0F;
			}
		}
		return any;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		// a calm sleep, slept long enough: every part mends, once
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSleeping() && player.isSleepingLongEnough() && calmSleep(player) && player.isAlive()) {
				float heal = sleepHeal(player);
				if (healEach(player, heal)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.calm_sleep").withStyle(ChatFormatting.GREEN));
				}
			}
		}
		if (TRACKED.isEmpty()) {
			return;
		}
		for (LivingEntity body : new ArrayList<>(TRACKED)) {
			if (body.isRemoved() || !(body.level() instanceof ServerLevel level)) {
				TRACKED.remove(body);
				continue;
			}
			if (!body.isAlive()) {
				continue;
			}
			long now = level.getGameTime();
			boolean busy = false;
			long[] knit = KNIT.get(body);
			if (knit != null) {
				for (BodyPart part : BodyPart.values()) {
					int i = part.ordinal();
					if (knit[i] == 0L) {
						continue;
					}
					if (knit[i] <= now || !Breaks.isBroken(body, part)) {
						// (done, or the bone has knitted)
						knit[i] = 0L;
						continue;
					}
					busy = true;
					Wounds.healPart(body, part, KNIT_PER_SECOND * EVERY / 20.0F);
				}
			}
			float[][] salve = SALVE.get(body);
			if (salve != null) {
				for (BodyPart part : BodyPart.values()) {
					int i = part.ordinal();
					if (salve[0][i] <= 0.0F) {
						continue;
					}
					float step = Math.min(salve[0][i], salve[1][i]);
					salve[0][i] -= step;
					Wounds.healPart(body, part, step);
					busy |= salve[0][i] > 0.0F;
				}
			}
			if (!busy) {
				TRACKED.remove(body);
				KNIT.remove(body);
				SALVE.remove(body);
			} else if (now % 100 < EVERY) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY(0.6), body.getZ(), 2, 0.25, 0.3, 0.25, 0.0);
			}
		}
	}
}
