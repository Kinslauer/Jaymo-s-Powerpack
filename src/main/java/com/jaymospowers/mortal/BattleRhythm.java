package com.jaymospowers.mortal;

import com.jaymospowers.core.TreeLocks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Battle rhythm: keep landing melee blows without a long gap and your attacks quicken, {@link #PER_STACK} a step up to
 * {@link #MAX_STACKS} steps (a step at most every {@link #GAIN_GAP} ticks). Stop for {@link #HOLD_TICKS} ticks and the steps
 * fade one by one, {@link #FADE_EVERY} ticks apart. Learned in Jaymo's Kingdoms' skill tree (Might's Battle Rhythm,
 * {@link TreeLocks#BATTLE_RHYTHM}), so it is for fighters; the speed is a transient attack speed modifier.
 */
public final class BattleRhythm {
	public static final int MAX_STACKS = 5, GAIN_GAP = 8, HOLD_TICKS = 80, FADE_EVERY = 30;
	public static final double PER_STACK = 0.04;

	private static final Identifier ID = Identifier.fromNamespaceAndPath("jaymospowers", "battle_rhythm");

	private static final class State {
		ServerPlayer owner;
		int stacks;
		long lastHit, lastGain, lastFade;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private BattleRhythm() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer player
				&& player != victim && TreeLocks.has(player, TreeLocks.BATTLE_RHYTHM)) {
				landed(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				State state = STATES.get(player.getUUID());
				if (state != null) {
					fade(player, state);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
	}

	/** The steps {@code player} has now. */
	public static int stacks(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state == null || state.owner != player ? 0 : state.stacks;
	}

	private static State state(ServerPlayer player) {
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		if (state.owner != player) {
			// a new body (a respawn): its attributes start clean
			state.owner = player;
			state.stacks = 0;
		}
		return state;
	}

	private static void landed(ServerPlayer player) {
		State state = state(player);
		long now = player.level().getGameTime();
		state.lastHit = now;
		if (state.stacks < MAX_STACKS && now - state.lastGain >= GAIN_GAP) {
			state.lastGain = now;
			state.stacks++;
			apply(player, state.stacks);
		}
	}

	private static void fade(ServerPlayer player, State state) {
		if (state.owner != player || state.stacks <= 0) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - state.lastHit > HOLD_TICKS && now - state.lastFade >= FADE_EVERY) {
			state.lastFade = now;
			state.stacks--;
			apply(player, state.stacks);
		}
	}

	private static void apply(ServerPlayer player, int stacks) {
		AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (speed == null) {
			return;
		}
		if (stacks <= 0) {
			speed.removeModifier(ID);
		} else {
			speed.removeModifier(ID);
			speed.addTransientModifier(new AttributeModifier(ID, PER_STACK * stacks, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}
}
