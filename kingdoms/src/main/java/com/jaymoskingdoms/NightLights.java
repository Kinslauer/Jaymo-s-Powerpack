package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * NPCs need light to see by (Andy, 2026-11-20: "npcs should be unable to see in the dark as well, searching for a torch
 * or lantern before they venture out, and night watch guards should always have one equipped", and "you may even
 * encounter villagers with lanterns on sticks").
 * <ul>
 * <li><b>Sight</b> ({@link #makesOut}, from {@code NightSightMixin} on every line-of-sight check an NPC makes): the
 * world's people (villagers, MCA's too, a settlement's and a camp's folk, outlaws) make someone out in good light
 * ({@link #CLEAR} or more) as by day; in less, only within {@link #DARK_NEAR} blocks and {@link #PER_LIGHT} more per
 * level of light. The light is the brightest of the light where the other stands, a light they carry (a torch or lantern
 * in hand, a hip lantern or lantern pole), and the looker's own carried light fading a level a block, as a placed one's
 * does. The night sky's share follows the moon, as the night looks on screen ({@code DarkNights}): a full moon lets them
 * see a dozen blocks or so, a new moon next to nothing. On fire or glowing, anyone is seen. Whoever sees in the dark is
 * left be: the undead and creatures of the night ({@link Senses#DARKVISION}), a blood with darkvision ({@link Darkvision}),
 * orcs and goblins, vampires, a changed werewolf, anyone with night vision. A foe someone is already after is kept in
 * sight; they lose it only once they stop chasing it.</li>
 * <li><b>Fetching a light</b> ({@link #step}): a grown, waking villager or folk out in the dark at night with nothing lit
 * in hand gets one before going on: from their pocket (a villager's own inventory), or the lantern they kept from a night
 * before; else they go for one, to the nearest of their home (their household's light: mostly a torch, sometimes a
 * lantern, now and then a lantern on a pole, by who they are), their settlement's stores (a lantern, else a torch, taken
 * from there) and a lit campfire (a torch lit at it). Home is the villagers' way, the stores a settlement's folk's, the
 * fire a camp's. Nothing within {@link #FETCH_RANGE}, or not got there within {@link #FETCH_TICKS}, and they go without
 * for a while ({@link #GIVE_UP}).</li>
 * <li><b>The night watch</b> ({@link #onWatch}): a guard or archer awake at night and not off duty always carries a
 * light, lit or not where he stands: a lantern from the stores if there is one (on a pole one time in
 * {@link #WATCH_POLE}), else a torch from them, else a torch for nothing (the watch is never sent out blind).</li>
 * <li><b>Putting it away</b>: at dawn, or asleep, the light goes and the hand holds what it held. A lantern is kept
 * ({@link #KEPT}) for the next night; a torch carried through the night has burnt down by dawn.</li>
 * </ul>
 * The light goes in the off hand, so a weapon and a shield's hand are left alone: a watchman's shield waits till dawn
 * ({@link #CARRYING} keeps it). A plain (non-MCA) villager holds it in the hand its model shows. Lights are drawn and
 * light the way around them with LambDynamicLights, as a player's do; a lantern pole held in hand is Jaymo's Powers'
 * ({@link #POLE}), and without Powers the pole's share are plain lanterns.
 */
public final class NightLights {
	// ---- sight ----
	/** At this much light (0-15) or more where someone stands, an NPC sees them as by day. */
	static final int CLEAR = 7;
	/** Below it, an NPC makes someone out within this many blocks, and this many more for each level of light. */
	static final double DARK_NEAR = 3.5, PER_LIGHT = 2.5;
	/**
	 * The night sky's light left in each phase of the moon, full (0) round to waxing gibbous (7), and only while the moon
	 * is up: the same as the night looks on screen ({@code client.DarkNights.MOONLIGHT}).
	 */
	static final float[] MOONLIGHT = {1.0F, 0.7F, 0.45F, 0.2F, 0.0F, 0.2F, 0.45F, 0.7F};
	/** A held item gives light if its block gives at least this much (a soul torch's 10 and up). */
	static final int HELD_MIN = 10;
	/** The lantern on a pole is a plain lantern. */
	static final int POLE_LIGHT = 15;

	// ---- carrying ----
	/** How often (ticks) the folk round each player are looked over, and how far round them (blocks). */
	static final int EVERY = 10;
	static final double SCAN = 64.0;
	/** How far (blocks) someone goes for a light, and how far they look for a lit campfire (across; up and down). */
	static final double FETCH_RANGE = 48.0;
	static final int FIRE_RANGE = 16, FIRE_RANGE_Y = 4;
	/** How long (ticks) a fetch may take before it's given up, and how long someone goes without after giving up. */
	static final int FETCH_TICKS = 20 * 60;
	static final long GIVE_UP = 20L * 120;
	/** Near enough (blocks) to take it; a villager's pace and a folk's. */
	static final double ARRIVED = 2.5;
	static final float VILLAGER_SPEED = 0.6F;
	static final double FOLK_SPEED = 1.0;
	/** A household's light, by who they are, out of {@link #HOUSE_OF}: a lantern on a pole, a lantern; the rest a torch. */
	static final int HOUSE_POLE = 3, HOUSE_LANTERN = 5, HOUSE_OF = 20;
	/** A watchman with a lantern carries it on a pole one time in this many (by who he is). */
	static final int WATCH_POLE = 3;

	/** Jaymo's Powers' lantern pole, by its id (no Powers: none, and a plain lantern instead). */
	static final Identifier POLE = Identifier.fromNamespaceAndPath("jaymospowers", "lantern_pole");

	/** A light someone was given to carry: which (a copy), in the off hand or not (the main), and what that hand held before. Saved. */
	public record Carrying(ItemStack light, boolean offhand, ItemStack before) {
		static final Codec<Carrying> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.fieldOf("light").forGetter(Carrying::light),
			Codec.BOOL.fieldOf("offhand").forGetter(Carrying::offhand),
			ItemStack.OPTIONAL_CODEC.fieldOf("before").forGetter(Carrying::before)).apply(i, Carrying::new));

		EquipmentSlot slot() {
			return this.offhand ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
		}
	}

	/** While someone carries a light they were given. Saved. */
	public static final AttachmentType<Carrying> CARRYING = AttachmentRegistry.create(JaymosKingdoms.id("night_light"),
		builder -> builder.persistent(Carrying.CODEC));
	/** A light someone put away (a lantern between nights, a torch while asleep). Saved. */
	public static final AttachmentType<ItemStack> KEPT = AttachmentRegistry.create(JaymosKingdoms.id("night_light_kept"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC));

	private enum Source { HOME, STORES, FIRE }

	/** Someone on their way to a light: where, what kind of place, and until when. */
	private record Fetch(BlockPos to, Source source, long until) {
	}

	private static final Map<UUID, Fetch> FETCHING = new HashMap<>();
	/** Who gave up looking, until when (game time). */
	private static final Map<UUID, Long> WITHOUT = new HashMap<>();
	private static @Nullable Item pole;
	private static boolean lookedForPole;

	private NightLights() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NightLights::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FETCHING.clear();
			WITHOUT.clear();
		});
		// a villager's own things in the hand the light took are found on them
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Carrying c = entity.getAttached(CARRYING);
			if (c != null && entity instanceof Villager && !c.before().isEmpty() && entity.level() instanceof ServerLevel level) {
				entity.spawnAtLocation(level, c.before().copy());
			}
		});
	}

	// ---- seeing -----------------------------------------------------------------------------------------------------------

	/** One of the world's people, who see as people do: a villager, a settlement's or camp's folk, an outlaw. */
	static boolean npc(Entity e) {
		return e instanceof Villager || e instanceof Folk || e instanceof Mob && Outlaws.is(e);
	}

	/** {@code e} sees in the dark: the undead and creatures of the night, a blood that does, orcs, goblins, the night's own. */
	static boolean seesInDark(LivingEntity e) {
		return e.getType().builtInRegistryHolder().is(Senses.DARKVISION) || UpperHand.seesInDark(e) || Orcs.isOrc(e) || Dialects.goblin(e)
			|| Vampires.isVampire(e) || Lycans.changed(e);
	}

	/**
	 * From {@code NightSightMixin}, once {@code looker} has a clear line to {@code target}: whether it makes them out in the
	 * light there. Always true for anyone who isn't one of the world's people, or who sees in the dark.
	 */
	public static boolean makesOut(LivingEntity looker, Entity target) {
		if (!(looker.level() instanceof ServerLevel level) || !npc(looker) || looker instanceof Mob m && m.getTarget() == target || seesInDark(looker)) {
			return true;
		}
		double d = looker.distanceTo(target);
		if (d <= DARK_NEAR) {
			return true;
		}
		int light = light(level, looker, target, d);
		return light >= CLEAR || d <= DARK_NEAR + PER_LIGHT * light;
	}

	/**
	 * The light {@code looker} sees {@code target} by, {@code d} blocks off (0 to 15): where they stand (the night sky's by
	 * the moon), what they carry, and what the looker carries, fading a level a block.
	 */
	static int light(ServerLevel level, LivingEntity looker, Entity target, double d) {
		if (target.isOnFire() || target.isCurrentlyGlowing()) {
			return 15;
		}
		int light = ambient(level, BlockPos.containing(target.getEyePosition()));
		if (target instanceof LivingEntity carrier) {
			light = Math.max(light, carried(carrier));
		}
		return Math.max(light, carried(looker) - Mth.floor(d));
	}

	/** The light at {@code pos}: its block light, and its sky light dimmed for the hour and, at night, by the moon. */
	static int ambient(ServerLevel level, BlockPos pos) {
		int block = level.getBrightness(LightLayer.BLOCK, pos);
		int sky = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
		if (sky > 0 && level.dimension() == Level.OVERWORLD && level.isDarkOutside()) {
			Vec3 at = Vec3.atCenterOf(pos);
			MoonPhase phase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, at);
			float moon = MOONLIGHT[Math.floorMod(phase.index(), MOONLIGHT.length)];
			// a lunar event's moon always shines full
			moon = Mth.lerp(Moons.fadeFor(level), moon, 1.0F);
			moon *= com.jaymoskingdoms.astronomy.SunMoon.moonlight(level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, at));
			sky = Math.round(sky * moon);
		}
		return Math.max(block, sky);
	}

	/** The brightest light {@code who} carries: in either hand, and a player's hip lantern or lantern pole. */
	static int carried(LivingEntity who) {
		int light = Math.max(given(who.getMainHandItem()), given(who.getOffhandItem()));
		if (who instanceof Player player && light < POLE_LIGHT) {
			for (ItemStack worn : PowersCompat.baubles(player)) {
				light = Math.max(light, given(worn));
			}
		}
		return light;
	}

	/** The light {@code stack} gives held (0 if none). */
	static int given(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		if (isPole(stack)) {
			return POLE_LIGHT;
		}
		int light = Block.byItem(stack.getItem()).defaultBlockState().getLightEmission();
		return light >= HELD_MIN ? light : 0;
	}

	static boolean isLight(ItemStack stack) {
		return given(stack) > 0;
	}

	private static @Nullable Item pole() {
		if (!lookedForPole) {
			lookedForPole = true;
			pole = BuiltInRegistries.ITEM.getOptional(POLE).orElse(null);
		}
		return pole;
	}

	private static boolean isPole(ItemStack stack) {
		Item p = pole();
		return p != null && stack.is(p);
	}

	// ---- carrying ---------------------------------------------------------------------------------------------------------

	/** Who carries a light: a grown villager or folk, alive, who doesn't see in the dark. */
	private static boolean bearer(PathfinderMob mob) {
		return (mob instanceof Villager || mob instanceof Folk) && mob.isAlive() && !mob.isBaby();
	}

	/** A guard or archer keeping the night watch: awake and not off duty (a realm's MCA guards have their evenings). */
	static boolean onWatch(ServerLevel level, PathfinderMob mob) {
		if (!Downed.isGuard(mob)) {
			return false;
		}
		return mob instanceof Folk || !(mob instanceof Worker w) || w.factionId() < 0 || !Rulers.offDuty(mob, w, Royals.timeOfDay(level));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			Set<PathfinderMob> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(SCAN), NightLights::bearer)) {
					if (seen.add(mob)) {
						step(level, mob, now);
					}
				}
			}
		}
	}

	/** {@code mob}'s light looked to: put away, kept carrying, or got. */
	private static void step(ServerLevel level, PathfinderMob mob, long now) {
		Carrying c = mob.getAttached(CARRYING);
		if (c != null && !isLight(mob.getItemBySlot(c.slot()))) {
			// someone else changed that hand (a guard dressing for duty): what it held is theirs again
			forget(level, mob, c);
			c = null;
		}
		boolean night = level.isDarkOutside();
		boolean resting = mob.isSleeping() || Downed.isDowned(mob) || PowersCompat.helpless(mob);
		boolean watch = night && !resting && onWatch(level, mob);
		if (!night || resting || !watch && seesInDark(mob)) {
			FETCHING.remove(mob.getUUID());
			if (c != null) {
				putAway(mob, c, night);
			} else if (!night && mob.getAttached(KEPT) != null && !keeps(mob.getAttached(KEPT))) {
				// a torch put away in the night is burnt down by morning
				mob.removeAttached(KEPT);
			}
			return;
		}
		if (c != null || isLight(mob.getMainHandItem()) || isLight(mob.getOffhandItem())) {
			FETCHING.remove(mob.getUUID());
			return;
		}
		// the watch always goes out with a light; anyone else only once it's dark where they are
		if (!watch && ambient(level, BlockPos.containing(mob.getEyePosition())) >= CLEAR) {
			FETCHING.remove(mob.getUUID());
			return;
		}
		ItemStack kept = mob.getAttached(KEPT);
		if (kept != null && !kept.isEmpty()) {
			mob.removeAttached(KEPT);
			take(mob, kept.copy());
			return;
		}
		if (watch) {
			take(mob, issue(level, mob));
			return;
		}
		ItemStack pocket = fromPocket(mob);
		if (!pocket.isEmpty()) {
			take(mob, pocket);
			return;
		}
		fetch(level, mob, now);
	}

	/** A light {@code mob} has about them: one from a villager's own inventory (taken out of it), or empty. */
	private static ItemStack fromPocket(PathfinderMob mob) {
		if (!(mob instanceof Villager v)) {
			return ItemStack.EMPTY;
		}
		var inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isLight(inv.getItem(i))) {
				return inv.removeItem(i, 1);
			}
		}
		return ItemStack.EMPTY;
	}

	/** Puts {@code light} in {@code mob}'s light hand (what it held is kept for later). */
	private static void take(PathfinderMob mob, ItemStack light) {
		if (light.isEmpty()) {
			return;
		}
		// a plain villager's model shows only the main hand; everyone else carries it in the off hand
		boolean offhand = !(mob instanceof Villager) || McaCompat.isMca(mob);
		EquipmentSlot slot = offhand ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
		ItemStack before = mob.getItemBySlot(slot).copy();
		mob.setAttached(CARRYING, new Carrying(light.copy(), offhand, before));
		mob.setItemSlot(slot, light);
		mob.swing(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	/** At dawn or asleep: the light goes (a lantern, or a torch in the night, is kept) and the hand holds what it held. */
	private static void putAway(PathfinderMob mob, Carrying c, boolean night) {
		ItemStack light = mob.getItemBySlot(c.slot()).copy();
		mob.setItemSlot(c.slot(), c.before().copy());
		mob.removeAttached(CARRYING);
		if (keeps(light) || night) {
			mob.setAttached(KEPT, light);
		}
	}

	/** A light kept from one night to the next: a lantern (on a pole or not); a torch burns down. */
	private static boolean keeps(ItemStack light) {
		return !light.isEmpty() && !light.is(Items.TORCH) && !light.is(Items.SOUL_TORCH);
	}

	/**
	 * The hand changed under {@code c} (a guard dressing for duty puts his shield back on): the light is put by for when
	 * that hand is free again, and what the hand held before goes back to a villager's pocket (folk's kit is reissued).
	 */
	private static void forget(ServerLevel level, PathfinderMob mob, Carrying c) {
		mob.removeAttached(CARRYING);
		if (!c.light().isEmpty()) {
			mob.setAttached(KEPT, c.light().copy());
		}
		if (mob instanceof Villager v && !c.before().isEmpty()) {
			ItemStack left = v.getInventory().addItem(c.before().copy());
			if (!left.isEmpty()) {
				mob.spawnAtLocation(level, left);
			}
		}
	}

	/** A night watchman's light: a lantern from the stores (now and then on a pole), else a torch from them, else one for nothing. */
	private static ItemStack issue(ServerLevel level, PathfinderMob mob) {
		Faction f = faction(level, mob);
		if (f != null) {
			ItemStack lantern = Storage.take(level, f, s -> s.is(Items.LANTERN) || s.is(Items.SOUL_LANTERN), 1);
			if (!lantern.isEmpty()) {
				return byWho(mob, WATCH_POLE) == 0 && lantern.is(Items.LANTERN) && pole() != null ? new ItemStack(pole()) : lantern;
			}
			ItemStack torch = Storage.take(level, f, s -> s.is(Items.TORCH), 1);
			if (!torch.isEmpty()) {
				return torch;
			}
		}
		return new ItemStack(Items.TORCH);
	}

	/** The settlement {@code mob} lives or serves in, if it stands. */
	private static @Nullable Faction faction(ServerLevel level, PathfinderMob mob) {
		if (!(mob instanceof Worker w) || w.factionId() < 0) {
			return null;
		}
		Faction f = Factions.get(level.getServer(), w.factionId());
		return f == null || f.fallen() ? null : f;
	}

	/** A number from 0 to {@code of - 1} that is always the same for {@code mob}. */
	private static int byWho(Entity mob, int of) {
		long h = mob.getUUID().getLeastSignificantBits() * 0x9E3779B97F4A7C15L;
		return (int) Math.floorMod(h ^ h >>> 29, (long) of);
	}

	/** {@code mob} goes for a light, or on its way gets there and takes it. */
	private static void fetch(ServerLevel level, PathfinderMob mob, long now) {
		UUID id = mob.getUUID();
		Long until = WITHOUT.get(id);
		if (until != null) {
			if (until > now) {
				return;
			}
			WITHOUT.remove(id);
		}
		Fetch f = FETCHING.get(id);
		if (f == null) {
			f = find(level, mob, now);
			if (f == null) {
				WITHOUT.put(id, now + GIVE_UP);
				return;
			}
			FETCHING.put(id, f);
		}
		if (now > f.until()) {
			FETCHING.remove(id);
			WITHOUT.put(id, now + GIVE_UP);
			return;
		}
		Vec3 to = Vec3.atBottomCenterOf(f.to());
		if (mob.position().subtract(to).horizontalDistance() > ARRIVED || Math.abs(mob.getY() - to.y) > 3.0) {
			if (mob instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, f.to(), VILLAGER_SPEED, 1);
			} else {
				mob.getNavigation().moveTo(to.x, to.y, to.z, FOLK_SPEED);
			}
			return;
		}
		FETCHING.remove(id);
		ItemStack light = switch (f.source()) {
			case HOME -> household(mob);
			case STORES -> fromStores(level, mob);
			case FIRE -> new ItemStack(Items.TORCH);
		};
		if (light.isEmpty()) {
			WITHOUT.put(id, now + GIVE_UP);
			return;
		}
		mob.getLookControl().setLookAt(Vec3.atCenterOf(f.to()));
		if (f.source() == Source.FIRE) {
			level.playSound(null, f.to(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
		}
		take(mob, light);
	}

	/** Where {@code mob} might find a light: the nearest of its home, its settlement's stores holding one, and a lit campfire. */
	private static @Nullable Fetch find(ServerLevel level, PathfinderMob mob, long now) {
		BlockPos at = mob.blockPosition();
		BlockPos best = null;
		Source kind = null;
		double bestSqr = FETCH_RANGE * FETCH_RANGE;
		if (mob instanceof Villager v) {
			GlobalPos home = McaCompat.home(v).orElse(null);
			if (home != null && home.dimension() == level.dimension() && home.pos().distSqr(at) <= bestSqr) {
				best = home.pos();
				kind = Source.HOME;
				bestSqr = home.pos().distSqr(at);
			}
		}
		Faction f = faction(level, mob);
		if (f != null) {
			for (BlockPos pos : f.holdings().storage()) {
				double d = pos.distSqr(at);
				if (d < bestSqr && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Container box && holds(box, NightLights::stocked)) {
					best = pos;
					kind = Source.STORES;
					bestSqr = d;
				}
			}
		}
		for (BlockPos pos : best != null ? java.util.List.<BlockPos>of() : BlockPos.betweenClosed(at.offset(-FIRE_RANGE, -FIRE_RANGE_Y, -FIRE_RANGE), at.offset(FIRE_RANGE, FIRE_RANGE_Y, FIRE_RANGE))) {
			double d = pos.distSqr(at);
			if (d < bestSqr) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof CampfireBlock && CampfireBlock.isLitCampfire(state)) {
					best = pos.immutable();
					kind = Source.FIRE;
					bestSqr = d;
				}
			}
		}
		return best == null ? null : new Fetch(best, kind, now + FETCH_TICKS);
	}

	/** A light worth taking from the stores: a lantern or a torch. */
	private static boolean stocked(ItemStack s) {
		return s.is(Items.LANTERN) || s.is(Items.SOUL_LANTERN) || s.is(Items.TORCH);
	}

	private static boolean holds(Container box, Predicate<ItemStack> what) {
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (what.test(box.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	/** From the settlement's stores: a lantern if there is one, else a torch. */
	private static ItemStack fromStores(ServerLevel level, PathfinderMob mob) {
		Faction f = faction(level, mob);
		if (f == null) {
			return ItemStack.EMPTY;
		}
		ItemStack lantern = Storage.take(level, f, s -> s.is(Items.LANTERN) || s.is(Items.SOUL_LANTERN), 1);
		return lantern.isEmpty() ? Storage.take(level, f, s -> s.is(Items.TORCH), 1) : lantern;
	}

	/** The household's own light, by who they are: now and then a lantern on a pole, sometimes a lantern, mostly a torch. */
	private static ItemStack household(PathfinderMob mob) {
		int roll = byWho(mob, HOUSE_OF);
		if (roll < HOUSE_POLE) {
			Item p = pole();
			return new ItemStack(p != null ? p : Items.LANTERN);
		}
		return new ItemStack(roll < HOUSE_POLE + HOUSE_LANTERN ? Items.LANTERN : Items.TORCH);
	}
}
