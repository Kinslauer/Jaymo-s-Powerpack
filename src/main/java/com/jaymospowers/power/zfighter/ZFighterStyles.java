package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Fighting styles (Z Fighter gaps 2): one style a Z Fighter learns from a master (once; a wish to the dragon lets him
 * learn another), on top of his Kingdoms class. Each is a passive:
 * <ul>
 * <li><b>Fury</b>: unbroken hits (each within {@link #FURY_GAP} of the last) stack {@link #FURY_PER_STACK} harder, up to
 * {@link #FURY_STACKS}.</li>
 * <li><b>Flowing Ki</b>: every cooldown {@link #FLOW_COOLDOWN} as long.</li>
 * <li><b>Predator</b>: {@link #PREDATOR_BONUS} more harm to a foe under half health.</li>
 * <li><b>Last Stand</b>: under {@link #LAST_STAND_HEALTH} of his health, he mends {@link #LAST_STAND_HEAL} every
 * {@link #LAST_STAND_EVERY} ticks and lands a {@link #LAST_STAND_CRIT} blow {@link #LAST_STAND_CRIT_CHANCE} of the time.</li>
 * <li><b>Guardian</b>: takes {@link #GUARD_SHARE} of what hurts a player within {@link #GUARD_RANGE} of him on himself.</li>
 * <li><b>Mender</b>: every {@link #MEND_EVERY} ticks, spends {@link #MEND_KI} ki to mend each hurt player near him
 * {@link #MEND_HEAL}.</li>
 * <li><b>Ki and Fist</b>: after a ki technique lands, his next {@link #FIST_PRIMED} punches hit {@link #FIST_BONUS} harder.</li>
 * <li><b>Bulwark</b>: {@link #BULWARK_ARMOR} armour, {@link #BULWARK_KNOCKBACK} knockback resistance, a little slower, and
 * {@link #BULWARK_TAKEN} of every hurt.</li>
 * </ul>
 */
public final class ZFighterStyles {
	public static final String[] KEYS = {"fury", "flow", "predator", "last_stand", "guardian", "mender", "ki_fist", "bulwark"};
	static final int FURY = 0, FLOW = 1, PREDATOR = 2, LAST_STAND = 3, GUARDIAN = 4, MENDER = 5, KI_FIST = 6, BULWARK = 7;

	static final int FURY_GAP = 60, FURY_STACKS = 10;
	static final float FURY_PER_STACK = 0.03F;
	static final float FLOW_COOLDOWN = 0.75F;
	static final float PREDATOR_BONUS = 0.25F;
	static final float LAST_STAND_HEALTH = 0.3F, LAST_STAND_HEAL = 1.0F, LAST_STAND_CRIT = 1.5F, LAST_STAND_CRIT_CHANCE = 0.2F;
	static final int LAST_STAND_EVERY = 40;
	static final float GUARD_SHARE = 0.3F;
	static final double GUARD_RANGE = 6.0;
	static final int MEND_EVERY = 100;
	static final float MEND_KI = 10.0F, MEND_HEAL = 2.0F;
	static final double MEND_RANGE = 6.0;
	static final int FIST_PRIMED = 3;
	static final float FIST_BONUS = 0.4F;
	static final double BULWARK_ARMOR = 4.0, BULWARK_KNOCKBACK = 0.2, BULWARK_SPEED = -0.1;
	static final float BULWARK_TAKEN = 0.85F;

	/** The style he learned ({@link #KEYS}), or none. */
	public static final AttachmentType<Integer> STYLE = AttachmentRegistry.create(JaymosPowers.id("zfighter_style"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private static final Identifier ARMOR_ID = JaymosPowers.id("zfighter_style_armor");
	private static final Identifier KNOCKBACK_ID = JaymosPowers.id("zfighter_style_knockback");
	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_style_speed");

	/** Fury: stacks and the last hit's time. Ki and Fist: punches still primed. */
	private static final Map<UUID, long[]> FURY_STATE = new HashMap<>();
	private static final Map<UUID, Integer> PRIMED = new HashMap<>();
	/** Set while a Guardian takes his share, so his own hurt isn't shared again. */
	private static boolean soaking;

	private ZFighterStyles() {
	}

	/** From {@link ZFighterPower#init}. */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterStyles::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FURY_STATE.clear();
			PRIMED.clear();
		});
	}

	/** His style, or -1: only a Z Fighter's counts. */
	static int of(ServerPlayer player) {
		Integer style = player.getAttached(STYLE);
		return style == null || !Powers.has(player, ZFighterPower.INSTANCE) ? -1 : style;
	}

	static boolean learned(ServerPlayer player) {
		return player.getAttached(STYLE) != null;
	}

	/** A master's offer: every style, to learn one. */
	static void offer(ServerPlayer player, LivingEntity master, Runnable back) {
		List<ZFighterNpcs.Option> options = new ArrayList<>();
		for (int i = 0; i < KEYS.length; i++) {
			int style = i;
			options.add(ZFighterNpcs.Option.of("talk.jaymospowers.master.style." + KEYS[i], true,
				() -> ZFighterNpcs.reply(player, master, () -> learn(player, style), back)));
		}
		ZFighterNpcs.ask(player, master, Component.translatable("talk.jaymospowers.master.style"), options);
	}

	private static void learn(ServerPlayer player, int style) {
		if (learned(player)) {
			return;
		}
		player.setAttached(STYLE, style);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_style_learned",
			Component.translatable("style.jaymospowers.zfighter." + KEYS[style])).withStyle(ChatFormatting.AQUA));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	/** The wish: he forgets his style, to learn another from a master. False if he has none. */
	static boolean forget(ServerPlayer player) {
		if (!learned(player)) {
			return false;
		}
		player.removeAttached(STYLE);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_style_forgotten").withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** (Hook in {@link ZFighterPower#cooldown}) Flowing Ki's shorter cooldowns. */
	static int cooldown(ServerPlayer player, int ticks) {
		return of(player) == FLOW ? Math.round(ticks * FLOW_COOLDOWN) : ticks;
	}

	/** (Hook in {@link ZFighterBattle}) What a Z Fighter's style adds to the harm he does {@code victim}. */
	static float dealt(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
		int style = of(player);
		if (style < 0) {
			return damage;
		}
		boolean punch = source.getDirectEntity() == player && source.is(DamageTypes.PLAYER_ATTACK);
		long now = player.level().getGameTime();
		switch (style) {
			case FURY -> {
				long[] fury = FURY_STATE.computeIfAbsent(player.getUUID(), id -> new long[] {0L, -1000L});
				fury[0] = now - fury[1] <= FURY_GAP ? Math.min(FURY_STACKS, fury[0] + 1) : 0L;
				fury[1] = now;
				damage *= 1.0F + FURY_PER_STACK * fury[0];
			}
			case PREDATOR -> {
				if (victim.getHealth() < victim.getMaxHealth() * 0.5F) {
					damage *= 1.0F + PREDATOR_BONUS;
				}
			}
			case LAST_STAND -> {
				if (player.getHealth() < player.getMaxHealth() * LAST_STAND_HEALTH && player.getRandom().nextFloat() < LAST_STAND_CRIT_CHANCE) {
					damage *= LAST_STAND_CRIT;
				}
			}
			case KI_FIST -> {
				if (!punch) {
					PRIMED.put(player.getUUID(), FIST_PRIMED);
				} else {
					Integer primed = PRIMED.get(player.getUUID());
					if (primed != null) {
						damage *= 1.0F + FIST_BONUS;
						if (primed <= 1) {
							PRIMED.remove(player.getUUID());
						} else {
							PRIMED.put(player.getUUID(), primed - 1);
						}
					}
				}
			}
			default -> {
			}
		}
		return damage;
	}

	/** (Hook in {@link ZFighterBattle}) What a Z Fighter's style takes off the harm done to him. */
	static float taken(ServerPlayer player, float damage) {
		return of(player) == BULWARK ? damage * BULWARK_TAKEN : damage;
	}

	/** (Hook in {@link ZFighterBattle}) A Guardian near a hurt player takes his share of it on himself. */
	static float soak(LivingEntity victim, DamageSource source, float damage) {
		if (soaking || damage <= 0.0F || !(victim instanceof ServerPlayer hurt) || !(hurt.level() instanceof ServerLevel level)) {
			return damage;
		}
		for (ServerPlayer guardian : level.players()) {
			if (guardian != hurt && guardian.isAlive() && !Downed.isDowned(guardian) && guardian != source.getEntity()
				&& guardian.distanceToSqr(hurt) <= GUARD_RANGE * GUARD_RANGE && of(guardian) == GUARDIAN) {
				float share = damage * GUARD_SHARE;
				soaking = true;
				try {
					guardian.hurtServer(level, level.damageSources().magic(), share);
				} finally {
					soaking = false;
				}
				return damage - share;
			}
		}
		return damage;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int style = of(player);
			bulwark(player, style == BULWARK);
			if (style == LAST_STAND && player.tickCount % LAST_STAND_EVERY == 0 && player.isAlive() && !Downed.isDowned(player)
				&& player.getHealth() < player.getMaxHealth() * LAST_STAND_HEALTH) {
				player.heal(LAST_STAND_HEAL);
			}
			if (style == MENDER && player.tickCount % MEND_EVERY == 0 && !Downed.isDowned(player)) {
				mend(player);
			}
		}
	}

	private static void mend(ServerPlayer player) {
		ZFighterPower.Live live = ZFighterPower.live(player);
		for (ServerPlayer friend : player.level().players()) {
			if (live.ki < MEND_KI) {
				return;
			}
			if (friend != player && friend.isAlive() && !Downed.isDowned(friend) && friend.getHealth() < friend.getMaxHealth()
				&& friend.distanceToSqr(player) <= MEND_RANGE * MEND_RANGE) {
				live.ki -= MEND_KI;
				friend.heal(MEND_HEAL);
				player.level().sendParticles(com.jaymospowers.core.fx.MagicFx.wisp(0x9CFFB0, 1.0F), friend.getX(), friend.getY() + 1.0, friend.getZ(), 6,
					0.3, 0.5, 0.3, 0.02);
			}
		}
	}

	private static void bulwark(ServerPlayer player, boolean on) {
		ZFighterPower.set(player, Attributes.ARMOR, ARMOR_ID, on ? BULWARK_ARMOR : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, on ? BULWARK_KNOCKBACK : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, on ? BULWARK_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}
}
