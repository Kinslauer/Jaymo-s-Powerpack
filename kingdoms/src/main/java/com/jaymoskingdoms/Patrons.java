package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.509) The patron gods, part (a) (docs/Patrons-Plan.md): who a player is sworn to, their favour and tier, the swearing
 * streak, the daily decay, the deeds that feed favour, the patrons a player has met, the devout start, the boon call, and the
 * one blessing Jaymo's Powers reads by reflection ({@link #blessing}). The blessings themselves are {@link Blessings}; the
 * villagers' faiths are {@link Faiths}; the altar is {@link Shrine}. Part (b) (turning away) and (c) (temples, champions) slot
 * into {@link State#marks()} and {@link #blessed}.
 */
public final class Patrons {
	// ---- tuning ----------------------------------------------------------------------------------------------------------
	/** (0.7.509) Days of prayer at one patron's shrine, running, to be sworn. */
	/** (0.7.518) The key prefix Powers asks the patron weapons' sworn reading by. */
	static final String WEAPON_KEY = "patron_weapon/";
	public static final int SWEAR_DAYS = 3;
	public static final int FAVOUR_MAX = 100;
	/** Favour at which tiers 1 to 4 begin. */
	public static final int[] TIER_AT = {10, 30, 55, 80};
	/** Favour on swearing, and for a devout life story's start. */
	static final int FAVOUR_SWORN = 12, FAVOUR_DEVOUT = 20;
	static final int FAVOUR_PRAYER = 4, FAVOUR_OFFERING = 3;
	/** (0.7.513) Favour your own patron loses for each day you pray towards another. */
	static final int FAVOUR_STRAYED = 5;
	public static final int DEED_SMALL = 1, DEED_MID = 2, DEED_GREAT = 4;
	/** The most favour deeds give in one day. */
	static final int DEEDS_DAILY_MOST = 8;
	/** Days without favour before it slips, the slip a day, what a Devotion rank takes off, and the least it slips by. */
	public static final int GRACE_DAYS = 2, DECAY_PER_DAY = 5, DEVOTION_PER_RANK = 1, DECAY_LEAST = 2;
	/** The boon's cooldown (game ticks): three in-game days. */
	static final long BOON_COOLDOWN = 3 * 24000L;
	static final int TICK_EVERY = 20, BOOK_SCAN_EVERY = 100, SEA_DEED_TICKS = 6000;

	// ---- what's kept -------------------------------------------------------------------------------------------------------
	/** The swearing streak: the patron prayed to, the days running, and the last day. */
	public record Streak(String patron, int days, long lastDay) {
		public static final Streak NONE = new Streak("", 0, -1L);
		static final Codec<Streak> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("patron", "").forGetter(Streak::patron),
			Codec.INT.optionalFieldOf("days", 0).forGetter(Streak::days),
			Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Streak::lastDay)).apply(i, Streak::new));
	}

	/** Today's deed favour and whether today's offering has been made. */
	public record Daily(long day, int deeds, boolean offered) {
		public static final Daily NONE = new Daily(-1L, 0, false);
		static final Codec<Daily> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("day", -1L).forGetter(Daily::day),
			Codec.INT.optionalFieldOf("deeds", 0).forGetter(Daily::deeds),
			Codec.BOOL.optionalFieldOf("offered", false).forGetter(Daily::offered)).apply(i, Daily::new));
	}

	/** (0.7.509) Reserved for part (b): turning away, a shrine defiled, a rival's rite. Nothing in (a) writes it. */
	public record Mark(String kind, String patron, long day) {
		static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("kind", "").forGetter(Mark::kind),
			Codec.STRING.optionalFieldOf("patron", "").forGetter(Mark::patron),
			Codec.LONG.optionalFieldOf("day", -1L).forGetter(Mark::day)).apply(i, Mark::new));
	}

	public record State(String patron, int favour, int tier, long swornDay, Streak streak, long lastFavourDay, long decayDay, Daily daily,
		long boonReady, List<String> known, List<Mark> marks, boolean seeded) {
		public static final State EMPTY = new State("", 0, 0, -1L, Streak.NONE, -1L, -1L, Daily.NONE, 0L, List.of(), List.of(), false);
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("patron", "").forGetter(State::patron),
			Codec.INT.optionalFieldOf("favour", 0).forGetter(State::favour),
			Codec.INT.optionalFieldOf("tier", 0).forGetter(State::tier),
			Codec.LONG.optionalFieldOf("sworn_day", -1L).forGetter(State::swornDay),
			Streak.CODEC.optionalFieldOf("streak", Streak.NONE).forGetter(State::streak),
			Codec.LONG.optionalFieldOf("last_favour_day", -1L).forGetter(State::lastFavourDay),
			Codec.LONG.optionalFieldOf("decay_day", -1L).forGetter(State::decayDay),
			Daily.CODEC.optionalFieldOf("daily", Daily.NONE).forGetter(State::daily),
			Codec.LONG.optionalFieldOf("boon_ready", 0L).forGetter(State::boonReady),
			Codec.STRING.listOf().optionalFieldOf("known", List.of()).forGetter(State::known),
			Mark.CODEC.listOf().optionalFieldOf("marks", List.of()).forGetter(State::marks),
			Codec.BOOL.optionalFieldOf("seeded", false).forGetter(State::seeded)).apply(i, State::new));

		public boolean sworn() {
			return !this.patron.isEmpty();
		}

		public @Nullable Patron patronOf() {
			return Patron.byKey(this.patron);
		}

		State withDaily(Daily d) {
			return new State(patron, favour, tier, swornDay, streak, lastFavourDay, decayDay, d, boonReady, known, marks, seeded);
		}

		State withStreak(Streak s) {
			return new State(patron, favour, tier, swornDay, s, lastFavourDay, decayDay, daily, boonReady, known, marks, seeded);
		}

		State withKnown(List<String> k) {
			return new State(patron, favour, tier, swornDay, streak, lastFavourDay, decayDay, daily, boonReady, k, marks, seeded);
		}

		State withBoon(long ready) {
			return new State(patron, favour, tier, swornDay, streak, lastFavourDay, decayDay, daily, ready, known, marks, seeded);
		}

		State withSeeded() {
			return new State(patron, favour, tier, swornDay, streak, lastFavourDay, decayDay, daily, boonReady, known, marks, true);
		}

		State withoutSeeded() {
			return new State(patron, favour, tier, swornDay, streak, lastFavourDay, decayDay, daily, boonReady, known, marks, false);
		}

		State withFavour(int f, int t, long lastFavour, long decay) {
			return new State(patron, f, t, swornDay, streak, lastFavour, decay, daily, boonReady, known, marks, seeded);
		}

		State withMarks(List<Mark> m) {
			return new State(patron, favour, tier, swornDay, streak, lastFavourDay, decayDay, daily, boonReady, known, List.copyOf(m), seeded);
		}

		State sworn(Patron p, int f, long today) {
			return new State(p.key, f, tierFor(f), today, Streak.NONE, today, today, daily, boonReady, known, marks, seeded);
		}
	}

	/** On a player: their patron, favour and what they know of the others. Saved, kept through death, synced to them alone. */
	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("patron"),
		b -> b.persistent(State.CODEC).copyOnDeath().syncWith(ByteBufCodecs.fromCodec(State.CODEC), AttachmentSyncPredicate.targetOnly()));

	/** Server to player: open the patrons screen with this shrine's patron picked. */
	public record OpenShrine(BlockPos pos) implements CustomPacketPayload {
		public static final Type<OpenShrine> TYPE = new Type<>(JaymosKingdoms.id("patron_shrine"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenShrine> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, OpenShrine::pos, OpenShrine::new);

		@Override
		public Type<OpenShrine> type() {
			return TYPE;
		}
	}

	/** Player to server: call the boon. */
	public record CallBoon() implements CustomPacketPayload {
		public static final CallBoon INSTANCE = new CallBoon();
		public static final Type<CallBoon> TYPE = new Type<>(JaymosKingdoms.id("patron_boon"));
		public static final StreamCodec<RegistryFriendlyByteBuf, CallBoon> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<CallBoon> type() {
			return TYPE;
		}
	}

	/** Not kept: ticks spent aboard on the ocean, per player. */
	private static final Map<UUID, Integer> AT_SEA = new HashMap<>();

	private Patrons() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenShrine.TYPE, OpenShrine.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CallBoon.TYPE, CallBoon.CODEC);
		Turning.register();
		ServerPlayNetworking.registerGlobalReceiver(CallBoon.TYPE, (payload, context) -> callBoon(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(Patrons::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> storyStart(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AT_SEA.remove(handler.player.getUUID());
			Blessings.forget(handler.player.getUUID());
			Turning.forget(handler.player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Patrons::killed);
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
				mined(sl, sp, pos, state, blockEntity);
			}
		});
		PowersCompat.<LivingEntity>listen("com.jaymospowers.core.wounds.Downed", "BROUGHT_ROUND_LISTENERS", (player, body) -> {
			if (body instanceof Villager || body instanceof Folk) {
				deed(player, Patron.HEARTHKEEPER, DEED_MID, "brought_round");
			}
			Blessings.broughtRound(player, body);
		});
		PowersCompat.<LivingEntity>listen("com.jaymospowers.core.wounds.Treatment", "TENDED_LISTENERS", (player, body) -> {
			if (body != player) {
				deed(player, Patron.HEARTHKEEPER, DEED_SMALL, "tended");
			}
		});
		PowersCompat.onForged((smith, result) -> {
			int grade = GradedTrades.gradeIndex(result);
			if (grade >= 4) {
				deed(smith, Patron.ANVIL_FATHER, DEED_GREAT, "masterwork");
			} else if (grade == 3) {
				deed(smith, Patron.ANVIL_FATHER, DEED_MID, "perfect");
			}
		});
		PowersCompat.zoneBonus(Blessings::zoneBonus);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			AT_SEA.clear();
			Blessings.clear();
			Turning.clear();
		});
	}

	// ---- reading the state -----------------------------------------------------------------------------------------------------

	public static State state(Player player) {
		return player.getAttachedOrElse(STATE, State.EMPTY);
	}

	private static void set(ServerPlayer player, State state) {
		player.setAttached(STATE, state);
	}

	/** The patron {@code player} is sworn to, or null (the client knows too: it is synced). */
	public static @Nullable Patron of(Player player) {
		return state(player).patronOf();
	}

	/** {@code player} is sworn to {@code p} and at least at tier {@code tier}. */
	public static boolean blessed(Player player, Patron p, int tier) {
		State s = state(player);
		return s.tier >= tier && p.key.equals(s.patron);
	}

	/** (0.7.509) Read by Jaymo's Powers by reflection (MedicineSkill): a blessing's strength for this player, 0 if they lack it. */
	public static float blessing(Player player, String key) {
		// (0.7.518) Jaymo's Powers' patron weapons: 1 sworn to that patron (tier 1 or more), -1 in its trials (turned from it), else 0
		if (key.startsWith(WEAPON_KEY)) {
			Patron p = Patron.byKey(key.substring(WEAPON_KEY.length()));
			if (p == null) {
				return 0.0F;
			}
			return Turning.trial(player, p) > 0.0F ? -1.0F : blessed(player, p, 1) ? 1.0F : 0.0F;
		}
		if ("gentle_hands".equals(key)) {
			return blessed(player, Patron.HEARTHKEEPER, 2) ? Blessings.GENTLE_HANDS : 0.0F;
		}
		if ("wounds_slow".equals(key)) {
			return Turning.healing(player);
		}
		return 0.0F;
	}

	static int tierFor(int favour) {
		int tier = 0;
		for (int i = 0; i < TIER_AT.length; i++) {
			if (favour >= TIER_AT[i]) {
				tier = i + 1;
			}
		}
		return tier;
	}

	/** The shrine's day: the overworld clock over a Minecraft day. */
	static long today(MinecraftServer server) {
		return day(server.overworld().getOverworldClockTime());
	}

	/** (0.7.513) The shrine day of an overworld clock time (the client reads it too). */
	public static long day(long clock) {
		return Math.floorDiv(clock, Shrine.DAY);
	}

	/** (0.7.513) Stores new marks on {@code sp}. */
	static void setMarks(ServerPlayer sp, List<Mark> marks) {
		set(sp, state(sp).withMarks(marks));
	}

	/** (0.7.513) For part (c): patrons whose champion has still to hunt this player, oldest first (the "hunted" marks). */
	public static List<Patron> hunters(Player player) {
		List<Patron> out = new ArrayList<>();
		for (Mark m : state(player).marks) {
			Patron p = Turning.HUNTED.equals(m.kind()) ? Patron.byKey(m.patron()) : null;
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	/** (0.7.513) For part (c): {@code p}'s champion has had its hunt; the mark is kept as "hunted_done". */
	static void hunted(ServerPlayer sp, Patron p) {
		long today = today(sp.level().getServer());
		List<Mark> marks = new ArrayList<>(state(sp).marks);
		if (marks.removeIf(m -> Turning.HUNTED.equals(m.kind()) && m.patron().equals(p.key))) {
			marks.add(new Mark(Turning.HUNTED_DONE, p.key, today));
			setMarks(sp, marks);
		}
	}

	/** Which patron an offering belongs to, or null (never a lore book). */
	public static @Nullable Patron offeringFor(ItemStack stack) {
		if (stack.isEmpty() || LoreBooks.isLore(stack)) {
			return null;
		}
		for (Patron p : Patron.values()) {
			if (stack.is(p.offerings)) {
				return p;
			}
		}
		return null;
	}

	static MutableComponent coloured(Patron p) {
		return p.named().copy().withStyle(style -> style.withColor(p.colour & 0xFFFFFF));
	}

	// ---- knowing the patrons -----------------------------------------------------------------------------------------------------

	/** Marks in the known list for a lore book already left (not a patron key). */
	static final String BOOK_MARK = "book:";

	/** {@code p} is now known to {@code sp}; true if it is news. */
	static boolean learn(ServerPlayer sp, Patron p) {
		State s = state(sp);
		if (s.known.contains(p.key)) {
			return false;
		}
		List<String> next = new ArrayList<>(s.known);
		next.add(p.key);
		set(sp, s.withKnown(List.copyOf(next)));
		return true;
	}

	/** A shrine of {@code p} dedicated or looked at: known from now on, and its book is left on the altar the first time. */
	static void met(ServerPlayer sp, Patron p, BlockPos pos) {
		learn(sp, p);
		// (0.7.499) the book is its own mark in the known list, so a patron learned some other way still leaves it once
		State s = state(sp);
		String mark = BOOK_MARK + p.key;
		if (!s.known.contains(mark) && sp.level() instanceof ServerLevel level) {
			List<String> next = new ArrayList<>(s.known);
			next.add(mark);
			set(sp, s.withKnown(List.copyOf(next)));
			ItemStack book = LoreBooks.book("patron_" + p.key);
			if (!book.isEmpty()) {
				Block.popResource(level, pos.above(), book);
			}
			// (0.7.535) and the book of its commandments
			Block.popResource(level, pos.above(), Commandments.book(p));
		}
	}

	/** Sneak and use on a shrine: the patrons screen, with this shrine's patron picked. */
	static void openShrine(ServerPlayer sp, BlockPos pos, BlockState state) {
		Patron p = state.getValue(Shrine.DEDICATION).patron();
		if (p != null) {
			met(sp, p, pos);
		}
		ServerPlayNetworking.send(sp, new OpenShrine(pos.immutable()));
	}

	// ---- swearing, offering, deeds ---------------------------------------------------------------------------------------------------

	/** A prayer answered at a shrine of {@code shrine} (null: plain). */
	static void prayed(ServerPlayer sp, @Nullable Patron shrine, BlockPos pos, long today) {
		if (shrine == null) {
			return;
		}
		met(sp, shrine, pos);
		Paladins.prayed(sp);
		Warlocks.prayed(sp, shrine);
		State s = state(sp);
		if (!s.sworn()) {
			Streak st = s.streak;
			int days = st.patron.equals(shrine.key) && st.lastDay == today - 1 ? st.days + 1 : 1;
			if (st.patron.equals(shrine.key) && st.lastDay == today) {
				days = st.days;
			}
			if (days >= SWEAR_DAYS) {
				swear(sp, shrine, FAVOUR_SWORN, today);
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.sworn", coloured(shrine)));
				if (sp.level() instanceof ServerLevel level) {
					level.playSound(null, sp.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.0F);
					Fx.burst(level, sp.getX(), sp.getY() + 1.0, sp.getZ(), shrine.colour & 0xFFFFFF, 1.2F, 30, 0.5, 0.8, 0.5);
					level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1.0, sp.getZ(), 20, 0.5, 0.8, 0.5, 0.3);
				}
			} else {
				set(sp, s.withStreak(new Streak(shrine.key, days, today)));
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.streak", coloured(shrine), days, SWEAR_DAYS));
			}
			return;
		}
		if (shrine.key.equals(s.patron)) {
			gain(sp, FAVOUR_PRAYER);
		} else {
			// (0.7.513) praying your way to another patron: the streak counts toward them, and your own patron notices each day
			Patron mine = s.patronOf();
			Streak st = s.streak;
			int days = st.patron.equals(shrine.key) && st.lastDay == today - 1 ? st.days + 1 : 1;
			if (st.patron.equals(shrine.key) && st.lastDay == today) {
				days = st.days;
			}
			lose(sp, FAVOUR_STRAYED);
			if (days >= SWEAR_DAYS && mine != null) {
				Turning.turn(sp, mine, shrine, today);
			} else {
				set(sp, state(sp).withStreak(new Streak(shrine.key, days, today)));
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.turning", coloured(shrine), mine == null ? "" : coloured(mine), days, SWEAR_DAYS));
				// a low bell, to the player
				sp.level().playSound(null, sp.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.5F, 0.5F);
			}
		}
	}

	/** (0.7.513) Favour down by {@code n} (not below nothing), the tier following. {@code lastFavourDay} is unchanged. */
	static void lose(ServerPlayer sp, int n) {
		State s = state(sp);
		Patron p = s.patronOf();
		if (p == null || n <= 0) {
			return;
		}
		retier(sp, s.withFavour(Math.max(0, s.favour - n), s.tier, s.lastFavourDay, s.decayDay), p);
	}

	/** (0.7.513) Turned to {@code p}: sworn with {@code favour}, the marks in {@code marks}, the old patron's timed boons ended. */
	static void swornTurned(ServerPlayer sp, Patron p, int favour, long today, List<Mark> marks) {
		set(sp, state(sp).sworn(p, favour, today).withMarks(marks));
		learn(sp, p);
		Commandments.sworn(sp, p);
		Blessings.forget(sp.getUUID());
		Blessings.refresh(sp);
	}

	/** Sworn to {@code p} from now: {@code favour} to begin with. */
	static void swear(ServerPlayer sp, Patron p, int favour, long today) {
		State s = state(sp);
		State next = s.sworn(p, favour, today);
		set(sp, next);
		learn(sp, p);
		Commandments.sworn(sp, p);
		Blessings.refresh(sp);
	}

	/** An offering of {@code p}'s made at their shrine. The caller has checked the stack; this takes it if it counts. */
	static void offered(ServerPlayer sp, Patron p, ItemStack stack) {
		State s = state(sp);
		long today = today(sp.level().getServer());
		if (!s.sworn() || !p.key.equals(s.patron)) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.theirs_only", coloured(p)));
			return;
		}
		Daily d = s.daily.day == today ? s.daily : new Daily(today, 0, false);
		if (d.offered) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.offered_today"));
			return;
		}
		if (!sp.isCreative()) {
			stack.shrink(1);
		}
		set(sp, s.withDaily(new Daily(d.day, d.deeds, true)));
		gain(sp, FAVOUR_OFFERING);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.offered", coloured(p)));
	}

	/** A deed of {@code p}'s kind, if {@code player} is sworn to them. Quiet: only a tier change speaks. */
	public static void deed(Player player, Patron p, int amount, String kind) {
		if (!(player instanceof ServerPlayer sp) || sp.isSpectator()) {
			return;
		}
		State s = state(sp);
		if (!s.sworn() || !p.key.equals(s.patron)) {
			return;
		}
		long today = today(sp.level().getServer());
		Daily d = s.daily.day == today ? s.daily : new Daily(today, 0, false);
		int room = DEEDS_DAILY_MOST - d.deeds;
		if (room <= 0) {
			return;
		}
		int given = Math.min(room, amount);
		set(sp, s.withDaily(new Daily(today, d.deeds + given, d.offered)));
		JaymosKingdoms.LOGGER.debug("{} deed {} for {}: +{}", sp.getName().getString(), kind, p.key, given);
		gain(sp, given);
	}

	/** Favour up by {@code n} (capped), the tier following. */
	static void gain(ServerPlayer sp, int n) {
		State s = state(sp);
		Patron p = s.patronOf();
		if (p == null || n <= 0) {
			return;
		}
		long today = today(sp.level().getServer());
		int favour = Math.min(FAVOUR_MAX, s.favour + n);
		retier(sp, s.withFavour(favour, s.tier, today, s.decayDay), p);
	}

	/** Stores {@code next} with the tier its favour earns, and says so if the tier changed. */
	private static void retier(ServerPlayer sp, State next, Patron p) {
		int tier = tierFor(next.favour);
		int was = next.tier;
		set(sp, new State(next.patron, next.favour, tier, next.swornDay, next.streak, next.lastFavourDay, next.decayDay, next.daily, next.boonReady,
			next.known, next.marks, next.seeded));
		if (tier != was) {
			Component name = Component.translatable("patron.jaymoskingdoms.tier." + tier);
			if (tier > was) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron.tier.up", coloured(p), name).withStyle(ChatFormatting.GOLD));
				sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
			} else {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron.tier.down", coloured(p), name).withStyle(ChatFormatting.GRAY));
			}
			Blessings.refresh(sp);
		}
	}

	// ---- every second ----------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % TICK_EVERY != 0) {
			return;
		}
		long today = today(server);
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!sp.isAlive()) {
				continue;
			}
			State s = state(sp);
			if (s.sworn()) {
				if (s.daily.day != today) {
					s = s.withDaily(new Daily(today, 0, false));
					set(sp, s);
				}
				if (s.decayDay < today) {
					decay(sp, s, today);
				}
				sea(sp, s);
			}
			if (tick % BOOK_SCAN_EVERY == 0) {
				scanBooks(sp);
			}
		}
		Blessings.tick(server);
		Turning.tick(server, today, tick);
		Faiths.sunrise(server);
	}

	/** The days without favour cost it: from the later of the last decay and the grace, a few a day, never below tier 1 (in part a). */
	private static void decay(ServerPlayer sp, State s, long today) {
		Patron p = s.patronOf();
		if (p == null) {
			return;
		}
		long from = Math.max(s.decayDay, s.lastFavourDay + GRACE_DAYS);
		long days = Math.max(0L, today - from);
		int perDay = Math.max(DECAY_LEAST, DECAY_PER_DAY - DEVOTION_PER_RANK * Skills.rank(sp, Skills.Perk.DEVOTION));
		// (0.7.513) while the old patron's trials run there is no floor
		// (0.7.581) nor in strict faith
		int floor = Turning.anyTrial(sp) || PatronWrath.strict(sp) ? 0 : TIER_AT[0];
		int favour = (int)Math.max((long)floor, s.favour - days * perDay);
		if (s.favour < floor) {
			favour = s.favour;
		}
		retier(sp, s.withFavour(favour, s.tier, s.lastFavourDay, today), p);
	}

	/** Time aboard on the ocean is a deed of the Deep Mother's. */
	private static void sea(ServerPlayer sp, State s) {
		if (!Patron.DEEP_MOTHER.key.equals(s.patron)) {
			return;
		}
		boolean aboard = (com.jaymoskingdoms.sailing.Ships.aboard(sp) != null || sp.getVehicle() instanceof AbstractBoat) && sp.level().getBiome(sp.blockPosition()).is(BiomeTags.IS_OCEAN);
		if (!aboard) {
			return;
		}
		int ticks = AT_SEA.merge(sp.getUUID(), TICK_EVERY, Integer::sum);
		if (ticks >= SEA_DEED_TICKS) {
			AT_SEA.put(sp.getUUID(), 0);
			deed(sp, Patron.DEEP_MOTHER, DEED_SMALL, "sea");
		}
	}

	/** A patron's lore book in the pack makes that patron known. */
	private static void scanBooks(ServerPlayer sp) {
		var inv = sp.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String id = LoreBooks.idOf(inv.getItem(i));
			if (id.startsWith("jaymoskingdoms:patron_")) {
				Patron p = Patron.byKey(id.substring("jaymoskingdoms:patron_".length()));
				if (p != null) {
					learn(sp, p);
				}
			}
		}
	}

	// ---- the story's start --------------------------------------------------------------------------------------------------------------

	/** The road came after the first start: the patron of the blood and the temperament may have changed, so seed again. */
	public static void storyRestart(ServerPlayer sp) {
		State s = state(sp);
		if (s.seeded && !s.sworn()) {
			set(sp, s.withoutSeeded());
		}
		storyStart(sp);
	}

	/** Once the life story is told: the patron of their blood is known, and a devout upbringing starts sworn (and on every join, for old players). */
	public static void storyStart(ServerPlayer sp) {
		State s = state(sp);
		Heritage.Kind kind = Heritage.of(sp);
		if (s.seeded || kind == null) {
			return;
		}
		set(sp, s.withSeeded());
		Patron mine = Faiths.startOf(kind);
		learn(sp, mine);
		if (PlayerTemperaments.of(sp) == Temperaments.Temperament.DEVOUT && !state(sp).sworn()) {
			swear(sp, mine, FAVOUR_DEVOUT, today(sp.level().getServer()));
			ItemStack book = LoreBooks.book("patron_" + mine.key);
			if (!book.isEmpty() && !sp.getInventory().add(book) && sp.level() instanceof ServerLevel level) {
				sp.spawnAtLocation(level, book);
			}
		}
	}

	// ---- the boon ---------------------------------------------------------------------------------------------------------------------------

	private static void callBoon(ServerPlayer sp) {
		State s = state(sp);
		Patron p = s.patronOf();
		long now = sp.level().getGameTime();
		if (p == null || s.tier < 4 || !sp.isAlive() || sp.isSpectator() || Downed.isDowned(sp)) {
			return;
		}
		if (now < s.boonReady) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.boon.waiting").withStyle(ChatFormatting.GRAY));
			return;
		}
		set(sp, s.withBoon(now + BOON_COOLDOWN));
		Blessings.boon(sp, p);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.boon.called", coloured(p)).withStyle(style -> style.withColor(p.colour & 0xFFFFFF)));
	}

	// ---- deeds from the world ---------------------------------------------------------------------------------------------------------------

	private static void killed(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer sp) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		State s = state(sp);
		if (!s.sworn()) {
			return;
		}
		// (0.7.581) beasts wasted, the tame slain, monsters cleared
		PatronWrath.killed(sp, level, victim);
		if (victim.getType() == com.jaymoskingdoms.whaling.Whaling.GREAT_WHALE) {
			deed(sp, Patron.DEEP_MOTHER, DEED_GREAT, "great_whale");
		}
		if (com.jaymoskingdoms.monsters.Monsters.legend(victim.getType())) {
			deed(sp, Patron.GREEN_HUNTSMAN, DEED_GREAT, "legend");
		} else if (victim instanceof com.jaymoskingdoms.wild.WildBeast || victim instanceof com.jaymoskingdoms.beasts.WildBoar
			|| victim instanceof Wolf wolf && !wolf.isTame() || victim instanceof PolarBear) {
			deed(sp, Patron.GREEN_HUNTSMAN, DEED_SMALL, "wild_beast");
		}
		if (Vampires.isVampire(victim)) {
			deed(sp, Patron.LANTERN_WARDEN, DEED_GREAT, "vampire");
		} else if (victim.is(EntityTypeTags.UNDEAD) && Vampires.night(level.getOverworldClockTime())) {
			deed(sp, Patron.LANTERN_WARDEN, DEED_SMALL, "undead_night");
		}
		if (!(victim instanceof Player) && (Crooks.isCriminal(victim) || Outlaws.is(victim))) {
			deed(sp, Patron.TRUTH, DEED_SMALL, "criminal");
		}
	}

	private static void mined(ServerLevel level, ServerPlayer sp, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.@Nullable BlockEntity be) {
		if (!state.is(ConventionalBlockTags.ORES) || sp.isCreative() || sp.preventsBlockDrops() || !sp.hasCorrectToolForDrops(state)) {
			return;
		}
		if (pos.getY() < 0) {
			deed(sp, Patron.ANVIL_FATHER, DEED_SMALL, "deep_ore");
			Commandments.act(sp, "deep_ore");
		}
		Blessings.richSeam(level, sp, pos, state, be);
	}
}
