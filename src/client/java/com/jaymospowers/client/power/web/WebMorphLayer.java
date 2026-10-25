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
 * draws back in. Before it, held, the gathering: lumps course over the suit to the shoulder and down the arm, the
 * forearm swells and the weapon grows out of the hand ({@link #submitGather}). Mace: a spiked ball. Scythe: a long curved blade across the end, its edge pale. Spear: a long barbed
 * point. Whip-blade: a lash of barbed links ending in a thin blade. Hammer-fist: a great knuckled block of a club. Hook
 * claw: three curved bone hooks, a grapple. The arm itself is posed by {@link #applyModel}; this layer draws the cord and
 * the weapon off the hand. On a shoulder tendril ({@code tendril}: the tendrils out, no armour) the arm is left alone:
 * {@link WebTendrilLayer} poses the tendril and draws the same heads ({@link #weapon}) at its tip.
 *
 * <p>(0.16.7) With Living Armor on ({@code armor}: the thin armour or the big suit) the arm is engulfed
 * ({@link #engulfedArm}): once the lumps reach the shoulder a tendril rears out of it and winds down the arm in coils
 * ({@link #ARMOR_TURNS} turns, over the collecting ticks), the coils thicken and fuse into one lumpy mass over the whole
 * arm ({@link #ARMOR_SLEEVE}) as the weapon forms, and the weapon swells out of the hand at {@link #WEAPON_SCALE_ARMOR}
 * times the plain suit's size. The strike swings the engulfed arm, on a cord {@link #ARMOR_CORD} times as thick, and the
 * mass melts back with the weapon at the end (Andy: "much larger ... so that tendril engulfs the entire arm").
 */
public final class WebMorphLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/**
	 * The strike under way: weapon, arm (1 right, -1 left), ticks in, the aim against the body (radians), how far
	 * (blocks), whether it's on a shoulder tendril, and whether it's Living Armor's engulfed arm.
	 */
	record Strike(int weapon, int side, float age, float aimYaw, float aimPitch, float reach, boolean tendril, boolean armor) {
		/** Not the engulfed arm (the shoulder tendril's heads, {@link WebTendrilLayer}). */
		Strike(int weapon, int side, float age, float aimYaw, float aimPitch, float reach, boolean tendril) {
			this(weapon, side, age, aimYaw, aimPitch, reach, tendril, false);
		}
	}

	static final RenderStateDataKey<Strike> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_morph");

	/** The gathering before it, held ({@link WebMorph.Charge}): weapon, arm, ticks in, on a shoulder tendril, Living Armor's engulfed arm. */
	record Gather(int weapon, int side, float age, boolean tendril, boolean armor) {
	}

	static final RenderStateDataKey<Gather> GATHER = RenderStateDataKey.create(() -> "jaymospowers:web_morph_gather");

	/**
	 * The lumps coursing over the body to the striking shoulder (body space, pixels; x given for the right arm, mirrored
	 * for the left; z on the front or back of the suit): from, to, and how many ticks after the press each sets off.
	 */
	private static final float[][] COURSES = {
		{3.0F, 11.5F, -2.1F, -3.5F, 1.0F, -2.1F, 0.0F},
		{-1.0F, 12.0F, -2.1F, -3.4F, 1.6F, -2.1F, 1.0F},
		{2.5F, 11.5F, 2.1F, -3.5F, 1.0F, 2.1F, 0.4F},
		{-2.5F, 12.0F, 2.1F, -3.7F, 1.5F, 2.1F, 1.6F},
		{3.7F, 1.0F, -2.1F, -3.4F, 0.8F, -2.1F, 2.0F},
		{3.7F, 1.0F, 2.1F, -3.4F, 0.8F, 2.1F, 2.6F},
	};
	/** A lump crosses the body in this many ticks; its size (radius, pixels) and length (share of its course). */
	private static final float COURSE_TRAVEL = 5.0F, LUMP = 1.25F, LUMP_LENGTH = 0.12F;
	/** The vein a lump leaves behind it (radius, pixels), shrinking away over this many ticks after it arrives. */
	private static final float VEIN = 0.4F, VEIN_FADE = 4.0F;
	/** Down the arm: three lumps, this far apart (ticks), each taking this long; then the forearm swells this fat (radius, pixels). */
	private static final float ARM_GAP = 0.8F, ARM_TRAVEL = 3.5F, SWELL = 2.7F;

	/** The cord's thickness at the hand and at the weapon (model pixels), and how far it bows behind a swing (share of its length). */
	private static final float CORD_ROOT = 1.5F, CORD_TIP = 1.0F, CORD_LAG = 0.22F;
	/** The cord is drawn in this many pieces. */
	private static final int PIECES = 10;
	/** How far an arm with nothing to hit reaches (blocks; the whip-blade and the hook claw this share of their reach). */
	private static final float AIR_REACH = 4.5F, AIR_REACH_LONG = 0.75F;
	/** The whip-blade's lash: how much further its cord bows behind the swing, and the wave rolling down it (pixels). */
	private static final float WHIP_LAG = 1.8F, WHIP_WAVE = 0.9F;
	/** The hook claw: the arm yanks back over this many ticks after it bites. */
	private static final float HOOK_REEL_TICKS = 4.0F;
	/** The whip-blade's links, and the hook claw's prongs. */
	private static final int WHIP_LINKS = 6, HOOK_PRONGS = 3;
	/** The hand, in the arm's own space (pixels; the big body's arms are longer). */
	private static final float HAND_Y = 10.0F, HULK_HAND_Y = 17.0F;
	/** The arm's own half-width (pixels; wide, slim), and how much wider the big body's is. */
	private static final float ARM_HALF = 2.0F, ARM_HALF_SLIM = 1.5F, HULK_ARM = 1.5F;

	// ---- (0.16.7) Living Armor's engulfed arm ----
	/** The weapon's size on the engulfed arm, against the plain suit's (every head scales with it). */
	static final float WEAPON_SCALE_ARMOR = 1.8F;
	/** The cord out to the target, against the plain suit's thickness. */
	private static final float ARMOR_CORD = 1.8F;
	/** The tendril winding down the arm: its radius as it winds and once fused (pixels), its turns shoulder to hand, its pieces. */
	private static final float ARMOR_TENDRIL = 1.3F, ARMOR_TENDRIL_FULL = 2.3F, ARMOR_TURNS = 3.5F;
	private static final int ARMOR_PIECES = 28;
	/** The winding tendril's leading end: fatter than the rest by this much (pixels). */
	private static final float ARMOR_HEAD = 0.9F;
	/** The fused mass over the arm: its radius past the arm's own (pixels), how much it ripples, and its pieces. */
	private static final float ARMOR_SLEEVE = 2.2F, ARMOR_RIPPLE = 0.14F;
	private static final int ARMOR_SLEEVE_PIECES = 6;
	/** Where the tendril rears out: this far above the shoulder's top, and this far back (pixels). */
	private static final float ARMOR_ROOT_UP = 1.5F, ARMOR_ROOT_BACK = 2.0F;
	/**
	 * Your own engulfed arm seen from your own eyes (First-person Model drawing your body): the heads this big instead
	 * (the plain suit's is 1), the tendril and mass this share of their size, and while it gathers the arm hangs this
	 * much further back and down (radians) and this much wider, so the mass sits low and to the side of the view, not
	 * across the crosshair (Andy's playtest: the formed arm already filled half the screen).
	 */
	private static final float WEAPON_SCALE_ARMOR_FP = 1.15F, ARMOR_MASS_FP = 0.55F, ARMOR_FP_BACK = 0.45F, ARMOR_FP_WIDE = 0.3F;

	WebMorphLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Entity entity, AvatarRenderState state) {
		WebMorph.Charge charge = entity instanceof Player p ? p.getAttached(WebMorph.CHARGE) : null;
		if (charge != null) {
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			state.setData(GATHER, new Gather(charge.weapon(), charge.side(), Math.max(0.0F, charge.age(entity.level().getGameTime() + partial)),
				charge.tendril(), charge.armor()));
		} else {
			state.setData(GATHER, null);
		}
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
		float air = morph.weapon() == WebMorph.WHIP || morph.weapon() == WebMorph.HOOK ? (float)WebMorph.reach(morph.weapon(), morph.armor()) * AIR_REACH_LONG
			: AIR_REACH + (morph.armor() ? (float)WebMorph.REACH_ARMOR : 0.0F);
		Vec3 to = target != null ? target.getBoundingBox().getCenter().subtract(shoulder)
			: player.getViewVector(partial).scale(air);
		float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
		float aimYaw = Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD;
		float aimPitch = (float)-Mth.atan2(to.y, to.horizontalDistance());
		float reach = (float)to.length() / Math.max(0.3F, player.getScale());
		state.setData(KEY, new Strike(morph.weapon(), morph.side(), age, aimYaw, aimPitch, reach, morph.tendril(), morph.armor()));
	}

	/**
	 * The weapon's size on this arm: the engulfed arm's heads are {@link #WEAPON_SCALE_ARMOR} times the plain suit's
	 * (only {@link #WEAPON_SCALE_ARMOR_FP} in your own first-person view, {@code fp}).
	 */
	private static float headScale(boolean armor, boolean fp) {
		return armor ? fp ? WEAPON_SCALE_ARMOR_FP : WEAPON_SCALE_ARMOR : 1.0F;
	}

	/** {@code state} is your own body, drawn in first person by First-person Model: what's on the arm fills the view. */
	private static boolean ownFirstPerson(LivingEntityRenderState state) {
		net.minecraft.client.player.LocalPlayer self = Minecraft.getInstance().player;
		return self != null && state instanceof AvatarRenderState avatar && avatar.id == self.getId() && FirstPersonCompat.bodyShown();
	}

	/** Gathering, the engulfed arm: how far down the arm the tendril has wound (0 at the shoulder, 1 at the hand). */
	private static float wound(Gather g) {
		return ease((g.age() - WebMorph.COURSE_TICKS) / (WebMorph.COLLECT_TICKS - WebMorph.COURSE_TICKS));
	}

	/** Gathering, the engulfed arm: how far the coils have thickened and fused into one mass (0-1). */
	private static float engulfed(Gather g) {
		return ease((g.age() - WebMorph.COLLECT_TICKS + 1.0F) / (WebMorph.FORMED_TICKS - WebMorph.COLLECT_TICKS + 1.0F));
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** How far through the swing (0 wound up, 1 at the target), and past it the follow-through. */
	private static float swing(float age) {
		return ease((age - WebMorph.FORM) / (WebMorph.HIT - WebMorph.FORM));
	}

	/** How much of the weapon is on the hand: whole from the gathering, melting back at the end. */
	static float formed(float age) {
		return ease((WebMorph.TICKS - age) / 3.0F);
	}

	/** Gathering: how much of the weapon has grown out of the hand (or the tendril's tip; it pulses a little once whole, held). */
	static float grown(Gather g) {
		float f = ease((g.age() - WebMorph.COLLECT_TICKS) / (WebMorph.FORMED_TICKS - WebMorph.COLLECT_TICKS));
		return g.age() < WebMorph.FORMED_TICKS ? f : 1.0F + 0.04F * Mth.sin(g.age() * 0.5F);
	}

	/** Gathering: how swollen the forearm is (filling as the lumps arrive, then going into the weapon). */
	private static float swollen(Gather g) {
		float fill = ease((g.age() - WebMorph.COURSE_TICKS + 1.0F) / (WebMorph.COLLECT_TICKS - WebMorph.COURSE_TICKS + 1.0F));
		return fill * (1.0F - 0.65F * Math.min(1.0F, grown(g)));
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
			case WebMorph.MACE, WebMorph.AXE -> {
				// reared up over the head, then brought down through the target and on a little (the axe chops the same way)
				float wound = -2.95F;
				x = Mth.lerp(sw, wound, aimX + 0.35F);
				y = aimY * sw;
			}
			case WebMorph.SCYTHE -> {
				// drawn out wide to its side, then swept across through it and past
				x = aimX - 0.15F;
				y = Mth.lerp(sw, aimY + s.side() * 1.7F, aimY - s.side() * 0.6F);
			}
			case WebMorph.WHIP -> {
				// drawn back low behind, then brought up over the shoulder and cracked out at it
				x = Mth.lerp(sw, 0.8F, aimX + 0.15F) - Mth.sin(sw * Mth.PI) * 1.6F;
				y = Mth.lerp(sw, aimY + s.side() * 0.9F, aimY);
			}
			case WebMorph.HAMMER -> {
				// dropped low and back, then an uppercut up through it
				x = Mth.lerp(sw, 0.7F, aimX - 0.3F);
				y = aimY * sw;
			}
			case WebMorph.HOOK -> {
				// raised high, flung out at it, then yanked back as it bites
				x = Mth.lerp(sw, -2.2F, aimX);
				y = aimY * sw + s.side() * 0.2F * (1.0F - sw);
				if (s.age() > WebMorph.HIT) {
					x = Mth.lerp(ease((s.age() - WebMorph.HIT) / HOOK_REEL_TICKS), aimX, 0.4F);
				}
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
		Gather g = living.getData(GATHER);
		if (s != null && s.tendril() || s == null && g != null && g.tendril()) {
			// on a tendril: the arm is left be; the body only braces into it a little
			int side = s != null ? s.side() : g.side();
			float on = s != null ? Math.min(ease(s.age() / 2.0F), ease((WebMorph.TICKS - s.age()) / 3.0F)) : ease(g.age() / 4.0F);
			float lean = s != null ? swing(s.age()) - 0.5F : -0.3F;
			h.body.yRot += -side * 0.12F * on * lean;
			return;
		}
		if (s == null && g != null) {
			// gathering: the arm drawn back a little and out from the side, shuddering as the lumps pour in (the engulfed
			// arm held further out, the mass dragging it down and wide)
			ModelPart arm = g.side() > 0 ? h.rightArm : h.leftArm;
			float on = ease(g.age() / 4.0F);
			float shudder = g.age() < WebMorph.FORMED_TICKS ? Mth.sin(g.age() * 2.7F) * (g.armor() ? 0.09F : 0.06F) : 0.0F;
			float wide = g.armor() ? 0.15F * engulfed(g) : 0.0F;
			// (your own, in first person: hung further back and out, so the mass stays low and to the side of the view)
			boolean fp = g.armor() && ownFirstPerson(living);
			float back = fp ? ARMOR_FP_BACK : 0.0F;
			arm.xRot = Mth.lerp(on, arm.xRot, 0.35F - wide + back + shudder);
			arm.yRot = Mth.lerp(on, arm.yRot, 0.0F);
			arm.zRot = Mth.lerp(on, arm.zRot, g.side() * (0.3F + wide + (fp ? ARMOR_FP_WIDE : 0.0F)) + shudder);
			h.body.yRot += -g.side() * 0.12F * on;
			return;
		}
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
		} else if (s.weapon() == WebMorph.HAMMER) {
			// following the uppercut up
			h.head.xRot = Mth.lerp(on * 0.5F, h.head.xRot, -0.35F * swing(s.age()));
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		Strike s = state.getData(KEY);
		Gather g = state.getData(GATHER);
		if (s == null && g != null && !state.isInvisible && !g.tendril()) {
			submitGather(poseStack, collector, state, g);
			return;
		}
		if (s == null || state.isInvisible || s.tendril()) {
			// on a tendril: WebTendrilLayer draws it
			return;
		}
		PlayerModel parent = this.getParentModel();
		WebSuitGrowth.shift(parent, state);
		ModelPart arm = s.side() > 0 ? parent.rightArm : parent.leftArm;
		boolean hulk = WebSuitGrowth.bigBody(state);
		boolean slim = state.skin != null && state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
		float handX = -s.side() * (slim ? 0.5F : 1.0F);
		float handY = hulk ? HULK_HAND_Y : HAND_Y;
		float grow = formed(s.age());
		// the engulfed arm: its heads bigger, its cord thicker (less so in your own first-person view)
		boolean fp = s.armor() && ownFirstPerson(state);
		float head = grow * headScale(s.armor(), fp);
		float mass = fp ? ARMOR_MASS_FP : 1.0F;
		float cord = s.armor() ? Mth.lerp(mass, 1.0F, ARMOR_CORD) : 1.0F;
		float armHalf = (slim ? ARM_HALF_SLIM : ARM_HALF) * (hulk ? HULK_ARM : 1.0F);
		float length = Math.max(0.0F, s.reach() * 16.0F - handY - 2.0F) * out(s.age());
		float sw = swing(s.age());
		// a swing whips the cord out behind the hand (the scythe sideways, the mace and spear back along the arm's arc)
		float lag = (s.age() < WebMorph.HIT ? 1.0F - sw : 0.0F) * CORD_LAG * length;
		Vec3 bow = s.weapon() == WebMorph.SCYTHE ? new Vec3(s.side() * lag, 0.0, 0.0)
			: s.weapon() == WebMorph.WHIP ? new Vec3(s.side() * lag * WHIP_LAG * 0.5, 0.0, -lag * WHIP_LAG)
			: new Vec3(0.0, 0.0, s.weapon() == WebMorph.MACE || s.weapon() == WebMorph.HAMMER ? -lag : 0.0);
		// the whip-blade's lash: a wave rolling out along it, cracking at the hit and dying away
		float whipWave = s.weapon() != WebMorph.WHIP ? 0.0F : WHIP_WAVE * (s.age() < WebMorph.HIT + 3.0F ? 1.0F : 0.3F);
		float age = s.age();
		float time = state.ageInTicks;
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		arm.translateAndRotate(poseStack);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebStrainLook.texture(WebTubes.TEXTURE, state)), (pose, buffer) -> {
			if (s.armor()) {
				// the whole arm still one mass of goo, swinging; it melts back with the weapon at the end
				engulfedArm(buffer, pose, handX, handY, armHalf, s.side(), 1.0F, 1.0F, grow, mass, time, state.lightCoords);
			}
			Vec3 hand = new Vec3(handX, handY, 0.0);
			Vec3 prev = hand;
			for (int i = 1; i <= PIECES; i++) {
				float f = i / (float)PIECES;
				// writhing a little along it
				double wx = Mth.sin(time * 1.3F + f * 6.0F) * 0.35 * f + Mth.sin(f * 9.0F - age * 2.2F) * whipWave * f * 3.0F,
					wz = Mth.cos(time * 1.1F + f * 5.0F) * 0.35 * f;
				Vec3 p = hand.add(0.0, length * f, 0.0).add(bow.scale(Mth.sin(f * Mth.PI))).add(wx, 0.0, wz);
				double r0 = Mth.lerp(f - 1.0F / PIECES, CORD_ROOT, CORD_TIP) * Math.max(0.35F, grow) * cord;
				double r1 = Mth.lerp(f, CORD_ROOT, CORD_TIP) * Math.max(0.35F, grow) * cord;
				if (length > 0.5F) {
					WebTubes.cord(buffer, pose, px(prev), px(p), r0 / 16.0, r1 / 16.0, i % 3 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, state.lightCoords);
				}
				prev = p;
			}
			Vec3 dir = length > 0.5F ? prev.subtract(hand.add(bow.scale(Mth.sin(0.9F * Mth.PI)))).normalize() : new Vec3(0.0, 1.0, 0.0);
			weapon(buffer, pose, s, prev, dir, head, state.lightCoords);
		});
		poseStack.popPose();
	}

	/**
	 * (0.16.7) Living Armor's engulfed arm, in the arm's own space (pixels): the tendril reared out above and behind the
	 * shoulder and wound {@code wound} of the way down the arm (0-1) in {@link #ARMOR_TURNS} coils round it, its leading
	 * end fat; the coils thickened and fused {@code engulf} of the way (0-1) into one lumpy mass over the whole arm,
	 * rippling toward the hand; the lot {@code on} of its size (melting away as a strike ends), its thickness {@code mass}
	 * of the full (slighter in your own first-person view).
	 */
	private static void engulfedArm(VertexConsumer buffer, PoseStack.Pose pose, float handX, float handY, float armHalf, int side, float wound,
		float engulf, float on, float mass, float time, int light) {
		if (on < 0.05F || wound <= 0.0F) {
			return;
		}
		float top = -2.0F;
		Vec3 root = new Vec3(handX, top - ARMOR_ROOT_UP, ARMOR_ROOT_BACK);
		// the coil: round the arm, down it, at the arm's surface; thicker as it fuses
		float radius = Mth.lerp(engulf, ARMOR_TENDRIL, ARMOR_TENDRIL_FULL) * on * mass;
		float ring = armHalf + radius * 0.7F;
		Vec3 prev = root;
		int pieces = Math.max(1, Mth.ceil(ARMOR_PIECES * wound));
		for (int i = 1; i <= pieces; i++) {
			float u = Math.min(wound, i / (float)ARMOR_PIECES);
			float angle = u * ARMOR_TURNS * Mth.TWO_PI * side + Mth.HALF_PI;
			// (the first piece comes down off the root onto the arm)
			float y = Mth.lerp(u, top, handY + 1.0F);
			Vec3 p = new Vec3(handX + Mth.cos(angle) * ring, y, Mth.sin(angle) * ring);
			boolean head = i == pieces && wound < 1.0F;
			double r0 = radius, r1 = head ? radius + ARMOR_HEAD * on : radius;
			WebTubes.cord(buffer, pose, px(prev), px(p), r0 / 16.0, r1 / 16.0, i % 4 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, light);
			if (head) {
				// the leading end, blunt and fat
				Vec3 ahead = p.subtract(prev).normalize().scale(1.2 * on);
				WebTubes.cord(buffer, pose, px(p), px(p.add(ahead)), r1 / 16.0, 0.3 * on / 16.0, WebTubes.SHEEN, light);
			}
			prev = p;
		}
		if (engulf < 0.02F) {
			return;
		}
		// the mass: one fat lumpy sleeve down the arm's middle, swallowing the coils as it swells, rippling toward the hand
		float sleeve = (armHalf + ARMOR_SLEEVE * engulf * mass) * on;
		float y0 = top - 0.5F, y1 = handY + 1.0F;
		Vec3 last = new Vec3(handX, y0, 0.0);
		for (int k = 1; k <= ARMOR_SLEEVE_PIECES; k++) {
			float f = k / (float)ARMOR_SLEEVE_PIECES;
			float ripple0 = 1.0F + ARMOR_RIPPLE * Mth.sin(time * 0.9F - (k - 1) * 1.3F) * engulf;
			float ripple1 = 1.0F + ARMOR_RIPPLE * Mth.sin(time * 0.9F - k * 1.3F) * engulf;
			// a little off the arm's line, so the lumps don't read as a straight pipe
			Vec3 p = new Vec3(handX + Mth.sin(f * 7.0F + time * 0.3F) * 0.5 * engulf, Mth.lerp(f, y0, y1), Mth.cos(f * 5.0F) * 0.5 * engulf);
			WebTubes.cord(buffer, pose, px(last), px(p), sleeve * ripple0 / 16.0, sleeve * ripple1 / 16.0, k % 2 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, light);
			last = p;
		}
	}

	/**
	 * Gathering: lumps course over the suit (front and back) toward the striking shoulder, each leaving a vein behind it,
	 * then run down the arm; the forearm swells with them, and the weapon grows out of the hand. With Living Armor the
	 * lumps meet at the shoulder as a tendril that winds down and engulfs the arm instead ({@link #engulfedArm}).
	 */
	private void submitGather(PoseStack poseStack, SubmitNodeCollector collector, AvatarRenderState state, Gather g) {
		PlayerModel parent = this.getParentModel();
		WebSuitGrowth.shift(parent, state);
		ModelPart arm = g.side() > 0 ? parent.rightArm : parent.leftArm;
		boolean hulk = WebSuitGrowth.bigBody(state);
		boolean slim = state.skin != null && state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
		float age = g.age();
		int light = state.lightCoords;
		// x mirrored for the left arm: the courses are written for the right (model -x)
		float mirror = g.side() > 0 ? 1.0F : -1.0F;
		if (age < WebMorph.COURSE_TICKS + VEIN_FADE) {
			poseStack.pushPose();
			parent.root().translateAndRotate(poseStack);
			parent.body.translateAndRotate(poseStack);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebStrainLook.texture(WebTubes.TEXTURE, state)), (pose, buffer) -> {
				for (int i = 0; i < COURSES.length; i++) {
					float[] c = COURSES[i];
					Vec3 from = new Vec3(c[0] * mirror, c[1], c[2]), to = new Vec3(c[3] * mirror, c[4], c[5]);
					float f = (age - c[6]) / COURSE_TRAVEL;
					if (f <= 0.0F) {
						continue;
					}
					float after = (age - c[6] - COURSE_TRAVEL) / VEIN_FADE;
					course(buffer, pose, from, to, Math.min(1.0F, f), after, i, light);
				}
			});
			poseStack.popPose();
		}
		float handX = -g.side() * (slim ? 0.5F : 1.0F);
		float handY = hulk ? HULK_HAND_Y : HAND_Y;
		float outer = -g.side() * (slim ? 2.6F : 3.1F);
		float swell = swollen(g);
		float grow = grown(g);
		float time = state.ageInTicks;
		poseStack.pushPose();
		parent.root().translateAndRotate(poseStack);
		arm.translateAndRotate(poseStack);
		if (g.armor()) {
			// Living Armor: the tendril winds down from the shoulder and engulfs the arm, and the weapon swells out of that
			float armHalf = (slim ? ARM_HALF_SLIM : ARM_HALF) * (hulk ? HULK_ARM : 1.0F);
			boolean fp = ownFirstPerson(state);
			float mass = fp ? ARMOR_MASS_FP : 1.0F;
			float head = grow * headScale(true, fp);
			collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebStrainLook.texture(WebTubes.TEXTURE, state)), (pose, buffer) -> {
				engulfedArm(buffer, pose, handX, handY, armHalf, g.side(), wound(g), engulfed(g), 1.0F, mass, time, light);
				Vec3 hand = new Vec3(handX, handY, 0.0);
				weapon(buffer, pose, new Strike(g.weapon(), g.side(), 0.0F, 0.0F, 0.0F, 0.0F, false, true), hand, new Vec3(0.0, 1.0, 0.0), head, light);
			});
			poseStack.popPose();
			return;
		}
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WebStrainLook.texture(WebTubes.TEXTURE, state)), (pose, buffer) -> {
			// down the arm: one lump on the front, the back and the outside, one after another
			float start = WebMorph.COURSE_TICKS - 1.5F;
			Vec3[][] runs = {
				{new Vec3(handX, -1.5, -2.1), new Vec3(handX, handY - 1.0, -2.1)},
				{new Vec3(outer, -1.0, 0.0), new Vec3(outer, handY - 1.0, 0.0)},
				{new Vec3(handX, -1.5, 2.1), new Vec3(handX, handY - 1.0, 2.1)},
			};
			for (int j = 0; j < runs.length; j++) {
				float f = (age - start - j * ARM_GAP) / ARM_TRAVEL;
				if (f > 0.0F) {
					course(buffer, pose, runs[j][0], runs[j][1], Math.min(1.0F, f), (age - start - j * ARM_GAP - ARM_TRAVEL) / VEIN_FADE, j + 7, light);
				}
			}
			if (swell > 0.02F) {
				// the forearm fat with it, lumpy, rippling toward the hand
				float r = SWELL * swell;
				for (int k = 0; k < 3; k++) {
					float y0 = handY * (0.45F + k * 0.18F), y1 = y0 + handY * 0.2F;
					float ripple = 1.0F + 0.12F * Mth.sin(age * 1.6F - k * 1.4F);
					WebTubes.cord(buffer, pose, px(new Vec3(handX, y0, 0.0)), px(new Vec3(handX, y1, 0.0)), r * 0.75 * ripple / 16.0, r * ripple / 16.0,
						k == 1 ? WebTubes.SHEEN : WebTubes.BLACK, light);
				}
			}
			// the weapon grows out of the hand, hanging down along the arm
			Vec3 hand = new Vec3(handX, handY, 0.0);
			weapon(buffer, pose, new Strike(g.weapon(), g.side(), 0.0F, 0.0F, 0.0F, 0.0F, false, false), hand, new Vec3(0.0, 1.0, 0.0), grow, light);
		});
		poseStack.popPose();
	}

	/**
	 * A lump coursing from {@code from} to {@code to} under the suit (pixels), {@code f} of the way along, wriggling a
	 * little as it goes; behind it the vein it leaves, shrinking away {@code after} (0-1) once it's arrived.
	 */
	private static void course(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to, float f, float after, int seed, int light) {
		Vec3 along = to.subtract(from);
		Vec3 cross = along.cross(new Vec3(0.0, 0.0, 1.0));
		Vec3 across = cross.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : cross.normalize();
		java.util.function.DoubleFunction<Vec3> at = u -> from.add(along.scale(u)).add(across.scale(Mth.sin((float)u * 9.0F + seed * 1.7F) * 0.6));
		float vein = VEIN * (1.0F - Mth.clamp(after, 0.0F, 1.0F));
		if (vein > 0.02F) {
			int pieces = 6;
			Vec3 prev = at.apply(0.0);
			for (int i = 1; i <= pieces; i++) {
				Vec3 p = at.apply(f * i / (double)pieces);
				WebTubes.cord(buffer, pose, px(prev), px(p), vein / 16.0, vein / 16.0, WebTubes.BLACK, light);
				prev = p;
			}
		}
		if (after < 0.0F) {
			// the lump itself: a swelling travelling along, fattest in its middle
			double a = Math.max(0.0, f - LUMP_LENGTH), b = Math.min(1.0, f + LUMP_LENGTH * 0.6);
			Vec3 mid = at.apply(f);
			WebTubes.cord(buffer, pose, px(at.apply(a)), px(mid), 0.3 / 16.0, LUMP / 16.0, WebTubes.SHEEN, light);
			WebTubes.cord(buffer, pose, px(mid), px(at.apply(b)), LUMP / 16.0, 0.3 / 16.0, WebTubes.SHEEN, light);
		}
	}

	/** Pixels to blocks. */
	private static Vec3 px(Vec3 v) {
		return v.scale(1.0 / 16.0);
	}

	/** The weapon at the end of the cord (or a tendril), {@code at}, pointing along {@code dir} (pixels), {@code grow} of its size. */
	static void weapon(VertexConsumer buffer, PoseStack.Pose pose, Strike s, Vec3 at, Vec3 dir, float grow, int light) {
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
			case WebMorph.WHIP -> {
				// a lash of barbed links, each with a pale barb hooked back off alternate sides, out to a long thin blade
				Vec3 p = at;
				for (int i = 0; i < WHIP_LINKS; i++) {
					Vec3 n = p.add(dir.scale(2.2 * g)).add(across.scale(Mth.sin(i * 1.3F) * 0.4 * g));
					WebTubes.prism(buffer, pose, px(p), px(n), across, 0.9 * g / 16.0, 0.6 * g / 16.0, 0.7 * g / 16.0, 0.5 * g / 16.0,
						i % 2 == 0 ? WebTubes.SHEEN : WebTubes.BLACK, light);
					int way = i % 2 == 0 ? 1 : -1;
					Vec3 root = n.add(dir.scale(-0.8 * g));
					WebTubes.cord(buffer, pose, px(root), px(root.add(across.scale(way * 1.8 * g)).add(dir.scale(-1.4 * g))), 0.45 * g / 16.0, 0.05 / 16.0,
						WebTubes.BONE, light);
					p = n;
				}
				Vec3 tip = p.add(dir.scale(6.0 * g));
				WebTubes.prism(buffer, pose, px(p), px(tip), across, 1.6 * g / 16.0, 0.35 * g / 16.0, 0.05 / 16.0, 0.05 / 16.0, WebTubes.SHEEN, light);
				for (int side = -1; side <= 1; side += 2) {
					WebTubes.prism(buffer, pose, px(p.add(across.scale(side * 1.4 * g))), px(tip), across, 0.3 * g / 16.0, 0.3 * g / 16.0, 0.05 / 16.0,
						0.05 / 16.0, WebTubes.BONE, light);
				}
			}
			case WebMorph.HAMMER -> {
				// a thick neck, then a great flaring block of a fist, banded round its middle, four pale knuckles on its face
				Vec3 neck = at.add(dir.scale(2.5 * g));
				WebTubes.cord(buffer, pose, px(at), px(neck), 1.5 * g / 16.0, 2.6 * g / 16.0, WebTubes.BLACK, light);
				Vec3 face = neck.add(dir.scale(7.0 * g));
				WebTubes.prism(buffer, pose, px(neck), px(face), across, 4.0 * g / 16.0, 4.0 * g / 16.0, 4.6 * g / 16.0, 4.6 * g / 16.0, WebTubes.SHEEN,
					light);
				WebTubes.prism(buffer, pose, px(neck.add(dir.scale(2.0 * g))), px(neck.add(dir.scale(4.5 * g))), across, 4.9 * g / 16.0, 4.9 * g / 16.0,
					4.9 * g / 16.0, 4.9 * g / 16.0, WebTubes.BLACK, light);
				for (int a = -1; a <= 1; a += 2) {
					for (int b = -1; b <= 1; b += 2) {
						Vec3 k = face.add(across.scale(a * 2.3 * g)).add(sideways.scale(b * 2.3 * g));
						WebTubes.cord(buffer, pose, px(k.add(dir.scale(-0.5 * g))), px(k.add(dir.scale(1.6 * g))), 1.1 * g / 16.0, 0.5 * g / 16.0,
							WebTubes.BONE, light);
					}
				}
			}
			case WebMorph.PALM -> {
				// the white strain's: a short wrist, then a broad flat open hand, the fingers pressed together, the heel pale
				Vec3 wrist = at.add(dir.scale(2.0 * g));
				WebTubes.cord(buffer, pose, px(at), px(wrist), 1.1 * g / 16.0, 1.4 * g / 16.0, WebTubes.BLACK, light);
				Vec3 heel = wrist.add(dir.scale(1.5 * g));
				// the palm: wide across the swing, thin through it
				WebTubes.prism(buffer, pose, px(wrist), px(heel), sideways, 3.2 * g / 16.0, 1.0 * g / 16.0, 4.2 * g / 16.0, 1.0 * g / 16.0, WebTubes.BONE,
					light);
				Vec3 fingers = heel.add(dir.scale(5.0 * g));
				WebTubes.prism(buffer, pose, px(heel), px(fingers), sideways, 4.2 * g / 16.0, 0.9 * g / 16.0, 3.4 * g / 16.0, 0.6 * g / 16.0, WebTubes.SHEEN,
					light);
				// four rounded fingertips
				for (int i = 0; i < 4; i++) {
					Vec3 f = fingers.add(sideways.scale((i - 1.5) * 1.8 * g));
					WebTubes.cord(buffer, pose, px(f.add(dir.scale(-0.8 * g))), px(f.add(dir.scale(1.2 * g))), 0.8 * g / 16.0, 0.5 * g / 16.0,
						WebTubes.SHEEN, light);
				}
			}
			case WebMorph.AXE -> {
				// the red strain's: a long haft, and a broad bearded blade out to one side, its cutting edge pale and hooked
				Vec3 haft = at.add(dir.scale(7.0 * g));
				WebTubes.cord(buffer, pose, px(at), px(haft), 1.0 * g / 16.0, 1.2 * g / 16.0, WebTubes.BLACK, light);
				Vec3 out = across.scale(-s.side());
				Vec3 head = haft.add(dir.scale(-3.5 * g));
				// the cheek of the blade, flaring wider as it goes out from the haft
				WebTubes.prism(buffer, pose, px(head), px(head.add(out.scale(6.0 * g))), dir, 2.0 * g / 16.0, 0.6 * g / 16.0, 4.2 * g / 16.0,
					0.45 * g / 16.0, WebTubes.SHEEN, light);
				// the edge: a pale arc down the blade's far side, its beard hooked back toward you
				Vec3 edgeTop = head.add(out.scale(6.2 * g)).add(dir.scale(3.8 * g));
				Vec3 edgeBottom = head.add(out.scale(6.2 * g)).add(dir.scale(-4.6 * g));
				WebTubes.prism(buffer, pose, px(edgeTop), px(edgeBottom), out, 0.6 * g / 16.0, 0.3 * g / 16.0, 0.6 * g / 16.0, 0.3 * g / 16.0,
					WebTubes.BONE, light);
				WebTubes.cord(buffer, pose, px(edgeBottom), px(edgeBottom.add(out.scale(-2.2 * g)).add(dir.scale(-1.6 * g))), 0.6 * g / 16.0,
					0.05 / 16.0, WebTubes.BONE, light);
				// a spike out of the back of the head
				WebTubes.cord(buffer, pose, px(head), px(head.add(out.scale(-3.5 * g))), 0.9 * g / 16.0, 0.05 / 16.0, WebTubes.BONE, light);
			}
			case WebMorph.HOOK -> {
				// a knob of a palm, and three hooks out of it curving forward, out and back like a grapple, bone at the points
				Vec3 base = at.add(dir.scale(2.5 * g));
				WebTubes.cord(buffer, pose, px(at), px(base), 1.1 * g / 16.0, 1.6 * g / 16.0, WebTubes.BLACK, light);
				Vec3 hub = base.add(dir.scale(1.5 * g));
				WebTubes.prism(buffer, pose, px(base), px(hub.add(dir.scale(0.6 * g))), across, 1.9 * g / 16.0, 1.9 * g / 16.0, 1.3 * g / 16.0,
					1.3 * g / 16.0, WebTubes.SHEEN, light);
				for (int i = 0; i < HOOK_PRONGS; i++) {
					float angle = i * Mth.TWO_PI / HOOK_PRONGS + 0.3F;
					Vec3 radial = across.scale(Mth.cos(angle)).add(sideways.scale(Mth.sin(angle)));
					Vec3 prev = hub;
					int pieces = 6;
					for (int j = 1; j <= pieces; j++) {
						double f = j / (double)pieces;
						Vec3 p = hub.add(dir.scale((10.0 * f - 7.0 * f * f) * g)).add(radial.scale((5.0 * f * f + 0.8 * f) * g));
						double r0 = (1.2 - 1.1 * (f - 1.0 / pieces)) * g, r1 = (1.2 - 1.1 * f) * g + 0.03;
						WebTubes.cord(buffer, pose, px(prev), px(p), r0 / 16.0, r1 / 16.0, j > pieces / 2 ? WebTubes.BONE : WebTubes.SHEEN, light);
						prev = p;
					}
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

	/** True while a weapon arm is out (nothing else should take that arm; a tendril's leaves the arms free). */
	static boolean striking(LivingEntityRenderState state) {
		Strike s = state.getData(KEY);
		Gather g = state.getData(GATHER);
		return s != null && !s.tendril() || s == null && g != null && !g.tendril();
	}

}
