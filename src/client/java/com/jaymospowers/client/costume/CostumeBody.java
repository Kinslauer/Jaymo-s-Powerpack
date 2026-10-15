package com.jaymospowers.client.costume;

import com.jaymospowers.client.EmfCompat;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.78) What the costume needs to know about the body it is drawn over, so it sits on that body and moves with it
 * (Andy: "the costumes clip over every character, it doesnt move consistently at all"):
 * <ul>
 * <li><b>The body's animated pose.</b> Entity Model Features (Fresh Animations) animates the player model in the renderer,
 * just after its own animation and before the render layers; the costume's models re-ran the plain animation when drawn
 * and so stayed in the vanilla walk while the body under them bobbed, swayed and swung Fresh Animations' way. The pose
 * is kept on the render state just before the layers ({@link #keep}, from {@code CostumePoseLivingEntityRendererMixin}),
 * only when EMF really animated it (not when our poses or Better Combat paused it: then the vanilla pose stands and our
 * poses are laid over the costume like over any player model). The same as Kingdoms' armour (its WornModel).</li>
 * <li><b>Which model it is.</b> MCA swaps the player renderer's model per player as it draws them, and models are drawn
 * after every renderer is done, so the costume takes the model the renderer had for this player ({@link #PARENT}).</li>
 * <li><b>MCA's villager body.</b> With MCA every player is drawn with a villager body: the arms are always wide (its
 * skin layers ignore a slim skin), the head is scaled by the villager's head size, and women have a bust (a box drawn
 * in the body's space, scaled by its size). The costume's arms follow the body model's own arm width, its head pieces
 * take the head's scale ({@link #headScale}, read from MCA by name), and the suit has a bust of its own that copies
 * MCA's ({@code CostumeModels.Worn}).</li>
 * </ul>
 */
public final class CostumeBody {
	/** The body's pose (root, head, body, arms, legs) as EMF animated it, for the costume drawn over it. */
	static final RenderStateDataKey<PartPose[]> BODY = RenderStateDataKey.create(() -> "jaymospowers:costume_body_pose");
	/** The model the player renderer drew this player with. */
	static final RenderStateDataKey<PlayerModel> PARENT = RenderStateDataKey.create(() -> "jaymospowers:costume_parent");
	/** Narrower than this (pixels), an arm is a slim one. */
	private static final float SLIM_BELOW = 3.5F;
	private static final RandomSource ANY = RandomSource.create();

	private static final String MCA = "net.conczin.mca.";
	private static final Map<Class<?>, Method[]> DIMENSIONS = new ConcurrentHashMap<>();
	private static boolean mcaBroken;

	private CostumeBody() {
	}

	/** Just before the render layers (LivingEntityRenderer.submit): keeps the body's pose if EMF has just animated it. */
	public static void keep(EntityRenderState state, Model<?> body) {
		if (state.getData(CostumeClient.KEY) == null || !(body instanceof PlayerModel player) || !EmfCompat.bodyAnimated(state, body)) {
			return;
		}
		ModelPart[] parts = CostumeModels.Worn.parts(player);
		PartPose[] pose = new PartPose[parts.length];
		for (int i = 0; i < parts.length; i++) {
			pose[i] = parts[i].storePose();
		}
		state.setData(BODY, pose);
	}

	/** Whether {@code model}'s arms are slim (by the arm's own box; {@code fallback} if it can't be told). */
	static boolean slim(PlayerModel model, boolean fallback) {
		return slimArm(model.rightArm, isMca(model) ? false : fallback);
	}

	private static boolean isMca(Model<?> model) {
		return model.getClass().getName().startsWith(MCA) || model.getClass().getSuperclass().getName().startsWith(MCA);
	}

	static boolean slimArm(ModelPart arm, boolean fallback) {
		if (arm.isEmpty()) {
			return fallback;
		}
		ModelPart.Cube cube = arm.getRandomCube(ANY);
		return cube.maxX - cube.minX < SLIM_BELOW;
	}

	/** The scale MCA draws {@code model}'s head at (its villager's head size), or 1 for any other model. */
	static float headScale(Model<?> model) {
		return dimension(model, 0);
	}

	/** How much of its bust MCA draws for {@code model}'s villager (its dimensions' bust; 1 for any other model). */
	static float bustScale(Model<?> model) {
		return dimension(model, 1);
	}

	/** One of MCA's villager dimensions (0 head, 1 bust) as the model holds them now, read by name; 1 if it can't be. */
	private static float dimension(Model<?> model, int which) {
		if (mcaBroken || !isMca(model)) {
			return 1.0F;
		}
		try {
			Method[] methods = DIMENSIONS.computeIfAbsent(model.getClass(), c -> {
				try {
					Method dimensions = c.getMethod("getDimensions");
					Class<?> type = dimensions.getReturnType();
					return new Method[] {dimensions, type.getMethod("getHead"), type.getMethod("getBreasts")};
				} catch (NoSuchMethodException e) {
					return new Method[0];
				}
			});
			if (methods.length == 0) {
				return 1.0F;
			}
			Object dims = methods[0].invoke(model);
			float value = dims != null ? ((Number)methods[1 + which].invoke(dims)).floatValue() : 1.0F;
			return value >= 0.0F && value < 4.0F ? value : 1.0F;
		} catch (Throwable e) {
			mcaBroken = true;
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't read MCA's villager dimensions; costumes keep the plain body's", e);
			return 1.0F;
		}
	}

	/** {@code model}'s bust (MCA's "breasts" part on the root), or null. */
	static @Nullable ModelPart bust(PlayerModel model) {
		return model.root().hasChild(BUST) ? model.root().getChild(BUST) : null;
	}

	static final String BUST = "breasts";
}
