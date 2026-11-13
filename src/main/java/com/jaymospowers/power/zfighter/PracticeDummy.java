package com.jaymospowers.power.zfighter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * (0.18.72) The Sparring Dummy for anyone who isn't a Z Fighter: it stands a still practice dummy (a mannequin) where it's
 * used, for Weapon Forms practice and setting the belt. Blows land on it but never hurt it; sneak and strike it to take
 * it back up. A Z Fighter's use is still the sparring partner ({@link ZFighterSparring}).
 * <p>
 * It is a target dummy too (the idea of MehVahdJukaar's MmmMmmMmmMmm, CC0, which has no 26.3 build; our own code):
 * <ul>
 * <li>every blow shows what it would have done as a number that floats up off the dummy, and tells the one who struck
 * it the blow, the running total and the damage a second of this bout (a bout ends after {@link #BOUT_GAP} ticks of
 * rest);</li>
 * <li>it can be dressed: use armour (or a shield, or anything held) on it to put it on, and use an empty hand on it
 * while sneaking to take everything off. What it wears counts: the numbers are after its armour;</li>
 * <li>it flinches when struck, and gives back what it wears when it is taken up.</li>
 * </ul>
 */
final class PracticeDummy {
	static final String TAG = "jaymospowers_practice_dummy";
	// ---- tuning ----
	/** Ticks without a blow that end a bout (the total and the damage a second start again). */
	static final int BOUT_GAP = 60;
	/** A number's life in ticks, how far it rises (blocks), and how far from the dummy's middle it may start. */
	static final int NUMBER_TICKS = 24;
	static final double NUMBER_RISE = 0.9, NUMBER_SPREAD = 0.35;
	/** Blows at least this hard show gold, and this hard red. */
	static final float BIG_BLOW = 10.0F, HUGE_BLOW = 20.0F;

	/** One dummy's bout: when it began, the last blow, the total so far. */
	private static final class Bout {
		long start, last;
		float total;
	}

	/** A number in the air and the game time it goes. */
	private record Shown(Display.TextDisplay display, long until) {
	}

	private static final Map<UUID, Bout> BOUTS = new HashMap<>();
	private static final List<Shown> NUMBERS = new ArrayList<>();

	private PracticeDummy() {
	}

	static void init() {
		// struck, it shrugs it off: a thud and a puff of straw
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Mannequin) || !entity.entityTags().contains(TAG)) {
				return true;
			}
			if (entity.level() instanceof ServerLevel level && source.getEntity() instanceof Player) {
				level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.2, entity.getZ(), 4, 0.2, 0.3, 0.2, 0.05);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_STAND_HIT, SoundSource.PLAYERS, 0.8F, 1.0F);
			}
			if (entity.level() instanceof ServerLevel level && amount > 0.0F) {
				struck(level, (Mannequin)entity, source, amount);
			}
			return false;
		});
		// dressing it: armour or a held thing goes on; an empty hand while sneaking takes everything off
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Mannequin dummy) || !entity.entityTags().contains(TAG) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				dress(server, sp, dummy);
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (NUMBERS.isEmpty()) {
				return;
			}
			long now = server.overworld().getGameTime();
			Iterator<Shown> it = NUMBERS.iterator();
			while (it.hasNext()) {
				Shown shown = it.next();
				if (now >= shown.until || !shown.display.isAlive()) {
					shown.display.discard();
					it.remove();
				}
			}
		});
		// numbers are never left hanging in a saved world
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Shown shown : NUMBERS) {
				shown.display.discard();
			}
			NUMBERS.clear();
			BOUTS.clear();
		});
		// sneak and strike it: it's picked back up
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Mannequin) || !entity.entityTags().contains(TAG) || !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && entity.isAlive()) {
				strip(server, sp, (Mannequin)entity);
				BOUTS.remove(entity.getUUID());
				entity.discard();
				server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_STAND_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
				if (!sp.isCreative()) {
					sp.getInventory().placeItemBackInInventory(new ItemStack(ZFighterItems.SPARRING_DUMMY), Prediction.SERVER_ONLY);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** A blow of {@code amount} landed: it flinches, shows the number (after what it wears) and tells the striker the bout. */
	private static void struck(ServerLevel level, Mannequin dummy, DamageSource source, float amount) {
		float dealt = CombatRules.getDamageAfterAbsorb(dummy, amount, source, dummy.getArmorValue(), (float)dummy.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		level.broadcastDamageEvent(dummy, source);
		long now = level.getGameTime();
		Bout bout = BOUTS.computeIfAbsent(dummy.getUUID(), id -> new Bout());
		if (bout.total <= 0.0F || now - bout.last > BOUT_GAP) {
			bout.start = now;
			bout.total = 0.0F;
		}
		bout.last = now;
		bout.total += dealt;
		number(level, dummy, dealt, now);
		if (source.getEntity() instanceof ServerPlayer striker) {
			float seconds = Math.max(1.0F, (now - bout.start) / 20.0F);
			striker.sendOverlayMessage(Component.translatable("message.jaymospowers.practice_dummy.bout", one(dealt), one(bout.total), one(bout.total / seconds)));
		}
	}

	private static String one(float value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** The number of a blow, floating up off the dummy for a moment (a text display, moved once so the client glides it). */
	private static void number(ServerLevel level, Mannequin dummy, float dealt, long now) {
		Display.TextDisplay display = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
		if (display == null) {
			return;
		}
		Component text = Component.literal(one(dealt)).withStyle(dealt >= HUGE_BLOW ? ChatFormatting.RED : dealt >= BIG_BLOW ? ChatFormatting.GOLD : ChatFormatting.WHITE);
		CompoundTag tag = new CompoundTag();
		ComponentSerialization.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text).result().ifPresent(encoded -> tag.put("text", encoded));
		tag.putString("billboard", "center");
		tag.putInt("teleport_duration", NUMBER_TICKS);
		tag.putInt("background", 0);
		tag.putBoolean("shadow", true);
		tag.putBoolean("see_through", true);
		display.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		double x = dummy.getX() + (level.getRandom().nextDouble() - 0.5) * 2.0 * NUMBER_SPREAD;
		double z = dummy.getZ() + (level.getRandom().nextDouble() - 0.5) * 2.0 * NUMBER_SPREAD;
		double y = dummy.getY() + dummy.getBbHeight() + 0.2;
		display.snapTo(x, y, z, 0.0F, 0.0F);
		if (!level.addFreshEntity(display)) {
			return;
		}
		display.teleportTo(x, y + NUMBER_RISE, z);
		NUMBERS.add(new Shown(display, now + NUMBER_TICKS));
	}

	/** {@code player} used a hand on the dummy: what they hold goes on it (what it wore there comes back), or, sneaking empty-handed, everything comes off. */
	private static void dress(ServerLevel level, ServerPlayer player, Mannequin dummy) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			if (player.isShiftKeyDown()) {
				strip(level, player, dummy);
				level.playSound(null, dummy.getX(), dummy.getY(), dummy.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, 0.9F);
			}
			return;
		}
		EquipmentSlot slot = dummy.getEquipmentSlotForItem(held);
		ItemStack old = dummy.getItemBySlot(slot).copy();
		dummy.setItemSlot(slot, held.copyWithCount(1));
		if (!player.isCreative()) {
			held.shrink(1);
		}
		if (!old.isEmpty()) {
			player.getInventory().placeItemBackInInventory(old, Prediction.SERVER_ONLY);
		}
		level.playSound(null, dummy.getX(), dummy.getY(), dummy.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	/** Everything the dummy wears or holds goes back to {@code player}. */
	private static void strip(ServerLevel level, ServerPlayer player, Mannequin dummy) {
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack worn = dummy.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				dummy.setItemSlot(slot, ItemStack.EMPTY);
				player.getInventory().placeItemBackInInventory(worn.copy(), Prediction.SERVER_ONLY);
			}
		}
	}

	/** Stands a practice dummy on the face {@code context} clicked, facing the one who set it. */
	static InteractionResult place(UseOnContext context, ServerPlayer player, ServerLevel level) {
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.above()).canBeReplaced()) {
			return InteractionResult.FAIL;
		}
		Mannequin dummy = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		if (dummy == null) {
			return InteractionResult.FAIL;
		}
		dummy.addTag(TAG);
		dummy.setCustomName(Component.translatable("entity.jaymospowers.practice_dummy"));
		dummy.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
		if (!level.addFreshEntity(dummy)) {
			return InteractionResult.FAIL;
		}
		level.playSound(null, dummy.getX(), dummy.getY(), dummy.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
