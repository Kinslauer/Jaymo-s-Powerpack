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
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
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
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.236) A mana spirit's arms. Andy: "these spirits should conjure as arms behind you with the same effects as your mana
 * aura making up their entire form", after Nero's Devil Breaker (DMC5) and Doppio's partial manifestation (JoJo), "all
 * different sizes". A render layer over players and over the casting folk (the same two-branch registration as
 * {@link ManaClient}): the wearer's own arm is copied, grown by the spirit's size about the shoulder and pushed behind,
 * then bent by the pose the spirit holds ({@link Poses}, a table of arm angles below) and drawn as the aura's own shells
 * ({@link ManaClient#SHELLS}), tinted by the spirit's colour mixed with mana's blue, at the strength the wearer's glow has
 * ({@link ManaClient#KEY}; no glow, no arms, so only the attuned see them). Each kind has its own hand on the end: Grimsby's
 * long bony fingers and the blade below the wrist, Varro's studded gauntlet, placeholders for the rest.
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
	/** Model pixels the arm is pushed behind the shoulder, per unit of size. */
	static final float BEHIND = 2.0F;
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
	 * with that pose's strength ({@code poseWeight} 0-1) and age in ticks, and whether it has {@code arms} one or two.
	 */
	record Look(int kind, float manifest, int pose, float poseWeight, float poseAge, int arms) {
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
		// the second patch's (first guesses; each spirit's track tunes them)
		TABLE[Poses.AEGIS] = new float[] {-1.4F, -0.5F, 0.60F};
		TABLE[Poses.BLESS] = new float[] {-2.9F, 0.0F, 0.40F};
		TABLE[Poses.CATCH_ME] = new float[] {-0.6F, -0.9F, 0.30F};
		TABLE[Poses.MEND] = new float[] {-1.1F, -0.7F, 0.15F};
		TABLE[Poses.ARMOUR] = new float[] {-0.4F, -1.2F, 0.80F};
		TABLE[Poses.RIDE] = new float[] {-3.0F, 0.0F, 0.90F};
		TABLE[Poses.PULL] = new float[] {-1.6F, -0.3F, 0.05F};
		TABLE[Poses.STEAL] = new float[] {-1.5F, -0.6F, 0.10F};
		TABLE[Poses.TRAP] = new float[] {-0.5F, 0.0F, 0.30F};
		TABLE[Poses.UNLOCK] = new float[] {-1.4F, -0.4F, 0.15F};
		TABLE[Poses.JUGGLE] = new float[] {-2.2F, -0.2F, 0.40F};
		TABLE[Poses.ECHO] = new float[] {-1.8F, 0.0F, 0.20F};
		TABLE[Poses.MIRROR] = new float[] {-0.8F, 0.6F, 0.50F};
		TABLE[Poses.STILL] = new float[] {-1.0F, 0.0F, 1.20F};
		TABLE[Poses.SILENT] = new float[] {-2.6F, -0.3F, 0.00F};
		TABLE[Poses.SENSE] = new float[] {-1.7F, 0.4F, 0.30F};
		TABLE[Poses.CLASH] = new float[] {-1.6F, -0.2F, 0.25F};
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
				return new Look(flickerKind, FLICKER_MANIFEST, Poses.IDLE, 0.0F, 0.0F, 1);
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
		return new Look(spirit.kind(), manifest, pose, weight, age, spirit.arms());
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
			}
		}
	}

	private static void wisp(ClientLevel level, LivingEntity entity) {
		SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
		int kind;
		int arms;
		float manifest;
		if (spirit != null) {
			Track track = TRACKS.get(entity.getId());
			kind = spirit.kind();
			arms = spirit.arms();
			manifest = track == null ? 0.0F : track.value(entity.level().getGameTime());
		} else if (flickerLeft > 0 && entity == Minecraft.getInstance().player) {
			kind = flickerKind;
			arms = 1;
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
			double side = (i == 0 ? 0.45 : -0.45) * Math.max(1.0F, sp.size() * 0.6F);
			double back = 0.25 + 0.1 * sp.size();
			double x = entity.getX() - Mth.cos(yaw) * side + behindX * back;
			double z = entity.getZ() - Mth.sin(yaw) * side + behindZ * back;
			double y = entity.getY() + Math.max(0.2, 1.3 - 0.45 * sp.size());
			level.addParticle(mote, x + (entity.getRandom().nextDouble() - 0.5) * 0.2, y, z + (entity.getRandom().nextDouble() - 0.5) * 0.2,
				0.0, 0.03, 0.0);
		}
	}

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

	// ---- the layers --------------------------------------------------------------------------------------------------------

	/** The arms on a player. */
	private static final class Player extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final Arms arms;

		Player(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
			this.arms = new Arms(this::getParentModel);
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

	/** One renderer's arm models (a model for each kind and shell, baked when first needed) and how they are drawn. */
	private static final class Arms {
		private final Supplier<HumanoidModel<?>> wearer;
		private final ArmModel[][] models = new ArmModel[Spirits.QUIET + 1][ManaClient.SHELLS.length];

		Arms(Supplier<HumanoidModel<?>> wearer) {
			this.wearer = wearer;
		}

		private ArmModel model(int kind, int shell) {
			if (this.models[kind][shell] == null) {
				this.models[kind][shell] = new ArmModel(LayerDefinition.create(mesh(kind, ManaClient.SHELLS[shell]), 64, 64).bakeRoot(), this.wearer);
			}
			return this.models[kind][shell];
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
			for (int i = 0; i < ManaClient.SHELLS.length; i++) {
				float light = Mth.clamp(ManaClient.SHELL_LIGHT[i] * glow.strength() * breathe * ARM_LIGHT * fade, 0.0F, 1.0F);
				collector.order(ORDER + i).submitModel(this.model(kind, i), state, poseStack,
					RenderTypes.energySwirl(ManaClient.TEXTURE, i * 0.29F + 0.13F, -rise * (1 + i)), lightCoords, OverlayTexture.NO_OVERLAY,
					ManaClient.tint(color, light), null, 0);
			}
		}
	}

	// ---- the model ---------------------------------------------------------------------------------------------------------

	/**
	 * A spirit's two arms, one shell of them. A HumanoidModel (as {@code WornModel} is) so every pose laid over a humanoid model
	 * lands on it; its only visible parts are the arms, each taking the wearer's own arm and then the spirit's pose.
	 */
	private static final class ArmModel extends HumanoidModel<HumanoidRenderState> {
		private final Supplier<HumanoidModel<?>> wearer;

		ArmModel(ModelPart root, Supplier<HumanoidModel<?>> wearer) {
			super(root);
			this.wearer = wearer;
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
			Spirit spirit = Spirits.of(look.kind());
			float size = spirit.size() * Math.max(0.001F, look.manifest());
			float[] idle = TABLE[Poses.IDLE];
			float[] pose = TABLE[Mth.clamp(look.pose(), 0, Poses.COUNT - 1)];
			for (int i = 0; i < 2; i++) {
				ModelPart arm = i == 0 ? this.rightArm : this.leftArm;
				ModelPart from = i == 0 ? parent.rightArm : parent.leftArm;
				arm.loadPose(from.storePose());
				arm.visible = i == 0 || look.arms() >= 2;
				float side = i == 0 ? 1.0F : -1.0F;
				float w = look.poseWeight();
				float x = Mth.lerp(w, idle[0], pose[0]);
				float y = Mth.lerp(w, idle[1], pose[1]);
				float z = Mth.lerp(w, idle[2], pose[2]);
				if (look.pose() == Poses.FLURRY || look.pose() == Poses.SHOVE) {
					// the blows: the two arms trade
					x += w * 0.5F * Mth.sin(look.poseAge() * 1.8F + i * Mth.PI);
				}
				float drift = SWAY * Mth.sin(state.ageInTicks * SWAY_SPEED + i * 2.1F) * (1.0F - 0.6F * w);
				arm.xRot += x + drift;
				arm.yRot += y * side;
				arm.zRot += z * side;
				// grown about the shoulder, and pushed behind (the model's +z is the back)
				arm.z += BEHIND * size;
				arm.xScale = size;
				arm.yScale = size;
				arm.zScale = size;
			}
		}
	}

	/**
	 * The mesh of one kind's two arms, inflated by {@code out}: the arm box at the vanilla offsets, and the kind's hand on the end
	 * (the box ends at y 10). The other body parts are empty, the humanoid model needing them to exist.
	 */
	private static MeshDefinition mesh(int kind, float out) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		CubeDeformation grow = new CubeDeformation(out);
		for (int i = 0; i < 2; i++) {
			// the right arm's box spans x -3..1 about its pivot; the left arm's -1..3
			float dx = i == 0 ? 0.0F : 2.0F;
			CubeListBuilder cubes = CubeListBuilder.create().texOffs(i == 0 ? 40 : 32, i == 0 ? 16 : 48).addBox(-3.0F + dx, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, grow);
			hand(kind, cubes, dx, grow);
			root.addOrReplaceChild(i == 0 ? "right_arm" : "left_arm", cubes, PartPose.offset(i == 0 ? -5.0F : 5.0F, 2.0F, 0.0F));
		}
		return mesh;
	}

	/** One kind's hand, added to the arm's cubes ({@code dx} shifts it onto the left arm). */
	private static void hand(int kind, CubeListBuilder c, float dx, CubeDeformation grow) {
		c.texOffs(0, 0);
		switch (kind) {
			case Spirits.GRIMSBY -> {
				// three long bony fingers (1x5x1) and the blade (1x10x3) below the wrist
				c.addBox(-2.5F + dx, 10.0F, -1.5F, 1.0F, 5.0F, 1.0F, grow);
				c.addBox(-1.0F + dx, 10.0F, 0.5F, 1.0F, 5.0F, 1.0F, grow);
				c.addBox(0.0F + dx, 10.0F, -1.5F, 1.0F, 5.0F, 1.0F, grow);
				c.addBox(-1.5F + dx, 10.0F, -1.5F, 1.0F, 10.0F, 3.0F, grow);
			}
			case Spirits.VARRO -> {
				// a gauntlet (6x8x6) with knuckle studs along the front
				c.addBox(-4.0F + dx, 10.0F, -3.0F, 6.0F, 8.0F, 6.0F, grow);
				for (int s = 0; s < 4; s++) {
					c.addBox(-3.6F + dx + s * 1.5F, 16.5F, -4.2F, 1.2F, 1.2F, 1.2F, grow);
				}
			}
			case Spirits.AMBROSE -> c.addBox(-3.5F + dx, 10.0F, -2.5F, 5.0F, 1.0F, 5.0F, grow);
			case Spirits.THESSALY -> c.addBox(-2.5F + dx, 10.0F, -1.5F, 3.0F, 3.0F, 3.0F, grow);
			default -> c.addBox(-2.0F + dx, 10.0F, -1.0F, 2.0F, 2.0F, 2.0F, grow);
		}
	}
}
