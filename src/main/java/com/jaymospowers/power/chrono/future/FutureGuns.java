package com.jaymospowers.power.chrono.future;

import com.jaymospowers.armory.TaczGuns;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Guns in the future: the dead and the echoes there carry TaCZ's own modern guns (its default gunpack, which is for the
 * future only: {@code armory/TaczGuns}) and fire them at what they're fighting. A mob can't
 * pull a TaCZ trigger, so the shots are ours: bullets (a shotgun's spread of pellets) traced from the muzzle, stopped by
 * walls, hurting the first thing they meet, in bursts for automatics, with TaCZ's own report for that gun. Then a
 * reload. No ammunition is used, but a dead gunner may drop its gun (loaded) and some rounds for it. The flintlocks
 * they carried before 0.10.109 still fire. Nothing here without TaCZ.
 */
public final class FutureGuns {
	public static final String TAG = "jaymospowers.gunner";

	/**
	 * How a kind of gun shoots in a mob's hands: how far, the damage per bullet, bullets per shot, stray (degrees),
	 * the pause between bursts (ticks), shots per burst and ticks between them.
	 */
	record Kind(double range, float damage, int balls, float spread, int reload, int burst, int gap) {
	}

	static final Kind PISTOL = new Kind(24.0, 4.0F, 1, 3.0F, 20, 2, 4);
	static final Kind SMG = new Kind(20.0, 2.5F, 1, 5.0F, 40, 6, 2);
	static final Kind RIFLE = new Kind(40.0, 3.5F, 1, 2.5F, 40, 3, 3);
	static final Kind MG = new Kind(36.0, 3.0F, 1, 5.0F, 70, 10, 2);
	static final Kind SHOTGUN = new Kind(12.0, 2.0F, 8, 8.0F, 30, 1, 0);
	static final Kind SNIPER = new Kind(64.0, 12.0F, 1, 0.6F, 80, 1, 0);
	/** Our flintlocks (gunners from before 0.10.109). */
	static final Kind MUSKET = new Kind(32.0, 9.0F, 1, 1.5F, 100, 1, 0);
	static final Kind FLINTLOCK_PISTOL = new Kind(18.0, 6.0F, 1, 3.0F, 60, 1, 0);
	static final Kind BLUNDERBUSS = new Kind(10.0, 2.5F, 6, 9.0F, 90, 1, 0);

	/** A gun a mob may carry: its TaCZ id, how it shoots, its ammo and magazine (what it drops with). */
	record Gun(String id, Kind kind, String ammo, int magazine) {
	}

	/** TaCZ's default guns (its explosive launchers and the minigun left out: nobody hands those to the dead). */
	static final Gun[] MODERN = {
		new Gun("tacz:aa12", SHOTGUN, "tacz:12g", 8), new Gun("tacz:ai_awp", SNIPER, "tacz:338", 5),
		new Gun("tacz:ak47", RIFLE, "tacz:762x39", 30), new Gun("tacz:aug", RIFLE, "tacz:556x45", 30),
		new Gun("tacz:b93r", PISTOL, "tacz:9mm", 20), new Gun("tacz:cz75", PISTOL, "tacz:9mm", 16),
		new Gun("tacz:db_long", SHOTGUN, "tacz:12g", 2), new Gun("tacz:db_short", SHOTGUN, "tacz:12g", 2),
		new Gun("tacz:deagle", PISTOL, "tacz:50ae", 7), new Gun("tacz:deagle_golden", PISTOL, "tacz:357mag", 9),
		new Gun("tacz:fn_evolys", MG, "tacz:308", 75), new Gun("tacz:fn_fal", RIFLE, "tacz:308", 20),
		new Gun("tacz:g36k", RIFLE, "tacz:556x45", 30), new Gun("tacz:glock_17", PISTOL, "tacz:9mm", 17),
		new Gun("tacz:hk416d", RIFLE, "tacz:556x45", 30), new Gun("tacz:hk_g3", RIFLE, "tacz:308", 20),
		new Gun("tacz:hk_mk23", PISTOL, "tacz:45acp", 12), new Gun("tacz:hk_mp5a5", SMG, "tacz:9mm", 30),
		new Gun("tacz:kar98", SNIPER, "tacz:792x57", 4), new Gun("tacz:lonetrail", PISTOL, "tacz:30_06", 1),
		new Gun("tacz:m1014", SHOTGUN, "tacz:12g", 6), new Gun("tacz:m107", SNIPER, "tacz:50bmg", 10),
		new Gun("tacz:m16a1", RIFLE, "tacz:556x45", 20), new Gun("tacz:m16a4", RIFLE, "tacz:556x45", 30),
		new Gun("tacz:m1911", PISTOL, "tacz:45acp", 7), new Gun("tacz:m249", MG, "tacz:556x45", 75),
		new Gun("tacz:m4a1", RIFLE, "tacz:556x45", 30), new Gun("tacz:m700", SNIPER, "tacz:30_06", 5),
		new Gun("tacz:m870", SHOTGUN, "tacz:12g", 5), new Gun("tacz:m95", SNIPER, "tacz:50bmg", 5),
		new Gun("tacz:m9a4", PISTOL, "tacz:9mm", 17), new Gun("tacz:mk14", RIFLE, "tacz:308", 10),
		new Gun("tacz:p320", PISTOL, "tacz:45acp", 12), new Gun("tacz:p90", SMG, "tacz:57x28", 50),
		new Gun("tacz:qbz_191", RIFLE, "tacz:58x42", 30), new Gun("tacz:qbz_95", RIFLE, "tacz:58x42", 30),
		new Gun("tacz:rhino357", PISTOL, "tacz:357mag", 6), new Gun("tacz:rpk", MG, "tacz:762x39", 40),
		new Gun("tacz:scar_h", RIFLE, "tacz:308", 20), new Gun("tacz:scar_l", RIFLE, "tacz:556x45", 30),
		new Gun("tacz:sks_tactical", RIFLE, "tacz:762x39", 10), new Gun("tacz:spas_12", SHOTGUN, "tacz:12g", 5),
		new Gun("tacz:spr15hb", RIFLE, "tacz:556x45", 15), new Gun("tacz:springfield1873", SNIPER, "tacz:45_70", 1),
		new Gun("tacz:taurus500", PISTOL, "tacz:500mag", 5), new Gun("tacz:taurus943", PISTOL, "tacz:22wmr", 8),
		new Gun("tacz:timeless50", PISTOL, "tacz:50ae", 8), new Gun("tacz:type_81", RIFLE, "tacz:762x39", 30),
		new Gun("tacz:ump45", SMG, "tacz:45acp", 25), new Gun("tacz:uzi", SMG, "tacz:9mm", 20),
		new Gun("tacz:vector45", SMG, "tacz:45acp", 20)};
	static final Gun[] FLINTLOCKS = {new Gun("jaymospowers:musket", MUSKET, "", 1),
		new Gun("jaymospowers:flintlock_pistol", FLINTLOCK_PISTOL, "", 1), new Gun("jaymospowers:blunderbuss", BLUNDERBUSS, "", 1)};
	static final Map<String, Gun> BY_ID = new HashMap<>();

	static {
		for (Gun gun : MODERN) {
			BY_ID.put(gun.id, gun);
		}
		for (Gun gun : FLINTLOCKS) {
			BY_ID.put(gun.id, gun);
		}
	}

	/** Nobody fires at point blank (they'd rather swing). */
	static final double NEAREST = 2.5;
	/** The chance a gun drops from a dead gunner, and the chance it leaves rounds for it (this many, at most). */
	static final float DROP_CHANCE = 0.1F, AMMO_CHANCE = 0.35F;
	static final int AMMO_MIN = 4, AMMO_MAX = 24;
	/** How far TaCZ's report of a mob's shot carries. */
	static final int SOUND_RANGE = 96;

	/** When each gunner may next fire, and how many shots are left in its burst. */
	private static final Map<Mob, Long> NEXT = new WeakHashMap<>();
	private static final Map<Mob, Integer> BURST = new WeakHashMap<>();
	/** The gun each gunner was handed (a gun that drops leaves its hand before the ammo is thought of). */
	private static final Map<Mob, Gun> ARMED = new WeakHashMap<>();
	private static @Nullable Method sendSound;
	private static boolean soundLooked;

	private FutureGuns() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && mob.entityTags().contains(TAG) && mob.level() instanceof ServerLevel level) {
				dropAmmo(level, mob);
			}
		});
	}

	/** The gun a mob holds, if it's one we know. */
	static @Nullable Gun held(Mob mob) {
		return BY_ID.get(TaczGuns.gunId(mob.getMainHandItem()));
	}

	/** Hands a mob a random modern gun, loaded (nothing without TaCZ). Returns whether it's armed. */
	public static boolean arm(Mob mob, RandomSource random) {
		Gun pick = MODERN[random.nextInt(MODERN.length)];
		ItemStack gun = TaczGuns.modernGun(pick.id.substring("tacz:".length()), pick.magazine);
		if (gun.isEmpty()) {
			return false;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, gun);
		mob.setDropChance(EquipmentSlot.MAINHAND, DROP_CHANCE);
		mob.addTag(TAG);
		ARMED.put(mob, pick);
		NEXT.put(mob, mob.level().getGameTime() + 20 + random.nextInt(60));
		return true;
	}

	/** Some rounds for the gun it carried, now and then. */
	private static void dropAmmo(ServerLevel level, Mob mob) {
		Gun gun = ARMED.containsKey(mob) ? ARMED.get(mob) : held(mob);
		RandomSource random = level.getRandom();
		if (gun == null || gun.ammo.isEmpty() || random.nextFloat() >= AMMO_CHANCE) {
			return;
		}
		ItemStack ammo = TaczGuns.modernAmmo(gun.ammo, AMMO_MIN + random.nextInt(AMMO_MAX - AMMO_MIN + 1));
		if (!ammo.isEmpty()) {
			mob.spawnAtLocation(level, ammo);
		}
	}

	/** Every couple of ticks for a gunner: fire at its target when loaded, in range and in sight, a burst at a time. */
	public static void tick(ServerLevel level, Mob mob) {
		Gun gun = held(mob);
		LivingEntity foe = mob.getTarget();
		if (gun == null || foe == null || !foe.isAlive()) {
			BURST.remove(mob);
			return;
		}
		long now = level.getGameTime();
		if (now < NEXT.getOrDefault(mob, 0L)) {
			return;
		}
		Kind kind = gun.kind;
		double d = mob.distanceTo(foe);
		if (d < NEAREST || d > kind.range || !mob.hasLineOfSight(foe)) {
			BURST.remove(mob);
			return;
		}
		int left = BURST.getOrDefault(mob, kind.burst) - 1;
		if (left > 0) {
			BURST.put(mob, left);
			NEXT.put(mob, now + kind.gap);
		} else {
			BURST.remove(mob);
			NEXT.put(mob, now + kind.reload + level.getRandom().nextInt(30));
		}
		fire(level, mob, foe, gun);
	}

	private static void fire(ServerLevel level, Mob mob, LivingEntity foe, Gun gun) {
		RandomSource random = level.getRandom();
		Kind kind = gun.kind;
		boolean flintlock = !gun.id.startsWith("tacz:");
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		Vec3 eye = mob.getEyePosition();
		Vec3 aim = foe.getBoundingBox().getCenter().subtract(eye).normalize();
		Vec3 muzzle = eye.add(aim.scale(0.9));
		for (int i = 0; i < kind.balls; i++) {
			Vec3 dir = stray(aim, kind.spread, random);
			Vec3 end = eye.add(dir.scale(kind.range));
			HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
			if (wall.getType() != HitResult.Type.MISS) {
				end = wall.getLocation();
			}
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(mob, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != mob, eye.distanceToSqr(end));
			if (hit != null) {
				end = hit.getLocation();
				hit.getEntity().hurtServer(level, level.damageSources().mobProjectile(mob, mob), kind.damage);
			} else if (wall.getType() != HitResult.Type.MISS) {
				level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 3, 0.05, 0.05, 0.05, 0.1);
			}
			if (flintlock) {
				// the smoke trail of the ball
				double len = end.distanceTo(muzzle);
				for (double t = 0.0; t < len; t += 1.5) {
					Vec3 p = muzzle.add(dir.scale(t));
					level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
		if (flintlock) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
			level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.01);
		} else {
			level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.03, 0.03, 0.01);
		}
		if (!report(mob, gun)) {
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
				kind == BLUNDERBUSS || kind == SHOTGUN ? 1.2F : 0.9F, (kind == FLINTLOCK_PISTOL || kind == PISTOL ? 1.9F : 1.6F) + random.nextFloat() * 0.2F);
		}
	}

	/** The gun's own third-person shot sound, through TaCZ (by reflection). Returns false if that couldn't be done. */
	private static boolean report(Mob mob, Gun gun) {
		if (!soundLooked) {
			soundLooked = true;
			try {
				sendSound = Class.forName("com.tacz.guns.sound.SoundManager").getMethod("sendSoundToNearby", LivingEntity.class,
					int.class, Identifier.class, Identifier.class, String.class, float.class, float.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				sendSound = null;
			}
		}
		if (sendSound == null) {
			return false;
		}
		Identifier id = Identifier.parse(gun.id);
		try {
			sendSound.invoke(null, mob, SOUND_RANGE, id, id.withSuffix("_display"), "shoot_3p", 0.8F, 0.95F + mob.getRandom().nextFloat() * 0.1F);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			sendSound = null;
			return false;
		}
	}

	private static Vec3 stray(Vec3 aim, float degrees, RandomSource random) {
		double s = Math.toRadians(degrees);
		return aim.add(random.nextGaussian() * s, random.nextGaussian() * s, random.nextGaussian() * s).normalize();
	}
}
