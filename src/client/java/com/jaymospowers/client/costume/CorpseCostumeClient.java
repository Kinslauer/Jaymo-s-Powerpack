package com.jaymospowers.client.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.costume.Costume;
import com.jaymospowers.costume.CostumeDesign;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.49) A dead player's body still wears the costume they died in ({@code costume.CorpseCostume}): drawn over Jaymo's
 * Kitchen's corpse through its {@code CorpseDressing} hooks (reached by name, so an older Kitchen just draws the body
 * bare). A costumed body is drawn in the plain player body, not MCA's villager look, so the costume sits on the model it
 * was made for; its pieces take the body's ragdoll and limp poses from Kitchen's model-feature mixins, like any model
 * submitted with the body's state.
 */
public final class CorpseCostumeClient {
	private static final String DRESSING = "com.jaymoskitchen.client.corpse.CorpseDressing";
	/** The costume models for bodies (they follow whichever body model is drawn just now). */
	private static CostumeModels.@Nullable Set models;
	private static @Nullable PlayerModel lastBody;

	private CorpseCostumeClient() {
	}

	@SuppressWarnings("unchecked")
	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		try {
			Class<?> dressing = Class.forName(DRESSING);
			((List<Predicate<Entity>>) dressing.getField("PLAIN").get(null)).add(CorpseCostumeClient::wears);
			((List<BiConsumer<Entity, AvatarRenderState>>) dressing.getField("EXTRACT").get(null)).add(CorpseCostumeClient::extract);
			((List<BiConsumer<AvatarRenderState, Object[]>>) dressing.getField("SUBMIT").get(null)).add(CorpseCostumeClient::submit);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosPowers.LOGGER.warn("Jaymo's Kitchen can't dress bodies (older Kitchen?): dead players' costumes aren't drawn on them", e);
		}
	}

	private static boolean wears(Entity corpse) {
		return corpse.getAttached(Costume.CORPSE_WORN) != null;
	}

	/** The body's state: the costume on it (its head piece off if it was off), the skin's outer layers under it hidden. */
	private static void extract(Entity corpse, AvatarRenderState avatar) {
		CostumeDesign design = corpse.getAttached(Costume.CORPSE_WORN);
		if (design != null && design.mask() != CostumeDesign.Mask.NONE && Boolean.TRUE.equals(corpse.getAttached(Costume.CORPSE_UNMASKED))) {
			design = design.withMask(CostumeDesign.Mask.NONE);
		}
		avatar.setData(CostumeClient.KEY, design);
		avatar.setData(CostumeBody.BODY, null);
		avatar.setData(CostumeBody.PARENT, null);
		avatar.setData(CostumeCover.KEY, null);
		if (design == null) {
			return;
		}
		avatar.showJacket = avatar.showLeftSleeve = avatar.showRightSleeve = avatar.showLeftPants = avatar.showRightPants = false;
		if (design.mask() != CostumeDesign.Mask.NONE) {
			avatar.showHat = false;
		}
	}

	/** Just after the body: the costume over it, in the body model's pose. */
	private static void submit(AvatarRenderState avatar, Object[] args) {
		CostumeDesign design = avatar.getData(CostumeClient.KEY);
		if (design == null || args.length < 4 || !(args[0] instanceof PlayerModel body) || !(args[1] instanceof PoseStack poseStack)
			|| !(args[2] instanceof SubmitNodeCollector collector) || !(args[3] instanceof Integer light)) {
			return;
		}
		lastBody = body;
		if (models == null) {
			models = new CostumeModels.Set(() -> lastBody);
		}
		CostumeClient.Layer.draw(models, body, null, poseStack, collector, light, avatar, design);
	}
}
