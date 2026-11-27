package com.jaymoskingdoms;

import com.jaymoskingdoms.money.PurseContents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import com.mojang.serialization.Codec;

/**
 * Andy (2026-11-27): "the dagger should be within the inventory when you go to loot them. all npcs should have some kind of
 * loot drawn from a table of realistic items they would have. and even house keys if they had a home."
 * <ul>
 * <li><b>Pockets</b> ({@link #POCKETS}, saved on the NPC): every grown villager (MCA's too), every settlement's folk and every illager
 * carries a few real things, rolled the first time anyone looks ({@link #fill}): most a purse of their own land's coin
 * (fuller on a guard or a merchant), a bite to eat, odds and ends, and what their trade would have on them ({@link #TRADES}:
 * a farmer's seeds, a fletcher's feathers, a librarian's paper and ink, a smith's coal and nuggets, a guard's bandage); a
 * camp's raider bones, flint and a little gold, a pirate gold and kelp, an illager spare bolts and plunder. A realm's villager with a door to lock carries their
 * house key ({@code NightLocks#houseKeyOf}), given once: one taken isn't made again. What they spend or lose is gone; each
 * new day ({@link #RESTOCK_TICKS}) their pockets are rolled again, the key (and a steel realm smith's blueprint,
 * {@link SteelRealms#BLUEPRINT}) aside.</li>
 * <li><b>Searching them</b> ({@link #open}, the body menu's "Search them" on anyone downed or hogtied): a screen of their belt
 * ({@link Sidearms#BELT}: the sidearm, or the bow or gun slung there) and their pockets, and your inventory below. Its slots
 * read and write the body's own belt and pockets, so two people looking see the same and nothing is taken twice. Where a
 * realm's law holds, taking from them is a theft anyone watching sees ({@link Theft.Crime#PICKPOCKET}), and what is taken is
 * marked stolen.</li>
 * <li><b>Dying</b> ({@link #died}, as their gear drops): the belt and the pockets drop with it, so they're on the body's loot
 * screen (Jaymo's Kitchen's corpses).</li>
 * <li>A pickpocket's lift ({@code Pickpocket}) comes out of these same pockets.</li>
 * </ul>
 */
public final class NpcPockets {
	/** Their pockets (present once rolled, even empty). */
	public static final AttachmentType<List<ItemStack>> POCKETS = AttachmentRegistry.create(JaymosKingdoms.id("npc_pockets"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()));
	/** When their pockets were last rolled (game time). */
	static final AttachmentType<Long> ROLLED = AttachmentRegistry.create(JaymosKingdoms.id("npc_pockets_rolled"),
		builder -> builder.persistent(Codec.LONG));
	/** On one whose house key has been given (it's never given twice). */
	static final String KEY_TAG = "jaymoskingdoms.house_key";

	/** Pocket slots: two rows of the search screen, less the belt's. */
	static final int SLOTS = 17;
	/** Their pockets are rolled again after this long (ticks: a day). */
	static final long RESTOCK_TICKS = 24000L;
	/** The chance of a purse, and its worth (copper bits, least and most: twice that on a guard or a merchant). */
	static final float PURSE_CHANCE = 0.75F;
	static final int PURSE_MIN_BITS = 4, PURSE_MAX_BITS = 30;
	/** How many odds and ends (least, most), and the chance of something to eat. */
	static final int ODDS_MIN = 0, ODDS_MAX = 2;
	static final float FOOD_CHANCE = 0.7F;
	/** How far (blocks) past reach the body may be before the search screen closes. */
	static final double REACH_SLACK = 2.0;

	/** One thing that may be in a pocket: an item id (looked up, so a missing mod's item is skipped), how many (least, most). */
	record Thing(String id, int least, int most) {
	}

	/** A bite to eat. */
	static final List<Thing> FOOD = List.of(new Thing("minecraft:bread", 1, 2), new Thing("minecraft:apple", 1, 2),
		new Thing("minecraft:cookie", 1, 3), new Thing("minecraft:baked_potato", 1, 2), new Thing("minecraft:dried_kelp", 2, 4));
	/** Odds and ends anyone might have on them. */
	static final List<Thing> ODDS = List.of(new Thing("minecraft:string", 1, 3), new Thing("minecraft:flint", 1, 1),
		new Thing("minecraft:paper", 1, 2), new Thing("minecraft:stick", 1, 2), new Thing("minecraft:candle", 1, 1),
		new Thing("minecraft:iron_nugget", 1, 3), new Thing("minecraft:feather", 1, 2), new Thing("minecraft:bone", 1, 1));
	/** What each trade (an MCA or vanilla profession, by its id's path) has on them. */
	static final Map<String, List<Thing>> TRADES = Map.ofEntries(
		Map.entry("farmer", List.of(new Thing("minecraft:wheat_seeds", 2, 6), new Thing("minecraft:carrot", 1, 3), new Thing("minecraft:potato", 1, 3))),
		Map.entry("fisherman", List.of(new Thing("minecraft:cod", 1, 2), new Thing("minecraft:string", 1, 3))),
		Map.entry("shepherd", List.of(new Thing("minecraft:white_wool", 1, 2), new Thing("minecraft:string", 1, 2))),
		Map.entry("fletcher", List.of(new Thing("minecraft:feather", 2, 5), new Thing("minecraft:flint", 1, 3), new Thing("minecraft:stick", 2, 4))),
		Map.entry("librarian", List.of(new Thing("minecraft:paper", 2, 4), new Thing("minecraft:ink_sac", 1, 1), new Thing("minecraft:book", 1, 1))),
		Map.entry("cartographer", List.of(new Thing("minecraft:paper", 2, 5), new Thing("minecraft:map", 1, 1))),
		Map.entry("cleric", List.of(new Thing("minecraft:glass_bottle", 1, 2), new Thing("minecraft:candle", 1, 2))),
		Map.entry("butcher", List.of(new Thing("minecraft:beef", 1, 2), new Thing("minecraft:porkchop", 1, 2))),
		Map.entry("leatherworker", List.of(new Thing("minecraft:leather", 1, 3), new Thing("minecraft:rabbit_hide", 1, 2))),
		Map.entry("mason", List.of(new Thing("minecraft:clay_ball", 2, 4), new Thing("minecraft:brick", 1, 3))),
		Map.entry("armorer", List.of(new Thing("minecraft:coal", 1, 3), new Thing("minecraft:iron_nugget", 2, 6))),
		Map.entry("toolsmith", List.of(new Thing("minecraft:coal", 1, 3), new Thing("minecraft:iron_nugget", 2, 6))),
		Map.entry("weaponsmith", List.of(new Thing("minecraft:coal", 1, 3), new Thing("minecraft:iron_nugget", 2, 6))),
		Map.entry("guard", List.of(new Thing("jaymospowers:linen_bandage", 1, 2), new Thing("minecraft:bread", 1, 2))),
		Map.entry("archer", List.of(new Thing("jaymospowers:linen_bandage", 1, 1), new Thing("minecraft:string", 1, 3), new Thing("minecraft:feather", 1, 3))),
		Map.entry("nitwit", List.of(new Thing("minecraft:dandelion", 1, 1), new Thing("minecraft:wheat_seeds", 1, 3))));
	/** A camp's raider, a pirate, a forest ranger. */
	static final List<Thing> RAIDER = List.of(new Thing("minecraft:bone", 1, 2), new Thing("minecraft:flint", 1, 2),
		new Thing("minecraft:gold_nugget", 1, 3), new Thing("minecraft:rotten_flesh", 1, 1), new Thing("jaymospowers:rag", 1, 2));
	static final List<Thing> PIRATE = List.of(new Thing("minecraft:gold_nugget", 1, 4), new Thing("minecraft:dried_kelp", 2, 4),
		new Thing("minecraft:string", 1, 3), new Thing("minecraft:gunpowder", 1, 2));
	/** An illager raider: a pillager's spare bolts, a raid's plunder. */
	static final List<Thing> ILLAGER = List.of(new Thing("minecraft:arrow", 2, 6), new Thing("minecraft:string", 1, 3),
		new Thing("minecraft:gold_nugget", 1, 3), new Thing("minecraft:bread", 1, 2), new Thing("minecraft:iron_nugget", 1, 3));
	static final List<Thing> RANGER = List.of(new Thing("minecraft:sweet_berries", 2, 4), new Thing("minecraft:leather", 1, 2),
		new Thing("minecraft:string", 1, 3), new Thing("minecraft:arrow", 2, 6));

	private NpcPockets() {
	}

	/** From {@link JaymosKingdoms}: the body menu's search, for anyone downed or hogtied with pockets. */
	static void register() {
		PowersCompat.bodyOption("search", 50, (player, body) -> body instanceof Mob mob && Pickpocket.searchable(mob) && has(mob),
			(player, body) -> {
				open(player, (Mob) body);
				return false;
			});
	}

	/** Whether {@code e} has pockets: a grown villager, a settlement's folk short of its ruler, or an illager. */
	static boolean has(LivingEntity e) {
		return !e.isBaby() && (e instanceof Villager || e instanceof Folk folk && !folk.leader() || e instanceof AbstractIllager);
	}

	// ---- the pockets -------------------------------------------------------------------------------------------------------

	/** {@code mob}'s pockets, rolled (or rolled again, after a day) first; empty for one who has none. Read only. */
	static List<ItemStack> pockets(ServerLevel level, Mob mob) {
		if (!has(mob)) {
			return List.of();
		}
		List<ItemStack> pockets = mob.getAttached(POCKETS);
		long now = level.getGameTime();
		Long rolled = mob.getAttached(ROLLED);
		if (pockets == null || rolled == null || now - rolled >= RESTOCK_TICKS && !Downed.isDowned(mob)) {
			pockets = fill(level, mob);
			mob.setAttached(POCKETS, pockets);
			mob.setAttached(ROLLED, now);
		}
		return pockets;
	}

	/** Sets pocket {@code slot} of {@code mob}'s. */
	static void set(Mob mob, int slot, ItemStack stack) {
		List<ItemStack> pockets = new ArrayList<>(mob.getAttachedOrElse(POCKETS, List.of()));
		while (pockets.size() < SLOTS) {
			pockets.add(ItemStack.EMPTY);
		}
		pockets.set(slot, stack);
		mob.setAttached(POCKETS, Collections.unmodifiableList(pockets));
	}

	/** A fresh roll of {@code mob}'s pockets (see the class comment), {@link #SLOTS} long. */
	static List<ItemStack> fill(ServerLevel level, Mob mob) {
		RandomSource random = mob.getRandom();
		List<ItemStack> out = new ArrayList<>();
		// (a house key or a realm's steel blueprint still in their pocket stays there through the new roll, first, so no
		// full roll pushes it out)
		for (ItemStack kept : mob.getAttachedOrElse(POCKETS, List.<ItemStack>of())) {
			if (keeps(kept)) {
				out.add(kept);
			}
		}
		if (random.nextFloat() < PURSE_CHANCE) {
			Faction f = Pickpocket.law(level, mob);
			Currency coin = f != null ? Currencies.of(level.getServer(), f) : Currencies.at(level, mob.blockPosition());
			int bits = PURSE_MIN_BITS + random.nextInt(PURSE_MAX_BITS - PURSE_MIN_BITS + 1);
			out.add(PurseContents.filled(coin, (long)bits * Currency.BIT.base * (Pickpocket.rich(mob) ? Pickpocket.RICH_FACTOR : 1)));
		}
		if (random.nextFloat() < FOOD_CHANCE) {
			add(out, FOOD.get(random.nextInt(FOOD.size())), random);
		}
		List<Thing> trade = trade(mob);
		for (Thing thing : trade) {
			if (random.nextBoolean()) {
				add(out, thing, random);
			}
		}
		for (int n = ODDS_MIN + random.nextInt(ODDS_MAX - ODDS_MIN + 1); n > 0; n--) {
			add(out, ODDS.get(random.nextInt(ODDS.size())), random);
		}
		if (!mob.entityTags().contains(KEY_TAG)) {
			ItemStack key = NightLocks.houseKeyOf(level, mob);
			if (!key.isEmpty()) {
				out.add(Math.min(out.size(), countKept(out)), key);
				mob.addTag(KEY_TAG);
			}
		}
		while (out.size() > SLOTS) {
			out.removeLast();
		}
		while (out.size() < SLOTS) {
			out.add(ItemStack.EMPTY);
		}
		return Collections.unmodifiableList(out);
	}

	/** What stays in a pocket through a new roll: a house key, a realm's steel blueprint. */
	static boolean keeps(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(Locks.KEY) || SteelRealms.BLUEPRINT != null && stack.is(SteelRealms.BLUEPRINT));
	}

	private static int countKept(List<ItemStack> out) {
		int n = 0;
		while (n < out.size() && keeps(out.get(n))) {
			n++;
		}
		return n;
	}

	/** Whether {@code mob}'s pockets hold something {@code match}es. */
	static boolean holds(ServerLevel level, Mob mob, java.util.function.Predicate<ItemStack> match) {
		return pockets(level, mob).stream().anyMatch(s -> !s.isEmpty() && match.test(s));
	}

	/**
	 * Puts {@code stack} into {@code mob}'s first empty pocket (rolling them first); false if they have no pockets or none is
	 * free.
	 */
	static boolean give(ServerLevel level, Mob mob, ItemStack stack) {
		List<ItemStack> pockets = pockets(level, mob);
		for (int i = 0; i < pockets.size(); i++) {
			if (pockets.get(i).isEmpty()) {
				set(mob, i, stack);
				return true;
			}
		}
		return false;
	}

	/** What {@code mob}'s trade or band has on them. */
	private static List<Thing> trade(Mob mob) {
		if (mob instanceof Worker w && Pirates.isPirate(w.role())) {
			return PIRATE;
		}
		if (Barbarians.isCampVillager(mob) || mob instanceof Folk f && f.role() != Folk.GUARD && f.role() != Folk.ARCHER && f.combatant()) {
			return RAIDER;
		}
		if (mob instanceof AbstractIllager) {
			return ILLAGER;
		}
		if (mob.entityTags().contains(ForestRangers.TAG)) {
			return RANGER;
		}
		if (mob instanceof Villager v) {
			String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
			return TRADES.getOrDefault(job, List.of());
		}
		if (mob instanceof Folk f && (f.role() == Folk.GUARD || f.role() == Folk.ARCHER)) {
			return TRADES.get("guard");
		}
		return List.of();
	}

	private static void add(List<ItemStack> out, Thing thing, RandomSource random) {
		RpgKit.item(thing.id()).ifPresent(item -> out.add(new ItemStack(item, thing.least() + random.nextInt(thing.most() - thing.least() + 1))));
	}

	/**
	 * From {@code Pickpocket}: one stack (a share of it, at least one) lifted out of {@code mob}'s pockets at random, or empty
	 * when they hold nothing.
	 */
	static ItemStack lift(ServerLevel level, Mob mob, RandomSource random, float share) {
		List<ItemStack> pockets = pockets(level, mob);
		List<Integer> full = new ArrayList<>();
		for (int i = 0; i < pockets.size(); i++) {
			if (!pockets.get(i).isEmpty()) {
				full.add(i);
			}
		}
		if (full.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int slot = full.get(random.nextInt(full.size()));
		ItemStack left = pockets.get(slot).copy();
		ItemStack taken = left.split(Math.max(1, Math.round(left.getCount() * share)));
		set(mob, slot, left);
		return taken;
	}

	/** From {@code LivingEntityDeathLootMixin}, as {@code mob}'s gear drops: its belt and pockets drop too. */
	public static void died(ServerLevel level, LivingEntity entity) {
		if (!(entity instanceof Mob mob)) {
			return;
		}
		ItemStack belt = Sidearms.take(mob);
		if (!belt.isEmpty()) {
			mob.spawnAtLocation(level, belt);
		}
		if (!has(mob)) {
			return;
		}
		for (ItemStack stack : pockets(level, mob)) {
			if (!stack.isEmpty()) {
				mob.spawnAtLocation(level, stack.copy());
			}
		}
		mob.setAttached(POCKETS, List.of());
	}

	// ---- searching them --------------------------------------------------------------------------------------------------

	/** Opens the search screen on {@code body} for {@code player}: the body menu's row has checked they may. */
	static void open(ServerPlayer player, Mob body) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		pockets(level, body);
		Belongings belongings = new Belongings(level, body, player);
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.translatable("container.jaymoskingdoms.npc_pockets", body.getDisplayName());
			}

			@Override
			public AbstractContainerMenu createMenu(int menuId, Inventory inventory, Player menuPlayer) {
				return new ChestMenu(MenuType.GENERIC_9x2, menuId, inventory, belongings, 2);
			}
		});
	}

	/**
	 * A body's belt (slot 0) and pockets (1 on) as a container: every read and write goes to the body. Taking from it where a
	 * realm's law holds is a theft anyone watching sees, once a screen; what is taken is marked stolen.
	 */
	static final class Belongings implements Container {
		private final ServerLevel level;
		private final Mob body;
		private final ServerPlayer player;
		private boolean seen;

		Belongings(ServerLevel level, Mob body, ServerPlayer player) {
			this.level = level;
			this.body = body;
			this.player = player;
		}

		@Override
		public int getContainerSize() {
			return 1 + SLOTS;
		}

		@Override
		public boolean isEmpty() {
			for (int i = 0; i < getContainerSize(); i++) {
				if (!getItem(i).isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public ItemStack getItem(int slot) {
			if (slot == 0) {
				return Sidearms.belt(this.body);
			}
			List<ItemStack> pockets = this.body.getAttachedOrElse(POCKETS, List.of());
			return slot - 1 < pockets.size() && slot >= 1 ? pockets.get(slot - 1) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack stack = getItem(slot);
			if (stack.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack left = stack.copy();
			ItemStack taken = left.split(count);
			setItem(slot, left);
			taken(taken);
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = getItem(slot).copy();
			setItem(slot, ItemStack.EMPTY);
			taken(stack);
			return stack;
		}

		/** Something was taken off them: where a realm's law holds, marked stolen, and seen by anyone watching (once a screen). */
		private void taken(ItemStack stack) {
			if (stack.isEmpty()) {
				return;
			}
			Faction f = Pickpocket.law(this.level, this.body);
			if (f == null) {
				return;
			}
			StolenGoods.mark(this.level, stack, f);
			if (!this.seen) {
				this.seen = true;
				List<LivingEntity> seenBy = new ArrayList<>(Theft.crimeWitnesses(this.level, f, this.player));
				seenBy.remove(this.body);
				Theft.commit(this.player, this.level, f, Theft.Crime.PICKPOCKET, null, true, seenBy);
			}
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (slot == 0) {
				Sidearms.setBelt(this.body, stack.copy());
			} else if (slot >= 1 && slot <= SLOTS && has(this.body)) {
				set(this.body, slot - 1, stack.copy());
			}
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return this.body.isAlive() && !this.body.isRemoved() && player.isAlive() && this.body.level() == player.level()
				&& Pickpocket.searchable(this.body) && !Downed.isDowned(player) && player.isWithinEntityInteractionRange(this.body, REACH_SLACK);
		}

		@Override
		public void clearContent() {
			// their belongings aren't cleared from here
		}
	}
}
