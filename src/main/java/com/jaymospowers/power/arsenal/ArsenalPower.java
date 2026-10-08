package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.arsenal.entity.ArsenalEntities;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Pocket Arsenal: reach through a rift into other dimensions and pull out whatever weapon answers (Balkon's WeaponMod
 * weapons plus vanilla ones). Pulled weapons are temporary: they carry the {@link Summoned} component and go back when
 * their time is up, when dropped, or when their holder dies.
 *
 * <ul>
 * <li>R - Reach Through: open a rift, pull a random weapon (+ ammo) into your hand.</li>
 * <li>G - Return: send the held summoned weapon back; refunds part of Reach's cooldown.</li>
 * <li>V - Barrage: 3-5 rifts open around you and fire weapons at what you're looking at.</li>
 * <li>B - Pocket Stash: your personal 27-slot pocket dimension.</li>
 * </ul>
 */
public final class ArsenalPower implements Power {
	public static final ArsenalPower INSTANCE = new ArsenalPower();
	public static final Identifier ID = JaymosPowers.id("arsenal");

	// tuning (ticks)
	public static final int REACH_COOLDOWN = 160;
	public static final int RIFT_DELAY = 10;
	public static final int SUMMON_TICKS = 1500;
	public static final int MAX_HELD = 3;
	public static final int RETURN_REFUND = 80;
	public static final int BARRAGE_COOLDOWN = 600;
	public static final int BARRAGE_RANGE = 48;
	public static final float BARRAGE_DAMAGE = 7.0F;
	/** A dropped summoned weapon lies on the ground this long before it dissolves. */
	public static final int DROPPED_TICKS = 60;

	/** When each ability is ready again (game time). Saved, and synced to its owner for the HUD. */
	public record Cooldowns(long reach, long barrage) {
		public static final Cooldowns NONE = new Cooldowns(0, 0);
		public static final Codec<Cooldowns> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("reach").forGetter(Cooldowns::reach),
				Codec.LONG.fieldOf("barrage").forGetter(Cooldowns::barrage))
			.apply(i, Cooldowns::new));
		public static final StreamCodec<ByteBuf, Cooldowns> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Cooldowns::reach,
			ByteBufCodecs.VAR_LONG, Cooldowns::barrage,
			Cooldowns::new);
	}

	public static final AttachmentType<Cooldowns> COOLDOWNS = AttachmentRegistry.create(JaymosPowers.id("arsenal_cooldowns"),
		builder -> builder.persistent(Cooldowns.CODEC).copyOnDeath().syncWith(Cooldowns.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Rifts that are opening: player → game time the weapon comes out. Server memory only. */
	private static final Map<UUID, Long> PENDING = new HashMap<>();

	private ArsenalPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		Summoned.TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("arsenal_summoned"),
			DataComponentType.<Summoned>builder().persistent(Summoned.CODEC).networkSynchronized(Summoned.STREAM_CODEC).build());
		ArsenalEntities.init();
		ArsenalStash.init();
		Powers.register(INSTANCE);
		ServerTickEvents.END_SERVER_TICK.register(ArsenalPower::serverTick);
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return 0x9B5CFF;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.ENDER_CHEST);
	}

	public static Cooldowns cooldowns(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(COOLDOWNS, Cooldowns.NONE);
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> reach(player);
			case ABILITY_2 -> sendBack(player);
			case ABILITY_3 -> barrage(player);
			case ABILITY_4 -> ArsenalStash.open(player);
		}
	}

	@Override
	public void onLost(ServerPlayer player) {
		PENDING.remove(player.getUUID());
		// the doorway closes: everything this player summoned goes home
		dissolveWhere(player, s -> s.ownedBy(player.getUUID()), true);
	}

	@Override
	public void tick(ServerPlayer player) {
		Long at = PENDING.get(player.getUUID());
		if (at == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!player.isAlive()) {
			PENDING.remove(player.getUUID());
			return;
		}
		Vec3 rift = riftPos(player);
		riftRing(level, player, rift, 0.45);
		if (level.getGameTime() >= at) {
			PENDING.remove(player.getUUID());
			pull(player, rift);
		}
	}

	// ---------------------------------------------------------------- R: Reach Through

	private static void reach(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (PENDING.containsKey(player.getUUID())) {
			return;
		}
		Cooldowns cd = cooldowns(player);
		if (now < cd.reach()) {
			denied(player, "message.jaymospowers.arsenal.reach_recharging", cd.reach() - now);
			return;
		}
		player.setAttached(COOLDOWNS, new Cooldowns(now + REACH_COOLDOWN, cd.barrage()));
		PENDING.put(player.getUUID(), now + RIFT_DELAY);
		Vec3 rift = riftPos(player);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.55F);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.15F, 1.8F);
	}

	private static Vec3 riftPos(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(look);
		return player.getEyePosition().add(look.scale(1.3)).add(right.scale(0.35)).add(0, -0.25, 0);
	}

	private static Vec3 rightOf(Vec3 look) {
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		return right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
	}

	/** A spinning ring of portal particles facing the player. */
	private static void riftRing(ServerLevel level, ServerPlayer player, Vec3 center, double radius) {
		Vec3 look = player.getLookAngle();
		Vec3 right = rightOf(look);
		Vec3 up = right.cross(look).normalize();
		double spin = level.getGameTime() * 0.35;
		for (int i = 0; i < 8; i++) {
			double a = spin + i * Math.PI / 4;
			Vec3 p = center.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 4, 0.1, 0.1, 0.1, 0.6);
	}

	private static void pull(ServerPlayer player, Vec3 rift) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		makeRoom(player, MAX_HELD - 1);

		List<ItemStack> stacks = ArsenalPool.roll(level, player.getRandom());
		Summoned tag = new Summoned(player.getUUID(), now + SUMMON_TICKS, player.getRandom().nextInt());
		Inventory inventory = player.getInventory();
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			stack.set(Summoned.TYPE, tag);
			if (i == 0 && player.getMainHandItem().isEmpty()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			} else {
				// into the inventory; at your feet if it's full (where it dissolves after a few seconds)
				inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}

		level.sendParticles(ParticleTypes.END_ROD, rift.x, rift.y, rift.z, 14, 0.15, 0.15, 0.15, 0.08);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, rift.x, rift.y, rift.z, 30, 0.25, 0.25, 0.25, 0.05);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.9F, 0.8F);
		level.playSound(null, rift.x, rift.y, rift.z, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 0.6F);
		if (!stacks.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.pulled", stacks.getFirst().getHoverName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	/** A summon came back into the player's hands (a Barrage blade picked up): keep to the cap of {@link #MAX_HELD}. */
	public static void capSummons(ServerPlayer player) {
		makeRoom(player, MAX_HELD);
	}

	/** Dissolves this player's oldest summons until at most {@code keep} batches are left. */
	private static void makeRoom(ServerPlayer player, int keep) {
		UUID id = player.getUUID();
		while (true) {
			Map<Integer, Long> batches = new HashMap<>();
			forEachHeld(player, (stack, setter) -> {
				Summoned s = Summoned.of(stack);
				if (s != null && s.ownedBy(id)) {
					batches.merge(s.batch(), s.expires(), Math::min);
				}
			});
			if (batches.size() <= keep) {
				return;
			}
			int oldest = batches.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow().getKey();
			dissolveWhere(player, s -> s.batch() == oldest && s.ownedBy(id), true);
		}
	}

	// ---------------------------------------------------------------- G: Return

	private static void sendBack(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		Summoned s = Summoned.of(held);
		if (s == null) {
			held = player.getOffhandItem();
			s = Summoned.of(held);
		}
		if (s == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.nothing_to_return").withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.4F);
			return;
		}
		Summoned sent = s;
		dissolveWhere(player, x -> x.batch() == sent.batch() && x.owner().equals(sent.owner()), true);
		if (sent.ownedBy(player.getUUID())) {
			long now = player.level().getGameTime();
			Cooldowns cd = cooldowns(player);
			player.setAttached(COOLDOWNS, new Cooldowns(Math.max(now, cd.reach() - RETURN_REFUND), cd.barrage()));
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.returned").withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	// ---------------------------------------------------------------- V: Barrage

	private static void barrage(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Cooldowns cd = cooldowns(player);
		if (now < cd.barrage()) {
			denied(player, "message.jaymospowers.arsenal.barrage_recharging", cd.barrage() - now);
			return;
		}
		player.setAttached(COOLDOWNS, new Cooldowns(cd.reach(), now + BARRAGE_COOLDOWN));

		// what are we looking at?
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(BARRAGE_RANGE));
		BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB search = player.getBoundingBox().expandTowards(look.scale(BARRAGE_RANGE)).inflate(1.5);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, search.inflate(0.0),
			e -> e.isPickable() && !e.isSpectator() && e.isAlive() && e != player, eye.distanceToSqr(end));
		@Nullable Entity target = entityHit == null ? null : entityHit.getEntity();
		Vec3 aim = target != null ? target.getBoundingBox().getCenter() : end;

		int count = 3 + player.getRandom().nextInt(3);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 right = rightOf(flat);
		for (int i = 0; i < count; i++) {
			double t = count == 1 ? 0 : (i / (count - 1.0)) * 2 - 1; // -1..1, left to right
			Vec3 pos = player.position()
				.add(right.scale(t * 1.9))
				.add(flat.scale(-0.7 + 0.4 * Math.abs(t)))
				.add(0, 2.3 - 0.6 * Math.abs(t), 0);
			RiftBlade blade = RiftBlade.create(level, player, ArsenalPool.randomBlade(player.getRandom()), pos, aim, target,
				10 + i * 4, BARRAGE_DAMAGE);
			level.addFreshEntity(blade);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 16, 0.25, 0.25, 0.25, 0.03);
			level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 10, 0.1, 0.1, 0.1, 0.5);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	private static void denied(ServerPlayer player, String key, long ticks) {
		player.sendOverlayMessage(Component.translatable(key, String.format("%.1f", ticks / 20.0)).withStyle(ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
	}

	// ---------------------------------------------------------------- expiry

	private interface HeldVisitor {
		void visit(ItemStack stack, java.util.function.Consumer<ItemStack> setter);
	}

	/** Every stack the player holds: inventory, armour, offhand and the stack on their cursor. */
	private static void forEachHeld(ServerPlayer player, HeldVisitor visitor) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			int s = slot;
			visitor.visit(inventory.getItem(slot), stack -> inventory.setItem(s, stack));
		}
		visitor.visit(player.containerMenu.getCarried(), stack -> player.containerMenu.setCarried(stack));
	}

	/** Removes every held summoned stack matching {@code which}; returns how many went. */
	private static int dissolveWhere(ServerPlayer player, java.util.function.Predicate<Summoned> which, boolean effects) {
		List<ItemStack> gone = new ArrayList<>();
		forEachHeld(player, (stack, setter) -> {
			Summoned s = Summoned.of(stack);
			if (s != null && which.test(s)) {
				gone.add(stack.copy());
				setter.accept(ItemStack.EMPTY);
			}
		});
		if (!gone.isEmpty() && effects) {
			Summoned.dissolveEffects(player.level(), player.position().add(0, 1.0, 0));
		}
		return gone.size();
	}

	/** Once a second: everything summoned whose time is up goes home, whoever holds it (power or not). */
	private static void serverTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long now = player.level().getGameTime();
			List<Component> names = new ArrayList<>();
			forEachHeld(player, (stack, setter) -> {
				Summoned s = Summoned.of(stack);
				if (s != null && now >= s.expires()) {
					if (names.isEmpty()) {
						names.add(stack.getHoverName());
					}
					setter.accept(ItemStack.EMPTY);
				}
			});
			if (!names.isEmpty()) {
				Summoned.dissolveEffects(player.level(), player.position().add(0, 1.0, 0));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.expired", names.getFirst())
					.withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}

	/** Called by {@code PlayerMixin} before a dying player's inventory drops: summoned weapons never become loot. */
	public static void stripOnDeath(ServerPlayer player) {
		dissolveWhere(player, s -> true, true);
	}
}
