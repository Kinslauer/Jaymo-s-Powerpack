package com.jaymoskitchen.kitchen;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Kitchen;
import java.util.ArrayList;
import java.util.List;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * While a dish is cooking at an oven, the cookware it uses can't be taken off: a Cooking Pot for pot dishes
 * ({@link Dish.Method#SEQUENCE}), a Frying Pan for grilled and seared ones ({@link Dish.Method#GRILL},
 * {@link Dish.Method#SEAR}). A dish counts while its minigame runs there (see {@link Kitchen#dishesCookingAt}) and
 * while it waits on the oven afterwards (see {@link HobQueue#pendingDishes}); when it's served the lock lifts by itself,
 * since it's always worked out from what's cooking now.
 * <p>
 * Taking one off the hob by hand ({@link OvenHob}) is refused if it would leave fewer pots (or pans) than are in use.
 * The oven screen's tool slots lock the first N slots holding a pot when N pots are in use (likewise pans): the
 * server works that out live, and keeps a synced bitmask ({@link #LOCKED}) on the oven so clients agree (see the
 * SlotOvenTool mixin).
 */
public final class HobLocks {
	/** Which of the oven's four tool slots are locked (bit i = slot i). Derived each oven tick; synced, not saved. */
	public static final AttachmentType<Integer> LOCKED = AttachmentRegistry.create(JaymosKitchen.id("hob_locked"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.all()));

	private static final int TOOL_SLOTS = 4;

	public enum Kind {
		NONE, POT, PAN
	}

	private HobLocks() {
	}

	/** Loads the class so the attachment type is registered early (on both sides). */
	public static void register() {
	}

	public static Kind kind(ItemStack stack) {
		if (stack.is(KitchenBlocks.COOKING_POT)) {
			return Kind.POT;
		}
		if (stack.is(KitchenBlocks.FRYING_PAN)) {
			return Kind.PAN;
		}
		return Kind.NONE;
	}

	public static Kind uses(Dish dish) {
		return switch (dish.method()) {
			case SEQUENCE -> Kind.POT;
			case GRILL, SEAR -> Kind.PAN;
			default -> Kind.NONE;
		};
	}

	/** Every dish cooking at the oven right now: minigames running there, then the ones waiting on it. Server side. */
	public static List<Dish> cooking(ServerLevel level, BlockPos pos, OvenBlockEntity oven) {
		List<Dish> dishes = new ArrayList<>(Kitchen.dishesCookingAt(level, pos));
		dishes.addAll(HobQueue.pendingDishes(oven));
		return dishes;
	}

	private static List<Dish> using(List<Dish> cooking, Kind kind) {
		List<Dish> out = new ArrayList<>();
		for (Dish dish : cooking) {
			if (uses(dish) == kind) {
				out.add(dish);
			}
		}
		return out;
	}

	private static int count(OvenBlockEntity oven, Kind kind) {
		int n = 0;
		for (int i = 0; i < TOOL_SLOTS; i++) {
			if (kind(oven.getToolItem(i)) == kind) {
				n++;
			}
		}
		return n;
	}

	/** The locked tool slots (bit i = slot i): the first N holding a pot with N pot dishes cooking, likewise pans. */
	public static int lockedMask(ServerLevel level, BlockPos pos, OvenBlockEntity oven) {
		List<Dish> cooking = cooking(level, pos, oven);
		if (cooking.isEmpty()) {
			return 0;
		}
		int pots = using(cooking, Kind.POT).size(), pans = using(cooking, Kind.PAN).size();
		int mask = 0;
		for (int i = 0; i < TOOL_SLOTS && (pots > 0 || pans > 0); i++) {
			Kind kind = kind(oven.getToolItem(i));
			if (kind == Kind.POT && pots > 0) {
				pots--;
				mask |= 1 << i;
			} else if (kind == Kind.PAN && pans > 0) {
				pans--;
				mask |= 1 << i;
			}
		}
		return mask;
	}

	/** Whether tool slot {@code slot} is locked: worked out live on the server, from the synced mask on a client. */
	public static boolean isLocked(OvenBlockEntity oven, int slot) {
		if (slot < 0 || slot >= TOOL_SLOTS) {
			return false;
		}
		Level level = oven.getLevel();
		int mask;
		if (level instanceof ServerLevel server) {
			mask = lockedMask(server, oven.getBlockPos(), oven);
		} else {
			Integer synced = oven.getAttached(LOCKED);
			mask = synced == null ? 0 : synced;
		}
		return (mask & (1 << slot)) != 0;
	}

	/** Server, every oven tick: keep the synced mask up to date (only touched when it changes). */
	public static void update(ServerLevel level, BlockPos pos, OvenBlockEntity oven) {
		int mask = lockedMask(level, pos, oven);
		Integer current = oven.getAttached(LOCKED);
		if ((current == null ? 0 : current) == mask) {
			return;
		}
		if (mask == 0) {
			oven.removeAttached(LOCKED);
		} else {
			oven.setAttached(LOCKED, mask);
		}
	}

	/**
	 * Why {@code removed} can't come off the hob (swapped for {@code incoming}, or EMPTY when just taken): a message
	 * naming the dish that needs it; or null if it's free to go. Server side.
	 */
	public static @Nullable Component removalBlocked(ServerLevel level, BlockPos pos, OvenBlockEntity oven, ItemStack removed,
		ItemStack incoming) {
		Kind kind = kind(removed);
		if (kind == Kind.NONE || kind(incoming) == kind) {
			return null;
		}
		List<Dish> using = using(cooking(level, pos, oven), kind);
		if (count(oven, kind) - 1 >= using.size()) {
			return null;
		}
		Component dish = new ItemStack(ModItems.dish(using.get(0))).getHoverName();
		return Component.translatable(kind == Kind.POT ? "message.jaymoskitchen.pot_in_use" : "message.jaymoskitchen.pan_in_use",
			dish);
	}
}
