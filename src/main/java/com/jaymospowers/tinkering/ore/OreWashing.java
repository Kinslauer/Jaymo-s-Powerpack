package com.jaymospowers.tinkering.ore;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.cauldron.CauldronBrewing;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.smeltery.Melting;
import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.28) Ore washing, Andy's pick from Gemini's GregTech start (phase 4). GregTech's hand route, on our ores:
 * <ul>
 * <li><b>Crushing:</b> raw iron, copper, gold or silver with a hammer ({@code #jaymospowers:hammers}: the smithing
 * hammers, the Tinkering sledge and vein hammers, a mace, the Aether hammers) in a crafting grid gives one crushed ore
 * ({@link HammerRecipe}; the hammer wears by {@link #HAMMER_WEAR}).</li>
 * <li><b>Washing:</b> crushed ore used on a water cauldron washes up to {@link #PER_LEVEL} of the stack for one level of
 * water; crushed ore thrown in one washes after {@link #CAULDRON_SOAK} ticks (a level per {@link #PER_LEVEL}); crushed
 * ore left in running water (not still: a source block won't do) washes after {@link #RIVER_SOAK} ticks, for nothing.
 * A cauldron holding a brew is left alone.</li>
 * <li><b>What it's for:</b> every ore washed has a {@link #BYPRODUCT_CHANCE} chance to give up a nugget of the metal
 * that rides along with it ({@link Ore#byproduct}, after GregTech's byproducts where the pack has the metal), and
 * washed ore smelts at full speed in a plain furnace, where raw and crushed ore smelt slowly
 * ({@code jaymospowers:slow_smelt}). In the smeltery crushed and washed ore melt as raw ore does.</li>
 * </ul>
 */
public final class OreWashing {
	/** Wear a hammer takes for each ore it crushes. */
	public static final int HAMMER_WEAR = 1;
	/** How many crushed ore one level of a cauldron's water washes. */
	public static final int PER_LEVEL = 8;
	/** Ticks crushed ore must lie in a water cauldron, or in running water, before it's washed. */
	public static final int CAULDRON_SOAK = 40, RIVER_SOAK = 200;
	/** The chance each washed ore gives a nugget of its byproduct. */
	public static final float BYPRODUCT_CHANCE = 0.15F;
	/** How often (ticks) the loaded worlds are looked over for crushed ore in running water. */
	static final int RIVER_EVERY = 20;

	/** The ores that crush and wash, what they melt into, and what rides along with them. */
	public enum Ore {
		IRON("iron", () -> Items.RAW_IRON, () -> Items.IRON_INGOT, () -> Items.COPPER_NUGGET, Molten.IRON),
		COPPER("copper", () -> Items.RAW_COPPER, () -> Items.COPPER_INGOT, () -> Items.GOLD_NUGGET, Molten.COPPER),
		GOLD("gold", () -> Items.RAW_GOLD, () -> Items.GOLD_INGOT, () -> Tinkering.SILVER_NUGGET, Molten.GOLD),
		SILVER("silver", () -> Tinkering.RAW_SILVER, () -> Tinkering.SILVER_INGOT, () -> Items.COPPER_NUGGET, Molten.SILVER);

		public final String id;
		final Supplier<Item> raw, ingot, byproduct;
		final Molten molten;
		public Item crushed, purified;

		Ore(String id, Supplier<Item> raw, Supplier<Item> ingot, Supplier<Item> byproduct, Molten molten) {
			this.id = id;
			this.raw = raw;
			this.ingot = ingot;
			this.byproduct = byproduct;
			this.molten = molten;
		}
	}

	/** How long each crushed ore item on the ground has soaked so far. */
	private static final Map<ItemEntity, Integer> SOAKED = new WeakHashMap<>();

	private OreWashing() {
	}

	/** After {@link Tinkering#init} (silver). */
	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, JaymosPowers.id("hammer_crafting"), HammerRecipe.SERIALIZER);
		for (Ore ore : Ore.values()) {
			ore.crushed = item("crushed_" + ore.id + "_ore");
			ore.purified = item("purified_" + ore.id + "_ore");
			Melting.ores(ore.molten, ore.crushed, ore.purified);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Ore ore : Ore.values()) {
				output.accept(ore.crushed);
				output.accept(ore.purified);
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			Ore ore = crushed(stack);
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (ore == null || player.isSpectator() || !washes(level, pos, state)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				int count = Math.min(stack.getCount(), PER_LEVEL);
				stack.shrink(count);
				ItemStack washed = new ItemStack(ore.purified, count);
				if (!player.getInventory().add(washed)) {
					player.drop(washed, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				byproducts(server, ore, count, net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0));
				LayeredCauldronBlock.lowerFillLevel(state, level, pos);
				splash(server, net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0));
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_LEVEL_TICK.register(OreWashing::rivers);
	}

	private static Item item(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/** The ore {@code stack} is crushed (unwashed) ore of, or null. */
	static @Nullable Ore crushed(ItemStack stack) {
		for (Ore ore : Ore.values()) {
			if (ore.crushed != null && stack.is(ore.crushed)) {
				return ore;
			}
		}
		return null;
	}

	/** A water cauldron that holds no brew. */
	private static boolean washes(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
		return state.is(Blocks.WATER_CAULDRON) && state.hasProperty(LayeredCauldronBlock.LEVEL) && CauldronBrewing.get(level, pos) == null;
	}

	/**
	 * Crushed ore thrown into a water cauldron (every tick it's in there, from {@code CauldronBrewItemMixin}): once it has
	 * soaked {@link #CAULDRON_SOAK}, as much as the water washes comes out washed. True if this was crushed ore (so
	 * brewing leaves it be).
	 */
	public static boolean itemInside(ServerLevel level, BlockPos pos, BlockState state, ItemEntity item) {
		Ore ore = crushed(item.getItem());
		if (ore == null || !item.isAlive()) {
			return false;
		}
		if (!washes(level, pos, state)) {
			return true;
		}
		int soaked = SOAKED.merge(item, 1, Integer::sum);
		if (soaked < CAULDRON_SOAK) {
			return true;
		}
		SOAKED.remove(item);
		int levels = state.getValue(LayeredCauldronBlock.LEVEL);
		int count = Math.min(item.getItem().getCount(), levels * PER_LEVEL);
		int used = (count + PER_LEVEL - 1) / PER_LEVEL;
		wash(level, item, ore, count);
		BlockState now = state;
		for (int i = 0; i < used && now.is(Blocks.WATER_CAULDRON); i++) {
			LayeredCauldronBlock.lowerFillLevel(now, level, pos);
			now = level.getBlockState(pos);
		}
		return true;
	}

	/** Crushed ore lying in running water washes, for nothing. */
	private static void rivers(ServerLevel level) {
		if (level.getGameTime() % RIVER_EVERY != 0) {
			return;
		}
		for (ItemEntity item : level.getEntities(EntityTypeTest.forClass(ItemEntity.class), e -> e.isAlive() && crushed(e.getItem()) != null)) {
			FluidState fluid = level.getFluidState(item.blockPosition());
			if (!fluid.is(FluidTags.WATER) || fluid.isSource()) {
				continue;
			}
			int soaked = SOAKED.merge(item, RIVER_EVERY, Integer::sum);
			if (soaked >= RIVER_SOAK) {
				SOAKED.remove(item);
				wash(level, item, crushed(item.getItem()), item.getItem().getCount());
			}
		}
	}

	/** Washes {@code count} of the crushed ore {@code item} holds where it lies: washed ore, and now and then a byproduct. */
	private static void wash(ServerLevel level, ItemEntity item, Ore ore, int count) {
		ItemStack stack = item.getItem();
		ItemStack washed = new ItemStack(ore.purified, count);
		if (count >= stack.getCount()) {
			item.setItem(washed);
		} else {
			stack.shrink(count);
			item.setItem(stack.copy());
			level.addFreshEntity(new ItemEntity(level, item.getX(), item.getY(), item.getZ(), washed));
		}
		byproducts(level, ore, count, item.position());
		splash(level, item.position());
	}

	private static void byproducts(ServerLevel level, Ore ore, int count, net.minecraft.world.phys.Vec3 at) {
		int nuggets = 0;
		for (int i = 0; i < count; i++) {
			if (level.getRandom().nextFloat() < BYPRODUCT_CHANCE) {
				nuggets++;
			}
		}
		Item byproduct = ore.byproduct.get();
		if (nuggets > 0 && byproduct != null) {
			level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(byproduct, nuggets)));
		}
	}

	private static void splash(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.3F);
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 10, 0.25, 0.1, 0.25, 0.05);
	}
}
