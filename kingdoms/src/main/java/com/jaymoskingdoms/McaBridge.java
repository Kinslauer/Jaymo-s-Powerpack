package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Pregnancy;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The half of {@link McaCompat} that names MCA's classes: only called while MCA is loaded. */
final class McaBridge {
	/** An MCA village counts as a faction's when its border (plus this) takes in the faction's seat. */
	static final int SEAT_MARGIN = 32;

	/** The villager a player is promoting just now ({@link #promote}): {@link #enlist} takes them, not someone else. */
	private static @Nullable VillagerEntityMCA chosen;

	private McaBridge() {
	}

	/** {@code player}'s reputation (summed hearts) with the MCA village round {@code seat}, or null if there's none. */
	static @Nullable Integer villageHearts(ServerLevel level, BlockPos seat, ServerPlayer player) {
		return VillageManager.get(level).findNearestVillage(seat, SEAT_MARGIN).map(v -> v.getReputation(player)).orElse(null);
	}

	static boolean isMca(Villager villager) {
		return villager instanceof VillagerEntityMCA;
	}

	/** (0.5.29) Hearts with {@code player} in an MCA villager's memory. */
	static void hearts(Villager villager, ServerPlayer player, int hearts) {
		if (villager instanceof VillagerEntityMCA v) {
			v.getVillagerBrain().getMemoriesForPlayer(player).modHearts(hearts);
		}
	}

	/** (0.5.39) Hearts with {@code player} in an MCA villager's memory (0 for a plain one). */
	static int heartsOf(Villager villager, ServerPlayer player) {
		return villager instanceof VillagerEntityMCA v ? v.getVillagerBrain().getMemoriesForPlayer(player).getHearts() : 0;
	}

	/** (0.5.50) An MCA villager's mood value (-15..15; 0 for a plain one). */
	static int mood(Villager villager) {
		return villager instanceof VillagerEntityMCA v ? v.getVillagerBrain().getMoodValue() : 0;
	}

	/** (0.5.50) An MCA villager is panicking. */
	static boolean panicking(Villager villager) {
		return villager instanceof VillagerEntityMCA v && v.getVillagerBrain().isPanicking();
	}

	// ---- workers (0.5.21) -------------------------------------------------------------------------------------------

	/** The workers' professions are important (MCA never makes them guards or lets them lapse), and they don't trade. */
	static void register() {
		for (VillagerProfession profession : List.of(Professions.BUILDER, Professions.FARMER, Professions.MINER, Professions.WOODCUTTER, Professions.PULLER,
			Professions.STABLEHAND, Professions.FARRIER, Professions.WIZARD)) {
			ResourceKey<VillagerProfession> key = BuiltInRegistries.VILLAGER_PROFESSION.getResourceKey(profession).orElseThrow();
			ProfessionsMCA.IS_IMPORTANT.add(key);
			ProfessionsMCA.CAN_NOT_TRADE.add(key);
		}
	}

	private static Enlisted worker(VillagerEntityMCA villager) {
		return (Enlisted)(Object)villager;
	}

	static @Nullable Worker enlist(ServerLevel level, Faction f, int role, BlockPos at, BlockPos home, int radius) {
		VillagerProfession job = role == Folk.GUARD ? (level.getRandom().nextInt(3) == 0 ? ProfessionsMCA.ARCHER : ProfessionsMCA.GUARD) : Professions.of(role);
		if (job == null) {
			return null;
		}
		VillagerEntityMCA villager = chosen != null ? chosen : candidate(level, f);
		if (villager == null) {
			// nobody to spare: someone new moves in to take the work
			villager = VillagerFactory.newVillager(level).withPosition(Vec3.atBottomCenterOf(at)).withAge(0).spawn(EntitySpawnReason.MOB_SUMMONED);
		}
		if (villager.getProfession() != job) {
			villager.setProfession(job);
		}
		Enlisted w = worker(villager);
		w.enlist(role, f.id());
		villager.setHomeTo(home, radius);
		if (Worker.isWork(role)) {
			// (a guard keeps the arms MCA gives them)
			w.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
			villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		JaymosKingdoms.LOGGER.debug("{} made {} its {}", f.name(), villager.getName().getString(), Folk.roleName(role));
		return w;
	}

	/**
	 * The villager {@code f} promotes: a grown one of its own who isn't its ruler, a guard or anything else MCA counts
	 * important, nor already at work; one without a trade first, then one who hasn't started theirs, then anyone.
	 */
	private static @Nullable VillagerEntityMCA candidate(ServerLevel level, Faction f) {
		List<VillagerEntityMCA> all = level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
			v -> v.isAlive() && !v.isBaby() && worker(v).role() < 0 && !v.isProfessionImportant()
				&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())));
		for (int tier = 0; tier < 3; tier++) {
			List<VillagerEntityMCA> pick = new ArrayList<>();
			for (VillagerEntityMCA v : all) {
				if (tier(v) == tier) {
					pick.add(v);
				}
			}
			if (!pick.isEmpty()) {
				return pick.get(level.getRandom().nextInt(pick.size()));
			}
		}
		return null;
	}

	private static int tier(VillagerEntityMCA v) {
		var profession = v.getVillagerData().profession();
		if (profession.is(VillagerProfession.NONE) || profession.is(VillagerProfession.NITWIT)) {
			return 0;
		}
		return v.getVillagerXp() == 0 && v.getVillagerData().level() <= 1 ? 1 : 2;
	}

	static List<Worker> workers(ServerLevel level, AABB box, Predicate<Worker> test) {
		List<Worker> found = new ArrayList<>();
		for (VillagerEntityMCA v : level.getEntitiesOfClass(VillagerEntityMCA.class, box, v -> worker(v).role() >= 0 && test.test(worker(v)))) {
			found.add(worker(v));
		}
		return found;
	}

	static boolean reconcile(ServerLevel level, Worker w) {
		if (!(w.mob() instanceof VillagerEntityMCA villager)) {
			return false;
		}
		Enlisted e = worker(villager);
		if (Hermits.is(e)) {
			// (0.5.62) a floating island's hermit keeps their robe and their island, whatever MCA's menu says
			if (villager.getProfession() != Professions.WIZARD) {
				villager.setProfession(Professions.WIZARD);
			}
			return true;
		}
		if (e.role() < 0 && Hermits.due(villager) && Hermits.onSkyIsland(level, villager.blockPosition())) {
			hermit(level, villager, e);
			return true;
		}
		int job = villager.isGuard() ? Folk.GUARD : Professions.role(villager.getVillagerData().profession());
		MinecraftServer server = level.getServer();
		if (e.role() >= 0) {
			Faction f = Factions.get(server, e.factionId());
			boolean fits = job >= 0 && (Folk.isBuilder(e.role()) ? Folk.isBuilder(job) : job == e.role());
			if (fits && f != null && !f.fallen() && !villager.isBaby()) {
				return true;
			}
			// given another trade (MCA's menu, a guard's post), or their settlement's gone: they're let go, a place to fill
			if (f != null) {
				Economy.lost(server, f.id(), e.role());
			}
			e.discharge();
			if (job >= 0 && job != Folk.GUARD) {
				noTrade(villager);
			}
			return false;
		}
		if (job == Sorcery.WIZARD) {
			// (0.5.53) a wizard's robe on someone who isn't one (the villager editor, say): only a kingdom makes a court wizard
			noTrade(villager);
			return false;
		}
		if (job == Folk.GUARD) {
			// an MCA guard of a realm's village: one of its guards, counted on its roster
			Faction f = settlement(level, villager);
			if (f != null && f.type().isRealm()) {
				e.enlist(Folk.GUARD, f.id());
				Factions.update(server, f.id(), x -> x.withHoldings(x.holdings().adjust(Folk.GUARD, 1)));
				return true;
			}
			return false;
		}
		// made a builder (or the like) some other way, the villager editor say: taken on, if there's work for them
		return job >= 0 && promote(level, villager, job, null);
	}

	/**
	 * {@code villager} takes on {@code role} for the settlement they're in, through the code that hires one (a farmer
	 * goes to the farm with fewest farmers, a builder joins the crew, its foreman if it has none). {@code player} (if
	 * any) is told how it went. True if they're at work now.
	 */
	static boolean promote(ServerLevel level, Villager villager, int role, @Nullable ServerPlayer player) {
		if (!(villager instanceof VillagerEntityMCA v) || role < 0) {
			return false;
		}
		Enlisted e = worker(v);
		if (e.role() >= 0 && (Folk.isBuilder(role) ? Folk.isBuilder(e.role()) : e.role() == role)) {
			return true;
		}
		MinecraftServer server = level.getServer();
		Faction f = settlement(level, v);
		if (f == null || v.isBaby()) {
			tell(player, Component.translatable("message.jaymoskingdoms.job_no_settlement", v.getName()));
			noTrade(v);
			return false;
		}
		if (e.role() >= 0) {
			// (from one job to another: the old place is to be filled)
			Faction old = Factions.get(server, e.factionId());
			if (old != null) {
				Economy.lost(server, old.id(), e.role());
			}
			e.discharge();
		}
		Worker hired;
		chosen = v;
		try {
			if (Folk.isBuilder(role)) {
				hired = Builders.spawn(level, f, Builders.foreman(level, f) == null ? Folk.FOREMAN : Folk.WORKER, null);
			} else if (role == Folk.FARMER) {
				hired = Farmers.spawn(level, f, null);
			} else if (role == Folk.PULLER) {
				hired = Rickshaws.spawn(level, f, null);
			} else if (Stables.isStableWorker(role)) {
				hired = Stables.spawn(level, f, role, null);
			} else {
				hired = Gatherers.spawn(level, f, role, null);
			}
		} finally {
			chosen = null;
		}
		if (hired == null || hired.mob() != v) {
			tell(player, Component.translatable("message.jaymoskingdoms.job_nothing." + Folk.roleName(role), v.getName(), f.name()));
			noTrade(v);
			return false;
		}
		VillagerProfession job = Professions.of(hired.role());
		tell(player, Component.translatable("message.jaymoskingdoms.job_taken", v.getName(), f.name(), job == null ? Component.empty() : job.name()));
		return true;
	}

	// ---- court wizards (0.5.53) -------------------------------------------------------------------------------------

	static @Nullable Worker enlistWizard(ServerLevel level, Faction f, @Nullable PathfinderMob chosen) {
		VillagerEntityMCA villager = chosen instanceof VillagerEntityMCA v ? v : eldest(level, f);
		if (villager == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		Enlisted w = worker(villager);
		if (w.role() >= 0) {
			// (an old hand leaves their work for the office: that place is to be filled)
			Faction old = Factions.get(server, w.factionId());
			if (old != null) {
				Economy.lost(server, old.id(), w.role());
			}
			w.discharge();
		}
		if (villager.getProfession() != Professions.WIZARD) {
			villager.setProfession(Professions.WIZARD);
		}
		w.enlist(Sorcery.WIZARD, f.id());
		villager.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Arcana.WIZARD_STAFF));
		villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		villager.setPersistenceRequired();
		Castle.Keep keep = Factions.keep(server, f.id());
		villager.setHomeTo(keep != null ? keep.hall() : f.seat(), Folk.HOME_RADIUS);
		Factions.update(server, f.id(), x -> x.withHoldings(x.holdings().adjust(Sorcery.WIZARD, 1)));
		level.sendParticles(ParticleTypes.ENCHANT, villager.getX(), villager.getY() + 1.2, villager.getZ(), 40, 0.4, 0.8, 0.4, 0.6);
		level.playSound(null, villager.blockPosition(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.NEUTRAL, 0.8F, 1.2F);
		JaymosKingdoms.LOGGER.debug("{} made {} its court wizard", f.name(), villager.getName().getString());
		return w;
	}

	/** (0.5.62) {@code villager}, found on a floating island, made its hermit wizard ({@link Hermits}): no kingdom's. */
	private static void hermit(ServerLevel level, VillagerEntityMCA villager, Enlisted e) {
		if (villager.getProfession() != Professions.WIZARD) {
			villager.setProfession(Professions.WIZARD);
		}
		e.enlist(Sorcery.WIZARD, Hermits.FACTION);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Arcana.WIZARD_STAFF));
		villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		villager.setHomeTo(villager.blockPosition(), Hermits.HOME_RADIUS);
		Hermits.settled(level, villager);
	}

	/** The eldest of {@code f}'s villagers who is an elder, isn't its ruler, nor at work, nor a guard or anything MCA keeps; or null. */
	private static @Nullable VillagerEntityMCA eldest(ServerLevel level, Faction f) {
		VillagerEntityMCA best = null;
		long bestDays = -1;
		for (VillagerEntityMCA v : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
			v -> v.isAlive() && !v.isBaby() && worker(v).role() < 0 && !v.isProfessionImportant()
				&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())))) {
			long days = CourtWizards.grownDays(level, worker(v));
			if (days >= CourtWizards.ELDER_DAYS && days > bestDays) {
				best = v;
				bestDays = days;
			}
		}
		return best;
	}

	static @Nullable PathfinderMob nearestGrown(ServerLevel level, ServerPlayer player) {
		return level.getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(8.0), v -> v.isAlive() && !v.isBaby()).stream()
			.min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
	}

	/** The settlement {@code villager} is in (a standing one, not a camp), or null. */
	private static @Nullable Faction settlement(ServerLevel level, Villager villager) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.around(server, level, villager.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			f = Factions.at(server, level, villager.blockPosition());
		}
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	private static void noTrade(VillagerEntityMCA villager) {
		if (Professions.role(villager.getVillagerData().profession()) >= 0) {
			villager.setProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
		}
	}

	private static void tell(@Nullable ServerPlayer player, Component message) {
		if (player != null) {
			player.sendSystemMessage(message.copy().withStyle(ChatFormatting.YELLOW));
		}
	}

	static boolean busy(PathfinderMob mob) {
		return mob instanceof VillagerEntityMCA v && v.getInteractions().getInteractingPlayer().isPresent();
	}

	/** MCA's hair dye: none (their own colour), or the grey and white of {@link OldAge}. */
	private static final int UNDYED = 0xFF000000, GREY = 0xFF9C9A98, WHITE = 0xFFE2E0DC;

	static void greyHair(PathfinderMob mob, boolean frail) {
		if (mob instanceof VillagerEntityMCA v) {
			int dye = v.getHairDye();
			int want = frail ? WHITE : GREY;
			if (dye != want && (dye == UNDYED || dye == GREY)) {
				v.setHairDye(want);
			}
		}
	}

	/** A villager takes {@code folk}'s place: the same job for the same faction, at the same work, with its load. */
	static boolean convert(ServerLevel level, Folk folk) {
		Faction f = Factions.get(level.getServer(), folk.factionId());
		VillagerProfession job = Professions.of(folk.role());
		if (f == null || job == null || !Workers.inPlace(f, folk.role())) {
			return false;
		}
		VillagerEntityMCA villager = VillagerFactory.newVillager(level).withPosition(folk.position()).withAge(0).withProfession(job)
			.spawn(EntitySpawnReason.CONVERSION);
		Enlisted w = worker(villager);
		w.enlist(folk.role(), f.id());
		villager.setHomeTo(folk.getHomePosition(), folk.getHomeRadius());
		w.setSite(folk.site());
		w.setTask(folk.task());
		w.setCarried(folk.takeCarried());
		villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		folk.discard();
		return true;
	}

	// ---- families (0.6.9) -------------------------------------------------------------------------------------------

	/**
	 * {@link Families}: if the settlement in {@code box} has few young ones, a couple there has a baby (born a toddler,
	 * as MCA's are), soon if there are no young ones at all; with no couple to have one, a toddler is taken in
	 * ({@code takeIn}). Returns what happened: 0 nothing, 1 a baby on the way, 2 a toddler taken in.
	 */
	static int families(ServerLevel level, AABB box, int grownPerYoung, int leastGrown, float chance, int soonUnits, boolean takeIn) {
		List<VillagerEntityMCA> all = level.getEntitiesOfClass(VillagerEntityMCA.class, box, VillagerEntityMCA::isAlive);
		List<VillagerEntityMCA> grown = new ArrayList<>();
		int young = 0;
		for (VillagerEntityMCA v : all) {
			if (v.getAgeState() == AgeState.ADULT) {
				grown.add(v);
				if (v.getRelationships().getPregnancy().isPregnant()) {
					young++;
				}
			} else {
				young++;
			}
		}
		if (grown.size() < leastGrown || young * grownPerYoung >= grown.size() || young > 0 && level.getRandom().nextFloat() >= chance) {
			return 0;
		}
		java.util.Collections.shuffle(grown, new java.util.Random(level.getRandom().nextLong()));
		for (VillagerEntityMCA v : grown) {
			if (v.getRelationships().getPregnancy().isPregnant() || !v.getRelationships().getPregnancy().tryStartGestation()) {
				continue;
			}
			if (young == 0) {
				// (the first is due soon: whichever of the two carries it)
				int due = Config.getInstance().babyItemGrowUpTime;
				for (VillagerEntityMCA m : grown) {
					Pregnancy p = m.getRelationships().getPregnancy();
					if (p.isPregnant()) {
						p.setBabyAge(Math.max(p.getBabyAge(), due - soonUnits));
					}
				}
			}
			return 1;
		}
		if (young > 0 || !takeIn || grown.isEmpty()) {
			return 0;
		}
		// (no couple to have one: a little one is taken in, beside someone grown)
		VillagerEntityMCA by = grown.getFirst();
		VillagerEntityMCA toddler = VillagerFactory.newVillager(level).withPosition(by.position()).withAge(AgeState.TODDLER.toAge())
			.spawn(EntitySpawnReason.BREEDING);
		if (toddler == null) {
			return 0;
		}
		level.sendParticles(ParticleTypes.HEART, toddler.getX(), toddler.getY() + 1.0, toddler.getZ(), 5, 0.3, 0.3, 0.3, 0.0);
		return 2;
	}

	/** (0.6.9) Where {@code villager} lives (their bed), if MCA knows. */
	static java.util.Optional<net.minecraft.core.GlobalPos> home(Villager villager) {
		return villager instanceof VillagerEntityMCA v ? v.getResidency().getHome() : java.util.Optional.empty();
	}
}
