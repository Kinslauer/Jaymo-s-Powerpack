package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.PowersCompat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.141) Eurus, the east wind: the fourth of the five wind dragons ({@link WindDragon}). The storm dragon: the fastest
 * flier of them and the weakest in the hide, lean and long-winged. It has no home biome and never spawns by itself: it
 * walks the world disguised as a villager ({@link Disguises}) and shows itself to a player who has beaten two other
 * dragons. A wild one that has lost its foe, with no home to go back to, soon flies off high and is gone. Its breath is a
 * bolt of lightning that arcs from one creature to the next ({@link DragonStorm}; it never burns blocks, though in a
 * thunderstorm a wild one's bolt may call down a real one). It flies as freely in rain and thunder as in clear skies, and
 * in a storm a wild one is faster still; a Wind user rides it faster, most of all in the rain. Its favourite is cooked
 * rabbit; Stormborn win it over twice as fast.
 */
public class EurusDragon extends WindDragon {
	// ---- body ----
	/** The weakest hide of the five (Zephyrus: 190, 8, 2). */
	static final double HEALTH = 160.0, ARMOR = 6.0, TOUGHNESS = 1.0, SPEED = 0.28, BITE = 9.0;
	/** How fast it cruises and dives wild: the fastest of the five (Notus: 0.55 and 1.15; Boreas 0.7 and 1.35). */
	static final double CRUISE = 0.9, SWOOP = 1.65;
	/** Wild in rain or thunder, its cruise and dive are this many times faster. */
	static final double STORM_BOOST = 1.25;
	/** Ridden: stamina a second flapping (Notus 1.6) and gliding (Notus 0.25). */
	static final float FLAP_DRAIN = 1.1F, GLIDE_DRAIN = 0.1F;
	/** How loud its wingbeats are (Notus 2): sharp and quick. */
	static final float FLAP_VOLUME = 1.0F;
	/** Its riders' seat height (blocks): (0.7.329) the Kintsugi wyvern's back, drawn at 0.28. */
	static final double SEAT_HEIGHT = 1.65;
	/** Its mouth, on the ground and in the air: how far ahead of its middle, and how high above its feet (0.7.329: the wyvern's). */
	static final double MOUTH_AHEAD_GROUND = 1.7, MOUTH_UP_GROUND = 1.5, MOUTH_AHEAD_AIR = 1.9, MOUTH_UP_AIR = 1.0;
	/** How much faster a Wind user flies it: in rain or thunder, and in clear weather. */
	static final float STORM_RIDER = 1.3F, CLEAR_RIDER = 1.15F;

	// ---- leaving ----
	/** A wild one that has had no foe this long (ticks) gives up and flies off. */
	static final int LEAVE_AFTER = 1200;
	/** Leaving, it climbs and flees at this many times its cruise, and is gone after this many ticks or this far above where it set off (blocks). */
	static final double LEAVE_SPEED = 1.4, LEAVE_CLIMB = 14.0, LEAVE_HEIGHT = 70.0;
	static final int LEAVE_TICKS = 400;

	/** Ticks a wild one has been without a foe (the base class's fight resets it). */
	private int idle;
	/** Whether it's flying off, how long it has been, and where from (height) and which way (a unit vector across the ground). */
	private boolean leaving;
	private int leavingFor;
	private double leaveFrom;
	private Vec3 leaveWay = Vec3.ZERO;

	public EurusDragon(EntityType<? extends EurusDragon> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.COOKED_RABBIT);
	}

	/** Stormborn (the closest heritage to the sky and the storm) win it over twice as fast. */
	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.STORMBORN) ? BOND_FACTOR : 1.0F;
	}

	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		// (the bolt never burns blocks; griefing only lets a real bolt fall in a thunderstorm)
		DragonStorm.breathe(level, this, mouth, aim, this.tickCount, griefing);
	}

	@Override
	protected double breathRange() {
		return DragonStorm.RANGE;
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
	protected Vec3 seat(int index) {
		return new Vec3(0.0, SEAT_HEIGHT, index == 0 ? 0.2 : -0.45);
	}

	/** Faster in a storm, wild: the rain and the thunder are its element. */
	@Override
	protected double cruiseSpeed() {
		return CRUISE * (stormy() ? STORM_BOOST : 1.0);
	}

	@Override
	protected double swoopSpeed() {
		return SWOOP * (stormy() ? STORM_BOOST : 1.0);
	}

	private boolean stormy() {
		return this.level().isRaining() || this.level().isThundering();
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
	protected float flapVolume() {
		return FLAP_VOLUME;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	/**
	 * A Wind user flies it faster, most of all in the rain and the thunder. Read on the rider's client, so it only asks for
	 * what's synced: the weather and the rider's power.
	 */
	@Override
	public float riderSpeedFactor(Player rider) {
		if (!"wind".equals(PowersCompat.power(rider))) {
			return 1.0F;
		}
		Level level = rider.level();
		return level.isRaining() || level.isThundering() ? STORM_RIDER : CLEAR_RIDER;
	}

	// ---- leaving ----------------------------------------------------------------------------------------------------

	/** Fighting keeps it here. */
	@Override
	protected void fight(ServerLevel level, LivingEntity target) {
		this.idle = 0;
		this.leaving = false;
		super.fight(level, target);
	}

	/**
	 * It has no home to go back to: a wild one that has had no foe for a while (it was beaten off, or whoever it came for
	 * has gone) flies off high and away, and vanishes, rather than circling over nothing. Tame, ridden or crashed, it stays.
	 */
	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (!this.isTamed() && !grounded() && !this.isBaby() && !this.isVehicle()) {
			this.idle++;
			if (this.leaving) {
				leave(level);
				return;
			}
			if (this.idle > LEAVE_AFTER) {
				this.leaving = true;
				this.leavingFor = 0;
				this.leaveFrom = this.getY();
				Vec3 way = this.getLookAngle().multiply(1.0, 0.0, 1.0);
				this.leaveWay = way.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : way.normalize();
				level.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 3.0F, 1.2F);
			}
		} else {
			this.idle = 0;
			this.leaving = false;
		}
		super.customServerAiStep(level);
	}

	/** Away and up, in a storm's hurry, and out of the world with a crack of thunder. */
	private void leave(ServerLevel level) {
		this.leavingFor++;
		if (this.onGround()) {
			takeOff();
		}
		setFlying(true);
		this.home = null;
		this.getNavigation().stop();
		Vec3 point = this.position().add(this.leaveWay.scale(30.0)).add(0.0, LEAVE_CLIMB, 0.0);
		flyToward(level, point, cruiseSpeed() * LEAVE_SPEED, false);
		if (this.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(0.5), this.getZ(), 4, 0.6, 0.4, 0.6, 0.05);
		}
		if (this.leavingFor > LEAVE_TICKS || this.getY() > this.leaveFrom + LEAVE_HEIGHT) {
			level.playSound(null, this.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 4.0F, 1.0F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(0.5), this.getZ(), 40, 1.0, 0.6, 1.0, 0.3);
			this.discard();
		}
	}

	/** Struck while it flees, it turns on whoever did it. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.leaving && !this.isTamed()) {
			this.leaving = false;
			this.idle = 0;
		}
		return hurt;
	}
}
