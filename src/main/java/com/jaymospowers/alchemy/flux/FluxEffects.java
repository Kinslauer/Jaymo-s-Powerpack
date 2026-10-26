package com.jaymospowers.alchemy.flux;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Flux;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Research;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.core.component.DataComponents;

/**
 * What flux does once it has gathered (see docs/Alchemy-Plan.md, track C). Three layers:
 * <ul>
 * <li>{@link FluxLife}: every chunk near a player fades, spreads to its neighbours, and above {@link #BLIGHT_AT} blights
 * its land, sickens its animals ({@link BlightedEffect}) and, above {@link #WISP_AT}, breeds {@link FluxWisp}s at night.</li>
 * <li>{@link FluxCorruption}: what standing in flux does to a person, from whispers to a wisp that hunts them.</li>
 * <li>The cures: {@link PurifyingSalts}, {@link PurifyingDraughtItem}, the {@link #WARD_STONE} and, for deep corruption,
 * {@link Cures}.</li>
 * </ul>
 * This class registers all of it and holds the tuning every part shares. Other parts of alchemy call {@link #burst}.
 */
public final class FluxEffects {
	// ---- tuning (chunk flux is 0..Flux.MAX) ----
	/** Flux a chunk loses on each visit of the life tick (see FluxLife.VISIT_INTERVAL for how often that is). */
	public static final int FADE = 2;
	/** Above this a chunk gives flux to its neighbours. */
	public static final int SPREAD_AT = 300;
	/** Above this the land blights and animals sicken. */
	public static final int BLIGHT_AT = 300;
	/** Above this, at night, Flux Wisps drift out. */
	public static final int WISP_AT = 800;
	/** Flux a Ward Stone drains from its chunk each {@link #WARD_INTERVAL} ticks (5 s). */
	public static final int WARD_DRAIN = 6, WARD_INTERVAL = 100;
	/** Flux (per chunk) at which standing in it starts to give fleeting corruption. */
	public static final int CORRUPT_AT = 100;
	/** Deep corruption the forbidden entry "whispers" gives when learned. */
	public static final int WHISPERS_DEEP = 5;

	public static Block BLIGHTED_SOIL, BLIGHT_GROWTH, FLUX_GOO, WITHERED_LEAVES, WARD_STONE;
	public static Item PURIFYING_SALTS, PURIFYING_DRAUGHT, SHADOW_INK, WISP_ITEM;
	public static EntityType<FluxWisp> WISP;
	public static EntityType<PurifyingSalts> SALTS_ENTITY;
	public static Holder<MobEffect> BLIGHTED;

	private FluxEffects() {
	}

	public static void init() {
		BLIGHTED_SOIL = block("blighted_soil", BlightedSoilBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6F)
			.sound(SoundType.GRAVEL).randomTicks());
		BLIGHT_GROWTH = block("blight_growth", BlightGrowthBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).instabreak().noCollision()
			.sound(SoundType.GRASS).pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ));
		FLUX_GOO = block("flux_goo", FluxGooBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.1F).noCollision()
			.noOcclusion().replaceable().noLootTable().sound(SoundType.SLIME_BLOCK).pushReaction(PushReaction.POPPED).randomTicks());
		WITHERED_LEAVES = block("withered_leaves", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.2F)
			.sound(SoundType.GRASS).noOcclusion().isViewBlocking((state, level, pos, box) -> false).isSuffocating((state, level, pos) -> false)
			.isRedstoneConductor((state, level, pos) -> false));
		WARD_STONE = block("ward_stone", WardStoneBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 6.0F)
			.sound(SoundType.STONE).lightLevel(state -> 5).requiresCorrectToolForDrops());

		PURIFYING_SALTS = Alchemy.item("purifying_salts", p -> new PurifyingSaltsItem(p.stacksTo(16)));
		PURIFYING_DRAUGHT = Alchemy.item("purifying_draught", p -> new PurifyingDraughtItem(p.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));
		SHADOW_INK = Alchemy.item("shadow_ink", Item::new);
		// the wisp's sprite only: not in the creative tab
		ResourceKey<Item> wispItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id("flux_wisp"));
		WISP_ITEM = Registry.register(BuiltInRegistries.ITEM, wispItem, new Item(new Item.Properties().setId(wispItem)));

		ResourceKey<EntityType<?>> wispKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("flux_wisp"));
		WISP = Registry.register(BuiltInRegistries.ENTITY_TYPE, wispKey, EntityType.Builder.of(FluxWisp::new, MobCategory.MONSTER)
			.sized(0.4F, 0.4F).eyeHeight(0.2F).noLootTable().clientTrackingRange(8).notInPeaceful().build(wispKey));
		FabricDefaultAttributeRegistry.register(WISP, FluxWisp.createAttributes());
		ResourceKey<EntityType<?>> saltsKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("purifying_salts"));
		SALTS_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, saltsKey, EntityType.Builder.<PurifyingSalts>of(PurifyingSalts::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(saltsKey));

		BLIGHTED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("blighted")),
			new BlightedEffect());

		registerResearch();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			FluxLife.tick(server);
			FluxCorruption.tick(server);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			// the fleeting kind does not survive death (coming back from the End isn't one)
			if (alive) {
				return;
			}
			Corruption.State state = Corruption.of(newPlayer);
			if (state.fleeting() > 0) {
				newPlayer.setAttached(Corruption.CORRUPTION, state.with(Corruption.Kind.FLEETING, 0));
			}
		});
		ServerPlayerEvents.LEAVE.register(FluxCorruption::forget);
		Knowledge.ON_UNLOCK.add((player, entry) -> {
			if (entry.id().equals("whispers")) {
				Corruption.add(player, Corruption.Kind.DEEP, WHISPERS_DEEP);
			}
		});
		Cures.init();
	}

	private static void registerResearch() {
		Research.Category tab = Research.Category.BLIGHT;
		Research.register(new Research.Entry("flux", tab, () -> Alchemy.ESSENCE_PHIAL,
			EssenceList.of(Essence.ARCANA, 10, Essence.ROT, 5), List.of("crucible"), 0, 0, 1));
		Research.register(new Research.Entry("blight", tab, () -> BLIGHTED_SOIL.asItem(),
			EssenceList.of(Essence.ROT, 8, Essence.PLANT, 6), List.of("flux"), 0, 1, 0));
		Research.register(new Research.Entry("cleansing", tab, () -> PURIFYING_SALTS,
			EssenceList.of(Essence.SALT, 12, Essence.LIGHT, 8).plus(Essence.LIFE, 6), List.of("blight"), 0, 2, 0));
		Research.register(new Research.Entry("whispers", tab, () -> SHADOW_INK,
			EssenceList.of(Essence.SHADOW, 10, Essence.MIND, 8).plus(Essence.FLUX, 6), List.of("flux"), 50, 1, 2));
	}

	/**
	 * A burst of raw flux at a place (an infusion gone wrong, a crucible boiling over): adds {@code flux} to the chunk,
	 * throws purple motes and a hiss, and gives fleeting corruption (a tenth of the flux, at most 30) to every player
	 * within 4 blocks.
	 */
	public static void burst(ServerLevel level, BlockPos pos, int flux) {
		if (flux <= 0) {
			return;
		}
		Flux.add(level, pos, flux);
		double x = pos.getX() + 0.5, y = pos.getY() + 0.6, z = pos.getZ() + 0.5;
		int count = Math.min(60, 6 + flux / 4);
		level.sendParticles(new DustParticleOptions(Essence.FLUX.colour, 1.2F), x, y, z, count, 0.5, 0.4, 0.5, 0.05);
		level.sendParticles(ParticleTypes.SMOKE, x, y, z, Math.min(20, count / 2), 0.4, 0.3, 0.4, 0.02);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 0.6F);
		int soak = Math.min(30, Math.max(1, flux / 10));
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(x, y, z) <= 16.0) {
				Corruption.add(player, Corruption.Kind.FLEETING, soak);
			}
		}
	}

	/** Dirt and grass flux can sour into Blighted Soil. */
	public static boolean soil(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.PODZOL)
			|| state.is(Blocks.MYCELIUM) || state.is(Blocks.ROOTED_DIRT);
	}

	/** Registers a block and its item (named with the block prefix, in the Alchemy tab). */
	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		Alchemy.item(id, p -> new BlockItem(block, p.useBlockDescriptionPrefix()));
		return block;
	}
}
