package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.105, Scan B) Swathe, the wrapped one: a tall figure bound head to foot in old grey-brown wrappings, loose ends
 * trailing, two pale eyes in the gap. 26 health, fists of 4. Scanned from a husk. While it's out it can't drown or
 * starve.
 * <ul>
 * <li>R, Lash Bind: a bandage lash wraps the legs of the creature you look at and brings it down, as a bolas does
 * ({@link Tripped#bind}); what can't be tripped is held fast instead.</li>
 * <li>G, Lash Pull: a lash round the creature you look at drags it to you.</li>
 * <li>V, Sandstorm: a storm of sand round you that blinds and scours; wider and harder in deserts and badlands.</li>
 * <li>B, Wrap: bind your own wounds: bleeding stops, and you heal.</li>
 * </ul>
 */
public final class Swathe extends XenoForm {
	// ---- tuning ----
	public static final double LASH_REACH = 9.0;
	public static final int BIND_TICKS = 80, BIND_COOLDOWN = 120;
	public static final double PULL_REACH = 12.0, PULL_SPEED = 0.35;
	public static final int PULL_COOLDOWN = 40;
	public static final int STORM_TICKS = 120, STORM_COOLDOWN = 400;
	public static final double STORM_RADIUS = 6.0, STORM_RADIUS_DESERT = 9.0;
	public static final float STORM_DAMAGE = 1.0F, STORM_DAMAGE_DESERT = 2.0F;
	public static final int WRAP_TICKS = 100, WRAP_COOLDOWN = 600;

	public Swathe() {
		super("swathe", 1.0F, 26.0F, 4.0, -0.02, 0.3, 0.0, Items.PAPER, 0xB8A890);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Husk;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.storm = 0;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_DROWNING) && !source.is(DamageTypes.STARVE);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R)) {
					return;
				}
				LivingEntity target = aimed(player, LASH_REACH, e -> true);
				if (target == null) {
					return;
				}
				lash(level, player, target);
				if (!Tripped.bind(target, player, BIND_TICKS)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BIND_TICKS, 9), player);
					target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, BIND_TICKS, 1), player);
				}
				sound(player, SoundEvents.WOOL_PLACE, 1.2F, 0.6F);
				cooldown(player, live, R, BIND_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, PULL_REACH, e -> true);
				if (target == null) {
					return;
				}
				lash(level, player, target);
				Vec3 in = player.position().subtract(target.position());
				target.setDeltaMovement(in.normalize().scale(PULL_SPEED * Math.min(3.0, Math.sqrt(in.length()))).add(0.0, 0.3, 0.0));
				target.syncVelocity = true;
				sound(player, SoundEvents.WOOL_PLACE, 1.0F, 1.2F);
				cooldown(player, live, G, PULL_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					live.storm = STORM_TICKS;
					sound(player, SoundEvents.SAND_BREAK, 1.5F, 0.5F);
					cooldown(player, live, V, STORM_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					player.removeAttached(Bleeding.BLEEDING);
					player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, WRAP_TICKS, 1, false, true));
					burst(level, ParticleTypes.WHITE_ASH, player.position().add(0.0, 1.0, 0.0), 20, 0.4, 0.02);
					sound(player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.8F);
					cooldown(player, live, B, WRAP_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	private static void lash(ServerLevel level, ServerPlayer player, LivingEntity target) {
		beam(level, player.getEyePosition().add(0.0, -0.4, 0.0), target.getBoundingBox().getCenter(), ParticleTypes.WHITE_ASH, 16);
	}

	/** In a desert or badlands, the storm's at home. */
	private static boolean sandy(ServerPlayer player) {
		var biome = player.level().getBiome(player.blockPosition());
		return biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		// it neither drowns nor starves
		player.setAirSupply(player.getMaxAirSupply());
		if (live.storm <= 0) {
			return;
		}
		live.storm--;
		ServerLevel level = player.level();
		boolean sandy = sandy(player);
		double radius = sandy ? STORM_RADIUS_DESERT : STORM_RADIUS;
		BlockParticleOption sand = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
		for (int i = 0; i < 6; i++) {
			double a = player.tickCount * 0.4 + i * Math.PI / 3.0;
			double r = radius * (0.3 + player.getRandom().nextDouble() * 0.7);
			level.sendParticles(sand, player.getX() + Math.cos(a) * r, player.getY() + player.getRandom().nextDouble() * 2.5, player.getZ() + Math.sin(a) * r,
				2, 0.3, 0.3, 0.3, 0.05);
		}
		if (live.storm % 20 == 0) {
			for (LivingEntity e : around(player, radius)) {
				e.hurtServer(level, player.damageSources().playerAttack(player), sandy ? STORM_DAMAGE_DESERT : STORM_DAMAGE);
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), player);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), player);
			}
			sound(player, SoundEvents.SAND_PLACE, 0.8F, 0.6F);
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), player.position().add(0.0, 1.0, 0.0), 60, 1.0, 0.1);
		sound(player, SoundEvents.HUSK_AMBIENT, 1.4F, 0.6F);
		return true;
	}
}
