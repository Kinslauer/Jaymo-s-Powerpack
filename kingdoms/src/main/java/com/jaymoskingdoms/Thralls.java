package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.35) The risen dead: vanilla zombies, skeletons (and their kin) raised by a necromancer ({@link Sorcery}) or a
 * player's Scroll of Raising ({@link Scrolls}). Each carries a {@link #TAG}: the side it fights for (a faction, or a
 * player), who raised it, and when it falls back to dust. A thrall never turns on its own side ({@link #refuses}, through
 * {@code ThrallTargetMixin}), and its side's folk leave it be. It goes for its master's foe, keeps near its master, and
 * crumbles when its time is up or its master is gone. A grey rag on its head keeps the sun off.
 */
public final class Thralls {
	/** How often they look about (ticks). */
	static final int THINK_TICKS = 10;
	/** How far a thrall strays from its master before it's called back, and how far it looks for a foe of its own. */
	static final double LEASH = 10.0, SIGHT = 16.0;
	/** The rag it wears (a rotten grey). */
	static final int RAG = 0x5F5B52;

	/** What a thrall is: its side (a faction id; -1 for a player's), who raised it, whether that was a player, and the game time it crumbles. */
	public record Tag(int faction, UUID master, boolean player, long expires) {
		public static final Codec<Tag> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("faction").forGetter(Tag::faction),
				UUIDUtil.CODEC.fieldOf("master").forGetter(Tag::master),
				Codec.BOOL.optionalFieldOf("player", false).forGetter(Tag::player),
				Codec.LONG.fieldOf("expires").forGetter(Tag::expires))
			.apply(i, Tag::new));
	}

	static final AttachmentType<Tag> TAG = AttachmentRegistry.create(JaymosKingdoms.id("thrall"), builder -> builder.persistent(Tag.CODEC));

	private static final Set<Mob> LOADED = new HashSet<>();

	private Thralls() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && tag(mob) != null) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				LOADED.remove(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Thralls::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
	}

	public static @Nullable Tag tag(Entity entity) {
		return entity.getAttached(TAG);
	}

	public static boolean isThrall(Entity entity) {
		return entity instanceof Mob && tag(entity) != null;
	}

	/** How many thralls {@code master} has up now. */
	static int count(UUID master) {
		int n = 0;
		for (Mob mob : LOADED) {
			Tag tag = tag(mob);
			if (tag != null && mob.isAlive() && tag.master().equals(master)) {
				n++;
			}
		}
		return n;
	}

	/** {@code master}'s thralls, now. */
	static List<Mob> of(UUID master) {
		List<Mob> out = new ArrayList<>();
		for (Mob mob : LOADED) {
			Tag tag = tag(mob);
			if (tag != null && mob.isAlive() && tag.master().equals(master)) {
				out.add(mob);
			}
		}
		return out;
	}

	/**
	 * Raises a thrall of {@code type} at {@code at} for {@code faction} (or for the player {@code master} if {@code player}),
	 * for {@code life} ticks, set on {@code foe} if there is one. It climbs out of the ground in a burst of souls.
	 */
	static @Nullable Mob raise(ServerLevel level, EntityType<? extends Mob> type, Vec3 at, int faction, UUID master, boolean player, int life,
		@Nullable LivingEntity foe) {
		Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		if (mob instanceof Zombie zombie) {
			zombie.setBaby(false);
		}
		if (type == net.minecraft.world.entity.EntityTypes.SKELETON || type == net.minecraft.world.entity.EntityTypes.STRAY
			|| type == net.minecraft.world.entity.EntityTypes.BOGGED) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		} else if (mob instanceof Zombie && level.getRandom().nextInt(3) == 0) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(level.getRandom().nextBoolean() ? Items.STONE_SWORD : Items.IRON_SHOVEL));
		}
		if (!(mob instanceof AbstractHorse)) {
			ItemStack rag = new ItemStack(Items.LEATHER_HELMET);
			rag.set(DataComponents.DYED_COLOR, new DyedItemColor(RAG));
			mob.setItemSlot(EquipmentSlot.HEAD, rag);
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
		mob.setCanPickUpLoot(false);
		mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 8.0);
		mob.setAttached(TAG, new Tag(faction, master, player, level.getGameTime() + life));
		level.addFreshEntity(mob);
		LOADED.add(mob);
		if (foe != null && foe.isAlive() && !refuses(mob, foe)) {
			mob.setTarget(foe);
		}
		level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.3, at.z, 14, 0.35, 0.2, 0.35, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.1, at.z, 8, 0.35, 0.1, 0.35, 0.01);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F, 0.7F);
		return mob;
	}

	/** Falls back to dust: a puff of souls and bone meal, and it's gone. */
	static void crumble(ServerLevel level, Mob mob) {
		level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
		level.sendParticles(ParticleTypes.ASH, mob.getX(), mob.getY() + 0.8, mob.getZ(), 30, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_DEATH, SoundSource.HOSTILE, 0.6F, 0.6F);
		LOADED.remove(mob);
		mob.discard();
	}

	/** Every thrall of {@code master} crumbles (its necromancer fell). */
	static void release(ServerLevel level, UUID master) {
		for (Mob mob : of(master)) {
			if (mob.level() instanceof ServerLevel here) {
				crumble(here, mob);
			}
		}
	}

	/** Side {@code a} and side {@code b} are one (the same faction, or allied). */
	static boolean side(@Nullable MinecraftServer server, int a, int b) {
		return a == b || server != null && a >= 0 && b >= 0 && Relations.between(server, a, b) == Relation.ALLIED;
	}

	/**
	 * {@code attacker} won't take {@code target} for a foe: a thrall spares its master and its master's side; its side's
	 * folk spare it. A player's thrall fights only monsters and whoever fights its master, never a player; folk leave it be
	 * unless they hold its master hostile.
	 */
	public static boolean refuses(LivingEntity attacker, LivingEntity target) {
		Tag a = tag(attacker), t = tag(target);
		if (a == null && t == null) {
			return false;
		}
		MinecraftServer server = attacker.level().getServer();
		if (a != null) {
			if (target.getUUID().equals(a.master())) {
				return true;
			}
			if (a.player()) {
				if (target instanceof Player || t != null && t.master().equals(a.master())) {
					return true;
				}
				ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(a.master());
				if (owner != null && (target == owner.getLastHurtByMob() || target == owner.getLastHurtMob())) {
					return false;
				}
				return !(target instanceof Enemy);
			}
			if (t != null) {
				return !t.player() && side(server, a.faction(), t.faction());
			}
			if (target instanceof Folk folk) {
				return side(server, a.faction(), folk.factionId());
			}
			// a court necromancer's dead spare the realm's villagers, and every player it doesn't hold hostile
			Faction realm = server == null ? null : Factions.get(server, a.faction());
			if (realm != null && realm.type().isRealm()) {
				if (target instanceof ServerPlayer p) {
					return Reputation.get(p, a.faction()) > Reputation.HOSTILE;
				}
				return target instanceof net.minecraft.world.entity.npc.villager.AbstractVillager;
			}
			return false;
		}
		if (attacker.getUUID().equals(t.master())) {
			return true;
		}
		if (t.player()) {
			if (attacker instanceof Folk folk) {
				ServerPlayer owner = server == null ? null : server.getPlayerList().getPlayer(t.master());
				return owner == null || Reputation.get(owner, folk.factionId()) > Reputation.HOSTILE;
			}
			return false;
		}
		return attacker instanceof Folk folk && side(server, folk.factionId(), t.faction());
	}

	private static void tick(MinecraftServer server) {
		if (LOADED.isEmpty()) {
			return;
		}
		long now = Factions.level(server).getGameTime();
		if (now % THINK_TICKS != 0) {
			return;
		}
		for (Mob mob : List.copyOf(LOADED)) {
			if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				LOADED.remove(mob);
				continue;
			}
			Tag tag = tag(mob);
			if (tag == null) {
				LOADED.remove(mob);
				continue;
			}
			LivingEntity master = tag.player() ? server.getPlayerList().getPlayer(tag.master())
				: level.getEntity(tag.master()) instanceof LivingEntity l ? l : null;
			if (level.getGameTime() >= tag.expires() || master == null || !master.isAlive() || master.level() != level) {
				crumble(level, mob);
				continue;
			}
			think(level, mob, tag, master);
		}
	}

	/** Takes up its master's fight (or the nearest foe in sight); with none, keeps near its master. */
	private static void think(ServerLevel level, Mob mob, Tag tag, LivingEntity master) {
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive()) {
			LivingEntity wanted = master instanceof Mob m ? m.getTarget() : master.getLastHurtByMob() != null ? master.getLastHurtByMob() : master.getLastHurtMob();
			if (wanted != null && wanted.isAlive() && wanted != mob && !refuses(mob, wanted) && wanted.distanceTo(mob) < SIGHT * 2) {
				mob.setTarget(wanted);
				return;
			}
			if (tag.player()) {
				LivingEntity monster = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(SIGHT),
						m -> m.isAlive() && m instanceof Enemy && tag(m) == null && mob.getSensing().hasLineOfSight(m)).stream()
					.min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
				if (monster != null) {
					mob.setTarget(monster);
					return;
				}
			}
			if (mob.distanceTo(master) > LEASH) {
				mob.getNavigation().moveTo(master, 1.1);
			}
		}
	}

	static void clear() {
		LOADED.clear();
	}
}
