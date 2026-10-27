package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Messenger;
import net.conczin.mca.entity.ai.Pregnancy;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.resources.Names;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

	/** (0.7.92) The name {@code player} gave themselves in MCA's creator (its family tree), or their username if none. */
	static String name(ServerPlayer player) {
		return Messenger.getName(player);
	}

	/** (0.7.160) Writes {@code name} into MCA's family tree as {@code player}'s, so MCA and everything reading it go by it. */
	static void setName(ServerPlayer player, String name) {
		FamilyTree tree = FamilyTree.get(player.level());
		tree.getOrCreate(player).setName(name);
		tree.setDirty();
	}

	/** (0.7.160) A name MCA would give a villager of {@code player}'s gender, or null if its name lists aren't loaded. */
	static @Nullable String randomName(ServerPlayer player) {
		try {
			String name = Names.pickCitizenName(PlayerSaveData.get(player).getGender(), player);
			return name == null || name.isBlank() ? null : name.strip();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** (0.7.236) An MCA villager's skin brought into {@code kind}'s range ({@link SkinTones#bornSkin}); anyone else: nothing. */
	static void keepSkin(net.minecraft.world.entity.Entity entity, Heritage.Kind kind) {
		if (entity instanceof VillagerEntityMCA v) {
			Genetics genes = v.getGenetics();
			float melanin = genes.getGene(Genetics.MELANIN), kept = SkinTones.bornSkin(kind, melanin, v.getRandom());
			if (kept != melanin) {
				genes.setGene(Genetics.MELANIN, kept);
			}
		}
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

	/** (0.7.20) {@code player} is family to an MCA villager. */
	static boolean family(Villager villager, ServerPlayer player) {
		return villager instanceof VillagerEntityMCA v && net.conczin.mca.entity.ai.Relationship.IS_FAMILY.test(v, (net.minecraft.world.entity.Entity)player);
	}

	/** (0.5.50) An MCA villager's mood value (-15..15; 0 for a plain one). */
	static int mood(Villager villager) {
		return villager instanceof VillagerEntityMCA v ? v.getVillagerBrain().getMoodValue() : 0;
	}

	/** (0.7.19) {@link McaCompat#kin}. */
	static int kin(Villager a, Villager b) {
		if (!(a instanceof VillagerEntityMCA x) || !(b instanceof VillagerEntityMCA y)) {
			return McaCompat.STRANGERS;
		}
		EntityRelationship r = x.getRelationships();
		if (r.isMarriedTo(y.getUUID()) || r.isEngagedWith(y.getUUID()) || r.isPromisedTo(y.getUUID())) {
			return McaCompat.PARTNER;
		}
		return r.getFamilyEntry().isRelative(y.getUUID()) ? McaCompat.KIN : McaCompat.STRANGERS;
	}

	/** (0.7.132) {@link McaCompat#kinOf}: their spouse (or betrothed), family, or no one to them. */
	static int kinOf(Villager a, java.util.UUID dead) {
		if (!(a instanceof VillagerEntityMCA x) || x.getUUID().equals(dead)) {
			return McaCompat.STRANGERS;
		}
		EntityRelationship r = x.getRelationships();
		if (r.isMarriedTo(dead) || r.isEngagedWith(dead) || r.isPromisedTo(dead) || dead.equals(r.getFamilyEntry().partner())) {
			return McaCompat.PARTNER;
		}
		return r.getFamilyEntry().isRelative(dead) ? McaCompat.KIN : McaCompat.STRANGERS;
	}

	/** (0.7.211) An MCA villager's personality id ("relaxed", "crabby"...; "" for a plain one). */
	static String personality(Villager v) {
		return v instanceof VillagerEntityMCA m ? m.getVillagerBrain().getPersonality().getId().getPath() : "";
	}

	/** (0.7.19) {@link McaCompat#temper}. */
	static int temper(Villager v) {
		if (!(v instanceof VillagerEntityMCA m)) {
			return 0;
		}
		return switch (m.getVillagerBrain().getPersonality().getId().getPath()) {
			case "friendly", "upbeat", "peaceful", "extroverted", "relaxed" -> 1;
			case "greedy", "gloomy", "anxious" -> -1;
			case "crabby" -> -2;
			default -> 0;
		};
	}

	/** (0.7.19) {@link McaCompat#courtable}. */
	static boolean courtable(Villager a, Villager b) {
		if (!(a instanceof VillagerEntityMCA x) || !(b instanceof VillagerEntityMCA y) || x.getAgeState() != AgeState.ADULT
			|| y.getAgeState() != AgeState.ADULT) {
			return false;
		}
		EntityRelationship rx = x.getRelationships(), ry = y.getRelationships();
		Gender gx = rx.getGender(), gy = ry.getGender();
		boolean pair = gx == Gender.MALE && gy == Gender.FEMALE || gx == Gender.FEMALE && gy == Gender.MALE;
		return pair && unattached(rx) && unattached(ry) && !rx.getFamilyEntry().isRelative(y.getUUID());
	}

	private static boolean unattached(EntityRelationship r) {
		return !r.isMarried() && !r.isEngaged() && !r.isPromised();
	}

	/** (0.7.19) {@link McaCompat#cheer}. */
	static void cheer(Villager v, int by) {
		if (v instanceof VillagerEntityMCA m) {
			m.getVillagerBrain().modifyMoodValue(by);
		}
	}

	/** (0.7.19) {@link McaCompat#marry}: both sides of the family tree learn of it. */
	static boolean marry(Villager a, Villager b) {
		if (!(a instanceof VillagerEntityMCA x) || !(b instanceof VillagerEntityMCA y)) {
			return false;
		}
		x.getRelationships().marry(y);
		y.getRelationships().marry(x);
		return true;
	}

	/** (0.7.21) {@link McaCompat#following}. */
	static java.util.@Nullable UUID following(Villager v) {
		if (!(v instanceof VillagerEntityMCA m) || m.getVillagerBrain().getMoveState() != net.conczin.mca.entity.ai.MoveState.FOLLOW) {
			return null;
		}
		return m.getBrain().getMemory(net.conczin.mca.entity.ai.MemoryModuleTypeMCA.PLAYER_FOLLOWING).map(net.minecraft.world.entity.Entity::getUUID).orElse(null);
	}

	/** (0.7.21) {@link McaCompat#stopFollowing}. */
	static void stopFollowing(Villager v) {
		if (v instanceof VillagerEntityMCA m && m.getVillagerBrain().getMoveState() == net.conczin.mca.entity.ai.MoveState.FOLLOW) {
			m.getVillagerBrain().setMoveState(net.conczin.mca.entity.ai.MoveState.MOVE, null);
		}
	}

	/** (0.7.220) {@link McaCompat#follow}. */
	static boolean follow(Villager v, net.minecraft.world.entity.player.Player player) {
		if (!(v instanceof VillagerEntityMCA m)) {
			return false;
		}
		m.getVillagerBrain().setMoveState(net.conczin.mca.entity.ai.MoveState.FOLLOW, player);
		return true;
	}

	/** (0.5.50) An MCA villager is panicking. */
	static boolean panicking(Villager villager) {
		return villager instanceof VillagerEntityMCA v && v.getVillagerBrain().isPanicking();
	}

	// ---- player size (0.7.87) ------------------------------------------------------------------------------------------

	/** MCA's Size gene, as a player's look stores it (under {@code MCAData}, or at the top). */
	private static final String SIZE_GENE = "GeneSize";

	/** Holds the Size gene in a player's MCA look at {@code cap} or under; whether it changed. */
	static boolean capSize(CompoundTag data, float cap) {
		CompoundTag mca = NbtHelper.getCompoundOrSelf(data, "MCAData");
		float size = mca.getFloat(SIZE_GENE).orElse(0.5F);
		if (size <= cap) {
			return false;
		}
		mca.putFloat(SIZE_GENE, cap);
		return true;
	}

	/** Holds {@code player}'s MCA Size at {@code cap} or under, re-saving their look and showing it to everyone. */
	static void capSize(ServerPlayer player, float cap) {
		PlayerSaveData save = PlayerSaveData.get(player);
		if (!save.isEntityDataSet()) {
			return;
		}
		CompoundTag data = save.getEntityData();
		if (!capSize(data, cap)) {
			return;
		}
		save.setEntityData(data);
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			Network.sendToPlayer(new PlayerDataMessage(player.getUUID(), data), other);
		}
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
		VillagerEntityMCA villager = chosen != null ? chosen : candidate(level, f, role);
		if (villager == null) {
			// nobody to spare: someone new moves in to take the work
			// (0.7.84) on open ground by {@code at}, not shut in wherever it is (a seat can be indoors)
			BlockPos open = ShutIn.openGround(level, at);
			villager = VillagerFactory.newVillager(level).withPosition(Vec3.atBottomCenterOf(open != null ? open : at)).withAge(0).spawn(EntitySpawnReason.MOB_SUMMONED);
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
	 * (0.7.130) A purist ruler's guards are drafted from his own blood ({@link Purists}) while any are to be had.
	 */
	private static @Nullable VillagerEntityMCA candidate(ServerLevel level, Faction f, int role) {
		List<VillagerEntityMCA> all = level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
			v -> v.isAlive() && !v.isBaby() && worker(v).role() < 0 && !v.isProfessionImportant()
				&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID()))
				// (0.7.141) never a wind dragon in disguise
				&& v.getAttached(com.jaymoskingdoms.dragons.Disguises.DISGUISE) == null);
		if (role == Folk.GUARD && Purists.is(f)) {
			List<VillagerEntityMCA> kin = all.stream().filter(v -> Purists.kin(f, v)).toList();
			if (!kin.isEmpty()) {
				all = kin;
			}
		}
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

	// ---- rulers and guards (0.7.138) -----------------------------------------------------------------------------------

	/** {@link McaCompat#enlistRuler}. */
	static @Nullable Worker enlistRuler(ServerLevel level, Faction f, int role, java.util.@Nullable UUID house) {
		VillagerEntityMCA villager = heir(level, f, house);
		if (villager == null) {
			// nobody grown in the town: someone moves in to take the throne
			Castle.Keep keep = Factions.keep(level.getServer(), f.id());
			BlockPos at = keep != null ? keep.throne() : ShutIn.openGround(level, f.seat());
			villager = VillagerFactory.newVillager(level).withPosition(Vec3.atBottomCenterOf(at != null ? at : f.seat())).withAge(0)
				.spawn(EntitySpawnReason.MOB_SUMMONED);
			if (villager == null) {
				return null;
			}
		}
		Enlisted w = worker(villager);
		if (w.role() >= 0) {
			// (a guard leaves their post for the throne: a place to fill)
			Economy.lost(level.getServer(), w.factionId(), w.role());
			w.discharge();
		}
		w.enlist(role, f.id());
		return w;
	}

	/**
	 * {@code f}'s heir among its grown villagers (its guards too, the townsfolk first): the late ruler's family
	 * ({@code house}), then their widow or widower, then anyone.
	 */
	private static @Nullable VillagerEntityMCA heir(ServerLevel level, Faction f, java.util.@Nullable UUID house) {
		List<VillagerEntityMCA> all = level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
			v -> v.isAlive() && !v.isBaby() && (worker(v).role() < 0 || worker(v).role() == Folk.GUARD) && !Hermits.is(worker(v))
				&& !v.getUUID().equals(house));
		if (all.isEmpty()) {
			return null;
		}
		if (house != null) {
			for (int kin : new int[] {McaCompat.KIN, McaCompat.PARTNER}) {
				List<VillagerEntityMCA> family = all.stream().filter(v -> kinOf(v, house) == kin).toList();
				if (!family.isEmpty()) {
					return family.get(level.getRandom().nextInt(family.size()));
				}
			}
		}
		List<VillagerEntityMCA> townsfolk = all.stream().filter(v -> worker(v).role() < 0).toList();
		List<VillagerEntityMCA> pick = townsfolk.isEmpty() ? all : townsfolk;
		// (0.7.218) the gifted are likelier to rise
		VillagerEntityMCA gifted = GiftedFolk.rises(pick, level.getRandom());
		return gifted != null ? gifted : pick.get(level.getRandom().nextInt(pick.size()));
	}

	/** {@link McaCompat#becomeGuard}: an MCA guard (or archer) of the same blood takes {@code folk}'s post. */
	static boolean convertGuard(ServerLevel level, Folk folk) {
		Faction f = Factions.get(level.getServer(), folk.factionId());
		if (f == null || !Workers.inPlace(f, Folk.GUARD)) {
			return false;
		}
		VillagerProfession job = level.getRandom().nextInt(3) == 0 ? ProfessionsMCA.ARCHER : ProfessionsMCA.GUARD;
		VillagerEntityMCA villager = VillagerFactory.newVillager(level).withPosition(folk.position()).withAge(0).withProfession(job)
			.spawn(EntitySpawnReason.CONVERSION);
		if (villager == null) {
			return false;
		}
		Heritage.Kind kind = VillagerHeritage.of(folk);
		if (kind != null) {
			VillagerHeritage.born(villager, kind, folk.entityTags().contains(VillagerHeritage.TAIL_CUT) ? VillagerHeritage.TAIL_CUT
				: folk.entityTags().contains(VillagerHeritage.TAIL_WRAPPED) ? VillagerHeritage.TAIL_WRAPPED : null);
		}
		worker(villager).enlist(Folk.GUARD, f.id());
		villager.setHomeTo(folk.getHomePosition(), folk.getHomeRadius());
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		folk.discard();
		return true;
	}

	/**
	 * (0.7.148) {@link McaCompat#becomeRaider}: an MCA guard of the same blood, in the same gear (a raider's hides and
	 * axe, a deckhand's cutlass), takes {@code folk}'s place in its camp, enlisted to it in the same role (on its roster as before).
	 */
	static boolean convertRaider(ServerLevel level, Folk folk) {
		VillagerEntityMCA villager = VillagerFactory.newVillager(level).withPosition(folk.position()).withAge(0).withProfession(ProfessionsMCA.GUARD)
			.spawn(EntitySpawnReason.CONVERSION);
		if (villager == null) {
			return false;
		}
		Heritage.Kind kind = VillagerHeritage.of(folk);
		if (kind != null) {
			VillagerHeritage.born(villager, kind, folk.entityTags().contains(VillagerHeritage.TAIL_CUT) ? VillagerHeritage.TAIL_CUT
				: folk.entityTags().contains(VillagerHeritage.TAIL_WRAPPED) ? VillagerHeritage.TAIL_WRAPPED : null);
		}
		// (0.7.184) a gunner keeps his pistol in the off hand and draws a cutlass (MCA's guards would try to work a crossbow
		// themselves); a bombardier's dynamite stays in his off hand
		boolean gunner = folk.role() == Pirates.GUNNER;
		for (EquipmentSlot slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			net.minecraft.world.item.ItemStack worn = gunner && slot == EquipmentSlot.MAINHAND ? new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD) : folk.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				// worn, and kept in their pack too: MCA's guards dress from what they carry
				villager.setItemSlot(slot, worn.copy());
				villager.getInventory().addItem(worn.copy());
				villager.setDropChance(slot, 0.0F);
			}
		}
		net.minecraft.world.item.ItemStack offhand = gunner ? folk.getMainHandItem() : folk.getOffhandItem();
		if (!offhand.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.OFFHAND, offhand.copy());
			villager.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		}
		Barbarians.harden(villager);
		worker(villager).enlist(folk.role(), folk.factionId());
		villager.setHomeTo(folk.getHomePosition(), folk.getHomeRadius());
		level.sendParticles(ParticleTypes.POOF, folk.getX(), folk.getY() + 1.0, folk.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		folk.discard();
		return true;
	}

	/**
	 * (0.7.242) {@link McaCompat#crewman}: a new grown MCA villager in {@code folk}'s place (a ship's crewman set up for
	 * their role, {@code Crewmen}), in the world: the same blood, gear and toughness, a guard (or {@code sailor}: a
	 * fisherman), enlisted to the folk's side in its role. The folk is left for the caller.
	 */
	static @Nullable PathfinderMob crewman(ServerLevel level, Folk folk) {
		return crewman(level, folk, folk);
	}

	/**
	 * (0.7.272) {@link McaCompat#recast}: a man in {@code her} place (one of a ship's crew), with her blood, gear, role,
	 * side and stowed weapons. She's left for the caller.
	 */
	static @Nullable PathfinderMob recast(ServerLevel level, PathfinderMob her) {
		if (!(her instanceof Worker w)) {
			return null;
		}
		PathfinderMob man = crewman(level, her, w);
		if (man == null) {
			return null;
		}
		for (String tag : her.entityTags()) {
			man.addTag(tag);
		}
		List<net.minecraft.world.item.ItemStack> kit = her.getAttached(Crewmen.STOWED);
		if (kit != null) {
			man.setAttached(Crewmen.STOWED, kit);
		}
		return man;
	}

	private static @Nullable PathfinderMob crewman(ServerLevel level, PathfinderMob folk, Worker as) {
		// (0.7.259) Andy: no guards aboard, every one a sailor; (0.7.272) "there shouldnt be any female sailors. they
		// wouldnt have had that job."
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FISHERMAN).value();
		VillagerEntityMCA villager = VillagerFactory.newVillager(level).withGender(Gender.MALE).withPosition(folk.position()).withAge(0)
			.withProfession(job).spawn(EntitySpawnReason.CONVERSION);
		if (villager == null) {
			return null;
		}
		villager.snapTo(folk.getX(), folk.getY(), folk.getZ(), folk.getYRot(), 0.0F);
		Heritage.Kind kind = VillagerHeritage.of(folk);
		if (kind != null) {
			VillagerHeritage.born(villager, kind, folk.entityTags().contains(VillagerHeritage.TAIL_CUT) ? VillagerHeritage.TAIL_CUT
				: folk.entityTags().contains(VillagerHeritage.TAIL_WRAPPED) ? VillagerHeritage.TAIL_WRAPPED : null);
		}
		// (0.7.259) no armour at sea; their folk's weapons stowed (Crewmen.STOWED), taken up when there's a fight
		villager.setItemSlot(EquipmentSlot.MAINHAND, folk.getMainHandItem().copy());
		villager.setItemSlot(EquipmentSlot.OFFHAND, folk.getOffhandItem().copy());
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			villager.setDropChance(slot, 0.0F);
		}
		Crewmen.stow(villager);
		// their folk's toughness: a captain's health, a knight's blows
		for (var attribute : List.of(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
			net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
			net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, net.minecraft.world.entity.ai.attributes.Attributes.SCALE)) {
			var from = folk.getAttribute(attribute);
			var to = villager.getAttribute(attribute);
			if (from == null || to == null) {
				continue;
			}
			// (never weaker than MCA makes them; a powder monkey's a boy's size)
			if (from.getBaseValue() > to.getBaseValue() || attribute == net.minecraft.world.entity.ai.attributes.Attributes.SCALE) {
				to.setBaseValue(from.getBaseValue());
			}
		}
		villager.setHealth(villager.getMaxHealth());
		worker(villager).enlist(as.role(), as.factionId());
		villager.setHomeTo(folk.getHomePosition(), folk.getHomeRadius());
		return villager;
	}

	/** (0.7.246) {@code villager} has seen {@code player} before: MCA greets them as no stranger (no "first" greeting). */
	static void met(LivingEntity villager, ServerPlayer player) {
		if (villager instanceof VillagerEntityMCA v) {
			com.google.gson.JsonObject seen = new com.google.gson.JsonObject();
			seen.addProperty("id", "seen");
			seen.addProperty("var", "player");
			v.getLongTermMemory().remember(net.conczin.mca.entity.ai.LongTermMemory.parseId(seen, player));
		}
	}

	/**
	 * (0.7.246) MCA's menu opening on {@code villager} ("root"): if they know of {@code player} (a shipmate, or one about
	 * their story: {@link StoryTalk#opening}), they open with that and go to the main menu, never the stranger's greeting;
	 * true if so (MCA's own opening is skipped).
	 */
	static boolean storyOpening(LivingEntity mob, ServerPlayer player) {
		if (!(mob instanceof VillagerEntityMCA villager) || !Languages.understands(player, villager)) {
			return false;
		}
		String told = "jaymoskingdoms.story." + player.getUUID();
		var memory = villager.getLongTermMemory();
		Component line = StoryTalk.opening(player, villager, memory.hasMemory(told));
		var main = net.conczin.mca.resources.Dialogues.getInstance().getQuestion("main");
		if (line == null || main == null) {
			return false;
		}
		met(villager, player);
		memory.remember(told);
		if (StoryTalk.shipmate((ServerLevel) villager.level(), player, villager)) {
			Introductions.learn(player, villager);
		}
		Network.sendToPlayer(new net.conczin.mca.network.s2c.InteractionDialogueResponse(main, player, villager), player);
		Network.sendToPlayer(new net.conczin.mca.network.s2c.InteractionDialogueQuestionResponse(line, false), player);
		return true;
	}

	static void toSea(PathfinderMob mob) {
		if (!(mob instanceof VillagerEntityMCA villager)) {
			return;
		}
		// (0.7.259) no armour in their pack either (MCA's villagers put on what they carry)
		var pack = villager.getInventory();
		for (int i = 0; i < pack.getContainerSize(); i++) {
			var held = pack.getItem(i).get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
			if (held != null && held.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				pack.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
		}
		if (villager.getProfession() != BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FISHERMAN).value()) {
			villager.setProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FISHERMAN).value());
		}
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
		// (0.7.242) a ship's crew serve her, not any town: never called on for its work, guard calls or escorts
		for (VillagerEntityMCA v : level.getEntitiesOfClass(VillagerEntityMCA.class, box, v -> worker(v).role() >= 0 && !Crewmen.tagged(v) && test.test(worker(v)))) {
			found.add(worker(v));
		}
		return found;
	}

	static boolean reconcile(ServerLevel level, Worker w) {
		if (!(w.mob() instanceof VillagerEntityMCA villager)) {
			return false;
		}
		Enlisted e = worker(villager);
		if (Crewmen.tagged(villager)) {
			// (0.7.242) a ship's crewman keeps their berth and side, aboard or ashore (off every roster)
			return e.role() >= 0;
		}
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
		if (Rulers.leaderRole(e.role())) {
			// (0.7.138) a realm's ruler, whatever their trade: while the throne is theirs
			Faction f = Factions.get(level.getServer(), e.factionId());
			if (f != null && !f.fallen() && !villager.isBaby() && f.leader().map(villager.getUUID()::equals).orElse(false)) {
				return true;
			}
			e.discharge();
			Rulers.unfit(villager);
			return false;
		}
		if (Barbarians.villagerRole(e.role())) {
			// (0.7.148) a camp's (or cove's) villager, while it stands and they're its guard; a fallen camp's are scattered
			Faction camp = Factions.get(level.getServer(), e.factionId());
			if (camp != null && !camp.fallen() && villager.isGuard() && !villager.isBaby()) {
				return true;
			}
			if (camp != null && !camp.fallen() && !Pirates.offRoster(e.role())) {
				Economy.lost(level.getServer(), camp.id(), e.role());
			}
			e.discharge();
			return false;
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

	/** {@link McaCompat#parents}. */
	static List<java.util.UUID> parents(net.minecraft.world.entity.Entity e) {
		if (!(e instanceof VillagerEntityMCA v)) {
			return List.of();
		}
		List<java.util.UUID> parents = new java.util.ArrayList<>(2);
		FamilyTreeNode node = v.getRelationships().getFamilyEntry();
		for (java.util.UUID p : new java.util.UUID[] {node.mother(), node.father()}) {
			if (p != null && (p.getMostSignificantBits() != 0L || p.getLeastSignificantBits() != 0L)) {
				parents.add(p);
			}
		}
		return parents;
	}

	/** {@link McaCompat#female}. */
	static boolean female(net.minecraft.world.entity.LivingEntity e) {
		return e instanceof VillagerEntityMCA v && v.getGenetics().getGender() == Gender.FEMALE;
	}

	/** {@link McaCompat#redress}. */
	static boolean redress(PathfinderMob mob) {
		if (!(mob instanceof VillagerEntityMCA v)) {
			return false;
		}
		// (0.7.272) a ruler in our robes or gowns, or out of them off the throne ({@link Uniforms})
		Uniforms.fit(v);
		return Rulers.is(v) && Uniforms.royal(v);
	}

	/** (0.7.272) {@link McaCompat#uniform}. */
	static void uniform(PathfinderMob mob) {
		if (mob instanceof VillagerEntityMCA v) {
			Uniforms.fit(v);
		}
	}

	static boolean isGuard(PathfinderMob mob) {
		return mob instanceof VillagerEntityMCA v && v.isGuard();
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

	/** (0.7.187) A new grown MCA guard at {@code at}, in the world, for no settlement ({@code nether.AvatarCamps}); null if it failed. */
	static @Nullable Villager spawnGuard(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		return VillagerFactory.newVillager(level).withPosition(at).withAge(0).withProfession(ProfessionsMCA.GUARD).spawn(EntitySpawnReason.EVENT);
	}

	/** (0.7.61) A new grown MCA villager at {@code at}, not yet added to the world ({@link Captives}). */
	static Villager newVillager(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		return VillagerFactory.newVillager(level).withPosition(at).withAge(0).build(EntitySpawnReason.EVENT);
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

	// ---- lineage (0.7.184) ----------------------------------------------------------------------------------------

	/** The family tree's own links, set straight (its setters also add to the other side's children). */
	private static final java.lang.reflect.Field FATHER = treeField("father"), MOTHER = treeField("mother"), PARTNER = treeField("partner");

	private static java.lang.reflect.Field treeField(String name) {
		try {
			java.lang.reflect.Field f = FamilyTreeNode.class.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("MCA's family tree has no " + name, e);
		}
	}

	/** {@link McaCompat#childOf}. */
	static boolean childOf(Villager villager, ServerPlayer player) {
		return villager instanceof VillagerEntityMCA v && FamilyTree.get(player.level()).getOrCreate(player).children().contains(v.getUUID());
	}

	/** {@link McaCompat#towns}. */
	/** (0.7.221) {@link McaCompat#playerFamily}. */
	static boolean playerFamily(Villager villager) {
		if (!(villager instanceof VillagerEntityMCA v)) {
			return false;
		}
		net.conczin.mca.server.world.data.FamilyTreeNode node = v.getRelationships().getFamilyEntry();
		return node.getParents().anyMatch(n -> n.isPlayer())
			|| node.lookup(java.util.stream.Stream.concat(java.util.stream.Stream.of(node.partner()), node.getChildren())).anyMatch(n -> n.isPlayer());
	}

	/** (0.7.221) {@link McaCompat#leaveVillage}. */
	static void leaveVillage(Villager villager) {
		if (villager instanceof VillagerEntityMCA v) {
			v.getResidency().leaveVillage();
		}
	}

	static List<McaCompat.Town> towns(ServerLevel level) {
		List<McaCompat.Town> out = new ArrayList<>();
		for (net.conczin.mca.server.world.data.Village v : VillageManager.get(level)) {
			out.add(new McaCompat.Town(BlockPos.containing(v.getCenter().getX(), v.getCenter().getY(), v.getCenter().getZ()), v.getName()));
		}
		return out;
	}

	/** {@link McaCompat#letter}: into their MCA mail, and the mail notice if they're on. */
	static void letter(ServerLevel level, java.util.UUID to, Component text) {
		PlayerSaveData.getIfPresent(level, to).ifPresent(save -> save.sendLetter(text));
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(to);
		if (player != null) {
			PlayerSaveData.showMailNotification(player);
		}
	}

	/** {@link McaCompat#takeMail}. */
	static net.minecraft.world.item.ItemStack takeMail(ServerPlayer player) {
		PlayerSaveData save = PlayerSaveData.get(player);
		if (!save.hasMail()) {
			return net.minecraft.world.item.ItemStack.EMPTY;
		}
		net.minecraft.world.item.ItemStack mail = save.getMail();
		save.setDirty();
		return mail == null ? net.minecraft.world.item.ItemStack.EMPTY : mail;
	}

	/** {@link McaCompat#teenOrOlder}. */
	static boolean teenOrOlder(Villager villager) {
		return villager instanceof VillagerEntityMCA v && (v.getAgeState() == AgeState.TEEN || v.getAgeState() == AgeState.ADULT);
	}

	/** {@link McaCompat#stow}. */
	static net.minecraft.world.item.ItemStack stow(PathfinderMob villager, net.minecraft.world.item.ItemStack stack) {
		return villager instanceof VillagerEntityMCA v ? v.getInventory().addItem(stack) : stack;
	}

	/**
	 * {@link Lineage}: {@code player} becomes their child {@code kid}. The player takes the child's look, name and place in
	 * the family tree (their parents, partner, children); the body they leave behind stays where they stood as a villager
	 * with their old look and name, an elder already ({@code elderSince}), and takes their old place in the tree. The child
	 * villager is gone. Returns the villager left behind, or null if nothing changed.
	 */
	static @Nullable PathfinderMob takeOver(ServerPlayer player, Villager kid, long elderSince) {
		if (!(kid instanceof VillagerEntityMCA child) || !(player.level() instanceof ServerLevel here)) {
			return null;
		}
		FamilyTree tree = FamilyTree.get(here);
		FamilyTreeNode me = tree.getOrCreate(player);
		java.util.UUID p = player.getUUID(), c = child.getUUID();
		FamilyTreeNode them = tree.getOrEmpty(c).orElse(null);
		if (them == null || !me.children().contains(c)) {
			return null;
		}
		PlayerSaveData save = PlayerSaveData.get(player);
		// (the old self, as a villager: their look, gender and name)
		net.conczin.mca.entity.VillagerLike<?> oldSelf = save.isEntityDataSet() ? net.conczin.mca.entity.VillagerLike.toVillager(save) : null;
		VillagerEntityMCA parent = VillagerFactory.newVillager(here).withGender(me.gender()).withName(me.getName()).withPosition(player.position())
			.withAge(0).build(EntitySpawnReason.EVENT);
		if (oldSelf != null) {
			parent.copyVillagerAttributesFrom(oldSelf);
		}
		parent.setYRot(player.getYRot());
		parent.setYHeadRot(player.getYHeadRot());
		CompoundTag kidLook = net.conczin.mca.network.c2s.VillagerEditorSyncRequest.createEditorPatch(
			net.conczin.mca.network.c2s.GetVillagerRequest.getVillagerData(child));
		if (!here.addFreshEntity(parent)) {
			return null;
		}
		java.util.UUID n = parent.getUUID();
		// the tree: the player's node takes the child's place, the new villager's takes the player's, the child's goes
		Kin was = new Kin(me), kidWas = new Kin(them);
		FamilyTreeNode old = tree.getOrCreate(parent);
		java.util.function.UnaryOperator<java.util.UUID> map = u -> p.equals(u) ? n : c.equals(u) ? p : u;
		java.util.Set<java.util.UUID> touched = new java.util.HashSet<>();
		for (Kin k : List.of(was, kidWas)) {
			touched.add(k.father);
			touched.add(k.mother);
			touched.add(k.partner);
			touched.addAll(k.children);
		}
		touched.removeAll(java.util.Set.of(p, c, n));
		for (java.util.UUID id : touched) {
			tree.getOrEmpty(id).ifPresent(node -> relink(tree, node, map));
		}
		was.onto(tree, old, map);
		kidWas.onto(tree, me, map);
		tree.remove(c);
		tree.setDirty();
		// the player wears the child's look now, and everyone sees it
		CompoundTag look = net.conczin.mca.network.c2s.VillagerEditorSyncRequest.mergeAllowedEditorPatch(save.getEntityData(), kidLook);
		save.setEntityData(look);
		save.setEntityDataSet(true);
		for (ServerPlayer other : here.getServer().getPlayerList().getPlayers()) {
			Network.sendToPlayer(new PlayerDataMessage(p, look), other);
		}
		worker(parent).setGrownSince(elderSince);
		child.discard();
		return parent;
	}

	/** One node's links and name, as they were before {@link #takeOver} moved them. */
	private static final class Kin {
		final String name;
		final Gender gender;
		final java.util.UUID father, mother, partner;
		final net.conczin.mca.entity.ai.relationship.RelationshipState state;
		final boolean deceased;
		final List<java.util.UUID> children;

		Kin(FamilyTreeNode node) {
			this.name = node.getName();
			this.gender = node.gender();
			this.father = node.father();
			this.mother = node.mother();
			this.partner = node.partner();
			this.state = node.getRelationshipState();
			this.deceased = node.isDeceased();
			this.children = List.copyOf(node.children());
		}

		/** Writes these links (each moved by {@code map}) onto {@code node}. */
		void onto(FamilyTree tree, FamilyTreeNode node, java.util.function.UnaryOperator<java.util.UUID> map) {
			node.setName(this.name);
			node.setGender(this.gender);
			node.setDeceased(this.deceased);
			link(node, FATHER, map.apply(this.father));
			link(node, MOTHER, map.apply(this.mother));
			link(node, PARTNER, map.apply(this.partner));
			node.setRelationshipState(this.state);
			marriedState(tree, node);
			node.children().clear();
			for (java.util.UUID u : this.children) {
				node.children().add(map.apply(u));
			}
		}
	}

	/** Moves {@code node}'s links to the player and the child by {@code map}. */
	private static void relink(FamilyTree tree, FamilyTreeNode node, java.util.function.UnaryOperator<java.util.UUID> map) {
		link(node, FATHER, map.apply(node.father()));
		link(node, MOTHER, map.apply(node.mother()));
		link(node, PARTNER, map.apply(node.partner()));
		marriedState(tree, node);
		List<java.util.UUID> kids = List.copyOf(node.children());
		node.children().clear();
		for (java.util.UUID u : kids) {
			node.children().add(map.apply(u));
		}
	}

	/** A married node is married to a player or a villager, as its partner now is. */
	private static void marriedState(FamilyTree tree, FamilyTreeNode node) {
		var state = node.getRelationshipState();
		if (state != net.conczin.mca.entity.ai.relationship.RelationshipState.MARRIED_TO_PLAYER
			&& state != net.conczin.mca.entity.ai.relationship.RelationshipState.MARRIED_TO_VILLAGER) {
			return;
		}
		tree.getOrEmpty(node.partner()).ifPresent(partner -> node.setRelationshipState(partner.isPlayer()
			? net.conczin.mca.entity.ai.relationship.RelationshipState.MARRIED_TO_PLAYER
			: net.conczin.mca.entity.ai.relationship.RelationshipState.MARRIED_TO_VILLAGER));
	}

	private static void link(FamilyTreeNode node, java.lang.reflect.Field field, java.util.UUID id) {
		try {
			field.set(node, id);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	static boolean destinyPending(ServerPlayer player) {
		return Config.getInstance().launchIntoDestiny && !PlayerSaveData.get(player).isEntityDataSet();
	}
}
