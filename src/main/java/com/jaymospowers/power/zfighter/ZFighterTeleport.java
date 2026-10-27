package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.45, Andy's NOEA picks) Instant Teleport, a late technique bought with TP on the training screen (10000 TP, and
 * Super 2 opened). It goes on the B key as an alternative to Flash Step: learning it puts it on B, and the technique's
 * button on the training screen swaps between the two (B is the one key a Z Fighter's short-range blink lives on, and
 * both are "go somewhere now", so they share it rather than take a fifth slot). Tap B: you are at the block you look
 * at, up to {@link #RANGE} blocks off (the clear line to it is the look that found it), on safe footing. Hold B for
 * {@link #HOLD_TICKS} ticks (a second): a list of the friends and masters whose ki you sensed (ki sense,
 * {@link ZFighterKiSense}) in the last day; pick one and you appear beside them. Costs {@link #KI} ki, and {@link #COOLDOWN} ticks (20 s)
 * on B's cooldown.
 */
public final class ZFighterTeleport {
	static final float KI = 40.0F;
	static final int COOLDOWN = 400;
	static final double RANGE = 64.0;
	/** B held this long (ticks) opens the list instead of teleporting. */
	static final int HOLD_TICKS = 20;
	/** Beside a friend: this many blocks off, tried round the compass. */
	static final double BESIDE = 2.0;
	/** How far above and below a landing spot is searched for something to stand on. */
	static final int SEARCH_UP = 1, SEARCH_DOWN = 6;
	/** Standing this far (blocks) above a floor still counts as standing on it. */
	static final double SUPPORT = 0.55;
	static final float EXHAUSTION = 1.0F;
	static final int MAX_LIST = 24;

	/** Whether Instant Teleport is on B in place of Flash Step. Saved, kept through death, synced to its owner. */
	public static final AttachmentType<Boolean> ON_B = AttachmentRegistry.create(JaymosPowers.id("zfighter_teleport_on_b"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Player → {when B went down, 1 once the list has opened}. */
	private static final Map<UUID, long[]> HOLD = new HashMap<>();

	private ZFighterTeleport() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HOLD.remove(handler.player.getUUID()));
	}

	/** Whether B is Instant Teleport for this player (it is learned and put on B). */
	public static boolean equipped(Player player) {
		return Boolean.TRUE.equals(player.getAttached(ON_B)) && ZFighterTraining.of(player).knows(ZFighterTraining.TELEPORT);
	}

	/** The training screen's button: B is Instant Teleport, or Flash Step again. */
	static void toggle(ServerPlayer player) {
		if (!ZFighterTraining.of(player).knows(ZFighterTraining.TELEPORT)) {
			return;
		}
		boolean now = !Boolean.TRUE.equals(player.getAttached(ON_B));
		setOnB(player, now);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5F, 1.2F);
	}

	/** Puts it on B (or Flash Step back), saying so. */
	static void setOnB(ServerPlayer player, boolean on) {
		if (on) {
			player.setAttached(ON_B, true);
		} else {
			player.removeAttached(ON_B);
		}
		player.sendSystemMessage(Component.translatable(on ? "message.jaymospowers.zfighter_teleport_on_b" : "message.jaymospowers.zfighter_teleport_off_b",
			Component.keybind("key.jaymospowers.ability_4")).withStyle(ChatFormatting.GRAY));
	}

	/**
	 * B went down or came up. Returns true when Instant Teleport has taken the press (it is on B); false lets Flash Step
	 * (or a Verdant's heal) have it.
	 */
	static boolean key(ServerPlayer player, ZFighterPower.Live live, boolean down) {
		if (!equipped(player)) {
			return false;
		}
		if (down && live.lineage == ZFighterLineage.VERDANT && player.isShiftKeyDown()) {
			return false;
		}
		long now = player.level().getGameTime();
		if (down) {
			if (!Powers.silenced(player)) {
				HOLD.put(player.getUUID(), new long[] {now, 0L});
			}
			return true;
		}
		long[] hold = HOLD.remove(player.getUUID());
		if (hold != null && hold[1] == 0L && now - hold[0] < HOLD_TICKS) {
			tap(player, live);
		}
		return true;
	}

	/** Every tick: B held long enough opens the list. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		long[] hold = HOLD.get(player.getUUID());
		if (hold == null || hold[1] != 0L) {
			return;
		}
		if (!equipped(player)) {
			HOLD.remove(player.getUUID());
		} else if (player.level().getGameTime() - hold[0] >= HOLD_TICKS) {
			hold[1] = 1L;
			open(player);
		}
	}

	// ---------------------------------------------------------------- the tap

	private static void tap(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.ready(player, ZFighterState.STEP)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_wait").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(RANGE));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_no_target").withStyle(ChatFormatting.GRAY));
			return;
		}
		Vec3 dest = landing(level, player, hit.getBlockPos().relative(hit.getDirection()));
		if (dest == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_no_footing").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ZFighterPower.spend(player, live, KI)) {
			return;
		}
		jump(player, live, dest, player.getYRot(), player.getXRot());
	}

	// ---------------------------------------------------------------- the list

	private static void open(ServerPlayer player) {
		if (!ZFighterPower.ready(player, ZFighterState.STEP)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_wait").withStyle(ChatFormatting.GRAY));
			return;
		}
		List<ZFighterSensesNet.Dest> dests = new ArrayList<>();
		for (Map.Entry<UUID, ZFighterKiSense.Seen> entry : ZFighterKiSense.recent(player).entrySet()) {
			ZFighterKiSense.Seen seen = entry.getValue();
			if (!entry.getKey().equals(player.getUUID())) {
				dests.add(new ZFighterSensesNet.Dest(entry.getKey(), seen.name(), seen.master(), (int)Math.round(Math.sqrt(player.distanceToSqr(seen.pos())))));
			}
		}
		if (dests.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_nobody").withStyle(ChatFormatting.GRAY));
			return;
		}
		dests.sort(Comparator.comparing(ZFighterSensesNet.Dest::master).thenComparingInt(ZFighterSensesNet.Dest::distance));
		ZFighterSensesNet.send(player, new ZFighterSensesNet.OpenTeleport(dests.size() > MAX_LIST ? dests.subList(0, MAX_LIST) : dests));
	}

	/** From the list: appear beside the one picked, if his ki was sensed within the day and the jump is ready. */
	static void to(ServerPlayer player, UUID id) {
		if (!Powers.has(player, ZFighterPower.INSTANCE) || !equipped(player) || Powers.silenced(player) || !player.isAlive()) {
			return;
		}
		ZFighterPower.Live live = ZFighterPower.live(player);
		ZFighterKiSense.Seen seen = ZFighterKiSense.recent(player).get(id);
		if (seen == null || id.equals(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_lost").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ZFighterPower.ready(player, ZFighterState.STEP)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_wait").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = player.level();
		Vec3 centre = seen.pos();
		Entity there = level.getServer().getPlayerList().getPlayer(id);
		if (there == null || there.level() != level) {
			there = level.getEntity(id);
		}
		if (there != null && there.isAlive() && there.level() == level) {
			if (there instanceof net.minecraft.world.entity.LivingEntity living && ZFighterKiSense.hidesKi(living)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_hidden").withStyle(ChatFormatting.GRAY));
				return;
			}
			centre = there.position();
		}
		Vec3 dest = null;
		for (int i = 0; i < 8 && dest == null; i++) {
			double a = i * Math.PI / 4.0;
			dest = landing(level, player, BlockPos.containing(centre.x + Math.cos(a) * BESIDE, centre.y, centre.z + Math.sin(a) * BESIDE));
		}
		if (dest == null) {
			dest = landing(level, player, BlockPos.containing(centre));
		}
		if (dest == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_teleport_no_footing").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ZFighterPower.spend(player, live, KI)) {
			return;
		}
		Vec3 toward = centre.subtract(dest);
		float yaw = (float)(Mth.atan2(toward.z, toward.x) * Mth.RAD_TO_DEG) - 90.0F;
		jump(player, live, dest, yaw, player.getXRot());
	}

	// ---------------------------------------------------------------- the jump

	private static void jump(ServerPlayer player, ZFighterPower.Live live, Vec3 dest, float yaw, float pitch) {
		ServerLevel level = player.level();
		Vec3 start = player.position();
		DustParticleOptions image = ZFighterPower.dust(live);
		for (double h = 0.1; h < player.getBbHeight(); h += 0.3) {
			level.sendParticles(image, true, false, start.x, start.y + h, start.z, 4, 0.2, 0.05, 0.2, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, true, false, start.x, start.y + 1.0, start.z, 8, 0.2, 0.4, 0.2, 0.03);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.7F);
		live.stepUntil = level.getGameTime() + 10;
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, pitch, false);
		player.resetFallDistance();
		level.sendParticles(image, true, false, dest.x, dest.y + 1.0, dest.z, 20, 0.3, 0.7, 0.3, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, true, false, dest.x, dest.y + 0.5, dest.z, 8, 0.25, 0.25, 0.25, 0.03);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8F, 1.5F);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.3F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		ZFighterPower.cooldown(player, ZFighterState.STEP, COOLDOWN);
	}

	// ---------------------------------------------------------------- safe footing

	/**
	 * Somewhere safe to stand at or near {@code spot}: the column itself first, then the four beside it, each searched from
	 * {@link #SEARCH_UP} above to {@link #SEARCH_DOWN} below for the first place the player fits with ground under it, and
	 * never in fluid, fire, cactus or on magma. Null when there is none.
	 */
	static Vec3 landing(ServerLevel level, ServerPlayer player, BlockPos spot) {
		int[][] columns = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] column : columns) {
			Vec3 found = floor(level, player, spot.offset(column[0], 0, column[1]));
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static Vec3 floor(ServerLevel level, ServerPlayer player, BlockPos column) {
		for (int dy = SEARCH_UP; dy >= -SEARCH_DOWN; dy--) {
			BlockPos feet = column.offset(0, dy, 0);
			double x = feet.getX() + 0.5, y = feet.getY(), z = feet.getZ() + 0.5;
			AABB box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
			if (!level.noCollision(player, box)) {
				continue;
			}
			if (level.noCollision(player, box.move(0.0, -SUPPORT, 0.0))) {
				continue;
			}
			return hazard(level, feet) ? null : new Vec3(x, y, z);
		}
		return null;
	}

	private static boolean hazard(ServerLevel level, BlockPos feet) {
		BlockState at = level.getBlockState(feet);
		BlockState below = level.getBlockState(feet.below());
		return !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty() || level.getFluidState(feet.below()).is(FluidTags.LAVA)
			|| at.is(BlockTags.FIRE) || at.is(Blocks.CACTUS) || at.is(Blocks.SWEET_BERRY_BUSH) || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CACTUS)
			|| below.is(BlockTags.FIRE);
	}
}
