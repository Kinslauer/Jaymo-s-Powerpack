package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteState;
import com.jaymospowers.power.web.WebSymbioteStrains;
import com.jaymospowers.power.web.WebTendrils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Idle fidgets (Patch 3 of the symbiote's 1.13 plan; client only, nothing is sent): a bonded player with the tendrils
 * out who has stood still a while lets one tendril wander: it pokes a block nearby, or, in rain under open sky, reaches up
 * to taste it. Worked out on each client from the player's own movement and the game clock, so everyone sees it; drawn
 * with the same flesh as {@link WebTendrilBundle}.
 */
final class WebSymbioteFidget {
	/** Standing still this long (ticks) before it starts, then a fidget every this long at the soonest and (random) this much later. */
	private static final int STILL_TICKS = 100, GAP_MIN = 160, GAP_RANGE = 280;
	/** One fidget lasts this long (ticks); a block is looked for this many times; it reaches this far (blocks); rain is tasted this far up. */
	private static final int FIDGET_TICKS = 50, TRIES = 14;
	private static final double POKE_REACH = 3.0, TASTE_UP = 2.4;
	/** Where its tendril roots on the back (blocks up, blocks behind), its pieces, and its radius at the root and tip. */
	private static final double ROOT_HEIGHT = 1.3, ROOT_BACK = 0.18;
	private static final int PIECES = 10;
	private static final double RADIUS = 0.045, TIP_RADIUS = 0.018;

	private static final class F {
		int still;
		long nextAt;
		long start = -1L;
		boolean rain;
		Vec3 target = Vec3.ZERO;
		Vec3 lastPos = Vec3.ZERO;
		boolean poked;
	}

	private static final Map<Integer, F> FIDGETS = new HashMap<>();
	private static long clock;

	private WebSymbioteFidget() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WebSymbioteFidget::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(WebSymbioteFidget::render);
	}

	private static boolean eligible(AbstractClientPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (!state.bonded() || !state.has(WebSymbioteState.TENDRILS) || !player.isAlive() || player.isInvisible() || player.isSleeping()
			|| player.isSwimming() || player.isFallFlying() || player.isCrouching() || !player.onGround()) {
			return false;
		}
		WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
		return (grips == null || grips.right() < 0 && grips.left() < 0) && player.getAttached(WebPower.POSE) == null;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			FIDGETS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		if (WebSymbioteSettingsClient.isOff(com.jaymospowers.power.web.WebSymbioteSettings.Setting.FIDGETS)) {
			FIDGETS.clear();
			return;
		}
		clock++;
		for (AbstractClientPlayer player : level.players()) {
			F f = FIDGETS.computeIfAbsent(player.getId(), id -> new F());
			Vec3 here = player.position();
			boolean still = here.subtract(f.lastPos).lengthSqr() < 0.0004;
			f.lastPos = here;
			if (!still || !eligible(player)) {
				f.still = 0;
				f.start = -1L;
				continue;
			}
			f.still++;
			if (f.start >= 0L) {
				if (clock - f.start >= FIDGET_TICKS) {
					f.start = -1L;
					f.nextAt = clock + GAP_MIN + player.getRandom().nextInt(GAP_RANGE);
				} else if (!f.rain && !f.poked && clock - f.start >= FIDGET_TICKS / 2) {
					f.poked = true;
					level.playLocalSound(f.target.x, f.target.y, f.target.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.3F, 1.5F, false);
				}
				continue;
			}
			if (f.still < STILL_TICKS || clock < f.nextAt) {
				continue;
			}
			begin(level, player, f);
		}
		if (FIDGETS.size() > 32) {
			FIDGETS.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	private static void begin(ClientLevel level, AbstractClientPlayer player, F f) {
		f.nextAt = clock + GAP_MIN;
		BlockPos feet = player.blockPosition();
		if (level.isRaining() && level.canSeeSky(feet.above(2)) && player.getRandom().nextFloat() < 0.5F) {
			f.rain = true;
			f.poked = true;
			f.target = player.position().add((player.getRandom().nextDouble() - 0.5) * 0.6, TASTE_UP, (player.getRandom().nextDouble() - 0.5) * 0.6);
			f.start = clock;
			return;
		}
		for (int i = 0; i < TRIES; i++) {
			BlockPos pos = feet.offset(player.getRandom().nextInt(7) - 3, player.getRandom().nextInt(4) - 1, player.getRandom().nextInt(7) - 3);
			if (level.getBlockState(pos).isAir()) {
				continue;
			}
			for (Direction face : Direction.values()) {
				if (level.getBlockState(pos.relative(face)).isAir()) {
					Vec3 at = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
					if (at.distanceTo(player.getEyePosition()) <= POKE_REACH && at.y >= player.getY() - 0.5) {
						f.rain = false;
						f.poked = false;
						f.target = at;
						f.start = clock;
						return;
					}
				}
			}
		}
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || FIDGETS.isEmpty()) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (AbstractClientPlayer player : level.players()) {
			F f = FIDGETS.get(player.getId());
			if (f == null || f.start < 0L || player.isInvisible()
				|| player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()
				|| player.position().distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			float p = Mth.clamp((clock - f.start + partial) / FIDGET_TICKS, 0.0F, 1.0F);
			double reach = Math.sin(Math.PI * p);
			reach = reach * reach * (3.0 - 2.0 * reach);
			float yaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
			Vec3 root = player.getPosition(partial).add(0.0, ROOT_HEIGHT, 0.0).subtract(forward.scale(ROOT_BACK));
			Vec3 tip = root.lerp(f.target, reach);
			int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partial);
			double time = clock + partial;
			Vec3[] points = new Vec3[PIECES + 1];
			Vec3 side = tip.subtract(root).cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
			for (int k = 0; k <= PIECES; k++) {
				double s = k / (double)PIECES;
				// it rises off the back, arcs and wavers a little on the way
				Vec3 along = root.lerp(tip, s);
				double arc = Math.sin(Math.PI * s) * 0.5 * (f.rain ? 0.3 : 1.0) * reach;
				double waver = Math.sin(time * 0.45 + s * 5.0) * 0.07 * s * reach;
				points[k] = along.add(0.0, arc, 0.0).add(side.scale(waver));
			}
			context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
				RenderTypes.entityCutout(WebStrainLook.texture(WebTubes.TEXTURE, WebSymbioteStrains.of(player))), (pose, buffer) -> draw(buffer, pose, camera, points, light));
		}
	}

	private static void draw(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera, Vec3[] points, int light) {
		for (int k = 0; k + 1 < points.length; k++) {
			double r0 = Mth.lerp(k / (double)PIECES, RADIUS, TIP_RADIUS), r1 = Mth.lerp((k + 1) / (double)PIECES, RADIUS, TIP_RADIUS);
			WebTubes.cord(buffer, pose, points[k].subtract(camera), points[k + 1].subtract(camera), r0, r1,
				(k / 3) % 2 == 0 ? WebTubes.BLACK : WebTubes.SHEEN, light);
		}
	}
}
