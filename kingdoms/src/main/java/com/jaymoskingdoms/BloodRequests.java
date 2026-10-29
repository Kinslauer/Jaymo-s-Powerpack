package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * (0.7.332) Requests, the vampire's side: about once a day the blood asks one thing of its host, in a line on the action
 * bar. By night: {@link #DAWN} (drink before dawn) or {@link #FRIEND} (feed on someone you know: a player). By day:
 * {@link #SUN} (stay out of the sun today) or {@link #PRIEST} (stay away from the priest: no cleric within
 * {@link #PRIEST_RANGE} blocks). Keeping to it before the deadline eases the fever and the surrender a little
 * ({@link #KEPT_EASE}, {@link #KEPT_SURRENDER}); ignoring it raises the fever ({@link #BROKEN_RISE}). Kept (it persists
 * with the player). No screen entry: only the action bar.
 */
public final class BloodRequests {
	public static final String NONE = "", DAWN = "dawn", FRIEND = "friend", SUN = "sun", PRIEST = "priest";
	/** What the blood is asking now (kind), when it's due (Overworld clock), when it may ask again (clock), and whether it's been broken. */
	record State(String kind, long deadline, long nextAt, boolean broken) {
		static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(State::kind),
			Codec.LONG.fieldOf("deadline").forGetter(State::deadline),
			Codec.LONG.fieldOf("next_at").forGetter(State::nextAt),
			Codec.BOOL.fieldOf("broken").forGetter(State::broken)).apply(i, State::new));
	}

	/** A day (ticks) between requests, from the one before being asked. */
	static final long EVERY = 24000L;
	/** Not asked with less than this left (ticks) before the deadline. */
	static final long MIN_LEFT = 3000L;
	/** A cleric this close (blocks) breaks "stay away from the priest". */
	static final double PRIEST_RANGE = 24.0;
	/** Keeping to it: the fever eases this much, the surrender this many. Breaking it: the fever climbs this much. */
	static final float KEPT_EASE = 8.0F, BROKEN_RISE = 12.0F;
	static final int KEPT_SURRENDER = 2;

	static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("blood_request"),
		builder -> builder.persistent(State.CODEC).copyOnDeath());

	private BloodRequests() {
	}

	/** The request now under way ({@link #NONE} for none). */
	public static String current(ServerPlayer player) {
		State s = player.getAttached(STATE);
		return s == null ? NONE : s.kind();
	}

	/** Every second, for a vampire with a fever. */
	static void tick(ServerPlayer player, long clock) {
		State s = player.getAttached(STATE);
		if (s == null) {
			s = new State(NONE, 0L, 0L, false);
		}
		boolean night = Vampires.night(clock);
		if (!s.kind().isEmpty()) {
			switch (s.kind()) {
				case SUN -> {
					if (!night && VampirePlayers.sunlit(player)) {
						s = broke(player, s);
					}
				}
				case PRIEST -> {
					if (cleric(player)) {
						s = broke(player, s);
					}
				}
				default -> {
				}
			}
			if (!s.kind().isEmpty() && clock >= s.deadline()) {
				// the time's up: the avoiding kinds were kept, the feeding kinds weren't
				boolean kept = (SUN.equals(s.kind()) || PRIEST.equals(s.kind())) && !s.broken();
				s = kept ? kept(player, s) : broke(player, s);
			}
			save(player, s);
			return;
		}
		if (clock < s.nextAt()) {
			return;
		}
		long today = Math.floorDiv(clock, 24000L) * 24000L;
		long t = clock - today;
		long deadline;
		List<String> kinds = new ArrayList<>();
		if (night) {
			deadline = today + (t >= Vampires.NIGHT_FROM ? Vampires.NIGHT_TO : Vampires.NIGHT_TO - 24000L);
			kinds.add(DAWN);
			if (anyoneElse(player)) {
				kinds.add(FRIEND);
			}
		} else {
			deadline = today + Vampires.NIGHT_FROM + (t >= Vampires.NIGHT_TO ? 24000L : 0L);
			kinds.add(SUN);
			kinds.add(PRIEST);
		}
		if (deadline - clock < MIN_LEFT) {
			return; // too late to ask; it asks next time round
		}
		String kind = kinds.get(player.getRandom().nextInt(kinds.size()));
		save(player, new State(kind, deadline, clock + EVERY, false));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_request." + kind).withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	/** A bite (from {@link VampirePlayers#drink}) on {@code target}. */
	static void fed(ServerPlayer player, LivingEntity target) {
		State s = player.getAttached(STATE);
		if (s == null || s.kind().isEmpty()) {
			return;
		}
		if (DAWN.equals(s.kind()) || FRIEND.equals(s.kind()) && target instanceof ServerPlayer) {
			save(player, kept(player, s));
		}
	}

	private static State kept(ServerPlayer player, State s) {
		BloodFever.grant(player, -KEPT_EASE, -KEPT_SURRENDER);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_request.kept").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		return new State(NONE, 0L, s.nextAt(), false);
	}

	private static State broke(ServerPlayer player, State s) {
		BloodFever.grant(player, BROKEN_RISE, 0);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_request.broken").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		return new State(NONE, 0L, s.nextAt(), true);
	}

	private static void save(ServerPlayer player, State s) {
		if (s.kind().isEmpty() && s.nextAt() == 0L) {
			return;
		}
		if (!s.equals(player.getAttached(STATE))) {
			player.setAttached(STATE, s);
		}
	}

	private static boolean anyoneElse(ServerPlayer player) {
		for (ServerPlayer other : player.level().players()) {
			if (other != player && Vampires.mortal(other)) {
				return true;
			}
		}
		return false;
	}

	private static boolean cleric(ServerPlayer player) {
		ServerLevel level = player.level();
		return !level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(PRIEST_RANGE),
			v -> v.isAlive() && !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.CLERIC)).isEmpty();
	}
}
