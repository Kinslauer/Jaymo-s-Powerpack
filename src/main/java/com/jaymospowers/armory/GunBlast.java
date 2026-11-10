package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The flintlock guns going off: WeaponMod's musket (bayonet ones too), blunderbuss and flintlock pistol, and the Void
 * Armory's flintlock. Each shot pours fire and smoke out of the muzzle for everyone nearby, and leaves smoke hanging
 * there after it (Tiv: "they need alotta smoke and fire to accompany the blast"). The shot is also marked on the shooter
 * ({@link #SHOT}, synced to every client), so the gun in their hand plays its fire animation
 * ({@code client/armory/guns/AnimatedGunModel}). Nothing about the shot itself changes: damage, range and reload are
 * WeaponMod's (or Arsenal's).
 * <pre>
 *   tick 0: flash, a cone of flame, a burst of thick smoke and sparks out of the muzzle
 *   then:   smoke keeps rolling out of the muzzle and drifting up, thinning, for the gun's LINGER ticks
 * </pre>
 */
public final class GunBlast {
	/** The guns and how big their blast is: flame, thick smoke, fine smoke, sparks, smoke puffs per tick after, for how long. */
	public enum Gun {
		FLINTLOCK(14, 16, 20, 3, 2, 20, 0.9, 0.35),
		MUSKET(22, 24, 30, 5, 2, 30, 1.6, 0.6),
		BLUNDERBUSS(34, 36, 40, 8, 3, 40, 1.25, 0.8);

		final int flame, thickSmoke, smoke, sparks, lingerPuffs, linger;
		/** How far ahead of the eye the muzzle is (blocks), and how wide the flame's cone (its spread per block out). */
		final double reach, cone;

		Gun(int flame, int thickSmoke, int smoke, int sparks, int lingerPuffs, int linger, double reach, double cone) {
			this.flame = flame;
			this.thickSmoke = thickSmoke;
			this.smoke = smoke;
			this.sparks = sparks;
			this.lingerPuffs = lingerPuffs;
			this.linger = linger;
			this.reach = reach;
			this.cone = cone;
		}

		/** WeaponMod's gun (or ours) by its id, or null if it isn't one of the flintlock guns. */
		public static @Nullable Gun of(ItemStack stack) {
			if (stack.isEmpty()) {
				return null;
			}
			String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (path.startsWith("musket")) {
				return MUSKET;
			}
			if (path.equals("blunderbuss")) {
				return BLUNDERBUSS;
			}
			return path.contains("flintlock") ? FLINTLOCK : null;
		}
	}

	/** How fast the flame leaves the muzzle, and the smoke (blocks per tick). */
	static final double FLAME_SPEED = 0.35;
	static final double SMOKE_SPEED = 0.12;
	/** How far the muzzle sits out to the side of the hand holding the gun, and below the eye (blocks). */
	static final double SIDE = 0.3;
	static final double DROP = 0.18;
	/** The flash's colour (ARGB). */
	static final int FLASH_COLOUR = 0xFFFFE0A0;

	/** The last shot: game time, and whether it was the main hand's gun. Synced, so the gun's model plays its fire. */
	public record Shot(long time, boolean mainHand) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Shot> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Shot::time,
			ByteBufCodecs.BOOL, Shot::mainHand,
			Shot::new);
	}

	public static final AttachmentType<Shot> SHOT = AttachmentRegistry.create(JaymosPowers.id("gun_shot"),
		builder -> builder.syncWith(Shot.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Smoke still rolling out of a muzzle after its shot. */
	private record Lingering(ServerLevel level, Vec3 muzzle, Vec3 dir, Gun gun, long start) {
	}

	private static final List<Lingering> LINGERING = new ArrayList<>();

	private GunBlast() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tickLingering());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LINGERING.clear());
	}

	/** A WeaponMod gun just fired (its shot's own effects are done): the blast, if it's one of the flintlock guns. */
	public static void weaponModFired(LivingEntity shooter, ItemStack stack) {
		Gun gun = Gun.of(stack);
		if (gun != null && shooter.level() instanceof ServerLevel) {
			InteractionHand hand = shooter.isUsingItem() ? shooter.getUsedItemHand()
				: shooter.getOffhandItem() == stack ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			fired(shooter, hand, gun);
		}
	}

	/** {@code shooter} fired the {@code gun} in {@code hand}: fire and smoke out of its muzzle, and its fire animation. */
	public static void fired(LivingEntity shooter, InteractionHand hand, Gun gun) {
		if (!(shooter.level() instanceof ServerLevel level)) {
			return;
		}
		shooter.setAttached(SHOT, new Shot(level.getGameTime(), hand == InteractionHand.MAIN_HAND));
		Vec3 dir = shooter.getLookAngle();
		Vec3 muzzle = muzzle(shooter, hand, dir, gun);
		RandomSource random = level.getRandom();
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, FLASH_COLOUR), muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x + dir.x * 0.4, muzzle.y + dir.y * 0.4, muzzle.z + dir.z * 0.4, 1, 0.0, 0.0, 0.0, 0.0);
		cone(level, ParticleTypes.FLAME, muzzle, dir, gun.flame, FLAME_SPEED, gun.cone, random);
		cone(level, ParticleTypes.SMALL_FLAME, muzzle, dir, gun.flame / 2, FLAME_SPEED * 1.3, gun.cone * 0.6, random);
		cone(level, ParticleTypes.LARGE_SMOKE, muzzle, dir, gun.thickSmoke, SMOKE_SPEED * 1.5, gun.cone * 1.4, random);
		cone(level, ParticleTypes.SMOKE, muzzle, dir, gun.smoke, SMOKE_SPEED * 2.0, gun.cone * 1.8, random);
		cone(level, ParticleTypes.LAVA, muzzle, dir, gun.sparks, 0.0, 0.0, random);
		// the pan's flash at the lock, by the shooter's hand
		Vec3 pan = muzzle.subtract(dir.scale(gun.reach * 0.6));
		level.sendParticles(ParticleTypes.SMOKE, pan.x, pan.y + 0.05, pan.z, 6, 0.05, 0.05, 0.05, 0.01);
		LINGERING.add(new Lingering(level, muzzle, dir, gun, level.getGameTime()));
	}

	/** Where the muzzle of a gun held in {@code hand} is: ahead of the eye, out to that hand's side, a little low. */
	static Vec3 muzzle(LivingEntity shooter, InteractionHand hand, Vec3 dir, Gun gun) {
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? shooter.getMainArm() : shooter.getMainArm().getOpposite();
		Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		double side = arm == HumanoidArm.RIGHT ? SIDE : -SIDE;
		return shooter.getEyePosition().add(dir.scale(gun.reach)).add(right.scale(side)).add(0.0, -DROP, 0.0);
	}

	/** {@code count} particles thrown out of the muzzle along {@code dir}, spread over a cone. */
	private static void cone(ServerLevel level, ParticleOptions particle, Vec3 muzzle, Vec3 dir, int count, double speed, double spread,
		RandomSource random) {
		for (int i = 0; i < count; i++) {
			Vec3 v = dir.add(random.triangle(0.0, spread), random.triangle(0.0, spread), random.triangle(0.0, spread)).normalize();
			double s = speed * (0.4 + random.nextDouble() * 0.8);
			double along = random.nextDouble() * 0.3;
			level.sendParticles(particle, muzzle.x + v.x * along, muzzle.y + v.y * along, muzzle.z + v.z * along, 0, v.x, v.y, v.z, s);
		}
	}

	/** Every tick after a shot: smoke rolling out of the muzzle and drifting up, thinning as it goes. */
	private static void tickLingering() {
		for (Iterator<Lingering> it = LINGERING.iterator(); it.hasNext(); ) {
			Lingering smoke = it.next();
			long age = smoke.level().getGameTime() - smoke.start();
			if (age > smoke.gun().linger || age < 0) {
				it.remove();
				continue;
			}
			double left = 1.0 - (double) age / smoke.gun().linger;
			int puffs = Math.max(1, (int) Math.round(smoke.gun().lingerPuffs * left));
			Vec3 at = smoke.muzzle().add(smoke.dir().scale(0.2 + age * 0.03)).add(0.0, age * 0.02, 0.0);
			smoke.level().sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, puffs, 0.12, 0.08, 0.12, 0.01);
			if (age % 4 == 0) {
				smoke.level().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 0, 0.0, 1.0, 0.0, 0.02 * left);
			}
		}
	}
}
