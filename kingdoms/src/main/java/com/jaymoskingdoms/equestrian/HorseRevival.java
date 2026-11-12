package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.CourtWizards;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Moons;
import com.jaymoskingdoms.Sorcery;
import com.jaymoskingdoms.Worker;
import com.jaymoskingdoms.ZFighterFolk;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * A horse brought back from a lock of its mane. Andy (2026-11-12): "add a screen at the wizards and necromancers that does
 * it. the wizards will warn against it and only teach you the spell after you do something for them. the necromancers just
 * charge you, and have no qualms. a resurrected horse is never the same and can mutate into one of the special ones during
 * blood moons".
 * <p>
 * "Raise my horse" ({@link #ANSWER}) shows on a court wizard or a necromancer (a realm's or a camp's) while you hold a mane
 * lock. A necromancer names a price ({@link #NECROMANCER_PRICE}) and raises it there and then. A wizard warns you off; if
 * you still want it, they set you a task (bring them a few things, {@link #TASKS}), and once it's done they teach you the
 * rite ({@link #RITE}): from then on, using a mane lock raises its horse yourself.
 * <p>
 * The horse comes back as it was, but worse ({@link #worse}): slower, shorter of wind, wilder, and marked
 * {@link #RETURNED}. On a blood moon each returned horse rolls once ({@link #MUTATE_CHANCE}) to turn into a Nightmare or
 * a Bat Horse.
 */
public final class HorseRevival {
	public static final String ANSWER = "jaymoskingdoms_revive_horse";
	/** What a necromancer asks to raise a horse (emeralds' worth, in the coin where you stand). */
	static final long NECROMANCER_PRICE = 24;
	/** What coming back costs a horse: its speed and stamina times these, its temper and courage moved by these. */
	static final float SPEED_KEPT = 0.85F, STAMINA_KEPT = 0.8F, TEMPER_GAINED = 0.25F, COURAGE_GAINED = 0.15F;
	/** The chance, once a blood moon, that a returned horse turns into one of the mythic ones. */
	static final float MUTATE_CHANCE = 0.35F;
	static final int CHECK_TICKS = 200;
	/** A horse rolls once a blood moon: no sooner than this after its last roll (ticks). */
	static final long ONE_NIGHT = 13000L;
	static final int DEAD_COLOR = 0x6A7A4A, ARCANE_COLOR = 0x8C7BFF, BLOOD_COLOR = 0xFF4033;

	/** A wizard's task: bring {@code count} of {@code item}. */
	record Task(Item item, int count) {
	}

	/** What a wizard may ask for before teaching the rite: things of the dead and of the deep. */
	static final List<Task> TASKS = List.of(
		new Task(Items.AMETHYST_SHARD, 12),
		new Task(Items.PHANTOM_MEMBRANE, 3),
		new Task(Items.GHAST_TEAR, 1),
		new Task(Items.ECHO_SHARD, 2),
		new Task(Items.GLOW_INK_SAC, 6));

	/** On a player: the rite they've been set or taught. Saved, kept through death. */
	public record Rite(int task, boolean known) {
		static final Rite NONE = new Rite(-1, false);
		static final Codec<Rite> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("task", -1).forGetter(Rite::task),
			Codec.BOOL.optionalFieldOf("known", false).forGetter(Rite::known)).apply(i, Rite::new));
	}

	public static final AttachmentType<Rite> RITE = AttachmentRegistry.create(JaymosKingdoms.id("horse_rite"),
		builder -> builder.persistent(Rite.CODEC).copyOnDeath());
	/** On a horse: it came back. The value is the game time it last rolled to change under a blood moon. Saved. */
	public static final AttachmentType<Long> RETURNED = AttachmentRegistry.create(JaymosKingdoms.id("returned"),
		builder -> builder.persistent(Codec.LONG));

	/** Returned horses in loaded chunks. */
	private static final Set<AbstractHorse> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

	private HorseRevival() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractHorse horse && horse.hasAttached(RETURNED)) {
				LOADED.add(horse);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(HorseRevival::tick);
		UseItemCallback.EVENT.register(HorseRevival::used);
	}

	// ---- who will do it --------------------------------------------------------------------------------------------

	private static boolean necromancer(LivingEntity who) {
		return who instanceof Worker w && w.role() == Sorcery.NECROMANCER;
	}

	private static boolean able(LivingEntity who) {
		return !who.level().isClientSide() && who.isAlive() && !Downed.isDowned(who) && (CourtWizards.is(who) || necromancer(who));
	}

	/** A mane lock that carries its horse (one dropped since locks began to). */
	static boolean isLock(ItemStack stack) {
		return stack.is(Equestrian.MANE_LOCK) && data(stack).getString("entity").isPresent();
	}

	private static CompoundTag data(ItemStack stack) {
		CustomData d = stack.get(DataComponents.CUSTOM_DATA);
		return d == null ? new CompoundTag() : d.copyTag();
	}

	/** Whether "Raise my horse" shows on {@code who}. */
	public static boolean canAsk(Player player, LivingEntity who) {
		return able(who) && (isLock(player.getMainHandItem()) || isLock(player.getOffhandItem()));
	}

	private static InteractionHand lockHand(Player player) {
		return isLock(player.getMainHandItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	// ---- the talk screens ------------------------------------------------------------------------------------------

	public static void open(ServerPlayer player, LivingEntity who) {
		if (!canAsk(player, who)) {
			return;
		}
		if (necromancer(who)) {
			necromancer(player, who);
		} else {
			wizard(player, who);
		}
	}

	/** No qualms: a price, and it's done. */
	private static void necromancer(ServerPlayer player, LivingEntity who) {
		long price = Money.worth(NECROMANCER_PRICE);
		List<Component> options = List.of(Component.translatable("talk.jaymoskingdoms.revive.pay", Money.text(player, price)),
			Component.translatable("talk.jaymoskingdoms.revive.leave"));
		ZFighterFolk.ask(player, who, Component.translatable("talk.jaymoskingdoms.revive.necromancer", Money.text(player, price)), options,
			List.of(true, true), pick -> {
				if (pick != 0 || !canAsk(player, who)) {
					return;
				}
				if (!Money.charge(player, price)) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.revive.poor").withStyle(ChatFormatting.RED));
					return;
				}
				raise(player, lockHand(player), who.position(), DEAD_COLOR);
			});
	}

	/** A warning, then a task, then the rite. */
	private static void wizard(ServerPlayer player, LivingEntity who) {
		Rite rite = player.getAttachedOrElse(RITE, Rite.NONE);
		if (rite.known()) {
			ZFighterFolk.ask(player, who, Component.translatable("talk.jaymoskingdoms.revive.wizard_known"),
				List.of(Component.translatable("talk.jaymoskingdoms.revive.leave")), List.of(true), pick -> {
				});
			return;
		}
		if (rite.task() < 0 || rite.task() >= TASKS.size()) {
			List<Component> options = List.of(Component.translatable("talk.jaymoskingdoms.revive.still_want"),
				Component.translatable("talk.jaymoskingdoms.revive.heed"));
			ZFighterFolk.ask(player, who, Component.translatable("talk.jaymoskingdoms.revive.wizard_warn"), options, List.of(true, true), pick -> {
				if (pick != 0) {
					return;
				}
				int t = player.getRandom().nextInt(TASKS.size());
				player.setAttached(RITE, new Rite(t, false));
				Task task = TASKS.get(t);
				ZFighterFolk.ask(player, who, Component.translatable("talk.jaymoskingdoms.revive.wizard_task", task.count(), task.item().getName(task.item().getDefaultInstance())),
					List.of(Component.translatable("talk.jaymoskingdoms.revive.agree")), List.of(true), p -> {
					});
			});
			return;
		}
		Task task = TASKS.get(rite.task());
		boolean has = count(player, task.item()) >= task.count();
		List<Component> options = List.of(Component.translatable("talk.jaymoskingdoms.revive.hand_over", task.count(), task.item().getName(task.item().getDefaultInstance())),
			Component.translatable("talk.jaymoskingdoms.revive.not_yet"));
		ZFighterFolk.ask(player, who, Component.translatable("talk.jaymoskingdoms.revive.wizard_waiting", task.count(), task.item().getName(task.item().getDefaultInstance())), options,
			List.of(has, true), pick -> {
				if (pick != 0 || count(player, task.item()) < task.count()) {
					return;
				}
				take(player, task.item(), task.count());
				player.setAttached(RITE, new Rite(-1, true));
				if (player.level() instanceof ServerLevel level) {
					Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), 1.4F, ARCANE_COLOR, 40);
					level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
				}
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.revive.taught").withStyle(ChatFormatting.LIGHT_PURPLE));
			});
	}

	private static int count(Player player, Item item) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static void take(Player player, Item item, int count) {
		for (int i = 0; i < player.getInventory().getContainerSize() && count > 0; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(item)) {
				int n = Math.min(count, s.getCount());
				s.shrink(n);
				count -= n;
			}
		}
	}

	/** One who knows the rite raises a horse from its lock themselves. */
	private static InteractionResult used(Player player, net.minecraft.world.level.Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(Equestrian.MANE_LOCK)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		if (!isLock(stack) || !sp.getAttachedOrElse(RITE, Rite.NONE).known()) {
			return InteractionResult.PASS;
		}
		raise(sp, hand, sp.position().add(sp.getLookAngle().multiply(2.0, 0.0, 2.0)), ARCANE_COLOR);
		return InteractionResult.SUCCESS;
	}

	// ---- the raising -----------------------------------------------------------------------------------------------

	/** The horse in the lock {@code player} holds comes back near {@code near}, and the lock is spent. */
	static void raise(ServerPlayer player, InteractionHand hand, Vec3 near, int color) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack lock = player.getItemInHand(hand);
		if (!isLock(lock)) {
			return;
		}
		CompoundTag tag = data(lock);
		net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(tag.getStringOr("entity", ""));
		Optional<EntityType<?>> type = key == null ? Optional.empty() : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(key);
		Entity made = type.map(t -> t.create(level, EntitySpawnReason.MOB_SUMMONED)).orElse(null);
		if (!(made instanceof AbstractHorse horse)) {
			if (made != null) {
				made.discard();
			}
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.revive.failed").withStyle(ChatFormatting.RED));
			return;
		}
		Equine was = tag.get("equine") == null ? null : Equine.CODEC.parse(NbtOps.INSTANCE, tag.get("equine")).result().orElse(null);
		BlockPos at = BlockPos.containing(near);
		horse.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		horse.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
		Equine back = worse(was != null ? was : Equestrian.equine(horse));
		Equestrian.apply(horse, back, true);
		tag.getString("name").ifPresent(n -> horse.setCustomName(Component.literal(n)));
		horse.setPersistenceRequired();
		horse.setAttached(RETURNED, Long.MIN_VALUE / 2);
		level.addFreshEntity(horse);
		horse.tameWithName(player);
		Bonding.tamed(horse, player);
		LOADED.add(horse);
		lock.shrink(1);
		Fx.sigil(level, horse.position().add(0.0, 0.05, 0.0), 1.8F, color, 60);
		Fx.burst(level, horse.getX(), horse.getY() + 1.0, horse.getZ(), color, 1.2F, 24, 0.6, 0.8, 0.6);
		level.playSound(null, at, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 0.8F, 0.6F);
		level.playSound(null, at, SoundEvents.HORSE_DEATH, SoundSource.NEUTRAL, 1.0F, 0.5F);
		Component name = horse.hasCustomName() ? horse.getCustomName() : Component.translatable(back.breed().key());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.revive.raised", name).withStyle(ChatFormatting.DARK_GREEN));
	}

	/** Never the same: slower, shorter of wind, wilder, braver (it's been dead). */
	static Equine worse(Equine e) {
		return new Equine(e.breed(), e.coat(), e.speed() * SPEED_KEPT, e.stamina() * STAMINA_KEPT, e.jump(),
			Math.min(1.0F, e.courage() + COURAGE_GAINED), Math.min(1.0F, e.temper() + TEMPER_GAINED));
	}

	/** Whether {@code horse} came back from its lock. */
	public static boolean returned(Entity horse) {
		return horse.hasAttached(RETURNED);
	}

	// ---- blood moons -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0 || LOADED.isEmpty()) {
			return;
		}
		Moons.Moon moon = Moons.now(server);
		if (moon != Moons.Moon.BLOOD && moon != Moons.Moon.SUPER_BLOOD) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (AbstractHorse horse : new ArrayList<>(LOADED)) {
			if (!horse.isAlive() || !(horse.level() instanceof ServerLevel level)) {
				LOADED.remove(horse);
				continue;
			}
			Long rolled = horse.getAttached(RETURNED);
			Equine e = Equestrian.equine(horse);
			if (rolled == null || now - rolled < ONE_NIGHT || e.breed() == Breed.NIGHTMARE || e.breed() == Breed.BAT_HORSE || e.breed().elephant()) {
				continue;
			}
			if (!(horse instanceof Horse) && horse.isTamed() && horse.getOwner() == null) {
				// a new body needs its owner at hand to be theirs; it waits until they're on
				continue;
			}
			horse.setAttached(RETURNED, now);
			if (horse.getRandom().nextFloat() < MUTATE_CHANCE) {
				mutate(level, horse, e);
			}
		}
	}

	/** Under the blood moon a returned horse turns: a Nightmare, or a Bat Horse. */
	private static void mutate(ServerLevel level, AbstractHorse horse, Equine e) {
		Breed into = horse.getRandom().nextBoolean() ? Breed.NIGHTMARE : Breed.BAT_HORSE;
		Equine changed = new Equine(into, 0, Math.max(e.speed(), 0.5F), Math.max(e.stamina(), 0.5F), e.jump(), e.courage(), e.temper());
		AbstractHorse body = horse;
		if (!(horse instanceof Horse)) {
			// a donkey, a mule or an undead horse becomes a horse's body for it (the mythic ones are drawn as horses)
			AbstractHorse fresh = net.minecraft.world.entity.EntityTypes.HORSE.create(level, EntitySpawnReason.CONVERSION);
			if (fresh == null) {
				return;
			}
			fresh.snapTo(horse.getX(), horse.getY(), horse.getZ(), horse.getYRot(), horse.getXRot());
			fresh.setCustomName(horse.getCustomName());
			fresh.setPersistenceRequired();
			fresh.setTamed(horse.isTamed());
			if (horse.getOwner() != null) {
				fresh.setOwner(horse.getOwner());
			}
			fresh.setAttached(RETURNED, horse.getAttached(RETURNED));
			Bonding.dropTack(level, horse);
			horse.ejectPassengers();
			horse.discard();
			LOADED.remove(horse);
			level.addFreshEntity(fresh);
			LOADED.add(fresh);
			body = fresh;
		}
		Equestrian.apply(body, changed, true);
		Fx.blast(level, body.position().add(0.0, 1.0, 0.0), 1.6F, BLOOD_COLOR);
		level.playSound(null, body.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.2F, 0.5F);
		if (body.getOwner() instanceof ServerPlayer owner) {
			Component name = body.hasCustomName() ? body.getCustomName() : Component.translatable(into.key());
			owner.sendSystemMessage(Component.translatable("message.jaymoskingdoms.revive.mutated." + into.id(), name).withStyle(ChatFormatting.DARK_RED));
		}
	}
}
