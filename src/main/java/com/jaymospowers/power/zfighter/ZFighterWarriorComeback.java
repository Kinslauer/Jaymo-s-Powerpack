package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.54, Andy's NOEA picks) The Warrior-born's comeback, which deepens his defeat growth ({@link ZFighterPower#zenkai},
 * doubled for his blood by {@link ZFighterLineage#WARRIOR_ZENKAI}) rather than copying it. Living through a fight at
 * {@link #LOW_HEALTH} of his health or less (at least {@link #MIN_LOW} ticks down there, then {@link #SURVIVE_TICKS} ticks
 * out of that danger and still standing), or being brought round from the downed state, is a comeback: {@link #HEAL} health
 * back, {@link #TP} TP (and {@link #TP_PER_COMEBACK} more for each he has had before), and one more comeback counted. Each
 * counts as an extra defeat for ki and fists ({@link #bonus}) and lifts the ceiling on the defeats that count, so the
 * growth goes past {@link ZFighterPower#ZENKAI_CAP}; at most {@link #CAP} comebacks, one per {@link #COOLDOWN} ticks.
 * Not in creative.
 */
public final class ZFighterWarriorComeback {
	static final float LOW_HEALTH = 0.15F;
	static final int MIN_LOW = 40;
	static final int SURVIVE_TICKS = 100;
	/** How recently a creature must have hurt him for it to be a fight (ticks). */
	static final int IN_FIGHT_TICKS = 100;
	static final int CAP = 10;
	/** Ticks between comebacks (40 minutes). */
	static final long COOLDOWN = 48000L;
	static final float HEAL = 6.0F;
	static final int TP = 40, TP_PER_COMEBACK = 10;
	static final int COLOR = 0xFFC030;

	/** How many comebacks he has had. Saved, kept through death. */
	public static final AttachmentType<Integer> COUNT = AttachmentRegistry.create(JaymosPowers.id("zfighter_comebacks"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** The game time of the last one (the cooldown). Saved, kept through death. */
	public static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosPowers.id("zfighter_comeback_last"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** When he first fell into the danger zone and when he was last in it (game time). */
	private record Low(long since, long last) {
	}

	private static final Map<UUID, Low> LOWS = new HashMap<>();
	/** Who was downed last tick. */
	private static final Set<UUID> DOWN = new HashSet<>();

	private ZFighterWarriorComeback() {
	}

	static void clear() {
		LOWS.clear();
		DOWN.clear();
	}

	static void forget(ServerPlayer player) {
		LOWS.remove(player.getUUID());
		DOWN.remove(player.getUUID());
	}

	/** The extra defeats his comebacks count for, in {@link ZFighterPower#maxKi} and {@link ZFighterPower#fistBonus}. */
	public static int bonus(Player player) {
		return Math.min(CAP, player.getAttachedOrElse(COUNT, 0));
	}

	/** Every tick, per Warrior-born Z Fighter. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		UUID id = player.getUUID();
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
			forget(player);
			return;
		}
		long now = player.level().getGameTime();
		if (Downed.isDowned(player)) {
			DOWN.add(id);
			LOWS.remove(id);
			return;
		}
		if (DOWN.remove(id)) {
			// brought round from down: a comeback of its own
			award(player, now, true);
			return;
		}
		boolean inFight = player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() <= IN_FIGHT_TICKS;
		boolean low = player.getHealth() / player.getMaxHealth() <= LOW_HEALTH;
		Low state = LOWS.get(id);
		if (low && inFight) {
			LOWS.put(id, new Low(state == null ? now : state.since(), now));
			return;
		}
		if (state != null && !low && now - state.last() >= SURVIVE_TICKS) {
			LOWS.remove(id);
			if (state.last() - state.since() >= MIN_LOW) {
				award(player, now, false);
			}
		} else if (state != null && now - state.last() >= SURVIVE_TICKS * 12) {
			LOWS.remove(id);
		}
	}

	private static void award(ServerPlayer player, long now, boolean revived) {
		int count = player.getAttachedOrElse(COUNT, 0);
		Long last = player.getAttached(LAST);
		if (count >= CAP || last != null && now - last < COOLDOWN && now >= last) {
			return;
		}
		player.setAttached(COUNT, count + 1);
		player.setAttached(LAST, now);
		ZFighterTraining.grant(player, TP + TP_PER_COMEBACK * count);
		player.heal(HEAL);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
		ServerLevel level = player.level();
		level.sendParticles(new DustParticleOptions(COLOR, 1.8F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.6, 0.9, 0.6, 0.15);
		level.sendParticles(ParticleTypes.END_ROD, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.8, 0.4, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
		Shakes.send(player, 0.6F);
		player.sendOverlayMessage(Component.translatable(revived ? "message.jaymospowers.zfighter_comeback_up" : "message.jaymospowers.zfighter_comeback")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}
}
