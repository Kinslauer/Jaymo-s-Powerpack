package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Sitting down together (docs/Pawn-Plan.md row D4, with Andy's note: you share a meal; after the pawns who ask their
 * master how they should be in Dragon's Dogma, a commercial game: ideas only). Sit (on any seat) at a table or by a
 * campfire with a companion near ({@link #NEAR}): after a moment ({@link #SIT_FOR}) one comes over and sits beside you
 * (a free cushion near, or the ground: {@link GroundSeats}) until you get up, shares a meal if you carry any food (one bite of yours: they're healed and rested), and asks two or three things on a small screen:
 * <ul>
 * <li>keep or change their second leaning ({@link Comrades.Leaning});</li>
 * <li>talk more, as now, or less ({@link #chatter}: their remarks come twice as often or half, and their callouts are
 * kept or skipped more);</li>
 * <li>how to take a win or a rescue: loud, warm or quiet ({@link #voice}).</li>
 * </ul>
 * A sworn captain asks one more: how his men should lean ({@link #MEN}, the second leaning of any band he leads). Each
 * companion sits down once a day; it warms the bond ({@link Bonds#TABLE}); a packhorse hands back what it carried.
 * The band side of this is {@link CampSuppers}.
 */
public final class TableTalk {
	/** Seated this long (ticks) before one comes over; companions this near (blocks) come. */
	static final int SIT_FOR = 60;
	static final double NEAR = 8.0;
	/** A campfire this near (blocks), or a table this near, makes it a table. */
	static final int FIRE_NEAR = 4, TABLE_NEAR = 2;
	/** Extra bond for a shared meal. */
	static final int MEAL = 2;
	/** Looked over this often (ticks). */
	static final int EVERY = 20;

	/** On a companion: -1 talk less, 0 as now, 1 more. Saved. */
	public static final AttachmentType<Integer> CHATTER = AttachmentRegistry.create(JaymosKingdoms.id("comrade_chatter"),
		builder -> builder.persistent(Codec.INT));
	/** On a companion: how they take a win or a rescue (0 loud, 1 warm, 2 quiet). Saved. */
	public static final AttachmentType<Integer> STYLE = AttachmentRegistry.create(JaymosKingdoms.id("comrade_style"),
		builder -> builder.persistent(Codec.INT));
	/** On a companion: the day they last sat down with you. Saved. */
	static final AttachmentType<Long> SAT = AttachmentRegistry.create(JaymosKingdoms.id("comrade_sat"),
		builder -> builder.persistent(Codec.LONG));
	/** On a sworn captain: the leaning his men take as their second. Saved. */
	public static final AttachmentType<String> MEN = AttachmentRegistry.create(JaymosKingdoms.id("captain_men_leaning"),
		builder -> builder.persistent(Codec.STRING));

	/** The screen: who, their leanings, how they talk and take a win, the bond, and (a captain) his men's leaning. */
	public record OpenTable(int entityId, Component name, String main, String second, int chatter, int style, int bond, boolean captain, String men,
		int lend, Component errand) implements CustomPacketPayload {
		public static final Type<OpenTable> TYPE = new Type<>(JaymosKingdoms.id("table_talk_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenTable> CODEC = new StreamCodec<>() {
			@Override
			public OpenTable decode(RegistryFriendlyByteBuf buf) {
				return new OpenTable(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(), buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf));
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, OpenTable p) {
				buf.writeVarInt(p.entityId);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.name);
				buf.writeUtf(p.main);
				buf.writeUtf(p.second);
				buf.writeVarInt(p.chatter);
				buf.writeVarInt(p.style);
				buf.writeVarInt(p.bond);
				buf.writeBoolean(p.captain);
				buf.writeUtf(p.men);
				buf.writeVarInt(p.lend);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.errand);
			}
		};

		@Override
		public Type<OpenTable> type() {
			return TYPE;
		}
	}

	/**
	 * The answers: the second leaning ("" keeps it), chatter, style, his men's leaning ("" as they are), how they're lent
	 * ({@link Loans#LEND}; -1 not asked), whether to take on the errand of what's in hand.
	 */
	public record SetTable(int entityId, String second, int chatter, int style, String men, int lend, boolean errand) implements CustomPacketPayload {
		public static final Type<SetTable> TYPE = new Type<>(JaymosKingdoms.id("table_talk_set"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetTable> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetTable::entityId,
			ByteBufCodecs.STRING_UTF8, SetTable::second,
			ByteBufCodecs.VAR_INT, SetTable::chatter,
			ByteBufCodecs.VAR_INT, SetTable::style,
			ByteBufCodecs.STRING_UTF8, SetTable::men,
			ByteBufCodecs.VAR_INT, SetTable::lend,
			ByteBufCodecs.BOOL, SetTable::errand,
			SetTable::new);

		@Override
		public Type<SetTable> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Integer> SEATED = new HashMap<>();
	/** Companions walking over to sit (until when, game time), and those sat with their player. */
	private record Coming(UUID player, long day, long until) {
	}

	private static final Map<Mob, Coming> COMING = new java.util.IdentityHashMap<>();
	private static final Map<Mob, UUID> SAT_WITH = new java.util.IdentityHashMap<>();
	/** How long one walks over before sitting anyway (ticks); how near counts as arrived (blocks). */
	static final int COME_TICKS = 120;
	static final double ARRIVED = 2.5;

	private TableTalk() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenTable.TYPE, OpenTable.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetTable.TYPE, SetTable.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetTable.TYPE, (payload, context) -> answer(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(TableTalk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SEATED.clear();
			COMING.clear();
			SAT_WITH.clear();
		});
	}

	// ---- what the answers change ---------------------------------------------------------------------------------------

	/** -1 talk less, 0 as now, 1 more. */
	static int chatter(Entity m) {
		return m.getAttachedOrElse(CHATTER, 0);
	}

	/** The voice {@code m} takes a win or a rescue in ({@code kind} "won" or "thanks"), else {@code voice}. */
	static Callouts.Voice voice(Entity m, String kind, Callouts.Voice voice) {
		Integer style = m.getAttached(STYLE);
		if (style == null || !kind.equals("won") && !kind.equals("thanks")) {
			return voice;
		}
		return style == 0 ? Callouts.Voice.BOLD : style == 1 ? Callouts.Voice.WARM : Callouts.Voice.QUIET;
	}

	/** (Comrades) the leaning {@code m}'s band leader, a sworn captain, told his men to take, or null. */
	static Comrades.@Nullable Leaning menLeaning(Mob m, String side) {
		if (!(m.level() instanceof ServerLevel level)) {
			return null;
		}
		LivingEntity leader = BandLeaders.leaderOf(level, m, side);
		String key = leader == null ? null : leader.getAttached(MEN);
		if (key == null || key.isEmpty()) {
			return null;
		}
		for (Comrades.Leaning l : Comrades.Leaning.values()) {
			if (l.key().equals(key)) {
				return l;
			}
		}
		return null;
	}

	// ---- sitting down --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		// walking over: sit when beside you (or when it's taken too long), then talk
		for (var it = COMING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Coming> e = it.next();
			Mob m = e.getKey();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getValue().player());
			if (player == null || !m.isAlive() || m.level() != player.level() || Comrades.target(m) != null || !seatedAtTable(player)) {
				it.remove();
				continue;
			}
			if (m.distanceTo(player) > ARRIVED && now < e.getValue().until()) {
				if (m.getNavigation().isDone()) {
					m.getNavigation().moveTo(player, 0.6);
				}
				continue;
			}
			it.remove();
			if (GroundSeats.sitDown(m, player.position())) {
				SAT_WITH.put(m, player.getUUID());
			}
			talk(player, m);
		}
		// sat: up again when you stand, a fight starts, or they're called away
		SAT_WITH.entrySet().removeIf(e -> {
			Mob m = e.getKey();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getValue());
			if (player != null && m.isAlive() && Comrades.target(m) == null && seatedAtTable(player) && GroundSeats.sitting(m)
				&& m.distanceTo(player) < NEAR) {
				m.getLookControl().setLookAt(player, 30.0F, 30.0F);
				return false;
			}
			GroundSeats.standUp(m);
			return true;
		});
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!seatedAtTable(player)) {
				SEATED.remove(player.getUUID());
				continue;
			}
			int ticks = SEATED.merge(player.getUUID(), EVERY, Integer::sum);
			if (ticks < SIT_FOR) {
				continue;
			}
			long day = player.level().getOverworldClockTime() / 24000L;
			for (Mob m : Bonds.companions(player, NEAR)) {
				if (Comrades.target(m) == null && !Comrades.busy(m) && m.getAttachedOrElse(SAT, -1L) != day && !COMING.containsKey(m)) {
					SEATED.put(player.getUUID(), 0);
					m.setAttached(SAT, day);
					m.getNavigation().moveTo(player, 0.6);
					COMING.put(m, new Coming(player.getUUID(), day, now + COME_TICKS));
					break;
				}
			}
		}
	}

	/** On a seat (not a mount) by a campfire or at a table. */
	static boolean seatedAtTable(ServerPlayer player) {
		Entity seat = player.getVehicle();
		if (seat == null || seat instanceof LivingEntity || Downed.isDowned(player)) {
			return false;
		}
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-FIRE_NEAR, -1, -FIRE_NEAR), at.offset(FIRE_NEAR, 1, FIRE_NEAR))) {
			BlockState s = level.getBlockState(p);
			if (s.isAir()) {
				continue;
			}
			if (s.is(BlockTags.CAMPFIRES) && s.hasProperty(CampfireBlock.LIT) && s.getValue(CampfireBlock.LIT)) {
				return true;
			}
			String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
			if (id.contains("firepit") || id.contains("fire_pit") || id.contains("bonfire")) {
				return true;
			}
			if (id.contains("table") && Math.abs(p.getX() - at.getX()) <= TABLE_NEAR && Math.abs(p.getZ() - at.getZ()) <= TABLE_NEAR) {
				return true;
			}
		}
		return false;
	}

	/** {@code m}, sat down beside you, shares a meal if there is one, and asks. */
	private static void talk(ServerPlayer player, Mob m) {
		m.getLookControl().setLookAt(player, 30.0F, 30.0F);
		ServerLevel level = player.level();
		ItemStack food = food(player);
		int warmth = Bonds.TABLE;
		if (food != null) {
			food.shrink(1);
			m.heal(m.getMaxHealth());
			m.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
			level.playSound(null, m.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
			Callouts.say(m, "table.meal", player, true, player.getDisplayName());
			warmth += MEAL;
		} else {
			Callouts.say(m, "table.sit", player, true, player.getDisplayName());
		}
		Bonds.add(m, player.getUUID(), warmth);
		if (Specialties.is(m, Specialties.Specialty.PACKHORSE) && Specialties.unpack(m, player)) {
			Callouts.say(m, "spec.unpack", player, true);
		}
		Comrades.Leanings l = Comrades.leanings(m);
		boolean captain = SwornCaptains.free(m);
		if (!ServerPlayNetworking.canSend(player, OpenTable.TYPE)) {
			return;
		}
		// a companion of one's own (not a sworn captain, not one on loan) is asked how it's lent, and given an errand (row A1, A5)
		boolean lends = McaCompat.LOADED && m instanceof net.minecraft.world.entity.npc.villager.Villager && !captain && !m.hasAttached(Loans.LOAN);
		ItemStack hand = player.getMainHandItem();
		Component errand = lends && !hand.isEmpty() && Loans.gatherable(hand.getItem()) ? hand.getHoverName() : Component.empty();
		ServerPlayNetworking.send(player, new OpenTable(m.getId(), m.getDisplayName(), l.main().key(), l.second().key(), chatter(m),
			m.getAttachedOrElse(STYLE, styleOf(m)), Bonds.of(m, player.getUUID()), captain, m.getAttachedOrElse(MEN, ""),
			lends ? m.getAttachedOrElse(Loans.LEND, 0) : -1, errand));
	}

	/** How {@code m} takes a win before anyone asked: by their voice. */
	private static int styleOf(Mob m) {
		return switch (Callouts.voice(m)) {
			case BOLD -> 0;
			case WARM -> 1;
			default -> 2;
		};
	}

	/** One bite of food from the player's pack, or null. */
	private static @Nullable ItemStack food(ServerPlayer player) {
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (!s.isEmpty() && s.has(DataComponents.FOOD)) {
				return s;
			}
		}
		return null;
	}

	/** {@code player} answered. */
	private static void answer(ServerPlayer player, SetTable a) {
		Entity e = player.level().getEntity(a.entityId());
		if (!(e instanceof Mob m) || !player.getUUID().equals(Bonds.lordOf(m)) || m.distanceTo(player) > 16.0) {
			return;
		}
		Comrades.Leanings l = Comrades.leanings(m);
		for (Comrades.Leaning to : Comrades.Leaning.values()) {
			if (to.key().equals(a.second()) && to != l.main() && to != l.second()) {
				m.setAttached(Comrades.LEANINGS, l.main().key() + "," + to.key());
				m.removeAttached(LeaningDrift.DRIFT);
				break;
			}
		}
		m.setAttached(CHATTER, Math.max(-1, Math.min(1, a.chatter())));
		m.setAttached(STYLE, Math.max(0, Math.min(2, a.style())));
		if (SwornCaptains.free(m)) {
			boolean known = a.men().isEmpty();
			for (Comrades.Leaning to : Comrades.Leaning.values()) {
				known |= to.key().equals(a.men());
			}
			if (known) {
				m.setAttached(MEN, a.men());
			}
		}
		if (a.lend() >= 0 && McaCompat.LOADED && !m.hasAttached(Loans.LOAN) && !SwornCaptains.free(m)) {
			Loans.fromTable(player, m, a.lend(), a.errand());
		}
		Callouts.say(m, "table.done", player, true, player.getDisplayName());
	}
}
