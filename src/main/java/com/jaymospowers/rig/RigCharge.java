package com.jaymospowers.rig;

import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Filling the Core. Nothing recharges it on its own:
 * <ul>
 * <li>stand on a redstone block for a slow trickle, or right-click one with an empty hand to drain it (the block is used
 * up) for a big gulp;</li>
 * <li>be struck by lightning: it fills completely;</li>
 * <li>stand at a Kingdoms smithy's forge while it burns (told apart by its block id, {@link #FORGE_IDS}: no hard
 * dependency on Jaymo's Kingdoms; its smithies' forge is a blast furnace).</li>
 * </ul>
 * Chrono's augment fills it faster ({@link #CHRONO_RATE}).
 */
public final class RigCharge {
	/** Standing on a redstone block: charge every {@link #STAND_EVERY} ticks. */
	static final int STAND_CHARGE = 8, STAND_EVERY = 5;
	/** Draining a redstone block by hand. */
	static final int DRAIN_CHARGE = 250;
	/** A lit forge within {@link #FORGE_REACH} blocks: charge every {@link #FORGE_EVERY} ticks. */
	static final int FORGE_CHARGE = 30, FORGE_EVERY = 20, FORGE_REACH = 2;
	/** Chrono's augment: every refill is this much bigger. */
	static final float CHRONO_RATE = 1.5F;
	/**
	 * The blocks that count as a Kingdoms forge, by registry id (lit ones only, when they have a lit state). Kingdoms'
	 * smithies are built round a blast furnace; {@code jaymoskingdoms:forge} is listed for when it gets a forge of its own.
	 */
	static final Set<String> FORGE_IDS = Set.of("minecraft:blast_furnace", "jaymoskingdoms:forge");

	private RigCharge() {
	}

	static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || Rig.core(player).isEmpty()
				|| !level.getBlockState(hit.getBlockPos()).is(Blocks.REDSTONE_BLOCK)) {
				return InteractionResult.PASS;
			}
			if (Rig.charge(player) >= Rig.MAX_CHARGE) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server && level instanceof ServerLevel serverLevel) {
				BlockPos pos = hit.getBlockPos();
				add(server, DRAIN_CHARGE);
				serverLevel.removeBlock(pos, false);
				serverLevel.sendParticles(DustParticleOptions.REDSTONE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.35, 0.35, 0.35, 0.0);
				serverLevel.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 1.4F);
			}
			return InteractionResult.SUCCESS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && source.is(DamageTypes.LIGHTNING_BOLT) && !Rig.core(player).isEmpty()) {
				boolean wasFull = Rig.charge(player) >= Rig.MAX_CHARGE;
				Rig.addCharge(player, Rig.MAX_CHARGE);
				if (!wasFull) {
					RigMind.struck(player);
				}
			}
			return true;
		});
	}

	/** Adds a refill (Chrono's augment makes it bigger). */
	static void add(ServerPlayer player, int amount) {
		if (Rig.augment(player) == Rig.Augment.CHRONO) {
			amount = Math.round(amount * CHRONO_RATE);
		}
		Rig.addCharge(player, amount);
	}

	static void tick(ServerPlayer player) {
		if (Rig.core(player).isEmpty() || Rig.charge(player) >= Rig.MAX_CHARGE) {
			return;
		}
		ServerLevel level = player.level();
		if (player.tickCount % STAND_EVERY == 0 && player.onGround() && level.getBlockState(player.getOnPos()).is(Blocks.REDSTONE_BLOCK)) {
			add(player, STAND_CHARGE);
			level.sendParticles(DustParticleOptions.REDSTONE, player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.3, 0.05, 0.3, 0.0);
		}
		if (player.tickCount % FORGE_EVERY == 0 && nearForge(level, player.blockPosition())) {
			add(player, FORGE_CHARGE);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
		}
	}

	private static boolean nearForge(ServerLevel level, BlockPos feet) {
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-FORGE_REACH, -1, -FORGE_REACH), feet.offset(FORGE_REACH, 1, FORGE_REACH))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !FORGE_IDS.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) {
				continue;
			}
			if (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT)) {
				return true;
			}
		}
		return false;
	}
}
