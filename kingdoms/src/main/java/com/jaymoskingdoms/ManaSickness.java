package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.237) Mana sickness. Andy: "add a type of mana sickness for punishing over zealous casters". Every bit of mana spent,
 * and every bit forced in past full (crushed amethyst, a wand), is <b>strain</b>, counted against the caster's most mana;
 * it eases off by itself ({@link #EASES} of the most a second, faster meditating). Push it too far and they fall sick, worse
 * the further they pushed, for a while:
 * <ol>
 * <li><b>Queasy</b> (strain past {@code AT[0]} times their most): the world swims a moment, mana comes back at half pace.</li>
 * <li><b>Mana-sick</b> ({@code AT[1]}): weak, slow and hungry, no mana back at all, and every cast costs half again.</li>
 * <li><b>Burnt out</b> ({@code AT[2]}): what mana is left burns out of them, it hurts, and they can't cast till it passes.</li>
 * </ol>
 * The sickness is synced to its own player ({@link #SICK}) for the tint on their screen ({@code ManaClient}).
 */
public final class ManaSickness {
	/** Strain eases off this share of the most mana a second; this many times as fast meditating. */
	static final float EASES = 0.04F, MEDITATING_EASES = 3.0F;
	/** Strain (as times the most mana) at which each stage takes hold. */
	static final float[] AT = {1.0F, 1.6F, 2.2F};
	/** How long each stage lasts (ticks). */
	static final int[] LASTS = {20 * 60, 20 * 90, 20 * 120};
	/** Mana back while queasy, sick, burnt out (share of the usual). */
	static final float[] REGEN = {1.0F, 0.5F, 0.0F, 0.0F};
	/** A cast while sick costs this many times as much. */
	static final float SICK_COST = 1.5F;
	/** Burning out hurts this much. */
	static final float BURNOUT_HURT = 4.0F;

	/** The stage of their sickness: 0 well, 1 queasy, 2 sick, 3 burnt out. Synced to them (the tint). */
	public static final AttachmentType<Integer> SICK = AttachmentRegistry.create(JaymosKingdoms.id("mana_sick"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** Strain now (times the most mana), and when the sickness passes (game time), by player. */
	private static final Map<UUID, Float> STRAIN = new HashMap<>();
	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private ManaSickness() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STRAIN.clear();
			UNTIL.clear();
		});
	}

	public static int stage(Player player) {
		return player.getAttachedOrElse(SICK, 0);
	}

	static float regenTimes(Player player) {
		return REGEN[Math.clamp(stage(player), 0, REGEN.length - 1)];
	}

	static float costTimes(Player player) {
		return stage(player) >= 2 ? SICK_COST : 1.0F;
	}

	static boolean burntOut(Player player) {
		return stage(player) >= 3;
	}

	/** {@code mana} spent or forced in: strain, and sickness if it's gone too far. */
	static void strain(ServerPlayer player, float mana) {
		float max = Mana.max(player);
		if (max <= 0.0F || mana <= 0.0F || player.isCreative()) {
			return;
		}
		float strain = STRAIN.merge(player.getUUID(), mana / max, Float::sum);
		int stage = 0;
		while (stage < AT.length && strain >= AT[stage]) {
			stage++;
		}
		if (stage > stage(player)) {
			fall(player, stage);
		}
	}

	/** Once a second: strain eases, and the sickness passes when its time is up. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		Float strain = STRAIN.get(id);
		if (strain != null) {
			float eased = strain - EASES * (Mana.meditating(player) > 0 ? MEDITATING_EASES : 1.0F);
			if (eased <= 0.0F) {
				STRAIN.remove(id);
			} else {
				STRAIN.put(id, eased);
			}
		}
		Long until = UNTIL.get(id);
		if (until != null && player.level().getGameTime() >= until) {
			UNTIL.remove(id);
			if (stage(player) > 0) {
				player.removeAttached(SICK);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mana.sick.passed").withStyle(ChatFormatting.AQUA));
			}
		} else if (until == null && stage(player) > 0) {
			// (a sickness from before a restart: let it go)
			player.removeAttached(SICK);
		}
	}

	/** (0.7.238) {@code player} burns out at once (releasing a spirit does this). */
	public static void burnOut(ServerPlayer player) {
		if (stage(player) < 3) {
			fall(player, 3);
		}
	}

	private static void fall(ServerPlayer player, int stage) {
		player.setAttached(SICK, stage);
		UNTIL.put(player.getUUID(), player.level().getGameTime() + LASTS[stage - 1]);
		ServerLevel level = (ServerLevel)player.level();
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.mana.sick." + stage).withStyle(ChatFormatting.DARK_AQUA));
		switch (stage) {
			case 1 -> player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 6, 0, false, false, true));
			case 2 -> {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 8, 0, false, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LASTS[1], 0, false, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LASTS[1], 0, false, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 0, false, false, true));
			}
			default -> {
				Mana.set(player, 0.0F);
				// (0.7.238) a spirit that's out is shaken
				com.jaymoskingdoms.spirits.Spirits.burntOut(player);
				player.hurtServer(level, level.damageSources().magic(), BURNOUT_HURT);
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 12, 0, false, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LASTS[2], 1, false, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LASTS[2], 0, false, false, true));
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
				Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.4F);
	}
}
