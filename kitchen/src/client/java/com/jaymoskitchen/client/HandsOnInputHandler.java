package com.jaymoskitchen.client;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Station;
import com.jaymoskitchen.delight.CookwareBlock;
import com.jaymoskitchen.delight.CookwareBlockEntity;
import com.jaymoskitchen.delight.CuttingBoardBlock;
import com.jaymoskitchen.delight.CuttingBoardEntity;
import com.jaymoskitchen.delight.Delight;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.UUID;

/**
 * Client-side input detection for hands-on cooking. Detects mouse/keyboard input
 * for CHOP (left-click swing on cutting board with knife) and STIR (mouse movement
 * around pot with spoon) and sends input packets to the server.
 */
public final class HandsOnInputHandler {
	private static final Minecraft MC = Minecraft.getInstance();

	private HandsOnInputHandler() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HandsOnInputHandler::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (!com.jaymoskitchen.JaymosKitchen.HANDS_ON_COOKING) {
			return;
		}
		Player player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}

		// Check for active sessions that need input
		for (var entry : HandsOnClient.SESSIONS.entrySet()) {
			HandsOnClient.ClientSession session = entry.getValue();
			if (!session.playerId.equals(player.getUUID())) {
				continue;
			}

			// Verify the station still exists and is valid
			BlockEntity be = level.getBlockEntity(session.stationPos);
			if (be == null) {
				continue;
			}

			switch (session.dish.method()) {
				case CHOP -> handleChopInput(player, level, session, be);
				case STIR -> handleStirInput(player, level, session, be);
				default -> {}
			}
		}

		// Check for starting a new session: right-click on station with correct tool
		if (minecraft.options.useKey.isDown() && minecraft.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = hit.getBlockPos();
			BlockEntity be = level.getBlockEntity(pos);
			ItemStack mainHand = player.getMainHandItem();

			if (be instanceof CuttingBoardEntity board && mainHand.is(Delight.KNIVES)) {
				// Start CHOP session: need a dish on the board that uses CHOP method
				// For now, we'll send a start request to the server via a custom packet
				// Actually, the server handles starting via UseBlockCallback, so we just need
				// to detect when the server sends HandsOnStartPayload
			} else if (be instanceof CookwareBlockEntity cookware && isSpoon(mainHand)) {
				// Start STIR session
			}
		}
	}

	private static void handleChopInput(Player player, ClientLevel level, HandsOnClient.ClientSession session, BlockEntity be) {
		// CHOP: left-click (attack) when looking at the cutting board with a knife
		if (MC.options.attackKey.isDown() && session.chopAnim < 0 && session.doneTicks < 0) {
			// Check if player is looking at the cutting board
			if (MC.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
					&& hit.getBlockPos().equals(session.stationPos)) {
				ItemStack mainHand = player.getMainHandItem();
				if (mainHand.is(Delight.KNIVES)) {
					// Perform chop
					session.chopAnim = 0;
					float mark = session.cutPositions.get(session.nextCut);
					float off = Math.abs(session.slidePos - mark);
					int result = off <= Minigames.CHOP_PERFECT ? 2 : off <= Minigames.CHOP_GOOD ? 1 : 0;
					session.results.add(result);
					session.lastResult = result;
					session.feedback = 10;
					session.nextCut++;
					session.slidePos += session.slideSpeed;
					session.slideSpeed += Minigames.CHOP_SPEED_STEP;

					// Send input to server
					HandsOnClient.sendChopInput(session.sessionId, session.slidePos);

					if (session.nextCut >= Minigames.CUTS) {
						session.doneTicks = 0;
					}
				}
			}
		}

		// Animate chop
		if (session.chopAnim >= 0) {
			session.chopAnim++;
			if (session.chopAnim > 6) {
				session.chopAnim = -1;
			}
		}

		// Feedback timer
		if (session.feedback > 0) {
			session.feedback--;
		}

		// Done timer
		if (session.doneTicks >= 0) {
			session.doneTicks++;
			if (session.doneTicks > 12) {
				// Session will be cleaned up by HandsOnClient.onFinish
			}
		}

		// Auto-slide when not done
		if (session.doneTicks < 0 && session.chopAnim < 0) {
			session.slidePos += session.slideSpeed;
			// Check for missed cuts
			while (session.nextCut < Minigames.CUTS && session.slidePos > session.cutPositions.get(session.nextCut) + Minigames.CHOP_GOOD) {
				session.results.add(0);
				session.misses++;
				session.feedback = 10;
				session.lastResult = 0;
				session.nextCut++;
				session.slideSpeed += Minigames.CHOP_SPEED_STEP;
			}
			if (session.nextCut >= Minigames.CUTS) {
				session.doneTicks = 0;
			}
		}
	}

	private static void handleStirInput(Player player, ClientLevel level, HandsOnClient.ClientSession session, BlockEntity be) {
		// STIR: mouse movement in a circle around the pot center
		if (!session.placed) {
			session.placed = true;
			// Pot center in screen space - we'll track mouse movement relative to block position
		}

		double mouseX = MC.mouseHandler.xpos();
		double mouseY = MC.mouseHandler.ypos();

		// Convert block position to screen coordinates
		Vec3 potCenter = new Vec3(session.stationPos.getX() + 0.5, session.stationPos.getY() + 1.2, session.stationPos.getZ() + 0.5);
		Vector2f screenPos = worldToScreen(potCenter);
		if (screenPos == null) {
			return; // Pot not visible
		}

		double dx = mouseX - screenPos.x;
		double dy = mouseY - screenPos.y;
		double distSq = dx * dx + dy * dy;

		// Only register stirring if mouse is reasonably close to pot (within ~100 pixels)
		if (distSq >= 36 && distSq <= 10000) {
			float angle = (float) Math.atan2(dy, dx);
			if (!Float.isNaN(session.lastAngle)) {
				float step = Math.abs(Mth.wrapDegrees((angle - session.lastAngle) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
				session.omega = session.omega * 0.7F + step * 0.3F;
				session.spoonAngle = angle;

				// Check boil state
				boolean boiling = session.boiling();

				// Send input to server
				HandsOnClient.sendStirInput(session.sessionId, angle, session.omega, boiling);

				// Update local state
				float min = boiling ? Minigames.BOIL_MIN : Minigames.STIR_MIN;
				float max = boiling ? Minigames.BOIL_MAX : Minigames.STIR_MAX;
				if (session.omega >= min && session.omega <= max) {
					session.stirred++;
				} else if (session.omega < min) {
					if (minecraft.tickCount > 20) {
						session.stuck++;
					}
				} else {
					session.spilled++;
				}

				if (session.stirred >= Minigames.STIR_TICKS) {
					// Done - server will send finish packet
				} else if (session.stuck >= Minigames.STUCK_CHARRED || minecraft.tickCount - session.startTime >= Minigames.STIR_LIMIT) {
					// Burnt/charred
				}
			} else {
				session.lastAngle = angle;
			}
		} else {
			session.lastAngle = Float.NaN;
		}

		// Boil timer
		if (session.boilTicks > 0) {
			session.boilTicks--;
		} else if (--session.nextBoil <= 0) {
			session.boilTicks = Minigames.BOIL_TICKS;
			session.nextBoil = Minigames.BOIL_GAP_MIN + new java.util.Random().nextInt(Minigames.BOIL_GAP_MAX - Minigames.BOIL_GAP_MIN);
		}
	}

	/** Converts a world position to screen coordinates. Returns null if not visible. */
	private static Vector2f worldToScreen(Vec3 worldPos) {
		net.minecraft.client.renderer.GameRenderer renderer = MC.gameRenderer;
		if (renderer == null) {
			return null;
		}
		net.minecraft.client.renderer.Camera camera = MC.gameRenderer.getMainCamera();
		if (camera == null) {
			return null;
		}
		// Get the view projection matrix
		com.mojang.blaze3d.vertex.PoseStack poseStack = new com.mojang.blaze3d.vertex.PoseStack();
		// Project world position to screen
		Vec3 cameraPos = camera.getPosition();
		Vec3 relative = worldPos.subtract(cameraPos);
		// Use the game's built-in projection
		double x = relative.x;
		double y = relative.y;
		double z = relative.z;

		// Get the view matrix
		org.joml.Matrix4f viewMatrix = camera.getViewMatrix(new org.joml.Matrix4f());
		org.joml.Matrix4f projectionMatrix = camera.getProjectionMatrix(new org.joml.Matrix4f());

		org.joml.Vector4f clipPos = new org.joml.Vector4f((float)x, (float)y, (float)z, 1.0f);
		clipPos.mul(viewMatrix).mul(projectionMatrix);

		if (clipPos.w <= 0) {
			return null; // Behind camera
		}

		float ndcX = clipPos.x / clipPos.w;
		float ndcY = clipPos.y / clipPos.w;

		int width = MC.getWindow().getGuiScaledWidth();
		int height = MC.getWindow().getGuiScaledHeight();

		float screenX = (ndcX + 1.0f) * 0.5f * width;
		float screenY = (1.0f - ndcY) * 0.5f * height;

		return new Vector2f(screenX, screenY);
	}

	/** Checks if an item is a spoon (for STIR). */
	private static boolean isSpoon(ItemStack stack) {
		return stack.is(Delight.SPOONS);
	}
}