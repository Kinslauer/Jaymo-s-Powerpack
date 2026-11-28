package com.jaymoskingdoms;

import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Shift-click a harness piece in an inventory to put it on: it goes in its own place of the harness when the harness
 * screen would take it there (spaulders after a chest piece, a crest on a helm, gilded plate only on a royal or lord).
 * A place already filled gives up what it held, which goes back where the new piece came from (a tabard over a worn
 * cloak). Otherwise the click moves it as it always did, and a short line says why when it was the fit. Works from the survival inventory ({@code mixin.HarnessQuickEquipMixin}) and from the bauble
 * screen's inventory (Jaymo's Powers calls {@link #shiftClick} by reflection). Shift-click on a worn piece in the
 * harness screen takes it off, as before ({@link HarnessMenu}).
 * <p>
 * Everything happens on the server; the client only holds back its own guess of the old move when it sees the place
 * free (the harness syncs to its wearer). The creative inventory moves items on the client alone, so there the client
 * tells the server ({@link QuickEquip}).
 */
public final class HarnessQuickEquip {
	/**
	 * Client → server: in the creative inventory, a shift-click on {@code slot} (its index in the player's inventory menu)
	 * that should put a harness piece on.
	 */
	public record QuickEquip(int slot) implements CustomPacketPayload {
		public static final Type<QuickEquip> TYPE = new Type<>(JaymosKingdoms.id("harness_quick_equip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, QuickEquip> CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast()
			.map(QuickEquip::new, QuickEquip::slot);

		@Override
		public Type<QuickEquip> type() {
			return TYPE;
		}
	}

	/**
	 * Client side, set by the client: tells the server about a shift-click on this inventory menu slot index when it came
	 * from the creative inventory screen (it does nothing for any other screen, whose clicks reach the server anyway).
	 */
	public static @Nullable IntConsumer creativeSend;

	private HarnessQuickEquip() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(QuickEquip.TYPE, QuickEquip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuickEquip.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			int index = payload.slot();
			// only from the creative inventory (no menu of the server's open), and only on the inventory's own slots
			if (player.isCreative() && player.containerMenu == player.inventoryMenu && index >= InventoryMenu.INV_SLOT_START
				&& index <= InventoryMenu.SHIELD_SLOT && index < player.inventoryMenu.slots.size()) {
				shiftClick(player, player.inventoryMenu.getSlot(index));
			}
		}));
	}

	/**
	 * Shift-click on {@code slot}, an inventory slot: true when one piece from it went on (or, on the client, when one
	 * will), so the old move is skipped. A piece already in its place comes off into {@code slot}, so only a lone piece
	 * (not one of a stack) is put on over another. On the server it takes the piece from the slot itself, so a repeated or late
	 * click can only put on what is still there.
	 */
	public static boolean shiftClick(Player player, Slot slot) {
		ItemStack stack = slot.getItem();
		Harness.Slot place = stack.isEmpty() ? null : Harness.slotOf(stack);
		if (place == null || player.isSpectator()) {
			return false;
		}
		ItemStack worn = Harness.get(player, place);
		boolean bare = worn.isEmpty();
		if (!bare && stack.getCount() != 1) {
			return false;
		}
		boolean fits = Harness.fits(player, place);
		boolean mayWear = HeraldryWear.mayWear(player, stack);
		if (!(player instanceof ServerPlayer server)) {
			boolean will = fits && mayWear;
			if (will && creativeSend != null && player.inventoryMenu.slots.contains(slot)) {
				creativeSend.accept(slot.index);
			}
			return will;
		}
		if (!fits) {
			server.sendOverlayMessage(Component.translatable(place == Harness.Slot.CREST ? "message.jaymoskingdoms.harness.needs_helm"
				: "message.jaymoskingdoms.harness.needs_chest"));
			return false;
		}
		if (!mayWear) {
			HeraldryWear.refused(server);
			return false;
		}
		ItemStack off = worn.copy();
		Harness.set(server, place, stack.copyWithCount(1));
		stack.shrink(1);
		if (stack.isEmpty()) {
			slot.setByPlayer(off);
		} else {
			slot.setChanged();
		}
		HeraldryArmour.Piece piece = HeraldryArmour.of(Harness.get(server, place));
		server.level().playSound(null, server.getX(), server.getY(), server.getZ(),
			(piece != null && piece.plate() ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_GENERIC).value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		return true;
	}
}
