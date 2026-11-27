package com.jaymoskingdoms;

import com.jaymoskingdoms.nightforms.NightGaze;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Servants (Night's Blood, patch 5). A person or beast held by the stare ({@link NightGaze#holds}) and drunk from, calmly, on
 * {@link #NIGHTS_TO_BIND} separate nights becomes the vampire's thrall for good ({@link #MASTER}, after {@link BloodThralls}, which does
 * the same for players): an MCA villager, or a tamed horse or wolf.
 * <ul>
 * <li>A thrall villager keeps their name, home, job and family; the family notices the nights it spends away from home
 * ({@link Suspicion#noticed}, and gossip).</li>
 * <li>A thrall takes the four quick orders ({@link QuickOrders}, as band leaders give them, {@link BandLeaders}); {@link Bonds} keep
 * their bond with the master. A horse or wolf takes them in its own way ({@link #order}).</li>
 * <li>A bottle of blood fed to a thrall keeps them sound ({@link #SOUND_DAYS}); one unfed that long is unsound (weak, slow, and
 * no body to ride), and after {@link #NEGLECT_DAYS} the link lapses.</li>
 * <li>The priest's cure ({@link Cures}), the master's death, or a long neglect frees them. So does standing a while in a temple
 * beside a priest ({@link #TEMPLE_CURE_TICKS}).</li>
 * <li>The master rides one by day ({@link ThrallRide}).</li>
 * </ul>
 * Whether a thrall's master is still theirs is {@link #BORN} (on the master, bumped by death and cure) against {@link #TIES} (on
 * the thrall, what it was when they were bound), so a thrall in an unloaded chunk is freed when it loads.
 */
public final class Servants {
	/** Separate nights of calm drinking before a person or beast is bound. */
	static final int NIGHTS_TO_BIND = BloodThralls.NIGHTS_TO_BIND;
	/** Days a thrall goes without a bottle before it is unsound, and before the link lapses. */
	static final int SOUND_DAYS = 3, NEGLECT_DAYS = 10;
	/** The bond a new thrall starts with the master, and what a bottle adds ({@link Bonds}). */
	static final int BOND_START = 50, BOND_BOTTLE = 5;
	/** Hearts' worth (health points) a bottle heals. */
	static final float BOTTLE_HEAL = 6.0F;
	/** How often (ticks) thralls are looked after. */
	static final int EVERY = 100;
	/** A thrall is away from home at night when this far (blocks) from its bed, or this near its master. */
	static final double HOME_FAR = 20.0, MASTER_NEAR = 8.0;
	/** How far (blocks) a thrall's family notices from. */
	static final double FAMILY_RANGE = 48.0;
	/** Ticks a thrall stands in a temple beside a priest before it is freed; how far (blocks) the priest is. */
	static final int TEMPLE_CURE_TICKS = 1200;
	static final double PRIEST_NEAR = 10.0;
	/** A horse or wolf told to come keeps coming for this long (ticks); a scout goes this far (blocks). */
	static final int COME_TICKS = 200;
	static final double SCOUT_AHEAD = 12.0, BEAST_SPEED = 1.3, FIGHT_NEAR = 16.0;
	/** The pack a thrall carries ({@link ThrallRide}) holds this many stacks. */
	static final int PACK_SLOTS = 9;

	/** On a thrall: the master's UUID. Kept. */
	public static final AttachmentType<String> MASTER = AttachmentRegistry.create(JaymosKingdoms.id("servant_master"), b -> b.persistent(Codec.STRING));
	/** On a thrall: what its master's {@link #BORN} was when it was bound. Kept. */
	static final AttachmentType<Integer> TIES = AttachmentRegistry.create(JaymosKingdoms.id("servant_ties"), b -> b.persistent(Codec.INT));
	/** On the master: bumped by their death and by a cure, freeing every thrall they had. Kept, through death. */
	static final AttachmentType<Integer> BORN = AttachmentRegistry.create(JaymosKingdoms.id("servant_born"), b -> b.persistent(Codec.INT).copyOnDeath());
	/** On a thrall: the day of the last bottle it was fed. Kept. */
	static final AttachmentType<Long> TENDED = AttachmentRegistry.create(JaymosKingdoms.id("servant_tended"), b -> b.persistent(Codec.LONG));
	/** On a thrall: the last night (see {@link Vampires#nightIndex}) its family noticed it away. Kept. */
	static final AttachmentType<Long> AWAY = AttachmentRegistry.create(JaymosKingdoms.id("servant_away"), b -> b.persistent(Codec.LONG));
	/** On a thrall: what it carries for its master ({@link ThrallRide}). Kept. */
	static final AttachmentType<List<ItemStack>> PACK = AttachmentRegistry.create(JaymosKingdoms.id("servant_pack"),
		b -> b.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

	/** What a horse or wolf was last told: the quick order, and until when (game time). */
	private record Told(int kind, UUID by, long until, @Nullable Vec3 point) {
	}

	private static final Map<UUID, Mob> LOADED = new HashMap<>();
	private static final Map<UUID, Told> TOLD = new HashMap<>();
	private static final Map<UUID, Integer> CURING = new HashMap<>();

	private Servants() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob m && m.hasAttached(MASTER)) {
				LOADED.put(m.getUUID(), m);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			LOADED.remove(entity.getUUID());
			TOLD.remove(entity.getUUID());
			CURING.remove(entity.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			TOLD.clear();
			CURING.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Servants::tick);
		// the master's death frees them; a thrall's death drops what it carried
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && Vampires.isVampire(p)) {
				p.setAttached(BORN, p.getAttachedOrElse(BORN, 0) + 1);
			} else if (entity instanceof Mob m && m.hasAttached(MASTER)) {
				dropPack(m);
			}
		});
		// a bottle of blood fed to a thrall keeps it sound
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server) || !(entity instanceof Mob m) || !ownedBy(m, sp)
				|| !bottle(player.getItemInHand(hand))) {
				return InteractionResult.PASS;
			}
			tend(server, sp, m, hand == net.minecraft.world.InteractionHand.MAIN_HAND ? sp.getMainHandItem() : sp.getOffhandItem());
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	// ---- who ----------------------------------------------------------------------------------------------------------

	/** A bottle of Kitchen's blood. */
	static boolean bottle(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(BloodPage.BLOOD_BOTTLE);
	}

	/** A tamed horse or wolf nobody holds yet: one the stare can hold and a drink can bind. */
	public static boolean beast(LivingEntity e) {
		return e.isAlive() && !e.hasAttached(MASTER) && (e instanceof AbstractHorse h && h.isTamed() || e instanceof Wolf w && w.isTame());
	}

	/** Whether the stare may hold {@code e}: a beast to bind, or any thrall. */
	public static boolean willing(LivingEntity e) {
		return beast(e) || e.hasAttached(MASTER);
	}

	/** An MCA villager, grown, that no vampire's, no blood and no curse has touched: one a drink can bind. */
	static boolean candidate(LivingEntity e) {
		if (e.hasAttached(MASTER) || !e.isAlive()) {
			return false;
		}
		if (e instanceof Villager v) {
			return McaCompat.isMca(v) && !v.isBaby() && !Vampires.isVampire(v) && !v.hasAttached(Vampires.BITTEN) && !Lycans.cursed(v)
				&& !Fledglings.isGet(v);
		}
		return beast(e);
	}

	/** Whether {@code e} is a thrall of {@code player} still. */
	public static boolean ownedBy(@Nullable Entity e, ServerPlayer player) {
		if (e == null) {
			return false;
		}
		String m = e.getAttached(MASTER);
		return m != null && m.equals(player.getUUID().toString()) && e.getAttachedOrElse(TIES, 0).equals(player.getAttachedOrElse(BORN, 0));
	}

	/** Whether {@code e} is anyone's thrall. */
	public static boolean thrall(Entity e) {
		return e.hasAttached(MASTER);
	}

	/** The side a thrall fights on ({@link Comrades#side}): its master's. */
	static @Nullable String side(LivingEntity e) {
		String m = e.getAttached(MASTER);
		return m == null ? null : "p:" + m;
	}

	/** Whether {@code target}, held by {@code player}'s stare, is one a calm drink binds or one of their thralls. */
	public static boolean calm(ServerPlayer player, LivingEntity target) {
		return target instanceof Mob m && Vampires.isVampire(player) && NightGaze.holds(player, m) && (candidate(m) || ownedBy(m, player));
	}

	/** Every thrall of {@code player}'s that is loaded within {@code range} of {@code at}. */
	static List<Mob> thrallsNear(ServerPlayer player, Vec3 at, double range) {
		List<Mob> out = new ArrayList<>();
		for (Mob m : LOADED.values()) {
			if (m.isAlive() && m.level() == player.level() && ownedBy(m, player) && m.position().distanceTo(at) <= range) {
				out.add(m);
			}
		}
		return out;
	}

	// ---- binding ------------------------------------------------------------------------------------------------------

	/** {@code vampire} drank from {@code target} on night {@code night}: held by the stare, it counts toward binding it. */
	static void drank(ServerLevel level, ServerPlayer vampire, LivingEntity target, long night) {
		if (!(target instanceof Mob mob) || !calm(vampire, mob) || !candidate(mob)) {
			return;
		}
		Map<String, List<Long>> all = new HashMap<>(vampire.getAttachedOrElse(BloodThralls.NIGHTS, Map.of()));
		String key = mob.getUUID().toString();
		List<Long> nights = new ArrayList<>(all.getOrDefault(key, List.of()));
		if (nights.contains(night)) {
			return;
		}
		nights.add(night);
		all.put(key, nights);
		vampire.setAttached(BloodThralls.NIGHTS, all);
		level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 0.6F, 0.7F);
		if (nights.size() >= NIGHTS_TO_BIND) {
			all.remove(key);
			vampire.setAttached(BloodThralls.NIGHTS, all);
			bind(level, vampire, mob);
		}
	}

	private static void bind(ServerLevel level, ServerPlayer vampire, Mob mob) {
		mob.setAttached(MASTER, vampire.getUUID().toString());
		mob.setAttached(TIES, vampire.getAttachedOrElse(BORN, 0));
		mob.setAttached(TENDED, Deeds.today(level.getServer()));
		mob.setPersistenceRequired();
		if (mob instanceof Wolf wolf && !wolf.isOwnedBy(vampire)) {
			wolf.tame(vampire);
		} else if (mob instanceof AbstractHorse horse && !vampire.getUUID().equals(horse.getOwnerReference() == null ? null : horse.getOwnerReference().getUUID())) {
			horse.setOwner(vampire);
		}
		Bonds.add(mob, vampire.getUUID(), BOND_START);
		LOADED.put(mob.getUUID(), mob);
		level.sendParticles(Fx.wisp(0x5A0A0A, 1.0F), mob.getX(), mob.getY(0.6), mob.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, mob.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 1.2F, 0.6F);
		if (mob instanceof Villager) {
			Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.thrall.bound"), Speech.RED, 12.0);
		}
	}

	/** {@code thrall} is free of its master. */
	static void free(Mob thrall) {
		if (!thrall.hasAttached(MASTER)) {
			return;
		}
		thrall.removeAttached(MASTER);
		thrall.removeAttached(TIES);
		thrall.removeAttached(TENDED);
		thrall.removeAttached(AWAY);
		LOADED.remove(thrall.getUUID());
		TOLD.remove(thrall.getUUID());
		CURING.remove(thrall.getUUID());
		thrall.removeEffect(MobEffects.WEAKNESS);
		thrall.removeEffect(MobEffects.SLOWNESS);
		ThrallRide.freed(thrall);
		dropPack(thrall);
		if (thrall.level() instanceof ServerLevel level) {
			level.sendParticles(Fx.LIGHT, thrall.getX(), thrall.getY(0.6), thrall.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
			level.playSound(null, thrall.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.6F, 1.4F);
			if (thrall instanceof Villager) {
				Speech.say(level, thrall, Component.translatable("speech.jaymoskingdoms.thrall.freed"), Speech.YELLOW, 12.0);
			}
		}
	}

	/** Every thrall {@code player} had is freed (their cure; their death does the same through {@link #BORN}). */
	static void freeAll(ServerPlayer player) {
		player.setAttached(BORN, player.getAttachedOrElse(BORN, 0) + 1);
		ThrallRide.end(player, false);
		for (Mob m : new ArrayList<>(LOADED.values())) {
			if (player.getUUID().toString().equals(m.getAttached(MASTER))) {
				free(m);
			}
		}
	}

	private static void dropPack(Mob mob) {
		List<ItemStack> pack = mob.getAttachedOrElse(PACK, List.of());
		if (!pack.isEmpty() && mob.level() instanceof ServerLevel level) {
			for (ItemStack s : pack) {
				if (!s.isEmpty()) {
					level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, mob.getX(), mob.getY(0.5), mob.getZ(), s.copy()));
				}
			}
		}
		mob.removeAttached(PACK);
	}

	// ---- keeping them sound -------------------------------------------------------------------------------------------

	private static void tend(ServerLevel level, ServerPlayer player, Mob thrall, ItemStack bottle) {
		bottle.shrink(1);
		ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
		if (!player.getInventory().add(empty)) {
			player.spawnAtLocation(player.level(), empty);
		}
		thrall.setAttached(TENDED, Deeds.today(level.getServer()));
		thrall.heal(BOTTLE_HEAL);
		thrall.removeEffect(MobEffects.WEAKNESS);
		thrall.removeEffect(MobEffects.SLOWNESS);
		Bonds.add(thrall, player.getUUID(), BOND_BOTTLE);
		level.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), thrall.getX(), thrall.getY(0.8), thrall.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, thrall.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.7F, 0.7F);
	}

	/** Days since {@code thrall} was last fed a bottle. */
	static long neglected(Mob thrall) {
		if (!(thrall.level() instanceof ServerLevel level)) {
			return 0L;
		}
		return Math.max(0L, Deeds.today(level.getServer()) - thrall.getAttachedOrElse(TENDED, Deeds.today(level.getServer())));
	}

	/** Whether {@code thrall} has gone unfed long enough to be unsound. */
	public static boolean unsound(Mob thrall) {
		return neglected(thrall) >= SOUND_DAYS;
	}

	// ---- upkeep -------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 10 == 0) {
			beasts(server, now);
		}
		if (now % EVERY != 0 || LOADED.isEmpty()) {
			return;
		}
		boolean night = Vampires.night(server.overworld().getOverworldClockTime());
		long nightNo = Vampires.nightIndex(server.overworld().getOverworldClockTime());
		for (Iterator<Mob> it = LOADED.values().iterator(); it.hasNext();) {
			Mob m = it.next();
			if (!m.isAlive() || m.isRemoved() || !(m.level() instanceof ServerLevel level)) {
				if (m.isRemoved()) {
					it.remove();
				}
				continue;
			}
			String id = m.getAttached(MASTER);
			ServerPlayer master = null;
			try {
				master = id == null ? null : server.getPlayerList().getPlayer(UUID.fromString(id));
			} catch (IllegalArgumentException e) {
				id = null;
			}
			if (id == null) {
				it.remove();
				continue;
			}
			if (master == null) {
				continue;
			}
			if (!ownedBy(m, master) || !Vampires.isVampire(master) || VampirePlayers.dhampir(master)) {
				it.remove();
				free(m);
				continue;
			}
			long days = neglected(m);
			if (days >= NEGLECT_DAYS) {
				it.remove();
				free(m);
				continue;
			}
			if (days >= SOUND_DAYS) {
				m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EVERY + 40, 0, false, false, false));
				m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EVERY + 40, 0, false, false, false));
			}
			if (m instanceof Villager v && priestCure(server, level, v)) {
				it.remove();
				free(m);
				continue;
			}
			if (night && m instanceof Villager v && v.getAttachedOrElse(AWAY, Long.MIN_VALUE) != nightNo && away(v, master)) {
				v.setAttached(AWAY, nightNo);
				for (Villager kin : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(FAMILY_RANGE),
					k -> k != v && k.isAlive() && !thrall(k) && McaCompat.kin(v, k) != McaCompat.STRANGERS)) {
					Suspicion.noticed(kin, master);
				}
			}
		}
	}

	/** At night and not at home: far from their bed, or in their master's company. */
	private static boolean away(Villager v, ServerPlayer master) {
		GlobalPos home = McaCompat.home(v).orElse(null);
		if (home == null || home.dimension() != v.level().dimension()) {
			return v.distanceTo(master) <= MASTER_NEAR;
		}
		BlockPos at = home.pos();
		return at.distToCenterSqr(v.position()) > HOME_FAR * HOME_FAR || v.distanceTo(master) <= MASTER_NEAR;
	}

	/** The priest's cure: a thrall brought into a temple and kept there beside a priest is freed. */
	private static boolean priestCure(MinecraftServer server, ServerLevel level, Villager v) {
		if (!HolyDread.inTemple(server, v)
			|| level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(PRIEST_NEAR), p -> p != v && p.isAlive() && Accusations.priest(p)).isEmpty()) {
			CURING.remove(v.getUUID());
			return false;
		}
		int held = CURING.merge(v.getUUID(), EVERY, Integer::sum);
		return held >= TEMPLE_CURE_TICKS;
	}

	// ---- orders to a horse or wolf --------------------------------------------------------------------------------------

	/**
	 * The quick orders to {@code player}'s horses and wolves within {@link Followers#ALL_RANGE}: they have no tongue to
	 * understand and no company to heed. Go: in a fight, set on the nearest foe; else scout ahead where you look. Come and Help:
	 * come to you. Wait: stand (a wolf sits). Returns whether any beast took it.
	 */
	static boolean order(ServerPlayer player, int kind) {
		boolean any = false;
		long now = player.level().getGameTime();
		boolean fight = !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FIGHT_NEAR), m -> Comrades.target(m) == player).isEmpty();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		for (Mob m : thrallsNear(player, player.position(), Followers.ALL_RANGE)) {
			if (m instanceof Villager) {
				continue;
			}
			any = true;
			if (m instanceof TamableAnimal t) {
				t.setOrderedToSit(kind == QuickOrders.WAIT);
			}
			switch (kind) {
				case QuickOrders.WAIT -> {
					m.getNavigation().stop();
					TOLD.put(m.getUUID(), new Told(kind, player.getUUID(), Long.MAX_VALUE, null));
				}
				case QuickOrders.GO -> {
					LivingEntity foe = fight ? foe(player, m) : null;
					if (foe != null) {
						m.setTarget(foe);
						TOLD.remove(m.getUUID());
					} else if (flat.lengthSqr() > 1.0E-4) {
						TOLD.put(m.getUUID(), new Told(kind, player.getUUID(), now + COME_TICKS, player.position().add(flat.normalize().scale(SCOUT_AHEAD))));
					}
				}
				default -> TOLD.put(m.getUUID(), new Told(QuickOrders.COME, player.getUUID(), now + COME_TICKS, null));
			}
		}
		return any;
	}

	private static @Nullable LivingEntity foe(ServerPlayer player, Mob beast) {
		LivingEntity best = null;
		for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FIGHT_NEAR), e -> e.isAlive() && e instanceof Enemy)) {
			if (best == null || m.distanceToSqr(beast) < best.distanceToSqr(beast)) {
				best = m;
			}
		}
		return best;
	}

	private static void beasts(MinecraftServer server, long now) {
		for (Iterator<Map.Entry<UUID, Told>> it = TOLD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Told> e = it.next();
			Mob m = LOADED.get(e.getKey());
			Told t = e.getValue();
			ServerPlayer by = server.getPlayerList().getPlayer(t.by());
			if (m == null || !m.isAlive() || by == null || by.level() != m.level() || now >= t.until()) {
				it.remove();
				continue;
			}
			if (t.kind() == QuickOrders.WAIT) {
				m.getNavigation().stop();
				continue;
			}
			Vec3 to = t.point() != null ? t.point() : by.position();
			if (m.position().distanceTo(to) <= 3.0) {
				it.remove();
				continue;
			}
			if (m.getNavigation().isDone() || now % 40 == 0) {
				m.getNavigation().moveTo(to.x, to.y, to.z, BEAST_SPEED);
			}
		}
	}
}
