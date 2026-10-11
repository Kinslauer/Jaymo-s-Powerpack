package com.jaymospowers.power.resonance;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Z, Throw Voice: tap to plant a sound at the block you look at (within {@code RANGE}). Players hear it from there; it
 * makes a vibration there (sculk and Wardens react), and creatures within {@code LURE_RADIUS} that aren't after anyone
 * walk over to it for {@code LURE_TICKS}. Hold Z and scroll to pick the sound: a footstep, a zombie's groan, a door, your
 * own voice.
 */
final class ResonanceVoice {
	static final double RANGE = 32.0;
	static final double LURE_RADIUS = 16.0;
	static final int LURE_TICKS = 120;
	static final int LURE_REPATH = 10;
	static final double LURE_SPEED = 1.0;

	/** The sounds, in scroll order. */
	enum Sound {
		FOOTSTEP(SoundEvents.STONE_STEP, SoundSource.PLAYERS, GameEvent.STEP, 1.0F),
		GROAN(SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, GameEvent.ENTITY_ACTION, 1.0F),
		DOOR(SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, GameEvent.BLOCK_OPEN, 1.0F),
		VOICE(SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, GameEvent.ENTITY_ACTION, 0.8F);

		final SoundEvent sound;
		final SoundSource source;
		final Holder<GameEvent> event;
		final float pitch;

		Sound(SoundEvent sound, SoundSource source, Holder<GameEvent> event, float pitch) {
			this.sound = sound;
			this.source = source;
			this.event = event;
			this.pitch = pitch;
		}

		static Sound of(int index) {
			Sound[] all = values();
			return all[Math.floorMod(index, all.length)];
		}

		String langKey() {
			return "message.jaymospowers.resonance_voice_" + name().toLowerCase();
		}
	}

	private record Lure(Mob mob, Vec3 spot, long until) {
	}

	/** Z held: true once it's been scrolled (so letting go throws nothing). */
	private static final Map<UUID, Boolean> HOLDING = new HashMap<>();
	private static final List<Lure> LURES = new ArrayList<>();

	private ResonanceVoice() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ResonanceVoice::tickLures);
	}

	static void mode(ServerPlayer player, boolean down) {
		if (down) {
			HOLDING.put(player.getUUID(), false);
			return;
		}
		Boolean scrolled = HOLDING.remove(player.getUUID());
		if (scrolled == null || scrolled || !ResonancePower.ready(player, ResonanceState.VOICE)) {
			return;
		}
		if (throwVoice(player)) {
			ResonancePower.cooldown(player, ResonanceState.VOICE, ResonancePower.VOICE_COOLDOWN);
			Mastery.used(player);
		}
	}

	/** Scrolled while holding Z: the next (or previous) sound. */
	static void cycle(ServerPlayer player, int steps) {
		if (!Powers.has(player, ResonancePower.INSTANCE) || !ResonancePower.INSTANCE.slotUnlocked(player, "mode") || steps == 0) {
			return;
		}
		if (HOLDING.containsKey(player.getUUID())) {
			HOLDING.put(player.getUUID(), true);
		}
		ResonanceState state = ResonancePower.state(player);
		int next = Math.floorMod(state.sound() + Integer.signum(steps), Sound.values().length);
		ResonancePower.set(player, state.withSound(next));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_voice_pick", Component.translatable(Sound.of(next).langKey()))
			.withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static boolean throwVoice(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1.0F).scale(RANGE)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.resonance_voice_nowhere").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Vec3 spot = Vec3.atCenterOf(hit.getBlockPos().relative(hit.getDirection()));
		Sound sound = Sound.of(ResonancePower.state(player).sound());
		float pitch = sound.pitch * (0.9F + level.getRandom().nextFloat() * 0.2F);
		level.playSound(null, spot.x, spot.y, spot.z, sound.sound, sound.source, 1.0F, pitch);
		// a vibration with no one behind it: sculk and Wardens turn to the spot, not to you
		level.gameEvent(sound.event, spot, new GameEvent.Context(null, null));
		// only the thrower sees where it landed
		level.sendParticles(player, ParticleTypes.NOTE, true, false, spot.x, spot.y + 0.3, spot.z, 1, 0.0, 0.0, 0.0, 0.5);
		long until = level.getGameTime() + LURE_TICKS;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(spot, spot).inflate(LURE_RADIUS),
			m -> m.isAlive() && m.getTarget() == null && !m.isNoAi() && m.distanceToSqr(spot) <= LURE_RADIUS * LURE_RADIUS)) {
			LURES.removeIf(lure -> lure.mob == mob);
			LURES.add(new Lure(mob, spot, until));
			mob.getNavigation().moveTo(spot.x, spot.y, spot.z, LURE_SPEED);
		}
		return true;
	}

	/** Lured creatures keep heading for the sound until they get there, find a target, or lose interest. */
	private static void tickLures(MinecraftServer server) {
		for (Iterator<Lure> it = LURES.iterator(); it.hasNext();) {
			Lure lure = it.next();
			Mob mob = lure.mob;
			long now = mob.level().getGameTime();
			if (!mob.isAlive() || mob.isRemoved() || mob.getTarget() != null || now >= lure.until
				|| mob.distanceToSqr(lure.spot) < 2.0 * 2.0) {
				it.remove();
				continue;
			}
			if (now % LURE_REPATH == 0) {
				mob.getNavigation().moveTo(lure.spot.x, lure.spot.y, lure.spot.z, LURE_SPEED);
				// they look where they heard it
				mob.getLookControl().setLookAt(lure.spot.x, lure.spot.y, lure.spot.z, 30.0F, Mth.clamp(mob.getMaxHeadXRot(), 1, 90));
			}
		}
	}

	static void forget(ServerPlayer player) {
		HOLDING.remove(player.getUUID());
	}

	static void clear() {
		HOLDING.clear();
		LURES.clear();
	}
}
