package com.jaymospowers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

/**
 * (Motion patch 1) The panicked flight, after Actions &amp; Stuff's scared run (Andy: "your pov follows the shaky cam and
 * turnaround"). A timid or suspicious player ({@link BodyMotion#jumpy}) sprinting away hurt from a monster close
 * behind them has the first-person view shake and swing round over the shoulder to look back at it for a moment, then
 * face front again. Only the view turns (applied with the hurt bob, {@code ScreenShakeMixin}): the aim and the run
 * keep going straight.
 */
public final class PanicCam {
	// ===== the knobs ================================================================================================
	/** At or under this share of health counts as hurt enough to panic. */
	static final float HEALTH = 0.5F;
	/** A monster this near (blocks), at least this many degrees off the look (behind you), is chasing. */
	static final double CHASER_RANGE = 12.0;
	static final float BEHIND = 110.0F;
	/** The look back: at most this many degrees, turning over these ticks, held, then back. */
	static final float LOOK_MAX = 75.0F;
	static final int TURN_TICKS = 7, HOLD_TICKS = 8, BACK_TICKS = 10;
	/** At most one look back this often (ticks). */
	static final int GAP_TICKS = 140;
	/** The shake when it starts (degrees), and the small rattle every {@link #RATTLE_EVERY} ticks while still chased. */
	static final float START_SHAKE = 1.6F, RATTLE = 0.5F;
	static final int RATTLE_EVERY = 12;
	/** How often to look for a chaser (ticks). */
	static final int CHECK_EVERY = 5;
	// ================================================================================================================

	private static int age = -1;
	private static float look;
	private static int cooldown;
	private static int chased;
	private static int clock;

	private PanicCam() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PanicCam::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			age = -1;
			chased = 0;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		if (age >= 0 && ++age > TURN_TICKS + HOLD_TICKS + BACK_TICKS) {
			age = -1;
		}
		if (++clock % CHECK_EVERY != 0) {
			if (chased > 0 && clock % RATTLE_EVERY == 0) {
				ScreenShake.add(RATTLE);
			}
			return;
		}
		Entity chaser = player.isSprinting() && player.getHealth() <= player.getMaxHealth() * HEALTH && BodyMotion.jumpy(player)
			? chaser(player) : null;
		if (chaser == null) {
			chased = 0;
			return;
		}
		chased++;
		if (age < 0 && cooldown <= 0) {
			double dx = chaser.getX() - player.getX(), dz = chaser.getZ() - player.getZ();
			float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			look = Mth.clamp(Mth.wrapDegrees(yaw - player.getYRot()), -LOOK_MAX, LOOK_MAX);
			age = 0;
			cooldown = GAP_TICKS;
			ScreenShake.add(START_SHAKE);
		}
	}

	/** The nearest monster close behind the player, or null. */
	private static Entity chaser(LocalPlayer player) {
		List<Monster> near = player.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(CHASER_RANGE),
			m -> m.isAlive() && !m.isInvisible());
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Monster m : near) {
			double dx = m.getX() - player.getX(), dz = m.getZ() - player.getZ();
			float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			double d = dx * dx + dz * dz;
			if (Math.abs(Mth.wrapDegrees(yaw - player.getYRot())) >= BEHIND && d < bestDistance && d <= CHASER_RANGE * CHASER_RANGE) {
				best = m;
				bestDistance = d;
			}
		}
		return best;
	}

	/** With the hurt bob: the view swung round towards the chaser (first person only). */
	public static void applyCamera(PoseStack poseStack, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (age < 0 || minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
			return;
		}
		float t = age + partialTicks, w;
		if (t < TURN_TICKS) {
			w = t / TURN_TICKS;
		} else if (t < TURN_TICKS + HOLD_TICKS) {
			w = 1.0F;
		} else {
			w = 1.0F - Math.min(1.0F, (t - TURN_TICKS - HOLD_TICKS) / BACK_TICKS);
		}
		w = w * w * (3.0F - 2.0F * w);
		// a positive turn about the view's up axis swings the view to the right, as a growing yaw does
		poseStack.rotateDegrees(Axis.YP, look * w);
	}
}
