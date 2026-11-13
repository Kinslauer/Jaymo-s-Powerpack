package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.HeldFast;
import com.jaymoskingdoms.UpperHand;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The gaze that turns you to stone: the basilisk's, and the gorgon's (the snake-haired archer of the jungles and swamps, and
 * of the ruins where she keeps her statues, {@link Statues}). Looking at it, with it looking back, fills a creeping stone in you: first
 * a heaviness (slowness that deepens), then at full {@link HeldFast held fast} like a statue until a Grit save breaks it or
 * a friend chips you free with a pickaxe. It ebbs when you look away. Looking away is safe but costs you: your blows at it
 * are bad footing ({@link UpperHand}), for you are fighting half blind. A mirror held up in front of you (hand mirror,
 * {@link #mirror}) turns the gaze back: then it is the beast that fills with stone, and a stone beast is held fast, open to
 * every blow.
 */
public final class Gaze {
	/** The gaze reaches this far (blocks); a look "at" it is within this cosine of dead centre. */
	static final double REACH = 24.0, AT = 0.93, MIRROR_AT = 0.75;
	/** Stone gained a check by looking at it, by the mirror's reflection, and lost a check looking away. */
	static final float GAIN = 3.0F, REFLECTED = 6.0F, EBB = 1.5F;
	/** Checks run this often (ticks). */
	static final int EVERY = 4;
	/** Full at this; held fast this long (ticks), and then it falls back to this share. */
	static final float FULL = 100.0F, AFTER = 40.0F;
	static final int HELD_TICKS = 20 * 40, BEAST_HELD_TICKS = 20 * 20;
	/** The deepest slowness, and its levels. */
	static final int SLOW_STEPS = 4;
	/** A player is "looking away" for this long (ticks) after a check that found them not looking. */
	static final int AWAY_TICKS = 40;

	private static final Map<UUID, Float> STONE = new HashMap<>();
	private static final Map<UUID, Long> AWAY = new HashMap<>();

	private Gaze() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Gaze::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			STONE.clear();
			AWAY.clear();
		});
		// looking away to fight it: bad footing for the blow
		UpperHand.add((attacker, target) -> gazer(target) && attacker instanceof ServerPlayer p && lookingAway(p) ? -1 : 0);
		// a friend chips you free
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && entity instanceof ServerPlayer stoned && HeldFast.held(stoned) && STONE.containsKey(stoned.getUUID())
				&& player.getItemInHand(hand).is(ItemTags.PICKAXES)) {
				STONE.remove(stoned.getUUID());
				HeldFast.release(stoned);
				server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), stoned.getX(), stoned.getY(0.5), stoned.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
				server.playSound(null, stoned.getX(), stoned.getY(), stoned.getZ(), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code who} has the gaze: a basilisk or a gorgon. */
	static boolean gazer(net.minecraft.world.entity.Entity who) {
		return who instanceof Basilisk || who instanceof com.jaymoskingdoms.beasts.Gorgon;
	}

	/** Whether {@code p} has been keeping their eyes off a basilisk or a gorgon lately. */
	static boolean lookingAway(ServerPlayer p) {
		Long until = AWAY.get(p.getUUID());
		return until != null && p.level().getGameTime() < until;
	}

	/** A hand mirror, or anything of that name from another mod, held in either hand. */
	static boolean mirror(LivingEntity who) {
		return isMirror(who.getMainHandItem()) || isMirror(who.getOffhandItem());
	}

	static boolean isMirror(ItemStack s) {
		return !s.isEmpty() && (s.is(Creatures.MIRROR) || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains("mirror"));
	}

	/** How stoned {@code who} is, 0 to 1 (a creeping slowness; full is held fast). */
	public static float stone(LivingEntity who) {
		return STONE.getOrDefault(who.getUUID(), 0.0F) / FULL;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			java.util.Set<UUID> eyed = new java.util.HashSet<>();
			List<Mob> gazers = new ArrayList<>(level.getEntities(Creatures.BASILISK, LivingEntity::isAlive));
			gazers.addAll(level.getEntities(com.jaymoskingdoms.beasts.Beasts.GORGON, LivingEntity::isAlive));
			for (Mob beast : gazers) {
				// (one turned to stone by its own gaze sees nothing)
				if (HeldFast.held(beast)) {
					continue;
				}
				for (ServerPlayer p : level.players()) {
					if (p.isSpectator() || p.isCreative() || p.distanceTo(beast) > REACH || !p.hasLineOfSight(beast)) {
						continue;
					}
					Vec3 to = beast.getEyePosition().subtract(p.getEyePosition()).normalize();
					double dot = p.getLookAngle().dot(to);
					if (mirror(p) && dot > MIRROR_AT) {
						// the gaze goes back where it came from
						add(level, beast, REFLECTED, true);
						level.sendParticles(ParticleTypes.END_ROD, p.getX() + to.x, p.getEyeY() - 0.2, p.getZ() + to.z, 1, 0.05, 0.05, 0.05, 0.0);
						eyed.add(p.getUUID());
					} else if (dot > AT) {
						add(level, p, GAIN, false);
						eyed.add(p.getUUID());
					} else {
						AWAY.put(p.getUUID(), now + AWAY_TICKS);
					}
				}
			}
			for (ServerPlayer p : level.players()) {
				if (!eyed.contains(p.getUUID()) && STONE.containsKey(p.getUUID())) {
					float s = STONE.get(p.getUUID()) - EBB;
					if (s <= 0.0F) {
						STONE.remove(p.getUUID());
					} else {
						STONE.put(p.getUUID(), s);
						slow(p, s);
					}
				}
			}
			AWAY.values().removeIf(t -> t < now);
		}
	}

	private static void add(ServerLevel level, LivingEntity who, float by, boolean beast) {
		float s = STONE.getOrDefault(who.getUUID(), 0.0F) + by;
		if (s >= FULL) {
			int ticks = beast ? BEAST_HELD_TICKS : HELD_TICKS;
			if (HeldFast.hold(who, ticks)) {
				level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 1.0F, 0.6F);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), who.getX(), who.getY(0.5), who.getZ(), 24, 0.3, 0.6, 0.3, 0.05);
			}
			s = beast ? 0.0F : AFTER + 20.0F;
			if (beast) {
				STONE.remove(who.getUUID());
				return;
			}
		}
		STONE.put(who.getUUID(), s);
		slow(who, s);
	}

	private static void slow(LivingEntity who, float s) {
		int step = (int) (s / FULL * SLOW_STEPS);
		if (step > 0 && !HeldFast.held(who)) {
			who.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EVERY * 4, Math.min(step, SLOW_STEPS) - 1, true, false, false));
		}
		if (who.level() instanceof ServerLevel level && who.tickCount % 12 == 0) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), who.getX(), who.getY(0.2), who.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
		}
	}
}
