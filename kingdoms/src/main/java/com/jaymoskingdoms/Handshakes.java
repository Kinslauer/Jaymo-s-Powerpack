package com.jaymoskingdoms;

import com.jaymoskingdoms.dragons.Disguises;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) Andy: "you can see their player tags once finding and interacting with them, (both players right clicking
 * each other, for a cinematic action cam handshake, then a success sound)". Use on another player, empty handed and
 * standing (sneak + use stays a vampire's bite and the downed's pick-up), holds your hand out to them ({@link Hand}:
 * your arm out, for everyone to see) for {@link #OFFER_TICKS}. If they use on you back while it's out, the two of you
 * shake hands: set facing each other at arm's length and held still, a few slow pumps while each of your cameras films
 * it ({@code client/HandshakeCinematic}), then a chime. From then on you know each other's names
 * ({@link Introductions}) and you're in one group ({@link Groups}). A blow, a fall or stepping apart breaks it off.
 * Not behind a full mask, as with {@code /introduce}.
 * <p>
 * Andy: "The initiating player's group is what is joined, everytime. Players within the same group can still
 * handshake, but the cutscene only ever plays once for the same combo of players. Every other time it is a quicker
 * single dap variation." Each player keeps whom they've shaken hands with ({@link #SHOOK}); a pair that already has
 * gets a quick dap ({@link Hand#DAP}: one clasp and a tug, no film) instead.
 * <p>
 * (0.7.204) Andy: "allow handshaking with villagers too, not everyone will accept one as an introduction. but it will
 * help if you dont speak the language to get started". "Offer a handshake" in MCA's menu ({@link #offerTo}): your hand
 * goes out, they think it over ({@link #VILLAGER_THINK}), and either take it ({@link Introductions#takesHand}), the
 * full shake on film and their name learned, or leave you hanging, and won't be asked again that day. A ruler never.
 * <p>
 * (0.7.289) Andy: "your first interaction with a villager instead of opening the menu prompts a handshake", "I want
 * the player to hold out their hand and the villager chooses to shake or wave. you only get the cutscene for villagers
 * that are special like eurus or a master". The first time you use an MCA villager you don't know ({@link #greet}), your
 * hand goes out to them in place of their menu; they think it over and take it ({@link Introductions#takesHand}: the
 * shake, and their name learned) or give you a wave instead ("women usually wave": {@link #WOMAN_SHAKES}). Either way their menu opens after, and from then on it
 * opens straight away ({@link #GREETED}). Every villager handshake is on film only with someone {@linkplain #special
 * special} (a dragon walking as a villager, a master of a gift); with anyone else it's the shake alone. Not a ruler, a
 * child, one with a grudge against you, or behind a full mask: those go straight to the menu as before.
 * <p>
 * (0.7.337) Andy: "Villagers should be able to run up to you with a wave or handshake for introduction". Every so
 * often ({@link #COME_SCAN}, at most once in {@link #COME_GAP} for each player) a villager near you who doesn't know you
 * hails you and hurries over ({@link #comeOver}): who comes goes by their temperament ({@link #COME_WEIGHTS}: the
 * cheerful and kindhearted most, the suspicious, timid and spiteful never) and what they remember of you. Up close they
 * either hold out a hand ({@link Introductions#takesHand}, "women usually wave" as before), which you take by using
 * them (the shake, and their name), or give you a wave and tell you their name. Leave a hand hanging and they let it
 * drop; they don't come again that day. Not at night, not mid-fight, not to someone masked, riding, or with a screen
 * open, and never one of {@link Approaches}' at the same time.
 */
public final class Handshakes {
	/** An offered hand waits this long (ticks) to be taken. */
	static final int OFFER_TICKS = 200;
	/** How near (blocks) the two must be to shake, how far apart they're set for it, and how far they may be moved there. */
	static final double REACH = 3.0, APART = 1.1, SET_MAX = 1.5;
	/** The handshake (ticks): the hands meet at {@link #GRIP_TICK}, let go at {@link #RELEASE_TICK}, it's over at {@link #SHAKE_TICKS}. */
	public static final int GRIP_TICK = 12, RELEASE_TICK = 36, SHAKE_TICKS = 46;
	/** The dap (ticks): the hands clasp at {@link #DAP_GRIP}, let go at {@link #DAP_RELEASE}, it's over at {@link #DAP_TICKS}. */
	public static final int DAP_GRIP = 5, DAP_RELEASE = 10, DAP_TICKS = 14;
	/** Further apart than this (blocks) mid-shake breaks it off. */
	static final double BREAK_DISTANCE = 3.5;
	/** (0.7.204) How long (ticks) a villager thinks over an offered hand. */
	static final int VILLAGER_THINK = 20;
	/** The success sound's volume and pitch. */
	static final float CHIME_VOLUME = 0.7F, CHIME_PITCH = 1.4F;
	/** (0.7.289) A villager's wave at a first meeting (ticks); their menu opens after it. */
	public static final int WAVE_TICKS = 30;
	/** (0.7.289) Andy: "women usually wave": the chance a woman shakes your hand at a first meeting, when she'd take it at all. */
	static final float WOMAN_SHAKES = 0.2F;
	/** (0.7.337) How often (ticks) players are looked at for a villager to come over, how far off they may be (blocks), and the chance a look brings one. */
	static final int COME_SCAN = 40;
	static final double COME_RANGE = 14.0;
	static final float COME_CHANCE = 0.3F;
	/** (0.7.337) Between villagers coming over to one player (ticks): at least this, plus up to this; the first after joining. */
	static final int COME_GAP = 20 * 60, COME_GAP_SPREAD = 20 * 120, COME_FIRST = 20 * 30;
	/** (0.7.337) Hurrying over: speed (MCA's brain), how near is near enough, how long before they give up, how far off you may get. */
	static final float COME_SPEED = 0.75F;
	static final double COME_ARRIVE = 2.2, COME_GIVE_UP = 28.0;
	static final int COME_WALK_TICKS = 20 * 20;
	/** (0.7.337) How long (ticks) a villager who came over holds out their hand, and how far you may step off before they let it drop. */
	static final int COME_OFFER_TICKS = 20 * 8;
	static final double COME_LEAVE = 6.0;
	/**
	 * (0.7.337) How keen each temperament is to come over and introduce themselves ({@link Temperaments.Temperament} order:
	 * kindhearted, cheerful, devout, dutiful, brave, proud, greedy, suspicious, timid, hotheaded, spiteful, gloomy).
	 */
	static final int[] COME_WEIGHTS = {5, 6, 3, 2, 3, 1, 2, 0, 0, 1, 0, 1};
	/** (0.7.337) Keenness with no temperament, and what a warm memory of you adds. */
	static final int COME_PLAIN = 2, COME_WARM = 2;
	/** (0.7.337) How many ways they hail you on setting off, and how far it carries (blocks). */
	static final int HAILS = 4;
	static final double HAIL_RANGE = 16.0;

	/** (0.7.289) On a player: the villagers who've greeted them, whose menu opens straight away from then (saved, kept through death). */
	static final AttachmentType<Set<UUID>> GREETED = AttachmentRegistry.create(JaymosKingdoms.id("greeted"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath());

	/** On a player: everyone they've shaken hands with (saved, kept through death; the server's alone). */
	static final AttachmentType<Set<UUID>> SHOOK = AttachmentRegistry.create(JaymosKingdoms.id("shook_hands"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath());

	/** Held still for it. */
	private static final Identifier HOLD = JaymosKingdoms.id("handshake_hold");

	/** Player → the hand they hold out: to whom, and until when (game time). */
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	/** The handshakes going on. */
	private static final List<Shake> SHAKES = new ArrayList<>();
	/** (0.7.204) Hands held out to villagers, waiting on their answer, and the handshakes with villagers going on. */
	private static final List<Asked> ASKED = new ArrayList<>();
	private static final List<VillagerShake> VILLAGER_SHAKES = new ArrayList<>();
	/** (0.7.204) "villager/player" → the day that villager last turned that player's hand down. */
	private static final Map<String, Long> TURNED_DOWN = new HashMap<>();
	/** (0.7.289) Player → the villager they're holding a hand out to at a first meeting. */
	private static final Map<UUID, Greeting> GREETINGS = new HashMap<>();
	/** (0.7.289) Waves at a first meeting going on. */
	private static final List<Wave> WAVES = new ArrayList<>();
	/** (0.7.337) Player → the villager coming over to introduce themselves. */
	private static final Map<UUID, Comer> COMERS = new HashMap<>();
	/** (0.7.337) Player → when a villager may next come over (game time). */
	private static final Map<UUID, Long> COME_NEXT = new HashMap<>();
	/** (0.7.337) "villager/player" → the day that villager last came over to that player. */
	private static final Map<String, Long> CAME = new HashMap<>();

	/** (0.7.289) Your hand is out to {@code villager}, who answers at {@code decide} (game time): {@code takes} it, or waves. */
	private record Greeting(Villager villager, long decide, boolean takes) {
	}

	/** (0.7.289) {@code villager} waves to {@code player} from {@code start} (game time); the menu {@code opens} after (0.7.334: not when they came to you). */
	private record Wave(UUID player, Villager villager, long start, boolean opens) {
	}

	/**
	 * (0.7.337) {@code villager} is coming over to {@code player} (till {@code until}, game time), and once there will
	 * hold out a hand ({@code takes}) or wave; {@code offering} once the hand's out.
	 */
	private static final class Comer {
		final Villager villager;
		final boolean takes;
		long until;
		boolean offering;

		Comer(Villager villager, boolean takes, long until) {
			this.villager = villager;
			this.takes = takes;
			this.until = until;
		}
	}

	/** (0.7.204) {@code player} holds a hand out to {@code villager}, who answers at {@code decide} (game time): {@code takes} it or not. */
	private record Asked(UUID player, Villager villager, long decide, boolean takes) {
	}

	/**
	 * (0.7.204) {@code player} and {@code villager} shake hands from {@code start} (game time); the villager stands facing
	 * {@code yaw}. (0.7.289) A first meeting's ({@code greeting}) opens their menu once it's done.
	 */
	private record VillagerShake(UUID player, Villager villager, long start, float yaw, boolean greeting) {
	}

	private record Offer(UUID to, long until) {
	}

	/** {@code offerer} held out the hand {@code taker} took, at {@code start} (game time); a quick dap, or the full shake. */
	private record Shake(UUID offerer, UUID taker, long start, boolean dap) {
		int grip() {
			return this.dap ? DAP_GRIP : GRIP_TICK;
		}

		int length() {
			return this.dap ? DAP_TICKS : SHAKE_TICKS;
		}

		boolean has(UUID id) {
			return this.offerer.equals(id) || this.taker.equals(id);
		}
	}

	/**
	 * Server → everyone who can see {@code playerId}: their hand toward {@code partnerId}: {@link #DOWN}, {@link #OFFERED}
	 * (held out), {@link #SHAKING} (the handshake starts now), {@link #DAP} (the quick one starts now), or (0.7.289)
	 * {@link #WAVE} (a wave starts now), or {@link #PLAIN} (the handshake starts now, not on film).
	 */
	public record Hand(int playerId, int partnerId, int state) implements CustomPacketPayload {
		public static final int DOWN = 0, OFFERED = 1, SHAKING = 2, DAP = 3, WAVE = 4, PLAIN = 5;
		public static final Type<Hand> TYPE = new Type<>(JaymosKingdoms.id("handshake"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hand> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.playerId);
				buf.writeVarInt(p.partnerId);
				buf.writeByte(p.state);
			},
			buf -> new Hand(buf.readVarInt(), buf.readVarInt(), buf.readByte()));

		@Override
		public Type<Hand> type() {
			return TYPE;
		}
	}

	private Handshakes() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("handshake attachments: {} {}", SHOOK, GREETED);
		PayloadTypeRegistry.clientboundPlay().register(Hand.TYPE, Hand.CODEC);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && entity instanceof Villager villager && player instanceof ServerPlayer sp && !world.isClientSide()
				&& meetComer(sp, villager)) {
				// (0.7.337) the villager who came over to you: take their hand, or have them say hello now
				return InteractionResult.SUCCESS;
			}
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Player other) || !can(player) || !can(other)
				|| player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || player.distanceTo(other) > REACH) {
				return InteractionResult.PASS;
			}
			if (!world.isClientSide() && player instanceof ServerPlayer sp && other instanceof ServerPlayer op) {
				offer(sp, op);
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Handshakes::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F) {
				breakOff(player);
			} else if (entity instanceof Villager villager && taken > 0.0F) {
				breakOff(villager);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			breakOff(handler.player);
			OFFERS.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OFFERS.clear();
			SHAKES.clear();
			ASKED.clear();
			VILLAGER_SHAKES.clear();
			TURNED_DOWN.clear();
			GREETINGS.clear();
			WAVES.clear();
			COMERS.clear();
			COME_NEXT.clear();
			CAME.clear();
		});
	}

	/**
	 * (0.7.289) A first meeting's handshake ({@link #greet}). Registered last of all, so every other use of a villager
	 * (a quest, a bounty, a pickpocket, someone come over to talk) goes first.
	 */
	static void registerGreetings() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(entity instanceof Villager villager)) {
				return InteractionResult.PASS;
			}
			return greet(sp, villager) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	/** {@code player} could shake hands just now: up, about, not a spectator. */
	private static boolean can(Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isSleeping() && !Downed.isDowned(player);
	}

	/** {@code player} used {@code other}: they take {@code other}'s hand if it's held out to them, else hold out their own. */
	private static void offer(ServerPlayer player, ServerPlayer other) {
		if (shaking(player.getUUID()) || shaking(other.getUUID())) {
			return;
		}
		if (Identities.masked(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.introduce.masked").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		long now = player.level().getGameTime();
		Offer back = OFFERS.get(other.getUUID());
		if (back != null && back.to.equals(player.getUUID()) && now <= back.until) {
			start(other, player, now);
			return;
		}
		Offer mine = OFFERS.get(player.getUUID());
		if (mine != null && mine.to.equals(other.getUUID()) && now <= mine.until) {
			// already held out to them
			return;
		}
		OFFERS.put(player.getUUID(), new Offer(other.getUUID(), now + OFFER_TICKS));
		send(player, other.getId(), Hand.OFFERED);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.4F, 1.3F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.offered", nameFor(player, other))
			.withStyle(ChatFormatting.GRAY), true);
		other.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.offer", nameFor(other, player))
			.withStyle(ChatFormatting.GRAY), true);
	}

	/** {@code whom} as {@code viewer} knows them: their name, or "Stranger". */
	private static Component nameFor(ServerPlayer viewer, ServerPlayer whom) {
		return Introductions.knows(viewer, whom) ? Identities.trueName(whom) : Introductions.stranger();
	}

	/**
	 * (0.7.204) {@code player} offers {@code villager} a handshake (MCA's menu, {@link Introductions#HANDSHAKE}): the
	 * hand goes out and they think it over. Not behind a mask, not to a ruler, and not twice in a day to one who said no.
	 */
	public static void offerTo(ServerPlayer player, Villager villager) {
		if (!can(player) || shaking(player.getUUID()) || busy(villager) || !villager.isAlive()) {
			return;
		}
		if (Identities.masked(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.introduce.masked").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (Rulers.is(villager)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.ruler").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		Long down = TURNED_DOWN.get(villager.getUUID() + "/" + player.getUUID());
		if (down != null && down == Deeds.today(player.level().getServer())) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.not_today").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		ASKED.add(new Asked(player.getUUID(), villager, player.level().getGameTime() + VILLAGER_THINK, Introductions.takesHand(player, villager)));
		villager.getNavigation().stop();
		send(player, villager.getId(), Hand.OFFERED);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.4F, 1.3F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.offered", villager.getType().getDescription())
			.withStyle(ChatFormatting.GRAY), true);
	}

	/** (0.7.204) {@code villager} is weighing a hand or shaking one. */
	private static boolean busy(Villager villager) {
		for (Asked a : ASKED) {
			if (a.villager == villager) {
				return true;
			}
		}
		for (VillagerShake s : VILLAGER_SHAKES) {
			if (s.villager == villager) {
				return true;
			}
		}
		for (Greeting g : GREETINGS.values()) {
			if (g.villager == villager) {
				return true;
			}
		}
		for (Wave w : WAVES) {
			if (w.villager == villager) {
				return true;
			}
		}
		for (Comer c : COMERS.values()) {
			if (c.villager == villager) {
				return true;
			}
		}
		return false;
	}

	/** (0.7.337) {@code mob} is coming over to someone, weighing a hand, shaking one or waving ({@link Approaches} leaves them be). */
	static boolean engaged(net.minecraft.world.entity.Mob mob) {
		return mob instanceof Villager v && busy(v);
	}

	/** (0.7.337) A villager is on their way to {@code player} to introduce themselves. */
	static boolean awaited(ServerPlayer player) {
		return COMERS.containsKey(player.getUUID());
	}

	/** (0.7.204) {@code villager} answers {@code player}'s hand: takes it (the shake begins) or leaves it hanging. */
	private static void answer(ServerPlayer player, Asked a, long now) {
		Villager villager = a.villager;
		ServerLevel level = player.level();
		if (!a.takes) {
			TURNED_DOWN.put(villager.getUUID() + "/" + player.getUUID(), Deeds.today(level.getServer()));
			send(player, -1, Hand.DOWN);
			Speech.say(level, villager, Component.translatable("handshake.jaymoskingdoms.refused"), Speech.WHITE, 12.0);
			level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.refused").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		shake(player, villager, now, false);
	}

	/** (0.7.289) Andy: "you only get the cutscene for villagers that are special like eurus or a master". */
	static boolean special(Villager villager) {
		return villager.hasAttached(Disguises.DISGUISE) || PowersCompat.giftMaster(villager);
	}

	/** (0.7.204) {@code player} and {@code villager} start shaking hands: you're set at arm's length in front of them, they turn to you. */
	private static void shake(ServerPlayer player, Villager villager, long now, boolean greeting) {
		Vec3 axis = new Vec3(player.getX() - villager.getX(), 0.0, player.getZ() - villager.getZ());
		axis = axis.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, villager.getYRot()) : axis.normalize();
		place(player, villager.position().add(axis.scale(APART)), axis.reverse());
		float yaw = (float)(Mth.atan2(axis.z, axis.x) * Mth.RAD_TO_DEG) - 90.0F;
		face(villager, yaw);
		hold(player, true);
		VILLAGER_SHAKES.add(new VillagerShake(player.getUUID(), villager, now, yaw, greeting));
		// (0.7.289) on film only with someone special
		send(player, villager.getId(), special(villager) ? Hand.SHAKING : Hand.PLAIN);
		send(villager, player.getId(), Hand.SHAKING);
	}

	/** (0.7.204) {@code villager} stood still facing along {@code yaw}. */
	private static void face(Villager villager, float yaw) {
		villager.getNavigation().stop();
		villager.setDeltaMovement(Vec3.ZERO);
		villager.setYRot(yaw);
		villager.setYBodyRot(yaw);
		villager.setYHeadRot(yaw);
	}

	/** (0.7.204) The villager's hand went through: you know their name, and they say it (a name needs no shared tongue). */
	private static void done(ServerPlayer player, Villager villager, Vec3 mid) {
		Introductions.learn(player, villager);
		ServerLevel level = player.level();
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.villager_done", Component.literal(villager.getName().getString()))
			.withStyle(ChatFormatting.GREEN), true);
	}

	/** (0.7.204) Both let go: you're free to move, both arms lowered. */
	private static void end(MinecraftServer server, VillagerShake s) {
		ServerPlayer p = server.getPlayerList().getPlayer(s.player);
		if (p != null) {
			hold(p, false);
			send(p, -1, Hand.DOWN);
		}
		send(s.villager, -1, Hand.DOWN);
	}

	/** (0.7.204) Whatever handshake {@code villager} is in is broken off (0.7.289: or greeting). */
	private static void breakOff(Villager villager) {
		MinecraftServer server = villager.level().getServer();
		for (Iterator<Map.Entry<UUID, Comer>> it = COMERS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Comer> e = it.next();
			if (e.getValue().villager == villager) {
				it.remove();
				leave(server.getPlayerList().getPlayer(e.getKey()), e.getValue());
			}
		}
		for (Iterator<Map.Entry<UUID, Greeting>> it = GREETINGS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Greeting> e = it.next();
			if (e.getValue().villager == villager) {
				it.remove();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					send(p, -1, Hand.DOWN);
				}
			}
		}
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave w = it.next();
			if (w.villager == villager) {
				it.remove();
				endWave(server, w);
			}
		}
		for (Iterator<VillagerShake> it = VILLAGER_SHAKES.iterator(); it.hasNext(); ) {
			VillagerShake s = it.next();
			if (s.villager == villager) {
				it.remove();
				end(server, s);
			}
		}
	}

	/** The two shake hands: set at arm's length facing each other, held still, and their clients told. */
	private static void start(ServerPlayer offerer, ServerPlayer taker, long now) {
		OFFERS.remove(offerer.getUUID());
		OFFERS.remove(taker.getUUID());
		Vec3 a = offerer.position(), b = taker.position();
		Vec3 axis = new Vec3(b.x - a.x, 0.0, b.z - a.z);
		axis = axis.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, offerer.getYRot()) : axis.normalize();
		Vec3 mid = a.add(b).scale(0.5);
		place(offerer, mid.subtract(axis.scale(APART / 2.0)), axis);
		place(taker, mid.add(axis.scale(APART / 2.0)), axis.reverse());
		hold(offerer, true);
		hold(taker, true);
		boolean dap = shook(offerer, taker) && shook(taker, offerer);
		SHAKES.add(new Shake(offerer.getUUID(), taker.getUUID(), now, dap));
		send(offerer, taker.getId(), dap ? Hand.DAP : Hand.SHAKING);
		send(taker, offerer.getId(), dap ? Hand.DAP : Hand.SHAKING);
	}

	/** {@code player} has shaken {@code other}'s hand before. */
	private static boolean shook(ServerPlayer player, ServerPlayer other) {
		Set<UUID> shook = player.getAttached(SHOOK);
		return shook != null && shook.contains(other.getUUID());
	}

	private static void remember(ServerPlayer player, ServerPlayer other) {
		Set<UUID> shook = player.getAttached(SHOOK);
		if (shook == null || !shook.contains(other.getUUID())) {
			Set<UUID> next = shook == null ? new HashSet<>() : new HashSet<>(shook);
			next.add(other.getUUID());
			player.setAttached(SHOOK, next);
		}
	}

	/** {@code player} stood at {@code spot} (their own height) if it's near and clear, facing along {@code facing}. */
	private static void place(ServerPlayer player, Vec3 spot, Vec3 facing) {
		float yaw = (float)(Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90.0F;
		Vec3 to = new Vec3(spot.x, player.getY(), spot.z);
		Vec3 move = to.subtract(player.position());
		boolean clear = move.length() <= SET_MAX && player.level().noCollision(player, player.getBoundingBox().move(move));
		if (!clear) {
			to = player.position();
		}
		player.teleportTo(player.level(), to.x, to.y, to.z, Set.of(), yaw, 0.0F, false);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
	}

	/** Held still (no walking, no jumping) while the hands are joined, or let go. */
	private static void hold(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		for (AttributeInstance a : new AttributeInstance[] {speed, jump}) {
			if (a == null) {
				continue;
			}
			if (on) {
				a.addOrUpdateTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else {
				a.removeModifier(HOLD);
			}
		}
	}

	private static boolean shaking(UUID id) {
		for (Shake s : SHAKES) {
			if (s.has(id)) {
				return true;
			}
		}
		return false;
	}

	/** Whatever handshake {@code player} is in is broken off. */
	private static void breakOff(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ASKED.removeIf(a -> a.player.equals(player.getUUID()));
		Comer comer = COMERS.remove(player.getUUID());
		if (comer != null) {
			leave(player, comer);
		}
		if (GREETINGS.remove(player.getUUID()) != null) {
			send(player, -1, Hand.DOWN);
		}
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave w = it.next();
			if (w.player.equals(player.getUUID())) {
				it.remove();
				endWave(server, w);
			}
		}
		for (Iterator<VillagerShake> it = VILLAGER_SHAKES.iterator(); it.hasNext(); ) {
			VillagerShake s = it.next();
			if (s.player.equals(player.getUUID())) {
				it.remove();
				end(server, s);
			}
		}
		for (Iterator<Shake> it = SHAKES.iterator(); it.hasNext(); ) {
			Shake s = it.next();
			if (s.has(player.getUUID())) {
				it.remove();
				end(server, s);
			}
		}
	}

	/** Both let go: free to move, the arms lowered. */
	private static void end(MinecraftServer server, Shake s) {
		for (UUID id : new UUID[] {s.offerer, s.taker}) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				hold(p, false);
				send(p, -1, Hand.DOWN);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % COME_SCAN == 0) {
			// (0.7.337) someone may come over to introduce themselves
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				considerComing(player);
			}
		}
		tickComers(server);
		if (OFFERS.isEmpty() && SHAKES.isEmpty() && ASKED.isEmpty() && VILLAGER_SHAKES.isEmpty() && GREETINGS.isEmpty() && WAVES.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		tickVillagers(server, now);
		for (Iterator<Map.Entry<UUID, Offer>> it = OFFERS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Offer> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			ServerPlayer to = server.getPlayerList().getPlayer(e.getValue().to);
			if (p == null || to == null || now > e.getValue().until || !can(p) || p.level() != to.level()
				|| p.distanceTo(to) > REACH * 2.0 || !p.getMainHandItem().isEmpty()) {
				it.remove();
				if (p != null) {
					send(p, -1, Hand.DOWN);
				}
			}
		}
		for (Iterator<Shake> it = SHAKES.iterator(); it.hasNext(); ) {
			Shake s = it.next();
			ServerPlayer a = server.getPlayerList().getPlayer(s.offerer), b = server.getPlayerList().getPlayer(s.taker);
			if (a == null || b == null || !can(a) || !can(b) || a.level() != b.level() || a.distanceTo(b) > BREAK_DISTANCE) {
				it.remove();
				end(server, s);
				continue;
			}
			long t = now - s.start;
			Vec3 mid = a.position().add(b.position()).scale(0.5).add(0.0, 1.1, 0.0);
			ServerLevel level = a.level();
			if (t == s.grip()) {
				level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, s.dap ? 1.1F : 0.8F);
			}
			if (t >= s.length()) {
				it.remove();
				end(server, s);
				done(a, b, mid, s.dap);
			}
		}
	}

	/** (0.7.204) Hands held out to villagers answered, and handshakes with them kept going (or broken off). */
	private static void tickVillagers(MinecraftServer server, long now) {
		for (Iterator<Asked> it = ASKED.iterator(); it.hasNext(); ) {
			Asked a = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(a.player);
			boolean gone = p == null || !can(p) || !a.villager.isAlive() || a.villager.isRemoved() || p.level() != a.villager.level()
				|| p.distanceTo(a.villager) > REACH * 2.0 || !p.getMainHandItem().isEmpty();
			if (gone) {
				it.remove();
				if (p != null) {
					send(p, -1, Hand.DOWN);
				}
			} else if (now >= a.decide) {
				it.remove();
				answer(p, a, now);
			} else {
				a.villager.getNavigation().stop();
				a.villager.getLookControl().setLookAt(p);
			}
		}
		for (Iterator<VillagerShake> it = VILLAGER_SHAKES.iterator(); it.hasNext(); ) {
			VillagerShake s = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(s.player);
			Villager v = s.villager;
			if (p == null || !can(p) || !v.isAlive() || v.isRemoved() || p.level() != v.level() || p.distanceTo(v) > BREAK_DISTANCE) {
				it.remove();
				end(server, s);
				continue;
			}
			face(v, s.yaw);
			long t = now - s.start;
			Vec3 mid = p.position().add(v.position()).scale(0.5).add(0.0, 1.1, 0.0);
			if (t == GRIP_TICK) {
				p.level().playSound(null, mid.x, mid.y, mid.z, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F, 0.8F);
			}
			if (t >= SHAKE_TICKS) {
				it.remove();
				end(server, s);
				done(p, v, mid);
				if (s.greeting) {
					menu(p, v);
				}
			}
		}
		tickGreetings(server, now);
	}

	// ---- (0.7.289) a first meeting ----------------------------------------------------------------------------------

	/**
	 * (0.7.289) {@code player} used {@code villager}: if it's their first time and the villager is one to greet, your
	 * hand goes out to them in place of opening their menu (true). A villager you already know (introduced, family,
	 * your group), or a ruler, counts as greeted.
	 */
	private static boolean greet(ServerPlayer player, Villager villager) {
		if (GREETINGS.containsKey(player.getUUID())) {
			// (your hand's already out to someone)
			return true;
		}
		if (!McaCompat.isMca(villager) || player.isShiftKeyDown() || !can(player) || greetedBy(player, villager) || !villager.isAlive()
			|| villager.isBaby() || villager.isSleeping() || villager.isTrading() || villager.getTarget() != null || McaCompat.panicking(villager)
			|| shaking(player.getUUID()) || busy(villager) || Identities.masked(player) || Recollections.refuses(villager, player)) {
			return false;
		}
		if (Introductions.knows(player, villager) || Rulers.is(villager)) {
			greeted(player, villager);
			return false;
		}
		boolean takes = Introductions.takesHand(player, villager) && (!McaCompat.female(villager) || villager.getRandom().nextFloat() < WOMAN_SHAKES);
		GREETINGS.put(player.getUUID(), new Greeting(villager, player.level().getGameTime() + VILLAGER_THINK, takes));
		villager.getNavigation().stop();
		villager.getLookControl().setLookAt(player);
		send(player, villager.getId(), Hand.OFFERED);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.4F, 1.3F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.offered", villager.getType().getDescription())
			.withStyle(ChatFormatting.GRAY), true);
		return true;
	}

	/** (0.7.289) {@code villager} has greeted {@code player} already. */
	private static boolean greetedBy(ServerPlayer player, Villager villager) {
		Set<UUID> greeted = player.getAttached(GREETED);
		return greeted != null && greeted.contains(villager.getUUID());
	}

	private static void greeted(ServerPlayer player, Villager villager) {
		Set<UUID> greeted = player.getAttached(GREETED);
		if (greeted == null || !greeted.contains(villager.getUUID())) {
			Set<UUID> next = greeted == null ? new HashSet<>() : new HashSet<>(greeted);
			next.add(villager.getUUID());
			player.setAttached(GREETED, next);
		}
	}

	/** (0.7.289) {@code villager} answers your hand at a first meeting: takes it, or waves to you instead. */
	private static void answerGreeting(ServerPlayer player, Villager villager, boolean takes, long now) {
		greeted(player, villager);
		if (takes) {
			shake(player, villager, now, true);
			return;
		}
		send(player, -1, Hand.DOWN);
		send(villager, player.getId(), Hand.WAVE);
		WAVES.add(new Wave(player.getUUID(), villager, now, true));
		player.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.greet.waved").withStyle(ChatFormatting.GRAY), true);
	}

	/** (0.7.289) {@code villager} is out of reach of {@code player}'s greeting. */
	private static boolean gone(ServerPlayer player, Villager villager) {
		return !villager.isAlive() || villager.isRemoved() || player.level() != villager.level() || player.distanceTo(villager) > REACH * 2.0;
	}

	/** (0.7.289) Hands held out at first meetings answered (or let drop), and waves kept going, then the menu opened. */
	private static void tickGreetings(MinecraftServer server, long now) {
		for (Iterator<Map.Entry<UUID, Greeting>> it = GREETINGS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Greeting> e = it.next();
			Greeting g = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !can(p) || gone(p, g.villager)) {
				it.remove();
				if (p != null) {
					send(p, -1, Hand.DOWN);
				}
			} else if (now >= g.decide) {
				it.remove();
				answerGreeting(p, g.villager, g.takes, now);
			} else {
				g.villager.getNavigation().stop();
				g.villager.getLookControl().setLookAt(p);
			}
		}
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave w = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(w.player);
			if (p == null || !can(p) || gone(p, w.villager)) {
				it.remove();
				endWave(server, w);
				continue;
			}
			w.villager.getNavigation().stop();
			w.villager.getLookControl().setLookAt(p);
			if (now - w.start >= WAVE_TICKS) {
				it.remove();
				endWave(server, w);
				if (w.opens) {
					menu(p, w.villager);
				}
			}
		}
	}

	// ---- (0.7.337) they come to you -----------------------------------------------------------------------------------

	/** (0.7.337) Maybe a villager near {@code player} who doesn't know them comes over to introduce themselves. */
	private static void considerComing(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		UUID id = player.getUUID();
		Long next = COME_NEXT.get(id);
		if (next == null) {
			COME_NEXT.put(id, now + COME_FIRST);
			return;
		}
		if (now < next || COMERS.containsKey(id) || GREETINGS.containsKey(id) || shaking(id) || !can(player) || player.isPassenger()
			|| player.containerMenu != player.inventoryMenu || Identities.masked(player) || Approaches.engaged(id) || Approaches.fighting(player)
			|| Approaches.night(level) || level.getRandom().nextFloat() > COME_CHANCE) {
			return;
		}
		long today = Deeds.today(level.getServer());
		Villager best = null;
		int total = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(COME_RANGE))) {
			int w = comeWeight(level, player, v, today);
			// (a weighted draw over everyone who'd come)
			if (w > 0) {
				total += w;
				if (level.getRandom().nextInt(total) < w) {
					best = v;
				}
			}
		}
		if (best != null) {
			comeOver(player, best, now, today);
		}
	}

	/** (0.7.337) How keen {@code v} is to come over and introduce themselves to {@code player} (0: they won't). */
	private static int comeWeight(ServerLevel level, ServerPlayer player, Villager v, long today) {
		if (!McaCompat.isMca(v) || v.isBaby() || v.isTrading() || McaCompat.panicking(v) || busy(v) || !Approaches.free(v)
			|| Introductions.knows(player, v) || greetedBy(player, v) || Rulers.is(v) || Recollections.refuses(v, player)) {
			return 0;
		}
		Long came = CAME.get(v.getUUID() + "/" + player.getUUID());
		if (came != null && came == today || !v.hasLineOfSight(player)) {
			return 0;
		}
		Faction f = Approaches.home(level, v);
		if (f != null && (Reputation.get(player, f.id()) <= Reputation.HOSTILE || Theft.wanted(player, f.id()))) {
			// (their people hate you, or are after you)
			return 0;
		}
		Temperaments.Temperament t = Temperaments.of(v);
		int w = t == null ? COME_PLAIN : COME_WEIGHTS[t.ordinal()];
		int lean = Recollections.leaning(v, player);
		if (lean <= Recollections.COLD) {
			return 0;
		}
		return w > 0 && lean >= Recollections.WARM ? w + COME_WARM : w;
	}

	/** (0.7.337) {@code villager} hails {@code player} and hurries over; whether they'll offer a hand is settled now. */
	private static void comeOver(ServerPlayer player, Villager villager, long now, long today) {
		boolean takes = Introductions.takesHand(player, villager) && (!McaCompat.female(villager) || villager.getRandom().nextFloat() < WOMAN_SHAKES);
		COMERS.put(player.getUUID(), new Comer(villager, takes, now + COME_WALK_TICKS));
		CAME.put(villager.getUUID() + "/" + player.getUUID(), today);
		COME_NEXT.put(player.getUUID(), now + COME_GAP + villager.getRandom().nextInt(COME_GAP_SPREAD));
		Speech.say(player.level(), villager, Component.translatable("greet.jaymoskingdoms.hail." + villager.getRandom().nextInt(HAILS)), Speech.WHITE, HAIL_RANGE);
		comeWalk(villager, player);
	}

	private static void comeWalk(Villager villager, ServerPlayer player) {
		BehaviorUtils.setWalkAndLookTargetMemories(villager, new EntityTracker(player, true), COME_SPEED, 1);
	}

	/** (0.7.337) Villagers coming over kept going, arriving, holding out a hand, or giving up. */
	private static void tickComers(MinecraftServer server) {
		if (COMERS.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Comer>> it = COMERS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Comer> e = it.next();
			Comer c = e.getValue();
			Villager v = c.villager;
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !can(p) || !v.isAlive() || v.isRemoved() || p.level() != v.level() || v.isSleeping() || v.getTarget() != null
				|| McaCompat.panicking(v) || now > c.until || p.distanceTo(v) > (c.offering ? COME_LEAVE : COME_GIVE_UP)) {
				it.remove();
				leave(p, c);
				continue;
			}
			if (c.offering) {
				stand(v, p);
			} else if (p.distanceTo(v) <= COME_ARRIVE) {
				if (arrive(p, c, now)) {
					it.remove();
				}
			} else if (now % 10 == 0) {
				comeWalk(v, p);
			}
		}
	}

	/** (0.7.337) {@code villager} stood still, looking at {@code player}. */
	private static void stand(Villager villager, ServerPlayer player) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getNavigation().stop();
		villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
	}

	/**
	 * (0.7.337) {@code c}'s villager has reached {@code player}: they hold out a hand, or wave and say their name (the
	 * name needs no shared tongue). True when it's done with (a wave); false while the hand waits.
	 */
	private static boolean arrive(ServerPlayer player, Comer c, long now) {
		Villager villager = c.villager;
		stand(villager, player);
		if (c.takes) {
			c.offering = true;
			c.until = now + COME_OFFER_TICKS;
			send(villager, player.getId(), Hand.OFFERED);
			player.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.4F, 1.3F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.greet.came_offer")
				.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		greeted(player, villager);
		Introductions.learn(player, villager);
		send(villager, player.getId(), Hand.WAVE);
		WAVES.add(new Wave(player.getUUID(), villager, now, false));
		Component name = Component.literal(villager.getName().getString());
		Speech.say(player.level(), villager, Component.translatable("dialogue.chat.jaymoskingdoms.name." + villager.getRandom().nextInt(Introductions.LINES), name),
			Speech.WHITE, 12.0);
		player.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable(Languages.understands(player, villager) ? "message.jaymoskingdoms.greet.came_waved"
			: "message.jaymoskingdoms.greet.came_waved_foreign", name).withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	/**
	 * (0.7.337) {@code player} used {@code villager}: if it's the one coming over to them, take their hand (it's out), or
	 * have them say hello there and then (they're on their way). True if it was them.
	 */
	private static boolean meetComer(ServerPlayer player, Villager villager) {
		Comer c = COMERS.get(player.getUUID());
		if (c == null || c.villager != villager || player.isShiftKeyDown()) {
			return false;
		}
		long now = player.level().getGameTime();
		if (!c.offering) {
			if (arrive(player, c, now)) {
				COMERS.remove(player.getUUID());
			}
			return true;
		}
		COMERS.remove(player.getUUID());
		greeted(player, villager);
		shake(player, villager, now, false);
		return true;
	}

	/** (0.7.337) {@code c}'s villager gives up coming over (or lets the hand they held out drop) and goes back about their day. */
	private static void leave(@Nullable ServerPlayer player, Comer c) {
		if (c.villager.isAlive()) {
			c.villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (c.offering) {
			send(c.villager, -1, Hand.DOWN);
			if (player != null && c.villager.isAlive()) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.greet.came_dropped").withStyle(ChatFormatting.GRAY), true);
			}
		}
	}

	/** (0.7.289) Both arms lowered after a wave. */
	private static void endWave(MinecraftServer server, Wave w) {
		ServerPlayer p = server.getPlayerList().getPlayer(w.player);
		if (p != null) {
			send(p, -1, Hand.DOWN);
		}
		send(w.villager, -1, Hand.DOWN);
	}

	/** (0.7.289) The greeting's over: {@code villager}'s menu opens, as the use would have opened it. */
	private static void menu(ServerPlayer player, Villager villager) {
		if (villager.isAlive() && !gone(player, villager) && player.containerMenu == player.inventoryMenu) {
			villager.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
		}
	}

	/** The handshake went through: each knows the other's name, they're in one party (0.7.264: {@link Parties}), and a chime (a lighter one for a dap). */
	private static void done(ServerPlayer offerer, ServerPlayer taker, Vec3 mid, boolean dap) {
		Introductions.learn(offerer, taker);
		Introductions.learn(taker, offerer);
		remember(offerer, taker);
		remember(taker, offerer);
		Component group = Parties.shaken(offerer, taker);
		ServerLevel level = offerer.level();
		if (!dap) {
			level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH);
		}
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, dap ? 0.7F : 1.0F, dap ? 1.5F : 1.2F);
		for (ServerPlayer[] pair : new ServerPlayer[][] {{offerer, taker}, {taker, offerer}}) {
			pair[0].sendSystemMessage(Component.translatable("message.jaymoskingdoms.handshake.done", Identities.trueName(pair[1]), group)
				.withStyle(ChatFormatting.GREEN), true);
		}
	}

	/** Tells everyone who can see {@code player} (and them) where their hand is (0.7.204: a villager's too). */
	private static void send(Entity player, int partnerId, int state) {
		Hand packet = new Hand(player.getId(), partnerId, state);
		List<ServerPlayer> to = new ArrayList<>(PlayerLookup.tracking(player));
		if (player instanceof ServerPlayer self) {
			to.add(self);
		}
		for (ServerPlayer p : to) {
			if (ServerPlayNetworking.canSend(p, Hand.TYPE)) {
				ServerPlayNetworking.send(p, packet);
			}
		}
	}
}
