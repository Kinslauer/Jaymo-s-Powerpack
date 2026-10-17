package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.7.92) Andy: "keep the glow but activate it only when the earthling is almost dead, and it activates giving them a
 * random buff for 5m". Brought below {@link #ALMOST_DEAD} of their health, an Earthling (player or villager) digs deep:
 * the ki round their fists flares (client HeritageLooks, VillagerLooks) and stays lit for {@link #SURGE_TICKS}, with one
 * buff drawn from {@link #BUFFS} for as long. Once over, it can't come again for {@link #REST_TICKS}.
 */
public final class EarthlingSurge {
	/** The share of max health at or below which the surge comes. */
	static final float ALMOST_DEAD = 0.25F;
	/** How long the glow and the buff last, and the rest after before the next (ticks). */
	static final int SURGE_TICKS = 6000, REST_TICKS = 6000;
	/** The buffs one is drawn from, each at its amplifier. */
	static final List<Buff> BUFFS = List.of(new Buff(MobEffects.STRENGTH, 0), new Buff(MobEffects.SPEED, 1), new Buff(MobEffects.RESISTANCE, 0),
		new Buff(MobEffects.REGENERATION, 0), new Buff(MobEffects.HASTE, 1), new Buff(MobEffects.JUMP_BOOST, 1), new Buff(MobEffects.ABSORPTION, 1));

	record Buff(Holder<MobEffect> effect, int amplifier) {
	}

	/** The game time the surge ends. Saved (not past death) and synced to everyone, for the glow. */
	public static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("earthling_surge"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private EarthlingSurge() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (entity.isAlive() && entity.getHealth() <= entity.getMaxHealth() * ALMOST_DEAD && VillagerHeritage.of(entity) == Heritage.Kind.EARTHLING) {
				surge(entity);
			}
		});
	}

	/** Whether {@code entity}'s ki is flaring now (for its look). */
	public static boolean surging(Entity entity) {
		Long until = entity.getAttached(UNTIL);
		return until != null && entity.level().getGameTime() < until;
	}

	private static void surge(LivingEntity entity) {
		long now = entity.level().getGameTime();
		Long until = entity.getAttached(UNTIL);
		if (until != null && now < until + REST_TICKS) {
			return;
		}
		entity.setAttached(UNTIL, now + SURGE_TICKS);
		Buff buff = BUFFS.get(entity.getRandom().nextInt(BUFFS.size()));
		entity.addEffect(new MobEffectInstance(buff.effect(), SURGE_TICKS, buff.amplifier(), false, true, true));
		if (entity.level() instanceof ServerLevel level) {
			level.playSound(null, entity.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
			level.playSound(null, entity.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 0.7F);
			level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 24, 0.4, 0.6, 0.4, 0.08);
		}
		if (entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.earthling_surge", Component.translatable(buff.effect().value().getDescriptionId()))
				.withStyle(ChatFormatting.GOLD));
		}
	}
}
