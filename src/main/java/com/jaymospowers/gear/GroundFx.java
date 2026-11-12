package com.jaymospowers.gear;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Tripped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Things the gear leaves lying about for a while, ticked on the server: clouds of smoke, stinging smoke and choking gas,
 * scattered caltrops, a set bear trap, a flare's light, and the sneezing fit of someone who breathed the powder. None of it is
 * saved: a restart clears them (a flare's light block is taken down first).
 */
public final class GroundFx {
	// ---- tuning ----
	/** Cloud radius (blocks) and lifetime (ticks) of each kind as a thrown pot or vial makes it. */
	public static final double POT_RADIUS = 3.5, VIAL_RADIUS = 3.5;
	public static final int POT_TICKS = 140, VIAL_TICKS = 120, ARROW_SMOKE_TICKS = 160;
	/** Damage a cloud deals each second to those in it (stinging and choking ones). */
	public static final float CLOUD_DAMAGE = 1.0F;
	/** Caltrops: points scattered, lifetime (ticks), reach (blocks), damage, how long they slow, and the wait before one can cut the same foot again. */
	public static final int CALTROP_POINTS = 8, CALTROP_TICKS = 2400, CALTROP_SLOW = 80, CALTROP_REPEAT = 30;
	public static final double CALTROP_SPREAD = 2.4, CALTROP_REACH = 0.8;
	public static final float CALTROP_DAMAGE = 1.0F;
	/** Bear trap: ticks to arm, to arm for its setter, lifetime, reach, damage, how long it roots (ticks). */
	public static final int TRAP_ARM = 20, TRAP_ARM_OWNER = 80, TRAP_TICKS = 6000, TRAP_ROOT = 120;
	public static final double TRAP_REACH = 0.75;
	public static final float TRAP_DAMAGE = 4.0F;
	/** Flare: radius (blocks) it marks and its lifetime (ticks). */
	public static final double FLARE_RADIUS = 14.0;
	public static final int FLARE_TICKS = 300;
	/** Sneezing fit: ticks, and the gap between sneezes. */
	public static final int SNEEZE_TICKS = 120, SNEEZE_GAP = 12;
	/** The whistle's pull: how fast mobs walk to it. */
	public static final double LURE_SPEED = 1.1;

	/** A thing on the ground. {@link #tick} returns false when it is done. */
	private interface Fx {
		boolean tick(ServerLevel level, long now);

		default void end(ServerLevel level) {
		}
	}

	private record Entry(ResourceKey<Level> dimension, Fx fx) {
	}

	/** The cloud kinds: their colour, and what they do to those in them every half second. */
	public enum Kind {
		SMOKE(0x90909A), STING(0xB8B070), CHOKE(0x78A040);

		public final int color;

		Kind(int color) {
			this.color = color;
		}
	}

	private static final List<Entry> ACTIVE = new ArrayList<>();

	private GroundFx() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (int i = ACTIVE.size() - 1; i >= 0; i--) {
				Entry entry = ACTIVE.get(i);
				ServerLevel level = server.getLevel(entry.dimension());
				if (level == null) {
					ACTIVE.remove(i);
				} else if (!entry.fx().tick(level, level.getGameTime())) {
					entry.fx().end(level);
					ACTIVE.remove(i);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Entry entry : ACTIVE) {
				ServerLevel level = server.getLevel(entry.dimension());
				if (level != null) {
					entry.fx().end(level);
				}
			}
			ACTIVE.clear();
		});
	}

	private static void add(ServerLevel level, Fx fx) {
		ACTIVE.add(new Entry(level.dimension(), fx));
	}

	private static boolean friendly(@Nullable UUID owner, Entity entity) {
		return owner != null && owner.equals(entity.getUUID());
	}

	// ---- clouds ----

	/** A cloud of {@code kind} at {@code at}, {@code radius} wide, for {@code ticks}. */
	public static void cloud(ServerLevel level, Vec3 at, @Nullable Entity owner, Kind kind, double radius, int ticks) {
		UUID by = owner == null ? null : owner.getUUID();
		long end = level.getGameTime() + ticks;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
		add(level, (lv, now) -> {
			if (now >= end) {
				return false;
			}
			if (now % 4 == 0) {
				lv.sendParticles(MagicFx.miasma(kind.color, 2.6F), at.x, at.y + 0.7, at.z, 6, radius * 0.45, 0.55, radius * 0.45, 0.01);
			}
			if (now % 10 == 0) {
				AABB box = new AABB(at, at).inflate(radius, radius * 0.7, radius);
				for (LivingEntity victim : lv.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
					if (victim.position().distanceTo(at) > radius * 1.2) {
						continue;
					}
					breathe(lv, victim, kind, by, now);
				}
			}
			return true;
		});
	}

	private static void breathe(ServerLevel level, LivingEntity victim, Kind kind, @Nullable UUID owner, long now) {
		boolean own = friendly(owner, victim);
		switch (kind) {
			case SMOKE -> {
				if (victim instanceof Mob mob) {
					mob.setTarget(null);
				}
				if (!own) {
					victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
				}
			}
			case STING -> {
				victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
				victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0));
				if (now % 20 == 0) {
					victim.hurtServer(level, level.damageSources().magic(), CLOUD_DAMAGE);
				}
			}
			case CHOKE -> {
				victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
				victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
				victim.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 100, 0));
				if (now % 20 == 0) {
					victim.hurtServer(level, level.damageSources().magic(), CLOUD_DAMAGE);
				}
			}
		}
	}

	// ---- caltrops ----

	/** Scatters {@code points} caltrops round {@code at}: each cuts and slows the first thing that steps on it, and is spent. */
	public static void caltrops(ServerLevel level, Vec3 at, @Nullable Entity owner, int points) {
		List<Vec3> spots = new ArrayList<>();
		for (int i = 0; i < points; i++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double reach = Math.sqrt(level.getRandom().nextDouble()) * CALTROP_SPREAD;
			spots.add(new Vec3(at.x + Math.cos(angle) * reach, at.y, at.z + Math.sin(angle) * reach));
		}
		Map<UUID, Long> last = new HashMap<>();
		long end = level.getGameTime() + CALTROP_TICKS;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.9F, 1.4F);
		add(level, (lv, now) -> {
			if (now >= end || spots.isEmpty()) {
				return false;
			}
			if (now % 20 == 0) {
				for (Vec3 spot : spots) {
					lv.sendParticles(MagicFx.mote(0xA8AEB8, 0.5F), spot.x, spot.y + 0.1, spot.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
			if (now % 2 != 0) {
				return true;
			}
			for (int i = spots.size() - 1; i >= 0; i--) {
				Vec3 spot = spots.get(i);
				AABB box = new AABB(spot, spot).inflate(CALTROP_REACH, 0.6, CALTROP_REACH);
				for (LivingEntity victim : lv.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e.onGround())) {
					Long before = last.get(victim.getUUID());
					if (before != null && now - before < CALTROP_REPEAT) {
						continue;
					}
					last.put(victim.getUUID(), now);
					victim.hurtServer(lv, lv.damageSources().generic(), CALTROP_DAMAGE);
					victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CALTROP_SLOW, 1));
					Bleeding.open(victim, BodyPart.LEFT_FOOT, false);
					Bleeding.open(victim, BodyPart.HIND_LEGS, false);
					lv.playSound(null, spot.x, spot.y, spot.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
					spots.remove(i);
					break;
				}
			}
			return true;
		});
	}

	// ---- bear trap ----

	/** Sets a bear trap at {@code at}: it springs on the first thing that steps near it (its setter only after a moment), and is spent. */
	public static void bearTrap(ServerLevel level, Vec3 at, @Nullable Entity owner) {
		UUID by = owner == null ? null : owner.getUUID();
		long start = level.getGameTime();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5F, 1.6F);
		add(level, (lv, now) -> {
			if (now - start > TRAP_TICKS) {
				return false;
			}
			if (now % 40 == 0) {
				lv.sendParticles(MagicFx.mote(0x9AA0AA, 0.5F), at.x, at.y + 0.1, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (now - start < TRAP_ARM || now % 2 != 0) {
				return true;
			}
			AABB box = new AABB(at, at).inflate(TRAP_REACH, 0.5, TRAP_REACH);
			for (LivingEntity victim : lv.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && e.onGround())) {
				if (friendly(by, victim) && now - start < TRAP_ARM_OWNER) {
					continue;
				}
				victim.hurtServer(lv, lv.damageSources().generic(), TRAP_DAMAGE);
				Bleeding.open(victim, BodyPart.LEFT_LEG, false);
				Bleeding.open(victim, BodyPart.FRONT_LEGS, false);
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TRAP_ROOT, 6));
				Tripped.bind(victim, owner, TRAP_ROOT);
				lv.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.9F, 1.8F);
				lv.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 0.8F);
				MagicFx.ring(lv, at, 1.4F, 0xB0B4BC);
				return false;
			}
			return true;
		});
	}

	// ---- flare ----

	/** A flare burning at {@code at}: light, and everything alive round it glows while it lasts. */
	public static void flare(ServerLevel level, Vec3 at, int color) {
		BlockPos pos = BlockPos.containing(at);
		BlockPos lit = null;
		for (BlockPos probe : new BlockPos[] {pos, pos.above(), pos.above(2)}) {
			if (level.getBlockState(probe).isAir()) {
				lit = probe;
				break;
			}
		}
		if (lit != null) {
			level.setBlock(lit, Blocks.LIGHT.defaultBlockState(), 3);
		}
		BlockPos light = lit;
		long end = level.getGameTime() + FLARE_TICKS;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		add(level, new Fx() {
			@Override
			public boolean tick(ServerLevel lv, long now) {
				if (now >= end) {
					return false;
				}
				if (now % 20 == 0) {
					MagicFx.orb(lv, at.add(0.0, 0.6, 0.0), 0.7F, color, 24);
					AABB box = new AABB(at, at).inflate(FLARE_RADIUS);
					for (LivingEntity marked : lv.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
						marked.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
					}
				}
				return true;
			}

			@Override
			public void end(ServerLevel lv) {
				if (light != null && lv.getBlockState(light).is(Blocks.LIGHT)) {
					lv.setBlock(light, Blocks.AIR.defaultBlockState(), 3);
				}
			}
		});
	}

	// ---- the whistle ----

	/** Calls every mob within {@code range} of {@code at} to it (not pets, leashed or ridden ones). */
	public static void lure(ServerLevel level, Vec3 at, double range) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 3.0F, 1.6F);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(range), m -> m.isAlive() && !m.isNoAi())) {
			if (mob.isLeashed() || mob.isVehicle() || mob.isPassenger() || mob instanceof TamableAnimal pet && pet.isTame()) {
				continue;
			}
			mob.getNavigation().moveTo(at.x, at.y, at.z, LURE_SPEED);
		}
	}

	// ---- sneezing ----

	/** Sets {@code victim} sneezing: mobs lose their target and stumble about, players are shaken out of whatever they were holding ready. */
	public static void sneeze(ServerLevel level, LivingEntity victim) {
		long end = level.getGameTime() + SNEEZE_TICKS;
		UUID id = victim.getUUID();
		if (victim instanceof ServerPlayer) {
			victim.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SNEEZE_TICKS + 40, 0));
		} else {
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SNEEZE_TICKS / 2, 1));
		}
		add(level, (lv, now) -> {
			Entity entity = lv.getEntity(id);
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || now >= end) {
				return false;
			}
			if ((end - now) % SNEEZE_GAP == 0) {
				lv.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PANDA_SNEEZE, SoundSource.NEUTRAL, 1.0F, 1.2F);
				if (living instanceof Mob mob) {
					mob.setTarget(null);
					mob.getNavigation().stop();
				}
				living.stopUsingItem();
				double angle = lv.getRandom().nextDouble() * Math.PI * 2.0;
				living.push(Math.cos(angle) * 0.25, 0.1, Math.sin(angle) * 0.25);
				living.needsSync = true;
			}
			return true;
		});
	}
}
