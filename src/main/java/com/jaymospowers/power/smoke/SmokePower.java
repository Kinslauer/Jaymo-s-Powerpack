package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Smoke: you make smoke, and the smoke is a real gas ({@link SmokeField}) that rises, fills rooms, leaks through gaps,
 * drifts on the wind and hides whatever is inside it. Everything is decided here, on the server.
 * <ul>
 * <li>R, Vapour Form ({@link SmokeVapour}): switched on, a sprint turns everything below your waist to smoke and you
 * cruise on it, Wind-style, at full speed until you brake to a hover; R switches it off. The starter (a thinner trail
 * until more is unlocked).</li>
 * <li>G (hold), Smokescreen ({@link SmokeScreen}): smoke pours out of you.</li>
 * <li>V, Smoke Bomb ({@link SmokeBomb}): a thrown ball that bursts into a cloud.</li>
 * <li>B (hold), Smother ({@link SmokeSmother}): smoke wraps whoever you aim at; they're blinded, slowed and choke.</li>
 * <li>X (hold), Smoke Jet ({@link SmokeJet}): a jet of smoke where you look; it fills a ship's sails.</li>
 * <li>Sneak + X, Smoke Step ({@link SmokeStep}): re-form inside one of your clouds under the crosshair.</li>
 * <li>Z, Smoke Sight ({@link SmokeSight}): creatures in smoke are outlined through walls.</li>
 * <li>Fully mastered, Shift in Vapour Form turns the whole body to smoke ({@link SmokeVapour#WHOLLY}): intangible.</li>
 * <li>No fall damage, ever: the legs go to smoke for a second as you land.</li>
 * <li>Corrupted, Smog ({@link SmokeSmog}): the smoke turns toxic and kills what grows; C is Pyroclasm
 * ({@link SmokePyroclasm}).</li>
 * <li>Ascended, Cloudshaper ({@link SmokeCloudshaper}): cloud smoke; C is Harness the Sky (a raincloud, or calm a
 * storm / unwind a tornado).</li>
 * <li>Both, Ashen Sky ({@link SmokeAshStorm}): C is Ash Storm; sneak + C rains soot or feeds a storm a tornado;
 * Unwind can kill a tornado, which drains the corruption.</li>
 * <li>Mastered Smokescreen banks the cloud: it holds against the wind and fades slowly ({@code SmokeField.bank}).</li>
 * </ul>
 * You see through your own smoke (the client draws it faint and doesn't grey your view in it). The way in is a calling
 * ({@code core/Callings}): breathe the smoke of a fire (3 burning blocks or lit campfires round you) for a minute.
 */
public final class SmokePower implements Power {
	public static final SmokePower INSTANCE = new SmokePower();
	public static final Identifier ID = JaymosPowers.id("smoke");
	public static final int COLOR = 0xA7ADB5;

	// ---- tuning (ticks) ----
	public static final int SCREEN_COOLDOWN_MIN = 40;
	public static final int SCREEN_COOLDOWN_MAX = 160;
	/** After Vapour Form ends, before it can be used again. */
	public static final int VAPOUR_COOLDOWN = 40;
	public static final int BOMB_COOLDOWN = 160;
	public static final int BOMB_COOLDOWN_MASTERED = 100;
	public static final int SMOTHER_COOLDOWN_MIN = 80;
	public static final int SMOTHER_COOLDOWN_MAX = 240;

	/** The owner's HUD state (cooldowns, what's running). Not saved: a relog clears cooldowns. */
	public static final AttachmentType<SmokeState> STATE = AttachmentRegistry.create(JaymosPowers.id("smoke_state"),
		builder -> builder.syncWith(SmokeState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private SmokePower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}. */
	public static void init() {
		com.jaymospowers.core.Powers.register(INSTANCE);
		SmokeNet.register();
		// (0.17.1) what a gifted villager does with Smoke
		SmokeGift.init();
		SmokeField.init();
		SmokeVapour.init();
		SmokeBomb.init();
		SmokeJet.init();
		SmokePyroclasm.init();
		SmokeCloudshaper.init();
		SmokeParticles.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SmokeScreen.clear();
			SmokeSmother.clear();
			SmokeBomb.clear();
			SmokeJet.clear();
			SmokeVapour.clear();
			SmokeSmog.clear();
			SmokePyroclasm.clear();
			SmokeCloudshaper.clear();
			SmokeAshStorm.clear();
		});
	}

	private static void forget(ServerPlayer player) {
		SmokeScreen.stop(player, false);
		SmokeSmother.stop(player, false);
		SmokeJet.stop(player);
		SmokeVapour.disarm(player);
		SmokeSight.off(player);
		SmokeSmog.forget(player);
		SmokePyroclasm.forget(player);
		SmokeCloudshaper.forget(player);
		SmokeAshStorm.forget(player);
		SmokeField.forget(player);
		SmokeHeat.forget(player);
		SmokeMoves.forget(player);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.CAMPFIRE);
	}

	/** R (Vapour Form) first, then G (Smokescreen), V (Smoke Bomb), B (Smother). */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_3, PowerKey.ABILITY_4};
	}

	/** A fresh awakening flies on smoke legs (Vapour Form) on R. */
	@Override
	public @Nullable PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/** Z is Smoke Sight (not for a fresh awakening); X (held) is the Smoke Jet, Sneak + X Smoke Step. */
	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return "special".equals(slot) || "mode".equals(slot) && SmokeSight.unlocked(player);
	}

	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (down) {
			SmokeSight.toggle(player);
		}
	}

	@Override
	public void onGained(ServerPlayer player) {
		player.setAttached(STATE, SmokeState.NONE);
	}

	/** (0.15.31) Downed: what was running ends. */
	@Override
	public void onDowned(ServerPlayer player) {
		// the screen, the smother and the jet stop (no cooldown), the vapour body comes back together
		SmokeScreen.stop(player, false);
		SmokeSmother.stop(player, false);
		SmokeJet.stop(player);
		SmokeVapour.disarm(player);
	}

	@Override
	public void onLost(ServerPlayer player) {
		forget(player);
		player.removeAttached(STATE);
	}

	@Override
	public void tick(ServerPlayer player) {
		SmokeScreen.tick(player);
		SmokeVapour.tick(player);
		SmokeSmother.tick(player);
		SmokeJet.tick(player);
		SmokeSight.tick(player);
		SmokeSmog.tick(player);
		SmokePyroclasm.gathering(player);
		SmokeCloudshaper.tick(player);
		SmokeAshStorm.tick(player);
		SmokeMoves.tick(player);
	}

	/** C: Harness the Sky (ascended), Pyroclasm (corrupted), Ash Storm and Harness the Sky's holds (both). */
	@Override
	public boolean ultimateWhenCorrupted() {
		return true;
	}

	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (com.jaymospowers.core.Fate.isCorrupted(player) && !com.jaymospowers.core.Fate.isAscended(player)) {
			SmokePyroclasm.key(player, down);
		} else {
			SmokeCloudshaper.key(player, down);
		}
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (down && key != PowerKey.ABILITY_1 && SmokeVapour.wholly(player)) {
			return; // wholly smoke: nothing but landing
		}
		switch (key) {
			case ABILITY_1 -> {
				if (down) {
					SmokeVapour.toggle(player);
				}
			}
			case ABILITY_2 -> {
				if (down && ready(player, SmokeState.SCREEN)) {
					SmokeScreen.start(player);
				} else if (!down) {
					SmokeScreen.stop(player, true);
				}
			}
			case ABILITY_3 -> {
				if (down && ready(player, SmokeState.BOMB) && SmokeBomb.throwBomb(player)) {
					cooldown(player, SmokeState.BOMB, mastered(player) ? BOMB_COOLDOWN_MASTERED : BOMB_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (down && ready(player, SmokeState.SMOTHER)) {
					SmokeSmother.start(player);
				} else if (!down) {
					SmokeSmother.stop(player, true);
				}
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		return SmokeVapour.active(player);
	}

	/** Vapour Form flies faster than the server's "moved too quickly" check allows. */
	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		return SmokeVapour.active(player);
	}

	// ---------------------------------------------------------------- state

	public static SmokeState state(Player player) {
		SmokeState state = player.getAttached(STATE);
		return state == null ? SmokeState.NONE : state;
	}

	static void set(ServerPlayer player, SmokeState state) {
		player.setAttached(STATE, state);
	}

	static boolean ready(ServerPlayer player, int slot) {
		return state(player).left(slot, player.level().getGameTime()) <= 0;
	}

	static void cooldown(ServerPlayer player, int slot, int ticks) {
		set(player, state(player).withCooldown(slot, player.level().getGameTime(), ticks));
	}

	/** Every ability unlocked. */
	static boolean mastered(Player player) {
		return Mastery.unlocked(player) >= Mastery.ALL;
	}
}
