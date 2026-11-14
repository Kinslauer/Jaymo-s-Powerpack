package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Mana;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Thessaly's later arts (Andy, 2026-11-14: "okay start on the other spirits"): the scholar's reach stops fetching and
 * starts rearranging the field, with a correction for everything on it.
 * <ul>
 * <li><b>Binding Script</b> (level {@value #BIND_LEVEL}): the foe you look at and every foe within {@value #BIND_RADIUS}
 * blocks of it cannot take a step for {@value #BIND_TICKS} ticks.</li>
 * <li><b>Return to Sender</b> ({@value #RETURN_LEVEL}): every arrow, bolt and thrown thing in the air within
 * {@value #RETURN_RANGE} blocks that is not yours turns round and is yours.</li>
 * <li><b>Marginalia</b> ({@value #READ_LEVEL}): every foe within {@value #READ_RANGE} blocks is read: it shows through walls
 * for {@value #READ_TICKS} ticks, and your next blow on each lands half again as hard.</li>
 * <li><b>Transposition</b> ({@value #SWAP_LEVEL}): you and the creature you look at, up to {@value #SWAP_REACH} blocks off,
 * change places; whatever was after you has lost you.</li>
 * </ul>
 */
public final class ThessalyArts {
	static final int BIND_LEVEL = 9, RETURN_LEVEL = 11, READ_LEVEL = 13, SWAP_LEVEL = 15;
	static final double BIND_REACH = 20.0, BIND_RADIUS = 5.0, RETURN_RANGE = 10.0, READ_RANGE = 16.0, SWAP_REACH = 24.0;
	static final int BIND_TICKS = 80, READ_TICKS = 400;
	static final float READ_BONUS = 0.5F;
	static final int ROSE = 0xFF9ED8;

	static final Move BIND = new Ambrose.Def("binding_script", 18.0F, 240, BIND_LEVEL, Poses.TRAP, Mana.WARD, (p, s, c) -> bind(p));
	static final Move RETURN = new Ambrose.Def("return_to_sender", 12.0F, 160, RETURN_LEVEL, Poses.CATCH, Mana.WARD, (p, s, c) -> returnAll(p));
	static final Move READ = new Ambrose.Def("marginalia", 16.0F, 20 * 30, READ_LEVEL, Poses.MARK, Mana.WILL, (p, s, c) -> read(p));
	static final Move SWAP = new Ambrose.Def("transposition", 20.0F, 300, SWAP_LEVEL, Poses.PULL, Mana.WILL, (p, s, c) -> swap(p));
	static final List<Move> MOVES = List.of(BIND, RETURN, READ, SWAP);

	/** Who has been read, by whom, until when. */
	private record Read(UUID by, long until) {
	}

	private static final Map<UUID, Read> READS = new HashMap<>();
	private static boolean bonusing;

	private ThessalyArts() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (bonusing || READS.isEmpty() || taken <= 0.0F || !entity.isAlive() || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			Read read = READS.get(entity.getUUID());
			if (read == null || !(source.getEntity() instanceof ServerPlayer keeper) || !keeper.getUUID().equals(read.by())) {
				return;
			}
			READS.remove(entity.getUUID());
			if (level.getGameTime() > read.until()) {
				return;
			}
			bonusing = true;
			try {
				SpiritFoes.strike(level, keeper, entity, base + base * READ_BONUS);
				level.sendParticles(ParticleTypes.ENCHANTED_HIT, entity.getX(), entity.getY(0.6), entity.getZ(), 8, 0.25, 0.3, 0.25, 0.2);
			} finally {
				bonusing = false;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READS.clear());
	}

	private static boolean bind(ServerPlayer player) {
		for (LivingEntity foe : SpiritFoes.cone(player, BIND_REACH, 10.0, true)) {
			if (player.hasLineOfSight(foe)) {
				bindAt(player, foe);
				CastHints.setReach(player, foe.distanceTo(player));
				return true;
			}
		}
		return false;
	}

	/** {@code centre} and every foe near it are held where they stand. */
	static void bindAt(ServerPlayer player, LivingEntity centre) {
		ServerLevel level = (ServerLevel)player.level();
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, centre.getBoundingBox().inflate(BIND_RADIUS),
			e -> e == centre || SpiritFoes.foe(player, e, false))) {
			Staggered.root(player, foe, BIND_TICKS);
			Mana.sendSeen(level, Fx.rune(ROSE, 1.0F), foe.getX(), foe.getY() + 0.2, foe.getZ(), 8, 0.3, 0.1, 0.3, 0.0);
		}
		Fx.sigil(level, centre.position().add(0.0, 0.05, 0.0), (float)BIND_RADIUS, ROSE, BIND_TICKS);
		level.playSound(null, centre.getX(), centre.getY(), centre.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	private static boolean returnAll(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		int turned = 0;
		for (Projectile shot : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(RETURN_RANGE),
			p -> p.isAlive() && p.getOwner() != player && !(p instanceof SpiritJavelin) && p.getDeltaMovement().lengthSqr() > 0.01)) {
			var sender = shot.getOwner();
			Vec3 back = sender != null ? sender.getBoundingBox().getCenter().subtract(shot.position()).normalize().scale(shot.getDeltaMovement().length())
				: shot.getDeltaMovement().scale(-1.0);
			shot.setOwner(player);
			shot.setDeltaMovement(back);
			shot.needsSync = true;
			Mana.sendSeen(level, Fx.wisp(ROSE, 0.8F), shot.getX(), shot.getY(), shot.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
			turned++;
		}
		if (turned == 0) {
			return false;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, 1.6F);
		return true;
	}

	private static boolean read(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		long until = level.getGameTime() + READ_TICKS;
		int n = 0;
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(READ_RANGE),
			e -> SpiritFoes.foe(player, e, false) && e.distanceToSqr(player) <= READ_RANGE * READ_RANGE)) {
			readOne(player, foe, until);
			n++;
		}
		if (n == 0) {
			return false;
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	static void readOne(ServerPlayer player, LivingEntity foe, long until) {
		foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, READ_TICKS, 0, false, false), player);
		READS.put(foe.getUUID(), new Read(player.getUUID(), until));
	}

	private static boolean swap(ServerPlayer player) {
		LivingEntity other = com.jaymoskingdoms.Arcana.sighted(player);
		if (other == null || other instanceof net.minecraft.world.entity.player.Player || other.distanceTo(player) > SWAP_REACH || other.getMaxHealth() > 150.0F) {
			return false;
		}
		swapWith(player, other);
		return true;
	}

	/** {@code player} and {@code other} change places; everything hunting the player loses them. */
	static void swapWith(ServerPlayer player, LivingEntity other) {
		ServerLevel level = (ServerLevel)player.level();
		Vec3 mine = player.position(), theirs = other.position();
		other.teleportTo(mine.x, mine.y, mine.z);
		player.connection.teleport(theirs.x, theirs.y, theirs.z, player.getYRot() + 180.0F, player.getXRot());
		player.fallDistance = 0.0;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(mine, mine).inflate(16.0), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
		Fx.beam(level, mine.add(0.0, 1.0, 0.0), theirs.add(0.0, 1.0, 0.0), 0.1F, ROSE, 8);
		level.playSound(null, theirs.x, theirs.y, theirs.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
	}
}
