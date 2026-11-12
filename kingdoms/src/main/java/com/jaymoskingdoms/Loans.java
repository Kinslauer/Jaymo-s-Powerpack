package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Companions lent out, and companions taken (docs/Pawn-Plan.md rows A1, A5, A6; after the pawns of Dragon's Dogma, a
 * commercial game, that other players hire while you're away and send home with a word and a gift: ideas only).
 * <p>
 * <b>Lent.</b> A companion knows whose it is ({@link #OWNER}). While its owner is away (offline), or once its owner has
 * left it at a tavern (the table asks, {@link TableTalk}), another player can hire it: hold coin and use it on them, and
 * a small screen offers it to fight or to work ({@link #price}; work is half). The fee is kept for the owner. A hired
 * companion follows its borrower for a day ({@link #LOAN_TICKS}); one hired to work never fights, but brings what the
 * land gives (Andy: some are hired to work, not to fight). At the end the borrower is asked for a parting word (fought
 * well, worked well, good company) and may send a gift home with them (what's in their other hand). The owner hears it
 * all when they next meet: the coin, the gifts, what was said, and what they learned ({@link Learners}). Good words
 * raise the companion's standing ({@link #RATING}) and so its price.
 * <p>
 * <b>Errands</b> (row A5). At the table the owner can leave an errand: gather what they hold (wood, stone, crops and
 * the like). When the owner comes back after a day or more away, the companion has some of it ready.
 * <p>
 * <b>Taken</b> (the foe side of A1 and A5). A companion downed with nobody of yours near, and a camp's band close,
 * is taken. When it's up again it's the camp's ({@link #TAKEN}: {@link Comrades#side} gives the camp's side), and they
 * set it to work, not to fight. Its owner can buy it back (coin used on it, the camp's price), or, if the bond is close,
 * it breaks free and turns on its captors when its owner comes; with no captor near, its owner only has to reach it.
 */
public final class Loans {
	/** A day's hire (ticks), the parting question's wait (ticks), how near the borrower must be to be asked (blocks). */
	static final long LOAN_TICKS = 24000L;
	static final int PART_WAIT = 600;
	static final double ASK_NEAR = 16.0, MEET = 6.0, HIRE_NEAR = 8.0;
	/** A worker brings something this often (ticks), when within this of their borrower (blocks). */
	static final int WORK_EVERY = 1200;
	static final double WORK_NEAR = 12.0;
	/** Errand goods per whole day away, at most a stack. */
	static final int ERRAND_PER_DAY = 16;
	/** Taken: a captor this near, nobody of the owner's this near, a camp's price as times the hire, ransom said this often. */
	static final double CAPTOR_NEAR = 8.0, OWNER_FAR = 24.0, CAMP_HOME = 20.0, OWNER_COMES = 12.0;
	static final int RANSOM_TIMES = 3, RANSOM_EVERY = 600;
	/** Looked over this often (ticks). */
	static final int EVERY = 20;

	/** On a companion: who it's with (id, name). Saved. */
	public record Owner(String id, String name) {
		static final Codec<Owner> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Owner::id),
			Codec.STRING.fieldOf("name").forGetter(Owner::name)).apply(i, Owner::new));
	}

	/** On a companion lent out: whose, to whom, until when, hired to work, wins when hired. Saved. */
	public record Loan(String owner, String borrower, String borrowerName, long until, boolean work, int wins) {
		static final Codec<Loan> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("owner").forGetter(Loan::owner),
			Codec.STRING.fieldOf("borrower").forGetter(Loan::borrower),
			Codec.STRING.optionalFieldOf("borrower_name", "").forGetter(Loan::borrowerName),
			Codec.LONG.fieldOf("until").forGetter(Loan::until),
			Codec.BOOL.optionalFieldOf("work", false).forGetter(Loan::work),
			Codec.INT.optionalFieldOf("wins", 0).forGetter(Loan::wins)).apply(i, Loan::new));
	}

	/** What a companion has to tell its owner: coin kept, the words said (word index), by whom, gifts, wins learned. Saved. */
	public record News(long coin, List<Integer> words, List<String> from, List<ItemStack> gifts, int learned) {
		static final Codec<News> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("coin", 0L).forGetter(News::coin),
			Codec.INT.listOf().optionalFieldOf("words", List.of()).forGetter(News::words),
			Codec.STRING.listOf().optionalFieldOf("from", List.of()).forGetter(News::from),
			ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("gifts", List.of()).forGetter(News::gifts),
			Codec.INT.optionalFieldOf("learned", 0).forGetter(News::learned)).apply(i, News::new));

		static final News NONE = new News(0L, List.of(), List.of(), List.of(), 0);
	}

	/** On a companion a camp took: the camp, its price (milli), when. Saved. */
	public record Taken(int camp, long ransom, long since) {
		static final Codec<Taken> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("camp").forGetter(Taken::camp),
			Codec.LONG.fieldOf("ransom").forGetter(Taken::ransom),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Taken::since)).apply(i, Taken::new));
	}

	public static final AttachmentType<Owner> OWNER = AttachmentRegistry.create(JaymosKingdoms.id("comrade_owner"),
		builder -> builder.persistent(Owner.CODEC));
	/** 0 lend me when you're away, 1 never lend me, 2 left at a tavern (lent while there). Saved. */
	public static final AttachmentType<Integer> LEND = AttachmentRegistry.create(JaymosKingdoms.id("comrade_lend"),
		builder -> builder.persistent(Codec.INT));
	/** Where one left at a tavern waits. Saved. */
	static final AttachmentType<Long> TAVERN = AttachmentRegistry.create(JaymosKingdoms.id("comrade_tavern"),
		builder -> builder.persistent(Codec.LONG));
	public static final AttachmentType<Loan> LOAN = AttachmentRegistry.create(JaymosKingdoms.id("comrade_loan"),
		builder -> builder.persistent(Loan.CODEC));
	static final AttachmentType<News> NEWS = AttachmentRegistry.create(JaymosKingdoms.id("comrade_news"),
		builder -> builder.persistent(News.CODEC));
	/** Good words had from borrowers. Saved. */
	public static final AttachmentType<Integer> RATING = AttachmentRegistry.create(JaymosKingdoms.id("comrade_rating"),
		builder -> builder.persistent(Codec.INT));
	/** The item an errand gathers (its id). Saved. */
	public static final AttachmentType<String> ERRAND = AttachmentRegistry.create(JaymosKingdoms.id("comrade_errand"),
		builder -> builder.persistent(Codec.STRING));
	/** When its owner went away (game time). Saved. */
	static final AttachmentType<Long> AWAY = AttachmentRegistry.create(JaymosKingdoms.id("comrade_away"),
		builder -> builder.persistent(Codec.LONG));
	public static final AttachmentType<Taken> TAKEN = AttachmentRegistry.create(JaymosKingdoms.id("comrade_taken"),
		builder -> builder.persistent(Taken.CODEC));

	/** The hire screen (mode 0) or the parting screen (mode 1). */
	public record OpenLoan(int entityId, int mode, Component name, Component owner, String main, String second, int steps, int rating,
		int fight, int work, Component gift) implements CustomPacketPayload {
		public static final Type<OpenLoan> TYPE = new Type<>(JaymosKingdoms.id("loan_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenLoan> CODEC = new StreamCodec<>() {
			@Override
			public OpenLoan decode(RegistryFriendlyByteBuf buf) {
				return new OpenLoan(buf.readVarInt(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
					ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf));
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, OpenLoan p) {
				buf.writeVarInt(p.entityId);
				buf.writeVarInt(p.mode);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.name);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.owner);
				buf.writeUtf(p.main);
				buf.writeUtf(p.second);
				buf.writeVarInt(p.steps);
				buf.writeVarInt(p.rating);
				buf.writeVarInt(p.fight);
				buf.writeVarInt(p.work);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.gift);
			}
		};

		@Override
		public Type<OpenLoan> type() {
			return TYPE;
		}
	}

	/** The answer: 0 hire to fight, 1 hire to work, 2 part (with a word 0-3, and the gift or not). */
	public record LoanAct(int entityId, int act, int word, boolean gift) implements CustomPacketPayload {
		public static final Type<LoanAct> TYPE = new Type<>(JaymosKingdoms.id("loan_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, LoanAct> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LoanAct::entityId,
			ByteBufCodecs.VAR_INT, LoanAct::act,
			ByteBufCodecs.VAR_INT, LoanAct::word,
			ByteBufCodecs.BOOL, LoanAct::gift,
			LoanAct::new);

		@Override
		public Type<LoanAct> type() {
			return TYPE;
		}
	}

	/** Words a borrower can leave: fought well, worked well, good company, nothing. */
	static final int WORDS = 4, NO_WORD = 3;

	private static final Set<Villager> TRACKED = new HashSet<>();
	/** Asked for a parting word: until when. */
	private static final Map<Villager, Long> PARTING = new java.util.IdentityHashMap<>();
	private static final Map<UUID, Long> WORKED = new HashMap<>();
	private static final Map<UUID, Long> RANSOMED = new HashMap<>();

	private Loans() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenLoan.TYPE, OpenLoan.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LoanAct.TYPE, LoanAct.CODEC);
		if (!McaCompat.LOADED) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(LoanAct.TYPE, (payload, context) -> act(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Loans::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && (v.hasAttached(OWNER) || v.hasAttached(TAKEN))) {
				TRACKED.add(v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				TRACKED.remove(v);
				PARTING.remove(v);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TRACKED.clear();
			PARTING.clear();
			WORKED.clear();
			RANSOMED.clear();
		});
		UseEntityCallback.EVENT.register(Loans::use);
	}

	// ---- what other systems ask --------------------------------------------------------------------------------------

	/** (Comrades.side) the side of a camp that took {@code e} or bought its sword, or of its rival party; else null. */
	static @Nullable String bandSide(Entity e) {
		Taken t = e.getAttached(TAKEN);
		if (t != null) {
			return "f:" + t.camp();
		}
		Integer sold = e.getAttached(Veterans.SOLD);
		if (sold != null) {
			return "f:" + sold;
		}
		RivalParties.Party party = e.getAttached(RivalParties.PARTY);
		return party == null ? null : RivalParties.side(e, party);
	}

	/** (Comrades.busy) taken and set to work, or hired to work: they don't fight. */
	static boolean working(Mob m) {
		if (m.hasAttached(TAKEN)) {
			return true;
		}
		Loan loan = m.getAttached(LOAN);
		return loan != null && loan.work();
	}

	/** (Followers.takesOrders) {@code player} has {@code v} on loan. */
	static boolean borrowedBy(Entity v, Player player) {
		Loan loan = v.getAttached(LOAN);
		return loan != null && loan.borrower().equals(player.getUUID().toString());
	}

	/** (ThrallTargetMixin) one of a camp's taken, a sellsword it bought, or a rival party never goes for its own side. */
	public static boolean sameBand(LivingEntity a, LivingEntity b) {
		boolean odd = a.hasAttached(TAKEN) || b.hasAttached(TAKEN) || a.hasAttached(Veterans.SOLD) || b.hasAttached(Veterans.SOLD)
			|| a.hasAttached(RivalParties.PARTY) || b.hasAttached(RivalParties.PARTY);
		return odd && Comrades.side(a).equals(Comrades.side(b));
	}

	/** A companion's price (emeralds' worth) to fight; work is half. */
	static int price(Villager v) {
		int steps = Learners.steps(v.getAttachedOrElse(Learners.WINS, 0));
		return Drifters.HIRE_COST + steps / 2 + Math.min(10, v.getAttachedOrElse(RATING, 0)) / 2;
	}

	static int workPrice(Villager v) {
		return Math.max(1, price(v) / 2);
	}

	/** {@code v} can be hired by someone else now. */
	static boolean loanable(MinecraftServer server, Villager v) {
		Owner owner = v.getAttached(OWNER);
		if (owner == null || v.hasAttached(LOAN) || v.hasAttached(TAKEN) || !v.isAlive() || Downed.isDowned(v) || v.isBaby()) {
			return false;
		}
		int lend = v.getAttachedOrElse(LEND, 0);
		if (lend == 1) {
			return false;
		}
		return lend == 2 && v.hasAttached(TAVERN) || server.getPlayerList().getPlayer(uuid(owner.id())) == null;
	}

	private static @Nullable UUID uuid(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ---- the table's answers -----------------------------------------------------------------------------------------

	/** (TableTalk) how the owner wants {@code m} lent (0 when away, 1 never, 2 left at a tavern now), and an errand from {@code hand}. */
	static void fromTable(ServerPlayer player, Mob m, int lend, boolean errand) {
		if (!(m instanceof Villager v)) {
			return;
		}
		stamp(v, player);
		lend = Math.max(0, Math.min(2, lend));
		if (errand) {
			Item item = player.getMainHandItem().getItem();
			if (gatherable(item)) {
				v.setAttached(ERRAND, BuiltInRegistries.ITEM.getKey(item).toString());
				Callouts.say(v, "loan.errand", player, true, player.getMainHandItem().getHoverName());
			}
		}
		int was = v.getAttachedOrElse(LEND, 0);
		v.setAttached(LEND, lend);
		if (lend == 2 && was != 2) {
			BlockPos tavern = tavernNear(player.level(), v.blockPosition());
			v.setAttached(TAVERN, (tavern != null ? tavern : v.blockPosition()).asLong());
			v.setAttached(AWAY, player.level().getGameTime());
			McaBridge.stopFollowing(v);
			v.removeAttached(Followers.ORDER);
			Callouts.say(v, tavern != null ? "loan.tavern" : "loan.wait", player, true);
		} else if (lend != 2) {
			v.removeAttached(TAVERN);
		}
	}

	/** Raw things a companion can gather on its own: wood, stone, earth, sand, crops, fruit. */
	static boolean gatherable(Item item) {
		String id = BuiltInRegistries.ITEM.getKey(item).getPath();
		return id.endsWith("_log") || id.equals("cobblestone") || id.equals("stone") || id.equals("dirt") || id.equals("sand")
			|| id.equals("gravel") || id.equals("clay_ball") || id.equals("flint") || id.equals("wheat") || id.equals("carrot")
			|| id.equals("potato") || id.equals("beetroot") || id.equals("apple") || id.equals("sweet_berries") || id.equals("kelp")
			|| id.equals("sugar_cane") || id.equals("bamboo") || id.equals("stick") || id.equals("brown_mushroom") || id.equals("red_mushroom");
	}

	private static @Nullable BlockPos tavernNear(ServerLevel level, BlockPos at) {
		Faction f = Factions.at(level.getServer(), level, at);
		if (f == null) {
			return null;
		}
		for (Meals.Eatery e : Meals.eateries(level, f)) {
			if (e.tavern()) {
				return Construction.standable(level, e.counter(), 3);
			}
		}
		return null;
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void stamp(Villager v, ServerPlayer owner) {
		Owner o = v.getAttached(OWNER);
		if (o == null || !o.id().equals(owner.getUUID().toString()) || !o.name().equals(owner.getGameProfile().name())) {
			if (o != null && !o.id().equals(owner.getUUID().toString())) {
				// a new owner: the old one's news and errand go with the old tie
				v.removeAttached(NEWS);
				v.removeAttached(ERRAND);
				v.removeAttached(LEND);
				v.removeAttached(TAVERN);
			}
			v.setAttached(OWNER, new Owner(owner.getUUID().toString(), owner.getGameProfile().name()));
		}
		TRACKED.add(v);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (Mob m : Bonds.companions(player, 32.0)) {
				if (m instanceof Villager v && !v.hasAttached(LOAN) && !v.hasAttached(TAKEN) && !v.hasAttached(RivalParties.PARTY) && !SwornCaptains.free(v)) {
					stamp(v, player);
					if (v.getAttachedOrElse(LEND, 0) == 2 && McaCompat.following(v) != null) {
						// picked up from the tavern
						v.removeAttached(TAVERN);
						v.setAttached(LEND, 0);
					}
				}
			}
		}
		for (Villager v : new ArrayList<>(TRACKED)) {
			if (!v.isAlive() || v.isRemoved() || !(v.level() instanceof ServerLevel level)) {
				TRACKED.remove(v);
				PARTING.remove(v);
				continue;
			}
			if (v.hasAttached(TAKEN)) {
				taken(level, v, now);
				continue;
			}
			Owner owner = v.getAttached(OWNER);
			if (owner == null) {
				TRACKED.remove(v);
				continue;
			}
			ServerPlayer home = server.getPlayerList().getPlayer(uuid(owner.id()));
			Loan loan = v.getAttached(LOAN);
			if (loan != null) {
				lent(level, v, loan, now);
				continue;
			}
			if (home == null) {
				if (!v.hasAttached(AWAY)) {
					v.setAttached(AWAY, now);
				}
			} else if (home.level() == level && home.distanceTo(v) < MEET) {
				meet(home, v, now);
			}
			if (Downed.isDowned(v)) {
				take(level, v, home, now);
				continue;
			}
			Long tavern = v.getAttached(TAVERN);
			if (tavern != null && v.getAttachedOrElse(LEND, 0) == 2 && McaCompat.following(v) == null) {
				BlockPos at = BlockPos.of(tavern);
				if (v.blockPosition().distSqr(at) > 16.0 && v.getNavigation().isDone()) {
					v.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.5);
				}
			}
		}
	}

	/** On loan: the worker brings finds; at the end the borrower is asked for a word, or (gone) it just ends. */
	private static void lent(ServerLevel level, Villager v, Loan loan, long now) {
		ServerPlayer borrower = level.getServer().getPlayerList().getPlayer(uuid(loan.borrower()));
		if (borrower == null || borrower.level() != level || Downed.isDowned(v)) {
			end(v, loan, NO_WORD, ItemStack.EMPTY);
			return;
		}
		Long asked = PARTING.get(v);
		if (asked != null) {
			if (now > asked) {
				end(v, loan, NO_WORD, ItemStack.EMPTY);
			}
			return;
		}
		if (now >= loan.until()) {
			if (borrower.distanceTo(v) <= ASK_NEAR && ServerPlayNetworking.canSend(borrower, OpenLoan.TYPE)) {
				askParting(borrower, v);
			} else {
				end(v, loan, NO_WORD, ItemStack.EMPTY);
			}
			return;
		}
		if (loan.work() && borrower.distanceTo(v) < WORK_NEAR && Comrades.target(v) == null) {
			long last = WORKED.computeIfAbsent(v.getUUID(), id -> now);
			if (now - last >= WORK_EVERY && level.canSeeSky(v.blockPosition())) {
				WORKED.put(v.getUUID(), now);
				Item found = Specialties.forage(level, v.blockPosition());
				if (found != null) {
					ItemStack stack = new ItemStack(found, 2 + v.getRandom().nextInt(3));
					Component what = stack.getHoverName();
					if (!borrower.getInventory().add(stack)) {
						borrower.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
					v.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
					Callouts.say(v, "loan.work", borrower, false, what);
				}
			}
		}
	}

	private static void askParting(ServerPlayer borrower, Villager v) {
		PARTING.put(v, v.level().getGameTime() + PART_WAIT);
		Callouts.say(v, "loan.part", borrower, true, borrower.getDisplayName());
		send(borrower, v, 1);
	}

	/** The loan's over: what was said and given goes home with them. */
	private static void end(Villager v, Loan loan, int word, ItemStack gift) {
		PARTING.remove(v);
		WORKED.remove(v.getUUID());
		v.removeAttached(LOAN);
		News n = v.getAttachedOrElse(NEWS, News.NONE);
		List<Integer> words = new ArrayList<>(n.words());
		List<String> from = new ArrayList<>(n.from());
		List<ItemStack> gifts = new ArrayList<>(n.gifts());
		words.add(word);
		from.add(loan.borrowerName());
		if (!gift.isEmpty()) {
			gifts.add(gift);
		}
		int learned = n.learned() + Math.max(0, v.getAttachedOrElse(Learners.WINS, 0) - loan.wins());
		v.setAttached(NEWS, new News(n.coin(), words, from, gifts, learned));
		if (word != NO_WORD) {
			v.setAttached(RATING, v.getAttachedOrElse(RATING, 0) + 1);
		}
		McaBridge.stopFollowing(v);
	}

	/** The owner and {@code v} meet again: coin, gifts, the word, and an errand's goods; it follows again. */
	private static void meet(ServerPlayer owner, Villager v, long now) {
		News n = v.getAttached(NEWS);
		Long away = v.getAttached(AWAY);
		ItemStack goods = ItemStack.EMPTY;
		String errand = v.getAttached(ERRAND);
		if (away != null && errand != null && v.getAttachedOrElse(LEND, 0) != 2) {
			long days = (now - away) / 24000L;
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(errand));
			if (days > 0 && item != null && gatherable(item)) {
				goods = new ItemStack(item, (int)Math.min(item.getDefaultMaxStackSize(), days * ERRAND_PER_DAY));
			}
		}
		if (v.getAttachedOrElse(LEND, 0) != 2) {
			v.removeAttached(AWAY);
		}
		if (n == null && goods.isEmpty()) {
			return;
		}
		v.removeAttached(NEWS);
		v.getLookControl().setLookAt(owner, 30.0F, 30.0F);
		int gifts = 0;
		if (n != null) {
			if (n.coin() > 0) {
				Money.pay(owner, n.coin());
			}
			for (ItemStack g : n.gifts()) {
				give(owner, g.copy());
				gifts++;
			}
			int last = n.words().isEmpty() ? NO_WORD : n.words().getLast();
			String who = n.from().isEmpty() ? "" : n.from().getLast();
			if (last != NO_WORD) {
				Callouts.say(v, "loan.home", owner, true, who, Component.translatable("gui.jaymoskingdoms.loan.word." + last));
			} else if (n.learned() > 0) {
				Callouts.say(v, "loan.learned", owner, true, who);
			} else {
				Callouts.say(v, "loan.back", owner, true, who);
			}
			owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.loan.home", v.getDisplayName(), Money.text(owner, n.coin()),
				gifts, n.from().size()).withStyle(ChatFormatting.GOLD));
		}
		if (!goods.isEmpty()) {
			Component what = goods.getHoverName();
			int count = goods.getCount();
			give(owner, goods);
			if (n == null) {
				Callouts.say(v, "loan.errand.done", owner, true, count, what);
			}
		}
		if (v.getAttachedOrElse(LEND, 0) == 0 && McaCompat.following(v) == null && Followers.takesOrders(v, owner)) {
			Followers.take(v, owner, Followers.FOLLOW);
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	// ---- taken -------------------------------------------------------------------------------------------------------

	/** Downed with none of the owner's near and a camp's band close: they take {@code v}. */
	private static void take(ServerLevel level, Villager v, @Nullable ServerPlayer owner, long now) {
		if (owner != null && owner.level() == level && owner.distanceTo(v) < OWNER_FAR) {
			return;
		}
		Owner o = v.getAttached(OWNER);
		String ours = o == null ? "" : "p:" + o.id();
		if (!level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(OWNER_FAR), m -> m != v && m.isAlive() && !Downed.isDowned(m)
			&& Comrades.side(m).equals(ours)).isEmpty()) {
			return;
		}
		for (Mob captor : level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(CAPTOR_NEAR), m -> m.isAlive() && !Downed.isDowned(m)
			&& Illagers.isCampIllager(m))) {
			Illagers.Tag tag = Illagers.tag(captor);
			if (tag == null) {
				continue;
			}
			v.setAttached(TAKEN, new Taken(tag.camp(), Money.worth(price(v) * RANSOM_TIMES), now));
			McaBridge.stopFollowing(v);
			v.removeAttached(Followers.ORDER);
			Callouts.say(captor, "taken.ours", v, true, v.getDisplayName());
			return;
		}
	}

	private static void taken(ServerLevel level, Villager v, long now) {
		Taken t = v.getAttached(TAKEN);
		Owner o = v.getAttached(OWNER);
		ServerPlayer owner = o == null ? null : level.getServer().getPlayerList().getPlayer(uuid(o.id()));
		if (Downed.isDowned(v)) {
			return;
		}
		boolean ownerNear = owner != null && owner.level() == level && owner.distanceTo(v) < OWNER_COMES && !owner.isSpectator();
		List<Mob> captors = level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(OWNER_FAR), m -> m.isAlive() && !Downed.isDowned(m)
			&& Illagers.isCampIllager(m) && Illagers.tag(m).camp() == t.camp());
		if (ownerNear) {
			if (captors.isEmpty() && owner.distanceTo(v) < MEET) {
				free(v, owner, null);
				return;
			}
			if (Bonds.of(v, owner.getUUID()) >= Bonds.CLOSE) {
				free(v, owner, captors.isEmpty() ? null : captors.getFirst());
				return;
			}
			Long said = RANSOMED.get(v.getUUID());
			if (!captors.isEmpty() && (said == null || now - said > RANSOM_EVERY)) {
				RANSOMED.put(v.getUUID(), now);
				Mob speaker = captors.getFirst();
				Callouts.say(speaker, "taken.ransom", owner, true, v.getDisplayName(), Money.text(owner, t.ransom()));
				Callouts.say(v, "taken.help", owner, false, owner.getDisplayName());
			}
			return;
		}
		Camps.CampData camp = Camps.data(level.getServer(), t.camp());
		if (camp != null && v.blockPosition().distSqr(camp.center()) > CAMP_HOME * CAMP_HOME && v.getNavigation().isDone()) {
			v.getNavigation().moveTo(camp.center().getX() + 0.5, camp.center().getY(), camp.center().getZ() + 0.5, 0.5);
		} else if (camp != null && level.getRandom().nextInt(30) == 0) {
			// set to work about the camp
			v.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}
	}

	/** {@code v} is its owner's again (and, with {@code at}, turns on its captors). */
	private static void free(Villager v, ServerPlayer owner, @Nullable Mob at) {
		v.removeAttached(TAKEN);
		RANSOMED.remove(v.getUUID());
		if (Followers.takesOrders(v, owner)) {
			Followers.take(v, owner, Followers.FOLLOW);
		}
		Callouts.say(v, "taken.free", owner, true, owner.getDisplayName());
		if (at != null) {
			Comrades.aim(v, at);
		}
	}

	// ---- hiring and parting ------------------------------------------------------------------------------------------

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || sp.isSpectator()
			|| !(entity instanceof Villager v) || !Coinage.isMoney(sp.getMainHandItem()) || !v.isAlive()) {
			return InteractionResult.PASS;
		}
		Owner owner = v.getAttached(OWNER);
		Taken taken = v.getAttached(TAKEN);
		if (taken != null) {
			if (owner == null || !owner.id().equals(sp.getUUID().toString())) {
				return InteractionResult.PASS;
			}
			if (!Money.canPay(sp, taken.ransom())) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.loan.ransom_short", Money.text(sp, taken.ransom()))
					.withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			Money.charge(sp, taken.ransom());
			free(v, sp, null);
			return InteractionResult.SUCCESS;
		}
		if (borrowedBy(v, sp)) {
			if (!PARTING.containsKey(v)) {
				PARTING.put(v, v.level().getGameTime() + PART_WAIT);
			}
			send(sp, v, 1);
			return InteractionResult.SUCCESS;
		}
		if (owner == null || owner.id().equals(sp.getUUID().toString()) || !loanable(sp.level().getServer(), v)) {
			return InteractionResult.PASS;
		}
		if (!Languages.understands(sp, v)) {
			Languages.shrug(sp, v);
			return InteractionResult.SUCCESS;
		}
		send(sp, v, 0);
		return InteractionResult.SUCCESS;
	}

	private static void send(ServerPlayer player, Villager v, int mode) {
		if (!ServerPlayNetworking.canSend(player, OpenLoan.TYPE)) {
			return;
		}
		Owner owner = v.getAttached(OWNER);
		Comrades.Leanings l = Comrades.leanings(v);
		int steps = Learners.steps(v.getAttachedOrElse(Learners.WINS, 0));
		ItemStack off = player.getOffhandItem();
		ServerPlayNetworking.send(player, new OpenLoan(v.getId(), mode, v.getDisplayName(), Component.literal(owner == null ? "?" : owner.name()),
			l.main().key(), l.second().key(), steps, v.getAttachedOrElse(RATING, 0), price(v), workPrice(v),
			off.isEmpty() ? Component.empty() : off.getHoverName()));
	}

	private static void act(ServerPlayer player, LoanAct a) {
		if (!(player.level().getEntity(a.entityId()) instanceof Villager v) || v.distanceTo(player) > ASK_NEAR) {
			return;
		}
		ServerLevel level = player.level();
		if (a.act() == 2) {
			Loan loan = v.getAttached(LOAN);
			if (loan == null || !borrowedBy(v, player)) {
				return;
			}
			int word = Math.max(0, Math.min(NO_WORD, a.word()));
			ItemStack gift = ItemStack.EMPTY;
			if (a.gift() && !player.getOffhandItem().isEmpty()) {
				gift = player.getOffhandItem().copy();
				player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			}
			Callouts.say(v, word == NO_WORD ? "loan.bye" : "loan.thanks", player, true, player.getDisplayName());
			end(v, loan, word, gift);
			return;
		}
		Owner owner = v.getAttached(OWNER);
		if (owner == null || owner.id().equals(player.getUUID().toString()) || !loanable(level.getServer(), v) || v.distanceTo(player) > HIRE_NEAR) {
			return;
		}
		boolean work = a.act() == 1;
		long fee = Money.worth(work ? workPrice(v) : price(v));
		if (!Money.charge(player, fee)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.loan.short", Money.text(player, fee)).withStyle(ChatFormatting.GRAY));
			return;
		}
		News n = v.getAttachedOrElse(NEWS, News.NONE);
		v.setAttached(NEWS, new News(n.coin() + fee, n.words(), n.from(), n.gifts(), n.learned()));
		v.setAttached(LOAN, new Loan(owner.id(), player.getUUID().toString(), player.getGameProfile().name(), level.getGameTime() + LOAN_TICKS,
			work, v.getAttachedOrElse(Learners.WINS, 0)));
		v.removeAttached(Followers.ORDER);
		McaBridge.moveState(v, 2, player);
		Callouts.say(v, work ? "loan.hired.work" : "loan.hired", player, true, player.getDisplayName(), owner.name());
	}
}
