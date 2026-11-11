package com.jaymospowers.client.power.web;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: the web sense should turn "the character's fpv and third person characters head to the incoming danger". The
 * moment Spider-Sense picks up something new (a creature that has just turned on you, a creeper starting to swell, a
 * skeleton drawing its bow) that is out of your sight, your head snaps round to it: a short turn of the look itself, so
 * the first-person view swings and everyone else sees the head go. The mouse stays yours the whole time (the turn is
 * added to it), and it stops as soon as the danger is in view.
 */
public final class WebSenseGlance {
	/** A danger further off centre than this (degrees) is out of sight and worth a glance. */
	static final float GLANCE_FROM = 65.0F;
	/** The turn stops once the danger is this close to the centre of the view. */
	static final float GLANCE_TO = 18.0F;
	/** Degrees a tick the look turns (yaw, pitch), and the longest a glance lasts. */
	static final float GLANCE_YAW_SPEED = 22.0F, GLANCE_PITCH_SPEED = 9.0F;
	static final int GLANCE_TICKS = 12;
	/** Ticks between glances, so a crowd doesn't spin you about. */
	static final int GLANCE_COOLDOWN = 50;
	/** How often creepers and skeletons near you are checked, and how far out. */
	static final int SCAN_TICKS = 4;
	static final double SCAN_RANGE = com.jaymospowers.power.web.WebAscension.SENSE_RANGE;

	private static final Set<Integer> KNOWN = new HashSet<>();
	private static final Set<Integer> TELLS = new HashSet<>();
	private static int target = -1;
	private static int ticksLeft;
	private static long readyAt;

	private WebSenseGlance() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WebSenseGlance::tick);
	}

	/** The server's new list of creatures after us: anything not on the last list is a fresh danger. */
	public static void noticed(Collection<Integer> ids) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player != null) {
			for (int id : ids) {
				if (!KNOWN.contains(id)) {
					glanceAt(player, player.level().getEntity(id));
				}
			}
		}
		KNOWN.clear();
		KNOWN.addAll(ids);
	}

	public static void reset() {
		KNOWN.clear();
		TELLS.clear();
		target = -1;
		ticksLeft = 0;
	}

	private static void glanceAt(LocalPlayer player, Entity danger) {
		long now = player.level().getGameTime();
		if (danger == null || ticksLeft > 0 || now < readyAt || Math.abs(yawOff(player, danger)) < GLANCE_FROM) {
			return;
		}
		target = danger.getId();
		ticksLeft = GLANCE_TICKS;
		readyAt = now + GLANCE_COOLDOWN;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		if (!WebSymbioteClient.sensing()) {
			if (!KNOWN.isEmpty() || !TELLS.isEmpty() || ticksLeft > 0) {
				reset();
			}
			return;
		}
		if (player.tickCount % SCAN_TICKS == 0) {
			scan(player);
		}
		if (ticksLeft <= 0) {
			return;
		}
		ticksLeft--;
		Entity danger = player.level().getEntity(target);
		if (danger == null || !danger.isAlive() || minecraft.gui.screen() != null) {
			ticksLeft = 0;
			return;
		}
		float yaw = yawOff(player, danger);
		if (Math.abs(yaw) <= GLANCE_TO) {
			ticksLeft = 0;
			return;
		}
		Vec3 to = danger.getEyePosition().subtract(player.getEyePosition());
		float wantPitch = (float)-Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		float pitch = Mth.clamp(wantPitch - player.getXRot(), -GLANCE_PITCH_SPEED, GLANCE_PITCH_SPEED);
		player.setYRot(player.getYRot() + Mth.clamp(yaw, -GLANCE_YAW_SPEED, GLANCE_YAW_SPEED));
		player.setXRot(Mth.clamp(player.getXRot() + pitch, -90.0F, 90.0F));
	}

	/** Creepers starting to swell and skeletons drawing a bow aren't on the server's list: they give themselves away here. */
	private static void scan(LocalPlayer player) {
		Set<Integer> now = new HashSet<>();
		AABB box = player.getBoundingBox().inflate(SCAN_RANGE);
		for (Entity entity : player.level().getEntities(player, box, e -> e instanceof Creeper || e instanceof AbstractSkeleton)) {
			boolean tell = entity instanceof Creeper creeper ? creeper.getSwelling(0.0F) > 0.0F
				: ((AbstractSkeleton)entity).isUsingItem();
			if (tell && entity.distanceToSqr(player) <= SCAN_RANGE * SCAN_RANGE) {
				now.add(entity.getId());
				if (!TELLS.contains(entity.getId())) {
					glanceAt(player, entity);
				}
			}
		}
		TELLS.clear();
		TELLS.addAll(now);
	}

	/** How far round (degrees, signed) the look has to turn to face {@code danger}. */
	private static float yawOff(LocalPlayer player, Entity danger) {
		Vec3 to = danger.position().subtract(player.position());
		float want = (float)Math.toDegrees(Math.atan2(-to.x, to.z));
		return Mth.wrapDegrees(want - player.getYRot());
	}
}
