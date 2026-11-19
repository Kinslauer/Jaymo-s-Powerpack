package com.jaymoskitchen.cooking.spit;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.corpse.CorpseEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import org.jspecify.annotations.Nullable;

/**
 * The great spit (Andy, 2026-11-19: "yes", to a big-game spit for whole gutted carcasses, "torn apart to feed a group"):
 * the spit's frame built wide (kitchen/tools/gen_great_spit.py), its crossbar running {@link #AXIS} across the fire, for
 * a whole beast. A carcass that's been skinned and gutted ({@link CorpseEntity#dressedWhole}: a sheep, a pig, a calf, a
 * deer, anything a meat hook takes) is carried or dragged onto it and turns there whole, slowly
 * ({@link SpitBlockEntity#WHOLE_COOK_TICKS}). Done, it's torn apart a piece at a time, as many pieces as there's meat on
 * it, and its bones come away with the last. Anything the plain spit takes goes on it too.
 */
public class GreatSpitBlock extends SpitBlock {
	public static Block GREAT_SPIT;
	public static Item ITEM;
	/** Which way the crossbar runs. */
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	/** How far a dragged carcass can be heaved up onto it (blocks, beyond its own height). */
	public static final double DRAG_ONTO = 3.0;

	static void registerGreat() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("great_spit"));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD)
			.instrument(NoteBlockInstrument.BASS)
			.strength(2.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.setId(key);
		GREAT_SPIT = Registry.register(BuiltInRegistries.BLOCK, key, new GreatSpitBlock(properties));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("great_spit"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
			new BlockItem(GREAT_SPIT, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(output -> output.accept(new ItemStack(ITEM)));
	}

	public GreatSpitBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		// the bar runs across the way the one setting it up faces, so the beast turns side-on in front of them
		return state == null ? null : state.setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
	}

	/** Whether {@code state} is a great spit. */
	public static boolean is(BlockState state) {
		return GREAT_SPIT != null && state.is(GREAT_SPIT);
	}

	/** {@code player} heaves {@code corpse} (carried or dragged) onto the great spit at {@code pos}. True if it went on. */
	public static boolean skewer(CorpseEntity corpse, Player player, BlockPos pos) {
		if (!(corpse.level() instanceof ServerLevel level) || !(level.getBlockEntity(pos) instanceof SpitBlockEntity spit)) {
			return false;
		}
		if (!corpse.dressedWhole()) {
			player.sendOverlayMessage(Component.translatable(corpse.spitWholeable() ? "message.jaymoskitchen.great_spit.dress_first"
				: "message.jaymoskitchen.great_spit.wont_go"));
			return false;
		}
		if (!spit.roasting().isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.great_spit.taken"));
			return false;
		}
		if (!spit.skewerWhole(corpse)) {
			return false;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.great_spit.on"));
		return true;
	}

	/** The block entity types' blocks: the spit and the great spit share one. */
	static Set<Block> blocks() {
		return Set.of(SPIT, GREAT_SPIT);
	}
}
