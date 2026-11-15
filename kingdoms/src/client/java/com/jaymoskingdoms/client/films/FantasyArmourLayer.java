package com.jaymoskingdoms.client.films;

import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.jaymoskingdoms.films.FilmActor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.geckolib.util.RenderUtil;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Fantasy Armor's sets on a film's actor (Andy, 2026-11-15: the Long Defence's cast in its Redeemer, Lady Maria, Golden
 * Horns, Thief, Wandering Wizard, Dark Lord and Dark Cover). Its pieces are GeckoLib models, which {@link FilmGear} can't
 * paint onto the skin, so each piece the actor has on is drawn from that mod's own model and texture, as the game has
 * them loaded (nothing of it is copied here; without Fantasy Armor nobody wears its pieces and this draws nothing). Each
 * of its armour bones rides the actor's matching bone: the helm the head, the breastplate the body, the sleeves the upper
 * arms, the leggings the thighs and the boots the shins.
 */
final class FantasyArmourLayer extends GeoRenderLayer<FilmActor, Void, LivingEntityRenderState> {
	static final String MOD = "fantasy_armor";
	/** The set each slot's piece is from (head, chest, legs, feet), or null. */
	static final DataTicket<String[]> SETS = DataTicket.create("jaymoskingdoms_film_fantasy_armour", String[].class);
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	static final String[] PIECES = {"_helmet", "_chestplate", "_leggings", "_boots"};
	/** Which of its bones each slot draws, and the actor's bone each rides: {slot, its bone, the actor's}. */
	static final String[][] RIDES = {
		{"0", "armorHead", "head"},
		{"1", "armorBody", "body"}, {"1", "armorRightArm", "right_arm"}, {"1", "armorLeftArm", "left_arm"},
		{"2", "armorRightLeg", "right_leg"}, {"2", "armorLeftLeg", "left_leg"},
		{"2", "armorRightLegCloth", "right_leg"}, {"2", "armorLeftLegCloth", "left_leg"},
		{"3", "armorRightBoot", "right_shin"}, {"3", "armorLeftBoot", "left_shin"}};
	/** A second copy of the cape its own renderer hides unless Epic Fight is loaded. */
	static final String HIDDEN = "armorCapeEpicFight";

	FantasyArmourLayer(GeoRenderer<FilmActor, Void, LivingEntityRenderState> renderer) {
		super(renderer);
	}

	@Override
	public void addRenderData(FilmActor actor, Void related, LivingEntityRenderState state, float partialTick) {
		String[] sets = new String[SLOTS.length];
		boolean any = false;
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack worn = actor.getItemBySlot(SLOTS[i]);
			Identifier id = worn.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(worn.getItem());
			if (id != null && id.getNamespace().equals(MOD) && id.getPath().endsWith(PIECES[i])) {
				sets[i] = id.getPath().substring(0, id.getPath().length() - PIECES[i].length());
				any = true;
			}
		}
		state.addGeckolibData(SETS, any ? sets : null);
	}

	@Override
	public void addPerBoneRender(RenderPassInfo<LivingEntityRenderState> info, BiConsumer<GeoBone, PerBoneRender<LivingEntityRenderState>> consumer) {
		String[] sets = info.getOrDefaultGeckolibData(SETS, null);
		if (sets == null || !info.willRender()) {
			return;
		}
		for (String[] ride : RIDES) {
			String set = sets[Integer.parseInt(ride[0])];
			if (set == null) {
				continue;
			}
			String own = ride[1];
			info.model().getBone(ride[2]).ifPresent(bone -> consumer.accept(bone, (pass, at, collector) -> {
				BakedGeoModel model = GeckoLibResources.getBakedModels().getModel(Identifier.fromNamespaceAndPath(MOD, set + "_armor"));
				if (model.isMissingno()) {
					return;
				}
				model.getBone(own).ifPresent(piece -> {
					Identifier texture = Identifier.fromNamespaceAndPath(MOD, "textures/armor/" + set + "_armor.png");
					int light = pass.packedLight(), overlay = pass.packedOverlay(), colour = pass.renderColor();
					collector.submitCustomGeometry(pass.poseStack(), RenderTypes.armorCutoutNoCull(texture), (pose, buffer) -> {
						PoseStack stack = new PoseStack();
						stack.last().set(pose);
						// (the actor's bone hands this its pivot: back to the model's own space, where both models' cubes are laid out)
						at.translateAwayFromPivotPoint(stack);
						draw(pass, stack, buffer, piece, light, overlay, colour);
					});
				});
			}));
		}
	}

	/**
	 * {@code bone} and everything under it, at its own pivot and turn. The pose another renderer last left on its bones
	 * (Fantasy Armor's own, for a player wearing the set) is set aside while it's drawn here.
	 */
	private static void draw(RenderPassInfo<LivingEntityRenderState> pass, PoseStack stack, VertexConsumer buffer, GeoBone bone, int light, int overlay,
		int colour) {
		if (bone.name().equals(HIDDEN)) {
			return;
		}
		BoneSnapshot kept = bone.frameSnapshot;
		bone.frameSnapshot = null;
		try {
			stack.pushPose();
			RenderUtil.prepMatrixForBone(stack, bone);
			bone.render(pass, stack, buffer, light, overlay, colour);
			for (GeoBone child : bone.children()) {
				draw(pass, stack, buffer, child, light, overlay, colour);
			}
			stack.popPose();
		} finally {
			bone.frameSnapshot = kept;
		}
	}
}
