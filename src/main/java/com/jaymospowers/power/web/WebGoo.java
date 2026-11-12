package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The symbiote's goo moves: {@link WebPuddle} (melt flat), {@link WebSpit} (spit a glob) and {@link WebWall} (throw up
 * a shell). Each has its own key on the client, which sends {@link Use}; this class checks the common conditions (bonded,
 * awake, not mid-takeover, suit up), keeps the cooldowns and takes the satiety each move costs. The black strain's
 * scent ({@code WebScent}) is client only, a sense.
 */
public final class WebGoo {
	/** The moves, as {@link Use} carries them. */
	public static final int PUDDLE = 0, SPIT = 1, WALL = 2;
	/** Satiety each move costs (a creative player pays nothing). */
	static final int PUDDLE_COST = 1, SPIT_COST = 2, WALL_COST = 5;
	/** Chance the symbiote remarks on a puddle or a wall. */
	static final float REMARK_CHANCE = 0.25F;

	/** Client to server: the key for {@code move} was pressed. */
	public record Use(int move) implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(JaymosPowers.id("web_goo_use"));
		public static final StreamCodec<ByteBuf, Use> CODEC = ByteBufCodecs.VAR_INT.map(Use::new, Use::move);

		@Override
		public Type<Use> type() {
			return TYPE;
		}
	}

	/** Per player, the game time each move is ready again. */
	private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();

	private WebGoo() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, ctx) -> use(ctx.player(), payload.move()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> COOLDOWNS.clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> COOLDOWNS.remove(handler.getPlayer().getUUID()));
		WebPuddle.register();
		WebSpit.register();
		WebWall.register();
	}

	private static void use(ServerPlayer player, int move) {
		if (!usable(player)) {
			return;
		}
		switch (move) {
			case PUDDLE -> WebPuddle.press(player);
			case SPIT -> WebSpit.fire(player);
			case WALL -> WebWall.raise(player);
			default -> {
			}
		}
	}

	/** Bonded, awake, not in a takeover, not held or latched, and the suit or its armour is up. */
	static boolean usable(ServerPlayer player) {
		if (!WebSymbiote.isBonded(player) || !player.isAlive() || player.isSpectator() || WebSymbioteBond.dormant(player)
			|| WebSymbioteStrains.asleep(player) || WebSymbiote.driving(player) || WebHeldGrab.holding(player) || WebLatch.latched(player)) {
			return false;
		}
		if (!WebSymbiote.suitShown(player) && !WebSymbiote.livingArmor(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_goo.no_suit"));
			return false;
		}
		return true;
	}

	/** The big suit, or Living Armor with its mask on: the face that can spit. */
	static boolean maw(Player player) {
		return WebSymbiote.bulked(player) || WebSymbiote.livingArmor(player) && !Boolean.TRUE.equals(player.getAttached(WebSymbiote.UNMASKED));
	}

	static boolean ready(ServerPlayer player, int move) {
		long[] ends = COOLDOWNS.get(player.getUUID());
		return ends == null || player.level().getGameTime() >= ends[move];
	}

	static void cool(ServerPlayer player, int move, int ticks) {
		COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new long[3])[move] = player.level().getGameTime() + ticks;
	}

	/** Takes {@code cost} satiety; false (and it says it is too weak) when there is not enough. */
	static boolean pay(ServerPlayer player, int cost) {
		if (player.isCreative()) {
			return true;
		}
		if (WebSymbiote.state(player).satiety() < cost) {
			WebSymbiote.nag(player, "weak");
			return false;
		}
		WebSymbiote.spendSatiety(player, cost);
		return true;
	}

	/** Sometimes it says something when a move is made. */
	static void remark(ServerPlayer player, String key) {
		if (player.getRandom().nextFloat() < REMARK_CHANCE) {
			WebSymbiote.whisper(player, key);
		}
	}
}
