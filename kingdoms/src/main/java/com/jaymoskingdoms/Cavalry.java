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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.WarElephant;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.14) Horses for a realm's folk. A rider's goals steer his horse (a mob riding a mob drives it, as skeleton
 * horsemen do), so a mounted guard keeps his post, fights, and rides at his ruler's back like any other, only faster.
 * A <b>prosperous</b> kingdom ({@link Wealth}) buys horses for {@link #GUARDS_PROSPEROUS} of its guards about the town
 * (never the hall's or the gate's); a <b>wealthy</b> one for {@link #GUARDS_WEALTHY}, and keeps {@link #ARCHERS_WEALTHY}
 * mounted archers riding round the town besides. Each horse costs the treasury {@link #HORSE_COST} emeralds; a horse
 * whose rider fell is ridden again by the next guard without one. Their horses are saddled and wear the realm's
 * colour (leather), or iron for a wealthy realm. War parties of a prosperous realm ride too (a share of them), and
 * caravans' escorts ({@link Caravans}).
 * <p>(0.5.34) Each horse is of a breed ({@link Equestrian}): guards ride destriers, archers coursers, carters and
 * coachmen drafts, and a desert realm's riders desert horses; a wealthy desert realm keeps {@link #ELEPHANTS_ARID}
 * war elephant ridden by one of its guards.
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
	/** (0.5.34) A wealthy desert (or snowy) realm's war elephants (mammoths in the snow, ridden by its guards), what one costs, and its pace under a rider of the realm. */
	static final int ELEPHANTS_ARID = 1, ELEPHANT_COST = 20;
	static final double ELEPHANT_SPEED = 0.24;
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

	static boolean mounted(Entity folk) {
		return horse(folk) != null;
	}

	/** Moves {@code e} with whatever it rides (a rider is put back in the saddle each tick, so moving him alone does nothing). */
	static void teleport(Entity e, double x, double y, double z) {
		if (Attention.held(e)) {
			// (0.7.281) a player's just dealt with the rider: not now
			return;
		}
		e.getRootVehicle().teleportTo(x, y, z);
	}

	/** A horse for {@code rider} of {@code f} (saddled, in its colours), and him on it. {@code lent}: gone when he is. */
	static @Nullable AbstractHorse mount(ServerLevel level, Worker rider, Faction f, boolean lent) {
		return mount(level, rider, f, lent, breedFor(level, rider, f));
	}

	/** (0.5.34) The breed {@code rider} of {@code f} rides: a carter or coachman a draft, an archer a courser, a guard a destrier; in a desert realm, desert horses. */
	static Breed breedFor(ServerLevel level, Worker rider, Faction f) {
		if (rider.role() == Caravans.CARTER) {
			return Breed.DRAFT;
		}
		if (Climate.at(level, f.seat()).arid() && (rider.role() == Folk.ARCHER || level.getRandom().nextBoolean())) {
			return Breed.DESERT_HORSE;
		}
		return rider.role() == Folk.ARCHER ? Breed.COURSER : Breed.DESTRIER;
	}

	/** (0.5.34) A mount of {@code breed} for {@code rider} of {@code f} (a horse, or a war elephant), and him on it. */
	static @Nullable AbstractHorse mount(ServerLevel level, Worker rider, Faction f, boolean lent, Breed breed) {
		// ((0.7.294) a folk's or an MCA villager's: a coachman, a carter)
		AbstractHorse horse = Equestrian.create(level, breed, rider.blockPosition(), EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return null;
		}
		horse.snapTo(rider.getX(), rider.getY(), rider.getZ(), rider.mob().getYRot(), 0.0F);
		dress(level.getServer(), horse, f);
		// (0.5.47) better for the realm's stables
		Stables.equip(level, horse, f, rider);
		horse.addTag(TAG);
		horse.addTag(OF + f.id());
		if (lent) {
			horse.addTag(LENT);
		}
		level.addFreshEntity(horse);
		rider.mob().startRiding(horse);
		return horse;
	}

	/** Tamed, saddled, steady, and in its realm's colour (leather), or iron for a wealthy realm. */
	static void dress(MinecraftServer server, AbstractHorse horse, Faction f) {
		horse.setTamed(true);
		horse.setPersistenceRequired();
		horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(horse instanceof WarElephant ? ELEPHANT_SPEED : HORSE_SPEED);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			horse.setDropChance(slot, 0.0F);
		}
		if (horse instanceof WarElephant) {
			// (its howdah and caparison come with the saddle: no barding)
			return;
		}
		ItemStack armour;
		if (Wealth.of(server, f) == Wealth.Tier.WEALTHY) {
			armour = new ItemStack(Items.IRON_HORSE_ARMOR);
		} else {
			armour = new ItemStack(Items.LEATHER_HORSE_ARMOR);
			armour.set(DataComponents.DYED_COLOR, new DyedItemColor(f.styleColor(server).getTextureDiffuseColor()));
		}
		horse.setItemSlot(EquipmentSlot.BODY, armour);
	}

	/** One of {@code faction}'s horses. */
	static boolean ours(Entity e, int faction) {
		return e instanceof AbstractHorse && e.entityTags().contains(OF + faction);
	}

	/** {@code folk} leaves the world: a lent horse goes with him. */
	static void dismiss(Entity folk) {
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
		for (AbstractHorse horse : level.getEntities(EntityTypeTest.forClass(AbstractHorse.class), h -> h.entityTags().contains(LENT) && !h.isVehicle()
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
			// ((0.7.295) its guards and archers, folk or (with MCA) its villagers)
			List<Worker> folk = Workers.near(level, round, x -> x.isAlive() && x.factionId() == f.id());
			Castle.Keep keep = Factions.keep(server, f.id());
			int wantGuards = tier == Wealth.Tier.WEALTHY ? GUARDS_WEALTHY : GUARDS_PROSPEROUS;
			long riding = folk.stream().filter(x -> x.role() == Folk.GUARD && mounted(x.mob()) && !soldier(x)).count();
			if (riding < wantGuards) {
				List<PathfinderMob> walkers = new ArrayList<>(folk.stream().filter(x -> x.role() == Folk.GUARD && !mounted(x.mob()) && !x.mob().isPassenger()
					&& Rulers.target(x.mob()) == null && !escorting(x) && !soldier(x) && x.task().isEmpty() && level.canSeeSky(x.blockPosition())
					&& !x.mob().isSleeping() && (keep == null || !x.mob().getHomePosition().closerThan(keep.gate(), POST_ON_FOOT)
						&& !x.mob().getHomePosition().closerThan(keep.throne(), POST_ON_FOOT))).map(Worker::mob).toList());
				walkers.sort(Comparator.comparingDouble(x -> x.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(f.seat()))));
				if (!walkers.isEmpty()) {
					PathfinderMob rider = walkers.getFirst();
					// a horse whose rider fell first, else a new one bought
					AbstractHorse spare = level.getEntitiesOfClass(AbstractHorse.class, rider.getBoundingBox().inflate(48.0),
						h -> ours(h, f.id()) && !h.isVehicle() && !h.entityTags().contains(LENT) && h.isAlive()).stream().findFirst().orElse(null);
					if (spare != null) {
						Cavalry.teleport(rider, spare.getX(), spare.getY(), spare.getZ());
						rider.startRiding(spare);
					} else if (tier == Wealth.Tier.WEALTHY && elephantLand(Climate.at(level, f.seat())) && elephants(level, round, f) < ELEPHANTS_ARID) {
						// (0.5.34) a wealthy desert realm's war elephant, a snowy one's woolly mammoth
						if (buy(server, f, ELEPHANT_COST)) {
							mount(level, (Worker)rider, f, false, Climate.at(level, f.seat()) == Climate.SNOWY ? Breed.WOOLLY_MAMMOTH : Breed.WAR_ELEPHANT);
							neigh(level, rider.blockPosition());
						}
					} else if (buy(server, f)) {
						mount(level, (Worker)rider, f, false);
						neigh(level, rider.blockPosition());
					}
				}
			}
			if (tier == Wealth.Tier.WEALTHY) {
				long archers = folk.stream().filter(x -> x.role() == Folk.ARCHER && mounted(x.mob())).count();
				if (archers < ARCHERS_WEALTHY && buy(server, f)) {
					mountedArcher(level, f);
				}
			}
		}
	}

	/** Pays for a horse from the treasury, if it can spare it. */
	private static boolean buy(MinecraftServer server, Faction f) {
		return buy(server, f, HORSE_COST);
	}

	private static boolean buy(MinecraftServer server, Faction f, int cost) {
		Faction now = Factions.get(server, f.id());
		if (now == null || now.treasury() < TREASURY_KEPT + cost) {
			return false;
		}
		Factions.update(server, f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - cost)));
		return true;
	}

	/** (0.5.34) Land whose realms keep war elephants: the desert (elephants) and the snow (mammoths). */
	static boolean elephantLand(Climate climate) {
		return climate.arid() || climate == Climate.SNOWY;
	}

	/** (0.5.34) How many of {@code f}'s war elephants are about its town. */
	private static long elephants(ServerLevel level, AABB round, Faction f) {
		return level.getEntitiesOfClass(WarElephant.class, round, e -> e.isAlive() && ours(e, f.id())).size();
	}

	/** (0.7.295) {@code w} is on a war party ({@link WarParties}). */
	private static boolean soldier(Worker w) {
		return w instanceof Folk folk && WarParties.isSoldier(folk);
	}

	/** (0.7.295) {@code w} rides at a ruler's back. */
	private static boolean escorting(Worker w) {
		return w instanceof Folk folk ? folk.escort() != null : w instanceof Enlisted e && Royals.escortOf(e) != null;
	}

	/** A mounted archer for a wealthy realm (on its roster), riding round its town. */
	private static @Nullable Worker mountedArcher(ServerLevel level, Faction f) {
		BlockPos at = Construction.standable(level, f.seat(), 6);
		if (Workers.inPlace(f, Folk.ARCHER)) {
			// (0.7.295) with MCA, one of its villagers, in the saddle
			Worker w = McaCompat.enlist(level, f, Folk.ARCHER, at, f.seat(), ARCHER_ROAM);
			if (w != null) {
				w.mob().snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
				Factions.update(level.getServer(), f.id(), x -> x.withHoldings(x.holdings().adjust(Folk.ARCHER, 1)));
				mount(level, w, f, false);
				neigh(level, at);
				return w;
			}
		}
		Folk archer = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (archer == null) {
			return null;
		}
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
