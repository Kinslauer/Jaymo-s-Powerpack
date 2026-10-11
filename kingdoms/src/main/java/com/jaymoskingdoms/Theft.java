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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
		LIVESTOCK(Weight.LIGHT, "livestock"), FOLK(Weight.MEDIUM, "murder"), VILLAGER(Weight.HEAVY, "murder"), GOLEM(Weight.HEAVY, "murder");

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

	private Theft() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && !(sp.isSecondaryUseActive() && !sp.getItemInHand(hand).isEmpty())) {
				opened(sp, server, hit.getBlockPos());
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
		ServerTickEvents.END_SERVER_TICK.register(Theft::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OPEN.clear();
			NOTICES.clear();
			GIFTS.clear();
		});
	}

	// ---- queries for other systems ------------------------------------------------------------------------------

	/** {@code player} is hunted by {@code faction}'s guards right now (a medium crime or worse, seen). */
	public static boolean wanted(ServerPlayer player, int faction) {
		Long until = player.getAttachedOrElse(WANTED, Map.of()).get(Integer.toString(faction));
		return until != null && until > player.level().getGameTime();
	}

	/** {@code player} is an outlaw in {@code faction} (a heavy crime, seen, not yet forgotten). */
	public static boolean outlaw(ServerPlayer player, int faction) {
		Long until = player.getAttachedOrElse(WANTED, Map.of()).get(Integer.toString(faction));
		return until != null && until - player.level().getGameTime() > HUNT_TICKS;
	}

	// ---- what counts --------------------------------------------------------------------------------------------

	/** Whose law holds at {@code pos} (null where none does, or in a camp). */
	private static @Nullable Faction lawAt(ServerLevel level, BlockPos pos) {
		Faction f = Factions.around(level.getServer(), level, pos, Villages.VILLAGE_RADIUS);
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	/** {@code player} answers to no one here: creative, or of rank enough in the faction to take from its stores. */
	private static boolean exempt(ServerPlayer player, Faction f) {
		return player.isSpectator() || Storage.mayTake(player, f);
	}

	private static void opened(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Faction f = Factions.storageOwner(level.getServer(), pos);
		if (f == null || f.fallen() || level != Factions.level(level.getServer()) || !(level.getBlockEntity(pos) instanceof Container c)) {
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
		double range = player.isShiftKeyDown() ? WITNESS_RANGE * SNEAK_FACTOR : WITNESS_RANGE;
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
		Weight w = weight != null ? weight : crime.weight;
		List<LivingEntity> seen = witnesses(level, f, player);
		if (seen.isEmpty()) {
			if (noticeable && level.getRandom().nextFloat() < NOTICE_CHANCE) {
				NOTICES.add(new Notice(player.getUUID(), f.id(), level.getGameTime() + NOTICE_TICKS));
			}
			return;
		}
		LivingEntity witness = seen.getFirst();
		Component cry = Component.translatable("message.jaymoskingdoms.theft.cry", witness.getDisplayName(),
			Component.translatable("message.jaymoskingdoms.theft.cry." + crime.cry + "." + level.getRandom().nextInt(SHOUT_VARIANTS)))
			.withStyle(w == Weight.LIGHT ? ChatFormatting.YELLOW : ChatFormatting.RED);
		for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, witness.getBoundingBox().inflate(SHOUT_RANGE))) {
			near.sendSystemMessage(cry);
		}
		level.playSound(null, witness.getX(), witness.getY(), witness.getZ(), witness instanceof IronGolem ? SoundEvents.IRON_GOLEM_HURT
			: SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.2F, w == Weight.HEAVY ? 0.8F : 1.0F);
		Reputation.change(player, f, w.rep);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.seen." + w.name().toLowerCase(java.util.Locale.ROOT), f.name())
			.withStyle(ChatFormatting.RED));
		if (w == Weight.LIGHT) {
			return;
		}
		long until = level.getGameTime() + (w == Weight.HEAVY ? OUTLAW_TICKS : HUNT_TICKS);
		Map<String, Long> wanted = new HashMap<>(player.getAttachedOrElse(WANTED, Map.of()));
		wanted.merge(Integer.toString(f.id()), until, Math::max);
		player.setAttached(WANTED, Map.copyOf(wanted));
		if (w == Weight.HEAVY) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.outlaw", f.name(), OUTLAW_TICKS / 24000L)
				.withStyle(ChatFormatting.DARK_RED));
		}
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

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
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
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.theft.noticed", f.name()).withStyle(ChatFormatting.GRAY));
			}
		}
		// the hunt
		if (now % PURSUIT_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			Map<String, Long> wanted = player.getAttached(WANTED);
			if (wanted == null || wanted.isEmpty() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			Map<String, Long> still = new HashMap<>();
			for (var e : wanted.entrySet()) {
				if (e.getValue() > now) {
					still.put(e.getKey(), e.getValue());
					Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
					if (f != null && !f.fallen()) {
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
