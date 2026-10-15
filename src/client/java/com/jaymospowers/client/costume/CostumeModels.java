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
	/** The emblem patch on the chest: its size (pixels) and the gap in front of the suit's chest. */
	static final float EMBLEM_SIZE = 6.0F, EMBLEM_TOP = 1.5F, EMBLEM_GAP = 0.05F;

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

		Worn(ModelPart root, boolean slim, Supplier<PlayerModel> wearer) {
			super(root, slim);
			this.wearer = wearer;
			this.cape = this.body.hasChild("cape") ? this.body.getChild("cape") : null;
			hideSkinLayers();
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			resetPose();
			PlayerModel parent = this.wearer.get();
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

		/** The skin's outer layers have no part in a costume. */
		private void hideSkinLayers() {
			this.hat.visible = false;
			this.jacket.visible = false;
			this.leftSleeve.visible = false;
			this.rightSleeve.visible = false;
			this.leftPants.visible = false;
			this.rightPants.visible = false;
		}

		private static ModelPart[] parts(PlayerModel model) {
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

	/** The body suit: the player's own boxes, grown a little; the armoured cut adds shoulder pads. */
	static LayerDefinition suit(CostumeDesign.Cut cut, boolean slim) {
		MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(SUIT_GROW), slim);
		if (cut == CostumeDesign.Cut.ARMOURED) {
			PartDefinition root = mesh.getRoot();
			// pads over the shoulders, textured from the (unused) hat region
			root.getChild("right_arm").addOrReplaceChild("pad", CubeListBuilder.create().texOffs(32, 0)
				.addBox(slim ? -3.0F : -3.5F, -2.8F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
			root.getChild("left_arm").addOrReplaceChild("pad", CubeListBuilder.create().texOffs(32, 0).mirror()
				.addBox(slim ? -2.0F : -1.5F, -2.8F, -2.5F, 5.0F, 3.0F, 5.0F, new CubeDeformation(SUIT_GROW)), PartPose.ZERO);
		}
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
			case HOOD -> HOOD_GROW;
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
