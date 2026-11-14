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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * (0.7.326) {@link Armory}'s Reverent Warplate, drawn over anything humanoid that wears it: eanncrod's model (Blockbench
 * Workshop, CC BY 4.0, credits in textures/entity/bb/CREDITS.txt), each slot's pieces hung on the wearer's own head,
 * body, arms and legs ({@link WornModel}, so they take every pose the vanilla armour does). The helm on the head; the
 * breastplate, pauldrons and cape on the body and arms; the cuisses and the skirt on the legs and waist; the sabatons on
 * the feet.
 * <p>
 * (0.7.339) The Heavenly Mantle ({@link Armory#HEAVENLY_PIECES}) the same way: eanncrod's model of the same make, its
 * bones named alike. Each set is its own layer ({@link Set}).
 * <p>
 * (0.7.351) The Kintsugi Armour and the Primal Visage ({@link Armory#KINTSUGI_PIECES}, {@link Armory#PRIMAL_PIECES}), both
 * eanncrod's, the same way. The Primal Visage keeps its skirt ({@code armorWaist}) straight under the body, with no
 * {@code bipedWaist} above it, so a bone the model doesn't have is skipped and the skirt is hung by its own name.
 * <p>
 * The fortress knight's vigil plate (our own, kingdoms/tools/gen_fortress_knight.py) the same way, but known by the
 * look its pieces carry ({@link com.jaymoskingdoms.nether.FortressKnight#PLATE}), not by their items.
 * <p>
 * The Frontier sets ({@link com.jaymoskingdoms.FrontierArmour}) the same way: one model a set, one texture a colouring.
 */
public final class ReverentLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	/** A set drawn this way: its model (and texture) name and its pieces by {@link ArmorType}. */
	record Set(String model, String texture, Map<ArmorType, net.minecraft.world.item.Item> pieces,
		@org.jspecify.annotations.Nullable ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> look) {
		Set(String model, Map<ArmorType, net.minecraft.world.item.Item> pieces) {
			this(model, model, pieces, null);
		}

		Set(String model, Map<ArmorType, net.minecraft.world.item.Item> pieces,
			@org.jspecify.annotations.Nullable ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> look) {
			this(model, model, pieces, look);
		}

		/** Whether {@code stack}, worn as {@code type}, is this set's piece: by its look if the set goes by one, else by the item. */
		boolean has(ItemStack stack, ArmorType type) {
			if (this.look == null) {
				return stack.getItem() == this.pieces.get(type);
			}
			net.minecraft.world.item.equipment.Equippable worn = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
			return worn != null && worn.assetId().filter(this.look::equals).isPresent();
		}
	}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** A skirt hung by its own name only in a model with no {@code bipedWaist} (in the others it's under that bone already). */
	private static final String WAIST = "armorWaist";

	/** Each slot's pieces: the model's bone, and the wearer's bone it hangs on. */
	private static final Map<EquipmentSlot, Map<String, String>> PIECES = new EnumMap<>(EquipmentSlot.class);

	static {
		PIECES.put(EquipmentSlot.HEAD, Map.of("armorHead", "head"));
		PIECES.put(EquipmentSlot.CHEST, Map.of("armorBody", "body", "armorRightArm", "right_arm", "armorLeftArm", "left_arm"));
		// the skirt's bone hangs at the feet, from the body
		PIECES.put(EquipmentSlot.LEGS, Map.of("armorRightLeg", "right_leg", "armorLeftLeg", "left_leg", "bipedWaist", "body", WAIST, "body"));
		PIECES.put(EquipmentSlot.FEET, Map.of("armorRightBoot", "right_leg", "armorLeftBoot", "left_leg"));
	}

	/** The models, each read once: their pieces never move on their own, so every wearer's layer hangs the same ones. */
	private static final Map<String, BbModel> MODELS = new java.util.HashMap<>();

	private final Set set;
	private final Identifier texture;
	private final Map<EquipmentSlot, WornModel> worn = new EnumMap<>(EquipmentSlot.class);

	private ReverentLayer(RenderLayerParent<S, HumanoidModel<S>> renderer, Set set) {
		super(renderer);
		this.set = set;
		this.texture = JaymosKingdoms.id("textures/entity/bb/" + set.texture() + ".png");
		BbModel model = MODELS.computeIfAbsent(set.model(), name -> BbModel.load(JaymosKingdoms.MOD_ID, name));
		PIECES.forEach((slot, pieces) -> this.worn.put(slot, new WornModel(skeleton(model, pieces), this::getParentModel)));
	}

	/** The humanoid's bones at their usual places, with {@code pieces} hung on them. */
	static ModelPart skeleton(BbModel model, Map<String, String> pieces) {
		Map<String, ModelPart> bones = new LinkedHashMap<>();
		bones.put("head", bone(model, pieces, "head", PartPose.ZERO, Map.of("hat", new ModelPart(List.of(), Map.of()))));
		bones.put("body", bone(model, pieces, "body", PartPose.ZERO, Map.of()));
		bones.put("right_arm", bone(model, pieces, "right_arm", PartPose.offset(-5.0F, 2.0F, 0.0F), Map.of()));
		bones.put("left_arm", bone(model, pieces, "left_arm", PartPose.offset(5.0F, 2.0F, 0.0F), Map.of()));
		bones.put("right_leg", bone(model, pieces, "right_leg", PartPose.offset(-1.9F, 12.0F, 0.0F), Map.of()));
		bones.put("left_leg", bone(model, pieces, "left_leg", PartPose.offset(1.9F, 12.0F, 0.0F), Map.of()));
		return new ModelPart(List.of(), bones);
	}

	private static ModelPart bone(BbModel model, Map<String, String> pieces, String name, PartPose pose, Map<String, ModelPart> extra) {
		Map<String, ModelPart> children = new LinkedHashMap<>(extra);
		pieces.forEach((piece, on) -> {
			ModelPart part = model.find(piece);
			if (on.equals(name) && part != null && !(piece.equals(WAIST) && model.find("bipedWaist") != null)) {
				children.put(piece, part);
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
				helper.register(create(renderer, new Set("reverent_warplate", Armory.REVERENT_PIECES)));
				helper.register(create(renderer, new Set("heavenly_mantle", Armory.HEAVENLY_PIECES)));
				helper.register(create(renderer, new Set("kintsugi_armour", Armory.KINTSUGI_PIECES)));
				helper.register(create(renderer, new Set("primal_visage", Armory.PRIMAL_PIECES)));
				// the fortress knight's plate goes by its look, on whatever netherite carries it
				helper.register(create(renderer, new Set("vigil_plate", Map.of(), com.jaymoskingdoms.nether.FortressKnight.PLATE)));
				// the Frontier sets: a model each, a texture a colouring
				for (com.jaymoskingdoms.FrontierArmour.Look look : com.jaymoskingdoms.FrontierArmour.LOOKS.values()) {
					helper.register(create(renderer, new Set(look.model(), "frontier_" + look.name(), look.pieces(), null)));
				}
				helper.register(create(renderer, new Set("frontier_settler_hat", "frontier_settler",
					Map.of(ArmorType.HELMET, com.jaymoskingdoms.FrontierArmour.SETTLER_HAT), null)));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ReverentLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer, Set set) {
		return new ReverentLayer((RenderLayerParent) renderer, set);
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
			if (stack.isEmpty() || !this.set.has(stack, type)) {
				continue;
			}
			collector.submitModel(this.worn.get(slot), state, poseStack, RenderTypes.armorCutoutNoCull(this.texture), lightCoords, OverlayTexture.NO_OVERLAY, -1, null,
				state.outlineColor);
		}
	}
}
