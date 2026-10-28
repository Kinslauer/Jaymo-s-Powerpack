package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.46, Andy's NOEA picks) Afterimage, a quick technique (G): you blink {@link #BLINK} blocks aside (the right, else the
 * left, else as far as there is room) and leave a still copy of yourself where you stood for {@link #LIFE_TICKS} (3 s).
 * It is a vanilla mannequin in your skin, the same idea as the sparring partner ({@link ZFighterSparring}), only it stands
 * still and has no health to lose: every mob within {@link #LURE_RANGE} that was after you turns on it, and the first
 * thing that hits it pops it. One at a time: a second blink pops the first.
 */
final class ZFighterAfterimage {
	static final float KI = 30.0F;
	static final int COOLDOWN = 240;
	static final double BLINK = 6.0;
	static final int LIFE_TICKS = 60;
	/** Mobs this near that have you as their target are turned on the copy, checked every this many ticks. */
	static final double LURE_RANGE = 32.0;
	static final int LURE_EVERY = 5;
	/** The mannequin's tag, so a copy left over from a restart can be swept away. */
	static final String TAG = "jaymospowers_afterimage";

	private record Copy(ServerLevel level, UUID copy, long until) {
	}

	/** Owner → their copy. */
	private static final Map<UUID, Copy> COPIES = new HashMap<>();

	private ZFighterAfterimage() {
	}

	/** The mannequin's skin, which only a mannequin's own code may set: read through a subclass that's never made. */
	private abstract static class Profile extends Mannequin {
		private Profile(Level level) {
			super(level);
		}

		static EntityDataAccessor<ResolvableProfile> key() {
			return DATA_PROFILE;
		}
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterAfterimage::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		// one left over from a restart has no owner to follow: it goes
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(TAG) && !isCopy(entity.getUUID())) {
				entity.discard();
			}
		});
		// the first hit pops it, and does nothing else
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Mannequin && entity.entityTags().contains(TAG) && entity.level() instanceof ServerLevel level) {
				for (Iterator<Map.Entry<UUID, Copy>> it = COPIES.entrySet().iterator(); it.hasNext(); ) {
					Map.Entry<UUID, Copy> entry = it.next();
					if (entry.getValue().copy.equals(entity.getUUID())) {
						it.remove();
						break;
					}
				}
				pop(level, entity);
				return false;
			}
			return true;
		});
	}

	static void clear() {
		for (Copy copy : COPIES.values()) {
			var entity = copy.level.getEntity(copy.copy);
			if (entity != null) {
				entity.discard();
			}
		}
		COPIES.clear();
	}

	private static boolean isCopy(UUID id) {
		for (Copy copy : COPIES.values()) {
			if (copy.copy.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** G: blink and leave the copy. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		ServerLevel level = player.level();
		Vec3 start = player.position();
		Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
		Vec3 dest = null;
		// the right, else the left: as far as there is room, up to BLINK
		for (double side : new double[] {1.0, -1.0}) {
			for (double d = BLINK; d >= 1.5 && dest == null; d -= 0.5) {
				Vec3 candidate = start.add(right.scale(side * d));
				if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start))) && clear(level, start, candidate, player)) {
					dest = candidate;
				}
			}
			if (dest != null) {
				break;
			}
		}
		if (dest == null) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.zfighter_afterimage_nospace")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			return 0;
		}
		if (!ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		// the old copy pops, a new one stands where he was
		Copy old = COPIES.remove(player.getUUID());
		if (old != null && old.level.getEntity(old.copy) != null) {
			pop(old.level, old.level.getEntity(old.copy));
		}
		Mannequin copy = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (copy != null) {
			copy.getEntityData().set(Profile.key(), ResolvableProfile.createResolved(player.getGameProfile()));
			copy.addTag(TAG);
			copy.snapTo(start.x, start.y, start.z, player.getYRot(), player.getXRot());
			copy.setYHeadRot(player.getYHeadRot());
			copy.setYBodyRot(player.yBodyRot);
			// (0.17.91) known before it is added: the load sweep below would otherwise take it for a leftover
			COPIES.put(player.getUUID(), new Copy(level, copy.getUUID(), level.getGameTime() + LIFE_TICKS));
			if (level.addFreshEntity(copy)) {
				lure(player, level, copy);
			} else {
				COPIES.remove(player.getUUID());
			}
		}
		DustParticleOptions image = new DustParticleOptions(ZFighterArts.color(live), 1.2F);
		for (double h = 0.1; h < player.getBbHeight(); h += 0.3) {
			level.sendParticles(image, true, false, start.x, start.y + h, start.z, 3, 0.2, 0.05, 0.2, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, true, false, start.x, start.y + 1.0, start.z, 8, 0.25, 0.5, 0.25, 0.02);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.7F);
		live.stepUntil = level.getGameTime() + 10;
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.CLOUD, true, false, dest.x, dest.y + 0.5, dest.z, 6, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.6F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.8F);
		}
		return COOLDOWN;
	}

	/** Whether nothing solid lies between the two spots at body height. */
	private static boolean clear(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer player) {
		Vec3 step = to.subtract(from);
		int steps = Mth.ceil(step.length() * 2.0);
		for (int i = 1; i < steps; i++) {
			Vec3 at = from.add(step.scale(i / (double)steps));
			if (!level.noCollision(player, player.getBoundingBox().move(at.subtract(from)))) {
				return false;
			}
		}
		return true;
	}

	/** Mobs near that had him as their target are turned on the copy. */
	private static void lure(ServerPlayer owner, ServerLevel level, LivingEntity copy) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(owner.position(), owner.position()).inflate(LURE_RANGE), m -> m.isAlive() && m.getTarget() == owner)) {
			mob.setTarget(copy);
		}
	}

	private static void tick(MinecraftServer server) {
		if (COPIES.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Copy>> it = COPIES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Copy> entry = it.next();
			Copy copy = entry.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			var entity = copy.level.getEntity(copy.copy);
			if (!(entity instanceof Mannequin mannequin) || !mannequin.isAlive()) {
				it.remove();
				continue;
			}
			long now = copy.level.getGameTime();
			if (owner == null || !owner.isAlive() || owner.level() != copy.level || !Powers.has(owner, ZFighterPower.INSTANCE) || now >= copy.until) {
				// it fades out on its own
				pop(copy.level, entity);
				it.remove();
				continue;
			}
			if (now % LURE_EVERY == 0) {
				lure(owner, copy.level, mannequin);
				copy.level.sendParticles(new DustParticleOptions(ZFighterPower.live(owner).kiColor >= 0 ? ZFighterPower.live(owner).kiColor : ZFighterPower.COLOR, 0.8F),
					true, false, mannequin.getX(), mannequin.getY() + 1.0, mannequin.getZ(), 1, 0.3, 0.6, 0.3, 0.0);
			}
		}
	}

	/** It pops: a puff of smoke and motes, and gone. */
	private static void pop(ServerLevel level, net.minecraft.world.entity.Entity entity) {
		Vec3 at = entity.position();
		level.sendParticles(ParticleTypes.POOF, true, false, at.x, at.y + 1.0, at.z, 14, 0.3, 0.6, 0.3, 0.03);
		level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y + 1.0, at.z, 8, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ALLAY_DEATH, SoundSource.PLAYERS, 0.8F, 1.6F);
		entity.discard();
	}
}
