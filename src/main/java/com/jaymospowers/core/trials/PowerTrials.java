package com.jaymospowers.core.trials;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) Power trials: every power has a ladder of {@link #LADDER} sixteen trials (use each of its abilities, land
 * blows, fell foes and a great one, take punishment, reach the deep, the heights, the Nether and the End, go far). Each
 * finished trial is {@link #POINTS} mastery points, so the ladder unlocks the power's moves beside mastery by use.
 * {@link #OPEN} trials are open at once, in ladder order (a trial whose ability isn't yours yet waits); pin one to see it on
 * the HUD, or swap one to the back of the ladder for one of {@link #MOST_SWAPS} swaps, which finishing any quest refills
 * (Kingdoms' quests, a Z Fighter master's jobs).
 * <p>Lore: nobody sets them. They are the holder's own thoughts of how they could push their limits, heard as a line in
 * the mind (an overlay, never chat) when one comes to them and when one is done. The trials page in the H menu lists them.
 */
public final class PowerTrials {
	// ---- tuning ----
	/** Mastery points a finished trial gives (16 of them: 400, nearly every ability's 420). */
	public static final int POINTS = 25;
	/** Trials open at once, and the most swaps held (a fresh ladder starts full). */
	public static final int OPEN = 3, MOST_SWAPS = 3;
	/** The places: the deep (at or below), the heights (at or above), in the Overworld. */
	static final int DEEP_Y = -40, HIGH_Y = 200;
	/** A great foe: at least this much health at full (or a boss to Kingdoms). */
	static final float BIG_HEALTH = 100.0F;
	/** Places and travel are checked this often (ticks). */
	static final int CHECK_TICKS = 20;

	/** What a trial asks. */
	public enum Kind {
		USE, HIT, KILL, KILL_POWER, KILL_BIG, HURT, DEEP, HIGH, NETHER, END, TRAVEL;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** One trial: what, how many, and (for {@link Kind#USE}) which of the power's abilities (0 the starter, 1-3 in unlock order). */
	public record Trial(Kind kind, int need, int ability) {
	}

	/** The ladder, the same shape for every power (its abilities' names fill it in). */
	public static final List<Trial> LADDER = List.of(
		new Trial(Kind.USE, 10, 0), new Trial(Kind.HIT, 25, -1), new Trial(Kind.DEEP, 1, -1), new Trial(Kind.USE, 10, 1),
		new Trial(Kind.KILL, 10, -1), new Trial(Kind.HURT, 30, -1), new Trial(Kind.USE, 15, 2), new Trial(Kind.NETHER, 1, -1),
		new Trial(Kind.TRAVEL, 3000, -1), new Trial(Kind.USE, 15, 3), new Trial(Kind.KILL_POWER, 10, -1), new Trial(Kind.HIGH, 1, -1),
		new Trial(Kind.KILL, 40, -1), new Trial(Kind.KILL_BIG, 1, -1), new Trial(Kind.END, 1, -1), new Trial(Kind.KILL_POWER, 30, -1));

	/**
	 * The ladder as it stands, synced to its own player: {@code [power, done bits, deferred bits, swaps, pinned, open x3,
	 * progress x3]}. Saved; kept through death; a new power starts a new ladder.
	 */
	public static final AttachmentType<List<Integer>> STATE = AttachmentRegistry.create(JaymosPowers.id("power_trials"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(16)), AttachmentSyncPredicate.targetOnly()));

	/** Client → server: pin (0) or swap (1) open trial {@code slot}. */
	public record Act(int action, int slot) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosPowers.id("trial_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.action);
			buf.writeVarInt(p.slot);
		}, buf -> new Act(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	/** The ladder's state, unpacked. */
	public static final class State {
		public int power, done, deferred, swaps = MOST_SWAPS, pinned = -1;
		public final int[] open = {-1, -1, -1}, progress = new int[OPEN];

		static State read(@Nullable List<Integer> saved) {
			State s = new State();
			if (saved == null || saved.size() < 5 + OPEN * 2) {
				return null;
			}
			s.power = saved.get(0);
			s.done = saved.get(1);
			s.deferred = saved.get(2);
			s.swaps = saved.get(3);
			s.pinned = saved.get(4);
			for (int i = 0; i < OPEN; i++) {
				s.open[i] = saved.get(5 + i);
				s.progress[i] = saved.get(5 + OPEN + i);
			}
			return s;
		}

		List<Integer> write() {
			List<Integer> out = new ArrayList<>(List.of(power, done, deferred, swaps, pinned));
			for (int i = 0; i < OPEN; i++) {
				out.add(open[i]);
			}
			for (int i = 0; i < OPEN; i++) {
				out.add(progress[i]);
			}
			return List.copyOf(out);
		}

		public int doneCount() {
			return Integer.bitCount(done);
		}

		boolean isOpen(int trial) {
			for (int o : open) {
				if (o == trial) {
					return true;
				}
			}
			return false;
		}
	}

	private static final Map<UUID, Vec3> LAST_AT = new HashMap<>();

	private PowerTrials() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, context) -> act(context.player(), payload.action(), payload.slot()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0F) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer player && entity != player) {
				progress(player, Kind.HIT, 1, null);
			}
			if (entity instanceof ServerPlayer player && player.isAlive()) {
				progress(player, Kind.HURT, Math.max(1, Math.round(taken)), null);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer player) || entity == player) {
				return;
			}
			if (entity instanceof Enemy) {
				progress(player, Kind.KILL, 1, null);
				if (Powers.actor() == player) {
					progress(player, Kind.KILL_POWER, 1, null);
				}
			}
			if (entity.getMaxHealth() >= BIG_HEALTH || KingdomsCompat.boss(entity)) {
				progress(player, Kind.KILL_BIG, 1, null);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PowerTrials::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_AT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST_AT.clear());
	}

	// ---- reading (both sides) ----

	/** The power's four abilities in trial order: its starter first, then the rest as they unlock. */
	public static PowerKey[] abilities(Power power) {
		List<PowerKey> out = new ArrayList<>();
		if (power.starterKey() != null) {
			out.add(power.starterKey());
		}
		for (PowerKey key : power.unlockOrder()) {
			if (!out.contains(key)) {
				out.add(key);
			}
		}
		for (PowerKey key : PowerKey.values()) {
			if (!out.contains(key)) {
				out.add(key);
			}
		}
		return out.subList(0, 4).toArray(new PowerKey[0]);
	}

	/** {@code player}'s ladder as synced (null: none yet, or for another power). */
	public static @Nullable State state(Player player) {
		Power power = Powers.of(player);
		State s = State.read(player.getAttached(STATE));
		return power == null || s == null || s.power != power.id().hashCode() ? null : s;
	}

	/** What trial {@code index} asks, in {@code power}'s words. */
	public static Component describe(Power power, int index) {
		Trial trial = LADDER.get(index);
		if (trial.kind() == Kind.USE) {
			PowerKey key = abilities(power)[trial.ability()];
			Component name = Component.translatable("power." + power.id().getNamespace() + "." + power.id().getPath() + ".key." + key.id() + ".name");
			return Component.translatable("trial.jaymospowers.use", name, trial.need());
		}
		return Component.translatable("trial.jaymospowers." + trial.kind().key(), trial.need());
	}

	// ---- the ladder (server) ----

	private static @Nullable State live(ServerPlayer player) {
		Power power = Powers.of(player);
		if (power == null || player.isSpectator()) {
			return null;
		}
		State s = State.read(player.getAttached(STATE));
		if (s == null || s.power != power.id().hashCode()) {
			s = new State();
			s.power = power.id().hashCode();
			fill(player, s, false);
			player.setAttached(STATE, s.write());
		}
		return s;
	}

	/** Can {@code player} work at trial {@code index} now (an ability trial waits for the ability)? */
	private static boolean possible(ServerPlayer player, int index) {
		Trial trial = LADDER.get(index);
		Power power = Powers.of(player);
		return trial.kind() != Kind.USE || power != null && Mastery.isUsable(player, abilities(power)[trial.ability()]);
	}

	/** Opens the next trials into the empty slots: ladder order first, the swapped-away ones after. */
	private static void fill(ServerPlayer player, State s, boolean think) {
		for (int slot = 0; slot < OPEN; slot++) {
			if (s.open[slot] >= 0) {
				continue;
			}
			int next = -1;
			for (int pass = 0; pass < 2 && next < 0; pass++) {
				for (int i = 0; i < LADDER.size(); i++) {
					boolean deferred = (s.deferred >> i & 1) != 0;
					if ((s.done >> i & 1) == 0 && !s.isOpen(i) && deferred == (pass == 1) && possible(player, i)) {
						next = i;
						break;
					}
				}
			}
			if (next < 0) {
				continue;
			}
			s.open[slot] = next;
			s.progress[slot] = 0;
			s.deferred &= ~(1 << next);
			if (think) {
				Power power = Powers.of(player);
				if (power != null) {
					player.sendOverlayMessage(Component.translatable("trial.jaymospowers.thought.open", describe(power, next))
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
			}
		}
	}

	/** {@code amount} more toward every open trial of {@code kind} (for {@link Kind#USE}, of the ability on {@code key}). */
	public static void progress(ServerPlayer player, Kind kind, int amount, @Nullable PowerKey key) {
		if (player.isCreative()) {
			return;
		}
		State s = live(player);
		Power power = Powers.of(player);
		if (s == null || power == null) {
			return;
		}
		boolean changed = false, finished = false;
		for (int slot = 0; slot < OPEN; slot++) {
			int index = s.open[slot];
			if (index < 0) {
				continue;
			}
			Trial trial = LADDER.get(index);
			if (trial.kind() != kind || kind == Kind.USE && abilities(power)[trial.ability()] != key) {
				continue;
			}
			s.progress[slot] = Math.min(trial.need(), s.progress[slot] + amount);
			changed = true;
			if (s.progress[slot] >= trial.need()) {
				s.done |= 1 << index;
				s.open[slot] = -1;
				s.progress[slot] = 0;
				finished = true;
				Mastery.grant(player, POINTS);
				player.sendOverlayMessage(Component.translatable("trial.jaymospowers.thought.done", describe(power, index))
					.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
			}
		}
		if (finished) {
			fill(player, s, false);
		}
		if (changed) {
			player.setAttached(STATE, s.write());
		}
	}

	/** An ability key pressed and answered ({@code Powers.key}). Also opens an ability trial the moment its ability is learned. */
	public static void used(ServerPlayer player, PowerKey key) {
		State s = live(player);
		if (s != null && emptySlot(s)) {
			fill(player, s, true);
			player.setAttached(STATE, s.write());
		}
		progress(player, Kind.USE, 1, key);
	}

	private static boolean emptySlot(State s) {
		for (int o : s.open) {
			if (o < 0) {
				return true;
			}
		}
		return false;
	}

	/** A quest finished (Kingdoms' quests by reflection, a Z Fighter master's jobs): a swap back. */
	public static void questDone(ServerPlayer player) {
		State s = live(player);
		if (s == null || s.swaps >= MOST_SWAPS) {
			return;
		}
		s.swaps++;
		player.setAttached(STATE, s.write());
		player.sendOverlayMessage(Component.translatable("trial.jaymospowers.thought.swap_back").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static void act(ServerPlayer player, int action, int slot) {
		State s = live(player);
		if (s == null || slot < 0 || slot >= OPEN) {
			return;
		}
		if (action == 0) {
			s.pinned = s.pinned == slot ? -1 : slot;
		} else if (action == 1 && s.open[slot] >= 0 && s.swaps > 0) {
			s.deferred |= 1 << s.open[slot];
			s.open[slot] = -1;
			s.progress[slot] = 0;
			s.swaps--;
			fill(player, s, true);
		} else {
			return;
		}
		player.setAttached(STATE, s.write());
	}

	/** Places reached and ground covered, once a second. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Powers.of(player) == null || !player.isAlive() || player.isSpectator()) {
				LAST_AT.remove(player.getUUID());
				continue;
			}
			var dim = player.level().dimension();
			if (dim == Level.NETHER) {
				progress(player, Kind.NETHER, 1, null);
			} else if (dim == Level.END) {
				progress(player, Kind.END, 1, null);
			} else if (dim == Level.OVERWORLD) {
				if (player.getY() <= DEEP_Y) {
					progress(player, Kind.DEEP, 1, null);
				} else if (player.getY() >= HIGH_Y) {
					progress(player, Kind.HIGH, 1, null);
				}
			}
			Vec3 at = player.position(), last = LAST_AT.put(player.getUUID(), at);
			if (last != null && !player.isPassenger()) {
				double moved = Math.sqrt((at.x - last.x) * (at.x - last.x) + (at.z - last.z) * (at.z - last.z));
				// (a teleport isn't travel)
				if (moved >= 1.0 && moved < 200.0) {
					progress(player, Kind.TRAVEL, (int)moved, null);
				}
			}
		}
	}
}
