package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The back slot's swap key (unbound by default): the weapon or tool on your back comes to hand, and what you held that can
 * go on the back goes there. Holding something that can't, the back item goes to a free hotbar slot and you take it up;
 * with none free, nothing moves and a line says so.
 */
public final class BackSlot {
	/** Client → server: swap my back slot with my hand. */
	public record Swap() implements CustomPacketPayload {
		public static final Type<Swap> TYPE = new Type<>(JaymosPowers.id("back_slot_swap"));
		public static final StreamCodec<ByteBuf, Swap> CODEC = StreamCodec.unit(new Swap());

		@Override
		public Type<Swap> type() {
			return TYPE;
		}
	}

	private BackSlot() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Swap.TYPE, Swap.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Swap.TYPE, (payload, context) -> swap(context.player()));
	}

	static void swap(ServerPlayer player) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		List<ItemStack> worn = Baubles.worn(player);
		ItemStack back = worn.get(Baubles.BACK_SLOT);
		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		ItemStack hand = inventory.getItem(selected);
		if (back.isEmpty() && hand.isEmpty()) {
			return;
		}
		if (hand.isEmpty() || hand.getCount() == 1 && Baubles.fits(Baubles.BACK_SLOT, hand)) {
			worn.set(Baubles.BACK_SLOT, hand);
			inventory.setItem(selected, back);
		} else if (back.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.back_slot.wont_fit"));
			return;
		} else {
			int free = -1;
			for (int slot = 0; slot < Inventory.getSelectionSize() && free < 0; slot++) {
				if (inventory.getItem(slot).isEmpty()) {
					free = slot;
				}
			}
			if (free < 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.back_slot.no_room"));
				return;
			}
			worn.set(Baubles.BACK_SLOT, ItemStack.EMPTY);
			inventory.setItem(free, back);
			inventory.setSelectedSlot(free);
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(free));
		}
		Baubles.setWorn(player, worn);
		player.containerMenu.broadcastChanges();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.6F, 1.1F);
	}
}
