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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) Power trials: every power has its own ladder of {@link #SIZE} trials, written for what that power does
 * ({@link TrialLadders}: feats with its own moves, and a few every power shares: fell foes and a great one, take
 * punishment, reach the deep, the heights, the Nether and the End, go far). A power's code reports a feat with one line,
 * {@link #count} or {@link #reach}. Each finished trial is {@link #POINTS} mastery points, so the ladder unlocks the
 * power's moves beside mastery by use.
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
		USE, HIT, KILL, KILL_POWER, KILL_BIG, HURT, DEEP, HIGH, NETHER, END, TRAVEL,
		/** A feat of the power's own, named by the trial's key and reported by the power ({@link #count}, {@link #reach}). */
		FEAT;

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Trials on every ladder. */
	public static final int SIZE = 16;
	/** Marks a saved ladder as one of per-power trials (a list without it is the old shared ladder, see {@link #carry}). */
	static final int FORMAT = -16;

	/**
	 * One trial: its key (what it is saved, counted and named by), what, how many, and which of the power's abilities it
	 * waits for (0 the starter, 1-3 in unlock order; -1 none).
	 */
	public record Trial(String key, Kind kind, int need, int ability) {
		/** One every power can have: {@code kill.10}, {@code nether.1}. */
		public static Trial shared(Kind kind, int need) {
			return new Trial(kind.key() + "." + need, kind, need, -1);
		}

		/** Use the ability {@code need} times. */
		public static Trial use(int ability, int need) {
			return new Trial("use." + ability + "." + need, Kind.USE, need, ability);
		}

		/** A feat of the power's own that needs none of its abilities in particular. */
		public static Trial feat(String key, int need) {
			return new Trial(key, Kind.FEAT, need, -1);
		}

		/** A feat with one of the power's abilities: it waits until that ability is learned. */
		public static Trial feat(String key, int need, int ability) {
			return new Trial(key, Kind.FEAT, need, ability);
		}
	}

	/**
	 * The ladder every power once shared (its abilities' names fill it in). A power without one of its own in
	 * {@link TrialLadders} still has it, and it is what an old save's progress is read by.
	 */
	public static final List<Trial> SHARED = List.of(
		Trial.use(0, 10), Trial.shared(Kind.HIT, 25), Trial.shared(Kind.DEEP, 1), Trial.use(1, 10),
		Trial.shared(Kind.KILL, 10), Trial.shared(Kind.HURT, 30), Trial.use(2, 15), Trial.shared(Kind.NETHER, 1),
		Trial.shared(Kind.TRAVEL, 3000), Trial.use(3, 15), Trial.shared(Kind.KILL_POWER, 10), Trial.shared(Kind.HIGH, 1),
		Trial.shared(Kind.KILL, 40), Trial.shared(Kind.KILL_BIG, 1), Trial.shared(Kind.END, 1), Trial.shared(Kind.KILL_POWER, 30));

	/**
	 * The ladder as it stands, synced to its own player: {@code [FORMAT, power, done bits, deferred bits, swaps, pinned,
	 * open x3, progress x3]} (trials by their place on the power's ladder). Saved; kept through death; a new power starts a
	 * new ladder.
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
			if (saved == null || saved.size() != 6 + OPEN * 2 || saved.get(0) != FORMAT) {
				return null;
			}
			s.power = saved.get(1);
			s.done = saved.get(2);
			s.deferred = saved.get(3);
			s.swaps = saved.get(4);
			s.pinned = saved.get(5);
			for (int i = 0; i < OPEN; i++) {
				s.open[i] = saved.get(6 + i);
				s.progress[i] = saved.get(6 + OPEN + i);
			}
			return s;
		}

		List<Integer> write() {
			List<Integer> out = new ArrayList<>(List.of(FORMAT, power, done, deferred, swaps, pinned));
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

	/** {@code power}'s ladder: its own, or the shared one. */
	public static List<Trial> ladder(Power power) {
		return TrialLadders.of(power);
	}

	/** What trial {@code index} asks, in {@code power}'s words. */
	public static Component describe(Power power, int index) {
		Trial trial = ladder(power).get(index);
		if (trial.kind() == Kind.FEAT) {
			return Component.translatable("trial.jaymospowers." + trial.key(), trial.need());
		}
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
		List<Integer> saved = player.getAttached(STATE);
		State s = State.read(saved);
		if (s == null || s.power != power.id().hashCode()) {
			s = new State();
			s.power = power.id().hashCode();
			carry(power, saved, s);
			fill(player, s, false);
			player.setAttached(STATE, s.write());
		}
		return s;
	}

	/**
	 * An old save ({@code [power, done bits, deferred bits, swaps, pinned, open x3, progress x3]} on the {@link #SHARED}
	 * ladder) for this same power, read into its new ladder {@code s} by trial key: a finished or swapped-away trial the new
	 * ladder still has stays so (it is not paid again), an open one keeps its slot and its progress, and the swaps are kept.
	 * A trial the new ladder doesn't have is dropped; the mastery it paid stays paid.
	 */
	private static void carry(Power power, @Nullable List<Integer> saved, State s) {
		if (saved == null || saved.size() != 5 + OPEN * 2 || saved.get(0) != s.power) {
			return;
		}
		List<Trial> ladder = ladder(power);
		int[] now = new int[SHARED.size()];
		for (int old = 0; old < SHARED.size(); old++) {
			now[old] = -1;
			for (int i = 0; i < ladder.size(); i++) {
				if (ladder.get(i).key().equals(SHARED.get(old).key())) {
					now[old] = i;
				}
			}
			if (now[old] >= 0) {
				s.done |= (saved.get(1) >> old & 1) << now[old];
				s.deferred |= (saved.get(2) >> old & 1) << now[old];
			}
		}
		s.swaps = Math.clamp(saved.get(3), 0, MOST_SWAPS);
		for (int slot = 0; slot < OPEN; slot++) {
			int old = saved.get(5 + slot);
			if (old >= 0 && old < now.length && now[old] >= 0 && (s.done >> now[old] & 1) == 0) {
				s.open[slot] = now[old];
				s.progress[slot] = Math.clamp(saved.get(5 + OPEN + slot), 0, ladder.get(now[old]).need() - 1);
				s.deferred &= ~(1 << now[old]);
				if (saved.get(4) == slot) {
					s.pinned = slot;
				}
			}
		}
	}

	/** Can {@code player} work at trial {@code index} now (an ability trial waits for the ability)? */
	private static boolean possible(ServerPlayer player, int index) {
		Power power = Powers.of(player);
		if (power == null) {
			return false;
		}
		Trial trial = ladder(power).get(index);
		return trial.ability() < 0 || Mastery.isUsable(player, abilities(power)[trial.ability()]);
	}

	/** Opens the next trials into the empty slots: ladder order first, the swapped-away ones after. */
	private static void fill(ServerPlayer player, State s, boolean think) {
		Power power = Powers.of(player);
		if (power == null) {
			return;
		}
		List<Trial> ladder = ladder(power);
		for (int slot = 0; slot < OPEN; slot++) {
			if (s.open[slot] >= 0) {
				continue;
			}
			int next = -1;
			for (int pass = 0; pass < 2 && next < 0; pass++) {
				for (int i = 0; i < ladder.size(); i++) {
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
				// a feat comes to mind in its own words
				Component thought = ladder.get(next).kind() == Kind.FEAT ? Component.translatable("trial.jaymospowers." + ladder.get(next).key() + ".thought")
					: Component.translatable("trial.jaymospowers.thought.open", describe(power, next));
				player.sendOverlayMessage(thought.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	/** {@code amount} more toward every open trial of {@code kind} (for {@link Kind#USE}, of the ability on {@code key}). */
	public static void progress(ServerPlayer player, Kind kind, int amount, @Nullable PowerKey key) {
		advance(player, kind, null, amount, key, false);
	}

	/**
	 * A feat done: {@code amount} more toward {@code who}'s open trial {@code key} (the power's own, as its ladder in
	 * {@link TrialLadders} names it). Nothing for anyone but a player, or with that trial not open.
	 */
	public static void count(@Nullable Entity who, String key, int amount) {
		if (who instanceof ServerPlayer player && amount > 0) {
			advance(player, Kind.FEAT, key, amount, null, false);
		}
	}

	/** A feat measured in one go (so far, so many at once): {@code who}'s open trial {@code key} keeps their best. */
	public static void reach(@Nullable Entity who, String key, int value) {
		if (who instanceof ServerPlayer player && value > 0) {
			advance(player, Kind.FEAT, key, value, null, true);
		}
	}

	private static void advance(ServerPlayer player, Kind kind, @Nullable String feat, int amount, @Nullable PowerKey key, boolean best) {
		if (player.isCreative()) {
			return;
		}
		State s = live(player);
		Power power = Powers.of(player);
		if (s == null || power == null) {
			return;
		}
		List<Trial> ladder = ladder(power);
		boolean changed = false, finished = false;
		for (int slot = 0; slot < OPEN; slot++) {
			int index = s.open[slot];
			if (index < 0) {
				continue;
			}
			Trial trial = ladder.get(index);
			if (trial.kind() != kind || kind == Kind.USE && abilities(power)[trial.ability()] != key || feat != null && !feat.equals(trial.key())) {
				continue;
			}
			int now = Math.min(trial.need(), best ? Math.max(s.progress[slot], amount) : s.progress[slot] + amount);
			if (now == s.progress[slot]) {
				continue;
			}
			s.progress[slot] = now;
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
			// (an old save is read into the new ladder here, even for one who stands still)
			live(player);
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
