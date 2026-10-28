package com.jaymospowers.frost;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.Potion;

/**
 * (0.17.115) Freezing, from Minecraft's 26.4 snapshots (Andy, 2026-10-28: "I just really want the way the chilling potion
 * works transferred to frostborn's prison"). Our own copy of the snapshot's rules, for 26.3: whoever has it slowly freezes
 * as if standing in powder snow, so the frost creeps over their screen and they slow to half speed over seven seconds;
 * once frozen through they shake and take a point of freeze damage every two seconds (fire-blooded mobs five times that)
 * until it ends, then thaw. Leather armour spares the damage but not the cold. Standing in fire or lava keeps it off;
 * mobs that can't freeze (strays, polar bears, snow golems, the Frostbite) shrug it off.
 * <p>
 * The game does the rest itself: the speed, the shaking, the frost overlay and the damage all follow an entity's frozen
 * ticks, which thaw by {@link #THAW} a tick out of powder snow; this only adds more than that each tick. Each level of
 * the effect freezes {@link #PER_LEVEL} a tick faster.
 * <p>
 * The Potion of Freezing is brewed from an awkward potion and an {@link Frost#ICE_BALL} (redstone to lengthen it,
 * gunpowder and dragon's breath for splash and lingering; the recipes are data, {@code recipe/brewing/}). Arrows of
 * Freezing come from the game's own tipped-arrow recipe. Kingdoms' Frostborn ice prison gives it too.
 */
public final class Freezing extends MobEffect {
	// ---- tuning ----
	/** The potion and the long one (ticks): 45 s and 90 s, as in the snapshot. */
	public static final int TICKS = 20 * 45, LONG_TICKS = 20 * 90;
	/** Frozen ticks the game takes off each tick out of powder snow, and how many a tick each level adds over that. */
	public static final int THAW = 2, PER_LEVEL = 1;

	public static Holder<MobEffect> FREEZING;
	public static Holder<Potion> POTION, LONG_POTION;

	private Freezing() {
		super(MobEffectCategory.HARMFUL, 0x9ED8F2, ParticleTypes.SNOWFLAKE);
	}

	static void register() {
		FREEZING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("freezing")),
			new Freezing());
		POTION = potion("freezing", new MobEffectInstance(FREEZING, TICKS));
		LONG_POTION = potion("long_freezing", new MobEffectInstance(FREEZING, LONG_TICKS));
	}

	private static Holder<Potion> potion(String id, MobEffectInstance effect) {
		return Registry.registerForHolder(BuiltInRegistries.POTION, JaymosPowers.id(id), new Potion("freezing", effect));
	}

	/** Whether the cold can't touch {@code entity} at all (leather only spares the damage, so it isn't asked here). */
	public static boolean immune(LivingEntity entity) {
		return entity.getType().builtInRegistryHolder().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES) || entity.isInLava() || entity.getInBlockState().is(BlockTags.FIRE);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		if (!immune(entity)) {
			// (never below what it was: a power may have frozen them past full)
			int frozen = entity.getTicksFrozen();
			entity.setTicksFrozen(Math.max(frozen, Math.min(entity.getTicksRequiredToFreeze(), frozen + THAW + PER_LEVEL * (amplifier + 1))));
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return true;
	}
}
