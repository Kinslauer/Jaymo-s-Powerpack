package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Octo Core (Andy: "adapt the octopus arms to another rig chestpiece, available through the tinkerer ... and to web
 * tinkerers who find a rig and use the forge. they can fashion tendril like abilities through the 4 mechanical limbs rather
 * than biological ones through the symbiote"; the arms are an idea from the Black Panther mod's waldo harness, CC0, our own
 * build). A Rig Core built at the Forge with four jointed arms folded down its spine ({@link #OCTO} on the Core). Frostborn Rig
 * tech, the Mad Tinkerer's copy of what a symbiote's tendrils do: plates and pistons, nothing grown or alive.
 * <p>Only a Tinkerer (Kingdoms' calling) or a web slinger can set the Forge to build one ({@link #canBuild}). Everything it does
 * is a Rig menu entry (Sneak + J, then J), shown only while one is worn; each needs the Octo Core worn (not the whole set:
 * the arms carry themselves) and costs the Core's charge:
 * <ul>
 * <li><b>Arms out</b> ({@link #ARMS}): unfold the four arms, or fold them away. While they are out the two lower arms grip
 * walls (you climb as on a ladder: the symbiote's wall crawl) and every full-strength blow you land is followed by one of the
 * arms ({@link #flurry}: lance, crescent, clamp, launch in turn, the tendril whip's combo).</li>
 * <li><b>Arm grab</b> ({@link #GRAB}): the upper arms snatch what you look at (a creature, a player, an item, a corpse) and
 * hold it up in front of you; pressed again, they hurl it, and it hurts what it hits. With Sneak, aimed at a wall or the
 * ground, they tear a chunk loose to hold and throw instead (the symbiote's rip).</li>
 * <li><b>Skewer and hoist</b> ({@link #SKEWER}): the lower arms run the foe through, lift it over your head and slam it
 * down.</li>
 * <li><b>Pinwheel</b> ({@link #PINWHEEL}): all four spin round you: arrows and other shots are batted back at whoever sent
 * them, and whatever stands in reach is struck again and again (the symbiote's reflexes).</li>
 * <li><b>Arm vault</b> ({@link #VAULT}): the lower arms plant and fling you forward and up; pressed again in the air, you
 * come down arms first on what's below (the skewer dive).</li>
 * <li><b>Arm anchor</b> ({@link #ANCHOR}): one arm shoots out on its telescoping struts: at a wall it hauls you there, at a
 * creature it hauls it to you (the tendril zip).</li>
 * <li><b>Arm watch</b> ({@link #WATCH}): a switch: the arms fight on their own, striking the nearest monster in reach and
 * batting away shots, each for a little charge, until the Core runs low.</li>
 * </ul>
 * Mind levels and costs are below. Menu entries and moves are numbered clear of the other Rig patches'.
 */
public final class RigOcto {
	// ---- tuning ----
	/** Rig menu entries ({@link RigPicks}). */
	public static final int ARMS = 80, GRAB = 81, SKEWER = 82, PINWHEEL = 83, VAULT = 84, ANCHOR = 85, WATCH = 86;
	/** Synced moves ({@link RigMoves}). */
	public static final int MOVE_GRAB = 80, MOVE_HOLD = 81, MOVE_HURL = 82, MOVE_SKEWER = 83, MOVE_PINWHEEL = 84, MOVE_VAULT = 85,
		MOVE_DIVE = 86, MOVE_ANCHOR = 87, MOVE_LANCE = 88, MOVE_CRESCENT = 89, MOVE_CLAMP = 90, MOVE_LAUNCH = 91, MOVE_WATCH = 92;
	public static final int GRAB_TICKS = 8, HOLD_TICKS = 300, HURL_TICKS = 8, SKEWER_TICKS = 22, PINWHEEL_TICKS = 50, VAULT_TICKS = 14,
		DIVE_TICKS = 30, ANCHOR_TICKS = 12, STRIKE_TICKS = 8, WATCH_STRIKE_TICKS = 6;
	/** Mind levels. */
	public static final int ARMS_LEVEL = 1, GRAB_LEVEL = 1, SKEWER_LEVEL = 2, PINWHEEL_LEVEL = 3, VAULT_LEVEL = 2, ANCHOR_LEVEL = 2, WATCH_LEVEL = 3;
	/** Charge each costs (the flurry and the watch per blow or shot). */
	static final int GRAB_COST = 20, RIP_COST = 30, SKEWER_COST = 45, PINWHEEL_COST = 50, VAULT_COST = 20, DIVE_COST = 25, ANCHOR_COST = 20,
		FLURRY_COST = 4, WATCH_COST = 3;
	/** Arm grab: reach (blocks), how far ahead it is held and how far down, the steer toward that spot, the hurl's speed; the rip. */
	static final double GRAB_RANGE = 9.0, HOLD_FORWARD = 2.1, HOLD_DROP = 0.4, HOLD_STEER = 0.45, HOLD_MAX_SPEED = 1.4, HURL_SPEED = 2.2;
	static final int RIP_PIECES = 6;
	static final float RIP_HARDEST = 4.0F;
	/** What the arms can't lift: anything with more health than this. */
	static final float MOST_HEALTH = 100.0F;
	/** A hurled body: hurt by its speed when it first hits something (at least the minimum), for this long after the throw. */
	static final float IMPACT_MIN = 4.0F, IMPACT_PER_SPEED = 6.0F;
	static final int THROWN_TICKS = 40;
	/** Skewer: reach on the look and round the front (blocks), the stab, the slam, the splash and its reach; what counts as too big to lift. */
	static final double SKEWER_REACH = 12.0, SKEWER_NEAR = 7.0, SPLASH_REACH = 3.5, LIFT_HEIGHT = 3.5;
	static final float STAB_DAMAGE = 8.0F, SLAM_DAMAGE = 14.0F, SPLASH_DAMAGE = 6.0F;
	static final int LIFT_TICKS = 10;
	/** Pinwheel: reach for shots and blows (blocks), the blow and how often. */
	static final double SPIN_SHOT_REACH = 3.0, SPIN_REACH = 2.8;
	static final float SPIN_DAMAGE = 3.0F;
	static final int SPIN_EVERY = 5;
	/** Vault: the fling (forward, up), the window to dive in (ticks); the dive's fall, its reach and blow. */
	static final double VAULT_FORWARD = 1.5, VAULT_UP = 1.0, DIVE_FALL = 2.2, DIVE_REACH = 5.0;
	static final int VAULT_WINDOW = 40;
	static final float DIVE_DAMAGE = 14.0F;
	/** Anchor: reach (blocks) and pull (blocks a tick). */
	static final double ANCHOR_RANGE = 20.0, ANCHOR_PULL = 1.4;
	/** The flurry: a step's blow on top of yours, the reach round the front; how long the chain waits for your next blow (ticks). */
	static final float LANCE_DAMAGE = 4.0F, CRESCENT_DAMAGE = 4.0F, CLAMP_DAMAGE = 5.0F, LAUNCH_DAMAGE = 6.0F;
	static final double FLURRY_REACH = 4.5;
	static final int FLURRY_GAP = 24, CLAMP_TICKS = 30;
	/** The watch: its reach, its blow, how often it strikes (ticks), and when it switches off (charge). */
	static final double WATCH_REACH = 4.5, WATCH_SHOT_REACH = 3.0;
	static final float WATCH_DAMAGE = 5.0F;
	static final int WATCH_EVERY = 10, WATCH_LOW = 40;
	/** Climbing: how close a wall must be (blocks). */
	static final double WALL_GAP = 0.35;
	/** The Forge's price for the Octo Core in the standard frame: salvage points, iron, pistons. */
	static final int OCTO_POINTS = 15, OCTO_IRON = 8, OCTO_PISTONS = 4;
	public static final int FX = 0x5FE6FF;

	/** On a Rig Core: it is an Octo Core. */
	public static DataComponentType<Boolean> OCTO;

	/** What the arms are doing, synced to everyone (client RigOctoClient): {@link #OUT} and {@link #WATCHING} bits. */
	public static final AttachmentType<Integer> STATE = AttachmentRegistry.create(JaymosPowers.id("rig_octo"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	public static final int OUT = 1, WATCHING = 2;
	/** Who the arms hold (entity id), synced to everyone so the arms are drawn reaching for it; -1 for nothing. */
	public static final AttachmentType<Integer> HELD = AttachmentRegistry.create(JaymosPowers.id("rig_octo_held"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final class Hoist {
		final UUID target;
		int tick;

		Hoist(UUID target) {
			this.target = target;
		}
	}

	private static final class Chain {
		int step;
		long last;
	}

	private record Thrown(UUID entity, UUID thrower, long until) {
	}

	private static final Map<UUID, Hoist> HOISTS = new HashMap<>();
	private static final Map<UUID, Integer> SPINS = new HashMap<>();
	/** Vaulted: the game time the dive window closes; diving: the dive's end. */
	private static final Map<UUID, Long> VAULTED = new HashMap<>(), DIVING = new HashMap<>();
	private static final Map<UUID, Chain> CHAINS = new HashMap<>();
	private static final Map<UUID, Long> HOLD_SINCE = new HashMap<>();
	private static final List<Thrown> THROWN = new ArrayList<>();

	private RigOcto() {
	}

	static void init() {
		OCTO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rig_octo"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer server && entity instanceof LivingEntity target && out(server)
				&& player.getAttackStrengthScale(0.5F) >= 0.9F) {
				// the blow lands first; the arm follows it in
				RigActives.later(server, 2, () -> flurry(server, target));
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOISTS.clear();
			SPINS.clear();
			VAULTED.clear();
			DIVING.clear();
			CHAINS.clear();
			HOLD_SINCE.clear();
			THROWN.clear();
		});
	}

	// ---- the Core ---------------------------------------------------------------------------------------------------------

	/** Whether {@code stack} is an Octo Core. */
	public static boolean is(ItemStack stack) {
		return OCTO != null && !stack.isEmpty() && stack.is(Rig.CORE) && stack.getOrDefault(OCTO, false);
	}

	/** Whether {@code player} wears an Octo Core (both sides). */
	public static boolean wears(Player player) {
		return is(Rig.core(player));
	}

	/** Makes a freshly built Core an Octo Core: the flag, its own name and its own icon. */
	static void make(ItemStack core) {
		core.set(OCTO, true);
		core.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.rig_octo_core"));
		core.set(DataComponents.ITEM_MODEL, JaymosPowers.id("rig_octo_core"));
	}

	/** An Octo Core as the creative tab shows it. */
	static ItemStack creativeStack() {
		ItemStack core = new ItemStack(Rig.CORE);
		make(core);
		return core;
	}

	/**
	 * Who may set the Forge to build one: a Tinkerer (Kingdoms' calling, who learned the trade from the Mad Tinkerer's
	 * drawings) or a web slinger (who knows what a tendril does from the inside), and anyone in creative.
	 */
	public static boolean canBuild(ServerPlayer player) {
		var power = Powers.of(player);
		return player.isCreative() || com.jaymospowers.compat.KingdomsCompat.tinkerer(player)
			|| power != null && com.jaymospowers.power.web.WebPower.ID.equals(power.id());
	}

	/** Its price at the Forge in {@code frame}: the Core's frame scaling, more salvage and iron, and pistons for the joints. */
	public static RigForgeBlock.Cost cost(RigFrame frame) {
		return new RigForgeBlock.Cost(frame.points(OCTO_POINTS), net.minecraft.world.item.Items.IRON_INGOT, frame.iron(OCTO_IRON),
			net.minecraft.world.item.Items.PISTON, frame == RigFrame.SCRAP ? OCTO_PISTONS / 2 : OCTO_PISTONS);
	}

	// ---- state ------------------------------------------------------------------------------------------------------------

	private static int bits(Player player) {
		Integer state = player.getAttached(STATE);
		return state == null ? 0 : state;
	}

	/** Whether the arms are out (both sides). */
	public static boolean out(Player player) {
		return (bits(player) & OUT) != 0 && wears(player);
	}

	/** Whether the arm watch is on (both sides). */
	public static boolean watching(Player player) {
		return (bits(player) & WATCHING) != 0 && out(player);
	}

	private static void setBits(ServerPlayer player, int bits) {
		if (bits == 0) {
			player.removeAttached(STATE);
		} else if (bits(player) != bits) {
			player.setAttached(STATE, bits);
		}
	}

	/** The entity the arms hold (both sides), or -1. */
	public static int heldId(Player player) {
		Integer id = player.getAttached(HELD);
		return id == null ? -1 : id;
	}

	/** Whether the lower arms have a wall to grip: arms out and a wall within {@link #WALL_GAP} to the side. */
	public static boolean clinging(Player player) {
		if (!out(player) || player.onGround() || player.isPassenger()) {
			return false;
		}
		AABB box = player.getBoundingBox();
		return !player.level().noCollision(player, box.inflate(WALL_GAP, -0.1, 0.0)) || !player.level().noCollision(player, box.inflate(0.0, -0.1, WALL_GAP));
	}

	// ---- the menu ---------------------------------------------------------------------------------------------------------

	/** Whether {@code action} is one of these menu entries. */
	public static boolean is(int action) {
		return action >= ARMS && action <= WATCH;
	}

	/** How long one of these moves plays (ticks), or 0. */
	static int length(int kind) {
		return switch (kind) {
			case MOVE_GRAB -> GRAB_TICKS;
			case MOVE_HOLD -> HOLD_TICKS;
			case MOVE_HURL -> HURL_TICKS;
			case MOVE_SKEWER -> SKEWER_TICKS;
			case MOVE_PINWHEEL -> PINWHEEL_TICKS;
			case MOVE_VAULT -> VAULT_TICKS;
			case MOVE_DIVE -> DIVE_TICKS;
			case MOVE_ANCHOR -> ANCHOR_TICKS;
			case MOVE_LANCE, MOVE_CRESCENT, MOVE_CLAMP, MOVE_LAUNCH -> STRIKE_TICKS;
			case MOVE_WATCH -> WATCH_STRIKE_TICKS;
			default -> 0;
		};
	}

	static void use(ServerPlayer player, int action) {
		if (!wears(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_octo.needs_core").withStyle(ChatFormatting.GRAY));
			return;
		}
		int level = switch (action) {
			case ARMS -> ARMS_LEVEL;
			case GRAB -> GRAB_LEVEL;
			case SKEWER -> SKEWER_LEVEL;
			case PINWHEEL -> PINWHEEL_LEVEL;
			case VAULT -> VAULT_LEVEL;
			case ANCHOR -> ANCHOR_LEVEL;
			default -> WATCH_LEVEL;
		};
		if (!RigActives.needs(player, level)) {
			return;
		}
		if (action == ARMS) {
			toggleArms(player);
			return;
		}
		if (!out(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_octo.arms_in").withStyle(ChatFormatting.GRAY));
			return;
		}
		switch (action) {
			case GRAB -> grabOrHurl(player);
			case SKEWER -> {
				if (pay(player, SKEWER_COST)) {
					skewer(player);
				}
			}
			case PINWHEEL -> {
				if (pay(player, PINWHEEL_COST)) {
					pinwheel(player);
				}
			}
			case VAULT -> vaultOrDive(player);
			case ANCHOR -> {
				if (pay(player, ANCHOR_COST)) {
					anchor(player);
				}
			}
			case WATCH -> toggleWatch(player);
			default -> {
			}
		}
	}

	private static boolean pay(ServerPlayer player, int cost) {
		if (Rig.spend(player, cost)) {
			return true;
		}
		RigMind.empty(player);
		return false;
	}

	private static void toggleArms(ServerPlayer player) {
		boolean out = !out(player);
		if (!out) {
			letGo(player, false);
		}
		setBits(player, out ? OUT : 0);
		servo(player, out ? 1.3F : 0.8F);
		player.sendOverlayMessage(Component.translatable(out ? "message.jaymospowers.rig_octo.out" : "message.jaymospowers.rig_octo.in")
			.withStyle(ChatFormatting.AQUA));
	}

	private static void toggleWatch(ServerPlayer player) {
		boolean on = !watching(player);
		if (on && Rig.charge(player) < WATCH_LOW && !player.isCreative()) {
			RigMind.empty(player);
			return;
		}
		setBits(player, OUT | (on ? WATCHING : 0));
		servo(player, on ? 1.6F : 0.9F);
		player.sendOverlayMessage(Component.translatable(on ? "message.jaymospowers.rig_octo.watch_on" : "message.jaymospowers.rig_octo.watch_off")
			.withStyle(ChatFormatting.AQUA));
	}

	private static void servo(ServerPlayer player, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.6F, pitch);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, pitch);
	}

	// ---- arm grab ---------------------------------------------------------------------------------------------------------

	private static void grabOrHurl(ServerPlayer player) {
		Entity held = held(player);
		if (held != null) {
			hurl(player, held);
			return;
		}
		if (player.isShiftKeyDown()) {
			rip(player);
			return;
		}
		Entity target = aimed(player, GRAB_RANGE, e -> com.jaymospowers.power.web.WebTendrils.grabbable(player, e)
			&& !(e instanceof LivingEntity l && l.getMaxHealth() > MOST_HEALTH));
		if (target == null) {
			RigMind.say(player, "octo_none", 1.0F);
			return;
		}
		if (!pay(player, GRAB_COST)) {
			return;
		}
		take(player, target);
		player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	private static void take(ServerPlayer player, Entity target) {
		player.setAttached(HELD, target.getId());
		HOLD_SINCE.put(player.getUUID(), player.level().getGameTime());
		RigMoves.start(player, MOVE_GRAB);
		servo(player, 1.2F);
	}

	/** Sneak + grab at a wall or the ground: a chunk torn loose, held like anything else. */
	private static void rip(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(GRAB_RANGE)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			RigMind.say(player, "octo_none", 1.0F);
			return;
		}
		if (Rig.charge(player) < RIP_COST && !player.isCreative()) {
			RigMind.empty(player);
			return;
		}
		BlockCluster chunk = BlockCluster.rip(level, hit, RIP_PIECES, RIP_HARDEST, player);
		if (chunk == null) {
			RigMind.say(player, "octo_none", 1.0F);
			return;
		}
		Rig.spend(player, RIP_COST);
		take(player, chunk);
		level.playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	private static @Nullable Entity held(ServerPlayer player) {
		int id = heldId(player);
		if (id < 0) {
			return null;
		}
		Entity e = player.level().getEntity(id);
		return e != null && e.isAlive() ? e : null;
	}

	private static void hurl(ServerPlayer player, Entity held) {
		letGo(player, false);
		RigMoves.start(player, MOVE_HURL);
		held.setDeltaMovement(player.getLookAngle().scale(HURL_SPEED).add(0.0, 0.2, 0.0));
		held.needsSync = true;
		held.syncVelocity = true;
		if (held instanceof LivingEntity) {
			THROWN.add(new Thrown(held.getUUID(), player.getUUID(), player.level().getGameTime() + THROWN_TICKS));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** Lets go of what's held (it drops where it is). */
	private static void letGo(ServerPlayer player, boolean drop) {
		if (player.getAttached(HELD) != null) {
			player.removeAttached(HELD);
		}
		HOLD_SINCE.remove(player.getUUID());
	}

	private static void tickHeld(ServerPlayer player, long now) {
		Entity held = held(player);
		Long since = HOLD_SINCE.get(player.getUUID());
		if (held == null || held.level() != player.level() || held.distanceTo(player) > GRAB_RANGE * 2.0 || since == null || now - since > HOLD_TICKS
			|| held instanceof Player p && p.isSpectator()) {
			letGo(player, true);
			return;
		}
		RigMoves.Move move = RigMoves.get(player);
		if (move == null || move.kind() != MOVE_GRAB && move.kind() != MOVE_HOLD) {
			RigMoves.start(player, MOVE_HOLD);
		}
		if (now - since < 3) {
			// still reaching for it
			return;
		}
		Vec3 point = player.getEyePosition().add(player.getLookAngle().scale(HOLD_FORWARD + held.getBbWidth() * 0.5)).subtract(0.0, HOLD_DROP, 0.0);
		Vec3 v = point.subtract(held.getBoundingBox().getCenter()).scale(HOLD_STEER);
		if (v.length() > HOLD_MAX_SPEED) {
			v = v.normalize().scale(HOLD_MAX_SPEED);
		}
		held.setDeltaMovement(v);
		held.needsSync = true;
		held.syncVelocity = true;
		held.resetFallDistance();
		if (held instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	/** Hurled bodies: the first hard knock hurts by how fast they were going. */
	private static void tickThrown(MinecraftServer server) {
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext(); ) {
			Thrown t = it.next();
			ServerPlayer thrower = server.getPlayerList().getPlayer(t.thrower());
			Entity e = thrower == null ? null : thrower.level().getEntity(t.entity());
			if (!(e instanceof LivingEntity body) || !body.isAlive() || thrower.level().getGameTime() >= t.until()) {
				it.remove();
				continue;
			}
			if (body.horizontalCollision || body.verticalCollision && body.getDeltaMovement().y <= 0.0 && thrower.level().getGameTime() + THROWN_TICKS - t.until() > 3) {
				it.remove();
				double speed = body.getDeltaMovement().length();
				ServerLevel level = thrower.level();
				body.hurtServer(level, level.damageSources().playerAttack(thrower), (float)Math.max(IMPACT_MIN, speed * IMPACT_PER_SPEED));
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
		}
	}

	// ---- skewer and hoist -------------------------------------------------------------------------------------------------

	private static void skewer(ServerPlayer player) {
		LivingEntity target = aimed(player, SKEWER_REACH, e -> e instanceof LivingEntity) instanceof LivingEntity l ? l : nearestInFront(player, SKEWER_NEAR);
		if (target == null) {
			// paid: the arms stab at nothing
			RigMoves.start(player, MOVE_SKEWER);
			RigMind.say(player, "octo_none", 1.0F);
			return;
		}
		ServerLevel level = player.level();
		RigMoves.start(player, MOVE_SKEWER);
		target.hurtServer(level, level.damageSources().playerAttack(player), STAB_DAMAGE * RigActives.levelDamage(player));
		MagicFx.stroke(level, player.getEyePosition().subtract(0.0, 0.4, 0.0), target.getBoundingBox().getCenter(), 0.12F, FX);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.2F, 0.8F);
		servo(player, 1.0F);
		HOISTS.put(player.getUUID(), new Hoist(target.getUUID()));
	}

	private static void tickHoist(ServerPlayer player, Hoist hoist) {
		ServerLevel level = player.level();
		Entity e = level.getEntity(hoist.target);
		int tick = hoist.tick++;
		if (!(e instanceof LivingEntity t) || !t.isAlive() || tick > LIFT_TICKS + 8) {
			HOISTS.remove(player.getUUID());
			return;
		}
		boolean heavy = heavy(t);
		if (tick < LIFT_TICKS && !heavy) {
			Vec3 flat = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
			Vec3 anchor = player.position().add(flat.scale(2.4)).add(0.0, LIFT_HEIGHT, 0.0);
			Vec3 at = t.position().lerp(anchor, 0.35);
			t.teleportTo(at.x, at.y, at.z);
			t.setDeltaMovement(Vec3.ZERO);
			t.resetFallDistance();
			return;
		}
		if (tick == LIFT_TICKS && !heavy) {
			t.setDeltaMovement(0.0, -2.6, 0.0);
			t.syncVelocity = true;
			level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.0F, 0.7F);
			return;
		}
		if (heavy || t.onGround() || tick >= LIFT_TICKS + 6) {
			HOISTS.remove(player.getUUID());
			float mul = RigActives.levelDamage(player);
			Vec3 at = t.position();
			t.hurtServer(level, level.damageSources().playerAttack(player), SLAM_DAMAGE * mul);
			for (LivingEntity o : around(player, at, SPLASH_REACH)) {
				if (o != t) {
					o.hurtServer(level, level.damageSources().playerAttack(player), SPLASH_DAMAGE * mul);
					launchAway(o, at, 0.9, 0.5);
				}
			}
			MagicFx.ring(level, at.add(0.0, 0.2, 0.0), (float)SPLASH_REACH, FX);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 0.8F);
		}
	}

	private static boolean heavy(LivingEntity e) {
		return e.getBbHeight() > 3.4F || e.getMaxHealth() >= 300.0F;
	}

	// ---- pinwheel ---------------------------------------------------------------------------------------------------------

	private static void pinwheel(ServerPlayer player) {
		SPINS.put(player.getUUID(), PINWHEEL_TICKS);
		RigMoves.start(player, MOVE_PINWHEEL);
		servo(player, 1.8F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	private static void tickSpin(ServerPlayer player, int left) {
		ServerLevel level = player.level();
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, 1, true, false, false));
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(SPIN_SHOT_REACH),
			p -> p.isAlive() && p.getOwner() != player)) {
			reflect(player, p);
		}
		if (left % SPIN_EVERY == 0) {
			float damage = SPIN_DAMAGE * RigActives.levelDamage(player);
			for (LivingEntity e : around(player, player.position(), SPIN_REACH)) {
				e.hurtServer(level, level.damageSources().playerAttack(player), damage);
				launchAway(e, player.position(), 0.5, 0.15);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.8F);
		}
		if (left <= 1) {
			SPINS.remove(player.getUUID());
		} else {
			SPINS.put(player.getUUID(), left - 1);
		}
	}

	/** A shot batted back the way it came: at whoever sent it, else straight away from you. */
	private static void reflect(ServerPlayer player, Projectile p) {
		Entity shooter = p.getOwner();
		Vec3 dir = shooter != null && shooter.isAlive() ? shooter.position().add(0.0, shooter.getBbHeight() * 0.6, 0.0).subtract(p.position())
			: p.position().subtract(player.position().add(0.0, 1.0, 0.0));
		dir = dir.lengthSqr() < 1.0E-4 ? player.getLookAngle() : dir.normalize();
		p.setDeltaMovement(dir.scale(Math.max(1.0, p.getDeltaMovement().length()) * 1.2));
		p.setOwner(player);
		p.needsSync = true;
		p.syncVelocity = true;
		player.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
	}

	// ---- vault and dive ---------------------------------------------------------------------------------------------------

	private static void vaultOrDive(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long window = VAULTED.get(player.getUUID());
		if (window != null && now <= window && !player.onGround()) {
			if (!pay(player, DIVE_COST)) {
				return;
			}
			VAULTED.remove(player.getUUID());
			DIVING.put(player.getUUID(), now + DIVE_TICKS);
			Vec3 look = player.getLookAngle();
			player.setDeltaMovement(look.x * 0.7, -DIVE_FALL, look.z * 0.7);
			player.syncVelocity = true;
			RigMoves.start(player, MOVE_DIVE);
			servo(player, 0.8F);
			return;
		}
		if (!pay(player, VAULT_COST)) {
			return;
		}
		Vec3 flat = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		player.setDeltaMovement(flat.x * VAULT_FORWARD, VAULT_UP, flat.z * VAULT_FORWARD);
		player.syncVelocity = true;
		player.resetFallDistance();
		VAULTED.put(player.getUUID(), now + VAULT_WINDOW);
		RigMoves.start(player, MOVE_VAULT);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	private static void tickDive(ServerPlayer player, long until, long now) {
		player.resetFallDistance();
		boolean down = player.onGround() || !around(player, player.position(), 1.4).isEmpty();
		if (!down && now < until) {
			Vec3 v = player.getDeltaMovement();
			if (v.y > -1.6) {
				player.setDeltaMovement(v.x, -DIVE_FALL, v.z);
				player.syncVelocity = true;
			}
			return;
		}
		DIVING.remove(player.getUUID());
		ServerLevel level = player.level();
		Vec3 at = player.position();
		float damage = DIVE_DAMAGE * RigActives.levelDamage(player);
		for (LivingEntity e : around(player, at, DIVE_REACH)) {
			e.hurtServer(level, level.damageSources().playerAttack(player), damage);
			launchAway(e, at, 1.1, 0.7);
		}
		MagicFx.ring(level, at.add(0.0, 0.2, 0.0), (float)DIVE_REACH, FX);
		MagicFx.blast(level, at.add(0.0, 0.5, 0.0), 1.2F, FX);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	// ---- anchor -----------------------------------------------------------------------------------------------------------

	private static void anchor(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(ANCHOR_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && e != player, eye.distanceToSqr(end));
		RigMoves.start(player, MOVE_ANCHOR);
		Vec3 from = eye.subtract(0.0, 0.5, 0.0);
		if (hit != null && hit.getEntity() instanceof LivingEntity caught && !heavy(caught)) {
			Vec3 pull = eye.subtract(caught.position()).normalize().scale(ANCHOR_PULL);
			caught.setDeltaMovement(pull.x, Math.max(0.3, pull.y), pull.z);
			caught.syncVelocity = true;
			MagicFx.beam(level, from, caught.getBoundingBox().getCenter(), 0.05F, FX, 8);
		} else if (block.getType() != HitResult.Type.MISS) {
			Vec3 pull = block.getLocation().subtract(player.position()).normalize().scale(ANCHOR_PULL);
			player.setDeltaMovement(pull.x, Math.max(0.35, pull.y + 0.2), pull.z);
			player.syncVelocity = true;
			player.resetFallDistance();
			MagicFx.beam(level, from, block.getLocation(), 0.05F, FX, 8);
		} else {
			MagicFx.beam(level, from, end, 0.05F, FX, 4);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
		servo(player, 1.1F);
	}

	// ---- the flurry -------------------------------------------------------------------------------------------------------

	/** After a full-strength blow with the arms out: the next arm in the chain follows it (lance, crescent, clamp, launch). */
	static void flurry(ServerPlayer player, LivingEntity target) {
		if (!out(player) || !target.isAlive() || player.isSpectator() || held(player) != null) {
			return;
		}
		if (!Rig.spend(player, FLURRY_COST)) {
			return;
		}
		long now = player.level().getGameTime();
		Chain chain = CHAINS.computeIfAbsent(player.getUUID(), k -> new Chain());
		int step = now - chain.last <= FLURRY_GAP ? chain.step : 0;
		chain.step = (step + 1) % 4;
		chain.last = step == 3 ? 0L : now;
		ServerLevel level = player.level();
		float mul = RigActives.levelDamage(player);
		Vec3 hitAt = target.getBoundingBox().getCenter();
		switch (step) {
			case 0 -> {
				// the lance: straight through, and anything just behind it
				RigMoves.start(player, MOVE_LANCE);
				strike(player, target, LANCE_DAMAGE * mul);
				for (LivingEntity e : inArc(player, FLURRY_REACH, 0.8)) {
					if (e != target) {
						strike(player, e, LANCE_DAMAGE * mul);
					}
				}
				level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.9F, 1.4F);
			}
			case 1 -> {
				// the crescent: a wide sweep that throws them back
				RigMoves.start(player, MOVE_CRESCENT);
				for (LivingEntity e : inArc(player, FLURRY_REACH, -0.2)) {
					strike(player, e, CRESCENT_DAMAGE * mul);
					launchAway(e, player.position(), 0.6, 0.2);
				}
				level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.3F);
			}
			case 2 -> {
				// the clamp: two arms close on it and hold it where it stands
				RigMoves.start(player, MOVE_CLAMP);
				strike(player, target, CLAMP_DAMAGE * mul);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CLAMP_TICKS, 9, false, false, true), player);
				level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8F, 1.6F);
			}
			default -> {
				// the launch: up it goes, and those round it are thrown off
				RigMoves.start(player, MOVE_LAUNCH);
				strike(player, target, LAUNCH_DAMAGE * mul);
				if (!heavy(target)) {
					target.setDeltaMovement(target.getDeltaMovement().multiply(0.3, 0.0, 0.3).add(0.0, 1.1, 0.0));
					target.syncVelocity = true;
				}
				for (LivingEntity e : around(player, target.position(), 3.0)) {
					if (e != target) {
						strike(player, e, LAUNCH_DAMAGE * 0.6F * mul);
						launchAway(e, target.position(), 0.8, 0.5);
					}
				}
				MagicFx.ring(level, target.position().add(0.0, 0.2, 0.0), 2.5F, FX);
				level.playSound(null, hitAt.x, hitAt.y, hitAt.z, SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0F, 1.1F);
			}
		}
	}

	private static void strike(ServerPlayer player, LivingEntity e, float damage) {
		// an arm is its own blow, not the one you just landed
		e.setInvulnerableTime(0);
		e.hurtServer(player.level(), player.level().damageSources().playerAttack(player), damage);
	}

	// ---- the watch --------------------------------------------------------------------------------------------------------

	private static void tickWatch(ServerPlayer player, long now) {
		if (!player.isCreative() && Rig.charge(player) < WATCH_LOW) {
			setBits(player, OUT);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_octo.watch_low").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = player.level();
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(WATCH_SHOT_REACH),
			p -> p.isAlive() && p.getOwner() != player && p.getDeltaMovement().dot(player.getBoundingBox().getCenter().subtract(p.position())) > 0.0)) {
			if (!Rig.spend(player, WATCH_COST)) {
				return;
			}
			reflect(player, p);
		}
		if (now % WATCH_EVERY != 0) {
			return;
		}
		LivingEntity foe = null;
		double best = WATCH_REACH * WATCH_REACH;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WATCH_REACH),
			e -> e instanceof Enemy && e.isAlive() && !e.isSpectator() && player.hasLineOfSight(e))) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				foe = e;
			}
		}
		if (foe == null || !Rig.spend(player, WATCH_COST)) {
			return;
		}
		RigMoves.start(player, MOVE_WATCH);
		strike(player, foe, WATCH_DAMAGE * RigActives.levelDamage(player));
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7F, 1.5F + player.getRandom().nextFloat() * 0.2F);
	}

	// ---- shared -----------------------------------------------------------------------------------------------------------

	private static @Nullable Entity aimed(ServerPlayer player, double reach, java.util.function.Predicate<Entity> which) {
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(reach));
		BlockHitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && which.test(e), eye.distanceToSqr(end));
		return hit != null ? hit.getEntity() : null;
	}

	private static @Nullable LivingEntity nearestInFront(ServerPlayer player, double reach) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : inArc(player, reach, 0.4)) {
			double d = e.distanceToSqr(player);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Living things within {@code reach} of {@code player} whose direction from them is within the arc ({@code dot} of the flat look). */
	private static List<LivingEntity> inArc(ServerPlayer player, double reach, double dot) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		Vec3 flat = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : around(player, player.position(), reach)) {
			Vec3 to = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() < 1.0E-4 || to.normalize().dot(flat) >= dot) {
				out.add(e);
			}
		}
		return out;
	}

	private static List<LivingEntity> around(ServerPlayer player, Vec3 at, double reach) {
		return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.position().distanceToSqr(at) <= reach * reach
				&& !(e instanceof Player p && (p.isCreative() || p.isSpectator())));
	}

	private static void launchAway(LivingEntity e, Vec3 from, double push, double lift) {
		Vec3 out = e.position().subtract(from).multiply(1.0, 0.0, 1.0);
		out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize();
		e.setDeltaMovement(e.getDeltaMovement().add(out.x * push, lift, out.z * push));
		e.syncVelocity = true;
	}

	/** Every tick, for every player: the arms' moves under way, the hold, the watch, the wall grip. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		if (!wears(player) || !player.isAlive() || player.isSpectator()) {
			if (player.getAttached(STATE) != null && !wears(player)) {
				// the Core came off: the arms went with it
				player.removeAttached(STATE);
			}
			if (player.getAttached(HELD) != null) {
				letGo(player, true);
			}
			HOISTS.remove(id);
			SPINS.remove(id);
			DIVING.remove(id);
			return;
		}
		long now = player.level().getGameTime();
		if (player.getAttached(HELD) != null) {
			tickHeld(player, now);
		}
		Hoist hoist = HOISTS.get(id);
		if (hoist != null) {
			tickHoist(player, hoist);
		}
		Integer spin = SPINS.get(id);
		if (spin != null) {
			tickSpin(player, spin);
		}
		Long dive = DIVING.get(id);
		if (dive != null) {
			tickDive(player, dive, now);
		}
		if (watching(player)) {
			tickWatch(player, now);
		}
		if (clinging(player)) {
			// the lower arms have the wall: no fall to take
			player.resetFallDistance();
		}
	}

	/** Every tick, once: hurled bodies. */
	static void serverTick(MinecraftServer server) {
		if (!THROWN.isEmpty()) {
			tickThrown(server);
		}
	}
}
