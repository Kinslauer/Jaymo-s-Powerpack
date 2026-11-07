package com.jaymoskingdoms.client;

import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.486, Andy: "tideborn need to vary wildly ... they are fish people so they can draw from any sealife") The thirteen
 * sea-blooded Tideborn looks' real model parts, the way the Beastfolk's ears and tails are ({@link BeastParts}): a shark's
 * dorsal fin and tail, a manta's back-wings and whip tail, an octopus's mantle and tentacles, an anglerfish's lure and fangs,
 * a jellyfish's clear bell and trailing threads, a moray's ribbon fins and eel tail, a crab's shell, claws and eyestalks, a
 * pufferfish's spines, a lionfish's mane of venom rays, a seadragon's leafy fronds and curled tail, a swordfish's bill and
 * sail, a sea slug's frills and horns, a sea turtle's shell and beak.
 * <p>
 * Every piece is in {@link #TABLE}, one line each, so the look is tuned there. {@code tools/gen_tideborn_sea.py} reads the same
 * table to paint each look's 64x64 sheet ({@code parts/sea_<key>.png}, its glow twin {@code _glow}) and the project's preview
 * renders, and both lay the boxes out on the sheet the same way ({@link #pack}). A line: id, anchor (HEAD, BODY, or ARM: the
 * right arm, its mirror on the left), pair (1: a mirrored twin on the other side), the box (x y z w h d, model pixels), the
 * pivot (x y z) and turn (x y z degrees, applied z, y, x like a model part), then options: {@code on=<id>} hangs it off the
 * end of that piece; {@code n=} segments, each moved by {@code step=} and turned by {@code bend=} from the last and shrunk
 * by {@code taper=}; {@code wave=amp,axis,speed,lag} sways it (degrees, per tick, each segment that much behind the last);
 * {@code ar=} how far it stands off the back over a chestplate; {@code s=} a stretch; {@code mat=} the generator's paint.
 * <p>
 * Head pieces hide under a helmet or hood, everything under a Powers costume or on the invisible, as the fins do. Parts are
 * drawn on MCA's skin body when MCA draws it ({@link McaLookModels}); a glow twin lights under water and at night while
 * wet (the Tideborn's rule: the Frillback's frill tips), or always (the Lanternjaw's lure, the Driftbell's bell and threads). A look's design line says which of the
 * Tideborn's own fins it keeps ({@link Fins}). Baked lazily, never mutated: all motion is in the pose stack.
 */
public final class SeaParts {
	/** How far a back piece stands off over a chestplate when its line gives none (model pixels). */
	static final float ARMOUR_GAP = 1.2F;

	/** What a piece hangs from. ARM: the right arm, its mirrored twin on the left. */
	enum Anchor { HEAD, BODY, ARM }

	/** Which of the Tideborn's own fins (the head crest and ear fins, the arm fins and hand webbing) a sea look keeps. */
	public enum Fins {
		ALL(true, true), ARMS(false, true), NONE(false, false);

		public final boolean head, arms;

		Fins(boolean head, boolean arms) {
			this.head = head;
			this.arms = arms;
		}
	}

	/** When a look's glow twin lights: never (it has none), as the Tideborn's do (under water, at night while wet), or always. */
	enum Glow { NONE, WET, ALWAYS }

	/**
	 * Every sea look's pieces. Tune here; rerun {@code python -I tools/gen_tideborn_sea.py} after changing a box's size, adding
	 * or reordering pieces (the sheet layout follows the order), or a {@code mat}.
	 */
	static final String TABLE = """
		@greyfin fins=ARMS glow=NONE
		dorsal   BODY 0   0 -9 0   0 9 7    0 3 2.2    -24 0 0   ar=1.5 mat=shark_fin
		tail     BODY 0   -1.5 -1.5 0   3 3 3    0 10.5 1.8   -35 0 0   n=3 step=0,0,3 bend=10,0,0 taper=0.82 wave=10,y,0.12,0.6 ar=1.5 mat=shark_hide
		caudal   BODY 0   0 -6 0   0 10 4    0 0 0   0 0 0   on=tail mat=shark_caudal
		@mantawing fins=NONE glow=NONE
		wing     BODY 1   0 -1 0   11 13 0    2 1 2.4    0 -38 14   wave=7,y,0.08,0 ar=1.2 mat=manta_wing
		horn     HEAD 1   -0.5 -1 -3   1 2 3    2.8 -7 -4    25 0 0   n=2 step=0,0,-3 bend=-60,0,0 taper=0.8 mat=manta_dark
		whip     BODY 0   -0.5 -0.5 0   1 1 4    0 10.5 2    -50 0 0   n=6 step=0,0,4 bend=7,0,0 taper=0.94 wave=9,y,0.1,0.5 ar=1.2 mat=manta_dark
		@eightarm fins=NONE glow=NONE
		mantle   HEAD 0   -3.5 -5 0   7 6 6    0 -6 1.5    -30 0 0   wave=3,x,0.05,0 mat=octo_mantle
		back     HEAD 1   -1 0 -1   2 3 2    2.4 -0.5 3.4    14 0 -8   n=5 step=0,3,0 bend=9,0,-4 taper=0.86 wave=7,x,0.09,0.7 mat=octo_arm
		nape     HEAD 1   -1 0 -1   2 3 2    0.9 -0.2 3.8    20 0 -3   n=5 step=0,3,0 bend=7,0,3 taper=0.86 wave=7,x,0.08,0.7 mat=octo_arm
		beard    HEAD 1   -1 0 -1   2 3 2    1.6 -0.5 -3.4    -12 0 -6   n=4 step=0,3,0 bend=-10,0,-6 taper=0.84 wave=6,x,0.1,0.8 mat=octo_arm
		@lanternjaw fins=ARMS glow=ALWAYS
		stalk    HEAD 0   -0.5 -3 -0.5   1 3 1    0 -8 -2.5    6 0 0   n=3 step=0,-3,0 bend=32,0,0 taper=0.92 wave=5,x,0.1,0.5 mat=angler_stalk
		bulb     HEAD 0   -1 -2 -1   2 2 2    0 0 0    0 0 0   on=stalk mat=angler_bulb
		jaw      HEAD 0   -4 -1 -2   8 2 2    0 -0.6 -3.8    0 0 0   mat=angler_jaw
		fang     HEAD 1   -0.5 -2 -0.5   1 2 1    3 -1.6 -5.2    -10 0 8   mat=tooth
		fang2    HEAD 1   -0.5 -2 -0.5   1 2 1    1.2 -1.6 -5.3    -6 0 -4   s=0.8,0.8,0.8 mat=tooth
		@driftbell fins=NONE glow=ALWAYS translucent
		bell     HEAD 0   -4.5 -4 -4.5   9 4 9    0 -7.5 0    0 0 0   wave=2,z,0.06,0 mat=jelly_bell
		cap      HEAD 0   -3 -2 -3   6 2 6    0 -11.5 0    0 0 0   wave=2,z,0.06,0 mat=jelly_bell
		thread   HEAD 1   -0.5 0 -0.5   1 4 1    3.2 -7.6 3.6    16 0 -4   n=6 step=0,4,0 bend=4,0,2 taper=0.92 wave=8,x,0.07,0.8 mat=jelly_thread
		thread2  HEAD 1   -0.5 0 -0.5   1 4 1    1.2 -7.6 4.2    20 0 -2   n=5 step=0,4,0 bend=3,0,-2 taper=0.92 wave=9,x,0.06,0.9 mat=jelly_thread
		@moray fins=NONE glow=NONE
		tail     BODY 0   -2 -2 0   4 4 4    0 10.5 1.8    -52 0 0   n=7 step=0,0,4 bend=7,0,0 taper=0.88 wave=14,y,0.1,0.6 ar=1.5 mat=moray_hide
		ribbon   BODY 0   0 -4 0   0 2 4    0 10.5 1.8    -52 0 0   n=7 step=0,0,4 bend=7,0,0 taper=0.88 wave=14,y,0.1,0.6 ar=1.5 mat=moray_fin
		spine    BODY 0   0 0 0   0 11 2    0 0.5 2.2    0 0 0   ar=1.5 mat=moray_fin
		crest    HEAD 0   0 -2 -3.5   0 2 8    0 -8 0    0 0 0   mat=moray_fin
		@shellclaw fins=NONE glow=NONE
		shell    BODY 0   -5 -1 0   10 9 2    0 1.5 2.1    8 0 0   ar=1.2 mat=crab_shell
		spike    BODY 1   -0.5 -2 -0.5   1 2 1    4.4 1 3    0 0 30   ar=1.2 mat=crab_spike
		bracer   ARM 1   -3.5 3 -2.5   5 4 5    0 0 0    0 0 0   mat=crab_shell
		finger   ARM 1   -1 0 -1   2 6 2    -3.6 6.8 -1.6    -14 0 10   mat=crab_claw
		thumb    ARM 1   -1 0 -1   2 5 2    -3.6 6.8 1.4    14 0 10   wave=8,x,0.2,0 mat=crab_claw
		stalk    HEAD 1   -0.5 -3 -0.5   1 3 1    2 -8 -3    -10 0 14   wave=6,x,0.15,0 mat=crab_stalk
		eye      HEAD 1   -1 -2 -1   2 2 2    0 -3 0    0 0 0   on=stalk mat=crab_eye
		@spinepuff fins=ARMS glow=NONE
		h1       HEAD 1   -0.5 -3 -0.5   1 3 1    4 -6 -1    0 0 80   mat=puffer_spine
		h2       HEAD 1   -0.5 -3 -0.5   1 3 1    4 -2.5 1    0 0 100   mat=puffer_spine
		h3       HEAD 1   -0.5 -3 -0.5   1 3 1    3 -8 -2    -10 0 40   mat=puffer_spine
		h4       HEAD 1   -0.5 -3 -0.5   1 3 1    3 -8 2    -25 0 40   mat=puffer_spine
		h5       HEAD 1   -0.5 -3 -0.5   1 3 1    1 -8 0    0 0 15   mat=puffer_spine
		h6       HEAD 1   -0.5 -3 -0.5   1 3 1    4 -5.5 3    -30 0 70   mat=puffer_spine
		h7       HEAD 1   -0.5 -3 -0.5   1 3 1    2 -4 4    -70 0 20   mat=puffer_spine
		b1       BODY 1   -0.5 -3 -0.5   1 3 1    2.5 2 2    -70 0 0   ar=1.2 mat=puffer_spine
		b2       BODY 1   -0.5 -3 -0.5   1 3 1    2.5 6 2    -75 0 0   ar=1.2 mat=puffer_spine
		b3       BODY 1   -0.5 -3 -0.5   1 3 1    1 10 2    -80 0 0   ar=1.2 mat=puffer_spine
		b4       BODY 1   -0.5 -3 -0.5   1 3 1    3.5 4 2    -60 0 30   ar=1.2 mat=puffer_spine
		a1       ARM 1   -0.5 -3 -0.5   1 3 1    -3 5 0    0 0 -80   mat=puffer_spine
		a2       ARM 1   -0.5 -3 -0.5   1 3 1    -3 8 1    -20 0 -85   mat=puffer_spine
		@lionmane fins=NONE glow=NONE
		r0       HEAD 0   -1 -9 0   2 9 0    0 -6 4    -40 0 0   wave=4,z,0.06,0 mat=lion_ray
		r1       HEAD 1   -1 -9 0   2 9 0    2.5 -7 3.5    -35 0 30   wave=4,z,0.06,0 mat=lion_ray
		r2       HEAD 1   -1 -9 0   2 9 0    4 -5 2    -20 0 65   wave=4,z,0.07,0 mat=lion_ray
		r3       HEAD 1   -1 -8 0   2 8 0    4 -2 1    -10 0 100   wave=4,z,0.07,0 mat=lion_ray
		r4       HEAD 1   -1 -8 0   2 8 0    3 -8 0    -15 0 18   wave=4,z,0.05,0 mat=lion_ray
		fan      ARM 1   0 0 0   0 7 6    -3 3 1.5    0 -30 0   wave=6,y,0.08,0 mat=lion_fan
		d1       BODY 0   0 -8 -1   0 8 2    0 1 2.2    -55 0 0   ar=1.5 mat=lion_ray
		d2       BODY 0   0 -8 -1   0 8 2    0 4 2.2    -62 0 0   ar=1.5 mat=lion_ray
		d3       BODY 0   0 -7 -1   0 7 2    0 7 2.2    -68 0 0   ar=1.5 mat=lion_ray
		@weedfrond fins=NONE glow=NONE
		crown    HEAD 0   -2 -2 -2   4 2 4    0 -8 0    0 0 0   mat=dragon_crown
		fh1      HEAD 1   -0.5 -3 -0.5   1 3 1    3 -8 0    -20 0 35   wave=6,z,0.07,0 mat=frond_stalk
		lh1      HEAD 1   -2 -4 0   4 4 0    0 -3 0    0 0 0   on=fh1 mat=frond_leaf
		fh2      HEAD 1   -0.5 -3 -0.5   1 3 1    4 -4 2    -30 0 80   wave=6,z,0.06,0 mat=frond_stalk
		lh2      HEAD 1   -2 -4 0   4 4 0    0 -3 0    0 0 0   on=fh2 mat=frond_leaf
		fh3      HEAD 0   -0.5 -3 -0.5   1 3 1    0 -8 3    -50 0 0   wave=6,z,0.07,0 mat=frond_stalk
		lh3      HEAD 0   -2 -4 0   4 4 0    0 -3 0    0 0 0   on=fh3 mat=frond_leaf
		fb1      BODY 1   -0.5 -3 -0.5   1 3 1    3 1 2.2    -60 0 30   wave=6,z,0.06,0 ar=1.3 mat=frond_stalk
		lb1      BODY 1   -2 -4 0   4 4 0    0 -3 0    0 0 0   on=fb1 mat=frond_leaf
		fb2      BODY 1   -0.5 -3 -0.5   1 3 1    2 7 2.2    -70 0 20   wave=6,z,0.07,0 ar=1.3 mat=frond_stalk
		lb2      BODY 1   -2 -4 0   4 4 0    0 -3 0    0 0 0   on=fb2 mat=frond_leaf
		fa1      ARM 1   -0.5 -3 -0.5   1 3 1    -3 4 1    0 0 -70   wave=6,z,0.08,0 mat=frond_stalk
		la1      ARM 1   -2 -4 0   4 4 0    0 -3 0    0 0 0   on=fa1 mat=frond_leaf
		tail     BODY 0   -1.5 -1.5 0   3 3 3    0 10.5 1.8    -60 0 0   n=7 step=0,0,3 bend=22,0,0 taper=0.86 wave=4,y,0.08,0.4 ar=1.3 mat=dragon_tail
		@swordbill fins=ARMS glow=NONE
		bill     HEAD 0   -1 -1 -4   2 2 4    0 -3 -4    4 0 0   n=3 step=0,0,-4 taper=0.72 mat=sword_bill
		sail     BODY 0   0 0 0   0 11 9    0 0.5 2.2    0 0 0   ar=1.5 mat=sword_sail
		tail     BODY 0   -1 -1 0   2 2 3    0 10.5 1.8    -30 0 0   n=2 step=0,0,3 bend=12,0,0 taper=0.85 wave=10,y,0.13,0.6 ar=1.5 mat=sword_hide
		caudal   BODY 0   0 -6 0   0 12 3    0 0 0    0 0 0   on=tail mat=sword_caudal
		@frillback fins=NONE glow=WET
		horn     HEAD 1   -0.5 -3 -0.5   1 3 1    2 -8 -2    -15 0 15   n=2 step=0,-3,0 bend=-12,0,0 taper=0.8 wave=5,x,0.1,0.5 mat=slug_horn
		c1       HEAD 1   -0.5 -3 -0.5   1 3 1    3 -7 2    -40 0 30   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.1,0.5 mat=slug_cerata
		c2       HEAD 1   -0.5 -3 -0.5   1 3 1    1.5 -8 3    -45 0 10   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.11,0.5 mat=slug_cerata
		c3       HEAD 1   -0.5 -3 -0.5   1 3 1    3.8 -4 3.5    -70 0 40   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.09,0.5 mat=slug_cerata
		c4       BODY 1   -0.5 -3 -0.5   1 3 1    2.5 1 2.2    -70 0 15   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.1,0.5 ar=1.2 mat=slug_cerata
		c5       BODY 1   -0.5 -3 -0.5   1 3 1    1 3 2.2    -75 0 0   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.12,0.5 ar=1.2 mat=slug_cerata
		c6       BODY 1   -0.5 -3 -0.5   1 3 1    3 5 2.2    -70 0 25   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.1,0.5 ar=1.2 mat=slug_cerata
		c7       BODY 1   -0.5 -3 -0.5   1 3 1    1.5 8 2.2    -80 0 0   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.11,0.5 ar=1.2 mat=slug_cerata
		c8       BODY 1   -0.5 -3 -0.5   1 3 1    3 10 2.2    -80 0 20   n=2 step=0,-3,0 bend=-12,0,6 taper=0.85 wave=6,x,0.09,0.5 ar=1.2 mat=slug_cerata
		@oldshell fins=ARMS glow=NONE
		shell    BODY 0   -5 -1 0   10 12 3    0 0.5 2.1    5 0 0   ar=1.5 mat=turtle_shell
		dome     BODY 0   -4 0 0   8 10 1    0 1.2 5    5 0 0   ar=1.5 mat=turtle_dome
		tail     BODY 0   -1 -1 0   2 2 2    0 11 2    -40 0 0   n=2 step=0,0,2 bend=10,0,0 taper=0.7 wave=6,y,0.08,0.5 ar=1.5 mat=turtle_skin
		beak     HEAD 0   -2 -1 -1   4 2 1    0 -2.4 -4    0 0 0   mat=turtle_beak
		""";

	/** One box of a look, with its place, its segments and its sway, and what hangs off its end. */
	static final class Piece {
		final String id;
		final Anchor anchor;
		final boolean pair;
		final float x, y, z, px, py, pz, rx, ry, rz;
		final int w, h, d;
		int n = 1;
		float stepX, stepY, stepZ, bendX, bendY, bendZ, taper = 1.0F, waveAmp, waveSpeed, waveLag, armour = ARMOUR_GAP;
		float scaleX = 1.0F, scaleY = 1.0F, scaleZ = 1.0F;
		char waveAxis = 'y';
		int u, v;
		final List<Piece> children = new ArrayList<>();
		private @Nullable ModelPart part, mirrored;

		Piece(String id, Anchor anchor, boolean pair, float[] f) {
			this.id = id;
			this.anchor = anchor;
			this.pair = pair;
			this.x = f[0];
			this.y = f[1];
			this.z = f[2];
			this.w = Math.round(f[3]);
			this.h = Math.round(f[4]);
			this.d = Math.round(f[5]);
			this.px = f[6];
			this.py = f[7];
			this.pz = f[8];
			this.rx = f[9];
			this.ry = f[10];
			this.rz = f[11];
		}

		/** The box, baked the first time it's drawn; the mirrored twin is the vanilla left-arm way (mirror flag, x flipped). */
		ModelPart part(boolean mirror) {
			if (mirror) {
				if (this.mirrored == null) {
					this.mirrored = bake(-(this.x + this.w), true);
				}
				return this.mirrored;
			}
			if (this.part == null) {
				this.part = bake(this.x, false);
			}
			return this.part;
		}

		private ModelPart bake(float atX, boolean mirror) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(this.u, this.v).mirror(mirror)
				.addBox(atX, this.y, this.z, this.w, this.h, this.d), PartPose.ZERO);
			return LayerDefinition.create(mesh, SHEET, SHEET).bakeRoot();
		}
	}

	/** A look: its sheet, its glow twin and when it lights, whether it's half-clear, which Tideborn fins it keeps, its pieces. */
	record Design(String key, Identifier sheet, Identifier glow, Glow lights, boolean translucent, Fins fins, List<Piece> roots) {
	}

	/** The sheets' size, and the gap left between boxes on them (texels). */
	static final int SHEET = 64, GAP = 0;

	private static final Map<HeritageVariants.Variant, Design> DESIGNS = new EnumMap<>(HeritageVariants.Variant.class);

	static {
		parse();
	}

	/** On a render state: the look this one wears, whether its glow is lit, and a phase from its UUID. */
	record Parts(Design design, boolean lit, float phase) {
	}

	static final RenderStateDataKey<Parts> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:sea_parts");

	private SeaParts() {
	}

	/** Which of the Tideborn's own fins {@code style}'s look keeps (all, for the first three looks). */
	public static Fins fins(VariantLooks.@Nullable Style style) {
		Design design = style == null ? null : DESIGNS.get(style.variant());
		return design == null ? Fins.ALL : design.fins();
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

	/** From the living renderer's extractRenderState: a sea look's parts (nothing for anyone else). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		HeritageVariants.Variant variant = HeritageVariants.of(entity);
		Design design = variant == null ? null : DESIGNS.get(variant);
		if (design == null) {
			state.setData(KEY, null);
			return;
		}
		// lit as the Tideborn's glow is: under water, or in the rain or still wet at night
		boolean lit = design.lights() == Glow.ALWAYS
			|| design.lights() == Glow.WET && (entity.isUnderWater() || entity.isInWaterOrRain() && entity.level().isDarkOutside());
		state.setData(KEY, new Parts(design, lit, HeritageLooks.variant(entity.getUUID(), 997) * 0.37F));
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Parts parts = state.getData(KEY);
			if (parts == null || state.isInvisible || state.getData(BioForms.KEY) != null) {
				return;
			}
			boolean headCovered = PowersCover.head(state) || !state.headEquipment.isEmpty(), bodyCovered = PowersCover.body(state);
			HumanoidModel<S> model = this.getParentModel();
			HumanoidModel<?> posed = model;
			if (McaCompat.LOADED) {
				HumanoidModel<?> mca = model instanceof PlayerModel player ? McaLookModels.playerMarks(player) : McaLookModels.marks(model);
				if (mca != null) {
					posed = mca;
				}
			}
			Design design = parts.design();
			boolean armour = !state.chestEquipment.isEmpty();
			Ctx ctx = new Ctx(poseStack, collector, lightCoords, design.sheet(), parts.lit() ? design.glow() : null, design.translucent());
			for (Piece root : design.roots()) {
				if (root.anchor == Anchor.HEAD ? headCovered : bodyCovered) {
					continue;
				}
				for (int side = 0; side < (root.pair ? 2 : 1); side++) {
					boolean mirror = side == 1;
					ModelPart at = switch (root.anchor) {
						case HEAD -> posed.head;
						case BODY -> posed.body;
						case ARM -> mirror ? posed.leftArm : posed.rightArm;
					};
					if (!at.visible) {
						continue;
					}
					poseStack.pushPose();
					at.translateAndRotate(poseStack);
					if (armour && root.anchor == Anchor.BODY) {
						poseStack.translate(0.0F, 0.0F, root.armour / 16.0F);
					}
					draw(ctx, root, mirror, state.ageInTicks, parts.phase());
					poseStack.popPose();
				}
			}
		}
	}

	/** What a piece is drawn with: the pose stack, the collector, the light, the sheet, its glow twin (null: unlit), half-clear or not. */
	private record Ctx(PoseStack ps, SubmitNodeCollector collector, int light, Identifier sheet, @Nullable Identifier glow, boolean translucent) {
		void draw(ModelPart part) {
			RenderType type = this.translucent ? RenderTypes.entityTranslucent(this.sheet) : RenderTypes.entityCutout(this.sheet);
			this.collector.order(1).submitModelPart(part, this.ps, type, this.light, OverlayTexture.NO_OVERLAY, null, -1);
			if (this.glow != null) {
				this.collector.order(2).submitModelPart(part, this.ps, RenderTypes.eyes(this.glow), this.light, OverlayTexture.NO_OVERLAY, null, -1);
			}
		}
	}

	/**
	 * A piece and what hangs off it. The mirrored twin is the same piece reflected across the middle: its pivot's x, its y and z
	 * turns (and bends, sways and steps) flipped, its box the mirror-flagged one.
	 */
	private static void draw(Ctx ctx, Piece p, boolean mirror, float age, float phase) {
		float m = mirror ? -1.0F : 1.0F;
		PoseStack ps = ctx.ps();
		ps.pushPose();
		ps.translate(m * p.px / 16.0F, p.py / 16.0F, p.pz / 16.0F);
		turn(ps, p.rx, m * p.ry, m * p.rz);
		if (p.scaleX != 1.0F || p.scaleY != 1.0F || p.scaleZ != 1.0F) {
			ps.scale(p.scaleX, p.scaleY, p.scaleZ);
		}
		for (int i = 0; i < p.n; i++) {
			float wave = p.waveAmp == 0.0F ? 0.0F : p.waveAmp * Mth.sin(age * p.waveSpeed + phase - i * p.waveLag);
			float ax = p.waveAxis == 'x' ? wave : 0.0F, ay = p.waveAxis == 'y' ? wave : 0.0F, az = p.waveAxis == 'z' ? wave : 0.0F;
			if (i > 0) {
				ax += p.bendX;
				ay += p.bendY;
				az += p.bendZ;
			}
			if (ax != 0.0F || ay != 0.0F || az != 0.0F) {
				turn(ps, ax, m * ay, m * az);
			}
			ctx.draw(p.part(mirror));
			ps.translate(m * p.stepX / 16.0F, p.stepY / 16.0F, p.stepZ / 16.0F);
			if (p.taper != 1.0F) {
				ps.scale(p.taper, p.taper, p.taper);
			}
		}
		for (Piece child : p.children) {
			draw(ctx, child, mirror, age, phase);
		}
		ps.popPose();
	}

	/** Turned the way a model part is (z, then y, then x), in degrees. */
	private static void turn(PoseStack ps, float x, float y, float z) {
		if (z != 0.0F) {
			ps.rotate(Axis.ZP, z * Mth.DEG_TO_RAD);
		}
		if (y != 0.0F) {
			ps.rotate(Axis.YP, y * Mth.DEG_TO_RAD);
		}
		if (x != 0.0F) {
			ps.rotate(Axis.XP, x * Mth.DEG_TO_RAD);
		}
	}

	// ---- the table

	private static void parse() {
		String key = null;
		Glow lights = Glow.WET;
		Fins fins = Fins.ALL;
		boolean translucent = false;
		List<Piece> pieces = new ArrayList<>();
		for (String raw : (TABLE + "@end\n").split("\n")) {
			String line = raw.strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] t = line.split("\\s+");
			if (t[0].startsWith("@")) {
				if (key != null) {
					finish(key, lights, translucent, fins, pieces);
				}
				key = t[0].substring(1);
				lights = Glow.WET;
				fins = Fins.ALL;
				translucent = false;
				pieces = new ArrayList<>();
				for (int i = 1; i < t.length; i++) {
					if (t[i].startsWith("fins=")) {
						fins = Fins.valueOf(t[i].substring(5));
					} else if (t[i].startsWith("glow=")) {
						lights = Glow.valueOf(t[i].substring(5));
					} else if (t[i].equals("translucent")) {
						translucent = true;
					}
				}
				continue;
			}
			float[] f = new float[12];
			for (int i = 0; i < 12; i++) {
				f[i] = Float.parseFloat(t[3 + i]);
			}
			Piece p = new Piece(t[0], Anchor.valueOf(t[1]), t[2].equals("1"), f);
			Piece parent = null;
			for (int i = 15; i < t.length; i++) {
				int eq = t[i].indexOf('=');
				String k = t[i].substring(0, eq);
				String[] val = t[i].substring(eq + 1).split(",");
				switch (k) {
					case "on" -> {
						for (Piece q : pieces) {
							if (q.id.equals(val[0])) {
								parent = q;
							}
						}
					}
					case "n" -> p.n = Integer.parseInt(val[0]);
					case "step" -> {
						p.stepX = Float.parseFloat(val[0]);
						p.stepY = Float.parseFloat(val[1]);
						p.stepZ = Float.parseFloat(val[2]);
					}
					case "bend" -> {
						p.bendX = Float.parseFloat(val[0]);
						p.bendY = Float.parseFloat(val[1]);
						p.bendZ = Float.parseFloat(val[2]);
					}
					case "taper" -> p.taper = Float.parseFloat(val[0]);
					case "wave" -> {
						p.waveAmp = Float.parseFloat(val[0]);
						p.waveAxis = val[1].charAt(0);
						p.waveSpeed = Float.parseFloat(val[2]);
						p.waveLag = Float.parseFloat(val[3]);
					}
					case "ar" -> p.armour = Float.parseFloat(val[0]);
					case "s" -> {
						p.scaleX = Float.parseFloat(val[0]);
						p.scaleY = Float.parseFloat(val[1]);
						p.scaleZ = Float.parseFloat(val[2]);
					}
					default -> {
						// mat: the generator's paint only
					}
				}
			}
			if (parent != null) {
				parent.children.add(p);
			}
			pieces.add(p);
		}
	}

	private static void finish(String key, Glow lights, boolean translucent, Fins fins, List<Piece> pieces) {
		HeritageVariants.Variant variant = HeritageVariants.Variant.byKey(key);
		if (variant == null) {
			JaymosKingdoms.LOGGER.warn("SeaParts: no heritage look called {}", key);
			return;
		}
		pack(pieces);
		List<Piece> roots = new ArrayList<>();
		Map<Piece, Boolean> isChild = new HashMap<>();
		for (Piece p : pieces) {
			for (Piece c : p.children) {
				isChild.put(c, true);
			}
		}
		for (Piece p : pieces) {
			if (!isChild.containsKey(p)) {
				roots.add(p);
			}
		}
		String root = "textures/entity/heritage/parts/sea_" + key.toLowerCase(Locale.ROOT);
		DESIGNS.put(variant, new Design(key, JaymosKingdoms.id(root + ".png"), JaymosKingdoms.id(root + "_glow.png"), lights, translucent, fins,
			List.copyOf(roots)));
	}

	/**
	 * Lays a look's boxes out on its sheet in table order, left to right in rows (a box's unwrap is 2(w+d) wide, d+h tall). The
	 * generator does exactly the same, so never change one without the other.
	 */
	static void pack(List<Piece> pieces) {
		int x = 0, y = 0, row = 0;
		for (Piece p : pieces) {
			int wide = 2 * (p.w + p.d), tall = p.d + p.h;
			if (x + wide > SHEET) {
				x = 0;
				y += row + GAP;
				row = 0;
			}
			p.u = x;
			p.v = y;
			x += wide + GAP;
			row = Math.max(row, tall);
		}
		if (y + row > SHEET) {
			JaymosKingdoms.LOGGER.warn("SeaParts: a look's boxes run off its sheet");
		}
	}
}
