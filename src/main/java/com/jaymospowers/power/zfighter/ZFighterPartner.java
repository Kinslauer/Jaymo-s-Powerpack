package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Find your partner. The two Bonding Earrings of a pair carry the same pair mark ({@link #pair}: a pair made together has the
 * same mark on both, even after the stack of two is split). A Z Fighter who carries one (in a hand or anywhere in his inventory)
 * is told, four times a second, where the other earring's carrier is ({@link #MARK}, sent to him alone): the client outlines
 * the partner through walls while he is within {@link #OUTLINE_RANGE} blocks, and past that points the way on the HUD.
 * Only the one paired partner is ever shown, and only in the same dimension.
 */
public final class ZFighterPartner {
	/** Ticks between looks at who carries what. */
	static final int EVERY = 5;
	/** Within this many blocks the partner is outlined; further, the HUD arrow points the way (the client decides). */
	public static final double OUTLINE_RANGE = 128.0;
	static final String PAIR_KEY = "jaymospowers_pair";

	/** Where the partner is, on the carrier's own screen only. */
	public record Mark(UUID partner, double x, double y, double z) {
		public static final StreamCodec<ByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, Mark::partner,
			ByteBufCodecs.DOUBLE, Mark::x,
			ByteBufCodecs.DOUBLE, Mark::y,
			ByteBufCodecs.DOUBLE, Mark::z,
			Mark::new);
	}

	public static final AttachmentType<Mark> MARK = AttachmentRegistry.create(JaymosPowers.id("zfighter_partner_mark"),
		builder -> builder.syncWith(Mark.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private record Carrier(ServerPlayer player, String pair) {
	}

	private ZFighterPartner() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterPartner::tick);
	}

	/** Marks {@code earrings} (a stack of two made together) as one pair, if it isn't marked yet. */
	static void pair(ItemStack earrings) {
		if (earrings.getCount() >= 2 && pairOf(earrings) == null) {
			CustomData.update(DataComponents.CUSTOM_DATA, earrings, tag -> tag.putString(PAIR_KEY, UUID.randomUUID().toString()));
		}
	}

	private static String pairOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String pair = data.copyTag().getStringOr(PAIR_KEY, "");
		return pair.isEmpty() ? null : pair;
	}

	/** The pair mark of the earring {@code player} carries (marking a stack of two he holds that has none), or null. */
	private static String carried(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ZFighterFusionItems.BONDING_EARRING)) {
				pair(stack);
				String pair = pairOf(stack);
				if (pair != null) {
					return pair;
				}
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		List<Carrier> carriers = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String pair = player.isAlive() && Powers.has(player, ZFighterPower.INSTANCE) ? carried(player) : null;
			if (pair != null) {
				carriers.add(new Carrier(player, pair));
			} else if (player.hasAttached(MARK)) {
				player.removeAttached(MARK);
			}
		}
		for (Carrier me : carriers) {
			ServerPlayer best = null;
			for (Carrier other : carriers) {
				if (other.player() != me.player() && other.pair().equals(me.pair()) && other.player().level() == me.player().level()
					&& (best == null || other.player().distanceToSqr(me.player()) < best.distanceToSqr(me.player()))) {
					best = other.player();
				}
			}
			if (best == null) {
				me.player().removeAttached(MARK);
			} else {
				Mark next = new Mark(best.getUUID(), best.getX(), best.getY(), best.getZ());
				if (!next.equals(me.player().getAttached(MARK))) {
					me.player().setAttached(MARK, next);
				}
			}
		}
	}
}
