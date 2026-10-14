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
 * <li>R (hold), Smokescreen ({@link SmokeScreen}): smoke pours out of you. The starter, weaker until mastered.</li>
 * <li>G, Vapour Form ({@link SmokeVapour}): you turn to smoke for a few seconds; blows pass through you.</li>
 * <li>V, Smoke Bomb ({@link SmokeBomb}): a thrown ball that bursts into a cloud.</li>
 * <li>B (hold), Smother ({@link SmokeSmother}): smoke wraps whoever you aim at; they're blinded, slowed and choke.</li>
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
	public static final int VAPOUR_COOLDOWN = 320;
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
		SmokeField.init();
		SmokeVapour.init();
		SmokeBomb.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SmokeScreen.clear();
			SmokeSmother.clear();
			SmokeBomb.clear();
		});
	}

	private static void forget(ServerPlayer player) {
		SmokeScreen.stop(player, false);
		SmokeSmother.stop(player, false);
		SmokeField.forget(player);
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

	/** R (Smokescreen) first, then V (Smoke Bomb), G (Vapour Form), B (Smother). */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_3, PowerKey.ABILITY_2, PowerKey.ABILITY_4};
	}

	/** A fresh awakening pours a thin Smokescreen on R. */
	@Override
	public @Nullable PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/** Nothing on Z or X yet. */
	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return false;
	}

	@Override
	public void onGained(ServerPlayer player) {
		player.setAttached(STATE, SmokeState.NONE);
	}

	@Override
	public void onLost(ServerPlayer player) {
		forget(player);
		SmokeVapour.end(player);
		player.removeAttached(STATE);
	}

	@Override
	public void tick(ServerPlayer player) {
		SmokeScreen.tick(player);
		SmokeVapour.tick(player);
		SmokeSmother.tick(player);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		switch (key) {
			case ABILITY_1 -> {
				if (down && ready(player, SmokeState.SCREEN)) {
					SmokeScreen.start(player);
				} else if (!down) {
					SmokeScreen.stop(player, true);
				}
			}
			case ABILITY_2 -> {
				if (down && ready(player, SmokeState.VAPOUR)) {
					SmokeVapour.start(player);
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
