package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.QuestNet;
import com.jaymoskingdoms.Quests;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.29) The client half of {@link Quests}: the giver's screen ({@link QuestScreen}), the marks over givers' heads (a
 * gold <b>!</b>: work on offer; a <b>?</b>: one of your quests, gold once it's ready to turn in) and the quest tracker on
 * the right of the screen.
 */
public final class QuestClient {
	/** Tracker: how many quests it lists, its width, how far down the right edge it starts (under the effect icons). */
	static final int TRACKED = 3, WIDTH = 150, TOP = 52;
	/** Marks: how big; how far over the name tag spot their foot is (blocks), clear of the name tag; over a speech bubble, this much more per line. */
	static final float MARK_SCALE = 0.065F;
	static final double MARK_LIFT = 0.8, BUBBLE_LINE = 0.25, BUBBLE_LIFT = 0.85;
	static final int GOLD = 0xFFFFC21A, GREY = 0xFFB0B0B0, ROYAL = 0xFFFF5544;

	/** The player's quests, as last sent. */
	private static List<QuestNet.Tracked> log = List.of();
	/** Givers of the player's quests → whether one's ready. */
	private static final Map<UUID, Boolean> GIVERS = new HashMap<>();

	private record Mark(FormattedCharSequence symbol, int color, Vec3 attachment) {
	}

	static final RenderStateDataKey<Mark> KEY = RenderStateDataKey.create(() -> "jaymoskingdoms:quest_mark");

	private QuestClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(QuestNet.Open.TYPE, (payload, context) -> context.client().gui.setScreen(new QuestScreen(payload)));
		ClientPlayNetworking.registerGlobalReceiver(QuestNet.Log.TYPE, (payload, context) -> {
			log = List.copyOf(payload.quests());
			GIVERS.clear();
			for (QuestNet.Tracked t : log) {
				GIVERS.merge(t.giver(), t.ready(), Boolean::logicalOr);
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			log = List.of();
			GIVERS.clear();
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, JaymosKingdoms.id("quest_tracker"), QuestClient::tracker);
	}

	// ---- marks ---------------------------------------------------------------------------------------------------------

	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		if (!(entity instanceof LivingEntity) || entity.isInvisible()) {
			state.setData(KEY, null);
			return;
		}
		Boolean ready = GIVERS.get(entity.getUUID());
		String symbol;
		int color;
		if (ready != null) {
			symbol = "?";
			color = ready ? GOLD : GREY;
		} else if (Boolean.TRUE.equals(entity.getAttached(Quests.MARK))) {
			symbol = "!";
			color = GOLD;
		} else {
			state.setData(KEY, null);
			return;
		}
		Vec3 at = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
		if (at == null) {
			at = new Vec3(0.0, entity.getBbHeight(), 0.0);
		}
		state.setData(KEY, new Mark(Component.literal(symbol).withStyle(ChatFormatting.BOLD).getVisualOrderText(), color, at));
	}

	public static void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		@Nullable Mark mark = state.getData(KEY);
		if (mark == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		poseStack.pushPose();
		// (its foot over the name tag, or over whatever they're saying)
		SpeechBubbles.Bubble bubble = state.getData(SpeechBubbles.KEY);
		double foot = bubble == null ? MARK_LIFT : BUBBLE_LIFT + bubble.rows().size() * BUBBLE_LINE;
		poseStack.translate(mark.attachment().x, mark.attachment().y + foot + font.lineHeight * MARK_SCALE, mark.attachment().z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(MARK_SCALE, -MARK_SCALE, MARK_SCALE);
		float x = -font.width(mark.symbol()) / 2.0F;
		collector.submitText(poseStack, x, 0.0F, mark.symbol(), true, Font.DisplayMode.NORMAL, 0xF000F0, mark.color(), 0, 0);
		poseStack.popPose();
	}

	// ---- the tracker ---------------------------------------------------------------------------------------------------

	private static void tracker(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || log.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int x = graphics.guiWidth() - WIDTH - 4;
		int y = TOP;
		int shown = 0;
		for (QuestNet.Tracked t : log) {
			if (shown++ >= TRACKED) {
				break;
			}
			List<FormattedCharSequence> title = font.split(t.title(), WIDTH);
			List<FormattedCharSequence> goal = font.split(t.objective(), WIDTH);
			Component where = t.target().map(pos -> direction(player, pos)).orElse(null);
			int lines = Math.min(1, title.size()) + Math.min(2, goal.size()) + (where == null ? 0 : 1);
			graphics.fill(x - 3, y - 2, x + WIDTH + 2, y + lines * 10, 0x60000000);
			graphics.text(font, title.get(0), x, y, t.royal() ? ROYAL : GOLD);
			y += 10;
			for (int i = 0; i < Math.min(2, goal.size()); i++) {
				graphics.text(font, goal.get(i), x, y, t.ready() ? 0xFF66EE66 : 0xFFDDDDDD);
				y += 10;
			}
			if (where != null) {
				graphics.text(font, where, x, y, GREY);
				y += 10;
			}
			y += 4;
		}
	}

	/** How far and which way {@code pos} lies from the player. */
	private static Component direction(LocalPlayer player, BlockPos pos) {
		double dx = pos.getX() + 0.5 - player.getX(), dz = pos.getZ() + 0.5 - player.getZ();
		int distance = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
		// (0 = south in Minecraft's yaw; eight points of the compass)
		double angle = Math.toDegrees(Math.atan2(dx, dz));
		String[] points = {"s", "se", "e", "ne", "n", "nw", "w", "sw"};
		int i = (int)Math.floorMod(Math.round(angle / 45.0), 8L);
		return Component.translatable("quest.jaymoskingdoms.tracker.where", distance, Component.translatable("quest.jaymoskingdoms.tracker." + points[i]));
	}
}
