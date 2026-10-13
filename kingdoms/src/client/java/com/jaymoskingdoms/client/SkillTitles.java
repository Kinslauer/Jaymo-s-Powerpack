package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Renown;
import com.jaymoskingdoms.Skills;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.51) Another player's alignment ({@link Renown}) over their name tag: Survivor, Rogue or Warrior, in its pillar's
 * colour. Hidden while they sneak, like a name tag behind a wall.
 */
public final class SkillTitles {
	/** How big; how far over the name tag's spot its foot is (blocks). */
	static final float SCALE = 0.02F;
	static final double LIFT = 0.78;
	static final int[] COLORS = {0xFF7CC46A, 0xFFB48CE6, 0xFFE0684A};

	private record Title(FormattedCharSequence text, int color, Vec3 attachment) {
	}

	static final RenderStateDataKey<Title> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:skill_title");

	private SkillTitles() {
	}

	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		Integer code = entity instanceof Player ? entity.getAttached(Renown.TITLE) : null;
		Skills.Pillar pillar = code == null ? null : Renown.titlePillar(code);
		if (pillar == null || entity == Minecraft.getInstance().player || entity.isInvisible() || entity.isShiftKeyDown()) {
			state.setData(KEY, null);
			return;
		}
		Vec3 at = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
		if (at == null) {
			at = new Vec3(0.0, entity.getBbHeight(), 0.0);
		}
		Component text = Component.translatable("title.jaymoskingdoms." + pillar.key() + (Renown.titleMaster(code) ? ".master" : ""));
		state.setData(KEY, new Title(text.getVisualOrderText(), COLORS[pillar.ordinal()], at));
	}

	public static void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		@Nullable Title title = state.getData(KEY);
		if (title == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		poseStack.pushPose();
		poseStack.translate(title.attachment().x, title.attachment().y + LIFT, title.attachment().z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(SCALE, -SCALE, SCALE);
		float x = -font.width(title.text()) / 2.0F;
		collector.submitText(poseStack, x, 0.0F, title.text(), false, Font.DisplayMode.NORMAL, 0xF000F0, title.color(), 0x40000000, 0);
		poseStack.popPose();
	}
}
