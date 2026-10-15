package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.59) Assassins. Now and then, someone pays for a death.
 *
 * <p><b>Contracts.</b> Each day a realm's ruler (a king, or a fiefdom's lord) has a small chance of a price on his
 * head: more for a tyrant (his own people pay) or a warlord, or a realm with a rival or an enemy at war; less for a noble
 * or a paranoid one. Players can be marked too: one known for a Rogue (the trade settles its own scores), one a realm
 * hates (it hires the blade), one whose name is known far and wide (envy). A contract is laid a day or two before the
 * blade comes ({@link #WHISPER_DAYS_MIN}-{@link #WHISPER_DAYS_MAX}), and lapses after {@link #EXPIRE_DAYS}.
 *
 * <p><b>Whispers.</b> While a contract waits, villagers in that realm's land whisper of a hooded stranger asking about
 * their ruler's ways (or about you). Heard it? Right-click the ruler to warn him: he's on his guard, and the first
 * blow is parried. A paranoid ruler is on his guard anyway, half the time.
 *
 * <p><b>The attempt.</b> Only with a player near to see it: a stranger in plain clothes walks into town and makes for
 * his mark. Close enough, he throws off the cloak (black leathers, a blade) and stabs: a quarter of a ruler's health
 * (half if he's asleep in his bed) and a poison that eats at him for half a minute. The ruler's guards come running.
 * Milk, honey or a golden apple given to him (right-click) cures it; his physician may, too. The assassin fights a
 * while, then throws down smoke and runs. A player he comes for gets a moment's warning (rogues sense their own kind
 * further off), a stab and a poison that won't kill on its own.
 *
 * <p><b>After.</b> Cut him down and he drops his purse and the sealed contract naming who paid; the realm he came for
 * thanks you, and if a rival realm paid, they're rivals now. A ruler murdered is mourned and his heir crowned (no ruin,
 * no conquest), and word of who paid may get out.
 */
final class Assassins {
	/** The assassin's role (see {@link Folk#defineRole}). */
	static final int ASSASSIN = 60;

	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** Each day, the chance of a contract on a realm's ruler, before his temper and his feuds. */
	static final float REALM_CHANCE = 0.015F;
	/** By temper (TYRANT, NOBLE, WARLORD, PARANOID, REVELER): how many enemies he makes. */
	static final float[] TEMPER_CHANCE = {2.5F, 0.5F, 1.5F, 0.75F, 1.0F};
	/** A realm with a rival or an enemy at war: this many times as likely. */
	static final float FEUD_CHANCE = 2.0F;
	/** Of a feuding realm's contracts, the share its rivals pay for (the rest are his own people's, an heir's, or no one knows). */
	static final float RIVAL_SHARE = 0.7F;
	/** Each day, the chance of a contract on a player known for a Rogue, one a realm hates, one known in this many settlements. */
	static final float ROGUE_CHANCE = 0.02F, GRUDGE_CHANCE = 0.02F, RENOWN_CHANCE = 0.01F;
	static final int RENOWN_KNOWN = 4;
	/** At most this many contracts out at once (over the whole world). */
	static final int MAX_CONTRACTS = 3;
	/** The blade comes this many days after the contract (at the earliest), and the contract lapses after this many. */
	static final int WHISPER_DAYS_MIN = 1, WHISPER_DAYS_MAX = 2, EXPIRE_DAYS = 6;
	/** How often a waiting blade looks for his moment (ticks), and the chance he takes it. */
	static final int LOOK_TICKS = 100;
	static final float STRIKE_CHANCE = 0.25F;
	/** A ruler's attempt needs a player (not in creative) this near him to see it. */
	static final double WITNESS_RANGE = 40.0;
	/** He comes in from this far off, and never closer than this to a player. */
	static final int SPAWN_MIN = 20, SPAWN_MAX = 30;
	static final double PLAYER_SPACE = 14.0;
	/** A player is marked at night only (time of day). */
	static final long NIGHT_FROM = 13000L, NIGHT_TO = 23000L;
	/** The assassin: his health, his blows (his iron sword adds 5), his speed; his walk while he stalks and his reach for the stab. */
	static final double HEALTH = 30.0, DAMAGE = 2.0, SPEED = 0.32, STALK_SPEED = 0.7, STAB_REACH = 2.6;
	/** The stab: this share of a ruler's health (asleep: the second), this much to a player, and the player's poison (ticks, level). */
	static final float STAB_SHARE = 0.25F, SLEEP_STAB_SHARE = 0.5F, PLAYER_STAB = 6.0F;
	static final int PLAYER_POISON_TICKS = 160, PLAYER_POISON_LEVEL = 1;
	/** The ruler's poison: how long, the share of his health it takes each second, and the chance each second his physician purges it. */
	static final int VENOM_TICKS = 600;
	static final float VENOM_SHARE = 0.035F, PURGE_CHANCE = 0.04F;
	/** A paranoid ruler parries the first blow this often (a warned one always does). */
	static final float PARANOID_PARRY = 0.5F;
	/** Stalking this long without reaching his mark, he gives up; he fights this long, or until he's down to this share of health, then runs. */
	static final int STALK_TICKS = 20 * 90, FIGHT_TICKS = 20 * 12;
	static final float FLEE_AT = 0.5F;
	/** Running: he's gone after this long, or this far from where he struck; the smoke hides him this long. */
	static final int FLEE_TICKS = 20 * 15, SMOKE_TICKS = 20 * 8;
	static final double FLEE_AWAY = 36.0;
	/** A player feels eyes on them when he's this near (a Stealth level of {@link #ROGUE_SENSE_LEVEL} or more: the second). */
	static final double FEEL_RANGE = 9.0, ROGUE_FEEL_RANGE = 22.0;
	static final int ROGUE_SENSE_LEVEL = 3;
	/** Good name: warning a ruler, curing him, and cutting down the blade sent for him. */
	static final int REP_WARN = 15, REP_CURE = 40, REP_CAUGHT = 50;
	/** A cured ruler's thanks from his treasury (emeralds, as far as it goes), and the assassin's purse. */
	static final int CURE_REWARD = 16, FEE_MIN = 8, FEE_MAX = 20;
	/** How often villagers may whisper of a contract to a player (ticks), the chance one does, and how near the villager is. */
	static final int WHISPER_TICKS = 600;
	static final float WHISPER_CHANCE = 0.3F;
	static final double WHISPER_RANGE = 16.0;
	/** The color of his leathers. */
	static final int LEATHERS = 0x1C1A1F;
	/** The guards this near the ruler come running. */
	static final double GUARD_CALL = 40.0;

	// ---- contracts ---------------------------------------------------------------------------------------------------------

	/**
	 * A price on a head. {@code target}: "realm:&lt;id&gt;" (its ruler) or a player's UUID. {@code hirer}: the realm that
	 * paid, or -1 ({@code motive} says who: rival, people, heir, guild; for a player grudge, rogue, renown). The blade
	 * comes from {@code strikeAfter} (game time) and the contract lapses at {@code expires}. {@code forewarned}: the
	 * ruler was warned. {@code forced}: an op's (comes at once, day or night).
	 */
	record Contract(String target, int hirer, String motive, long strikeAfter, long expires, boolean forewarned, boolean forced, int fee) {
		static final Codec<Contract> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("target").forGetter(Contract::target),
				Codec.INT.optionalFieldOf("hirer", -1).forGetter(Contract::hirer),
				Codec.STRING.optionalFieldOf("motive", "guild").forGetter(Contract::motive),
				Codec.LONG.fieldOf("strike_after").forGetter(Contract::strikeAfter),
				Codec.LONG.fieldOf("expires").forGetter(Contract::expires),
				Codec.BOOL.optionalFieldOf("forewarned", false).forGetter(Contract::forewarned),
				Codec.BOOL.optionalFieldOf("forced", false).forGetter(Contract::forced),
				Codec.INT.optionalFieldOf("fee", FEE_MIN).forGetter(Contract::fee))
			.apply(i, Contract::new));

		int realm() {
			return this.target.startsWith("realm:") ? Integer.parseInt(this.target.substring(6)) : -1;
		}

		@Nullable UUID player() {
			return this.target.startsWith("realm:") ? null : UUID.fromString(this.target);
		}

		Contract warned() {
			return new Contract(this.target, this.hirer, this.motive, this.strikeAfter, this.expires, true, this.forced, this.fee);
		}
	}

	/** The contracts out (saved with the Overworld). */
	static final AttachmentType<List<Contract>> CONTRACTS = AttachmentRegistry.create(JaymosKingdoms.id("contracts"),
		builder -> builder.persistent(Contract.CODEC.listOf()));

	// ---- attempts under way (not saved: an assassin left over from before a restart slips away) -------------------------

	enum Stage {
		STALK, FIGHT, FLEE
	}

	static final class Plot {
		final Contract contract;
		final UUID assassin, victim;
		Stage stage = Stage.STALK;
		long stageAt;
		Vec3 struckAt = Vec3.ZERO;
		/** The ruler's poison (ticks left), whether the assassin's gone, whether a player felt him coming. */
		int venom;
		boolean gone, sensed, done;

		Plot(Contract contract, UUID assassin, UUID victim, long now) {
			this.contract = contract;
			this.assassin = assassin;
			this.victim = victim;
			this.stageAt = now;
		}
	}

	/** Assassin's UUID → his plot. */
	private static final Map<UUID, Plot> PLOTS = new HashMap<>();
	/** Who's heard the whispers of which contract (by target). */
	private static final Map<UUID, Set<String>> HEARD = new HashMap<>();
	private static long lastDay = Long.MIN_VALUE;

	private Assassins() {
	}

	static void register() {
		Folk.defineRole(new Folk.Role(ASSASSIN, "assassin", true, false, null, (folk, faction) -> disguise(folk)));
		ServerTickEvents.END_SERVER_TICK.register(Assassins::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PLOTS.clear();
			HEARD.clear();
			LINGERING.clear();
			lastDay = Long.MIN_VALUE;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
				playerDied(level, player);
			}
		});
	}

	static boolean is(Entity e) {
		return e instanceof Folk f && f.role() == ASSASSIN;
	}

	private static List<Contract> contracts(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CONTRACTS, List.of());
	}

	private static void store(MinecraftServer server, List<Contract> contracts) {
		Factions.level(server).setAttached(CONTRACTS, List.copyOf(contracts));
	}

	private static @Nullable Contract contractOn(MinecraftServer server, String target) {
		for (Contract c : contracts(server)) {
			if (c.target().equals(target)) {
				return c;
			}
		}
		return null;
	}

	private static void remove(MinecraftServer server, String target) {
		List<Contract> all = new ArrayList<>(contracts(server));
		if (all.removeIf(c -> c.target().equals(target))) {
			store(server, all);
		}
		for (Set<String> heard : HEARD.values()) {
			heard.remove(target);
		}
	}

	private static void replace(MinecraftServer server, Contract c) {
		List<Contract> all = new ArrayList<>(contracts(server));
		all.removeIf(o -> o.target().equals(c.target()));
		all.add(c);
		store(server, all);
	}

	private static boolean underWay(String target) {
		for (Plot p : PLOTS.values()) {
			if (!p.done && p.contract.target().equals(target)) {
				return true;
			}
		}
		return false;
	}

	// ---- the days ----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 20 == 0) {
			long day = Production.day(server);
			if (lastDay == Long.MIN_VALUE) {
				lastDay = day;
			} else if (day != lastDay) {
				lastDay = day;
				layContracts(server, level, now);
			}
			venom(level);
		}
		if (now % LOOK_TICKS == 0) {
			for (Contract c : List.copyOf(contracts(server))) {
				if (!c.forced() && now >= c.expires()) {
					remove(server, c.target());
				} else if (now >= c.strikeAfter() && !underWay(c.target())) {
					tryStrike(server, level, c, now);
				}
			}
		}
		if (now % WHISPER_TICKS == 0) {
			whispers(server, level, now);
		}
	}

	/** A new day: maybe someone pays for a death. */
	private static void layContracts(MinecraftServer server, ServerLevel level, long now) {
		RandomSource random = level.getRandom();
		List<Contract> all = new ArrayList<>(contracts(server));
		for (Faction f : Factions.all(server)) {
			if (all.size() >= MAX_CONTRACTS) {
				break;
			}
			Tempers.Temper temper = Tempers.of(f);
			if (temper == null || f.fallen() || f.successionAt() >= 0 || contractOn(server, "realm:" + f.id()) != null) {
				continue;
			}
			List<Faction> feuds = new ArrayList<>();
			for (Faction o : Factions.all(server)) {
				Relation r = o.id() == f.id() || !o.type().isRealm() || o.fallen() ? Relation.NEUTRAL : Relations.between(o, f);
				if (r == Relation.RIVAL || r == Relation.WAR) {
					feuds.add(o);
				}
			}
			float chance = REALM_CHANCE * TEMPER_CHANCE[temper.ordinal()] * (feuds.isEmpty() ? 1.0F : FEUD_CHANCE);
			if (random.nextFloat() >= chance) {
				continue;
			}
			int hirer = -1;
			String motive;
			if (!feuds.isEmpty() && random.nextFloat() < RIVAL_SHARE) {
				hirer = feuds.get(random.nextInt(feuds.size())).id();
				motive = "rival";
			} else if (temper == Tempers.Temper.TYRANT && random.nextBoolean()) {
				motive = "people";
			} else {
				motive = random.nextBoolean() ? "heir" : "guild";
			}
			Contract c = contract("realm:" + f.id(), hirer, motive, now, random, false);
			all.add(c);
			JaymosKingdoms.LOGGER.info("A contract on the ruler of {} ({})", f.name(), motive);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (all.size() >= MAX_CONTRACTS || player.isCreative() || player.isSpectator() || contractOn(server, player.getStringUUID()) != null) {
				continue;
			}
			Skills.Pillar alignment = Renown.alignment(Skills.of(player));
			Faction grudge = null;
			for (Faction f : Factions.all(server)) {
				if (f.type().isRealm() && !f.fallen() && Reputation.get(player, f.id()) <= Reputation.HOSTILE
					&& (grudge == null || Reputation.get(player, f.id()) < Reputation.get(player, grudge.id()))) {
					grudge = f;
				}
			}
			String motive = null;
			int hirer = -1;
			if (grudge != null && random.nextFloat() < GRUDGE_CHANCE) {
				motive = "grudge";
				hirer = grudge.id();
			} else if (alignment == Skills.Pillar.STEALTH && random.nextFloat() < ROGUE_CHANCE) {
				motive = "rogue";
			} else if (player.getAttachedOrElse(Renown.KNOWN, Map.of()).size() >= RENOWN_KNOWN && random.nextFloat() < RENOWN_CHANCE) {
				motive = "renown";
			}
			if (motive != null) {
				all.add(contract(player.getStringUUID(), hirer, motive, now, random, false));
				JaymosKingdoms.LOGGER.info("A contract on {} ({})", player.getName().getString(), motive);
			}
		}
		if (all.size() != contracts(server).size()) {
			store(server, all);
		}
	}

	private static Contract contract(String target, int hirer, String motive, long now, RandomSource random, boolean forced) {
		long wait = forced ? 0L : (WHISPER_DAYS_MIN + random.nextInt(WHISPER_DAYS_MAX - WHISPER_DAYS_MIN + 1)) * 24000L;
		return new Contract(target, hirer, motive, now + wait, now + EXPIRE_DAYS * 24000L, false, forced, FEE_MIN + random.nextInt(FEE_MAX - FEE_MIN + 1));
	}

	// ---- whispers ----------------------------------------------------------------------------------------------------------

	/** While a contract waits, the people of the land whisper of it to players among them. */
	private static void whispers(MinecraftServer server, ServerLevel level, long now) {
		RandomSource random = level.getRandom();
		for (Contract c : contracts(server)) {
			if (underWay(c.target())) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || HEARD.getOrDefault(player.getUUID(), Set.of()).contains(c.target()) || random.nextFloat() >= WHISPER_CHANCE) {
					continue;
				}
				Faction here = Factions.at(server, level, player.blockPosition());
				Component line;
				if (c.realm() >= 0) {
					Faction f = Factions.get(server, c.realm());
					if (f == null || here == null || here.id() != f.id()) {
						continue;
					}
					line = Component.translatable("assassin.jaymoskingdoms.whisper." + c.motive() + "." + random.nextInt(2), ruler(level, f));
				} else if (player.getUUID().equals(c.player()) && here != null) {
					line = Component.translatable("assassin.jaymoskingdoms.whisper.you." + random.nextInt(3), player.getName());
				} else {
					continue;
				}
				List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(WHISPER_RANGE), v -> v.isAlive() && !v.isBaby());
				if (near.isEmpty()) {
					continue;
				}
				Villager v = near.get(random.nextInt(near.size()));
				// said aloud over the villager's head only: no copy in chat, so it's easy to miss
				Speech.say(level, v, line, Speech.WHITE, 8.0);
				HEARD.computeIfAbsent(player.getUUID(), id -> new HashSet<>()).add(c.target());
			}
		}
	}

	private static Component ruler(ServerLevel level, Faction f) {
		if (f.leader().isPresent() && level.getEntity(f.leader().get()) instanceof Folk king) {
			return king.getDisplayName();
		}
		return Component.literal(f.name());
	}

	// ---- the attempt -------------------------------------------------------------------------------------------------------

	private static void tryStrike(MinecraftServer server, ServerLevel level, Contract c, long now) {
		RandomSource random = level.getRandom();
		if (!c.forced() && random.nextFloat() >= STRIKE_CHANCE) {
			return;
		}
		LivingEntity victim;
		Faction dress;
		if (c.realm() >= 0) {
			Faction f = Factions.get(server, c.realm());
			if (f == null || f.fallen() || f.leader().isEmpty()) {
				remove(server, c.target());
				return;
			}
			if (!(level.getEntity(f.leader().get()) instanceof Folk king) || !king.isAlive() || !level.isPositionEntityTicking(king.blockPosition())) {
				return;
			}
			boolean seen = !level.getEntitiesOfClass(ServerPlayer.class, king.getBoundingBox().inflate(WITNESS_RANGE),
				p -> !p.isCreative() && !p.isSpectator() || c.forced()).isEmpty();
			if (!seen) {
				return;
			}
			victim = king;
			dress = f;
		} else {
			ServerPlayer player = server.getPlayerList().getPlayer(c.player());
			if (player == null || player.level() != level || player.isSpectator() || player.isCreative() && !c.forced()) {
				return;
			}
			long t = Royals.timeOfDay(level);
			if (!c.forced() && (t < NIGHT_FROM || t >= NIGHT_TO)) {
				return;
			}
			victim = player;
			dress = c.hirer() >= 0 ? Factions.get(server, c.hirer()) : null;
			if (dress == null) {
				dress = Factions.realmNear(server, player.blockPosition(), 100000);
			}
			if (dress == null && !Factions.all(server).isEmpty()) {
				dress = Factions.all(server).get(0);
			}
			if (dress == null) {
				return;
			}
		}
		BlockPos spot = approach(level, victim, random);
		if (spot == null) {
			return;
		}
		Folk a = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (a == null) {
			return;
		}
		a.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		a.setup(ASSASSIN, dress, victim.blockPosition(), 64);
		a.setFactionId(-1);
		a.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		a.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DAMAGE);
		a.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEED);
		a.setHealth((float)HEALTH);
		disguise(a);
		level.addFreshEntity(a);
		PLOTS.put(a.getUUID(), new Plot(c, a.getUUID(), victim.getUUID(), level.getGameTime()));
		remove(server, c.target());
		JaymosKingdoms.LOGGER.info("An assassin comes for {} at {}", victim.getName().getString(), victim.blockPosition());
	}

	/** Where he comes in from: on the ground {@link #SPAWN_MIN}-{@link #SPAWN_MAX} blocks from his mark, no player close. */
	private static @Nullable BlockPos approach(ServerLevel level, LivingEntity victim, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			BlockPos near = BlockPos.containing(victim.getX() + Math.cos(angle) * r, victim.getY(), victim.getZ() + Math.sin(angle) * r);
			if (!level.isPositionEntityTicking(near)) {
				continue;
			}
			BlockPos spot = Construction.standable(level, near, 3);
			if (!level.getBlockState(spot.below()).isSolid() || Math.abs(spot.getY() - victim.getY()) > 8) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(spot);
			if (level.getEntitiesOfClass(ServerPlayer.class, new AABB(spot).inflate(PLAYER_SPACE), p -> p.position().distanceTo(at) < PLAYER_SPACE).isEmpty()) {
				return spot;
			}
		}
		return null;
	}

	/** Plain clothes: nothing in his hands, nothing on, a stranger's name. */
	static void disguise(Folk a) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			a.setItemSlot(slot, ItemStack.EMPTY);
			a.setDropChance(slot, 0.0F);
		}
		a.setCustomName(Component.translatable("entity.jaymoskingdoms.stranger"));
	}

	/** The cloak off: black leathers, a blade. */
	private static void reveal(ServerLevel level, Folk a) {
		a.setItemSlot(EquipmentSlot.HEAD, leathers(Items.LEATHER_HELMET));
		a.setItemSlot(EquipmentSlot.CHEST, leathers(Items.LEATHER_CHESTPLATE));
		a.setItemSlot(EquipmentSlot.LEGS, leathers(Items.LEATHER_LEGGINGS));
		a.setItemSlot(EquipmentSlot.FEET, leathers(Items.LEATHER_BOOTS));
		a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			a.setDropChance(slot, 0.0F);
		}
		a.setCustomName(Component.translatable("entity.jaymoskingdoms.assassin"));
		level.sendParticles(ParticleTypes.LARGE_SMOKE, a.getX(), a.getY() + 1.0, a.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
	}

	private static ItemStack leathers(net.minecraft.world.item.Item item) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.DYED_COLOR, new DyedItemColor(LEATHERS));
		return s;
	}

	private static @Nullable LivingEntity victim(ServerLevel level, Plot p) {
		Entity e = level.getEntity(p.victim);
		if (e == null && p.contract.player() != null) {
			e = level.getServer().getPlayerList().getPlayer(p.victim);
		}
		return e instanceof LivingEntity l && l.isAlive() && l.level() == level ? l : null;
	}

	/** The assassin's own doings, each tick (from {@link Folk#customServerAiStep}). */
	static void act(ServerLevel level, Folk a) {
		Plot p = PLOTS.get(a.getUUID());
		if (p == null) {
			// (from before a restart: he slips away)
			a.discard();
			return;
		}
		long now = level.getGameTime();
		LivingEntity victim = victim(level, p);
		switch (p.stage) {
			case STALK -> {
				a.setTarget(null);
				if (victim == null || now - p.stageAt > STALK_TICKS) {
					// lost his mark: he melts back into the crowd
					p.stage = Stage.FLEE;
					p.stageAt = now;
					p.struckAt = a.position();
					return;
				}
				if (a.getLastHurtByMob() != null && a.tickCount - a.getLastHurtByMobTimestamp() < 5) {
					// found out before he could strike
					reveal(level, a);
					flee(level, a, p, now);
					return;
				}
				if (a.tickCount % 10 == 0) {
					a.getNavigation().moveTo(victim, STALK_SPEED);
				}
				if (victim instanceof ServerPlayer player && !p.sensed) {
					double range = Skills.of(player).level(Skills.Pillar.STEALTH) >= ROGUE_SENSE_LEVEL ? ROGUE_FEEL_RANGE : FEEL_RANGE;
					if (a.distanceTo(player) <= range) {
						// only a sound to go on, no words on screen
						p.sensed = true;
						level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
					}
				}
				if (a.distanceTo(victim) <= STAB_REACH) {
					strike(level, a, p, victim, now);
				}
			}
			case FIGHT -> {
				if (victim == null || now - p.stageAt > FIGHT_TICKS || a.getHealth() < a.getMaxHealth() * FLEE_AT) {
					flee(level, a, p, now);
					return;
				}
				if (a.getTarget() != victim) {
					a.setTarget(victim);
				}
			}
			case FLEE -> {
				a.setTarget(null);
				if (now - p.stageAt > FLEE_TICKS || a.position().distanceTo(p.struckAt) > FLEE_AWAY) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, a.getX(), a.getY() + 1.0, a.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
					p.gone = true;
					PLOTS.remove(a.getUUID());
					a.discard();
					settle(level, p);
					return;
				}
				if (a.tickCount % 20 == 0) {
					Vec3 away = a.position().subtract(p.struckAt).multiply(1.0, 0.0, 1.0);
					if (away.lengthSqr() < 1.0) {
						away = new Vec3(level.getRandom().nextDouble() - 0.5, 0.0, level.getRandom().nextDouble() - 0.5);
					}
					Vec3 to = a.position().add(away.normalize().scale(16.0));
					a.getNavigation().moveTo(to.x, a.getY(), to.z, 1.3);
				}
			}
		}
	}

	private static void strike(ServerLevel level, Folk a, Plot p, LivingEntity victim, long now) {
		reveal(level, a);
		a.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		a.getLookControl().setLookAt(victim, 30.0F, 30.0F);
		p.struckAt = victim.position();
		if (victim instanceof Folk ruler) {
			Faction f = Factions.get(level.getServer(), p.contract.realm());
			boolean parried = p.contract.forewarned() || Tempers.of(f) == Tempers.Temper.PARANOID && level.getRandom().nextFloat() < PARANOID_PARRY;
			callGuards(level, ruler, a);
			if (parried) {
				level.playSound(null, ruler.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
				Speech.say(level, ruler, Component.translatable("assassin.jaymoskingdoms.parried.says"), Speech.RED, WITNESS_RANGE);
				witnesses(level, ruler, Component.translatable("assassin.jaymoskingdoms.parried", ruler.getDisplayName()).withStyle(ChatFormatting.GOLD));
				ruler.setTarget(a);
				flee(level, a, p, now);
				return;
			}
			float share = ruler.isSleeping() ? SLEEP_STAB_SHARE : STAB_SHARE;
			if (ruler.isSleeping()) {
				ruler.stopSleeping();
			}
			ruler.hurtServer(level, level.damageSources().indirectMagic(a, a), ruler.getMaxHealth() * share);
			level.playSound(null, ruler.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2F, 0.7F);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, ruler.getX(), ruler.getY() + 1.2, ruler.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
			p.venom = VENOM_TICKS;
			ruler.setTarget(a);
			witnesses(level, ruler, Component.translatable("assassin.jaymoskingdoms.strikes", ruler.getDisplayName()).withStyle(ChatFormatting.DARK_RED));
			witnesses(level, ruler, Component.translatable("assassin.jaymoskingdoms.poisoned", ruler.getDisplayName()).withStyle(ChatFormatting.RED));
		} else if (victim instanceof ServerPlayer player) {
			player.hurtServer(level, level.damageSources().mobAttack(a), PLAYER_STAB);
			player.addEffect(new MobEffectInstance(MobEffects.POISON, PLAYER_POISON_TICKS, PLAYER_POISON_LEVEL));
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2F, 0.7F);
			player.sendSystemMessage(Component.translatable("assassin.jaymoskingdoms.player.stabbed").withStyle(ChatFormatting.DARK_RED));
		}
		p.stage = Stage.FIGHT;
		p.stageAt = now;
		a.setTarget(victim);
	}

	/** Smoke, and he runs. */
	private static void flee(ServerLevel level, Folk a, Plot p, long now) {
		p.stage = Stage.FLEE;
		p.stageAt = now;
		if (p.struckAt == Vec3.ZERO) {
			p.struckAt = a.position();
		}
		a.setTarget(null);
		a.getNavigation().stop();
		a.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SMOKE_TICKS, 0, false, false));
		a.addEffect(new MobEffectInstance(MobEffects.SPEED, FLEE_TICKS, 1, false, false));
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, a.getX(), a.getY() + 0.8, a.getZ(), 30, 0.8, 0.6, 0.8, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, a.getX(), a.getY() + 0.8, a.getZ(), 30, 1.0, 0.8, 1.0, 0.05);
		level.playSound(null, a.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	/** The ruler's guards come running. */
	private static void callGuards(ServerLevel level, Folk ruler, Folk a) {
		for (Folk g : level.getEntitiesOfClass(Folk.class, ruler.getBoundingBox().inflate(GUARD_CALL),
			g -> g.isAlive() && g != ruler && g.combatant() && g.factionId() == ruler.factionId())) {
			g.setTarget(a);
		}
	}

	private static void witnesses(ServerLevel level, Entity at, Component message) {
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, at.getBoundingBox().inflate(WITNESS_RANGE))) {
			p.sendSystemMessage(message);
		}
	}

	// ---- the poison --------------------------------------------------------------------------------------------------------

	/** Each second: a stabbed ruler's poison eats at him, unless his physician purges it. */
	private static void venom(ServerLevel level) {
		for (Plot p : List.copyOf(PLOTS.values())) {
			venomTick(level, p);
		}
		for (Plot p : List.copyOf(LINGERING)) {
			venomTick(level, p);
		}
	}

	/** Plots whose assassin is gone but whose poison still works. */
	private static final List<Plot> LINGERING = new ArrayList<>();

	private static void venomTick(ServerLevel level, Plot p) {
		if (p.venom <= 0 || p.done) {
			return;
		}
		if (!(level.getEntity(p.victim) instanceof Folk ruler) || !ruler.isAlive()) {
			// (out of reach, or dead already: it's over)
			p.venom = 0;
			settle(level, p);
			return;
		}
		if (level.getRandom().nextFloat() < PURGE_CHANCE) {
			p.venom = 0;
			Speech.say(level, ruler, Component.translatable("assassin.jaymoskingdoms.purged.says"), Speech.GOLD, WITNESS_RANGE);
			settle(level, p);
			return;
		}
		p.venom -= 20;
		level.sendParticles(ParticleTypes.ITEM_SLIME, ruler.getX(), ruler.getY() + 1.4, ruler.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
		ruler.hurtServer(level, level.damageSources().magic(), ruler.getMaxHealth() * VENOM_SHARE);
		if (p.venom <= 0) {
			settle(level, p);
		}
	}

	/** True if {@code ruler} is under a poison now (or an assassin's at him). */
	private static @Nullable Plot plotOn(UUID victim) {
		for (Plot p : PLOTS.values()) {
			if (p.victim.equals(victim) && !p.done) {
				return p;
			}
		}
		for (Plot p : LINGERING) {
			if (p.victim.equals(victim) && !p.done) {
				return p;
			}
		}
		return null;
	}

	/** Once the assassin's gone and the poison's done: the ruler lived. Word gets round. */
	private static void settle(ServerLevel level, Plot p) {
		if (p.done) {
			return;
		}
		if (!p.gone || p.venom > 0) {
			if (p.gone && !LINGERING.contains(p)) {
				LINGERING.add(p);
			}
			return;
		}
		p.done = true;
		LINGERING.remove(p);
		if (p.contract.realm() >= 0 && p.stage != Stage.STALK) {
			Faction f = Factions.get(level.getServer(), p.contract.realm());
			if (f != null && level.getEntity(p.victim) instanceof Folk ruler && ruler.isAlive()) {
				KingdomEvents.announce(level.getServer(), Component.translatable("assassin.jaymoskingdoms.news.survived", ruler.getDisplayName(), f.name())
					.withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	// ---- players' parts ------------------------------------------------------------------------------------------------------

	/**
	 * {@code player} right-clicks {@code folk} holding {@code held}: a poisoned ruler given milk, honey or a golden apple
	 * is cured; a ruler with a price on his head is warned by one who heard the whispers; a stranger is spoken to. True
	 * when that's all the click does.
	 */
	static boolean interact(ServerPlayer player, Folk folk, ItemStack held) {
		ServerLevel level = (ServerLevel)player.level();
		MinecraftServer server = level.getServer();
		if (is(folk)) {
			Plot p = PLOTS.get(folk.getUUID());
			if (p != null && p.stage == Stage.STALK) {
				Speech.say(level, folk, Component.translatable("assassin.jaymoskingdoms.stranger." + level.getRandom().nextInt(3)), Speech.WHITE, 8.0);
				if (Skills.of(player).level(Skills.Pillar.STEALTH) >= ROGUE_SENSE_LEVEL) {
					player.sendOverlayMessage(Component.translatable("assassin.jaymoskingdoms.stranger.known").withStyle(ChatFormatting.DARK_RED));
				}
			}
			return true;
		}
		if (!folk.leader()) {
			return false;
		}
		Plot p = plotOn(folk.getUUID());
		if (p != null && p.venom > 0 && (held.is(Items.MILK_BUCKET) || held.is(Items.HONEY_BOTTLE) || held.is(Items.GOLDEN_APPLE)
			|| held.is(Items.ENCHANTED_GOLDEN_APPLE))) {
			cure(level, player, folk, p, held);
			return true;
		}
		Faction f = Factions.get(server, folk.factionId());
		if (f == null || f.leader().isEmpty() || !f.leader().get().equals(folk.getUUID())) {
			return false;
		}
		String target = "realm:" + f.id();
		Contract c = contractOn(server, target);
		if (c != null && !c.forewarned() && HEARD.getOrDefault(player.getUUID(), Set.of()).contains(target)) {
			replace(server, c.warned());
			Reputation.change(player, f, REP_WARN);
			Speech.say(level, folk, Component.translatable("assassin.jaymoskingdoms.warned.says"), Speech.GOLD, 16.0);
			player.sendSystemMessage(Component.translatable("assassin.jaymoskingdoms.warned", folk.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
		return false;
	}

	private static void cure(ServerLevel level, ServerPlayer player, Folk ruler, Plot p, ItemStack held) {
		if (!player.isCreative()) {
			if (held.is(Items.MILK_BUCKET)) {
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
			} else if (held.is(Items.HONEY_BOTTLE)) {
				player.setItemInHand(InteractionHand.MAIN_HAND, held.getCount() > 1 ? held.copyWithCount(held.getCount() - 1) : new ItemStack(Items.GLASS_BOTTLE));
				if (held.getCount() > 1 && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
					player.drop(new ItemStack(Items.GLASS_BOTTLE), false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			} else {
				held.shrink(1);
			}
		}
		p.venom = 0;
		ruler.heal(ruler.getMaxHealth() * 0.1F);
		level.playSound(null, ruler.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ruler.getX(), ruler.getY() + 1.6, ruler.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
		Faction f = Factions.get(level.getServer(), ruler.factionId());
		if (f != null) {
			Reputation.change(player, f, REP_CURE);
			int reward = (int)Math.min(CURE_REWARD, f.treasury());
			if (reward > 0) {
				Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() - reward));
				ItemStack emeralds = new ItemStack(Items.EMERALD, reward);
				if (!player.getInventory().add(emeralds)) {
					player.drop(emeralds, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		}
		Speech.say(level, ruler, Component.translatable("assassin.jaymoskingdoms.cured.says", player.getName()), Speech.GOLD, WITNESS_RANGE);
		witnesses(level, ruler, Component.translatable("assassin.jaymoskingdoms.cured", player.getDisplayName(), ruler.getDisplayName()).withStyle(ChatFormatting.GOLD));
		settle(level, p);
	}

	/** The assassin falls (from {@link Folk#die}): his purse and his contract drop, and whoever cut him down is thanked. */
	static void died(ServerLevel level, Folk a, DamageSource source) {
		if (!is(a)) {
			return;
		}
		Plot p = PLOTS.remove(a.getUUID());
		if (p == null) {
			return;
		}
		p.gone = true;
		MinecraftServer server = level.getServer();
		Contract c = p.contract;
		Faction hirer = c.hirer() >= 0 ? Factions.get(server, c.hirer()) : null;
		Component victimName = c.realm() >= 0 ? realmRuler(level, c.realm()) : playerName(server, c.player());
		a.spawnAtLocation(level, sealedContract(victimName, hirer, c));
		a.spawnAtLocation(level, new ItemStack(Items.EMERALD, c.fee()));
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp : null;
		if (c.realm() >= 0) {
			Faction f = Factions.get(server, c.realm());
			if (f != null) {
				if (killer != null) {
					Reputation.change(killer, f, REP_CAUGHT);
				}
				KingdomEvents.announce(server, Component.translatable(killer != null ? "assassin.jaymoskingdoms.news.caught.by" : "assassin.jaymoskingdoms.news.caught",
					victimName, f.name(), killer != null ? killer.getDisplayName() : Component.empty()).withStyle(ChatFormatting.GOLD));
				expose(server, f, hirer);
			}
		} else if (killer != null && killer.getUUID().equals(c.player())) {
			killer.sendSystemMessage(Component.translatable(hirer != null ? "assassin.jaymoskingdoms.player.caught.hirer" : "assassin.jaymoskingdoms.player.caught",
				hirer != null ? hirer.name() : "").withStyle(ChatFormatting.GOLD));
		}
		settle(level, p);
	}

	/** A rival realm's seal on the contract: the two are rivals now (an open war stays a war). */
	private static void expose(MinecraftServer server, Faction target, @Nullable Faction hirer) {
		if (hirer == null || hirer.fallen()) {
			return;
		}
		if (Relations.between(server, target.id(), hirer.id()) == Relation.NEUTRAL) {
			Relations.set(server, target.id(), hirer.id(), Relation.RIVAL);
		}
		KingdomEvents.announce(server, Component.translatable("assassin.jaymoskingdoms.news.exposed", hirer.name(), target.name()).withStyle(ChatFormatting.RED));
	}

	private static ItemStack sealedContract(Component victim, @Nullable Faction hirer, Contract c) {
		ItemStack paper = new ItemStack(Items.PAPER);
		paper.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.sealed_contract").withStyle(ChatFormatting.DARK_RED));
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("item.jaymoskingdoms.sealed_contract.on", victim).withStyle(ChatFormatting.GRAY));
		lines.add((hirer != null ? Component.translatable("item.jaymoskingdoms.sealed_contract.by", hirer.name())
			: Component.translatable("item.jaymoskingdoms.sealed_contract.by." + c.motive())).withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("item.jaymoskingdoms.sealed_contract.fee", c.fee()).withStyle(ChatFormatting.GRAY));
		paper.set(DataComponents.LORE, new ItemLore(lines));
		return paper;
	}

	private static Component realmRuler(ServerLevel level, int realm) {
		Faction f = Factions.get(level.getServer(), realm);
		return f == null ? Component.literal("?") : ruler(level, f);
	}

	private static Component playerName(MinecraftServer server, @Nullable UUID id) {
		ServerPlayer p = id == null ? null : server.getPlayerList().getPlayer(id);
		return p == null ? Component.literal("?") : p.getName();
	}

	/**
	 * (From {@link KingdomEvents#folkDied}) A ruler died: if it was the assassin's blade or his poison, it's murder. Word
	 * goes round, and maybe of who paid; his heir is crowned. True if so (the usual fates don't follow).
	 */
	static boolean murdered(ServerLevel level, Faction faction, Folk ruler, DamageSource source) {
		Plot p = plotOn(ruler.getUUID());
		boolean byBlade = is(source.getEntity());
		boolean byPoison = p != null && p.venom > 0 && source.getEntity() == null;
		if (!byBlade && !byPoison) {
			return false;
		}
		if (p != null) {
			p.venom = 0;
			p.done = true;
			LINGERING.remove(p);
		}
		MinecraftServer server = level.getServer();
		String lord = ruler.role() == Folk.LORD ? "lord" : "king";
		KingdomEvents.announce(server, Component.translatable("assassin.jaymoskingdoms.news.murdered." + lord, ruler.getDisplayName(), faction.name())
			.withStyle(ChatFormatting.DARK_RED));
		Faction hirer = p != null && p.contract.hirer() >= 0 ? Factions.get(server, p.contract.hirer()) : null;
		if (hirer != null && level.getRandom().nextBoolean()) {
			expose(server, faction, hirer);
		} else if (p != null) {
			KingdomEvents.announce(server, Component.translatable("assassin.jaymoskingdoms.news.rumour." + p.contract.motive(), faction.name())
				.withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/** A marked player died: if the blade was at them, he's done; he slips off, and they're told the price was paid. */
	private static void playerDied(ServerLevel level, ServerPlayer player) {
		for (Plot p : PLOTS.values()) {
			if (p.victim.equals(player.getUUID()) && p.stage == Stage.FIGHT && level.getEntity(p.assassin) instanceof Folk a) {
				flee(level, a, p, level.getGameTime());
				Faction hirer = p.contract.hirer() >= 0 ? Factions.get(level.getServer(), p.contract.hirer()) : null;
				player.sendSystemMessage(Component.translatable(hirer != null ? "assassin.jaymoskingdoms.player.killed.hirer" : "assassin.jaymoskingdoms.player.killed",
					hirer != null ? hirer.name() : "").withStyle(ChatFormatting.DARK_RED));
			}
		}
	}

	// ---- for testing -------------------------------------------------------------------------------------------------------

	/** {@code /kingdom assassin [realm]} (ops): a blade sent at once for that realm's ruler, or for you. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("assassin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(c -> send(c, null))
			.then(Commands.argument("realm", StringArgumentType.word()).executes(c -> send(c, StringArgumentType.getString(c, "realm"))));
	}

	private static int send(CommandContext<CommandSourceStack> ctx, @Nullable String realm) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MinecraftServer server = ctx.getSource().getServer();
		RandomSource random = player.getRandom();
		long now = Factions.level(server).getGameTime();
		Contract c;
		if (realm == null) {
			c = contract(player.getStringUUID(), -1, "guild", now, random, true);
		} else {
			Faction f = Factions.byName(server, realm);
			if (f == null || !f.type().isRealm()) {
				ctx.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such", realm));
				return 0;
			}
			c = contract("realm:" + f.id(), -1, "guild", now, random, true);
		}
		replace(server, c);
		ctx.getSource().sendSuccess(() -> Component.translatable("assassin.jaymoskingdoms.command.sent"), false);
		return 1;
	}
}
