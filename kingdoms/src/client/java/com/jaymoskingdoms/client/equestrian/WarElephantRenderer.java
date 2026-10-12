package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import com.jaymoskingdoms.equestrian.WarElephant;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

/** (0.5.33) Draws war elephants: {@link WarElephantModel} in one of the breed's four hides. */
final class WarElephantRenderer extends MobRenderer<WarElephant, WarElephantRenderer.State, WarElephantModel> {
	private static final Identifier[] HIDES = new Identifier[Breed.WAR_ELEPHANT.coats.length];

	static {
		for (int i = 0; i < HIDES.length; i++) {
			HIDES[i] = JaymosKingdoms.id("textures/entity/war_elephant/" + Breed.WAR_ELEPHANT.coats[i] + ".png");
		}
	}

	static final class State extends LivingEntityRenderState {
		int coat;
		boolean saddled;
		float standAnimation;
	}

	WarElephantRenderer(EntityRendererProvider.Context context) {
		super(context, new WarElephantModel(WarElephantModel.createBodyLayer().bakeRoot()), 1.0F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WarElephant elephant, State state, float partialTicks) {
		super.extractRenderState(elephant, state, partialTicks);
		Equine e = elephant.getAttached(Equestrian.EQUINE);
		state.coat = e == null ? 0 : e.coat();
		state.saddled = !elephant.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
		state.standAnimation = elephant.getStandAnim(partialTicks);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return HIDES[Math.floorMod(state.coat, HIDES.length)];
	}
}
