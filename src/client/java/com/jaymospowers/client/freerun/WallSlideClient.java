package com.jaymospowers.client.freerun;

import com.jaymospowers.client.LedgeClient;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.freerun.WallSlide;
import com.jaymospowers.stamina.Feathers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (ParCool track A) The local player's wall slide (see {@link WallSlide} for the rules and the numbers): falling beside
 * or facing a wall, right-click held with an empty hand presses you against it (the wall ahead first, then the one at
 * your left or right), braking the fall to {@link WallSlide#FALL_SPEED}, with dust off the wall under the hands. It
 * ends when the click is let go, you land, the wall ends or turns slippery, or the Feathers run out.
 */
public final class WallSlideClient {
	/** Dust off the wall every this many ticks. */
	private static final int DUST_TICKS = 2;

	private static @Nullable Direction wall;
	private static int ticks;

	private WallSlideClient() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WallSlideClient::tick);
	}

	/** The local player is pressed against a wall, sliding down it (the right-clicks are the slide's). */
	public static boolean sliding() {
		return wall != null;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			wall = null;
			return;
		}
		boolean held = minecraft.options.keyUse.isDown() && player.getMainHandItem().isEmpty() && minecraft.gui.screen() == null
			&& WallSlide.canSlide(player) && !player.isFallFlying() && !LedgeClient.active() && !FreerunClient.active();
		if (wall != null) {
			ticks++;
			boolean paid = ticks % WallSlide.COST_TICKS != 0 || Feathers.canSpend(player, WallSlide.HOLD_COST);
			if (!held || !paid || player.getDeltaMovement().y > 0.0 || !WallSlide.slideWall(player.level(), player, wall, WallSlide.REACH)) {
				stop();
				return;
			}
			press(player, wall);
			return;
		}
		if (!held || player.getDeltaMovement().y > -WallSlide.MIN_FALL || !Feathers.canSpend(player, WallSlide.START_COST)
			|| LedgeClient.clickTaken(minecraft, player)) {
			return;
		}
		Direction ahead = Direction.fromYRot(player.getYRot());
		for (Direction side : new Direction[] {ahead, ahead.getCounterClockWise(), ahead.getClockWise()}) {
			if (WallSlide.slideWall(player.level(), player, side, WallSlide.REACH)) {
				wall = side;
				ticks = 0;
				ClientPlayNetworking.send(new WallSlide.Slide(side.get2DDataValue()));
				press(player, side);
				return;
			}
		}
	}

	/** Brake the fall toward the slide's speed and keep pressed to the wall, with a little dust. */
	private static void press(LocalPlayer player, Direction face) {
		Vec3 v = player.getDeltaMovement();
		double excess = -WallSlide.FALL_SPEED - v.y;
		// (eased down from a fast fall, then held at the slide's speed: travel adds gravity back each tick)
		double vy = excess <= 0.0 ? v.y : excess < WallSlide.SNAP ? -WallSlide.FALL_SPEED : v.y + excess * WallSlide.BRAKE;
		player.setDeltaMovement(v.x * 0.6 + face.getStepX() * WallSlide.HUG, vy, v.z * 0.6 + face.getStepZ() * WallSlide.HUG);
		if (ticks % DUST_TICKS == 0) {
			BlockPos pos = WallSlide.wallBlock(player, face, WallSlide.REACH, 1.3);
			BlockState state = player.level().getBlockState(pos);
			if (!state.isAir()) {
				double out = player.getBbWidth() * 0.5;
				player.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
					player.getX() + face.getStepX() * out, player.getY() + 1.4, player.getZ() + face.getStepZ() * out, 0.0, 0.1, 0.0);
			}
		}
	}

	private static void stop() {
		wall = null;
		ticks = 0;
		if (ClientPlayNetworking.canSend(WallSlide.Slide.TYPE)) {
			ClientPlayNetworking.send(new WallSlide.Slide(-1));
		}
	}
}
