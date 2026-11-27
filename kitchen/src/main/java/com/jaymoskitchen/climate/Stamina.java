package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Sprint stamina, after Walk Jog Run's (LGPL; its idea only, our own code). Sprinting spends your wind ({@link #MAX})
 * instead of food, and how much wind you can hold is how full you are: a full hunger bar holds all of it, half a bar half
 * of it. Run it out and you're winded: slowed for {@link #WINDED_TICKS}, no sprinting until it's back to
 * {@link #RECOVER_SHARE}. It comes back once you've stopped sprinting for {@link #REFILL_DELAY}.
 * <p>
 * Only sprinting: Jaymo's Powers' feathers are still what dodges, powers and fighting in armour spend, and its
 * overclocked speedsters run on their power ({@link #addTireless}).
 * <p>Andy (2026-11-23): adrenaline "should fill the new stamina 'wind' meter, when it depletes you lose the adrenaline".
 * Jaymo's Powers fills the wind as adrenaline kicks in ({@link #fill}) and, while it lasts, the wind burns
 * ({@link #addBurning}): it doesn't come back, and it runs down by {@link #BURN} a tick on top of what sprinting spends.
 * Run dry, you're winded as from a sprint, and Powers ends the adrenaline.
 */
public final class Stamina {
	/** A full bar. */
	public static final float MAX = 20.0F;
	/** Spent a tick sprinting (a full bar lasts twenty seconds), and refilled a tick at rest. */
	static final float SPRINT_COST = 0.05F, REFILL = 0.1F;
	/** Ticks after a sprint before it refills; ticks you're slowed when it runs out. */
	static final int REFILL_DELAY = 20, WINDED_TICKS = 60;
	/** Winded until it's refilled to this share of what you can hold. */
	static final float RECOVER_SHARE = 0.25F;
	/** Burning (adrenaline): spent a tick even at rest (a full bar lasts about 30 seconds standing, 12 sprinting). */
	static final float BURN = MAX / 600.0F;

	/** What you have, what you can hold now, and whether you're winded. */
	public record Wind(float left, float cap, boolean winded) {
		static final StreamCodec<ByteBuf, Wind> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Wind::left,
			ByteBufCodecs.FLOAT, Wind::cap,
			ByteBufCodecs.BOOL, Wind::winded,
			Wind::new);
		public static final Wind FULL = new Wind(MAX, MAX, false);
	}

	/** Synced to you for the HUD and your own client's sprint (absent = full). Not saved: you log in rested. */
	public static final AttachmentType<Wind> WIND = AttachmentRegistry.create(JaymosKitchen.id("wind"),
		builder -> builder.syncWith(Wind.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private static final class State {
		float left = MAX;
		int rested, windedFor;
		boolean winded;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Who sprints without spending wind (Jaymo's Powers' overclocked speedsters); other mods add by reflection. */
	private static final List<Predicate<Player>> TIRELESS = new CopyOnWriteArrayList<>();
	/** Whose wind burns and doesn't come back (Jaymo's Powers' adrenaline); other mods add by reflection. */
	private static final List<Predicate<Player>> BURNING = new CopyOnWriteArrayList<>();

	private Stamina() {
	}

	/** For other mods (by reflection): who runs without tiring. */
	public static void addTireless(Predicate<Player> tireless) {
		TIRELESS.add(tireless);
	}

	/** For other mods (by reflection): whose wind burns ({@link #BURN} a tick) and doesn't come back. */
	public static void addBurning(Predicate<Player> burning) {
		BURNING.add(burning);
	}

	/**
	 * For other mods (by reflection): the wind filled to all {@code player} can hold now, and no longer winded. False (and
	 * nothing done) while the stamina rule is off, when no wind is kept.
	 */
	public static boolean fill(ServerPlayer player) {
		if (!player.level().getGameRules().get(Climate.STAMINA)) {
			return false;
		}
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		state.left = MAX * player.getFoodData().getFoodLevel() / 20.0F;
		state.winded = false;
		state.windedFor = 0;
		state.rested = 0;
		// (synced now, so whoever reads it this tick sees it full)
		if (state.left >= MAX) {
			player.removeAttached(WIND);
		} else {
			player.setAttached(WIND, new Wind(state.left, state.left, false));
		}
		return true;
	}

	/** The rule switched off: no wind kept, nothing winded. */
	static void clear(ServerPlayer player) {
		STATES.remove(player.getUUID());
		player.removeAttached(WIND);
	}

	public static Wind wind(Player player) {
		return player.getAttachedOrElse(WIND, Wind.FULL);
	}

	/** Out of wind: no sprinting. Both sides. */
	public static boolean winded(Player player) {
		return wind(player).winded();
	}

	static void forget(UUID id) {
		STATES.remove(id);
	}

	private static boolean tireless(Player player) {
		return any(TIRELESS, player);
	}

	private static boolean any(List<Predicate<Player>> list, Player player) {
		for (Predicate<Player> t : list) {
			if (t.test(player)) {
				return true;
			}
		}
		return false;
	}

	/** Run dry: winded, slowed, the sprint stopped, a gasp. */
	private static void windOut(ServerPlayer player, State state) {
		state.left = 0.0F;
		state.winded = true;
		state.windedFor = WINDED_TICKS;
		player.setSprinting(false);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WINDED_TICKS, 0, false, false, true));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.9F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.stamina.winded").withStyle(ChatFormatting.GRAY));
	}

	/** Every tick, survival and adventure. */
	static void tick(ServerPlayer player) {
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		float cap = MAX * player.getFoodData().getFoodLevel() / 20.0F;
		boolean sprinting = player.isSprinting() && !player.getAbilities().mayfly && !player.isPassenger() && !tireless(player);
		// (adrenaline: it burns even at rest, and nothing comes back while it lasts)
		boolean burning = !state.winded && any(BURNING, player);
		if (sprinting || burning) {
			state.rested = 0;
			state.left -= (sprinting ? SPRINT_COST : 0.0F) + (burning ? BURN : 0.0F);
			if (state.left <= 0.0F) {
				windOut(player, state);
			}
		} else if (state.windedFor > 0) {
			state.windedFor--;
		} else if (++state.rested >= REFILL_DELAY) {
			state.left += REFILL;
		}
		state.left = Math.min(state.left, cap);
		if (state.winded && state.windedFor <= 0 && state.left >= cap * RECOVER_SHARE && cap > 0.0F) {
			state.winded = false;
		}
		Wind now = new Wind(state.left, cap, state.winded);
		Wind was = wind(player);
		if (state.left >= MAX && !state.winded) {
			player.removeAttached(WIND);
		} else if (Math.abs(was.left() - now.left()) >= 0.25F || was.cap() != now.cap() || was.winded() != now.winded()
			|| now.left() >= cap != was.left() >= was.cap()) {
			player.setAttached(WIND, now);
		}
	}
}
