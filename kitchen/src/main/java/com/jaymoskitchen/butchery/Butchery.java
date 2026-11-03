package com.jaymoskitchen.butchery;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.51) Andy: "add a hanging carcass and a butchery inspired by theirs" (Alex's Butchery's carcass hooks and blood
 * grates, ideas only: it's GPLv3 Forge 1.20.1 and needs Alex's Mobs). A carried (or dragged) carcass is hung on a
 * {@link MeatHookBlock}; a cleaver bleeds it out, into a {@link BloodGrateBlock} under it if there is one (else onto the
 * ground, as stains Jaymo's Powers keeps), then takes the head, a skinning knife its skin, and three cleaver cuts the
 * rest ({@code corpse/Hanging}). The grate's blood is bottled ({@link BloodBottleItem}); blood magic draws from it
 * ({@link #drawGrates}).
 */
public final class Butchery {
	/** Cleavers: open the throat, take the head, make the cuts. Axes and every cleaver in the pack. */
	public static final TagKey<Item> CLEAVERS = TagKey.create(Registries.ITEM, JaymosKitchen.id("cleavers"));
	/** Skinning knives: the kitchen's knives (an iron, primitive or bone knife). */
	public static final TagKey<Item> SKINNING_KNIVES = TagKey.create(Registries.ITEM, JaymosKitchen.id("skinning_knives"));
	/** What comes off with the skin: hides, leather, wool, feathers, pelts. */
	public static final TagKey<Item> SKINS = TagKey.create(Registries.ITEM, JaymosKitchen.id("skins"));
	/** A grate holds this many millibuckets a level, up to {@link BloodGrateBlock#MAX} levels; a bottle takes one level. */
	public static final int MB_PER_LEVEL = 250;

	public static MeatHookBlock MEAT_HOOK;
	public static BloodGrateBlock BLOOD_GRATE;
	public static Item BLOOD_BOTTLE;

	/**
	 * Someone drank a bottle of blood: each hook is asked in turn, and the first that says it's theirs (a vampire's
	 * thirst, a blood sorcerer's pool: Jaymo's Kingdoms) has it; nobody's, and it turns the stomach.
	 */
	private static final List<Predicate<ServerPlayer>> ON_DRINK = new ArrayList<>();

	private Butchery() {
	}

	public static void register() {
		ResourceKey<Block> hookKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("meat_hook"));
		MEAT_HOOK = Registry.register(BuiltInRegistries.BLOCK, hookKey, new MeatHookBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.CHAIN).noOcclusion().noCollision().setId(hookKey)));
		blockItem("meat_hook", MEAT_HOOK);
		ResourceKey<Block> grateKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("blood_grate"));
		BLOOD_GRATE = Registry.register(BuiltInRegistries.BLOCK, grateKey, new BloodGrateBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.IRON).noOcclusion().setId(grateKey)));
		blockItem("blood_grate", BLOOD_GRATE);
		ResourceKey<Item> bottleKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("blood_bottle"));
		BLOOD_BOTTLE = Registry.register(BuiltInRegistries.ITEM, bottleKey, new BloodBottleItem(new Item.Properties()
			.stacksTo(16).craftRemainder(Items.GLASS_BOTTLE).usingConvertsTo(Items.GLASS_BOTTLE).setId(bottleKey)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(new ItemStack(MEAT_HOOK));
			output.accept(new ItemStack(BLOOD_GRATE));
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(new ItemStack(BLOOD_BOTTLE)));
	}

	private static void blockItem(String id, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	/** (For Jaymo's Kingdoms, by reflection.) A bottle of blood drunk by a player is theirs if {@code hook} says so. */
	public static void addOnDrink(Predicate<ServerPlayer> hook) {
		ON_DRINK.add(hook);
	}

	static boolean drunkBySomeone(ServerPlayer player) {
		for (Predicate<ServerPlayer> hook : ON_DRINK) {
			try {
				if (hook.test(player)) {
					return true;
				}
			} catch (RuntimeException e) {
				JaymosKitchen.LOGGER.warn("A blood bottle hook failed", e);
			}
		}
		return false;
	}

	/** The meat hook a carcass can hang from at {@code pos}. */
	public static boolean isHook(BlockState state) {
		return MEAT_HOOK != null && state.is(MEAT_HOOK);
	}

	/**
	 * The blood grate that catches what drips from {@code from}: the first block under it (down to {@code depth} blocks),
	 * if that's a grate; null if it's anything else (the blood falls on the ground).
	 */
	public static @Nullable BlockPos grateUnder(ServerLevel level, Vec3 from, int depth) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(from).mutable();
		for (int i = 0; i <= depth; i++) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BLOOD_GRATE)) {
				return pos.immutable();
			}
			if (!state.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
				return null;
			}
			pos.move(0, -1, 0);
		}
		return null;
	}

	/** Pours {@code levels} into the grate at {@code pos}; returns how many didn't fit (spilt). */
	public static int pour(ServerLevel level, BlockPos pos, int levels) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(BLOOD_GRATE) || levels <= 0) {
			return levels;
		}
		int now = state.getValue(BloodGrateBlock.LEVEL);
		int fit = Math.min(levels, BloodGrateBlock.MAX - now);
		if (fit > 0) {
			level.setBlockAndUpdate(pos, state.setValue(BloodGrateBlock.LEVEL, now + fit));
		}
		return levels - fit;
	}

	/**
	 * (For Jaymo's Kingdoms' blood magic, by reflection; plain types only.) Draws up to {@code most} levels of blood out
	 * of the grates within {@code range} blocks of {@code at}, the nearest first; {@code drawn} hears where each grate
	 * is. Returns how many levels it took (each {@link #MB_PER_LEVEL} millibuckets).
	 */
	public static int drawGrates(ServerLevel level, Vec3 at, int range, int most, @Nullable Consumer<Vec3> drawn) {
		if (BLOOD_GRATE == null || most <= 0) {
			return 0;
		}
		List<BlockPos> found = new ArrayList<>();
		BlockPos centre = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-range, -range, -range), centre.offset(range, range, range))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BLOOD_GRATE) && state.getValue(BloodGrateBlock.LEVEL) > 0) {
				found.add(pos.immutable());
			}
		}
		found.sort(java.util.Comparator.comparingDouble(pos -> pos.distToCenterSqr(at)));
		int taken = 0;
		for (BlockPos pos : found) {
			if (taken >= most) {
				break;
			}
			BlockState state = level.getBlockState(pos);
			int there = state.getValue(BloodGrateBlock.LEVEL);
			int take = Math.min(there, most - taken);
			level.setBlockAndUpdate(pos, state.setValue(BloodGrateBlock.LEVEL, there - take));
			taken += take;
			if (drawn != null) {
				drawn.accept(Vec3.atCenterOf(pos));
			}
		}
		if (taken > 0) {
			level.playSound(null, centre, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.5F, 0.6F);
		}
		return taken;
	}
}
