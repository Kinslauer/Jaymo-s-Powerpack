package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Looking it up: a sage (a court wizard, an elder, a librarian) or a library (a lectern among bookshelves) for a day's
 * study. You pick a <b>topic</b>, then something to look up under it, pay the fee, and read while a bar fills; stay near
 * (and unhurt), and what you read is learned. Once a day.
 * <p><b>Topics are a hook</b> ({@link #addTopic}): other systems add their own and are listed beside the built-in one.
 * A {@link Topic} says what can be looked up ({@link Topic#subjects}) and what reading it does ({@link Topic#learn}); the
 * boss vessels and relic properties plug in this way. The built-in topic is <b>creature fears</b>
 * ({@link FoeStudy#teach}): a kind you have fought gains a star and shows what it dreads.
 * <p>A day's study is {@link #STUDY_TICKS} ticks at a library and {@link #SAGE_TICKS} with a sage, who charges more.
 */
public final class Research {
	/** One thing to look up under a topic. */
	public record Subject(String key, Component label) {
	}

	/** A line of study. */
	public interface Topic {
		String id();

		Component title();

		/** What there is to look up for {@code player} now (empty: nothing, and the topic is greyed out). */
		List<Subject> subjects(ServerPlayer player);

		/** Reading done: what {@code player} learned, in a line. */
		Component learn(ServerPlayer player, String subject);
	}

	/** Ticks of reading: at a library, and with a sage. */
	static final int STUDY_TICKS = 2400, SAGE_TICKS = 1200;
	/** Fees (emeralds' worth): at a library, and with a sage. */
	static final int LIBRARY_FEE = 2, SAGE_FEE = 6;
	/** A lectern is a library with this many bookshelves within {@link #SHELF_RANGE}. */
	static final int SHELVES = 6, SHELF_RANGE = 5;
	/** How far the reader may go from the place, and the most subjects listed at once. */
	static final double STAY = 8.0;
	static final int LISTED = 6;

	private static final List<Topic> TOPICS = new ArrayList<>();

	private record Reading(UUID player, Topic topic, String subject, Vec3 at, long start, long done, float health) {
	}

	private static final Map<UUID, Reading> READING = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	/** Server → player: a study begins (its title and length); the screen shows a bar. */
	public record Study(Component title, int ticks) implements CustomPacketPayload {
		public static final Type<Study> TYPE = new Type<>(JaymosKingdoms.id("research_study"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Study> CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC, Study::title, ByteBufCodecs.VAR_INT, Study::ticks, Study::new);

		@Override
		public Type<Study> type() {
			return TYPE;
		}
	}

	/** Server → player: the study ended; {@code ok} if it was finished, and what it came to. */
	public record Finish(boolean ok, Component result) implements CustomPacketPayload {
		public static final Type<Finish> TYPE = new Type<>(JaymosKingdoms.id("research_finish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Finish> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Finish::ok, ComponentSerialization.STREAM_CODEC, Finish::result, Finish::new);

		@Override
		public Type<Finish> type() {
			return TYPE;
		}
	}

	/** Player → server: they closed the book. */
	public record Close() implements CustomPacketPayload {
		public static final Type<Close> TYPE = new Type<>(JaymosKingdoms.id("research_close"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.unit(new Close());

		@Override
		public Type<Close> type() {
			return TYPE;
		}
	}

	private Research() {
	}

	/** Adds a line of study, listed in every library and sage's menu. */
	public static void addTopic(Topic topic) {
		TOPICS.removeIf(t -> t.id().equals(topic.id()));
		TOPICS.add(topic);
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Study.TYPE, Study.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Finish.TYPE, Finish.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Close.TYPE, Close.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Close.TYPE, (payload, context) -> abandon(context.player(), false));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READING.remove(handler.player.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(Research::tick);
		addTopic(fears());
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)
				|| player.isShiftKeyDown() || !level.getBlockState(hit.getBlockPos()).is(Blocks.LECTERN) || shelves(sl, hit.getBlockPos()) < SHELVES) {
				return InteractionResult.PASS;
			}
			open(sp, null, hit.getBlockPos());
			return InteractionResult.SUCCESS;
		});
	}

	private static Topic fears() {
		return new Topic() {
			@Override
			public String id() {
				return "fears";
			}

			@Override
			public Component title() {
				return Component.translatable("research.jaymoskingdoms.topic.fears");
			}

			@Override
			public List<Subject> subjects(ServerPlayer player) {
				List<Subject> out = new ArrayList<>();
				for (String kind : FoeStudy.studyable(player)) {
					out.add(new Subject(kind, Component.translatable("entity.minecraft." + kind)));
				}
				return out;
			}

			@Override
			public Component learn(ServerPlayer player, String subject) {
				boolean fear = FoeStudy.fears(subject) != null && !FoeStudy.learned(player, subject);
				FoeStudy.teach(player, subject);
				Component kind = Component.translatable("entity.minecraft." + subject);
				FoeStudy.Fears f = FoeStudy.fears(subject);
				if (fear && f != null) {
					return Component.translatable("research.jaymoskingdoms.fears.learned", kind, Component.translatable("foe.fear." + f.key()),
						FoeStudy.stars(player, subject));
				}
				return Component.translatable("research.jaymoskingdoms.fears.stars", kind, FoeStudy.stars(player, subject));
			}
		};
	}

	private static int shelves(ServerLevel level, BlockPos lectern) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(lectern.offset(-SHELF_RANGE, -2, -SHELF_RANGE), lectern.offset(SHELF_RANGE, 3, SHELF_RANGE))) {
			if (level.getBlockState(p).is(Blocks.BOOKSHELF)) {
				n++;
			}
		}
		return n;
	}

	/** Whether {@code v} is a sage who can be consulted: a court wizard, a village elder or a librarian. */
	static boolean sage(LivingEntity v) {
		if (!v.isAlive() || v instanceof Villager b && b.isBaby()) {
			return false;
		}
		return CourtWizards.is(v) || v instanceof Villager vil && vil.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)
			|| v instanceof Aging aging && v.level() instanceof ServerLevel level && CourtWizards.isElder(level, aging);
	}

	/** The menu of topics, at {@code sage} or (when null) the lectern at {@code place}. */
	static void open(ServerPlayer player, @Nullable LivingEntity sage, @Nullable BlockPos place) {
		ServerLevel level = player.level();
		long day = level.getGameTime() / 24000L;
		LivingEntity speaker = sage != null ? sage : nearestScribe(player);
		if (speaker == null) {
			player.sendOverlayMessage(Component.translatable("research.jaymoskingdoms.no_one"));
			return;
		}
		Long last = LAST.get(player.getUUID());
		if (last != null && last >= day) {
			Speech.tell(level, speaker, player, Component.translatable("research.jaymoskingdoms.tired"), Speech.WHITE);
			return;
		}
		int fee = sage != null ? SAGE_FEE : LIBRARY_FEE;
		Vec3 at = sage != null ? sage.position() : Vec3.atCenterOf(place);
		List<Menus.Choice> choices = new ArrayList<>();
		for (Topic t : TOPICS) {
			boolean any = !t.subjects(player).isEmpty() && Money.canPay(player, Money.worth(fee));
			choices.add(Menus.Choice.of(Component.translatable("research.jaymoskingdoms.pick", t.title(), fee), any, p -> subjects(p, speaker, t, fee, at, sage != null)));
		}
		Menus.ask(player, speaker, Component.translatable(sage != null ? "research.jaymoskingdoms.sage" : "research.jaymoskingdoms.library"), choices);
	}

	/** Someone to speak the library's lines: the nearest grown villager, or any living thing near the lectern. */
	private static @Nullable LivingEntity nearestScribe(ServerPlayer player) {
		return player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Menus.REACH - 1.0), v -> v.isAlive() && !v.isBaby())
			.stream().findFirst().orElse(null);
	}

	private static void subjects(ServerPlayer player, LivingEntity speaker, Topic topic, int fee, Vec3 at, boolean sage) {
		List<Subject> all = topic.subjects(player);
		List<Menus.Choice> choices = new ArrayList<>();
		for (Subject s : all.subList(0, Math.min(LISTED, all.size()))) {
			choices.add(Menus.Choice.of(s.label(), p -> begin(p, topic, s, fee, at, sage)));
		}
		Menus.ask(player, speaker, Component.translatable("research.jaymoskingdoms.which", topic.title()), choices);
	}

	private static void begin(ServerPlayer player, Topic topic, Subject subject, int fee, Vec3 at, boolean sage) {
		ServerLevel level = player.level();
		if (READING.containsKey(player.getUUID()) || !topic.subjects(player).stream().anyMatch(s -> s.key().equals(subject.key()))
			|| !Money.charge(player, Money.worth(fee))) {
			return;
		}
		int ticks = sage ? SAGE_TICKS : STUDY_TICKS;
		long now = level.getGameTime();
		LAST.put(player.getUUID(), now / 24000L);
		READING.put(player.getUUID(), new Reading(player.getUUID(), topic, subject.key(), at, now, now + ticks, player.getHealth()));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		if (ServerPlayNetworking.canSend(player, Study.TYPE)) {
			ServerPlayNetworking.send(player, new Study(Component.translatable("research.jaymoskingdoms.reading", topic.title(), subject.label()), ticks));
		}
	}

	private static void abandon(ServerPlayer player, boolean tell) {
		Reading r = READING.remove(player.getUUID());
		if (r != null && tell && ServerPlayNetworking.canSend(player, Finish.TYPE)) {
			ServerPlayNetworking.send(player, new Finish(false, Component.translatable("research.jaymoskingdoms.lost")));
		}
	}

	private static void tick(MinecraftServer server) {
		if (READING.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Reading r : new ArrayList<>(READING.values())) {
			ServerPlayer p = server.getPlayerList().getPlayer(r.player());
			if (p == null) {
				READING.remove(r.player());
				continue;
			}
			if (!p.isAlive() || p.getHealth() < r.health() || p.position().distanceToSqr(r.at()) > STAY * STAY) {
				abandon(p, true);
				continue;
			}
			if (now >= r.done()) {
				READING.remove(r.player());
				Component result = r.topic().learn(p, r.subject());
				Skills.grant(p, Skills.Pillar.SCHOLAR, 20);
				Sparks.act(p, Sparks.Act.LEARNED, false);
				if (ServerPlayNetworking.canSend(p, Finish.TYPE)) {
					ServerPlayNetworking.send(p, new Finish(true, result));
				}
				p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
			}
		}
	}
}
