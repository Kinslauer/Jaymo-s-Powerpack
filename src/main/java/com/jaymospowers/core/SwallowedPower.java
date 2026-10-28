package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.gifted.Gifted;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.103, Andy: "you get a player's power or villager if you absorb them") A Rubber-folk (Jaymo's Kingdoms'
 * {@code RubberFolk}, by reflection) who swallows a downed player or a gifted villager takes on their power: a player's
 * as far as they had mastered it, a villager's whole. Only one at a time: their own is kept aside ({@link #OWN}) while
 * they hold someone else's, the next swallow replaces it, and on death they wake with their own again.
 */
public final class SwallowedPower {
	/** What they had of their own before they took another's: their power (none: empty), mastery and fate. */
	record Own(Optional<Identifier> power, Optional<Integer> unlocked, Optional<Integer> points, Optional<Fate.State> fate) {
		static final Codec<Own> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.optionalFieldOf("power").forGetter(Own::power),
			Codec.INT.optionalFieldOf("unlocked").forGetter(Own::unlocked),
			Codec.INT.optionalFieldOf("points").forGetter(Own::points),
			Fate.State.CODEC.optionalFieldOf("fate").forGetter(Own::fate)
		).apply(i, Own::new));
	}

	/** Set while they hold a swallowed power: their own, to have back. Kept through death (it's given back on respawn). */
	public static final AttachmentType<Own> OWN = AttachmentRegistry.create(JaymosPowers.id("swallowed_own"),
		builder -> builder.persistent(Own.CODEC).copyOnDeath());

	private SwallowedPower() {
	}

	public static void register() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && newPlayer.hasAttached(OWN)) {
				giveBack(newPlayer);
			}
		});
	}

	/**
	 * {@code taker} swallowed {@code body}: they take on its power, in place of whatever they took before. The power's
	 * name, or null if it had none. Called by Jaymo's Kingdoms (by reflection) before the body dies.
	 */
	public static @Nullable Component take(ServerPlayer taker, LivingEntity body) {
		Power power;
		int unlocked = -1, points = 0;
		if (body instanceof ServerPlayer victim) {
			power = Powers.of(victim);
			Integer u = victim.getAttached(Mastery.UNLOCKED);
			if (u != null) {
				unlocked = u;
				points = Mastery.points(victim);
			}
		} else {
			String gift = Gifted.power(body);
			power = gift == null ? null : Powers.byName(gift);
		}
		if (power == null) {
			return null;
		}
		Own own = taker.getAttached(OWN);
		if (own != null && own.power().map(power.id()::equals).orElse(false)) {
			// swallowing their own power back: it's theirs again, as they had it
			giveBack(taker);
		} else if (own != null || Powers.of(taker) != power) {
			if (own == null) {
				Power mine = Powers.of(taker);
				taker.setAttached(OWN, new Own(Optional.ofNullable(mine == null ? null : mine.id()), Optional.ofNullable(taker.getAttached(Mastery.UNLOCKED)),
					Optional.ofNullable(taker.getAttached(Mastery.POINTS)), Optional.ofNullable(taker.getAttached(Fate.STATE))));
			}
			// (set() swaps the kits over and hands it on whole; a player's comes as far as they had it)
			Powers.set(taker, power);
			if (unlocked >= 0) {
				taker.setAttached(Mastery.UNLOCKED, unlocked);
				taker.setAttached(Mastery.POINTS, points);
			}
		}
		Component name = power.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(power.color())).withBold(true));
		taker.sendSystemMessage(Component.translatable("message.jaymospowers.swallowed_power", name, body.getDisplayName())
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		return name;
	}

	/** Their own power back, as they had it, and nothing swallowed held any more. */
	private static void giveBack(ServerPlayer player) {
		Own own = player.removeAttached(OWN);
		if (own == null) {
			return;
		}
		Powers.set(player, own.power().map(Powers::get).orElse(null));
		own.unlocked().ifPresentOrElse(u -> player.setAttached(Mastery.UNLOCKED, u), () -> player.removeAttached(Mastery.UNLOCKED));
		own.points().ifPresentOrElse(p -> player.setAttached(Mastery.POINTS, p), () -> player.removeAttached(Mastery.POINTS));
		own.fate().ifPresentOrElse(f -> Fate.set(player, f), () -> Fate.reset(player));
		if (own.power().isPresent()) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.swallowed_power_back").withStyle(ChatFormatting.GRAY));
		}
	}
}
