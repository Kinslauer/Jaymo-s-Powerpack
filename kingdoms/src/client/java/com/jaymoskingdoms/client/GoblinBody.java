package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.321, Andy: "can this replace our goblins?", then "Whole body, clothes redrawn") A goblin villager's body: the
 * shape of kokosikk's Blockbench Workshop goblin (CC BY 4.0, {@code tools/blockbench/kokosikk_goblin.bbmodel}, credit in
 * {@code textures/entity/greenskin/CREDITS.txt}): a big head, a short tunic of a body bent forward in a hunch, thin arms
 * and short bandy legs.
 * <ul>
 * <li>Every humanoid body drawn for a goblin (MCA's skin, face, clothes and hair, its marks, any armour) is reshaped bone
 * by bone into the goblin's ({@link #apply}): each bone moved to the goblin's pivot and scaled so a man's box fills the
 * goblin's (the head wider, the body shorter and deeper, arms and legs thin and short), the body, head and arms leant
 * into the hunch. So whatever MCA dresses a goblin in is drawn on the goblin.</li>
 * <li>On it ({@link #submit}), parts of our own from the model (textures from {@code tools/gen_goblin_body.py}): ears out
 * and up, a drooping nose, two small tusks, knobbly fists, wide flat feet with toenails, and its gear: a belt with a
 * buckle and a pouch, a loincloth, wraps round the shins; half of them a red cap, one in four an earring. Flesh is tinted
 * by each goblin's green; feet and wraps go under boots, the loincloth under leggings, the cap under a helmet.</li>
 * <li>What it holds sits in its fist ({@link #hand}).</li>
 * </ul>
 * Orcs keep MCA's body ({@link GreenskinLooks}).
 */
public final class GoblinBody {
	enum Bone { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

	/** When a part is drawn: always, without boots (feet), without boots or leggings (wraps), without leggings, with the cap, with the earring. */
	enum Flag { ALWAYS, FEET, WRAPS, LOIN, CAP, EARRING }

	/** Where each bone's pivot is on a man's body (MCA's), as the bones are first posed (pixels). */
	static final float[][] HUMAN = {{0.0F, 0.0F, 0.0F}, {0.0F, 0.0F, 0.0F}, {-5.0F, 2.0F, 0.0F}, {5.0F, 2.0F, 0.0F}, {-1.9F, 12.0F, 0.0F},
		{1.9F, 12.0F, 0.0F}};
	/** Where each bone's pivot is on the goblin's, before the hunch (keep in step with gen_goblin_body.py's PIVOT). */
	static final float[][] PIVOTS = {{0.0F, 6.7F, -1.2F}, {0.0F, 6.5F, 0.0F}, {-4.65F, 8.8F, -0.4F}, {4.65F, 8.8F, -0.4F}, {-3.3F, 16.0F, 0.0F},
		{3.3F, 16.0F, 0.0F}};
	/** How much each bone of a man's body is scaled to fill the goblin's (gen_goblin_body.py's SCALE). */
	static final float[][] SCALES = {{1.125F, 1.0F, 1.0F}, {1.0F, 0.75F, 1.25F}, {0.65F, 0.75F, 0.65F}, {0.65F, 0.75F, 0.65F},
		{0.75F, 0.6667F, 0.75F}, {0.75F, 0.6667F, 0.75F}};
	/** The hunch (radians, the body bent forward at the hip), the hip it bends at, and how much of it the arms hang with. */
	static final float HUNCH = 12.0F * Mth.DEG_TO_RAD, ARM_LEAN = 0.5F;
	static final float[] HIP = {0.0F, 15.5F, 0.0F};
	/** How much of a man's moves away from the pose (crouching, sitting) the goblin's bones take: it's smaller. */
	static final float MOVE = 0.75F;
	/**
	 * Where an item held in the fist sits, from where it would sit in a man's hand (pixels, out along the arm and to the
	 * side): the goblin's arm is shorter and its fist lower on it.
	 */
	static final float HAND_DOWN = -3.8F, HAND_SIDE = 0.35F;
	/** How far a reshaped right leg's pivot is off a man's, beyond which a body is known to be posed as a goblin's already. */
	static final float POSED = 0.7F;

	/** The pivots of the head, body and arms with the hunch, worked out once. */
	static final float[][] HUNCHED = new float[6][];

	static {
		for (Bone bone : Bone.values()) {
			float[] p = PIVOTS[bone.ordinal()];
			if (bone == Bone.RIGHT_LEG || bone == Bone.LEFT_LEG) {
				HUNCHED[bone.ordinal()] = p;
				continue;
			}
			float y = p[1] - HIP[1], z = p[2] - HIP[2];
			float cos = Mth.cos(HUNCH), sin = Mth.sin(HUNCH);
			HUNCHED[bone.ordinal()] = new float[] {p[0], HIP[1] + y * cos - z * sin, HIP[2] + y * sin + z * cos};
		}
	}

	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/greenskin/goblin_body.png");

	/** One box: its place on the sheet, its corner, its size on the sheet and how far it's grown to its true size. */
	record Box(int u, int v, float x, float y, float z, int w, int h, int d, float gx, float gy, float gz) {
	}

	/** One part: on which bone, when it's drawn, whether it's flesh (tinted), its pivot off the bone's and turn, its box. */
	record Part(Bone bone, Flag flag, boolean flesh, String name, float px, float py, float pz, float rx, float ry, float rz, Box box) {
	}

	private static Box box(int u, int v, float x, float y, float z, int w, int h, int d, float gx, float gy, float gz) {
		return new Box(u, v, x, y, z, w, h, d, gx, gy, gz);
	}

	private static Part part(Bone bone, Flag flag, boolean flesh, String name, float px, float py, float pz, float rx, float ry, float rz, Box box) {
		return new Part(bone, flag, flesh, name, px, py, pz, rx, ry, rz, box);
	}

	/** The parts ({@code tools/gen_goblin_body.py --java}). */
	static final Part[] PARTS = {
		part(Bone.RIGHT_LEG, Flag.FEET, true, "foot_R", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 0, -2.5F, 6.0F, -3.8F, 5, 2, 6, 0.0F, 0.0F, 0.0F)),
		part(Bone.RIGHT_LEG, Flag.FEET, false, "toenail_R1", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(23, 0, -2.2F, 6.8F, -4.7F, 1, 1, 1, 0.0F, 0.2F, 0.0F)),
		part(Bone.RIGHT_LEG, Flag.FEET, false, "toenail_R2", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(28, 0, -0.5F, 6.8F, -4.7F, 1, 1, 1, 0.0F, 0.2F, 0.0F)),
		part(Bone.RIGHT_LEG, Flag.FEET, false, "toenail_R3", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(33, 0, 1.2F, 6.8F, -4.7F, 1, 1, 1, 0.0F, 0.2F, 0.0F)),
		part(Bone.RIGHT_LEG, Flag.WRAPS, false, "shin_wrap_R", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(38, 0, -1.5F, 2.6F, -1.5F, 3, 3, 3, 0.2F, -0.2F, 0.2F)),
		part(Bone.LEFT_LEG, Flag.FEET, true, "foot_L", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 9, -2.5F, 6.0F, -3.8F, 5, 2, 6, 0.0F, 0.0F, 0.0F)),
		part(Bone.LEFT_LEG, Flag.FEET, false, "toenail_L1", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(23, 9, -2.2F, 6.8F, -4.7F, 1, 1, 1, 0.0F, 0.2F, 0.0F)),
		part(Bone.LEFT_LEG, Flag.FEET, false, "toenail_L2", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(28, 9, -0.5F, 6.8F, -4.7F, 1, 1, 1, 0.0F, 0.2F, 0.0F)),
		part(Bone.LEFT_LEG, Flag.FEET, false, "toenail_L3", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(33, 9, 1.2F, 6.8F, -4.7F, 1, 1, 1, 0.0F, 0.2F, 0.0F)),
		part(Bone.LEFT_LEG, Flag.WRAPS, false, "shin_wrap_L", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(38, 9, -1.5F, 2.6F, -1.5F, 3, 3, 3, 0.2F, -0.2F, 0.2F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "nose_cube", 0.0F, -3.1F, -4.0F, 0.2443F, 0.0F, 0.0F, box(51, 9, -1.0F, -0.9F, -2.8F, 2, 3, 3, 0.0F, 0.0F, 0.0F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_base", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 18, -5.7F, -6.5F, -1.9F, 1, 4, 5, 0.2F, 0.2F, -0.2F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_mid", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(13, 18, -7.05F, -7.1F, -0.9F, 1, 3, 3, 0.15F, 0.1F, 0.2F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_tip", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(22, 18, -8.3F, -7.8F, -0.4F, 1, 2, 2, 0.1F, 0.2F, 0.2F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_end", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(29, 18, -9.4F, -8.8F, 0.1F, 1, 2, 1, 0.0F, -0.2F, 0.2F)),
		part(Bone.HEAD, Flag.EARRING, false, "earring", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(34, 18, -5.7F, -2.1F, 0.1F, 1, 1, 1, -0.15F, 0.2F, -0.15F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_base", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(39, 18, 4.7F, -6.5F, -1.9F, 1, 4, 5, 0.2F, 0.2F, -0.2F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_mid", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(52, 18, 6.05F, -7.1F, -0.9F, 1, 3, 3, 0.15F, 0.1F, 0.2F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_tip", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 28, 7.3F, -7.8F, -0.4F, 1, 2, 2, 0.1F, 0.2F, 0.2F)),
		part(Bone.HEAD, Flag.ALWAYS, true, "ear_end", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(7, 28, 8.4F, -8.8F, 0.1F, 1, 2, 1, 0.0F, -0.2F, 0.2F)),
		part(Bone.HEAD, Flag.CAP, false, "cap_tip_cube", 0.5F, -10.1F, 0.4F, 0.0F, 0.0F, 0.384F, box(12, 28, -0.7F, -1.5F, -1.5F, 3, 2, 3, 0.0F, 0.2F, 0.0F)),
		part(Bone.HEAD, Flag.CAP, false, "cap_pom", 0.5F, -10.1F, 0.4F, 0.0F, 0.0F, 0.384F, box(25, 28, 0.3F, -2.7F, -0.5F, 1, 1, 1, 0.2F, 0.2F, 0.2F)),
		part(Bone.HEAD, Flag.CAP, false, "cap_band", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 34, -5.0F, -8.0F, -4.4F, 10, 2, 9, -0.2F, 0.2F, -0.2F)),
		part(Bone.HEAD, Flag.CAP, false, "cap_top", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(39, 34, -2.8F, -9.8F, -2.8F, 6, 2, 6, 0.0F, 0.0F, 0.0F)),
		part(Bone.HEAD, Flag.ALWAYS, false, "tusk_R", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 46, -2.5F, -2.3F, -4.85F, 1, 2, 1, -0.05F, -0.1F, -0.05F)),
		part(Bone.HEAD, Flag.ALWAYS, false, "tusk_L", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(5, 46, 1.5F, -2.3F, -4.85F, 1, 2, 1, -0.05F, -0.1F, -0.05F)),
		part(Bone.RIGHT_ARM, Flag.ALWAYS, true, "fist_R", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(10, 46, -2.15F, 4.7F, -1.5F, 3, 3, 3, 0.0F, -0.2F, 0.0F)),
		part(Bone.LEFT_ARM, Flag.ALWAYS, true, "fist_L", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(23, 46, -0.85F, 4.7F, -1.5F, 3, 3, 3, 0.0F, -0.2F, 0.0F)),
		part(Bone.BODY, Flag.ALWAYS, false, "belt", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 53, -4.5F, 6.1F, -3.0F, 9, 2, 6, -0.15F, -0.2F, -0.15F)),
		part(Bone.BODY, Flag.ALWAYS, false, "belt_buckle", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(31, 53, -1.0F, 6.1F, -3.6F, 2, 2, 1, 0.0F, -0.1F, -0.2F)),
		part(Bone.BODY, Flag.ALWAYS, false, "pouch", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(38, 53, -6.9F, 6.9F, -1.6F, 3, 3, 2, 0.0F, 0.1F, 0.0F)),
		part(Bone.BODY, Flag.LOIN, false, "loincloth", 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(49, 53, -2.5F, 7.85F, -3.25F, 5, 4, 1, 0.2F, -0.05F, -0.1F))
	};

	/** Per bone, per flag: the flesh parts [0] and the rest [1], baked the first time (null: none). */
	private static @Nullable Map<Bone, Map<Flag, ModelPart[]>> baked;

	private GoblinBody() {
	}

	/** {@code state} is a goblin's (an MCA villager of the goblin brood). */
	static boolean goblin(Object state) {
		if (!(state instanceof HumanoidRenderState humanoid)) {
			return false;
		}
		GreenskinLooks.Look look = humanoid.getData(GreenskinLooks.KEY);
		return look != null && look.kind() == Heritage.Kind.GOBLIN;
	}

	private static ModelPart bone(HumanoidModel<?> model, Bone bone) {
		return switch (bone) {
			case HEAD -> model.head;
			case BODY -> model.body;
			case RIGHT_ARM -> model.rightArm;
			case LEFT_ARM -> model.leftArm;
			case RIGHT_LEG -> model.rightLeg;
			case LEFT_LEG -> model.leftLeg;
		};
	}

	/**
	 * Right after a body was posed for {@code state} (the villager's own, each MCA layer's, each armour piece's): a
	 * goblin's reshaped into the goblin's. MCA's layers can take the pose already reshaped (their skin layer hands the
	 * villager's pose on, scale left out), so the move into the goblin's pose is made once and the scale always.
	 */
	public static void apply(Model<?> model, Object state) {
		if (!(model instanceof HumanoidModel<?> m) || !goblin(state)) {
			return;
		}
		boolean posed = Math.abs(m.rightLeg.x - HUMAN[Bone.RIGHT_LEG.ordinal()][0]) > POSED;
		for (Bone bone : Bone.values()) {
			ModelPart part = bone(m, bone);
			int i = bone.ordinal();
			if (!posed) {
				part.x = HUNCHED[i][0] + (part.x - HUMAN[i][0]) * MOVE;
				part.y = HUNCHED[i][1] + (part.y - HUMAN[i][1]) * MOVE;
				part.z = HUNCHED[i][2] + (part.z - HUMAN[i][2]) * MOVE;
			}
			part.xScale = SCALES[i][0];
			part.yScale = SCALES[i][1];
			part.zScale = SCALES[i][2];
		}
		if (!posed) {
			m.body.xRot += HUNCH;
			m.rightArm.xRot += HUNCH * ARM_LEAN;
			m.leftArm.xRot += HUNCH * ARM_LEAN;
		}
		if (McaCompat.LOADED) {
			// (a woman's breasts are parts of their own, posed after the body: onto the goblin's chest)
			McaLookModels.goblinChest(m, HUNCHED[Bone.BODY.ordinal()], SCALES[Bone.BODY.ordinal()], HUNCH);
		}
	}

	/**
	 * At the end of {@code model.translateToHand}: a goblin's item in its fist, unsquashed by the arm's scale, up the
	 * shorter arm and in to the fist.
	 */
	public static void hand(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack) {
		if (!goblin(state)) {
			return;
		}
		ModelPart part = model.getArm(arm);
		if (part.xScale == 0.0F || part.yScale == 0.0F || part.zScale == 0.0F) {
			return;
		}
		poseStack.scale(1.0F / part.xScale, 1.0F / part.yScale, 1.0F / part.zScale);
		poseStack.translate((arm == HumanoidArm.RIGHT ? HAND_SIDE : -HAND_SIDE) / 16.0F, HAND_DOWN / 16.0F, 0.0F);
	}

	/** A goblin's parts on {@code model}'s bones ({@link GreenskinLooks}): its flesh in {@code color}, the rest as painted. */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state, HumanoidModel<?> model,
		GreenskinLooks.Look look) {
		Map<Bone, Map<Flag, ModelPart[]>> parts = baked();
		boolean boots = !state.feetEquipment.isEmpty(), leggings = !state.legsEquipment.isEmpty();
		EnumMap<Flag, Boolean> on = new EnumMap<>(Flag.class);
		on.put(Flag.ALWAYS, true);
		on.put(Flag.FEET, !boots);
		on.put(Flag.WRAPS, !boots && !leggings);
		on.put(Flag.LOIN, !leggings);
		// (the shape and the scars, which a goblin's look doesn't use, decide its cap and its earring)
		on.put(Flag.CAP, look.shape() == 0 && state.headEquipment.isEmpty());
		on.put(Flag.EARRING, look.scars() == 0);
		for (Map.Entry<Bone, Map<Flag, ModelPart[]>> entry : parts.entrySet()) {
			ModelPart part = bone(model, entry.getKey());
			if (!part.visible) {
				continue;
			}
			poseStack.pushPose();
			part.translateAndRotate(poseStack);
			// (the parts are the goblin's own size: the bone's scale is for a man's boxes)
			poseStack.scale(1.0F / part.xScale, 1.0F / part.yScale, 1.0F / part.zScale);
			for (Map.Entry<Flag, ModelPart[]> flagged : entry.getValue().entrySet()) {
				if (!on.get(flagged.getKey())) {
					continue;
				}
				ModelPart[] baked = flagged.getValue();
				if (baked[0] != null) {
					collector.order(1).submitModelPart(baked[0], poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY,
						null, look.color());
				}
				if (baked[1] != null) {
					collector.order(1).submitModelPart(baked[1], poseStack, RenderTypes.entityCutout(TEXTURE), light, OverlayTexture.NO_OVERLAY,
						null, -1);
				}
			}
			poseStack.popPose();
		}
	}

	/** The parts, one root per bone, flag and kind (flesh or not), baked the first time. */
	private static Map<Bone, Map<Flag, ModelPart[]>> baked() {
		if (baked != null) {
			return baked;
		}
		Map<Bone, Map<Flag, MeshDefinition[]>> meshes = new EnumMap<>(Bone.class);
		for (int i = 0; i < PARTS.length; i++) {
			Part part = PARTS[i];
			MeshDefinition[] kinds = meshes.computeIfAbsent(part.bone(), b -> new EnumMap<>(Flag.class))
				.computeIfAbsent(part.flag(), f -> new MeshDefinition[2]);
			int kind = part.flesh() ? 0 : 1;
			if (kinds[kind] == null) {
				kinds[kind] = new MeshDefinition();
			}
			Box b = part.box();
			kinds[kind].getRoot().addOrReplaceChild(part.name() + "_" + i, CubeListBuilder.create().texOffs(b.u(), b.v())
					.addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d(), new CubeDeformation(b.gx(), b.gy(), b.gz())),
				PartPose.offsetAndRotation(part.px(), part.py(), part.pz(), part.rx(), part.ry(), part.rz()));
		}
		Map<Bone, Map<Flag, ModelPart[]>> done = new EnumMap<>(Bone.class);
		for (Map.Entry<Bone, Map<Flag, MeshDefinition[]>> entry : meshes.entrySet()) {
			Map<Flag, ModelPart[]> flags = new EnumMap<>(Flag.class);
			for (Map.Entry<Flag, MeshDefinition[]> flagged : entry.getValue().entrySet()) {
				MeshDefinition[] kinds = flagged.getValue();
				flags.put(flagged.getKey(), new ModelPart[] {
					kinds[0] == null ? null : LayerDefinition.create(kinds[0], 64, 64).bakeRoot(),
					kinds[1] == null ? null : LayerDefinition.create(kinds[1], 64, 64).bakeRoot()});
			}
			done.put(entry.getKey(), flags);
		}
		baked = done;
		return done;
	}
}
