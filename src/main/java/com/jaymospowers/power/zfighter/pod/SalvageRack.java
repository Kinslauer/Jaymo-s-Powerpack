package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.CarryBlock;
import com.jaymospowers.tinkering.Tinkering;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.21, Andy's SevTech picks: "Salvage Bench") The Salvage Rack, a low bench for mending worn pod parts instead of
 * making new ones. Carry a worn part to it and set it down looking at the rack ({@link PodParts#tryAttach}): it lies on
 * the rack. Strike it with a hammer in the main hand and the part's metal in the other ({@link #metalOf}): the first
 * blow takes one ingot, and {@link #BLOWS} blows in all make the part sound. Sneak and strike a part that isn't mended
 * yet and it is broken to labelled scrap instead. With empty arms, use the rack to take a mended part off into your arms.
 * Nothing here makes a keel. Everything the rack is holding is in its block state ({@link #LAID}, {@link #MENDED},
 * {@link #DONE}); tools/gen_pod_mending.py draws the part on the rack.
 */
public final class SalvageRack {
	// ---- tuning ----
	/** Hammer blows that mend a part. */
	public static final int BLOWS = 3;

	/** What lies on the rack: nothing, or one of the parts. */
	public enum Laid implements StringRepresentable {
		NONE("none", null), KEEL("keel", PodPart.KEEL), FIN("fin", PodPart.FIN), BELL("bell", PodPart.BELL), TANK("tank", PodPart.TANK),
		PLATE("plate", PodPart.PLATE), CANOPY("canopy", PodPart.CANOPY), MAST("mast", PodPart.MAST);

		private final String name;
		final @Nullable PodPart part;

		Laid(String name, @Nullable PodPart part) {
			this.name = name;
			this.part = part;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		static Laid of(PodPart part) {
			for (Laid laid : values()) {
				if (laid.part == part) {
					return laid;
				}
			}
			return NONE;
		}
	}

	public static final EnumProperty<Laid> LAID = EnumProperty.create("laid", Laid.class);
	/** The part on the rack has been mended and can be taken. */
	public static final BooleanProperty MENDED = BooleanProperty.create("mended");
	/** Blows struck so far on the part being mended. */
	public static final IntegerProperty DONE = IntegerProperty.create("blows", 0, BLOWS - 1);

	public static Block BLOCK;
	private static boolean ready;

	private SalvageRack() {
	}

	/** Registers the rack, its item and its creative entry. Called once, from {@link PodParts#init}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("salvage_rack"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new RackBlock(BlockBehaviour.Properties.of().strength(2.0F).noOcclusion()
			.sound(SoundType.WOOD).setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("salvage_rack"));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(BLOCK));
		// sneaking with something in hand skips a block's own use, so breaking a part up for scrap (sneak + hammer) is caught here
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator() || !player.getMainHandItem().is(PodParts.HAMMERS)) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!state.is(BLOCK) || state.getValue(LAID) == Laid.NONE) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer smith) {
				strike(server, smith, hit.getBlockPos(), state, smith.getMainHandItem());
			}
			return InteractionResult.SUCCESS;
		});
	}

	// ---------------------------------------------------------------- what mends what

	/** The metal that mends {@code part}: iron for the frame, plates and mast, steel for the fins, copper for the tank and bell, glass for the canopy. */
	public static ItemStack metalOf(PodPart part) {
		return switch (part) {
			case KEEL, PLATE, MAST -> new ItemStack(Items.IRON_INGOT);
			case FIN -> new ItemStack(Tinkering.STEEL_INGOT);
			case TANK, BELL -> new ItemStack(Items.COPPER_INGOT);
			case CANOPY -> new ItemStack(Items.GLASS);
		};
	}

	static boolean isMetalOf(ItemStack stack, PodPart part) {
		return !stack.isEmpty() && stack.is(metalOf(part).getItem());
	}

	// ---------------------------------------------------------------- putting a worn part on

	/** Lays {@code part} (worn) on the empty rack at {@code pos}. */
	static void lay(ServerLevel level, BlockPos pos, BlockState state, PodPart part) {
		level.setBlock(pos, state.setValue(LAID, Laid.of(part)).setValue(MENDED, false).setValue(DONE, 0), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 1.3F);
	}

	// ---------------------------------------------------------------- the block

	private static VoxelShape shape() {
		return Shapes.or(Block.box(0, 10, 0, 16, 13, 16), Block.box(1, 0, 1, 3, 10, 3), Block.box(13, 0, 1, 15, 10, 3), Block.box(1, 0, 13, 3, 10, 15),
			Block.box(13, 0, 13, 15, 10, 15));
	}

	private static final class RackBlock extends Block {
		private static final VoxelShape SHAPE = shape();

		RackBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(LAID, Laid.NONE).setValue(MENDED, false).setValue(DONE, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(LAID, MENDED, DONE);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}

		/** A part left on a rack that is broken drops with it. */
		@Override
		protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
			PodPart part = state.getValue(LAID).part;
			if (part != null) {
				Block.popResource(level, pos, PodParts.stackOf(part, !state.getValue(MENDED), 0));
			}
			super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		}

		/** A hammer in the main hand strikes the part on the rack. */
		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (hand != InteractionHand.MAIN_HAND || !stack.is(PodParts.HAMMERS) || state.getValue(LAID) == Laid.NONE) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer smith) {
				strike(server, smith, pos, state, stack);
			}
			return InteractionResult.SUCCESS;
		}

		/** Empty arms take a mended part off into the arms; anything else is told what the rack wants. */
		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			PodPart part = state.getValue(LAID).part;
			if (part == null) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer carrier)) {
				return InteractionResult.SUCCESS;
			}
			if (!state.getValue(MENDED)) {
				carrier.sendOverlayMessage(Component.translatable("message.jaymospowers.salvage.hint", metalOf(part).getHoverName())
					.withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			if (!carrier.getMainHandItem().isEmpty() || !carrier.getOffhandItem().isEmpty() || Carry.isCarrying(carrier) || Carry.isCarried(carrier)) {
				carrier.sendOverlayMessage(Component.translatable("message.jaymospowers.salvage.hands").withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			server.setBlock(pos, state.setValue(LAID, Laid.NONE).setValue(MENDED, false).setValue(DONE, 0), Block.UPDATE_ALL);
			CarryBlock.carryNew(server, carrier, PodParts.block(part).defaultBlockState());
			return InteractionResult.SUCCESS;
		}
	}

	// ---------------------------------------------------------------- the hammer

	/** One blow of {@code smith}'s hammer on the part lying at {@code pos}. */
	private static void strike(ServerLevel level, ServerPlayer smith, BlockPos pos, BlockState state, ItemStack hammer) {
		PodPart part = state.getValue(LAID).part;
		if (part == null) {
			return;
		}
		Vec3 top = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
		if (state.getValue(MENDED)) {
			smith.sendOverlayMessage(Component.translatable("message.jaymospowers.salvage.sound").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (smith.isShiftKeyDown()) {
			// broken up for what it is worth
			level.setBlock(pos, state.setValue(LAID, Laid.NONE).setValue(MENDED, false).setValue(DONE, 0), Block.UPDATE_ALL);
			ItemEntity scrap = new ItemEntity(level, top.x, top.y, top.z, PodParts.scrapOf(part, 0));
			scrap.setDefaultPickUpDelay();
			level.addFreshEntity(scrap);
			level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, top.x, top.y, top.z, 10, 0.3, 0.2, 0.3, 0.2);
			smith.sendOverlayMessage(Component.translatable("message.jaymospowers.salvage.scrapped").withStyle(ChatFormatting.GRAY));
			return;
		}
		int done = state.getValue(DONE);
		if (done == 0) {
			ItemStack off = smith.getOffhandItem();
			if (!isMetalOf(off, part)) {
				smith.sendOverlayMessage(Component.translatable("message.jaymospowers.salvage.needs", metalOf(part).getHoverName())
					.withStyle(ChatFormatting.GRAY));
				return;
			}
			off.consume(1, smith);
		}
		done++;
		smith.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		hammer.hurtAndBreak(1, smith, InteractionHand.MAIN_HAND);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 8, 0.25, 0.1, 0.25, 0.3);
		if (done >= BLOWS) {
			level.setBlock(pos, state.setValue(MENDED, true).setValue(DONE, 0), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.9F, 1.3F);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.4F);
			smith.sendOverlayMessage(Component.translatable("message.jaymospowers.salvage.mended").withStyle(ChatFormatting.GOLD));
		} else {
			level.setBlock(pos, state.setValue(DONE, done), Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.9F + 0.15F * done);
		}
	}
}
