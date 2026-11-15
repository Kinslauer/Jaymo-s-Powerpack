package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.Blessings;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.PatronChampion;
import com.jaymoskingdoms.PatronChampions;
import com.jaymoskingdoms.Patrons;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, cv-avatars; Andy: "dont the patrons have champions?") The old age's temples were the patrons' before
 * the realms' (Lore: the old age's peoples), and an offering on one's altar ({@link OldAltarBlock}) still calls that
 * patron's champion down to test whoever made it: a {@link PatronChampion}, the same multi-phase fight as a temple's,
 * coming down in a beam of the patron's light in front of the altar. The altar goes out until it relights. Beaten, the
 * champion gives what any champion gives ({@code PatronChampions}), and everyone who fought it gets the patron's boon
 * ({@link Blessings#boon}). A wrong offering, or one on a cold altar, just smokes.
 */
public final class OldTemples {
	// ---- tuning ----
	/** A cold altar's chance, at each random tick (about once a minute), to burn again: a few days on average. */
	static final float RELIGHT_CHANCE = 0.02F;
	/** How near the altar the champion comes down (blocks), and how far its fighters may stand for the boon. */
	static final int SPOT_REACH = 4;
	static final double BOON_RANGE = 48.0;
	/** How high its beam comes down from, and how long it lasts (ticks). */
	static final double BEAM_HEIGHT = 28.0;
	static final int BEAM_TICKS = 30;

	/** On a champion an old temple called: its fighters get the boon. */
	static final String CALLED = "jaymoskingdoms.old_temple_champion";

	private OldTemples() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof PatronChampion c) || !c.entityTags().contains(CALLED) || c.decoy() || !(c.level() instanceof ServerLevel level)) {
				return;
			}
			for (UUID id : c.hitters()) {
				ServerPlayer sp = level.getServer().getPlayerList().getPlayer(id);
				if (sp != null && sp.level() == level && sp.isAlive() && sp.distanceTo(c) <= BOON_RANGE) {
					Blessings.boon(sp, c.patron());
				}
			}
		});
	}

	/** {@code sp} lays {@code stack} on the altar at {@code pos}. Whether it was taken. */
	static boolean offer(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer sp, ItemStack stack) {
		Patron p = OldAltarBlock.patron(state);
		if (!state.getValue(OldAltarBlock.LIT) || Patrons.offeringFor(stack) != p
			|| !level.getEntitiesOfClass(PatronChampion.class, new AABB(pos).inflate(40.0), e -> e.entityTags().contains(CALLED)).isEmpty()) {
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.4F);
			level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.15, 0.05, 0.15, 0.01);
			return false;
		}
		EntityType<PatronChampion> type = PatronChampions.typeOf(p);
		PatronChampion champion = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
		if (champion == null) {
			return false;
		}
		BlockPos spot = spot(level, pos, sp);
		Vec3 at = Vec3.atBottomCenterOf(spot);
		float yaw = (float) (Math.toDegrees(Math.atan2(sp.getZ() - at.z, sp.getX() - at.x)) - 90.0);
		champion.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		champion.loose();
		champion.setPersistenceRequired();
		champion.addTag(CALLED);
		level.addFreshEntity(champion);
		champion.setTarget(sp);
		stack.consume(1, sp);
		level.setBlockAndUpdate(pos, state.setValue(OldAltarBlock.LIT, false));
		int colour = p.colour & 0xFFFFFF;
		Fx.beam(level, at.add(0.0, BEAM_HEIGHT, 0.0), at, 1.4F, colour, BEAM_TICKS);
		Fx.ring(level, at.add(0.0, 0.1, 0.0), 3.5F, colour);
		level.playSound(null, spot, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 0.6F);
		level.playSound(null, spot, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.6F, 1.3F);
		return true;
	}

	/** Open ground in front of the altar for the champion to stand on: feet and head clear, footing under. */
	private static BlockPos spot(ServerLevel level, BlockPos altar, ServerPlayer sp) {
		@Nullable BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -SPOT_REACH; dx <= SPOT_REACH; dx++) {
			for (int dz = -SPOT_REACH; dz <= SPOT_REACH; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos at = altar.offset(dx, dy, dz);
					if (Math.abs(dx) + Math.abs(dz) < 2 || !level.getBlockState(at).getCollisionShape(level, at).isEmpty()
						|| !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
						|| !level.getBlockState(at.above(2)).getCollisionShape(level, at.above(2)).isEmpty()
						|| !level.getBlockState(at.below()).isSolid()) {
						continue;
					}
					// halfway between the altar and whoever made the offering
					double d = at.distToCenterSqr(sp.position()) + at.distSqr(altar);
					if (d < bestD) {
						bestD = d;
						best = at;
					}
				}
			}
		}
		return best != null ? best : sp.blockPosition();
	}
}
