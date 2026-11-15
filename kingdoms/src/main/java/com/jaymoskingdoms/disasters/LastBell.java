package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.DisasterRelief;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Loaded;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Last Bell (medieval picks, nd-harbinger; Andy: "doomsday should be tied to the arsenal. maybe he gets an artifact
 * that warns him how powerful and destructive it can be. his activation will bring upon the end of the world."). A
 * relic the Pocket Arsenal pulls through late in its mastery (Powers, through {@code KingdomsCompat}; one in the world
 * at a time). Rung, it shows its holder the land about on a map ({@code BellMapScreen}); where they pick, the
 * disaster that land holds falls: a wave out of the sea, a mountain opening in the hot lands, the snow letting go, the
 * ground breaking in the hills, and anywhere else a star. Each ringing costs dear (levels, blood, hunger, and a day
 * before it will sound again), and each warns its holder with worse visions: the sky flashing red, the ground shaking
 * under them, a toll from nowhere. After {@link #LESSER} it rings for nothing less than the end: held through its last
 * warning ({@link #FINAL_HOLD}), it shatters and the world's ending comes ({@link Doomsday}).
 */
public final class LastBell extends Item {
	/** The lesser ringings before it will only ring the end. */
	static final int LESSER = 4;
	/** The first ringing's strength; each one after is a magnitude worse ({@link Magnitude}). */
	static final int FIRST_MAGNITUDE = 3;
	/** Its costs: levels (and more each ringing), hurt (and more each ringing), hunger, and the wait (ticks). */
	static final int LEVELS = 10, LEVELS_PER_RING = 5, HUNGER = 6;
	static final float HURT = 6.0F, HURT_PER_RING = 2.0F;
	static final long WAIT = 24000L;
	/** How far from its holder a spot may be picked (blocks; the map's half-width). */
	public static final int RANGE = 512;
	/** The last ringing: held this long (ticks), a worse vision every so often. */
	static final int FINAL_HOLD = 200, VISION_EVERY = 40;
	/** A bell not seen in anyone's hands for this long is lost, and the Arsenal may pull another (ticks); how often it's seen. */
	static final long LOST_AFTER = 7L * 24000L;
	static final int SEEN_EVERY = 1200;
	private static final int FOREVER = 72000;

	public static Item ITEM;
	/** How many times it has been rung; when it may next be. */
	public static DataComponentType<Integer> RINGS;
	public static DataComponentType<Long> NEXT;

	/** Whether a bell is out in the world, and when one was last seen. */
	record Out(boolean out, long seen) {
		static final Out NONE = new Out(false, 0L);
		static final Codec<Out> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.fieldOf("out").forGetter(Out::out),
			Codec.LONG.fieldOf("seen").forGetter(Out::seen)).apply(i, Out::new));
	}

	static final AttachmentType<Out> OUT = AttachmentRegistry.create(JaymosKingdoms.id("last_bell"), b -> b.persistent(Out.CODEC));

	/** Server → holder: the map opens, the land within {@code range} of them. */
	public record OpenMap(int range) implements CustomPacketPayload {
		public static final Type<OpenMap> TYPE = new Type<>(JaymosKingdoms.id("last_bell_map"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenMap> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.range), buf -> new OpenMap(buf.readVarInt()));

		@Override
		public Type<OpenMap> type() {
			return TYPE;
		}
	}

	/** Holder → server: they rang it over (x, z). */
	public record Ring(int x, int z) implements CustomPacketPayload {
		public static final Type<Ring> TYPE = new Type<>(JaymosKingdoms.id("last_bell_ring"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ring> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.x);
				buf.writeVarInt(p.z);
			}, buf -> new Ring(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Ring> type() {
			return TYPE;
		}
	}

	/** Server → holder: a vision, as bad as {@code level} (1 the first ringing; past {@link #LESSER}, the end's). */
	public record Vision(int level) implements CustomPacketPayload {
		public static final Type<Vision> TYPE = new Type<>(JaymosKingdoms.id("last_bell_vision"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Vision> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.level), buf -> new Vision(buf.readVarInt()));

		@Override
		public Type<Vision> type() {
			return TYPE;
		}
	}

	LastBell(Item.Properties properties) {
		super(properties);
	}

	static void register() {
		RINGS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("bell_rings"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		NEXT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("bell_next"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("last_bell"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new LastBell(new Item.Properties().setId(key).stacksTo(1)
			.rarity(Rarity.EPIC).fireResistant()));
		PayloadTypeRegistry.clientboundPlay().register(OpenMap.TYPE, OpenMap.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Vision.TYPE, Vision.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ring.TYPE, Ring.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ring.TYPE, (payload, context) -> context.server().execute(() -> ring(context.player(), payload.x(), payload.z())));
	}

	// ---- one in the world -------------------------------------------------------------------------------------------

	/** (Powers, through reflection) Whether a bell is out in the world now. */
	public static boolean isOut(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Out o = level.getAttachedOrElse(OUT, Out.NONE);
		return o.out() && level.getGameTime() - o.seen() < LOST_AFTER;
	}

	/** (Powers, through reflection) A new bell (it isn't out until {@link #made}: the Arsenal shows one as its rift opens). */
	public static ItemStack make(MinecraftServer server) {
		return new ItemStack(ITEM);
	}

	/** (Powers, through reflection: the Arsenal has pulled it through) A bell is out in the world now. */
	public static void made(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.setAttached(OUT, new Out(true, level.getGameTime()));
	}

	/** (Doomsday) The end it rang has passed: no bell is out now. */
	static void passed(ServerLevel level) {
		level.setAttached(OUT, Out.NONE);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (level.getGameTime() % SEEN_EVERY == 0) {
			ServerLevel overworld = level.getServer().overworld();
			Out o = overworld.getAttachedOrElse(OUT, Out.NONE);
			overworld.setAttached(OUT, new Out(true, Math.max(o.seen(), overworld.getGameTime())));
		}
	}

	// ---- ringing ----------------------------------------------------------------------------------------------------

	private static boolean ready(ServerLevel level, ItemStack stack) {
		return level.dimension() == Level.OVERWORLD && level.getGameTime() >= stack.getOrDefault(NEXT, 0L) && !Doomsday.active(level)
			&& !Doomsday.state(level).on();
	}

	private static void dull(ServerLevel level, Player player) {
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.4F, 0.25F);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.getOrDefault(RINGS, 0) >= LESSER) {
			if (level instanceof ServerLevel server && !ready(server, stack)) {
				dull(server, player);
				return InteractionResult.FAIL;
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}
		if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
			if (!ready(server, stack)) {
				dull(server, player);
				return InteractionResult.FAIL;
			}
			ServerPlayNetworking.send(sp, new OpenMap(RANGE));
		}
		return InteractionResult.SUCCESS;
	}

	private static @Nullable ItemStack held(ServerPlayer p) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (p.getItemInHand(hand).is(ITEM)) {
				return p.getItemInHand(hand);
			}
		}
		return null;
	}

	/** The holder picked (x, z) on the map: a lesser ringing. */
	private static void ring(ServerPlayer p, int x, int z) {
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack stack = held(p);
		if (stack == null || !ready(level, stack)) {
			return;
		}
		int rings = stack.getOrDefault(RINGS, 0);
		double dx = x - p.getX(), dz = z - p.getZ();
		if (rings >= LESSER || dx * dx + dz * dz > (double)RANGE * RANGE || !Loaded.made(level, x, z)) {
			dull(level, p);
			return;
		}
		int levels = LEVELS + LEVELS_PER_RING * rings;
		if (!p.isCreative() && p.experienceLevel < levels) {
			dull(level, p);
			return;
		}
		int m = Magnitude.clamp(FIRST_MAGNITUDE + rings);
		if (!fall(level, x, z, m, level.getRandom())) {
			dull(level, p);
			return;
		}
		if (!p.isCreative()) {
			p.giveExperienceLevels(-levels);
			p.getFoodData().setFoodLevel(Math.max(0, p.getFoodData().getFoodLevel() - HUNGER));
		}
		long next = level.getGameTime() + WAIT;
		stack.set(RINGS, rings + 1);
		stack.set(NEXT, next);
		p.getCooldowns().addCooldown(stack, (int)WAIT);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 3.0F, 0.6F - 0.05F * rings);
		level.playSound(null, x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z, SoundEvents.BELL_RESONATE, SoundSource.AMBIENT, 8.0F, 0.4F);
		Fx.ring(level, p.position().add(0.0, 1.0, 0.0), 2.5F + rings, 0x8A1408);
		p.hurtServer(level, level.damageSources().magic(), HURT + HURT_PER_RING * rings);
		ServerPlayNetworking.send(p, new Vision(rings + 1));
	}

	/** What the land at (x, z) holds falls on it, as strong as {@code m}. Whether anything could. */
	static boolean fall(ServerLevel level, int x, int z, int m, RandomSource random) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos top = new BlockPos(x, y, z);
		Vec3 at = Vec3.atBottomCenterOf(top);
		if (Tsunamis.atSea(level, x + 0.5, z + 0.5)) {
			return Tsunamis.from(level, at, m) || Hurricanes.start(level, at, m) != null;
		}
		if (Earthquakes.mountainous(level, top)) {
			if (Volcanoes.hot(level, top) && Volcanoes.wakeNear(level, top, m, true)) {
				return true;
			}
			if (Avalanches.snowyHills(level, top) && Avalanches.near(level, top, m, random)) {
				Earthquakes.start(level, at, Math.max(1, m - 1), random, true);
				return true;
			}
			Calamity c = Earthquakes.start(level, at, Math.max(m, Magnitude.GREAT), random);
			if (c != null) {
				DisasterRelief.struck(level, c.x, c.z, c.radius, "quake", Earthquakes.magnitude(c));
				return true;
			}
		}
		return Bolides.fall(level, at, m, false);
	}

	// ---- the last ringing -------------------------------------------------------------------------------------------

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return FOREVER;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (!(user instanceof ServerPlayer p) || !(level instanceof ServerLevel server) || stack.getOrDefault(RINGS, 0) < LESSER) {
			return;
		}
		int held = FOREVER - remaining;
		if (held % VISION_EVERY == 0) {
			ServerPlayNetworking.send(p, new Vision(LESSER + 1 + held / VISION_EVERY));
			server.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0F + held / 40.0F, 0.4F);
		}
		if (held < FINAL_HOLD || !ready(server, stack)) {
			return;
		}
		// it shatters, and the end comes
		server.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 6.0F, 0.3F);
		server.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 3.0F, 0.4F);
		Fx.blast(server, p.position().add(0.0, 1.0, 0.0), 6.0F, 0x8A1408);
		p.stopUsingItem();
		stack.shrink(1);
		Doomsday.rung(server.getServer());
	}
}
