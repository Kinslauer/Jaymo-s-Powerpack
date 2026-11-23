package com.jaymoskingdoms.client.inhabitants.fts;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Item model type {@code jaymoskingdoms:geo_weapon}: a weapon of From the Shadows Reborn (Sonmok14 and L_Ender, AGPL v3;
 * their models and textures, see licenses/from-the-shadows/) drawn in every view by GeckoLib from
 * {@code geckolib/models/entity/<weapon>.geo.json}, as the original draws them (they are still, nothing animates).
 * Shaped after Jaymo's Powers' animated guns.
 * <pre>
 * {"type": "jaymoskingdoms:geo_weapon",
 *  "weapon": "devil_splitter",                  the GeckoLib model, animation and texture (by that name)
 *  "base": "jaymoskingdoms:item/devil_splitter",  a model with no shape whose display transforms are the original's
 *  "transformation": [16 numbers]}              where the model lies in the item (row by row; a model's origin is its
 *                                               feet, an item's the corner of its cube, so this moves it to the middle)
 * </pre>
 */
public final class GeoWeaponModel implements ItemModel {
	public static final Identifier ID = JaymosKingdoms.id("geo_weapon");

	/** The renderer's own pass: a weapon in the hand needs nothing of the camera (no name tags, no billboards). */
	private static final CameraRenderState CAMERA = new CameraRenderState();
	private static final Map<String, Renderer> BY_NAME = new ConcurrentHashMap<>();

	private static final SpecialModelRenderer<Renderer> RENDERER = new SpecialModelRenderer<>() {
		@Override
		public void submit(@Nullable Renderer renderer, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil,
			int outline) {
			if (renderer != null) {
				renderer.submit(poseStack, collector, light);
			}
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
		}

		@Override
		public @Nullable Renderer extractArgument(ItemStack stack) {
			return null;
		}
	};

	private final Renderer renderer;
	private final ModelRenderProperties properties;
	private final Matrix4fc transformation;

	private GeoWeaponModel(Renderer renderer, ModelRenderProperties properties, Matrix4fc transformation) {
		this.renderer = renderer;
		this.properties = properties;
		this.transformation = transformation;
	}

	public static void init() {
		ItemModels.ID_MAPPER.put(ID, Unbaked.MAP_CODEC);
	}

	@Override
	public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
		@Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		output.appendModelIdentityElement(this);
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		if (item.hasFoil()) {
			layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
			output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
		}
		layer.setLocalTransform(this.transformation);
		this.properties.applyToLayer(layer, displayContext);
		layer.setupSpecialModel(RENDERER, this.renderer);
	}

	/** A weapon as GeckoLib draws it: nothing to animate, one instance. */
	private static final class Still implements GeoAnimatable {
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return this.cache;
		}
	}

	private static final class Renderer extends GeoObjectRenderer<Still, Void, GeoRenderState> {
		private final Still animatable = new Still();

		Renderer(String name) {
			super(new DefaultedEntityGeoModel<Still>(JaymosKingdoms.id(name)));
		}

		void submit(PoseStack poseStack, SubmitNodeCollector collector, int light) {
			performRenderPass(this.animatable, null, poseStack, collector, CAMERA, light,
				Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}

		@Override
		public long getInstanceId(Still animatable, @Nullable Void state) {
			return 1L;
		}

		@Override
		public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {
			// the item model's transformation already lays the model where the weapon goes in the item
		}
	}

	public record Unbaked(String weapon, Identifier base, Optional<Transformation> transformation) implements ItemModel.Unbaked {
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				com.mojang.serialization.Codec.STRING.fieldOf("weapon").forGetter(Unbaked::weapon),
				Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
				Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation))
			.apply(i, Unbaked::new));

		@Override
		public void resolveDependencies(Resolver resolver) {
			resolver.markDependency(this.base);
		}

		@Override
		public ItemModel bake(BakingContext context, Matrix4fc transformation) {
			ModelBaker baker = context.blockModelBaker();
			ResolvedModel model = baker.getModel(this.base);
			ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots());
			return new GeoWeaponModel(BY_NAME.computeIfAbsent(this.weapon, Renderer::new), properties,
				Transformation.compose(transformation, this.transformation));
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
