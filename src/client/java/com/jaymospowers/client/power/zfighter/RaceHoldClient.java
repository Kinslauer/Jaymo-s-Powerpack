package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.client.power.ember.EmberKindleClient;
import com.jaymospowers.client.power.heritage.HeritageMovesClient;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.ember.EmberKindle;
import com.jaymospowers.power.heritage.HeritageMoves;
import com.jaymospowers.power.smoke.SmokePower;
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
 * prison once a hold, after {@link #FROST_HOLD}. A Frost-born Z Fighter keeps his hold combo for anything within plain
 * reach: the race move only takes the hold when nothing is. A Verdant Z Fighter's whole hold is his hold combo, which
 * stretches (0.13.30, {@code ZFighterStretchCombo}). Also the stretching arm itself, on everyone.
 * <p>
 * (0.13.30) Andy: "Swap the lineage click hold ability to secondary when you have a power or any other click hold, for
 * example, stretch should be the shift click instead". With no power, the race's move is the plain left hold. With a
 * power (other than the Z Fighter's, whose hold combo the race is part of), or another left-click hold wanting the
 * click ({@link #powerWants}), the plain left hold is the power's and the race's move is sneak + left hold
 * ({@link #secondary}, {@link #yielded}).
 * <p>
 * (0.14.0) An Emberborn's <b>Kindle</b> is held here too ({@link EmberKindleClient} keeps its own timing and packets):
 * the same rules, except that an Emberborn with the Smoke power keeps Kindle on the plain hold (Smoke has no left-click
 * hold). Once the flame runs it keeps the click even when the crosshair meets a block (to light a campfire, or melt snow).
 * <p>
 * (0.15.72) A Stormborn's Thunderclap and a Dawnborn's Sunflare ({@link HeritageMovesClient}) are held here the same way;
 * once the charge runs it keeps the click at a block too (so looking down to call the bolt at your feet doesn't drop it).
 */
public final class RaceHoldClient {
	static final int STRETCH_HOLD = 4, FROST_HOLD = 12;
	public static final RenderStateDataKey<Float> STRETCH = RenderStateDataKey.create(() -> "jaymospowers:verdant_stretch");
	/** The arm's 12 pixels, in blocks; and how far the shoulder sits from the eyes, roughly (blocks). */
	static final float ARM = 0.75F, SHOULDER = 0.35F;

	private static int held;
	private static boolean fired;
	/** Other powers' left-click holds that a sneaking Verdant or Frost-born can take the click for instead. */
	private static final java.util.List<java.util.function.Predicate<Player>> POWER_WANTS = new java.util.ArrayList<>();

	private RaceHoldClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(RaceHoldClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		int lineage = player == null ? ZFighterLineage.NONE : ZFighterLineage.of(player);
		boolean ember = player != null && EmberKindle.ember(player);
		boolean blood = player != null && HeritageMoves.blood(player) != HeritageMoves.NONE;
		boolean down = player != null && minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && !player.isSpectator()
			&& player.getMainHandItem().isEmpty() && (lineage == ZFighterLineage.VERDANT || lineage == ZFighterLineage.FROST || ember || blood)
			&& (claims(minecraft, player) || ember && EmberKindleClient.kindling() || blood && HeritageMovesClient.charging()) && !yielded(player);
		// (0.14.0) Kindle times itself; (0.15.72) so do Thunderclap and Sunflare
		EmberKindleClient.held(ember && down);
		HeritageMovesClient.held(blood && down);
		if (!down || ember || blood) {
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

	/**
	 * Whether the race's own hold move has the left mouse: not at a block (digging); for a Frost-born Z Fighter nothing
	 * within plain reach; never for a Verdant Z Fighter (his hold combo stretches instead).
	 */
	public static boolean claims(Minecraft minecraft, Player player) {
		HitResult hit = minecraft.hitResult;
		HitResult.Type type = hit == null ? HitResult.Type.MISS : hit.getType();
		if (type == HitResult.Type.BLOCK) {
			return false;
		}
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			return true;
		}
		return ZFighterLineage.of(player) != ZFighterLineage.VERDANT && type == HitResult.Type.MISS;
	}

	/** Another power's left-click hold, which a sneaking Verdant or Frost-born hands the click to (registered at init). */
	public static void powerWants(java.util.function.Predicate<Player> wants) {
		POWER_WANTS.add(wants);
	}

	/**
	 * The race's move is secondary for {@code player} (sneak + left hold): he has a power other than the Z Fighter's, or
	 * another power's left-click hold wants the click.
	 */
	public static boolean secondary(Player player) {
		if (Powers.has(player, ZFighterPower.INSTANCE)) {
			return false;
		}
		if (EmberKindle.ember(player) && Powers.has(player, SmokePower.INSTANCE)) {
			// (0.14.0) smoke merged into fire: for an Emberborn Smoke user the plain hold stays Kindle
			return false;
		}
		if (Powers.of(player) != null) {
			return true;
		}
		for (java.util.function.Predicate<Player> wants : POWER_WANTS) {
			if (wants.test(player)) {
				return true;
			}
		}
		return false;
	}

	/** The race stands aside for this click: its move is secondary ({@link #secondary}) and he isn't sneaking. */
	public static boolean yielded(Player player) {
		return secondary(player) && !player.isShiftKeyDown();
	}

	/**
	 * Whether the race has the left click right now (other powers' left-click holds stand aside): a Verdant or
	 * Frost-born, bare-handed, not at a block, and either with no other power or sneaking ({@link #yielded}). A Verdant Z
	 * Fighter's hold combo counts as his race's (it stretches).
	 */
	public static boolean first(Minecraft minecraft, Player player) {
		if (minecraft.gui.screen() != null || player.isSpectator() || !player.getMainHandItem().isEmpty() || ZFighterLineage.ape(player)) {
			return false;
		}
		int lineage = ZFighterLineage.of(player);
		boolean ember = EmberKindle.ember(player);
		boolean blood = HeritageMoves.blood(player) != HeritageMoves.NONE;
		if (lineage != ZFighterLineage.VERDANT && lineage != ZFighterLineage.FROST && !ember && !blood) {
			return false;
		}
		HitResult hit = minecraft.hitResult;
		if (hit != null && hit.getType() == HitResult.Type.BLOCK && !(ember && EmberKindleClient.kindling()) && !HeritageMovesClient.charging()) {
			return false;
		}
		return !yielded(player);
	}

	/** Whether the local player's race hold is under way (the hold combo stands aside). */
	public static boolean holding() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !minecraft.options.keyAttack.isDown() || !player.getMainHandItem().isEmpty()) {
			return false;
		}
		if (EmberKindleClient.kindling() || HeritageMovesClient.charging()) {
			return true;
		}
		int lineage = ZFighterLineage.of(player);
		return (lineage == ZFighterLineage.VERDANT || lineage == ZFighterLineage.FROST || EmberKindle.ember(player)
			|| HeritageMoves.blood(player) != HeritageMoves.NONE) && claims(minecraft, player)
			&& !yielded(player);
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
