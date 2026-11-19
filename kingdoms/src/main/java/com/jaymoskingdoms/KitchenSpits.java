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

	static {
		Block spit = null;
		Method skewer = null, tear = null, doneness = null, roasting = null;
		if (FabricLoader.getInstance().isModLoaded(KitchenCompat.MOD)) {
			try {
				Class<?> be = Class.forName("com.jaymoskitchen.cooking.spit.SpitBlockEntity");
				skewer = be.getMethod("skewer", ItemStack.class);
				tear = be.getMethod("tear", ServerLevel.class);
				doneness = be.getMethod("doneness");
				roasting = be.getMethod("roasting");
				spit = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(KitchenCompat.MOD, "spit")).orElse(null);
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

	/** The spit's own data at {@code pos}, or null if there's no spit there. */
	static @Nullable BlockEntity at(ServerLevel level, BlockPos pos) {
		if (SPIT == null || !level.getBlockState(pos).is(SPIT)) {
			return null;
		}
		return level.getBlockEntity(pos);
	}

	/** One of {@code raw} goes on the bare spit (shrinking it). True if it did. */
	static boolean skewer(BlockEntity spit, ItemStack raw) {
		Object out = call(SKEWER, spit, raw);
		return out instanceof Boolean b && b;
	}

	/** A cooked chunk torn off (empty if it wasn't done, or was burnt and is thrown out). */
	static ItemStack tear(BlockEntity spit, ServerLevel level) {
		Object out = call(TEAR, spit, level);
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
