package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Where a power is headed, beyond mastery: it can <b>ascend</b> (a new form and an ascended ability on its own key,
 * reached through each power's own milestone or item) and it can be <b>corrupted</b> (each power fills its own meter in
 * its own way; a full meter corrupts it). A corrupted power can be <b>purified</b> only by a specific circumstance each
 * power defines, and a purified power can never be corrupted again. Ascended and corrupted are independent: a power can
 * be both.
 *
 * <p>Everything here is discovered in play, so the messages are deliberately vague. Saved, kept through death, reset
 * when the power changes, and synced to every client (others see forms and auras).
 */
public final class Fate {
	/** How full the corruption meter goes before the power corrupts. */
	public static final int MAX_CORRUPTION = 100;

	public record State(boolean ascended, boolean corrupted, boolean purified, int corruption) {
		public static final State NONE = new State(false, false, false, 0);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("ascended", false).forGetter(State::ascended),
				Codec.BOOL.optionalFieldOf("corrupted", false).forGetter(State::corrupted),
				Codec.BOOL.optionalFieldOf("purified", false).forGetter(State::purified),
				Codec.INT.optionalFieldOf("corruption", 0).forGetter(State::corruption))
			.apply(i, State::new));
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, State::ascended,
			ByteBufCodecs.BOOL, State::corrupted,
			ByteBufCodecs.BOOL, State::purified,
			ByteBufCodecs.VAR_INT, State::corruption,
			State::new);
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosPowers.id("fate"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private Fate() {
	}

	public static State state(Player player) {
		State state = player.getAttached(STATE);
		return state == null ? State.NONE : state;
	}

	public static boolean isAscended(Player player) {
		return state(player).ascended();
	}

	public static boolean isCorrupted(Player player) {
		return state(player).corrupted();
	}

	/** Purified: cured of corruption for good. */
	public static boolean isPurified(Player player) {
		return state(player).purified();
	}

	/** The corruption meter, 0-{@link #MAX_CORRUPTION} (full once corrupted). */
	public static int corruption(Player player) {
		State state = state(player);
		return state.corrupted() ? MAX_CORRUPTION : state.corruption();
	}

	/** Whether the meter can still move: has a power, not corrupted yet, never purified. */
	public static boolean canCorrupt(Player player) {
		State state = state(player);
		return Powers.of(player) != null && !state.corrupted() && !state.purified();
	}

	/** A power can ascend once every ability is mastered (creative/command powers come mastered). */
	public static boolean canAscend(Player player) {
		return Powers.of(player) != null && !isAscended(player) && Mastery.unlocked(player) >= Mastery.UNLOCK_AT.length;
	}

	/**
	 * Moves the corruption meter by {@code amount} (negative eases it). Fills to {@link #MAX_CORRUPTION} → corrupted.
	 * Does nothing once corrupted or purified. Creative players are spared.
	 */
	public static void addCorruption(ServerPlayer player, int amount) {
		if (amount == 0 || !canCorrupt(player) || player.isCreative()) {
			return;
		}
		State state = state(player);
		int meter = Mth.clamp(state.corruption() + amount, 0, MAX_CORRUPTION);
		if (meter == state.corruption()) {
			return;
		}
		if (meter >= MAX_CORRUPTION) {
			corrupt(player);
			return;
		}
		player.setAttached(STATE, new State(state.ascended(), false, false, meter));
	}

	/** Corrupts the player's power outright (the meter fills). Returns false if it can't be (purified, or already). */
	public static boolean corrupt(ServerPlayer player) {
		Power power = Powers.of(player);
		if (power == null || !canCorrupt(player)) {
			return false;
		}
		State state = state(player);
		player.setAttached(STATE, new State(state.ascended(), true, false, MAX_CORRUPTION));
		power.onCorrupted(player);
		title(player, Component.translatable("message.jaymospowers.corrupted"), Component.translatable("message.jaymospowers.corrupted_sub"),
			0x6A2A8A);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		player.level().sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.8, 0.4, 0.05);
		return true;
	}

	/** Cures a corrupted power for good: it can never be corrupted again. Returns false if it wasn't corrupted. */
	public static boolean purify(ServerPlayer player) {
		Power power = Powers.of(player);
		State state = state(player);
		if (power == null || !state.corrupted()) {
			return false;
		}
		player.setAttached(STATE, new State(state.ascended(), false, true, 0));
		power.onPurified(player);
		title(player, Component.translatable("message.jaymospowers.purified"), Component.translatable("message.jaymospowers.purified_sub"),
			0xFFFFFF);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.2F);
		player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.5, 1.0, 0.5, 0.1);
		return true;
	}

	/** Ascends the player's power. Counts as a level-up (see {@link Power#onLevelUp}). Returns false if it already is. */
	public static boolean ascend(ServerPlayer player) {
		Power power = Powers.of(player);
		State state = state(player);
		if (power == null || state.ascended()) {
			return false;
		}
		player.setAttached(STATE, new State(true, state.corrupted(), state.purified(), state.corruption()));
		power.onAscended(player);
		title(player, Component.translatable("message.jaymospowers.ascended"), power.name(), power.color());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.level().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.5, 1.0, 0.5, 0.4);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.ascended_chat", Component.keybind("key.jaymospowers.ultimate"))
			.withStyle(ChatFormatting.GOLD));
		power.onLevelUp(player, Mastery.UNLOCK_AT.length + 1);
		return true;
	}

	/** Back to nothing (the power changed, or a command). */
	public static void reset(ServerPlayer player) {
		player.removeAttached(STATE);
	}

	/** For commands: sets the state directly, without fanfare or hooks. */
	public static void set(ServerPlayer player, State state) {
		player.setAttached(STATE, state);
	}

	private static void title(ServerPlayer player, Component title, Component subtitle, int color) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(title.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color)))));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
	}
}
