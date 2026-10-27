package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The seven wish orbs and the Eternal Dragon (our own: a serpent of green light coiling up into the night sky).
 * <ul>
 * <li>Each orb lies buried in its own shrine ({@link ZFighterSites}); the Wish Radar (sold by the Tower's Hermit) points
 * to the nearest orb still in the world, or (sneaking) to the Tower.</li>
 * <li>All seven in your pack, at night in the overworld: use one and the dragon rises. It grants one wish from its
 * menu, then the orbs scatter to seven new shrines and turn to stone for three days.</li>
 * </ul>
 * Wishes: power (5000 TP for a Z Fighter; the Z Fighter power for someone with none; else 30 levels), revive (everyone
 * who died in the last hour comes back to your side with what they dropped), riches, awakening (the way to Super opens),
 * rebirth (the lineage goes back to what your heritage gives), a blessing on everyone and (0.17.53) a pair of bonding earrings.
 * (0.17.60) Five more, in {@link ZFighterWishExtras}: re-make character, reset racial skill, move stat points, change difficulty, and undo
 * the android rebuild.
 */
public final class ZFighterWishes {
	public static final int POWER = 0;
	public static final int REVIVE = 1;
	public static final int RICHES = 2;
	public static final int AWAKEN = 3;
	public static final int REBIRTH = 4;
	public static final int BLESSING = 5;
	/** (0.17.53) A pair of bonding earrings ({@link ZFighterFusionItems}). */
	public static final int BONDING = 6;
	/** (0.17.60) Re-make character, reset racial skill, move stat points, change difficulty, undo the android rebuild ({@link ZFighterWishExtras}). */
	public static final int REMAKE = 7;
	public static final int RACIAL = 8;
	public static final int RESTAT = 9;
	public static final int DIFFICULTY = 10;
	public static final int UNDO_ANDROID = 11;
	public static final int WISHES = 12;
	public static final String[] KEYS = {"power", "revive", "riches", "awaken", "rebirth", "blessing", "bonding", "remake", "racial", "restat", "difficulty",
		"undo_android"};

	static final int WISH_TP = 1250;
	static final int WISH_LEVELS = 30;
	static final int RICHES_STACKS = 3;
	/** Deaths this recent (ticks) are undone by the revive wish. */
	static final long REVIVE_WINDOW = 72000L;
	static final double REVIVE_GATHER = 12.0;
	static final int BLESSING_TICKS = 24000;
	/** After a wish the orbs are stone this long (three days). */
	static final long REST = 72000L;
	/** How long the dragon waits for a wish. */
	static final int SUMMON_TICKS = 6000;
	static final int DRAGON_COLOR = 0x3CC85A;
	static final int SCALE_COLOR = 0xE8C040;
	static final int EYE_COLOR = 0xFF2020;

	public static final Item[] ORB = new Item[ZFighterSites.ORBS];
	public static Item RADAR;

	private record Summon(ServerLevel level, Vec3 at, long until) {
	}

	private record Death(ResourceKey<Level> dimension, Vec3 at, long time) {
	}

	private static final Map<UUID, Summon> SUMMONS = new HashMap<>();
	private static final Map<UUID, Death> DEATHS = new HashMap<>();

	private ZFighterWishes() {
	}

	static void init() {
		for (int i = 0; i < ZFighterSites.ORBS; i++) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("wish_orb_" + (i + 1)));
			ORB[i] = Registry.register(BuiltInRegistries.ITEM, key, new Orb(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)
				.fireResistant()));
		}
		ResourceKey<Item> radarKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("wish_radar"));
		RADAR = Registry.register(BuiltInRegistries.ITEM, radarKey, new Radar(new Item.Properties().setId(radarKey).stacksTo(1).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(RADAR);
			for (Item orb : ORB) {
				output.accept(orb);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ZFighterWishes::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				DEATHS.put(player.getUUID(), new Death(player.level().dimension(), player.position(), player.level().getServer().overworld()
					.getGameTime()));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SUMMONS.clear();
			DEATHS.clear();
		});
	}

	public static boolean isOrb(ItemStack stack) {
		for (Item orb : ORB) {
			if (stack.is(orb)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the radar

	static final class Radar extends Item {
		Radar(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer server) {
				ping(server, player.isShiftKeyDown(), true);
				player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
			}
			return InteractionResult.SUCCESS;
		}
	}

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	/** Points the way: to the nearest orb still in the world, or (sneaking) to the Tower. */
	static void ping(ServerPlayer player, boolean tower, boolean loud) {
		MinecraftServer server = player.level().getServer();
		ServerLevel overworld = server.overworld();
		if (player.level() != overworld) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_radar_dead").withStyle(ChatFormatting.GRAY));
			return;
		}
		ZFighterSites.Data data = ZFighterSites.data(server);
		Vec3 target = null;
		String key;
		if (tower) {
			BlockPos at = data.tower().orElseThrow();
			target = Vec3.atBottomCenterOf(at);
			key = "message.jaymospowers.zfighter_radar_tower";
		} else {
			long now = overworld.getGameTime();
			if (now < data.restUntil()) {
				long days = (data.restUntil() - now + 23999L) / 24000L;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_radar_stone", days).withStyle(ChatFormatting.GRAY));
				return;
			}
			double best = Double.MAX_VALUE;
			int left = 0;
			for (int i = 0; i < ZFighterSites.ORBS; i++) {
				if (!ZFighterSites.holdsOrb(overworld, data, i)) {
					continue;
				}
				left++;
				Vec3 at = Vec3.atBottomCenterOf(data.shrines().get(i));
				double d = horizontal(player.position(), at);
				if (d < best) {
					best = d;
					target = at;
				}
			}
			// orbs others are carrying show up too
			for (ServerPlayer other : overworld.players()) {
				if (other == player || !carries(other)) {
					continue;
				}
				double d = horizontal(player.position(), other.position());
				if (d < best) {
					best = d;
					target = other.position();
				}
			}
			if (target == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_radar_none").withStyle(ChatFormatting.GRAY));
				return;
			}
			key = left == 1 ? "message.jaymospowers.zfighter_radar_one" : "message.jaymospowers.zfighter_radar";
		}
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		int distance = (int)Math.sqrt(dx * dx + dz * dz);
		float yaw = (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		int arrow = Math.floorMod(Math.round(Mth.wrapDegrees(yaw - player.getYRot()) / 45.0F), 8);
		player.sendOverlayMessage(Component.translatable(key, ARROWS[arrow], distance).withStyle(ChatFormatting.GOLD));
		if (loud) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6F,
				distance < 64 ? 2.0F : distance < 256 ? 1.4F : 0.9F);
		}
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return dx * dx + dz * dz;
	}

	private static boolean carries(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (isOrb(player.getInventory().getItem(i))) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the summoning

	static final class Orb extends Item {
		Orb(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer server) {
				summon(server);
			}
			return InteractionResult.SUCCESS;
		}
	}

	private static void summon(ServerPlayer player) {
		ServerLevel level = player.level();
		if (SUMMONS.containsKey(player.getUUID())) {
			ServerPlayNetworking.send(player, new ZFighterNet.OpenWish());
			return;
		}
		int[] slots = new int[ZFighterSites.ORBS];
		for (int i = 0; i < ZFighterSites.ORBS; i++) {
			slots[i] = -1;
			for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
				if (player.getInventory().getItem(s).is(ORB[i])) {
					slots[i] = s;
					break;
				}
			}
			if (slots[i] < 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_orbs_missing").withStyle(ChatFormatting.GRAY));
				return;
			}
		}
		if (level != level.getServer().overworld() || !level.isDarkOutside()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_orbs_night").withStyle(ChatFormatting.GRAY));
			return;
		}
		for (int slot : slots) {
			player.getInventory().getItem(slot).shrink(1);
		}
		Vec3 at = player.position().add(player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(4.0));
		SUMMONS.put(player.getUUID(), new Summon(level, at, level.getGameTime() + SUMMON_TICKS));
		for (int i = 0; i < 3; i++) {
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(at.x + (i - 1) * 6.0, at.y, at.z + (i - 1) * 3.0);
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
		}
		for (ServerPlayer near : level.players()) {
			if (near.position().distanceToSqr(at) < 96.0 * 96.0) {
				near.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
				near.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_dragon", player.getDisplayName())
					.withStyle(ChatFormatting.GREEN));
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 4.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.8F);
		com.jaymospowers.core.Shakes.around(level, at, 96.0, 2.5F);
		ServerPlayNetworking.send(player, new ZFighterNet.OpenWish());
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getMainHandItem().is(RADAR) || player.getOffhandItem().is(RADAR)) {
					ping(player, player.isShiftKeyDown(), false);
				}
			}
		}
		for (Iterator<Map.Entry<UUID, Summon>> it = SUMMONS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Summon> entry = it.next();
			Summon summon = entry.getValue();
			if (now >= summon.until() || server.getPlayerList().getPlayer(entry.getKey()) == null) {
				// it waited long enough: the orbs scatter all the same
				it.remove();
				depart(server, summon);
				continue;
			}
			if (now % 2 == 0) {
				draw(summon, now);
			}
		}
		DEATHS.values().removeIf(death -> now - death.time() > REVIVE_WINDOW);
	}

	/** The dragon: a long body coiling up into the sky, gold along its back, a head with red eyes looking down. */
	private static void draw(Summon summon, long now) {
		ServerLevel level = summon.level();
		DustParticleOptions body = new DustParticleOptions(DRAGON_COLOR, 3.0F);
		DustParticleOptions scales = new DustParticleOptions(SCALE_COLOR, 2.0F);
		int points = 70;
		Vec3 head = summon.at();
		for (int i = 0; i <= points; i++) {
			double t = i / (double)points;
			double angle = t * Math.PI * 5.0 + now * 0.03;
			double r = 7.0 * (1.0 - 0.55 * t);
			Vec3 p = summon.at().add(Math.cos(angle) * r, 2.0 + t * 26.0, Math.sin(angle) * r);
			level.sendParticles(body, true, false, p.x, p.y, p.z, 1, 0.15, 0.15, 0.15, 0.0);
			if (i % 4 == 0) {
				level.sendParticles(scales, true, false, p.x, p.y + 0.5, p.z, 1, 0.1, 0.1, 0.1, 0.0);
			}
			head = p;
		}
		level.sendParticles(body, true, false, head.x, head.y, head.z, 8, 0.8, 0.5, 0.8, 0.0);
		DustParticleOptions eye = new DustParticleOptions(EYE_COLOR, 1.5F);
		level.sendParticles(eye, true, false, head.x - 0.5, head.y + 0.3, head.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(eye, true, false, head.x + 0.5, head.y + 0.3, head.z, 1, 0.0, 0.0, 0.0, 0.0);
		if (now % 60 == 0) {
			level.playSound(null, head.x, head.y, head.z, SoundEvents.ENDER_DRAGON_AMBIENT, SoundSource.PLAYERS, 3.0F, 0.5F);
		}
	}

	/** The wish is spoken (or the dragon tires of waiting): it goes, and the orbs fly to seven new shrines. */
	private static void depart(MinecraftServer server, Summon summon) {
		ServerLevel level = summon.level();
		Vec3 at = summon.at();
		level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y + 10.0, at.z, 120, 6.0, 10.0, 6.0, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 4.0F, 0.6F);
		ZFighterSites.Data data = ZFighterSites.data(server);
		ZFighterSites.save(server, new ZFighterSites.Data(ZFighterSites.scatter(level.getRandom()), 0, server.overworld().getGameTime() + REST,
			data.tower(), data.towerBuilt()));
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_orbs_scatter")
			.withStyle(ChatFormatting.GREEN), false);
	}

	// ---------------------------------------------------------------- the wishes

	/** From the wish screen. A wish that can't be granted is refused and the dragon waits for another. */
	static void wish(ServerPlayer player, int wish) {
		Summon summon = SUMMONS.get(player.getUUID());
		if (summon == null || wish < 0 || wish >= WISHES) {
			return;
		}
		boolean zfighter = Powers.has(player, ZFighterPower.INSTANCE);
		boolean granted = switch (wish) {
			case POWER -> power(player, zfighter);
			case REVIVE -> revive(player);
			case RICHES -> {
				for (int i = 0; i < RICHES_STACKS; i++) {
					give(player, new ItemStack(Items.EMERALD, 64));
				}
				yield true;
			}
			case AWAKEN -> awaken(player, zfighter);
			case REBIRTH -> rebirth(player, zfighter);
			case BLESSING -> {
				for (ServerPlayer everyone : player.level().getServer().getPlayerList().getPlayers()) {
					everyone.setHealth(everyone.getMaxHealth());
					everyone.getFoodData().setFoodLevel(20);
					everyone.addEffect(new MobEffectInstance(MobEffects.LUCK, BLESSING_TICKS, 0));
					everyone.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
				}
				yield true;
			}
			case BONDING -> {
				give(player, new ItemStack(ZFighterFusionItems.BONDING_EARRING, 2));
				yield true;
			}
			case REMAKE -> ZFighterWishExtras.remake(player, zfighter);
			case RACIAL -> ZFighterWishExtras.racial(player, zfighter);
			case RESTAT -> ZFighterWishExtras.restat(player, zfighter);
			case DIFFICULTY -> ZFighterWishExtras.difficulty(player);
			case UNDO_ANDROID -> ZFighterWishExtras.android(player, zfighter);
			default -> false;
		};
		if (!granted) {
			ServerPlayNetworking.send(player, new ZFighterNet.OpenWish());
			return;
		}
		SUMMONS.remove(player.getUUID());
		player.level().getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.jaymospowers.zfighter_wished",
			player.getDisplayName(), Component.translatable("wish.jaymospowers.zfighter." + KEYS[wish])).withStyle(ChatFormatting.GREEN), false);
		depart(player.level().getServer(), summon);
	}

	private static boolean power(ServerPlayer player, boolean zfighter) {
		if (zfighter) {
			ZFighterTraining.grant(player, WISH_TP);
		} else if (Powers.of(player) == null) {
			Powers.set(player, ZFighterPower.INSTANCE);
		} else {
			player.giveExperienceLevels(WISH_LEVELS);
		}
		return true;
	}

	private static boolean revive(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		List<UUID> fallen = new ArrayList<>(DEATHS.keySet());
		boolean any = false;
		for (UUID id : fallen) {
			ServerPlayer friend = server.getPlayerList().getPlayer(id);
			if (friend == null || friend == player || !friend.isAlive()) {
				continue;
			}
			Death death = DEATHS.remove(id);
			ServerLevel where = server.getLevel(death.dimension());
			if (where != null) {
				where.getChunk(BlockPos.containing(death.at()));
				for (ItemEntity dropped : where.getEntitiesOfClass(ItemEntity.class, new AABB(death.at(), death.at()).inflate(REVIVE_GATHER))) {
					give(friend, dropped.getItem().copy());
					dropped.discard();
				}
			}
			friend.teleportTo(player.level(), player.getX() + 1.0, player.getY(), player.getZ(), Set.of(), friend.getYRot(), friend.getXRot(), true);
			friend.setHealth(friend.getMaxHealth());
			friend.getFoodData().setFoodLevel(20);
			friend.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_revived", player.getDisplayName())
				.withStyle(ChatFormatting.GREEN));
			any = true;
		}
		if (!any) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wish_no_fallen").withStyle(ChatFormatting.GRAY));
		}
		return any;
	}

	private static boolean awaken(ServerPlayer player, boolean zfighter) {
		if (!zfighter || ZFighterTraining.formsOpen(player) > 0) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wish_cannot").withStyle(ChatFormatting.GRAY));
			return false;
		}
		player.setAttached(ZFighterPower.HARDSHIP, Math.max(ZFighterPower.hardship(player), ZFighterBattle.SUPER_HARDSHIP));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wish_awakened").withStyle(ChatFormatting.GOLD));
		return true;
	}

	private static boolean rebirth(ServerPlayer player, boolean zfighter) {
		if (!zfighter || ZFighterLineage.of(player) == ZFighterLineage.NONE) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wish_cannot").withStyle(ChatFormatting.GRAY));
			return false;
		}
		return ZFighterLineage.reborn(player);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}
}
