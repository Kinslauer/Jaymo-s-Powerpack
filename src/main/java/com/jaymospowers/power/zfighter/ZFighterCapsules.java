package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.58, Andy's NOEA picks) Pocket capsules, three kinds, thrown like a snowball (right click). Where one lands it
 * opens:
 * <ul>
 * <li><b>Pocket Hut Capsule</b>: a small house, 7 by 7 and four high (spruce, a flat dark roof, a door on the side that faces
 * you, a bed, a crafting table, a lantern, a rug) pops up where it lands. It is built into <em>air only</em> (what is already
 * there stays; if less than {@link #ROOM_NEEDED} of its blocks have room the capsule comes back to you instead). A glowing
 * <b>Capsule Seal</b> sits over the door: right-click it, or sneak and use any capsule on it, and the house packs back into a
 * capsule (only the blocks still as the capsule made them; anything you added is left standing).</li>
 * <li><b>Pocket Skiff Capsule</b>: an oak boat.</li>
 * <li><b>Pocket Steed Capsule</b>: a tame, saddled horse, yours.</li>
 * </ul>
 * A skiff or a steed packs back too: sneak and right-click it with an empty hand (only its owner, the one who threw the
 * capsule). A capsule thrown in creative is not used up.
 */
public final class ZFighterCapsules {
	/** Ticks between throws; a flying capsule is dropped from watch after this long. */
	static final int COOLDOWN = 20, LIFE = 200;
	static final float SPEED = 1.3F, SPREAD = 0.5F;
	/** The share of the house's solid blocks that must have room (air or something replaceable). */
	static final float ROOM_NEEDED = 0.8F;
	/** How far up a capsule that landed inside stone looks for air, and how far down it looks for the ground (blocks). */
	static final int LOOK_UP = 4, LOOK_DOWN = 10;
	static final String TAG_PREFIX = "jaymospowers_capsule_";

	/** The three kinds: item id, rarity. */
	enum Kind {
		HUT("pocket_hut_capsule"), SKIFF("pocket_skiff_capsule"), STEED("pocket_steed_capsule");

		final String id;

		Kind(String id) {
			this.id = id;
		}
	}

	public static final Item[] CAPSULE = new Item[Kind.values().length];
	public static Block SEAL;
	public static Item SEAL_ITEM;

	private record Thrown(Snowball ball, Kind kind, UUID thrower, boolean creative, int age) {
	}

	private static final List<Thrown> FLYING = new ArrayList<>();

	private ZFighterCapsules() {
	}

	static void init() {
		for (Kind kind : Kind.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(kind.id));
			CAPSULE[kind.ordinal()] = Registry.register(BuiltInRegistries.ITEM, key, new CapsuleItem(new Item.Properties().setId(key).stacksTo(16)
				.rarity(Rarity.UNCOMMON), kind));
		}
		ResourceKey<Block> sealKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("capsule_seal"));
		SEAL = Registry.register(BuiltInRegistries.BLOCK, sealKey, new SealBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(3.0F, 6.0F).noLootTable().lightLevel(state -> 10).sound(SoundType.AMETHYST).setId(sealKey)));
		ResourceKey<Item> sealItemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("capsule_seal"));
		SEAL_ITEM = Registry.register(BuiltInRegistries.ITEM, sealItemKey, new BlockItem(SEAL, new Item.Properties().setId(sealItemKey)
			.useBlockDescriptionPrefix().rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (Item capsule : CAPSULE) {
				output.accept(capsule);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ZFighterCapsules::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FLYING.clear());
		// a skiff or a steed packs back: sneak, empty hand, its owner
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			Kind kind = kindOf(entity);
			if (kind == null || !entity.entityTags().contains(TAG_PREFIX + "owner_" + player.getUUID())) {
				return InteractionResult.PASS;
			}
			entity.ejectPassengers();
			entity.discard();
			give((ServerPlayer)player, new ItemStack(CAPSULE[kind.ordinal()]));
			((ServerLevel)level).playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0F, 1.4F);
			return InteractionResult.SUCCESS;
		});
	}

	private static Kind kindOf(Entity entity) {
		for (Kind kind : new Kind[] {Kind.SKIFF, Kind.STEED}) {
			if (entity.entityTags().contains(TAG_PREFIX + kind.name().toLowerCase())) {
				return kind;
			}
		}
		return null;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	// ---------------------------------------------------------------- the throw

	static final class CapsuleItem extends Item {
		final Kind kind;

		CapsuleItem(Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		/** Sneak and use it on a Capsule Seal: the house packs up. */
		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			Player player = context.getPlayer();
			if (player == null || !player.isShiftKeyDown() || !level.getBlockState(context.getClickedPos()).is(SEAL)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				pack((ServerLevel)level, context.getClickedPos(), server);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel server) {
				ItemStack capsule = stack.copyWithCount(1);
				Snowball ball = Projectile.spawnProjectileFromRotation((l, owner, s) -> new Snowball(l, owner, s), server, capsule, player, 0.0F, SPEED,
					SPREAD);
				FLYING.add(new Thrown(ball, kind, player.getUUID(), player.getAbilities().instabuild, 0));
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.8F);
				player.getCooldowns().addCooldown(stack, COOLDOWN);
				stack.consume(1, player);
			}
			return InteractionResult.SUCCESS;
		}
	}

	private static void tick(MinecraftServer server) {
		if (FLYING.isEmpty()) {
			return;
		}
		List<Thrown> landed = new ArrayList<>();
		FLYING.removeIf(t -> {
			if (t.ball().isRemoved()) {
				landed.add(t);
				return true;
			}
			return t.age() >= LIFE;
		});
		for (Thrown t : landed) {
			if (t.ball().level() instanceof ServerLevel level) {
				land(level, t, server.getPlayerList().getPlayer(t.thrower()));
			}
		}
		// the ones still flying age (a record: rebuilt)
		for (int i = 0; i < FLYING.size(); i++) {
			Thrown t = FLYING.get(i);
			FLYING.set(i, new Thrown(t.ball(), t.kind(), t.thrower(), t.creative(), t.age() + 1));
		}
	}

	/** A capsule has hit something: it opens there (or, if the way is blocked, comes back). */
	private static void land(ServerLevel level, Thrown t, ServerPlayer thrower) {
		Vec3 at = t.ball().position();
		BlockPos ground = settle(level, BlockPos.containing(at));
		boolean opened = ground != null && switch (t.kind()) {
			case HUT -> hut(level, ground, thrower, at);
			case SKIFF -> skiff(level, ground, thrower);
			case STEED -> steed(level, ground, thrower);
		};
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 24, 0.5, 0.4, 0.5, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.5F);
		if (!opened) {
			if (thrower != null) {
				thrower.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_capsule_blocked").withStyle(ChatFormatting.GRAY));
			}
			if (!t.creative()) {
				ItemEntity item = new ItemEntity(level, at.x, at.y + 0.3, at.z, new ItemStack(CAPSULE[t.kind().ordinal()]));
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
		}
	}

	/** The air block a capsule that landed at {@code at} rests in: out of any stone it hit, down to the ground. Null if there is no room. */
	private static BlockPos settle(ServerLevel level, BlockPos at) {
		BlockPos pos = at;
		for (int i = 0; i < LOOK_UP && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
			pos = pos.above();
		}
		if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
			return null;
		}
		for (int i = 0; i < LOOK_DOWN && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
			&& level.getBlockState(pos.below()).getFluidState().isEmpty(); i++) {
			pos = pos.below();
		}
		return pos;
	}

	private static void own(Entity entity, Kind kind, ServerPlayer owner) {
		entity.addTag(TAG_PREFIX + kind.name().toLowerCase());
		if (owner != null) {
			entity.addTag(TAG_PREFIX + "owner_" + owner.getUUID());
		}
	}

	private static boolean skiff(ServerLevel level, BlockPos ground, ServerPlayer thrower) {
		Boat boat = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.TRIGGERED);
		if (boat == null) {
			return false;
		}
		boat.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, thrower == null ? 0.0F : thrower.getYRot() + 180.0F, 0.0F);
		own(boat, Kind.SKIFF, thrower);
		level.addFreshEntity(boat);
		return true;
	}

	private static boolean steed(ServerLevel level, BlockPos ground, ServerPlayer thrower) {
		Horse horse = EntityTypes.HORSE.spawn(level, ground, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return false;
		}
		if (thrower != null) {
			horse.tameWithName(thrower);
			horse.setOwner(thrower);
		}
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		horse.setPersistenceRequired();
		own(horse, Kind.STEED, thrower);
		return true;
	}

	// ---------------------------------------------------------------- the house

	/** One block of the house, in the house's own frame (x east, z south, y up from the floor; the door is on the south). */
	private record Piece(int x, int y, int z, BlockState state, boolean solid) {
	}

	private static final int SEAL_X = 0, SEAL_Y = 3, SEAL_Z = 3;

	private static BlockState block(Block block) {
		return block.defaultBlockState();
	}

	private static BlockState named(String id) {
		return BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id)).defaultBlockState();
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
		return state.hasProperty(property) ? state.setValue(property, value) : state;
	}

	/** The house: floor, walls with windows, a door, roof, and what is inside. */
	private static List<Piece> house() {
		List<Piece> pieces = new ArrayList<>();
		BlockState planks = block(Blocks.SPRUCE_PLANKS), log = block(Blocks.STRIPPED_SPRUCE_LOG), glass = block(Blocks.GLASS);
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				pieces.add(new Piece(x, 0, z, planks, true));
				boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
				boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
				if (edge) {
					for (int y = 1; y <= 3; y++) {
						boolean window = y == 2 && (Math.abs(x) == 3 && z == 0 || z == -3 && x == 0);
						boolean door = x == 0 && z == 3 && y <= 2;
						if (door || x == SEAL_X && y == SEAL_Y && z == SEAL_Z) {
							continue;
						}
						pieces.add(new Piece(x, y, z, window ? glass : corner ? log : planks, true));
					}
				}
				pieces.add(new Piece(x, 4, z, block(Blocks.DARK_OAK_PLANKS), true));
				if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
					pieces.add(new Piece(x, 5, z, with(block(Blocks.DARK_OAK_SLAB), BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), true));
				}
			}
		}
		BlockState door = with(with(block(Blocks.SPRUCE_DOOR), DoorBlock.FACING, Direction.SOUTH), DoorBlock.HALF, DoubleBlockHalf.LOWER);
		pieces.add(new Piece(0, 1, 3, door, true));
		pieces.add(new Piece(0, 2, 3, with(door, DoorBlock.HALF, DoubleBlockHalf.UPPER), true));
		pieces.add(new Piece(SEAL_X, SEAL_Y, SEAL_Z, block(SEAL), true));
		// the step in front of the door
		pieces.add(new Piece(0, 0, 4, with(block(Blocks.SPRUCE_SLAB), BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM), false));
		// inside: a rug, a bed along the north wall, a crafting table and a lantern
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				pieces.add(new Piece(x, 1, z, named("red_carpet"), false));
			}
		}
		BlockState foot = with(with(named("red_bed"), BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), BlockStateProperties.BED_PART, BedPart.FOOT);
		pieces.add(new Piece(-2, 1, -1, foot, false));
		pieces.add(new Piece(-2, 1, -2, with(foot, BlockStateProperties.BED_PART, BedPart.HEAD), false));
		pieces.add(new Piece(2, 1, -2, block(Blocks.CRAFTING_TABLE), false));
		pieces.add(new Piece(2, 2, -2, block(Blocks.LANTERN), false));
		return pieces;
	}

	/** Clockwise turns from a south-facing door to {@code side}. */
	private static int turnsTo(Direction side) {
		return switch (side) {
			case WEST -> 1;
			case NORTH -> 2;
			case EAST -> 3;
			default -> 0;
		};
	}

	private static BlockPos at(BlockPos origin, Piece piece, int turns) {
		int x = piece.x(), z = piece.z();
		for (int i = 0; i < turns; i++) {
			int nx = -z;
			z = x;
			x = nx;
		}
		return origin.offset(x, piece.y(), z);
	}

	private static BlockState turned(BlockState state, int turns) {
		for (int i = 0; i < turns; i++) {
			state = state.rotate(Rotation.CLOCKWISE_90);
		}
		return state;
	}

	private static boolean hut(ServerLevel level, BlockPos origin, ServerPlayer thrower, Vec3 landed) {
		Direction side = Direction.SOUTH;
		if (thrower != null) {
			side = Direction.getApproximateNearest(thrower.getX() - landed.x, 0.0, thrower.getZ() - landed.z);
			if (side.getAxis().isVertical()) {
				side = Direction.SOUTH;
			}
		}
		int turns = turnsTo(side);
		List<Piece> house = house();
		int solid = 0, room = 0;
		for (Piece piece : house) {
			if (piece.solid()) {
				solid++;
				BlockState now = level.getBlockState(at(origin, piece, turns));
				if (now.isAir() || now.canBeReplaced()) {
					room++;
				}
			}
		}
		if (room < solid * ROOM_NEEDED) {
			return false;
		}
		for (Piece piece : house) {
			BlockPos pos = at(origin, piece, turns);
			BlockState now = level.getBlockState(pos);
			if (now.isAir() || now.canBeReplaced()) {
				level.setBlock(pos, turned(piece.state(), turns), Block.UPDATE_CLIENTS);
			}
		}
		return true;
	}

	/** Packs the house a seal belongs to back into a capsule: every block still as the capsule made it goes; the rest stays. */
	private static void pack(ServerLevel level, BlockPos seal, ServerPlayer player) {
		BlockState state = level.getBlockState(seal);
		if (!state.is(SEAL)) {
			return;
		}
		int turns = turnsTo(state.getValue(HorizontalDirectionalBlock.FACING));
		Piece sealPiece = new Piece(SEAL_X, SEAL_Y, SEAL_Z, state, true);
		int x = sealPiece.x(), z = sealPiece.z();
		for (int i = 0; i < turns; i++) {
			int nx = -z;
			z = x;
			x = nx;
		}
		BlockPos origin = seal.offset(-x, -SEAL_Y, -z);
		for (Piece piece : house()) {
			BlockPos pos = at(origin, piece, turns);
			if (level.getBlockState(pos).is(piece.state().getBlock())) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		level.setBlock(seal, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		give(player, new ItemStack(CAPSULE[Kind.HUT.ordinal()]));
		level.sendParticles(ParticleTypes.POOF, origin.getX() + 0.5, origin.getY() + 2.0, origin.getZ() + 0.5, 40, 2.0, 1.2, 2.0, 0.05);
		level.playSound(null, seal, SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	/** The seal over a capsule house's door: right-click it to pack the house. */
	static final class SealBlock extends HorizontalDirectionalBlock {
		SealBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}

		@Override
		public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				pack(server, pos, sp);
			}
			return InteractionResult.SUCCESS;
		}
	}
}
