package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;

/**
 * <b>Hot food runs</b>: a baker or tavern keeper (a cook) with more orders than hands pays someone quick to run hot
 * dishes to doors before they go cold. "Hot dishes" on a cook's Town business screen ({@link TalkErrands}) hands you
 * {@link #LEAST} to {@link #MOST} covered dishes, each marked hot and addressed to a different villager of the
 * neighbourhood (named on the dish). Hold a dish and use it on the villager it's for within {@link #HOT_TICKS} ticks:
 * it pays {@link #HOT_MILLI} thousandths of an emerald, a quarter more for each other dish in the same run. Once cold
 * it pays only {@link #COLD_MILLI}. You can take one run at a time. The dishes can't be eaten.
 */
public final class HotRuns {
	/** How many dishes a run is, how far from the cook its doors are (blocks), and how long a dish stays hot (ticks). */
	static final int LEAST = 1, MOST = 5;
	static final double DOORS = 48.0;
	static final long HOT_TICKS = 4800L;
	/** A hot and a cold dish's pay (thousandths of an emerald), and the share more each other dish in a run adds. */
	static final long HOT_MILLI = 600L, COLD_MILLI = 100L;
	static final double BUNDLE_BONUS = 0.25;
	/** How often (ticks) carried dishes are checked for cooling. */
	static final int EVERY = 40;
	static final String HOT = "jaymos_hot_dish";
	static final List<Item> DISHES = List.of(Items.BREAD, Items.PUMPKIN_PIE, Items.MUSHROOM_STEW, Items.BAKED_POTATO, Items.COOKED_CHICKEN,
		Items.RABBIT_STEW, Items.COOKED_PORKCHOP);

	private HotRuns() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager v) || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			CompoundTag tag = dish(held);
			if (tag == null) {
				return InteractionResult.PASS;
			}
			if (!v.getUUID().toString().equals(tag.getStringOr("to", ""))) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hot.not_mine", v.getName(), tag.getStringOr("to_name", "")));
				return InteractionResult.SUCCESS;
			}
			deliver(server, sp, v, held, tag);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				cool(server);
			}
		});
	}

	/** {@code v} is a cook (a baker or tavern keeper) who can hand out a run. */
	static boolean cooks(Villager v) {
		return !v.isBaby() && Meals.cook(v);
	}

	/** The marker of a run's dish {@code stack}, or null. */
	private static CompoundTag dish(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return tag.contains(HOT) ? tag : null;
	}

	private static boolean carrying(ServerPlayer player) {
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (dish(s) != null) {
				return true;
			}
		}
		return false;
	}

	// ---- the cook hands out a run ------------------------------------------------------------------------------------

	/** {@code cook} hands {@code player} a run: dishes for neighbours within {@link #DOORS}. */
	static void offer(ServerPlayer player, Villager cook) {
		if (!(cook.level() instanceof ServerLevel level)) {
			return;
		}
		if (carrying(player)) {
			player.sendSystemMessage(Component.translatable("talk.jaymoskingdoms.hot.busy", cook.getName()));
			return;
		}
		List<Villager> doors = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(cook.blockPosition()).inflate(DOORS),
			v -> v != cook && v.isAlive() && !v.isBaby()));
		doors.removeIf(v -> v.getName().getString().isEmpty());
		if (doors.isEmpty()) {
			player.sendSystemMessage(Component.translatable("talk.jaymoskingdoms.hot.none", cook.getName()));
			return;
		}
		RandomSource random = level.getRandom();
		java.util.Collections.shuffle(doors, new java.util.Random(random.nextLong()));
		int count = Math.min(doors.size(), LEAST + random.nextInt(MOST - LEAST + 1));
		for (int i = 0; i < count; i++) {
			Villager to = doors.get(i);
			ItemStack stack = new ItemStack(DISHES.get(random.nextInt(DISHES.size())));
			stack.remove(DataComponents.CONSUMABLE);
			stack.remove(DataComponents.FOOD);
			CompoundTag tag = new CompoundTag();
			tag.putBoolean(HOT, true);
			tag.putString("to", to.getStringUUID());
			tag.putString("to_name", to.getName().getString());
			tag.putLong("born", level.getGameTime());
			tag.putInt("size", count);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			describe(stack, true);
			player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("talk.jaymoskingdoms.hot.given", cook.getName(), count).withStyle(ChatFormatting.GOLD));
	}

	/** Names a dish for its state: hot (steaming) or gone cold, and who it is for. */
	private static void describe(ItemStack stack, boolean hot) {
		CompoundTag tag = dish(stack);
		if (tag == null) {
			return;
		}
		Component name = Component.translatable(stack.getItem().getDescriptionId());
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(hot ? "item.jaymoskingdoms.hot_dish" : "item.jaymoskingdoms.cold_dish", name)
			.withStyle(style -> style.withItalic(false).withColor(hot ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.jaymoskingdoms.hot_dish.for", tag.getStringOr("to_name", "")).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.YELLOW)))));
	}

	/** Dishes carried past {@link #HOT_TICKS} go cold (renamed, and they pay little). */
	private static void cool(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
				CompoundTag tag = dish(s);
				if (tag != null && !tag.getBooleanOr("cold", false) && now - tag.getLongOr("born", now) > HOT_TICKS) {
					tag.putBoolean("cold", true);
					s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
					describe(s, false);
					p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hot.cold", s.getHoverName()));
				}
			}
		}
	}

	// ---- the door ----------------------------------------------------------------------------------------------------

	private static void deliver(ServerLevel level, ServerPlayer player, Villager to, ItemStack dish, CompoundTag tag) {
		boolean hot = !tag.getBooleanOr("cold", false) && level.getGameTime() - tag.getLongOr("born", 0L) <= HOT_TICKS;
		int size = Math.max(1, tag.getIntOr("size", 1));
		long milli = hot ? Math.round(HOT_MILLI * (1.0 + BUNDLE_BONUS * (size - 1))) : COLD_MILLI;
		Currencies.Pay pay = Currencies.pay(Currencies.at(level, to.blockPosition()), milli);
		Currencies.give(player, pay);
		dish.shrink(1);
		level.playSound(null, to.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.0F);
		player.sendOverlayMessage(Component.translatable(hot ? "message.jaymoskingdoms.hot.thanks" : "message.jaymoskingdoms.hot.grumble", to.getName(),
			Currencies.amount(pay)));
	}
}
