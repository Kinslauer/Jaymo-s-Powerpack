package com.jaymospowers.client.power.smoke;

import com.jaymospowers.client.Dismembered;
import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.power.smoke.SmokeVapour;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * How Vapour Form looks, on everyone's screen (from the synced {@link SmokeVapour#FORM}): no legs (and no leggings or
 * boots), only a thick tail of smoke pouring from the waist, straight down while hovering and streaming out behind at
 * speed, where Wind's pose lays the flier out flat ({@link #cruising}).
 */
public final class SmokeFlightPose {
	/** On a player in Vapour Form: the legs are hidden (the value is unused). */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:smoke_vapour");

	/** Someone else moving at least this fast (blocks a tick) in Vapour Form is taken to be cruising. */
	static final double CRUISE_SPEED = 0.6;
	/** Smoke a tick from the waist: big dark puffs and pale wisps; fewer from your own in first person (they'd fill the view). */
	static final int PUFFS = 4, WISPS = 3, OWN_FIRST_PERSON = 2;
	/** How far the tail reaches below the waist (blocks, the legs' length) and how fast it pours. */
	static final double TAIL = 0.9, POUR = 0.12;

	private static final String[] LEGS = {"right_leg", "left_leg", "right_pants", "left_pants"};

	private SmokeFlightPose() {
	}

	/** In Vapour Form and flying fast enough to lie out flat (WindPose asks). */
	public static boolean cruising(Player player) {
		if (!SmokeVapour.active(player)) {
			return false;
		}
		if (player == Minecraft.getInstance().player) {
			return SmokeFlightClient.cruising();
		}
		double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
		return dx * dx + dy * dy + dz * dz >= CRUISE_SPEED * CRUISE_SPEED;
	}

	/** Every client tick: the smoke pouring from everyone in Vapour Form. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			if (SmokeVapour.active(player) && !player.isInvisible()) {
				boolean own = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
				pour(level, player, own);
			}
		}
	}

	/** The smoke tail: from the waist down to where the feet were, or out behind along the flight. */
	private static void pour(ClientLevel level, Player player, boolean own) {
		RandomSource random = player.getRandom();
		float cruise = WindPose.amount(player, 1.0F);
		double height = player.getBbHeight();
		double scale = height / 1.8;
		Vec3 look = player.getLookAngle();
		// the waist: three-eighths up the standing body; laid out flat it's just behind the middle
		Vec3 waistUp = player.position().add(0.0, height * 0.42, 0.0);
		Vec3 waistFlat = player.position().add(0.0, height * 0.5, 0.0).subtract(look.scale(height * 0.08));
		Vec3 waist = waistUp.lerp(waistFlat, cruise);
		Vec3 down = new Vec3(0.0, -1.0, 0.0).lerp(look.scale(-1.0), cruise).normalize();
		Vec3 motion = player.getDeltaMovement();
		int puffs = own ? OWN_FIRST_PERSON : PUFFS;
		for (int i = 0; i < puffs; i++) {
			// anywhere along the tail, thickest at the waist
			double along = random.nextDouble() * random.nextDouble() * TAIL * scale;
			Vec3 at = waist.add(down.scale(along)).add(random.nextGaussian() * 0.12 * scale, random.nextGaussian() * 0.08 * scale,
				random.nextGaussian() * 0.12 * scale);
			Vec3 v = down.scale(POUR + random.nextDouble() * 0.05).add(motion.scale(0.3));
			level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, v.x + random.nextGaussian() * 0.015, v.y, v.z + random.nextGaussian() * 0.015);
		}
		for (int i = 0; i < (own ? 1 : WISPS); i++) {
			Vec3 at = waist.add(down.scale(random.nextDouble() * TAIL * scale * 1.2))
				.add(random.nextGaussian() * 0.15 * scale, 0.0, random.nextGaussian() * 0.15 * scale);
			Vec3 v = down.scale(POUR * 0.6).add(motion.scale(0.2));
			level.addParticle(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, v.x, v.y, v.z);
		}
		if (random.nextInt(own ? 6 : 3) == 0) {
			// the tip of the tail curls away in a big slow plume
			Vec3 tip = waist.add(down.scale(TAIL * scale));
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, tip.x, tip.y, tip.z, down.x * 0.03 + motion.x * 0.1, -0.02,
				down.z * 0.03 + motion.z * 0.1);
		}
	}

	/** From the avatar renderer's extractRenderState: no leggings or boots on smoke. */
	static void extract(Avatar entity, AvatarRenderState state) {
		boolean form = entity instanceof Player player && SmokeVapour.active(player);
		state.setData(KEY, form ? Boolean.TRUE : null);
		if (form) {
			state.legsEquipment = net.minecraft.world.item.ItemStack.EMPTY;
			state.feetEquipment = net.minecraft.world.item.ItemStack.EMPTY;
		}
	}

	/** Right after a model animates (with the lost limbs, so they're shown again once it's drawn): the legs hidden. */
	public static void hide(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || avatar.getData(KEY) == null) {
			return;
		}
		ModelPart root = model.root();
		for (String name : LEGS) {
			Dismembered.hidePart(Dismembered.find(root, name));
		}
	}
}
