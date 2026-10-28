package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.gifted.Gifted;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.108, Andy: "you get a player's power or villager if you absorb them", then "maybe they just get the power's R
 * ability, and keep their power if they have one", and of running it whole behind that one key: "do the other option")
 * A Rubber-folk (Jaymo's Kingdoms' {@code RubberFolk}, by reflection) who swallows a downed player or a gifted villager
 * keeps their own power, and the swallowed one runs in them too ({@link #SWALLOWED}): its tick every tick (its passives
 * come with it), but of its keys only R, on a key of its own ({@link Key}; comma by default, or R itself with no power of
 * their own). Only one at a time: the next swallow replaces it, and it's gone when they die.
 */
public final class SwallowedPower {
	/** The power they swallowed. Saved; lost on death. */
	public static final AttachmentType<Identifier> SWALLOWED = AttachmentRegistry.create(JaymosPowers.id("swallowed_power"),
		builder -> builder.persistent(Identifier.CODEC));

	/** Client → server: the swallowed power's key went down or up. */
	public record Key(boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("swallowed_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = ByteBufCodecs.BOOL.map(Key::new, Key::down);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	/** Players holding the swallowed power's key. */
	private static final Set<UUID> HELD = new HashSet<>();

	private SwallowedPower() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, ctx) -> key(ctx.player(), payload.down()));
		ServerTickEvents.END_SERVER_TICK.register(SwallowedPower::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				drop(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HELD.remove(handler.player.getUUID()));
	}

	/** The power {@code player} swallowed and is running beside their own, or null. */
	public static @Nullable Power of(ServerPlayer player) {
		Power power = Powers.get(player.getAttached(SWALLOWED));
		return power == null || power == Powers.of(player) ? null : power;
	}

	/**
	 * {@code taker} swallowed {@code body}: its power runs in them now, in place of whatever they took before. The power's
	 * name, or null if it had none. Called by Jaymo's Kingdoms (by reflection) before the body dies.
	 */
	public static @Nullable Component take(ServerPlayer taker, LivingEntity body) {
		Power power;
		if (body instanceof ServerPlayer victim) {
			power = Powers.of(victim);
		} else {
			String gift = Gifted.power(body);
			power = gift == null ? null : Powers.byName(gift);
		}
		if (power == null) {
			return null;
		}
		Component name = power.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(power.color())).withBold(true));
		if (power == Powers.of(taker)) {
			taker.sendSystemMessage(Component.translatable("message.jaymospowers.swallowed_power_same", body.getDisplayName(), name)
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			return name;
		}
		drop(taker);
		taker.setAttached(SWALLOWED, power.id());
		power.onGained(taker);
		taker.sendSystemMessage(Component.translatable("message.jaymospowers.swallowed_power", body.getDisplayName(), name)
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		return name;
	}

	/** Whatever they swallowed leaves them (death, or a new swallow). */
	private static void drop(ServerPlayer player) {
		Power power = of(player);
		HELD.remove(player.getUUID());
		if (player.hasAttached(SWALLOWED)) {
			player.removeAttached(SWALLOWED);
		}
		if (power != null) {
			power.onLost(player);
		}
	}

	/**
	 * The swallowed power's key went down or up (also R, for one with no power of their own: {@link Powers}). False if they
	 * hold no swallowed power.
	 */
	static boolean key(ServerPlayer player, boolean down) {
		Power power = of(player);
		if (power == null) {
			HELD.remove(player.getUUID());
			return false;
		}
		if (down && (!player.isAlive() || player.isSpectator() || Downed.isDowned(player) || Powers.silenced(player))) {
			return true;
		}
		if (down) {
			HELD.add(player.getUUID());
		} else {
			HELD.remove(player.getUUID());
		}
		as(player, () -> power.onKey(player, PowerKey.ABILITY_1, down));
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Power power = player.hasAttached(SWALLOWED) ? of(player) : null;
			if (power != null) {
				as(player, () -> power.tick(player));
			}
		}
	}

	/**
	 * Runs {@code run} as the swallowed power sees things: its R held while their key is, and none of their own power's
	 * keys (which are its G, V and B).
	 */
	private static void as(ServerPlayer player, Runnable run) {
		int own = PowerInput.bits(player);
		PowerInput.setBits(player, HELD.contains(player.getUUID()) ? 1 << PowerKey.ABILITY_1.ordinal() : 0);
		try {
			run.run();
		} finally {
			PowerInput.setBits(player, own);
		}
	}

	/** Whether the swallowed power is keeping them up right now (for the server's flying and moving checks). */
	public static boolean allowsFloating(ServerPlayer player) {
		Power power = player.hasAttached(SWALLOWED) ? of(player) : null;
		return power != null && power.allowsFloating(player);
	}

	public static boolean skipsMovementCheck(ServerPlayer player) {
		Power power = player.hasAttached(SWALLOWED) ? of(player) : null;
		return power != null && power.skipsMovementCheck(player);
	}
}
