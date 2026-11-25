package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wild Tideborn of the Abyssal Chasm (Andy, 2026-11-24: "the abyssal caves can be tideborn eel and manta ray inhabited.
 * extrememly hostile"; Lore.md, "The wild Tideborn of the chasm"). While a player is down in Alex's Abyssal Chasm
 * ({@link #CHASM}), the eel-shaped and manta-winged ({@link HeritageVariants.Variant#MORAY},
 * {@link HeritageVariants.Variant#MANTAWING}) come up out of the dark, {@link #COME_MIN} to {@link #COME_MAX} blocks off,
 * up to {@link #MAX_NEAR} at a time. They are MCA villagers of the Tideborn blood, bare but for a trident, tougher than
 * any guard ({@link #HEALTH}, {@link #DAMAGE}), and they go for whatever comes down from above on sight
 * ({@link #SIGHT}): players and any other villager, Tideborn or not. Your standing with the Deep Ones eases them
 * ({@link #KIN_FROM}). In the water they swim straight at their foe
 * ({@link #SWIM}; the mantas faster), where the villagers' own legs would only bob them up. They keep to the chasm: one
 * with no player within {@link #GONE} is gone, and none are left after a restart.
 */
public final class ChasmTideborn {
	public static final ResourceKey<Biome> CHASM = ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(AlexsCavesCompat.MOD_ID, "abyssal_chasm"));
	public static final String TAG = "jaymoskingdoms.chasm_wild";
	// ---- tuning ----
	/** How often (ticks) the chasm may send more, the chance each time, how many come, and the most near one player. */
	static final int COME_EVERY = 100;
	static final float COME_CHANCE = 0.35F;
	static final int COME_LEAST = 1, COME_MOST = 2, MAX_NEAR = 4;
	/** Blocks: where they come up from, how near counts toward {@link #MAX_NEAR}, how far they see a foe, and when one is gone. */
	static final int COME_MIN = 16, COME_MAX = 28;
	static final double NEAR = 64.0, SIGHT = 24.0, GONE = 96.0;
	/** Swimming speed (blocks a tick), eels and mantas. */
	static final double SWIM = 0.16, MANTA_SWIM = 0.22;
	static final double HEALTH = 40.0, DAMAGE = 6.0;
	/**
	 * Your standing with the Deep Ones (Alex's own reputation, -100 to 100, won by trading with them and defending them)
	 * eases them (Andy: "they should ease up on you as you build relation with the lurkers who stalk you, either through
	 * trades or defense"): above {@link #WARY_FROM} they only go for you when you come within {@link #WARY_SIGHT}; from
	 * {@link #KIN_FROM} they leave you be unless you strike one first.
	 */
	static final int WARY_FROM = 0, KIN_FROM = 50;
	static final double WARY_SIGHT = 6.0;
	static final int TRIES = 12;

	private static final Set<UUID> WILD = new HashSet<>();

	private ChasmTideborn() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ChasmTideborn::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WILD.clear());
		// none are kept over a restart
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && !WILD.contains(entity.getUUID())) {
				entity.discard();
			}
		});
	}

	static boolean wild(LivingEntity e) {
		return e.entityTags().contains(TAG);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null || !McaCompat.LOADED) {
			return;
		}
		if (!WILD.isEmpty()) {
			for (UUID id : List.copyOf(WILD)) {
				if (level.getEntity(id) instanceof Villager v && v.isAlive()) {
					hunt(level, v);
				} else {
					WILD.remove(id);
				}
			}
		}
		if (server.getTickCount() % COME_EVERY != 0) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !level.getBiome(player.blockPosition()).is(CHASM) || random.nextFloat() >= COME_CHANCE) {
				continue;
			}
			int near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR), ChasmTideborn::wild).size();
			int count = Math.min(MAX_NEAR - near, COME_LEAST + random.nextInt(COME_MOST - COME_LEAST + 1));
			for (int i = 0; i < count; i++) {
				come(level, player, random);
			}
		}
	}

	/** One comes up out of the dark, in open water some way off. */
	private static void come(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = COME_MIN + random.nextInt(COME_MAX - COME_MIN + 1);
			BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY() + random.nextInt(9) - 6,
				player.getZ() + Math.sin(angle) * distance);
			if (!level.getFluidState(at).is(FluidTags.WATER) || !level.getFluidState(at.above()).is(FluidTags.WATER) || !level.getBiome(at).is(CHASM)) {
				continue;
			}
			Villager wild = McaCompat.spawnGuard(level, Vec3.atBottomCenterOf(at));
			if (wild == null) {
				return;
			}
			wild.addTag(TAG);
			VillagerHeritage.born(wild, Heritage.Kind.TIDEBORN, null);
			HeritageVariants.set(wild, random.nextBoolean() ? HeritageVariants.Variant.MORAY : HeritageVariants.Variant.MANTAWING);
			for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND)) {
				wild.setItemSlot(slot, ItemStack.EMPTY);
			}
			wild.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
			wild.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			wild.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
			wild.setHealth((float)HEALTH);
			if (wild.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
				wild.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DAMAGE);
			}
			WILD.add(wild.getUUID());
			return;
		}
	}

	/** Every tick: gone with nobody near; else at the nearest foe in sight, swimming straight at it in the water. */
	private static void hunt(ServerLevel level, Villager wild) {
		Player watcher = level.getNearestPlayer(wild, GONE);
		if (watcher == null) {
			WILD.remove(wild.getUUID());
			wild.discard();
			return;
		}
		LivingEntity foe = Rulers.target(wild);
		if (foe instanceof Player p && wild.tickCount % 20 == 0 && eased(level, wild, p)) {
			// their standing has eased this one off them
			wild.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
			foe = null;
		}
		if (foe == null || !foe.isAlive() || wild.tickCount % 20 == 0) {
			LivingEntity next = foe(level, wild);
			if (next != null && next != foe) {
				Rulers.charge(wild, next);
				foe = next;
			}
		}
		if (foe != null && foe.isAlive() && wild.isInWater()) {
			Vec3 to = foe.getEyePosition().subtract(wild.position().add(0.0, wild.getBbHeight() * 0.5, 0.0));
			double speed = HeritageVariants.of(wild) == HeritageVariants.Variant.MANTAWING ? MANTA_SWIM : SWIM;
			if (to.lengthSqr() > 1.0) {
				wild.setDeltaMovement(to.normalize().scale(speed));
				wild.getLookControl().setLookAt(foe);
			}
		}
	}

	/** Whether {@code player}'s standing with the Deep Ones keeps {@code wild} off them now (never once they've struck it). */
	static boolean eased(ServerLevel level, Villager wild, Player player) {
		if (wild.getLastHurtByMob() == player) {
			return false;
		}
		int standing = standing(level, player);
		return standing >= KIN_FROM || standing > WARY_FROM && wild.distanceToSqr(player) > WARY_SIGHT * WARY_SIGHT;
	}

	/** {@code player}'s standing with the Deep Ones, Alex's own count (0 if it can't be read). */
	static int standing(ServerLevel level, Player player) {
		try {
			return com.github.alexmodguy.alexscaves.server.level.storage.ACWorldData.get(level).getDeepOneReputation(player.getUUID());
		} catch (RuntimeException | LinkageError e) {
			return 0;
		}
	}

	/** The nearest in sight that came down from above: a player in play, or any villager not of the chasm. */
	private static @Nullable LivingEntity foe(ServerLevel level, Villager wild) {
		LivingEntity best = null;
		double bestD = SIGHT * SIGHT;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, wild.getBoundingBox().inflate(SIGHT),
			e -> e.isAlive() && (e instanceof Player p && !p.isCreative() && !p.isSpectator() || e instanceof AbstractVillager && !wild(e)))) {
			double d = wild.distanceToSqr(e);
			if (e instanceof Player p && eased(level, wild, p)) {
				continue;
			}
			if (d < bestD && wild.hasLineOfSight(e)) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}
}
