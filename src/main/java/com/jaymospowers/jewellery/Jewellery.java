package com.jaymospowers.jewellery;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.tinkering.kiln.Kiln;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.jaymospowers.tinkering.smithing.Grade;
import com.jaymospowers.tinkering.smithing.Grades;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.87) Jewellery (docs/Jewellery-Plan.md): cut a gem on the Lapidary Wheel, hammer a gold, silver or copper frame
 * on the Smithing Anvil, set one in the other at the Jeweller's Bench. The piece is graded like forged gear (the lower
 * of the frame's and the gem's grade) and gives a small bonus from its gem while worn as a bauble.
 */
public final class Jewellery {
	/** How much each grade scales a bonus. */
	public static final Map<Grade, Float> GRADE_FACTOR = new EnumMap<>(Grade.class);
	/** How much each metal scales a bonus. */
	public static final Map<Molten, Float> METAL_FACTOR = new EnumMap<>(Molten.class);

	static {
		GRADE_FACTOR.put(Grade.POOR, 0.5F);
		GRADE_FACTOR.put(Grade.WELL, 1.0F);
		GRADE_FACTOR.put(Grade.EXPERT, 1.25F);
		GRADE_FACTOR.put(Grade.PERFECT, 1.5F);
		GRADE_FACTOR.put(Grade.MASTER, 2.0F);
		METAL_FACTOR.put(Molten.COPPER, 0.8F);
		METAL_FACTOR.put(Molten.SILVER, 1.0F);
		METAL_FACTOR.put(Molten.GOLD, 1.15F);
	}

	public static DataComponentType<CutGem> CUT_GEM;
	/** A frame's or piece's metal, a {@link Molten} id. */
	public static DataComponentType<String> JEWEL_METAL;
	public static Item CUT_GEM_ITEM;
	public static final Map<JewelKind, Item> FRAMES = new EnumMap<>(JewelKind.class);
	public static final Map<JewelKind, Item> PIECES = new EnumMap<>(JewelKind.class);
	public static Block JEWELLERS_BENCH, LAPIDARY_WHEEL;
	public static BlockEntityType<LapidaryWheelEntity> LAPIDARY_WHEEL_ENTITY;
	public static MenuType<JewellersBenchMenu> BENCH_MENU;

	private Jewellery() {
	}

	public static void init() {
		CUT_GEM = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("cut_gem"),
			DataComponentType.<CutGem>builder().persistent(CutGem.CODEC).networkSynchronized(CutGem.STREAM_CODEC).build());
		JEWEL_METAL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("jewel_metal"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

		CUT_GEM_ITEM = item("cut_gem", CutGemItem::new, p -> p.stacksTo(16));
		for (JewelKind kind : JewelKind.values()) {
			FRAMES.put(kind, item(kind.frameId, JewelFrameItem::new, p -> p.stacksTo(1)));
			PIECES.put(kind, item(kind.pieceId, p -> new JewelleryItem(p, kind), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON)));
		}

		JEWELLERS_BENCH = Kiln.blockWithTooltip("jewellers_bench", JewellersBenchBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD));
		LAPIDARY_WHEEL = Kiln.blockWithTooltip("lapidary_wheel", LapidaryWheelBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
		LAPIDARY_WHEEL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("lapidary_wheel"),
			new BlockEntityType<>(LapidaryWheelEntity::new, Set.of(LAPIDARY_WHEEL)));
		BENCH_MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("jewellers_bench"),
			new MenuType<>(JewellersBenchMenu::new, FeatureFlags.VANILLA_SET));

		JewelFrames.register();
		for (Gems.Gem gem : Gems.ALL) {
			Baubles.touch(gem.attribute());
		}
		Baubles.picture(Baubles.Kind.WRIST, PIECES.get(JewelKind.BRACELET));
		JewelleryNet.register();
		com.jaymospowers.jewellery.sockets.Sockets.register(); // (0.20.7) gem sockets in forged gear

		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(JEWELLERS_BENCH);
			output.accept(LAPIDARY_WHEEL);
			for (JewelKind kind : JewelKind.values()) {
				ItemStack frame = JewelFrameItem.of(kind, Molten.SILVER);
				Grades.set(frame, Grade.WELL);
				output.accept(frame);
			}
			for (Gems.Gem gem : Gems.ALL) {
				output.accept(CutGemItem.of(gem, Cut.TABLE, Grade.WELL, 0));
			}
			Gems.Gem emerald = Gems.byId("emerald");
			for (JewelKind kind : JewelKind.values()) {
				ItemStack frame = JewelFrameItem.of(kind, Molten.SILVER);
				Grades.set(frame, Grade.WELL);
				output.accept(piece(frame, CutGemItem.of(emerald, Cut.TABLE, Grade.WELL, 0)));
			}
			// (0.20.7) a Masterwork sword with two empty gem sockets
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			Grades.set(sword, Grade.MASTER);
			output.accept(sword);
		});
	}

	/** The piece set from this frame and cut gem, or EMPTY. Server and client (the bench's result). */
	public static ItemStack piece(ItemStack frame, ItemStack gem) {
		JewelKind kind = frame.getItem() instanceof JewelFrameItem ? JewelKind.byFrame(frame.getItem()) : null;
		String metal = frame.get(JEWEL_METAL);
		CutGem cut = gem.get(CUT_GEM);
		Gems.Gem type = cut == null ? null : cut.gemType();
		if (kind == null || metal == null || Grades.of(frame) == null || type == null) {
			return ItemStack.EMPTY;
		}
		ItemStack out = new ItemStack(PIECES.get(kind));
		out.set(JEWEL_METAL, metal);
		out.set(CUT_GEM, cut);
		Grades.carry(frame, out);
		Grades.set(out, Grade.min(Grades.of(out), cut.grade()));
		out.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(metal, cut.cut()), List.of(type.colour())));
		out.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers." + kind.pieceId + ".named", metalName(metal), type.displayName()));
		return out;
	}

	/** Why this frame can't be set yet (still hot), or null. */
	public static @Nullable Component refusal(ItemStack frame) {
		return Grades.refusal(frame);
	}

	/** The bonus a worn piece gives, or null if it has no gem, metal or grade. */
	public static BaubleItem.@Nullable Bonus bonus(ItemStack piece) {
		JewelKind kind = JewelKind.byPiece(piece.getItem());
		CutGem cut = piece.get(CUT_GEM);
		String metalId = piece.get(JEWEL_METAL);
		Grade grade = Grades.of(piece);
		Molten metal = metalId == null ? null : Molten.byId(metalId);
		Gems.Gem gem = cut == null ? null : cut.gemType();
		if (kind == null || gem == null || metal == null || grade == null || !METAL_FACTOR.containsKey(metal)) {
			return null;
		}
		double scale = GRADE_FACTOR.get(grade) * cut.cutType().bonus * METAL_FACTOR.get(metal) * kind.bonus;
		return gem.bonus(scale);
	}

	public static Component metalName(String molten) {
		return Component.translatable("jewel_metal.jaymospowers." + molten);
	}

	static Item item(String id, Function<Item.Properties, Item> factory, Function<Item.Properties, Item.Properties> props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.apply(new Item.Properties().setId(key))));
	}
}
