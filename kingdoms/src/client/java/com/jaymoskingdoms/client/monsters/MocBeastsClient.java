package com.jaymoskingdoms.client.monsters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.monsters.BigGolem;
import com.jaymoskingdoms.monsters.GolemRock;
import com.jaymoskingdoms.monsters.Manticore;
import com.jaymoskingdoms.monsters.MocBeasts;
import com.jaymoskingdoms.monsters.Scorpion;
import com.jaymoskingdoms.monsters.Zebra;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.RenderShape;

/**
 * The renderers of the Mo' Creatures beasts Kingdoms keeps (the Nether scorpion, the manticore, the big golem and the
 * blocks it throws; see {@link MocBeasts}). Their models are ports of the originals (GPL-3, see {@code licenses/mocreatures});
 * the textures are the originals' too, under {@code textures/entity/moc/}.
 */
public final class MocBeastsClient {
	private MocBeastsClient() {
	}

	static Identifier texture(String name) {
		return JaymosKingdoms.id("textures/entity/moc/" + name + ".png");
	}

	public static void register() {
		EntityRenderers.register(MocBeasts.NETHER_SCORPION, c -> new Beast<Scorpion>(c, new ScorpionModel(ScorpionModel.createBodyLayer().bakeRoot()), 0.6F, 1.0F,
			s -> texture("scorpion_nether"), (scorpion, state) -> {
				state.babies = scorpion.hasBrood();
				state.stinging = scorpion.stinging();
				state.clawCounter = (int)(state.attackAnim * 20.0F);
			}, scorpion -> scorpion.isBaby() ? 0.5F : 1.0F));
		EntityRenderers.register(MocBeasts.MANTICORE, c -> new Beast<Manticore>(c, new ManticoreModel(ManticoreModel.createBodyLayer().bakeRoot()), 0.9F, 1.25F,
			s -> texture(MANTICORE_COATS[Math.floorMod(s.variant, MANTICORE_COATS.length)]), (manticore, state) -> {
				state.variant = manticore.getUUID().hashCode();
				state.airborne = state.flapping = manticore.airborne();
				state.stinging = manticore.stinging();
				state.mouth = (int)(state.attackAnim * 20.0F);
			}, manticore -> 1.0F));
		EntityRenderers.register(MocBeasts.ZEBRA, c -> zebra(c, "zebra"));
		EntityRenderers.register(MocBeasts.ZORSE, c -> zebra(c, "zorse"));
		com.jaymoskingdoms.client.dragons.WyvernClient.register();
		EntityRenderers.register(MocBeasts.BIG_GOLEM, GolemRenderer::new);
		EntityRenderers.register(MocBeasts.GOLEM_ROCK, RockRenderer::new);
	}

	private static Beast<Zebra> zebra(EntityRendererProvider.Context context, String coat) {
		return new Beast<>(context, new ZebraModel(ZebraModel.createBodyLayer().bakeRoot()), 0.75F, 1.0F, s -> texture(coat), (horse, state) -> {
			state.saddled = horse.isSaddled();
			state.ridden = horse.isVehicle();
			state.eating = horse.getEatAnim(1.0F) > 0.0F;
			state.rearing = horse.getStandAnim(1.0F) > 0.0F;
			state.mouthOpen = horse.getMouthAnim(1.0F) > 0.0F;
			state.tailMoving = horse.tailCounter > 0;
		}, horse -> horse.isBaby() ? 0.5F : 1.0F);
	}

	private static final String[] MANTICORE_COATS = {"manticore", "manticore_dark", "manticore_blue", "manticore_green"};

	/** One renderer for the beasts that only need a model, a texture, a size and a few flags copied across. */
	static final class Beast<T extends Mob> extends MobRenderer<T, BeastState, EntityModel<BeastState>> {
		private final Function<BeastState, Identifier> texture;
		private final BiConsumer<T, BeastState> extract;
		private final Function<T, Float> size;
		private final float scale;

		Beast(EntityRendererProvider.Context context, EntityModel<BeastState> model, float shadow, float scale, Function<BeastState, Identifier> texture,
			BiConsumer<T, BeastState> extract, Function<T, Float> size) {
			super(context, model, shadow);
			this.texture = texture;
			this.extract = extract;
			this.size = size;
			this.scale = scale;
		}

		@Override
		public BeastState createRenderState() {
			return new BeastState();
		}

		@Override
		public void extractRenderState(T mob, BeastState state, float partialTicks) {
			super.extractRenderState(mob, state, partialTicks);
			state.attackAnim = mob.getSwingAnimation(partialTicks);
			this.extract.accept(mob, state);
			state.tick = this.size.apply(mob);
		}

		@Override
		protected void scale(BeastState state, PoseStack poseStack) {
			float s = this.scale * state.tick;
			poseStack.scale(s, s, s);
		}

		@Override
		public Identifier getTextureLocation(BeastState state) {
			return this.texture.apply(state);
		}
	}

	/** The big golem: its body of cubes, 1.8 times as it was drawn, and its glow while it is worked up. */
	static final class GolemRenderer extends MobRenderer<BigGolem, BeastState, GolemModel> {
		private static final Identifier BODY = texture("golem");
		private static final Identifier[] GLOW = {null, texture("golem_effect_1"), texture("golem_effect_2"), texture("golem_effect_3"), texture("golem_effect_4")};

		GolemRenderer(EntityRendererProvider.Context context) {
			super(context, new GolemModel(GolemModel.createBodyLayer().bakeRoot()), 1.2F);
			GolemModel glowModel = new GolemModel(GolemModel.createBodyLayer().bakeRoot());
			for (int n = 1; n <= 4; n++) {
				this.addLayer(new Glow(this, glowModel, n));
			}
		}

		@Override
		public BeastState createRenderState() {
			return new BeastState();
		}

		@Override
		public void extractRenderState(BigGolem golem, BeastState state, float partialTicks) {
			super.extractRenderState(golem, state, partialTicks);
			state.golemState = golem.state();
			state.open = golem.chestOpen();
			state.raised = golem.raised();
			for (int i = 0; i < BigGolem.SLOTS; i++) {
				state.cubes[i] = golem.cube(i);
			}
		}

		@Override
		protected void scale(BeastState state, PoseStack poseStack) {
			poseStack.scale(1.8F, 1.8F, 1.8F);
		}

		@Override
		public Identifier getTextureLocation(BeastState state) {
			return BODY;
		}

		/** The swirl over the whole body that the original golem wore, a different one for each state. */
		private static final class Glow extends EnergySwirlLayer<BeastState, GolemModel> {
			private final GolemModel model;
			private final int golemState;

			Glow(RenderLayerParent<BeastState, GolemModel> parent, GolemModel model, int golemState) {
				super(parent);
				this.model = model;
				this.golemState = golemState;
			}

			@Override
			protected boolean isPowered(BeastState state) {
				return state.golemState == this.golemState && !state.isInvisible;
			}

			@Override
			protected float xOffset(float tick) {
				return tick * 0.01F;
			}

			@Override
			protected Identifier getTextureLocation() {
				return GLOW[this.golemState];
			}

			@Override
			protected GolemModel model() {
				return this.model;
			}
		}
	}

	/** A block in the air, the golem's (pulled toward it or hurled), tumbling as it goes. */
	static final class RockRenderer extends EntityRenderer<GolemRock, RockRenderer.State> {
		static final class State extends EntityRenderState {
			final MovingBlockRenderState block = new MovingBlockRenderState();
		}

		RockRenderer(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.3F;
		}

		@Override
		public State createRenderState() {
			return new State();
		}

		@Override
		public void extractRenderState(GolemRock rock, State state, float partialTicks) {
			super.extractRenderState(rock, state, partialTicks);
			BlockPos at = BlockPos.containing(rock.getX(), rock.getBoundingBox().maxY, rock.getZ());
			state.block.blockState = rock.blockState();
			state.block.blockPos = at;
			state.block.randomSeedPos = at;
			if (rock.level() instanceof ClientLevel level) {
				state.block.biome = level.getBiome(at);
				state.block.cardinalLighting = level.cardinalLighting();
				state.block.lightEngine = level.getLightEngine();
			}
			state.ageInTicks = rock.tickCount + partialTicks;
		}

		@Override
		public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
			if (state.block.blockState.getRenderShape() == RenderShape.MODEL) {
				poseStack.pushPose();
				poseStack.translate(0.0F, 0.3F, 0.0F);
				poseStack.rotateDegrees(com.mojang.math.Axis.YP, state.ageInTicks * 17.0F);
				poseStack.rotateDegrees(com.mojang.math.Axis.XP, state.ageInTicks * 11.0F);
				poseStack.scale(0.6F, 0.6F, 0.6F);
				poseStack.translate(-0.5F, -0.5F, -0.5F);
				collector.submitMovingBlock(poseStack, state.block, state.outlineColor);
				poseStack.popPose();
			}
			super.submit(state, poseStack, collector, camera);
		}
	}
}
