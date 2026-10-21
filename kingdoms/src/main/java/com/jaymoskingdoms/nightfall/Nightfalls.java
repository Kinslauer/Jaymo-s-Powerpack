package com.jaymoskingdoms.nightfall;

import com.jaymoskingdoms.BedRest;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Vampires;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Nightmares, and the Nightfall itself (after Nightfall Plague's).
 *
 * <p><b>Nightmares.</b> Now and then someone who falls asleep can't wake: they lie in bed unable to move (leaving it, they
 * sink straight back), and after a few seconds they're torn awake with the vampires on them, a flock of vampire bats or
 * a pair of Blightwings about to be Night Bleeders. More likely with the plague in you ({@link Atrophy}), or with the
 * plague's things on you ({@link #dread}).
 *
 * <p><b>The Nightfall.</b> With the plague in someone, three things set it off: a totem of undying saving them, a
 * nightmare, or too many of the plague's creatures round them at once. For two or three nights after, the sun doesn't
 * rise (each dawn the clock runs on to the next nightfall, so the days still count), no one can sleep (creative players
 * aside), the vampires are stronger, the undead don't burn, fires smoke out fast, and a pale miasma hangs over everything
 * (the client's fog, {@code NightfallClient}). Operators: {@code /kingdom nightfall start|stop}.
 */
public final class Nightfalls {
	/** Nightmares: the chance on falling asleep, more with the plague, more for each of the plague's things carried (at most). */
	static final float NIGHTMARE_CHANCE = 0.03F, NIGHTMARE_ATROPHY = 0.15F, NIGHTMARE_PER_RELIC = 0.03F, NIGHTMARE_MAX = 0.5F;
	/** How long the sleeper lies paralysed (ticks), and how soon after falling asleep the nightmare's rolled (ticks). */
	static final int PARALYSIS_TICKS = 80, ROLL_AFTER = 20;
	/** What waits: this chance of bats (how many), else Blightwings (how many, and how soon they're Night Bleeders, ticks). */
	static final float BAT_HORDE_CHANCE = 0.55F;
	static final int HORDE_MIN = 5, HORDE_MAX = 7, WINGS = 2, WINGS_RETURN = 60;
	/** The Nightfall lasts this many nights at least, at most; and can't come again for this many days after. */
	static final int NIGHTS_MIN = 2, NIGHTS_MAX = 3, COOLDOWN_DAYS = 5;
	/** The time of day each dawn of it is skipped from, and to (the next nightfall). */
	static final long SKIP_AT = 22800L, SKIP_TO = 13000L;
	/** Too many of the plague's creatures: this many within this range of someone with the plague. */
	static final int PRESENCE_COUNT = 8;
	static final double PRESENCE_RANGE = 32.0;
	/** During it: vampires within this range of a player are stronger; fires within this range smoke out. */
	static final double EMPOWER_RANGE = 48.0;
	static final int FIRE_SAMPLES = 24, FIRE_REACH = 14, BURN_TICKS_MAX = 30;
	/** During it, round each player now and then (ticks), the chance of a flock of bats, and of a Night Bleeder. */
	static final int RAISE_EVERY = 400;
	static final float RAISE_BATS = 0.35F, RAISE_BLEEDER = 0.1F;

	/** The Overworld's Nightfall: its first and last nights ({@link Vampires#nightIndex}; -1 none), and no new one before this night. */
	record State(long from, long to, long calmUntil) {
		static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("from").forGetter(State::from),
			Codec.LONG.fieldOf("to").forGetter(State::to),
			Codec.LONG.fieldOf("calm_until").forGetter(State::calmUntil)).apply(i, State::new));
		static final State NONE = new State(-1L, -1L, -1L);
	}

	static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosKingdoms.id("nightfall"), builder -> builder.persistent(State.CODEC));

	/** Server → player: whether the Nightfall's miasma is over the Overworld now. */
	public record Sync(boolean active) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("nightfall"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = ByteBufCodecs.BOOL.map(Sync::new, Sync::active).cast();

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** On the client: whether the miasma's out. */
	public static volatile boolean clientActive;

	/** A sleeper held by a nightmare: their bed, when it lets go, and whether the plague's in them. */
	private record Paralysis(BlockPos bed, long until, boolean plagued) {
	}

	private static final Map<UUID, Paralysis> PARALYSED = new HashMap<>();
	/** Sleepers already rolled for a nightmare this sleep. */
	private static final Set<UUID> ROLLED = new HashSet<>();
	/** Players with the plague brought to death this tick, and how many totems they'd used. */
	private static final Map<UUID, Integer> DYING = new HashMap<>();
	private static boolean shown;

	private Nightfalls() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, new Sync(shown)));
		ServerTickEvents.END_SERVER_TICK.register(Nightfalls::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && p.hasEffect(Nightfall.ATROPHY)) {
				DYING.put(p.getUUID(), p.getStats().getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING)));
			}
			return true;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (shown && source.is(DamageTypeTags.IS_FIRE) && entity.is(EntityTypeTags.UNDEAD) && entity.level() instanceof ServerLevel level
				&& level == level.getServer().overworld()) {
				entity.clearFire();
				return false;
			}
			return true;
		});
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> shown && !player.isCreative() && !player.isSpectator()
			? new Player.BedSleepingProblem(Component.translatable("message.jaymoskingdoms.nightfall.no_sleep")) : null);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PARALYSED.clear();
			ROLLED.clear();
			DYING.clear();
			shown = false;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("nightfall").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("start").executes(c -> command(c.getSource(), true)))
				.then(Commands.literal("stop").executes(c -> command(c.getSource(), false))))));
	}

	// ---- the Nightfall -------------------------------------------------------------------------------------------------

	/** Whether the Nightfall's on over the server's Overworld just now (its nights, and the skipped days between). */
	public static boolean active(MinecraftServer server) {
		return shown;
	}

	private static boolean running(State s, long clock) {
		if (s.from() < 0L) {
			return false;
		}
		long idx = Vampires.nightIndex(clock);
		return idx >= s.from() && idx <= s.to() && (Vampires.night(clock) || idx < s.to());
	}

	/** Sets the Nightfall off (if one isn't on, or too recently over). True if it began. */
	static boolean begin(ServerLevel overworld, boolean forced) {
		long clock = overworld.getOverworldClockTime();
		State s = overworld.getAttachedOrElse(STATE, State.NONE);
		long idx = Vampires.nightIndex(clock);
		if (running(s, clock) || !forced && s.calmUntil() > idx) {
			return false;
		}
		long from = Vampires.night(clock) ? idx : idx + 1;
		int nights = Mth.nextInt(overworld.getRandom(), NIGHTS_MIN, NIGHTS_MAX);
		overworld.setAttached(STATE, new State(from, from + nights - 1, -1L));
		for (ServerPlayer p : overworld.getServer().getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.nightfall.begins").withStyle(net.minecraft.ChatFormatting.DARK_RED));
			p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WITHER_SPAWN), SoundSource.AMBIENT,
				p.getX(), p.getY(), p.getZ(), 0.5F, 0.5F, overworld.getRandom().nextLong()));
		}
		return true;
	}

	private static void end(ServerLevel overworld, long idx) {
		overworld.setAttached(STATE, new State(-1L, -1L, idx + COOLDOWN_DAYS));
		for (ServerPlayer p : overworld.getServer().getPlayerList().getPlayers()) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.nightfall.ends").withStyle(net.minecraft.ChatFormatting.GRAY));
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		long now = overworld.getGameTime();
		State s = overworld.getAttachedOrElse(STATE, State.NONE);
		boolean on = running(s, clock);
		if (s.from() >= 0L && !on && Vampires.nightIndex(clock) >= s.to()) {
			end(overworld, Vampires.nightIndex(clock));
		}
		if (on) {
			long t = Math.floorMod(clock, 24000L);
			if (t >= SKIP_AT && Vampires.nightIndex(clock) < s.to()) {
				long to = clock + (24000L - t) + SKIP_TO;
				overworld.dimensionType().defaultClock().ifPresent(c -> server.clockManager().setTotalTicks(c, to));
			}
		}
		// the miasma shows only by night (a Nightfall set off by day starts at dusk)
		boolean show = on && Vampires.night(clock);
		if (show != shown) {
			shown = show;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				ServerPlayNetworking.send(p, new Sync(show));
			}
		}
		if (!DYING.isEmpty()) {
			for (Map.Entry<UUID, Integer> e : DYING.entrySet()) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null && p.isAlive() && p.getStats().getValue(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING)) > e.getValue()) {
					// the totem kept them, and let the plague loose
					begin(overworld, false);
				}
			}
			DYING.clear();
		}
		nightmares(server, overworld, now);
		if (now % 20 == 0) {
			for (ServerPlayer p : overworld.players()) {
				if (shown) {
					during(overworld, p, now);
				} else if (now % 200 == 0 && p.hasEffect(Nightfall.ATROPHY) && presence(overworld, p) >= PRESENCE_COUNT) {
					begin(overworld, false);
				}
			}
		}
	}

	private static int presence(ServerLevel level, ServerPlayer p) {
		return level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(PRESENCE_RANGE), e -> e.isAlive() && Nightfall.kin(e) && e != p).size();
	}

	/** Once a second round each player while it's on: no sleep, stronger vampires, fires smoked out, more of them about. */
	private static void during(ServerLevel level, ServerPlayer p, long now) {
		if (p.isSleeping() && !BedRest.resting(p) && !p.isCreative() && !PARALYSED.containsKey(p.getUUID())) {
			p.stopSleepInBed(true, true);
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.no_sleep"));
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(EMPOWER_RANGE), Entity::isAlive)) {
			if (Nightfall.kin(e) && !Vampires.dhampir(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 0, true, false, true));
				e.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, true));
			}
			if (e.isOnFire()) {
				if (e.is(EntityTypeTags.UNDEAD)) {
					e.clearFire();
				} else if (e.getRemainingFireTicks() > BURN_TICKS_MAX) {
					e.setRemainingFireTicks(BURN_TICKS_MAX);
				}
			}
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < FIRE_SAMPLES; i++) {
			BlockPos pos = p.blockPosition().offset(random.nextInt(FIRE_REACH * 2 + 1) - FIRE_REACH, random.nextInt(9) - 4,
				random.nextInt(FIRE_REACH * 2 + 1) - FIRE_REACH);
			if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
				level.removeBlock(pos, false);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.02);
				level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
			}
		}
		if ((now + p.getId()) % RAISE_EVERY < 20 && !p.isCreative() && !p.isSpectator()) {
			float roll = random.nextFloat();
			if (roll < RAISE_BLEEDER) {
				raise(level, p, true, 1);
			} else if (roll < RAISE_BLEEDER + RAISE_BATS) {
				raise(level, p, false, Mth.nextInt(random, 2, 4));
			}
		}
	}

	/** A Night Bleeder or a flock of vampire bats, out of sight 20-30 blocks from {@code p}. */
	private static void raise(ServerLevel level, ServerPlayer p, boolean bleeder, int count) {
		RandomSource random = level.getRandom();
		double a = random.nextDouble() * Math.PI * 2.0, d = 20.0 + random.nextDouble() * 10.0;
		BlockPos column = BlockPos.containing(p.getX() + Math.cos(a) * d, p.getY(), p.getZ() + Math.sin(a) * d);
		if (!level.isLoaded(column)) {
			return;
		}
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		for (int i = 0; i < count; i++) {
			Entity e = bleeder ? Nightfall.NIGHT_BLEEDER.create(level, EntitySpawnReason.EVENT) : Nightfall.VAMPIRE_BAT.create(level, EntitySpawnReason.EVENT);
			if (e == null) {
				return;
			}
			e.snapTo(ground.getX() + 0.5 + random.nextGaussian(), ground.getY() + (bleeder ? 0.0 : 2.0 + random.nextDouble() * 3.0),
				ground.getZ() + 0.5 + random.nextGaussian(), random.nextFloat() * 360.0F, 0.0F);
			if (e instanceof NightBleeder b) {
				b.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), EntitySpawnReason.EVENT, null);
			} else if (e instanceof VampireBat bat) {
				bat.setResting(false);
			}
			level.addFreshEntity(e);
		}
	}

	// ---- nightmares ----------------------------------------------------------------------------------------------------

	/** How many of the plague's things {@code p} carries. */
	static int dread(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(Nightfall.CURSED_TUSK) || s.is(Nightfall.CURSED_CARD) || s.is(Nightfall.LUTE) || s.is(Nightfall.DRIED_MEAT)) {
				n++;
			}
		}
		return n;
	}

	private static void nightmares(MinecraftServer server, ServerLevel overworld, long now) {
		for (Iterator<Map.Entry<UUID, Paralysis>> it = PARALYSED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Paralysis> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Paralysis para = e.getValue();
			if (p == null || !p.isAlive() || p.level() != overworld) {
				it.remove();
				continue;
			}
			boolean bed = overworld.getBlockState(para.bed()).getBlock() instanceof AbstractBedBlock;
			if (now >= para.until() || !bed) {
				it.remove();
				wake(overworld, p, para);
			} else if (!p.isSleeping()) {
				// they try to get up, and sink straight back
				p.startSleeping(para.bed());
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.paralysed"));
			} else if ((now - para.until()) % 20 == 0) {
				sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.8F);
			}
		}
		if (now % 10 != 0) {
			return;
		}
		for (ServerPlayer p : overworld.players()) {
			UUID id = p.getUUID();
			if (!p.isSleeping() || BedRest.resting(p)) {
				ROLLED.remove(id);
				continue;
			}
			if (p.isCreative() || p.isSpectator() || p.getSleepTimer() < ROLL_AFTER || !ROLLED.add(id) || PARALYSED.containsKey(id)
				|| !Vampires.night(overworld.getOverworldClockTime())) {
				continue;
			}
			boolean plagued = p.hasEffect(Nightfall.ATROPHY);
			float chance = Math.min(NIGHTMARE_MAX, NIGHTMARE_CHANCE + (plagued ? NIGHTMARE_ATROPHY : 0.0F) + dread(p) * NIGHTMARE_PER_RELIC);
			if (overworld.getRandom().nextFloat() < chance) {
				BlockPos bed = p.getSleepingPos().orElse(p.blockPosition());
				PARALYSED.put(id, new Paralysis(bed, now + PARALYSIS_TICKS, plagued));
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, PARALYSIS_TICKS + 40, 0, false, false, false));
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.paralysed"));
				sound(p, SoundEvents.AMBIENT_CAVE.value(), 1.0F, 0.6F);
			}
		}
	}

	/** Torn awake, with them on you. */
	private static void wake(ServerLevel level, ServerPlayer p, Paralysis para) {
		if (p.isSleeping()) {
			p.stopSleepInBed(true, true);
		}
		RandomSource random = level.getRandom();
		Vec3 at = p.position();
		if (random.nextFloat() < BAT_HORDE_CHANCE) {
			int n = Mth.nextInt(random, HORDE_MIN, HORDE_MAX);
			for (int i = 0; i < n; i++) {
				VampireBat bat = Nightfall.VAMPIRE_BAT.create(level, EntitySpawnReason.EVENT);
				if (bat != null) {
					bat.snapTo(at.x + random.nextGaussian() * 2.0, at.y + 1.5 + random.nextDouble(), at.z + random.nextGaussian() * 2.0, random.nextFloat() * 360.0F, 0.0F);
					bat.summoned(p);
					level.addFreshEntity(bat);
				}
			}
		} else {
			for (int i = 0; i < WINGS; i++) {
				Blightwing wing = Nightfall.BLIGHTWING.create(level, EntitySpawnReason.EVENT);
				if (wing != null) {
					wing.snapTo(at.x + random.nextGaussian() * 2.5, at.y + 1.5, at.z + random.nextGaussian() * 2.5, random.nextFloat() * 360.0F, 0.0F);
					wing.setVariant(random.nextInt(NightBleeder.VARIANTS));
					wing.returnIn(WINGS_RETURN + random.nextInt(40));
					level.addFreshEntity(wing);
				}
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 30, 1.2, 0.6, 1.2, 0.02);
		sound(p, SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.6F);
		p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightfall.woken"));
		if (para.plagued()) {
			begin(level, false);
		}
	}

	private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE, p.getX(), p.getY(), p.getZ(),
			volume, pitch, p.getRandom().nextLong()));
	}

	private static int command(CommandSourceStack source, boolean start) {
		ServerLevel overworld = source.getServer().overworld();
		if (start) {
			if (!begin(overworld, true)) {
				source.sendFailure(Component.literal("A Nightfall is already on."));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("The Nightfall begins."), true);
			return 1;
		}
		end(overworld, Vampires.nightIndex(overworld.getOverworldClockTime()));
		source.sendSuccess(() -> Component.literal("The Nightfall is over."), true);
		return 1;
	}

	static @Nullable State state(ServerLevel overworld) {
		return overworld.getAttached(STATE);
	}
}
