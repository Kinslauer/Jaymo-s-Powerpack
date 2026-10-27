package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * (0.17.54, Andy's NOEA picks) The Earthling's two extras. <b>Adrenaline</b>: in a fight (a creature hurt him in the last
 * five seconds), when he is down to {@link #LOW_HEALTH} of his health or {@link #OUTNUMBERED} different foes have hurt
 * him in the last {@link #FOE_WINDOW} ticks, a surge for {@link #SURGE_TICKS}: faster ({@link #SURGE_SPEED}), harder fists
 * ({@link #SURGE_FISTS}) and {@link #SURGE_KI} ki back every tick. When it ends he crashes (a little hunger, a slow spell).
 * One surge per {@link #COOLDOWN} ticks. <b>Blood-paid ki</b>: when a technique costs more ki than he has, the rest is paid
 * in health, automatically ({@link #BLOOD_KI_PER_HP} ki for each health point) as long as that is at most {@link #BLOOD_MAX_HP}
 * health points at once and leaves him at least {@link #BLOOD_FLOOR} health; at most one payment every {@link #BLOOD_GAP}
 * ticks. Only things bought through {@link ZFighterPower#spend} can be paid in blood (not flight or a form's drain).
 */
public final class ZFighterEarthlingRace {
	// ---- adrenaline ----
	static final float LOW_HEALTH = 0.35F;
	static final int OUTNUMBERED = 3;
	static final int FOE_WINDOW = 200;
	/** How recently a creature must have hurt him for it to be a fight (ticks). */
	static final int IN_FIGHT_TICKS = 100;
	static final int SURGE_TICKS = 160;
	static final long COOLDOWN = 3600L;
	static final double SURGE_SPEED = 0.3, SURGE_FISTS = 0.35;
	static final float SURGE_KI = 0.12F;
	/** The crash after: hunger (exhaustion), and slowness (ticks, amplifier). */
	static final float CRASH_EXHAUSTION = 3.0F;
	static final int CRASH_TICKS = 60, CRASH_SLOW = 0;
	// ---- blood-paid ki ----
	static final float BLOOD_KI_PER_HP = 5.0F;
	static final float BLOOD_MAX_HP = 8.0F;
	static final float BLOOD_FLOOR = 4.0F;
	static final int BLOOD_GAP = 10;
	static final int SURGE_COLOR = 0xFF5A3A, BLOOD_COLOR = 0xB01010;

	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_adrenaline_speed");
	private static final Identifier FISTS_ID = JaymosPowers.id("zfighter_adrenaline_fists");

	/** The game time of his last surge (the cooldown). Saved, kept through death. */
	public static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosPowers.id("zfighter_adrenaline_last"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Surges under way: until when. */
	private static final Map<UUID, Long> SURGING = new HashMap<>();
	/** When blood last paid for a technique. */
	private static final Map<UUID, Long> BLEEDS = new HashMap<>();

	private ZFighterEarthlingRace() {
	}

	static void clear() {
		SURGING.clear();
		BLEEDS.clear();
	}

	static void forget(ServerPlayer player) {
		SURGING.remove(player.getUUID());
		BLEEDS.remove(player.getUUID());
	}

	/** Every tick, per Earthling Z Fighter. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		long now = player.level().getGameTime();
		Long until = SURGING.get(player.getUUID());
		if (until != null) {
			if (!player.isAlive() || now >= until) {
				end(player);
				return;
			}
			live.ki += SURGE_KI;
			if (now % 4 == 0) {
				player.level().sendParticles(new DustParticleOptions(SURGE_COLOR, 1.0F), true, false, player.getX(), player.getY() + 1.0, player.getZ(),
					3, 0.35, 0.7, 0.35, 0.02);
			}
			return;
		}
		if (player.isCreative() || player.isSpectator() || ZFighterLineage.ape(player)) {
			return;
		}
		boolean inFight = player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() <= IN_FIGHT_TICKS;
		if (!inFight) {
			return;
		}
		boolean low = player.getHealth() / player.getMaxHealth() <= LOW_HEALTH;
		boolean outnumbered = !low && foes(live, now) >= OUTNUMBERED;
		if (!low && !outnumbered) {
			return;
		}
		Long last = player.getAttached(LAST);
		if (last != null && now - last < COOLDOWN && now >= last) {
			return;
		}
		start(player, now, outnumbered);
	}

	/** How many different foes have hurt him in the last {@link #FOE_WINDOW} ticks. */
	private static int foes(ZFighterPower.Live live, long now) {
		int count = 0;
		for (long when : live.attackers.values()) {
			if (now - when <= FOE_WINDOW) {
				count++;
			}
		}
		return count;
	}

	private static void start(ServerPlayer player, long now, boolean outnumbered) {
		SURGING.put(player.getUUID(), now + SURGE_TICKS);
		player.setAttached(LAST, now);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SURGE_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FISTS_ID, SURGE_FISTS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ServerLevel level = player.level();
		level.sendParticles(new DustParticleOptions(SURGE_COLOR, 1.6F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.5F, 1.4F);
		player.sendOverlayMessage(Component.translatable(outnumbered ? "message.jaymospowers.zfighter_adrenaline_crowd" : "message.jaymospowers.zfighter_adrenaline")
			.withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
	}

	private static void end(ServerPlayer player) {
		SURGING.remove(player.getUUID());
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FISTS_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.isAlive()) {
			if (!player.isCreative()) {
				player.causeFoodExhaustion(CRASH_EXHAUSTION);
			}
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CRASH_TICKS, CRASH_SLOW, false, true));
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_adrenaline_end").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/**
	 * Part of {@link ZFighterPower#spend}: a technique costs {@code cost} and he has less. Pays the rest in health if the rules
	 * allow it, topping his ki up to {@code cost} (the caller then spends it). False: he cannot pay it that way.
	 */
	static boolean blood(ServerPlayer player, ZFighterPower.Live live, float cost) {
		float need = cost - live.ki;
		if (need <= 0.0F) {
			return true;
		}
		float hp = (float)Math.ceil(need / BLOOD_KI_PER_HP);
		long now = player.level().getGameTime();
		Long last = BLEEDS.get(player.getUUID());
		if (hp > BLOOD_MAX_HP || player.getHealth() - hp < BLOOD_FLOOR || last != null && now - last < BLOOD_GAP && now >= last) {
			return false;
		}
		BLEEDS.put(player.getUUID(), now);
		player.setHealth(player.getHealth() - hp);
		live.ki += hp * BLOOD_KI_PER_HP;
		ServerLevel level = player.level();
		level.sendParticles(new DustParticleOptions(BLOOD_COLOR, 1.2F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_blood_ki", Math.round(hp)).withStyle(ChatFormatting.DARK_RED));
		return true;
	}
}
