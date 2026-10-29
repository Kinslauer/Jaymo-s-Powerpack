package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.149) The symbiote's swing line grows out of the body and arm. On R, tendrils pour out of the torso and wrap the
 * swinging arm from shoulder to hand, and only then does the cord shoot from the hand; while held the arm stays engulfed;
 * on release the cord reels back from the anchor into the hand and the wrap withdraws into the body. No spent line is
 * left behind (see {@link WebSpentLines}: a symbiote's lines are never spent).
 */
final class WebEngulf {
	/** Ticks for the wrap to climb the arm, for the cord to reel in, and for the wrap to draw back into the body. */
	private static final float WRAP_TICKS = 3.0F, REEL_TICKS = 3.0F, UNWRAP_TICKS = 4.0F;
	/** The cord starts to shoot once the wrap is this far down the arm. */
	static final float CORD_AT = 0.75F;
	private static final int STRANDS = 9, PIECES = 10;
	/** Share of a strand's length that is the stretch from the torso to the shoulder. */
	private static final double BODY_SHARE = 0.25;
	private static final double ARM_RADIUS = 0.115, HAND_RADIUS = 0.14, STRAND_RADIUS = 0.042, WRITHE = 0.025, TURNS = 2.4;

	private static final class State {
		float wrap, prevWrap, reel, prevReel;
		boolean holding;
		Vec3 lastEnd;
	}

	/** Keyed by player id * 2 + hand. */
	private static final Map<Integer, State> STATES = new HashMap<>();

	private WebEngulf() {
	}

	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATES.clear();
			return;
		}
		for (Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, State> entry = it.next();
			net.minecraft.world.entity.Entity entity = level.getEntity(entry.getKey() >> 1);
			if (!(entity instanceof Player player) || !com.jaymospowers.power.web.WebSymbiote.suitShown(player)) {
				it.remove();
			}
		}
		for (Player player : level.players()) {
			if (!com.jaymospowers.power.web.WebSymbiote.suitShown(player)) {
				continue;
			}
			for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
				int key = player.getId() * 2 + hand;
				Vec3 end = WebArms.lineEnd(minecraft, level, player, hand, 1.0F);
				State state = STATES.get(key);
				if (end != null) {
					if (state == null) {
						state = new State();
						STATES.put(key, state);
					}
					state.prevWrap = state.wrap;
					state.prevReel = state.reel;
					state.holding = true;
					state.lastEnd = end;
					state.wrap = Math.min(1.0F, state.wrap + 1.0F / WRAP_TICKS);
					state.reel = 1.0F;
				} else if (state != null) {
					state.prevWrap = state.wrap;
					state.prevReel = state.reel;
					state.holding = false;
					if (state.reel > 0.0F) {
						state.reel = Math.max(0.0F, state.reel - 1.0F / REEL_TICKS);
					} else {
						state.wrap = Math.max(0.0F, state.wrap - 1.0F / UNWRAP_TICKS);
					}
					if (state.wrap <= 0.0F && state.reel <= 0.0F) {
						STATES.remove(key);
					}
				}
			}
		}
	}

	/** How far the wrap has climbed the arm (0 none, 1 hand). */
	static float wrap(Player player, int hand, float partialTick) {
		State state = STATES.get(player.getId() * 2 + hand);
		return state == null ? 0.0F : Mth.lerp(partialTick, state.prevWrap, state.wrap);
	}

	/** A cord reeling back after release: the point it has reeled in to between the hand and where it was stuck, or null. */
	static Vec3 reelingEnd(Player player, int hand, float partialTick, Vec3 handPos) {
		State state = STATES.get(player.getId() * 2 + hand);
		if (state == null || state.holding || state.lastEnd == null) {
			return null;
		}
		float reel = Mth.lerp(partialTick, state.prevReel, state.reel);
		return reel <= 0.02F ? null : handPos.lerp(state.lastEnd, reel);
	}

	/** Strands out of the torso, wound down the whole arm to the hand, to the extent of the wrap. */
	static void render(LevelRenderContext context, Minecraft minecraft, Player player, int hand, float partialTick, Vec3 camera) {
		float wrap = wrap(player, hand, partialTick);
		if (wrap <= 0.01F) {
			return;
		}
		double scale = player.getScale();
		boolean big = com.jaymospowers.power.web.WebSymbiote.bulked(player);
		Vec3 shoulder = WebArms.shoulder(player, hand, partialTick);
		Vec3 handPos = WebLineRenderer.handPos(minecraft, player, hand, partialTick);
		Vec3 chest = player.getPosition(partialTick).add(0.0, 1.05 * scale, 0.0);
		Vec3 root = shoulder.lerp(chest, 0.8);
		Vec3 armDir = handPos.subtract(shoulder);
		if (armDir.lengthSqr() < 1.0E-4) {
			return;
		}
		armDir = armDir.normalize();
		Vec3 au = Math.abs(armDir.y) < 0.95 ? armDir.cross(new Vec3(0.0, 1.0, 0.0)).normalize() : armDir.cross(new Vec3(1.0, 0.0, 0.0)).normalize();
		Vec3 av = armDir.cross(au).normalize();
		// eased, so the tendrils pour out fast and settle
		double reach = Mth.clamp(1.0 - Math.pow(1.0 - wrap, 2.0), 0.0, 1.0) * PIECES;
		double time = player.tickCount + partialTick;
		double armR = (big ? 1.7 : 1.0) * scale;
		Vec3[][] strands = new Vec3[STRANDS][PIECES + 1];
		for (int k = 0; k < STRANDS; k++) {
			double phase = k * Math.PI * 2.0 / STRANDS + player.getId();
			for (int i = 0; i <= PIECES; i++) {
				double t = i / (double)PIECES;
				double writhe = Math.sin(time * 0.4 + i * 0.8 + k * 1.9) * WRITHE * scale;
				Vec3 p;
				if (t < BODY_SHARE) {
					double f = t / BODY_SHARE;
					double a = phase + f;
					double r = Mth.lerp(f, 0.04, ARM_RADIUS * armR) + writhe;
					p = root.lerp(shoulder, f).add(au.scale(Math.cos(a) * r)).add(av.scale(Math.sin(a) * r));
				} else {
					double f = (t - BODY_SHARE) / (1.0 - BODY_SHARE);
					double a = phase + 1.0 + f * TURNS * Math.PI * 2.0;
					double r = Mth.lerp(f, ARM_RADIUS, HAND_RADIUS) * armR + writhe;
					p = shoulder.lerp(handPos, f).add(au.scale(Math.cos(a) * r)).add(av.scale(Math.sin(a) * r));
				}
				strands[k][i] = p.subtract(camera);
			}
		}
		int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(player, partialTick);
		double radius = STRAND_RADIUS * scale * (big ? 1.5 : 1.0);
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(),
			RenderTypes.entityCutout(WebStrainLook.texture(WebLineRenderer.TENDRIL_TEXTURE, com.jaymospowers.power.web.WebSymbioteStrains.of(player))),
			(pose, buffer) -> {
				for (Vec3[] strand : strands) {
					for (int i = 0; i < PIECES && i < reach; i++) {
						Vec3 tip = i + 1 <= reach ? strand[i + 1] : strand[i].lerp(strand[i + 1], reach - i);
						double r0 = radius * (1.0 - 0.3 * i / PIECES), r1 = radius * (1.0 - 0.3 * (i + 1) / PIECES);
						WebLineRenderer.tube(buffer, pose, strand[i], tip, r0, r1, i, light);
					}
				}
			});
	}
}
