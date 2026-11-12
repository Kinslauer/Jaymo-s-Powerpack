package com.jaymoskingdoms;

import com.jaymoskingdoms.LifeStory.Start;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Starts that keep giving (fifth edition's background features). Every start leaves one lasting social perk: a {@link Circle} of
 * folk who owe you something, and what they give ({@link Boon}s). Some are passive: the Underworld trusts a Banished or
 * hideout-bred player ({@link #trusted}: it reads chalk marks and is sold forged papers), merchants shave a city-born player's
 * prices ({@link #prices}), a sworn soldier's old comrades wave them through ({@link #salutes}). The rest are favours asked of
 * one of the circle on the Interact page ("Ask a Favour"): food and a hiding place from farmers, passage from sailors, news
 * from beggars, a bed from priests, a lesson from scholars. Each villager gives a favour once a day.
 */
public final class StartPerks {
	/** Who owes you. */
	public enum Circle {
		FARMERS, MERCHANTS, SAILORS, UNDERWORLD, BEGGARS, GUARDS, PRIESTS, SCHOLARS, SMITHS, HUNTERS, HERMITS, GREENSKINS
	}

	/** What they give. */
	public enum Boon {
		FEED, HIDE, PASSAGE, TRUST, NEWS, SHELTER, DISCOUNT, SALUTE, LORE
	}

	public record Perk(Circle circle, List<Boon> boons) {
		static Perk of(Circle circle, Boon... boons) {
			return new Perk(circle, List.of(boons));
		}
	}

	/** A merchant's prices come down this much for the city-born (and the like). */
	static final float DISCOUNT = 0.15F;
	/** A hiding place: how long the player is out of the watch's sight (ticks). */
	static final int HIDE_TICKS = 20 * 90;
	/** Passage: the nearest other realm's seat that is at least this far (blocks) and at most the other. */
	static final double PASSAGE_MIN = 150.0, PASSAGE_MAX = 4000.0;
	/** A shelter: hearts it mends is the whole bar; effects it clears are the bad ones. */
	static final int FOOD_COUNT = 2;
	/** The reputation a guard needs toward you to wave you through. */
	static final int SALUTE_REP = 0;

	/** The start a player landed on (name). Saved, kept through death, synced to its own player. */
	public static final AttachmentType<String> LANDED = AttachmentRegistry.create(JaymosKingdoms.id("perk_start"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

	private static final Map<Start, Perk> PERKS = new EnumMap<>(Start.class);
	/** Villager+player → the day a favour was last given. */
	private static final Map<String, Long> ASKED = new HashMap<>();

	static {
		PERKS.put(Start.VILLAGE, Perk.of(Circle.FARMERS, Boon.FEED, Boon.HIDE));
		PERKS.put(Start.CITY, Perk.of(Circle.MERCHANTS, Boon.DISCOUNT));
		PERKS.put(Start.BANISHED, Perk.of(Circle.UNDERWORLD, Boon.TRUST));
		PERKS.put(Start.CAVE, Perk.of(Circle.SMITHS, Boon.NEWS));
		PERKS.put(Start.SHIPWRECK, Perk.of(Circle.SAILORS, Boon.PASSAGE));
		PERKS.put(Start.SWORN, Perk.of(Circle.GUARDS, Boon.SALUTE));
		PERKS.put(Start.MONOLITH, Perk.of(Circle.SCHOLARS, Boon.LORE));
		PERKS.put(Start.WILD, Perk.of(Circle.HUNTERS, Boon.FEED));
		PERKS.put(Start.SKYFALL, Perk.of(Circle.HERMITS, Boon.SHELTER));
		PERKS.put(Start.TIMELOST, Perk.of(Circle.SCHOLARS, Boon.NEWS));
		PERKS.put(Start.BURNED, Perk.of(Circle.FARMERS, Boon.SHELTER));
		PERKS.put(Start.SNOWED, Perk.of(Circle.HUNTERS, Boon.SHELTER));
		PERKS.put(Start.CAPTIVE, Perk.of(Circle.BEGGARS, Boon.HIDE));
		PERKS.put(Start.WHALERS, Perk.of(Circle.SAILORS, Boon.FEED));
		PERKS.put(Start.UNARMED, Perk.of(Circle.PRIESTS, Boon.SHELTER));
		PERKS.put(Start.CHAINED, Perk.of(Circle.GREENSKINS, Boon.HIDE));
		PERKS.put(Start.ROCK_BOTTOM, Perk.of(Circle.BEGGARS, Boon.NEWS));
		PERKS.put(Start.SENTENCED, Perk.of(Circle.UNDERWORLD, Boon.HIDE));
		PERKS.put(Start.EVICTED, Perk.of(Circle.BEGGARS, Boon.FEED));
		PERKS.put(Start.HIDEOUT, Perk.of(Circle.UNDERWORLD, Boon.TRUST, Boon.HIDE));
		PERKS.put(Start.CAST_OUT, Perk.of(Circle.HERMITS, Boon.FEED));
		PERKS.put(Start.GREENSKIN, Perk.of(Circle.GREENSKINS, Boon.FEED));
		PERKS.put(Start.HEIRLOOM, Perk.of(Circle.SMITHS, Boon.DISCOUNT));
		PERKS.put(Start.TRADER, Perk.of(Circle.MERCHANTS, Boon.NEWS));
		PERKS.put(Start.PUP, Perk.of(Circle.HUNTERS, Boon.NEWS));
		PERKS.put(Start.NOBODIES, Perk.of(Circle.BEGGARS, Boon.SHELTER));
		PERKS.put(Start.LAB, Perk.of(Circle.SCHOLARS, Boon.LORE, Boon.NEWS));
		PERKS.put(Start.ANDROID, Perk.of(Circle.SMITHS, Boon.FEED));
		PERKS.put(Start.MOON_CURSED, Perk.of(Circle.HERMITS, Boon.HIDE));
		PERKS.put(Start.MOON, Perk.of(Circle.SCHOLARS, Boon.SHELTER));
		PERKS.put(Start.VERDANT, Perk.of(Circle.HERMITS, Boon.NEWS));
		PERKS.put(Start.RUSTFALL, Perk.of(Circle.SMITHS, Boon.SHELTER));
		PERKS.put(Start.MAW, Perk.of(Circle.UNDERWORLD, Boon.NEWS));
		PERKS.put(Start.DEMON, Perk.of(Circle.UNDERWORLD, Boon.DISCOUNT));
		PERKS.put(Start.AFTERLIFE, Perk.of(Circle.PRIESTS, Boon.LORE));
	}

	private StartPerks() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ASKED.clear());
	}

	/** (From {@link LifeStory}) {@code start} is where {@code player} really landed (fate has been settled). */
	static void landed(ServerPlayer player, Start start) {
		player.setAttached(LANDED, start.name());
	}

	/** The start {@code player} landed on; a character from before this was kept falls back on their story's road. */
	public static @Nullable Start start(Player player) {
		String name = player.getAttached(LANDED);
		if (name != null) {
			try {
				return Start.valueOf(name);
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		LifeStory.Answer road = LifeStory.answer(player, LifeStory.Question.ROAD);
		return road == null || road.start() == Start.FATE ? null : road.start();
	}

	/** {@code player}'s lasting perk, or null. */
	public static @Nullable Perk of(Player player) {
		Start start = start(player);
		return start == null ? null : PERKS.get(start);
	}

	private static boolean has(Player player, Boon boon) {
		Perk perk = of(player);
		return perk != null && perk.boons().contains(boon);
	}

	/** The Underworld trusts {@code player}: it reads their chalk marks, and its crooks sell to them. */
	public static boolean trusted(Player player) {
		return has(player, Boon.TRUST);
	}

	/** Old comrades: a guard who thinks no ill of {@code player} waves them through. */
	static boolean salutes(Player player, int realm) {
		return has(player, Boon.SALUTE) && player instanceof ServerPlayer sp && Reputation.get(sp, realm) >= SALUTE_REP;
	}

	/** A merchant of the circle charges {@code player} less. */
	public static void prices(Villager villager, Player player) {
		Perk perk = of(player);
		if (perk == null || !perk.boons().contains(Boon.DISCOUNT) || !belongs(villager, perk.circle())) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			offer.addToSpecialPriceDiff(-Math.max(1, Math.round(offer.getBaseCostA().getCount() * DISCOUNT)));
		}
	}

	/** Whether {@code v} is one of {@code circle}. */
	static boolean belongs(net.minecraft.world.entity.Entity v, Circle circle) {
		if (v instanceof net.minecraft.world.entity.LivingEntity l && l.isBaby()) {
			return false;
		}
		String job = v instanceof Villager villager
			? BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().profession().value()).getPath() : "";
		return switch (circle) {
			case FARMERS -> job.equals("farmer") || FarmFamily.is(v);
			case MERCHANTS -> Pedlars.pedlar(v) || v instanceof WanderingTrader || List.of("cartographer", "shepherd", "leatherworker", "butcher", "fisherman").contains(job);
			case SAILORS -> Crewmen.tagged(v);
			case UNDERWORLD -> v.entityTags().contains(Underworld.FENCE_TAG) || v.entityTags().contains(Underworld.DICER_TAG)
				|| v.entityTags().contains(Bookies.TAG) || Crooks.isCrook(v);
			case BEGGARS -> Drifters.is(v);
			case GUARDS -> v instanceof net.minecraft.world.entity.LivingEntity l && Downed.isGuard(l);
			case PRIESTS -> Accusations.priest(v);
			case SCHOLARS -> CourtWizards.is(v) || job.equals("librarian");
			case SMITHS -> List.of("armorer", "weaponsmith", "toolsmith", "mason").contains(job);
			case HUNTERS -> job.equals("fletcher") || job.equals("butcher") || job.equals("fisherman");
			case HERMITS -> Hermits.is(v) || job.equals("nitwit");
			case GREENSKINS -> Dialects.of(v) != null;
		};
	}

	/** Whether {@code v} would give {@code player} a favour now (they are of the circle and have not today). */
	static boolean offers(ServerPlayer player, net.minecraft.world.entity.LivingEntity v) {
		Perk perk = of(player);
		if (perk == null || !(v instanceof Mob) || !belongs(v, perk.circle()) || perk.boons().stream().noneMatch(StartPerks::asked)) {
			return false;
		}
		Long day = ASKED.get(v.getUUID() + "/" + player.getUUID());
		return day == null || day < v.level().getGameTime() / 24000L;
	}

	private static boolean asked(Boon boon) {
		return boon != Boon.TRUST && boon != Boon.DISCOUNT && boon != Boon.SALUTE;
	}

	/** {@code player} asks {@code v} the favour their start earned: each boon the perk has that is asked for. */
	static void ask(ServerPlayer player, net.minecraft.world.entity.LivingEntity v) {
		Perk perk = of(player);
		if (perk == null || !offers(player, v) || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		ASKED.put(v.getUUID() + "/" + player.getUUID(), level.getGameTime() / 24000L);
		String circle = perk.circle().name().toLowerCase(java.util.Locale.ROOT);
		int line = level.getRandom().nextInt(3);
		for (Boon boon : perk.boons()) {
			if (asked(boon)) {
				give(level, player, v, boon, circle, line);
			}
		}
		Recollections.socialised(v, player, "favour", 1);
	}

	private static void give(ServerLevel level, ServerPlayer player, net.minecraft.world.entity.LivingEntity v, Boon boon, String circle, int line) {
		String key = "perk.jaymoskingdoms." + boon.name().toLowerCase(java.util.Locale.ROOT);
		switch (boon) {
			case FEED -> {
				ItemStack food = new ItemStack(circle.equals("sailors") ? Items.COOKED_COD : circle.equals("hunters") ? Items.COOKED_PORKCHOP
					: circle.equals("farmers") ? Items.BAKED_POTATO : Items.BREAD, FOOD_COUNT);
				if (!player.getInventory().add(food)) {
					player.spawnAtLocation(level, food);
				}
				player.getFoodData().eat(6, 0.6F);
				level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
				Speech.tell(level, v, player, Component.translatable(key + "." + line), Speech.GOLD);
			}
			case HIDE -> {
				Faction f = Theft.lawAt(level, v.blockPosition());
				if (f != null) {
					Theft.clearWanted(player, f.id());
				}
				player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, HIDE_TICKS, 0, false, false, true));
				for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(Theft.PURSUIT_RANGE), m -> m.getTarget() == player)) {
					m.setTarget(null);
				}
				level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 0.8F);
				Speech.tell(level, v, player, Component.translatable(key + "." + line), Speech.WHITE);
			}
			case SHELTER -> {
				player.setHealth(player.getMaxHealth());
				player.removeEffect(MobEffects.POISON);
				player.removeEffect(MobEffects.WEAKNESS);
				player.removeEffect(MobEffects.NAUSEA);
				player.removeEffect(MobEffects.HUNGER);
				player.getFoodData().eat(4, 0.4F);
				level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.NEUTRAL, 0.6F, 1.4F);
				Speech.tell(level, v, player, Component.translatable(key + "." + line), Speech.GOLD);
			}
			case NEWS -> {
				Rumours.tell(level, v, player);
			}
			case PASSAGE -> passage(level, player, v, key + "." + line);
			case LORE -> {
				String kind = FoeLore.studyable(player).stream().findFirst().orElse(null);
				if (kind != null && FoeLore.teach(player, kind)) {
					Speech.tell(level, v, player, Component.translatable(key + "." + line, Component.translatable("entity.minecraft." + kind)), Speech.GOLD);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
				} else {
					Speech.tell(level, v, player, Component.translatable(key + ".none"), Speech.WHITE);
				}
			}
			default -> {
			}
		}
	}

	/** Sailors carry you to the nearest other realm's harbour town: its seat, set down on its ground. */
	private static void passage(ServerLevel level, ServerPlayer player, net.minecraft.world.entity.LivingEntity v, String line) {
		MinecraftServer server = level.getServer();
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			double d = Math.sqrt(f.seat().distToCenterSqr(player.position()));
			if (d >= PASSAGE_MIN && d <= PASSAGE_MAX && d < bestD) {
				best = f;
				bestD = d;
			}
		}
		if (best == null) {
			Speech.tell(level, v, player, Component.translatable("perk.jaymoskingdoms.passage.none"), Speech.WHITE);
			return;
		}
		BlockPos seat = best.seat();
		int x = seat.getX() + 5, z = seat.getZ() + 5;
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		Speech.tell(level, v, player, Component.translatable(line, best.name()), Speech.GOLD);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), player.getYRot(), 0.0F, false);
		level.playSound(null, x, y, z, SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
