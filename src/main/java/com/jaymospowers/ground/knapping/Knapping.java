package com.jaymospowers.ground.knapping;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.ground.GroundRocks;
import com.jaymospowers.ground.LooseStonesBlock;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.WeaponParts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Knapping (after Overgeared's idea, our own code): sneak and use a loose rock ({@link GroundRocks}) and a 3x3 grid of
 * stone chips opens. Knock chips out one at a time; when what is left is the shape of a stone head the Part Builder also
 * makes, that head appears in the output. Taking it uses up one rock. Knocking out every chip wastes the rock.
 * <p>
 * The gesture (it must not clash with the other uses of a rock): right click on air throws it ({@code ThrownRocks}), a
 * rock with flint in the other hand strikes fire ({@code FirstTools}), right click on a block sets it down. So knapping
 * is <b>sneak + use</b>, on the air or on a hard block (stone, ore, bricks); sneak + use on soft ground still sets the
 * rock down.
 */
public final class Knapping {
	/** The grid is this many cells a side. */
	public static final int SIDE = 3;
	public static final int CELLS = SIDE * SIDE;
	/** Every chip still on the rock. */
	public static final int FULL = (1 << CELLS) - 1;
	/** Rocks that make granite-grade and diorite-grade heads; every other rock gives plain stone ones. */
	private static final Map<GroundRocks.Variant, TinkerMaterial> MATERIALS = Map.of(GroundRocks.Variant.GRANITE, TinkerMaterial.ROCK_GRANITE,
		GroundRocks.Variant.DIORITE, TinkerMaterial.ROCK_DIORITE);
	/** The look of a chip, by rock (ARGB). */
	private static final Map<GroundRocks.Variant, Integer> COLOURS = Map.of(GroundRocks.Variant.STONE, 0xFF8A8A8A, GroundRocks.Variant.MOSSY, 0xFF7A8C6A,
		GroundRocks.Variant.GRANITE, 0xFF9C6B5B, GroundRocks.Variant.DIORITE, 0xFFBDBDBD, GroundRocks.Variant.ANDESITE, 0xFF878787,
		GroundRocks.Variant.DEEPSLATE, 0xFF4C4C56, GroundRocks.Variant.TUFF, 0xFF6F6F63, GroundRocks.Variant.SANDSTONE, 0xFFD6C68E,
		GroundRocks.Variant.RED_SANDSTONE, 0xFFB5613B);

	/**
	 * One shape: three rows of three, {@code #} a chip that stays and {@code .} one knocked out. {@code make} builds the
	 * head from the rock's material (null: its mod isn't loaded, so the shape does nothing).
	 */
	public record Shape(String id, String rows, Function<TinkerMaterial, @Nullable ItemStack> make) {
		public int mask() {
			int mask = 0;
			for (int i = 0; i < CELLS; i++) {
				if (rows.charAt(i) == '#') {
					mask |= 1 << i;
				}
			}
			return mask;
		}

		public boolean has(int cell) {
			return rows.charAt(cell) == '#';
		}

		/** The head this shape makes from this rock, or null. */
		public @Nullable ItemStack result(TinkerMaterial material) {
			return make.apply(material);
		}
	}

	/** The shapes, nine characters each (rows top to bottom). Tune what they give here. */
	public static final List<Shape> SHAPES = List.of(
		part("pick_head", "###" + "#.#" + "...", PartType.PICK_HEAD, 1),
		part("small_axe_head", "##." + "###" + "##.", PartType.SMALL_AXE_HEAD, 1),
		part("adze_head", "###" + "##." + "#..", PartType.ADZE_HEAD, 1),
		part("small_blade", ".#." + ".#." + "###", PartType.SMALL_BLADE, 1),
		// two arrow heads from one rock
		part("arrow_head", ".#." + "###" + "...", PartType.ARROW_HEAD, 2),
		weapon("dagger_blade", ".#." + ".#." + ".#.", "dagger_blade", 1),
		weapon("hatchet_head", "##." + "###" + ".#.", "hatchet_head", 1),
		weapon("spear_tip", ".#." + "###" + ".#.", "spear_tip", 1),
		weapon("skewer_head", "..#" + ".#." + "#..", "skewer_head", 1),
		weapon("knife_blade", "#.." + "##." + "#..", "knife_blade", 1));

	public static MenuType<KnappingMenu> MENU;
	private static final Identifier GESTURE = JaymosPowers.id("knapping");

	private Knapping() {
	}

	public static void init() {
		MENU = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("knapping"), new MenuType<>(KnappingMenu::new, FeatureFlags.VANILLA_SET));
		// ahead of ThrownRocks (right click on air throws) and the block use that sets a rock down
		UseItemCallback.EVENT.addPhaseOrdering(GESTURE, Event.DEFAULT_PHASE);
		UseItemCallback.EVENT.register(GESTURE, (player, level, hand) -> gesture(player, level, hand, null));
		UseBlockCallback.EVENT.addPhaseOrdering(GESTURE, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(GESTURE, (player, level, hand, hit) -> gesture(player, level, hand, hit.getBlockPos()));
		for (Shape shape : SHAPES) {
			if (SHAPES.stream().filter(other -> other.mask() == shape.mask()).count() > 1) {
				throw new IllegalStateException("two knapping shapes are the same: " + shape.id());
			}
		}
	}

	/** Sneak + use with a rock, on the air ({@code at} null) or on a hard block. */
	private static InteractionResult gesture(Player player, Level level, InteractionHand hand, @Nullable BlockPos at) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSpectator() || !player.isSecondaryUseActive() || !isRock(stack) || striking(player)) {
			return InteractionResult.PASS;
		}
		if (at != null && !hard(level, at, level.getBlockState(at))) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			open(server, hand);
		}
		return InteractionResult.SUCCESS;
	}

	/** Opens the grid, handing the held rocks to it (they come back when it closes). */
	public static void open(ServerPlayer player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		ItemStack moved = held.copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		if (player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new KnappingMenu(id, inventory, moved),
			Component.translatable("gui.jaymospowers.knapping"))).isEmpty()) {
			player.setItemInHand(hand, moved);
		}
	}

	/** A loose rock. */
	public static boolean isRock(ItemStack stack) {
		return variant(stack) != null;
	}

	public static GroundRocks.@Nullable Variant variant(ItemStack stack) {
		if (stack.getItem() instanceof BlockItem item) {
			for (Map.Entry<GroundRocks.Variant, Block> rock : GroundRocks.ROCKS.entrySet()) {
				if (rock.getValue() == item.getBlock()) {
					return rock.getKey();
				}
			}
		}
		return null;
	}

	/** The material a head knapped from this rock is. */
	public static TinkerMaterial material(ItemStack stack) {
		GroundRocks.Variant variant = variant(stack);
		TinkerMaterial material = variant == null ? null : MATERIALS.get(variant);
		return material != null ? material : TinkerMaterial.ROCK_STONE;
	}

	/** How a chip of this rock is drawn (ARGB). */
	public static int colour(ItemStack stack) {
		GroundRocks.Variant variant = variant(stack);
		Integer colour = variant == null ? null : COLOURS.get(variant);
		return colour != null ? colour : 0xFF8A8A8A;
	}

	/** The shape the chips left make, or null. */
	public static @Nullable Shape match(int mask, TinkerMaterial material) {
		for (Shape shape : SHAPES) {
			if (shape.mask() == mask && shape.result(material) != null) {
				return shape;
			}
		}
		return null;
	}

	/** The shapes that do something (weapon heads of a mod that isn't loaded are left out). */
	public static List<Shape> available() {
		List<Shape> out = new ArrayList<>();
		for (Shape shape : SHAPES) {
			if (shape.result(TinkerMaterial.ROCK_STONE) != null) {
				out.add(shape);
			}
		}
		return out;
	}

	/** The item a shape shows as (its head in plain stone). */
	public static ItemStack icon(Shape shape) {
		ItemStack made = shape.result(TinkerMaterial.ROCK_STONE);
		return made != null ? made : ItemStack.EMPTY;
	}

	/** A rock in one hand and flint in the other strikes fire instead. */
	private static boolean striking(Player player) {
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		return main.is(Items.FLINT) && isRock(off) || isRock(main) && off.is(Items.FLINT);
	}

	/** Stone, bricks, ore and the like: anything that wants a pickaxe and isn't soft (as {@code FirstTools}). */
	private static boolean hard(Level level, BlockPos pos, BlockState state) {
		return !(state.getBlock() instanceof LooseStonesBlock) && state.requiresCorrectToolForDrops() && state.getDestroySpeed(level, pos) >= 1.0F
			&& state.is(BlockTags.MINEABLE_WITH_PICKAXE);
	}

	private static Shape part(String id, String rows, PartType part, int count) {
		return new Shape(id, rows, material -> ToolPartItem.of(part, material, count));
	}

	private static Shape weapon(String id, String rows, String head, int count) {
		return new Shape(id, rows, material -> {
			WeaponParts.Kind kind = WeaponParts.KINDS.get(head);
			return kind == null || !kind.loaded() ? null : WeaponPartItem.of(kind, WeaponParts.Metal.STONE, count);
		});
	}
}
