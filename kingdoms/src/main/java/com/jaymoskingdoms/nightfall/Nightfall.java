package com.jaymoskingdoms.nightfall;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Vampires;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Kingdoms 0.7.129) The Nightfall Plague, our own take on the Forge mod of that name (All Rights Reserved: nothing of
 * it is used, only its ideas), woven into our vampires ({@link Vampires}):
 *
 * <ul>
 * <li>The plague, {@link Atrophy}: carried by Night Bleeders' bites, Cursed Tusks and Tusk Arrows (tipped arrows of the
 * Plague potion). Whatever dies with it in its blood rises as a vampire bat ({@link #rise}).</li>
 * <li>The cure, {@link Immunity}: the Immunity potion, brewed from an awkward potion and a Cursed Tusk (and so Immunity
 * Arrows); it burns the undead. Turned vampires still need the priest ({@code Cures}).</li>
 * <li>The Night Bleeder ({@link NightBleeder}), its Blightwing ({@link Blightwing}) and vampire bats ({@link VampireBat}).</li>
 * <li>Our vampires' callings ({@link Callings}) and their wares ({@link Relics}); raw beef quiets a vampire for the night.</li>
 * <li>Nightmares, and the Nightfall itself: two or three nights the sun doesn't rise ({@link Nightfalls}).</li>
 * </ul>
 */
public final class Nightfall {
	/** Natural spawns (a zombie is 100): Night Bleeders, vampire bats (and how many come together). */
	static final int BLEEDER_WEIGHT = 3, BAT_WEIGHT = 10, BAT_GROUP_MIN = 2, BAT_GROUP_MAX = 4;
	/** A raid's last waves bring at most this many Night Bleeders in all; they arrive fire-resistant this long (ticks). */
	static final int RAID_BLEEDERS = 2, RAID_FIRE_TICKS = 20 * 15;
	/** The plague potions' lengths (ticks): plain, strong; the Immunity potion's, plain and long. */
	static final int ATROPHY_POTION_TICKS = 20 * 45, STRONG_ATROPHY_TICKS = 20 * 22, IMMUNITY_TICKS = 20 * 60 * 10, LONG_IMMUNITY_TICKS = 20 * 60 * 20;

	static final DustParticleOptions BLOOD = new DustParticleOptions(0x8A0303, 1.0F);

	public static Holder<MobEffect> ATROPHY, IMMUNITY;
	public static Holder<Potion> IMMUNITY_POTION, LONG_IMMUNITY_POTION, ATROPHY_POTION, STRONG_ATROPHY_POTION;
	public static EntityType<NightBleeder> NIGHT_BLEEDER;
	public static EntityType<Blightwing> BLIGHTWING;
	public static EntityType<VampireBat> VAMPIRE_BAT;
	public static Item CURSED_TUSK, CURSED_CARD, LUTE, DRIED_MEAT, NIGHT_BLEEDER_SPAWN_EGG, VAMPIRE_BAT_SPAWN_EGG;

	/** Night Bleeders already sent with each raid, and the last wave one came with. */
	private static final Map<Raid, int[]> RAID_BLEEDERS_SENT = new WeakHashMap<>();
	/** Raiders seen arriving this tick, to send a Night Bleeder along with (done next tick, outside the load). */
	private static final List<Raider> ARRIVING = new ArrayList<>();

	private Nightfall() {
	}

	public static void register() {
		ATROPHY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id("atrophy"), new Atrophy());
		IMMUNITY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id("immunity"), new Immunity());
		IMMUNITY_POTION = potion("immunity", "immunity", new MobEffectInstance(IMMUNITY, IMMUNITY_TICKS));
		LONG_IMMUNITY_POTION = potion("long_immunity", "immunity", new MobEffectInstance(IMMUNITY, LONG_IMMUNITY_TICKS));
		ATROPHY_POTION = potion("atrophy", "atrophy", new MobEffectInstance(ATROPHY, ATROPHY_POTION_TICKS));
		STRONG_ATROPHY_POTION = potion("strong_atrophy", "atrophy", new MobEffectInstance(ATROPHY, STRONG_ATROPHY_TICKS, 1));

		NIGHT_BLEEDER = type("night_bleeder", EntityType.Builder.of(NightBleeder::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
		BLIGHTWING = type("blightwing", EntityType.Builder.of(Blightwing::new, MobCategory.MONSTER).sized(0.8F, 1.2F).clientTrackingRange(8));
		VAMPIRE_BAT = type("vampire_bat", EntityType.Builder.of(VampireBat::new, MobCategory.MONSTER).sized(0.5F, 0.9F).clientTrackingRange(5));
		FabricDefaultAttributeRegistry.register(NIGHT_BLEEDER, NightBleeder.createAttributes());
		FabricDefaultAttributeRegistry.register(BLIGHTWING, Blightwing.createAttributes());
		FabricDefaultAttributeRegistry.register(VAMPIRE_BAT, VampireBat.createAttributes());
		SpawnPlacements.register(NIGHT_BLEEDER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(type, level, reason, pos, random) -> nightOut(level, reason) && Monster.checkMonsterSpawnRules(type, level, reason, pos, random));
		SpawnPlacements.register(VAMPIRE_BAT, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			(type, level, reason, pos, random) -> nightOut(level, reason) && (EntitySpawnReason.ignoresLightRequirements(reason)
				|| Monster.isDarkEnoughToSpawn(level, pos, random) && level.canSeeSky(pos)) && Mob.checkMobSpawnRules(type, level, reason, pos, random));
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, NIGHT_BLEEDER, BLEEDER_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER, VAMPIRE_BAT, BAT_WEIGHT, BAT_GROUP_MIN, BAT_GROUP_MAX);

		CURSED_TUSK = item("cursed_tusk", Relics.CursedTusk::new, new Item.Properties().rarity(Rarity.UNCOMMON));
		CURSED_CARD = item("cursed_card", Relics.CursedCard::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		LUTE = item("lute", Relics.Lute::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
		DRIED_MEAT = item("dried_meat", Relics.DriedMeat::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build()));
		NIGHT_BLEEDER_SPAWN_EGG = item("night_bleeder_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(NIGHT_BLEEDER));
		VAMPIRE_BAT_SPAWN_EGG = item("vampire_bat_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(VAMPIRE_BAT));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.GHAST_TEAR, CURSED_TUSK));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.GOAT_HORN, LUTE);
			output.insertAfter(LUTE, CURSED_CARD);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.insertAfter(Items.COOKED_BEEF, DRIED_MEAT));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.insertAfter(Items.VINDICATOR_SPAWN_EGG, NIGHT_BLEEDER_SPAWN_EGG);
			output.insertAfter(Items.BAT_SPAWN_EGG, VAMPIRE_BAT_SPAWN_EGG);
		});

		ServerLivingEntityEvents.ALLOW_DEATH.register(Nightfall::allowDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(Nightfall::rise);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.getEntity() instanceof Mob mob && source.getDirectEntity() == mob && Relics.Lute.calmed(mob, entity.level().getGameTime())));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Raider raider && !(entity instanceof NightBleeder) && raider.getCurrentRaid() != null) {
				ARRIVING.add(raider);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Nightfall::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RAID_BLEEDERS_SENT.clear();
			ARRIVING.clear();
			Relics.Lute.CALMED.clear();
		});
		Callings.register();
		Nightfalls.register();
	}

	/** A potion under {@code id}, named (for its translation) {@code name}, as vanilla's long and strong ones share their plain one's. */
	private static Holder<Potion> potion(String id, String name, MobEffectInstance effect) {
		return Registry.registerForHolder(BuiltInRegistries.POTION, JaymosKingdoms.id(id), new Potion(name, effect));
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	// ---- who's what ----------------------------------------------------------------------------------------------------

	/** Night Bleeders, Blightwings and vampire bats. */
	public static boolean vampiric(Entity e) {
		return e instanceof NightBleeder || e instanceof Blightwing || e instanceof VampireBat;
	}

	/** Who the plague's creatures leave alone: their own, and every vampire (a player too). */
	static boolean kin(Entity e) {
		return vampiric(e) || Vampires.isVampire(e);
	}

	/** Whether the plague can take hold in {@code mob}: not with Immunity in it, not in the undead or a vampire. */
	static boolean catches(LivingEntity mob) {
		return !mob.hasEffect(IMMUNITY) && !Immunity.unholy(mob) && !(mob instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** Gives {@code mob} the plague, if it can catch it. */
	static void infect(LivingEntity mob, int ticks, int amplifier) {
		if (catches(mob)) {
			mob.addEffect(new MobEffectInstance(ATROPHY, ticks, amplifier));
		}
	}

	/** A Night Bleeder's bite: the plague, or worse if it's already in them. */
	static void bite(LivingEntity mob, int ticks) {
		infect(mob, ticks, mob.hasEffect(ATROPHY) ? 1 : 0);
	}

	/** Whether the blow was struck with a wooden blade. */
	static boolean wooden(DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
			return false;
		}
		ItemStack weapon = attacker.getMainHandItem();
		return weapon.is(Items.WOODEN_SWORD) || weapon.is(ItemTags.SWORDS) && BuiltInRegistries.ITEM.getKey(weapon.getItem()).getPath().contains("wood");
	}

	/** By night (or not a natural spawn), in the Overworld's time. */
	private static boolean nightOut(ServerLevelAccessor level, EntitySpawnReason reason) {
		return reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
			|| Vampires.night(level.getLevel().getOverworldClockTime());
	}

	/** Solid ground under {@code at} (within a few blocks down), or the surface. */
	static Vec3 ground(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		for (int i = 0; i < 16; i++) {
			BlockPos below = pos.below(i);
			if (level.getBlockState(below.below()).isFaceSturdy(level, below.below(), net.minecraft.core.Direction.UP)
				&& level.getBlockState(below).getCollisionShape(level, below).isEmpty()
				&& level.getBlockState(below.above()).getCollisionShape(level, below.above()).isEmpty()) {
				return Vec3.atBottomCenterOf(below);
			}
		}
		return Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos));
	}

	// ---- deaths --------------------------------------------------------------------------------------------------------

	/** A Night Bleeder brought down by anything but a critical hit breaks into its Blightwing instead of dying. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof NightBleeder bleeder) || !(entity.level() instanceof ServerLevel level) || NightBleeder.critical(source)
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		Blightwing wing = BLIGHTWING.create(level, EntitySpawnReason.CONVERSION);
		if (wing == null) {
			return true;
		}
		wing.snapTo(bleeder.getX(), bleeder.getY(0.5), bleeder.getZ(), bleeder.getYRot(), 0.0F);
		wing.setVariant(bleeder.variant());
		if (bleeder.hasCustomName()) {
			wing.setCustomName(bleeder.getCustomName());
		}
		if (bleeder.isPersistenceRequired()) {
			wing.setPersistenceRequired();
		}
		wing.setDeltaMovement(0.0, 0.4, 0.0);
		level.addFreshEntity(wing);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, bleeder.getX(), bleeder.getY(0.5), bleeder.getZ(), 30, 0.4, 0.8, 0.4, 0.04);
		level.sendParticles(BLOOD, bleeder.getX(), bleeder.getY(0.5), bleeder.getZ(), 12, 0.3, 0.5, 0.3, 0.0);
		level.playSound(null, bleeder.getX(), bleeder.getY(), bleeder.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.4F, 0.5F);
		bleeder.discard();
		return false;
	}

	/** Whatever dies with the plague in it rises as a vampire bat (players and the plague's own creatures don't). */
	static void rise(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || entity instanceof Player || entity instanceof Bat || kin(entity) || !entity.hasEffect(ATROPHY)) {
			return;
		}
		VampireBat bat = VAMPIRE_BAT.create(level, EntitySpawnReason.CONVERSION);
		if (bat == null) {
			return;
		}
		bat.snapTo(entity.getX(), entity.getY(0.6), entity.getZ(), entity.getYRot(), 0.0F);
		bat.setResting(false);
		level.addFreshEntity(bat);
		level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY(0.6), entity.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!ARRIVING.isEmpty()) {
			List<Raider> arriving = new ArrayList<>(ARRIVING);
			ARRIVING.clear();
			for (Raider raider : arriving) {
				joinRaid(raider);
			}
		}
		if (server.getTickCount() % Relics.CursedCard.QUICKEN_EVERY == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if ((player.getMainHandItem().is(CURSED_CARD) || player.getOffhandItem().is(CURSED_CARD))
					&& player.getRandom().nextFloat() < Relics.CursedCard.QUICKEN_CHANCE) {
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, Relics.CursedCard.QUICKEN_TICKS, 0, false, false, true));
				}
			}
		}
	}

	/** A raid's last waves: a Night Bleeder comes along (at most {@link #RAID_BLEEDERS} a raid). */
	private static void joinRaid(Raider raider) {
		Raid raid = raider.getCurrentRaid();
		if (raid == null || !raider.isAlive() || !(raider.level() instanceof ServerLevel level)) {
			return;
		}
		int wave = raid.getGroupsSpawned();
		int last = raid.getNumGroups(level.getDifficulty());
		int[] sent = RAID_BLEEDERS_SENT.computeIfAbsent(raid, r -> new int[] {0, -1});
		if (wave < Math.max(2, last - 1) || sent[0] >= RAID_BLEEDERS || sent[1] == wave) {
			return;
		}
		RandomSource random = level.getRandom();
		if (random.nextFloat() > 0.6F) {
			return;
		}
		NightBleeder bleeder = NIGHT_BLEEDER.create(level, EntitySpawnReason.EVENT);
		if (bleeder == null) {
			return;
		}
		sent[0]++;
		sent[1] = wave;
		raid.joinRaid(level, wave, bleeder, raider.blockPosition().below(), false);
		bleeder.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, RAID_FIRE_TICKS, 0));
	}

	static @Nullable ServerLevel overworld(MinecraftServer server) {
		return server.overworld();
	}
}
