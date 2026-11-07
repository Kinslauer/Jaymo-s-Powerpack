package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.VillagerHeritage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490, Andy: "definetly real modeled parts") The Beastfolk's ears, tails, wings and legs, the Avian's crest and the
 * Sylvan fae's wings and sprigs as real model parts, no longer painted flat on the skin ({@code tools/gen_heritage_looks.py}
 * paints only the fur now). Textures {@code parts/<design>.png} from {@code tools/gen_heritage_parts.py}, one 64x64 sheet per
 * design (the layout is in that script's header; every box here is built to match it). They follow the parent model's head
 * and body pose, on players and on MCA's villagers alike (one layer on every humanoid renderer, drawn on MCA's own skin body
 * when MCA draws it, {@link McaLookModels}).
 * <ul>
 * <li><b>Beastfolk:</b> rounded ears, a tail. <b>Canine:</b> upright pointed ears, a bushy tail. <b>Feline:</b> wide
 * triangular ears, a long thin tail held straight up, tip hooked
 * over, that drops level behind at a sprint and steers the turns. <b>Kitsune:</b> tall fox ears and one to three fox tails (by UUID).</li>
 * <li><b>Avian:</b> feathered wings folded on the back that twitch now and then, a small crest, a stub of tail feathers.
 * <b>Arachnid:</b> four thin spider legs folded off the back and sides (the painted extra eyes stay on the skin).
 * <b>Serpen:</b> a long scaled tail trailing behind.</li>
 * <li><b>Sprite, Faerie:</b> translucent gossamer wings, folded back at rest, spreading and fluttering as they move.
 * <b>Dryad:</b> small branch sprigs with leaves on the head.</li>
 * </ul>
 * Tails wander, curl and slap on their own, only calmed and swung a little by the walk. Ears, crest and sprigs are hidden
 * under a helmet or a hood, tails, wings and legs
 * stand off the back clear of a chestplate or leggings, and everything is hidden under a Powers costume
 * ({@link PowersCover}, as the Tideborn's fins are) and on the invisible. The local player's own first-person view never
 * draws the avatar's layers, so nothing shows over the camera. Parts are baked lazily like the fins and never mutated (a
 * submitted part is drawn later, so all motion is in the pose stack); there is no {@code ModelLayerLocation} to register.
 */
public final class BeastParts {
	// ---- tuning
	/**
	 * (0.7.501, Andy: "they should pulse and slap like they have their own mind") A tail lives on its own clock: a lazy wander
	 * of two slow waves, a curl that comes and goes, and now and then a slap (a few quick whips to the side). Every wave runs on
	 * the age at a fixed rate; the walk only adds a small swing on the stride ({@code walkAnimationPos}) and calms the rest.
	 * (Before, the walk speed sped the wave up: age times a changing rate threw the phase around and the tail thrashed.)
	 */
	static final float WANDER_RATE = 0.06F, WANDER_SLOW = 0.023F, WALK_CALM = 0.5F, STRIDE_SWAY = 0.05F;
	/** The slap: how often its slow gate opens (radians a tick), the share of each beat it stays open, its whip rate and size. */
	static final float SLAP_GATE = 0.031F, SLAP_OPEN = 0.08F, SLAP_RATE = 0.7F, SLAP = 0.3F;
	/** The curl: how often it comes (radians a tick) and how far the later segments roll up at its height. */
	static final float CURL_RATE = 0.04F, CURL = 0.14F;
	/**
	 * A tail with a sprint pose (the feline's): how hard a turn swings it (radians per radian a tick the body turns, signed so
	 * it swings into the turn; flip the sign to swing it out), the most it swings, how fast the sprint pose and the turn ease
	 * in (a tick), and the speed (blocks a tick) that counts as a sprint for those who never set the sprint flag.
	 */
	static final float TAIL_RUDDER = -3.5F, RUDDER_MAX = 0.8F, SPRINT_EASE = 0.2F, TURN_EASE = 0.3F, SPRINT_SPEED = 0.2F;
	/** How much a run lifts a tail (radians off its first bend), and how much it straightens the rest. */
	static final float RUN_LIFT = 0.15F, RUN_STRAIGHTEN = 0.2F;
	/**
	 * (0.7.507) Lying down: the body's tilt where the tail starts to lie along the ground and where it lies flat (cosines of the
	 * tilt from upright: about 45 and 80 degrees), and how much of its curl is straightened then.
	 */
	static final float LIE_START = 0.7F, LIE_FLAT = 0.17F, LIE_FLATTEN = 0.9F;
	/** Where a tail leaves the body: the small of the back (body space), and how far back it starts clear of armour. */
	static final float TAIL_Y = 10.5F, TAIL_Z = 1.7F, ARMOUR_Z = 3.3F;
	/** Wings and legs on the back: how far out they sit, and the added gap under armour. */
	static final float WING_Z = 2.5F, ARMOUR_WING_Z = 3.4F, LEG_X = 3.8F, ARMOUR_LEG_X = 5.3F;
	/** A twitch: how often a wing flutters (radians a tick of the slow wave), and how hard. */
	static final float TWITCH_RATE = 0.045F, TWITCH_THRESHOLD = 0.85F, TWITCH_GAIN = 6.0F, FLUTTER_RATE = 1.1F;

	/** The wings of a design: none, folded feathered ones, or flat gossamer ones. */
	enum Wings { NONE, FOLDED, GOSSAMER }

	/** Ears: tall (three boxes) or short (two); stretched this much across and up, leaning out this much (radians), this far from the middle. */
	record Ears(boolean tall, float wide, float high, float lean, float x) {
	}

	/**
	 * A tail: each segment's bend (radians off the one before; the first off the body: negative droops down and back) and its
	 * thickness in pixels, the length of a segment, how far it sways, and whether it ends in the tip's texture.
	 */
	record Tail(float[] bend, float[] thick, float length, float sway, boolean tip, float @Nullable [] sprint) {
		Tail(float[] bend, float[] thick, float length, float sway, boolean tip) {
			this(bend, thick, length, sway, tip, null);
		}
	}

	/**
	 * One design: its sheet and the parts it has (its ears, its tail, its wings, spider legs, sprigs on the head, a crest of
	 * plumes). {@code glowing}: its sheet has an emissive twin ({@code <key>_glow.png}). Half-clear gossamer wings are blended.
	 */
	enum Design {
		BEASTFOLK("beastfolk", new Ears(false, 1.0F, 1.0F, 0.2F, 2.6F),
			new Tail(new float[] {-0.6F, -0.1F, -0.05F, 0.05F, 0.1F}, new float[] {2.4F, 2.4F, 2.4F, 2.2F, 1.8F}, 3.2F, 0.18F, true), Wings.NONE, false, false,
			false, false),
		// The bushy tail levels out at the end so its tip stays above the feet when standing (0.7.490).
		CANINE("canine", new Ears(true, 0.85F, 1.25F, 0.1F, 2.5F),
			new Tail(new float[] {-0.55F, -0.12F, -0.05F, 0.0F, 0.05F, 0.1F}, new float[] {2.6F, 3.6F, 4.4F, 4.4F, 3.6F, 2.4F}, 3.2F, 0.2F, true), Wings.NONE,
			false, false, false, false),
		// (0.7.501, Andy: "like a cheetah's in sprint and otherwise ... sticking straight up and moving") Carried straight up with
		// the tip hooked over; at a sprint it drops level out behind and steers the turns (TAIL_RUDDER).
		FELINE("feline", new Ears(true, 1.1F, 0.9F, 0.3F, 3.0F),
			new Tail(new float[] {1.05F, 0.08F, 0.04F, 0.0F, -0.04F, 0.12F, 0.3F, 0.45F}, new float[] {1.8F, 1.7F, 1.7F, 1.6F, 1.6F, 1.5F, 1.5F, 1.5F}, 3.4F,
				0.16F, true, new float[] {-0.25F, 0.06F, 0.04F, 0.02F, 0.0F, 0.0F, 0.02F, 0.06F}), Wings.NONE, false, false, false, false),
		KITSUNE("kitsune", new Ears(true, 0.9F, 1.5F, 0.12F, 2.6F),
			new Tail(new float[] {-0.55F, -0.1F, 0.0F, 0.1F, 0.15F, 0.15F}, new float[] {2.2F, 3.4F, 4.0F, 3.6F, 2.8F, 1.8F}, 3.2F, 0.2F, true), Wings.NONE,
			false, false, true, false),
		AVIAN("avian", null, new Tail(new float[] {-0.3F, 0.0F, 0.0F}, new float[] {3.0F, 3.0F, 2.4F}, 3.0F, 0.1F, true), Wings.FOLDED, false, false,
			false, true),
		ARACHNID("arachnid", null, null, Wings.NONE, true, false, false, false),
		SERPEN("serpen", null, new Tail(new float[] {-0.7F, -0.2F, 0.15F, 0.2F, 0.2F, 0.1F, 0.05F, 0.05F, 0.05F},
			new float[] {4.4F, 4.2F, 3.8F, 3.4F, 3.0F, 2.6F, 2.1F, 1.7F, 1.3F}, 3.6F, 0.32F, true), Wings.NONE, false, false, false, false),
		SPRITE("sprite", null, null, Wings.GOSSAMER, false, false, true, false),
		FAERIE("faerie", null, null, Wings.GOSSAMER, false, false, true, false),
		DRYAD("dryad", null, null, Wings.NONE, false, true, false, false);

		final Identifier sheet, glow;
		final @Nullable Ears ears;
		final @Nullable Tail tail;
		final Wings wings;
		final boolean legs, sprigs, hasGlow, crest;

		Design(String key, @Nullable Ears ears, @Nullable Tail tail, Wings wings, boolean legs, boolean sprigs, boolean glowing, boolean crest) {
			this.sheet = JaymosKingdoms.id("textures/entity/heritage/parts/" + key + ".png");
			this.glow = JaymosKingdoms.id("textures/entity/heritage/parts/" + key + "_glow.png");
			this.ears = ears;
			this.tail = tail;
			this.wings = wings;
			this.legs = legs;
			this.sprigs = sprigs;
			this.hasGlow = glowing;
			this.crest = crest;
		}

		static @Nullable Design of(HeritageVariants.@Nullable Variant variant) {
			if (variant == null) {
				return null;
			}
			return switch (variant) {
				case CANINE -> CANINE;
				case FELINE -> FELINE;
				case KITSUNE -> KITSUNE;
				case AVIAN -> AVIAN;
				case ARACHNID -> ARACHNID;
				case SERPEN -> SERPEN;
				case SPRITE -> SPRITE;
				case FAERIE -> FAERIE;
				case DRYAD -> DRYAD;
				default -> null;
			};
		}
	}

	/** On a render state: which design this one wears, and a number from its UUID (a Kitsune's tails, everyone's phase). */
	record Parts(Design design, int seed, float sprint, float turn) {
	}

	/** Per entity, eased toward the live values each frame: sprint (0..1), turn (radians a tick), and when it was last eased. */
	private static final java.util.Map<LivingEntity, float[]> EASED = new java.util.WeakHashMap<>();

	static final RenderStateDataKey<Parts> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:beast_parts");

	private BeastParts() {
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

	/** From the living renderer's extractRenderState: the design a Beastfolk or a fae wears (nothing for anyone else). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		HeritageVariants.Variant variant = HeritageVariants.of(entity);
		Design design = Design.of(variant);
		if (design == null && variant == null) {
			boolean beast = entity instanceof Player player ? Heritage.is(player, Heritage.Kind.BEASTFOLK)
				: VillagerHeritage.of(entity) == Heritage.Kind.BEASTFOLK;
			design = beast ? Design.BEASTFOLK : null;
		}
		if (design == null) {
			state.setData(KEY, null);
			return;
		}
		float sprint = 0.0F, turn = 0.0F;
		if (design.tail != null && design.tail.sprint != null) {
			// eased so the tail drops and rises smoothly and a turn reads as a steady swing, not a tick-by-tick jump
			float now = (float)(System.nanoTime() / 5.0E7D);
			float[] eased = EASED.computeIfAbsent(entity, e -> new float[] {0.0F, 0.0F, now});
			float dt = Mth.clamp(now - eased[2], 0.0F, 5.0F);
			eased[2] = now;
			boolean fast = entity.isSprinting() || Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo) > SPRINT_SPEED;
			float rate = Mth.wrapDegrees(entity.yBodyRot - entity.yBodyRotO) * Mth.DEG_TO_RAD;
			eased[0] += ((fast ? 1.0F : 0.0F) - eased[0]) * (1.0F - (float)Math.exp(-dt * SPRINT_EASE));
			eased[1] += (rate - eased[1]) * (1.0F - (float)Math.exp(-dt * TURN_EASE));
			sprint = eased[0];
			turn = eased[1];
		}
		state.setData(KEY, new Parts(design, HeritageLooks.variant(entity.getUUID(), 997), sprint, turn));
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
			// a costume or the symbiote covers it all; a hood or helmet covers the head's parts
			boolean headCovered = PowersCover.head(state) || !state.headEquipment.isEmpty(), bodyCovered = PowersCover.body(state);
			HumanoidModel<S> model = this.getParentModel();
			HumanoidModel<?> posed = model;
			if (McaCompat.LOADED) {
				// on MCA's skin body, in the pose MCA gives it (the fins and marks do the same)
				HumanoidModel<?> mca = model instanceof PlayerModel player ? McaLookModels.playerMarks(player) : McaLookModels.marks(model);
				if (mca != null) {
					posed = mca;
				}
			}
			boolean armour = !state.chestEquipment.isEmpty() || !state.legsEquipment.isEmpty();
			float run = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
			Ctx ctx = new Ctx(poseStack, collector, lightCoords, parts.design().sheet, parts.design().hasGlow ? parts.design().glow : null,
				parts.design().wings == Wings.GOSSAMER);
			Design design = parts.design();
			float phase = parts.seed() * 0.37F;
			if (!headCovered && posed.head.visible) {
				if (design.ears != null) {
					ears(ctx, posed, design.ears);
				}
				if (design.crest) {
					crest(ctx, posed);
				}
				if (design.sprigs) {
					sprigs(ctx, posed, state.ageInTicks, phase);
				}
			}
			if (bodyCovered || !posed.body.visible) {
				return;
			}
			if (design.tail != null) {
				int tails = design == Design.KITSUNE ? 1 + parts.seed() % 3 : 1;
				for (int i = 0; i < tails; i++) {
					// a fan of tails: spread apart, the outer ones lifted a little, each swaying on its own beat
					float spread = tails == 1 ? 0.0F : (i - (tails - 1) * 0.5F) * 0.55F;
					tail(ctx, posed, design.tail, armour, state.ageInTicks, state.walkAnimationPos, run, parts.sprint(), parts.turn(), spread, tails == 1 ? 0.0F : Math.abs(spread) * 0.3F, phase + i * 1.9F);
				}
			}
			if (design.wings == Wings.FOLDED) {
				foldedWings(ctx, posed, armour, state.ageInTicks, run, phase);
			} else if (design.wings == Wings.GOSSAMER) {
				gossamerWings(ctx, posed, armour, state.ageInTicks, run, phase, design == Design.SPRITE ? 0.75F : 1.1F);
			}
			if (design.legs) {
				legs(ctx, posed, armour, state.ageInTicks, state.walkAnimationPos, run, phase);
			}
		}
	}

	/** What a part is drawn with: the pose stack, the collector, the light, the sheet, its glow twin, and whether it is half-clear. */
	private record Ctx(PoseStack ps, SubmitNodeCollector collector, int light, Identifier sheet, @Nullable Identifier glow, boolean translucent) {
		void draw(ModelPart part) {
			RenderType type = this.translucent ? RenderTypes.entityTranslucent(this.sheet) : RenderTypes.entityCutout(this.sheet);
			this.collector.order(1).submitModelPart(part, this.ps, type, this.light, OverlayTexture.NO_OVERLAY, null, -1);
			if (this.glow != null) {
				this.collector.order(2).submitModelPart(part, this.ps, RenderTypes.eyes(this.glow), this.light, OverlayTexture.NO_OVERLAY, null, -1);
			}
		}
	}

	// ---- head: ears, crest, sprigs (head space: the top of the head at y -8, the front at z -4)

	private static void ears(Ctx ctx, HumanoidModel<?> model, Ears ears) {
		ModelPart base = Geo.ear0(), mid = Geo.ear1(), tip = Geo.ear2();
		for (int side = -1; side <= 1; side += 2) {
			ctx.ps.pushPose();
			model.head.translateAndRotate(ctx.ps);
			ctx.ps.translate(side * ears.x / 16.0F, -7.6F / 16.0F, -1.0F / 16.0F);
			ctx.ps.rotate(Axis.ZP, side * ears.lean);
			ctx.ps.scale(ears.wide, ears.high, 1.0F);
			ctx.draw(base);
			ctx.ps.translate(0.0F, -2.0F / 16.0F, 0.0F);
			ctx.draw(mid);
			if (ears.tall) {
				ctx.ps.translate(0.0F, -2.0F / 16.0F, 0.0F);
				ctx.draw(tip);
			}
			ctx.ps.popPose();
		}
	}

	/** (A small crest: three plumes along the top of the head, swept back, the middle one tallest.) */
	private static void crest(Ctx ctx, HumanoidModel<?> model) {
		ModelPart plume = Geo.crest();
		float[] z = {-0.8F, 0.6F, 2.0F}, tall = {0.8F, 1.0F, 0.7F}, sweep = {-0.35F, -0.55F, -0.8F};
		for (int i = 0; i < 3; i++) {
			ctx.ps.pushPose();
			model.head.translateAndRotate(ctx.ps);
			ctx.ps.translate(0.0F, -7.8F / 16.0F, z[i] / 16.0F);
			ctx.ps.rotate(Axis.XP, sweep[i]);
			ctx.ps.scale(1.4F, tall[i], 1.0F);
			ctx.draw(plume);
			ctx.ps.popPose();
		}
	}

	/** A Dryad's sprigs: a twig up from each side of the crown, forking, leaves at the tips; they sway a little. */
	private static void sprigs(Ctx ctx, HumanoidModel<?> model, float age, float phase) {
		ModelPart trunk = Geo.trunk(), twig = Geo.twig(), leaf = Geo.leaf();
		for (int side = -1; side <= 1; side += 2) {
			float sway = 0.04F * Mth.sin(age * 0.05F + phase + side);
			ctx.ps.pushPose();
			model.head.translateAndRotate(ctx.ps);
			ctx.ps.translate(side * 2.4F / 16.0F, -7.8F / 16.0F, -0.5F / 16.0F);
			ctx.ps.rotate(Axis.ZP, side * 0.3F + sway);
			ctx.draw(trunk);
			ctx.ps.translate(0.0F, -4.0F / 16.0F, 0.0F);
			ctx.draw(leaf);
			for (int fork = 0; fork < 2; fork++) {
				ctx.ps.pushPose();
				ctx.ps.translate(0.0F, 1.5F / 16.0F, 0.0F);
				ctx.ps.rotate(Axis.ZP, side * (fork == 0 ? 0.75F : -0.5F) + sway);
				ctx.ps.rotate(Axis.XP, fork == 0 ? 0.0F : -0.3F);
				ctx.draw(twig);
				ctx.ps.translate(0.0F, -3.0F / 16.0F, 0.0F);
				ctx.draw(leaf);
				ctx.ps.popPose();
			}
			ctx.ps.popPose();
		}
	}

	// ---- body: tails, wings, legs (body space: the box is -4..4 x, 0..12 y, -2..2 z; +z is behind)

	private static void tail(Ctx ctx, HumanoidModel<?> model, Tail tail, boolean armour, float age, float walkPos, float run, float sprint, float turn,
		float yawSpread, float lift, float phase) {
		ModelPart segment = Geo.tail(), tip = Geo.tip();
		boolean steers = tail.sprint != null;
		// a sprint-posed tail is held still and level at speed, so only its own life fades; the walk calms the rest
		float calm = (1.0F - WALK_CALM * run) * (steers ? 1.0F - 0.75F * sprint : 1.0F);
		float slap = slap(age, phase) * calm;
		float curl = 0.5F + 0.5F * Mth.sin(age * CURL_RATE + phase * 1.3F);
		curl = curl * curl * curl * calm;
		ctx.ps.pushPose();
		model.body.translateAndRotate(ctx.ps);
		ctx.ps.translate(0.0F, TAIL_Y / 16.0F, (armour ? ARMOUR_Z : TAIL_Z) / 16.0F);
		// (0.7.507) a body lying down (downed, crawling, executed) lays its tail along the ground toward its feet, not
		// standing up off its back into the air
		float lie = lying(ctx.ps);
		if (steers) {
			// the turn: level at a sprint it swings sideways like a rudder; held up it leans across like an aileron
			float rudder = Mth.clamp(TAIL_RUDDER * turn, -RUDDER_MAX, RUDDER_MAX);
			ctx.ps.rotate(Axis.YP, rudder * sprint);
			ctx.ps.rotate(Axis.ZP, rudder * 0.6F * (1.0F - sprint));
		}
		int n = tail.bend.length;
		for (int i = 0; i < n; i++) {
			float along = n == 1 ? 1.0F : i / (float)(n - 1);
			float bend = steers ? Mth.lerp(sprint, tail.bend[i], tail.sprint[i]) : tail.bend[i];
			float pitch = steers ? bend - (i == 0 ? lift : 0.0F) : i == 0 ? bend - lift - RUN_LIFT * run : bend * (1.0F - RUN_STRAIGHTEN * run);
			pitch = i == 0 ? Mth.lerp(lie, pitch, -Mth.HALF_PI) : pitch * (1.0F - LIE_FLATTEN * lie);
			// a slow pulse down the tail, the curl rolling up its far half, a little bob on the stride
			pitch += 0.04F * Mth.sin(age * 0.08F + i + phase) + CURL * curl * along * (steers ? 1.0F - sprint : 1.0F) + 0.03F * run * Mth.sin(walkPos * 1.3324F - i * 0.5F);
			float wander = 0.6F * Mth.sin(age * WANDER_RATE + phase - i * 0.5F) + 0.4F * Mth.sin(age * WANDER_SLOW + phase * 1.7F - i * 0.3F);
			float yaw = (i == 0 ? yawSpread : 0.0F) + tail.sway * calm * wander
				+ SLAP * slap * Mth.sin(age * SLAP_RATE + phase - i * 0.5F) * (0.4F + 0.6F * along)
				+ STRIDE_SWAY * run * Mth.sin(walkPos * 0.6662F - i * 0.5F);
			ctx.ps.rotate(Axis.XP, pitch);
			ctx.ps.rotate(Axis.YP, yaw);
			ctx.ps.pushPose();
			// the box is 3 x 3 x 4: stretch it to this segment's thickness and length
			float thick = tail.thick[i] / 3.0F;
			ctx.ps.scale(thick, thick, tail.length / 4.0F);
			ctx.draw(tail.tip && i == n - 1 ? tip : segment);
			ctx.ps.popPose();
			ctx.ps.translate(0.0F, 0.0F, tail.length / 16.0F);
		}
		ctx.ps.popPose();
	}

	/** How hard a tail is slapping, 0 most of the time: a slow wave's crest opens it for a moment, eased in and out. */
	static float slap(float age, float phase) {
		float open = Math.max(0.0F, Mth.sin(age * SLAP_GATE + phase * 2.3F) - (1.0F - SLAP_OPEN)) / SLAP_OPEN;
		return open * open * (3.0F - 2.0F * open);
	}

	/**
	 * (0.7.507) How far the body this pose stack is in lies down, from where the world's down points in it: 0 standing (or
	 * leaning up to {@link #LIE_START} from upright), 1 flat on the ground. The world's entity pose stack carries no camera
	 * turn, and a screen's figure stands upright, so this reads the body's own tilt.
	 * <p>
	 * (0.7.532) A screen's y runs down the screen, so a figure standing upright in a screen (the life story, the inventory)
	 * has the stack's down pointing up its body. Only the tilt off the up-down line counts, either way up: before, every
	 * screen figure read as lying flat and its tail hung down between its legs (Andy: "intro animations are broken").
	 */
	static float lying(PoseStack ps) {
		org.joml.Matrix3f m = new org.joml.Matrix3f(ps.last().pose());
		if (Math.abs(m.determinant()) < 1.0E-8F) {
			return 0.0F;
		}
		// the model's own down is +y; how much of the world's down still points that way
		org.joml.Vector3f down = m.invert().transform(new org.joml.Vector3f(0.0F, -1.0F, 0.0F)).normalize();
		return Mth.clamp((LIE_START - Math.abs(down.y)) / (LIE_START - LIE_FLAT), 0.0F, 1.0F);
	}

	/**
	 * A slow wave with a flutter on top now and then: how far a wing is twitching, 0 at rest. The wave rises over the threshold
	 * for a moment every so often (each design and player on its own beat).
	 */
	private static float twitch(float age, float phase, int side) {
		float burst = Math.max(0.0F, Mth.sin(age * TWITCH_RATE + phase) - TWITCH_THRESHOLD) * TWITCH_GAIN;
		return burst * Mth.sin(age * FLUTTER_RATE + side);
	}

	/** Avian wings folded on the back: shoulder, flight feathers and tip hang down along the spine, a little apart. */
	private static void foldedWings(Ctx ctx, HumanoidModel<?> model, boolean armour, float age, float run, float phase) {
		ModelPart cover = Geo.cover(), flight = Geo.flight(), tip = Geo.wingTip();
		for (int side = -1; side <= 1; side += 2) {
			float twitch = twitch(age, phase, side);
			ctx.ps.pushPose();
			model.body.translateAndRotate(ctx.ps);
			ctx.ps.translate(side * 2.3F / 16.0F, 0.8F / 16.0F, (armour ? ARMOUR_WING_Z : WING_Z) / 16.0F);
			// the feather ends lie a little out and back; a run lifts them away, a twitch shakes them
			ctx.ps.rotate(Axis.ZP, -side * (0.12F + 0.3F * run + 0.35F * twitch));
			ctx.ps.rotate(Axis.XP, 0.22F + 0.1F * run);
			ctx.draw(cover);
			ctx.ps.translate(0.0F, 6.0F / 16.0F, 0.0F);
			ctx.ps.rotate(Axis.XP, 0.12F);
			ctx.draw(flight);
			ctx.ps.translate(0.0F, 8.0F / 16.0F, 0.0F);
			ctx.ps.rotate(Axis.XP, 0.1F + 0.2F * Math.abs(twitch));
			ctx.draw(tip);
			ctx.ps.popPose();
		}
	}

	/** Gossamer wings: an upper and a lower lobe a side, swept back at rest, opening and fluttering with speed. */
	private static void gossamerWings(Ctx ctx, HumanoidModel<?> model, boolean armour, float age, float run, float phase, float size) {
		ModelPart upper = Geo.upper(false), lower = Geo.lower(false), upperLeft = Geo.upper(true), lowerLeft = Geo.lower(true);
		for (int side = -1; side <= 1; side += 2) {
			float twitch = twitch(age, phase, side);
			// a gentle breathing flutter all the time, a quick one when they run
			float flap = 0.06F * Mth.sin(age * (0.18F + 0.3F * run) + phase + side) + 0.25F * run * Mth.sin(age * 0.9F + side) + 0.2F * twitch;
			for (int lobe = 0; lobe < 2; lobe++) {
				ctx.ps.pushPose();
				model.body.translateAndRotate(ctx.ps);
				ctx.ps.translate(side * 1.2F / 16.0F, (lobe == 0 ? 2.5F : 3.5F) / 16.0F, (armour ? ARMOUR_WING_Z : WING_Z) / 16.0F);
				// the outer edge swept back (negative turn on the +x side), the wing's tip up (upper) or down (lower)
				float sweep = (lobe == 0 ? 1.0F : 1.25F) - 0.45F * run + flap;
				ctx.ps.rotate(Axis.YP, -side * sweep);
				ctx.ps.rotate(Axis.ZP, -side * (lobe == 0 ? 0.35F : -0.3F));
				ctx.ps.scale(size, size, size);
				ctx.draw(lobe == 0 ? (side > 0 ? upper : upperLeft) : (side > 0 ? lower : lowerLeft));
				ctx.ps.popPose();
			}
		}
	}

	/** Four spider legs, two a side off the back and flanks, folded up and over: a segment up and out, one down. */
	private static void legs(Ctx ctx, HumanoidModel<?> model, boolean armour, float age, float walkPos, float run, float phase) {
		ModelPart segment = Geo.leg();
		float[] y = {2.0F, 6.0F}, yaw = {0.55F, 1.15F};
		for (int side = -1; side <= 1; side += 2) {
			for (int k = 0; k < 2; k++) {
				// legs tap: each pair on its own beat of the walk, a tiny shiver at rest
				float tap = 0.12F * Mth.sin(walkPos * 0.8F + k * Mth.PI + (side > 0 ? 0.0F : 1.2F)) * run + 0.03F * Mth.sin(age * 0.07F + phase + k * 2.0F + side);
				ctx.ps.pushPose();
				model.body.translateAndRotate(ctx.ps);
				ctx.ps.translate(side * (armour ? ARMOUR_LEG_X : LEG_X) / 16.0F, y[k] / 16.0F, 1.8F / 16.0F);
				ctx.ps.rotate(Axis.YP, side * yaw[k]);
				ctx.ps.rotate(Axis.XP, 0.9F + tap);
				ctx.draw(segment);
				ctx.ps.translate(0.0F, 0.0F, 6.0F / 16.0F);
				ctx.ps.rotate(Axis.XP, -1.9F - tap);
				ctx.ps.scale(1.0F, 1.0F, 7.0F / 6.0F);
				ctx.draw(segment);
				ctx.ps.popPose();
			}
		}
	}

	/** The parts' geometry, baked the first time each is drawn; every box is placed to match tools/gen_heritage_parts.py's sheet. */
	private static final class Geo {
		private static @Nullable ModelPart ear0, ear1, ear2, tail, tip, cover, flight, wingTip, crest, trunk, twig, leaf, leg, upper, upperL, lower, lowerL;

		static ModelPart cube(int u, int v, float x, float y, float z, float w, float h, float d, boolean mirror) {
			MeshDefinition mesh = new MeshDefinition();
			mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(u, v).mirror(mirror).addBox(x, y, z, w, h, d), PartPose.ZERO);
			return LayerDefinition.create(mesh, 64, 64).bakeRoot();
		}

		// an ear's three boxes, each growing up (negative y) from the one under it
		static ModelPart ear0() {
			return ear0 != null ? ear0 : (ear0 = cube(0, 0, -1.5F, -2.0F, -0.5F, 3.0F, 2.0F, 1.0F, false));
		}

		static ModelPart ear1() {
			return ear1 != null ? ear1 : (ear1 = cube(0, 3, -1.0F, -2.0F, -0.5F, 2.0F, 2.0F, 1.0F, false));
		}

		static ModelPart ear2() {
			return ear2 != null ? ear2 : (ear2 = cube(0, 6, -0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F, false));
		}

		// a tail segment runs back along +z from its root
		static ModelPart tail() {
			return tail != null ? tail : (tail = cube(0, 16, -1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F, false));
		}

		static ModelPart tip() {
			return tip != null ? tip : (tip = cube(16, 16, -1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F, false));
		}

		// a folded wing hangs down (+y) from the shoulder
		static ModelPart cover() {
			return cover != null ? cover : (cover = cube(0, 24, -1.5F, 0.0F, 0.0F, 3.0F, 6.0F, 1.0F, false));
		}

		static ModelPart flight() {
			return flight != null ? flight : (flight = cube(10, 24, -1.5F, 0.0F, 0.0F, 3.0F, 8.0F, 1.0F, false));
		}

		static ModelPart wingTip() {
			return wingTip != null ? wingTip : (wingTip = cube(20, 24, -1.0F, 0.0F, 0.0F, 2.0F, 3.0F, 1.0F, false));
		}

		static ModelPart crest() {
			return crest != null ? crest : (crest = cube(28, 24, -0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, false));
		}

		static ModelPart trunk() {
			return trunk != null ? trunk : (trunk = cube(34, 24, -0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, false));
		}

		static ModelPart twig() {
			return twig != null ? twig : (twig = cube(40, 24, -0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, false));
		}

		/** A leaf: a flat plane, 3 x 3, standing on its stalk. */
		static ModelPart leaf() {
			return leaf != null ? leaf : (leaf = cube(46, 24, -1.5F, -3.0F, 0.0F, 3.0F, 3.0F, 0.0F, false));
		}

		static ModelPart leg() {
			return leg != null ? leg : (leg = cube(0, 34, -0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 6.0F, false));
		}

		// gossamer wings: flat planes hung from a pivot edge; the -x wing is the mirror of the +x one on the same sheet area
		static ModelPart upper(boolean left) {
			if (left) {
				return upperL != null ? upperL : (upperL = cube(0, 44, -10.0F, -7.0F, 0.0F, 10.0F, 12.0F, 0.0F, true));
			}
			return upper != null ? upper : (upper = cube(0, 44, 0.0F, -7.0F, 0.0F, 10.0F, 12.0F, 0.0F, false));
		}

		static ModelPart lower(boolean left) {
			if (left) {
				return lowerL != null ? lowerL : (lowerL = cube(22, 44, -7.0F, -1.0F, 0.0F, 7.0F, 8.0F, 0.0F, true));
			}
			return lower != null ? lower : (lower = cube(22, 44, 0.0F, -1.0F, 0.0F, 7.0F, 8.0F, 0.0F, false));
		}
	}
}
