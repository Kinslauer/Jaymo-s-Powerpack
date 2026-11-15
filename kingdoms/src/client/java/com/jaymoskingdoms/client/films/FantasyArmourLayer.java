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
import com.mojang.math.Axis;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Fantasy Armor's sets on a film's actor (Andy, 2026-11-15: the Long Defence's cast in its Redeemer, Lady Maria, Golden
 * Horns, Thief, Wandering Wizard, Dark Lord and Dark Cover). Its pieces are GeckoLib models, which {@link FilmGear} can't
 * paint onto the skin, so each piece the actor has on is drawn from that mod's own model and texture, as the game has
 * them loaded (nothing of it is copied here; without Fantasy Armor nobody wears its pieces and this draws nothing). Each
 * of its armour bones rides the actor's matching bone: the helm the head, the breastplate the body, the sleeves the upper
 * arms, the leggings the thighs and the boots the shins.
 * <p>
 * Andy (2026-11-15): the Quiet One's Redeemer plate "should weave onto his body like the symbiote usually does". A set in
 * {@code WEAVES} isn't simply there: put on, it winds out over the body in the symbiote suit's order (the chest first,
 * then the arms, the head and the legs, the boots last), each piece growing out from its joint, twisting as it comes and
 * swollen at first, then settling flat.
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
	/** The sets that weave on, and how long the whole weave takes (ticks). */
	static final Set<String> WEAVES = Set.of("redeemer");
	static final float WEAVE_TICKS = 30.0F;
	/**
	 * When each piece starts winding out (share of the weave, by slot: helm, breastplate, leggings, boots; the sleeves go
	 * with the breastplate, a little after it, ARM_AFTER) and how long each takes to lay (LAY_SPAN), as the symbiote's suit
	 * does (body, arms, head, legs). Laying, a piece is turned up to TWIST degrees round its joint and up to BULGE wider.
	 */
	static final float[] WEAVE_START = {0.16F, 0.0F, 0.22F, 0.34F};
	static final float ARM_AFTER = 0.1F, LAY_SPAN = 0.5F, TWIST = 70.0F, BULGE = 0.25F;
	/** How long each slot's piece has been on (ticks), for the weave. */
	static final DataTicket<float[]> WORN_FOR = DataTicket.create("jaymoskingdoms_film_fantasy_worn", float[].class);
	/** The set each actor had on each slot, and the tick it went on. */
	private static final Map<FilmActor, Object[]> SEEN = new WeakHashMap<>();

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
		Object[] seen = SEEN.computeIfAbsent(actor, k -> new Object[] {new String[SLOTS.length], new int[SLOTS.length]});
		String[] had = (String[])seen[0];
		int[] since = (int[])seen[1];
		float[] worn = new float[SLOTS.length];
		for (int i = 0; i < SLOTS.length; i++) {
			if (!Objects.equals(had[i], sets[i])) {
				had[i] = sets[i];
				since[i] = actor.tickCount;
			}
			worn[i] = actor.tickCount - since[i] + partialTick;
		}
		state.addGeckolibData(WORN_FOR, worn);
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
			int slot = Integer.parseInt(ride[0]);
			float laid = laid(set, slot, own, info.getOrDefaultGeckolibData(WORN_FOR, null));
			if (laid <= 0.0F) {
				continue;
			}
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
						draw(pass, stack, buffer, piece, light, overlay, colour, laid);
					});
				});
			}));
		}
	}

	/** How far {@code bone} (slot {@code slot}'s) of {@code set} has woven on, 0 to 1: always 1 for a set that doesn't weave. */
	private static float laid(String set, int slot, String bone, float[] worn) {
		if (!WEAVES.contains(set) || worn == null) {
			return 1.0F;
		}
		float start = WEAVE_START[slot] + (bone.endsWith("Arm") ? ARM_AFTER : 0.0F);
		float t = Mth.clamp((worn[slot] / WEAVE_TICKS - start) / LAY_SPAN, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	/**
	 * {@code bone} and everything under it, at its own pivot and turn. The pose another renderer last left on its bones
	 * (Fantasy Armor's own, for a player wearing the set) is set aside while it's drawn here. Not yet {@code laid} (under 1)
	 * it's winding out from its joint: turned, swollen and short at first, flat and whole at the end.
	 */
	private static void draw(RenderPassInfo<LivingEntityRenderState> pass, PoseStack stack, VertexConsumer buffer, GeoBone bone, int light, int overlay,
		int colour, float laid) {
		if (bone.name().equals(HIDDEN)) {
			return;
		}
		BoneSnapshot kept = bone.frameSnapshot;
		bone.frameSnapshot = null;
		try {
			stack.pushPose();
			RenderUtil.prepMatrixForBone(stack, bone);
			if (laid < 1.0F) {
				bone.translateToPivotPoint(stack);
				float wide = 1.0F + BULGE * Mth.sin(laid * Mth.PI);
				stack.rotate(Axis.YP.rotationDegrees(TWIST * (1.0F - laid) * (bone.name().contains("Left") ? -1.0F : 1.0F)));
				stack.scale(wide, Math.max(laid, 0.02F), wide);
				bone.translateAwayFromPivotPoint(stack);
			}
			bone.render(pass, stack, buffer, light, overlay, colour);
			for (GeoBone child : bone.children()) {
				draw(pass, stack, buffer, child, light, overlay, colour, 1.0F);
			}
			stack.popPose();
		} finally {
			bone.frameSnapshot = kept;
		}
	}
}
