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
	/**
	 * Where a name tag sits over the name tag spot (blocks, as vanilla draws it), and the gap left above it: the bubble's
	 * bottom sits there, so a name tag never runs into the words.
	 */
	static final double NAME_TAG_TOP = 0.5, NAME_TAG_GAP = 0.08;
	/** Text size in the world (blocks per font pixel), as a name tag's. */
	static final float SCALE = 0.025F;
	/**
	 * Andy's clip: words from a villager a block or two away filled the screen and ran off its top. Nearer than this
	 * (blocks) the words shrink with the distance, so they look the size they would from here.
	 */
	static final double FULL_SIZE_FROM = 5.0;
	/** Rows shown per speaker at most (the oldest go first), so a long talk doesn't tower over them. */
	static final int MAX_ROWS = 5;
	/** Background behind the words: black at this opacity (0-255), faded with the words; dark enough to read over sky or snow. */
	static final int BACKGROUND_ALPHA = 0xB0;
	/** (0.7.597) How strongly the words show through a block in the way (of their full opacity). */
	static final float SEE_THROUGH_ALPHA = 0.5F;

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
		// (0.7.597) and words still showing aren't stacked again under themselves: they just stay up longer
		String said = text.getString();
		for (java.util.Iterator<Line> it = lines.iterator(); it.hasNext(); ) {
			Line old = it.next();
			if (old.said.equals(said) && (now - old.shownAt < REPEAT_MS || old.until - FADE_MS > now)) {
				if (now - old.shownAt < REPEAT_MS) {
					return;
				}
				it.remove();
				lines.addLast(new Line(said, old.rows, color, old.shownAt, Math.max(old.until, now + lasts)));
				return;
			}
		}
		lines.addLast(new Line(said, font.split(text, WRAP_WIDTH), color, now, now + lasts));
		while (lines.size() > MAX_LINES) {
			lines.removeFirst();
		}
	}

	/** The words still showing over {@code entityId}'s head, newest last, with their colours (the player's own head is not drawn in first person, so a small HUD repeats it). */
	static List<Map.Entry<List<FormattedCharSequence>, Integer>> own(int entityId) {
		Deque<Line> lines = SAID.get(entityId);
		List<Map.Entry<List<FormattedCharSequence>, Integer>> out = new java.util.ArrayList<>();
		long now = Util.getMillis();
		if (lines != null) {
			for (Line line : lines) {
				if (line.until > now) {
					out.add(Map.entry(line.rows, line.color));
				}
			}
		}
		return out;
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
		while (rows.size() > MAX_ROWS) {
			rows.removeFirst();
			colors.removeFirst();
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
		int n = bubble.rows().size();
		float scale = SCALE * (float)Math.min(1.0, Math.sqrt(state.distanceToCameraSq) / FULL_SIZE_FROM);
		// (the bottom row's foot just above the name tag; rows are drawn from their top, down)
		double lift = NAME_TAG_TOP + NAME_TAG_GAP + font.lineHeight * scale;
		poseStack.translate(bubble.attachment().x, bubble.attachment().y + lift, bubble.attachment().z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(scale, -scale, scale);
		for (int i = 0; i < n; i++) {
			FormattedCharSequence row = bubble.rows().get(i);
			int color = bubble.colors().get(i);
			int background = ARGB.color((int)(ARGB.alpha(color) / 255.0F * BACKGROUND_ALPHA), 0);
			float x = -font.width(row) / 2.0F;
			float y = (i - n + 1) * (font.lineHeight + 1);
			// (0.7.597) Andy's clip showed words cut off where a wall stood between them and the camera: like a name tag, a
			// faint copy shows through blocks, and the full words wherever nothing is in the way
			int faint = ARGB.color((int)(ARGB.alpha(color) * SEE_THROUGH_ALPHA), color);
			collector.submitText(poseStack, x, y, row, false, Font.DisplayMode.SEE_THROUGH, 0xF000F0, faint, background, 0);
			collector.submitText(poseStack, x, y, row, true, Font.DisplayMode.NORMAL, 0xF000F0, color, 0, 0);
		}
		poseStack.popPose();
	}
}
