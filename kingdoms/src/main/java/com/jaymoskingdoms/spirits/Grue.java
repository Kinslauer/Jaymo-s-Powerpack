package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.Voices;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.465) The Grue. Andy (2026-11-01): "each spirit warns against it, vehemently because the closer you dip into the dark
 * arts, i.e flux, death and blood magic, the Grue will overtake your spirit, fighting it as much as it can along the way,
 * sometimes forcing you away from certain harmful actions, plucking the items from your very hands, until the grue attacks
 * you both, ultimately over taking the spirit and turning a deep dark red hue instead. The grue was a vile evil wizard who
 * warred the spirit wizards in the past and searches often for hosts to the overworld. none of your goals are your own, The
 * grue's abilities are extremely powerful, but only it has control. you will be allowed to use your body for food and
 * staying alive and that is it. The grue will take full control doing its best to assume your personality and everyday
 * routine." Only a spirit's keeper can be taken. The Grue's hold on them ({@link State#hold}, 0-{@link #TAKEN}):
 * <ul>
 * <li><b>It creeps in</b> while alchemical corruption is above {@link #SAFE} (more the more there is, slower the stronger the
 * bond), and with every forbidden cast ({@link #darkAct}); it ebbs while the keeper stays clean. The spirit warns every time
 * (death or blood magic, pooled flux, corruption climbing). Even the Quiet One speaks of it.</li>
 * <li><b>From {@link #STIR}</b> the spirit fights for you: it may stop a forbidden cast and pluck what is in your hand
 * ({@link #stayHand}), or walk you out of pooled flux ({@link #pullAway}). Its arms flicker red.</li>
 * <li><b>From {@link #ASSAULT}</b> the Grue lashes out at you both now and then: it hurts you (never past two hearts) and tears
 * at the bond. The arms stay streaked red.</li>
 * <li><b>At {@link #TAKEN}</b> it has the spirit, deep dark red, and your body ({@link #possess}): it walks your routine (where
 * you used to be at each hour, {@link #ROUTINE}), fights with the spirit's arms far harder than you could, and speaks in your
 * words ({@link #HEARD}) when people are near. You may only eat and drink; your own words don't come out (/ooc still does).</li>
 * <li><b>Freed</b> only by a friend: down the host, then give them a Purifying Draught ({@link #exorcise}). The Grue burns out
 * and takes the spirit with it: never another (Andy chose "Friends exorcise you"). Ops have {@code /kingdom grue}.</li>
 * </ul>
 * The client drives the body ({@code GrueClient}, the same steering as a seizure) and tints the arms.
 */
public final class Grue {
	/** Corruption the Grue can't use: below it, its hold ebbs. */
	static final int SAFE = 10;
	/** The hold is looked at this often (ticks). */
	static final int LOOK_EVERY = 400;
	/** Hold gained a look per point of corruption above {@link #SAFE}; at full bond the spirit holds back this share of it. */
	static final float GROW_PER_POINT = 0.03F, BOND_RESIST = 0.5F;
	/** Hold lost a look when clean; hold gained for each forbidden cast. */
	static final float EBB = 1.0F, DARK_ACT = 3.0F;
	/** Corruption climbing this much between two looks is warned about. */
	static final int CLIMB_WARN = 8;
	/** The stages: the spirit starts fighting it; the Grue starts lashing out; it has you. */
	public static final int STIR = 25, ASSAULT = 55, TAKEN = 100;
	/** The least time between two warnings (ticks). */
	static final int WARN_GAP = 300;
	/** The chance the spirit stops a forbidden cast, at {@link #STIR} and near {@link #TAKEN} (it fights harder as it loses). */
	static final float STAY_LOW = 0.35F, STAY_HIGH = 0.75F;
	/** Pickup delay on a plucked item (ticks), and how hard it's flung ahead. */
	static final int PLUCK_DELAY = 60;
	static final double PLUCK_FLING = 0.35;
	/** Pooled flux that counts (Powers' chunk flux), how often it's looked for (ticks), and the chance the spirit walks you out. */
	static final int FLUX_FROM = 100, FLUX_EVERY = 40;
	static final float PULL_CHANCE = 0.25F;
	/** The walk out of flux: how long the spirit has the wheel (ticks), how far it takes you (blocks), and how fast. */
	static final int PULL_TICKS = 50;
	static final double PULL_DIST = 14.0;
	static final float PULL_SPEED = 0.22F;
	/** The Grue's lashes at {@link #ASSAULT} and up: the least and most ticks between, the hurt (more toward {@link #TAKEN}). */
	static final int LASH_MIN = 1800, LASH_MAX = 4200;
	static final float LASH_HURT = 3.0F, LASH_HURT_MORE = 5.0F, LASH_SPARE = 4.0F;
	static final int LASH_BOND = 2;
	/** The Grue's own words to its host: least and most ticks between. */
	static final int TAUNT_MIN = 2400, TAUNT_MAX = 6000;
	/** Possessed: how far it sees a foe, how close it strikes, how far its grasp reaches (blocks). */
	static final double THREAT_RANGE = 16.0, STRIKE_REACH = 3.5, GRASP_REACH = 14.0;
	/** Its strike and grasp: hurt and ticks between. Far beyond what the spirit gave its keeper. */
	static final float STRIKE_HURT = 14.0F, GRASP_HURT = 9.0F;
	static final int STRIKE_EVERY = 14, GRASP_EVERY = 80;
	static final double STRIKE_KNOCK = 1.2, GRASP_PULL = 1.1;
	/** Below this share of health it runs from a fight to keep its vessel. */
	static final float FLEE_HEALTH = 0.3F;
	/** Walking speeds: about its routine, and fighting or fleeing. */
	static final float WALK = 0.2F, RUSH = 0.3F;
	/** How long each drive order lasts on the client (refreshed every {@link #DRIVE_EVERY} ticks, so it ends by itself if we stop). */
	static final int DRIVE_TICKS = 40, DRIVE_EVERY = 10;
	/** The routine: a spot kept per in-game hour, written this often (ticks); a spot farther than this isn't walked to (blocks). */
	static final int ROUTINE_EVERY = 600;
	static final double ROUTINE_FAR = 256.0, ARRIVED = 3.0, WANDER = 6.0;
	static final int WANDER_EVERY = 200;
	/** Speaking as you: least and most ticks between, someone within this range to hear it, and how many of your lines it keeps. */
	static final int MIMIC_MIN = 7200, MIMIC_MAX = 14400, HEARD_KEEP = 16;
	static final double MIMIC_NEAR = 32.0;
	/** Your own words don't come out while it has you. */
	static final boolean MUTE_HOST = true;
	/** Lingering corruption the exorcism burns away. */
	static final int EXORCISM_CURE = 20;
	/** How many lines the Grue has for each thing it says. */
	static final int GRUE_LINES = 3;
	/** The Grue's colour: deep dark red. */
	public static final int COLOR = 0x7A0010;

	static final Identifier PURIFYING_DRAUGHT = Identifier.fromNamespaceAndPath("jaymospowers", "purifying_draught");

	/** The Grue's hold on a keeper: {@code hold} 0-{@link #TAKEN}, {@code taken} once it has them. */
	public record State(float hold, boolean taken) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.optionalFieldOf("hold", 0.0F).forGetter(State::hold),
				Codec.BOOL.optionalFieldOf("taken", false).forGetter(State::taken))
			.apply(i, State::new));
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, State::hold, ByteBufCodecs.BOOL, State::taken, State::new);
	}

	/** The hold: saved, kept through death, synced to everyone (they see the arms redden). */
	public static final AttachmentType<State> GRUE = AttachmentRegistry.create(JaymosKingdoms.id("grue"),
		builder -> builder.persistent(State.CODEC).copyOnDeath().syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Where the keeper is at each in-game hour ("hour" → "dimension;packed pos"). Saved, never synced. */
	public static final AttachmentType<Map<String, String>> ROUTINE = AttachmentRegistry.create(JaymosKingdoms.id("grue_routine"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)).copyOnDeath());
	/** The keeper's last words aloud, for the Grue to borrow. Saved, never synced. */
	public static final AttachmentType<List<String>> HEARD = AttachmentRegistry.create(JaymosKingdoms.id("grue_heard"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/**
	 * Server → player: something else has the wheel. Walk at ({@code x}, {@code y}, {@code z}), facing entity {@code face}
	 * (-1: the spot), at {@code speed}, for {@code ticks} (0: let go now). {@code grue}: the Grue (else the spirit pulling you out).
	 */
	public record Drive(double x, double y, double z, int face, float speed, int ticks, boolean grue) implements CustomPacketPayload {
		public static final Type<Drive> TYPE = new Type<>(JaymosKingdoms.id("grue_drive"));
		public static final StreamCodec<FriendlyByteBuf, Drive> CODEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, Drive::x,
			ByteBufCodecs.DOUBLE, Drive::y, ByteBufCodecs.DOUBLE, Drive::z, ByteBufCodecs.VAR_INT, Drive::face, ByteBufCodecs.FLOAT, Drive::speed,
			ByteBufCodecs.VAR_INT, Drive::ticks, ByteBufCodecs.BOOL, Drive::grue, Drive::new);

		@Override
		public Type<Drive> type() {
			return TYPE;
		}
	}

	/** Server → player: the Grue lashed at you (a red flash and a jolt). */
	public record Lash(float strength) implements CustomPacketPayload {
		public static final Type<Lash> TYPE = new Type<>(JaymosKingdoms.id("grue_lash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Lash> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Lash::strength, Lash::new);

		@Override
		public Type<Lash> type() {
			return TYPE;
		}
	}

	/** Per keeper, game time of: the next warning, lash, taunt, strike, grasp, words as them, wander pick; and the drive target. */
	private static final Map<UUID, Long> NEXT_WARN = new HashMap<>(), NEXT_LASH = new HashMap<>(), NEXT_TAUNT = new HashMap<>(),
		NEXT_STRIKE = new HashMap<>(), NEXT_GRASP = new HashMap<>(), NEXT_MIMIC = new HashMap<>(), NEXT_WANDER = new HashMap<>(),
		PULLING = new HashMap<>();
	private static final Map<UUID, Vec3> WANDER_TO = new HashMap<>();
	private static final Map<UUID, Integer> LAST_CORRUPTION = new HashMap<>();

	private Grue() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("grue attachments: {} {} {}", GRUE, ROUTINE, HEARD);
		PayloadTypeRegistry.clientboundPlay().register(Drive.TYPE, Drive.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Lash.TYPE, Lash.CODEC);
		// (0.7.467) the trial when it takes you
		GrueTrial.register();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> forgetAll());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
		// the host's hands are the Grue's: no blows, no blocks, nothing used but what keeps the body fed and alive
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> taken(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> taken(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> taken(player) && !keepsAlive(player.getItemInHand(hand))
			? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> taken(player) && !keepsAlive(player.getItemInHand(hand))
			? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer friend && entity instanceof ServerPlayer host && exorcise(friend, host, hand)) {
				return InteractionResult.SUCCESS;
			}
			// (the client leaves it to the server, rather than drinking the draught itself)
			if (level.isClientSide() && entity instanceof Player host && host != player && taken(host) && draught(player.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			return taken(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("grue").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("free").then(Commands.argument("player", EntityArgument.player())
					.executes(c -> freeCommand(c.getSource(), EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("hold").then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("n", IntegerArgumentType.integer(0, TAKEN))
						.executes(c -> holdCommand(c.getSource(), EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "n")))))))));
	}

	private static void forget(UUID id) {
		NEXT_WARN.remove(id);
		NEXT_LASH.remove(id);
		NEXT_TAUNT.remove(id);
		NEXT_STRIKE.remove(id);
		NEXT_GRASP.remove(id);
		NEXT_MIMIC.remove(id);
		NEXT_WANDER.remove(id);
		PULLING.remove(id);
		WANDER_TO.remove(id);
		LAST_CORRUPTION.remove(id);
	}

	private static void forgetAll() {
		NEXT_WARN.clear();
		NEXT_LASH.clear();
		NEXT_TAUNT.clear();
		NEXT_STRIKE.clear();
		NEXT_GRASP.clear();
		NEXT_MIMIC.clear();
		NEXT_WANDER.clear();
		PULLING.clear();
		WANDER_TO.clear();
		LAST_CORRUPTION.clear();
	}

	// ---- who -----------------------------------------------------------------------------------------------------------

	/** Whether the Grue has {@code entity} (their body and their spirit). */
	public static boolean taken(@Nullable Entity entity) {
		State s = entity == null ? null : entity.getAttached(GRUE);
		return s != null && s.taken();
	}

	/** The Grue's hold on {@code entity} (0 for none). */
	public static float hold(Entity entity) {
		State s = entity.getAttached(GRUE);
		if (s == null) {
			// (0.7.467) Grue Form burns as red as the Grue itself
			SpiritState spirit = Spirits.state(entity);
			return spirit != null && spirit.toggled(SpiritState.GRUE_FORM) ? TAKEN : 0.0F;
		}
		return s.taken() ? TAKEN : s.hold();
	}

	/** What a host may still use: food and anything drunk or eaten. */
	public static boolean keepsAlive(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE));
	}

	// ---- every tick ------------------------------------------------------------------------------------------------------

	/** Every tick for each player ({@link Spirits#tick}). */
	static void tick(ServerPlayer player, long now) {
		SpiritState spirit = Spirits.state(player);
		if (spirit == null || player.isCreative() || player.isSpectator() || !player.isAlive() || GrueTrial.active(player)) {
			return;
		}
		// (0.7.467) subjugated: it never creeps in again, and Grue Form costs its mana
		if (GrueTrial.mastered(spirit)) {
			if ((now + player.getId()) % 20L == 0L) {
				GrueTrial.formSecond(player, spirit);
			}
			return;
		}
		State state = player.getAttached(GRUE);
		long beat = now + player.getId();
		if (beat % LOOK_EVERY == 0L && (state == null || !state.taken())) {
			look(player, spirit, state);
			state = player.getAttached(GRUE);
		}
		if (state != null && state.taken()) {
			possess(player, now);
			return;
		}
		if (beat % ROUTINE_EVERY == 0L) {
			remember(player);
		}
		float hold = state == null ? 0.0F : state.hold();
		if (beat % FLUX_EVERY == 0L && player.level() instanceof ServerLevel level && PowersCompat.flux(level, player.blockPosition()) >= FLUX_FROM) {
			if (hold >= STIR && now >= PULLING.getOrDefault(player.getUUID(), 0L) && player.getRandom().nextFloat() < PULL_CHANCE) {
				pullAway(player, level, now);
			} else {
				warn(player, "grue.flux", now);
			}
		}
		if (hold >= ASSAULT) {
			assault(player, hold, now);
		}
	}

	/** Every {@link #LOOK_EVERY} ticks: corruption feeds the hold (the bond resists it), or it ebbs. */
	private static void look(ServerPlayer player, SpiritState spirit, @Nullable State state) {
		int corruption = PowersCompat.corruption(player);
		Integer last = LAST_CORRUPTION.put(player.getUUID(), corruption);
		if (last != null && corruption - last >= CLIMB_WARN) {
			warn(player, "grue.warn", player.level().getGameTime());
		}
		float hold = state == null ? 0.0F : state.hold();
		float change = corruption > SAFE
			? (corruption - SAFE) * GROW_PER_POINT * (1.0F - BOND_RESIST * spirit.bond() / (float)Spirits.MAX_BOND)
			: -EBB;
		if (change < 0.0F && hold <= 0.0F) {
			return;
		}
		setHold(player, hold + change);
	}

	/** The hold set (clamped); the stages crossed are said, and at {@link #TAKEN} it has them. */
	private static void setHold(ServerPlayer player, float to) {
		float was = hold(player);
		float hold = Math.clamp(to, 0.0F, (float)TAKEN);
		if (hold <= 0.0F) {
			player.removeAttached(GRUE);
			return;
		}
		player.setAttached(GRUE, new State(hold, false));
		SpiritState spirit = Spirits.state(player);
		if (spirit == null) {
			return;
		}
		if (was < STIR && hold >= STIR) {
			SpiritVoice.sayNow(player, spirit.kind(), "grue.stir");
		} else if (was < ASSAULT && hold >= ASSAULT) {
			SpiritVoice.sayNow(player, spirit.kind(), "grue.fight");
			NEXT_LASH.put(player.getUUID(), player.level().getGameTime() + LASH_MIN / 2);
		}
		if (hold >= TAKEN && !GrueTrial.start(player)) {
			// (0.7.467) the trial first (GrueTrial); straight to it only where there's no sky to raise an arena in
			overtake(player, spirit);
		}
	}

	/** The spirit warns of the dark ({@code event}: grue.warn, grue.flux), at most once a {@link #WARN_GAP}. */
	private static void warn(ServerPlayer player, String event, long now) {
		if (now < NEXT_WARN.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		SpiritState spirit = Spirits.state(player);
		if (spirit != null && SpiritVoice.sayNow(player, spirit.kind(), event)) {
			NEXT_WARN.put(player.getUUID(), now + WARN_GAP);
		}
	}

	// ---- the dark arts -------------------------------------------------------------------------------------------------

	/** A forbidden cast was made by {@code player} ({@code ForbiddenArts}): the spirit warns, and the Grue gains. */
	public static void darkAct(ServerPlayer player) {
		if (Spirits.state(player) == null || taken(player) || player.isCreative() || GrueTrial.mastered(Spirits.state(player))
			|| GrueTrial.active(player)) {
			return;
		}
		warn(player, "grue.warn", player.level().getGameTime());
		setHold(player, hold(player) + DARK_ACT);
	}

	/**
	 * {@code player} is about to make a forbidden cast: from {@link #STIR}, the spirit may stop it, plucking what is in their
	 * hand and flinging it ahead. Whether it stopped it (the cast must not happen, and nothing is paid).
	 */
	public static boolean stayHand(ServerPlayer player) {
		SpiritState spirit = Spirits.state(player);
		float hold = hold(player);
		if (spirit == null || taken(player) || player.isCreative() || hold < STIR || GrueTrial.mastered(spirit) || GrueTrial.active(player)) {
			return false;
		}
		float chance = STAY_LOW + (STAY_HIGH - STAY_LOW) * Math.clamp((hold - STIR) / (TAKEN - STIR), 0.0F, 1.0F);
		if (player.getRandom().nextFloat() >= chance) {
			return false;
		}
		ServerLevel level = (ServerLevel)player.level();
		ItemStack held = player.getMainHandItem();
		if (!held.isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			ItemEntity thrown = player.drop(held, false, net.minecraft.util.Prediction.SERVER_ONLY);
			if (thrown != null) {
				thrown.setPickUpDelay(PLUCK_DELAY);
				Vec3 ahead = player.getLookAngle().scale(PLUCK_FLING);
				thrown.setDeltaMovement(ahead.x, 0.25, ahead.z);
			}
		}
		Spirits.show(player, Poses.STEAL);
		player.setAttached(Spirits.FLASH, level.getGameTime() * 8L + Spirits.FLASH_WARN);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		SpiritVoice.sayNow(player, spirit.kind(), "grue.stay");
		NEXT_WARN.put(player.getUUID(), level.getGameTime() + WARN_GAP);
		// (after the cast's own "no target" line, which would cover this one)
		level.getServer().execute(() -> player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.grue.stayed")
			.withStyle(s -> s.withColor(Spirits.of(spirit.kind()).color() & 0xFFFFFF))));
		return true;
	}

	/** The spirit takes the wheel and walks {@code player} out of pooled flux, toward the cleanest ground near. */
	private static void pullAway(ServerPlayer player, ServerLevel level, long now) {
		SpiritState spirit = Spirits.state(player);
		if (spirit == null) {
			return;
		}
		Vec3 best = null;
		int least = Integer.MAX_VALUE;
		for (int i = 0; i < 4; i++) {
			Vec3 dir = new Vec3(1.0, 0.0, 0.0).yRot(i * (float)Math.PI / 2.0F);
			Vec3 to = player.position().add(dir.scale(PULL_DIST));
			int flux = PowersCompat.flux(level, BlockPos.containing(to));
			if (flux < least) {
				least = flux;
				best = to;
			}
		}
		if (best == null) {
			return;
		}
		PULLING.put(player.getUUID(), now + PULL_TICKS * 4L);
		ServerPlayNetworking.send(player, new Drive(best.x, player.getEyeY(), best.z, -1, PULL_SPEED, PULL_TICKS, false));
		Spirits.show(player, Poses.PULL);
		player.setAttached(Spirits.FLASH, now * 8L + Spirits.FLASH_WARN);
		SpiritVoice.sayNow(player, spirit.kind(), "grue.stay");
		NEXT_WARN.put(player.getUUID(), now + WARN_GAP);
	}

	/** From {@link #ASSAULT}: now and then the Grue lashes at the keeper and their spirit both. */
	private static void assault(ServerPlayer player, float hold, long now) {
		UUID id = player.getUUID();
		Long next = NEXT_LASH.get(id);
		if (next == null) {
			NEXT_LASH.put(id, now + LASH_MIN + player.getRandom().nextInt(LASH_MAX - LASH_MIN + 1));
			return;
		}
		if (now < next || Downed.isDowned(player) || player.isSleeping()) {
			return;
		}
		NEXT_LASH.put(id, now + LASH_MIN + player.getRandom().nextInt(LASH_MAX - LASH_MIN + 1));
		ServerLevel level = (ServerLevel)player.level();
		float share = Math.clamp((hold - ASSAULT) / (TAKEN - ASSAULT), 0.0F, 1.0F);
		float hurt = Math.min(LASH_HURT + LASH_HURT_MORE * share, player.getHealth() - LASH_SPARE);
		if (hurt > 0.0F) {
			player.hurtServer(level, level.damageSources().magic(), hurt);
		}
		Spirits.addBond(player, -LASH_BOND);
		redBurst(level, player.position().add(0.0, 1.0, 0.0), 30, 0.6);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 0.5F);
		ServerPlayNetworking.send(player, new Lash(0.6F + 0.4F * share));
		PowersCompat.shake(player, 0.5F + 0.5F * share);
		SpiritState spirit = Spirits.state(player);
		if (spirit != null) {
			Spirits.show(player, Poses.WARD);
			// the spirit cries out, or the Grue speaks through it
			if (player.getRandom().nextBoolean()) {
				SpiritVoice.sayNow(player, spirit.kind(), "grue.fight");
			} else {
				speak(player, "whisper");
			}
		}
	}

	// ---- taken -----------------------------------------------------------------------------------------------------------

	/** (0.7.467) The trial lost: it has them now. */
	static void overtakeNow(ServerPlayer player) {
		SpiritState spirit = Spirits.state(player);
		if (spirit != null) {
			overtake(player, spirit);
		}
	}

	/** (0.7.467) The Grue's own voice to {@code player} ({@link #speak}). */
	static void speakTo(ServerPlayer player, String event) {
		speak(player, event);
	}

	/** It has the spirit, and the body. */
	private static void overtake(ServerPlayer player, SpiritState spirit) {
		SpiritVoice.sayNow(player, spirit.kind(), "grue.lost");
		player.setAttached(GRUE, new State(TAKEN, true));
		Spirits.forceOut(player);
		ServerLevel level = (ServerLevel)player.level();
		redBurst(level, player.position().add(0.0, 1.0, 0.0), 80, 1.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.5F, 0.6F);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundGameEventPacket(
			net.minecraft.network.protocol.game.ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT, 1.0F));
		ServerPlayNetworking.send(player, new Lash(1.0F));
		PowersCompat.shake(player, 1.0F);
		speak(player, "taken");
		long now = level.getGameTime();
		NEXT_TAUNT.put(player.getUUID(), now + TAUNT_MIN);
		NEXT_MIMIC.put(player.getUUID(), now + MIMIC_MIN / 2);
	}

	/** Every tick while it has {@code player}: it walks their routine, fights, speaks as them, and taunts them. */
	private static void possess(ServerPlayer player, long now) {
		if (Downed.isDowned(player) || player.isSleeping()) {
			return;
		}
		UUID id = player.getUUID();
		SpiritState spirit = Spirits.state(player);
		if (spirit != null && !spirit.out()) {
			Spirits.forceOut(player);
		}
		ServerLevel level = (ServerLevel)player.level();
		if (now >= NEXT_TAUNT.getOrDefault(id, 0L)) {
			NEXT_TAUNT.put(id, now + TAUNT_MIN + player.getRandom().nextInt(TAUNT_MAX - TAUNT_MIN + 1));
			speak(player, "taunt");
		}
		if (now >= NEXT_MIMIC.getOrDefault(id, 0L)) {
			NEXT_MIMIC.put(id, now + MIMIC_MIN + player.getRandom().nextInt(MIMIC_MAX - MIMIC_MIN + 1));
			mimic(player, level);
		}
		LivingEntity threat = threat(player, level);
		if (threat != null) {
			fight(player, level, threat, spirit, now);
		}
		if ((now + player.getId()) % DRIVE_EVERY != 0L) {
			return;
		}
		if (threat != null) {
			if (player.getHealth() <= player.getMaxHealth() * FLEE_HEALTH && threat.distanceTo(player) < 8.0F) {
				// keeping its vessel alive: away from it
				Vec3 away = player.position().subtract(threat.position()).multiply(1.0, 0.0, 1.0);
				Vec3 to = player.position().add(away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize().scale(10.0));
				drive(player, to.add(0.0, player.getEyeHeight(), 0.0), -1, RUSH);
			} else {
				drive(player, threat.getBoundingBox().getCenter(), threat.getId(), RUSH);
			}
			return;
		}
		Vec3 spot = routineSpot(player, level);
		if (spot != null && spot.distanceTo(player.position()) > ARRIVED) {
			drive(player, spot.add(0.0, player.getEyeHeight(), 0.0), -1, WALK);
			return;
		}
		// there: idling about it, the way you would
		Vec3 wander = WANDER_TO.get(id);
		if (wander == null || now >= NEXT_WANDER.getOrDefault(id, 0L)) {
			Vec3 around = spot != null ? spot : player.position();
			double a = player.getRandom().nextDouble() * Math.PI * 2.0, r = player.getRandom().nextDouble() * WANDER;
			wander = around.add(Math.cos(a) * r, 0.0, Math.sin(a) * r);
			WANDER_TO.put(id, wander);
			NEXT_WANDER.put(id, now + WANDER_EVERY + player.getRandom().nextInt(WANDER_EVERY));
		}
		drive(player, wander.add(0.0, player.getEyeHeight(), 0.0), -1, WALK * 0.6F);
	}

	private static void drive(ServerPlayer player, Vec3 to, int face, float speed) {
		ServerPlayNetworking.send(player, new Drive(to.x, to.y, to.z, face, speed, DRIVE_TICKS, true));
	}

	/** What the Grue fights: whatever is after its vessel, or a monster close by. */
	private static @Nullable LivingEntity threat(ServerPlayer player, ServerLevel level) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(THREAT_RANGE), m -> m.isAlive() && !m.isInvisible())) {
			boolean after = mob.getTarget() == player;
			if (!after && !(mob instanceof Enemy && mob.distanceTo(player) < THREAT_RANGE / 2.0)) {
				continue;
			}
			double d = mob.distanceToSqr(player) * (after ? 0.5 : 1.0);
			if (d < bestD) {
				bestD = d;
				best = mob;
			}
		}
		if (best == null && player.getLastHurtByMob() != null && player.getLastHurtByMob().isAlive()
			&& player.getLastHurtByMob().distanceTo(player) < THREAT_RANGE) {
			best = player.getLastHurtByMob();
		}
		return best;
	}

	/** The Grue's blows: a crushing strike up close, and a grasp that drags a foe in from afar. */
	private static void fight(ServerPlayer player, ServerLevel level, LivingEntity foe, @Nullable SpiritState spirit, long now) {
		UUID id = player.getUUID();
		float scale = spirit == null ? 1.0F : Spirits.damageScale(spirit);
		double d = foe.distanceTo(player);
		if (d <= STRIKE_REACH && now >= NEXT_STRIKE.getOrDefault(id, 0L)) {
			NEXT_STRIKE.put(id, now + STRIKE_EVERY);
			Spirits.show(player, Poses.SLAM, d);
			foe.hurtServer(level, level.damageSources().indirectMagic(player, player), STRIKE_HURT * scale);
			foe.knockback(STRIKE_KNOCK, player.getX() - foe.getX(), player.getZ() - foe.getZ(), level.damageSources().indirectMagic(player, player), STRIKE_HURT);
			redBurst(level, foe.getBoundingBox().getCenter(), 24, 0.5);
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0F, 0.7F);
		} else if (d > STRIKE_REACH && d <= GRASP_REACH && now >= NEXT_GRASP.getOrDefault(id, 0L) && player.hasLineOfSight(foe)) {
			NEXT_GRASP.put(id, now + GRASP_EVERY);
			Spirits.show(player, Poses.PULL, d);
			foe.hurtServer(level, level.damageSources().indirectMagic(player, player), GRASP_HURT * scale);
			Vec3 in = player.position().subtract(foe.position()).normalize().scale(GRASP_PULL);
			foe.setDeltaMovement(in.x, 0.35, in.z);
			foe.needsSync = true;
			Vec3 from = player.getEyePosition(), to = foe.getBoundingBox().getCenter();
			for (int i = 0; i <= 12; i++) {
				Vec3 p = from.lerp(to, i / 12.0);
				level.sendParticles(new DustParticleOptions(COLOR, 1.4F), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
			}
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.2F, 0.5F);
		}
	}

	/** Every {@link #ROUTINE_EVERY} ticks while free: where the keeper is at this hour (the Grue keeps to it later). */
	private static void remember(ServerPlayer player) {
		if (Downed.isDowned(player) || player.isPassenger()) {
			return;
		}
		Map<String, String> routine = new HashMap<>(player.getAttachedOrElse(ROUTINE, Map.of()));
		routine.put(Integer.toString(hour(player)), player.level().dimension().identifier() + ";" + player.blockPosition().asLong());
		player.setAttached(ROUTINE, routine);
	}

	private static int hour(ServerPlayer player) {
		return (int)(Math.floorMod(player.level().getOverworldClockTime(), 24000L) / 1000L);
	}

	/** Where the keeper would be at this hour, in this world and not too far; else null. */
	private static @Nullable Vec3 routineSpot(ServerPlayer player, ServerLevel level) {
		String saved = player.getAttachedOrElse(ROUTINE, Map.<String, String>of()).get(Integer.toString(hour(player)));
		if (saved == null) {
			return null;
		}
		int cut = saved.lastIndexOf(';');
		if (cut < 0 || !saved.substring(0, cut).equals(level.dimension().identifier().toString())) {
			return null;
		}
		try {
			Vec3 spot = Vec3.atBottomCenterOf(BlockPos.of(Long.parseLong(saved.substring(cut + 1))));
			return spot.distanceTo(player.position()) <= ROUTINE_FAR ? spot : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** The keeper said {@code text} aloud ({@code ProximityChat}): kept for the Grue. False if the Grue has them (the words don't come out). */
	public static boolean heard(ServerPlayer player, String text) {
		if (taken(player)) {
			if (MUTE_HOST) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.grue.mute").withStyle(s -> s.withColor(COLOR)));
				return false;
			}
			return true;
		}
		if (Spirits.state(player) == null || text.length() > 120) {
			return true;
		}
		List<String> heard = new ArrayList<>(player.getAttachedOrElse(HEARD, List.of()));
		heard.add(text);
		while (heard.size() > HEARD_KEEP) {
			heard.remove(0);
		}
		player.setAttached(HEARD, heard);
		return true;
	}

	/** The Grue speaks as its host, in their own words, when someone is near to hear. */
	private static void mimic(ServerPlayer player, ServerLevel level) {
		List<String> heard = player.getAttachedOrElse(HEARD, List.of());
		if (heard.isEmpty()) {
			return;
		}
		boolean near = false;
		for (ServerPlayer other : level.players()) {
			if (other != player && !other.isSpectator() && other.distanceTo(player) <= MIMIC_NEAR) {
				near = true;
				break;
			}
		}
		if (near) {
			com.jaymoskingdoms.ProximityChat.speakAs(player, heard.get(player.getRandom().nextInt(heard.size())));
		}
	}

	/** The Grue's own voice, to its host only: {@code event} is whisper, taken, taunt or free. */
	private static void speak(ServerPlayer player, String event) {
		String key = "spirit.jaymoskingdoms.grue." + event + "." + (1 + player.getRandom().nextInt(GRUE_LINES));
		TextColor colour = TextColor.fromRgb(COLOR);
		MutableComponent name = Component.translatable("spirit.jaymoskingdoms.grue.name").withStyle(s -> s.withColor(colour).withItalic(true));
		MutableComponent line = Component.translatable(key).withStyle(s -> s.withColor(TextColor.fromRgb(0xB04050)).withItalic(true));
		player.sendSystemMessage(Component.empty().append(name).append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(line));
		Voices.speak(player, key);
	}

	private static void redBurst(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(new DustParticleOptions(COLOR, 1.8F), at.x, at.y, at.z, count, spread, spread, spread, 0.02);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, count / 3, spread, spread, spread, 0.02);
	}

	// ---- freed -----------------------------------------------------------------------------------------------------------

	/**
	 * {@code friend} gives a Purifying Draught to {@code host}, downed: the Grue is burnt out of them, and the spirit with it.
	 * Whether it happened (the draught is used up).
	 */
	static boolean exorcise(ServerPlayer friend, ServerPlayer host, InteractionHand hand) {
		ItemStack draught = friend.getItemInHand(hand);
		if (friend == host || !taken(host) || !draught(draught)) {
			return false;
		}
		if (!Downed.isDowned(host)) {
			friend.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.grue.not_down").withStyle(s -> s.withColor(COLOR)));
			return false;
		}
		if (!friend.isCreative()) {
			draught.shrink(1);
		}
		free(host);
		friend.sendSystemMessage(Component.translatable("message.jaymoskingdoms.grue.exorcised", host.getDisplayName()).withStyle(ChatFormatting.GRAY));
		return true;
	}

	private static boolean draught(ItemStack stack) {
		return !stack.isEmpty() && PURIFYING_DRAUGHT.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	/** The Grue is burnt out of {@code host}: it takes the spirit with it, and no other will ever come. */
	static void free(ServerPlayer host) {
		ServerLevel level = (ServerLevel)host.level();
		speak(host, "free");
		Spirits.putAway(host, null);
		host.removeAttached(GRUE);
		host.removeAttached(Spirits.SPIRIT);
		host.setAttached(Spirits.RELEASED, true);
		PowersCompat.corrupt(host, -EXORCISM_CURE);
		forget(host.getUUID());
		ServerPlayNetworking.send(host, new Drive(0.0, 0.0, 0.0, -1, 0.0F, 0, true));
		redBurst(level, host.position().add(0.0, 1.0, 0.0), 120, 1.5);
		level.sendParticles(ParticleTypes.END_ROD, host.getX(), host.getY() + 1.0, host.getZ(), 40, 0.6, 0.8, 0.6, 0.1);
		level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_DEATH, SoundSource.PLAYERS, 1.2F, 0.6F);
		host.sendSystemMessage(Component.translatable("message.jaymoskingdoms.grue.freed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static int freeCommand(CommandSourceStack source, ServerPlayer player) {
		if (!taken(player) && player.getAttached(GRUE) == null) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " has no Grue in them"));
			return 0;
		}
		if (taken(player)) {
			free(player);
		} else {
			player.removeAttached(GRUE);
		}
		source.sendSuccess(() -> Component.literal(player.getGameProfile().name() + " is free of the Grue"), true);
		return 1;
	}

	private static int holdCommand(CommandSourceStack source, ServerPlayer player, int n) {
		if (Spirits.state(player) == null) {
			source.sendFailure(Component.literal(player.getGameProfile().name() + " has no spirit"));
			return 0;
		}
		if (taken(player) && n < TAKEN) {
			player.setAttached(GRUE, new State(n, false));
			ServerPlayNetworking.send(player, new Drive(0.0, 0.0, 0.0, -1, 0.0F, 0, true));
		} else {
			setHold(player, n);
		}
		source.sendSuccess(() -> Component.literal("grue hold " + n), true);
		return 1;
	}
}
