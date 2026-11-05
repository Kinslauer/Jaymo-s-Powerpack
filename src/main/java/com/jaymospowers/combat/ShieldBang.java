package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.web.WebSymbioteBlob;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.3) Andy: "while holding a shield and wielding a weapon, left click slams the shield generating noise and
 * attracting mobs (or repelling sensitive ones...)". With a shield raised in the off hand and a weapon in the main hand,
 * a left click (which does nothing while you block) bangs the weapon on the shield: a loud clang, the weapon arm swings,
 * a vibration for sculk and wardens. Hostile mobs that hear it and aren't already fighting come to see what made it
 * ({@link #DRAW_RANGE}); wild animals, bats and fish bolt away from it ({@link #STARTLE_RANGE}). Neutral mobs (wolves,
 * bees, bears, golems) and anything tamed, ridden or leashed don't care. One bang per click, {@link #COOLDOWN} apart.
 * <p>
 * Andy, then: "holding left click while your shield is up charges a shield bash, it should also deter the symbiote if
 * metal weapons and shields are clanged together".
 * <ul>
 * <li>A click shorter than {@link #BASH_MIN} ticks is the bang. Held longer, a bar fills (full at {@link #BASH_FULL}),
 * and letting go throws the shield forward: you step in, and what's in front of you within reach is struck, shoved back,
 * staggered, and a raised shield knocked aside, all growing with the charge. It costs {@link #BASH_FEATHERS} feathers;
 * with none left you only bang.</li>
 * <li>A metal weapon banged on a metal shield ({@link #metal}: iron, steel, gold, copper, netherite and the like by name,
 * or the {@link #CLANGING_METAL} tag, which holds the iron-rimmed vanilla shield) is a noise the symbiote can't bear:
 * every host within {@link #SYMBIOTE_RANGE} flinches as from a bell, and loose symbiotes run from it.</li>
 * </ul>
 * The client spots the click and times the hold ({@code client/combat/ShieldBangClient}); the server checks the hands
 * again and times the charge itself.
 */
public final class ShieldBang {
	/** Hostiles this far (blocks) come to the noise; skittish mobs this far run from it. */
	public static final double DRAW_RANGE = 24.0, STARTLE_RANGE = 16.0;
	/** Ticks between bangs (and bashes). */
	public static final int COOLDOWN = 10;
	/** Walking pace of a drawn hostile, the sprint of a startled animal (navigation speed factors). */
	public static final double DRAW_SPEED = 1.0, FLEE_SPEED = 1.8;
	/** How far (blocks) a startled mob runs, and how far up or down it may look for the spot. */
	public static final int FLEE_DISTANCE = 14, FLEE_HEIGHT = 6;
	/** Metal on metal: symbiote hosts this close flinch, loose symbiotes this close run. */
	public static final double SYMBIOTE_RANGE = 12.0;
	/** A hold this long (ticks) is a bash, not a bang; the bash is full at the second. */
	public static final int BASH_MIN = 6, BASH_FULL = 30;
	/** The bash at no charge and at full: reach (blocks), damage, shove, stagger (ticks), shield knocked aside (seconds). */
	public static final double BASH_REACH_MIN = 2.2, BASH_REACH_MAX = 3.2, BASH_SHOVE_MIN = 0.5, BASH_SHOVE_MAX = 1.4;
	public static final float BASH_DAMAGE_MIN = 1.0F, BASH_DAMAGE_MAX = 5.0F, BASH_DISABLE_MIN = 1.0F, BASH_DISABLE_MAX = 3.0F;
	public static final int BASH_STUN_MIN = 8, BASH_STUN_MAX = 30;
	/** How wide the bash reaches round your front (degrees each side). */
	public static final double BASH_ANGLE = 50.0;
	public static final int BASH_FEATHERS = 2;
	/** Items that ring as metal whatever they're called. */
	public static final TagKey<Item> CLANGING_METAL = TagKey.create(Registries.ITEM, JaymosPowers.id("clanging_metal"));
	/** Words in an item's name that make it metal. */
	private static final java.util.Set<String> METALS = java.util.Set.of("iron", "steel", "gold", "golden", "copper",
		"netherite", "bronze", "silver", "tin", "brass", "chain", "chainmail", "metal", "cobalt", "mithril", "tungsten",
		"titanium", "platinum", "nickel", "electrum", "manyullyn", "rose", "orichalcum", "adamantite", "palladium");

	/** Client → server: the player banged their shield (a short click). */
	public record Bang() implements CustomPacketPayload {
		public static final Bang INSTANCE = new Bang();
		public static final Type<Bang> TYPE = new Type<>(JaymosPowers.id("shield_bang"));
		public static final StreamCodec<ByteBuf, Bang> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Bang> type() {
			return TYPE;
		}
	}

	/** Client → server: the click is held, a bash charging ({@code start}), or let go to bash ({@code !start}). */
	public record Bash(boolean start) implements CustomPacketPayload {
		public static final Type<Bash> TYPE = new Type<>(JaymosPowers.id("shield_bash"));
		public static final StreamCodec<ByteBuf, Bash> CODEC = ByteBufCodecs.BOOL.map(Bash::new, Bash::start);

		@Override
		public Type<Bash> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** When each charging player's hold began (game time). */
	private static final Map<UUID, Long> CHARGING = new HashMap<>();

	private ShieldBang() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Bang.TYPE, Bang.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Bash.TYPE, Bash.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Bang.TYPE, (payload, context) -> bang(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Bash.TYPE, (payload, context) -> bash(context.player(), payload.start()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST.remove(handler.player.getUUID());
			CHARGING.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LAST.clear();
			CHARGING.clear();
		});
	}

	/**
	 * Whether {@code player} can bang right now (both sides): a shield (something that blocks and isn't a weapon) raised in
	 * the off hand, a weapon in the main hand.
	 */
	public static boolean ready(Player player) {
		return !player.isSpectator() && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND
			&& Guard.raised(player) && !Guard.weaponGuard(player.getUseItem()) && player.getMainHandItem().has(DataComponents.WEAPON);
	}

	/** Whether {@code stack} rings as metal: the {@link #CLANGING_METAL} tag, or a metal's name in its id. */
	public static boolean metal(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(CLANGING_METAL)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
		for (String word : path.split("[_./]")) {
			if (METALS.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/** Off cooldown: marks it used and returns true. */
	private static boolean take(ServerPlayer player, long now) {
		Long last = LAST.get(player.getUUID());
		// a couple of ticks' grace for network jitter
		if (last != null && now - last < COOLDOWN - 2) {
			return false;
		}
		LAST.put(player.getUUID(), now);
		return true;
	}

	private static void bang(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!ready(player) || !take(player, level.getGameTime())) {
			return;
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		float pitch = 0.75F + level.getRandom().nextFloat() * 0.2F;
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 2.0F, pitch);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.4F + pitch * 0.3F);
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0.0, -0.4, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.25);
		level.gameEvent(GameEvent.ENTITY_ACTION, player.position(), GameEvent.Context.of(player));
		boolean clang = metal(player.getMainHandItem()) && metal(player.getUseItem());
		if (clang) {
			// metal on metal: the ring the symbiote can't bear
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 1.6F + pitch * 0.4F);
			WebSymbiote.noise(level, player.position(), SYMBIOTE_RANGE);
		}
		Vec3 from = player.position();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DRAW_RANGE), mob -> mob.isAlive() && !mob.isNoAi())) {
			double distance = mob.distanceToSqr(player);
			if (mob instanceof WebSymbioteBlob blob) {
				if (clang && distance <= SYMBIOTE_RANGE * SYMBIOTE_RANGE) {
					blob.startle(from);
				}
			} else if (skittish(mob)) {
				if (distance <= STARTLE_RANGE * STARTLE_RANGE && mob instanceof PathfinderMob runner) {
					Vec3 away = DefaultRandomPos.getPosAway(runner, FLEE_DISTANCE, FLEE_HEIGHT, from);
					if (away != null) {
						send(mob, away, FLEE_SPEED);
					}
				}
			} else if (mob instanceof Enemy && !(mob instanceof NeutralMob) && mob.getTarget() == null && distance <= DRAW_RANGE * DRAW_RANGE) {
				send(mob, from, DRAW_SPEED);
			}
		}
	}

	/** The held click: {@code start} marks the charge's beginning, its end throws the bash. */
	private static void bash(ServerPlayer player, boolean start) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (start) {
			if (ready(player)) {
				CHARGING.put(player.getUUID(), now);
			}
			return;
		}
		Long began = CHARGING.remove(player.getUUID());
		if (began == null || !ready(player)) {
			return;
		}
		// the client sends the start after BASH_MIN ticks of holding
		long held = now - began + BASH_MIN;
		if (!Feathers.spend(player, BASH_FEATHERS)) {
			bang(player);
			return;
		}
		if (!take(player, now)) {
			return;
		}
		float charge = Math.clamp((held - BASH_MIN) / (float) (BASH_FULL - BASH_MIN), 0.0F, 1.0F);
		double reach = lerp(charge, BASH_REACH_MIN, BASH_REACH_MAX);
		float damage = (float) lerp(charge, BASH_DAMAGE_MIN, BASH_DAMAGE_MAX);
		double shove = lerp(charge, BASH_SHOVE_MIN, BASH_SHOVE_MAX);
		int stun = (int) Math.round(lerp(charge, BASH_STUN_MIN, BASH_STUN_MAX));
		float disable = (float) lerp(charge, BASH_DISABLE_MIN, BASH_DISABLE_MAX);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
		Vec3 eye = player.getEyePosition();
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		boolean hit = false;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e))) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(player.position());
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			if (Math.sqrt(target.getBoundingBox().distanceToSqr(eye)) > reach || flat.lengthSqr() < 1.0E-4
				|| flat.normalize().dot(look) < Math.cos(Math.toRadians(BASH_ANGLE))) {
				continue;
			}
			hit = true;
			if (target.isBlocking()) {
				// a shield bash knocks a raised shield aside, as an axe does
				ItemStack guard = target.getUseItem();
				BlocksAttacks blocks = guard.get(DataComponents.BLOCKS_ATTACKS);
				if (blocks != null) {
					blocks.disable(level, target, disable, guard);
				}
				target.stopUsingItem();
			}
			target.hurtServer(level, level.damageSources().playerAttack(player), damage);
			Vec3 away = flat.normalize();
			target.push(away.x * shove, 0.2 + 0.15 * charge, away.z * shove);
			target.needsSync = true;
			DarktideStatus.stun(target, stun);
			Vec3 at = target.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10, 0.25, 0.3, 0.25, 0.35);
		}
		if (hit) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4F, 0.6F);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
	}

	private static double lerp(float t, double from, double to) {
		return from + (to - from) * t;
	}

	/** Wild, untamed, unridden animals, bats and fish: they run from the noise. */
	private static boolean skittish(Mob mob) {
		MobCategory category = mob.getType().getCategory();
		boolean shy = mob instanceof Animal || mob instanceof AmbientCreature
			|| category == MobCategory.WATER_AMBIENT || category == MobCategory.WATER_CREATURE;
		if (!shy || mob instanceof Enemy || mob instanceof NeutralMob || mob.isLeashed() || mob.isVehicle() || mob.isPassenger()) {
			return false;
		}
		return !(mob instanceof TamableAnimal pet && pet.isTame() || mob instanceof AbstractHorse horse && horse.isTamed());
	}

	/** Walk (or run) {@code mob} to {@code to}: its navigation for goal mobs, its walk target for brain mobs. */
	private static void send(Mob mob, Vec3 to, double speed) {
		mob.getNavigation().moveTo(to.x, to.y, to.z, speed);
		// a brain that doesn't keep a walk target ignores this
		mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, (float) speed, 2));
	}
}
