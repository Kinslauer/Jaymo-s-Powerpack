package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rivet's pulse and hack, on a tap of Z. Plain: a pulse of its metal that shuts machines down (clockwork and iron golems,
 * the future's androids, mechs, omens and workys, the ruins' sentries; the {@code jaymospowers:machines} tag adds more)
 * for several seconds, and seizes every other living thing near for a moment (the stun Darktide uses). Sneaking: a hack
 * that opens (or, when all are open, shuts) iron doors and iron trapdoors, throws levers, presses buttons and flashes
 * redstone lamps for a moment, for the blocks the player may touch.
 */
public final class RivetPulse {
	// ---- tuning ----
	public static final double PULSE_RADIUS = 9.0;
	/** How long a machine stalls and how long anything else is seized (ticks), and the cooldown. */
	public static final int MACHINE_STALL = 160, SEIZE_TICKS = 30, PULSE_COOLDOWN = 160;
	public static final double HACK_RADIUS = 6.0;
	/** At most this many blocks a hack touches; the cooldown; how long a lamp's flash lasts (ticks). */
	public static final int HACK_MAX = 24, HACK_COOLDOWN = 60, LAMP_TICKS = 30;
	public static final int COLOR = 0x9FB4C0;

	private static final TagKey<EntityType<?>> MACHINES = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("machines"));
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	/** A lamp whose state was flipped: where, what it was, and when it goes back. */
	private record Flash(BlockPos pos, BlockState was, BlockState flipped, long until) {
	}

	private static final Map<UUID, List<Flash>> FLASHES = new HashMap<>();

	private RivetPulse() {
	}

	/** Whether {@code e} is a machine the pulse shuts down. */
	public static boolean isMachine(Entity e) {
		return Rivet.machine(e) || e instanceof com.jaymospowers.power.chrono.future.FutureAndroid || e instanceof com.jaymospowers.power.chrono.future.FutureOmen
			|| e instanceof com.jaymospowers.power.chrono.future.FutureWorky || e instanceof com.jaymospowers.aether.dungeon.SentryGolem
			|| e instanceof com.jaymospowers.aether.dungeon.DetonationSentry || e.getType().builtInRegistryHolder().is(MACHINES);
	}

	/** Z tap as Rivet: the pulse, or (sneaking) the hack. */
	public static boolean tap(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (NEXT.getOrDefault(player.getUUID(), 0L) > now) {
			return false;
		}
		boolean hack = player.isShiftKeyDown();
		boolean ok = hack ? hack(player) : pulse(player);
		if (ok) {
			NEXT.put(player.getUUID(), now + (hack ? HACK_COOLDOWN : PULSE_COOLDOWN));
		}
		return ok;
	}

	private static boolean pulse(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 0.6, 0.0);
		int machines = 0, seized = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(PULSE_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player)
				&& !(e instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwner() == player) && !player.isAlliedTo(e)
				&& e.distanceToSqr(player) <= PULSE_RADIUS * PULSE_RADIUS)) {
			boolean machine = isMachine(e);
			DarktideStatus.stun(e, machine ? MACHINE_STALL : SEIZE_TICKS);
			if (e instanceof Mob mob && mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (machine) {
				machines++;
				level.sendParticles(MagicFx.CRACKLE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, e.getBbWidth() * 0.4, e.getBbHeight() * 0.4, e.getBbWidth() * 0.4, 0.1);
			} else {
				seized++;
			}
		}
		MagicFx.ring(level, player.position(), (float)PULSE_RADIUS, COLOR);
		MagicFx.blast(level, at, 2.0F, COLOR);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.PLAYERS, 1.2F, 1.5F);
		if (machines + seized > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.pulse", machines, seized).withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	private static boolean hack(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos centre = player.blockPosition();
		int r = (int)HACK_RADIUS;
		List<BlockPos> doors = new ArrayList<>();
		List<BlockPos> others = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			if (doors.size() + others.size() >= HACK_MAX || pos.distSqr(centre) > HACK_RADIUS * HACK_RADIUS || !player.mayInteract(level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.IRON_DOOR) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER || state.is(Blocks.IRON_TRAPDOOR)) {
				doors.add(pos.immutable());
			} else if (state.getBlock() instanceof LeverBlock || state.getBlock() instanceof ButtonBlock || state.is(Blocks.REDSTONE_LAMP)) {
				others.add(pos.immutable());
			}
		}
		if (doors.isEmpty() && others.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rivet.nothing_to_hack").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		boolean open = doors.stream().anyMatch(p -> !level.getBlockState(p).getValue(BlockStateProperties.OPEN));
		for (BlockPos pos : doors) {
			BlockState state = level.getBlockState(pos);
			if (state.getValue(BlockStateProperties.OPEN) != open) {
				level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), 10);
				level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 1.2F);
				if (state.is(Blocks.IRON_DOOR)) {
					BlockPos upper = pos.above();
					BlockState top = level.getBlockState(upper);
					if (top.is(Blocks.IRON_DOOR)) {
						level.setBlock(upper, top.setValue(BlockStateProperties.OPEN, open), 10);
					}
				}
			}
		}
		long now = level.getGameTime();
		List<Flash> flashes = FLASHES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
		for (BlockPos pos : others) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.REDSTONE_LAMP)) {
				BlockState flipped = state.setValue(BlockStateProperties.LIT, !state.getValue(BlockStateProperties.LIT));
				level.setBlock(pos, flipped, 3);
				flashes.add(new Flash(pos, state, flipped, now + LAMP_TICKS));
			} else {
				state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
			}
		}
		for (BlockPos pos : doors.isEmpty() ? others : doors) {
			MagicFx.beam(level, player.position().add(0.0, 1.0, 0.0), Vec3.atCenterOf(pos), 0.05F, COLOR, 6);
		}
		MagicFx.ring(level, player.position(), (float)HACK_RADIUS, COLOR);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COPPER_GOLEM_SPAWN, SoundSource.PLAYERS, 1.0F, 1.8F);
		return true;
	}

	/** Every tick as Rivet: a flashed lamp goes back to what it was. */
	public static void tick(ServerPlayer player) {
		List<Flash> flashes = FLASHES.get(player.getUUID());
		if (flashes == null) {
			return;
		}
		long now = player.level().getGameTime();
		flashes.removeIf(f -> {
			if (now < f.until) {
				return false;
			}
			restore(player.level(), f);
			return true;
		});
		if (flashes.isEmpty()) {
			FLASHES.remove(player.getUUID());
		}
	}

	private static void restore(ServerLevel level, Flash f) {
		if (level.getBlockState(f.pos).equals(f.flipped)) {
			level.setBlock(f.pos, f.was, 3);
		}
	}

	/** Rivet is over: every flashed lamp goes back now. */
	public static void clear(ServerPlayer player) {
		List<Flash> flashes = FLASHES.remove(player.getUUID());
		if (flashes != null) {
			flashes.forEach(f -> restore(player.level(), f));
		}
		NEXT.remove(player.getUUID());
	}
}
