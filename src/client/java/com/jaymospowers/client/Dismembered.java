package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.blood.BloodSettings;
import com.jaymospowers.client.mixin.LivingEntityRendererLayersAccessor;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import com.jaymospowers.core.SeveredPart;
import com.jaymospowers.core.blood.BloodParticles;
import com.jaymospowers.core.blood.Bloodied;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Wounds;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Drawing {@link Dismember}: a body is drawn without the parts it's lost, and a {@link SeveredPart} as just that one
 * part (its renderer draws the whole body with only that part showing). Parts are found by name in any model (head,
 * arms, legs, and the hat, sleeves and trousers over them), hidden right after the model animates and shown again as
 * soon as it's drawn, so a model shared by many bodies is never left missing anything. A player who's lost their head
 * sees through it where it fell until they respawn: the view sits in the head and turns with it as it flies, rolls
 * and settles ({@link #headView}), so it can end up sideways or face down in the dirt.
 * <p>
 * Where a part was cut off (a stump or neck on the body, the severed end of a {@link SeveredPart}, the upper half of a
 * waist halving, and Jaymo's Kitchen's corpses), a cross-section is drawn across the cut at every blood setting except
 * Off: meat and a ring of bone (with organs at the waist), or the body's own material for skeletons, golems, slimes,
 * magma creatures, Stoneborn and bloodless Hollows.
 */
public final class Dismembered {
	/** On a living body's render state: the parts it's lost. */
	public static final RenderStateDataKey<Integer> MISSING = RenderStateDataKey.create(() -> "jaymospowers:missing_parts");
	/** On a living body's render state: the bones it has broken (drawn hanging limp). */
	public static final RenderStateDataKey<Integer> BROKEN = RenderStateDataKey.create(() -> "jaymospowers:broken_bones");
	/** A broken limb's droop: an arm a little forward and out from the side, a leg trailing slightly behind and in. */
	private static final float LIMP_ARM_PITCH = -0.1F, LIMP_ARM_ROLL = 0.12F, LIMP_LEG_PITCH = 0.18F, LIMP_LEG_ROLL = -0.05F;
	/** (0.14.5) On a living body's render state: how badly each leg limps, {left, right} (0 sound, 1 broken), and 1 when walking on its feet (else 0). */
	public static final RenderStateDataKey<float[]> GAIT = RenderStateDataKey.create(() -> "jaymospowers:gait");
	/** (0.14.5) How badly a leg limps: broken, broken in a splint (half), and only the foot sprained. */
	private static final float GAIT_BROKEN = 1.0F, GAIT_SPLINTED = 0.5F, GAIT_SPRAINED = 0.5F;
	/** (0.14.5) The limp: a sprained leg's swing cut by this much, the sound leg's by this much, and the body's dip on the bad step (model pixels). */
	private static final float GAIT_SWING_CUT = 0.6F, GAIT_OTHER_CUT = 0.25F, GAIT_DIP = 1.5F;
	/** The walk's stride, as vanilla's humanoid model swings its legs. */
	private static final float GAIT_STRIDE = 0.6662F;
	/** On a severed part's body: the one part to draw (everything else hidden, the part straightened out). */
	public static final RenderStateDataKey<Integer> ONLY = RenderStateDataKey.create(() -> "jaymospowers:only_part");
	/**
	 * On a living body's render state: the lost arms and legs drawn as stumps (no prosthetic on them). Andy's Cruor pick:
	 * a humanoid's lost limb leaves a short stump at the shoulder or hip, its end drawn raw while it bleeds and capped
	 * once it has closed ({@code WoundMarksLayer}); the unarmed start's long-healed stump is capped from the start.
	 */
	public static final RenderStateDataKey<Integer> STUBS = RenderStateDataKey.create(() -> "jaymospowers:stumps");
	/** How long a stump is, as a share of the limb. */
	public static final float STUB = 0.28F;
	private static final int LEGS = Dismember.RIGHT_LEG | Dismember.LEFT_LEG;
	/** Cut-face state on a living body or corpse, and on a severed part's stand-in state. */
	static final RenderStateDataKey<CutState> CUTS = RenderStateDataKey.create(() -> "jaymospowers:cut_faces");
	static final RenderStateDataKey<CutKind> SEVERED_CUT = RenderStateDataKey.create(() -> "jaymospowers:severed_cut_face");

	private static final List<ModelPart> STUBBED = new ArrayList<>();
	private static final List<Float> STUBBED_SCALE = new ArrayList<>();

	private static final Map<ModelPart, Function<String, ModelPart>> LOOKUPS = new WeakHashMap<>();
	private static final List<ModelPart> HIDDEN = new ArrayList<>();
	private static final List<ModelPart> SKIPPED = new ArrayList<>();
	private static @Nullable Entity cameraBefore;

	/** Models built to draw cut cross-sections: limb/neck/socket/severed pass, and torso waist-halving pass. */
	private static final Set<Model<?>> LIMB_CUT_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Set<Model<?>> TORSO_CUT_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<Model<?>, CutRig> CUT_RIGS = Collections.synchronizedMap(new WeakHashMap<>());

	private static final String CUT_NECK = "jaymos_cut_neck";
	private static final String CUT_WAIST = "jaymos_cut_waist";
	private static final String CUT_RIGHT_SHOULDER = "jaymos_cut_right_shoulder";
	private static final String CUT_LEFT_SHOULDER = "jaymos_cut_left_shoulder";
	private static final String CUT_RIGHT_HIP = "jaymos_cut_right_hip";
	private static final String CUT_LEFT_HIP = "jaymos_cut_left_hip";
	private static final String CUT_STUMP = "jaymos_cut_stump";
	private static final String CUT_SEVERED = "jaymos_cut_severed";
	private static final String[] TORSO_NAMES = {"body", "torso", "upper_body", "chest", "abdomen"};
	private static final String[][] QUAD_LEG_FALLBACKS = {
		{}, {"right_front_leg"}, {"left_front_leg"}, {"right_hind_leg"}, {"left_hind_leg"}
	};

	static {
		registerCutFaces();
	}

	private Dismembered() {
	}

	/**
	 * What a body's insides look like across a severed plane: meat and bone (and organs at the waist) in its blood's
	 * palette, or solid material all the way through for bones, golems, slimes, magma, snow, wood, Stoneborn and spirits.
	 */
	public enum CutKind {
		FLESH("flesh", BladeBlood.RED),
		ROTTEN("rotten", BladeBlood.ROTTEN),
		ENDER("ender", BladeBlood.ENDER),
		DARK("dark", BladeBlood.DARK),
		BUG("bug", BladeBlood.BUG),
		SAP("sap", BladeBlood.SAP),
		GOLD("gold", BladeBlood.GOLD),
		BONE("bone", -1),
		WITHER_BONE("wither_bone", -1),
		STONE("stone", -1),
		OBSIDIAN("obsidian", -1),
		IRON("iron", -1),
		COPPER("copper", -1),
		GOO("goo", BladeBlood.SLIME),
		MAGMA("magma", BloodParticles.MAGMA),
		SNOW("snow", -1),
		WOOD("wood", -1),
		SPIRIT("spirit", -1);

		private static final CutKind[] BLEEDING = {FLESH, ROTTEN, ENDER, DARK, BUG, SAP, GOLD, GOO, MAGMA};
		final Identifier limb;
		final Identifier torso;
		final int bloodRgb;

		CutKind(String name, int bloodRgb) {
			this.limb = JaymosPowers.id("textures/entity/wounds/cut_face_" + name + ".png");
			this.torso = JaymosPowers.id("textures/entity/wounds/cut_face_" + name + "_torso.png");
			this.bloodRgb = bloodRgb;
		}

		static CutKind fromBloodColor(int color) {
			if (color == BloodParticles.MAGMA) {
				return MAGMA;
			}
			if (color == BladeBlood.SLIME) {
				return GOO;
			}
			if (color == BladeBlood.ROTTEN) {
				return ROTTEN;
			}
			if (color == BladeBlood.ENDER) {
				return ENDER;
			}
			if (color == BladeBlood.DARK) {
				return DARK;
			}
			if (color == BladeBlood.BUG) {
				return BUG;
			}
			if (color == BladeBlood.SAP || color == BladeBlood.CREEPER) {
				return SAP;
			}
			if (color == BladeBlood.GOLD) {
				return GOLD;
			}
			if (color == BladeBlood.RED) {
				return FLESH;
			}
			int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
			CutKind best = FLESH;
			int bestDist = Integer.MAX_VALUE;
			for (CutKind candidate : BLEEDING) {
				int cr = candidate.bloodRgb >> 16 & 0xFF, cg = candidate.bloodRgb >> 8 & 0xFF, cb = candidate.bloodRgb & 0xFF;
				int dr = r - cr, dg = g - cg, db = b - cb;
				int dist = dr * dr + dg * dg + db * db;
				if (dist < bestDist) {
					bestDist = dist;
					best = candidate;
				}
			}
			return best;
		}
	}

	/** Open cuts on a body or corpse: material, missing parts (less prosthetics/wraps), and which limbs are short stumps. */
	record CutState(CutKind kind, int openMissing, int openStubs) {
		boolean waistHalved() {
			return (this.openMissing & LEGS) == LEGS && (this.openStubs & LEGS) == 0;
		}

		boolean hasLimbPass() {
			int nonWaist = this.waistHalved() ? this.openMissing & ~LEGS : this.openMissing;
			return nonWaist != 0;
		}
	}

	/**
	 * The model parts that make up {@code part}, by name (a part and what's layered over it). After the game's own names
	 * come our creatures' ones: Kingdoms' shape-file creatures (a Digger's "armR", "legL"), Blockbench mobs (a goblin
	 * scout's "arm_right", "leg_left"; the zombie knight's "hi_head"; the revenant's "neck", "armR0", "legL0") and the loot
	 * goblin ("thigh", "thigh2").
	 */
	public static String[] names(int part) {
		return switch (part) {
			case Dismember.HEAD -> new String[] {"head", "hat", "nose", "hi_head", "neck"};
			case Dismember.RIGHT_ARM -> new String[] {"right_arm", "right_sleeve", "armR", "arm_right", "armR0"};
			case Dismember.LEFT_ARM -> new String[] {"left_arm", "left_sleeve", "armL", "arm_left", "armL0"};
			case Dismember.RIGHT_LEG -> new String[] {"right_leg", "right_pants", "legR", "leg_right", "thigh", "legR0"};
			default -> new String[] {"left_leg", "left_pants", "legL", "leg_left", "thigh2", "legL0"};
		};
	}

	/** The model's own part for {@code part}: the first of its {@link #names} the model has, or null. */
	public static @Nullable ModelPart limb(ModelPart root, int part) {
		for (String name : names(part)) {
			ModelPart found = find(root, name);
			if (found != null) {
				return found;
			}
		}
		int idx = partIndex(part);
		for (String fallback : QUAD_LEG_FALLBACKS[idx]) {
			ModelPart found = find(root, fallback);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static int partIndex(int part) {
		return switch (part) {
			case Dismember.HEAD -> 0;
			case Dismember.RIGHT_ARM -> 1;
			case Dismember.LEFT_ARM -> 2;
			case Dismember.RIGHT_LEG -> 3;
			default -> 4;
		};
	}

	public static @Nullable ModelPart find(ModelPart root, String name) {
		if (root.hasChild(name)) {
			return root.getChild(name);
		}
		try {
			return LOOKUPS.computeIfAbsent(root, ModelPart::createPartLookup).apply(name);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public static void extract(LivingEntity entity, EntityRenderState state) {
		int mask = Dismember.missing(entity);
		state.setData(MISSING, mask == 0 ? null : mask);
		int fitted = Prosthetics.fitted(entity);
		int stubs = mask & ~Dismember.HEAD & ~fitted;
		int bound = boundLimbs(entity, stubs);
		boolean showCuts = !BloodSettings.off() && !BloodSettings.streamerSafe();
		// a waist halving (both legs cut off, neither fitted or bound) leaves the upper torso at the waist instead of two thigh stubs
		if (showCuts && (stubs & LEGS) == LEGS && (bound & LEGS) == 0) {
			stubs &= ~LEGS;
		}
		state.setData(STUBS, stubs == 0 ? null : stubs);
		int openMissing = mask & ~fitted & ~bound;
		state.setData(CUTS, showCuts && openMissing != 0 ? new CutState(resolveEntity(entity), openMissing, stubs & ~bound) : null);
		state.setData(SEVERED_CUT, null);
		// (0.14.2) Wounds' broken arms and legs (synced to everyone)
		int broken = Wounds.brokenLimbs(entity);
		state.setData(BROKEN, broken == 0 ? null : broken);
		// (0.14.5) the limp: broken legs and sprained feet (both synced to everyone), half as bad in a splint
		float left = gait(entity, BodyPart.LEFT_LEG, BodyPart.LEFT_FOOT), right = gait(entity, BodyPart.RIGHT_LEG, BodyPart.RIGHT_FOOT);
		boolean walking = entity.onGround() && !entity.isPassenger() && !entity.isInWater() && !Downed.isDowned(entity)
			&& !(entity instanceof Player player && Crawl.crawling(player));
		state.setData(GAIT, left <= 0.0F && right <= 0.0F ? null : new float[] {left, right, walking ? 1.0F : 0.0F});
	}

	/** Which lost limbs in {@code stubs} have been bound in linen or a rag. */
	private static int boundLimbs(LivingEntity entity, int stubs) {
		if (stubs == 0) {
			return 0;
		}
		int wrapped = Bleeding.wrapped(entity);
		if (wrapped == 0) {
			return 0;
		}
		int bound = 0;
		for (BodyPart part : BodyPart.values()) {
			int limb = Breaks.limb(part);
			if (limb != Dismember.HEAD && (stubs & limb) != 0 && (wrapped & 1 << part.ordinal()) != 0) {
				bound |= limb;
			}
		}
		return bound;
	}

	/** (0.14.5) How badly {@code leg} limps: 0 when sound or cut off. */
	private static float gait(LivingEntity entity, BodyPart leg, BodyPart foot) {
		if ((Dismember.missing(entity) & Breaks.limb(leg)) != 0) {
			return 0.0F;
		}
		int broken = Wounds.broken(entity);
		int bit = 1 << leg.ordinal();
		if ((broken & bit) != 0) {
			return (Breaks.splinted(entity) & bit) != 0 ? GAIT_SPLINTED : GAIT_BROKEN;
		}
		return (broken & 1 << foot.ordinal()) != 0 ? GAIT_SPRAINED : 0.0F;
	}

	/** Right after a model animates: the lost parts hidden (or all but the severed one). */
	public static void hide(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState renderState)) {
			return;
		}
		boolean limbCutModel = LIMB_CUT_MODELS.contains(model);
		boolean torsoCutModel = !limbCutModel && TORSO_CUT_MODELS.contains(model);
		if (limbCutModel || torsoCutModel) {
			prepareCutModel(model, renderState, torsoCutModel);
			return;
		}
		ModelPart root = model.root();
		Integer only = renderState.getData(ONLY);
		if (only != null) {
			// Andy: "limbs from dead zombies are constantly floating". Back to rest first, pivots and all, the parts it
			// hangs from too: Fresh Animations (and the like) move pivots as well as turning them, so the part was drawn
			// away from the middle the severed part's renderer puts on the ground, worked out from the rest pose
			for (ModelPart part : root.getAllParts()) {
				part.resetPose();
			}
			// every part but the severed one (and what hangs off it) draws nothing of its own
			Set<ModelPart> keep = Collections.newSetFromMap(new IdentityHashMap<>());
			for (String name : names(only)) {
				ModelPart part = find(root, name);
				if (part != null) {
					keep.add(part);
					keep.addAll(part.getAllParts());
					// straightened out: drawn as it lies
					part.xRot = 0.0F;
					part.yRot = 0.0F;
					part.zRot = 0.0F;
				}
			}
			for (ModelPart part : root.getAllParts()) {
				if (!keep.contains(part) && !part.skipDraw) {
					part.skipDraw = true;
					SKIPPED.add(part);
				}
			}
			return;
		}
		Integer broken = renderState.getData(BROKEN);
		float[] gait = renderState.getData(GAIT);
		if (broken != null) {
			limp(root, broken, gait);
		}
		if (gait != null && model instanceof HumanoidModel<?> && renderState instanceof LivingEntityRenderState living) {
			walk(root, living, gait, broken == null ? 0 : broken);
		}
		Integer missing = renderState.getData(MISSING);
		// streamer safe: living bodies are drawn whole
		if (BloodSettings.streamerSafe()) {
			return;
		}
		// (0.18.82) a wooden or iron limb's own model draws exactly the limb that is missing
		if (missing == null || com.jaymospowers.client.wounds.ProstheticLayer.owns(model)) {
			return;
		}
		Integer stubs = model instanceof HumanoidModel<?> ? renderState.getData(STUBS) : null;
		for (int part : Dismember.PARTS) {
			if ((missing & part) != 0) {
				boolean stub = stubs != null && (stubs & part) != 0;
				for (String name : names(part)) {
					if (stub) {
						stubPart(find(root, name));
					} else {
						hidePart(find(root, name));
					}
				}
			}
		}
	}

	/** Shows only the cross-section faces needed for {@code renderState} on our cut-face model. */
	private static void prepareCutModel(Model<?> model, EntityRenderState renderState, boolean torsoPass) {
		ModelPart root = model.root();
		Integer only = renderState.getData(ONLY);
		if (only != null) {
			for (ModelPart part : root.getAllParts()) {
				part.resetPose();
			}
			hideAllCutFaces(root);
			if (!torsoPass && !BloodSettings.off() && !BloodSettings.streamerSafe()) {
				ModelPart target = limb(root, only);
				if (target != null) {
					target.xRot = 0.0F;
					target.yRot = 0.0F;
					target.zRot = 0.0F;
					showChild(target, CUT_SEVERED);
				}
			}
			return;
		}
		Integer broken = renderState.getData(BROKEN);
		float[] gait = renderState.getData(GAIT);
		if (broken != null) {
			limp(root, broken, gait);
		}
		if (gait != null && model instanceof HumanoidModel<?> && renderState instanceof LivingEntityRenderState living) {
			walk(root, living, gait, broken == null ? 0 : broken);
		}
		hideAllCutFaces(root);
		if (BloodSettings.off() || BloodSettings.streamerSafe()) {
			return;
		}
		CutState cuts = renderState.getData(CUTS);
		if (cuts == null) {
			return;
		}
		ModelPart torso = findTorso(root);
		if (torsoPass) {
			if (cuts.waistHalved() && torso != null) {
				torso.visible = true;
				showChild(torso, CUT_WAIST);
			}
			return;
		}
		int open = cuts.openMissing();
		int stubs = model instanceof HumanoidModel<?> ? cuts.openStubs() : 0;
		if ((open & Dismember.HEAD) != 0 && torso != null) {
			torso.visible = true;
			showChild(torso, CUT_NECK);
		}
		for (int part : new int[] {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG}) {
			if ((open & part) == 0) {
				continue;
			}
			if ((part & LEGS) != 0 && cuts.waistHalved()) {
				continue;
			}
			ModelPart limbPart = limb(root, part);
			if ((stubs & part) != 0 && limbPart != null) {
				if (!limbPart.visible) {
					limbPart.visible = true;
				}
				stubPart(limbPart);
				showChild(limbPart, CUT_STUMP);
			} else if (torso != null) {
				torso.visible = true;
				String socket = switch (part) {
					case Dismember.RIGHT_ARM -> CUT_RIGHT_SHOULDER;
					case Dismember.LEFT_ARM -> CUT_LEFT_SHOULDER;
					case Dismember.RIGHT_LEG -> CUT_RIGHT_HIP;
					default -> CUT_LEFT_HIP;
				};
				showChild(torso, socket);
			}
		}
	}

	private static void hideAllCutFaces(ModelPart root) {
		for (ModelPart part : root.getAllParts()) {
			for (String cut : new String[] {
				CUT_NECK, CUT_WAIST, CUT_RIGHT_SHOULDER, CUT_LEFT_SHOULDER, CUT_RIGHT_HIP, CUT_LEFT_HIP, CUT_STUMP, CUT_SEVERED
			}) {
				if (part.hasChild(cut)) {
					hidePart(part.getChild(cut));
				}
			}
		}
	}

	private static void showChild(ModelPart parent, String childName) {
		if (parent.hasChild(childName)) {
			parent.getChild(childName).visible = true;
		}
	}

	/**
	 * The same on a GeckoLib model (a Kingdoms Imp), whose bones carry the game's names: in this render pass, the lost
	 * parts' bones (and what hangs off them) aren't drawn, or for a severed part only its bones are. Runs as the pass's
	 * last bone update, after the renderer's own.
	 */
	public static void hideGeo(com.geckolib.renderer.base.RenderPassInfo<?> info) {
		if (!((Object)info.renderState() instanceof EntityRenderState renderState)) {
			return;
		}
		Integer only = renderState.getData(ONLY);
		Integer missing = BloodSettings.streamerSafe() ? null : renderState.getData(MISSING);
		if (only == null && missing == null) {
			return;
		}
		info.addBoneUpdater((pass, snapshots) -> {
			com.geckolib.cache.model.BakedGeoModel model = pass.model();
			if (only != null) {
				Set<com.geckolib.cache.model.GeoBone> keep = Collections.newSetFromMap(new IdentityHashMap<>());
				for (String name : names(only)) {
					model.getBone(name).ifPresent(bone -> keepWithChildren(bone, keep));
				}
				for (com.geckolib.cache.model.GeoBone bone : model.boneLookup().get().values()) {
					if (!keep.contains(bone)) {
						snapshots.get(bone).skipRender(true);
					}
				}
				return;
			}
			for (int part : Dismember.PARTS) {
				if ((missing & part) != 0) {
					for (String name : names(part)) {
						snapshots.ifPresent(name, bone -> bone.skipRender(true).skipChildrenRender(true));
					}
				}
			}
		});
	}

	private static void keepWithChildren(com.geckolib.cache.model.GeoBone bone, Set<com.geckolib.cache.model.GeoBone> keep) {
		keep.add(bone);
		for (com.geckolib.cache.model.GeoBone child : bone.children()) {
			keepWithChildren(child, keep);
		}
	}

	/**
	 * Broken limbs (Wounds' broken parts, (0.14.2); before, Fractures') hang limp: an arm dangles at the side, a leg drags
	 * straight, neither swings. (0.14.5) A splinted leg ({@code gait} under 1) only goes halfway there.
	 */
	private static void limp(ModelPart root, int broken, float @Nullable [] gait) {
		for (int part : new int[] {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG}) {
			if ((broken & part) == 0) {
				continue;
			}
			ModelPart limb = limb(root, part);
			if (limb == null) {
				continue;
			}
			boolean right = part == Dismember.RIGHT_ARM || part == Dismember.RIGHT_LEG;
			boolean arm = part == Dismember.RIGHT_ARM || part == Dismember.LEFT_ARM;
			float roll = (right ? 1.0F : -1.0F) * (arm ? LIMP_ARM_ROLL : LIMP_LEG_ROLL);
			if (arm) {
				limb.xRot = LIMP_ARM_PITCH;
				limb.yRot = 0.0F;
				limb.zRot = roll;
				continue;
			}
			float how = gait == null ? 1.0F : Mth.clamp(gait[right ? 1 : 0], 0.0F, 1.0F);
			limb.xRot = Mth.lerp(how, limb.xRot, LIMP_LEG_PITCH);
			limb.yRot = Mth.lerp(how, limb.yRot, 0.0F);
			limb.zRot = Mth.lerp(how, limb.zRot, roll);
		}
	}

	/**
	 * (0.14.5) The limp on the walk, after {@link #limp}: a sprained leg's swing is shortened, the sound leg takes shorter
	 * steps too, and the whole body dips each time its weight comes down on the bad leg. Only on foot (not swimming,
	 * crawling, riding or downed), and only in humanoid models, whose pose is reset every frame, so the dip never builds up.
	 */
	private static void walk(ModelPart root, LivingEntityRenderState state, float[] gait, int broken) {
		float stride = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		if (gait[2] <= 0.0F || stride <= 0.01F) {
			return;
		}
		float dip = 0.0F;
		for (int side = 0; side < 2; side++) {
			boolean right = side == 1;
			int part = right ? Dismember.RIGHT_LEG : Dismember.LEFT_LEG;
			float how = Mth.clamp(gait[side], 0.0F, 1.0F);
			float other = Mth.clamp(gait[1 - side], 0.0F, 1.0F);
			ModelPart leg = find(root, names(part)[0]);
			if (leg != null) {
				if (how > 0.0F && (broken & part) == 0) {
					leg.xRot *= 1.0F - GAIT_SWING_CUT * how;
				} else if (how <= 0.0F && other > 0.0F) {
					leg.xRot *= 1.0F - GAIT_OTHER_CUT * other;
				}
			}
			if (how > 0.0F) {
				// the bad leg's stance: vanilla swings the right leg as cos(step) and the left half a stride behind, so it
				// carries the body while its swing angle is rising (sin falling below zero)
				float step = state.walkAnimationPos * GAIT_STRIDE + (right ? 0.0F : Mth.PI);
				dip += GAIT_DIP * how * stride * Math.max(0.0F, -Mth.sin(step));
			}
		}
		root.y += dip;
	}

	/** Hides {@code part} until the model's drawn ({@link #restore}). */
	public static void hidePart(@Nullable ModelPart part) {
		if (part != null && part.visible) {
			part.visible = false;
			HIDDEN.add(part);
		}
	}

	/** Shortens {@code part} to a stump (about its pivot: the shoulder or the hip) until the model's drawn ({@link #restore}). */
	static void stubPart(@Nullable ModelPart part) {
		if (part != null && part.visible) {
			STUBBED.add(part);
			STUBBED_SCALE.add(part.yScale);
			part.yScale *= STUB;
		}
	}

	/** Once the model's drawn: everything hidden for it shown again. */
	public static void restore() {
		if (!STUBBED.isEmpty()) {
			for (int i = 0; i < STUBBED.size(); i++) {
				STUBBED.get(i).yScale = STUBBED_SCALE.get(i);
			}
			STUBBED.clear();
			STUBBED_SCALE.clear();
		}
		if (!HIDDEN.isEmpty()) {
			for (ModelPart part : HIDDEN) {
				part.visible = true;
			}
			HIDDEN.clear();
		}
		if (!SKIPPED.isEmpty()) {
			for (ModelPart part : SKIPPED) {
				part.skipDraw = false;
			}
			SKIPPED.clear();
		}
	}

	/** The head cam looks out this far ahead of the head's middle (blocks), from just behind the face. */
	private static final float HEAD_CAM_FORWARD = 0.12F;
	/** The severed head's middle sits this high over its entity's feet (as {@link SeveredPartRenderer} draws it). */
	private static final float HEAD_CAM_LIFT = 0.17F;

	/**
	 * The fallen head's view, for the camera, or null when the camera isn't on one of these heads: the head's own
	 * turn as {@link SeveredPartRenderer} draws it (its yaw, then its tumble about a level axis), so a camera given this
	 * rotation looks out of the face and rolls with it. Writes the eye into {@code eye}.
	 */
	public static @Nullable Quaternionf headView(@Nullable Entity camera, float partialTicks, Vector3d eye) {
		if (!(camera instanceof SeveredPart head) || head.part() != Dismember.HEAD) {
			return null;
		}
		float axis = head.spinAxis * Mth.DEG_TO_RAD;
		float spin = Mth.lerp(partialTicks, head.spinO, head.spin) * Mth.DEG_TO_RAD;
		Quaternionf view = new Quaternionf().rotationY(-head.getYRot(partialTicks) * Mth.DEG_TO_RAD)
			.rotateAxis(spin, Mth.cos(axis), 0.0F, Mth.sin(axis));
		Vector3f ahead = new Vector3f(0.0F, 0.0F, -HEAD_CAM_FORWARD).rotate(view);
		eye.set(Mth.lerp(partialTicks, head.xo, head.getX()) + ahead.x,
			Mth.lerp(partialTicks, head.yo, head.getY()) + HEAD_CAM_LIFT + ahead.y,
			Mth.lerp(partialTicks, head.zo, head.getZ()) + ahead.z);
		return view;
	}

	/** Every client tick: a player who's lost their head looks out from it until they're back. */
	public static void tick(Minecraft minecraft) {
		var player = minecraft.player;
		if (player == null || minecraft.level == null) {
			cameraBefore = null;
			return;
		}
		Entity camera = minecraft.getCameraEntity();
		if (player.isAlive()) {
			if (camera instanceof SeveredPart) {
				minecraft.setCameraEntity(cameraBefore != null && !cameraBefore.isRemoved() ? cameraBefore : player);
				cameraBefore = null;
			}
			return;
		}
		if (camera instanceof SeveredPart) {
			if (!camera.isRemoved()) {
				return;
			}
			// (the head they were looking out of was taken, as a killer's trophy: back to their own view)
			minecraft.setCameraEntity(cameraBefore != null && !cameraBefore.isRemoved() ? cameraBefore : player);
			cameraBefore = null;
			camera = minecraft.getCameraEntity();
		}
		UUID self = player.getUUID();
		AABB around = player.getBoundingBox().inflate(48.0);
		for (SeveredPart head : minecraft.level.getEntitiesOfClass(SeveredPart.class, around,
			p -> p.part() == Dismember.HEAD && p.owner().map(self::equals).orElse(false))) {
			cameraBefore = camera;
			minecraft.setCameraEntity(head);
			return;
		}
	}

	// ---- Cut-face registration, resolution and rendering -------------------------------------------------------------

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerCutFaces() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> helper.register((RenderLayer) new CutLayer(renderer)));
		hookCorpses();
	}

	/** Marks a severed part's render state with the cross-section material of the body it came off. */
	static CutKind markSevered(SeveredPart part, @Nullable Entity body, @Nullable EntityRenderState bodyState) {
		CutKind kind = resolveSevered(part, body);
		if (bodyState != null) {
			bodyState.setData(CUTS, null);
			bodyState.setData(SEVERED_CUT, BloodSettings.off() || BloodSettings.streamerSafe() ? null : kind);
		}
		return kind;
	}

	/** Submits the severed-end cross-section for a plain player {@link SeveredPart}. */
	static void submitSeveredPlayer(CutKind kind, PlayerModel model, AvatarRenderState avatar, PoseStack poseStack,
		SubmitNodeCollector collector, int light) {
		if (BloodSettings.off() || BloodSettings.streamerSafe()) {
			return;
		}
		CutRig rig = CUT_RIGS.computeIfAbsent(model, CutRig::build);
		if (rig.hasAny) {
			collector.order(2).submitModel(rig.limbModel, avatar, poseStack, RenderTypes.entityCutout(kind.limb), light,
				OverlayTexture.NO_OVERLAY, avatar.outlineColor);
		}
	}

	/** Submits a severed-end cross-section for a renderer that has no {@link ModelPart} tree (a GeckoLib Imp). */
	static void submitLooseSevered(CutKind kind, int part, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		if (BloodSettings.off() || BloodSettings.streamerSafe()) {
			return;
		}
		boolean head = part == Dismember.HEAD;
		float half = head ? 0.14F : 0.12F;
		float y = head ? -0.22F : 0.19F;
		float ny = head ? -1.0F : 1.0F;
		collector.order(2).submitCustomGeometry(poseStack, RenderTypes.entityCutout(kind.limb), (pose, buffer) -> {
			Matrix4f mat = pose.pose();
			Vector3f n = pose.transformNormal(0.0F, ny, 0.0F, new Vector3f());
			if (head) {
				buffer.addVertex(mat, half, y, -half).setColor(-1).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
				buffer.addVertex(mat, -half, y, -half).setColor(-1).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
				buffer.addVertex(mat, -half, y, half).setColor(-1).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
				buffer.addVertex(mat, half, y, half).setColor(-1).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
			} else {
				buffer.addVertex(mat, half, y, half).setColor(-1).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
				buffer.addVertex(mat, -half, y, half).setColor(-1).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
				buffer.addVertex(mat, -half, y, -half).setColor(-1).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
				buffer.addVertex(mat, half, y, -half).setColor(-1).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
			}
		});
	}

	static final class CutLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
		private final LivingEntityRenderer<?, S, M> renderer;

		CutLayer(LivingEntityRenderer<?, S, M> renderer) {
			super(renderer);
			this.renderer = renderer;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			if (state.isInvisible || BloodSettings.off() || BloodSettings.streamerSafe()) {
				return;
			}
			Integer only = state.getData(ONLY);
			if (only != null) {
				CutKind severed = state.getData(SEVERED_CUT);
				if (severed == null) {
					return;
				}
				Model<?> target = McaRef.activeModel(this.renderer, state, this.getParentModel());
				CutRig rig = CUT_RIGS.computeIfAbsent(target, CutRig::build);
				if (rig.hasAny) {
					int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
					collector.order(2).submitModel(rig.limbModel, state, poseStack, RenderTypes.entityCutout(severed.limb), lightCoords,
						overlay, state.outlineColor);
				}
				return;
			}
			CutState cuts = state.getData(CUTS);
			if (cuts == null) {
				return;
			}
			Model<?> target = McaRef.activeModel(this.renderer, state, this.getParentModel());
			CutRig rig = CUT_RIGS.computeIfAbsent(target, CutRig::build);
			if (!rig.hasAny) {
				return;
			}
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
			if (cuts.hasLimbPass()) {
				collector.order(2).submitModel(rig.limbModel, state, poseStack, RenderTypes.entityCutout(cuts.kind().limb), lightCoords,
					overlay, state.outlineColor);
			}
			if (cuts.waistHalved() && rig.hasTorso) {
				collector.order(2).submitModel(rig.torsoModel, state, poseStack, RenderTypes.entityCutout(cuts.kind().torso), lightCoords,
					overlay, state.outlineColor);
			}
		}
	}

	// ---- Kitchen corpse integration (by reflection, matching BloodCoatLayer) -----------------------------------------

	private static @Nullable RenderStateDataKey<Integer> kitchenMissing;
	private static @Nullable Method corpseMissing, corpseIsPlayer, corpseOwner, corpseLookTags, corpseLook, corpseClientBody;

	@SuppressWarnings("unchecked")
	private static void hookCorpses() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		try {
			Class<?> dressing = Class.forName("com.jaymoskitchen.client.corpse.CorpseDressing");
			((List<BiConsumer<Entity, EntityRenderState>>) dressing.getField("BODY").get(null)).add(Dismembered::corpseBody);
			((List<BiConsumer<Entity, AvatarRenderState>>) dressing.getField("EXTRACT").get(null)).add(Dismembered::corpseBody);
			((List<BiConsumer<AvatarRenderState, Object[]>>) dressing.getField("SUBMIT").get(null)).add(Dismembered::submitPlayerCorpse);
			kitchenMissing = (RenderStateDataKey<Integer>) Class.forName("com.jaymoskitchen.client.corpse.Severed").getField("KEY").get(null);
			Class<?> corpseCls = Class.forName("com.jaymoskitchen.corpse.CorpseEntity");
			corpseMissing = corpseCls.getMethod("missing");
			corpseIsPlayer = corpseCls.getMethod("isPlayerCorpse");
			corpseOwner = corpseCls.getMethod("owner");
			corpseLookTags = corpseCls.getMethod("lookTags");
			corpseLook = corpseCls.getMethod("look");
			corpseClientBody = corpseCls.getMethod("clientBody", float.class);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosPowers.LOGGER.warn("Jaymo's Kitchen has no corpse cut-face hook (older Kitchen?): corpses skip cut cross-sections", e);
		}
	}

	private static void corpseBody(Entity corpse, EntityRenderState state) {
		state.setData(SEVERED_CUT, null);
		if (BloodSettings.off() || BloodSettings.streamerSafe()) {
			state.setData(CUTS, null);
			return;
		}
		int missing = corpseMissingBits(corpse, state);
		if (missing == 0) {
			state.setData(CUTS, null);
			return;
		}
		state.setData(CUTS, new CutState(resolveCorpse(corpse), missing, 0));
	}

	private static int corpseMissingBits(Entity corpse, EntityRenderState state) {
		if (corpseMissing != null) {
			try {
				if (corpseMissing.invoke(corpse) instanceof Integer m) {
					return m;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall back to Severed.KEY
			}
		}
		Integer severed = kitchenMissing == null ? null : state.getData(kitchenMissing);
		return severed == null ? 0 : severed;
	}

	private static void submitPlayerCorpse(AvatarRenderState avatar, Object[] args) {
		if (BloodSettings.off() || BloodSettings.streamerSafe()) {
			return;
		}
		CutState cuts = avatar.getData(CUTS);
		if (cuts == null || args.length < 4 || !(args[0] instanceof PlayerModel body) || !(args[1] instanceof PoseStack poseStack)
			|| !(args[2] instanceof SubmitNodeCollector collector) || !(args[3] instanceof Integer light)) {
			return;
		}
		CutRig rig = CUT_RIGS.computeIfAbsent(body, CutRig::build);
		if (!rig.hasAny) {
			return;
		}
		if (cuts.hasLimbPass()) {
			collector.order(2).submitModel(rig.limbModel, avatar, poseStack, RenderTypes.entityCutout(cuts.kind().limb), light,
				OverlayTexture.NO_OVERLAY, avatar.outlineColor);
		}
		if (cuts.waistHalved() && rig.hasTorso) {
			collector.order(2).submitModel(rig.torsoModel, avatar, poseStack, RenderTypes.entityCutout(cuts.kind().torso), light,
				OverlayTexture.NO_OVERLAY, avatar.outlineColor);
		}
	}

	// ---- Material resolution (heritages, tags, bloodless materials, blood colours, SNBT) -----------------------------

	private static final boolean KINGDOMS = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
	private static boolean kingdomsLooked;
	private static @Nullable AttachmentType<String> heritageAttachment;
	private static @Nullable AttachmentType<String> variantAttachment;

	@SuppressWarnings("unchecked")
	private static void ensureKingdoms() {
		if (kingdomsLooked || !KINGDOMS) {
			return;
		}
		kingdomsLooked = true;
		try {
			heritageAttachment = (AttachmentType<String>) Class.forName("com.jaymoskingdoms.Heritage").getField("HERITAGE").get(null);
			variantAttachment = (AttachmentType<String>) Class.forName("com.jaymoskingdoms.HeritageVariants").getField("VARIANT").get(null);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			heritageAttachment = null;
			variantAttachment = null;
		}
	}

	private static @Nullable String heritageKey(Entity entity) {
		ensureKingdoms();
		if (heritageAttachment != null) {
			String attached = entity.getAttached(heritageAttachment);
			if (attached != null && !attached.isEmpty()) {
				return attached;
			}
		}
		for (String tag : entity.entityTags()) {
			if (tag.startsWith("jaymoskingdoms_heritage_")) {
				return tag.substring("jaymoskingdoms_heritage_".length());
			}
		}
		return null;
	}

	private static @Nullable String variantKey(Entity entity) {
		ensureKingdoms();
		if (variantAttachment != null) {
			return entity.getAttached(variantAttachment);
		}
		return null;
	}

	private static @Nullable CutKind fromHeritage(@Nullable String heritage, @Nullable String variant) {
		if (heritage == null || heritage.isEmpty()) {
			return null;
		}
		String h = heritage.toLowerCase(Locale.ROOT);
		String v = variant == null ? "" : variant.toLowerCase(Locale.ROOT);
		return switch (h) {
			case "stoneborn" -> "magmavein".equals(v) ? CutKind.MAGMA : "obsidian".equals(v) ? CutKind.OBSIDIAN : CutKind.STONE;
			case "emberborn" -> CutKind.MAGMA;
			case "hollow" -> "skeleton".equals(v) ? CutKind.BONE : "zombie".equals(v) ? CutKind.ROTTEN : CutKind.SPIRIT;
			case "goblin" -> "slimeborn".equals(v) ? CutKind.GOO : null;
			case "divine" -> CutKind.GOLD;
			case "verdant", "biobuilt" -> CutKind.SAP;
			case "endborn" -> CutKind.ENDER;
			default -> null;
		};
	}

	static CutKind resolveEntity(LivingEntity entity) {
		CutKind byHeritage = fromHeritage(heritageKey(entity), variantKey(entity));
		if (byHeritage != null) {
			return byHeritage;
		}
		if (!BladeBlood.bleeds(entity)) {
			return resolveBloodless(entity, entity.getType());
		}
		return CutKind.fromBloodColor(BladeBlood.color(entity));
	}

	private static CutKind resolveBloodless(@Nullable LivingEntity entity, EntityType<?> type) {
		var holder = type.builtInRegistryHolder();
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().toLowerCase(Locale.ROOT);
		if (entity instanceof WitherSkeleton || entity instanceof WitherBoss || path.contains("wither")) {
			return CutKind.WITHER_BONE;
		}
		if (holder.is(EntityTypeTags.SKELETONS) || entity instanceof AbstractSkeleton || entity instanceof SkeletonHorse
			|| hasWord(path, "skeleton", "skull", "bones")) {
			return CutKind.BONE;
		}
		if (holder.is(BladeBlood.METAL_BODIES) || entity instanceof IronGolem
			|| hasWord(path, "iron_golem", "copper_golem", "clockwork", "robot", "mech", "android", "drone", "automaton", "war_barrel")) {
			return path.contains("copper") || path.contains("clockwork") || path.contains("brass") || path.contains("bronze")
				? CutKind.COPPER : CutKind.IRON;
		}
		if (entity instanceof MagmaCube || entity instanceof Blaze || holder.is(BladeBlood.MAGMA_BLOOD)
			|| hasWord(path, "magma", "lava", "blaze", "ember")) {
			return CutKind.MAGMA;
		}
		if (entity instanceof Slime || holder.is(BladeBlood.SLIME_BLOOD) || hasWord(path, "slime", "ooze", "jelly", "blob")) {
			return CutKind.GOO;
		}
		if (entity instanceof SnowGolem || hasWord(path, "snow", "ice", "frost", "glacial")) {
			return CutKind.SNOW;
		}
		if (entity instanceof Creaking || hasWord(path, "creaking", "wood", "tree", "ent", "treant", "plank", "bark")) {
			return CutKind.WOOD;
		}
		if (hasWord(path, "obsidian", "blackstone", "deepslate")) {
			return CutKind.OBSIDIAN;
		}
		if (hasWord(path, "stone", "rock", "cobble", "tuff", "granite", "diorite", "andesite", "terracotta", "clay", "golem")) {
			return CutKind.STONE;
		}
		return CutKind.SPIRIT;
	}

	private static boolean hasWord(String path, String... words) {
		for (String segment : path.split("[_./-]")) {
			for (String word : words) {
				if (segment.equals(word) || word.length() > 4 && segment.startsWith(word)) {
					return true;
				}
			}
		}
		return false;
	}

	private static CutKind resolveSevered(SeveredPart part, @Nullable Entity body) {
		if (part.owner().isPresent() && part.level() != null) {
			Player live = part.level().getPlayerByUUID(part.owner().get());
			if (live != null) {
				return resolveEntity(live);
			}
		}
		if (body instanceof LivingEntity living) {
			CutKind fromSnbt = resolveSnbtHeritage(part.look());
			return fromSnbt != null ? fromSnbt : resolveEntity(living);
		}
		CutKind fromLook = resolveSnbt(part.look());
		if (fromLook != null) {
			return fromLook;
		}
		Bloodied.Blood blood = Bloodied.corpseBlood(part);
		return blood != null ? CutKind.fromBloodColor(blood.color()) : CutKind.FLESH;
	}

	@SuppressWarnings("unchecked")
	private static CutKind resolveCorpse(Entity corpse) {
		try {
			boolean playerCorpse = corpseIsPlayer != null && Boolean.TRUE.equals(corpseIsPlayer.invoke(corpse));
			if (playerCorpse) {
				Optional<UUID> owner = corpseOwner != null ? (Optional<UUID>) corpseOwner.invoke(corpse) : Optional.empty();
				String tagHeritage = null;
				if (corpseLookTags != null && corpseLookTags.invoke(corpse) instanceof List<?> tags) {
					for (Object item : tags) {
						if (item instanceof String s && s.startsWith("jaymoskingdoms_heritage_")) {
							tagHeritage = s.substring("jaymoskingdoms_heritage_".length());
						} else if (item instanceof String s && s.startsWith("jaymoskingdoms_bio_")) {
							return CutKind.SAP;
						}
					}
				}
				String variant = null;
				if (owner.isPresent() && corpse.level() != null) {
					Player live = corpse.level().getPlayerByUUID(owner.get());
					if (live != null) {
						if (tagHeritage == null) {
							return resolveEntity(live);
						}
						if (tagHeritage.equals(heritageKey(live))) {
							variant = variantKey(live);
						}
					}
				}
				Entity mca = owner.isPresent() && FabricLoader.getInstance().isModLoaded("jaymoskitchen")
					? com.jaymoskitchen.client.corpse.McaLooks.of(owner) : null;
				if (mca instanceof LivingEntity mcaLiving) {
					if (tagHeritage == null) {
						return resolveEntity(mcaLiving);
					}
					if (variant == null && tagHeritage.equals(heritageKey(mcaLiving))) {
						variant = variantKey(mcaLiving);
					}
				}
				CutKind byTag = fromHeritage(tagHeritage, variant);
				if (byTag != null) {
					return byTag;
				}
			} else if (corpseClientBody != null && corpseClientBody.invoke(corpse, 0.0F) instanceof LivingEntity body) {
				CutKind fromLook = corpseLook != null && corpseLook.invoke(corpse) instanceof String s ? resolveSnbtHeritage(s) : null;
				return fromLook != null ? fromLook : resolveEntity(body);
			}
			if (corpseLook != null && corpseLook.invoke(corpse) instanceof String lookStr) {
				CutKind parsed = resolveSnbt(lookStr);
				if (parsed != null) {
					return parsed;
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// fall back to corpse blood
		}
		Bloodied.Blood blood = Bloodied.corpseBlood(corpse);
		return blood != null ? CutKind.fromBloodColor(blood.color()) : CutKind.FLESH;
	}

	private static @Nullable CutKind resolveSnbtHeritage(String snbt) {
		if (snbt == null || snbt.isEmpty() || !snbt.contains("heritage")) {
			return null;
		}
		try {
			CompoundTag tag = TagParser.parseCompoundFully(snbt);
			String heritage = tag.getStringOr("jaymoskingdoms_heritage", "");
			String variant = tag.getStringOr("jaymoskingdoms_heritage_variant", "");
			if (tag.contains("fabric:attachments")) {
				CompoundTag att = tag.getCompoundOrEmpty("fabric:attachments");
				if (heritage.isEmpty()) {
					heritage = att.getStringOr("jaymoskingdoms:heritage", "");
				}
				if (variant.isEmpty()) {
					variant = att.getStringOr("jaymoskingdoms:heritage_variant", "");
				}
			}
			return fromHeritage(heritage, variant);
		} catch (Exception e) {
			return null;
		}
	}

	private static @Nullable CutKind resolveSnbt(String snbt) {
		if (snbt == null || snbt.isEmpty()) {
			return null;
		}
		CutKind byHeritage = resolveSnbtHeritage(snbt);
		if (byHeritage != null) {
			return byHeritage;
		}
		try {
			CompoundTag tag = TagParser.parseCompoundFully(snbt);
			Identifier id = Identifier.tryParse(tag.getStringOr("id", ""));
			Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
			if (type.isPresent()) {
				return BladeBlood.bleeds(type.get()) ? CutKind.fromBloodColor(BladeBlood.color(type.get())) : resolveBloodless(null, type.get());
			}
		} catch (Exception e) {
			// fall back
		}
		return null;
	}

	// ---- MCA active model lookup (safe with or without MCA installed) ------------------------------------------------

	private static final class McaRef {
		private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("mca");
		private static final Map<Object, @Nullable Model<?>> SKIN_MODELS = Collections.synchronizedMap(new WeakHashMap<>());
		private static @Nullable Method isActive;
		private static @Nullable Class<?> skinLayerClass;
		private static @Nullable Field layerModelField;
		private static boolean broken;

		static Model<?> activeModel(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, Model<?> fallback) {
			if (!LOADED || broken || !(state instanceof HumanoidRenderState)) {
				return fallback;
			}
			try {
				if (isActive == null) {
					Class<?> holder = Class.forName("net.conczin.mca.client.render.VillagerStateHolder");
					if (!holder.isInstance(state)) {
						return fallback;
					}
					isActive = holder.getMethod("mca$isVillagerRendererActive");
					skinLayerClass = Class.forName("net.conczin.mca.client.render.layer.SkinLayer");
					layerModelField = Class.forName("net.conczin.mca.client.render.layer.VillagerLayer").getField("model");
				}
				if (!isActive.getDeclaringClass().isInstance(state) || !Boolean.TRUE.equals(isActive.invoke(state))) {
					return fallback;
				}
				Model<?> mcaModel = SKIN_MODELS.computeIfAbsent(renderer, r -> {
					for (RenderLayer<?, ?> layer : ((LivingEntityRendererLayersAccessor) r).jaymospowers$layers()) {
						if (skinLayerClass != null && skinLayerClass.isInstance(layer) && layerModelField != null) {
							try {
								if (layerModelField.get(layer) instanceof Model<?> found) {
									return found;
								}
							} catch (IllegalAccessException ignored) {
							}
						}
					}
					return null;
				});
				return mcaModel != null ? mcaModel : fallback;
			} catch (Throwable e) {
				broken = true;
				return fallback;
			}
		}
	}

	// ---- Cut-face model geometry (built once per entity model from its own cube bounds) ------------------------------

	private static @Nullable ModelPart findTorso(ModelPart root) {
		for (String name : TORSO_NAMES) {
			ModelPart found = find(root, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private record Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
		float width() {
			return this.maxX - this.minX;
		}

		float height() {
			return this.maxY - this.minY;
		}

		float depth() {
			return this.maxZ - this.minZ;
		}

		float midX() {
			return (this.minX + this.maxX) * 0.5F;
		}

		float midZ() {
			return (this.minZ + this.maxZ) * 0.5F;
		}
	}

	private static @Nullable Bounds boundsOf(@Nullable ModelPart part) {
		if (part == null) {
			return null;
		}
		ModelPart source = part;
		if (source.isEmpty()) {
			for (ModelPart child : part.getAllParts()) {
				if (child != part && !child.isEmpty()) {
					source = child;
					break;
				}
			}
		}
		if (source.isEmpty()) {
			return null;
		}
		ModelPart.Cube cube = source.getRandomCube(RandomSource.create(0L));
		return new Bounds(cube.minX, cube.maxX, cube.minY, cube.maxY, cube.minZ, cube.maxZ);
	}

	private static ModelPart quadPart(Direction facing, float minX, float maxX, float minY, float maxY, float minZ, float maxZ,
		float u0, float v0, float u1, float v1) {
		ModelPart.Cube cube = new ModelPart.Cube(0, 0, minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ, 0.0F, 0.0F, 0.0F,
			false, 16.0F, 16.0F, Set.of(facing));
		ModelPart.Vertex[] v = switch (facing) {
			case DOWN -> new ModelPart.Vertex[] {
				new ModelPart.Vertex(maxX, minY, maxZ, 0.0F, 0.0F),
				new ModelPart.Vertex(minX, minY, maxZ, 0.0F, 0.0F),
				new ModelPart.Vertex(minX, minY, minZ, 0.0F, 0.0F),
				new ModelPart.Vertex(maxX, minY, minZ, 0.0F, 0.0F)
			};
			case UP -> new ModelPart.Vertex[] {
				new ModelPart.Vertex(maxX, maxY, minZ, 0.0F, 0.0F),
				new ModelPart.Vertex(minX, maxY, minZ, 0.0F, 0.0F),
				new ModelPart.Vertex(minX, maxY, maxZ, 0.0F, 0.0F),
				new ModelPart.Vertex(maxX, maxY, maxZ, 0.0F, 0.0F)
			};
			case WEST -> new ModelPart.Vertex[] {
				new ModelPart.Vertex(minX, minY, minZ, 0.0F, 0.0F),
				new ModelPart.Vertex(minX, minY, maxZ, 0.0F, 0.0F),
				new ModelPart.Vertex(minX, maxY, maxZ, 0.0F, 0.0F),
				new ModelPart.Vertex(minX, maxY, minZ, 0.0F, 0.0F)
			};
			default -> new ModelPart.Vertex[] {
				new ModelPart.Vertex(maxX, minY, maxZ, 0.0F, 0.0F),
				new ModelPart.Vertex(maxX, minY, minZ, 0.0F, 0.0F),
				new ModelPart.Vertex(maxX, maxY, minZ, 0.0F, 0.0F),
				new ModelPart.Vertex(maxX, maxY, maxZ, 0.0F, 0.0F)
			};
		};
		cube.polygons[0] = new ModelPart.Polygon(v, u0, v0, u1, v1, 16.0F, 16.0F, false, facing);
		return new ModelPart(List.of(cube), Map.of());
	}

	private static final class CutRig {
		final Model<EntityRenderState> limbModel;
		final Model<EntityRenderState> torsoModel;
		final boolean hasAny;
		final boolean hasTorso;

		private CutRig(Model<EntityRenderState> limbModel, Model<EntityRenderState> torsoModel, boolean hasAny, boolean hasTorso) {
			this.limbModel = limbModel;
			this.torsoModel = torsoModel;
			this.hasAny = hasAny;
			this.hasTorso = hasTorso;
		}

		static CutRig build(Model<?> parent) {
			Built limbBuilt = buildTree(parent, false);
			Built torsoBuilt = buildTree(parent, true);
			Model<EntityRenderState> limb = wrapModel(parent, limbBuilt);
			Model<EntityRenderState> torso = wrapModel(parent, torsoBuilt);
			LIMB_CUT_MODELS.add(limb);
			TORSO_CUT_MODELS.add(torso);
			return new CutRig(limb, torso, limbBuilt.hasQuads || torsoBuilt.hasQuads, torsoBuilt.hasQuads);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		private static Model<EntityRenderState> wrapModel(Model<?> parent, Built built) {
			if (parent instanceof HumanoidModel<?> humanoid) {
				return (Model) new HumanoidCutModel(built.root, humanoid, built.sources, built.targets);
			}
			return new GeneralCutModel(built.root, parent, built.sources, built.targets);
		}
	}

	private record Built(ModelPart root, ModelPart[] sources, ModelPart[] targets, boolean hasQuads) {
	}

	private static final class NodeSpec {
		final ModelPart source;
		final Map<String, NodeSpec> children = new LinkedHashMap<>();
		final Map<String, ModelPart> extraLeaves = new LinkedHashMap<>();

		NodeSpec(ModelPart source) {
			this.source = source;
		}
	}

	private static Built buildTree(Model<?> parent, boolean torsoOnly) {
		ModelPart parentRoot = parent.root();
		List<ModelPart> all = parentRoot.getAllParts();
		Map<ModelPart, String> namesByPart = new IdentityHashMap<>();
		namesByPart.put(parentRoot, "root");
		if (parent instanceof HumanoidModel<?> h) {
			namesByPart.put(h.head, "head");
			namesByPart.put(h.hat, "hat");
			namesByPart.put(h.body, "body");
			namesByPart.put(h.rightArm, "right_arm");
			namesByPart.put(h.leftArm, "left_arm");
			namesByPart.put(h.rightLeg, "right_leg");
			namesByPart.put(h.leftLeg, "left_leg");
		}
		for (String torsoName : TORSO_NAMES) {
			ModelPart p = find(parentRoot, torsoName);
			if (p != null) {
				namesByPart.putIfAbsent(p, torsoName);
			}
		}
		for (int part : Dismember.PARTS) {
			for (String name : names(part)) {
				ModelPart p = find(parentRoot, name);
				if (p != null) {
					namesByPart.putIfAbsent(p, name);
				}
			}
			for (String fallback : QUAD_LEG_FALLBACKS[partIndex(part)]) {
				ModelPart p = find(parentRoot, fallback);
				if (p != null) {
					namesByPart.putIfAbsent(p, fallback);
				}
			}
		}

		Map<ModelPart, NodeSpec> specs = new IdentityHashMap<>();
		NodeSpec rootSpec = new NodeSpec(parentRoot);
		specs.put(parentRoot, rootSpec);
		int synthetic = 0;

		for (Map.Entry<ModelPart, String> entry : new ArrayList<>(namesByPart.entrySet())) {
			ModelPart target = entry.getKey();
			if (target == parentRoot) {
				continue;
			}
			List<ModelPart> chain = new ArrayList<>();
			for (ModelPart candidate : all) {
				if (candidate.getAllParts().contains(target)) {
					chain.add(candidate);
				}
			}
			chain.sort(Comparator.comparingInt((ModelPart p) -> p.getAllParts().size()).reversed());
			for (int i = 1; i < chain.size(); i++) {
				ModelPart prev = chain.get(i - 1);
				ModelPart curr = chain.get(i);
				NodeSpec parentNode = specs.computeIfAbsent(prev, NodeSpec::new);
				NodeSpec currNode = specs.get(curr);
				if (currNode == null) {
					currNode = new NodeSpec(curr);
					specs.put(curr, currNode);
					String childKey = namesByPart.get(curr);
					if (childKey == null || parentNode.children.containsKey(childKey)) {
						childKey = "chain_" + synthetic++;
					}
					parentNode.children.put(childKey, currNode);
				}
			}
		}

		boolean hasQuads = false;
		ModelPart torsoPart = findTorso(parentRoot);
		Bounds tb = boundsOf(torsoPart);
		NodeSpec torsoSpec = torsoPart == null ? null : specs.get(torsoPart);
		if (tb != null && torsoSpec != null && tb.width() > 0.1F && tb.depth() > 0.1F) {
			if (torsoOnly) {
				float y = tb.maxY + 0.03F;
				torsoSpec.extraLeaves.put(CUT_WAIST, quadPart(Direction.UP, tb.minX, tb.maxX, y, y, tb.minZ, tb.maxZ,
					0.0F, 4.0F, 16.0F, 12.0F));
				hasQuads = true;
			} else {
				float neckHalfW = Math.min(tb.width() * 0.28F, tb.depth() * 0.58F);
				float neckHalfD = tb.depth() * 0.45F;
				float ny = tb.minY - 0.03F;
				torsoSpec.extraLeaves.put(CUT_NECK, quadPart(Direction.DOWN, tb.midX() - neckHalfW, tb.midX() + neckHalfW, ny, ny,
					tb.midZ() - neckHalfD, tb.midZ() + neckHalfD, 0.0F, 0.0F, 16.0F, 16.0F));
				float hy = tb.maxY + 0.03F;
				torsoSpec.extraLeaves.put(CUT_RIGHT_HIP, quadPart(Direction.UP, tb.minX, tb.midX(), hy, hy, tb.minZ, tb.maxZ,
					0.0F, 0.0F, 16.0F, 16.0F));
				torsoSpec.extraLeaves.put(CUT_LEFT_HIP, quadPart(Direction.UP, tb.midX(), tb.maxX, hy, hy, tb.minZ, tb.maxZ,
					0.0F, 0.0F, 16.0F, 16.0F));
				float sh = Math.min(tb.height() * 0.36F, tb.depth());
				float sy0 = tb.minY;
				float sy1 = sy0 + sh;
				float rx = tb.minX - 0.03F, lx = tb.maxX + 0.03F;
				torsoSpec.extraLeaves.put(CUT_RIGHT_SHOULDER, quadPart(Direction.WEST, rx, rx, sy0, sy1, tb.minZ, tb.maxZ,
					0.0F, 0.0F, 16.0F, 16.0F));
				torsoSpec.extraLeaves.put(CUT_LEFT_SHOULDER, quadPart(Direction.EAST, lx, lx, sy0, sy1, tb.minZ, tb.maxZ,
					0.0F, 0.0F, 16.0F, 16.0F));
				hasQuads = true;
			}
		}

		if (!torsoOnly) {
			float sleeveClearance = parent instanceof PlayerModel ? 0.27F : 0.03F;
			for (int part : Dismember.PARTS) {
				ModelPart lp = limb(parentRoot, part);
				Bounds lb = boundsOf(lp);
				NodeSpec limbSpec = lp == null ? null : specs.get(lp);
				if (lb == null || limbSpec == null || lb.width() <= 0.05F || lb.depth() <= 0.05F) {
					continue;
				}
				if (part == Dismember.HEAD) {
					float hw = lb.width() * 0.28F;
					float hd = lb.depth() * 0.28F;
					boolean bottomPivot = Math.abs(lb.maxY) <= Math.abs(lb.minY);
					float y = bottomPivot ? lb.maxY + 0.03F : lb.minY - 0.03F;
					limbSpec.extraLeaves.put(CUT_SEVERED, quadPart(bottomPivot ? Direction.UP : Direction.DOWN,
						lb.midX() - hw, lb.midX() + hw, y, y, lb.midZ() - hd, lb.midZ() + hd, 0.0F, 0.0F, 16.0F, 16.0F));
					hasQuads = true;
				} else {
					float stumpY = lb.maxY + 0.30F;
					limbSpec.extraLeaves.put(CUT_STUMP, quadPart(Direction.UP, lb.minX, lb.maxX, stumpY, stumpY, lb.minZ, lb.maxZ,
						0.0F, 0.0F, 16.0F, 16.0F));
					boolean topPivot = Math.abs(lb.minY) <= Math.abs(lb.maxY);
					float sevY = topPivot ? lb.minY - sleeveClearance : lb.maxY + sleeveClearance;
					limbSpec.extraLeaves.put(CUT_SEVERED, quadPart(topPivot ? Direction.DOWN : Direction.UP,
						lb.minX, lb.maxX, sevY, sevY, lb.minZ, lb.maxZ, 0.0F, 0.0F, 16.0F, 16.0F));
					hasQuads = true;
				}
			}
		}

		List<ModelPart> srcList = new ArrayList<>();
		List<ModelPart> dstList = new ArrayList<>();
		ModelPart bakedRoot = bakeNode(rootSpec, srcList, dstList);
		return new Built(bakedRoot, srcList.toArray(ModelPart[]::new), dstList.toArray(ModelPart[]::new), hasQuads);
	}

	private static ModelPart bakeNode(NodeSpec spec, List<ModelPart> srcList, List<ModelPart> dstList) {
		Map<String, ModelPart> children = new HashMap<>();
		for (Map.Entry<String, NodeSpec> entry : spec.children.entrySet()) {
			children.put(entry.getKey(), bakeNode(entry.getValue(), srcList, dstList));
		}
		children.putAll(spec.extraLeaves);
		ModelPart part = new ModelPart(List.of(), children);
		part.setInitialPose(spec.source.getInitialPose());
		part.resetPose();
		srcList.add(spec.source);
		dstList.add(part);
		return part;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void syncFromParent(Model<?> parent, Object state, ModelPart[] sources, ModelPart[] targets) {
		try {
			((Model) parent).setupAnim(state);
		} catch (RuntimeException ignored) {
		}
		for (int i = 0; i < sources.length; i++) {
			ModelPart from = sources[i];
			ModelPart to = targets[i];
			to.x = from.x;
			to.y = from.y;
			to.z = from.z;
			to.xRot = from.xRot;
			to.yRot = from.yRot;
			to.zRot = from.zRot;
			to.xScale = from.xScale;
			to.yScale = from.yScale;
			to.zScale = from.zScale;
			to.visible = true;
		}
	}

	private static final class HumanoidCutModel extends HumanoidModel<HumanoidRenderState> implements PosedCopy.Holder {
		private final HumanoidModel<?> parent;
		private final ModelPart[] sources;
		private final ModelPart[] targets;

		HumanoidCutModel(ModelPart root, HumanoidModel<?> parent, ModelPart[] sources, ModelPart[] targets) {
			super(root);
			this.parent = parent;
			this.sources = sources;
			this.targets = targets;
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			resetPose();
			syncFromParent(this.parent, state, this.sources, this.targets);
			if (PosedCopy.of(this.parent)) {
				root().loadPose(PartPose.ZERO);
			}
		}

		@Override
		public boolean carriesPose() {
			return PosedCopy.of(this.parent);
		}
	}

	private static final class GeneralCutModel extends EntityModel<EntityRenderState> {
		private final Model<?> parent;
		private final ModelPart[] sources;
		private final ModelPart[] targets;

		GeneralCutModel(ModelPart root, Model<?> parent, ModelPart[] sources, ModelPart[] targets) {
			super(root);
			this.parent = parent;
			this.sources = sources;
			this.targets = targets;
		}

		@Override
		public void setupAnim(EntityRenderState state) {
			resetPose();
			syncFromParent(this.parent, state, this.sources, this.targets);
		}
	}
}
