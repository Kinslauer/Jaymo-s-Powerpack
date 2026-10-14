package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** The power registry, who holds which power, and the survival awakening. */
public final class Powers {
	private static final Map<Identifier, Power> ALL = new LinkedHashMap<>();

	/** The player's power, if they have one. Saved, kept through death, and synced to every client (so others can see it). */
	public static final AttachmentType<Identifier> POWER = AttachmentRegistry.create(JaymosPowers.id("power"),
		builder -> builder.persistent(Identifier.CODEC).copyOnDeath().syncWith(Identifier.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Survival ticks left until a powerless player awakens. Rolled the first time they're seen. */
	public static final AttachmentType<Integer> AWAKEN_IN = AttachmentRegistry.create(JaymosPowers.id("awaken_in"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/**
	 * Marks a countdown rolled with the 3-5 day timing. A countdown saved before it (rolled at 1-3 days) is stretched
	 * once by {@link #AWAKEN_STRETCH} so nobody awakens sooner than the new minimum.
	 */
	public static final AttachmentType<Boolean> AWAKEN_SLOW = AttachmentRegistry.create(JaymosPowers.id("awaken_slow"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Awakening comes after 3-5 Minecraft days (60-100 minutes) of survival play. */
	public static final int AWAKEN_MIN_TICKS = 72000;
	public static final int AWAKEN_MAX_TICKS = 120000;
	/** Added once to a countdown from the old 1-3 day timing (the difference between the old and new minimum). */
	private static final int AWAKEN_STRETCH = 48000;

	/**
	 * Checks that switch a player's power keys off for now (Resonance's Hush). Only presses are stopped: releases still
	 * go through, so nothing is left held.
	 */
	private static final List<java.util.function.Predicate<ServerPlayer>> SILENCERS = new ArrayList<>();

	private Powers() {
	}

	/** Adds a check that can switch a player's power keys off (see {@link #silenced}). */
	public static void addSilencer(java.util.function.Predicate<ServerPlayer> silencer) {
		SILENCERS.add(silencer);
	}

	/** Whether a press of {@code player}'s power keys (R G V B, Z, C) does nothing right now. */
	public static boolean silenced(ServerPlayer player) {
		for (java.util.function.Predicate<ServerPlayer> silencer : SILENCERS) {
			if (silencer.test(player)) {
				return true;
			}
		}
		return false;
	}

	public static <P extends Power> P register(P power) {
		if (ALL.putIfAbsent(power.id(), power) != null) {
			throw new IllegalStateException("power registered twice: " + power.id());
		}
		return power;
	}

	public static Collection<Power> all() {
		return Collections.unmodifiableCollection(ALL.values());
	}

	/** The powers fate can hand out ({@link Power#fated}): for random awakenings and the affinity choice. */
	public static List<Power> fated() {
		return ALL.values().stream().filter(Power::fated).toList();
	}

	public static @Nullable Power get(@Nullable Identifier id) {
		return id == null ? null : ALL.get(id);
	}

	/** By id path (e.g. "wind"), for commands. */
	public static @Nullable Power byName(String name) {
		return get(Identifier.tryParse(name.contains(":") ? name : JaymosPowers.MOD_ID + ":" + name));
	}

	/** The player's power, or null. Works on both sides (the attachment is synced). */
	public static @Nullable Power of(Player player) {
		return get(player.getAttached(POWER));
	}

	public static boolean has(Player player, Power power) {
		return of(player) == power;
	}

	/** Gives the player {@code power} (or takes theirs away, for null). */
	public static void set(ServerPlayer player, @Nullable Power power) {
		Power old = of(player);
		if (old == power) {
			return;
		}
		if (old != null) {
			old.onLost(player);
		}
		// a power given by hand (creative, /power set) comes complete; awaken() narrows it back to one ability
		Mastery.unlockAll(player);
		// a new power starts its fate fresh (not ascended, not corrupted)
		Fate.reset(player);
		if (power == null) {
			player.removeAttached(POWER);
		} else {
			player.setAttached(POWER, power.id());
			player.removeAttached(AWAKEN_IN);
			player.removeAttached(AWAKEN_SLOW);
			player.removeAttached(Callings.CALLING);
			player.removeAttached(Callings.WEAPON_KILLS);
			player.removeAttached(Callings.FIST_KILLS);
			power.onGained(player);
		}
	}

	/** Awakens a random power (a different one, if they already have one), with the full fanfare. */
	public static void awaken(ServerPlayer player) {
		List<Power> choices = new ArrayList<>(fated());
		Power current = of(player);
		if (choices.size() > 1 && current != null) {
			choices.remove(current);
		}
		if (choices.isEmpty()) {
			return;
		}
		// a power that called them (Callings) always wins (the only way to an unfated power); then their affinity's,
		// almost always; otherwise fate decides
		Power called = Callings.power(player);
		Power leaning = Affinity.power(player);
		Power power = called != null && called != current ? called
			: leaning != null && leaning != current && player.getRandom().nextFloat() < Affinity.CHANCE
			? leaning : choices.get(player.getRandom().nextInt(choices.size()));
		set(player, power);
		// a natural awakening starts small: the rest of the abilities come with mastery
		Mastery.startFresh(player);
		Component name = power.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(power.color())).withBold(true));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.jaymospowers.awakened")));
		player.connection.send(new ClientboundSetSubtitleTextPacket(name));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.awakened_chat", name)
			.append(" ").append(Component.translatable("message.jaymospowers.open_menu").withStyle(ChatFormatting.GRAY)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.4, 0.8, 0.4, 0.15);
	}

	static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Power power = of(player);
			if (power != null) {
				// a fall is measured from its highest point: anything still moving up isn't falling yet (so a swing's or
				// a hover's ups and downs don't pile up into one huge fall)
				if (!player.onGround() && player.getY() > player.yo + 1.0E-3) {
					player.resetFallDistance();
				}
				power.tick(player);
				Mastery.tick(player);
				continue;
			}
			// powerless: count down survival play towards the awakening
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			Affinity.tick(player);
			Callings.tick(player);
			if (of(player) != null) {
				continue;
			}
			Integer left = player.getAttached(AWAKEN_IN);
			if (left == null) {
				RandomSource random = player.getRandom();
				player.setAttached(AWAKEN_IN, AWAKEN_MIN_TICKS + random.nextInt(AWAKEN_MAX_TICKS - AWAKEN_MIN_TICKS + 1));
				player.setAttached(AWAKEN_SLOW, true);
				continue;
			}
			if (player.getAttached(AWAKEN_SLOW) == null) {
				// rolled under the old 1-3 day timing: stretch it once so it takes at least 3 days in all
				left += AWAKEN_STRETCH;
				player.setAttached(AWAKEN_SLOW, true);
			}
			left--;
			if (left <= 0) {
				awaken(player);
				continue;
			}
			player.setAttached(AWAKEN_IN, left);
			if (left == 6000 || left == 1200) {
				// foreshadowing: 5 minutes and 1 minute out
				player.sendOverlayMessage(Component.translatable(left == 6000 ? "message.jaymospowers.stirring" : "message.jaymospowers.stirring_soon")
					.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 0.5F);
			}
		}
	}

	static void key(ServerPlayer player, PowerKey key, boolean down) {
		Power power = of(player);
		if (power != null && !Mastery.isUsable(player, key)) {
			if (down) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.locked").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (down && power != null && silenced(player)) {
			return;
		}
		PowerInput.set(player, key, down);
		if (power != null && player.isAlive() && !player.isSpectator()) {
			power.onKey(player, key, down);
			if (down) {
				Mastery.used(player);
				Oddities.hold(player, Oddities.POWER + power.id().getPath(), Oddities.POWER_SEEN_TICKS);
			}
		}
	}

	/** The Power Mode key (Z): each power decides what it does (Web: Zip; Wind: its storm or tempest mode). */
	static void mode(ServerPlayer player, boolean down) {
		Power power = of(player);
		if (down && power != null && silenced(player)) {
			return;
		}
		if (power != null && player.isAlive() && !player.isSpectator()) {
			power.onMode(player, down);
			if (down) {
				Oddities.hold(player, Oddities.POWER + power.id().getPath(), Oddities.POWER_SEEN_TICKS);
			}
		}
	}

	/** The ascended ability's key: only an ascended power answers it (anyone else's press does nothing at all). */
	static void ultimate(ServerPlayer player, boolean down) {
		Power power = of(player);
		if (down && power != null && silenced(player)) {
			return;
		}
		if (power != null && (Fate.isAscended(player) || power.ultimateWhenCorrupted() && Fate.isCorrupted(player)) && player.isAlive()
			&& !player.isSpectator()) {
			power.onUltimate(player, down);
			if (down) {
				Oddities.hold(player, Oddities.POWER + power.id().getPath(), Oddities.POWER_SEEN_TICKS);
			}
		}
	}

	/** From the powers screen: creative players (and operators) may pick any power, or none. */
	static void choose(ServerPlayer player, String id) {
		if (!player.isCreative() && !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.creative_only"));
			return;
		}
		if (id.isEmpty()) {
			set(player, null);
			return;
		}
		Power power = get(Identifier.tryParse(id));
		// the screen doesn't offer a power fate can't give (the band only, for Xenoform); /power set still does
		if (power != null && power.fated()) {
			set(player, power);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chosen", power.name()));
		}
	}
}
