package com.jaymospowers.client.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.costume.CostumeDesign;
import com.jaymospowers.costume.CostumeKit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * The costume's models, all worn over the player model ({@link Worn}): the body suit (one per cut, wide and slim arms),
 * what covers the head (one per mask), the cape (one per kind) and the emblem's patch on the chest. Each piece is drawn
 * twice, once per colour, from a pair of greyscale mask textures ({@code tools/gen_costume.py}): where the primary colour
 * goes and where the secondary goes, which never overlap.
 */
public final class CostumeModels {
	/**
	 * How far the suit stands off the skin: over its outer layer (0.25), under where armour would be. (0.13.26) And clear
	 * of MCA's clothes, whose outer layer sits at 0.3125 (its clothing body 0.0625 plus the 0.25): at 0.3 the two fought
	 * and the clothes flickered through; still under MCA's hair (0.375), so long hair falls over the suit.
	 */
	static final float SUIT_GROW = 0.35F;
	/**
	 * Masks sit over the skin's hat layer (0.5); the hood looser, the helmet loosest. (0.13.26) And over MCA's clothing
	 * hat layer (0.5625: hoods, caps) and its hair (0.425).
	 */
	static final float MASK_GROW = 0.62F, HOOD_GROW = 0.8F, HELMET_GROW = 1.0F;
	/** The cape hangs this far (pixels) behind where the vanilla cape would, clear of the suit's back. */
	static final float CAPE_GAP = 0.45F;
	/** The cape's length in pixels, per kind (the vanilla cape is 16). */
	static final int SHORT_CAPE = 11, LONG_CAPE = 20, TATTERED_CAPE = 19;
	/** (0.11.78) Wind's gale cloak (its tails reach past the knees) and Darktide's tide cape. */
	static final int GALE_CAPE = 22, TIDE_CAPE = 20;
	/**
	 * (0.14.3) The assassin's robe: its coat tails hang from the waist (pixels) this far down, one pixel thick, standing
	 * this far (pixels) clear of the suit's front and back, and swing out with the legs (this much of the leg's swing);
	 * and how far the bracers, mantle, the peaked hood's brim and its cowl are grown.
	 */
	static final int TAIL_TOP = 9, TAIL_LENGTH = 11;
	static final float TAIL_GAP = 0.05F, TAIL_SWAY = 0.6F;
	static final float BRACER_GROW = 0.15F, MANTLE_GROW = 0.2F, HOOD_BRIM_GROW = 0.4F, COWL_GROW = 0.6F;
	/** (0.17.166) The thief's hood stands off the head as its model has it. */
	static final float THIEF_HOOD_GROW = 1.0F;
	/** The emblem patch on the chest: its size (pixels) and the gap in front of the suit's chest. */
	static final float EMBLEM_SIZE = 6.0F, EMBLEM_TOP = 1.5F, EMBLEM_GAP = 0.05F;
	/**
	 * (0.11.78) The suit's bust over MCA's (VillagerEntityBaseModelMCA.newBreasts: a 6x3x3 box at 18,21, drawn in the
	 * body's space scaled by the bust's size; CommonVillagerModel.renderCommon/applyVillagerDimensions).
	 */
	static final float BUST_X = -3.25F, BUST_Y = -1.25F, BUST_Z = -1.5F, BUST_W = 6.0F, BUST_H = 3.0F, BUST_D = 3.0F;
	static final int BUST_U = 18, BUST_V = 21;
	/** MCA's bust scale for a size {@code b}: (b * 0.2 + 1.05, b * 0.75 + 0.75, b * 0.75 + 0.75); and the size from its z. */
	static final float BUST_SX = 0.2F, BUST_SX0 = 1.05F, BUST_SYZ = 0.75F, BUST_SYZ0 = 0.75F, BUST_Z0 = -1.5F, BUST_Z_PER = 0.25F;

	private CostumeModels() {
	}

	/**
	 * (0.15.123) A model worn over the player the way a costume is, for other features (Wounds' bandages and splints):
	 * {@code layer}'s pieces hang off the player's bones ({@link #bones}, then children of head, body, arms and legs), and
	 * each draw it takes the pose the wearer's model is drawn in just then, other mods' animations, MCA's body and our
	 * own poses included. {@code after} runs last as it animates (show or hide pieces for this player there: the model
	 * is shared by everyone drawn with this renderer). Before submitting it, call {@link #wear}.
	 */
	public static PlayerModel worn(LayerDefinition layer, boolean slim, Supplier<PlayerModel> wearer, BiConsumer<PlayerModel, AvatarRenderState> after) {
		return new Worn(layer.bakeRoot(), slim, wearer, after);
	}

	/** (0.15.123) The player's bones with nothing on them, to hang a {@link #worn} model's pieces off. */
	public static MeshDefinition bareBones(boolean slim) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
		mesh.getRoot().clearRecursively();
		return mesh;
	}

	/** (0.15.123) Before a {@link #worn} model is submitted: which model this player is drawn with (MCA swaps it per player). */
	public static void wear(AvatarRenderState state, PlayerModel parent) {
		state.setData(CostumeBody.PARENT, parent);
	}

	/** (0.15.123) Whether {@code parent} draws its arms slim (MCA's models say so themselves). */
	public static boolean slim(PlayerModel parent, AvatarRenderState state) {
		return CostumeBody.slim(parent, state.skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM);
	}

	/**
	 * A model worn over the player: each frame (as it's drawn) it re-runs the wearer's model and copies its head, body and
	 * limbs, so whatever animates the player (other mods included) moves it too; our own poses (crawl, flight, combos...)
	 * are then laid on it like on any player model, by the model-feature mixins. A cape, if it has one, swings like the
	 * vanilla cape from the render state's cape motion.
	 */
	static class Worn extends PlayerModel implements com.jaymospowers.client.PosedCopy.Holder {
		private final Supplier<PlayerModel> wearer;
		private final ModelPart cape;
		/** (0.11.78) The suit's bust (over MCA's) and the emblem's patch, when this model has them. */
		private final @Nullable ModelPart bust, emblem;
		/** (0.14.3) The assassin's coat tails, when this model has them. */
		private final @Nullable ModelPart tailFront, tailBack;
		/** (0.15.34) Whether it's being drawn over an MCA body just now (set as it animates, read just before it's drawn). */
		private boolean mcaBody;
		/** (0.15.123) For a model worn by another feature ({@link #worn}): run last as it animates, with the wearer's state. */
		private final @Nullable BiConsumer<PlayerModel, AvatarRenderState> after;

		Worn(ModelPart root, boolean slim, Supplier<PlayerModel> wearer) {
			this(root, slim, wearer, null);
		}

		Worn(ModelPart root, boolean slim, Supplier<PlayerModel> wearer, @Nullable BiConsumer<PlayerModel, AvatarRenderState> after) {
			super(root, slim);
			this.wearer = wearer;
			this.after = after;
			this.cape = this.body.hasChild("cape") ? this.body.getChild("cape") : null;
			this.bust = this.body.hasChild(CostumeBody.BUST) ? this.body.getChild(CostumeBody.BUST) : null;
			this.emblem = this.body.hasChild("emblem") ? this.body.getChild("emblem") : null;
			this.tailFront = this.body.hasChild("tail_front") ? this.body.getChild("tail_front") : null;
			this.tailBack = this.body.hasChild("tail_back") ? this.body.getChild("tail_back") : null;
			hideSkinLayers();
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			resetPose();
			// (0.11.78) the model this player was drawn with (MCA swaps it per player), animated for them
			PlayerModel kept = state.getData(CostumeBody.PARENT);
			PlayerModel parent = kept != null ? kept : this.wearer.get();
			parent.setupAnim(state);
			ModelPart[] from = parts(parent);
			ModelPart[] to = parts(this);
			for (int i = 0; i < to.length; i++) {
				to[i].loadPose(from[i].storePose());
				to[i].xScale = from[i].xScale;
				to[i].yScale = from[i].yScale;
				to[i].zScale = from[i].zScale;
				to[i].visible = from[i].visible;
			}
			// (0.11.78) the body as Fresh Animations moved it, when it did (CostumeBody)
			PartPose[] body = state.getData(CostumeBody.BODY);
			if (body != null) {
				for (int i = 0; i < to.length; i++) {
					to[i].loadPose(body[i]);
				}
			}
			// (0.15.34) on an MCA body, the very pose MCA's own layers draw it in (and, like them, no root: beforeDraw)
			this.mcaBody = CostumeBody.mcaPose(state, this);
			// (0.11.78) MCA draws the head at the villager's head size (scaled from the model's origin)
			float head = CostumeBody.headScale(parent);
			if (head != 1.0F) {
				this.head.x *= head;
				this.head.y *= head;
				this.head.z *= head;
				this.head.xScale *= head;
				this.head.yScale *= head;
				this.head.zScale *= head;
			}
			bust(parent);
			hideSkinLayers();
			if (this.tailFront != null && this.tailBack != null) {
				// (0.14.3) the coat tails swing out ahead of a leg going forward and behind one going back
				float forward = Math.min(0.0F, Math.min(this.rightLeg.xRot, this.leftLeg.xRot));
				float back = Math.max(0.0F, Math.max(this.rightLeg.xRot, this.leftLeg.xRot));
				this.tailFront.xRot = forward * TAIL_SWAY;
				// (under a cape the back tail hangs still, or it would swing through the cape)
				CostumeDesign worn = state.getData(CostumeClient.KEY);
				this.tailBack.xRot = worn != null && worn.cape() != CostumeDesign.Cape.NONE ? 0.0F : back * TAIL_SWAY;
			}
			if (this.cape != null) {
				// as the vanilla cape (PlayerCapeModel)
				this.cape.rotateBy(new Quaternionf()
					.rotateY((float)-Math.PI)
					.rotateX((6.0F + state.capeLean / 2.0F + state.capeFlap) * (float)(Math.PI / 180.0))
					.rotateZ(state.capeLean2 / 2.0F * (float)(Math.PI / 180.0))
					.rotateY((180.0F - state.capeLean2 / 2.0F) * (float)(Math.PI / 180.0)));
			}
			if (this.after != null) {
				this.after.accept(this, state);
			}
		}

		/**
		 * (0.11.78) Over MCA's bust: the same box in the same place. MCA draws it in the body's space scaled by
		 * {@code S}, then at its own pose; S scales y and z alike, so it turns with the bust's x rotation and the same
		 * is had as a child of the body at pose {@code S * pos}, scale {@code S}. The emblem moves out in front of it.
		 */
		private void bust(PlayerModel parent) {
			if (this.bust == null && this.emblem == null) {
				return;
			}
			ModelPart theirs = CostumeBody.bust(parent);
			// the bust's size from where MCA put it (z = -1.5 + size / 4), times the villager's bust dimension
			float b = theirs != null ? (theirs.z - BUST_Z0) / BUST_Z_PER * CostumeBody.bustScale(parent) : 0.0F;
			boolean shown = theirs != null && theirs.visible && parent.body.visible && b > 0.01F;
			if (this.bust != null) {
				this.bust.visible = shown;
			}
			if (!shown) {
				return;
			}
			float sx = b * BUST_SX + BUST_SX0, syz = b * BUST_SYZ + BUST_SYZ0;
			if (this.bust != null) {
				this.bust.setPos(theirs.x * sx, theirs.y * syz, theirs.z * syz);
				this.bust.setRotation(theirs.xRot, theirs.yRot, theirs.zRot);
				this.bust.xScale = sx;
				this.bust.yScale = syz;
				this.bust.zScale = syz;
			}
			if (this.emblem != null) {
				// the bust's front-most point (its box, grown like the suit, turned by its x rotation)
				float sin = (float)Math.sin(theirs.xRot), cos = (float)Math.cos(theirs.xRot);
				float front = Float.MAX_VALUE;
				for (float y : new float[] {BUST_Y - SUIT_GROW, BUST_Y + BUST_H + SUIT_GROW}) {
					for (float z : new float[] {BUST_Z - SUIT_GROW, BUST_Z + BUST_D + SUIT_GROW}) {
						front = Math.min(front, y * sin + z * cos);
					}
				}
				front = syz * (theirs.z + front);
				this.emblem.z = Math.min(0.0F, front + 2.0F + SUIT_GROW);
			}
		}

		/**
		 * (0.15.34) Just before it's drawn, after every pose (CostumeCover): over an MCA body the root's pose goes, as MCA
		 * draws its body's parts each on its own and never the root (CommonVillagerModel.renderCommon), so a pose that
		 * moves the root (Gravity's arm lean) would otherwise carry the costume off the body.
		 */
		/** (0.15.44) Over an MCA body it wears the posed body's copy: our poses aren't laid on it again. */
		@Override
		public boolean carriesPose() {
			return this.mcaBody;
		}

		void beforeDraw() {
			if (this.mcaBody) {
				root().loadPose(PartPose.ZERO);
			}
		}

		/** The skin's outer layers have no part in a costume. */
		private void hideSkinLayers() {
			this.hat.visible = false;
			this.jacket.visible = false;
			this.leftSleeve.visible = false;
			this.rightSleeve.visible = false;
			this.leftPants.visible = false;
			this.rightPants.visible = false;
		}

		static ModelPart[] parts(PlayerModel model) {
			return new ModelPart[] {model.root(), model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		}
	}

	/** One renderer's set of costume models (they follow that renderer's player model). */
	static final class Set {
		private final Map<CostumeDesign.Cut, Worn> wide = new EnumMap<>(CostumeDesign.Cut.class);
		private final Map<CostumeDesign.Cut, Worn> slim = new EnumMap<>(CostumeDesign.Cut.class);
		private final Map<CostumeDesign.Mask, Worn> masks = new EnumMap<>(CostumeDesign.Mask.class);
		private final Map<CostumeDesign.Cape, Worn> capes = new EnumMap<>(CostumeDesign.Cape.class);
		final Worn emblem;
		/** (0.17.60) The suit editor's pieces, by {@link CostumeModels#kitKey} (gloves also by arm width). */
		private final Map<String, Worn> kit = new HashMap<>();

		Set(Supplier<PlayerModel> wearer) {
			for (CostumeDesign.Cut cut : CostumeDesign.Cut.values()) {
				this.wide.put(cut, new Worn(CostumeModels.suit(cut, false).bakeRoot(), false, wearer));
				this.slim.put(cut, new Worn(CostumeModels.suit(cut, true).bakeRoot(), true, wearer));
			}
			for (CostumeDesign.Mask mask : CostumeDesign.Mask.values()) {
				if (mask != CostumeDesign.Mask.NONE) {
					this.masks.put(mask, new Worn(CostumeModels.mask(mask).bakeRoot(), false, wearer));
				}
			}
			for (CostumeDesign.Cape cape : CostumeDesign.Cape.values()) {
				if (cape != CostumeDesign.Cape.NONE) {
					this.capes.put(cape, new Worn(CostumeModels.cape(cape).bakeRoot(), false, wearer));
				}
			}
			this.emblem = new Worn(CostumeModels.emblem().bakeRoot(), false, wearer);
			for (CostumeKit.Gloves gloves : CostumeKit.Gloves.values()) {
				if (gloves != CostumeKit.Gloves.NONE) {
					this.kit.put(kitKey(gloves, false), new Worn(CostumeModels.gloves(gloves, false).bakeRoot(), false, wearer));
					this.kit.put(kitKey(gloves, true), new Worn(CostumeModels.gloves(gloves, true).bakeRoot(), true, wearer));
				}
			}
			for (CostumeKit.Boots boots : CostumeKit.Boots.values()) {
				if (boots != CostumeKit.Boots.NONE) {
					this.kit.put(kitKey(boots), new Worn(CostumeModels.boots(boots).bakeRoot(), false, wearer));
				}
			}
			for (CostumeKit.Belt belt : CostumeKit.Belt.values()) {
				if (belt != CostumeKit.Belt.NONE) {
					this.kit.put(kitKey(belt), new Worn(CostumeModels.belt(belt).bakeRoot(), false, wearer));
				}
			}
			for (CostumeKit.Helm helm : CostumeKit.Helm.values()) {
				if (helm != CostumeKit.Helm.NONE) {
					this.kit.put(kitKey(helm), new Worn(CostumeModels.helm(helm).bakeRoot(), false, wearer));
				}
			}
			for (CostumeKit.Fins fins : CostumeKit.Fins.values()) {
				if (fins != CostumeKit.Fins.NONE) {
					this.kit.put(kitKey(fins), new Worn(CostumeModels.fins(fins).bakeRoot(), false, wearer));
				}
			}
		}

		/** (0.17.60) A kit piece's model, by its {@link CostumeModels#kitKey}. */
		Worn kit(String key) {
			return this.kit.get(key);
		}

		Worn suit(CostumeDesign.Cut cut, boolean slim) {
			return (slim ? this.slim : this.wide).get(cut);
		}

		Worn mask(CostumeDesign.Mask mask) {
			return this.masks.get(mask);
		}

		Worn cape(CostumeDesign.Cape cape) {
			return this.capes.get(cape);
		}
	}

	/** The body suit: the player's own boxes, grown a little; the armoured cut (and Gravity's dense one) adds shoulder pads. */
	static LayerDefinition suit(CostumeDesign.Cut cut, boolean slim) {
		MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(SUIT_GROW), slim);
		if (cut == CostumeDesign.Cut.ARMOURED || cut == CostumeDesign.Cut.DENSE || cut == CostumeDesign.Cut.BATTLE_ARMOUR) {
			PartDefinition root = mesh.getRoot();
			// pads over the shoulders, textured from the (unused) hat region
			root.getChild("right_arm").addOrReplaceChild("pad", CubeListBuilder.create().texOffs(32, 0)
				.addBox(slim ? -3.0F : -3.5F, -2.8F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
			root.getChild("left_arm").addOrReplaceChild("pad", CubeListBuilder.create().texOffs(32, 0).mirror()
				.addBox(slim ? -2.0F : -1.5F, -2.8F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
		}
		if (cut == CostumeDesign.Cut.ASSASSIN) {
			assassin(mesh.getRoot(), slim);
		}
		if (cut == CostumeDesign.Cut.THIEF) {
			return thief(slim);
		}
		// (0.11.78) a bust over MCA's, shown and placed only when the body has one (Worn.bust); textured from the chest
		mesh.getRoot().getChild("body").addOrReplaceChild(CostumeBody.BUST, CubeListBuilder.create().texOffs(BUST_U, BUST_V)
			.addBox(BUST_X, BUST_Y, BUST_Z, BUST_W, BUST_H, BUST_D, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/**
	 * (0.14.3) The assassin's robe over the suit: a layered shoulder mantle (the armoured cut's pad and a lower layer,
	 * textured from the unused hat region), leather bracers on the forearms (the sleeve regions), and long coat tails
	 * hanging from the waist in front and behind (the jacket and pants regions). Painted by tools/gen_costume.py.
	 */
	private static void assassin(PartDefinition root, boolean slim) {
		for (boolean left : new boolean[] {false, true}) {
			PartDefinition arm = root.getChild(left ? "left_arm" : "right_arm");
			float x = left ? (slim ? -2.0F : -1.5F) : (slim ? -3.0F : -3.5F);
			arm.addOrReplaceChild("pad", CubeListBuilder.create().texOffs(32, 0)
				.addBox(x, -2.8F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
			arm.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(32, 8)
				.addBox(x, -0.8F, -2.5F, 5.0F, 2.0F, 5.0F, new CubeDeformation(SUIT_GROW + MANTLE_GROW)), PartPose.ZERO);
			float bx = left ? -1.0F : (slim ? -2.0F : -3.0F);
			arm.addOrReplaceChild("bracer", CubeListBuilder.create().texOffs(left ? 48 : 40, left ? 48 : 32)
				.addBox(bx, 5.0F, -2.0F, slim ? 3.0F : 4.0F, 4.0F, 4.0F, new CubeDeformation(SUIT_GROW + BRACER_GROW)), PartPose.ZERO);
		}
		PartDefinition body = root.getChild("body");
		float front = -2.0F - SUIT_GROW - TAIL_GAP - 1.0F, back = 2.0F + SUIT_GROW + TAIL_GAP;
		// hinged on their own top edge at the waist, so they swing clear of the robe (Worn.setupAnim)
		body.addOrReplaceChild("tail_front", CubeListBuilder.create().texOffs(0, 32)
			.addBox(-4.0F, 0.0F, -1.0F, 8.0F, TAIL_LENGTH, 1.0F), PartPose.offset(0.0F, TAIL_TOP, front + 1.0F));
		body.addOrReplaceChild("tail_back", CubeListBuilder.create().texOffs(20, 32)
			.addBox(-4.0F, 0.0F, 0.0F, 8.0F, TAIL_LENGTH, 1.0F), PartPose.offset(0.0F, TAIL_TOP, back));
	}

	/**
	 * (0.17.166) The thief cut: eanncrod's Thief Armor (Blockbench Workshop, CC BY 4.0) in place of the suit, its own
	 * cubes on its own 128-square sheet ({@code tools/blockbench/thief_costume.py}): a padded tunic and belt, sloped
	 * leather straps over the shoulders, a short cloak at the back, pauldrons and bracers, wrapped legs and boots. Slim
	 * arms hang half a pixel lower, so their pieces do too.
	 */
	private static LayerDefinition thief(boolean slim) {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
		PartDefinition root = mesh.getRoot();
		root.clearRecursively();
		int i = 0;
		for (ThiefCube c : THIEF_CUBES) {
			PartDefinition part = root.getChild(c.part);
			float lower = slim && c.part.endsWith("_arm") ? -0.5F : 0.0F;
			CubeListBuilder cube = CubeListBuilder.create().texOffs(c.u, c.v).mirror(c.mirror);
			if (c.xRot == 0.0F && c.yRot == 0.0F && c.zRot == 0.0F) {
				part.addOrReplaceChild("thief_" + i++, cube.addBox(c.x, c.y + lower, c.z, c.w, c.h, c.d, new CubeDeformation(c.grow)), PartPose.ZERO);
			} else {
				// turned about its own pivot
				part.addOrReplaceChild("thief_" + i++, cube.addBox(c.x - c.px, c.y - c.py, c.z - c.pz, c.w, c.h, c.d, new CubeDeformation(c.grow)),
					PartPose.offsetAndRotation(c.px, c.py + lower, c.pz, c.xRot * DEG, c.yRot * DEG, c.zRot * DEG));
			}
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	private static final float DEG = (float)(Math.PI / 180.0);

	/** One of the Thief Armor's cubes, in our player model's space: on which part, its texture corner, box and grow, the
	 * pivot it's turned about and how far (degrees). */
	private record ThiefCube(String part, int u, int v, float x, float y, float z, float w, float h, float d, float grow,
		float px, float py, float pz, float xRot, float yRot, float zRot, boolean mirror) {
	}

	private static final ThiefCube[] THIEF_CUBES = {
		new ThiefCube("body", 32, 17, -4F, 1F, -2F, 8F, 9F, 4F, 0.6F, 0F, 24F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("body", 52, 13, -4F, 9F, -2F, 8F, 3F, 4F, 0.7F, 0F, 24F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("body", 14, 58, -7F, -2F, -2F, 3F, 9F, 4F, 0.65F, 0F, 0F, 0F, 0F, 0F, -42.5F, false),
		new ThiefCube("body", 14, 58, 4F, -2F, -2F, 3F, 9F, 4F, 0.68F, 0F, 0F, 0F, 0F, 0F, 45F, false),
		new ThiefCube("body", 32, 0, -5F, 1F, 2F, 10F, 16F, 1F, 0.5F, 0F, 0F, 0F, 5F, 0F, 0F, false),
		new ThiefCube("right_arm", 60, 37, -4F, -1F, -2F, 4F, 5F, 4F, 0.7F, 1F, 0F, 0F, 0F, 0F, 27.5F, false),
		new ThiefCube("right_arm", 16, 34, -3F, 5F, -2F, 2F, 3F, 4F, 0.7F, 5F, 22F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("right_arm", 32, 46, -3F, -2F, -2F, 4F, 12F, 4F, 0.51F, 5F, 22F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("left_arm", 44, 58, 0F, -1F, -2F, 4F, 5F, 4F, 0.7F, -1F, 0F, 0F, 0F, 0F, -27.5F, false),
		new ThiefCube("left_arm", 60, 54, 1F, 5F, -2F, 2F, 3F, 4F, 0.7F, -5F, 22F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("left_arm", 48, 42, -1F, -2F, -2F, 4F, 12F, 4F, 0.51F, -5F, 22F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("right_leg", 16, 49, -2.1F, 8F, -2F, 4F, 4F, 4F, 0.51F, 1.9F, 12F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("right_leg", 54, 0, -2.1F, 1F, -2F, 4F, 7F, 4F, 0.8F, 1.9F, 12F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("left_leg", 0, 42, -1.9F, 8F, -2F, 4F, 4F, 4F, 0.51F, -1.9F, 12F, 0F, 0F, 0F, 0F, false),
		new ThiefCube("left_leg", 52, 26, -1.9F, 1F, -2F, 4F, 7F, 4F, 0.8F, -1.9F, 12F, 0F, 0F, 0F, 0F, false)
	};

	/** The bones only, for pieces that hang off one of them. */
	private static MeshDefinition bones() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		mesh.getRoot().clearRecursively();
		return mesh;
	}

	/** What covers the head: a box over it, grown to sit over the hat layer; the helmet has a crest. */
	static LayerDefinition mask(CostumeDesign.Mask mask) {
		MeshDefinition mesh = bones();
		PartDefinition head = mesh.getRoot().getChild("head");
		float grow = switch (mask) {
			case HOOD, ASH_VEIL, PEAKED_HOOD -> HOOD_GROW;
			case HELMET -> HELMET_GROW;
			default -> MASK_GROW;
		};
		head.addOrReplaceChild("mask", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(grow)),
			PartPose.ZERO);
		if (mask == CostumeDesign.Mask.HELMET) {
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -11.0F, -4.5F, 1.0F, 2.0F, 9.0F), PartPose.ZERO);
		}
		if (mask == CostumeDesign.Mask.THIEF_HOOD) {
			// (0.17.166) the Thief Armor's hood: deeper than the head, falling past the jaw to the neck
			MeshDefinition hood = bones();
			hood.getRoot().getChild("head").addOrReplaceChild("mask", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4.0F, -8.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(THIEF_HOOD_GROW)), PartPose.ZERO);
			return LayerDefinition.create(hood, 64, 32);
		}
		if (mask == CostumeDesign.Mask.PEAKED_HOOD) {
			// (0.14.3) the peak: a brim standing out over the brow, shading the upper face; and a cowl falling onto the shoulders
			head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 16)
				.addBox(-4.0F, -8.0F, -7.0F, 8.0F, 2.0F, 3.0F, new CubeDeformation(HOOD_BRIM_GROW)), PartPose.ZERO);
			head.addOrReplaceChild("cowl", CubeListBuilder.create().texOffs(24, 16)
				.addBox(-4.0F, 0.0F, -3.0F, 8.0F, 2.0F, 6.0F, new CubeDeformation(COWL_GROW)), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** The cape: like the vanilla one (outside on its north face, lining on its south), a little further back. */
	static LayerDefinition cape(CostumeDesign.Cape cape) {
		MeshDefinition mesh = bones();
		int length = switch (cape) {
			case SHORT -> SHORT_CAPE;
			case TATTERED -> TATTERED_CAPE;
			case GALE -> GALE_CAPE;
			case TIDE -> TIDE_CAPE;
			default -> LONG_CAPE;
		};
		mesh.getRoot().getChild("body").addOrReplaceChild("cape",
			CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, -1.0F, 10.0F, length, 1.0F),
			PartPose.offsetAndRotation(0.0F, 0.0F, 2.0F + CAPE_GAP, 0.0F, (float)Math.PI, 0.0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	/**
	 * The emblem's patch: a flat square in front of the chest. Its front face takes the whole texture (the texture size
	 * is the face's own size), which is the design's 16x16 emblem ({@link CostumeEmblems}).
	 */
	static LayerDefinition emblem() {
		MeshDefinition mesh = bones();
		float z = -2.0F - SUIT_GROW - EMBLEM_GAP;
		mesh.getRoot().getChild("body").addOrReplaceChild("emblem", CubeListBuilder.create().texOffs(0, 0)
			.addBox(-EMBLEM_SIZE / 2.0F, EMBLEM_TOP, z, EMBLEM_SIZE, EMBLEM_SIZE, 0.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, (int)EMBLEM_SIZE, (int)EMBLEM_SIZE);
	}

	// ---- (0.17.60) the suit editor's pieces (tools/gen_costume.py paints them; the boxes here must agree) ---------------

	/**
	 * How far each piece stands off the suit, on top of {@link #SUIT_GROW}: a wrap or glove a little, a gauntlet or greave
	 * more, the belt between; the helm sits over every mask (the helmet's 1.0) and the fins stand on the head's sides.
	 */
	static final float KIT_GROW = 0.08F, KIT_PLATE_GROW = 0.16F, KIT_BELT_GROW = 0.12F, HELM_GROW = 1.1F;

	/** The model key of a piece: {@code gloves_wraps}, {@code gloves_wraps_slim}, {@code fins_swept}... */
	static String kitKey(CostumeKit.Gloves gloves, boolean slim) {
		return "gloves_" + gloves.getSerializedName() + (slim ? "_slim" : "");
	}

	static String kitKey(CostumeKit.Boots boots) {
		return "boots_" + boots.getSerializedName();
	}

	static String kitKey(CostumeKit.Belt belt) {
		return "belt_" + belt.getSerializedName();
	}

	static String kitKey(CostumeKit.Helm helm) {
		return "helm_" + helm.getSerializedName();
	}

	static String kitKey(CostumeKit.Fins fins) {
		return "fins_" + fins.getSerializedName();
	}

	/**
	 * Which colours a piece is painted in, as bits: 1 primary, 2 secondary, 4 trim (a texture exists only for those; the
	 * generator prints this table). {@code key} is a {@link #kitKey} without any {@code _slim}.
	 */
	static int channels(String key) {
		return switch (key) {
			case "gloves_gauntlets", "belt_champion" -> 7;
			case "helm_circlet" -> 3;
			case "fins_swept" -> 5;
			default -> 6;
		};
	}

	/** A piece's texture in one colour: {@code colour} is primary, secondary or trim. */
	static Identifier kitTexture(String key, String colour) {
		return TEXTURES.computeIfAbsent("textures/entity/costume/kit/" + key + "_" + colour + ".png", JaymosPowers::id);
	}

	/** Gloves: a box round the forearm and hand of each arm. */
	static LayerDefinition gloves(CostumeKit.Gloves gloves, boolean slim) {
		MeshDefinition mesh = bones();
		int h = switch (gloves) {
			case WRAPS -> 2;
			case GLOVES -> 3;
			default -> 5;
		};
		float y = 10.0F - h - (gloves == CostumeKit.Gloves.WRAPS ? 2.0F : 0.0F);
		CubeDeformation grow = new CubeDeformation(SUIT_GROW + (gloves == CostumeKit.Gloves.GAUNTLETS ? KIT_PLATE_GROW : KIT_GROW));
		float aw = slim ? 3.0F : 4.0F;
		mesh.getRoot().getChild("right_arm").addOrReplaceChild("kit", CubeListBuilder.create().texOffs(0, 0)
			.addBox(slim ? -2.0F : -3.0F, y, -2.0F, aw, h, 4.0F, grow), PartPose.ZERO);
		mesh.getRoot().getChild("left_arm").addOrReplaceChild("kit", CubeListBuilder.create().texOffs(24, 0)
			.addBox(-1.0F, y, -2.0F, aw, h, 4.0F, grow), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** Boots: a box round the foot and shin of each leg. */
	static LayerDefinition boots(CostumeKit.Boots boots) {
		MeshDefinition mesh = bones();
		int h = boots == CostumeKit.Boots.GREAVES ? 7 : 3;
		float y = switch (boots) {
			case WRAPPED -> 8.0F;
			case BOOTS -> 9.0F;
			default -> 5.0F;
		};
		CubeDeformation grow = new CubeDeformation(SUIT_GROW + (boots == CostumeKit.Boots.GREAVES ? KIT_PLATE_GROW : KIT_GROW));
		mesh.getRoot().getChild("right_leg").addOrReplaceChild("kit", CubeListBuilder.create().texOffs(0, 0)
			.addBox(-2.0F, y, -2.0F, 4.0F, h, 4.0F, grow), PartPose.ZERO);
		mesh.getRoot().getChild("left_leg").addOrReplaceChild("kit", CubeListBuilder.create().texOffs(24, 0)
			.addBox(-2.0F, y, -2.0F, 4.0F, h, 4.0F, grow), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** A belt: a band round the waist, with the sash's hanging ends, the buckle or the champion's plate on the front. */
	static LayerDefinition belt(CostumeKit.Belt belt) {
		MeshDefinition mesh = bones();
		PartDefinition body = mesh.getRoot().getChild("body");
		boolean champion = belt == CostumeKit.Belt.CHAMPION;
		body.addOrReplaceChild("kit", CubeListBuilder.create().texOffs(0, 0)
			.addBox(-4.0F, champion ? 7.0F : 8.0F, -2.0F, 8.0F, champion ? 4 : 2, 4.0F, new CubeDeformation(SUIT_GROW + KIT_BELT_GROW)), PartPose.ZERO);
		// the band's front is at -2 - (SUIT_GROW + KIT_BELT_GROW); what hangs or stands out starts there
		float front = -2.0F - SUIT_GROW - KIT_BELT_GROW;
		switch (belt) {
			case SASH -> body.addOrReplaceChild("kit_ends", CubeListBuilder.create().texOffs(0, 8)
				.addBox(1.0F, 10.0F, front - 1.0F, 2.0F, 3.0F, 1.0F), PartPose.ZERO);
			case BELT -> body.addOrReplaceChild("kit_buckle", CubeListBuilder.create().texOffs(0, 8)
				.addBox(-1.0F, 8.0F, front - 0.5F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
			case CHAMPION -> body.addOrReplaceChild("kit_plate", CubeListBuilder.create().texOffs(0, 10)
				.addBox(-2.0F, 7.0F, front - 0.5F, 4.0F, 4.0F, 1.0F), PartPose.ZERO);
			default -> {
			}
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** A helmet piece: a circlet round the brow, a battle helm with cheek guards, or a visor band over the eyes. */
	static LayerDefinition helm(CostumeKit.Helm helm) {
		MeshDefinition mesh = bones();
		PartDefinition head = mesh.getRoot().getChild("head");
		CubeDeformation grow = new CubeDeformation(HELM_GROW);
		switch (helm) {
			case CIRCLET -> {
				head.addOrReplaceChild("kit", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -7.0F, -4.0F, 8.0F, 2.0F, 8.0F, grow), PartPose.ZERO);
				head.addOrReplaceChild("kit_jewel", CubeListBuilder.create().texOffs(0, 12).addBox(-1.0F, -7.0F, -4.0F - HELM_GROW - 1.0F, 2.0F, 2.0F, 1.0F),
					PartPose.ZERO);
			}
			case BATTLE_HELM -> {
				head.addOrReplaceChild("kit", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 5.0F, 8.0F, grow), PartPose.ZERO);
				head.addOrReplaceChild("kit_cheek_r", CubeListBuilder.create().texOffs(32, 0).addBox(-5.3F, -4.0F, -3.0F, 1.0F, 4.0F, 5.0F), PartPose.ZERO);
				head.addOrReplaceChild("kit_cheek_l", CubeListBuilder.create().texOffs(46, 0).addBox(4.3F, -4.0F, -3.0F, 1.0F, 4.0F, 5.0F), PartPose.ZERO);
			}
			case VISOR -> head.addOrReplaceChild("kit", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -5.0F, -4.0F, 8.0F, 2.0F, 8.0F, grow),
				PartPose.ZERO);
			default -> {
			}
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	/** Head fins: plates at the temples, swept back, or a crest along the top. */
	static LayerDefinition fins(CostumeKit.Fins fins) {
		MeshDefinition mesh = bones();
		PartDefinition head = mesh.getRoot().getChild("head");
		switch (fins) {
			case SMALL -> {
				head.addOrReplaceChild("kit_r", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -6.0F, 0.0F, 3.0F, 4.0F, 1.0F), PartPose.ZERO);
				head.addOrReplaceChild("kit_l", CubeListBuilder.create().texOffs(10, 0).addBox(4.0F, -6.0F, 0.0F, 3.0F, 4.0F, 1.0F), PartPose.ZERO);
			}
			case SWEPT -> {
				head.addOrReplaceChild("kit_r", CubeListBuilder.create().texOffs(0, 0).addBox(-5.5F, -6.0F, 1.0F, 1.0F, 3.0F, 7.0F), PartPose.ZERO);
				head.addOrReplaceChild("kit_l", CubeListBuilder.create().texOffs(16, 0).addBox(4.5F, -6.0F, 1.0F, 1.0F, 3.0F, 7.0F), PartPose.ZERO);
			}
			case CREST -> head.addOrReplaceChild("kit", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -12.5F, -4.0F, 1.0F, 4.0F, 8.0F), PartPose.ZERO);
			default -> {
			}
		}
		return LayerDefinition.create(mesh, 64, 32);
	}

	private static final Map<String, Identifier> TEXTURES = new HashMap<>();

	/** A piece's mask texture: {@code kind} suit, mask or cape; {@code secondary} for the secondary colour's. */
	static Identifier texture(String kind, String name, boolean slim, boolean secondary) {
		String path = "textures/entity/costume/" + kind + "/" + name.toLowerCase(Locale.ROOT) + (slim ? "_slim" : "")
			+ (secondary ? "_secondary" : "_primary") + ".png";
		return TEXTURES.computeIfAbsent(path, JaymosPowers::id);
	}
}
