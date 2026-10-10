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
		CampSkins.register();
	}

	/** Their render state: which of them it is, its faction's style (type ordinal), and the dye it wears (-1 for none). */
	public static final class FolkState extends HumanoidRenderState {
		public int role;
		public int style;
		public int color = -1;
	}

	/** Humanoid, in armour (theirs is real armour), with a skin per role, style and colour. */
	static final class FolkRenderer extends HumanoidMobRenderer<Folk, FolkState, HumanoidModel<FolkState>> {
		private static final Identifier GUARD = JaymosKingdoms.id("textures/entity/guard.png");
		private static final Identifier KING = JaymosKingdoms.id("textures/entity/king.png");
		private static final Identifier[] LORDS = new Identifier[DyeColor.values().length];
		private static final Identifier[] KINGDOM_FOREMEN = new Identifier[DyeColor.values().length];
		private static final Identifier[] KINGDOM_WORKERS = new Identifier[DyeColor.values().length];
		private static final Identifier VILLAGE_FOREMAN = JaymosKingdoms.id("textures/entity/builder/village_foreman.png");
		private static final Identifier VILLAGE_WORKER = JaymosKingdoms.id("textures/entity/builder/village_worker.png");
		private static final Identifier BARBARIAN_FOREMAN = JaymosKingdoms.id("textures/entity/builder/barbarian_foreman.png");
		private static final Identifier BARBARIAN_WORKER = JaymosKingdoms.id("textures/entity/builder/barbarian_worker.png");

		static {
			for (DyeColor dye : DyeColor.values()) {
				LORDS[dye.getId()] = JaymosKingdoms.id("textures/entity/lord/" + dye.getName() + ".png");
				KINGDOM_FOREMEN[dye.getId()] = JaymosKingdoms.id("textures/entity/builder/kingdom_foreman_" + dye.getName() + ".png");
				KINGDOM_WORKERS[dye.getId()] = JaymosKingdoms.id("textures/entity/builder/kingdom_worker_" + dye.getName() + ".png");
			}
		}

		FolkRenderer(EntityRendererProvider.Context context) {
			super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
			this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
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
		}

		@Override
		public Identifier getTextureLocation(FolkState state) {
			Function<FolkState, Identifier> custom = SKINS.get(state.role);
			if (custom != null) {
				return custom.apply(state);
			}
			int dye = state.color >= 0 && state.color < LORDS.length ? state.color : DyeColor.WHITE.getId();
			return switch (state.role) {
				case Folk.KING -> KING;
				case Folk.LORD -> LORDS[dye];
				case Folk.FOREMAN, Folk.WORKER -> builder(state, dye);
				default -> GUARD;
			};
		}

		private static Identifier builder(FolkState state, int dye) {
			boolean foreman = state.role == Folk.FOREMAN;
			if (state.style == FactionType.VILLAGE.ordinal()) {
				return foreman ? VILLAGE_FOREMAN : VILLAGE_WORKER;
			} else if (state.style == FactionType.BARBARIAN.ordinal()) {
				return foreman ? BARBARIAN_FOREMAN : BARBARIAN_WORKER;
			}
			return foreman ? KINGDOM_FOREMEN[dye] : KINGDOM_WORKERS[dye];
		}
	}
}
