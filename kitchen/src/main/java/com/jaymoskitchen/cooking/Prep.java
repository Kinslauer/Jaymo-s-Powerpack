package com.jaymoskitchen.cooking;

import net.blay09.mods.cookingforblockheads.block.CounterBlock;
import net.blay09.mods.cookingforblockheads.block.CuttingBoardBlock;
import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.herb.JointItem;
import com.jaymoskitchen.herb.JointQuality;
import com.jaymoskitchen.net.Payloads.PrepUpdate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The prep board, on a wood, stone or slab block: mix dough, or roll a joint. Pour water, pour oil, knead.
 * The pouring and kneading happen client-side (they're about holding and tapping keys);
 * the client reports the two pour levels and whether the knead finished, and both sides score it with
 * {@link #score} so they always agree.
 */
public final class Prep {
	/**
	 * Pour bands, as fractions of the gauge: [min, max] is acceptable, the inner band is perfect. Each mix the server
	 * slides both bands by a random shift (within the SHIFT ranges), so the right level is different every time.
	 */
	public static final float WATER_MIN = 0.55F, WATER_MAX = 0.75F, WATER_PERFECT_MIN = 0.61F, WATER_PERFECT_MAX = 0.69F;
	public static final float OIL_MIN = 0.20F, OIL_MAX = 0.34F, OIL_PERFECT_MIN = 0.24F, OIL_PERFECT_MAX = 0.30F;
	public static final float WATER_SHIFT = 0.12F, OIL_SHIFT_DOWN = 0.08F, OIL_SHIFT_UP = 0.12F;
	/** Gauge fill per tick when a pour starts (each pour picks a rate up to x1.4 this), and how much it speeds up per tick. */
	public static final float WATER_RATE = 0.011F, OIL_RATE = 0.009F, POUR_ACCEL = 0.00018F;
	/** Knead presses (alternating left/right) needed, and the time allowed for them. */
	public static final int KNEADS = 20, KNEAD_TICKS = 110;
	/** A real attempt takes at least this long; anything quicker is ignored. */
	private static final int MIN_TICKS = 40;

	/** What's being made on the prep board. */
	public enum Recipe {
		/** Pour water, pour oil, knead. */
		DOUGH,
		/** Grind Pmeh, sprinkle it on paper, roll it (see {@link Rolling}). */
		JOINT;

		public static Recipe byIndex(int index) {
			return index == JOINT.ordinal() ? JOINT : DOUGH;
		}
	}

	/** How it turned out, and how many dough (or joints) that makes. */
	public enum Result {
		RUINED(0), GOOD(1), PERFECT(2);

		private final int amount;

		Result(int amount) {
			this.amount = amount;
		}

		public int amount() {
			return amount;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private record Session(Recipe recipe, BlockPos pos, int startTick, float waterShift, float oilShift) {
	}

	private Prep() {
	}

	/** Dough: a bowl (handed back), a water bottle, cooking oil and wheat for flour. Joint: Pmeh and paper. */
	public static Map<Step, Integer> ingredients(Recipe recipe) {
		Map<Step, Integer> map = new EnumMap<>(Step.class);
		if (recipe == Recipe.JOINT) {
			map.put(Step.PMEH, 1);
			map.put(Step.PAPER, 1);
			return map;
		}
		map.put(Step.BOWL, 1);
		map.put(Step.WATER_BOTTLE, 1);
		map.put(Step.COOKING_OIL, 1);
		map.put(Step.FLOUR, 3);
		return map;
	}

	/**
	 * A prep board: the top of a half slab a player put down (not a village's or a ruin's). Clients can only tell it's
	 * a half slab; the server also checks a player placed it (see {@link PlacedSlabs}).
	 */
	public static boolean isPrepSurface(Level level, BlockPos pos, BlockState state, Direction face) {
		if (state.getBlock() instanceof CuttingBoardBlock || state.getBlock() instanceof com.jaymoskitchen.delight.CuttingBoardBlock
			|| face == Direction.UP && state.getBlock() instanceof CounterBlock) {
			// a cutting board, or the top of a kitchen counter
			return true;
		}
		if (face != Direction.UP || !PlacedSlabs.isHalfSlab(state)) {
			return false;
		}
		return level.isClientSide() || PlacedSlabs.isPlayerSlab(level, pos, state);
	}

	public static int band(float level, float min, float max, float perfectMin, float perfectMax) {
		if (level >= perfectMin && level <= perfectMax) {
			return 2;
		}
		return level >= min && level <= max ? 1 : 0;
	}

	public static int waterBand(float level, float shift) {
		return band(level, WATER_MIN + shift, WATER_MAX + shift, WATER_PERFECT_MIN + shift, WATER_PERFECT_MAX + shift);
	}

	public static int oilBand(float level, float shift) {
		return band(level, OIL_MIN + shift, OIL_MAX + shift, OIL_PERFECT_MIN + shift, OIL_PERFECT_MAX + shift);
	}

	public static Result score(float water, float oil, boolean kneaded, float waterShift, float oilShift) {
		int w = waterBand(water, waterShift);
		int o = oilBand(oil, oilShift);
		if (!kneaded || w == 0 || o == 0) {
			return Result.RUINED;
		}
		return w == 2 && o == 2 ? Result.PERFECT : Result.GOOD;
	}

	/** Bit i set: recipe i can be made from what's in reach of the board. */
	public static int stocked(ServerPlayer player, BlockPos pos) {
		com.jaymoskitchen.kitchen.KitchenPool sources = Kitchen.sources(player, pos);
		int mask = 0;
		for (Recipe recipe : Recipe.values()) {
			if (Kitchen.hasIngredients(sources, ingredients(recipe))) {
				mask |= 1 << recipe.ordinal();
			}
		}
		return mask;
	}

	public static boolean isPrepping(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	public static void start(ServerPlayer player, BlockPos pos, Recipe recipe) {
		// a new mix replaces any stale one (e.g. left over from a disconnect)
		SESSIONS.remove(player.getUUID());
		if (!Kitchen.inReach(player, pos) || Grill.isGrilling(player)
			|| !isPrepSurface(player.level(), pos, player.level().getBlockState(pos), Direction.UP)) {
			reject(player, "message.jaymoskitchen.station_gone");
			return;
		}
		if (!player.hasInfiniteMaterials()) {
			if (!Kitchen.hasIngredients(Kitchen.sources(player, pos), ingredients(recipe))) {
				reject(player, "message.jaymoskitchen.missing_ingredients");
				return;
			}
			Kitchen.takeIngredients(player, pos, ingredients(recipe));
			if (recipe == Recipe.DOUGH) {
				// the mixing bowl is only borrowed
				Kitchen.give(player, new ItemStack(Items.BOWL));
			}
		}
		float waterShift = (player.getRandom().nextFloat() * 2.0F - 1.0F) * WATER_SHIFT;
		float oilShift = -OIL_SHIFT_DOWN + player.getRandom().nextFloat() * (OIL_SHIFT_DOWN + OIL_SHIFT_UP);
		SESSIONS.put(player.getUUID(),
			new Session(recipe, pos.immutable(), player.level().getServer().getTickCount(), waterShift, oilShift));
		player.level().playSound(null, pos, recipe == Recipe.JOINT ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.BOTTLE_FILL,
			SoundSource.BLOCKS, 1.0F, 1.0F);
		ServerPlayNetworking.send(player, new PrepUpdate(PrepUpdate.STATUS_STARTED, 0, waterShift, oilShift));
	}

	public static void finish(ServerPlayer player, float water, float oil, boolean kneaded) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || session.recipe != Recipe.DOUGH
			|| player.level().getServer().getTickCount() - session.startTick < MIN_TICKS) {
			return;
		}
		SESSIONS.remove(player.getUUID());
		Result result = score(water, oil, kneaded, session.waterShift, session.oilShift);
		reward(player, session, result, new ItemStack(ModItems.DOUGH, Math.max(1, result.amount())));
	}

	public static void finishRoll(ServerPlayer player, boolean ground, float evenness, float tears, boolean rolled) {
		Session session = SESSIONS.get(player.getUUID());
		if (session == null || session.recipe != Recipe.JOINT
			|| player.level().getServer().getTickCount() - session.startTick < Rolling.MIN_TICKS) {
			return;
		}
		SESSIONS.remove(player.getUUID());
		Result result = Rolling.score(ground, evenness, tears, rolled);
		ItemStack joints = new ItemStack(ModItems.JOINT, Math.max(1, result.amount()));
		JointItem.stamp(joints, result == Result.PERFECT ? JointQuality.PERFECT
			: tears >= Rolling.TORN_TEARS ? JointQuality.TORN : JointQuality.CLEAN);
		reward(player, session, result, joints);
	}

	private static void reward(ServerPlayer player, Session session, Result result, ItemStack made) {
		if (result.amount() > 0) {
			made.setCount(result.amount());
			Kitchen.give(player, made);
		}
		player.level().playSound(null, session.pos,
			result == Result.RUINED ? SoundEvents.SLIME_SQUISH : SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS,
			result == Result.PERFECT ? 1.0F : 0.6F, 1.0F);
		ServerPlayNetworking.send(player, new PrepUpdate(PrepUpdate.STATUS_FINISHED, result.ordinal(), 0.0F, 0.0F));
	}

	/** Walking away mid-mix wastes the ingredients. */
	public static void abandon(ServerPlayer player) {
		SESSIONS.remove(player.getUUID());
	}

	private static void reject(ServerPlayer player, String messageKey) {
		player.sendOverlayMessage(Component.translatable(messageKey));
		ServerPlayNetworking.send(player, new PrepUpdate(PrepUpdate.STATUS_REJECTED, 0, 0.0F, 0.0F));
	}
}
