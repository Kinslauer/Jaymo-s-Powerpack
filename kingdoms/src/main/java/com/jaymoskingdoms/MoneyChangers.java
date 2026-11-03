package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import com.jaymoskingdoms.money.Changer;

/**
 * (0.7.487) Money changers in the towns: every capital (a kingdom's seat), and every fiefdom whose overlord is at least
 * {@link Wealth.Tier#PROSPEROUS}, keeps one money changer ({@link Changer}), an idle townsperson taken on from the
 * villagers about its seat, the way a cookhouse takes on a cook. They make change at the Counting Table (a villager who
 * finds one takes the job on by himself too) and trade the gems the coinage left behind.
 */
final class MoneyChangers {
	/** How often (ticks) the realms are checked for a changer, and how far (blocks) from its seat one counts as the town's. */
	static final int CHECK_TICKS = 1200;
	static final double TOWN_RANGE = Economy.POPULATION_RANGE;

	private MoneyChangers() {
	}

	static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD && level.getGameTime() % CHECK_TICKS == 7L) {
				check(level);
			}
		});
	}

	/** Whether {@code f}'s town has a money changer: a capital, or a fiefdom of a prosperous kingdom. */
	private static boolean wants(MinecraftServer server, Faction f) {
		if (f.fallen()) {
			return false;
		}
		if (f.type() == FactionType.KINGDOM) {
			return true;
		}
		if (f.type() == FactionType.FIEFDOM) {
			Faction lord = Factions.get(server, f.overlord());
			return lord != null && Wealth.atLeast(server, lord, Wealth.Tier.PROSPEROUS);
		}
		return false;
	}

	private static void check(ServerLevel level) {
		MinecraftServer server = level.getServer();
		for (Faction f : Factions.all(server)) {
			if (!wants(server, f) || !level.isLoaded(f.seat())) {
				continue;
			}
			List<Villager> about = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(TOWN_RANGE), v -> v.isAlive() && !Visitors.is(v));
			if (about.stream().anyMatch(Changer::is)) {
				continue;
			}
			for (Villager v : about) {
				if (!v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.NONE) && !(v instanceof Worker w && w.role() >= 0) && !Rulers.is(v)
					&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())) && !McaCompat.playerFamily(v) && McaCompat.following(v) == null
					&& !v.hasAttached(Emigrants.MOVE)) {
					v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(Changer.PROFESSION)));
					// (a little experience, so the game doesn't take the trade off him for want of a job site)
					v.setVillagerXp(Math.max(1, v.getVillagerXp()));
					v.setPersistenceRequired();
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
					break;
				}
			}
		}
	}
}
