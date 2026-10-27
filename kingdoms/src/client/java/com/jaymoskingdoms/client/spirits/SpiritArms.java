package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.client.ManaClient;
import com.jaymoskingdoms.client.ManaClient.Glow;
import com.jaymoskingdoms.spirits.Poses;
import com.jaymoskingdoms.spirits.Spirit;
import com.jaymoskingdoms.spirits.SpiritSickness;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.Spirits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.245) A mana spirit's arms. Andy: "these spirits should conjure as arms behind you with the same effects as your mana
 * aura making up their entire form", after Nero's Devil Breaker (DMC5) and Doppio's partial manifestation (JoJo), "all
 * different sizes". A render layer over players and over the casting folk (the same two-branch registration as
 * {@link ManaClient}): the wearer's own arm is copied, grown by the spirit's size about the shoulder and pushed behind,
 * then bent by the pose the spirit holds ({@link Poses}, a table of arm angles below) and drawn as the aura's own shells
 * ({@link ManaClient#SHELLS}), tinted by the spirit's colour mixed with mana's blue, at the strength the wearer's glow has
 * ({@link ManaClient#KEY}; no glow, no arms, so only the attuned see them). Each arm is jointed, with each kind's own make
 * and the move's wind-up, snap and fingers ({@link SpiritRig}); a blow trails ghosts and bursts where it lands.
 * <p>
 * The arms ease in over {@link #MANIFEST_TICKS} when the spirit is called and out when it is sent away; a pose is lerped in
 * over {@link #POSE_IN}, held to {@link #POSE_TICKS} and eased back. Wisps rise from the hands. While spirit sickness is on
 * you, a one-arm flicker shows now and then ({@link #FLICKER_MIN}-{@link #FLICKER_MAX} ticks apart, sooner each day), and
 * {@link SpiritHud} tints the screen edge with it.
 */
public final class SpiritArms {
	/** On a being's render state: its spirit's arms this frame, or null for none. */
	static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "jaymoskingdoms:spirit_arms");

	/** Ticks the arms take to appear and to vanish. */
	static final int MANIFEST_TICKS = 10;
	/** A pose is lerped in over {@code POSE_IN} ticks, held until {@code POSE_TICKS} after it was stamped, and lerped out over {@code POSE_OUT}. */
	static final int POSE_IN = 4, POSE_TICKS = 16, POSE_OUT = 6;
	/** Model pixels the arm is pushed behind the shoulder, per unit of size (the spirits that sit on your arm: Grimsby, Thessaly). */
	static final float BEHIND = 2.0F;
	/**
	 * The spirits that float free of your arm (Varro, Ambrose, the Quiet One) hover above and ahead of the shoulder, as a stand
	 * does (Andy, 2026-10-27): model pixels up, ahead and out, per unit of size (the size counted to at most {@code FLOAT_SIZE_CAP},
	 * so the biggest don't float off), and their idle angle (raised forward, a little out).
	 */
	static final float FLOAT_UP = 5.0F, FLOAT_AHEAD = 5.0F, FLOAT_OUT = 1.5F, FLOAT_SIZE_CAP = 1.6F;
	private static final float[] FLOAT_IDLE = {-1.05F, 0.0F, 0.30F};
	/** The arms are the whole form of the spirit: this many times the aura's shell light. */
	static final float ARM_LIGHT = 2.2F;
	/** How much the kind colour shows against mana's blue (0 all blue, 1 all kind). */
	static final float KIND_MIX = 0.5F;
	/** The draw order of the shells, after the aura's own. */
	static final int ORDER = 8;
	/** Idle sway: how far and how fast the floating arms drift. */
	static final float SWAY = 0.07F, SWAY_SPEED = 0.08F;
	/** Wisps: chance each tick, per arm, of one dust mote at the hand; the arms must be this far manifest. */
	static final float WISP_CHANCE = 0.35F, WISP_FROM = 0.8F;
	/** The sickness flicker: ticks between flickers (longest, shortest), how long one shows, how strong the arm is. */
	static final int FLICKER_MIN = 100, FLICKER_MAX = 300, FLICKER_TICKS = 6;
	static final float FLICKER_MANIFEST = 0.4F;
	/** A day of sickness in ticks (the client counts it from when it first sees the sickness), and the sickness's length in days. */
	static final int DAY_TICKS = 24000, SICK_DAYS = 5;

	/**
	 * What one being's spirit looks like this frame: its {@code kind}, how far manifest it is (0-1), the {@code pose} it holds
	 * with that pose's strength ({@code poseWeight} 0-1) and age in ticks, whether it has {@code arms} one or two, and the {@code size} of them as conjured ({@link Spirit#size(SpiritState)}).
	 */
	record Look(int kind, float manifest, int pose, float poseWeight, float poseAge, int arms, float size) {
	}

	/** Arm angle offsets (x, y, z in radians, for the right arm; the left mirrors y and z) added to the wearer's own arm for each pose. */
	private static final float[][] TABLE = new float[Poses.COUNT][];

	static {
		TABLE[Poses.IDLE] = new float[] {0.25F, 0.0F, 0.45F};
		TABLE[Poses.REACH] = new float[] {-1.5F, -0.2F, 0.10F};
		TABLE[Poses.GRAB] = new float[] {-1.2F, -0.5F, 0.20F};
		TABLE[Poses.THROW] = new float[] {-2.6F, 0.0F, 0.10F};
		TABLE[Poses.CATCH] = new float[] {-1.6F, -0.4F, 0.30F};
		TABLE[Poses.SHOVE] = new float[] {-1.5F, 0.0F, 0.00F};
		TABLE[Poses.PIN] = new float[] {-1.3F, -0.3F, 0.15F};
		TABLE[Poses.HOLD] = new float[] {-0.9F, -0.6F, 0.00F};
		TABLE[Poses.SWORD] = new float[] {-1.9F, -0.3F, 0.20F};
		TABLE[Poses.GREATSWORD] = new float[] {-2.4F, 0.0F, 0.35F};
		TABLE[Poses.SPEAR] = new float[] {-1.5F, -0.1F, 0.00F};
		TABLE[Poses.AXE] = new float[] {-2.3F, 0.0F, 0.25F};
		TABLE[Poses.HAMMER] = new float[] {-2.7F, 0.0F, 0.00F};
		TABLE[Poses.FLURRY] = new float[] {-1.4F, -0.2F, 0.20F};
		TABLE[Poses.PARRY] = new float[] {-1.2F, -0.8F, 0.10F};
		TABLE[Poses.SLAM] = new float[] {-2.8F, 0.0F, 0.05F};
		TABLE[Poses.JAVELIN] = new float[] {-2.0F, -0.1F, 0.60F};
		TABLE[Poses.VOLLEY] = new float[] {-1.9F, 0.0F, 0.70F};
		TABLE[Poses.BOMBARD] = new float[] {-2.2F, 0.0F, 0.15F};
		TABLE[Poses.WALL] = new float[] {-1.0F, 0.0F, 0.90F};
		TABLE[Poses.MARK] = new float[] {-1.6F, 0.3F, 0.20F};
		TABLE[Poses.WARD] = new float[] {-1.5F, -0.6F, 0.50F};
		TABLE[Poses.FINISH] = new float[] {-2.5F, 0.0F, 0.10F};
		// the second patch's, tuned to read as the move (x raises the arm forward when negative; y swings it across the body when
		// negative; z spreads it out to the side when positive; see also poseAdjust for the ones that move)
		TABLE[Poses.AEGIS] = new float[] {-1.55F, -0.55F, 0.10F};   // both arms up and forward, meeting: a wall
		TABLE[Poses.BLESS] = new float[] {-3.0F, -0.1F, 0.15F};     // palm held high over the head
		TABLE[Poses.CATCH_ME] = new float[] {0.10F, -0.10F, 1.00F}; // arms down and open wide
		TABLE[Poses.MEND] = new float[] {-0.85F, -1.10F, 0.00F};    // a hand laid across the chest
		TABLE[Poses.ARMOUR] = new float[] {-0.50F, -1.30F, -0.20F}; // wrapped round the body
		TABLE[Poses.RIDE] = new float[] {0.05F, 0.00F, 0.12F};      // straight down, as stilts
		TABLE[Poses.PULL] = new float[] {-1.45F, -0.20F, 0.05F};    // reaching out, then drawn back (poseAdjust)
		TABLE[Poses.STEAL] = new float[] {-1.30F, -0.90F, 0.00F};   // a snatch across the body
		TABLE[Poses.TRAP] = new float[] {-1.20F, -1.00F, 0.20F};    // both hands closing together
		TABLE[Poses.UNLOCK] = new float[] {-1.40F, -0.50F, 0.10F};  // one hand out, turning a key (poseAdjust)
		TABLE[Poses.JUGGLE] = new float[] {-1.30F, -0.20F, 0.30F};  // one up one down, trading (poseAdjust)
		TABLE[Poses.ECHO] = new float[] {-1.80F, -0.10F, 0.20F};    // a blow, and the blow again (poseAdjust)
		TABLE[Poses.MIRROR] = new float[] {-1.20F, 0.70F, 0.60F};   // arms swept out to either side
		TABLE[Poses.STILL] = new float[] {0.10F, 0.00F, 0.20F};     // hanging, calm
		TABLE[Poses.SILENT] = new float[] {-2.40F, -0.20F, 0.05F};  // drawn back, then thrust (poseAdjust)
		TABLE[Poses.SENSE] = new float[] {-0.10F, 0.30F, 1.50F};    // spread wide and still
		TABLE[Poses.CLASH] = new float[] {-1.57F, -0.65F, 0.05F};   // both thrust forward, hands locked at full reach
	}

	/** How far manifest each being's arms are: eased from where they were when the spirit went out or away. */
	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	/** The sickness flicker: ticks left showing, which spirit-to-be, ticks to the next, and ticks the sickness has been seen. */
	private static int flickerLeft, flickerKind, nextFlicker, sickTicks;

	private SpiritArms() {
	}

	private static final class Track {
		boolean out;
		float from;
		double since;
		/** The show whose blow last burst, so each blow bursts once. */
		long burst;

		float value(double now) {
			float k = Mth.clamp((float)(now - this.since) / MANIFEST_TICKS, 0.0F, 1.0F);
			k = k * k * (3.0F - 2.0F * k);
			return this.from + ((this.out ? 1.0F : 0.0F) - this.from) * k;
		}
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Player(avatar));
			} else if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(folk(renderer));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(SpiritArms::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TRACKS.clear();
			flickerLeft = 0;
			sickTicks = 0;
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Folk<?> folk(RenderLayerParent<?, ?> renderer) {
		return new Folk((RenderLayerParent)renderer);
	}

	/** Whether the sickness flicker is showing now, and the colour of the spirit-to-be (for {@link SpiritHud}); else -1. */
	static int flickering() {
		return flickerLeft > 0 ? Spirits.of(flickerKind).color() : -1;
	}

	// ---- the state of the arms ---------------------------------------------------------------------------------------------

	/** From the living renderer's extractRenderState (called by {@link ManaClient#extract}): this being's arms, if it has a spirit. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(LOOK, look(entity));
	}

	private static @Nullable Look look(LivingEntity entity) {
		SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
		Minecraft minecraft = Minecraft.getInstance();
		double now = entity.level().getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (spirit == null) {
			TRACKS.remove(entity.getId());
			if (flickerLeft > 0 && entity == minecraft.player) {
				return new Look(flickerKind, FLICKER_MANIFEST, Poses.IDLE, 0.0F, 0.0F, 1, Spirits.of(flickerKind).size());
			}
			return null;
		}
		Track track = TRACKS.get(entity.getId());
		if (track == null) {
			track = new Track();
			track.since = now;
			TRACKS.put(entity.getId(), track);
		}
		if (track.out != spirit.out()) {
			track.from = track.value(now);
			track.out = spirit.out();
			track.since = now;
		}
		float manifest = track.value(now);
		if (manifest <= 0.001F && !track.out) {
			TRACKS.remove(entity.getId());
			return null;
		}
		int pose = spirit.pose();
		float age = (float)(now - spirit.shownAt());
		float weight = 0.0F;
		if (spirit.show() != 0L && pose > Poses.IDLE && pose < Poses.COUNT && age >= 0.0F) {
			weight = age < POSE_TICKS ? Math.min(1.0F, age / POSE_IN) : 1.0F - (age - POSE_TICKS) / POSE_OUT;
			weight = Mth.clamp(weight, 0.0F, 1.0F);
		}
		return new Look(spirit.kind(), manifest, pose, weight, age, spirit.arms(), Spirits.of(spirit.kind()).size(spirit));
	}

	/** Each client tick: the sickness flicker's timing, and wisps rising from the hands of every spirit in view. */
	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		SpiritSickness.State sick = player.getAttached(Spirits.SICK);
		if (sick == null) {
			flickerLeft = 0;
			sickTicks = 0;
			nextFlicker = 0;
		} else {
			sickTicks++;
			flickerKind = sick.kind();
			if (flickerLeft > 0) {
				flickerLeft--;
			} else if (--nextFlicker <= 0) {
				flickerLeft = FLICKER_TICKS;
				// every day of the sickness the flickers come sooner
				float sooner = 1.0F - 0.6F * Math.min(sickTicks / (float)DAY_TICKS, SICK_DAYS - 1.0F) / (SICK_DAYS - 1.0F);
				nextFlicker = (int)((FLICKER_MIN + player.getRandom().nextInt(FLICKER_MAX - FLICKER_MIN + 1)) * sooner);
			}
		}
		if (minecraft.isPaused() || TRACKS.isEmpty() && flickerLeft <= 0) {
			return;
		}
		if (level.getGameTime() % 100L == 0L) {
			// (whoever left the world, or was unloaded, with arms out)
			TRACKS.keySet().removeIf(id -> level.getEntity(id) == null);
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && living.distanceToSqr(player) < 32.0 * 32.0) {
				wisp(level, living);
				burst(level, living);
			}
		}
	}

	private static void wisp(ClientLevel level, LivingEntity entity) {
		SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
		int kind;
		int arms;
		float manifest;
		float size;
		if (spirit != null) {
			Track track = TRACKS.get(entity.getId());
			kind = spirit.kind();
			arms = spirit.arms();
			size = Spirits.of(kind).size(spirit);
			manifest = track == null ? 0.0F : track.value(entity.level().getGameTime());
		} else if (flickerLeft > 0 && entity == Minecraft.getInstance().player) {
			kind = flickerKind;
			arms = 1;
			size = Spirits.of(kind).size();
			manifest = FLICKER_MANIFEST * 2.0F;
		} else {
			return;
		}
		if (manifest < WISP_FROM || entity.isInvisible() || !visibleToAttuned(entity)) {
			return;
		}
		Spirit sp = Spirits.of(kind);
		DustParticleOptions mote = new DustParticleOptions(mix(sp.color()) & 0xFFFFFF, 1.0F);
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		double behindX = Mth.sin(yaw), behindZ = -Mth.cos(yaw);
		for (int i = 0; i < arms; i++) {
			if (entity.getRandom().nextFloat() >= WISP_CHANCE) {
				continue;
			}
			double side = (i == 0 ? 0.45 : -0.45) * Math.max(1.0F, size * 0.6F);
			boolean floating = floats(kind);
			float reach = Math.min(size, FLOAT_SIZE_CAP);
			// the floating ones' hands hang above and ahead; the rest behind and down your arm
			double back = floating ? -(0.3 + 0.25 * reach) : 0.25 + 0.1 * size;
			double x = entity.getX() - Mth.cos(yaw) * side + behindX * back;
			double z = entity.getZ() - Mth.sin(yaw) * side + behindZ * back;
			double y = entity.getY() + (floating ? 1.25 + 0.2 * reach : Math.max(0.2, 1.3 - 0.45 * size));
			level.addParticle(mote, x + (entity.getRandom().nextDouble() - 0.5) * 0.2, y, z + (entity.getRandom().nextDouble() - 0.5) * 0.2,
				0.0, 0.03, 0.0);
		}
	}

	/**
	 * The blow landing: when a strike or a swing reaches its hit, a burst where the hand lands ahead of the caster: dust in the
	 * spirit's colour, sparks and crits flung out, and a crack (heard only by those who can see the arms).
	 */
	private static void burst(ClientLevel level, LivingEntity entity) {
		SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
		Track track = TRACKS.get(entity.getId());
		if (spirit == null || track == null || !spirit.out() || spirit.show() == 0L || track.burst == spirit.show()
			|| !SpiritRig.lands(spirit.pose())) {
			return;
		}
		float age = (float)(level.getGameTime() - spirit.shownAt());
		if (age < SpiritRig.HIT_T || age > SpiritRig.HIT_T + BURST_LATE) {
			return;
		}
		track.burst = spirit.show();
		if (entity.isInvisible() || !visibleToAttuned(entity)) {
			return;
		}
		float size = Math.min(Spirits.of(spirit.kind()).size(spirit), FLOAT_SIZE_CAP);
		Vec3 at = entity.getEyePosition().add(entity.getLookAngle().scale(BURST_AHEAD + BURST_AHEAD * size));
		DustParticleOptions dust = new DustParticleOptions(mix(Spirits.of(spirit.kind()).color()) & 0xFFFFFF, 1.6F);
		var random = entity.getRandom();
		for (int n = 0; n < BURST_MOTES; n++) {
			double vx = (random.nextDouble() - 0.5) * BURST_SPEED, vy = (random.nextDouble() - 0.5) * BURST_SPEED, vz = (random.nextDouble() - 0.5) * BURST_SPEED;
			level.addParticle(dust, at.x, at.y, at.z, vx, vy, vz);
			if (n % 2 == 0) {
				level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, vx * 0.6, vy * 0.6, vz * 0.6);
			}
			if (n % 3 == 0) {
				level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, vx * 1.5, vy * 1.5, vz * 1.5);
			}
		}
		level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 1.3F - 0.15F * size, false);
		level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.6F, false);
	}

	/** The burst: blocks ahead of the eye per unit of size (plus as many), motes, their speed, and how many ticks late it may still fire. */
	static final float BURST_AHEAD = 0.9F, BURST_SPEED = 0.35F, BURST_LATE = 4.0F;
	static final int BURST_MOTES = 18;

	/** Whether this client may see the aura of {@code entity} (the arms are made of it): attuned, or reading magic. */
	private static boolean visibleToAttuned(LivingEntity entity) {
		LocalPlayer viewer = Minecraft.getInstance().player;
		return viewer != null && Mana.attuned(entity) && (Mana.attuned(viewer) || com.jaymoskingdoms.ManaAlchemy.readsMagic(viewer));
	}

	/** The spirit's colour mixed with mana's blue. */
	static int mix(int color) {
		int r = (int)Mth.lerp(KIND_MIX, ARGB.red(ManaClient.COLOR), ARGB.red(color));
		int g = (int)Mth.lerp(KIND_MIX, ARGB.green(ManaClient.COLOR), ARGB.green(color));
		int b = (int)Mth.lerp(KIND_MIX, ARGB.blue(ManaClient.COLOR), ARGB.blue(color));
		return ARGB.color(255, r, g, b);
	}

	/** Whether {@code kind}'s arms float free of yours (above and ahead) rather than sitting on your own arm. */
	static boolean floats(int kind) {
		return kind == Spirits.VARRO || kind == Spirits.AMBROSE || kind == Spirits.QUIET;
	}

	// ---- first person ------------------------------------------------------------------------------------------------------

	/** The player layer's arms, kept for the first-person draw (the player isn't drawn in first person, so its layers aren't). */
	private static @Nullable Arms firstPersonArms;

	/**
	 * First person (after the hands, from {@code SpiritFirstPersonMixin}): your own spirit's arms, placed where they are on your
	 * body in third person, so the floating ones hover in view and a strike comes out in front of you (Andy: "more of the spirit
	 * should be visible in first person, especially when it strikes in front of you"). The pose stack is the hands' view space:
	 * the camera's turn and your body's are put back on it, then the living renderer's own steps.
	 */
	public static void firstPerson(PoseStack poseStack, SubmitNodeCollector collector, @Nullable AvatarRenderState avatar) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Arms arms = firstPersonArms;
		if (player == null || avatar == null || arms == null || avatar.isInvisible) {
			return;
		}
		// (the hands' state may not carry the arms and the glow: read them fresh)
		ManaClient.extract(player, avatar);
		Look look = avatar.getData(LOOK);
		if (look == null || look.manifest() <= 0.01F) {
			return;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		poseStack.pushPose();
		// the hands have their own, narrower lens: squeeze across so the arms sit where the world would show them
		float widen = (float)(Math.tan(Math.toRadians(camera.getFov() * 0.5)) / Math.tan(Math.toRadians(HAND_FOV * 0.5)));
		poseStack.scale(1.0F / widen, 1.0F / widen, 1.0F);
		poseStack.rotate(new Quaternionf(camera.rotation()).conjugate());
		poseStack.translate(avatar.x - eye.x, avatar.y - eye.y, avatar.z - eye.z);
		poseStack.scale(avatar.scale, avatar.scale, avatar.scale);
		poseStack.rotateDegrees(Axis.YP, 180.0F - avatar.bodyRot);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
		poseStack.translate(0.0F, MODEL_DROP, 0.0F);
		arms.submit(poseStack, collector, avatar.lightCoords, avatar);
		poseStack.popPose();
	}

	/** The player renderer's own scale, and the drop from the feet to the model's origin (LivingEntityRenderer.submit). */
	static final float PLAYER_SCALE = 0.9375F, MODEL_DROP = -1.501F;
	/** The field of view the hands are drawn with (GameRenderer's item-in-hand projection). */
	static final double HAND_FOV = 70.0;

	// ---- the layers --------------------------------------------------------------------------------------------------------

	/** The arms on a player. */
	private static final class Player extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final Arms arms;

		Player(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
			this.arms = new Arms(this::getParentModel);
			firstPersonArms = this.arms;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			this.arms.submit(poseStack, collector, lightCoords, state);
		}
	}

	/** The arms on the casting folk, and on anyone else humanoid. */
	private static final class Folk<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		private final Arms arms;

		Folk(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
			this.arms = new Arms(this::getParentModel);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			this.arms.submit(poseStack, collector, lightCoords, state);
		}
	}

	/** One renderer's arm models (a model for each kind and shell, and the trail's lagging ghosts, baked when first needed) and how they are drawn. */
	private static final class Arms {
		private final Supplier<HumanoidModel<?>> wearer;
		private final ArmModel[][] models = new ArmModel[Spirits.QUIET + 1][ManaClient.SHELLS.length];
		private final ArmModel[][] trails = new ArmModel[Spirits.QUIET + 1][TRAIL_LAG.length];

		Arms(Supplier<HumanoidModel<?>> wearer) {
			this.wearer = wearer;
		}

		private ArmModel model(int kind, int shell) {
			if (this.models[kind][shell] == null) {
				this.models[kind][shell] = new ArmModel(bake(kind, shell), this.wearer, kind, 0.0F);
			}
			return this.models[kind][shell];
		}

		private ArmModel trail(int kind, int ghost) {
			if (this.trails[kind][ghost] == null) {
				this.trails[kind][ghost] = new ArmModel(bake(kind, 0), this.wearer, kind, TRAIL_LAG[ghost]);
			}
			return this.trails[kind][ghost];
		}

		private static ModelPart bake(int kind, int shell) {
			return LayerDefinition.create(SpiritRig.mesh(kind, ManaClient.SHELLS[shell]), 64, 64).bakeRoot();
		}

		void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidRenderState state) {
			Look look = state.getData(LOOK);
			Glow glow = state.getData(ManaClient.KEY);
			if (look == null || glow == null || state.isInvisible || look.manifest() <= 0.01F) {
				return;
			}
			int kind = Math.clamp(look.kind(), 0, Spirits.QUIET);
			int color = mix(Spirits.of(kind).color());
			float breathe = 1.0F + ManaClient.BREATHE * Mth.sin(state.ageInTicks * ManaClient.BREATHE_SPEED);
			float rise = (state.ageInTicks * ManaClient.RISE) % 1.0F;
			// the arms fade with how manifest they are, as well as grow
			float fade = look.manifest() * look.manifest();
			float strike = look.pose() == Poses.IDLE ? 0.0F : look.poseWeight();
			for (int i = 0; i < ManaClient.SHELLS.length; i++) {
				float light = Mth.clamp(ManaClient.SHELL_LIGHT[i] * glow.strength() * breathe * ARM_LIGHT * fade, 0.0F, 1.0F);
				// a strike blazes: every shell up to full while the move's pose holds (Andy: "glow with their maximum intensity when striking")
				light = Mth.lerp(strike * fade, light, 1.0F);
				collector.order(ORDER + i).submitModel(this.model(kind, i), state, poseStack,
					RenderTypes.energySwirl(ManaClient.TEXTURE, i * 0.29F + 0.13F, -rise * (1 + i)), lightCoords, OverlayTexture.NO_OVERLAY,
					ManaClient.tint(color, light), null, 0);
			}
			// the blow's trail: ghosts of the arm a tick and two behind, fading, from the snap until just after it lands
			if (SpiritRig.lands(look.pose()) && look.poseAge() >= SpiritRig.WIND_T && look.poseAge() <= SpiritRig.HIT_T + TRAIL_AFTER) {
				for (int g = 0; g < TRAIL_LAG.length; g++) {
					collector.order(ORDER + ManaClient.SHELLS.length + g).submitModel(this.trail(kind, g), state, poseStack,
						RenderTypes.energySwirl(ManaClient.TEXTURE, 0.13F, -rise), lightCoords, OverlayTexture.NO_OVERLAY,
						ManaClient.tint(color, TRAIL_LIGHT[g] * fade), null, 0);
				}
			}
		}
	}

	/** The trail: how many ticks each ghost lags the arm, how bright each is, and how long after the hit it lingers. */
	static final float[] TRAIL_LAG = {1.0F, 2.0F}, TRAIL_LIGHT = {0.55F, 0.3F};
	static final float TRAIL_AFTER = 3.0F;

	// ---- the model ---------------------------------------------------------------------------------------------------------

	/**
	 * A spirit's two arms, one shell of them (or one ghost of the trail, {@code lag} ticks behind). A HumanoidModel (as
	 * {@code WornModel} is) so every pose laid over a humanoid model lands on it; its only visible parts are the arms, jointed
	 * ({@link SpiritRig}): the shoulder takes the wearer's own arm (the floating ones only their look) and then the spirit's pose,
	 * and the elbow, wrist and fingers follow the move's shape.
	 */
	private static final class ArmModel extends HumanoidModel<HumanoidRenderState> {
		private final Supplier<HumanoidModel<?>> wearer;
		private final SpiritRig.Limb right, left;
		private final float lag;
		private final SpiritRig.Frame frame = new SpiritRig.Frame();

		ArmModel(ModelPart root, Supplier<HumanoidModel<?>> wearer, int kind, float lag) {
			super(root);
			this.wearer = wearer;
			this.right = new SpiritRig.Limb(this.rightArm, kind);
			this.left = new SpiritRig.Limb(this.leftArm, kind);
			this.lag = lag;
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public void setupAnim(HumanoidRenderState state) {
			resetPose();
			this.head.visible = false;
			this.hat.visible = false;
			this.body.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			Look look = state.getData(LOOK);
			HumanoidModel parent = this.wearer.get();
			parent.setupAnim(state);
			if (look == null) {
				this.rightArm.visible = false;
				this.leftArm.visible = false;
				return;
			}
			float size = look.size() * Math.max(0.001F, look.manifest());
			boolean floats = floats(look.kind());
			float reach = Math.min(size, FLOAT_SIZE_CAP);
			int poseId = Mth.clamp(look.pose(), 0, Poses.COUNT - 1);
			float[] idle = floats ? FLOAT_IDLE : TABLE[Poses.IDLE];
			float[] target = TABLE[poseId];
			float age = Math.max(0.0F, look.poseAge() - this.lag);
			float w = look.poseWeight();
			for (int i = 0; i < 2; i++) {
				ModelPart arm = i == 0 ? this.rightArm : this.leftArm;
				ModelPart from = i == 0 ? parent.rightArm : parent.leftArm;
				arm.loadPose(from.storePose());
				arm.visible = i == 0 || look.arms() >= 2;
				float side = i == 0 ? 1.0F : -1.0F;
				SpiritRig.Frame f = this.frame;
				SpiritRig.frame(f, poseId, age, w, idle, target, floats, i, state.ageInTicks);
				float drift = SWAY * Mth.sin(state.ageInTicks * SWAY_SPEED + i * 2.1F) * (1.0F - 0.6F * w);
				f.sx += w * poseAdjust(poseId, age, i) + drift;
				if (floats) {
					// floating free: not swung by your walk, but a blow goes where you look (down when you look down)
					arm.xRot = w * state.xRot * Mth.DEG_TO_RAD;
					arm.yRot = 0.0F;
					arm.zRot = 0.0F;
				}
				arm.xRot += f.sx;
				arm.yRot += f.sy * side;
				arm.zRot += f.sz * side;
				(i == 0 ? this.right : this.left).pose(f, side);
				if (floats) {
					// hovering above and ahead of the shoulder (model -y is up, -z ahead; the right arm's outside is -x)
					arm.y -= FLOAT_UP * reach;
					arm.z -= FLOAT_AHEAD * reach;
					arm.x -= side * FLOAT_OUT * reach;
				} else {
					// grown about the shoulder, and pushed behind (the model's +z is the back)
					arm.z += BEHIND * size;
				}
				arm.xScale = size;
				arm.yScale = size;
				arm.zScale = size;
			}
		}
	}

	/** How far the SILENT arm is drawn back before it thrusts (ticks), and by how much (radians added to x at the extreme). */
	static final float SILENT_DRAW_TICKS = 6.0F, SILENT_DRAW = 3.2F;
	/** JUGGLE's swing and speed; ECHO's second blow; PULL's draw-back; UNLOCK's turn. */
	static final float JUGGLE_SWING = 1.0F, JUGGLE_SPEED = 0.9F, ECHO_PULSE = 0.5F, ECHO_SPEED = 1.5F, PULL_DRAW = 1.2F, TURN = 0.4F;

	/** Extra x-angle for the poses that move rather than hold: arm {@code i} at {@code age} ticks into pose {@code pose}. */
	private static float poseAdjust(int pose, float age, int i) {
		return switch (pose) {
			case Poses.JUGGLE -> JUGGLE_SWING * Mth.sin(age * JUGGLE_SPEED + i * Mth.PI);
			case Poses.ECHO -> ECHO_PULSE * Mth.sin(age * ECHO_SPEED);
			case Poses.PULL -> age < POSE_IN + 4 ? 0.0F : PULL_DRAW * Mth.clamp((age - POSE_IN - 4) / 4.0F, 0.0F, 1.0F);
			case Poses.UNLOCK -> TURN * Mth.sin(age * 0.8F);
			case Poses.SILENT -> age < SILENT_DRAW_TICKS ? SILENT_DRAW * (1.0F - age / SILENT_DRAW_TICKS) : 0.0F;
			default -> 0.0F;
		};
	}
}
