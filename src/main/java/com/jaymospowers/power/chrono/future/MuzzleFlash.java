package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.TaczGuns;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.135) Andy: "there should be more muzzle flash on the guns from the future". Every shot from a future gun (a
 * gunner's, a mech's cannons, or a player's TaCZ gun) throws a bright burst at the muzzle that everyone around sees:
 * our own {@link Flash} (a full-bright star that pops and is gone in a few ticks, the client's {@code MuzzleFlashParticle}),
 * a couple of smaller ones thrown forward as a plume, and a puff of smoke. Bigger guns flash bigger. A player's own shot
 * isn't sent to them (TaCZ draws its flash on the gun in their hands, which the client's {@code TaczMuzzleFlashMixin}
 * makes bigger); a silenced gun doesn't flash. Players' shots are heard through TaCZ's own fire event, found by
 * reflection, so nothing here without TaCZ.
 */
public final class MuzzleFlash {
	/** The burst's size (1 = about a block across at its peak) for each kind of gun. */
	static final float PISTOL = 0.9F, SMG = 0.85F, RIFLE = 1.2F, MG = 1.35F, SHOTGUN = 1.6F, SNIPER = 1.8F;
	/** A gun we don't know (a pack's own gun in a player's hands). */
	static final float UNKNOWN = 1.1F;
	/** The plume thrown forward: how many, their size (a share of the burst's) and speed. */
	static final int PLUME = 2;
	static final float PLUME_SHARE = 0.6F;
	static final double PLUME_SPEED = 0.35;
	/** Smoke left hanging at the muzzle, more for bigger guns. */
	static final int SMOKE = 3;
	/** Where a player's muzzle sits: this far ahead of the eye, out to the gun hand and down. */
	static final double PLAYER_AHEAD = 1.1, PLAYER_SIDE = 0.32, PLAYER_DOWN = 0.28;
	/** A mob's: this far ahead of the eye, out to the gun hand and down (the old muzzle was 0.9 straight ahead). */
	static final double MOB_AHEAD = 0.95, MOB_SIDE = 0.3, MOB_DOWN = 0.25;

	/** The burst, this size. */
	public record Flash(float scale) implements ParticleOptions {
		public static final MapCodec<Flash> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(Flash::scale)).apply(i, Flash::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flash> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Flash::scale, Flash::new);

		@Override
		public ParticleType<?> getType() {
			return FLASH;
		}
	}

	public static final ParticleType<Flash> FLASH = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosPowers.id("muzzle_flash"),
		FabricParticleTypes.complex(true, Flash.CODEC, Flash.STREAM_CODEC));

	private MuzzleFlash() {
	}

	/** Registers {@link #FLASH} and listens for players' TaCZ shots. */
	static void init() {
		if (!TaczGuns.LOADED) {
			return;
		}
		try {
			Class<?> event = Class.forName("com.tacz.guns.api.event.common.GunFireEvent");
			Class<?> callback = Class.forName("com.tacz.guns.api.event.common.GunFireEvent$Callback");
			Method shooter = event.getMethod("getShooter"), stack = event.getMethod("getGunItemStack"), side = event.getMethod("getLogicalSide");
			Object listener = Proxy.newProxyInstance(MuzzleFlash.class.getClassLoader(), new Class<?>[] {callback}, (proxy, method, args) -> {
				if (method.getDeclaringClass() == Object.class) {
					return switch (method.getName()) {
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> "MuzzleFlash";
					};
				}
				Object fired = args[0];
				try {
					if ("SERVER".equals(String.valueOf(side.invoke(fired))) && shooter.invoke(fired) instanceof LivingEntity who
						&& who.level() instanceof ServerLevel level && stack.invoke(fired) instanceof ItemStack gun) {
						playerShot(level, who, gun);
					}
				} catch (ReflectiveOperationException | RuntimeException e) {
					// never get in the way of the shot itself
				}
				return null;
			});
			@SuppressWarnings("unchecked")
			Event<Object> hook = (Event<Object>) event.getField("CALLBACK").get(null);
			hook.register(listener);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't hook TaCZ's gun fire for muzzle flashes: {}", e.toString());
		}
	}

	/** A shot from a TaCZ gun in someone's hands (a player's, mostly): the burst, for everyone but them. */
	private static void playerShot(ServerLevel level, LivingEntity who, ItemStack gun) {
		if (silenced(gun)) {
			return;
		}
		Vec3 aim = who.getLookAngle();
		FutureGuns.Gun known = FutureGuns.BY_ID.get(TaczGuns.gunId(gun));
		flash(level, muzzle(who, aim, PLAYER_AHEAD, PLAYER_SIDE, PLAYER_DOWN), aim, known == null ? UNKNOWN : size(known.kind()),
			who instanceof ServerPlayer player ? player : null);
	}

	/** A gunner's (or mech's) shot along {@code aim}: the burst, for everyone. */
	static void mobShot(ServerLevel level, LivingEntity mob, Vec3 aim, FutureGuns.Kind kind) {
		flash(level, muzzle(mob, aim, MOB_AHEAD, MOB_SIDE, MOB_DOWN), aim, size(kind), null);
	}

	/** Where the muzzle of a gun in {@code who}'s main hand is, pointed along {@code aim}. */
	static Vec3 muzzle(LivingEntity who, Vec3 aim, double ahead, double side, double down) {
		Vec3 right = aim.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-4 ? Vec3.ZERO : right.normalize();
		double hand = who.getMainArm() == HumanoidArm.LEFT ? -side : side;
		float scale = who.getScale();
		return who.getEyePosition().add(aim.scale(ahead * scale)).add(right.scale(hand * scale)).add(0.0, -down * scale, 0.0);
	}

	static float size(FutureGuns.Kind kind) {
		if (kind == FutureGuns.PISTOL) {
			return PISTOL;
		} else if (kind == FutureGuns.SMG) {
			return SMG;
		} else if (kind == FutureGuns.MG) {
			return MG;
		} else if (kind == FutureGuns.SHOTGUN) {
			return SHOTGUN;
		} else if (kind == FutureGuns.SNIPER) {
			return SNIPER;
		}
		return RIFLE;
	}

	/** A suppressor on the gun (TaCZ keeps its attachments' ids in the gun's data; all its suppressors are "silencer"s). */
	static boolean silenced(ItemStack gun) {
		return String.valueOf(gun.get(DataComponents.CUSTOM_DATA)).contains("silencer");
	}

	/** The burst at {@code at}, pointed along {@code aim}, this size; sent to every player near but {@code skip}. */
	static void flash(ServerLevel level, Vec3 at, Vec3 aim, float size, @Nullable ServerPlayer skip) {
		RandomSource random = level.getRandom();
		Flash core = new Flash(size * (0.9F + random.nextFloat() * 0.2F));
		Flash plume = new Flash(size * PLUME_SHARE);
		int smoke = SMOKE + Math.round(size * 2.0F);
		for (ServerPlayer player : level.players()) {
			if (player == skip) {
				continue;
			}
			level.sendParticles(player, core, true, true, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			for (int i = 0; i < PLUME; i++) {
				// a count of 0 sends the offsets as the direction, times the speed
				double speed = PLUME_SPEED * size * (0.7 + 0.6 * i);
				level.sendParticles(player, plume, true, true, at.x, at.y, at.z, 0, aim.x, aim.y, aim.z, speed);
			}
			level.sendParticles(player, ParticleTypes.SMOKE, false, false, at.x, at.y, at.z, smoke, 0.06, 0.06, 0.06, 0.015);
		}
	}
}
