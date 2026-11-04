package com.jaymoskingdoms.client.champions;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.PatronChampion;
import com.jaymoskingdoms.PatronChampions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** (0.7.518) Draws a patron's champion ({@link PatronChampion}): one model and skin each, the Forge-Thane broader, all taller than a man. */
public final class ChampionRenderer extends HumanoidMobRenderer<PatronChampion, ChampionRenderer.State, ChampionModel> {
	public static final class State extends HumanoidRenderState {
		Patron patron = Patron.HEARTHKEEPER;
		boolean dormant, turning;
		int casting = -1;
	}

	private static float scale(Patron p) {
		return switch (p) {
			case HEARTHKEEPER -> 1.30F;
			case ANVIL_FATHER -> 1.25F;
			case DEEP_MOTHER, LANTERN_WARDEN, TRUTH -> 1.40F;
			case GREEN_HUNTSMAN, CROWNED_SUN -> 1.45F;
			case ASH_WHISPERER -> 1.20F;
		};
	}

	private final Identifier texture;
	private final float scale, wide;

	private ChampionRenderer(EntityRendererProvider.Context context, Patron patron) {
		super(context, new ChampionModel(ChampionModel.createBodyLayer(patron).bakeRoot(), patron), 0.5F * scale(patron));
		this.texture = JaymosKingdoms.id("textures/entity/champion/" + PatronChampions.id(patron) + ".png");
		this.scale = scale(patron);
		this.wide = patron == Patron.ANVIL_FATHER ? 1.15F : 1.0F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PatronChampion champion, State state, float partialTicks) {
		super.extractRenderState(champion, state, partialTicks);
		state.patron = champion.patron();
		state.dormant = champion.dormant();
		state.casting = champion.casting();
		state.turning = champion.turning();
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(this.scale * this.wide, this.scale, this.scale * this.wide);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.texture;
	}

	public static void register() {
		for (Patron p : Patron.values()) {
			EntityRenderers.register(PatronChampions.typeOf(p), context -> new ChampionRenderer(context, p));
		}
	}
}
