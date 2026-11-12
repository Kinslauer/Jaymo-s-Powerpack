package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Mimic;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Noticing without looking (fifth edition's passive Perception, Andy's pick A5), and how fast you travel changing it
 * (pick F1). Every second a player's eyes and ears are at work by themselves: a tripwire across the path, a chest that
 * isn't a chest, something hunting them from where they aren't looking, a foe hidden by magic. Noticed, it gives itself
 * away to that player alone: a soft sound from where it is and a glint there. Nothing is written; a low score walks
 * into it.
 * <p>
 * The chance comes from Wits; sneaking along is better, and running flat out or riding hard is much worse. Other systems
 * add what can be noticed ({@link #add}): traps, ceiling hunters, chalk marks, a pickpocket's hand.
 */
public final class Notice {
	/** The chance a second of noticing a thing: with no Wits, and what the top of Wits adds. */
	static final float LEAST = 0.08F, WITS = 0.5F;
	/** Sneaking adds this; sprinting or riding fast keeps this share of it. */
	static final float SNEAKING = 0.15F, RUSHING = 0.3F;
	/** A mount moving this fast (blocks a tick) is riding hard. */
	static final double HARD_RIDE = 0.25;
	/** How near (blocks) a tripwire, a sleeping mimic, and a hunter may be. */
	static final int WIRE = 5;
	static final double MIMIC = 8.0, HUNTER = 14.0;
	/** A hunter outside this half-angle of the player's look (as a cosine) is one they're not looking at. */
	static final double UNSEEN_COS = 0.5;
	/** A thing once noticed isn't given away again for this long (ticks). */
	static final int AGAIN = 20 * 120;

	/** One thing to be noticed: a key that stays the same while it is there, and where it is. */
	public record Spot(long key, Vec3 at, SoundEvent sound) {
	}

	/** What else a player might notice about them. */
	@FunctionalInterface
	public interface Finder {
		void find(ServerPlayer player, List<Spot> into);
	}

	private static final List<Finder> FINDERS = new ArrayList<>();
	/** When each player last noticed each thing (game time). */
	private static final Map<UUID, Map<Long, Long>> SEEN = new HashMap<>();

	private Notice() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Notice::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SEEN.clear());
		add(Notice::builtIn);
	}

	/** Adds more that can be noticed. */
	public static void add(Finder finder) {
		FINDERS.add(finder);
	}

	/** {@code player}'s chance (0-1) a second of noticing a thing. */
	public static float chance(ServerPlayer player) {
		float c = LEAST + WITS * Saves.share(player, Stats.Stat.WITS);
		if (player.isShiftKeyDown()) {
			c += SNEAKING;
		}
		if (player.isSprinting() || player.getVehicle() != null && player.getVehicle().getDeltaMovement().horizontalDistance() > HARD_RIDE) {
			c *= RUSHING;
		}
		return Math.min(0.95F, c);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((player.tickCount + player.getId()) % 20 != 0 || !player.isAlive() || player.isSpectator() || PowersCompat.helpless(player)) {
				continue;
			}
			List<Spot> spots = new ArrayList<>();
			for (Finder f : FINDERS) {
				f.find(player, spots);
			}
			if (spots.isEmpty()) {
				continue;
			}
			long now = player.level().getGameTime();
			Map<Long, Long> seen = SEEN.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
			seen.values().removeIf(t -> now - t > AGAIN);
			float chance = chance(player);
			for (Spot spot : spots) {
				if (seen.containsKey(spot.key()) || player.getRandom().nextFloat() >= chance) {
					continue;
				}
				seen.put(spot.key(), now);
				giveAway(player, spot);
			}
		}
	}

	/** The thing gives itself away to {@code player} alone. */
	private static void giveAway(ServerPlayer player, Spot spot) {
		Vec3 at = spot.at();
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(spot.sound()), SoundSource.AMBIENT,
			at.x, at.y, at.z, 0.6F, 1.2F + player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
		player.level().sendParticles(player, ParticleTypes.WAX_OFF, true, false, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.0);
	}

	/** Tripwires, sleeping mimics, and whatever hunts them from where they aren't looking or can't be seen. */
	private static void builtIn(ServerPlayer player, List<Spot> into) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-WIRE, -2, -WIRE), at.offset(WIRE, 2, WIRE))) {
			if (level.getBlockState(p).is(Blocks.TRIPWIRE)) {
				into.add(new Spot(p.asLong(), Vec3.atCenterOf(p), SoundEvents.TRIPWIRE_ATTACH));
			}
		}
		for (Mimic mimic : level.getEntitiesOfClass(Mimic.class, player.getBoundingBox().inflate(MIMIC), Mimic::shut)) {
			into.add(new Spot(~(long) mimic.getId(), mimic.position().add(0, 0.6, 0), SoundEvents.CHEST_LOCKED));
		}
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HUNTER),
			m -> m.isAlive() && m instanceof Enemy && m.getTarget() == player)) {
			Vec3 to = mob.getEyePosition().subtract(eye).normalize();
			if (mob.hasEffect(MobEffects.INVISIBILITY) || to.dot(look) < UNSEEN_COS) {
				into.add(new Spot(~(long) mob.getId() ^ 0x5EE5L << 40, mob.position().add(0, mob.getBbHeight() * 0.5, 0), SoundEvents.GRASS_STEP));
			}
		}
	}
}
