package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

/**
 * (0.17.54, Andy's NOEA picks) The Frost-born's two extras. <b>Ki reserve</b>: a second, hidden pool of ki, up to
 * {@link #RESERVE_SHARE} of his maximum, that never shows on the HUD. It fills slowly ({@link #FILL} a tick while his ki is
 * at {@link #FILL_KI_SHARE} of full or more, {@link #FILL_CHARGING} while he charges; nothing for {@link #RESTING} ticks after
 * it was last drawn on). When his ki runs below {@link #KICK_BELOW} it kicks in: up to {@link #DRAW} a tick moves over at
 * {@link #EFFICIENCY} of its worth, and a technique he cannot quite afford draws the shortfall the same way. A pale shimmer
 * and a chime tell him it has woken. <b>Ki-hardened body</b>: while charging, or holding at least {@link #HARDEN_KI_SHARE} of his
 * ki, blows are cut by {@link #HARDEN_MIN} (rising to {@link #HARDEN_MAX} at full ki; charging counts as full), and every health
 * point turned aside costs {@link #HARDEN_KI_COST} ki. Nothing turns aside what goes through invulnerability.
 */
public final class ZFighterFrostReserve {
	static final float RESERVE_SHARE = 0.5F;
	static final float FILL = 0.015F, FILL_CHARGING = 0.06F;
	static final float FILL_KI_SHARE = 0.8F;
	static final int RESTING = 100;
	static final float KICK_BELOW = 3.0F;
	static final float DRAW = 1.0F;
	static final float EFFICIENCY = 0.9F;
	static final float HARDEN_KI_SHARE = 0.6F;
	static final float HARDEN_MIN = 0.08F, HARDEN_MAX = 0.2F;
	static final float HARDEN_KI_COST = 2.0F;

	/** What is in the reserve. Saved (not kept through death). */
	public static final AttachmentType<Float> RESERVE = AttachmentRegistry.create(JaymosPowers.id("zfighter_frost_reserve"),
		builder -> builder.persistent(Codec.FLOAT));

	/** When each last drew on it. */
	private static final Map<UUID, Long> DRAWN = new HashMap<>();

	private ZFighterFrostReserve() {
	}

	static void clear() {
		DRAWN.clear();
	}

	static void forget(ServerPlayer player) {
		DRAWN.remove(player.getUUID());
	}

	/** What is in the reserve now. */
	public static float reserve(ServerPlayer player) {
		return player.getAttachedOrElse(RESERVE, 0.0F);
	}

	/** Every tick, per Frost-born Z Fighter. */
	static void tick(ServerPlayer player, ZFighterPower.Live live, int max) {
		if (!player.isAlive() || player.isCreative()) {
			return;
		}
		float reserve = reserve(player);
		long now = player.level().getGameTime();
		if (live.ki < KICK_BELOW && reserve > 0.0F) {
			float draw = Math.min(reserve, DRAW);
			live.ki += draw * EFFICIENCY;
			set(player, reserve - draw);
			woke(player, now);
			return;
		}
		Long drawn = DRAWN.get(player.getUUID());
		float cap = max * RESERVE_SHARE;
		boolean rested = drawn == null || now - drawn >= RESTING || now < drawn;
		if (rested && reserve < cap && (live.charging || live.ki >= max * FILL_KI_SHARE)) {
			set(player, Math.min(cap, reserve + (live.charging ? FILL_CHARGING : FILL)));
		}
	}

	private static void set(ServerPlayer player, float amount) {
		if (amount <= 0.0F) {
			player.removeAttached(RESERVE);
		} else {
			player.setAttached(RESERVE, amount);
		}
	}

	/** Records a draw; the first in a while shows it. */
	private static void woke(ServerPlayer player, long now) {
		Long last = DRAWN.put(player.getUUID(), now);
		ServerLevel level = player.level();
		if (last == null || now - last > RESTING) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_reserve").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		}
		if (now % 6 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.4, 0.7, 0.4, 0.02);
		}
	}

	/**
	 * Part of {@link ZFighterPower#spend}: a technique costs {@code cost} and he has less. Draws the shortfall from the reserve
	 * (topping his ki up to {@code cost}) if it is enough; otherwise draws nothing and returns false.
	 */
	static boolean cover(ServerPlayer player, ZFighterPower.Live live, float cost) {
		float need = cost - live.ki;
		if (need <= 0.0F) {
			return true;
		}
		float drawn = need / EFFICIENCY;
		float reserve = reserve(player);
		if (reserve < drawn) {
			return false;
		}
		live.ki += need;
		set(player, reserve - drawn);
		woke(player, player.level().getGameTime());
		return true;
	}

	/** Part of {@link ZFighterBattle#modifyDamage}: the ki-hardened body turns aside a share of {@code damage}. */
	static float harden(ServerPlayer player, DamageSource source, float damage) {
		if (ZFighterLineage.of(player) != ZFighterLineage.FROST || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || player.isCreative()) {
			return damage;
		}
		ZFighterPower.Live live = ZFighterPower.live(player);
		float share = live.charging ? 1.0F : live.ki / Math.max(1, ZFighterPower.maxKi(player));
		if (share < HARDEN_KI_SHARE) {
			return damage;
		}
		float cut = HARDEN_MIN + (HARDEN_MAX - HARDEN_MIN) * Math.min(1.0F, (share - HARDEN_KI_SHARE) / (1.0F - HARDEN_KI_SHARE));
		float turned = damage * cut;
		live.ki = Math.max(0.0F, live.ki - turned * HARDEN_KI_COST);
		return damage - turned;
	}
}
