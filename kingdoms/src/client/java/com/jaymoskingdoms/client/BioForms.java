package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BioBuilt;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageHold;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.313, Andy: "I also want the forms to be drastically different from each other. with the third form becoming the
 * most powerful, and stable ... more akin to Cell from dragon ball ... the first form shouldnt necessarily be larval, but
 * intensely grotesque and with a tail") A Bio-built player's three forms ({@link BioBuilt#form}), each a body of its own
 * over theirs (textures and the parts table from {@code tools/gen_bio_forms.py}, in the palette of their variant: the
 * seamed green, the Brassbound's bronze, the Opaline's pearl):
 * <ul>
 * <li><b>Imperfect</b> (the first): hunched ({@link #apply}: the head thrust forward and low, the arms hanging forward),
 * a spotted carapace with black chest plates and an orange segmented belly, an insect face with a beak and pink slit
 * eyes, V horns, stubby wing cases, claws, a spine ridge and a growth on one shoulder, and a long segmented tail with a
 * stinger, swaying.</li>
 * <li><b>Semi-Perfect</b> (the second): big and bulked (wider body and arms, and a size up, {@link BioBuilt#SIZE}),
 * black forearms and shins, heavy lips and no nose, a crown of horns, pauldrons; a short thick tail.</li>
 * <li><b>Perfect</b> (the third): sleek, a pale face with purple cheek stripes, the spotted skin under a black chest
 * and shoulders, slim crown horns, long wings down the back, and the tail folded away to a stinger nub (it unfurls to
 * absorb, {@link BioTailEngulf}).</li>
 * </ul>
 * Down the spine of every form run twelve beads, one in each heritage's colour (their blood in them), lit. Drawn instead
 * of their marks in {@link HeritageLooks}; MCA's face, clothes and hair are left off (the form is all of them), and so are
 * a vanilla skin's hat and sleeves. Under a costume nothing of the body shows, and under a hood no horns.
 */
public final class BioForms {
	/** The palettes, by variant: the first look, the Brassbound, the Opaline. */
	static final String[] PALETTES = {"seamed", "brassbound", "opaline"};
	/** The second form's bulk (body and arms, wide and deep) and how far out its arms sit (model pixels). */
	static final float BULK = 1.14F, BULK_SHOULDER = 0.98F;
	/** The first form's hunch: the head forward and down (pixels), the arms forward (pixels) and swung forward (radians). */
	static final float HUNCH_HEAD_DOWN = 1.2F, HUNCH_HEAD_FORWARD = 1.6F, HUNCH_ARMS_FORWARD = 0.8F, HUNCH_ARMS_SWING = 0.25F;

	/** A form's idle tail: each segment's length (pixels), the first one's thickness, how much each gets thinner, how far it sways (radians), and the bends. */
	record Tail(float length, float thick, float taper, float sway, float[] bends, int u, int v, int[][] sting) {
	}

	/** The tails of the first two forms (the third's is folded away). */
	static final Tail[] TAILS = {
		new Tail(2.8F, 2.6F, 0.93F, 0.12F, new float[] {-0.72F, -0.12F, -0.08F, 0.0F, 0.08F, 0.14F, 0.22F, 0.3F, 0.38F, 0.42F}, 46, 24,
			new int[][] {{0, 35}, {10, 35}}),
		new Tail(2.8F, 3.0F, 0.9F, 0.16F, new float[] {-1.0F, 0.1F, 0.25F, 0.35F, 0.45F, 0.5F, 0.5F}, 32, 8, new int[][] {{46, 8}, {56, 8}}),
		null};
	/** A tail segment's box (pixels, scaled to each tail), where it starts on the body, and the stinger's two boxes. */
	static final float[] SEGMENT = {4.0F, 4.0F, 3.0F}, TAIL_ROOT = {0.0F, 10.5F, 1.8F};
	static final float[][] STING = {{2, 2, 3, -1, -1, 0}, {1, 1, 3, -0.5F, -0.5F, 2.6F}};
	/** How fast the tail sways (radians a tick). */
	static final float SWAY_SPEED = 0.07F;

	/** What a Bio-built player being drawn looks like: their form, palette, and whether the tail is off absorbing. */
	record Shape(int form, int palette, boolean absorbing) {
	}

	public static final RenderStateDataKey<Shape> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:bio_form");

	enum Attach { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

	/** One box of a part: its place on the parts sheet, its corner and size (pixels). */
	record Box(int u, int v, float x, float y, float z, float w, float h, float d) {
	}

	/** One part of a form: on which bone, its name, the part it hangs from (or none), its pivot and turn, its boxes. */
	record Part(int form, Attach attach, String name, @Nullable String parent, float px, float py, float pz, float rx, float ry, float rz, Box... boxes) {
	}

	private static Box box(int u, int v, float x, float y, float z, float w, float h, float d) {
		return new Box(u, v, x, y, z, w, h, d);
	}

	private static Part part(int form, Attach attach, String name, @Nullable String parent, float px, float py, float pz, float rx, float ry, float rz,
		Box... boxes) {
		return new Part(form, attach, name, parent, px, py, pz, rx, ry, rz, boxes);
	}

	/** Every form's parts ({@code tools/gen_bio_forms.py --java}). */
	static final Part[] PARTS = {
			part(0, Attach.HEAD, "horn_r", null, -2.4F, -7.0F, -1.5F, -0.55F, 0.0F, -0.42F, box(0, 0, -1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F)),
			part(0, Attach.HEAD, "horn_tip_r", "horn_r", 0.0F, -6.6F, 0.0F, -0.45F, 0.0F, -0.1F, box(8, 0, -0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F)),
			part(0, Attach.BODY, "wing_r", null, -0.6F, 0.6F, 2.05F, 0.14F, 0.0F, 0.07F, box(12, 0, -4.0F, 0.0F, 0.0F, 4.0F, 11.0F, 1.0F)),
			part(0, Attach.RIGHT_ARM, "claw_r", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(22, 0, -2.6F, 9.5F, -2.4F, 1.0F, 2.0F, 1.0F), box(26, 0, -0.8F, 9.5F, -2.4F, 1.0F, 2.0F, 1.0F)),
			part(0, Attach.RIGHT_LEG, "toes_r", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(30, 0, -2.2F, 11.0F, -3.4F, 1.0F, 1.0F, 2.0F), box(36, 0, -0.5F, 11.0F, -3.6F, 1.0F, 1.0F, 2.0F), box(42, 0, 1.2F, 11.0F, -3.4F, 1.0F, 1.0F, 2.0F), box(48, 0, -0.5F, 9.5F, 2.0F, 1.0F, 2.0F, 1.0F)),
			part(0, Attach.HEAD, "horn_l", null, 2.4F, -7.0F, -1.5F, -0.55F, 0.0F, 0.42F, box(52, 0, -1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F)),
			part(0, Attach.HEAD, "horn_tip_l", "horn_l", 0.0F, -6.6F, 0.0F, -0.45F, 0.0F, 0.1F, box(60, 0, -0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F)),
			part(0, Attach.BODY, "wing_l", null, 0.6F, 0.6F, 2.05F, 0.14F, 0.0F, -0.07F, box(0, 12, 0.0F, 0.0F, 0.0F, 4.0F, 11.0F, 1.0F)),
			part(0, Attach.LEFT_ARM, "claw_l", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(10, 12, 1.6F, 9.5F, -2.4F, 1.0F, 2.0F, 1.0F), box(14, 12, -0.2F, 9.5F, -2.4F, 1.0F, 2.0F, 1.0F)),
			part(0, Attach.LEFT_LEG, "toes_l", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(18, 12, -2.2F, 11.0F, -3.4F, 1.0F, 1.0F, 2.0F), box(24, 12, -0.5F, 11.0F, -3.6F, 1.0F, 1.0F, 2.0F), box(30, 12, 1.2F, 11.0F, -3.4F, 1.0F, 1.0F, 2.0F), box(36, 12, -0.5F, 9.5F, 2.0F, 1.0F, 2.0F, 1.0F)),
			part(0, Attach.HEAD, "beak", null, 0.0F, -2.6F, -4.0F, 0.28F, 0.0F, 0.0F, box(40, 12, -1.5F, -1.0F, -3.0F, 3.0F, 2.0F, 3.0F), box(52, 12, -1.0F, 1.0F, -2.4F, 2.0F, 1.0F, 2.0F)),
			part(0, Attach.HEAD, "brow", null, 0.0F, -5.4F, -4.0F, 0.0F, 0.0F, 0.0F, box(0, 24, -4.0F, -0.5F, -0.6F, 8.0F, 1.0F, 1.0F)),
			part(0, Attach.BODY, "ridge", null, 0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F, box(18, 24, -0.5F, 0.8F, 0.0F, 1.0F, 10.0F, 1.0F)),
			part(0, Attach.RIGHT_ARM, "growth", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(22, 24, -3.4F, -3.0F, -1.0F, 3.0F, 2.0F, 3.0F), box(34, 24, -2.8F, -4.4F, 0.2F, 2.0F, 2.0F, 2.0F)),
			part(0, Attach.LEFT_ARM, "spur", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(42, 24, 1.6F, 4.0F, 1.6F, 1.0F, 3.0F, 1.0F)),
			part(1, Attach.HEAD, "crown_r", null, -3.2F, -7.4F, -0.6F, -0.12F, 0.0F, -0.3F, box(0, 0, -1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)),
			part(1, Attach.HEAD, "crown_tip_r", "crown_r", 0.0F, -5.6F, 0.0F, 0.05F, 0.0F, 0.42F, box(8, 0, -0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F)),
			part(1, Attach.HEAD, "crown_back_r", null, -2.0F, -7.6F, 2.4F, -0.35F, 0.0F, -0.18F, box(12, 0, -0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F)),
			part(1, Attach.RIGHT_ARM, "pauldron_r", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(16, 0, -3.5F, -2.7F, -2.5F, 5.0F, 3.0F, 5.0F)),
			part(1, Attach.HEAD, "crown_l", null, 3.2F, -7.4F, -0.6F, -0.12F, 0.0F, 0.3F, box(36, 0, -1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F)),
			part(1, Attach.HEAD, "crown_tip_l", "crown_l", 0.0F, -5.6F, 0.0F, 0.05F, 0.0F, -0.42F, box(44, 0, -0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F)),
			part(1, Attach.HEAD, "crown_back_l", null, 2.0F, -7.6F, 2.4F, -0.35F, 0.0F, 0.18F, box(48, 0, -0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F)),
			part(1, Attach.LEFT_ARM, "pauldron_l", null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, box(0, 8, -1.5F, -2.7F, -2.5F, 5.0F, 3.0F, 5.0F)),
			part(1, Attach.HEAD, "ball", null, 0.0F, -7.6F, -4.0F, 0.0F, 0.0F, 0.0F, box(20, 8, -1.0F, -0.4F, -0.7F, 2.0F, 2.0F, 1.0F)),
			part(1, Attach.BODY, "ridge", null, 0.0F, 0.0F, 2.0F, 0.0F, 0.0F, 0.0F, box(26, 8, -1.0F, 0.5F, 0.0F, 2.0F, 6.0F, 1.0F)),
			part(2, Attach.HEAD, "crown_r", null, -3.3F, -7.4F, -0.4F, -0.1F, 0.0F, -0.22F, box(0, 0, -0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F)),
			part(2, Attach.HEAD, "crown_tip_r", "crown_r", 0.0F, -5.6F, 0.0F, 0.05F, 0.0F, 0.38F, box(4, 0, -0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F)),
			part(2, Attach.HEAD, "crown_back_r", null, -1.8F, -7.6F, 2.6F, -0.3F, 0.0F, -0.15F, box(8, 0, -0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F)),
			part(2, Attach.BODY, "wing_r", null, -0.5F, 0.2F, 2.05F, 0.07F, 0.0F, 0.11F, box(12, 0, -5.0F, 0.0F, 0.0F, 5.0F, 17.0F, 1.0F)),
			part(2, Attach.HEAD, "crown_l", null, 3.3F, -7.4F, -0.4F, -0.1F, 0.0F, 0.22F, box(24, 0, -0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F)),
			part(2, Attach.HEAD, "crown_tip_l", "crown_l", 0.0F, -5.6F, 0.0F, 0.05F, 0.0F, -0.38F, box(28, 0, -0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F)),
			part(2, Attach.HEAD, "crown_back_l", null, 1.8F, -7.6F, 2.6F, -0.3F, 0.0F, 0.15F, box(32, 0, -0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F)),
			part(2, Attach.BODY, "wing_l", null, 0.5F, 0.2F, 2.05F, 0.07F, 0.0F, -0.11F, box(36, 0, 0.0F, 0.0F, 0.0F, 5.0F, 17.0F, 1.0F)),
			part(2, Attach.HEAD, "ball", null, 0.0F, -7.6F, -4.0F, 0.0F, 0.0F, 0.0F, box(48, 0, -1.0F, -0.4F, -0.7F, 2.0F, 2.0F, 1.0F)),
			part(2, Attach.BODY, "stinger", null, 0.0F, 9.5F, 2.0F, 0.5F, 0.0F, 0.0F, box(54, 0, -1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 2.0F), box(0, 18, -0.5F, -0.5F, 1.8F, 1.0F, 1.0F, 2.0F))
	};

	/** Per form, per bone: its parts, baked (null: none on that bone). */
	private static final List<Map<Attach, ModelPart>> BAKED = new ArrayList<>();
	private static final ModelPart[] segment = new ModelPart[2], stinger = new ModelPart[2];
	/** The textures, by palette and form. */
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();

	private BioForms() {
	}

	/** {@code entity} is Bio-built (a player, or anyone carrying the heritage). */
	static boolean bio(Entity entity) {
		return Heritage.Kind.BIOBUILT.key().equals(entity.getAttached(Heritage.HERITAGE));
	}

	static Identifier texture(int palette, String name) {
		return TEXTURES.computeIfAbsent(PALETTES[palette] + "_" + name,
			key -> JaymosKingdoms.id("textures/entity/heritage/bioform/" + key + ".png"));
	}

	/** {@code entity}'s palette: by its variant. */
	static int palette(@Nullable Entity entity) {
		HeritageVariants.Variant variant = entity == null ? null : HeritageVariants.of(entity);
		return variant == HeritageVariants.Variant.BRASSBOUND ? 1 : variant == HeritageVariants.Variant.OPALINE ? 2 : 0;
	}

	/** Render-state extraction (after {@link HeritageLooks#extract}): a Bio-built player's form, and their wear hidden under it. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		if (!(entity instanceof Player) || !bio(entity)) {
			state.setData(KEY, null);
			return;
		}
		state.setData(KEY, new Shape(BioBuilt.form(entity), palette(entity), entity.hasAttached(HeritageHold.ABSORBING)));
		if (state instanceof AvatarRenderState avatar) {
			// (the form is the whole body: a skin's own hat, jacket and sleeves would stand out of it)
			avatar.showHat = false;
			avatar.showJacket = false;
			avatar.showLeftSleeve = false;
			avatar.showRightSleeve = false;
			avatar.showLeftPants = false;
			avatar.showRightPants = false;
		}
	}

	/** Right after a model was posed for this state: the first form hunched, the second bulked. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof HumanoidRenderState living) || !(model instanceof HumanoidModel<?> m)) {
			return;
		}
		Shape shape = living.getData(KEY);
		if (shape == null) {
			return;
		}
		if (shape.form() == 0) {
			m.head.y += HUNCH_HEAD_DOWN;
			m.head.z -= HUNCH_HEAD_FORWARD;
			for (ModelPart arm : new ModelPart[] {m.rightArm, m.leftArm}) {
				arm.z -= HUNCH_ARMS_FORWARD;
				arm.xRot -= HUNCH_ARMS_SWING;
			}
		} else if (shape.form() == 1) {
			m.body.xScale *= BULK;
			m.body.zScale *= BULK;
			for (ModelPart arm : new ModelPart[] {m.rightArm, m.leftArm}) {
				arm.xScale *= BULK;
				arm.zScale *= BULK;
			}
			m.rightArm.x -= BULK_SHOULDER;
			m.leftArm.x += BULK_SHOULDER;
		}
	}

	/**
	 * Draws a Bio-built player's form ({@link HeritageLooks#submit}): the form's skin on {@code marks}, its glow on
	 * {@code glowing}, its parts on the bones, and its tail. False when the state carries no form (not a Bio-built player).
	 */
	static boolean submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, PlayerModel marks,
		PlayerModel glowing, boolean headCovered, boolean bodyCovered) {
		Shape shape = state.getData(KEY);
		if (shape == null) {
			return false;
		}
		int form = shape.form(), palette = shape.palette();
		if (!bodyCovered) {
			collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityCutout(texture(palette, form + "_skin")), light,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(texture(palette, form + "_glow")), light,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
		}
		parts(poseStack, collector, light, marks, form, palette, headCovered, bodyCovered);
		Tail tail = TAILS[form];
		if (tail != null && !shape.absorbing() && !bodyCovered && marks.body.visible) {
			tail(poseStack, collector, light, marks.body, form, palette, tail, state.ageInTicks);
		}
		return true;
	}

	/** The form's parts on {@code model}'s bones (a bud's too). */
	static void parts(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidModel<?> model, int form, int palette, boolean headCovered,
		boolean bodyCovered) {
		Map<Attach, ModelPart> baked = baked(form);
		Identifier skin = texture(palette, form + "_parts"), glow = texture(palette, form + "_parts_glow");
		for (Map.Entry<Attach, ModelPart> entry : baked.entrySet()) {
			Attach attach = entry.getKey();
			ModelPart bone = bone(model, attach);
			if (!bone.visible || (attach == Attach.HEAD ? headCovered : bodyCovered)) {
				continue;
			}
			poseStack.pushPose();
			bone.translateAndRotate(poseStack);
			collector.order(1).submitModelPart(entry.getValue(), poseStack, RenderTypes.entityCutout(skin), light, OverlayTexture.NO_OVERLAY, null, -1);
			collector.order(2).submitModelPart(entry.getValue(), poseStack, RenderTypes.eyes(glow), light, OverlayTexture.NO_OVERLAY, null, -1);
			poseStack.popPose();
		}
	}

	private static ModelPart bone(HumanoidModel<?> model, Attach attach) {
		return switch (attach) {
			case HEAD -> model.head;
			case BODY -> model.body;
			case RIGHT_ARM -> model.rightArm;
			case LEFT_ARM -> model.leftArm;
			case RIGHT_LEG -> model.rightLeg;
			case LEFT_LEG -> model.leftLeg;
		};
	}

	/** The idle tail: a chain of segments off the small of the back, each bent a little more, swaying side to side. */
	private static void tail(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart body, int form, int palette, Tail tail, float age) {
		bakeTails();
		Identifier skin = texture(palette, form + "_parts");
		poseStack.pushPose();
		body.translateAndRotate(poseStack);
		poseStack.translate(TAIL_ROOT[0] / 16.0F, TAIL_ROOT[1] / 16.0F, TAIL_ROOT[2] / 16.0F);
		float thick = tail.thick() / SEGMENT[0];
		for (int i = 0; i < tail.bends().length; i++) {
			// the sway runs down it as a wave, bigger toward the tip
			float sway = tail.sway() * Mth.sin(age * SWAY_SPEED - i * 0.5F) * (0.3F + i / (float)tail.bends().length);
			poseStack.rotate(new org.joml.Quaternionf().rotationZYX(0.0F, sway, tail.bends()[i]));
			poseStack.pushPose();
			poseStack.scale(thick, thick, tail.length() / SEGMENT[2]);
			collector.order(1).submitModelPart(segment[form], poseStack, RenderTypes.entityCutout(skin), light, OverlayTexture.NO_OVERLAY, null, -1);
			poseStack.popPose();
			poseStack.translate(0.0F, 0.0F, tail.length() / 16.0F);
			poseStack.scale(tail.taper(), tail.taper(), tail.taper());
		}
		collector.order(1).submitModelPart(stinger[form], poseStack, RenderTypes.entityCutout(skin), light, OverlayTexture.NO_OVERLAY, null, -1);
		poseStack.popPose();
	}

	private static void bakeTails() {
		if (segment[0] != null) {
			return;
		}
		for (int form = 0; form < 2; form++) {
			Tail tail = TAILS[form];
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("segment", CubeListBuilder.create().texOffs(tail.u(), tail.v())
				.addBox(-SEGMENT[0] / 2.0F, -SEGMENT[1] / 2.0F, 0.0F, SEGMENT[0], SEGMENT[1], SEGMENT[2]), PartPose.ZERO);
			segment[form] = LayerDefinition.create(mesh, 64, 64).bakeRoot();
			MeshDefinition sting = new MeshDefinition();
			CubeListBuilder boxes = CubeListBuilder.create();
			for (int i = 0; i < STING.length; i++) {
				float[] b = STING[i];
				boxes.texOffs(tail.sting()[i][0], tail.sting()[i][1]).addBox(b[3], b[4], b[5], b[0], b[1], b[2]);
			}
			sting.getRoot().addOrReplaceChild("stinger", boxes, PartPose.ZERO);
			stinger[form] = LayerDefinition.create(sting, 64, 64).bakeRoot();
		}
	}

	/** Form {@code form}'s parts, baked the first time: one root per bone, each part under its parent. */
	private static Map<Attach, ModelPart> baked(int form) {
		while (BAKED.size() <= form) {
			BAKED.add(null);
		}
		Map<Attach, ModelPart> baked = BAKED.get(form);
		if (baked != null) {
			return baked;
		}
		baked = new EnumMap<>(Attach.class);
		Map<Attach, MeshDefinition> meshes = new EnumMap<>(Attach.class);
		Map<String, PartDefinition> named = new HashMap<>();
		for (Part part : PARTS) {
			if (part.form() != form) {
				continue;
			}
			PartDefinition parent = part.parent() != null ? named.get(part.parent())
				: meshes.computeIfAbsent(part.attach(), a -> new MeshDefinition()).getRoot();
			CubeListBuilder boxes = CubeListBuilder.create();
			for (Box b : part.boxes()) {
				boxes.texOffs(b.u(), b.v()).addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d());
			}
			named.put(part.name(), parent.addOrReplaceChild(part.name(), boxes,
				PartPose.offsetAndRotation(part.px(), part.py(), part.pz(), part.rx(), part.ry(), part.rz())));
		}
		for (Map.Entry<Attach, MeshDefinition> entry : meshes.entrySet()) {
			baked.put(entry.getKey(), LayerDefinition.create(entry.getValue(), 64, 64).bakeRoot());
		}
		BAKED.set(form, baked);
		return baked;
	}
}
