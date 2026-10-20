package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Crime in a faction's land, our own take on the idea (nothing from the Thief mod): rummaging in or taking from its
 * storage without the right ({@link Storage#mayTake}), breaking its bell, its storage, chests, beds, job blocks or crops,
 * killing its livestock, villagers, golems or folk. Each crime is light, medium or heavy, and only counts if one of the
 * faction's people sees it: its folk, villagers or golems, in line of sight within {@link #WITNESS_RANGE} (less if you
 * sneak), looking your way or close enough to hear. Seen: your reputation with it drops by the crime's weight, the
 * witness cries out, a medium crime has its guards (and golems) after you for a while, a heavy one makes you an outlaw
 * there for longer. Taking unseen is noticed later, and costs you only a little. Putting things into its storage is a
 * gift: it raises your reputation (and a member's standing). Barbarian camps keep no law.
 *
 * <p>(0.5.20) Lighting a fire there (flint and steel or a fire charge) is arson, punished like a theft. Seen, it also
 * turns the townsfolk around on you: every grown villager and civilian of the faction within {@link #RIOT_RANGE} runs
 * at you and lays into you with their fists until you hit one of them back, when they scatter (the guards keep on).
 *
 * <p>(0.5.37) A kingdom that doesn't believe in magic ({@link Sorcery#believesInMagic}) prosecutes it: reading a spell
 * scroll inside its walls is a crime like arson, for its own people too (only its ruler may).
 *
 * <p>(0.5.46) Blocks a player placed themselves ({@link Placed}) are theirs: breaking them or opening them is no crime.
 *
 * <p>(0.5.50) Picking a villager's or civilian's pocket ({@link Pickpocket}) is a theft like taking from its stores.
 *
 * <p>(0.5.52) A theft nobody saw is Stealth experience ({@link Skills}); Soft Step hides a sneaking thief from farther
 * off, and a Ghost's unseen thefts are never missed.
 */
public final class Theft {
	/** How far a witness sees a crime (sneaking: this share of it); closer than {@link #HEAR_RANGE} they notice whichever way they face. */
	static final double WITNESS_RANGE = 16.0, SNEAK_FACTOR = 0.5, HEAR_RANGE = 4.0;
	/** How wide a witness's gaze is (the cosine of half its angle). */
	static final double VIEW_COS = 0.2;
	/** Reputation a seen crime costs, by weight. */
	static final int REP_LIGHT = -10, REP_MEDIUM = -35, REP_HEAVY = -90;
	/** Taking this many items (or more) at once is a heavy theft. */
	static final int HEAVY_TAKE_ITEMS = 32;
	/** A medium crime sets its guards on you this long; a heavy one makes you an outlaw this long (ticks). */
	static final long HUNT_TICKS = 2400L, OUTLAW_TICKS = 48000L;
	/** Guards (and golems) this close to a wanted player go after them; checked this often (ticks). */
	static final double PURSUIT_RANGE = 32.0;
	static final int PURSUIT_TICKS = 20;
	/** Unseen theft is noticed this much later (ticks), and costs this much reputation. */
	static final long NOTICE_TICKS = 6000L;
	/** The chance a theft nobody saw is noticed at all (later); otherwise it goes unnoticed. */
	static final float NOTICE_CHANCE = 0.3F;
	static final int REP_UNSEEN = -3;
	/** A gift to storage: a point of reputation (and standing) per this many items, at most this much a day per faction. */
	static final int GIFT_ITEMS_PER_POINT = 8, GIFT_DAILY_CAP = 25;
	/** Witnesses' cries are heard this far. */
	static final double SHOUT_RANGE = 24.0;
	/** Lines a witness may cry, per kind of crime. */
	static final int SHOUT_VARIANTS = 2;
	/** Arson: townsfolk this close join in; they give up after this long (ticks) or if you get this far away. */
	static final double RIOT_RANGE = 24.0, RIOT_GIVE_UP = 48.0;
	static final long RIOT_TICKS = 1200L;
	/** A rioter hits you from this close, this hard, at most this often (ticks); how fast they come (villagers, folk). */
	static final double RIOT_REACH = 2.0, RIOT_FOLK_SPEED = 1.15;
	static final float RIOT_DAMAGE = 1.0F, RIOT_VILLAGER_SPEED = 0.75F;
	static final int RIOT_EVERY = 5, RIOT_PUNCH_TICKS = 30;
	/** However many there are, the crowd lands at most one blow this often (ticks), so it hurts without felling you at once. */
	static final int RIOT_CROWD_TICKS = 10;

	/** How bad a crime is. */
	enum Weight {
		LIGHT(REP_LIGHT), MEDIUM(REP_MEDIUM), HEAVY(REP_HEAVY);

		final int rep;

		Weight(int rep) {
			this.rep = rep;
		}
	}

	/** The crimes, each with its weight (taking can be made heavy by how much) and the kind of cry it draws. */
	enum Crime {
		RUMMAGE(Weight.LIGHT, "rummage"), TAKE(Weight.MEDIUM, "theft"), CROP(Weight.LIGHT, "vandal"), BED(Weight.MEDIUM, "vandal"),
		JOB_BLOCK(Weight.MEDIUM, "vandal"), CHEST(Weight.MEDIUM, "theft"), STOREHOUSE(Weight.HEAVY, "theft"), BELL(Weight.HEAVY, "vandal"),
		ARSON(Weight.MEDIUM, "arson"), PICKPOCKET(Weight.MEDIUM, "pickpocket"), SORCERY(Weight.MEDIUM, "sorcery"), LIVESTOCK(Weight.LIGHT, "livestock"), FOLK(Weight.MEDIUM, "murder"), VILLAGER(Weight.HEAVY, "murder"), GOLEM(Weight.HEAVY, "murder"),
		/** (0.5.47) a caravan horse's saddlebags rifled. */
		SADDLEBAG(Weight.MEDIUM, "theft"),
		/** (0.6.9) lingering in someone's house ({@link Trespass}). */
		TRESPASS(Weight.LIGHT, "trespass"),
		/** (0.7.0) a vampire player seen feeding ({@link VampirePlayers}). */
		BLOODLETTING(Weight.HEAVY, "bloodletting");

		final Weight weight;
		final String cry;

		Crime(Weight weight, String cry) {
			this.weight = weight;
			this.cry = cry;
		}
	}

	/** Per player: faction id → game time their guards stop hunting them (outlaws have the longest). Saved. */
	static final AttachmentType<Map<String, Long>> WANTED = AttachmentRegistry.create(JaymosKingdoms.id("wanted"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

	/**
	 * A look into a faction's storage: what the container and the player's own pack held when it was opened (only what
	 * moved between the two counts, so a builder loading from the same chest isn't blamed on them), and whether the
	 * container's screen has been seen open.
	 */
	private static final class Opened {
		final int faction;
		final BlockPos pos;
		final Map<Item, Integer> before, pack;
		final long at;
		boolean shown;

		Opened(int faction, BlockPos pos, Map<Item, Integer> before, Map<Item, Integer> pack, long at) {
			this.faction = faction;
			this.pos = pos;
			this.before = before;
			this.pack = pack;
			this.at = at;
		}
	}

	/** A theft nobody saw, noticed later. */
	private record Notice(UUID player, int faction, long at) {
	}

	private static final Map<UUID, Opened> OPEN = new HashMap<>();
	private static final List<Notice> NOTICES = new ArrayList<>();
	/** Player + faction → gifts counted today (reputation points), and on which day. */
	private static final Map<String, int[]> GIFTS = new HashMap<>();
	/** A fire a player may have just lit (flint and steel or a fire charge), checked a tick later. */
	private record Kindled(UUID player, BlockPos pos, long at) {
	}

	/** Townsfolk set on an arsonist: who, until when, and when each may hit next. */
	private static final class Riot {
		final int faction;
		final long until;
		long nextBlow;
		final Map<UUID, Long> rioters = new HashMap<>();

		Riot(int faction, long until) {
			this.faction = faction;
			this.until = until;
		}
	}

	private static final List<Kindled> KINDLED = new ArrayList<>();
	/** Arsonist → the crowd after them. */
	private static final Map<UUID, Riot> RIOTS = new HashMap<>();

	private Theft() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && !(sp.isSecondaryUseActive() && !sp.getItemInHand(hand).isEmpty())) {
				opened(sp, server, hit.getBlockPos());
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && server == Factions.level(server.getServer())
				&& (player.getItemInHand(hand).is(Items.FLINT_AND_STEEL) || player.getItemInHand(hand).is(Items.FIRE_CHARGE))) {
				KINDLED.add(new Kindled(sp.getUUID(), hit.getBlockPos().relative(hit.getDirection()).immutable(), server.getGameTime()));
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				broke(sp, server, pos, state, entity);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && entity.level() instanceof ServerLevel level) {
				killed(killer, level, entity);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player) {
				hitBack(player, entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Theft::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OPEN.clear();
			NOTICES.clear();
			GIFTS.clear();
			KINDLED.clear();
			RIOTS.clear();
		});
	}

	// ---- queries for other systems ------------------------------------------------------------------------------

	/** {@code player} is hunted by {@code faction}'s guards right now (a medium crime or worse, seen). */
	public static boolean wanted(ServerPlayer player, int faction) {
		return until(player, faction) > player.level().getGameTime();
	}

	/** {@code player} is an outlaw in {@code faction} (a heavy crime, seen, not yet forgotten). */
	public static boolean outlaw(ServerPlayer player, int faction) {
		long until = until(player, faction);
		return until > 0L && until - player.level().getGameTime() > HUNT_TICKS;
	}

	/** (0.7.62) When {@code faction}'s guards stop hunting {@code player} (0: they aren't): a masked stranger's is the costume's ({@link Identities}). */
	private static long until(ServerPlayer player, int faction) {
		String persona = Identities.persona(player, faction);
		if (persona != null) {
			return Identities.wantedUntil(player.level().getServer(), persona, faction);
		}
		Long until = player.getAttachedOrElse(WANTED, Map.of()).get(Integer.toString(faction));
		return until == null ? 0L : until;
	}

	/** (0.7.62) {@code faction} hunts {@code player} until at least {@code until}: them, or the costume they're masked in there. */
	private static void markWanted(ServerPlayer player, int faction, long until) {
		String persona = Identities.persona(player, faction);
		if (persona != null) {
			Identities.markWanted(player.level().getServer(), persona, faction, until);
			return;
		}
		Map<String, Long> wanted = new HashMap<>(player.getAttachedOrElse(WANTED, Map.of()));
		wanted.merge(Integer.toString(faction), until, Math::max);
		player.setAttached(WANTED, Map.copyOf(wanted));
	}

	/** {@code who} is one of a crowd set on an arsonist (they leave the fire to others). */
	public static boolean rioting(LivingEntity who) {
		for (Riot riot : RIOTS.values()) {
			if (riot.rioters.containsKey(who.getUUID())) {
				return true;
			}
		}
		return false;
	}

	// ---- what counts --------------------------------------------------------------------------------------------

	/** Whose law holds at {@code pos} (null where none does, or in a camp). */
	static @Nullable Faction lawAt(ServerLevel level, BlockPos pos) {
		Faction f = Factions.around(level.getServer(), level, pos, Villages.VILLAGE_RADIUS);
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	/** {@code player} answers to no one here: creative, or of rank enough in the faction to take from its stores. */
	static boolean exempt(ServerPlayer player, Faction f) {
		// (0.7.62: not in a mask they don't see through)
		return player.isSpectator() || !Identities.hidden(player, f.id()) && Storage.mayTake(player, f);
	}

	private static void opened(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Faction f = Factions.storageOwner(level.getServer(), pos);
		if (f == null || f.fallen() || level != Factions.level(level.getServer()) || !(level.getBlockEntity(pos) instanceof Container c)
			|| Placed.byPlayer(player, level, pos, level.getBlockState(pos))) {
			// (0.5.46) a chest you placed yourself is yours to open
			return;
		}
		OPEN.put(player.getUUID(), new Opened(f.id(), pos.immutable(), counts(c), counts(player.getInventory()), level.getGameTime()));
	}

	private static Map<Item, Integer> counts(Container c) {
		Map<Item, Integer> out = new HashMap<>();
		for (int slot = 0; slot < c.getContainerSize(); slot++) {
			ItemStack stack = c.getItem(slot);
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** The container's closed: what was taken is a theft (if they had no right), what was put in a gift. */
	private static void closed(ServerPlayer player, ServerLevel level, Opened open) {
		Faction f = Factions.get(level.getServer(), open.faction);
		if (f == null || !(level.getBlockEntity(open.pos) instanceof Container c)) {
			return;
		}
		Map<Item, Integer> after = counts(c);
		Map<Item, Integer> pack = counts(player.getInventory());
		int taken = 0, given = 0;
		for (var e : open.before.entrySet()) {
			int out = Math.max(0, e.getValue() - after.getOrDefault(e.getKey(), 0));
			int gained = Math.max(0, pack.getOrDefault(e.getKey(), 0) - open.pack.getOrDefault(e.getKey(), 0));
			taken += Math.min(out, gained);
		}
		for (var e : after.entrySet()) {
			int in = Math.max(0, e.getValue() - open.before.getOrDefault(e.getKey(), 0));
			int lost = Math.max(0, open.pack.getOrDefault(e.getKey(), 0) - pack.getOrDefault(e.getKey(), 0));
			given += Math.min(in, lost);
		}
		if (taken > given && Wars.plunder(player, level, f, taken - given)) {
			// (at war: plunder, not theft)
			return;
		}
		if (given > taken) {
			gift(player, level, f, given - taken);
		} else if (!exempt(player, f)) {
			if (taken > 0) {
				commit(player, level, f, Crime.TAKE, open.pos, taken >= HEAVY_TAKE_ITEMS ? Weight.HEAVY : null, true);
			} else {
				commit(player, level, f, Crime.RUMMAGE, open.pos, null, false);
			}
		}
	}

	/** Items given to a faction's storage: reputation (and a member's standing), a little at a time. */
	private static void gift(ServerPlayer player, ServerLevel level, Faction f, int items) {
		int points = items / GIFT_ITEMS_PER_POINT;
		if (points <= 0) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		int[] today = GIFTS.computeIfAbsent(player.getUUID() + "|" + f.id(), k -> new int[] {(int)day, 0});
		if (today[0] != (int)day) {
			today[0] = (int)day;
			today[1] = 0;
		}
		points = Math.min(points, GIFT_DAILY_CAP - today[1]);
		if (points <= 0) {
			return;
		}
		today[1] += points;
		Reputation.change(player, f, points);
		Faction.Member member = f.member(player.getUUID());
		if (member != null) {
			KingdomEvents.standing(level.getServer(), player, f, member.standing() + points);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.gift", f.name()).withStyle(ChatFormatting.GREEN));
	}

	private static void broke(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity entity) {
		boolean own = Placed.byPlayer(player, level, pos, state);
		Placed.forget(level, pos);
		if (own) {
			// (0.5.46) breaking a block you placed yourself is no crime
			return;
		}
		Faction owner = Factions.storageOwner(level.getServer(), pos);
		Faction f = owner != null ? owner : lawAt(level, pos);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN || exempt(player, f)) {
			return;
		}
		Crime crime;
		if (owner != null) {
			crime = Crime.STOREHOUSE;
		} else if (state.is(Blocks.BELL)) {
			crime = Crime.BELL;
		} else if (state.is(BlockTags.BEDS)) {
			crime = Crime.BED;
		} else if (entity instanceof Container) {
			crime = Crime.CHEST;
		} else if (PoiTypes.forState(state).map(h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false)) {
			crime = Crime.JOB_BLOCK;
		} else if (state.is(BlockTags.CROPS)) {
			crime = Crime.CROP;
		} else {
			return;
		}
		commit(player, level, f, crime, pos, null, false);
	}

	/** A fire {@code player} lit at {@code pos} (it caught): arson, where a faction's law holds (a hearth doesn't count). */
	private static void kindled(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos).is(BlockTags.FIRE) || level.getBlockState(pos.below()).is(BlockTags.INFINIBURN_OVERWORLD)
			|| player.isCreative()) {
			return;
		}
		Faction f = lawAt(level, pos);
		if (f == null || exempt(player, f)) {
			return;
		}
		commit(player, level, f, Crime.ARSON, pos, null, false);
	}

	/**
	 * {@code player} worked magic at {@code pos} (read a spell scroll): a crime where a kingdom that shuns magic holds the
	 * law, whoever they are, short of its ruler.
	 */
	public static void sorcery(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Faction f = lawAt(level, pos);
		if (f == null || f.type() != FactionType.KINGDOM || Sorcery.believesInMagic(f) || player.isCreative() || player.isSpectator()
			|| f.leader().filter(player.getUUID()::equals).isPresent()) {
			return;
		}
		commit(player, level, f, Crime.SORCERY, pos, null, false);
	}

	private static void killed(ServerPlayer killer, ServerLevel level, LivingEntity victim) {
		Faction f;
		Crime crime;
		if (victim instanceof Folk folk) {
			f = Factions.get(level.getServer(), folk.factionId());
			crime = Crime.FOLK;
		} else if (victim instanceof Villager) {
			f = lawAt(level, victim.blockPosition());
			crime = Crime.VILLAGER;
		} else if (victim instanceof IronGolem golem && !golem.isPlayerCreated()) {
			f = lawAt(level, victim.blockPosition());
			crime = Crime.GOLEM;
		} else if (victim instanceof Animal) {
			f = Factions.at(level.getServer(), level, victim.blockPosition());
			crime = Crime.LIVESTOCK;
		} else {
			return;
		}
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN || killer.isCreative() || killer.isSpectator()) {
			return;
		}
		if (crime == Crime.LIVESTOCK && exempt(killer, f)) {
			return;
		}
		if (victim instanceof Folk folk && folk.leader()) {
			// (a ruler's death is its own story: see KingdomEvents)
			return;
		}
		commit(killer, level, f, crime, victim.blockPosition(), null, false);
	}

	// ---- witnesses and consequences -----------------------------------------------------------------------------

	/** The faction's people who see {@code player} now, nearest first. */
	static List<LivingEntity> witnesses(ServerLevel level, Faction f, ServerPlayer player) {
		// (0.7.101) a hood hides you from farther off, on top of sneaking
		double range = (player.isShiftKeyDown() ? WITNESS_RANGE * Skills.sneakFactor(player, SNEAK_FACTOR) : WITNESS_RANGE) * Identities.hoodSight(player);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), LivingEntity::isAlive)) {
			boolean ours = e instanceof Folk folk ? folk.factionId() == f.id() : e instanceof Villager || (e instanceof IronGolem g && !g.isPlayerCreated());
			if (!ours || e.isSleeping() || e.distanceTo(player) > range) {
				continue;
			}
			if (e instanceof Villager || e instanceof IronGolem) {
				// a village's people (or a realm's townsfolk) only bear witness where its law holds
				Faction theirs = lawAt(level, e.blockPosition());
				if (theirs == null || theirs.id() != f.id()) {
					continue;
				}
			}
			if (!e.hasLineOfSight(player)) {
				continue;
			}
			if (e.distanceTo(player) > HEAR_RANGE) {
				Vec3 look = e.getViewVector(1.0F);
				Vec3 to = player.getEyePosition().subtract(e.getEyePosition()).normalize();
				if (look.dot(to) < VIEW_COS) {
					continue;
				}
			}
			out.add(e);
		}
		out.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		return out;
	}

	/**
	 * {@code player} did {@code crime} against {@code f} at {@code pos}. If anyone of its people saw it: reputation, a
	 * cry, guards after them (medium), outlawry (heavy). If not, and it was a theft ({@code noticeable}), it's noticed later.
	 */
	static void commit(ServerPlayer player, ServerLevel level, Faction f, Crime crime, BlockPos pos, @Nullable Weight weight, boolean noticeable) {
		if (Wars.enemy(player, f)) {
			// (no crime against a realm at war with yours)
			return;
		}
		commit(player, level, f, crime, weight, noticeable, witnesses(level, f, player));
	}

	/** {@link #commit}, with who saw it already worked out (a pickpocket's mark knows, or doesn't, whatever way they face). */
	static void commit(ServerPlayer player, ServerLevel level, Faction f, Crime crime, @Nullable Weight weight, boolean noticeable, List<LivingEntity> seen) {
		if (Wars.enemy(player, f)) {
			return;
		}
		Weight w = weight != null ? weight : crime.weight;
		if (!seen.isEmpty()) {
			// (0.7.101) a hood is a fragile mask: seen close, face on, in the light, it is you
			Identities.recognise(level, player, f, seen, null);
			// (0.5.39) word gets round: its people will ask about it
			Approaches.suspect(player, f.id(), level.getGameTime());
		}
		if (seen.isEmpty()) {
			if (noticeable && crime != Crime.PICKPOCKET) {
				// (0.5.52) a theft nobody saw is Stealth practice (a pickpocket's counts in Pickpocket)
				Skills.unseenTheft(player, level.getGameTime());
			}
			if (noticeable && !Skills.ghost(player) && level.getRandom().nextFloat() < NOTICE_CHANCE) {
				NOTICES.add(new Notice(player.getUUID(), f.id(), level.getGameTime() + NOTICE_TICKS));
			}
			return;
		}
		// (0.5.42) golems can't speak: one that saw it only groans, unless someone else saw it too and cries out
		LivingEntity witness = seen.stream().filter(e -> !(e instanceof IronGolem)).findFirst().orElse(seen.getFirst());
		Component cry = Component.translatable("message.jaymoskingdoms.theft.cry." + crime.cry + "." + level.getRandom().nextInt(SHOUT_VARIANTS));
		Speech.say(level, witness, cry, w == Weight.LIGHT ? Speech.YELLOW : Speech.RED, SHOUT_RANGE);
		level.playSound(null, witness.getX(), witness.getY(), witness.getZ(), witness instanceof IronGolem ? SoundEvents.IRON_GOLEM_HURT
			: SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.2F, w == Weight.HEAVY ? 0.8F : 1.0F);
		Reputation.change(player, f, w.rep);
		if (crime == Crime.ARSON) {
			riot(level, f, player);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.seen." + w.name().toLowerCase(java.util.Locale.ROOT), f.name())
			.withStyle(ChatFormatting.RED));
		if (w == Weight.LIGHT) {
			return;
		}
		long until = level.getGameTime() + (w == Weight.HEAVY ? OUTLAW_TICKS : HUNT_TICKS);
		markWanted(player, f.id(), until);
		if (w == Weight.HEAVY) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.outlaw", f.name(), OUTLAW_TICKS / 24000L)
				.withStyle(ChatFormatting.DARK_RED));
		}
		pursue(level, f, player);
	}

	/**
	 * (0.6.2) {@code f}'s guards are set on {@code player} for {@code ticks} without a crime (defying a tyrant's guard,
	 * slandering a paranoid king): wanted, and the guards nearby come for them.
	 */
	static void arrest(ServerPlayer player, ServerLevel level, Faction f, long ticks) {
		if (Wars.enemy(player, f)) {
			return;
		}
		markWanted(player, f.id(), level.getGameTime() + ticks);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.guard.arrest", f.name(), ticks / 20).withStyle(ChatFormatting.RED));
		pursue(level, f, player);
	}

	/** Its guards, archers and golems near {@code player} turn on them. */
	private static void pursue(ServerLevel level, Faction f, ServerPlayer player) {
		AABB around = player.getBoundingBox().inflate(PURSUIT_RANGE);
		for (Folk guard : level.getEntitiesOfClass(Folk.class, around, x -> x.isAlive() && x.factionId() == f.id() && x.combatant())) {
			if (guard.getTarget() == null || !guard.getTarget().isAlive()) {
				guard.setTarget(player);
			}
		}
		for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, around, g -> g.isAlive() && !g.isPlayerCreated())) {
			Faction theirs = lawAt(level, golem.blockPosition());
			if (theirs != null && theirs.id() == f.id() && golem.getTarget() == null) {
				golem.setTarget(player);
			}
		}
	}

	/** {@code f}'s grown villagers and civilians near {@code player} turn on them. */
	private static void riot(ServerLevel level, Faction f, ServerPlayer player) {
		Riot riot = new Riot(f.id(), level.getGameTime() + RIOT_TICKS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RIOT_RANGE), LivingEntity::isAlive)) {
			if (e.isBaby() || e.isSleeping() || e.distanceTo(player) > RIOT_RANGE) {
				continue;
			}
			boolean ours;
			if (e instanceof Folk folk) {
				ours = folk.factionId() == f.id() && !folk.combatant() && !folk.leader();
			} else if (e instanceof Villager) {
				Faction theirs = lawAt(level, e.blockPosition());
				ours = theirs != null && theirs.id() == f.id();
			} else {
				ours = false;
			}
			if (ours) {
				riot.rioters.put(e.getUUID(), 0L);
			}
		}
		if (riot.rioters.isEmpty()) {
			return;
		}
		RIOTS.put(player.getUUID(), riot);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.riot", f.name()).withStyle(ChatFormatting.RED));
	}

	/** {@code player} struck {@code victim}: if it's one of the crowd after them, the crowd scatters. */
	private static void hitBack(ServerPlayer player, LivingEntity victim) {
		Riot riot = RIOTS.get(player.getUUID());
		if (riot != null && riot.rioters.containsKey(victim.getUUID())) {
			RIOTS.remove(player.getUUID());
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.scatter").withStyle(ChatFormatting.GRAY));
			if (victim.level() instanceof ServerLevel level) {
				for (UUID id : riot.rioters.keySet()) {
					if (level.getEntity(id) instanceof Villager v) {
						v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
					} else if (level.getEntity(id) instanceof Folk folk) {
						folk.getNavigation().stop();
					}
				}
			}
		}
	}

	/** The crowds close on their arsonists and hit them. */
	private static void riots(ServerLevel level, long now) {
		for (Iterator<Map.Entry<UUID, Riot>> it = RIOTS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Riot riot = e.getValue();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (player == null || now > riot.until || !player.isAlive() || player.isCreative() || player.isSpectator() || player.level() != level) {
				it.remove();
				continue;
			}
			for (Iterator<Map.Entry<UUID, Long>> r = riot.rioters.entrySet().iterator(); r.hasNext();) {
				var re = r.next();
				if (!(level.getEntity(re.getKey()) instanceof net.minecraft.world.entity.Mob mob) || !mob.isAlive() || mob.distanceTo(player) > RIOT_GIVE_UP) {
					r.remove();
					continue;
				}
				mob.getLookControl().setLookAt(player);
				if (mob.distanceTo(player) <= RIOT_REACH) {
					if (now >= re.getValue() && now >= riot.nextBlow) {
						re.setValue(now + RIOT_PUNCH_TICKS);
						riot.nextBlow = now + RIOT_CROWD_TICKS;
						mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						player.hurtServer(level, level.damageSources().mobAttack(mob), RIOT_DAMAGE);
					}
				} else if (mob instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(player, true), RIOT_VILLAGER_SPEED, 1);
				} else {
					mob.getNavigation().moveTo(player, RIOT_FOLK_SPEED);
				}
			}
			if (riot.rioters.isEmpty()) {
				it.remove();
			}
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		// fires lit
		for (Iterator<Kindled> it = KINDLED.iterator(); it.hasNext();) {
			Kindled k = it.next();
			if (now > k.at()) {
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(k.player());
				if (player != null) {
					kindled(player, level, k.pos());
				}
			}
		}
		if (now % RIOT_EVERY == 0 && !RIOTS.isEmpty()) {
			riots(level, now);
		}
		// containers closed
		for (Iterator<Map.Entry<UUID, Opened>> it = OPEN.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			Opened open = e.getValue();
			if (player.containerMenu != player.inventoryMenu) {
				open.shown = true;
			} else if (now - open.at >= 2) {
				it.remove();
				if (open.shown) {
					closed(player, level, open);
				}
			}
		}
		// thefts noticed
		for (Iterator<Notice> it = NOTICES.iterator(); it.hasNext();) {
			Notice n = it.next();
			if (now < n.at()) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(n.player());
			Faction f = Factions.get(server, n.faction());
			if (player != null && f != null) {
				Reputation.change(player, f, REP_UNSEEN);
				Approaches.suspect(player, f.id(), now);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.noticed", f.name()).withStyle(ChatFormatting.GRAY));
			}
		}
		// the hunt
		if (now % PURSUIT_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			// (0.7.62) a masked player: the guards hunt the costume where it's wanted and they don't know who wears it
			String mask = Identities.key(player);
			if (mask != null) {
				for (var e : Identities.wantedBy(server, mask).entrySet()) {
					int id = Integer.parseInt(e.getKey());
					Faction f = e.getValue() > now ? Factions.get(server, id) : null;
					if (f != null && !f.fallen() && Identities.hidden(player, id)) {
						pursue(level, f, player);
					}
				}
			}
			Map<String, Long> wanted = player.getAttached(WANTED);
			if (wanted == null || wanted.isEmpty()) {
				continue;
			}
			Map<String, Long> still = new HashMap<>();
			for (var e : wanted.entrySet()) {
				if (e.getValue() > now) {
					still.put(e.getKey(), e.getValue());
					Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
					if (f != null && !f.fallen() && !Identities.hidden(player, f.id())) {
						pursue(level, f, player);
					}
				}
			}
			if (still.size() != wanted.size()) {
				player.setAttached(WANTED, Map.copyOf(still));
			}
		}
	}
}
