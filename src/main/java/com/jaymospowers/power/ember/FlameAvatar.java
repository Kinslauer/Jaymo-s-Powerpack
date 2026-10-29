package com.jaymospowers.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.151) The <b>Flame Avatar</b>, what an Emberborn becomes when the fire in them wins. Andy: "an emberborn variant
 * that triggers whenever an emberborn spends too many of its years in the nether, they become consumed by the flame already
 * within them, turning into an avatar of flame, capable of immense destruction. It should be seen as a problem for
 * emberborn to control their fire, and this form is the end result of one failing to control."
 * <p>
 * <b>The way in:</b> every second an Emberborn spends in the Nether adds to their {@link #NETHER} count; every second
 * elsewhere takes back a quarter of that ({@link #COOL_SHARE}), so short trips never add up. At half, three quarters
 * and nine tenths of {@link #CONSUME_TICKS} they're warned, vaguely ({@code message.jaymospowers.avatar.sign_*}); past
 * three quarters flames lick off them now and then. At the full count the fire takes them: a burst of flame round
 * them, and from then on they're an avatar ({@link #AVATAR}: saved, kept through death; {@code /power avatar} for ops).
 * It lasts only while they're still of Emberborn blood, and (Andy) until they go down in water: the damage tick that
 * would drop them there puts the fire out, and they're plain Emberborn again ({@link #doused}).
 * <p>
 * <b>Passive, Flaring body:</b> never more than {@link #MAX_HEARTS} hearts, whatever else adds health. In their place a
 * bar of <b>flame</b> ({@link #MAX_FLAME}): a hit (anything with an attacker, a projectile or a blast; not falls,
 * drowning, hunger or the void) is taken from the flame first, and the body flares (whoever struck them in reach is
 * set alight). What the flame can't cover goes on to the hearts. The flame comes back standing in fire or lava, and
 * from eating ({@link #FOOD_FLAME} a food point); slower in the cold ({@link #CHILL_REGEN}). Rain gutters it slowly;
 * water drains it fast, and in water with no flame left the avatar takes harm ({@link #DOUSED_DAMAGE}).
 * <p>
 * Their moves ({@link AvatarMoves}): R Fire Fist, G Flaming Pillar (tap: round them; hold: at the cursor), B Ignition
 * Switch (tap: light what's in front; hold: set off everything burning nearby). With no power they're the plain
 * keys; beside a power, sneak + the key (the client decides, like Kindle's sneak + left hold).
 */
public final class FlameAvatar {
	// ---- tuning ----
	/** Ticks in the Nether (net of the cooling) before the fire takes them: 7 in-game days, about 2 h 20 min of play. */
	public static final int CONSUME_TICKS = 7 * 24000;
	/** Outside the Nether the count cools by this share of the rate it builds. */
	static final float COOL_SHARE = 0.25F;
	/** The warnings: these shares of the way there. */
	static final float SIGN_1 = 0.5F, SIGN_2 = 0.75F, SIGN_3 = 0.9F;
	/** Hearts an avatar has at most, whatever adds health. */
	public static final int MAX_HEARTS = 3;
	/** The flame bar's size (one flame takes one point of damage). */
	public static final float MAX_FLAME = 20.0F;
	/** Flame a tick standing in lava, and in fire (a fire block, a lit campfire, magma). */
	static final float LAVA_FLAME = 0.5F, FIRE_FLAME = 0.25F;
	/** Flame for every food point eaten. */
	static final float FOOD_FLAME = 1.5F;
	/** Chilled (a cold land, freezing, winter): regen at this share. */
	static final float CHILL_REGEN = 0.6F;
	/** Flame lost a tick in water, and in the rain. */
	static final float WATER_DRAIN = 0.25F, RAIN_DRAIN = 0.02F;
	/** In water with no flame left: this much harm every {@link #DOUSED_EVERY} ticks. */
	static final float DOUSED_DAMAGE = 1.0F;
	static final int DOUSED_EVERY = 20;
	/** A hit inside this many ticks of the last one costs only what it adds over it (vanilla's invulnerable frames). */
	static final int HIT_FRAMES = 10;
	/** The flaring body sets an attacker this close alight (blocks), for this long (seconds). */
	static final double FLARE_REACH = 4.0;
	static final int FLARE_BURN_SECONDS = 3;
	/** The consuming burst: fires started round them (radius, blocks), and how many tries. */
	static final int BURST_RADIUS = 4, BURST_FIRES = 24;
	/** How often (ticks) the count and the hearts are looked at. */
	static final int CHECK_EVERY = 20;
	/** The scoreboard tag on every avatar, for the other mods. */
	public static final String TAG = "jaymospowers_flame_avatar";

	/** Ticks in the Nether so far (net). Saved, kept through death; not synced. */
	public static final AttachmentType<Integer> NETHER = AttachmentRegistry.create(JaymosPowers.id("ember_nether"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** An avatar: saved, kept through death, seen by all (the look). */
	public static final AttachmentType<Boolean> AVATAR = AttachmentRegistry.create(JaymosPowers.id("flame_avatar"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** The flame bar in tenths of {@link #MAX_FLAME} steps (0-200), seen by all (the HUD; how fiercely they burn). Not saved. */
	public static final AttachmentType<Integer> FLAME = AttachmentRegistry.create(JaymosPowers.id("avatar_flame"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Client → server: an avatar's key ({@code key}: the {@code PowerKey} ordinal) pressed or let go. */
	public record Key(int key, boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("avatar_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Key::key, ByteBufCodecs.BOOL, Key::down, Key::new);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	private static final Identifier HEARTS = JaymosPowers.id("flame_avatar");

	/** One avatar's flame on the server. */
	static final class Live {
		float flame = MAX_FLAME;
		int sent = -1;
		int food = -1;
		/** The last hit taken from the flame: when (game time) and how much. */
		long hitAt = Long.MIN_VALUE;
		float hitFor;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	/** Set while the remainder of a hit is passed on, or the water does its harm, so it isn't taken from the flame again. */
	private static boolean passing;

	private FlameAvatar() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, context) -> key(context.player(), payload.key(), payload.down()));
		ServerTickEvents.END_SERVER_TICK.register(FlameAvatar::serverTick);
		// back from death with the fire full again
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((old, fresh, alive) -> LIVE.remove(fresh.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			AvatarMoves.clear();
		});
		AvatarMoves.init();
	}

	/** Whether {@code player} is a Flame Avatar now (either side): consumed, and still of Emberborn blood. */
	public static boolean is(Player player) {
		return Boolean.TRUE.equals(player.getAttached(AVATAR)) && EmberKindle.ember(player);
	}

	/** {@code player}'s flame, 0-1 (either side). */
	public static float flame(Player player) {
		Integer tenths = player.getAttached(FLAME);
		return tenths == null ? 0.0F : tenths / (MAX_FLAME * 10.0F);
	}

	static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player.getUUID(), k -> new Live());
	}

	/** Takes {@code cost} flame if {@code player} has it (creative always has). True if they did. */
	static boolean spend(ServerPlayer player, float cost) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		Live live = live(player);
		if (live.flame < cost) {
			return false;
		}
		live.flame -= cost;
		return true;
	}

	/** Gives {@code player} back {@code amount} flame (up to the full bar). */
	static void feed(ServerPlayer player, float amount) {
		Live live = live(player);
		live.flame = Math.min(MAX_FLAME, live.flame + amount);
	}

	private static void key(ServerPlayer player, int key, boolean down) {
		if (down && (!is(player) || !player.isAlive() || player.isSpectator() || Downed.isDowned(player) || Powers.silenced(player))) {
			return;
		}
		AvatarMoves.key(player, key, down);
	}

	// ---- the way in ----------------------------------------------------------------------------------------------------

	private static void serverTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean avatar = is(player);
			if (player.tickCount % CHECK_EVERY == 0) {
				if (!avatar) {
					count(player);
				}
				hearts(player, avatar);
				if (avatar != player.entityTags().contains(TAG)) {
					if (avatar) {
						player.addTag(TAG);
					} else {
						player.removeTag(TAG);
					}
				}
			}
			if (avatar) {
				tick(player, live(player));
			} else if (LIVE.remove(player.getUUID()) != null || player.hasAttached(FLAME)) {
				player.removeAttached(FLAME);
			}
		}
		LIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		AvatarMoves.tick(server);
	}

	/** Once a second for an Emberborn not yet consumed: the Nether count builds or cools, the warnings, the fire taking them. */
	private static void count(ServerPlayer player) {
		if (!EmberKindle.ember(player) || player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		int was = player.getAttachedOrElse(NETHER, 0);
		boolean nether = player.level().dimension() == Level.NETHER;
		int now = nether ? was + CHECK_EVERY : Math.max(0, was - Math.round(CHECK_EVERY * COOL_SHARE));
		if (now == was) {
			return;
		}
		if (now == 0) {
			player.removeAttached(NETHER);
		} else {
			player.setAttached(NETHER, now);
		}
		if (!nether) {
			return;
		}
		float share = now / (float)CONSUME_TICKS;
		float before = was / (float)CONSUME_TICKS;
		for (int i = 0; i < 3; i++) {
			float at = i == 0 ? SIGN_1 : i == 1 ? SIGN_2 : SIGN_3;
			if (before < at && share >= at) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.sign_" + (i + 1))
					.withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.5F);
			}
		}
		if (share >= SIGN_2 && player.getRandom().nextInt(4) == 0) {
			// the fire showing through: a lick of flame off them
			player.level().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
		}
		if (now >= CONSUME_TICKS) {
			consume(player);
		}
	}

	/** The fire takes {@code player}: from now on they're an avatar. */
	public static void consume(ServerPlayer player) {
		player.setAttached(AVATAR, true);
		player.removeAttached(NETHER);
		live(player).flame = MAX_FLAME;
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 160, 1.2, 1.4, 1.2, 0.2);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 30, 1.0, 1.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 40, 1.0, 1.5, 1.0, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 1.5F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.6F);
		Shakes.around(level, at, 16.0, 6.0F);
		Flames.area(level, at, BURST_RADIUS, 6, player, e -> true);
		var random = player.getRandom();
		for (int i = 0; i < BURST_FIRES; i++) {
			BlockPos pos = player.blockPosition().offset(random.nextInt(BURST_RADIUS * 2 + 1) - BURST_RADIUS, random.nextInt(3) - 1,
				random.nextInt(BURST_RADIUS * 2 + 1) - BURST_RADIUS);
			EmberKindle.ignite(level, pos);
		}
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 30));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.jaymospowers.avatar.consumed")
			.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.jaymospowers.avatar.consumed_sub")
			.withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)));
	}

	/** Makes {@code player} plain Emberborn again (ops only, by command). */
	public static void release(ServerPlayer player) {
		player.removeAttached(AVATAR);
		player.removeAttached(FLAME);
		player.removeAttached(NETHER);
		LIVE.remove(player.getUUID());
		hearts(player, false);
	}

	/** An avatar's hearts held to {@link #MAX_HEARTS}, whatever else adds health (or the hold let go). */
	private static void hearts(ServerPlayer player, boolean avatar) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		AttributeModifier ours = health.getModifier(HEARTS);
		if (!avatar) {
			if (ours != null) {
				health.removeModifier(HEARTS);
			}
			return;
		}
		double others = health.getValue() - (ours == null ? 0.0 : ours.amount());
		double amount = MAX_HEARTS * 2.0 - others;
		if (ours == null || Math.abs(ours.amount() - amount) > 1.0E-3) {
			health.addOrUpdateTransientModifier(new AttributeModifier(HEARTS, amount, AttributeModifier.Operation.ADD_VALUE));
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---- the flaring body ----------------------------------------------------------------------------------------------

	/** Every tick for an avatar: the flame's regen and loss, the water's harm, the sync. */
	private static void tick(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		float regen = 1.0F;
		if (EmberKindle.chilled(player)) {
			regen = CHILL_REGEN;
		}
		int food = player.getFoodData().getFoodLevel();
		if (live.food >= 0 && food > live.food) {
			live.flame += (food - live.food) * FOOD_FLAME * regen;
		}
		live.food = food;
		if (player.isInLava()) {
			live.flame += LAVA_FLAME * regen;
		} else if (inFire(level, player.blockPosition())) {
			live.flame += FIRE_FLAME * regen;
		}
		if (player.isInWater()) {
			live.flame -= WATER_DRAIN;
			if (player.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.2, player.getZ(), 3, 0.3, 0.5, 0.3, 0.03);
			}
			if (player.tickCount % 10 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.2F);
			}
			if (live.flame <= 0.0F && player.tickCount % DOUSED_EVERY == 0 && !player.getAbilities().invulnerable) {
				passing = true;
				try {
					player.hurtServer(level, level.damageSources().drown(), DOUSED_DAMAGE);
				} finally {
					passing = false;
				}
			}
		} else if (player.isInWaterOrRain()) {
			live.flame -= RAIN_DRAIN;
			if (player.tickCount % 20 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.8, player.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
			}
		}
		live.flame = Mth.clamp(live.flame, 0.0F, MAX_FLAME);
		int tenths = Math.round(live.flame * 10.0F);
		if (tenths != live.sent) {
			live.sent = tenths;
			player.setAttached(FLAME, tenths);
		}
	}

	/** Fire at their feet: a fire block, a lit campfire, magma. */
	private static boolean inFire(ServerLevel level, BlockPos feet) {
		for (BlockPos pos : new BlockPos[] {feet, feet.above(), feet.below()}) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BaseFireBlock || state.is(Blocks.MAGMA_BLOCK)
				|| state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)
				&& AvatarMoves.path(state).endsWith("campfire")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * From {@code LivingEntity.hurtServer}'s head (mixin): an avatar's flame takes the hit first. Returns true if the hit is
	 * to be dropped (the flame took it, or it's passed on with only what the flame couldn't cover).
	 */
	public static boolean absorb(LivingEntity entity, ServerLevel level, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player && amount > 0.0F && is(player) && player.isInWater() && !player.isCreative() && !player.isSpectator()) {
			// (Andy) going down in water undoes the avatar, on the very tick of the damage that drops them
			float through = passing || !hit(source) ? amount : Math.max(0.0F, amount - live(player).flame);
			if (through >= player.getHealth()) {
				doused(player, level);
				return false;
			}
		}
		// (0.18.7) a Valor-coated blow goes through the flame to the body
		if (passing || !(entity instanceof ServerPlayer player) || amount <= 0.0F || !is(player) || !hit(source)
			|| player.isCreative() || player.isSpectator() || com.jaymospowers.core.wounds.ValorSkill.pierces(source)) {
			return false;
		}
		Live live = live(player);
		long now = level.getGameTime();
		float cost = amount;
		if (now - live.hitAt < HIT_FRAMES && now >= live.hitAt) {
			// inside the last hit's frames only what it adds over that one counts, as in vanilla
			if (amount <= live.hitFor) {
				return true;
			}
			cost = amount - live.hitFor;
		} else {
			live.hitAt = now;
			live.hitFor = 0.0F;
		}
		if (live.flame <= 0.0F) {
			return false;
		}
		live.hitFor = Math.max(live.hitFor, amount);
		flare(player, level, source);
		if (live.flame >= cost) {
			live.flame -= cost;
			return true;
		}
		float rest = cost - live.flame;
		live.flame = 0.0F;
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.guttered").withStyle(ChatFormatting.RED));
		passing = true;
		try {
			player.setInvulnerableTime(0);
			player.hurtServer(level, source, rest);
		} finally {
			passing = false;
		}
		return true;
	}

	/** Dropped in water: the fire goes out of them and they're plain Emberborn again (the Nether count starts over). */
	private static void doused(ServerPlayer player, ServerLevel level) {
		release(player);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.6, 0.9, 0.6, 0.08);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.4, player.getZ(), 20, 0.5, 0.6, 0.5, 0.04);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_DEATH, SoundSource.PLAYERS, 0.8F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.avatar.doused").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
	}

	/** A blow, a shot or a blast (not a fall, drowning, hunger, the void, fire). */
	private static boolean hit(DamageSource source) {
		// (fire never hurts an Emberborn anyway: Kingdoms drops it, so it mustn't cost flame either)
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
			|| source.is(DamageTypeTags.IS_FIRE)) {
			return false;
		}
		return source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION);
	}

	/** The body flares where it's struck: flame bursts off them, and whoever struck them in reach catches. */
	private static void flare(ServerPlayer player, ServerLevel level, DamageSource source) {
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.35, 0.6, 0.35, 0.06);
		level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_HURT, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
		Entity attacker = source.getDirectEntity();
		if (attacker != null && attacker != player && attacker.distanceToSqr(player) <= FLARE_REACH * FLARE_REACH) {
			Flames.touch(level, attacker, FLARE_BURN_SECONDS, player);
		}
	}

	// ---- command -------------------------------------------------------------------------------------------------------

	/** {@code /power avatar <targets> on|off|progress}. */
	public static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("avatar")
			.then(Commands.argument("targets", EntityArgument.players())
				.then(Commands.literal("on").executes(c -> {
					Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
					players.forEach(FlameAvatar::consume);
					c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.avatar.on", players.size()), true);
					return players.size();
				}))
				.then(Commands.literal("off").executes(c -> {
					Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
					players.forEach(FlameAvatar::release);
					c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.avatar.off", players.size()), true);
					return players.size();
				}))
				.then(Commands.literal("progress").executes(c -> {
					Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "targets");
					for (ServerPlayer p : players) {
						int ticks = p.getAttachedOrElse(NETHER, 0);
						c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.avatar.progress", p.getDisplayName(),
							Boolean.TRUE.equals(p.getAttached(AVATAR)) ? "consumed" : (ticks * 100 / CONSUME_TICKS) + "%"), false);
					}
					return players.size();
				})));
	}
}
