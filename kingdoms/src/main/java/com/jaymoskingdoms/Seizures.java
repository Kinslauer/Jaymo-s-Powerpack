package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Werewolf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.185) Andy: "I want the vampire and werewolf to have their own system similar to the symbiote takeovers, slowly
 * losing yourself to the blood or the curse." The hold itself, shared by both: the blood ({@link BloodFever}) or the
 * wolf ({@link FeralCurse}) takes the wheel and drives you at someone. Your client walks you there (no key does what it
 * would: each new press is a heave against it, filling a bar that drains on its own); here, in reach, the vampire's
 * mouth or the beast's claws do the rest. Fill the bar and you tear yourself free. A vampire can also stop fighting
 * and give in ({@code U}).
 *
 * <p>A third hold has no prey: the wolf tearing out of you on a night that isn't the full moon ({@link Kind#CHANGE}).
 * You stand there doubled over, fighting it down; lose, and the change comes.
 */
public final class Seizures {
	/** Once in reach (blocks), it strikes or drinks. */
	static final double REACH = 2.6;
	/** The beast's blows, this many ticks apart. */
	static final int BLOW_EVERY = 12;
	/** For the first ticks it has you, presses don't count yet (it seizes you before you can fight it). */
	static final int GRACE = 20;
	/** The prey lost (dead, gone, too far: blocks) for this long, and it lets go. */
	static final double LOST_RANGE = 48.0;
	static final int LOST_TICKS = 60;

	/** What has you. */
	public enum Kind {
		/** The blood fever (a vampire). */
		FEVER,
		/** The wolf (a werewolf's beast). */
		FERAL,
		/** The wolf tearing its way out (no prey: you hold still and fight it down). */
		CHANGE;

		public static Kind of(int id) {
			Kind[] all = values();
			return all[Math.floorMod(id, all.length)];
		}
	}

	/** How a hold ended. */
	enum End {
		/** You tore free. */
		BROKE_FREE,
		/** It had what it wanted (fed, or its prey fell). */
		SATED,
		/** It ran out of time or lost its prey. */
		LOST,
		/** Cut short: death, leaving, the curse lifted. */
		CUT
	}

	/**
	 * Server → player: a hold on you ({@code target}: who it drives you at, your own id for none; -1: it let go),
	 * {@code ticks} it lasts, {@code needed} presses to break it, {@code fade} a tick, and whether you may give in.
	 */
	public record Seize(int kind, int target, int ticks, int needed, float fade, boolean giveIn) implements CustomPacketPayload {
		public static final Type<Seize> TYPE = new Type<>(JaymosKingdoms.id("seize"));
		public static final StreamCodec<FriendlyByteBuf, Seize> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Seize::kind,
			ByteBufCodecs.VAR_INT, Seize::target, ByteBufCodecs.VAR_INT, Seize::ticks, ByteBufCodecs.VAR_INT, Seize::needed, ByteBufCodecs.FLOAT,
			Seize::fade, ByteBufCodecs.BOOL, Seize::giveIn, Seize::new);

		@Override
		public Type<Seize> type() {
			return TYPE;
		}
	}

	/** Player → server: a heave against the hold. */
	public record Struggle() implements CustomPacketPayload {
		public static final Type<Struggle> TYPE = new Type<>(JaymosKingdoms.id("seize_struggle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Struggle> CODEC = StreamCodec.unit(new Struggle());

		@Override
		public Type<Struggle> type() {
			return TYPE;
		}
	}

	/** Player → server: they stop fighting it ({@code U}). */
	public record GiveIn() implements CustomPacketPayload {
		public static final Type<GiveIn> TYPE = new Type<>(JaymosKingdoms.id("seize_give_in"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GiveIn> CODEC = StreamCodec.unit(new GiveIn());

		@Override
		public Type<GiveIn> type() {
			return TYPE;
		}
	}

	/** A hold under way. */
	static final class Hold {
		final Kind kind;
		int target;
		int ticks;
		final int needed;
		final float fade;
		final boolean canGiveIn;
		final long since;
		float struggle;
		long lastPress = -1L;
		boolean given;
		long nextBlow;
		int lost;
		int bites;

		Hold(Kind kind, int target, int ticks, int needed, float fade, boolean canGiveIn, long since) {
			this.kind = kind;
			this.target = target;
			this.ticks = ticks;
			this.needed = needed;
			this.fade = fade;
			this.canGiveIn = canGiveIn;
			this.since = since;
		}
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();

	private Seizures() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Seize.TYPE, Seize.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Struggle.TYPE, Struggle.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(GiveIn.TYPE, GiveIn.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Struggle.TYPE, (payload, context) -> struggle(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(GiveIn.TYPE, (payload, context) -> giveIn(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(Seizures::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				end(player, End.CUT);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HOLDS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOLDS.clear());
	}

	/** Whether something has {@code player} right now. */
	public static boolean held(ServerPlayer player) {
		return HOLDS.containsKey(player.getUUID());
	}

	static @Nullable Hold hold(ServerPlayer player) {
		return HOLDS.get(player.getUUID());
	}

	/** It takes the wheel: drives {@code player} at {@code target} (null: holds them where they stand, {@link Kind#CHANGE}). */
	static void start(ServerPlayer player, Kind kind, @Nullable LivingEntity target, int ticks, int needed, float fade, boolean canGiveIn) {
		if (HOLDS.containsKey(player.getUUID())) {
			return;
		}
		int id = target == null ? player.getId() : target.getId();
		long now = player.level().getGameTime();
		Hold hold = new Hold(kind, id, ticks, needed, fade, canGiveIn, now);
		hold.nextBlow = now + GRACE / 2;
		HOLDS.put(player.getUUID(), hold);
		if (player.isPassenger()) {
			player.stopRiding();
		}
		ServerPlayNetworking.send(player, new Seize(kind.ordinal(), id, ticks, needed, fade, canGiveIn));
		player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F,
			kind == Kind.FEVER ? 1.3F : 0.8F);
	}

	/** It lets go (and the curse that had them hears how). */
	static void end(ServerPlayer player, End how) {
		Hold hold = HOLDS.remove(player.getUUID());
		if (hold == null) {
			return;
		}
		if (player.connection != null) {
			ServerPlayNetworking.send(player, new Seize(hold.kind.ordinal(), -1, 0, 0, 0.0F, false));
		}
		switch (hold.kind) {
			case FEVER -> BloodFever.released(player, hold, how);
			case FERAL -> FeralCurse.released(player, hold, how);
			case CHANGE -> FeralCurse.heldBack(player, hold, how);
		}
	}

	private static void struggle(ServerPlayer player) {
		Hold hold = HOLDS.get(player.getUUID());
		long now = player.level().getGameTime();
		// (one press a tick counts, and none in the first moment)
		if (hold == null || hold.given || hold.lastPress == now || now - hold.since < GRACE) {
			return;
		}
		hold.lastPress = now;
		hold.struggle += 1.0F;
		if (hold.struggle >= hold.needed) {
			end(player, End.BROKE_FREE);
		}
	}

	private static void giveIn(ServerPlayer player) {
		Hold hold = HOLDS.get(player.getUUID());
		if (hold != null && hold.canGiveIn && !hold.given) {
			hold.given = true;
			hold.struggle = 0.0F;
			// (the bar's gone from their screen: nothing left to fight)
			ServerPlayNetworking.send(player, new Seize(hold.kind.ordinal(), hold.target, hold.ticks, 0, 0.0F, false));
			BloodFever.gaveIn(player);
		}
	}

	/** Its hold on {@code player} turns to {@code next} (its last prey down). */
	static void retarget(ServerPlayer player, Hold hold, LivingEntity next, int ticks) {
		hold.target = next.getId();
		hold.ticks = Math.max(hold.ticks, ticks);
		hold.lost = 0;
		ServerPlayNetworking.send(player, new Seize(hold.kind.ordinal(), hold.target, hold.ticks, hold.given ? 0 : hold.needed,
			hold.given ? 0.0F : hold.fade, hold.canGiveIn && !hold.given));
	}

	private static void tick(MinecraftServer server) {
		if (HOLDS.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Hold hold = HOLDS.get(player.getUUID());
			if (hold == null) {
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
				end(player, End.CUT);
				continue;
			}
			hold.struggle = Math.max(0.0F, hold.struggle - hold.fade);
			if (--hold.ticks <= 0) {
				end(player, End.LOST);
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (hold.kind == Kind.CHANGE) {
				if (!FeralCurse.stillCursed(player)) {
					end(player, End.CUT);
				} else {
					Lycans.convulse(level, player, now - hold.since);
				}
				continue;
			}
			if (hold.kind == Kind.FEVER ? !Vampires.isVampire(player) : !WerewolfPlayers.beastUp(player)) {
				end(player, End.CUT);
				continue;
			}
			Entity e = level.getEntity(hold.target);
			if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceTo(player) > LOST_RANGE) {
				// (it fell: it's had what it came for, unless it wants more)
				boolean fell = e instanceof LivingEntity dead && !dead.isAlive();
				LivingEntity next = hold.kind == Kind.FEVER ? BloodFever.nextPrey(player, hold) : FeralCurse.nextPrey(player, hold);
				if (next != null) {
					retarget(player, hold, next, 0);
				} else if (fell) {
					end(player, End.SATED);
				} else if (++hold.lost > LOST_TICKS) {
					end(player, End.LOST);
				}
				continue;
			}
			hold.lost = 0;
			if (target.distanceTo(player) > REACH || now < hold.nextBlow) {
				continue;
			}
			if (hold.kind == Kind.FEVER) {
				hold.nextBlow = now + VampirePlayers.DRINK_EVERY;
				hold.bites++;
				VampirePlayers.drink(level, player, target);
				if (BloodFever.sated(player) || !target.isAlive()) {
					end(player, End.SATED);
				}
			} else {
				hold.nextBlow = now + BLOW_EVERY;
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				WerewolfPlayers.clawAt(player);
				player.attack(target);
				player.resetAttackStrengthTicker();
			}
		}
	}

	/** Whether {@code e} is a player's own beast (never its prey). */
	static boolean ownBeast(Entity e) {
		return e instanceof Werewolf w && WerewolfPlayers.puppet(w);
	}
}
