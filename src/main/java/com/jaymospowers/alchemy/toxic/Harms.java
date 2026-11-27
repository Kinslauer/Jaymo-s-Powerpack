package com.jaymospowers.alchemy.toxic;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The five harms beside Poisons' own (Hunter's Road, patch 6, "T-A5"), as effects any living thing can carry. Kingdoms'
 * poison vials, coated blades, doses and gas clouds deliver them through its PowersCompat; patch 6's oils and brews call
 * {@link #apply} directly.
 * <ul>
 * <li>{@link Harm#TOXIN}: big heart losses every two seconds ({@link #TOXIN_HURT}); it can kill.</li>
 * <li>{@link Harm#ACID}: armour weaker for a while ({@link #ACID_CUT} of it and of its toughness off, each level).</li>
 * <li>{@link Harm#FLAMMABLE}: catches fire near flame (fire, lava, a lit campfire within {@link #FLAME_REACH} blocks).</li>
 * <li>{@link Harm#CRIPPLE}: physical blows (swords, fists, arrows, falls) hurt {@link #CRIPPLE_MORE} more, each level.</li>
 * <li>{@link Harm#HUNT}: marked: wolves, beasts (the {@code jaymoskingdoms:kind/beasts} tag) and anything a
 * {@link #HUNTERS} predicate names (the beast-mutated, patch 11) hit it {@link #HUNT_MORE} harder.</li>
 * </ul>
 */
public final class Harms {
	// ---- tuning ----
	/** Toxin: ticks between hurts and the hurt (health points). */
	public static final int TOXIN_EVERY = 40;
	public static final float TOXIN_HURT = 2.5F;
	/** Acid: the share of armour and toughness taken off, per level. */
	public static final double ACID_CUT = 0.4;
	/** Flammable: ticks between looks for flame, how far (blocks) flame reaches, and how long it burns (seconds). */
	public static final int FLAME_EVERY = 10;
	public static final int FLAME_REACH = 2;
	public static final float FLAME_BURN = 6.0F;
	/** Cripple and hunt: the share of extra harm, per level. */
	public static final float CRIPPLE_MORE = 0.35F, HUNT_MORE = 0.5F;
	/** Hunt: ticks between the mark's puffs over the head. */
	public static final int MARK_EVERY = 20;

	/** The beasts tag (Kingdoms, patch 2), which may not exist: an unknown tag is simply empty. */
	private static final TagKey<EntityType<?>> BEASTS = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("jaymoskingdoms", "kind/beasts"));

	/** What else counts as a hunter of a marked one, beside wolves and beasts (patch 11 adds the beast-mutated). */
	public static final List<Predicate<LivingEntity>> HUNTERS = new ArrayList<>();

	/** The harms, by id (the id is the effect's own and the vial's key in Kingdoms). */
	public enum Harm {
		TOXIN(0x7FB020), ACID(0xB8D030), FLAMMABLE(0xE88020), CRIPPLE(0x907060), HUNT(0xB02828);

		public final int color;

		Harm(int color) {
			this.color = color;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static @Nullable Harm byId(@Nullable String id) {
			for (Harm h : values()) {
				if (h.id().equals(id)) {
					return h;
				}
			}
			return null;
		}
	}

	private static final java.util.EnumMap<Harm, Holder<MobEffect>> EFFECTS = new java.util.EnumMap<>(Harm.class);

	private Harms() {
	}

	static void init() {
		for (Harm harm : Harm.values()) {
			EFFECTS.put(harm, Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
				ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("harm_" + harm.id())), new HarmEffect(harm)));
		}
	}

	/** The effect of one harm. */
	public static Holder<MobEffect> effect(Harm harm) {
		return EFFECTS.get(harm);
	}

	/** {@code victim} takes {@code harm} for {@code ticks}, {@code amplifier} levels over the first (0 is the first); a longer one is kept. */
	public static void apply(LivingEntity victim, Harm harm, int ticks, int amplifier) {
		if (!victim.level().isClientSide() && victim.isAlive()) {
			victim.addEffect(new MobEffectInstance(effect(harm), ticks, amplifier));
		}
	}

	/** As {@link #apply(LivingEntity, Harm, int, int)} by the harm's id (for Kingdoms' reflection); false if there is no such harm. */
	public static boolean apply(LivingEntity victim, String id, int ticks, int amplifier) {
		Harm harm = Harm.byId(id);
		if (harm == null) {
			return false;
		}
		apply(victim, harm, ticks, amplifier);
		return true;
	}

	public static boolean has(LivingEntity who, Harm harm) {
		return who.hasEffect(effect(harm));
	}

	/** The level of {@code harm} on {@code who} (1 for the first), 0 for none. */
	public static int level(LivingEntity who, Harm harm) {
		MobEffectInstance i = who.getEffect(effect(harm));
		return i == null ? 0 : i.getAmplifier() + 1;
	}

	/** Takes every harm off {@code who} (the antidote). */
	public static void clear(LivingEntity who) {
		for (Harm harm : Harm.values()) {
			who.removeEffect(effect(harm));
		}
	}

	/** What a blow of {@code amount} becomes on {@code victim} (from the same mixin as the skins and pacts): cripple and the mark. */
	public static float incoming(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F || victim.getActiveEffectsMap().isEmpty()) {
			return amount;
		}
		int cripple = level(victim, Harm.CRIPPLE);
		if (cripple > 0 && physical(source)) {
			amount *= 1.0F + CRIPPLE_MORE * cripple;
		}
		int mark = level(victim, Harm.HUNT);
		if (mark > 0 && source.getEntity() instanceof LivingEntity by && hunter(by)) {
			amount *= 1.0F + HUNT_MORE * mark;
		}
		return amount;
	}

	/** A blow of the body: a swing, a bite, a shot, a fall; not fire, magic, poison or the void. */
	private static boolean physical(DamageSource source) {
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypeTags.IS_DROWNING)
			|| source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_LIGHTNING)) {
			return false;
		}
		return source.getDirectEntity() instanceof LivingEntity || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_FALL);
	}

	/** Whether {@code by} is one that hunts a marked body harder: a wolf, a beast, or one a {@link #HUNTERS} predicate names. */
	public static boolean hunter(LivingEntity by) {
		if (by instanceof Wolf || by.getType().builtInRegistryHolder().is(BEASTS)) {
			return true;
		}
		for (Predicate<LivingEntity> p : HUNTERS) {
			if (p.test(by)) {
				return true;
			}
		}
		return false;
	}

	/** Whether flame is within {@link #FLAME_REACH} blocks of {@code who}'s body. */
	private static boolean nearFlame(ServerLevel level, LivingEntity who) {
		BlockPos at = who.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-FLAME_REACH, -1, -FLAME_REACH), at.offset(FLAME_REACH, 2, FLAME_REACH))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.FIRE) || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT)
				|| level.getFluidState(p).is(FluidTags.LAVA)) {
				return true;
			}
		}
		return false;
	}

	/** One harm as an effect: harmful, its colour its harm's. */
	private static final class HarmEffect extends MobEffect {
		private final Harm harm;

		HarmEffect(Harm harm) {
			super(MobEffectCategory.HARMFUL, harm.color);
			this.harm = harm;
			if (harm == Harm.ACID) {
				this.addAttributeModifier(Attributes.ARMOR, JaymosPowers.id("acid_armor"), -ACID_CUT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS, JaymosPowers.id("acid_toughness"), -ACID_CUT,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			}
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return switch (this.harm) {
				case TOXIN -> duration % TOXIN_EVERY == 0;
				case FLAMMABLE -> duration % FLAME_EVERY == 0;
				case HUNT -> duration % MARK_EVERY == 0;
				default -> false;
			};
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			switch (this.harm) {
				case TOXIN -> entity.hurtServer(level, level.damageSources().magic(), TOXIN_HURT * (amplifier + 1));
				case FLAMMABLE -> {
					if (!entity.isOnFire() && nearFlame(level, entity)) {
						entity.igniteForSeconds(FLAME_BURN);
					}
				}
				case HUNT -> level.sendParticles(ParticleTypes.ANGRY_VILLAGER, entity.getX(), entity.getY() + entity.getBbHeight() + 0.3, entity.getZ(), 1,
					0.15, 0.05, 0.15, 0.0);
				default -> {
				}
			}
			return true;
		}
	}
}
