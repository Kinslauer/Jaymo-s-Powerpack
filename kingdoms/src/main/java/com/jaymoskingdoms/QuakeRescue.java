package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Folk trapped under a quake's rubble, and the relief quest to dig them out (Andy, 2026-11-12, on the rescue half of
 * disaster relief never being built: "can we build this?"). An earthquake ({@link DisasterRelief#struck}) shuts one of a
 * struck settlement's grown folk (two from magnitude {@link #TWO_FROM}) in a cell of fallen stone where they stand; a
 * neighbour asks for them to be dug out ({@link Quest#RESCUE}, target {@code trapped:<uuid>}). They call through the
 * stones while anyone is near. Once there's a way out, they're free: the player who dug them out (the nearest within
 * {@link #RESCUER_RANGE}) can go back to the neighbour for the reward. Left alone a day, their neighbours dig them out
 * themselves and the quest is taken back: nobody is hurt for a player's not coming.
 */
public final class QuakeRescue {
	/** A second one trapped from this magnitude; how far from the seat they may be. */
	static final int TWO_FROM = 3;
	static final double TRAP_RANGE = 40.0;
	/** How often the trapped are looked at, and how often they call out while someone is within {@link #HEAR_RANGE}. */
	static final int CHECK_TICKS = 40, CALL_TICKS = 200;
	static final double HEAR_RANGE = 24.0, RESCUER_RANGE = 10.0;
	/** Left alone this long, their neighbours dig them out. */
	static final long NEIGHBOURS_DIG = 24000L;
	/** The pay for digging someone out. */
	static final int EMERALDS = 8, HEARTS = 10, REPUTATION = 30, STANDING = 10;

	/** On a trapped villager: since when, and the neighbour who asked for them (who holds the quest). */
	public record Trapped(long since, UUID asker) {
		static final Codec<Trapped> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("since").forGetter(Trapped::since),
				UUIDUtil.CODEC.fieldOf("asker").forGetter(Trapped::asker))
			.apply(i, Trapped::new));
	}

	static final AttachmentType<Trapped> TRAPPED = AttachmentRegistry.create(JaymosKingdoms.id("quake_trapped"),
		builder -> builder.persistent(Trapped.CODEC));

	/** The trapped about now, by UUID → their level. */
	private static final Map<UUID, ServerLevel> TRAPPED_NOW = new HashMap<>();

	private QuakeRescue() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.hasAttached(TRAPPED)) {
				TRAPPED_NOW.put(entity.getUUID(), level);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> TRAPPED_NOW.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(QuakeRescue::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRAPPED_NOW.clear());
	}

	/** A quake of {@code magnitude} has struck {@code f}: someone is shut in by the rubble, and a neighbour asks for help. */
	static void struck(ServerLevel level, Faction f, int magnitude) {
		RandomSource random = level.getRandom();
		List<Villager> folk = new ArrayList<>(level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(TRAP_RANGE),
			v -> v.isAlive() && !v.isBaby() && !Quests.offering(v) && v.onGround() && !v.isInWater() && !v.isPassenger() && !v.hasAttached(TRAPPED)));
		java.util.Collections.shuffle(folk, new java.util.Random(random.nextLong()));
		int trap = magnitude >= TWO_FROM ? 2 : 1;
		List<Villager> trapped = new ArrayList<>();
		for (Villager v : folk) {
			if (trapped.size() >= trap) {
				break;
			}
			if (wall(level, v.blockPosition(), random)) {
				trapped.add(v);
			}
		}
		folk.removeAll(trapped);
		for (int i = 0; i < trapped.size(); i++) {
			Villager v = trapped.get(i);
			Villager asker = i < folk.size() ? folk.get(i) : null;
			v.setAttached(TRAPPED, new Trapped(level.getGameTime(), asker == null ? v.getUUID() : asker.getUUID()));
			TRAPPED_NOW.put(v.getUUID(), level);
			v.getNavigation().stop();
			Speech.say(level, v, Component.translatable("relief.jaymoskingdoms.trapped.call"), Speech.RED, HEAR_RANGE);
			if (asker != null) {
				ask(level, f, asker, v);
			}
		}
	}

	/** Fallen stone round {@code feet} (where it's open), and over the head. Whether it shut them in. */
	private static boolean wall(ServerLevel level, BlockPos feet, RandomSource random) {
		if (!open(level, feet) || !open(level, feet.above())) {
			return false;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					if ((dx == 0 && dz == 0 && dy < 2)) {
						continue;
					}
					BlockPos p = feet.offset(dx, dy, dz);
					if (level.getBlockState(p).canBeReplaced()) {
						level.setBlockAndUpdate(p, stone(random));
					}
				}
			}
		}
		level.playSound(null, feet, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
		return !free(level, feet);
	}

	private static BlockState stone(RandomSource random) {
		return switch (random.nextInt(5)) {
			case 0 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case 1 -> Blocks.ANDESITE.defaultBlockState();
			case 2 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
			default -> Blocks.COBBLESTONE.defaultBlockState();
		};
	}

	/** Rubble this quake leaves (what {@link #stone} lays): what neighbours clear away. */
	private static boolean rubble(BlockState state) {
		Block b = state.getBlock();
		return b == Blocks.COBBLESTONE || b == Blocks.MOSSY_COBBLESTONE || b == Blocks.ANDESITE || b == Blocks.CRACKED_STONE_BRICKS;
	}

	private static boolean open(ServerLevel level, BlockPos p) {
		return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
	}

	/** There's a way out of {@code feet}: a side open at feet and head, or open overhead. */
	private static boolean free(ServerLevel level, BlockPos feet) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (open(level, feet.relative(d)) && open(level, feet.relative(d).above())) {
				return true;
			}
		}
		return open(level, feet.above(2)) && open(level, feet.above(3));
	}

	private static void ask(ServerLevel level, Faction f, Villager asker, Villager trapped) {
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(asker.getVillagerData().profession().value()).getPath();
		Quest.Giver who = new Quest.Giver(asker.getUUID(), asker.getName().getString(), f.id(), false, job);
		Quest.Objective o = new Quest.Objective("trapped:" + trapped.getUUID(), 1, 0, Optional.of(trapped.blockPosition()), Optional.of(trapped.getUUID()),
			trapped.getName().getString(), f.name(), false);
		Quest.Reward r = new Quest.Reward(EMERALDS, List.of(), Optional.empty(), Optional.empty(), HEARTS, REPUTATION, STANDING);
		Quests.offerNow(level, asker, new Quest(UUID.randomUUID().toString(), Quest.RESCUE, "relief.trapped.quake", who, o, r, 0L));
	}

	/** A rescue quest's objective is a trapped villager to dig out (not a captive to bring home). */
	static boolean digOut(Quest q) {
		return q.objective().target().startsWith("trapped:");
	}

	private static void tick(MinecraftServer server) {
		if (TRAPPED_NOW.isEmpty() || server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (Map.Entry<UUID, ServerLevel> e : List.copyOf(TRAPPED_NOW.entrySet())) {
			ServerLevel level = e.getValue();
			Entity entity = level.getEntity(e.getKey());
			if (!(entity instanceof Villager v) || !v.isAlive()) {
				TRAPPED_NOW.remove(e.getKey());
				continue;
			}
			Trapped t = v.getAttached(TRAPPED);
			if (t == null) {
				TRAPPED_NOW.remove(e.getKey());
				continue;
			}
			BlockPos feet = v.blockPosition();
			if (free(level, feet)) {
				freed(level, v, t);
				continue;
			}
			long now = level.getGameTime();
			if (now - t.since() >= NEIGHBOURS_DIG) {
				dugOutByNeighbours(level, v, feet);
				continue;
			}
			if (now % CALL_TICKS < CHECK_TICKS && level.getNearestPlayer(v, HEAR_RANGE) != null) {
				Speech.say(level, v, Component.translatable("relief.jaymoskingdoms.trapped.call"), Speech.RED, HEAR_RANGE);
			}
		}
	}

	/** {@code v} has a way out: thanks, and the quest is done for whoever dug them out. */
	private static void freed(ServerLevel level, Villager v, Trapped t) {
		v.removeAttached(TRAPPED);
		TRAPPED_NOW.remove(v.getUUID());
		Speech.say(level, v, Component.translatable("relief.jaymoskingdoms.trapped.freed"), Speech.GOLD, HEAR_RANGE);
		net.minecraft.world.entity.player.Player near = level.getNearestPlayer(v, RESCUER_RANGE);
		if (near instanceof ServerPlayer sp) {
			Quests.rescued(sp, v);
		}
	}

	/** Left a day, {@code v}'s neighbours dig them out; whoever asked takes the quest back. */
	private static void dugOutByNeighbours(ServerLevel level, Villager v, BlockPos feet) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos p = feet.offset(dx, dy, dz);
					if (!(dx == 0 && dz == 0 && dy < 2) && rubble(level.getBlockState(p))) {
						level.removeBlock(p, false);
					}
				}
			}
		}
		Trapped t = v.getAttached(TRAPPED);
		v.removeAttached(TRAPPED);
		TRAPPED_NOW.remove(v.getUUID());
		if (t != null && level.getEntity(t.asker()) instanceof Villager asker) {
			Quests.withdraw(asker, "relief.trapped");
		}
		Speech.say(level, v, Component.translatable("relief.jaymoskingdoms.trapped.neighbours"), Speech.WHITE, HEAR_RANGE);
	}
}
