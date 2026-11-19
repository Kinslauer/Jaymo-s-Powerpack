package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Harness;
import com.jaymoskingdoms.HeraldryArmour;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import com.jaymoskingdoms.client.disasters.WindCloth;
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

	/** What the wind does to the wearer's cloak: {back, side, flutter} in radians, from {@link WindCloth}; null for none. */
	static final RenderStateDataKey<float[]> WIND = RenderStateDataKey.create(() -> "jaymoskingdoms:cloak_wind");

	/** The model's bones, and the wearer's bone each hangs on. */
	private static final Map<String, String> BONES = Map.of("armorHead", "head", "armorBody", "body", "armorRightArm", "right_arm",
		"armorLeftArm", "left_arm", "armorRightLeg", "right_leg", "armorLeftLeg", "left_leg", "armorRightBoot", "right_leg", "armorLeftBoot", "left_leg");
	/** The harness places drawn here, innermost first. */
	private static final Harness.Slot[] DRAWN = {Harness.Slot.SHINS, Harness.Slot.THIGHS, Harness.Slot.WAIST, Harness.Slot.HANDS, Harness.Slot.ARMS,
		Harness.Slot.NECK, Harness.Slot.SHOULDERS, Harness.Slot.OVER, Harness.Slot.HOOD, Harness.Slot.CREST};
	/** An undyed piece of cloth's colour, by piece (as its icon's: the generator's DYED). */
	private static final Map<String, Integer> UNDYED = Map.of("giornea", 0x8E2B2B, "cloak", 0x4A5568, "torn_cloak", 0x5A5246, "hood", 0x4A5568,
		"torn_hood", 0x5A5246, "tabard", 0x2B4A8E, "coat_of_plates", 0x7A2E2A);
	static final int TORSE_UNDYED = 0xB02E26;
	/** How many ordinaries a tabard's arms can show (the generator's ORDINARIES, one sheet each: heraldry_tabard_arms_N). */
	static final int ORDINARIES = 18;
	/** The share of a plate piece's durability gone when it shows dents, and when it shows many. */
	static final float DENTED = 0.5F, BATTERED = 0.8F;
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
		state.setData(WIND, cloakWind(entity));
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		for (ItemStack stack : new ItemStack[] {state.feetEquipment, state.chestEquipment, state.headEquipment}) {
			HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
			if (piece != null && piece.slot() == null) {
				// (the coat of plates is cloth outside, in its dye)
				Integer undyed = UNDYED.get(piece.name());
				draw(poseStack, collector, lightCoords, state, piece.model(), texture(piece, stack),
					undyed == null ? -1 : ARGB.opaque(DyedItemColor.getOrDefault(stack, undyed)));
				dents(poseStack, collector, lightCoords, state, piece, stack);
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
				dents(poseStack, collector, lightCoords, state, piece, stack);
				// a tabard in a realm's arms: its ordinary laid over the field, front and back, in the charge's colour
				HeraldryArmour.Arms arms = stack.get(HeraldryArmour.ARMS);
				if (arms != null && arms.ordinary() >= 0 && arms.ordinary() < ORDINARIES) {
					draw(poseStack, collector, lightCoords, state, piece.model(), "heraldry_tabard_arms_" + arms.ordinary(), ARGB.opaque(arms.charge()));
				}
			}
		}
	}

	/**
	 * (Harness 5) Plate shows its wear: past {@link #DENTED} of its durability gone it is dented and scratched, past
	 * {@link #BATTERED} badly (the generator's {@code heraldry_PIECE_dents1} and {@code _dents2}, over any finish).
	 */
	private void dents(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, HeraldryArmour.Piece piece, ItemStack stack) {
		if (!piece.plate() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return;
		}
		float worn = stack.getDamageValue() / (float)stack.getMaxDamage();
		int stage = worn >= BATTERED ? 2 : worn >= DENTED ? 1 : 0;
		if (stage > 0) {
			draw(poseStack, collector, lightCoords, state, piece.model(), "heraldry_" + piece.name() + "_dents" + stage, -1);
		}
	}

	/** The wind on a cloak {@code entity} wears over its harness: its cape streams back, sideways and flutters; null when it wears none or the air is still. */
	private static float @Nullable [] cloakWind(LivingEntity entity) {
		if (!entity.hasAttached(Harness.WORN)) {
			return null;
		}
		HeraldryArmour.Piece piece = HeraldryArmour.of(Harness.worn(entity).get(Harness.Slot.OVER.ordinal()));
		if (piece == null || !piece.model().endsWith("cloak")) {
			return null;
		}
		return WindCloth.cape(entity);
	}

	/** A piece's texture: its own name's, and for plate its finish's. */
	private static String texture(HeraldryArmour.Piece piece, ItemStack stack) {
		String finish = piece.plate() ? HeraldryArmour.finish(stack) : "";
		return "heraldry_" + piece.name() + (finish.isEmpty() ? "" : "_" + finish);
	}

	/**
	 * Models a piece names but the mod doesn't have, not drawn: a piece whose model was never made (Harness 3's tabard,
	 * fur-lined set and others) crashed every game that saw one worn (Andy, 2026-11-19 4:02 AM). Logged once each.
	 */
	private static final java.util.Set<String> MISSING = new java.util.HashSet<>();

	private void draw(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, String model, String texture, int color) {
		if (MISSING.contains(model)) {
			return;
		}
		Worn piece;
		try {
			piece = this.worn.computeIfAbsent(model, name -> new Worn(MODELS.computeIfAbsent(name, n -> BbModel.load(JaymosKingdoms.MOD_ID, n)),
				this::getParentModel, name));
		} catch (IllegalStateException e) {
			MISSING.add(model);
			JaymosKingdoms.LOGGER.warn("Heraldry piece model {} is missing; it isn't drawn", model, e);
			return;
		}
		Identifier at = TEXTURES.computeIfAbsent(texture, name -> JaymosKingdoms.id("textures/entity/bb/" + name + ".png"));
		collector.submitModel(piece, state, poseStack, RenderTypes.armorCutoutNoCull(at), lightCoords, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
	}

	/** One piece's model on the wearer's body. A helm turns its visor up when raised; a crest stands as high as the helm under it. */
	private static final class Worn extends WornModel {
		private final @Nullable ModelPart visor;
		/** The crest's own bone, lifted for a tall helm (null for anything else, and for horns and wings, which grow from the helm's sides). */
		private final @Nullable ModelPart crest;
		/** The cloak's cape, which the wind moves (null for anything else). */
		private final @Nullable ModelPart cape;

		Worn(BbModel model, Supplier<HumanoidModel<?>> wearer, String name) {
			super(ReverentLayer.skeleton(model, BONES), wearer);
			this.visor = model.find("visor");
			boolean stands = name.equals(TORSE) || name.startsWith("heraldry_crest_") && !name.equals("heraldry_crest_horns") && !name.equals("heraldry_crest_wings");
			this.crest = stands ? model.find("armorHead") : null;
			this.cape = name.endsWith("cloak") ? model.find("cape") : null;
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			ItemStack helm = state.headEquipment;
			if (this.visor != null && helm.getOrDefault(HeraldryArmour.VISOR_UP, false)) {
				this.visor.xRot -= VISOR_LIFT;
			}
			float[] wind = state.getData(WIND);
			if (this.cape != null && wind != null) {
				this.cape.xRot += wind[0] + wind[2] * Mth.sin(state.ageInTicks * WindCloth.FLAP_SPEED);
				this.cape.zRot += wind[1];
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
