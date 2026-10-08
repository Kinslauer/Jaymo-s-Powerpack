package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chronokinesis: golden clockwork time magic. Everything is decided here, on the server.
 * <ul>
 * <li>R, Rewind: snap back to where you were 3 s ago (health restored if it was higher, fire put out).</li>
 * <li>G, Stasis Field: stop time for mobs and projectiles in a 6-block bubble for 4 s.</li>
 * <li>V, Overclock: 6 s of Speed III, Haste II and faster attacks.</li>
 * <li>B, Time Bloom: 5 s of fast-forward for crops, saplings, furnaces, smokers and ovens within 5 blocks.</li>
 * </ul>
 * The HUD is fed by {@link ChronoSync}.
 */
public final class ChronoPower implements Power {
	public static final ChronoPower INSTANCE = new ChronoPower();
	public static final Identifier ID = JaymosPowers.id("chrono");

	// ---- tuning (ticks) ----
	public static final int REWIND_TICKS = 60;
	/** Rewind needs at least this much history (0.5 s), so it can't be spammed right after a teleport or respawn. */
	static final int REWIND_MIN_HISTORY = 10;
	static final int REWIND_COOLDOWN = 300;
	static final int STASIS_COOLDOWN = 600;
	static final int OVERCLOCK_TICKS = 120;
	static final int OVERCLOCK_COOLDOWN = 500;
	/** Attack speed while overclocked: x1.6 of the total (a sword goes from 1.6 to ~2.56 swings a second). */
	static final double OVERCLOCK_ATTACK_SPEED = 0.6;
	static final int BLOOM_COOLDOWN = 800;

	static final int REWIND = 0;
	static final int STASIS = 1;
	static final int OVERCLOCK = 2;
	static final int BLOOM = 3;
	private static final int[] COOLDOWNS = {REWIND_COOLDOWN, STASIS_COOLDOWN, OVERCLOCK_COOLDOWN, BLOOM_COOLDOWN};

	static final Identifier OVERCLOCK_MODIFIER = JaymosPowers.id("chrono_overclock");
	/** Resend the HUD state this often while anything is counting, to correct drift. */
	private static final int SYNC_INTERVAL = 20;

	private static final Map<UUID, ChronoState> STATES = new HashMap<>();

	private ChronoPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		Powers.register(INSTANCE);
		PayloadTypeRegistry.clientboundPlay().register(ChronoSync.TYPE, ChronoSync.CODEC);
		StasisField.registerLocks();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ChronoState state = STATES.get(handler.player.getUUID());
			if (state != null) {
				stopAll(handler.player, state);
				state.clearHistory();
				state.synced = false;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ChronoState state : STATES.values()) {
				if (state.stasis != null) {
					state.stasis.end();
					state.stasis = null;
				}
			}
			StasisField.releaseAll();
			STATES.clear();
		});
	}

	@Override
	public Identifier id() {
		return ID;
	}

	/** Rewind first; Stasis Field, which stops other players too, comes last. */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_3, PowerKey.ABILITY_4, PowerKey.ABILITY_2};
	}

	@Override
	public int color() {
		return 0xF2C14E;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.CLOCK);
	}

	private static ChronoState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), uuid -> new ChronoState());
	}

	private static int now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}

	@Override
	public void onGained(ServerPlayer player) {
		ChronoState state = state(player);
		state.clearHistory();
		state.synced = false;
	}

	@Override
	public void onLost(ServerPlayer player) {
		ChronoState state = STATES.get(player.getUUID());
		if (state != null) {
			if (state.overclockLeft > 0) {
				removeOverclockEffect(player, MobEffects.SPEED, 2, state.overclockLeft);
				removeOverclockEffect(player, MobEffects.HASTE, 1, state.overclockLeft);
			}
			stopAll(player, state);
			STATES.remove(player.getUUID());
		}
		// clear the HUD state on the client
		if (ServerPlayNetworking.canSend(player, ChronoSync.TYPE)) {
			int[] zero = new int[ChronoSync.ABILITIES];
			ServerPlayNetworking.send(player, new ChronoSync(zero, zero, zero, zero));
		}
	}

	/** Ends everything running (stasis, bloom, overclock, trail). Cooldowns are kept. */
	private static void stopAll(ServerPlayer player, ChronoState state) {
		if (state.stasis != null) {
			state.stasis.end();
			state.stasis = null;
		}
		state.bloom = null;
		state.trail = null;
		state.overclockLeft = 0;
		setOverclockModifier(player, false);
		state.synced = false;
	}

	@Override
	public void tick(ServerPlayer player) {
		ChronoState state = state(player);
		ServerLevel level = player.level();
		int now = now(player);

		if (!player.isAlive() || player.isSpectator()) {
			if (state.stasis != null || state.bloom != null || state.overclockLeft > 0) {
				stopAll(player, state);
			}
			state.clearHistory();
			sync(player, state, now);
			return;
		}

		state.record(player.position(), player.getYRot(), player.getXRot(), player.getHealth(), level.dimension());

		if (state.trail != null && !state.trail.tick()) {
			state.trail = null;
		}
		if (state.stasis != null && (state.stasis.level != level || !state.stasis.tick())) {
			if (state.stasis.ticksLeft > 0) {
				state.stasis.end(); // left the dimension
			}
			state.stasis = null;
			state.synced = false;
		}
		if (state.bloom != null && (state.bloom.level != level || !state.bloom.tick())) {
			state.bloom = null;
			state.synced = false;
		}
		if (state.overclockLeft > 0) {
			state.overclockLeft--;
			overclockFx(player, level, state.overclockLeft);
			if (state.overclockLeft == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.7F);
				state.synced = false;
			}
		}
		setOverclockModifier(player, state.overclockLeft > 0);
		sync(player, state, now);
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ChronoState state = state(player);
		int ability = key.ordinal();
		int now = now(player);
		if (state.cooldownLeft(ability, now) > 0) {
			return; // the client plays the "not ready" feedback itself
		}
		boolean used = switch (ability) {
			case REWIND -> rewind(player, state);
			case STASIS -> stasis(player, state);
			case OVERCLOCK -> overclock(player, state);
			case BLOOM -> bloom(player, state);
			default -> false;
		};
		if (used) {
			state.readyAt[ability] = now + COOLDOWNS[ability];
			state.cooldownTotal[ability] = COOLDOWNS[ability];
			state.synced = false;
			sync(player, state, now);
		}
	}

	// ---------------------------------------------------------------- Rewind

	private static boolean rewind(ServerPlayer player, ChronoState state) {
		ServerLevel level = player.level();
		if (state.size() < REWIND_MIN_HISTORY) {
			return fail(player, "message.jaymospowers.chrono_rewind_short");
		}
		if (state.spansOtherDimension(level.dimension())) {
			return fail(player, "message.jaymospowers.chrono_rewind_tangled");
		}
		// the oldest recorded spot you'd fit into again (blocks may have been placed there since)
		int chosen = -1;
		Vec3 here = player.position();
		for (int age = state.size() - 1; age >= REWIND_MIN_HISTORY - 1; age--) {
			AABB box = player.getBoundingBox().move(state.pos(age).subtract(here));
			if (level.noCollision(player, box)) {
				chosen = age;
				break;
			}
		}
		if (chosen < 0) {
			return fail(player, "message.jaymospowers.chrono_rewind_blocked");
		}
		Vec3 target = state.pos(chosen);
		float pastHealth = state.health(chosen);
		var path = state.path(chosen);

		level.playSound(null, here.x, here.y, here.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, here.x, here.y + 1.0, here.z, 16, 0.3, 0.6, 0.3, 0.05);

		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.teleportTo(level, target.x, target.y, target.z, Set.of(), state.yRot(chosen), state.xRot(chosen), false);
		player.resetFallDistance();
		player.extinguishFire();
		if (pastHealth > player.getHealth()) {
			player.setHealth(Math.min(pastHealth, player.getMaxHealth()));
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.x, target.y + 1.0, target.z, 6, 0.3, 0.5, 0.3, 0.0);
		}
		level.playSound(null, target.x, target.y, target.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.4F);

		state.clearHistory(); // the jump itself shouldn't be part of the next rewind
		state.trail = new RewindTrail(level, path);
		return true;
	}

	private static boolean fail(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.6F, 0.6F);
		return false;
	}

	// ---------------------------------------------------------------- Stasis

	private static boolean stasis(ServerPlayer player, ChronoState state) {
		if (state.stasis != null) {
			state.stasis.end();
		}
		state.stasis = new StasisField(player.level(), player.position(), player.getUUID());
		state.stasis.start();
		return true;
	}

	// ---------------------------------------------------------------- Overclock

	private static boolean overclock(ServerPlayer player, ChronoState state) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, OVERCLOCK_TICKS, 2, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, OVERCLOCK_TICKS, 1, false, false, true));
		state.overclockLeft = OVERCLOCK_TICKS;
		setOverclockModifier(player, true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 2.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
		level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.7, 0.4, 0.6);
		return true;
	}

	private static void setOverclockModifier(ServerPlayer player, boolean on) {
		AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed == null || attackSpeed.hasModifier(OVERCLOCK_MODIFIER) == on) {
			return;
		}
		if (on) {
			// transient: never saved, so a crash or relog can't leave it behind
			attackSpeed.addTransientModifier(new AttributeModifier(OVERCLOCK_MODIFIER, OVERCLOCK_ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			attackSpeed.removeModifier(OVERCLOCK_MODIFIER);
		}
	}

	/** On losing the power mid-Overclock: take back our effects (only if they still look like ours). */
	private static void removeOverclockEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int left) {
		MobEffectInstance instance = player.getEffect(effect);
		if (instance != null && instance.getAmplifier() == amplifier && instance.getDuration() <= left + 1) {
			player.removeEffect(effect);
		}
	}

	private static void overclockFx(ServerPlayer player, ServerLevel level, int left) {
		if (left % 2 != 0) {
			return;
		}
		double a = left * 0.55;
		double r = 0.55;
		level.sendParticles(ParticleTypes.WAX_ON, player.getX() + Math.cos(a) * r, player.getY() + 0.3 + (left % 10) * 0.15,
			player.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		if (left % 6 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.25, 0.1, 0.25, 0.02);
		}
		if (left % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.35F, 2.0F);
		}
	}

	// ---------------------------------------------------------------- Time Bloom

	private static boolean bloom(ServerPlayer player, ChronoState state) {
		state.bloom = new TimeBloom(player.level(), player.blockPosition());
		state.bloom.start();
		return true;
	}

	// ---------------------------------------------------------------- HUD sync

	private static void sync(ServerPlayer player, ChronoState state, int now) {
		boolean due = !state.synced || (now - state.lastSync >= SYNC_INTERVAL && state.anythingTicking(now));
		if (!due || !ServerPlayNetworking.canSend(player, ChronoSync.TYPE)) {
			return;
		}
		int[] cooldownLeft = new int[ChronoSync.ABILITIES];
		int[] activeLeft = new int[ChronoSync.ABILITIES];
		int[] activeTotal = new int[ChronoSync.ABILITIES];
		for (int i = 0; i < ChronoSync.ABILITIES; i++) {
			cooldownLeft[i] = state.cooldownLeft(i, now);
		}
		if (state.stasis != null) {
			activeLeft[STASIS] = state.stasis.ticksLeft;
			activeTotal[STASIS] = StasisField.DURATION;
		}
		if (state.overclockLeft > 0) {
			activeLeft[OVERCLOCK] = state.overclockLeft;
			activeTotal[OVERCLOCK] = OVERCLOCK_TICKS;
		}
		if (state.bloom != null) {
			activeLeft[BLOOM] = state.bloom.ticksLeft;
			activeTotal[BLOOM] = TimeBloom.DURATION;
		}
		ServerPlayNetworking.send(player, new ChronoSync(cooldownLeft, state.cooldownTotal.clone(), activeLeft, activeTotal));
		state.synced = true;
		state.lastSync = now;
	}
}
