package com.jaymospowers.core;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) Trading powers, through the Bonding Earrings (Andy: "done through the fusion earrings"). Two holders
 * standing close, each with an earring in hand: sneak and use yours facing the other to hold it out for a trade, and each
 * says yes twice (the offer and its answer, then once more to be sure) within {@link #OFFER_TICKS} of the last. Then both
 * earrings crack and the powers change hands; mastery starts again from the first ability, and fate (ascended, corrupted)
 * starts fresh. Not Z Fighters only: any two with a power. The earrings bind two souls for the fusion; held out without the
 * dance, the bond carries what woke in each of them across to the other.
 */
public final class PowerTrade {
	/** How near they stand, how squarely they face (the look's dot), and how long a yes waits for the next. */
	static final double RANGE = 5.0, AIM = 0.8;
	static final int OFFER_TICKS = 300;

	/** A holder's yes: to whom, when, and how many times they've said it (1 or 2). */
	private record Offer(UUID to, long at, int times) {
	}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	private PowerTrade() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> OFFERS.clear());
	}

	/** The holder {@code player} faces within {@link #RANGE}, the squarest first. */
	private static @Nullable ServerPlayer facing(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		ServerPlayer best = null;
		double bestDot = AIM;
		for (ServerPlayer other : player.level().players()) {
			if (other == player || !other.isAlive() || other.isSpectator() || other.distanceTo(player) > RANGE) {
				continue;
			}
			double dot = look.dot(other.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize());
			if (dot > bestDot) {
				bestDot = dot;
				best = other;
			}
		}
		return best;
	}

	private static @Nullable InteractionHand holding(ServerPlayer player, Item earring) {
		if (player.getMainHandItem().is(earring)) {
			return InteractionHand.MAIN_HAND;
		}
		return player.getOffhandItem().is(earring) ? InteractionHand.OFF_HAND : null;
	}

	/** {@code player} sneak-used a Bonding Earring: a yes to trading powers with the one they face. */
	public static void offer(ServerPlayer player, Item earring) {
		long now = player.level().getGameTime();
		Power mine = Powers.of(player);
		if (mine == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.no_power").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerPlayer other = facing(player);
		if (other == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.face").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (Powers.of(other) == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.them", other.getDisplayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		Offer was = OFFERS.get(player.getUUID());
		int times = was != null && was.to().equals(other.getUUID()) && now - was.at() <= OFFER_TICKS ? Math.min(2, was.times() + 1) : 1;
		OFFERS.put(player.getUUID(), new Offer(other.getUUID(), now, times));
		Offer theirs = OFFERS.get(other.getUUID());
		boolean answered = theirs != null && theirs.to().equals(player.getUUID()) && now - theirs.at() <= OFFER_TICKS;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.9F + 0.3F * times);
		if (answered && times >= 2 && theirs.times() >= 2) {
			trade(player, other, earring);
			return;
		}
		if (!answered) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.offered", other.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
			other.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.asked", player.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		} else if (times < 2) {
			for (ServerPlayer one : new ServerPlayer[] {player, other}) {
				one.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.again").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.sure", other.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
			other.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.waiting", player.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** Both said yes twice: the earrings crack and the powers change hands, each starting again from its first ability. */
	private static void trade(ServerPlayer a, ServerPlayer b, Item earring) {
		OFFERS.remove(a.getUUID());
		OFFERS.remove(b.getUUID());
		InteractionHand ha = holding(a, earring), hb = holding(b, earring);
		Power pa = Powers.of(a), pb = Powers.of(b);
		if (ha == null || hb == null || pa == null || pb == null || pa == pb) {
			for (ServerPlayer one : new ServerPlayer[] {a, b}) {
				one.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.failed").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!a.isCreative()) {
			a.getItemInHand(ha).shrink(1);
		}
		if (!b.isCreative()) {
			b.getItemInHand(hb).shrink(1);
		}
		Powers.set(a, null);
		Powers.set(b, null);
		Powers.set(a, pb);
		Powers.set(b, pa);
		for (ServerPlayer one : new ServerPlayer[] {a, b}) {
			Power now = Powers.of(one);
			if (now == null) {
				continue;
			}
			Mastery.startFresh(one);
			ServerLevel level = one.level();
			MagicFx.ring(level, one.position().add(0.0, 0.1, 0.0), 1.5F, now.color());
			level.playSound(null, one.getX(), one.getY(), one.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.5F, 0.8F);
			level.playSound(null, one.getX(), one.getY(), one.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
			Component name = now.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(now.color())));
			one.sendOverlayMessage(Component.translatable("message.jaymospowers.trade.done", name).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}
}
