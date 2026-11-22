package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.PowersClimbClient;
import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import com.jaymoskingdoms.equestrian.WarElephant;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/**
 * (0.5.34) Draws war elephants and woolly mammoths: {@link WarElephantModel} in its breed's hide (Mo' Creatures' Indian,
 * African and royal elephants; its mammoth, and two coats made from it).
 */
final class WarElephantRenderer extends MobRenderer<WarElephant, WarElephantRenderer.State, WarElephantModel> {
	private static final Map<String, Identifier> HIDES = new HashMap<>();
	private static final Identifier FALLBACK = hide("indian");

	static final class State extends LivingEntityRenderState {
		Identifier hide = FALLBACK;
		boolean saddled, mammoth, bigEars, royal;
		float standAnimation;
		/** Kneeling for a rider (0 to 1), and how far through a trunk lift they are (0 to 1; negative: none). */
		float kneel, lift = -1.0F;
	}

	private static Identifier hide(String coat) {
		return HIDES.computeIfAbsent(coat, c -> JaymosKingdoms.id("textures/entity/war_elephant/" + c + ".png"));
	}

	WarElephantRenderer(EntityRendererProvider.Context context) {
		super(context, new WarElephantModel(WarElephantModel.createBodyLayer().bakeRoot()), 1.3F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WarElephant elephant, State state, float partialTicks) {
		super.extractRenderState(elephant, state, partialTicks);
		Equine e = elephant.getAttached(Equestrian.EQUINE);
		String coat = e == null ? "indian" : e.coatName();
		state.hide = hide(coat);
		state.mammoth = e != null && e.breed() == Breed.WOOLLY_MAMMOTH;
		state.bigEars = coat.equals("african");
		state.royal = coat.equals("royal");
		state.saddled = !elephant.getItemBySlot(EquipmentSlot.SADDLE).isEmpty();
		state.standAnimation = elephant.getStandAnim(partialTicks);
		state.kneel = Bonding.kneelAmount(elephant, elephant.level().getGameTime() + partialTicks);
		PowersClimbClient.Pose boarding = PowersClimbClient.on(elephant.getId(), partialTicks);
		state.lift = boarding != null && boarding.style() == PowersClimbClient.TRUNK ? boarding.share() : -1.0F;
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.hide;
	}
}
