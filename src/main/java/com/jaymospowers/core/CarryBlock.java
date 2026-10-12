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
 * Carrying a block (anyone): sneak + right-click a block a player placed, with empty hands, to lift it out of the
 * world and carry it in both arms, the same as a body ({@link Carry}, hold {@link Carry#BLOCK}): right-click sets it
 * down ahead of you, sneak + right-click throws it. It lands as a falling block and settles where it comes down (or
 * breaks into its item if it can't stand there, spilling whatever was in it).
 * <p>
 * Only blocks players placed ({@link #PLACED}, kept per chunk; natural terrain never counts). Chests, furnaces and the
 * like come along with everything in them. Not liftable: two-part blocks (doors, beds, tall flowers, a double chest),
 * liquids, unbreakable blocks, anything you could walk through, and protected spots (spawn, adventure mode, claims
 * that hook block breaking).
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
		return entity instanceof Display.BlockDisplay display ? display.getBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
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
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.canBeReplaced()) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)
			|| state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED)
			|| state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.getValue(BlockStateProperties.CHEST_TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
			return false;
		}
		return state.getDestroySpeed(level, pos) >= 0.0F;
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
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer carrier) || !liftable(level, pos, state) || !placed(level, pos)) {
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
		Display display;
		if (blockEntity == null) {
			// centred across, in front of the carrier at arm height (the display turns with the carrier: +z is ahead)
			Display.BlockDisplay block = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
			block.setBlockState(held);
			block.setTransformation(new Transformation(new Vector3f(-SCALE / 2.0F, HOLD_UP, HOLD_AHEAD), null, new Vector3f(SCALE), null));
			display = block;
		} else {
			// a chest and the like is drawn by its block entity, not a block model: show it as its item (drawn centred)
			Display.ItemDisplay item = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
			item.setItemStack(new ItemStack(held.getBlock()));
			item.setItemTransform(ItemDisplayContext.NONE);
			item.setTransformation(new Transformation(new Vector3f(0.0F, HOLD_UP + SCALE / 2.0F, HOLD_AHEAD + SCALE / 2.0F), null, new Vector3f(SCALE), null));
			display = item;
		}
		display.setAttached(STATE, held);
		if (contents != null) {
			display.setAttached(CONTENTS, contents);
		}
		// drawn even when the carrier's feet are out of view
		display.setWidth(2.0F);
		display.setHeight(2.0F);
		display.setPos(carrier.getX(), carrier.getY(), carrier.getZ());
		display.setYRot(carrier.getYRot());
		level.addFreshEntity(display);
		display.addTag(TAG);
		Carry.pickUp(carrier, display);
		level.playSound(null, pos, held.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 0.9F);
	}

	/**
	 * Puts a carried block back into the world as a falling block moving at {@code velocity}: ahead of the carrier
	 * (or to a side, or behind, if that's blocked) when there is one, otherwise where the display is. If nowhere is
	 * free, it drops as its item. Returns where it went.
	 */
	static Vec3 land(ServerLevel level, Entity display, @Nullable ServerPlayer carrier, Vec3 velocity) {
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
