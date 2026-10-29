package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.360) Valor folk: villagers with a Valor of their own, who'll tell you how they earned it. Andy: "you should be able
 * to come across a villager and notice his, and he tells you about how he developed it and over how long". And his lore:
 * "Valor is more common because so many folk have encountered great danger and survived ... it isnt looked down upon at
 * all, rather the opposite, villagers who know no better will treat you like royalty if you can prove you have it."
 * <ul>
 * <li><b>Who</b> ({@link #step}): each grown MCA villager is weighed once: about one in {@value #COMMON_ODDS}, a guard one in
 * {@value #GUARD_ODDS}, an elder one in {@value #ELDER_ODDS}, with an origin, look, name and rank of their own (rulers:
 * {@link ValorKings#rulerRoll}).</li>
 * <li><b>Noticing it</b>: in a fight they coat up ({@link #tick}); a player who sees that may ask "Ask about their Valor"
 * in MCA's menu ({@link #tale}): how it woke, how old they were, how many winters before they could call it, their greatest
 * feat, its name, told in their temper's way. An elder (or one of Steeled rank) names a ruler they never beat and marks
 * them on a map, once ({@link #lead}). Two Valor holders near each other feel a pressure: a heartbeat.</li>
 * <li><b>Their Valor grows</b>: the foes they fell are feats, as yours are.</li>
 * <li><b>Sparring</b> ({@link #startSpar}): once told, ask them to spar. Last a minute or put them to a third of their
 * health and it's a feat, once per teacher; neither of you is hurt past a third.</li>
 * <li><b>Like royalty</b> ({@link #awe}): coat up where townsfolk without Valor see you, and each warms to you (MCA hearts,
 * the realm thinks better of you), once each.</li>
 * </ul>
 */
public final class ValorFolk {
	/** The odds (one in this) a grown villager, a guard, an elder has Valor. */
	static final int COMMON_ODDS = 20, GUARD_ODDS = 4, ELDER_ODDS = 8;
	/** Their Valor experience (least, most): common folk, guards, elders. */
	static final float COMMON_MIN = 5.0F, COMMON_MAX = 150.0F, GUARD_MIN = 40.0F, GUARD_MAX = 330.0F, ELDER_MIN = 120.0F, ELDER_MAX = 520.0F;
	/** How long after a blow they stay coated (ticks). */
	static final int CALM_TICKS = 200;
	/** How far a coated one is seen (blocks); how near two holders feel each other; how far townsfolk see you coated. */
	static final double SEE_REACH = 16.0, MEET_REACH = 6.0, AWE_REACH = 12.0;
	/** Like royalty: MCA hearts, and the realm's regard, per villager awed. */
	static final int AWE_HEARTS = 6, AWE_REPUTATION = 3;
	/** Sparring: how long, the hit that ends it (share of most health), how close for a blow, how often they strike (ticks), its harm. */
	static final int SPAR_TICKS = 1200, SPAR_STRIKE_EVERY = 20;
	static final float SPAR_FLOOR = 1.0F / 3.0F, SPAR_HARM = 2.0F, SPAR_HARM_PER_RANK = 1.0F;
	static final double STRIKE_REACH = 2.6, SPAR_LEAVE = 24.0;
	/** How many ways each piece of a tale is told. */
	static final int LINES = 2;

	public static final String TALE = "jaymoskingdoms_valor", SPAR = "jaymoskingdoms_valor_spar";
	static final String ROLLED = "jaymoskingdoms_valor_rolled";

	/** On a player: the Valor folk they've seen coated, and those who've told their tale, and those who marked them a lead. Saved. */
	static final AttachmentType<Set<UUID>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("valor_seen"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath());
	static final AttachmentType<Set<UUID>> TOLD = AttachmentRegistry.create(JaymosKingdoms.id("valor_told"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath());
	static final AttachmentType<Set<UUID>> LED = AttachmentRegistry.create(JaymosKingdoms.id("valor_led"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath());
	/** On a villager: the players whose Valor they've seen and been awed by. Saved. */
	static final AttachmentType<Set<UUID>> AWED = AttachmentRegistry.create(JaymosKingdoms.id("valor_awed"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET));

	/** Loaded holders who aren't players. */
	private static final Set<LivingEntity> HOLDERS = Collections.newSetFromMap(new WeakHashMap<>());
	/** When each holder last fought (game time). */
	private static final Map<LivingEntity, Long> FOUGHT = new WeakHashMap<>();
	/** A pressure felt: player → other holder → the day. */
	private static final Map<UUID, Map<UUID, Long>> FELT = new HashMap<>();

	/** A spar: the teacher, the player, its end (game time), the teacher's next blow. */
	private static final class Spar {
		final PathfinderMob teacher;
		final UUID player;
		final long until;
		long nextBlow;

		Spar(PathfinderMob teacher, UUID player, long until) {
			this.teacher = teacher;
			this.player = player;
			this.until = until;
		}
	}

	private static final List<Spar> SPARS = new ArrayList<>();

	private ValorFolk() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof Player) && entity instanceof LivingEntity living && Valor.has(living)) {
				HOLDERS.add(living);
			}
		});
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (!(killer instanceof Player) && killer instanceof LivingEntity holder && Valor.has(holder) && killed instanceof LivingEntity dead) {
				if (BossBars.boss(dead)) {
					Valor.feat(holder, Valor.Feat.GREAT_FOE, dead);
					ValorKings.felled(holder, dead);
				} else if (ValorKings.weakerThan(dead, holder)) {
					Valor.feat(holder, Valor.beast(dead) ? Valor.Feat.BEAST : Valor.Feat.STRONGER, dead);
				} else if (dead instanceof Enemy) {
					Valor.feat(holder, Valor.Feat.PROTECT, dead);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ValorFolk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDERS.clear();
			FOUGHT.clear();
			FELT.clear();
			SPARS.clear();
		});
	}

	static void track(LivingEntity holder) {
		if (!(holder instanceof Player)) {
			HOLDERS.add(holder);
		}
	}

	/** {@code holder} is fighting now: coated for a while. */
	static void fighting(LivingEntity holder) {
		FOUGHT.put(holder, holder.level().getGameTime());
		track(holder);
	}

	// ---- who has it --------------------------------------------------------------------------------------------------

	/** (from {@link Workers#step}) A grown MCA villager is weighed once for Valor; a ruler for theirs. */
	static void step(ServerLevel level, Enlisted e) {
		PathfinderMob mob = e.mob();
		if (mob.isBaby() || !McaCompat.isMca(mob)) {
			return;
		}
		if (Rulers.leaderRole(e.role())) {
			ValorKings.rulerRoll(level, mob);
		}
		if (mob.entityTags().contains(ROLLED)) {
			return;
		}
		mob.addTag(ROLLED);
		if (Valor.has(mob)) {
			return;
		}
		RandomSource r = level.getRandom();
		boolean guard = e.role() == Folk.GUARD || e.role() == Folk.ARCHER;
		boolean elder = CourtWizards.isElder(level, e);
		int odds = guard ? GUARD_ODDS : elder ? ELDER_ODDS : COMMON_ODDS;
		// (a Valor parent's child: twice as likely)
		for (UUID parent : McaCompat.parents(mob)) {
			if (level.getEntity(parent) instanceof LivingEntity p && Valor.has(p)) {
				odds = Math.max(1, odds / 2);
				break;
			}
		}
		if (r.nextInt(odds) != 0) {
			return;
		}
		float min = guard ? GUARD_MIN : elder ? ELDER_MIN : COMMON_MIN, max = guard ? GUARD_MAX : elder ? ELDER_MAX : COMMON_MAX;
		mob.setAttached(Valor.VALOR, born(level, mob, min + r.nextFloat() * (max - min)));
		track(mob);
	}

	/** A villager's Valor, as though it woke years ago: its origin, look, soul colour (by temper) and name. */
	static Valor.Data born(ServerLevel level, LivingEntity mob, float exp) {
		RandomSource r = level.getRandom();
		Valor.Origin origin = Valor.Origin.values()[r.nextInt(Valor.Origin.values().length)];
		Valor.Look look = Valor.favouredLook(origin, r);
		long now = level.getGameTime();
		Valor.Data data = new Valor.Data(exp, origin.ordinal(), look.ordinal(), -1, soul(mob, r), Valor.name(origin, look, r), false, now, now / Will.DAY,
			Map.of(), 0);
		if (data.rank() >= Valor.TEMPERED) {
			data = data.withBent(Valor.Bent.values()[r.nextInt(Valor.Bent.values().length)].ordinal());
		}
		return data;
	}

	/** The soul colour, leaning on temper: the hotheaded red, the steady green, the proud gold; else any. */
	static int soul(LivingEntity mob, RandomSource r) {
		Temperaments.Temperament t = Temperaments.of(mob);
		if (t != null && r.nextBoolean()) {
			return switch (t) {
				case HOTHEADED, SPITEFUL -> 1;
				case DUTIFUL, KINDHEARTED, CHEERFUL -> 0;
				case PROUD, GREEDY -> 2;
				case DEVOUT -> 5;
				case GLOOMY, SUSPICIOUS -> 6;
				case BRAVE -> 3;
				case TIMID -> 4;
			};
		}
		return r.nextInt(Valor.SOUL.length);
	}

	// ---- every tick --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tickCount = server.getTickCount();
		for (Iterator<LivingEntity> it = HOLDERS.iterator(); it.hasNext();) {
			LivingEntity holder = it.next();
			if (holder.isRemoved() || !holder.isAlive() || !(holder.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			Valor.Data data = Valor.of(holder);
			if (data == null) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			boolean fighting = !Downed.isDowned(holder) && (now - FOUGHT.getOrDefault(holder, -CALM_TICKS * 2L) < CALM_TICKS
				|| holder instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
				|| holder.getLastHurtByMob() != null && holder.tickCount - holder.getLastHurtByMobTimestamp() < CALM_TICKS);
			int packed = fighting ? Valor.REINFORCED | (data.rank() >= Valor.KINDLED ? Valor.ARMED : 0) | (ValorKings.coatOpen(data) ? Valor.KINGS : 0) | 0xFF00 : 0;
			if (holder.getAttachedOrElse(Valor.STATE, 0) != packed) {
				holder.setAttached(Valor.STATE, packed);
			}
			if (fighting && now % 4 == 0) {
				Valor.coatParticles(level, holder, data.rank() >= Valor.KINDLED, (packed & Valor.KINGS) != 0 ? data.soulColor() : data.lookKind().color);
			}
		}
		sparring(server);
		if (tickCount % 20 != 0) {
			return;
		}
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			if (!sp.isAlive() || sp.isSpectator() || !(sp.level() instanceof ServerLevel level)) {
				continue;
			}
			for (LivingEntity holder : HOLDERS) {
				if (holder.level() != level || holder.distanceToSqr(sp) > SEE_REACH * SEE_REACH) {
					continue;
				}
				if ((Valor.reinforced(holder) || Valor.armed(holder)) && sp.hasLineOfSight(holder)) {
					add(sp, SEEN, holder);
				}
				if (Valor.has(sp) && holder.distanceToSqr(sp) <= MEET_REACH * MEET_REACH) {
					felt(level, sp, holder);
				}
			}
			if (Valor.has(sp)) {
				for (ServerPlayer other : level.players()) {
					if (other != sp && Valor.has(other) && other.distanceToSqr(sp) <= MEET_REACH * MEET_REACH) {
						felt(level, sp, other);
					}
				}
				if (Valor.reinforced(sp) || Valor.armed(sp)) {
					awe(level, sp);
				}
			}
		}
	}

	/** Two holders near: {@code sp} feels the other's Valor, once a day each: a heartbeat in their ears and a shiver. */
	private static void felt(ServerLevel level, ServerPlayer sp, LivingEntity other) {
		long day = level.getGameTime() / Will.DAY;
		Map<UUID, Long> felt = FELT.computeIfAbsent(sp.getUUID(), id -> new HashMap<>());
		Long was = felt.get(other.getUUID());
		if (was != null && was == day) {
			return;
		}
		felt.put(other.getUUID(), day);
		sp.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.WARDEN_HEARTBEAT), SoundSource.PLAYERS, sp.getX(), sp.getEyeY(), sp.getZ(),
			1.0F, 0.75F, sp.getRandom().nextLong()));
		PowersCompat.shake(sp, 0.2F);
	}

	/** Townsfolk without Valor who see {@code sp} coated warm to them, once each: like royalty. */
	private static void awe(ServerLevel level, ServerPlayer sp) {
		for (PathfinderMob v : level.getEntitiesOfClass(PathfinderMob.class, sp.getBoundingBox().inflate(AWE_REACH), m -> m.isAlive() && McaCompat.isMca(m)
			&& !Valor.has(m) && !m.isBaby() && m.getTarget() != sp && !Downed.isDowned(m))) {
			Set<UUID> awed = v.getAttached(AWED);
			if (awed != null && awed.contains(sp.getUUID()) || !v.hasLineOfSight(sp)) {
				continue;
			}
			Set<UUID> next = awed == null ? new HashSet<>() : new HashSet<>(awed);
			next.add(sp.getUUID());
			v.setAttached(AWED, next);
			McaCompat.hearts(v, sp, AWE_HEARTS);
			if (v instanceof Worker w && w.factionId() >= 0) {
				Faction f = Factions.get(level.getServer(), w.factionId());
				if (f != null) {
					Reputation.change(sp, f, AWE_REPUTATION);
				}
			}
			Speech.say(level, v, Component.translatable("valor.jaymoskingdoms.awe." + level.getRandom().nextInt(4)), 0xFFE8C860, AWE_REACH);
		}
	}

	private static boolean holds(Player player, AttachmentType<Set<UUID>> type, Entity e) {
		Set<UUID> set = player.getAttached(type);
		return set != null && set.contains(e.getUUID());
	}

	private static void add(Player player, AttachmentType<Set<UUID>> type, Entity e) {
		if (!holds(player, type, e)) {
			Set<UUID> set = player.getAttached(type);
			Set<UUID> next = set == null ? new HashSet<>() : new HashSet<>(set);
			next.add(e.getUUID());
			player.setAttached(type, next);
		}
	}

	// ---- the tale ----------------------------------------------------------------------------------------------------

	/** Whether {@code player} may ask {@code villager} about their Valor: they've seen it. */
	public static boolean canAsk(ServerPlayer player, Villager villager) {
		return Valor.has(villager) && holds(player, SEEN, villager);
	}

	/** {@code player} asked {@code villager} about their Valor (MCA's menu): their tale, and maybe a lead. */
	public static Component tale(ServerPlayer player, Villager villager) {
		Valor.Data data = Valor.of(villager);
		if (data == null) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.valor.none");
		}
		if (Recollections.refuses(villager, player)) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.valor.refused");
		}
		add(player, TOLD, villager);
		// (the same tale each time: its numbers are theirs)
		long seed = villager.getUUID().getMostSignificantBits() ^ villager.getUUID().getLeastSignificantBits();
		RandomSource r = RandomSource.create(seed);
		int age = 14 + r.nextInt(17), winters = 1 + Math.min(14, (int) (data.exp() / 40.0F) + r.nextInt(4));
		String temper = temper(Temperaments.of(villager));
		String origin = data.originKind().key();
		Component tale = Component.empty()
			.append(Component.translatable("dialogue.chat.jaymoskingdoms.valor.open." + temper + "." + r.nextInt(LINES)))
			.append(" ").append(Component.translatable("dialogue.chat.jaymoskingdoms.valor.woke." + origin + "." + r.nextInt(LINES), age))
			.append(" ").append(Component.translatable("dialogue.chat.jaymoskingdoms.valor.winters." + r.nextInt(LINES), winters));
		Component feat = greatest(data);
		if (feat != null) {
			tale = tale.copy().append(" ").append(Component.translatable("dialogue.chat.jaymoskingdoms.valor.feat." + r.nextInt(LINES), feat));
		}
		if (ValorKings.open(data)) {
			tale = tale.copy().append(" ").append(Component.translatable("dialogue.chat.jaymoskingdoms.valor.kings", data.greatFoes()));
		}
		tale = tale.copy().append(" ").append(Component.translatable("dialogue.chat.jaymoskingdoms.valor.named", data.name()));
		if (lead(player, villager, data)) {
			tale = tale.copy().append(" ").append(Component.translatable("dialogue.chat.jaymoskingdoms.valor.lead"));
		}
		return tale;
	}

	/** How a temper tells it: boasting, shrugging it off, or not liking to talk of it. */
	static String temper(Temperaments.@Nullable Temperament t) {
		if (t == null) {
			return "plain";
		}
		return switch (t) {
			case PROUD, HOTHEADED, GREEDY, BRAVE -> "proud";
			case KINDHEARTED, CHEERFUL, DEVOUT, TIMID -> "humble";
			case GLOOMY, SUSPICIOUS, SPITEFUL -> "grim";
			case DUTIFUL -> "plain";
		};
	}

	/** Their greatest feat's foe: a great foe if any, else the strongest kind they've beaten; null if none on record. */
	static @Nullable Component greatest(Valor.Data data) {
		String best = null;
		int rank = -1;
		for (String key : data.feats().keySet()) {
			int colon = key.indexOf(':');
			if (colon < 0) {
				continue;
			}
			Valor.Feat feat = Valor.byKey(key.substring(0, colon));
			int r = feat == Valor.Feat.GREAT_FOE ? 3 : feat == Valor.Feat.STRONGER || feat == Valor.Feat.BEAST ? 2 : feat == Valor.Feat.CASTER ? 1 : 0;
			if (r > rank) {
				rank = r;
				best = key.substring(colon + 1);
			}
		}
		if (best == null) {
			return null;
		}
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(best);
		return id == null ? Component.literal(best)
			: net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(t -> (Component) t.getDescription()).orElse(Component.literal(best));
	}

	/**
	 * An elder (or one of Steeled rank) names a ruler they never beat, and marks their seat on a map for {@code player},
	 * once: the nearest other realm's. True if they gave one.
	 */
	static boolean lead(ServerPlayer player, Villager villager, Valor.Data data) {
		if (holds(player, LED, villager) || !(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean elder = villager instanceof Aging a && CourtWizards.isElder(level, a);
		if (!elder && data.rank() < Valor.STEELED) {
			return false;
		}
		int own = villager instanceof Worker w ? w.factionId() : -1;
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(level.getServer())) {
			if (f.fallen() || f.id() == own || !f.type().isRealm() || f.leader().isEmpty()) {
				continue;
			}
			double d = f.seat().distSqr(villager.blockPosition());
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		if (best == null) {
			return false;
		}
		add(player, LED, villager);
		BlockPos at = best.seat();
		ItemStack map = MapItem.create(level, at.getX(), at.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, at, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.valor_lead", best.name()));
		map.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.valor_lead.lore", villager.getDisplayName()))));
		if (!player.getInventory().add(map)) {
			player.spawnAtLocation(level, map);
		}
		return true;
	}

	// ---- sparring ----------------------------------------------------------------------------------------------------

	/** Whether {@code player} may ask {@code villager} to spar: both have Valor, they've told their tale, it's not done yet, no fight on. */
	public static boolean canSpar(ServerPlayer player, Villager villager) {
		Valor.Data mine = Valor.of(player);
		if (mine == null || !Valor.has(villager) || !holds(player, TOLD, villager) || mine.feats().containsKey(sparKey(villager))
			|| villager.getTarget() != null || Downed.isDowned(villager)) {
			return false;
		}
		for (Spar s : SPARS) {
			if (s.teacher == villager || s.player.equals(player.getUUID())) {
				return false;
			}
		}
		return true;
	}

	static String sparKey(Entity teacher) {
		return "spar:" + teacher.getUUID();
	}

	/** The spar begins: they square up and come at you, coated. */
	public static void startSpar(ServerPlayer player, Villager villager) {
		if (!canSpar(player, villager)) {
			return;
		}
		ServerLevel level = player.level();
		Spar spar = new Spar(villager, player.getUUID(), level.getGameTime() + SPAR_TICKS);
		spar.nextBlow = level.getGameTime() + 40;
		SPARS.add(spar);
		fighting(villager);
		Speech.sayTo(level, villager, player, Component.translatable("valor.jaymoskingdoms.spar.start"), 0xFFE8C860);
	}

	/** Whether {@code a} and {@code b} are sparring (a blow between them is no crime). */
	public static boolean sparring(Entity a, Entity b) {
		return !SPARS.isEmpty() && sparOf(a, b) != null;
	}

	private static @Nullable Spar sparOf(Entity a, Entity b) {
		for (Spar s : SPARS) {
			if (s.teacher == a && s.player.equals(b.getUUID()) || s.teacher == b && s.player.equals(a.getUUID())) {
				return s;
			}
		}
		return null;
	}

	/**
	 * A blow between sparring partners ({@code LivingEntityHurtMixin}, last): it never takes either past a third of their
	 * health; the blow that would is the spar's end.
	 */
	public static float spar(LivingEntity target, DamageSource source, float amount) {
		if (SPARS.isEmpty() || amount <= 0.0F || source.getEntity() == null) {
			return amount;
		}
		Spar spar = sparOf(target, source.getEntity());
		if (spar == null) {
			return amount;
		}
		float floor = target.getMaxHealth() * SPAR_FLOOR;
		if (target.getHealth() - amount > floor) {
			return amount;
		}
		end(spar, target == spar.teacher);
		return Math.max(0.0F, target.getHealth() - floor);
	}

	private static void sparring(MinecraftServer server) {
		for (Iterator<Spar> it = SPARS.iterator(); it.hasNext();) {
			Spar s = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(s.player);
			PathfinderMob teacher = s.teacher;
			if (player == null || teacher.isRemoved() || !teacher.isAlive() || !player.isAlive() || Downed.isDowned(player) || Downed.isDowned(teacher)
				|| player.level() != teacher.level() || teacher.distanceTo(player) > SPAR_LEAVE) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			if (now >= s.until) {
				// lasted the minute
				it.remove();
				won(level, player, teacher);
				continue;
			}
			fighting(teacher);
			if (now >= s.nextBlow) {
				s.nextBlow = now + SPAR_STRIKE_EVERY;
				Valor.Data data = Valor.of(teacher);
				float harm = SPAR_HARM + SPAR_HARM_PER_RANK * (data == null ? 0 : data.rank());
				hit(level, teacher, player, harm);
			} else {
				teacher.getNavigation().moveTo(player, 1.0);
				teacher.getLookControl().setLookAt(player);
			}
		}
	}

	/** {@code spar} ends: whoever fell to a third lost. */
	private static void end(Spar spar, boolean teacherLost) {
		SPARS.remove(spar);
		if (!(spar.teacher.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(spar.player);
		if (player == null) {
			return;
		}
		if (teacherLost) {
			won(level, player, spar.teacher);
		} else {
			spar.teacher.getNavigation().stop();
			Speech.sayTo(level, spar.teacher, player, Component.translatable("valor.jaymoskingdoms.spar.lost"), 0xFFE8C860);
		}
	}

	private static void won(ServerLevel level, ServerPlayer player, PathfinderMob teacher) {
		teacher.getNavigation().stop();
		teacher.setTarget(null);
		FOUGHT.remove(teacher);
		Valor.featKeyed(player, Valor.Feat.SPAR, sparKey(teacher));
		Speech.sayTo(level, teacher, player, Component.translatable("valor.jaymoskingdoms.spar.won"), 0xFFE8C860);
		McaCompat.hearts(teacher, player, AWE_HEARTS);
	}

	/** {@code mob} closes on {@code foe} and strikes when in reach (a ruler standing their ground). */
	static void strike(ServerLevel level, PathfinderMob mob, LivingEntity foe) {
		mob.getLookControl().setLookAt(foe);
		if (mob.distanceTo(foe) > STRIKE_REACH) {
			mob.getNavigation().moveTo(foe, 1.1);
			return;
		}
		mob.getNavigation().stop();
		if (mob.tickCount % SPAR_STRIKE_EVERY == 0) {
			Valor.Data data = Valor.of(mob);
			float harm = (float) Math.max(4.0, mob.getAttributes().hasAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
				? mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) : 4.0) + (data == null ? 0 : data.rank());
			hit(level, mob, foe, harm);
		}
	}

	private static void hit(ServerLevel level, PathfinderMob mob, LivingEntity foe, float harm) {
		if (mob.distanceTo(foe) > STRIKE_REACH) {
			mob.getNavigation().moveTo(foe, 1.0);
			return;
		}
		mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		foe.hurtServer(level, level.damageSources().mobAttack(mob), harm);
	}
}
