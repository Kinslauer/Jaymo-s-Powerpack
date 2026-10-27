package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.17.53, Andy's NOEA picks) The five-way fusion, a late ritual. Five Z Fighters stand in a ring (about {@link #RING_MIN}
 * to {@link #RING_MAX} blocks from the ring's middle, no gap wider than {@link #MAX_GAP} degrees), every one of them with
 * Super 3 open (past Super 2, {@link #GATE_FORMS}) and free to fuse; each taps Z, then C, within {@link #AGREE_TICKS} of the
 * others (the dance's own keys: a ring of five near him takes his Z then C for agreeing instead of the dance, once he
 * qualifies). When the fifth agrees, they become one huge body ({@link #SCALE} bigger) for {@link #FIVE_TICKS}: the first to
 * agree steers, the other four watch from inside as spectators on his camera, as the dance's partner does. The body has all
 * five's fists, health, ki and ki power added together and {@link #FIVE_BONUS} more; there is no change of places. Every one of
 * the five is then worn out for {@link #FIVE_COOLDOWN} (and for the dance's {@link ZFighterFusion#COOLDOWN} besides).
 * The two-way machinery ({@link ZFighterFusion#begin}) does the rest, forms burning the timer included.
 */
public final class ZFighterFiveFusion {
	static final int FIVE = 5;
	/** Everyone must have this many forms open (3: Super 3, which is past Super 2). */
	static final int GATE_FORMS = 3;
	/** How near (blocks) the others must be for a ring to be meant at all. */
	static final double NEAR = 12.0;
	/** Each stands this far from the ring's middle (blocks), at least and at most. */
	static final double RING_MIN = 1.5, RING_MAX = 8.0;
	/** The widest empty arc (degrees) the ring may leave between neighbours (a true five is 72). */
	static final double MAX_GAP = 150.0;
	/** All five must have agreed within this long (ticks) of one another. */
	static final int AGREE_TICKS = 100;
	/** How long the five stay one (4 minutes), what they have on top of the five added together, how much bigger the body is (1: twice), and the rest after. */
	static final int FIVE_TICKS = 4800;
	static final float FIVE_BONUS = 0.35F;
	static final double SCALE = 1.0;
	static final long FIVE_COOLDOWN = 48000L;

	/** When he last took part in a five-way fusion (game time). */
	public static final AttachmentType<Long> FIVE_LAST = AttachmentRegistry.create(JaymosPowers.id("zfighter_five_fusion_last"),
		builder -> builder.persistent(Codec.LONG));

	/** Who has agreed, and when. */
	private static final Map<UUID, Long> AGREED = new HashMap<>();

	private ZFighterFiveFusion() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> AGREED.clear());
	}

	/** Whether {@code player} is ready for this ritual (not counting the rest after the last one). */
	private static boolean ready(ServerPlayer player, long now) {
		if (!ZFighterFusion.fusable(player) || ZFighterTraining.formsOpen(player) < GATE_FORMS) {
			return false;
		}
		Long last = player.getAttached(FIVE_LAST);
		return player.isCreative() || last == null || now - last >= FIVE_COOLDOWN;
	}

	/** Four ready fighters near {@code player} besides him (they need not have agreed). */
	private static List<ServerPlayer> neighbours(ServerPlayer player, long now) {
		List<ServerPlayer> near = new ArrayList<>();
		for (ServerPlayer other : player.level().players()) {
			if (other != player && other.distanceTo(player) <= NEAR && ready(other, now)) {
				near.add(other);
			}
		}
		return near;
	}

	/**
	 * Z then C was pressed by {@code player}. If he and at least four more ready fighters are near, this is an agreement to
	 * the five-way (true: the dance is not done); the fifth agreement in a good ring fuses them.
	 */
	static boolean agree(ServerPlayer player, long now) {
		if (!ready(player, now)) {
			return false;
		}
		List<ServerPlayer> near = neighbours(player, now);
		if (near.size() < FIVE - 1) {
			return false;
		}
		AGREED.put(player.getUUID(), now);
		ZFighterFusion.pose(player, 1, now);
		AGREED.values().removeIf(at -> now - at > AGREE_TICKS);
		List<ServerPlayer> agreed = new ArrayList<>();
		agreed.add(player);
		for (ServerPlayer other : near) {
			if (AGREED.containsKey(other.getUUID())) {
				agreed.add(other);
			}
		}
		if (agreed.size() < FIVE) {
			Component said = Component.translatable("message.jaymospowers.zfighter_five_agree", agreed.size(), FIVE).withStyle(ChatFormatting.AQUA);
			for (ServerPlayer one : agreed) {
				one.sendOverlayMessage(said);
			}
			return true;
		}
		// the five who agreed first lead the ring (the earliest steers)
		agreed.sort(Comparator.comparingLong(p -> AGREED.get(p.getUUID())));
		List<ServerPlayer> five = new ArrayList<>(agreed.subList(0, FIVE));
		if (!ring(five)) {
			Component said = Component.translatable("message.jaymospowers.zfighter_five_ring").withStyle(ChatFormatting.GRAY);
			for (ServerPlayer one : five) {
				one.sendOverlayMessage(said);
			}
			return true;
		}
		for (ServerPlayer one : five) {
			AGREED.remove(one.getUUID());
		}
		fuse(five, now);
		return true;
	}

	/** Whether the five stand round a middle, each in {@link #RING_MIN}-{@link #RING_MAX}, with no gap wider than {@link #MAX_GAP}. */
	static boolean ring(List<ServerPlayer> five) {
		double cx = 0.0, cz = 0.0;
		for (ServerPlayer p : five) {
			cx += p.getX() / FIVE;
			cz += p.getZ() / FIVE;
		}
		List<Double> angles = new ArrayList<>();
		for (ServerPlayer p : five) {
			double dx = p.getX() - cx, dz = p.getZ() - cz;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d < RING_MIN || d > RING_MAX) {
				return false;
			}
			angles.add(Math.toDegrees(Math.atan2(dz, dx)));
		}
		angles.sort(Double::compare);
		for (int i = 0; i < FIVE; i++) {
			double next = i == FIVE - 1 ? angles.get(0) + 360.0 : angles.get(i + 1);
			if (next - angles.get(i) > MAX_GAP) {
				return false;
			}
		}
		return true;
	}

	/** The first of {@code five} steers; the other four go inside. */
	private static void fuse(List<ServerPlayer> five, long now) {
		ServerPlayer leader = five.get(0);
		List<ServerPlayer> inside = new ArrayList<>(five.subList(1, FIVE));
		ZFighterFusion.begin(leader, inside, ZFighterFusion.Kind.FIVE, FIVE_TICKS, FIVE_BONUS, null);
		for (ServerPlayer one : five) {
			one.setAttached(FIVE_LAST, now);
		}
		Component names = Component.empty();
		for (int i = 0; i < FIVE; i++) {
			names = Component.empty().append(names).append(i == 0 ? Component.empty() : Component.literal(i == FIVE - 1 ? " and " : ", "))
				.append(five.get(i).getDisplayName());
		}
		Component message = Component.translatable("message.jaymospowers.zfighter_five_fused", names).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer near : leader.level().players()) {
			if (near.distanceToSqr(leader) < 96.0 * 96.0) {
				near.sendSystemMessage(message);
			}
		}
		Component holds = Component.translatable("message.jaymospowers.zfighter_fusion_timed", (FIVE_TICKS + 1199) / 1200).withStyle(ChatFormatting.AQUA);
		for (ServerPlayer one : five) {
			one.sendOverlayMessage(holds);
		}
		com.jaymospowers.core.Shakes.around(leader.level(), leader.position(), 48.0, 2.5F);
	}
}
