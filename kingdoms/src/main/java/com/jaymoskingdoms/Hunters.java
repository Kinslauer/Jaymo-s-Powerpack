package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hunting and gathering, by a camp's band and a town's folk. Andy (2026-11-19): "everyone should have to do this or some
 * way of getting food. so you will see them hunting", "when anyone goes hungry they cant just make the food come out of
 * nowhere", and "any of them can hunt on their own and gather and put their spoils into a communal chest. but rangers who
 * see this will hogtie and turn them in, just like players".
 * <p>
 * A camp eats what is in its larder ({@link CampLarder}), a town what is in its storehouse ({@link Storage}), and nothing
 * else. When a camp has less meat than mouths, or a town less food than it wants ({@link Meals#want}), one of them goes
 * out by day; now and then one goes on their own anyway. Any of the band, or any free grown townsfolk (not its ruler,
 * guards, cooks, visitors or anyone at work or at a meal), may go.
 * <ul>
 * <li><b>Hunting</b> real game ({@link #GAME}: deer, boar, sheep, pigs, cattle, goats, fowl, rabbits) out in the wilds
 * ({@link ForestRangers#wild}: no realm's law, no homestead, not on a lead, named or tamed): they creep up crouched, then
 * run it down when it bolts, striking as they reach it. A camp's hunter carries the body home and sets it down by the fire
 * for the cook to roast whole on the great spit ({@link Firesides}); a town's hunter cuts it up where it fell and carries
 * the cuts to the storehouse. Without Kitchen they gather up its meat where it fell.</li>
 * <li><b>Gathering</b>: ripe sweet berries picked off the bush (it grows again) and wild mushrooms, an armful, carried
 * home.</li>
 * <li>The spoils go into the communal chest: the camp's chests by the fire, the town's storehouse.</li>
 * <li>Game hunted out is gone: a camp that has killed everything round it goes hungry, and says so. And a hunter who kills
 * too much in a day is a poacher, and the Green Huntsman's rangers who see it hogtie them and turn them in, as they do
 * players ({@link ForestRangers}).</li>
 * </ul>
 * Only while a player is near enough to see it ({@link #WATCHED}).
 */
final class Hunters {
	/** Outings are looked at this often (ticks); a new one is thought about this often (ticks). */
	static final int EVERY = 5, CONSIDER = 200;
	/** A camp or town with a player this near sends them out (blocks); a camp's band is everyone of it this near its centre (blocks). */
	static final double WATCHED = 128.0, BAND = 32.0, TOWNSFOLK = 80.0;
	/** Game is sought this far from a camp, from a town's seat (blocks: out past its land); things to gather this far (blocks). */
	static final double RANGE = 48.0, TOWN_RANGE = 128.0, FORAGE_RANGE = 40.0;
	/** By day only: from dawn to a while before supper (time of day). */
	static final long OUT_TO = CampSuppers.FROM - 1000L;
	/** An outing is given up after this long (ticks); after one, that place waits this long before the next (ticks). */
	static final int OUT_TICKS = 2400, GAP = 1200;
	/** The chance each think that one goes out on their own, with no need. */
	static final float OWN_CHANCE = 0.08F;
	/** Creeping on it this far off, then running (blocks); striking this close (blocks), once a second at most (ticks). */
	static final double CREEP = 10.0, STRIKE = 2.2;
	static final int STRIKE_EVERY = 20;
	/** A blow from a hunter with no attack of their own (a villager's hands and knife). */
	static final float BARE_BLOW = 4.0F;
	/** Walking paces: creeping, running it down, carrying it home. */
	static final float CREEP_PACE = 0.45F, RUN_PACE = 0.75F, HOME_PACE = 0.5F;
	/** After the kill, its body (or its meat) is looked for this long (ticks) this near where it fell (blocks); taken up this close (blocks). */
	static final int BODY_WAIT = 80;
	static final double BODY_NEAR = 4.0, TAKE = 1.8;
	/** A town hunter cuts the body up with this many knife strokes, one a second. */
	static final int CUT_STROKES = 6;
	/** Gathering: an armful (items); picked this close (blocks). */
	static final int ARMFUL = 6;
	static final double PICK = 2.0;
	/** Home: this near the fire or the storehouse (blocks); a body set down this far off the fire (blocks). */
	static final double HOME = 3.0, SET_DOWN = 2.0;

	/** What they hunt (data/jaymoskingdoms/tags/entity_type/camp_game.json). */
	static final TagKey<EntityType<?>> GAME = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("camp_game"));

	/** Where an outing comes home to: a camp's fire, or a town's storehouse ({@code town} set). */
	private record Home(String key, BlockPos center, BlockPos drop, @Nullable Faction town) {
	}

	private enum Stage { STALK, FELLED, CUT, GATHER, HOME }

	private static final class Outing {
		final Home home;
		final Mob who;
		@Nullable LivingEntity quarry;
		Stage stage;
		final long since;
		long stageAt;
		long struck;
		int strokes;
		Vec3 fell = Vec3.ZERO;
		@Nullable Entity body;
		@Nullable BlockPos spot;
		final List<ItemStack> spoils = new ArrayList<>();

		Outing(Home home, Mob who, @Nullable LivingEntity quarry, long since) {
			this.home = home;
			this.who = who;
			this.quarry = quarry;
			this.stage = quarry != null ? Stage.STALK : Stage.GATHER;
			this.since = since;
			this.stageAt = since;
		}
	}

	private static final Map<String, Outing> OUT = new HashMap<>();
	private static final Map<Mob, Outing> BY_WHO = new IdentityHashMap<>();
	/** When each camp or town may next send someone out (game time). */
	private static final Map<String, Long> NEXT = new HashMap<>();

	private Hunters() {
	}

	static void register() {
		for (String kind : new String[] {"hunt.out", "hunt.kill", "hunt.back", "hunt.lost", "hunt.none", "forage.out", "forage.back"}) {
			Callouts.LINES.put(kind, 3);
		}
		ServerTickEvents.END_SERVER_TICK.register(Hunters::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OUT.clear();
			BY_WHO.clear();
			NEXT.clear();
		});
	}

	/** {@code m} is out hunting or gathering. */
	static boolean busy(Mob m) {
		return BY_WHO.containsKey(m);
	}

	/** {@code m} has been caught (a ranger's hogtie): their outing is over where they are. */
	static void caught(ServerLevel level, Mob m) {
		Outing o = BY_WHO.get(m);
		if (o != null) {
			end(level, o, level.getGameTime(), false);
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Outing o : new ArrayList<>(OUT.values())) {
			step(level, o, now);
		}
		if (server.getTickCount() % CONSIDER != 0 || level.getOverworldClockTime() % 24000L >= OUT_TO) {
			return;
		}
		Map<String, Camps.CampData> camps = Camps.all(server);
		for (Map.Entry<String, Camps.CampData> e : camps.entrySet()) {
			BlockPos center = e.getValue().center();
			if (!ready(level, "c:" + e.getKey(), center, now)) {
				continue;
			}
			BlockPos lit = Firesides.findFire(level, center);
			consider(level, new Home("c:" + e.getKey(), center, lit != null ? lit : center, null), "f:" + e.getKey(), now);
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || camps.containsKey(Integer.toString(f.id())) || !ready(level, "t:" + f.id(), f.seat(), now)
				|| !Storage.loaded(level, f)) {
				continue;
			}
			consider(level, new Home("t:" + f.id(), f.seat(), f.holdings().depot().orElse(f.seat()), f), null, now);
		}
	}

	private static boolean ready(ServerLevel level, String key, BlockPos at, long now) {
		return !OUT.containsKey(key) && NEXT.getOrDefault(key, Long.MIN_VALUE) <= now && Loaded.made(level, at) && watched(level, at);
	}

	private static boolean watched(ServerLevel level, BlockPos at) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(at, WATCHED)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code home} needs food, or someone goes anyway; and if so who goes for what. {@code side}: a camp's band. */
	private static void consider(ServerLevel level, Home home, @Nullable String side, long now) {
		RandomSource random = level.getRandom();
		List<Mob> folk = home.town == null
			? level.getEntitiesOfClass(Mob.class, new AABB(home.center).inflate(BAND), m -> m.isAlive() && !m.isBaby() && Temperaments.humanoid(m)
				&& Comrades.side(m).equals(side))
			: new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(home.center).inflate(TOWNSFOLK), v -> v.isAlive() && !v.isBaby()));
		if (folk.isEmpty()) {
			return;
		}
		boolean short_ = home.town == null
			? CampLarder.count(level, home.drop, stack -> stack.is(KitchenSpits.ROASTABLE)) < folk.size()
				&& KitchenCompat.spitCarcassNear(level, Vec3.atCenterOf(home.drop), Firesides.CARCASS_NEAR) == null
			: Storage.food(level, home.town) < Meals.want(home.town);
		if (!short_ && random.nextFloat() >= OWN_CHANCE) {
			return;
		}
		NEXT.put(home.key, now + GAP);
		Mob who = null;
		for (Mob m : folk) {
			if (free(m, home)) {
				who = m;
				break;
			}
		}
		if (who == null) {
			return;
		}
		// hunting when they're short (and now and then besides), else gathering
		LivingEntity quarry = short_ || random.nextBoolean() ? quarry(level, home) : null;
		BlockPos spot = quarry == null ? forage(level, home.center, null) : null;
		if (quarry == null && spot == null) {
			if (short_ && home.town == null) {
				// hunted out round here
				Callouts.say(who, "hunt.none", null, false);
			}
			return;
		}
		Outing o = new Outing(home, who, quarry, now);
		o.spot = spot;
		OUT.put(home.key, o);
		BY_WHO.put(who, o);
		Comrades.stand(who);
		Callouts.say(who, quarry != null ? "hunt.out" : "forage.out", null, false);
		walk(who, quarry != null ? quarry.position() : Vec3.atBottomCenterOf(spot), quarry != null ? CREEP_PACE : HOME_PACE);
	}

	/** Free to go: on their feet, not fighting, wanted elsewhere or bound; a camp's leader stays; a town's ruler, guards, cooks and visitors, and anyone at work or a meal, stay. */
	private static boolean free(Mob m, Home home) {
		if (Downed.isDowned(m) || Comrades.target(m) != null || Comrades.busy(m) || Captives.bound(m) || m.isNoAi() || com.jaymoskingdoms.forms.Lasso.hogtied(m)) {
			return false;
		}
		if (home.town == null) {
			return !Firesides.sitting(m) && !CampSuppers.eating(m) && !BandLeaders.isLeader(m);
		}
		if (!(m instanceof Villager v) || !Routines.idle(v) || Rulers.is(v) || Visitors.is(v) || Meals.eating(v) || Meals.cook(v)
			|| McaCompat.following(v) != null || v.hasAttached(Emigrants.MOVE) || v instanceof Worker w && w.role() >= 0) {
			return false;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		return !job.equals("guard") && !job.equals("archer");
	}

	/** The nearest game out in the wilds round {@code home} that nobody else is after. */
	private static @Nullable LivingEntity quarry(ServerLevel level, Home home) {
		double range = home.town == null ? RANGE : TOWN_RANGE;
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Animal a : level.getEntitiesOfClass(Animal.class, new AABB(home.center).inflate(range, 16.0, range),
			a -> a.isAlive() && !a.isBaby() && a.getType().builtInRegistryHolder().is(GAME))) {
			double d = a.distanceToSqr(Vec3.atCenterOf(home.center));
			if (d >= bestD || !ForestRangers.wild(a) || hunted(a)) {
				continue;
			}
			best = a;
			bestD = d;
		}
		return best;
	}

	private static boolean hunted(Entity a) {
		for (Outing o : OUT.values()) {
			if (o.quarry == a) {
				return true;
			}
		}
		return false;
	}

	/** Something to gather round {@code at}: a ripe sweet berry bush or a wild mushroom, the nearest (not {@code but}, nor on a homestead); null if none. */
	private static @Nullable BlockPos forage(ServerLevel level, BlockPos at, @Nullable BlockPos but) {
		int r = (int) FORAGE_RANGE;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -6, -r), at.offset(r, 6, r))) {
			if (p.equals(but) || !gatherable(level.getBlockState(p))) {
				continue;
			}
			double d = p.distSqr(at);
			// (never off someone's homestead: that's their garden)
			if (d < bestD && Homesteads.ownerAt(level, p) == null) {
				bestD = d;
				best = p.immutable();
			}
		}
		return best;
	}

	private static boolean gatherable(BlockState state) {
		return state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) >= 2
			|| state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM);
	}

	private static void step(ServerLevel level, Outing o, long now) {
		Mob who = o.who;
		if (!who.isAlive() || who.isRemoved() || Downed.isDowned(who) || Comrades.target(who) != null || Captives.bound(who)
			|| com.jaymoskingdoms.forms.Lasso.hogtied(who) || now - o.since > OUT_TICKS) {
			end(level, o, now, false);
			return;
		}
		switch (o.stage) {
			case STALK -> stalk(level, o, now);
			case FELLED -> felled(level, o, now);
			case CUT -> cut(level, o, now);
			case GATHER -> gather(level, o, now);
			case HOME -> {
				if (o.body != null && (o.body.isRemoved() || KitchenCompat.carrier(o.body) != who)) {
					end(level, o, now, false);
					return;
				}
				if (who.position().distanceTo(Vec3.atBottomCenterOf(o.home.drop)) > HOME) {
					if ((now - o.stageAt) % 20 < EVERY) {
						walk(who, Vec3.atBottomCenterOf(o.home.drop), HOME_PACE);
					}
					return;
				}
				who.getNavigation().stop();
				who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				Callouts.say(who, o.quarry != null ? "hunt.back" : "forage.back", null, false);
				end(level, o, now, true);
			}
		}
	}

	private static void stalk(ServerLevel level, Outing o, long now) {
		Mob who = o.who;
		LivingEntity q = o.quarry;
		if (q == null || q.isRemoved() || !q.isAlive()) {
			if (q != null && (q.isDeadOrDying() || q.getHealth() <= 0.0F)) {
				o.fell = q.position();
				to(o, Stage.FELLED, now);
				who.setShiftKeyDown(false);
				Callouts.say(who, "hunt.kill", null, false);
				return;
			}
			end(level, o, now, false);
			return;
		}
		double d = who.distanceTo(q);
		who.getLookControl().setLookAt(q, 30.0F, 30.0F);
		if (d <= STRIKE) {
			who.getNavigation().stop();
			if (now - o.struck >= STRIKE_EVERY) {
				o.struck = now;
				strike(level, who, q);
			}
			return;
		}
		// creeping up on it (bent low), till it's near or has bolted; then running it down
		boolean running = d < CREEP || q.getLastHurtByMob() == who;
		who.setShiftKeyDown(!running);
		if ((now - o.stageAt) % 10 < EVERY) {
			walk(who, q.position(), running ? RUN_PACE : CREEP_PACE);
		}
	}

	/** The beast is down: its body taken up (a camp's) or cut up (a town's); without one, its meat gathered where it fell. */
	private static void felled(ServerLevel level, Outing o, long now) {
		Mob who = o.who;
		if (o.body == null) {
			Entity body = KitchenCompat.spitCarcassNear(level, o.fell, BODY_NEAR);
			if (body != null) {
				o.body = body;
			} else if (now - o.stageAt < BODY_WAIT && KitchenCompat.makesCorpses()) {
				walk(who, o.fell, HOME_PACE);
				return;
			}
		}
		Entity body = o.body;
		if (body != null) {
			if (body.isRemoved() || KitchenCompat.carrier(body) != null && KitchenCompat.carrier(body) != who) {
				end(level, o, now, false);
				return;
			}
			if (who.distanceTo(body) > TAKE) {
				if ((now - o.stageAt) % 10 < EVERY) {
					walk(who, body.position(), HOME_PACE);
				}
				return;
			}
			if (o.home.town != null) {
				o.strokes = 0;
				to(o, Stage.CUT, now);
				return;
			}
			if (!KitchenCompat.carry(body, who)) {
				end(level, o, now, false);
				return;
			}
		} else {
			if (pickUp(level, o) > 0) {
				return;
			}
			if (o.spoils.isEmpty()) {
				if (now - o.stageAt < BODY_WAIT * 2) {
					walk(who, o.fell, HOME_PACE);
					return;
				}
				// nothing came of it
				end(level, o, now, false);
				return;
			}
		}
		goHome(o, now);
	}

	/** A town's hunter cuts the body up where it fell, a knife stroke a second, and takes up the cuts. */
	private static void cut(ServerLevel level, Outing o, long now) {
		Mob who = o.who;
		Entity body = o.body;
		if (body == null || body.isRemoved() || KitchenCompat.carrier(body) != null) {
			end(level, o, now, false);
			return;
		}
		who.getNavigation().stop();
		who.getLookControl().setLookAt(body.getX(), body.getY() + 0.3, body.getZ());
		if ((now - o.stageAt) % 20 >= EVERY) {
			return;
		}
		who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, body.blockPosition(), o.strokes % 2 == 0 ? SoundEvents.SHEEP_SHEAR : SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL,
			0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		level.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY() + 0.3, body.getZ(), 3, 0.3, 0.2, 0.3, 0.05);
		if (++o.strokes < CUT_STROKES) {
			return;
		}
		List<ItemStack> cuts = KitchenCompat.butcherWhereLies(body);
		if (cuts.isEmpty()) {
			end(level, o, now, false);
			return;
		}
		o.body = null;
		for (ItemStack cutPiece : cuts) {
			take(o, cutPiece);
		}
		goHome(o, now);
	}

	/** Picking: walk to the bush or the mushroom, take what's ripe, on to the next till their arms are full or there's no more. */
	private static void gather(ServerLevel level, Outing o, long now) {
		Mob who = o.who;
		BlockPos spot = o.spot;
		if (spot == null || !gatherable(level.getBlockState(spot))) {
			spot = o.spot = o.spoils.size() < ARMFUL ? forage(level, o.home.center, null) : null;
			if (spot == null) {
				if (o.spoils.isEmpty()) {
					end(level, o, now, false);
				} else {
					goHome(o, now);
				}
				return;
			}
		}
		if (who.position().distanceTo(Vec3.atBottomCenterOf(spot)) > PICK) {
			if ((now - o.stageAt) % 10 < EVERY) {
				walk(who, Vec3.atBottomCenterOf(spot), HOME_PACE);
			}
			return;
		}
		who.getNavigation().stop();
		who.getLookControl().setLookAt(spot.getX() + 0.5, spot.getY() + 0.3, spot.getZ() + 0.5);
		who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		BlockState state = level.getBlockState(spot);
		RandomSource random = level.getRandom();
		if (state.is(Blocks.SWEET_BERRY_BUSH)) {
			// as a player picks one: the berries off it, the bush back to growing
			int age = state.getValue(SweetBerryBushBlock.AGE);
			take(o, new ItemStack(Items.SWEET_BERRIES, 1 + random.nextInt(2) + (age == 3 ? 1 : 0)));
			level.setBlock(spot, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
			level.playSound(null, spot, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + random.nextFloat() * 0.4F);
		} else {
			take(o, new ItemStack(state.getBlock().asItem()));
			level.destroyBlock(spot, false, who);
		}
		o.spot = null;
		o.stageAt = now;
	}

	/** A blow at the game: with their own attack (and weapon) if they have one, else with their hands and a knife. */
	private static void strike(ServerLevel level, Mob who, LivingEntity q) {
		who.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (who.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
			who.doHurtTarget(level, q);
		} else {
			q.hurtServer(level, level.damageSources().mobAttack(who), BARE_BLOW);
		}
	}

	/** Without a body: the meat lying where it fell, taken up as they reach it, walking on to the next. How much is still lying there. */
	private static int pickUp(ServerLevel level, Outing o) {
		int left = 0;
		ItemEntity next = null;
		for (ItemEntity i : level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(o.fell)).inflate(BODY_NEAR),
			i -> i.isAlive() && CampLarder.food(i.getItem()))) {
			if (o.who.distanceTo(i) <= TAKE) {
				take(o, i.getItem().copy());
				i.discard();
				level.playSound(null, o.who.getX(), o.who.getY(), o.who.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 0.9F);
			} else {
				left++;
				if (next == null || o.who.distanceToSqr(i) < o.who.distanceToSqr(next)) {
					next = i;
				}
			}
		}
		if (next != null) {
			walk(o.who, next.position(), HOME_PACE);
		}
		return left;
	}

	/** Into their arms (the first thing shown in their off hand). */
	private static void take(Outing o, ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		o.spoils.add(stack);
		if (o.who.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
			o.who.setItemSlot(EquipmentSlot.OFFHAND, stack.copyWithCount(1));
		}
	}

	private static void goHome(Outing o, long now) {
		to(o, Stage.HOME, now);
		walk(o.who, Vec3.atBottomCenterOf(o.home.drop), HOME_PACE);
	}

	private static void to(Outing o, Stage stage, long now) {
		o.stage = stage;
		o.stageAt = now;
	}

	/** The outing's over: home with it ({@code home}: what they carried put in the communal chest), or given up where they are. */
	private static void end(ServerLevel level, Outing o, long now, boolean home) {
		OUT.remove(o.home.key);
		BY_WHO.remove(o.who);
		NEXT.put(o.home.key, now + GAP);
		Mob who = o.who;
		who.setShiftKeyDown(false);
		who.getNavigation().stop();
		if (o.body != null && KitchenCompat.carrier(o.body) == who) {
			KitchenCompat.throwAt(o.body, home ? setDown(o.home.drop, who) : who.position());
		}
		if (!o.spoils.isEmpty()) {
			if (who.getItemBySlot(EquipmentSlot.OFFHAND).is(o.spoils.get(0).getItem())) {
				who.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			}
			for (ItemStack stack : o.spoils) {
				if (!home) {
					if (who.isAlive()) {
						who.spawnAtLocation(level, stack);
					}
				} else if (o.home.town != null) {
					ItemStack left = Storage.put(level, o.home.town, stack);
					if (!left.isEmpty()) {
						who.spawnAtLocation(level, left);
					}
				} else {
					CampLarder.put(level, o.home.drop, stack);
				}
			}
			o.spoils.clear();
		}
		if (!home && who.isAlive() && o.stage == Stage.STALK) {
			Callouts.say(who, "hunt.lost", null, false);
		}
	}

	/** Where a body is set down: on their side of the fire, a little off it. */
	private static Vec3 setDown(BlockPos fire, Mob who) {
		Vec3 c = Vec3.atBottomCenterOf(fire);
		Vec3 d = who.position().subtract(c).multiply(1.0, 0.0, 1.0);
		if (d.lengthSqr() < 1.0E-4) {
			d = new Vec3(1.0, 0.0, 0.0);
		}
		return c.add(d.normalize().scale(SET_DOWN));
	}

	private static void walk(Mob m, Vec3 to, float pace) {
		if (m instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, BlockPos.containing(to), pace, 0);
		} else {
			m.getNavigation().moveTo(to.x, to.y, to.z, pace / 0.5F);
		}
	}
}
