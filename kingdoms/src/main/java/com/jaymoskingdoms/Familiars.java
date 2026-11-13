package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A familiar (fifth edition's find familiar): a small spirit in animal shape, an owl, a cat, a rat, a raven or a frog, bound
 * by a ritual (ten seconds still with a book open, and a lure the animal likes: a feather, a fish, wheat, gold or slime) and kept for good.
 * <ul>
 * <li><b>It follows and scouts.</b> It keeps close; if you are farther than {@link #TELEPORT_AT} it is simply there beside you
 * again (it is a spirit). When a monster that is not yet near you is in its sight, it calls once, and a mark shows over the
 * monster to your eyes alone.</li>
 * <li><b>Through its eyes.</b> Hold the familiar key (default Page Down): your view is the familiar's. It goes where you
 * look (sneak to hold it still), within {@link #STEER_MAX} blocks of you. Your body stands slow and open to harm, and a blow
 * to you closes the eyes. Spells that need a target and a touch ({@link MindSpells#target}) land on whoever the familiar
 * is touching, which is how it delivers a touch spell.</li>
 * <li><b>It carries.</b> Use an item on it and it holds it (use with an empty hand to take it back). "Send it" on the Magic
 * screen has it fly or run to whoever you look at, or the spot, and hand the item over (a key, a note) or drop it there.</li>
 * <li><b>If it is killed</b> it leaves a pinch of ash. The rite of calling it back, with the ash in your pack, brings it again.</li>
 * </ul>
 */
public final class Familiars {
	static final String TAG = "jaymoskingdoms_familiar", OF = "jaymoskingdoms_familiar_of_";
	static final String ASH_KIND = "jaymoskingdoms_familiar_kind";
	/** How close it keeps (blocks), when it comes running, and when it simply appears beside you instead. */
	static final double KEEP = 5.0, CLOSE = 3.0, TELEPORT_AT = 40.0;
	/** The pace on foot; the flight speed (blocks a tick) when it is sent or steered. */
	static final double WALK = 1.3, FLY = 0.3;
	/** Through its eyes: how far from you it may go, how far it feels (touch), the slowness on your body. */
	static final double STEER_MAX = 48.0, TOUCH_REACH = 3.0;
	static final int SLOW_LEVEL = 3, HEARD_TICKS = 10;
	/** An errand: how near is arrived, the longest it takes, the longest sight of it to send it (blocks). */
	static final double ARRIVED = 2.0, SEND_REACH = 48.0;
	static final int ERRAND_TICKS = 20 * 60;
	/** Scouting: how far it sees a threat (blocks), how near the owner a threat must not yet be, and how long before it calls again for the same one. */
	static final double SCOUT_SIGHT = 22.0, SCOUT_OUTSIDE = 12.0;
	static final long SCOUT_AGAIN = 400L;
	/** How soon after joining (ticks) a missing familiar is made again beside its owner. */
	static final int REMAKE_AFTER = 100;
	static final int COLOUR = 0xC9B8FF;

	/** What each kind is lured with at the binding. */
	static final Map<String, Item> LURES = Map.of("owl", Items.FEATHER, "cat", Items.COD, "rat", Items.WHEAT, "raven", Items.GOLD_NUGGET, "frog", Items.SLIME_BALL);

	/** Client to server: the familiar key went down or up. */
	public record Eyes(boolean on) implements CustomPacketPayload {
		public static final Type<Eyes> TYPE = new Type<>(JaymosKingdoms.id("familiar_eyes"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Eyes> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Eyes::on, Eyes::new);

		@Override
		public Type<Eyes> type() {
			return TYPE;
		}
	}

	/** Server to client: see through the entity with this id (or -1: back to your own eyes). */
	public record Seeing(int entity) implements CustomPacketPayload {
		public static final Type<Seeing> TYPE = new Type<>(JaymosKingdoms.id("familiar_seeing"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Seeing> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Seeing::entity, Seeing::new);

		@Override
		public Type<Seeing> type() {
			return TYPE;
		}
	}

	private record Errand(@Nullable UUID target, @Nullable Vec3 spot, long until) {
	}

	/** The bond: "kind|1" with its familiar alive, "kind|0" when it is ash. */
	private static final AttachmentType<String> BOND = AttachmentRegistry.create(JaymosKingdoms.id("familiar_bond"), b -> b.persistent(Codec.STRING).copyOnDeath());
	/** What it carries. */
	private static final AttachmentType<ItemStack> CARRIED = AttachmentRegistry.create(JaymosKingdoms.id("familiar_carried"), b -> b.persistent(ItemStack.OPTIONAL_CODEC).copyOnDeath());

	public static Item ASH;
	private static final Map<UUID, UUID> BODIES = new HashMap<>();
	private static final Set<UUID> EYES = new HashSet<>();
	private static final Map<UUID, Errand> ERRANDS = new HashMap<>();
	private static final Map<Entity, Long> CALLED = new WeakHashMap<>();
	private static final Map<UUID, Float> HEALTH = new HashMap<>();

	private Familiars() {
	}

	static void register() {
		FamiliarBodies.register();
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("familiar_ash"));
		ASH = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.BONE_MEAL, ASH));
		PayloadTypeRegistry.serverboundPlay().register(Eyes.TYPE, Eyes.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Seeing.TYPE, Seeing.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Eyes.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> eyes(player, payload.on()));
		});
		ServerTickEvents.END_SERVER_TICK.register(Familiars::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BODIES.clear();
			EYES.clear();
			ERRANDS.clear();
			HEALTH.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			UUID owner = ownerOf(entity);
			if (owner == null) {
				return;
			}
			UUID known = BODIES.get(owner);
			if (known != null && !known.equals(entity.getUUID()) && level.getEntity(known) != null) {
				// a second body of the same bond (the old one woke after a new one was made): the spirit has one
				entity.discard();
			} else {
				BODIES.put(owner, entity.getUUID());
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			UUID owner = ownerOf(victim);
			// no blow of its owner's (or their friends') lands on a familiar
			return owner == null || source.getEntity() == null || !source.getEntity().getUUID().equals(owner);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (victim instanceof ServerPlayer p && EYES.contains(p.getUUID())) {
				eyes(p, false);
			}
		});
	}

	// ---- the bond ----------------------------------------------------------------------------------------------------------------

	private static @Nullable String kindOf(ServerPlayer player) {
		String b = player.getAttached(BOND);
		return b == null || !b.contains("|") ? null : b.substring(0, b.indexOf('|'));
	}

	private static boolean alive(ServerPlayer player) {
		String b = player.getAttached(BOND);
		return b != null && b.endsWith("|1");
	}

	/** Whether {@code player} carries the ash of a dead familiar. */
	public static boolean hasAsh(ServerPlayer player) {
		return ash(player) != null;
	}

	private static @Nullable ItemStack ash(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(ASH)) {
				return s;
			}
		}
		return null;
	}

	/** The owner a familiar's body belongs to, or null for any other entity. */
	static @Nullable UUID ownerOf(Entity e) {
		if (!e.entityTags().contains(TAG)) {
			return null;
		}
		for (String t : e.entityTags()) {
			if (t.startsWith(OF)) {
				try {
					return UUID.fromString(t.substring(OF.length()));
				} catch (IllegalArgumentException ex) {
					return null;
				}
			}
		}
		return null;
	}

	/** The familiar body of {@code player} in their level, or null. */
	static @Nullable Mob body(ServerPlayer player) {
		UUID id = BODIES.get(player.getUUID());
		Entity e = id == null ? null : player.level().getEntity(id);
		return e instanceof Mob m && m.isAlive() ? m : null;
	}

	/** The rite of binding (cast as a ritual) and of calling back (from ash). */
	static boolean cast(String spell, ServerLevel level, Player who) {
		if (!(who instanceof ServerPlayer player)) {
			return false;
		}
		String kind;
		if (spell.equals(NewSpells.CALL_FAMILIAR)) {
			ItemStack ash = ash(player);
			if (ash == null) {
				return false;
			}
			CustomData data = ash.get(DataComponents.CUSTOM_DATA);
			kind = data == null ? "owl" : data.copyTag().getString(ASH_KIND).orElse("owl");
			ash.shrink(1);
		} else {
			kind = Choices.get(player, "familiar");
			Item lure = LURES.get(kind);
			if (!player.isCreative()) {
				ItemStack have = null;
				for (int i = 0; i < player.getInventory().getContainerSize() && have == null; i++) {
					if (player.getInventory().getItem(i).is(lure)) {
						have = player.getInventory().getItem(i);
					}
				}
				if (have == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.parts.needs", 1, new ItemStack(lure).getHoverName()));
					return false;
				}
				have.shrink(1);
			}
		}
		make(level, player, kind);
		return true;
	}

	/** A new body for {@code player}'s bond of {@code kind}; the old one goes. */
	private static void make(ServerLevel level, ServerPlayer player, String kind) {
		Mob old = body(player);
		if (old != null) {
			old.discard();
		}
		Mob m = FamiliarBodies.type(kind).create(level, EntitySpawnReason.MOB_SUMMONED);
		if (m == null) {
			return;
		}
		Vec3 at = player.position().add(player.getLookAngle().multiply(1.0, 0.0, 1.0).scale(1.2)).add(0.0, 0.5, 0.0);
		m.snapTo(at.x, at.y, at.z, player.getYRot(), 0.0F);
		m.addTag(TAG);
		m.addTag(OF + player.getUUID());
		m.setPersistenceRequired();
		level.addFreshEntity(m);
		BODIES.put(player.getUUID(), m.getUUID());
		player.setAttached(BOND, kind + "|1");
		Fx.blast(level, m.position().add(0.0, 0.3, 0.0), 1.2F, COLOUR);
		level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.4F);
		call(m, 0.8F);
	}

	/** A body died: its bond is ash. */
	static void died(Mob body) {
		UUID owner = ownerOf(body);
		if (owner == null || !(body.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		String kind = FamiliarBodies.kindOf(body);
		if (player != null) {
			player.setAttached(BOND, kind + "|0");
			EYES.remove(owner);
			sendSeeing(player, -1);
			ERRANDS.remove(owner);
		}
		BODIES.remove(owner);
		ItemStack ash = new ItemStack(ASH);
		CustomData.update(DataComponents.CUSTOM_DATA, ash, tag -> tag.putString(ASH_KIND, kind));
		ash.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.familiar_ash.of", Component.translatable("familiar.jaymoskingdoms." + kind))
			.withStyle(s -> s.withItalic(false)));
		ItemEntity drop = new ItemEntity(level, body.getX(), body.getY() + 0.3, body.getZ(), ash);
		level.addFreshEntity(drop);
		ItemStack carried = player == null ? ItemStack.EMPTY : player.getAttachedOrElse(CARRIED, ItemStack.EMPTY);
		if (!carried.isEmpty() && player != null) {
			level.addFreshEntity(new ItemEntity(level, body.getX(), body.getY() + 0.3, body.getZ(), carried.copy()));
			player.setAttached(CARRIED, ItemStack.EMPTY);
		}
		level.sendParticles(Fx.mote(0x808080, 0.9F), body.getX(), body.getY(0.5), body.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
		if (player != null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
		}
	}

	// ---- the animal's voice -----------------------------------------------------------------------------------------------------

	private static void call(Mob body, float volume) {
		SoundEvent sound = switch (FamiliarBodies.kindOf(body)) {
			case "cat" -> SoundEvents.CAT_AMBIENT_BABY.value();
			case "rat" -> SoundEvents.RABBIT_AMBIENT;
			case "raven" -> SoundEvents.PARROT_AMBIENT;
			case "frog" -> SoundEvents.FROG_AMBIENT;
			default -> SoundEvents.PARROT_IMITATE_PHANTOM;
		};
		body.level().playSound(null, body.getX(), body.getY(), body.getZ(), sound, SoundSource.NEUTRAL, volume, 0.8F);
	}

	// ---- touching, carrying -----------------------------------------------------------------------------------------------------

	/** Use on a familiar: its owner hands it an item, or takes back what it carries. */
	static InteractionResult interact(Mob body, Player player, InteractionHand hand) {
		if (body.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer owner) || !owner.getUUID().equals(ownerOf(body))) {
			return InteractionResult.PASS;
		}
		ItemStack held = owner.getItemInHand(hand);
		ItemStack carried = owner.getAttachedOrElse(CARRIED, ItemStack.EMPTY);
		if (held.isEmpty()) {
			if (carried.isEmpty()) {
				call(body, 0.6F);
				return InteractionResult.SUCCESS_SERVER;
			}
			owner.setItemInHand(hand, carried.copy());
			owner.setAttached(CARRIED, ItemStack.EMPTY);
		} else {
			ItemStack take = held.copyWithCount(1);
			held.shrink(1);
			if (!carried.isEmpty() && !owner.getInventory().add(carried.copy())) {
				owner.drop(carried.copy(), false, Prediction.SERVER_ONLY);
			}
			owner.setAttached(CARRIED, take);
		}
		body.level().playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.4F);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Who the familiar is touching, when {@code player} looks through its eyes; else null. */
	static @Nullable LivingEntity touched(ServerPlayer player) {
		if (!EYES.contains(player.getUUID())) {
			return null;
		}
		Mob body = body(player);
		if (body == null) {
			return null;
		}
		Vec3 from = body.getEyePosition(), to = from.add(body.getLookAngle().scale(TOUCH_REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(body, from, to, body.getBoundingBox().expandTowards(body.getLookAngle().scale(TOUCH_REACH)).inflate(1.0),
			e -> e instanceof LivingEntity l && l.isAlive() && e != body && e != player && !e.isSpectator(), TOUCH_REACH * TOUCH_REACH);
		return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
	}

	/** Sends the familiar, with what it carries, to whoever {@code player} looks at (or the spot). */
	static void send(ServerPlayer player) {
		Mob body = body(player);
		if (body == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity looked = Arcana.sighted(player);
		if (looked != null && looked.distanceTo(player) <= SEND_REACH && looked != body) {
			ERRANDS.put(player.getUUID(), new Errand(looked.getUUID(), null, level.getGameTime() + ERRAND_TICKS));
		} else {
			ERRANDS.put(player.getUUID(), new Errand(null, NewSpells.aim(level, player, SEND_REACH), level.getGameTime() + ERRAND_TICKS));
		}
		call(body, 0.7F);
	}

	/** Whether {@code player} has a familiar alive (the Magic screen offers the errand). */
	static boolean has(ServerPlayer player) {
		return alive(player);
	}

	// ---- eyes --------------------------------------------------------------------------------------------------------------------

	private static void sendSeeing(ServerPlayer player, int entity) {
		if (ServerPlayNetworking.canSend(player, Seeing.TYPE)) {
			ServerPlayNetworking.send(player, new Seeing(entity));
		}
	}

	private static void eyes(ServerPlayer player, boolean on) {
		if (!on || !alive(player)) {
			if (EYES.remove(player.getUUID())) {
				sendSeeing(player, -1);
			}
			return;
		}
		Mob body = body(player);
		if (body == null || player.isSleeping()) {
			return;
		}
		ERRANDS.remove(player.getUUID());
		EYES.add(player.getUUID());
		sendSeeing(player, body.getId());
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!alive(player) || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			Mob body = body(player);
			if (body == null) {
				if (EYES.remove(player.getUUID())) {
					sendSeeing(player, -1);
				}
				// a spirit does not stay lost: made again beside its owner once they have been in the world a while
				if (player.tickCount > REMAKE_AFTER && server.getTickCount() % 100 == 0 && !BODIES.containsKey(player.getUUID())) {
					String kind = kindOf(player);
					if (kind != null) {
						make(level, player, kind);
					}
				}
				continue;
			}
			if (body.level() != level) {
				// through a gate, it comes too
				String kind = kindOf(player);
				body.discard();
				BODIES.remove(player.getUUID());
				if (kind != null) {
					make(level, player, kind);
				}
				continue;
			}
			boolean eyes = EYES.contains(player.getUUID());
			if (eyes) {
				steer(player, body);
			} else {
				Errand errand = ERRANDS.get(player.getUUID());
				if (errand != null) {
					errand(level, player, body, errand);
				} else {
					follow(player, body);
				}
				if (body.isNoGravity()) {
					body.setNoGravity(false);
				}
			}
			if (server.getTickCount() % 40 == 0) {
				scout(level, player, body);
			}
		}
	}

	private static boolean flier(Mob body) {
		return body instanceof Parrot;
	}

	private static void follow(ServerPlayer player, Mob body) {
		double d = body.distanceTo(player);
		if (d > TELEPORT_AT) {
			Vec3 at = player.position().add(player.getLookAngle().multiply(1.0, 0.0, 1.0).scale(-1.5));
			body.teleportTo((ServerLevel) body.level(), at.x, at.y + 0.3, at.z, Set.of(), body.getYRot(), 0.0F, false);
			body.getNavigation().stop();
		} else if (d > KEEP) {
			body.getNavigation().moveTo(player, WALK);
		} else if (d < CLOSE && body.getNavigation().isInProgress()) {
			body.getNavigation().stop();
		}
	}

	private static void steer(ServerPlayer player, Mob body) {
		// its eyes turn where yours do
		body.setYRot(player.getYRot());
		body.setYHeadRot(player.getYHeadRot());
		body.yBodyRot = player.getYRot();
		body.setXRot(player.getXRot());
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HEARD_TICKS, SLOW_LEVEL, false, false, false));
		if (player.isShiftKeyDown() || body.distanceTo(player) > STEER_MAX) {
			if (body.distanceTo(player) > STEER_MAX) {
				Vec3 back = player.position().subtract(body.position()).normalize().scale(FLY);
				body.setDeltaMovement(back);
			} else {
				body.setDeltaMovement(Vec3.ZERO);
				body.getNavigation().stop();
			}
			return;
		}
		Vec3 look = player.getLookAngle();
		if (flier(body)) {
			body.setNoGravity(true);
			body.setDeltaMovement(look.scale(FLY));
		} else {
			Vec3 ahead = body.position().add(look.multiply(1.0, 0.0, 1.0).normalize().scale(6.0));
			body.getNavigation().moveTo(ahead.x, ahead.y, ahead.z, WALK);
		}
	}

	private static void errand(ServerLevel level, ServerPlayer player, Mob body, Errand errand) {
		Entity target = errand.target() == null ? null : level.getEntity(errand.target());
		Vec3 goal = target != null ? target.getBoundingBox().getCenter() : errand.spot();
		if (goal == null || level.getGameTime() >= errand.until()) {
			ERRANDS.remove(player.getUUID());
			return;
		}
		if (body.position().distanceTo(goal) <= ARRIVED) {
			deliver(level, player, body, target, goal);
			ERRANDS.remove(player.getUUID());
			return;
		}
		if (flier(body)) {
			body.setNoGravity(true);
			body.setDeltaMovement(goal.subtract(body.position()).normalize().scale(FLY));
			body.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, goal);
		} else {
			body.getNavigation().moveTo(goal.x, goal.y, goal.z, WALK);
		}
	}

	private static void deliver(ServerLevel level, ServerPlayer owner, Mob body, @Nullable Entity target, Vec3 at) {
		ItemStack item = owner.getAttachedOrElse(CARRIED, ItemStack.EMPTY);
		if (item.isEmpty()) {
			call(body, 0.8F);
			return;
		}
		owner.setAttached(CARRIED, ItemStack.EMPTY);
		if (target instanceof ServerPlayer p && p.getInventory().add(item.copy())) {
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
		} else {
			ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, item.copy());
			level.addFreshEntity(e);
		}
		call(body, 0.8F);
		level.sendParticles(Fx.mote(COLOUR, 0.7F), at.x, at.y + 0.3, at.z, 6, 0.2, 0.2, 0.2, 0.02);
	}

	/** It sees a monster that is not yet at its owner's heels and calls, once, with a mark over it for its owner's eyes alone. */
	private static void scout(ServerLevel level, ServerPlayer player, Mob body) {
		long now = level.getGameTime();
		for (Mob m : level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(SCOUT_SIGHT), x -> x instanceof Enemy && x.isAlive() && x != body
			&& x.distanceTo(player) > SCOUT_OUTSIDE && body.hasLineOfSight(x))) {
			Long last = CALLED.get(m);
			if (last != null && now - last < SCOUT_AGAIN) {
				continue;
			}
			CALLED.put(m, now);
			Divination.hear(player, SoundEvents.PARROT_IMITATE_ENDERMITE, 0.6F, 1.5F);
			call(body, 0.9F);
			level.sendParticles(player, Fx.rune(0xE86060, 1.0F), true, true, m.getX(), m.getY(1.0) + 0.9, m.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
			level.sendParticles(player, Fx.mote(0xE86060, 0.9F), true, true, m.getX(), m.getY(1.0) + 0.5, m.getZ(), 5, 0.1, 0.3, 0.1, 0.01);
			return;
		}
	}

	/** What the Magic screen offers about the familiar: an errand, when it lives. */
	static List<String> offers(ServerPlayer player) {
		return alive(player) ? List.of("send=go") : List.of();
	}
}
