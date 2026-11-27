package com.jaymoskingdoms.client;

import com.jaymoskingdoms.MobAmmo;
import com.jaymoskingdoms.Sidearms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "skeletons should carry tattered quivers". Every bow skeleton (skeleton, stray, bogged, parched) wears a
 * {@link MobAmmo#TATTERED_QUIVER} slung across its back, arrows over the shoulder; how many it has left is
 * {@link MobAmmo}'s count.
 * <p>NPC archers too (Andy, 2026-11-27: "you should be able to see what npcs have equipped"; "we have actual quivers they can
 * wear"): a guard, camp fighter or villager with a bow or crossbow and arrows left wears a real quiver, the one the server
 * says ({@link Sidearms#QUIVER}, synced), over the left shoulder, clear of a bow slung across the right.
 */
public final class QuiverLayer {
	/** The skeletons that carry one (the wither skeleton fights with a sword). */
	static final Set<EntityType<?>> CARRIERS = Set.of(EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED, EntityTypes.PARCHED);
	/** Where it sits on the back (model pixels: across, down from the neck, out from the spine), its size and slant (degrees). */
	static final float ACROSS = 0.0F, DOWN = 5.5F, OUT = 2.6F, SIZE = 0.75F, SLANT = 0.0F;
	/** An NPC archer's: over the left shoulder (model pixels across, to the body's left), tipped out a little (degrees). */
	static final float NPC_ACROSS = 2.0F, NPC_SLANT = 15.0F;

	/** On an NPC archer's render state: the quiver it wears ({@link #extract}). */
	static final RenderStateDataKey<ItemStack> WORN = RenderStateDataKey.create(() -> "jaymoskingdoms:npc_quiver");

	/**
	 * Jaymo's Powers (optional) draws a severed head or limb as the whole body with only that part showing, marking its
	 * render state with {@code Dismembered.ONLY}. The quiver isn't on any of those parts: without this a dead skeleton's
	 * quiver was drawn again on its head, both arms and both legs.
	 */
	private static final @Nullable RenderStateDataKey<?> ONLY_PART = onlyPartKey();

	private QuiverLayer() {
	}

	private static @Nullable RenderStateDataKey<?> onlyPartKey() {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return null;
		}
		try {
			return (RenderStateDataKey<?>) Class.forName("com.jaymospowers.client.Dismembered").getField("ONLY").get(null);
		} catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
			return null;
		}
	}

	/** {@code state} is a severed part's body (only that part drawn). */
	static boolean severedPart(EntityRenderState state) {
		return ONLY_PART != null && state.getData(ONLY_PART) != null;
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			// (the skeletons always; any other humanoid's body when it is an NPC archer, players' never)
			if (renderer.getModel() instanceof HumanoidModel<?> && !(renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?>)) {
				helper.register(layer(renderer, CARRIERS.contains(entityType)));
			}
		});
	}

	/** From the living renderer's extractRenderState: whether this NPC wears an archer's quiver. */
	public static void extract(net.minecraft.world.entity.LivingEntity entity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
		ItemStack quiver = entity.getAttached(Sidearms.QUIVER);
		state.setData(WORN, quiver == null || quiver.isEmpty() ? null : quiver);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer, boolean skeleton) {
		return new Layer((RenderLayerParent) renderer, skeleton);
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		private final ItemStackRenderState quiver = new ItemStackRenderState();
		/** A bow skeleton's renderer: it always wears one, across the middle of its back. */
		private final boolean skeleton;

		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer, boolean skeleton) {
			super(renderer);
			this.skeleton = skeleton;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			ItemStack worn = this.skeleton ? null : state.getData(WORN);
			boolean npc = worn != null;
			if (state.isInvisible || severedPart(state) || !this.skeleton && !npc) {
				return;
			}
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getItemModelResolver().updateForTopItem(this.quiver, npc ? worn : new ItemStack(MobAmmo.TATTERED_QUIVER), ItemDisplayContext.FIXED,
				minecraft.level, null, 0);
			HumanoidModel<S> model = this.getParentModel();
			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			model.body.translateAndRotate(poseStack);
			poseStack.translate((npc ? NPC_ACROSS : ACROSS) / 16.0F, DOWN / 16.0F, OUT / 16.0F);
			// upright item space, flat against the back; the sprite already runs corner to corner
			poseStack.scale(-SIZE, -SIZE, SIZE);
			poseStack.rotateDegrees(Axis.ZP, npc ? NPC_SLANT : SLANT);
			this.quiver.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
