package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.power.wind.AirlessDome;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The stretching grab (Andy's Awakening pick: "Rubber-folk", "and Verdant"). A Rubber-folk's or a Verdant's arm shoots out up to
 * {@link #REACH} blocks to the foe they look toward, bending round whatever is in the way (over it or round its side), hooks
 * it, and yanks it to their feet. Whatever it was winding up is cut off: a drawn bow or a raised shield lowered, a creeper's
 * fuse, an illager's spell, its steps; it reels a moment and its poise is worn.
 * <ul>
 * <li>A Z Fighter (Rubber-folk, or of the Verdant lineage): sprint + R.</li>
 * <li>Anyone of Jaymo's Kingdoms' Rubber-folk or Verdant heritage: the heritage key while sprinting (Kingdoms calls
 * {@link #reel} by reflection).</li>
 * </ul>
 * Everyone sees the arm go out (the Verdant combo's stretch, {@link ZFighterStretchCombo#REACH}).
 */
public final class ZFighterReel {
	// ---- tuning ----
	private static final String TABLE = "zfighter_reel";
	/** How far the arm goes (blocks), how wide round the look it finds a foe (cosine), the wait after (ticks). */
	static final double REACH = 8.0, CONE = 0.82;
	static final int COOLDOWN = 80;
	/** The arm's whole stretch (ticks); it hooks at the half. */
	static final int TICKS = 14;
	/** How long it reels after (ticks). */
	static final int REEL_TICKS = 20;

	private record Pull(LivingEntity foe, @Nullable Vec3 bend, long start) {
	}

	private static final Map<UUID, Pull> PULLS = new HashMap<>();

	private ZFighterReel() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterReel::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PULLS.clear());
	}

	/** A Rubber-folk or Verdant Z Fighter (not as the Great Ape). */
	static boolean stretchy(ServerPlayer player) {
		return (ZFighterRubberBody.is(player) || ZFighterLineage.of(player) == ZFighterLineage.VERDANT) && !ZFighterLineage.ape(player);
	}

	/** The arm goes out at the foe looked toward. True if it went (or was cooling: the key is spent either way). */
	public static boolean reel(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || PULLS.containsKey(player.getUUID())) {
			return false;
		}
		if (!PickKit.ready(TABLE, player, 0) && !player.isCreative()) {
			return true;
		}
		LivingEntity foe = aimed(player);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter.reel_none").withStyle(ChatFormatting.GRAY));
			return true;
		}
		Vec3 to = foe.getBoundingBox().getCenter();
		Vec3 bend = null;
		if (!clear(player, player.getEyePosition(), to)) {
			bend = bend(player, to);
			if (bend == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter.reel_blocked").withStyle(ChatFormatting.GRAY));
				return true;
			}
		}
		ServerLevel level = player.level();
		PickKit.spend(TABLE, player, 0, COOLDOWN);
		long now = level.getGameTime();
		PULLS.put(player.getUUID(), new Pull(foe, bend, now));
		player.setAttached(ZFighterStretchCombo.REACH, new ZFighterStretchCombo.Reach(now, TICKS, ZFighterStretchCombo.RIGHT_ARM,
			(float)player.getEyePosition().distanceTo(to), foe.getId()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.PLAYERS, 0.9F, 1.1F);
		return true;
	}

	/** The foe the crosshair is on, or else the nearest one close round the look, within reach. */
	private static @Nullable LivingEntity aimed(ServerPlayer player) {
		LivingEntity on = PickKit.aimed(player, REACH);
		if (on != null) {
			return on;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double near = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(REACH), e -> PickKit.foe(player, e))) {
			Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
			double dist = d.length();
			if (dist <= REACH && dist > 1.0E-3 && d.scale(1.0 / dist).dot(look) >= CONE && dist < near) {
				near = dist;
				best = e;
			}
		}
		return best;
	}

	/** A point the arm can bend at to get round what's in the way: over it, or round either side. */
	private static @Nullable Vec3 bend(ServerPlayer player, Vec3 to) {
		Vec3 eye = player.getEyePosition();
		Vec3 mid = eye.add(to).scale(0.5);
		Vec3 span = to.subtract(eye);
		Vec3 side = new Vec3(-span.z, 0.0, span.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		for (Vec3 off : new Vec3[] {new Vec3(0.0, 1.5, 0.0), side.scale(2.0), side.scale(-2.0), new Vec3(0.0, 3.0, 0.0), side.scale(3.5),
			side.scale(-3.5), side.scale(2.0).add(0.0, 1.5, 0.0), side.scale(-2.0).add(0.0, 1.5, 0.0)}) {
			Vec3 at = mid.add(off);
			if (eye.distanceTo(at) + at.distanceTo(to) <= REACH + 2.0 && clear(player, eye, at) && clear(player, at, to)) {
				return at;
			}
		}
		return null;
	}

	private static boolean clear(ServerPlayer player, Vec3 from, Vec3 to) {
		return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
	}

	private static void tick(MinecraftServer server) {
		if (PULLS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Pull>> it = PULLS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Pull> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Pull pull = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long t = level.getGameTime() - pull.start();
			LivingEntity foe = pull.foe();
			if (t == TICKS / 2) {
				if (foe.isAlive() && foe.level() == level && player.isAlive() && foe.distanceTo(player) <= REACH + 2.0) {
					yank(level, player, foe, pull.bend());
				} else {
					ZFighterStretchCombo.snap(player);
				}
			}
			if (t >= TICKS) {
				ZFighterStretchCombo.clear(player);
				it.remove();
			}
		}
	}

	/** Hooked: to their feet it comes, and whatever it was at is cut off. */
	private static void yank(ServerLevel level, ServerPlayer player, LivingEntity foe, @Nullable Vec3 bend) {
		Vec3 hand = player.position().add(0.0, 1.35, 0.0);
		Vec3 at = foe.getBoundingBox().getCenter();
		if (bend != null) {
			trail(level, hand, bend);
			trail(level, bend, at);
		} else {
			trail(level, hand, at);
		}
		level.sendParticles(ParticleTypes.ITEM_SLIME, true, false, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.15);
		Vec3 flat = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 feet = player.position().add(flat.scale(0.8 * player.getScale() + foe.getBbWidth() * 0.5));
		if (!level.noCollision(foe, foe.getDimensions(foe.getPose()).makeBoundingBox(feet))) {
			feet = player.position();
		}
		if (foe.isPassenger()) {
			foe.stopRiding();
		}
		foe.teleportTo(feet.x, feet.y, feet.z);
		foe.setDeltaMovement(Vec3.ZERO);
		foe.syncVelocity = true;
		foe.resetFallDistance();
		// cut off: a drawn bow, a raised shield, a meal, a fuse, a spell, its steps
		foe.stopUsingItem();
		if (foe instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (foe instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		AirlessDome.breakSpell(foe);
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, REEL_TICKS, 3, false, false), player);
		ZFighterPoise.wear(foe, ZFighterPoise.PARRY_WEAR);
		ZFighterStretchCombo.snap(player);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 0.9F, 0.8F);
	}

	private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 span = to.subtract(from);
		int bits = Math.max(2, (int)(span.length() * 2));
		for (int i = 1; i < bits; i++) {
			Vec3 p = from.add(span.scale(i / (double)bits));
			level.sendParticles(ParticleTypes.ITEM_SLIME, true, false, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
	}
}
