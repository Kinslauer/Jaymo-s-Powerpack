package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Reaper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.GrimReaperEntityModel;
import net.conczin.mca.client.model.ModelTransformSet;
import net.conczin.mca.client.render.GrimReaperRenderState;
import net.conczin.mca.entity.ReaperAttackState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;

/**
 * The Reaper's body: Minecraft Comes Alive's own Grim Reaper (Andy: "is MCA's reaper in? if so it should look like that"),
 * its model and its texture used where they are, in MCA's jar. Only {@link ReaperClient} touches this, and only when MCA is
 * loaded.
 * <p>
 * Its weapon (Andy: "do the execution with a random weapon but usually his scythe, same for when he kills ghosts", then
 * "use the real models please"): MCA's scythe, or one of the pack's own weapons in its place ({@link #ITEMS}: the real
 * item, drawn in the same hand so MCA's own poses swing it). Where that item isn't in the game (Balkon's WeaponMod left
 * out), a plain one built of boxes stands in.
 */
final class McaReaper {
	static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mca", "textures/entity/grimreaper.png");
	/** Steel on the left (x under 36), wood on the right. */
	static final Identifier WEAPONS = JaymosKingdoms.id("textures/entity/reaper_weapons.png");
	/** The scythe: at rest, drawn back, brought down. */
	static final int REST = 0, DRAWN = 1, DOWN = 2;
	/** The part of MCA's model the scythe is, on its left arm. */
	private static final String SCYTHE = "scythe_handle";

	/** The real weapons, by {@link Reaper#GREATSWORD} and the rest (the scythe is MCA's own). */
	private static final String[] ITEMS = {null, "jaymospowers:iron_claymore", "weaponmod:battleaxe.iron", "weaponmod:warhammer.iron", "weaponmod:halberd.iron"};
	/** How big each is drawn, and how far up from its grip end the hand holds it (in the item's own width). */
	private static final float[] ITEM_SIZE = {0.0F, 1.7F, 1.6F, 1.6F, 2.1F}, ITEM_GRIP = {0.0F, 0.30F, 0.26F, 0.26F, 0.16F};
	/** The weapon is drawn only once the figure is at least this solid (an item can't be drawn faint). */
	private static final int ITEM_FROM_ALPHA = 110;
	private static final ItemStack[] STACKS = new ItemStack[Reaper.WEAPONS];
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();

	private static GrimReaperEntityModel model, bare;
	private static final GrimReaperEntityModel[] ARMED = new GrimReaperEntityModel[Reaper.WEAPONS];

	private McaReaper() {
	}

	/** MCA's Grim Reaper with its hand's part replaced by {@code weapon} (null: MCA's own scythe). */
	private static GrimReaperEntityModel bake(CubeListBuilder weapon) {
		MeshDefinition mesh = GrimReaperEntityModel.getModelData(CubeDeformation.NONE);
		if (weapon != null) {
			mesh.getRoot().getChild("left_arm").addOrReplaceChild(SCYTHE, weapon, ModelTransformSet.Builder.createTransform(0.0F, 10.0F, 0.0F, 90.0F, -20.0F, 90.0F));
		}
		return new GrimReaperEntityModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	/** One of our weapons, the hand at y 0 and its business end toward -y (as the scythe's is). */
	private static CubeListBuilder weapon(int weapon) {
		CubeListBuilder b = CubeListBuilder.create();
		switch (weapon) {
			case Reaper.GREATSWORD -> b.texOffs(40, 0).addBox(0.0F, -4.0F, 0.0F, 1.0F, 9.0F, 1.0F)
				.texOffs(0, 40).addBox(-2.5F, -5.0F, -0.25F, 6.0F, 1.0F, 1.5F)
				.texOffs(0, 0).addBox(-0.5F, -29.0F, 0.25F, 2.0F, 24.0F, 0.5F);
			case Reaper.GREATAXE -> b.texOffs(40, 0).addBox(0.0F, -26.0F, 0.0F, 1.0F, 31.0F, 1.0F)
				.texOffs(0, 0).addBox(1.0F, -27.0F, 0.25F, 6.0F, 9.0F, 0.5F)
				.texOffs(0, 12).addBox(-3.0F, -25.0F, 0.25F, 3.0F, 5.0F, 0.5F);
			case Reaper.WARHAMMER -> b.texOffs(40, 0).addBox(0.0F, -26.0F, 0.0F, 1.0F, 31.0F, 1.0F)
				.texOffs(0, 0).addBox(-3.0F, -28.0F, -1.0F, 7.0F, 4.0F, 3.0F);
			default -> b.texOffs(40, 0).addBox(0.0F, -26.0F, 0.0F, 1.0F, 31.0F, 1.0F)
				.texOffs(0, 0).addBox(0.0F, -33.0F, 0.25F, 1.0F, 7.0F, 0.5F)
				.texOffs(0, 12).addBox(1.0F, -30.0F, 0.25F, 4.0F, 5.0F, 0.5F)
				.texOffs(0, 20).addBox(-2.0F, -28.0F, 0.25F, 2.0F, 2.0F, 0.5F);
		}
		return b;
	}

	/** Every part's own boxes hidden but the weapon's (children are still drawn). */
	private static void weaponOnly(GrimReaperEntityModel m) {
		for (ModelPart part : new ModelPart[] {m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg}) {
			part.skipDraw = true;
		}
	}

	/**
	 * MCA's Grim Reaper at {@code pose}, {@code age} ticks old, its weapon ({@code weapon}: {@link Reaper#SCYTHE} or one of
	 * ours) held as {@code scythe}, tinted {@code argb} (its alpha how solid it is).
	 */
	static void submit(SubmitNodeCollector collector, PoseStack pose, float age, int scythe, int weapon, int argb, int order) {
		if (model == null) {
			model = bake(null);
			bare = bake(CubeListBuilder.create());
			for (int w = 1; w < Reaper.WEAPONS; w++) {
				ARMED[w] = bake(weapon(w));
				weaponOnly(ARMED[w]);
			}
		}
		GrimReaperRenderState state = new GrimReaperRenderState();
		state.ageInTicks = age;
		state.attackState = scythe == DRAWN ? ReaperAttackState.PRE : scythe == DOWN ? ReaperAttackState.POST : ReaperAttackState.IDLE;
		boolean own = weapon > Reaper.SCYTHE && weapon < Reaper.WEAPONS;
		collector.order(order).submitModel(own ? bare : model, state, pose, RenderTypes.entityTranslucent(TEXTURE), 0xF000F0, OverlayTexture.NO_OVERLAY, argb, null, 0);
		if (!own) {
			return;
		}
		ItemStack stack = stack(weapon);
		if (stack.isEmpty()) {
			collector.order(order).submitModel(ARMED[weapon], state, pose, RenderTypes.entityTranslucent(WEAPONS), 0xF000F0, OverlayTexture.NO_OVERLAY, argb, null, 0);
		} else if (ARGB.alpha(argb) >= ITEM_FROM_ALPHA) {
			// the hand's own place: the model posed as it will be drawn, then down its left arm to where the scythe sits
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.getItemModelResolver().updateForTopItem(ITEM, stack, ItemDisplayContext.NONE, minecraft.level, null, weapon);
			bare.setupAnim(state);
			pose.pushPose();
			bare.root().translateAndRotate(pose);
			bare.leftArm.translateAndRotate(pose);
			bare.leftArm.getChild(SCYTHE).translateAndRotate(pose);
			pose.translate(0.5F / 16.0F, 0.0F, 0.5F / 16.0F);
			// (a held item's picture runs grip to tip from its lower left to its upper right; the scythe's runs toward -y)
			pose.rotateDegrees(Axis.ZP, 225.0F);
			pose.scale(ITEM_SIZE[weapon], ITEM_SIZE[weapon], ITEM_SIZE[weapon]);
			pose.translate(0.5F - ITEM_GRIP[weapon], 0.5F - ITEM_GRIP[weapon], 0.0F);
			ITEM.submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}
	}

	/** The real item for {@code weapon}, or an empty stack if it isn't in this game. */
	private static ItemStack stack(int weapon) {
		if (STACKS[weapon] == null) {
			STACKS[weapon] = BuiltInRegistries.ITEM.getOptional(Identifier.parse(ITEMS[weapon])).map(ItemStack::new).orElse(ItemStack.EMPTY);
		}
		return STACKS[weapon];
	}
}
