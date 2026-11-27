package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.VillagerHeritage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-26): "some of the heritage looks have flat faces. nonhumans should get protruding features like biobuilts".
 * The animal and monster faces that were only painted on the skin get real boxes on the head, the way a Bio-built form's
 * beak and brow are ({@link BioForms}): muzzles and noses on the Beastfolk, a beak on the Avian, fangs on the Arachnid and
 * the Serpen, a lizard's snout on the Scalebound, horns on the Dragonblood, a shark's and a moray's toothed jaws, the
 * Pufferfish's lips, the crab's mandibles, the seadragon's tube snout, and the Skeleton's jaw.
 * <p>
 * No new art: every box is cut from a texture the pack already has, named on its line ({@code tex=}, {@code uv=}): the
 * Beastfolk's and the sea looks' own parts sheets (the same fur and hide as their ears and tails), and vanilla's chicken
 * beak, goat horns and skeleton head. {@code tools/face_parts_preview.py} reads this table, checks every face of every box
 * lands on painted texels, and draws the before-and-after preview.
 * <p>
 * A line: id, pair (1: a mirrored twin on the other side), the box (x y z w h d, head pixels: the face is at z -4, the eyes
 * at y -5 to -4, the chin at y 0), the pivot (x y z) and turn (x y z degrees, applied z, y, x like a model part), then
 * {@code uv=u,v} on the texture; {@code tex=} and {@code size=w,h} override the look's; {@code s=x,y,z} stretches it (a box
 * cut small to fit the texels it borrows); {@code tint=RRGGBB} colours it.
 * A look's key is a variant's ({@link HeritageVariants.Variant#key}), or {@code *} and a heritage's for its first look.
 * <p>
 * Hidden under a helmet or a hood, a Powers mask or costume ({@link PowersCover}), on the invisible, and on a Bio-built's
 * form (its own face). Drawn on MCA's skin body when MCA draws it ({@link McaLookModels}), so they follow its head.
 */
public final class FaceParts {
	static final String TABLE = """
		# ---- the Beastfolk: muzzles in their own fur (parts/<design>.png: the tail's fur), noses cut from vanilla's spider
		@*beastfolk tex=jaymoskingdoms:textures/entity/heritage/parts/beastfolk.png
		muzzle   0   -1.5 -1 -3   3 2 3    0 -2.2 -4    0 0 0   uv=1,17 s=1.3,1,0.8
		nose     0   -1 -0.5 -0.5   2 1 1    0 -2.9 -6.4    0 0 0   uv=6,0 tex=minecraft:textures/entity/spider/spider.png size=64,32
		@canine tex=jaymoskingdoms:textures/entity/heritage/parts/canine.png
		muzzle   0   -1.5 -1.5 -4   3 3 4    0 -2.4 -4    0 0 0   uv=0,16 s=1.3,0.9,1
		jaw      0   -1.5 -0.5 -3   3 1 3    0 -0.4 -4    0 0 0   uv=1,18
		nose     0   -1 -0.5 -0.5   2 1 1    0 -3.4 -7.6    0 0 0   uv=6,0 tex=minecraft:textures/entity/spider/spider.png size=64,32
		@feline tex=jaymoskingdoms:textures/entity/heritage/parts/feline.png
		muzzle   0   -1.5 -1 -1.5   3 2 2    0 -2 -4    0 0 0   uv=2,18 s=1.2,1,0.8
		nose     0   -0.5 -0.5 -0.5   1 1 1    0 -2.8 -5.3    0 0 0   uv=6,0 tex=minecraft:textures/entity/spider/spider.png size=64,32
		@kitsune tex=jaymoskingdoms:textures/entity/heritage/parts/kitsune.png
		muzzle   0   -1.5 -1 -4   3 2 4    0 -2.4 -4    0 0 0   uv=0,16
		jaw      0   -1 -0.5 -3   2 1 3    0 -0.9 -4    0 0 0   uv=18,17
		nose     0   -0.5 -0.5 -0.5   1 1 1    0 -3.2 -7.6    0 0 0   uv=6,0 tex=minecraft:textures/entity/spider/spider.png size=64,32
		# ---- the Avian's beak (vanilla chicken), the Arachnid's fangs (vanilla spider), the snake's and the lizard's snouts
		@avian tex=minecraft:textures/entity/chicken/chicken_temperate.png size=64,32
		beak     0   -2 -1 -2   4 2 2    0 -3 -4    10 0 0   uv=14,0
		hook     0   -1 0 -1   2 1 1    0 -2.2 -5.8    0 0 0   uv=14,2
		@arachnid tex=minecraft:textures/entity/spider/spider.png size=64,32
		fang     1   -0.5 0 -0.5   1 2 1    1 -2 -4.4    -12 0 -10   uv=6,0
		@serpen tex=jaymoskingdoms:textures/entity/heritage/parts/serpen.png
		snout    0   -1.5 -1 -3   3 2 3    0 -2 -4    0 0 0   uv=1,17 s=1.8,1,1
		fang     1   -0.5 0 -0.5   1 1 1    1.6 -1 -6.2    0 0 0   uv=8,0 tex=minecraft:textures/entity/skeleton/skeleton.png size=64,32
		@scalebound tex=jaymoskingdoms:textures/entity/heritage/parts/scalebound.png
		snout    0   -1.5 -1.5 -4   3 3 4    0 -2.2 -4    0 0 0   uv=0,16 s=1.5,0.9,1
		brow     0   -1.5 -0.5 -0.5   3 1 1    0 -5.2 -4.3    0 0 0   uv=1,20 s=2.7,1,1
		# ---- the Dragonblood: horns swept back from the temples (vanilla goat horn), all four bloods
		@*dragonblood tex=minecraft:textures/entity/goat/goat.png
		horn     1   -1 -4 -1   2 4 2    3 -7 -1.5    -55 0 18   uv=12,55
		tip      1   -0.5 -3 -0.5   1 3 1    3.6 -8.9 1.5    -80 0 18   uv=14,55
		@goldscale tex=minecraft:textures/entity/goat/goat.png
		horn     1   -1 -4 -1   2 4 2    3 -7 -1.5    -55 0 18   uv=12,55
		tip      1   -0.5 -3 -0.5   1 3 1    3.6 -8.9 1.5    -80 0 18   uv=14,55
		@rimewyrm tex=minecraft:textures/entity/goat/goat.png
		horn     1   -1 -4 -1   2 4 2    3 -7 -1.5    -55 0 18   uv=12,55
		tip      1   -0.5 -3 -0.5   1 3 1    3.6 -8.9 1.5    -80 0 18   uv=14,55
		@stormwyrm tex=minecraft:textures/entity/goat/goat.png
		horn     1   -1 -4 -1   2 4 2    3 -7 -1.5    -55 0 18   uv=12,55
		tip      1   -0.5 -3 -0.5   1 3 1    3.6 -8.9 1.5    -80 0 18   uv=14,55
		@venomscale tex=minecraft:textures/entity/goat/goat.png
		horn     1   -1 -4 -1   2 4 2    3 -7 -1.5    -55 0 18   uv=12,55
		tip      1   -0.5 -3 -0.5   1 3 1    3.6 -8.9 1.5    -80 0 18   uv=14,55
		# ---- the sea looks whose faces were painted: a dolphin-grey shark snout, the moray's own hide, the anglerfish's toothed jaw
		@greyfin tex=minecraft:textures/entity/dolphin/dolphin.png
		snout    0   -3 -2 -3   6 2 3    0 -2.5 -4    6 0 0   uv=3,3
		jaw      0   -3 -1 -2   6 2 2    0 -0.6 -3.8    0 0 0   uv=12,0 tex=jaymoskingdoms:textures/entity/heritage/parts/sea_lanternjaw.png
		@moray tex=jaymoskingdoms:textures/entity/heritage/parts/sea_moray.png
		snout    0   -2 -1 -2   4 2 2    0 -2.4 -4    4 0 0   uv=0,4 s=1.2,1,1.4
		jaw      0   -3 -1 -2   6 2 2    0 -0.6 -3.8    0 0 0   uv=12,0 tex=jaymoskingdoms:textures/entity/heritage/parts/sea_lanternjaw.png
		@spinepuff tex=jaymoskingdoms:textures/entity/heritage/parts/sea_spinepuff.png
		lips     0   -1.5 -1 -1   3 2 1    0 -1.5 -4    0 0 0   uv=0,1 s=1,1,1.6
		@shellclaw tex=jaymoskingdoms:textures/entity/heritage/parts/sea_shellclaw.png
		mandible 1   -0.5 0 -1   1 2 1    1 -2.2 -4    -10 0 -14   uv=2,0
		@weedfrond tex=jaymoskingdoms:textures/entity/heritage/parts/sea_weedfrond.png
		snout    0   -1 -0.5 -2   2 1 2    0 -2.6 -4    4 0 0   uv=4,1 s=1,2,2.4
		# ---- the Hollow's Skeleton: a bare jaw (vanilla skeleton's teeth)
		@skeleton tex=minecraft:textures/entity/skeleton/skeleton.png size=64,32
		jaw      0   -3 -1 -2   6 2 2    0 -0.8 -3.4    0 0 0   uv=7,10
		""";

	/** One box: whether it has a mirrored twin, where it sits and turns, its texture and place on it, its tint. */
	static final class Piece {
		final String id;
		final boolean pair;
		final float x, y, z, w, h, d, px, py, pz, rx, ry, rz;
		int u, v, texW = 64, texH = 64, tint = -1;
		/** A stretch, for a box cut small to fit the painted texels it borrows. */
		float sx = 1.0F, sy = 1.0F, sz = 1.0F;
		Identifier tex;
		private @Nullable ModelPart part, mirrored;

		Piece(String id, boolean pair, float[] f, Identifier tex) {
			this.id = id;
			this.pair = pair;
			this.x = f[0];
			this.y = f[1];
			this.z = f[2];
			this.w = f[3];
			this.h = f[4];
			this.d = f[5];
			this.px = f[6];
			this.py = f[7];
			this.pz = f[8];
			this.rx = f[9];
			this.ry = f[10];
			this.rz = f[11];
			this.tex = tex;
		}

		/** The box (baked the first time it's drawn) on its pivot; the twin is mirrored across the middle of the face. */
		ModelPart part(boolean mirror) {
			if (mirror) {
				if (this.mirrored == null) {
					this.mirrored = bake(true);
				}
				return this.mirrored;
			}
			if (this.part == null) {
				this.part = bake(false);
			}
			return this.part;
		}

		private ModelPart bake(boolean mirror) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(this.u, this.v).mirror(mirror)
				.addBox(mirror ? -(this.x + this.w) : this.x, this.y, this.z, this.w, this.h, this.d), PartPose.ZERO);
			return LayerDefinition.create(mesh, this.texW, this.texH).bakeRoot();
		}

		/** Onto its pivot and turn (a model part's way: z, y, x) and its stretch; the twin's x and its y and z turns flipped. */
		void place(PoseStack poseStack, boolean mirror) {
			float m = mirror ? -1.0F : 1.0F;
			poseStack.translate(this.px * m / 16.0F, this.py / 16.0F, this.pz / 16.0F);
			poseStack.rotate(new Quaternionf().rotationZYX(this.rz * m * Mth.DEG_TO_RAD, this.ry * m * Mth.DEG_TO_RAD, this.rx * Mth.DEG_TO_RAD));
			poseStack.scale(this.sx, this.sy, this.sz);
		}
	}

	/** The looks' pieces, by variant, and by heritage for a first look. */
	private static final Map<HeritageVariants.Variant, List<Piece>> BY_VARIANT = new HashMap<>();
	private static final Map<Heritage.Kind, List<Piece>> BY_FIRST = new HashMap<>();

	static {
		parse();
	}

	static final RenderStateDataKey<List<Piece>> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:face_parts");

	private FaceParts() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the face pieces {@code entity}'s look has (none for most). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		HeritageVariants.Variant variant = HeritageVariants.of(entity);
		List<Piece> pieces = variant != null ? BY_VARIANT.get(variant) : null;
		if (variant == null) {
			Heritage.Kind kind = VillagerHeritage.of(entity);
			pieces = kind == null ? null : BY_FIRST.get(kind);
		}
		state.setData(KEY, pieces);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			List<Piece> pieces = state.getData(KEY);
			if (pieces == null || state.isInvisible || state.getData(BioForms.KEY) != null || !state.headEquipment.isEmpty()
				|| PowersCover.head(state) || state instanceof AvatarRenderState && PowersCover.face(state)) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			HumanoidModel<?> posed = model;
			if (McaCompat.LOADED) {
				// on MCA's skin body, in the pose MCA gives it (the ears and fins do the same)
				HumanoidModel<?> mca = model instanceof PlayerModel player ? McaLookModels.playerMarks(player) : McaLookModels.marks(model);
				if (mca != null) {
					posed = mca;
				}
			}
			if (!posed.head.visible) {
				return;
			}
			poseStack.pushPose();
			posed.head.translateAndRotate(poseStack);
			for (Piece p : pieces) {
				for (int side = 0; side < (p.pair ? 2 : 1); side++) {
					poseStack.pushPose();
					p.place(poseStack, side == 1);
					collector.order(1).submitModelPart(p.part(side == 1), poseStack, RenderTypes.entityCutout(p.tex), lightCoords,
						OverlayTexture.NO_OVERLAY, null, p.tint);
					poseStack.popPose();
				}
			}
			poseStack.popPose();
		}
	}

	// ---- the table

	private static void parse() {
		String key = null;
		Identifier tex = null;
		int[] size = {64, 64};
		List<Piece> pieces = new ArrayList<>();
		for (String raw : (TABLE + "@end\n").split("\n")) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] t = line.split("\\s+");
			if (t[0].startsWith("@")) {
				if (key != null) {
					finish(key, pieces);
				}
				key = t[0].substring(1);
				tex = null;
				size = new int[] {64, 64};
				pieces = new ArrayList<>();
				for (int i = 1; i < t.length; i++) {
					if (t[i].startsWith("tex=")) {
						tex = Identifier.parse(t[i].substring(4));
					} else if (t[i].startsWith("size=")) {
						String[] s = t[i].substring(5).split(",");
						size = new int[] {Integer.parseInt(s[0]), Integer.parseInt(s[1])};
					}
				}
				continue;
			}
			float[] f = new float[12];
			for (int i = 0; i < 12; i++) {
				f[i] = Float.parseFloat(t[2 + i]);
			}
			Piece p = new Piece(t[0], t[1].equals("1"), f, tex);
			p.texW = size[0];
			p.texH = size[1];
			for (int i = 14; i < t.length; i++) {
				int eq = t[i].indexOf('=');
				String k = t[i].substring(0, eq), val = t[i].substring(eq + 1);
				String[] vals = val.split(",");
				switch (k) {
					case "uv" -> {
						p.u = Integer.parseInt(vals[0]);
						p.v = Integer.parseInt(vals[1]);
					}
					case "tex" -> p.tex = Identifier.parse(val);
					case "size" -> {
						p.texW = Integer.parseInt(vals[0]);
						p.texH = Integer.parseInt(vals[1]);
					}
					case "s" -> {
						p.sx = Float.parseFloat(vals[0]);
						p.sy = Float.parseFloat(vals[1]);
						p.sz = Float.parseFloat(vals[2]);
					}
					case "tint" -> p.tint = 0xFF000000 | Integer.parseInt(val, 16);
					default -> JaymosKingdoms.LOGGER.warn("FaceParts: unknown option {} on {}", k, p.id);
				}
			}
			pieces.add(p);
		}
	}

	private static void finish(String key, List<Piece> pieces) {
		if (key.startsWith("*")) {
			Heritage.Kind kind = Heritage.Kind.byKey(key.substring(1));
			if (kind == null) {
				JaymosKingdoms.LOGGER.warn("FaceParts: no heritage called {}", key);
				return;
			}
			BY_FIRST.put(kind, List.copyOf(pieces));
			return;
		}
		HeritageVariants.Variant variant = HeritageVariants.Variant.byKey(key);
		if (variant == null) {
			JaymosKingdoms.LOGGER.warn("FaceParts: no heritage look called {}", key);
			return;
		}
		BY_VARIANT.put(variant, List.copyOf(pieces));
	}
}
