package com.jaymospowers.client;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.SeveredPart;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Drawing {@link Dismember}: a body is drawn without the parts it's lost, and a {@link SeveredPart} as just that one
 * part (its renderer draws the whole body with only that part showing). Parts are found by name in any model (head,
 * arms, legs, and the hat, sleeves and trousers over them), hidden right after the model animates and shown again as
 * soon as it's drawn, so a model shared by many bodies is never left missing anything. A player who's lost their head
 * sees through it where it fell until they respawn.
 */
public final class Dismembered {
	/** On a living body's render state: the parts it's lost. */
	public static final RenderStateDataKey<Integer> MISSING = RenderStateDataKey.create(() -> "jaymospowers:missing_parts");
	/** On a severed part's body: the one part to draw (everything else hidden, the part straightened out). */
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

	private static void hidePart(@Nullable ModelPart part) {
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
