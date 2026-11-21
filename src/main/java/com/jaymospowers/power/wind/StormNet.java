package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Stormbound's own keys, and the one place they are routed: draw from the sky ({@link StormDraw}), throw a hammer
 * ({@link StormHammer}), call a weapon back ({@link StormRecall}), bring two storm weapons together ({@link StormTwin}),
 * the storm landing ({@link StormLanding}) and the storm road ({@link StormRoad}). The client sends each key's press (and
 * release, for the two held ones); everything else is decided here.
 */
public final class StormNet {
	public static final int DRAW = 0, HURL = 1, RECALL = 2, TWIN = 3, LAND = 4, ROAD = 5;
	/** Bits of {@link Hud#flags}. */
	public static final int BOUND = 1, MARKED = 2, ARMED = 4, SPINNING = 8, CHARGING = 16;

	/** Client to server: a storm key went down (or up). Sneak is read from the player on the server. */
	public record Key(int action, boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("wind_storm_key"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Key> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.action);
				buf.writeBoolean(p.down);
			},
			buf -> new Key(buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	/**
	 * Server to client, to the player themselves: the gauge ({@code charge} 0-100 drawn; {@code storm} the storm state's
	 * ticks left when it is on), the hammer's throw mode (0 lightning, 1 ride, 2 spin) and what is bound or armed.
	 */
	public record Hud(int charge, int storm, int mode, int flags) implements CustomPacketPayload {
		public static final Type<Hud> TYPE = new Type<>(JaymosPowers.id("wind_storm_hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.charge);
				buf.writeVarInt(p.storm);
				buf.writeVarInt(p.mode);
				buf.writeVarInt(p.flags);
			},
			buf -> new Hud(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Hud> type() {
			return TYPE;
		}
	}

	/** Until when (game time) a player's falls hurt in full, whatever Wind's mastery says (the storm's leaps and rides). */
	private static final Map<UUID, Long> HARD_FALL = new HashMap<>();
	private static final Map<UUID, Hud> SENT = new HashMap<>();

	private StormNet() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Hud.TYPE, Hud.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, ctx) -> onKey(ctx.player(), payload));
	}

	/** A Wind user with the Stormbound (or Thunderhead) mode on. */
	static boolean ready(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && Powers.has(player, WindPower.INSTANCE) && WindModes.storm(player);
	}

	static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.wind_storm." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static void onKey(ServerPlayer player, Key key) {
		if (!ready(player)) {
			if (!key.down) {
				StormDraw.hold(player, false);
				StormHammer.release(player);
			}
			return;
		}
		switch (key.action) {
			case DRAW -> StormDraw.hold(player, key.down);
			case HURL -> {
				if (key.down) {
					StormHammer.press(player);
				} else {
					StormHammer.release(player);
				}
			}
			case RECALL -> {
				if (key.down) {
					StormRecall.press(player);
				}
			}
			case TWIN -> {
				if (key.down) {
					StormTwin.press(player);
				}
			}
			case LAND -> {
				if (key.down) {
					StormLanding.press(player);
				}
			}
			case ROAD -> {
				if (key.down) {
					StormRoad.press(player);
				}
			}
			default -> {
			}
		}
	}

	/** Falls hurt in full for this long (ticks): fall damage always applies to what the storm throws a player into. */
	static void hardFall(ServerPlayer player, int ticks) {
		HARD_FALL.put(player.getUUID(), player.level().getGameTime() + ticks);
	}

	/** Whether this player's falls hurt in full right now ({@link WindFall#damage} asks). */
	static boolean hardFall(ServerPlayer player) {
		Long until = HARD_FALL.get(player.getUUID());
		return until != null && until >= player.level().getGameTime();
	}

	/** Every tick, per Wind user. */
	static void tick(ServerPlayer player) {
		if (!ready(player)) {
			if (SENT.containsKey(player.getUUID())) {
				forget(player);
				ServerPlayNetworking.send(player, new Hud(0, 0, 0, 0));
			}
			return;
		}
		StormDraw.tick(player);
		StormHammer.tick(player);
		StormRecall.tick(player);
		StormLanding.tick(player);
		StormRoad.tick(player);
		Hud hud = new Hud(StormDraw.charge(player), StormDraw.stormLeft(player), StormHammer.mode(player), flags(player));
		if (!hud.equals(SENT.put(player.getUUID(), hud))) {
			ServerPlayNetworking.send(player, hud);
		}
	}

	private static int flags(ServerPlayer player) {
		int flags = 0;
		flags |= StormRecall.bound(player) ? BOUND : 0;
		flags |= StormRoad.marked(player) ? MARKED : 0;
		flags |= StormLanding.armed(player) ? ARMED : 0;
		flags |= StormHammer.spinning(player) ? SPINNING : 0;
		flags |= StormHammer.charging(player) ? CHARGING : 0;
		return flags;
	}

	/** The player left: the per-player memory that outlives a mode going off goes too. */
	static void disconnected(ServerPlayer player) {
		HARD_FALL.remove(player.getUUID());
		StormHammer.disconnected(player);
	}

	/** The mode went off, the power went, or the player left: everything the storm held lets go. */
	static void forget(ServerPlayer player) {
		StormDraw.forget(player);
		StormHammer.forget(player);
		StormRecall.forget(player);
		StormLanding.forget(player);
		StormRoad.forget(player);
		SENT.remove(player.getUUID());
	}
}
