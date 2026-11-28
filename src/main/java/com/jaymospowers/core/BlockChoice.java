package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "a context box for blocks like these, with use (and the specific in parentheses, for example a furnace would say
 * "Use(bake)") lift, or whatever other function the block has". Sneak + right-click, empty hands, on any block Carry On
 * could lift ({@link CarryOnCompat#couldLift}) opens a small box on the client ({@code client/BlockChoiceScreen}) instead
 * of lifting it: Use (what the sneak-click did before, such as Kitchen's cooking screen or a chest opening), Open door
 * on a Cooking for Blockheads oven, and Lift (Carry On, with everything in it). The box sends the pick here; the server
 * checks it again before doing it.
 */
public final class BlockChoice {
	/** How far past the player's own block reach the box's block may be (as vanilla's use-on packet allows). */
	static final double REACH_SLACK = 1.0;
	/** How far from the block's centre the clicked spot may be, on each axis (as vanilla's use-on packet allows). */
	static final double HIT_SLACK = 1.0000001;
	/** Cooking for Blockheads' ovens: the only blocks with Open door. */
	public static final TagKey<Block> OVENS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("cookingforblockheads", "ovens"));

	public enum Option {
		USE, DOOR, LIFT
	}

	/** Client → server: the player picked {@code option} for the block they sneak-clicked ({@code sneaking}: as they were). */
	public record Choose(BlockPos pos, Direction face, Vec3 at, boolean inside, boolean sneaking, Option option) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosPowers.id("block_choice"));
		public static final StreamCodec<FriendlyByteBuf, Choose> CODEC = StreamCodec.of(Choose::write, Choose::read);

		public Choose(BlockHitResult hit, boolean sneaking, Option option) {
			this(hit.getBlockPos(), hit.getDirection(), hit.getLocation(), hit.isInside(), sneaking, option);
		}

		private static void write(FriendlyByteBuf buf, Choose choose) {
			buf.writeBlockPos(choose.pos);
			buf.writeEnum(choose.face);
			buf.writeDouble(choose.at.x());
			buf.writeDouble(choose.at.y());
			buf.writeDouble(choose.at.z());
			buf.writeBoolean(choose.inside);
			buf.writeBoolean(choose.sneaking);
			buf.writeEnum(choose.option);
		}

		private static Choose read(FriendlyByteBuf buf) {
			return new Choose(buf.readBlockPos(), buf.readEnum(Direction.class), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
				buf.readBoolean(), buf.readBoolean(), buf.readEnum(Option.class));
		}

		BlockHitResult hit() {
			return new BlockHitResult(at, face, pos, inside);
		}

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	private BlockChoice() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, context) -> choose(context.player(), payload));
	}

	/** The choices for this block, in the box's order (both sides). */
	public static Option[] options(BlockState state) {
		return state.is(OVENS) ? new Option[] {Option.USE, Option.DOOR, Option.LIFT} : new Option[] {Option.USE, Option.LIFT};
	}

	private static void choose(ServerPlayer player, Choose choose) {
		ServerLevel level = player.level();
		BlockPos pos = choose.pos();
		BlockHitResult hit = choose.hit();
		Vec3 off = hit.getLocation().subtract(Vec3.atCenterOf(pos));
		if (!player.isAlive() || player.isSpectator() || !level.isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, REACH_SLACK)
			|| Math.abs(off.x()) >= HIT_SLACK || Math.abs(off.y()) >= HIT_SLACK || Math.abs(off.z()) >= HIT_SLACK
			|| level.getServer().isUnderSpawnProtection(level, pos, player) || !player.getMainHandItem().isEmpty()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		switch (choose.option()) {
			// the sneak-click as it would have gone: every use hook (Kitchen's cooking screen, plots, ...) and the block's own use
			case USE -> sneaking(player, choose.sneaking(), () -> player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit));
			case LIFT -> {
				if (!CarryOnCompat.couldLift(player, level, pos, state) || !CarryOnCompat.liftChosen(player, pos)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.block_choice.cant_lift"));
				}
			}
			case DOOR -> {
				// the oven's own sneak-click on its front (its door), past Kitchen's sneak-click (which opens the cooking screen)
				if (state.is(OVENS) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
					BlockHitResult front = new BlockHitResult(hit.getLocation(), state.getValue(BlockStateProperties.HORIZONTAL_FACING), pos, hit.isInside());
					sneaking(player, true, () -> state.useWithoutItem(level, player, front));
				}
			}
		}
	}

	/** Runs {@code use} with the player counted as sneaking or not (the box's screen let go of every key). */
	private static void sneaking(ServerPlayer player, boolean sneak, Runnable use) {
		boolean was = player.isShiftKeyDown();
		player.setShiftKeyDown(sneak);
		try {
			use.run();
		} finally {
			player.setShiftKeyDown(was);
		}
	}
}
