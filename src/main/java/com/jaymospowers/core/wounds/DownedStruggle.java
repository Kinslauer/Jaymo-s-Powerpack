package com.jaymospowers.core.wounds;

import com.jaymospowers.compat.SpookyDoorsCompat;
import com.jaymospowers.core.Carry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.49) Andy: "every downed right click should be a 3s struggle ... making the door push open slowly for every second
 * holding, right clicking a bed while downed hauls you into it, so you can recover there without the need for meds or
 * another player, it just takes time, more the more you are hurt". And: "you get back up whenever you want, but you recover
 * the whole time".
 * <ul>
 * <li><b>The struggle:</b> a downed player who is still awake ({@link Downed#awake}) holds use on a block for
 * {@link #STRUGGLE_TICKS}: the bar fills (the repeats of held use, every 4 ticks, are the hold going on) and then the block
 * works as if the hand were empty. Let go and the bar keeps where it got to (Andy: "every meter should work this way"),
 * unless they turn to another block or leave it {@link #FORGET_TICKS}. With Spooky Doors in the pack
 * ({@link SpookyDoorsCompat}) a door swings a little further every moment it's pushed, shut ones open and open ones shut.</li>
 * <li><b>A bed:</b> the struggle ends with them hauled up onto it, resting. Lying there (within {@link #REST_REACH} of the
 * spot) they never pass out, and every {@link #REST_INTERVAL} ticks their most hurt part mends {@link #REST_HEAL}, up to
 * {@link #REST_CAP} of it, so the more hurt, the longer it takes. After {@link #REST_SET_TICKS} in bed a broken bone sets
 * itself (no splint needed). Getting up is the usual sneak, any time they're well enough
 * ({@link Downed#tooHurt}); they rise with what the parts hold ({@link Wounds#restedHealth}). Crawling off ends the rest.</li>
 * </ul>
 * Fires are {@link Resting}'s (the campfire rest of the standing); a downed player resting by one is not this class.
 */
public final class DownedStruggle {
	// ---- tuning ----
	/** How long a downed player struggles with a block before it works (ticks). */
	public static final int STRUGGLE_TICKS = 60;
	/** No repeat of held use for this long and the hold is let go (vanilla repeats every 4 ticks). */
	static final int GRACE_TICKS = 8;
	/** A struggle left this long (ticks) is forgotten: the next one starts from nothing. */
	public static final int FORGET_TICKS = 200;
	/** The struggle's bar length. */
	static final int BAR = 12;
	/** In bed: ticks between mends, what each mends (health points), and the share of a part rest mends it to. */
	public static final int REST_INTERVAL = 40;
	public static final float REST_HEAL = 1.0F;
	public static final float REST_CAP = 1.0F;
	/** In bed this long (ticks, a minute) and a broken bone sets itself; 0 never. */
	public static final int REST_SET_TICKS = 1200;
	/** How far (blocks) from where they were laid they may shift and still be resting. */
	public static final double REST_REACH = 1.2;
	/** How often the resting are told how they're doing (ticks). */
	static final int REST_HINT_TICKS = 40;
	/**
	 * (0.21.49) Andy: "the bleeding can hold up to two cuts by holding still. any others will still bleed. taking into
	 * account each bleeding entity's mB". Lying still in bed a downed player holds this many open cuts shut (no blood lost
	 * from them); every other cut pours as a downed body does, each its own share ({@code BleedOut}, by the body's own
	 * blood). Moving, every cut bleeds. Their blood run dry, they pass out where they lie.
	 */
	public static final int HELD_CUTS = 2;
	/** The most they may shift in a second (blocks, squared) and still be holding still (as {@link Resting}'s). */
	static final double STILL_DISTANCE_SQR = Resting.STILL_DISTANCE_SQR;
	/** How high on a bed a body lies (the bed's top). */
	static final double BED_TOP = 0.5625;

	private static final Map<UUID, Struggle> STRUGGLES = new HashMap<>();
	private static final Map<UUID, Rest> RESTS = new HashMap<>();

	private DownedStruggle() {
	}

	private static final class Struggle {
		final BlockPos pos;
		/** How far a door was open when the struggle began (-1: not a Spooky door). */
		final float doorFrom;
		int ticks;
		long last;

		Struggle(BlockPos pos, float doorFrom, long now) {
			this.pos = pos;
			this.doorFrom = doorFrom;
			this.last = now;
		}
	}

	private static final class Rest {
		final BlockPos bed;
		final Vec3 spot;
		final long since;
		/** Where they lay a second ago, and whether they've kept still since (holding their cuts). */
		Vec3 last;
		boolean still;

		Rest(BlockPos bed, Vec3 spot, long since) {
			this.bed = bed;
			this.spot = spot;
			this.since = since;
			this.last = spot;
		}
	}

	// ---- the struggle (server, from Downed's last right-click say on a block) --------------------------------------------

	/** One repeat of a downed, awake player's held use on a block. */
	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof CakeBlock || state.getBlock() instanceof CandleCakeBlock) {
			// (no eating while down)
			return InteractionResult.FAIL;
		}
		if (state.getBlock() instanceof AbstractBedBlock && RESTS.containsKey(player.getUUID())) {
			// (already in it)
			return InteractionResult.FAIL;
		}
		boolean door = state.getBlock() instanceof DoorBlock && SpookyDoorsCompat.loaded();
		if (door && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			pos = pos.below();
		}
		long now = level.getGameTime();
		Struggle s = STRUGGLES.get(player.getUUID());
		if (s == null || !s.pos.equals(pos) || now - s.last > FORGET_TICKS) {
			s = new Struggle(pos.immutable(), door ? SpookyDoorsCompat.percentOpen(level, pos) : -1.0F, now);
			STRUGGLES.put(player.getUUID(), s);
		}
		long gap = now - s.last;
		s.ticks += gap > 0 && gap <= GRACE_TICKS ? (int) gap : 1;
		s.last = now;
		float done = Math.min(1.0F, s.ticks / (float) STRUGGLE_TICKS);
		if (s.doorFrom >= 0.0F) {
			// pushed a little further every moment: a shut door opens, an open one shuts
			float to = s.doorFrom >= 0.5F ? 0.0F : 1.0F;
			SpookyDoorsCompat.operate(level, pos, player, s.doorFrom + (to - s.doorFrom) * done);
		}
		if (s.ticks % 20 < 4) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.5F,
				0.6F + level.getRandom().nextFloat() * 0.3F);
		}
		if (done < 1.0F) {
			int filled = Math.round(BAR * done);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.struggle", "|".repeat(filled) + ".".repeat(BAR - filled))
				.withStyle(ChatFormatting.YELLOW));
			return InteractionResult.SUCCESS_SERVER;
		}
		STRUGGLES.remove(player.getUUID());
		if (state.getBlock() instanceof AbstractBedBlock) {
			intoBed(level, player, pos, state);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (s.doorFrom >= 0.0F && Math.abs(SpookyDoorsCompat.percentOpen(level, pos) - s.doorFrom) > 0.01F) {
			// (Spooky Doors swung it all the way)
			return InteractionResult.SUCCESS_SERVER;
		}
		// the block as if the hand were empty: the held item is never used, placed or eaten from
		InteractionResult used = level.getBlockState(hit.getBlockPos()).useWithoutItem(level, player, hit);
		return used.consumesAction() ? used : InteractionResult.FAIL;
	}

	// ---- the bed ---------------------------------------------------------------------------------------------------------

	private static void intoBed(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		// the middle of the bed, both halves: its foot half points along FACING to the head
		Direction facing = state.getValue(AbstractBedBlock.FACING);
		boolean head = state.getValue(AbstractBedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
		Direction toOther = head ? facing.getOpposite() : facing;
		Vec3 spot = Vec3.atBottomCenterOf(pos).add(toOther.getStepX() * 0.5, BED_TOP, toOther.getStepZ() * 0.5);
		if (Carry.carrierOf(player) instanceof ServerPlayer carrier) {
			Carry.slip(carrier);
		}
		player.teleportTo(level, spot.x, spot.y, spot.z, java.util.Set.of(), facing.toYRot(), player.getXRot(), false);
		RESTS.put(player.getUUID(), new Rest(pos.immutable(), spot, level.getGameTime()));
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.bed").withStyle(ChatFormatting.GREEN));
	}

	/**
	 * How many of a resting player's cuts are bleeding: every open cut (a severed stump counts as two) less the
	 * {@link #HELD_CUTS} they hold while lying still. -1 if they aren't resting (a downed body's usual bleeding).
	 */
	public static int bleedingCuts(ServerPlayer player) {
		Rest rest = RESTS.get(player.getUUID());
		if (rest == null) {
			return -1;
		}
		int stumps = Bleeding.stumps(player);
		int open = Integer.bitCount(Bleeding.bleeding(player) & ~stumps) + 2 * Integer.bitCount(stumps);
		return Math.max(0, open - (rest.still ? HELD_CUTS : 0));
	}

	/** Whether this downed player lies resting in a bed. */
	public static boolean resting(ServerPlayer player) {
		return RESTS.containsKey(player.getUUID());
	}

	/**
	 * Downed's tick for one downed player, before anything else of theirs: a rest goes on (true) or ends; a struggle left
	 * too long is forgotten.
	 */
	static boolean tick(ServerLevel level, ServerPlayer player, long now) {
		Struggle s = STRUGGLES.get(player.getUUID());
		if (s != null && now - s.last > FORGET_TICKS) {
			STRUGGLES.remove(player.getUUID());
		}
		Rest rest = RESTS.get(player.getUUID());
		if (rest == null) {
			return false;
		}
		if (player.level() != level || !(level.getBlockState(rest.bed).getBlock() instanceof AbstractBedBlock)
			|| player.position().distanceTo(rest.spot) > REST_REACH || Carry.isCarried(player) || !Downed.awake(player)) {
			// crawled off, carried off, the bed gone, or knocked out
			RESTS.remove(player.getUUID());
			return false;
		}
		if (now % 20 == 0) {
			rest.still = rest.last.distanceToSqr(player.position()) < STILL_DISTANCE_SQR;
			rest.last = player.position();
		}
		if (com.jaymospowers.core.blood.BleedOut.left(player) <= 0.01F) {
			// bled dry in bed: out cold where they lie
			RESTS.remove(player.getUUID());
			player.setAttached(Downed.OUT, true);
			return false;
		}
		// in bed: the wounds' clock held at the end of the adrenaline, so they don't bleed out toward passing out (only
		// their open cuts take blood now, {@link #bleedingCuts})
		Downed.holdClock(player, now);
		if (now % 20 == 0) {
			Pain.ease(player, Resting.EASE_TICKS);
		}
		long lain = now - rest.since;
		if (lain > 0 && lain % REST_INTERVAL == 0) {
			mendWorst(player);
		}
		if (REST_SET_TICKS > 0 && lain == REST_SET_TICKS) {
			BodyPlan plan = Wounds.plan(player);
			for (int i = 0; plan != null && i < plan.size(); i++) {
				Breaks.setPart(player, plan.part(i));
			}
		}
		if (now % REST_HINT_TICKS == 0) {
			BodyPart worst = Downed.blocksRise(player);
			player.sendOverlayMessage((worst == null ? Component.translatable("message.jaymospowers.downed.bed_ready")
				: Component.translatable("message.jaymospowers.downed.bed_resting", worst.displayName())).withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/** The most hurt part that can still mend, by {@link #REST_HEAL}. */
	private static void mendWorst(ServerPlayer player) {
		BodyPlan plan = Wounds.plan(player);
		if (plan == null) {
			return;
		}
		@Nullable BodyPart worst = null;
		float lowest = REST_CAP - 0.001F;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			float f = Wounds.fraction(player, part);
			if (!Breaks.missing(player, part) && f < lowest){
				// (a part capped by its break can't mend past it: only pick one with room)
				float[] caps = Breaks.caps(player, plan);
				if (caps == null || f < caps[i] - 0.001F) {
					worst = part;
					lowest = f;
				}
			}
		}
		if (worst != null) {
			Wounds.restPart(player, worst, REST_HEAL, REST_CAP);
		}
	}

	/** They're up, gone or dead: nothing of theirs is kept. */
	static void forget(UUID player) {
		STRUGGLES.remove(player);
		RESTS.remove(player);
	}
}
