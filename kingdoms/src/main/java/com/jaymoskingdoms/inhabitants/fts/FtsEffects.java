package com.jaymoskingdoms.inhabitants.fts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.nightfall.Nightfall;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * The effects, potions and enchantments ported from From the Shadows Reborn (Sonmok14 and L_Ender, AGPL v3; the code here is
 * our own, the two effect icons are theirs: licenses/from-the-shadows/CREDITS-effects.md). Its Bleeding is Powers' Bleeding
 * (the {@code jaymospowers.tears} marker on the mobs that open cuts) and its Plague is Nightfall's Atrophy, so neither is made
 * again here. Registered from {@link Fts#register} (the effects) and, once Nightfall's Atrophy exists, from
 * {@link #registerPotions} (the potions).
 */
public final class FtsEffects {
	/** Heal Block: no healing reaches the body (a mixin on {@code LivingEntity.heal}). Track C's gear looks it up by this id. */
	public static Holder<MobEffect> HEAL_BLOCK;
	/** Blessing of Gluttony: what a player eats feeds them a fifth more per level (a mixin on the food's {@code onConsume}). */
	public static Holder<MobEffect> BLESSING_OF_GLUTTONY;
	public static Holder<Potion> FRENZY, TERRIBLE_PLAGUE, HARDSHELL;

	/** The effects' colours, as in the original. */
	static final int HEAL_BLOCK_COLOUR = 7897742, GLUTTONY_COLOUR = 15909412;
	/** How long the Nehemoth's bite and a Bulldrogioth's thorn block healing (the original's 100). */
	public static final int HEAL_BLOCK_TICKS = 100;
	/** A cooked froglin leg's Blessing of Gluttony: its length and level (II), as in the original. */
	public static final int GLUTTONY_TICKS = 600, GLUTTONY_AMPLIFIER = 1;
	/** Each level of the blessing adds this share of the food's nutrition again. */
	static final float GLUTTONY_SHARE = 0.2F;
	/** Frenzy: Resistance I and Strength II for a minute. */
	static final int FRENZY_TICKS = 1200, FRENZY_RESISTANCE = 0, FRENZY_STRENGTH = 1;
	/** Terrible Plague: the plague for half a minute, with hunger and weakness (II) for fifteen seconds. */
	static final int PLAGUE_TICKS = 600, PLAGUE_SICK_TICKS = 300, PLAGUE_SICK_AMPLIFIER = 1;
	/** Hardshell: Resistance IV with Hunger III for ten seconds. */
	static final int HARDSHELL_TICKS = 200, HARDSHELL_RESISTANCE = 3, HARDSHELL_HUNGER = 2;
	/** Freezing: a struck mob freezes for this many ticks a level (the original's 250), if it is not frozen already. */
	static final int FREEZING_TICKS_PER_LEVEL = 250;
	static final ResourceKey<Enchantment> FREEZING = ResourceKey.create(Registries.ENCHANTMENT, JaymosKingdoms.id("freezing"));

	private FtsEffects() {
	}

	public static void register() {
		HEAL_BLOCK = effect("heal_block", new MobEffect(MobEffectCategory.HARMFUL, HEAL_BLOCK_COLOUR) {
		});
		BLESSING_OF_GLUTTONY = effect("blessing_of_gluttony", new MobEffect(MobEffectCategory.BENEFICIAL, GLUTTONY_COLOUR) {
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!blocked && taken > 0.0F && source.getEntity() instanceof LivingEntity attacker && attacker.level() instanceof ServerLevel level) {
				freeze(level, attacker, entity);
			}
		});
	}

	/** The potions: after Nightfall has registered its Atrophy, which the Terrible Plague carries. */
	public static void registerPotions() {
		FRENZY = potion("frenzy", new Potion("frenzy", new MobEffectInstance(MobEffects.RESISTANCE, FRENZY_TICKS, FRENZY_RESISTANCE),
			new MobEffectInstance(MobEffects.STRENGTH, FRENZY_TICKS, FRENZY_STRENGTH)));
		TERRIBLE_PLAGUE = potion("terrible_plague", new Potion("terrible_plague", new MobEffectInstance(Nightfall.ATROPHY, PLAGUE_TICKS),
			new MobEffectInstance(MobEffects.HUNGER, PLAGUE_SICK_TICKS, PLAGUE_SICK_AMPLIFIER),
			new MobEffectInstance(MobEffects.WEAKNESS, PLAGUE_SICK_TICKS, PLAGUE_SICK_AMPLIFIER)));
		HARDSHELL = potion("hardshell", new Potion("hardshell", new MobEffectInstance(MobEffects.RESISTANCE, HARDSHELL_TICKS, HARDSHELL_RESISTANCE),
			new MobEffectInstance(MobEffects.HUNGER, HARDSHELL_TICKS, HARDSHELL_HUNGER)));
	}

	/** Whether no healing may reach {@code mob} (read by the heal mixin). */
	public static boolean healBlocked(LivingEntity mob) {
		return HEAL_BLOCK != null && mob.hasEffect(HEAL_BLOCK);
	}

	/** Puts Heal Block on {@code mob} for {@code ticks}. */
	public static void healBlock(LivingEntity mob, int ticks) {
		if (HEAL_BLOCK != null) {
			mob.addEffect(new MobEffectInstance(HEAL_BLOCK, ticks));
		}
	}

	/** What a Blessing of Gluttony adds when {@code eater} finishes a bite of {@code food} (read by the food mixin). */
	public static void gluttony(LivingEntity eater, FoodProperties food) {
		if (BLESSING_OF_GLUTTONY == null || !(eater instanceof Player player) || food.nutrition() <= 0) {
			return;
		}
		MobEffectInstance blessing = player.getEffect(BLESSING_OF_GLUTTONY);
		if (blessing != null) {
			int bonus = Math.round(food.nutrition() * GLUTTONY_SHARE * (blessing.getAmplifier() + 1));
			// the same saturation per point of nutrition as the food itself gives
			player.getFoodData().eat(bonus, food.saturation() / (2.0F * food.nutrition()));
		}
	}

	/** The Freezing enchantment on {@code attacker}'s main hand freezes what it struck. */
	private static void freeze(ServerLevel level, LivingEntity attacker, LivingEntity struck) {
		Holder<Enchantment> freezing = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(FREEZING).orElse(null);
		if (freezing == null) {
			return;
		}
		int levels = EnchantmentHelper.getItemEnchantmentLevel(freezing, attacker.getMainHandItem());
		if (levels > 0 && struck.getTicksFrozen() == 0) {
			struck.playSound(SoundEvents.PLAYER_HURT_FREEZE, 2.0F, 1.0F);
			struck.setTicksFrozen(FREEZING_TICKS_PER_LEVEL * levels);
		}
	}

	private static Holder<MobEffect> effect(String name, MobEffect effect) {
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id(name), effect);
	}

	private static Holder<Potion> potion(String name, Potion potion) {
		Identifier id = JaymosKingdoms.id(name);
		return Registry.registerForHolder(BuiltInRegistries.POTION, id, potion);
	}
}
