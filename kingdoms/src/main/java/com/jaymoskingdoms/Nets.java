package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Nets (fifth edition's restrained, and the net weapon, Andy's pick B6). A weighted net is thrown ({@link ThrownNet}) and
 * spent in the throwing, like every throw. Whoever it lands on, a player or anyone else, moves at a crawl and swings badly
 * ({@link UpperHand}: bad footing for them, the upper hand for whoever strikes them) until they get free:
 * <ul>
 * <li>a Might save ({@link Saves.Kind#GRAB}) comes round every {@link #SAVE_EVERY} ticks;</li>
 * <li>a blade cuts it: use a sword, axe, dagger or shears on yourself (right-click in the air) or on someone netted, and the
 * net falls apart into string;</li>
 * <li>or it rots off by itself after {@link #MOST} ticks.</li>
 * </ul>
 * The weaponsmith sells nets (data trade {@code weaponsmith/net}); guards making an arrest throw one at a crook who has run
 * past arm's reach ({@link Arrests}), and an orc keeping goblin slaves nets one running off ({@link Slaves}). A guard or
 * slaver has one net a day ({@link #spare}).
 */
public final class Nets {
	/** A net's hold lasts at the longest this long (ticks); a Might save comes round this often (ticks). */
	static final int MOST = 20 * 40, SAVE_EVERY = 60;
	/** Netted: slowness level (0 is the first). */
	static final int CRAWL = 4;
	/** A guard or slaver throws a net from this far to this far (blocks). */
	public static final double THROW_NEAR = 3.5, THROW_FAR = 12.0;
	/** The wait between throws of a player's (ticks). */
	static final int THROW_COOLDOWN = 20;
	/** Cutting a net costs the blade this much wear. */
	static final int CUT_WEAR = 1;
	/** A day in ticks (a guard or slaver has a net a day). */
	static final long DAY = 24000L;

	public static Item NET;

	/** The game day a guard or slaver last threw its net. Saved: a restart doesn't hand them another. */
	private static final AttachmentType<Long> THROWN_DAY = AttachmentRegistry.create(JaymosKingdoms.id("net_day"), b -> b.persistent(Codec.LONG));

	private static final class Hold {
		long until, nextSave;
	}

	private static final Map<LivingEntity, Hold> HOLDS = new WeakHashMap<>();

	private Nets() {
	}

	static void register() {
		ThrownNet.register();
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("net"));
		NET = Registry.register(BuiltInRegistries.ITEM, key, new NetItem(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(NET));
		ServerTickEvents.END_SERVER_TICK.register(Nets::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> HOLDS.clear());
		// bad footing for the netted, the upper hand against them
		UpperHand.add((attacker, target) -> netted(target) ? 1 : netted(attacker) ? -1 : 0);
		// a blade used in the air frees yourself; used on someone netted frees them
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (!world.isClientSide() && netted(player) && cut(player, player, player.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && entity instanceof LivingEntity other && netted(other) && cut(player, other, player.getItemInHand(hand))) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code who} is held in a net just now. */
	public static boolean netted(LivingEntity who) {
		Hold h = HOLDS.get(who);
		return h != null && who.level().getGameTime() < h.until;
	}

	/** A net lands on {@code who}: it crawls and swings badly until it is free. Returns false for one nothing can hold (a boss). */
	public static boolean catchIn(LivingEntity who, @Nullable LivingEntity by) {
		if (!(who.level() instanceof ServerLevel level) || who instanceof Mob m && Senses.boss(m) || who instanceof Player p && p.isSpectator()) {
			return false;
		}
		Hold h = new Hold();
		h.until = level.getGameTime() + MOST;
		h.nextSave = level.getGameTime() + SAVE_EVERY;
		HOLDS.put(who, h);
		who.setSprinting(false);
		who.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, CRAWL, true, false, false), by);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.COBWEB), who.getX(), who.getY(0.5), who.getZ(), 14, 0.3, 0.5, 0.3, 0.05);
		if (who instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		return true;
	}

	/** Frees {@code who} from a net, as it falls apart (a save, a cut, or a cure). */
	public static void free(LivingEntity who, boolean cut) {
		if (HOLDS.remove(who) == null || !(who.level() instanceof ServerLevel level)) {
			return;
		}
		who.removeEffect(MobEffects.SLOWNESS);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), cut ? SoundEvents.SHEEP_SHEAR : SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 0.9F, 1.1F);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.STRING), who.getX(), who.getY(0.5), who.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
		who.spawnAtLocation(level, new ItemStack(Items.STRING, 1 + who.getRandom().nextInt(2)));
	}

	/** Whether {@code stack} is a blade that cuts a net. */
	static boolean blade(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ClassPerks.LIGHT_BLADES) || stack.is(Items.SHEARS);
	}

	/** {@code by} cuts {@code who} out of their net with {@code stack}; false if it isn't a blade. */
	private static boolean cut(Player by, LivingEntity who, ItemStack stack) {
		if (!blade(stack)) {
			return false;
		}
		if (stack.isDamageableItem()) {
			stack.hurtAndBreak(CUT_WEAR, by, by.getUsedItemHand() == InteractionHand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
		}
		free(who, true);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (HOLDS.isEmpty()) {
			return;
		}
		for (Map.Entry<LivingEntity, Hold> e : new ArrayList<>(HOLDS.entrySet())) {
			LivingEntity who = e.getKey();
			Hold h = e.getValue();
			if (who == null || !who.isAlive() || who.isRemoved() || !(who.level() instanceof ServerLevel level)) {
				HOLDS.remove(who);
				continue;
			}
			long now = level.getGameTime();
			if (now >= h.until) {
				free(who, false);
				continue;
			}
			if (now % 10 == 0) {
				who.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, CRAWL, true, false, false));
			}
			if (now >= h.nextSave) {
				h.nextSave = now + SAVE_EVERY;
				// a mob with a blade in hand saws at the net too
				boolean sawing = who instanceof Mob && blade(who.getMainHandItem()) && who.getRandom().nextBoolean();
				if (sawing || Saves.resist(who, Saves.Kind.GRAB)) {
					free(who, sawing);
				}
			}
		}
	}

	/** Whether a guard or slaver has its net to throw today; if so, it is spent. */
	public static boolean spare(Mob thrower) {
		long day = thrower.level().getGameTime() / DAY;
		Long last = thrower.getAttached(THROWN_DAY);
		if (last != null && last == day) {
			return false;
		}
		thrower.setAttached(THROWN_DAY, day);
		return true;
	}

	/** {@code thrower} (a guard or slaver) throws a net at {@code target} if it has one today and the target is in range and in sight. */
	public static boolean guardThrow(ServerLevel level, Mob thrower, LivingEntity target) {
		double d = thrower.distanceTo(target);
		if (d < THROW_NEAR || d > THROW_FAR || !thrower.hasLineOfSight(target) || netted(target) || !spare(thrower)) {
			return false;
		}
		Vec3 from = thrower.getEyePosition();
		Vec3 to = target.getBoundingBox().getCenter();
		ThrownNet.throwAt(level, thrower, target, from, to);
		thrower.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		return true;
	}

	/** A net in hand: use it to throw it. */
	static final class NetItem extends Item {
		NetItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(net.minecraft.world.level.Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel server) {
				Vec3 look = player.getLookAngle();
				ThrownNet.throwFrom(server, player, look.scale(ThrownNet.THROW_SPEED).add(0.0, ThrownNet.THROW_LOFT, 0.0)
					.add(player.getDeltaMovement().multiply(1.0, 0.0, 1.0)));
			}
			ItemStack held = player.getItemInHand(hand);
			player.getCooldowns().addCooldown(held, THROW_COOLDOWN);
			held.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}
}
