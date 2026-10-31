package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.cryo.CryoLent;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.item.Items;

/**
 * (0.18.105, Scan B of {@code docs/ScanGaps-Plan.md}) Rime, the frost alien: tall and angular, pale blue ice for skin,
 * icicle spines down its back and a crown of frost. 30 health, fists of 5. Scanned from a stray, a snow golem or the
 * Friends & Foes Iceologer ({@code XenoDna}). Per Andy, it has the whole Cryokinesis kit, lent through
 * {@link CryoLent}, so every move works (and looks) as the Cryo power's:
 * <ul>
 * <li>R (hold), Frost Breath.</li>
 * <li>G, Ice Shards.</li>
 * <li>V, Flash Freeze.</li>
 * <li>B, Ice Wall.</li>
 * <li>Sprinting on the ground: Ice Slide; it ends a moment after you stop sprinting.</li>
 * <li>The Ultimate, Hoarfrost: its B is the Blizzard.</li>
 * </ul>
 * Frost armour: whatever strikes it in reach is chilled (frozen, slow). Cold never hurts it.
 */
public final class Rime extends XenoForm {
	// ---- tuning ----
	public static final int BREATH_COOLDOWN = 60, SHARDS_COOLDOWN = 60, FREEZE_COOLDOWN = 200, WALL_COOLDOWN = 240;
	/** Ticks without sprinting before the slide ends. */
	public static final int SKATE_GRACE = 15;
	/** Frost armour: how long an attacker in reach is chilled (ticks), and how frozen it gets. */
	public static final int CHILL_TICKS = 60, CHILL_FROZEN = 140;
	public static final double CHILL_REACH = 4.0;

	public Rime() {
		super("rime", 1.05F, 30.0F, 5.0, 0.0, 0.3, 0.0, Items.BLUE_ICE, 0x9ED8F0);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Stray || creature instanceof SnowGolem;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		CryoLent.end(player);
		live.skateIdle = 0;
		live.breathWas = false;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			return false;
		}
		// frost armour: a blow from close by chills whoever struck it
		if (source.getEntity() instanceof LivingEntity attacker && attacker != player && attacker.distanceTo(player) <= CHILL_REACH) {
			attacker.setTicksFrozen(Math.max(attacker.getTicksFrozen(), CHILL_FROZEN));
			attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, 1), player);
			burst(player.level(), ParticleTypes.SNOWFLAKE, attacker.getBoundingBox().getCenter(), 10, 0.4, 0.02);
		}
		return true;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		switch (key) {
			case ABILITY_1 -> {
				if (down) {
					if (ready(player, live, R)) {
						CryoLent.breath(player, true);
					}
				} else {
					// let go: the breath stops, and tick starts its cooldown
					CryoLent.breath(player, false);
				}
			}
			case ABILITY_2 -> {
				if (down && ready(player, live, G)) {
					CryoLent.shards(player);
					cooldown(player, live, G, SHARDS_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (down && ready(player, live, V)) {
					CryoLent.freeze(player);
					cooldown(player, live, V, FREEZE_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (down && ready(player, live, B)) {
					CryoLent.wall(player);
					cooldown(player, live, B, WALL_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		player.setTicksFrozen(0);
		// Ice Slide: on while sprinting on the ground, off a moment after you stop
		if (player.isSprinting() && player.onGround()) {
			live.skateIdle = 0;
			if (!CryoLent.sliding(player)) {
				CryoLent.slideOn(player);
			}
		} else if (CryoLent.sliding(player) && ++live.skateIdle > SKATE_GRACE) {
			CryoLent.slideOff(player);
			live.skateIdle = 0;
		}
		CryoLent.tick(player);
		// the breath ended (let go, or it ran out): its cooldown starts
		boolean breathing = CryoLent.breathing(player);
		if (live.breathWas && !breathing) {
			cooldown(player, live, R, BREATH_COOLDOWN);
		}
		live.breathWas = breathing;
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.SNOWFLAKE, player.position().add(0.0, 1.0, 0.0), 60, 1.0, 0.05);
		sound(player, SoundEvents.POWDER_SNOW_BREAK, 1.4F, 0.5F);
		return true;
	}

	@Override
	public boolean ultimateHasMove() {
		return true;
	}

	/** The Ultimate's B: the Blizzard, as Cryokinesis's C. */
	@Override
	public void onUltimateAgain(ServerPlayer player, XenoformPower.Live live) {
		if (ultimateReady(player, live)) {
			CryoLent.blizzard(player);
			ultimateCooldown(player, live, com.jaymospowers.power.cryo.CryoBlizzard.DURATION_TICKS + 600);
		}
	}
}
