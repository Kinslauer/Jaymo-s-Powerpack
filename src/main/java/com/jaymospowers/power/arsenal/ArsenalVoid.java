package com.jaymospowers.power.arsenal;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Void Arsenal: Pocket Arsenal's corruption. The pocket dimension is the End's neighbour, and the things that live
 * between them notice you. Every brush with an Enderman fills the meter; setting foot in the End corrupts outright.
 * Corrupted, the rift opens onto the void itself: summoned weapons hit harder and carry darkness and wither, but water
 * and rain burn like they burn an Enderman, and a heavy hit can blink you away against your will.
 *
 * <p>The cure: be there when the Ender Dragon dies, having fought it. The void is torn out of you.
 */
public final class ArsenalVoid {
	// ---- corruption sources (meter points; the meter is 0-100)
	/** An Enderman sets its sights on you. */
	public static final int CORRUPT_AGGRO = 2;
	/** You hit an Enderman, or one hits you. */
	public static final int CORRUPT_HIT = 4;
	/** You kill an Enderman. */
	public static final int CORRUPT_KILL = 10;
	/** The meter whispers as it crosses these. */
	public static final int[] WHISPER_AT = {25, 50, 75};

	// ---- the trade
	/** Summoned weapons in corrupted hands deal this much more. */
	public static final float DAMAGE_MULTIPLIER = 1.3F;
	/** Their hits darken and wither for this long (ticks). */
	public static final int DARKNESS_TICKS = 60;
	public static final int WITHER_TICKS = 60;
	public static final int WITHER_AMPLIFIER = 0;
	/** Water and rain burn: this much damage every {@link #WATER_INTERVAL} ticks. */
	public static final float WATER_DAMAGE = 1.0F;
	public static final int WATER_INTERVAL = 20;
	/** A hit of at least this much has this chance to blink you {@link #BLINK_MIN}-{@link #BLINK_MAX} blocks away. */
	public static final float BLINK_MIN_DAMAGE = 6.0F;
	public static final float BLINK_CHANCE = 0.3F;
	public static final double BLINK_MIN = 4.0;
	public static final double BLINK_MAX = 8.0;
	public static final int BLINK_ATTEMPTS = 16;

	// ---- the cure
	/** Corrupted fighters within this many blocks of the dragon when it dies are purified. */
	public static final double CURE_RADIUS = 160.0;
	/** How long the void takes to be torn out (ticks) before the purification lands. */
	public static final int PURGE_TICKS = 70;
	/** Or it wears off on its own: three Minecraft days after it took you (world time, so it runs while you're away). */
	public static final long CURE_TICKS = 3L * 24000L;
	/** When the void took this player (Overworld game time). Saved, kept through death. */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Long> VOID_SINCE =
		net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(com.jaymospowers.JaymosPowers.id("arsenal_void_since"),
			builder -> builder.persistent(com.mojang.serialization.Codec.LONG).copyOnDeath());

	/** Dragon → the players who hurt it this fight. Server memory only. */
	private static final Map<UUID, Set<UUID>> DRAGON_FIGHTERS = new HashMap<>();
	/** Player → game time their purge started. */
	private static final Map<UUID, Long> PURGES = new HashMap<>();

	private ArsenalVoid() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ArsenalVoid::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(ArsenalVoid::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(ArsenalVoid::serverTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// leaving mid-purge: the cure still lands
			if (PURGES.remove(handler.player.getUUID()) != null) {
				Fate.purify(handler.player);
			}
		});
	}

	// ---------------------------------------------------------------- queries

	public static boolean isArsenal(@Nullable Entity entity) {
		return entity instanceof ServerPlayer player && Powers.has(player, ArsenalPower.INSTANCE);
	}

	/** An arsenal user whose power is corrupted (either side). */
	public static boolean isVoid(@Nullable Entity entity) {
		return entity instanceof net.minecraft.world.entity.player.Player player && Powers.has(player, ArsenalPower.INSTANCE)
			&& Fate.isCorrupted(player);
	}

	// ---------------------------------------------------------------- the meter

	/** Moves the meter (through {@link Fate#addCorruption}), whispering as it crosses {@link #WHISPER_AT}. */
	public static void addCorruption(ServerPlayer player, int amount) {
		if (!Powers.has(player, ArsenalPower.INSTANCE)) {
			return;
		}
		int before = Fate.corruption(player);
		Fate.addCorruption(player, amount);
		if (Fate.isCorrupted(player)) {
			return;
		}
		int after = Fate.corruption(player);
		for (int i = WHISPER_AT.length - 1; i >= 0; i--) {
			if (before < WHISPER_AT[i] && after >= WHISPER_AT[i]) {
				whisper(player, "message.jaymospowers.arsenal.whisper_" + (i + 1), SoundEvents.ENDERMAN_AMBIENT, 0.35F + 0.15F * i, 0.5F - 0.05F * i);
				break;
			}
		}
	}

	/** A line in the action bar and a sound only this player hears. */
	static void whisper(ServerPlayer player, String key, SoundEvent sound, float volume, float pitch) {
		player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.AMBIENT,
			player.getX(), player.getEyeY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	/** Called from {@code EnderManMixin}: an Enderman just picked a new target. */
	public static void endermanTargets(Enderman enderman, @Nullable LivingEntity previous, @Nullable LivingEntity target) {
		if (target != previous && target instanceof ServerPlayer player && isArsenal(player)) {
			addCorruption(player, CORRUPT_AGGRO);
		}
	}

	/** Every tick, for an arsenal user (from {@link ArsenalPower#tick}). */
	static void tick(ServerPlayer player) {
		ServerLevel level = player.level();
		// setting foot in the End: the void takes you at once (a purified power is immune, which Fate enforces)
		if (level.dimension() == Level.END && player.isAlive() && !player.isCreative() && !player.isSpectator()
			&& Fate.canCorrupt(player)) {
			if (Fate.corrupt(player)) {
				whisper(player, "message.jaymospowers.arsenal.whisper_end", SoundEvents.ENDERMAN_STARE, 0.8F, 0.6F);
			}
		}
		if (!Fate.isCorrupted(player)) {
			if (player.getAttached(VOID_SINCE) != null) {
				player.removeAttached(VOID_SINCE);
			}
			return;
		}
		long worldTime = level.getServer().overworld().getGameTime();
		Long since = player.getAttached(VOID_SINCE);
		if (since == null) {
			player.setAttached(VOID_SINCE, worldTime);
		} else if (worldTime - since >= CURE_TICKS && player.isAlive()) {
			// three days on, the void lets go of you
			player.removeAttached(VOID_SINCE);
			Fate.purify(player);
			player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.arsenal.void_faded")
				.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
			return;
		}
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
			return;
		}
		// water and rain burn like they burn an Enderman
		if (level.getGameTime() % WATER_INTERVAL == 0 && player.isInWaterOrRain()) {
			player.hurtServer(level, player.damageSources().drown(), WATER_DAMAGE);
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
		}
	}

	// ---------------------------------------------------------------- the trade: damage

	/** Whether this hit is a summoned weapon in corrupted hands. */
	private static boolean voidWeaponHit(DamageSource source) {
		if (!isVoid(source.getEntity())) {
			return false;
		}
		ItemStack weapon = source.getWeaponItem();
		return weapon != null && Summoned.is(weapon);
	}

	/** Called from {@code ArsenalLivingEntityMixin} at the start of every hurt: the void's edge. */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float damage) {
		return voidWeaponHit(source) ? damage * DAMAGE_MULTIPLIER : damage;
	}

	/** Marks a weapon as drawn from the void: it wears a void sheen (the glint). Returns the same stack. */
	public static ItemStack voidTouched(ItemStack stack) {
		stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** Darkness and wither from a void-touched hit. */
	public static void voidHit(LivingEntity victim, @Nullable Entity attacker, boolean wither) {
		victim.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0), attacker);
		if (wither) {
			victim.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, WITHER_AMPLIFIER), attacker);
		}
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		Entity attacker = source.getEntity();
		if (!blocked && voidWeaponHit(source)) {
			voidHit(entity, attacker, true);
		}
		// Endermen: either way round
		if (entity instanceof Enderman && attacker instanceof ServerPlayer player && isArsenal(player)) {
			addCorruption(player, CORRUPT_HIT);
		}
		if (entity instanceof ServerPlayer player && attacker instanceof Enderman && isArsenal(player)) {
			addCorruption(player, CORRUPT_HIT);
		}
		// the dragon remembers who fought it
		if (entity instanceof EnderDragon dragon && attacker instanceof ServerPlayer player) {
			DRAGON_FIGHTERS.computeIfAbsent(dragon.getUUID(), k -> new HashSet<>()).add(player.getUUID());
		}
		// a heavy blow can knock a corrupted user loose from this world for a moment
		if (entity instanceof ServerPlayer player && isVoid(player) && damageTaken >= BLINK_MIN_DAMAGE && player.isAlive()
			&& player.getRandom().nextFloat() < BLINK_CHANCE) {
			blink(player);
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		Entity killer = source.getEntity();
		if (entity instanceof Enderman && killer instanceof ServerPlayer player && isArsenal(player)) {
			addCorruption(player, CORRUPT_KILL);
		}
		if (entity instanceof EnderDragon dragon && entity.level() instanceof ServerLevel level) {
			Set<UUID> fighters = DRAGON_FIGHTERS.remove(dragon.getUUID());
			if (fighters == null) {
				fighters = new HashSet<>();
			}
			if (killer instanceof ServerPlayer player) {
				fighters.add(player.getUUID());
			}
			dragonDied(level, dragon.position(), fighters);
		}
	}

	// ---------------------------------------------------------------- the trade: blink

	/** An involuntary blink, Enderman-style, onto safe ground {@link #BLINK_MIN}-{@link #BLINK_MAX} blocks away. */
	public static boolean blink(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 from = player.position();
		for (int i = 0; i < BLINK_ATTEMPTS; i++) {
			double angle = player.getRandom().nextDouble() * Mth.TWO_PI;
			double dist = Mth.lerp(player.getRandom().nextDouble(), BLINK_MIN, BLINK_MAX);
			double x = from.x + Math.cos(angle) * dist;
			double z = from.z + Math.sin(angle) * dist;
			double y = from.y + player.getRandom().nextInt(7) - 2;
			if (player.randomTeleport(x, y, z, true, state -> false)) {
				if (player.position().distanceTo(from) < BLINK_MIN - 0.5) {
					continue; // it settled too close: try again from here
				}
				player.resetFallDistance();
				level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
				level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 40, 0.3, 0.8, 0.3, 0.6);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the cure

	/** The dragon died at {@code where}: every corrupted arsenal user nearby who fought it is purified. */
	public static int dragonDied(ServerLevel level, Vec3 where, Set<UUID> fighters) {
		int purged = 0;
		for (ServerPlayer player : level.players()) {
			if (fighters.contains(player.getUUID()) && isVoid(player) && player.position().distanceTo(where) <= CURE_RADIUS) {
				startPurge(player);
				purged++;
			}
		}
		return purged;
	}

	/** Begins tearing the void out of this player; {@link Fate#purify} lands at the end. */
	public static void startPurge(ServerPlayer player) {
		if (PURGES.containsKey(player.getUUID())) {
			return;
		}
		PURGES.put(player.getUUID(), player.level().getGameTime());
		whisper(player, "message.jaymospowers.arsenal.purge_start", SoundEvents.ENDERMAN_SCREAM, 1.0F, 0.5F);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PURGE_TICKS, 3, false, false));
	}

	private static void serverTick(MinecraftServer server) {
		if (PURGES.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Long>> it = PURGES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long t = level.getGameTime() - entry.getValue();
			if (t < 0 || t > PURGE_TICKS + 5) {
				it.remove();
				continue;
			}
			purgeTick(player, level, (int) t);
			if (t >= PURGE_TICKS) {
				it.remove();
				Fate.purify(player);
				purgeFinale(player, level);
			}
		}
	}

	/** The void, torn out: black and purple streaming up and out of the body in a widening spiral. */
	private static void purgeTick(ServerPlayer player, ServerLevel level, int t) {
		if (!player.isAlive()) {
			return;
		}
		double x = player.getX(), y = player.getY(), z = player.getZ();
		float progress = t / (float) PURGE_TICKS;
		int streams = 3 + (int) (progress * 5);
		for (int i = 0; i < streams; i++) {
			double a = t * 0.45 + i * Mth.TWO_PI / streams;
			double r = 0.3 + progress * 0.9;
			double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
			double py = y + 0.2 + (t % 10) * 0.18;
			// count 0: the "spread" is the exact velocity, so these fly up and out
			level.sendParticles(ParticleTypes.SQUID_INK, px, py, pz, 0, Math.cos(a) * 0.08, 0.35 + progress * 0.3, Math.sin(a) * 0.08, 1.0);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, px, py + 0.4, pz, 0, Math.cos(a) * 0.05, 0.25, Math.sin(a) * 0.05, 1.0);
		}
		if (t % 4 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 4, 0.25, 0.5, 0.25, 0.08);
		}
		if (t == 20) {
			level.playSound(null, x, y, z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.5F);
			level.playSound(null, x, y, z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.8F, 0.5F);
		}
		if (t == 45) {
			level.playSound(null, x, y, z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.7F);
			level.playSound(null, x, y, z, SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.2F, 0.4F);
		}
	}

	/** The last of it rips free: a column of void thrown skyward, then light. */
	private static void purgeFinale(ServerPlayer player, ServerLevel level) {
		double x = player.getX(), y = player.getY(), z = player.getZ();
		for (int i = 0; i < 40; i++) {
			level.sendParticles(ParticleTypes.SQUID_INK, x, y + 1.0 + i * 0.25, z, 3, 0.15, 0.1, 0.15, 0.02);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 1.0, z, 160, 0.2, 0.2, 0.2, 0.6);
		level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 60, 0.1, 0.1, 0.1, 0.35);
		level.playSound(null, x, y, z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.4F);
		level.playSound(null, x, y, z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.5F, 0.5F);
		player.removeEffect(MobEffects.SLOWNESS);
		player.removeEffect(MobEffects.DARKNESS);
		player.removeEffect(MobEffects.WITHER);
	}

	/** For the debug command: is this player mid-purge? */
	public static boolean purging(ServerPlayer player) {
		return PURGES.containsKey(player.getUUID());
	}
}
