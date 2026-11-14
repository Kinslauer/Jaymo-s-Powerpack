package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Harness;
import com.jaymoskingdoms.HeraldryArmour;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * {@link HeraldryArmour}'s harness, drawn over anything humanoid that wears it: each piece its own baked model
 * (kingdoms/tools/gen_heraldry_armour.py), hung on the wearer's head, body, arms and legs ({@link WornModel}, so it
 * takes every pose the vanilla armour does). The helm, cuirass and sabatons come from the armour slots; the spaulders,
 * arm harness, gauntlets, cuisses, greaves, the giornea or cloak, the hood and the crest from the {@link Harness} (put
 * on the render state by {@link #extract}). The padding and mail under it all are {@link UnderArmourLayer}'s.
 * <p>
 * A plate piece's texture is its finish's; cloth is tinted with its dye. A visored helm's visor turns up when it is
 * raised, and a crest (its beast, on a torse in the crest's dye) is lifted to stand on whichever helm is under it.
 */
public final class HeraldryLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	/** How far a raised visor is turned up (radians). */
	static final float VISOR_LIFT = 65.0F * Mth.DEG_TO_RAD;
	/** What a wearer has in the harness, by {@link Harness.Slot#ordinal()}; null for none. */
	static final RenderStateDataKey<List<ItemStack>> HARNESS = RenderStateDataKey.create(() -> "jaymoskingdoms:harness");

	/** The model's bones, and the wearer's bone each hangs on. */
	private static final Map<String, String> BONES = Map.of("armorHead", "head", "armorBody", "body", "armorRightArm", "right_arm",
		"armorLeftArm", "left_arm", "armorRightLeg", "right_leg", "armorLeftLeg", "left_leg", "armorRightBoot", "right_leg", "armorLeftBoot", "left_leg");
	/** The harness places drawn here, innermost first. */
	private static final Harness.Slot[] DRAWN = {Harness.Slot.SHINS, Harness.Slot.THIGHS, Harness.Slot.HANDS, Harness.Slot.ARMS, Harness.Slot.SHOULDERS,
		Harness.Slot.OVER, Harness.Slot.HOOD, Harness.Slot.CREST};
	/** An undyed piece of cloth's colour, by piece (as its icon's: the generator's DYED). */
	private static final Map<String, Integer> UNDYED = Map.of("giornea", 0x8E2B2B, "cloak", 0x4A5568, "torn_cloak", 0x5A5246, "hood", 0x4A5568,
		"torn_hood", 0x5A5246);
	static final int TORSE_UNDYED = 0xB02E26;
	private static final String TORSE = "heraldry_torse";

	/** The models, each read once, and the textures by name. */
	private static final Map<String, BbModel> MODELS = new HashMap<>();
	private static final Map<String, Identifier> TEXTURES = new HashMap<>();

	/** This renderer's copy of each model it has drawn, hung on its own body. */
	private final Map<String, Worn> worn = new HashMap<>();

	private HeraldryLayer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
		super(renderer);
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
	private static HeraldryLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer) {
		return new HeraldryLayer((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: what {@code entity} has in the harness. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(HARNESS, entity.hasAttached(Harness.WORN) ? Harness.worn(entity) : null);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		for (ItemStack stack : new ItemStack[] {state.feetEquipment, state.chestEquipment, state.headEquipment}) {
			HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
			if (piece != null && piece.slot() == null) {
				draw(poseStack, collector, lightCoords, state, piece.model(), texture(piece, stack), -1);
			}
		}
		List<ItemStack> harness = state.getData(HARNESS);
		if (harness == null) {
			return;
		}
		for (Harness.Slot slot : DRAWN) {
			ItemStack stack = harness.get(slot.ordinal());
			HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
			if (piece == null || piece.model().isEmpty()) {
				continue;
			}
			if (slot == Harness.Slot.CREST) {
				// the torse in the crest's dye, and the beast (or horns, or wings) on it
				draw(poseStack, collector, lightCoords, state, TORSE, TORSE, ARGB.opaque(DyedItemColor.getOrDefault(stack, TORSE_UNDYED)));
				if (!piece.model().equals(TORSE)) {
					draw(poseStack, collector, lightCoords, state, piece.model(), "heraldry_" + piece.name(), -1);
				}
			} else {
				Integer undyed = UNDYED.get(piece.name());
				draw(poseStack, collector, lightCoords, state, piece.model(), texture(piece, stack),
					undyed == null ? -1 : ARGB.opaque(DyedItemColor.getOrDefault(stack, undyed)));
			}
		}
	}

	/** A piece's texture: its own name's, and for plate its finish's. */
	private static String texture(HeraldryArmour.Piece piece, ItemStack stack) {
		String finish = piece.plate() ? HeraldryArmour.finish(stack) : "";
		return "heraldry_" + piece.name() + (finish.isEmpty() ? "" : "_" + finish);
	}

	private void draw(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, String model, String texture, int color) {
		Worn piece = this.worn.computeIfAbsent(model, name -> new Worn(MODELS.computeIfAbsent(name, n -> BbModel.load(JaymosKingdoms.MOD_ID, n)),
			this::getParentModel, name));
		Identifier at = TEXTURES.computeIfAbsent(texture, name -> JaymosKingdoms.id("textures/entity/bb/" + name + ".png"));
		collector.submitModel(piece, state, poseStack, RenderTypes.armorCutoutNoCull(at), lightCoords, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
	}

	/** One piece's model on the wearer's body. A helm turns its visor up when raised; a crest stands as high as the helm under it. */
	private static final class Worn extends WornModel {
		private final @Nullable ModelPart visor;
		/** The crest's own bone, lifted for a tall helm (null for anything else, and for horns and wings, which grow from the helm's sides). */
		private final @Nullable ModelPart crest;

		Worn(BbModel model, Supplier<HumanoidModel<?>> wearer, String name) {
			super(ReverentLayer.skeleton(model, BONES), wearer);
			this.visor = model.find("visor");
			boolean stands = name.equals(TORSE) || name.startsWith("heraldry_crest_") && !name.equals("heraldry_crest_horns") && !name.equals("heraldry_crest_wings");
			this.crest = stands ? model.find("armorHead") : null;
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			ItemStack helm = state.headEquipment;
			if (this.visor != null && helm.getOrDefault(HeraldryArmour.VISOR_UP, false)) {
				this.visor.xRot -= VISOR_LIFT;
			}
			if (this.crest != null) {
				HeraldryArmour.Piece under = HeraldryArmour.of(helm);
				if (under != null) {
					this.crest.y -= under.crestRise();
				}
			}
		}
	}
}
