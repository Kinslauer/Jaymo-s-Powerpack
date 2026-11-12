package com.jaymospowers.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.smoke.SmokePower;
import com.jaymospowers.power.web.WebNet;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The <b>burning steed</b>: a horse with a mane and hooves of fire, kept by the Flame Avatars' camps (Jaymo's Kingdoms
 * raises them there, through {@link #spawn}). It is the vanilla horse with {@link #STEED} on it, so nothing else about a
 * horse changes: it is tame and saddled, faster ({@link #SPEED}), fire cannot hurt it, a trail of flame streams behind it
 * as it runs, and with a rider it gallops over water and lava as if it were ground.
 * <p>
 * Only a Flame Avatar or a Smoke user may ride one ({@link #may}). Anyone else who climbs on, or is put on, is set alight
 * and thrown clear ({@link #repel}). The look (fire on the mane and flanks, embers at the hooves) is drawn on the client by
 * {@code FlameAvatarClient}, from the synced mark.
 */
public final class BurningSteed {
	// ---- tuning ----
	/** The speed added to the horse's own (a share of it). */
	static final double SPEED = 0.35;
	/** What a stranger who climbs on takes: burning (seconds), the fire's hurt, and the throw (out, up). */
	static final float REPEL_BURN = 6.0F, REPEL_HURT = 2.0F;
	static final double REPEL_OUT = 0.9, REPEL_UP = 0.5;
	/** A rider is on the water or lava surface when the horse's feet are this far in; a gallop faster than this (blocks a tick) leaves a trail. */
	static final double TRAIL_SPEED = 0.12;
	/** The trail's flames a tick, and how far behind the horse they start (blocks of its motion). */
	static final int TRAIL_FLAMES = 3;
	static final double TRAIL_BEHIND = 2.5;
	/** The horizontal drag a surface gallop keeps (as on ground), so the horse still slows when not urged. */
	static final double SURFACE_LIFT = 0.02;
	/** How often (ticks) the loaded steeds are looked at for a stranger aboard. */
	static final int CHECK_EVERY = 5;

	/** On a steed: saved, seen by all (the look). */
	public static final AttachmentType<Boolean> STEED = AttachmentRegistry.create(JaymosPowers.id("burning_steed"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** The scoreboard tag on every steed, for the other mods. */
	public static final String TAG = "jaymospowers_burning_steed";
	private static final Identifier SPEED_ID = JaymosPowers.id("burning_steed_speed");

	/** Steeds loaded now. */
	private static final Set<AbstractHorse> LOADED = new HashSet<>();

	private BurningSteed() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractHorse horse && is(horse)) {
				LOADED.add(horse);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LOADED.clear());
		ServerTickEvents.END_SERVER_TICK.register(BurningSteed::tick);
		// fire does not hurt it
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(is(entity) && source.is(DamageTypeTags.IS_FIRE)));
		// climbing on: the avatar and the Smoke user ride; anyone else is burned and thrown
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !is(entity) || !(player instanceof ServerPlayer rider) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (!may(rider)) {
				repel(rider, entity);
				return InteractionResult.SUCCESS;
			}
			if (rider.isShiftKeyDown() || entity.hasPassenger(rider) || !entity.getPassengers().isEmpty()) {
				return InteractionResult.PASS;
			}
			rider.startRiding(entity);
			return InteractionResult.SUCCESS;
		});
	}

	/** Whether {@code entity} is a burning steed (either side). */
	public static boolean is(@Nullable Entity entity) {
		return entity instanceof AbstractHorse && Boolean.TRUE.equals(entity.getAttached(STEED));
	}

	/** Who may ride one: a Flame Avatar, or a Smoke user. */
	public static boolean may(Player player) {
		return FlameAvatar.is(player) || Powers.has(player, SmokePower.INSTANCE);
	}

	/** Makes {@code horse} a burning steed: marked, tame, saddled, faster. */
	public static void mark(AbstractHorse horse) {
		horse.setAttached(STEED, true);
		horse.addTag(TAG);
		horse.setTamed(true);
		if (horse.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()) {
			horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
			horse.setDropChance(EquipmentSlot.SADDLE, 0.0F);
		}
		horse.setPersistenceRequired();
		AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrReplacePermanentModifier(new AttributeModifier(SPEED_ID, SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		LOADED.add(horse);
	}

	/** A new burning steed at {@code at} (null if it could not be made). */
	public static @Nullable Horse spawn(ServerLevel level, Vec3 at) {
		Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.EVENT);
		if (horse == null) {
			return null;
		}
		horse.setPos(at.x, at.y, at.z);
		horse.setYRot(level.getRandom().nextFloat() * 360.0F);
		mark(horse);
		level.addFreshEntity(horse);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.0, at.z, 24, 0.4, 0.6, 0.4, 0.04);
		return horse;
	}

	/** A stranger on a steed: set alight, hurt a little, and thrown clear. */
	static void repel(Entity stranger, Entity steed) {
		if (stranger.getVehicle() == steed) {
			stranger.stopRiding();
		}
		stranger.igniteForSeconds(REPEL_BURN);
		if (stranger instanceof LivingEntity living && stranger.level() instanceof ServerLevel level) {
			living.hurtServer(level, level.damageSources().onFire(), REPEL_HURT);
		}
		Vec3 away = stranger.position().subtract(steed.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
		Vec3 push = away.scale(REPEL_OUT).add(0.0, REPEL_UP, 0.0);
		stranger.setDeltaMovement(stranger.getDeltaMovement().add(push));
		stranger.needsSync = true;
		stranger.syncVelocity = true;
		if (stranger instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, new WebNet.Shove(push));
		}
		if (steed.level() instanceof ServerLevel level) {
			level.playSound(null, steed.getX(), steed.getY(), steed.getZ(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 0.7F);
			level.sendParticles(ParticleTypes.FLAME, stranger.getX(), stranger.getY() + 1.0, stranger.getZ(), 16, 0.3, 0.5, 0.3, 0.05);
		}
	}

	private static void tick(MinecraftServer server) {
		if (LOADED.isEmpty()) {
			return;
		}
		for (AbstractHorse horse : LOADED) {
			if (!horse.isAlive() || !(horse.level() instanceof ServerLevel level)) {
				continue;
			}
			// fire cannot hold on to it
			horse.clearFire();
			Entity rider = horse.getFirstPassenger();
			if (rider != null && (horse.tickCount + horse.getId()) % CHECK_EVERY == 0 && !(rider instanceof Player player && may(player))) {
				// a stranger put aboard some other way
				repel(rider, horse);
				continue;
			}
			if (rider == null) {
				continue;
			}
			Vec3 v = horse.getDeltaMovement();
			if (horse.isInWater() || horse.isInLava()) {
				surface(level, horse);
			}
			double speed = Math.sqrt(v.x * v.x + v.z * v.z);
			if (speed > TRAIL_SPEED) {
				trail(level, horse, v, speed);
			}
		}
	}

	/** The fluid it stands in is a floor: it is lifted to the surface and carries on at the gallop. */
	private static void surface(ServerLevel level, AbstractHorse horse) {
		BlockPos feet = horse.blockPosition();
		for (int up = 0; up <= 1; up++) {
			BlockPos at = feet.above(up);
			FluidState fluid = level.getFluidState(at);
			if (!fluid.is(FluidTags.WATER) && !fluid.is(FluidTags.LAVA)) {
				continue;
			}
			double top = at.getY() + fluid.getHeight(level, at);
			if (horse.getY() < top + SURFACE_LIFT) {
				horse.setPos(horse.getX(), top + SURFACE_LIFT, horse.getZ());
				Vec3 v = horse.getDeltaMovement();
				horse.setDeltaMovement(v.x, 0.0, v.z);
				horse.setOnGround(true);
				horse.resetFallDistance();
				horse.needsSync = true;
			}
			return;
		}
	}

	/** Flames streaming off behind a galloping steed, and now and then an ember. */
	private static void trail(ServerLevel level, AbstractHorse horse, Vec3 v, double speed) {
		Vec3 behind = horse.position().subtract(v.scale(TRAIL_BEHIND / speed * 0.5));
		level.sendParticles(ParticleTypes.FLAME, behind.x, behind.y + 0.2, behind.z, TRAIL_FLAMES, 0.2, 0.1, 0.2, 0.01);
		if (horse.getRandom().nextInt(4) == 0) {
			level.sendParticles(MagicFx.mote(MagicFx.EMBER_COLOR, 0.5F), behind.x, behind.y + 0.4, behind.z, 1, 0.2, 0.2, 0.2, 0.02);
		}
		if (horse.getRandom().nextInt(6) == 0) {
			level.sendParticles(ParticleTypes.SMOKE, behind.x, behind.y + 0.6, behind.z, 1, 0.2, 0.2, 0.2, 0.01);
		}
	}
}
