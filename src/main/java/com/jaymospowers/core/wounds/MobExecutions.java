package com.jaymospowers.core.wounds;

import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.finisher.Finishers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.16) Andy (2026-11-01): "enemy mobs and illagers should be able to execute you, with a letterboxed scene". A downed
 * player lying where hostiles can see them gets finished off: after {@link #GRACE_TICKS} (time for a friend to come), the
 * nearest hostile ({@link Enemy}: zombies, skeletons, spiders, illagers, piglins, the warden...; not creepers, fliers,
 * slimes or bosses, {@link #able}), or anyone else fighting them (a bandit, a barbarian, a guard, a golem) that sees them leaves its fight, walks over and kills them with a move of its own
 * ({@link Move}: an axe takes the head, a blade runs them through, a bow or crossbow fires point blank, an evoker's fangs
 * close on them, a zombie or spider feeds, a witch breaks a potion on them, a ravager or the warden crushes them).
 * <p>
 * The victim sees it as a film ({@link UltimateFilm}): the bars come in and the camera goes low and tilted on the
 * executioner as it stands over them and raises its weapon; after the blow it cuts round the killer over the body before
 * the death screen. Players near see the bars. Nothing is taken from anyone's controls.
 * <p>
 * <b>Stopping it:</b> hit the executioner. Any blow on it (yours, a friend's, a golem's) before the last breaks it off; it
 * turns on whoever hit it and won't try again for {@link #SHAKEN_TICKS}. It also stops if the victim is brought round,
 * rises, is carried off or crawls away (it follows), or the executioner is downed or dies. While it walks over and stands
 * there, its own AI is set aside ({@code DownedMobMixin} calls {@link #step}), so it does nothing else.
 */
public final class MobExecutions {
	/** How long a player lies downed before hostiles come to finish them (ticks). */
	public static final int GRACE_TICKS = 100;
	/** How far an executioner may be when it decides to come over (blocks); it must see them. */
	public static final double SEEK_RANGE = 16.0;
	/** How close it stands to do it (blocks, centre to centre, flat), and how far the victim may crawl before it steps after them. */
	public static final double REACH = 1.6, SLIP = 2.8;
	/** Its pace walking over (a share of its speed), and how long it may take to get there before it gives up (ticks). */
	public static final double APPROACH_SPEED = 1.1;
	public static final int APPROACH_MAX = 200;
	/** The scene once it's there: the weapon raised at {@link #WINDUP}, the killing blow at {@link #STRIKE} (ticks). */
	public static final int WINDUP = 18, STRIKE = 40, SCENE_TICKS = 56;
	/** The film after the blow, round the killer over the body, before the death screen (ticks). */
	public static final int AFTER_TICKS = 50;
	/** Players this near the executioner see the bars too (blocks). */
	public static final double WITNESSES = 24.0;
	/** An executioner hit or thwarted won't try again this long (ticks). */
	public static final int SHAKEN_TICKS = 300;
	/** How often downed players are looked over for someone coming (ticks). */
	static final int SCAN_EVERY = 10;
	/** Screen shake for the victim at the wind-up and the blow. */
	static final float WINDUP_SHAKE = 0.6F, KILL_SHAKE = 2.2F;
	/** (0.19.16) Struck by the victim this recently (ticks), a mob counts as fighting them. */
	public static final int GRUDGE_TICKS = 400;
	/** Smaller than this (width and height) is too small to do it (silverfish, endermites). */
	static final float MIN_SIZE = 0.5F;

	/**
	 * More mobs that may execute a downed player (Kingdoms' raiders, a mod's monsters), asked of mobs that aren't
	 * {@link Enemy} already. They still have to be {@link #able}.
	 */
	public static final List<Predicate<Mob>> EXECUTIONERS = new ArrayList<>();
	/**
	 * (0.19.16) Mobs that won't execute this victim even fighting them. Andy: guards "prioritize arresting you because
	 * unless they are of a certain temperament they try to be righteous and just": Kingdoms adds its guards here, all but
	 * the hotheaded and spiteful. Plain JDK and vanilla types, so another mod can add to it by reflection.
	 */
	public static final List<java.util.function.BiPredicate<Mob, LivingEntity>> SPARE = new ArrayList<>();

	/** How it kills, picked by what it holds and what it is ({@link #moveOf}). */
	public enum Move {
		/** An axe (a vindicator's): one chop, the head taken. */
		CHOP,
		/** A blade or spear: driven down through them. */
		STAB,
		/** A bow or crossbow: one shot point blank. */
		SHOT,
		/** A club, a rock, anything else held: brought down hard. */
		BLUDGEON,
		/** An evoker: fangs close on them from the ground. */
		FANGS,
		/** A witch: a potion broken on them. */
		POTION,
		/** A zombie or spider, empty-handed: it falls on them and feeds. */
		FEED,
		/** Something big (a ravager, the warden): it crushes them. */
		CRUSH,
		/** Empty hands: a stamp on the neck. */
		STAMP
	}

	/** One execution under way. {@code at}: when the scene began (0 while it's still walking over). */
	private static final class Execution {
		final UUID victim;
		final Mob mob;
		final Move move;
		final long claimed;
		long at;

		Execution(UUID victim, Mob mob, Move move, long claimed) {
			this.victim = victim;
			this.mob = mob;
			this.move = move;
			this.claimed = claimed;
		}
	}

	/** Victim → their execution. */
	private static final Map<UUID, Execution> BY_VICTIM = new HashMap<>();
	/** Executioner → its execution. */
	private static final Map<Mob, Execution> BY_MOB = new java.util.WeakHashMap<>();
	/** Mob → the game time it may try again (hit or thwarted). */
	private static final Map<Mob, Long> SHAKEN = new java.util.WeakHashMap<>();

	private MobExecutions() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MobExecutions::tick);
		// a blow on the executioner breaks it off, and it turns on whoever struck
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob mob && BY_MOB.containsKey(mob) && taken > 0.0F && Downed.attack(source)) {
				Execution e = BY_MOB.get(mob);
				if (e.at == 0L || mob.level().getGameTime() - e.at < STRIKE) {
					stop(e, true);
					if (source.getEntity() instanceof LivingEntity by && by != mob && by.isAlive()) {
						mob.setTarget(by);
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BY_VICTIM.clear();
			BY_MOB.clear();
			SHAKEN.clear();
		});
	}

	/** Whether {@code mob} is walking over to, or standing over, someone to execute them (its AI is set aside). */
	public static boolean busy(Mob mob) {
		return BY_MOB.containsKey(mob);
	}

	/** Whether {@code player} is about to be executed (someone's coming, or it's begun). */
	public static boolean doomed(ServerPlayer player) {
		return BY_VICTIM.containsKey(player.getUUID());
	}

	/**
	 * Whether {@code mob} may execute {@code victim}: on its feet, big enough, and walking (no creepers, fliers, slimes,
	 * water mobs or bosses), not ridden or riding; and (0.19.16, Andy: "everyone") either a hostile ({@link Enemy}, or one
	 * {@link #EXECUTIONERS} names) or anyone at all who is fighting them: a bandit, a barbarian, a guard, a golem, a wolf.
	 */
	public static boolean able(Mob mob, LivingEntity victim) {
		if (!mob.isAlive() || mob.isNoAi() || Downed.isDowned(mob) || mob.isPassenger() || mob.isVehicle() && !(mob instanceof Spider)
			|| Carry.isCarried(mob) || mob.isSleeping() || mob.isBaby() && !(mob instanceof Enemy)) {
			return false;
		}
		if (mob instanceof Creeper || mob instanceof Slime || mob instanceof Ghast || mob instanceof Phantom || mob instanceof Vex
			|| mob instanceof Blaze || mob instanceof Breeze || mob instanceof Shulker || mob instanceof Guardian || mob instanceof WitherBoss
			|| mob instanceof EnderDragon) {
			return false;
		}
		if (mob.getBbWidth() < MIN_SIZE && mob.getBbHeight() < MIN_SIZE) {
			return false;
		}
		for (java.util.function.BiPredicate<Mob, LivingEntity> spare : SPARE) {
			if (spare.test(mob, victim)) {
				return false;
			}
		}
		if (mob instanceof Enemy || fighting(mob, victim)) {
			return true;
		}
		for (Predicate<Mob> p : EXECUTIONERS) {
			if (p.test(mob)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether {@code mob} is fighting {@code victim}: it has them as its target (or its brain's attack target), or they
	 * struck it within {@link #GRUDGE_TICKS}.
	 */
	public static boolean fighting(Mob mob, LivingEntity victim) {
		if (mob.getTarget() == victim) {
			return true;
		}
		// (as Kingdoms' Rulers.target: a brain without the memory throws on getMemory)
		if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
			&& mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == victim) {
			return true;
		}
		return mob.getLastHurtByMob() == victim && mob.tickCount - mob.getLastHurtByMobTimestamp() < GRUDGE_TICKS;
	}

	/** What {@code mob} does it with. */
	public static Move moveOf(Mob mob) {
		if (mob instanceof Evoker) {
			return Move.FANGS;
		}
		if (mob instanceof Witch) {
			return Move.POTION;
		}
		if (mob instanceof Ravager || mob instanceof Warden) {
			return Move.CRUSH;
		}
		if (!mob.getMainHandItem().isEmpty()) {
			return switch (Finishers.kind(mob.getMainHandItem())) {
				case AXE, GREATSWORD -> Move.CHOP;
				case SWORD, DAGGER, SPEAR, POLEARM -> Move.STAB;
				case BOW, CROSSBOW, GUN -> Move.SHOT;
				case FIST -> bare(mob);
				default -> Move.BLUDGEON;
			};
		}
		return bare(mob);
	}

	private static Move bare(Mob mob) {
		if (mob instanceof Zombie || mob instanceof Spider || BodyPlan.of(mob) != BodyPlan.HUMANOID) {
			return mob.getBbWidth() >= 1.3F ? Move.CRUSH : Move.FEED;
		}
		return Move.STAMP;
	}

	// ---- the tick --------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		// the ones under way
		for (Iterator<Execution> it = new ArrayList<>(BY_VICTIM.values()).iterator(); it.hasNext();) {
			Execution e = it.next();
			ServerPlayer victim = server.getPlayerList().getPlayer(e.victim);
			if (victim == null || !(victim.level() instanceof ServerLevel level) || e.mob.level() != level || !e.mob.isAlive()
				|| Downed.isDowned(e.mob) || !Downed.isDowned(victim) || !victim.isAlive() || Carry.isCarried(victim) || victim.isPassenger()
				|| victim.hasAttached(Downed.RISING)) {
				stop(e, true);
				continue;
			}
			advance(level, e, victim);
		}
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Downed.isDowned(player) || BY_VICTIM.containsKey(player.getUUID()) || player.isCreative() || player.isSpectator()
				|| !player.isAlive() || Carry.isCarried(player) || player.isPassenger() || player.hasAttached(Downed.RISING)
				|| !(player.level() instanceof ServerLevel level) || level.getGameTime() - Downed.since(player) < GRACE_TICKS) {
				continue;
			}
			Mob mob = seek(level, player);
			if (mob != null) {
				Execution e = new Execution(player.getUUID(), mob, moveOf(mob), level.getGameTime());
				BY_VICTIM.put(e.victim, e);
				BY_MOB.put(mob, e);
				mob.setTarget(null);
				mob.setAggressive(false);
			}
		}
	}

	/** The nearest hostile that may do it, sees them and is free; null if none. */
	private static @Nullable Mob seek(ServerLevel level, ServerPlayer player) {
		long now = level.getGameTime();
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SEEK_RANGE), Mob::isAlive)) {
			Long shaken = SHAKEN.get(mob);
			if (shaken != null && now < shaken || BY_MOB.containsKey(mob) || !able(mob, player) || Finishers.busy(mob)) {
				continue;
			}
			double d = mob.distanceToSqr(player);
			if (d < bestDistance && d <= SEEK_RANGE * SEEK_RANGE && mob.hasLineOfSight(player)) {
				best = mob;
				bestDistance = d;
			}
		}
		return best;
	}

	/** One tick of an execution: walking over, then the scene. */
	private static void advance(ServerLevel level, Execution e, ServerPlayer victim) {
		Mob mob = e.mob;
		long now = level.getGameTime();
		double flat = Math.sqrt(Mth.square(mob.getX() - victim.getX()) + Mth.square(mob.getZ() - victim.getZ()));
		if (e.at == 0L) {
			if (now - e.claimed > APPROACH_MAX) {
				stop(e, true);
				return;
			}
			if (flat > REACH || Math.abs(mob.getY() - victim.getY()) > 1.5) {
				if (now % 5 == 0 || mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(victim, APPROACH_SPEED);
				}
				return;
			}
			begin(level, e, victim);
			return;
		}
		int t = (int)(now - e.at);
		if (t < STRIKE && flat > SLIP) {
			// they crawled off: after them, and it begins again when it's there
			e.at = 0L;
			return;
		}
		mob.getNavigation().stop();
		face(mob, victim);
		if (t == WINDUP) {
			windup(level, e, victim);
		} else if (e.move == Move.FEED && (t == STRIKE - 12 || t == STRIKE - 6)) {
			// tearing at them before the end
			mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			bite(level, victim, 0.8F);
		} else if (t == STRIKE) {
			strike(level, e, victim);
		} else if (t >= SCENE_TICKS) {
			stop(e, false);
		}
	}

	/** It's there: the scene begins, and the victim's film. */
	private static void begin(ServerLevel level, Execution e, ServerPlayer victim) {
		Mob mob = e.mob;
		e.at = level.getGameTime();
		mob.getNavigation().stop();
		face(mob, victim);
		mob.playAmbientSound();
		UltimateFilm.play(victim, UltimateFilm.DUTCH, SCENE_TICKS, mob.getEyePosition(), mob, 0.0);
		UltimateFilm.witnessed(mob, UltimateFilm.DUTCH, SCENE_TICKS, mob.getEyePosition(), mob, WITNESSES);
	}

	/** The weapon raised: a sound of what's coming. */
	private static void windup(ServerLevel level, Execution e, ServerPlayer victim) {
		Mob mob = e.mob;
		SoundEvent sound = switch (e.move) {
			case CHOP -> mob instanceof Raider raider ? raider.getCelebrateSound() : SoundEvents.PLAYER_ATTACK_SWEEP;
			case STAB, BLUDGEON, STAMP -> SoundEvents.PLAYER_ATTACK_SWEEP;
			case SHOT -> SoundEvents.CROSSBOW_LOADING_END.value();
			case FANGS -> SoundEvents.EVOKER_PREPARE_ATTACK;
			case POTION -> SoundEvents.WITCH_CELEBRATE;
			case FEED -> mob instanceof Spider ? SoundEvents.SPIDER_AMBIENT : SoundEvents.ZOMBIE_AMBIENT;
			case CRUSH -> mob instanceof Warden ? SoundEvents.WARDEN_ROAR : SoundEvents.RAVAGER_ROAR;
		};
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), sound, SoundSource.HOSTILE, 1.1F, 0.8F + mob.getRandom().nextFloat() * 0.2F);
		if (e.move == Move.FANGS) {
			level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 30, 0.4, 0.4, 0.4, 0.5);
		}
		Shakes.send(victim, WINDUP_SHAKE, mob.position());
	}

	/** The killing blow: the sound, blood, the body jolting, the film after, and their death credited to it. */
	private static void strike(ServerLevel level, Execution e, ServerPlayer victim) {
		Mob mob = e.mob;
		double x = victim.getX(), y = victim.getY() + 0.3, z = victim.getZ();
		float pitch = 0.85F + mob.getRandom().nextFloat() * 0.3F;
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		switch (e.move) {
			case CHOP -> {
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2F, pitch * 0.7F);
				BladeBlood.splash(victim, 12.0F);
			}
			case STAB -> {
				level.playSound(null, x, y, z, SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.1F, pitch * 0.8F);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.9F, pitch * 0.7F);
				BladeBlood.splash(victim, 10.0F);
			}
			case SHOT -> {
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.2F, pitch);
				level.playSound(null, x, y, z, SoundEvents.ARROW_HIT_PLAYER, SoundSource.HOSTILE, 1.0F, pitch * 0.7F);
				level.sendParticles(ParticleTypes.CRIT, x, y, z, 14, 0.2, 0.2, 0.2, 0.3);
				BladeBlood.splash(victim, 8.0F);
			}
			case FANGS -> {
				float yaw = (float)Math.toDegrees(Math.atan2(z - mob.getZ(), x - mob.getX()));
				level.addFreshEntity(new EvokerFangs(level, x, victim.getY(), z, yaw * Mth.DEG_TO_RAD, 0, mob));
				level.playSound(null, x, y, z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.2F, pitch);
				BladeBlood.splash(victim, 10.0F);
			}
			case POTION -> {
				level.playSound(null, x, y, z, SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.0F, pitch);
				level.playSound(null, x, y, z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0F, pitch);
				level.sendParticles(ParticleTypes.WITCH, x, y + 0.3, z, 30, 0.5, 0.3, 0.5, 0.1);
				level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 15, 0.4, 0.2, 0.4, 0.02);
			}
			case FEED -> bite(level, victim, 1.3F);
			case CRUSH -> {
				SoundEvent sound = mob instanceof Warden ? SoundEvents.WARDEN_ATTACK_IMPACT : SoundEvents.RAVAGER_ATTACK;
				level.playSound(null, x, y, z, sound, SoundSource.HOSTILE, 1.4F, pitch * 0.8F);
				level.playSound(null, x, y, z, SoundEvents.HOSTILE_BIG_FALL, SoundSource.HOSTILE, 1.0F, 0.7F);
				BladeBlood.blunt(victim, 16.0F);
				Shakes.around(level, victim.position(), 10.0, 1.2F);
			}
			case BLUDGEON, STAMP -> {
				level.playSound(null, x, y, z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 0.8F, pitch * 0.7F);
				level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, pitch * 0.6F);
				BladeBlood.blunt(victim, 10.0F);
			}
		}
		level.broadcastDamageEvent(victim, level.damageSources().mobAttack(mob));
		Shakes.send(victim, KILL_SHAKE, mob.position());
		// the film after: round the killer over the body, before the death screen (a Kingdoms captain's own scene, from the
		// death, takes its place)
		UltimateFilm.afterDeath(victim, UltimateFilm.CUTS, AFTER_TICKS, mob.getBoundingBox().getCenter(), mob);
		if (e.move == Move.CHOP && Dismember.canLoseParts(victim)) {
			Dismember.sever(victim, Dismember.HEAD, mob);
		}
		Downed.execute(level, victim, mob);
		if (e.move == Move.CRUSH) {
			com.jaymospowers.core.Crushing.mark(victim);
		}
	}

	/** A zombie's or spider's bite: wet, and bloody. */
	private static void bite(ServerLevel level, ServerPlayer victim, float loud) {
		double x = victim.getX(), y = victim.getY() + 0.3, z = victim.getZ();
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, loud, 0.6F + level.getRandom().nextFloat() * 0.2F);
		level.playSound(null, x, y, z, SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, loud * 0.7F, 0.8F);
		BladeBlood.splash(victim, 6.0F * loud);
		Shakes.send(victim, WINDUP_SHAKE, victim.position());
	}

	/** Turned to look down at them. */
	private static void face(Mob mob, LivingEntity victim) {
		double dx = victim.getX() - mob.getX(), dz = victim.getZ() - mob.getZ();
		if (dx * dx + dz * dz > 1.0E-4) {
			float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			mob.setYRot(yaw);
			mob.setYHeadRot(yaw);
			mob.yBodyRot = yaw;
		}
		mob.getLookControl().setLookAt(victim.getX(), victim.getY() + 0.2, victim.getZ());
	}

	/** It's over, done or broken off ({@code thwarted}: it won't try again a while). */
	private static void stop(Execution e, boolean thwarted) {
		BY_VICTIM.remove(e.victim, e);
		BY_MOB.remove(e.mob, e);
		e.mob.getNavigation().stop();
		if (thwarted) {
			SHAKEN.put(e.mob, e.mob.level().getGameTime() + SHAKEN_TICKS);
		}
	}

	/**
	 * {@code DownedMobMixin}: the executioner's AI step, in place of its own while it's at this: only its legs, eyes and
	 * jumps run (where it walks and looks is set by {@link #advance}). False if it isn't one.
	 */
	public static boolean step(Mob mob) {
		if (!BY_MOB.containsKey(mob)) {
			return false;
		}
		mob.getNavigation().tick();
		mob.getMoveControl().tick();
		mob.getLookControl().tick();
		mob.getJumpControl().tick();
		return true;
	}
}
