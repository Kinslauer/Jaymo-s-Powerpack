package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Vampires;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The old vampire rules, for the mobs and the players alike:
 * <ul>
 * <li><b>A threshold.</b> A vampire cannot enter a home (a roofed room or rooms, no bigger than {@link #HOME_MOST} cells, with
 * a bed in it) until a resident invites it. A resident is a villager whose bed is there, or a player who sleeps there; they
 * invite by sneaking and using an empty hand on the vampire, and doing it again takes the welcome back. A vampire mob that has
 * not been exposed can be invited by a villager who lives in the home. The invitation is the vampire's, kept across saves.</li>
 * <li><b>Running water.</b> A vampire cannot cross flowing water or a river on its own; it is thrown back, scalded.
 * A boat or a mount carries it over.</li>
 * <li><b>The coffin.</b> A bed boxed in by dark oak slabs is a coffin (the vampires' lairs have them); a stake into a vampire
 * asleep in one kills it for good, a vampire player too.</li>
 * </ul>
 */
public final class OldRules {
	/** The biggest a home is (cells) and how far the search for its beds goes from the door. */
	static final int HOME_MOST = 500;
	/** Slabs round a bed that make it a coffin. */
	static final int COFFIN_SLABS = 2;
	/** How often (ticks) vampires are looked at, the scald from running water, and how near a villager must be to invite a mob. */
	static final int EVERY = 4;
	static final float SCALD = 2.0F;
	static final double VILLAGER_NEAR = 8.0;
	/** A mob vampire goes to sleep in a coffin by day within this (blocks), looking this often (ticks). */
	static final int COFFIN_REACH = 16, COFFIN_EVERY = 200;
	/** The invitations a vampire holds: the key of each home. */
	public static final AttachmentType<List<Long>> INVITED = AttachmentRegistry.create(com.jaymoskingdoms.JaymosKingdoms.id("vampire_invites"),
		b -> b.persistent(Codec.LONG.listOf()).copyOnDeath());

	/** Where each vampire last stood in the clear (not in a home it is barred from, not in running water). */
	private static final Map<UUID, Vec3> LAST_CLEAR = new HashMap<>();
	private static final List<LivingEntity> TO_KILL = new ArrayList<>();
	private static final Map<UUID, Long> WARNED = new HashMap<>();

	private OldRules() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(OldRules::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
				|| !(world instanceof ServerLevel level) || !(entity instanceof LivingEntity vampire) || !Vampires.isVampire(vampire) || entity == player) {
				return InteractionResult.PASS;
			}
			return invite(level, player, vampire) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	// ---- homes ------------------------------------------------------------------------------------------------------

	/** The key of the home {@code pos} is in (its first bed), or null: open air, a cave with no bed, or a hall too big for a home. */
	public static @Nullable Long home(ServerLevel level, BlockPos pos) {
		if (level.canSeeSky(pos) || level.canSeeSky(pos.above())) {
			return null;
		}
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = free(level, pos) ? pos : pos.above();
		if (!free(level, start)) {
			return null;
		}
		seen.add(start);
		queue.add(start);
		long best = Long.MAX_VALUE;
		boolean bed = false;
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			if (level.canSeeSky(at)) {
				return null;
			}
			if (level.getBlockState(at).is(BlockTags.BEDS)) {
				bed = true;
				best = Math.min(best, at.asLong());
			}
			for (Direction d : Direction.values()) {
				BlockPos next = at.relative(d);
				if (seen.contains(next) || !free(level, next)) {
					continue;
				}
				if (seen.size() >= HOME_MOST) {
					return null;
				}
				seen.add(next);
				queue.add(next);
			}
		}
		return bed ? best : null;
	}

	/** A cell a person can stand in or pass through (open air, a bed, a door or gate). */
	private static boolean free(ServerLevel level, BlockPos at) {
		BlockState s = level.getBlockState(at);
		return s.getCollisionShape(level, at).isEmpty() || s.is(BlockTags.BEDS) || s.is(BlockTags.DOORS) || s.is(BlockTags.FENCE_GATES);
	}

	/** Whether {@code who} lives in the home keyed {@code key}. */
	static boolean resident(ServerLevel level, LivingEntity who, long key) {
		BlockPos bed = BlockPos.of(key);
		if (who instanceof ServerPlayer p) {
			ServerPlayer.RespawnConfig cfg = p.getRespawnConfig();
			return cfg != null && cfg.respawnData().dimension() == level.dimension() && cfg.respawnData().pos().distSqr(bed) <= 100.0
				&& home(level, cfg.respawnData().pos()) instanceof Long k && k == key;
		}
		if (who instanceof Villager v) {
			var home = com.jaymoskingdoms.Doubles.homeOf(v);
			return home != null && home.dimension() == level.dimension() && home(level, home.pos()) instanceof Long k && k == key;
		}
		return false;
	}

	public static boolean invited(LivingEntity vampire, long key) {
		return vampire.getAttachedOrElse(INVITED, List.<Long>of()).contains(key);
	}

	private static void welcome(LivingEntity vampire, long key, boolean on) {
		List<Long> list = new ArrayList<>(vampire.getAttachedOrElse(INVITED, List.<Long>of()));
		if (on) {
			if (!list.contains(key)) {
				list.add(key);
			}
		} else {
			list.remove(key);
		}
		vampire.setAttached(INVITED, list);
	}

	/** {@code resident}, standing in their home, welcomes (or turns away) {@code vampire}. */
	private static boolean invite(ServerLevel level, Player resident, LivingEntity vampire) {
		Long key = home(level, resident.blockPosition());
		if (key == null || !resident(level, resident, key)) {
			return false;
		}
		boolean now = !invited(vampire, key);
		welcome(vampire, key, now);
		level.playSound(null, vampire.blockPosition(), now ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 0.9F);
		resident.sendOverlayMessage(Component.translatable(now ? "creatures.jaymoskingdoms.invited" : "creatures.jaymoskingdoms.uninvited", vampire.getDisplayName()));
		if (vampire instanceof ServerPlayer vp) {
			vp.sendOverlayMessage(Component.translatable(now ? "creatures.jaymoskingdoms.welcomed" : "creatures.jaymoskingdoms.turned_away"));
		}
		return true;
	}

	// ---- the tick ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!TO_KILL.isEmpty()) {
			for (LivingEntity victim : new ArrayList<>(TO_KILL)) {
				if (victim.isAlive() && victim.level() instanceof ServerLevel level) {
					victim.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				}
			}
			TO_KILL.clear();
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				if (Vampires.isVampire(player) && !player.isCreative() && !player.isSpectator() && player.isAlive()) {
					watch(level, player);
				}
			}
			if (level.getGameTime() % COFFIN_EVERY == 0) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, nearPlayers(level), m -> Vampires.isVampire(m) && m.isAlive())) {
					watch(level, mob);
					toBed(level, mob);
				}
			} else {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, nearPlayers(level), m -> Vampires.isVampire(m) && m.isAlive())) {
					watch(level, mob);
				}
			}
		}
	}

	private static net.minecraft.world.phys.AABB nearPlayers(ServerLevel level) {
		net.minecraft.world.phys.AABB box = null;
		for (ServerPlayer p : level.players()) {
			net.minecraft.world.phys.AABB b = p.getBoundingBox().inflate(48.0);
			box = box == null ? b : box.minmax(b);
		}
		return box == null ? new net.minecraft.world.phys.AABB(0, 0, 0, 0, 0, 0) : box;
	}

	/** Keeps {@code v} out of homes it is not asked into, and out of running water. */
	private static void watch(ServerLevel level, LivingEntity v) {
		if (v.isPassenger() || v.isSleeping()) {
			return;
		}
		BlockPos pos = v.blockPosition();
		boolean barred = false;
		String why = null;
		if (runningWater(level, pos)) {
			barred = true;
			why = "creatures.jaymoskingdoms.running_water";
			v.hurtServer(level, level.damageSources().magic(), SCALD);
		} else {
			Long key = home(level, pos);
			if (key != null && !invited(v, key)) {
				if (v instanceof Mob mob && !(Vampires.exposed(mob)) && askedIn(level, mob, key)) {
					welcome(v, key, true);
				} else {
					barred = true;
					why = "creatures.jaymoskingdoms.uninvited_door";
				}
			}
		}
		if (!barred) {
			LAST_CLEAR.put(v.getUUID(), v.position());
			return;
		}
		Vec3 back = LAST_CLEAR.get(v.getUUID());
		if (back == null) {
			return;
		}
		if (v instanceof ServerPlayer p) {
			p.connection.teleport(back.x, back.y, back.z, p.getYRot(), p.getXRot());
			Long warned = WARNED.get(p.getUUID());
			if (warned == null || level.getGameTime() - warned > 60L) {
				WARNED.put(p.getUUID(), level.getGameTime());
				p.sendOverlayMessage(Component.translatable(why));
			}
		} else {
			v.teleportTo(back.x, back.y, back.z);
			if (v instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		v.setDeltaMovement(Vec3.ZERO);
		level.playSound(null, v.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.4F);
	}

	/** A villager who lives in the home near an unexposed vampire mob asks it in. */
	private static boolean askedIn(ServerLevel level, Mob vampire, long key) {
		for (Villager v : level.getEntitiesOfClass(Villager.class, vampire.getBoundingBox().inflate(VILLAGER_NEAR), x -> x.isAlive() && !x.isBaby())) {
			if (resident(level, v, key)) {
				return true;
			}
		}
		return false;
	}

	/** Flowing water at the feet, or any water of a river: not to be crossed on foot. */
	static boolean runningWater(ServerLevel level, BlockPos pos) {
		var fluid = level.getFluidState(pos);
		if (fluid.is(Fluids.FLOWING_WATER)) {
			return true;
		}
		return fluid.is(Fluids.WATER) && level.getBiome(pos).is(BiomeTags.IS_RIVER);
	}

	// ---- the coffin -------------------------------------------------------------------------------------------------

	/** A bed boxed in by dark oak slabs. */
	public static boolean coffin(ServerLevel level, BlockPos bed) {
		if (!level.getBlockState(bed).is(BlockTags.BEDS)) {
			return false;
		}
		int slabs = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (level.getBlockState(bed.offset(dx, 0, dz)).is(Blocks.DARK_OAK_SLAB)) {
					slabs++;
				}
			}
		}
		return slabs >= COFFIN_SLABS;
	}

	/** A stake into a vampire asleep in its coffin: true if it was struck dead (the blow itself does nothing). */
	public static boolean stakeAsleep(ServerLevel level, LivingEntity vampire) {
		if (!vampire.isSleeping() || vampire.getSleepingPos().isEmpty() || !coffin(level, vampire.getSleepingPos().get())) {
			return false;
		}
		level.sendParticles(com.jaymoskingdoms.Fx.wisp(0x5A0A0A, 1.4F), vampire.getX(), vampire.getY(0.5), vampire.getZ(), 30, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, vampire.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 0.8F, 1.5F);
		TO_KILL.add(vampire);
		return true;
	}

	/** By day a vampire mob near a coffin lies down in it. */
	private static void toBed(ServerLevel level, Mob mob) {
		long t = level.getOverworldClockTime() % 24000L;
		if (t > 12000L && t < 23000L || mob.isSleeping() || mob.getTarget() != null || Vampires.exposed(mob)) {
			return;
		}
		BlockPos at = mob.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-COFFIN_REACH, -4, -COFFIN_REACH), at.offset(COFFIN_REACH, 4, COFFIN_REACH))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.BEDS) && coffin(level, p) && level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p).inflate(0.5),
				x -> x != mob && x.isSleeping()).isEmpty()) {
				BlockPos bed = p.immutable();
				mob.teleportTo(bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5);
				mob.startSleeping(bed);
				return;
			}
		}
	}
}
