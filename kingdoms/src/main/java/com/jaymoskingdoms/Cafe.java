package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.578) Andy's cafe (patches 7 to 9 of the seven-mods plan; Cozy Cafe's idea, our own code). While a menu board's
 * owner is near it in opening hours ({@link #OPEN_FROM} to {@link #OPEN_TILL}) and it has something on its menu, townsfolk
 * and visitors come in: each walks to a free seat (a bottom slab, bottom stairs, a bench, or anything in
 * {@link #SEATS}), sits, and asks for one dish off the menu in a speech bubble. Hand it to them (use it on them) and they
 * pay in coin, eat, thank you and go. They won't wait for ever: their patience runs out and they leave cross, and a wrong
 * dish tries it further.
 *
 * <p><b>Service and tips (patch 7):</b> a quick serve earns a tip, more in a comfortable room and at cheap prices, less at
 * dear ones.
 *
 * <p><b>Name and comfort (patch 8):</b> each board keeps a {@link CafeBoard.Entity#reputation reputation} (0 to 100):
 * served customers raise it (more for a perfect dish or a quick serve), angry ones lower it. It brings more customers at
 * once and more often, and lets the room grow: seats count further from the board. Each 25 points passed puts word of
 * the place on the town boards (Tidings). Comfort is the count of cosy things round the seats ({@link #COMFORT}: rugs,
 * carpets, candles, lanterns, pots, bookshelves, banners; any Beautify block; Kitchen's cabinets and rugs), up to
 * {@link #COMFORT_MOST}: it makes customers patient and generous.
 *
 * <p><b>The trade (patch 9):</b> the Innkeeper career ({@link Careers.Job#INNKEEPER}): each customer served counts toward
 * its "serve" orders. A Journeyman's customers wait half again as long; a Master's tips are doubled, and their apprentice
 * serves for them, carrying ordered dishes from chests and barrels beside the board, the coin going to the Master.
 */
final class Cafe {
	static final int OPEN_FROM = 1000, OPEN_TILL = 12500;
	/** How near the owner must be for the cafe to be open; how far customers come from. */
	static final double OWNER_RANGE = 48.0, DRAW_RANGE = 48.0;
	/** Seats count this far from the board, plus a block per {@link #ROOM_PER_REP} reputation, to {@link #ROOM_MOST}. */
	static final int ROOM_BASE = 6, ROOM_PER_REP = 20, ROOM_MOST = 11, ROOM_UP = 2;
	/** How often (ticks) a board looks again at its seats and comfort. */
	static final int LOOK_EVERY = 200;
	/** Customers at once: 1 and one more each {@link #CROWD_PER_REP} reputation. */
	static final int CROWD_PER_REP = 25;
	/** Chance a board draws a customer each second it has room: the base, and more per point of reputation. */
	static final double DRAW_CHANCE = 0.03, DRAW_PER_REP = 0.0015;
	/** Ticks before the same villager comes again. */
	static final long AGAIN_TICKS = 6000;
	/** Ticks to reach a seat before they give up; to wait once seated (before comfort and rank); to eat. */
	static final long WALK_TICKS = 900, PATIENCE_TICKS = 1800, EAT_TICKS = 160;
	/** Ticks between reminders of what they're waiting for. */
	static final long REMIND_TICKS = 600;
	/** The share of their patience a wrong dish costs. */
	static final double WRONG_COST = 0.2;
	/** Comfort counted at most; the patience it adds at most. */
	static final int COMFORT_MOST = 40;
	static final double COMFORT_PATIENCE = 0.6;
	/** A Journeyman innkeeper's patience; a Master's tips. */
	static final double JOURNEYMAN_PATIENCE = 1.5, MASTER_TIPS = 2.0;
	/** A full tip's share of the price, for a dish handed over at once. */
	static final double TIP_SHARE = 0.3;
	/** Reputation for a dish served, a perfect one, a quick one (in the first half of their patience); lost to a wrong dish and an angry customer. */
	static final int REP_SERVED = 1, REP_PERFECT = 1, REP_QUICK = 1, REP_WRONG = 1, REP_ANGRY = 3, REP_MOST = 100, REP_TIER = 25;
	/** How near an apprentice must be to their Master's board to serve at it; how far from it they fetch. */
	static final double APPRENTICE_RANGE = 24.0;
	static final int FETCH_RANGE = 2;
	static final long ERRAND_TICKS = 400;
	static final double ARRIVE = 1.6, HAND_OVER = 2.5;
	static final float WALK = 0.55F;
	static final double SPEECH_RANGE = 12.0;
	static final int LINES = 3;
	static final String TAG = "jaymoskingdoms.cafe_seat";

	/** Extra seats (chairs from other mods...). */
	static final TagKey<Block> SEATS = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("cafe_seats"));
	/** Things that make a room comfortable. */
	static final TagKey<Block> COMFORT = TagKey.create(Registries.BLOCK, JaymosKingdoms.id("cafe_comfort"));

	enum State { WALKING, WAITING, EATING }

	static final class Customer {
		final UUID villager;
		final BlockPos board, seat;
		final Item order;
		State state = State.WALKING;
		final long came;
		long sat = -1, ate = -1, reminded;
		long patience;
		int comfort;
		/** Their dish's look, while eating; paid with a tip. */
		ItemStack dish = ItemStack.EMPTY;
		boolean tipped;

		Customer(UUID villager, BlockPos board, BlockPos seat, Item order, long came) {
			this.villager = villager;
			this.board = board;
			this.seat = seat;
			this.order = order;
			this.came = came;
		}
	}

	/** An apprentice carrying a dish to a customer. */
	record Errand(UUID apprentice, UUID customer, UUID master, ItemStack dish, long since) {
	}

	private static final Map<UUID, Customer> CUSTOMERS = new HashMap<>();
	private static final Map<UUID, Long> LAST_CAME = new HashMap<>();
	private static final Map<UUID, Errand> ERRANDS = new HashMap<>();
	private static final Set<Cushion> CUSHIONS = new HashSet<>();

	private Cafe() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Cafe::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Cushion c && c.entityTags().contains(TAG)) {
				CUSHIONS.add(c);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CUSTOMERS.clear();
			LAST_CAME.clear();
			ERRANDS.clear();
			CUSHIONS.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof Villager v)) {
				return InteractionResult.PASS;
			}
			Customer c = CUSTOMERS.get(v.getUUID());
			if (c == null || c.state != State.WAITING) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			if (!CafeBoard.dish(held)) {
				return InteractionResult.PASS;
			}
			ServerLevel level = sp.level();
			if (!held.is(c.order)) {
				wrong(level, v, c);
				return InteractionResult.SUCCESS;
			}
			if (!(level.getBlockEntity(c.board) instanceof CafeBoard.Entity board)) {
				return InteractionResult.PASS;
			}
			ItemStack dish = held.copyWithCount(1);
			if (!sp.isCreative()) {
				held.shrink(1);
			}
			serve(level, board, c, v, dish, sp);
			return InteractionResult.SUCCESS;
		});
	}

	// ---- the board's side ------------------------------------------------------------------------------------------

	/** Why a board is shut just now, or null if it's open. */
	static @Nullable String shut(ServerLevel level, CafeBoard.Entity board) {
		long time = level.getOverworldClockTime() % 24000L;
		if (time < OPEN_FROM || time > OPEN_TILL) {
			return "hours";
		}
		if (board.dishes().isEmpty()) {
			return "empty";
		}
		ServerPlayer owner = owner(level, board);
		if (owner == null || owner.level() != level || owner.distanceToSqr(Vec3.atCenterOf(board.getBlockPos())) > OWNER_RANGE * OWNER_RANGE) {
			return "away";
		}
		return null;
	}

	static @Nullable ServerPlayer owner(ServerLevel level, CafeBoard.Entity board) {
		return board.owner == null ? null : level.getServer().getPlayerList().getPlayer(board.owner);
	}

	/** Each second for an owned board: its room looked at now and then, a customer drawn in, its apprentice at work. */
	static void boardTick(ServerLevel level, CafeBoard.Entity board) {
		long now = level.getGameTime();
		if (board.seats == null || now - board.lookedAt >= LOOK_EVERY) {
			look(level, board);
			board.lookedAt = now;
		}
		ServerPlayer owner = owner(level, board);
		if (owner != null) {
			apprentice(level, board, owner, now);
		}
		if (shut(level, board) != null) {
			return;
		}
		BlockPos at = board.getBlockPos();
		int here = 0;
		Set<BlockPos> taken = new HashSet<>();
		for (Customer c : CUSTOMERS.values()) {
			if (c.board.equals(at)) {
				here++;
				taken.add(c.seat);
			}
		}
		int most = 1 + board.reputation / CROWD_PER_REP;
		RandomSource random = level.getRandom();
		if (here >= most || random.nextDouble() >= DRAW_CHANCE + DRAW_PER_REP * board.reputation) {
			return;
		}
		List<BlockPos> free = new ArrayList<>();
		for (BlockPos seat : board.seats) {
			if (!taken.contains(seat) && level.getEntitiesOfClass(Entity.class, new AABB(seat.above()).deflate(0.1), e -> e instanceof Cushion cu
				? cu.isVehicle() : e instanceof net.minecraft.world.entity.LivingEntity).isEmpty()) {
				free.add(seat);
			}
		}
		if (free.isEmpty()) {
			return;
		}
		Villager v = pick(level, board, now, random);
		if (v == null) {
			return;
		}
		List<ItemStack> dishes = board.dishes();
		Item order = dishes.get(random.nextInt(dishes.size())).getItem();
		BlockPos seat = free.get(random.nextInt(free.size()));
		Customer c = new Customer(v.getUUID(), at, seat, order, now);
		c.comfort = board.comfort;
		CUSTOMERS.put(v.getUUID(), c);
		LAST_CAME.put(v.getUUID(), now);
		walk(v, seat);
	}

	/** Someone to come in: a grown townsfolk or visitor near, free and unafraid, not a guard; visitors thrice as likely. */
	private static @Nullable Villager pick(ServerLevel level, CafeBoard.Entity board, long now, RandomSource random) {
		AABB box = new AABB(board.getBlockPos()).inflate(DRAW_RANGE, 16.0, DRAW_RANGE);
		Set<UUID> apprentices = new HashSet<>();
		for (Errand e : ERRANDS.values()) {
			apprentices.add(e.apprentice());
		}
		ServerPlayer owner = owner(level, board);
		if (owner != null) {
			Careers.Post p = Careers.of(owner).post(Careers.Job.INNKEEPER.key);
			if (p != null && p.apprentice().isPresent()) {
				apprentices.add(p.apprentice().get());
			}
		}
		List<Villager> pool = new ArrayList<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !v.isPassenger())) {
			UUID id = v.getUUID();
			if (CUSTOMERS.containsKey(id) || apprentices.contains(id) || now - LAST_CAME.getOrDefault(id, Long.MIN_VALUE / 2) < AGAIN_TICKS) {
				continue;
			}
			if (McaCompat.isGuard(v) || BlastFright.standsGround(v) || Frights.afraid(id) || McaCompat.panicking(v) || Meals.eating(v) || Meals.cook(v)
				|| McaCompat.busy(v) || Rulers.is(v)) {
				continue;
			}
			pool.add(v);
			if (Visitors.is(v)) {
				pool.add(v);
				pool.add(v);
			}
		}
		return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
	}

	/** The seats and the comfort round a board. */
	private static void look(ServerLevel level, CafeBoard.Entity board) {
		BlockPos at = board.getBlockPos();
		int r = Math.min(ROOM_MOST, ROOM_BASE + board.reputation / ROOM_PER_REP);
		List<BlockPos> seats = new ArrayList<>();
		int comfort = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = -ROOM_UP; dy <= ROOM_UP; dy++) {
					p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
					BlockState state = level.getBlockState(p);
					if (state.isAir()) {
						continue;
					}
					if (cosy(state)) {
						comfort++;
					}
					if (seatHeight(level, p, state) > 0.0 && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
						seats.add(p.immutable());
					}
				}
			}
		}
		board.seats = seats;
		board.comfort = Math.min(COMFORT_MOST, comfort);
	}

	private static boolean cosy(BlockState state) {
		if (state.is(COMFORT)) {
			return true;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals("beautify")
			|| id.getNamespace().equals("jaymoskitchen") && (id.getPath().endsWith("_cabinet") || id.getPath().endsWith("_rug")
				|| id.getPath().equals("bone_stand"));
	}

	/** How high one sits on {@code state} (above its block), or 0 if it isn't a seat. */
	static double seatHeight(ServerLevel level, BlockPos pos, BlockState state) {
		Block b = state.getBlock();
		if (b instanceof SlabBlock) {
			return state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM ? 0.5 : 0.0;
		}
		if (b instanceof StairBlock) {
			return state.getValue(BlockStateProperties.HALF) == Half.BOTTOM ? 0.5 : 0.0;
		}
		if (b instanceof BedBlock) {
			return 0.0;
		}
		boolean tagged = state.is(SEATS);
		if (tagged || b instanceof com.jaymoskingdoms.carpentry.FurnitureBlock) {
			VoxelShape shape = state.getCollisionShape(level, pos);
			double top = shape.isEmpty() ? 0.5 : shape.max(Direction.Axis.Y);
			// a bench's seat; a table (top at a full block) isn't one
			return tagged ? Math.max(0.3, Math.min(1.0, top)) : top > 0.3 && top < 0.65 ? top : 0.0;
		}
		return 0.0;
	}

	/** What the owner sees opening their board. */
	static void status(ServerLevel level, CafeBoard.Entity board, ServerPlayer player) {
		look(level, board);
		board.lookedAt = level.getGameTime();
		String shut = shut(level, board);
		Component open = Component.translatable(shut == null ? "cafe.jaymoskingdoms.open" : "cafe.jaymoskingdoms.shut." + shut);
		player.sendOverlayMessage(Component.translatable("cafe.jaymoskingdoms.status", open, tierName(board.reputation), board.seats.size(),
			board.comfort, board.markup.title()).withStyle(ChatFormatting.GOLD));
	}

	static Component tierName(int reputation) {
		return Component.translatable("cafe.jaymoskingdoms.tier." + Math.min(4, reputation / REP_TIER));
	}

	/** A board's reputation moved by {@code by}; word goes round when it passes a tier. */
	static void reputation(ServerLevel level, CafeBoard.Entity board, int by) {
		int before = board.reputation;
		board.reputation = Math.max(0, Math.min(REP_MOST, before + by));
		board.setChanged();
		int was = before / REP_TIER, now = board.reputation / REP_TIER;
		if (now > was) {
			Faction f = Factions.around(level.getServer(), level, board.getBlockPos(), 160.0);
			Component town = f == null ? Component.translatable("cafe.jaymoskingdoms.news.wilds") : Component.literal(f.name());
			Post.news(level.getServer(), Component.translatable("cafe.jaymoskingdoms.news." + now, board.ownerName, town));
		}
	}

	/** The board at {@code pos} is gone: its customers leave. */
	static void closed(ServerLevel level, BlockPos pos) {
		for (Iterator<Map.Entry<UUID, Customer>> it = CUSTOMERS.entrySet().iterator(); it.hasNext();) {
			Customer c = it.next().getValue();
			if (c.board.equals(pos)) {
				if (level.getEntity(c.villager) instanceof Villager v) {
					stand(v);
				}
				it.remove();
			}
		}
	}

	// ---- the customers ---------------------------------------------------------------------------------------------

	private static int clock;

	private static void tick(MinecraftServer server) {
		if (++clock % 5 != 0) {
			return;
		}
		if (!CUSTOMERS.isEmpty()) {
			for (Iterator<Map.Entry<UUID, Customer>> it = CUSTOMERS.entrySet().iterator(); it.hasNext();) {
				Customer c = it.next().getValue();
				Villager v = find(server, c.villager);
				if (v == null || !step((ServerLevel)v.level(), v, c)) {
					if (v != null) {
						stand(v);
					}
					it.remove();
				}
			}
		}
		if (!ERRANDS.isEmpty()) {
			for (Iterator<Errand> it = ERRANDS.values().iterator(); it.hasNext();) {
				if (!errand(server, it.next())) {
					it.remove();
				}
			}
		}
		if (clock % 20 == 0 && !CUSHIONS.isEmpty()) {
			for (Iterator<Cushion> it = CUSHIONS.iterator(); it.hasNext();) {
				Cushion seat = it.next();
				if (seat.isRemoved()) {
					it.remove();
				} else if (!seat.isVehicle()) {
					seat.discard();
					it.remove();
				}
			}
		}
	}

	private static @Nullable Villager find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof Villager v && v.isAlive()) {
				return v;
			}
		}
		return null;
	}

	/** One think for a customer; false once they're done (or gone). */
	private static boolean step(ServerLevel level, Villager v, Customer c) {
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		if (!(level.getBlockEntity(c.board) instanceof CafeBoard.Entity board)) {
			return false;
		}
		if (Frights.afraid(c.villager) || McaCompat.panicking(v) || v.getTarget() != null) {
			return false;
		}
		switch (c.state) {
			case WALKING -> {
				Vec3 seat = Vec3.atBottomCenterOf(c.seat);
				if (v.position().distanceToSqr(seat) <= ARRIVE * ARRIVE || v.position().distanceToSqr(seat.add(0, 1, 0)) <= ARRIVE * ARRIVE) {
					sit(level, v, c);
					c.state = State.WAITING;
					c.sat = now;
					c.reminded = now;
					c.patience = patience(level, board, c);
					say(level, v, "order", random, c.order.getName(new ItemStack(c.order)));
					return true;
				}
				if (now - c.came > WALK_TICKS) {
					return false;
				}
				if (v.getNavigation().isDone() && clock % 40 == 0) {
					walk(v, c.seat);
				}
				return true;
			}
			case WAITING -> {
				if (!v.isPassenger() && v.position().distanceToSqr(Vec3.atBottomCenterOf(c.seat)) <= 4.0) {
					sit(level, v, c);
				}
				look(level, v, board);
				long waited = now - c.sat;
				if (waited >= c.patience) {
					say(level, v, "angry", random);
					McaCompat.cheer(v, Meals.WENT_WITHOUT_MOOD);
					reputation(level, board, -REP_ANGRY);
					return false;
				}
				if (now - c.reminded >= REMIND_TICKS) {
					c.reminded = now;
					say(level, v, waited > c.patience / 2 ? "waiting_long" : "waiting", random, c.order.getName(new ItemStack(c.order)));
				}
				return true;
			}
			case EATING -> {
				v.getNavigation().stop();
				if (random.nextInt(3) != 0) {
					v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, c.dish.getItem()), v.getX(), v.getEyeY() - 0.2, v.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);
				}
				if (now - c.ate >= EAT_TICKS) {
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.3F, 1.2F);
					say(level, v, c.tipped ? "thanks_tip" : "thanks", random);
					McaCompat.cheer(v, Meals.TAVERN_MOOD);
					return false;
				}
				return true;
			}
		}
		return false;
	}

	/** How long a customer waits: longer in comfort and for a Journeyman (or better) innkeeper. */
	private static long patience(ServerLevel level, CafeBoard.Entity board, Customer c) {
		double p = PATIENCE_TICKS * (1.0 + COMFORT_PATIENCE * Math.min(COMFORT_MOST, c.comfort) / COMFORT_MOST);
		ServerPlayer owner = owner(level, board);
		if (owner != null && Careers.heldRank(owner, Careers.Job.INNKEEPER) >= Careers.JOURNEYMAN) {
			p *= JOURNEYMAN_PATIENCE;
		}
		return Math.round(p);
	}

	private static void wrong(ServerLevel level, Villager v, Customer c) {
		say(level, v, "wrong", level.getRandom(), c.order.getName(new ItemStack(c.order)));
		c.sat -= Math.round(c.patience * WRONG_COST);
		if (level.getBlockEntity(c.board) instanceof CafeBoard.Entity board) {
			reputation(level, board, -REP_WRONG);
		}
	}

	/** {@code dish} handed to {@code v}; {@code payee} is paid the price and the tip. */
	static void serve(ServerLevel level, CafeBoard.Entity board, Customer c, Villager v, ItemStack dish, ServerPlayer payee) {
		long now = level.getGameTime();
		double left = c.patience <= 0 ? 0.0 : Math.max(0.0, 1.0 - (double)(now - c.sat) / c.patience);
		double fresh = CafeBoard.freshness(level, dish);
		long price = Math.max(CafeBoard.LEAST_PRICE, Math.round(CafeBoard.fair(dish) * board.markup.price * CafeBoard.quality(dish) * fresh));
		double tipShare = TIP_SHARE * left * (1.0 + (double)c.comfort / (COMFORT_MOST * 2)) * board.markup.tips;
		if (Careers.heldRank(payee, Careers.Job.INNKEEPER) >= Careers.MASTER) {
			tipShare *= MASTER_TIPS;
		}
		long tip = Math.round(price * tipShare);
		if (tip < price / 20) {
			tip = 0;
		}
		Faction f = Factions.around(level.getServer(), level, board.getBlockPos(), 160.0);
		Money.pay(payee, f, price + tip);
		payee.sendOverlayMessage(Component.translatable(tip > 0 ? "cafe.jaymoskingdoms.paid_tip" : "cafe.jaymoskingdoms.paid",
			Money.text(f, price), Money.text(f, tip)).withStyle(ChatFormatting.GOLD));
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 1.2F);
		int rep = REP_SERVED + (CafeBoard.perfect(dish) ? REP_PERFECT : 0) + (left >= 0.5 ? REP_QUICK : 0)
			// (food nearly off loses the cafe what serving it would have won, and they say so)
			- (fresh <= CafeBoard.OFF_PRICE ? REP_SERVED : 0);
		if (fresh <= CafeBoard.OFF_PRICE) {
			Speech.say(level, v, Component.translatable("cafe.jaymoskingdoms.stale"), Speech.YELLOW, 12.0);
		}
		reputation(level, board, rep);
		Careers.work(level, payee, "serve", 1, board.getBlockPos());
		c.state = State.EATING;
		c.ate = now;
		c.dish = dish;
		c.tipped = tip > 0;
	}

	// ---- an apprentice ---------------------------------------------------------------------------------------------

	/** A Master innkeeper's apprentice takes the next waiting order they can fill from the stores beside the board. */
	private static void apprentice(ServerLevel level, CafeBoard.Entity board, ServerPlayer owner, long now) {
		if (Careers.heldRank(owner, Careers.Job.INNKEEPER) < Careers.MASTER) {
			return;
		}
		Careers.Post p = Careers.of(owner).post(Careers.Job.INNKEEPER.key);
		Optional<UUID> id = p == null ? Optional.empty() : p.apprentice();
		if (id.isEmpty() || ERRANDS.containsKey(id.get())) {
			return;
		}
		if (!(level.getEntity(id.get()) instanceof Villager a) || !a.isAlive()
			|| a.distanceToSqr(Vec3.atCenterOf(board.getBlockPos())) > APPRENTICE_RANGE * APPRENTICE_RANGE) {
			return;
		}
		Set<UUID> carried = new HashSet<>();
		for (Errand e : ERRANDS.values()) {
			carried.add(e.customer());
		}
		for (Customer c : CUSTOMERS.values()) {
			if (!c.board.equals(board.getBlockPos()) || c.state != State.WAITING || carried.contains(c.villager)) {
				continue;
			}
			ItemStack dish = take(level, board.getBlockPos(), c.order);
			if (dish.isEmpty()) {
				continue;
			}
			ERRANDS.put(a.getUUID(), new Errand(a.getUUID(), c.villager, owner.getUUID(), dish, now));
			a.setItemInHand(InteractionHand.MAIN_HAND, dish.copy());
			Speech.say(level, a, Component.translatable("cafe.jaymoskingdoms.apprentice.coming." + level.getRandom().nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
			return;
		}
	}

	/** One of {@code item} out of a chest or barrel beside the board, or nothing. */
	private static ItemStack take(ServerLevel level, BlockPos board, Item item) {
		for (BlockPos p : BlockPos.betweenClosed(board.offset(-FETCH_RANGE, -FETCH_RANGE, -FETCH_RANGE), board.offset(FETCH_RANGE, FETCH_RANGE, FETCH_RANGE))) {
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof Container box && !(be instanceof CafeBoard.Entity)) {
				for (int i = 0; i < box.getContainerSize(); i++) {
					ItemStack s = box.getItem(i);
					if (s.is(item)) {
						ItemStack one = s.split(1);
						box.setChanged();
						return one;
					}
				}
			}
		}
		return ItemStack.EMPTY;
	}

	/** One think on an errand; false once it's over. */
	private static boolean errand(MinecraftServer server, Errand e) {
		Villager a = find(server, e.apprentice());
		Customer c = CUSTOMERS.get(e.customer());
		Villager v = find(server, e.customer());
		ServerPlayer master = server.getPlayerList().getPlayer(e.master());
		if (a == null) {
			return false;
		}
		ServerLevel level = (ServerLevel)a.level();
		boolean over = c == null || v == null || master == null || c.state != State.WAITING || level.getGameTime() - e.since > ERRAND_TICKS;
		if (!over && a.distanceToSqr(v) <= HAND_OVER * HAND_OVER && level.getBlockEntity(c.board) instanceof CafeBoard.Entity board) {
			a.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			serve(level, board, c, v, e.dish(), master);
			return false;
		}
		if (over) {
			// (the dish goes back where it can: the master's pack, else the floor by the apprentice)
			a.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			ItemStack back = e.dish().copy();
			if (master == null || !master.getInventory().add(back)) {
				a.spawnAtLocation(level, back);
			}
			return false;
		}
		if (a.getNavigation().isDone() || clock % 20 == 0) {
			BehaviorUtils.setWalkAndLookTargetMemories(a, v, WALK, 1);
		}
		return true;
	}

	// ---- odds and ends -----------------------------------------------------------------------------------------------

	private static void sit(ServerLevel level, Villager v, Customer c) {
		if (v.isPassenger()) {
			return;
		}
		BlockState state = level.getBlockState(c.seat);
		double h = seatHeight(level, c.seat, state);
		if (h <= 0.0) {
			return;
		}
		Vec3 at = new Vec3(c.seat.getX() + 0.5, c.seat.getY() + h, c.seat.getZ() + 0.5);
		Cushion seat = EntityTypes.CUSHION.create(level, EntitySpawnReason.TRIGGERED);
		if (seat == null) {
			return;
		}
		seat.setColor(DyeColor.BROWN);
		seat.setPermanentlyInvulnerable(true);
		seat.addTag(TAG);
		float yaw = (float)(Math.toDegrees(Math.atan2(c.board.getZ() - c.seat.getZ(), c.board.getX() - c.seat.getX())) - 90.0);
		seat.snapTo(at, yaw, 0.0F);
		level.addFreshEntity(seat);
		CUSHIONS.add(seat);
		v.getNavigation().stop();
		if (!v.startRiding(seat)) {
			seat.discard();
		}
	}

	private static void stand(Villager v) {
		if (v.getVehicle() instanceof Cushion seat && seat.entityTags().contains(TAG)) {
			v.stopRiding();
			seat.discard();
		}
	}

	private static void look(ServerLevel level, Villager v, CafeBoard.Entity board) {
		ServerPlayer owner = owner(level, board);
		Vec3 at = owner != null && owner.distanceToSqr(v) < 16 * 16 ? owner.getEyePosition() : Vec3.atCenterOf(board.getBlockPos());
		v.getLookControl().setLookAt(at.x, at.y, at.z);
	}

	private static void walk(Villager v, BlockPos to) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, to, WALK, 1);
	}

	private static void say(ServerLevel level, Villager v, String kind, RandomSource random, Object... args) {
		Speech.say(level, v, Component.translatable("cafe.jaymoskingdoms." + kind + "." + random.nextInt(LINES), args),
			kind.equals("angry") || kind.equals("wrong") ? Speech.RED : Speech.WHITE, SPEECH_RANGE);
	}
}
