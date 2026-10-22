package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.blood.BloodParticles;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Blood (Andy): every blade's blow sprays it, on every weapon, held or thrown ({@link Dismember#blade}), and the powers'
 * own blades call {@link #splash} for theirs ({@link Dismember#cut} does for every power's cut). Harder blows spray
 * more. Only a creature crushed to death bursts ({@link #crushed}: Gravity's weight, a meteor, a wall it's slammed
 * into, the Great Ape's stomp), never a blade's kill. Nothing that has no blood ({@link #BLOODLESS}: bones, golems,
 * spirits) bleeds.
 * <p>(0.15.91) The blood is Iron's Simple Blood's ({@link BloodParticles}): drops, spatters and swipes that fly, fall and
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
	/** (0.15.91) A blunt blow this heavy (health points) or more splatters, with this many times a blade's drops. */
	static final float HEAVY_BLUNT = 6.0F, BLUNT_DROPS = 1.5F;
	/**
	 * (0.15.91) A heavy blunt blow's pools: one, plus one for every this much damage over {@link #HEAVY_BLUNT}, at most this
	 * many; each this big, plus this much per point over, at most this big (1 = a block and a half across).
	 */
	static final float POOL_PER_DAMAGE = 5.0F, POOL_SCALE = 0.9F, POOL_GROWTH = 0.12F, POOL_MAX = 3.0F;
	static final int POOLS_MAX = 5;
	/** (0.15.91) An open cut's drip: this many drops (a stump's more), this small. */
	static final int DRIP_DROPS = 1, STUMP_DRIP_DROPS = 2;
	static final float DRIP_SCALE = 0.5F;

	/** What has no blood to spill. */
	public static final TagKey<EntityType<?>> BLOODLESS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("bloodless"));
	/** (0.15.91) What bleeds another colour than red (Simple Blood's kinds). */
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
			blunt(target, damage);
			return;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && Dismember.blade(weapon)) {
			splash(target, damage);
		}
	}

	/** (0.15.91) A falling anvil or block, a mace's smash, or a blunt weapon ({@link Fractures#blunt}) hitting this hard. */
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
		return !target.getType().builtInRegistryHolder().is(BLOODLESS);
	}

	/** (0.15.91) {@code target}'s blood colour (RGB). */
	public static int color(LivingEntity target) {
		var type = target.getType().builtInRegistryHolder();
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
		return true;
	}

	/** How big {@code target}'s drops and pools are (a man's are 1; Simple Blood's rule, by its width). */
	private static float size(LivingEntity target) {
		return (target.getBbWidth() + 2.0F) / 3.0F;
	}

	/**
	 * (0.15.91) A heavy blunt blow landed on {@code target} for {@code damage}: a spray half again a blade's, and pools
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
		double spread = target.getBbWidth() * 0.4;
		level.sendParticles(new BloodParticles.Puddle(color(target), scale), true, true, target.getX(), target.getY() + 0.1, target.getZ(),
			count, spread, 0.0, spread, 0.0);
	}

	/** (0.15.91) Wounds' open cut on {@code body} drips, from this far up it (a fraction of its height); a stump drips more. */
	public static void drip(ServerLevel level, LivingEntity body, double up, boolean stump) {
		if (!bleeds(body)) {
			return;
		}
		double w = body.getBbWidth() * 0.3;
		level.sendParticles(new BloodParticles.Spray(color(body), size(body) * DRIP_SCALE), true, false, body.getX(),
			body.getY() + body.getBbHeight() * up, body.getZ(), stump ? STUMP_DRIP_DROPS : DRIP_DROPS, w, 0.05, w, 0.01);
	}

	/** If {@code target} is dead (crushed: a blow just landed that only crushing does), it bursts. */
	public static void crushed(LivingEntity target) {
		if (!(target.level() instanceof ServerLevel level) || !target.isDeadOrDying() || !bleeds(target)
			|| target instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		Vec3 at = target.getBoundingBox().getCenter();
		double w = target.getBbWidth() * 0.5, h = target.getBbHeight() * 0.35;
		level.sendParticles(new BloodParticles.Spray(color(target), size(target)), true, true, at.x, at.y, at.z, BURST_DROPS, w, h, w,
			BURST_SPEED);
		pool(level, target, 2, BURST_POOL * size(target));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at.x, at.y, at.z,
			BURST_GORE, w, h, w, BURST_GORE_SPEED);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, 0.8F, 0.6F);
	}
}
