package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The legendary Warrior-born take radiation as strength (Andy, 2026-11-24: "them and legendary warrior born that are
 * increased by being irradiated", and nuclear energy in their kit; Lore.md, "The sick caves and who can stand them").
 * A legendary ({@link ZFighterLegendary#mutant}) is never Irradiated (Alex's Caves' sickness): whatever would irradiate
 * him, and every breath of the Toxic Caves ({@link #TOXIC_CAVES}), builds up in him as {@link #RADS} instead. The more he
 * holds, the harder he hits (up to {@link #DAMAGE_MAX} more, on all his blows), the greener he glows, and his rage's surge
 * ({@link ZFighterLegendary#surge}) burns hotter ({@link #SURGE_MAX}) and leaves those it strikes Irradiated. It fades
 * slowly once he is out of the green ({@link #FADE}): the longer he stays down, the harder he comes back up.
 * Only with Alex's Caves loaded (without it nothing here ever finds the effect or the biome).
 */
public final class ZFighterRadiance {
	// ---- tuning ----
	/** Ticks between checks. */
	static final int EVERY = 40;
	/** Rads he can hold; gained each check in the caves, or while something irradiates him; lost each check otherwise. */
	static final int MAX = 900, CAVE_GAIN = 3, DOSE_GAIN = 2, FADE = 1;
	/** At full: this much more damage (a share of all of it), a surge this much hotter. */
	static final double DAMAGE_MAX = 0.4;
	static final float SURGE_MAX = 0.5F;
	/** A surge at any rads leaves this long an Irradiated (amplifier 0, 1 from half full). */
	static final int SURGE_DOSE_TICKS = 200;
	/** Green motes from this share up. */
	static final float GLOW_FROM = 0.25F;
	static final int GLOW = 0x7CFF4A;

	static final ResourceKey<Biome> TOXIC_CAVES = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("alexscaves", "toxic_caves"));
	static final Identifier IRRADIATED = Identifier.fromNamespaceAndPath("alexscaves", "irradiated");
	private static final Identifier DAMAGE_ID = JaymosPowers.id("zfighter_radiance_damage");

	/** The rads a legendary holds. Saved; not kept through death. */
	public static final AttachmentType<Integer> RADS = AttachmentRegistry.create(JaymosPowers.id("zfighter_rads"),
		builder -> builder.persistent(Codec.INT));

	private ZFighterRadiance() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterRadiance::tick);
	}

	static @Nullable Holder<MobEffect> irradiated() {
		return BuiltInRegistries.MOB_EFFECT.get(IRRADIATED).map(h -> (Holder<MobEffect>)h).orElse(null);
	}

	/** How full he is, 0 to 1. */
	public static float share(LivingEntity body) {
		Integer rads = body.getAttached(RADS);
		return rads == null ? 0.0F : Math.min(1.0F, rads / (float)MAX);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Holder<MobEffect> sick = irradiated();
		if (sick == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Integer had = player.getAttached(RADS);
			if (!ZFighterLegendary.mutant(player)) {
				if (had != null) {
					player.removeAttached(RADS);
					ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				}
				continue;
			}
			int rads = had == null ? 0 : had;
			boolean dosed = player.removeEffect(sick);
			boolean cave = player.isAlive() && player.level().getBiome(player.blockPosition()).is(TOXIC_CAVES);
			rads = cave ? rads + CAVE_GAIN : dosed ? rads + DOSE_GAIN : rads - FADE;
			rads = Math.max(0, Math.min(MAX, rads));
			if (had == null || rads != had) {
				if (rads == 0) {
					player.removeAttached(RADS);
				} else {
					player.setAttached(RADS, rads);
				}
			}
			float share = rads / (float)MAX;
			ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_MAX * share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			if (share >= GLOW_FROM) {
				MagicFx.burst(player.level(), player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), GLOW, 1.0F,
					2 + (int)(share * 6), 0.4, player.getBbHeight() * 0.4, 0.4);
			}
		}
	}

	/** A surge's damage, hotter with what he holds. */
	static float surge(ServerPlayer player, float damage) {
		return damage * (1.0F + SURGE_MAX * share(player));
	}

	/** Someone a charged legendary's surge struck: left Irradiated. */
	static void struck(ServerPlayer player, LivingEntity hit) {
		float share = share(player);
		Holder<MobEffect> sick = share > 0.0F ? irradiated() : null;
		if (sick != null && !(hit instanceof net.minecraft.world.entity.player.Player other && ZFighterLegendary.mutant(other))) {
			hit.addEffect(new MobEffectInstance(sick, SURGE_DOSE_TICKS, share >= 0.5F ? 1 : 0), player);
		}
	}
}
