package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebMorph;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The weapon arm ({@link WebMorph}), drawn for everyone. The striking arm winds up its own way for each weapon (the
 * mace reared overhead, the scythe drawn out wide to its side, the spear cocked back), the hand swelling into the
 * weapon as it does, then whips through at the target while the arm stretches out as a tendril to reach it, and
 * draws back in. Mace: a spiked ball. Scythe: a long curved blade across the end, its edge pale. Spear: a long barbed
 * point. The arm itself is posed by {@link #applyModel}; this layer draws the cord and the weapon off the hand.
 */
public final class WebMorphLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** The strike under way: weapon, arm (1 right, -1 left), ticks in, the aim against the body (radians), and how far (blocks). */
	record Strike(int weapon, int side, float age, float aimYaw, float aimPitch, float reach) {
	}

	static final RenderStateDataKey<Strike> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_morph");

	/** The cord's thickness at the hand and at the weapon (model pixels), and how far it bows behind a swing (share of its length). */
	private static final float CORD_ROOT = 1.5F, CORD_TIP = 1.0F, CORD_LAG = 0.22F;
	/** The cord is drawn in this many pieces. */
	private static final int PIECES = 10;
	/** How far an arm with nothing to hit reaches (blocks). */
	private static final float AIR_REACH = 4.5F;
	/** The hand, in the arm's own space (pixels; the big body's arms are longer). */
	private static final float HAND_Y = 10.0F, HULK_HAND_Y = 17.0F;

	WebMorphLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Entity entity, AvatarRenderState state) {
		WebMorph.Morph morph = entity instanceof Player player ? player.getAttached(WebMorph.MORPH) : null;
		if (morph == null) {
			state.setData(KEY, null);
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float age = morph.age(entity.level().getGameTime() + partial);
		if (age < 0.0F || age >= WebMorph.TICKS) {
			state.setData(KEY, null);
			return;
		}
		Player player = (Player)entity;
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 shoulder = player.getPosition(partial).add(0.0, player.getBbHeight() * 0.78, 0.0);
		Entity target = morph.target() < 0 ? null : player.level().getEntity(morph.target());
		Vec3 to = target != null ? target.getBoundingBox().getCenter().subtract(shoulder)
			: player.getViewVector(partial).scale(AIR_REACH);
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float aimYaw = Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD;
		float aimPitch = (float)-Mth.atan2(to.y, to.horizontalDistance());
		float reach = (float)to.length() / Math.max(0.3F, player.getScale());
		state.setData(KEY, new Strike(morph.weapon(), morph.side(), age, aimYaw, aimPitch, reach));
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** How far through the swing (0 wound up, 1 at the target), and past it the follow-through. */
	private static float swing(float age) {
		return ease((age - WebMorph.FORM) / (WebMorph.HIT - WebMorph.FORM));
	}

	/** How much of the weapon has formed on the hand: swelling into it, then melting back at the end. */
	static float formed(float age) {
		return Math.min(ease(age / WebMorph.FORM), ease((WebMorph.TICKS - age) / 3.0F));
	}

	/** How far the cord is out (0-1 of the reach): flung out in the swing, held, drawn back in. */
	private static float out(float age) {
		if (age < WebMorph.FORM) {
			return 0.0F;
		}
		if (age < WebMorph.HIT) {
			float s = (age - WebMorph.FORM) / (WebMorph.HIT - WebMorph.FORM);
			return s * s;
		}
		return 1.0F - ease((age - WebMorph.HIT - 2.0F) / (WebMorph.TICKS - WebMorph.HIT - 3.0F));
	}

	/** The striking arm's angles now: {xRot, yRot, zRot}. */
	private static float[] armAngles(Strike s) {
		float aimX = -Mth.HALF_PI + s.aimPitch(), aimY = s.aimYaw();
		float sw = swing(s.age());
		float back = s.age() > WebMorph.HIT ? ease((s.age() - WebMorph.HIT - 2.0F) / 5.0F) : 0.0F;
		float x, y;
		switch (s.weapon()) {
			case WebMorph.MACE -> {
				// reared up over the head, then brought down through the target and on a little
				float wound = -2.95F;
				x = Mth.lerp(sw, wound, aimX + 0.35F);
				y = aimY * sw;
			}
			case WebMorph.SCYTHE -> {
				// drawn out wide to its side, then swept across through it and past
				x = aimX - 0.15F;
				y = Mth.lerp(sw, aimY + s.side() * 1.7F, aimY - s.side() * 0.6F);
			}
			default -> {
				// cocked back at the shoulder, then driven straight at it
				x = Mth.lerp(sw, 0.55F, aimX);
				y = aimY * sw;
			}
		}
		float wind = ease(s.age() / WebMorph.FORM);
		// from the arm's rest, into the wind-up, and back to rest at the end
		float on = wind * (1.0F - back);
		return new float[] {x * on, y * on, s.side() * 0.08F * on};
	}

	/** After the model animates: the striking arm swings its weapon. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Strike s = living.getData(KEY);
		if (s == null) {
			return;
		}
		ModelPart arm = s.side() > 0 ? h.rightArm : h.leftArm;
		float[] a = armAngles(s);
		float on = Math.min(ease(s.age() / 2.0F), ease((WebMorph.TICKS - s.age()) / 3.0F));
		arm.xRot = Mth.lerp(on, arm.xRot, a[0]);
		arm.yRot = Mth.lerp(on, arm.yRot, a[1]);
		arm.zRot = Mth.lerp(on, arm.zRot, a[2]);
		// the body turns into it a little
		h.body.yRot += s.side() * 0.25F * on * (swing(s.age()) - 0.5F);
		if (s.weapon() == WebMorph.MACE) {
			h.head.xRot = Mth.lerp(on * 0.5F, h.head.xRot, 0.3F * swing(s.age()));
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Strike s = state.getData(KEY);
		if (s == null || state.isInvisible) {
			return;
		}
		PlayerModel parent = this.getParentModel();
		WebSuitGrowth.shift(parent, state);
		ModelPart arm = s.side() > 0 ? parent.rightArm : parent.leftArm;
		boolean hulk = state.getData(WebSymbioteClient.TENDRILS) instanceof WebSymbioteClient.Tendrils t && t.armor();
		boolean slim = state.skin != null && state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
		float handX = -s.side() * (slim ? 0.5F : 1.0F);
		float handY = hulk ? HULK_HAND_Y : HAND_Y;
		float grow = formed(s.age());
		float length = Math.max(0.0F, s.reach() * 16.0F - handY - 2.0F) * out(s.age());
		float sw = swing(s.age());
		// a swing whips the cord out behind the hand (the scythe sideways, the mace and spear back along the arm's arc)
		float lag = (s.age() < WebMorph.HIT ? 1.0F - sw : 0.0F) * CORD_LAG * length;
		Vec3 bow = s.weapon() == WebMorph.SCYTHE ? new Vec3(s.side() * lag, 0.0, 0.0) : new Vec3(0.0, 0.0, s.weapon() == WebMorph.MACE ? -lag : 0.0);
		float time = state.ageInTicks;
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		arm.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebTubes.TEXTURE), (pose, buffer) -> {
			Vec3 hand = new Vec3(handX, handY, 0.0);
			Vec3 prev = hand;
			for (int i = 1; i <= PIECES; i++) {
				float f = i / (float)PIECES;
				// writhing a little along it
				double wx = Mth.sin(time * 1.3F + f * 6.0F) * 0.35 * f, wz = Mth.cos(time * 1.1F + f * 5.0F) * 0.35 * f;
				Vec3 p = hand.add(0.0, length * f, 0.0).add(bow.scale(Mth.sin(f * Mth.PI))).add(wx, 0.0, wz);
				double r0 = Mth.lerp(f - 1.0F / PIECES, CORD_ROOT, CORD_TIP) * Math.max(0.35F, grow);
				double r1 = Mth.lerp(f, CORD_ROOT, CORD_TIP) * Math.max(0.35F, grow);
				if (length > 0.5F) {
					WebTubes.cord(buffer, pose, px(prev), px(p), r0 / 16.0, r1 / 16.0, i % 3 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, state.lightCoords);
				}
				prev = p;
			}
			Vec3 dir = length > 0.5F ? prev.subtract(hand.add(bow.scale(Mth.sin(0.9F * Mth.PI)))).normalize() : new Vec3(0.0, 1.0, 0.0);
			weapon(buffer, pose, s, prev, dir, grow, state.lightCoords);
		});
		poseStack.popPose();
	}

	/** Pixels to blocks. */
	private static Vec3 px(Vec3 v) {
		return v.scale(1.0 / 16.0);
	}

	/** The weapon at the end of the cord, {@code at}, pointing along {@code dir} (pixels), {@code grow} of its size. */
	private static void weapon(VertexConsumer buffer, PoseStack.Pose pose, Strike s, Vec3 at, Vec3 dir, float grow, int light) {
		if (grow < 0.05F) {
			return;
		}
		// across it: in the arm's swing plane (the scythe's blade lies along its sweep)
		Vec3 across = Math.abs(dir.x) < 0.9 ? dir.cross(new Vec3(1.0, 0.0, 0.0)).normalize() : dir.cross(new Vec3(0.0, 0.0, 1.0)).normalize();
		Vec3 sideways = dir.cross(across).normalize();
		float g = grow;
		switch (s.weapon()) {
			case WebMorph.MACE -> {
				// a heavy ball, ringed with spikes, on a thickened neck
				Vec3 neck = at.add(dir.scale(2.0 * g));
				WebTubes.cord(buffer, pose, px(at), px(neck), 1.4 * g / 16.0, 2.2 * g / 16.0, WebTubes.BLACK, light);
				Vec3 c = neck.add(dir.scale(3.0 * g));
				WebTubes.prism(buffer, pose, px(neck), px(c.add(dir.scale(3.0 * g))), across, 3.6 * g / 16.0, 3.6 * g / 16.0, 3.6 * g / 16.0,
					3.6 * g / 16.0, WebTubes.SHEEN, light);
				// a second block turned 45°, for a rounder ball
				WebTubes.prism(buffer, pose, px(neck.add(dir.scale(0.6 * g))), px(c.add(dir.scale(2.4 * g))), across.add(sideways),
					3.9 * g / 16.0, 3.9 * g / 16.0, 3.9 * g / 16.0, 3.9 * g / 16.0, WebTubes.BLACK, light);
				Vec3[] ways = {across, across.reverse(), sideways, sideways.reverse(), dir, across.add(sideways), across.subtract(sideways),
					sideways.subtract(across), across.add(sideways).reverse(), dir.add(across), dir.subtract(across), dir.add(sideways), dir.subtract(sideways)};
				for (Vec3 way : ways) {
					Vec3 w = way.normalize();
					Vec3 root = c.add(w.scale(3.2 * g));
					WebTubes.cord(buffer, pose, px(root), px(root.add(w.scale(3.2 * g))), 0.9 * g / 16.0, 0.05 / 16.0, WebTubes.BONE, light);
				}
			}
			case WebMorph.SCYTHE -> {
				// a short haft, then a long blade out to one side, curving back toward you to a point; its inner edge pale
				Vec3 haft = at.add(dir.scale(3.0 * g));
				WebTubes.cord(buffer, pose, px(at), px(haft), 1.1 * g / 16.0, 1.4 * g / 16.0, WebTubes.BLACK, light);
				Vec3 out = across.scale(-s.side());
				int pieces = 7;
				Vec3 prev = haft;
				for (int i = 1; i <= pieces; i++) {
					double f = i / (double)pieces;
					// out along the blade, sweeping back down the haft as it goes
					Vec3 p = haft.add(out.scale(16.0 * g * f)).add(dir.scale(-7.0 * g * f * f));
					double w0 = (3.0 - 2.6 * (f - 1.0 / pieces)) * g, w1 = (3.0 - 2.6 * f) * g;
					// flat: wide toward the haft, thin across
					WebTubes.prism(buffer, pose, px(prev), px(p), dir, w0 / 16.0, 0.45 * g / 16.0, w1 / 16.0, 0.4 * g / 16.0, WebTubes.SHEEN, light);
					// the edge, along the inside of the curve
					Vec3 e0 = prev.add(dir.scale(-w0 * 0.85)), e1 = p.add(dir.scale(-w1 * 0.85));
					WebTubes.prism(buffer, pose, px(e0), px(e1), dir, 0.55 * g / 16.0, 0.3 * g / 16.0, 0.3 * g / 16.0, 0.2 * g / 16.0, WebTubes.BONE, light);
					prev = p;
				}
			}
			default -> {
				// a long leaf of a point, two barbs swept back from its base
				Vec3 base = at.add(dir.scale(2.0 * g));
				WebTubes.cord(buffer, pose, px(at), px(base), 1.0 * g / 16.0, 1.3 * g / 16.0, WebTubes.BLACK, light);
				Vec3 belly = base.add(dir.scale(4.0 * g));
				Vec3 tip = belly.add(dir.scale(10.0 * g));
				WebTubes.prism(buffer, pose, px(base), px(belly), across, 1.3 * g / 16.0, 1.1 * g / 16.0, 2.6 * g / 16.0, 1.2 * g / 16.0, WebTubes.SHEEN, light);
				WebTubes.prism(buffer, pose, px(belly), px(tip), across, 2.6 * g / 16.0, 1.2 * g / 16.0, 0.05 / 16.0, 0.05 / 16.0, WebTubes.BLACK, light);
				// a pale edge down each side of the point
				for (int side = -1; side <= 1; side += 2) {
					Vec3 e = across.scale(side * 2.2 * g);
					WebTubes.prism(buffer, pose, px(belly.add(e)), px(tip), across, 0.4 * g / 16.0, 0.4 * g / 16.0, 0.05 / 16.0, 0.05 / 16.0, WebTubes.BONE,
						light);
					Vec3 barb = base.add(across.scale(side * 1.2 * g));
					WebTubes.cord(buffer, pose, px(barb), px(barb.add(across.scale(side * 3.0 * g)).add(dir.scale(-3.0 * g))), 0.7 * g / 16.0, 0.05 / 16.0,
						WebTubes.BONE, light);
				}
			}
		}
	}

	/** True while a weapon arm is out (nothing else should take that arm). */
	static boolean striking(LivingEntityRenderState state) {
		return state.getData(KEY) != null;
	}
}
