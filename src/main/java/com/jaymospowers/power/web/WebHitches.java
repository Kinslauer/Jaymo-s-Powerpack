package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Creatures hitched to a block with a web (Yank released while aiming at a block), like a lead tied to a fence: the
 * line keeps the length it had when it was tied, pulls the creature back when it strains, and leaves it very little
 * say over where it goes. The line breaks if the block it's tied to goes, the creature fights (hits anything), it's
 * wrenched far past the line (an explosion, a big knockback), or after {@link #MAX_TICKS}.
 */
public final class WebHitches {
	/** Longest a hitch lasts. */
	public static final int MAX_TICKS = 1200;
	/** Shortest and longest line a hitch can be tied with; hung from a ceiling, it's shorter still. */
	public static final double MIN_LENGTH = 1.0, MAX_LENGTH = 3.0, CEILING_MAX_LENGTH = 2.0;
	/** Pulled this far past the line's length, it snaps. */
	public static final double SNAP_OVER = 1.5;
	/** How hard the line pulls back (per block over), at most per tick. */
	private static final double SPRING = 0.3;
	private static final double MAX_PULL = 0.9;
	/** A hitched creature's own movement is cut to this fraction. */
	private static final double OWN_MOVEMENT = 0.15;
	/**
	 * A hitched <i>player</i> isn't helpless: they keep more of their movement, the line pulls them back more gently,
	 * swinging at it (attacking) cuts it, and straining past the line for {@code PLAYER_STRAIN_TICKS} snaps it.
	 */
	private static final double PLAYER_OWN_MOVEMENT = 0.6;
	private static final double PLAYER_SPRING = 0.08;
	private static final double PLAYER_MAX_PULL = 0.22;
	private static final double PLAYER_STRAIN_OVER = 1.5;
	private static final int PLAYER_STRAIN_TICKS = 30;
	/** Per hitched player: how long they've strained, and whether they were mid-swing last tick. */
	private static final java.util.Map<UUID, int[]> STRAIN = new java.util.HashMap<>();
	private static final Identifier SLOWED = JaymosPowers.id("web_hitched");

	private record Hitch(ResourceKey<Level> dimension, UUID entity, Vec3 anchor, BlockPos block, double length, long until, UUID owner, boolean dark) {
	}

	/** One line on a hitched creature, for drawing: where it's tied, and whether it's a symbiote's black cord. */
	public record Line(Vec3 anchor, boolean dark) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Line> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(Vec3.STREAM_CODEC, Line::anchor,
				net.minecraft.network.codec.ByteBufCodecs.BOOL, Line::dark, Line::new);
	}

	/** Lines one slinger can have out at once: one, or {@code MASTERED_LINES} once Web is mastered. */
	public static final int LINES = 1, MASTERED_LINES = 5;
	/** More lines than this on one creature, and it's webbed up like Web Shot does (Slowness, cobwebs). */
	public static final int WEBBED_AT = 2;

	private static final List<Hitch> HITCHES = new ArrayList<>();
	private static long clock;

	private WebHitches() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebHitches::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HITCHES.clear();
			STRAIN.clear();
		});
		// a hitched creature that fights breaks free
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			Entity attacker = source.getEntity();
			if (attacker != null && attacker.getAttached(WebPower.HITCH) != null && attacker.level() instanceof ServerLevel level) {
				HITCHES.removeIf(h -> h.entity().equals(attacker.getUUID()));
				release(level, attacker, true);
			}
		});
	}

	/** Ties {@code entity} to the point {@code anchor} on {@code block}, at the line's current length. */
	static void hitch(ServerLevel level, net.minecraft.server.level.ServerPlayer owner, Entity entity, Vec3 anchor, BlockPos block,
		net.minecraft.core.Direction face) {
		// one line out (five once mastered): the oldest comes loose to make room
		int cap = com.jaymospowers.core.Mastery.unlocked(owner) >= com.jaymospowers.core.Mastery.ALL ? MASTERED_LINES : LINES;
		List<Hitch> mine = HITCHES.stream().filter(h -> h.owner().equals(owner.getUUID())).toList();
		for (int i = 0; i <= mine.size() - cap; i++) {
			Hitch oldest = mine.get(i);
			HITCHES.remove(oldest);
			Entity tied = level.getEntity(oldest.entity());
			if (tied != null) {
				sync(level, tied);
			}
		}
		double max = face == net.minecraft.core.Direction.DOWN ? CEILING_MAX_LENGTH : MAX_LENGTH;
		double length = Math.clamp(center(entity).distanceTo(anchor), MIN_LENGTH, max);
		boolean dark = WebSymbiote.suitShown(owner);
		HITCHES.add(new Hitch(level.dimension(), entity.getUUID(), anchor, block.immutable(), length, clock + MAX_TICKS, owner.getUUID(), dark));
		sync(level, entity);
		// webbed up: too many lines on one creature and it's ensnared like a Web Shot
		long onIt = HITCHES.stream().filter(h -> h.entity().equals(entity.getUUID())).count();
		if (onIt > WEBBED_AT && entity instanceof LivingEntity living) {
			WebTraps.ensnare(level, living, WebBall.SLOWNESS_AMPLIFIER, WebBall.SLOWNESS_MAX_TICKS);
		}
		STRAIN.put(entity.getUUID(), new int[] {0, entity instanceof LivingEntity living && living.isSwinging() ? 1 : 0});
		if (entity instanceof LivingEntity living) {
			AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				double own = entity instanceof net.minecraft.world.entity.player.Player ? PLAYER_OWN_MOVEMENT : OWN_MOVEMENT;
				speed.addOrUpdateTransientModifier(new AttributeModifier(SLOWED, own - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.9F);
		level.sendParticles(dark ? WebSymbiote.dust(1.2F) : ParticleTypes.ITEM_COBWEB, anchor.x, anchor.y, anchor.z, 8, 0.1, 0.1, 0.1, 0.02);
	}

	/** The creature's lines, for everyone to draw (none left: it's free). */
	private static void sync(ServerLevel level, Entity entity) {
		List<Line> lines = new ArrayList<>();
		for (Hitch h : HITCHES) {
			if (h.entity().equals(entity.getUUID())) {
				lines.add(new Line(h.anchor(), h.dark()));
			}
		}
		if (lines.isEmpty()) {
			release(level, entity, false);
		} else {
			entity.setAttached(WebPower.HITCH, List.copyOf(lines));
		}
	}

	private static Vec3 center(Entity entity) {
		return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
	}

	private static void tick(MinecraftServer server) {
		clock++;
		for (Iterator<Hitch> it = HITCHES.iterator(); it.hasNext(); ) {
			Hitch hitch = it.next();
			ServerLevel level = server.getLevel(hitch.dimension());
			Entity entity = level == null ? null : level.getEntity(hitch.entity());
			if (entity == null || !entity.isAlive() || entity.isRemoved()) {
				it.remove();
				continue;
			}
			Vec3 to = hitch.anchor().subtract(center(entity));
			double distance = to.length();
			boolean blockGone = !level.isLoaded(hitch.block()) || level.getBlockState(hitch.block()).getCollisionShape(level, hitch.block()).isEmpty();
			if (blockGone || clock >= hitch.until() || distance > hitch.length() + SNAP_OVER) {
				// this line snaps; any others hold
				it.remove();
				Vec3 at = center(entity);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
				syncLater.add(entity);
				continue;
			}
			if (entity instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			boolean isPlayer = entity instanceof net.minecraft.world.entity.player.Player;
			if (isPlayer) {
				// a player cuts the line by swinging at it, or tears free by straining against it
				int[] strain = STRAIN.computeIfAbsent(entity.getUUID(), k -> new int[2]);
				boolean swinging = ((LivingEntity)entity).isSwinging();
				boolean cut = swinging && strain[1] == 0;
				strain[1] = swinging ? 1 : 0;
				if (distance > hitch.length() + PLAYER_STRAIN_OVER) {
					strain[0]++;
				} else if (strain[0] > 0) {
					strain[0]--;
				}
				if (cut || strain[0] >= PLAYER_STRAIN_TICKS) {
					it.remove();
					HITCHES.removeIf(h -> h != hitch && h.entity().equals(entity.getUUID()));
					release(level, entity, true);
					return;
				}
			}
			if (distance > hitch.length()) {
				Vec3 dir = to.scale(1.0 / distance);
				Vec3 v = entity.getDeltaMovement();
				double inward = v.dot(dir);
				double want = isPlayer
					? Math.min((distance - hitch.length()) * PLAYER_SPRING, PLAYER_MAX_PULL)
					: Math.min((distance - hitch.length()) * SPRING, MAX_PULL);
				if (inward < want) {
					entity.setDeltaMovement(v.add(dir.scale(want - inward)));
					entity.needsSync = true;
					entity.syncVelocity = true;
				}
			}
			if (clock % 8 == 0) {
				Vec3 mid = center(entity).add(to.scale(0.5));
				level.sendParticles(hitch.dark() ? WebSymbiote.dust(1.0F) : ParticleTypes.ITEM_COBWEB, mid.x, mid.y, mid.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		for (Entity entity : syncLater) {
			if (entity.level() instanceof ServerLevel level) {
				sync(level, entity);
			}
		}
		syncLater.clear();
	}

	private static final List<Entity> syncLater = new ArrayList<>();

	/** Someone punched one of this creature's lines (the one tied near {@code anchor}): it breaks, the rest hold. */
	static boolean cut(ServerLevel level, Entity entity, Vec3 anchor) {
		Hitch nearest = null;
		double best = 2.5;
		for (Hitch h : HITCHES) {
			if (h.entity().equals(entity.getUUID()) && h.anchor().distanceTo(anchor) < best) {
				best = h.anchor().distanceTo(anchor);
				nearest = h;
			}
		}
		if (nearest == null) {
			return false;
		}
		HITCHES.remove(nearest);
		sync(level, entity);
		return true;
	}

	private static void release(ServerLevel level, Entity entity, boolean snapped) {
		entity.removeAttached(WebPower.HITCH);
		STRAIN.remove(entity.getUUID());
		if (entity instanceof LivingEntity living) {
			AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(SLOWED);
			}
		}
		if (snapped) {
			Vec3 at = center(entity);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.05);
		}
	}
}
