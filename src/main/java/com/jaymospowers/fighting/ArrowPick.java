package com.jaymospowers.fighting;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jspecify.annotations.Nullable;

/**
 * Pick your arrow (Clouds and Bits patch 6): scroll while a bow or crossbow is being drawn to step through the kinds of
 * arrow it can fire ({@link #kinds}: those in the worn quiver first, top of the quiver first, then the rest of the
 * pack). The pick is saved on the weapon ({@link #CHOSEN}) and the weapon fires it from then on, from the quiver first,
 * else the pack; with none of that kind left it fires as it always did. The wheel is taken only while drawing, so the
 * stance wheel (Sneak + scroll) and a rider's gaits keep it the rest of the time. The client draws the pick beside the
 * hotbar ({@code ArrowPickClient}).
 * <p>
 * Jaymo's Kitchen's quiver is optional: it is touched only through {@link Kitchen}, after a check that Kitchen is loaded.
 * Choosing a kind that is in the quiver turns the quiver so that kind is on top, and Kitchen's own draw does the rest.
 */
public final class ArrowPick {
	private static final boolean KITCHEN = FabricLoader.getInstance().isModLoaded("jaymoskitchen");

	/** The kind of arrow this weapon fires (one arrow's worth: item and components), unset until a pick is made. */
	public static DataComponentType<ItemStackTemplate> CHOSEN;

	/** The wheel turned while drawing: -1 for the previous kind, 1 for the next. */
	public record Scroll(int step) implements CustomPacketPayload {
		public static final Type<Scroll> TYPE = new Type<>(JaymosPowers.id("arrow_pick"));
		public static final StreamCodec<ByteBuf, Scroll> CODEC = ByteBufCodecs.VAR_INT.map(Scroll::new, Scroll::step);

		@Override
		public Type<Scroll> type() {
			return TYPE;
		}
	}

	private ArrowPick() {
	}

	public static void init() {
		CHOSEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("chosen_arrow"),
			DataComponentType.<ItemStackTemplate>builder().persistent(ItemStackTemplate.CODEC).networkSynchronized(ItemStackTemplate.STREAM_CODEC).build());
		PayloadTypeRegistry.serverboundPlay().register(Scroll.TYPE, Scroll.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Scroll.TYPE, (payload, context) -> scroll(context.player(), payload.step()));
	}

	/** The arrows {@code weapon} can fire among what {@code player} carries, each kind once as a single arrow: the worn quiver's first. */
	public static List<ItemStack> kinds(Player player, ItemStack weapon) {
		List<ItemStack> kinds = new ArrayList<>();
		if (!(weapon.getItem() instanceof ProjectileWeaponItem bow)) {
			return kinds;
		}
		Predicate<ItemStack> supported = bow.getAllSupportedProjectiles();
		if (KITCHEN) {
			for (ItemStack arrow : Kitchen.quiverKinds(player)) {
				add(kinds, arrow, supported);
			}
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			add(kinds, inventory.getItem(slot), supported);
		}
		return kinds;
	}

	private static void add(List<ItemStack> kinds, ItemStack stack, Predicate<ItemStack> supported) {
		if (stack.isEmpty() || !supported.test(stack)) {
			return;
		}
		for (ItemStack have : kinds) {
			if (ItemStack.isSameItemSameComponents(have, stack)) {
				return;
			}
		}
		kinds.add(stack.copyWithCount(1));
	}

	/** How many arrows of this kind {@code player} carries, quiver included. */
	public static int count(Player player, ItemStack kind) {
		int total = KITCHEN ? Kitchen.quiverCount(player, kind) : 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (ItemStack.isSameItemSameComponents(stack, kind)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * The chosen arrow for {@code weapon}, or null to leave the choice of ammunition to the game (nothing chosen, none of that
	 * kind carried, or it sits in a quiver, which has been turned to bring it up for Kitchen's own draw to find).
	 */
	public static @Nullable ItemStack ammo(Player player, ItemStack weapon) {
		ItemStackTemplate chosen = weapon.get(CHOSEN);
		if (chosen == null || !(weapon.getItem() instanceof ProjectileWeaponItem bow)) {
			return null;
		}
		ItemStack kind = chosen.create();
		if (KITCHEN && Kitchen.bring(player, kind)) {
			return null;
		}
		Predicate<ItemStack> supported = bow.getAllSupportedProjectiles();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, kind) && supported.test(stack)) {
				return stack;
			}
		}
		return null;
	}

	private static void scroll(ServerPlayer player, int step) {
		ItemStack weapon = player.isUsingItem() ? player.getItemInHand(player.getUsedItemHand()) : ItemStack.EMPTY;
		List<ItemStack> kinds = kinds(player, weapon);
		if (kinds.isEmpty()) {
			return;
		}
		ItemStackTemplate chosen = weapon.get(CHOSEN);
		int at = -1;
		for (int i = 0; chosen != null && i < kinds.size(); i++) {
			if (ItemStack.isSameItemSameComponents(kinds.get(i), chosen.create())) {
				at = i;
			}
		}
		// (nothing picked yet: the first kind is what fires, so the first step goes on from there)
		ItemStack next = kinds.get(Mth.positiveModulo(Math.max(at, 0) + Integer.signum(step), kinds.size()));
		weapon.set(CHOSEN, ItemStackTemplate.fromNonEmptyStack(next, 1));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.5F, 1.5F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arrow.pick", next.getHoverName()).withStyle(ChatFormatting.GOLD));
	}

	/** The only holder of Kitchen's quiver classes, loaded only when Kitchen is. */
	private static final class Kitchen {
		static List<ItemStack> quiverKinds(Player player) {
			List<ItemStack> kinds = new ArrayList<>();
			ItemStack quiver = com.jaymoskitchen.quiver.Quivers.worn(player);
			if (quiver != null) {
				List<ItemStackTemplate> arrows = com.jaymoskitchen.quiver.QuiverItem.contents(quiver).arrows();
				for (int i = arrows.size() - 1; i >= 0; i--) {
					kinds.add(arrows.get(i).create());
				}
			}
			return kinds;
		}

		static int quiverCount(Player player, ItemStack kind) {
			ItemStack quiver = com.jaymoskitchen.quiver.Quivers.worn(player);
			int total = 0;
			if (quiver != null) {
				for (ItemStackTemplate arrow : com.jaymoskitchen.quiver.QuiverItem.contents(quiver).arrows()) {
					if (ItemStack.isSameItemSameComponents(arrow.create(), kind)) {
						total += arrow.count();
					}
				}
			}
			return total;
		}

		/** Turns the worn quiver to put this kind on top. True if the quiver has it (the quiver's draw then supplies it). */
		static boolean bring(Player player, ItemStack kind) {
			ItemStack quiver = com.jaymoskitchen.quiver.Quivers.worn(player);
			if (quiver == null) {
				return false;
			}
			List<ItemStackTemplate> arrows = new ArrayList<>(com.jaymoskitchen.quiver.QuiverItem.contents(quiver).arrows());
			for (int i = 0; i < arrows.size(); i++) {
				if (ItemStack.isSameItemSameComponents(arrows.get(i).create(), kind)) {
					if (i != arrows.size() - 1) {
						arrows.add(arrows.remove(i));
						quiver.set(com.jaymoskitchen.quiver.Quivers.CONTENTS, new com.jaymoskitchen.quiver.QuiverContents(arrows));
					}
					return true;
				}
			}
			return false;
		}
	}
}
