package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebBehemoth;
import com.jaymospowers.power.web.WebCounter;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebTendrilCombos;
import com.jaymospowers.power.web.WebTendrils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The symbiote's tendrils as the PS5 Spider-Man 2 draws them (Andy's references): not one or two cords but a thick
 * bundle of many glossy strands, in the world, for everyone.
 * <ul>
 * <li><b>The tendril slam</b> (the symbiote's Spider-Sense counters, {@link WebCounter#bundle}): the bundle whips out
 * of the lead arm (a third of it from the back over the shoulder), splaying from the forearm, lashing as it flies; at
 * the target it coils round it; it bows through the haul's arc, trailing behind the thing it hauls; once it lets go it
 * recoils back into the arm, whipping. Spear Flurry braids it into one twisted point that stabs.</li>
 * <li><b>Tendril Grab and the combos</b>: whatever Living Armor's tendrils hold is sheathed in a bundle of strands
 * braided round the holding tendril (drawn by WebLineRenderer), coiled round what it holds; in a combo, more of them,
 * lashing.</li>
 * <li><b>The surge</b> (Spike Burst, and the roar's blast): long thick tendrils burst out of the whole body, whipping
 * outward, then dissolve into smoke ({@link #tick} puffs it off them).</li>
 * <li><b>Mobs the symbiote rides</b> ({@link WebBehemoth}): each lash is a quick bundle off the back; the tendril slam
 * the same as the counters'.</li>
 * </ul>
 */
final class WebTendrilBundle {
	// ---- the bundle ----
	/** Strands: a counter's bundle, a mob's slam, a held thing's sheath (and in a combo), a mob's lash. */
	static final int STRANDS = 14, MOB_SLAM_STRANDS = 12, GRAB_STRANDS = 6, COMBO_STRANDS = 12, LASH_STRANDS = 7;
	/** Each strand's radius at the root and at the tip (blocks; give or take {@code RADIUS_SPREAD}). */
	static final double RADIUS = 0.05, TIP_RADIUS = 0.016, RADIUS_SPREAD = 0.35;
	/** How far apart the strands run mid-way (blocks), and how far they splay at the root (the forearm). */
	static final double SPREAD = 0.2, ROOT_SPREAD = 0.55;
	/** Pieces per strand. */
	static final int PIECES = 16;
	/** The last share of each strand coils round the target: this many turns, this far off its box (blocks). */
	static final double WRAP_SHARE = 0.32, WRAP_TURNS = 1.7, WRAP_OUT = 0.07;
	/** Lashing: how far a strand whips sideways (blocks, at full lash), how fast, how many waves along it. */
	static final double LASH = 0.22, LASH_SPEED = 0.85, LASH_WAVES = 1.6;
	/** The haul: the bundle bows up through it by this share of its length, and trails the hauled thing by this much of its motion. */
	static final double HAUL_BOW = 0.35, HAUL_LAG = 2.5;
	/** Whipping out: it rises in a curve this share of its length before it lands. */
	static final double REACH_BOW = 0.4;
	/** Spear Flurry: the braid's twist (turns along it), how far it draws back between stabs (blocks), the stab's share around each hit. */
	static final double SPEAR_TWIST = 2.5, SPEAR_DRAW = 1.4;
	static final float SPEAR_STAB_IN = 2.0F, SPEAR_STAB_OUT = 3.0F;
	/** In your own first person the bundle comes from the lower corner of your view: this far out to the side, down, ahead. */
	static final double FP_SIDE = 0.45, FP_DOWN = 0.5, FP_AHEAD = 0.25;

	// ---- the surge ----
	/** Spike Burst's tendrils: how many, how long (share of the burst's radius, give or take), how thick at the root and tip. */
	static final int SURGE_TENDRILS = 18;
	static final double SURGE_LENGTH = 0.85, SURGE_LENGTH_SPREAD = 0.3, SURGE_RADIUS = 0.09, SURGE_TIP = 0.02;
	/** They shoot out over this many ticks, whip this hard (share of their length), and turn this far as they whip (radians). */
	static final float SURGE_SHOOT = 3.0F;
	static final double SURGE_WHIP = 0.22, SURGE_SWING = 0.5;
	/** From this many ticks after the burst they fall apart into smoke (by the end of {@link WebSymbiote#SPIKE_LASH_TICKS}). */
	static final float SURGE_DISSOLVE = 13.0F;
	/** Smoke puffed off each dissolving tendril a tick, and the embers shed at the burst. */
	static final int SURGE_SMOKE = 2, SURGE_EMBERS = 3;

	// ---- mobs ----
	/** A mob's back (where its tendrils root): this share of its height up, this far behind its middle (share of its width). */
	static final double MOB_BACK_HEIGHT = 0.74, MOB_BACK_DEPTH = 0.3;

	/** Entities with something of ours to draw (players and mobs), found once a tick. */
	private static final List<LivingEntity> DRAWN = new ArrayList<>();
	/** Where each counter's target last was (by the player's id), for the recoil once it's gone. */
	private static final Map<Integer, Vec3> LAST_TARGET = new HashMap<>();

	private WebTendrilBundle() {
	}

	static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(WebTendrilBundle::render);
		ClientTickEvents.END_CLIENT_TICK.register(WebTendrilBundle::tick);
	}

	// =================================================================================================================

	/** End of every client tick: who has a bundle to draw, and the smoke off dissolving surge tendrils. */
	private static void tick(Minecraft minecraft) {
		DRAWN.clear();
		ClientLevel level = minecraft.level;
		if (level == null) {
			LAST_TARGET.clear();
			return;
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && wanted(living)) {
				DRAWN.add(living);
			}
		}
		if (LAST_TARGET.size() > 64) {
			LAST_TARGET.keySet().removeIf(id -> level.getEntity(id) == null);
		}
		if (minecraft.isPaused()) {
			return;
		}
		// the surge's tendrils going to smoke, and the embers at the burst
		for (LivingEntity living : DRAWN) {
			if (!(living instanceof Player player)) {
				continue;
			}
			float age = surgeAge(player, 0.0F);
			if (age < 0.0F) {
				continue;
			}
			for (int i = 0; i < SURGE_TENDRILS; i++) {
				Vec3[] points = surgeTendril(player, i, age, 0.0F);
				if (points == null) {
					continue;
				}
				if (age >= SURGE_DISSOLVE) {
					for (int k = 0; k < SURGE_SMOKE; k++) {
						Vec3 p = points[level.getRandom().nextInt(points.length)];
						level.addParticle(k == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, p.x, p.y, p.z,
							(level.getRandom().nextDouble() - 0.5) * 0.04, 0.02 + level.getRandom().nextDouble() * 0.03, (level.getRandom().nextDouble() - 0.5) * 0.04);
					}
				} else if (age < SURGE_SHOOT + 2.0F && i % 3 == 0) {
					Vec3 tip = points[points.length - 1];
					for (int k = 0; k < SURGE_EMBERS; k++) {
						level.addParticle(ParticleTypes.SMALL_FLAME, tip.x, tip.y, tip.z, (level.getRandom().nextDouble() - 0.5) * 0.1,
							level.getRandom().nextDouble() * 0.08, (level.getRandom().nextDouble() - 0.5) * 0.1);
					}
				}
			}
		}
	}

	private static boolean wanted(LivingEntity living) {
		if (living instanceof Player player) {
			WebCounter.Active counter = player.getAttached(WebCounter.ACTIVE);
			WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
			return counter != null && WebCounter.bundle(counter.kind()) != null || grips != null && (grips.right() >= 0 || grips.left() >= 0)
				|| player.getAttached(WebPower.POSE) != null;
		}
		return living.hasAttached(WebBehemoth.SLAM) || living.hasAttached(WebBehemoth.STRIKE);
	}

	/** In the level pass: every bundle in view. */
	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || DRAWN.isEmpty()) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<Draw> draws = new ArrayList<>();
		for (LivingEntity living : DRAWN) {
			if (living.isRemoved() || living.level() != level || living.isInvisible()
				|| living.getPosition(partial).distanceToSqr(camera) > 80.0 * 80.0) {
				continue;
			}
			int light = minecraft.getEntityRenderDispatcher().getPackedLightCoords(living, partial);
			if (living instanceof Player player) {
				counter(draws, minecraft, level, player, partial, light);
				held(draws, minecraft, level, player, partial, light);
				surge(draws, player, partial, light);
			} else {
				mob(draws, level, living, partial, light);
			}
		}
		if (draws.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
			for (Draw draw : draws) {
				draw.run(buffer, pose, camera);
			}
		});
	}

	@FunctionalInterface
	private interface Draw {
		void run(VertexConsumer buffer, PoseStack.Pose pose, Vec3 camera);
	}

	// ---- the tendril slam (counters) --------------------------------------------------------------------------------

	private static void counter(List<Draw> draws, Minecraft minecraft, ClientLevel level, Player player, float partial, int light) {
		WebCounter.Active active = player.getAttached(WebCounter.ACTIVE);
		WebCounter.Bundle bundle = active == null ? null : WebCounter.bundle(active.kind());
		if (bundle == null) {
			return;
		}
		float t = level.getGameTime() - active.start() + partial;
		if (t < 0.0F || t >= WebCounter.ticks(active.kind())) {
			return;
		}
		Entity found = level.getEntity(active.target());
		Vec3 end;
		double width = 0.6, height = 1.8;
		Vec3 motion = Vec3.ZERO;
		if (found != null) {
			end = found.getPosition(partial).add(0.0, found.getBbHeight() * 0.5, 0.0);
			width = found.getBbWidth();
			height = found.getBbHeight();
			motion = new Vec3(found.getX() - found.xo, found.getY() - found.yo, found.getZ() - found.zo);
			LAST_TARGET.put(player.getId(), end);
		} else {
			end = LAST_TARGET.get(player.getId());
			if (end == null) {
				return;
			}
		}
		int side = (active.start() & 1L) == 0L ? 1 : -1;
		Vec3[] roots = armRoots(minecraft, player, partial, side, end);
		float reach = t < bundle.reached() ? ease(t / bundle.reached())
			: t < bundle.release() ? 1.0F : 1.0F - ease((t - bundle.release()) / WebCounter.RECOIL_TICKS);
		float wrap = bundle.spear() ? 0.0F : t < bundle.reached() ? 0.0F : t < bundle.wrapped()
			? ease((t - bundle.reached()) / Math.max(1.0F, bundle.wrapped() - bundle.reached()))
			: t < bundle.release() ? 1.0F : 1.0F - ease((t - bundle.release()) / (WebCounter.RECOIL_TICKS * 0.5F));
		// lashing while it flies and while it recoils; held still-ish while it hauls
		float lash = t < bundle.reached() ? 1.0F : t >= bundle.release() ? 1.4F : 0.35F;
		double bow = 0.0;
		Vec3 trail = Vec3.ZERO;
		if (t < bundle.reached()) {
			bow = REACH_BOW * (1.0 - t / bundle.reached());
		} else if (t >= bundle.wrapped() && t <= bundle.haulEnd() && bundle.haulEnd() > bundle.wrapped()) {
			double u = (t - bundle.wrapped()) / (bundle.haulEnd() - bundle.wrapped());
			bow = HAUL_BOW * Math.sin(u * Math.PI);
			trail = motion.scale(-HAUL_LAG);
		} else if (t >= bundle.release()) {
			bow = REACH_BOW * 0.6 * ease((t - bundle.release()) / WebCounter.RECOIL_TICKS);
		}
		if (bundle.spear()) {
			// drawn back between the stabs, driven into it at each
			double thrust = 0.0;
			for (int stab : WebCounter.FLURRY_STABS) {
				thrust = Math.max(thrust, t < stab ? ease(1.0F - (stab - t) / SPEAR_STAB_IN) : ease(1.0F - (t - stab) / SPEAR_STAB_OUT));
			}
			Vec3 from = roots[1];
			Vec3 dir = end.subtract(from);
			double len = dir.length();
			if (len > 1.0E-3) {
				end = from.add(dir.scale((len - SPEAR_DRAW * (1.0 - thrust) + 0.35 * width) / len));
			}
		}
		draws.add(bundleDraw(roots[0], roots[1], roots[2], end, width, height, bow, trail, STRANDS, 1.0, reach, wrap, lash,
			bundle.spear(), player.tickCount + partial, player.getId() * 7919L + active.start(), light));
	}

	/**
	 * Where a player's bundle roots: along the lead arm (shoulder to hand, reaching toward {@code at}) and on the back
	 * over that shoulder; from your own eyes, the lower corner of the view on that side. [0] the forearm's start, [1]
	 * the hand, [2] the back.
	 */
	private static Vec3[] armRoots(Minecraft minecraft, Player player, float partial, int side, Vec3 at) {
		boolean right = side > 0;
		if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
			Vec3 eye = player.getEyePosition(partial);
			Vec3 look = player.getViewVector(partial);
			float yaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
			Vec3 toSide = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw)).scale(right ? -FP_SIDE : FP_SIDE);
			Vec3 hand = eye.add(toSide).subtract(0.0, FP_DOWN, 0.0).add(look.scale(FP_AHEAD));
			return new Vec3[] {hand.add(toSide.scale(0.4)).subtract(look.scale(0.2)), hand, hand.add(toSide.scale(0.6)).add(0.0, 0.25, 0.0)};
		}
		float bend = WebFeral.bend(player, partial);
		// model x is to the wearer's left: the right shoulder is at -5
		Vec3 shoulder = WebFeral.bodyPoint(player, partial, bend, right ? -5.0F : 5.0F, 1.5F, 0.0F);
		Vec3 back = WebFeral.bodyPoint(player, partial, bend, right ? -3.0F : 3.0F, 1.5F, 2.0F);
		Vec3 toward = at.subtract(shoulder);
		toward = toward.lengthSqr() < 1.0E-4 ? player.getViewVector(partial) : toward.normalize();
		double arm = 0.62 * player.getScale();
		return new Vec3[] {shoulder.add(toward.scale(arm * 0.35)), shoulder.add(toward.scale(arm)), back};
	}

	// ---- Tendril Grab and the combos --------------------------------------------------------------------------------

	private static void held(List<Draw> draws, Minecraft minecraft, ClientLevel level, Player player, float partial, int light) {
		WebTendrils.Grips grips = player.getAttached(WebTendrils.GRIPS);
		if (grips == null) {
			return;
		}
		WebTendrils.Combo combo = player.getAttached(WebTendrils.COMBO);
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		float time = player.tickCount + partial;
		for (int hand = com.jaymospowers.power.web.WebNet.MAIN; hand <= com.jaymospowers.power.web.WebNet.OFF; hand++) {
			Entity held = grips.get(hand) < 0 ? null : level.getEntity(grips.get(hand));
			if (held == null) {
				continue;
			}
			boolean right = (hand == com.jaymospowers.power.web.WebNet.MAIN) == mainRight;
			// the same path as the holding tendril (WebLineRenderer.tentacle): up over the shoulder and down to it
			Vec3 to = held.getPosition(partial).add(0.0, held.getBbHeight() * 0.55, 0.0);
			float bend = WebFeral.bend(player, partial);
			Vec3 root = WebFeral.bodyPoint(player, partial, bend, right ? -3.0F : 3.0F, 1.5F, 2.0F);
			Vec3 over = WebFeral.bodyPoint(player, partial, bend, right ? -7.0F : 7.0F, -9.0F, 0.0F);
			if (player == minecraft.player && minecraft.options.getCameraType().isFirstPerson()) {
				float yaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
				Vec3 toSide = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw)).scale(right ? -0.55 : 0.55);
				over = player.getEyePosition(partial).add(0.0, 0.3, 0.0).add(toSide);
				root = over.subtract(0.0, 0.6, 0.0).add(toSide.scale(0.3));
			}
			double path = root.distanceTo(over) + over.distanceTo(to);
			float reach = WebGrowth.fraction(((long)player.getId() << 24) ^ ((long)held.getId() << 2) ^ hand ^ 0x2000_0000_0000_0000L, to, path,
				WebTendrils.TENDRIL_SPEED * path / Math.max(0.5, root.distanceTo(to)), true);
			boolean inCombo = combo != null && (combo.first() == held.getId() || combo.second() == held.getId());
			float wrap = ease((reach - 0.8F) / 0.2F);
			// in a combo it heaves and lashes; holding, it only writhes
			float lash = inCombo ? (combo.kind() == WebTendrilCombos.BARRAGE ? 0.5F : 1.1F) : 0.3F;
			Vec3 r = root, o = over, end = to;
			DoubleFunction<Vec3> spine = s -> r.lerp(o, s).lerp(o.lerp(end, s), s);
			draws.add(strands(spine, r, r, r, end, held.getBbWidth(), held.getBbHeight(), inCombo ? COMBO_STRANDS : GRAB_STRANDS, 0.8, reach, wrap, lash,
				false, time, player.getId() * 31L + held.getId(), light, 0.0));
		}
	}

	// ---- the surge (Spike Burst, the roar) --------------------------------------------------------------------------

	/** Ticks since the surge burst out of this player (partial ticks in), or -1 when it isn't out. */
	private static float surgeAge(Player player, float partial) {
		WebPose pose = player.getAttached(WebPower.POSE);
		if (pose == null || !WebSymbiote.isBonded(player)) {
			return -1.0F;
		}
		float time = player.level().getGameTime() + partial - pose.start();
		float age = switch (pose.kind()) {
			case WebPose.SPIKES -> time - WebSymbiote.SPIKE_GATHER_TICKS;
			case WebPose.ROAR -> time - WebSymbiote.ROAR_BLAST_AT;
			default -> -1.0F;
		};
		return age >= 0.0F && age < WebSymbiote.SPIKE_LASH_TICKS ? age : -1.0F;
	}

	private static void surge(List<Draw> draws, Player player, float partial, int light) {
		float age = surgeAge(player, partial);
		if (age < 0.0F) {
			return;
		}
		List<Vec3[]> tendrils = new ArrayList<>();
		for (int i = 0; i < SURGE_TENDRILS; i++) {
			Vec3[] points = surgeTendril(player, i, age, partial);
			if (points != null) {
				tendrils.add(points);
			}
		}
		Minecraft minecraft = Minecraft.getInstance();
		boolean own = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
		Vec3 eye = player.getEyePosition(partial);
		float dissolve = ease((age - SURGE_DISSOLVE) / (WebSymbiote.SPIKE_LASH_TICKS - SURGE_DISSOLVE));
		double scale = player.getScale();
		long seed = player.getId() * 104729L;
		draws.add((buffer, pose, camera) -> {
			for (int i = 0; i < tendrils.size(); i++) {
				Vec3[] points = tendrils.get(i);
				for (int k = 0; k + 1 < points.length; k++) {
					double s = k / (double)(points.length - 1);
					// from your own eyes, not the first stretch (it would fill the view)
					if (own && points[k].distanceToSqr(eye) < 0.6 * 0.6) {
						continue;
					}
					// falling apart: pieces drop out, more and more, the rest thinning
					if (dissolve > 0.0F && hash(seed + i * 131L + k * 17L) < dissolve * 1.1) {
						continue;
					}
					double r0 = Mth.lerp(s, SURGE_RADIUS, SURGE_TIP) * scale * (1.0 - 0.7 * dissolve);
					double r1 = Mth.lerp((k + 1) / (double)(points.length - 1), SURGE_RADIUS, SURGE_TIP) * scale * (1.0 - 0.7 * dissolve);
					WebTubes.cord(buffer, pose, points[k].subtract(camera), points[k + 1].subtract(camera), r0, r1, (k + i) % 3 == 0 ? WebTubes.SHEEN : WebTubes.BLACK,
						light);
				}
			}
		});
	}

	/**
	 * One surge tendril (world space), or null: rooted somewhere on the body, flung straight out from its middle
	 * (scattered), shooting out to its length over {@link #SURGE_SHOOT}, whipping as it goes.
	 */
	private static Vec3 @Nullable [] surgeTendril(Player player, int i, float age, float partial) {
		long seed = player.getId() * 7349L + i * 977L;
		float bend = WebFeral.bend(player, partial);
		// a spot on the torso or the arms, front or back
		float x = (float)(hash(seed) * 8.0 - 4.0) * (i % 4 == 0 ? 2.0F : 1.0F);
		float y = (float)(hash(seed + 1) * 11.0);
		float z = hash(seed + 2) < 0.5 ? -2.0F : 2.0F;
		Vec3 root = WebFeral.bodyPoint(player, partial, bend, x, y, z);
		Vec3 middle = WebFeral.bodyPoint(player, partial, bend, 0.0F, 6.0F, 0.0F);
		Vec3 out = root.subtract(middle);
		Vec3 scatter = new Vec3(hash(seed + 3) - 0.5, hash(seed + 4) - 0.35, hash(seed + 5) - 0.5).scale(1.4);
		out = out.lengthSqr() < 1.0E-6 ? scatter : out.normalize().add(scatter);
		if (out.lengthSqr() < 1.0E-6) {
			return null;
		}
		out = out.normalize();
		// it whips: the whole tendril swings about (round the vertical), and waves run out along it
		double swing = Math.sin(age * 0.55 + i * 1.7) * SURGE_SWING * Math.min(1.0, age / SURGE_SHOOT);
		double cos = Math.cos(swing), sin = Math.sin(swing);
		out = new Vec3(out.x * cos - out.z * sin, out.y, out.x * sin + out.z * cos);
		double length = WebSymbiote.SPIKE_RADIUS * SURGE_LENGTH * (1.0 + (hash(seed + 6) - 0.5) * 2.0 * SURGE_LENGTH_SPREAD) * player.getScale();
		float grown = age < SURGE_SHOOT ? 1.0F - (1.0F - age / SURGE_SHOOT) * (1.0F - age / SURGE_SHOOT) : 1.0F;
		Vec3 across = out.cross(new Vec3(0.0, 1.0, 0.0));
		across = across.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : across.normalize();
		Vec3 up = across.cross(out).normalize();
		int pieces = 12;
		Vec3[] points = new Vec3[pieces + 1];
		for (int k = 0; k <= pieces; k++) {
			double s = k / (double)pieces;
			double wave = Math.sin(age * 1.1 + i * 2.1 - s * 7.0) * SURGE_WHIP * length * s;
			double wave2 = Math.cos(age * 0.9 + i * 1.3 - s * 5.0) * SURGE_WHIP * 0.6 * length * s;
			// a little droop toward the tips once they're out
			double droop = -0.15 * length * s * s * Math.min(1.0, age / 8.0);
			points[k] = root.add(out.scale(length * grown * s)).add(across.scale(wave)).add(up.scale(wave2)).add(0.0, droop, 0.0);
		}
		return points;
	}

	// ---- mobs the symbiote rides ------------------------------------------------------------------------------------

	private static void mob(List<Draw> draws, ClientLevel level, LivingEntity host, float partial, int light) {
		float yaw = Mth.lerp(partial, host.yBodyRotO, host.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 back = host.getPosition(partial).add(0.0, host.getBbHeight() * MOB_BACK_HEIGHT, 0.0).subtract(forward.scale(host.getBbWidth() * MOB_BACK_DEPTH));
		float time = host.tickCount + partial;
		double scale = Mth.clamp(host.getBbHeight() / 1.8, 0.6, 1.8);
		// each lash: a quick bundle off the back that strikes and draws back
		for (WebBehemoth.Strike strike : host.getAttachedOrElse(WebBehemoth.STRIKE, List.<WebBehemoth.Strike>of())) {
			Entity target = level.getEntity(strike.target());
			if (target == null) {
				continue;
			}
			float t = level.getGameTime() - strike.start() + partial;
			if (t < 0.0F || t >= WebBehemoth.STRIKE_TICKS) {
				continue;
			}
			float hit = WebBehemoth.STRIKE_HIT;
			float reach = t < hit ? ease(t / hit) : 1.0F - ease((t - hit) / (WebBehemoth.STRIKE_TICKS - hit));
			double side = (strike.tendril() % 2 == 0 ? 1.0 : -1.0) * (0.15 + 0.1 * (strike.tendril() / 2)) * host.getBbWidth();
			Vec3 root = back.add(right.scale(side));
			Vec3 end = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0);
			draws.add(bundleDraw(root.subtract(forward.scale(0.1)), root, root, end, target.getBbWidth(), target.getBbHeight(), REACH_BOW * (1.0 - reach * 0.5),
				Vec3.ZERO, LASH_STRANDS, scale * 0.8, reach, 0.0F, 1.0F, true, time, host.getId() * 13L + strike.tendril() * 101L + strike.start(), light));
		}
		WebBehemoth.Slam slam = host.getAttached(WebBehemoth.SLAM);
		Entity target = slam == null ? null : level.getEntity(slam.target());
		if (slam == null || target == null) {
			return;
		}
		float t = level.getGameTime() - slam.start() + partial;
		if (t < 0.0F || t >= WebBehemoth.SLAM_TICKS) {
			return;
		}
		float reach = t < WebBehemoth.SLAM_REACHED ? ease(t / WebBehemoth.SLAM_REACHED)
			: t < WebBehemoth.SLAM_RELEASE ? 1.0F : 1.0F - ease((t - WebBehemoth.SLAM_RELEASE) / (WebBehemoth.SLAM_TICKS - WebBehemoth.SLAM_RELEASE));
		float wrap = t < WebBehemoth.SLAM_REACHED ? 0.0F : t < WebBehemoth.SLAM_WRAPPED
			? ease((t - WebBehemoth.SLAM_REACHED) / (WebBehemoth.SLAM_WRAPPED - WebBehemoth.SLAM_REACHED))
			: t < WebBehemoth.SLAM_RELEASE ? 1.0F : 1.0F - ease((t - WebBehemoth.SLAM_RELEASE) / 3.0F);
		double bow = t < WebBehemoth.SLAM_REACHED ? REACH_BOW : t < WebBehemoth.SLAM_HIT
			? HAUL_BOW * Math.sin((t - WebBehemoth.SLAM_WRAPPED) / (WebBehemoth.SLAM_HIT - WebBehemoth.SLAM_WRAPPED) * Math.PI) : REACH_BOW * 0.5;
		Vec3 motion = new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
		Vec3 end = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0);
		draws.add(bundleDraw(back.add(right.scale(0.3 * host.getBbWidth())), back.add(0.0, 0.1, 0.0), back.subtract(right.scale(0.3 * host.getBbWidth())),
			end, target.getBbWidth(), target.getBbHeight(), Math.max(0.0, bow), motion.scale(-HAUL_LAG), MOB_SLAM_STRANDS, scale,
			reach, wrap, t < WebBehemoth.SLAM_REACHED || t >= WebBehemoth.SLAM_RELEASE ? 1.2F : 0.35F, false, time,
			host.getId() * 17L + slam.start(), light));
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	/**
	 * A bundle from its roots ({@code rootA} to {@code rootB} along the arm, and {@code back}) to {@code end}: the spine
	 * a curve from the hand, bowed up by {@code bow} of its length, its far end trailing by {@code trail}.
	 */
	private static Draw bundleDraw(Vec3 rootA, Vec3 rootB, Vec3 back, Vec3 end, double width, double height, double bow, Vec3 trail, int count,
		double size, float reach, float wrap, float lash, boolean spear, float time, long seed, int light) {
		double length = rootB.distanceTo(end);
		Vec3 lift = new Vec3(0.0, length * bow, 0.0);
		Vec3 p1 = rootB.lerp(end, 0.33).add(lift);
		Vec3 p2 = rootB.lerp(end, 0.7).add(lift.scale(0.6)).add(trail);
		DoubleFunction<Vec3> spine = s -> {
			double a = 1.0 - s;
			return rootB.scale(a * a * a).add(p1.scale(3.0 * a * a * s)).add(p2.scale(3.0 * a * s * s)).add(end.scale(s * s * s));
		};
		return strands(spine, rootA, rootB, back, end, width, height, count, size, reach, wrap, lash, spear, time, seed, light, ROOT_SPREAD);
	}

	/**
	 * The strands of a bundle along {@code spine} (s 0-1, from the root end to {@code end}): each from its own root
	 * (spread along the forearm, a third from the back), gathering into the bundle, running {@code SPREAD} apart, as far
	 * out as {@code reach}; lashing ({@code lash}); the last {@link #WRAP_SHARE} coiled round the target's box
	 * ({@code width} x {@code height} about {@code end}) as far as {@code wrap}; or, for a spear, braided into a point.
	 */
	private static Draw strands(DoubleFunction<Vec3> spine, Vec3 rootA, Vec3 rootB, Vec3 back, Vec3 end, double width, double height, int count,
		double size, float reach, float wrap, float lash, boolean spear, float time, long seed, int light, double rootSpread) {
		if (reach <= 0.01F) {
			return (buffer, pose, camera) -> {
			};
		}
		List<Vec3[]> lines = new ArrayList<>(count);
		double[] radii = new double[count];
		for (int i = 0; i < count; i++) {
			long h = seed * 31L + i * 7919L;
			double angle = i * 2.399963 + hash(h) * 0.5;
			double spread = SPREAD * (0.45 + 0.55 * hash(h + 1)) * size;
			double phase = hash(h + 2) * Math.PI * 2.0;
			double speed = LASH_SPEED * (0.8 + 0.4 * hash(h + 3));
			Vec3 root = i % 3 == 2 ? back : rootA.lerp(rootB, hash(h + 4));
			// each strand's own reach: they don't all land at once
			double own = Mth.clamp(reach * (1.12 - 0.12 * hash(h + 5)), 0.0, 1.0);
			int pieces = Math.max(1, (int)Math.ceil(PIECES * own));
			Vec3[] points = new Vec3[pieces + 1];
			double coilR = width * 0.5 + WRAP_OUT;
			for (int k = 0; k <= pieces; k++) {
				double s = Math.min(own, k / (double)PIECES);
				Vec3 c = spine.apply(s);
				Vec3 tangent = spine.apply(Math.min(1.0, s + 0.02)).subtract(spine.apply(Math.max(0.0, s - 0.02)));
				tangent = tangent.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : tangent.normalize();
				Vec3 n = tangent.cross(new Vec3(0.0, 1.0, 0.0));
				n = n.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : n.normalize();
				Vec3 b = tangent.cross(n).normalize();
				double a = spear ? angle + s * SPEAR_TWIST * Math.PI * 2.0 : angle;
				// fat in the middle, gathered at the root; a spear narrows to its point
				double r = spread * Math.pow(Math.sin(Math.PI * Math.min(1.0, s * 0.85 + 0.15)), 0.6) * (spear ? 1.0 - 0.85 * s : 1.0);
				Vec3 p = c.add(n.scale(Math.cos(a) * r)).add(b.scale(Math.sin(a) * r));
				// splayed from its own root, gathering into the bundle
				double gather = ease((float)(s / 0.22));
				if (rootSpread > 0.0) {
					p = p.add(root.subtract(spine.apply(0.0)).scale(1.0 - gather));
				}
				// lashing: waves running out along it, more toward the tip
				double wave = Math.sin(time * speed - s * LASH_WAVES * Math.PI * 2.0 + phase) * LASH * lash * s * size;
				double wave2 = Math.cos(time * speed * 1.3 - s * LASH_WAVES * 4.0 + phase * 1.7) * LASH * 0.6 * lash * s * size;
				p = p.add(n.scale(Math.cos(a + 1.57) * wave)).add(b.scale(Math.sin(a + 1.57) * wave)).add(0.0, wave2, 0.0);
				// the end coils round what it has hold of
				double from = 1.0 - WRAP_SHARE;
				if (wrap > 0.0F && s > from) {
					double u = (s - from) / WRAP_SHARE;
					double phi = angle + u * WRAP_TURNS * Math.PI * 2.0 + (i % 2 == 0 ? 0.0 : Math.PI);
					double y = height * (0.38 - 0.76 * ((u + hash(h + 6) * 0.3) % 1.0));
					double out = coilR * (1.0 + 0.08 * Math.sin(time * 0.7 + i));
					Vec3 coil = end.add(Math.cos(phi) * out, y, Math.sin(phi) * out);
					p = p.lerp(coil, wrap * ease((float)(u * 2.5)));
				}
				points[k] = p;
			}
			lines.add(points);
			radii[i] = RADIUS * size * (1.0 + (hash(h + 7) - 0.5) * 2.0 * RADIUS_SPREAD);
		}
		return (buffer, pose, camera) -> {
			for (int i = 0; i < lines.size(); i++) {
				Vec3[] points = lines.get(i);
				int swatch = i % 2 == 0 ? WebTubes.SHEEN : WebTubes.BLACK;
				for (int k = 0; k + 1 < points.length; k++) {
					double s0 = k / (double)PIECES, s1 = (k + 1) / (double)PIECES;
					double r0 = Mth.lerp(s0, radii[i], TIP_RADIUS * size), r1 = Mth.lerp(s1, radii[i], TIP_RADIUS * size);
					if (k + 2 == points.length) {
						// the growing tip comes to a point
						r1 *= 0.5;
					}
					WebTubes.cord(buffer, pose, points[k].subtract(camera), points[k + 1].subtract(camera), r0, r1, (k / 3) % 2 == 0 ? swatch : WebTubes.SHEEN, light);
				}
			}
		};
	}

	/** A steady random number 0-1 for {@code seed}. */
	private static double hash(long seed) {
		long z = seed * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
