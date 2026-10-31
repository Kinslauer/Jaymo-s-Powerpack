package com.jaymoskingdoms.client;

import com.jaymoskingdoms.EarthlingSurge;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
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
	/**
	 * On a villager's render state: its heritage, which drawing, whether the eyes glow now, the glow's tint (0: none).
	 * (0.7.214) {@code style}: its heritage variant's look ({@link VariantLooks}), drawn instead of the first's.
	 */
	record Look(Heritage.Kind heritage, int variant, boolean eyesLit, int glow, boolean avatar, VariantLooks.@Nullable Style style, int palette) {
	}

	/**
	 * (0.7.443, Havens patch 7b: "more monstrous non-humans") A Warrior-born villager's tail, always out (only players tie
	 * theirs down): its segments (length, thickness in pixels), how each tapers, the bends down it, how far and fast it sways.
	 */
	static final float TAIL_LENGTH = 3.2F, TAIL_THICK = 2.2F, TAIL_TAPER = 0.93F, TAIL_SWAY = 0.18F, TAIL_SWAY_SPEED = 0.06F;
	static final float[] TAIL_BENDS = {-0.85F, -0.15F, 0.05F, 0.15F, 0.25F, 0.35F, 0.45F, 0.5F};
	static final float[] TAIL_ROOT = {0.0F, 10.5F, 1.8F};
	static final Identifier TAIL = JaymosKingdoms.id("textures/entity/heritage/warrior_tail.png");
	private static net.minecraft.client.model.geom.ModelPart tailSegment;

	/** (0.7.187) A Flame Avatar's char and lava veins (the same as a player avatar's in Jaymo's Powers; tools/gen_flame_avatar.py). */
	static final Identifier AVATAR_CHAR = JaymosKingdoms.id("textures/entity/ember/avatar_char.png");
	static final Identifier AVATAR_VEINS = JaymosKingdoms.id("textures/entity/ember/avatar_veins.png");

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
		if (GreenskinLooks.extract(entity, state)) {
			// (0.7.292) a goblin or an orc: its look is GreenskinLooks'
			state.setData(KEY, null);
			return;
		}
		Heritage.Kind heritage = entity instanceof Player ? null : VillagerHeritage.of(entity);
		// (0.7.214) a plain Heartlander or Warrior-born has no look; their variants do
		VariantLooks.Style style = heritage == null ? null : VariantLooks.of(entity);
		// ((0.7.443) a Warrior-born's tail always shows, so they keep a look)
		if (heritage == null || style == null && heritage == Heritage.Kind.HEARTLANDER) {
			state.setData(KEY, null);
			return;
		}
		HeritageLooks.Kind kind = HeritageLooks.Kind.of(heritage);
		boolean lit = kind != null ? HeritageLooks.eyesLit(kind, entity, style)
			: style != null ? style.eyes() != null && HeritageLooks.eyesLit(style.eyes(), entity) : heritage == Heritage.Kind.EMBERBORN;
		int glow = kind == null ? -1 : HeritageLooks.mood(kind, entity);
		if (heritage == Heritage.Kind.TIDEBORN) {
			// lit under water, and in the rain or wet at night
			boolean on = entity.isUnderWater() || entity.isInWaterOrRain() && entity.level().isDarkOutside();
			glow = on ? -1 : 0;
			lit &= on;
		} else if (heritage == Heritage.Kind.SYLVAN && entity.isCrouching()) {
			glow = HeritageLooks.SYLVAN_SNEAK_GLOW;
		} else if (heritage == Heritage.Kind.EARTHLING) {
			// (0.7.184) a villager is never a Z Fighter: no ki glow (EarthlingSurge.shows)
			glow = EarthlingSurge.surging(entity) && EarthlingSurge.shows(entity) ? -1 : 0;
		}
		// (0.7.262) a Bio-built villager (only ever born to a Bio-built parent) shows the first form
		int variant = kind == null || kind == HeritageLooks.Kind.BIOBUILT ? 0 : HeritageLooks.variant(entity.getUUID(), kind.skin.length);
		// (0.7.187) one of the Nether's Flame Avatars: wreathed in fire (not in water)
		boolean avatar = com.jaymoskingdoms.nether.AvatarCamps.avatar(entity);
		if (avatar && !entity.isInWater() && !entity.isInvisible()) {
			state.displayFireAnimation = true;
		}
		state.setData(KEY, new Look(heritage, variant, lit, glow, avatar, style, heritage == Heritage.Kind.BIOBUILT ? BioForms.palette(entity) : 0));
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Look look = state.getData(KEY);
			boolean form = state.getData(BioForms.KEY) != null;
			if (look == null && !form || state.isInvisible) {
				return;
			}
			HumanoidModel<S> model = this.getParentModel();
			// on an MCA villager, its own skin body just over the skin (and for the glow, over the face), so the looks
			// don't fight MCA's skin for the same depth (McaLookModels); anyone else's, the model as it is
			HumanoidModel<S> marks = model, glowing = model;
			if (McaCompat.LOADED) {
				HumanoidModel<S> mcaMarks = mcaMarks(model), mcaGlow = mcaGlow(model);
				if (mcaMarks != null && mcaGlow != null) {
					marks = mcaMarks;
					glowing = mcaGlow;
				}
			}
			if (form && BioForms.submit(poseStack, collector, lightCoords, state, marks, glowing, false, false)) {
				// (0.7.363) a Bio-built player's corpse: the form they died in (BioForms.extract)
				return;
			}
			if (look.avatar()) {
				// (0.7.187) burnt to char, the lava veins glowing through
				collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityTranslucent(AVATAR_CHAR), lightCoords, OverlayTexture.NO_OVERLAY,
					-1, null, state.outlineColor);
				collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(AVATAR_VEINS), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
				return;
			}
			if (look.heritage() == Heritage.Kind.EMBERBORN && look.style() == null) {
				collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(EmberLook.MARKS), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
				Identifier eyes = eyes(state, EmberLook.EYE_GLOW, EmberLook.EYES);
				collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
					state.outlineColor);
				return;
			}
			if (look.heritage() == Heritage.Kind.WARRIORBORN) {
				// (0.7.443) its tail, out for all to see
				tail(poseStack, collector, lightCoords, marks, state.ageInTicks);
			}
			HeritageLooks.Kind kind = HeritageLooks.Kind.of(look.heritage());
			VariantLooks.Style style = look.style();
			if (kind == null && style == null) {
				return;
			}
			collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityCutout(style != null ? style.skin() : kind.skin[look.variant()]), lightCoords,
				OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
			if (look.glow() != 0) {
				collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(style != null ? style.glow() : kind.glow[look.variant()]), lightCoords,
					OverlayTexture.NO_OVERLAY, look.glow(), null, state.outlineColor);
			}
			if (look.eyesLit() && style != null && style.stockEyes() != null) {
				collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(eyes(state, style.eyeColor(), style.stockEyes())), lightCoords,
					OverlayTexture.NO_OVERLAY, look.glow(), null, state.outlineColor);
			} else if (look.eyesLit() && kind != null && kind.stockEyes != null) {
				collector.order(2).submitModel(glowing, state, poseStack, RenderTypes.eyes(eyes(state, kind.eyeColor, kind.stockEyes)), lightCoords,
					OverlayTexture.NO_OVERLAY, look.glow(), null, state.outlineColor);
			}
			if (kind == HeritageLooks.Kind.TIDEBORN) {
				HeritageLooks.finTextures(style);
				HeritageLooks.fins(poseStack, collector, lightCoords, model, look.variant(), look.glow() != 0, false, false);
			}
			if (kind == HeritageLooks.Kind.STONEBORN) {
				// (0.7.443) cracks over the face and chest, and the heavy brow
				collector.order(1).submitModel(marks, state, poseStack, RenderTypes.entityCutout(HeritageLooks.STONE_CRACKS), lightCoords,
					OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
				HeritageLooks.brow(poseStack, collector, lightCoords, marks);
			}
			if (kind == HeritageLooks.Kind.BIOBUILT) {
				// (0.7.443) more than marks: the first form's horns, beak, claws and growths, and its tail
				BioForms.villagerForm(poseStack, collector, lightCoords, marks, look.palette(), state.ageInTicks);
			}
		}
	}

	/** (0.7.443) A Warrior-born villager's tail: a chain of furred segments off the small of the back, bending down and out, swaying. */
	private static void tail(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidModel<?> model, float age) {
		if (!model.body.visible) {
			return;
		}
		if (tailSegment == null) {
			net.minecraft.client.model.geom.builders.MeshDefinition mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
			mesh.getRoot().addOrReplaceChild("segment", net.minecraft.client.model.geom.builders.CubeListBuilder.create().texOffs(0, 0)
				.addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F), net.minecraft.client.model.geom.PartPose.ZERO);
			tailSegment = net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 32, 32).bakeRoot();
		}
		poseStack.pushPose();
		model.body.translateAndRotate(poseStack);
		poseStack.translate(TAIL_ROOT[0] / 16.0F, TAIL_ROOT[1] / 16.0F, TAIL_ROOT[2] / 16.0F);
		float thick = TAIL_THICK / 3.0F;
		for (int i = 0; i < TAIL_BENDS.length; i++) {
			float sway = TAIL_SWAY * Mth.sin(age * TAIL_SWAY_SPEED - i * 0.5F) * (0.3F + i / (float)TAIL_BENDS.length);
			poseStack.rotate(new org.joml.Quaternionf().rotationZYX(0.0F, sway, TAIL_BENDS[i]));
			poseStack.pushPose();
			poseStack.scale(thick, thick, TAIL_LENGTH / 4.0F);
			collector.order(1).submitModelPart(tailSegment, poseStack, RenderTypes.entityCutout(TAIL), light, OverlayTexture.NO_OVERLAY, null, -1);
			poseStack.popPose();
			poseStack.translate(0.0F, 0.0F, TAIL_LENGTH / 16.0F);
			poseStack.scale(TAIL_TAPER, TAIL_TAPER, TAIL_TAPER);
		}
		poseStack.popPose();
	}

	@SuppressWarnings("unchecked")
	private static <S extends HumanoidRenderState> @Nullable HumanoidModel<S> mcaMarks(HumanoidModel<S> parent) {
		return (HumanoidModel<S>) McaLookModels.marks(parent);
	}

	@SuppressWarnings("unchecked")
	private static <S extends HumanoidRenderState> @Nullable HumanoidModel<S> mcaGlow(HumanoidModel<S> parent) {
		return (HumanoidModel<S>) McaLookModels.glow(parent);
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
			if (heritage == null) {
				continue;
			}
			RandomSource random = villager.getRandom();
			VariantLooks.Style style = VariantLooks.of(villager);
			if (style != null && !com.jaymoskingdoms.nether.AvatarCamps.avatar(villager)) {
				// (0.7.214) a variant's own particle
				if (VariantLooks.particleNow(style, villager, random)) {
					VariantLooks.particle(level, villager, style, random);
				}
				continue;
			}
			if (heritage == Heritage.Kind.HEARTLANDER) {
				continue;
			}
			if (heritage == Heritage.Kind.EMBERBORN) {
				if (com.jaymoskingdoms.nether.AvatarCamps.avatar(villager) && random.nextInt(3) == 0 && !villager.isInWaterOrRain()) {
					// (0.7.187) embers and smoke off an avatar's whole body
					double x = villager.getX() + (random.nextDouble() - 0.5) * villager.getBbWidth();
					double y = villager.getY() + random.nextDouble() * villager.getBbHeight();
					double z = villager.getZ() + (random.nextDouble() - 0.5) * villager.getBbWidth();
					level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME, x, y, z, 0.0, 0.04, 0.0);
				}
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
