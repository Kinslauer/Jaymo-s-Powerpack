package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.19) Horses for a realm's folk. A rider's goals steer his horse (a mob riding a mob drives it, as skeleton
 * horsemen do), so a mounted guard keeps his post, fights, and rides at his ruler's back like any other, only faster.
 * A <b>prosperous</b> kingdom ({@link Wealth}) buys horses for {@link #GUARDS_PROSPEROUS} of its guards about the town
 * (never the hall's or the gate's); a <b>wealthy</b> one for {@link #GUARDS_WEALTHY}, and keeps {@link #ARCHERS_WEALTHY}
 * mounted archers riding round the town besides. Each horse costs the treasury {@link #HORSE_COST} emeralds; a horse
 * whose rider fell is ridden again by the next guard without one. Their horses are saddled and wear the realm's
 * colour (leather), or iron for a wealthy realm. War parties of a prosperous realm ride too (a share of them), and
 * caravans' escorts ({@link Caravans}).
 */
final class Cavalry {
	/** Every this many ticks each realm's horses are seen to. */
	static final int CHECK_TICKS = 300;
	/** Mounted guards about the town: a prosperous realm's, a wealthy one's; a wealthy realm's mounted archers. */
	static final int GUARDS_PROSPEROUS = 2, GUARDS_WEALTHY = 4, ARCHERS_WEALTHY = 2;
	/** A horse's price (emeralds), and what a realm keeps in its treasury after buying one. */
	static final int HORSE_COST = 6, TREASURY_KEPT = 40;
	/** A war party of a prosperous realm: one soldier in this many rides. */
	static final int PARTY_RIDER_ONE_IN = 3;
	/** Its horses: their speed (a horse's own runs 0.11-0.34), how far round the seat the realm's horses are looked for. */
	static final double HORSE_SPEED = 0.3, RANGE = 112.0;
	/** Mounted archers ride round the town within this of the seat. */
	static final int ARCHER_ROAM = 44;
	/** A guard posted this close to the gate or the throne keeps his feet (halls and gateways are no place for a horse). */
	static final double POST_ON_FOOT = 9.0;

	/** Tags on a realm's horses: one of ours, whose (its id after the dot), and one only lent (a war party's, a caravan's: gone with its rider). */
	static final String TAG = "jaymoskingdoms.horse", OF = "jaymoskingdoms.horse_of.", LENT = "jaymoskingdoms.horse_lent";

	private Cavalry() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % CHECK_TICKS == 0) {
				tick(server, level);
			}
		});
		// a realm's horse can't smother in a wall: its head brushes through leaves and eaves, and one set down with its
		// rider (a stuck caravan, a king's carriage) may land a little close to one
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof AbstractHorse
			&& source.is(DamageTypes.IN_WALL) && entity.entityTags().contains(TAG)));
	}

	/** {@code e}'s horse, if it rides one. */
	static @Nullable AbstractHorse horse(@Nullable Entity e) {
		return e != null && e.getVehicle() instanceof AbstractHorse horse ? horse : null;
	}

	static boolean mounted(Folk folk) {
		return horse(folk) != null;
	}

	/** Moves {@code e} with whatever it rides (a rider is put back in the saddle each tick, so moving him alone does nothing). */
	static void teleport(Entity e, double x, double y, double z) {
		e.getRootVehicle().teleportTo(x, y, z);
	}

	/** A horse for {@code rider} of {@code f} (saddled, in its colours), and him on it. {@code lent}: gone when he is. */
	static @Nullable Horse mount(ServerLevel level, Folk rider, Faction f, boolean lent) {
		Horse horse = net.minecraft.world.entity.EntityTypes.HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return null;
		}
		horse.snapTo(rider.getX(), rider.getY(), rider.getZ(), rider.getYRot(), 0.0F);
		horse.finalizeSpawn(level, level.getCurrentDifficultyAt(rider.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
		dress(level.getServer(), horse, f);
		horse.addTag(TAG);
		horse.addTag(OF + f.id());
		if (lent) {
			horse.addTag(LENT);
		}
		level.addFreshEntity(horse);
		rider.startRiding(horse);
		return horse;
	}

	/** Tamed, saddled, steady, and in its realm's colour (leather), or iron for a wealthy realm. */
	static void dress(MinecraftServer server, AbstractHorse horse, Faction f) {
		horse.setTamed(true);
		horse.setPersistenceRequired();
		horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(HORSE_SPEED);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		ItemStack armour;
		if (Wealth.of(server, f) == Wealth.Tier.WEALTHY) {
			armour = new ItemStack(Items.IRON_HORSE_ARMOR);
		} else {
			armour = new ItemStack(Items.LEATHER_HORSE_ARMOR);
			armour.set(DataComponents.DYED_COLOR, new DyedItemColor(f.styleColor(server).getTextureDiffuseColor()));
		}
		horse.setItemSlot(EquipmentSlot.BODY, armour);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			horse.setDropChance(slot, 0.0F);
		}
	}

	/** One of {@code faction}'s horses. */
	static boolean ours(Entity e, int faction) {
		return e instanceof AbstractHorse && e.entityTags().contains(OF + faction);
	}

	/** {@code folk} leaves the world: a lent horse goes with him. */
	static void dismiss(Folk folk) {
		AbstractHorse horse = horse(folk);
		if (horse != null && horse.entityTags().contains(LENT)) {
			horse.discard();
		}
	}

	/** A lent horse left without its rider (he fell, or was sent home): gone in a puff. */
	static void strayed(ServerLevel level, AbstractHorse horse) {
		level.sendParticles(ParticleTypes.POOF, horse.getX(), horse.getY() + 1.0, horse.getZ(), 10, 0.5, 0.5, 0.5, 0.02);
		horse.discard();
	}

	// ---- each realm's riders ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server, ServerLevel level) {
		// lent horses whose riders are gone (a war party's, a caravan's)
		// (a caravan sees to its own: its riders get back in the saddle after a river)
		for (Horse horse : level.getEntities(net.minecraft.world.entity.EntityTypes.HORSE, h -> h.entityTags().contains(LENT) && !h.isVehicle()
			&& !h.entityTags().contains(Caravans.TAG))) {
			strayed(level, horse);
		}
		for (Faction f : Factions.all(server, FactionType.KINGDOM)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat()) || Economy.underAttack(server, f)) {
				continue;
			}
			AABB round = new AABB(f.seat()).inflate(RANGE);
			Wealth.Tier tier = Wealth.of(server, f);
			if (tier == Wealth.Tier.POOR) {
				continue;
			}
			List<Folk> folk = level.getEntitiesOfClass(Folk.class, round, x -> x.isAlive() && x.factionId() == f.id());
			Castle.Keep keep = Factions.keep(server, f.id());
			int wantGuards = tier == Wealth.Tier.WEALTHY ? GUARDS_WEALTHY : GUARDS_PROSPEROUS;
			long riding = folk.stream().filter(x -> x.role() == Folk.GUARD && mounted(x) && !WarParties.isSoldier(x)).count();
			if (riding < wantGuards) {
				List<Folk> walkers = new ArrayList<>(folk.stream().filter(x -> x.role() == Folk.GUARD && !mounted(x) && !x.isPassenger()
					&& x.getTarget() == null && x.escort() == null && !WarParties.isSoldier(x) && x.task().isEmpty() && level.canSeeSky(x.blockPosition())
					&& (keep == null || !x.getHomePosition().closerThan(keep.gate(), POST_ON_FOOT) && !x.getHomePosition().closerThan(keep.throne(),
						POST_ON_FOOT))).toList());
				walkers.sort(Comparator.comparingDouble(x -> x.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(f.seat()))));
				if (!walkers.isEmpty()) {
					Folk rider = walkers.getFirst();
					// a horse whose rider fell first, else a new one bought
					AbstractHorse spare = level.getEntitiesOfClass(AbstractHorse.class, rider.getBoundingBox().inflate(48.0),
						h -> ours(h, f.id()) && !h.isVehicle() && !h.entityTags().contains(LENT) && h.isAlive()).stream().findFirst().orElse(null);
					if (spare != null) {
						Cavalry.teleport(rider, spare.getX(), spare.getY(), spare.getZ());
						rider.startRiding(spare);
					} else if (buy(server, f)) {
						mount(level, rider, f, false);
						neigh(level, rider.blockPosition());
					}
				}
			}
			if (tier == Wealth.Tier.WEALTHY) {
				long archers = folk.stream().filter(x -> x.role() == Folk.ARCHER && mounted(x)).count();
				if (archers < ARCHERS_WEALTHY && buy(server, f)) {
					mountedArcher(level, f);
				}
			}
		}
	}

	/** Pays for a horse from the treasury, if it can spare it. */
	private static boolean buy(MinecraftServer server, Faction f) {
		Faction now = Factions.get(server, f.id());
		if (now == null || now.treasury() < TREASURY_KEPT + HORSE_COST) {
			return false;
		}
		Factions.update(server, f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - HORSE_COST)));
		return true;
	}

	/** A mounted archer for a wealthy realm (on its roster), riding round its town. */
	private static @Nullable Folk mountedArcher(ServerLevel level, Faction f) {
		Folk archer = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (archer == null) {
			return null;
		}
		BlockPos at = Construction.standable(level, f.seat(), 6);
		archer.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		archer.setup(Folk.ARCHER, f, f.seat(), ARCHER_ROAM);
		level.addFreshEntity(archer);
		Factions.update(level.getServer(), f.id(), x -> x.withHoldings(x.holdings().adjust(Folk.ARCHER, 1)));
		mount(level, archer, f, false);
		neigh(level, at);
		JaymosKingdoms.LOGGER.debug("{} put a mounted archer in the saddle", f.name());
		return archer;
	}

	private static void neigh(ServerLevel level, BlockPos at) {
		level.playSound(null, at, SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}
}
