package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.wild.FedPredators;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The wyvern (after Mo' Creatures' wyvern, GPL-3, see {@code licenses/mocreatures}; its model and four of its hides are
 * the original's): the lesser, wilder kin of the wind dragons ({@link WindDragon}), two-legged and long in the wing,
 * that nests on the high crags of the harshest mountains ({@link Wyverns}) and hunts the herds of the high pastures.
 * Smaller than any wind dragon, so it tires sooner, and a beast, not a miniboss: no boss bar, and killing it counts
 * for nothing toward the wind dragons' disguises.
 * <ul>
 * <li><b>Wild:</b> a mother keeps to her nest and flies out over it; hungry, she stoops on sheep, goats, llamas and cattle
 * of the herds within sight ({@link #HUNT_RANGE}), and a kill feeds her for two days ({@code sated_predators}, see
 * {@link FedPredators}): fed, she leaves the flocks and players alone until she is hungry again, unless hurt. A herd that
 * keeps clear of her crag, and a farmer who keeps their flocks low, are never troubled.</li>
 * <li><b>Raised:</b> its egg ({@link Wyverns#EGG}) kept warm hatches a hatchling tamed to whoever hatched it, which grows
 * on meat (its favourite, cooked mutton, most) into a saddle-broken wyvern to ride ({@link WindDragon}'s riding and
 * stamina). A tamed one doesn't hunt the herds; it guards its keeper.</li>
 * <li><b>Breath:</b> a short spray of venom, not fire ({@link #VENOM}), that poisons.</li>
 * </ul>
 */
public class WyvernDragon extends WindDragon {
	static final double HEALTH = 110.0, ARMOR = 4.0, TOUGHNESS = 0.0, SPEED = 0.26, BITE = 7.0;
	/** Its wild cruise: a little quicker than the wind dragons' (0.55), as it is lighter. */
	static final double CRUISE = 0.62;
	/** Ridden: stamina a second flapping and gliding; the wyvern tires sooner than Eurus (1.1, 0.1). */
	static final float FLAP_DRAIN = 1.5F, GLIDE_DRAIN = 0.15F;
	/** Its riders' seat height (blocks), and where its mouth is on the ground and in the air. */
	static final double SEAT_HEIGHT = 1.5, MOUTH_AHEAD_GROUND = 2.4, MOUTH_UP_GROUND = 1.4, MOUTH_AHEAD_AIR = 2.6, MOUTH_UP_AIR = 0.9;
	/** Its venom: how far it reaches (blocks), how wide, what it does and how often, and how long it poisons. */
	static final double VENOM_RANGE = 12.0, VENOM_CONE = 0.9;
	static final float VENOM = 3.0F;
	static final int VENOM_EVERY = 5, VENOM_POISON = 100;
	/** Hunting: a wild one looks this far (blocks), once every so many ticks, for a flock or herd to stoop on. */
	static final double HUNT_RANGE = 28.0;
	static final int HUNT_EVERY = 20;

	public WyvernDragon(EntityType<? extends WyvernDragon> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.COOKED_MUTTON);
	}

	@Override
	protected boolean bossBar() {
		return false;
	}

	@Override
	protected boolean windDragon() {
		return false;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.GREEN;
	}

	@Override
	protected double cruiseSpeed() {
		return CRUISE;
	}

	@Override
	protected float flapDrain() {
		return FLAP_DRAIN;
	}

	@Override
	protected float glideDrain() {
		return GLIDE_DRAIN;
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return flying ? MOUTH_AHEAD_AIR : MOUTH_AHEAD_GROUND;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return flying ? MOUTH_UP_AIR : MOUTH_UP_GROUND;
	}

	@Override
	protected double breathRange() {
		return VENOM_RANGE;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, SEAT_HEIGHT, index == 0 ? 0.2 : -0.45);
	}

	/** A spray of venom: green motes along the aim, and whatever stands in it is bitten by it and poisoned. */
	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		for (int i = 0; i < 4; i++) {
			Vec3 d = DragonBreath.spread(aim, this.random, 0.14).scale(0.6 + this.random.nextDouble() * 0.9);
			level.sendParticles(ParticleTypes.ITEM_SLIME, mouth.x + d.x, mouth.y + d.y, mouth.z + d.z, 1, d.x * 0.1, d.y * 0.1, d.z * 0.1, 0.05);
		}
		if (this.tickCount % VENOM_EVERY != 0) {
			return;
		}
		AABB reach = new AABB(mouth, mouth.add(aim.scale(VENOM_RANGE))).inflate(2.0);
		List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, reach, this::spareable);
		for (LivingEntity e : hit) {
			Vec3 to = e.getEyePosition().subtract(mouth);
			if (to.length() < VENOM_RANGE + 1.0 && to.normalize().dot(aim) > VENOM_CONE && DragonBreath.clear(level, this, mouth, e.getEyePosition())) {
				e.hurtServer(level, level.damageSources().mobAttack(this), VENOM);
				e.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_POISON, 0), this);
			}
		}
	}

	/** A hungry wild one picks a quarry from the herds in sight. */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (!this.isTamed() && !this.isBaby() && !grounded() && this.getTarget() == null && this.tickCount % HUNT_EVERY == 0 && !FedPredators.fed(this)) {
			Animal prey = null;
			double best = HUNT_RANGE * HUNT_RANGE;
			for (Animal a : level.getEntitiesOfClass(Animal.class, this.getBoundingBox().inflate(HUNT_RANGE), WyvernDragon::quarry)) {
				double d = a.distanceToSqr(this);
				if (d < best && this.hasLineOfSight(a)) {
					best = d;
					prey = a;
				}
			}
			if (prey != null) {
				this.setTarget(prey);
			}
		}
		super.customServerAiStep(level);
	}

	/** The high pastures' herds: sheep, goats, llamas, cattle (not tamed horses, nor anything a player rides). */
	private static boolean quarry(Animal a) {
		return a.isAlive() && !a.isBaby() && a.getPassengers().isEmpty() && (a instanceof Sheep || a instanceof net.minecraft.world.entity.animal.goat.Goat
			|| a instanceof net.minecraft.world.entity.animal.equine.Llama llama && !llama.isTamed() || a instanceof net.minecraft.world.entity.animal.cow.Cow);
	}
}
