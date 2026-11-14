package com.jaymoskingdoms;

import com.jaymoskingdoms.reaped.Reaped;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.spider.Spider;
import org.jspecify.annotations.Nullable;

/**
 * What the Reaper's host ({@link Reaped}, {@link ZombieHordes}) does beyond walking and killing:
 * <ul>
 * <li><b>Riders</b> ({@link #mount}): part of a host rides skeleton horses, and some of its pillagers ride spiders, which
 * take them straight up a wall. Only the dead ride spiders (Andy). A horse whose rider falls is left for whoever wants it.</li>
 * <li><b>The reaped evoker raises</b> ({@link #raise}): where a living evoker calls vexes, it lifts the bodies lying near
 * it (Jaymo's Kitchen's corpses) back into the fight: a dead illager as the same kind of reaped, anything else as the old
 * dead it would rise as for a necromancer. Without Jaymo's Kitchen it keeps its vexes.</li>
 * <li><b>Folk talk</b> ({@link #gossip}): now and then a villager speaks of the horn nobody was blowing.</li>
 * </ul>
 */
public final class ReapedHost {
	/** The share of a host's pillagers and vindicators that ride a skeleton horse, and of its pillagers that ride a spider. */
	static final float HORSE_SHARE = 0.25F, SPIDER_SHARE = 0.2F;
	/** On a host's mount. */
	static final String MOUNT_TAG = "jaymoskingdoms.reaped_mount";
	/** How far the reaped evoker reaches for bodies, and the most it lifts with one casting. */
	public static final double RAISE_RANGE = 12.0;
	static final int RAISE_MOST = 3;
	/** How often a villager's gossip is of the host, and how many lines there are. */
	static final float GOSSIP_CHANCE = 0.05F;
	static final int GOSSIP_LINES = 4;

	private ReapedHost() {
	}

	/** Part of {@code members} (a host just risen) mounts up. */
	static void mount(ServerLevel level, List<Mob> members, RandomSource random) {
		for (Mob m : members) {
			if (!(m instanceof Reaped) || m.isPassenger() || !(m instanceof Pillager || m instanceof Vindicator)) {
				continue;
			}
			float roll = random.nextFloat();
			Mob steed = null;
			if (m instanceof Pillager && roll < SPIDER_SHARE) {
				steed = EntityTypes.SPIDER.create(level, EntitySpawnReason.EVENT);
			} else if (roll < SPIDER_SHARE + HORSE_SHARE) {
				SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.EVENT);
				if (horse != null) {
					horse.setTamed(true);
					horse.setAge(0);
				}
				steed = horse;
			}
			if (steed == null) {
				continue;
			}
			steed.snapTo(m.getX(), m.getY(), m.getZ(), m.getYRot(), 0.0F);
			if (!level.noCollision(steed)) {
				steed.discard();
				continue;
			}
			steed.addTag(MOUNT_TAG);
			level.addFreshEntity(steed);
			m.startRiding(steed, true, false);
		}
	}

	/** The Reaper takes a host's mount back with its rider. */
	static void dismiss(Mob rider) {
		Entity steed = rider.getVehicle();
		if (steed != null && steed.entityTags().contains(MOUNT_TAG)) {
			rider.stopRiding();
			steed.discard();
		}
	}

	/** There is a body within reach of {@code caster} for it to lift. */
	public static boolean bodies(Mob caster) {
		return KitchenCompat.loaded() && !near(caster).isEmpty();
	}

	/** Whether the reaped evoker raises bodies (Jaymo's Kitchen lays them) or keeps its vexes. */
	public static boolean raises() {
		return KitchenCompat.loaded();
	}

	private static List<Entity> near(Mob caster) {
		return caster.level().getEntities((Entity)null, caster.getBoundingBox().inflate(RAISE_RANGE),
			e -> KitchenCompat.isRaisableCorpse(e) && e.distanceToSqr(caster) <= RAISE_RANGE * RAISE_RANGE && risesAs(caster.level() instanceof ServerLevel l ? l : null, e) != null);
	}

	private static @Nullable EntityType<? extends Mob> risesAs(@Nullable ServerLevel level, Entity body) {
		EntityType<?> was = KitchenCompat.sourceType(body);
		if (was == EntityTypes.PILLAGER) {
			return Reaped.pillager();
		}
		if (was == EntityTypes.VINDICATOR) {
			return Reaped.vindicator();
		}
		if (was == EntityTypes.EVOKER || was == EntityTypes.ILLUSIONER) {
			return Reaped.evoker();
		}
		return level == null ? null : Necromancy.risesAs(was, Climate.at(level, body.blockPosition()));
	}

	/** {@code caster} (a reaped evoker) lifts the bodies near it; they fall in with its host. Returns how many got up. */
	public static int raise(Mob caster) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return 0;
		}
		int raised = 0;
		for (Entity body : new ArrayList<>(near(caster))) {
			if (raised >= RAISE_MOST) {
				break;
			}
			EntityType<? extends Mob> as = risesAs(level, body);
			Mob mob = as == null ? null : as.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (mob == null) {
				continue;
			}
			BlockPos pos = body.blockPosition();
			mob.snapTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
			Fx.sigil(level, body.position(), 1.2F, Reaper.RED, 30);
			level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.3F), body.getX(), body.getY() + 0.5, body.getZ(), 12, 0.3, 0.4, 0.3, 0.01);
			body.discard();
			level.addFreshEntity(mob);
			ZombieHordes.enlist(caster, mob);
			raised++;
		}
		if (raised > 0) {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2F, 0.5F);
		}
		return raised;
	}

	/** Now and then, a villager's word on the host (never what it is). */
	static @Nullable Component gossip(ServerLevel level) {
		if (!ZombieHordes.begun(level.getServer()) || level.getRandom().nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		return Component.translatable("talk.jaymoskingdoms.gossip.reaped." + level.getRandom().nextInt(GOSSIP_LINES));
	}
}
