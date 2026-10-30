package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Andy, 2026-10-29) The Mad Tinkerer's outfit: the Blockbench Workshop's "sanguine outfit" (a wide-brim feathered hat, a long
 * coat with tails, bracers and boots; credits in textures/entity/bb/CREDITS.txt) in a darker shade
 * ({@code tools/blockbench/tinkerer_outfit.py}), drawn over Jaymo's Powers' Z Fighter tinkerer ({@link com.jaymoskingdoms.ZFighterFolk})
 * as an MCA villager. Its pieces hang on the villager's own head, body, arms and legs ({@link WornModel}, so they take every
 * pose armour does): the hat on the head, the coat and its tails (the waist) on the body, the bracers on the arms, the boots on
 * the legs. The coat covers his MCA clothes, and the hat takes his hair's place ({@code McaBioFormLayerMixin}).
 * <p>
 * Who he is, client side: entity tags aren't synced, but MCA's clothes are, and the server dresses him in his own outfit
 * ({@code Uniforms.zfighter("tinkerer")}: {@code skins/clothing/zfighter/tinkerer_<n>.png}); a villager in one of those is him.
 * Nothing new is synced. Only registered and read with MCA.
 */
public final class TinkererOutfit<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	/** On a villager's render state: set while it is the Tinkerer. */
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:tinkerer_outfit");

	/** The model's name (assets/jaymoskingdoms/bbmodels, textures/entity/bb). */
	private static final String MODEL = "tinkerer_sanguine";
	/** His clothes' start: his outfits are {@code zfighter/tinkerer_0..3} among ours. */
	private static final String CLOTHES = "jaymoskingdoms:skins/clothing/zfighter/tinkerer_";
	/** Each of the model's pieces, and the villager's bone it hangs on (the coat's tails, {@code bipedWaist}, from the body). */
	private static final Map<String, String> PIECES = Map.of("armorHead", "head", "armorBody", "body", "bipedWaist", "body",
		"armorRightArm", "right_arm", "armorLeftArm", "left_arm", "armorRightLeg", "right_leg", "armorLeftLeg", "left_leg",
		"armorRightBoot", "right_leg", "armorLeftBoot", "left_leg");

	private final WornModel worn;
	private final Identifier texture = JaymosKingdoms.id("textures/entity/bb/" + MODEL + ".png");

	private TinkererOutfit(RenderLayerParent<S, HumanoidModel<S>> renderer) {
		super(renderer);
		this.worn = new WornModel(ReverentLayer.skeleton(BbModel.load(JaymosKingdoms.MOD_ID, MODEL), PIECES), this::getParentModel);
	}

	/** Puts this layer on every humanoid renderer but the player's, with MCA. */
	public static void register() {
		if (!McaCompat.LOADED) {
			return;
		}
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (!(renderer instanceof AvatarRenderer<?>) && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static TinkererOutfit<?> create(LivingEntityRenderer<?, ?, ?> renderer) {
		return new TinkererOutfit((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: whether {@code entity} is the Tinkerer. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, McaCompat.LOADED && Mca.tinkerer(entity) ? Boolean.TRUE : null);
	}

	/** The part that names an MCA class, only touched with MCA. */
	private static final class Mca {
		static boolean tinkerer(LivingEntity entity) {
			return entity instanceof VillagerEntityMCA villager && !villager.isBaby() && villager.getClothes().startsWith(CLOTHES);
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		if (state.getData(KEY) == null || state.isInvisible) {
			return;
		}
		collector.submitModel(this.worn, state, poseStack, RenderTypes.armorCutoutNoCull(this.texture), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
			state.outlineColor);
	}
}
