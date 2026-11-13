package com.jaymoskingdoms.client.inhabitants;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.SkeletonLord;
import com.jaymoskingdoms.inhabitants.BarrowBeetle;
import com.jaymoskingdoms.inhabitants.Bogre;
import com.jaymoskingdoms.inhabitants.Draugr;
import com.jaymoskingdoms.inhabitants.Impaler;
import com.jaymoskingdoms.inhabitants.ImpalerGear;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import com.jaymoskingdoms.inhabitants.WarpedClam;
import com.jaymoskingdoms.inhabitants.Inhabitants;
import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.SilverSkeleton;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The renderers of Kingdoms' GeckoLib mobs ({@link Inhabitants}, and the silver skeleton and the Skeleton Lord). One line
 * per mob, an {@link InhabitantRenderer} with its file name, size, shadow and head bone; a mob whose look depends on its
 * state passes that state into the render state ({@code addRenderData}) and reads it back when its bones are set.
 */
public final class InhabitantsClient {
	/** The barrow beetle and the draugr are drawn at their models' size. */
	static final float BEETLE_SHADOW = 0.3F, DRAUGR_SHADOW = 0.5F, SILVER_SHADOW = 0.6F;
	/** The Bogre is drawn a quarter over its model's size (as Inhabitants draws it), with a wide shadow. */
	static final float BOGRE_SCALE = 1.25F, BOGRE_SHADOW = 1.25F;
	/** The Impaler and the Warped Clam are drawn at their models' size (as Inhabitants draws them). */
	static final float IMPALER_SHADOW = 0.9F, CLAM_SHADOW = 0.7F;
	/** The Skeleton Lord's model stands 5 blocks to its crown (8 to the staff's tip): drawn to his 2.9-block height. */
	static final float LORD_SCALE = 0.55F, LORD_SHADOW = 0.9F;
	/** The Skeleton Lord's crystal bones, shown only while he sleeps as a statue. */
	static final String[] CRYSTALS = {"head_crystals", "crystal_brow", "crystal_hand", "crystal_fingerbase", "crystal_fingertips", "crystal_thumbbase",
		"crystal_thumbtip", "crystal_arm", "crystal_chest", "crystal_waist", "crystal_leg", "crystalized_pauldron", "R_crystalized_pauldron"};
	/** His laugh works his jaw this far (radians), this fast. */
	static final float LAUGH_JAW = 0.35F, LAUGH_JAW_SPEED = 0.9F;

	/**
	 * A draugr's tail (Warrior-born, every one of them), wrapped round its waist and cut short in one of these ways, picked
	 * by its id: whole, gone at the root, cut halfway round, or the tip gone. Most are whole.
	 */
	enum DraugrTail { WHOLE, WHOLE_TOO, ROOT_STUMP, HALF_CUT, TIP_GONE }
	static final String[] DRAUGR_TAIL = {"tail_0", "tail_1", "tail_2", "tail_3", "tail_4"};

	static final DataTicket<Boolean> DRAUGR_AXE = DataTicket.create("jaymoskingdoms_draugr_axe", Boolean.class);
	static final DataTicket<Boolean> DRAUGR_LIVING = DataTicket.create("jaymoskingdoms_draugr_living", Boolean.class);
	static final DataTicket<Integer> DRAUGR_MAIMED = DataTicket.create("jaymoskingdoms_draugr_maimed", Integer.class);
	static final DataTicket<Integer> DRAUGR_TAIL_ROLL = DataTicket.create("jaymoskingdoms_draugr_tail", Integer.class);
	static final DataTicket<Boolean> IMPALER_SPIKES = DataTicket.create("jaymoskingdoms_impaler_spikes", Boolean.class);
	static final DataTicket<Integer> IMPALER_COAT = DataTicket.create("jaymoskingdoms_impaler_coat", Integer.class);
	static final DataTicket<Boolean> CLAM_PEARL = DataTicket.create("jaymoskingdoms_clam_pearl", Boolean.class);
	static final DataTicket<Boolean> LORD_DORMANT = DataTicket.create("jaymoskingdoms_lord_dormant", Boolean.class);
	static final DataTicket<Float> LORD_SINCE_WOKE = DataTicket.create("jaymoskingdoms_lord_since_woke", Float.class);

	private InhabitantsClient() {
	}

	public static void register() {
		EntityRenderers.register(Inhabitants.BARROW_BEETLE,
			context -> new InhabitantRenderer<BarrowBeetle>(context, "barrow_beetle", 1.0F, BEETLE_SHADOW, "head"));
		EntityRenderers.register(Inhabitants.DRAUGR, DraugrRenderer::new);
		EntityRenderers.register(Inhabitants.BOGRE, BogreRenderer::new);
		EntityRenderers.register(Inhabitants.IMPALER, ImpalerRenderer::new);
		EntityRenderers.register(Inhabitants.WARPED_CLAM, ClamRenderer::new);
		EntityRenderers.register(Inhabitants.IMPALER_SPIKE, ThrownItemRenderer::new);
		EntityRenderers.register(ImpalerGear.CONCUSSIVE_ARROW_ENTITY, ConcussiveArrowRenderer::new);
		Concussed.register();
		EntityRenderers.register(Monsters.SILVER_SKELETON,
			context -> new InhabitantRenderer<SilverSkeleton>(context, "silver_skeleton", 1.0F, SILVER_SHADOW, "head"));
		EntityRenderers.register(SkeletonLord.TYPE, LordRenderer::new);
	}

	/**
	 * Its sword and shield, or its axe; its tail, cut short its own way. A lore film's living Warrior-born
	 * ({@link Draugr#stageLiving}) wears its look's {@code draugr_living_<n>.png} (the same gear in better condition) and a
	 * whole tail.
	 */
	static final class DraugrRenderer extends InhabitantRenderer<Draugr> {
		/** Its three looks (tools/gen_tomb_undead.py DRAUGR_LOOKS), dead and alive, picked by its id. */
		static final int LOOKS = 3;
		static final Identifier[] DEAD = new Identifier[LOOKS], LIVING = new Identifier[LOOKS];

		static {
			for (int i = 0; i < LOOKS; i++) {
				DEAD[i] = JaymosKingdoms.id(i == 0 ? "textures/entity/draugr.png" : "textures/entity/draugr_" + i + ".png");
				LIVING[i] = JaymosKingdoms.id("textures/entity/draugr_living_" + i + ".png");
			}
		}

		DraugrRenderer(EntityRendererProvider.Context context) {
			super(context, new DefaultedEntityGeoModel<Draugr>(JaymosKingdoms.id("draugr")) {
				@Override
				public Identifier getTextureResource(GeoRenderState state) {
					int look = look(state.getOrDefaultGeckolibData(DRAUGR_TAIL_ROLL, 0));
					return (state.getOrDefaultGeckolibData(DRAUGR_LIVING, false) ? LIVING : DEAD)[look];
				}
			}, 1.0F, DRAUGR_SHADOW, "head");
		}

		@Override
		public void addRenderData(Draugr draugr, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(draugr, related, state, partialTick);
			state.addGeckolibData(DRAUGR_AXE, draugr.axe());
			state.addGeckolibData(DRAUGR_TAIL_ROLL, draugr.getUUID().hashCode());
			state.addGeckolibData(DRAUGR_LIVING, draugr.living());
			state.addGeckolibData(DRAUGR_MAIMED, draugr.maimed());
		}

		@Override
		public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
			super.adjustModelBonesForRender(info, snapshots);
			boolean axe = info.getOrDefaultGeckolibData(DRAUGR_AXE, false);
			snapshots.ifPresent("axe", bone -> bone.skipRender(!axe));
			snapshots.ifPresent("sword", bone -> bone.skipRender(axe));
			snapshots.ifPresent("shield", bone -> bone.skipRender(axe));
			// the bronze chieftain's left horn broken off
			snapshots.ifPresent("left_horn", bone -> bone.skipRender(look(info.getOrDefaultGeckolibData(DRAUGR_TAIL_ROLL, 0)) == 1));
			// a lore film's finisher took its head or its weapon arm
			int maimed = info.getOrDefaultGeckolibData(DRAUGR_MAIMED, 0);
			if ((maimed & Draugr.MAIM_HEAD) != 0) {
				snapshots.ifPresent("head", bone -> bone.skipRender(true).skipChildrenRender(true));
			}
			if ((maimed & Draugr.MAIM_ARM) != 0) {
				snapshots.ifPresent("right_arm", bone -> bone.skipRender(true).skipChildrenRender(true));
			}
			if (info.getOrDefaultGeckolibData(DRAUGR_LIVING, false)) {
				// alive: no ribs through the mail, the tail whole
				snapshots.ifPresent("ribs", bone -> bone.skipRender(true));
				return;
			}
			int roll = info.getOrDefaultGeckolibData(DRAUGR_TAIL_ROLL, 0);
			switch (DraugrTail.values()[Math.floorMod(roll, DraugrTail.values().length)]) {
				case ROOT_STUMP -> cut(snapshots, 1);
				case HALF_CUT -> cut(snapshots, 3);
				case TIP_GONE -> cut(snapshots, 4);
				default -> {
				}
			}
		}

		/** Which of its looks a draugr wears, from its id (apart from the bits its tail is cut by). */
		static int look(int roll) {
			return Math.floorMod(roll >>> 12, LOOKS);
		}

		/** The tail ends before this segment. */
		private static void cut(BoneSnapshots snapshots, int segment) {
			snapshots.ifPresent(DRAUGR_TAIL[segment], bone -> bone.skipRender(true).skipChildrenRender(true));
		}
	}

	/** It falls by its own death animation, so it isn't tipped over on its side as well. */
	static final class BogreRenderer extends InhabitantRenderer<Bogre> {
		BogreRenderer(EntityRendererProvider.Context context) {
			super(context, "bogre", BOGRE_SCALE, BOGRE_SHADOW, "head");
		}

		@Override
		protected float getDeathMaxRotation(GeoRenderState state) {
			return 0.0F;
		}
	}

	/**
	 * Its coat (textures/entity/impaler/{@code coat}.png), its eyes and mouth glowing in the dark (each coat's
	 * {@code _glowmask}), and its arm bare of spikes once it has thrown them all (they grow back).
	 */
	static final class ImpalerRenderer extends InhabitantRenderer<Impaler> {
		static final Identifier[] COATS = new Identifier[Impaler.Coat.values().length];

		static {
			for (Impaler.Coat coat : Impaler.Coat.values()) {
				COATS[coat.ordinal()] = JaymosKingdoms.id("textures/entity/impaler/" + coat.texture + ".png");
			}
		}

		ImpalerRenderer(EntityRendererProvider.Context context) {
			super(context, new DefaultedEntityGeoModel<Impaler>(JaymosKingdoms.id("impaler")) {
				@Override
				public Identifier getTextureResource(GeoRenderState state) {
					return COATS[Mth.clamp(state.getOrDefaultGeckolibData(IMPALER_COAT, 0), 0, COATS.length - 1)];
				}
			}, 1.0F, IMPALER_SHADOW, "head");
			this.withRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public void addRenderData(Impaler impaler, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(impaler, related, state, partialTick);
			state.addGeckolibData(IMPALER_SPIKES, impaler.spikes() > 0);
			state.addGeckolibData(IMPALER_COAT, impaler.coat().ordinal());
		}

		@Override
		public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
			super.adjustModelBonesForRender(info, snapshots);
			boolean spikes = info.getOrDefaultGeckolibData(IMPALER_SPIKES, true);
			snapshots.ifPresent("spikes8", bone -> bone.skipRender(!spikes));
		}
	}

	/** Its pearl, and the glow inside, only once it has grown one. It has no head to turn. */
	static final class ClamRenderer extends InhabitantRenderer<WarpedClam> {
		ClamRenderer(EntityRendererProvider.Context context) {
			super(context, "warped_clam", 1.0F, CLAM_SHADOW, null);
		}

		@Override
		public void addRenderData(WarpedClam clam, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(clam, related, state, partialTick);
			state.addGeckolibData(CLAM_PEARL, clam.pearl());
		}

		@Override
		public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
			super.adjustModelBonesForRender(info, snapshots);
			boolean pearl = info.getOrDefaultGeckolibData(CLAM_PEARL, false);
			snapshots.ifPresent("pearl", bone -> bone.skipRender(!pearl));
			snapshots.ifPresent("glowLayer", bone -> bone.skipRender(!pearl));
		}
	}

	/** His crystals only while he sleeps; his jaw working while he laughs. */
	static final class LordRenderer extends InhabitantRenderer<SkeletonLord> {
		LordRenderer(EntityRendererProvider.Context context) {
			super(context, "skeleton_lord", LORD_SCALE, LORD_SHADOW, "head");
		}

		@Override
		public void addRenderData(SkeletonLord lord, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(lord, related, state, partialTick);
			state.addGeckolibData(LORD_DORMANT, lord.dormant());
			state.addGeckolibData(LORD_SINCE_WOKE, lord.sinceWoke() + partialTick);
		}

		@Override
		public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
			super.adjustModelBonesForRender(info, snapshots);
			boolean dormant = info.getOrDefaultGeckolibData(LORD_DORMANT, false);
			for (String crystal : CRYSTALS) {
				snapshots.ifPresent(crystal, bone -> bone.skipRender(!dormant));
			}
			float sinceWoke = info.getOrDefaultGeckolibData(LORD_SINCE_WOKE, 1000.0F);
			if (!dormant && sinceWoke < SkeletonLord.WAKE_TICKS) {
				snapshots.ifPresent("jaw", bone -> bone.setRotX(bone.getRotX() + Math.abs(Mth.sin(sinceWoke * LAUGH_JAW_SPEED)) * LAUGH_JAW));
			}
		}
	}
}
