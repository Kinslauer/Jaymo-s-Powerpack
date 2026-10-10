package com.jaymoskitchen.kitchen;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.blay09.mods.cookingforblockheads.tag.ModItemTags;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.util.Prediction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cooking for Blockheads' oven, run as a stove (the way the old Jaymo's stove worked):
 * <ul>
 * <li>Right-click the knobs along the top of its front to turn it on or off (the rest of the front still opens it). It
 * only lights with something to heat with: fuel in its fuel slots, or, with a heating unit fitted, a redstone signal.
 * Lit, it burns that into stored heat; cooking a dish takes heat, and when it's all gone the oven goes out.
 * <li>Up to four utensils (pots, pans, ...) stand on top, one in each of its four tool spots. Each shows an outline
 * of its own: right-click a spot holding a utensil to put one there (or swap it for what's there), or with an empty
 * hand to take it back.
 * <li>Sneak-click it lit with an empty hand to cook: nothing on top bakes, a pot cooks stews and soups, a pan grills
 * and sears.
 * <li>Lit, its window glows, a pot bubbles and steams, a pan smokes (see the client's oven mixins).
 * </ul>
 */
public final class OvenHob {
	/** Whether the oven's been turned on: saved with it, and synced so clients can show it. */
	public static final AttachmentType<Boolean> LIT = AttachmentRegistry.create(JaymosKitchen.id("oven_lit"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL.cast(), AttachmentSyncPredicate.all()));
	/**
	 * Heat stored in the oven, from burning fuel in its fuel slots (or, with a heating unit fitted, from a redstone
	 * signal). A lit oven slowly uses it up, a dish uses a good deal; at nothing it goes out. Server side only.
	 */
	public static final AttachmentType<Integer> HEAT = AttachmentRegistry.create(JaymosKitchen.id("oven_heat"),
		builder -> builder.persistent(Codec.INT));
	public static final int MAX_HEAT = 3200;
	/** What a dish takes, and reheating one; a lit oven also uses one a tick just staying hot. */
	public static final int DISH_COST = 400, REHEAT_COST = 100;
	private static final int IDLE_DRAIN = 1;
	/** A heating unit with a redstone signal heats this much a tick. */
	private static final int REDSTONE_HEAT = 10;
	/** The knobs: the top quarter of the front. */
	private static final double KNOBS_FROM = 12.0 / 16.0;
	/** The oven's tool spots on top. */
	private static final int TOOL_SLOTS = 4;

	public enum Cookware {
		NONE, POT, PAN
	}

	private OvenHob() {
	}

	public static boolean isLit(BlockEntity oven) {
		return Boolean.TRUE.equals(oven.getAttached(LIT));
	}

	/** What's on top: a pot or a pan (the first found), or neither. */
	public static Cookware cookware(OvenBlockEntity oven) {
		for (int i = 0; i < TOOL_SLOTS; i++) {
			ItemStack tool = oven.getToolItem(i);
			if (tool.is(KitchenBlocks.COOKING_POT)) {
				return Cookware.POT;
			}
			if (tool.is(KitchenBlocks.FRYING_PAN)) {
				return Cookware.PAN;
			}
		}
		return Cookware.NONE;
	}

	/** Whether any of the tool spots holds {@code item}. */
	public static boolean has(OvenBlockEntity oven, Item item) {
		for (int i = 0; i < TOOL_SLOTS; i++) {
			if (oven.getToolItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	/** Whether a Cooking Pot sits in any of the oven's tool spots. */
	public static boolean hasPot(OvenBlockEntity oven) {
		return has(oven, KitchenBlocks.COOKING_POT);
	}

	/** Whether a Frying Pan sits in any of the oven's tool spots. */
	public static boolean hasPan(OvenBlockEntity oven) {
		return has(oven, KitchenBlocks.FRYING_PAN);
	}

	/** Whether a Saucepan sits in any of the oven's tool spots. */
	public static boolean hasSaucepan(OvenBlockEntity oven) {
		return has(oven, KitchenBlocks.SAUCEPAN);
	}

	// ------------------------------------------------------------------------------------------------------------
	// The four spots on top. In the oven's own frame (as if it faced north, front at -z) Cooking for Blockheads'
	// renderer centres them at: 0 (0.28, 0.70), 1 (0.72, 0.70), 2 (0.28, 0.30), 3 (0.72, 0.30), on y = 1; the
	// renderer turns that frame with the oven's facing (rotateDegrees(YP, 180 - facing.toYRot()) about the centre),
	// which takes local +x to facing.getClockWise() and local +z to facing.getOpposite().
	// ------------------------------------------------------------------------------------------------------------

	private static final double[] SLOT_X = {0.28, 0.72, 0.28, 0.72};
	private static final double[] SLOT_Z = {0.70, 0.70, 0.30, 0.30};
	/** Half the width of a spot's box: 0.2 (6.4 px square), so the four boxes never touch. */
	private static final double SLOT_HALF = 0.2;
	/** What part of a spot's box sticks up through the top: under it, above the oven's own top. */
	private static final double TOP = 1.0;
	private static final Map<ShapeKey, VoxelShape> SHAPES = new ConcurrentHashMap<>();

	private record ShapeKey(VoxelShape base, Direction facing, int heights) {
	}

	/** How tall (in pixels) the thing in a spot is drawn, so its box matches it; 0 for an empty spot. */
	private static int heightPx(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (stack.is(KitchenBlocks.COOKING_POT)) {
			return 5;
		}
		if (stack.is(KitchenBlocks.SAUCEPAN)) {
			return 4;
		}
		if (stack.is(KitchenBlocks.FRYING_PAN) || stack.is(KitchenBlocks.BAKING_TRAY) || stack.is(KitchenBlocks.ROLLING_PIN)) {
			return 2;
		}
		// other mods' utensils: a flat sprite tilted 45 degrees
		return 4;
	}

	/**
	 * The oven's outline/interaction shape: its own shape plus a box over each occupied tool spot, so each thing on
	 * top can be aimed at on its own (see the BaseKitchenBlock shape mixin). Collision keeps the plain shape (see the
	 * OvenBlock collision mixin).
	 */
	public static VoxelShape withCookware(VoxelShape base, BlockState state, BlockGetter level, BlockPos pos) {
		if (!state.hasProperty(OvenBlock.FACING) || !(level.getBlockEntity(pos) instanceof OvenBlockEntity oven)) {
			return base;
		}
		int heights = 0;
		for (int i = 0; i < TOOL_SLOTS; i++) {
			heights |= heightPx(oven.getToolItem(i)) << (i * 4);
		}
		if (heights == 0) {
			return base;
		}
		Direction facing = state.getValue(OvenBlock.FACING);
		return SHAPES.computeIfAbsent(new ShapeKey(base, facing, heights), key -> {
			VoxelShape shape = key.base();
			Direction right = key.facing().getClockWise(), back = key.facing().getOpposite();
			for (int i = 0; i < TOOL_SLOTS; i++) {
				int h = (key.heights() >> (i * 4)) & 0xF;
				if (h == 0) {
					continue;
				}
				double dx = SLOT_X[i] - 0.5, dz = SLOT_Z[i] - 0.5;
				double cx = 0.5 + dx * right.getStepX() + dz * back.getStepX();
				double cz = 0.5 + dx * right.getStepZ() + dz * back.getStepZ();
				shape = Shapes.or(shape, Shapes.box(cx - SLOT_HALF, TOP, cz - SLOT_HALF, cx + SLOT_HALF, TOP + h / 16.0,
					cz + SLOT_HALF));
			}
			return shape.optimize();
		});
	}

	/**
	 * Which tool spot a click aims at: on the oven's top, or on one of the boxes of the things standing there; -1 for
	 * anywhere else (its front, sides, ...).
	 */
	public static int aimedSlot(BlockHitResult hit, BlockPos pos, Direction facing) {
		Vec3 at = hit.getLocation();
		double y = at.y - pos.getY();
		boolean onTop = hit.getDirection() == Direction.UP && y >= TOP - 1.0E-4;
		if (!onTop && y <= TOP + 1.0E-4) {
			return -1;
		}
		// step a hair into what was hit, so a hit on a box's side counts for that box's spot
		Direction face = hit.getDirection();
		double ox = at.x - face.getStepX() * 1.0E-3 - pos.getX() - 0.5;
		double oz = at.z - face.getStepZ() * 1.0E-3 - pos.getZ() - 0.5;
		Direction right = facing.getClockWise(), back = facing.getOpposite();
		double lx = 0.5 + ox * right.getStepX() + oz * right.getStepZ();
		double lz = 0.5 + ox * back.getStepX() + oz * back.getStepZ();
		if (lx < 0.0 || lx > 1.0 || lz < 0.0 || lz > 1.0) {
			return -1;
		}
		return (lz >= 0.5 ? 0 : 2) + (lx < 0.5 ? 0 : 1);
	}

	/** After a tool spot changes: save it, and send it (and so the new shape) to the clients. */
	private static void changed(Level level, BlockPos pos, BlockState state, OvenBlockEntity oven) {
		oven.setChanged();
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
	}

	public static void register() {
		HobLocks.register();
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof OvenBlock) || !(level.getBlockEntity(pos) instanceof OvenBlockEntity oven)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getMainHandItem();
			Direction front = state.getValue(OvenBlock.FACING);
			int slot = aimedSlot(hit, pos, front);
			if (slot >= 0) {
				return useSlot(player, level, pos, state, oven, slot, held);
			}
			double up = hit.getLocation().y - pos.getY();
			if (hit.getDirection() == front && up >= KNOBS_FROM && held.isEmpty() && !player.isShiftKeyDown()) {
				if (!level.isClientSide()) {
					toggle(level, pos, oven, player);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/**
	 * A click on one of the spots on top. Empty hand (not sneaking): take what's there. A utensil (not sneaking): put
	 * one in an empty spot, or swap it for what's there. Anything else passes on (sneak-click to cook, dyes, ...).
	 */
	private static InteractionResult useSlot(Player player, Level level, BlockPos pos, BlockState state, OvenBlockEntity oven,
		int slot, ItemStack held) {
		if (player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack there = oven.getToolItem(slot);
		if (held.isEmpty()) {
			if (there.isEmpty()) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				if (refused(server, pos, oven, player, there, ItemStack.EMPTY)) {
					return InteractionResult.FAIL;
				}
				oven.setToolItem(slot, ItemStack.EMPTY);
				player.setItemInHand(InteractionHand.MAIN_HAND, there.copy());
				changed(level, pos, state, oven);
				level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.2F);
			}
			return InteractionResult.SUCCESS;
		}
		if (!held.is(ModItemTags.UTENSILS)) {
			return InteractionResult.PASS;
		}
		if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, held)) {
			// the same thing's already there: nothing to do (and don't let Cooking for Blockheads put it elsewhere)
			return InteractionResult.CONSUME;
		}
		if (level instanceof ServerLevel server) {
			if (!there.isEmpty() && refused(server, pos, oven, player, there, held)) {
				return InteractionResult.FAIL;
			}
			ItemStack one = player.hasInfiniteMaterials() ? held.copyWithCount(1) : held.split(1);
			oven.setToolItem(slot, one);
			if (!there.isEmpty()) {
				// swap: what was there comes back to the player
				if (player.getMainHandItem().isEmpty()) {
					player.setItemInHand(InteractionHand.MAIN_HAND, there.copy());
				} else {
					player.getInventory().placeItemBackInInventory(there.copy(), Prediction.SERVER_ONLY);
				}
			}
			changed(level, pos, state, oven);
			level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
		}
		return InteractionResult.SUCCESS;
	}

	/** A pot or pan a dish is cooking in can't come off (see HobLocks): says why, with a dull click. */
	private static boolean refused(ServerLevel level, BlockPos pos, OvenBlockEntity oven, Player player, ItemStack removed,
		ItemStack incoming) {
		Component why = HobLocks.removalBlocked(level, pos, oven, removed, incoming);
		if (why == null) {
			return false;
		}
		player.sendOverlayMessage(why);
		level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.6F);
		return true;
	}

	public static int heat(OvenBlockEntity oven) {
		Integer heat = oven.getAttached(HEAT);
		return heat == null ? 0 : heat;
	}

	private static void setHeat(OvenBlockEntity oven, int heat) {
		if (heat <= 0) {
			oven.removeAttached(HEAT);
		} else {
			oven.setAttached(HEAT, Math.min(MAX_HEAT, heat));
		}
		oven.setChanged();
	}

	/** Takes {@code amount} heat for a dish; false (and nothing taken) if there isn't that much. */
	public static boolean spend(OvenBlockEntity oven, int amount) {
		int heat = heat(oven);
		if (heat < amount) {
			return false;
		}
		setHeat(oven, heat - amount);
		return true;
	}

	/** Whether it has something to heat with: stored heat, fuel in its slots, or (with a heating unit) a redstone signal. */
	private static boolean hasPower(Level level, BlockPos pos, OvenBlockEntity oven) {
		if (heat(oven) > 0) {
			return true;
		}
		if (oven.hasPowerUpgrade()) {
			return level.hasNeighborSignal(pos);
		}
		Container fuel = oven.getFuelContainer();
		for (int i = 0; i < fuel.getContainerSize(); i++) {
			if (((OvenFuel) oven).jaymoskitchen$burnTime(level, fuel.getItem(i)) > 0) {
				return true;
			}
		}
		return false;
	}

	/** Server, every tick the oven ticks: a lit oven takes in heat (burning fuel, or redstone), uses some, and goes out when it's spent. */
	public static void serverTick(Level level, BlockPos pos, OvenBlockEntity oven) {
		if (level instanceof ServerLevel server) {
			// which tool slots hold cookware a dish is cooking in (synced for the oven screen)
			HobLocks.update(server, pos, oven);
		}
		if (!isLit(oven)) {
			return;
		}
		int heat = heat(oven);
		if (oven.hasPowerUpgrade()) {
			if (level.hasNeighborSignal(pos)) {
				heat += REDSTONE_HEAT;
			}
		} else if (heat < DISH_COST || heat < MAX_HEAT / 2) {
			// burn the next piece of fuel to top the heat back up
			Container fuel = oven.getFuelContainer();
			for (int i = 0; i < fuel.getContainerSize(); i++) {
				ItemStack stack = fuel.getItem(i);
				int burn = ((OvenFuel) oven).jaymoskitchen$burnTime(level, stack);
				if (burn > 0) {
					ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
					stack.shrink(1);
					if (stack.isEmpty() && remainder != null) {
						fuel.setItem(i, remainder.create());
					}
					fuel.setChanged();
					heat += burn;
					break;
				}
			}
		}
		heat -= IDLE_DRAIN;
		if (heat <= 0) {
			setHeat(oven, 0);
			if (!hasPower(level, pos, oven)) {
				// out of fuel: it goes out
				oven.removeAttached(LIT);
				oven.setChanged();
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
			}
			return;
		}
		setHeat(oven, heat);
	}

	private static void toggle(Level level, BlockPos pos, OvenBlockEntity oven, net.minecraft.world.entity.player.Player player) {
		boolean lit = !isLit(oven);
		if (lit && !hasPower(level, pos, oven)) {
			player.sendOverlayMessage(Component.translatable(oven.hasPowerUpgrade()
				? "message.jaymoskitchen.oven_no_signal" : "message.jaymoskitchen.oven_no_fuel"));
			level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.4F, 0.8F);
			return;
		}
		if (lit) {
			oven.setAttached(LIT, true);
		} else {
			oven.removeAttached(LIT);
		}
		oven.setChanged();
		level.playSound(null, pos, lit ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
			lit ? 1.0F : 0.4F, lit ? 1.2F : 1.6F);
		level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, lit ? 1.3F : 0.9F);
	}
}
