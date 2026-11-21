package com.jaymospowers.power.cryo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Glacier Rampart (Andy's Awakening pick: Cryokinesis). Sneak + X, once every ability is learned: a glacier surges
 * {@link #LENGTH} blocks out along your look, a block a tick, climbing a block every {@link #RISE_EVERY} as it goes, so it is a ramp
 * you can walk up. Every foe it reaches is shut in ice for {@link #FREEZE_TICKS} (4 s, {@link CryoFlashFreeze#encase}). It
 * stands {@link #STAND_TICKS} (12 s) and then shatters all at once. Like all of Cryokinesis' ice it only goes into air, water,
 * snow and plants, never into a creature, and nothing is left behind ({@link CryoIce}). Shares the Ice Wall's cooldown.
 */
public final class CryoRampart {
	// ---- tuning ----
	/** How far (blocks), how wide, and a block higher every this many blocks. */
	static final int LENGTH = 20, WIDTH = 3, RISE_EVERY = 3;
	/** How long a foe it reaches is frozen, and how long the glacier stands (ticks). */
	static final int FREEZE_TICKS = 80, STAND_TICKS = 240;
	/** The wait after (ticks). */
	static final int COOLDOWN = 600;
	/** How far up or down (blocks) the surge follows the ground. */
	static final int STEP = 3;

	private static final class Surge {
		final ServerPlayer owner;
		final ServerLevel level;
		final Vec3 dir;
		final BlockPos start;
		final List<BlockPos> ice = new ArrayList<>();
		final long until;
		int done;
		int groundY;

		Surge(ServerPlayer owner, Vec3 dir, BlockPos start, long until) {
			this.owner = owner;
			this.level = owner.level();
			this.dir = dir;
			this.start = start;
			this.until = until;
			this.groundY = start.getY();
		}
	}

	private static final List<Surge> SURGES = new ArrayList<>();

	private CryoRampart() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(CryoRampart::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SURGES.clear());
	}

	/** Sneak + X: the glacier, if every ability is learned and the wall's wait is over. */
	static void surge(ServerPlayer player) {
		if (!CryoPower.mastered(player) && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.locked").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!CryoPower.ready(player, CryoState.WALL)) {
			return;
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 dir = new Vec3(look.x, 0.0, look.z);
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		ServerLevel level = player.level();
		SURGES.add(new Surge(player, dir, player.blockPosition(), level.getGameTime() + LENGTH + STAND_TICKS));
		CryoPower.cooldown(player, CryoState.WALL, COOLDOWN);
		CryoMoves.begin(player, CryoMoves.WALL);
		com.jaymospowers.core.Mastery.used(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.5F, 0.4F);
		com.jaymospowers.core.Shakes.around(level, player.position(), 12.0, 0.5F);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Surge> it = SURGES.iterator(); it.hasNext();) {
			Surge s = it.next();
			if (s.done < LENGTH) {
				grow(s);
				continue;
			}
			if (s.level.getGameTime() >= s.until) {
				it.remove();
				shatter(s);
			}
		}
	}

	/** One more block of glacier. */
	private static void grow(Surge s) {
		s.done++;
		ServerLevel level = s.level;
		Vec3 at = Vec3.atBottomCenterOf(s.start).add(s.dir.scale(s.done + 0.5));
		// follow the ground: the first open block over solid ground within a few blocks of the last
		BlockPos column = BlockPos.containing(at.x, s.groundY, at.z);
		BlockPos ground = null;
		for (int dy = STEP; dy >= -STEP; dy--) {
			BlockPos p = column.above(dy);
			if (open(level, p) && !open(level, p.below())) {
				ground = p;
				break;
			}
		}
		if (ground == null) {
			ground = column;
		}
		s.groundY = ground.getY();
		int height = 1 + s.done / RISE_EVERY;
		int ticks = (int)(s.until - level.getGameTime());
		Vec3 side = new Vec3(-s.dir.z, 0.0, s.dir.x);
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		for (int w = -WIDTH / 2; w <= WIDTH / 2; w++) {
			BlockPos base = BlockPos.containing(at.x + side.x * w, ground.getY(), at.z + side.z * w);
			for (int up = 0; up < height; up++) {
				BlockPos pos = base.above(up);
				if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty()) {
					continue; // never into a creature
				}
				if (CryoIce.place(level, pos, ice, ticks, CryoIce.Mode.OPEN)) {
					s.ice.add(pos.immutable());
				}
			}
			level.sendParticles(ParticleTypes.SNOWFLAKE, base.getX() + 0.5, base.getY() + height, base.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.05);
		}
		// whoever it reaches is frozen in it
		Vec3 front = new Vec3(at.x, ground.getY() + height * 0.5, at.z);
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(WIDTH * 0.5 + 0.5, height * 0.5 + 1.5, WIDTH * 0.5 + 0.5),
			e -> foe(s.owner, e))) {
			CryoFlashFreeze.encase(s.owner, foe, Math.round(FREEZE_TICKS * CryoPower.strength(s.owner)));
		}
		if (s.done % 2 == 0) {
			level.playSound(null, front.x, front.y, front.z, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.8F, 0.5F + s.done * 0.03F);
		}
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced() && !state.getFluidState().isSource() || CryoIce.ours(level, pos) && state.is(Blocks.PACKED_ICE);
	}

	private static boolean foe(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof TamableAnimal pet && pet.isOwnedBy(owner))
			&& !(e instanceof Player other && (other.isCreative() || !owner.canHarmPlayer(other)));
	}

	/** It all breaks at once. */
	private static void shatter(Surge s) {
		CryoIce.meltNow(s.level, s.ice, s.until);
		BlockParticleOption shards = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
		for (int i = 0; i < s.ice.size(); i += 3) {
			BlockPos p = s.ice.get(i);
			s.level.sendParticles(shards, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
		}
		Vec3 mid = Vec3.atCenterOf(s.start).add(s.dir.scale(LENGTH * 0.5));
		s.level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0F, 0.5F);
		s.level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5F, 0.6F);
	}
}
