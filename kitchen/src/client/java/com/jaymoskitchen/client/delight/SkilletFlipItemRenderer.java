package com.jaymoskitchen.client.delight;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.client.mixin.CuboidItemModelWrapperAccessor;
import com.jaymoskitchen.delight.SkilletItem;
import com.jaymoskitchen.delight.StackBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * (From Farmer's Delight's {@code SkilletFlipItemRenderer}, MIT.) The item model of a skillet with something cooking in
 * it: the pan, with the food lying in it, and both of them tossing when the pan is flipped. The model type
 * {@code jaymoskitchen:skillet_flip} wraps the plain pan model (see {@code items/frying_pan.json}).
 */
public class SkilletFlipItemRenderer implements ItemModel {
	public static final Identifier ID = JaymosKitchen.id("skillet_flip");
	private static final SpecialRenderer SPECIAL_RENDERER = new SpecialRenderer();
	/** How high the flip tosses the food, in blocks. */
	private static final float FLIP_HEIGHT = 0.4F;
	/** The food on the pan: a tile 1/16 above the pan's floor, half size. */
	private static final float FOOD_SCALE = 0.5F;
	/** How far the arm tips while the pan flips, in degrees. */
	private static final float ARM_TIP = 15.0F;
	/** Where the arm rests while cooking, in degrees (the pan is held out in front). */
	private static final float ARM_REST = -20.0F;

	private final CuboidItemModelWrapper model;
	private final ModelRenderProperties properties;

	public SkilletFlipItemRenderer(CuboidItemModelWrapper model, ModelRenderProperties properties) {
		this.model = model;
		this.properties = properties;
	}

	@Override
	public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
		@Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		output.appendModelIdentityElement(this);
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		Argument argument = SPECIAL_RENDERER.extractArgument(item);
		properties.applyToLayer(layer, displayContext);
		model.update(argument.skillet, item, resolver, ItemDisplayContext.NONE, level, owner, seed);
		resolver.updateForTopItem(argument.ingredient, item.getOrDefault(SkilletItem.INGREDIENT, StackBox.EMPTY).stack(),
			ItemDisplayContext.FIXED, level, owner, seed);
		argument.displayContext = displayContext;
		layer.setupSpecialModel(SPECIAL_RENDERER, argument);
		output.appendModelIdentityElement(argument);
	}

	public static class SpecialRenderer implements SpecialModelRenderer<Argument> {
		@Override
		public void submit(@Nullable Argument argument, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
			if (argument == null) {
				return;
			}
			Minecraft minecraft = Minecraft.getInstance();
			ClientLevel level = minecraft.level;
			float animation = 0;
			if (!argument.ingredient.isEmpty() && level != null) {
				poseStack.pushPose();
				poseStack.translate(0.5, 1 / 16.0F, 0.5);
				if (argument.flipTimestamp != -1 && argument.displayContext != ItemDisplayContext.GUI) {
					float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
					animation = Mth.clamp(((level.getGameTime() - argument.flipTimestamp) + partial) / SkilletItem.FLIP_TIME, 0, 1);
					poseStack.translate(0, FLIP_HEIGHT * Mth.sin(animation * Mth.PI), 0);
					poseStack.rotateDegrees(Axis.XP, 180 * (argument.flipped ? animation + 1.0F : animation));
				} else {
					poseStack.rotateDegrees(Axis.XP, argument.flipped ? 180 : 0);
				}
				poseStack.rotateDegrees(Axis.XP, 90);
				poseStack.scale(FOOD_SCALE, FOOD_SCALE, FOOD_SCALE);
				argument.ingredient.submit(poseStack, collector, light, overlay, outline);
				poseStack.popPose();
			}
			poseStack.pushPose();
			poseStack.translate(0.5, 0.5, 0.5);
			if (animation != 0 && argument.displayContext.firstPerson()) {
				poseStack.translate(0, 0, 1);
				poseStack.rotateDegrees(Axis.XN, Mth.sin(animation * Mth.TWO_PI) * ARM_TIP);
				poseStack.translate(0.0F, 0, -1);
				poseStack.translate(0, 0, -Mth.sin(animation * Mth.PI) * 0.2);
			}
			argument.skillet.submit(poseStack, collector, light, overlay, outline);
			poseStack.popPose();
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
		}

		@Override
		public Argument extractArgument(ItemStack stack) {
			Argument argument = new Argument();
			argument.flipTimestamp = stack.getOrDefault(SkilletItem.FLIP_TIMESTAMP, -1L);
			argument.flipped = stack.getOrDefault(SkilletItem.FLIPPED, false);
			return argument;
		}
	}

	/** What one draw of the pan needs: the pan and the food already resolved, and how the flip stands. */
	public static class Argument {
		public final ItemStackRenderState skillet = new ItemStackRenderState();
		public final ItemStackRenderState ingredient = new ItemStackRenderState();
		public long flipTimestamp;
		public boolean flipped;
		public ItemDisplayContext displayContext = ItemDisplayContext.NONE;
	}

	public record Unbaked(CuboidItemModelWrapper.Unbaked model) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> CODEC = CuboidItemModelWrapper.Unbaked.MAP_CODEC.xmap(Unbaked::new, Unbaked::model);

		@Override
		public ItemModel bake(BakingContext context, Matrix4fc transformation) {
			CuboidItemModelWrapper baked = (CuboidItemModelWrapper) model.bake(context, new Matrix4f());
			CuboidItemModelWrapperAccessor accessor = (CuboidItemModelWrapperAccessor) baked;
			ModelRenderProperties properties = accessor.jaymoskitchen$getProperties();
			// the pan is drawn inside the special renderer, so the wrapper itself carries no display transforms
			accessor.jaymoskitchen$setProperties(new ModelRenderProperties(properties.usesBlockLight(), properties.particleMaterial(), ItemTransforms.NO_TRANSFORMS));
			return new SkilletFlipItemRenderer(baked, properties);
		}

		@Override
		public void resolveDependencies(Resolver resolver) {
			model.resolveDependencies(resolver);
		}

		@Override
		public MapCodec<? extends ItemModel.Unbaked> type() {
			return CODEC;
		}
	}

	/** Tips the arm that holds a flipping pan (third person). */
	public static void applyArm(@Nullable ClientLevel level, HumanoidModel<?> model, SkilletFlipState state, HumanoidArm arm) {
		long time = state.jaymoskitchen$getFlipTimestamp();
		if (time == -1 || level == null) {
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float animation = Mth.clamp(((level.getGameTime() - time) + partial) / SkilletItem.FLIP_TIME, 0, 1);
		float angle = (-Mth.sin(animation * Mth.TWO_PI) * ARM_TIP + ARM_REST) * (float) (Math.PI / 180.0);
		if (arm == HumanoidArm.LEFT) {
			model.leftArm.xRot = angle;
		} else {
			model.rightArm.xRot = angle;
		}
	}
}
