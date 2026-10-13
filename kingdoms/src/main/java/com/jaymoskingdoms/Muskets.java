package com.jaymoskingdoms;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.64) The richest kingdoms ({@link #ARM_TREASURY} emeralds or more) arm about one guard in {@link #SHARE} with a
 * musket: Balkon's WeaponMod musket with an iron bayonet when it's loaded (a crossbow without it). A musketeer fires at a
 * foe in sight from {@link #NEAREST} to {@link #FURTHEST} blocks, then reloads slowly, closing in with the bayonet
 * meanwhile; he throws no javelins. The shot spares his own side as a javelin does ({@link Javelins#harms}). Should the
 * treasury fall below {@link #KEEP_TREASURY}, the muskets are sold and the guards take up their swords again.
 */
final class Muskets {
	/** A kingdom with this many emeralds arms its musketeers; they keep their muskets till it has fewer than {@link #KEEP_TREASURY}. */
	static final long ARM_TREASURY = 1000L, KEEP_TREASURY = 800L;
	/** One guard in this many carries a musket (picked by who he is, so always the same men). */
	static final int SHARE = 3;
	/** How often a guard's arms are looked over (ticks). */
	static final int CHECK = 100;
	/** He fires from this far to this far (blocks). */
	static final double NEAREST = 3.0, FURTHEST = 26.0;
	/** Reloading takes this long (ticks), plus up to {@link #JITTER} more. */
	static final int RELOAD = 120, JITTER = 40;
	/** A ball hits for this much (WeaponMod's own musket hits for 20, too much for a whole garrison of them). */
	static final float DAMAGE = 9.0F;
	/** How fast the ball flies, and how wide it may stray. */
	static final float SPEED = 4.0F, SPREAD = 2.5F;

	private static final Map<Folk, Long> NEXT = new WeakHashMap<>();
	private static @Nullable Item musket;

	private Muskets() {
	}

	/** WeaponMod's musket with an iron bayonet (or its plain musket); a crossbow without WeaponMod. */
	static Item musket() {
		if (musket == null) {
			Item found = Items.AIR;
			if (Javelins.WEAPONMOD_LOADED) {
				for (String name : new String[] {"musketbayonet.iron", "musket"}) {
					found = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, name));
					if (found != Items.AIR) {
						break;
					}
				}
			}
			musket = found == Items.AIR ? Items.CROSSBOW : found;
		}
		return musket;
	}

	/** A guard with his musket in hand. */
	static boolean armed(Folk folk) {
		return folk.role() == Folk.GUARD && folk.getMainHandItem().is(musket());
	}

	/** One of the guards who'd carry a musket in a rich enough kingdom. */
	static boolean picked(Folk folk) {
		return folk.role() == Folk.GUARD && Math.floorMod(folk.getUUID().hashCode(), SHARE) == 0;
	}

	/** Whether {@code f} can afford guns: a standing realm with {@code treasury} emeralds or more. */
	static boolean afford(@Nullable Faction f, long treasury) {
		return f != null && f.type().isRealm() && !f.fallen() && f.treasury() >= treasury;
	}

	/** From {@link Folk#customServerAiStep}: his musket handed out (or taken back), and fired when it's loaded and his foe in sight. */
	static void tick(ServerLevel level, Folk folk) {
		if (folk.role() != Folk.GUARD) {
			return;
		}
		boolean armed = armed(folk);
		if ((folk.tickCount + folk.getId()) % CHECK == 0) {
			Faction f = Factions.get(level.getServer(), folk.factionId());
			boolean should = picked(folk) && afford(f, armed ? KEEP_TREASURY : ARM_TREASURY);
			if (should && !armed) {
				folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(musket()));
				folk.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
				folk.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
				NEXT.put(folk, level.getGameTime() + level.getRandom().nextInt(JITTER));
				armed = true;
			} else if (!should && armed) {
				folk.rearm(level);
				return;
			}
		}
		LivingEntity foe = folk.getTarget();
		if (!armed || foe == null || !foe.isAlive()) {
			return;
		}
		long now = level.getGameTime();
		if (now < NEXT.getOrDefault(folk, 0L)) {
			return;
		}
		double d = folk.distanceTo(foe);
		if (d < NEAREST || d > FURTHEST || !folk.hasLineOfSight(foe)) {
			return;
		}
		NEXT.put(folk, now + RELOAD + level.getRandom().nextInt(JITTER));
		fire(level, folk, foe);
	}

	private static void fire(ServerLevel level, Folk folk, LivingEntity foe) {
		Projectile ball = Javelins.WEAPONMOD_LOADED ? WeaponModJavelins.bullet(level, folk, DAMAGE) : null;
		if (ball == null) {
			AbstractArrow bolt = ProjectileUtil.getMobArrow(folk, new ItemStack(Items.ARROW), 1.0F, folk.getMainHandItem());
			bolt.setBaseDamage(DAMAGE / SPEED);
			bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
			ball = bolt;
		}
		Javelins.mark(ball);
		double dx = foe.getX() - folk.getX(), dz = foe.getZ() - folk.getZ();
		double dy = foe.getY(0.5) - ball.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		folk.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		folk.getNavigation().stop();
		Projectile.spawnProjectileUsingShoot(ball, level, ItemStack.EMPTY, dx, dy + flat * 0.02, dz, SPEED, SPREAD);
		Vec3 muzzle = folk.getEyePosition().add(new Vec3(dx, dy, dz).normalize().scale(1.1));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.01);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
			0.9F, 1.7F + level.getRandom().nextFloat() * 0.2F);
	}
}
