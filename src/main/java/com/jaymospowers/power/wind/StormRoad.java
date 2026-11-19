package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Storm road home: sneak + the road key marks the place you stand (under open sky). Later the key alone calls a column
 * of storm light down on you; held still in it for {@link #WINDUP} ticks, you are carried to the mark, in the same
 * world (a teleport, not a portal). Long cooldown; it fails underground, at either end of the call.
 */
final class StormRoad {
	private static final String TABLE = "wind_storm";
	private static final int SLOT = 6;
	/** Ticks of the wind-up you can see, the cooldown after a call, hunger. */
	static final int WINDUP = 60, COOLDOWN = 6000;
	static final float EXHAUSTION = 6.0F;
	static final int MIN_FOOD = 6;
	/** How high the column stands (blocks), and how far over the mark a safe footing is looked for. */
	static final double COLUMN = 48.0;
	static final int SEARCH_UP = 4;

	private record Mark(String dimension, BlockPos pos) {
		static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Mark::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Mark::pos)
		).apply(i, Mark::new));
	}

	/** Where the player marked the road. */
	private static final AttachmentType<Mark> MARK = AttachmentRegistry.create(JaymosPowers.id("wind_storm_road"),
		builder -> builder.persistent(Mark.CODEC).copyOnDeath());

	private record Windup(Vec3 from, long until) {
	}

	private static final Map<UUID, Windup> WINDUPS = new HashMap<>();

	private StormRoad() {
	}

	static boolean marked(ServerPlayer player) {
		return player.getAttached(MARK) != null;
	}

	static void press(ServerPlayer player) {
		ServerLevel level = player.level();
		if (WINDUPS.containsKey(player.getUUID())) {
			return;
		}
		if (!level.canSeeSky(player.blockPosition())) {
			StormNet.say(player, "road_underground");
			return;
		}
		if (player.isShiftKeyDown()) {
			player.setAttached(MARK, new Mark(level.dimension().identifier().toString(), player.blockPosition()));
			MagicFx.sigil(level, player.position().add(0.0, 0.1, 0.0), 2.5F, StormDraw.GLOW, 40);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
			StormNet.say(player, "road_marked");
			return;
		}
		Mark mark = player.getAttached(MARK);
		if (mark == null) {
			StormNet.say(player, "road_none");
			return;
		}
		if (!mark.dimension.equals(level.dimension().identifier().toString())) {
			StormNet.say(player, "road_world");
			return;
		}
		if (!PickKit.ready(TABLE, player, SLOT)) {
			StormNet.say(player, "cooling");
			return;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			StormNet.say(player, "hungry");
			return;
		}
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		WINDUPS.put(player.getUUID(), new Windup(player.position(), level.getGameTime() + WINDUP));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.6F);
	}

	static void tick(ServerPlayer player) {
		Windup w = WINDUPS.get(player.getUUID());
		if (w == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Mark mark = player.getAttached(MARK);
		if (mark == null || !mark.dimension.equals(level.dimension().identifier().toString())) {
			cancel(player);
			return;
		}
		if (player.isShiftKeyDown() && w.until - now < WINDUP - 10) {
			cancel(player);
			StormNet.say(player, "road_stopped");
			return;
		}
		// held in the light, and lifted a little
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(0.0, Math.max(v.y, 0.0) + 0.03, 0.0);
		player.syncVelocity = true;
		player.resetFallDistance();
		double progress = 1.0 - (w.until - now) / (double)WINDUP;
		if (now % 3 == 0) {
			Vec3 foot = player.position();
			MagicFx.beam(level, foot.add(0.0, COLUMN, 0.0), foot, (float)(0.3 + progress * 0.7), StormDraw.GLOW, 6);
			MagicFx.ring(level, foot.add(0.0, 0.1, 0.0), null, (float)(2.4 - progress * 1.4), StormDraw.BOLT, 8);
			Vec3 there = Vec3.atBottomCenterOf(mark.pos);
			if (level.hasChunkAt(mark.pos)) {
				MagicFx.beam(level, there.add(0.0, COLUMN, 0.0), there, (float)(0.2 + progress * 0.5), StormDraw.GLOW, 6);
			}
		}
		if (now >= w.until) {
			arrive(player, level, mark, w);
		}
	}

	private static void arrive(ServerPlayer player, ServerLevel level, Mark mark, Windup w) {
		WINDUPS.remove(player.getUUID());
		if (!level.canSeeSky(player.blockPosition())) {
			PickKit.spend(TABLE, player, SLOT, 0);
			StormNet.say(player, "road_underground");
			return;
		}
		Vec3 dest = null;
		for (int up = 0; up <= SEARCH_UP && dest == null; up++) {
			Vec3 feet = new Vec3(mark.pos.getX() + 0.5, mark.pos.getY() + up, mark.pos.getZ() + 0.5);
			AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
			if (level.noCollision(player, box)) {
				dest = feet;
			}
		}
		if (dest == null) {
			PickKit.spend(TABLE, player, SLOT, 0);
			StormNet.say(player, "road_blocked");
			return;
		}
		Vec3 from = player.position();
		MagicFx.blast(level, from.add(0.0, 1.0, 0.0), 3.0F, StormDraw.BOLT);
		Shakes.around(level, from, 20.0, 0.8F);
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), player.getYRot(), player.getXRot(), false);
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = true;
		player.resetFallDistance();
		player.causeFoodExhaustion(EXHAUSTION);
		MagicFx.blast(level, dest.add(0.0, 1.0, 0.0), 3.0F, StormDraw.BOLT);
		MagicFx.beam(level, dest.add(0.0, COLUMN, 0.0), dest, 1.0F, StormDraw.GLOW, 12);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.6F, 0.9F);
		Shakes.around(level, dest, 20.0, 0.8F);
	}

	private static void cancel(ServerPlayer player) {
		WINDUPS.remove(player.getUUID());
		PickKit.spend(TABLE, player, SLOT, 0);
	}

	static void forget(ServerPlayer player) {
		if (WINDUPS.remove(player.getUUID()) != null) {
			PickKit.spend(TABLE, player, SLOT, 0);
		}
	}
}
