package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Banks (Andy, 2026-11-23, after Numismatic Overhaul's ideas; nothing of its code; {@code docs/Banks-Plan.md}): "a new
 * bank in every realm with enough commerce to make sense", where "the money changers should work here as well", and
 * "realms can fall and so will your money, meaning players can have a real (under)hand in their finances".
 *
 * <ul>
 * <li><b>The bank</b> ({@link #ID}, {@link #draw}): a stone counting house with a counter and a strongroom. A realm
 * raises one once it has a market and is wealthy; a realm of the Coin creed as soon as it has a market
 * ({@link #mayBuild}). Its tellers are the town's money-changers ({@link Currencies#changer}): in working hours they keep
 * to the counter ({@link #tick}), and their trade screen still changes coin.</li>
 * <li><b>Accounts</b>: one per player per realm, whichever of its towns' banks the coin went into (a fiefdom's bank is its
 * realm's, {@link Wars#top}). Banking is on a teller's talk menu while they're at the counter ({@link #open}): deposit
 * (your coin weighed as the town takes it, {@link Money#count}), withdraw (in the town's coin), a letter of credit,
 * borrow, repay.</li>
 * <li><b>Interest</b>: every {@link #INTEREST_DAYS} days, {@link #INTEREST} on up to {@link #INTEREST_CAP} emeralds' worth
 * of a balance, out of the realm's treasury while it can pay.</li>
 * <li><b>Loans</b>: up to a tenth of your reputation with the realm in emeralds' worth (none below
 * {@link #LOAN_FROM_REP}), one at a time, {@link #LOAN_RATE} on top, due in {@link #LOAN_DAYS} days, out of the treasury
 * and back into it. Overdue, the bank takes it from your balance, and what it can't take costs you standing every day.</li>
 * <li><b>Letters of credit</b> ({@link LetterOfCredit}): your balance as a sealed note in your name, cashed only by you,
 * at any bank: free in the realm it's drawn on, {@link #LETTER_FEE} at another's. A thief who lifts it has paper.</li>
 * <li><b>A fallen realm takes its bank with it</b>: its balances are gone, its letters are waste paper and its loans are
 * forgotten. A vault is kept with its realm's seat, so a realm founded later under the same number doesn't inherit it.
 * A player who dies for good ({@link #reborn}) starts with no accounts, no loans, and letters that are no longer theirs.</li>
 * </ul>
 */
public final class Banks {
	// ---- tuning ----
	/** The building's catalogue id. */
	public static final String ID = "bank";
	/** MCA's talk menu answer that opens the banking card. */
	public static final String ANSWER = "jaymoskingdoms_banking";
	/** Folk a realm needs before it raises a bank (the market's own mark). */
	static final int FROM_SIZE = 24;
	/** Interest: paid every this many days, this share, on up to this many emeralds' worth of a balance. */
	static final int INTEREST_DAYS = 7;
	static final double INTEREST = 0.02;
	static final long INTEREST_CAP = 500L;
	/** Loans: none below this reputation; reputation per emerald's worth lent; this share on top; due in this many days. */
	static final int LOAN_FROM_REP = 100, REP_PER_EMERALD = 10, LOAN_DAYS = 7;
	static final double LOAN_RATE = 0.10;
	/** Standing lost each day a loan is overdue and can't be taken from the balance. */
	static final int OVERDUE_REP = -25;
	/** A letter cashed at another realm's bank: this share is kept (more than the cut on foreign coin, so it's no cheaper). */
	static final double LETTER_FEE = Currencies.FOREIGN_CUT + 0.02;
	/** A teller this close to the bank's middle (blocks) is at the counter; how far off one is called back from. */
	static final double COUNTER = 6.0, TELLER_RANGE = 64.0;
	/** How often (ticks) the tellers are sent to their counters and the books are looked over. */
	static final int EVERY = 200;
	static final float TELLER_WALK = 0.5F;

	/** A loan: what is owed (milli), and the day it is due. */
	record Loan(long owed, long due) {
		static final Codec<Loan> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("owed").forGetter(Loan::owed), Codec.LONG.fieldOf("due").forGetter(Loan::due)).apply(i, Loan::new));
	}

	/** One realm's books: its seat (so a later realm of the same number is a new bank), balances (milli) and loans, by player. */
	record Vault(BlockPos seat, Map<String, Long> balances, Map<String, Loan> loans, long paidDay) {
		static final Codec<Vault> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("seat").forGetter(Vault::seat),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("balances").forGetter(Vault::balances),
			Codec.unboundedMap(Codec.STRING, Loan.CODEC).fieldOf("loans").forGetter(Vault::loans),
			Codec.LONG.fieldOf("paid_day").forGetter(Vault::paidDay)).apply(i, Vault::new));

		long balance(UUID player) {
			return this.balances.getOrDefault(player.toString(), 0L);
		}

		@Nullable Loan loan(UUID player) {
			return this.loans.get(player.toString());
		}

		Vault withBalance(UUID player, long milli) {
			Map<String, Long> b = new HashMap<>(this.balances);
			if (milli <= 0L) {
				b.remove(player.toString());
			} else {
				b.put(player.toString(), milli);
			}
			return new Vault(this.seat, Map.copyOf(b), this.loans, this.paidDay);
		}

		Vault withLoan(UUID player, @Nullable Loan loan) {
			Map<String, Loan> l = new HashMap<>(this.loans);
			if (loan == null || loan.owed() <= 0L) {
				l.remove(player.toString());
			} else {
				l.put(player.toString(), loan);
			}
			return new Vault(this.seat, this.balances, Map.copyOf(l), this.paidDay);
		}
	}

	/** Every bank's books (realm id → vault), and when each player last died for good (game time). */
	record Ledger(Map<String, Vault> vaults, Map<String, Long> reborn) {
		static final Ledger EMPTY = new Ledger(Map.of(), Map.of());
		static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Vault.CODEC).fieldOf("vaults").forGetter(Ledger::vaults),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("reborn").forGetter(Ledger::reborn)).apply(i, Ledger::new));
	}

	static final AttachmentType<Ledger> LEDGER = AttachmentRegistry.create(JaymosKingdoms.id("banks"), b -> b.persistent(Ledger.CODEC));

	/** The last day the books were looked over. */
	private static long lastDay = -1L;

	private Banks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Banks::tick);
	}

	// ---- the books -----------------------------------------------------------------------------------------------------

	private static Ledger ledger(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(LEDGER, Ledger.EMPTY);
	}

	private static void save(MinecraftServer server, Ledger ledger) {
		server.overworld().setAttached(LEDGER, ledger);
	}

	/** {@code realm}'s vault as it stands (a fresh one if it has none, or only an older realm's of the same number). */
	private static Vault vault(MinecraftServer server, Faction realm) {
		Vault v = ledger(server).vaults().get(Integer.toString(realm.id()));
		return v != null && v.seat().equals(realm.seat()) ? v : new Vault(realm.seat(), Map.of(), Map.of(), Quests.day(server.overworld()));
	}

	private static void put(MinecraftServer server, Faction realm, Vault v) {
		Ledger l = ledger(server);
		Map<String, Vault> vaults = new HashMap<>(l.vaults());
		vaults.put(Integer.toString(realm.id()), v);
		save(server, new Ledger(Map.copyOf(vaults), l.reborn()));
	}

	/** {@code player} has died for good: their accounts and loans are gone, and the letters they drew are no longer theirs. */
	static void reborn(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Ledger l = ledger(server);
		Map<String, Vault> vaults = new HashMap<>();
		l.vaults().forEach((id, v) -> vaults.put(id, v.withBalance(player.getUUID(), 0L).withLoan(player.getUUID(), null)));
		Map<String, Long> reborn = new HashMap<>(l.reborn());
		reborn.put(player.getUUID().toString(), server.overworld().getGameTime());
		save(server, new Ledger(Map.copyOf(vaults), Map.copyOf(reborn)));
	}

	/** When {@code player} last died for good (game time), or -1. */
	static long rebornAt(MinecraftServer server, UUID player) {
		return ledger(server).reborn().getOrDefault(player.toString(), -1L);
	}

	/** Whether realm {@code id} still stands where its vault was kept. */
	static boolean stands(MinecraftServer server, int id, BlockPos seat) {
		Faction f = Factions.get(server, id);
		return f != null && !f.fallen() && f.seat().equals(seat);
	}

	// ---- the building ----------------------------------------------------------------------------------------------------

	/** Whether {@code f} may raise its bank now (Planner): a realm with a market, wealthy or of the Coin creed. */
	static boolean mayBuild(MinecraftServer server, Faction f) {
		return f.type().isRealm() && Works.of(server, f.id()).count("market") > 0
			&& (GreatWars.creed(server, f) == GreatWars.Creed.COIN || Wealth.atLeast(server, f, Wealth.Tier.WEALTHY));
	}

	/** The middle of {@code f}'s standing bank, or null if it has none. */
	static @Nullable Vec3 counter(MinecraftServer server, Faction f) {
		for (Works.Built b : Works.of(server, f.id()).built()) {
			if (b.entry().equals(ID) && b.ruined() == 0) {
				return new Vec3((b.min().getX() + b.max().getX() + 1) / 2.0, b.min().getY() + 1, (b.min().getZ() + b.max().getZ() + 1) / 2.0);
			}
		}
		return null;
	}

	/**
	 * A counting house (11 x 9): stone walls with pillars, a front door, a counter across the hall with bars above it,
	 * and the strongroom behind (barrels of coin, a lantern) where the tellers stand.
	 */
	static Blueprint draw(Buildings.Kit k) {
		Buildings.Sketch s = new Buildings.Sketch(ID, 11, 9, 8);
		Palette p = k.p;
		for (int x = 0; x < 11; x++) {
			for (int z = 0; z < 9; z++) {
				s.set(x, 0, z, p.floor());
				boolean edge = x == 0 || x == 10 || z == 0 || z == 8;
				for (int y = 1; y <= 4; y++) {
					if (edge) {
						boolean pillar = (x == 0 || x == 10) && (z == 0 || z == 8 || z == 4) || (z == 0 || z == 8) && (x == 0 || x == 10 || x == 5);
						s.set(x, y, z, pillar ? p.trim() : p.wall());
					}
				}
				s.set(x, 5, z, edge ? p.trim() : p.slab());
			}
		}
		for (int x : new int[] {2, 3, 7, 8}) {
			s.set(x, 2, 0, k.pane);
			s.set(x, 3, 0, k.pane);
		}
		for (int z : new int[] {2, 6}) {
			s.set(0, 2, z, k.pane);
			s.set(10, 2, z, k.pane);
		}
		s.door(5, 1, 0, Direction.NORTH, k.door);
		// the counter across the hall, a gap at the east end to get behind it, bars above
		for (int x = 1; x <= 8; x++) {
			s.set(x, 1, 5, Buildings.Sketch.top(p.woodSlab()));
			if (x % 2 == 0) {
				s.set(x, 2, 5, Blocks.IRON_BARS.defaultBlockState());
			}
		}
		// the strongroom along the back wall
		for (int x = 1; x <= 9; x++) {
			s.set(x, 1, 7, Blocks.BARREL.defaultBlockState());
		}
		s.set(5, 2, 7, Blocks.BARREL.defaultBlockState());
		s.set(5, 4, 3, Buildings.Sketch.hanging(Blocks.LANTERN));
		s.set(5, 4, 6, Buildings.Sketch.hanging(Blocks.LANTERN));
		return s.done();
	}

	// ---- the tellers, and the daily books ---------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		if (t >= Routines.WORK_FROM && t < Routines.WORK_TO) {
			for (Faction f : Factions.all(server)) {
				Vec3 at = f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat()) ? null : counter(server, f);
				if (at == null) {
					continue;
				}
				for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(BlockPos.containing(at)).inflate(TELLER_RANGE),
					v -> v.isAlive() && Currencies.changer(v) && Routines.idle(v))) {
					if (v.position().distanceTo(at) > COUNTER / 2) {
						BehaviorUtils.setWalkAndLookTargetMemories(v, BlockPos.containing(at), TELLER_WALK, 1);
					}
				}
			}
		}
		long day = Quests.day(level);
		if (day != lastDay) {
			lastDay = day;
			books(server, level, day);
		}
	}

	/** Once a day: fallen realms' banks closed, interest paid, overdue loans taken in. */
	private static void books(MinecraftServer server, ServerLevel level, long day) {
		Ledger l = ledger(server);
		if (l.vaults().isEmpty()) {
			return;
		}
		Map<String, Vault> vaults = new HashMap<>();
		for (Map.Entry<String, Vault> e : l.vaults().entrySet()) {
			int id = Integer.parseInt(e.getKey());
			Vault v = e.getValue();
			if (!stands(server, id, v.seat())) {
				// the realm fell: its bank, and every coin in it, with it
				for (String who : v.balances().keySet()) {
					ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(who));
					if (p != null) {
						p.sendSystemMessage(Component.translatable("bank.jaymoskingdoms.fell", Money.text(v.balances().get(who))).withStyle(ChatFormatting.RED));
					}
				}
				continue;
			}
			Faction realm = Factions.get(server, id);
			// interest, out of the treasury while it can
			if (day - v.paidDay() >= INTEREST_DAYS) {
				long treasury = realm.treasury() * Money.UNIT;
				long paid = 0L;
				Map<String, Long> balances = new HashMap<>(v.balances());
				for (Map.Entry<String, Long> b : v.balances().entrySet()) {
					long due = (long)(Math.min(b.getValue(), INTEREST_CAP * Money.UNIT) * INTEREST);
					if (due <= 0L || paid + due > treasury) {
						continue;
					}
					paid += due;
					balances.put(b.getKey(), b.getValue() + due);
				}
				long emeralds = (paid + Money.UNIT - 1) / Money.UNIT;
				if (emeralds > 0L) {
					Factions.update(server, id, f -> f.withTreasury(Math.max(0L, f.treasury() - emeralds)));
				}
				v = new Vault(v.seat(), Map.copyOf(balances), v.loans(), day);
			}
			// overdue loans: from the balance first, then the borrower's standing
			for (Map.Entry<String, Loan> ln : v.loans().entrySet()) {
				if (day <= ln.getValue().due()) {
					continue;
				}
				UUID who = UUID.fromString(ln.getKey());
				long owed = ln.getValue().owed();
				long taken = Math.min(owed, v.balance(who));
				if (taken > 0L) {
					v = v.withBalance(who, v.balance(who) - taken).withLoan(who, new Loan(owed - taken, ln.getValue().due()));
					long back = taken / Money.UNIT;
					Factions.update(server, id, f -> f.withTreasury(f.treasury() + back));
				}
				ServerPlayer p = server.getPlayerList().getPlayer(who);
				if (p != null && owed - taken > 0L) {
					Reputation.change(p, realm, OVERDUE_REP);
					p.sendSystemMessage(Component.translatable("bank.jaymoskingdoms.overdue", realm.name(), Money.text(realm, owed - taken)).withStyle(ChatFormatting.RED));
				}
			}
			vaults.put(e.getKey(), v);
		}
		save(server, new Ledger(Map.copyOf(vaults), l.reborn()));
	}

	// ---- at the counter ---------------------------------------------------------------------------------------------------

	/** The realm whose bank {@code teller} keeps, if they're a money-changer at a standing bank's counter; else null. */
	static @Nullable Faction bankOf(LivingEntity teller) {
		if (!(teller instanceof Villager v) || !Currencies.changer(v) || !(v.level() instanceof ServerLevel level) || Downed.isDowned(v)) {
			return null;
		}
		Faction town = Quests.settlement(level, v);
		if (town == null || town.fallen() || !town.type().isRealm()) {
			return null;
		}
		Vec3 at = counter(level.getServer(), town);
		return at == null || v.position().distanceTo(at) > COUNTER ? null : town;
	}

	/** Whether {@code teller} can bank for you now (MCA's talk menu shows "Banking"). */
	public static boolean canAsk(LivingEntity teller) {
		return !teller.level().isClientSide() && bankOf(teller) != null;
	}

	/** The banking card. */
	public static void open(ServerPlayer player, LivingEntity teller) {
		Faction town = bankOf(teller);
		if (town == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		Faction realm = Wars.top(server, town);
		Vault v = vault(server, realm);
		UUID me = player.getUUID();
		long balance = v.balance(me);
		Loan loan = v.loan(me);
		long carried = Money.count(player, town);
		long lend = loan != null ? 0L : lendable(player, realm);
		ItemStack held = player.getMainHandItem();
		LetterOfCredit.Note note = LetterOfCredit.note(held);

		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		List<Runnable> picks = new ArrayList<>();
		option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.deposit_all", Money.text(town, carried)), carried > 0L,
			() -> deposit(player, town, realm, carried));
		option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.deposit_half", Money.text(town, carried / 2)), carried / 2 > 0L,
			() -> deposit(player, town, realm, carried / 2));
		for (long n : new long[] {10L, 50L}) {
			long milli = Money.worth(n);
			option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.withdraw", Money.text(town, milli)), balance >= milli,
				() -> withdraw(player, town, realm, milli));
		}
		option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.withdraw_all", Money.text(town, balance)), balance > 0L,
			() -> withdraw(player, town, realm, balance));
		option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.letter", Money.text(town, balance)), balance > 0L,
			() -> letter(player, town, realm));
		if (note != null) {
			option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.cash", Money.text(town, note.amount())), true,
				() -> cash(player, town, realm, held));
		}
		if (loan == null) {
			option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.borrow", Money.text(town, lend)), lend > 0L,
				() -> borrow(player, town, realm, lend));
		} else {
			option(options, enabled, picks, Component.translatable("bank.jaymoskingdoms.repay", Money.text(town, loan.owed())), balance + carried > 0L,
				() -> repay(player, town, realm));
		}
		options.add(Component.translatable("bank.jaymoskingdoms.leave"));
		enabled.add(true);
		Component words = loan == null
			? Component.translatable("bank.jaymoskingdoms.greet", realm.name(), Money.text(town, balance))
			: Component.translatable("bank.jaymoskingdoms.greet_loan", realm.name(), Money.text(town, balance), Money.text(town, loan.owed()),
				Math.max(0L, loan.due() - Quests.day(player.level())));
		ZFighterFolk.ask(player, teller, words, options, enabled, pick -> {
			if (pick >= 0 && pick < picks.size() && bankOf(teller) == town) {
				picks.get(pick).run();
				if (player.level() instanceof ServerLevel level) {
					level.playSound(null, teller.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.7F, 1.0F);
				}
			}
		});
	}

	private static void option(List<Component> options, List<Boolean> enabled, List<Runnable> picks, Component label, boolean on, Runnable pick) {
		options.add(label);
		enabled.add(on);
		picks.add(pick);
	}

	/** What {@code realm} will lend {@code player} (milli): a tenth of their standing, while the treasury can spare it. */
	static long lendable(ServerPlayer player, Faction realm) {
		int rep = Reputation.get(player, realm.id());
		if (rep < LOAN_FROM_REP) {
			return 0L;
		}
		long emeralds = Math.min(rep / REP_PER_EMERALD, realm.treasury());
		return Money.worth(Math.max(0L, emeralds));
	}

	private static void tell(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable("bank.jaymoskingdoms." + key, args).withStyle(ChatFormatting.GOLD));
	}

	private static void deposit(ServerPlayer player, Faction town, Faction realm, long milli) {
		MinecraftServer server = player.level().getServer();
		if (milli <= 0L || !Money.charge(player, town, milli)) {
			return;
		}
		Vault v = vault(server, realm);
		put(server, realm, v.withBalance(player.getUUID(), v.balance(player.getUUID()) + milli));
		tell(player, "deposited", Money.text(town, milli), Money.text(town, v.balance(player.getUUID()) + milli));
	}

	private static void withdraw(ServerPlayer player, Faction town, Faction realm, long milli) {
		MinecraftServer server = player.level().getServer();
		Vault v = vault(server, realm);
		long take = Math.min(milli, v.balance(player.getUUID()));
		if (take <= 0L) {
			return;
		}
		put(server, realm, v.withBalance(player.getUUID(), v.balance(player.getUUID()) - take));
		Money.pay(player, town, take);
		tell(player, "withdrew", Money.text(town, take), Money.text(town, v.balance(player.getUUID()) - take));
	}

	private static void letter(ServerPlayer player, Faction town, Faction realm) {
		MinecraftServer server = player.level().getServer();
		Vault v = vault(server, realm);
		long amount = v.balance(player.getUUID());
		if (amount <= 0L) {
			return;
		}
		put(server, realm, v.withBalance(player.getUUID(), 0L));
		ItemStack note = LetterOfCredit.make(new LetterOfCredit.Note(realm.id(), realm.seat(), realm.name(), player.getUUID(),
			player.getName().getString(), amount, server.overworld().getGameTime()));
		player.getInventory().placeItemBackInInventory(note, net.minecraft.util.Prediction.SERVER_ONLY);
		tell(player, "letter_drawn", Money.text(town, amount), realm.name());
	}

	private static void cash(ServerPlayer player, Faction town, Faction realm, ItemStack held) {
		MinecraftServer server = player.level().getServer();
		LetterOfCredit.Note note = LetterOfCredit.note(held);
		if (note == null || player.getMainHandItem() != held) {
			return;
		}
		if (!note.owner().equals(player.getUUID()) || note.issued() < rebornAt(server, player.getUUID())) {
			tell(player, "not_yours");
			return;
		}
		if (!stands(server, note.realm(), note.seat())) {
			held.shrink(1);
			player.sendSystemMessage(Component.translatable("bank.jaymoskingdoms.waste_paper", note.realmName()).withStyle(ChatFormatting.RED));
			return;
		}
		long paid = note.realm() == realm.id() ? note.amount() : (long)(note.amount() * (1.0 - LETTER_FEE));
		held.shrink(1);
		Money.pay(player, town, paid);
		tell(player, "cashed", Money.text(town, paid));
	}

	private static void borrow(ServerPlayer player, Faction town, Faction realm, long milli) {
		MinecraftServer server = player.level().getServer();
		Vault v = vault(server, realm);
		long emeralds = milli / Money.UNIT;
		Faction now = Factions.get(server, realm.id());
		if (milli <= 0L || v.loan(player.getUUID()) != null || now == null || now.treasury() < emeralds) {
			return;
		}
		Factions.update(server, realm.id(), f -> f.withTreasury(f.treasury() - emeralds));
		long owed = (long)Math.ceil(milli * (1.0 + LOAN_RATE));
		long due = Quests.day(player.level()) + LOAN_DAYS;
		put(server, realm, v.withLoan(player.getUUID(), new Loan(owed, due)));
		Money.pay(player, town, milli);
		tell(player, "borrowed", Money.text(town, milli), Money.text(town, owed), LOAN_DAYS);
	}

	private static void repay(ServerPlayer player, Faction town, Faction realm) {
		MinecraftServer server = player.level().getServer();
		Vault v = vault(server, realm);
		Loan loan = v.loan(player.getUUID());
		if (loan == null) {
			return;
		}
		long fromBalance = Math.min(loan.owed(), v.balance(player.getUUID()));
		long rest = loan.owed() - fromBalance;
		long fromCoin = Math.min(rest, Money.count(player, town));
		if (fromCoin > 0L && !Money.charge(player, town, fromCoin)) {
			fromCoin = 0L;
		}
		long paid = fromBalance + fromCoin;
		put(server, realm, v.withBalance(player.getUUID(), v.balance(player.getUUID()) - fromBalance)
			.withLoan(player.getUUID(), new Loan(loan.owed() - paid, loan.due())));
		long back = paid / Money.UNIT;
		Factions.update(server, realm.id(), f -> f.withTreasury(f.treasury() + back));
		tell(player, paid >= loan.owed() ? "repaid" : "repaid_part", Money.text(town, paid), Money.text(town, loan.owed() - paid));
	}

	/** The balance (milli) {@code player} holds at {@code realm}'s bank. */
	static long balance(MinecraftServer server, Faction realm, UUID player) {
		return vault(server, realm).balance(player);
	}
}
