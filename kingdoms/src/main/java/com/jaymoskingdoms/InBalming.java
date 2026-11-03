package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Ogre;
import com.jaymoskingdoms.spirits.Spirits;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.433) In-balming (Andy: "All the spirits need a method of binding with orcs and goblins, called In-balming; they all
 * have their own methods and abilities"). Each spirit's In-balming move ({@code inbalm_<spirit>}, level {@value #LEVEL}) is
 * cast on a downed orc or goblin in reach (never a chief or king), and each binds him its own way:
 * <ul>
 * <li><b>Varro, the ki mark</b> (Andy's): a mark of ki on his brow, seen only by ki users. He gets up and goes home, his
 * tribe's as before; but now and then, when his keeper's in a fight within {@value #JOIN_REACH} blocks, he joins it: a
 * short film for those near, a battle cry ("FOR Andy!!"), and he fights their foe for a minute before going home.</li>
 * <li><b>Ambrose, the gentle hand</b> (Andy's: he counters large orcs and brutes): only on the big ones, an orc (not a
 * goblin) downed, or an ogre (never the Ogre King) brought under a quarter of its health. That one, one at a time, follows
 * you and fights for you. A captain so calmed is sworn to you ({@link SwornCaptains}).</li>
 * <li><b>Grimsby, the steel oath</b>: Grimsby's old trick of making a beaten swordsman his pupil. He goes home with an iron
 * blade, and when his keeper fights within {@value #OATH_REACH} blocks of him, he turns that blade on his own camp.</li>
 * <li><b>Thessaly, the borrowed eyes</b>: she reads through his eyes. He goes home, and each dawn she tells her keeper
 * something of his tribe: a captain's secret, or that it's mustering to hunt them.</li>
 * <li><b>The Quiet One, the silent shadow</b>: he goes nowhere and says nothing. He follows you unseen, and when a foe you're
 * fighting is near death he steps out of nothing, ends it, and is gone again (once in {@value #SHADOW_AGAIN} ticks).</li>
 * </ul>
 * A keeper has so many of each ({@link #cap}); a new one past it lets the oldest go. The bond lives in a tag on him
 * ({@link #TAG}) and a list on the keeper ({@link #BALMED}).
 */
public final class InBalming {
	// ---- tuning ----------------------------------------------------------------------------------------------------
	/** The spirit level In-balming is taught at, and how far off the downed one may lie. */
	public static final int LEVEL = 4;
	public static final double REACH = 6.0;
	/** Its mana and wait (ticks). */
	public static final float COST = 40.0F;
	public static final int COOLDOWN = 2400;
	/** How many each spirit holds at once, by spirit kind: Grimsby, Varro, Ambrose, Thessaly, the Quiet One. */
	static final int[] CAPS = {3, 5, 1, 3, 1};
	/** How long after a blow (given or taken, ticks) a keeper counts as in a fight. */
	static final int FIGHTING = 100;
	/** Varro's mark: how far ki users see it; how near a fight he joins, his odds each fight, the wait before he may again (ticks), how long he fights. */
	static final double MARK_SEEN = 32.0, JOIN_REACH = 64.0;
	static final float JOIN_CHANCE = 0.5F;
	static final long JOIN_AGAIN = 6000L;
	static final int JOIN_TICKS = 1200, JOIN_FILM = 40;
	static final double FILM_WITNESSES = 32.0;
	/** Grimsby's oath: how near his keeper's fight must be. */
	static final double OATH_REACH = 24.0;
	/** Ambrose: an ogre is calmed only under this share of its health. */
	static final float OGRE_CALM_AT = 0.25F;
	/** A follower closer than this stays put; further than {@link #FOLLOW_FAR} catches up. */
	static final double FOLLOW_NEAR = 5.0, FOLLOW_FAR = 48.0;
	/** The Quiet One's shadow: a foe under this share is ended, within this reach, once in this long (ticks). */
	static final float SHADOW_AT = 0.3F;
	static final double SHADOW_REACH = 16.0;
	static final long SHADOW_AGAIN = 2400L;
	static final double SAY_RANGE = 24.0;
	static final int LINES = 3;

	/** On an in-balmed one: {@code TAG + keeper id + "|" + spirit kind}. */
	static final String TAG = "jaymoskingdoms.inbalm=";
	/** On a keeper: those they hold, {@code "<entity id>|<spirit kind>"}, oldest first. Saved, kept through death. */
	static final AttachmentType<List<String>> BALMED = AttachmentRegistry.create(JaymosKingdoms.id("inbalmed"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Varro's marked, now fighting for their keeper: until when (game time), by their id. */
	private static final Map<UUID, Long> JOINED = new HashMap<>();
	/** When each marked one last joined a fight, and when each shadow last struck, by their id. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private InBalming() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOINED.clear();
			LAST.clear();
		});
	}

	// ---- who's in-balmed -----------------------------------------------------------------------------------------------

	/** The bond on {@code e}: its keeper and spirit kind; null if none. */
	private record Mark(UUID keeper, int spirit) {
	}

	private static @Nullable Mark mark(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(TAG)) {
				String[] parts = tag.substring(TAG.length()).split("\\|");
				try {
					return parts.length == 2 ? new Mark(UUID.fromString(parts[0]), Integer.parseInt(parts[1])) : null;
				} catch (IllegalArgumentException e2) {
					return null;
				}
			}
		}
		return null;
	}

	/** {@code e} is in-balmed. */
	public static boolean is(Entity e) {
		return mark(e) != null;
	}

	/** {@code e} is in-balmed by {@code keeper}: he never goes for them. */
	public static boolean spares(Entity e, Entity keeper) {
		Mark m = mark(e);
		return m != null && m.keeper().equals(keeper.getUUID());
	}

	private static void unmark(Entity e) {
		for (String tag : List.copyOf(e.entityTags())) {
			if (tag.startsWith(TAG)) {
				e.removeTag(tag);
			}
		}
	}

	/** {@code e} is an orc or a goblin of a camp (or loose), not its chief or king. */
	private static boolean greenskin(Entity e) {
		if (e instanceof Worker w && (CampChiefs.isRole(w.role()) || Greenskins.chief(w.role()))) {
			return false;
		}
		return Greenskins.of(e) != null || e instanceof Worker w && Greenskins.isRole(w.role());
	}

	private static boolean orc(Entity e) {
		return Greenskins.of(e) == Heritage.Kind.ORC || e instanceof Worker w && Orcs.isOrc(w.role());
	}

	// ---- the cast --------------------------------------------------------------------------------------------------

	/** (Each spirit's In-balming move) {@code player}'s spirit {@code kind} in-balms what's in sight or nearest. False if nothing could be. */
	public static boolean cast(ServerPlayer player, int kind) {
		ServerLevel level = (ServerLevel)player.level();
		LivingEntity seen = Arcana.sighted(player);
		if (seen == null || seen.distanceTo(player) > REACH || !fits(seen, kind)) {
			seen = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
				e -> fits(e, kind) && player.hasLineOfSight(e))) {
				if (seen == null || e.distanceTo(player) < seen.distanceTo(player)) {
					seen = e;
				}
			}
		}
		if (seen == null) {
			return false;
		}
		balm(level, player, seen, kind);
		return true;
	}

	/** {@code e} can be in-balmed by spirit {@code kind}. */
	private static boolean fits(LivingEntity e, int kind) {
		if (!e.isAlive() || is(e) || SwornCaptains.free(e) || kind == Spirits.AMBROSE && Captains.is(e) && !(e instanceof Mob m && Captains.starable(m))) {
			return false;
		}
		if (kind == Spirits.AMBROSE && e instanceof Ogre ogre) {
			return !OgreHolds.king(ogre) && ogre.getHealth() <= ogre.getMaxHealth() * OGRE_CALM_AT;
		}
		if (!Downed.isDowned(e) || !greenskin(e)) {
			return false;
		}
		return kind != Spirits.AMBROSE || orc(e);
	}

	private static void balm(ServerLevel level, ServerPlayer player, LivingEntity e, int kind) {
		RandomSource random = level.getRandom();
		e.addTag(Downed.REVIVE_TAG);
		if (e instanceof Mob m) {
			m.setTarget(null);
		}
		e.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		e.setLastHurtByMob(null);
		if (kind == Spirits.AMBROSE && Captains.is(e) && SwornCaptains.bind(level, player, e)) {
			// (a captain Ambrose calms is sworn to you outright: he follows, and takes orders)
			SpiritVoiceHook.say(player, kind);
			return;
		}
		e.addTag(TAG + player.getStringUUID() + "|" + kind);
		keep(player, e, kind);
		if (kind == Spirits.AMBROSE || kind == Spirits.QUIET) {
			// (he leaves his camp and walks at your side; the Quiet One's unseen)
			McaCompat.swear(e, player);
		}
		if (kind == Spirits.GRIMSBY && e instanceof Mob m) {
			m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
			m.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
		}
		int colour = Spirits.of(kind).color() & 0xFFFFFF;
		level.sendParticles(new DustParticleOptions(colour, 1.4F), e.getX(), e.getY(0.6), e.getZ(), 30, 0.4, 0.6, 0.4, 0.0);
		level.sendParticles(ParticleTypes.ENCHANT, e.getX(), e.getY(0.6), e.getZ(), 30, 0.4, 0.6, 0.4, 0.6);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.7F + kind * 0.1F);
		if (kind != Spirits.QUIET) {
			Speech.say(level, e, Component.translatable("talk.jaymoskingdoms.inbalm." + key(kind) + "." + random.nextInt(LINES), player.getDisplayName()),
				Speech.RED, SAY_RANGE);
		}
		SpiritVoiceHook.say(player, kind);
	}

	/** The spirit's word for its In-balming (its voice lines' event is {@code inbalm}). */
	private static final class SpiritVoiceHook {
		static void say(ServerPlayer player, int kind) {
			com.jaymoskingdoms.spirits.SpiritVoice.sayNow(player, kind, "inbalm");
		}
	}

	private static String key(int kind) {
		return switch (kind) {
			case Spirits.GRIMSBY -> "grimsby";
			case Spirits.AMBROSE -> "ambrose";
			case Spirits.THESSALY -> "thessaly";
			case Spirits.QUIET -> "quiet";
			default -> "varro";
		};
	}

	/** {@code e} is one of {@code player}'s for spirit {@code kind}; past the cap the oldest is let go (it lapses when next seen). */
	private static void keep(ServerPlayer player, Entity e, int kind) {
		List<String> held = new ArrayList<>(player.getAttachedOrElse(BALMED, List.of()));
		held.add(e.getStringUUID() + "|" + kind);
		int cap = kind >= 0 && kind < CAPS.length ? CAPS[kind] : 1;
		List<String> ofKind = held.stream().filter(s -> s.endsWith("|" + kind)).toList();
		for (int i = 0; i < ofKind.size() - cap; i++) {
			held.remove(ofKind.get(i));
		}
		player.setAttached(BALMED, List.copyOf(held));
	}

	/** Whether {@code keeper} still holds {@code e} (they let the oldest go past the cap). */
	private static boolean held(ServerPlayer keeper, Entity e, int kind) {
		return keeper.getAttachedOrElse(BALMED, List.of()).contains(e.getStringUUID() + "|" + kind);
	}

	// ---- their days ------------------------------------------------------------------------------------------------

	/** What {@code keeper} is fighting (a blow given or taken lately), or null. */
	private static @Nullable LivingEntity foeOf(ServerPlayer keeper) {
		LivingEntity hit = keeper.getLastHurtMob();
		if (hit != null && hit.isAlive() && keeper.tickCount - keeper.getLastHurtMobTimestamp() < FIGHTING && !spares(hit, keeper)) {
			return hit;
		}
		LivingEntity by = keeper.getLastHurtByMob();
		if (by != null && by.isAlive() && keeper.tickCount - keeper.getLastHurtByMobTimestamp() < FIGHTING && !spares(by, keeper)) {
			return by;
		}
		return null;
	}

	/**
	 * (From {@code Workers.step}) an in-balmed villager's step, before his own: null to go on as he would (a marked one at
	 * home), true or false for {@code Workers.step}'s answer (false: his brain has him, fighting or following).
	 */
	static @Nullable Boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		Mark m = mark(mob);
		if (m == null) {
			return null;
		}
		ServerPlayer keeper = level.getServer().getPlayerList().getPlayer(m.keeper());
		if (keeper != null && mob.tickCount % 100 == 0 && !held(keeper, mob, m.spirit())) {
			lapse(mob, m);
			return null;
		}
		long now = level.getGameTime();
		if (m.spirit() == Spirits.VARRO && mob.tickCount % 20 == 0) {
			kiMark(level, mob);
		}
		boolean here = keeper != null && keeper.level() == level;
		switch (m.spirit()) {
			case Spirits.VARRO -> {
				Long until = JOINED.get(mob.getUUID());
				if (until != null && now < until) {
					LivingEntity foe = here ? foeOf(keeper) : null;
					if (foe != null && mob.tickCount % 10 == 0) {
						Rulers.charge(mob, foe);
					}
					return false;
				}
				if (until != null) {
					JOINED.remove(mob.getUUID());
				}
				if (here && mob.tickCount % 20 == 0 && !Downed.isDowned(mob) && mob.distanceTo(keeper) < JOIN_REACH) {
					join(level, mob, keeper, now);
				}
				return null;
			}
			case Spirits.GRIMSBY -> {
				LivingEntity foe = here && mob.distanceTo(keeper) < OATH_REACH ? foeOf(keeper) : null;
				if (foe != null && !Downed.isDowned(mob)) {
					if (mob.tickCount % 10 == 0) {
						Rulers.charge(mob, foe);
					}
					return false;
				}
				return null;
			}
			case Spirits.THESSALY -> {
				if (keeper != null && mob.tickCount % 200 == 0) {
					eyes(level, mob, keeper, now);
				}
				return null;
			}
			default -> {
				// Ambrose's and the Quiet One's: at your side
				if (here) {
					follow(level, mob, keeper, m.spirit());
				}
				return false;
			}
		}
	}

	/** {@code e}'s bond lapsed: he's his own (and his camp's) again. */
	private static void lapse(LivingEntity e, Mark m) {
		unmark(e);
		JOINED.remove(e.getUUID());
		e.removeEffect(MobEffects.INVISIBILITY);
		if (e instanceof Villager v && (m.spirit() == Spirits.AMBROSE || m.spirit() == Spirits.QUIET)) {
			McaCompat.stopFollowing(v);
		}
	}

	/** Varro's mark on his brow, shown to the ki users near. */
	private static void kiMark(ServerLevel level, LivingEntity mob) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceTo(mob) < MARK_SEEN && "zfighter".equals(PowersCompat.power(p))) {
				Vec3 brow = mob.getEyePosition().add(mob.getLookAngle().scale(0.25)).add(0.0, 0.15, 0.0);
				level.sendParticles(p, new DustParticleOptions(0xFFB347, 0.7F), true, false, brow.x, brow.y, brow.z, 2, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}

	/** A marked one hears his keeper's fight: now and then he comes, with a film and a battle cry. */
	private static void join(ServerLevel level, PathfinderMob mob, ServerPlayer keeper, long now) {
		LivingEntity foe = foeOf(keeper);
		if (foe == null || foe == mob || now - LAST.getOrDefault(mob.getUUID(), -JOIN_AGAIN) < JOIN_AGAIN) {
			return;
		}
		LAST.put(mob.getUUID(), now);
		if (level.getRandom().nextFloat() >= JOIN_CHANCE) {
			return;
		}
		JOINED.put(mob.getUUID(), now + JOIN_TICKS);
		if (mob.distanceTo(keeper) > OATH_REACH) {
			// (he comes at a run: out of the brush beside them)
			Vec3 at = beside(level, keeper);
			mob.teleportTo(at.x, at.y, at.z);
		}
		PowersCompat.film(mob, keeper, PowersCompat.FILM_PUSH_IN, JOIN_FILM, mob.getEyePosition(), mob, FILM_WITNESSES);
		Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.inbalm.varro.cry", keeper.getDisplayName()), Speech.RED, SAY_RANGE);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 1.3F);
		Rulers.charge(mob, foe);
	}

	/** Each dawn-ish (a tenth of a day apart): Thessaly tells what she saw through his eyes. */
	private static void eyes(ServerLevel level, LivingEntity mob, ServerPlayer keeper, long now) {
		long day = now / 24000L;
		String seenTag = "jaymoskingdoms.inbalm_told=" + day;
		if (mob.entityTags().contains(seenTag)) {
			return;
		}
		for (String tag : List.copyOf(mob.entityTags())) {
			if (tag.startsWith("jaymoskingdoms.inbalm_told=")) {
				mob.removeTag(tag);
			}
		}
		mob.addTag(seenTag);
		String tribe = Tribes.of(mob);
		if (tribe == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (Tribes.grudge(server, keeper.getUUID(), tribe) >= Tribes.HUNT_AT) {
			Post.tidings(keeper.getUUID(), Component.translatable("tidings.jaymoskingdoms.inbalm.thessaly.hunt", Tribes.name(tribe)));
			return;
		}
		Map.Entry<Captains.Captain, Captains.Trait> e = Captains.unknown(keeper, tribe, w(mob), java.util.EnumSet.allOf(Captains.Sort.class), level.getRandom());
		if (e != null) {
			Captains.learn(keeper, e.getKey().id(), e.getValue());
			Post.tidings(keeper.getUUID(), Component.translatable("tidings.jaymoskingdoms.inbalm.thessaly.saw", Captains.told(e.getKey(), e.getValue())));
		}
	}

	private static int w(LivingEntity mob) {
		return mob instanceof Worker w ? w.factionId() : -1;
	}

	/** Ambrose's and the Quiet One's: at your side (the Quiet One unseen, and ending what you've nearly beaten). */
	private static void follow(ServerLevel level, PathfinderMob mob, ServerPlayer keeper, int spirit) {
		if (mob.tickCount % 20 == 0) {
			if (mob instanceof Villager v && !keeper.getUUID().equals(McaCompat.following(v))) {
				McaCompat.follow(v, keeper);
			}
			if (mob.distanceTo(keeper) > FOLLOW_FAR && keeper.onGround()) {
				Vec3 at = beside(level, keeper);
				mob.teleportTo(at.x, at.y, at.z);
				mob.getNavigation().stop();
			}
			LivingEntity foe = foeOf(keeper);
			if (foe != null && spirit == Spirits.AMBROSE) {
				Rulers.charge(mob, foe);
			}
		}
		if (spirit == Spirits.QUIET) {
			shadow(level, mob, keeper);
		}
	}

	/** The Quiet One's shadow: unseen; out of nothing to end a foe of yours near death, and gone. */
	private static void shadow(ServerLevel level, PathfinderMob mob, ServerPlayer keeper) {
		if (!mob.hasEffect(MobEffects.INVISIBILITY)) {
			mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 60 * 60, 0, false, false));
		}
		long now = level.getGameTime();
		if (mob.tickCount % 10 != 0 || now - LAST.getOrDefault(mob.getUUID(), -SHADOW_AGAIN) < SHADOW_AGAIN) {
			return;
		}
		LivingEntity foe = foeOf(keeper);
		if (foe == null || foe.distanceTo(keeper) > SHADOW_REACH || foe.getHealth() > foe.getMaxHealth() * SHADOW_AT
			|| foe instanceof net.minecraft.world.entity.player.Player || foe.level() != level) {
			return;
		}
		LAST.put(mob.getUUID(), now);
		Vec3 behind = foe.position().subtract(foe.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(1.2));
		mob.teleportTo(behind.x, foe.getY(), behind.z);
		mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
		level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY(1.0), mob.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
		mob.removeEffect(MobEffects.INVISIBILITY);
		mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		foe.hurtServer(level, level.damageSources().mobAttack(mob), Math.max(foe.getHealth() + 1.0F, 10.0F));
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	private static Vec3 beside(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		BlockPos near = player.blockPosition().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
		return Vec3.atBottomCenterOf(Math.abs(top.getY() - player.getBlockY()) <= 3 ? top : player.blockPosition());
	}

	/**
	 * (From {@code McaCompat.foe}) who an in-balmed one goes for: never his keeper or their others; while fighting for them
	 * (Varro's joined, Grimsby's oath, Ambrose's), their foe. Null where it's none of this.
	 */
	public static @Nullable Boolean foe(LivingEntity guard, LivingEntity target) {
		Mark m = mark(guard);
		if (m == null) {
			return null;
		}
		if (m.keeper().equals(target.getUUID())) {
			return false;
		}
		Mark t = mark(target);
		if (t != null && t.keeper().equals(m.keeper()) || SwornCaptains.free(target) && m.keeper().equals(SwornCaptains.lordOf(target))) {
			// (never one of the keeper's others)
			return false;
		}
		return null;
	}

	/** (From {@code Ogre}) an ogre Ambrose calmed: at its keeper's side, going for their foe, never for them. */
	public static void ogreStep(ServerLevel level, Ogre ogre) {
		Mark m = mark(ogre);
		if (m == null || ogre.tickCount % 10 != 0) {
			return;
		}
		ServerPlayer keeper = level.getServer().getPlayerList().getPlayer(m.keeper());
		if (keeper == null || keeper.level() != level) {
			if (ogre.getTarget() != null && ogre.getTarget().getUUID().equals(m.keeper())) {
				ogre.setTarget(null);
			}
			return;
		}
		if (ogre.tickCount % 100 == 0 && !held(keeper, ogre, m.spirit())) {
			unmark(ogre);
			return;
		}
		LivingEntity target = ogre.getTarget();
		if (target == keeper || target != null && spares(target, keeper)) {
			ogre.setTarget(null);
		}
		LivingEntity foe = foeOf(keeper);
		if (foe != null && foe != ogre) {
			ogre.setTarget(foe);
		} else if (ogre.getTarget() == null) {
			double d = ogre.distanceTo(keeper);
			if (d > FOLLOW_FAR && keeper.onGround()) {
				Vec3 at = beside(level, keeper);
				ogre.teleportTo(at.x, at.y, at.z);
			} else if (d > FOLLOW_NEAR) {
				ogre.getNavigation().moveTo(keeper, 1.0);
			}
		}
	}
}
