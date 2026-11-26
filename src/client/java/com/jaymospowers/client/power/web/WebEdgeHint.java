package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The ledge leap from a wall run. Andy's pick from Legends: Superheroes, "running up a wall, hold use and you leap for the
 * next open ledge above, pulling yourself over it". The wall run ({@link WebWallRunClient}) and the cling and climb up a
 * wall ({@link WebParkourClient}) carry you along and up a wall; the ledge launch ({@link WebLedge}) throws you up and over
 * a lip at the end of a zip. This joins them: on either, a ledge up the wall (an edge of the wall with open air above it,
 * between {@link #MIN_UP} and {@link #SCAN_UP} blocks over your feet) is looked for every tick and shown as a small web
 * glint on its lip, and holding the use key leaps for it with the ledge launch's own throw (up and over, with the push
 * into the roof), ending the run or the cling.
 */
final class WebEdgeHint {
	/** The lowest and the highest a ledge's top may be over your feet (blocks) to be leapt for. */
	static final double MIN_UP = 1.4, SCAN_UP = 6.0;
	/** The ledge's glint is drawn this often (ticks). */
	static final int GLINT_EVERY = 5;

	/** A ledge found up the wall: where the throw to it goes, and the middle of its lip (for the glint). */
	record Ledge(Vec3 velocity, Vec3 lip) {
	}

	private WebEdgeHint() {
	}

	/** The use key is held (with no screen open). */
	static boolean wanted() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.options.keyUse.isDown() && minecraft.gui.screen() == null;
	}

	/**
	 * The next open ledge up the wall at {@code toward} (the direction from you to the wall), if there is one: the first
	 * block up the wall's column, over {@link #MIN_UP} above your feet and within {@link #SCAN_UP}, with the wall under it
	 * and two open blocks over it.
	 */
	static @Nullable Ledge find(LocalPlayer player, Direction toward) {
		Level level = player.level();
		BlockPos column = BlockPos.containing(player.getX() + toward.getStepX() * 0.8, player.getY(), player.getZ() + toward.getStepZ() * 0.8);
		int first = (int)Math.floor(MIN_UP), last = (int)Math.ceil(SCAN_UP);
		for (int k = first; k <= last; k++) {
			BlockPos wall = column.above(k);
			double top = wall.getY() + 1.0;
			if (top - player.getY() < MIN_UP || top - player.getY() > SCAN_UP + 1.0) {
				continue;
			}
			if (solid(level, wall) && solid(level, wall.below()) && open(level, wall.above()) && open(level, wall.above(2))) {
				Direction out = toward.getOpposite();
				// the point on the wall's near face, a little under the lip: the ledge launch looks for the lip from there
				Vec3 anchor = new Vec3(wall.getX() + 0.5 - toward.getStepX() * 0.5, top - 0.4, wall.getZ() + 0.5 - toward.getStepZ() * 0.5);
				Vec3 thrown = WebLedge.launch(player, new WebNet.Pull(anchor, anchor, -1, 0, (byte)out.ordinal()));
				if (thrown == null) {
					return null;
				}
				return new Ledge(thrown, new Vec3(anchor.x, top + 0.05, anchor.z));
			}
		}
		return null;
	}

	/** Leaps for the ledge up the wall at {@code toward}, if the use key is held and there is one: true if you did. */
	static boolean leap(LocalPlayer player, Direction toward) {
		if (!wanted()) {
			return false;
		}
		Ledge ledge = find(player, toward);
		if (ledge == null) {
			return false;
		}
		player.setDeltaMovement(ledge.velocity());
		player.resetFallDistance();
		WebSwing.play(player, SoundEvents.SLIME_JUMP, 0.8F, 1.1F);
		WebSwing.play(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 1.4F);
		Vec3 at = player.position().add(toward.getStepX() * 0.4, 0.5, toward.getStepZ() * 0.4);
		for (int i = 0; i < 6; i++) {
			player.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, (player.getRandom().nextDouble() - 0.5) * 0.1, 0.05,
				(player.getRandom().nextDouble() - 0.5) * 0.1);
		}
		return true;
	}

	/** Shows the next ledge up the wall (a few web specks on its lip), every few ticks. */
	static void glint(LocalPlayer player, Direction toward) {
		if (player.tickCount % GLINT_EVERY != 0) {
			return;
		}
		Ledge ledge = find(player, toward);
		if (ledge == null) {
			return;
		}
		Vec3 lip = ledge.lip();
		for (int i = 0; i < 2; i++) {
			player.level().addParticle(ParticleTypes.ITEM_COBWEB, lip.x + (player.getRandom().nextDouble() - 0.5) * 0.8, lip.y,
				lip.z + (player.getRandom().nextDouble() - 0.5) * 0.8, 0.0, 0.02, 0.0);
		}
	}

	private static boolean solid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static boolean open(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
