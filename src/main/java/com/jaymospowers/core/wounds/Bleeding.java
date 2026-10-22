package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Dismember;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * Wounds, phase 4 (0.14.4): open cuts, and the festering that comes of dirty ones. Andy (2026-10-20): "I dont want any
 * modern medicine".
 * <ul>
 * <li><b>Bleeding</b> ({@link #BLEEDING}): a blade's blow, a shot or a zombie's claws that takes at least
 * {@link #BLEED_FROM} from a part opens a cut there. A cut loses {@link #BLEED_DAMAGE} every {@link #BLEED_TICKS} (through
 * {@link Wounds#hurtPart}, the {@code jaymospowers:bleeding} damage) and closes by itself after {@link #BLEED_STOP_TICKS};
 * while open, its part never heals past {@link #CUT_CAP}. A severed stump bleeds {@link #STUMP_BLEED} and never stops by
 * itself: only cautery ({@link Treatment}) closes it. Bosses and the bloodless ({@link BladeBlood#BLOODLESS}) never
 * bleed.</li>
 * <li><b>Festering</b> ({@link #FESTERING}): a dirty cut (a zombie's, or one dressed with a rag: {@link #DIRTY}) rolls
 * {@link #FESTER_CHANCE} every bleed tick while it bleeds or is dressed. A festering part never heals above where it is;
 * its owner has a fever (Weakness, {@link #FEVER_DAMAGE} to the body every {@link #FEVER_TICKS}). Cured by spirits or
 * honey poured on it. A limb left festering {@link #FESTER_LIMB_TICKS} is lost to save the body.</li>
 * <li><b>Dressings</b> ({@link #BANDAGED}): a bandage or rag heals its part a little at a time
 * ({@link Treatment}).</li>
 * </ul>
 * Kingdoms' villagers bandage the downed they tend: the scoreboard tag {@link #BANDAGED_TAG} stops that body's bleeding
 * and gives it a bandage's heal.
 */
public final class Bleeding {
	/** (0.14.4) A cut opens on a part that a blade, a shot or claws took at least this much from (health points). */
	public static final float BLEED_FROM = 2.0F;
	/**
	 * (0.15.4) Sharpness (Just-In COMBAT's idea: a sharpened weapon makes its target bleed): any weapon with it cuts, and
	 * each level lowers {@link #BLEED_FROM} by this much, to no less than {@link #SHARP_BLEED_FROM_MIN}.
	 */
	public static final float SHARPNESS_BLEED_FROM = 0.3F, SHARP_BLEED_FROM_MIN = 0.5F;
	/** (0.14.4) A cut's loss every {@link #BLEED_TICKS}, and a severed stump's. */
	public static final float BLEED_DAMAGE = 0.5F, STUMP_BLEED = 1.0F;
	/** (0.14.4) How often the bleeding bleeds (ticks), and how long a cut stays open by itself. */
	public static final int BLEED_TICKS = 80, BLEED_STOP_TICKS = 600;
	/** (0.14.4) An open cut's part heals no further than this fraction (stitching or a bandage closes it). */
	public static final float CUT_CAP = 0.5F;
	/** (0.14.4) A dirty cut's chance to fester, each bleed tick. */
	public static final float FESTER_CHANCE = 0.05F;
	/**
	 * (0.14.4) A fever: this often (ticks), this much off the body. (0.14.5) Every minute, was every 20 s: a festering
	 * body can't heal, so a fever every 20 s downed a player from a single dirty cut on the chest within a minute or two.
	 */
	public static final int FEVER_TICKS = 1200;
	public static final float FEVER_DAMAGE = 1.0F;
	/** (0.14.4) A limb festering this long (a Minecraft day) is lost to save the body. */
	public static final int FESTER_LIMB_TICKS = 24000;
	/** (0.14.4) A part cleaned with spirits or honey can't turn dirty for this long (ticks). */
	public static final int CLEAN_TICKS = 6000;
	/** (0.14.4) Dressings heal this often (ticks). */
	static final int DRESS_STEP = 20;
	/** (0.14.4) Put on a body by Kingdoms' tending villagers: its bleeding stops, with a bandage's heal. */
	public static final String BANDAGED_TAG = "jaymospowers_bandaged";
	public static final ResourceKey<DamageType> BLEEDING_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("bleeding"));

	/** (0.14.4) The bleeding parts (a bit per {@link BodyPart#ordinal()}; one that's severed is a stump). Saved, synced to its owner, not kept through death. */
	public static final AttachmentType<Integer> BLEEDING = AttachmentRegistry.create(JaymosPowers.id("wounds_bleeding"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** (0.14.4) The festering parts. Saved, synced to the owner (the fever's shimmer). */
	public static final AttachmentType<Integer> FESTERING = AttachmentRegistry.create(JaymosPowers.id("wounds_festering"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** (0.14.4) When each part began to fester (game time, by {@link BodyPart#ordinal()}; 0: not). Saved, server side. */
	public static final AttachmentType<List<Long>> FESTER_SINCE = AttachmentRegistry.create(JaymosPowers.id("wounds_fester_since"),
		builder -> builder.persistent(Codec.LONG.listOf()));
	/** (0.14.4) Dirty cuts (a zombie's, a rag's dressing): they may fester. Saved, server side. */
	public static final AttachmentType<Integer> DIRTY = AttachmentRegistry.create(JaymosPowers.id("wounds_dirty"),
		builder -> builder.persistent(Codec.INT));
	/** (0.14.4) Parts with a dressing on (healing). Saved, server side. */
	public static final AttachmentType<Integer> BANDAGED = AttachmentRegistry.create(JaymosPowers.id("wounds_bandaged"),
		builder -> builder.persistent(Codec.INT));
	/**
	 * (0.15.90) The parts wearing a dressing anyone can see (a bit per {@link BodyPart#ordinal()}), and, shifted up
	 * {@link #RAG_SHIFT}, which of those are rags. Put on with a dressing; it stays on after the dressing's heal is spent,
	 * until the part is whole, not bleeding and not festering (or it's gone). Saved, synced to everyone, not kept through
	 * death.
	 */
	public static final AttachmentType<Integer> WRAPPED = AttachmentRegistry.create(JaymosPowers.id("wounds_wrapped"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	public static final int RAG_SHIFT = 16;

	private static final int PARTS = BodyPart.values().length;
	/** Everyone bleeding, festering, dressed or dirty. Not saved: a reload finds them again. */
	private static final Set<LivingEntity> TRACKED = new HashSet<>();
	/** When each open cut opened (game time, by ordinal). Not saved: after a reload a cut counts from then. */
	private static final Map<LivingEntity, long[]> CUT_SINCE = new WeakHashMap<>();
	/** A dressing's heal left, and its heal per {@link #DRESS_STEP}, by ordinal. Not saved: a reload ends it. */
	private static final Map<LivingEntity, float[][]> DRESSING = new WeakHashMap<>();
	/** Until when each part stays clean (game time, by ordinal). Not saved. */
	private static final Map<LivingEntity, long[]> CLEANED = new WeakHashMap<>();

	private Bleeding() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && (bleeding(living) | festering(living) | bandaged(living) | dirty(living) | wrapped(living)) != 0) {
				TRACKED.add(living);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Bleeding::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TRACKED.clear();
			CUT_SINCE.clear();
			DRESSING.clear();
			CLEANED.clear();
		});
	}

	// ---- queries ----------------------------------------------------------------------------------------------------------

	public static int bleeding(Entity entity) {
		return entity.getAttachedOrElse(BLEEDING, 0);
	}

	public static int festering(Entity entity) {
		return entity.getAttachedOrElse(FESTERING, 0);
	}

	public static int bandaged(Entity entity) {
		return entity.getAttachedOrElse(BANDAGED, 0);
	}

	public static int dirty(Entity entity) {
		return entity.getAttachedOrElse(DIRTY, 0);
	}

	/** (0.15.90) The parts wearing a dressing you can see (the low bits), and which are rags (above {@link #RAG_SHIFT}). */
	public static int wrapped(Entity entity) {
		return entity.getAttachedOrElse(WRAPPED, 0);
	}

	/** The bleeding parts that are stumps (severed): only cautery closes them. */
	static int stumps(LivingEntity entity) {
		int mask = bleeding(entity);
		int stumps = 0;
		for (BodyPart part : BodyPart.values()) {
			if ((mask & Breaks.bit(part)) != 0 && Breaks.missing(entity, part)) {
				stumps |= Breaks.bit(part);
			}
		}
		return stumps;
	}

	/** Whether {@code entity} can bleed at all: a body Wounds parts, with blood in it, not a creative player. */
	static boolean bleeds(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		return plan != null && entity.isAlive() && !entity.getType().builtInRegistryHolder().is(BladeBlood.BLOODLESS)
			&& !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
	}

	private static void set(LivingEntity entity, AttachmentType<Integer> type, int mask) {
		if (mask == 0) {
			if (entity.hasAttached(type)) {
				entity.removeAttached(type);
			}
		} else if (entity.getAttachedOrElse(type, 0) != mask) {
			entity.setAttached(type, mask);
		}
	}

	private static boolean clean(LivingEntity entity, int ordinal, long now) {
		long[] until = CLEANED.get(entity);
		return until != null && until[ordinal] > now;
	}

	// ---- opening and closing ----------------------------------------------------------------------------------------------

	/** (0.14.4) A routed hit ({@link Wounds}): a blade's, a shot's or a zombie's claws that took enough from the part it landed on opens a cut there. */
	static void onHit(LivingEntity victim, BodyPlan plan, DamageSource source, @Nullable BodyPart landed, float[] was, float[] now) {
		if (landed == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		int index = plan.index(landed);
		ItemStack weapon = source.getWeaponItem();
		int sharpness = weapon == null || !(victim.level() instanceof ServerLevel level) ? 0
			: EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), weapon);
		float bleedFrom = sharpness > 0 ? Math.max(SHARP_BLEED_FROM_MIN, BLEED_FROM - SHARPNESS_BLEED_FROM * sharpness) : BLEED_FROM;
		if (index < 0 || was[index] - now[index] < bleedFrom) {
			return;
		}
		Entity direct = source.getDirectEntity();
		Entity by = source.getEntity();
		boolean blade = weapon != null && (Dismember.blade(weapon) || sharpness > 0);
		boolean claws = by instanceof Zombie && direct == by;
		if (!blade && !claws && !(direct instanceof Projectile)) {
			return;
		}
		open(victim, landed, by instanceof Zombie);
	}

	/** (0.14.4) Opens a cut on {@code part} ({@code dirty}: it may fester). A severed part bleeds as a stump ({@link #stump}) instead. */
	public static void open(LivingEntity entity, BodyPart part, boolean dirty) {
		if (!bleeds(entity) || !(entity.level() instanceof ServerLevel level) || BodyPlan.of(entity).index(part) < 0 || Breaks.missing(entity, part)) {
			return;
		}
		int bit = Breaks.bit(part);
		int mask = bleeding(entity);
		long now = level.getGameTime();
		CUT_SINCE.computeIfAbsent(entity, e -> new long[PARTS])[part.ordinal()] = now;
		if (dirty && !clean(entity, part.ordinal(), now)) {
			set(entity, DIRTY, dirty(entity) | bit);
		}
		TRACKED.add(entity);
		if ((mask & bit) != 0) {
			return;
		}
		set(entity, BLEEDING, mask | bit);
		if (entity instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.bleeding", part.displayName()).withStyle(ChatFormatting.RED));
		}
	}

	/** (0.14.4) A part just severed ({@link Dismember}): its stump bleeds until it's cauterised. Not the head (that's death). */
	public static void stump(LivingEntity entity, BodyPart part) {
		if (part.vital() || !bleeds(entity) || BodyPlan.of(entity).index(part) < 0) {
			return;
		}
		set(entity, BLEEDING, bleeding(entity) | Breaks.bit(part));
		TRACKED.add(entity);
	}

	/** Stops {@code part}'s bleeding; a stump only if {@code stumps}. True if it was bleeding and has stopped. */
	static boolean stop(LivingEntity entity, BodyPart part, boolean stumps) {
		int bit = Breaks.bit(part);
		int mask = bleeding(entity);
		if ((mask & bit) == 0 || !stumps && Breaks.missing(entity, part)) {
			return false;
		}
		set(entity, BLEEDING, mask & ~bit);
		long[] since = CUT_SINCE.get(entity);
		if (since != null) {
			since[part.ordinal()] = 0L;
		}
		return true;
	}

	/** Stops every part's bleeding ({@code stumps} too, or not). True if any stopped. */
	static boolean stopAll(LivingEntity entity, boolean stumps) {
		boolean any = false;
		for (BodyPart part : BodyPart.values()) {
			any |= stop(entity, part, stumps);
		}
		return any;
	}

	/**
	 * (0.14.4) A dressing on {@code part}: {@code heal} health points over {@code ticks}, the part dressed ({@link #BANDAGED})
	 * till then; {@code dirty} (a rag): it may fester while it's on.
	 */
	static void dress(LivingEntity entity, BodyPart part, float heal, int ticks, boolean dirty) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		int i = part.ordinal();
		float[][] dressing = DRESSING.computeIfAbsent(entity, e -> new float[2][PARTS]);
		dressing[0][i] = heal;
		dressing[1][i] = heal * DRESS_STEP / Math.max(DRESS_STEP, ticks);
		set(entity, BANDAGED, bandaged(entity) | Breaks.bit(part));
		// (0.15.90) and it shows: linen, or a rag
		int bit = Breaks.bit(part);
		set(entity, WRAPPED, wrapped(entity) & ~(bit << RAG_SHIFT) | bit | (dirty ? bit << RAG_SHIFT : 0));
		long now = level.getGameTime();
		if (dirty && !clean(entity, i, now)) {
			set(entity, DIRTY, dirty(entity) | Breaks.bit(part));
		} else if (!dirty) {
			// (clean linen: what was dirty in the cut is bound out of it)
			set(entity, DIRTY, dirty(entity) & ~Breaks.bit(part));
		}
		TRACKED.add(entity);
	}

	/** Whether {@code part} has a dressing still healing it. */
	static boolean dressed(LivingEntity entity, BodyPart part) {
		float[][] dressing = DRESSING.get(entity);
		return dressing != null && dressing[0][part.ordinal()] > 0.0F && (bandaged(entity) & Breaks.bit(part)) != 0;
	}

	/**
	 * (0.14.4) Spirits or honey poured on {@code part}: festering cured, the dirt washed out, and clean for
	 * {@link #CLEAN_TICKS}. True if there was festering or dirt to clean.
	 */
	static boolean clean(LivingEntity entity, BodyPart part) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return false;
		}
		int bit = Breaks.bit(part);
		boolean any = ((festering(entity) | dirty(entity)) & bit) != 0;
		unfester(entity, part);
		set(entity, DIRTY, dirty(entity) & ~bit);
		CLEANED.computeIfAbsent(entity, e -> new long[PARTS])[part.ordinal()] = level.getGameTime() + CLEAN_TICKS;
		return any;
	}

	private static void fester(LivingEntity entity, BodyPart part, long now) {
		int bit = Breaks.bit(part);
		set(entity, FESTERING, festering(entity) | bit);
		set(entity, DIRTY, dirty(entity) & ~bit);
		List<Long> since = new ArrayList<>(entity.getAttachedOrElse(FESTER_SINCE, List.of()));
		while (since.size() < PARTS) {
			since.add(0L);
		}
		since.set(part.ordinal(), now);
		entity.setAttached(FESTER_SINCE, List.copyOf(since));
		if (entity instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.festering", part.displayName()).withStyle(ChatFormatting.DARK_GREEN));
		}
	}

	private static void unfester(LivingEntity entity, BodyPart part) {
		int mask = festering(entity) & ~Breaks.bit(part);
		set(entity, FESTERING, mask);
		if (mask == 0) {
			entity.removeAttached(FESTER_SINCE);
		} else {
			List<Long> since = entity.getAttached(FESTER_SINCE);
			if (since != null && since.size() > part.ordinal() && since.get(part.ordinal()) != 0L) {
				List<Long> left = new ArrayList<>(since);
				left.set(part.ordinal(), 0L);
				entity.setAttached(FESTER_SINCE, List.copyOf(left));
			}
		}
	}

	/** (0.14.4) Parts grown back (Dismember's regrowth, as {@code mask} bits): no stump, nothing festering there. */
	static void regrown(LivingEntity entity, int mask) {
		set(entity, BLEEDING, bleeding(entity) & ~mask);
		set(entity, WRAPPED, wrapped(entity) & ~(mask | mask << RAG_SHIFT));
		for (BodyPart part : BodyPart.values()) {
			if ((mask & Breaks.bit(part)) != 0 && (festering(entity) & Breaks.bit(part)) != 0) {
				unfester(entity, part);
			}
		}
	}

	/** Every cut, fever, dressing and dirt gone (full healing, a debug command). */
	static void clearAll(LivingEntity entity) {
		entity.removeAttached(BLEEDING);
		entity.removeAttached(FESTERING);
		entity.removeAttached(FESTER_SINCE);
		entity.removeAttached(DIRTY);
		entity.removeAttached(BANDAGED);
		entity.removeAttached(WRAPPED);
		CUT_SINCE.remove(entity);
		DRESSING.remove(entity);
	}

	/** (0.14.4) Kingdoms' villagers bandaged this body ({@link #BANDAGED_TAG}): its bleeding stops (stumps bound tight too) and the worst part gets a bandage's heal. */
	static void tended(LivingEntity entity) {
		if (!entity.entityTags().contains(BANDAGED_TAG)) {
			return;
		}
		entity.removeTag(BANDAGED_TAG);
		stopAll(entity, true);
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null) {
			return;
		}
		BodyPart worst = plan.part(0);
		float lowest = Float.MAX_VALUE;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			float fraction = Wounds.fraction(entity, part);
			if (!Breaks.missing(entity, part) && fraction < lowest) {
				lowest = fraction;
				worst = part;
			}
		}
		dress(entity, worst, Treatment.BANDAGE_HEAL, Treatment.BANDAGE_TICKS, false);
	}

	// ---- every tick -------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (TRACKED.isEmpty()) {
			return;
		}
		int t = server.getTickCount();
		boolean bleedTick = t % BLEED_TICKS == 0, dressTick = t % DRESS_STEP == 0, feverTick = t % FEVER_TICKS == 0;
		if (!bleedTick && !dressTick && !feverTick) {
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
			if (dressTick) {
				tended(body);
				dress(body);
				unwrap(body);
			}
			if (bleedTick && body.isAlive()) {
				bleed(level, body);
			}
			if (feverTick && body.isAlive()) {
				fever(level, body);
			}
			if ((bleeding(body) | festering(body) | bandaged(body) | dirty(body) | wrapped(body)) == 0) {
				TRACKED.remove(body);
				CUT_SINCE.remove(body);
				DRESSING.remove(body);
			}
		}
	}

	private static void dress(LivingEntity body) {
		int mask = bandaged(body);
		if (mask == 0) {
			return;
		}
		float[][] dressing = DRESSING.get(body);
		for (BodyPart part : BodyPart.values()) {
			int bit = Breaks.bit(part);
			if ((mask & bit) == 0) {
				continue;
			}
			int i = part.ordinal();
			if (dressing == null || dressing[0][i] <= 0.0F) {
				// (done, or lost to a reload)
				mask &= ~bit;
				continue;
			}
			float step = Math.min(dressing[0][i], dressing[1][i]);
			dressing[0][i] -= step;
			Wounds.healPart(body, part, step);
			if (dressing[0][i] <= 0.0F) {
				mask &= ~bit;
			}
		}
		set(body, BANDAGED, mask);
	}

	/** (0.15.90) A dressing comes off once its part is whole and clean (no cut, no festering), or the part is gone. */
	private static void unwrap(LivingEntity body) {
		int mask = wrapped(body);
		if (mask == 0) {
			return;
		}
		int open = bleeding(body) | festering(body) | bandaged(body);
		for (BodyPart part : BodyPart.values()) {
			int bit = Breaks.bit(part);
			if ((mask & bit) != 0 && (Breaks.missing(body, part) || (open & bit) == 0 && Wounds.fraction(body, part) >= 0.999F)) {
				mask &= ~(bit | bit << RAG_SHIFT);
			}
		}
		set(body, WRAPPED, mask);
	}

	private static void bleed(ServerLevel level, LivingEntity body) {
		long now = level.getGameTime();
		int mask = bleeding(body);
		int bandaged = bandaged(body);
		// a dirty cut, while it's open or dressed, may fester
		int dirty = dirty(body) & (mask | bandaged);
		if (dirty != dirty(body)) {
			set(body, DIRTY, dirty);
		}
		for (BodyPart part : BodyPart.values()) {
			int bit = Breaks.bit(part);
			if ((dirty & bit) != 0 && (festering(body) & bit) == 0 && !clean(body, part.ordinal(), now)
				&& body.getRandom().nextFloat() < FESTER_CHANCE) {
				fester(body, part, now);
			}
		}
		if (mask == 0 || Downed.isDowned(body)) {
			// (the downed bleed out by Downed's own clock)
			return;
		}
		long[] since = CUT_SINCE.computeIfAbsent(body, e -> new long[PARTS]);
		BodyPlan plan = BodyPlan.of(body);
		for (BodyPart part : BodyPart.values()) {
			int bit = Breaks.bit(part);
			if ((mask & bit) == 0) {
				continue;
			}
			if (plan == null || plan.index(part) < 0) {
				mask &= ~bit;
				continue;
			}
			boolean stump = Breaks.missing(body, part);
			int i = part.ordinal();
			if (since[i] == 0L) {
				since[i] = now;
			}
			if (!stump && now - since[i] >= BLEED_STOP_TICKS) {
				// it has closed by itself
				mask &= ~bit;
				since[i] = 0L;
				continue;
			}
			drip(level, body, part, stump);
		}
		set(body, BLEEDING, mask);
		if (body instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return;
		}
		for (BodyPart part : BodyPart.values()) {
			if ((mask & Breaks.bit(part)) == 0 || !body.isAlive() || Downed.isDowned(body)) {
				continue;
			}
			Wounds.hurtPart(body, part, Breaks.missing(body, part) ? STUMP_BLEED : BLEED_DAMAGE, null, BLEEDING_DAMAGE);
		}
	}

	/** Blood running from {@code part}: (0.15.93) drops that pool on the ground under it ({@link BladeBlood#drip}). */
	private static void drip(ServerLevel level, LivingEntity body, BodyPart part, boolean stump) {
		double y = switch (part) {
			case HEAD -> 0.85;
			case BODY, LEFT_ARM, RIGHT_ARM, WINGS -> 0.6;
			case LEFT_FOOT, RIGHT_FOOT -> 0.08;
			default -> 0.3;
		};
		BladeBlood.drip(level, body, y, stump);
	}

	private static void fever(ServerLevel level, LivingEntity body) {
		int mask = festering(body);
		if (mask == 0) {
			return;
		}
		long now = level.getGameTime();
		body.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FEVER_TICKS + 40, 0, false, false, true));
		if (!(body instanceof Player player && (player.isCreative() || player.isSpectator())) && !Downed.isDowned(body)) {
			Wounds.hurtPart(body, BodyPart.BODY, FEVER_DAMAGE, null);
		}
		if (!body.isAlive()) {
			return;
		}
		List<Long> since = body.getAttachedOrElse(FESTER_SINCE, List.of());
		for (BodyPart part : BodyPart.values()) {
			int i = part.ordinal();
			if ((mask & Breaks.bit(part)) == 0 || part.vital()) {
				continue;
			}
			long from = i < since.size() ? since.get(i) : 0L;
			if (from == 0L) {
				// (lost to an old save: count from now)
				fester(body, part, now);
				continue;
			}
			if (now - from >= FESTER_LIMB_TICKS) {
				rot(body, part);
			}
		}
	}

	/** (0.14.4) A limb festered too long: it's lost to save the body (a person's comes off; any other creature's is broken for good). */
	private static void rot(LivingEntity body, BodyPart part) {
		unfester(body, part);
		int limb = Breaks.limb(part);
		if (limb != 0 && limb != Dismember.HEAD && BodyPlan.of(body) == BodyPlan.HUMANOID) {
			Dismember.sever(body, limb, null);
			// (taken off clean: the rot's gone with it, the stump seared)
			BodyPart lost = Breaks.part(limb);
			if (lost != null) {
				stop(body, lost, true);
				unfester(body, lost);
			}
		} else {
			Wounds.breakPart(body, part, null);
		}
		if (body instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.rotted", part.displayName()).withStyle(ChatFormatting.DARK_RED));
		}
	}
}
