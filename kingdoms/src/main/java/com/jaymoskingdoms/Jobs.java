package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.5) A settlement's villagers at work. One without a trade is sent to a free job site in one of the settlement's
 * own buildings (its smithy, mason's yard, farm, stall, stable...), and takes that trade. By day, one at its job site
 * is seen working there (its trade's sound, smoke from a forge) and turns what's in the storehouse into goods: smiths
 * smelt the miners' raw ore with coal, the butcher cooks the farmer's meat, the mason cuts spare cobblestone into
 * bricks, the fletcher makes arrows, the fisherman brings in fish, and a farmer hands over the harvest it doesn't need.
 */
final class Jobs {
	/** How often the villagers are looked over (ticks). */
	static final int CHECK_TICKS = 100;
	/** A worker turns out one batch per this many ticks at its job site. */
	static final int WORK_EVERY = 1200;
	/** Working hours (time of day; vanilla villagers work from about 2000 to 9000). */
	static final long WORK_FROM = 2000L, WORK_TO = 9000L;
	/** How near its job site a villager counts as at work. */
	static final double AT_SITE = 3.0;
	/** How far a villager without a trade is sent for a job site. */
	static final int SITE_RANGE = 96;
	/** A batch: raw ore smelted (with one coal), meat cooked, cobblestone cut, fish caught. */
	static final int SMELT = 8, COOK = 8, CUT = 8, FISH = 2;
	/** The mason cuts only cobblestone over this (the builders' share is kept). */
	static final int MASON_KEEP = 256;
	/** A farmer keeps this many of each crop for sowing and bread; the rest goes to storage. */
	static final int FARMER_KEEPS = 12;

	private static final Map<UUID, Long> LAST_BATCH = new HashMap<>();

	private static final Item[][] SMELTS = {
		{Items.RAW_IRON, Items.IRON_INGOT}, {Items.RAW_COPPER, Items.COPPER_INGOT}, {Items.RAW_GOLD, Items.GOLD_INGOT}};
	private static final Item[][] COOKS = {
		{Items.BEEF, Items.COOKED_BEEF}, {Items.PORKCHOP, Items.COOKED_PORKCHOP}, {Items.MUTTON, Items.COOKED_MUTTON},
		{Items.CHICKEN, Items.COOKED_CHICKEN}, {Items.RABBIT, Items.COOKED_RABBIT}, {Items.COD, Items.COOKED_COD},
		{Items.SALMON, Items.COOKED_SALMON}};
	private static final List<Item> CROPS = List.of(Items.WHEAT, Items.BEETROOT, Items.POTATO, Items.CARROT);

	private Jobs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Jobs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_BATCH.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		boolean hours = t >= WORK_FROM && t < WORK_TO;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			List<Works.Built> built = Works.of(server, f.id()).built();
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE), v -> v.isAlive() && !v.isBaby())) {
				Holder<VillagerProfession> trade = v.getVillagerData().profession();
				if (trade.is(VillagerProfession.NONE)) {
					place(level, v, built);
				} else if (hours && !trade.is(VillagerProfession.NITWIT)) {
					work(level, f, v, trade, now);
				}
			}
		}
	}

	/** One without a trade is sent to a free job site in the settlement's own buildings (it takes the site's trade there). */
	private static void place(ServerLevel level, Villager v, List<Works.Built> built) {
		Brain<?> brain = v.getBrain();
		Optional<GlobalPos> site = brain.getMemoryInternal(MemoryModuleType.JOB_SITE);
		Optional<GlobalPos> going = brain.getMemoryInternal(MemoryModuleType.POTENTIAL_JOB_SITE);
		// (a villager whose mind doesn't work the game's way, or one already going to a site, is left be)
		if (site == null || going == null || site.isPresent() || going.isPresent() || built.isEmpty()) {
			return;
		}
		level.getPoiManager()
			.take(type -> type.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), (type, pos) -> inside(built, pos), v.blockPosition(), SITE_RANGE)
			.ifPresent(pos -> brain.setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), pos)));
	}

	private static boolean inside(List<Works.Built> built, BlockPos pos) {
		for (Works.Built b : built) {
			if (pos.getX() >= b.min().getX() && pos.getX() <= b.max().getX() && pos.getY() >= b.min().getY() && pos.getY() <= b.max().getY()
				&& pos.getZ() >= b.min().getZ() && pos.getZ() <= b.max().getZ()) {
				return true;
			}
		}
		return false;
	}

	/** By day, at its job site: seen working, and now and then a batch of goods into storage. */
	private static void work(ServerLevel level, Faction f, Villager v, Holder<VillagerProfession> trade, long now) {
		Optional<GlobalPos> held = v.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE);
		if (held == null || held.isEmpty() || held.get().dimension() != level.dimension()) {
			return;
		}
		BlockPos site = held.get().pos();
		Vec3 at = Vec3.atCenterOf(site);
		if (v.distanceToSqr(at) > AT_SITE * AT_SITE || v.isSleeping()) {
			return;
		}
		v.getLookControl().setLookAt(at.x, at.y, at.z);
		if (level.getRandom().nextBoolean()) {
			v.playWorkSound();
			level.sendParticles(particle(trade), at.x, at.y + 0.6, at.z, 4, 0.25, 0.1, 0.25, 0.01);
		}
		long last = LAST_BATCH.getOrDefault(v.getUUID(), 0L);
		if (now - last < WORK_EVERY || !Storage.loaded(level, f)) {
			return;
		}
		LAST_BATCH.put(v.getUUID(), now);
		if (batch(level, f, v, trade)) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
		}
	}

	private static SimpleParticleType particle(Holder<VillagerProfession> trade) {
		if (trade.is(VillagerProfession.ARMORER) || trade.is(VillagerProfession.TOOLSMITH) || trade.is(VillagerProfession.WEAPONSMITH)
			|| trade.is(VillagerProfession.BUTCHER)) {
			return ParticleTypes.SMOKE;
		}
		return trade.is(VillagerProfession.FARMER) ? ParticleTypes.COMPOSTER : ParticleTypes.CRIT;
	}

	/** Its trade's batch of goods (true if it made any). */
	private static boolean batch(ServerLevel level, Faction f, Villager v, Holder<VillagerProfession> trade) {
		if (trade.is(VillagerProfession.ARMORER) || trade.is(VillagerProfession.TOOLSMITH) || trade.is(VillagerProfession.WEAPONSMITH)) {
			// raw ore smelted, a coal a batch
			for (Item[] smelt : SMELTS) {
				if (Storage.count(level, f, smelt[0]) == 0) {
					continue;
				}
				ItemStack fuel = Storage.take(level, f, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 1);
				if (fuel.isEmpty()) {
					return false;
				}
				return convert(level, f, smelt[0], smelt[1], SMELT);
			}
			return false;
		}
		if (trade.is(VillagerProfession.BUTCHER)) {
			for (Item[] cook : COOKS) {
				if (Storage.count(level, f, cook[0]) > 0) {
					return convert(level, f, cook[0], cook[1], COOK);
				}
			}
			return false;
		}
		if (trade.is(VillagerProfession.MASON)) {
			return Storage.count(level, f, Items.COBBLESTONE) > MASON_KEEP && convert(level, f, Items.COBBLESTONE, Items.STONE_BRICKS, CUT);
		}
		if (trade.is(VillagerProfession.FLETCHER)) {
			if (Storage.count(level, f, Items.STICK) > 0 && Storage.count(level, f, Items.FLINT) > 0 && Storage.count(level, f, Items.FEATHER) > 0) {
				Storage.take(level, f, Items.STICK, 1);
				Storage.take(level, f, Items.FLINT, 1);
				Storage.take(level, f, Items.FEATHER, 1);
				Storage.put(level, f, Items.ARROW, 4);
				return true;
			}
			return false;
		}
		if (trade.is(VillagerProfession.FISHERMAN)) {
			Storage.put(level, f, level.getRandom().nextInt(3) == 0 ? Items.SALMON : Items.COD, 1 + level.getRandom().nextInt(FISH));
			return true;
		}
		if (trade.is(VillagerProfession.FARMER)) {
			// the harvest it carries, but for what it keeps to sow and bake
			SimpleContainer bag = v.getInventory();
			boolean gave = false;
			for (Item crop : CROPS) {
				int spare = bag.countItem(crop) - FARMER_KEEPS;
				for (int slot = 0; slot < bag.getContainerSize() && spare > 0; slot++) {
					ItemStack stack = bag.getItem(slot);
					if (stack.is(crop)) {
						ItemStack part = stack.split(Math.min(spare, stack.getCount()));
						spare -= part.getCount();
						ItemStack left = Storage.put(level, f, part);
						gave |= left.getCount() < part.getCount();
						if (!left.isEmpty()) {
							bag.addItem(left);
							spare = 0;
						}
					}
				}
			}
			bag.setChanged();
			return gave;
		}
		return false;
	}

	/** Up to {@code most} of {@code from} out of storage, as many of {@code to} back in (what doesn't fit goes back). */
	private static boolean convert(ServerLevel level, Faction f, Item from, Item to, int most) {
		ItemStack taken = Storage.take(level, f, from, most);
		if (taken.isEmpty()) {
			return false;
		}
		int left = Storage.put(level, f, to, taken.getCount());
		if (left > 0) {
			Storage.put(level, f, from, left);
		}
		return left < taken.getCount();
	}
}
