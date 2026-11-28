package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.HammerSlam;
import io.netty.buffer.ByteBuf;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Andy (2026-11-28, after playing Combo Clash, whose block is on F): "move blocking to f as well". The guard key (F,
 * {@code client.combat.GuardKeyClient}) raises a guard; right click no longer does. Held, it raises whatever in your hands
 * blocks ({@link DataComponents#BLOCKS_ATTACKS}): a shield in the off hand first, else the weapon in the main hand, else
 * one in the off hand. The guard, the parry and the perfect parry are {@link Guard}'s as before: the raise is still
 * vanilla's own blocking, only started from F. Darktide's guard goes on F too ({@code DarktidePowerClient}).
 * <p>
 * Right click still does everything else a blocking item does on it (a warhammer's smash, Sora's Dagger's Flurry, a
 * knife's sneaking throw, a lance's couch, the off hand's turn). A guard that comes up any other way than from F (an
 * item's own use putting it up while its move cools down, say) is let down again on both sides ({@link #held}).
 * In Combo Clash's combat mode F is its block, and ours stays down.
 */
public final class GuardKey {
	/** Who holds the guard key down (server side; the client keeps its own in {@code GuardKeyClient}). */
	private static final Set<UUID> HELD = ConcurrentHashMap.newKeySet();
	/** The local player's key, for the client side's checks ({@code GuardKeyClient} keeps it). */
	public static volatile boolean localHeld;

	public record Held(boolean down) implements CustomPacketPayload {
		public static final Type<Held> TYPE = new Type<>(JaymosPowers.id("guard_key"));
		public static final StreamCodec<ByteBuf, Held> CODEC = ByteBufCodecs.BOOL.map(Held::new, Held::down);

		@Override
		public Type<Held> type() {
			return TYPE;
		}
	}

	private GuardKey() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Held.TYPE, Held.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Held.TYPE, (payload, ctx) -> {
			if (payload.down()) {
				HELD.add(ctx.player().getUUID());
			} else {
				HELD.remove(ctx.player().getUUID());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (strayGuard(player)) {
					player.stopUsingItem();
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HELD.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> HELD.clear());
	}

	/** Whether {@code player} holds the guard key (both sides: the local player's on the client). */
	public static boolean held(Player player) {
		return player.level().isClientSide() ? localHeld : HELD.contains(player.getUUID());
	}

	/** Something in this hand that blocks. */
	public static boolean blocks(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.BLOCKS_ATTACKS);
	}

	/** The hand F raises a guard with: a shield in the off hand, else a blocking main hand, else a blocking off hand; null for none. */
	public static InteractionHand guardHand(Player player) {
		ItemStack off = player.getOffhandItem(), main = player.getMainHandItem();
		if (blocks(off) && !off.has(DataComponents.WEAPON)) {
			return InteractionHand.OFF_HAND;
		}
		if (blocks(main)) {
			return InteractionHand.MAIN_HAND;
		}
		return blocks(off) ? InteractionHand.OFF_HAND : null;
	}

	/**
	 * Asked first of every right click and every guard-key raise (both sides, from {@link Guard}'s use callback): with the
	 * key held, the hand F chose raises its guard, whatever its own right click would do, and the other hand does nothing;
	 * without it, null (the right click goes on as usual, and {@link Guard} keeps a guard from coming up on it).
	 */
	public static InteractionResult use(Player player, InteractionHand hand) {
		if (!held(player)) {
			return null;
		}
		if (hand != guardHand(player)) {
			return InteractionResult.FAIL;
		}
		if (com.jaymospowers.armory.KnifeGuard.guards(player.getItemInHand(hand).getItem())) {
			return com.jaymospowers.armory.KnifeGuard.raise(player, hand);
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/**
	 * A guard up that F didn't raise (both sides): a blocking item in use, showing the block, with the key up. A
	 * warhammer winding up its smash is its own move, not a guard ({@link HammerSlam#charging}).
	 */
	public static boolean strayGuard(Player player) {
		if (!player.isUsingItem() || held(player)) {
			return false;
		}
		ItemStack using = player.getUseItem();
		return blocks(using) && using.getUseAnimation() == ItemUseAnimation.BLOCK && !HammerSlam.charging(player);
	}
}
