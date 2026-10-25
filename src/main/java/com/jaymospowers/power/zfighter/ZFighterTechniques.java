package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Soundscape;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * The techniques bought with training (the G and V ones that fly are in {@link ZFighterKi}):
 * <ul>
 * <li>G, Solar Flash: a blinding burst of light; everything looking his way is blinded and loses track of him.</li>
 * <li>G, Ki Barrier: a dome of ki round him for a few seconds that swallows arrows and anything else thrown.</li>
 * <li>C in Super 3, the Spirit Bomb: hold it and a sphere gathers over his head from his own ki and from every living
 * thing within {@link #SPIRIT_RANGE} blocks (animals, villagers, and players who sneak to lend theirs). Let go and it
 * falls where he looks. It hurts only his foes (monsters, whatever is after him or has hurt him lately); everything
 * else is shoved aside.</li>
 * </ul>
 */
final class ZFighterTechniques {
	// ---- Solar Flash ----
	static final float FLASH_KI = 15.0F;
	static final int FLASH_COOLDOWN = 200;
	static final double FLASH_RANGE = 16.0;
	static final int FLASH_BLIND = 100;
	/** Something must be looking at least this much towards him (dot of its look and the way to him). */
	static final double FLASH_FACING = 0.5;

	// ---- Ki Barrier ----
	static final float BARRIER_KI = 25.0F;
	static final int BARRIER_COOLDOWN = 300;
	static final int BARRIER_TICKS = 60;
	static final double BARRIER_RADIUS = 3.0;
	/** Blasts that get through the dome are cut this much. */
	static final float BARRIER_BLAST_CUT = 0.5F;
	static final int BARRIER_COLOR = 0x9FE8FF;

	// ---- Spirit Bomb ----
	static final int SPIRIT_COLOR = 0x8FD8FF;
	static final double SPIRIT_RANGE = 48.0;
	/** (0.16.24) Raising the Spirit Bomb's film (ticks): looking up past you into the sky; bars for all it asks. */
	static final int SPIRIT_FILM_TICKS = 50;
	static final int SPIRIT_COOLDOWN = 3600;
	/** Energy it can hold, and what each gives every second it gathers. */
	static final float SPIRIT_MAX = 400.0F;
	static final float SPIRIT_ANIMAL = 1.0F;
	static final float SPIRIT_FOLK = 2.0F;
	static final float SPIRIT_PLAYER = 6.0F;
	static final int SPIRIT_MAX_LENDERS = 60;
	/** His own ki pours in too (per tick), one energy for each ki. */
	static final float SPIRIT_OWN_KI = 0.5F;
	/** A lending player's own appetite pays a little (exhaustion a second). */
	static final float SPIRIT_LEND_EXHAUSTION = 0.5F;
	static final float SPIRIT_MIN_THROW = 10.0F;
	static final float SPIRIT_BASE_DAMAGE = 20.0F;
	static final float SPIRIT_DAMAGE_PER_ENERGY = 0.25F;
	static final double SPIRIT_SPEED = 0.7;
	/** The sphere's radius at full: 15, so 30 blocks across. */
	static final double SPIRIT_MAX_SIZE = 15.0;
	/** Where it lands: hurt reach and blast power, per block of its radius. */
	static final double SPIRIT_REACH_PER_SIZE = 2.5;
	static final float SPIRIT_BURST_PER_SIZE = 1.0F;
	/** How far off players still see the sphere drawn. */
	static final double SPIRIT_SEEN = 256.0;
	/** Someone who hurt him this recently counts as a foe (ticks). */
	static final int FOE_MEMORY = 1200;

	private ZFighterTechniques() {
	}

	/** Whether {@code target} is someone {@code owner} is fighting: a monster, whatever is after him, or whoever hurt him lately. */
	static boolean foe(ServerPlayer owner, LivingEntity target) {
		if (target == owner) {
			return false;
		}
		if (target instanceof Enemy) {
			return true;
		}
		if (target instanceof Mob mob && mob.getTarget() == owner) {
			return true;
		}
		if (owner.getLastHurtByMob() == target) {
			return true;
		}
		ZFighterPower.Live live = ZFighterPower.live(owner);
		Long when = live.attackers.get(target.getUUID());
		if (when != null && owner.level().getGameTime() - when <= FOE_MEMORY) {
			return true;
		}
		return ZFighterBattle.grudge(owner, target);
	}

	/** Whoever just hurt him is remembered as a foe for a while. */
	static void hurtBy(ServerPlayer player, LivingEntity attacker) {
		Map<UUID, Long> attackers = ZFighterPower.live(player).attackers;
		long now = player.level().getGameTime();
		attackers.values().removeIf(when -> now - when > FOE_MEMORY);
		attackers.put(attacker.getUUID(), now);
	}

	// ---------------------------------------------------------------- Solar Flash

	static boolean flash(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.spend(player, live, FLASH_KI)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(FLASH_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 toward = eye.subtract(near.getEyePosition());
			if (toward.length() > FLASH_RANGE || toward.lengthSqr() < 1.0E-4) {
				continue;
			}
			if (near.getViewVector(1.0F).dot(toward.normalize()) < FLASH_FACING || !near.hasLineOfSight(player)) {
				continue;
			}
			near.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLASH_BLIND, 0), player);
			near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FLASH_BLIND / 2, 1), player);
			if (near instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), true, true, eye.x, eye.y, eye.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, true, false, eye.x, eye.y, eye.z, 40, 0.3, 0.3, 0.3, 0.5);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 2.0F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		return true;
	}

	// ---------------------------------------------------------------- Ki Barrier

	static boolean barrier(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.spend(player, live, BARRIER_KI)) {
			return false;
		}
		live.barrierTicks = BARRIER_TICKS;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	/** While the dome stands: whatever is thrown at him burns up on it. */
	static void tickBarrier(ServerPlayer player, ZFighterPower.Live live) {
		if (live.barrierTicks <= 0) {
			return;
		}
		live.barrierTicks--;
		ServerLevel level = player.level();
		Vec3 centre = player.getBoundingBox().getCenter();
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(BARRIER_RADIUS),
			p -> p.getOwner() != player)) {
			Vec3 at = projectile.position();
			level.sendParticles(new DustParticleOptions(ZFighterKi.baseColor(live, BARRIER_COLOR), 1.5F), true, false, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.05);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.7F, 1.5F);
			projectile.discard();
		}
		if (live.barrierTicks % 2 == 0) {
			RandomSource random = level.getRandom();
			DustParticleOptions dust = new DustParticleOptions(ZFighterKi.baseColor(live, BARRIER_COLOR), 1.0F);
			for (int i = 0; i < 14; i++) {
				Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()), random.nextGaussian()).normalize();
				Vec3 at = centre.add(dir.scale(BARRIER_RADIUS * 0.8));
				level.sendParticles(dust, true, false, at.x, at.y - 0.6, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (live.barrierTicks == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
		}
	}

	// ---------------------------------------------------------------- the Spirit Bomb

	/** Whether C (in Super 3) raises a Spirit Bomb: learned and ready. */
	static boolean canRaise(ServerPlayer player, ZFighterPower.Live live) {
		return live.form >= ZFighterTraining.FORMS && ZFighterTraining.of(player).knows(ZFighterTraining.SPIRIT_BOMB)
			&& ZFighterPower.ready(player, ZFighterState.SPIRIT);
	}

	static void raise(ServerPlayer player, ZFighterPower.Live live) {
		ServerLevel level = player.level();
		live.spiritFrom = level.getGameTime();
		live.spiritEnergy = 0.0F;
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.SKYWARD, SPIRIT_FILM_TICKS, SPIRIT_RANGE);
		Component ask = Component.translatable("message.jaymospowers.zfighter_spirit_ask", player.getDisplayName()).withStyle(ChatFormatting.AQUA);
		for (ServerPlayer other : level.players()) {
			if (other != player && other.distanceToSqr(player) <= SPIRIT_RANGE * SPIRIT_RANGE) {
				other.sendOverlayMessage(ask);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
	}

	/** Where the sphere hangs: over his head, higher as it grows. */
	static Vec3 spherePos(ServerPlayer player, ZFighterPower.Live live) {
		return player.position().add(0.0, player.getBbHeight() + 2.5 + size(live.spiritEnergy), 0.0);
	}

	static double size(float energy) {
		return 1.0 + (SPIRIT_MAX_SIZE - 1.0) * Math.sqrt(Mth.clamp(energy / SPIRIT_MAX, 0.0F, 1.0F));
	}

	static void tickSpirit(ServerPlayer player, ZFighterPower.Live live) {
		if (live.spiritFrom < 0) {
			return;
		}
		if (live.form < ZFighterTraining.FORMS || !player.isAlive()) {
			cancel(player, live);
			return;
		}
		lenders(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		// his own ki first
		if (live.spiritEnergy < SPIRIT_MAX) {
			if (player.isCreative()) {
				live.spiritEnergy += SPIRIT_OWN_KI;
			} else if (live.ki > 0.0F) {
				float take = Math.min(SPIRIT_OWN_KI, live.ki);
				live.ki -= take;
				live.spiritEnergy += take;
			}
		}
		Vec3 sphere = spherePos(player, live);
		if ((now - live.spiritFrom) % 20 == 0 && live.spiritEnergy < SPIRIT_MAX) {
			int lenders = 0;
			DustParticleOptions mote = new DustParticleOptions(SPIRIT_COLOR, 1.2F);
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SPIRIT_RANGE),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
				if (lenders >= SPIRIT_MAX_LENDERS) {
					break;
				}
				float gives = lends(near);
				if (gives <= 0.0F || near.distanceToSqr(player) > SPIRIT_RANGE * SPIRIT_RANGE) {
					continue;
				}
				lenders++;
				live.spiritEnergy += gives;
				if (near instanceof ServerPlayer lender && !lender.isCreative()) {
					lender.causeFoodExhaustion(SPIRIT_LEND_EXHAUSTION);
				}
				// a thread of light rises off each one that gives
				Vec3 up = sphere.subtract(near.position()).normalize();
				level.sendParticles(mote, true, false, near.getX(), near.getY() + near.getBbHeight(), near.getZ(), 0, up.x, up.y, up.z, 0.6);
			}
			live.spiritEnergy = Math.min(SPIRIT_MAX, live.spiritEnergy);
		}
		drawSphere(level, sphere, size(live.spiritEnergy), now);
		spiritFx(level, player.getId(), sphere, size(live.spiritEnergy));
	}

	/** Tells players near where the sphere is and how big, so they draw it. */
	static void spiritFx(ServerLevel level, int id, Vec3 at, double size) {
		spiritFx(level, id, at, size, 0.0F);
	}

	/** ...and how bright (0-1): it glows brighter as it embeds itself, up to the burst. */
	static void spiritFx(ServerLevel level, int id, Vec3 at, double size, float glow) {
		ZFighterNet.SpiritFx fx = new ZFighterNet.SpiritFx(id, at.x, at.y, at.z, (float)size, glow);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(at) <= SPIRIT_SEEN * SPIRIT_SEEN) {
				ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	/** Who is lending to whose Spirit Bomb: lender → the one raising it. */
	private static final Map<UUID, UUID> LENDERS = new java.util.HashMap<>();

	/**
	 * Each tick of a Spirit Bomb: its raiser's hands are up; a player near who sneaks raises theirs and lends from then on,
	 * sneaking or not, until they jump, go too far, fall, or the bomb is thrown or fizzles.
	 */
	private static void lenders(ServerPlayer raiser) {
		hands(raiser, true);
		UUID id = raiser.getUUID();
		for (ServerPlayer other : raiser.level().players()) {
			if (other == raiser || !other.isAlive() || other.isSpectator()) {
				continue;
			}
			boolean near = other.distanceToSqr(raiser) <= SPIRIT_RANGE * SPIRIT_RANGE;
			UUID lendsTo = LENDERS.get(other.getUUID());
			if (lendsTo == null) {
				if (near && other.isShiftKeyDown()) {
					LENDERS.put(other.getUUID(), id);
					hands(other, true);
					other.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_spirit_lending").withStyle(ChatFormatting.AQUA));
				}
			} else if (lendsTo.equals(id) && (!near || other.getLastClientInput().jump())) {
				LENDERS.remove(other.getUUID());
				hands(other, false);
			}
		}
	}

	/** The bomb is gone: everyone lending to {@code raiser} lowers their hands, and so does {@code raiser}. */
	private static void endLending(ServerPlayer raiser) {
		hands(raiser, false);
		UUID id = raiser.getUUID();
		var server = raiser.level().getServer();
		LENDERS.entrySet().removeIf(entry -> {
			if (!entry.getValue().equals(id)) {
				return false;
			}
			ServerPlayer lender = server.getPlayerList().getPlayer(entry.getKey());
			if (lender != null) {
				hands(lender, false);
			}
			return true;
		});
	}

	/** Lenders whose raiser left, died or stopped without saying so lower their hands (every second). */
	static void sweepLenders(net.minecraft.server.MinecraftServer server) {
		LENDERS.entrySet().removeIf(entry -> {
			ServerPlayer lender = server.getPlayerList().getPlayer(entry.getKey());
			ServerPlayer raiser = server.getPlayerList().getPlayer(entry.getValue());
			boolean gone = lender == null || !lender.isAlive() || raiser == null || !raiser.isAlive() || ZFighterPower.live(raiser).spiritFrom < 0
				|| lender.level() != raiser.level();
			if (gone && lender != null) {
				hands(lender, false);
			}
			return gone;
		});
	}

	private static void hands(ServerPlayer player, boolean up) {
		if (up) {
			if (!Boolean.TRUE.equals(player.getAttached(ZFighterPower.SPIRIT_HANDS))) {
				player.setAttached(ZFighterPower.SPIRIT_HANDS, true);
			}
		} else {
			player.removeAttached(ZFighterPower.SPIRIT_HANDS);
		}
	}

	/** What a living thing near gives each second: animals a little, folk more, players only while they lend it. */
	private static float lends(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			return LENDERS.containsKey(player.getUUID()) ? SPIRIT_PLAYER : 0.0F;
		}
		if (entity instanceof Npc) {
			return SPIRIT_FOLK;
		}
		return entity instanceof Animal ? SPIRIT_ANIMAL : 0.0F;
	}

	private static void drawSphere(ServerLevel level, Vec3 centre, double size, long now) {
		RandomSource random = level.getRandom();
		DustParticleOptions shell = new DustParticleOptions(SPIRIT_COLOR, 3.0F);
		int count = (int)Math.min(70, 10 + size * size * 4);
		for (int i = 0; i < count; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			Vec3 at = centre.add(dir.scale(size * (0.7 + random.nextDouble() * 0.3)));
			level.sendParticles(shell, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(new DustParticleOptions(0xFFFFFF, 3.5F), true, false, centre.x, centre.y, centre.z, 3, size * 0.25, size * 0.25,
			size * 0.25, 0.0);
		if (now % 20 == 0) {
			Soundscape.play(level, centre, SoundSource.PLAYERS, ZFighterKi.KI_HUM.louder(2.0F).pitched(0.5F + (float)size * 0.05F), ZFighterKi.KI_SURGE);
		}
	}

	/** C let go: the sphere falls where he looks, if there's enough in it. */
	static void release(ServerPlayer player, ZFighterPower.Live live) {
		if (live.spiritFrom < 0) {
			return;
		}
		if (live.spiritEnergy < SPIRIT_MIN_THROW) {
			cancel(player, live);
			return;
		}
		float energy = live.spiritEnergy;
		Vec3 from = spherePos(player, live);
		endLending(player);
		live.spiritFrom = -1L;
		live.spiritEnergy = 0.0F;
		double size = size(energy);
		float damage = (SPIRIT_BASE_DAMAGE + energy * SPIRIT_DAMAGE_PER_ENERGY) * ZFighterPower.kiPower(player, live);
		Vec3 look = player.getViewVector(1.0F);
		ZFighterKi.throwSpirit(player, from, look.scale(SPIRIT_SPEED), damage, size, 3.0 + size * SPIRIT_REACH_PER_SIZE,
			3.0F + (float)size * SPIRIT_BURST_PER_SIZE);
		ZFighterPower.cooldown(player, ZFighterState.SPIRIT, SPIRIT_COOLDOWN);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 0.6F);
		com.jaymospowers.core.Shakes.send(player, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_spirit_thrown").withStyle(ChatFormatting.AQUA));
	}

	/** It fizzles: nothing thrown, no cooldown. */
	static void cancel(ServerPlayer player, ZFighterPower.Live live) {
		if (live.spiritFrom < 0) {
			return;
		}
		endLending(player);
		live.spiritFrom = -1L;
		live.spiritEnergy = 0.0F;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
	}
}
