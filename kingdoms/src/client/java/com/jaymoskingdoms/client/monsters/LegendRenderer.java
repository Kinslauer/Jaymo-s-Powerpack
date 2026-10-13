package com.jaymoskingdoms.client.monsters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.monsters.LegendMonster;
import com.jaymoskingdoms.monsters.Mimic;
import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.Ogre;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * Draws the monsters of legend ({@link Monsters}): the Mo' Creatures ones with their models and skins (the wraiths see
 * through, like ghosts), the mud golem and the mimic with ours. The werewolf has its own ({@link WerewolfRenderer}).
 */
public class LegendRenderer<T extends LegendMonster> extends MobRenderer<T, LegendState, EntityModel<LegendState>> {
	/** A wraith's tint: grey, and 60% there. */
	static final int GHOST = 0x99CCCCCC;

	private final Function<LegendState, Identifier> texture;
	private final float scale;
	private final boolean ghost;

	LegendRenderer(EntityRendererProvider.Context context, EntityModel<LegendState> model, float shadow, float scale,
		Function<LegendState, Identifier> texture, boolean ghost) {
		super(context, model, shadow);
		this.texture = texture;
		this.scale = scale;
		this.ghost = ghost;
	}

	static Identifier texture(String name) {
		return JaymosKingdoms.id("textures/entity/legend/" + name + ".png");
	}

	@Override
	public LegendState createRenderState() {
		return new LegendState();
	}

	@Override
	public void extractRenderState(T monster, LegendState state, float partialTicks) {
		super.extractRenderState(monster, state, partialTicks);
		state.variant = monster.variant();
		state.arm = monster.swingingArm();
		state.leftAttack = monster.leftCounter() > 0 ? monster.leftCounter() + partialTicks : 0.0F;
		state.rightAttack = monster.rightCounter() > 0 ? monster.rightCounter() + partialTicks : 0.0F;
		state.sprinting = monster.isSprinting();
		state.aggressive = monster.isAggressive();
		state.twoHeads = monster instanceof Ogre ogre && ogre.twoHeads();
		state.shut = monster instanceof Mimic mimic && mimic.shut();
	}

	@Override
	protected void scale(LegendState state, PoseStack poseStack) {
		if (this.scale != 1.0F) {
			poseStack.scale(this.scale, this.scale, this.scale);
		}
	}

	@Override
	protected void setupRotations(LegendState state, PoseStack poseStack, float bodyRot, float entityScale) {
		super.setupRotations(state, poseStack, bodyRot, entityScale);
		// (the silver skeleton) leaning into a sprint
		if (state.sprinting && state.walkAnimationSpeed > 0.3F) {
			poseStack.rotateDegrees(Axis.XP, state.walkAnimationSpeed * 20.0F);
		}
	}

	@Override
	public Identifier getTextureLocation(LegendState state) {
		return this.texture.apply(state);
	}

	@Override
	protected @Nullable RenderType getRenderType(LegendState state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (this.ghost && bodyVisible) {
			return RenderTypes.entityTranslucent(this.getTextureLocation(state));
		}
		return super.getRenderType(state, bodyVisible, translucent, glowing);
	}

	@Override
	protected int getModelTint(LegendState state) {
		return this.ghost ? GHOST : super.getModelTint(state);
	}

	@Override
	protected boolean isShaking(LegendState state) {
		return state.changing || super.isShaking(state);
	}

	private static <T extends LegendMonster> void register(EntityType<T> type, Function<ModelPart, EntityModel<LegendState>> model, LayerDefinition layer,
		float shadow, float scale, Function<LegendState, Identifier> texture, boolean ghost) {
		EntityRenderers.register(type, context -> new LegendRenderer<>(context, model.apply(layer.bakeRoot()), shadow, scale, texture, ghost));
	}

	private static Function<LegendState, Identifier> one(String name) {
		Identifier id = texture(name);
		return state -> id;
	}

	public static void register() {
		WerewolfRenderer.install();
		register(Monsters.OGRE, OgreModel::new, OgreModel.createBodyLayer(), 1.2F, 1.0F, one("ogre_green"), false);
		register(Monsters.CAVE_OGRE, OgreModel::new, OgreModel.createBodyLayer(), 1.2F, 1.0F, one("ogre_cave"), false);
		register(Monsters.FIRE_OGRE, OgreModel::new, OgreModel.createBodyLayer(), 1.2F, 1.0F, one("ogre_fire"), false);
		register(Monsters.WRAITH, WraithModel::new, WraithModel.createBodyLayer(), 0.0F, 1.0F, one("wraith"), true);
		register(Monsters.FLAME_WRAITH, WraithModel::new, WraithModel.createBodyLayer(), 0.0F, 1.0F, one("flame_wraith"), false);
		register(Monsters.SILVER_SKELETON, SilverSkeletonModel::new, SilverSkeletonModel.createBodyLayer(), 0.6F, 1.0F, one("silver_skeleton"), false);
		Identifier[] mud = {texture("mud_golem"), texture("mud_golem_mossy")};
		register(Monsters.MUD_GOLEM, MudGolemModel::new, MudGolemModel.createBodyLayer(), 0.9F, 1.2F, state -> mud[Math.floorMod(state.variant, mud.length)],
			false);
		register(Monsters.MIMIC, MimicModel::new, MimicModel.createBodyLayer(), 0.6F, 1.0F, one("mimic"), false);
	}
}
