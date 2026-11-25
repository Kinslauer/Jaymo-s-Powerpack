package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Harness;
import com.jaymoskingdoms.HeraldryArmour;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.client.bb.BbModel;
import com.jaymoskingdoms.client.disasters.WindCloth;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.core.registries.BuiltInRegistries;
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
	private static final Harness.Slot[] DRAWN = {Harness.Slot.HOSE, Harness.Slot.PADDING, Harness.Slot.MAIL, Harness.Slot.SHINS, Harness.Slot.THIGHS, Harness.Slot.WAIST, Harness.Slot.HANDS, Harness.Slot.ARMS,
		Harness.Slot.NECK, Harness.Slot.SHOULDERS, Harness.Slot.OVER, Harness.Slot.HOOD, Harness.Slot.CREST};
	/** An undyed piece of cloth's colour, by piece (as its icon's: the generator's DYED). */
	private static final Map<String, Integer> UNDYED = Map.of("giornea", 0x8E2B2B, "cloak", 0x4A5568, "torn_cloak", 0x5A5246, "hood", 0x4A5568,
		"torn_hood", 0x5A5246, "tabard", 0x2B4A8E, "coat_of_plates", 0x7A2E2A);
	static final int TORSE_UNDYED = 0xB02E26;
	/** (Mordhau harness 2) The new cloth's undyed colours (gen_heraldry_hoods.py's DYED), and the second colour of a two-coloured piece made without one. */
	private static final Map<String, Integer> HOOD_UNDYED = Map.ofEntries(Map.entry("tight_hood", 0x6B5A44), Map.entry("monks_cowl", 0x5A4632),
		Map.entry("clerics_mantle", 0x8E2B2B), Map.entry("arming_cap", 0xD8CCB0), Map.entry("linen_coif", 0xECE6D6), Map.entry("bag_hat", 0x6E7F4A),
		Map.entry("archers_cap", 0x3F6B3A), Map.entry("hunters_hat", 0x5E4A32), Map.entry("landsknecht_bonnet", 0xA02828), Map.entry("turban", 0xE8E0CC),
		Map.entry("veiled_turban", 0x3A5A8E), Map.entry("jesters_hood", 0xB02E26));
	private static final Map<String, Integer> SECOND_UNDYED = Map.of("landsknecht_bonnet", 0xF0C838, "jesters_hood", 0xE8C83A, "jesters_motley", 0xE8C83A, "embroidered_skirt", 0xE8C83A, "landsknecht_hose", 0xD8B034);
	/** (Mordhau harness 3) The body's cloth models' undyed colours (gen_heraldry_body.py's UNDYED), and the colour a covered plate piece shows undyed. */
	private static final Map<String, Integer> BODY_UNDYED = Map.ofEntries(Map.entry("wool_tunic", 0x6B5A44), Map.entry("quilted_coat", 0xCEBE96),
		Map.entry("surcoat", 0x2B4A8E), Map.entry("torn_surcoat", 0x5A5246), Map.entry("monks_robe", 0x5A4632), Map.entry("kaftan", 0x2F5A7A),
		Map.entry("jesters_motley", 0xB02E26), Map.entry("sash", 0xB02E26), Map.entry("ailettes", 0x2B4A8E), Map.entry("brigandine_coat", 0x7A2E2A),
		// (Mordhau harness 4, gen_heraldry_legs.py's UNDYED) the waist's and the legs' cloth
		Map.entry("coat_of_plates_skirt", 0x7A2E2A), Map.entry("padded_skirt", 0xD8CCB0), Map.entry("cloth_skirt", 0x6B5A44), Map.entry("short_skirt", 0x6E7F4A),
		Map.entry("long_skirt", 0x4A5568), Map.entry("apron", 0xCEBE96), Map.entry("pleated_base", 0x8E2B2B), Map.entry("embroidered_skirt", 0x2B4A8E),
		Map.entry("leg_wraps", 0xB8A878), Map.entry("baggy_breeches", 0x7A6A48), Map.entry("landsknecht_hose", 0x2B4A8E));
	private static final int COVERED_UNDYED = 0x7A2E3A;
	/** Dyed pieces with parts drawn as they are painted (feathers, bells, gilt: heraldry_PIECE_fixed). */
	private static final Set<String> TRIMMED = Set.of("clerics_mantle", "archers_cap", "hunters_hat", "landsknecht_bonnet", "turban", "veiled_turban",
		"jesters_hood", "surcoat", "torn_surcoat", "monks_robe", "kaftan", "jesters_motley", "ailettes", "brigandine_coat", "coat_of_plates_skirt", "padded_skirt", "cloth_skirt", "short_skirt", "long_skirt", "apron",
		"pleated_base");
	/** Hats, worn bare-headed: not drawn under a helm (the hood, cowl, mantle, caps and coifs go on over or under one). */
	private static final Set<String> HATS = Set.of("tight_hood", "headsmans_hood", "bag_hat", "archers_cap", "field_hat", "hunters_hat",
		"landsknecht_bonnet", "fur_hat", "turban", "veiled_turban", "jesters_hood", "laurel_wreath");
	/** Crests worn without a torse under them (the crown rings the helm; the orle is worn in the torse's place), and crests in the torse's dye. */
	private static final Set<String> NO_TORSE = Set.of("crest_crown", "orle"), DYED_CRESTS = Set.of("orle", "crest_mantling");
	/** Any ruler's crown over a helm: one box (gen_heraldry_hoods.py), drawn with the crown's own armour-layer sheet. */
	private static final String CROWN_OVER_HELM = "heraldry_crown_over_helm";
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
				// (the coat of plates is cloth outside, in its dye; plate covered in cloth the same)
				draw(poseStack, collector, lightCoords, state, piece.model(), texture(piece, stack), tint(piece, stack));
				dents(poseStack, collector, lightCoords, state, piece, stack);
			}
		}
		List<ItemStack> harness = state.getData(HARNESS);
		if (harness == null) {
			return;
		}
		for (Harness.Slot slot : DRAWN) {
			ItemStack stack = harness.get(slot.ordinal());
			if (slot == Harness.Slot.CREST && HeraldryArmour.regaliaCrown(stack)) {
				// a ruler's crown over the helm, in its own sheet
				draw(poseStack, collector, lightCoords, state, CROWN_OVER_HELM, "equipment/humanoid/" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), -1);
				continue;
			}
			HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
			if (piece == null || piece.model().isEmpty()) {
				continue;
			}
			if (slot == Harness.Slot.CREST) {
				// the torse in the crest's dye, and the beast (or horns, or wings) on it
				int dye = ARGB.opaque(DyedItemColor.getOrDefault(stack, TORSE_UNDYED));
				if (!NO_TORSE.contains(piece.name())) {
					draw(poseStack, collector, lightCoords, state, TORSE, TORSE, dye);
				}
				if (!piece.model().equals(TORSE)) {
					draw(poseStack, collector, lightCoords, state, piece.model(), "heraldry_" + piece.name(), DYED_CRESTS.contains(piece.name()) ? dye : -1);
				}
			} else if (slot == Harness.Slot.HOOD && HATS.contains(piece.name()) && !state.headEquipment.isEmpty()
				|| (piece.name().equals("bevor") || piece.name().equals("armet_wrapper")) && sallet(state.headEquipment)) {
				// a hat isn't worn under a helm, and the sallet has its own bevor
				continue;
			} else {
				draw(poseStack, collector, lightCoords, state, piece.model(), texture(piece, stack), tint(piece, stack));
				dents(poseStack, collector, lightCoords, state, piece, stack);
				// (Mordhau harness 2) feathers, bells and gilt as they are painted, and the second colour's cloth in it
				if (TRIMMED.contains(piece.name())) {
					draw(poseStack, collector, lightCoords, state, piece.model(), "heraldry_" + piece.name() + "_fixed", -1);
				}
				Integer second = SECOND_UNDYED.get(piece.name());
				if (second != null) {
					draw(poseStack, collector, lightCoords, state, piece.model(), "heraldry_" + piece.name() + "_second",
						ARGB.opaque(stack.getOrDefault(HeraldryArmour.SECOND_COLOUR, second)));
				}
				// a tabard in a realm's arms: its ordinary laid over the field, front and back, in the charge's colour
				HeraldryArmour.Arms arms = stack.get(HeraldryArmour.ARMS);
				if (arms != null && arms.ordinary() >= 0 && arms.ordinary() < ORDINARIES) {
					// (the ailettes' boards carry them on a sheet of their own)
					draw(poseStack, collector, lightCoords, state, piece.model(), (piece.name().equals("ailettes") ? "heraldry_ailettes_arms_" : "heraldry_tabard_arms_")
						+ arms.ordinary(), ARGB.opaque(arms.charge()));
				}
			}
		}
	}

	/**
	 * (Harness 5) Plate shows its wear: past {@link #DENTED} of its durability gone it is dented and scratched, past
	 * {@link #BATTERED} badly (the generator's {@code heraldry_PIECE_dents1} and {@code _dents2}, over any finish).
	 */
	private void dents(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, HeraldryArmour.Piece piece, ItemStack stack) {
		if (!piece.plate() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0 || HeraldryArmour.COVERED.equals(HeraldryArmour.finish(stack))) {
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

	/** An undyed piece of cloth's colour, or null for a piece that isn't dyed. */
	private static @Nullable Integer undyed(String name) {
		Integer undyed = UNDYED.get(name);
		if (undyed == null) {
			undyed = HOOD_UNDYED.get(name);
		}
		return undyed != null ? undyed : BODY_UNDYED.get(name);
	}

	/** The colour a piece is drawn tinted with: its dye if it is cloth, or plate covered in cloth (the cloth's colour); -1 for anything else. */
	private static int tint(HeraldryArmour.Piece piece, ItemStack stack) {
		Integer undyed = undyed(piece.name());
		if (undyed == null && piece.plate() && HeraldryArmour.COVERED.equals(HeraldryArmour.finish(stack))) {
			undyed = COVERED_UNDYED;
		}
		return undyed == null ? -1 : ARGB.opaque(DyedItemColor.getOrDefault(stack, undyed));
	}

	/** Whether {@code helm} is the sallet, whose bevor is part of it (a bevor or wrapper in the harness isn't drawn under it). */
	private static boolean sallet(ItemStack helm) {
		HeraldryArmour.Piece piece = HeraldryArmour.of(helm);
		return piece != null && piece.name().equals("sallet");
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
		// (a crown over a helm is drawn with the crown's own armour-layer sheet)
		Identifier at = TEXTURES.computeIfAbsent(texture, name -> JaymosKingdoms.id(name.startsWith("equipment/") ? "textures/entity/" + name + ".png"
			: "textures/entity/bb/" + name + ".png"));
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
			// (the crown, antlers, horns and wings are fixed round or to the sides of the helm, not on its crown)
			boolean stands = name.equals(TORSE) || name.equals("heraldry_orle") || name.startsWith("heraldry_crest_") && !name.equals("heraldry_crest_horns")
				&& !name.equals("heraldry_crest_wings") && !name.equals("heraldry_crest_crown") && !name.equals("heraldry_crest_antlers");
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
			if (McaCompat.LOADED) {
				// (Andy, 2026-11-19) fitted to the body MCA draws: its head size, breasts and clothes
				McaHarnessFit.fit(this, state);
			}
		}
	}
}
