package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FactionType;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/** The client: how a faction's people look (a guard, the king with his crown, a lord in his overlord's colours, builders by style). */
public final class JaymosKingdomsClient implements ClientModInitializer {
	/**
	 * Skins for roles this client doesn't draw itself (role id → its skin for a render state). Camps and later roles add
	 * theirs here.
	 */
	public static final Map<Integer, Function<FolkState, Identifier>> SKINS = new HashMap<>();

	@Override
	public void onInitializeClient() {
		EntityRenderers.register(JaymosKingdoms.FOLK, FolkRenderer::new);
		EntityRenderers.register(com.jaymoskingdoms.FallingTree.TYPE, FallingTreeRenderer::new);
		// 0.7.7: ships under sail, and the client's part in sailing
		EntityRenderers.register(com.jaymoskingdoms.sailing.Ship.TYPE, ShipRenderer::new);
		EntityRenderers.register(com.jaymoskingdoms.sailing.ShipShot.TYPE, context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 1.2F, false));
		ShipClient.init();
		net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(com.jaymoskingdoms.ChoppedLog.ENTITY, ChoppedLogRenderer::new);
		EntityRenderers.register(com.jaymoskingdoms.Wagon.TYPE, WagonRenderer::new);
		// 0.5.64: the richest kingdoms' cannons and their balls
		EntityRenderers.register(com.jaymoskingdoms.Cannon.TYPE, CannonRenderer::new);
		EntityRenderers.register(com.jaymoskingdoms.Cannonball.TYPE, context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 1.4F, false));
		CampSkins.register();
		GoblinSkins.register();
		// 0.5.45: orcs
		OrcSkins.register();
		// (0.7.42) pirates and the kingdoms' sailors
		PirateSkins.register();
		// 0.5.37: the casters
		SorcerySkins.register();
		com.jaymoskingdoms.client.lootgoblins.LootGoblinRenderer.register();
		com.jaymoskingdoms.client.wild.WildRenderer.register();
		com.jaymoskingdoms.client.monsters.LegendRenderer.register();
		MobAnimations.register();
		PullerPose.register();
		// 0.5.50: a pickpocket's arm in the pocket
		PickpocketPose.register();
		SpeechBubbles.register();
		// 0.5.29: quest givers' screens, marks and the tracker
		QuestClient.register();
		// 0.5.50: the traveller's journal on M
		JournalClient.register();
		// 0.5.52: the skill tree (K)
		SkillScreen.register();
		// 0.6.0: the class screen (on first joining, and after a Writ of Rebirth)
		ClassScreen.register();
		ClassKeys.register();
		CombatArtKey.register();
		WerewolfKey.register();
		BloodVision.register();
		VampireLooks.register();
		TalkScreen.register();
		// 0.6.6: lunar events' colours
		MoonClient.register();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(DarkNights::tick);
		// 0.7.5: bed rest and the timelapse night (the bed's darkness, the Sleep button)
		RestClient.register();
		ArmoryLayer.register();
		// 0.7.23: Verdants' antennae
		VerdantLook.register();
		// 0.5.34: Jaymo's Equestrian (gaits, the stamina HUD, coats, war elephants)
		com.jaymoskingdoms.client.equestrian.EquestrianClient.register();
		// 0.6.12: storm clouds, tornado funnels and the rain under a storm
		com.jaymoskingdoms.client.disasters.StormsClient.register();
		// 0.5.47: stablehands' and farriers' screens (the scroll kept when one comes again after a button)
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.jaymoskingdoms.StableNet.Open.TYPE, (payload, context) -> {
			int scroll = context.client().gui.screen() instanceof StableScreen was && was.workerId() == payload.workerId() ? was.scroll() : 0;
			context.client().gui.setScreen(new StableScreen(payload, scroll));
		});
	}

	/** Their render state: which of them it is, its faction's style (type ordinal), and the dye it wears (-1 for none). */
	public static final class FolkState extends HumanoidRenderState {
		public int role;
		public int style;
		public int color = -1;
		/** Its land's skin group ({@link com.jaymoskingdoms.Climate#skinGroup}), and which face it has (from its UUID, the same on every client). */
		public int skinGroup;
		public int variant;
	}

	/** Humanoid, in armour (theirs is real armour), with a skin per role, style and colour. */
	static final class FolkRenderer extends HumanoidMobRenderer<Folk, FolkState, HumanoidModel<FolkState>> {
		/** Skin groups' folder names (by {@link com.jaymoskingdoms.Climate#skinGroup}), and faces drawn per group ({@code tools/gen_folk.py}). */
		private static final String[] LANDS = {"temperate", "sunny", "cold"};
		static final int GUARD_FACES = 4, KING_FACES = 2;
		private static final Identifier[][] GUARDS = new Identifier[LANDS.length][GUARD_FACES];
		private static final Identifier[][] KINGS = new Identifier[LANDS.length][KING_FACES];
		/** By land, then dye (temperate: the original skins). */
		private static final Identifier[][] LORDS = new Identifier[LANDS.length][DyeColor.values().length];
		private static final Identifier[][] KINGDOM_FOREMEN = new Identifier[LANDS.length][DyeColor.values().length];
		private static final Identifier[][] KINGDOM_WORKERS = new Identifier[LANDS.length][DyeColor.values().length];
		private static final Identifier VILLAGE_FOREMAN = JaymosKingdoms.id("textures/entity/builder/village_foreman.png");
		private static final Identifier VILLAGE_WORKER = JaymosKingdoms.id("textures/entity/builder/village_worker.png");
		private static final Identifier BARBARIAN_FOREMAN = JaymosKingdoms.id("textures/entity/builder/barbarian_foreman.png");
		private static final Identifier BARBARIAN_WORKER = JaymosKingdoms.id("textures/entity/builder/barbarian_worker.png");

		static {
			for (int land = 0; land < LANDS.length; land++) {
				String sub = land == 0 ? "" : LANDS[land] + "/";
				for (int i = 0; i < GUARD_FACES; i++) {
					GUARDS[land][i] = JaymosKingdoms.id("textures/entity/guard/" + LANDS[land] + "_" + i + ".png");
				}
				for (int i = 0; i < KING_FACES; i++) {
					KINGS[land][i] = JaymosKingdoms.id("textures/entity/king/" + LANDS[land] + "_" + i + ".png");
				}
				for (DyeColor dye : DyeColor.values()) {
					LORDS[land][dye.getId()] = JaymosKingdoms.id("textures/entity/lord/" + sub + dye.getName() + ".png");
					KINGDOM_FOREMEN[land][dye.getId()] = JaymosKingdoms.id("textures/entity/builder/" + sub + "kingdom_foreman_" + dye.getName() + ".png");
					KINGDOM_WORKERS[land][dye.getId()] = JaymosKingdoms.id("textures/entity/builder/" + sub + "kingdom_worker_" + dye.getName() + ".png");
				}
			}
		}

		FolkRenderer(EntityRendererProvider.Context context) {
			super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
			this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
			// (0.5.27) goblins' long ears and hooked noses
			this.addLayer(new GoblinSkins.Features(this));
			// (0.5.45) orcs' tusks, brows and ears
			this.addLayer(new OrcSkins.Features(this));
			// (0.5.37) a court wizard's pointed hat
			this.addLayer(new SorcerySkins.Hat(this));
		}

		@Override
		protected void scale(FolkState state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
			super.scale(state, poseStack);
			// (0.5.45) orcs are broader than men
			OrcSkins.broaden(state, poseStack);
		}

		@Override
		public FolkState createRenderState() {
			return new FolkState();
		}

		@Override
		public void extractRenderState(Folk entity, FolkState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.role = entity.role();
			state.style = entity.style().ordinal();
			state.color = entity.colorId();
			state.skinGroup = Math.clamp(entity.climate().skinGroup(), 0, LANDS.length - 1);
			java.util.UUID uuid = entity.getUUID();
			state.variant = (int)((uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()) >>> 33);
		}

		@Override
		public Identifier getTextureLocation(FolkState state) {
			Function<FolkState, Identifier> custom = SKINS.get(state.role);
			if (custom != null) {
				return custom.apply(state);
			}
			int dye = state.color >= 0 && state.color < DyeColor.values().length ? state.color : DyeColor.WHITE.getId();
			int land = state.skinGroup;
			return switch (state.role) {
				case Folk.KING -> KINGS[land][state.variant % KING_FACES];
				case Folk.LORD -> LORDS[land][dye];
				// (a farmer wears a worker's clothes)
				case Folk.FOREMAN, Folk.WORKER, Folk.FARMER, Folk.MINER, Folk.WOODCUTTER, Folk.CARTER, Folk.PULLER, Folk.STABLEHAND, Folk.FARRIER -> builder(state, land, dye);
				default -> GUARDS[land][state.variant % GUARD_FACES];
			};
		}

		private static Identifier builder(FolkState state, int land, int dye) {
			boolean foreman = state.role == Folk.FOREMAN;
			if (state.style == FactionType.VILLAGE.ordinal()) {
				return foreman ? VILLAGE_FOREMAN : VILLAGE_WORKER;
			} else if (state.style == FactionType.BARBARIAN.ordinal()) {
				return foreman ? BARBARIAN_FOREMAN : BARBARIAN_WORKER;
			}
			return foreman ? KINGDOM_FOREMEN[land][dye] : KINGDOM_WORKERS[land][dye];
		}
	}
}
