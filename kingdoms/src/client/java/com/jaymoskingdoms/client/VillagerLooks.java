package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.VillagerHeritage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A villager's heritage look ({@link VillagerHeritage}), drawn as a player's is ({@link HeritageLooks}, {@link EmberLook})
 * on any humanoid that isn't a player (MCA's villagers, the settlements' folk): the marks on the hands and forearms, lit
 * in the dark, a Tideborn's fins, glowing eyes, and the particles. A Verdant's green and antennae are
 * {@link VerdantLook}'s and {@code McaVerdantSkinMixin}'s. MCA's eyes are their own face texture ({@link McaEyes}); folk
 * get the stock eye spots. Clothes drawn over the arms (MCA's long sleeves) hide the marks under them.
 */
public final class VillagerLooks {
	/** On a villager's render state: its heritage, which drawing, whether the eyes glow now, the glow's tint (0: none). */
	record Look(Heritage.Kind heritage, int variant, boolean eyesLit, int glow) {
	}

	static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:villager_heritage_look");
	/** The Emberborn's flames off a hand: one in this many ticks. */
	static final int EMBER_FLAME_EVERY = 12;

	private VillagerLooks() {
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (!(renderer instanceof AvatarRenderer<?>) && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(VillagerLooks::tick);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> create(RenderLayerParent<?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: a villager's heritage look (nothing for players or plain Heartlanders). */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		Heritage.Kind heritage = entity instanceof Player ? null : VillagerHeritage.of(entity);
		if (heritage == null || heritage == Heritage.Kind.HEARTLANDER || heritage == Heritage.Kind.WARRIORBORN) {
			state.setData(KEY, null);
			return;
		}
		HeritageLooks.Kind kind = HeritageLooks.Kind.of(heritage);
		boolean lit = heritage == Heritage.Kind.EMBERBORN || kind != null && switch (kind.eyes) {
			case ALWAYS -> true;
			case DAY -> entity.level().isBrightOutside();
			case NEVER -> false;
		};
		int glow = -1;
		if (heritage == Heritage.Kind.TIDEBORN) {
			// lit under water, and in the rain or wet at night
			boolean on = entity.isUnderWater() || entity.isInWaterOrRain() && entity.level().isDarkOutside();
			glow = on ? -1 : 0;
			lit &= on;
		} else if (heritage == Heritage.Kind.SYLVAN && entity.isCrouching()) {
			glow = HeritageLooks.SYLVAN_SNEAK_GLOW;
		}
		int variant = kind == null ? 0 : HeritageLooks.variant(entity.getUUID(), kind.skin.length);
		state.setData(KEY, new Look(heritage, variant, lit, glow));
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Look look = state.getData(KEY);
			if (look == null || state.isInvisible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			if (look.heritage() == Heritage.Kind.EMBERBORN) {
				collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(EmberLook.MARKS), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
				Identifier eyes = eyes(state, EmberLook.EYE_GLOW, EmberLook.EYES);
				collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
				return;
			}
			HeritageLooks.Kind kind = HeritageLooks.Kind.of(look.heritage());
			if (kind == null) {
				return;
			}
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityCutout(kind.skin[look.variant()]), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			if (look.glow() != 0) {
				collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(kind.glow[look.variant()]), lightCoords,
					OverlayTexture.NO_OVERLAY, look.glow(), null, state.outlineColor);
			}
			if (look.eyesLit() && kind.stockEyes != null) {
				collector.order(2).submitModel(model, state, poseStack, RenderTypes.eyes(eyes(state, kind.eyeColor, kind.stockEyes)), lightCoords,
					OverlayTexture.NO_OVERLAY, look.glow(), null, state.outlineColor);
			}
			if (kind == HeritageLooks.Kind.TIDEBORN) {
				HeritageLooks.fins(poseStack, collector, lightCoords, model, look.variant(), look.glow() != 0, false, false);
			}
		}
	}

	/** Glowing eyes: an MCA villager's own ({@link McaEyes}), anyone else's the stock spots. */
	private static Identifier eyes(Object state, int color, Identifier stock) {
		if (McaCompat.LOADED) {
			Identifier mca = McaEyes.glowing(state, color);
			if (mca != null) {
				return mca;
			}
		}
		return stock;
	}

	/** Every client tick: now and then a particle off a villager's hand, as off a player's. */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof Player || !(entity instanceof LivingEntity villager) || villager.isInvisible()) {
				continue;
			}
			Heritage.Kind heritage = VillagerHeritage.of(villager);
			if (heritage == null || heritage == Heritage.Kind.HEARTLANDER) {
				continue;
			}
			RandomSource random = villager.getRandom();
			if (heritage == Heritage.Kind.EMBERBORN) {
				if (random.nextInt(EMBER_FLAME_EVERY) == 0 && !villager.isInWaterOrRain()) {
					float yaw = villager.yBodyRot * Mth.DEG_TO_RAD;
					double side = (random.nextBoolean() ? 1.0 : -1.0) * villager.getBbWidth() * 0.6;
					level.addParticle(ParticleTypes.SMALL_FLAME, villager.getX() - Mth.cos(yaw) * side, villager.getY() + villager.getBbHeight() * 0.4,
						villager.getZ() - Mth.sin(yaw) * side, 0.0, 0.02, 0.0);
				}
				continue;
			}
			HeritageLooks.Kind kind = HeritageLooks.Kind.of(heritage);
			if (kind != null && kind.particleEvery > 0 && random.nextInt(kind.particleEvery) == 0) {
				HeritageLooks.particle(level, villager, kind, random, villager.isInWaterOrRain());
			}
		}
	}
}
