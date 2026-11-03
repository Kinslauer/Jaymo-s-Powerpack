package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.67) Seats and the Sit key, on top of {@link Meditate}'s sit-anywhere (Andy: "add a button as well").
 *
 * <ul>
 * <li><b>Seats:</b> right-click a stair (right way up) or a bottom slab with an empty hand, not sneaking, and you sit on
 * it: on a stair facing away from its back, on a slab the way you were facing. A block in {@link #SEATS}
 * ({@code #jaymospowers:seats}, for chairs and benches from other mods) works too, facing its own way. A seat needs two
 * blocks of room above, and holds one sitter.</li>
 * <li><b>The Sit key</b> (] by default): sits you on the seat under you or the one you look at, else on the ground, at
 * once. Pressed again while sat, it goes round the ways of sitting ({@link Meditate#POSES}).</li>
 * <li><b>Getting up:</b> press Sneak, move, jump, or get hurt; breaking the seat drops you off it.</li>
 * <li><b>By the fire:</b> sitting within {@link #FIRE_RANGE} of a lit campfire heals {@link #FIRE_HEAL} every
 * {@link #FIRE_HEAL_EVERY} ticks and (with Jaymo's Kitchen) warms you by {@link #FIRE_WARMTH} more.</li>
 * </ul>
 *
 * Sitting on a seat counts as meditating with the Meditation perk, like the ground. Held items change the arms while
 * you sit (a book, a cup, a blade across the lap); that's drawn by MeditatePose from what's in your hand.
 */
public final class Sitting {
	/** Blocks you can sit on besides stairs and bottom slabs (other mods' chairs and benches). */
	public static final TagKey<Block> SEATS = TagKey.create(Registries.BLOCK, JaymosPowers.id("seats"));
	/** How far a seat may be from your eyes for the Sit key or a right-click (blocks). */
	static final double REACH = 3.0;
	/** How high you sit on a stair or a slab, and on a tagged seat with no top at its middle (blocks above the block). */
	static final double SEAT_HEIGHT = 0.5;
	/** Sitting by a lit campfire: how near (blocks), how much it heals, how often (ticks), how much warmer (body heat). */
	static final int FIRE_RANGE = 4;
	static final float FIRE_HEAL = 1.0F;
	static final int FIRE_HEAL_EVERY = 100;
	static final double FIRE_WARMTH = 10.0;

	/** Client → server: the Sit key was pressed. */
	public record Key() implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("sit_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = StreamCodec.unit(new Key());

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	/** Sitters with a lit campfire near (checked once a second while they sit). */
	private static final Set<UUID> BY_FIRE = new HashSet<>();

	private Sitting() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, ctx) -> key(ctx.player()));
		UseBlockCallback.EVENT.register(Sitting::use);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BY_FIRE.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BY_FIRE.clear());
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				ToDoubleFunction<ServerPlayer> warmth = player -> BY_FIRE.contains(player.getUUID()) && Meditate.sitting(player) ? FIRE_WARMTH : 0.0;
				Class.forName("com.jaymoskitchen.climate.BodyHeat").getMethod("addWeather", ToDoubleFunction.class).invoke(null, warmth);
			} catch (ReflectiveOperationException | LinkageError e) {
				JaymosPowers.LOGGER.warn("Jaymo's Kitchen body heat not found; sitting by the fire won't warm", e);
			}
		}
	}

	/** Whether this block could be a seat at all (both sides; the room above and who sits there come later). */
	static boolean seatBlock(BlockState state) {
		if (state.getBlock() instanceof StairBlock) {
			return state.getValue(StairBlock.HALF) == Half.BOTTOM;
		}
		if (state.getBlock() instanceof SlabBlock) {
			return state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
		}
		return state.is(SEATS);
	}

	/** The seat at {@code pos} for this player: where and which way they'd sit, or null if it's no seat or has no room. */
	static Meditate.@Nullable Seat seat(Level level, BlockPos pos, Player player) {
		BlockState state = level.getBlockState(pos);
		if (!seatBlock(state)) {
			return null;
		}
		for (int up = 1; up <= 2; up++) {
			BlockPos above = pos.above(up);
			if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
				return null;
			}
		}
		double height = SEAT_HEIGHT;
		float yaw = Direction.fromYRot(player.getYRot()).toYRot();
		if (state.getBlock() instanceof StairBlock) {
			// a stair's facing is its high back: you sit facing away from it
			yaw = state.getValue(StairBlock.FACING).getOpposite().toYRot();
		} else if (!(state.getBlock() instanceof SlabBlock)) {
			// a modded chair: the top of its middle (not its backrest), facing the way it was placed to face
			VoxelShape shape = state.getCollisionShape(level, pos);
			double top = shape.isEmpty() ? Double.NaN : shape.max(Direction.Axis.Y, 0.5, 0.5);
			if (top > 0.0 && top <= 1.0) {
				height = top;
			}
			if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				yaw = state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
			}
		}
		return new Meditate.Seat(pos.immutable(), new Vec3(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5), yaw);
	}

	/** A right-click on a block: sits you on it if it's a free seat and your main hand is empty. */
	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
			|| !seatBlock(level.getBlockState(hit.getBlockPos()))) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer server)) {
			return InteractionResult.SUCCESS;
		}
		return trySeat(server, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** Sits this player on the seat at {@code pos} if it's free, in reach and they can sit; true if they did. */
	private static boolean trySeat(ServerPlayer player, BlockPos pos) {
		if (!Meditate.canSit(player) || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > (REACH + 1.0) * (REACH + 1.0)) {
			return false;
		}
		Meditate.Seat seat = seat(player.level(), pos, player);
		if (seat == null || Meditate.taken(player.level(), pos)) {
			return false;
		}
		Meditate.sitDown(player, seat);
		return true;
	}

	/** The Sit key: the next pose while sat; otherwise the seat under you or the one you look at, else the ground. */
	private static void key(ServerPlayer player) {
		if (Meditate.sitting(player)) {
			Meditate.nextPose(player);
			Meditate.State state = Meditate.of(player);
			if (state != null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.sit." + (state.seated() ? "seat" : "ground") + "." + state.pose())
					.withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!Meditate.canSit(player)) {
			return;
		}
		if (trySeat(player, player.blockPosition())) {
			return;
		}
		HitResult hit = player.pick(REACH, 1.0F, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && trySeat(player, block.getBlockPos())) {
			return;
		}
		Meditate.sitDown(player, null);
	}

	/** Each tick a player has sat settled ({@code ticks} since): the fire's warmth and a slow heal beside it. */
	static void rest(ServerPlayer player, long ticks) {
		if (ticks % 20 == 0) {
			if (litCampfireNear(player.level(), player.blockPosition())) {
				BY_FIRE.add(player.getUUID());
			} else {
				BY_FIRE.remove(player.getUUID());
			}
		}
		if (ticks % FIRE_HEAL_EVERY == FIRE_HEAL_EVERY - 1 && BY_FIRE.contains(player.getUUID()) && player.getHealth() < player.getMaxHealth()) {
			player.heal(FIRE_HEAL);
		}
	}

	private static boolean litCampfireNear(ServerLevel level, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIRE_RANGE, -1, -FIRE_RANGE), at.offset(FIRE_RANGE, 1, FIRE_RANGE))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(state)) {
				return true;
			}
		}
		return false;
	}
}
