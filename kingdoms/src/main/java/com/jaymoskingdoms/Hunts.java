package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (am-hunts, medieval picks; Armed Mobs' revenge squads, an idea only) A realm or a camp that hates you (its standing
 * of you at or under {@link Reputation#HOSTILE}) sends a hunting party after you: its guards, or the camp's raiders,
 * as many as your gear calls for ({@link #size}: your armour and the weight of your blow), coming on from
 * {@link #SPAWN_FAR} off. Never right beside a town (nowhere on a realm's land or within {@link #TOWN_CLEAR} of any
 * seat), and at most once every {@link #GAP_TICKS} (saved on you, {@link #HUNTED_AT}). They give up after
 * {@link #HUNT_TICKS}, or if they lose you.
 */
final class Hunts {
	/** On a player: when they were last hunted (game time). Saved. */
	static final AttachmentType<Long> HUNTED_AT = AttachmentRegistry.create(JaymosKingdoms.id("hunted_at"),
		builder -> builder.persistent(Codec.LONG));
	static final String TAG = "jaymoskingdoms.hunter", TASK = "hunt";

	/** At most one hunt in this long (ticks); considered this often, with this chance. */
	static final long GAP_TICKS = 20L * 60 * 30;
	static final int CHECK_EVERY = 20 * 60;
	static final float CHANCE = 0.2F;
	/** Who may send one: a realm or camp whose seat is within this of you. */
	static final double SEND_FROM = 800.0;
	/** Never this near any seat, or on a realm's land; the party comes on from this far off. */
	static final double TOWN_CLEAR = 128.0, SPAWN_FAR = 40.0;
	/** The party: one, and one more for each this much armour and blow together; at most this many. */
	static final int GEAR_PER = 10, MOST = 6;
	/** They hunt this long (ticks); they lose you this far off; they see you to set on you this near. */
	static final long HUNT_TICKS = 20L * 60 * 5;
	static final double LOSE = 160.0, SIGHT = 32.0;
	static final double SPEED = 1.0;
	static final int LINES = 2;

	private static final class Hunt {
		final UUID player;
		final int from;
		final List<UUID> men = new ArrayList<>();
		final long since;
		boolean seen;

		Hunt(UUID player, int from, long since) {
			this.player = player;
			this.from = from;
			this.since = since;
		}
	}

	private static final Map<UUID, Hunt> ACTIVE = new HashMap<>();
	private static final List<Entity> STRAYS = new ArrayList<>();

	private Hunts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Hunts::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && ACTIVE.values().stream().noneMatch(h -> h.men.contains(entity.getUUID()))) {
				STRAYS.add(entity);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			STRAYS.clear();
		});
	}

	/** How many {@code p}'s gear calls for. */
	static int size(ServerPlayer p) {
		double gear = p.getArmorValue() + p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		return Mth.clamp(1 + (int)(gear / GEAR_PER), 1, MOST);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (!STRAYS.isEmpty()) {
			for (Entity e : List.copyOf(STRAYS)) {
				if (e.isAlive()) {
					McaCompat.vanish(e);
				}
			}
			STRAYS.clear();
		}
		long now = level.getGameTime();
		if (now % 20 == 0) {
			drive(server, level, now);
		}
		if (now % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isCreative() || p.isSpectator() || ACTIVE.containsKey(p.getUUID()) || now - p.getAttachedOrElse(HUNTED_AT, -GAP_TICKS) < GAP_TICKS
				|| level.getRandom().nextFloat() >= CHANCE || !wild(server, level, p.blockPosition())) {
				continue;
			}
			Faction from = hater(server, p);
			if (from != null) {
				send(level, p, from, now);
			}
		}
	}

	/** Out of the way of towns: on nobody's land and no seat near. */
	private static boolean wild(MinecraftServer server, ServerLevel level, BlockPos pos) {
		if (Banners.owner(server, ChunkPos.containing(pos)) != null) {
			return false;
		}
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.seat().distSqr(pos) < TOWN_CLEAR * TOWN_CLEAR) {
				return false;
			}
		}
		return true;
	}

	/** The realm or camp near enough that hates {@code p} most, or null. */
	private static @Nullable Faction hater(MinecraftServer server, ServerPlayer p) {
		Faction best = null;
		int worst = Reputation.HOSTILE + 1;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.seat().distSqr(p.blockPosition()) > SEND_FROM * SEND_FROM || f.member(p.getUUID()) != null) {
				continue;
			}
			boolean camp = f.type() == FactionType.BARBARIAN && Camps.data(server, f.id()) != null;
			if (!camp && !f.type().isRealm()) {
				continue;
			}
			int rep = Reputation.get(p, f.id());
			if (rep < worst) {
				best = f;
				worst = rep;
			}
		}
		return best;
	}

	private static void send(ServerLevel level, ServerPlayer p, Faction from, long now) {
		MinecraftServer server = level.getServer();
		Vec3 way = Vec3.atCenterOf(from.seat()).subtract(p.position()).multiply(1.0, 0.0, 1.0);
		if (way.lengthSqr() < 1.0) {
			way = new Vec3(1.0, 0.0, 0.0);
		}
		BlockPos column = BlockPos.containing(p.position().add(way.normalize().scale(SPAWN_FAR)));
		if (!Loaded.made(level, column) || !wild(server, level, column)) {
			return;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) {
			return;
		}
		Hunt hunt = new Hunt(p.getUUID(), from.id(), now);
		int n = size(p);
		for (int i = 0; i < n; i++) {
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.offset(level.getRandom().nextInt(5) - 2, 0,
				level.getRandom().nextInt(5) - 2));
			Mob m = man(level, from, i, spot);
			if (m == null) {
				continue;
			}
			m.addTag(TAG);
			if (m instanceof PathfinderMob pm) {
				pm.setHomeTo(p.blockPosition(), (int)LOSE);
			}
			if (hunt.men.isEmpty()) {
				Ranks.lead(m);
			}
			hunt.men.add(m.getUUID());
		}
		if (hunt.men.isEmpty()) {
			return;
		}
		p.setAttached(HUNTED_AT, now);
		ACTIVE.put(p.getUUID(), hunt);
		JaymosKingdoms.LOGGER.info("{} sends {} hunters after {}", from.name(), hunt.men.size(), p.getName().getString());
	}

	/** One of {@code from}'s hunters at {@code at}: a realm's guard (MCA's with MCA), or a camp's raider. */
	private static @Nullable Mob man(ServerLevel level, Faction from, int i, BlockPos at) {
		float yaw = level.getRandom().nextFloat() * 360.0F;
		if (from.type() == FactionType.BARBARIAN) {
			return Raids.member(level, from, i, at, at, (int)LOSE, false);
		}
		Worker w = McaCompat.escort(level, from, at, yaw, TASK, (int)LOSE);
		if (w != null) {
			return w.mob();
		}
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		folk.setup(Folk.GUARD, from, at, (int)LOSE);
		level.addFreshEntity(folk);
		folk.setTask(TASK);
		return folk;
	}

	/** Each hunt: on your trail, set on you once they see you; over when it's run its time, they're gone, or they've lost you. */
	private static void drive(MinecraftServer server, ServerLevel level, long now) {
		for (Iterator<Hunt> it = ACTIVE.values().iterator(); it.hasNext();) {
			Hunt h = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(h.player);
			List<Mob> men = new ArrayList<>();
			for (UUID id : h.men) {
				if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
					men.add(m);
				}
			}
			boolean lost = p == null || p.level() != level || !p.isAlive() || men.isEmpty() || men.getFirst().distanceTo(p) > LOSE;
			if (lost || now - h.since > HUNT_TICKS) {
				for (Mob m : men) {
					McaCompat.vanish(m);
				}
				it.remove();
				continue;
			}
			for (Mob m : men) {
				if (Downed.isDowned(m) || Rulers.target(m) == p) {
					continue;
				}
				if (m.distanceTo(p) <= SIGHT && m.hasLineOfSight(p)) {
					if (!h.seen) {
						h.seen = true;
						Faction from = Factions.get(server, h.from);
						Speech.tell(level, m, p, Component.translatable("speech.jaymoskingdoms.hunt.found." + level.getRandom().nextInt(LINES),
							from == null ? "?" : from.name()), Speech.RED);
					}
					Comrades.aim(m, p);
				} else if (m instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, p.blockPosition(), (float)SPEED * 0.6F, 4);
				} else if (m instanceof PathfinderMob pm && pm.getNavigation().isDone()) {
					pm.setHomeTo(p.blockPosition(), (int)LOSE);
					pm.getNavigation().moveTo(p, SPEED);
				}
			}
		}
	}
}
