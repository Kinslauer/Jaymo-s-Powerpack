package com.jaymoskingdoms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The people and creatures of a tale ({@link Tales}) in the world. Each of a tale's cast is set out at their place when
 * a player in the tale first comes near it, named and dressed as the tale's file says, and kept there. They can't be
 * hurt until the tale comes to fighting them. A person who's beaten yields (downed, or badly hurt): they stop, they
 * plead, and the player kills them or lets them go; a creature only dies. When the tale is over its people go their
 * ways, unless its ending has one join the player. Also the ones who come to find a player: the runner with a letter,
 * and whoever an ending sends back days later with thanks, a warning, an offer, or a grudge.
 */
final class TaleCast {
	// ================================================================================================================
	// Tuning
	/** What a fighter or a beast has when its tale doesn't say: health, blow, armour; a foe's men have this share of his health. */
	static final double HEALTH = 40.0, DAMAGE = 5.0, ARMOR = 4.0, MEN_SHARE = 0.6;
	/** A person who's fought yields at this share of their health (unless the step says), or when they go down. */
	static final double YIELD_AT = 0.3;
	/** A yielded foe left this far behind, or this long (ticks), has been let go. */
	static final double WALK_AWAY = 40.0;
	static final int MERCY_TICKS = 20 * 180;
	/** Killing someone who'd yielded is seen by those this near. */
	static final double WITNESS_RANGE = 24.0;
	/** A tale's people keep within this many blocks of where they were set out; one who's gone missing is back when a player is this near. */
	static final int KEEP_WITHIN = 6, BACK_WITHIN = 24;
	/** People of a tale that's over leave once no player is this near, or after this long (ticks). */
	static final double LEAVE_UNSEEN = 20.0;
	static final int LEAVE_TICKS = 20 * 120;
	/** A visitor (runner, or one come back) starts this far from the player, has this long to reach them, and this long to walk off after. */
	static final int VISIT_NEAR = 18, VISIT_FAR = 26, VISIT_TICKS = 20 * 120, DEPART_TICKS = 20 * 20;
	/** How near a visitor comes before speaking. */
	static final double VISIT_REACH = 3.5;
	/** One come back for revenge brings this many, and gives up after this long (ticks). */
	static final int REVENGE_MEN = 2, REVENGE_TICKS = 20 * 300;
	/** Someone a player is talking with stands still this long at most (ticks). */
	static final int HOLD_TICKS = 20 * 120;
	/** How often (ticks) visitors and held talkers are looked at, and the tale's people kept in place. */
	static final int TICKS = 10, KEEP_TICKS = 100;
	/** How many ended tales are remembered (so their people leave when their chunks next load). */
	static final int ENDED_MAX = 300;

	/**
	 * On one of a tale's people: the tale's run ({@code instance}), their {@code role} in it (one of a foe's men: the
	 * foe's role and a {@code ~}), where they were set out, whether they're fighting, whether they've yielded, whether
	 * their tale is over, and when (game time) they yielded or were let go their way.
	 */
	record Cast(String instance, String role, long anchor, boolean fight, boolean yielded, boolean over, long since) {
		static final Codec<Cast> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("instance").forGetter(Cast::instance),
				Codec.STRING.fieldOf("role").forGetter(Cast::role),
				Codec.LONG.optionalFieldOf("anchor", 0L).forGetter(Cast::anchor),
				Codec.BOOL.optionalFieldOf("fight", false).forGetter(Cast::fight),
				Codec.BOOL.optionalFieldOf("yielded", false).forGetter(Cast::yielded),
				Codec.BOOL.optionalFieldOf("over", false).forGetter(Cast::over),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Cast::since))
			.apply(i, Cast::new));

		Cast fighting(boolean fight) {
			return new Cast(instance, role, anchor, fight, yielded, over, since);
		}

		Cast yielding(long now) {
			return new Cast(instance, role, anchor, false, true, over, now);
		}

		Cast done(long now) {
			return new Cast(instance, role, anchor, false, yielded, true, now);
		}
	}

	static final AttachmentType<Cast> CAST = AttachmentRegistry.create(JaymosKingdoms.id("tale_cast"), builder -> builder.persistent(Cast.CODEC));
	/** On the overworld: the runs that are over (their people leave when next seen). */
	static final AttachmentType<List<String>> ENDED = AttachmentRegistry.create(JaymosKingdoms.id("tales_ended"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	/** The tale's people loaded now. */
	private static final Set<Mob> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Who's standing still for a player's talk (them → the player, until when). */
	private static final Map<Mob, Hold> HELD = new IdentityHashMap<>();
	/** Who's on their way to a player. */
	private static final List<Visit> VISITS = new ArrayList<>();

	private record Hold(ServerPlayer player, int until) {
	}

	private static final int LETTER = 0, THANKS = 1, RECRUIT = 2, REVENGE = 3;
	private static final int COMING = 0, ASKING = 1, LEAVING = 2;

	private static final class Visit {
		final Mob mob;
		final UUID player;
		final int kind;
		final ItemStack letter;
		final @Nullable Runnable delivered;
		final Tales.@Nullable Return back;
		int state = COMING;
		int until;

		Visit(Mob mob, UUID player, int kind, ItemStack letter, @Nullable Runnable delivered, Tales.@Nullable Return back, int until) {
			this.mob = mob;
			this.player = player;
			this.kind = kind;
			this.letter = letter;
			this.delivered = delivered;
			this.back = back;
			this.until = until;
		}
	}

	private TaleCast() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TaleCast::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(TaleCast::died);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			Cast c = entity instanceof Mob ? entity.getAttached(CAST) : null;
			if (c == null) {
				return;
			}
			Mob mob = (Mob)entity;
			// (a visitor left over from before a restart, or one of a tale that ended while they were away)
			boolean stray = c.instance().startsWith("visit:") && VISITS.stream().noneMatch(v -> v.mob == mob);
			if (!c.over() && (stray || Factions.level(level.getServer()).getAttachedOrElse(ENDED, List.of()).contains(c.instance()))) {
				mob.setAttached(CAST, c.done(level.getGameTime()));
			}
			LOADED.add(mob);
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				LOADED.remove(mob);
				HELD.remove(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			HELD.clear();
			VISITS.clear();
		});
	}

	static @Nullable Cast cast(Entity entity) {
		return entity.getAttached(CAST);
	}

	/** {@code who} is one of a tale's people who's been beaten and has yielded. */
	static boolean yielded(Entity who) {
		Cast c = who.getAttached(CAST);
		return c != null && c.yielded();
	}

	/**
	 * ({@link McaCompat#foe}) Whom a tale's person fights: the players of their tale, once it's come to a fight, and no
	 * other player; and nobody's guard goes for a tale's people. Null: not for the tale to say.
	 */
	static @Nullable Boolean foe(LivingEntity guard, LivingEntity target) {
		Cast c = guard.getAttached(CAST);
		if (c == null) {
			return target.getAttached(CAST) != null ? Boolean.FALSE : null;
		}
		if (target instanceof ServerPlayer p) {
			return c.fight() && against(c, p);
		}
		return target.getAttached(CAST) != null ? Boolean.FALSE : null;
	}

	private static boolean against(Cast c, ServerPlayer p) {
		return !p.isCreative() && !p.isSpectator() && (Tales.in(p, c.instance()) || c.instance().equals("visit:" + p.getUUID()));
	}

	// ---- setting out -----------------------------------------------------------------------------------------------------

	private static @Nullable VillagerProfession profession(String job) {
		for (String namespace : List.of("minecraft", "mca")) {
			Identifier id = Identifier.tryParse(namespace + ":" + job);
			VillagerProfession p = id == null ? null : BuiltInRegistries.VILLAGER_PROFESSION.getOptional(id).orElse(null);
			if (p != null) {
				return p;
			}
		}
		return null;
	}

	private static void attribute(Mob mob, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static void gear(Mob mob, EquipmentSlot slot, String id) {
		Identifier key = id.isEmpty() ? null : Identifier.tryParse(id);
		Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
		if (item != null) {
			mob.setItemSlot(slot, new ItemStack(item));
			mob.setDropChance(slot, 0.0F);
		}
	}

	/**
	 * One of a tale's cast, made as {@code body} (their part of the tale's file) says and put at {@code at}: a townsperson,
	 * an armed person, or a creature; {@code man}: one of the foe's men instead of the foe. Null if they couldn't be made.
	 */
	static @Nullable Mob spawn(ServerLevel level, String instance, String role, JsonObject body, String name, BlockPos at, boolean man) {
		String own = Tale.string(body, "body", "villager");
		String kind = man ? Tale.string(body, "men_body", own.equals("villager") ? "fighter" : own) : own;
		Vec3 pos = Vec3.atBottomCenterOf(at);
		Mob mob;
		if (kind.equals("villager") || kind.equals("fighter")) {
			boolean fighter = kind.equals("fighter");
			Boolean female = body.has("female") && !man ? Tale.flag(body, "female") : null;
			mob = McaCompat.talePerson(level, pos, fighter ? null : profession(Tale.string(body, "job", "none")), female, man ? "" : name, fighter);
			if (mob == null && fighter) {
				// (without MCA: a vindicator, who stands until it comes to a fight)
				mob = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
				if (mob != null) {
					mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
					mob.setNoAi(true);
					if (!man && !name.isEmpty()) {
						mob.setCustomName(Component.literal(name));
					}
					level.addFreshEntity(mob);
				}
			}
		} else {
			Identifier id = Identifier.tryParse(kind);
			EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
			Entity made = type == null ? null : type.create(level, EntitySpawnReason.EVENT);
			if (!(made instanceof Mob m)) {
				return null;
			}
			mob = m;
			mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			if (!man && !name.isEmpty()) {
				mob.setCustomName(Component.literal(name));
			}
			level.addFreshEntity(mob);
		}
		if (mob == null) {
			return null;
		}
		if (!kind.equals("villager")) {
			attribute(mob, Attributes.MAX_HEALTH, Tale.decimal(body, "health", HEALTH) * (man ? MEN_SHARE : 1.0));
			mob.setHealth(mob.getMaxHealth());
			attribute(mob, Attributes.ATTACK_DAMAGE, Tale.decimal(body, "damage", DAMAGE));
			attribute(mob, Attributes.ARMOR, Tale.decimal(body, "armor", ARMOR));
		}
		gear(mob, EquipmentSlot.MAINHAND, Tale.string(body, "hand", ""));
		if (!man) {
			gear(mob, EquipmentSlot.HEAD, Tale.string(body, "head", ""));
		}
		gear(mob, EquipmentSlot.CHEST, Tale.string(body, "chest", ""));
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.setPermanentlyInvulnerable(true);
		mob.setAttached(CAST, new Cast(instance, man ? role + "~" : role, at.asLong(), false, false, false, 0L));
		LOADED.add(mob);
		return mob;
	}

	private static @Nullable Mob find(ServerLevel level, @Nullable String id) {
		if (id == null || id.isEmpty()) {
			return null;
		}
		try {
			return level.getEntity(UUID.fromString(id)) instanceof Mob m && m.isAlive() ? m : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static double flat(Entity e, BlockPos at) {
		double dx = at.getX() + 0.5 - e.getX(), dz = at.getZ() + 0.5 - e.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static @Nullable Mob setOut(ServerLevel level, ServerPlayer player, Tales.Run run, String role, JsonObject body, BlockPos ground) {
		BlockPos at = Construction.standable(level, ground.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2), 3);
		Mob mob = spawn(level, run.instance, role, body, run.names.getOrDefault(role, ""), at, false);
		if (mob == null) {
			return null;
		}
		String id = mob.getUUID().toString();
		Tales.each(player, run, r -> r.cast.put(role, id));
		for (int i = 0; i < Tale.number(body, "men", 0); i++) {
			spawn(level, run.instance, role, body, "", Construction.standable(level, at.offset(level.getRandom().nextInt(7) - 3, 0, level.getRandom().nextInt(7) - 3), 3), true);
		}
		return mob;
	}

	/**
	 * ({@link Tales}, each second, for a run's open step) The tale's people whose places {@code player} has come near are
	 * set out; the one the step is about is there (again, if they went missing); and a fight's turns are taken: it
	 * begins, the foe yields, the foe is let go by being left.
	 */
	static void tend(ServerLevel level, ServerPlayer player, Tales.Run run, Tale tale, JsonObject step, String kind) {
		for (Map.Entry<String, JsonElement> e : Tale.object(tale.json, "cast").entrySet()) {
			if (!e.getValue().isJsonObject() || run.cast.containsKey(e.getKey())) {
				continue;
			}
			JsonObject body = e.getValue().getAsJsonObject();
			String key = Tale.string(body, "at", "");
			if (key.isEmpty() || flat(player, Tales.place(level, run, tale, key)) > Tales.SET_OUT_RANGE) {
				continue;
			}
			BlockPos ground = Tales.ground(level, run, tale, key);
			if (ground != null) {
				setOut(level, player, run, e.getKey(), body, ground);
			}
		}
		String who = Tale.pickString(step, "who", c -> Tales.holds(player, run, c), "");
		JsonObject body = who.isEmpty() || who.equals("giver") ? null : tale.role(who);
		if (body == null || !run.cast.containsKey(who)) {
			return;
		}
		Mob mob = find(level, run.cast.get(who));
		if (mob == null) {
			// (gone missing: killed while nobody was by, or lost with their chunk. They're there again when the player is.)
			String key = Tale.string(body, "at", "");
			BlockPos ground = key.isEmpty() ? null : Tales.ground(level, run, tale, key);
			if (ground != null && flat(player, ground) <= BACK_WITHIN && level.isPositionEntityTicking(ground)) {
				int missed = parse(run.scratch.getOrDefault("missed", "0")) + 1;
				run.scratch.put("missed", Integer.toString(missed));
				if (missed >= 3) {
					run.scratch.remove("missed");
					setOut(level, player, run, who, body, ground);
				}
			}
			return;
		}
		if (kind.equals("foe")) {
			fight(level, player, run, step, mob);
		}
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ---- fighting --------------------------------------------------------------------------------------------------------

	private static List<Mob> men(ServerLevel level, Mob foe, Cast c) {
		return level.getEntitiesOfClass(Mob.class, foe.getBoundingBox().inflate(48.0), m -> {
			Cast o = m.getAttached(CAST);
			return m != foe && m.isAlive() && o != null && o.instance().equals(c.instance()) && o.role().equals(c.role() + "~");
		});
	}

	private static void rouse(Mob mob, Cast c, ServerPlayer at) {
		mob.setAttached(CAST, c.fighting(true));
		mob.setPermanentlyInvulnerable(false);
		mob.setNoAi(false);
		mob.setTarget(at);
	}

	/** {@code mob} stops fighting: no target, by its goals or its brain. */
	private static void calm(Mob mob) {
		mob.setTarget(null);
		if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
		mob.getNavigation().stop();
	}

	private static void fight(ServerLevel level, ServerPlayer player, Tales.Run run, JsonObject step, Mob foe) {
		Cast c = foe.getAttached(CAST);
		if (c == null) {
			return;
		}
		long now = level.getGameTime();
		if (!c.fight() && !c.yielded()) {
			if (foe.distanceToSqr(player) > 24.0 * 24.0) {
				return;
			}
			rouse(foe, c, player);
			for (Mob man : men(level, foe, c)) {
				Cast mc = man.getAttached(CAST);
				if (mc != null) {
					rouse(man, mc, player);
				}
			}
			String taunt = Tale.pickString(step, "taunt", cond -> Tales.holds(player, run, cond), "");
			if (!taunt.isEmpty()) {
				Speech.tell(level, foe, player, Tales.text(player, run, taunt), Speech.RED);
			}
			return;
		}
		if (c.fight()) {
			boolean person = foe instanceof Villager || foe.getType() == EntityTypes.VINDICATOR;
			double yieldAt = Tale.decimal(step, "yield_at", YIELD_AT);
			if (step.has("spared") && person && (Downed.isDowned(foe) || foe.getHealth() <= foe.getMaxHealth() * yieldAt)) {
				foe.setAttached(CAST, c.yielding(now));
				calm(foe);
				for (Mob man : men(level, foe, c)) {
					Cast mc = man.getAttached(CAST);
					if (mc != null) {
						man.setAttached(CAST, mc.fighting(false));
						calm(man);
					}
				}
				String plea = Tale.pickString(step, "plea", cond -> Tales.holds(player, run, cond), "");
				Speech.tell(level, foe, player, plea.isEmpty() ? Component.translatable("tale.jaymoskingdoms.plea") : Tales.text(player, run, plea), Speech.WHITE);
				return;
			}
			if (Rulers.target(foe) == null && !player.isCreative() && !Downed.isDowned(player) && foe.distanceToSqr(player) < 32.0 * 32.0) {
				foe.setTarget(player);
			}
			for (Mob man : men(level, foe, c)) {
				if (Rulers.target(man) == null && !player.isCreative() && !Downed.isDowned(player)) {
					man.setTarget(player);
				}
			}
			return;
		}
		// yielded: left behind, or left alone long enough, they've been let go
		if (foe.distanceToSqr(player) > WALK_AWAY * WALK_AWAY || now - c.since() > MERCY_TICKS) {
			Tales.foeDone(player, run, foe, false);
		}
	}

	private static void died(LivingEntity entity, DamageSource source) {
		Cast c = entity.getAttached(CAST);
		if (c == null || c.role().endsWith("~") || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			Tales.Run run = Tales.run(p, c.instance());
			Tale tale = run == null ? null : Tale.get(run.tale);
			JsonObject step = tale == null ? null : tale.step(run.step);
			if (step != null && Tale.string(step, "do", "").equals("foe") && Tales.is(run, Tale.pickString(step, "who", cond -> Tales.holds(p, run, cond), ""), entity)) {
				// (once: the tale moves on for everyone sharing it)
				Tales.foeDone(p, run, entity, true);
				return;
			}
		}
	}

	// ---- when the tale is over ----------------------------------------------------------------------------------------------

	/** {@code run} is over: its people go their ways (now, the ones loaded; the rest when they're next seen). */
	static void over(ServerLevel level, Tales.Run run, @Nullable String keep) {
		ServerLevel world = Factions.level(level.getServer());
		List<String> ended = new ArrayList<>(world.getAttachedOrElse(ENDED, List.of()));
		ended.remove(run.instance);
		ended.add(run.instance);
		while (ended.size() > ENDED_MAX) {
			ended.remove(0);
		}
		world.setAttached(ENDED, List.copyOf(ended));
		for (Mob mob : new ArrayList<>(LOADED)) {
			Cast c = mob.getAttached(CAST);
			if (c != null && !c.over() && c.instance().equals(run.instance)) {
				mob.setAttached(CAST, c.done(level.getGameTime()));
				calm(mob);
			}
		}
	}

	/** An ending has {@code run}'s {@code role} join {@code player}: theirs from now on, and no longer the tale's. */
	static void join(ServerLevel level, ServerPlayer player, Tales.Run run, String role) {
		Mob mob = find(level, run.cast.get(role));
		Tale tale = Tale.get(run.tale);
		JsonObject body = tale == null ? null : tale.role(role);
		if (mob == null && body != null) {
			// (they weren't by: they catch the player up)
			BlockPos at = visitSpot(level, player, 6, 10);
			mob = at == null ? null : spawn(level, run.instance, role, body, run.names.getOrDefault(role, ""), at, false);
		}
		if (mob != null) {
			joined(mob, player);
		}
	}

	private static void joined(Mob mob, ServerPlayer player) {
		mob.removeAttached(CAST);
		LOADED.remove(mob);
		mob.setPermanentlyInvulnerable(false);
		mob.setNoAi(false);
		calm(mob);
		McaCompat.swear(mob, player);
	}

	// ---- visitors -----------------------------------------------------------------------------------------------------------

	private static boolean visiting(ServerPlayer player) {
		for (Visit v : VISITS) {
			if (v.player.equals(player.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** Whether someone could walk up to {@code player} now: by day, under the sky, on their feet, and nobody else already coming. */
	static boolean canVisit(ServerLevel level, ServerPlayer player) {
		return player.level() == level && level == Factions.level(level.getServer()) && player.isAlive() && !player.isSpectator() && !Downed.isDowned(player)
			&& !player.isSleeping() && player.getVehicle() == null && level.isBrightOutside() && level.canSeeSky(player.blockPosition()) && !visiting(player);
	}

	/** Open ground between {@code least} and {@code most} blocks from {@code player}, under the sky; null if there's none just now. */
	private static @Nullable BlockPos visitSpot(ServerLevel level, ServerPlayer player, int least, int most) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 12; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			int d = least + random.nextInt(most - least + 1);
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(player.getX() + Math.cos(a) * d, player.getY(), player.getZ() + Math.sin(a) * d));
			if (level.isLoaded(top) && level.getFluidState(top).isEmpty() && level.getFluidState(top.below()).isEmpty() && Math.abs(top.getY() - player.getBlockY()) <= 8
				&& !level.getBlockState(top.below()).getCollisionShape(level, top.below()).isEmpty()) {
				return top;
			}
		}
		return null;
	}

	/**
	 * A runner sets out to find {@code player} with {@code letter}: on foot, from a little way off. {@code delivered} is
	 * run when it's handed over. Nothing happens if there's nowhere for them to come from just now (ask again later).
	 */
	static void runner(ServerLevel level, ServerPlayer player, ItemStack letter, Runnable delivered) {
		BlockPos at = visiting(player) ? null : visitSpot(level, player, VISIT_NEAR, VISIT_FAR);
		JsonObject body = new JsonObject();
		body.addProperty("body", "villager");
		Mob mob = at == null ? null : spawn(level, "visit:" + player.getUUID(), "runner", body, "", at, false);
		if (mob != null) {
			VISITS.add(new Visit(mob, player.getUUID(), LETTER, letter, delivered, null, level.getServer().getTickCount() + VISIT_TICKS));
		}
	}

	/** Someone from a tale that's over comes to find {@code player}, as {@code back} says. False if they couldn't just now. */
	static boolean comeBack(ServerLevel level, ServerPlayer player, Tales.Return back) {
		BlockPos at = visiting(player) ? null : visitSpot(level, player, VISIT_NEAR, VISIT_FAR);
		if (at == null) {
			return false;
		}
		JsonObject body;
		try {
			body = JsonParser.parseString(back.body()).getAsJsonObject();
		} catch (RuntimeException e) {
			return true;
		}
		boolean revenge = back.as().equals("revenge");
		Mob mob = spawn(level, "visit:" + player.getUUID(), "back", body, back.name(), at, false);
		if (mob == null) {
			return false;
		}
		int tick = level.getServer().getTickCount();
		if (revenge) {
			Cast c = mob.getAttached(CAST);
			rouse(mob, c, player);
			for (int i = 0; i < REVENGE_MEN; i++) {
				Mob man = spawn(level, "visit:" + player.getUUID(), "back", body, "", Construction.standable(level, at.offset(i * 2 - 1, 0, 1), 3), true);
				if (man != null) {
					rouse(man, man.getAttached(CAST), player);
				}
			}
		}
		VISITS.add(new Visit(mob, player.getUUID(), revenge ? REVENGE : back.as().equals("recruit") ? RECRUIT : THANKS, ItemStack.EMPTY, null, back,
			tick + (revenge ? REVENGE_TICKS : VISIT_TICKS)));
		return true;
	}

	private static void come(Mob mob, ServerPlayer player) {
		if (!(mob instanceof Villager v) || !McaCompat.follow(v, player)) {
			mob.getNavigation().moveTo(player, 1.1);
		}
	}

	private static void depart(ServerLevel level, Visit v, int tick) {
		v.state = LEAVING;
		v.until = tick + DEPART_TICKS;
		if (v.mob instanceof Villager villager) {
			McaCompat.stopFollowing(villager);
		}
		Cast c = v.mob.getAttached(CAST);
		if (c != null) {
			v.mob.setAttached(CAST, c.done(level.getGameTime()));
			for (Mob man : men(level, v.mob, c)) {
				Cast mc = man.getAttached(CAST);
				if (mc != null) {
					man.setAttached(CAST, mc.done(level.getGameTime()));
					calm(man);
				}
			}
		}
		calm(v.mob);
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	private static void arrive(ServerLevel level, Visit v, ServerPlayer player, int tick) {
		Mob mob = v.mob;
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		if (v.kind == LETTER) {
			Speech.tell(level, mob, player, Component.translatable("tale.jaymoskingdoms.runner." + level.getRandom().nextInt(3)), Speech.WHITE);
			give(player, v.letter.copy());
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 0.9F);
			if (v.delivered != null) {
				v.delivered.run();
			}
			depart(level, v, tick);
			return;
		}
		Tales.Return back = v.back;
		Component say = back == null || back.say().isEmpty() ? Component.translatable("tale.jaymoskingdoms.back." + (v.kind == RECRUIT ? "recruit" : "thanks"))
			: Component.literal(back.say());
		if (v.kind == RECRUIT) {
			v.state = ASKING;
			v.until = tick + HOLD_TICKS;
			if (mob instanceof Villager villager) {
				McaCompat.stopFollowing(villager);
			}
			hold(mob, player);
			Menus.ask(player, mob, say, List.of(
				Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.back.yes"), p -> {
					if (VISITS.remove(v) && mob.isAlive()) {
						joined(mob, p);
					}
				}),
				Menus.Choice.of(Component.translatable("tale.jaymoskingdoms.back.no"), p -> {
					if (VISITS.contains(v)) {
						depart(level, v, level.getServer().getTickCount());
					}
				})));
			return;
		}
		Speech.tell(level, mob, player, say, Speech.GOLD);
		if (back != null) {
			for (String spec : back.gift()) {
				int star = spec.indexOf('*');
				Identifier id = Identifier.tryParse(star < 0 ? spec : spec.substring(0, star));
				Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				if (item != null) {
					give(player, new ItemStack(item, star < 0 ? 1 : Math.max(1, parse(spec.substring(star + 1)))));
				}
			}
		}
		depart(level, v, tick);
	}

	/** {@code who} stands and faces {@code player} while they talk. */
	static void hold(LivingEntity who, ServerPlayer player) {
		if (who instanceof Mob mob && player.level().getServer() != null) {
			HELD.put(mob, new Hold(player, player.level().getServer().getTickCount() + HOLD_TICKS));
		}
	}

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % TICKS != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		HELD.entrySet().removeIf(e -> {
			Mob mob = e.getKey();
			ServerPlayer player = e.getValue().player();
			if (!mob.isAlive() || player.isRemoved() || player.level() != mob.level() || e.getValue().until() < tick || player.distanceToSqr(mob) > Menus.REACH * Menus.REACH) {
				return true;
			}
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
			return false;
		});
		for (Visit v : new ArrayList<>(VISITS)) {
			ServerPlayer player = server.getPlayerList().getPlayer(v.player);
			Mob mob = v.mob;
			if (!mob.isAlive() || mob.isRemoved()) {
				VISITS.remove(v);
				continue;
			}
			boolean gone = player == null || !player.isAlive() || player.level() != level;
			if (v.state == LEAVING) {
				if (gone || tick >= v.until || mob.distanceToSqr(player) > 30.0 * 30.0) {
					VISITS.remove(v);
				} else if (tick % 40 == 0) {
					Vec3 away = mob.position().subtract(player.position()).normalize().scale(24.0);
					mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, 0.9);
				}
				continue;
			}
			if (gone || tick >= v.until) {
				// (never reached them: a letter is sent again another day; one come back tries again tomorrow)
				if (player != null && v.back != null && v.kind != REVENGE) {
					Tales.Return b = v.back;
					Tales.state(player).returns.add(new Tales.Return(b.name(), b.body(), Tales.day(level) + 1, b.as(), b.say(), b.gift(), b.faction()));
				}
				depart(level, v, tick);
				v.until = tick;
				continue;
			}
			if (v.kind == REVENGE) {
				if (v.state == COMING && mob.distanceToSqr(player) < 12.0 * 12.0) {
					v.state = ASKING;
					Tales.Return back = v.back;
					Speech.tell(level, mob, player, back == null || back.say().isEmpty() ? Component.translatable("tale.jaymoskingdoms.back.revenge")
						: Component.literal(back.say()), Speech.RED);
				}
				if (Rulers.target(mob) == null && !Downed.isDowned(player) && !Downed.isDowned(mob)) {
					mob.setTarget(player);
					if (mob.distanceToSqr(player) > 8.0 * 8.0) {
						mob.getNavigation().moveTo(player, 1.1);
					}
				}
				continue;
			}
			if (v.state == COMING) {
				if (mob.distanceToSqr(player) <= VISIT_REACH * VISIT_REACH) {
					arrive(level, v, player, tick);
				} else if (tick % 20 == 0) {
					come(mob, player);
				}
			}
		}
		if (tick % KEEP_TICKS != 0) {
			return;
		}
		long now = level.getGameTime();
		for (Mob mob : new ArrayList<>(LOADED)) {
			Cast c = mob.getAttached(CAST);
			if (c == null || mob.isRemoved() || !mob.isAlive()) {
				LOADED.remove(mob);
				continue;
			}
			if (c.over()) {
				// (their tale's done: they walk off while watched, and are gone once nobody's looking)
				if (now - c.since() > LEAVE_TICKS || level.getNearestPlayer(mob, LEAVE_UNSEEN) == null) {
					LOADED.remove(mob);
					McaCompat.vanish(mob);
				}
				continue;
			}
			if (c.instance().startsWith("visit:") || c.fight() || HELD.containsKey(mob)) {
				continue;
			}
			BlockPos anchor = BlockPos.of(c.anchor());
			if (mob.distanceToSqr(Vec3.atCenterOf(anchor)) > KEEP_WITHIN * KEEP_WITHIN) {
				mob.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 0.6);
			}
		}
	}
}
