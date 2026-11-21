package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.jaymospowers.tinkering.smithing.Forging;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * The tackle bench's fittings: a hook, a line and a float fitted to any rod ({@link #FIT} on the rod, set at the bench,
 * {@link TackleBenchMenu}). A barbed hook keeps a fish on (it takes line back slower); a braided line takes more strain
 * before it snaps; a weighted line sinks the hook fast, bait or lure; a float is colour only, a bead of dye on the line
 * over the bobber (drawn by the client's hook renderer). Barbed hooks are forged at the Smithing Anvil from iron, like
 * the pack's other iron gear. Also the wet fishers' scraps ({@link #TORN_NOTE}): torn notes in the crates and the
 * shipwrecks, each hinting where one rare fish bites, read into the Fisher's Journal ({@link FishLog}).
 */
public final class FishTackle {
	/** A barbed hook keeps this share of the line a fish takes back while it's played (1 = no help). */
	static final float BARBED_HOLD = 0.5F;
	/** A braided line breaks at this many times the strain of the plain one. */
	static final float BRAIDED_STRAIN = 1.3F;
	/** A weighted line sinks the hook this many times as fast as a lure does (bait on a float rod, too). */
	static final float WEIGHTED_FASTER = 1.6F;
	/** Barbed hooks one iron ingot is forged into. */
	static final int HOOKS_PER_INGOT = 4;
	/** A note turns up in this share of the crates and shipwreck chests. */
	static final float NOTE_IN_CRATE = 0.3F, NOTE_IN_WRECK = 0.25F;

	/** The rare fish a torn note can hint at, by species id (the journal has a line for each: gui.jaymospowers.journal.hint.*). */
	public static final List<String> HINTS = List.of("cavefish", "olm", "ember_tilapia", "cinder_pupfish");

	public static final int NO_HOOK = 0, BARBED = 1;
	public static final int NO_LINE = 0, BRAIDED = 1, WEIGHTED = 2;

	/** What is fitted to a rod: its hook and line (see above) and the float's dye (an item id, empty for none). */
	public record Fit(int hook, int line, String bead) {
		public static final Fit NONE = new Fit(NO_HOOK, NO_LINE, "");
		public static final Codec<Fit> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("hook", NO_HOOK).forGetter(Fit::hook),
			Codec.INT.optionalFieldOf("line", NO_LINE).forGetter(Fit::line),
			Codec.STRING.optionalFieldOf("bead", "").forGetter(Fit::bead)).apply(i, Fit::new));

		public boolean isNone() {
			return hook == NO_HOOK && line == NO_LINE && bead.isEmpty();
		}
	}

	public static DataComponentType<Fit> FIT;
	/** A torn note's hint: which fish (see {@link #HINTS}). */
	public static DataComponentType<String> HINT;
	public static Item BARBED_HOOK, BRAIDED_LINE, WEIGHTED_LINE, TORN_NOTE;
	public static Block TACKLE_BENCH;

	private FishTackle() {
	}

	static void init() {
		FIT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rod_fit"),
			DataComponentType.<Fit>builder().persistent(Fit.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(Fit.CODEC)).build());
		HINT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("note_hint"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		BARBED_HOOK = Tackle.register("barbed_hook", new Item(Tackle.props("barbed_hook")));
		BRAIDED_LINE = Tackle.register("braided_line", new Item(Tackle.props("braided_line")));
		WEIGHTED_LINE = Tackle.register("weighted_line", new Item(Tackle.props("weighted_line")));
		ResourceKey<Item> noteKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("torn_note"));
		TORN_NOTE = Registry.register(BuiltInRegistries.ITEM, noteKey, new TornNote(new Item.Properties().setId(noteKey)));
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("tackle_bench"));
		TACKLE_BENCH = Registry.register(BuiltInRegistries.BLOCK, key,
			new TackleBenchBlock(BlockBehaviour.Properties.of().setId(key).strength(2.0F).sound(SoundType.WOOD)));
		ResourceKey<Item> benchItem = ResourceKey.create(Registries.ITEM, JaymosPowers.id("tackle_bench"));
		Registry.register(BuiltInRegistries.ITEM, benchItem,
			new net.minecraft.world.item.BlockItem(TACKLE_BENCH, new Item.Properties().setId(benchItem).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(TACKLE_BENCH));
		TackleBenchMenu.init();
		// the iron hooks are hammered out at the Smithing Anvil, a few to the ingot
		Forging.PROVIDERS.add(() -> List.of(new Forging.Option("fishing/barbed_hook", new ItemStack(BARBED_HOOK, HOOKS_PER_INGOT), Molten.IRON, 1,
			Forging.hitsFor(1))));
		// a torn note in some crates, and in the shipwrecks' chests
		LootTableEvents.MODIFY.register((table, builder, source, registries) -> {
			float chance = 0.0F;
			if (table.equals(BuiltInLootTables.SHIPWRECK_TREASURE) || table.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)
				|| table.equals(BuiltInLootTables.SHIPWRECK_MAP)) {
				chance = NOTE_IN_WRECK;
			} else {
				for (Crates.Water water : Crates.Water.values()) {
					if (table.equals(water.loot)) {
						chance = NOTE_IN_CRATE;
					}
				}
			}
			if (chance > 0.0F) {
				LootPool.Builder pool = LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(chance));
				for (String hint : HINTS) {
					pool.add(LootItem.lootTableItem(TORN_NOTE).apply(SetComponentsFunction.setComponent(HINT, hint)));
				}
				builder.withPool(pool);
			}
		});
	}

	/** What is fitted to {@code rod}. */
	public static Fit fit(ItemStack rod) {
		Fit fit = FIT == null ? null : rod.get(FIT);
		return fit == null ? Fit.NONE : fit;
	}

	/** The rod {@code player} fishes with, as fitted. */
	private static Fit fit(Player player) {
		return fit(HookBait.rod(player));
	}

	/** How much of the line a fish takes back that the player's hook lets it (1 = all of it). */
	static float lineBack(Player player) {
		return fit(player).hook() == BARBED ? BARBED_HOLD : 1.0F;
	}

	/** How many times the plain line's strain the player's line takes. */
	static float strain(Player player) {
		return fit(player).line() == BRAIDED ? BRAIDED_STRAIN : 1.0F;
	}

	/** The player's line is weighted. */
	static boolean weighted(Player player) {
		return fit(player).line() == WEIGHTED;
	}

	/** The float's dye as an item, or null. */
	public static Item bead(Fit fit) {
		Identifier id = fit.bead().isEmpty() ? null : Identifier.tryParse(fit.bead());
		return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}
}
