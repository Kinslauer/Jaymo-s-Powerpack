package com.jaymoskingdoms;

import com.jaymoskingdoms.disasters.Calamities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.31) Natural disasters, stage 3: the kingdoms' side. When a disaster ({@code disasters.Calamities}) strikes a
 * settlement, the news goes out (across the land for a realm, round about for a village); its treasury pays for the
 * repairs (a noble king pays more and is seen to); the ruler's temper shows (a tyrant raises a storm tax on top, a
 * paranoid king blames whoever near has strange powers); and its folk ask for help: relief quests, bring food or clear
 * the rubble. Court wizards and the almanac foretell disasters ({@link Calamities#foretold}).
 */
public final class DisasterRelief {
	/** A settlement this much further than a disaster's own reach still counts as struck. */
	static final double STRUCK_MARGIN = 24.0;
	/** What repairs cost the treasury, by disaster (emeralds, times the quake's magnitude); a noble king's share more. */
	static final int COST_STORM = 10, COST_FIRE = 25, COST_FLOOD = 20, COST_QUAKE = 25;
	static final float NOBLE_PAYS = 1.5F;
	/** A tyrant's storm tax: this many times the repairs, wrung from the folk. */
	static final float TYRANT_TAX = 2.0F;
	/** A paranoid king blames those with powers within this distance of his seat: this much reputation. */
	static final double BLAME_RANGE = 200.0;
	static final int BLAME = -40;
	/** Relief quests: how many folk ask, and how far from the seat they may be. */
	static final int ASKERS = 2;
	static final double ASK_RANGE = 48.0;
	/** Rubble: how many blocks to clear (min, more), how far from the seat they count, the pay. */
	static final int RUBBLE_MIN = 24, RUBBLE_MORE = 24, RUBBLE_EMERALDS = 6, RUBBLE_REPUTATION = 25;
	static final double RUBBLE_RANGE = 96.0;
	/** Food: what's asked for (item, least, more), the pay. */
	static final String[] FOOD = {"minecraft:bread", "minecraft:baked_potato", "minecraft:cooked_beef", "minecraft:cooked_cod"};
	static final int[] FOOD_MIN = {12, 16, 8, 12}, FOOD_MORE = {8, 8, 6, 8};
	static final int FOOD_EMERALDS = 5, FOOD_REPUTATION = 25;
	/** Players hear of a village's trouble within this distance of it. */
	static final double LOCAL_NEWS = 300.0;

	private DisasterRelief() {
	}

	static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && world instanceof ServerLevel level) {
				cleared(level, sp, pos, state);
			}
		});
	}

	/**
	 * A disaster of {@code kind} ({@code sandstorm}, {@code blizzard}, {@code hail}, {@code wildfire}, {@code flood},
	 * {@code quake}) has struck round (x, z), {@code radius} across; {@code magnitude} for a quake (else 1).
	 */
	public static void struck(ServerLevel level, double x, double z, double radius, String kind, int magnitude) {
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			return;
		}
		double reach = radius + STRUCK_MARGIN;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN) {
				continue;
			}
			double dx = f.seat().getX() - x, dz = f.seat().getZ() - z;
			if (dx * dx + dz * dz <= reach * reach) {
				hit(level, f, kind, magnitude);
			}
		}
	}

	private static void hit(ServerLevel level, Faction f, String kind, int magnitude) {
		MinecraftServer server = level.getServer();
		Component what = Component.translatable("relief.jaymoskingdoms.kind." + kind);
		Component headline = Component.translatable("relief.jaymoskingdoms.struck", f.name(), what).withStyle(ChatFormatting.GOLD);
		if (f.type().isRealm()) {
			KingdomEvents.announce(server, headline);
		} else {
			local(level, f, headline);
		}
		int cost = switch (kind) {
			case "wildfire" -> COST_FIRE;
			case "flood" -> COST_FLOOD;
			case "quake" -> COST_QUAKE * magnitude;
			default -> COST_STORM;
		};
		Tempers.Temper temper = Tempers.of(f);
		if (temper == Tempers.Temper.NOBLE) {
			cost = Math.round(cost * NOBLE_PAYS);
		}
		long paid = Math.min(f.treasury(), cost);
		if (paid > 0) {
			Factions.update(server, f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - paid)));
			local(level, f, Component.translatable(temper == Tempers.Temper.NOBLE ? "relief.jaymoskingdoms.paid.noble" : "relief.jaymoskingdoms.paid",
				f.name(), paid).withStyle(ChatFormatting.GRAY));
		}
		if (temper == Tempers.Temper.TYRANT) {
			long tax = Math.round(cost * TYRANT_TAX);
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + tax));
			local(level, f, Component.translatable("relief.jaymoskingdoms.tyrant", f.name(), tax).withStyle(ChatFormatting.RED));
		} else if (temper == Tempers.Temper.PARANOID) {
			blame(level, f);
		}
		ask(level, f, kind);
	}

	/** News to the players near {@code f}'s seat. */
	private static void local(ServerLevel level, Faction f, Component message) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(f.seat(), LOCAL_NEWS)) {
				p.sendSystemMessage(message);
			}
		}
	}

	/** A paranoid king blames the disaster on anyone near with strange powers. */
	private static void blame(ServerLevel level, Faction f) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(f.seat(), BLAME_RANGE) && Oddities.power(p) != null) {
				Reputation.change(p, f, BLAME);
				p.sendSystemMessage(Component.translatable("relief.jaymoskingdoms.blamed", f.name()).withStyle(ChatFormatting.RED));
			}
		}
	}

	/** A couple of {@code f}'s grown villagers ask for help: food, and the rubble cleared. */
	private static void ask(ServerLevel level, Faction f, String kind) {
		RandomSource random = level.getRandom();
		List<Villager> folk = new ArrayList<>(level.getEntitiesOfClass(Villager.class,
			new net.minecraft.world.phys.AABB(f.seat()).inflate(ASK_RANGE), v -> v.isAlive() && !v.isBaby() && !Quests.offering(v)));
		java.util.Collections.shuffle(folk, new java.util.Random(random.nextLong()));
		// hail and blizzards leave no rubble to speak of: food only
		boolean rubble = !kind.equals("hail") && !kind.equals("blizzard");
		for (int i = 0; i < Math.min(ASKERS, folk.size()); i++) {
			Villager v = folk.get(i);
			String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
			Quest.Giver who = new Quest.Giver(v.getUUID(), v.getName().getString(), f.id(), false, job);
			Quest q;
			if (i == 0 || !rubble) {
				int k = random.nextInt(FOOD.length);
				int count = FOOD_MIN[k] + random.nextInt(FOOD_MORE[k] + 1);
				Quest.Objective o = new Quest.Objective(FOOD[k], count, 0, Optional.empty(), Optional.empty(), "", f.name(), false);
				Quest.Reward r = new Quest.Reward(FOOD_EMERALDS, List.of(), Optional.empty(), Optional.empty(), 2, FOOD_REPUTATION, 10);
				q = new Quest(UUID.randomUUID().toString(), Quest.GATHER, "relief.food." + kind, who, o, r, 0L);
			} else {
				int count = RUBBLE_MIN + random.nextInt(RUBBLE_MORE + 1);
				Quest.Objective o = new Quest.Objective("rubble", count, 0, Optional.of(f.seat()), Optional.empty(), "", f.name(), false);
				Quest.Reward r = new Quest.Reward(RUBBLE_EMERALDS, List.of(), Optional.empty(), Optional.empty(), 2, RUBBLE_REPUTATION, 10);
				q = new Quest(UUID.randomUUID().toString(), Quest.RELIEF, "relief.rubble." + kind, who, o, r, 0L);
			}
			Quests.offerNow(level, v, q);
		}
	}

	/** Whether {@code state} is rubble a disaster leaves: stone, earth, sand, gravel, drifts and fallen blocks. */
	static boolean rubble(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
			|| state.is(Blocks.COBBLESTONE) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)
			|| state.is(com.jaymoskingdoms.disasters.Disasters.SAND_LAYER) || state.is(com.jaymoskingdoms.disasters.Disasters.RED_SAND_LAYER)
			|| state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS);
	}

	/** {@code player} broke a block: it counts for any rubble-clearing quest of theirs near its settlement. */
	private static void cleared(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (!rubble(state)) {
			return;
		}
		for (Quest q : Quests.log(player)) {
			Quest.Objective o = q.objective();
			if (q.kind() != Quest.RELIEF || q.counted() || o.pos().isEmpty() || !o.pos().get().closerThan(pos, RUBBLE_RANGE)) {
				continue;
			}
			Quest next = q.withProgress(o.progress() + 1);
			Quests.put(player, next);
			player.sendOverlayMessage(Quests.objective(level, player, next).copy().withStyle(next.counted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			Quests.sync(player, false);
			return;
		}
	}

	/** A court wizard, spoken to, tells {@code player} what the stars foretell, if anything. Whether he had something. */
	static boolean foretell(ServerLevel level, ServerPlayer player, LivingEntity wizard) {
		List<Component> lines = Calamities.foretold(level, wizard.blockPosition());
		if (lines.isEmpty()) {
			return false;
		}
		for (Component line : lines) {
			Speech.sayTo(level, wizard, player, Component.translatable("relief.jaymoskingdoms.wizard", line), Speech.GOLD);
		}
		return true;
	}

	/** The Traveller's Journal's weather page line: what's foretold near {@code player} (null: fair). */
	static @Nullable Component journal(ServerLevel level, ServerPlayer player) {
		List<Component> lines = Calamities.foretold(level, player.blockPosition());
		if (lines.isEmpty()) {
			return null;
		}
		net.minecraft.network.chat.MutableComponent all = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				all.append(" ");
			}
			all.append(lines.get(i));
		}
		return all;
	}
}
