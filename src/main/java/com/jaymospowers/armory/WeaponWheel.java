package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.BackSlot;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.compat.ToolBeltCompat;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ShieldItem;

/**
 * The weapon wheel (Weapon Wheel's idea, CC0, by henalne; {@code client/armory/WeaponWheelScreen} draws it): one key
 * opens a ring of what the body carries, sorted by kind (blades, axes, long arms, pistols, shields, tools, the worn tool
 * belt's tools, and bare fists), and the pick comes to hand. A hotbar weapon is simply drawn (its slot selected, so the
 * others stay where they hang); a shield or a stack of arrows goes to the off hand; the back slot's item swaps with the hand as the back-slot
 * key does ({@link BackSlot}); a belt tool swaps out of the belt the way the belt's own menu does; fists put the held
 * thing away. The server checks every pick against what is really there.
 */
public final class WeaponWheel {
	/** Where a pick comes from. */
	public static final int HOTBAR = 0, BACK = 1, BELT = 2, FISTS = 3;

	/** Client → server: bring this to hand. {@code index} is the hotbar slot, or the belt slot; unused otherwise. */
	public record Pick(int source, int index) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosPowers.id("weapon_wheel_pick"));
		public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Pick::source, ByteBufCodecs.VAR_INT, Pick::index, Pick::new);

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	private WeaponWheel() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> pick(context.player(), payload.source(), payload.index()));
	}

	private static void pick(ServerPlayer player, int source, int index) {
		if (player.isSpectator() || !player.isAlive() || player.isUsingItem()) {
			return;
		}
		switch (source) {
			case HOTBAR -> hotbar(player, index);
			case BACK -> back(player);
			case BELT -> belt(player, index);
			case FISTS -> fists(player);
			default -> {
			}
		}
	}

	/** Whether {@code stack} goes to the off hand when picked (a shield, arrows), not the main hand. Both sides. */
	public static boolean offHand(ItemStack stack) {
		return stack.getItem() instanceof ShieldItem || stack.is(ItemTags.ARROWS);
	}

	/** A hotbar weapon: drawn; a shield or arrows: to the off hand, what was there taking its slot. */
	private static void hotbar(ServerPlayer player, int slot) {
		Inventory inventory = player.getInventory();
		if (slot < 0 || slot >= Inventory.getSelectionSize()) {
			return;
		}
		ItemStack stack = inventory.getItem(slot);
		if (!BodyWeapons.carriedOnBody(stack) && !offHand(stack)) {
			return;
		}
		if (offHand(stack)) {
			inventory.setItem(slot, player.getOffhandItem());
			player.setItemInHand(InteractionHand.OFF_HAND, stack);
			equipped(player);
			return;
		}
		select(player, slot);
	}

	/** The back slot's item, by the back-slot swap; a shield on the back goes to the off hand instead. */
	private static void back(ServerPlayer player) {
		List<ItemStack> worn = Baubles.worn(player);
		ItemStack back = worn.get(Baubles.BACK_SLOT);
		if (back.isEmpty()) {
			return;
		}
		ItemStack off = player.getOffhandItem();
		if (back.getItem() instanceof ShieldItem && (off.isEmpty() || off.getCount() == 1 && Baubles.fits(Baubles.BACK_SLOT, off))) {
			worn.set(Baubles.BACK_SLOT, off);
			player.setItemInHand(InteractionHand.OFF_HAND, back);
			Baubles.setWorn(player, worn);
			equipped(player);
			return;
		}
		BackSlot.swap(player);
	}

	/**
	 * A belt tool: the belt's swap takes only an empty hand or something the belt holds, so holding anything else the
	 * hand moves to a free hotbar slot first (none free: nothing moves, and a line says so).
	 */
	private static void belt(ServerPlayer player, int slot) {
		List<ItemStack> tools = ToolBeltCompat.tools(player);
		if (slot < 0 || slot >= tools.size() || tools.get(slot).isEmpty()) {
			return;
		}
		ItemStack hand = player.getMainHandItem();
		if (!hand.isEmpty() && !ToolBeltCompat.holds(hand) && !freeHand(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.weapon_wheel.no_room"));
			return;
		}
		ToolBeltCompat.swap(player, slot);
	}

	/** Bare fists: an empty hotbar slot is selected, so the held weapon goes back to its place on the body. */
	private static void fists(ServerPlayer player) {
		if (!player.getMainHandItem().isEmpty() && !freeHand(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.weapon_wheel.no_room"));
		}
	}

	/** Selects the empty hotbar slot nearest the held one; false when none is empty. */
	private static boolean freeHand(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int size = Inventory.getSelectionSize();
		int held = inventory.getSelectedSlot();
		for (int step = 1; step < size; step++) {
			for (int slot : new int[] {held + step, held - step}) {
				if (slot >= 0 && slot < size && inventory.getItem(slot).isEmpty()) {
					select(player, slot);
					return true;
				}
			}
		}
		return false;
	}

	private static void select(ServerPlayer player, int slot) {
		if (player.getInventory().getSelectedSlot() == slot) {
			return;
		}
		player.getInventory().setSelectedSlot(slot);
		player.connection.send(new ClientboundSetHeldSlotPacket(slot));
	}

	private static void equipped(ServerPlayer player) {
		player.containerMenu.broadcastChanges();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.6F, 1.1F);
	}
}
