package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.blood.BloodParticles;
import com.jaymospowers.core.blood.BloodTrail;
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
 */
public final class BladeBlood {
	/** Red blood (Simple Blood's), and the other kinds' colours (see {@link #color}). */
	static final int RED = 0x80000D, ENDER = 0x800D80, DARK = 0x231D21, SLIME = 0x76BE6D, BUG = 0xD8CB3A;
	/** Drops a blow sprays: this many, plus this many per point of damage, at most this many. */
	static final int HIT_DROPS = 1, HIT_DROPS_MAX = 14;
	static final float DROPS_PER_DAMAGE = 0.35F;
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
		SLIME_BLOOD = tag("blood/slime"), BUG_BLOOD = tag("blood/bug");

	/** Who bled this tick (the game time): a power's blade blow that also reads as a held blade bleeds only once. */
	private static final Map<LivingEntity, Long> SPLASHED = new WeakHashMap<>();

	private BladeBlood() {
	}

	public static void init() {
		BloodParticles.init();
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BladeBlood::afterDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register((target, source, damage) -> {
			hit(target, source, damage);
			return true;
		});
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
		if (heavyBlunt(source, damage)) {
			// (0.15.117) a killing one crushes a spider (Kitchen)
			Crushing.mark(target);
			blunt(target, damage);
			return;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && Dismember.blade(weapon)) {
			splash(target, damage);
		}
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

	/** Whether {@code target} has blood to spill. */
	public static boolean bleeds(LivingEntity target) {
		return bleeds(target.getType());
	}

	/** (0.15.111) Whether this kind of creature has blood to spill. */
	public static boolean bleeds(EntityType<?> type) {
		return !type.builtInRegistryHolder().is(BLOODLESS);
	}

	/** (0.15.93) {@code target}'s blood colour (RGB). */
	public static int color(LivingEntity target) {
		return color(target.getType());
	}

	/** (0.15.111) This kind of creature's blood colour (RGB). */
	public static int color(EntityType<?> entityType) {
		var type = entityType.builtInRegistryHolder();
		if (type.is(ENDER_BLOOD)) {
			return ENDER;
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
		spray(target, damage, 1.0F);
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
		int drops = Math.min(HIT_DROPS_MAX, Mth.floor((HIT_DROPS + damage * DROPS_PER_DAMAGE) * more));
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
	public static void blunt(LivingEntity target, float damage) {
		if (!spray(target, damage, BLUNT_DROPS)) {
			return;
		}
		float over = Math.max(damage - HEAVY_BLUNT, 0.0F);
		int pools = Math.min(POOLS_MAX, 1 + Mth.floor(over / POOL_PER_DAMAGE));
		float scale = Math.min(POOL_MAX, POOL_SCALE + over * POOL_GROWTH) * size(target);
		pool((ServerLevel) target.level(), target, pools, scale);
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
		level.sendParticles(new BloodParticles.Spray(color(body), size(body) * DRIP_SCALE, false), true, false, body.getX(),
			body.getY() + body.getBbHeight() * up, body.getZ(), drops, w, 0.05, w, 0.01);
		BloodTrail.bleed(level, body, color(body), drops, w, DRIP_STAIN * size(body), FALL_TICKS);
	}

	/**
	 * (0.15.111) Andy: hitting a body on the ground splatters its blood, a blade most of all. {@code corpse} is Jaymo's
	 * Kitchen's body of a {@code type} (Kitchen only asks for one with flesh on it), hit by {@code source} for
	 * {@code damage}. A blade sprays drops that fly, fall and pool, and flecks; a heavy blunt blow sprays and pools like
	 * one on the living ({@link #heavyBlunt}); anything else (a fist, an arrow) only throws a few flecks.
	 */
	public static void corpse(Entity corpse, EntityType<?> type, DamageSource source, float damage) {
		if (!(corpse.level() instanceof ServerLevel level) || damage <= 0.0F || !bleeds(type)) {
			return;
		}
		int color = color(type);
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
		if (!(body.level() instanceof ServerLevel level) || !bleeds(type)) {
			return;
		}
		int color = color(type);
		float size = (type.getWidth() + 2.0F) / 3.0F;
		double w = type.getWidth() * 0.6 + 0.2, h = type.getHeight() * 0.3 + 0.1;
		double x = body.getX(), y = body.getY() + type.getHeight() * 0.4, z = body.getZ();
		level.sendParticles(new BloodParticles.Spray(color, size), true, true, x, y, z, BLAST_DROPS, w, h, w, BLAST_SPEED);
		BloodTrail.bleed(level, body, color, BLAST_POOLS, w * 1.5, BURST_POOL * size, 0);
		level.sendParticles(BloodParticles.fleck(color, 2.0F), x, y, z, BLAST_GORE, w, h, w, BLAST_GORE_SPEED);
	}
}
