package com.jaymospowers.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;

/**
 * (0.15.44) Models that are posed as a copy of a body we've already posed, so our poses mustn't be laid on them again.
 * Andy (2026-10-21): "down state doesnt look write, looks like the animation is drawn twice again, down arrow crawling is
 * the same, and so is the slide". With MCA, a player's (or villager's) body is drawn by MCA's own layers; each layer's
 * model takes, in its {@code setupAnim}, the pose the body had as the layers began (MCA's {@code HumanoidModelPose}),
 * ours already in it. Every pose of ours that eases through {@link PoseBlend} adds its offsets to whatever model it's
 * handed, so on those layers (and on a costume worn over them, which takes the same copy) it landed twice: the downed
 * body folded, the crawl and the slide overdone. {@link PoseBlend} asks here and leaves such a model as it is.
 */
public final class PosedCopy {
	/** A model of ours that may have taken such a copy this draw (a costume over an MCA body). */
	public interface Holder {
		boolean carriesPose();
	}

	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");
	/** MCA's own flag on its layer models: they take the copied pose (PlayerEntityExtendedModel, VillagerEntityBaseModelMCA). */
	private static final String MCA_FIELD = "receivesDeferredAnimationPose";
	/** Per model class, MCA's flag (null: the class has none). */
	private static final Map<Class<?>, Field> FIELDS = new HashMap<>();
	private static boolean broken;

	private PosedCopy() {
	}

	/** Whether {@code model} is already wearing our poses, by way of the body it copied. Render thread only. */
	public static boolean of(HumanoidModel<?> model) {
		if (model instanceof Holder holder) {
			return holder.carriesPose();
		}
		if (!MCA || broken) {
			return false;
		}
		try {
			Class<?> type = model.getClass();
			Field field = FIELDS.computeIfAbsent(type, PosedCopy::find);
			return field != null && field.getBoolean(model);
		} catch (Throwable e) {
			broken = true;
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't read MCA's layer models; our poses may show twice on them", e);
			return false;
		}
	}

	private static Field find(Class<?> type) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			try {
				Field field = c.getDeclaredField(MCA_FIELD);
				if (field.getType() == boolean.class) {
					field.setAccessible(true);
					return field;
				}
			} catch (NoSuchFieldException e) {
				// up a class
			}
		}
		return null;
	}
}
