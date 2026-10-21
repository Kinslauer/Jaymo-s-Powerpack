package com.jaymospowers.aether.gear;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.FloatingBlock;
import com.jaymospowers.aether.FloatingBlockEntity;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What gravitite does: a gravitite tool ({@link GravititeToolItem}) used on a block lifts it as a rising block
 * ({@link FloatingBlockEntity}), unless that block holds items (a block entity), can't be broken, or has a solid block over
 * it. The whole gravitite armour set also gives one extra jump in mid-air: the client sends {@link DoubleJump} (the move
 * itself is the player's own, as every movement is), the server checks the set and that the jump wasn't spent since the last
 * landing, and forgets the fall.
 */
public final class GravititeTools {
	// ---- tuning ----
	/** The extra jump: its strength against a normal jump, and its push sideways (blocks a tick) the way you steer. */
	public static final double JUMP_POWER = 1.25, JUMP_PUSH = 0.2;
	/** A normal jump's speed (blocks a tick), what the extra jump is a multiple of. */
	public static final double BASE_JUMP = 0.42;
	/** Durability a lift costs the tool. */
	public static final int LIFT_WEAR = 1;

	/** Client to server: "I used my extra jump". */
	public record DoubleJump() implements CustomPacketPayload {
		public static final Type<DoubleJump> TYPE = new Type<>(JaymosPowers.id("gravitite_double_jump"));
		public static final StreamCodec<ByteBuf, DoubleJump> CODEC = StreamCodec.unit(new DoubleJump());

		@Override
		public Type<DoubleJump> type() {
			return TYPE;
		}
	}

	/** Players who have used their extra jump since they last stood on something. Server memory only. */
	private static final Set<UUID> SPENT = new HashSet<>();

	private GravititeTools() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(DoubleJump.TYPE, DoubleJump.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DoubleJump.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (ArmorAbilities.gravitite(player) && !player.onGround() && !player.isInWater() && !player.isSpectator() && SPENT.add(player.getUUID())) {
				player.resetFallDistance();
				if (player.level() instanceof ServerLevel level) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
					level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!SPENT.isEmpty()) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.onGround() || player.isInWater() || player.getAbilities().flying) {
						SPENT.remove(player.getUUID());
					}
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SPENT.remove(handler.getPlayer().getUUID()));
	}

	/** Lifts the block {@code context} points at if it can be, and says whether it did. */
	static boolean lift(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (player == null || player.isSpectator() || !liftable(level, pos, state)) {
			return false;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			server.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			server.addFreshEntity(FloatingBlockEntity.create(server, pos, state, false));
			server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
			context.getItemInHand().hurtAndBreak(LIFT_WEAR, sp, context.getHand());
		}
		return true;
	}

	/** Blocks that can be lifted: solid ones with room above, nothing that holds items, grows over two blocks, or can't be broken. */
	static boolean liftable(Level level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.hasBlockEntity() && !(state.getBlock() instanceof LiquidBlock) && state.getDestroySpeed(level, pos) >= 0.0F
			&& !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && !state.hasProperty(BlockStateProperties.BED_PART)
			&& FloatingBlock.free(level.getBlockState(pos.above()));
	}
}
