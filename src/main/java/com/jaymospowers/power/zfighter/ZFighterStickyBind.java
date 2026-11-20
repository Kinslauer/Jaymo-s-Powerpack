package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Soundscape;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sticky Bind: a Rubber-folk wad that sticks a foe; mash to tear free (the out-cold mash).
 * Quick technique on G ({@link ZFighterTraining#STICKY_BIND}).
 * Throws a sticky wad that attaches to a target, immobilizing them until they mash jump/sneak.
 * Only usable by Rubber-folk Z Fighters.
 */
public final class ZFighterStickyBind {
	/** Ki cost. */
	static final float KI_COST = 18.0F;
	/** Cooldown (ticks). */
	static final int COOLDOWN = 140;
	/** Speed (blocks/tick). */
	static final double SPEED = 1.2;
	/** Max range (blocks). */
	static final double RANGE = 20.0;
	/** Wad size (blocks). */
	static final double WAD_SIZE = 0.5;
	/** Base duration (ticks) before it falls off. */
	static final int BASE_DURATION = 200;
	/** Mash reduction per jump/sneak press (ticks). */
	static final int MASH_REDUCTION = 8;
	/** Color (Rubber-folk green). */
	static final int COLOR = 0x4AFF4A;

	/** A stuck wad on a target. */
	private static class Wad {
		final ServerLevel level;
		final UUID target;
		final UUID owner;
		int ticksLeft;
		Wad(ServerLevel level, UUID target, UUID owner, int ticksLeft) {
			this.level = level;
			this.target = target;
			this.owner = owner;
			this.ticksLeft = ticksLeft;
		}
	}

	/** Active wads by target UUID. */
	private static final Map<UUID, Wad> WADS = new HashMap<>();

	/** Attachment on the stuck target: how long they're stuck (ticks). */
	public static final AttachmentType<Integer> STUCK = AttachmentRegistry.create(JaymosPowers.id("zfighter_stuck"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Attachment on the stuck target: previous key state for detecting presses (sneak|jump bits). */
	public static final AttachmentType<Integer> MASH_STATE = AttachmentRegistry.create(JaymosPowers.id("zfighter_mash_state"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private ZFighterStickyBind() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterStickyBind::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WADS.clear());
	}

	static void clear() {
		WADS.clear();
	}

	/** G: throw the sticky wad. Returns cooldown, or 0 if it couldn't start. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		// Only Rubber-folk can use Sticky Bind
		if (!ZFighterRubberBody.is(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_sticky_bind.rubber_only").withStyle(net.minecraft.ChatFormatting.GRAY));
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		LivingEntity target = ZFighterArts.find(player, RANGE);
		if (target == null) {
			ZFighterArts.noFoe(player);
			return 0;
		}
		// Spend ki only after confirming a valid target
		if (!ZFighterPower.spend(player, live, KI_COST)) {
			return 0;
		}
		// Throw the wad as a projectile
		throwWad(player, live, target);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 1.2F);
		return COOLDOWN;
	}

	/** Throw a wad at the target. */
	private static void throwWad(ServerPlayer player, ZFighterPower.Live live, LivingEntity target) {
		ServerLevel level = player.level();
		Vec3 from = ZFighterHands.palm(player, LockOn.aim(player), false);
		Vec3 to = target.getBoundingBox().getCenter();
		Vec3 dir = to.subtract(from).normalize();
		// Simple projectile: we'll just apply it immediately for simplicity, or track it
		// For now, apply immediately with a travel time based on distance
		double dist = player.distanceTo(target);
		int travelTicks = (int)Math.ceil(dist / SPEED);
		// Schedule the stick
		ZFighterHands.later(player, travelTicks, () -> {
			if (target.isAlive() && target.level() == level) {
				stick(target, player, level);
			}
		});
		// Visual: wad flying
		for (int i = 0; i < travelTicks; i++) {
			final int tick = i;
			ZFighterHands.later(player, tick, () -> {
				if (target.isAlive()) {
					Vec3 at = from.add(dir.scale(SPEED * tick));
					level.sendParticles(MagicFx.wisp(COLOR, 1.0F), true, false, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
				}
			});
		}
	}

	/** Apply the sticky bind to the target. */
	private static void stick(LivingEntity target, ServerPlayer owner, ServerLevel level) {
		if (!ZFighterArts.valid(owner, target)) {
			return;
		}
		int duration = BASE_DURATION;
		target.setAttached(STUCK, duration);
		target.setAttached(MASH_STATE, 0); // initialize mash state
		WADS.put(target.getUUID(), new Wad(level, target.getUUID(), owner.getUUID(), duration));
		// Immobilize
		target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, duration, 10, false, false));
		if (target instanceof net.minecraft.world.entity.Mob m) m.getNavigation().stop();
		level.sendParticles(MagicFx.wisp(COLOR, 1.5F), true, false, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
			20, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 1.5F);
		if (target instanceof ServerPlayer sp) {
			sp.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_sticky_bind.stuck").withStyle(net.minecraft.ChatFormatting.YELLOW));
		}
	}

	/** Called when the target presses jump or sneak (mashing to escape). Detects key presses (edges), not holds. */
	public static void mash(ServerPlayer target) {
		Integer stuck = target.getAttached(STUCK);
		if (stuck == null || stuck <= 0) {
			return;
		}
		// Detect key presses: sneak (bit 0) and jump (bit 1)
		int prev = target.getAttachedOrElse(MASH_STATE, 0);
		int curr = 0;
		if (target.isShiftKeyDown()) curr |= 1;
		if (target.getLastClientInput() != null && target.getLastClientInput().jump()) curr |= 2;
		int pressed = curr & ~prev; // edges: newly pressed this tick
		target.setAttached(MASH_STATE, curr);
		if (pressed != 0) {
			target.setAttached(STUCK, Math.max(0, stuck - MASH_REDUCTION));
			ServerLevel level = target.level();
			level.sendParticles(ParticleTypes.CRIT, true, false, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
				4, 0.2, 0.2, 0.2, 0.1);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.5F, 1.8F);
			target.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_sticky_bind.mash", (Math.max(0, stuck - MASH_REDUCTION) + 19) / 20).withStyle(net.minecraft.ChatFormatting.GREEN));
		}
	}

	/** Server tick: count down stuck duration and check for mash presses. */
	private static void tick(MinecraftServer server) {
		if (WADS.isEmpty()) {
			return;
		}
		for (java.util.Iterator<Map.Entry<UUID, Wad>> it = WADS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Wad> entry = it.next();
			Wad wad = entry.getValue();
			var entity = wad.level.getEntity(entry.getKey());
			LivingEntity target = entity instanceof LivingEntity l ? l : null;
			if (target == null || !target.isAlive() || target.level() != wad.level) {
				it.remove();
				continue;
			}
			Integer stuck = target.getAttached(STUCK);
			if (stuck == null || stuck <= 0) {
				it.remove();
				target.removeAttached(STUCK);
				target.removeAttached(MASH_STATE);
				target.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
				if (target instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_sticky_bind.free").withStyle(net.minecraft.ChatFormatting.GOLD));
					sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 0.7F, 1.6F);
				}
				continue;
			}
			target.setAttached(STUCK, stuck - 1);
			// Check for mash presses (sneak or jump) for players
			if (target instanceof ServerPlayer sp) {
				mash(sp);
			}
			// Particles while stuck
			if (server.getTickCount() % 10 == 0) {
				wad.level.sendParticles(MagicFx.wisp(COLOR, 0.8F), true, false, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					4, 0.2, 0.2, 0.2, 0.02);
			}
		}
	}

	/** Whether {@code target} is currently stuck. */
	public static boolean isStuck(LivingEntity target) {
		return target.getAttached(STUCK) != null && target.getAttached(STUCK) > 0;
	}
}