package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.Potion;

/**
 * (0.17.52) The Potion of Cleansing, brewed: Andy (2026-10-27): "there should also be a brewable potion for it"
 * (festering). An awkward potion and a honeycomb at a brewing stand (redstone to lengthen it, gunpowder and dragon's
 * breath for splash and lingering; the recipes are data, {@code recipe/brewing/}). While it lasts, every festering part
 * is cured and every dirty cut washed out, as spirits or honey poured on it would ({@link Bleeding#clean}), and the
 * fever's weakness lifts; a cut kept clean that way can't fester while it runs. It closes no cuts and heals nothing.
 */
public final class Cleansing extends MobEffect {
	/** How long the plain potion and the long one last (ticks). */
	public static final int TICKS = 20 * 60 * 3, LONG_TICKS = 20 * 60 * 8;
	/** It works this often (ticks). */
	static final int EVERY = 20;

	public static Holder<MobEffect> CLEANSING;
	public static Holder<Potion> POTION, LONG_POTION;

	private Cleansing() {
		super(MobEffectCategory.BENEFICIAL, 0xE8B84A, ParticleTypes.WAX_ON);
	}

	static void register() {
		CLEANSING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("cleansing")),
			new Cleansing());
		// (named "wound_cleansing" for its translation: a potion's name is shared by every mod's)
		POTION = potion("wound_cleansing", new MobEffectInstance(CLEANSING, TICKS));
		LONG_POTION = potion("long_wound_cleansing", new MobEffectInstance(CLEANSING, LONG_TICKS));
	}

	private static Holder<Potion> potion(String id, MobEffectInstance effect) {
		return Registry.registerForHolder(BuiltInRegistries.POTION, JaymosPowers.id(id), new Potion("wound_cleansing", effect));
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		int festering = Bleeding.festering(entity);
		int foul = festering | Bleeding.dirty(entity);
		if (foul == 0) {
			return true;
		}
		for (BodyPart part : BodyPart.values()) {
			if ((foul & Breaks.bit(part)) != 0) {
				Bleeding.clean(entity, part);
			}
		}
		if (festering != 0) {
			entity.removeEffect(MobEffects.WEAKNESS);
			level.sendParticles(ParticleTypes.WAX_ON, entity.getX(), entity.getY(0.6), entity.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
			if (entity instanceof Player player) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.cleansed").withStyle(ChatFormatting.GOLD));
			}
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return tickCount % EVERY == 0;
	}
}
