package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.111, Scan D) Lumen, the light: small and pale blue, half see-through, a glowing core in its chest, two thin
 * wing-fins on its back. 16 health, fists of 2: the support alien. It drifts down gently rather than falls, and falls
 * never hurt it. Scanned from an allay, or Naturalist's firefly or the Friends &amp; Foes glare ({@code XenoDna}).
 * <ul>
 * <li>R, Mend: a healing glow on you and the players (and your pets) round you.</li>
 * <li>G, Flare: a flash that blinds every creature round you.</li>
 * <li>V, Light: a light hangs where you look for {@link #LIGHT_TICKS} ticks, as a glare leaves.</li>
 * <li>B, Fetch: as an allay, items on the ground of the kind you hold fly to you (anything, with an empty hand).</li>
 * </ul>
 */
public final class Lumen extends XenoForm {
	// ---- tuning ----
	public static final double MEND_RADIUS = 8.0;
	public static final float MEND_HEAL = 4.0F;
	public static final int MEND_COOLDOWN = 200;
	public static final double FLARE_RADIUS = 8.0;
	public static final int FLARE_BLIND = 80, FLARE_COOLDOWN = 160;
	public static final double LIGHT_REACH = 24.0;
	public static final int LIGHT_TICKS = 600, LIGHT_COOLDOWN = 40, LIGHT_MAX = 6;
	public static final double FETCH_RADIUS = 24.0, FETCH_PULL = 0.35;
	public static final int FETCH_TICKS = 200, FETCH_COOLDOWN = 300;
	/** Falling no faster than this (blocks/tick) unless it sneaks. */
	public static final double DRIFT_FALL = -0.15;

	public Lumen() {
		super("lumen", 0.7F, 16.0F, 2.0, 0.05, 0.0, 0.0, Items.GLOWSTONE_DUST, 0x9ADCF4);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Allay;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		douse(player.level(), live, Long.MAX_VALUE);
		live.fetch = 0;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MEND_RADIUS),
						e -> e.isAlive() && (e == player || e instanceof Player || spared(player, e)) && e.distanceTo(player) <= MEND_RADIUS)) {
						e.heal(MEND_HEAL);
						level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
					}
					ring(level, ParticleTypes.GLOW, player.position().add(0.0, 0.2, 0.0), MEND_RADIUS * 0.5, 24);
					sound(player, SoundEvents.ALLAY_ITEM_GIVEN, 1.2F, 1.0F);
					cooldown(player, live, R, MEND_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					for (LivingEntity e : around(player, FLARE_RADIUS)) {
						e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLARE_BLIND, 0), player);
						if (e instanceof Mob mob && mob.getTarget() == player) {
							mob.setTarget(null);
						}
					}
					Vec3 at = player.getEyePosition();
					level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
					burst(level, ParticleTypes.END_ROD, at, 30, 0.6, 0.15);
					sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 1.6F);
					cooldown(player, live, G, FLARE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && light(player, live, level)) {
					cooldown(player, live, V, LIGHT_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.fetch = FETCH_TICKS;
					sound(player, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 1.2F, 1.0F);
					cooldown(player, live, B, FETCH_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** A light where it looks: in the air just short of the block hit, or at the end of its reach. */
	private static boolean light(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		HitResult hit = player.pick(LIGHT_REACH, 1.0F, false);
		BlockPos at = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos().relative(block.getDirection())
			: BlockPos.containing(hit.getLocation());
		if (!level.getBlockState(at).isAir() || !player.mayBuild() || !level.mayInteract(player, at)) {
			return false;
		}
		if (live.lights.size() >= LIGHT_MAX) {
			// the oldest goes out first
			long oldest = 0L, soonest = Long.MAX_VALUE;
			for (var entry : live.lights.long2LongEntrySet()) {
				if (entry.getLongValue() < soonest) {
					soonest = entry.getLongValue();
					oldest = entry.getLongKey();
				}
			}
			out(level, BlockPos.of(oldest));
			live.lights.remove(oldest);
		}
		level.setBlockAndUpdate(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
		live.lights.put(at.asLong(), level.getGameTime() + LIGHT_TICKS);
		level.sendParticles(ParticleTypes.GLOW, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
		return true;
	}

	/** Puts out every light that has burned past {@code now}. */
	private static void douse(ServerLevel level, XenoformPower.Live live, long now) {
		var it = live.lights.long2LongEntrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			if (entry.getLongValue() <= now) {
				out(level, BlockPos.of(entry.getLongKey()));
				it.remove();
			}
		}
	}

	private static void out(ServerLevel level, BlockPos at) {
		if (level.isLoaded(at) && level.getBlockState(at).is(Blocks.LIGHT)) {
			level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// it drifts down rather than falls
		Vec3 m = player.getDeltaMovement();
		if (!player.onGround() && !player.isShiftKeyDown() && !player.isInWater() && m.y < DRIFT_FALL) {
			player.setDeltaMovement(m.x, DRIFT_FALL, m.z);
			player.syncVelocity = true;
		}
		if (player.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 0.9, player.getZ(), 1, 0.2, 0.3, 0.2, 0.0);
		}
		if (!live.lights.isEmpty() && player.tickCount % 20 == 0) {
			douse(level, live, level.getGameTime());
		}
		if (live.fetch > 0) {
			live.fetch--;
			ItemStack held = player.getMainHandItem().isEmpty() ? player.getOffhandItem() : player.getMainHandItem();
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(FETCH_RADIUS),
				i -> i.isAlive() && (held.isEmpty() || ItemStack.isSameItem(held, i.getItem())))) {
				Vec3 pull = player.position().add(0.0, 0.8, 0.0).subtract(item.position());
				item.setDeltaMovement(pull.normalize().scale(FETCH_PULL));
				if (player.tickCount % 5 == 0) {
					level.sendParticles(ParticleTypes.GLOW, item.getX(), item.getY() + 0.2, item.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
				}
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return !player.onGround();
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.END_ROD, player.position().add(0.0, 1.0, 0.0), 50, 1.0, 0.05);
		sound(player, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.4F);
		return true;
	}
}
