package com.jaymospowers.client.bits;

import com.jaymospowers.bits.SoulBurn;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.world.entity.Entity;

/**
 * Soul fire burns blue ({@link SoulBurn} says whose fire it is): the flames drawn on a burning entity, and the fire over your
 * own view, use the soul fire sprites in place of the ordinary fire's. The mixins are {@code SoulFlameEntityRendererMixin} (notes
 * it on the entity's render state), {@code SoulFlameFeatureRendererMixin} (swaps the sprites drawing that entity's flames) and
 * {@code SoulFlameScreenMixin} (your own view).
 */
public final class SoulFlames {
	/** On an entity's render state: its flames are the blue kind. */
	public static final RenderStateDataKey<Boolean> BLUE = RenderStateDataKey.create(() -> "jaymospowers:soul_flames");

	/** The soul fire's two frames in the block atlas (the ordinary fire's are {@code fire_0} and {@code fire_1}). */
	public static final SpriteId SOUL_0 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("soul_fire_0");
	public static final SpriteId SOUL_1 = Sheets.BLOCKS_MAPPER.defaultNamespaceApply("soul_fire_1");

	private SoulFlames() {
	}

	/** From the entity renderer's extraction: whether {@code entity}'s flames are blue. */
	public static void extract(Entity entity, EntityRenderState state) {
		state.setData(BLUE, state.displayFireAnimation && SoulBurn.blue(entity) ? Boolean.TRUE : null);
	}

	public static boolean blue(EntityRenderState state) {
		return Boolean.TRUE.equals(state.getData(BLUE));
	}

	/** Whether the fire over your own view is the blue kind. */
	public static boolean ownBlue() {
		Entity player = Minecraft.getInstance().player;
		return player != null && SoulBurn.blue(player);
	}
}
