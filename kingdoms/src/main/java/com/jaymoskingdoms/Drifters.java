package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.413) Drifters for hire. Now and then a down-and-out stranger turns up in a settlement's tavern, or on the road a
 * short way out of it: an MCA villager in the poorest clothes MCA has (the nitwit's), who'll fight for a few emeralds.
 * Hold {@link #HIRE_COST} emeralds and use them on one (MCA's own Hire is 5 or 10) and they're a hired mercenary (MCA's,
 * as if hired the usual way: they follow, stay, and are the player's to command), for {@link #HIRED_TICKS}. But they
 * start weak ({@link #HEALTH} health, {@link #DAMAGE} damage), so they're worth it for what {@link Learners} makes of them.
 * <p>
 * Until hired a drifter is no one's: no hearts and no tie, so MCA's stranger rule ({@link Strangers}) refuses them every
 * order; hiring makes them MCA's MERCENARY, which the rule counts as involved. Rare and capped: a settlement thinks about
 * it every {@link #EVERY} ticks ({@link #CHANCE}), with at most {@link #AREA_CAP} unhired drifters within {@link #AREA}
 * of its seat, and one nobody takes on moves along after {@link #LIFETIME_TICKS} with no one near. Only loaded with MCA.
 */
public final class Drifters {
	/** How often a settlement may draw a drifter (ticks), the chance each time, and the most unhired ones round a seat. */
	static final int EVERY = 400;
	static final float CHANCE = 0.06F;
	static final int AREA = 96, AREA_CAP = 2;
	/** A drifter only comes if a player is this close to the seat. */
	static final double PLAYER_RANGE = 96.0;
	/** The chance one comes to the tavern when the settlement has one (else, or otherwise, the road). */
	static final float TAVERN_CHANCE = 0.6F;
	/** On a road: how many route points from the settlement's end they stand (at least, and up to this much more). */
	static final int ROAD_MIN = 24, ROAD_MORE = 40;
	/** Emeralds to hire one (MCA's is 5 for a short contract), how long they're hired for (ticks; MCA's long contract), the least hearts they take to you ({@link #keptHearts} raises it to what MCA needs to keep them). */
	static final int HIRE_COST = 2, HIRED_TICKS = 168000, HIRE_HEARTS = 30;
	/** What they start with (a villager's is 20 and 1). */
	static final double HEALTH = 8.0, DAMAGE = 1.0;
	/** An unhired drifter moves along after this long (ticks), if no player is within {@link #LEAVE_RANGE}. */
	static final long LIFETIME_TICKS = 96000L;
	static final double LEAVE_RANGE = 48.0, PITCH_RANGE = 6.0;
	/** How often the drifters are looked at (ticks). */
	static final int LOOK_EVERY = 40;
	static final int LINES = 3;
	static final double SPEECH_RANGE = 10.0;
	/** The poorest clothes MCA has: the nitwit's (by gender). */
	static final String CLOTHES = "mca:skins/clothing/normal/%s/nitwit/0.png";

	/** On a drifter: the game time they turned up. Saved. */
	public static final AttachmentType<Long> DRIFTER = AttachmentRegistry.create(JaymosKingdoms.id("drifter"),
		builder -> builder.persistent(Codec.LONG));

	private static final Set<VillagerEntityMCA> LOADED = new HashSet<>();
	/** Who has made their pitch (once each, per session). */
	private static final Set<UUID> PITCHED = new HashSet<>();

	private Drifters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Drifters::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof VillagerEntityMCA v && v.hasAttached(DRIFTER)) {
				LOADED.add(v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof VillagerEntityMCA v) {
				LOADED.remove(v);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			PITCHED.clear();
		});
		UseEntityCallback.EVENT.register(Drifters::use);
	}

	/** {@code e} is a drifter (hired or not). */
	public static boolean is(@Nullable Entity e) {
		return e instanceof VillagerEntityMCA && e.hasAttached(DRIFTER);
	}

	/** {@code e} is a drifter still looking for work. */
	public static boolean unhired(@Nullable Entity e) {
		return e instanceof VillagerEntityMCA v && v.hasAttached(DRIFTER) && !Learners.hired(v);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % LOOK_EVERY == 0) {
			look(level, now);
		}
		if (now % EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())
				|| level.getRandom().nextFloat() >= CHANCE || Economy.atWar(server, f) || Economy.underAttack(server, f)) {
				continue;
			}
			BlockPos seat = f.seat();
			if (level.getNearestPlayer(seat.getX(), seat.getY(), seat.getZ(), PLAYER_RANGE, false) == null) {
				continue;
			}
			AABB box = new AABB(seat).inflate(AREA);
			if (level.getEntitiesOfClass(VillagerEntityMCA.class, box, Drifters::unhired).size() >= AREA_CAP) {
				continue;
			}
			arrive(level, f, now);
		}
	}

	/** Drifters nobody took on move along; a player who comes close to one hears their pitch. */
	private static void look(ServerLevel level, long now) {
		for (VillagerEntityMCA v : new ArrayList<>(LOADED)) {
			if (!v.isAlive()) {
				LOADED.remove(v);
				continue;
			}
			if (v.level() != level) {
				// (in another dimension: left for its own pass)
				continue;
			}
			if (Learners.hired(v)) {
				continue;
			}
			Long came = v.getAttached(DRIFTER);
			if (came != null && now - came > LIFETIME_TICKS && level.getNearestPlayer(v, LEAVE_RANGE) == null) {
				LOADED.remove(v);
				PITCHED.remove(v.getUUID());
				v.discard();
				continue;
			}
			if (!PITCHED.contains(v.getUUID()) && level.getNearestPlayer(v, PITCH_RANGE) != null) {
				PITCHED.add(v.getUUID());
				Speech.say(level, v, Component.translatable("speech.jaymoskingdoms.drifter.pitch." + level.getRandom().nextInt(LINES), HIRE_COST),
					Speech.WHITE, SPEECH_RANGE);
			}
		}
	}

	/** A drifter turns up at {@code f}: in its tavern if it has one (often), otherwise on a road leading out of it. Null if there was nowhere. */
	static @Nullable VillagerEntityMCA arrive(ServerLevel level, Faction f, long now) {
		RandomSource random = level.getRandom();
		BlockPos at = null;
		if (random.nextFloat() < TAVERN_CHANCE) {
			List<Meals.Eatery> taverns = Meals.eateries(level, f).stream().filter(Meals.Eatery::tavern).toList();
			if (!taverns.isEmpty()) {
				at = Construction.standable(level, taverns.get(random.nextInt(taverns.size())).counter(), 3);
			}
		}
		if (at == null) {
			at = onRoad(level, f, random);
		}
		if (at == null || !level.isPositionEntityTicking(at)) {
			return null;
		}
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NITWIT).value();
		Villager made = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), job, false);
		if (!(made instanceof VillagerEntityMCA v)) {
			return null;
		}
		v.setAttached(DRIFTER, now);
		LOADED.add(v);
		weaken(v);
		String gender = v.getGenetics().getGender() == Gender.FEMALE ? "female" : "male";
		v.setClothes(String.format(CLOTHES, gender));
		JaymosKingdoms.LOGGER.debug("A drifter came to {}", f.name());
		return v;
	}

	/** A spot on a road leading out of {@code f}, a way from its end; null if it has none loaded. */
	private static @Nullable BlockPos onRoad(ServerLevel level, Faction f, RandomSource random) {
		List<Roads.Road> mine = Roads.network(level.getServer()).roads().stream().filter(r -> r.a() == f.id() || r.b() == f.id()).toList();
		if (mine.isEmpty()) {
			return null;
		}
		Roads.Road road = mine.get(random.nextInt(mine.size()));
		int n = road.points().size();
		if (n < 2) {
			return null;
		}
		int in = Math.min(n - 1, ROAD_MIN + random.nextInt(ROAD_MORE));
		long packed = road.points().get(road.a() == f.id() ? in : n - 1 - in);
		BlockPos p = BlockPos.of(packed);
		return level.isPositionEntityTicking(p) ? Construction.standable(level, p.above(), 2) : null;
	}

	/**
	 * Hearts a hire gives: at least MCA's marriage requirement, since at the contract's end MCA keeps a mercenary only if
	 * {@code nextInt(requirement) < hearts} (and discards them otherwise), and a hired drifter is always to be kept.
	 */
	static int keptHearts() {
		return Math.max(HIRE_HEARTS, net.conczin.mca.Config.getInstance().marriageHeartsRequirement);
	}

	/** {@code v} starts out thin and weak. */
	static void weaken(VillagerEntityMCA v) {
		AttributeInstance health = v.getAttribute(Attributes.MAX_HEALTH), damage = v.getAttribute(Attributes.ATTACK_DAMAGE);
		if (health != null) {
			health.setBaseValue(HEALTH);
			v.setHealth((float)HEALTH);
		}
		if (damage != null) {
			damage.setBaseValue(DAMAGE);
		}
	}

	/** Emeralds in the main hand, used on an unhired drifter, hire them. */
	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || sp.isSpectator()
			|| !unhired(entity) || !sp.getMainHandItem().is(Items.EMERALD)) {
			return InteractionResult.PASS;
		}
		VillagerEntityMCA v = (VillagerEntityMCA)entity;
		if (!v.isAlive() || Purse.count(sp, Items.EMERALD) < HIRE_COST) {
			Speech.sayTo(server, v, sp, Component.translatable("speech.jaymoskingdoms.drifter.short", HIRE_COST), Speech.WHITE);
			return InteractionResult.SUCCESS;
		}
		Purse.take(sp, Items.EMERALD, HIRE_COST);
		String rags = v.getClothes();
		v.makeMercenary();
		// (making them a mercenary dresses them again, and so does the contract's end: keep their rags)
		v.setClothes(rags);
		v.setClothingLocked(true);
		v.setDespawnDelay(HIRED_TICKS);
		net.conczin.mca.entity.ai.Memories memories = v.getVillagerBrain().getMemoriesForPlayer(sp);
		memories.setHearts(Math.max(memories.getHearts(), keptHearts()));
		// (hired, they never go back to the drifter pool: no cap, no moving along)
		v.removeAttached(DRIFTER);
		LOADED.remove(v);
		PITCHED.remove(v.getUUID());
		sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.drifter.hired", v.getName(), HIRE_COST).withStyle(ChatFormatting.GRAY), true);
		Speech.sayTo(server, v, sp, Component.translatable("speech.jaymoskingdoms.drifter.hired." + server.getRandom().nextInt(LINES)), Speech.WHITE);
		return InteractionResult.SUCCESS;
	}
}
