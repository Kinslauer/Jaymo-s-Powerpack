package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Passing for a goblin: a player in a {@link #MASK} (and nothing shinier than leather on the rest of them) walks into a
 * goblin village unchallenged. The goblins grow <b>suspicious</b> of one who lingers by their king or runs about among
 * them (a meter over the hotbar); full, or caught in the act (striking one of them, taking from their chests, breaking
 * their village, seen killing one), they see through it, and the whole village is after the player for
 * {@link #EXPOSED_TICKS}. A disguised player's blow at a goblin who isn't looking is a <b>backstab</b>
 * ({@link #BACKSTAB_MULTIPLIER} times the damage): a quiet kill nobody sees keeps the disguise, so a king can be
 * murdered on his throne.
 */
public final class Disguise {
	/** How long a village knows a player's face once it has seen through the mask (ticks). */
	static final long EXPOSED_TICKS = 20L * 60 * 5;
	/** Suspicion: full at this; what a half second near the king (within {@code KING_RANGE}) or running among them adds; what calm takes off. */
	static final float MAX_SUSPICION = 100.0F, KING_RISE = 6.0F, SPRINT_RISE = 4.0F, CALM_FALL = 2.0F;
	static final double KING_RANGE = 3.5, SPRINT_RANGE = 8.0;
	/** A backstab's damage, times the blow's. */
	static final float BACKSTAB_MULTIPLIER = 3.0F;
	/** How often players are looked at (ticks); the chance a goblin mutters when its suspicion grows. */
	static final int CHECK_TICKS = 10;
	static final float MUTTER_CHANCE = 0.15F;
	/** How far a cry carries, and how far the goblins who hear it come running. */
	static final double CRY_RANGE = 24.0, ALARM_RANGE = 40.0;
	/** Lines a goblin may say, per kind. */
	static final int MUTTERS = 4, GREETINGS = 4;

	static Item MASK;

	/** Player → camp id → the tick the village forgets them. */
	private static final Map<UUID, Map<Integer, Long>> EXPOSED = new HashMap<>();
	/** Player → how suspicious the village they're in is of them, and which village that is. */
	private static final Map<UUID, Float> SUSPICION = new HashMap<>();
	private static final Map<UUID, Integer> INSIDE = new HashMap<>();
	private static boolean backstabbing;

	private Disguise() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("goblin_mask"));
		Equippable worn = Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
			.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("goblin_mask"))).build();
		MASK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).component(DataComponents.EQUIPPABLE, worn)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.LEATHER_BOOTS, MASK));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Disguise::struck);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && level.getBlockEntity(hit.getBlockPos()) instanceof Container) {
				caught(sp, server, hit.getBlockPos(), "thief");
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				caught(sp, server, pos, "vandal");
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Disguise::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			EXPOSED.clear();
			SUSPICION.clear();
			INSIDE.clear();
		});
	}

	// ---- who passes ----------------------------------------------------------------------------------------------------

	/** In a goblin mask, with nothing but leather (or nothing) on the rest of them. */
	public static boolean wearsDisguise(LivingEntity who) {
		if (MASK == null || !who.getItemBySlot(EquipmentSlot.HEAD).is(MASK)) {
			return false;
		}
		return plain(who.getItemBySlot(EquipmentSlot.CHEST), Items.LEATHER_CHESTPLATE) && plain(who.getItemBySlot(EquipmentSlot.LEGS), Items.LEATHER_LEGGINGS)
			&& plain(who.getItemBySlot(EquipmentSlot.FEET), Items.LEATHER_BOOTS);
	}

	private static boolean plain(ItemStack worn, Item leather) {
		return worn.isEmpty() || worn.is(leather);
	}

	/** {@code player} passes for a goblin among {@code camp}'s: disguised, and not seen through there lately. */
	public static boolean fooled(ServerPlayer player, int camp) {
		return wearsDisguise(player) && !exposed(player, camp);
	}

	static boolean exposed(ServerPlayer player, int camp) {
		Map<Integer, Long> seen = EXPOSED.get(player.getUUID());
		Long until = seen == null ? null : seen.get(camp);
		return until != null && until > player.level().getGameTime();
	}

	/** The standing goblin village whose ground {@code pos} is on (inside its ring, give or take), or null. */
	static @Nullable Faction villageAt(MinecraftServer server, BlockPos pos) {
		double reach = GoblinBuildings.RING_RADIUS + 4.0;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (!f.fallen() && Raids.horizontal(f.seat(), pos) <= reach * reach && Math.abs(f.seat().getY() - pos.getY()) < 24
				&& GoblinVillages.is(server, f.id())) {
				return f;
			}
		}
		return null;
	}

	// ---- caught in the act ---------------------------------------------------------------------------------------------

	/** A disguised player struck a goblin: a backstab if it wasn't looking; then, if it lived or anyone saw, the mask is seen through. */
	private static void struck(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (backstabbing || !(source.getEntity() instanceof ServerPlayer player) || !(victim instanceof Folk goblin) || !Goblins.isGoblin(goblin)
			|| !(victim.level() instanceof ServerLevel level) || !fooled(player, goblin.factionId())) {
			return;
		}
		int camp = goblin.factionId();
		if (source.getDirectEntity() == player && goblin.getTarget() != player && goblin.isAlive() && damage > 0.0F) {
			// it never saw it coming
			backstabbing = true;
			try {
				goblin.setInvulnerableTime(0);
				goblin.hurtServer(level, source, damage * (BACKSTAB_MULTIPLIER - 1.0F));
			} finally {
				backstabbing = false;
			}
			level.sendParticles(ParticleTypes.CRIT, goblin.getX(), goblin.getY(0.6), goblin.getZ(), 12, 0.25, 0.3, 0.25, 0.2);
			level.playSound(null, goblin.getX(), goblin.getY(), goblin.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.goblin.backstab").withStyle(ChatFormatting.DARK_GREEN));
		}
		Faction village = Factions.get(level.getServer(), camp);
		if (village == null || village.fallen()) {
			return;
		}
		if (goblin.isAlive()) {
			expose(level, player, village, goblin, "hit");
			return;
		}
		List<LivingEntity> seen = Theft.witnesses(level, village, player);
		seen.remove(goblin);
		if (!seen.isEmpty()) {
			expose(level, player, village, seen.getFirst(), "murder");
		}
	}

	/** A disguised player opened a chest or broke a block in a goblin village: if a goblin saw it, the mask is seen through. */
	private static void caught(ServerPlayer player, ServerLevel level, BlockPos pos, String crime) {
		if (player.isCreative() || player.isSpectator() || level != Factions.level(level.getServer()) || !wearsDisguise(player)) {
			return;
		}
		Faction village = villageAt(level.getServer(), pos);
		if (village == null || exposed(player, village.id())) {
			return;
		}
		List<LivingEntity> seen = Theft.witnesses(level, village, player);
		if (!seen.isEmpty()) {
			expose(level, player, village, seen.getFirst(), crime);
		}
	}

	/** {@code village} sees through {@code player}'s mask: {@code crier} raises the cry, and every goblin in earshot comes for them. */
	static void expose(ServerLevel level, ServerPlayer player, Faction village, @Nullable LivingEntity crier, String why) {
		EXPOSED.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(village.id(), level.getGameTime() + EXPOSED_TICKS);
		SUSPICION.remove(player.getUUID());
		if (crier != null) {
			Speech.say(level, crier, Component.translatable("message.jaymoskingdoms.goblin.cry." + why), Speech.RED, CRY_RANGE);
			level.playSound(null, crier.getX(), crier.getY(), crier.getZ(), SoundEvents.PIGLIN_ANGRY, SoundSource.HOSTILE, 1.5F, 1.6F);
		}
		for (Folk goblin : level.getEntitiesOfClass(Folk.class, player.getBoundingBox().inflate(ALARM_RANGE),
			g -> g.factionId() == village.id() && Goblins.isGoblin(g) && g.isAlive())) {
			goblin.setTarget(player);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.goblin.exposed", village.name()).withStyle(ChatFormatting.RED));
		JaymosKingdoms.LOGGER.debug("{} saw through {}'s disguise ({})", village.name(), player.getName().getString(), why);
	}

	// ---- suspicion -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			Faction village = player.isCreative() || player.isSpectator() ? null : villageAt(server, player.blockPosition());
			Integer was = INSIDE.get(id);
			if (village == null || !wearsDisguise(player) || exposed(player, village.id())) {
				if (village == null) {
					INSIDE.remove(id);
				}
				calm(player, SUSPICION.containsKey(id) && village == null);
				continue;
			}
			if (was == null || was != village.id()) {
				INSIDE.put(id, village.id());
				SUSPICION.remove(id);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.goblin.welcome", village.name()).withStyle(ChatFormatting.DARK_GREEN));
			}
			float rise = 0.0F;
			LivingEntity near = null;
			Entity king = village.leader().map(level::getEntity).orElse(null);
			if (king instanceof LivingEntity k && k.isAlive() && k.distanceTo(player) <= KING_RANGE && k.hasLineOfSight(player)) {
				rise += KING_RISE;
				near = k;
			}
			if (player.isSprinting()) {
				List<LivingEntity> seen = Theft.witnesses(level, village, player);
				seen.removeIf(e -> e.distanceTo(player) > SPRINT_RANGE);
				if (!seen.isEmpty()) {
					rise += SPRINT_RISE;
					near = near == null ? seen.getFirst() : near;
				}
			}
			float now = SUSPICION.getOrDefault(id, 0.0F);
			if (rise <= 0.0F) {
				calm(player, now > 0.0F);
				continue;
			}
			now = Math.min(MAX_SUSPICION, now + rise);
			if (now >= MAX_SUSPICION) {
				expose(level, player, village, near, "spy");
				continue;
			}
			SUSPICION.put(id, now);
			show(player, now);
			if (near != null && level.getRandom().nextFloat() < MUTTER_CHANCE) {
				Speech.say(level, near, Component.translatable("message.jaymoskingdoms.goblin.mutter." + level.getRandom().nextInt(MUTTERS)), Speech.YELLOW, 12.0);
			}
		}
		EXPOSED.values().forEach(m -> m.values().removeIf(until -> until <= level.getGameTime()));
		EXPOSED.values().removeIf(Map::isEmpty);
	}

	/** Suspicion cools (and the meter goes once it's gone). */
	private static void calm(ServerPlayer player, boolean showing) {
		UUID id = player.getUUID();
		Float was = SUSPICION.get(id);
		if (was == null) {
			return;
		}
		float now = was - CALM_FALL;
		if (now <= 0.0F) {
			SUSPICION.remove(id);
			if (showing) {
				player.sendOverlayMessage(Component.empty());
			}
			return;
		}
		SUSPICION.put(id, now);
		show(player, now);
	}

	/** The meter over the hotbar: ten pips, yellow to red. */
	private static void show(ServerPlayer player, float suspicion) {
		int pips = Math.round(suspicion / MAX_SUSPICION * 10.0F);
		MutableComponent bar = Component.translatable("message.jaymoskingdoms.goblin.suspicion").withStyle(ChatFormatting.GOLD).append(" ");
		bar.append(Component.literal("■".repeat(pips)).withStyle(pips >= 7 ? ChatFormatting.RED : ChatFormatting.YELLOW));
		bar.append(Component.literal("■".repeat(10 - pips)).withStyle(ChatFormatting.DARK_GRAY));
		player.sendOverlayMessage(bar);
	}

	/** A disguised player talks to a goblin (right click): it grunts something back. */
	static void greet(ServerLevel level, ServerPlayer player, Folk goblin) {
		if (fooled(player, goblin.factionId()) && goblin.getTarget() == null) {
			Speech.say(level, goblin, Component.translatable("message.jaymoskingdoms.goblin.greet." + level.getRandom().nextInt(GREETINGS)), Speech.WHITE, 12.0);
			level.playSound(null, goblin.getX(), goblin.getY(), goblin.getZ(), SoundEvents.PIGLIN_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.7F);
		}
	}

	/** Its seat's goblins within {@code range} of {@code pos}, for the village's own business. */
	static List<Folk> goblinsNear(ServerLevel level, int camp, BlockPos pos, double range) {
		return level.getEntitiesOfClass(Folk.class, new AABB(pos).inflate(range), g -> g.factionId() == camp && Goblins.isGoblin(g) && g.isAlive());
	}
}
