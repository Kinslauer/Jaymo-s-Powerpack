package com.jaymospowers.client.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.client.power.web.WebPowerClient;
import com.jaymospowers.core.HiddenArmor;
import com.jaymospowers.power.web.WebCracks;
import com.jaymospowers.power.web.WebTendrilCombos;
import com.jaymospowers.power.web.WebTendrils;
import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigMoves;
import com.jaymospowers.rig.RigOcto;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Octo Core's four arms as everyone sees them ({@link RigOcto}): jointed gunmetal arms on the Core's spine, each a
 * shoulder strut, a forearm and a three-pronged claw, with a lit joint at the elbow. Both struts telescope (Andy: "they
 * can be that size at rest and should telescope further out"): a polished rod slides out of each, so an arm reaches from
 * its rest length to several blocks. Folded, they lie short down the back; out, the upper pair arcs up over the shoulders
 * and the lower pair reaches down and out like legs.
 * <p>Where an arm has somewhere to be in the world, it is posed there ({@link #reach}: two joints and the rods, worked out
 * from the point): walking or climbing, each claw on the block it planted on ({@link RigOctoWalk}); holding, the upper
 * claws on either side of what they hold, wherever it is (so a grab telescopes out to it and draws it in); the anchor's
 * claw on the wall or creature it bit; the jab and the cross punch out to the first thing down the look; the blast brings
 * all four claws together ahead of the chest. Other moves pose them by hand: the lower pair stabs and lifts for the
 * skewer, all four spin flat for the pinwheel, the lower pair plants behind for the vault and all four point down for the
 * dive; the flurry and the watch thrust one arm. Every angle eases toward its target, so the arms move like machinery.
 * <p>The arms never pass through the wearer (Andy: "does it clip through the body in game?"): each elbow goes out a way
 * that keeps the arm clear ({@link #clear}), the upper pair back and out over the shoulders and the lower pair out past
 * the hips, and every move's claw points sit where the forearms reach them around the body, not through it.
 * <p>Folded, they draw all the way into their mounts, nothing left sticking out (Andy: "they should also collapse
 * completely"). With the Core hidden (the inventory's eye button), the suit's plates come off but the reactor stays, and the
 * arms are mounted on it ({@link #MOUNT_FRONT}), reaching out from the chest (Andy: "the reactor core stays visible drawn
 * and the arms should extend from that too, still visible").
 * <p>Also here, on the wearer's own client (movement is the client's): R and G as the upper arms ({@link #routeKey}: the
 * symbiote's tendril keys, sent as the tendrils' grabs), and on the arm walk, the arms' Wall Crawl ({@link #move}: walls,
 * ceilings, ledges, the coiled leap) and the stride (carried on the four arms, held up off the ground between them).
 * Drawn on the body bone, after the suit ({@link RigSuit}).
 */
public final class RigOctoClient {
	// ---- tuning ----
	/** How quickly each angle eases toward its target (share a frame), with a claw placed in the world, and how quickly the arms unfold. */
	static final float EASE = 0.22F, EASE_PLACED = 0.55F, UNFOLD_EASE = 0.12F;
	/**
	 * The arms' Wall Crawl: how fast they crawl you along a wall or a ceiling (blocks a tick), how quickly you take that
	 * speed (share a tick), and how hard you're held against the wall or up into the ceiling (blocks a tick).
	 */
	static final double CRAWL_SPEED = 0.2, CRAWL_GRIP = 0.45, WALL_PRESS = 0.04, CEILING_PRESS = 0.1;
	/**
	 * Sneak at a wall (or under a ceiling, within {@code CEILING_LATCH_REACH}, {@code CEILING_LATCH_GROUND} from the
	 * ground): all four slam into it. You're thrown at it ({@code LATCH_SLAM}, {@code CEILING_SLAM_EXTRA} more up into a
	 * ceiling), lifted off the ground ({@code LATCH_LIFT}) and pinned for {@code LATCH_HOLD_TICKS}. By a wall, the ceiling
	 * wins only looking up more than {@code CEILING_LATCH_LOOK} degrees.
	 */
	static final double LATCH_SLAM = 0.35, LATCH_LIFT = 0.3, CEILING_LATCH_REACH = 1.8, CEILING_LATCH_GROUND = 0.9, CEILING_SLAM_EXTRA = 0.15;
	static final int LATCH_HOLD_TICKS = 8;
	static final float CEILING_LATCH_LOOK = 30.0F;
	/** How deep a slam cracks the blocks the claws hit (0-9). */
	static final int SLAM_CRACK = 5;
	/** Over the top of a wall: the hop onto the ledge (up, forward). */
	static final double LEDGE_HOP = 0.35, LEDGE_FORWARD = 0.18;
	/**
	 * The coiled leap: holding Jump on the walk where there's no climbing to be had (fewer than {@code STRIDE_RISE_ANCHORS}
	 * grips round you) or sprinting, the arms sink you to {@code STRIDE_COIL} of your height on them; let go after at least
	 * {@code COIL_MIN} ticks and they fling you down the look, harder the longer they coiled (full at {@code COIL_FULL}),
	 * from {@code LEAP_MIN} to {@code LEAP_MAX} along it and {@code LEAP_UP_MIN} to {@code LEAP_UP_MAX} up; they let you
	 * fly for {@code LEAP_GRACE_TICKS} before they take hold again.
	 */
	static final double STRIDE_COIL = 0.55, LEAP_MIN = 0.6, LEAP_MAX = 1.5, LEAP_UP_MIN = 0.45, LEAP_UP_MAX = 0.95;
	static final int COIL_MIN = 4, COIL_FULL = 20, LEAP_GRACE_TICKS = 8;
	/**
	 * The arm walk: held {@code STRIDE_HEIGHT} blocks off the ground (eased there at {@code STRIDE_LIFT} of the gap a tick),
	 * walked along at {@code STRIDE_SPEED} ({@code STRIDE_SPRINT} sprinting), the grip taking {@code STRIDE_GRIP} of the
	 * change a tick; Jump climbs and Sneak lowers at {@code STRIDE_CLIMB}, and climbing needs {@code STRIDE_RISE_ANCHORS}
	 * ways to grab.
	 */
	static final double STRIDE_HEIGHT = 1.3, STRIDE_LIFT = 0.3, STRIDE_SPEED = 0.2, STRIDE_SPRINT = 0.3, STRIDE_GRIP = 0.35, STRIDE_CLIMB = 0.22;
	static final int STRIDE_RISE_ANCHORS = 4;
	/** The pinwheel's spin (radians a tick). */
	static final float SPIN_RATE = 0.9F;
	/** How far the rods may run out (times the struts' own length): walking, and reaching for something far off (a grab, the anchor, a punch). */
	static final float EXT_WALK = 3.5F, EXT_FAR = 16.0F;
	/** Your own first person, at rest: where the upper claws hover (body model pixels: out to the side, up, ahead). */
	static final float[] FP_HOVER = {8.0F, -8.0F, -15.0F};
	/** The jab and cross: how far down the look they punch with nothing in reach (blocks). */
	static final double PUNCH_EMPTY = 3.0;

	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/rig/octo.png");
	static final Identifier GLOW = JaymosPowers.id("textures/entity/rig/octo_glow.png");
	/** The arms' paintable sheets ({@code tools/gen_rig_paint.py}): their plates in light greys, and their lights in white. */
	static final Identifier PAINT = JaymosPowers.id("textures/entity/rig/octo_paint.png");
	static final Identifier LIGHTS = JaymosPowers.id("textures/entity/rig/octo_lights.png");
	/** On the render state: per arm {pitch, yaw, roll, elbow, upper reach, fore reach, claw open}; null when the arms aren't worn. */
	static final RenderStateDataKey<float[]> ARMS = RenderStateDataKey.create(() -> "jaymospowers:rig_octo");

	/** Arm order: upper right, upper left, lower right, lower left. Where each meets the back (body bone pixels). */
	private static final float[][] MOUNT = {{-2.5F, 2.5F, 3.0F}, {2.5F, 2.5F, 3.0F}, {-2.5F, 8.5F, 3.0F}, {2.5F, 8.5F, 3.0F}};
	/** With the suit hidden, where each meets the reactor on the chest instead (the suit's {@code core} plate, at 0, 3, -3). */
	private static final float[][] MOUNT_FRONT = {{-1.3F, 2.0F, -3.9F}, {1.3F, 2.0F, -3.9F}, {-1.3F, 4.0F, -3.9F}, {1.3F, 4.0F, -3.9F}};
	/** Whether the pose being worked out (or drawn) is on the reactor ({@link #MOUNT_FRONT}): set by {@link #pose} for its own run. */
	private static boolean front;
	/** The arm's lengths (pixels): the shoulder strut (to the elbow, at rest), the forearm (to the claw), the claw to its prongs' tips. */
	private static final float UPPER = 10.0F, FORE = 9.0F, CLAW_TIP = 4.2F;
	/** How much each strut grows (pixels) for every unit of reach past 1: the rods sliding out. */
	private static final float UPPER_ROD = 9.0F, FORE_ROD = 8.0F;
	private static final int VALUES = 7;
	/** As the avatar renderer draws a player: the model shrunk this much, lifted this high (blocks), and dropped this far crouching. */
	private static final double MODEL_SCALE = 0.9375, MODEL_LIFT = 1.501, CROUCH_DROP = -2.0 / 16.0;
	/** The longest the shoulder strut runs out to its elbow (pixels), however far the claw is going. */
	private static final float ELBOW_MAX = 26.0F;

	private static final Map<UUID, float[]> POSE = new HashMap<>();
	private static final Map<UUID, Float> UNFOLD = new HashMap<>();
	/** Whether each wearer's arms were on the reactor last frame (a change snaps them over rather than swinging round). */
	private static final Map<UUID, Boolean> FRONT = new HashMap<>();
	/** Where each wearer's anchor bit (worked out once, when the move starts): the move's start, and the point. */
	private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();

	private record Anchor(long start, Vec3 at) {
	}

	private static ModelPart root;
	private static final ModelPart[] ARM = new ModelPart[4], UPPER_RODS = new ModelPart[4], FORE_PART = new ModelPart[4], FORE_RODS = new ModelPart[4],
		CLAW = new ModelPart[4], PRONG_A = new ModelPart[4], PRONG_B = new ModelPart[4], PRONG_C = new ModelPart[4];

	private RigOctoClient() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(RigOctoClient::move);
		LevelRenderEvents.COLLECT_SUBMITS.register(RigOctoClient::firstPerson);
	}

	private static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition back = mesh.getRoot();
		CubeDeformation none = new CubeDeformation(0.0F);
		for (int i = 0; i < 4; i++) {
			PartDefinition arm = back.addOrReplaceChild("arm_" + i, CubeListBuilder.create()
				// the mount on the spine, then the shoulder strut's sleeve
				.texOffs(0, 24).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 2.0F, none)
				.texOffs(0, 0).addBox(-1.0F, -1.0F, 1.0F, 2.0F, 2.0F, 9.0F, none),
				PartPose.offset(MOUNT[i][0], MOUNT[i][1], MOUNT[i][2]));
			// the rod that slides out of it
			arm.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(24, 0).addBox(-0.6F, -0.6F, 0.0F, 1.2F, 1.2F, 9.0F, none),
				PartPose.offset(0.0F, 0.0F, 1.0F));
			PartDefinition fore = arm.addOrReplaceChild("fore", CubeListBuilder.create()
				// the elbow joint (lit), then the forearm's sleeve
				.texOffs(12, 24).addBox(-1.25F, -1.25F, -1.25F, 2.5F, 2.5F, 2.5F, none)
				.texOffs(0, 12).addBox(-0.75F, -0.75F, 1.0F, 1.5F, 1.5F, 8.0F, none),
				PartPose.offset(0.0F, 0.0F, UPPER));
			fore.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(24, 12).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F, none),
				PartPose.offset(0.0F, 0.0F, 1.0F));
			PartDefinition claw = fore.addOrReplaceChild("claw", CubeListBuilder.create()
				.texOffs(22, 24).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 1.5F, none), PartPose.offset(0.0F, 0.0F, FORE));
			claw.addOrReplaceChild("prong_a", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(0.0F, -0.7F, 1.2F));
			claw.addOrReplaceChild("prong_b", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(-0.6F, 0.5F, 1.2F));
			claw.addOrReplaceChild("prong_c", CubeListBuilder.create().texOffs(22, 28).addBox(-0.4F, -0.4F, 0.0F, 0.8F, 0.8F, 3.0F, none),
				PartPose.offset(0.6F, 0.5F, 1.2F));
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static void bake() {
		if (root != null) {
			return;
		}
		root = mesh().bakeRoot();
		for (int i = 0; i < 4; i++) {
			ARM[i] = root.getChild("arm_" + i);
			UPPER_RODS[i] = ARM[i].getChild("rod");
			FORE_PART[i] = ARM[i].getChild("fore");
			FORE_RODS[i] = FORE_PART[i].getChild("rod");
			CLAW[i] = FORE_PART[i].getChild("claw");
			PRONG_A[i] = CLAW[i].getChild("prong_a");
			PRONG_B[i] = CLAW[i].getChild("prong_b");
			PRONG_C[i] = CLAW[i].getChild("prong_c");
		}
	}

	// ---- poses ------------------------------------------------------------------------------------------------------------

	/** Writes arm {@code i}'s target into {@code t}: pitch (up is positive), yaw (out from the spine), elbow, reach, claw open. */
	private static void set(float[] t, int i, float pitch, float yaw, float elbow, float reach, float open) {
		int o = i * VALUES;
		// the left arms mirror the right
		t[o] = pitch;
		t[o + 1] = i % 2 == 0 ? -yaw : yaw;
		t[o + 2] = 0.0F;
		t[o + 3] = elbow;
		t[o + 4] = reach;
		t[o + 5] = reach;
		t[o + 6] = open;
	}

	/** -1 for the right arms, 1 for the left. */
	private static float side(int i) {
		return i % 2 == 0 ? -1.0F : 1.0F;
	}

	/**
	 * Where arm {@code i}'s elbow goes to keep it clear of the body: a way out from the mount, then the share of the
	 * distance to the claw's point the shoulder strut runs out. The upper pair goes back and out over the shoulders (up
	 * through the helm otherwise), the lower pair out past the hips.
	 */
	private static float[] clear(int i) {
		return i < 2 ? new float[] {side(i) * 1.2F, -0.6F, 0.8F, 0.35F} : new float[] {side(i), 0.15F, 0.3F, 0.35F};
	}

	/** Walking and climbing: the knees up and out like a spider's. */
	private static float[] knee(int i) {
		return i < 2 ? new float[] {side(i) * 1.2F, -0.7F, 0.6F, 0.45F} : new float[] {side(i), -0.5F, 0.1F, 0.45F};
	}

	/** The upper pair's elbows riding higher, so the forearms come forward over the shoulders, not through them. */
	private static float[] high(int i) {
		return new float[] {side(i) * 1.2F, -1.2F, 0.7F, 0.4F};
	}

	/** {@link #reach} with the elbow out the way that keeps the arm clear of the body. */
	static void reach(float[] t, int i, Vec3 tip, float open, float maxReach) {
		reach(t, i, tip, open, maxReach, clear(i));
	}

	/**
	 * Arm {@code i} posed to put its claw's tips on {@code tip} (body model space, pixels: +X the wearer's left, +Y down, +Z
	 * back). The elbow goes out {@code way} from the mount ({@link #clear}: a direction, then the share of the distance), the
	 * shoulder strut's rod running out to it; then the forearm's rod runs out to the point (up to {@code maxReach} each). The
	 * arm turns in its socket (yaw, pitch and roll) to point at the elbow with the forearm's bend toward the point.
	 */
	static void reach(float[] t, int i, Vec3 tip, float open, float maxReach, float[] way) {
		float[] m = mount(i);
		Vec3 mount = new Vec3(m[0], m[1], m[2]);
		double far = tip.distanceTo(mount);
		if (far < 1.0E-3) {
			return;
		}
		double strut = Mth.clamp(way[3] * far, UPPER, Math.min(ELBOW_MAX, UPPER + UPPER_ROD * (maxReach - 1.0)));
		// on the reactor, the elbows go out ahead of the chest rather than back past the spine
		Vec3 elbow = mount.add(new Vec3(way[0], way[1], front ? -way[2] : way[2]).normalize().scale(strut));
		// the forearm can't fold shorter than itself: back the elbow off along the line from the point
		double shortest = FORE + CLAW_TIP;
		if (tip.distanceTo(elbow) < shortest) {
			elbow = tip.add(elbow.subtract(tip).normalize().scale(shortest));
		}
		double upperReach = Math.max(1.0, (elbow.distanceTo(mount) - UPPER) / UPPER_ROD + 1.0);
		double foreReach = Mth.clamp((tip.distanceTo(elbow) - shortest) / FORE_ROD + 1.0, 1.0, maxReach);
		Vec3 z = elbow.subtract(mount).normalize(), w = tip.subtract(elbow).normalize();
		// the arm's own frame: z down the strut, y away from the way the forearm bends (the elbow bends about x)
		Vec3 bend = w.subtract(z.scale(w.dot(z)));
		Vec3 y;
		if (bend.lengthSqr() < 1.0E-8) {
			Vec3 q = new Vec3(0.0, -1.0, 0.0).subtract(z.scale(-z.y));
			if (q.lengthSqr() < 1.0E-8) {
				q = new Vec3(1.0, 0.0, 0.0).subtract(z.scale(z.x));
			}
			y = q.normalize();
		} else {
			y = bend.normalize().scale(-1.0);
		}
		Vec3 x = y.cross(z);
		int o = i * VALUES;
		// as a ModelPart turns (z, then y, then x)
		t[o] = (float)Math.atan2(y.z, z.z);
		t[o + 1] = (float)Math.asin(Mth.clamp(-x.z, -1.0, 1.0));
		t[o + 2] = (float)Math.atan2(x.y, x.x);
		t[o + 3] = (float)Math.acos(Mth.clamp(z.dot(w), -1.0, 1.0));
		t[o + 4] = (float)upperReach;
		t[o + 5] = (float)foreReach;
		t[o + 6] = open;
	}

	/** Out and at rest: the upper pair arched over the shoulders, the lower pair down and out. */
	private static void rest(float[] t, float time) {
		float sway = Mth.sin(time * 0.08F) * 0.05F;
		set(t, 0, 0.9F + sway, 0.5F, 1.1F, 1.0F, 0.3F);
		set(t, 1, 0.9F - sway, 0.5F, 1.1F, 1.0F, 0.3F);
		set(t, 2, -0.55F, 0.95F, -0.7F + sway, 1.0F, 0.3F);
		set(t, 3, -0.55F, 0.95F, -0.7F - sway, 1.0F, 0.3F);
	}

	/** Folded all the way into the mounts: drawn down to nothing (see {@link #draw}), so none of it sticks out. */
	private static void folded(float[] t) {
		for (int i = 0; i < 4; i++) {
			set(t, i, -1.45F, 0.12F, 2.7F, 0.0F, 0.0F);
		}
	}

	/** Where arm {@code i} meets the body in the pose being worked out: the spine, or the reactor with the suit hidden. */
	private static float[] mount(int i) {
		return (front ? MOUNT_FRONT : MOUNT)[i];
	}

	/**
	 * A pose set by hand ({@link #set}, pointing back off the spine) turned to point ahead off the reactor: each arm takes
	 * its partner's angles turned half round, which puts its claw where its own would be, mirrored front to back.
	 */
	private static void faceFront(float[] t) {
		float[] c = t.clone();
		for (int i = 0; i < 4; i++) {
			int o = i * VALUES, p = (i ^ 1) * VALUES;
			System.arraycopy(c, p, t, o, VALUES);
			t[o + 1] += Mth.PI;
		}
	}

	/** The Octo Core is hidden (the inventory's eye button): the reactor stays, and the arms come from it. */
	static boolean onReactor(Player player) {
		return RigOcto.wears(player) && HiddenArmor.hidden(player, EquipmentSlot.CHEST);
	}

	/**
	 * Where arm {@code i}'s claw tips are in pose {@code t} (body model space, pixels), so a move can carry the claw from
	 * there to a point in a straight line rather than swinging the joints across.
	 */
	private static Vec3 tip(float[] t, int i) {
		int o = i * VALUES;
		float upperReach = t[o + 4], foreReach = t[o + 5];
		double shrink = Mth.clamp(upperReach, 0.0F, 1.0F);
		double a = (UPPER + UPPER_ROD * (Math.max(1.0F, upperReach) - 1.0)) * shrink;
		double b = (FORE + FORE_ROD * (Math.max(1.0F, foreReach) - 1.0) + CLAW_TIP) * shrink;
		double ca = Math.cos(t[o]), sa = Math.sin(t[o]), cb = Math.cos(t[o + 1]), sb = Math.sin(t[o + 1]);
		double cc = Math.cos(t[o + 2]), sc = Math.sin(t[o + 2]), bend = t[o + 3];
		Vec3 y = new Vec3(cc * sb * sa - sc * ca, sc * sb * sa + cc * ca, cb * sa);
		Vec3 z = new Vec3(cc * sb * ca + sc * sa, sc * sb * ca - cc * sa, cb * ca);
		Vec3 w = z.scale(Math.cos(bend)).subtract(y.scale(Math.sin(bend)));
		float[] m = mount(i);
		return new Vec3(m[0], m[1], m[2]).add(z.scale(a)).add(w.scale(b));
	}

	/** {@code player} is you, seen from your own eyes. */
	private static boolean ownView(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		return player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
	}

	/** A point in the world, in the body's model space (pixels: +X the wearer's left, +Y down from the neck, +Z back). */
	private static Vec3 model(Player player, float partial, Vec3 world) {
		double yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 left = new Vec3(ahead.z, 0.0, -ahead.x);
		Vec3 c = world.subtract(neck(player, partial));
		double px = 16.0 / (player.getScale() * MODEL_SCALE);
		return new Vec3(c.dot(left) * px, -c.y * px, -c.dot(ahead) * px);
	}

	/** Where the body model's origin (the neck) is in the world, as the avatar renderer places it. */
	private static Vec3 neck(Player player, float partial) {
		double scale = player.getScale();
		double crouch = player.isCrouching() ? CROUCH_DROP * scale : 0.0;
		return player.getPosition(partial).add(0.0, MODEL_LIFT * MODEL_SCALE * scale + crouch, 0.0);
	}

	/** Where a punch lands: the first living thing down the look within the combo's reach, else a little way out. */
	private static Vec3 punchAt(Player player, float partial) {
		Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
		Vec3 end = eye.add(look.scale(RigOcto.COMBO_REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, RigOcto.COMBO_REACH * RigOcto.COMBO_REACH);
		return hit != null ? hit.getLocation() : eye.add(look.scale(PUNCH_EMPTY));
	}

	/** Where an anchor bites: the block or creature down the look, else as far as it reaches. */
	private static Vec3 anchorAt(Player player, float partial) {
		Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
		Vec3 end = eye.add(look.scale(RigOcto.ANCHOR_RANGE));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && e != player, eye.distanceToSqr(end));
		return hit != null ? hit.getEntity().getBoundingBox().getCenter() : end;
	}

	/** {@link #target}'s answer: a claw is placed somewhere (the arms ease faster), and the pose is taken as it is (no ease). */
	private static final int PLACED = 1, SNAP = 2;

	/** Arm {@code i}'s claw on a point (body model space), telescoping as far as it takes. */
	private static int at(float[] t, int i, Vec3 point, float open) {
		reach(t, i, point, open, EXT_FAR);
		return PLACED;
	}

	private static Vec3 v(double x, double y, double z) {
		return new Vec3(x, y, z);
	}

	/** What the arms want to look like on {@code player} now; returns {@link #PLACED} and {@link #SNAP}. */
	private static int target(Player player, float partial, float[] t, float now) {
		rest(t, now);
		if (front) {
			faceFront(t);
		}
		RigMoves.Move move = RigMoves.get(player);
		int held = RigOcto.heldId(player);
		int kind = 0;
		float k = 0.0F;
		if (move != null) {
			float len = Math.max(1, RigMoves.length(move.kind()));
			float age = now - move.start();
			if (age >= 0.0F && age < len) {
				kind = move.kind();
				k = Mth.clamp(age / len, 0.0F, 1.0F);
			}
		}
		int flags = 0;
		// R and G's grips and tears (the tendrils' own, worked by the upper arms)
		boolean machine = WebTendrils.machine(player);
		WebTendrils.Grips grips = machine ? player.getAttached(WebTendrils.GRIPS) : null;
		WebTendrils.Latch tug = machine ? player.getAttached(WebTendrils.TUG) : null;
		boolean gripping = grips != null && (grips.right() >= 0 || grips.left() >= 0) || tug != null;
		// walking or climbing: the claws on whatever they planted on (the upper pair stays free while a move needs it)
		Vec3[] claws = RigOctoWalk.claws(player, partial, held >= 0 || gripping || kind != 0 && kind != RigOcto.MOVE_WATCH);
		if (claws != null) {
			for (int i = 0; i < 4; i++) {
				if (claws[i] != null) {
					reach(t, i, model(player, partial, claws[i]), 0.15F, EXT_WALK, knee(i));
					flags |= PLACED;
				}
			}
		}
		float[] base = t.clone();
		Entity hold = held >= 0 ? player.level().getEntity(held) : null;
		if (hold != null) {
			// the upper claws on either side of it, telescoping out to it wherever it is
			boolean reaching = kind == RigOcto.MOVE_GRAB && k < 0.5F;
			Vec3 point = model(player, partial, hold.getBoundingBox().getCenter());
			double side = (hold.getBbWidth() * 0.5 + 0.1) * 16.0 / player.getScale();
			flags |= at(t, 0, point.add(-side, 0.0, 0.0), reaching ? 0.9F : 0.0F);
			flags |= at(t, 1, point.add(side, 0.0, 0.0), reaching ? 0.9F : 0.0F);
		} else if (kind == 0 && held < 0 && !gripping && ownView(player)) {
			// your own first person, at rest: the upper claws hover at the top corners of your view, so you see they're out
			float bob = Mth.sin(now * 0.08F) * 0.6F;
			for (int i = 0; i < 2; i++) {
				if (claws == null || claws[i] == null) {
					reach(t, i, v(side(i) * FP_HOVER[0], FP_HOVER[1] + (i == 0 ? bob : -bob), FP_HOVER[2]), 0.3F, EXT_FAR);
				}
			}
		} else if (held >= 0 || kind == RigOcto.MOVE_GRAB || kind == RigOcto.MOVE_HOLD) {
			// reaching out ahead, then drawn in close
			boolean reaching = kind == RigOcto.MOVE_GRAB && k < 0.5F;
			for (int i = 0; i < 2; i++) {
				flags |= at(t, i, v(side(i) * 3.0F, -2.0, reaching ? -30.0 : -20.0), reaching ? 0.9F : 0.0F);
			}
		}
		if (gripping) {
			// each upper claw closed on what its key holds (R the right, G the left), or dug into the wall it's tearing at
			for (int hand = 0; hand < 2; hand++) {
				Entity e = grips == null || grips.get(hand) < 0 ? null : player.level().getEntity(grips.get(hand));
				if (e != null) {
					// on its near side, so the claw closes on it rather than inside it
					Vec3 centre = e.getBoundingBox().getCenter();
					Vec3 toward = player.getEyePosition(partial).subtract(centre);
					toward = toward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toward.normalize().scale(e.getBbWidth() * 0.5);
					flags |= at(t, hand, model(player, partial, centre.add(toward)), 0.1F);
				} else if (tug != null && tug.has(hand)) {
					flags |= at(t, hand, model(player, partial, tug.get(hand)), 0.8F);
				}
			}
			WebTendrils.Combo combo = player.getAttached(WebTendrils.COMBO);
			Entity pinned = combo != null && combo.kind() == WebTendrilCombos.BARRAGE ? player.level().getEntity(combo.first()) : null;
			if (pinned != null) {
				// the barrage: the lower pair stabs the pinned creature over and over, one then the other
				float every = Math.max(1, WebTendrilCombos.barrageEvery(player));
				Vec3 point = model(player, partial, pinned.getBoundingBox().getCenter());
				for (int i = 2; i < 4; i++) {
					float s = ((now - combo.start()) / every + (i == 3 ? 0.5F : 0.0F)) % 1.0F;
					flags |= at(t, i, tip(base, i).lerp(point, bump(s)), 0.0F);
				}
			}
		}
		if (kind != RigOcto.MOVE_ANCHOR) {
			ANCHORS.remove(player.getUUID());
		}
		switch (kind) {
			case RigOcto.MOVE_HURL -> {
				// snapped forward and up, claws open, the rods run out with the throw
				for (int i = 0; i < 2; i++) {
					flags |= at(t, i, v(side(i) * 4.0F, -6.0, -34.0), 1.0F);
				}
			}
			case RigOcto.MOVE_SKEWER -> {
				// the lower pair stabs ahead, then lifts what it ran through overhead and brings it down ahead
				for (int i = 2; i < 4; i++) {
					float s = side(i);
					Vec3 stab = v(s * 5.0F, 10.0, -30.0), lift = v(s * 5.0F, -24.0, -14.0), down = v(s * 5.0F, 26.0, -26.0);
					Vec3 point = k < 0.25F ? tip(base, i).lerp(stab, k / 0.25F) : k < 0.65F ? stab.lerp(lift, (k - 0.25F) / 0.4F)
						: lift.lerp(down, (k - 0.65F) / 0.35F);
					flags |= at(t, i, point, 0.0F);
				}
			}
			case RigOcto.MOVE_PINWHEEL -> {
				// all four flat out in a wheel behind the back, spinning (posed as it turns, not eased)
				float spin = (now - move.start()) * SPIN_RATE;
				float[] start = {-0.75F * Mth.PI, -0.25F * Mth.PI, 0.75F * Mth.PI, 0.25F * Mth.PI};
				for (int i = 0; i < 4; i++) {
					float c = Mth.cos(start[i] + spin), s = Mth.sin(start[i] + spin);
					// (behind the back off the spine, ahead of the chest off the reactor)
					reach(t, i, v(c * 18.0F, 6.0F + s * 18.0F, front ? -10.0 : 10.0), 0.8F, EXT_FAR, new float[] {c, s, 1.2F, 0.5F});
				}
				flags |= PLACED | SNAP;
			}
			case RigOcto.MOVE_VAULT -> {
				// the lower pair planted behind and below, pushing
				for (int i = 2; i < 4; i++) {
					flags |= at(t, i, v(side(i) * 8.0F, 30.0, 20.0), 0.6F);
				}
			}
			case RigOcto.MOVE_DIVE -> {
				// all four point down at what's below
				for (int i = 0; i < 4; i++) {
					flags |= at(t, i, i < 2 ? v(side(i) * 12.0F, 38.0, -6.0) : v(side(i) * 11.0F, 40.0, 6.0), 0.0F);
				}
			}
			case RigOcto.MOVE_ANCHOR -> {
				// the upper right arm telescopes out to where it bit and stays on it while it hauls
				Anchor anchor = ANCHORS.get(player.getUUID());
				if (anchor == null || anchor.start() != move.start()) {
					anchor = new Anchor(move.start(), anchorAt(player, partial));
					ANCHORS.put(player.getUUID(), anchor);
				}
				// shot straight out from where the claw was
				flags |= at(t, 0, tip(base, 0).lerp(model(player, partial, anchor.at()), Math.min(1.0F, k / 0.25F)), 0.0F);
			}
			case RigOcto.MOVE_LANCE -> flags |= at(t, 0, v(-3.0, -4.0, -14.0 - bump(k) * 20.0F), 0.0F);
			case RigOcto.MOVE_CRESCENT -> {
				// the upper left arm sweeps across ahead of the face, far enough out that the forearm passes clear of it
				float f = Mth.lerp(k, 1.1F, -1.1F);
				flags |= at(t, 1, v(Mth.sin(f) * 13.0F, -4.0, -28.0F - Mth.cos(f) * 3.0F), 0.6F);
			}
			case RigOcto.MOVE_CLAMP -> {
				for (int i = 0; i < 2; i++) {
					flags |= at(t, i, v(side(i) * 3.0F, -2.0, -22.0), 1.0F - k);
				}
			}
			case RigOcto.MOVE_LAUNCH -> {
				for (int i = 2; i < 4; i++) {
					flags |= at(t, i, v(side(i) * 5.0F, 22.0, -16.0).lerp(v(side(i) * 5.0F, -10.0, -22.0), bump(k)), 0.2F);
				}
			}
			case RigOcto.MOVE_JAB, RigOcto.MOVE_CROSS -> {
				// a telescoping punch out to the first thing down the look, and back: the right arm jabs, the left crosses
				// (straight out from where the claw was, as a jab goes, not swung round)
				int arm = kind == RigOcto.MOVE_JAB ? 0 : 1;
				flags |= at(t, arm, tip(base, arm).lerp(model(player, partial, punchAt(player, partial)), punch(k)), 0.0F);
				// the other arm guards, drawn in close over the shoulder
				flags |= at(t, 1 - arm, v(side(1 - arm) * 6.0F, -6.0, -8.0), 0.0F);
			}
			case RigOcto.MOVE_BLAST -> {
				// all four claws close on one point ahead of the chest, open, and tremble as it builds
				Vec3 eye = player.getEyePosition(partial), look = player.getViewVector(partial);
				Vec3 focus = model(player, partial, eye.add(look.scale(1.6)).subtract(0.0, 0.3, 0.0));
				float shake = k < 0.5F ? Mth.sin(now * 3.1F) * 0.35F : 0.0F;
				for (int i = 0; i < 4; i++) {
					double sx = side(i) * 1.2 + shake, sy = i < 2 ? -1.0 : 1.0;
					Vec3 point = tip(base, i).lerp(focus.add(sx, sy, 0.0), Math.min(1.0F, k / 0.2F));
					reach(t, i, point, k < 0.5F ? 1.0F : 0.5F, EXT_FAR, i < 2 ? high(i) : clear(i));
				}
				flags |= PLACED;
			}
			case RigOcto.MOVE_WATCH -> {
				// one arm darts out, a different one each time
				int i = (int)(move.start() % 4L);
				Vec3 point = i < 2 ? v(side(i) * 3.0F, -4.0, -14.0 - bump(k) * 20.0F) : v(side(i) * 5.0F, 14.0, -12.0 - bump(k) * 18.0F);
				flags |= at(t, i, point, 0.0F);
			}
			default -> {
			}
		}
		return flags;
	}

	/** Out and back over a move (0..1..0). */
	private static float bump(float k) {
		return k < 0.35F ? k / 0.35F : 1.0F - (k - 0.35F) / 0.65F;
	}

	/** A punch: out fast, held a moment, back (0..1..0). */
	private static float punch(float k) {
		return k < 0.25F ? k / 0.25F : k < 0.5F ? 1.0F : 1.0F - (k - 0.5F) / 0.5F;
	}

	// ---- render -----------------------------------------------------------------------------------------------------------

	/** (From {@link RigSuit#extract}) what the arms are doing on {@code player} this frame. */
	static void extract(Player player, AvatarRenderState state, float partialTicks) {
		state.setData(ARMS, pose(player, partialTicks));
	}

	/** The arms' pose on {@code player} this frame, eased on from the last; null when they aren't worn. */
	private static float @Nullable [] pose(Player player, float partialTicks) {
		UUID id = player.getUUID();
		if (!RigOcto.wears(player)) {
			POSE.remove(id);
			UNFOLD.remove(id);
			ANCHORS.remove(id);
			FRONT.remove(id);
			return null;
		}
		boolean reactor = onReactor(player);
		Boolean wasReactor = FRONT.put(id, reactor);
		front = reactor;
		try {
			float[] pose = pose(player, partialTicks, id, wasReactor != null && wasReactor != reactor);
			// the last value says which mount it's drawn from
			float[] out = java.util.Arrays.copyOf(pose, pose.length + 1);
			out[pose.length] = reactor ? 1.0F : 0.0F;
			return out;
		} finally {
			front = false;
		}
	}

	private static float[] pose(Player player, float partialTicks, UUID id, boolean moved) {
		float now = player.level().getGameTime() + partialTicks;
		boolean out = RigOcto.out(player);
		float unfold = UNFOLD.getOrDefault(id, 0.0F);
		unfold += ((out ? 1.0F : 0.0F) - unfold) * UNFOLD_EASE;
		UNFOLD.put(id, unfold);
		float[] want = new float[4 * VALUES];
		float[] fold = new float[4 * VALUES];
		int flags = 0;
		if (out) {
			flags = target(player, partialTicks, want, now);
		} else {
			rest(want, now);
			if (front) {
				faceFront(want);
			}
		}
		folded(fold);
		if (front) {
			faceFront(fold);
		}
		for (int i = 0; i < want.length; i++) {
			// the three turns of the socket go the short way round
			want[i] = i % VALUES < 3 ? fold[i] + turn(fold[i], want[i]) * unfold : Mth.lerp(unfold, fold[i], want[i]);
		}
		float[] pose = POSE.get(id);
		if (pose == null || (flags & SNAP) != 0 || moved) {
			pose = want.clone();
			POSE.put(id, pose);
		} else {
			float ease = (flags & PLACED) != 0 ? EASE_PLACED : EASE;
			for (int i = 0; i < pose.length; i++) {
				pose[i] += (i % VALUES < 3 ? turn(pose[i], want[i]) : want[i] - pose[i]) * ease;
			}
		}
		return pose.clone();
	}

	/** The short way round from angle {@code from} to {@code to} (radians). */
	private static float turn(float from, float to) {
		return Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
	}

	/** (From {@link RigSuit}'s layer) draws the arms on the body bone. */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel parent) {
		float[] p = state.getData(ARMS);
		if (p == null) {
			return;
		}
		poseStack.pushPose();
		parent.body.translateAndRotate(poseStack);
		// painted at the Forge: the arms take the trim's paint (else the Core's plates'), and the lights' colour
		Integer trim = state.getData(RigSuit.TRIM);
		int[] plates = state.getData(RigSuit.COLORS);
		int paint = trim != null ? trim : plates != null ? plates[Rig.Piece.CORE.ordinal()] : -1;
		Integer lights = state.getData(RigSuit.LIGHTS);
		draw(poseStack, collector, light, p, paint, lights == null ? -1 : lights);
		poseStack.popPose();
	}

	/** The arms in pose {@code p}, from the body's model space, in {@code paint} with lights in {@code lights} (-1 for their own). */
	private static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, float[] p, int paint, int lights) {
		bake();
		float[][] mounts = p.length > 4 * VALUES && p[4 * VALUES] > 0.5F ? MOUNT_FRONT : MOUNT;
		for (int i = 0; i < 4; i++) {
			int o = i * VALUES;
			ModelPart arm = ARM[i];
			arm.x = mounts[i][0];
			arm.y = mounts[i][1];
			arm.z = mounts[i][2];
			// a part points back out of the spine (+z), turned in its socket
			arm.xRot = p[o];
			arm.yRot = p[o + 1];
			arm.zRot = p[o + 2];
			float upperReach = p[o + 4], foreReach = p[o + 5];
			// short of full length the whole arm draws into its mount (folded, down to nothing); past it the rods slide out of each strut
			float size = Mth.clamp(upperReach, 0.0F, 1.0F);
			arm.visible = size > 0.02F;
			arm.xScale = size;
			arm.yScale = size;
			arm.zScale = size;
			float upper = Math.max(1.0F, upperReach), fore = Math.max(1.0F, foreReach);
			UPPER_RODS[i].zScale = upper;
			FORE_PART[i].z = UPPER + UPPER_ROD * (upper - 1.0F);
			FORE_PART[i].xRot = p[o + 3];
			FORE_RODS[i].zScale = fore;
			CLAW[i].z = FORE + FORE_ROD * (fore - 1.0F);
			float open = Mth.clamp(p[o + 6], 0.0F, 1.0F) * 0.6F;
			PRONG_A[i].xRot = -open;
			PRONG_B[i].xRot = open * 0.5F;
			PRONG_B[i].yRot = -open;
			PRONG_C[i].xRot = open * 0.5F;
			PRONG_C[i].yRot = open;
		}
		collector.submitModelPart(root, poseStack, RenderTypes.entityCutout(paint < 0 ? TEXTURE : PAINT), light, OverlayTexture.NO_OVERLAY, null,
			RigSuit.tint(paint));
		collector.submitModelPart(root, poseStack, RenderTypes.eyes(lights < 0 ? GLOW : LIGHTS), light, OverlayTexture.NO_OVERLAY, null,
			lights < 0 ? 0xFFFFFFFF : RigSuit.tint(lights));
	}

	/**
	 * Your own arms in first person (Andy's pick: "First-person arms", high priority): the game doesn't draw your body from
	 * your eyes, so the arms are drawn here, in the world where your body would be, posed just as everyone else sees them.
	 * So they come over your shoulders into view, a jab's claw lands on what it hits, and a climb's claws sit on the wall.
	 * With First-person Model drawing your body, its layer already draws them.
	 */
	private static void firstPerson(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !minecraft.options.getCameraType().isFirstPerson() || player.isInvisible() || player.isSpectator()
			|| FirstPersonCompat.bodyShown()) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float[] p = pose(player, partial);
		if (p == null) {
			return;
		}
		int paint = -1, lights = -1;
		for (Rig.Piece piece : Rig.Piece.values()) {
			if (piece.item() == null) {
				continue;
			}
			ItemStack stack = player.getItemBySlot(piece.slot);
			if (!stack.is(piece.item())) {
				continue;
			}
			// as the suit takes them (RigSuit.extract): the Core's first, else any piece's
			Integer trim = stack.get(Rig.TRIM);
			if (trim != null && (paint < 0 || piece == Rig.Piece.CORE)) {
				paint = trim & 0xFFFFFF;
			}
			Integer lit = stack.get(Rig.LIGHTS);
			if (lit != null && (lights < 0 || piece == Rig.Piece.CORE)) {
				lights = lit & 0xFFFFFF;
			}
		}
		if (paint < 0) {
			DyedItemColor dye = player.getItemBySlot(Rig.Piece.CORE.slot).get(DataComponents.DYED_COLOR);
			paint = dye == null ? -1 : dye.rgb() & 0xFFFFFF;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 at = neck(player, partial).subtract(camera);
		float scale = (float)(player.getScale() * MODEL_SCALE);
		BlockPos pos = BlockPos.containing(player.getEyePosition(partial));
		int light = LightCoordsUtil.pack(player.level().getBrightness(LightLayer.BLOCK, pos), player.level().getBrightness(LightLayer.SKY, pos));
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		// as the avatar renderer turns and flips a player's model, from the neck
		poseStack.translate(at.x, at.y, at.z);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot)));
		poseStack.scale(-scale, -scale, scale);
		draw(poseStack, context.submitNodeCollector(), light, p, paint, lights);
		poseStack.popPose();
	}

	// ---- R and G ------------------------------------------------------------------------------------------------------------

	/** R and G on the upper arms: whether each key is the arms' now, whether it was down last tick, and which arm holds a grab. */
	private static final boolean[] KEY_HELD = new boolean[2], KEY_WAS = new boolean[2], GRABBING = new boolean[2];

	/** Whether R and G are the arms' for {@code player}: the arms out, and no symbiote tendrils out to take them. */
	static boolean armKeys(Player player) {
		return WebTendrils.machine(player);
	}

	/** Whether {@code key} is held by the arms right now (so the Rig's own use of it, with no power, stays quiet). */
	public static boolean armKey(Player player, PowerKey key) {
		int i = key == PowerKey.ABILITY_1 ? 0 : key == PowerKey.ABILITY_2 ? 1 : -1;
		return i >= 0 && (KEY_HELD[i] || armKeys(player));
	}

	/**
	 * From the power keys' tick, before they're reported (as the symbiote host's tendril keys): with the arms out, R is the
	 * upper right arm and G the upper left, as the tendrils' keys (Andy: the arms' controls "should be the same as the
	 * tendrils"). Held on something grabbable, the arm grabs it, and letting go hurls it; Sneak at a block rips a chunk out.
	 * {@code physical} is whether the key is down, {@code powerHeld} whether the wearer's own power already holds it.
	 * Returns whether the arms hold it now (then the power is told it's up).
	 */
	public static boolean routeKey(LocalPlayer player, PowerKey key, boolean physical, boolean powerHeld) {
		if (key != PowerKey.ABILITY_1 && key != PowerKey.ABILITY_2) {
			return false;
		}
		int i = key == PowerKey.ABILITY_1 ? 0 : 1;
		boolean pressed = physical && !KEY_WAS[i];
		KEY_WAS[i] = physical;
		if (KEY_HELD[i]) {
			if (!physical) {
				KEY_HELD[i] = false;
				grabKey(player, i, false);
			}
			return KEY_HELD[i];
		}
		if (pressed && !powerHeld && !player.isSpectator() && armKeys(player)) {
			KEY_HELD[i] = true;
			grabKey(player, i, true);
		}
		return KEY_HELD[i];
	}

	/** One upper arm's key pressed or let go: the tendrils' grab, sent for that arm (WebNet's MAIN is R, OFF is G). */
	private static void grabKey(LocalPlayer player, int hand, boolean down) {
		if (!ClientPlayNetworking.canSend(WebTendrils.Grab.TYPE)) {
			return;
		}
		if (!down) {
			if (GRABBING[hand]) {
				// let go: what it holds is hurled down the look
				GRABBING[hand] = false;
				ClientPlayNetworking.send(new WebTendrils.Grab(hand, -1));
			}
			return;
		}
		if (RigOcto.heldId(player) >= 0) {
			// the arms already carry someone (the Rig menu's grab): that's their hold
			return;
		}
		Entity target = WebPowerClient.tendrilTarget(player);
		if (target != null) {
			GRABBING[hand] = true;
			ClientPlayNetworking.send(new WebTendrils.Grab(hand, target.getId()));
		} else if (player.isShiftKeyDown() && WebPowerClient.tendrilBlock(player)) {
			// sneaking at a wall or the ground: the claw rips a chunk of it loose
			GRABBING[hand] = true;
			ClientPlayNetworking.send(new WebTendrils.Grab(hand, WebTendrils.GRAB_BLOCK));
		}
	}

	// ---- the crawl and the walk ---------------------------------------------------------------------------------------------

	/** The crawl, on the wearer's own client: on a wall, on a ceiling, ticks left of a slam's pin and of a leap's flight, the coil's ticks. */
	private static boolean wallClinging, ceilingClinging, sneakWas;
	private static int latchHold, leapGrace, coil;

	/**
	 * On the wearer's own client, with the arm walk on: the arms' Wall Crawl, as the symbiote's (Andy: "the same as the
	 * tendrils"). Against a wall, forward or Jump climbs, Sneak or back goes down and strafing goes along it; Jump under a
	 * ceiling (or a Sneak press under it) takes you up onto it, where the movement keys crawl you about upside down, and
	 * another Sneak press drops you. A Sneak press at a wall slams all four into it. Over the top of a wall, a hop onto the
	 * ledge. Anywhere else the stride, and the coiled leap. Water doesn't stop them.
	 */
	private static void move(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.isPaused()) {
			return;
		}
		if (leapGrace > 0) {
			// just flung off the arms: they let you fly
			leapGrace--;
			letGo();
			return;
		}
		if (!player.isAlive() || player.isSpectator() || player.getAbilities().flying || player.isPassenger() || player.isFallFlying()
			|| player.isInLava() || !RigOcto.walking(player)) {
			letGo();
			sneakWas = false;
			return;
		}
		AABB box = player.getBoundingBox();
		boolean wall = !player.level().noCollision(player, box.inflate(0.12, -0.1, 0.12));
		boolean ceiling = !player.onGround() && !player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05));
		Input input = player.input.keyPresses;
		Vec3 move = wish(player, input);
		boolean sneakPressed = input.shift() && !sneakWas;
		sneakWas = input.shift();
		if (ceilingClinging && sneakPressed && latchHold <= 0) {
			// Sneak again on the ceiling: they let go and you drop
			letGo();
			return;
		}
		if (sneakPressed && !ceilingClinging && !wallClinging) {
			// Sneak by a ceiling (looking up, or with no wall to hand): slammed up into it, just as into a wall
			double reach = (player.onGround() ? CEILING_LATCH_GROUND : CEILING_LATCH_REACH) * player.getScale();
			Vec3 overhead = ceilingOver(player, reach);
			if (overhead != null && (!wall || player.getXRot() < -CEILING_LATCH_LOOK)) {
				slamCeiling(player, overhead);
				return;
			}
		}
		if (latchHold > 0) {
			latchHold--;
		}
		if (ceilingClinging && !ceiling && latchHold > 0) {
			// slammed in, still flying up to it
			Vec3 v = player.getDeltaMovement();
			player.setDeltaMovement(v.x * 0.5, Math.max(v.y, LATCH_SLAM), v.z * 0.5);
			player.resetFallDistance();
			return;
		}
		if (ceiling && (input.jump() && !input.shift() || ceilingClinging)) {
			// on the ceiling: crawled about upside down (pinned still just after a slam)
			ceilingClinging = true;
			wallClinging = false;
			coil = 0;
			crawl(player, latchHold > 0 ? Vec3.ZERO : move, new Vec3(0.0, -1.0, 0.0));
			return;
		}
		ceilingClinging = false;
		if (wall && sneakPressed && !wallClinging) {
			slamWall(player);
			return;
		}
		if (wall && (!player.onGround() || input.forward() && player.horizontalCollision || latchHold > 0)) {
			Vec3 out = wallNormal(player);
			// the way you want to go along the wall: up or down, and sideways (pinned still just after a slam)
			double vertical = latchHold > 0 ? 0.0 : (input.jump() || input.forward() ? 1.0 : 0.0) - (input.shift() || input.backward() ? 1.0 : 0.0);
			Vec3 along = out.cross(new Vec3(0.0, 1.0, 0.0));
			along = along.lengthSqr() < 1.0E-4 ? Vec3.ZERO : along.normalize();
			Vec3 want = new Vec3(0.0, vertical, 0.0).add(along.scale(latchHold > 0 ? 0.0 : move.dot(along)));
			wallClinging = true;
			coil = 0;
			crawl(player, want, out);
			return;
		}
		boolean wasClinging = wallClinging;
		if (wallClinging && !player.onGround() && player.getDeltaMovement().y > 0.0) {
			// over the top: a hop onto the ledge
			Vec3 forward = player.getLookAngle().horizontal();
			forward = forward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : forward.normalize();
			player.setDeltaMovement(forward.x * LEDGE_FORWARD, LEDGE_HOP, forward.z * LEDGE_FORWARD);
		}
		wallClinging = false;
		if (!wasClinging) {
			stride(player, input, move);
		}
	}

	private static void letGo() {
		wallClinging = false;
		ceilingClinging = false;
		latchHold = 0;
		coil = 0;
	}

	/**
	 * One tick of the crawl: the arms haul you along the surface facing {@code out} toward {@code want} (zero: you hang
	 * where you are), at {@link #CRAWL_SPEED}, held to the wall or up into the ceiling.
	 */
	private static void crawl(LocalPlayer player, Vec3 want, Vec3 out) {
		boolean ceiling = out.y < -0.5;
		Vec3 go = want.lengthSqr() < 1.0E-4 ? Vec3.ZERO : want.normalize().scale(CRAWL_SPEED * player.getScale());
		Vec3 v = player.getDeltaMovement();
		Vec3 next = new Vec3(Mth.lerp(CRAWL_GRIP, v.x, go.x), Mth.lerp(CRAWL_GRIP, v.y, go.y), Mth.lerp(CRAWL_GRIP, v.z, go.z));
		if (ceiling) {
			next = new Vec3(next.x, CEILING_PRESS, next.z);
		} else {
			next = next.subtract(out.scale(WALL_PRESS));
		}
		player.setDeltaMovement(next);
		player.resetFallDistance();
	}

	/** Sneak at a wall: all four slam into it at once, throwing you against it, and you hang there. */
	private static void slamWall(LocalPlayer player) {
		Vec3 out = wallNormal(player);
		Vec3 center = player.getBoundingBox().getCenter();
		BlockHitResult hit = player.level().clip(new ClipContext(center, center.subtract(out.scale(1.5)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : center.subtract(out.scale(0.4));
		wallClinging = true;
		ceilingClinging = false;
		latchHold = LATCH_HOLD_TICKS;
		coil = 0;
		player.setDeltaMovement(out.scale(-LATCH_SLAM).add(0.0, player.onGround() ? LATCH_LIFT : 0.0, 0.0));
		player.resetFallDistance();
		Vec3 side = out.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(0.45, 0.0, 0.0) : side.normalize().scale(0.45);
		slamFx(player, at, side, new Vec3(0.0, 0.45, 0.0), at.subtract(out.scale(0.05)));
	}

	/** Sneak under a ceiling: all four slam up into it at once, yanking you up against it, and you hang there. */
	private static void slamCeiling(LocalPlayer player, Vec3 at) {
		ceilingClinging = true;
		wallClinging = false;
		latchHold = LATCH_HOLD_TICKS;
		coil = 0;
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 0.3, LATCH_SLAM + CEILING_SLAM_EXTRA, v.z * 0.3);
		player.resetFallDistance();
		Vec3 ahead = player.getLookAngle().horizontal();
		ahead = ahead.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : ahead.normalize();
		Vec3 side = ahead.cross(new Vec3(0.0, 1.0, 0.0)).normalize().scale(0.45);
		slamFx(player, at, side, ahead.scale(0.45), at.add(0.0, 0.05, 0.0));
	}

	/** A slam's four claws biting in round {@code at} (a pair each way along {@code side} and {@code up}): cracks and a clank. */
	private static void slamFx(LocalPlayer player, Vec3 at, Vec3 side, Vec3 up, Vec3 block) {
		for (int i = 0; i < 4; i++) {
			Vec3 claw = at.add(side.scale((i & 1) == 0 ? 1.0 : -1.0)).add(up.scale(i < 2 ? 1.0 : -1.0));
			WebCracks.crackAt(player.level(), claw, SLAM_CRACK);
		}
		player.level().playLocalSound(at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.35F, 1.3F, false);
		player.level().playLocalSound(at.x, at.y, at.z, player.level().getBlockState(BlockPos.containing(block)).getSoundType().getHitSound(),
			SoundSource.PLAYERS, 1.0F, 0.6F, false);
	}

	/** The ceiling point straight over your head within {@code reach} (blocks above the top of your head), or null. */
	private static @Nullable Vec3 ceilingOver(LocalPlayer player, double reach) {
		AABB box = player.getBoundingBox();
		Vec3 top = new Vec3(box.getCenter().x, box.maxY - 0.05, box.getCenter().z);
		BlockHitResult hit = player.level().clip(new ClipContext(top, top.add(0.0, reach + 0.05, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.DOWN ? hit.getLocation() : null;
	}

	/** Which way the wall you're on faces (out of it, toward you): the side your box is pressed against, the one you face winning. */
	private static Vec3 wallNormal(LocalPlayer player) {
		AABB box = player.getBoundingBox().deflate(0.0, 0.1, 0.0);
		Vec3 look = player.getLookAngle().horizontal();
		Vec3 best = Vec3.ZERO;
		double bestDot = -2.0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			Vec3 d = dir.getUnitVec3();
			if (!player.level().noCollision(player, box.move(d.scale(0.15)))) {
				double dot = d.dot(look);
				if (dot > bestDot) {
					bestDot = dot;
					best = d.reverse();
				}
			}
		}
		if (best.lengthSqr() < 1.0E-4) {
			best = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, -1.0) : look.normalize().reverse();
		}
		return best;
	}

	/**
	 * Carried on the arm walk, held up off the ground between the arms (not on a wall or a ceiling), on any client: the
	 * body hangs limp between them (WebHangPose).
	 */
	public static boolean carried(Player player) {
		return !RigOcto.clinging(player) && !RigOcto.onCeiling(player) && RigOctoWalk.striding(player);
	}

	/**
	 * On the arm walk, off any wall: held up between the arms and walked along (the symbiote's stride; see
	 * {@link #STRIDE_HEIGHT}). Holding Jump with nothing to climb (or sprinting) coils them low; let go and they fling you
	 * down the look ({@link #STRIDE_COIL}).
	 */
	private static void stride(LocalPlayer player, Input input, Vec3 move) {
		if (input.shift() && player.onGround()) {
			// on your own feet
			coil = 0;
			return;
		}
		double scale = player.getScale();
		int anchors = RigOctoWalk.anchors(player, RigOctoWalk.STRIDE_REACH * scale);
		if (anchors < 2) {
			// nothing to grab: you fall
			coil = 0;
			return;
		}
		double ground = RigOctoWalk.groundBelow(player, RigOctoWalk.STRIDE_GROUND * scale);
		boolean coiling = input.jump() && ground >= 0.0 && (coil > 0 || anchors < STRIDE_RISE_ANCHORS || player.isSprinting());
		double vy;
		if (coiling) {
			// coiling: they sink you low on bent arms, ready to fling you (let go of Jump to leap)
			coil++;
			if (coil == 1) {
				player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS,
					0.5F, 0.8F, false);
			}
			vy = Mth.clamp((STRIDE_HEIGHT * STRIDE_COIL * scale - ground) * STRIDE_LIFT, -STRIDE_CLIMB, STRIDE_CLIMB);
		} else {
			if (coil >= COIL_MIN) {
				leap(player, coil);
				coil = 0;
				return;
			}
			coil = 0;
			if (input.jump()) {
				vy = anchors >= STRIDE_RISE_ANCHORS ? STRIDE_CLIMB : 0.0;
			} else if (input.shift()) {
				vy = -STRIDE_CLIMB;
			} else if (ground >= 0.0) {
				// held up on them, rising and dipping a touch with each stride
				double bob = move.lengthSqr() > 0.0 ? Mth.sin(player.tickCount * 0.45F) * 0.06 : 0.0;
				vy = Mth.clamp((STRIDE_HEIGHT * scale + bob - ground) * STRIDE_LIFT, -STRIDE_CLIMB, STRIDE_CLIMB);
			} else {
				// hanging between whatever they hold
				vy = 0.0;
			}
		}
		Vec3 want = move.scale(coiling ? STRIDE_SPEED * 0.5 : player.isSprinting() ? STRIDE_SPRINT : STRIDE_SPEED);
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(Mth.lerp(STRIDE_GRIP, v.x, want.x), vy, Mth.lerp(STRIDE_GRIP, v.z, want.z));
		player.resetFallDistance();
	}

	/** The coil let go after {@code ticks}: the arms fling you down the look, harder the longer they coiled. */
	private static void leap(LocalPlayer player, int ticks) {
		float k = Mth.clamp((ticks - COIL_MIN) / (float)(COIL_FULL - COIL_MIN), 0.0F, 1.0F);
		Vec3 look = player.getLookAngle();
		Vec3 flat = look.horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		// looking up, more of the fling goes up and less along
		double lift = Math.max(0.0, look.y);
		double along = Mth.lerp(k, LEAP_MIN, LEAP_MAX) * (1.0 - 0.5 * lift);
		double up = Mth.lerp(k, LEAP_UP_MIN, LEAP_UP_MAX) * (1.0 + 0.5 * lift);
		player.setDeltaMovement(flat.x * along, up, flat.z * along);
		player.resetFallDistance();
		leapGrace = LEAP_GRACE_TICKS;
		letGo();
		player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS,
			0.8F, 0.7F + 0.4F * k, false);
	}

	/** The way the movement keys point, flat. */
	private static Vec3 wish(LocalPlayer player, Input input) {
		double forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
		double strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
		if (forward == 0 && strafe == 0) {
			return Vec3.ZERO;
		}
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double sin = Mth.sin(yaw);
		double cos = Mth.cos(yaw);
		return new Vec3(strafe * cos - forward * sin, 0.0, forward * cos + strafe * sin).normalize();
	}
}
