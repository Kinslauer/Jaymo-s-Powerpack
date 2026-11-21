package com.jaymoskingdoms;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kitchen's spit (optional), for {@link Firesides}: the frame of sticks over a lit campfire that a camp's cook
 * roasts its meat on. Reached by reflection ({@code SpitBlockEntity.skewer}, {@code tear}, {@code doneness}), so Kingdoms
 * needs no Kitchen jar. Without Kitchen, or one too old to let anyone but a player use the spit, {@link #ready} is false
 * and nobody cooks.
 */
final class KitchenSpits {
	/** What Kitchen lets on the spit: raw meats and fish, and carcasses. */
	static final TagKey<Item> ROASTABLE = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "spit_foods"));
	/** Kitchen's doneness marks: cooked through, burnt. */
	static final float COOKED = 1.0F, BURNT = 2.0F;

	private static final @Nullable Block SPIT;
	private static final @Nullable Method SKEWER, TEAR, DONENESS, ROASTING;
	/**
	 * The great spit, for a whole beast (Kitchen's {@code GreatSpitBlock}), and what goes with it: a carcass put on whole
	 * ({@code skewerWhole}), the tear seen by whoever tears it ({@code tear(ServerLevel, Entity)}), and whether the bar's
	 * full ({@code full}). Null with a Kitchen too old for them: the cook roasts a piece at a time as before.
	 */
	private static final @Nullable Block GREAT;
	private static final @Nullable Method SKEWER_WHOLE, TEAR_BY, FULL;

	static {
		Block spit = null, great = null;
		Method skewer = null, tear = null, doneness = null, roasting = null, skewerWhole = null, tearBy = null, full = null;
		if (FabricLoader.getInstance().isModLoaded(KitchenCompat.MOD)) {
			try {
				Class<?> be = Class.forName("com.jaymoskitchen.cooking.spit.SpitBlockEntity");
				skewer = be.getMethod("skewer", ItemStack.class);
				tear = be.getMethod("tear", ServerLevel.class);
				doneness = be.getMethod("doneness");
				roasting = be.getMethod("roasting");
				spit = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "spit")).orElse(null);
				try {
					skewerWhole = be.getMethod("skewerWhole", net.minecraft.world.entity.Entity.class);
					tearBy = be.getMethod("tear", ServerLevel.class, net.minecraft.world.entity.Entity.class);
					full = be.getMethod("full");
					great = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "great_spit")).orElse(null);
				} catch (ReflectiveOperationException e) {
					JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for camp cooks to roast a whole beast: {}", e.toString());
					skewerWhole = null;
				}
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for camp cooks to use its spit: {}", e.toString());
				skewer = null;
			}
		}
		SPIT = skewer == null ? null : spit;
		SKEWER = skewer;
		TEAR = tear;
		DONENESS = doneness;
		ROASTING = roasting;
		GREAT = skewer == null || skewerWhole == null ? null : great;
		SKEWER_WHOLE = GREAT == null ? null : skewerWhole;
		TEAR_BY = GREAT == null ? null : tearBy;
		FULL = GREAT == null ? null : full;
	}

	private KitchenSpits() {
	}

	/** Kitchen's spit can be used by a camp's cook. */
	static boolean ready() {
		return SPIT != null;
	}

	/** The spit block (null without {@link #ready}). */
	static @Nullable Block block() {
		return SPIT;
	}

	/** The great spit (null without a Kitchen that has it). */
	static @Nullable Block great() {
		return GREAT;
	}

	/** The spit's own data at {@code pos} (a spit or a great spit), or null if there's no spit there. */
	static @Nullable BlockEntity at(ServerLevel level, BlockPos pos) {
		if (SPIT == null || !level.getBlockState(pos).is(SPIT) && (GREAT == null || !level.getBlockState(pos).is(GREAT))) {
			return null;
		}
		return level.getBlockEntity(pos);
	}

	/** The spit at {@code pos} is a great spit. */
	static boolean isGreat(ServerLevel level, BlockPos pos) {
		return GREAT != null && level.getBlockState(pos).is(GREAT);
	}

	/** No room on the bar for another piece (with an old Kitchen: anything on it). */
	static boolean full(BlockEntity spit) {
		if (FULL == null) {
			return roasting(spit);
		}
		return call(FULL, spit) instanceof Boolean b && b;
	}

	/** A dressed carcass ({@link KitchenCompat#dressForSpit}) goes on the bare great spit whole. True if it did. */
	static boolean skewerWhole(BlockEntity spit, net.minecraft.world.entity.Entity corpse) {
		return call(SKEWER_WHOLE, spit, corpse) instanceof Boolean b && b;
	}

	/** One of {@code raw} goes on the bare spit (shrinking it). True if it did. */
	static boolean skewer(BlockEntity spit, ItemStack raw) {
		Object out = call(SKEWER, spit, raw);
		return out instanceof Boolean b && b;
	}

	/** A cooked chunk torn off (empty if it wasn't done, or was burnt and is thrown out). */
	static ItemStack tear(BlockEntity spit, ServerLevel level, net.minecraft.world.entity.Entity by) {
		// everyone near sees them reach in and rip it off (Kitchen's tearing), with a Kitchen that knows who
		Object out = TEAR_BY != null ? call(TEAR_BY, spit, level, by) : call(TEAR, spit, level);
		return out instanceof ItemStack s ? s : ItemStack.EMPTY;
	}

	/** How done the roast is: 0 raw, {@link #COOKED}, {@link #BURNT}. */
	static float doneness(BlockEntity spit) {
		Object out = call(DONENESS, spit);
		return out instanceof Float f ? f : 0.0F;
	}

	/** Something is on the spit. */
	static boolean roasting(BlockEntity spit) {
		Object out = call(ROASTING, spit);
		return out instanceof ItemStack s && !s.isEmpty();
	}

	private static @Nullable Object call(@Nullable Method m, Object target, Object... args) {
		if (m == null) {
			return null;
		}
		try {
			return m.invoke(target, args);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
