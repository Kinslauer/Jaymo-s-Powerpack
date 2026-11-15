package com.jaymoskingdoms.client.oldage;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.oldage.OldAge;
import com.jaymoskingdoms.oldage.OldRuler;
import com.jaymoskingdoms.oldage.People;
import com.jaymoskingdoms.oldage.TombGuard;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * (medieval picks) The old age's rulers ({@link OldRuler}, taller than a man) and their guard ({@link TombGuard}): the
 * humanoid shape in each people's own skin (kingdoms/tools/gen_old_age.py), holding their people's arms.
 */
public final class OldAgeClient {
	/** How much taller than a man a ruler stands. */
	static final float RULER_SCALE = 1.25F;

	private OldAgeClient() {
	}

	public static void register() {
		EntityRenderers.register(OldAge.RULER, context -> new Renderer<OldRuler>(context, RULER_SCALE, "ruler", OldRuler::people));
		EntityRenderers.register(OldAge.GUARD, context -> new Renderer<TombGuard>(context, 1.0F, "guard", TombGuard::people));
	}

	/** A render state with the people it is of. */
	static final class State extends HumanoidRenderState {
		People people = People.SUNSTEP;
	}

	static final class Renderer<T extends Mob> extends HumanoidMobRenderer<T, State, HumanoidModel<State>> {
		private final float scale;
		private final Identifier[] skins = new Identifier[People.values().length];
		private final java.util.function.Function<T, People> people;

		Renderer(EntityRendererProvider.Context context, float scale, String kind, java.util.function.Function<T, People> people) {
			super(context, new HumanoidModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot()), 0.5F * scale);
			this.scale = scale;
			this.people = people;
			for (People p : People.values()) {
				this.skins[p.ordinal()] = JaymosKingdoms.id("textures/entity/old_age/" + kind + "_" + p.id + ".png");
			}
		}

		@Override
		public State createRenderState() {
			return new State();
		}

		@Override
		public void extractRenderState(T entity, State state, float partialTick) {
			super.extractRenderState(entity, state, partialTick);
			state.people = this.people.apply(entity);
		}

		@Override
		protected void scale(State state, PoseStack poseStack) {
			poseStack.scale(this.scale, this.scale, this.scale);
		}

		@Override
		public Identifier getTextureLocation(State state) {
			return this.skins[state.people.ordinal()];
		}
	}
}
