package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Someone sees you suffering (docs/Climate-Plan.md, track F: vl-notice). A settlement's villager or guard who sees a player
 * <b>freezing</b> (Kitchen's body heat at or below its FREEZING) comes over and does something real about it, by temperament:
 * <ul>
 * <li><b>a seat by their fire</b>: they say so and lead you to the nearest lit fire (a campfire, a bonfire, any block Kitchen's
 * tag {@code heat_sources/flame} lists, {@link HearthFolk#fireNear}), waiting when you fall behind, and say so when you're there;</li>
 * <li><b>a blanket</b>: the kind-hearted and the cheerful pull a woollen shirt ({@link WoolClothes#SHIRT_ITEM}) out of their own
 * chest and give it you, liking you the better for taking it. Each gives one only now and then ({@link #BLANKET_GAP}).</li>
 * </ul>
 * And a <b>guard</b> who sees you <b>overheating</b> in a hot land under the noon sun ({@link #NOON_FROM} to {@link #NOON_TO}, open sky,
 * not in desert garb that shades you) comes over, tells you to get out of it and leads you to the nearest shade.
 * <p>
 * Bubbles only, no chat, with a gesture instead of words for one who doesn't share your tongue. The cold-hearted
 * (the spiteful and hot-headed) never bother; the gloomy, proud, suspicious, greedy and timid only now and then
 * ({@link #bother}); nobody does for someone they think ill of ({@link #SOUR}), or who is the camp's captive, or whom their
 * realm wants in its cells. Those busy or talking with anyone are left be ({@link ClimateVisits#free}). It nags nobody: one
 * notice for a player every {@link #GAP} ticks, and after a villager chooses not to, {@link #REBUFF} before another tries.
 */
public final class Notices {
	/** Players are looked at this often (ticks). */
	static final int LOOK = 40;
	/** How far away they notice you, in sight (blocks). */
	static final double RANGE = 12.0;
	/** A fire is led to from at most this far from the one who offers (blocks), and shade from this far from you. */
	static final int FIRE_RANGE = 24, SHADE_RANGE = 14;
	/** Ticks before the same player is noticed again, and before another tries after a refusal. */
	static final long GAP = 20L * 180L, REBUFF = 20L * 45L;
	/** One villager's blanket is given this seldom (ticks). */
	static final long BLANKET_GAP = 24000L * 3L;
	/** Hearts below which nobody bothers. */
	static final int SOUR = -15;
	/** The noon sun, in the day's ticks. */
	static final long NOON_FROM = 4000L, NOON_TO = 9000L;
	/** How many variants each line has. */
	static final int LINES = 3;
	/** Hearts given for taking a blanket. */
	static final int BLANKET_HEARTS = 2;

	/** Player → the game time they may be noticed again. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	/** Villager → when they may give a blanket again. */
	private static final Map<UUID, Long> BLANKETS = new HashMap<>();

	private Notices() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Notices::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEXT.clear();
			BLANKETS.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || Downed.isDowned(player) || PowersCompat.fainted(player)
				|| ClimateVisits.busy(player.getUUID()) || Approaches.engaged(player.getUUID())) {
				continue;
			}
			long now = level.getGameTime();
			if (NEXT.getOrDefault(player.getUUID(), 0L) > now) {
				continue;
			}
			float heat = KitchenCompat.bodyHeat(player);
			if (Float.isNaN(heat)) {
				continue;
			}
			if (heat <= KitchenCompat.freezing()) {
				cold(level, player, now);
			} else if (heat >= KitchenCompat.overheating()) {
				sun(level, player, now);
			}
		}
	}

	/** Whether {@code m} would bother with {@code player} at all (they've nothing against them, and bind them to nothing). */
	private static boolean notices(Mob m, ServerPlayer player) {
		return Recollections.rememberer(m) && m.hasLineOfSight(player) && ClimateVisits.free(m) && !Captors.sentient(m) && !HeldBack.wants(player, m)
			&& (!(m instanceof Villager v) || McaCompat.heartsOf(v, player) >= SOUR);
	}

	/** One in {@code n} bother, by temperament: the cold-hearted never. */
	static boolean bother(Mob m) {
		Temperaments.Temperament t = Temperaments.of(m);
		if (t == null) {
			return true;
		}
		float chance = switch (t) {
			case SPITEFUL, HOTHEADED -> 0.0F;
			case GLOOMY, PROUD, SUSPICIOUS -> 0.25F;
			case GREEDY, TIMID -> 0.5F;
			default -> 1.0F;
		};
		return m.getRandom().nextFloat() < chance;
	}

	private static @Nullable Mob nearest(ServerLevel level, ServerPlayer player, boolean guards) {
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(RANGE),
			x -> notices(x, player) && (!guards || Downed.isGuard(x)))) {
			double d = m.distanceToSqr(player);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	// ---- the cold ------------------------------------------------------------------------------------------------------

	private static void cold(ServerLevel level, ServerPlayer player, long now) {
		Mob who = nearest(level, player, false);
		if (who == null) {
			return;
		}
		if (!bother(who)) {
			NEXT.put(player.getUUID(), now + REBUFF);
			return;
		}
		Temperaments.Temperament t = Temperaments.of(who);
		boolean kind = t == Temperaments.Temperament.KINDHEARTED || t == Temperaments.Temperament.CHEERFUL || t == Temperaments.Temperament.DEVOUT;
		boolean blanket = who instanceof Villager && WoolClothes.SHIRT_ITEM != null && BLANKETS.getOrDefault(who.getUUID(), 0L) <= now;
		BlockPos fire = HearthFolk.fireNear(level, who.blockPosition(), FIRE_RANGE);
		// the kind give first, the rest offer their fire first, and the greedy have no blanket to spare
		boolean giveBlanket = blanket && (kind || fire == null) && t != Temperaments.Temperament.GREEDY;
		if (!giveBlanket && fire == null) {
			NEXT.put(player.getUUID(), now + REBUFF);
			return;
		}
		boolean started = giveBlanket ? ClimateVisits.start(player, who, new Blanket()) : ClimateVisits.start(player, who, new Seat(fire));
		if (started) {
			NEXT.put(player.getUUID(), now + GAP);
		}
	}

	private static void say(ServerLevel level, Mob mob, ServerPlayer player, String kind) {
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (!Languages.understands(player, mob)) {
			Speech.sayPlainly(level, mob, player, Component.translatable("notice.jaymoskingdoms.gesture." + kind), Speech.WHITE);
			return;
		}
		String tone = SharedHistory.toneNoGreed(mob).key();
		Speech.tell(level, mob, player, Component.translatable("notice.jaymoskingdoms." + kind + "." + tone + "." + mob.getRandom().nextInt(LINES),
			Recollections.nameTo(player, mob)), Speech.WHITE);
	}

	/** A seat by their fire: they say so, lead you to it, and say so when you're there. */
	private record Seat(BlockPos fire) implements ClimateVisits.Script {
		@Override
		public @Nullable BlockPos reach(ServerLevel level, ServerPlayer player, Mob mob) {
			say(level, mob, player, "fire");
			return Construction.standable(level, fire, 3);
		}

		@Override
		public void there(ServerLevel level, ServerPlayer player, Mob mob) {
			say(level, mob, player, "fire_there");
		}
	}

	/** A blanket: a woollen shirt from their own chest. */
	private static final class Blanket implements ClimateVisits.Script {
		@Override
		public @Nullable BlockPos reach(ServerLevel level, ServerPlayer player, Mob mob) {
			say(level, mob, player, "blanket");
			ItemStack shirt = new ItemStack(WoolClothes.SHIRT_ITEM);
			if (!player.getInventory().add(shirt)) {
				player.drop(shirt, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			BLANKETS.put(mob.getUUID(), level.getGameTime() + BLANKET_GAP);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8F, 0.9F);
			if (mob instanceof Villager v) {
				McaCompat.hearts(v, player, BLANKET_HEARTS);
			}
			return null;
		}
	}

	// ---- the sun -------------------------------------------------------------------------------------------------------

	private static void sun(ServerLevel level, ServerPlayer player, long now) {
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		BlockPos at = player.blockPosition();
		if (day < NOON_FROM || day >= NOON_TO || !level.canSeeSky(at.above()) || ClimateWear.shaded(player) || !ClimateWear.hot(level, at)) {
			return;
		}
		Mob guard = nearest(level, player, true);
		if (guard == null) {
			return;
		}
		if (!bother(guard)) {
			NEXT.put(player.getUUID(), now + REBUFF);
			return;
		}
		if (ClimateVisits.start(player, guard, new Shade())) {
			NEXT.put(player.getUUID(), now + GAP);
		}
	}

	/** A guard tells you to get out of the sun and walks you to the shade. */
	private static final class Shade implements ClimateVisits.Script {
		@Override
		public @Nullable BlockPos reach(ServerLevel level, ServerPlayer player, Mob mob) {
			BlockPos shade = HearthFolk.shadeNear(level, player.blockPosition(), SHADE_RANGE, mob.getRandom());
			say(level, mob, player, shade == null ? "sun_open" : "sun");
			return shade;
		}

		@Override
		public void there(ServerLevel level, ServerPlayer player, Mob mob) {
			say(level, mob, player, "sun_there");
		}
	}
}
