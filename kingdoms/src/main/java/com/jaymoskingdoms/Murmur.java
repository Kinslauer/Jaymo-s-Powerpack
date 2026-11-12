package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Murmur (docs/Pawn-Plan.md row H2, the name Andy's; after Dragon's Dogma 2's dragonsplague, a commercial game:
 * ideas only). A sickness that rides between companions.
 * <ul>
 * <li><b>Caught</b> in a dark place (the deep dark, a soul sand valley: {@link #DARK_CATCH} for each minute there), or
 * from another companion, anyone's, at a shared rest: sitting down at a table or fire together, or near you while you
 * sleep ({@link #REST_PASS}). Some rival parties carry it ({@link #CARRIER}).</li>
 * <li><b>Shown, never told</b>: from the first day its eyes flicker red ({@link Possession#EYES_FLICKER}) and now and then
 * it stops and holds its head; from the second ({@link #STAGE_TWO}) it ignores one order in three, hits harder than it
 * should ({@link #STRONG}) and talks brazenly.</li>
 * <li><b>The worst</b>, never before the signs have shown a full day ({@link #STAGE_THREE}): a night in a settlement while
 * its player sleeps, or with its player gone off, and it's loose in the town, going for the townsfolk, and those it
 * puts down it finishes (Andy: they can die). It stays loose till it's knocked down.</li>
 * <li><b>Cured</b> by letting it fall and come round; by the rare remedy (an enchanted golden apple, used on it); by a
 * cleric or a priest (it goes to one itself in a settlement, and a few seconds beside one cures it, Andy's answer); or
 * by passing it on at a rest, which leaves the one who passed it well.</li>
 * </ul>
 */
public final class Murmur {
	/** The signs grow after this long (ticks: a day), and the worst may come after this long (two days). */
	static final long STAGE_TWO = 24000L, STAGE_THREE = 48000L;
	/** Chances: caught per minute in a dark place; passed at a shared rest; a rival party member setting out with it. */
	static final float DARK_CATCH = 0.03F, REST_PASS = 0.4F, CARRIER = 0.05F;
	/** Ignores an order at this chance once it's grown; hits this much harder (share of its base). */
	static final float IGNORE = 0.33F;
	static final double STRONG = 0.3;
	/** A rest counts within this (blocks); a cleric cures within this, after this long (ticks); a sick one seeks one this far. */
	static final double REST_NEAR = 6.0, CLERIC_NEAR = 3.0, CLERIC_SEEK = 48.0, PREY_NEAR = 24.0;
	static final int CLERIC_TICKS = 100;
	/** Signs at most this often (ticks); a head held this long. */
	static final int SIGN_EVERY = 900, HEAD_TICKS = 40;
	static final int EVERY = 20, MINUTE = 1200;

	/** On the sick: since when, from where, the last night it was checked for the worst. Saved. */
	public record Sick(long since, String from, long night) {
		static final Codec<Sick> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("since").forGetter(Sick::since),
			Codec.STRING.optionalFieldOf("from", "").forGetter(Sick::from),
			Codec.LONG.optionalFieldOf("night", -1L).forGetter(Sick::night)).apply(i, Sick::new));
	}

	public static final AttachmentType<Sick> SICK = AttachmentRegistry.create(JaymosKingdoms.id("murmur"),
		builder -> builder.persistent(Sick.CODEC));

	private static final Set<Mob> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	private static final Map<Mob, Long> HEAD = new IdentityHashMap<>();
	private static final Map<Mob, Integer> AT_CLERIC = new IdentityHashMap<>();
	private static final Set<UUID> FELL = new HashSet<>();
	private static final Map<UUID, Long> SIGNED = new HashMap<>();
	/** Rests already counted: a pair (or a sitting) by night. */
	private static final Set<String> RESTED = new HashSet<>();

	private Murmur() {
	}

	static void register() {
		PowersCompat.murmurFinishes();
		ServerTickEvents.END_SERVER_TICK.register(Murmur::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob m && m.hasAttached(SICK)) {
				LOADED.add(m);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob m) {
				LOADED.remove(m);
				HEAD.remove(m);
				AT_CLERIC.remove(m);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			HEAD.clear();
			AT_CLERIC.clear();
			FELL.clear();
			SIGNED.clear();
			RESTED.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof Mob m)
				|| !m.hasAttached(SICK) || !sp.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE)) {
				return InteractionResult.PASS;
			}
			sp.getMainHandItem().shrink(1);
			world.playSound(null, m.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
			cure(m, "remedy");
			return InteractionResult.SUCCESS;
		});
	}

	public static boolean sick(LivingEntity e) {
		return e.hasAttached(SICK);
	}

	private static long stageAge(Mob m) {
		Sick s = m.getAttached(SICK);
		return s == null ? -1L : m.level().getGameTime() - s.since();
	}

	/** {@code m} catches it ({@code from}: where). */
	static void infect(Mob m, String from) {
		if (sick(m) || !Temperaments.humanoid(m) || m.isBaby()) {
			return;
		}
		m.setAttached(SICK, new Sick(m.level().getGameTime(), from, -1L));
		LOADED.add(m);
	}

	/** (RivalParties) one setting out may carry it. */
	static void carrier(Mob m) {
		if (m.getRandom().nextFloat() < CARRIER) {
			infect(m, "road");
		}
	}

	/** {@code m} is well again ({@code how}: fell, remedy, cleric, passed). */
	static void cure(Mob m, String how) {
		if (!sick(m)) {
			return;
		}
		m.removeAttached(SICK);
		LOADED.remove(m);
		HEAD.remove(m);
		AT_CLERIC.remove(m);
		if (!Possession.held(m)) {
			m.removeAttached(Possession.EYES);
		}
		AttributeInstance attack = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.removeModifier(JaymosKingdoms.id("murmur_strong"));
		}
		if (!how.equals("passed")) {
			Callouts.say(m, "murmur.well", null, true);
		}
	}

	/** (QuickOrders) a grown case ignores the order: true if it did (it says so, its way). */
	static boolean ignores(Villager v, ServerPlayer player) {
		if (stageAge(v) < STAGE_TWO || v.getRandom().nextFloat() >= IGNORE) {
			return false;
		}
		Callouts.say(v, "murmur.ignore", player, true, player.getDisplayName());
		return true;
	}

	/** (Possession) whom one loose goes for: the nearest townsperson not anyone's companion, else anyone near. */
	static @Nullable LivingEntity prey(ServerLevel level, Mob m) {
		LivingEntity best = null;
		double bestD = PREY_NEAR * PREY_NEAR;
		for (Villager v : level.getEntitiesOfClass(Villager.class, m.getBoundingBox().inflate(PREY_NEAR), v -> v != m && v.isAlive()
			&& !Downed.isDowned(v) && Bonds.lordOf(v) == null)) {
			double d = v.distanceToSqr(m);
			if (d < bestD) {
				bestD = d;
				best = v;
			}
		}
		if (best == null) {
			Mob mob = m;
			best = level.getNearestPlayer(mob, PREY_NEAR) instanceof ServerPlayer p && !p.isSpectator() && !p.isCreative() ? p : null;
		}
		return best;
	}

	/** (Powers' executions, through PowersCompat) one loose finishes the townsfolk it put down. */
	static boolean finishes(Mob mob, LivingEntity body) {
		return "murmur".equals(Possession.cause(mob)) && body instanceof Villager && Bonds.lordOf(body) == null;
	}

	/** (TableTalk) {@code m} sat down with {@code player}: a shared rest. */
	static void rested(Mob m, ServerPlayer player) {
		List<Mob> round = new ArrayList<>(player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(REST_NEAR),
			e -> e.isAlive() && !Downed.isDowned(e) && Temperaments.humanoid(e) && (Bonds.lordOf(e) != null || e.hasAttached(RivalParties.PARTY))));
		if (!round.contains(m)) {
			round.add(m);
		}
		share(round, "rest:" + player.getUUID() + ":" + player.level().getGameTime() / 24000L);
	}

	/** Those resting together: the sick may pass it to one who's well, and is well itself for it. */
	private static void share(List<Mob> round, String key) {
		if (!RESTED.add(key)) {
			return;
		}
		List<Mob> sick = round.stream().filter(Murmur::sick).toList();
		List<Mob> well = new ArrayList<>(round.stream().filter(e -> !sick(e)).toList());
		for (Mob s : sick) {
			if (well.isEmpty() || s.getRandom().nextFloat() >= REST_PASS) {
				continue;
			}
			Mob to = well.remove(s.getRandom().nextInt(well.size()));
			infect(to, "rest");
			cure(s, "passed");
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		boolean minute = now % MINUTE < EVERY;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			List<Mob> yours = Bonds.companions(player, 32.0);
			// a dark place
			if (minute) {
				for (Mob m : yours) {
					if (!sick(m) && dark(player.level(), m.blockPosition()) && m.getRandom().nextFloat() < DARK_CATCH) {
						infect(m, "dark");
					}
				}
			}
			// sleeping with companions about: a shared rest
			if (player.isSleeping()) {
				List<Mob> round = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(REST_NEAR), e -> e.isAlive()
					&& !Downed.isDowned(e) && Temperaments.humanoid(e) && (Bonds.lordOf(e) != null || e.hasAttached(RivalParties.PARTY)));
				if (round.size() > 1) {
					share(round, "sleep:" + player.getUUID() + ":" + now / 24000L);
				}
			}
		}
		for (Mob m : new ArrayList<>(LOADED)) {
			if (!m.isAlive() || m.isRemoved() || !sick(m)) {
				LOADED.remove(m);
				continue;
			}
			step((ServerLevel)m.level(), m, now);
		}
		if (RESTED.size() > 512) {
			RESTED.clear();
		}
	}

	/** A dark place: the deep dark, a soul sand valley. */
	static boolean dark(ServerLevel level, BlockPos at) {
		String biome = level.getBiome(at).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return biome.equals("deep_dark") || biome.equals("soul_sand_valley");
	}

	private static void step(ServerLevel level, Mob m, long now) {
		Sick s = m.getAttached(SICK);
		long age = now - s.since();
		// letting it fall and come round
		if (Downed.isDowned(m)) {
			FELL.add(m.getUUID());
			return;
		}
		if (FELL.remove(m.getUUID())) {
			cure(m, "fell");
			return;
		}
		if (Possession.held(m)) {
			return;
		}
		m.setAttached(Possession.EYES, Possession.EYES_FLICKER);
		// oddly strong
		AttributeInstance attack = m.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null && age >= STAGE_TWO && attack.getModifier(JaymosKingdoms.id("murmur_strong")) == null) {
			attack.addPermanentModifier(new AttributeModifier(JaymosKingdoms.id("murmur_strong"), STRONG, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		// holding its head; brazen talk
		Long head = HEAD.get(m);
		if (head != null) {
			if (now > head) {
				HEAD.remove(m);
				m.setShiftKeyDown(false);
			} else {
				m.getNavigation().stop();
				m.getLookControl().setLookAt(m.getX(), m.getY() - 1.0, m.getZ());
				m.setShiftKeyDown(true);
			}
		} else if (Comrades.target(m) == null && now - SIGNED.getOrDefault(m.getUUID(), now - SIGN_EVERY / 2) > SIGN_EVERY
			&& m.getRandom().nextInt(3) == 0) {
			SIGNED.put(m.getUUID(), now);
			HEAD.put(m, now + HEAD_TICKS);
			level.playSound(null, m.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 0.5F, 1.4F);
			if (age >= STAGE_TWO) {
				Callouts.say(m, "murmur.brazen", null, false);
			}
		}
		Faction town = Factions.near(level.getServer(), m.blockPosition(), 64.0);
		boolean inTown = town != null && town.type() != FactionType.BARBARIAN && !town.fallen();
		// a cleric or a priest
		if (inTown) {
			Villager cleric = null;
			for (Villager v : level.getEntitiesOfClass(Villager.class, m.getBoundingBox().inflate(CLERIC_SEEK), Murmur::cleric)) {
				if (v != m && (cleric == null || v.distanceToSqr(m) < cleric.distanceToSqr(m))) {
					cleric = v;
				}
			}
			if (cleric != null) {
				if (cleric.distanceTo(m) < CLERIC_NEAR) {
					int t = AT_CLERIC.merge(m, EVERY, Integer::sum);
					cleric.getLookControl().setLookAt(m, 30.0F, 30.0F);
					if (t >= CLERIC_TICKS) {
						Callouts.say(cleric, "murmur.cleric", m, true, m.getDisplayName());
						cure(m, "cleric");
						return;
					}
				} else {
					AT_CLERIC.remove(m);
					if (Comrades.target(m) == null && m.getNavigation().isDone() && now % 200 < EVERY) {
						m.getNavigation().moveTo(cleric, 0.6);
					}
				}
			}
		}
		// the worst: a night in town, its player asleep or gone off
		long night = level.getOverworldClockTime() / 24000L;
		if (age >= STAGE_THREE && inTown && level.isDarkOutside() && s.night() != night) {
			UUID lord = Bonds.lordOf(m);
			ServerPlayer p = lord == null ? null : level.getServer().getPlayerList().getPlayer(lord);
			UUID owner = ownerOf(m);
			boolean unwatched = lord == null ? owner != null : p == null || p.isSleeping() || p.level() != level || p.distanceTo(m) > 48.0;
			if (unwatched) {
				m.setAttached(SICK, new Sick(s.since(), s.from(), night));
				if (Possession.seize(m, "murmur:" + m.getUUID(), -1, "murmur", null)) {
					Callouts.say(m, "murmur.loose", null, true);
					level.playSound(null, m.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0F, 0.7F);
					// screams wake the sleepers near
					for (ServerPlayer near : level.getPlayers(q -> q.isSleeping() && q.distanceTo(m) < 64.0)) {
						near.stopSleepInBed(true, true);
					}
				}
			}
		}
	}

	private static @Nullable UUID ownerOf(Mob m) {
		Loans.Owner o = m.getAttached(Loans.OWNER);
		try {
			return o == null ? null : UUID.fromString(o.id());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A cleric, or MCA's priest. */
	static boolean cleric(Villager v) {
		if (!v.isAlive() || v.isBaby() || Downed.isDowned(v) || sick(v)) {
			return false;
		}
		if (v.getVillagerData().profession().is(VillagerProfession.CLERIC)) {
			return true;
		}
		return v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath().contains("priest")).orElse(false);
	}
}
