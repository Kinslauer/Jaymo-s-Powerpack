package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Speech;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Words spoken in the world, floating over the speaker's head and fading (the client half of {@link Speech}; MCA
 * villagers' talk comes here too, through {@code McaVillagerMessageMixin}). Newest line at the bottom, older ones pushed
 * up, a few at most per speaker.
 */
public final class SpeechBubbles {
	/** How long a line stays: a base, plus a little per letter, capped (milliseconds). */
	static final long BASE_MS = 3000, PER_CHAR_MS = 60, MAX_MS = 9000;
	/** The last stretch of it, fading out (milliseconds). */
	static final long FADE_MS = 800;
	/** (0.7.51) The same line from the same speaker within this long shows once (milliseconds). */
	static final long REPEAT_MS = 1500;
	/** Lines kept per speaker; a longer line wraps at this width (font pixels). */
	static final int MAX_LINES = 3, WRAP_WIDTH = 170;
	/** Seen from no farther than this (blocks), as chat reached before. */
	static final double MAX_DISTANCE = 24.0;
	/** Height above the name tag spot (blocks), so a name tag still fits under it. */
	static final double LIFT = 0.75;
	/** Background behind the words: black at this opacity (0-255), faded with the words. */
	static final int BACKGROUND_ALPHA = 0x60;

	private record Line(String said, List<FormattedCharSequence> rows, int color, long shownAt, long until) {
	}

	/** What a speaker shows this frame, taken off the entity for the renderer. */
	public record Bubble(List<FormattedCharSequence> rows, List<Integer> colors, Vec3 attachment) {
	}

	private static final Map<Integer, Deque<Line>> SAID = new HashMap<>();

	static final RenderStateDataKey<Bubble> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:speech");

	private SpeechBubbles() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Speech.Said.TYPE, (payload, context) -> add(payload.entityId(), payload.text(), payload.color()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> prune());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SAID.clear());
	}

	/** An MCA villager (by UUID) says {@code text}; false when it isn't in sight here (then it goes to chat as before). */
	public static boolean say(UUID speaker, Component text) {
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level != null ? mc.level.getEntity(speaker) : null;
		if (entity == null || mc.player == null || entity.distanceToSqr(mc.player) > MAX_DISTANCE * MAX_DISTANCE) {
			return false;
		}
		add(entity.getId(), text, Speech.WHITE);
		return true;
	}

	static void add(int entityId, Component text, int color) {
		Font font = Minecraft.getInstance().font;
		long now = Util.getMillis();
		long lasts = Math.min(MAX_MS, BASE_MS + PER_CHAR_MS * text.getString().length());
		Deque<Line> lines = SAID.computeIfAbsent(entityId, id -> new ArrayDeque<>());
		// (0.7.51) the same words twice at once (said to you and overheard, or one line sent to several of us) show once
		String said = text.getString();
		Line last = lines.peekLast();
		if (last != null && now - last.shownAt < REPEAT_MS && last.said.equals(said)) {
			return;
		}
		lines.addLast(new Line(said, font.split(text, WRAP_WIDTH), color, now, now + lasts));
		while (lines.size() > MAX_LINES) {
			lines.removeFirst();
		}
	}

	private static void prune() {
		long now = Util.getMillis();
		SAID.values().removeIf(lines -> {
			lines.removeIf(line -> line.until <= now);
			return lines.isEmpty();
		});
	}

	/** Render-state extraction: the entity's lines this frame, with their fade. */
	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		Deque<Line> lines = SAID.isEmpty() ? null : SAID.get(entity.getId());
		if (lines == null || lines.isEmpty() || entity.isInvisible()) {
			state.setData(KEY, null);
			return;
		}
		long now = Util.getMillis();
		List<FormattedCharSequence> rows = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (Line line : lines) {
			float alpha = Math.clamp((line.until - now) / (float)FADE_MS, 0.0F, 1.0F) * Math.clamp((now - line.shownAt) / 150.0F, 0.0F, 1.0F);
			if (alpha <= 0.02F) {
				continue;
			}
			for (FormattedCharSequence row : line.rows) {
				rows.add(row);
				colors.add(ARGB.color(alpha, line.color));
			}
		}
		Vec3 at = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
		if (at == null) {
			at = new Vec3(0.0, entity.getBbHeight(), 0.0);
		}
		state.setData(KEY, rows.isEmpty() ? null : new Bubble(rows, colors, at));
	}

	/** Draws them, facing the camera, over the name tag (the pose is at the entity's feet). */
	public static void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		@Nullable Bubble bubble = state.getData(KEY);
		if (bubble == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		poseStack.pushPose();
		poseStack.translate(bubble.attachment().x, bubble.attachment().y + LIFT, bubble.attachment().z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(0.025F, -0.025F, 0.025F);
		int n = bubble.rows().size();
		for (int i = 0; i < n; i++) {
			FormattedCharSequence row = bubble.rows().get(i);
			int color = bubble.colors().get(i);
			int background = ARGB.color((int)(ARGB.alpha(color) / 255.0F * BACKGROUND_ALPHA), 0);
			float x = -font.width(row) / 2.0F;
			float y = (i - n + 1) * (font.lineHeight + 1);
			collector.submitText(poseStack, x, y, row, false, Font.DisplayMode.NORMAL, 0xF000F0, color, background, 0);
		}
		poseStack.popPose();
	}
}
