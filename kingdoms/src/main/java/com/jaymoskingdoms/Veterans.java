package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Veterans for hire on the roads (docs/Pawn-Plan.md row A3; after the higher-level pawns of Dragon's Dogma, a
 * commercial game, who cost more to take on: ideas only). Not every {@link Drifters drifter} is down and out: some who
 * turn up on a road are old hands ({@link #VETERAN}) or a fallen knight in his own iron ({@link #KNIGHT}), with what
 * they've learned already behind them ({@link Learners#WINS}) and a price to match ({@link #PRICE}).
 * <p>
 * The foe side: a veteran nobody takes on doesn't always just move along. If a camp is near ({@link #SELL_RANGE}) they
 * sell their sword to it ({@link #SOLD}: {@link Comrades#side} gives the camp's side) and fight for its band; and they
 * remember who turned them down ({@link #SPURNED}).
 */
public final class Veterans {
	public static final int GREEN = 0, VETERAN = 1, KNIGHT = 2;
	/** By grade: health, damage, wins already learned, price (emeralds' worth). */
	static final double[] HEALTH = {Drifters.HEALTH, 18.0, 24.0}, DAMAGE = {Drifters.DAMAGE, 3.0, 4.0};
	static final int[] WINS = {0, 10, 24}, PRICE = {Drifters.HIRE_COST, 5, 10};
	/** Chances, on a road and in a tavern: a veteran, a knight. */
	static final float ROAD_VETERAN = 0.3F, ROAD_KNIGHT = 0.08F, TAVERN_VETERAN = 0.15F;
	/** A camp this near (blocks) buys a veteran's sword; a sold one keeps this near its camp. */
	static final double SELL_RANGE = 192.0, CAMP_HOME = 16.0, BAND_NEAR = 24.0, SPURNED_NEAR = 10.0;
	static final int SPURNED_EVERY = 1200, EVERY = 40;

	public static final AttachmentType<Integer> GRADE = AttachmentRegistry.create(JaymosKingdoms.id("drifter_grade"),
		builder -> builder.persistent(Codec.INT));
	/** On a veteran who sold their sword: the camp. Saved. */
	public static final AttachmentType<Integer> SOLD = AttachmentRegistry.create(JaymosKingdoms.id("sold_sword"),
		builder -> builder.persistent(Codec.INT));
	/** When a sold sword last went raiding (or was sold). Saved. */
	static final AttachmentType<Long> SOLD_AT = AttachmentRegistry.create(JaymosKingdoms.id("sold_sword_at"),
		builder -> builder.persistent(Codec.LONG));
	/** A sold sword leads a raid on someone who turned it down at most this often (ticks: a week), when they're this near a settlement. */
	static final long RAID_EVERY = 168000L;
	/** Players a veteran pitched to who didn't take them on. Saved. */
	static final AttachmentType<List<String>> SPURNED = AttachmentRegistry.create(JaymosKingdoms.id("drifter_spurned"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	private static final Set<Villager> SOLD_LOADED = new HashSet<>();
	private static final Map<UUID, Long> SAID = new HashMap<>();

	private Veterans() {
	}

	static void register() {
		if (!McaCompat.LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(Veterans::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && v.hasAttached(SOLD)) {
				SOLD_LOADED.add(v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				SOLD_LOADED.remove(v);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SOLD_LOADED.clear();
			SAID.clear();
		});
	}

	static int grade(LivingEntity v) {
		return Math.max(GREEN, Math.min(KNIGHT, v.getAttachedOrElse(GRADE, GREEN)));
	}

	/** (Drifters) what a newcomer is: most are green; on a road more are old hands. */
	static int roll(RandomSource random, boolean tavern) {
		float r = random.nextFloat();
		if (tavern) {
			return r < TAVERN_VETERAN ? VETERAN : GREEN;
		}
		return r < ROAD_KNIGHT ? KNIGHT : r < ROAD_KNIGHT + ROAD_VETERAN ? VETERAN : GREEN;
	}

	/** (Drifters) {@code v} is an old hand of {@code grade}: stronger, learned, and kitted (the caller applies what's learned). */
	static void make(Villager v, int grade) {
		if (grade <= GREEN) {
			return;
		}
		v.setAttached(GRADE, grade);
		v.setAttached(Learners.WINS, WINS[grade]);
		AttributeInstance health = v.getAttribute(Attributes.MAX_HEALTH), damage = v.getAttribute(Attributes.ATTACK_DAMAGE);
		if (health != null) {
			health.setBaseValue(HEALTH[grade]);
			v.setHealth((float)HEALTH[grade]);
		}
		if (damage != null) {
			damage.setBaseValue(DAMAGE[grade]);
		}
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(grade == KNIGHT ? Items.IRON_SWORD : Items.STONE_SWORD));
		v.setItemSlot(EquipmentSlot.CHEST, new ItemStack(grade == KNIGHT ? Items.IRON_CHESTPLATE : Items.LEATHER_CHESTPLATE));
		if (grade == KNIGHT) {
			v.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		}
		// a few kinds they've fought often enough to know (row B1): two at two stars, a knight three and one at three
		List<String> kinds = new ArrayList<>(List.of(KNOWN_KINDS));
		java.util.Collections.shuffle(kinds, new java.util.Random(v.getRandom().nextLong()));
		Map<String, Integer> stars = new HashMap<>(v.getAttachedOrElse(FoeLore.STARS, Map.of()));
		int n = grade == KNIGHT ? 3 : 2;
		for (int i = 0; i < n; i++) {
			stars.merge(kinds.get(i), grade == KNIGHT && i == 0 ? FoeLore.KILLS_PER_STAR[2] : FoeLore.KILLS_PER_STAR[1], Math::max);
		}
		v.setAttached(FoeLore.STARS, stars);
	}

	/** Kinds an old hand may know. */
	static final String[] KNOWN_KINDS = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper", "minecraft:pillager",
		"minecraft:vindicator", "minecraft:husk", "minecraft:drowned", "minecraft:witch"};

	/** (BandLeaders) a sold sword leading its camp's raid on someone who turned it down. */
	static boolean leading(LivingEntity m) {
		return m.hasAttached(SOLD) && BandTallies.raiding(m);
	}

	/** The price to take {@code v} on (emeralds' worth). */
	static int price(LivingEntity v) {
		return PRICE[grade(v)];
	}

	/** {@code player} heard {@code v}'s pitch. */
	static void pitched(Villager v, Player player) {
		List<String> spurned = new ArrayList<>(v.getAttachedOrElse(SPURNED, List.of()));
		String id = player.getUUID().toString();
		if (!spurned.contains(id)) {
			spurned.add(id);
			v.setAttached(SPURNED, spurned);
		}
	}

	/** (Drifters) an old hand nobody hired sells their sword to a camp near, if there is one. True if they did. */
	static boolean sell(ServerLevel level, Villager v) {
		if (grade(v) == GREEN) {
			return false;
		}
		String best = null;
		double bestD = SELL_RANGE * SELL_RANGE;
		for (Map.Entry<String, Camps.CampData> e : Camps.all(level.getServer()).entrySet()) {
			double d = e.getValue().center().distSqr(v.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = e.getKey();
			}
		}
		if (best == null) {
			return false;
		}
		try {
			v.setAttached(SOLD, Integer.parseInt(best));
		} catch (NumberFormatException e) {
			return false;
		}
		McaBridge.toGuard(v);
		v.setPersistenceRequired();
		v.setAttached(SOLD_AT, level.getGameTime());
		SOLD_LOADED.add(v);
		return true;
	}

	/** A sold sword whose week is up leads its camp on a settlement where someone who turned it down is (row A3, the foe side). */
	private static void raidOn(ServerLevel level, Villager v, int campId, Camps.CampData data, long now) {
		if (now - v.getAttachedOrElse(SOLD_AT, now) < RAID_EVERY || Raids.raiding(campId)) {
			return;
		}
		List<String> spurned = v.getAttachedOrElse(SPURNED, List.of());
		Faction camp = Factions.get(level.getServer(), campId);
		if (camp == null || spurned.isEmpty()) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (!spurned.contains(p.getUUID().toString())) {
				continue;
			}
			for (Faction f : Factions.all(level.getServer())) {
				if (f.fallen() || f.type() == FactionType.BARBARIAN || f.seat().distSqr(p.blockPosition()) > Raids.PLAYER_RANGE * Raids.PLAYER_RANGE
					|| f.seat().distSqr(data.center()) > Raids.REACH * Raids.REACH) {
					continue;
				}
				v.setAttached(SOLD_AT, now);
				if (Raids.start(level, camp, f)) {
					Callouts.say(v, "sell.raid", p, true, p.getDisplayName());
				}
				return;
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (SOLD_LOADED.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Villager v : new ArrayList<>(SOLD_LOADED)) {
			Integer camp = v.getAttached(SOLD);
			if (camp == null || !v.isAlive() || v.isRemoved() || !(v.level() instanceof ServerLevel level)) {
				SOLD_LOADED.remove(v);
				continue;
			}
			Camps.CampData data = Camps.data(server, camp);
			if (data == null) {
				// the camp's gone: they drift off again
				if (level.getNearestPlayer(v, 48.0) == null) {
					SOLD_LOADED.remove(v);
					v.discard();
				}
				continue;
			}
			if (Downed.isDowned(v)) {
				continue;
			}
			String side = "f:" + camp;
			if (BandTallies.raiding(v)) {
				continue;
			}
			raidOn(level, v, camp, data, now);
			if (Comrades.target(v) == null) {
				for (Mob m : level.getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(BAND_NEAR), m -> m != v && Comrades.side(m).equals(side))) {
					LivingEntity foe = Comrades.target(m);
					if (foe != null && !Comrades.side(foe).equals(side)) {
						Comrades.aim(v, foe);
						break;
					}
				}
			}
			if (Comrades.target(v) == null && v.blockPosition().distSqr(data.center()) > CAMP_HOME * CAMP_HOME && v.getNavigation().isDone()) {
				BlockPos c = data.center();
				v.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0.6);
			}
			List<String> spurned = v.getAttachedOrElse(SPURNED, List.of());
			if (!spurned.isEmpty() && now - SAID.getOrDefault(v.getUUID(), -SPURNED_EVERY * 2L) > SPURNED_EVERY) {
				for (ServerPlayer p : level.getPlayers(p -> p.distanceTo(v) < SPURNED_NEAR && spurned.contains(p.getUUID().toString()))) {
					SAID.put(v.getUUID(), now);
					Callouts.say(v, "sell.camp", p, true, p.getDisplayName());
					break;
				}
			}
		}
	}
}
