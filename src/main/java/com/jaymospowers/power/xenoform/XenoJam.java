package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.Potion;

/**
 * (0.18.88) Jammed (the band-jammer pick): static in the band. An alien who catches it is thrown back into their own body,
 * and the band won't slam (or save them) until it wears off. Does nothing to anyone without a band.
 * <p>
 * The Potion of Jamming is brewed from an awkward potion and an echo shard (redstone to lengthen it, gunpowder and
 * dragon's breath for splash and lingering; the recipes are data, {@code recipe/brewing/}), so it shows in JEI's brewing
 * pages; arrows come from the game's own tipped-arrow recipe. Guards' jammer weapons in realms that know about aliens are a
 * later patch.
 */
public final class XenoJam extends MobEffect {
	// ---- tuning ----
	/** The potion and the long one (ticks): 20 s and 40 s. */
	public static final int TICKS = 20 * 20, LONG_TICKS = 20 * 40;

	public static Holder<MobEffect> JAMMED;
	public static Holder<Potion> POTION, LONG_POTION;

	private XenoJam() {
		super(MobEffectCategory.HARMFUL, 0x6A2CA0, ParticleTypes.ELECTRIC_SPARK);
	}

	static void register() {
		JAMMED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("jammed")),
			new XenoJam());
		POTION = potion("jamming", new MobEffectInstance(JAMMED, TICKS));
		LONG_POTION = potion("long_jamming", new MobEffectInstance(JAMMED, LONG_TICKS));
	}

	private static Holder<Potion> potion(String id, MobEffectInstance effect) {
		return Registry.registerForHolder(BuiltInRegistries.POTION, JaymosPowers.id(id), new Potion("jamming", effect));
	}

	/** Whether {@code player}'s band is jammed. */
	public static boolean jammed(Player player) {
		return JAMMED != null && player.hasEffect(JAMMED);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		// (the change back happens in XenoformPower.tick; here only the static round the band)
		if (entity instanceof ServerPlayer player && XenoformPower.has(player) && entity.tickCount % 10 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() * 0.55, entity.getZ(), 3, 0.25, 0.2, 0.25, 0.05);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return true;
	}
}
