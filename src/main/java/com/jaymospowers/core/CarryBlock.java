package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.KitchenCompat;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Carrying a block (anyone): sneak + right-click one with empty hands to lift it out of the world and carry it in both
 * arms, the same as a body ({@link Carry}, hold {@link Carry#BLOCK}). (0.13.11) Right-click puts it straight into the
 * free spot you're looking at, within {@link #PLACE_REACH} (turned to face you, as if placed); with no room there you
 * keep hold of it. Sneak + right-click throws it (never a chest): it lands as a falling block and settles where it
 * comes down (or breaks into its item if it can't stand there).
 * <p>
 * (0.13.11) Only crafting tables, anvils and single chests can be lifted ({@link #carriable}); dirt, stone and every
 * other block stay where they are. A chest comes along with everything in it. Never from protected spots (spawn,
 * adventure mode, claims that hook block breaking). {@link #PLACED} still notes which blocks players placed.
 * <p>
 * The carried block is a display riding at the carrier's feet (a block display, or an item display for blocks drawn
 * by a block entity renderer, like chests, which a block display shows as nothing); each client draws it in their
 * arms. One left behind by a crash or a stop mid-carry falls back into the world when it next loads.
 */
public final class CarryBlock {
	/** The block is drawn this size (share of a full block) in the arms. */
	static final float SCALE = 0.7F;
	/** Where its near bottom edge sits, from the carrier's feet: this high, this far ahead (blocks). */
	static final float HOLD_UP = 0.62F, HOLD_AHEAD = 0.28F;
	/** Set down or thrown, it leaves the arms this far ahead and this high (blocks), or to a side if that's blocked. */
	static final double RELEASE_AHEAD = 1.25, RELEASE_UP = 0.6;
	/** (0.13.11) How far off a carried block can be set into place (blocks, from the eyes). */
	static final double PLACE_REACH = 3.0;
	/** Marks a carried block's display, so a stray one is found again after a reload. */
	static final String TAG = "jaymospowers.carried_block";

	/** On a chunk: the blocks in it that players placed (packed positions). Saved with the chunk. */
	public static final AttachmentType<LongOpenHashSet> PLACED = AttachmentRegistry.create(JaymosPowers.id("carry_placed"),
		builder -> builder.persistent(Codec.LONG.listOf().xmap(LongOpenHashSet::new, List::copyOf)));
	/** On a carried block's display: the block, and what was in it (its block entity, saved), if anything. */
	public static final AttachmentType<BlockState> STATE = AttachmentRegistry.create(JaymosPowers.id("carry_block_state"),
		builder -> builder.persistent(BlockState.CODEC));
	public static final AttachmentType<CompoundTag> CONTENTS = AttachmentRegistry.create(JaymosPowers.id("carry_block_contents"),
		builder -> builder.persistent(CompoundTag.CODEC));

	/** Stray carried blocks found as their chunks loaded, put back into the world next tick. */
	private static final List<Display> STRAYS = new ArrayList<>();
	/** Carried blocks on their way down: once each settles, its spot counts as placed, or its contents spill. */
	private static final Map<FallingBlockEntity, CompoundTag> FALLING = new HashMap<>();

	private CarryBlock() {
	}

	static void init() {
		UseBlockCallback.EVENT.register(CarryBlock::onUseBlock);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> unmark(level, pos));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FALLING.clear());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display display && is(display) && !Carry.isCarried(display)) {
				STRAYS.add(display);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!FALLING.isEmpty()) {
				settle();
			}
			if (!STRAYS.isEmpty()) {
				for (Display display : List.copyOf(STRAYS)) {
					if (!display.isRemoved() && !Carry.isCarried(display) && display.level() instanceof ServerLevel level) {
						land(level, display, null, Vec3.ZERO);
					}
				}
				STRAYS.clear();
			}
		});
	}

	/** Whether this is a carried block's display. */
	public static boolean is(Entity entity) {
		return entity instanceof Display && entity.entityTags().contains(TAG);
	}

	/** A carried container (chest, furnace...): it's only ever set down, never thrown. */
	public static boolean holdsContents(Entity entity) {
		return is(entity) && entity.getAttached(CONTENTS) != null;
	}

	/** The carried block's state (air if it isn't one). */
	public static BlockState stateOf(Entity entity) {
		BlockState state = entity.getAttached(STATE);
		if (state != null) {
			return state;
		}
		return entity instanceof Display.BlockDisplay display ? display.getBlockState() : Blocks.AIR.defaultBlockState();
	}

	// ---- which blocks players placed

	/** Server: a player placed a block here (from {@code CarryBlockItemMixin}). */
	public static void markPlaced(Level level, BlockPos pos) {
		if (level instanceof ServerLevel) {
			LevelChunk chunk = level.getChunkAt(pos);
			LongOpenHashSet placed = chunk.getAttached(PLACED);
			placed = placed == null ? new LongOpenHashSet() : placed;
			placed.add(pos.asLong());
			chunk.setAttached(PLACED, placed);
			chunk.markUnsaved();
		}
	}

	static void unmark(Level level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		LongOpenHashSet placed = chunk.getAttached(PLACED);
		if (placed != null && placed.remove(pos.asLong())) {
			if (placed.isEmpty()) {
				chunk.removeAttached(PLACED);
			} else {
				chunk.setAttached(PLACED, placed);
			}
			chunk.markUnsaved();
		}
	}

	static boolean placed(Level level, BlockPos pos) {
		LongOpenHashSet placed = level.getChunkAt(pos).getAttached(PLACED);
		return placed != null && placed.contains(pos.asLong());
	}

	/** Whether this kind of block could be picked up at all (wherever it came from). */
	public static boolean liftable(Level level, BlockPos pos, BlockState state) {
		return liftableShape(level, pos, state) && carriable(state);
	}

	/**
	 * (0.15.96) Whether any solid block could be lifted here (Duplex's doubles fetch these): every block with a body,
	 * not just the {@link #carriable} ones, but still never a liquid, plant-like filler, half of a two-block block, a
	 * double chest or something unbreakable.
	 */
	public static boolean liftableAny(Level level, BlockPos pos, BlockState state) {
		return liftableShape(level, pos, state) && !state.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean liftableShape(Level level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.canBeReplaced()) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)
			|| state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED)
			|| state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0F;
	}

	/**
	 * (0.13.11) The only blocks that can be carried: a crafting table, an anvil, a single chest (Minecraft's own: other
	 * mods' chests, like Lootr's, keep each player's loot in them and stay put).
	 */
	public static boolean carriable(BlockState state) {
		if (state.is(Blocks.CRAFTING_TABLE) || state.is(BlockTags.ANVIL) || com.jaymospowers.power.zfighter.pod.PodParts.partOf(state) != null) {
			return true;
		}
		return state.getBlock() instanceof ChestBlock
			&& BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft")
			&& state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE;
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()
			|| !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		if (Carry.isCarrying(player) || Carry.isCarried(player) || KitchenCompat.isCarryingCorpse(player) || player.isPassenger() || player.isSleeping()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		// (only the server knows who placed what: the client lets the click through, and the server decides)
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer carrier) || !liftable(level, pos, state)) {
			return InteractionResult.PASS;
		}
		if (!carrier.mayInteract(server, pos) || carrier.blockActionRestricted(server, pos, carrier.gameMode())
			|| !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server, carrier, pos, state, server.getBlockEntity(pos))) {
			return InteractionResult.FAIL;
		}
		lift(server, carrier, pos, state);
		return InteractionResult.SUCCESS;
	}

	/** Takes the block out of the world and into the carrier's arms. */
	private static void lift(ServerLevel level, ServerPlayer carrier, BlockPos pos, BlockState state) {
		Display display = takeOut(level, pos, state, carrier, false);
		Carry.pickUp(carrier, display);
	}

	/**
	 * Takes the block out of the world (with whatever was in it) as a carried block's display beside {@code holder},
	 * not yet riding anything: held in a player's arms, or ({@code overhead}, 0.15.96) up on a little one's head, as
	 * Duplex's doubles carry it.
	 */
	public static Display takeOut(ServerLevel level, BlockPos pos, BlockState state, Entity holder, boolean overhead) {
		BlockState held = state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
		// whatever's in it comes along, and is taken out first so nothing spills when the block goes
		CompoundTag contents = null;
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity != null) {
			contents = blockEntity.saveWithFullMetadata(level.registryAccess());
			if (blockEntity instanceof Clearable clearable) {
				clearable.clearContent();
			}
		}
		unmark(level, pos);
		// a waterlogged block leaves its water behind
		// (and a block entity's own removal effects, like a furnace's stored XP, stay with the saved contents)
		level.setBlock(pos, state.getFluidState().createLegacyBlock(),
			blockEntity == null ? Block.UPDATE_ALL : Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
		// in the arms: in front of the carrier at arm height (the display turns with the carrier: +z is ahead);
		// overhead: centred on top of whatever it rides
		float up = overhead ? 0.0F : HOLD_UP, ahead = overhead ? -SCALE / 2.0F : HOLD_AHEAD;
		Display display;
		if (blockEntity == null) {
			Display.BlockDisplay block = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
			block.setBlockState(held);
			block.setTransformation(new Transformation(new Vector3f(-SCALE / 2.0F, up, ahead), null, new Vector3f(SCALE), null));
			display = block;
		} else {
			// a chest and the like is drawn by its block entity, not a block model: show it as its item (drawn centred)
			Display.ItemDisplay item = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
			item.setItemStack(new ItemStack(held.getBlock()));
			item.setItemTransform(ItemDisplayContext.NONE);
			item.setTransformation(new Transformation(new Vector3f(0.0F, up + SCALE / 2.0F, ahead + SCALE / 2.0F), null, new Vector3f(SCALE), null));
			display = item;
		}
		display.setAttached(STATE, held);
		if (contents != null) {
			display.setAttached(CONTENTS, contents);
		}
		// drawn even when the carrier's feet are out of view
		display.setWidth(2.0F);
		display.setHeight(2.0F);
		display.setPos(holder.getX(), holder.getY(), holder.getZ());
		display.setYRot(holder.getYRot());
		level.addFreshEntity(display);
		display.addTag(TAG);
		level.playSound(null, pos, held.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 0.9F);
		return display;
	}

	/**
	 * (0.18.51) Puts a block that never stood in the world (a pod part taken off a salvage rack) straight into
	 * {@code carrier}'s arms, as if they had lifted it.
	 */
	public static void carryNew(ServerLevel level, ServerPlayer carrier, BlockState state) {
		Display.BlockDisplay block = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		block.setBlockState(state);
		block.setTransformation(new Transformation(new Vector3f(-SCALE / 2.0F, HOLD_UP, HOLD_AHEAD), null, new Vector3f(SCALE), null));
		block.setAttached(STATE, state);
		block.setWidth(2.0F);
		block.setHeight(2.0F);
		block.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
		block.setYRot(carrier.getYRot());
		level.addFreshEntity(block);
		block.addTag(TAG);
		level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.7F, 0.9F);
		Carry.pickUp(carrier, block);
	}

	/**
	 * Puts a carried block back into the world as a falling block moving at {@code velocity}: ahead of the carrier
	 * (or to a side, or behind, if that's blocked) when there is one, otherwise where the display is. If nowhere is
	 * free, it drops as its item. Returns where it went.
	 */
	public static Vec3 land(ServerLevel level, Entity display, @Nullable ServerPlayer carrier, Vec3 velocity) {
		BlockState state = stateOf(display);
		CompoundTag contents = display.getAttached(CONTENTS);
		Vec3 at = display.position();
		display.discard();
		if (state.isAir()) {
			return at;
		}
		Vec3 spot = null;
		if (carrier != null) {
			float yaw = carrier.getYRot() * Mth.DEG_TO_RAD;
			Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
			Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
			for (Vec3 way : new Vec3[] {ahead, right, right.reverse(), ahead.reverse()}) {
				Vec3 tryAt = carrier.position().add(way.scale(RELEASE_AHEAD)).add(0.0, RELEASE_UP, 0.0);
				if (free(level, tryAt)) {
					spot = tryAt;
					break;
				}
			}
		} else if (free(level, at)) {
			spot = at;
		}
		if (spot == null) {
			Entity drop = carrier != null ? carrier : display;
			drop.spawnAtLocation(level, state.getBlock());
			spill(level, BlockPos.containing(drop.position()), state, contents);
			return at;
		}
		BlockPos pos = BlockPos.containing(spot);
		FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
		falling.setPos(spot.x, spot.y, spot.z);
		falling.setOldPosAndRot(spot, 0.0F, 0.0F);
		falling.setDeltaMovement(velocity);
		falling.syncVelocity = true;
		falling.blockData = contents;
		FALLING.put(falling, contents == null ? new CompoundTag() : contents);
		return spot;
	}

	/**
	 * (0.13.11) Where a carried block would go if set down now: the free spot the carrier is looking at within
	 * {@link #PLACE_REACH} (the block looked at if it's something like grass, else the space against the face looked
	 * at), where it can stand, nobody's in the way, and they may build. Null if there's none.
	 */
	static @Nullable BlockPos placeSpot(ServerPlayer carrier, Entity display) {
		ServerLevel level = carrier.level();
		BlockState state = facing(stateOf(display), carrier.getDirection());
		Vec3 eye = carrier.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(carrier.getLookAngle().scale(PLACE_REACH)), ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, carrier));
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		BlockPos pos = level.getBlockState(hit.getBlockPos()).canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
		if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)
			|| !level.isUnobstructed(state, pos, CollisionContext.empty())
			|| !carrier.mayInteract(level, pos) || carrier.blockActionRestricted(level, pos, carrier.gameMode())) {
			return null;
		}
		return pos;
	}

	/** (0.13.11) Puts the carried block into the world at {@code pos} (from {@link #placeSpot}), with whatever was in it. */
	static Vec3 place(ServerLevel level, Entity display, ServerPlayer carrier, BlockPos pos) {
		return place(level, display, pos, carrier.getDirection());
	}

	/** (0.15.96) Puts a carried block into the world at {@code pos}, turned as if placed by someone looking {@code toward}. */
	public static Vec3 place(ServerLevel level, Entity display, BlockPos pos, Direction toward) {
		BlockState state = facing(stateOf(display), toward);
		CompoundTag contents = display.getAttached(CONTENTS);
		display.discard();
		if (state.isAir()) {
			return Vec3.atBottomCenterOf(pos);
		}
		if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
			state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(pos).is(Fluids.WATER));
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (contents != null) {
			BlockEntity loaded = BlockEntity.loadStatic(pos, state, contents, level.registryAccess());
			if (loaded != null) {
				level.setBlockEntity(loaded);
			}
		}
		markPlaced(level, pos);
		level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		return Vec3.atBottomCenterOf(pos);
	}

	/** Turned as if someone looking {@code toward} had just placed it: a chest facing them, an anvil lying across their view. */
	public static BlockState facing(BlockState state, Direction toward) {
		if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || toward.getAxis().isVertical()) {
			return state;
		}
		return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
			state.getBlock() instanceof AnvilBlock ? toward.getClockWise() : toward.getOpposite());
	}

	/** Carried blocks that came down this tick: where each settled counts as placed; one that broke spills its contents. */
	private static void settle() {
		var it = FALLING.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			FallingBlockEntity falling = e.getKey();
			if (!falling.isRemoved()) {
				continue;
			}
			it.remove();
			if (falling.getRemovalReason() != Entity.RemovalReason.DISCARDED || !(falling.level() instanceof ServerLevel level)) {
				// unloaded mid-fall: it'll land as a plain falling block
				continue;
			}
			BlockPos pos = falling.blockPosition();
			BlockState state = falling.getBlockState();
			if (level.getBlockState(pos).is(state.getBlock())) {
				markPlaced(level, pos);
			} else {
				spill(level, pos, state, e.getValue().isEmpty() ? null : e.getValue());
			}
		}
	}

	/** What was in a carried block that broke, dropped where it broke. */
	private static void spill(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag contents) {
		if (contents != null && BlockEntity.loadStatic(pos, state, contents, level.registryAccess()) instanceof Container container) {
			Containers.dropContents(level, pos, container);
		}
	}

	/** Room for a block with its bottom centre here (and the block space it starts in is empty, so nothing's lost). */
	private static boolean free(ServerLevel level, Vec3 at) {
		return level.getBlockState(BlockPos.containing(at)).isAir()
			&& level.noCollision(AABB.ofSize(at.add(0.0, 0.49, 0.0), 0.98, 0.98, 0.98));
	}
}
