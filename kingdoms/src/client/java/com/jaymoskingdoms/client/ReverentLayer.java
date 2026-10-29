package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Armory;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * (0.7.326) {@link Armory}'s Reverent Warplate, drawn over anything humanoid that wears it: eanncrod's model (Blockbench
 * Workshop, CC BY 4.0, credits in textures/entity/bb/CREDITS.txt), each slot's pieces hung on the wearer's own head,
 * body, arms and legs ({@link WornModel}, so they take every pose the vanilla armour does). The helm on the head; the
 * breastplate, pauldrons and cape on the body and arms; the cuisses and the skirt on the legs and waist; the sabatons on
 * the feet.
 */
public final class ReverentLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/bb/reverent_warplate.png");
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Each slot's pieces: the model's bone, and the wearer's bone it hangs on. */
	private static final Map<EquipmentSlot, Map<String, String>> PIECES = new EnumMap<>(EquipmentSlot.class);

	static {
		PIECES.put(EquipmentSlot.HEAD, Map.of("armorHead", "head"));
		PIECES.put(EquipmentSlot.CHEST, Map.of("armorBody", "body", "armorRightArm", "right_arm", "armorLeftArm", "left_arm"));
		// the skirt's bone hangs at the feet, from the body
		PIECES.put(EquipmentSlot.LEGS, Map.of("armorRightLeg", "right_leg", "armorLeftLeg", "left_leg", "bipedWaist", "body"));
		PIECES.put(EquipmentSlot.FEET, Map.of("armorRightBoot", "right_leg", "armorLeftBoot", "left_leg"));
	}

	/** The model, read once: its pieces never move on their own, so every wearer's layer hangs the same ones. */
	private static BbModel model;

	private final Map<EquipmentSlot, WornModel> worn = new EnumMap<>(EquipmentSlot.class);

	private ReverentLayer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
		super(renderer);
		if (model == null) {
			model = BbModel.load(JaymosKingdoms.MOD_ID, "reverent_warplate");
		}
		PIECES.forEach((slot, pieces) -> this.worn.put(slot, new WornModel(skeleton(pieces), this::getParentModel)));
	}

	/** The humanoid's bones at their usual places, with {@code pieces} hung on them. */
	private static ModelPart skeleton(Map<String, String> pieces) {
		Map<String, ModelPart> bones = new LinkedHashMap<>();
		bones.put("head", bone(pieces, "head", PartPose.ZERO, Map.of("hat", new ModelPart(List.of(), Map.of()))));
		bones.put("body", bone(pieces, "body", PartPose.ZERO, Map.of()));
		bones.put("right_arm", bone(pieces, "right_arm", PartPose.offset(-5.0F, 2.0F, 0.0F), Map.of()));
		bones.put("left_arm", bone(pieces, "left_arm", PartPose.offset(5.0F, 2.0F, 0.0F), Map.of()));
		bones.put("right_leg", bone(pieces, "right_leg", PartPose.offset(-1.9F, 12.0F, 0.0F), Map.of()));
		bones.put("left_leg", bone(pieces, "left_leg", PartPose.offset(1.9F, 12.0F, 0.0F), Map.of()));
		return new ModelPart(List.of(), bones);
	}

	private static ModelPart bone(Map<String, String> pieces, String name, PartPose pose, Map<String, ModelPart> extra) {
		Map<String, ModelPart> children = new LinkedHashMap<>(extra);
		pieces.forEach((piece, on) -> {
			if (on.equals(name)) {
				children.put(piece, model.part(piece));
			}
		});
		ModelPart part = new ModelPart(List.of(), children);
		part.setInitialPose(pose);
		part.loadPose(pose);
		return part;
	}

	/** Puts this layer on every humanoid renderer. */
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ReverentLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer) {
		return new ReverentLayer((RenderLayerParent) renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = switch (slot) {
				case HEAD -> state.headEquipment;
				case CHEST -> state.chestEquipment;
				case LEGS -> state.legsEquipment;
				default -> state.feetEquipment;
			};
			ArmorType type = switch (slot) {
				case HEAD -> ArmorType.HELMET;
				case CHEST -> ArmorType.CHESTPLATE;
				case LEGS -> ArmorType.LEGGINGS;
				default -> ArmorType.BOOTS;
			};
			if (stack.isEmpty() || stack.getItem() != Armory.REVERENT_PIECES.get(type)) {
				continue;
			}
			collector.submitModel(this.worn.get(slot), state, poseStack, RenderTypes.armorCutoutNoCull(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
	}
}
