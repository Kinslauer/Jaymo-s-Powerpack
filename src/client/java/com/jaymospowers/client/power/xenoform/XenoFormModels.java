package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * The aliens' bodies: each a humanoid model (so it takes the player's own pose, swing, crouch and swim) with the
 * player's proportions pushed about and extra parts hung on, and its own 64x64 skin
 * ({@code textures/entity/xenoform_<id>.png}, 64x128, drawn by {@code tools/gen_xenoform.py}). The base limbs use the
 * vanilla skin layout in the top 64 rows; the extras use the rows below it, so nothing shares texels with a limb. {@link #of} finds a form's model by its id.
 */
public final class XenoFormModels {
	/** One alien's model: the texture to draw it with, and the model itself. */
	public abstract static class FormModel extends HumanoidModel<AvatarRenderState> {
		public final Identifier texture;
		/** (0.18.93) The Ultimate's skin (armoured, plated) and its glowing seams, drawn fullbright over it. */
		public final Identifier ultimateTexture, ultimateGlow;
		/** The model's id (its form's). */
		public final String id;
		/** How much bigger than the player's body it's drawn, from the feet (a small form's model built small). */
		protected float renderScale = 1.0F;
		/**
		 * (0.18.90) The insignia on its chest, which it taps to change back (it wears no band): the middle of it (model
		 * pixels in the body's space, y down) on the front of the chest (z), and its size against the band's 5-pixel face.
		 */
		protected float insigniaY = 7.5F, insigniaZ = -2.0F, insigniaSize = 1.0F;
		/** (0.19.84) Its right arm reaches for its own insignia on the change-back tap ({@link XenoformSlap} leaves that arm alone). */
		public boolean ownsTap;
		/** (0.19.84) Its legs are solved onto the ground: Cryo's poses leave them alone. */
		public boolean ownsLegs;

		protected FormModel(ModelPart root, String id) {
			super(root);
			this.texture = JaymosPowers.id("textures/entity/xenoform_" + id + ".png");
			this.ultimateTexture = JaymosPowers.id("textures/entity/xenoform_" + id + "_ultimate.png");
			this.ultimateGlow = JaymosPowers.id("textures/entity/xenoform_" + id + "_ultimate_glow.png");
			this.id = id;
		}

		/**
		 * (0.20.9) A model's own extra passes (glow, wings, comet), drawn after its body inside {@link XenoformLayer}'s pose. A no-op
		 * by default: the older models keep their effects in the layer's {@code instanceof} branches.
		 */
		public void submitExtras(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
		}
	}

	/**
	 * (0.20.9, Ultimates patch 0) The base of every Ultimate's model ({@code client/.../ultimate/Ultimate<Name>.java}). It is a
	 * {@link FormModel} built with the BASE alien's id, so {@link #ultimateTexture} / {@link #ultimateGlow} are the Ultimate's skin
	 * ({@code textures/entity/xenoform_<id>_ultimate[_glow].png}); the layer draws those, skips {@code UltimateSpikes}, and calls
	 * {@link #submitExtras}. Subclasses pose themselves in {@link #pose} (not {@code setupAnim}, which is final here): after it,
	 * the change-in morph ({@link XenoMorph}) scales the parts named in {@code XenoUltimate.morphParts()} that haven't grown in yet.
	 */
	public abstract static class UltimateModel extends FormModel {
		private java.util.function.Function<String, ModelPart> lookup;
		private final Map<String, float[]> centres = new java.util.HashMap<>();

		protected UltimateModel(ModelPart root, String id) {
			super(root, id);
		}

		/** The body's pose for this state: override this and call {@code super.pose(state)} first (it is the humanoid pose). */
		protected void pose(AvatarRenderState state) {
			super.setupAnim(state);
		}

		@Override
		public final void setupAnim(AvatarRenderState state) {
			pose(state);
			XenoMorph.apply(this, state);
		}

		/**
		 * The part called {@code name} anywhere in the model (a ModelPart child at any depth, by the name it was added under in the
		 * MeshDefinition; "root" is the root), or null when there is none: a morph part that isn't found is skipped.
		 */
		public @Nullable ModelPart part(String name) {
			if (lookup == null) {
				lookup = root.createPartLookup();
			}
			return lookup.apply(name);
		}

		/**
		 * Where {@code name} sits at rest, as a point in blocks in the entity's frame (right, up from the feet, back; the model's
		 * 1.501 above the feet and its y flipped): the middle of its cubes and its descendants'. Null if there is no such part.
		 */
		public float @Nullable [] centre(String name) {
			if (centres.containsKey(name)) {
				return centres.get(name);
			}
			if (name.contains("+")) {
				// (0.20.9) a beat of several parts: the middle of their middles
				float[] sum = new float[3];
				int got = 0;
				for (String piece : name.split("\\+")) {
					float[] c = centre(piece);
					if (c != null) {
						sum[0] += c[0];
						sum[1] += c[1];
						sum[2] += c[2];
						got++;
					}
				}
				float[] found = got == 0 ? null : new float[] {sum[0] / got, sum[1] / got, sum[2] / got};
				centres.put(name, found);
				return found;
			}
			float[] found = null;
			ModelPart part = part(name);
			if (part != null) {
				// (0.20.9) from the rest pose of every part, not however the last frame left them
				root.getAllParts().forEach(ModelPart::resetPose);
				float[] sum = new float[3];
				int[] count = new int[1];
				root.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, path, index, cube) -> {
					boolean in = false;
					for (String segment : path.split("/")) {
						in |= segment.equals(name);
					}
					if (in) {
						org.joml.Vector3f at = pose.pose().transformPosition((cube.minX + cube.maxX) * 0.03125F, (cube.minY + cube.maxY) * 0.03125F,
							(cube.minZ + cube.maxZ) * 0.03125F, new org.joml.Vector3f());
						sum[0] += at.x;
						sum[1] += at.y;
						sum[2] += at.z;
						count[0]++;
					}
				});
				if (count[0] > 0) {
					found = new float[] {-sum[0] / count[0], 1.501F - sum[1] / count[0], sum[2] / count[0]};
				}
			}
			centres.put(name, found);
			return found;
		}
	}

	private static final Map<String, FormModel> MODELS = new LinkedHashMap<>();

	private XenoFormModels() {
	}

	/** (0.20.9) The Ultimate models by BASE alien id, filled lazily like {@link #MODELS}: one line per Ultimate, added by its stage. */
	private static final Map<String, FormModel> ULTIMATES = new LinkedHashMap<>();
	private static boolean ultimatesFilled;

	/** (0.20.9) The Ultimate model of the alien {@code form} (its base id; {@code ooze} and {@code magma_ooze} are separate), or null if none was added yet. */
	public static @Nullable FormModel ultimate(@Nullable String form) {
		if (form == null) {
			return null;
		}
		if (!ultimatesFilled) {
			ultimatesFilled = true;
			// (0.20.9) one line per Ultimate
			ULTIMATES.put("bulwark", new com.jaymospowers.client.power.xenoform.ultimate.UltimateTitan());
			ULTIMATES.put("pyre", new com.jaymospowers.client.power.xenoform.ultimate.UltimateSunheart()); // (0.20.21)
			ULTIMATES.put("blur", new com.jaymospowers.client.power.xenoform.ultimate.UltimateStandstill()); // (0.20.21)
			ULTIMATES.put("prism", new com.jaymospowers.client.power.xenoform.ultimate.UltimateGeode()); // (0.20.21)
			ULTIMATES.put("wisp", new com.jaymospowers.client.power.xenoform.ultimate.UltimateRevenant()); // (0.20.21)
			ULTIMATES.put("gremlin", new com.jaymospowers.client.power.xenoform.ultimate.UltimateSwarm()); // (0.20.21)
			ULTIMATES.put("knell", new com.jaymospowers.client.power.xenoform.ultimate.UltimateTolling()); // (0.20.21)
			ULTIMATES.put("wyrm", new com.jaymospowers.client.power.xenoform.ultimate.UltimateElderWyrm()); // (0.21.37)
			ULTIMATES.put("duplex", new com.jaymospowers.client.power.xenoform.ultimate.UltimateCopperStorm()); // (0.21.37)
			ULTIMATES.put("rivet", new com.jaymospowers.client.power.xenoform.ultimate.UltimateIronpour()); // (0.21.37)
			ULTIMATES.put("gorger", new com.jaymospowers.client.power.xenoform.ultimate.UltimateWorldmaw()); // (0.21.37)
			ULTIMATES.put("slipfin", new com.jaymospowers.client.power.xenoform.ultimate.UltimateBrinecrown()); // (0.21.37)
			ULTIMATES.put("polestar", new com.jaymospowers.client.power.xenoform.ultimate.UltimateLodestar()); // (0.21.37)
			ULTIMATES.put("lurk", new com.jaymospowers.client.power.xenoform.ultimate.UltimateNightfall()); // (0.21.37)
		}
		return ULTIMATES.get(form);
	}

	public static @Nullable FormModel of(@Nullable String form) {
		if (form == null) {
			return null;
		}
		if (MODELS.isEmpty()) {
			MODELS.put("bulwark", new Bulwark());
			MODELS.put("pyre", new Pyre());
			MODELS.put("blur", new Velox());
			MODELS.put("prism", new Prism());
			MODELS.put("wisp", new Wisp());
			MODELS.put("gremlin", new Gremlin());
			MODELS.put("knell", new Knell());
			MODELS.put("wyrm", new Wyrm());
			MODELS.put("nadir", new Nadir());
			MODELS.put("duplex", new Duplex());
			MODELS.put("rivet", new Rivet());
			MODELS.put("gorger", new Gorger());
			MODELS.put("slipfin", new Slipfin());
			MODELS.put("polestar", new Polestar());
			MODELS.put("lurk", new Lurk());
			MODELS.put("gyre", new Gyre());
			MODELS.put("cortex", new Cortex());
			MODELS.put("locust", new Locust());
			MODELS.put("croaker", new Croaker());
			MODELS.put("dud", new Dud());
			MODELS.put("rime", new Rime());
			MODELS.put("marrow", new Marrow());
			MODELS.put("swathe", new Swathe());
			MODELS.put("blight", new Blight());
			MODELS.put("howl", new Howl());
			MODELS.put("prowl", new Prowl());
			MODELS.put("tusk", new Tusk());
			MODELS.put("talon", new Talon());
			MODELS.put("murk", new Murk());
			MODELS.put("gnasher", new Gnasher());
			MODELS.put("lumen", new Lumen());
			MODELS.put("keen", new Keen());
			MODELS.put("hive", new Hive());
			MODELS.put("skitter", new Skitter());
			MODELS.put("shade", new Shade());
			MODELS.put("mirage", new Mirage());
			MODELS.put("ooze", new Ooze("ooze"));
			MODELS.put("magma_ooze", new Ooze("magma_ooze"));
			MODELS.put("fuse", new Fuse());
			MODELS.put("thornwild", new Thornwild());
			MODELS.put("billow", new Billow());
			MODELS.put("towerback", new Towerback());
		}
		return MODELS.get(form);
	}

	/** How far a tail rises: up as it falls, whipping down on landing, low when crouched (radians, + is up). */
	static float tailLift(XenoMotion.Read m) {
		return Math.max(0.0F, m.fall()) * 0.5F * m.air() - m.land() * 0.7F - (m.crouching() ? 0.35F : 0.0F);
	}

	private static ModelPart bake(MeshDefinition mesh) {
		return bake(mesh, 64, 128);
	}

	/** (0.20.9) An Ultimate's model root: its own UV size (64x128, or larger where it needs it). */
	public static ModelPart bake(MeshDefinition mesh, int textureWidth, int textureHeight) {
		return LayerDefinition.create(mesh, textureWidth, textureHeight).bakeRoot();
	}

	/**
	 * (0.18.95; 0.19.84 a puddle that stands up to fight) Rivet: living metal. Left alone it is a low, rippling puddle
	 * of liquid metal that flows along the ground: a wide slab with a smaller mound on it, four lobes round the rim that
	 * swell and sink (faster as it moves), a wake dragging behind that swings with its turns, and its face a squashed dome
	 * at the front with two green slit eyes. Fighting (an attack swing, or {@code XenoformState.ACT} "fight", set while it
	 * has struck or been struck lately) it RISES: the torso (the hidden-in-the-puddle body, lying face-up with the
	 * insignia on top) lifts upright into a hunched figure, two stubby legs rise out of the slab, arms form out of the
	 * mass, and the arm that swings stretches like a whip; when the fight is over it melts back down. "pour" (Pour
	 * dash) flattens it into a long streak with the wake streaming out behind; sneaking or riding a machine keeps it a
	 * puddle. Melded into a shell it's drawn as that block instead ({@code XenoformLayer}). The humanoid parts are all
	 * still here: the body is the torso (and the insignia's anchor), head, arms and legs are the figure's.
	 */
	static final class Rivet extends FormModel {
		// ---- tuning ----
		/** How fast it rises, melts back down (slowly, or quickly into a pour) and pours flat (share of the way per tick). */
		static final float RISE_UP = 0.16F, MELT_DOWN = 0.035F, POUR_MELT = 0.25F, POUR_IN = 0.3F, POUR_OUT = 0.1F;
		/** The lying torso: its pivot's height, and the z of its hips' end; the risen stance's forward lean (radians). */
		static final float LIE_Y = 20.7F, LIE_Z = -3.0F, LEAN = 0.3F;
		/** How far down the torso (pixels) its base point is: the hips meet it, the last pixels of the box ({@link #TORSO_LEN} long) overlap the legs. */
		static final float TORSO_REACH = 7.5F, TORSO_LEN = 9.0F;
		/** The legs: hip height standing (the puddle's is {@link #HIP_LOW}), where the feet rest, the box's length and half depth, spread, swing (radians), foot lift and clock. */
		static final float HIP_RISEN = 14.8F, HIP_LOW = 23.0F, FOOT_Y = 23.9F, LEG_LEN = 11.6F, FOOT_HALF = 1.6F, LEG_X = 2.3F, LEG_SWING = 0.55F, FOOT_LIFT = 1.2F, LEG_RATE = 0.6662F;
		/** The arms: smallest scale (just a nub), shoulder spread and drop below the torso's top, box length, flare, and the lash arm's extra stretch. */
		static final float ARM_MIN = 0.05F, ARM_X = 4.5F, ARM_DROP = 2.0F, ARM_LEN = 11.5F, ARM_FLARE = 0.18F, LASH_STRETCH = 0.9F;
		/** How much of its width the slab, the mound and the lobes' spread keep when it stands. */
		static final float POOL_RISEN = 0.75F, MOUND_RISEN = 0.6F, LOBE_IN = 0.55F;
		/** The head: where the dome sits in the puddle (y, z), how far its neck sinks into the torso, how squashed the dome is. */
		static final float HEAD_Y = 22.5F, HEAD_Z = -5.0F, NECK_SINK = 1.2F, DOME = 0.6F;
		/** How far a sneaking renderer sinks the model (pixels). */
		static final float CROUCH_SINK = 2.0F / 0.9375F;
		/** The lobes' places on the slab (x, z). */
		private static final float[][] LOBES = {{-6.0F, -6.5F}, {6.0F, -6.0F}, {-6.5F, 5.0F}, {6.5F, 5.5F}};
		private static final int[] LOBE_U = {0, 16, 32, 48};
		/** Per-entity smoothing between frames: {age, rise, pour}. */
		private static final java.util.Map<Integer, float[]> TRACKS = new java.util.HashMap<>();

		private final ModelPart pool, mound, wake;
		private final ModelPart[] lobes = new ModelPart[4];
		/** Every part on the root (set afresh each frame, so the crouch's raise never adds up). */
		private final ModelPart[] all;

		Rivet() {
			super(bake(mesh()), "rivet");
			ModelPart root = this.root();
			this.pool = root.getChild("pool");
			this.mound = root.getChild("mound");
			this.wake = root.getChild("wake");
			for (int i = 0; i < this.lobes.length; i++) {
				this.lobes[i] = root.getChild("lobe" + i);
			}
			this.all = new ModelPart[] {this.pool, this.mound, this.wake, this.lobes[0], this.lobes[1], this.lobes[2], this.lobes[3], this.head, this.body,
				this.rightArm, this.leftArm, this.rightLeg, this.leftLeg};
			// the plate sits on the torso's front (local -z), which faces up while it lies down and forward once it stands
			this.insigniaY = 4.5F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 0.7F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the face: a squat dome (squashed further in the puddle)
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F),
				PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			// the torso: 9 long from its top (pivot) down; the puddle lays it face-up on the mound
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 9.0F, 4.0F),
				PartPose.offset(0.0F, LIE_Y, LIE_Z + TORSO_REACH));
			// arms and legs: slim and centred on their pivots, grown out of the mass (scaled) when it stands
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.5F)),
				PartPose.offset(-2.0F, LIE_Y, LIE_Z + 4.5F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.5F)),
				PartPose.offset(2.0F, LIE_Y, LIE_Z + 4.5F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.4F)),
				PartPose.offset(-LEG_X, HIP_LOW, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.4F)),
				PartPose.offset(LEG_X, HIP_LOW, 0.0F));
			// the puddle: a wide slab on the ground, a smaller mound on it, lobes round the rim, a wake behind (all pivoted on the ground, so they swell upward)
			root.addOrReplaceChild("pool", CubeListBuilder.create().texOffs(0, 64).addBox(-7.0F, -2.0F, -8.0F, 14.0F, 2.0F, 16.0F), PartPose.offset(0.0F, 24.0F, 0.0F));
			root.addOrReplaceChild("mound", CubeListBuilder.create().texOffs(0, 102).addBox(-5.0F, -3.0F, -5.0F, 10.0F, 3.0F, 10.0F), PartPose.offset(0.0F, 22.2F, 0.0F));
			for (int i = 0; i < LOBES.length; i++) {
				root.addOrReplaceChild("lobe" + i, CubeListBuilder.create().texOffs(LOBE_U[i], 82).addBox(-2.0F, -3.0F, -2.0F, 4.0F, 3.0F, 4.0F),
					PartPose.offset(LOBES[i][0], 24.0F, LOBES[i][1]));
			}
			root.addOrReplaceChild("wake", CubeListBuilder.create().texOffs(0, 90).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 2.0F, 8.0F), PartPose.offset(0.0F, 24.0F, 4.5F));
			return mesh;
		}

		/** {rise, pour} smoothed per entity over its own age (so a second draw in the same frame changes nothing). */
		private static float[] track(int id, float age, float riseTo, float pourTo) {
			if (TRACKS.size() > 64) {
				TRACKS.clear();
			}
			float[] t = TRACKS.get(id);
			if (t == null) {
				t = new float[] {age, riseTo, pourTo};
				TRACKS.put(id, t);
				return t;
			}
			float dt = age - t[0];
			if (dt < 0.0F || dt > 40.0F) {
				t[0] = age;
				t[1] = riseTo;
				t[2] = pourTo;
			} else if (dt > 0.0F) {
				t[0] = age;
				t[1] += Mth.clamp(riseTo - t[1], -(pourTo > 0.5F ? POUR_MELT : MELT_DOWN) * dt, RISE_UP * dt);
				t[2] += Mth.clamp(pourTo - t[2], -POUR_OUT * dt, POUR_IN * dt);
			}
			return t;
		}

		private static float smooth(float x) {
			x = Mth.clamp(x, 0.0F, 1.0F);
			return x * x * (3.0F - 2.0F * x);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// keep what the swing and the held item did to the arms and body, then start the parts afresh
			float twist = this.body.yRot;
			float[] ra = {this.rightArm.xRot, this.rightArm.yRot, this.rightArm.zRot};
			float[] la = {this.leftArm.xRot, this.leftArm.yRot, this.leftArm.zRot};
			for (ModelPart part : this.all) {
				part.resetPose();
			}
			this.rightArm.xRot = ra[0];
			this.rightArm.yRot = ra[1];
			this.rightArm.zRot = ra[2];
			this.leftArm.xRot = la[0];
			this.leftArm.yRot = la[1];
			this.leftArm.zRot = la[2];
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			String act = state.getData(XenoformClient.ACT);
			float swingT = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float swing = Mth.sin(swingT * (float)Math.PI);
			boolean pouring = "pour".equals(act);
			// it stands to fight, and melts when the fight's over, when it sneaks, or when it rides; pouring it is flat
			boolean stand = !pouring && !state.isCrouching && !state.isPassenger && ("fight".equals(act) || swingT > 0.0F);
			float[] t = track(state.id, age, stand ? 1.0F : 0.0F, pouring ? 1.0F : 0.0F);
			float e = smooth(t[1]);
			float p = smooth(t[2]);
			float speed = Mth.clamp(state.walkAnimationSpeed * 1.3F, 0.0F, 1.0F);
			float flow = state.walkAnimationPos * 0.9F;
			float breath = m.breath();

			// ---- the puddle: the slab ripples and stretches with its flow; the mound leans into the way it goes ----
			float surge = Mth.sin(flow) * 0.08F * speed;
			float shrink = Mth.lerp(e, 1.0F, POOL_RISEN);
			this.pool.xScale = shrink * (1.0F - 0.5F * surge) * (1.0F - 0.15F * p);
			this.pool.zScale = shrink * (1.0F + surge + 0.12F * speed) * (1.0F + 0.9F * p);
			this.pool.yScale = (1.0F + 0.1F * breath) * (1.0F - 0.35F * p);
			float moundWide = Mth.lerp(e, 1.0F, MOUND_RISEN) * (1.0F - 0.2F * p);
			this.mound.xScale = moundWide;
			this.mound.zScale = moundWide * (1.0F + 0.5F * p);
			this.mound.yScale = (1.0F + 0.08F * breath) * Mth.lerp(e, 1.0F, 0.6F) * (1.0F - 0.5F * p);
			this.mound.xRot = speed * 0.12F * (1.0F - e);
			this.mound.yRot = m.turn() * 0.2F;
			for (int i = 0; i < this.lobes.length; i++) {
				ModelPart lobe = this.lobes[i];
				float ph = age * 0.11F + i * 1.9F;
				float h = 0.85F + 0.25F * Mth.sin(ph) + 0.45F * speed * Mth.sin(flow + i * 1.57F);
				h = h * Mth.lerp(e, 1.0F, 0.6F) + swing * 0.5F * e;
				lobe.yScale = Mth.clamp(h * (1.0F - 0.5F * p), 0.3F, 1.5F);
				float in = Mth.lerp(e, 1.0F, LOBE_IN) * (1.0F + 0.05F * Mth.sin(ph));
				lobe.x = LOBES[i][0] * in;
				lobe.z = LOBES[i][1] * in + p * (LOBES[i][1] > 0.0F ? 3.0F : 4.0F);
				lobe.zScale = 1.0F + 1.2F * p;
			}
			// the wake drags behind, swinging with its turns, and streams out when it pours
			this.wake.yScale = (0.8F + 0.2F * breath) * (1.0F - 0.3F * p) * Mth.lerp(e, 1.0F, 0.5F);
			this.wake.zScale = (1.0F + 0.5F * speed) * (1.0F + 2.0F * p) * Mth.lerp(e, 1.0F, 0.6F);
			this.wake.xScale = 1.0F - 0.2F * p;
			this.wake.yRot = m.trail() * 0.5F;

			// ---- the legs: stubby columns that rise out of the slab; each foot stays on the floor, lifting only as it swings forward ----
			float hip = Mth.lerp(e, HIP_LOW, HIP_RISEN);
			float reach = FOOT_Y - hip;
			float amp = e * LEG_SWING * Mth.clamp(state.walkAnimationSpeed * 1.4F, 0.0F, 1.0F);
			float ph = state.walkAnimationPos * LEG_RATE;
			float stepR = Mth.cos(ph) * amp;
			float stepL = -stepR;
			float lift = FOOT_LIFT * e * Mth.clamp(state.walkAnimationSpeed * 1.4F, 0.0F, 1.0F);
			float bob = "fight".equals(act) ? Mth.sin(age * 0.3F) * 0.4F * e : 0.0F;
			// (a swung leg's toe or heel corner dips by its half depth times the swing's sine: the hip rises that much so it never sinks into the floor)
			// (0.19.84) a foot swings forward (its angle falling) while the other half of the cycle pushes back: it lifts on the swing
			float yR = hip + reach * (1.0F - Mth.cos(stepR)) - FOOT_HALF * Math.abs(Mth.sin(stepR)) - Math.max(0.0F, Mth.sin(ph)) * lift + bob;
			float yL = hip + reach * (1.0F - Mth.cos(stepL)) - FOOT_HALF * Math.abs(Mth.sin(stepL)) - Math.max(0.0F, -Mth.sin(ph)) * lift + bob;
			float legScale = reach / LEG_LEN;
			this.rightLeg.setPos(-LEG_X, yR, 0.0F);
			this.leftLeg.setPos(LEG_X, yL, 0.0F);
			this.rightLeg.xRot = stepR;
			this.leftLeg.xRot = stepL;
			this.rightLeg.yScale = this.leftLeg.yScale = legScale;

			// ---- the torso: lying face-up on the mound, it swings up to a hunch; its base point follows the hips ----
			float lean = LEAN + 0.04F * breath + swing * 0.12F;
			float a = Mth.lerp(e, -(float)Math.PI * 0.5F, lean);
			float cs = Mth.cos(a);
			float sn = Mth.sin(a);
			float baseY = Mth.lerp(e, LIE_Y, (yR + yL) * 0.5F) + p * 1.0F;
			float baseZ = Mth.lerp(e, LIE_Z, 0.0F);
			this.body.setPos(0.0F, baseY - TORSO_REACH * cs, baseZ - TORSO_REACH * sn);
			this.body.xRot = a;
			this.body.yRot = twist * e;
			this.body.zRot = (Mth.sin(age * 0.09F) * 0.03F + m.turn() * 0.08F) * e;

			// ---- the head: a squashed dome at the front of the puddle; on the torso's top once it stands ----
			// (it rides the torso's axis: at the hips' end, which lies forward, then up to the neck as the torso stands)
			float along = Mth.lerp(e, TORSO_LEN, NECK_SINK);
			this.head.setPos(0.0F, this.body.y + along * cs + (1.0F - e) * (HEAD_Y - LIE_Y), this.body.z + along * sn + (1.0F - e) * (HEAD_Z - (LIE_Z + TORSO_REACH - TORSO_LEN)));
			this.head.yScale = Mth.lerp(e, DOME, 1.0F) * (1.0F - 0.3F * p);
			this.head.xScale = Mth.lerp(e, 1.2F, 1.0F);
			this.head.zScale = Mth.lerp(e, 1.15F, 1.0F) * (1.0F + 0.8F * p);
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F) * Mth.lerp(e, 0.7F, 1.0F);
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD * Mth.lerp(e, 0.3F, 0.9F) + e * 0.1F;
			this.head.zRot = -m.turn() * 0.1F;

			// ---- the arms: nubs inside the torso until it stands, then they grow out of its sides; the swinging arm stretches ----
			float armS = ARM_MIN + (1.0F - ARM_MIN) * e;
			float wob = Mth.sin(age * 0.15F) * 0.05F * e;
			float sx = Mth.lerp(e, 2.0F, ARM_X);
			float sy = this.body.y + ARM_DROP * cs;
			float sz = this.body.z + ARM_DROP * sn;
			float tw = this.body.yRot;
			// the arm that swings is the lash: the main arm, or the other one for an off-hand swing
			boolean offSwing = state.currentSwing != null && state.currentSwing.hand() == net.minecraft.world.InteractionHand.OFF_HAND;
			boolean rightLash = (state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT) != offSwing;
			this.arm(this.rightArm, -1, sx, sy, sz, tw, armS, rightLash ? swing * LASH_STRETCH * e : 0.0F, wob);
			this.arm(this.leftArm, 1, sx, sy, sz, tw, armS, rightLash ? 0.0F : swing * LASH_STRETCH * e, -wob);
			// (0.19.84) a sneaking player is drawn CROUCH_SINK lower: everything raised that much, so the puddle stays on the floor
			if (state.isCrouching) {
				for (ModelPart part : this.all) {
					part.y -= CROUCH_SINK;
				}
			}
		}

		/** One arm at its shoulder, scaled out of the mass, flared a little, stretched by {@code stretch} but never past the floor. */
		private void arm(ModelPart arm, int side, float sx, float sy, float sz, float twist, float scale, float stretch, float wobble) {
			arm.setPos(side * Mth.cos(twist) * sx, sy, -side * Mth.sin(twist) * sx + sz);
			arm.zRot -= side * (ARM_FLARE * (scale - ARM_MIN) / (1.0F - ARM_MIN) + wobble);
			float s = scale * (1.0F + stretch);
			float down = ARM_LEN * Math.max(0.0F, Mth.cos(arm.xRot) * Mth.cos(arm.zRot));
			if (down > 0.01F) {
				s = Math.min(s, Math.max(scale, (FOOT_Y - sy) / down));
			}
			arm.xScale = arm.zScale = scale;
			arm.yScale = s;
		}
	}

	/**
	 * (0.18.96; 0.19.62 a real armadillo) Gorger: a squat armadillo on four short legs: a pointed snout with a bone horn,
	 * little ears, a domed shell of six bands over its back (the bands bob one after another as it scurries, diagonal legs
	 * together in quick little steps) and a tail dragging behind. Its head nods a beat behind the step and butts down
	 * on an attack; charging, it drops its head with the horn levelled and scrabbles faster; rolled up (B) it tucks head,
	 * legs and tail inside and the six bands close into a ball that rolls with the way it moves. The humanoid parts stay,
	 * hidden or reused: the body (hidden) carries the insignia on the back of the rump, the legs are its legs.
	 */
	static final class Gorger extends FormModel {
		// ---- tuning ----
		/** How fast its legs scurry against the walk, how far they swing and how high a foot lifts (pixels). */
		static final float GAIT = 0.6662F * 2.4F, STEP = 0.6F, LIFT = 1.0F;
		/** (charge) the legs scrabble this much faster, the head drops and the tail rises. */
		static final float CHARGE_GAIT = 1.4F, CHARGE_HEAD_DROP = 1.2F, CHARGE_HEAD_PITCH = 0.55F, CHARGE_TAIL = 0.35F;
		/** The body's dip at the widest stride (pixels), its side sway, a band's bob (pixels) and the bob's wave along the bands. */
		static final float BOB = 0.7F, SWAY = 0.4F, BAND_BOB = 0.5F, BAND_WAVE = 0.9F;
		/** How far it drops when sneaking (pixels). */
		static final float CROUCH_DROP = 1.5F;
		/** The attack: the head butts down (radians) with a little push forward (pixels; it stays on its neck). */
		static final float LUNGE = 0.4F, LUNGE_PITCH = 0.7F;
		/** Airborne, the legs reach out this far (radians); landing squashes it down this far (pixels). */
		static final float AIR_REACH = 0.5F, LAND_SQUASH = 1.0F;
		/** How far the head can look up and down (radians); its snout stops at the ground, however far it looks down. */
		static final float HEAD_UP = 0.9F, HEAD_DOWN = 1.2F;
		/** The tail at rest (radians, + is up). */
		static final float TAIL_REST = 0.15F;
		/** Rolled up: how far the ball turns for each unit of walk, and its size (the flat sides' distance from the middle). */
		static final float ROLL_PER_POS = 0.53F, BALL_APOTHEM = 7.0F, BALL_WIDTH = 1.2F;
		/** Each band of the ball a hair narrower than the last, so their flat sides don't flicker through each other. */
		static final float BALL_STEP = 0.02F;
		/** The ball is a hexagon of the six bands: 60 degrees a band, a corner this far (apothem / cos 30) from the middle. */
		private static final float SEGMENT = (float)Math.PI / 3.0F;
		private static final float BALL_CORNER = BALL_APOTHEM / 0.8660254F;
		private static final float BALL_SIDE = BALL_APOTHEM * 1.1547005F;
		/** The shell, front to back: where each band sits (z, and y of its bottom), how tall and thick it is. */
		private static final float[] SHELL_Z = {-4.5F, -2.0F, 0.0F, 2.0F, 4.0F, 6.5F};
		private static final float[] SHELL_Y = {20.0F, 18.0F, 18.0F, 18.0F, 18.0F, 20.0F};
		private static final float[] SHELL_H = {8.0F, 7.0F, 8.0F, 8.0F, 7.0F, 8.0F};
		private static final float[] SHELL_D = {3.0F, 2.0F, 2.0F, 2.0F, 2.0F, 3.0F};
		/** Front and hind legs rest here (x either side, z, y of the hip), are this long and their feet this deep (half). */
		private static final float LEG_X = 2.5F, FRONT_Z = -3.5F, HIND_Z = 4.5F, HIP_Y = 19.0F, LEG_LEN = 5.0F, FOOT_HALF = 2.0F;
		/** The head's pivot, and how far below it the snout's tip reaches at most (its lowest corner: reach * sin(pitch + angle)). */
		private static final float HEAD_Y = 17.0F, HEAD_Z = -5.5F, SNOUT_REACH = 6.32F, SNOUT_ANGLE = 0.32F;
		/** The tail's root (in the rump shield), how far below the root its tip reaches as it hangs down, and its half-thickness. */
		private static final float TAIL_Y = 20.0F, TAIL_Z = 7.0F, TAIL_REACH = 8.5F, TAIL_HALF = 1.5F;
		/** The hidden body stands here (y), at the back of the rump shield (z), facing back. */
		private static final float PLATE_Y = 15.5F, PLATE_Z = 6.0F;

		private final ModelPart torso, snout, tail, tail2, rightEar, leftEar, hindRight, hindLeft;
		private final ModelPart[] shell = new ModelPart[6];

		Gorger() {
			super(bake(mesh()), "gorger");
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.snout = this.head.getChild("snout");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
			this.tail = root.getChild("tail");
			this.tail2 = this.tail.getChild("tail2");
			this.hindRight = root.getChild("hind_right_leg");
			this.hindLeft = root.getChild("hind_left_leg");
			for (int i = 0; i < 6; i++) {
				this.shell[i] = root.getChild("shell" + i);
			}
			// on the back of the rump (the body, hidden, stands there facing back), the plate flat on its face, above the tail
			this.insigniaY = 0.0F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: a small wedge, a pointed snout with the horn on its tip, ears at the back
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -3.0F, 5.0F, 5.0F, 3.0F),
				PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(16, 0).addBox(-1.5F, -1.5F, -3.0F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(0.0F, 0.5F, -3.0F));
			snout.addOrReplaceChild("horn", CubeListBuilder.create().texOffs(18, 8).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, -1.2F, -2.0F, 0.9F, 0.0F, 0.0F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(24, 8).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(-2.0F, -2.3F, -0.5F, 0.0F, 0.0F, -0.35F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(24, 12).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(2.0F, -2.3F, -0.5F, 0.0F, 0.0F, 0.35F));
			// the soft belly and flanks under the shell
			root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -2.5F, -6.0F, 8.0F, 5.0F, 12.0F),
				PartPose.offset(0.0F, 17.5F, 1.0F));
			// the shell: shoulder shield, four bands, rump shield (centred in z so it can close into a ball)
			int[][] uv = {{0, 82}, {0, 94}, {0, 104}, {26, 104}, {26, 94}, {28, 82}};
			for (int i = 0; i < 6; i++) {
				root.addOrReplaceChild("shell" + i, CubeListBuilder.create().texOffs(uv[i][0], uv[i][1])
					.addBox(-5.0F, -SHELL_H[i], -SHELL_D[i] / 2.0F, 10.0F, SHELL_H[i], SHELL_D[i]), PartPose.offset(0.0F, SHELL_Y[i], SHELL_Z[i]));
			}
			// the legs: the man's legs are its front ones, the hind pair new
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(-LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(32, 116).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(-LEG_X, HIP_Y, HIND_Z));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(48, 116).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(LEG_X, HIP_Y, HIND_Z));
			// the tail: two tapering joints dragging behind, low, its root set into the rump shield
			PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 116).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 5.0F),
				PartPose.offset(0.0F, TAIL_Y, TAIL_Z));
			tail.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(18, 116).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, 4.5F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.body.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft}) {
				part.resetPose();
			}
			String act = state.getData(XenoformClient.ACT);
			if ("roll".equals(act)) {
				this.ball(state);
			} else {
				this.walk(state, "charge".equals(act));
			}
		}

		private void show(boolean out) {
			this.head.visible = this.torso.visible = this.tail.visible = out;
			this.rightLeg.visible = this.leftLeg.visible = this.hindRight.visible = this.hindLeft.visible = out;
		}

		/** Walking, standing, charging: the quadruped. */
		private void walk(AvatarRenderState state, boolean charge) {
			this.show(true);
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float p = state.walkAnimationPos * GAIT * (charge ? CHARGE_GAIT : 1.0F);
			float crouch = state.isCrouching ? CROUCH_DROP : 0.0F;
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			// the body dips at the widest stride, and squashes on landing
			float drop = crouch + Mth.abs(Mth.cos(p)) * BOB * speed * (1.0F - air) + Math.max(0.0F, m.land()) * LAND_SQUASH;
			float sway = Mth.sin(p) * SWAY * speed;
			// the shell: the bands bob in a wave along its back; it breathes a little
			float breath = Mth.sin(age * 0.08F) * 0.12F;
			for (int i = 0; i < 6; i++) {
				ModelPart band = this.shell[i];
				float lift = (i == 0 || i == 5) ? 0.0F : Math.max(0.0F, Mth.sin(p * 2.0F - i * BAND_WAVE)) * BAND_BOB * speed + breath * 0.5F + 0.12F;
				band.x = sway;
				band.y = SHELL_Y[i] + drop - lift;
				band.z = SHELL_Z[i];
				band.xRot = band.yRot = band.zRot = 0.0F;
				band.xScale = band.yScale = band.zScale = 1.0F;
			}
			this.torso.x = sway;
			this.torso.y = 17.5F + drop;
			this.torso.z = 1.0F;
			this.torso.xRot = this.torso.yRot = this.torso.zRot = 0.0F;
			this.torso.xScale = this.torso.yScale = this.torso.zScale = 1.0F;
			// the insignia: the hidden body stands at the rump facing back, its plate on the shield's back, above the tail
			this.body.x = sway;
			this.body.y = PLATE_Y + drop;
			this.body.z = PLATE_Z;
			this.body.yRot = (float)Math.PI;
			// the head: where it looks, nodding a beat behind the step, a sniff when idle, a snap on the attack; however far
			// it looks down, its snout stops at the ground
			float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float sniff = Mth.sin(age * 0.35F) * 0.05F * (1.0F - speed);
			this.head.x = sway;
			this.head.y = HEAD_Y + drop + (charge ? CHARGE_HEAD_DROP : 0.0F);
			this.head.z = HEAD_Z - swing * LUNGE;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.3F, 1.3F);
			float room = (24.0F - this.head.y) / SNOUT_REACH;
			float down = room >= 1.0F ? HEAD_DOWN : Math.min(HEAD_DOWN, (float)Math.asin(Math.max(0.0F, room)) - SNOUT_ANGLE);
			float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -HEAD_UP, HEAD_UP) + Mth.sin(p * 2.0F - 0.8F) * 0.06F * speed + sniff
				+ swing * LUNGE_PITCH + (charge ? CHARGE_HEAD_PITCH : 0.0F);
			this.head.xRot = Math.max(-HEAD_UP, Math.min(down, pitch));
			this.snout.xRot = sniff * 0.8F;
			// the ears prick up and flick now and then
			float flick = Math.max(0.0F, Mth.sin(age * 0.11F + 1.0F) - 0.85F) * 2.0F;
			this.rightEar.zRot = -0.35F - flick - m.trail() * 0.1F;
			this.leftEar.zRot = 0.35F + flick + m.trail() * 0.1F;
			// the tail: drags low, sways with the walk and the turn, lifts when it falls or charges; it never dips through
			// the ground (landing or sneaking would whip it down)
			this.tail.x = sway;
			this.tail.y = TAIL_Y + drop;
			float floor = -(float)Math.asin(Mth.clamp((24.0F - this.tail.y - TAIL_HALF) / TAIL_REACH, 0.0F, 1.0F));
			this.tail.xRot = Math.max(floor, TAIL_REST + tailLift(m) + (charge ? CHARGE_TAIL : 0.0F));
			this.tail.yRot = Mth.sin(age * 0.07F) * 0.12F - Mth.sin(p) * 0.25F * speed - m.turn() * 0.5F;
			this.tail2.xRot = 0.1F;
			this.tail2.yRot = Mth.sin(age * 0.07F - 0.8F) * 0.15F - m.turn() * 0.3F;
			// the legs: diagonal pairs together; each pivots at the hip, which sinks or rises as the leg swings so the
			// lowest corner of its foot meets the ground, and rises as a foot lifts through its forward swing; in the air
			// the hips stay put (in the body) and the legs reach out
			float leg = (LEG_LEN - crouch) / LEG_LEN;
			float len = LEG_LEN * leg;
			ModelPart[] legs = {this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft};
			float[] phase = {0.0F, (float)Math.PI, (float)Math.PI, 0.0F};
			for (int i = 0; i < 4; i++) {
				ModelPart foot = legs[i];
				float theta = Mth.cos(p + phase[i]) * STEP * speed + (i < 2 ? -AIR_REACH : AIR_REACH) * air;
				float up = Math.max(0.0F, Mth.sin(p + phase[i])) * LIFT * speed * (1.0F - air);
				foot.xRot = theta;
				foot.yScale = leg;
				foot.y = HIP_Y + crouch + (len * (1.0F - Mth.cos(theta)) - FOOT_HALF * Mth.abs(Mth.sin(theta))) * (1.0F - air) - up;
			}
		}

		/** Rolled up: head, legs and tail tucked away, the six bands closed into a hexagonal ball that turns as it moves. */
		private void ball(AvatarRenderState state) {
			this.show(false);
			float roll = state.walkAnimationPos * ROLL_PER_POS;
			float rem = roll - (float)Math.floor(roll / SEGMENT) * SEGMENT;
			// resting on a flat side, a corner lifts it as it turns over
			float cy = 24.0F - BALL_CORNER * Mth.cos(rem - SEGMENT * 0.5F);
			for (int i = 0; i < 6; i++) {
				ModelPart band = this.shell[i];
				band.x = 0.0F;
				band.y = cy;
				band.z = 1.0F;
				band.xRot = roll + i * SEGMENT;
				band.yRot = band.zRot = 0.0F;
				band.xScale = BALL_WIDTH - i * BALL_STEP;
				band.yScale = BALL_APOTHEM / SHELL_H[i];
				band.zScale = BALL_SIDE / SHELL_D[i];
			}
			// the plate sits inside the ball, out of sight
			this.body.x = 0.0F;
			this.body.y = cy;
			this.body.z = 1.0F;
			this.body.yRot = (float)Math.PI;
		}
	}

	/**
	 * (0.18.96; 0.19.66 axolotl) Slipfin: an axolotl, long and low. A wide flat head with a hinged jaw and three
	 * feathery gill stalks each side that wave as it breathes and sweep back as it swims, a soft pink trunk in two
	 * parts, a deep flat finned tail of four segments, four small splayed legs. On land it belly-crawls the way a
	 * salamander does: the legs paddle in diagonal pairs (front right with hind left) while the trunk and tail bend
	 * into a travelling S, the head holding where it looks; sneaking presses it flat. In water it swims by undulating:
	 * the legs tucked back along its sides and a wave running down the body and tail, nose pitched where it looks. A
	 * bite is a darting lunge of the head with the jaw flung open. Every segment hangs off the one in front of it
	 * (trunk, rear trunk, tail; legs on the trunk they belong to), so nothing comes apart as it bends. The man's own
	 * parts are hidden; the hidden body carries the insignia on the back of the front trunk.
	 */
	static final class Slipfin extends FormModel {
		// ---- tuning ----
		/** Model y of the ground, of the trunk's centre belly-crawling, how far sneaking sinks it, and swimming. */
		static final float GROUND = 24.0F, CRAWL_Y = 20.0F, CROUCH_DROP = 0.4F, SWIM_Y = 13.5F;
		/** The renderer draws a sneaking player this many pixels lower (AvatarRenderer.getRenderOffset): the animal is raised that much to stay on the ground. */
		static final float CROUCH_SINK = 2.0F;
		/** Where the front trunk's centre sits (model z), so the whole length is centred on the player. */
		static final float HUB_Z = -8.0F;
		/** A leg's length (its box height), and the lowest its pivot may sit on the trunk (so the leg stays attached). */
		static final float LEG_LEN = 3.0F, LEG_TOP_MAX = 1.8F;
		/** The paddling gait: how fast against the walk, how far a leg swings (radians), how high a stepping foot lifts (px). */
		static final float GAIT = 0.6662F * 1.25F, LEG_SWING = 0.7F, LEG_LIFT = 1.1F;
		/** Splay: a leg's outward angle at rest and sneaking, and how much more it opens mid-step (radians). */
		static final float SPLAY = 0.35F, SPLAY_CROUCH = 0.7F, SPLAY_STEP = 0.2F;
		/** How far the walk reads as full (walk speed times this, capped at 1), and the idle sway at a standstill. */
		static final float WALK_FULL = 1.6F, IDLE_SWAY = 0.35F;
		/** The land S-wave: each joint's yaw amplitude and phase (front trunk, rear trunk, then the four tail segments). */
		private static final float[] WAVE_YAW = {0.12F, 0.14F, 0.2F, 0.28F, 0.36F, 0.44F};
		private static final float[] WAVE_PHASE = {0.0F, -3.1416F, -3.9F, -4.7F, -5.5F, -6.3F};
		/** The swim wave: each joint's pitch amplitude (radians), the lag between joints, and its speed. */
		private static final float[] SWIM_PITCH = {0.04F, 0.07F, 0.11F, 0.16F, 0.21F, 0.26F};
		static final float SWIM_LAG = 0.75F, SWIM_RATE = 0.33F, SWIM_STRIDE = 1.0F, SWIM_LOOK = 0.35F;
		/** Swimming: the legs' tuck (back along the body), their outward angle, and their flutter. */
		static final float TUCK = 1.25F, TUCK_SPLAY = 0.2F, TUCK_FLUTTER = 0.12F;
		/** How far the head may turn on its neck, how far it nods up and down (radians); lunge: reach (px) and jaw (radians). */
		static final float HEAD_TURN = 0.75F, NOD_UP = 0.45F, NOD_DOWN = 0.04F, LUNGE_REACH = 2.2F, LUNGE_JAW = 0.6F;
		/** The skull's pivot height on the front trunk, and how far it rises as the nose tips up (its length behind the pivot, so its back never digs in). */
		static final float SKULL_Y = 1.0F, SKULL_LIFT = 3.0F;
		/** The gill stalks' heights on the skull's sides and their fan (radians, + is up): top, middle, bottom. */
		private static final float[] GILL_Y = {-1.5F, -0.5F, 0.5F};
		private static final float[] GILL_FAN = {0.75F, 0.35F, 0.0F};
		private static final int JOINTS = 6;
		/** Where the rear trunk hangs off the front one (hub z), the hind legs' z on it, and the front legs' z on the hub. */
		private static final float REAR_Z = 3.0F, HIND_LEG_Z = 4.0F, FORE_LEG_Z = 0.5F;
		/** How far out the legs' pivots sit: the front pair on the front trunk, the hind pair on the (slightly shrunk) rear one. */
		private static final float FORE_LEG_X = 3.5F, HIND_LEG_X = 3.2F;
		/** Tail segments: depth (their next one hangs one pixel inside the end). */
		private static final float[] TAIL_DEPTH = {5.0F, 5.0F, 5.0F, 4.0F};

		private final ModelPart hub, skull, jaw, rear;
		private final ModelPart[] gills = new ModelPart[6];
		private final ModelPart[] tail = new ModelPart[4];
		/** Front right, front left, hind right, hind left. */
		private final ModelPart[] legs = new ModelPart[4];
		/** Each joint's yaw and pitch, from the front trunk to the tail tip (relative to the world, not the joint before). */
		private final float[] yaw = new float[JOINTS], pitch = new float[JOINTS];

		Slipfin() {
			super(bake(mesh()), "slipfin");
			ModelPart root = this.root();
			this.hub = root.getChild("hub");
			this.skull = this.hub.getChild("skull");
			this.jaw = this.skull.getChild("jaw");
			this.rear = this.hub.getChild("rear");
			for (int i = 0; i < 6; i++) {
				this.gills[i] = this.skull.getChild("gill_" + i);
			}
			ModelPart parent = this.rear;
			for (int i = 0; i < 4; i++) {
				this.tail[i] = parent.getChild("tail_" + i);
				parent = this.tail[i];
			}
			this.legs[0] = this.hub.getChild("leg_0");
			this.legs[1] = this.hub.getChild("leg_1");
			this.legs[2] = this.rear.getChild("leg_2");
			this.legs[3] = this.rear.getChild("leg_3");
			// the plate lies on the back of the front trunk (the hidden body is laid face-up there each frame)
			this.insigniaY = 0.0F;
			this.insigniaZ = 0.0F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the front trunk (6 wide, 4 tall, 8 long), centred on its pivot
			PartDefinition hub = root.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 8.0F),
				PartPose.offset(0.0F, CRAWL_Y, HUB_Z));
			// the head: a wide flat skull reaching back inside the trunk, so a lunge never opens a neck gap, and the jaw under it
			PartDefinition skull = hub.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 76).addBox(-4.0F, -2.0F, -6.0F, 8.0F, 3.0F, 9.0F),
				PartPose.offset(0.0F, SKULL_Y, -3.5F));
			skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(34, 76).addBox(-3.0F, 0.0F, -5.0F, 6.0F, 1.0F, 5.0F),
				PartPose.offset(0.0F, 1.0F, -1.0F));
			// three gill stalks a side: 5 long, fanned from steeply up to level (a lower one would drag on the ground); the left ones mirror
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				int row = i % 3;
				CubeListBuilder gill = CubeListBuilder.create().texOffs(34, 82);
				if (left) {
					gill.mirror().addBox(0.0F, -0.5F, -1.0F, 5.0F, 1.0F, 2.0F);
				} else {
					gill.addBox(-5.0F, -0.5F, -1.0F, 5.0F, 1.0F, 2.0F);
				}
				skull.addOrReplaceChild("gill_" + i, gill, PartPose.offsetAndRotation(left ? 4.0F : -4.0F, GILL_Y[row], 0.0F, 0.0F, 0.0F, (left ? -1.0F : 1.0F) * GILL_FAN[row]));
			}
			// the rear trunk hangs one pixel inside the front one's end (a hair smaller, so their faces never share a plane)
			CubeDeformation shrink = new CubeDeformation(-0.3F);
			PartDefinition rear = hub.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(28, 64).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 4.0F, 8.0F, shrink),
				PartPose.offset(0.0F, 0.0F, REAR_Z));
			// the tail: four segments, each hung one pixel inside the end of the one before, deep and flat like a fin
			PartDefinition t0 = rear.addOrReplaceChild("tail_0", CubeListBuilder.create().texOffs(0, 88).addBox(-2.0F, -2.5F, 0.0F, 4.0F, 5.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, 7.0F));
			PartDefinition t1 = t0.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(18, 88).addBox(-1.5F, -2.5F, 0.0F, 3.0F, 5.0F, 5.0F, shrink),
				PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[0] - 1.0F));
			PartDefinition t2 = t1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(34, 88).addBox(-1.0F, -2.0F, 0.0F, 2.0F, 4.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[1] - 1.0F));
			t2.addOrReplaceChild("tail_3", CubeListBuilder.create().texOffs(48, 88).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 4.0F, shrink),
				PartPose.offset(0.0F, 0.0F, TAIL_DEPTH[2] - 1.0F));
			// the legs: stubby 2x3x3 paddles, their inner face half a pixel inside the trunk; right then left, front pair on the front trunk
			for (int i = 0; i < 4; i++) {
				boolean left = (i & 1) == 1;
				CubeListBuilder leg = CubeListBuilder.create().texOffs(48, 82);
				if (left) {
					leg.mirror();
				}
				leg.addBox(-1.0F, 0.0F, -1.5F, 2.0F, 3.0F, 3.0F);
				PartDefinition parent = i < 2 ? hub : rear;
				parent.addOrReplaceChild("leg_" + i, leg, PartPose.offset((left ? 1.0F : -1.0F) * (i < 2 ? FORE_LEG_X : HIND_LEG_X), 1.2F, i < 2 ? FORE_LEG_Z : HIND_LEG_Z));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man's parts are all hidden; the hidden body only carries the insignia
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			boolean swim = state.isInWater;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * WALK_FULL);
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// a sneaking player is drawn CROUCH_SINK lower: the ground, in model y, is that much higher
			float sink = state.isCrouching ? CROUCH_SINK : 0.0F;
			float ground = GROUND - sink;
			float hubY;
			if (swim) {
				hubY = SWIM_Y - sink + Mth.sin(age * 0.09F) * 0.6F;
				// the nose pitched where it looks (more as it moves), a wave running down the length of it
				float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.7F, -SWIM_LOOK, SWIM_LOOK) * (0.4F + 0.6F * speed);
				float t = age * SWIM_RATE * 0.5F + state.walkAnimationPos * SWIM_STRIDE;
				float amp = 0.45F + 0.55F * speed;
				for (int j = 0; j < JOINTS; j++) {
					this.pitch[j] = look + SWIM_PITCH[j] * amp * Mth.sin(t * 2.0F - j * SWIM_LAG);
					this.yaw[j] = 0.05F * Mth.sin(age * 0.07F - j * 0.5F);
				}
			} else {
				hubY = CRAWL_Y + (state.isCrouching ? CROUCH_DROP : 0.0F) - sink;
				float phi = state.walkAnimationPos * GAIT;
				float idle = (1.0F - speed) * IDLE_SWAY;
				// how far the tail is lifted (it flares as it falls, whips down on landing), spread along its length
				float lift = Mth.clamp(tailLift(m), -0.15F, 0.5F);
				for (int j = 0; j < JOINTS; j++) {
					this.yaw[j] = WAVE_YAW[j] * (speed * Mth.sin(phi + WAVE_PHASE[j]) + idle * Mth.sin(age * 0.05F - j * 0.8F));
					this.pitch[j] = 0.0F;
					if (j >= 2) {
						this.yaw[j] -= m.trail() * 0.1F * (j - 1);
						this.pitch[j] = lift * (j - 1) * 0.25F;
					}
				}
			}
			// the spine: the front trunk is placed in the world, each joint after it bends by the difference in angle
			// (swimming, the whole animal pitches about its middle, not about the front trunk)
			float tilt = swim ? this.pitch[0] : 0.0F;
			this.hub.x = 0.0F;
			this.hub.y = hubY - HUB_Z * Mth.sin(tilt);
			this.hub.z = HUB_Z * Mth.cos(tilt);
			this.hub.xRot = this.pitch[0];
			this.hub.yRot = this.yaw[0];
			this.hub.zRot = 0.0F;
			this.rear.xRot = this.pitch[1] - this.pitch[0];
			this.rear.yRot = this.yaw[1] - this.yaw[0];
			for (int i = 0; i < 4; i++) {
				this.tail[i].xRot = this.pitch[i + 2] - this.pitch[i + 1];
				this.tail[i].yRot = this.yaw[i + 2] - this.yaw[i + 1];
			}
			// the insignia: the hidden body laid face-up on the middle of the front trunk's back, tilted and turned with it
			float sp = Mth.sin(this.pitch[0]);
			this.body.x = -2.0F * sp * Mth.sin(this.yaw[0]);
			this.body.y = this.hub.y - 2.0F * Mth.cos(this.pitch[0]);
			this.body.z = this.hub.z - 2.0F * sp * Mth.cos(this.yaw[0]);
			this.body.xRot = this.pitch[0] - Mth.HALF_PI;
			this.body.yRot = this.yaw[0];
			this.legs(state, m, swim, ground, hubY, speed, age);
			this.face(state, m, swim, speed, age, bite);
		}

		/** The legs: paddling in diagonal pairs on land (feet planted through the stance), tucked back and fluttering swimming. */
		private void legs(AvatarRenderState state, XenoMotion.Read m, boolean swim, float ground, float hubY, float speed, float age) {
			float phi = state.walkAnimationPos * GAIT;
			for (int k = 0; k < 4; k++) {
				boolean left = (k & 1) == 1;
				ModelPart leg = this.legs[k];
				if (swim) {
					leg.xRot = TUCK + TUCK_FLUTTER * Mth.sin(age * 0.3F + k * 1.3F);
					leg.zRot = (left ? -1.0F : 1.0F) * TUCK_SPLAY;
					leg.y = 1.0F;
					continue;
				}
				// front right with hind left, front left with hind right
				float ph = phi + ((k == 0 || k == 3) ? 0.0F : (float)Math.PI);
				// + swings the foot back (the stance, on the ground), - brings it forward (the swing, lifted)
				float a = LEG_SWING * speed * Mth.sin(ph);
				float step = Math.max(0.0F, -Mth.cos(ph)) * speed;
				// in the air the legs dangle and kick
				a += m.air() * (0.5F + 0.3F * Mth.sin(age * 0.5F + k * 1.7F));
				float splay = (state.isCrouching ? SPLAY_CROUCH : SPLAY) + SPLAY_STEP * step;
				leg.xRot = a;
				leg.zRot = (left ? -1.0F : 1.0F) * splay;
				// the pivot rides so the lowest corner of the paw stays on the ground (up by the lift on the swing), and
				// never so low that the leg's top leaves the trunk's side
				leg.y = Math.min(LEG_TOP_MAX, ground - hubY - drop(a, (left ? -1.0F : 1.0F) * splay) - LEG_LIFT * step);
			}
		}

		/** How far below its pivot a leg's lowest corner hangs once turned by {@code a} (forward-back) and {@code zr} (out). */
		private static float drop(float a, float zr) {
			float lowest = -1.0E9F;
			for (int x = -1; x <= 1; x += 2) {
				for (int y = 0; y <= 1; y++) {
					for (int z = -1; z <= 1; z += 2) {
						float y1 = y * LEG_LEN * Mth.cos(a) - z * 1.5F * Mth.sin(a);
						lowest = Math.max(lowest, x * Mth.sin(zr) + y1 * Mth.cos(zr));
					}
				}
			}
			return lowest;
		}

		/** The head on its neck, the jaw, and the gills. */
		private void face(AvatarRenderState state, XenoMotion.Read m, boolean swim, float speed, float age, float bite) {
			float turn = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -HEAD_TURN, HEAD_TURN) - this.yaw[0] * 0.5F;
			float nod;
			if (swim) {
				nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.3F, -0.3F, 0.3F) - this.pitch[0] * 0.4F;
			} else {
				nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.5F, -NOD_UP, NOD_DOWN);
			}
			// a bite: the head darts out, tips up, the jaw flung open; tipped up, it rises so its back (and the swept
			// gills behind its pivot) stay off the ground
			float tip = nod - bite * 0.4F;
			this.skull.x = 0.0F;
			this.skull.y = SKULL_Y - bite * 0.8F + m.breath() * 0.08F - SKULL_LIFT * Mth.sin(Math.max(0.0F, -tip));
			this.skull.z = -3.5F - bite * LUNGE_REACH;
			this.skull.yRot = turn;
			this.skull.xRot = tip;
			this.jaw.xRot = 0.05F + m.breath() * 0.03F + bite * LUNGE_JAW + (swim ? 0.04F + 0.04F * Mth.sin(age * 0.2F) : 0.0F);
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				int row = i % 3;
				float s = left ? -1.0F : 1.0F;
				// the gills sweep back as it moves or swims, and flutter all the time
				float sweep = 0.4F + (swim ? 0.45F : 0.0F) + speed * 0.3F + bite * 0.2F;
				float flutter = Mth.sin(age * 0.22F + i * 1.1F) * 0.16F * (1.0F + row * 0.3F) + m.breath() * 0.05F;
				this.gills[i].yRot = s * (sweep + flutter);
				this.gills[i].zRot = s * (GILL_FAN[row] + Mth.sin(age * 0.17F + i * 0.7F) * 0.1F);
			}
		}
	}

	/** (0.18.96) Polestar: broad, banded like a magnet, a horseshoe of two prongs rising off its head, plates on its shoulders. */
	static final class Polestar extends FormModel {
		Polestar() {
			super(bake(mesh()), "polestar");
			this.insigniaZ = -2.7F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.7F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_prong", CubeListBuilder.create().texOffs(0, 64).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offsetAndRotation(-3.5F, -8.5F, 0.0F, 0.0F, 0.0F, -0.12F));
			head.addOrReplaceChild("left_prong", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offsetAndRotation(3.5F, -8.5F, 0.0F, 0.0F, 0.0F, 0.12F));
			root.getChild("body").addOrReplaceChild("plates", CubeListBuilder.create().texOffs(0, 74).addBox(-6.0F, -1.0F, -3.0F, 12.0F, 3.0F, 6.0F, new CubeDeformation(0.7F)),
				PartPose.ZERO);
			return mesh;
		}
	}

	/**
	 * (0.18.96; 0.19.84 on all fours when it stalks) Lurk, the stalker: tall and thin, arms that hang past its knees ending
	 * in long fingers, a face that's mostly eyes. It holds its head a little forward and tilted, as if listening. Sneaking
	 * or faded ({@code ACT} "fade", set while Fade is on) it drops onto all fours: the long arms become forelegs, hands flat
	 * on the ground, the torso tipped nearly level, the knees folded up under the belly and the head carried forward and
	 * level, the eyes watching; it changes stance over {@link #STANCE_TICKS}. Standing, the man's head, arms and legs are
	 * reshaped (a bulb of a head with two eye domes, thin arms of an upper arm, forearm and long hand, bent legs); on all
	 * fours a swing is a pounce: the body rises and lunges and the forepaws strike. The hidden body keeps the insignia: on
	 * the chest standing, on the back on all fours.
	 */
	static final class Lurk extends FormModel {
		// ---- tuning ----
		/** The long bones (model pixels): upper arm and forearm (the hand runs {@link #HAND_LONG} past the wrist), thigh and shin (down to the ankle, the middle of a flat foot). */
		static final float UPPER_ARM = 6.0F, FOREARM = 8.0F, THIGH = 7.0F, SHIN = 7.0F;
		/** In the torso's space (its pivot is the hips; the box runs from NECK_Y to a pixel below): the neck's base, the shoulders (down, out), the chest's middle (the insignia) and how far out the hips are. */
		static final float NECK_Y = -10.0F, SHOULDER_Y = -9.0F, SHOULDER_X = 3.0F, CHEST_Y = -5.0F, HIP_X = 1.5F;
		/** Standing and on all fours: how high the hips are above the ground, how far the torso leans forward (radians) and how far back the hips are (z). */
		static final float STAND_HIP_UP = 13.6F, FOURS_HIP_UP = 8.0F, STAND_PITCH = 0.1F, FOURS_PITCH = 1.2F, FOURS_HIP_Z = 8.0F;
		/** How long the change of stance takes (ticks), and how far the renderer draws a sneaking player lower (the ground is that much higher in model space). */
		static final float STANCE_TICKS = 9.0F, CROUCH_SINK = 2.0F;
		/** An ankle (and a flat wrist) sits this far above the ground: the foot and the hand are 2 thick. The hand runs this far past the wrist. */
		static final float ANKLE_UP = 1.0F, HAND_LONG = 5.0F;
		/** The head: how far it pokes forward of the neck's base (standing, on all fours), how far it drops there, its tilt (radians), how much it tips forward standing, and how much of the look it follows on all fours. */
		static final float STAND_HEAD_FWD = 0.5F, FOURS_HEAD_FWD = 4.5F, FOURS_HEAD_DROP = 0.5F, STAND_TILT = 0.18F, FOURS_TILT = 0.08F, STAND_DIP = 0.15F, FOURS_LOOK = 0.6F;
		/** The gaits: the cycle against the walk (standing, on all fours), and the walking speed that is a full step. */
		static final float STAND_GAIT = 0.6662F, FOURS_GAIT = 0.5F, STEP_FULL = 0.3F;
		/** Standing: how far the legs stride (half a stride), how high a foot lifts, how much of the step it is down, how far the hips dip and how far a landing squats. */
		static final float STAND_STRIDE = 4.0F, STAND_LIFT = 2.6F, STAND_DUTY = 0.6F, STAND_BOB = 0.6F, STAND_DIP_LAND = 1.5F;
		/** On all fours: where the hind and fore feet rest (z), how far they stride, how high they lift, how much of the step they are down, the hips' bob, the landing squat and the sway of the spine (radians). */
		static final float HIND_REST_Z = 9.5F, FORE_REST_Z = -7.0F, HIND_STRIDE = 2.0F, FORE_STRIDE = 2.5F, HIND_LIFT = 2.0F, FORE_LIFT = 3.0F;
		static final float FOURS_DUTY = 0.75F, FOURS_BOB = 0.5F, FOURS_DIP_LAND = 1.0F, SWAY = 0.1F;
		/** A run stretches the stride by this much. */
		static final float RUN_STRETCH = 0.35F;
		/** The walking part of the vanilla arm swing that is kept (the arms are long): the rest is taken out. */
		static final float ARM_KEEP = 0.45F;
		/** Standing, a swing leans the torso this far forward. */
		static final float STAND_LUNGE = 0.2F;
		/** The pounce (a swing on all fours): how far the torso rises (radians), the hips rise and lunge (pixels), the head thrusts and dips, how far the forepaws reach, rise and drag back, how far the fingers claw down, and how much of it the left hand does. */
		static final float RISE_PITCH = 0.6F, RISE_UP = 2.5F, LUNGE = 3.0F, THRUST = 2.0F, STRIKE_DIP = 0.2F;
		static final float STRIKE_REACH = 6.0F, STRIKE_UP = 8.0F, STRIKE_DRAG = 4.0F, CLAW = 1.0F, OFF_HAND = 0.6F;
		/** In the air: how far the feet lift and trail (hind, standing then on all fours) and how far the forepaws reach forward and up. */
		static final float AIR_LIFT_STAND = 2.5F, AIR_LIFT_FOURS = 4.5F, AIR_BACK_STAND = 0.5F, AIR_BACK_FOURS = 3.0F, AIR_FORE_FWD = 3.0F, AIR_FORE_UP = 3.5F, FALL_TILT = 0.25F;
		/** How far the torso breathes at rest (a fraction of its length). */
		static final float BREATHE = 0.012F;
		private static final float TAU = (float)Math.PI * 2.0F;
		private static final float HALF_PI = (float)Math.PI * 0.5F;
		/** Each foot's place in the cycle on all fours (hind right, fore right, hind left, fore left): a lateral walk. */
		private static final float[] FOURS_PHASE = {0.0F, 0.25F, 0.5F, 0.75F};

		private final ModelPart torso, neck, rightEye, leftEye;
		private final ModelPart rightForearm, leftForearm, rightHand, leftHand, rightShin, leftShin, rightFoot, leftFoot;
		private final ModelPart[] moving;
		/** Each drawn entity's stance (0 standing, 1 on all fours) and the age it was last stepped at. */
		private final Map<Integer, float[]> stances = new java.util.HashMap<>();
		/** Scratch: a point in the torso's space made world, a foot's path, a limb's solved angles. */
		private final float[] point = new float[3];
		private final float[] step = new float[2];
		private final float[] angles = new float[2];

		Lurk() {
			super(bake(mesh()), "lurk");
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.neck = root.getChild("neck");
			this.rightEye = this.head.getChild("right_eye");
			this.leftEye = this.head.getChild("left_eye");
			this.rightForearm = this.rightArm.getChild("forearm");
			this.leftForearm = this.leftArm.getChild("forearm");
			this.rightHand = this.rightForearm.getChild("hand");
			this.leftHand = this.leftForearm.getChild("hand");
			this.rightShin = this.rightLeg.getChild("shin");
			this.leftShin = this.leftLeg.getChild("shin");
			this.rightFoot = this.rightShin.getChild("foot");
			this.leftFoot = this.leftShin.getChild("foot");
			this.moving = new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.torso, this.neck,
				this.rightEye, this.leftEye, this.rightForearm, this.leftForearm, this.rightHand, this.leftHand, this.rightShin, this.leftShin,
				this.rightFoot, this.leftFoot};
			// the hidden body carries the plate: on the chest standing, on the back on all fours (see setupAnim)
			this.insigniaY = 0.0F;
			this.insigniaZ = -1.5F;
			this.insigniaSize = 0.7F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
			PartDefinition root = mesh.getRoot();
			float hipY = 24.0F - STAND_HIP_UP;
			// the head: a tall bulb, two domes of eyes on its face, a neck (a root part, so it can stretch from the torso to the head)
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, hipY + NECK_Y, -STAND_HEAD_FWD));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(24, 0).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F), PartPose.offset(-1.5F, -3.5F, -3.5F));
			head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(24, 4).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 1.0F), PartPose.offset(1.5F, -3.5F, -3.5F));
			root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 12).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 4.0F, 3.0F),
				PartPose.offset(0.0F, hipY + SHOULDER_Y, 0.0F));
			// the torso: thin, pivoting at the hips; the man's body stays hidden, to carry the insignia
			root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(16, 16).addBox(-2.5F, NECK_Y, -1.5F, 5.0F, 11.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, hipY, 0.0F, STAND_PITCH, 0.0F, 0.0F));
			// the arms: an upper arm, a forearm a hair thinner, a long hand; every box runs a pixel past its joints
			arm(root, "right_arm", false, 40, 16, 48, 16, 0, 76);
			arm(root, "left_arm", true, 32, 48, 40, 48, 10, 76);
			// the legs: a thigh, a shin a hair thinner, a flat foot with the toes forward
			leg(root, "right_leg", false, 0, 64, 8, 64, 32, 64);
			leg(root, "left_leg", true, 16, 64, 24, 64, 48, 64);
			return mesh;
		}

		private static void arm(PartDefinition root, String name, boolean left, int upperU, int upperV, int foreU, int foreV, int handU, int handV) {
			float side = left ? 1.0F : -1.0F;
			PartDefinition arm = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(upperU, upperV)
				.addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(-0.1F, 0.0F, -0.1F)),
				PartPose.offset(side * SHOULDER_X, 24.0F - STAND_HIP_UP + SHOULDER_Y, 0.0F));
			PartDefinition fore = arm.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(foreU, foreV)
				.addBox(-1.0F, -1.0F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(-0.3F, 0.0F, -0.3F)),
				PartPose.offset(0.0F, UPPER_ARM, 0.0F));
			fore.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(handU, handV).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 6.0F, 2.0F),
				PartPose.offset(0.0F, FOREARM, 0.0F));
		}

		private static void leg(PartDefinition root, String name, boolean left, int thighU, int thighV, int shinU, int shinV, int footU, int footV) {
			float side = left ? 1.0F : -1.0F;
			PartDefinition leg = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(thighU, thighV)
				.addBox(-1.0F, -1.0F, -1.0F, 2.0F, 9.0F, 2.0F, new CubeDeformation(-0.1F, 0.0F, -0.1F)),
				PartPose.offset(side * HIP_X, 24.0F - STAND_HIP_UP, 0.0F));
			PartDefinition shin = leg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(shinU, shinV)
				.addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(-0.3F, 0.0F, -0.3F)),
				PartPose.offset(0.0F, THIGH, 0.0F));
			shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(footU, footV).addBox(-1.5F, -1.0F, -4.0F, 3.0F, 2.0F, 5.0F),
				PartPose.offset(0.0F, SHIN, 0.0F));
		}

		/** 0-1 eased. */
		private static float smooth(float s) {
			return s * s * (3.0F - 2.0F * s);
		}

		/** The fraction of {@code x}. */
		private static float frac(float x) {
			return x - (float)Math.floor(x);
		}

		/** {@code state}'s entity's stance this frame, 0 standing to 1 on all fours: it moves toward its target over {@link #STANCE_TICKS}, stepped by the entity's age so a second draw in the same frame changes nothing. */
		private float stance(AvatarRenderState state, boolean fours) {
			float now = state.ageInTicks;
			float[] s = this.stances.get(state.id);
			if (s == null) {
				if (this.stances.size() > 64) {
					this.stances.clear();
				}
				s = new float[] {fours ? 1.0F : 0.0F, now};
				this.stances.put(state.id, s);
			}
			if (now < s[1] || now - s[1] > 100.0F) {
				// seen again after a while (or its age restarted): no slow change from where it was
				s[0] = fours ? 1.0F : 0.0F;
				s[1] = now;
			}
			float dt = Math.min(now - s[1], 3.0F);
			if (dt > 0.0F) {
				s[1] = now;
				s[0] = fours ? Math.min(1.0F, s[0] + dt / STANCE_TICKS) : Math.max(0.0F, s[0] - dt / STANCE_TICKS);
			}
			return s[0];
		}

		/**
		 * A point in the torso's space made world, into {@link #point}: the torso leans forward by {@code pitch} about the
		 * hips (at {@code py}, {@code pz}), then yaws by {@code yaw} about the upright through them.
		 */
		private void at(float py, float pz, float pitch, float yaw, float lx, float ly, float lz) {
			float y1 = ly * Mth.cos(pitch) - lz * Mth.sin(pitch);
			float z1 = ly * Mth.sin(pitch) + lz * Mth.cos(pitch);
			this.point[0] = lx * Mth.cos(yaw) + z1 * Mth.sin(yaw);
			this.point[1] = py + y1;
			this.point[2] = pz - lx * Mth.sin(yaw) + z1 * Mth.cos(yaw);
		}

		/**
		 * A foot's path against the body for a point in the cycle, into {@link #step}: on the ground it slides back at a steady
		 * pace, then it swings forward in an arc. {fore-aft offset (+ is back), height off the ground}.
		 */
		private void path(float p, float duty, float stride, float lift) {
			if (p < duty) {
				this.step[0] = -stride + 2.0F * stride * (p / duty);
				this.step[1] = 0.0F;
				return;
			}
			float v = (p - duty) / (1.0F - duty);
			this.step[0] = stride * Mth.cos((float)Math.PI * v);
			this.step[1] = lift * Mth.sin((float)Math.PI * v);
		}

		/**
		 * Two bones from a hip to an ankle in the side plane (z along the body, y down): the upper bone's and the lower bone's
		 * angles from straight down about x (+ swings the end back), into {@link #angles}. {@code bend} is +1 when the middle
		 * joint points back, -1 when it points forward. A target out of reach is stretched to the nearest reachable point.
		 */
		private void solve(float hipZ, float hipY, float targetZ, float targetY, float upper, float lower, float bend) {
			float dz = targetZ - hipZ;
			float dy = targetY - hipY;
			float dist = Mth.clamp((float)Math.sqrt(dz * dz + dy * dy), Math.abs(upper - lower) + 0.05F, upper + lower - 0.001F);
			float toTarget = (float)Math.atan2(dz, dy);
			float cos = Mth.clamp((upper * upper + dist * dist - lower * lower) / (2.0F * upper * dist), -1.0F, 1.0F);
			float t1 = toTarget + bend * (float)Math.acos(cos);
			float kneeZ = hipZ + upper * Mth.sin(t1);
			float kneeY = hipY + upper * Mth.cos(t1);
			this.angles[0] = t1;
			this.angles[1] = (float)Math.atan2(targetZ - kneeZ, targetY - kneeY);
		}

		/** The first-person arm (see XenoformHand): the forearm and hand straight, as they hang. */
		void handPose() {
			for (ModelPart part : new ModelPart[] {this.rightForearm, this.leftForearm, this.rightHand, this.leftHand}) {
				part.resetPose();
			}
			this.rightForearm.xRot = this.leftForearm.xRot = -0.15F;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// what vanilla made of the arms (held poses, swings, the idle sway) and of the body's twist in a swing: kept while it stands
			float twist = this.body.yRot;
			float rightX = this.rightArm.xRot, rightY = this.rightArm.yRot, rightZ = this.rightArm.zRot;
			float leftX = this.leftArm.xRot, leftY = this.leftArm.yRot, leftZ = this.leftArm.zRot;
			if (state.isCrouching) {
				// (vanilla tips a sneaking man's arms forward: the stance does that here, so a sneak doesn't jerk the arms first)
				rightX -= 0.4F;
				leftX -= 0.4F;
			}
			this.hat.visible = this.body.visible = false;
			for (ModelPart part : this.moving) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			boolean sneaking = state.isCrouching;
			// sneaking, or faded ("fade", Lurk.faded on the server): all fours. (The shed skin kneels the same way.)
			float q = smooth(this.stance(state, sneaking || "fade".equals(state.getData(XenoformClient.ACT))));
			// a sneaking player is drawn CROUCH_SINK lower (a mob, the shed skin, is not): the ground, in model y, is that much higher
			float ground = 24.0F - (sneaking && !Boolean.TRUE.equals(state.getData(XenoformClient.SHED)) ? CROUCH_SINK : 0.0F);
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			float amp = Mth.clamp(speed / STEP_FULL, 0.0F, 1.0F);
			float stretch = 1.0F + RUN_STRETCH * Mth.clamp((speed - STEP_FULL) / (1.0F - STEP_FULL), 0.0F, 1.0F);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float sw = Mth.sin(swing * (float)Math.PI);
			float rise = sw * q;
			float walk = state.walkAnimationPos;
			float phS = walk * STAND_GAIT;
			float phF = walk * FOURS_GAIT;
			float cycS = phS / TAU;
			float cycF = phF / TAU;

			// the hips: they dip with each step, squat on a landing, and rise and lunge into a pounce
			float bobS = STAND_BOB * amp * (0.5F - 0.5F * Mth.cos(2.0F * phS));
			float bobF = FOURS_BOB * amp * (0.5F - 0.5F * Mth.cos(4.0F * phF));
			float hipUp = Mth.lerp(q, STAND_HIP_UP - bobS - m.land() * STAND_DIP_LAND, FOURS_HIP_UP - bobF - m.land() * FOURS_DIP_LAND) + RISE_UP * rise;
			float hipY = ground - hipUp;
			float hipZ = Mth.lerp(q, 0.0F, FOURS_HIP_Z) - LUNGE * rise;
			// the torso leans forward (and further into a standing swing), rears into a pounce, pitches with a fall; on all fours it sways like a spine
			float pitch = Mth.lerp(q, STAND_PITCH + STAND_LUNGE * sw, FOURS_PITCH) - RISE_PITCH * rise + m.fall() * air * FALL_TILT;
			float yaw = Mth.lerp(q, twist, Mth.sin(phF - 0.6F) * SWAY * amp);
			this.torso.setPos(0.0F, hipY, hipZ);
			this.torso.xRot = pitch;
			this.torso.yRot = yaw;
			this.torso.yScale = 1.0F + BREATHE * m.breath() * (1.0F - amp);

			// the hidden body lies on the torso: its front on the chest standing, turned over onto the back on all fours (it turns
			// over at once halfway through the change, so the plate is never half inside the torso)
			this.at(hipY, hipZ, pitch, yaw, 0.0F, CHEST_Y, 0.0F);
			this.body.setPos(this.point[0], this.point[1], this.point[2]);
			this.body.xRot = pitch + (q > 0.5F ? (float)Math.PI : 0.0F);
			this.body.yRot = yaw;

			// the head: on the torso's end, forward of it, looking where you look (level on all fours, and never into the ground)
			this.at(hipY, hipZ, pitch, yaw, 0.0F, NECK_Y, 0.0F);
			float headX = this.point[0];
			float headY = this.point[1] + Mth.lerp(q, 0.0F, FOURS_HEAD_DROP);
			float headZ = this.point[2] - Mth.lerp(q, STAND_HEAD_FWD, FOURS_HEAD_FWD) - THRUST * rise;
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
			float lookFours = Mth.clamp(look * FOURS_LOOK, -0.5F, 0.4F);
			this.head.setPos(headX, headY, headZ);
			this.head.xRot = Mth.lerp(q, look + STAND_DIP, lookFours) + STRIKE_DIP * rise + m.land() * 0.15F;
			float gaze = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
			this.head.yRot = gaze - yaw * 0.5F * q;
			this.head.zRot = Mth.lerp(q, STAND_TILT, FOURS_TILT) + Mth.sin(age * 0.03F) * 0.06F - m.turn() * 0.06F;
			// the eyes blink together and swell and shrink a little as if they were looking at something
			float blink = Math.max(0.0F, 1.0F - Math.abs(age % 110.0F - 2.0F) * 0.5F);
			float gleam = 1.0F + 0.05F * Mth.sin(age * 0.11F);
			this.rightEye.yScale = this.leftEye.yScale = Mth.lerp(blink, gleam, 0.1F);
			this.rightEye.xScale = this.leftEye.xScale = gleam;
			// the neck is stretched from the torso's end to the head's pivot, so it never leaves either
			this.at(hipY, hipZ, pitch, yaw, 0.0F, SHOULDER_Y + 1.0F, 0.0F);
			float neckY = this.point[1];
			float neckZ = this.point[2];
			// (aimed two pixels up into the head, so it joins the head's middle rather than its underside)
			float dy = headY - 2.0F * Mth.cos(this.head.xRot) - neckY;
			float dz = headZ - 2.0F * Mth.sin(this.head.xRot) - neckZ;
			this.neck.setPos(this.point[0], neckY, neckZ);
			this.neck.xRot = (float)Math.atan2(-dz, -dy);
			this.neck.yScale = ((float)Math.sqrt(dy * dy + dz * dz) + 0.5F) / 3.0F;

			// the arms: standing, the vanilla pose (the walking swing mostly taken out, the forearm folding as the arm swings forward);
			// on all fours, forelegs solved to the ground, a pounce raising and clawing them; changing stance, the wrist goes from
			// where the standing arm holds it to its place on the ground and the arm is solved to reach it, kept high enough that
			// the hand (turning from hanging to flat) never goes into the ground
			float armWalk = Mth.cos(walk * 0.6662F) * speed * (1.0F - ARM_KEEP);
			rightX += armWalk;
			leftX -= armWalk;
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				float sign = left ? 1.0F : -1.0F;
				ModelPart arm = left ? this.leftArm : this.rightArm;
				ModelPart fore = left ? this.leftForearm : this.rightForearm;
				ModelPart hand = left ? this.leftHand : this.rightHand;
				this.at(hipY, hipZ, pitch, yaw, sign * SHOULDER_X, SHOULDER_Y, 0.0F);
				float shoulderX = this.point[0];
				float shoulderY = this.point[1];
				float shoulderZ = this.point[2];
				arm.setPos(shoulderX, shoulderY, shoulderZ);
				float standX = left ? leftX : rightX;
				float standFore = -(0.12F + 0.5F * Math.max(0.0F, -standX));
				float standHand = -0.2F * standX;
				// the forelegs
				float strike = sw * (left ? OFF_HAND : 1.0F);
				float stride = FORE_STRIDE * amp * stretch;
				float liftMax = FORE_LIFT * amp;
				this.path(frac(cycF + FOURS_PHASE[side * 2 + 1]), FOURS_DUTY, stride, liftMax);
				float lift = this.step[1];
				float wristZ = FORE_REST_Z + this.step[0] - STRIKE_REACH * strike + STRIKE_DRAG * (swing - 0.4F) * strike - AIR_FORE_FWD * air;
				float wristY = ground - ANKLE_UP - lift - STRIKE_UP * strike - AIR_FORE_UP * air;
				// the hand lies flat on the ground, its fingers forward; lifted, the fingers droop, and a pounce claws them down
				float flat = -HALF_PI + CLAW * strike + 0.5F * (liftMax > 0.01F ? lift / liftMax : 0.0F) * amp + 0.4F * air;
				arm.yRot = Mth.lerp(q, left ? leftY : rightY, 0.0F);
				arm.zRot = Mth.lerp(q, left ? leftZ : rightZ, 0.0F);
				if (q <= 0.0F) {
					arm.xRot = standX;
					fore.xRot = standFore;
					hand.xRot = standHand;
					continue;
				}
				float handAngle = Mth.lerp(q, standX + standFore + standHand, flat);
				float standWristZ = shoulderZ + UPPER_ARM * Mth.sin(standX) + FOREARM * Mth.sin(standX + standFore);
				float standWristY = shoulderY + UPPER_ARM * Mth.cos(standX) + FOREARM * Mth.cos(standX + standFore);
				// how far below the wrist the hand reaches at that angle (its length one way, a pixel the other, a pixel thick)
				float below = Math.max(HAND_LONG * Mth.cos(handAngle), -Mth.cos(handAngle)) + Math.abs(Mth.sin(handAngle));
				float toY = Math.min(Mth.lerp(q, standWristY, wristY), ground - below);
				this.solve(shoulderZ, shoulderY, Mth.lerp(q, standWristZ, wristZ), toY, UPPER_ARM, FOREARM, 1.0F);
				arm.xRot = this.angles[0];
				fore.xRot = this.angles[1] - this.angles[0];
				hand.xRot = handAngle - this.angles[1];
			}

			// the legs: the ankle goes where a foot would be (a stride standing, a lateral walk on all fours) and the thigh and shin are
			// solved to reach it with the knee forward, so the leg never breaks at a joint; the foot stays flat, toes down as it lifts
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				float sign = left ? 1.0F : -1.0F;
				ModelPart leg = left ? this.leftLeg : this.rightLeg;
				ModelPart shin = left ? this.leftShin : this.rightShin;
				ModelPart foot = left ? this.leftFoot : this.rightFoot;
				float liftMax = Mth.lerp(q, STAND_LIFT, HIND_LIFT) * amp;
				this.path(frac(cycS + side * 0.5F), STAND_DUTY, STAND_STRIDE * amp * stretch, STAND_LIFT * amp);
				float standZ = this.step[0];
				float standLift = this.step[1];
				this.path(frac(cycF + FOURS_PHASE[side * 2]), FOURS_DUTY, HIND_STRIDE * amp * stretch, HIND_LIFT * amp);
				float footZ = Mth.lerp(q, standZ, HIND_REST_Z + this.step[0]) + air * Mth.lerp(q, AIR_BACK_STAND, AIR_BACK_FOURS);
				float lift = Mth.lerp(q, standLift, this.step[1]);
				lift = Mth.lerp(air, lift, Mth.lerp(q, AIR_LIFT_STAND, AIR_LIFT_FOURS));
				this.solve(hipZ, hipY, footZ, ground - ANKLE_UP - lift, THIGH, SHIN, -1.0F);
				float thigh = this.angles[0];
				float shinAngle = this.angles[1];
				float tip = 0.35F * (liftMax > 0.01F ? Mth.clamp(lift / liftMax, 0.0F, 1.0F) : 0.0F) * amp + 0.3F * air;
				leg.setPos(sign * HIP_X, hipY, hipZ);
				leg.xRot = thigh;
				shin.xRot = shinAngle - thigh;
				foot.xRot = tip - shinAngle;
			}
		}
	}

	/**
	 * (0.18.103, patch 5; 0.19.62 a turtle on four flippers) Gyre: a broad domed shell over a pale plastron, four
	 * flippers that paddle in diagonal pairs and rock the shell as it waddles, a neck that pokes out and turns with the
	 * head and a short tail. Swimming it flaps its front flippers like wings. Rolled up (holding B) the head peeks from
	 * the shell and the limbs are drawn in; in Shell Spin the shell spins fast about its middle. In the turtle the man's
	 * body is hidden and laid face-up on the shell, so the insignia sits on its crown.
	 */
	static final class Gyre extends FormModel {
		// ---- tuning ----
		/**
		 * The shell's pivot height (model y), the ground, the foot's length below the flipper's pivot, and how high a
		 * flipper's foot comes up off the ground as it swings forward (pixels).
		 */
		static final float SHELL_Y = 17.0F, GROUND = 24.0F, FOOT = 5.0F, STEP_UP = 1.0F;
		/** How far the shell drops when crouched, rolled up and spinning (pixels); a landing squats it 1.5 more. */
		static final float CROUCH_DROP = 1.5F, ROLL_DROP = 3.0F, SPIN_DROP = 2.5F, LAND_SQUAT = 1.5F;
		/** The paddle: its rate against the walk, how far the front and hind flippers swing, how far they splay and lift. */
		static final float GAIT = 0.6662F * 0.85F, STRIDE_FRONT = 0.75F, STRIDE_HIND = 0.5F, SPLAY = 0.14F, LIFT = 0.45F;
		/** The waddle: the shell's roll and yaw against the walk. */
		static final float ROCK = 0.07F, SWAY = 0.05F;
		/** Swimming: the front flippers' stroke rate (radians a tick), and how far out the stroke sweeps. */
		static final float STROKE_RATE = 0.25F, STROKE_OUT = 0.8F, STROKE_SWEEP = 0.6F;
		/** The neck: its base (model y, z), its length, how far the head overlaps its tip, and how long it is at rest. */
		static final float NECK_Y = 16.5F, NECK_Z = -6.0F, NECK_LEN = 4.0F, NECK_OVERLAP = 1.5F, REST_EXT = 0.9F;
		/** The neck's reach: when sneaking it draws in, when biting it darts out, and in the water it stretches. */
		static final float SNEAK_PULL = 0.3F, BITE_REACH = 0.6F, SWIM_REACH = 0.35F;
		/** Shell Spin: how fast the shell turns (radians a tick). */
		static final float SPIN_RATE = 0.9F;

		private final ModelPart shell, shellTop, plastron, neck, tail, hindRight, hindLeft;

		Gyre() {
			super(bake(mesh()), "gyre");
			// the man's body lies face-up on the shell's crown (an 8x10x2 box): the plate sits on its top face
			this.insigniaY = 0.0F;
			this.insigniaZ = -1.0F;
			this.insigniaSize = 1.2F;
			ModelPart root = this.root();
			this.shell = root.getChild("shell");
			this.shellTop = root.getChild("shell_top");
			this.plastron = root.getChild("plastron");
			this.neck = root.getChild("neck");
			this.tail = root.getChild("tail");
			this.hindRight = root.getChild("hind_right_leg");
			this.hindLeft = root.getChild("hind_left_leg");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: a small turtle's, a beak under its snout; it hangs from the neck's tip (placed in setupAnim)
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -6.0F, 5.0F, 5.0F, 6.0F),
				PartPose.offset(0.0F, NECK_Y, NECK_Z - (NECK_LEN * REST_EXT - NECK_OVERLAP)));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(40, 112).addBox(-1.5F, 0.0F, -2.0F, 3.0F, 2.0F, 2.0F),
				PartPose.offset(0.0F, 0.3F, -6.0F));
			// the shell: a broad carapace, a smaller crown on it and the plastron under it, all hung from one pivot
			// so they roll and spin as one
			root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 64).addBox(-6.0F, -2.0F, -7.0F, 12.0F, 4.0F, 14.0F),
				PartPose.offset(0.0F, SHELL_Y, 0.0F));
			root.addOrReplaceChild("shell_top", CubeListBuilder.create().texOffs(0, 82).addBox(-4.0F, -4.0F, -5.0F, 8.0F, 2.0F, 10.0F),
				PartPose.offset(0.0F, SHELL_Y, 0.0F));
			root.addOrReplaceChild("plastron", CubeListBuilder.create().texOffs(0, 94).addBox(-5.0F, 2.0F, -6.0F, 10.0F, 2.0F, 12.0F),
				PartPose.offset(0.0F, SHELL_Y, 0.0F));
			root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 108).addBox(-1.5F, -1.5F, -NECK_LEN, 3.0F, 3.0F, NECK_LEN),
				PartPose.offset(0.0F, NECK_Y, NECK_Z));
			root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 108).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 1.0F, 3.0F),
				PartPose.offset(0.0F, 17.5F, 6.0F));
			// the front flippers are the man's legs (so the pose code still finds them), the hind pair new; each
			// reaches 1 pixel up into the shell and 5 down to the ground
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 6.0F, 4.0F),
				PartPose.offset(-5.5F, 19.0F, -4.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-1.5F, -1.0F, -2.0F, 3.0F, 6.0F, 4.0F),
				PartPose.offset(5.5F, 19.0F, -4.0F));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(16, 108).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(-5.0F, 19.0F, 4.5F));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(28, 108).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(5.0F, 19.0F, 4.5F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			String act = state.getData(XenoformClient.ACT);
			boolean spin = "spin".equals(act);
			boolean roll = "roll".equals(act);
			boolean tucked = spin || roll;
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.body.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft,
				this.shell, this.shellTop, this.plastron, this.neck, this.tail}) {
				part.resetPose();
			}
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			// swimming (or paddling through water): the level body stays, the flippers row
			float swim = Math.max(state.swimAmount, state.isInWater ? Math.min(1.0F, speed * 2.0F) : 0.0F);
			float age = state.ageInTicks;

			// ---- the shell: one rigid group, dropped when it sits down, rocking as it waddles ----
			float drop = state.isCrouching ? CROUCH_DROP : 0.0F;
			if (roll) {
				drop = ROLL_DROP;
			} else if (spin) {
				drop = SPIN_DROP;
			} else {
				drop += m.land() * LAND_SQUAT;
			}
			float rock;
			float yaw;
			if (spin) {
				rock = Mth.sin(age * 0.35F) * 0.1F;
				yaw = age * SPIN_RATE;
			} else if (roll) {
				rock = Mth.sin(age * 0.08F) * 0.02F;
				yaw = 0.0F;
			} else {
				rock = Mth.sin(walk) * ROCK * speed * (1.0F - swim);
				yaw = Mth.cos(walk) * SWAY * speed * (1.0F - swim);
			}
			for (ModelPart part : new ModelPart[] {this.shell, this.shellTop, this.plastron}) {
				part.y = SHELL_Y + drop;
				part.yRot = yaw;
				part.zRot = rock;
			}
			// the (hidden) body lies face-up at the crown's middle, 3 above the pivot, turning with the shell
			this.body.x = Mth.sin(rock) * 3.0F;
			this.body.y = SHELL_Y + drop - Mth.cos(rock) * 3.0F;
			this.body.z = 0.0F;
			this.body.xRot = -Mth.HALF_PI;
			this.body.yRot = yaw;
			this.body.zRot = rock;

			// ---- pulled in: the limbs are drawn into the shell, the head peeks out of it (not at all when spinning) ----
			boolean out = !tucked;
			this.neck.visible = this.tail.visible = out;
			this.rightLeg.visible = this.leftLeg.visible = this.hindRight.visible = this.hindLeft.visible = out;
			this.head.visible = !spin;
			if (tucked) {
				this.head.x = 0.0F;
				this.head.y = SHELL_Y + drop + 0.5F;
				this.head.z = 0.0F;
				return;
			}

			// ---- the flippers: diagonal pairs (front right with hind left), the feet kept on the ground ----
			float legY = SHELL_Y + 2.0F + drop;
			float stroke = Mth.sin(age * STROKE_RATE);
			float strokeX = Mth.cos(age * STROKE_RATE);
			float air = m.air();
			this.flipper(this.rightLeg, 1.0F, 0.0F, STRIDE_FRONT, legY, walk, speed, swim, stroke, strokeX, air, true);
			this.flipper(this.leftLeg, -1.0F, Mth.PI, STRIDE_FRONT, legY, walk, speed, swim, stroke, strokeX, air, true);
			this.flipper(this.hindRight, 1.0F, Mth.PI, STRIDE_HIND, legY, walk, speed, swim, stroke, strokeX, air, false);
			this.flipper(this.hindLeft, -1.0F, 0.0F, STRIDE_HIND, legY, walk, speed, swim, stroke, strokeX, air, false);

			// ---- the neck and head ----
			float yawH = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.4F, 1.4F);
			float pitchH = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * Mth.PI);
			float ext = REST_EXT + Mth.cos(walk * 2.0F) * 0.1F * speed + m.breath() * 0.03F + bite * BITE_REACH + swim * SWIM_REACH
				- (state.isCrouching ? SNEAK_PULL : 0.0F);
			this.neck.y = NECK_Y + drop;
			this.neck.z = NECK_Z;
			this.neck.zScale = ext;
			this.neck.yRot = yawH * 0.55F;
			this.neck.xRot = pitchH * 0.5F + bite * 0.1F;
			// the head sits at the neck's tip, a little inside it, and turns on past the neck
			float dist = NECK_LEN * ext - NECK_OVERLAP;
			float flat = Mth.cos(this.neck.xRot) * dist;
			this.head.x = -flat * Mth.sin(this.neck.yRot);
			this.head.y = this.neck.y + dist * Mth.sin(this.neck.xRot);
			this.head.z = NECK_Z - flat * Mth.cos(this.neck.yRot);
			this.head.yRot = yawH;
			this.head.xRot = pitchH + bite * 0.3F;
			this.head.zRot = -m.turn() * 0.12F;

			// ---- the tail: sways, trails a turn, lifts as it falls and drops on a landing or a sneak ----
			this.tail.y = 17.5F + drop;
			this.tail.yRot = Mth.sin(age * 0.12F) * 0.12F + Mth.sin(walk) * 0.3F * speed - m.trail() * 0.5F;
			this.tail.xRot = tailLift(m);
		}

		/**
		 * One flipper: walking it swings fore and aft (its pair half a cycle out) and lifts outward as it comes forward;
		 * swimming the front pair sweeps out and down together like wings and the hind pair trails and steers. Its length
		 * is set so the lowest corner of its foot (tilted by the swing and splay) is on the ground while it pushes back,
		 * STEP_UP above it at the top of its forward swing; in the water it keeps its own length.
		 */
		private void flipper(ModelPart leg, float side, float phase, float stride, float legY, float walk, float speed, float swim,
			float stroke, float strokeX, float air, boolean front) {
			float a = walk + phase;
			float walkX = Mth.cos(a) * stride * speed;
			float walkZ = side * (SPLAY + LIFT * Math.max(0.0F, Mth.sin(a)) * speed);
			float swimX;
			float swimZ;
			if (front) {
				swimX = -0.35F + 0.45F * strokeX;
				swimZ = side * (STROKE_OUT + STROKE_SWEEP * stroke);
			} else {
				swimX = 0.5F + 0.2F * Mth.sin(phase + stroke);
				swimZ = side * (0.5F + 0.15F * stroke);
			}
			leg.xRot = Mth.lerp(swim, walkX, swimX);
			leg.zRot = Mth.lerp(swim, walkZ, swimZ) + side * air * 0.5F;
			// the box reaches 1.5 to each side and half its depth fore and aft of the pivot, FOOT below it (times yScale)
			float halfDepth = front ? 2.0F : 1.5F;
			float cx = Mth.cos(leg.xRot);
			float cz = Mth.cos(leg.zRot);
			float down = GROUND - legY - STEP_UP * Math.max(0.0F, Mth.sin(a)) * speed;
			float pinned = ((down - 1.5F * Mth.abs(Mth.sin(leg.zRot))) / Math.max(0.2F, cz) - halfDepth * Mth.abs(Mth.sin(leg.xRot)))
				/ (FOOT * Math.max(0.2F, cx));
			leg.y = legY;
			leg.yScale = Mth.lerp(swim, Mth.clamp(pinned, 0.5F, 1.3F), 1.0F);
		}
	}

	/**
	 * (0.18.103, patch 5; 0.19.82 an eye fish) Cortex: plated, one big claw on the right hand and a small one on the left
	 * that open and shut, and an open skull with the brain showing, pulsing a little. Now an eye fish, after the guardian
	 * it was scanned from: a floating, boxy fish, not a man: a plated hull with one big eye on its front that looks where
	 * the head looks, a cracked skull-dome on top with the brain sparking in it (the insignia plate lies on the hull
	 * behind the dome), three spikes a side that slowly push out and draw in, and a tail of three shrinking segments with
	 * a fin that swings behind. Its hull's centre hangs at model y {@link #HOVER_Y} (its belly about 9 pixels off the
	 * ground), bobbing and drifting a little, leaning into a walk and banking into a turn. Swimming or in water it hangs
	 * level, nose pitched where it looks, the tail beating harder. An attack swing shoots the spikes out, flares the eye,
	 * lunges the hull and bursts the sparks. The man's parts are hidden; the {@code body} part is replaced by an empty rig
	 * at the hull's centre (laid face-up so the insignia sits on the hull's top) that also undoes vanilla's swimming tilt,
	 * so the fish stays level. Uses no {@code XenoformClient.ACT}: none of its abilities is a charge, roll or spin.
	 */
	static final class Cortex extends FormModel {
		// ---- tuning ----
		/** The hull's centre height (model y; ground is 24: the belly floats about 9 up), its bob and how fast (radians a tick), the centre's height swimming. */
		static final float HOVER_Y = 10.5F, BOB = 0.8F, BOB_RATE = 0.06F, WATER_Y = 16.5F;
		/** The lowest the hull's centre may sink on land (a landing, a sneak): the tail's tip would touch the ground below this. */
		static final float MAX_Y = 13.5F;
		/** Sneaking it hovers this much lower (pixels); the renderer already sinks a crouching player this much (AvatarRenderer.getRenderOffset), so it rises that back first. A landing dips it a little. */
		static final float SNEAK_DROP = 1.5F, CROUCH_SINK = 2.0F, LAND_DIP = 0.8F;
		/** How far it drifts about its spot (pixels). */
		static final float DRIFT_X = 0.8F, DRIFT_Z = 0.6F;
		/** Nose-down lean at a full walk, how far it banks into a turn, how far the nose follows the look when swimming (radians), how far an attack lunges the hull (pixels). */
		static final float PITCH = 0.12F, WADE_PITCH = 0.15F, BANK = 0.3F, LOOK_PITCH = 0.5F, LUNGE = 1.5F;
		/** The eye: how far it turns and nods against the hull (radians), how much bigger it flares on an attack (fraction). */
		static final float EYE_YAW = 0.4F, EYE_PITCH = 0.3F, EYE_FLARE = 0.3F;
		/** The spikes: length scale drawn in and pushed out, how fast they slowly slide (radians a tick), how much more a swing shoots them. */
		static final float SPIKE_MIN = 0.4F, SPIKE_MAX = 1.0F, SPIKE_RATE = 0.045F, SPIKE_SHOT = 0.5F;
		/** Where each spike's pivot sits on the hull's side: x (pixels out; 1 inside the wall), then per spike front to back its z, y, tilt up and sweep forward (radians). */
		static final float SPIKE_X = 4.0F;
		private static final float[] SPIKE_Z = {-3.5F, 0.5F, 4.5F};
		private static final float[] SPIKE_Y = {-0.5F, 0.8F, -0.3F};
		private static final float[] SPIKE_UP = {0.25F, -0.1F, 0.2F};
		private static final float[] SPIKE_YAW = {-0.35F, 0.0F, 0.35F};
		/** The tail: each segment's yaw amplitude and its limits (a joint overlaps its parent 1.5 pixels, so these keep the corners joined), the pitch limit, the lowest the whole tail may hang (radians), the wave's lag between joints. */
		private static final float[] TAIL_YAW = {0.12F, 0.2F, 0.3F};
		private static final float[] YAW_MAX = {0.2F, 0.3F, 0.45F};
		private static final float[] PITCH_MAX = {0.22F, 0.3F, 0.45F};
		static final float TAIL_DOWN = 0.3F, TAIL_LAG = 0.8F;
		/** How far forward of the hull's middle the dome (and the brain and sparks in it) sits: its back edge is half a pixel behind the middle, leaving the hull's top behind it for the insignia. */
		static final float DOME_Z = -2.5F;
		/** The sparks over the brain: how many, and each one's speed and phase. */
		private static final int SPARKS = 3;
		private static final float[] SPARK_RATE = {0.23F, 0.31F, 0.19F};
		private static final float[] SPARK_PHASE = {0.0F, 2.1F, 4.2F};

		private final ModelPart fish, eye, brain;
		private final ModelPart[] spikes = new ModelPart[6], tail = new ModelPart[3], sparks = new ModelPart[SPARKS];
		private final ModelPart fin;
		private final org.joml.Quaternionf quat = new org.joml.Quaternionf();
		private final org.joml.Vector3f euler = new org.joml.Vector3f();

		Cortex() {
			super(bake(mesh()), "cortex");
			this.fish = this.body.getChild("fish");
			this.eye = this.fish.getChild("eye");
			this.brain = this.fish.getChild("brain");
			for (int i = 0; i < 6; i++) {
				this.spikes[i] = this.fish.getChild("spike" + i);
			}
			ModelPart parent = this.fish;
			for (int i = 0; i < 3; i++) {
				this.tail[i] = parent.getChild("tail" + i);
				parent = this.tail[i];
			}
			this.fin = parent.getChild("fin");
			for (int i = 0; i < SPARKS; i++) {
				this.sparks[i] = this.fish.getChild("spark" + i);
			}
			// the plate lies on the hull's top (the body rig is laid face-up, so its -z is up): 4.5 up to the top, 3 behind the
			// middle (over the skin's insignia, rows 1-5 of the top face), clear of the dome that ends half a pixel behind the middle
			this.insigniaY = -3.0F;
			this.insigniaZ = -4.5F;
			this.insigniaSize = 0.8F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the rig: an empty body at the hull's centre; "fish" turns it back upright (the rig itself is laid face-up for the insignia)
			PartDefinition rig = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, HOVER_Y, 0.0F));
			// the hull: 10 wide, 9 tall, 13 long, centred on the pivot, the front at -z
			PartDefinition fish = rig.addOrReplaceChild("fish", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -4.5F, -6.5F, 10.0F, 9.0F, 13.0F),
				PartPose.rotation(Mth.HALF_PI, 0.0F, 0.0F));
			// the eye: 6x6x6, its back 3 inside the hull's front so a turn never opens a gap
			fish.addOrReplaceChild("eye", CubeListBuilder.create().texOffs(0, 86).addBox(-3.0F, -3.0F, -6.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 0.3F, -3.5F));
			// the cracked dome on the hull's top, front half, and the brain in it
			fish.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(24, 86).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 3.0F, 6.0F), PartPose.offset(0.0F, -4.0F, DOME_Z));
			fish.addOrReplaceChild("brain", CubeListBuilder.create().texOffs(48, 86).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.offset(0.0F, -6.5F, DOME_Z));
			for (int i = 0; i < SPARKS; i++) {
				fish.addOrReplaceChild("spark" + i, CubeListBuilder.create().texOffs(48, 92).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F),
					PartPose.offset(0.0F, -8.0F, DOME_Z));
			}
			// three spikes a side, each a 6x2x2 shaft with a 3x1x1 tip; the right ones reach toward -x, the left ones mirror
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				int row = i % 3;
				CubeListBuilder shaft = CubeListBuilder.create().texOffs(46, 64);
				CubeListBuilder tip = CubeListBuilder.create().texOffs(46, 68);
				if (left) {
					shaft.mirror().addBox(0.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F);
					tip.mirror().addBox(0.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F);
				} else {
					shaft.addBox(-6.0F, -1.0F, -1.0F, 6.0F, 2.0F, 2.0F);
					tip.addBox(-3.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F);
				}
				float s = left ? 1.0F : -1.0F;
				PartDefinition spike = fish.addOrReplaceChild("spike" + i, shaft, PartPose.offset(s * SPIKE_X, SPIKE_Y[row], SPIKE_Z[row]));
				spike.addOrReplaceChild("tip", tip, PartPose.offset(s * 5.5F, 0.0F, 0.0F));
			}
			// the tail: three shrinking segments, each hung 1.5 pixels inside the end of the one before, then the fin
			PartDefinition t0 = fish.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(0, 98).addBox(-3.5F, -3.0F, 0.0F, 7.0F, 6.0F, 5.0F),
				PartPose.offset(0.0F, 0.2F, 5.0F));
			PartDefinition t1 = t0.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(24, 98).addBox(-2.5F, -2.0F, 0.0F, 5.0F, 4.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, 3.5F));
			PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(44, 98).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, 3.5F));
			t2.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(46, 70).addBox(-0.5F, -4.0F, 0.0F, 1.0F, 8.0F, 4.0F), PartPose.offset(0.0F, 0.0F, 3.5F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			// swimming (the swim pose) it floats level in the water's middle; wading or on land it hovers off the ground; wet, its tail beats harder
			float sw = Mth.clamp(state.swimAmount, 0.0F, 1.0F);
			float w = state.isInWater ? 1.0F : sw;
			float burst = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);

			// ---- where the hull's centre is: hovering, bobbing, dipping on a landing; never low enough for the tail to touch down ----
			float sneak = state.isCrouching && sw < 0.5F ? SNEAK_DROP - CROUCH_SINK : 0.0F;
			float bob = Mth.sin(age * BOB_RATE) * BOB + Mth.sin(age * 0.37F) * 0.25F * speed;
			float hover = Math.min(MAX_Y, HOVER_Y + sneak + Math.max(0.0F, m.land()) * LAND_DIP + bob);
			float cy = Mth.lerp(sw, hover, WATER_Y + bob);
			float dx = Mth.sin(age * 0.021F) * DRIFT_X;
			float dz = Mth.cos(age * 0.017F) * DRIFT_Z - burst * LUNGE;

			// ---- how it leans: nose down into a walk (never nose up on land: the tail would dig in), pitched where it looks swimming ----
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.6F, -LOOK_PITCH, LOOK_PITCH);
			float idle = Mth.sin(age * 0.031F) * 0.03F;
			float landPitch = Math.max(-0.03F, speed * PITCH + idle);
			float waterPitch = look * (0.5F + 0.5F * speed) + idle;
			float wetPitch = Mth.lerp(w, landPitch, waterPitch);
			// wading, the nose never tips up (the tail would dig into the bed) nor far down (the eye would)
			float pitch = Mth.lerp(sw, Mth.clamp(wetPitch, -0.03F, WADE_PITCH), wetPitch);
			float bank = Mth.clamp(m.turn() * BANK, -BANK, BANK) + Mth.sin(age * 0.04F) * 0.04F;
			float sway = Mth.sin(age * 0.023F) * 0.06F;

			// ---- undo vanilla's swimming tilt (AvatarRenderer.setupRotations: an XP turn of swimAmount * (-90 - xRot) about the feet, then 0.3 forward and 1 block down when visually swimming) ----
			float theta = 0.0F;
			float shiftY = 0.0F;
			float shiftZ = 0.0F;
			if (state.swimAmount > 0.0F) {
				float target = state.isInWater ? -90.0F - state.xRot : -90.0F;
				theta = state.swimAmount * target * Mth.DEG_TO_RAD;
				if (state.isVisuallySwimming) {
					// the model is drawn y-down, so vanilla's (0, -1, 0.3) blocks undone is (0, -16, -4.8) pixels
					shiftY = -16.0F;
					shiftZ = -4.8F;
				}
			}
			// the hull's centre, turned about the feet (model y 24) by the same angle that undoes the tilt
			float relY = cy - 24.0F;
			this.body.x = dx;
			this.body.y = 24.0F + relY * Mth.cos(theta) - dz * Mth.sin(theta) + shiftY;
			this.body.z = relY * Mth.sin(theta) + dz * Mth.cos(theta) + shiftZ;
			// the rig's turn: the swim undo, then the bank and sway and pitch, then laid face-up (the fish part stands it back up)
			this.quat.identity().rotateX(theta).rotateZ(bank).rotateY(sway).rotateX(pitch).rotateX(-Mth.HALF_PI);
			this.quat.getEulerAnglesZYX(this.euler);
			this.body.xRot = this.euler.x;
			this.body.yRot = this.euler.y;
			this.body.zRot = this.euler.z;

			// ---- the eye looks where the head looks, held against the hull's own lean; an attack flares it ----
			this.eye.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD + Mth.sin(age * 0.09F) * 0.05F, -EYE_YAW, EYE_YAW);
			this.eye.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.6F - pitch * 0.5F, -EYE_PITCH, EYE_PITCH);
			float flare = 1.0F + burst * EYE_FLARE + m.breath() * 0.02F;
			this.eye.xScale = this.eye.yScale = this.eye.zScale = flare;

			// ---- the dome's brain pulses, and sparks hop over it ----
			float pulse = Mth.sin(age * 0.2F);
			this.brain.yScale = 1.0F + pulse * 0.12F + burst * 0.3F;
			this.brain.xScale = this.brain.zScale = 1.0F + pulse * 0.05F + burst * 0.15F;
			for (int i = 0; i < SPARKS; i++) {
				float t = age * SPARK_RATE[i] + SPARK_PHASE[i];
				float reach = 2.2F + burst * 2.0F;
				ModelPart spark = this.sparks[i];
				spark.x = Mth.cos(t) * reach;
				spark.z = DOME_Z + Mth.sin(t) * reach;
				spark.y = -8.6F - Mth.abs(Mth.sin(t * 1.7F)) * (1.2F + burst * 1.5F);
				// each spark flickers: only on for a short while of its beat, bigger on a swing
				float on = Math.max(0.0F, Mth.sin(age * 0.41F + SPARK_PHASE[i] * 1.3F) - 0.3F) * 1.4F;
				float size = on * (1.0F + burst * 0.8F);
				spark.xScale = spark.yScale = spark.zScale = size;
			}

			// ---- the spikes slowly slide out and in; a swing shoots them out; they sweep back with speed ----
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				int row = i % 3;
				float slide = 0.5F + 0.5F * Mth.sin(age * SPIKE_RATE + row * 1.7F + (left ? 0.9F : 0.0F));
				float length = Mth.lerp(slide, SPIKE_MIN, SPIKE_MAX) + burst * SPIKE_SHOT;
				ModelPart spike = this.spikes[i];
				spike.xScale = length;
				float up = SPIKE_UP[row] + burst * 0.1F + Mth.sin(age * 0.05F + i) * 0.05F;
				float yaw = SPIKE_YAW[row] + speed * 0.25F;
				// the left spikes mirror the right ones
				spike.zRot = left ? -up : up;
				spike.yRot = left ? -yaw : yaw;
			}

			// ---- the tail: a wave down its three segments, stronger in water and with the walk; it lifts as it falls and dips on a landing, but never so low it touches down ----
			float phi = state.walkAnimationPos * 0.8F;
			float strength = 1.0F + 0.5F * w;
			float lift = Mth.clamp(tailLift(m), -0.25F, 0.3F) * 0.45F;
			float cum = 0.0F;
			for (int j = 0; j < 3; j++) {
				float yaw = TAIL_YAW[j] * strength * (speed * Mth.sin(phi - j * TAIL_LAG) + (1.0F - speed) * 0.5F * Mth.sin(age * 0.07F - j * 0.9F)) - m.trail() * 0.1F * (j + 1);
				float want = lift + Mth.sin(age * 0.05F + 1.0F - j * 0.7F) * 0.04F;
				float limited = Mth.clamp(want, -PITCH_MAX[j], PITCH_MAX[j]);
				// the whole tail may hang only TAIL_DOWN below level (on land)
				float total = Mth.clamp(cum + limited, sw > 0.5F ? -PITCH_MAX[j] * 2.0F : -TAIL_DOWN, 0.6F);
				this.tail[j].xRot = total - cum;
				cum = total;
				this.tail[j].yRot = Mth.clamp(yaw, -YAW_MAX[j], YAW_MAX[j]);
			}
			this.fin.yRot = Mth.sin(age * 0.12F - 3.0F) * (0.1F + 0.1F * speed);
		}
	}

	/**
	 * (0.18.103, patch 5; 0.19.66 a grasshopper) Locust: lean, wing cases folded down its back, long feelers that twitch,
	 * spurs on its legs. Now a big grasshopper, not a man: a long level body (head, thorax, two abdomen segments), folded
	 * wings on its back, feelers that sway and stream back in the air, two short pairs of walking legs that step in an
	 * insect's tripod gait (front-left, middle-right and hind-left together, then the other three) and two huge folded
	 * hind legs (thigh up and back, shin folded forward to the ground). Every leg is solved by two-bone IK from its hip
	 * to a foot target, so it stays joined and its foot stays on the ground while it walks. Crouching (the Crash Leap
	 * charge) lowers the body and folds the hind legs tighter; rising in a leap (or the attack swing, one hind leg) kicks
	 * them straight back, and the wings open and flutter in the air. The humanoid parts stay, hidden: the body carries the
	 * insignia on the thorax's back.
	 */
	static final class Locust extends FormModel {
		// ---- tuning ----
		/** The thorax's height above the ground (model y), how far crouching and a landing sink it, how much a step bobs it, how much it trembles when crouched. */
		static final float BODY_Y = 15.0F, CROUCH_DROP = 2.6F, LAND_DIP = 1.8F, BOB = 0.35F, TREMBLE = 0.12F;
		/** The renderer draws a sneaking player this many pixels lower (AvatarRenderer.getRenderOffset): crouched, the feet are planted that much higher to stay on the ground. */
		static final float CROUCH_SINK = 2.0F;
		/** How fast the legs step against the walk, and how far (pixels) the walking legs and the hind legs stride and lift a foot. */
		static final float GAIT = 0.6662F * 2.2F, STRIDE = 3.2F, LIFT = 2.6F, HIND_STRIDE = 2.0F, HIND_LIFT = 1.6F;
		/** In the air: how high the walking feet are drawn up (resting, and extra while rising), and how far the front feet reach forward when falling. */
		static final float TUCK = 2.5F, TUCK_RISE = 3.5F, REACH = 3.0F;
		/** The walking feet spread this much wider crouched and in the air, and are never drawn up closer than this under their hip (a short leg's knee would fold into the body). */
		static final float CROUCH_SPREAD = 2.0F, AIR_SPREAD = 2.0F, FOOT_BELOW = 4.0F;
		/** The hind feet stand this far above the ground line, so the slanted shin's lower edge rests on it instead of sinking in. */
		static final float HIND_FOOT_LIFT = 0.6F;
		/** A hind leg's kick: how far out it already is in the air before the rise, and the extra pull of the attack swing on the left leg (the right goes fully). */
		static final float KICK_AIR = 0.4F, KICK_SWING_LEFT = 0.35F;
		/** Body lean (radians): nose up rising, nose down falling, a kick's lunge, a step's roll. */
		static final float RISE_PITCH = 0.3F, FALL_PITCH = 0.2F, KICK_PITCH = 0.1F, ROLL = 0.03F;
		/** The bones (pixels): the walking legs' femur and tibia, the hind legs' thigh and shin, a feeler's two halves. */
		private static final float FEMUR = 5.0F, TIBIA = 9.0F, THIGH = 10.0F, SHIN = 14.0F, FEELER = 7.0F, FEELER_TIP = 6.0F;
		/** Where the legs join the thorax (x out from the middle, y down, z back) and where their feet rest: front, middle, hind. */
		private static final float[] HIP_X = {3.0F, 3.0F, 3.4F}, HIP_Y = {1.5F, 1.5F, 0.4F}, HIP_Z = {-2.8F, 0.6F, 3.2F};
		private static final float[] REST_X = {7.0F, 7.6F, 5.6F}, REST_Z = {-7.5F, 1.6F, 6.0F};
		/** The hind feet when crouched: closer in and forward, so the knees fold up. */
		private static final float CROUCH_HIND_X = 5.0F, CROUCH_HIND_Z = 4.0F;
		/** A fully kicked hind foot, from its hip (x out, y down, z back): nearly the legs' whole length straight back. */
		private static final float KICK_X = 1.2F, KICK_Y = 5.0F, KICK_Z = 21.5F;
		/** Off the ground a kicked hind foot may reach this far below the ground line (so a nose-up push-off still kicks straight). */
		private static final float KICK_BELOW = 6.0F;

		private final ModelPart thorax, abdomen, tail;
		/** Right then left. */
		private final ModelPart[] wing = new ModelPart[2], wingTip = new ModelPart[2], feeler = new ModelPart[2], feelerTip = new ModelPart[2];
		/** Front right, front left, middle right, middle left. */
		private final ModelPart[] femur = new ModelPart[4], tibia = new ModelPart[4];
		private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2];
		/** Scratch: the solved knee and foot, a rotated point, and the body's lean for this frame. */
		private final float[] knee = new float[3], foot = new float[3], pt = new float[3];
		private float sinP, cosP, sinR, cosR;

		Locust() {
			super(bake(mesh()), "locust");
			ModelPart root = this.root();
			this.thorax = root.getChild("thorax");
			this.abdomen = this.thorax.getChild("abdomen");
			this.tail = this.abdomen.getChild("tail");
			for (int i = 0; i < 2; i++) {
				String side = i == 0 ? "right" : "left";
				this.wing[i] = this.thorax.getChild(side + "_wing");
				this.wingTip[i] = this.wing[i].getChild("tip");
				this.feeler[i] = this.head.getChild(side + "_feeler");
				this.feelerTip[i] = this.feeler[i].getChild("tip");
				this.thigh[i] = root.getChild(side + "_thigh");
				this.shin[i] = root.getChild(side + "_shin");
			}
			for (int i = 0; i < 4; i++) {
				String side = i % 2 == 0 ? "right" : "left";
				String pair = i < 2 ? "front" : "mid";
				this.femur[i] = root.getChild(pair + "_" + side + "_femur");
				this.tibia[i] = root.getChild(pair + "_" + side + "_tibia");
			}
			// the plate on the thorax's back: the (hidden) body lies face-up there, a little forward of the wings
			this.insigniaY = 0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.5F, -6.0F, 6.0F, 7.0F, 6.0F),
				PartPose.offset(0.0F, BODY_Y + 0.5F, -3.6F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				String side = left ? "left" : "right";
				float sx = left ? 1.0F : -1.0F;
				CubeListBuilder base = CubeListBuilder.create().texOffs(52, 64);
				CubeListBuilder tipBox = CubeListBuilder.create().texOffs(56, 64);
				if (left) {
					base.mirror();
					tipBox.mirror();
				}
				PartDefinition feeler = head.addOrReplaceChild(side + "_feeler", base.addBox(-0.5F, 0.0F, -0.5F, 1.0F, FEELER, 1.0F),
					PartPose.offsetAndRotation(sx * 1.5F, -3.4F, -5.2F, -0.9F, 0.0F, sx * -0.3F));
				feeler.addOrReplaceChild("tip", tipBox.addBox(-0.5F, 0.0F, -0.5F, 1.0F, FEELER_TIP, 1.0F), PartPose.offsetAndRotation(0.0F, FEELER, 0.0F, 0.35F, 0.0F, 0.0F));
			}
			// the body: thorax (the pronotum on top), two abdomen segments behind it
			PartDefinition thorax = root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -2.5F, -4.0F, 6.0F, 5.0F, 8.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
			PartDefinition abdomen = thorax.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(28, 64).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 7.0F),
				PartPose.offset(0.0F, 0.0F, 3.8F));
			abdomen.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 78).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 6.0F), PartPose.offset(0.0F, 0.0F, 6.8F));
			// folded wings, a roof over the back, each with a tip that flutters
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				String side = left ? "left" : "right";
				CubeListBuilder wing = CubeListBuilder.create().texOffs(20, 78);
				CubeListBuilder tipBox = CubeListBuilder.create().texOffs(44, 78);
				if (left) {
					wing.mirror().addBox(0.0F, -1.0F, 0.0F, 3.0F, 1.0F, 9.0F);
					tipBox.mirror().addBox(0.0F, -1.0F, 0.0F, 3.0F, 1.0F, 6.0F);
				} else {
					wing.addBox(-3.0F, -1.0F, 0.0F, 3.0F, 1.0F, 9.0F);
					tipBox.addBox(-3.0F, -1.0F, 0.0F, 3.0F, 1.0F, 6.0F);
				}
				PartDefinition w = thorax.addOrReplaceChild(side + "_wing", wing, PartPose.offsetAndRotation(left ? 0.1F : -0.1F, -2.5F, 2.5F, -0.08F, 0.0F, left ? 0.3F : -0.3F));
				w.addOrReplaceChild("tip", tipBox, PartPose.offsetAndRotation(0.0F, 0.0F, 8.8F, -0.06F, 0.0F, 0.0F));
			}
			// legs: the walking pairs (femur and tibia, each a root part placed by the animation) and the hind pair
			for (int i = 0; i < 4; i++) {
				boolean left = i % 2 == 1;
				String name = (i < 2 ? "front_" : "mid_") + (left ? "left" : "right");
				CubeListBuilder femur = CubeListBuilder.create().texOffs(20, 90);
				CubeListBuilder tibia = CubeListBuilder.create().texOffs(28, 90);
				if (left) {
					femur.mirror();
					tibia.mirror();
				}
				root.addOrReplaceChild(name + "_femur", femur.addBox(-1.0F, 0.0F, -1.0F, 2.0F, FEMUR, 2.0F), PartPose.ZERO);
				root.addOrReplaceChild(name + "_tibia", tibia.addBox(-0.5F, 0.0F, -0.5F, 1.0F, TIBIA, 1.0F), PartPose.ZERO);
			}
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				String side = left ? "left" : "right";
				CubeListBuilder thigh = CubeListBuilder.create().texOffs(0, 90);
				CubeListBuilder shin = CubeListBuilder.create().texOffs(12, 90);
				if (left) {
					thigh.mirror();
					shin.mirror();
				}
				root.addOrReplaceChild(side + "_thigh", thigh.addBox(-1.5F, 0.0F, -1.5F, 3.0F, THIGH, 3.0F), PartPose.ZERO);
				root.addOrReplaceChild(side + "_shin", shin.addBox(-1.0F, 0.0F, -1.0F, 2.0F, SHIN, 2.0F), PartPose.ZERO);
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.visible = false;
			this.head.resetPose();
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			boolean crouch = m.crouching();
			float air = m.air();
			float ground = 1.0F - air;
			// rising is negative fall: a leap's push-off
			float rise = Mth.clamp(-m.fall() * 2.5F, 0.0F, 1.0F) * air;
			float drop = Mth.clamp(m.fall() * 2.0F, 0.0F, 1.0F) * air;
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float snap = Mth.sin(swing * (float)Math.PI);
			// the ground line in model space: crouched, the renderer draws the whole model CROUCH_SINK lower
			float floor = 24.0F - (crouch ? CROUCH_SINK : 0.0F);
			float by = BODY_Y + (crouch ? CROUCH_DROP - CROUCH_SINK : 0.0F) + m.land() * LAND_DIP - Mth.cos(walk * 2.0F) * BOB * speed * ground
				+ (crouch ? Mth.sin(age * 2.3F) * TREMBLE : 0.0F) + m.breath() * 0.1F;
			float pitch = -RISE_PITCH * rise + FALL_PITCH * drop + snap * KICK_PITCH + Mth.sin(walk * 2.0F) * 0.02F * speed * ground;
			float roll = Mth.sin(walk) * ROLL * speed * ground - m.turn() * 0.08F;
			this.sinP = Mth.sin(pitch);
			this.cosP = Mth.cos(pitch);
			this.sinR = Mth.sin(roll);
			this.cosR = Mth.cos(roll);
			// the body: thorax (and with it the wings and abdomen), the hidden body for the plate, the head at the front
			this.thorax.x = 0.0F;
			this.thorax.y = by;
			this.thorax.z = 0.0F;
			this.thorax.xRot = pitch;
			this.thorax.zRot = roll;
			this.body.x = 0.0F;
			this.body.y = by;
			this.body.z = 0.0F;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			this.body.zRot = roll;
			this.rot(0.0F, 0.5F, -3.6F);
			this.head.x = this.pt[0];
			this.head.y = by + this.pt[1];
			this.head.z = this.pt[2];
			float nod = Mth.sin(walk * 2.0F - 0.8F) * 0.04F * speed * ground;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.0F, 1.0F);
			this.head.xRot = pitch + Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.7F, 0.9F) * 0.8F + nod + snap * 0.15F;
			this.head.zRot = roll;
			// the abdomen sways with the turn and the stride, lifts as it falls; the tail trails a beat after it
			float lift = tailLift(m);
			this.abdomen.xRot = 0.04F + lift * 0.35F + m.breath() * 0.015F;
			this.abdomen.yRot = m.trail() * 0.2F + Mth.sin(walk) * 0.06F * speed * ground;
			this.tail.xRot = 0.02F + lift * 0.3F;
			this.tail.yRot = m.trail() * 0.25F + Mth.sin(walk - 0.8F) * 0.05F * speed * ground;
			float breathe = 1.0F + m.breath() * 0.025F;
			this.abdomen.xScale = this.abdomen.yScale = breathe;
			this.wings(age, air, rise, speed, m);
			this.feelers(age, air, rise, m);
			// the walking legs: tripod gait, the front pair also fidgeting at rest
			for (int i = 0; i < 4; i++) {
				int pair = i / 2;
				int s = i % 2;
				float sx = s == 0 ? -1.0F : 1.0F;
				this.hip(pair, sx, by);
				float hx = this.pt[0], hy = this.pt[1], hz = this.pt[2];
				float cycle = frac(walk / ((float)Math.PI * 2.0F) + ((pair + s) % 2) * 0.5F);
				float dz;
				float up = 0.0F;
				if (cycle < 0.5F) {
					dz = -STRIDE + 2.0F * STRIDE * (cycle * 2.0F);
				} else {
					float u = (cycle - 0.5F) * 2.0F;
					dz = STRIDE - 2.0F * STRIDE * u;
					up = Mth.sin(u * (float)Math.PI) * LIFT;
				}
				float idle = (1.0F - speed) * ground;
				float fidgetZ = pair == 0 ? Mth.sin(age * 0.09F + s * 1.7F) * 0.5F * idle : 0.0F;
				float fidgetUp = pair == 0 ? Math.max(0.0F, Mth.sin(age * 0.045F + s * 3.1F)) * 2.0F * idle : 0.0F;
				float tx = sx * (REST_X[pair] + (crouch ? CROUCH_SPREAD : 0.0F) + air * AIR_SPREAD);
				float tz = REST_Z[pair] + dz * speed * ground + fidgetZ - drop * (pair == 0 ? REACH : REACH * 0.4F);
				float tuck = air * (TUCK + TUCK_RISE * rise - TUCK * 0.6F * drop);
				float ty = Math.max(floor - up * speed * ground - fidgetUp - tuck, Math.min(floor, hy + FOOT_BELOW));
				this.solve(hx, hy, hz, tx, Math.min(floor, ty), tz, FEMUR, TIBIA, sx * 0.8F, -1.0F, 0.0F);
				aim(this.femur[i], hx, hy, hz, this.knee[0], this.knee[1], this.knee[2]);
				aim(this.tibia[i], this.knee[0], this.knee[1], this.knee[2], this.foot[0], this.foot[1], this.foot[2]);
			}
			// the hind legs: folded (thigh up and back, shin forward to the ground), kicked straight back in a leap or a swing
			for (int s = 0; s < 2; s++) {
				float sx = s == 0 ? -1.0F : 1.0F;
				this.hip(2, sx, by);
				float hx = this.pt[0], hy = this.pt[1], hz = this.pt[2];
				float cycle = frac(walk / ((float)Math.PI * 2.0F) + s * 0.5F);
				float dz;
				float up = 0.0F;
				if (cycle < 0.5F) {
					dz = -HIND_STRIDE + 2.0F * HIND_STRIDE * (cycle * 2.0F);
				} else {
					float u = (cycle - 0.5F) * 2.0F;
					dz = HIND_STRIDE - 2.0F * HIND_STRIDE * u;
					up = Mth.sin(u * (float)Math.PI) * HIND_LIFT;
				}
				float tx = sx * (crouch ? CROUCH_HIND_X : REST_X[2]);
				float tz = (crouch ? CROUCH_HIND_Z : REST_Z[2]) + dz * speed * ground;
				float ty = floor - HIND_FOOT_LIFT - up * speed * ground;
				// the kick: out in the air (fuller the harder it rises), and one leg snapping back on the swing
				float kick = air * Mth.clamp(KICK_AIR + rise * (1.0F - KICK_AIR) - drop * 0.15F, 0.0F, 1.0F);
				kick = Math.max(kick, s == 0 ? snap : snap * KICK_SWING_LEFT);
				this.rot(sx * KICK_X, KICK_Y, KICK_Z);
				float kx = hx + this.pt[0], ky = hy + this.pt[1], kz = hz + this.pt[2];
				this.solve(hx, hy, hz, Mth.lerp(kick, tx, kx), Math.min(floor + air * KICK_BELOW, Mth.lerp(kick, ty, ky)), Mth.lerp(kick, tz, kz), THIGH, SHIN, sx * 0.15F, -0.9F, 0.9F);
				aim(this.thigh[s], hx, hy, hz, this.knee[0], this.knee[1], this.knee[2]);
				aim(this.shin[s], this.knee[0], this.knee[1], this.knee[2], this.foot[0], this.foot[1], this.foot[2]);
			}
		}

		/** The wing cases: a roof over the back, opening and fluttering in the air, their tips trembling a little always. */
		private void wings(float age, float air, float rise, float speed, XenoMotion.Read m) {
			float open = air * (0.3F + 0.4F * rise) + m.land() * 0.3F;
			for (int i = 0; i < 2; i++) {
				float sx = i == 0 ? -1.0F : 1.0F;
				float flutter = Mth.sin(age * 1.9F + i * 0.7F) * (0.03F + air * 0.14F);
				ModelPart w = this.wing[i];
				w.zRot = sx * (0.3F - open * 0.9F + flutter);
				w.yRot = sx * open * 0.45F;
				w.xRot = -0.08F + Mth.sin(age * 1.9F + 1.3F) * air * 0.06F;
				this.wingTip[i].xRot = -0.06F + Mth.sin(age * 2.3F + i * 0.6F) * (0.02F + air * 0.16F + speed * 0.03F);
				this.wingTip[i].zRot = sx * Mth.sin(age * 2.1F) * air * 0.08F;
			}
		}

		/** The feelers: swaying on their own, lagging a turn, streaming back in the air; the tips curl and trail behind the bases. */
		private void feelers(float age, float air, float rise, XenoMotion.Read m) {
			for (int i = 0; i < 2; i++) {
				float sx = i == 0 ? -1.0F : 1.0F;
				float t = age * 0.12F + i * 1.9F;
				float sway = Mth.sin(t) * 0.12F + Mth.sin(t * 2.3F) * 0.04F - m.trail() * 0.3F;
				// where the feeler points (head space, x out, y down, z back), then aimed from its socket
				float dx = sx * 0.28F + sway;
				float dy = -0.62F + Mth.sin(t * 0.7F) * 0.05F;
				float dz = -0.78F + air * (0.7F + 0.4F * rise);
				float ox = sx * 1.5F, oy = -3.4F, oz = -5.2F;
				aim(this.feeler[i], ox, oy, oz, ox + dx, oy + dy, oz + dz);
				this.feelerTip[i].xRot = 0.35F + air * 0.5F + Mth.sin(t - 0.9F) * 0.15F;
				this.feelerTip[i].zRot = Mth.sin(t - 1.0F) * 0.2F;
			}
		}

		/** Sets {@code pt} to a body-space offset turned by the body's lean (pitch, then roll). */
		private void rot(float x, float y, float z) {
			float y1 = y * this.cosP - z * this.sinP;
			float z1 = y * this.sinP + z * this.cosP;
			this.pt[0] = x * this.cosR - y1 * this.sinR;
			this.pt[1] = x * this.sinR + y1 * this.cosR;
			this.pt[2] = z1;
		}

		/** Sets {@code pt} to where a hip (0 front, 1 middle, 2 hind; sx -1 right, +1 left) is, in model space, for a thorax at height {@code by}. */
		private void hip(int pair, float sx, float by) {
			this.rot(sx * HIP_X[pair], HIP_Y[pair], HIP_Z[pair]);
			this.pt[1] += by;
		}

		/**
		 * Two-bone IK: the knee of a leg of bones {@code l1}, {@code l2} from the hip (h) toward the foot target (f), bending
		 * toward {@code pole}. Writes {@code knee} and {@code foot} (where the foot really is: the target, held to what the
		 * bones can reach, so the leg is never torn off its hip nor bent past straight).
		 */
		private void solve(float hx, float hy, float hz, float fx, float fy, float fz, float l1, float l2, float px, float py, float pz) {
			float dx = fx - hx, dy = fy - hy, dz = fz - hz;
			float d = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < 1.0E-4F) {
				dx = 0.0F;
				dy = 1.0F;
				dz = 0.0F;
				d = 1.0F;
			}
			float ux = dx / d, uy = dy / d, uz = dz / d;
			float reach = Mth.clamp(d, Math.abs(l1 - l2) + 0.1F, l1 + l2 - 0.05F);
			// the pole with its part along the leg taken out, to say which way the knee goes
			float dot = px * ux + py * uy + pz * uz;
			float qx = px - dot * ux, qy = py - dot * uy, qz = pz - dot * uz;
			float ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
			if (ql < 1.0E-3F) {
				qx = 0.0F;
				qy = -1.0F;
				qz = 0.0F;
				ql = 1.0F;
			}
			qx /= ql;
			qy /= ql;
			qz /= ql;
			float a = (l1 * l1 - l2 * l2 + reach * reach) / (2.0F * reach);
			float h = (float)Math.sqrt(Math.max(0.0F, l1 * l1 - a * a));
			this.knee[0] = hx + ux * a + qx * h;
			this.knee[1] = hy + uy * a + qy * h;
			this.knee[2] = hz + uz * a + qz * h;
			this.foot[0] = hx + ux * reach;
			this.foot[1] = hy + uy * reach;
			this.foot[2] = hz + uz * reach;
		}

		/** Puts a box that hangs along +y from its pivot at (sx, sy, sz) pointing at (ex, ey, ez) (x, then z; the ZYX order turns the leg as it needs). */
		private static void aim(ModelPart part, float sx, float sy, float sz, float ex, float ey, float ez) {
			float dx = ex - sx, dy = ey - sy, dz = ez - sz;
			float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
			part.x = sx;
			part.y = sy;
			part.z = sz;
			part.yRot = 0.0F;
			if (len < 1.0E-4F) {
				part.xRot = part.zRot = 0.0F;
				return;
			}
			dx /= len;
			dy /= len;
			dz /= len;
			part.xRot = (float)Math.asin(Mth.clamp(dz, -1.0F, 1.0F));
			part.zRot = (float)Math.atan2(-dx, dy);
		}

		private static float frac(float v) {
			return v - (float)Math.floor(v);
		}
	}

	/**
	 * (0.18.103, patch 5; 0.19.66 a hopping frog) Croaker: a squat, low FROG (about nine pixels tall), no longer a man in
	 * green: a wide flat torso sitting with its nose tipped up, a broad head with a wide mouth and two big eyes on top, a
	 * throat sac under the jaw, short front legs planted under its chest and big hind legs folded Z-wise along its flanks.
	 * It hops instead of walking: the walk's clock drives a hop (coil, push, flight, landing) in which the hind legs are
	 * solved by two-bone IK so the foot stays flat on the ground until the leg is straight, the hips rising as it
	 * unfolds, then the legs trail behind in the air and tuck for the landing. At rest the throat sac swells and sinks
	 * slowly and the eyes blink; on an attack swing (the tongue, the croak) the mouth opens and the sac balloons. The
	 * hidden humanoid body is only the anchor of the insignia, laid face-up on the frog's back.
	 */
	static final class Croaker extends FormModel {
		// ---- tuning ----
		/** Where the torso's centre sits at rest (model y, z) and how far its nose is tipped up (radians, - is up). */
		static final float TORSO_Y = 19.9F, TORSO_Z = 2.0F, TILT = -0.2F;
		/** How far it sinks when coiled (pixels), how far the nose lifts as the legs push, the extra tip when coiled and the tip when it falls. */
		static final float COIL_DROP = 1.0F, PUSH_TILT = 0.2F, COIL_TILT = 0.08F, FALL_TILT = 0.4F;
		/** How much the torso flattens (and widens) when coiled or landing. */
		static final float SQUASH = 0.14F;
		/** The hop: radians of clock per unit of walk, how much walking speed it takes to hop fully, the arc's height and the hips' rise as the legs unfold (pixels). */
		static final float HOP_RATE = 0.6662F * 0.85F, SPEED_GAIN = 1.4F, HOP_HEIGHT = 2.2F, PUSH_LIFT = 3.3F;
		/** The hop's timeline (fractions of a cycle): the push ends, the flight ends, the landing settles; how deep the landing coils; how much a landing adds to the squash. */
		static final float PUSH_END = 0.15F, FLIGHT_END = 0.6F, LAND_END = 0.7F, LAND_COIL = 0.6F, LAND_SQUASH = 0.8F;
		/** How far the front legs reach forward in the air (radians per unit of reach). */
		static final float FRONT_REACH = 1.0F;
		/** The throat sac: its idle swell, the croak's swell, how far the jaw opens and how far the head lifts for a croak. */
		static final float IDLE_SWELL = 0.3F, CROAK_SWELL = 0.7F, OPEN = 0.5F, CROAK_LIFT = 0.35F;
		/** The hind leg: thigh and shin lengths, and the hip's place on the torso (x out from the middle; y, z from the torso's centre). */
		private static final float L1 = 4.0F, L2 = 5.0F, HIP_X = 3.6F, HIP_Y = 0.5F, HIP_Z = 2.5F;
		/** The ankle's place from the hip (y down, z back), folded and fully stretched, and its height standing flat (the foot is 2 thick). */
		private static final float REST_DY = 2.9F, REST_DZ = -0.9F, EXT_DY = 3.6F, EXT_DZ = 6.0F, ANKLE_GROUND = 23.0F;
		/** The head's hinge on the torso (y, z from its centre), the front legs' shoulder (x, y, z) and the foot's length. */
		private static final float HEAD_Y = 0.2F, HEAD_Z = -3.2F, SHOULDER_X = 2.6F, SHOULDER_Y = 19.0F, SHOULDER_Z = -0.8F, FOOT_LEN = 3.5F;
		/** How far the head may nod down as you look down (radians of look), and the lowest the throat sac may hang (model y): the head lifts to keep it off the ground. */
		private static final float LOOK_DOWN = 0.5F, SAC_FLOOR = 23.9F;
		/** The jaw's hinge under the head (y, z), the sac's place under the jaw (y, z), its height and half its depth: the mesh's numbers, which the sac's floor check reuses. */
		private static final float JAW_Y = 0.5F, JAW_Z = -1.0F, SAC_Y = 1.0F, SAC_Z = -2.0F, SAC_H = 2.0F, SAC_HALF = 1.5F;

		private final ModelPart torso, jaw, sac, eyeRight, eyeLeft;
		private final ModelPart thighRight, thighLeft, shinRight, shinLeft, footRight, footLeft, handRight, handLeft;
		/** The hop's phase this frame: coil, extension, arc. */
		private final float[] hop = new float[3];

		Croaker() {
			super(bake(mesh()), "croaker");
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.jaw = this.head.getChild("jaw");
			this.sac = this.jaw.getChild("sac");
			this.eyeRight = this.head.getChild("right_eye");
			this.eyeLeft = this.head.getChild("left_eye");
			this.thighRight = root.getChild("right_thigh");
			this.thighLeft = root.getChild("left_thigh");
			this.shinRight = this.thighRight.getChild("shin");
			this.shinLeft = this.thighLeft.getChild("shin");
			this.footRight = this.shinRight.getChild("foot");
			this.footLeft = this.shinLeft.getChild("foot");
			this.handRight = this.rightLeg.getChild("hand");
			this.handLeft = this.leftLeg.getChild("hand");
			// the hidden body lies face-up on the frog's back (as Skitter's does): the plate sits on top of the torso
			this.insigniaY = 0.0F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 1.0F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: wide and flat, the jaw hinged under it, the throat sac hung from the jaw, the eyes on top
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -2.5F, -5.0F, 7.0F, 3.0F, 5.0F),
				PartPose.offset(0.0F, 19.1F, -1.1F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, 0.0F, -4.0F, 6.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, JAW_Y, JAW_Z));
			jaw.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(40, 64).addBox(-2.0F, 0.0F, -SAC_HALF, 4.0F, SAC_H, 2.0F * SAC_HALF),
				PartPose.offset(0.0F, SAC_Y, SAC_Z));
			head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(24, 64).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(-2.4F, -1.8F, -2.0F));
			head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(32, 64).mirror().addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(2.4F, -1.8F, -2.0F));
			// the body: wide and flat
			root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 72).addBox(-3.0F, -2.0F, -3.5F, 6.0F, 4.0F, 7.0F),
				PartPose.offset(0.0F, TORSO_Y, TORSO_Z));
			// the front legs are the man's legs (so the pose code still finds them): short, a flat hand on each
			PartDefinition right = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(-SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
			right.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(28, 106).addBox(-1.5F, 0.0F, -3.5F, 3.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 4.0F, 0.0F));
			PartDefinition left = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
			left.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(42, 106).mirror().addBox(-1.5F, 0.0F, -3.5F, 3.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 4.0F, 0.0F));
			// the hind legs, hip to thigh to shin to foot: each box runs a pixel past its joints so the bends have no gaps
			PartDefinition thighRight = root.addOrReplaceChild("right_thigh", CubeListBuilder.create().texOffs(0, 88).addBox(-1.0F, -1.0F, -5.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(-HIP_X, 21.0F, 6.0F));
			PartDefinition shinRight = thighRight.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(0, 96).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(0.0F, 0.0F, -L1));
			shinRight.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 106).addBox(-1.5F, -1.0F, -3.5F, 3.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, L2));
			PartDefinition thighLeft = root.addOrReplaceChild("left_thigh", CubeListBuilder.create().texOffs(16, 88).mirror().addBox(-1.0F, -1.0F, -5.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(HIP_X, 21.0F, 6.0F));
			PartDefinition shinLeft = thighLeft.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(16, 96).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(0.0F, 0.0F, -L1));
			shinLeft.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(14, 106).mirror().addBox(-1.5F, -1.0F, -3.5F, 3.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, L2));
			return mesh;
		}

		/** The hop's phase at {@code u} (0-1 through a cycle) into {@link #hop}: coil (0-1), the legs' extension (0-1), the arc (0-1). */
		private void cycle(float u) {
			float coil;
			float extend;
			float arc = 0.0F;
			if (u < PUSH_END) {
				// pushing off: the coil unwinds, the legs unfold, the feet still on the ground
				float s = smooth(u / PUSH_END);
				coil = 1.0F - s;
				extend = s;
			} else if (u < FLIGHT_END) {
				// in the air: the legs trail behind, then fold up for the landing
				float f = (u - PUSH_END) / (FLIGHT_END - PUSH_END);
				arc = Mth.sin(f * (float)Math.PI);
				extend = 1.0F - smooth(Mth.clamp((f - 0.25F) / 0.75F, 0.0F, 1.0F));
				coil = 0.0F;
			} else if (u < LAND_END) {
				// touching down: it settles into a coil
				coil = LAND_COIL * (u - FLIGHT_END) / (LAND_END - FLIGHT_END);
				extend = 0.0F;
			} else {
				// gathering itself for the next hop
				coil = LAND_COIL + (1.0F - LAND_COIL) * (u - LAND_END) / (1.0F - LAND_END);
				extend = 0.0F;
			}
			this.hop[0] = coil;
			this.hop[1] = extend;
			this.hop[2] = arc;
		}

		private static float smooth(float s) {
			return s * s * (3.0F - 2.0F * s);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.body.visible = this.rightArm.visible = this.leftArm.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg}) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed * SPEED_GAIN, 0.0F, 1.0F);
			float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			// the hop clock follows the walk, so it hops as fast as it moves and settles when it stops
			float turns = state.walkAnimationPos * HOP_RATE / ((float)Math.PI * 2.0F);
			this.cycle(turns - (float)Math.floor(turns));
			float coil = this.hop[0] * speed;
			float extend = this.hop[1] * speed;
			float arc = this.hop[2] * speed;
			if (state.isCrouching || Boolean.TRUE.equals(state.getData(XenoformClient.SNEAK))) {
				// low and flat, but it still straightens to push off
				coil = Math.max(coil, 0.9F * (1.0F - extend));
			}
			// a real fall or leap: stretched out behind, nose pitched with the fall; a landing squashes it flat
			float rising = Mth.clamp(-m.fall(), 0.0F, 1.0F);
			extend = Mth.lerp(air, extend, 0.75F + 0.25F * rising);
			arc *= 1.0F - air;
			coil = Math.min(1.3F, coil * (1.0F - air) + m.land() * LAND_SQUASH * (1.0F - extend));
			float lift = HOP_HEIGHT * arc + PUSH_LIFT * (float)Math.sqrt(extend);
			float tilt = TILT - extend * PUSH_TILT + coil * COIL_TILT + Mth.clamp(m.fall(), -1.0F, 1.0F) * air * FALL_TILT;
			float ty = TORSO_Y + coil * COIL_DROP - lift;

			// the torso: flattens when coiled, breathes at rest, swells a little on a croak
			float puff = 1.0F + swing * 0.06F;
			this.torso.setPos(0.0F, ty, TORSO_Z);
			this.torso.xRot = tilt;
			this.torso.yRot = m.turn() * 0.06F;
			this.torso.zRot = 0.0F;
			this.torso.yScale = (1.0F - SQUASH * coil + 0.02F * m.breath()) * puff;
			this.torso.xScale = (1.0F + 0.3F * SQUASH * coil) * puff;
			this.torso.zScale = (1.0F + 0.5F * SQUASH * coil) * puff;
			// the hidden body is the insignia's anchor: face-up on the frog's back, the plate flush with the top
			this.body.setPos(0.0F, ty, TORSO_Z);
			this.body.xRot = tilt - (float)Math.PI * 0.5F;
			this.insigniaY = 0.0F;
			this.insigniaZ = -2.0F * this.torso.yScale;
			this.insigniaSize = 1.0F;

			// the head rides the front of the torso, lifting out of a coil, looking where you look, thrown up for a croak
			float cs = Mth.cos(tilt);
			float sn = Mth.sin(tilt);
			this.head.setPos(0.0F, ty + HEAD_Y * cs - HEAD_Z * sn - coil * 0.7F, TORSO_Z + HEAD_Y * sn + HEAD_Z * cs);
			float pitch = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.7F, LOOK_DOWN);
			this.head.xRot = tilt * 0.8F + pitch * 0.5F - swing * CROAK_LIFT + m.land() * 0.2F * (1.0F - swing);
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -0.9F, 0.9F);
			this.head.zRot = -m.turn() * 0.08F;

			// the throat sac swells and sinks, balloons on a croak with the mouth open; the eyes bulge with it and blink
			float idle = 0.5F + 0.5F * Mth.sin(age * 0.07F);
			float swell = IDLE_SWELL * idle + CROAK_SWELL * swing;
			this.jaw.xRot = OPEN * swing + 0.05F * idle;
			this.sac.yScale = 1.0F + 0.3F * swell;
			this.sac.xScale = this.sac.zScale = 1.0F + 0.5F * swell;
			// a croak looking down out of a deep coil would push the sac into the ground: lift the head just clear of it
			float jc = Mth.cos(this.jaw.xRot);
			float js = Mth.sin(this.jaw.xRot);
			float hc = Mth.cos(this.head.xRot);
			float hs = Mth.sin(this.head.xRot);
			float low = -Float.MAX_VALUE;
			for (int end = -1; end <= 1; end += 2) {
				// the sac's front and back bottom edges in the jaw's space, then the head's, then below the head's hinge
				float y1 = SAC_Y + SAC_H * this.sac.yScale;
				float z1 = SAC_Z + SAC_HALF * end * this.sac.zScale;
				float y2 = JAW_Y + y1 * jc - z1 * js;
				float z2 = JAW_Z + y1 * js + z1 * jc;
				low = Math.max(low, y2 * hc - z2 * hs);
			}
			this.head.y = Math.min(this.head.y, SAC_FLOOR - low);
			float blink = Math.max(0.0F, 1.0F - Math.abs(age % 90.0F - 2.0F) * 0.5F);
			float bulge = 1.0F + 0.12F * swell;
			this.eyeRight.xScale = this.eyeRight.zScale = this.eyeLeft.xScale = this.eyeLeft.zScale = bulge;
			this.eyeRight.yScale = this.eyeLeft.yScale = Mth.lerp(blink, bulge, 0.1F);

			// the front legs stay planted; they reach forward in the air and brace on a croak, the hands kept flat
			float reach = Math.max(Math.max(air, arc * 0.9F), extend * 0.5F);
			float brace = swing * 0.2F;
			this.rightLeg.setPos(-SHOULDER_X, SHOULDER_Y - lift, SHOULDER_Z);
			this.leftLeg.setPos(SHOULDER_X, SHOULDER_Y - lift, SHOULDER_Z);
			this.rightLeg.xRot = this.leftLeg.xRot = -reach * FRONT_REACH + brace;
			this.handRight.xRot = this.handLeft.xRot = reach * FRONT_REACH - brace;

			this.hind(ty, tilt, extend);
		}

		/**
		 * Both hind legs: the ankle goes where a foot would be (flat on the ground, sliding back as the leg unfolds, then
		 * hanging behind once the body has left it), and the thigh and shin are solved to reach it with the knee up and
		 * forward, so the leg never breaks at a joint.
		 */
		private void hind(float ty, float tilt, float extend) {
			float cs = Mth.cos(tilt);
			float sn = Mth.sin(tilt);
			float hipY = ty + HIP_Y * cs - HIP_Z * sn;
			float hipZ = TORSO_Z + HIP_Y * sn + HIP_Z * cs;
			float ankleY = Math.min(ANKLE_GROUND, hipY + Mth.lerp(extend, REST_DY, EXT_DY));
			float ankleZ = hipZ + Mth.lerp(extend, REST_DZ, EXT_DZ);
			float dy = ankleY - hipY;
			float dz = ankleZ - hipZ;
			float far = Math.max(0.01F, (float)Math.sqrt(dy * dy + dz * dz));
			float reach = Mth.clamp(far, Math.abs(L1 - L2) + 0.1F, L1 + L2 - 0.05F);
			float uy = dy / far;
			float uz = dz / far;
			dy = uy * reach;
			dz = uz * reach;
			// two-bone IK: the knee is a along the chord and h out of it, on the up-and-forward side
			float a = (L1 * L1 - L2 * L2 + reach * reach) / (2.0F * reach);
			float h = (float)Math.sqrt(Math.max(0.0F, L1 * L1 - a * a));
			float kneeY = hipY + a * uy + h * uz;
			float kneeZ = hipZ + a * uz - h * uy;
			float thigh = (float)Math.atan2(kneeY - hipY, -(kneeZ - hipZ));
			float shin = (float)Math.atan2(-(hipY + dy - kneeY), hipZ + dz - kneeZ);
			// the foot hangs flat on the ground and tips toes-down as the ankle rises off it
			float gap = Math.max(0.0F, ANKLE_GROUND - (hipY + dy));
			float foot = (float)Math.asin(Math.min(1.0F, gap / FOOT_LEN)) * 0.85F;
			for (int side = 0; side < 2; side++) {
				ModelPart t = side == 0 ? this.thighRight : this.thighLeft;
				ModelPart s = side == 0 ? this.shinRight : this.shinLeft;
				ModelPart f = side == 0 ? this.footRight : this.footLeft;
				t.setPos(side == 0 ? -HIP_X : HIP_X, hipY, hipZ);
				t.xRot = thigh;
				s.xRot = shin - thigh;
				f.xRot = foot - shin;
			}
		}
	}

	/**
	 * (0.18.103, patch 5) Dud: small, soft and round, two nubs on its head; it wobbles as it goes.
	 * <p>
	 * (0.19.84) A soft round squishy ball, not a man: a stack of boxes rounding a ball (the body part), a worried face on
	 * a plate bulging from its front (it turns a little where you look), two nubs on top, nubby hands at its sides and
	 * tiny stubby feet under it. It waddles, rocking from foot to foot with a jiggle; in the air it stretches and on
	 * landing it squashes (Boing). Reads {@code XenoformClient.ACT}: "dead" while it plays dead (the server's
	 * {@code Live.horn > 0}) flops it on its back, face up, feet and hands sticking up; "roll" while it rolls away (the
	 * server's roll timer) tucks its face and nubs in and spins it as a ball. The humanoid head is hidden (empty); the arms
	 * and legs are the hands and feet.
	 */
	static final class Dud extends FormModel {
		// ---- tuning ----
		/** The ball's rest height (its centre, model y), its half height (core plus caps) and its half depth lying down. */
		static final float BODY_Y = 16.0F, RADIUS = 7.0F, LYING_RADIUS = 6.0F;
		/** The waddle: radians of clock per unit of walk, walking speed it takes to waddle fully, how far it rocks (radians), bobs and jiggles (pixels / fraction). */
		static final float WADDLE_RATE = 0.6662F * 1.2F, SPEED_GAIN = 1.5F, ROCK = 0.16F, BOB = 0.8F, JIGGLE = 0.05F;
		/** The feet: how far they stride and lift (pixels), and the rest height of their pivots. */
		static final float STRIDE = 1.6F, LIFT = 1.6F, FOOT_REST = 21.0F;
		/** Boing: how much it stretches in the air and squashes on landing (fractions of its height). */
		static final float STRETCH = 0.2F, SQUASH = 0.25F;
		/** The hands: how far out they sit (pixels), where on the ball (body-space y), how far they swing and flap out. */
		static final float HAND_X = 6.5F, HAND_Y = -1.0F, HAND_SWING = 0.7F, HAND_OUT = 0.25F;
		/** Playing dead: where the feet stick up from the ball's face-up front (body-space y toward the bottom cap, z just inside the front). */
		static final float DEAD_FOOT_Y = 4.0F, DEAD_FOOT_Z = -5.5F;
		/** The roll: how far it spins per unit of walk and per tick (radians), how tight it is tucked and its centre height. */
		static final float ROLL_PER_WALK = 0.9F, ROLL_PER_TICK = 0.3F, ROLL_SCALE = 0.9F, ROLL_Y = 16.6F;
		/** The roll's tuck: how far the face plate sinks back and down into the ball and the nubs down into the top cap (pixels). */
		static final float ROLL_FACE_BACK = 2.0F, ROLL_FACE_DOWN = 1.0F, ROLL_NUB_DOWN = 1.0F;
		/** How far the face turns and nods with your head (radians). */
		static final float FACE_YAW = 0.6F, FACE_PITCH = 0.3F;
		/** How far a sneaking renderer sinks the model (pixels); and how far down the arm the nub hand is drawn in first person. */
		static final float CROUCH_SINK = 2.0F / 0.9375F, HAND_DOWN = 8.0F;

		private final ModelPart face, nubRight, nubLeft;

		Dud() {
			super(bake(mesh()), "dud");
			this.face = this.body.getChild("face");
			this.nubRight = this.body.getChild("right_nub");
			this.nubLeft = this.body.getChild("left_nub");
			// small, low on the ball's front under the face plate
			this.insigniaY = 1.5F;
			this.insigniaZ = -6.0F;
			this.insigniaSize = 0.7F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the humanoid head is only an anchor: the face is on the ball
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			// the ball: a fat core with a cap above and below
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
				.texOffs(0, 64).addBox(-6.0F, -5.0F, -6.0F, 12.0F, 10.0F, 12.0F)
				.texOffs(0, 86).addBox(-4.0F, -7.0F, -4.0F, 8.0F, 2.0F, 8.0F)
				.texOffs(32, 86).addBox(-4.0F, 5.0F, -4.0F, 8.0F, 2.0F, 8.0F), PartPose.offset(0.0F, BODY_Y, 0.0F));
			body.addOrReplaceChild("face", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -2.5F, -3.0F, 10.0F, 5.0F, 4.0F),
				PartPose.offset(0.0F, -4.5F, -4.0F));
			body.addOrReplaceChild("right_nub", CubeListBuilder.create().texOffs(0, 96).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(-2.5F, -6.5F, 0.0F));
			body.addOrReplaceChild("left_nub", CubeListBuilder.create().texOffs(0, 96).mirror().addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
				PartPose.offset(2.5F, -6.5F, 0.0F));
			// nubby hands and stubby feet: the humanoid arms and legs, 3 pixels cube
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(-HAND_X, BODY_Y + HAND_Y, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(HAND_X, BODY_Y + HAND_Y, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(-3.0F, FOOT_REST, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 3.0F, 3.0F),
				PartPose.offset(3.0F, FOOT_REST, 0.0F));
			return mesh;
		}

		/** (0.19.84) The nub hand drawn down the arm, where a player's fist would be, for the first-person arm (the arm is only the nub). */
		void handPose(ModelPart limb) {
			limb.y += HAND_DOWN;
		}

		/** Puts {@code part} at a point of the ball's own space (x, y, z from its centre), following its tilt and scale. */
		private void place(ModelPart part, float x, float y, float z) {
			float xx = x * this.body.xScale, yy = y * this.body.yScale, zz = z * this.body.zScale;
			float c = Mth.cos(this.body.xRot), s = Mth.sin(this.body.xRot);
			float y1 = yy * c - zz * s, z1 = yy * s + zz * c;
			float c2 = Mth.cos(this.body.zRot), s2 = Mth.sin(this.body.zRot);
			part.x = this.body.x + xx * c2 - y1 * s2;
			part.y = this.body.y + xx * s2 + y1 * c2;
			part.z = this.body.z + z1;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.resetPose();
			this.body.resetPose();
			this.rightArm.resetPose();
			this.leftArm.resetPose();
			this.rightLeg.resetPose();
			this.leftLeg.resetPose();
			this.face.resetPose();
			this.nubRight.resetPose();
			this.nubLeft.resetPose();
			this.hat.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = true;
			XenoMotion.Read m = XenoMotion.read(state);
			String act = state.getData(XenoformClient.ACT);
			boolean dead = "dead".equals(act), roll = !dead && "roll".equals(act);
			// (0.19.84) a sneaking player is drawn CROUCH_SINK lower: the ground that much higher in the model
			float sink = state.isCrouching ? CROUCH_SINK : 0.0F;
			float age = state.ageInTicks;
			float phase = state.walkAnimationPos * WADDLE_RATE;
			float spd = Mth.clamp(state.walkAnimationSpeed * SPEED_GAIN, 0.0F, 1.0F);
			float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			float land = Mth.clamp(m.land(), 0.0F, 1.0F);
			// the face looks where you look (not while it plays dead or rolls), the nubs wag
			boolean still = dead || roll;
			this.face.yRot = still ? 0.0F : Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -FACE_YAW, FACE_YAW);
			this.face.xRot = still ? 0.0F : Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.4F, -FACE_PITCH, FACE_PITCH);
			float wag = Mth.sin(age * 0.17F) * 0.25F + m.turn() * 0.5F;
			this.nubRight.zRot = wag - 0.1F;
			this.nubLeft.zRot = wag + 0.1F;
			if (dead) {
				// flopped on its back: the ball lies face-up, the feet stick up off its top by the bottom cap, the hands up at its sides; they twitch
				float breath = Mth.sin(age * 0.08F) * 0.03F;
				this.body.xRot = -(float)Math.PI / 2.0F;
				this.body.zRot = 0.0F;
				this.body.yScale = 1.0F + breath;
				this.body.xScale = this.body.zScale = 1.0F - breath * 0.5F;
				this.body.y = 24.0F - sink - LYING_RADIUS * this.body.zScale;
				for (int side = 0; side < 2; side++) {
					float sign = side == 0 ? -1.0F : 1.0F;
					ModelPart foot = side == 0 ? this.rightLeg : this.leftLeg;
					ModelPart hand = side == 0 ? this.rightArm : this.leftArm;
					this.place(foot, sign * 3.0F, DEAD_FOOT_Y, DEAD_FOOT_Z);
					foot.xRot = (float)Math.PI + Mth.sin(age * 0.2F + side * 2.1F) * 0.25F;
					foot.zRot = sign * 0.15F;
					this.place(hand, sign * HAND_X, HAND_Y, 0.0F);
					hand.xRot = (float)Math.PI + Mth.sin(age * 0.17F + side * 1.3F) * 0.2F;
					hand.zRot = sign * 0.35F;
				}
				return;
			}
			if (roll) {
				// tucked into a ball (face and nubs pulled in, so nothing pokes through the ground as it turns), spinning over the ground
				this.face.z += ROLL_FACE_BACK;
				this.face.y += ROLL_FACE_DOWN;
				this.nubRight.y += ROLL_NUB_DOWN;
				this.nubLeft.y += ROLL_NUB_DOWN;
				this.body.xRot = state.walkAnimationPos * ROLL_PER_WALK + age * ROLL_PER_TICK;
				this.body.zRot = 0.0F;
				this.body.xScale = this.body.yScale = this.body.zScale = ROLL_SCALE;
				this.body.y = ROLL_Y - sink;
				this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
				return;
			}
			// the ball: it rocks from foot to foot and jiggles; stretched in the air, squashed on landing and when crouched
			float height = 1.0F + STRETCH * air - SQUASH * land - (state.isCrouching ? 0.1F : 0.0F) + Mth.sin(age * 0.14F) * 0.03F
				+ Mth.sin(phase * 2.0F) * JIGGLE * spd - swing * 0.08F;
			float width = 1.0F / (float)Math.sqrt(height);
			this.body.yScale = height;
			this.body.xScale = this.body.zScale = width;
			float bob = Mth.abs(Mth.sin(phase)) * BOB * spd;
			this.body.y = 24.0F - sink - 1.0F - RADIUS * height - bob;
			this.body.zRot = Mth.sin(phase) * ROCK * spd + Mth.sin(age * 0.09F) * 0.02F;
			this.body.xRot = swing * 0.25F + spd * 0.06F;
			for (int side = 0; side < 2; side++) {
				float sign = side == 0 ? -1.0F : 1.0F;
				ModelPart foot = side == 0 ? this.rightLeg : this.leftLeg;
				ModelPart hand = side == 0 ? this.rightArm : this.leftArm;
				// a foot lifts and strides forward while the ball rocks off it, then plants and slides back
				float s = Mth.sin(phase) * sign * -1.0F;
				float lift = Math.max(0.0F, s) * LIFT * spd;
				foot.x = sign * 3.0F;
				foot.y = FOOT_REST - sink - lift - air * 1.2F;
				foot.z = -sign * Mth.cos(phase) * STRIDE * spd;
				this.place(hand, sign * HAND_X, HAND_Y, 0.0F);
				hand.xRot = -sign * Mth.sin(phase) * HAND_SWING * spd + Mth.sin(age * 0.1F + side * 1.7F) * 0.05F;
				// out from the ball (a part on the -x side swings out with + zRot, as vanilla's right arm), more in the air
				hand.zRot = -sign * (HAND_OUT + air * 0.6F);
			}
			// the swinging hand jabs ahead
			this.rightArm.xRot -= swing * 1.8F;
		}
	}

	/**
	 * (0.18.107, Scan B; 0.19.84 a stooped ice brute) Rime: angular ice, a crown of icicles and icicle spines down its back
	 * that shiver. Now a stooped brute: the back bent forward over its hips (the trunk is hung from them, tipped by
	 * {@link #HUNCH}), a hump of ice for shoulders, icicle spines along the back, a low-set head with a frost crown and two
	 * fangs, long arms ending in big fists that hang to the knees, thick two-jointed legs. It walks a heavy stooped
	 * stride: each foot slides back flat on the ground, then swings forward and plants, the hips sinking as the feet
	 * spread, the shoulders twisting against the hips, the arms swinging against the legs. Every part keeps the man's name
	 * (head, body, arms, legs) so the held items, the first-person arm, the attack swing and Cryo's poses (Frost Breath,
	 * the slide, Flash Freeze) all still work: the body part is the pivot {@link #WAIST} above the hips that
	 * {@code CryoPose.turnTorso} turns the torso about (so Cryo's lean swings the trunk about the hips), turned upside down
	 * so the insignia plate (which faces the body's front) lies on the back, below the spines, where it shows over the head.
	 */
	static final class Rime extends FormModel {
		// ---- tuning ----
		/** The hips (model y, z, and x out from the middle) the trunk hangs from, and how far it is stooped (radians, + is forward). */
		static final float HIP_Y = 11.0F, HIP_Z = 2.0F, HIP_X = 3.1F, HUNCH = 0.72F;
		/** {@code CryoPose.WAIST}: Cryo turns the torso about this far below the body part's rest place, so the body part rests this far above the hips. */
		static final float WAIST = 12.0F;
		/** The head's neck (model y, z), low and forward of the hump, and how far it is dipped (radians). */
		static final float HEAD_Y = 5.4F, HEAD_Z = -8.5F, HEAD_DIP = 0.18F;
		/** The shoulders (model x out from the middle, y, z): the arms' pivots. */
		static final float SHOULDER_X = 8.0F, SHOULDER_Y = 3.8F, SHOULDER_Z = -4.4F;
		/** The leg: thigh and shin lengths, where the ankle hangs standing (the foot is 2 thick under it, so the ground is 24), and the ankle's rest place ahead of the hip (z). */
		private static final float LEG = 6.0F, ANKLE_Y = 22.0F, REST_Z = -1.1F;
		/** The stride: how far a foot slides each way from its rest place (pixels), how high it lifts, the share of the cycle it is planted, how fast the legs go against the walk, and how much walking speed it takes to stride fully. */
		static final float STRIDE = 3.2F, LIFT = 2.6F, STANCE = 0.6F, GAIT = 0.6662F * 0.75F, SPEED_GAIN = 1.6F;
		/** The hips sink this far as the feet spread (pixels), crouched and landing. */
		static final float BOB = 0.5F, CROUCH_DROP = 1.6F, LAND_DROP = 1.2F;
		/** How far a sneaking renderer sinks the model (pixels): the ground and the hips are raised that much. */
		static final float CROUCH_SINK = 2.0F / 0.9375F;
		/** The trunk: how much more it stoops at a run and crouched, how far the shoulders twist and the hips roll with the stride, how much it rocks (radians). */
		static final float RUN_STOOP = 0.18F, CROUCH_STOOP = 0.35F, TWIST = 0.12F, ROLL = 0.05F, ROCK = 0.04F;
		/** The arms: rest pitch, how far they swing against the legs, how far they hang out (radians), and the fist's curl. */
		static final float ARM_REST = -0.1F, ARM_SWING = 0.55F, ARM_OUT = 0.1F, FIST_CURL = -0.2F;
		/** The attack: how far the arm is thrown up and over, how far the trunk lunges, twists and the head drops. */
		static final float BLOW_ARM = 2.5F, BLOW_LUNGE = 0.25F, BLOW_TWIST = 0.3F, BLOW_HEAD = 0.3F;
		/** The back spines: their heights on the trunk (y, from the hips; kept above the insignia), how far each is laid back (radians) and how long each is drawn. */
		private static final float[] SPINE_Y = {-11.4F, -9.6F, -7.8F, -6.0F};
		private static final float[] SPINE_LEN = {1.25F, 1.1F, 0.95F, 0.8F};
		static final float SPINE_TILT = -0.8F;
		/** The insignia on the back (trunk y from the hips, over the skin's rows 73-79 of the back face) and its size. */
		static final float BACK_INSIGNIA_Y = -3.5F, BACK_INSIGNIA_SIZE = 0.6F;
		/** The crown's icicles: x, z on the head, how far swept back, how far splayed out (radians), and how long each is drawn. */
		private static final float[][] CROWN = {{-2.6F, -1.5F, -0.3F, -0.4F}, {2.6F, -1.5F, -0.3F, 0.4F}, {-1.1F, 1.9F, -0.6F, -0.15F}, {1.1F, 1.9F, -0.6F, 0.15F}};
		private static final float[] CROWN_LEN = {1.1F, 1.1F, 1.0F, 1.0F};
		/** The hips in the body part's own space (it rests {@link #WAIST} above them, stooped and turned upside down). */
		private static final float BODY_LY = -WAIST * (float)Math.cos(HUNCH), BODY_LZ = WAIST * (float)Math.sin(HUNCH);
		/** The neck and the right shoulder in the trunk's space (from the hips, unstooped), so they ride the trunk exactly. */
		private static final float[] HEAD_AT = trunkSpace(0.0F, HEAD_Y, HEAD_Z), SHOULDER_AT = trunkSpace(-SHOULDER_X, SHOULDER_Y, SHOULDER_Z);

		private final ModelPart trunk;
		private final ModelPart[] spines = new ModelPart[SPINE_Y.length];
		private final ModelPart[] crown = new ModelPart[CROWN.length];
		private final ModelPart shardRight, shardLeft, fistRight, fistLeft;
		private final ModelPart shinRight, shinLeft, footRight, footLeft;
		/** This frame's crouch sink (pixels). */
		private float sink;
		/** This frame's foot (right, left): the ankle's z from the hip, how far it is lifted, the toe's tip (radians). */
		private final float[] stepRight = new float[3], stepLeft = new float[3];
		/** Scratch for a turned point. */
		private final float[] turned = new float[3];

		Rime() {
			super(bake(mesh()), "rime");
			this.ownsLegs = true;
			this.trunk = this.body.getChild("trunk");
			for (int i = 0; i < SPINE_Y.length; i++) {
				this.spines[i] = this.trunk.getChild("spine" + i);
			}
			for (int i = 0; i < CROWN.length; i++) {
				this.crown[i] = this.head.getChild("crown" + i);
			}
			this.shardRight = this.trunk.getChild("right_shard");
			this.shardLeft = this.trunk.getChild("left_shard");
			this.fistRight = this.rightArm.getChild("fist");
			this.fistLeft = this.leftArm.getChild("fist");
			this.shinRight = this.rightLeg.getChild("shin");
			this.shinLeft = this.leftLeg.getChild("shin");
			this.footRight = this.shinRight.getChild("foot");
			this.footLeft = this.shinLeft.getChild("foot");
			// on the back below the spines (the body part is the trunk turned upside down: its -z is the trunk's back)
			this.insigniaY = BODY_LY - BACK_INSIGNIA_Y;
			this.insigniaZ = BODY_LZ - 3.0F;
			this.insigniaSize = BACK_INSIGNIA_SIZE;
		}

		/** A model point at rest into the trunk's space: from the hips, the stoop undone. */
		private static float[] trunkSpace(float x, float y, float z) {
			float dy = y - HIP_Y;
			float dz = z - HIP_Z;
			float c = (float)Math.cos(HUNCH);
			float s = (float)Math.sin(HUNCH);
			return new float[] {x, dy * c + dz * s, -dy * s + dz * c};
		}

		/** {@code (x, y, z)} turned as a model part turns it (x, then y, then z) into {@code out}. */
		private static void turn(float xRot, float yRot, float zRot, float x, float y, float z, float[] out) {
			float cx = Mth.cos(xRot), sx = Mth.sin(xRot), cy = Mth.cos(yRot), sy = Mth.sin(yRot), cz = Mth.cos(zRot), sz = Mth.sin(zRot);
			float y1 = y * cx - z * sx;
			float z1 = y * sx + z * cx;
			float x2 = x * cy + z1 * sy;
			float z2 = -x * sy + z1 * cy;
			out[0] = x2 * cz - y1 * sz;
			out[1] = x2 * sz + y1 * cz;
			out[2] = z2;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: low and forward of the shoulders, a frost crown of icicles swept back, two fangs hung under the jaw
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F,
				new CubeDeformation(0.3F)), PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			for (int i = 0; i < CROWN.length; i++) {
				float[] c = CROWN[i];
				head.addOrReplaceChild("crown" + i, CubeListBuilder.create().texOffs(60, 64 + 5 * i).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
					PartPose.offsetAndRotation(c[0], -3.7F, c[1], c[2], 0.0F, c[3]));
			}
			head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(44, 114).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				PartPose.offset(-2.0F, 3.6F, -3.8F));
			head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(48, 114).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F),
				PartPose.offset(2.0F, 3.6F, -3.8F));
			// the body part: WAIST above the hips, stooped and turned upside down; the trunk hangs from the hips in it, turned back upright
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, HIP_Y - WAIST, HIP_Z, HUNCH + (float)Math.PI, 0.0F, 0.0F));
			PartDefinition trunk = body.addOrReplaceChild("trunk", CubeListBuilder.create().texOffs(0, 64).addBox(-4.5F, -10.0F, -3.0F, 9.0F, 10.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, BODY_LY, BODY_LZ, (float)Math.PI, 0.0F, 0.0F));
			trunk.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(0, 80).addBox(-6.5F, -3.0F, -3.5F, 13.0F, 5.0F, 7.0F, new CubeDeformation(0.2F)),
				PartPose.offset(0.0F, -9.0F, 0.5F));
			for (int i = 0; i < SPINE_Y.length; i++) {
				trunk.addOrReplaceChild("spine" + i, CubeListBuilder.create().texOffs(56, 90 + 8 * i).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
					PartPose.offsetAndRotation(0.0F, SPINE_Y[i], 2.7F, SPINE_TILT, 0.0F, 0.0F));
			}
			trunk.addOrReplaceChild("right_shard", CubeListBuilder.create().texOffs(44, 119).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offsetAndRotation(-6.2F, -10.5F, 0.5F, 0.0F, 0.0F, -0.55F));
			trunk.addOrReplaceChild("left_shard", CubeListBuilder.create().texOffs(56, 122).mirror().addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offsetAndRotation(6.2F, -10.5F, 0.5F, 0.0F, 0.0F, 0.55F));
			// the arms: long, from the hump to the knees, a big fist hung on each (its box fattened a little past the arm)
			PartDefinition right = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 92).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 12.0F, 5.0F),
				PartPose.offset(-SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
			right.addOrReplaceChild("fist", CubeListBuilder.create().texOffs(0, 109).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.6F)),
				PartPose.offset(0.0F, 10.5F, 0.0F));
			PartDefinition left = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(20, 92).mirror().addBox(-2.5F, -1.0F, -2.5F, 5.0F, 12.0F, 5.0F),
				PartPose.offset(SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
			left.addOrReplaceChild("fist", CubeListBuilder.create().texOffs(20, 109).mirror().addBox(-2.5F, -1.0F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0.6F)),
				PartPose.offset(0.0F, 10.5F, 0.0F));
			// the legs: thigh, shin and foot, each box running a pixel past its joint so the bends show no gap
			PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(40, 64).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 8.0F, 5.0F),
				PartPose.offset(-HIP_X, HIP_Y, HIP_Z));
			PartDefinition rightShin = rightLeg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(40, 90).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F),
				PartPose.offset(0.0F, LEG, 0.0F));
			rightShin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(0, 119).addBox(-2.5F, -1.0F, -4.0F, 5.0F, 3.0F, 6.0F),
				PartPose.offset(0.0F, LEG, 0.0F));
			PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(40, 77).mirror().addBox(-2.5F, -1.0F, -2.5F, 5.0F, 8.0F, 5.0F),
				PartPose.offset(HIP_X, HIP_Y, HIP_Z));
			PartDefinition leftShin = leftLeg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(40, 101).mirror().addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F),
				PartPose.offset(0.0F, LEG, 0.0F));
			leftShin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(22, 119).mirror().addBox(-2.5F, -1.0F, -4.0F, 5.0F, 3.0F, 6.0F),
				PartPose.offset(0.0F, LEG, 0.0F));
			return mesh;
		}

		/**
		 * One foot's place at {@code f} (0-1 through its cycle) into {@code out}: planted flat, sliding back, for the first
		 * {@link #STANCE} of it; then swung forward, lifted, toe down as it leaves and up as it lands. Scaled by {@code amp}
		 * (0 stands still, 1 strides fully).
		 */
		private static void step(float f, float amp, float[] out) {
			float z;
			float lift = 0.0F;
			float toe = 0.0F;
			if (f < STANCE) {
				z = REST_Z - STRIDE * (1.0F - 2.0F * f / STANCE);
			} else {
				float s = (f - STANCE) / (1.0F - STANCE);
				float e = s * s * (3.0F - 2.0F * s);
				float arc = Mth.sin(s * (float)Math.PI);
				z = REST_Z + STRIDE * (1.0F - 2.0F * e);
				lift = LIFT * arc;
				toe = 0.5F * arc * (1.0F - s) - 0.3F * arc * s;
			}
			out[0] = REST_Z + (z - REST_Z) * amp;
			out[1] = lift * amp;
			out[2] = toe * amp;
		}

		/**
		 * A leg from the hip to the ankle: the thigh and shin are solved to reach it with the knee forward, so the leg never
		 * breaks at a joint, and the foot is laid at {@code toe} to the ground.
		 */
		private static void leg(ModelPart thigh, ModelPart shin, ModelPart foot, float hipY, float ankleY, float ankleZ, float toe) {
			float dy = ankleY - hipY;
			float dz = ankleZ - HIP_Z;
			float reach = Mth.clamp((float)Math.sqrt(dy * dy + dz * dz), 3.0F, 2.0F * LEG - 0.05F);
			float thighAngle = (float)Math.atan2(dz, dy) - (float)Math.acos(reach / (2.0F * LEG));
			float kneeY = hipY + LEG * Mth.cos(thighAngle);
			float kneeZ = HIP_Z + LEG * Mth.sin(thighAngle);
			float shinAngle = (float)Math.atan2(ankleZ - kneeZ, ankleY - kneeY);
			thigh.xRot = thighAngle;
			shin.xRot = shinAngle - thighAngle;
			foot.xRot = toe - shinAngle;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the arm poses vanilla chose for what is held (a bow drawn, a shield up, ...): kept for those, the walk is ours otherwise
			float[] heldRight = {this.rightArm.xRot, this.rightArm.yRot, this.rightArm.zRot};
			float[] heldLeft = {this.leftArm.xRot, this.leftArm.yRot, this.leftArm.zRot};
			boolean emptyRight = state.rightArmPose == HumanoidModel.ArmPose.EMPTY;
			boolean emptyLeft = state.leftArmPose == HumanoidModel.ArmPose.EMPTY;
			boolean itemRight = state.rightArmPose == HumanoidModel.ArmPose.ITEM;
			boolean itemLeft = state.leftArmPose == HumanoidModel.ArmPose.ITEM;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.trunk, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.fistRight,
				this.fistLeft, this.shinRight, this.shinLeft, this.footRight, this.footLeft, this.shardRight, this.shardLeft}) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float tau = (float)Math.PI * 2.0F;
			boolean crouch = state.isCrouching || Boolean.TRUE.equals(state.getData(XenoformClient.SNEAK));
			// Cryo's poses turn the torso from the parts' rest places (CryoPose.turnTorso, not when crouched): the trunk holds its rest stoop for them
			boolean cryo = !state.isCrouching && state.getData(com.jaymospowers.client.power.cryo.CryoPose.KEY) != null;
			float amp = Mth.clamp(state.walkAnimationSpeed * SPEED_GAIN, 0.0F, 1.0F);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			float sw = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float blow = Mth.sin(sw * (float)Math.PI);
			float turns = state.walkAnimationPos * GAIT / tau;
			float u = turns - (float)Math.floor(turns);
			float phase = u * tau;
			step(u, amp, this.stepRight);
			step(u + 0.5F - (float)Math.floor(u + 0.5F), amp, this.stepLeft);

			// the hips sink as the feet spread, crouched and landing; they hold still in the air
			float spread = Math.max(Math.abs(this.stepRight[0] - REST_Z), Math.abs(this.stepLeft[0] - REST_Z)) / STRIDE;
			// (0.19.84) a sneaking player is drawn CROUCH_SINK lower: the ground (and so the hips) that much higher in the model
			this.sink = state.isCrouching ? CROUCH_SINK : 0.0F;
			float hipY = (cryo ? HIP_Y : HIP_Y + (BOB * spread + (crouch ? CROUCH_DROP : 0.0F) + m.land() * LAND_DROP) * (1.0F - air)) - this.sink;
			float tuck = air * 2.0F;
			this.rightLeg.setPos(-HIP_X, hipY, HIP_Z);
			this.leftLeg.setPos(HIP_X, hipY, HIP_Z);
			this.legTo(hipY, this.stepRight, this.rightLeg, this.shinRight, this.footRight, tuck);
			this.legTo(hipY, this.stepLeft, this.leftLeg, this.shinLeft, this.footLeft, tuck);

			// the trunk: stooped, more at a run, crouched, lunging on a blow, tipped nose-down in a fall; the shoulders twist against the hips
			float sign = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT ? -1.0F : 1.0F;
			float hunch = HUNCH;
			float twist = 0.0F;
			float roll = 0.0F;
			if (!cryo) {
				hunch += RUN_STOOP * amp + (crouch ? CROUCH_STOOP : 0.0F) + blow * BLOW_LUNGE
					+ Mth.clamp(m.fall(), -1.0F, 1.0F) * air * 0.2F + 0.02F * m.breath() + ROCK * Mth.cos(phase * 2.0F) * amp;
				twist = -Mth.sin(phase) * TWIST * amp + sign * Mth.sin((float)Math.sqrt(sw) * tau) * BLOW_TWIST;
				roll = Mth.sin(phase) * ROLL * amp + m.turn() * 0.08F;
			}
			// the body part stands WAIST above the hips in the trunk's turn (upside down), so the hips stay put
			this.body.xRot = hunch + (float)Math.PI;
			this.body.yRot = twist;
			this.body.zRot = roll;
			turn(this.body.xRot, twist, roll, 0.0F, BODY_LY, BODY_LZ, this.turned);
			this.body.setPos(-this.turned[0], hipY - this.turned[1], HIP_Z - this.turned[2]);
			this.trunk.yScale = 1.0F + 0.015F * m.breath();
			// the head and shoulders ride the trunk as it turns about the hips
			turn(hunch, twist, roll, HEAD_AT[0], HEAD_AT[1], HEAD_AT[2], this.turned);
			this.head.setPos(this.turned[0], hipY + this.turned[1], HIP_Z + this.turned[2]);
			turn(hunch, twist, roll, SHOULDER_AT[0], SHOULDER_AT[1], SHOULDER_AT[2], this.turned);
			this.rightArm.setPos(this.turned[0], hipY + this.turned[1], HIP_Z + this.turned[2]);
			turn(hunch, twist, roll, -SHOULDER_AT[0], SHOULDER_AT[1], SHOULDER_AT[2], this.turned);
			this.leftArm.setPos(this.turned[0], hipY + this.turned[1], HIP_Z + this.turned[2]);

			// the head: dipped, looking where you look, dropping with a blow, rolling with the trunk against the turn
			float tip = hunch - HUNCH;
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.7F, 0.9F);
			this.head.xRot = look + HEAD_DIP + tip * 0.6F + blow * BLOW_HEAD;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.0F, 1.0F) - twist * 0.5F;
			this.head.zRot = -m.turn() * 0.08F - roll * 0.5F;

			// the arms swing against the legs (each against the opposite foot), hanging a little out, the fists curled
			float sway = Mth.sin(age * 0.067F) * 0.03F;
			float rightSwing = ARM_REST + ARM_SWING * (this.stepLeft[0] - REST_Z) / STRIDE + sway;
			float leftSwing = ARM_REST + ARM_SWING * (this.stepRight[0] - REST_Z) / STRIDE - sway;
			boolean leftMain = sign < 0.0F;
			float rightX = leftMain ? rightSwing : rightSwing - blow * BLOW_ARM;
			float leftX = leftMain ? leftSwing - blow * BLOW_ARM : leftSwing;
			this.rightArm.xRot = emptyRight ? rightX : itemRight ? rightX * 0.5F - 0.31F : heldRight[0];
			this.leftArm.xRot = emptyLeft ? leftX : itemLeft ? leftX * 0.5F - 0.31F : heldLeft[0];
			this.rightArm.yRot = emptyRight || itemRight ? twist * 0.5F : heldRight[1];
			this.leftArm.yRot = emptyLeft || itemLeft ? twist * 0.5F : heldLeft[1];
			this.rightArm.zRot = emptyRight || itemRight ? ARM_OUT - roll : heldRight[2];
			this.leftArm.zRot = emptyLeft || itemLeft ? -ARM_OUT - roll : heldLeft[2];
			this.fistRight.xRot = this.fistLeft.xRot = FIST_CURL - 0.3F * blow;

			// the spines shiver and lift with each breath; the crown and shoulder shards stand at their lengths
			for (int i = 0; i < this.spines.length; i++) {
				ModelPart spine = this.spines[i];
				spine.xRot = SPINE_TILT - 0.03F * m.breath() - 0.05F * amp;
				spine.zRot = Mth.sin(age * 0.9F + i * 1.3F) * 0.02F - roll * 0.3F;
				spine.yScale = SPINE_LEN[i];
			}
			for (int i = 0; i < this.crown.length; i++) {
				ModelPart icicle = this.crown[i];
				float[] k = CROWN[i];
				icicle.xRot = k[2] - 0.15F * amp;
				icicle.zRot = k[3] + Mth.sin(age * 0.7F + i * 1.9F) * 0.025F;
				icicle.yScale = CROWN_LEN[i];
			}
		}

		/** (0.19.84) The fists at their rest curl for the first-person arm (the model is shared, so they hold whoever was drawn last). */
		void handPose() {
			this.fistRight.xRot = FIST_CURL;
			this.fistLeft.xRot = FIST_CURL;
		}

		/** One leg to its foot's step: the ankle ahead of the hip by the step's z, lifted by its lift (and tucked up in the air). */
		private void legTo(float hipY, float[] step, ModelPart thigh, ModelPart shin, ModelPart foot, float tuck) {
			leg(thigh, shin, foot, hipY, ANKLE_Y - this.sink - step[1] - tuck, HIP_Z + step[0], step[2]);
		}
	}

	/** (0.18.107, Scan B) Marrow: bare bone, two horns curling back off the skull, an open ribcage that breathes, a spine out its back. */
	static final class Marrow extends FormModel {
		private final ModelPart ribs;

		Marrow() {
			super(bake(mesh()), "marrow");
			this.insigniaZ = -2.6F;
			this.ribs = this.body.getChild("ribs");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 64).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(-3.0F, -7.6F, -1.0F, -0.6F, 0.0F, -0.4F));
			head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(3.0F, -7.6F, -1.0F, -0.6F, 0.0F, 0.4F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 10.0F, 2.0F),
				PartPose.offset(0.0F, 0.5F, 1.8F));
			body.addOrReplaceChild("ribs", CubeListBuilder.create().texOffs(0, 80).addBox(-4.5F, 0.0F, -2.5F, 9.0F, 7.0F, 5.0F),
				PartPose.offset(0.0F, 1.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float breathe = 1.0F + Mth.sin(state.ageInTicks * 0.1F) * 0.03F;
			this.ribs.xScale = this.ribs.zScale = breathe;
		}
	}

	/** (0.18.107, Scan B) Swathe: wrapped head to foot, a loose end trailing from each arm and a long one down its back, all swaying. */
	static final class Swathe extends FormModel {
		private final ModelPart rightEnd, leftEnd, tail;

		Swathe() {
			super(bake(mesh()), "swathe");
			this.insigniaZ = -2.25F;
			this.rightEnd = this.rightArm.getChild("loose_end");
			this.leftEnd = this.leftArm.getChild("loose_end");
			this.tail = this.body.getChild("trail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.25F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("right_arm").addOrReplaceChild("loose_end", CubeListBuilder.create().texOffs(0, 64).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 9.0F, 1.0F),
				PartPose.offset(-1.0F, 4.0F, 2.2F));
			root.getChild("left_arm").addOrReplaceChild("loose_end", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-1.5F, 0.0F, 0.0F, 3.0F, 9.0F, 1.0F),
				PartPose.offset(1.0F, 4.0F, 2.2F));
			root.getChild("body").addOrReplaceChild("trail", CubeListBuilder.create().texOffs(8, 64).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 12.0F, 1.0F),
				PartPose.offset(0.0F, 0.5F, 2.3F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float sway = Mth.sin(state.ageInTicks * 0.12F) * 0.08F;
			float drag = Math.min(0.8F, state.walkAnimationSpeed * 0.7F);
			this.rightEnd.xRot = drag + sway;
			this.leftEnd.xRot = drag - sway;
			this.tail.xRot = drag * 0.8F + sway * 0.5F;
			this.tail.zRot = sway;
		}
	}

	/**
	 * (0.18.107, Scan B; 0.19.82 a floating Wither) Blight: coal-black bone, a skull on each shoulder beside the big middle one, a ribbed tail that swings.
	 * Now the Wither, not a man: three skulls (the middle one largest) on a crossbar over a ribbed chest that tapers
	 * down into a three-joint tail, no legs. It floats, its lowest tail joint about 5 pixels off the ground, bobbing
	 * and leaning its heads into a move; the tail sways in a wave (each joint a beat behind the last), streams back
	 * as it goes, lifts as it falls and droops (never to the ground) on a landing, and the ribs breathe with
	 * their tips curled in. The middle skull looks where the head looks (turning as far as {@link #LOOK_MAX}); the side skulls look round on their own, slowly,
	 * and turn to the look when it attacks, when all three snap forward and nod into the strike. The man's parts are
	 * hidden; the hidden body carries the trunk's own pose, so the insignia plate lies on the front of the chest.
	 */
	static final class Blight extends FormModel {
		// ---- tuning ----
		/** The crossbar's height (model y; the ground is 24), the bob's size and speed (radians a tick), and how a sneak sits (pixels): the renderer sinks a crouching player by CROUCH_SINK, so it rises that back first. */
		static final float TRUNK_Y = 2.0F, BOB = 1.0F, BOB_RATE = 0.07F, SNEAK_DROP = 1.0F, CROUCH_SINK = 2.0F;
		/** The trunk's forward lean (radians): at rest, at a full walk, and thrown into an attack; how much it rises when it strikes (pixels), how far a fall flares it back. */
		static final float LEAN_REST = 0.03F, LEAN = 0.2F, LUNGE = 0.3F, LUNGE_RISE = 1.0F, FALL_FLARE = 0.15F;
		/** The trunk's bank into a turn and its idle roll (radians) and the roll's speed. */
		static final float BANK = 0.12F, SWAY = 0.04F, SWAY_RATE = 0.05F;
		/** An attack: how far all the heads thrust forward (pixels) and nod into it (radians). */
		static final float HEAD_LUNGE = 2.0F, HEAD_DIP = 0.35F;
		/** How far the middle skull may turn to the look (radians): further and its corners swing into the side skulls. */
		static final float LOOK_MAX = 0.8F;
		/** The side skulls: their x off the middle (pixels; far enough out that the turning middle skull clears them, bar a corner's brush at the extremes), how far out they look at rest, how far and how fast they wander, their idle nod, and how far they may turn to the look (radians). */
		static final float SIDE_X = 10.5F, SIDE_OUT = 0.15F, SIDE_WANDER = 0.4F, SIDE_RATE = 0.031F, SIDE_NOD = 0.12F, SIDE_FOLLOW_MAX = 0.6F;
		/** The tail: the resting bend of each joint (radians, + is back), how much of the fall/landing lift each takes, its droop and rise limits, how much it streams up at speed. */
		private static final float[] TAIL_REST = {0.34F, 0.44F, 0.54F};
		private static final float[] TAIL_FOLLOW = {1.0F, 0.8F, 0.6F};
		static final float TAIL_DROOP = 0.15F, TAIL_RISE = 0.5F, TAIL_STREAM = 0.3F;
		/** The tail's wave: each joint's side-to-side sweep (radians), its nod, how much a turn drags it, the wave's speed and the beat between joints. */
		private static final float[] TAIL_WAG = {0.18F, 0.3F, 0.42F};
		private static final float[] TAIL_NOD = {0.03F, 0.05F, 0.07F};
		private static final float[] TAIL_TRAIL = {0.25F, 0.4F, 0.55F};
		static final float TAIL_RATE = 0.07F, TAIL_LAG = 0.8F;
		/** The ribs: how wide each is, how high on the chest (pixels), how far the tips curl in (radians) and how much they breathe. */
		private static final float[] RIB_W = {14.0F, 12.0F, 10.0F};
		private static final float[] RIB_Y = {1.5F, 3.5F, 5.5F};
		static final float RIB_CURL = 0.5F, RIB_BREATH = 0.03F;

		private final ModelPart trunk, midSkull, rightSkull, leftSkull;
		private final ModelPart[] ribs = new ModelPart[3];
		private final ModelPart[] tail = new ModelPart[3];

		Blight() {
			super(bake(mesh()), "blight");
			this.trunk = this.root().getChild("trunk");
			this.midSkull = this.trunk.getChild("mid_skull");
			this.rightSkull = this.trunk.getChild("right_skull");
			this.leftSkull = this.trunk.getChild("left_skull");
			ModelPart chest = this.trunk.getChild("chest");
			for (int i = 0; i < 3; i++) {
				this.ribs[i] = chest.getChild("rib" + i);
			}
			ModelPart joint = chest;
			for (int i = 0; i < 3; i++) {
				joint = joint.getChild("tail" + i);
				this.tail[i] = joint;
			}
			// the plate lies flush on the front of the chest (its front face is at z = -2, 7 pixels tall from y 1.5)
			this.insigniaY = 5.0F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 0.95F;
		}

		private static MeshDefinition mesh() {
			// the man's slim parts, kept for the first-person hand
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.4F), 0.0F);
			PartDefinition trunk = mesh.getRoot().addOrReplaceChild("trunk", CubeListBuilder.create(), PartPose.offset(0.0F, TRUNK_Y, 0.0F));
			// the crossbar 22x3x3 and the three skulls sitting on it, their bases a pixel down in the bar; each turns about
			// its own middle (so a nod or a turn doesn't swing it into its neighbour, and a cube turned any way still
			// reaches down into the bar)
			trunk.addOrReplaceChild("bar", CubeListBuilder.create().texOffs(0, 80).addBox(-11.0F, -1.5F, -1.5F, 22.0F, 3.0F, 3.0F), PartPose.ZERO);
			trunk.addOrReplaceChild("mid_skull", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, -4.5F, 0.0F));
			trunk.addOrReplaceChild("right_skull", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(-SIDE_X, -3.5F, 0.5F));
			trunk.addOrReplaceChild("left_skull", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(SIDE_X, -3.5F, 0.5F));
			// the chest 6x7x4 under the bar, with three ribs across it (and their tips curling forward) and the tail off its foot
			PartDefinition chest = trunk.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 88).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 7.0F, 4.0F),
				PartPose.offset(0.0F, 1.5F, 0.0F));
			for (int i = 0; i < 3; i++) {
				float w = RIB_W[i];
				PartDefinition rib = chest.addOrReplaceChild("rib" + i, CubeListBuilder.create().texOffs(0, 100 + i * 4)
					.addBox(-w / 2.0F, -0.5F, -1.0F, w, 1.0F, 2.0F), PartPose.offset(0.0F, RIB_Y[i], 0.0F));
				rib.addOrReplaceChild("rtip", CubeListBuilder.create().texOffs(32, 100).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F),
					PartPose.offsetAndRotation(-w / 2.0F + 0.5F, 0.0F, -0.5F, 0.0F, -RIB_CURL, 0.0F));
				rib.addOrReplaceChild("ltip", CubeListBuilder.create().texOffs(32, 100).mirror().addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F),
					PartPose.offsetAndRotation(w / 2.0F - 0.5F, 0.0F, -0.5F, 0.0F, RIB_CURL, 0.0F));
			}
			// the tail: 3x5x3, 2x4x2, 1x4x1, each hung off the foot of the one before (half a pixel in, so they never part)
			PartDefinition t0 = chest.addOrReplaceChild("tail0", CubeListBuilder.create().texOffs(28, 104).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, 6.5F, 0.0F, TAIL_REST[0], 0.0F, 0.0F));
			PartDefinition t1 = t0.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(42, 104).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 4.5F, 0.0F, TAIL_REST[1], 0.0F, 0.0F));
			t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(52, 104).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 3.5F, 0.0F, TAIL_REST[2], 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			boolean sneak = state.isCrouching;
			// an attack: the heads snap forward and nod into it, the trunk leans and rises behind them
			float atk = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// floating: bobbing (a quick flutter of it at speed), leaning its heads into a move and never tipping back,
			// so the tail only ever swings up and the tail's tip stays clear of the ground
			float bob = Mth.sin(age * BOB_RATE) * BOB + Mth.sin(age * 0.19F) * 0.25F * speed;
			float pitch = Math.max(0.0F, LEAN_REST + speed * LEAN + atk * LUNGE + Mth.sin(age * 0.09F) * 0.02F
				- Math.max(0.0F, m.fall()) * m.air() * FALL_FLARE);
			float bank = m.turn() * BANK + Mth.sin(age * SWAY_RATE) * SWAY;
			this.trunk.y = TRUNK_Y + bob - atk * LUNGE_RISE + (sneak ? SNEAK_DROP - CROUCH_SINK : 0.0F);
			this.trunk.xRot = pitch;
			this.trunk.yRot = Mth.sin(age * 0.037F) * 0.05F;
			this.trunk.zRot = bank;
			// the hidden body takes the trunk's whole pose, so the insignia plate lies on the chest's front
			this.body.y = this.trunk.y;
			this.body.xRot = this.trunk.xRot;
			this.body.yRot = this.trunk.yRot;
			this.body.zRot = this.trunk.zRot;
			// the middle skull looks where the head looks (held level against the lean), thrusting and nodding on a strike
			float look = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -LOOK_MAX, LOOK_MAX);
			float nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.9F, 0.9F) * 0.9F - pitch * 0.7F + atk * HEAD_DIP;
			this.midSkull.yRot = look;
			this.midSkull.xRot = nod;
			this.midSkull.z = -atk * HEAD_LUNGE;
			// the side skulls look round on their own, a beat apart, and turn to the look as it attacks
			float follow = Mth.clamp(atk * 1.6F, 0.0F, 1.0F);
			float sideLook = Mth.clamp(look, -SIDE_FOLLOW_MAX, SIDE_FOLLOW_MAX);
			float rightWander = SIDE_OUT + Mth.sin(age * SIDE_RATE) * SIDE_WANDER;
			float leftWander = -SIDE_OUT + Mth.sin(age * SIDE_RATE * 0.83F + 2.1F) * SIDE_WANDER;
			this.rightSkull.yRot = Mth.lerp(follow, rightWander, sideLook);
			this.leftSkull.yRot = Mth.lerp(follow, leftWander, sideLook);
			this.rightSkull.xRot = Mth.lerp(follow, Mth.sin(age * 0.043F + 0.7F) * SIDE_NOD, nod);
			this.leftSkull.xRot = Mth.lerp(follow, Mth.sin(age * 0.039F + 3.1F) * SIDE_NOD, nod);
			this.rightSkull.z = this.leftSkull.z = 0.5F - atk * HEAD_LUNGE;
			// the ribs breathe, their tips curling a little tighter on the breath in
			float breath = m.breath();
			for (int i = 0; i < 3; i++) {
				this.ribs[i].xScale = 1.0F + breath * RIB_BREATH;
				this.ribs[i].getChild("rtip").yRot = -(RIB_CURL + breath * 0.05F);
				this.ribs[i].getChild("ltip").yRot = RIB_CURL + breath * 0.05F;
			}
			// the tail: a wave down its joints, each a beat behind, dragged by a turn, streaming up at speed, lifting as it
			// falls and drooping (no further than TAIL_DROOP) on a landing or a sneak
			float lift = Mth.clamp(tailLift(m), -TAIL_DROOP, TAIL_RISE);
			for (int i = 0; i < 3; i++) {
				float wave = Mth.sin(age * TAIL_RATE - i * TAIL_LAG);
				this.tail[i].xRot = TAIL_REST[i] + lift * TAIL_FOLLOW[i] + speed * TAIL_STREAM * (i + 1) / 3.0F + wave * TAIL_NOD[i];
				this.tail[i].yRot = wave * TAIL_WAG[i] * (1.0F + speed) - m.trail() * TAIL_TRAIL[i];
			}
		}
	}

	/**
	 * (0.18.109, Scan C; 0.19.62 a four-legged wolf) Howl: a wolf's muzzle and tall ears, a bushy tail that swings as it
	 * runs. Now a wolf, not a man: a long body level with the ground (a deep chest and mane in front, a lean rump
	 * behind) on four legs in a trot, diagonal pairs together, the spine flexing a little and the body bobbing with each
	 * stride. The head, with its muzzle, jaw, ruff and tall ears, turns where it looks; the bushy two-part tail hangs
	 * low and lifts and streams when it runs, flares as it falls and whips down on landing. A leap stretches it out,
	 * front legs reaching, hind legs trailing, nose following the arc. Crouched it is a low stalk (legs splayed, head
	 * and tail down, ears flat); an attack is a snapping bite: the head lunges, the jaw gapes and snaps shut. The man's
	 * parts stay, hidden, but the front legs are the player's own legs reshaped; the hidden body lies on its back
	 * under the insignia so the plate sits flat on the rump. The neck stays level in the shoulders as the head nods.
	 */
	static final class Howl extends FormModel {
		// ---- tuning ----
		/** The torso's centre height (model y), how far the leg's foot hangs below its hip, and the foot's half depth. */
		static final float TORSO_Y = 10.5F, LEG_LENGTH = 10.0F, FOOT_HALF = 1.5F;
		/** Where the legs sit: half the gap between a pair (x) and the front and hind hips (z, torso space). */
		static final float LEG_X = 2.0F, FRONT_Z = -3.5F, HIND_Z = 5.0F;
		/** How fast the legs step against the walk, how far they swing (radians), how high a swinging foot lifts, the body bob. */
		static final float GAIT = 0.6662F * 1.25F, SWING = 0.55F, LIFT = 1.4F, BOB = 0.7F;
		/** The spine's side-to-side flex in a trot, and how far it bends into a turn (radians). */
		static final float FLEX = 0.06F, TURN_BEND = 0.12F;
		/** Crouched (the stalk): how far the body drops, how far the legs splay, how much of the stride is left, the nose-down tilt. */
		static final float CROUCH_DROP = 3.0F, SPLAY = 0.55F, CROUCH_STRIDE = 0.4F, STALK_PITCH = 0.12F, STALK_HEAD = 1.2F;
		/** The renderer draws a sneaking player this many pixels lower (AvatarRenderer.getRenderOffset): the paws are planted that much higher to stay on the ground. */
		static final float CROUCH_SINK = 2.0F;
		/** A leap: how far the front legs reach going up and coming down, how far the hind trail, and the tilt with the arc. */
		static final float LEAP_REACH = 0.9F, LAND_REACH = 0.35F, TRAIL_REACH = 0.8F, LEAP_PITCH = 0.35F;
		/** A landing squashes the body down this far (pixels). */
		static final float LAND_DROP = 1.5F;
		/** A bite: the head lunges this far (pixels), the jaw gapes this wide (radians) and has shut again by SNAP_END; the front paws reach. */
		static final float LUNGE = 2.0F, JAW_OPEN = 0.8F, OPEN_AT = 0.4F, SNAP_END = 0.6F, BITE_REACH = 0.25F;
		/** The tail: how far it hangs (radians below straight back), how far its tip curls, and how far a run lifts it. */
		static final float TAIL_HANG = 0.9F, TIP_CURL = 0.3F, TAIL_RUN = 0.6F;

		private final ModelPart hindRight, hindLeft, torso, chest, tail, tailTip, jaw, neck, rightEar, leftEar;

		Howl() {
			super(bake(mesh()), "howl");
			// the plate sits on the top of the rump (the hidden body lies on its back)
			this.insigniaY = -3.5F;
			this.insigniaZ = -3.5F;
			this.insigniaSize = 1.0F;
			ModelPart root = this.root();
			this.hindRight = root.getChild("hind_right_leg");
			this.hindLeft = root.getChild("hind_left_leg");
			this.torso = root.getChild("torso");
			this.chest = this.torso.getChild("chest");
			this.tail = this.torso.getChild("tail");
			this.tailTip = this.tail.getChild("tail_tip");
			this.jaw = this.head.getChild("jaw");
			this.neck = this.head.getChild("neck");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: a skull with a muzzle and a jaw, a ruff of cheek fur, a short neck, tall ears
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -5.0F, 6.0F, 6.0F, 5.0F),
				PartPose.offset(0.0F, 9.5F, -6.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(32, 64).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 3.0F, 4.0F),
				PartPose.offset(0.0F, -0.5F, -5.0F));
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(32, 72).addBox(-1.5F, 0.0F, -4.0F, 3.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 2.4F, -5.0F));
			head.addOrReplaceChild("ruff", CubeListBuilder.create().texOffs(0, 96).addBox(-3.5F, -3.5F, -2.0F, 7.0F, 7.0F, 3.0F), PartPose.ZERO);
			head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(20, 96).addBox(-2.0F, -2.5F, 0.0F, 4.0F, 5.0F, 5.0F), PartPose.ZERO);
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(40, 96).addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(-2.0F, -2.8F, -1.5F, 0.0F, 0.0F, -0.18F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 96).mirror().addBox(-1.0F, -4.0F, -0.5F, 2.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(2.0F, -2.8F, -1.5F, 0.0F, 0.0F, 0.18F));
			// the wolf's body: a lean rump with a deeper chest and mane in front, a bushy tail off the back
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -3.5F, -1.0F, 7.0F, 7.0F, 9.0F),
				PartPose.offset(0.0F, TORSO_Y, 0.0F));
			torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 80).addBox(-4.0F, -4.5F, -6.0F, 8.0F, 8.0F, 7.0F),
				PartPose.offset(0.0F, 0.0F, -1.0F));
			PartDefinition tail = torso.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 64).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, 7.5F, -TAIL_HANG, 0.0F, 0.0F));
			tail.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(36, 80).addBox(-2.0F, -2.0F, -0.5F, 4.0F, 4.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 4.5F, -TIP_CURL, 0.0F, 0.0F));
			// the front legs are the man's legs (so the pose code still finds them), the hind pair new; each reaches well up into the body
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(-LEG_X, 14.0F, FRONT_Z));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(LEG_X, 14.0F, FRONT_Z));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(-LEG_X, 14.0F, HIND_Z));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 13.0F, 3.0F),
				PartPose.offset(LEG_X, 14.0F, HIND_Z));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.body.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft,
				this.torso, this.chest, this.tail, this.tailTip, this.jaw, this.neck, this.rightEar, this.leftEar}) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float t = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			float fall = Mth.clamp(m.fall(), -1.0F, 1.0F);
			float land = Math.max(0.0F, m.land());
			boolean crouch = state.isCrouching;
			// the ground, in model y: a sneaking player is drawn CROUCH_SINK lower, so the stalk stands that much higher
			float ground = 24.0F - (crouch ? CROUCH_SINK : 0.0F);
			float phase = state.walkAnimationPos * GAIT;
			float run = speed * (1.0F - air);
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			// a bite: the head lunges, the jaw gapes and snaps shut
			float lunge = Mth.sin(swing * Mth.PI);
			float open = swing < OPEN_AT ? Mth.sin(swing / OPEN_AT * Mth.HALF_PI)
				: Math.max(0.0F, 1.0F - (swing - OPEN_AT) / (SNAP_END - OPEN_AT));

			// ---- the body: level, bobbing with the stride, tipped with a leap, dropped low when stalking ----
			float pitch = air * fall * LEAP_PITCH + (crouch ? STALK_PITCH : 0.0F) + land * 0.1F;
			float torsoY = TORSO_Y + (crouch ? CROUCH_DROP - CROUCH_SINK : 0.0F) + Mth.cos(phase * 2.0F) * BOB * run + land * LAND_DROP;
			float cosP = Mth.cos(pitch);
			float sinP = Mth.sin(pitch);
			this.torso.y = torsoY;
			this.torso.xRot = pitch;
			float chestYaw = Mth.sin(phase) * FLEX * run - m.turn() * TURN_BEND;
			this.chest.yRot = chestYaw;
			this.chest.yScale = 1.0F + m.breath() * 0.02F;
			// the hidden body lies on its back under the insignia
			this.body.y = torsoY;
			this.body.xRot = -Mth.HALF_PI + pitch;

			// ---- the legs: a trot (front right with hind left, front left with hind right), planted flat ----
			float stride = Mth.cos(phase) * SWING * run * (crouch ? CROUCH_STRIDE : 1.0F);
			float splay = crouch ? SPLAY : 0.0F;
			float reach = BITE_REACH * lunge * (1.0F - air);
			float lift = LIFT * run * (crouch ? 0.6F : 1.0F);
			float liftA = lift * Math.max(0.0F, Mth.sin(phase));
			float liftB = lift * Math.max(0.0F, -Mth.sin(phase));
			// a leap: the front legs reach (less on the way down), the hind trail behind
			float reachUp = LEAP_REACH + (LAND_REACH - LEAP_REACH) * Math.max(0.0F, fall);
			float airFront = -reachUp;
			float airHind = TRAIL_REACH * (1.0F - 0.5F * Math.max(0.0F, fall));
			float frontRight = airFront * air + (1.0F - air) * (-splay - reach + stride);
			float frontLeft = airFront * air + (1.0F - air) * (-splay - reach - stride);
			float hindRightA = airHind * air + (1.0F - air) * (splay - stride);
			float hindLeftA = airHind * air + (1.0F - air) * (splay + stride);
			this.leg(this.rightLeg, -1.0F, FRONT_Z, frontRight, liftA, ground, torsoY, cosP, sinP, chestYaw, air, true);
			this.leg(this.leftLeg, 1.0F, FRONT_Z, frontLeft, liftB, ground, torsoY, cosP, sinP, chestYaw, air, true);
			this.leg(this.hindRight, -1.0F, HIND_Z, hindRightA, liftB, ground, torsoY, cosP, sinP, chestYaw, air, false);
			this.leg(this.hindLeft, 1.0F, HIND_Z, hindLeftA, liftA, ground, torsoY, cosP, sinP, chestYaw, air, false);

			// ---- the head: at the front of the chest, turning where it looks ----
			float relY = -1.0F;
			float relZ = -5.0F - lunge * LUNGE;
			float hx = relZ * Mth.sin(chestYaw);
			float hz = relZ * Mth.cos(chestYaw) - 1.0F;
			float headDrop = crouch ? STALK_HEAD : 0.0F;
			this.head.x = hx;
			this.head.y = torsoY + relY * cosP - hz * sinP + headDrop;
			this.head.z = relY * sinP + hz * cosP;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.0F, 1.0F);
			// sniffing now and then when it stands about
			float sniff = Math.max(0.0F, Mth.sin(t * 0.045F));
			sniff = sniff * sniff;
			sniff = sniff * sniff * sniff * sniff * (1.0F - speed) * (crouch ? 0.0F : 1.0F);
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD - pitch * 0.6F + (crouch ? 0.12F : 0.0F) + lunge * 0.3F + sniff * 0.25F
				+ Mth.cos(phase * 2.0F - 0.6F) * 0.05F * run;
			this.head.zRot = -m.turn() * 0.12F;
			// the neck stays level in the shoulders however the head nods (it only turns with it)
			this.neck.xRot = -this.head.xRot;
			float pant = (Mth.sin(t * 0.9F) * 0.5F + 0.5F) * 0.1F * speed;
			this.jaw.xRot = open * JAW_OPEN + pant;

			// ---- the ears: up and alert, flattening back when it runs, stalks or bites, each twitching on its own ----
			float back = Math.min(1.0F, (crouch ? 0.7F : 0.0F) + speed * 0.4F + lunge * 0.6F + open * 0.3F);
			float twitchR = Math.max(0.0F, Mth.sin(t * 0.07F + 1.3F));
			twitchR *= twitchR;
			twitchR *= twitchR;
			twitchR *= twitchR;
			float twitchL = Math.max(0.0F, Mth.sin(t * 0.057F));
			twitchL *= twitchL;
			twitchL *= twitchL;
			twitchL *= twitchL;
			this.rightEar.xRot = 0.1F - back * 0.9F + twitchR * 0.35F;
			this.rightEar.zRot = -0.18F - back * 0.35F - twitchR * 0.2F;
			this.leftEar.xRot = 0.1F - back * 0.9F + twitchL * 0.35F;
			this.leftEar.zRot = 0.18F + back * 0.35F + twitchL * 0.2F;

			// ---- the tail: hangs low, streams out when it runs, flares as it falls, whips down on landing ----
			this.tail.xRot = -TAIL_HANG + TAIL_RUN * run + tailLift(m) + Mth.sin(phase * 2.0F) * 0.05F * run;
			this.tail.yRot = Mth.sin(t * 0.09F) * 0.12F * (1.0F - run) + Mth.sin(phase) * 0.3F * run - m.trail() * 0.5F - m.turn() * 0.2F;
			this.tailTip.xRot = -TIP_CURL + m.breath() * 0.05F + m.trail() * 0.1F;
			this.tailTip.yRot = Mth.sin(t * 0.09F - 0.8F) * 0.2F * (1.0F - run) + Mth.sin(phase - 0.9F) * 0.25F * run - m.trail() * 0.35F;
		}

		/**
		 * One leg: swung to {@code theta} (+ is the foot back) and its hip set so the lowest corner of the foot is on {@code ground}
		 * (less {@code lift}; hanging from the torso's underside in the air), the hip kept under the torso whatever the torso's tilt and the chest's flex.
		 */
		private void leg(ModelPart leg, float side, float zTorso, float theta, float lift, float ground, float torsoY, float cosP, float sinP,
			float chestYaw, float air, boolean front) {
			float planted = ground - LEG_LENGTH * Mth.cos(theta) - FOOT_HALF * Mth.abs(Mth.sin(theta)) - lift;
			float hip = planted + (Math.min(planted, torsoY + 3.5F) - planted) * air;
			leg.x = side * LEG_X + (front ? (zTorso + 1.0F) * Mth.sin(chestYaw) : 0.0F);
			leg.y = hip;
			leg.z = zTorso * cosP + (hip - torsoY) * sinP;
			leg.xRot = theta;
		}
	}

	/**
	 * (0.18.109, Scan C; 0.19.62 a cat on four legs) Prowl: a big cat, long and low: a chest and a haunch that flex
	 * about the spine, a round-eared head, four two-jointed legs on paws that stay flat on the ground, walking in a
	 * cat's lateral order and falling into a bounding gallop at speed (the spine arching, forepaws together, hindpaws
	 * together), and a long tail that droops, curls up at the tip and twitches. Sneaking is a stalk: belly low, paws
	 * tucked under, shoulders down, ears back, slow high-lifted steps; a swing is a forepaw rake; a leap stretches it
	 * out and a landing crouches it. In the cat the man's parts are hidden and the (hidden) body is laid on the chest's
	 * back, so the insignia sits between the shoulders.
	 */
	static final class Prowl extends FormModel {
		// ---- tuning ----
		/** The leg bones (model pixels): the shoulder's upper arm and forearm, the hip's thigh and shin (to the ankle). */
		static final float FRONT_UPPER = 4.0F, FRONT_LOWER = 4.0F, HIND_UPPER = 5.0F, HIND_LOWER = 4.0F;
		/** Where the legs hang from: how far below the spine, and how far along it (shoulders forward, hips back); sideways. */
		static final float HIP_DROP = 1.5F, SHOULDER_Z = -5.5F, HIP_Z = 5.5F, LEG_X = 2.0F;
		/** How far the ankle sits above the ground (the paw's height under it), so the ankle's ground line. */
		static final float PAW_DROP = 1.5F, GROUND = 24.0F - PAW_DROP;
		/** How far the hips stand above the ankles at rest, so the spine's height, and where the hind paw rests (behind the hip). */
		static final float REST_REACH = 6.8F, HIND_REST_Z = 6.5F;
		static final float SPINE_Y = GROUND - REST_REACH - HIP_DROP;
		/** The gait: the cycle's speed against the walk, and the speeds the legs start (full steps at STEP_FULL) and a gallop builds (GALLOP_FROM to GALLOP_FULL). */
		static final float GAIT = 0.54F, STEP_FULL = 0.25F, GALLOP_FROM = 0.8F, GALLOP_FULL = 1.0F;
		/** The part of a step a paw is down (walk, stalk, gallop), how far a paw reaches fore and aft (half the stride) and how high it lifts. */
		static final float WALK_DUTY = 0.62F, STALK_DUTY = 0.72F, GALLOP_DUTY = 0.4F;
		static final float WALK_STRIDE = 3.0F, STALK_STRIDE = 1.6F, GALLOP_STRIDE = 3.8F, HIND_STRIDE = 1.1F;
		static final float WALK_LIFT = 2.4F, STALK_LIFT = 2.2F, GALLOP_LIFT = 3.2F;
		/** Each paw's place in the cycle (right hind, right fore, left hind, left fore): a walk is lateral (hind, then fore on its side), a gallop pairs them. */
		private static final float[] WALK_PHASE = {0.0F, 0.25F, 0.5F, 0.75F};
		private static final float[] GALLOP_PHASE = {0.5F, 0.0F, 0.6F, 0.1F};
		/** The stalk: how far the belly drops, the chest tips nose-down, the haunch stays higher (relative), the head dips, the ears go back. */
		static final float STALK_DROP = 3.0F, STALK_PITCH = 0.16F, STALK_HAUNCH = -0.12F, STALK_HEAD = 0.2F, STALK_EARS = -0.35F;
		/** The stalk's paws tucked under the belly (so the folded legs clear the ground): the forepaws back, the hindpaws forward. */
		static final float STALK_FORE_Z = 1.5F, STALK_HIND_Z = -3.0F;
		/** Stalking, the lowest the head may look (radians against the world), so the nose never digs in. */
		static final float STALK_LOOK_DOWN = 0.5F;
		/** The gallop's spine flex (radians), its phase, and the body's dip a step (walk, gallop). */
		static final float FLEX = 0.14F, FLEX_PHASE = -0.6F, WALK_BOB = 0.5F, GALLOP_BOB = 0.9F;
		/** A landing squats the body this much (pixels; little when already stalking, less on the move); a fall pitches the nose this much. */
		static final float LAND_SQUAT = 2.5F, STALK_LAND_SQUAT = 0.5F, LAND_MOVING = 0.65F, FALL_PITCH = 0.35F;
		/** A leap: where the forepaws reach (forward, up) and the hindpaws trail (back, up). */
		static final float AIR_FORE_Z = -4.0F, AIR_FORE_UP = 5.0F, AIR_HIND_Z = 4.5F, AIR_HIND_UP = 3.5F;
		/** The rake: how high the right forepaw rises, how far it reaches forward, how far it drags back, how far the chest rears (and rises, from a stalk), how far the paw tips its pads forward, and the least the leg stretches out. */
		static final float RAKE_UP = 7.0F, RAKE_REACH = 5.0F, RAKE_DRAG = 4.0F, RAKE_REAR = 0.15F, STALK_RAKE_RISE = 2.0F, RAKE_CURL = 0.9F, RAKE_MIN_REACH = 6.0F;
		/** The tail: how it hangs (radians, + is up), its curl at the tip, how far a gallop lifts it, how strongly the fall lifts it. */
		static final float TAIL_HANG = -0.45F, TAIL_CURL_MID = 0.25F, TAIL_CURL_TIP = 0.9F, TAIL_GALLOP = 0.6F, TAIL_FALL = 0.6F;
		/** The lowest the tail's root may hang (radians), walking and stalking. */
		static final float TAIL_MIN = -0.75F, TAIL_MIN_STALK = -0.6F;
		/** How far the head can turn and tip against the chest. */
		static final float HEAD_YAW = 1.2F, HEAD_PITCH = 0.9F;
		/** Where along the chest's back (from its rear) the insignia plate sits. */
		static final float INSIGNIA_AT = 4.5F;
		private static final float TAU = (float)Math.PI * 2.0F;

		private final ModelPart chest, haunch, catHead, muzzle, rightEar, leftEar, tail1, tail2, tail3;
		/** Right hind, right fore, left hind, left fore. */
		private final ModelPart[] upper = new ModelPart[4];
		private final ModelPart[] lower = new ModelPart[4];
		private final ModelPart[] paw = new ModelPart[4];
		private final ModelPart[] moving;
		/** Scratch for the leg solver: the thigh's and shin's angles. */
		private final float[] angles = new float[2];

		Prowl() {
			super(bake(mesh()), "prowl");
			// the plate sits on the chest's back, between the shoulders (the body is laid face-up on it, see setupAnim)
			this.insigniaY = 0.0F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.8F;
			ModelPart root = this.root();
			this.chest = root.getChild("chest");
			this.haunch = this.chest.getChild("haunch");
			this.catHead = this.chest.getChild("cat_head");
			this.muzzle = this.catHead.getChild("muzzle");
			this.rightEar = this.catHead.getChild("right_ear");
			this.leftEar = this.catHead.getChild("left_ear");
			this.tail1 = this.haunch.getChild("tail1");
			this.tail2 = this.tail1.getChild("tail2");
			this.tail3 = this.tail2.getChild("tail3");
			String[] names = {"hind_right", "fore_right", "hind_left", "fore_left"};
			for (int i = 0; i < 4; i++) {
				this.upper[i] = (i % 2 == 0 ? this.haunch : this.chest).getChild(names[i] + "_upper");
				this.lower[i] = this.upper[i].getChild(names[i] + "_lower");
				this.paw[i] = this.lower[i].getChild(names[i] + "_paw");
			}
			ModelPart[] all = new ModelPart[21];
			int n = 0;
			for (ModelPart part : new ModelPart[] {this.chest, this.haunch, this.catHead, this.muzzle, this.rightEar, this.leftEar, this.tail1, this.tail2, this.tail3}) {
				all[n++] = part;
			}
			for (int i = 0; i < 4; i++) {
				all[n++] = this.upper[i];
				all[n++] = this.lower[i];
				all[n++] = this.paw[i];
			}
			this.moving = all;
		}

		/**
		 * A leg: the thigh or upper arm hung at the hip (a pixel of it above the pivot, to sit in the body), the shin below
		 * it to the ankle, and a paw on the ankle that is kept level with the ground (see setupAnim).
		 */
		private static void leg(PartDefinition parent, String name, boolean hind, boolean left, float x, float z, int upperU, int upperV, int lowerU, int lowerV, int pawU, int pawV) {
			float upperLength = hind ? HIND_UPPER : FRONT_UPPER;
			float lowerLength = hind ? HIND_LOWER : FRONT_LOWER;
			CubeListBuilder thigh = CubeListBuilder.create().texOffs(upperU, upperV);
			if (hind) {
				thigh.addBox(-2.0F, -1.0F, -2.0F, 4.0F, 6.0F, 4.0F);
			} else {
				thigh.addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F);
			}
			PartDefinition upper = parent.addOrReplaceChild(name + "_upper", thigh, PartPose.offset(left ? x : -x, HIP_DROP, z));
			// the shin is a hair narrower than the thigh so they never fight for the same pixels
			CubeListBuilder shin = CubeListBuilder.create().texOffs(lowerU, lowerV).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 5.0F, 3.0F, new CubeDeformation(-0.1F, 0.0F, -0.1F));
			PartDefinition lower = upper.addOrReplaceChild(name + "_lower", shin, PartPose.offset(0.0F, upperLength, 0.0F));
			// the paw: its top half a pixel up the shin (covering the shin's end however it tilts), its bottom PAW_DROP below the ankle, toes forward
			lower.addOrReplaceChild(name + "_paw", CubeListBuilder.create().texOffs(pawU, pawV).addBox(-1.5F, -0.5F, -2.0F, 3.0F, 2.0F, 3.0F),
				PartPose.offset(0.0F, lowerLength, 0.0F));
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
			PartDefinition root = mesh.getRoot();
			// the man's parts stay (hidden) for the poses and the first-person hand to find
			PartDefinition chest = root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.0F, -8.0F, 6.0F, 6.0F, 8.0F),
				PartPose.offset(0.0F, SPINE_Y, 0.0F));
			// the haunch runs a pixel into the chest (and is a hair slimmer) so the spine never opens a gap when it flexes
			PartDefinition haunch = chest.addOrReplaceChild("haunch", CubeListBuilder.create().texOffs(28, 64).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 6.0F, 9.0F, new CubeDeformation(-0.15F)),
				PartPose.ZERO);
			PartDefinition head = chest.addOrReplaceChild("cat_head", CubeListBuilder.create().texOffs(0, 80).addBox(-3.0F, -3.0F, -4.5F, 6.0F, 5.0F, 5.0F),
				PartPose.offset(0.0F, -1.0F, -7.5F));
			head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(22, 80).addBox(-1.5F, -0.5F, -2.0F, 3.0F, 2.0F, 2.0F),
				PartPose.offset(0.0F, 0.0F, -4.5F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(32, 80).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(-2.0F, -3.0F, -1.5F, 0.0F, 0.0F, -0.2F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(38, 80).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(2.0F, -3.0F, -1.5F, 0.0F, 0.0F, 0.2F));
			// the tail: a stout root, a tapering middle, a tip that curls up
			CubeDeformation taper = new CubeDeformation(-0.1F, -0.1F, 0.0F);
			PartDefinition t1 = haunch.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(44, 80).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, -1.0F, 7.5F, TAIL_HANG, 0.0F, 0.0F));
			PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(0, 92).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F, taper),
				PartPose.offset(0.0F, 0.0F, 5.5F));
			t2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(16, 92).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F, taper),
				PartPose.offset(0.0F, 0.0F, 5.5F));
			// legs, the right ones at -x: forelegs off the chest, hind legs off the haunch
			leg(haunch, "hind_right", true, false, LEG_X, HIP_Z, 0, 110, 44, 100, 32, 110);
			leg(chest, "fore_right", false, false, LEG_X, SHOULDER_Z, 28, 92, 0, 100, 32, 116);
			leg(haunch, "hind_left", true, true, LEG_X, HIP_Z, 16, 110, 0, 120, 44, 110);
			leg(chest, "fore_left", false, true, LEG_X, SHOULDER_Z, 40, 92, 12, 100, 44, 116);
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.rightArm.visible = this.leftArm.visible = false;
			this.rightLeg.visible = this.leftLeg.visible = this.body.visible = false;
			this.body.resetPose();
			for (ModelPart part : this.moving) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			boolean stalk = state.isCrouching;
			float amp = Mth.clamp(speed / STEP_FULL, 0.0F, 1.0F);
			float gallop = stalk ? 0.0F : smooth(GALLOP_FROM, GALLOP_FULL, speed);
			float cycle = state.walkAnimationPos * GAIT / TAU;
			float ph = cycle * TAU;
			float duty = stalk ? STALK_DUTY : Mth.lerp(gallop, WALK_DUTY, GALLOP_DUTY);
			float stride = (stalk ? STALK_STRIDE : Mth.lerp(gallop, WALK_STRIDE, GALLOP_STRIDE)) * amp;
			float lift = (stalk ? STALK_LIFT : Mth.lerp(gallop, WALK_LIFT, GALLOP_LIFT)) * amp;
			float air = m.air();
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float sw = Mth.sin(swing * (float)Math.PI);

			// the spine: the gallop arches it (chest and haunch bow against each other), the stalk tips it nose-down
			float flex = FLEX * gallop * amp * Mth.sin(ph + FLEX_PHASE);
			float airPitch = m.fall() * air * FALL_PITCH;
			float chestPitch = (stalk ? STALK_PITCH : 0.0F) + flex + airPitch - sw * RAKE_REAR;
			float haunchPitch = (stalk ? STALK_HAUNCH : 0.0F) - flex * 0.9F - airPitch * 0.5F;
			float haunchTotal = chestPitch + haunchPitch;
			// the body drops (never rises) a little each step, more in a gallop, and squats on landing (less on the move, the legs already bent)
			float bob = amp * ((1.0F - gallop) * WALK_BOB * (0.5F - 0.5F * Mth.cos(4.0F * ph))
				+ gallop * GALLOP_BOB * (0.5F - 0.5F * Mth.cos(2.0F * ph + 1.0F)));
			float squat = m.land() * (stalk ? STALK_LAND_SQUAT : LAND_SQUAT) * (1.0F - LAND_MOVING * amp);
			float spineY = SPINE_Y + (stalk ? STALK_DROP - sw * STALK_RAKE_RISE : 0.0F) + bob + squat;
			float breath = m.breath() * 0.12F * (1.0F - amp);
			this.chest.y = spineY + breath;
			this.chest.xRot = chestPitch;
			this.haunch.xRot = haunchPitch;
			this.haunch.yRot = Mth.sin(ph) * 0.06F * amp * (1.0F - gallop);

			// the body, hidden, lies face-up on the chest so the insignia plate sits on its back
			float plateZ = -INSIGNIA_AT;
			this.body.x = 0.0F;
			this.body.y = spineY + breath - plateZ * Mth.sin(chestPitch);
			this.body.z = plateZ * Mth.cos(chestPitch);
			this.body.xRot = chestPitch - (float)Math.PI * 0.5F;

			// the legs: each ankle follows its own path; two-bone solving keeps every joint attached and the paw on the ground
			for (int i = 0; i < 4; i++) {
				boolean hind = i % 2 == 0;
				float theta = hind ? haunchTotal : chestPitch;
				float hipZ = hind ? HIP_Z : SHOULDER_Z;
				float restZ = (hind ? HIND_REST_Z : SHOULDER_Z) + (stalk ? (hind ? STALK_HIND_Z : STALK_FORE_Z) : 0.0F);
				float phase = Mth.lerp(gallop, WALK_PHASE[i], GALLOP_PHASE[i]) + cycle;
				float[] foot = pawPath(phase - (float)Math.floor(phase), duty, stride * (hind ? HIND_STRIDE : 1.0F), lift);
				float targetZ = restZ + foot[0];
				float targetY = GROUND - foot[1];
				if (air > 0.0F) {
					float reach = Mth.clamp(m.fall(), 0.0F, 1.0F);
					float airZ = hind ? restZ + AIR_HIND_Z : restZ + AIR_FORE_Z;
					float airY = GROUND - (hind ? AIR_HIND_UP : Mth.lerp(reach, AIR_FORE_UP, 1.5F));
					targetZ = Mth.lerp(air, targetZ, airZ);
					targetY = Mth.lerp(air, targetY, airY);
				}
				float hipY = spineY + breath + HIP_DROP * Mth.cos(theta) - hipZ * Mth.sin(theta);
				float hipWorldZ = HIP_DROP * Mth.sin(theta) + hipZ * Mth.cos(theta);
				boolean rake = i == 1 && sw > 0.0F;
				if (rake) {
					// the rake: the right forepaw comes up and forward, the leg stretched out, then drags back down
					float w = Mth.clamp(sw * 3.0F, 0.0F, 1.0F);
					float rakeZ = restZ - RAKE_REACH * sw + RAKE_DRAG * (swing - 0.4F) * sw;
					float rakeY = GROUND - RAKE_UP * sw;
					// swung about the shoulder (angle and reach blended, not the points), so the leg never folds up on the way:
					// folded close, the elbow would dig into the ground (a stalk's low shoulders); it reaches out instead
					float fromAngle = (float)Math.atan2(targetZ - hipWorldZ, targetY - hipY);
					float fromReach = (float)Math.sqrt((targetZ - hipWorldZ) * (targetZ - hipWorldZ) + (targetY - hipY) * (targetY - hipY));
					float toAngle = (float)Math.atan2(rakeZ - hipWorldZ, rakeY - hipY);
					float toReach = Math.max(RAKE_MIN_REACH, (float)Math.sqrt((rakeZ - hipWorldZ) * (rakeZ - hipWorldZ) + (rakeY - hipY) * (rakeY - hipY)));
					float angle = Mth.lerp(w, fromAngle, toAngle);
					float reach = Mth.lerp(w, fromReach, toReach);
					targetZ = hipWorldZ + reach * Mth.sin(angle);
					targetY = Math.min(GROUND, hipY + reach * Mth.cos(angle));
				}
				float a = hind ? HIND_UPPER : FRONT_UPPER;
				float b = hind ? HIND_LOWER : FRONT_LOWER;
				// the hind knee points forward, the fore elbow back
				solve(hipWorldZ, hipY, targetZ, targetY, a, b, hind ? -1.0F : 1.0F, this.angles);
				this.upper[i].xRot = this.angles[0] - theta;
				this.lower[i].xRot = this.angles[1] - this.angles[0];
				// the paw stays level with the ground; the raking one, once off it, tips its pads forward
				float curl = rake ? sw * RAKE_CURL * Mth.clamp((GROUND - targetY) / PAW_DROP, 0.0F, 1.0F) : 0.0F;
				this.paw[i].xRot = -this.angles[1] - curl;
			}

			// the head: looks where the player looks, level with the world whatever the chest does
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -HEAD_PITCH, HEAD_PITCH);
			float nod = Mth.sin(ph * 2.0F - 0.8F) * 0.05F * amp;
			float headPitch = Mth.clamp(look - chestPitch + (stalk ? STALK_HEAD : 0.0F) + nod - sw * 0.1F, -HEAD_PITCH, HEAD_PITCH);
			if (stalk) {
				// the stalk carries the head low already: looking down any further would put the nose in the ground
				headPitch = Math.min(headPitch, STALK_LOOK_DOWN - chestPitch);
			}
			this.catHead.xRot = headPitch;
			this.catHead.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -HEAD_YAW, HEAD_YAW);
			this.catHead.zRot = m.turn() * 0.1F;
			this.catHead.y -= breath * 0.5F;
			// the ears: flat back when stalking, a flick now and then
			float flick = Mth.sin(age * 0.37F) * Math.max(0.0F, Mth.sin(age * 0.05F + 1.0F)) * 0.25F;
			float back = stalk ? STALK_EARS : 0.0F;
			this.rightEar.xRot = back + flick;
			this.leftEar.xRot = back - flick * 0.7F;
			this.rightEar.zRot += stalk ? -0.25F : 0.0F;
			this.leftEar.zRot += stalk ? 0.25F : 0.0F;

			// the tail: hangs and curls at the tip, trails turns, streams out in a gallop, whips on landing, the tip twitching
			float hang = TAIL_HANG + gallop * amp * TAIL_GALLOP + tailLift(m) * TAIL_FALL + Mth.sin(ph * 2.0F) * 0.05F * amp;
			// never so low the tip digs into the ground (a stalk carries it low already)
			this.tail1.xRot = Math.max(hang, stalk ? TAIL_MIN_STALK : TAIL_MIN);
			float curl = 1.0F - gallop * amp * 0.7F;
			this.tail2.xRot = TAIL_CURL_MID * curl;
			this.tail3.xRot = TAIL_CURL_TIP * curl + Mth.sin(age * 0.11F) * 0.06F;
			float sway = Mth.sin(age * 0.07F) * 0.2F;
			this.tail1.yRot = -m.trail() * 0.5F + sway;
			this.tail2.yRot = -m.trail() * 0.35F + Mth.sin(age * 0.07F - 0.8F) * 0.25F;
			float gate = Math.max(0.0F, Mth.sin(age * 0.043F));
			this.tail3.yRot = Mth.sin(age * 0.9F) * gate * gate * gate * 0.5F + Mth.sin(age * 0.07F - 1.6F) * 0.2F;
		}

		/** 0 below {@code from}, 1 above {@code to}, eased between. */
		private static float smooth(float from, float to, float x) {
			float t = Mth.clamp((x - from) / (to - from), 0.0F, 1.0F);
			return t * t * (3.0F - 2.0F * t);
		}

		/**
		 * An ankle's path against the body for a point in the cycle: on the ground it slides back at a steady pace, then
		 * it swings forward in an arc. Returns {fore-aft offset (+ is back), height off the ground}.
		 */
		private float[] pawPath(float p, float duty, float stride, float lift) {
			if (p < duty) {
				return new float[] {-stride + 2.0F * stride * (p / duty), 0.0F};
			}
			float v = (p - duty) / (1.0F - duty);
			return new float[] {stride * Mth.cos((float)Math.PI * v), lift * Mth.sin((float)Math.PI * v)};
		}

		/**
		 * Two bones from a hip to an ankle in the side plane (z along the body, y down): the thigh's and the shin's angles,
		 * as rotations about x from straight down (+ swings the foot back). {@code bend} is +1 when the middle joint points
		 * back, -1 when it points forward. A target out of reach is stretched to the nearest reachable point.
		 */
		private static void solve(float hipZ, float hipY, float targetZ, float targetY, float upperLength, float lowerLength, float bend, float[] out) {
			float dz = targetZ - hipZ;
			float dy = targetY - hipY;
			float dist = Mth.clamp((float)Math.sqrt(dz * dz + dy * dy), Math.abs(upperLength - lowerLength) + 0.05F, upperLength + lowerLength - 0.01F);
			float toTarget = (float)Math.atan2(dz, dy);
			float cos = Mth.clamp((upperLength * upperLength + dist * dist - lowerLength * lowerLength) / (2.0F * upperLength * dist), -1.0F, 1.0F);
			float t1 = toTarget + bend * (float)Math.acos(cos);
			float kneeZ = hipZ + upperLength * Mth.sin(t1);
			float kneeY = hipY + upperLength * Mth.cos(t1);
			out[0] = t1;
			out[1] = (float)Math.atan2(targetZ - kneeZ, targetY - kneeY);
		}
	}

	/**
	 * (0.18.109, Scan C) Tusk: a flat snout with two curved tusks, and a stiff mane down its back; (0.19.62) a boar on
	 * four legs: a barrel body lying along the ground, short thick legs, a big low head that bobs as it trots, a curly
	 * tail. Its Charge drops the head and gallops; an attack tosses the tusks up. The man's body, head and legs are the
	 * barrel, the head and the front pair (so the pose code finds them), the hind pair new; the arms and hat are hidden.
	 */
	static final class Tusk extends FormModel {
		// ---- tuning ----
		/** The barrel's middle height (model y) and where its rear end pivots (z); it is 10 wide, 16 long, 8 thick. */
		static final float BODY_Y = 13.0F, BODY_Z = 8.0F;
		/** The legs: hip height (the barrel's underside), length below it, how far up into the barrel the box reaches, half its depth, and where they stand. */
		static final float HIP_Y = 17.0F, LEG_LEN = 7.0F, LEG_UP = 3.0F, LEG_HALF_DEPTH = 2.5F;
		static final float LEG_X = 2.8F, FRONT_Z = -5.0F, HIND_Z = 5.0F;
		/** The head's pivot (its back, the neck) in model space. */
		static final float HEAD_Y = 14.0F, HEAD_Z = -6.5F;
		/** The lowest the snout and jaw may reach (model y; the ground is 24), and how far up the head may tip (radians). */
		static final float HEAD_FLOOR = 23.5F, HEAD_UP = -1.2F;
		/** The lowest corners of the snout and of the jaw, below and ahead of the neck (pixels), which {@code HEAD_FLOOR} keeps off the ground. */
		static final float SNOUT_LOW_Y = 3.5F, SNOUT_LOW_Z = 12.5F, JAW_LOW_Y = 4.8F, JAW_LOW_Z = 8.8F;
		/** The trot: how fast the legs go against the walk, how far they swing (radians), how high a foot lifts (pixels). */
		static final float TROT = 0.6662F, TROT_SWING = 0.55F, TROT_LIFT = 1.5F;
		/** The gallop of a charge: the same, the least a charge swings, and how far the hind pair trails the front. */
		static final float GALLOP = 0.6662F * 1.6F, GALLOP_SWING = 0.95F, GALLOP_LIFT = 2.4F, GALLOP_MIN = 0.85F, GALLOP_TRAIL = 2.7F;
		/** The bounce of a step (pixels), the sway of the barrel (yaw, pitch), the head's nod (radians). */
		static final float BOB = 0.5F, SWAY = 0.05F, ROCK = 0.03F, NOD = 0.12F;
		/** How far a sneak drops the barrel (pixels), and how much of a foot's lift is left then (the legs stay put while the barrel sinks, so a lift would poke them through its back). */
		static final float CROUCH_DROP = 2.5F, CROUCH_LIFT = 0.0F;
		/** A charge: the head's pitch (radians, + is down; the tusks then point forward), how far the head is raised so the snout clears the ground (pixels), the barrel's nose-down tilt, how much the mane bristles. */
		static final float CHARGE_HEAD = 0.5F, CHARGE_RAISE = 1.0F, CHARGE_TILT = 0.1F, CHARGE_MANE = 0.35F;
		/** An attack: the head dips (radians) then tosses up, the front rears (radians) with its legs reaching forward (radians), how far the head lunges and lifts (pixels). */
		static final float TOSS_DIP = 0.35F, TOSS_UP = 0.95F, TOSS_REAR = 0.12F, TOSS_REACH = 0.4F, TOSS_LUNGE = 1.0F, TOSS_LIFT = 1.5F;

		private final ModelPart hindRight, hindLeft, mane, tail;

		Tusk() {
			super(bake(mesh()), "tusk");
			// the plate lies on top of the barrel's rear half: the body's front face (-z) faces up once it is laid down
			this.insigniaY = 4.5F;
			this.insigniaZ = -4.0F;
			this.insigniaSize = 1.0F;
			this.hindRight = this.root().getChild("hind_right_leg");
			this.hindLeft = this.root().getChild("hind_left_leg");
			this.mane = this.body.getChild("mane");
			this.tail = this.body.getChild("tail");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: the man's 8x8x8 head (his skin's head), grown, pivoting at the back of the neck
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.8F)),
				PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 98).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 4.0F, 4.0F),
				PartPose.offset(0.0F, 1.5F, -8.5F));
			for (int i = 0; i < 2; i++) {
				boolean left = i == 1;
				float side = left ? 1.0F : -1.0F;
				PartDefinition tusk = head.addOrReplaceChild(left ? "left_tusk" : "right_tusk",
					CubeListBuilder.create().texOffs(20, 98).mirror(left).addBox(-0.5F, -1.0F, -4.0F, 1.0F, 2.0F, 4.0F),
					PartPose.offsetAndRotation(side * 3.3F, 2.0F, -9.5F, -0.45F, -side * 0.12F, 0.0F));
				tusk.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(30, 98).mirror(left).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 4.0F),
					PartPose.offsetAndRotation(0.0F, 0.0F, -3.5F, -0.7F, 0.0F, 0.0F));
				head.addOrReplaceChild(left ? "left_ear" : "right_ear",
					CubeListBuilder.create().texOffs(40, 98).mirror(left).addBox(-1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F),
					PartPose.offsetAndRotation(side * 3.6F, -4.6F, -1.5F, -0.2F, 0.0F, side * 0.4F));
			}
			// the barrel: laid along the ground by xRot -90 (its front face is the back, facing up; y is the length, rear to front)
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, 0.0F, -4.0F, 10.0F, 16.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, BODY_Y, BODY_Z, -(float)Math.PI * 0.5F, 0.0F, 0.0F));
			// the mane over the shoulders (the barrel's front half), a ridge standing off its back
			body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(0, 88).addBox(-1.0F, -3.5F, -3.0F, 2.0F, 7.0F, 3.0F),
				PartPose.offset(0.0F, 12.5F, -3.5F));
			body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 98).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 0.5F, -2.5F, -0.4F, 0.0F, 0.0F));
			// the front legs are the man's legs, the hind pair new; all four share one skin box (the left mirrored)
			root.addOrReplaceChild("right_leg", legBox(false), PartPose.offset(-LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("left_leg", legBox(true), PartPose.offset(LEG_X, HIP_Y, FRONT_Z));
			root.addOrReplaceChild("hind_right_leg", legBox(false), PartPose.offset(-LEG_X, HIP_Y, HIND_Z));
			root.addOrReplaceChild("hind_left_leg", legBox(true), PartPose.offset(LEG_X, HIP_Y, HIND_Z));
			return mesh;
		}

		/** A thick leg: a 5x10x5 box from {@code LEG_UP} above the hip to {@code LEG_LEN} below it. */
		private static CubeListBuilder legBox(boolean left) {
			return CubeListBuilder.create().texOffs(0, 106).mirror(left).addBox(-2.5F, -LEG_UP, -LEG_HALF_DEPTH, 5.0F, LEG_UP + LEG_LEN, 2.0F * LEG_HALF_DEPTH);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft, this.mane, this.tail}) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			boolean charge = "charge".equals(state.getData(XenoformClient.ACT));
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float amp = charge ? Math.max(speed, GALLOP_MIN) : speed;
			float walk = state.walkAnimationPos * (charge ? GALLOP : TROT);
			float swing = (charge ? GALLOP_SWING : TROT_SWING) * amp;
			float lift = (charge ? GALLOP_LIFT : TROT_LIFT) * amp * (state.isCrouching ? CROUCH_LIFT : 1.0F);
			float crouch = state.isCrouching ? CROUCH_DROP : 0.0F;
			// an attack: the head dips, then the tusks toss up
			float sw = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float toss = tossAngle(sw);
			float tossAmt = Mth.sin(sw * (float)Math.PI);
			// the barrel: bounces twice a stride, sways, and tilts nose-down in a charge and nose-up in a toss
			float bounce = Mth.abs(Mth.cos(walk)) * BOB * amp;
			float yaw = Mth.sin(walk) * SWAY * amp;
			float pitch = (charge ? CHARGE_TILT : 0.0F) - tossAmt * TOSS_REAR + Mth.sin(walk * 2.0F + 0.5F) * ROCK * amp;
			this.body.x = 0.0F;
			this.body.y = BODY_Y - bounce + crouch;
			this.body.z = BODY_Z;
			this.body.xRot = -(float)Math.PI * 0.5F + pitch;
			this.body.yRot = yaw;
			// the legs: a trot has diagonal pairs together; a gallop has the fronts close and the hinds trailing
			float frontOther = walk + (charge ? 0.6F : (float)Math.PI);
			float hind = walk + (charge ? GALLOP_TRAIL : (float)Math.PI);
			float hindOther = walk + (charge ? GALLOP_TRAIL + 0.6F : 0.0F);
			// a toss rears the front: its legs reach forward and rise with the barrel's front
			float rear = tossAmt * TOSS_REACH;
			float rise = (BODY_Z - FRONT_Z) * Mth.sin(tossAmt * TOSS_REAR);
			this.leg(this.rightLeg, walk, -LEG_X, FRONT_Z, swing, lift, yaw, rear, rise);
			this.leg(this.leftLeg, frontOther, LEG_X, FRONT_Z, swing, lift, yaw, rear, rise);
			this.leg(this.hindRight, hind, -LEG_X, HIND_Z, swing, lift, yaw, 0.0F, 0.0F);
			this.leg(this.hindLeft, hindOther, LEG_X, HIND_Z, swing, lift, yaw, 0.0F, 0.0F);
			// the head: low on the neck, follows the look, nods a beat after each step, never digs its snout in
			float headDrop = (BODY_Z - HEAD_Z) * Mth.sin(pitch);
			this.head.x = (HEAD_Z - BODY_Z) * Mth.sin(yaw);
			this.head.y = HEAD_Y - bounce + crouch + headDrop - tossAmt * TOSS_LIFT - (charge ? CHARGE_RAISE : 0.0F);
			this.head.z = HEAD_Z - tossAmt * TOSS_LUNGE;
			float look = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.1F, 1.1F);
			float aim = state.xRot * Mth.DEG_TO_RAD;
			this.head.yRot = look;
			float headPitch = (charge ? CHARGE_HEAD + Math.min(aim, 0.0F) * 0.3F : Mth.clamp(aim * 0.6F, -0.8F, 0.35F))
				+ Mth.sin(walk * 2.0F - 0.8F) * NOD * amp * (charge ? 0.4F : 1.0F) + toss;
			float room = HEAD_FLOOR - this.head.y;
			this.head.xRot = Mth.clamp(headPitch, HEAD_UP, Math.min(reach(room, SNOUT_LOW_Y, SNOUT_LOW_Z), reach(room, JAW_LOW_Y, JAW_LOW_Z)));
			// the mane sways and bristles; the tail wags, and lifts when it falls or charges
			this.mane.xRot = Mth.sin(state.ageInTicks * 0.09F) * 0.02F + m.fall() * 0.12F;
			this.mane.zRot = m.trail() * 0.1F;
			this.mane.zScale = 1.0F + (charge ? CHARGE_MANE : 0.0F) + tossAmt * 0.2F;
			this.tail.xRot = (charge ? 0.25F : -0.4F) + tailLift(m);
			this.tail.zRot = Mth.sin(state.ageInTicks * 0.15F) * 0.15F + Mth.sin(walk) * 0.2F * amp + m.trail() * 0.5F;
		}

		/**
		 * One leg: swings about the hip (a rearing front leg reaches forward by {@code rear} and rises {@code rise}),
		 * follows the barrel's sway, drops its hip so the lowest corner of its foot stays on the ground, and lifts on the
		 * way forward.
		 */
		private void leg(ModelPart leg, float phase, float x, float z, float swingAmt, float liftAmt, float yaw, float rear, float rise) {
			float angle = Mth.sin(phase) * swingAmt - rear;
			float up = Math.max(0.0F, -Mth.cos(phase)) * liftAmt;
			leg.x = x + (z - BODY_Z) * Mth.sin(yaw);
			leg.z = z;
			leg.y = HIP_Y + LEG_LEN * (1.0F - Mth.cos(angle)) - LEG_HALF_DEPTH * Mth.abs(Mth.sin(angle)) - up - rise;
			leg.xRot = angle;
		}

		/** The head's angle through an attack (radians, + is down): a dip, then the toss up. */
		private static float tossAngle(float swing) {
			float a = Mth.sin(swing * 2.0F * (float)Math.PI);
			return a > 0.0F ? a * TOSS_DIP : a * TOSS_UP;
		}

		/**
		 * The most the head may pitch down (radians) before a corner {@code below} under and {@code ahead} in front of
		 * the neck sinks past {@code room} pixels below the neck.
		 */
		private static float reach(float room, float below, float ahead) {
			float r = (float)Math.sqrt(below * below + ahead * ahead);
			if (room >= r) {
				return (float)Math.PI * 0.5F;
			}
			return (float)(Math.asin(Math.max(-1.0F, room / r)) - Math.atan2(below, ahead));
		}
	}

	/**
	 * (0.18.109, Scan C; 0.19.75 a bird of prey) Talon: a hooked beak, broad feathered wings and a tail fan, scaly legs
	 * with hooked talons; now a hawk, not a man. On the ground it perches upright (the body leaning 60 degrees up from
	 * level, the wings folded at its sides, the tail hanging behind), the head turning where it looks with the beak
	 * hooked below it, and it walks in short two-footed hops. Flying, the body goes level, the wings spread (two parts,
	 * an arm and a hand with four long flight feathers) and beat as it climbs ({@code Flight.wings}), hold out stiff in
	 * a glide ({@code Flight.glide}) and sweep back on a stoop ({@code Flight.fold}), with the legs tucked under the tail
	 * (talons forward on a stoop). A strike rakes both legs forward and pecks the head down. The man's parts are hidden
	 * (the arm stays for the first-person hand); the torso is the rig every bird part hangs on, so the insignia plate sits on
	 * its breast. Wind's cruise tilt ({@code WindPose}) and the glide's lean ({@code XenoformFlightPose#rotate}) turn the whole
	 * drawn model, so the body undoes the part of them it does not want ({@link #setupAnim}).
	 */
	static final class Talon extends FormModel {
		// ---- tuning ----
		/** The perch: the body leans this far from upright (radians; 0.52 is 60 degrees up from level), more crouched or striking, and the rock of a hop. */
		static final float PERCH_TILT = 0.52F, CROUCH_TILT = 0.95F, STRIKE_TILT = 0.4F, ROCK = 0.07F;
		/** Standing legs (radians from straight down, + is back): the thigh forward, the shin back, and how much more a crouch bends them. */
		static final float THIGH_REST = -0.45F, SHIN_REST = 0.25F, CROUCH_THIGH = -0.4F, CROUCH_SHIN = 0.55F;
		/** The hop: radians of clock per unit of walk, its height (pixels), how far the legs tuck in it (thigh, shin), and the legs' alternate shuffle. */
		static final float HOP = 1.6F, HOP_LIFT = 1.4F, HOP_TUCK_THIGH = 0.45F, HOP_TUCK_SHIN = 0.55F, SHUFFLE = 0.2F;
		/** The head bobs forward and back as it hops (pixels). */
		static final float HEAD_BOB = 0.8F;
		/** A strike: how far the thighs rake forward and the shins with them (radians), how far the body lunges ahead (pixels) and the head pecks down. */
		static final float STRIKE_THIGH = 1.35F, STRIKE_SHIN = 0.25F, STRIKE_LUNGE = 1.5F, PECK = 0.6F;
		/** In the air: the body's height (model y; the ground is 24), how far it bobs with each wingbeat (pixels) and the wingbeat's rate (radians a tick). */
		static final float AIR_Y = 10.5F, WING_BOB = 0.9F, BEAT = 0.95F;
		/** Falling (not flying), the wings come this far open and flutter this hard. */
		static final float FALL_SPREAD = 0.85F, FALL_FLAP = 0.6F;
		/** The stroke: the wing's mean lift above level and how far it swings (radians), the nose-down of a glide and the body's pitch with each beat. */
		static final float FLAP_BIAS = 0.1F, FLAP_SPAN = 0.55F, GLIDE_PITCH = 0.2F, BEAT_PITCH = 0.06F;
		/** A glide holds the wings up at this angle and rocks them a little. */
		static final float GLIDE_DIHEDRAL = 0.16F, GLIDE_ROCK = 0.04F;
		/** The wings on a stoop sweep back this far; the hand follows this much more, and lags a beat this much. */
		static final float SWEEP = 1.15F, HAND_SWEEP = 0.45F, HAND_LAG = 0.55F;
		/** Folded: the wing leans out from the body this much and its hand folds back along the body this much (radians); spread, the wing pitches about its length this much. */
		static final float FOLD_OUT = 0.1F, FOLD_HAND = 0.75F, WING_PITCH = 0.2F;
		/** Legs in the air (radians back from hanging): hovering, tucked in a glide, forward on a stoop; the shin's and the foot's bend. */
		private static final float HOVER_LEG = 0.7F, TUCK_LEG = 1.15F, STOOP_LEG = -0.9F;
		private static final float HOVER_SHIN = 0.55F, TUCK_SHIN = 0.9F, STOOP_SHIN = 0.1F, HOVER_CURL = 0.5F, STOOP_CURL = -0.3F;
		/** The tail: how it rests (the feathers' pitch), its fan (radians between feathers) perched and spread, and its lowest pitch (so its tip clears the ground). */
		static final float TAIL_REST = 0.15F, TAIL_FAN = 0.12F, TAIL_FAN_WIDE = 0.34F, TAIL_LOW = -0.05F;
		/** Where the parts hang in the torso's space (x of the right side; the left mirrors): the shoulders, the head and the hips (z and y as well). */
		private static final float SHOULDER_X = 3.7F, SHOULDER_Y = -3.0F, SHOULDER_Z = 0.3F;
		private static final float HEAD_Y = -5.0F, HEAD_Z = -0.5F;
		private static final float HIP_X = 1.9F, HIP_Y = 3.0F, HIP_Z = 0.0F;
		/** The legs' bones (pixels) and the foot's half thickness: a standing foot's sole is the sum below the hip. */
		private static final float THIGH_LENGTH = 4.0F, SHIN_LENGTH = 6.0F, FOOT_HALF = 0.5F;
		/** How far the front talons and the hind talon reach from the middle of the foot (pixels), for how low a curled foot hangs. */
		private static final float TOE_REACH = 4.5F, HIND_REACH = 2.5F;
		/**
		 * A sneaking player is drawn 2/16 of a block lower (AvatarRenderer.getRenderOffset), 2/0.9375 model pixels: crouched,
		 * the ground is that much higher in model space.
		 */
		private static final float CROUCH_SINK = 2.0F / 0.9375F;
		/** The four flight feathers of a hand: where each is hinged (y, z), its length against the longest, and its fan open and shut (radians, the right wing's). */
		private static final float[] FEATHER_Y = {-1.5F, -0.5F, 0.5F, 1.5F}, FEATHER_Z = {0.0F, 0.1F, 0.2F, 0.3F};
		private static final float[] FEATHER_LENGTH = {1.0F, 0.95F, 0.88F, 0.78F};
		private static final float[] FAN_OPEN = {0.14F, -0.05F, -0.24F, -0.42F}, FAN_SHUT = {0.0F, -0.02F, -0.04F, -0.06F};
		/** The five tail feathers: their z (stacked so none shares a plane), and their length against the middle one. */
		private static final float[] TAIL_Z = {1.0F, 1.1F, 1.2F, 1.1F, 1.0F}, TAIL_LENGTH = {0.8F, 0.92F, 1.0F, 0.92F, 0.8F};
		private static final float HALF_PI = (float)Math.PI * 0.5F;

		private final ModelPart skull;
		/** 0 right, 1 left. */
		private final ModelPart[] arm = new ModelPart[2], hand = new ModelPart[2];
		private final ModelPart[][] feather = new ModelPart[2][4], toe = new ModelPart[2][4];
		private final ModelPart[] thigh = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
		private final ModelPart[] tail = new ModelPart[5];

		Talon() {
			super(bake(mesh()), "talon");
			// on the breast, a small plate (the skin's insignia is the small one)
			this.insigniaY = 0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 0.7F;
			this.skull = this.body.getChild("skull");
			for (int s = 0; s < 2; s++) {
				String p = s == 0 ? "r_" : "l_";
				this.arm[s] = this.body.getChild(p + "arm");
				this.hand[s] = this.arm[s].getChild("hand");
				this.thigh[s] = this.body.getChild(p + "thigh");
				this.shin[s] = this.thigh[s].getChild("shin");
				this.foot[s] = this.shin[s].getChild("foot");
				for (int i = 0; i < 4; i++) {
					this.feather[s][i] = this.hand[s].getChild("feather" + i);
					this.toe[s][i] = this.foot[s].getChild("toe" + i);
				}
			}
			for (int i = 0; i < 5; i++) {
				this.tail[i] = this.body.getChild("tail" + i);
			}
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the man's head, hat, arms and legs stay (hidden; the arms for the first-person hand); his body is replaced by the
			// bird's torso, 7 wide, 10 long and 5 deep, which every other part hangs on
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -5.0F, -2.5F, 7.0F, 10.0F, 5.0F),
				PartPose.offset(0.0F, 12.0F, 0.0F));
			// the head, sunk two pixels into the shoulders so it joins however the body leans; a brow, a hooked beak
			PartDefinition skull = body.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(24, 64).addBox(-2.5F, -5.0F, -2.5F, 5.0F, 7.0F, 5.0F),
				PartPose.offset(0.0F, HEAD_Y, HEAD_Z));
			skull.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(44, 70).addBox(-2.5F, -0.5F, -1.0F, 5.0F, 1.0F, 1.0F),
				PartPose.offset(0.0F, -3.6F, -2.4F));
			PartDefinition beak = skull.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(44, 64).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 3.0F),
				PartPose.offset(0.0F, -2.0F, -2.4F));
			beak.addOrReplaceChild("hook", CubeListBuilder.create().texOffs(54, 64).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 0.3F, -2.2F, 0.15F, 0.0F, 0.0F));
			for (int s = 0; s < 2; s++) {
				boolean left = s == 1;
				String p = left ? "l_" : "r_";
				float sx = left ? 1.0F : -1.0F;
				// the wing: an arm (7 across, 5 chord) hinged on the shoulder, a hand on its tip, four flight feathers on the hand
				PartDefinition wing = body.addOrReplaceChild(p + "arm", flip(CubeListBuilder.create().texOffs(0, 80), left).addBox(left ? -1.0F : -6.0F, -2.5F, -0.5F, 7.0F, 5.0F, 1.0F),
					PartPose.offset(sx * SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
				PartDefinition palm = wing.addOrReplaceChild("hand", flip(CubeListBuilder.create().texOffs(16, 80), left).addBox(left ? 0.0F : -3.0F, -2.0F, -0.5F, 3.0F, 4.0F, 1.0F),
					PartPose.offset(sx * 6.0F, 0.0F, 0.0F));
				for (int i = 0; i < 4; i++) {
					palm.addOrReplaceChild("feather" + i, flip(CubeListBuilder.create().texOffs(24, 80), left).addBox(left ? 0.0F : -8.0F, -1.0F, -0.5F, 8.0F, 2.0F, 1.0F),
						PartPose.offset(sx * 3.0F, FEATHER_Y[i], FEATHER_Z[i]));
				}
				// the leg: a feathered thigh, a scaly shin, a foot with three toes forward and one back
				PartDefinition leg = body.addOrReplaceChild(p + "thigh", flip(CubeListBuilder.create().texOffs(0, 90), left).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F),
					PartPose.offset(sx * HIP_X, HIP_Y, HIP_Z));
				PartDefinition shank = leg.addOrReplaceChild("shin", flip(CubeListBuilder.create().texOffs(12, 90), left).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
					PartPose.offset(0.0F, THIGH_LENGTH, 0.0F));
				PartDefinition paw = shank.addOrReplaceChild("foot", flip(CubeListBuilder.create().texOffs(20, 90), left).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 2.0F),
					PartPose.offset(0.0F, SHIN_LENGTH, 0.0F));
				for (int i = 0; i < 4; i++) {
					// the outer toe to the side, the inner toe to the other, the hind toe (turned round and half the length, in setupAnim) behind
					float x = (i == 1 ? -0.6F : i == 2 ? 0.6F : 0.0F) * -sx;
					float z = i == 3 ? 0.5F : -0.5F;
					paw.addOrReplaceChild("toe" + i, flip(CubeListBuilder.create().texOffs(28, 90), left).addBox(-0.5F, -0.5F, -4.0F, 1.0F, 1.0F, 4.0F),
						PartPose.offsetAndRotation(x, i == 1 || i == 2 ? -0.04F : 0.0F, z, 0.0F, i == 3 ? (float)Math.PI : 0.0F, 0.0F));
				}
			}
			// the tail: five feathers fanned from the rump
			for (int i = 0; i < 5; i++) {
				body.addOrReplaceChild("tail" + i, CubeListBuilder.create().texOffs(42, 80).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 9.0F, 1.0F),
					PartPose.offset(0.0F, 4.5F, TAIL_Z[i]));
			}
			return mesh;
		}

		/** The left side's boxes are drawn with the skin mirrored. */
		private static CubeListBuilder flip(CubeListBuilder builder, boolean left) {
			return left ? builder.mirror() : builder;
		}

		/** The standing legs this frame (world angles, radians from straight down, + back), kept to avoid allocating each frame. */
		private final float[] groundThigh = new float[2], groundShin = new float[2];

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man is hidden (the arms are drawn alone in first person); the torso is the rig
			this.head.visible = this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			Float wind = state.getData(com.jaymospowers.client.power.wind.WindPose.KEY);
			float age = state.ageInTicks;
			float fly = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
			float glide = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.glide());
			float fold = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.fold()) * fly;
			float cruise = wind == null ? 0.0F : Mth.clamp(wind, 0.0F, 1.0F);
			// airborne (flying, gliding, or just off the ground) against perched
			float air = m.air();
			float lift = Math.max(Math.max(fly, glide), air);
			float spread = Math.max(Math.max(fly, glide), air * FALL_SPREAD);
			float flap = Math.max(fly, air * FALL_FLAP * (1.0F - glide));
			float beat = age * BEAT;
			float strike = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float crouch = Math.max(m.crouching() ? 1.0F : 0.0F, m.land()) * (1.0F - lift);
			// walking: short two-footed hops
			float sp = Mth.clamp(state.walkAnimationSpeed * 1.5F, 0.0F, 1.0F) * (1.0F - lift);
			float phase = state.walkAnimationPos * HOP;
			float hop = Math.max(0.0F, Mth.sin(phase)) * sp;

			// the body's pitch: the perch's lean, or level (following the look at speed); undoing what the whole-model tilts add
			float look = state.xRot * Mth.DEG_TO_RAD;
			float tGround = Mth.lerp(crouch, PERCH_TILT, CROUCH_TILT) + STRIKE_TILT * strike + Mth.sin(phase) * ROCK * sp + m.breath() * 0.02F;
			float tAir = HALF_PI + GLIDE_PITCH * glide + BEAT_PITCH * fly * Mth.sin(beat) + cruise * look;
			float t = Mth.lerp(lift, tGround, tAir);
			// Wind's cruise tips the drawn model forward to lie along the flight, the glide leans it back; both about the middle of
			// its box. The bird is already level, so it stands up in the model to arrive level (its pitch kept) after them.
			float tilt = cruise * (HALF_PI + look) - XenoformFlightPose.GLIDE_LEAN * Mth.DEG_TO_RAD * glide;

			// perched, the lowest foot is on the ground (a hop lifts the body further than the legs tuck up)
			float lowest = 0.0F;
			float chainZ = 0.0F;
			for (int s = 0; s < 2; s++) {
				float side = s == 0 ? 1.0F : -1.0F;
				float swing = Mth.cos(phase) * SHUFFLE * sp * side;
				this.groundThigh[s] = THIGH_REST + CROUCH_THIGH * crouch + swing;
				this.groundShin[s] = SHIN_REST + CROUCH_SHIN * crouch - swing * 0.6F;
				// (a tilted shin's lower corners hang a little below the foot)
				lowest = Math.max(lowest, THIGH_LENGTH * Mth.cos(this.groundThigh[s]) + SHIN_LENGTH * Mth.cos(this.groundShin[s])
					+ Math.max(FOOT_HALF, Math.abs(Mth.sin(this.groundShin[s]))));
				chainZ += (THIGH_LENGTH * Mth.sin(this.groundThigh[s]) + SHIN_LENGTH * Mth.sin(this.groundShin[s])) * 0.5F;
			}
			float hipDrop = HIP_Y * Mth.cos(tGround) - HIP_Z * Mth.sin(tGround);
			float hipBack = HIP_Y * Mth.sin(tGround) + HIP_Z * Mth.cos(tGround);
			float floor = 24.0F - (state.isCrouching ? CROUCH_SINK : 0.0F);
			float groundY = floor - lowest - hipDrop - hop * HOP_LIFT;
			float groundZ = -(hipBack + chainZ) - strike * STRIKE_LUNGE;
			float airY = AIR_Y + WING_BOB * fly * Mth.sin(beat);
			float wantY = Mth.lerp(lift, groundY, airY);
			float wantZ = Mth.lerp(lift, groundZ, 0.0F);
			// between perched and airborne (a landing, the wings easing out) the legs swing through hanging straight down
			// while the body has barely risen: lift it so neither foot goes through the ground
			for (int s = 0; s < 2; s++) {
				this.leg(s, lift, t, hop, strike, fold, glide, age);
				wantY = Math.min(wantY, floor - this.footDrop(s, t));
			}
			// the tilt turns the model about its box's middle (in the renderer's frame, which is the model's scaled by 0.9375
			// with its ground at y 24.016): put the body where, once turned, it is wanted
			float pivot = 24.016F - state.boundingBoxHeight / Math.max(state.scale, 0.01F) * 8.0F / 0.9375F;
			float vy = wantY - pivot;
			float ce = Mth.cos(tilt), se = Mth.sin(tilt);
			this.body.x = 0.0F;
			this.body.y = pivot + vy * ce + wantZ * se;
			this.body.z = -vy * se + wantZ * ce;
			this.body.xRot = t - tilt;
			this.body.yRot = this.body.zRot = 0.0F;

			this.head(state, t, cruise, strike, sp, phase, age);
			for (int s = 0; s < 2; s++) {
				this.wing(s, spread, flap, beat, fold, glide, m.land() + Math.abs(m.turn()) * 0.25F, age);
			}
			this.tail(m, spread, glide, fold, fly, beat, age);
		}

		/** How far below the body's middle this leg reaches as it is posed, the body pitched {@code t} (pixels, down). */
		private float footDrop(int s, float t) {
			float a = t + this.thigh[s].xRot, b = a + this.shin[s].xRot, f = b + this.foot[s].xRot;
			float toes = FOOT_HALF * Mth.abs(Mth.cos(f)) + Math.max(0.0F, Math.max(TOE_REACH * Mth.sin(f), -HIND_REACH * Mth.sin(f)));
			return HIP_Y * Mth.cos(t) - HIP_Z * Mth.sin(t) + THIGH_LENGTH * Mth.cos(a) + SHIN_LENGTH * Mth.cos(b) + Math.max(Mth.abs(Mth.sin(b)), toes);
		}

		/** The head looks where the player does whatever the body is doing (the turn the body's tilt gives it undone), and bobs as it hops. */
		private void head(AvatarRenderState state, float t, float cruise, float strike, float sp, float phase, float age) {
			float idle = 1.0F - sp;
			float yaw = Mth.clamp(state.yRot * (1.0F - cruise), -75.0F, 75.0F) * Mth.DEG_TO_RAD + Mth.sin(age * 0.047F) * 0.06F * idle;
			float pitch = Mth.clamp(state.xRot, -80.0F, 80.0F) * Mth.DEG_TO_RAD + PECK * strike + Mth.sin(age * 0.031F + 1.0F) * 0.04F * idle;
			// the head's turn in the world, Ry(yaw) Rx(pitch), seen from the body's frame, Rx(-t) first; as ModelPart's Rz Ry Rx
			float cy = Mth.cos(yaw), sy = Mth.sin(yaw), cp = Mth.cos(pitch), sn = Mth.sin(pitch), ct = Mth.cos(t), st = Mth.sin(t);
			this.skull.xRot = (float)Math.atan2(-st * cp + ct * cy * sn, st * sn + ct * cy * cp);
			this.skull.yRot = (float)Math.asin(Mth.clamp(ct * sy, -1.0F, 1.0F));
			this.skull.zRot = (float)Math.atan2(-st * sy, cy);
			this.skull.x = 0.0F;
			this.skull.y = HEAD_Y;
			this.skull.z = HEAD_Z + Mth.sin(phase) * HEAD_BOB * sp - strike * STRIKE_LUNGE * 0.5F;
		}

		/**
		 * One wing. Folded (spread 0) it lies against the body's side; spread, the arm goes out level and swings up and down
		 * with the beat, the hand behind it by a beat's lag; a glide holds it up and still, a stoop sweeps it back, and the
		 * flight feathers fan out when it is open.
		 */
		private void wing(int s, float spread, float flap, float beat, float fold, float glide, float flare, float age) {
			float side = s == 0 ? 1.0F : -1.0F;
			float up = flap * (FLAP_BIAS + FLAP_SPAN * Mth.sin(beat)) * (1.0F - glide)
				+ glide * (GLIDE_DIHEDRAL + Mth.sin(age * 0.07F + side) * GLIDE_ROCK);
			ModelPart a = this.arm[s];
			// the roll about its length turns the folded wing's flat to the body's side
			a.xRot = Mth.lerp(spread, HALF_PI, WING_PITCH * flap * Mth.cos(beat) * (1.0F - glide) - 0.1F * glide);
			a.yRot = side * up * spread;
			a.zRot = side * Mth.lerp(spread, -(HALF_PI - FOLD_OUT - Mth.clamp(flare, 0.0F, 1.0F) * 0.4F), -SWEEP * fold);
			ModelPart h = this.hand[s];
			h.xRot = 0.0F;
			h.yRot = -side * spread * flap * HAND_LAG * Mth.cos(beat) * (1.0F - glide);
			h.zRot = side * Mth.lerp(spread, -FOLD_HAND, -HAND_SWEEP * fold);
			float open = spread * (1.0F - 0.85F * fold);
			for (int i = 0; i < 4; i++) {
				ModelPart f = this.feather[s][i];
				f.xRot = 0.0F;
				f.xScale = FEATHER_LENGTH[i];
				f.zRot = side * (Mth.lerp(open, FAN_SHUT[i], FAN_OPEN[i]) + Mth.sin(age * 0.3F + i * 1.1F + side) * 0.02F * glide);
				f.yRot = -side * spread * flap * Mth.cos(beat) * 0.3F * (0.4F + 0.2F * i) * (1.0F - glide);
			}
		}

		/** The tail: five feathers that fan wider in the air and on a landing, close on a stoop, lift as it falls and swing out of a turn. */
		private void tail(XenoMotion.Read m, float spread, float glide, float fold, float fly, float beat, float age) {
			float open = Mth.clamp(spread * 0.6F + glide * 0.4F + m.land() * 0.6F, 0.0F, 1.0F) * (1.0F - 0.8F * fold);
			float fan = Mth.lerp(open, TAIL_FAN, TAIL_FAN_WIDE);
			float pitch = Math.max(TAIL_LOW, TAIL_REST + tailLift(m) * 0.5F);
			for (int i = 0; i < 5; i++) {
				ModelPart feather = this.tail[i];
				feather.xRot = pitch + Mth.sin(age * 0.11F + i * 0.5F) * 0.02F + fly * Mth.sin(beat + 1.2F) * 0.05F;
				feather.yRot = 0.0F;
				feather.zRot = (i - 2) * fan + m.trail() * 0.3F + Mth.sin(age * 0.05F) * 0.02F;
				feather.yScale = TAIL_LENGTH[i];
			}
		}

		/**
		 * One leg. Perched, the thigh and shin stand it on the ground, its foot flat (the walk's hop tucks it, a strike rakes
		 * it forward, talons open); airborne it hangs from the belly, tucked back under the tail (forward on a stoop).
		 */
		private void leg(int s, float lift, float t, float hop, float strike, float fold, float glide, float age) {
			float side = s == 0 ? 1.0F : -1.0F;
			float a = this.groundThigh[s] - HOP_TUCK_THIGH * hop - STRIKE_THIGH * strike;
			float b = this.groundShin[s] + HOP_TUCK_SHIN * hop - STRIKE_SHIN * strike;
			float curl = 0.4F * hop + 0.5F * strike;
			float tau = Mth.lerp(fold, Mth.lerp(glide, HOVER_LEG, TUCK_LEG), STOOP_LEG) - STRIKE_THIGH * 0.9F * strike
				+ Mth.sin(age * 0.1F + side * 1.3F) * 0.04F * (1.0F - glide);
			float shinAir = Mth.lerp(fold, Mth.lerp(glide, HOVER_SHIN, TUCK_SHIN), STOOP_SHIN);
			float curlAir = Mth.lerp(fold, HOVER_CURL + 0.3F * glide, STOOP_CURL);
			this.thigh[s].xRot = Mth.lerp(lift, a - t, -HALF_PI + tau);
			this.shin[s].xRot = Mth.lerp(lift, b - a, shinAir);
			this.foot[s].xRot = Mth.lerp(lift, -b + curl, curlAir);
			// the talons spread wider when they strike or stoop
			float splay = 0.3F + 0.35F * Math.max(strike, fold);
			this.toe[s][0].yRot = 0.0F;
			this.toe[s][1].yRot = side * splay;
			this.toe[s][2].yRot = -side * splay;
			this.toe[s][3].yRot = (float)Math.PI;
			this.toe[s][3].zScale = 0.5F;
		}
	}

	/**
	 * (0.18.111, Scan D; 0.19.82 a real squid) Murk: a long soft mantle swept back from its head, big black eyes, a
	 * beak ringed by eight jointed tentacles. In water it swims mantle-first (the man's swim pitch is cancelled and the
	 * body drawn level, pitched where it looks): the tentacles trail and open slowly, then snap together as the mantle
	 * squeezes and the fins ripple. On land it drags itself with its head low, the mantle slumped or raised behind it,
	 * the tentacles splayed along the ground (each a three-joint chain solved so it lies flat, whatever its ring height)
	 * and pulling in a ripple, lifting as they reach forward. Attack lashes the tentacles forward. The man's parts are
	 * hidden; the hidden body is laid face-up on the mantle's back and carries the insignia.
	 */
	static final class Murk extends FormModel {
		// ---- tuning ----
		/** The ground (model y) and how far a sneaking player is drawn lower (px). */
		static final float GROUND = 24.0F, CROUCH_SINK = 2.0F;
		/**
		 * Vanilla's swim pose (AvatarRenderer.setupRotations) turns the model about the feet, a block's offset (1.501, so
		 * 24.016 px) above its origin, then lifts it a block and pushes it back 0.3, before the player's 0.9375 scale: in
		 * model pixels that is 16 / 0.9375 and 4.8 / 0.9375. Cancelled so the squid stays level.
		 */
		static final float SWIM_PIVOT = 24.016F, SWIM_LIFT = 17.067F, SWIM_PUSH = 5.12F;
		/** The squid's middle in water: height standing (treading) and in the swim pose (model y), its bob, and where its middle is along the body (px behind the head). */
		static final float WATER_Y = 11.0F, WATER_Y_POSE = 19.5F, WATER_BOB = 0.8F, CENTER_Z = 3.5F;
		/** How far the walk reads as full (walk speed times this, capped at 1) and the pull gait's speed against the walk. */
		static final float WALK_FULL = 1.6F, GAIT = 0.7F;
		/** The head's half height (px), and where the mantle hangs off the head (y, z). */
		static final float HEAD_HALF = 2.5F, MANTLE_Y = -0.4F, MANTLE_Z = 2.0F;
		/** The mantle's pitch on land (radians, + raises its tip): idle, how much walking lowers it, slumped (sneaking, crawling), breath. */
		static final float RAISE_IDLE = 0.8F, RAISE_MOVE = 0.3F, RAISE_SLUMP = 0.12F, RAISE_BREATH = 0.06F;
		/** How far the head may turn (radians), and how far the head hops up between pulls (px). */
		static final float HEAD_TURN = 0.7F, HOP = 0.5F;
		/** Land tentacles: the ripple between neighbours (radians), the rest spread of the inner and outer ones, how far a pull swings them, how far they hook, their lift on the reach (radians). */
		static final float RIPPLE = 0.9F, SPREAD_IN = 0.25F, SPREAD_OUT = 0.6F, PULL = 0.9F, HOOK = 0.5F, REACH_LIFT = 0.45F;
		/** A lash: how far the tentacles lift, how far they straighten to the front (fraction of their spread), the head's lunge (px). */
		static final float LASH_UP = 0.5F, LASH_STRAIGHT = 0.85F, LASH_LUNGE = 2.0F;
		/** Airborne: the tentacles' extra lift and extra spread (radians); they stop lying on the ground. */
		static final float AIR_LIFT = 0.5F, AIR_SPREAD = 0.4F;
		/** The swim pulse: speed against time and against the walk, the share of it spent opening, the tentacles' open spread and hook, the mantle's squeeze, the surge on the jet (px). */
		static final float SWIM_RATE = 0.08F, SWIM_STRIDE = 0.45F, OPEN_SHARE = 0.72F, OPEN_SPREAD = 0.65F, OPEN_HOOK = 0.35F, SQUEEZE = 0.1F, SURGE = 1.2F;
		/** How far the swimming nose follows the head's pitch (fraction, cap in radians), and how wide the fins flap. */
		static final float SWIM_LOOK = 0.8F, SWIM_LOOK_MAX = 1.0F, FIN_FLAP = 0.55F;
		/** The mantle's joints (it has 4): land sway yaw and curl up, swim pitch wave and its lag along the body. */
		private static final float[] WAVE_YAW = {0.08F, 0.16F, 0.22F, 0.3F}, CURL_UP = {0.0F, 0.08F, 0.12F, 0.12F};
		private static final float[] SWIM_PITCH = {0.05F, 0.1F, 0.16F, 0.22F};
		static final float SWIM_LAG = 0.8F;
		private static final int ARMS = 8, SEGS = 3;
		/** A tentacle's segment lengths and how far each hangs inside the one before (px). */
		private static final float[] SEG_LEN = {4.0F, 4.0F, 4.0F};
		static final float JOINT = 0.7F;
		/** Half the thickness of each segment (px), and the gap left between a resting tentacle and the ground (px). */
		private static final float[] SEG_HALF = {1.0F, 0.8F, 0.5F};
		static final float REST = 0.05F;
		/** Where the insignia plate's centre lies along the top of the mantle's first section (px behind its pivot; its front edge stays clear of the head when the mantle is raised). */
		static final float PLATE_Z = 2.5F;
		/** The ring the tentacles hang from on the head's front face: half width and half height (px). */
		static final float RING_RX = 2.6F, RING_RY = 1.6F;
		private static final float[] RING_X = new float[ARMS], RING_Y = new float[ARMS];

		static {
			for (int k = 0; k < ARMS; k++) {
				float a = (k + 0.5F) * (float)Math.PI / 4.0F;
				RING_X[k] = RING_RX * Mth.sin(a);
				RING_Y[k] = -RING_RY * Mth.cos(a);
			}
		}

		private final ModelPart rig, hub;
		private final ModelPart[] mantle = new ModelPart[4];
		/** Right fin, left fin. */
		private final ModelPart[] fins = new ModelPart[2];
		private final ModelPart[][] arms = new ModelPart[ARMS][SEGS];
		private final float[] pitch = new float[SEGS], yaw = new float[SEGS];
		private final org.joml.Quaternionf qa = new org.joml.Quaternionf(), qb = new org.joml.Quaternionf(), qc = new org.joml.Quaternionf();
		private final org.joml.Vector3f ev = new org.joml.Vector3f(), pv = new org.joml.Vector3f();

		Murk() {
			super(bake(mesh()), "murk");
			ModelPart root = this.root();
			this.rig = root.getChild("rig");
			this.hub = this.rig.getChild("hub");
			ModelPart parent = this.hub;
			for (int i = 0; i < 4; i++) {
				this.mantle[i] = parent.getChild("mantle_" + i);
				parent = this.mantle[i];
			}
			this.fins[0] = this.mantle[2].getChild("fin_0");
			this.fins[1] = this.mantle[2].getChild("fin_1");
			for (int k = 0; k < ARMS; k++) {
				ModelPart p = this.hub;
				for (int i = 0; i < SEGS; i++) {
					this.arms[k][i] = p.getChild("arm_" + k + "_" + i);
					p = this.arms[k][i];
				}
			}
			// the plate lies flat on the top of the mantle's first section (the hidden body is laid face-up there each frame)
			this.insigniaY = 0.0F;
			this.insigniaZ = 0.0F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the rig carries the cancel of vanilla's swim pose; everything else hangs off the head (the hub)
			PartDefinition rig = root.addOrReplaceChild("rig", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition hub = rig.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(32, 64).addBox(-3.0F, -2.5F, -2.5F, 6.0F, 5.0F, 5.0F),
				PartPose.offset(0.0F, 21.0F, 0.0F));
			// the beak in the middle of the ring of arms, and the big eyes on the sides of the head
			hub.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(46, 104).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
			hub.addOrReplaceChild("eye_0", CubeListBuilder.create().texOffs(46, 94).addBox(-4.0F, -2.0F, -2.5F, 1.0F, 4.0F, 4.0F), PartPose.ZERO);
			hub.addOrReplaceChild("eye_1", CubeListBuilder.create().texOffs(46, 94).mirror().addBox(3.0F, -2.0F, -2.5F, 1.0F, 4.0F, 4.0F), PartPose.ZERO);
			// the mantle: four sections each hung one pixel inside the end of the one before, tapering to a point
			PartDefinition m0 = hub.addOrReplaceChild("mantle_0", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -3.0F, -2.0F, 7.0F, 6.0F, 8.0F),
				PartPose.offset(0.0F, MANTLE_Y, MANTLE_Z));
			PartDefinition m1 = m0.addOrReplaceChild("mantle_1", CubeListBuilder.create().texOffs(0, 80).addBox(-3.0F, -2.5F, 0.0F, 6.0F, 5.0F, 6.0F),
				PartPose.offset(0.0F, 0.0F, 5.0F));
			PartDefinition m2 = m1.addOrReplaceChild("mantle_2", CubeListBuilder.create().texOffs(26, 80).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 5.0F),
				PartPose.offset(0.0F, 0.0F, 5.0F));
			m2.addOrReplaceChild("mantle_3", CubeListBuilder.create().texOffs(46, 80).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 3.0F),
				PartPose.offset(0.0F, 0.0F, 4.0F));
			// the fins: flat diamonds either side of the mantle's tail, their roots half a pixel inside it
			m2.addOrReplaceChild("fin_0", CubeListBuilder.create().texOffs(0, 94).addBox(-5.0F, -0.5F, 0.0F, 5.0F, 1.0F, 4.0F), PartPose.offset(-1.5F, 0.0F, 0.5F));
			m2.addOrReplaceChild("fin_1", CubeListBuilder.create().texOffs(0, 94).addBox(0.0F, -0.5F, 0.0F, 5.0F, 1.0F, 4.0F), PartPose.offset(1.5F, 0.0F, 0.5F));
			// eight tentacles round the beak, each three joints reaching to -z; a joint hangs inside the end of the one before
			CubeDeformation thin = new CubeDeformation(-0.2F);
			for (int k = 0; k < ARMS; k++) {
				PartDefinition a0 = hub.addOrReplaceChild("arm_" + k + "_0", CubeListBuilder.create().texOffs(20, 94).addBox(-1.0F, -1.0F, -SEG_LEN[0], 2.0F, 2.0F, SEG_LEN[0]),
					PartPose.offset(RING_X[k], RING_Y[k], -2.0F));
				PartDefinition a1 = a0.addOrReplaceChild("arm_" + k + "_1", CubeListBuilder.create().texOffs(20, 94).addBox(-1.0F, -1.0F, -SEG_LEN[1], 2.0F, 2.0F, SEG_LEN[1], thin),
					PartPose.offset(0.0F, 0.0F, -(SEG_LEN[0] - JOINT)));
				a1.addOrReplaceChild("arm_" + k + "_2", CubeListBuilder.create().texOffs(34, 94).addBox(-0.5F, -0.5F, -SEG_LEN[2], 1.0F, 1.0F, SEG_LEN[2]),
					PartPose.offset(0.0F, 0.0F, -(SEG_LEN[1] - JOINT)));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man's parts are all hidden; the hidden body only carries the insignia
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			this.hub.resetPose();
			for (ModelPart part : this.mantle) {
				part.resetPose();
			}
			this.fins[0].resetPose();
			this.fins[1].resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed * WALK_FULL, 0.0F, 1.0F);
			float lash = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float headYaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -HEAD_TURN, HEAD_TURN);
			this.cancelSwim(state);
			if (state.isInWater) {
				this.swim(state, age, speed, lash, headYaw, m);
			} else {
				this.land(state, age, speed, lash, headYaw, m);
			}
			this.plate();
		}

		/**
		 * The renderer lies a swimming player along the swim (a turn of {@code swimAmount * (-90 - pitch)} degrees about the
		 * feet, then down a block and back 0.3, both before the player's 0.9375 scale); the rig undoes that exactly, so the
		 * squid is drawn as if it stood.
		 */
		private void cancelSwim(AvatarRenderState state) {
			float tgt = state.isInWater ? -90.0F - state.xRot : -90.0F;
			float h = state.swimAmount * tgt * Mth.DEG_TO_RAD;
			float vs = state.isVisuallySwimming ? 1.0F : 0.0F;
			this.rig.x = 0.0F;
			this.rig.y = SWIM_PIVOT - SWIM_PIVOT * Mth.cos(h) - SWIM_LIFT * vs;
			this.rig.z = -SWIM_PIVOT * Mth.sin(h) - SWIM_PUSH * vs;
			this.rig.xRot = h;
		}

		/** On land: head low on the ground, the mantle raised or slumped behind, the tentacles pulling along the ground. */
		private void land(AvatarRenderState state, float age, float speed, float lash, float headYaw, XenoMotion.Read m) {
			float phi = state.walkAnimationPos * GAIT;
			float sink = state.isCrouching ? CROUCH_SINK : 0.0F;
			float ground = GROUND - sink;
			boolean flat = state.isCrouching || state.swimAmount > 0.3F;
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			// the mantle: raised when it rests, lower as it moves, flared as it falls, whipped down on landing
			float p = (flat ? RAISE_SLUMP : RAISE_IDLE - RAISE_MOVE * speed) + Mth.sin(age * 0.06F) * RAISE_BREATH + m.breath() * 0.04F
				+ Mth.clamp(tailLift(m), -0.3F, 0.5F);
			p = Mth.clamp(p, 0.05F, 1.25F);
			// the head rests where the mantle's lowest corner meets the ground; it hops a little between pulls
			float bottom = Math.max(HEAD_HALF, MANTLE_Y + 3.0F * Mth.cos(p) + 2.0F * Mth.sin(p));
			float hop = speed * HOP * (0.5F + 0.5F * Mth.sin(phi * 2.0F)) * (1.0F - air);
			float hy = ground - bottom - hop;
			this.hub.x = 0.0F;
			this.hub.y = hy;
			this.hub.z = (flat ? -2.0F : 0.0F) - LASH_LUNGE * lash;
			this.hub.yRot = headYaw;
			float turn = m.trail();
			this.mantle[0].xRot = p;
			for (int j = 0; j < 4; j++) {
				float sway = WAVE_YAW[j] * (speed * Mth.sin(phi - j * 0.8F) + (1.0F - speed) * 0.5F * Mth.sin(age * 0.04F - j * 0.7F));
				this.mantle[j].yRot = sway - turn * 0.12F * (j + 1) - (j == 0 ? headYaw * 0.6F : 0.0F);
				if (j > 0) {
					this.mantle[j].xRot = CURL_UP[j] + Mth.sin(age * 0.05F - j) * 0.03F;
				}
			}
			float fin = 0.12F + 0.1F * speed;
			this.fins[0].zRot = fin * Mth.sin(age * 0.2F);
			this.fins[1].zRot = -this.fins[0].zRot;
			for (int k = 0; k < ARMS; k++) {
				// (0.19.82) a positive yaw swings a forward-pointing arm toward -x, so the +x arms take a negative one to fan out
				float s = RING_X[k] >= 0.0F ? -1.0F : 1.0F;
				float outer = Math.abs(RING_X[k]) / RING_RX;
				int j = k < 4 ? k : 7 - k;
				// the pull: the pairs in antiphase, rippling down each side; a reaching one lifts and narrows, a planted one swings wide
				float u = phi + j * RIPPLE + (k < 4 ? 0.0F : (float)Math.PI);
				float swing = Math.max(0.0F, Mth.sin(u)) * speed;
				float wide = 0.5F + 0.5F * Mth.cos(u);
				float spread = SPREAD_IN + (SPREAD_OUT - SPREAD_IN) * outer + speed * PULL * (wide - 0.5F) + air * AIR_SPREAD;
				spread *= 1.0F - LASH_STRAIGHT * lash;
				spread = Math.max(0.04F, spread);
				float lift = swing * REACH_LIFT + lash * LASH_UP + air * AIR_LIFT;
				// the pitches that lay the chain on the ground from this ring height (the drop spread over the joints, each
				// reaching the ground with its own thickness by the end of its box)
				float y = hy + RING_Y[k];
				for (int i = 0; i < SEGS; i++) {
					float step = SEG_LEN[i] - JOINT;
					float a = (float)Math.asin(Mth.clamp((ground - SEG_HALF[i] - REST - y) / SEG_LEN[i], -0.8F, 0.8F));
					y += step * Mth.sin(a);
					this.pitch[i] = a * (1.0F - air) - lift * (1.0F - 0.4F * i) - (i == 2 ? 0.05F : 0.0F);
				}
				float lazy1 = 0.15F * Mth.sin(age * 0.07F + k * 1.1F) * (1.0F - speed);
				float lazy2 = 0.25F * Mth.sin(age * 0.09F + k * 1.7F) * (1.0F - speed);
				float hook = HOOK * wide * speed * (1.0F - lash);
				this.yaw[0] = s * spread;
				this.yaw[1] = this.yaw[0] + s * hook + lazy1;
				this.yaw[2] = this.yaw[1] + s * hook * 0.7F + lazy2;
				this.aim(this.arms[k]);
			}
		}

		/** In water: level, mantle-first, pitched where it looks; the tentacles open slowly and snap together to jet. */
		private void swim(AvatarRenderState state, float age, float speed, float lash, float headYaw, XenoMotion.Read m) {
			float cyc = age * SWIM_RATE + state.walkAnimationPos * SWIM_STRIDE;
			float x = cyc / (2.0F * (float)Math.PI);
			x -= (float)Math.floor(x);
			// open: eased up over most of the cycle; snap: eased down fast
			float o;
			float jet = 0.0F;
			if (x < OPEN_SHARE) {
				o = smooth(x / OPEN_SHARE);
			} else {
				float f = (x - OPEN_SHARE) / (1.0F - OPEN_SHARE);
				o = 1.0F - smooth(f);
				jet = Mth.sin(f * (float)Math.PI);
			}
			float look = Mth.clamp(-state.xRot * Mth.DEG_TO_RAD * SWIM_LOOK, -SWIM_LOOK_MAX, SWIM_LOOK_MAX) * (0.35F + 0.65F * speed);
			float amp = 0.5F + 0.5F * speed;
			// the middle of the body stays put while it pitches (the head is CENTER_Z behind it, in the mantle's direction)
			float mid = Mth.lerp(state.swimAmount, WATER_Y, WATER_Y_POSE) - (state.isCrouching ? CROUCH_SINK : 0.0F) + Mth.sin(age * 0.06F) * WATER_BOB;
			float push = (SURGE * jet * speed + LASH_LUNGE * lash);
			this.hub.x = 0.0F;
			this.hub.y = mid + CENTER_Z * Mth.sin(look);
			this.hub.z = CENTER_Z * Mth.cos(look) - push;
			this.hub.xRot = look;
			this.hub.yRot = (float)Math.PI + headYaw * 0.3F;
			// the mantle squeezes as the arms snap shut, swells as they open; a wave runs down it
			float squeeze = 1.0F + SQUEEZE * (o - 0.5F) * 2.0F * amp;
			this.mantle[0].xScale = squeeze;
			this.mantle[0].yScale = squeeze;
			for (int j = 0; j < 4; j++) {
				this.mantle[j].xRot = SWIM_PITCH[j] * amp * Mth.sin(cyc - j * SWIM_LAG);
				this.mantle[j].yRot = 0.05F * Mth.sin(age * 0.07F - j * 0.6F) - m.trail() * 0.1F * (j + 1);
			}
			float flap = FIN_FLAP * (0.4F + 0.6F * amp) * Mth.sin(age * 0.3F + state.walkAnimationPos);
			this.fins[0].zRot = flap;
			this.fins[1].zRot = -flap;
			for (int k = 0; k < ARMS; k++) {
				// (0.19.82) a positive yaw swings a forward-pointing arm toward -x, so the +x arms take a negative one to fan out
				float s = RING_X[k] >= 0.0F ? -1.0F : 1.0F;
				float outer = Math.abs(RING_X[k]) / RING_RX;
				// open: spread wide (the outer ones wider) and fanned up and down by the ring; shut: straight back
				float spread = 0.05F + (o + lash) * OPEN_SPREAD * (0.4F + 0.6F * outer);
				float fan = RING_Y[k] / RING_RY * 0.3F * (o + lash);
				float hang = 0.1F * (1.0F - o);
				float hook = OPEN_HOOK * (o + lash);
				float w1 = 0.1F * Mth.sin(age * 0.1F + k * 1.3F);
				float w2 = 0.16F * Mth.sin(age * 0.12F + k * 1.9F);
				this.yaw[0] = s * spread;
				this.yaw[1] = this.yaw[0] + s * hook + w1;
				this.yaw[2] = this.yaw[1] + s * hook * 0.7F + w2;
				this.pitch[0] = fan + hang;
				this.pitch[1] = this.pitch[0] + hang * 1.2F + w1 * 0.5F;
				this.pitch[2] = this.pitch[1] + hang * 1.5F + w2 * 0.5F;
				this.aim(this.arms[k]);
			}
		}

		private static float smooth(float e) {
			e = Mth.clamp(e, 0.0F, 1.0F);
			return e * e * (3.0F - 2.0F * e);
		}

		/**
		 * Poses a chain from the pitch and yaw each joint should have in the head's frame (the yaw is about the head's up axis,
		 * whatever the joints before it have done), so a tentacle can be told to lie flat and bend sideways at once.
		 */
		private void aim(ModelPart[] chain) {
			this.qa.identity();
			for (int i = 0; i < SEGS; i++) {
				this.qb.rotationZYX(0.0F, this.yaw[i], this.pitch[i]);
				this.qc.set(this.qa).conjugate().mul(this.qb);
				this.qc.getEulerAnglesZYX(this.ev);
				chain[i].xRot = this.ev.x;
				chain[i].yRot = this.ev.y;
				chain[i].zRot = this.ev.z;
				this.qa.set(this.qb);
			}
		}

		/** The hidden body, laid face-up on the top of the mantle's first section ({@link #PLATE_Z} along it), tilted and turned with it. */
		private void plate() {
			ModelPart m0 = this.mantle[0];
			// the top of the first section on its centre line, carried up the chain: mantle, head, rig
			this.pv.set(0.0F, -3.0F * m0.yScale, PLATE_Z);
			this.qa.rotationZYX(m0.zRot, m0.yRot, m0.xRot);
			this.qa.transform(this.pv);
			this.pv.add(m0.x, m0.y, m0.z);
			this.qb.rotationZYX(this.hub.zRot, this.hub.yRot, this.hub.xRot);
			this.qb.transform(this.pv);
			this.pv.add(this.hub.x, this.hub.y, this.hub.z);
			this.qc.rotationZYX(0.0F, 0.0F, this.rig.xRot);
			this.qc.transform(this.pv);
			this.pv.add(this.rig.x, this.rig.y, this.rig.z);
			this.qc.mul(this.qb).mul(this.qa).rotateX(-Mth.HALF_PI);
			this.qc.getEulerAnglesZYX(this.ev);
			this.body.x = this.pv.x;
			this.body.y = this.pv.y;
			this.body.z = this.pv.z;
			this.body.xRot = this.ev.x;
			this.body.yRot = this.ev.y;
			this.body.zRot = this.ev.z;
			this.insigniaSize = 0.9F * 0.5F * (m0.xScale + m0.yScale);
		}
	}

	/**
	 * (0.18.111, Scan D) Gnasher: a blunt snout over a toothed jaw, a tall fin on its back, a heavy tail that sweeps.
	 * <p>
	 * (0.19.82) A shark, not a man: a torpedo of three segments (the chest, the belly and the rear, each bending a little
	 * past the one before it) with a blunt skull on the chest and a toothed jaw hinged under it, a tall swept dorsal fin on
	 * the chest, a small one on the rear, two pectoral fins, and a crescent tail (a long upper lobe and a short lower one)
	 * on the end of the rear. The man's parts are all hidden (only the body is still posed, on the chest's back, for the
	 * insignia plate). In the water it swims level, the whole length undulating side to side (a yaw wave down the segments,
	 * bigger towards the tail, faster the faster it moves) and the nose pitched where it looks. On land it drags itself on
	 * its pectoral fins, belly on the ground: the fins take turns to plant and push (each lifting clear while it swings
	 * forward) while the body snakes and the tail flops; standing still the jaw gapes and the tail flops. An attack swing is
	 * a lunge: the body surges forward, the head darts out and tips up, the jaw flung open and snapped shut. A death roll
	 * ({@code XenoformClient.ACT} "roll"), if the server sets it, spins the whole body about its length with the fins
	 * tucked. It is drawn level always: the client holds its swimming pose upright, as it does Billow's.
	 */
	static final class Gnasher extends FormModel {
		// ---- tuning ----
		/** Model y of the ground, of the chest's centre above it (half its height: its belly is flat on the ground), of the chest swimming (half a block up: the middle of the swimming box), how far sneaking sinks it, and how far a rolled shark rides up (so its lowest point, over a whole turn, just touches the ground). */
		static final float GROUND = 24.0F, BELLY = 4.0F, SWIM_Y = 16.0F, CROUCH_SINK = 2.0F, ROLL_LIFT = 4.3F;
		/** Where the chest's pivot sits (model z), so the whole length is centred on the player. */
		static final float HUB_Z = -5.5F;
		/** How far the walk reads as full (walk speed times this, capped at 1). */
		static final float WALK_FULL = 1.6F;
		/** The land crawl: its speed against the walk, the lag between joints, and the idle flop (how much of the wave a standing shark keeps). */
		static final float GAIT = 0.6662F * 1.1F, LAND_LAG = 0.9F, IDLE_FLOP = 0.5F;
		/** The body wave's yaw amplitude (radians) at the chest, belly, rear and tail, on land and swimming. */
		private static final float[] LAND_YAW = {0.10F, 0.18F, 0.30F, 0.42F};
		private static final float[] SWIM_YAW = {0.05F, 0.12F, 0.26F, 0.40F};
		/** Swimming: the lag between joints, the wave's phase per step of the walk and per tick (idle), the least amplitude, how far the nose may pitch. */
		static final float SWIM_LAG = 0.95F, SWIM_STRIDE = 1.1F, SWIM_IDLE = 0.07F, SWIM_MIN = 0.35F, SWIM_LOOK = 0.5F;
		/** Pectoral fins on land: how far swept back at rest, how far the stroke swings, how high a swinging fin lifts (px); in the air their angle; swimming, their drop and sweep; rolled, their sweep and drop. */
		static final float FIN_SWEEP_REST = 0.55F, FIN_SWEEP_STROKE = 0.65F, FIN_LIFT = 1.8F, FIN_AIR = -0.25F;
		static final float SWIM_FIN_DROP = 0.5F, SWIM_FIN_SWEEP = 0.6F, ROLL_FIN_SWEEP = 1.25F, ROLL_FIN_DROP = 0.15F;
		/** A pectoral fin's pivot (x out from the middle, y and z on the chest) and its length. */
		private static final float PEC_X = 3.8F, PEC_Y = 1.5F, PEC_Z = -1.5F, PEC_LEN = 6.0F;
		/** The head: how far it may turn on the chest, how far it nods up and down (radians: never down, lying on its belly its chin is on the ground, and a nod down would only lift the skull over the insignia); a bite: the jaw's gape (radians), how far the body surges and the head darts (px), how far the nose tips up. */
		static final float HEAD_TURN = 0.6F, NOD_UP = 0.4F, NOD_DOWN = 0.0F, JAW_OPEN = 0.8F, LUNGE_BODY = 1.5F, LUNGE_HEAD = 1.5F, LUNGE_TIP = 0.35F;
		/** On land, standing, the jaw gasps between these (radians); kept small, so the skull rises less than a pixel to keep the chin off the ground and never over the insignia. */
		static final float GASP_MIN = 0.06F, GASP_RANGE = 0.06F;
		/** Of a swing (0 to 1), how much is spent opening the jaw and how much snapping it shut. */
		static final float BITE_OPEN = 0.4F, BITE_SNAP = 0.2F;
		/** The dorsal fins' sweep back, and the tail lobes' (upper, lower) sweep (radians). */
		static final float DORSAL_SWEEP = 0.45F, DORSAL2_SWEEP = 0.5F, LOBE_UP_SWEEP = 0.5F, LOBE_LOW_SWEEP = 0.7F;
		/** The death roll's turn speed (radians a tick). */
		static final float ROLL_RATE = 0.45F;
		/** The skull's hinge on the chest (z), the jaw's hinge under the skull (y), the belly's and rear's hinges, the tail's hinge (z), and the belly's drop (so its underside never shares a plane with the chest's). */
		private static final float SKULL_Z = -4.0F, JAW_Y = 2.0F, MID_Z = 4.0F, REAR_Z = 6.5F, TAIL_Z = 6.5F, MID_Y = 0.15F, REAR_Y = -0.05F;
		/** How far the belly and rear boxes are shrunk (so they never share a plane with the segment they overlap). */
		private static final float SHRINK = 0.2F;
		/** Where the dorsal fin stands on the chest's back (y, z), and the rear's small one on the rear (y, z). */
		private static final float FIN_Y = -3.6F, FIN_Z = 3.0F, FIN2_Y = -0.5F, FIN2_Z = 2.0F;
		/** Where the insignia plate lies on the chest's back (z; the hidden body is laid face-up there), clear of the skull's back in front of it and the dorsal fin behind it. */
		private static final float PLATE_Z = 0.0F, CHEST_TOP = -4.0F;
		private static final int JOINTS = 4;
		/** Newton steps to set a planted pectoral fin's lowest corner on the ground. */
		private static final int FIN_SOLVE = 6;

		private final ModelPart fore, skull, jaw, mid, rear, caudal, lobeUp, lobeLow, dorsal, dorsal2;
		/** Right then left. */
		private final ModelPart[] pec = new ModelPart[2];
		/** Each joint's yaw and pitch (chest, belly, rear, tail), relative to the world, not the joint before. */
		private final float[] yaw = new float[JOINTS], pitch = new float[JOINTS];

		Gnasher() {
			super(bake(mesh()), "gnasher");
			ModelPart root = this.root();
			this.fore = root.getChild("fore");
			this.skull = this.fore.getChild("skull");
			this.jaw = this.skull.getChild("jaw");
			this.dorsal = this.fore.getChild("dorsal");
			this.pec[0] = this.fore.getChild("pec_0");
			this.pec[1] = this.fore.getChild("pec_1");
			this.mid = this.fore.getChild("mid");
			this.rear = this.mid.getChild("rear");
			this.dorsal2 = this.rear.getChild("dorsal2");
			this.caudal = this.rear.getChild("caudal");
			this.lobeUp = this.caudal.getChild("lobe_up");
			this.lobeLow = this.caudal.getChild("lobe_low");
			// the plate lies flat on the chest's back (the hidden body is laid face-up there each frame)
			this.insigniaY = 0.0F;
			this.insigniaZ = 0.0F;
			this.insigniaSize = 1.0F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			CubeDeformation shrink = new CubeDeformation(-SHRINK);
			// the chest: 8 wide, 8 tall, 11 long, centred on its pivot; it carries everything in front of and on top of it
			PartDefinition fore = root.addOrReplaceChild("fore", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -4.0F, -5.5F, 8.0F, 8.0F, 11.0F),
				PartPose.offset(0.0F, GROUND - BELLY, HUB_Z));
			// the skull: blunt, 7 wide, reaching back into the chest; the jaw hinged under it at its back
			PartDefinition skull = fore.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(30, 84).addBox(-3.5F, -3.0F, -8.0F, 7.0F, 5.0F, 9.0F),
				PartPose.offset(0.0F, 0.0F, SKULL_Z));
			skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 99).addBox(-3.0F, 0.0F, -7.0F, 6.0F, 2.0F, 7.0F),
				PartPose.offset(0.0F, JAW_Y, 0.0F));
			// the tall dorsal fin on the back half of the chest, swept back
			fore.addOrReplaceChild("dorsal", CubeListBuilder.create().texOffs(26, 99).addBox(-0.5F, -8.0F, 0.0F, 1.0F, 8.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, FIN_Y, FIN_Z, -DORSAL_SWEEP, 0.0F, 0.0F));
			// the pectoral fins: flat paddles on the chest's sides, the left one a mirror of the right
			fore.addOrReplaceChild("pec_0", CubeListBuilder.create().texOffs(0, 111).addBox(-6.0F, -0.5F, -2.0F, 6.0F, 1.0F, 4.0F),
				PartPose.offset(-PEC_X, PEC_Y, PEC_Z));
			fore.addOrReplaceChild("pec_1", CubeListBuilder.create().texOffs(0, 111).mirror().addBox(0.0F, -0.5F, -2.0F, 6.0F, 1.0F, 4.0F),
				PartPose.offset(PEC_X, PEC_Y, PEC_Z));
			// the belly hangs 1.5 px inside the chest's back, the rear 1.5 px inside the belly's, both a hair smaller than what they overlap
			PartDefinition mid = fore.addOrReplaceChild("mid", CubeListBuilder.create().texOffs(0, 84).addBox(-3.5F, -3.0F, 0.0F, 7.0F, 7.0F, 8.0F, shrink),
				PartPose.offset(0.0F, MID_Y, MID_Z));
			PartDefinition rear = mid.addOrReplaceChild("rear", CubeListBuilder.create().texOffs(38, 64).addBox(-2.5F, -1.0F, 0.0F, 5.0F, 5.0F, 8.0F, shrink),
				PartPose.offset(0.0F, REAR_Y, REAR_Z));
			rear.addOrReplaceChild("dorsal2", CubeListBuilder.create().texOffs(38, 77).addBox(-0.5F, -3.0F, 0.0F, 1.0F, 3.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, FIN2_Y, FIN2_Z, -DORSAL2_SWEEP, 0.0F, 0.0F));
			// the tail: a hinge on the rear's end carrying a long upper lobe and a short lower one, both swept back (a crescent)
			PartDefinition caudal = rear.addOrReplaceChild("caudal", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, TAIL_Z));
			caudal.addOrReplaceChild("lobe_up", CubeListBuilder.create().texOffs(36, 99).addBox(-0.5F, -8.0F, -1.0F, 1.0F, 8.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -LOBE_UP_SWEEP, 0.0F, 0.0F));
			caudal.addOrReplaceChild("lobe_low", CubeListBuilder.create().texOffs(46, 77).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 4.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, LOBE_LOW_SWEEP, 0.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man's parts are all hidden; the hidden body only carries the insignia
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			boolean roll = "roll".equals(state.getData(XenoformClient.ACT));
			boolean swim = state.isInWater;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * WALK_FULL);
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			// a bite: the jaw opens over the first part of the swing, then snaps shut and stays shut
			float bite = swing < BITE_OPEN ? swing / BITE_OPEN : Math.max(0.0F, 1.0F - (swing - BITE_OPEN) / BITE_SNAP);
			float lunge = Mth.sin(swing * (float)Math.PI);
			// a sneaking player is drawn CROUCH_SINK lower: the ground, in model y, is that much higher
			float ground = GROUND - (state.isCrouching ? CROUCH_SINK : 0.0F);
			float hubY;
			float phase;
			if (swim) {
				hubY = SWIM_Y + Mth.sin(age * 0.09F) * 0.6F;
				// the nose pitched where it looks (more as it moves), the wave running down the length of it
				float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.7F, -SWIM_LOOK, SWIM_LOOK) * (0.4F + 0.6F * speed);
				phase = age * SWIM_IDLE + state.walkAnimationPos * SWIM_STRIDE;
				float amp = SWIM_MIN + (1.0F - SWIM_MIN) * speed;
				for (int j = 0; j < JOINTS; j++) {
					this.yaw[j] = SWIM_YAW[j] * amp * Mth.sin(phase - j * SWIM_LAG);
					this.pitch[j] = look + 0.03F * j * Mth.sin(age * 0.09F - j);
				}
			} else {
				hubY = ground - BELLY;
				phase = state.walkAnimationPos * GAIT;
				float idle = (1.0F - speed) * IDLE_FLOP;
				// the tail lifts as it falls (never down into the ground)
				float lift = Mth.clamp(tailLift(m), 0.0F, 0.5F);
				for (int j = 0; j < JOINTS; j++) {
					this.yaw[j] = LAND_YAW[j] * (speed * Mth.sin(phase - j * LAND_LAG) + idle * Mth.sin(age * 0.06F - j * 0.8F));
					this.pitch[j] = j >= 2 ? lift * (j - 1) * 0.3F : 0.0F;
				}
			}
			float spin = 0.0F;
			if (roll) {
				// a death roll: the whole body turns about its length, lifted so the fins clear the ground
				spin = age * ROLL_RATE;
				if (!swim) {
					hubY -= ROLL_LIFT;
				}
				for (int j = 0; j < JOINTS; j++) {
					this.yaw[j] = 0.15F * Mth.sin(age * 0.4F - j);
				}
			}
			for (int j = 1; j < JOINTS; j++) {
				this.yaw[j] -= m.trail() * 0.1F * j;
			}
			// the spine: the chest is placed in the world, each joint after it bends by the difference in angle
			// (swimming, the whole animal pitches about its middle, not about the chest)
			float tilt = this.pitch[0];
			this.fore.x = 0.0F;
			this.fore.y = hubY - HUB_Z * Mth.sin(tilt);
			this.fore.z = HUB_Z * Mth.cos(tilt) - lunge * LUNGE_BODY;
			this.fore.xRot = tilt;
			this.fore.yRot = this.yaw[0];
			this.fore.zRot = spin;
			this.mid.xRot = this.pitch[1] - this.pitch[0];
			this.mid.yRot = this.yaw[1] - this.yaw[0];
			this.rear.xRot = this.pitch[2] - this.pitch[1];
			this.rear.yRot = this.yaw[2] - this.yaw[1];
			this.caudal.xRot = this.pitch[3] - this.pitch[2];
			float flex = this.yaw[3] - this.yaw[2];
			this.caudal.yRot = flex;
			// the lobes trail the sweep a little (the upper one more)
			this.lobeUp.yRot = flex * 0.35F;
			this.lobeLow.yRot = flex * 0.2F;
			this.lobeUp.xRot = -LOBE_UP_SWEEP;
			this.lobeLow.xRot = LOBE_LOW_SWEEP;
			this.plate(tilt, spin);
			float gRel = ground - hubY;
			this.fins(m, swim, roll, speed, phase, gRel, age);
			this.face(state, swim, roll, bite, lunge, speed, age, gRel, tilt);
		}

		/** The insignia: the hidden body laid face-up on the chest's back, turned and tilted with the chest. */
		private void plate(float tilt, float spin) {
			float py = CHEST_TOP;
			float pz = PLATE_Z;
			float y1 = py * Mth.cos(tilt) - pz * Mth.sin(tilt);
			float z1 = py * Mth.sin(tilt) + pz * Mth.cos(tilt);
			float yw = this.fore.yRot;
			float x2 = z1 * Mth.sin(yw);
			float z2 = z1 * Mth.cos(yw);
			float x3 = x2 * Mth.cos(spin) - y1 * Mth.sin(spin);
			float y3 = x2 * Mth.sin(spin) + y1 * Mth.cos(spin);
			this.body.x = this.fore.x + x3;
			this.body.y = this.fore.y + y3;
			this.body.z = this.fore.z + z2;
			this.body.xRot = tilt - Mth.HALF_PI;
			this.body.yRot = yw;
			this.body.zRot = spin;
		}

		/** The pectoral fins and the dorsal fins. */
		private void fins(XenoMotion.Read m, boolean swim, boolean roll, float speed, float phase, float gRel, float age) {
			for (int k = 0; k < 2; k++) {
				float s = k == 0 ? 1.0F : -1.0F;
				float sweep;
				float down;
				if (roll) {
					sweep = ROLL_FIN_SWEEP;
					down = ROLL_FIN_DROP;
				} else if (swim) {
					sweep = SWIM_FIN_SWEEP + 0.08F * Mth.sin(age * 0.2F + k * 2.0F);
					down = SWIM_FIN_DROP + s * m.turn() * 0.2F;
				} else {
					// the right fin with the left a half turn behind: planted while it sweeps back (pushing), lifted clear while it swings forward
					float ph = phase + (k == 0 ? 0.0F : (float)Math.PI);
					float step = Math.max(0.0F, -Mth.cos(ph)) * speed;
					sweep = FIN_SWEEP_REST + FIN_SWEEP_STROKE * speed * Mth.sin(ph);
					// the paddle's lowest corner on the ground (up by the lift on the swing)
					float target = gRel - PEC_Y - FIN_LIFT * step;
					down = (float)Math.asin(Mth.clamp((target - 0.5F) / (PEC_LEN * Math.max(0.35F, Mth.cos(sweep))), -0.6F, 0.95F));
					for (int i = 0; i < FIN_SOLVE; i++) {
						down += (target - finDrop(sweep, down)) / Math.max(1.0F, PEC_LEN * Math.max(0.35F, Mth.cos(sweep)) * Mth.cos(down));
					}
					// in the air they spread
					sweep = Mth.lerp(m.air(), sweep, 0.3F);
					down = Mth.lerp(m.air(), down, FIN_AIR);
				}
				this.pec[k].yRot = s * sweep;
				this.pec[k].zRot = -s * down;
			}
			// the back fins lean further back the faster it swims; folded flat-ish for a roll
			float lean = DORSAL_SWEEP + (swim ? 0.15F * speed : 0.0F) + Math.max(0.0F, m.fall()) * 0.05F * m.air();
			this.dorsal.xRot = -lean;
			this.dorsal.yScale = roll ? 0.4F : 1.0F;
			this.dorsal2.xRot = -DORSAL2_SWEEP - (swim ? 0.1F * speed : 0.0F);
		}

		/** The head on the chest, the jaw. */
		private void face(AvatarRenderState state, boolean swim, boolean roll, float bite, float lunge, float speed, float age, float gRel, float tilt) {
			float turn = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -HEAD_TURN, HEAD_TURN);
			float nod;
			if (swim) {
				nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.3F, -0.3F, 0.3F) - tilt * 0.4F;
			} else {
				nod = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.5F, -NOD_UP, NOD_DOWN);
			}
			// a bite: the head darts out and tips up; standing, the jaw gapes and shuts slowly, gasping
			float tip = nod - lunge * LUNGE_TIP;
			float gape = swim ? 0.06F + 0.04F * Mth.sin(age * 0.2F) : (GASP_MIN + GASP_RANGE * (0.5F + 0.5F * Mth.sin(age * 0.11F))) * (1.0F - 0.6F * speed);
			float open = roll ? 0.35F : gape + JAW_OPEN * bite;
			this.skull.x = 0.0F;
			this.skull.z = SKULL_Z - lunge * LUNGE_HEAD;
			this.skull.yRot = turn * (swim ? 0.5F : 1.0F) - this.yaw[0] * 0.5F;
			this.skull.xRot = tip;
			this.jaw.xRot = open;
			float y = 0.0F;
			if (!swim && !roll) {
				// on the ground the head rides up so the open jaw and the tipped snout never dig in
				y = Math.min(0.0F, gRel - chinDrop(tip, open));
			}
			this.skull.y = y;
		}

		/** How far below its pivot a pectoral fin's lowest corner hangs, swept back by {@code sweep} and turned down by {@code down}. */
		private static float finDrop(float sweep, float down) {
			float lowest = -1.0E9F;
			for (int xi = 0; xi < 2; xi++) {
				for (int yi = 0; yi < 2; yi++) {
					for (int zi = 0; zi < 2; zi++) {
						float x = xi == 0 ? 0.0F : -PEC_LEN;
						float y = yi == 0 ? -0.5F : 0.5F;
						float z = zi == 0 ? -2.0F : 2.0F;
						float x1 = x * Mth.cos(sweep) + z * Mth.sin(sweep);
						lowest = Math.max(lowest, -x1 * Mth.sin(down) + y * Mth.cos(down));
					}
				}
			}
			return lowest;
		}

		/** How far below the skull's pivot its lowest point hangs once tipped by {@code tilt} with the jaw turned by {@code open}. */
		private static float chinDrop(float tilt, float open) {
			float low = 2.0F * Mth.cos(tilt) + 8.0F * Mth.sin(tilt);
			for (int zi = 0; zi < 2; zi++) {
				for (int yi = 0; yi < 2; yi++) {
					float zl = zi == 0 ? -7.0F : 0.0F;
					float yl = yi == 0 ? 0.0F : 2.0F;
					float y1 = JAW_Y + yl * Mth.cos(open) - zl * Mth.sin(open);
					float z1 = yl * Mth.sin(open) + zl * Mth.cos(open);
					low = Math.max(low, y1 * Mth.cos(tilt) - z1 * Mth.sin(tilt));
				}
			}
			return low;
		}
	}

	/**
	 * (0.18.111, Scan D) Lumen: small and slight, two thin wing-fins on its back that flutter.
	 * <p>
	 * (0.19.75) An orb of light: no body or limbs. A white-gold core (the head box, 7x7x8, with two crossed boxes to round
	 * its corners) inside a dithered halo shell (its front left clear, so the core and the insignia on its front show),
	 * hovering {@link #HOVER} pixels above the ground and bobbing slowly, with {@link #MOTES} tiny motes orbiting it on
	 * tilted rings. Moving, the motes tighten and stream out behind it (+z), swinging away from its turns and rising as it
	 * falls. An attack swing pulses the orb bigger and bursts the motes outward. The humanoid body and limbs are hidden (the
	 * body is still posed at the core, for the insignia plate). Uses no {@code XenoformClient.ACT}: Lumen has no charge, roll or spin.
	 */
	static final class Lumen extends FormModel {
		// ---- tuning ----
		/** How high the core's centre floats (model pixels up from the ground at y 24: 22 is ~1 block at its 0.7 scale, the halo's underside ~0.7 block up), its bob, the bob's speed, how far it sinks when sneaking. */
		static final float HOVER = 22.0F, BOB = 1.2F, BOB_RATE = 0.07F, CROUCH_DROP = 2.5F;
		/** The core's idle breath (fraction) and how fast, and how much bigger it pulses on an attack swing. */
		static final float BREATH = 0.04F, BREATH_RATE = 0.12F, BURST_CORE = 0.4F;
		/** The halo shell's extra swell on a swing, and how far it tilts as it moves. */
		static final float BURST_SHELL = 0.3F, SHELL_LEAN = 0.15F;
		/** The motes: the number, ring radius (pixels), orbit speed (radians a tick), how far a swing flings them (fraction of the radius). */
		static final int MOTES = 8;
		static final float ORBIT_R = 8.5F, ORBIT_RATE = 0.05F, BURST_MOTES = 1.1F;
		/** Moving: how far behind the longest-trailing mote streams (pixels), how much the rings tighten, a turn's sway, a fall's lift. */
		static final float TRAIL_LEN = 15.0F, TRAIL_TIGHT = 0.4F, TURN_SWING = 5.0F, FALL_RISE = 4.0F;
		/** Each mote's ring: radius scale, tilt about x, roll about z, direction and speed, how much of the stream it takes (0-1). */
		private static final float[] RADIUS = {1.0F, 0.85F, 1.15F, 0.95F, 1.1F, 0.8F, 1.2F, 0.9F};
		private static final float[] TILT = {0.3F, 1.0F, -0.6F, 0.7F, -1.1F, 1.4F, -0.2F, 0.5F};
		private static final float[] ROLL = {0.0F, 0.9F, -0.8F, 1.6F, 0.4F, -1.3F, 1.1F, -0.4F};
		private static final float[] RATE = {1.0F, -0.8F, 1.25F, -1.1F, 0.9F, -1.3F, 0.75F, 1.15F};
		private static final float[] LAG = {0.35F, 0.9F, 0.55F, 1.0F, 0.2F, 0.75F, 0.45F, 0.65F};

		private final ModelPart halo;
		private final ModelPart[] motes = new ModelPart[MOTES];

		Lumen() {
			super(bake(mesh()), "lumen");
			ModelPart root = this.root();
			this.halo = root.getChild("halo");
			for (int i = 0; i < MOTES; i++) {
				this.motes[i] = root.getChild("mote" + i);
			}
			// flush on the front of the core (the head box's front is at z = -4), centred on the 7-pixel face
			this.insigniaY = 0.0F;
			this.insigniaZ = -4.0F;
			this.insigniaSize = 0.95F;
		}

		private static MeshDefinition mesh() {
			// the slim limbs it always had (-0.3), kept for the first-person hand
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the core: the head, 7x7x8 (z the long way), with the crossed 8x7x7 and 7x8x7 boxes on it
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.5F, -4.0F, 7.0F, 7.0F, 8.0F),
				PartPose.offset(0.0F, 24.0F - HOVER, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("core_x", CubeListBuilder.create().texOffs(0, 94).addBox(-4.0F, -3.5F, -3.5F, 8.0F, 7.0F, 7.0F), PartPose.ZERO);
			head.addOrReplaceChild("core_y", CubeListBuilder.create().texOffs(30, 94).addBox(-3.5F, -4.0F, -3.5F, 7.0F, 8.0F, 7.0F), PartPose.ZERO);
			// the halo shell round it (its front face is clear in the skin)
			root.addOrReplaceChild("halo", CubeListBuilder.create().texOffs(0, 70).addBox(-5.5F, -5.5F, -5.5F, 11.0F, 11.0F, 11.0F),
				PartPose.offset(0.0F, 24.0F - HOVER, 0.0F));
			for (int i = 0; i < MOTES; i++) {
				root.addOrReplaceChild("mote" + i, CubeListBuilder.create().texOffs(i < 4 ? 0 : 8, 64)
					.addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.35F)), PartPose.offset(0.0F, 24.0F - HOVER, 0.0F));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.body.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.head.resetPose();
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float burst = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			boolean crouch = state.isCrouching;
			float cy = 24.0F - HOVER + Mth.sin(age * BOB_RATE) * BOB + (crouch ? CROUCH_DROP : 0.0F);
			// the core swells with its breath and a swing
			float pulse = 1.0F + Mth.sin(age * BREATH_RATE) * BREATH + burst * BURST_CORE;
			this.head.y = cy;
			this.head.xScale = this.head.yScale = this.head.zScale = pulse;
			// the body (hidden) sits at the core's centre so the insignia plate lies on its front face
			this.body.y = cy;
			this.insigniaZ = -4.0F * pulse;
			this.insigniaSize = 0.95F * pulse;
			// the halo: breathing a beat behind the core, leaning into its way and wobbling
			float shell = pulse + Mth.sin(age * 0.08F + 1.0F) * 0.04F + burst * BURST_SHELL;
			this.halo.y = cy;
			this.halo.xScale = this.halo.yScale = this.halo.zScale = shell;
			this.halo.xRot = Mth.sin(age * 0.037F) * 0.08F + speed * SHELL_LEAN;
			this.halo.zRot = Mth.sin(age * 0.05F) * 0.08F;
			this.halo.yRot = 0.0F;
			// the motes: each round its own tilted ring, streamed back by the walk
			float tight = (1.0F - TRAIL_TIGHT * speed) * (crouch ? 0.75F : 1.0F) * (1.0F + BURST_MOTES * burst);
			float rise = Math.max(0.0F, m.fall()) * m.air() * FALL_RISE;
			for (int i = 0; i < MOTES; i++) {
				float r = ORBIT_R * RADIUS[i] * tight;
				float ph = age * ORBIT_RATE * RATE[i] + i * (float)Math.PI * 2.0F / MOTES;
				float x0 = r * Mth.cos(ph);
				float z0 = r * Mth.sin(ph);
				// tilt the ring about x, then roll it about z
				float y1 = -z0 * Mth.sin(TILT[i]);
				float z1 = z0 * Mth.cos(TILT[i]);
				float x2 = x0 * Mth.cos(ROLL[i]) - y1 * Mth.sin(ROLL[i]);
				float y2 = x0 * Mth.sin(ROLL[i]) + y1 * Mth.cos(ROLL[i]);
				float lag = LAG[i];
				ModelPart mote = this.motes[i];
				// a turn (+ to its left, +x) swings the stream the other way, as the tails' -trail does
				mote.x = x2 - m.trail() * TURN_SWING * lag;
				mote.y = cy + y2 + speed * lag * 1.5F - rise * lag;
				mote.z = z1 + TRAIL_LEN * speed * lag;
				float twinkle = (0.75F + Mth.sin(age * 0.3F + i * 2.1F) * 0.35F) * (1.0F + burst * 0.5F);
				mote.xScale = mote.yScale = mote.zScale = twinkle;
			}
		}
	}

	/**
	 * (0.18.111, Scan D; 0.19.75 a real bat) Keen: a small furry bat, not a man: a round fur body with the insignia on
	 * its back, a pug head with huge ears, a jaw and fangs, two hind legs and two long membrane wings (a humerus, a
	 * long forearm, three finger bones fanned from the wrist, a thumb claw, and the membrane stretched between them).
	 * On the ground the wings fold up against it and it crawls on its wrists and feet (a diagonal gait, the arms
	 * solved so each wrist plants on the ground and the elbow rides up behind it); in the air the wings spread and beat
	 * fast (FLY_WINGS), folding on the upstroke and sweeping back at speed, or hold out and ride the air (FLY_GLIDE);
	 * under Wind's cruise pose the model is turned back level (the body is laid along the flight), and gliding is turned
	 * back too. While it hangs ({@link XenoformClient#ACT} "hang") it hangs head-down by its feet from the ceiling, turned
	 * belly to the front with the wings drawn across it like a cloak, swaying. A bite lunges the head, opens the jaw and
	 * lays the ears back. The man's parts are hidden; the body is the bat's torso, laid face-up so the insignia sits on
	 * its back, and every other part hangs off it.
	 */
	static final class Keen extends FormModel {
		// ---- tuning ----
		/** The body's height (model y; the ground is 24) crawling and flying, how far it drops when sneaking, and what the renderer sinks a crouching player. */
		static final float CRAWL_Y = 17.0F, FLY_Y = 10.5F, CROUCH_DROP = 2.5F, CROUCH_SINK = 2.0F;
		/** The middle of the player box (model y), which Wind's cruise pose and the glide tip the whole model about. */
		static final float MIDDLE_Y = 9.6F;
		/** The crawl: how fast the limbs step against the walk, how far each wrist and foot swings and lifts (pixels), how much the body bobs. */
		static final float GAIT = 0.6662F * 1.3F, FORE_STRIDE = 2.6F, HIND_STRIDE = 2.2F, FORE_LIFT = 2.2F, HIND_LIFT = 1.6F, BOB = 0.5F;
		/** Where a planted wrist rests (body space: out from the middle, and ahead of it) and its radius, and how much wider the wrists go when it sneaks. */
		static final float WRIST_X = 4.6F, WRIST_Y = 5.2F, WRIST_R = 1.7F, CROUCH_WIDEN = 1.6F;
		/** How far the hind feet splay out at rest and at most (radians), and a pad under the foot (pixels) so its sole sits on the ground. */
		static final float SPLAY = 0.22F, SPLAY_MAX = 0.5F, FOOT_PAD = 0.25F;
		/** The wing beat: radians a tick, the stroke and the mean raise (radians); the glide's slow dip likewise. */
		static final float FLAP_RATE = 1.9F, FLAP_AMP = 0.75F, FLAP_UP = 0.12F, GLIDE_RATE = 0.16F, GLIDE_AMP = 0.10F, GLIDE_UP = 0.22F;
		/** Pitch of the body hovering (negative is nose up) and cruising, how far it banks into a turn, and how far the wings spread when it falls. */
		static final float HOVER_PITCH = -0.5F, CRUISE_PITCH = 0.1F, BANK = 0.3F, AIR_SPREAD = 0.6F;
		/**
		 * How much of the glide's tip ({@link XenoformFlightPose#GLIDE_LEAN}) is turned back out of the model (1 level the
		 * bat out, 0 leave it to the renderer). The renderer turns the model by +38 degrees about the X axis, which leans the
		 * model back; flip this to -1 if it is seen to lean forward in game.
		 */
		static final float GLIDE_COUNTER = 1.0F;
		/** A bite lunges the body this far (pixels), and the head this much further out of the torso (kept under its 0.5 pixel overlap so it stays on). */
		static final float LUNGE = 2.0F, NECK_LUNGE = 0.4F;
		/**
		 * Hanging: the ceiling (model y: the top of the player box, standing 1.8 and crouching 1.5 tall, drawn at
		 * AvatarRenderer's 0.9375; the server holds the bat flush against it), how far the body hangs below the grip of its
		 * feet, the sway (radians) and how far the belly sheets reach in.
		 */
		static final float CEILING_Y = 24.0F - 1.8F * 16.0F / 0.9375F, CEILING_CROUCH_Y = 24.0F - 1.5F * 16.0F / 0.9375F, HANG_REACH = 7.7F, HANG_SWAY = 0.07F, HANG_SHEET = 6.2F;
		/** Hanging: how far each wing's sheet sits in from its root (pixels, toward the belly) so it lies on the belly, not a hair off it. */
		static final float HANG_SHEET_IN = 0.65F;
		/** Wind's air brake leans the body back this much (degrees); WindPose keeps it private. */
		static final float BRAKE_LEAN_BACK = 25.0F;

		private static final float HALF_PI = (float)Math.PI * 0.5F;
		private static final float DEG = Mth.DEG_TO_RAD;
		/** The skeleton (pixels): the shoulder on the torso's side, the humerus, forearm and three fingers. */
		private static final float SHOULDER_X = 3.0F, SHOULDER_Y = 1.6F, SHOULDER_Z = -1.0F, HUMERUS = 5.0F, FOREARM = 9.0F;
		private static final float[] FINGER = {10.0F, 9.0F, 7.0F};
		/** Hips (body space) and the leg's length hip to sole. */
		private static final float HIP_X = 2.2F, HIP_Y = -2.4F, HIP_Z = 1.5F, LEG = 5.5F;
		/** The neck's joint on the torso's head end. */
		private static final float NECK_Y = 3.0F, NECK_Z = -0.2F;
		/** The wing's pose at the far ends: spread (finger angles from the forearm) and folded on the ground. */
		private static final float[] FINGER_SPREAD = {-0.12F, -0.55F, -1.0F};
		private static final float[] FINGER_FOLD = {-2.99F, -2.87F, -2.75F};
		private static final CubeDeformation THIN = new CubeDeformation(0.0F, 0.0F, -0.25F);
		private static final CubeDeformation BONE = new CubeDeformation(0.1F);

		private final ModelPart batHead, jaw, web;
		private final ModelPart[] ears = new ModelPart[2], legs = new ModelPart[2], feet = new ModelPart[2];
		/** Index 0 right, 1 left. */
		private final ModelPart[] shoulder = new ModelPart[2], arm = new ModelPart[2], fore = new ModelPart[2], thumb = new ModelPart[2];
		private final ModelPart[] webIn = new ModelPart[2], webA = new ModelPart[2], webB = new ModelPart[2];
		private final ModelPart[][] fingers = new ModelPart[2][3];
		/** Scratch: the wing angles being solved (alpha, beta, A, F, g1, g2, g3): the crawl's (right, left), the flight's, the hang's, and the blend drawn. */
		private final float[][] wingGround = new float[2][7];
		private final float[] wingFly = new float[7], wingHang = new float[7], wingNow = new float[7];

		Keen() {
			super(bake(mesh()), "keen");
			ModelPart torso = this.body;
			this.batHead = torso.getChild("bat_head");
			this.jaw = this.batHead.getChild("jaw");
			this.web = torso.getChild("web");
			for (int s = 0; s < 2; s++) {
				String p = s == 1 ? "left_" : "right_";
				this.ears[s] = this.batHead.getChild(p + "ear");
				this.legs[s] = torso.getChild(p + "hind");
				this.feet[s] = this.legs[s].getChild("foot");
				this.shoulder[s] = torso.getChild(p + "shoulder");
				this.webIn[s] = this.shoulder[s].getChild("web_in");
				this.arm[s] = this.shoulder[s].getChild("arm");
				this.fore[s] = this.arm[s].getChild("fore");
				ModelPart knob = this.fore[s].getChild("knob");
				this.thumb[s] = knob.getChild("thumb");
				for (int i = 0; i < 3; i++) {
					this.fingers[s][i] = this.fore[s].getChild("finger" + i);
				}
				this.webA[s] = this.fore[s].getChild("web_a");
				this.webB[s] = this.fore[s].getChild("web_b");
			}
			// the plate lies on the torso's back (the body is laid face-up, see setupAnim)
			this.insigniaY = -0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 1.0F;
		}

		/** A box that is on the left (+x) side as given, or mirrored on the right (its texture too). */
		private static CubeListBuilder cube(boolean left, int u, int v, float x, float y, float z, int w, int h, int d, CubeDeformation grow) {
			CubeListBuilder builder = CubeListBuilder.create().texOffs(u, v);
			return left ? builder.addBox(x, y, z, w, h, d, grow) : builder.mirror().addBox(-x - w, y, z, w, h, d, grow);
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the torso replaces the man's body; lying face-up (-90 degrees), its local +y is forward and +z is down
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.5F, -2.5F, 6.0F, 7.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, CRAWL_Y, 0.0F, -HALF_PI, 0.0F, 0.0F));
			// the head sits in the man's frame (its face on -z) turned a quarter to face forward along the spine
			PartDefinition head = body.addOrReplaceChild("bat_head", CubeListBuilder.create().texOffs(22, 64).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 3.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, NECK_Y, NECK_Z, HALF_PI, 0.0F, 0.0F));
			head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(40, 64).addBox(-1.5F, -1.0F, -5.0F, 3.0F, 2.0F, 1.0F), PartPose.ZERO);
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(48, 64).addBox(-2.0F, 0.0F, -3.0F, 4.0F, 1.0F, 3.0F),
				PartPose.offset(0.0F, 1.0F, -0.6F));
			head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(8, 76).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offset(-1.4F, 1.0F, -3.5F));
			head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(8, 76).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offset(1.4F, 1.0F, -3.5F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 76).addBox(-1.5F, -6.0F, -0.5F, 3.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(-1.4F, -1.8F, -1.2F, 0.1F, 0.0F, -0.25F));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(0, 76).mirror().addBox(-1.5F, -6.0F, -0.5F, 3.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(1.4F, -1.8F, -1.2F, 0.1F, 0.0F, 0.25F));
			// the tail membrane between the hind legs, hinged at the torso's tail end
			body.addOrReplaceChild("web", CubeListBuilder.create().texOffs(0, 90).addBox(-3.5F, -5.0F, -0.5F, 7.0F, 5.0F, 1.0F, THIN),
				PartPose.offset(0.0F, -3.0F, 1.0F));
			for (int s = 0; s < 2; s++) {
				boolean left = s == 1;
				String p = left ? "left_" : "right_";
				float side = left ? 1.0F : -1.0F;
				// a hind leg hangs off the hip along +y turned a quarter (so it points down), a flat foot at its end
				PartDefinition leg = body.addOrReplaceChild(p + "hind", cube(left, 12, 76, -1.0F, 0.0F, -1.0F, 2, 5, 2, CubeDeformation.NONE),
					PartPose.offsetAndRotation(side * HIP_X, HIP_Y, HIP_Z, HALF_PI, 0.0F, 0.0F));
				leg.addOrReplaceChild("foot", cube(left, 20, 76, -1.0F, -0.5F, -3.0F, 2, 1, 3, CubeDeformation.NONE), PartPose.offset(0.0F, 5.0F, 0.0F));
				// a wing: a shoulder joint (roll and twist), the humerus, the forearm, a wrist knob with a thumb, three fingers
				PartDefinition sh = body.addOrReplaceChild(p + "shoulder", CubeListBuilder.create(), PartPose.offset(side * SHOULDER_X, SHOULDER_Y, SHOULDER_Z));
				sh.addOrReplaceChild("web_in", cube(left, 16, 90, 0.0F, -8.5F, -0.5F, 16, 9, 1, THIN), PartPose.offset(0.0F, 0.0F, 0.05F));
				PartDefinition upper = sh.addOrReplaceChild("arm", cube(left, 30, 76, -1.0F, -1.0F, -1.0F, 6, 2, 2, CubeDeformation.NONE), PartPose.ZERO);
				PartDefinition lower = upper.addOrReplaceChild("fore", cube(left, 0, 84, -1.0F, -0.5F, -0.5F, 11, 1, 1, CubeDeformation.NONE),
					PartPose.offset(side * HUMERUS, 0.0F, 0.0F));
				PartDefinition knob = lower.addOrReplaceChild("knob", cube(left, 40, 86, -1.0F, -1.0F, -1.0F, 2, 2, 2, CubeDeformation.NONE),
					PartPose.offset(side * FOREARM, 0.0F, 0.0F));
				knob.addOrReplaceChild("thumb", CubeListBuilder.create().texOffs(48, 84).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
				int[][] fingerUv = {{24, 84, 11}, {0, 86, 10}, {22, 86, 8}};
				for (int i = 0; i < 3; i++) {
					lower.addOrReplaceChild("finger" + i, cube(left, fingerUv[i][0], fingerUv[i][1], -0.5F, -0.5F, -0.5F, fingerUv[i][2], 1, 1, BONE),
						PartPose.offset(side * FOREARM, 0.0F, 0.0F));
				}
				lower.addOrReplaceChild("web_a", cube(left, 0, 100, 0.0F, -3.0F, -0.5F, 10, 6, 1, THIN), PartPose.offset(side * FOREARM, 0.0F, 0.12F));
				lower.addOrReplaceChild("web_b", cube(left, 22, 100, 0.0F, -3.0F, -0.5F, 9, 6, 1, THIN), PartPose.offset(side * FOREARM, 0.0F, -0.12F));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.visible = true;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			boolean hang = "hang".equals(state.getData(XenoformClient.ACT));
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			Float windAmount = state.getData(com.jaymospowers.client.power.wind.WindPose.KEY);
			Float brakeAmount = state.getData(com.jaymospowers.client.power.wind.WindPose.BRAKE_KEY);
			float cruise = hang || windAmount == null ? 0.0F : windAmount;
			float wings = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
			float glide = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.glide());
			float fold = flight == null ? 0.0F : flight.fold();
			float time = flight == null ? age : flight.time();
			// how far it is airborne (the body lifts and the legs tuck) and how far the wings are spread
			float fly = hang ? 0.0F : Math.max(wings, glide);
			float spread = hang ? 0.0F : Mth.clamp(Math.max(fly, m.air() * AIR_SPREAD), 0.0F, 1.0F);
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			float turn = m.turn();
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// under Wind's cruise the renderer turns the whole model to the look (WindPose.tilt), so the head's own turn and nod fade out
			float lookYaw = Mth.clamp(state.yRot * DEG, -1.1F, 1.1F) * (1.0F - cruise);
			float lookPitch = Mth.clamp(state.xRot * DEG, -0.9F, 0.9F) * (1.0F - cruise);
			float sinkFix = state.isCrouching ? -CROUCH_SINK : 0.0F;
			// ---- the body ----
			float crouch = state.isCrouching && !hang ? CROUCH_DROP * (1.0F - fly) : 0.0F;
			float bob = Mth.abs(Mth.sin(walk)) * BOB * speed * (1.0F - fly);
			float groundBodyY = CRAWL_Y + crouch - bob;
			float hover = Mth.sin(time * 0.21F) * 0.5F * fly;
			float bodyY = Mth.lerp(fly, groundBodyY, FLY_Y + hover);
			float flyPitch = Mth.lerp(Math.min(1.0F, speed * 1.6F), HOVER_PITCH, CRUISE_PITCH) + m.fall() * 0.3F * m.air();
			float pitch = (hang ? 0.0F : flyPitch * fly) * (1.0F - cruise);
			// Wind's cruise tips the whole model by (90 + look pitch) degrees about the player box's middle (a man laid out
			// along the flight), and a glide by a few more. The body is turned back by the 90 degrees (and the glide), so it
			// lies along the flight; the look pitch it keeps, so it dives and climbs where it looks. tilt is what the
			// renderer does (its position is moved to match), turnBack the part undone.
			float tilt = 0.0F, turnBack = 0.0F;
			if (cruise > 0.001F) {
				float brake = brakeAmount == null ? 0.0F : brakeAmount;
				float laid = cruise * (1.0F - 0.75F * brake);
				tilt += laid * (90.0F + state.xRot) * DEG - brake * BRAKE_LEAN_BACK * DEG;
				turnBack += laid * 90.0F * DEG;
			}
			if (!hang && glide > 0.001F && state.deathTime <= 0.0F) {
				float lean = -XenoformFlightPose.GLIDE_LEAN * glide * DEG * GLIDE_COUNTER;
				tilt += lean;
				turnBack += lean;
			}
			float forwardY = Mth.sin(pitch), forwardZ = -Mth.cos(pitch);
			float wantY = bodyY + sinkFix + forwardY * bite * LUNGE;
			float wantZ = forwardZ * bite * LUNGE;
			float cs = Mth.cos(tilt), sn = Mth.sin(tilt);
			this.body.x = 0.0F;
			this.body.y = MIDDLE_Y + (wantY - MIDDLE_Y) * cs + wantZ * sn;
			this.body.z = -(wantY - MIDDLE_Y) * sn + wantZ * cs;
			this.body.xRot = -HALF_PI + pitch - turnBack;
			this.body.yRot = 0.0F;
			// rolled into a turn (under Wind's cruise it banks the model itself)
			this.body.zRot = hang || cruise > 0.001F ? 0.0F : -turn * BANK * spread + Mth.sin(walk) * 0.05F * speed * (1.0F - fly);
			if (hang) {
				// head-down by its feet, belly to the front (turned half round), swaying about its grip on the ceiling
				float sway = Mth.sin(age * 0.045F) * HANG_SWAY;
				this.body.xRot = sway;
				this.body.yRot = (float)Math.PI;
				this.body.y = (state.isCrouching ? CEILING_CROUCH_Y : CEILING_Y) + HANG_REACH * Mth.cos(sway) + sinkFix;
				this.body.z = -HANG_REACH * Mth.sin(sway);
			}
			// ---- the head ----
			float headPitch;
			float neckY = NECK_Y + bite * NECK_LUNGE;
			float neckZ = NECK_Z;
			if (hang) {
				// the face turned out to the front (the belly's side) from the hanging line of the body; hanging head-down,
				// a smaller angle turns the face further down, so looking down takes from it
				neckY = NECK_Y + 0.6F;
				neckZ = 0.4F;
				headPitch = HALF_PI + 0.9F - lookPitch * 0.4F;
			} else {
				float ground = HALF_PI - 0.3F + lookPitch * 0.7F + bite * 0.2F + Mth.sin(age * 0.07F) * 0.03F;
				float air = HALF_PI + 0.02F + lookPitch + Mth.sin(time * FLAP_RATE + 0.8F) * 0.05F * fly;
				headPitch = Mth.lerp(fly, ground, air);
			}
			this.batHead.x = 0.0F;
			this.batHead.y = neckY;
			this.batHead.z = neckZ;
			this.batHead.xRot = headPitch;
			// turned where it looks: about the body's down axis, or hanging head-down, about the vertical
			this.batHead.zRot = hang ? 0.0F : lookYaw;
			this.batHead.yRot = hang ? lookYaw : 0.0F;
			this.jaw.xRot = 0.12F + bite * 0.9F + (hang ? 0.0F : Math.max(0.0F, Mth.sin(age * 0.05F)) * 0.04F);
			for (int s = 0; s < 2; s++) {
				float side = s == 1 ? 1.0F : -1.0F;
				float twitch = Mth.sin(age * 0.19F + s * 2.3F) * (0.05F + (1.0F - speed) * 0.03F);
				// ears: pricked at rest, laid back flying and biting, drooped hanging
				float back = bite * 0.6F + fly * 0.35F + cruise * 0.3F + (hang ? 0.9F : 0.0F);
				this.ears[s].xRot = 0.1F - back + twitch;
				this.ears[s].zRot = side * (0.25F + twitch - bite * 0.1F);
			}
			// ---- the tail membrane ----
			this.web.xRot = (hang ? 0.5F : Mth.lerp(fly, -0.1F, -0.3F + Mth.sin(time * FLAP_RATE) * 0.12F * wings)) + Math.max(-0.4F, tailLift(m)) * 0.8F;
			// ---- the hind legs ----
			float groundZ = 24.0F - groundBodyY;
			for (int s = 0; s < 2; s++) {
				float side = s == 1 ? 1.0F : -1.0F;
				ModelPart leg = this.legs[s];
				ModelPart foot = this.feet[s];
				if (hang) {
					leg.xRot = (float)Math.PI - 0.12F + Mth.sin(age * 0.045F + s) * 0.02F;
					leg.yRot = side * 0.35F;
					leg.yScale = 1.0F;
					foot.xRot = -0.3F;
					continue;
				}
				// the right hind steps with the left wrist: diagonal pairs
				float psi = walk + (s == 1 ? (float)Math.PI : 0.0F);
				float p = HIND_STRIDE * speed * Mth.sin(psi);
				float lift = HIND_LIFT * speed * Math.max(0.0F, Mth.cos(psi));
				float delta = -(float)Math.asin(Mth.clamp(p / LEG, -0.9F, 0.9F));
				float hv = groundZ - HIP_Z - lift - FOOT_PAD;
				float cd = LEG * Mth.cos(delta);
				float minCos = Mth.cos(SPLAY);
				// a lifted or lowered hip is met by splaying the foot out, and past that by shortening the leg; the foot's
				// outer edge dips as it rolls out, so it is solved twice, the second time a foot's half width higher
				float splay = SPLAY;
				float ratio = hv / cd;
				for (int pass = 0; pass < 2; pass++) {
					ratio = (hv - (pass == 0 ? 0.0F : Mth.sin(splay))) / cd;
					splay = ratio >= minCos ? SPLAY : Math.min(SPLAY_MAX, (float)Math.acos(Math.max(0.0F, ratio)));
				}
				float stretch = ratio / Mth.cos(splay);
				float groundAngle = HALF_PI + delta;
				float flightAngle = 2.55F + Mth.sin(time * FLAP_RATE + 0.5F + s) * 0.1F * wings + m.trail() * 0.1F;
				leg.xRot = Mth.lerp(fly, groundAngle, flightAngle);
				leg.yRot = side * Mth.lerp(fly, splay, 0.3F);
				leg.yScale = Mth.lerp(fly, stretch, 1.0F);
				foot.xRot = Mth.lerp(fly, -delta - lift * 0.15F, 0.5F);
			}
			// ---- the wings ----
			for (int s = 0; s < 2; s++) {
				this.solveGround(s, walk, speed, groundZ, crouch);
			}
			this.solveFlight(time, wings, fold, cruise);
			this.solveHang(age);
			for (int s = 0; s < 2; s++) {
				float[] w = this.wingNow;
				for (int i = 0; i < 7; i++) {
					w[i] = hang ? this.wingHang[i] : Mth.lerp(spread, this.wingGround[s][i], this.wingFly[i]);
				}
				// banking: the inside wing drops, the outside lifts
				if (!hang) {
					w[1] += (s == 1 ? -turn : turn) * BANK * spread;
				}
				float side = s == 1 ? 1.0F : -1.0F;
				// hanging, the wing's root sits under the belly (the second wing a little lower so the two sheets never share a plane)
				this.shoulder[s].x = side * (hang ? 3.4F : SHOULDER_X);
				this.shoulder[s].y = hang ? 2.6F : SHOULDER_Y;
				this.shoulder[s].z = hang ? (s == 1 ? 3.2F : 3.55F) : SHOULDER_Z;
				this.placeWing(s, w, hang ? 0.0F : spread, hang);
			}
		}

		/** The crawl: a wing reaching to its planted (or stepping) wrist, its fingers folded back along the forearm. */
		private void solveGround(int s, float walk, float speed, float groundZ, float crouch) {
			// diagonal pairs: the left wrist steps with the right hind foot; both sides are solved as a left wing
			float psi = walk + (s == 1 ? 0.0F : (float)Math.PI);
			float tx = WRIST_X + crouch * (CROUCH_WIDEN / CROUCH_DROP) - SHOULDER_X;
			float ty = WRIST_Y + FORE_STRIDE * speed * Mth.sin(psi) - SHOULDER_Y;
			float lift = FORE_LIFT * speed * Math.max(0.0F, Mth.cos(psi));
			float tz = groundZ - WRIST_R - lift - SHOULDER_Z;
			float[] w = this.wingGround[s];
			reach(tx, ty, tz, 0.4F, -0.7F, -0.6F, w);
			for (int i = 0; i < 3; i++) {
				w[4 + i] = FINGER_FOLD[i];
			}
		}

		/** In the air: the beat (the wing lifts and drops, folding at the elbow and closing its fingers as it rises), swept back at speed. */
		private void solveFlight(float time, float wings, float fold, float cruise) {
			float rate = Mth.lerp(wings, GLIDE_RATE, FLAP_RATE);
			float amp = Mth.lerp(wings, GLIDE_AMP, FLAP_AMP);
			float up = Mth.lerp(wings, GLIDE_UP, FLAP_UP);
			float theta = time * rate;
			float flex = wings * Math.max(0.0F, Mth.cos(theta)) * 0.9F;
			float[] w = this.wingFly;
			w[0] = 0.1F + 0.2F * Mth.cos(theta) * wings;
			w[1] = up + amp * Mth.sin(theta);
			w[2] = 0.2F - fold * 0.7F - cruise * 0.1F;
			w[3] = -0.2F - flex - fold * 0.5F;
			float close = 1.0F - 0.45F * flex - 0.4F * fold;
			for (int i = 0; i < 3; i++) {
				w[4 + i] = FINGER_SPREAD[i] * close;
			}
		}

		/**
		 * Hanging: both wings drawn across the belly like a cloak: the humerus reaches in across the chest, the forearm
		 * runs back along the belly toward the feet, the fingers fold forward along it, the membrane lying under the belly.
		 */
		private void solveHang(float age) {
			float[] w = this.wingHang;
			float loose = Mth.sin(age * 0.04F) * 0.03F;
			w[0] = 0.0F;
			w[1] = loose;
			w[2] = (float)Math.PI;
			w[3] = HALF_PI + loose;
			for (int i = 0; i < 3; i++) {
				w[4 + i] = FINGER_FOLD[i];
			}
		}

		/**
		 * Poses wing {@code s} from its canonical (left wing) angles: the shoulder's twist and roll, the humerus' sweep, the
		 * elbow's bend and each finger's fan, then sizes the membranes to the shape the bones make, and points the thumb forward.
		 */
		private void placeWing(int s, float[] w, float spread, boolean hang) {
			float side = s == 1 ? 1.0F : -1.0F;
			float alpha = w[0], beta = w[1], a = w[2], f = w[3];
			this.shoulder[s].xRot = alpha;
			this.shoulder[s].yRot = side * beta;
			this.shoulder[s].zRot = 0.0F;
			this.arm[s].zRot = side * a;
			this.fore[s].zRot = side * f;
			float phi = a + f;
			// the bones in the wing's plane (u out along the shoulder's axis, v forward): the wrist and the finger tips
			float wu = HUMERUS * Mth.cos(a) + FOREARM * Mth.cos(phi);
			float wv = HUMERUS * Mth.sin(a) + FOREARM * Mth.sin(phi);
			for (int i = 0; i < 3; i++) {
				this.fingers[s][i].zRot = side * w[4 + i];
			}
			float tip2U = wu + FINGER[2] * Mth.cos(phi + w[6]);
			float tip1V = wv + FINGER[1] * Mth.sin(phi + w[5]), tip2V = wv + FINGER[2] * Mth.sin(phi + w[6]);
			// the membrane from the body out to the wrist, as deep as the trailing finger reaches back
			float reachU = Math.max(0.5F, Math.max(wu, tip2U) * 0.97F);
			float depth = Mth.clamp(1.5F - Math.min(tip1V, tip2V), 0.5F, 8.5F);
			if (hang) {
				// the sheet lies on the belly, reaching in across it (the membrane's box shifted in, not turned)
				this.webIn[s].x = -side * HANG_SHEET;
				this.webIn[s].z = 0.05F - HANG_SHEET_IN;
				this.webIn[s].xScale = HANG_SHEET / 16.0F;
				this.webIn[s].yScale = 1.0F;
				this.webIn[s].zRot = 0.0F;
			} else {
				this.webIn[s].x = 0.0F;
				this.webIn[s].z = 0.05F;
				this.webIn[s].xScale = Mth.lerp(spread, 0.4F, reachU / 16.0F);
				this.webIn[s].yScale = Mth.lerp(spread, 0.55F, depth / 8.5F);
				this.webIn[s].zRot = side * a * 0.4F;
			}
			// the panels between the fingers: along the bisector, as wide as the gap
			float gapA = Math.abs(w[4] - w[5]), gapB = Math.abs(w[5] - w[6]);
			this.webA[s].zRot = side * (w[4] + w[5]) * 0.5F;
			this.webB[s].zRot = side * (w[5] + w[6]) * 0.5F;
			this.webA[s].xScale = (FINGER[0] + FINGER[1]) * 0.5F * 0.92F / 10.0F;
			this.webB[s].xScale = (FINGER[1] + FINGER[2]) * 0.5F * 0.92F / 9.0F;
			this.webA[s].yScale = Math.max(0.04F, (FINGER[0] + FINGER[1]) * 0.5F * gapA * 0.65F / 6.0F);
			this.webB[s].yScale = Math.max(0.04F, (FINGER[1] + FINGER[2]) * 0.5F * gapB * 0.65F / 6.0F);
			// the thumb points ahead of the body whichever way the forearm lies: its direction in the forearm's own frame
			float ca = Mth.cos(alpha), sa = Mth.sin(alpha), cb = Mth.cos(beta), sb = Mth.sin(beta);
			float e1x = cb, e1y = 0.0F, e1z = -sb;
			float e3x = ca * sb, e3y = -sa, e3z = ca * cb;
			float e2x = e3y * e1z - e3z * e1y, e2y = e3z * e1x - e3x * e1z, e2z = e3x * e1y - e3y * e1x;
			float cp = Mth.cos(phi), sp = Mth.sin(phi);
			float fxx = cp * e1x + sp * e2x, fxy = cp * e1y + sp * e2y, fxz = cp * e1z + sp * e2z;
			float fyx = -sp * e1x + cp * e2x, fyy = -sp * e1y + cp * e2y, fyz = -sp * e1z + cp * e2z;
			// ahead and a little down
			float dx = 0.0F, dy = 0.96F, dz = 0.29F;
			float ux = dx * fxx + dy * fxy + dz * fxz;
			float uy = dx * fyx + dy * fyy + dz * fyz;
			float uz = dx * e3x + dy * e3y + dz * e3z;
			this.thumb[s].xRot = (float)Math.asin(Mth.clamp(uz, -1.0F, 1.0F));
			this.thumb[s].zRot = side * (float)Math.atan2(-ux, uy);
		}

		/**
		 * Solves a left wing's joint angles to put the wrist at (tx, ty, tz) from the shoulder (body space), the elbow
		 * toward the pole (px, py, pz). out = alpha (the shoulder's twist), beta (its roll), A (the humerus' sweep in the
		 * wing's plane), F (the elbow's bend). The wing's plane is the one through the shoulder, the wrist and the pole,
		 * which always contains a line across the body, the one the shoulder's twist and roll can lay the wing along.
		 */
		private static void reach(float tx, float ty, float tz, float px, float py, float pz, float[] out) {
			float d = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
			float dc = Mth.clamp(d, Math.abs(HUMERUS - FOREARM) + 0.05F, HUMERUS + FOREARM - 0.05F);
			float ex = tx / d, ey = ty / d, ez = tz / d;
			// the pole's part across the line to the wrist is which way the elbow bows
			float dot = px * ex + py * ey + pz * ez;
			float qx = px - dot * ex, qy = py - dot * ey, qz = pz - dot * ez;
			float ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
			if (ql < 1.0E-4F) {
				qx = 0.0F;
				qy = 0.0F;
				qz = -1.0F;
				dot = ez * -1.0F;
				qx -= dot * ex;
				qy -= dot * ey;
				qz -= dot * ez;
				ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
			}
			qx /= ql;
			qy /= ql;
			qz /= ql;
			float nx = ey * qz - ez * qy, ny = ez * qx - ex * qz, nz = ex * qy - ey * qx;
			// the humerus' direction, the forearm's
			float cosD = (HUMERUS * HUMERUS + dc * dc - FOREARM * FOREARM) / (2.0F * HUMERUS * dc);
			float sinD = (float)Math.sqrt(Math.max(0.0F, 1.0F - cosD * cosD));
			float hx = cosD * ex + sinD * qx, hy = cosD * ey + sinD * qy, hz = cosD * ez + sinD * qz;
			float fx = (dc * ex - HUMERUS * hx) / FOREARM, fy = (dc * ey - HUMERUS * hy) / FOREARM, fz = (dc * ez - HUMERUS * hz) / FOREARM;
			// the wing's frame: e3 (its face) down-ish, e1 across the body and outward, e2 = e3 x e1
			if (nz < 0.0F) {
				nx = -nx;
				ny = -ny;
				nz = -nz;
			}
			float e1x = -nz, e1z = nx;
			float l1 = (float)Math.sqrt(e1x * e1x + e1z * e1z);
			if (l1 < 1.0E-4F) {
				// the wing's face straight along the body: lay it across the body
				e1x = 1.0F;
				e1z = 0.0F;
				l1 = 1.0F;
			}
			e1x /= l1;
			e1z /= l1;
			if (e1x < 0.0F) {
				e1x = -e1x;
				e1z = -e1z;
			}
			float e2x = ny * e1z, e2y = nz * e1x - nx * e1z, e2z = -ny * e1x;
			float beta = (float)Math.atan2(-e1z, e1x);
			float alpha = (float)Math.atan2(-ny, nx * Mth.sin(beta) + nz * Mth.cos(beta));
			float a = (float)Math.atan2(hx * e2x + hy * e2y + hz * e2z, hx * e1x + hz * e1z);
			float phi = (float)Math.atan2(fx * e2x + fy * e2y + fz * e2z, fx * e1x + fz * e1z);
			float bend = phi - a;
			while (bend > Math.PI) {
				bend -= (float)(Math.PI * 2.0);
			}
			while (bend < -Math.PI) {
				bend += (float)(Math.PI * 2.0);
			}
			out[0] = alpha;
			out[1] = beta;
			out[2] = a;
			out[3] = bend;
		}
	}

	/**
	 * (0.18.113, Scan E; 0.19.66 a flying insect) Hive: antennae, a striped abdomen behind, and two clear wings that buzz;
	 * now a big bee, not a man. It hovers, never touching down (its lowest part about 4 pixels off the ground, a little
	 * lower when sneaking), bobbing a pixel and pitched nose-down as it moves: a round fuzzy thorax with the insignia on
	 * its back, a head with big compound eyes and waving antennae, two pairs of wings that always buzz, six thin legs
	 * that dangle and sway and are drawn up in flight, and a striped abdomen with a stinger hanging under and behind it,
	 * which curls forward under it when it attacks. The man's parts are hidden (the hidden body carries the insignia plate on the thorax).
	 */
	static final class Hive extends FormModel {
		// ---- tuning ----
		/** Hover: the thorax's centre height (model y; ground is 24), how far it bobs and how fast (radians a tick). */
		static final float HOVER_Y = 11.8F, BOB = 0.6F, BOB_RATE = 0.13F;
		/** Nose-down pitch at full speed, and how far it banks into a turn (radians). */
		static final float PITCH = 0.45F, BANK = 0.25F;
		/** The wings: beats a tick (radians), the stroke's size, the lift at rest and the sweep back. */
		static final float FLAP = 3.1F, FLAP_AMP = 0.26F, WING_REST = 0.35F, WING_SWEEP = 0.5F;
		/** The abdomen and stinger hanging pitch (radians, negative hangs the back end down) and how far a sting curls them. */
		static final float ABDOMEN_REST = -0.55F, STINGER_REST = -0.65F, CURL = 1.0F, STINGER_CURL = 0.5F;
		/** How much the thorax lifts (pixels) as it stings, so the curled stinger clears the ground. */
		static final float STING_LIFT = 1.2F;
		/**
		 * Sneaking it hovers this much lower (pixels); the player renderer already sinks a crouching player 2 pixels
		 * (AvatarRenderer.getRenderOffset), so it rises that much back first and never touches down.
		 */
		static final float SNEAK_DROP = 1.0F, CROUCH_SINK = 2.0F;
		/** How far the abdomen may droop on a landing or a sneak (radians of tailLift), so its tip stays off the ground. */
		static final float ABDOMEN_DROOP = 0.35F;
		/** How far out from the middle of its back each wing is hinged (pixels), so the right and left pairs never cross. */
		static final float WING_X = 2.2F;
		/** The legs' z along the thorax (front to back) and how far each tucks back at full speed. */
		private static final float[] LEG_Z = {-2.2F, 0.0F, 2.2F};
		private static final float[] LEG_TUCK = {0.9F, 1.0F, 1.2F};

		private final ModelPart thorax, beeHead, abdomen, stinger, proboscis;
		private final ModelPart rightWing, leftWing, rightHind, leftHind, rightAntenna, leftAntenna;
		/** 0-2 right (front to back), 3-5 left. */
		private final ModelPart[] legs = new ModelPart[6];

		Hive() {
			super(bake(mesh()), "hive");
			ModelPart root = this.root();
			this.thorax = root.getChild("thorax");
			this.beeHead = this.thorax.getChild("bee_head");
			this.proboscis = this.beeHead.getChild("proboscis");
			this.rightAntenna = this.beeHead.getChild("right_antenna");
			this.leftAntenna = this.beeHead.getChild("left_antenna");
			this.abdomen = this.thorax.getChild("abdomen");
			this.stinger = this.abdomen.getChild("stinger");
			this.rightWing = this.thorax.getChild("right_wing");
			this.leftWing = this.thorax.getChild("left_wing");
			this.rightHind = this.thorax.getChild("right_hind_wing");
			this.leftHind = this.thorax.getChild("left_hind_wing");
			for (int i = 0; i < 6; i++) {
				this.legs[i] = this.thorax.getChild("leg" + i);
			}
			// the plate lies on the thorax's back (the hidden body is laid face-up there, see setupAnim)
			this.insigniaY = 0.0F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.6F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition thorax = root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(0, 64).addBox(-3.0F, -3.0F, -3.5F, 6.0F, 6.0F, 7.0F),
				PartPose.offset(0.0F, HOVER_Y, 0.0F));
			PartDefinition abdomen = thorax.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(28, 64).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, 0.8F, 2.5F, ABDOMEN_REST, 0.0F, 0.0F));
			abdomen.addOrReplaceChild("stinger", CubeListBuilder.create().texOffs(0, 92).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 8.0F, STINGER_REST, 0.0F, 0.0F));
			PartDefinition head = thorax.addOrReplaceChild("bee_head", CubeListBuilder.create().texOffs(0, 80).addBox(-2.5F, -2.5F, -4.0F, 5.0F, 5.0F, 4.0F),
				PartPose.offset(0.0F, -0.5F, -2.6F));
			head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(20, 80).addBox(-4.5F, -2.0F, -4.0F, 2.0F, 4.0F, 4.0F), PartPose.ZERO);
			head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(20, 80).mirror().addBox(2.5F, -2.0F, -4.0F, 2.0F, 4.0F, 4.0F), PartPose.ZERO);
			head.addOrReplaceChild("proboscis", CubeListBuilder.create().texOffs(48, 80).addBox(-1.0F, -0.5F, -2.0F, 2.0F, 1.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 1.6F, -3.6F, 0.3F, 0.0F, 0.0F));
			head.addOrReplaceChild("right_antenna", CubeListBuilder.create().texOffs(34, 80).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F),
				PartPose.offsetAndRotation(-1.4F, -2.3F, -3.6F, -0.7F, 0.3F, 0.0F));
			head.addOrReplaceChild("left_antenna", CubeListBuilder.create().texOffs(34, 80).mirror().addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 5.0F),
				PartPose.offsetAndRotation(1.4F, -2.3F, -3.6F, -0.7F, -0.3F, 0.0F));
			// wings: the right pair reaches toward -x, the left mirrors; the fore pair sits forward, the hind pair behind
			thorax.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 100).addBox(-9.0F, -0.5F, -1.5F, 9.0F, 1.0F, 4.0F),
				PartPose.offset(-WING_X, -2.7F, -1.2F));
			thorax.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 100).mirror().addBox(0.0F, -0.5F, -1.5F, 9.0F, 1.0F, 4.0F),
				PartPose.offset(WING_X, -2.7F, -1.2F));
			thorax.addOrReplaceChild("right_hind_wing", CubeListBuilder.create().texOffs(28, 100).addBox(-6.0F, -0.5F, -1.0F, 6.0F, 1.0F, 3.0F),
				PartPose.offset(-WING_X, -2.7F, 1.8F));
			thorax.addOrReplaceChild("left_hind_wing", CubeListBuilder.create().texOffs(28, 100).mirror().addBox(0.0F, -0.5F, -1.0F, 6.0F, 1.0F, 3.0F),
				PartPose.offset(WING_X, -2.7F, 1.8F));
			// six legs off the underside of the thorax, three a side
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				thorax.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(0, 108).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
					PartPose.offset(left ? 1.8F : -1.8F, 2.6F, LEG_Z[i % 3]));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			boolean sneak = state.isCrouching;
			// a sting: the abdomen curls forward under it and back
			float sting = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// hover: bobbing, and a quicker flutter of the bob when it flies; nose-down flying, flaring up as it falls
			float bob = Mth.sin(age * BOB_RATE) * BOB + Mth.sin(age * 0.4F) * 0.3F * speed;
			float pitch = speed * PITCH - m.fall() * m.air() * 0.25F + Mth.sin(age * 0.09F) * 0.03F + sting * 0.2F;
			float bank = m.turn() * BANK + Mth.sin(age * 0.07F) * 0.03F;
			this.thorax.y = HOVER_Y + bob - sting * STING_LIFT + (sneak ? SNEAK_DROP - CROUCH_SINK : 0.0F);
			this.thorax.xRot = pitch;
			this.thorax.zRot = bank;
			// the head turns where it looks, holding level against the pitch
			this.beeHead.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F);
			this.beeHead.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.7F, -0.9F, 0.9F) - pitch * 0.6F;
			this.proboscis.xRot = 0.3F + Mth.sin(age * 0.2F) * 0.1F + sting * 0.3F;
			// antennae wave, and sweep back as it flies
			float feel = Mth.sin(age * 0.17F) * 0.08F;
			this.rightAntenna.xRot = -0.7F - speed * 0.4F + feel;
			this.leftAntenna.xRot = -0.7F - speed * 0.4F - feel;
			this.rightAntenna.yRot = 0.3F + Mth.sin(age * 0.13F) * 0.08F + m.turn() * 0.2F;
			this.leftAntenna.yRot = -0.3F - Mth.sin(age * 0.13F + 1.0F) * 0.08F + m.turn() * 0.2F;
			// the abdomen hangs behind, rising as it flies and falls, curling forward under it on a sting
			this.abdomen.xRot = ABDOMEN_REST - speed * 0.15F - sting * CURL + Mth.sin(age * 0.35F) * 0.04F
				+ Math.max(-ABDOMEN_DROOP, tailLift(m)) * 0.6F;
			this.abdomen.yRot = m.trail() * 0.3F + Mth.sin(age * 0.1F) * 0.05F;
			float breath = 1.0F + m.breath() * 0.03F;
			this.abdomen.xScale = this.abdomen.yScale = this.abdomen.zScale = breath;
			this.stinger.xRot = STINGER_REST - sting * STINGER_CURL;
			// wings: always buzzing, the hind pair a beat behind, swept back more in flight
			float lift = WING_REST + Mth.sin(age * FLAP) * FLAP_AMP;
			float sweep = WING_SWEEP + speed * 0.25F + Mth.cos(age * FLAP) * 0.1F;
			this.rightWing.zRot = lift;
			this.leftWing.zRot = -lift;
			this.rightWing.yRot = sweep;
			this.leftWing.yRot = -sweep;
			float hindLift = WING_REST * 0.8F + Mth.sin(age * FLAP + 0.9F) * FLAP_AMP * 0.9F;
			float hindSweep = sweep + 0.25F;
			this.rightHind.zRot = hindLift;
			this.leftHind.zRot = -hindLift;
			this.rightHind.yRot = hindSweep;
			this.leftHind.yRot = -hindSweep;
			// legs: dangle under it (held plumb against the pitch and bank), swaying, drawn up and back when it flies
			for (int i = 0; i < 6; i++) {
				float side = i >= 3 ? -1.0F : 1.0F;
				float tuck = speed * LEG_TUCK[i % 3] + (sneak ? 0.5F : 0.0F);
				ModelPart leg = this.legs[i];
				leg.xRot = -pitch + tuck + Mth.sin(age * 0.11F + i * 1.3F) * 0.1F + m.trail() * 0.15F;
				leg.zRot = side * (0.12F + m.air() * 0.35F + Mth.sin(age * 0.09F + i) * 0.06F) - bank;
			}
			// the hidden body lies face-up on the thorax's back, so the insignia plate sits flush there
			this.body.x = 0.0F;
			this.body.y = this.thorax.y;
			this.body.z = 0.0F;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			this.body.yRot = 0.0F;
			// banked with the thorax too: Rz(bank)Rx(pitch - PI/2) is the thorax's own turn, then laid face-up
			this.body.zRot = bank;
		}
	}

	/**
	 * (0.18.113, Scan E; 0.19.62 upright, a spider when prone) Skitter: standing, the shape it always had, a man with
	 * four thin spider legs off its back and a small abdomen behind. Prone (the stance wheel's crawl) it drops into a
	 * spider ({@link SkitterCeiling#CRAWL_KEY}): a small thorax, a head with fangs that turns where it looks, a big round
	 * abdomen behind (the insignia on top) and eight legs. (0.20.59) The legs are the pack's spiders' own (Kingdoms'
	 * {@code SpiderLegsMixin} on the game's {@code SpiderModel}): the game's leg poses and step, each leg kneed (two
	 * 8-long halves, the lower a child "shin") and held high with the foot planted, the shin bending more as the foot
	 * lifts, idle legs twitching; the abdomen breathes and sways as theirs does. Its own on top: it crouches flat when sneaking, a bite lunges the head and rears the front legs, and up a
	 * wall (tipped head-up by SkitterCeiling) the legs keep climbing. Standing back up turns it back. In the spider the
	 * man's parts are hidden and the body is laid on the abdomen, so the insignia sits on its back.
	 */
	static final class Skitter extends FormModel {
		// ---- tuning ----
		/** The spider's body height off the ground (model y, a spider's), how far it drops when sneaking. */
		static final float BODY_Y = 15.0F, CROUCH_DROP = 3.0F;
		/** (0.20.59) A spider's step, the game's: gait against the walk, swing and lift. */
		static final float GAIT = 0.6662F, SWING = 0.4F, LIFT = 0.4F;
		/** (0.20.59) The spiders' knee: each thigh raised this far, its shin bent down this far, more per unit of step lift. */
		static final float KNEE_LIFT = 0.45F, SHIN_BEND = 1.0F, STEP_BEND = 0.9F;
		/** (0.20.59) How much more each thigh rises and shin bends when it crouches flat. */
		static final float CROUCH_SPLAY = 0.35F;
		/** (0.19.62) Up a wall, the legs step this much faster (the walk barely moves while climbing). */
		static final float CLIMB_GAIT = 0.35F;
		/** Rest: each leg pair's yaw (hind to front, the right side's; the left mirrors) and droop, the game's spider's. */
		private static final float[] REST_YAW = {(float)(Math.PI / 4), (float)(Math.PI / 8), (float)(-Math.PI / 8), (float)(-Math.PI / 4)};
		private static final float[] REST_DROOP = {(float)(Math.PI / 4), 0.58119464F, 0.58119464F, (float)(Math.PI / 4)};
		/** Each pair's step phase (hind to front), the game's spider's; both sides on the same beat, mirrored. */
		private static final float[] PHASE = {0.0F, (float)Math.PI, (float)Math.PI * 0.5F, (float)Math.PI * 1.5F};
		/** (0.19.62) Past this far into the crawl it's drawn as the spider. */
		static final float SPIDER_AT = 0.5F;

		private final ModelPart thorax, spiderHead, abdomen;
		/** Hind to front: 0-3 right, 4-7 left; and each one's shin. */
		private final ModelPart[] legs = new ModelPart[8], shins = new ModelPart[8];
		/** (0.19.62) Upright: the four thin legs off its back, and the small abdomen behind. */
		private final ModelPart[] backLegs = new ModelPart[4];
		private final ModelPart backAbdomen;

		Skitter() {
			super(bake(mesh()), "skitter");
			ModelPart root = this.root();
			this.thorax = root.getChild("thorax");
			this.spiderHead = root.getChild("spider_head");
			this.abdomen = root.getChild("abdomen");
			for (int i = 0; i < 8; i++) {
				this.legs[i] = root.getChild("leg" + i);
				this.shins[i] = this.legs[i].getChild("shin");
			}
			this.backAbdomen = this.body.getChild("back_abdomen");
			for (int i = 0; i < 4; i++) {
				this.backLegs[i] = this.body.getChild("back_leg" + i);
			}
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// upright: the shape it always had, a man with four spider legs off its back and an abdomen behind
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("back_abdomen", CubeListBuilder.create().texOffs(20, 64).addBox(-3.5F, -3.0F, 0.0F, 7.0F, 6.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 9.0F, 1.5F, 0.3F, 0.0F, 0.0F));
			// legs 0 and 1 on its right, 2 and 3 on its left, the front pair higher
			for (int i = 0; i < 4; i++) {
				boolean left = i >= 2;
				float y = (i % 2 == 0) ? 3.0F : 6.5F;
				CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 64);
				if (left) {
					leg.mirror().addBox(0.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F);
				} else {
					leg.addBox(-8.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F);
				}
				body.addOrReplaceChild("back_leg" + i, leg, PartPose.offsetAndRotation(left ? 3.5F : -3.5F, y, 0.0F, 0.0F, 0.0F, left ? 0.6F : -0.6F));
			}
			// prone: the spider
			root.addOrReplaceChild("thorax", CubeListBuilder.create().texOffs(32, 98).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
			PartDefinition head = root.addOrReplaceChild("spider_head", CubeListBuilder.create().texOffs(0, 98).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, BODY_Y, -3.0F));
			head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(44, 78).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(-1.5F, 3.5F, -7.0F, -0.3F, 0.0F, 0.15F));
			head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(44, 78).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(1.5F, 3.5F, -7.0F, -0.3F, 0.0F, -0.15F));
			root.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 78).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F),
				PartPose.offset(0.0F, BODY_Y, 9.0F));
			// hind to front along the thorax, right side then left, kneed like the spiders' (thigh and shin both take the
			// leg strip's first 8, as the spiders' two halves share their one leg strip)
			for (int i = 0; i < 8; i++) {
				boolean left = i >= 4;
				float z = 2.0F - (i % 4);
				CubeListBuilder thigh = left
					? CubeListBuilder.create().texOffs(0, 118).mirror().addBox(-1.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F)
					: CubeListBuilder.create().texOffs(0, 114).addBox(-7.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F);
				CubeListBuilder shin = left
					? CubeListBuilder.create().texOffs(0, 118).mirror().addBox(0.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F)
					: CubeListBuilder.create().texOffs(0, 114).addBox(-8.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F);
				PartDefinition leg = root.addOrReplaceChild("leg" + i, thigh, PartPose.offset(left ? 4.0F : -4.0F, BODY_Y, z));
				leg.addOrReplaceChild("shin", shin, PartPose.offset(left ? 7.0F : -7.0F, 0.0F, 0.0F));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Float crawl = state.getData(SkitterCeiling.CRAWL_KEY);
			boolean spider = crawl != null && crawl > SPIDER_AT;
			this.thorax.visible = this.spiderHead.visible = this.abdomen.visible = spider;
			for (ModelPart leg : this.legs) {
				leg.visible = spider;
			}
			this.head.visible = this.hat.visible = this.body.visible = !spider;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = !spider;
			if (spider) {
				this.spider(state);
			} else {
				this.upright(state);
			}
		}

		/** Standing: the old upright gait, its back legs stepping as it walks. */
		private void upright(AvatarRenderState state) {
			this.insigniaY = 7.5F;
			this.insigniaZ = -2.0F;
			this.insigniaSize = 1.0F;
			float walk = state.walkAnimationPos * 0.9F;
			float amount = state.walkAnimationSpeed;
			for (int i = 0; i < 4; i++) {
				boolean left = i >= 2;
				float spread = (i % 2 == 0) ? 0.45F : -0.45F;
				float step = Mth.cos(walk + i * (float)Math.PI * 0.5F) * 0.4F * amount;
				this.backLegs[i].yRot = (left ? -spread : spread) + step;
				this.backLegs[i].zRot = (left ? 0.6F : -0.6F) + Mth.abs(Mth.sin(walk + i)) * 0.25F * amount * (left ? -1.0F : 1.0F);
			}
		}

		/** Prone: the spider, on eight legs stepping in alternating fours (on a wall too, see SkitterCeiling). */
		private void spider(AvatarRenderState state) {
			// the insignia on top of the abdomen: the man's (hidden) body lies face-up there
			this.insigniaY = 0.0F;
			this.insigniaZ = -4.0F;
			this.insigniaSize = 1.3F;
			Float climb = state.getData(SkitterCeiling.CLIMB_KEY);
			boolean climbing = climb != null && climb > 0.5F;
			// up a wall the walk hardly moves, so the legs run on the clock instead
			float walk = climbing ? state.ageInTicks * CLIMB_GAIT : state.walkAnimationPos * GAIT;
			float speed = climbing ? 1.0F : Math.min(1.0F, state.walkAnimationSpeed);
			float crouch = state.isCrouching && !climbing ? 1.0F : 0.0F;
			float y = BODY_Y + CROUCH_DROP * crouch;
			float age = state.ageInTicks;
			// a bite: the head lunges and the fangs open, the front legs rear
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			this.thorax.y = y;
			this.spiderHead.y = y;
			this.spiderHead.z = -3.0F - bite * 2.0F;
			this.spiderHead.yRot = climbing ? 0.0F : state.yRot * Mth.DEG_TO_RAD;
			this.spiderHead.xRot = (climbing ? 0.0F : state.xRot * Mth.DEG_TO_RAD) - bite * 0.3F;
			// the abdomen breathes, and sways as it walks (as the spiders' does)
			this.abdomen.y = y + Mth.sin(age * 0.08F) * 0.35F;
			this.abdomen.xRot = Mth.sin(age * 0.08F) * 0.04F;
			this.abdomen.yRot = Mth.sin(walk) * 0.12F * speed;
			for (int i = 0; i < 8; i++) {
				int pair = i % 4;
				float side = i >= 4 ? -1.0F : 1.0F;
				ModelPart leg = this.legs[i];
				leg.y = y;
				// the game's spider step: both sides on the same beat, mirrored
				float swing = -Mth.cos(walk * 2.0F + PHASE[pair]) * SWING * speed;
				float step = Math.abs(Mth.sin(walk + PHASE[pair]) * LIFT) * speed;
				// idle: a slow twitch, each leg on its own beat, fading out as it walks (the spiders' own)
				float twitch = Mth.sin(age * 0.13F + i * 1.7F) * 0.04F * (1.0F - Math.min(1.0F, speed * 2.0F));
				leg.yRot = side * (REST_YAW[pair] + swing);
				leg.zRot = side * (-REST_DROOP[pair] + step + KNEE_LIFT + twitch + CROUCH_SPLAY * crouch);
				if (pair == 3) {
					// the front pair rears on a bite
					leg.zRot += side * bite * 0.6F;
				}
				this.shins[i].zRot = -side * (SHIN_BEND + step * STEP_BEND + CROUCH_SPLAY * crouch);
			}
			this.body.x = 0.0F;
			this.body.y = this.abdomen.y;
			this.body.z = 9.0F;
			this.body.xRot = -(float)Math.PI * 0.5F;
			this.body.yRot = this.body.zRot = 0.0F;
		}
	}

	/**
	 * (0.18.113, Scan E; 0.19.84 a crawling shadow that unfolds tall) Shade: very tall and thin, its eyes do the rest.
	 * <p>
	 * No longer a man in black. Moving on the ground it goes low on its long limbs, all fours,
	 * spidery: the torso pitched nearly flat, the arms' elbows high and forward with the hands reaching ahead, the hind
	 * legs folded back like a cricket's, all of them splayed wide, stepping in a lateral sequence (hind, fore, hind, fore
	 * across the body) with the torso swaying. Standing still, attacking, staring, holding a stolen block, jumping or
	 * falling, it unfolds to its full very tall, thin height (a smooth, speed-limited transition both ways, per player:
	 * stand up fast, fold back slowly). Standing, the arms hang to the knees and swing against the legs; an attack
	 * chops overhead; {@link XenoformClient#ACT} "hold" (Steal's block held up) raises both arms over the head and
	 * "stare" (Stare's lock) spreads the arms wide and leans the head in, both server-side from
	 * {@code XenoformPower.Live}: {@code lifted != null} and {@code stalked >= 0}. Every limb is two segments solved by
	 * two-bone IK, so the feet and hands stay on the ground through the whole unfold. The man's body is hidden and
	 * carries only the insignia plate, which rides the torso's front standing and its back crawling. Swimming it stays stretched out.
	 */
	static final class Shade extends FormModel {
		// ---- tuning ----
		/** Standing: the hips' height (model y; the ground is 24, the ankle {@link #FOOT_H} above it, a leg reaches {@link #MAX_REACH}) and the arms' hang, wrist below shoulder. */
		static final float STAND_HIP_Y = 5.8F, ARM_HANG = 17.0F;
		/** Crawling: the hips' height, how far back the hips sit (the body is centred on the box), the torso's pitch (radians, forward) and how much lower it sinks when sneaking. */
		static final float CRAWL_HIP_Y = 17.0F, CRAWL_Z = 6.0F, CRAWL_PITCH = 1.2F, CRAWL_SINK = 1.0F;
		/** How far a hind foot sits behind its hip crawling, and how far ahead of the shoulder a hand reaches. */
		static final float CRAWL_FOOT_DZ = 3.0F, CRAWL_REACH = 8.0F;
		/** The unfold: ticks to stand up and to fold back down, the walking speed where it starts to crawl and how sharply it goes low. */
		static final float UNFOLD_TICKS = 5.0F, FOLD_TICKS = 12.0F, CRAWL_FROM = 0.04F, CRAWL_GAIN = 4.0F;
		/** The gait: radians of clock per unit of walk, how much walking speed it takes to step fully. */
		static final float GAIT = 0.6662F, GAIT_GAIN = 2.0F;
		/** Half a stride (pixels) and how high a stepping foot lifts: crawling (legs and arms alike), and standing (legs; the arms' swing). */
		static final float CRAWL_STRIDE = 4.0F, CRAWL_LIFT = 3.0F, STAND_STRIDE = 3.0F, STAND_LIFT = 2.0F, ARM_STAND_STRIDE = 3.0F;
		/** The limbs' outward splay (radians about the hip or shoulder): crawling and standing; the arms' extra splay while staring; the arms' inward lean holding a block. */
		static final float LEG_SPLAY_CRAWL = 0.5F, LEG_SPLAY_STAND = 0.04F, ARM_SPLAY_CRAWL = 0.45F, ARM_SPLAY_STAND = 0.06F, STARE_SPLAY = 0.5F, HOLD_SPLAY = -0.08F;
		/** Where the limbs hang on the torso: the hips out from the middle (crawling, standing), the shoulders out. */
		static final float HIP_X_CRAWL = 2.2F, HIP_X_STAND = 1.5F, SHOULDER_X = 3.5F;
		/** The torso sways (radians of yaw) with the steps: crawling, standing. */
		static final float CRAWL_SWAY = 0.16F, STAND_SWAY = 0.04F;
		/** Standing: lean forward while staring, the extra lunge of an attack, the lean back holding a block (radians); sneaking standing drops the hips (pixels). */
		static final float STARE_LEAN = 0.12F, SWING_LEAN = 0.22F, HOLD_LEAN = -0.06F, CROUCH_DROP = 2.0F;
		/** The renderer sinks a crouching player 2 pixels, so everything is raised that much first. */
		static final float CROUCH_RISE = -2.0F;
		/** The head: how far it tips crawling (radians; the face stays near forward), how much of the look's pitch it takes, and the lunge of an attack. */
		static final float HEAD_TILT_CRAWL = 0.3F, LOOK_PITCH = 1.0F, SWING_LUNGE = 0.35F;
		/** The attack's chop: the hand's start and end z against the shoulder (+ is back), and how high the arc lifts it (pixels). */
		static final float SWING_BACK = 4.0F, SWING_FWD = -13.0F, SWING_ARC = 14.0F;
		/** Holding a block: how far above the shoulder the wrists rise, and how far the fingers curl (radians). */
		static final float HOLD_UP = 13.5F, HOLD_CURL = 0.9F;
		/** Using an item (a bow, a shield, food): how far ahead and how far below the shoulder the hand comes up (pixels). */
		static final float USE_FWD = -11.0F, USE_DROP = 4.0F;
		/** Jumping or falling: how far the feet draw up and the hands fly up (pixels); a landing dips the hips (pixels per unit of land). */
		static final float AIR_LEG = 4.0F, AIR_ARM = 5.0F, LAND_DIP = 2.5F;
		/**
		 * A hand crawling points this far forward from straight down (radians, - is forward) and so its wrist sits this
		 * high off the ground (pixels: the 3-pixel fingers' drop plus the 2-pixel hand's thickness, so no corner sinks in);
		 * it only starts to hang once the unfold is this far along (so it never swings down into the ground).
		 */
		static final float HAND_CRAWL = -1.2F, HAND_GROUND = 2.2F, HAND_LIFT_FROM = 0.5F;
		/** The change-back tap: how far in front of the insignia the wrist stops (the hand's length) and how far further out and up it is poised. */
		static final float TAP_HAND = 3.0F, TAP_POISE = 5.0F, TAP_POISE_UP = 3.0F;
		/** A segment's length (a limb has two) and how short of fully stretched a limb is kept (so a straight leg still has a hint of bend). */
		private static final float SEG = 9.0F, SLACK = 0.4F, MAX_REACH = 2.0F * SEG - SLACK, MIN_REACH = 1.5F;
		/** The foot's thickness: it hangs under the ankle, so the ankle is solved to this far above the ground (no shin corner sinks in). */
		private static final float FOOT_H = 1.0F;
		/** The torso's hinge points (y in its own space, up is -): the neck, the shoulders and the middle (where the plate sits). */
		private static final float NECK_BY = -10.5F, SHOULDER_BY = -9.5F, CHEST_BY = -5.5F;

		/** Per player: the unfold (0 crawling, 1 standing), the hold and stare blends, and the age they were stepped at. */
		private static final java.util.Map<Integer, float[]> TRACKS = new java.util.HashMap<>();

		private final ModelPart torso;
		private final ModelPart rightForearm, leftForearm, rightHand, leftHand, rightShin, leftShin, rightFoot, leftFoot;
		/** Scratch: the IK's two angles, and a point on the torso. */
		private final float[] angles = new float[2], point = new float[3];

		Shade() {
			super(bake(mesh()), "shade");
			this.ownsTap = true;
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.rightForearm = this.rightArm.getChild("forearm");
			this.leftForearm = this.leftArm.getChild("forearm");
			this.rightHand = this.rightForearm.getChild("hand");
			this.leftHand = this.leftForearm.getChild("hand");
			this.rightShin = this.rightLeg.getChild("shin");
			this.leftShin = this.leftLeg.getChild("shin");
			this.rightFoot = this.rightShin.getChild("foot");
			this.leftFoot = this.leftShin.getChild("foot");
			// (0.19.84) the man's body is hidden from the start: the band's hologram and plate draw the root without setupAnim
			this.body.visible = false;
			this.hat.visible = false;
			// the hidden body sits at the torso's middle, turned so the plate lies on its front standing and its back crawling
			this.insigniaY = 0.0F;
			this.insigniaZ = -1.5F;
			this.insigniaSize = 0.8F;
		}

		/** A 2x10x2 segment (a pixel of overlap at its top so the joints close). */
		private static CubeListBuilder seg(int u, int v, boolean left) {
			CubeListBuilder b = CubeListBuilder.create().texOffs(u, v);
			if (left) {
				b.mirror();
			}
			return b.addBox(-1.0F, -1.0F, -1.0F, 2.0F, 10.0F, 2.0F);
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// a narrow head on the end of the torso
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 64).addBox(-2.5F, -6.0F, -2.5F, 5.0F, 6.0F, 5.0F),
				PartPose.offset(0.0F, STAND_HIP_Y + NECK_BY, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			// the real torso, thin and long (the man's body, kept for the plate, is hidden)
			root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 76).addBox(-2.5F, -11.0F, -1.5F, 5.0F, 11.0F, 3.0F),
				PartPose.offset(0.0F, STAND_HIP_Y, 0.0F));
			// arms: upper arm, forearm, a hand of long fingers
			PartDefinition rightArm = root.addOrReplaceChild("right_arm", seg(0, 92, false), PartPose.offset(-SHOULDER_X, STAND_HIP_Y + SHOULDER_BY, 0.0F));
			rightArm.addOrReplaceChild("forearm", seg(8, 92, false), PartPose.offset(0.0F, SEG, 0.0F))
				.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(0, 106).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, SEG, 0.0F));
			PartDefinition leftArm = root.addOrReplaceChild("left_arm", seg(16, 92, true), PartPose.offset(SHOULDER_X, STAND_HIP_Y + SHOULDER_BY, 0.0F));
			leftArm.addOrReplaceChild("forearm", seg(24, 92, true), PartPose.offset(0.0F, SEG, 0.0F))
				.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(8, 106).mirror().addBox(-1.0F, -1.0F, -1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, SEG, 0.0F));
			// legs: thigh, shin, a long flat foot (under the ankle, its sole FOOT_H below it)
			PartDefinition rightLeg = root.addOrReplaceChild("right_leg", seg(32, 92, false), PartPose.offset(-HIP_X_STAND, STAND_HIP_Y, 0.0F));
			rightLeg.addOrReplaceChild("shin", seg(40, 92, false), PartPose.offset(0.0F, SEG, 0.0F))
				.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(16, 106).addBox(-1.0F, 0.0F, -3.0F, 2.0F, FOOT_H, 4.0F), PartPose.offset(0.0F, SEG, 0.0F));
			PartDefinition leftLeg = root.addOrReplaceChild("left_leg", seg(48, 92, true), PartPose.offset(HIP_X_STAND, STAND_HIP_Y, 0.0F));
			leftLeg.addOrReplaceChild("shin", seg(56, 92, true), PartPose.offset(0.0F, SEG, 0.0F))
				.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(28, 106).mirror().addBox(-1.0F, 0.0F, -3.0F, 2.0F, FOOT_H, 4.0F), PartPose.offset(0.0F, SEG, 0.0F));
			return mesh;
		}

		private static float smooth(float s) {
			return s * s * (3.0F - 2.0F * s);
		}

		private static float approach(float v, float to, float step) {
			return v < to ? Math.min(to, v + step) : Math.max(to, v - step);
		}

		/**
		 * This player's blends stepped to {@code age}: the unfold toward {@code target} (quick up, slow down), and the
		 * hold and stare blends. Repeat draws at the same age change nothing; a long gap snaps to the targets.
		 */
		private static float[] track(AvatarRenderState state, float target, float hold, float stare) {
			float now = state.ageInTicks;
			float[] t = TRACKS.get(state.id);
			if (t == null) {
				if (TRACKS.size() > 64) {
					TRACKS.clear();
				}
				t = new float[] {target, hold, stare, now};
				TRACKS.put(state.id, t);
				return t;
			}
			float dt = now - t[3];
			if (dt < 0.0F || dt > 40.0F) {
				t[0] = target;
				t[1] = hold;
				t[2] = stare;
				t[3] = now;
			} else if (dt > 0.0F) {
				t[3] = now;
				t[0] = approach(t[0], target, dt / (target > t[0] ? UNFOLD_TICKS : FOLD_TICKS));
				t[1] = approach(t[1], hold, dt / UNFOLD_TICKS);
				t[2] = approach(t[2], stare, dt / UNFOLD_TICKS);
			}
			return t;
		}

		/**
		 * A point on the torso, {@code bx} out and {@code by} along it (from the hip, up is -), after its pitch and yaw,
		 * into {@link #point} (x, y, z from the hip).
		 */
		private void onTorso(float bx, float by, float pitch, float yaw) {
			float y1 = by * Mth.cos(pitch);
			float z1 = by * Mth.sin(pitch);
			this.point[0] = bx * Mth.cos(yaw) + z1 * Mth.sin(yaw);
			this.point[1] = y1;
			this.point[2] = -bx * Mth.sin(yaw) + z1 * Mth.cos(yaw);
		}

		/**
		 * Two equal segments reaching {@code (ty, tz)} (down, back) from the hinge: the upper one's angle and the lower
		 * one's bend against it into {@link #angles}. {@code bend} +1 folds the joint forward, -1 backward, 0 straight.
		 */
		private void ik(float ty, float tz, float bend) {
			float d = Mth.clamp((float)Math.sqrt(ty * ty + tz * tz), MIN_REACH, MAX_REACH);
			float phi = (float)Math.atan2(tz, ty);
			float alpha = (float)Math.acos(Mth.clamp(d / (2.0F * SEG), -1.0F, 1.0F));
			this.angles[0] = phi - bend * alpha;
			this.angles[1] = 2.0F * bend * alpha;
		}

		/** The forearm and hand straight for the first-person arm (the model is shared, so they hold whoever was drawn last). */
		void handPose() {
			this.rightForearm.xRot = 0.0F;
			this.leftForearm.xRot = 0.0F;
			this.rightHand.xRot = 0.2F;
			this.leftHand.xRot = 0.2F;
		}

		@Override
		public void translateToHand(net.minecraft.client.renderer.entity.state.HumanoidRenderState state, net.minecraft.world.entity.HumanoidArm arm,
			com.mojang.blaze3d.vertex.PoseStack pose) {
			// what's held sits a pixel past the wrist, at the end of the forearm (the arm is two segments)
			this.root().translateAndRotate(pose);
			ModelPart upper = this.getArm(arm);
			upper.translateAndRotate(pose);
			upper.getChild("forearm").translateAndRotate(pose);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.body.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
				part.resetPose();
			}
			this.body.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			String act = state.getData(XenoformClient.ACT);
			boolean holding = "hold".equals(act);
			boolean staring = "stare".equals(act);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			float swingT = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			boolean swinging = swingT > 0.0F && swingT < 1.0F;
			float swingArc = Mth.sin(swingT * (float)Math.PI);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			boolean crouch = state.isCrouching;
			boolean using = state.isUsingItem;
			// what it wants to be: crawling when it moves, standing when it is still, attacks, stares, holds or uses something
			float target = 1.0F - Mth.clamp((speed - CRAWL_FROM) * CRAWL_GAIN, 0.0F, 1.0F);
			if (crouch) {
				target = 0.0F;
			}
			// (0.19.84) the change-back tap: the right hand comes up onto the insignia (XenoformSlap leaves that arm to us); it stands for it
			XenoformSlap.Pose slap = state.getData(XenoformSlap.KEY);
			float tap = slap != null && slap.chest() && !XenoformSlap.ownBody(state) ? slap.raise() : 0.0F;
			float tapHit = slap != null ? slap.strike() : 0.0F;
			if (holding || staring || swinging || using || tap > 0.0F) {
				target = 1.0F;
			}
			// in the air or swimming (the renderer lays a swimmer flat) it stays stretched out
			target = Math.max(target, Math.max(air, Mth.clamp(state.swimAmount, 0.0F, 1.0F)));
			float[] tr = track(state, target, holding ? 1.0F : 0.0F, staring ? 1.0F : 0.0F);
			float e = smooth(tr[0]);
			float hold = smooth(tr[1]);
			float stare = smooth(tr[2]);

			float pi = (float)Math.PI;
			float walk = state.walkAnimationPos * GAIT;
			float gait = Mth.clamp(speed * GAIT_GAIN, 0.0F, 1.0F);
			float ground = 24.0F + (crouch ? CROUCH_RISE : 0.0F);
			float breath = Mth.sin(age * 0.09F);
			// the torso: pitched flat crawling, upright standing (leaning for a stare, a blow or a held block)
			float lean = stare * STARE_LEAN + swingArc * SWING_LEAN + hold * HOLD_LEAN + breath * 0.015F;
			float theta = Mth.lerp(e, CRAWL_PITCH, lean);
			float psi = Mth.sin(walk) * Mth.lerp(e, CRAWL_SWAY, STAND_SWAY) * gait + Mth.sin(age * 0.05F) * 0.03F;
			float hipY = Mth.lerp(e, CRAWL_HIP_Y + (crouch ? CRAWL_SINK : 0.0F), STAND_HIP_Y + (crouch ? CROUCH_DROP : 0.0F)) + (ground - 24.0F)
				+ m.land() * LAND_DIP + Mth.sin(walk * 2.0F) * 0.4F * gait * (1.0F - e) + breath * 0.12F;
			float hipZ = Mth.lerp(e, CRAWL_Z, 0.0F);
			this.torso.y = hipY;
			this.torso.z = hipZ;
			this.torso.xRot = theta;
			this.torso.yRot = psi;
			this.torso.yScale = 1.0F + breath * 0.012F;
			// the plate carrier: at the torso's middle, front standing, back crawling (it flips at half unfolded: turned
			// gradually it would sweep through the torso, its corners poking out of the sides)
			this.onTorso(0.0F, CHEST_BY, theta, psi);
			this.body.x = this.point[0];
			this.body.y = hipY + this.point[1];
			this.body.z = hipZ + this.point[2];
			this.body.xRot = theta + (e < 0.5F ? pi : 0.0F);
			this.body.yRot = psi;
			// the head, on the torso's end, turned where it looks
			this.onTorso(0.0F, NECK_BY, theta, psi);
			this.head.x = this.point[0];
			this.head.y = hipY + this.point[1];
			this.head.z = hipZ + this.point[2];
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * LOOK_PITCH, -1.0F, 1.0F);
			this.head.xRot = look + Mth.lerp(e, HEAD_TILT_CRAWL, 0.0F) + stare * 0.12F + swingArc * SWING_LUNGE - hold * 0.3F;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F) - psi * 0.5F + Mth.sin(age * 0.04F) * 0.12F * (1.0F - e) * (1.0F - gait);
			this.head.zRot = Mth.sin(age * 0.07F) * 0.04F + stare * (0.12F + Mth.sin(age * 1.9F) * 0.02F);

			// legs: two-bone IK down to the ground in a tilted plane, the hind legs folded back
			float stride = Mth.lerp(e, CRAWL_STRIDE, STAND_STRIDE) * gait;
			float liftMax = Mth.lerp(e, CRAWL_LIFT, STAND_LIFT) * gait;
			float legSplay = Mth.lerp(e, LEG_SPLAY_CRAWL, LEG_SPLAY_STAND);
			float footDz = Mth.lerp(e, CRAWL_FOOT_DZ, 0.0F);
			float hipX = Mth.lerp(e, HIP_X_CRAWL, HIP_X_STAND);
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				float sgn = left ? -1.0F : 1.0F;
				ModelPart leg = left ? this.leftLeg : this.rightLeg;
				ModelPart shin = left ? this.leftShin : this.rightShin;
				ModelPart foot = left ? this.leftFoot : this.rightFoot;
				float phase = walk + (left ? pi : 0.0F);
				float lift = Math.max(0.0F, -Mth.sin(phase)) * liftMax + air * AIR_LEG;
				float ty = (ground - FOOT_H - lift - hipY) / Mth.cos(legSplay);
				this.ik(ty, footDz - Mth.cos(phase) * stride, -1.0F);
				leg.x = -sgn * hipX;
				leg.y = hipY;
				leg.z = hipZ;
				leg.xRot = this.angles[0];
				leg.zRot = sgn * legSplay;
				shin.xRot = this.angles[1];
				// the foot stays flat (toes down as it swings through the air): it undoes the leg's pitch and its splay,
				// whose turn Rz(s)Rx(a) it reverses as Rx(-a)Rz(-s), put into the part's Z-Y-X angles
				float a = this.angles[0] + this.angles[1];
				float s = sgn * legSplay;
				foot.xRot = (float)Math.atan2(-Mth.cos(s) * Mth.sin(a), Mth.cos(a)) + air * 0.5F;
				foot.yRot = (float)Math.asin(Mth.clamp(-Mth.sin(s) * Mth.sin(a), -1.0F, 1.0F));
				foot.zRot = (float)Math.atan2(-Mth.sin(s) * Mth.cos(a), Mth.cos(s));
			}

			// arms: from the shoulders on the torso, elbows high and forward crawling, hanging and swinging standing
			boolean mainLeft = state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT;
			boolean useLeft = state.useItemHand == net.minecraft.world.InteractionHand.MAIN_HAND ? mainLeft : !mainLeft;
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				float sgn = left ? -1.0F : 1.0F;
				ModelPart arm = left ? this.leftArm : this.rightArm;
				ModelPart forearm = left ? this.leftForearm : this.rightForearm;
				ModelPart hand = left ? this.leftHand : this.rightHand;
				this.onTorso(-sgn * SHOULDER_X, SHOULDER_BY, theta, psi);
				float sx = this.point[0];
				float sy = hipY + this.point[1];
				float sz = hipZ + this.point[2];
				// the lateral sequence crawling (each arm a quarter beat after the hind leg on its side), against the legs standing
				float phase = walk + (left ? 3.0F : 1.0F) * pi * 0.5F + e * pi * 0.5F;
				float step = -Mth.cos(phase) * Mth.lerp(e, CRAWL_STRIDE, ARM_STAND_STRIDE) * gait;
				float lift = Math.max(0.0F, -Mth.sin(phase)) * CRAWL_LIFT * gait * (1.0F - e);
				// (never below the crawl's height: half unfolded, the standing hang still reaches under the ground)
				float wristY = Math.min(Mth.lerp(e, ground - HAND_GROUND - lift, sy + ARM_HANG), ground - HAND_GROUND - lift) - air * AIR_ARM;
				float wristZ = Mth.lerp(e, sz - CRAWL_REACH, sz) + step + Mth.sin(age * 0.05F + side * 2.0F) * 0.6F * e;
				float splay = Mth.lerp(e, ARM_SPLAY_CRAWL, ARM_SPLAY_STAND) + stare * STARE_SPLAY;
				float bend = 1.0F;
				if (swinging && left == mainLeft) {
					// a chop: back and up, over, down and forward
					float k = smooth(swingT);
					wristZ = Mth.lerp(e, wristZ, sz + Mth.lerp(k, SWING_BACK, SWING_FWD));
					wristY = Mth.lerp(e, wristY, sy + ARM_HANG - swingArc * SWING_ARC);
				}
				if (using && left == useLeft) {
					wristY = sy + USE_DROP;
					wristZ = sz + USE_FWD + Mth.sin(age * 0.6F) * 0.3F;
					bend = 0.6F;
				}
				// holding a block over its head: both wrists up, elbows forward
				wristY = Mth.lerp(hold, wristY, sy - HOLD_UP);
				wristZ = Mth.lerp(hold, wristZ, sz - 0.5F);
				splay = Mth.lerp(hold, splay, HOLD_SPLAY);
				bend = Mth.lerp(hold, bend, -1.0F);
				float tapped = left ? 0.0F : tap * e;
				if (tapped > 0.0F) {
					// the insignia on the plate carrier's front (its x turn), the wrist a hand's length in front of it, rolled in across the chest
					float ix = this.body.x;
					float iy = this.body.y - this.insigniaZ * Mth.sin(this.body.xRot);
					float iz = this.body.z + this.insigniaZ * Mth.cos(this.body.xRot);
					float ty = iy - TAP_POISE_UP * (1.0F - tapHit);
					float tz = iz - TAP_HAND - TAP_POISE * (1.0F - tapHit);
					wristY = Mth.lerp(tapped, wristY, ty);
					wristZ = Mth.lerp(tapped, wristZ, tz);
					splay = Mth.lerp(tapped, splay, -(float)Math.atan2(ix - sx, Math.max(0.5F, ty - sy)));
					bend = Mth.lerp(tapped, bend, 1.0F);
				}
				this.ik((wristY - sy) / Mth.cos(splay), wristZ - sz, bend);
				arm.x = sx;
				arm.y = sy;
				arm.z = sz;
				arm.xRot = this.angles[0];
				arm.zRot = sgn * splay;
				forearm.xRot = this.angles[1];
				// the hand: flat forward on the ground crawling, hanging with the fingers flexing standing, curled round a block
				float forearmAngle = this.angles[0] + this.angles[1];
				float flex = 0.2F + Mth.sin(age * 0.35F + side * 3.0F) * 0.08F;
				float hang = Mth.clamp((e - HAND_LIFT_FROM) / (1.0F - HAND_LIFT_FROM), 0.0F, 1.0F);
				hand.xRot = Mth.lerp(hold, Mth.lerp(hang, HAND_CRAWL - forearmAngle, flex), HOLD_CURL);
			}
		}
	}

	/** (0.18.113, Scan E) Mirage: a deep hood over the head and a robe skirt from the waist. */
	static final class Mirage extends FormModel {
		Mirage() {
			super(bake(mesh()), "mirage");
			this.insigniaZ = -1.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(-0.1F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F,
				new CubeDeformation(0.6F)), PartPose.ZERO);
			root.getChild("body").addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 82).addBox(-4.0F, 12.0F, -2.0F, 8.0F, 8.0F, 4.0F,
				new CubeDeformation(0.5F)), PartPose.ZERO);
			return mesh;
		}
	}

	/**
	 * (0.18.117, Scan F) Ooze (and Magma Ooze): a soft, swollen body with a blob on its head that wobbles as it moves.
	 * <p>
	 * (0.19.82) A cube of jelly, drawn low (a box under 0.6 blocks, {@code XenoformState.LOW_BOX}). Ooze is a see-through
	 * outer cube (window-paned sides, a gooey floor) around a darker core with two eyes; Magma Ooze is the magma cube's
	 * three stacked slabs around a glowing core. Every step is a hop: a squat wind-up, a stretch tall as it leaves the
	 * ground, squashed wide and flat on landing, wobbling out; at rest it jiggles with its breath. Falls stretch it, a
	 * landing flattens it, sneaking squats it down. The slabs spread apart as it stretches and close as it squashes, the
	 * core showing between, and the upper ones lag behind a turn or a stride. Its attack is a lunge: it flattens and
	 * thrusts forward.
	 */
	static final class Ooze extends FormModel {
		// ---- tuning ----
		/** The hop: radians of clock per unit of walk, how much walking speed it takes to hop fully, and the arc's height (pixels). */
		static final float HOP_RATE = 0.6662F * 0.8F, SPEED_GAIN = 1.4F, HOP_HEIGHT = 2.5F;
		/** The hop's timeline (fractions of a cycle): the wind-up ends, the launch ends, the flight ends, the landing squash ends (then it wobbles out). */
		static final float WINDUP_END = 0.22F, LAUNCH_END = 0.36F, FLIGHT_END = 0.74F, LAND_END = 0.84F;
		/** How squat the wind-up and the sneak go (squash 0-1), and how much of the stretch is left at the end of the flight. */
		static final float WINDUP_SQUASH = 0.55F, SNEAK_SQUASH = 0.55F, FLIGHT_END_STRETCH = 0.25F;
		/** How much the height changes at full squash (down) and full stretch (up), and the widest it spreads (fraction). */
		static final float SQUASH = 0.32F, STRETCH = 0.28F, MAX_WIDEN = 1.25F;
		/** Falling: the stretch with no fall speed and the extra at full fall speed; how hard a landing squashes it. */
		static final float AIR_STRETCH = 0.2F, FALL_STRETCH = 0.7F, LAND_SQUASH = 1.0F;
		/** The rest wobble: two jiggles (fractions of the squash), hushed as it moves. */
		static final float WOBBLE = 0.05F, WOBBLE2 = 0.025F;
		/** How far it leans into a hop's stretch (radians) and rolls with a turn. */
		static final float LEAN = 0.14F, ROLL = 0.06F;
		/** The lunge (attack): how far it thrusts forward (pixels), how much longer and flatter it goes, how far the core lags behind it. */
		static final float LUNGE = 3.0F, LUNGE_STRETCH = 0.35F, LUNGE_SQUASH = 0.25F, LUNGE_CORE_LAG = 1.0F;
		/** The slabs (Magma Ooze): thickness, the gap between them at rest, fully squashed and fully stretched, how much a squash thins them, and how far the upper ones lag (x with a turn, z with a stride, per slab). */
		static final float SLAB_H = 2.0F, GAP_REST = 1.0F, GAP_SQUASH = 0.1F, GAP_STRETCH = 2.0F, SLAB_THIN = 0.2F, SHEAR_X = 0.6F, SHEAR_Z = 0.45F, SLAB_TWIST = 0.1F;
		/** The cube's half width (pixels) and the jelly's height at rest; Ooze's core is this high above the ground at its middle. */
		private static final float HALF = 5.0F, SHELL_H = 8.0F, CORE_MID = 4.0F;
		/** Ooze's core may come no closer than this to the inside of the shell (pixels, unscaled); Magma Ooze's core is this wide against the slabs, so a twisted, sheared stack never shows it through a slab's side. */
		private static final float CORE_WALL = 0.25F, CORE_FILL = 0.8F;
		/** Ooze's core's half sizes (pixels, unscaled: its 6x5x6 box). */
		private static final float CORE_HX = 3.0F, CORE_HY = 2.5F;
		private static final float HALF_PI = (float)Math.PI * 0.5F;
		private static final int SLABS = 3;

		private final boolean magma;
		private final ModelPart shell, core;
		private final ModelPart[] slabs = new ModelPart[SLABS];
		/** The hop's state this frame: squash (negative is stretch), then lift (pixels). */
		private final float[] hop = new float[2];

		Ooze(String id) {
			super(bake(mesh("magma_ooze".equals(id))), id);
			this.magma = "magma_ooze".equals(id);
			ModelPart root = this.root();
			if (this.magma) {
				this.shell = null;
				this.core = root.getChild("core");
				for (int i = 0; i < SLABS; i++) {
					this.slabs[i] = root.getChild("slab" + i);
				}
			} else {
				this.shell = root.getChild("shell");
				this.core = this.shell.getChild("core");
			}
			// the hidden body lies face-up on the top of the cube (as Skitter's does): the plate sits flush on top
			this.insigniaY = 0.0F;
			this.insigniaZ = 0.0F;
			this.insigniaSize = 0.9F;
		}

		/**
		 * Ooze: a 10x8x10 shell hinged at the ground with the 6x5x6 core inside it (the core a child, so it squashes
		 * with it). Magma Ooze: three 10x2x10 slabs, each hinged at its own underside, and an 8x8x8 core hinged at the
		 * ground that is stretched to run the whole height of the stack.
		 */
		private static MeshDefinition mesh(boolean magma) {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			if (magma) {
				for (int i = 0; i < SLABS; i++) {
					root.addOrReplaceChild("slab" + i, CubeListBuilder.create().texOffs(0, 64 + 12 * i).addBox(-5.0F, -2.0F, -5.0F, 10.0F, 2.0F, 10.0F),
						PartPose.offset(0.0F, 24.0F - i * 3.0F, 0.0F));
				}
				root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 100).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
					PartPose.offset(0.0F, 24.0F, 0.0F));
			} else {
				PartDefinition shell = root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -8.0F, -5.0F, 10.0F, 8.0F, 10.0F),
					PartPose.offset(0.0F, 24.0F, 0.0F));
				shell.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 84).addBox(-3.0F, -2.5F, -3.0F, 6.0F, 5.0F, 6.0F),
					PartPose.offset(0.0F, -CORE_MID, 0.0F));
			}
			return mesh;
		}

		/**
		 * The hop at {@code u} (0-1 through a cycle) into {@link #hop}: squash (+) or stretch (-), then lift. A wind-up
		 * squat, a launch that stretches before it leaves the ground, a flight that eases from stretched toward round,
		 * a landing squash and a damped wobble that ends level with where the wind-up begins.
		 */
		private void cycle(float u) {
			float sq;
			float lift = 0.0F;
			if (u < WINDUP_END) {
				sq = WINDUP_SQUASH * smooth(u / WINDUP_END);
			} else if (u < LAUNCH_END) {
				sq = Mth.lerp(smooth((u - WINDUP_END) / (LAUNCH_END - WINDUP_END)), WINDUP_SQUASH, -1.0F);
			} else if (u < FLIGHT_END) {
				float f = (u - LAUNCH_END) / (FLIGHT_END - LAUNCH_END);
				sq = Mth.lerp(f, -1.0F, -FLIGHT_END_STRETCH);
				lift = HOP_HEIGHT * Mth.sin(f * (float)Math.PI);
			} else if (u < LAND_END) {
				sq = Mth.lerp(smooth((u - FLIGHT_END) / (LAND_END - FLIGHT_END)), -FLIGHT_END_STRETCH, 1.0F);
			} else {
				float g = (u - LAND_END) / (1.0F - LAND_END);
				sq = (1.0F - g) * (1.0F - g) * Mth.cos(g * (float)Math.PI * 1.5F);
			}
			this.hop[0] = sq;
			this.hop[1] = lift;
		}

		private static float smooth(float s) {
			return s * s * (3.0F - 2.0F * s);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.body.visible = this.rightArm.visible = this.leftArm.visible = false;
			this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed * SPEED_GAIN, 0.0F, 1.0F);
			float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			boolean sneak = state.isCrouching || Boolean.TRUE.equals(state.getData(XenoformClient.SNEAK));
			// the hop clock follows the walk, so it hops as fast as it moves and settles when it stops
			float turns = state.walkAnimationPos * HOP_RATE / ((float)Math.PI * 2.0F);
			this.cycle(turns - (float)Math.floor(turns));
			float sq = this.hop[0] * speed;
			float lift = this.hop[1] * speed * (1.0F - air);
			if (sneak) {
				// squatted flat, only shuffling
				sq = SNEAK_SQUASH + 0.25F * sq;
				lift = 0.0F;
			}
			// a real fall or leap: stretched by how fast it goes; a landing squashes it flat
			float fall = Mth.clamp(Math.abs(m.fall()), 0.0F, 1.0F);
			sq = Mth.lerp(air, sq, -AIR_STRETCH - FALL_STRETCH * fall);
			sq += m.land() * LAND_SQUASH * (1.0F - air);
			sq += (WOBBLE * m.breath() + WOBBLE2 * Mth.sin(age * 0.23F + 1.0F)) * (1.0F - speed);
			sq = Mth.clamp(sq, -1.0F, 1.25F);
			if (this.magma) {
				this.slabs(sq, lift, speed, swing, m);
			} else {
				this.jelly(sq, lift, speed, swing, age, m);
			}
		}

		/** Ooze: the shell squashes and stretches about its base, tilts into the hop, and the core follows a beat behind. */
		private void jelly(float sq, float lift, float speed, float swing, float age, XenoMotion.Read m) {
			float ys = (sq >= 0.0F ? 1.0F - SQUASH * sq : 1.0F - STRETCH * sq);
			// volume kept: flatter is wider
			float wide = Math.min(MAX_WIDEN, 1.0F / (float)Math.sqrt(ys));
			float xs = wide * (1.0F - 0.08F * swing);
			float zs = wide * (1.0F + LUNGE_STRETCH * swing);
			float ysF = ys * (1.0F - LUNGE_SQUASH * swing);
			float lean = LEAN * speed * Math.max(0.0F, -sq);
			float roll = -m.turn() * ROLL;
			// tipped, it rests on its lower edge: raise it so the edge, not the middle, stays on the ground
			float raise = HALF * (zs * Math.abs(Mth.sin(lean)) + xs * Math.abs(Mth.sin(roll)));
			this.shell.setPos(0.0F, 24.0F - lift - raise, -swing * LUNGE);
			this.shell.xRot = lean;
			this.shell.zRot = roll;
			this.shell.xScale = xs;
			this.shell.yScale = ysF;
			this.shell.zScale = zs;
			// the hidden body is the insignia's anchor: face-up on the middle of the cube's top, carried round by its tilt
			// (the shell turns Z*X, as ModelPart does: the top's middle (0, -h, 0) lands at (h cos l sin r, -h cos l cos r, -h sin l))
			float h = SHELL_H * ysF;
			this.body.setPos(this.shell.x + h * Mth.cos(lean) * Mth.sin(roll), this.shell.y - h * Mth.cos(lean) * Mth.cos(roll),
				this.shell.z - h * Mth.sin(lean));
			this.body.xRot = lean - HALF_PI;
			this.body.zRot = roll;
			// the core squashes only half as much as the jelly around it, sloshes, trails behind a stride and a fall's lift
			float ox = Mth.sin(age * 0.17F) * 0.5F + m.turn() * 0.8F;
			float oy = Mth.sin(age * 0.21F + 0.7F) * 0.4F - m.fall() * 0.6F;
			float oz = speed * 0.8F + swing * LUNGE_CORE_LAG;
			this.core.setPos(ox, -CORE_MID + oy, oz);
			// (in the shell's space, so its walls are HALF and SHELL_H / 2 from the middle whatever its scale): never through a wall
			this.core.xScale = Math.min(0.5F * (1.0F + 1.0F / xs), (HALF - CORE_WALL - Math.abs(ox)) / CORE_HX);
			this.core.yScale = Math.min(0.5F * (1.0F + 1.0F / ysF), (SHELL_H * 0.5F - CORE_WALL - Math.abs(oy)) / CORE_HY);
			this.core.zScale = Math.min(0.5F * (1.0F + 1.0F / zs), (HALF - CORE_WALL - Math.abs(oz)) / CORE_HX);
		}

		/**
		 * Magma Ooze: the slabs stack from the ground, the gaps between them opening as it stretches and closing as it
		 * squashes; the core runs the whole height of the stack, so it shows in the gaps. Upper slabs lag behind turns and
		 * strides, and lead a lunge.
		 */
		private void slabs(float sq, float lift, float speed, float swing, XenoMotion.Read m) {
			float thin = 1.0F - SLAB_THIN * Math.max(0.0F, sq);
			float gap = sq >= 0.0F ? Mth.lerp(Math.min(1.0F, sq), GAP_REST, GAP_SQUASH) : Mth.lerp(-sq, GAP_REST, GAP_STRETCH);
			gap *= 1.0F - 0.5F * swing;
			float thick = SLAB_H * thin;
			float total = SLABS * thick + (SLABS - 1) * gap;
			float rest = SLABS * SLAB_H + (SLABS - 1) * GAP_REST;
			float wide = Math.min(MAX_WIDEN, 1.0F / (float)Math.sqrt(total / rest));
			float xs = wide * (1.0F - 0.08F * swing);
			float zs = wide * (1.0F + LUNGE_STRETCH * swing);
			for (int i = 0; i < SLABS; i++) {
				ModelPart slab = this.slabs[i];
				slab.setPos(m.trail() * SHEAR_X * i, 24.0F - lift - i * (thick + gap), speed * SHEAR_Z * i - swing * LUNGE * (0.4F + 0.2F * i));
				slab.yRot = m.turn() * SLAB_TWIST * i;
				slab.xScale = xs;
				slab.yScale = thin;
				slab.zScale = zs;
			}
			ModelPart top = this.slabs[SLABS - 1];
			ModelPart mid = this.slabs[SLABS / 2];
			this.body.setPos(top.x, top.y - thick, top.z);
			this.body.xRot = -HALF_PI;
			this.body.yRot = top.yRot;
			// the core is a hair inside the stack's ends, turned with the middle slab and narrower than the slabs, so it never
			// pokes through a twisted, sheared one
			this.core.setPos(mid.x, 24.0F - lift - 0.1F, mid.z);
			this.core.yRot = mid.yRot;
			this.core.yScale = (total - 0.2F) / 8.0F;
			this.core.xScale = xs * CORE_FILL;
			this.core.zScale = zs * CORE_FILL;
		}
	}

	/**
	 * (0.18.117, Scan F; 0.19.34 four-legged) Fuse: a creeper's build, no arms: a blocky head on a tall narrow body
	 * on four stubby legs. It moves its own way: quick shuffling steps, diagonal legs together, with a side-to-side
	 * waddle and a bounce, the head nodding a beat behind; when it punches (every punch pops) it swells up for a moment.
	 */
	static final class Fuse extends FormModel {
		// ---- tuning ----
		/** How fast its little legs shuffle against the walk, and how far they swing. */
		static final float SHUFFLE = 0.6662F * 1.6F, STEP = 1.2F;
		/** The waddle (roll), the bounce (pixels) and the head's nod. */
		static final float WADDLE = 0.12F, BOUNCE = 0.7F, NOD = 0.1F;
		/** How much it swells on a punch. */
		static final float PUNCH_SWELL = 0.18F;

		private final ModelPart hindRight, hindLeft;

		Fuse() {
			super(bake(mesh()), "fuse");
			this.insigniaZ = -2.0F;
			this.hindRight = this.root().getChild("hind_right_leg");
			this.hindLeft = this.root().getChild("hind_left_leg");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				PartPose.offset(0.0F, 6.0F, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
				PartPose.offset(0.0F, 6.0F, 0.0F));
			// the front legs are the man's legs (so the pose code still finds them), the hind pair new
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(-2.0F, 18.0F, -4.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(2.0F, 18.0F, -4.0F));
			root.addOrReplaceChild("hind_right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(-2.0F, 18.0F, 4.0F));
			root.addOrReplaceChild("hind_left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F),
				PartPose.offset(2.0F, 18.0F, 4.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = false;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.hindRight, this.hindLeft}) {
				part.resetPose();
			}
			float walk = state.walkAnimationPos * SHUFFLE;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float step = Mth.cos(walk) * STEP * speed;
			// diagonal pairs together: front right with hind left
			this.rightLeg.xRot = step;
			this.hindLeft.xRot = step;
			this.leftLeg.xRot = -step;
			this.hindRight.xRot = -step;
			float waddle = Mth.sin(walk) * WADDLE * speed;
			float bounce = Mth.abs(Mth.cos(walk)) * BOUNCE * speed;
			float crouch = state.isCrouching ? 2.0F : 0.0F;
			this.body.y = 6.0F - bounce + crouch;
			this.body.zRot = waddle;
			this.head.y = 6.0F - bounce + crouch;
			this.head.zRot = waddle * 1.4F;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			// the nod comes a beat after the step
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD + Mth.sin(walk * 2.0F - 0.8F) * NOD * speed;
			float swell = 1.0F + Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI) * PUNCH_SWELL;
			for (ModelPart part : new ModelPart[] {this.head, this.body}) {
				part.xScale = part.yScale = part.zScale = swell;
			}
		}
	}

	/**
	 * (0.18.117, Scan F; 0.19.84 a treant on root feet) Thornwild: a crown of twigs, and thorns along its arms. Now a
	 * treant, not a man: a trunk of bark, wider at the base and burled at the chest, a hump on its back; a knotted face
	 * (heavy brow, a knot of a nose, two amber eyes) sunk between mossy shoulders; long branching arms with twig
	 * fingers, a side branch and thorns; and leafy twigs on its head, shoulders and back that sway, trail behind when it
	 * moves and stir on a turn. Its legs are posts that end in ROOT FEET: a mound with five roots splaying out (each
	 * ending in a thinner rootlet) that stay flat on the ground. It walks slowly, creaking: short steps, the feet lifting
	 * little and gripping flat, the trunk rocking over the planted foot and twisting a little with each step, the arms
	 * swinging short. The man's parts stay (the head, the legs, the arms reshaped; the body part IS the trunk's chest
	 * burl, so the insignia plate sits flat on it) and take the player's own pose, so held items, swings
	 * and the first-person arm work. Roots ({@code XenoformClient.ACT} "roots"): it bows over and drives both arms
	 * overhead and down into the ground in a beat every {@link #ROOT_BEAT} ticks, fingers spread; Bloom ("bloom"): it
	 * arches back with its arms flung wide and its leaves flourish.
	 */
	static final class Thornwild extends FormModel {
		// ---- tuning ----
		/** Its lean (radians, + is forward): standing, the extra at a walk, crouched, at the peak of an attack's lunge, after a hard landing. */
		static final float LEAN = 0.10F, RUN_LEAN = 0.07F, CROUCH_LEAN = 0.38F, LUNGE_LEAN = 0.22F, LAND_LEAN = 0.2F;
		/** A crouching player is drawn this many pixels lower (AvatarRenderer.getRenderOffset): the ground is that much higher in model space; the extra squat (pixels) and the legs' splay (radians) then. */
		static final float CROUCH_SINK = 2.0F, CROUCH_SQUAT = 1.5F, CROUCH_SPLAY = 0.14F;
		/** The walk: radians of clock per unit of walk (slow), how far a leg swings (radians), what is left of that crouched, how high a stepping foot lifts (pixels), how far the arms swing against the legs. */
		static final float GAIT = 0.6662F * 0.75F, SWING = 0.34F, CROUCH_STRIDE = 0.6F, LIFT = 0.9F, ARM_SWING = 0.4F;
		/** How much of the leg's swing the foot cancels to stay flat, how far the toes curl up as the foot lifts (radians per pixel), and how far the feet hang forward in the air (radians). */
		static final float FOOT_KEEP = 0.8F, TOE_CURL = 0.12F, AIR_REACH = 0.15F;
		/** The trunk's rock over the planted foot and its twist with each step (radians), how far a landing sinks it and how far it breathes (pixels), the arms' slight lean outward (radians). */
		static final float SWAY = 0.045F, TWIST = 0.09F, LAND_DROP = 1.2F, BREATH = 0.15F, ARM_OUT = 0.07F;
		/** The head: how far it sits in the shoulders (down, forward; pixels) and how far it moves forward with the lean (pixels per radian). */
		static final float HEAD_SINK = 1.0F, HEAD_FORWARD = 1.0F, HEAD_LEAN = 3.0F;
		/** Roots: ticks between blows, the part of the beat spent rearing up and slamming down, the arms' raised and slammed angles (radians), the lean, how far it rears back, the arms' and legs' spread. */
		static final float ROOT_BEAT = 20.0F, RAISE_END = 0.65F, SLAM_END = 0.8F, ROOT_UP = -2.6F, ROOT_DOWN = -0.3F;
		static final float ROOT_LEAN = 0.55F, ROOT_LEAN_BACK = 0.4F, ROOT_SPREAD = 0.18F, ROOT_SPLAY = 0.1F, ROOT_GRIP = 0.3F;
		/** Bloom: the arms' angle out to the sides, how far it arches back (radians), how much bigger the leaves swell. */
		static final float BLOOM_ARMS = 2.1F, BLOOM_ARCH = 0.12F, BLOOM_FLOURISH = 0.3F;
		/** The leaves: radians of clock a tick, the stalks' sway, how far they trail when it walks and lean into a turn, the leaf's own flutter. */
		static final float LEAF_RATE = 0.045F, LEAF_SWAY = 0.09F, LEAF_TRAIL = 0.3F, LEAF_TURN = 0.25F, LEAF_FLUTTER = 0.16F;
		/** The body's length from its shoulder pivot to the hips, and the shoulder's drop below the pivot (pixels). */
		private static final float BODY_LEN = 12.0F, SHOULDER = 2.0F;
		/**
		 * The legs: half the gap between them (the two 7-wide root mounds then stand a hair apart instead of overlapping
		 * flush and flickering), the foot's pivot below the hip, the toes' thickness, the farthest a rootlet's tip reaches
		 * from the pivot, the bottom of the leg post and its half depth.
		 */
		private static final float LEG_X = 3.45F, PIVOT = 9.0F, TOE_H = 3.0F, FOOT_R = 6.5F, LEG_BOTTOM = 11.5F, LEG_HALF = 2.5F;

		/** The stalks and leaves that sway, the fingers, the rootlets, and the two foot groups. */
		private final ModelPart[] stalks, leaves, fingers, tips;
		private final ModelPart footRight, footLeft;

		Thornwild() {
			super(bake(mesh()), "thornwild");
			// flat on the front of the chest burl, between the shoulders
			this.insigniaY = 4.5F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 1.0F;
			ModelPart branchR = this.head.getChild("branch_r");
			ModelPart branchL = this.head.getChild("branch_l");
			ModelPart sprout = this.head.getChild("sprout");
			ModelPart sprigR = this.rightArm.getChild("sprig");
			ModelPart sprigL = this.leftArm.getChild("sprig");
			ModelPart backR = this.body.getChild("back_r");
			ModelPart backL = this.body.getChild("back_l");
			ModelPart twigR = branchR.getChild("twig");
			ModelPart twigL = branchL.getChild("twig");
			this.stalks = new ModelPart[] {branchR, branchL, twigR, twigL, sprout, sprigR, sprigL, backR, backL};
			this.leaves = new ModelPart[] {branchR.getChild("leaf"), branchL.getChild("leaf"), twigR.getChild("leaf"), twigL.getChild("leaf"),
				sprout.getChild("leaf"), sprigR.getChild("leaf"), sprigL.getChild("leaf"), backR.getChild("leaf"), backL.getChild("leaf")};
			this.fingers = new ModelPart[6];
			this.tips = new ModelPart[10];
			this.footRight = this.rightLeg.getChild("foot");
			this.footLeft = this.leftLeg.getChild("foot");
			for (int j = 0; j < 3; j++) {
				this.fingers[j] = this.rightArm.getChild("finger" + j);
				this.fingers[3 + j] = this.leftArm.getChild("finger" + j);
			}
			for (int i = 0; i < 5; i++) {
				this.tips[i] = this.footRight.getChild("toe" + i).getChild("tip");
				this.tips[5 + i] = this.footLeft.getChild("toe" + i).getChild("tip");
			}
		}

		/** A twig (1x5x1) pointing up from its pivot with a leaf (4x1x4) at its tip. */
		private static PartDefinition sprig(PartDefinition parent, String name, float x, float y, float z, float xRot, float zRot) {
			PartDefinition twig = parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(44, 97).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F),
				PartPose.offsetAndRotation(x, y, z, xRot, 0.0F, zRot));
			twig.addOrReplaceChild("leaf", CubeListBuilder.create().texOffs(28, 99).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offset(0.0F, -4.6F, 0.0F));
			return twig;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head: a knotted block with a heavy brow and a knot of a nose, antlers of branches and a sprout on top, each tipped with leaves
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				PartPose.ZERO);
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(32, 73).addBox(-4.5F, -7.0F, -5.5F, 9.0F, 2.0F, 2.0F), PartPose.ZERO);
			head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(54, 64).addBox(-1.0F, -4.0F, -6.0F, 2.0F, 3.0F, 2.0F), PartPose.ZERO);
			for (float s : new float[] {-1.0F, 1.0F}) {
				String side = s < 0.0F ? "r" : "l";
				PartDefinition branch = head.addOrReplaceChild("branch_" + side, CubeListBuilder.create().texOffs(54, 72).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
					PartPose.offsetAndRotation(s * 2.6F, -7.6F, 0.5F, -0.15F, 0.0F, s * 0.5F));
				branch.addOrReplaceChild("leaf", CubeListBuilder.create().texOffs(28, 99).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 1.0F, 4.0F), PartPose.offset(0.0F, -6.6F, 0.0F));
				sprig(branch, "twig", 0.0F, -3.5F, 0.0F, -0.1F, s * 0.9F);
			}
			sprig(head, "sprout", 0.0F, -7.6F, 0.5F, -0.1F, 0.0F);
			// the trunk: the chest burl is the body (the insignia sits on its front), a wider base below it, a hump on the back with two leafy sprigs
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, 0.0F, -3.0F, 10.0F, 9.0F, 6.0F),
				PartPose.ZERO);
			body.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 80).addBox(-6.0F, 8.0F, -3.5F, 12.0F, 5.0F, 7.0F), PartPose.ZERO);
			body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(32, 64).addBox(-4.0F, 1.0F, 2.5F, 8.0F, 6.0F, 3.0F), PartPose.ZERO);
			sprig(body, "back_r", -2.5F, 2.5F, 5.0F, -0.6F, -0.25F);
			sprig(body, "back_l", 2.5F, 2.5F, 5.0F, -0.6F, 0.25F);
			// the arms: thick bark, a mossy pad on each shoulder with a sprig, a side branch at the elbow, thorns down the outside, three twig fingers
			for (float s : new float[] {-1.0F, 1.0F}) {
				boolean right = s < 0.0F;
				float cx = s;
				PartDefinition arm = root.addOrReplaceChild(right ? "right_arm" : "left_arm", CubeListBuilder.create().texOffs(right ? 40 : 32, right ? 16 : 48)
					.addBox(right ? -3.0F : -1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.5F)), PartPose.offset(s * 5.0F, 2.0F, 0.0F));
				arm.addOrReplaceChild("moss", CubeListBuilder.create().texOffs(38, 81).addBox(cx - 2.5F, -4.3F, -3.0F, 5.0F, 2.0F, 6.0F,
					new CubeDeformation(0.1F)), PartPose.ZERO);
				sprig(arm, "sprig", cx, -4.1F, 0.0F, -0.15F, s * 0.35F);
				arm.addOrReplaceChild("limb", CubeListBuilder.create().texOffs(44, 97).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
					PartPose.offsetAndRotation(cx + s * 2.3F, 6.0F, 0.5F, 0.0F, 0.0F, -s * 0.8F));
				for (int i = 0; i < 3; i++) {
					arm.addOrReplaceChild("thorn" + i, CubeListBuilder.create().texOffs(54, 69).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F),
						PartPose.offsetAndRotation(cx + s * 2.3F, 1.0F + 3.2F * i, -0.5F + 0.5F * i, 0.35F, s * 1.2F, 0.0F));
				}
				for (int j = 0; j < 3; j++) {
					arm.addOrReplaceChild("finger" + j, CubeListBuilder.create().texOffs(44, 97).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 5.0F, 1.0F),
						PartPose.offsetAndRotation(cx + (j - 1) * 1.2F, 10.0F, j == 1 ? 0.5F : -0.5F, j == 1 ? 0.0F : -0.12F, 0.0F, -(j - 1) * 0.22F));
				}
			}
			// the legs: bark posts ending in a mound with five roots splaying out, each with a rootlet at its end
			for (float s : new float[] {-1.0F, 1.0F}) {
				boolean right = s < 0.0F;
				PartDefinition leg = root.addOrReplaceChild(right ? "right_leg" : "left_leg", CubeListBuilder.create().texOffs(right ? 0 : 16, right ? 16 : 48)
					.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F, new CubeDeformation(0.5F)), PartPose.offset(s * LEG_X, 12.0F, 0.0F));
				PartDefinition foot = leg.addOrReplaceChild("foot", CubeListBuilder.create(), PartPose.offset(0.0F, PIVOT, 0.0F));
				foot.addOrReplaceChild("heel", CubeListBuilder.create().texOffs(0, 92).addBox(-3.5F, -1.0F, -3.5F, 7.0F, 4.0F, 7.0F, new CubeDeformation(-0.1F)), PartPose.ZERO);
				// front outer, front inner, side, back outer, back inner (yaw: 0 is straight back, PI straight forward)
				float[] yaw = {(float)Math.PI - s * 0.4F, (float)Math.PI + s * 0.35F, s * 1.5708F, s * 0.55F, -s * 0.4F};
				for (int i = 0; i < 5; i++) {
					PartDefinition toe = foot.addOrReplaceChild("toe" + i, CubeListBuilder.create().texOffs(28, 92).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 3.0F, 4.0F),
						PartPose.offsetAndRotation(0.0F, i * 0.02F, 0.0F, 0.0F, yaw[i], 0.0F));
					toe.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(40, 92).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 3.0F),
						PartPose.offsetAndRotation(0.0F, 1.02F, 3.5F, 0.0F, (i % 2 == 0 ? 0.25F : -0.25F) * s, 0.0F));
				}
			}
			return mesh;
		}

		/**
		 * How much of vanilla's walking arm swing an arm in this pose keeps (HumanoidModel.poseRightArm): all of it empty,
		 * half holding an item, a shield, a trident or a brush, none in an aimed pose that sets the arm outright.
		 */
		private static float walkKept(HumanoidModel.ArmPose pose) {
			return switch (pose) {
				case EMPTY -> 1.0F;
				case ITEM, BLOCK, THROW_TRIDENT, BRUSH -> 0.5F;
				default -> 0.0F;
			};
		}

		private static float smooth(float s) {
			return s * s * (3.0F - 2.0F * s);
		}

		/**
		 * Where a leg's hip sits (model y) for the lowest point of its root foot (or its post) to rest on {@code ground},
		 * less {@code lift}: the leg swung to {@code theta} and splayed out {@code splay}, the foot cancelling most of both.
		 */
		private static float plant(float theta, float splay, float lift, float ground) {
			float phi = theta * (1.0F - FOOT_KEEP);
			float roll = splay * (1.0F - FOOT_KEEP);
			float foot = (PIVOT * Mth.cos(theta) + TOE_H * Mth.cos(phi) + FOOT_R * Mth.abs(Mth.sin(phi))) * Mth.cos(splay) + FOOT_R * Mth.abs(Mth.sin(roll));
			float post = (LEG_BOTTOM * Mth.cos(theta) + LEG_HALF * Mth.abs(Mth.sin(theta))) * Mth.cos(splay) + LEG_HALF * Mth.abs(Mth.sin(splay));
			return ground - Math.max(foot, post) - lift;
		}

		/** One leg: hung from its hip, swung to {@code theta}, splayed, its root foot cancelling most of that so it lies flat (the toes curling as it lifts). */
		private void leg(ModelPart leg, ModelPart foot, float side, float theta, float splay, float lift, float hip, float x, float z) {
			leg.x = side * LEG_X + x;
			leg.y = hip;
			leg.z = z;
			leg.xRot = theta;
			leg.zRot = -side * splay;
			foot.xRot = -theta * FOOT_KEEP - lift * TOE_CURL;
			foot.zRot = side * splay * FOOT_KEEP;
		}

		/** The swaying parts: leafy stalks, leaves, fingers and rootlets (each from its rest pose). */
		private void stir(float age, XenoMotion.Read m, float speed, boolean bloom, boolean roots) {
			float flourish = bloom ? BLOOM_FLOURISH : 0.0F;
			for (int i = 0; i < this.stalks.length; i++) {
				ModelPart stalk = this.stalks[i];
				stalk.resetPose();
				float p = age * LEAF_RATE + i * 1.7F;
				// they trail behind a walk (a stalk points up: - is back) and lean into a turn
				stalk.xRot += Mth.sin(p) * LEAF_SWAY - speed * LEAF_TRAIL;
				stalk.zRot += Mth.cos(p * 0.8F) * LEAF_SWAY - m.trail() * LEAF_TURN;
			}
			for (int i = 0; i < this.leaves.length; i++) {
				ModelPart leaf = this.leaves[i];
				leaf.resetPose();
				float p = age * LEAF_RATE * 2.3F + i * 1.1F;
				leaf.xRot += Mth.sin(p) * LEAF_FLUTTER;
				leaf.zRot += Mth.cos(p * 0.83F) * LEAF_FLUTTER;
				leaf.xScale = leaf.zScale = 1.0F + flourish + Mth.sin(p * 0.5F) * 0.04F;
			}
			for (int i = 0; i < this.fingers.length; i++) {
				ModelPart finger = this.fingers[i];
				finger.resetPose();
				// idle: a slow flex; under Roots they spread wide to grip
				finger.xRot += Mth.sin(age * 0.05F + i * 1.1F) * 0.07F;
				finger.zRot += Math.signum(finger.zRot) * (roots ? ROOT_GRIP : 0.0F);
			}
			for (int i = 0; i < this.tips.length; i++) {
				ModelPart tip = this.tips[i];
				tip.resetPose();
				tip.yRot += Mth.sin(age * 0.025F + i * 0.9F) * 0.12F;
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			String act = state.getData(XenoformClient.ACT);
			boolean roots = "roots".equals(act);
			boolean bloom = "bloom".equals(act);
			this.stir(age, m, speed, bloom, roots);
			if (state.isFallFlying || state.isVisuallySwimming || state.isPassenger) {
				// flying, swimming or riding: vanilla's pose, the leaves still stirring
				return;
			}
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			float run = speed * (1.0F - air);
			boolean crouch = state.isCrouching;
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float lunge = Mth.sin(swing * Mth.PI);
			float land = Math.max(0.0F, m.land());
			// vanilla twists the body (and moves the arms' roots) only while a swing runs
			float twist = swing > 0.0F ? this.body.yRot : 0.0F;
			float rightZ = swing > 0.0F ? this.rightArm.z : 0.0F;
			float leftZ = swing > 0.0F ? this.leftArm.z : 0.0F;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightLeg, this.leftLeg, this.footRight, this.footLeft}) {
				part.resetPose();
			}
			float ground = 24.0F - (crouch ? CROUCH_SINK : 0.0F);
			float phase = state.walkAnimationPos * GAIT;
			float cos = Mth.cos(phase);
			float sin = Mth.sin(phase);
			// Roots: 0 slammed down, 1 reared up, one blow every ROOT_BEAT ticks
			float up = 0.0F;
			if (roots) {
				float u = (age / ROOT_BEAT) % 1.0F;
				up = u < RAISE_END ? smooth(u / RAISE_END)
					: u < SLAM_END ? 1.0F - smooth((u - RAISE_END) / (SLAM_END - RAISE_END)) : 0.0F;
			}

			// ---- the trunk's lean and rock ----
			float lean = LEAN + run * RUN_LEAN + (crouch ? CROUCH_LEAN : 0.0F) + lunge * LUNGE_LEAN + land * LAND_LEAN;
			if (roots) {
				lean = ROOT_LEAN - up * ROOT_LEAN_BACK;
			} else if (bloom) {
				lean = -BLOOM_ARCH;
			}
			// it rocks over the planted foot: the right foot is planted while its leg swings back (cos > 0)
			float sway = -cos * SWAY * run;

			// ---- the legs: right back as left comes forward; a foot lifts as its leg swings forward ----
			float splay = (crouch ? CROUCH_SPLAY : 0.0F) + (roots ? ROOT_SPLAY : 0.0F);
			float stride = SWING * run * (crouch ? CROUCH_STRIDE : 1.0F);
			float thetaRight = cos * stride - AIR_REACH * air;
			float thetaLeft = -cos * stride - AIR_REACH * air;
			float liftScale = LIFT * run * (crouch ? 0.6F : 1.0F);
			float liftRight = liftScale * Math.max(0.0F, sin);
			float liftLeft = liftScale * Math.max(0.0F, -sin);
			float hipRight = plant(thetaRight, splay, liftRight, ground);
			float hipLeft = plant(thetaLeft, splay, liftLeft, ground);
			// the hips stay under the tipped trunk
			float hipX = -BODY_LEN * Mth.sin(sway) * Mth.cos(lean);
			float hipZ = BODY_LEN * Mth.sin(lean);
			this.leg(this.rightLeg, this.footRight, -1.0F, thetaRight, splay, liftRight, hipRight, hipX, hipZ);
			this.leg(this.leftLeg, this.footLeft, 1.0F, thetaLeft, splay, liftLeft, hipLeft, hipX, hipZ);

			// ---- the body: hung from the hips (the shoulder pivot is at the top of the chest), sinking on a landing, a crouch ----
			this.body.y = (hipRight + hipLeft) * 0.5F - BODY_LEN * Mth.cos(lean) * Mth.cos(sway) + land * LAND_DROP + (crouch ? CROUCH_SQUAT : 0.0F)
				+ m.breath() * BREATH;
			this.body.xRot = lean;
			this.body.yRot = twist + sin * TWIST * run;
			this.body.zRot = sway;

			// ---- the arms: on the shoulders of the tipped trunk ----
			float shoulderY = this.body.y + SHOULDER * Mth.cos(lean);
			this.rightArm.y = this.leftArm.y = shoulderY;
			this.rightArm.z = rightZ + SHOULDER * Mth.sin(lean);
			this.leftArm.z = leftZ + SHOULDER * Mth.sin(lean);
			if (roots) {
				// reared overhead, then driven down into the ground, fingers spread
				float a = Mth.lerp(up, ROOT_DOWN, ROOT_UP);
				this.rightArm.xRot = this.leftArm.xRot = a;
				this.rightArm.yRot = this.leftArm.yRot = 0.0F;
				this.rightArm.zRot = ROOT_SPREAD;
				this.leftArm.zRot = -ROOT_SPREAD;
			} else if (bloom) {
				float flap = Mth.sin(age * 0.08F) * 0.06F;
				this.rightArm.xRot = this.leftArm.xRot = -0.25F;
				this.rightArm.yRot = this.leftArm.yRot = 0.0F;
				this.rightArm.zRot = BLOOM_ARMS + flap;
				this.leftArm.zRot = -BLOOM_ARMS - flap;
			} else {
				// swap vanilla's quick walking swing (right arm -cos, left +cos, the speed over speedValue) for its slow one,
				// as much of it as the arm's pose kept (an attack's swing and the crouch are added on top, so they stay)
				boolean twoHanded = state.rightArmPose.isTwoHanded() || state.leftArmPose.isTwoHanded();
				float vanilla = Mth.cos(state.walkAnimationPos * 0.6662F) * state.walkAnimationSpeed / state.speedValue;
				float own = cos * ARM_SWING * run;
				this.rightArm.xRot += (twoHanded ? 0.0F : walkKept(state.rightArmPose)) * (vanilla - own);
				this.leftArm.xRot += (twoHanded ? 0.0F : walkKept(state.leftArmPose)) * (own - vanilla);
				this.rightArm.zRot += ARM_OUT;
				this.leftArm.zRot -= ARM_OUT;
			}

			// ---- the head: sunk between the shoulders, turning where it looks ----
			// its neck is a point of the trunk's top (HEAD_SINK down, a little forward and more forward as it hunches), carried round with the lean
			float neckZ = -HEAD_FORWARD - lean * HEAD_LEAN;
			this.head.y = this.body.y + HEAD_SINK * Mth.cos(lean) - neckZ * Mth.sin(lean);
			this.head.z = HEAD_SINK * Mth.sin(lean) + neckZ * Mth.cos(lean);
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F);
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD - lean * 0.5F + Mth.sin(age * 0.02F) * 0.02F;
			this.head.zRot = -sway * 0.8F + Mth.sin(age * 0.031F) * 0.02F;
		}
	}

	/**
	 * (0.18.117, Scan F; 0.19.34 a real cloud) Billow: a cloud, not a man: a broad soft mass low over the ground with
	 * its calm face in a puff at the front (it turns where it looks), and heaps of smaller puffs churning on top and
	 * swelling out of its sides, each breathing and drifting on its own. Moving, it leans into the way it goes and its
	 * top trails behind. Its box is the swimming pose's (it seeps through gaps like smoke: {@code BillowCloudMixins}),
	 * and its render state is turned back upright ({@code XenoformClient.extract}). The humanoid parts stay, hidden:
	 * the body is the mass and the head the face puff.
	 */
	static final class Billow extends FormModel {
		// ---- tuning ----
		/** How far the puffs bob and breathe, how fast they churn, and how far the top trails when it moves. */
		static final float BOB = 0.7F, BREATHE = 0.07F, CHURN = 0.06F, TRAIL = 2.0F, LEAN = 0.12F;
		/** The puffs: where each sits (x, y, z) and which box it is (0 big, 1 small). */
		private static final float[][] PUFFS = {
			{-5.5F, 12.5F, 2.0F, 0}, {5.5F, 12.0F, 3.0F, 0}, {0.0F, 11.5F, 6.0F, 1}, {-10.0F, 18.0F, 0.0F, 1}, {10.0F, 18.5F, -1.0F, 1},
			{0.0F, 13.0F, -2.0F, 1}};

		private final ModelPart[] puffs = new ModelPart[PUFFS.length];

		Billow() {
			super(bake(mesh()), "billow");
			for (int i = 0; i < PUFFS.length; i++) {
				this.puffs[i] = this.root().getChild("puff" + i);
			}
			// on the front of the mass, under the face
			this.insigniaY = 1.0F;
			this.insigniaZ = -9.0F;
			this.insigniaSize = 0.9F;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F,
				new CubeDeformation(1.5F)), PartPose.offset(0.0F, 15.0F, -4.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 64).addBox(-9.0F, -3.5F, -7.0F, 18.0F, 7.0F, 14.0F,
				new CubeDeformation(2.0F)), PartPose.offset(0.0F, 18.0F, 0.0F));
			for (int i = 0; i < PUFFS.length; i++) {
				float[] p = PUFFS[i];
				CubeListBuilder puff = p[3] == 0
					? CubeListBuilder.create().texOffs(0, 86).addBox(-5.0F, -4.0F, -5.0F, 10.0F, 8.0F, 10.0F, new CubeDeformation(1.0F))
					: CubeListBuilder.create().texOffs(0, 104).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(1.0F));
				root.addOrReplaceChild("puff" + i, puff, PartPose.offset(p[0], p[1], p[2]));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.head.resetPose();
			this.body.resetPose();
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			// it leans into the way it goes, the whole mass rolling slowly as it hangs
			this.body.xRot = speed * LEAN;
			this.body.zRot = Mth.sin(age * 0.03F) * 0.03F;
			this.body.y = 18.0F + Mth.sin(age * 0.05F) * 0.4F;
			float big = 1.0F + Mth.sin(age * 0.04F) * BREATHE * 0.5F;
			this.body.xScale = this.body.zScale = big;
			this.head.y = 15.0F + Mth.sin(age * 0.05F + 0.6F) * 0.5F;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
			for (int i = 0; i < PUFFS.length; i++) {
				float[] p = PUFFS[i];
				ModelPart puff = this.puffs[i];
				float t = age * CHURN + i * 1.7F;
				// the top ones trail behind as it moves
				float trail = p[1] < 15.0F ? speed * TRAIL : speed * TRAIL * 0.4F;
				puff.x = p[0] + Mth.sin(t * 0.7F) * 0.5F;
				puff.y = p[1] + Mth.sin(t) * BOB;
				puff.z = p[2] + trail + Mth.cos(t * 0.8F) * 0.4F;
				puff.yRot = Mth.sin(t * 0.3F) * 0.25F;
				float swell = 1.0F + Mth.sin(t * 1.3F) * BREATHE;
				puff.xScale = puff.yScale = puff.zScale = swell;
			}
		}
	}

	/**
	 * (0.18.119, Scan G) Towerback: a hulk of stone, a heavy brow, mossy boulders for pauldrons, and a broken watchtower
	 * grown out of its back, its battlements on top. It plays its grab
	 * ({@link com.jaymospowers.power.xenoform.forms.Towerback#grabPhase}): the right hand rears back, comes down, holds its
	 * catch at its chest and hurls it into the ground.
	 * <p>
	 * (0.19.28) Andy: "long exaggerated steps and motions while still covering alot of ground", and "the backhand should be
	 * slow too, like a charged ability". Its legs follow its own stride over the ground ({@link XenoformClient#TOWER_STRIDE},
	 * a step every {@code Towerback#STRIDE_PER_SCALE} blocks per scale) instead of vanilla's walk, swung wide and lifted;
	 * it sinks onto each footfall so its feet stay planted, its weight rolls over the planted foot, its shoulders turn and
	 * its arms swing big against its legs. The swat ({@link XenoformClient#TOWER_SWAT}) draws the right arm across its
	 * chest and coils, trembling, then whips it out wide.
	 */
	static final class Towerback extends FormModel {
		/**
		 * (0.19.28) Its walk (radians, pixels): how far the legs swing (wide of what the stride needs, so it reads as
		 * striding), how high the foot coming forward lifts, its legs' length (for how far it sinks onto a footfall), and
		 * an extra drop as the foot lands; its arms' swing and flare, its roll over the planted foot, its shoulders' turn,
		 * its lean into the walk.
		 */
		static final float LEG_SWING = 0.85F, FOOT_LIFT = 2.5F, LEG_LENGTH = 12.0F, LAND_DROP = 1.2F;
		static final float ARM_SWING = 0.9F, ARM_FLARE = 0.12F, ROLL = 0.11F, SHOULDER_TURN = 0.22F, LEAN = 0.12F;
		/** (0.19.28) The swat's arm (radians): drawn across the chest and coiled, then flung out wide to its right; how it turns its shoulders and trembles. */
		static final float SWAT_RAISE = -1.45F, SWAT_ACROSS = -1.15F, SWAT_OUT = 1.35F, SWAT_COIL = -0.5F, SWAT_UNCOIL = 0.45F, SWAT_TREMBLE = 0.05F;
		/** The grab's arm (radians, x): reared back, reaching down, held at the chest, slammed down. */
		static final float REAR = -2.9F, REACH = -1.1F, HOLD_UP = -1.45F, SLAMMED = -0.35F;
		private final ModelPart tower;

		Towerback() {
			super(bake(mesh()), "towerback");
			this.insigniaZ = -2.8F;
			this.tower = this.body.getChild("tower");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.6F), 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -9.0F, -5.5F, 10.0F, 2.0F, 3.0F),
				PartPose.ZERO);
			root.getChild("right_arm").addOrReplaceChild("boulder", CubeListBuilder.create().texOffs(28, 64).addBox(-4.5F, -3.5F, -2.5F, 5.0F, 4.0F, 5.0F,
				new CubeDeformation(0.5F)), PartPose.ZERO);
			root.getChild("left_arm").addOrReplaceChild("boulder", CubeListBuilder.create().texOffs(28, 64).mirror().addBox(-0.5F, -3.5F, -2.5F, 5.0F, 4.0F,
				5.0F, new CubeDeformation(0.5F)), PartPose.ZERO);
			PartDefinition tower = root.getChild("body").addOrReplaceChild("tower", CubeListBuilder.create().texOffs(0, 74).addBox(-3.0F, -5.0F, 0.0F, 6.0F,
				11.0F, 5.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 2.4F, 0.08F, 0.0F, 0.06F));
			float[][] merlons = {{-3.0F, 0.0F}, {1.0F, 0.0F}, {-3.0F, 3.0F}, {1.0F, 3.0F}};
			for (int i = 0; i < merlons.length; i++) {
				tower.addOrReplaceChild("merlon" + i, CubeListBuilder.create().texOffs(24, 74).addBox(0.0F, -2.0F, 0.0F, 2.0F, 2.0F, 2.0F),
					PartPose.offset(merlons[i][0], -5.0F, merlons[i][1]));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			Float stride = state.getData(XenoformClient.TOWER_STRIDE);
			float speed = state.walkAnimationSpeed;
			if (stride != null && !state.isPassenger) {
				// its own walk replaces vanilla's quick swing
				state.walkAnimationSpeed = 0.0F;
				super.setupAnim(state);
				state.walkAnimationSpeed = speed;
				walk(stride, Math.min(1.0F, speed * 1.3F));
			} else {
				super.setupAnim(state);
			}
			float breath = Mth.sin(state.ageInTicks * 0.05F);
			this.tower.xRot = 0.08F + breath * 0.02F;
			Float swat = state.getData(XenoformClient.TOWER_SWAT);
			if (swat != null && swat >= 0.0F) {
				swat(swat);
			}
			Float phase = state.getData(XenoformClient.TOWER_GRAB);
			if (phase == null || phase < 0.0F) {
				return;
			}
			int windup = com.jaymospowers.power.xenoform.forms.Towerback.WINDUP, snatch = com.jaymospowers.power.xenoform.forms.Towerback.SNATCH;
			int hold = com.jaymospowers.power.xenoform.forms.Towerback.HOLD, slam = com.jaymospowers.power.xenoform.forms.Towerback.SLAM;
			int recover = com.jaymospowers.power.xenoform.forms.Towerback.RECOVER;
			float t = phase, arm;
			float weight = 1.0F;
			if (t < windup) {
				arm = Mth.lerp(ease(t / windup), this.rightArm.xRot, REAR);
			} else if ((t -= windup) < snatch) {
				arm = Mth.lerp(ease(t / snatch), REAR, REACH);
			} else if ((t -= snatch) < hold) {
				arm = Mth.lerp(ease(Math.min(1.0F, t / 10.0F)), REACH, HOLD_UP) + Mth.sin(t * 0.8F) * 0.04F;
			} else if ((t -= hold) < slam) {
				arm = Mth.lerp(ease(t / slam), HOLD_UP, SLAMMED);
			} else {
				t -= slam;
				arm = SLAMMED;
				weight = 1.0F - ease(Math.min(1.0F, t / recover));
			}
			this.rightArm.xRot = Mth.lerp(weight, this.rightArm.xRot, arm);
			this.rightArm.zRot = Mth.lerp(weight, this.rightArm.zRot, 0.15F);
			// it leans into the throw
			this.body.xRot = Mth.lerp(weight, this.body.xRot, arm < -2.0F ? -0.15F : 0.2F);
		}

		/**
		 * (0.19.28) One step of its walk: {@code stride} in radians (a foot down at each half turn past a quarter),
		 * {@code amount} how far into walking it is (0 standing).
		 */
		private void walk(float stride, float amount) {
			if (amount <= 0.001F) {
				return;
			}
			float swing = Mth.sin(stride), forward = Mth.cos(stride);
			// legs: wide swings, the foot coming forward lifted clear of the ground
			this.rightLeg.xRot = swing * LEG_SWING * amount;
			this.leftLeg.xRot = -swing * LEG_SWING * amount;
			this.rightLeg.y -= Math.max(0.0F, -forward) * FOOT_LIFT * amount;
			this.leftLeg.y -= Math.max(0.0F, forward) * FOOT_LIFT * amount;
			// it sinks onto each footfall (a swung leg reaches less far down), and a little more as the foot lands
			float sink = LEG_LENGTH * (1.0F - Mth.cos(this.rightLeg.xRot)) + Mth.abs(swing) * Mth.abs(swing) * Mth.abs(swing) * LAND_DROP * amount;
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
				part.y += sink;
			}
			// its weight rolls over the planted foot; its head keeps level; it leans into the walk
			this.body.zRot += forward * ROLL * amount;
			this.head.zRot -= forward * ROLL * 0.6F * amount;
			this.body.xRot += LEAN * amount;
			this.head.xRot -= LEAN * 0.5F * amount;
			// its shoulders turn with the arm swinging forward, and the arms swing big against the legs, flared out
			turn(-swing * SHOULDER_TURN * amount);
			this.rightArm.xRot += -swing * ARM_SWING * amount;
			this.leftArm.xRot += swing * ARM_SWING * amount;
			this.rightArm.zRot += ARM_FLARE * amount;
			this.leftArm.zRot -= ARM_FLARE * amount;
		}

		/** Its shoulders turned by {@code yaw} (radians): the body turns and the arms go round with it, as vanilla's swing does. */
		private void turn(float yaw) {
			this.body.yRot += yaw;
			float total = this.body.yRot;
			this.rightArm.z = Mth.sin(total) * 5.0F;
			this.rightArm.x = -Mth.cos(total) * 5.0F;
			this.leftArm.z = -Mth.sin(total) * 5.0F;
			this.leftArm.x = Mth.cos(total) * 5.0F;
			this.rightArm.yRot += yaw;
			this.leftArm.yRot += yaw;
		}

		/**
		 * (0.19.28) The swat, {@code t} ticks in: the right arm drawn across the chest as the shoulders coil, trembling
		 * as it builds; then whipped out wide as they uncoil (the blow lands halfway); then easing back.
		 */
		private void swat(float t) {
			int windup = com.jaymospowers.power.xenoform.forms.Towerback.SWAT_WINDUP;
			int strike = com.jaymospowers.power.xenoform.forms.Towerback.SWAT_STRIKE;
			int recover = com.jaymospowers.power.xenoform.forms.Towerback.SWAT_RECOVER;
			float across, coil, weight = 1.0F, tremble = 0.0F;
			if (t < windup) {
				float k = ease(t / windup);
				weight = k;
				across = SWAT_ACROSS;
				coil = SWAT_COIL;
				tremble = Mth.sin(t * 2.3F) * SWAT_TREMBLE * k;
			} else if ((t -= windup) < strike) {
				// fast out of the coil: the swing itself is quick, after the long draw
				float k = t / strike;
				k = k * k;
				across = Mth.lerp(k, SWAT_ACROSS, SWAT_OUT);
				coil = Mth.lerp(k, SWAT_COIL, SWAT_UNCOIL);
			} else {
				t -= strike;
				across = SWAT_OUT;
				coil = SWAT_UNCOIL;
				weight = 1.0F - ease(t / recover);
			}
			float bodyTurn = Mth.lerp(weight, this.body.yRot, coil) - this.body.yRot;
			turn(bodyTurn);
			this.rightArm.xRot = Mth.lerp(weight, this.rightArm.xRot, SWAT_RAISE + tremble);
			this.rightArm.yRot = Mth.lerp(weight, this.rightArm.yRot, across + this.body.yRot);
			this.rightArm.zRot = Mth.lerp(weight, this.rightArm.zRot, 0.0F);
			this.body.xRot = Mth.lerp(weight, this.body.xRot, 0.15F);
		}

		private static float ease(float x) {
			x = Mth.clamp(x, 0.0F, 1.0F);
			return x * x * (3.0F - 2.0F * x);
		}
	}

	/**
	 * Bulwark: thick-set, a heavy brow, pauldrons, and a second pair of arms under the first.
	 * <p>
	 * (0.19.84) Andy: "bulwark should emphasize the four arms more, with a thinner body and outstretched constantly
	 * swapping idle stance arms". A tall, narrow-waisted brute with a wedge of a chest, a short thick neck, a heavy
	 * tusked head and legs bent like a bear's. Four long arms, each an upper arm, a heavy forearm and a fist: the upper
	 * pair are the humanoid arms (the first-person hand and held items), the lower pair hang off the ribs. Standing, the
	 * arms are always held OUT, never hanging, and the guard keeps swapping: the upper pair is raised while the lower
	 * pair is low and out, then they trade places over a few seconds, smoothly, like a fighter shifting guard (the
	 * right side a beat ahead of the left). Walking swings the arms wide in counter-phase (the upper pair against the
	 * lower). A punch ({@code swingAnimation}) throws one arm: the main hand the upper arm on its side, the off hand
	 * the lower arm on the other, so the combo alternates through all four. While it carries a creature overhead
	 * ({@code XenoformClient.ACT} "carry", from {@code live.grabbed >= 0}) the upper pair is raised over the head round
	 * the load, forearms folded in, and the lower pair braced out wide at head height under it, clear of the upper arms;
	 * through the Leap Slam ("leap", from {@code live.leaping}) they are flung up and back. Crouched, the feet are
	 * planted higher by the renderer's sneak offset so they stay on the ground.
	 * The legs are solved from their knee angles, and the whole body sinks so the lowest foot is always on the
	 * ground; the chest carries the insignia.
	 */
	static final class Bulwark extends FormModel {
		// ---- tuning ----
		/** The skeleton (pixels): where the hips (body pivot) and the hip joints sit, the leg's bones and the foot's thickness. */
		static final float HIP_Y = 12.5F, LEG_Y = 11.2F, THIGH = 6.0F, SHIN = 6.0F, FOOT = 2.0F;
		/** The resting knee (radians): the thigh forward, the shin folded back under it. */
		static final float KNEE_T = -0.45F, KNEE_S = 0.9F;
		/** The torso's forward lean (radians): at rest, per walking speed, and crouched. */
		static final float LEAN = 0.14F, LEAN_WALK = 0.08F, LEAN_CROUCH = 0.3F;
		/** The legs' stride (radians), the extra knee bend on the leg coming forward, and the toe curled up as it lifts. */
		static final float LEG_SWING = 0.7F, KNEE_LIFT = 0.8F, FOOT_TOE = 0.3F;
		/** The guard's swap: ticks for the pair to go up and come back, how long each stance dwells (higher is longer), and how many ticks the right side leads the left. */
		static final float SWAP_TICKS = 120.0F, DWELL = 1.8F, SKEW = 6.0F;
		/** The upper pair's guard, {low, high}: arm swing (x, + is back), splay out (z), forearm bend forward (x) and in (z). */
		static final float[] UP_X = {0.1F, -0.3F}, UP_Z = {1.45F, 2.15F}, UP_FX = {-0.5F, -0.9F}, UP_FZ = {0.25F, 0.6F};
		/** The lower pair's guard, {low, high}: always below the upper pair's so the arms never cross. */
		static final float[] LO_X = {0.15F, -0.35F}, LO_Z = {0.5F, 1.15F}, LO_FX = {-0.3F, -0.6F}, LO_FZ = {0.15F, 0.3F};
		/** Walking: how far the arms swing, and how much wider they are held out at a run (radians). */
		static final float ARM_SWING = 0.7F, ARM_WIDE = 0.18F;
		/** A punch: the arm's swing at full reach, its splay, and how far the other arm of the pair is drawn back. */
		static final float PUNCH_X = -1.75F, PUNCH_Z = 0.25F, PUNCH_RECOIL = 0.5F;
		/** Carrying (upper then lower pair): arm swing, splay, forearm bend forward and in (the lower pair braced out at head height, clear of the upper arms); leaping: splay of each pair. */
		static final float[] CARRY_X = {-0.35F, 0.0F}, CARRY_Z = {2.5F, 2.0F}, CARRY_FX = {-0.4F, -0.4F}, CARRY_FZ = {1.0F, 0.6F};
		static final float[] LEAP_Z = {2.7F, 2.2F};
		/** Where the shoulders and the ribs' arms sit on the body (pixels): x out, y up from the hips. */
		static final float SHOULDER_X = 5.5F, SHOULDER_Y = 11.5F, RIB_X = 5.0F, RIB_Y = 6.5F;
		/** The renderer draws a sneaking player this many pixels lower (AvatarRenderer.getRenderOffset): crouched, the feet are planted that much higher to stay on the ground. */
		static final float CROUCH_SINK = 2.0F;

		/** The four arms, forearms and fists: upper right, upper left, lower right, lower left. */
		private final ModelPart[] arms = new ModelPart[4], fores = new ModelPart[4], fists = new ModelPart[4];
		private final ModelPart rightShin, leftShin, rightFoot, leftFoot;
		private final float[] ax = new float[4], az = new float[4], fx = new float[4], fz = new float[4];

		Bulwark() {
			super(bake(mesh()), "bulwark");
			this.arms[0] = this.rightArm;
			this.arms[1] = this.leftArm;
			this.arms[2] = this.body.getChild("right_lower_arm");
			this.arms[3] = this.body.getChild("left_lower_arm");
			for (int i = 0; i < 4; i++) {
				this.fores[i] = this.arms[i].getChild("forearm");
				this.fists[i] = this.fores[i].getChild("fist");
			}
			this.rightShin = this.rightLeg.getChild("shin");
			this.leftShin = this.leftLeg.getChild("shin");
			this.rightFoot = this.rightShin.getChild("foot");
			this.leftFoot = this.leftShin.getChild("foot");
			// flush on the front of the chest (10 wide, 8 tall), centred on the skin's small insignia (rows 6-12 of the face)
			this.insigniaY = -8.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 1.0F;
		}

		/** One arm: a forearm hung from the upper arm's elbow and a fist hung from the wrist. */
		private static void addArm(PartDefinition arm, int faU, int faV, int fiU, int fiV) {
			PartDefinition fore = arm.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(faU, faV).addBox(-2.5F, -0.5F, -2.5F, 5.0F, 6.0F, 5.0F),
				PartPose.offset(0.0F, 6.0F, 0.0F));
			fore.addOrReplaceChild("fist", CubeListBuilder.create().texOffs(fiU, fiV).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 4.0F, 6.0F),
				PartPose.offset(0.0F, 5.5F, 0.0F));
		}

		private static void addLeg(PartDefinition root, String name, float x, int thU, int thV, int shU, int shV, int ftU, int ftV) {
			PartDefinition leg = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(thU, thV).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 7.0F, 3.0F),
				PartPose.offset(x, LEG_Y, 0.0F));
			PartDefinition shin = leg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(shU, shV).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 7.0F, 3.0F),
				PartPose.offset(0.0F, 6.0F, 0.0F));
			shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(ftU, ftV).addBox(-2.0F, -1.0F, -4.0F, 4.0F, 3.0F, 6.0F), PartPose.offset(0.0F, 6.0F, 0.0F));
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// a short thick head, forward of the shoulders, a heavy brow, an underbite and two tusks
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(30, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, 0.8F, -1.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(20, 86).addBox(-4.0F, -5.5F, -4.0F, 8.0F, 2.0F, 3.0F), PartPose.ZERO);
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(42, 86).addBox(-3.0F, -2.0F, -4.0F, 6.0F, 2.0F, 3.0F), PartPose.ZERO);
			head.addOrReplaceChild("tusk_r", CubeListBuilder.create().texOffs(60, 86).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offset(-2.2F, -0.5F, -3.6F));
			head.addOrReplaceChild("tusk_l", CubeListBuilder.create().texOffs(0, 92).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.offset(2.2F, -0.5F, -3.6F));
			// the torso: a wedge of a chest on a narrow waist and a belt, a ridge of spine down the back; pivot at the hips
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -13.0F, -2.5F, 10.0F, 8.0F, 5.0F),
				PartPose.offset(0.0F, HIP_Y, 0.0F));
			body.addOrReplaceChild("waist", CubeListBuilder.create().texOffs(40, 77).addBox(-2.0F, -5.0F, -1.5F, 4.0F, 5.0F, 3.0F), PartPose.ZERO);
			body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 86).addBox(-3.0F, -2.0F, -2.0F, 6.0F, 2.0F, 4.0F), PartPose.ZERO);
			body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(0, 25).addBox(-1.0F, -12.5F, 2.5F, 2.0F, 9.0F, 2.0F), PartPose.ZERO);
			// the upper pair, from the shoulders, with pauldrons
			PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 13).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 8.0F, 4.0F),
				PartPose.offset(-SHOULDER_X, HIP_Y - SHOULDER_Y, 0.0F));
			PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(16, 13).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 8.0F, 4.0F),
				PartPose.offset(SHOULDER_X, HIP_Y - SHOULDER_Y, 0.0F));
			rightArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(12, 67).addBox(-3.5F, -3.5F, -3.0F, 6.0F, 3.0F, 6.0F), PartPose.ZERO);
			leftArm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(36, 67).addBox(-2.5F, -3.5F, -3.0F, 6.0F, 3.0F, 6.0F), PartPose.ZERO);
			addArm(rightArm, 8, 25, 40, 36);
			addArm(leftArm, 28, 25, 0, 47);
			// the lower pair, from the ribs (children of the body, so they lean with it)
			PartDefinition lowerRight = body.addOrReplaceChild("right_lower_arm", CubeListBuilder.create().texOffs(32, 13).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 8.0F, 4.0F),
				PartPose.offset(-RIB_X, -RIB_Y, 0.0F));
			PartDefinition lowerLeft = body.addOrReplaceChild("left_lower_arm", CubeListBuilder.create().texOffs(48, 13).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 8.0F, 4.0F),
				PartPose.offset(RIB_X, -RIB_Y, 0.0F));
			addArm(lowerRight, 0, 36, 24, 47);
			addArm(lowerLeft, 20, 36, 0, 57);
			// legs: a thigh, a shin folded back from the knee, a broad foot
			addLeg(root, "right_leg", -2.5F, 24, 57, 48, 57, 0, 77);
			addLeg(root, "left_leg", 2.5F, 36, 57, 0, 67, 20, 77);
			return mesh;
		}

		/** The forearms and fists straight for the first-person arm (the model is shared, so they hold whoever was drawn last). */
		void handPose() {
			for (int i = 0; i < 2; i++) {
				this.fores[i].resetPose();
				this.fists[i].resetPose();
			}
		}

		private static float smooth(float x) {
			x = Mth.clamp(x, 0.0F, 1.0F);
			return x * x * (3.0F - 2.0F * x);
		}

		/** The guard's swap at {@code t} ticks: 0 with the upper pair low, 1 with it raised; it dwells in each stance. */
		private static float swap(float t) {
			return smooth(0.5F + Mth.clamp(Mth.sin(t * 2.0F * Mth.PI / SWAP_TICKS) * DWELL * 0.5F, -0.5F, 0.5F));
		}

		/**
		 * One leg's bones for the walk's phase ({@code c} the cosine, {@code w} the sine): the thigh swings, the knee folds
		 * on the leg coming forward, the foot is held level (toe curled up as it lifts). Returns how far below the hip joint
		 * its lowest point sits.
		 */
		private float leg(ModelPart thigh, ModelPart shin, ModelPart foot, float c, float w, float sp, float cr, float air) {
			float lift = Math.max(0.0F, w) * sp;
			float t = KNEE_T + c * LEG_SWING * sp - lift * 0.25F - cr * 0.35F - air * 0.3F;
			float s = KNEE_S + lift * KNEE_LIFT + cr * 0.7F + air * 0.4F;
			float f = -lift * FOOT_TOE;
			thigh.xRot = t;
			shin.xRot = s;
			foot.xRot = -(t + s) + f;
			// the foot's lowest corner: the toe (4 forward) or the heel (2 back)
			float foot2 = FOOT * Mth.cos(f);
			return THIGH * Mth.cos(t) + SHIN * Mth.cos(t + s) + Math.max(foot2 + 4.0F * Mth.sin(f), foot2 - 2.0F * Mth.sin(f));
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			String act = state.getData(XenoformClient.ACT);
			boolean carry = "carry".equals(act), leap = "leap".equals(act);
			float age = state.ageInTicks;
			float sp = Math.min(1.0F, state.walkAnimationSpeed);
			float p = state.walkAnimationPos * 0.6662F;
			float cc = Mth.cos(p), ww = Mth.sin(p);
			float air = m.air();
			float cr = state.isCrouching ? 1.0F : 0.0F;
			this.head.resetPose();
			this.body.resetPose();
			this.rightLeg.resetPose();
			this.leftLeg.resetPose();
			this.rightShin.resetPose();
			this.leftShin.resetPose();
			this.rightFoot.resetPose();
			this.leftFoot.resetPose();
			for (int i = 0; i < 4; i++) {
				this.arms[i].resetPose();
				this.fores[i].resetPose();
				this.fists[i].resetPose();
			}
			// the legs: walking solves the knees, then the whole body sinks until the lowest foot is on the ground
			float depthR = leg(this.rightLeg, this.rightShin, this.rightFoot, cc, ww, sp, cr, air);
			float depthL = leg(this.leftLeg, this.leftShin, this.leftFoot, -cc, -ww, sp, cr, air);
			float dy = (24.0F - CROUCH_SINK * cr - LEG_Y) - Math.max(depthR, depthL);
			// the torso leans into the walk and over when crouched; everything on it is placed through the lean
			float lean = LEAN + LEAN_WALK * sp + LEAN_CROUCH * cr + m.land() * 0.15F - Math.max(0.0F, m.fall()) * 0.08F * air;
			float ls = Mth.sin(lean), lc = Mth.cos(lean);
			this.body.y = HIP_Y + dy;
			this.body.xRot = lean;
			this.body.zRot = cc * 0.04F * sp;
			this.rightLeg.y = this.leftLeg.y = LEG_Y + dy;
			this.rightLeg.z = this.leftLeg.z = -1.3F * ls;
			// the head, on a short neck jutting forward of the shoulders, looking where the player looks
			this.head.y = this.body.y - 11.7F * lc + 1.0F * ls + Mth.sin(age * 0.05F) * 0.25F;
			this.head.z = -11.7F * ls - 1.0F * lc;
			this.head.yRot = Mth.clamp(state.yRot, -70.0F, 70.0F) * Mth.DEG_TO_RAD;
			this.head.xRot = Mth.clamp(state.xRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD - lean * 0.7F;
			// the upper arms hang off the shoulders, which lean with the body
			float shy = this.body.y - SHOULDER_Y * lc;
			float shz = -SHOULDER_Y * ls;
			this.rightArm.setPos(-SHOULDER_X, shy, shz);
			this.leftArm.setPos(SHOULDER_X, shy, shz);

			// ---- the arms' targets: the swapping guard, the walk's swing, the air ----
			float uRight = swap(age + SKEW), uLeft = swap(age);
			float sw = cc * sp;
			for (int i = 0; i < 4; i++) {
				boolean upper = i < 2, rightSide = i % 2 == 0;
				float k = upper ? (rightSide ? uRight : uLeft) : 1.0F - (rightSide ? uRight : uLeft);
				float[] x = upper ? UP_X : LO_X, z = upper ? UP_Z : LO_Z, fxs = upper ? UP_FX : LO_FX, fzs = upper ? UP_FZ : LO_FZ;
				this.ax[i] = Mth.lerp(k, x[0], x[1]);
				this.az[i] = Mth.lerp(k, z[0], z[1]) + Mth.sin(age * 0.07F + i * 1.3F) * 0.05F;
				this.fx[i] = Mth.lerp(k, fxs[0], fxs[1]);
				this.fz[i] = Mth.lerp(k, fzs[0], fzs[1]);
				// walking: the upper pair swings against its own side's leg, the lower pair against the upper, all held wider
				float swing = (rightSide ? -sw : sw) * ARM_SWING * (upper ? 1.0F : -0.8F);
				this.ax[i] += swing;
				this.az[i] += ARM_WIDE * sp;
				// in the air they flare; a fall lifts them, a landing slams them down
				this.az[i] += air * 0.25F + Math.max(0.0F, m.fall()) * air * 0.4F - m.land() * 0.6F;
				if (carry) {
					int pair = upper ? 0 : 1;
					this.ax[i] = CARRY_X[pair] + swing * 0.3F;
					this.az[i] = CARRY_Z[pair] + Mth.sin(age * 0.6F + i) * 0.03F;
					this.fx[i] = CARRY_FX[pair];
					this.fz[i] = CARRY_FZ[pair];
				} else if (leap) {
					this.ax[i] = 0.25F;
					this.az[i] = LEAP_Z[upper ? 0 : 1];
					this.fx[i] = -0.2F;
					this.fz[i] = 0.2F;
				}
			}
			// a punch: (0.20.60) each swing is thrown by the next of the four arms in turn (XenoformClient.BULWARK_ARM);
			// without that, the main hand throws the upper arm on its side, the off hand the lower arm on the other side
			float sa = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			if (state.currentSwing != null && sa > 0.0F && !carry) {
				Integer turn = state.getData(XenoformClient.BULWARK_ARM);
				boolean off = state.currentSwing.hand() == net.minecraft.world.InteractionHand.OFF_HAND;
				boolean right = (state.mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT) != off;
				int punch = turn != null ? turn : (off ? 2 : 0) + (right ? 0 : 1);
				int other = punch ^ 1;
				float pk = Mth.sin(sa * Mth.PI);
				this.ax[punch] = Mth.lerp(pk, this.ax[punch], PUNCH_X);
				this.az[punch] = Mth.lerp(pk, this.az[punch], PUNCH_Z);
				this.fx[punch] = Mth.lerp(pk, this.fx[punch], 0.0F);
				this.fz[punch] = Mth.lerp(pk, this.fz[punch], 0.0F);
				// the other arm of the pair is drawn back as the fist goes out
				this.ax[other] += PUNCH_RECOIL * pk;
				this.az[other] += 0.2F * pk;
				this.fx[other] -= 0.3F * pk;
			}
			for (int i = 0; i < 4; i++) {
				float sg = i % 2 == 0 ? 1.0F : -1.0F;
				this.arms[i].xRot = this.ax[i] + (i < 2 ? lean : 0.0F);
				this.arms[i].zRot = sg * this.az[i];
				this.fores[i].xRot = this.fx[i];
				this.fores[i].zRot = sg * this.fz[i];
				this.fists[i].xRot = -0.25F;
			}
		}
	}

	/**
	 * Pyre: a body of living flame, hair of fire streaming up and back off the head, flames licking off the shoulders
	 * and forearms, all swaying; its skin is drawn a second time, fullbright, from {@link #GLOW} so it shines in the dark.
	 */
	static final class Pyre extends FormModel {
		static final Identifier GLOW = JaymosPowers.id("textures/entity/xenoform_pyre_glow.png");
		private final ModelPart hair, hairTip, rightPlume, leftPlume, rightLick, leftLick;

		Pyre() {
			super(bake(mesh()), "pyre");
			this.insigniaY = 4.5F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.8F;
			this.hair = this.head.getChild("hair");
			this.hairTip = this.hair.getChild("tip");
			this.rightPlume = this.body.getChild("right_plume");
			this.leftPlume = this.body.getChild("left_plume");
			this.rightLick = this.rightArm.getChild("lick");
			this.leftLick = this.leftArm.getChild("lick");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.getChild("head");
			// a ring of flame round the top of the head, and the hair rising out of it, a thinner tongue at its tip
			head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.4F)),
				PartPose.ZERO);
			PartDefinition hair = head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(32, 64).addBox(-2.0F, -8.0F, -2.0F, 4.0F, 8.0F, 4.0F),
				PartPose.offset(0.0F, -7.0F, 1.0F));
			hair.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(48, 64).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(0.0F, -7.0F, 0.0F));
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("right_plume", CubeListBuilder.create().texOffs(0, 78).addBox(-2.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(-6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("left_plume", CubeListBuilder.create().texOffs(0, 78).mirror().addBox(-1.0F, -5.0F, -1.5F, 3.0F, 6.0F, 3.0F),
				PartPose.offset(6.0F, 0.0F, 0.0F));
			body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(12, 78).addBox(-2.5F, 2.0F, -3.0F, 5.0F, 5.0F, 1.0F), PartPose.ZERO);
			// a lick of flame off the outside of each forearm
			root.getChild("right_arm").addOrReplaceChild("lick", CubeListBuilder.create().texOffs(24, 78).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(-3.0F, 6.0F, 0.5F));
			root.getChild("left_arm").addOrReplaceChild("lick", CubeListBuilder.create().texOffs(24, 78).mirror().addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(3.0F, 6.0F, 0.5F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks;
			XenoMotion.Read m = XenoMotion.read(state);
			// the flames flicker faster and stream back the faster it goes, lean out of a turn, and leap up as it falls
			float flicker = t * (1.0F + m.speed() * 0.8F);
			float stream = m.speed() * 0.45F + Math.max(0.0F, m.fall()) * 0.4F;
			this.hair.xRot = Mth.cos(flicker * 0.27F) * 0.08F - 0.35F - stream;
			this.hair.zRot = Mth.sin(flicker * 0.35F) * 0.12F - m.trail() * 0.25F;
			this.hairTip.xRot = Mth.cos(flicker * 0.41F + 0.7F) * 0.15F - 0.3F - stream * 0.8F;
			this.hairTip.zRot = Mth.sin(flicker * 0.53F + 1.3F) * 0.2F - m.trail() * 0.3F;
			this.rightPlume.zRot = 0.3F + Mth.sin(flicker * 0.4F) * 0.15F - m.trail() * 0.2F;
			this.leftPlume.zRot = -0.3F - Mth.sin(flicker * 0.4F + 1.0F) * 0.15F - m.trail() * 0.2F;
			this.rightPlume.xRot = -stream;
			this.leftPlume.xRot = -stream;
			// the forearm flames point up whatever the arm does (cancelling its swing) and stream back
			this.rightLick.xRot = -this.rightArm.xRot - stream + Mth.sin(flicker * 0.47F) * 0.15F;
			this.leftLick.xRot = -this.leftArm.xRot - stream + Mth.sin(flicker * 0.47F + 2.0F) * 0.15F;
			this.rightLick.zRot = 0.25F + Mth.cos(flicker * 0.38F) * 0.12F;
			this.leftLick.zRot = -0.25F - Mth.cos(flicker * 0.38F + 0.5F) * 0.12F;
		}

		/** The forearm flames upright for the first-person arm (the model is shared, so they hold whoever was drawn last). */
		void handPose() {
			this.rightLick.xRot = 0.0F;
			this.leftLick.xRot = 0.0F;
			this.rightLick.zRot = 0.25F;
			this.leftLick.zRot = -0.25F;
		}
	}

	/**
	 * (0.19.62 a raptor) Velox: slight, a swept-back crest, a visor that drops over the eyes at speed, a short snout and
	 * a long tail. A bird-legged biped leaning forward, its body near horizontal on long digitigrade legs (thigh, shin,
	 * tarsus, toes: the ankle bends backward, the thigh and tarsus kept parallel so the knee never rises into the body)
	 * that stride long and fast, tucked clawed forearms that slash, a four-part tail held out to balance it, a long
	 * head on an S-neck with a jaw that snaps; the visor still slides down over its eyes as it speeds up. At full run
	 * the body lowers, leans and stretches, the neck reaches and the tail straightens. The man's body is hidden and
	 * lies face-up on the torso so the insignia sits on its back.
	 */
	static final class Velox extends FormModel {
		// ---- tuning ----
		/** The hip's height off the ground (model y) at rest, how far it drops at full speed, when sneaking, and on a landing. */
		static final float HIP_Y = 8.0F, RUN_DROP = 1.0F, CROUCH_DROP = 2.6F, LAND_DROP = 2.0F;
		/** How far apart the hips are from the middle, and how far the feet stand from the hip's line when still (front/back). */
		static final float HIP_X = 2.5F, FOOT_Z = -1.0F, IDLE_STAGGER = 0.8F;
		/** Leg segments (pixels): thigh (hip to knee), shin (knee to ankle), tarsus (ankle to toes). */
		static final float THIGH = 5.5F, SHIN = 7.0F, TARSUS = 5.0F;
		/** The foot's pivot is this far below its top; its toes reach this far forward of it (the lowest point of a pitched foot). */
		static final float TOE_REACH = 4.0F;
		/** The gait: steps per unit of the walk (a full stride cycle), the stride's half-length and the swing's lift at full speed (pixels). */
		static final float GAIT = 0.2F, MAX_STRIDE = 5.2F, MAX_LIFT = 2.4F;
		/** The share of a stride a foot is on the ground: walking, and flat out (under half is a flight). */
		static final float DUTY_WALK = 0.62F, DUTY_RUN = 0.42F;
		/** How far the hip bounces over a stride at full speed (pixels), and how much the body pitches (radians: + is nose down). */
		static final float BOB = 0.9F, REST_PITCH = 0.1F, RUN_PITCH = 0.17F, CROUCH_PITCH = 0.22F;
		/** The neck's two segments' lengths (pixels) and their angles from the vertical (radians, + is forward): resting, and at full run. */
		static final float NECK1 = 4.0F, NECK2 = 4.0F;
		static final float NECK1_REST = 0.85F, NECK2_REST = 0.05F, NECK1_RUN = 1.15F, NECK2_RUN = 0.7F;
		/** Where the neck starts on the body (y, z in the body's space). */
		static final float NECK_Y = -1.0F, NECK_Z = -7.0F;
		/** The arms: where they hang off the chest (body space), their tucked angle and the hand's bend (radians). */
		static final float ARM_X = 2.5F, ARM_Y = 0.5F, ARM_Z = -6.0F, ARM_REST = -0.95F, HAND_REST = 0.7F, CLAW_REST = 0.4F;
		/** The slash: how far the arm winds up and how far it rakes down (radians). */
		static final float SLASH_UP = -1.25F, SLASH_DOWN = 1.0F;
		/** The tail: its segment lengths are in the mesh; how far each sways at rest and at a run (radians), and how fast at rest. */
		static final float TAIL_IDLE_SWAY = 0.15F, TAIL_RUN_SWAY = 0.05F, TAIL_IDLE_RATE = 0.07F;
		/** The lowest the tail droops (radians of tailLift): a hard landing while sneaking would otherwise drive its tip into the ground. */
		static final float TAIL_LOW = -0.35F;

		private final ModelPart torso, neck1, neck2, tail1, tail2, tail3, tail4;
		private final ModelPart rightShin, rightTarsus, rightFoot, leftShin, leftTarsus, leftFoot;
		private final ModelPart rightHand, rightClaw, leftHand, leftClaw;
		private final ModelPart visor, jaw;
		private final float[] solved = new float[6];

		Velox() {
			super(bake(mesh()), "blur");
			ModelPart root = this.root();
			// the plate lies on the torso's back: the (hidden) body is laid face-up there (see setupAnim)
			this.insigniaY = -0.5F;
			this.insigniaZ = -2.5F;
			this.insigniaSize = 0.9F;
			this.torso = root.getChild("torso");
			this.neck1 = this.torso.getChild("neck1");
			this.neck2 = this.neck1.getChild("neck2");
			this.tail1 = this.torso.getChild("tail1");
			this.tail2 = this.tail1.getChild("tail2");
			this.tail3 = this.tail2.getChild("tail3");
			this.tail4 = this.tail3.getChild("tail4");
			this.rightShin = this.rightLeg.getChild("shin");
			this.rightTarsus = this.rightShin.getChild("tarsus");
			this.rightFoot = this.rightTarsus.getChild("foot");
			this.leftShin = this.leftLeg.getChild("shin");
			this.leftTarsus = this.leftShin.getChild("tarsus");
			this.leftFoot = this.leftTarsus.getChild("foot");
			this.rightHand = this.rightArm.getChild("hand");
			this.rightClaw = this.rightHand.getChild("claw");
			this.leftHand = this.leftArm.getChild("hand");
			this.leftClaw = this.leftHand.getChild("claw");
			this.visor = this.head.getChild("visor");
			this.jaw = this.head.getChild("jaw");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head (at the end of the neck, set each frame): a skull, a long snout, a jaw that opens, a crest, the visor
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -5.0F, 4.0F, 4.0F, 6.0F),
				PartPose.offset(0.0F, 4.0F, -10.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 10).addBox(-1.5F, -1.0F, -4.0F, 3.0F, 2.0F, 5.0F),
				PartPose.offset(0.0F, 0.5F, -5.0F));
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 17).addBox(-1.5F, 0.0F, -7.0F, 3.0F, 1.0F, 7.0F),
				PartPose.offset(0.0F, 1.5F, -1.5F));
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(38, 0).addBox(-0.5F, -2.0F, -1.5F, 1.0F, 2.0F, 4.0F),
				PartPose.offset(0.0F, -1.5F, -1.5F));
			head.addOrReplaceChild("crest_sweep", CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -1.0F, 0.0F, 1.0F, 2.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, -2.8F, 1.0F, -0.3F, 0.0F, 0.0F));
			head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(20, 0).addBox(-2.5F, 0.0F, -4.0F, 5.0F, 2.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, -2.9F, -1.5F, -0.4F, 0.0F, 0.0F));
			// the man's body is hidden; it lies face-up on the torso (the insignia sits on its back)
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, HIP_Y, 0.0F));
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 26).addBox(-2.5F, -2.5F, -4.0F, 5.0F, 5.0F, 8.0F),
				PartPose.offsetAndRotation(0.0F, HIP_Y, 0.0F, REST_PITCH, 0.0F, 0.0F));
			torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(26, 26).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 5.0F),
				PartPose.offset(0.0F, 0.5F, -4.0F));
			PartDefinition neck1 = torso.addOrReplaceChild("neck1", CubeListBuilder.create().texOffs(44, 26).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 5.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, NECK_Y, NECK_Z, 0.75F, 0.0F, 0.0F));
			neck1.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(56, 26).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -NECK1, 0.0F, -0.8F, 0.0F, 0.0F));
			// the balancing tail: four segments, thinning out
			PartDefinition tail1 = torso.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 40).addBox(-2.0F, -1.5F, -1.0F, 4.0F, 3.0F, 7.0F),
				PartPose.offset(0.0F, -0.5F, 4.0F));
			PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(22, 40).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 2.0F, 7.0F),
				PartPose.offset(0.0F, 0.0F, 6.0F));
			PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(42, 40).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offset(0.0F, 0.0F, 6.0F));
			tail3.addOrReplaceChild("tail4", CubeListBuilder.create().texOffs(0, 51).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 1.0F, 4.0F),
				PartPose.offset(0.0F, 0.0F, 5.0F));
			// the little arms: an upper arm, a hand bent down off it, a hooked claw
			arm(root, "right_arm", -1, 20, 36, 52);
			arm(root, "left_arm", 1, 28, 44, 56);
			// the legs: thigh, shin, tarsus and toes (a sickle claw held up off each foot)
			leg(root, "right_leg", -1, 0, 28, 48, 0, 36);
			leg(root, "left_leg", 1, 14, 38, 56, 18, 44);
			return mesh;
		}

		private static void arm(PartDefinition root, String name, int side, int uArm, int uHand, int uClaw) {
			// rests at the man's shoulder, hanging: the first-person hand resets it to this (setupAnim tucks it at the chest)
			PartDefinition arm = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uArm, 51).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offset(side * 5.0F, 2.0F, 0.0F));
			PartDefinition hand = arm.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(uHand, 51).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, HAND_REST, 0.0F, 0.0F));
			hand.addOrReplaceChild("claw", CubeListBuilder.create().texOffs(uClaw, 51).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F),
				PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, CLAW_REST, 0.0F, 0.0F));
		}

		private static void leg(PartDefinition root, String name, int side, int uThigh, int uShin, int uTarsus, int uFoot, int uSickle) {
			PartDefinition thigh = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(uThigh, 64).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 7.0F, 4.0F),
				PartPose.offset(side * HIP_X, HIP_Y, 0.0F));
			PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(uShin, 64).addBox(-1.0F, -1.0F, -1.5F, 2.0F, 8.0F, 3.0F),
				PartPose.offset(0.0F, THIGH, 0.0F));
			PartDefinition tarsus = shin.addOrReplaceChild("tarsus", CubeListBuilder.create().texOffs(uTarsus, 64).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offset(0.0F, SHIN, 0.0F));
			PartDefinition foot = tarsus.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(uFoot, 76).addBox(-2.0F, -1.0F, -4.0F, 4.0F, 2.0F, 5.0F),
				PartPose.offset(0.0F, TARSUS, 0.0F));
			foot.addOrReplaceChild("sickle", CubeListBuilder.create().texOffs(uSickle, 76).addBox(-0.5F, -0.5F, -3.0F, 1.0F, 1.0F, 3.0F),
				PartPose.offsetAndRotation(0.0F, -1.2F, -1.0F, -0.5F, 0.0F, 0.0F));
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
				part.resetPose();
			}
			this.hat.visible = false;
			this.body.visible = false;
			float speed = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			// the legs only really stride once it is going; a still raptor stands with its feet flat
			float blend = Mth.clamp(speed * 4.0F, 0.0F, 1.0F);
			float air = m.air();
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float bite = Mth.sin(swing * (float)Math.PI);
			float ticks = state.ageInTicks;
			boolean crouch = state.isCrouching;
			// ---- the gait: right leg on the clock, left half a stride behind ----
			float cycle = state.walkAnimationPos * GAIT;
			float duty = Mth.lerp(speed, DUTY_WALK, DUTY_RUN);
			float stride = MAX_STRIDE * Math.min(1.0F, speed * 1.1F);
			float lift = MAX_LIFT * Math.min(1.0F, speed * 1.2F);
			// the hip dips mid-stance (a spring) and rises in flight, twice a cycle
			float bob = BOB * speed * 0.5F * (1.0F + Mth.cos((cycle - duty * 0.5F) * 4.0F * (float)Math.PI));
			float hipY = HIP_Y + RUN_DROP * speed + (crouch ? CROUCH_DROP : 0.0F) + bob + m.land() * LAND_DROP + m.breath() * 0.12F * (1.0F - speed);
			float pitch = REST_PITCH + RUN_PITCH * speed + (crouch ? CROUCH_PITCH : 0.0F) + bite * 0.1F
				+ Mth.sin((cycle - duty * 0.5F) * 4.0F * (float)Math.PI) * 0.03F * speed + Math.max(0.0F, m.fall()) * air * 0.2F;
			this.legs(this.rightLeg, this.rightShin, this.rightTarsus, this.rightFoot, hipY, cycle, stride, lift, duty, FOOT_Z + IDLE_STAGGER * (1.0F - blend), blend, air, 2.0F);
			this.legs(this.leftLeg, this.leftShin, this.leftTarsus, this.leftFoot, hipY, cycle + 0.5F, stride, lift, duty, FOOT_Z - IDLE_STAGGER * (1.0F - blend), blend, air, -2.0F);
			// ---- the body: near horizontal, pitched over the hips ----
			this.torso.x = 0.0F;
			this.torso.y = hipY;
			this.torso.z = 0.0F;
			this.torso.xRot = pitch;
			this.torso.yRot = this.torso.zRot = 0.0F;
			// the hidden body lies face-up on the torso's back, for the insignia
			this.body.x = 0.0F;
			this.body.y = hipY;
			this.body.z = 0.0F;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			this.body.yRot = this.body.zRot = 0.0F;
			// ---- the neck: an S, reaching forward as it runs, the head held level on the end of it ----
			float reach = Mth.clamp(speed * 1.3F, 0.0F, 1.0F);
			float n1 = Mth.lerp(reach, NECK1_REST, NECK1_RUN) + bite * 0.35F + (crouch ? 0.2F : 0.0F) + m.breath() * 0.03F * (1.0F - speed);
			float n2 = Mth.lerp(reach, NECK2_REST, NECK2_RUN) - bite * 0.15F;
			this.neck1.xRot = n1 - pitch;
			this.neck2.xRot = n2 - n1;
			float cos = Mth.cos(pitch);
			float sin = Mth.sin(pitch);
			this.head.x = 0.0F;
			this.head.y = hipY + NECK_Y * cos - NECK_Z * sin - NECK1 * Mth.cos(n1) - NECK2 * Mth.cos(n2);
			this.head.z = NECK_Y * sin + NECK_Z * cos - NECK1 * Mth.sin(n1) - NECK2 * Mth.sin(n2);
			float look = Mth.sin(ticks * 0.05F) * 0.1F * (1.0F - speed);
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD + look;
			this.head.xRot = state.xRot * Mth.DEG_TO_RAD + 0.1F - 0.1F * speed + bite * 0.25F;
			this.head.zRot = 0.0F;
			this.jaw.xRot = 0.05F + bite * 0.7F + Math.max(0.0F, Mth.sin(ticks * 0.11F + 1.0F) - 0.9F) * 2.0F * (1.0F - speed);
			// the visor slides down over the eyes as it picks up speed and rides up on the brow when it slows
			float down = Mth.clamp((speed - 0.35F) / 0.4F, 0.0F, 1.0F);
			down = down * down * (3.0F - 2.0F * down);
			this.visor.y = Mth.lerp(down, -2.9F, -0.9F);
			this.visor.xRot = Mth.lerp(down, -0.4F, 0.0F);
			// ---- the arms: tucked at the chest, clawing in a slash, the left a beat behind ----
			this.arm(this.rightArm, this.rightHand, this.rightClaw, -1, hipY, pitch, slash(swing), speed, ticks);
			this.arm(this.leftArm, this.leftHand, this.leftClaw, 1, hipY, pitch, slash(Mth.clamp((swing - 0.12F) / 0.88F, 0.0F, 1.0F)), speed, ticks);
			// ---- the tail: out behind, countering the body, swaying when idle, steady at a run ----
			float rise = Math.max(TAIL_LOW, tailLift(m));
			float idle = 1.0F - speed;
			float wave = ticks * TAIL_IDLE_RATE;
			float stepWave = cycle * 2.0F * (float)Math.PI;
			this.tail1.xRot = -pitch + 0.04F * idle + rise;
			this.tail2.xRot = -0.1F * idle + rise * 0.5F;
			this.tail3.xRot = -0.08F * idle + 0.03F * speed + rise * 0.4F;
			this.tail4.xRot = -0.06F * idle + 0.05F * speed + rise * 0.3F;
			ModelPart[] tail = {this.tail1, this.tail2, this.tail3, this.tail4};
			for (int i = 0; i < 4; i++) {
				float idleSway = Mth.sin(wave - i * 0.9F) * TAIL_IDLE_SWAY * (0.6F + 0.3F * i);
				float runSway = -Mth.sin(stepWave - i * 0.7F) * TAIL_RUN_SWAY;
				tail[i].yRot = idleSway * idle + runSway * speed + (i == 0 ? -m.trail() * 0.3F : (m.turn() - m.trail()) * 0.12F);
				tail[i].zRot = 0.0F;
			}
		}

		/** The slash's arm angle added to the tuck, for the attack's progress (0-1): wind up, rake down, come back. */
		private static float slash(float swing) {
			if (swing <= 0.0F) {
				return 0.0F;
			}
			if (swing < 0.25F) {
				return Mth.lerp(swing / 0.25F, 0.0F, SLASH_UP);
			}
			if (swing < 0.55F) {
				float k = (swing - 0.25F) / 0.3F;
				return Mth.lerp(k * k * (3.0F - 2.0F * k), SLASH_UP, SLASH_DOWN);
			}
			float k = (swing - 0.55F) / 0.45F;
			return Mth.lerp(k * k * (3.0F - 2.0F * k), SLASH_DOWN, 0.0F);
		}

		private void arm(ModelPart arm, ModelPart hand, ModelPart claw, int side, float hipY, float pitch, float slash, float speed, float ticks) {
			float cos = Mth.cos(pitch);
			float sin = Mth.sin(pitch);
			arm.x = side * ARM_X;
			arm.y = hipY + ARM_Y * cos - ARM_Z * sin;
			arm.z = ARM_Y * sin + ARM_Z * cos;
			// held tucked, a little twitch at rest, drawn in tighter at a run
			float tuck = ARM_REST - 0.15F * speed + Mth.sin(ticks * 0.09F + side) * 0.04F * (1.0F - speed);
			arm.xRot = pitch + tuck + slash;
			arm.zRot = -side * 0.2F;
			hand.xRot = HAND_REST + slash * 0.5F + 0.2F * speed;
			claw.xRot = CLAW_REST + slash * 0.3F;
		}

		/**
		 * One leg: its foot's path through the stride, then the leg's angles from it ({@link #solveLeg}), kept on the
		 * ground while it's down.
		 */
		private void legs(ModelPart thigh, ModelPart shin, ModelPart tarsus, ModelPart foot, float hipY, float cycle, float stride, float lift,
			float duty, float footZ, float blend, float air, float tuckZ) {
			thigh.x = Math.signum(thigh.x) * HIP_X;
			thigh.y = hipY;
			thigh.z = 0.0F;
			solveLeg(hipY, cycle, stride, lift, duty, footZ, blend, air, tuckZ, this.solved);
			thigh.xRot = this.solved[0];
			shin.xRot = this.solved[1] - this.solved[0];
			tarsus.xRot = this.solved[2] - this.solved[1];
			foot.xRot = this.solved[3] - this.solved[2];
		}

		/** How far below its pivot a foot's lowest point is when pitched {@code pitch} (+ toes down): the sole, or the toe tips. */
		static float toeDrop(float pitch) {
			float sin = (float)Math.sin(pitch);
			return (float)Math.cos(pitch) + (pitch >= 0.0F ? TOE_REACH * sin : -sin);
		}

		/**
		 * Solves one leg into {@code out}: the thigh, shin, tarsus and foot angles from the vertical (radians, + swings the
		 * bottom backward), then the foot pivot's y and z from the hip. A foot is on the ground for {@code duty} of the
		 * cycle, sweeping from {@code footZ - stride} (front) to {@code footZ + stride}, then swings back through the air,
		 * lifted; {@code air} tucks it for a leap ({@code tuckZ} behind or ahead of the hip); {@code blend} (0-1) fades the
		 * stride's foot angles out for a raptor standing still. The thigh and tarsus stay parallel (a bird's leg), so the
		 * hip-to-foot reach is a two-bone solve of (thigh + tarsus) and shin: the knee always forward and below the hip's
		 * line, the ankle behind, however low the hip sinks.
		 */
		static void solveLeg(float hipY, float cycle, float stride, float lift, float duty, float footZ, float blend, float air, float tuckZ, float[] out) {
			float t = cycle - (float)Math.floor(cycle);
			float fz;
			float rise = 0.0F;
			float toes;
			if (t < duty) {
				float u = t / duty;
				fz = footZ + stride * (2.0F * u - 1.0F);
				// down flat on the heel's first touch, flat through the stance, rolling onto the toes to push off
				toes = u < 0.2F ? Mth.lerp(u / 0.2F, -0.15F, 0.0F) : u > 0.7F ? Mth.lerp((u - 0.7F) / 0.3F, 0.0F, 0.55F) : 0.0F;
			} else {
				float u = (t - duty) / (1.0F - duty);
				float s = u * u * (3.0F - 2.0F * u);
				fz = footZ + stride * (1.0F - 2.0F * s);
				rise = lift * (float)Math.sin(Math.PI * u);
				toes = Mth.lerp(s, 0.55F, -0.15F) + 0.2F * (float)Math.sin(Math.PI * u);
			}
			toes *= blend;
			// a leap: the feet come up and tuck, one back and one forward
			rise = Math.max(rise, 2.4F * air);
			fz = Mth.lerp(air, fz, footZ + tuckZ);
			toes = Mth.lerp(air, toes, 0.5F);
			float dy = 24.0F - toeDrop(toes) - rise - hipY;
			float dz = fz;
			float reach = (float)Math.sqrt(dy * dy + dz * dz);
			float paired = THIGH + TARSUS;
			float min = paired - SHIN + 0.3F;
			float max = paired + SHIN - 0.05F;
			float d = Mth.clamp(reach, min, max);
			if (reach > 1.0E-4F && d != reach) {
				dy *= d / reach;
				dz *= d / reach;
			}
			float psi = (float)Math.atan2(dz, dy);
			float cosA = Mth.clamp((paired * paired + d * d - SHIN * SHIN) / (2.0F * paired * d), -1.0F, 1.0F);
			// the thigh (and the tarsus with it) leans forward of the hip-to-foot line: knee ahead, ankle back
			float lean = psi - (float)Math.acos(cosA);
			float cosL = (float)Math.cos(lean);
			float sinL = (float)Math.sin(lean);
			float kneeY = THIGH * cosL;
			float kneeZ = THIGH * sinL;
			float ankleY = dy - TARSUS * cosL;
			float ankleZ = dz - TARSUS * sinL;
			out[0] = lean;
			out[1] = (float)Math.atan2(ankleZ - kneeZ, ankleY - kneeY);
			out[2] = lean;
			out[3] = toes;
			out[4] = hipY + dy;
			out[5] = dz;
		}
	}

	/**
	 * Prism: angular, a crown of crystal and crystals jutting from its back and shoulders (0.19.82 a crystal cluster that
	 * walks on shards, not a man).
	 * <p>
	 * A faceted core slab (the insignia set in its top, two glowing facets on its front) bearing {@link #CRYSTALS} amethyst
	 * prisms tilted outward from it (each a body and a narrower tip), on six thin pointed shard legs that step like a crab's
	 * in a tripod gait (front-right, mid-left and hind-right swing together, then the other three). Each leg is solved to
	 * its foot in a straight line (its angles and its length), so a tip is on the ground whenever it isn't swung, and the
	 * leg never comes off the core. Moving, the core bobs and the crystals jiggle; a turn leans them away; falling flares
	 * them and tucks the legs, a landing squats it; sneaking it lowers and splays (the feet planted against the renderer's
	 * sneak offset). Holding Reflect ({@link XenoformClient#ACT} "root") roots it: the legs splay and dig in (no stepping),
	 * the core sinks and the crystals flare and grow. An attack swing thrusts the crystals forward. The man's parts are
	 * hidden; the body is laid face-up on the core so the insignia plate sits flush on its top.
	 */
	static final class Prism extends FormModel {
		// ---- tuning ----
		/** The core's centre height (model y; the hips are at it) and how far it drops when sneaking, rooted, and per unit of landing. */
		static final float CORE_Y = 13.0F, CROUCH_DROP = 2.5F, ROOT_DROP = 2.0F, LAND_SQUASH = 2.0F;
		/**
		 * A sneaking player is drawn 2/16 of a block lower (AvatarRenderer.getRenderOffset), 2/0.9375 model pixels: crouched,
		 * the ground is that much higher in model space.
		 */
		private static final float CROUCH_SINK = 2.0F / 0.9375F;
		/** The shard legs' length (the three boxes in the skin, 4 + 4 + 4 pixels) and the clamp on how far they stretch or shrink. */
		static final float LEG_LEN = 12.0F, LEG_MIN = 0.6F, LEG_MAX = 1.3F;
		/** The gait: how fast the legs step against the walk, how far a foot strides, how high it lifts, the core's bob, and how fast the walk speed reaches full stride. */
		static final float GAIT = 0.8F, STRIDE = 3.2F, LIFT = 3.0F, BOB = 0.4F, AMP_GAIN = 1.4F;
		/** How far a lifted foot draws in toward the body (fraction of its lift). */
		static final float LIFT_TUCK = 0.25F;
		/** Splay (pixels the feet go farther out): sneaking, rooted, in the air; and how far a rooted foot spreads fore and aft, how deep its tip digs, how far the legs tuck up in the air. */
		static final float CROUCH_SPLAY = 2.0F, ROOT_SPLAY = 3.0F, AIR_SPLAY = 1.5F, ROOT_REACH = 1.5F, ROOT_SINK = 0.8F, AIR_TUCK = 2.5F;
		/** The crystals: idle sway (radians) and its rate, the walk jiggle, how far a turn leans them, a fall's flare, a root's flare and growth. */
		static final float SWAY = 0.025F, SWAY_RATE = 0.07F, JIGGLE = 0.03F, TRAIL_LEAN = 0.15F, FALL_FLARE = 0.2F, ROOT_FLARE = 0.14F, ROOT_GROW = 0.12F;
		/** An attack swing thrusts the top crystals: the tilt forward (radians) and the shift forward (pixels), at full weight. */
		static final float THRUST_TILT = 0.9F, THRUST_SHIFT = 2.5F;
		/** The hips: how far out from the centre, and each row's z (front to hind). */
		static final float HIP_X = 3.5F;
		private static final float[] HIP_Z = {-4.0F, 0.0F, 4.0F};
		/** Each row's rest foot: how far out of its hip, and its z from the hip (the front pair reaches ahead, the hind pair behind). */
		private static final float[] FOOT_OUT = {5.0F, 6.0F, 5.0F};
		private static final float[] FOOT_DZ = {-3.0F, 0.0F, 3.0F};
		/** The crystals' bases sit this far above the core's centre (inside its 6-pixel-tall top). */
		static final float BASE_Y = -2.3F;
		static final int CRYSTALS = 10;
		/** Each crystal: its base x and z on the core, rest tilt about x and z, and its weight in a thrust (1 = the tall ones). */
		private static final float[] CX = {0.0F, -3.2F, 3.2F, -1.0F, -3.2F, 3.2F, -3.4F, 3.4F, -3.0F, 2.2F};
		private static final float[] CZ = {4.2F, 1.8F, 2.6F, 5.4F, -4.6F, -4.8F, -0.8F, 0.2F, 4.6F, 5.2F};
		private static final float[] CRX = {-0.12F, 0.0F, -0.1F, -0.5F, 0.55F, 0.5F, 0.1F, -0.1F, -0.3F, -0.35F};
		private static final float[] CRZ = {0.0F, -0.38F, 0.42F, -0.1F, -0.3F, 0.3F, -0.95F, 0.9F, -0.55F, 0.5F};
		private static final float[] CW = {1.0F, 0.9F, 0.9F, 0.7F, 0.5F, 0.5F, 0.3F, 0.3F, 0.6F, 0.6F};

		private final ModelPart core;
		private final ModelPart[] crystals = new ModelPart[CRYSTALS];
		/** Right side front to hind (0-2), then left (3-5). */
		private final ModelPart[] legs = new ModelPart[6];

		Prism() {
			super(bake(mesh()), "prism");
			ModelPart root = this.root();
			this.core = root.getChild("core");
			for (int i = 0; i < CRYSTALS; i++) {
				this.crystals[i] = this.core.getChild("crystal" + i);
			}
			for (int i = 0; i < 6; i++) {
				this.legs[i] = root.getChild("leg" + i);
			}
			// laid face-up on the core (half its 6 pixels tall above the body's centre), a little forward of the middle
			this.insigniaY = 0.8F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.8F;
		}

		private static MeshDefinition mesh() {
			// the man's parts stay (hidden) for the first-person hand and the insignia plate
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			PartDefinition core = root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, -3.0F, -6.0F, 8.0F, 6.0F, 12.0F),
				PartPose.offset(0.0F, CORE_Y, 0.0F));
			// the crystals: a body box and a narrower tip on top of it. The six small ones share one set of boxes in the skin
			crystal(core, 0, 4, 11, 0, 82, 2, 3, 40, 71);
			crystal(core, 1, 3, 8, 32, 82, 1, 3, 48, 71);
			crystal(core, 2, 3, 7, 32, 93, 1, 3, 48, 71);
			crystal(core, 3, 3, 6, 0, 97, 1, 3, 48, 71);
			for (int i = 4; i < CRYSTALS; i++) {
				crystal(core, i, 2, 4, 40, 76, 1, 2, 52, 71);
			}
			// six shard legs from the core's sides, hanging from the hip: a 3x3, a 2x2 and a 1x1 section, each 4 long
			for (int i = 0; i < 6; i++) {
				boolean left = i >= 3;
				CubeListBuilder leg = CubeListBuilder.create()
					.texOffs(40, 64).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 4.0F, 3.0F)
					.texOffs(52, 64).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F)
					.texOffs(60, 64).addBox(-0.5F, 8.0F, -0.5F, 1.0F, 4.0F, 1.0F);
				root.addOrReplaceChild("leg" + i, leg, PartPose.offset(left ? HIP_X : -HIP_X, CORE_Y, HIP_Z[i % 3]));
			}
			return mesh;
		}

		private static void crystal(PartDefinition core, int i, int w, int bodyH, int bu, int bv, int tipW, int tipH, int tu, int tv) {
			CubeListBuilder c = CubeListBuilder.create()
				.texOffs(bu, bv).addBox(-w / 2.0F, -bodyH, -w / 2.0F, w, bodyH, w)
				.texOffs(tu, tv).addBox(-tipW / 2.0F, -bodyH - tipH, -tipW / 2.0F, tipW, tipH, tipW);
			core.addOrReplaceChild("crystal" + i, c, PartPose.offsetAndRotation(CX[i], BASE_Y, CZ[i], CRX[i], 0.0F, CRZ[i]));
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			boolean rooted = "root".equals(state.getData(XenoformClient.ACT));
			boolean crouch = state.isCrouching;
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			// rooted it doesn't step at all
			float amp = rooted ? 0.0F : Math.min(1.0F, state.walkAnimationSpeed * AMP_GAIN);
			float walk = state.walkAnimationPos * GAIT;
			float swing = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			// the ground line in model space: sneaking, the renderer draws the whole model CROUCH_SINK lower
			float sink = crouch ? CROUCH_SINK : 0.0F;
			float ground = 24.0F - sink;
			float drop = (rooted ? ROOT_DROP : crouch ? CROUCH_DROP : 0.0F) + Math.max(0.0F, m.land()) * LAND_SQUASH;
			float hipY = CORE_Y + drop - sink - Mth.sin(walk * 2.0F) * BOB * amp;
			this.core.resetPose();
			this.core.y = hipY;
			// the hidden body lies face-up on the core, so the insignia plate sits on its top
			this.body.resetPose();
			this.body.y = hipY;
			this.body.xRot = -(float)Math.PI * 0.5F;
			float splay = rooted ? ROOT_SPLAY : crouch ? CROUCH_SPLAY : air * AIR_SPLAY;
			for (int i = 0; i < 6; i++) {
				int row = i % 3;
				int side = i / 3;
				float out = side == 0 ? -1.0F : 1.0F;
				// the tripod: the right front, left mid and right hind swing together, then the other three
				float phase = walk + ((row + side) % 2) * (float)Math.PI;
				float stride = Mth.cos(phase) * STRIDE * amp;
				// swinging forward (sin > 0) the foot lifts; on the ground (sin <= 0) it slides back
				float lift = Math.max(0.0F, Mth.sin(phase)) * LIFT * amp;
				float idle = Mth.sin(age * 0.05F + i * 1.7F) * 0.15F * (1.0F - amp);
				// where the tip goes, from the hip: out, down to the ground (a rooted tip digs in, an airborne one tucks up) and along
				float tx = out * (FOOT_OUT[row] + splay - lift * LIFT_TUCK);
				float ty = ground - hipY - lift + (rooted ? ROOT_SINK : 0.0F) - air * AIR_TUCK;
				float tz = FOOT_DZ[row] + stride + idle + (rooted ? (row - 1) * ROOT_REACH : 0.0F);
				float len = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
				ModelPart leg = this.legs[i];
				leg.resetPose();
				leg.y = hipY;
				// the leg hangs along +y; x then z turn it (z outward, x fore-aft) onto the tip, and it stretches to reach
				leg.xRot = (float)Math.asin(Mth.clamp(tz / len, -1.0F, 1.0F));
				leg.zRot = (float)Math.atan2(-tx, ty);
				leg.yScale = Mth.clamp(len / LEG_LEN, LEG_MIN, LEG_MAX);
			}
			float flareFall = Math.max(0.0F, m.fall()) * air * FALL_FLARE;
			for (int i = 0; i < CRYSTALS; i++) {
				ModelPart c = this.crystals[i];
				c.resetPose();
				float w = CW[i];
				float side = Math.signum(CRZ[i]);
				float sway = Mth.sin(age * SWAY_RATE + i * 1.3F) * SWAY;
				c.zRot = CRZ[i] + sway + Mth.sin(walk * 2.0F + i) * JIGGLE * amp - m.trail() * TRAIL_LEAN * w
					+ side * (flareFall + (rooted ? ROOT_FLARE : 0.0F));
				c.xRot = CRX[i] + Mth.cos(age * SWAY_RATE * 0.8F + i * 2.1F) * SWAY * 0.6F + swing * THRUST_TILT * w + (crouch ? 0.12F : 0.0F);
				c.z = CZ[i] - swing * THRUST_SHIFT * w;
				float grow = rooted ? 1.0F + ROOT_GROW + Mth.sin(age * 0.15F + i) * 0.02F : 1.0F + Mth.sin(age * 0.1F + i) * 0.015F;
				c.yScale = grow;
				c.xScale = c.zScale = 1.0F + (grow - 1.0F) * 0.5F;
			}
		}
	}

	/**
	 * Wisp: a hooded thing with no legs, trailing off into tatters that sway. (0.18.90) Andy: "wisp should float instead
	 * of walk and be made of ki with the current texture overlaid". It never walks: its arms hang and drift instead of
	 * swinging with its steps, trail back as it moves, and {@link XenoformLayer} draws it floating, bobbing, its skin
	 * see-through and fullbright over glowing shells of ki ({@link #shells}, the same body puffed out).
	 */
	static final class Wisp extends FormModel {
		/** How far the ki shells stand out from the body (model pixels), inside out. */
		static final float[] KI_SHELLS = {0.45F, 1.1F};
		/** How far back its arms trail at a run (radians), and their slow drift. */
		static final float ARM_TRAIL = 0.5F, ARM_DRIFT = 0.08F;
		private final ModelPart tatters;
		/** Its ki, the body again a little bigger each time ({@link #KI_SHELLS}); none on a shell itself. */
		Wisp[] shells = new Wisp[0];

		Wisp() {
			this(0.0F);
			this.shells = new Wisp[KI_SHELLS.length];
			for (int i = 0; i < KI_SHELLS.length; i++) {
				this.shells[i] = new Wisp(KI_SHELLS[i]);
			}
		}

		private Wisp(float grow) {
			super(bake(mesh(grow)), "wisp");
			this.tatters = this.body.getChild("tatters");
		}

		private static MeshDefinition mesh(float grow) {
			CubeDeformation puff = new CubeDeformation(grow);
			MeshDefinition mesh = HumanoidModel.createMesh(puff, 0.0F);
			PartDefinition root = mesh.getRoot();
			root.getChild("head").addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -9.0F, -5.0F, 10.0F, 10.0F, 10.0F, puff), PartPose.ZERO);
			root.getChild("body").addOrReplaceChild("tatters", CubeListBuilder.create().texOffs(0, 86).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 7.0F, 4.0F, puff),
				PartPose.offset(0.0F, 12.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			// it doesn't walk: the arms take no swing from its steps (the state is put back for the next model)
			float walk = state.walkAnimationSpeed;
			state.walkAnimationSpeed = 0.0F;
			super.setupAnim(state);
			state.walkAnimationSpeed = walk;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			XenoMotion.Read m = XenoMotion.read(state);
			// arms hang loose, drifting, and trail back as it glides along
			float move = Math.min(1.0F, walk);
			this.rightArm.xRot += move * ARM_TRAIL + Mth.sin(state.ageInTicks * 0.07F) * ARM_DRIFT;
			this.leftArm.xRot += move * ARM_TRAIL + Mth.sin(state.ageInTicks * 0.07F + 1.7F) * ARM_DRIFT;
			this.rightArm.zRot += 0.08F + Mth.cos(state.ageInTicks * 0.05F) * 0.04F;
			this.leftArm.zRot -= 0.08F + Mth.cos(state.ageInTicks * 0.05F + 1.1F) * 0.04F;
			// the tatters drag behind it, billow up as it drops and swing out of a turn
			this.tatters.xRot = Mth.sin(state.ageInTicks * 0.12F) * 0.15F + walk * 0.5F + Math.abs(m.fall()) * 0.6F * m.air()
				+ m.land() * 0.3F;
			this.tatters.zRot = Mth.cos(state.ageInTicks * 0.09F) * 0.1F - m.trail() * 0.35F;
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			if (flight != null && flight.drift() > 0.001F) {
				// (0.20.23) floating upright, the tatters hang below and ripple, trailing a little as it drifts
				float g = XenoformFlightPose.smooth(flight.drift());
				this.tatters.xRot = Mth.lerp(g, this.tatters.xRot, 0.12F + walk * 0.35F + Mth.sin(state.ageInTicks * 0.2F) * 0.12F);
				this.tatters.zRot = Mth.lerp(g, this.tatters.zRot, Mth.sin(state.ageInTicks * 0.15F) * 0.12F);
			}
		}
	}

	/**
	 * Nadir: a tall dark silhouette, broad at the shoulder, a face of nothing; a thin mantle of void drifts behind it.
	 * The insignia is the skin's alone.
	 * <p>
	 * (0.19.84) Shapeless: no limbs, no face. A mass of darkness about a man's height made of twenty-odd dark cubes of
	 * four sizes that slowly drift, tumble, swell and shrink round a centre, the edges fraying into small loose
	 * fragments; the insignia floats on a dim core at its heart, facing front. Moving, the mass leans and streams
	 * behind (the top most), a turn swings it out, a fall lifts it; an attack swells the core and lashes the front
	 * fragments out ahead. Nothing sinks through the ground: a fragment that drifts down settles on it. The man's parts
	 * are hidden (the arm stays for the first-person hand); the body is the core. Uses no {@code XenoformClient.ACT}.
	 */
	static final class Nadir extends FormModel {
		// ---- tuning ----
		/** The core's height (model pixels, y down: 10 puts its centre ~14 up, the heart of a 32-pixel column), its bob and how far it sinks when sneaking. */
		static final float HEART = 10.0F, BOB = 0.8F, CROUCH_DROP = 2.5F;
		/** The ground (model pixels, y down): no fragment's lowest corner goes below it. */
		static final float GROUND = 24.0F, CROUCH_SINK = 2.0F / 0.9375F;
		/** The core's breath (fraction), its rate, and how much it swells on an attack swing. */
		static final float BREATH = 0.04F, BREATH_RATE = 0.09F, BURST_CORE = 0.25F;
		/** How fast the fragments drift (radians a tick), and how far each size drifts (pixels): large, medium, small, tiny. */
		static final float DRIFT = 0.045F;
		private static final float[] AMP = {0.8F, 1.4F, 1.9F, 2.4F};
		/** Each size's half-edge (pixels): large 6, medium 4, small 3, tiny 2 cubed. */
		private static final float[] HALF = {3.0F, 2.0F, 1.5F, 1.0F};
		/** How much harder the fragments tumble at a full walk and on an attack swing, and how far that extra tumble sways their turn about y (radians). */
		static final float SPIN_WALK = 1.5F, SPIN_BURST = 1.0F, SPIN_TURN = 0.6F;
		/** Moving: how far the top streams behind (pixels), how far a turn swings it, how far a fall lifts it, how much it squashes on landing. */
		static final float TRAIL = 4.0F, TURN_SWING = 4.0F, FALL_RISE = 5.0F, LAND_SQUASH = 0.2F, CROUCH_SQUASH = 0.8F, LEAN = 0.15F;
		/** An attack: how far the front fragments lash out ahead (pixels), and how much every fragment swells. */
		static final float LASH = 11.0F, BURST_SWELL = 0.2F;
		/** Each fragment: x, y, z of its centre (pixels, y down, ground 24), its size (0 = 6, 1 = 4, 2 = 3, 3 = 2 cubed), how much of a lash it takes (0-1). */
		private static final float[][] FRAGS = {
			// the heavy cubes stacked round the core (each overlaps or touches its neighbour; the ones beside the
			// core's front sit back from it, so they never drift across the insignia)
			{-3.0F, 3.0F, 0.5F, 0, 1.0F}, {3.0F, 3.0F, 1.5F, 0, 0.6F}, {-2.0F, 16.0F, 1.0F, 0, 0.4F},
			{3.0F, 18.0F, 0.5F, 0, 1.0F}, {0.0F, 0.0F, 1.0F, 0, 0.5F}, {0.0F, 21.0F, 0.0F, 0, 0.3F},
			// medium cubes on the flanks
			{-6.0F, 9.0F, 0.0F, 1, 0.5F}, {6.0F, 11.0F, 1.0F, 1, 0.5F}, {-5.0F, -3.0F, -1.0F, 1, 1.0F},
			{5.0F, 1.0F, 2.0F, 1, 0.3F}, {-5.0F, 19.0F, 1.0F, 1, 0.6F}, {5.0F, 20.0F, -2.0F, 1, 1.0F},
			// small ones fraying off the edge
			{-8.0F, 3.0F, 3.0F, 2, 0.3F}, {8.0F, 6.0F, -3.0F, 2, 1.0F}, {-7.0F, 19.0F, -2.0F, 2, 1.0F},
			{7.0F, 22.0F, 2.0F, 2, 0.3F}, {2.0F, -7.0F, -2.0F, 2, 1.0F}, {-3.0F, -6.0F, 3.0F, 2, 0.3F},
			// tiny drifting motes
			{-9.0F, 12.0F, -3.0F, 3, 1.0F}, {9.0F, 14.0F, 3.0F, 3, 0.3F}, {5.0F, -5.0F, 3.0F, 3, 0.3F},
			{-4.0F, 23.0F, 0.0F, 3, 0.5F}};

		private final ModelPart[] frags = new ModelPart[FRAGS.length];

		Nadir() {
			super(bake(mesh()), "nadir");
			for (int i = 0; i < FRAGS.length; i++) {
				this.frags[i] = this.root().getChild("frag" + i);
			}
			// flush on the front of the core (its front face is at z = -3), in its middle; the core's own scale
			// (its breath and swell) carries the plate with it, so these stay fixed
			this.insigniaY = 0.0F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 1.0F;
		}

		private static MeshDefinition mesh() {
			// the heavy (+0.3) arms it always had, kept for the first-person hand
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.3F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the head stays (hidden) with its empty hat
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
				PartPose.ZERO);
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			// the core: a dim 8x8x6 cube at the heart, carrying the insignia on its front
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 96).addBox(-4.0F, -4.0F, -3.0F, 8.0F, 8.0F, 6.0F),
				PartPose.offset(0.0F, HEART, 0.0F));
			for (int i = 0; i < FRAGS.length; i++) {
				CubeListBuilder c = CubeListBuilder.create();
				switch ((int)FRAGS[i][3]) {
					case 0 -> c.texOffs(0, 64).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F);
					case 1 -> c.texOffs(0, 76).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F);
					case 2 -> c.texOffs(0, 84).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F);
					default -> c.texOffs(0, 90).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F);
				}
				root.addOrReplaceChild("frag" + i, c, PartPose.offset(FRAGS[i][0], FRAGS[i][1], FRAGS[i][2]));
			}
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = this.head.visible = this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.head.resetPose();
			this.body.resetPose();
			XenoMotion.Read m = XenoMotion.read(state);
			float age = state.ageInTicks;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float burst = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			boolean crouch = state.isCrouching;
			float squash = (crouch ? CROUCH_SQUASH : 1.0F) - m.land() * LAND_SQUASH;
			// (0.19.84) a sneaking player is drawn CROUCH_SINK lower: the ground (and the core) that much higher in the model
			float sink = crouch ? CROUCH_SINK : 0.0F;
			float ground = GROUND - sink;
			float cy = HEART + Mth.sin(age * 0.05F) * BOB + (crouch ? CROUCH_DROP : 0.0F) - sink;
			float rise = Math.max(0.0F, m.fall()) * m.air() * FALL_RISE;
			// the core: breathing, swelling on a swing, leaning into its way; the insignia rides its front (the plate
			// is drawn in the core's scaled space, so insigniaZ/insigniaSize stay as set in the constructor)
			float pulse = 1.0F + Mth.sin(age * BREATH_RATE) * BREATH + burst * BURST_CORE;
			this.body.y = cy;
			this.body.xRot = speed * LEAN;
			this.body.zRot = Mth.sin(age * 0.03F) * 0.03F;
			this.body.xScale = this.body.yScale = this.body.zScale = pulse;
			// the fragments: each drifts on its own slow orbit, tumbles and swells; the high ones stream behind
			float spin = 1.0F + speed * SPIN_WALK + burst * SPIN_BURST;
			for (int i = 0; i < FRAGS.length; i++) {
				float[] f = FRAGS[i];
				ModelPart p = this.frags[i];
				int size = (int)f[3];
				float amp = AMP[size];
				float t = age * DRIFT + i * 2.3F;
				float high = Mth.clamp((22.0F - f[1]) / 28.0F, 0.0F, 1.0F);
				p.x = f[0] + Mth.sin(t * 0.9F + i) * amp - m.trail() * TURN_SWING * high;
				p.y = cy + (f[1] - HEART) * squash + Mth.sin(t * 0.7F + i * 1.3F) * amp * 0.8F - rise * high - burst * f[4] * 2.0F;
				p.z = f[2] + Mth.cos(t * 0.8F + i * 0.7F) * amp + speed * TRAIL * high - burst * f[4] * LASH * (f[2] < 1.5F ? 1.0F : 0.3F);
				// the tumble: the angles' wobble grows with the spin, and the turn about y gets a bounded extra sway
				// (never a rate times the age, which would jump as the spin changes)
				p.xRot = Mth.sin(t * 0.5F + i) * 0.5F * spin;
				p.yRot = t * 0.4F + (spin - 1.0F) * SPIN_TURN * Mth.sin(t * 1.3F + i);
				p.zRot = Mth.sin(t * 0.4F + 1.0F) * 0.5F * spin;
				float s = 0.85F + 0.15F * Mth.sin(t * 1.2F + i * 0.9F) + burst * BURST_SWELL * f[4];
				p.xScale = p.yScale = p.zScale = s;
				// settle on the ground: the turned cube's lowest corner (its half-edge times the y row of Rz*Ry*Rx) stays above it
				float cx = Mth.cos(p.xRot), sx = Mth.sin(p.xRot), cyr = Mth.cos(p.yRot), syr = Mth.sin(p.yRot);
				float cz = Mth.cos(p.zRot), sz = Mth.sin(p.zRot);
				float reach = HALF[size] * s * (Mth.abs(sz * cyr) + Mth.abs(cz * cx + sz * syr * sx) + Mth.abs(sz * syr * cx - cz * sx));
				p.y = Math.min(p.y, ground - reach);
			}
		}
	}

	/**
	 * Duplex: a copper golem's build, box for box: a wide flat head with a nose, a short body, long arms hanging near
	 * the ground, stubby legs and the lightning rod with its ball tip, plus a copper belt. It's built at the golem's own
	 * size (16 pixels to the top of its head) and drawn {@link #RENDER_SCALE} times up from a player at Duplex's scale,
	 * so the whole is a copper golem's height.
	 */
	static final class Duplex extends FormModel {
		/** 16 px tall, at the player's 0.9375 and Duplex's scale, comes to a copper golem's 0.98 blocks. */
		static final float RENDER_SCALE = 0.98F / (0.9375F * com.jaymospowers.power.xenoform.forms.Duplex.SCALE);
		private final ModelPart rod;

		Duplex() {
			super(bake(mesh()), "duplex");
			this.insigniaY = 2.5F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = 0.6F;
			this.renderScale = RENDER_SCALE;
			this.rod = this.head.getChild("rod");
		}

		private static MeshDefinition mesh() {
			// the golem's boxes (CopperGolemModel), stood on the humanoid's feet line (y 24): legs from 19, body and
			// head from 13; the arms hang from the top of the body
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -5.0F, -5.0F, 8.0F, 5.0F, 10.0F)
				.texOffs(40, 0).addBox(-1.0F, -2.0F, -6.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 13.0F, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			PartDefinition rod = head.addOrReplaceChild("rod", CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F),
				PartPose.offset(0.0F, -5.0F, 0.0F));
			rod.addOrReplaceChild("knob", CubeListBuilder.create().texOffs(40, 8).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, -4.0F, 0.0F));
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 6.0F, 6.0F),
				PartPose.offset(0.0F, 13.0F, 0.0F));
			body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(0, 64).addBox(-4.0F, 4.0F, -3.0F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.3F)),
				PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 16).addBox(-3.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F),
				PartPose.offset(-4.0F, 14.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(42, 16).addBox(0.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F),
				PartPose.offset(4.0F, 14.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(-2.0F, 19.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(16, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
				PartPose.offset(2.0F, 19.0F, 0.0F));
			return mesh;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			// the rod nods with each step, lags a turn and whips back on landing
			float step = Mth.sin(state.walkAnimationPos * 0.6662F * 2.0F) * 0.12F * state.walkAnimationSpeed;
			this.rod.xRot = step - m.land() * 0.4F + Math.max(0.0F, m.fall()) * 0.3F * m.air();
			this.rod.zRot = -m.trail() * 0.35F + Mth.sin(state.ageInTicks * 0.05F) * 0.03F;
		}
	}

	/**
	 * (0.18.xxx, Gremlin: a big-headed imp with long ears that twitch and a thin tail that flicks; 0.19.66 a hunched
	 * knuckle-walker) A small near-horizontal body carried between long arms planted on the knuckles and short bowed
	 * hind legs, the huge skull held up and forward on a short neck with its ears flopping. It lopes: arms and legs
	 * step in diagonal pairs (the legs a beat behind), each limb solved to a foot on the ground (two-bone IK) so
	 * nothing floats or stretches, the body bobs and the BIG SKULL BOBS and nods with every footfall a beat behind the
	 * body; a swing is a bite (the head darts, the jaw snaps) with a swipe of the right arm; a leap throws the arms
	 * forward and tucks the legs. In the imp the man's parts are hidden or replaced: the head is the skull, the arms
	 * and legs are the two-jointed limbs, and the hidden body lies face-up on the back so the insignia sits there.
	 */
	static final class Gremlin extends FormModel {
		// ---- tuning ----
		/** The torso's centre (model y, z), its resting pitch (negative: shoulders up, hips low), and how far it drops when sneaking. */
		static final float TORSO_Y = 11.0F, TORSO_Z = 1.5F, TORSO_PITCH = -0.30F, CROUCH_DROP = 2.5F;
		/** Where the shoulders and hips sit on the torso (local x, y, z; the right side is negative x). */
		static final float SHOULDER_X = 4.6F, SHOULDER_Y = -0.5F, SHOULDER_Z = -3.0F;
		static final float HIP_X = 3.2F, HIP_Y = 1.0F, HIP_Z = 3.0F;
		/** The neck: where the skull is hung on the torso's front (local y, z) and how far below it the pivot sits. */
		static final float NECK_Y = -1.5F, NECK_Z = -4.0F, NECK_DROP = 1.0F;
		/** Bone lengths: arm upper and lower, leg upper and lower; how far a fist and a foot reach below their joint. */
		static final float ARM_UPPER = 7.0F, ARM_LOWER = 7.0F, LEG_UPPER = 5.0F, LEG_LOWER = 6.0F;
		static final float FIST_DROP = 2.5F, FOOT_DROP = 1.5F;
		/** Where the fists and feet plant (model z) at rest, how far a stride sweeps (the same for both, so no foot slips), and how high a swinging limb lifts. */
		static final float HAND_Z = -5.0F, FOOT_Z = 3.5F, HAND_STRIDE = 5.0F, FOOT_STRIDE = 5.0F, HAND_LIFT = 3.0F, FOOT_LIFT = 2.5F;
		/** The share of a cycle a limb is planted, and how fast the cycle runs against the walk. */
		static final float DUTY = 0.6F, GAIT = 0.6662F * 1.25F;
		/** Where each limb is in the cycle: the arms alternate, the legs a beat behind the opposite arm. */
		static final float RIGHT_ARM_AT = 0.0F, LEFT_LEG_AT = 0.12F, LEFT_ARM_AT = 0.5F, RIGHT_LEG_AT = 0.62F;
		/** The body's bob (pixels) and rock (radians) a step. */
		static final float BOB = 0.9F, ROCK = 0.06F;
		/** The big skull's bob (pixels) and nod (radians) each footfall, and the phase it lags the body by. */
		static final float SKULL_BOB = 1.3F, SKULL_NOD = 0.2F, SKULL_LAG = 0.9F;
		/** The skull's resting pitch (negative: held up) and how far a bite darts it forward (pixels; keep under 2.5 so the skull stays on the torso). */
		static final float HEAD_PITCH = -0.1F, BITE_DART = 2.0F;
		/** The jaw: how wide a bite opens it, and the idle chatter. */
		static final float JAW_BITE = 0.85F, JAW_IDLE = 0.04F;
		/** A swipe: how high and how far forward the right fist goes. */
		static final float SWIPE_LIFT = 7.0F, SWIPE_REACH = 4.0F;
		/** A leap: how high the fists and feet are drawn up, and how far forward the fists reach. */
		static final float AIR_HAND_LIFT = 8.0F, AIR_HAND_REACH = 3.0F, AIR_FOOT_LIFT = 5.0F;
		/** The ears' resting droop (zRot; right ear, negative is outward), their sweep back at a run, and their flop. */
		static final float EAR_REST = -0.2F, EAR_SWEEP = 0.45F, EAR_FLOP = 0.25F;
		/** The tail's resting rise, and the insignia's size on the back. */
		static final float TAIL_REST = 0.5F, PLATE_SIZE = 0.85F;

		private final ModelPart torso, tail, jaw, rightEar, leftEar;
		private final ModelPart rightLower, leftLower, rightFist, leftFist, rightShin, leftShin, rightFoot, leftFoot;
		/** The foot a limb is aimed at (set by {@link #aim}). */
		private float aimY, aimZ;

		Gremlin() {
			super(bake(mesh()), "gremlin");
			this.insigniaY = 0.0F;
			this.insigniaZ = -3.0F;
			this.insigniaSize = PLATE_SIZE;
			ModelPart root = this.root();
			this.torso = root.getChild("torso");
			this.tail = root.getChild("tail");
			this.jaw = this.head.getChild("jaw");
			this.rightEar = this.head.getChild("right_ear");
			this.leftEar = this.head.getChild("left_ear");
			this.rightLower = this.rightArm.getChild("forearm");
			this.leftLower = this.leftArm.getChild("forearm");
			this.rightFist = this.rightLower.getChild("fist");
			this.leftFist = this.leftLower.getChild("fist");
			this.rightShin = this.rightLeg.getChild("shin");
			this.leftShin = this.leftLeg.getChild("shin");
			this.rightFoot = this.rightShin.getChild("foot");
			this.leftFoot = this.leftShin.getChild("foot");
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the skull is the head: big, held up and forward of the neck pivot, with a hinged jaw and two great ears
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -9.0F, -8.0F, 10.0F, 10.0F, 10.0F),
				PartPose.offset(0.0F, 8.0F, -4.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 0.0F, -6.5F, 7.0F, 3.0F, 6.0F),
				PartPose.offset(0.0F, 1.0F, -1.5F));
			head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(40, 64).addBox(-5.0F, -6.0F, -0.5F, 5.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(-4.5F, -5.5F, -3.0F, 0.0F, 0.0F, EAR_REST));
			head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 64).mirror().addBox(0.0F, -6.0F, -0.5F, 5.0F, 6.0F, 1.0F),
				PartPose.offsetAndRotation(4.5F, -5.5F, -3.0F, 0.0F, 0.0F, -EAR_REST));
			// the man's body is hidden (no cubes): it only carries the insignia, laid on the back of the real torso
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, TORSO_Y, TORSO_Z));
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(16, 16).addBox(-3.5F, -3.0F, -4.5F, 7.0F, 6.0F, 9.0F),
				PartPose.offsetAndRotation(0.0F, TORSO_Y, TORSO_Z, TORSO_PITCH, 0.0F, 0.0F));
			for (int i = 0; i < 4; i++) {
				boolean left = (i & 1) == 1;
				float z = i < 2 ? -2.5F : 2.5F;
				CubeListBuilder spine = CubeListBuilder.create().texOffs(56, 64);
				if (left) {
					spine.mirror();
				}
				torso.addOrReplaceChild("spine" + i, spine.addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
					PartPose.offsetAndRotation(left ? 2.9F : -2.9F, -2.6F, z, 0.4F, 0.0F, left ? 0.2F : -0.2F));
			}
			root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(40, 72).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 11.0F, 6.0F, TAIL_REST, 0.0F, 0.0F));
			// long arms (upper, lower, knuckled fist) and short bowed legs (thigh, shin, foot)
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				String s = left ? "left" : "right";
				float sx = left ? SHOULDER_X : -SHOULDER_X;
				float hx = left ? HIP_X : -HIP_X;
				PartDefinition arm = root.addOrReplaceChild(s + "_arm", limb(48, 16, left).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 8.0F, 3.0F),
					PartPose.offset(sx, 9.6F, -1.2F));
				PartDefinition fore = arm.addOrReplaceChild("forearm", limb(48, 32, left).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F),
					PartPose.offset(0.0F, ARM_UPPER, 0.0F));
				fore.addOrReplaceChild("fist", limb(48, 44, left).addBox(-1.5F, -0.5F, -2.5F, 3.0F, 3.0F, 4.0F),
					PartPose.offset(0.0F, ARM_LOWER, 0.0F));
				PartDefinition leg = root.addOrReplaceChild(s + "_leg", limb(0, 16, left).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 6.0F, 3.0F),
					PartPose.offset(hx, 12.8F, 4.0F));
				PartDefinition shin = leg.addOrReplaceChild("shin", limb(0, 32, left).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F),
					PartPose.offset(0.0F, LEG_UPPER, 0.0F));
				shin.addOrReplaceChild("foot", limb(0, 44, left).addBox(-1.5F, -0.5F, -3.5F, 3.0F, 2.0F, 5.0F),
					PartPose.offset(0.0F, LEG_LOWER, 0.0F));
			}
			return mesh;
		}

		private static CubeListBuilder limb(int u, int v, boolean left) {
			CubeListBuilder b = CubeListBuilder.create().texOffs(u, v);
			return left ? b.mirror() : b;
		}

		/** Where in its cycle (0-1) a limb is, given the walk clock and its offset. */
		private static float cycle(float walk, float at) {
			float u = walk / (2.0F * (float)Math.PI) + at;
			return u - (float)Math.floor(u);
		}

		/**
		 * Aims a limb's foot for a point in the cycle: planted and sweeping back for {@link #DUTY}, then lifted and
		 * swinging forward. Idle (speed 0) it stands on its rest spot.
		 */
		private void aim(float u, float speed, float groundY, float restZ, float stride, float lift) {
			float s = stride * speed;
			if (u < DUTY) {
				this.aimY = groundY;
				this.aimZ = restZ - s * 0.5F + s * (u / DUTY);
			} else {
				float q = (u - DUTY) / (1.0F - DUTY);
				this.aimY = groundY - lift * speed * Mth.sin(q * (float)Math.PI);
				this.aimZ = restZ + s * 0.5F - s * q;
			}
		}

		/**
		 * Two-bone IK in the side plane: swings {@code upper} (hung at {@code hy, hz}) and {@code lower} so the end of
		 * {@code end} reaches (ty, tz), or as near as the bones allow. {@code bend} is which way the joint folds (+1
		 * back, like an elbow; -1 forward, like a knee); {@code tilt} is the end part's pitch from level.
		 */
		private static void solve(ModelPart upper, ModelPart lower, ModelPart end, float hy, float hz, float ty, float tz, float l1, float l2,
			float bend, float tilt) {
			float dy = ty - hy;
			float dz = tz - hz;
			float d = Math.max(1.0E-3F, (float)Math.sqrt(dy * dy + dz * dz));
			float reach = Mth.clamp(d, Math.abs(l1 - l2) + 0.05F, l1 + l2 - 0.05F);
			dy *= reach / d;
			dz *= reach / d;
			float phi = (float)Math.atan2(dz, dy);
			float cos = Mth.clamp((l1 * l1 + reach * reach - l2 * l2) / (2.0F * l1 * reach), -1.0F, 1.0F);
			float a1 = phi + bend * (float)Math.acos(cos);
			float a2 = (float)Math.atan2(dz - l1 * Mth.sin(a1), dy - l1 * Mth.cos(a1));
			upper.y = hy;
			upper.z = hz;
			upper.xRot = a1;
			lower.xRot = a2 - a1;
			end.xRot = tilt - a2;
		}

		/** The model-space y of a point (local y, z) on the torso at this centre height and pitch. */
		private static float worldY(float cy, float pitch, float ly, float lz) {
			return cy + ly * Mth.cos(pitch) - lz * Mth.sin(pitch);
		}

		private static float worldZ(float pitch, float ly, float lz) {
			return TORSO_Z + ly * Mth.sin(pitch) + lz * Mth.cos(pitch);
		}

		/** The forearm and fist straight for the first-person arm (the model is shared, so they hold whoever was drawn last). */
		void handPose() {
			this.rightLower.xRot = 0.0F;
			this.leftLower.xRot = 0.0F;
			this.rightFist.xRot = 0.0F;
			this.leftFist.xRot = 0.0F;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			XenoMotion.Read m = XenoMotion.read(state);
			for (ModelPart part : new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg}) {
				part.resetPose();
			}
			this.hat.visible = false;
			this.body.visible = false;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			float walk = state.walkAnimationPos * GAIT;
			float age = state.ageInTicks;
			float air = m.air();
			float swing = Mth.clamp(state.swingAnimation, 0.0F, 1.0F);
			float bite = Mth.sin(swing * (float)Math.PI);
			float swipe = bite;
			boolean low = m.crouching();
			// the body: bobs with the lope, rocks nose-down on a bite, stretches in a leap and squashes on landing
			float cy = TORSO_Y + (low ? CROUCH_DROP : 0.0F) - BOB * Mth.abs(Mth.sin(walk)) * speed
				+ Mth.sin(age * 0.09F) * 0.15F + Math.max(0.0F, m.land()) * 1.5F - air * 0.5F;
			float pitch = TORSO_PITCH + ROCK * Mth.sin(walk * 2.0F + 0.4F) * speed + bite * 0.12F - air * 0.2F + m.land() * 0.1F;
			this.torso.y = cy;
			this.torso.xRot = pitch;
			// the hidden body lies face-up on the torso's back so the insignia plate sits flush there
			this.body.y = cy;
			this.body.z = TORSO_Z;
			this.body.xRot = pitch - (float)Math.PI * 0.5F;
			// limbs: arms alternate, the legs a beat behind the opposite arm; planted fists and feet reach the ground
			float shoulderY = worldY(cy, pitch, SHOULDER_Y, SHOULDER_Z);
			float shoulderZ = worldZ(pitch, SHOULDER_Y, SHOULDER_Z);
			float hipY = worldY(cy, pitch, HIP_Y, HIP_Z);
			float hipZ = worldZ(pitch, HIP_Y, HIP_Z);
			float handY = 24.0F - FIST_DROP;
			float footY = 24.0F - FOOT_DROP;
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				ModelPart arm = left ? this.leftArm : this.rightArm;
				ModelPart lower = left ? this.leftLower : this.rightLower;
				ModelPart fist = left ? this.leftFist : this.rightFist;
				ModelPart leg = left ? this.leftLeg : this.rightLeg;
				ModelPart shin = left ? this.leftShin : this.rightShin;
				ModelPart foot = left ? this.leftFoot : this.rightFoot;
				this.aim(cycle(walk, left ? LEFT_ARM_AT : RIGHT_ARM_AT), speed, handY, HAND_Z, HAND_STRIDE, HAND_LIFT);
				float ty = this.aimY;
				float tz = this.aimZ;
				if (!left) {
					ty -= swipe * SWIPE_LIFT;
					tz -= swipe * SWIPE_REACH;
				}
				ty = Mth.lerp(air, ty, handY - AIR_HAND_LIFT);
				tz = Mth.lerp(air, tz, HAND_Z - AIR_HAND_REACH);
				arm.x = left ? SHOULDER_X : -SHOULDER_X;
				solve(arm, lower, fist, shoulderY, shoulderZ, ty, tz, ARM_UPPER, ARM_LOWER, 1.0F, (handY - ty) * 0.05F);
				this.aim(cycle(walk, left ? LEFT_LEG_AT : RIGHT_LEG_AT), speed, footY, FOOT_Z, FOOT_STRIDE, FOOT_LIFT);
				ty = Mth.lerp(air, this.aimY, footY - AIR_FOOT_LIFT);
				tz = Mth.lerp(air, this.aimZ, FOOT_Z - 1.0F);
				leg.x = left ? HIP_X : -HIP_X;
				solve(leg, shin, foot, hipY, hipZ, ty, tz, LEG_UPPER, LEG_LOWER, -1.0F, (footY - ty) * 0.08F);
			}
			// the big skull: hung on the front of the torso, bobbing and nodding each footfall a beat behind the body
			float lag = walk * 2.0F - SKULL_LAG;
			float bob = Mth.sin(lag) * SKULL_BOB * speed + Mth.sin(age * 0.07F) * 0.25F;
			this.head.y = worldY(cy, pitch, NECK_Y, NECK_Z) + NECK_DROP + bob - bite * 0.5F;
			this.head.z = worldZ(pitch, NECK_Y, NECK_Z) - bite * BITE_DART;
			this.head.xRot = HEAD_PITCH + state.xRot * Mth.DEG_TO_RAD + Mth.sin(lag + 0.6F) * SKULL_NOD * speed
				- (pitch - TORSO_PITCH) * 0.8F + bite * 0.25F + m.land() * 0.25F - air * 0.2F;
			this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
			this.head.zRot = Mth.sin(walk) * 0.05F * speed;
			this.jaw.xRot = JAW_IDLE + Mth.sin(age * 0.23F) * JAW_IDLE * (0.5F + speed) + Mth.sin(Math.min(1.0F, swing * 1.3F) * (float)Math.PI) * JAW_BITE
				+ Math.max(0.0F, m.fall()) * 0.2F * air;
			// the ears bounce with each step, flop down on landing (and lift as it falls), blow back at a run and lag a turn
			float twitch = Mth.sin(age * 0.3F) * 0.1F + speed * 0.1F;
			float flop = m.land() * 0.7F - Math.max(0.0F, m.fall()) * 0.4F * air;
			float step = Mth.sin(lag - 0.6F) * EAR_FLOP * speed;
			float sweep = EAR_SWEEP * speed + Math.max(0.0F, m.fall()) * 0.3F * air;
			this.rightEar.zRot = EAR_REST + twitch - flop - step - m.trail() * 0.25F;
			this.leftEar.zRot = -EAR_REST - twitch + flop + step - m.trail() * 0.25F;
			this.rightEar.yRot = sweep;
			this.leftEar.yRot = -sweep;
			// the tail hangs off the back of the torso and flicks
			this.tail.y = worldY(cy, pitch, -0.5F, 4.5F);
			this.tail.z = worldZ(pitch, -0.5F, 4.5F);
			this.tail.yRot = Mth.sin(age * 0.2F) * 0.5F + Mth.cos(walk) * 0.6F * speed - m.trail() * 0.8F;
			this.tail.xRot = TAIL_REST + tailLift(m);
		}
	}

	/**
	 * Knell: hulking, horned, with a glowing ribcage, heavy shoulders and sculk tendrils at the jaw; (0.19.84 a hunched
	 * beast on two legs, not a man: a massive humped back leaning far forward over short, thick, bowed legs, a thick neck
	 * slinging the horned head low and forward, and very long heavy arms that hang to the ground, the knuckles nearly
	 * dragging). It lumbers, rocking side to side with each step, the arms swinging against the legs (a knuckle-walker's
	 * diagonal gait, every foot and fist solved to plant flat on the ground). The man's own parts are redefined, so the
	 * first-person hand, held items and the poses laid over a man still find them: the body is the trunk (a hump, two
	 * hinged rib plates over a glowing chest, and the insignia in the gap between them, as children), the head the horned
	 * skull (curling horns, a brow, a hinged jaw with sculk tendrils), the arms and legs jointed. A swing (a Sonic Boom
	 * or a Shriek is a swing; the attack) throws the head out on its neck, opens the jaw and the ribs and rears the
	 * back, the right fist slamming forward. {@link XenoformClient#ACT}: "sense" while Tremor Sense is held (low on
	 * all fours, head down, listening), "toll" during the Tolling (reared up, beating the ground and its chest to the
	 * toll, the ribs open), "burrow" while it digs in (squatting, scooping with both fists).
	 */
	static final class Knell extends FormModel {
		// ---- tuning ----
		/** The pelvis, the body's pivot (model y, z), the back's forward lean about it at rest (radians), its breath and how far a swing rears it back. */
		static final float PELVIS_Y = 14.5F, PELVIS_Z = 3.0F, LEAN = 0.62F, LEAN_BREATH = 0.025F, BOOM_REAR = 0.16F;
		/** How far a sneaking renderer sinks the model (pixels), and how far a landing and a crouch dip the pelvis. */
		static final float CROUCH_SINK = 2.0F / 0.9375F, LAND_DIP = 2.0F, CROUCH_SQUAT = 1.5F, CROUCH_LEAN = 0.12F;
		/** The gait: radians of step cycle per unit of walkAnimationPos, how much walk speed it takes to be at full stride, the fraction of the cycle a foot is down. */
		static final float GAIT = 0.6662F * 0.9F, SPEED_GAIN = 1.25F, DUTY = 0.62F;
		/** The lumber: how far the body shifts (pixels) and rolls (radians) over the planted foot, how far it bobs (pixels), how far a turn twists it. */
		static final float ROCK_SHIFT = 0.9F, ROCK = 0.07F, BOB = 0.7F, TURN_TWIST = 0.12F;
		/** The legs: a hip's offset from the middle and its rest place (model y, z), the thigh's and the shin's length, the ankle's height over the sole. */
		static final float HIP_X = 3.8F, HIP_Y = 16.0F, HIP_Z = 3.6F, THIGH = 5.0F, SHIN = 5.0F, ANKLE = 1.0F;
		/** The legs' steps: half a stride and the lift of a swinging foot (pixels), how far ahead of its hip a foot rests, and the bow and the toe-out (radians). */
		static final float STRIDE = 3.4F, LIFT = 1.8F, FOOT_AHEAD = -0.6F, BOW = 0.2F, TOE_OUT = 0.12F;
		/** The shoulders (in the body's space: out from the middle, y, z), the arms' two bones, the height of the wrist over the fist's sole and the fist's half length and half width (a tipped fist is lifted by them so its edge stays on the ground). */
		static final float SHOULDER_X = 7.5F, SHOULDER_Y = -10.5F, SHOULDER_Z = -0.5F, UPPER = 8.0F, FORE = 8.0F, FIST = 3.0F, FIST_HALF = 3.5F, FIST_SIDE = 3.0F;
		/** The fists' steps: half a stride and the lift of a swinging fist (pixels), how far ahead of its shoulder a fist rests, and how high the wrist rests over the ground. */
		static final float ARM_STRIDE = 3.8F, ARM_LIFT = 1.6F, ARM_AHEAD = 0.5F, WRIST_Y = 3.4F;
		/** The neck's base on the body (y, z), its length and its resting pitch (radians, absolute, - is forward), and how far a swing throws it out (pixels). */
		static final float NECK_Y = -11.0F, NECK_Z = -1.0F, NECK = 7.0F, NECK_PITCH = -0.95F, THRUST = 3.2F;
		/** The head: how far it hangs down at rest, how far a swing throws it up (radians), and how far the jaw hangs shut and opens on a swing. */
		static final float HEAD_DROP = 0.22F, BOOM_LIFT = 0.4F, JAW_REST = 0.08F, JAW_BOOM = 0.9F;
		/** The rib plates: their middle's height on the trunk (below the hump's overhang, which ends at y = -8) and how far they swing open on a swing and in the toll (radians). */
		static final float RIB_Y = -4.0F, RIB_OPEN = 1.2F;
		/** Tremor Sense: how much lower the back leans, how far the pelvis squats and how far ahead the fists are put. */
		static final float SENSE_LEAN = 0.3F, SENSE_SQUAT = 2.0F, SENSE_REACH = 3.0F;
		/** The Tolling: ticks between tolls (Knell.TOLL_EVERY), the back's lean at rest and reared, how far the pelvis squats, how high the fists rise over the shoulder. */
		static final float TOLL_TICKS = 40.0F, TOLL_LEAN = 0.35F, TOLL_REAR = 0.25F, TOLL_SQUAT = 2.0F, TOLL_RISE = 6.0F;
		/** Burrowing: how much lower the back leans, how far the pelvis squats, how fast the fists scoop and how far they reach, how far the head bows. */
		static final float BURROW_LEAN = 0.4F, BURROW_SQUAT = 4.0F, SCOOP_RATE = 0.7F, SCOOP_REACH = 4.0F, BURROW_BOW = 0.1F;
		/**
		 * The change-back tap: the fist comes in flat across the chest, its face on the insignia: how far in front of the
		 * plate its middle stops (its half depth, 3.5, and a hair), how far further out and up it is poised, and how far
		 * down the fist its middle is (from the wrist).
		 */
		static final float TAP_FIST = 4.0F, TAP_POISE = 3.0F, TAP_POISE_UP = 0.5F, TAP_FIST_MID = 1.0F;
		/** Which way the tapping elbow points (model space before the body's yaw: out to the right side, down and forward). */
		static final float TAP_POLE_X = -1.0F, TAP_POLE_Y = 1.0F, TAP_POLE_Z = -1.0F;
		private static final float PI = (float)Math.PI;

		private final ModelPart neck, jaw, ribRight, ribLeft;
		private final ModelPart[] horns = new ModelPart[4], growths = new ModelPart[2], tendrils = new ModelPart[3];
		/** Index 0 right, 1 left. */
		private final ModelPart[] fore = new ModelPart[2], fist = new ModelPart[2], shin = new ModelPart[2], foot = new ModelPart[2];
		private final ModelPart[] posed;
		private final float[] at = new float[3], gait = new float[3], bones = new float[2], tapAt = new float[3], tapRot = new float[4], rot = new float[3];

		Knell() {
			super(bake(mesh()), "knell");
			this.ownsTap = true;
			ModelPart root = this.root();
			this.neck = root.getChild("neck");
			this.jaw = this.head.getChild("jaw");
			this.ribRight = this.body.getChild("right_rib");
			this.ribLeft = this.body.getChild("left_rib");
			this.horns[0] = this.head.getChild("right_horn");
			this.horns[1] = this.horns[0].getChild("tip");
			this.horns[2] = this.head.getChild("left_horn");
			this.horns[3] = this.horns[2].getChild("tip");
			this.growths[0] = this.body.getChild("right_growth");
			this.growths[1] = this.body.getChild("left_growth");
			for (int i = 0; i < 3; i++) {
				this.tendrils[i] = this.jaw.getChild("tendril_" + i);
			}
			this.fore[0] = this.rightArm.getChild("forearm");
			this.fore[1] = this.leftArm.getChild("forearm");
			this.fist[0] = this.fore[0].getChild("fist");
			this.fist[1] = this.fore[1].getChild("fist");
			this.shin[0] = this.rightLeg.getChild("shin");
			this.shin[1] = this.leftLeg.getChild("shin");
			this.foot[0] = this.shin[0].getChild("foot");
			this.foot[1] = this.shin[1].getChild("foot");
			this.posed = new ModelPart[] {this.head, this.body, this.rightArm, this.leftArm, this.rightLeg, this.leftLeg, this.neck};
			// the plate lies in the gap between the rib plates, flush on the trunk's front (the trunk's front face is at z = -3.5),
			// below the hump's overhang (a whole skin row: the ribs' middle is a row's edge)
			this.insigniaY = RIB_Y + 0.5F;
			this.insigniaZ = -3.5F;
			this.insigniaSize = 0.6F;
		}

		/** A point of the body's space put where the body is: turned about x (the lean), y, then z, as a part is, then moved to the pelvis. */
		private static void place(float x, float y, float z, float xr, float yr, float zr, float px, float py, float pz, float[] out) {
			float y1 = y * Mth.cos(xr) - z * Mth.sin(xr);
			float z1 = y * Mth.sin(xr) + z * Mth.cos(xr);
			float x2 = x * Mth.cos(yr) + z1 * Mth.sin(yr);
			float z2 = -x * Mth.sin(yr) + z1 * Mth.cos(yr);
			out[0] = px + x2 * Mth.cos(zr) - y1 * Mth.sin(zr);
			out[1] = py + x2 * Mth.sin(zr) + y1 * Mth.cos(zr);
			out[2] = pz + z2;
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
			PartDefinition root = mesh.getRoot();
			float[] p = new float[3];
			// the trunk (the man's body): the back, leaning forward about the pelvis; a hump of shoulders over it, rib plates hinged over the glowing chest, spurs of sculk
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 14).addBox(-5.5F, -13.0F, -3.5F, 11.0F, 16.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, PELVIS_Y, PELVIS_Z, LEAN, 0.0F, 0.0F));
			body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(0, 37).addBox(-7.5F, -14.0F, -4.5F, 15.0F, 6.0F, 9.0F), PartPose.ZERO);
			body.addOrReplaceChild("right_rib", CubeListBuilder.create().texOffs(42, 97).addBox(0.0F, -4.0F, -1.0F, 4.0F, 8.0F, 1.0F), PartPose.offset(-5.5F, RIB_Y, -3.5F));
			body.addOrReplaceChild("left_rib", CubeListBuilder.create().texOffs(52, 97).addBox(-4.0F, -4.0F, -1.0F, 4.0F, 8.0F, 1.0F), PartPose.offset(5.5F, RIB_Y, -3.5F));
			body.addOrReplaceChild("right_growth", CubeListBuilder.create().texOffs(48, 44).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F),
				PartPose.offsetAndRotation(-5.5F, -13.5F, 1.0F, 0.0F, 0.0F, -0.35F));
			body.addOrReplaceChild("left_growth", CubeListBuilder.create().texOffs(52, 68).addBox(-1.5F, -5.0F, -1.5F, 3.0F, 5.0F, 3.0F),
				PartPose.offsetAndRotation(5.5F, -13.5F, 1.0F, 0.0F, 0.0F, 0.35F));
			// the neck, thick, from the hump forward and down; the head on its end
			place(0.0F, NECK_Y, NECK_Z, LEAN, 0.0F, 0.0F, 0.0F, PELVIS_Y, PELVIS_Z, p);
			root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(36, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 11.0F, 6.0F),
				PartPose.offsetAndRotation(p[0], p[1], p[2], NECK_PITCH, 0.0F, 0.0F));
			place(0.0F, NECK, 0.0F, NECK_PITCH, 0.0F, 0.0F, p[0], p[1], p[2], p);
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -5.0F, -7.0F, 8.0F, 6.0F, 8.0F),
				PartPose.offsetAndRotation(p[0], p[1], p[2], HEAD_DROP, 0.0F, 0.0F));
			head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 101).addBox(-4.5F, -6.0F, -8.0F, 9.0F, 2.0F, 3.0F), PartPose.ZERO);
			PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 92).addBox(-3.5F, -0.5F, -6.5F, 7.0F, 3.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 0.5F, -0.5F, JAW_REST, 0.0F, 0.0F));
			// sculk tendrils hanging from the chin
			int[][] tendrilUv = {{32, 0}, {60, 0}, {32, 5}};
			for (int i = 0; i < 3; i++) {
				jaw.addOrReplaceChild("tendril_" + i, CubeListBuilder.create().texOffs(tendrilUv[i][0], tendrilUv[i][1]).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
					PartPose.offsetAndRotation(-2.5F + 2.5F * i, 2.5F, -5.5F, 0.15F, 0.0F, 0.2F - 0.2F * i));
			}
			// the horns curl out and up, then in and forward
			PartDefinition rightHorn = head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(0, 106).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offsetAndRotation(-3.6F, -4.5F, -2.5F, 0.0F, 0.0F, -0.7F));
			rightHorn.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(16, 106).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, -0.5F, 0.0F, 1.1F));
			PartDefinition leftHorn = head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(8, 106).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
				PartPose.offsetAndRotation(3.6F, -4.5F, -2.5F, 0.0F, 0.0F, 0.7F));
			leftHorn.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(42, 106).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, -0.5F, 0.0F, -1.1F));
			// the arms (the man's): upper arm, forearm bent forward at rest, fist; the shoulders where the hump's ends are
			place(-SHOULDER_X, SHOULDER_Y, SHOULDER_Z, LEAN, 0.0F, 0.0F, 0.0F, PELVIS_Y, PELVIS_Z, p);
			PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(24, 52).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 11.0F, 5.0F),
				PartPose.offset(p[0], p[1], p[2]));
			PartDefinition rightFore = rightArm.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(36, 17).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 10.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, UPPER, 0.0F, -0.6F, 0.0F, 0.0F));
			rightFore.addOrReplaceChild("fist", CubeListBuilder.create().texOffs(0, 68).addBox(-3.0F, -1.0F, -3.5F, 6.0F, 4.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, FORE, 0.0F, 0.3F, 0.0F, 0.0F));
			place(SHOULDER_X, SHOULDER_Y, SHOULDER_Z, LEAN, 0.0F, 0.0F, 0.0F, PELVIS_Y, PELVIS_Z, p);
			PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(44, 52).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 11.0F, 5.0F),
				PartPose.offset(p[0], p[1], p[2]));
			PartDefinition leftFore = leftArm.addOrReplaceChild("forearm", CubeListBuilder.create().texOffs(0, 52).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 10.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, UPPER, 0.0F, -0.6F, 0.0F, 0.0F));
			leftFore.addOrReplaceChild("fist", CubeListBuilder.create().texOffs(26, 68).addBox(-3.0F, -1.0F, -3.5F, 6.0F, 4.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, FORE, 0.0F, 0.3F, 0.0F, 0.0F));
			// the legs (the man's): thigh, shin, flat foot; knees forward at rest
			PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 79).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 8.0F, 5.0F),
				PartPose.offsetAndRotation(-HIP_X, HIP_Y, HIP_Z, -0.8F, 0.0F, 0.0F));
			PartDefinition rightShin = rightLeg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(48, 33).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 7.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, THIGH, 0.0F, 1.6F, 0.0F, 0.0F));
			rightShin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(40, 79).addBox(-2.5F, -1.0F, -5.0F, 5.0F, 2.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, SHIN, 0.0F, -0.8F, 0.0F, 0.0F));
			PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(20, 79).addBox(-2.5F, -2.0F, -2.5F, 5.0F, 8.0F, 5.0F),
				PartPose.offsetAndRotation(HIP_X, HIP_Y, HIP_Z, -0.8F, 0.0F, 0.0F));
			PartDefinition leftShin = leftLeg.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(26, 97).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 7.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, THIGH, 0.0F, 1.6F, 0.0F, 0.0F));
			leftShin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(40, 88).addBox(-2.5F, -1.0F, -5.0F, 5.0F, 2.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, SHIN, 0.0F, -0.8F, 0.0F, 0.0F));
			return mesh;
		}

		/** A step's phase at {@code u} (0-1 through a cycle) into {@link #gait}: the foot's place (-1 forward to 1 back), its lift (0-1), 1 while it swings. */
		private void step(float u) {
			if (u < DUTY) {
				this.gait[0] = -1.0F + 2.0F * u / DUTY;
				this.gait[1] = 0.0F;
				this.gait[2] = 0.0F;
			} else {
				float t = (u - DUTY) / (1.0F - DUTY);
				float ease = t * t * (3.0F - 2.0F * t);
				this.gait[0] = 1.0F - 2.0F * ease;
				this.gait[1] = Mth.sin(t * PI);
				this.gait[2] = 1.0F;
			}
		}

		/** Two bones from (sy, sz) to (ty, tz), the joint on the side {@code bend} (-1 forward, 1 back), into {@link #bones}: each bone's angle from straight down (x rotation). */
		private void ik(float sy, float sz, float ty, float tz, float l1, float l2, float bend) {
			float dz = tz - sz;
			float dy = ty - sy;
			float dist = Mth.clamp((float)Math.sqrt(dz * dz + dy * dy), Math.abs(l1 - l2) + 0.05F, l1 + l2 - 0.01F);
			float to = (float)Math.atan2(dz, dy);
			float cos = Mth.clamp((l1 * l1 + dist * dist - l2 * l2) / (2.0F * l1 * dist), -1.0F, 1.0F);
			float t1 = to + bend * (float)Math.acos(cos);
			float jz = sz + l1 * Mth.sin(t1);
			float jy = sy + l1 * Mth.cos(t1);
			this.bones[0] = t1;
			this.bones[1] = (float)Math.atan2(sz + dist * Mth.sin(to) - jz, sy + dist * Mth.cos(to) - jy);
		}

		/**
		 * (0.19.84) The right arm from the shoulder (sx, sy, sz) to put the fist's middle on (tx, ty, tz), a 3D two-bone
		 * reach with the elbow toward the pole (px, py, pz) and the wrist straight: into {@link #tapRot} the upper arm's
		 * x, y, z angles (the parts' Z*Y*X order) and the forearm's hinge.
		 */
		private void tapArm(float sx, float sy, float sz, float tx, float ty, float tz, float px, float py, float pz) {
			float dx = tx - sx, dy = ty - sy, dz = tz - sz;
			float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 0.1F) {
				return;
			}
			dx /= len;
			dy /= len;
			dz /= len;
			// the second bone runs on through the straight wrist to the fist's middle
			float lower = FORE + TAP_FIST_MID;
			float reach = Mth.clamp(len, Math.abs(UPPER - lower) + 0.05F, UPPER + lower - 0.01F);
			float along = (UPPER * UPPER + reach * reach - lower * lower) / (2.0F * reach);
			float out = (float)Math.sqrt(Math.max(0.0F, UPPER * UPPER - along * along));
			// the pole, made square to the reach
			float qx = px, qy = py, qz = pz;
			float dot = qx * dx + qy * dy + qz * dz;
			qx -= dot * dx;
			qy -= dot * dy;
			qz -= dot * dz;
			float ql = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
			if (ql < 1.0E-4F) {
				return;
			}
			qx /= ql;
			qy /= ql;
			qz /= ql;
			// the elbow, and the upper arm (local y) and forearm directions
			float ex = dx * along + qx * out, ey = dy * along + qy * out, ez = dz * along + qz * out;
			float yx = ex / UPPER, yy = ey / UPPER, yz = ez / UPPER;
			float fx = (dx * reach - ex) / lower, fy = (dy * reach - ey) / lower, fz = (dz * reach - ez) / lower;
			// the hinge (local x) is square to both, turned so the elbow bends the way the arm's rest pose does (a
			// negative turn about x), so nothing flips over as the tap starts; local z completes the frame
			float xx = yz * fy - yy * fz, xy = yx * fz - yz * fx, xz = yy * fx - yx * fy;
			float xl = (float)Math.sqrt(xx * xx + xy * xy + xz * xz);
			if (xl < 1.0E-4F) {
				return;
			}
			xx /= xl;
			xy /= xl;
			xz /= xl;
			float zx = xy * yz - xz * yy, zy = xz * yx - xx * yz, zz = xx * yy - xy * yx;
			// R = [x y z] as columns = Rz * Ry * Rx
			this.tapRot[0] = (float)Math.atan2(yz, zz);
			this.tapRot[1] = (float)Math.asin(Mth.clamp(-xz, -1.0F, 1.0F));
			this.tapRot[2] = (float)Math.atan2(xy, xx);
			// the forearm's hinge: its direction in the upper arm's frame, (0, cos, sin) of the turn about x
			float fore = (float)Math.atan2(fx * zx + fy * zy + fz * zz, fx * yx + fy * yy + fz * yz);
			this.tapRot[3] = fore;
		}

		/** (0.19.84) {@code (x, y, z)} turned as a part is (Z*Y*X by {@code xr, yr, zr}), into {@link #rot}. */
		private void turnPart(float x, float y, float z, float xr, float yr, float zr) {
			float y1 = y * Mth.cos(xr) - z * Mth.sin(xr), z1 = y * Mth.sin(xr) + z * Mth.cos(xr);
			float x2 = x * Mth.cos(yr) + z1 * Mth.sin(yr), z2 = -x * Mth.sin(yr) + z1 * Mth.cos(yr);
			this.rot[0] = x2 * Mth.cos(zr) - y1 * Mth.sin(zr);
			this.rot[1] = x2 * Mth.sin(zr) + y1 * Mth.cos(zr);
			this.rot[2] = z2;
		}

		/**
		 * The forearms and fists straight, and the shoulders back over the player's, for the first-person arm (the model is
		 * shared, so they hold whoever was drawn last, and the arms' rest place is out on the hump, ahead of the chest).
		 */
		void handPose() {
			this.rightArm.z = 0.0F;
			this.leftArm.z = 0.0F;
			for (int side = 0; side < 2; side++) {
				this.fore[side].xRot = 0.0F;
				this.fist[side].xRot = 0.0F;
			}
		}

		private static float fract(float v) {
			return v - (float)Math.floor(v);
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			this.hat.visible = false;
			for (ModelPart part : this.posed) {
				part.resetPose();
			}
			XenoMotion.Read m = XenoMotion.read(state);
			String act = state.getData(XenoformClient.ACT);
			boolean burrow = "burrow".equals(act);
			boolean toll = "toll".equals(act);
			boolean sense = "sense".equals(act);
			float age = state.ageInTicks;
			boolean crouch = state.isCrouching;
			boolean low = crouch || Boolean.TRUE.equals(state.getData(XenoformClient.SNEAK));
			float ground = 24.0F - (crouch ? CROUCH_SINK : 0.0F);
			float air = Mth.clamp(m.air(), 0.0F, 1.0F);
			// a swing is a Sonic Boom, a Shriek or a blow; burrowing swings too as it starts digging, but that is the scoop's to show
			float swing = burrow ? 0.0F : Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * PI);
			float breath = m.breath();
			float speed = Mth.clamp(state.walkAnimationSpeed * SPEED_GAIN, 0.0F, 1.0F) * (1.0F - air) * (toll ? 0.0F : burrow ? 0.25F : 1.0F);
			float w = state.walkAnimationPos * GAIT;
			// the Tolling's beat: the fists rise over the toll and slam down on it (the server tolls when the level's game
			// time is a multiple of the interval; the client's game time follows the server's, an entity's age does not)
			net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
			float clock = level != null ? (level.getGameTime() % (long)TOLL_TICKS) + fract(age) : age;
			float beat = fract(clock / TOLL_TICKS);
			float rise = beat < 0.7F ? Mth.sin(beat / 0.7F * PI * 0.5F) : 1.0F - (float)Math.pow((beat - 0.7F) / 0.3F, 3.0);
			if (!toll) {
				rise = 0.0F;
			}
			// (0.19.84) the change-back tap: the right fist comes up off the ground onto the insignia (XenoformSlap leaves that arm to us)
			XenoformSlap.Pose slap = state.getData(XenoformSlap.KEY);
			float tap = slap != null && slap.chest() && !XenoformSlap.ownBody(state) ? slap.raise() : 0.0F;
			float tapHit = slap != null ? slap.strike() : 0.0F;

			// ---- the body: the back leans over the pelvis, rocking over the planted foot ----
			// (the crouch's own squat and lean only when no ability already has it low)
			boolean crouchPose = low && !sense && !burrow && !toll;
			float lean = LEAN + breath * LEAN_BREATH - swing * BOOM_REAR + (crouchPose ? CROUCH_LEAN : 0.0F);
			float squat = (crouchPose ? CROUCH_SQUAT : 0.0F) + (burrow ? 0.0F : m.land() * LAND_DIP);
			float wide = 0.0F;
			if (sense) {
				lean += SENSE_LEAN;
				squat += SENSE_SQUAT;
				wide = 0.8F;
			}
			if (burrow) {
				lean += BURROW_LEAN;
				squat += BURROW_SQUAT;
				wide = 0.8F;
			}
			if (toll) {
				lean = TOLL_LEAN - TOLL_REAR * rise;
				squat += TOLL_SQUAT;
				wide = 1.0F;
			}
			float rock = Mth.cos(w - DUTY * PI) * speed;
			float bob = Mth.cos(2.0F * (w - DUTY * PI)) * speed * BOB;
			float px = -rock * ROCK_SHIFT;
			float py = PELVIS_Y - (crouch ? CROUCH_SINK : 0.0F) + squat - bob;
			float pz = PELVIS_Z;
			float yaw = m.turn() * TURN_TWIST;
			float roll = -rock * ROCK;
			this.body.setPos(px, py, pz);
			this.body.xRot = lean;
			this.body.yRot = yaw;
			this.body.zRot = roll;
			// the rib plates: open on a swing (the chest glows) and while it tolls
			float open = Mth.clamp(swing * 1.1F + rise * 0.9F + (toll ? 0.25F : 0.0F), 0.0F, 1.0F) * RIB_OPEN;
			this.ribRight.yRot = open;
			this.ribLeft.yRot = -open;
			float spur = Mth.sin(age * 0.1F) * 0.05F;
			this.growths[0].zRot = -0.35F + spur - m.trail() * 0.1F;
			this.growths[1].zRot = 0.35F - spur - m.trail() * 0.1F;

			// ---- the legs: planted flat on the ground, the knees forward ----
			float hipY = py + (HIP_Y - PELVIS_Y);
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				float sgn = left ? 1.0F : -1.0F;
				ModelPart leg = left ? this.leftLeg : this.rightLeg;
				float hx = sgn * (HIP_X + wide) + px * 0.5F;
				this.step(fract((w + (left ? PI : 0.0F)) / (2.0F * PI)));
				float tz = HIP_Z + FOOT_AHEAD + this.gait[0] * STRIDE * speed - air * 1.0F;
				float ty = ground - ANKLE - this.gait[1] * LIFT * speed - air * 3.5F;
				this.ik(hipY, HIP_Z, ty, tz, THIGH, SHIN, -1.0F);
				leg.setPos(hx, hipY, HIP_Z);
				leg.xRot = this.bones[0];
				leg.yRot = -sgn * TOE_OUT;
				leg.zRot = -sgn * (BOW + wide * 0.15F);
				this.shin[side].xRot = this.bones[1] - this.bones[0];
				this.shin[side].zRot = sgn * (BOW + wide * 0.15F) * 1.5F;
				// the foot lies flat on the ground, the toes dropping as it swings
				float lifted = this.gait[1] * LIFT * speed;
				this.foot[side].xRot = (float)Math.min(0.3F, Math.asin(Math.min(1.0F, lifted / 5.0F))) - this.bones[1];
				// and level across: the bow's tilt of the shin taken back out at the ankle, so the sole lies flat
				this.foot[side].zRot = sgn * (BOW + wide * 0.15F) * 0.5F;
			}

			// ---- the arms: the fists plant on the ground ahead of the shoulders, swinging against the legs ----
			float splay = sense || burrow ? 0.3F : toll ? 0.12F : 0.05F;
			for (int side = 0; side < 2; side++) {
				boolean left = side == 1;
				float sgn = left ? 1.0F : -1.0F;
				ModelPart arm = left ? this.leftArm : this.rightArm;
				place(sgn * SHOULDER_X, SHOULDER_Y, SHOULDER_Z, lean, yaw, roll, px, py, pz, this.at);
				float sx = this.at[0];
				float sy = this.at[1];
				float sz = this.at[2];
				// an arm swings with the opposite leg
				this.step(fract((w + (left ? 0.0F : PI)) / (2.0F * PI)));
				float tz = sz + ARM_AHEAD + this.gait[0] * ARM_STRIDE * speed + (sense ? SENSE_REACH : 0.0F);
				float ty = ground - WRIST_Y - this.gait[1] * ARM_LIFT * speed;
				float fistAbs = this.gait[2] * this.gait[1] * 0.35F;
				if (burrow) {
					// scooping the ground back, one fist and then the other
					float scoop = Mth.sin(age * SCOOP_RATE + (left ? PI : 0.0F));
					tz = sz + 2.0F - scoop * SCOOP_REACH;
					ty = ground - WRIST_Y - Math.max(0.0F, Mth.cos(age * SCOOP_RATE + (left ? PI : 0.0F))) * 3.0F;
					fistAbs = scoop * 0.4F;
				}
				if (toll) {
					// up over the shoulders and down onto the ground
					tz = Mth.lerp(rise, sz + ARM_AHEAD + 1.0F, sz - 3.0F);
					ty = Mth.lerp(rise, ground - WRIST_Y, sy - TOLL_RISE);
					fistAbs = -rise * 0.5F;
				}
				if (!left && swing > 0.0F && !toll) {
					// the swing: the right fist slams forward and up
					tz = Mth.lerp(swing, tz, sz - 11.0F);
					ty = Mth.lerp(swing, ty, sy + 2.0F);
					fistAbs = -swing * 0.4F;
				}
				// a tipped fist (pitched or rolled with the arm) is lifted so its lowest edge stays where its flat sole would be
				float fistRoll = -sgn * splay + roll;
				ty -= FIST * (Mth.cos(fistAbs) - 1.0F) + FIST_HALF * Math.abs(Mth.sin(fistAbs)) + FIST_SIDE * Math.abs(Mth.sin(fistRoll));
				// the arm stands splayed out, which shortens it by the splay's cosine
				float flat = Mth.cos(splay);
				this.ik(sy, sz, sy + (ty - sy) / flat, tz, UPPER, FORE, 1.0F);
				arm.setPos(sx, sy, sz);
				arm.xRot = this.bones[0];
				arm.yRot = yaw;
				arm.zRot = fistRoll;
				this.fore[side].xRot = this.bones[1] - this.bones[0];
				this.fist[side].xRot = fistAbs - this.bones[1];
				float tapped = left ? 0.0F : tap;
				if (tapped > 0.0F) {
					// (0.19.84) the change-back tap: the forearm comes in across the chest with the wrist straight, the fist's
					// face flat on the insignia (poised a little further out), the elbow out to the side clear of the chest and
					// the neck. The fist's middle and the elbow's side are eased over from the walking arm's, not its angles,
					// so the arm stays solved (never sweeping through the trunk) the whole way up.
					place(0.0F, this.insigniaY - TAP_POISE_UP * (1.0F - tapHit), this.insigniaZ - TAP_FIST - TAP_POISE * (1.0F - tapHit), lean, yaw, roll, px, py,
						pz, this.tapAt);
					this.turnPart(0.0F, UPPER, 0.0F, arm.xRot, arm.yRot, arm.zRot);
					float ex = this.rot[0], ey = this.rot[1], ez = this.rot[2];
					// the walking fist's middle: down the forearm, then down the fist at its own bend
					float fa = this.fore[side].xRot, fb = fa + this.fist[side].xRot;
					this.turnPart(0.0F, FORE * Mth.cos(fa) + TAP_FIST_MID * Mth.cos(fb), FORE * Mth.sin(fa) + TAP_FIST_MID * Mth.sin(fb), arm.xRot, arm.yRot,
						arm.zRot);
					float wx = sx + ex + this.rot[0], wy = sy + ey + this.rot[1], wz = sz + ez + this.rot[2];
					float pole = (float)Math.sqrt(TAP_POLE_X * TAP_POLE_X + TAP_POLE_Y * TAP_POLE_Y + TAP_POLE_Z * TAP_POLE_Z);
					float qx = (TAP_POLE_X * Mth.cos(yaw) + TAP_POLE_Z * Mth.sin(yaw)) / pole, qy = TAP_POLE_Y / pole;
					float qz = (-TAP_POLE_X * Mth.sin(yaw) + TAP_POLE_Z * Mth.cos(yaw)) / pole;
					this.tapRot[0] = arm.xRot;
					this.tapRot[1] = arm.yRot;
					this.tapRot[2] = arm.zRot;
					this.tapRot[3] = this.fore[side].xRot;
					this.tapArm(sx, sy, sz, Mth.lerp(tapped, wx, this.tapAt[0]), Mth.lerp(tapped, wy, this.tapAt[1]), Mth.lerp(tapped, wz, this.tapAt[2]),
						Mth.lerp(tapped, ex / UPPER, qx), Mth.lerp(tapped, ey / UPPER, qy), Mth.lerp(tapped, ez / UPPER, qz));
					arm.xRot = this.tapRot[0];
					arm.yRot = this.tapRot[1];
					arm.zRot = this.tapRot[2];
					this.fore[side].xRot = this.tapRot[3];
					// the wrist straightens as it comes up
					this.fist[side].xRot *= 1.0F - tapped;
				}
			}

			// ---- the neck and head: slung low and forward; a swing throws them out ----
			float look = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.9F, 0.9F);
			float neckPitch = NECK_PITCH + (lean - LEAN) * -0.6F + look * 0.2F;
			float neckYaw = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.0F, 1.0F) * 0.35F;
			place(0.0F, NECK_Y, NECK_Z, lean, yaw, roll, px, py, pz, this.at);
			this.neck.setPos(this.at[0], this.at[1], this.at[2]);
			this.neck.xRot = neckPitch;
			this.neck.yRot = yaw + neckYaw;
			this.neck.zRot = roll;
			// a swing throws the head out: the neck stretches with it, so the skull never leaves it
			this.neck.yScale = (NECK + 1.0F + THRUST * swing) / (NECK + 1.0F);
			place(0.0F, NECK + THRUST * swing, 0.0F, this.neck.xRot, this.neck.yRot, this.neck.zRot, this.at[0], this.at[1], this.at[2], this.at);
			this.head.setPos(this.at[0], this.at[1], this.at[2]);
			float listen = sense ? 0.35F + Mth.sin(age * 0.06F) * 0.1F : 0.0F;
			float headPitch = HEAD_DROP + look * (burrow ? 0.4F : 0.85F) - (neckPitch - NECK_PITCH) * 0.5F - swing * BOOM_LIFT + listen - rise * 0.8F + (burrow ? BURROW_BOW : 0.0F)
				+ m.land() * 0.2F + Mth.sin(w * 2.0F) * 0.04F * speed + breath * 0.01F;
			this.head.xRot = headPitch;
			this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -1.2F, 1.2F) + (sense ? Mth.sin(age * 0.045F) * 0.3F : 0.0F);
			this.head.zRot = roll * 1.5F;
			this.jaw.xRot = JAW_REST + Mth.sin(age * 0.09F) * 0.03F + swing * JAW_BOOM + rise * 0.9F + Math.max(0.0F, m.fall()) * 0.2F * air;
			// the tendrils hang straight down whichever way the head turns, sway on their own and swing out of a turn
			for (int i = 0; i < 3; i++) {
				float own = Mth.sin(age * 0.11F + i * 1.7F);
				this.tendrils[i].xRot = 0.1F - (this.head.xRot + this.jaw.xRot) * 0.9F + own * 0.08F + m.speed() * 0.25F + m.land() * 0.4F;
				this.tendrils[i].zRot = 0.2F - 0.2F * i + own * 0.06F - m.trail() * 0.4F;
			}
			// the horns twitch with the head
			float flick = Mth.sin(age * 0.07F) * 0.03F + m.land() * 0.15F;
			this.horns[0].zRot = -0.7F + flick;
			this.horns[2].zRot = 0.7F - flick;
			this.horns[1].zRot = 1.1F + flick;
			this.horns[3].zRot = -1.1F - flick;
		}
	}

	/**
	 * Wyrm: horned, winged and long-tailed (0.19.75 a small dragon on four legs). A long low scaly body on four clawed
	 * legs that trot in a lizard's diagonal gait (each leg two bones and a foot, solved so the planted feet never slide off
	 * the ground), a neck that rises to a horned head with a hinged jaw (a bite throws the head forward and opens the jaw),
	 * a spine ridge, and a tail in three joints to a spade tip that sways behind each turn. The dragon hangs off its own
	 * torso on the root, not off the man's body, so the poses laid over a man's body after it animates (the chest turning
	 * after the head, the crouch's lean and sway, the knee) leave it alone; the man's parts are all hidden, and his body
	 * copies the torso's pose each frame so the insignia sits on the chest's front. The torso carries the wings
	 * ({@link WyrmWings}, which copy it: on its shoulders, folded along its back on the ground) and cancels Wind's cruise
	 * tilt (a dragon already lies level; see {@link #CRUISE_BRAKE_LEAN}). Flying the body is level and the legs tuck up.
	 */
	static final class Wyrm extends FormModel {
		// ---- tuning ----
		/** The torso's height (model y of its middle) at rest; how far it sinks when sneaking and on a landing. */
		static final float BODY_Y = 14.0F, CROUCH_DROP = 2.0F, LAND_DIP = 1.5F;
		/**
		 * Where Wind's cruise tilt pivots (WindPose.rotate: half the box's height over the feet), found in model space: the
		 * player renderer draws the model at this scale (AvatarRenderer.scale) from this far over the feet (blocks).
		 */
		static final float PLAYER_SCALE = 0.9375F, MODEL_ORIGIN = 1.501F;
		/**
		 * The renderer draws a sneaking player 2 pixels of the world lower (AvatarRenderer.getRenderOffset), this many model
		 * pixels: sneaking, the feet are planted that much higher to stay on the ground.
		 */
		static final float CROUCH_SINK = 2.0F / PLAYER_SCALE;
		/** The gait: radians of step cycle per unit of walkAnimationPos, half a stride (pixels), the lift of a swinging foot, the fraction of the cycle a foot is down. */
		static final float GAIT = 1.8F, STRIDE = 3.5F, LIFT = 2.2F, DUTY = 0.6F;
		/** How far the walk bobs the torso (pixels), rolls it (radians) and wriggles it side to side (radians). */
		static final float BOB = 0.6F, ROLL = 0.04F, WRIGGLE = 0.06F;
		/** A foot lifting off curls its toes up this much (radians). */
		static final float TOE_LIFT = 0.35F;
		/** walkAnimationSpeed at which the gait is at full stride. */
		static final float WALK_FULL = 0.3F;
		/** The legs: a hip's offset from the torso's middle (x either side, y, z fore and hind), the two bones' and the foot's lengths. */
		static final float HIP_X = 3.7F, HIP_Y = 1.5F, FRONT_Z = -4.2F, HIND_Z = 4.0F, THIGH = 4.0F, SHIN = 4.5F, FOOT_H = 1.5F;
		/** The legs splay out a little (radians). */
		static final float SPLAY = 0.08F;
		/** Where an ankle rests ahead of its hip (pixels): the forelegs a touch forward, the hind straight under. */
		static final float FRONT_REACH = -0.5F, HIND_REACH = 0.0F;
		/** Flying, the legs tuck up under the body: the ankle's offset from its hip (z: fore, hind; y), and the toes' angle. */
		static final float TUCK_FRONT_Z = -0.5F, TUCK_HIND_Z = 2.5F, TUCK_Y = 3.6F, TUCK_TOE = 0.5F;
		/** Falling, the legs reach down and out: how far they reach (0-1), the ankle's offset (front z, hind z, y). */
		static final float REACH = 0.7F, REACH_FRONT_Z = -2.0F, REACH_HIND_Z = 1.5F, REACH_Y = 7.6F;
		/** The tail's resting pitch at each joint (+ is up): arching a little from the back, then drooping. */
		static final float[] TAIL_REST = {0.22F, -0.16F, -0.10F, 0.0F};
		/** How far the tail's lift (tailLift) may drop it and raise it (radians), and how much of it each joint takes. */
		static final float TAIL_DROOP = 0.35F, TAIL_RISE = 0.5F;
		private static final float[] TAIL_SHARE = {0.3F, 0.3F, 0.2F, 0.1F};
		/** The neck's resting pitch at its two joints and the head (+ is down): up from the chest, then level. */
		static final float NECK1 = -0.7F, NECK2 = 0.45F, HEAD = 0.3F;
		/** A bite: how far the neck straightens (joint 1, joint 2), how far the head thrusts forward (pixels), how wide the jaw opens (radians). */
		static final float BITE_NECK1 = 0.6F, BITE_NECK2 = -0.2F, BITE_THRUST = 0.9F, JAW_OPEN = 1.0F, JAW_REST = 0.06F;
		/** Flying, the neck stretches out ahead. */
		static final float FLY_NECK1 = 0.35F, FLY_NECK2 = -0.1F;
		/** How far the head follows where the player looks (degrees clamp: yaw, pitch). */
		static final float LOOK_YAW = 80.0F, LOOK_PITCH = 60.0F;
		/** Cruising (Wind's pose), the body dives and climbs after the look by this much of it (degrees clamp, share). */
		static final float DIVE_MAX = 60.0F, DIVE_FOLLOW = 0.7F;
		/** WindPose's air brake leans the body back this much (degrees); copied from it so it can be cancelled. */
		static final float CRUISE_BRAKE_LEAN = 25.0F;
		/** The spine's ridge tilts back this much (radians). */
		static final float SPIKE_TILT = -0.35F;
		/** Its flying wing beat bobs the body (pixels). */
		static final float BEAT_BOB = 0.6F;

		private static final float TWO_PI = (float)Math.PI * 2.0F;
		/** Spikes on the back: where each sits (parent: 0 torso, 1 first neck joint, 2 second, 3 first tail joint, 4 second; z along it). */
		private static final float[][] SPIKES = {
			{0, -3.5F}, {0, -1.0F}, {0, 1.5F}, {0, 4.0F}, {1, -2.0F}, {2, -1.0F}, {3, 2.5F}, {3, 5.0F}, {4, 2.5F}};

		private final ModelPart torso, chest, neck1, neck2, skull, jaw, tail1, tail2, tail3, spade;
		/** Fore right, fore left, hind right, hind left. */
		private final ModelPart[] thighs = new ModelPart[4], shins = new ModelPart[4], feet = new ModelPart[4];
		private final ModelPart[] spikes = new ModelPart[SPIKES.length];
		/** Every part that is animated, to put back to its resting pose each frame. */
		private final ModelPart[] moving;

		Wyrm() {
			super(bake(mesh()), "wyrm");
			// on the chest's front, under the neck (the man's body copies the torso's pose, so this is in the torso's space)
			this.insigniaY = 1.0F;
			this.insigniaZ = -8.0F;
			this.insigniaSize = 0.8F;
			this.torso = this.root().getChild("torso");
			this.chest = this.torso.getChild("chest");
			this.neck1 = this.torso.getChild("neck1");
			this.neck2 = this.neck1.getChild("neck2");
			this.skull = this.neck2.getChild("skull");
			this.jaw = this.skull.getChild("jaw");
			this.tail1 = this.torso.getChild("tail1");
			this.tail2 = this.tail1.getChild("tail2");
			this.tail3 = this.tail2.getChild("tail3");
			this.spade = this.tail3.getChild("spade");
			java.util.List<ModelPart> all = new java.util.ArrayList<>(java.util.List.of(this.torso, this.chest, this.neck1, this.neck2, this.skull,
				this.jaw, this.tail1, this.tail2, this.tail3, this.spade));
			for (int i = 0; i < 4; i++) {
				this.thighs[i] = this.torso.getChild("thigh" + i);
				this.shins[i] = this.thighs[i].getChild("shin");
				this.feet[i] = this.shins[i].getChild("foot");
				all.add(this.thighs[i]);
				all.add(this.shins[i]);
				all.add(this.feet[i]);
			}
			for (int i = 0; i < SPIKES.length; i++) {
				this.spikes[i] = this.spikeParent(i).getChild("spike" + i);
				all.add(this.spikes[i]);
			}
			this.moving = all.toArray(new ModelPart[0]);
		}

		/** The dragon's torso as it is posed this frame: every other part of it hangs off this, and the wings copy it ({@link WyrmWings}). */
		ModelPart torso() {
			return this.torso;
		}

		private ModelPart spikeParent(int i) {
			return switch ((int)SPIKES[i][0]) {
				case 0 -> this.torso;
				case 1 -> this.neck1;
				case 2 -> this.neck2;
				case 3 -> this.tail1;
				default -> this.tail2;
			};
		}

		private static MeshDefinition mesh() {
			MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.5F), 0.0F);
			PartDefinition root = mesh.getRoot();
			// the man's head, body and legs are never drawn (the arms are only for the first-person hand); his body is the
			// insignia's anchor, posed as the torso
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, -6.0F)).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, BODY_Y, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-HIP_X, BODY_Y + HIP_Y, HIND_Z));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(HIP_X, BODY_Y + HIP_Y, HIND_Z));
			PartDefinition torso = root.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, -3.0F, -5.5F, 7.0F, 6.0F, 11.0F),
				PartPose.offset(0.0F, BODY_Y, 0.0F));
			torso.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(36, 64).addBox(-3.5F, -3.5F, -2.0F, 7.0F, 7.0F, 4.0F), PartPose.offset(0.0F, 1.0F, -6.0F));
			// the neck: two joints rising from the chest, then the head with its snout, jaw and horns
			PartDefinition neck1 = torso.addOrReplaceChild("neck1", CubeListBuilder.create().texOffs(0, 92).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 3.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, -2.0F, -6.0F, NECK1, 0.0F, 0.0F));
			PartDefinition neck2 = neck1.addOrReplaceChild("neck2", CubeListBuilder.create().texOffs(38, 92).addBox(-1.5F, -1.5F, -4.0F, 3.0F, 3.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, -4.5F, NECK2, 0.0F, 0.0F));
			PartDefinition skull = neck2.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(18, 92).addBox(-2.5F, -2.0F, -4.0F, 5.0F, 4.0F, 5.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, -3.5F, HEAD, 0.0F, 0.0F));
			skull.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(8, 109).addBox(-1.5F, -0.7F, -4.0F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, 0.0F, -4.0F));
			skull.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(28, 101).addBox(-1.5F, 0.0F, -6.0F, 3.0F, 1.0F, 6.0F), PartPose.offset(0.0F, 1.4F, -0.5F));
			// the horns sweep back and out, up a little
			skull.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(46, 101).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F),
				PartPose.offsetAndRotation(-1.8F, -1.3F, 0.2F, 0.5F, -0.3F, 0.0F));
			skull.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(46, 101).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F),
				PartPose.offsetAndRotation(1.8F, -1.3F, 0.2F, 0.5F, 0.3F, 0.0F));
			// the tail: three joints and a spade
			PartDefinition tail1 = torso.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 81).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, -0.5F, 5.2F, TAIL_REST[0], 0.0F, 0.0F));
			PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(22, 81).addBox(-1.5F, -1.5F, -1.0F, 3.0F, 3.0F, 7.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, TAIL_REST[1], 0.0F, 0.0F));
			PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(0, 101).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 6.0F),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.5F, TAIL_REST[2], 0.0F, 0.0F));
			tail3.addOrReplaceChild("spade", CubeListBuilder.create().texOffs(42, 81).addBox(-0.5F, -2.5F, -1.0F, 1.0F, 5.0F, 5.0F)
				.texOffs(36, 109).addBox(-2.5F, -0.5F, -0.5F, 5.0F, 1.0F, 4.0F), PartPose.offsetAndRotation(0.0F, 0.0F, 4.5F, TAIL_REST[3], 0.0F, 0.0F));
			// the spine ridge
			for (int i = 0; i < SPIKES.length; i++) {
				PartDefinition parent = switch ((int)SPIKES[i][0]) {
					case 0 -> torso;
					case 1 -> neck1;
					case 2 -> neck2;
					case 3 -> tail1;
					default -> tail2;
				};
				float y = switch ((int)SPIKES[i][0]) {
					case 0 -> -3.0F;
					case 1, 2 -> -1.5F;
					case 3 -> -2.0F;
					default -> -1.5F;
				};
				parent.addOrReplaceChild("spike" + i, CubeListBuilder.create().texOffs(54, 109).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F),
					PartPose.offsetAndRotation(0.0F, y, SPIKES[i][1], SPIKE_TILT, 0.0F, 0.0F));
			}
			// the legs: thigh, shin, foot; fore right, fore left, hind right, hind left
			for (int i = 0; i < 4; i++) {
				boolean front = i < 2, left = (i & 1) == 1;
				PartDefinition thigh = torso.addOrReplaceChild("thigh" + i, CubeListBuilder.create().texOffs(16, 101).addBox(-1.5F, -0.5F, -1.5F, 3.0F, 5.0F, 3.0F),
					PartPose.offset(left ? HIP_X : -HIP_X, HIP_Y, front ? FRONT_Z : HIND_Z));
				PartDefinition shin = thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(0, 109).addBox(-1.0F, -0.5F, -1.0F, 2.0F, 5.0F, 2.0F),
					PartPose.offset(0.0F, THIGH, 0.0F));
				shin.addOrReplaceChild("foot", CubeListBuilder.create().texOffs(22, 109).addBox(-1.5F, -0.5F, -3.5F, 3.0F, 2.0F, 4.0F), PartPose.offset(0.0F, SHIN, 0.0F));
			}
			return mesh;
		}

		/** Where a foot is along its stride (-1 forward .. +1 back, relative to the body) a fraction u of the way through its cycle. */
		private static float stride(float u) {
			if (u < DUTY) {
				// planted: the ground slides back under it
				return -1.0F + 2.0F * u / DUTY;
			}
			// swinging: forward again, easing at both ends
			float s = (u - DUTY) / (1.0F - DUTY);
			return 1.0F - 2.0F * (0.5F - 0.5F * Mth.cos(s * (float)Math.PI));
		}

		/** How high a foot is lifted (0-1) a fraction u of the way through its cycle. */
		private static float lifted(float u) {
			return u < DUTY ? 0.0F : Mth.sin((u - DUTY) / (1.0F - DUTY) * (float)Math.PI);
		}

		/**
		 * Two bones from the hip to an ankle {@code (tz, ty)} away (z back, y down, from the hip), the middle joint
		 * forward (hind legs) or back (forelegs); the foot is kept at {@code toe} to the ground.
		 */
		private static void solve(ModelPart thigh, ModelPart shin, ModelPart foot, float tz, float ty, boolean kneeForward, float toe) {
			float reach = Mth.clamp((float)Math.sqrt(tz * tz + ty * ty), Math.abs(THIGH - SHIN) + 0.3F, THIGH + SHIN - 0.05F);
			float dir = (float)Math.atan2(tz, ty);
			float bend = (float)Math.acos(Mth.clamp((THIGH * THIGH + reach * reach - SHIN * SHIN) / (2.0F * THIGH * reach), -1.0F, 1.0F));
			float a = kneeForward ? dir - bend : dir + bend;
			float ankleZ = reach * Mth.sin(dir), ankleY = reach * Mth.cos(dir);
			float b = (float)Math.atan2(ankleZ - THIGH * Mth.sin(a), ankleY - THIGH * Mth.cos(a)) - a;
			thigh.xRot = a;
			shin.xRot = b;
			foot.xRot = -(a + b) + toe;
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			// the man is never drawn: his body is only the insignia's anchor (posed as the torso below)
			this.head.visible = this.hat.visible = this.body.visible = false;
			this.rightArm.visible = this.leftArm.visible = this.rightLeg.visible = this.leftLeg.visible = false;
			this.body.resetPose();
			for (ModelPart part : this.moving) {
				part.resetPose();
			}
			float t = state.ageInTicks;
			XenoMotion.Read m = XenoMotion.read(state);
			XenoformFlightPose.Flight flight = state.getData(XenoformFlightPose.KEY);
			float fly = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.wings());
			float fold = flight == null ? 0.0F : XenoformFlightPose.smooth(flight.fold());
			Float cruiseData = state.getData(com.jaymospowers.client.power.wind.WindPose.KEY);
			float cruise = cruiseData == null || state.deathTime > 0.0F ? 0.0F : cruiseData;

			// ---- the walk: diagonal pairs (fore right with hind left) in step, the torso bobbing twice a cycle ----
			float walk = Mth.clamp(state.walkAnimationSpeed / WALK_FULL, 0.0F, 1.0F) * (1.0F - fly);
			float cycle = state.walkAnimationPos * GAIT / TWO_PI;
			cycle -= (float)Math.floor(cycle);
			float rise = 0.5F + 0.5F * Mth.cos(2.0F * TWO_PI * (cycle - 0.3F));
			float by = BODY_Y - BOB * rise * walk + m.land() * LAND_DIP;
			// the ground, in model y: higher when sneaking, as the whole model is drawn lower
			float ground = 24.0F;
			if (state.isCrouching) {
				by += CROUCH_DROP - CROUCH_SINK;
				ground -= CROUCH_SINK;
			}
			// flying, the wings' beat lifts it
			by += Mth.sin(t * WyrmWings.FLAP_RATE) * BEAT_BOB * fly * (1.0F - fold);
			float roll = Mth.sin(cycle * TWO_PI) * ROLL * walk;
			float wriggle = Mth.sin(cycle * TWO_PI + 0.4F) * WRIGGLE * walk;

			// ---- the torso. Wind's cruise tilt (WindPose.rotate) lays an upright body flat about the middle of the box, and rolls
			// it about its length; a dragon already lies level, so it tips back by the same angle about the same point, and
			// follows the look's pitch instead ----
			float tilt = 0.0F;
			if (cruise > 0.0F) {
				Float brakeData = state.getData(com.jaymospowers.client.power.wind.WindPose.BRAKE_KEY);
				float brake = brakeData == null ? 0.0F : brakeData;
				float laid = cruise * (1.0F - 0.75F * brake) * (-90.0F - state.xRot) + brake * CRUISE_BRAKE_LEAN;
				float dive = Mth.clamp(state.xRot, -DIVE_MAX, DIVE_MAX) * cruise * DIVE_FOLLOW;
				tilt = (laid + dive) * Mth.DEG_TO_RAD;
			}
			// the pivot, in model pixels: half the box's height over the feet (WindPose's), through the renderer's scale
			float middle = (MODEL_ORIGIN - state.boundingBoxHeight / Math.max(state.scale, 0.01F) * 0.5F / PLAYER_SCALE) * 16.0F;
			float toMiddle = by - middle;
			this.torso.y = middle + toMiddle * Mth.cos(tilt);
			this.torso.z = toMiddle * Mth.sin(tilt);
			this.torso.xRot = tilt;
			this.torso.yRot = wriggle;
			this.torso.zRot = roll;
			this.chest.yScale = 1.0F + m.breath() * 0.03F;
			// the man's body is the insignia's anchor: on the torso
			this.body.x = this.torso.x;
			this.body.y = this.torso.y;
			this.body.z = this.torso.z;
			this.body.xRot = this.torso.xRot;
			this.body.yRot = this.torso.yRot;
			this.body.zRot = this.torso.zRot;

			// ---- the legs ----
			float air = m.air() * (1.0F - fly) * REACH;
			for (int i = 0; i < 4; i++) {
				boolean front = i < 2, left = (i & 1) == 1;
				float u = cycle + (front == left ? 0.5F : 0.0F);
				u -= (float)Math.floor(u);
				float swing = lifted(u) * walk;
				float tz = (front ? FRONT_REACH : HIND_REACH) + STRIDE * walk * stride(u);
				// the ankle's height over the ground, in the torso's frame: the planted foot stays on the ground as the torso bobs
				float ty = ground - FOOT_H - by - HIP_Y - LIFT * swing;
				float toe = -TOE_LIFT * swing;
				// falling: reaching down; flying: tucked up under the body
				tz = Mth.lerp(air, tz, front ? REACH_FRONT_Z : REACH_HIND_Z);
				ty = Mth.lerp(air, ty, REACH_Y);
				tz = Mth.lerp(fly, tz, front ? TUCK_FRONT_Z : TUCK_HIND_Z);
				ty = Mth.lerp(fly, ty, TUCK_Y);
				toe = Mth.lerp(fly, toe, TUCK_TOE);
				solve(this.thighs[i], this.shins[i], this.feet[i], tz, ty, !front, toe);
				this.thighs[i].zRot = left ? -SPLAY : SPLAY;
				this.shins[i].zRot = left ? SPLAY * 0.8F : -SPLAY * 0.8F;
			}

			// ---- the neck and head: they follow where the player looks, and a bite throws the head forward ----
			float bite = Mth.sin(Mth.clamp(state.swingAnimation, 0.0F, 1.0F) * (float)Math.PI);
			float yaw = Mth.clamp(state.yRot, -LOOK_YAW, LOOK_YAW) * Mth.DEG_TO_RAD * (1.0F - cruise);
			float pitch = Mth.clamp(state.xRot, -LOOK_PITCH, LOOK_PITCH) * Mth.DEG_TO_RAD * (1.0F - DIVE_FOLLOW * cruise);
			// the head rides level against the torso's bob, and sways a little as it breathes
			float nod = Mth.cos(2.0F * TWO_PI * (cycle - 0.3F)) * 0.06F * walk + m.breath() * 0.03F;
			float sway = Mth.sin(t * 0.05F) * 0.05F;
			this.neck1.xRot = NECK1 + pitch * 0.3F + BITE_NECK1 * bite + FLY_NECK1 * fly;
			this.neck2.xRot = NECK2 + pitch * 0.3F + BITE_NECK2 * bite + FLY_NECK2 * fly + nod;
			this.skull.xRot = HEAD + pitch * 0.4F - nod;
			this.neck1.yRot = yaw * 0.3F - wriggle * 1.5F + sway;
			this.neck2.yRot = yaw * 0.3F - sway * 0.5F;
			this.skull.yRot = yaw * 0.4F;
			this.skull.zRot = Mth.sin(t * 0.07F + 0.5F) * 0.03F;
			this.neck2.z -= BITE_THRUST * bite;
			this.skull.z -= BITE_THRUST * bite;
			this.jaw.xRot = JAW_REST + (1.0F + Mth.sin(t * 0.09F)) * 0.04F + JAW_OPEN * bite;

			// ---- the tail swings behind each turn, each joint a beat after the last; it lifts as it drops, whips down on landing and droops when crouched ----
			float lag = m.trail() - m.turn();
			float tailUp = Mth.clamp(tailLift(m), -TAIL_DROOP, TAIL_RISE);
			float wag = Mth.cos(cycle * TWO_PI) * 0.25F * walk + Mth.sin(t * 0.08F) * 0.08F;
			this.tail1.yRot = wag - m.trail() * 0.5F - wriggle * 1.2F;
			this.tail2.yRot = Mth.sin(t * 0.08F - 0.8F) * 0.1F + wag * 0.6F - lag * 0.6F - m.trail() * 0.2F;
			this.tail3.yRot = Mth.sin(t * 0.08F - 1.6F) * 0.14F + wag * 0.5F - lag * 0.8F;
			this.spade.yRot = Mth.sin(t * 0.08F - 2.4F) * 0.2F + wag * 0.4F - lag * 0.8F;
			this.tail1.xRot = TAIL_REST[0] + walk * 0.05F + tailUp * TAIL_SHARE[0] + fly * 0.1F;
			this.tail2.xRot = TAIL_REST[1] + tailUp * TAIL_SHARE[1] - fly * 0.05F;
			this.tail3.xRot = TAIL_REST[2] + tailUp * TAIL_SHARE[2];
			this.spade.xRot = TAIL_REST[3] + tailUp * TAIL_SHARE[3];

			// ---- the ridge ripples ----
			for (int i = 0; i < this.spikes.length; i++) {
				this.spikes[i].xRot = SPIKE_TILT + Mth.sin(t * 0.1F - i * 0.7F) * 0.04F + m.breath() * 0.02F;
			}
		}
	}
}
