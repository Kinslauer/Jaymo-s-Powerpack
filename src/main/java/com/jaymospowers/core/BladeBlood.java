package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.blood.BloodParticles;
import com.jaymospowers.core.blood.BloodTrail;
import com.jaymospowers.core.blood.Bloodied;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Blood (Andy): every blade's blow sprays it, on every weapon, held or thrown ({@link Dismember#blade}), and the powers'
 * own blades call {@link #splash} for theirs ({@link Dismember#cut} does for every power's cut). Harder blows spray
 * more. Only a creature crushed to death bursts ({@link #crushed}: Gravity's weight, a meteor, a wall it's slammed
 * into, the Great Ape's stomp), never a blade's kill. Nothing that has no blood ({@link #BLOODLESS}: bones, golems,
 * spirits) bleeds.
 * <p>(0.15.93) The blood is Iron's Simple Blood's ({@link BloodParticles}): drops, spatters and swipes that fly, fall and
 * pool where they land, in the creature's own colour ({@link #color}: ender purple, dark for the deep things, slime
 * green, a bug's yellow). Andy: a heavy blunt blow ({@link #heavyBlunt}: a falling anvil or block, a mace's smash, a
 * hammer or club hitting for 3 hearts or more) splatters too, and pools under the body, more and wider the harder it
 * was. A blow that kills bleeds as well (it's noted in {@code ALLOW_DEATH}, as {@code AFTER_DAMAGE} never sees it).
 * Wounds' open cuts drip ({@link #drip}).
 * <p>(0.20.41, Hematic's ideas) Whoever bleeds is splashed with it, and so are the weapon that did it and whoever swung it
 * ({@link Bloodied}). Arrows and bullets bleed too ({@link #shot}): an arrow where it goes in, a bullet out the far side as
 * well, thrown on along its path. Magma creatures bleed glowing magma ({@link BloodParticles#MAGMA}) that cools on the ground.
 */
public final class BladeBlood {
	/** Red blood (Simple Blood's), and the other kinds' colours (see {@link #color}). */
	public static final int RED = 0x80000D, ENDER = 0x800D80, DARK = 0x231D21, SLIME = 0x76BE6D, BUG = 0xD8CB3A;
	/**
	 * (0.21.50) Andy: "all zombies should get the rotten blood": a zombie's dark, rotten blood. And the heritages' own
	 * (Jaymo's Kingdoms picks them): a Divine's gold, a Verdant's green sap.
	 */
	public static final int ROTTEN = 0x2E2410, GOLD = 0xC9A227, SAP = 0x4F8A1E;
	/**
	 * (0.20.41) A shot's blood: the drops where it went in (this many, plus this many per point of damage, at most this many),
	 * flying back toward the shooter this fast; and for a bullet out the far side, this many times as many, this fast, and
	 * stains laid along its path behind the body, up to this far (blocks).
	 */
	static final int SHOT_DROPS = 2, SHOT_DROPS_MAX = 8;
	static final float SHOT_DROPS_PER_DAMAGE = 0.3F, EXIT_MORE = 2.2F;
	static final double ENTRY_SPEED = 0.12, EXIT_SPEED = 0.45, EXIT_SPREAD = 0.18, EXIT_REACH = 3.0;
	static final float EXIT_STAIN = 0.3F;
	/** Drops a blow sprays: this many, plus this many per point of damage, at most this many. */
	static final int HIT_DROPS = 1, HIT_DROPS_MAX = 10;
	static final float DROPS_PER_DAMAGE = 0.3F;
	/**
	 * (0.20.51) Andy (2026-11-05): "the blood effects can be toned down, maybe emitting more as the enemy is lower on health".
	 * A blow's and a shot's blood is this many times the above on someone at full health, rising to this many times at none
	 * (downed or dying counts as none), along a curve this steep (1 = straight; more holds it low until they're well hurt).
	 */
	static final float FULL_HEALTH_BLOOD = 0.3F, NO_HEALTH_BLOOD = 1.2F, HEALTH_CURVE = 1.5F;
	/**
	 * (0.20.51) The badly hurt bleed as they go: under this share of their health, someone near a player drips now and then
	 * (checked every {@link #WOUNDED_EVERY} ticks, at most this chance, rising as they near none), from this far up them.
	 */
	static final float WOUNDED_HEALTH = 0.4F, WOUNDED_DRIP_CHANCE = 0.7F;
	static final int WOUNDED_EVERY = 20;
	static final double WOUNDED_RANGE = 32.0, WOUNDED_DRIP_FROM = 0.55;
	/** How fast the drops fly: this, plus this much for each drop in the spray. */
	static final double SPRAY_SPEED = 0.09, SPEED_PER_DROP = 0.005;
	/** Crushed to death: this many drops this fast, a pool this big, and this many flecks of gore thrown out, this fast. */
	static final int BURST_DROPS = 26, BURST_GORE = 28;
	static final double BURST_SPEED = 0.22, BURST_GORE_SPEED = 0.35;
	static final float BURST_POOL = 3.0F;
	/** (0.15.128) Blown apart by a blast: this many drops this fast, this many pools (each a burst's), this much gore this fast. */
	static final int BLAST_DROPS = 40, BLAST_POOLS = 3, BLAST_GORE = 40;
	static final double BLAST_SPEED = 0.35, BLAST_GORE_SPEED = 0.55;
	/** (0.15.93) A blunt blow this heavy (health points) or more splatters, with this many times a blade's drops. */
	static final float HEAVY_BLUNT = 6.0F, BLUNT_DROPS = 1.5F;
	/**
	 * (0.15.93) A heavy blunt blow's pools: one, plus one for every this much damage over {@link #HEAVY_BLUNT}, at most this
	 * many; each this big, plus this much per point over, at most this big (1 = a block and a half across).
	 */
	static final float POOL_PER_DAMAGE = 5.0F, POOL_SCALE = 0.9F, POOL_GROWTH = 0.12F, POOL_MAX = 3.0F;
	static final int POOLS_MAX = 5;
	/** (0.15.93) An open cut's drip: this many drops (a stump's more), this small. */
	static final int DRIP_DROPS = 1, STUMP_DRIP_DROPS = 2;
	static final float DRIP_SCALE = 0.5F;
	/** (0.20.86) How far outside the body's box a drip leaves from (blocks). */
	static final double DRIP_OUT = 0.04;
	/**
	 * (0.17.182) The lasting stains ({@link BloodTrail}): a drip's (grows where a body keeps bleeding), a blow's (one for
	 * every this many drops, at most this many), and how long a drop takes to reach the ground before its stain shows (ticks).
	 */
	static final float DRIP_STAIN = 0.2F, HIT_STAIN = 0.35F;
	static final int DROPS_PER_STAIN = 5, HIT_STAINS_MAX = 3, FALL_TICKS = 8;
	/**
	 * (0.15.111) A blow on a corpse (Jaymo's Kitchen's, see {@link #corpse}): a blade's sprays this many times a living
	 * body's drops and throws this many flecks per point of damage (at most this many); any other blow only spatters,
	 * with this many flecks per point (at least one). Flecks fly this fast and are this big.
	 */
	static final float CORPSE_BLADE_DROPS = 1.5F, CORPSE_BLADE_FLECKS = 1.2F, CORPSE_FLECKS = 0.5F, CORPSE_FLECK_SPEED = 0.25F,
		CORPSE_FLECK_SIZE = 1.4F;
	static final int CORPSE_FLECKS_MAX = 16;

	/** What has no blood to spill. */
	public static final TagKey<EntityType<?>> BLOODLESS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("bloodless"));
	/** (0.15.93) What bleeds another colour than red (Simple Blood's kinds). */
	public static final TagKey<EntityType<?>> ENDER_BLOOD = tag("blood/ender"), DARK_BLOOD = tag("blood/dark"),
		SLIME_BLOOD = tag("blood/slime"), BUG_BLOOD = tag("blood/bug"), MAGMA_BLOOD = tag("blood/magma"), ROTTEN_BLOOD = tag("blood/rotten");

	/** Who bled this tick (the game time): a power's blade blow that also reads as a held blade bleeds only once. */
	private static final Map<LivingEntity, Long> SPLASHED = new WeakHashMap<>();

	private BladeBlood() {
	}

	public static void init() {
		BloodParticles.init();
		// (0.20.41) blood on bodies, armour and weapons
		Bloodied.init();
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BladeBlood::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register((target, source, damage) -> {
			hit(target, source, damage);
			return true;
		});
		// (0.20.51) the badly hurt drip as they go
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % WOUNDED_EVERY == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					wounded(level);
				}
			}
		});
	}

	/**
	 * (0.20.51) Andy: "skeletons currently drop bone pngs when hit, they should be debris splinter particles if anything".
	 * A skeleton (or skeleton horse) struck throws chips of bone (the bone block's crumbs): this many, plus this many per
	 * point of damage, at most this many, this fast. Subtle Effects' bone sprites are off ({@code SubtleEffectsCompat}).
	 */
	static final int SPLINTERS = 2, SPLINTERS_MAX = 10;
	static final float SPLINTERS_PER_DAMAGE = 0.6F;
	static final double SPLINTER_SPEED = 0.12;

	/** (0.20.51) {@code target}, if it's bones, splinters under a blow of {@code damage}. */
	static void splinters(LivingEntity target, float damage) {
		if (!(target.level() instanceof ServerLevel level) || !(target.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SKELETONS)
			|| target instanceof net.minecraft.world.entity.animal.equine.SkeletonHorse)) {
			return;
		}
		int count = Math.min(SPLINTERS_MAX, Mth.floor(SPLINTERS + damage * SPLINTERS_PER_DAMAGE));
		Vec3 at = target.getBoundingBox().getCenter();
		double w = target.getBbWidth() * 0.3;
		level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
			net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), at.x, at.y + target.getBbHeight() * 0.1, at.z, count, w,
			target.getBbHeight() * 0.2, w, SPLINTER_SPEED);
	}

	/**
	 * (0.20.51) How much blood a blow on {@code target} spills against a full one ({@link #FULL_HEALTH_BLOOD} at full
	 * health up to {@link #NO_HEALTH_BLOOD} at none), so how hurt they are shows in it.
	 */
	static float hurtShare(LivingEntity target) {
		float left = target.isDeadOrDying() || com.jaymospowers.core.wounds.Downed.isDowned(target) ? 0.0F
			: Mth.clamp(target.getHealth() / Math.max(target.getMaxHealth(), 1.0F), 0.0F, 1.0F);
		float hurt = 1.0F - (float)Math.pow(left, 1.0 / HEALTH_CURVE);
		return FULL_HEALTH_BLOOD + (NO_HEALTH_BLOOD - FULL_HEALTH_BLOOD) * hurt;
	}

	/** (0.20.51) Those near a player and under {@link #WOUNDED_HEALTH} drip, more often the nearer they are to none. */
	private static void wounded(ServerLevel level) {
		if (level.players().isEmpty()) {
			return;
		}
		java.util.Set<LivingEntity> seen = new java.util.HashSet<>();
		for (net.minecraft.server.level.ServerPlayer player : level.players()) {
			for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WOUNDED_RANGE), LivingEntity::isAlive)) {
				if (!seen.add(body) || !bleeds(body) || body.isInWater() || com.jaymospowers.core.wounds.Downed.isDowned(body)
					|| body instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) {
					continue;
				}
				float left = body.getHealth() / Math.max(body.getMaxHealth(), 1.0F);
				if (left < WOUNDED_HEALTH && level.getRandom().nextFloat() < WOUNDED_DRIP_CHANCE * (1.0F - left / WOUNDED_HEALTH)) {
					drip(level, body, WOUNDED_DRIP_FROM, false);
				}
			}
		}
	}

	private static TagKey<EntityType<?>> tag(String path) {
		return TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id(path));
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (!blocked) {
			hit(target, source, damage);
		}
	}

	private static void hit(LivingEntity target, DamageSource source, float damage) {
		if (damage <= 0.0F || source.getEntity() == target) {
			return;
		}
		// (0.20.51) bones splinter
		splinters(target, damage);
		if (heavyBlunt(source, damage)) {
			// (0.15.117) a killing one crushes a spider (Kitchen)
			Crushing.mark(target);
			if (blunt(target, damage)) {
				Bloodied.struck(target, source, damage, color(target));
			}
			return;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && Dismember.blade(weapon)) {
			if (spray(target, damage, 1.0F)) {
				Bloodied.struck(target, source, damage, color(target));
			}
			return;
		}
		// (0.20.41) an arrow or bullet (not a fireball or a snowball)
		if (source.getDirectEntity() instanceof Projectile projectile && projectile != source.getEntity() && pierces(projectile)) {
			Vec3 dir = projectile.getDeltaMovement();
			if (dir.lengthSqr() < 1.0E-6 && source.getEntity() != null) {
				dir = target.position().subtract(source.getEntity().position());
			}
			if (shot(target, dir, damage, bullet(projectile))) {
				Bloodied.struck(target, source, damage, color(target));
			}
		}
	}

	/** (0.20.41) A projectile that cuts into a body: not fire, ice, wind, spit or magic. */
	static boolean pierces(Projectile projectile) {
		String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).getPath();
		return !(path.contains("fire") || path.contains("snow") || path.contains("wind") || path.contains("spit") || path.contains("potion")
			|| path.contains("egg") || path.contains("pearl") || path.contains("skull") || path.contains("shulker") || path.contains("dragon")
			|| path.contains("experience") || path.contains("firework") || path.contains("orb") || path.contains("web") || path.contains("ki"));
	}

	/** (0.20.41) Whether {@code projectile} is a bullet (WeaponMod's, TaCZ's, any mod's shot or ball) rather than an arrow or bolt. */
	static boolean bullet(Projectile projectile) {
		String path = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).getPath();
		return path.contains("bullet") || path.contains("pellet") || path.contains("musket") || path.contains("buckshot")
			|| path.equals("shot") || path.endsWith("_shot") || path.contains("round");
	}

	/**
	 * (0.20.41) Andy liked Hematic's bullets leaving blood "and exit wounds along their path". {@code target} was shot along
	 * {@code dir} (the way the shot flew) for {@code damage}: drops spit back out where it went in, and for a bullet
	 * ({@code through}) a cone of them bursts out the far side and flies on, splashing whatever they hit (a wall behind
	 * gets its own splat, see the client's {@code BloodWallMark}), with stains laid on the ground along the path. Our own
	 * guns that hit without a projectile (the Void Armory's flintlocks, the future's guns) call it themselves. False if
	 * it didn't bleed.
	 */
	public static boolean shot(LivingEntity target, Vec3 dir, float damage, boolean through) {
		if (!(target.level() instanceof ServerLevel level) || damage <= 0.0F || !bleeds(target) || dir.lengthSqr() < 1.0E-8) {
			return false;
		}
		long now = level.getGameTime();
		Long last = SPLASHED.put(target, now);
		if (last != null && last == now) {
			return false;
		}
		Vec3 along = dir.normalize();
		int color = color(target);
		float size = size(target);
		Vec3 centre = target.getBoundingBox().getCenter().add(0.0, target.getBbHeight() * 0.15, 0.0);
		double half = target.getBbWidth() * 0.5;
		int drops = Math.max(1, Math.min(SHOT_DROPS_MAX, Mth.floor((SHOT_DROPS + damage * SHOT_DROPS_PER_DAMAGE) * hurtShare(target))));
		Vec3 entry = centre.subtract(along.scale(half));
		BloodParticles.Spray spray = new BloodParticles.Spray(color, size * 0.8F);
		for (int i = 0; i < drops; i++) {
			Vec3 v = along.scale(-ENTRY_SPEED).add(jitter(level, EXIT_SPREAD * 0.5));
			level.sendParticles(spray, true, true, entry.x, entry.y, entry.z, 0, v.x, v.y, v.z, 1.0);
		}
		if (through) {
			Vec3 exit = centre.add(along.scale(half + 0.05));
			int out = Math.min(Mth.floor(SHOT_DROPS_MAX * EXIT_MORE), Mth.floor(drops * EXIT_MORE));
			for (int i = 0; i < out; i++) {
				Vec3 v = along.scale(EXIT_SPEED * (0.6 + level.getRandom().nextDouble() * 0.6)).add(jitter(level, EXIT_SPREAD));
				level.sendParticles(spray, true, true, exit.x, exit.y, exit.z, 0, v.x, v.y, v.z, 1.0);
			}
			// the path behind: a stain or two along it where the drops come down
			for (double d = 1.0; d <= EXIT_REACH; d += 1.0 + level.getRandom().nextDouble()) {
				Vec3 at = exit.add(along.scale(d));
				BloodTrail.lay(level, null, color, at.x, at.y, at.z, EXIT_STAIN * size, FALL_TICKS);
			}
		}
		BloodTrail.bleed(level, target, color, 1, half, HIT_STAIN * size, FALL_TICKS);
		return true;
	}

	/** (0.20.41) {@link #shot} as a bullet, from {@code shooter}'s eyes along its look, for guns that hit without a projectile. */
	public static void shot(LivingEntity target, Entity shooter, float damage) {
		if (target.isAlive() || target.isDeadOrDying()) {
			Vec3 dir = target.getBoundingBox().getCenter().subtract(shooter.getEyePosition());
			if (shot(target, dir, damage, true)) {
				Bloodied.struck(target, null, damage, color(target));
			}
		}
	}

	private static Vec3 jitter(ServerLevel level, double spread) {
		var random = level.getRandom();
		return new Vec3((random.nextDouble() * 2.0 - 1.0) * spread, (random.nextDouble() * 2.0 - 1.0) * spread + spread * 0.5,
			(random.nextDouble() * 2.0 - 1.0) * spread);
	}

	/** (0.15.93) A falling anvil or block, a mace's smash, or a blunt weapon ({@link Fractures#blunt}) hitting this hard. */
	public static boolean heavyBlunt(DamageSource source, float damage) {
		if (damage < HEAVY_BLUNT) {
			return false;
		}
		if (source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.MACE_SMASH)) {
			return true;
		}
		ItemStack weapon = source.getWeaponItem();
		return weapon != null && Fractures.blunt(weapon);
	}

	/**
	 * (0.21.49) Andy: "make sure stoneborn and certain hollow don't bleed, and any other relevant race. (emberborn should have
	 * the magma blood)". A body's own blood, beyond its kind's: each answers {@link #NO_SAY}, {@link #NO_BLOOD} or an RGB
	 * colour. Jaymo's Kingdoms adds its heritages here ({@code PowersCompat}); the first that has a say decides.
	 */
	public static final List<java.util.function.ToIntFunction<LivingEntity>> BLOOD = new ArrayList<>();
	public static final int NO_SAY = Integer.MIN_VALUE, NO_BLOOD = -1;

	private static int own(LivingEntity target) {
		for (java.util.function.ToIntFunction<LivingEntity> blood : BLOOD) {
			int said = blood.applyAsInt(target);
			if (said != NO_SAY) {
				return said;
			}
		}
		return NO_SAY;
	}

	/** Whether {@code target} has blood to spill. */
	public static boolean bleeds(LivingEntity target) {
		int own = own(target);
		return own == NO_SAY ? bleeds(target.getType()) : own != NO_BLOOD;
	}

	/** (0.15.111) Whether this kind of creature has blood to spill. */
	public static boolean bleeds(EntityType<?> type) {
		return !type.builtInRegistryHolder().is(BLOODLESS);
	}

	/** (0.15.93) {@code target}'s blood colour (RGB). */
	public static int color(LivingEntity target) {
		int own = own(target);
		if (own == NO_SAY || own == NO_BLOOD) {
			// (0.21.50) any mod's zombie that is one in code bleeds rotten, tagged or not
			return target instanceof net.minecraft.world.entity.monster.zombie.Zombie ? ROTTEN : color(target.getType());
		}
		return own;
	}

	/**
	 * (0.21.50) Andy: corpses bleed the body's own blood. Whether the corpse {@code body} of a {@code type} has blood, by
	 * the blood it died with ({@code BleedOut.CORPSE_OWN}), or its kind's if it has none noted.
	 */
	public static boolean bleeds(Entity body, EntityType<?> type) {
		if (body instanceof LivingEntity living) {
			return bleeds(living);
		}
		Integer own = body.getAttached(com.jaymospowers.core.blood.BleedOut.CORPSE_OWN);
		return own != null ? own != NO_BLOOD : bleeds(type);
	}

	/** (0.21.50) The colour of the corpse {@code body}'s blood (see {@link #bleeds(Entity, EntityType)}). */
	public static int color(Entity body, EntityType<?> type) {
		if (body instanceof LivingEntity living) {
			return color(living);
		}
		Integer own = body.getAttached(com.jaymospowers.core.blood.BleedOut.CORPSE_OWN);
		return own != null && own != NO_BLOOD ? own : color(type);
	}

	/**
	 * (0.21.50) For Jaymo's Kitchen (looked up by reflection): the blood of {@code body}, a living one or one of Kitchen's
	 * corpses, as an RGB colour, or {@link #NO_BLOOD} if it has none (a skeleton in the chopper, a Stoneborn on the hook).
	 */
	public static int bodyBlood(Entity body) {
		if (body instanceof LivingEntity living) {
			return bleeds(living) ? color(living) : NO_BLOOD;
		}
		EntityType<?> type = com.jaymospowers.power.web.KitchenCompat.bodyType(body);
		if (type == null) {
			return RED;
		}
		return bleeds(body, type) ? color(body, type) : NO_BLOOD;
	}

	/** (0.15.111) This kind of creature's blood colour (RGB). */
	public static int color(EntityType<?> entityType) {
		var type = entityType.builtInRegistryHolder();
		if (type.is(ENDER_BLOOD)) {
			return ENDER;
		}
		// (0.21.50) magma before dark: a magma cube was in both and bled dark
		if (type.is(MAGMA_BLOOD)) {
			return BloodParticles.MAGMA;
		}
		if (type.is(ROTTEN_BLOOD)) {
			return ROTTEN;
		}
		if (type.is(DARK_BLOOD)) {
			return DARK;
		}
		if (type.is(SLIME_BLOOD)) {
			return SLIME;
		}
		return type.is(BUG_BLOOD) ? BUG : RED;
	}

	/** A blade's blow just landed on {@code target} for {@code damage}: blood, more the harder it was. */
	public static void splash(LivingEntity target, float damage) {
		if (spray(target, damage, 1.0F)) {
			// (0.20.41) a power's own blade: the bleeder is splashed (no weapon to bloody)
			Bloodied.struck(target, null, damage, color(target));
		}
	}

	/** Sprays {@code target}'s blood for a blow of {@code damage}, {@code more} times a blade's; false if it didn't bleed. */
	private static boolean spray(LivingEntity target, float damage, float more) {
		if (!(target.level() instanceof ServerLevel level) || damage <= 0.0F || !bleeds(target)) {
			return false;
		}
		long now = level.getGameTime();
		Long last = SPLASHED.put(target, now);
		if (last != null && last == now) {
			return false;
		}
		int drops = Math.max(1, Math.min(HIT_DROPS_MAX, Mth.floor((HIT_DROPS + damage * DROPS_PER_DAMAGE) * more * hurtShare(target))));
		Vec3 at = target.getBoundingBox().getCenter();
		double w = Math.max(target.getBbWidth() * 0.5 - 0.5, 0.0) + 0.05;
		level.sendParticles(new BloodParticles.Spray(color(target), size(target)), true, true, at.x, at.y + target.getBbHeight() * 0.25, at.z,
			drops, w, 0.1, w, SPRAY_SPEED + drops * SPEED_PER_DROP);
		BloodTrail.bleed(level, target, color(target), Math.min(HIT_STAINS_MAX, 1 + drops / DROPS_PER_STAIN), w + 0.4,
			HIT_STAIN * size(target), FALL_TICKS);
		return true;
	}

	/** How big {@code target}'s drops and pools are (a man's are 1; Simple Blood's rule, by its width). */
	private static float size(LivingEntity target) {
		return (target.getBbWidth() + 2.0F) / 3.0F;
	}

	/**
	 * (0.15.93) A heavy blunt blow landed on {@code target} for {@code damage}: a spray half again a blade's, and pools
	 * under it, more and wider the harder it was.
	 */
	public static boolean blunt(LivingEntity target, float damage) {
		if (!spray(target, damage, BLUNT_DROPS)) {
			return false;
		}
		float over = Math.max(damage - HEAVY_BLUNT, 0.0F);
		int pools = Math.min(POOLS_MAX, 1 + Mth.floor(over / POOL_PER_DAMAGE));
		float scale = Math.min(POOL_MAX, POOL_SCALE + over * POOL_GROWTH) * size(target);
		pool((ServerLevel) target.level(), target, pools, scale);
		return true;
	}

	/** Lays {@code count} pools of {@code target}'s blood, this big, on the ground around its feet. */
	private static void pool(ServerLevel level, LivingEntity target, int count, float scale) {
		BloodTrail.bleed(level, target, color(target), count, target.getBbWidth() * 0.4, scale, 0);
	}

	/** (0.15.93) Wounds' open cut on {@code body} drips, from this far up it (a fraction of its height); a stump drips more. */
	public static void drip(ServerLevel level, LivingEntity body, double up, boolean stump) {
		if (!bleeds(body)) {
			return;
		}
		double w = body.getBbWidth() * 0.3;
		int drops = stump ? STUMP_DRIP_DROPS : DRIP_DROPS;
		// (0.17.182) the drops fly as before but leave no passing splash: each lands as a lasting stain (BloodTrail)
		// (0.20.86) each drop leaves from the body's side, not from inside it (Jung's clip: drips spawned in the chest
		// read as blood clipping out of the model)
		BloodParticles.Spray spray = new BloodParticles.Spray(color(body), size(body) * DRIP_SCALE, false);
		double out = body.getBbWidth() * 0.5 + DRIP_OUT;
		for (int i = 0; i < drops; i++) {
			double turn = level.getRandom().nextDouble() * Math.PI * 2.0;
			double y = body.getY() + body.getBbHeight() * up + (level.getRandom().nextDouble() - 0.5) * 0.1;
			level.sendParticles(spray, true, false, body.getX() + Math.cos(turn) * out, y, body.getZ() + Math.sin(turn) * out, 1, 0.0, 0.0, 0.0,
				0.0);
		}
		BloodTrail.bleed(level, body, color(body), drops, w, DRIP_STAIN * size(body), FALL_TICKS);
	}

	/** A dead body hitting the ground or a wall this hard (blocks a tick) splashes; past the second, as hard as it gets. */
	public static final double IMPACT_FROM = 0.4, IMPACT_FULL = 1.2;
	/** An impact's stain at its softest and hardest (stain scale), and drops thrown up at the hardest. */
	public static final float IMPACT_STAIN_MIN = 0.3F, IMPACT_STAIN_MAX = 0.8F;
	public static final int IMPACT_DROPS = 6;

	/**
	 * Andy's Cruor pick: a ragdolling body (Jaymo's Kitchen's corpse) that hits the ground or a wall hard leaves a smear
	 * where it struck, and throws a few drops (they splat on any wall they fly into). {@code lost} is the speed it lost.
	 */
	public static void impact(ServerLevel level, Entity body, int color, double x, double y, double z, double lost) {
		if (lost < IMPACT_FROM || color == NO_BLOOD) {
			return;
		}
		float k = (float) Mth.clamp((lost - IMPACT_FROM) / (IMPACT_FULL - IMPACT_FROM), 0.0, 1.0);
		BloodTrail.blot(level, body, color, x, y + 0.3, z, Mth.lerp(k, IMPACT_STAIN_MIN, IMPACT_STAIN_MAX));
		int drops = 1 + Math.round(k * (IMPACT_DROPS - 1));
		BloodParticles.Spray spray = new BloodParticles.Spray(color, 0.4F + 0.3F * k, false);
		level.sendParticles(spray, true, false, x, y + 0.25, z, drops, 0.25, 0.1, 0.25, 0.15 + 0.15 * k);
	}

	/**
	 * (0.15.111) Andy: hitting a body on the ground splatters its blood, a blade most of all. {@code corpse} is Jaymo's
	 * Kitchen's body of a {@code type} (Kitchen only asks for one with flesh on it), hit by {@code source} for
	 * {@code damage}. A blade sprays drops that fly, fall and pool, and flecks; a heavy blunt blow sprays and pools like
	 * one on the living ({@link #heavyBlunt}); anything else (a fist, an arrow) only throws a few flecks.
	 */
	public static void corpse(Entity corpse, EntityType<?> type, DamageSource source, float damage) {
		if (!(corpse.level() instanceof ServerLevel level) || damage <= 0.0F || !bleeds(corpse, type)) {
			return;
		}
		int color = color(corpse, type);
		float size = (corpse.getBbWidth() + 2.0F) / 3.0F;
		Vec3 at = corpse.getBoundingBox().getCenter();
		double w = corpse.getBbWidth() * 0.35 + 0.05;
		ItemStack weapon = source.getWeaponItem();
		boolean blade = weapon != null && Dismember.blade(weapon);
		boolean heavy = !blade && heavyBlunt(source, damage);
		if (blade || heavy) {
			int drops = Math.min(HIT_DROPS_MAX, Mth.floor((HIT_DROPS + damage * DROPS_PER_DAMAGE) * (blade ? CORPSE_BLADE_DROPS : BLUNT_DROPS)));
			level.sendParticles(new BloodParticles.Spray(color, size), true, true, at.x, at.y + 0.1, at.z, drops, w, 0.05, w,
				SPRAY_SPEED + drops * SPEED_PER_DROP);
		}
		if (heavy) {
			float over = Math.max(damage - HEAVY_BLUNT, 0.0F);
			BloodTrail.bleed(level, corpse, color, Math.min(POOLS_MAX, 1 + Mth.floor(over / POOL_PER_DAMAGE)), w,
				Math.min(POOL_MAX, POOL_SCALE + over * POOL_GROWTH) * size, 0);
		}
		int flecks = Math.min(CORPSE_FLECKS_MAX, Math.max(1, Mth.floor(damage * (blade ? CORPSE_BLADE_FLECKS : CORPSE_FLECKS))));
		level.sendParticles(BloodParticles.fleck(color, CORPSE_FLECK_SIZE), at.x, at.y + 0.1, at.z, flecks, w, 0.05, w, CORPSE_FLECK_SPEED);
		if (blade) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_STEP, SoundSource.PLAYERS, 0.6F, 0.7F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	/** If {@code target} is dead (crushed: a blow just landed that only crushing does), it bursts. */
	public static void crushed(LivingEntity target) {
		Crushing.mark(target);
		if (!(target.level() instanceof ServerLevel level) || !target.isDeadOrDying() || !bleeds(target)
			|| target instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		Vec3 at = target.getBoundingBox().getCenter();
		double w = target.getBbWidth() * 0.5, h = target.getBbHeight() * 0.35;
		level.sendParticles(new BloodParticles.Spray(color(target), size(target)), true, true, at.x, at.y, at.z, BURST_DROPS, w, h, w,
			BURST_SPEED);
		pool(level, target, 2, BURST_POOL * size(target));
		level.sendParticles(BloodParticles.fleck(color(target), 2.0F), at.x, at.y, at.z,
			BURST_GORE, w, h, w, BURST_GORE_SPEED);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	/**
	 * (0.15.128) Andy: explosions "should blast other corpses apart into a mess". {@code body} (a creature of
	 * {@code type}, or Jaymo's Kitchen's body of one) was just blown apart by a blast (Kitchen throws the chunks): its blood
	 * bursts out farther than a crushing's, with pools and gore flung wide.
	 */
	public static void blownApart(Entity body, EntityType<?> type) {
		if (!(body.level() instanceof ServerLevel level) || !bleeds(body, type)) {
			return;
		}
		int color = color(body, type);
		float size = (type.getWidth() + 2.0F) / 3.0F;
		double w = type.getWidth() * 0.6 + 0.2, h = type.getHeight() * 0.3 + 0.1;
		double x = body.getX(), y = body.getY() + type.getHeight() * 0.4, z = body.getZ();
		level.sendParticles(new BloodParticles.Spray(color, size), true, true, x, y, z, BLAST_DROPS, w, h, w, BLAST_SPEED);
		BloodTrail.bleed(level, body, color, BLAST_POOLS, w * 1.5, BURST_POOL * size, 0);
		level.sendParticles(BloodParticles.fleck(color, 2.0F), x, y, z, BLAST_GORE, w, h, w, BLAST_GORE_SPEED);
	}
}
