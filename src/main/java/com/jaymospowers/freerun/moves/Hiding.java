package com.jaymospowers.freerun.moves;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Ledge;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hiding in leaves or hay (after ParCool's hide-in-block): on the ground, hold Sneak and walk into a leaves block or a
 * hay bale ({@link #HIDE_IN}) at your feet for {@link #ENTER_TICKS} and you squeeze into it, sunk down so the block
 * covers you. Hidden, mobs lose track of you (they drop you as a target, and you count as nearly unseen:
 * {@code HidingVisibilityMixin}, so only one right beside you notices), and your name tag doesn't show. Jump climbs back
 * out; so does taking damage, the block going (broken, burnt), or using or swinging anything.
 * <p>The client pins the body in place and asks ({@code RunMovesClient}); the server checks the block and puts you there.
 * Inside a solid hay bale you neither suffocate ({@code HidingWallMixin}) nor get pushed out, and your view isn't
 * covered by the block's texture (the client mixins), so you can peek out.
 */
public final class Hiding {
	// ===== the knobs ================================================================================================
	/** Ticks of Sneak held, pressing into the block, before you're in. */
	public static final int ENTER_TICKS = 12;
	/** How far below the block's floor the feet sink (blocks), so a standing body is under its top. */
	public static final double SINK = 0.8;
	/** Server: the block must be this close (blocks, centre to feet); moving this far from the spot ends it. */
	static final double REACH = 1.6, DRIFT = 0.6;
	/** Every this many ticks, mobs close by that still hunt you are told to forget you, out to this range (blocks). */
	static final int FORGET_EVERY = 10;
	static final double FORGET_RANGE = 32.0;
	// ================================================================================================================

	/** What one can hide in: leaves and hay bales. */
	public static final TagKey<Block> HIDE_IN = TagKey.create(Registries.BLOCK, JaymosPowers.id("freerun_hide_in"));

	/** On a hidden player: the block they're in (synced to everyone: the pin, the name tag, the eye inside the block). */
	public static final AttachmentType<BlockPos> HIDDEN = AttachmentRegistry.create(JaymosPowers.id("hidden_in"),
		builder -> builder.syncWith(BlockPos.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Server: where each hidden player stood before, to step back out to. */
	private static final Map<UUID, Vec3> FROM = new HashMap<>();

	private Hiding() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Hiding::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && hidden(player)) {
				leave(player);
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> broke(player));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> broke(player));
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> broke(player));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> broke(player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (hidden(handler.player)) {
				leave(handler.player);
			}
			FROM.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> FROM.clear());
	}

	/** Whether {@code player} is hiding in a block (any side). */
	public static boolean hidden(Player player) {
		return player.hasAttached(HIDDEN);
	}

	/** The block {@code player} hides in, or null. */
	public static @Nullable BlockPos spot(Player player) {
		return player.getAttached(HIDDEN);
	}

	/** Where the feet of someone hiding in {@code pos} are kept. */
	public static Vec3 anchor(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() - SINK, pos.getZ() + 0.5);
	}

	/** Whether one could hide in this block. */
	public static boolean hideable(BlockState state) {
		return state.is(HIDE_IN);
	}

	/** Using or swinging anything gives you away (server; the client's own use is let through the same way). */
	private static InteractionResult broke(Player player) {
		if (player instanceof ServerPlayer server && hidden(server)) {
			leave(server);
		}
		return InteractionResult.PASS;
	}

	static void hide(ServerPlayer player, long packed) {
		BlockPos pos = BlockPos.of(packed);
		ServerLevel level = player.level();
		BlockState state = level.getBlockState(pos);
		Vec3 feet = player.position();
		if (hidden(player) || !player.isShiftKeyDown() || !player.onGround() || !Ledge.canHang(player) || !hideable(state)
			|| Math.abs(pos.getY() - feet.y) > 0.6 || Vec3.atBottomCenterOf(pos).distanceTo(feet) > REACH) {
			return;
		}
		FROM.put(player.getUUID(), feet);
		player.setAttached(HIDDEN, pos);
		Vec3 at = anchor(pos);
		player.teleportTo(at.x, at.y, at.z);
		player.resetFallDistance();
		rustle(level, pos, state);
		forget(level, player);
	}

	/** Out again: back where you stood if there's room, otherwise up on top of the block. */
	static void leave(ServerPlayer player) {
		BlockPos pos = spot(player);
		player.removeAttached(HIDDEN);
		Vec3 from = FROM.remove(player.getUUID());
		if (pos == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 out = from != null && level.noCollision(player, player.getBoundingBox().move(from.subtract(player.position()))) ? from
			: Vec3.atBottomCenterOf(pos.above());
		player.teleportTo(out.x, out.y, out.z);
		player.resetFallDistance();
		rustle(level, pos, level.getBlockState(pos));
	}

	private static void rustle(ServerLevel level, BlockPos pos, BlockState state) {
		SoundType sound = state.getSoundType();
		level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, sound.getVolume() * 0.6F, sound.getPitch() * 0.9F);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			BlockPos pos = spot(player);
			if (pos == null) {
				continue;
			}
			player.resetFallDistance();
			if (!player.isAlive() || !hideable(player.level().getBlockState(pos)) || player.position().distanceTo(anchor(pos)) > DRIFT
				|| player.isPassenger() || player.isSpectator()) {
				leave(player);
				continue;
			}
			if (server.getTickCount() % FORGET_EVERY == 0) {
				forget(player.level(), player);
			}
		}
	}

	/** Mobs hunting {@code player} lose them. */
	private static void forget(ServerLevel level, ServerPlayer player) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FORGET_RANGE))) {
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
				&& mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t == player).isPresent()) {
				mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
		}
	}
}
