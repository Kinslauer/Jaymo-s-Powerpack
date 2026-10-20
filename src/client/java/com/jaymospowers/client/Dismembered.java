package com.jaymospowers.client;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.SeveredPart;
import com.jaymospowers.core.wounds.Wounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
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
 */
public final class Dismembered {
	/** On a living body's render state: the parts it's lost. */
	public static final RenderStateDataKey<Integer> MISSING = RenderStateDataKey.create(() -> "jaymospowers:missing_parts");
	/** On a severed part's body: the one part to draw (everything else hidden, the part straightened out). */
	/** On a living body's render state: the bones it has broken (drawn hanging limp). */
	public static final RenderStateDataKey<Integer> BROKEN = RenderStateDataKey.create(() -> "jaymospowers:broken_bones");
	/** A broken limb's droop: an arm a little forward and out from the side, a leg trailing slightly behind and in. */
	private static final float LIMP_ARM_PITCH = -0.1F, LIMP_ARM_ROLL = 0.12F, LIMP_LEG_PITCH = 0.18F, LIMP_LEG_ROLL = -0.05F;
	public static final RenderStateDataKey<Integer> ONLY = RenderStateDataKey.create(() -> "jaymospowers:only_part");

	private static final Map<ModelPart, Function<String, ModelPart>> LOOKUPS = new WeakHashMap<>();
	private static final List<ModelPart> HIDDEN = new ArrayList<>();
	private static final List<ModelPart> SKIPPED = new ArrayList<>();
	private static @Nullable Entity cameraBefore;

	private Dismembered() {
	}

	/** The model parts that make up {@code part}, by name (a part and what's layered over it). */
	public static String[] names(int part) {
		return switch (part) {
			case Dismember.HEAD -> new String[] {"head", "hat", "nose"};
			case Dismember.RIGHT_ARM -> new String[] {"right_arm", "right_sleeve"};
			case Dismember.LEFT_ARM -> new String[] {"left_arm", "left_sleeve"};
			case Dismember.RIGHT_LEG -> new String[] {"right_leg", "right_pants"};
			default -> new String[] {"left_leg", "left_pants"};
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
		// (0.14.2) Wounds' broken arms and legs (synced to everyone)
		int broken = Wounds.brokenLimbs(entity);
		state.setData(BROKEN, broken == 0 ? null : broken);
	}

	/** Right after a model animates: the lost parts hidden (or all but the severed one). */
	public static void hide(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState renderState)) {
			return;
		}
		ModelPart root = model.root();
		Integer only = renderState.getData(ONLY);
		if (only != null) {
			// every part but the severed one (and what hangs off it) draws nothing of its own
			java.util.Set<ModelPart> keep = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
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
		if (broken != null) {
			limp(root, broken);
		}
		Integer missing = renderState.getData(MISSING);
		if (missing == null) {
			return;
		}
		for (int part : Dismember.PARTS) {
			if ((missing & part) != 0) {
				for (String name : names(part)) {
					hidePart(find(root, name));
				}
			}
		}
	}

	/** Broken limbs (Wounds' broken parts, (0.14.2); before, Fractures') hang limp: an arm dangles at the side, a leg drags straight, neither swings. */
	private static void limp(ModelPart root, int broken) {
		for (int part : new int[] {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG}) {
			if ((broken & part) == 0) {
				continue;
			}
			ModelPart limb = find(root, names(part)[0]);
			if (limb == null) {
				continue;
			}
			boolean right = part == Dismember.RIGHT_ARM || part == Dismember.RIGHT_LEG;
			boolean arm = part == Dismember.RIGHT_ARM || part == Dismember.LEFT_ARM;
			limb.xRot = arm ? LIMP_ARM_PITCH : LIMP_LEG_PITCH;
			limb.yRot = 0.0F;
			limb.zRot = (right ? 1.0F : -1.0F) * (arm ? LIMP_ARM_ROLL : LIMP_LEG_ROLL);
		}
	}

	/** Hides {@code part} until the model's drawn ({@link #restore}). */
	public static void hidePart(@Nullable ModelPart part) {
		if (part != null && part.visible) {
			part.visible = false;
			HIDDEN.add(part);
		}
	}

	/** Once the model's drawn: everything hidden for it shown again. */
	public static void restore() {
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
			return;
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
}
