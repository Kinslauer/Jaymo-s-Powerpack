package com.jaymospowers.power.resonance;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Resonance: sound and the Deep Dark's sculk. Everything is decided here, on the server.
 * <ul>
 * <li>R, Pulse ({@link ResonancePulse}): a cone of sound that hurts, knocks back and interrupts, and shatters panes.</li>
 * <li>G (hold), Shatter ({@link ResonanceShatter}): hum at a block until it breaks, or at a creature's armour.</li>
 * <li>V, Echolocation ({@link ResonanceEcho}): every creature in 32 blocks outlined, for you alone, for 4 s.</li>
 * <li>B, Hush ({@link ResonanceHush}): a dome of silence that switches off other players' power keys.</li>
 * <li>Z, Throw Voice ({@link ResonanceVoice}); X (hold + scroll), Tune; C, Sonic Boom or Shriek ({@link ResonanceBoom}).</li>
 * </ul>
 * The starter is passive (the heartbeat ring, drawn by the client). Fate: {@link ResonanceFate}. Items:
 * {@link TuningForkItem}.
 */
public final class ResonancePower implements Power {
	public static final ResonancePower INSTANCE = new ResonancePower();
	public static final Identifier ID = JaymosPowers.id("resonance");
	public static final int COLOR = 0xB48CF0;

	// ---- tuning (ticks) ----
	public static final int PULSE_COOLDOWN = 80;
	public static final int SHATTER_COOLDOWN = 200;
	public static final int ECHO_COOLDOWN = 160;
	public static final int HUSH_COOLDOWN = 800;
	public static final int VOICE_COOLDOWN = 60;
	public static final int BOOM_COOLDOWN = 600;
	public static final int SHRIEK_COOLDOWN = 1200;
	/** Tune's range: -2 (lowest) to +2 (highest); 0 is your natural pitch. */
	public static final int TUNE_MIN = -2;
	public static final int TUNE_MAX = 2;
	/** The starter's heartbeat ring hears creatures this close (blocks; the client draws it). */
	public static final double HEARTBEAT_RADIUS = 12.0;
	/** Mastery needed for Z (Throw Voice) and X (Tune): abilities unlocked. */
	static final int VOICE_UNLOCK = 2;
	static final int TUNE_UNLOCK = 3;

	/** The owner's HUD state (cooldowns, the sound and pitch picked). Not saved: a relog clears cooldowns. */
	public static final AttachmentType<ResonanceState> STATE = AttachmentRegistry.create(JaymosPowers.id("resonance_state"),
		builder -> builder.syncWith(ResonanceState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private ResonancePower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}. */
	public static void init() {
		Powers.register(INSTANCE);
		ResonanceNet.register();
		TuningForkItem.init();
		ResonanceHush.init();
		ResonanceEcho.init();
		ResonanceVoice.init();
		ResonanceFate.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ResonanceDebugCommand.register(dispatcher));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ResonanceShatter.clear();
			ResonanceBoom.clear();
			ResonanceVoice.clear();
			ResonanceHush.clear();
			ResonanceFate.clear();
			ResonanceEcho.clear();
		});
	}

	private static void forget(ServerPlayer player) {
		ResonanceShatter.stop(player, false);
		ResonanceBoom.forget(player);
		ResonanceVoice.forget(player);
		ResonanceFate.forget(player);
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
		return new ItemStack(TuningForkItem.TUNING_FORK);
	}

	/** V (Echolocation) first, then R (Pulse), G (Shatter), B (Hush). */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_3, PowerKey.ABILITY_1, PowerKey.ABILITY_2, PowerKey.ABILITY_4};
	}

	/** Shrieker (corrupted) shrieks on C. */
	@Override
	public boolean ultimateWhenCorrupted() {
		return true;
	}

	@Override
	public boolean slotUnlocked(Player player, String slot) {
		return switch (slot) {
			case "mode" -> Mastery.unlocked(player) >= VOICE_UNLOCK;
			case "special" -> Mastery.unlocked(player) >= TUNE_UNLOCK;
			default -> true;
		};
	}

	@Override
	public void onGained(ServerPlayer player) {
		player.setAttached(STATE, ResonanceState.NONE);
	}

	/** (0.15.31) Downed: what was running ends. */
	@Override
	public void onDowned(ServerPlayer player) {
		// a hum in progress ends without its last note
		ResonanceShatter.stop(player, false);
	}

	@Override
	public void onLost(ServerPlayer player) {
		forget(player);
		ResonanceHush.end(player);
		player.removeAttached(STATE);
	}

	@Override
	public void tick(ServerPlayer player) {
		ResonanceShatter.tick(player);
		ResonanceBoom.tick(player);
		ResonanceFate.tick(player);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_2) {
			if (down && ready(player, ResonanceState.SHATTER)) {
				ResonanceShatter.start(player);
			} else if (!down) {
				ResonanceShatter.stop(player, true);
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, ResonanceState.PULSE) && ResonancePulse.fire(player)) {
					cooldown(player, ResonanceState.PULSE, PULSE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, ResonanceState.ECHO) && ResonanceEcho.ping(player)) {
					cooldown(player, ResonanceState.ECHO, ECHO_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, ResonanceState.HUSH) && ResonanceHush.cast(player)) {
					cooldown(player, ResonanceState.HUSH, HUSH_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Z: Throw Voice (held and scrolled: pick the sound). */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (slotUnlocked(player, "mode")) {
			ResonanceVoice.mode(player, down);
		}
	}

	/** C: Sonic Boom (ascended), or Shriek (corrupted; when both, sneak for Shriek). */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (!down || !ready(player, ResonanceState.ULTIMATE)) {
			return;
		}
		boolean shriek = Fate.isCorrupted(player) && (!Fate.isAscended(player) || player.isShiftKeyDown());
		if (shriek) {
			if (ResonanceBoom.shriek(player)) {
				cooldown(player, ResonanceState.ULTIMATE, SHRIEK_COOLDOWN);
			}
		} else if (ResonanceBoom.charge(player)) {
			cooldown(player, ResonanceState.ULTIMATE, BOOM_COOLDOWN);
		}
	}

	// ---------------------------------------------------------------- state

	public static ResonanceState state(Player player) {
		ResonanceState state = player.getAttached(STATE);
		return state == null ? ResonanceState.NONE : state;
	}

	static void set(ServerPlayer player, ResonanceState state) {
		player.setAttached(STATE, state);
	}

	static boolean ready(ServerPlayer player, int slot) {
		return state(player).left(slot, player.level().getGameTime()) <= 0;
	}

	static void cooldown(ServerPlayer player, int slot, int ticks) {
		set(player, state(player).withCooldown(slot, player.level().getGameTime(), ticks));
	}

	/** Every ability unlocked: Pulse reaches further. */
	static boolean mastered(Player player) {
		return Mastery.unlocked(player) >= Mastery.ALL;
	}

	/** The pitch tuned with X: -2 (low) to +2 (high). */
	static int tune(Player player) {
		return Mth.clamp(state(player).tune(), TUNE_MIN, TUNE_MAX);
	}

	/** X held and scrolled: the pitch moves by {@code steps} (0 sets it back to the middle). */
	static void tune(ServerPlayer player, int steps) {
		if (!Powers.has(player, INSTANCE) || !INSTANCE.slotUnlocked(player, "special")) {
			return;
		}
		int now = tune(player);
		int next = steps == 0 ? 0 : Mth.clamp(now + Integer.signum(steps), TUNE_MIN, TUNE_MAX);
		if (next == now) {
			return;
		}
		set(player, state(player).withTune(next));
		String key = next < 0 ? "low" : next > 0 ? "high" : "middle";
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_tune_" + key, (next > 0 ? "+" : "") + next)
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F,
			pitch(next));
	}

	/** The sound pitch for a tuning (0.5 low to 2.0 high). */
	static float pitch(int tune) {
		return (float)Math.pow(2.0, tune / 2.0);
	}
}
