package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.494) The pigeon roost: a little dovecote on a post. Andy: "tidings should now only come to you if you have a roost
 * built for recieving messenger pigeons, and even then it will be rare, and of large happeneings, nothing small and
 * insignificant." Whoever sets one up (under open sky: a bird has to find it) is on the criers' rounds, and the
 * {@link Post}'s Tidings come to them; nobody else gets any. Each player's roosts are kept on the Overworld
 * ({@link #ROOSTS}); one that's gone or roofed over is dropped or doesn't count the next time it's looked at
 * ({@link #has}). Use it to hear whether the birds can find it (an action-bar line, no chat).
 */
public final class Roost extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** At most this many roosts kept for each player (the oldest go). */
	static final int MAX_EACH = 4;

	/** On the Overworld: each player's roosts ("dimension@packed pos"), by their id. Saved. */
	static final AttachmentType<Map<String, List<String>>> ROOSTS = AttachmentRegistry.create(JaymosKingdoms.id("pigeon_roosts"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())));

	public static Block ROOST;
	public static Item ROOST_ITEM;

	private static final VoxelShape SHAPE = Shapes.or(Block.box(6.0, 0.0, 6.0, 10.0, 7.0, 10.0), Block.box(2.0, 7.0, 2.0, 14.0, 16.0, 14.0));

	public Roost(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("pigeon_roost"));
		ROOST = Registry.register(BuiltInRegistries.BLOCK, key, new Roost(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.WOOD).strength(1.5F, 3.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion()));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("pigeon_roost"));
		ROOST_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(ROOST, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ROOST_ITEM));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	/** Its holes face you. */
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel server && placer instanceof ServerPlayer player) {
			add(server, player.getUUID(), pos);
			server.playSound(null, pos, SoundEvents.PARROT_FLY, SoundSource.BLOCKS, 0.6F, 1.3F);
			player.sendOverlayMessage(Component.translatable(open(server, pos) ? "roost.jaymoskingdoms.placed" : "roost.jaymoskingdoms.covered"));
		}
	}

	/** Whose it is, and whether the birds can find it. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		UUID owner = ownerAt(server, pos);
		if (owner == null) {
			// (one set up before anyone kept count, or by a builder: it's yours now)
			add(server, sp.getUUID(), pos);
			owner = sp.getUUID();
		}
		String key = !open(server, pos) ? "roost.jaymoskingdoms.covered"
			: owner.equals(sp.getUUID()) ? "roost.jaymoskingdoms.yours" : "roost.jaymoskingdoms.theirs";
		sp.sendOverlayMessage(Component.translatable(key, Post.nameOf(server.getServer(), owner)));
		return InteractionResult.SUCCESS;
	}

	/** A bird can come down to the roost at {@code pos}: nothing over it. */
	private static boolean open(ServerLevel level, BlockPos pos) {
		return !level.dimensionType().hasSkyLight() || level.canSeeSky(pos.above());
	}

	private static String where(ServerLevel level, BlockPos pos) {
		return level.dimension().identifier() + "@" + pos.asLong();
	}

	private static void add(ServerLevel level, UUID id, BlockPos pos) {
		ServerLevel overworld = Factions.level(level.getServer());
		Map<String, List<String>> all = new HashMap<>(overworld.getAttachedOrElse(ROOSTS, Map.of()));
		String here = where(level, pos);
		// (a roost is one player's: whoever had it before doesn't any more)
		all.replaceAll((k, v) -> v.contains(here) ? v.stream().filter(s -> !s.equals(here)).toList() : v);
		List<String> mine = new ArrayList<>(all.getOrDefault(id.toString(), List.of()));
		mine.add(here);
		while (mine.size() > MAX_EACH) {
			mine.removeFirst();
		}
		all.put(id.toString(), List.copyOf(mine));
		all.values().removeIf(List::isEmpty);
		overworld.setAttached(ROOSTS, Map.copyOf(all));
	}

	private static @Nullable UUID ownerAt(ServerLevel level, BlockPos pos) {
		String here = where(level, pos);
		for (Map.Entry<String, List<String>> e : Factions.level(level.getServer()).getAttachedOrElse(ROOSTS, Map.of()).entrySet()) {
			if (e.getValue().contains(here)) {
				try {
					return UUID.fromString(e.getKey());
				} catch (IllegalArgumentException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	/**
	 * The player {@code id} has a roost the birds can find. A roost in a part of the world that isn't loaded is taken on trust;
	 * one that's been broken is forgotten, one that's roofed over doesn't count.
	 */
	static boolean has(MinecraftServer server, UUID id) {
		ServerLevel overworld = Factions.level(server);
		List<String> mine = overworld.getAttachedOrElse(ROOSTS, Map.of()).get(id.toString());
		if (mine == null || mine.isEmpty()) {
			return false;
		}
		boolean found = false;
		List<String> kept = new ArrayList<>();
		for (String entry : mine) {
			int at = entry.lastIndexOf('@');
			ServerLevel level = null;
			BlockPos pos = null;
			try {
				level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(entry.substring(0, at))));
				pos = BlockPos.of(Long.parseLong(entry.substring(at + 1)));
			} catch (RuntimeException ignored) {
				// (a bad entry: forgotten)
			}
			if (level == null || pos == null) {
				continue;
			}
			if (!level.isLoaded(pos)) {
				kept.add(entry);
				found = true;
				continue;
			}
			if (!level.getBlockState(pos).is(ROOST)) {
				continue;
			}
			kept.add(entry);
			found |= open(level, pos);
		}
		if (kept.size() != mine.size()) {
			Map<String, List<String>> all = new HashMap<>(overworld.getAttachedOrElse(ROOSTS, Map.of()));
			if (kept.isEmpty()) {
				all.remove(id.toString());
			} else {
				all.put(id.toString(), List.copyOf(kept));
			}
			overworld.setAttached(ROOSTS, Map.copyOf(all));
		}
		return found;
	}
}
