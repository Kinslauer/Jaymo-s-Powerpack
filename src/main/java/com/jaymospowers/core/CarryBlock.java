package com.jaymospowers.core;

import com.jaymospowers.power.web.KitchenCompat;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
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
 * Carrying a block (anyone): sneak + right-click a single block with an empty hand to lift it out of the world and
 * carry it in both arms, the same as a body ({@link Carry}, hold {@link Carry#BLOCK}): right-click sets it down ahead
 * of you, sneak + right-click throws it. It lands as a falling block and settles where it comes down (or breaks into
 * its item if it can't stand there).
 * <p>
 * Only plain single blocks: nothing that holds things or data (chests, furnaces, signs...), nothing two blocks tall
 * or wide (doors, beds, tall flowers), no liquids, nothing unbreakable, and nothing you could walk through (grass,
 * fire). Protected spots (spawn, adventure mode, claims that hook block breaking) can't be lifted from.
 * <p>
 * The carried block is a block display riding at the carrier's feet; each client draws it in their arms. One left
 * behind by a crash or a stop mid-carry falls back into the world when it next loads.
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

	/** Stray carried blocks found as their chunks loaded, put back into the world next tick. */
	private static final List<Display.BlockDisplay> STRAYS = new ArrayList<>();

	private CarryBlock() {
	}

	static void init() {
		UseBlockCallback.EVENT.register(CarryBlock::onUseBlock);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Display.BlockDisplay display && is(display) && !Carry.isCarried(display)) {
				STRAYS.add(display);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!STRAYS.isEmpty()) {
				for (Display.BlockDisplay display : List.copyOf(STRAYS)) {
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
		return entity instanceof Display.BlockDisplay && entity.entityTags().contains(TAG);
	}

	/** The carried block's state (air if it isn't one). */
	public static BlockState stateOf(Entity entity) {
		return entity instanceof Display.BlockDisplay display ? display.getBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
	}

	/** Whether this block could be picked up at all. */
	public static boolean liftable(Level level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.hasBlockEntity() || state.canBeReplaced()) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)
			|| state.hasProperty(BlockStateProperties.EXTENDED) && state.getValue(BlockStateProperties.EXTENDED)) {
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
		if (!liftable(level, pos, state)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer carrier) {
			if (!carrier.mayInteract(server, pos) || carrier.blockActionRestricted(server, pos, carrier.gameMode())
				|| !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(server, carrier, pos, state, null)) {
				return InteractionResult.FAIL;
			}
			lift(server, carrier, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	/** Takes the block out of the world and into the carrier's arms. */
	private static void lift(ServerLevel level, ServerPlayer carrier, BlockPos pos, BlockState state) {
		BlockState held = state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
		// a waterlogged block leaves its water behind
		level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
		Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
		display.setBlockState(held);
		// centred across, in front of the carrier at arm height (the display turns with the carrier: +z is ahead)
		display.setTransformation(new Transformation(new Vector3f(-SCALE / 2.0F, HOLD_UP, HOLD_AHEAD), null, new Vector3f(SCALE), null));
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
			return at;
		}
		BlockPos pos = BlockPos.containing(spot);
		FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
		falling.setPos(spot.x, spot.y, spot.z);
		falling.setOldPosAndRot(spot, 0.0F, 0.0F);
		falling.setDeltaMovement(velocity);
		falling.syncVelocity = true;
		return spot;
	}

	/** Room for a block with its bottom centre here (and the block space it starts in is empty, so nothing's lost). */
	private static boolean free(ServerLevel level, Vec3 at) {
		return level.getBlockState(BlockPos.containing(at)).isAir()
			&& level.noCollision(AABB.ofSize(at.add(0.0, 0.49, 0.0), 0.98, 0.98, 0.98));
	}
}
