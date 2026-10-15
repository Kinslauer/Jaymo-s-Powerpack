package com.jaymospowers.client.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.costume.CostumeDesign;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
final class CostumeModels {
	/** How far the suit stands off the skin: over its outer layer (0.25), under where armour would be. */
	static final float SUIT_GROW = 0.3F;
	/** Masks sit over the skin's hat layer (0.5); the hood looser, the helmet loosest. */
	static final float MASK_GROW = 0.55F, HOOD_GROW = 0.8F, HELMET_GROW = 1.0F;
	/** The cape hangs this far (pixels) behind where the vanilla cape would, clear of the suit's back. */
	static final float CAPE_GAP = 0.45F;
	/** The cape's length in pixels, per kind (the vanilla cape is 16). */
	static final int SHORT_CAPE = 11, LONG_CAPE = 20, TATTERED_CAPE = 19;
	/** (0.11.78) Wind's gale cloak (its tails reach past the knees) and Darktide's tide cape. */
	static final int GALE_CAPE = 22, TIDE_CAPE = 20;
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
	 * A model worn over the player: each frame (as it's drawn) it re-runs the wearer's model and copies its head, body and
	 * limbs, so whatever animates the player (other mods included) moves it too; our own poses (crawl, flight, combos...)
	 * are then laid on it like on any player model, by the model-feature mixins. A cape, if it has one, swings like the
	 * vanilla cape from the render state's cape motion.
	 */
	static class Worn extends PlayerModel {
		private final Supplier<PlayerModel> wearer;
		private final ModelPart cape;
		/** (0.11.78) The suit's bust (over MCA's) and the emblem's patch, when this model has them. */
		private final @Nullable ModelPart bust, emblem;

		Worn(ModelPart root, boolean slim, Supplier<PlayerModel> wearer) {
			super(root, slim);
			this.wearer = wearer;
			this.cape = this.body.hasChild("cape") ? this.body.getChild("cape") : null;
			this.bust = this.body.hasChild(CostumeBody.BUST) ? this.body.getChild(CostumeBody.BUST) : null;
			this.emblem = this.body.hasChild("emblem") ? this.body.getChild("emblem") : null;
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
			if (this.cape != null) {
				// as the vanilla cape (PlayerCapeModel)
				this.cape.rotateBy(new Quaternionf()
					.rotateY((float)-Math.PI)
					.rotateX((6.0F + state.capeLean / 2.0F + state.capeFlap) * (float)(Math.PI / 180.0))
					.rotateZ(state.capeLean2 / 2.0F * (float)(Math.PI / 180.0))
					.rotateY((180.0F - state.capeLean2 / 2.0F) * (float)(Math.PI / 180.0)));
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
		if (cut == CostumeDesign.Cut.ARMOURED || cut == CostumeDesign.Cut.DENSE) {
			PartDefinition root = mesh.getRoot();
			// pads over the shoulders, textured from the (unused) hat region
			root.getChild("right_arm").addOrReplaceChild("pad", CubeListBuilder.create().texOffs(32, 0)
				.addBox(slim ? -3.0F : -3.5F, -2.8F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
			root.getChild("left_arm").addOrReplaceChild("pad", CubeListBuilder.create().texOffs(32, 0).mirror()
				.addBox(slim ? -2.0F : -1.5F, -2.8F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
		}
		// (0.11.78) a bust over MCA's, shown and placed only when the body has one (Worn.bust); textured from the chest
		mesh.getRoot().getChild("body").addOrReplaceChild(CostumeBody.BUST, CubeListBuilder.create().texOffs(BUST_U, BUST_V)
			.addBox(BUST_X, BUST_Y, BUST_Z, BUST_W, BUST_H, BUST_D, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

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
			case HOOD, ASH_VEIL -> HOOD_GROW;
			case HELMET -> HELMET_GROW;
			default -> MASK_GROW;
		};
		head.addOrReplaceChild("mask", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(grow)),
			PartPose.ZERO);
		if (mask == CostumeDesign.Mask.HELMET) {
			head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, -11.0F, -4.5F, 1.0F, 2.0F, 9.0F), PartPose.ZERO);
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

	private static final Map<String, Identifier> TEXTURES = new HashMap<>();

	/** A piece's mask texture: {@code kind} suit, mask or cape; {@code secondary} for the secondary colour's. */
	static Identifier texture(String kind, String name, boolean slim, boolean secondary) {
		String path = "textures/entity/costume/" + kind + "/" + name.toLowerCase(Locale.ROOT) + (slim ? "_slim" : "")
			+ (secondary ? "_secondary" : "_primary") + ".png";
		return TEXTURES.computeIfAbsent(path, JaymosPowers::id);
	}
}
