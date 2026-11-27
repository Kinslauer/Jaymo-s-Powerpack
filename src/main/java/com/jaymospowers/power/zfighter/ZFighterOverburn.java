package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Overburn: a Z Fighter stacking power-up that layers over base or any form.
 * <ul>
 * <li>Hold Sneak+X to climb through stages 1–{@link #MAX_STAGE}, growing a crimson aura each step.</li>
 * <li>Above half ({@link #HURT_ABOVE}+1 and up), the strain hurts the fighter every few seconds.</li>
 * <li>Letting go holds the boost for a while ({@link #HOLD_TICKS}), then drops off and leaves the fighter slowed.</li>
 * </ul>
 */
public final class ZFighterOverburn {
	public static final int MAX_STAGE = 4;
	/** Distinct crimson red aura hue (drawn on {@code ZFighterKiAura}). */
	public static final int COLOR = 0xE82424;

	/** Ticks held per stage step while raising Overburn. */
	static final int RAISE_TICKS = 16;
	/** Ticks the boost persists after releasing the hold, by stage (1..4). */
	static final int[] HOLD_TICKS = {0, 180, 150, 120, 100};
	/** Stages strictly above this (stages 3 and 4, i.e., above half) hurt the user periodically. */
	static final int HURT_ABOVE = 2;
	static final int[] HURT_EVERY = {0, 0, 0, 50, 34};
	static final float[] HURT_AMOUNT = {0.0F, 0.0F, 0.0F, 1.5F, 2.5F};
	/** Never lets Overburn self-strain kill you: drops off if health would fall to this or lower. */
	static final float MIN_HEALTH = 2.0F;

	/** Combat bonuses by stage (1..4). */
	static final double[] FIST_BONUS = {0.0, 0.14, 0.28, 0.45, 0.65};
	static final double[] SPEED_BONUS = {0.0, 0.05, 0.10, 0.15, 0.20};
	static final float[] KI_BONUS = {1.0F, 1.12F, 1.24F, 1.38F, 1.55F};
	static final float[] KI_DRAIN = {0.0F, 0.04F, 0.08F, 0.14F, 0.22F};

	/** Recovery slow duration after Overburn drops off. */
	static final int RECOVERY_BASE = 60;
	static final int RECOVERY_PER_STAGE = 25;

	static final Identifier FIST_ID = JaymosPowers.id("zfighter_overburn_fists");
	static final Identifier SPEED_ID = JaymosPowers.id("zfighter_overburn_speed");

	/** Active Overburn stage (1..{@link #MAX_STAGE}) synced to all clients for the crimson aura; absent at 0. */
	public static final AttachmentType<Integer> STAGE = AttachmentRegistry.create(JaymosPowers.id("zfighter_overburn_stage"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final class State {
		boolean holding;
		int stage;
		int peakStage;
		int holdCharge;
		int ticksLeft;
		int hurtTimer;
		long lockedUntil;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private ZFighterOverburn() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STATES.clear());
	}

	/** Current synced Overburn stage (0 when inactive) on either side. */
	public static int stage(Player player) {
		Integer s = player.getAttached(STAGE);
		return s == null ? 0 : Math.clamp(s, 0, MAX_STAGE);
	}

	/** Called when the client starts or stops holding Sneak+X. */
	static void hold(ServerPlayer player, boolean down) {
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			return;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		long now = player.level().getGameTime();
		if (down) {
			if (!player.isAlive() || Powers.silenced(player) || ZFighterLineage.ape(player)) {
				return;
			}
			if (now < state.lockedUntil) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_overburn_recovering").withStyle(ChatFormatting.GRAY));
				return;
			}
			if (!player.isCreative() && player.getHealth() <= MIN_HEALTH + 2.0F) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_overburn_hurt").withStyle(ChatFormatting.RED));
				return;
			}
			state.holding = true;
			state.holdCharge = 0;
			if (state.stage == 0) {
				raiseStage(player, state);
			}
		} else if (state.holding) {
			state.holding = false;
			state.holdCharge = 0;
			if (state.stage > 0) {
				state.ticksLeft = HOLD_TICKS[state.stage];
			}
		}
	}

	/** Per-tick update from {@link ZFighterPower#tick}. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		State state = STATES.get(player.getUUID());
		if (state == null || (state.stage == 0 && !state.holding)) {
			return;
		}
		if (!player.isAlive() || Powers.silenced(player) || ZFighterLineage.ape(player)) {
			end(player, live, false);
			return;
		}
		ServerLevel level = player.level();
		if (state.holding) {
			if (state.stage < MAX_STAGE) {
				state.holdCharge++;
				if (state.holdCharge >= RAISE_TICKS) {
					state.holdCharge = 0;
					raiseStage(player, state);
				}
			}
			state.ticksLeft = HOLD_TICKS[state.stage];
		} else {
			state.ticksLeft--;
			if (state.ticksLeft <= 0) {
				end(player, live, true);
				return;
			}
		}
		// drain ki gently while burning
		if (!player.isCreative() && !live.raging()) {
			live.ki = Math.max(0.0F, live.ki - KI_DRAIN[state.stage]);
			if (live.ki <= 0.0F) {
				end(player, live, true);
				return;
			}
		}
		// above half (stages 3 and 4), periodic strain damage
		if (state.stage > HURT_ABOVE) {
			state.hurtTimer++;
			if (state.hurtTimer >= HURT_EVERY[state.stage]) {
				state.hurtTimer = 0;
				if (!player.isCreative()) {
					float dmg = HURT_AMOUNT[state.stage];
					if (player.getHealth() - dmg <= MIN_HEALTH) {
						end(player, live, true);
						return;
					}
					player.hurtServer(level, level.damageSources().magic(), dmg);
					MagicFx.burst(level, player.getX(), player.getY() + 1.0, player.getZ(), COLOR, 1.3F, 12, 0.4, 0.6, 0.4);
				}
			}
		}
		// rising red aura wisps
		if (level.getGameTime() % 3 == 0) {
			level.sendParticles(MagicFx.wisp(COLOR, 1.0F + 0.25F * state.stage), true, false,
				player.getX(), player.getY() + 0.9, player.getZ(),
				state.stage * 2, 0.4 + 0.08 * state.stage, 0.7, 0.4 + 0.08 * state.stage, 0.05);
		}
	}

	private static void raiseStage(ServerPlayer player, State state) {
		if (state.stage >= MAX_STAGE) {
			return;
		}
		state.stage++;
		state.peakStage = Math.max(state.peakStage, state.stage);
		state.ticksLeft = HOLD_TICKS[state.stage];
		state.hurtTimer = 0;
		player.setAttached(STAGE, state.stage);
		ServerLevel level = player.level();
		MagicFx.ring(level, player.position().add(0.0, 0.15, 0.0), 2.2F + 0.75F * state.stage, COLOR);
		MagicFx.blast(level, player.getX(), player.getY() + 1.0, player.getZ(), 1.2F + 0.35F * state.stage, COLOR);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
			0.75F + 0.15F * state.stage, 0.7F + 0.22F * state.stage);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
			0.6F, 1.1F + 0.18F * state.stage);
		Shakes.send(player, 0.2F + 0.14F * state.stage, player.position());
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_overburn_stage", state.stage).withStyle(ChatFormatting.RED));
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	private static void end(ServerPlayer player, ZFighterPower.Live live, boolean withRecovery) {
		State state = STATES.get(player.getUUID());
		int peak = state == null ? 0 : state.peakStage;
		if (state != null) {
			state.holding = false;
			state.stage = 0;
			state.peakStage = 0;
			state.holdCharge = 0;
			state.ticksLeft = 0;
			state.hurtTimer = 0;
		}
		if (player.hasAttached(STAGE)) {
			player.removeAttached(STAGE);
		}
		if (withRecovery && peak > 0) {
			int slowTicks = RECOVERY_BASE + RECOVERY_PER_STAGE * peak;
			if (state != null) {
				state.lockedUntil = player.level().getGameTime() + slowTicks;
			}
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slowTicks, peak >= 3 ? 1 : 0));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RECOVERY_BASE, 0));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_overburn_spent").withStyle(ChatFormatting.GRAY));
		}
		ZFighterPower.refresh(player, live);
	}

	/** Applies Overburn's fist and speed bonuses inside {@link ZFighterPower#refresh}. */
	static void attributes(ServerPlayer player) {
		int s = stage(player);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FIST_ID, FIST_BONUS[s], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_BONUS[s], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Ki technique damage multiplier for active Overburn. */
	static float kiFactor(ServerPlayer player) {
		return KI_BONUS[stage(player)];
	}

	/** Removes Overburn state and modifiers (on power loss, death or disconnect). */
	static void clear(ServerPlayer player) {
		STATES.remove(player.getUUID());
		if (player.hasAttached(STAGE)) {
			player.removeAttached(STAGE);
		}
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FIST_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}
}
