package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Valor;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.408) What a Valor's coating looks like on whoever has it up (Andy: "we only see particles"; until now the coat was
 * only a dark dust). Every part of a Valor shows:
 * <ul>
 * <li>its {@link Valor.Look}: the coat drawn over the arms, gunmetal bands, cracked stone, scales, flaking ash, a see-through
 * amber haze or black lacquer ({@code tools/gen_valor_coats.py});</li>
 * <li>its rank: the hands at Spark, half the forearm, the forearm, the whole arm, and at Unbroken over the shoulders;</li>
 * <li>its {@link Valor.Bent} (Tempered on): a mark round the wrist, a bracer, crossed slashes, claw stripes, runes or a scar;</li>
 * <li>King's Valor Coating: the coat's own lines (the scales' edges, the cracks, the bands) glow and beat in the soul colour;</li>
 * <li>a Hunter's eyes glint amber while coated (the other origins have their own particles, {@code Valor#coatParticles}).</li>
 * </ul>
 * Drawn on a shell just past the sleeves, or just past armour when a chestplate is worn, so armour never hides it; on players,
 * on MCA villagers and our own folk alike, and on your own first-person arm.
 */
public final class ValorLook {
	/** The shell's growth past the skin: bare (past the sleeves), over a chestplate (past armour). */
	static final float BARE = 0.35F, ARMOURED = 1.08F;
	/** The King's lines' beat: how fast, how deep; their least brightness (a dark soul colour still shows). */
	static final float BEAT_SPEED = 0.15F, BEAT = 0.25F, GLOW_FLOOR = 0.75F;
	/** A Hunter's coated eyes. */
	static final int HUNTER_EYES = 0xFFFFB030;
	/** Each look's wrist-mark colour (iron, stone, scale, ash, haze, lacquer). */
	static final int[] BENT_TINT = {0xFFB8C0CC, 0xFFA89E8E, 0xFF7FC09A, 0xFFE0DCD4, 0xFFF0C890, 0xFFA0A0D8};

	/** On a render state: the coating up right now. */
	public static final RenderStateDataKey<Coat> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:valor_coat");

	/** A coating up: its look, rank, bent (-1 before Tempered), origin, whether King's, its soul colour. */
	public record Coat(int look, int rank, int bent, int origin, boolean kings, int soul) {
		Identifier coat() {
			return texture(look, "_" + rank);
		}

		Identifier veins() {
			return texture(look, "_veins_" + rank);
		}

		@Nullable Identifier bentMark() {
			return bent < 0 || bent >= Valor.Bent.values().length ? null
				: JaymosKingdoms.id("textures/entity/valor/bent_" + Valor.Bent.values()[bent].key() + ".png");
		}

		boolean hunter() {
			return origin == Valor.Origin.HUNTER.ordinal();
		}
	}

	private static final Map<String, Identifier> TEXTURES = new HashMap<>();
	private static final Map<Identifier, Identifier> HUNTER_EYES_BY_SKIN = new HashMap<>();

	private ValorLook() {
	}

	static Identifier texture(int look, String suffix) {
		Valor.Look kind = Valor.Look.values()[Mth.clamp(look, 0, Valor.Look.values().length - 1)];
		return TEXTURES.computeIfAbsent(kind.key() + suffix, k -> JaymosKingdoms.id("textures/entity/valor/" + k + ".png"));
	}

	static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Players(avatar));
			} else if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(folk(renderer));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Folk<?> folk(RenderLayerParent<?, ?> renderer) {
		return new Folk((RenderLayerParent) renderer);
	}

	/** From the living renderer's extractRenderState: the coating, while Reinforcement or Armament is up. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, coatOf(entity));
	}

	static @Nullable Coat coatOf(LivingEntity entity) {
		int bits = Valor.state(entity);
		if ((bits & (Valor.REINFORCED | Valor.ARMED | Valor.KINGS)) == 0 || entity.isInvisible()) {
			return null;
		}
		Valor.Data data = Valor.of(entity);
		if (data == null) {
			return null;
		}
		return new Coat(data.look(), Mth.clamp(data.rank(), 0, 4), data.rank() >= Valor.TEMPERED ? data.bent() : -1, data.origin(),
			(bits & Valor.KINGS) != 0, data.soulColor());
	}

	/** The King's lines' colour now: the soul colour, lifted so a dark one still glows, beating. */
	static int glow(int soul, float age) {
		float r = ARGB.red(soul) / 255.0F, g = ARGB.green(soul) / 255.0F, b = ARGB.blue(soul) / 255.0F;
		float top = Math.max(r, Math.max(g, b));
		float lift = top <= 0.0F ? 1.0F : Math.max(1.0F, GLOW_FLOOR / top);
		float k = lift * (1.0F - BEAT + BEAT * Mth.sin(age * BEAT_SPEED));
		return ARGB.colorFromFloat(1.0F, Math.min(1.0F, r * k), Math.min(1.0F, g * k), Math.min(1.0F, b * k));
	}

	/** On players: the coat, and a Hunter's eyes found on their own skin. */
	static final class Players extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final Shells shells;

		Players(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
			this.shells = new Shells(this::getParentModel);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Coat coat = state.getData(KEY);
			if (coat == null || state.isInvisible) {
				return;
			}
			this.shells.submit(poseStack, collector, lightCoords, state, coat, state.skin.model() == PlayerModelType.SLIM);
			if (coat.hunter() && !PowersCover.face(state)) {
				Identifier eyes = VampireLooks.glowingEyes(state.skin.body().texturePath(), HUNTER_EYES, EmberLook.EYES, "valor_hunter_eyes/",
					HUNTER_EYES_BY_SKIN);
				collector.order(2).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(eyes), lightCoords, OverlayTexture.NO_OVERLAY, -1,
					null, state.outlineColor);
			}
		}
	}

	/** On everyone else humanoid: MCA's villagers (rulers, guards, Valor teachers) and our own folk. */
	static final class Folk<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		private final Shells shells;

		Folk(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
			this.shells = new Shells(this::getParentModel);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Coat coat = state.getData(KEY);
			if (coat == null || state.isInvisible) {
				return;
			}
			this.shells.submit(poseStack, collector, lightCoords, state, coat, false);
			if (coat.hunter() && McaCompat.LOADED) {
				Identifier eyes = McaEyes.glowing(state, HUNTER_EYES);
				HumanoidModel<?> glowing = McaLookModels.glow(this.getParentModel());
				if (eyes != null && glowing != null) {
					submitAny(collector, glowing, state, poseStack, RenderTypes.eyes(eyes), lightCoords, -1, state.outlineColor);
				}
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void submitAny(SubmitNodeCollector collector, HumanoidModel model, HumanoidRenderState state, PoseStack poseStack,
		net.minecraft.client.renderer.rendertype.RenderType type, int light, int color, int outline) {
		collector.order(2).submitModel(model, state, poseStack, type, light, OverlayTexture.NO_OVERLAY, color, null, outline);
	}

	/** The coat's shells over one renderer's model: bare or armoured, classic or slim arms. */
	static final class Shells {
		private final Supplier<HumanoidModel<?>> wearer;
		/** [armoured][slim] */
		private final HumanoidModel<?>[][] models = new HumanoidModel<?>[2][2];

		Shells(Supplier<HumanoidModel<?>> wearer) {
			this.wearer = wearer;
		}

		private HumanoidModel<?> shell(boolean armoured, boolean slim) {
			int a = armoured ? 1 : 0, s = slim ? 1 : 0;
			if (this.models[a][s] == null) {
				CubeDeformation grow = new CubeDeformation(armoured ? ARMOURED : BARE);
				this.models[a][s] = new WornModel(LayerDefinition.create(PlayerModel.createMesh(grow, slim), 64, 64).bakeRoot(), this.wearer);
			}
			return this.models[a][s];
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidRenderState state, Coat coat, boolean slim) {
			HumanoidModel model = this.shell(!state.chestEquipment.isEmpty(), slim);
			collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(coat.coat()), lightCoords, OverlayTexture.NO_OVERLAY, -1,
				null, state.outlineColor);
			Identifier bent = coat.bentMark();
			if (bent != null) {
				collector.order(2).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(bent), lightCoords, OverlayTexture.NO_OVERLAY,
					BENT_TINT[Mth.clamp(coat.look(), 0, BENT_TINT.length - 1)], null, state.outlineColor);
			}
			if (coat.kings()) {
				collector.order(3).submitModel(model, state, poseStack, RenderTypes.eyes(coat.veins()), lightCoords, OverlayTexture.NO_OVERLAY,
					glow(coat.soul(), state.ageInTicks), null, state.outlineColor);
			}
		}
	}

	// ---- your own arm in first person ------------------------------------------------------------------------------

	/** [slim]: the first-person arms' own shells (apart from the body's, which another mod may draw in first person). */
	private static final PlayerModel[] HANDS = new PlayerModel[2];

	/** After your own first-person arm is drawn (AvatarRenderer.renderHand): your coat over it. */
	public static void firstPersonHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Coat coat = coatOf(player);
		if (coat == null) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		int s = slim ? 1 : 0;
		if (HANDS[s] == null) {
			HANDS[s] = new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(BARE), slim), 64, 64).bakeRoot(), slim);
		}
		ModelPart part = arm == source.rightArm ? HANDS[s].rightArm : HANDS[s].leftArm;
		part.loadPose(arm.storePose());
		part.xScale = arm.xScale;
		part.yScale = arm.yScale;
		part.zScale = arm.zScale;
		part.visible = true;
		collector.order(1).submitModelPart(part, poseStack, RenderTypes.entityTranslucent(coat.coat()), light, OverlayTexture.NO_OVERLAY, null, -1);
		Identifier bent = coat.bentMark();
		if (bent != null) {
			collector.order(2).submitModelPart(part, poseStack, RenderTypes.entityTranslucent(bent), light, OverlayTexture.NO_OVERLAY, null,
				BENT_TINT[Mth.clamp(coat.look(), 0, BENT_TINT.length - 1)]);
		}
		if (coat.kings()) {
			collector.order(3).submitModelPart(part, poseStack, RenderTypes.eyes(coat.veins()), light, OverlayTexture.NO_OVERLAY, null,
				glow(coat.soul(), player.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)));
		}
	}
}
