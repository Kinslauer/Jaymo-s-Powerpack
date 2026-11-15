package com.jaymospowers.client.combat;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.resources.palette.PalettedTextureManager;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Medieval picks (gu-netherite): the netherite shield in hand, the shield's own shape in dark netherite, and instead of
 * a banner the armour trim smithed onto it, drawn as armour trims are (the pattern's shield decal, coloured by its
 * material's palette: textures/trims/entity/shield, made by tools/gen_medieval_smithing.py). A trim pattern with no
 * shield decal (another mod's) is left off rather than drawn missing.
 */
public class NetheriteShieldRenderer implements SpecialModelRenderer<DataComponentMap> {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/netherite_shield.png");
	/** Whether each trim decal exists (looked up once per resource reload). */
	private static final Map<Identifier, Boolean> DECALS = new HashMap<>();

	private final ShieldModel model;

	NetheriteShieldRenderer(ShieldModel model) {
		this.model = model;
	}

	public static void init() {
		SpecialModelRenderers.ID_MAPPER.put(JaymosPowers.id("netherite_shield"), Unbaked.MAP_CODEC);
		// a resource pack can add or take away decals: look them up again after each reload
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JaymosPowers.id("netherite_shield_decals"),
			(ResourceManagerReloadListener) manager -> DECALS.clear());
	}

	@Override
	public @Nullable DataComponentMap extractArgument(ItemStack stack) {
		return stack.immutableComponents();
	}

	@Override
	public void submit(@Nullable DataComponentMap components, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
		boolean hasFoil, int outline) {
		ArmorTrim trim = components == null ? null : components.get(DataComponents.TRIM);
		Identifier decal = trim == null ? null : decal(trim);
		collector.submitModel(model, Unit.INSTANCE, poseStack, hasFoil && decal == null ? RenderTypes.entitySolidGlint(TEXTURE)
			: RenderTypes.entitySolid(TEXTURE), light, overlay, -1, null, outline);
		if (decal != null) {
			PalettedTextureManager.Handle handle = Minecraft.getInstance().getPalettedTextureManager().getOrPrepare(decal,
				trim.material().value().paletteId());
			collector.order(1).submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.armorTrim(handle.textureLocation(),
				trim.pattern().value().decal()), light, overlay, -1, handle, outline);
			if (hasFoil) {
				collector.order(2).submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.trimmedArmorGlint(), light, overlay, -1, null, 0);
			}
		}
	}

	/** The shield decal of the trim's pattern, or null if there is none. */
	private static @Nullable Identifier decal(ArmorTrim trim) {
		Identifier id = JaymosPowers.id("trims/entity/shield/" + trim.pattern().value().assetId().getPath());
		boolean there = DECALS.computeIfAbsent(id, key -> Minecraft.getInstance().getResourceManager()
			.getResource(key.withPath(path -> "textures/" + path + ".png")).isPresent());
		return there ? id : null;
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		model.root().getExtentsForGui(new PoseStack(), output);
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<DataComponentMap> {
		public static final Unbaked INSTANCE = new Unbaked();
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(INSTANCE);

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public NetheriteShieldRenderer bake(SpecialModelRenderer.BakingContext context) {
			return new NetheriteShieldRenderer(new ShieldModel(context.entityModelSet().bakeLayer(ModelLayers.SHIELD)));
		}
	}
}
