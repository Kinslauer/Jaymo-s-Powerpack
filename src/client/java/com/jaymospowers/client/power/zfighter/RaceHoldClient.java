package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.core.Powers;
import com.jaymospowers.power.zfighter.RaceMoves;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.101) The races' left-mouse holds ({@link RaceMoves}): bare-handed, not at a block. A Verdant's stretch punch goes
 * off after {@link #STRETCH_HOLD} ticks, asked for again every {@link RaceMoves#STRETCH_EVERY} while held (the server
 * lets one through per {@link RaceMoves#STRETCH_COOLDOWN}); a Frost-born's ice
 * prison once a hold, after {@link #FROST_HOLD}. A Z Fighter keeps his hold combo for anything within plain reach: the
 * race move only takes the hold when nothing is. Also the stretching arm itself, on everyone.
 */
public final class RaceHoldClient {
	static final int STRETCH_HOLD = 4, FROST_HOLD = 12;
	public static final RenderStateDataKey<Float> STRETCH = RenderStateDataKey.create(() -> "jaymospowers:verdant_stretch");
	/** The arm's 12 pixels, in blocks; and how far the shoulder sits from the eyes, roughly (blocks). */
	static final float ARM = 0.75F, SHOULDER = 0.35F;

	private static int held;
	private static boolean fired;

	private RaceHoldClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(RaceHoldClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		int lineage = player == null ? ZFighterLineage.NONE : ZFighterLineage.of(player);
		boolean down = player != null && minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& player.getMainHandItem().isEmpty() && (lineage == ZFighterLineage.VERDANT || lineage == ZFighterLineage.FROST) && claims(minecraft, player);
		if (!down) {
			held = 0;
			fired = false;
			return;
		}
		held++;
		if (lineage == ZFighterLineage.VERDANT) {
			if (held >= STRETCH_HOLD && (held - STRETCH_HOLD) % RaceMoves.STRETCH_EVERY == 0) {
				ClientPlayNetworking.send(new RaceMoves.Hold());
			}
		} else if (!fired && held >= FROST_HOLD) {
			fired = true;
			ClientPlayNetworking.send(new RaceMoves.Hold());
		}
	}

	/** Whether the race's hold has the left mouse: not at a block (digging), and for a Z Fighter nothing within plain reach. */
	public static boolean claims(Minecraft minecraft, Player player) {
		HitResult hit = minecraft.hitResult;
		HitResult.Type type = hit == null ? HitResult.Type.MISS : hit.getType();
		if (type == HitResult.Type.BLOCK) {
			return false;
		}
		return type == HitResult.Type.MISS || !Powers.has(player, ZFighterPower.INSTANCE);
	}

	/** Whether the local player's race hold is under way (the hold combo stands aside). */
	public static boolean holding() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !minecraft.options.keyAttack.isDown() || !player.getMainHandItem().isEmpty()) {
			return false;
		}
		int lineage = ZFighterLineage.of(player);
		return (lineage == ZFighterLineage.VERDANT || lineage == ZFighterLineage.FROST) && claims(minecraft, player);
	}

	static void extract(Avatar entity, AvatarRenderState state, float partial) {
		@Nullable Float out = null;
		if (entity instanceof Player player) {
			RaceMoves.Stretch stretch = player.getAttached(RaceMoves.STRETCH);
			if (stretch != null) {
				float t = RaceMoves.stretchOut(stretch, player.level().getGameTime(), partial);
				if (t > 0.0F) {
					out = 1.0F + Math.max(0.0F, stretch.reach() - SHOULDER - ARM) / ARM * t;
				}
			}
		}
		state.setData(STRETCH, out);
	}

	/** The right arm thrown straight out along the look and drawn out long. */
	static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h) || avatar.deathTime > 0.0F) {
			return;
		}
		Float scale = avatar.getData(STRETCH);
		if (scale == null) {
			return;
		}
		h.rightArm.xRot = -Mth.HALF_PI + avatar.xRot * Mth.DEG_TO_RAD;
		h.rightArm.yRot = h.head.yRot;
		h.rightArm.zRot = 0.0F;
		h.rightArm.yScale = scale;
	}
}
