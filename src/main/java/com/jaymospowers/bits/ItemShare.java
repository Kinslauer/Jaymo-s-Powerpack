package com.jaymospowers.bits;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.KingdomsCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Shift+T over an item in a container screen shares it in chat with its hover tooltip. The client sends only which slot of
 * which open menu; the server reads the stack from its own copy of the menu (so nobody can show an item they don't have),
 * builds the line with the item's hover component, and says it the way typed chat is heard: with Jaymo's Kingdoms'
 * proximity chat only to players within {@link #RANGE} (heard as the sharer's name as each hearer knows them, "Stranger"
 * until introduced), without it to everyone.
 */
public final class ItemShare {
	/** How far a shared item is seen with proximity chat (the same as talk's full range), and the least ticks between two shares by one player. */
	static final double RANGE = 24.0;
	static final int COOLDOWN_TICKS = 20;

	/** Client to server: show the item in this slot (its menu slot number) of the menu with this id. */
	public record Share(int containerId, int slot) implements CustomPacketPayload {
		public static final Type<Share> TYPE = new Type<>(JaymosPowers.id("share_item"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Share> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.containerId);
				buf.writeVarInt(p.slot);
			},
			buf -> new Share(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Share> type() {
			return TYPE;
		}
	}

	/** Player to the game time of their last share. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private ItemShare() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Share.TYPE, Share.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Share.TYPE, (payload, ctx) ->
			ctx.server().execute(() -> share(ctx.player(), payload.containerId(), payload.slot())));
	}

	private static void share(ServerPlayer player, int containerId, int slot) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu.containerId != containerId || slot < 0 || slot >= menu.slots.size()) {
			return;
		}
		ItemStack stack = menu.getSlot(slot).getItem();
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (stack.isEmpty() || last != null && now - last < COOLDOWN_TICKS) {
			return;
		}
		LAST.put(player.getUUID(), now);
		boolean near = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
		Component item = stack.getDisplayName();
		for (ServerPlayer p : player.level().getServer().getPlayerList().getPlayers()) {
			if (near && (p.level() != player.level() || p != player && p.distanceTo(player) > RANGE)) {
				continue;
			}
			p.sendSystemMessage(Component.translatable("message.jaymospowers.share_item", name(player, p), item));
		}
	}

	/** The sharer's name as {@code to} knows them (Kingdoms' introductions, when it is there). */
	private static Component name(ServerPlayer sharer, ServerPlayer to) {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			return sharer.getDisplayName();
		}
		boolean knows = Boolean.TRUE.equals(KingdomsCompat.call("Introductions", "knows",
			new Class<?>[] {Player.class, net.minecraft.world.entity.Entity.class}, true, to, sharer));
		if (knows) {
			return sharer.getDisplayName();
		}
		Object stranger = KingdomsCompat.call("Introductions", "stranger", new Class<?>[0], null);
		return stranger instanceof Component c ? c : sharer.getDisplayName();
	}
}
