package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.12) Every ruler (a king, or a fiefdom's lord) has a temper of his own, fixed for his life (drawn from who he
 * is; his heir may be quite another man). It shows in how he treats his people on his rounds, what he does with their
 * goods, how his realm makes war, and how he takes to players:
 * <ul>
 * <li><b>Tyrant</b>: slaps the villagers he meets, seizes food from the stalls and a tithe of the storehouse for his own
 * coffers; his realm's builders get less, his treasury more; good deeds win him over slowly.</li>
 * <li><b>Noble</b>: greets his people and talks with them, gives them bread; his realm's buildings get a quarter more
 * materials and food, he taxes lightly; he's quick to think well of players.</li>
 * <li><b>Warlord</b>: inspects rather than greets; his guards are drilled (stronger in a fight, quicker with javelins);
 * his realm sends war parties twice as often, and bigger; he taxes for the war chest.</li>
 * <li><b>Paranoid</b>: keeps his rounds short, greets no one and eyes everyone; a stranger who comes close is pushed
 * back by his guard, and one who keeps hanging about him is harassed and then driven out of the city
 * ({@link Retinue}); good deeds count half with him, bad ones double.</li>
 * <li><b>Reveler</b>: greets his people with gifts, and at dusk throws a feast at his seat: the villagers gather, there's
 * music and fireworks, they're given bread (and so have children), and it eats into the stores; players at his feast
 * are remembered fondly.</li>
 * <li><b>Purist</b>: (0.7.98) proud of his blood ({@link Purists#blood}) and insists his realm be of it: most born on his
 * land are, his folk and guards are, and he ridicules and pushes past everyone who isn't, as his people do
 * ({@link Purists}). His own kind he greets with bread and pride; good name comes quick to them and slow to outsiders.</li>
 * </ul>
 * How each one's guards take players about him (kneel to the tyrant, sheathe your blade by the warlord, the noble's
 * bread, the reveler's gifts) is {@link Retinue}'s.
 */
final class Tempers {
	enum Temper {
		TYRANT, NOBLE, WARLORD, PARANOID, REVELER,
		/** (0.7.98) Drawn on its own ({@link #PURIST_ONE_IN}), so the others' rulers keep the temper they had. */
		PURIST;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** (0.7.98) One ruler in this many is a purist. */
	static final int PURIST_ONE_IN = 6;
	/** Production: materials, food and taxes, in percent of the usual, by temper (TYRANT, NOBLE, WARLORD, PARANOID, REVELER, PURIST). */
	static final int[] MATERIALS = {75, 125, 100, 100, 100, 100}, FOOD = {85, 125, 100, 100, 85, 100}, TAXES = {200, 75, 125, 100, 100, 110};
	/** Good name won with him, and lost, in percent (a purist's goes by your blood too: {@link Purists#reputation}). */
	static final int[] GAIN = {50, 150, 100, 50, 150, 100}, LOSS = {100, 100, 100, 200, 100, 100};
	/** The tyrant's daily seizure: this share of each food in the stores (percent), at most this much of each, and goods per emerald. */
	static final int SEIZE_PERCENT = 10, SEIZE_MOST = 32, GOODS_PER_EMERALD = 8;
	/** What he takes from a stall when he passes it. */
	static final int STALL_TAKE = 4;
	/** A slap: its damage (only if the villager has more health than {@link #SLAP_SPARES}), and its push. */
	static final float SLAP_DAMAGE = 1.0F, SLAP_SPARES = 10.0F;
	static final double SLAP_PUSH = 0.6;
	/** The warlord's realm: war parties this many times as often, and this many more soldiers. */
	static final float WAR_CHANCE = 2.0F;
	static final int WAR_EXTRA = 3;
	/** The paranoid king's round ends by this time of day (how his guards take strangers is {@link Retinue}'s). */
	static final long PARANOID_ROUND_END = 5000L;
	/** The feast: when (time of day), how far villagers come from, the food it eats, the bread each villager gets, and the good name it wins a guest. */
	static final long FEAST_FROM = 10000L, FEAST_TO = 11000L;
	static final double FEAST_RANGE = 48.0;
	static final int FEAST_FOOD = 24, FEAST_BREAD = 3, FEAST_FAVOUR = 2;
	/** How often the tempers' daily doings are looked to (ticks). */
	static final int TICK = 20;
	/** How far his words carry to players. */
	static final double SPEECH_RANGE = 16.0;
	/** (0.7.98) The purist pushes one not of his blood out of his path this hard, and gives his own kind this much bread. */
	static final double PURIST_PUSH = 0.7;
	static final int PURIST_BREAD = 2;

	/** Faction id → the day of its last feast. */
	private static final Map<Integer, Long> FEASTED = new HashMap<>();

	private Tempers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Tempers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FEASTED.clear();
		});
	}

	/** Its ruler's temper; null for one without a ruler of its own (a village, a camp, a player's realm). */
	static @Nullable Temper of(@Nullable Faction f) {
		if (f == null || !f.type().isRealm() || f.leader().isEmpty() || f.members().containsKey(f.leader().get())) {
			return null;
		}
		UUID id = f.leader().get();
		long mix = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits();
		// (0.7.98) the purist on his own draw, so the rest keep the temper they had before there were purists
		long proud = Long.rotateLeft(mix, 23) * 0x9E3779B97F4A7C15L;
		if (Math.floorMod(proud >>> 11, (long)PURIST_ONE_IN) == 0) {
			return Temper.PURIST;
		}
		return Temper.values()[(int)Math.floorMod(mix ^ (mix >>> 29), (long)Temper.PURIST.ordinal())];
	}

	static @Nullable Temper of(MinecraftServer server, int faction) {
		return of(Factions.get(server, faction));
	}

	/** What it says of a ruler (for {@code /kingdom info}); (0.7.98) a purist's, with his blood. */
	static Component describe(Temper t, Faction f) {
		return t == Temper.PURIST ? Component.translatable("temper.jaymoskingdoms.purist.of", Purists.bloodName(f))
			: Component.translatable("temper.jaymoskingdoms." + t.key());
	}

	private static int percent(@Nullable Faction f, int[] table) {
		Temper t = of(f);
		return t == null ? 100 : table[t.ordinal()];
	}

	static int materials(Faction f, int amount) {
		return amount * percent(f, MATERIALS) / 100;
	}

	static int food(Faction f, int amount) {
		return amount * percent(f, FOOD) / 100;
	}

	static long taxes(Faction f, long amount) {
		return amount * percent(f, TAXES) / 100;
	}

	/** Good name won or lost with {@code kingdom}, as its ruler takes it. */
	static int reputation(Faction kingdom, int by) {
		return by > 0 ? Math.max(1, by * percent(kingdom, GAIN) / 100) : by * percent(kingdom, LOSS) / 100;
	}

	/** The chance his realm sends a war party, and the soldiers it adds. */
	static float warChance(MinecraftServer server, int faction, float chance) {
		return of(server, faction) == Temper.WARLORD ? chance * WAR_CHANCE : chance;
	}

	static int warExtra(Faction f) {
		return of(f) == Temper.WARLORD ? WAR_EXTRA : 0;
	}

	/** The paranoid king cuts his round short. */
	static boolean staysOut(ServerLevel level, Faction f) {
		return of(f) != Temper.PARANOID || Royals.timeOfDay(level) < PARANOID_ROUND_END;
	}

	// ---- meeting his people --------------------------------------------------------------------------------------------

	/**
	 * He meets {@code v} on his round ({@link Royals}): how depends on his temper. False leaves it to the plain greeting
	 * (no temper, or a noble one's: he greets, then talks).
	 */
	static boolean meet(ServerLevel level, Folk king, Villager v) {
		Faction f = Factions.get(level.getServer(), king.factionId());
		Temper t = of(f);
		if (t == null || f == null) {
			return false;
		}
		king.getLookControl().setLookAt(v, 30.0F, 30.0F);
		switch (t) {
			case TYRANT -> {
				king.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				Vec3 push = v.position().subtract(king.position()).multiply(1.0, 0.0, 1.0).normalize();
				v.push(push.x * SLAP_PUSH, 0.2, push.z * SLAP_PUSH);
				if (v.getHealth() > SLAP_SPARES) {
					v.hurtServer(level, level.damageSources().mobAttack(king), SLAP_DAMAGE);
				}
				level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0F, 1.0F);
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, v.getX(), v.getY() + 1.8, v.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
				say(level, king, f, t);
				seizeFromStall(level, king, f);
				return true;
			}
			case NOBLE -> {
				// the plain greeting, and a word, and bread
				gift(v, new ItemStack(Items.BREAD, 2));
				say(level, king, f, t);
				return false;
			}
			case WARLORD -> {
				// a curt nod (no hand raised)
				level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6F, 1.0F);
				say(level, king, f, t);
				return true;
			}
			case PARANOID -> {
				// he eyes them, and they shrink from him
				Vec3 away = v.position().subtract(king.position()).multiply(1.0, 0.0, 1.0).normalize().scale(4.0);
				v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(v.position().add(away), 0.6F, 1));
				say(level, king, f, t);
				return true;
			}
			case PURIST -> {
				// (0.7.98) bread and pride for his own blood; a sneer and a shove out of his path for anyone else
				if (Purists.kin(f, v)) {
					gift(v, new ItemStack(Items.BREAD, PURIST_BREAD));
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 1.8, v.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
					Speech.say(level, king, Component.translatable("temper.jaymoskingdoms.purist.kin" + level.getRandom().nextInt(4), Purists.bloodName(f),
						v.getDisplayName()), Speech.GOLD, SPEECH_RANGE);
					return false;
				}
				king.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				Vec3 push = v.position().subtract(king.position()).multiply(1.0, 0.0, 1.0).normalize();
				v.push(push.x * PURIST_PUSH, 0.15, push.z * PURIST_PUSH);
				Purists.slink(v, king);
				level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 0.9F);
				Speech.say(level, king, Component.translatable("temper.jaymoskingdoms.purist.line" + level.getRandom().nextInt(4),
					Purists.name(VillagerHeritage.of(v)), Purists.bloodName(f), v.getDisplayName()), Speech.RED, SPEECH_RANGE);
				return true;
			}
			case REVELER -> {
				gift(v, new ItemStack(level.getRandom().nextBoolean() ? Items.COOKIE : Items.BREAD, 2));
				level.playSound(null, v.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
				level.sendParticles(ParticleTypes.NOTE, king.getX(), king.getY() + 2.3, king.getZ(), 3, 0.4, 0.2, 0.4, 1.0);
				say(level, king, f, t);
				return false;
			}
		}
		return false;
	}

	/** A line of his, over his head for players nearby. */
	private static void say(ServerLevel level, Folk king, Faction f, Temper t) {
		int line = level.getRandom().nextInt(4);
		Component words = Component.translatable("temper.jaymoskingdoms." + t.key() + ".line" + line);
		Speech.say(level, king, words, t == Temper.TYRANT || t == Temper.PARANOID ? Speech.RED : Speech.GOLD, SPEECH_RANGE);
	}

	private static void gift(Villager v, ItemStack gift) {
		if (v.getInventory().canAddItem(gift)) {
			v.getInventory().addItem(gift);
		}
	}

	/** The tyrant passes a stall or market: food from its barrels and chests is his. */
	private static void seizeFromStall(ServerLevel level, Folk king, Faction f) {
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			if (!b.entry().equals("stall") && !b.entry().equals("market")) {
				continue;
			}
			if (!new AABB(Vec3.atLowerCornerOf(b.min()), Vec3.atLowerCornerOf(b.max()).add(1, 1, 1)).inflate(Royals.GREET_RANGE).contains(king.position())) {
				continue;
			}
			int taken = 0;
			for (BlockPos p : BlockPos.betweenClosed(b.min(), b.max())) {
				BlockEntity be = level.getBlockEntity(p);
				if (!(be instanceof Container c)) {
					continue;
				}
				for (int slot = 0; slot < c.getContainerSize() && taken < STALL_TAKE; slot++) {
					ItemStack s = c.getItem(slot);
					if (Storage.isFood(s)) {
						int n = Math.min(STALL_TAKE - taken, s.getCount());
						c.removeItem(slot, n);
						taken += n;
						c.setChanged();
					}
				}
			}
			if (taken > 0) {
				level.playSound(null, king.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.8F, 0.8F);
			}
			return;
		}
	}

	// ---- his days ------------------------------------------------------------------------------------------------------

	/**
	 * A day's goods are in ({@link Production#produce}): the tyrant takes his tithe of the stores (food and
	 * materials) and sells it for his coffers. Returns the emeralds it made him.
	 */
	static long tithe(ServerLevel level, Faction f, int days) {
		if (of(f) != Temper.TYRANT || !Storage.loaded(level, f)) {
			return 0;
		}
		int goods = 0;
		for (Map.Entry<net.minecraft.world.item.Item, Integer> e : Storage.totals(level, f).entrySet()) {
			ItemStack sample = new ItemStack(e.getKey());
			if (!Storage.isFood(sample) && !e.getKey().equals(Items.IRON_INGOT) && !e.getKey().equals(Items.GOLD_INGOT)) {
				continue;
			}
			int take = Math.min(SEIZE_MOST * days, e.getValue() * SEIZE_PERCENT / 100);
			if (take > 0) {
				goods += Storage.take(level, f, e.getKey(), take).getCount();
			}
		}
		long emeralds = goods / GOODS_PER_EMERALD;
		if (emeralds > 0) {
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + emeralds));
		}
		return emeralds;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % TICK != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		long day = Production.day(server);
		for (Faction f : Factions.all(server)) {
			Temper temper = of(f);
			if (temper == null || f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			Folk king = level.getEntity(f.leader().get()) instanceof Folk k && k.isAlive() ? k : null;
			if (temper == Temper.PURIST && king != null) {
				// (0.7.98) a purist is of the blood he's proud of
				Heritage.Kind blood = Purists.blood(f);
				if (blood != null && VillagerHeritage.of(king) != blood) {
					VillagerHeritage.born(king, blood, null);
				}
			}
			Retinue.tick(level, f, temper, king, now);
			switch (temper) {
				case WARLORD -> drill(level, f);
				case REVELER -> {
					if (t >= FEAST_FROM && t < FEAST_TO && FEASTED.getOrDefault(f.id(), -1L) != day) {
						FEASTED.put(f.id(), day);
						feast(level, f, king);
					}
				}
				default -> {
				}
			}
		}
	}

	/** The warlord's guards, drilled: stronger while they fight. */
	private static void drill(ServerLevel level, Faction f) {
		for (Folk g : level.getEntitiesOfClass(Folk.class, new AABB(f.seat()).inflate(Royals.ESCORT_CALL),
			x -> x.isAlive() && x.factionId() == f.id() && x.combatant() && x.getTarget() != null)) {
			g.addEffect(new MobEffectInstance(MobEffects.STRENGTH, TICK * 3, 0, false, false));
		}
	}

	/** The reveler's feast at the seat: his people gather, music and fireworks, bread for all, and it eats the stores. */
	private static void feast(ServerLevel level, Faction f, @Nullable Folk king) {
		BlockPos seat = f.seat();
		List<Villager> people = level.getEntitiesOfClass(Villager.class, new AABB(seat).inflate(FEAST_RANGE), Villager::isAlive);
		for (Villager v : people) {
			BlockPos spot = Construction.standable(level, seat.offset(level.getRandom().nextInt(9) - 4, 0, level.getRandom().nextInt(9) - 4), 3);
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 0.6F, 2));
			gift(v, new ItemStack(Items.BREAD, FEAST_BREAD));
		}
		if (Storage.loaded(level, f)) {
			int eaten = 0;
			for (int i = 0; i < 6 && eaten < FEAST_FOOD; i++) {
				eaten += Storage.take(level, f, Storage::isFood, FEAST_FOOD - eaten).getCount();
			}
		}
		BlockPos up = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, seat);
		for (int i = 0; i < 3; i++) {
			FireworkRocketEntity rocket = new FireworkRocketEntity(level, up.getX() + 0.5 + i * 2 - 2, up.getY() + 1.0, up.getZ() + 0.5,
				new ItemStack(Items.FIREWORK_ROCKET));
			level.addFreshEntity(rocket);
		}
		level.playSound(null, seat, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 2.0F, 1.0F);
		level.sendParticles(ParticleTypes.NOTE, seat.getX() + 0.5, seat.getY() + 2.0, seat.getZ() + 0.5, 20, 3.0, 1.0, 3.0, 1.0);
		Component news = Component.translatable("message.jaymoskingdoms.feast", king != null ? king.getDisplayName() : Component.literal(f.name()), f.name())
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(seat).inflate(FEAST_RANGE))) {
			p.sendSystemMessage(news);
			Reputation.change(p, f, FEAST_FAVOUR);
		}
		JaymosKingdoms.LOGGER.debug("{} feasts {} villagers", f.name(), people.size());
	}
}
