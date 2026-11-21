package com.jaymoskitchen.client.spit;

import com.jaymoskitchen.client.corpse.Dressed;
import com.jaymoskitchen.cooking.spit.GreatSpitBlock;
import com.jaymoskitchen.cooking.spit.SpitBlock;
import com.jaymoskitchen.cooking.spit.SpitBlockEntity;
import com.jaymoskitchen.corpse.CorpseData;
import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.corpse.Corpses;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws what's on the spit, turning slowly over the fire. Up to {@link SpitBlockEntity#PLACES} pieces of meat along the
 * bar, each drawn in 3D by its kind (kitchen/tools/gen_great_spit.py): mutton, chicken and rabbit as a shank, any other
 * meat as a steak, raw to charred as it cooks; fish as itself. A body takes the whole bar: a small one as it lay, a whole
 * beast on the great spit lying along the bar, skinned and gutted, browning as it roasts. When someone tears a piece off
 * ({@link #ripAt}), what's on it shakes for a few ticks.
 */
public class SpitBlockEntityRenderer implements BlockEntityRenderer<SpitBlockEntity, SpitBlockEntityRenderer.State> {
	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState[] items = new ItemStackRenderState[SpitBlockEntity.PLACES];
		final boolean[] has = new boolean[SpitBlockEntity.PLACES];
		boolean any;
		boolean great;
		boolean alongZ;
		float turn;
		int rip;
		float bodyHeight;
		@Nullable Entity corpseEntity;
		@Nullable EntityRenderState corpseState;
		@Nullable EntityRenderer<Entity, EntityRenderState> corpseRenderer;

		public State() {
			for (int i = 0; i < items.length; i++) {
				items[i] = new ItemStackRenderState();
			}
		}
	}

	/** How high the frame's crossbar runs above the spit block's floor (blocks; kitchen/tools/gen_spit_frame.py prints it). */
	static final float BAR_HEIGHT = 0.204F;
	/** How far apart the pieces sit along the bar (blocks): the plain spit, the great one. */
	static final float SPACING = 0.26F, GREAT_SPACING = 0.4F;
	/** Raw meat drawn as a shank rather than a steak. */
	private static final List<net.minecraft.world.item.Item> SHANKS = List.of(Items.MUTTON, Items.CHICKEN, Items.RABBIT);

	/** The game time a tear started at each position, for the tearing countdown. */
	private static final Map<BlockPos, Long> RIP_START = new HashMap<>();

	/** The client plays the tearing animation here when the server reports a chunk torn off. */
	public static void ripAt(BlockPos pos, long startTick) {
		RIP_START.put(pos, startTick);
	}

	private final ItemModelResolver itemModels;

	public SpitBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.itemModels = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void extractRenderState(SpitBlockEntity be, State state, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
		state.great = GreatSpitBlock.is(be.getBlockState());
		state.alongZ = state.great && be.getBlockState().getValue(GreatSpitBlock.AXIS) == Direction.Axis.Z;
		state.turn = (be.getLevel().getGameTime() + partialTicks) * (state.great ? 1.0F : 2.0F) % 360.0F;
		Long ripStart = RIP_START.get(be.getBlockPos());
		if (ripStart != null && be.getLevel().getGameTime() - ripStart >= 8) {
			RIP_START.remove(be.getBlockPos());
			ripStart = null;
		}
		state.rip = ripStart == null ? 0 : (int) Mth.clamp(8 - (be.getLevel().getGameTime() - ripStart), 0, 8);
		state.corpseEntity = null;
		state.corpseState = null;
		state.corpseRenderer = null;
		state.any = false;
		ItemStack first = be.at(0);
		if (first.is(Corpses.CORPSE_ITEM)) {
			CorpseData data = first.get(Corpses.CORPSE_DATA);
			if (data != null) {
				CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, be.getLevel());
				// an id of its own (never in the world, it has none): clientBody gives the body one from it, and asking
				// for an unset id throws
				corpse.setId(-4000 - (int) (be.getBlockPos().asLong() & 0xFFFF));
				corpse.restore(data);
				Entity body = corpse.clientBody(0.0F);
				if (body != null) {
					EntityRenderer rawRenderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(body);
					state.corpseRenderer = (EntityRenderer<Entity, EntityRenderState>) rawRenderer;
					state.corpseState = rawRenderer.createRenderState(body, partialTicks);
					rawRenderer.extractRenderState(body, state.corpseState, partialTicks);
					state.corpseEntity = corpse;
					state.bodyHeight = body.getBbHeight();
					if (be.whole()) {
						// a whole beast lies along the bar, head to one end, skinned and gutted and browning
						if (state.corpseState instanceof LivingEntityRenderState living) {
							living.bodyRot = -90.0F;
							living.yRot = 0.0F;
							living.xRot = 0.0F;
						}
						Dressed.mark(state.corpseState, be.dressed() | Dressed.roasted(be.doneness(0)));
					}
					state.any = true;
				}
			}
		}
		if (state.corpseState != null) {
			java.util.Arrays.fill(state.has, false);
			return;
		}
		for (int i = 0; i < SpitBlockEntity.PLACES; i++) {
			ItemStack stack = be.at(i);
			state.has[i] = !stack.isEmpty();
			if (state.has[i]) {
				state.any = true;
				itemModels.updateForTopItem(state.items[i], look(stack, be.doneness(i)), ItemDisplayContext.FIXED, be.getLevel(), null, 0);
			}
		}
	}

	/** How a piece of meat looks on the spit: a shank or a steak, as done as it is; fish (and anything else) as itself. */
	private static ItemStack look(ItemStack stack, float doneness) {
		if (stack.is(ItemTags.FISHES) || !stack.has(DataComponents.FOOD)) {
			return stack;
		}
		ItemStack look = new ItemStack(SHANKS.contains(stack.getItem()) || stack.getItem() == com.jaymoskitchen.delight.Delight.MUTTON_CHOPS
			? SpitBlock.SHANK : SpitBlock.STEAK);
		look.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(doneness), List.of(), List.of(), List.of()));
		return look;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.any) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, BAR_HEIGHT, 0.5F);
		if (state.alongZ) {
			poseStack.rotateDegrees(Axis.YP, 90.0F);
		}
		float shake = state.rip > 0 ? Mth.sin(state.rip * 0.9F) * 0.04F : 0.0F;
		if (state.corpseState != null && state.corpseRenderer != null) {
			poseStack.translate(0.0F, shake, 0.0F);
			poseStack.rotateDegrees(Axis.XP, state.turn);
			if (state.great) {
				// turned about its own middle
				poseStack.translate(0.0F, -state.bodyHeight * 0.5F, 0.0F);
			}
			state.corpseRenderer.submit(state.corpseState, poseStack, collector, camera);
		} else {
			float spacing = state.great ? GREAT_SPACING : SPACING;
			for (int i = 0; i < SpitBlockEntity.PLACES; i++) {
				if (!state.has[i]) {
					continue;
				}
				poseStack.pushPose();
				poseStack.translate((i - (SpitBlockEntity.PLACES - 1) / 2.0F) * spacing, shake, 0.0F);
				// each a little out of step with the next, as pieces skewered by hand are
				poseStack.rotateDegrees(Axis.XP, state.turn + i * 37.0F);
				state.items[i].submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
		}
		poseStack.popPose();
	}
}
