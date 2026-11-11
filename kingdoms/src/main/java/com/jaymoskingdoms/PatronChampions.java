package com.jaymoskingdoms;

import com.jaymoskingdoms.champions.MoveKit;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The eight patron champions' types and everything round them: the hunt (the old patron's champion hunts you once,
 * after the trials), what a fallen champion leaves (a weapon head for each fighter, sure by their third kill), the adds, and
 * the op commands. See {@link PatronChampion} and docs/Patrons-C-Plan.md.
 */
public final class PatronChampions {
	/** How often hunted players are looked at (ticks), and the chance the champion comes then (about ten minutes on average). */
	static final int HUNT_CHECK = 20 * 30;
	static final float HUNT_CHANCE = 0.05F;
	/** Where it comes: this far from its quarry, on standable ground with HEADROOM, tried this often; out of their view if it can. */
	static final int HUNT_NEAR_MIN = 18, HUNT_NEAR_MAX = 28, HUNT_TRIES = 12, HEADROOM = 3;
	/** After its quarry got away (logged off, left the world, outran it, or it ran out of time), the wait before it comes again (ticks). */
	static final int HUNT_RETRY = 24000;
	/** It stands over its downed quarry this long before it goes (ticks). */
	static final int HUNT_STAND = 60;

	/** (0.7.518) Per player: champion kills of each patron (by key) since that patron's weapon last fell for them. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> TRIES = AttachmentRegistry.create(JaymosKingdoms.id("champion_tries"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
	/** The chance the head falls for a fighter on their 1st, 2nd and 3rd try since their last: the third is sure. */
	static final float[] HEAD_CHANCE = {0.34F, 0.5F, 1.0F};
	/** The grade a head falls at (Powers' smithing grade ids): a solo kill's is a Masterwork. */
	static final String HEAD_GRADE = "perfect", HEAD_GRADE_SOLO = "master";
	/** The pickup delay of a fighter's head (ticks). */
	static final int HEAD_PICKUP_DELAY = 10;

	/** The champion each of its adds belongs to. Saved only so an add that reloads can be found and discarded. */
	static final AttachmentType<UUID> ADD = AttachmentRegistry.create(JaymosKingdoms.id("champion_add"),
		b -> b.persistent(net.minecraft.core.UUIDUtil.CODEC));
	/** The smoke double's burst: Blindness this long (ticks) on players within this many blocks. */
	static final int DECOY_BLIND_TICKS = 60;
	static final double DECOY_BLIND_RADIUS = 3.0;
	/** How far from the champion its adds and moves look for foes (blocks). */
	public static final double FOE_RANGE = 40.0;

	static final Map<Patron, EntityType<PatronChampion>> TYPES = new EnumMap<>(Patron.class);
	private static final Map<EntityType<?>, Patron> BY_TYPE = new HashMap<>();
	private static final Map<Patron, Item> EGGS = new EnumMap<>(Patron.class);
	private static final Map<UUID, List<Mob>> ADDS = new HashMap<>();
	/** Each hunted player's hunter, and when each hunt may begin again, and who stands over a quarry since when. */
	private static final Map<UUID, PatronChampion> HUNTERS = new HashMap<>();
	private static final Map<UUID, Long> NEXT_HUNT = new HashMap<>();
	private static final Map<UUID, Long> STANDING = new HashMap<>();

	private PatronChampions() {
	}

	/** The entity id of a patron's champion. */
	public static String id(Patron p) {
		return switch (p) {
			case HEARTHKEEPER -> "hearth_warden";
			case ANVIL_FATHER -> "forge_thane";
			case DEEP_MOTHER -> "tide_bride";
			case GREEN_HUNTSMAN -> "antlered_hunter";
			case LANTERN_WARDEN -> "grave_lamp";
			case CROWNED_SUN -> "sun_marshal";
			case ASH_WHISPERER -> "cinder_shade";
			case TRUTH -> "inquisitor";
		};
	}

	/** Width, height, fire immunity. */
	private static float width(Patron p) {
		return switch (p) {
			case HEARTHKEEPER, GREEN_HUNTSMAN, LANTERN_WARDEN, DEEP_MOTHER, TRUTH -> 0.8F;
			case ANVIL_FATHER -> 1.1F;
			case CROWNED_SUN -> 0.9F;
			case ASH_WHISPERER -> 0.7F;
		};
	}

	private static float height(Patron p) {
		return switch (p) {
			case HEARTHKEEPER -> 2.4F;
			case ANVIL_FATHER, ASH_WHISPERER -> 2.2F;
			case DEEP_MOTHER, LANTERN_WARDEN, TRUTH -> 2.6F;
			case GREEN_HUNTSMAN -> 2.8F;
			case CROWNED_SUN -> 2.7F;
		};
	}

	static boolean fireImmune(Patron p) {
		return p == Patron.HEARTHKEEPER || p == Patron.ANVIL_FATHER || p == Patron.CROWNED_SUN || p == Patron.ASH_WHISPERER;
	}

	static BossEvent.BossBarColor barColour(Patron p) {
		return switch (p) {
			case HEARTHKEEPER -> BossEvent.BossBarColor.RED;
			case ANVIL_FATHER, TRUTH -> BossEvent.BossBarColor.WHITE;
			case DEEP_MOTHER -> BossEvent.BossBarColor.BLUE;
			case GREEN_HUNTSMAN -> BossEvent.BossBarColor.GREEN;
			case LANTERN_WARDEN -> BossEvent.BossBarColor.PURPLE;
			case CROWNED_SUN -> BossEvent.BossBarColor.YELLOW;
			case ASH_WHISPERER -> BossEvent.BossBarColor.PINK;
		};
	}

	/** Powers' item id of a patron's weapon, and of its head. */
	static String weaponId(Patron p) {
		return switch (p) {
			case HEARTHKEEPER -> "hearthguard";
			case ANVIL_FATHER -> "anvil_fathers_hammer";
			case DEEP_MOTHER -> "tidecaller";
			case GREEN_HUNTSMAN -> "huntsmans_spear";
			case LANTERN_WARDEN -> "gravelight";
			case CROWNED_SUN -> "sunbrand";
			case ASH_WHISPERER -> "ashfang";
			case TRUTH -> "plain_word";
		};
	}

	static String headId(Patron p) {
		return switch (p) {
			case HEARTHKEEPER -> "hearthguard_head";
			case ANVIL_FATHER -> "anvil_fathers_hammer_head";
			case DEEP_MOTHER -> "tidecaller_head";
			case GREEN_HUNTSMAN -> "huntsmans_spear_tip";
			case LANTERN_WARDEN -> "gravelight_blade";
			case CROWNED_SUN -> "sunbrand_blade";
			case ASH_WHISPERER -> "ashfang_blade";
			case TRUTH -> "plain_word_blade";
		};
	}

	public static @Nullable Patron patronOf(EntityType<?> type) {
		return BY_TYPE.get(type);
	}

	public static @Nullable EntityType<PatronChampion> typeOf(Patron p) {
		return TYPES.get(p);
	}

	static void register() {
		for (Patron p : Patron.values()) {
			String name = id(p);
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
			EntityType.Builder<PatronChampion> builder = EntityType.Builder.<PatronChampion>of((t, l) -> new PatronChampion(t, l), MobCategory.MONSTER)
				.sized(width(p), height(p)).clientTrackingRange(10);
			if (fireImmune(p)) {
				builder.fireImmune();
			}
			EntityType<PatronChampion> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
			TYPES.put(p, type);
			BY_TYPE.put(type, p);
			FabricDefaultAttributeRegistry.register(type, PatronChampion.createAttributes());
			ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name + "_spawn_egg"));
			Item egg = Registry.register(BuiltInRegistries.ITEM, eggKey, new SpawnEggItem(new Item.Properties().setId(eggKey).spawnEgg(type)));
			EGGS.put(p, egg);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> EGGS.values().forEach(output::accept));
		JaymosKingdoms.LOGGER.debug("champion attachment: {}", TRIES);
		ServerLivingEntityEvents.AFTER_DEATH.register(PatronChampions::died);
		ServerTickEvents.END_SERVER_TICK.register(PatronChampions::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> hunterGone(handler.player));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// an add that comes back from a save has lost its champion: it goes
			if (entity instanceof Mob mob && mob.hasAttached(ADD) && !(mob instanceof PatronChampion)) {
				List<Mob> mine = ADDS.get(mob.getAttached(ADD));
				if (mine == null || !mine.contains(mob)) {
					level.getServer().execute(mob::discard);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ADDS.clear();
			HUNTERS.clear();
			NEXT_HUNT.clear();
			STANDING.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("champion").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("summon").then(Commands.argument("patron", StringArgumentType.word()).suggests(PatronChampions::suggest)
					.executes(c -> summon(c, false))))
				.then(Commands.literal("hunt").then(Commands.argument("patron", StringArgumentType.word()).suggests(PatronChampions::suggest)
					.executes(c -> summon(c, true))))
				.then(Commands.literal("tries").then(Commands.argument("patron", StringArgumentType.word()).suggests(PatronChampions::suggest)
					.then(Commands.argument("n", IntegerArgumentType.integer(0, 9)).executes(PatronChampions::tries)))))));
	}

	static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(CommandContext<CommandSourceStack> c,
		com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
		List<String> keys = new ArrayList<>();
		for (Patron p : Patron.values()) {
			keys.add(p.key);
		}
		return SharedSuggestionProvider.suggest(keys, b);
	}

	// ---- op commands ---------------------------------------------------------------------------------------------------------

	private static int summon(CommandContext<CommandSourceStack> c, boolean hunt) {
		Patron p = Patron.byKey(StringArgumentType.getString(c, "patron"));
		ServerPlayer sp = c.getSource().getPlayer();
		if (p == null || sp == null || !(sp.level() instanceof ServerLevel level)) {
			c.getSource().sendFailure(Component.literal("No such patron, or no player."));
			return 0;
		}
		if (hunt) {
			send(sp, p, level, true);
			return 1;
		}
		PatronChampion champ = TYPES.get(p).create(level, EntitySpawnReason.COMMAND);
		if (champ == null) {
			return 0;
		}
		champ.snapTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0.0F);
		champ.loose();
		champ.setPersistenceRequired();
		level.addFreshEntity(champ);
		c.getSource().sendSuccess(() -> Component.literal("A " + p.key + " champion stands here."), false);
		return 1;
	}

	private static int tries(CommandContext<CommandSourceStack> c) {
		Patron p = Patron.byKey(StringArgumentType.getString(c, "patron"));
		ServerPlayer sp = c.getSource().getPlayer();
		if (p == null || sp == null) {
			return 0;
		}
		Map<String, Integer> map = new HashMap<>(sp.getAttachedOrElse(TRIES, Map.of()));
		map.put(p.key, IntegerArgumentType.getInteger(c, "n"));
		sp.setAttached(TRIES, Map.copyOf(map));
		c.getSource().sendSuccess(() -> Component.literal("Tries for " + p.key + " set."), false);
		return 1;
	}

	// ---- setting a temple's champion down -----------------------------------------------------------------------------------

	/** A champion asleep at {@code spot}, facing {@code facing} (the way out), for a temple. Null if it can't be made. */
	static @Nullable PatronChampion raise(ServerLevel level, Patron p, int templeId, int kind, BlockPos altar, BlockPos spot, Direction facing) {
		PatronChampion champ = TYPES.get(p).create(level, EntitySpawnReason.STRUCTURE);
		if (champ == null) {
			return null;
		}
		champ.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, facing.toYRot(), 0.0F);
		champ.sleep(templeId, kind, spot, facing.toYRot());
		level.addFreshEntityWithPassengers(champ);
		return champ;
	}

	// ---- adds ----------------------------------------------------------------------------------------------------------------

	/** A champion or one of its adds: the town's guards, royals and casters never count it a foe. */
	public static boolean outsideTown(Entity e) {
		return e instanceof PatronChampion || isAdd(e);
	}

	public static boolean isAdd(Entity e) {
		return e.hasAttached(ADD);
	}

	static void addTo(ServerLevel level, PatronChampion c, Mob mob) {
		mob.setAttached(ADD, c.getUUID());
		Vec3 spot = MoveKit.blinkSpot(level, c, 2.0, 6.0);
		Vec3 at = spot == null ? c.position() : spot;
		mob.snapTo(at.x, at.y, at.z, c.getYRot(), 0.0F);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
		((com.jaymoskingdoms.mixin.MobAccessor)mob).jaymoskingdoms$xpReward(0);
		((com.jaymoskingdoms.mixin.MobAccessor)mob).jaymoskingdoms$lootTable(java.util.Optional.of(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("empty"))));
		net.minecraft.world.entity.ai.goal.GoalSelector targets = ((com.jaymoskingdoms.mixin.MobAccessor)mob).jaymoskingdoms$targets();
		targets.removeAllGoals(g -> true);
		targets.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, true));
		List<LivingEntity> foes = c.foes(FOE_RANGE);
		if (!foes.isEmpty()) {
			mob.setTarget(foes.get(0));
		}
		ADDS.computeIfAbsent(c.getUUID(), id -> new ArrayList<>()).add(mob);
		level.addFreshEntity(mob);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 12, 0.3, 0.5, 0.3, 0.02);
	}

	static void clearAdds(ServerLevel level, PatronChampion c) {
		List<Mob> adds = ADDS.remove(c.getUUID());
		if (adds == null) {
			return;
		}
		for (Mob m : adds) {
			if (m.isAlive()) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 1.0, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				m.discard();
			}
		}
	}

	/** A smoke double burst (struck, one bursts into smoke: Blindness near it). */
	static void decoyBurst(ServerLevel level, PatronChampion decoy) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, decoy.getX(), decoy.getY() + 1.2, decoy.getZ(), 40, 0.6, 1.0, 0.6, 0.03);
		MoveKit.sound(level, decoy.position(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, decoy.getBoundingBox().inflate(DECOY_BLIND_RADIUS), e -> e instanceof Player && e.isAlive())) {
			e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, DECOY_BLIND_TICKS, 0));
		}
	}

	static void wakeSound(ServerLevel level, PatronChampion c) {
		Vec3 at = c.position();
		switch (c.patron()) {
			case HEARTHKEEPER -> MoveKit.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 1.5F, 0.5F);
			case ANVIL_FATHER -> MoveKit.sound(level, at, SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
			case DEEP_MOTHER -> MoveKit.sound(level, at, SoundEvents.CONDUIT_ATTACK_TARGET, 1.5F, 1.0F);
			case GREEN_HUNTSMAN -> MoveKit.sound(level, at, SoundEvents.POLAR_BEAR_WARNING, 1.0F, 0.6F);
			case LANTERN_WARDEN -> MoveKit.sound(level, at, SoundEvents.BELL_BLOCK, 1.0F, 0.5F);
			case CROWNED_SUN -> MoveKit.sound(level, at, SoundEvents.BEACON_ACTIVATE, 1.5F, 1.0F);
			case ASH_WHISPERER -> MoveKit.sound(level, at, SoundEvents.SCULK_CLICKING, 1.5F, 1.0F);
			case TRUTH -> MoveKit.sound(level, at, SoundEvents.BELL_BLOCK, 1.0F, 0.8F);
		}
	}

	// ---- a champion falls -----------------------------------------------------------------------------------------------------

	private static void died(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer dead && HUNTERS.containsKey(dead.getUUID())) {
			// the quarry fell: the hunt is over now, whatever the player does next (a quick respawn must not read as a lost trail)
			PatronChampion hunter = HUNTERS.remove(dead.getUUID());
			STANDING.remove(hunter.getUUID());
			if (hunter.isAlive() && hunter.level() instanceof ServerLevel hl) {
				hunter.speak("hunted");
				hl.sendParticles(ParticleTypes.LARGE_SMOKE, hunter.getX(), hunter.getY() + 1.0, hunter.getZ(), 40, 0.5, 1.0, 0.5, 0.03);
				clearAdds(hl, hunter);
				Patrons.hunted(dead, hunter.patron());
				dead.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.hunt.over", Patrons.coloured(hunter.patron())));
				hunter.discard();
			}
			return;
		}
		if (!(entity instanceof PatronChampion c) || c.decoy() || !(c.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Patron p = c.patron();
		c.clearMoves();
		clearAdds(level, c);
		Fx.burst(level, c.getX(), c.getY() + 1.2, c.getZ(), p.colour & 0xFFFFFF, 2.0F, 80, 0.6, 1.2, 0.6);
		c.speak("fall");
		if (c.hunting()) {
			HUNTERS.values().removeIf(h -> h == c);
			ServerPlayer quarry = c.quarry() == null ? null : server.getPlayerList().getPlayer(c.quarry());
			if (quarry != null) {
				Patron now = Patrons.of(quarry);
				if (now != null) {
					Patrons.deed(quarry, now, Patrons.DEED_GREAT, "stood_the_hunt");
				}
				Patrons.hunted(quarry, p);
				quarry.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.hunt.over", Patrons.coloured(p)));
			}
			return;
		}
		List<ServerPlayer> fighters = new ArrayList<>();
		for (UUID id : c.hitters()) {
			ServerPlayer sp = server.getPlayerList().getPlayer(id);
			if (sp != null && sp.level() == level && sp.distanceTo(c) <= WorldTiers.CREDIT_RANGE) {
				fighters.add(sp);
			}
		}
		boolean solo = c.group() == 1 && fighters.size() == 1;
		for (ServerPlayer sp : fighters) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.slain", Patrons.coloured(p)));
			Patron sworn = Patrons.of(sp);
			StoryRoads.champion(sp, sworn == p, sworn != null && sworn != p && sworn.rivals(p));
			if (sworn == p) {
				Patrons.deed(sp, p, Patrons.DEED_GREAT, "champion");
			} else if (sworn != null && sworn.rivals(p)) {
				Patrons.deed(sp, sworn, Patrons.DEED_MID, "rival_champion");
			}
			roll(level, c, sp, p, solo);
		}
		if (c.templeId() >= 0) {
			Temples.slain(server, c.templeId(), Patrons.today(server));
		}
	}

	private static void roll(ServerLevel level, PatronChampion c, ServerPlayer sp, Patron p, boolean solo) {
		ItemStack head = Spoils.powers(headId(p));
		if (head.isEmpty()) {
			return;
		}
		Map<String, Integer> tries = new HashMap<>(sp.getAttachedOrElse(TRIES, Map.of()));
		int n = tries.getOrDefault(p.key, 0) + 1;
		if (level.getRandom().nextFloat() < HEAD_CHANCE[Math.min(n, HEAD_CHANCE.length) - 1]) {
			grade(head, solo ? HEAD_GRADE_SOLO : HEAD_GRADE);
			ItemEntity drop = new ItemEntity(level, c.getX(), c.getY() + 0.5, c.getZ(), head);
			drop.setTarget(sp.getUUID());
			drop.setExtendedLifetime();
			drop.setPickUpDelay(HEAD_PICKUP_DELAY);
			level.addFreshEntity(drop);
			tries.put(p.key, 0);
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.weapon", head.getHoverName()));
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 1.2F);
		} else {
			tries.put(p.key, n);
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.no_weapon", n, HEAD_CHANCE.length));
		}
		sp.setAttached(TRIES, Map.copyOf(tries));
	}

	@SuppressWarnings("unchecked")
	private static void grade(ItemStack stack, String grade) {
		DataComponentType<String> type = (DataComponentType<String>)BuiltInRegistries.DATA_COMPONENT_TYPE
			.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", "forging_grade")).orElse(null);
		if (type != null) {
			stack.set(type, grade);
		}
	}

	// ---- the hunt ------------------------------------------------------------------------------------------------------------

	static boolean isHunter(PatronChampion c) {
		return HUNTERS.containsValue(c);
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % 20 == 0) {
			watchHunters(server);
		}
		if (t % 100 == 0) {
			pruneAdds(server);
		}
		if (t % HUNT_CHECK == 0) {
			huntTick(server);
		}
	}

	private static void pruneAdds(MinecraftServer server) {
		ADDS.entrySet().removeIf(e -> {
			for (ServerLevel l : server.getAllLevels()) {
				if (l.getEntity(e.getKey()) != null) {
					return false;
				}
			}
			e.getValue().forEach(m -> {
				if (m.isAlive()) {
					m.discard();
				}
			});
			return true;
		});
	}

	private static void huntTick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!sp.isAlive() || sp.isSpectator() || sp.isCreative() || sp.isSleeping() || sp.isPassenger() || sp.isUnderWater()
				|| HUNTERS.containsKey(sp.getUUID()) || NEXT_HUNT.getOrDefault(sp.getUUID(), 0L) > now || !(sp.level() instanceof ServerLevel level)) {
				continue;
			}
			ResourceKey<Level> realm = Stacked.realm(sp);
			if (realm != Level.OVERWORLD && realm != Level.NETHER && !realm.identifier().toString().equals("jaymospowers:aether")) {
				continue;
			}
			if (Factions.at(server, level, sp.blockPosition()) != null) {
				continue;
			}
			Patron p = null;
			for (Patron h : Patrons.hunters(sp)) {
				if (Turning.trial(sp, h) == 0.0F) {
					p = h;
					break;
				}
			}
			if (p != null && level.getRandom().nextFloat() < HUNT_CHANCE) {
				send(sp, p, level, false);
			}
		}
	}

	/** The champion's hunting form comes for {@code sp}. */
	static void send(ServerPlayer sp, Patron p, ServerLevel level, boolean anyway) {
		Vec3 spot = null;
		for (int i = 0; i < HUNT_TRIES; i++) {
			double a = level.getRandom().nextDouble() * Math.PI * 2.0;
			double d = HUNT_NEAR_MIN + level.getRandom().nextDouble() * (HUNT_NEAR_MAX - HUNT_NEAR_MIN);
			Vec3 s = MoveKit.standable(level, sp.getX() + Math.cos(a) * d, sp.getY(), sp.getZ() + Math.sin(a) * d);
			if (s == null || level.getBlockState(BlockPos.containing(s).above(HEADROOM)).isSolid()) {
				continue;
			}
			Vec3 to = s.subtract(sp.position()).normalize();
			if (spot == null) {
				spot = s;
			}
			if (sp.getLookAngle().dot(to) < 0.3) {
				spot = s;
				break;
			}
		}
		if (spot == null) {
			return;
		}
		PatronChampion champ = TYPES.get(p).create(level, EntitySpawnReason.EVENT);
		if (champ == null) {
			return;
		}
		champ.snapTo(spot.x, spot.y, spot.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		HUNTERS.put(sp.getUUID(), champ);
		level.addFreshEntity(champ);
		champ.hunt(sp);
		champ.setTarget(sp);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.4F, 0.7F);
		sp.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.ELDER_GUARDIAN_CURSE), SoundSource.HOSTILE, sp.getX(), sp.getEyeY(),
			sp.getZ(), 1.0F, 0.8F, sp.getRandom().nextLong()));
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.hunt", Patrons.coloured(p)));
		MoveKit.column(level, spot, 4.0, p.colour);
		champ.speak("hunt");
	}

	private static void watchHunters(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (var it = HUNTERS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			PatronChampion c = e.getValue();
			if (!c.isAlive() || c.isRemoved()) {
				it.remove();
				continue;
			}
			ServerLevel level = (ServerLevel)c.level();
			ServerPlayer q = server.getPlayerList().getPlayer(e.getKey());
			if (q == null || q.level() != level || c.distanceTo(q) > PatronChampion.HUNT_LOSE || c.tickCount > PatronChampion.HUNT_LIFE) {
				lose(level, c, e.getKey(), q, now);
				it.remove();
				continue;
			}
			if (!q.isAlive() || Downed.isDowned(q)) {
				long since = STANDING.computeIfAbsent(c.getUUID(), id -> now);
				if (now - since >= HUNT_STAND) {
					STANDING.remove(c.getUUID());
					c.speak("hunted");
					level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX(), c.getY() + 1.0, c.getZ(), 40, 0.5, 1.0, 0.5, 0.03);
					Patrons.hunted(q, c.patron());
					q.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.hunt.over", Patrons.coloured(c.patron())));
					c.discard();
					it.remove();
				}
			} else {
				STANDING.remove(c.getUUID());
			}
		}
	}

	private static void lose(ServerLevel level, PatronChampion c, UUID quarry, @Nullable ServerPlayer q, long now) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.getX(), c.getY() + 1.0, c.getZ(), 40, 0.5, 1.0, 0.5, 0.03);
		clearAdds(level, c);
		NEXT_HUNT.put(quarry, now + HUNT_RETRY);
		if (q != null) {
			q.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.patron_champion.hunt.lost", Patrons.coloured(c.patron())));
		}
		c.discard();
	}

	private static void hunterGone(ServerPlayer player) {
		PatronChampion c = HUNTERS.remove(player.getUUID());
		if (c != null && c.level() instanceof ServerLevel level) {
			lose(level, c, player.getUUID(), null, level.getServer().overworld().getGameTime());
		}
	}
}
