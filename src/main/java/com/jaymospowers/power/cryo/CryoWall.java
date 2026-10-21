package com.jaymospowers.power.cryo;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) X, Ice Wall: a wall of packed ice {@link #WIDTH} wide and {@link #HEIGHT} high rises where you look (the
 * ground or a wall's foot within {@link #REACH} blocks), square across your look. It only goes into air, water, snow
 * layers and plants (never into anything solid, never into a creature) and always melts away after {@link #MELT_TICKS}
 * ({@link CryoIce}): nothing is left behind.
 */
public final class CryoWall {
	static final double REACH = 24.0;
	static final int WIDTH = 5;
	static final int HEIGHT = 3;
	/** How long the wall stands (ticks: 15 s); a Frost-born in the cold holds it longer. */
	public static final int MELT_TICKS = 300;
	public static final int COOLDOWN = 200;
	/** How far down (blocks) a wall raised in the air looks for ground to stand on. */
	static final int DROP = 4;

	private CryoWall() {
	}

	static void cast(ServerPlayer player) {
		if (!Powers.has(player, CryoPower.INSTANCE) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (Mastery.unlocked(player) < CryoPower.WALL_AT) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.locked").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (Powers.silenced(player) || !CryoPower.ready(player, CryoState.WALL)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_wall_nothing").withStyle(ChatFormatting.GRAY));
			return;
		}
		// the open block in front of the face it hit, then down to the ground
		BlockPos base = hit.getBlockPos().relative(hit.getDirection());
		for (int i = 0; i < DROP && level.getBlockState(base.below()).isAir(); i++) {
			base = base.below();
		}
		// square across the look: along Z when you look mostly east or west, along X when north or south
		Direction along = Math.abs(look.x) > Math.abs(look.z) ? Direction.SOUTH : Direction.EAST;
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		int ticks = Math.round(MELT_TICKS * CryoPower.strength(player));
		int placed = 0;
		for (int side = -WIDTH / 2; side <= WIDTH / 2; side++) {
			for (int up = 0; up < HEIGHT; up++) {
				BlockPos pos = base.relative(along, side).above(up);
				AABB cell = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
				if (!level.getEntitiesOfClass(LivingEntity.class, cell, e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
					continue; // never into a creature
				}
				if (CryoIce.place(level, pos, ice, ticks, CryoIce.Mode.OPEN)) {
					placed++;
					if (up == 0) {
						level.sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.02);
					}
				}
			}
		}
		if (placed == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_wall_nothing").withStyle(ChatFormatting.GRAY));
			return;
		}
		CryoPower.cooldown(player, CryoState.WALL, COOLDOWN);
		CryoMoves.begin(player, CryoMoves.WALL);
		com.jaymospowers.core.Mastery.used(player);
		Vec3 at = Vec3.atCenterOf(base);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
		com.jaymospowers.core.Shakes.around(level, at, 8.0, 0.3F);
	}
}
