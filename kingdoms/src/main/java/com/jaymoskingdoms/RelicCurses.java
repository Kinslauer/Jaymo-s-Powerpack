package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The curses a found relic or bauble hides until it is bonded (Jaymo's Powers' {@code Bonds}). The curse is a tag on the item
 * ({@link ItemCurses}); until a rest has bonded the thing to someone it does nothing and shows nothing. Once bonded:
 * <ul>
 * <li><b>Clinging:</b> you cannot put it down. It will not leave a slot, the hand or the ground (see {@code SlotCurseMixin},
 * {@code PlayerDropMixin}), and the bond will not let go.</li>
 * <li><b>Thirst:</b> it drinks your blood each time you kill: {@link #BLOOD} health, never enough to kill you.</li>
 * <li><b>Whispers:</b> now and then it murmurs, in a bubble only you see, the way to a friend of yours, and how near.</li>
 * <li><b>Misfortune:</b> bad luck ({@link #LUCK}) while you carry it.</li>
 * </ul>
 * A cleric lifts a curse for a fee (less beside a shrine): hold the cursed thing and speak to them. {@link Dispel} lifts it too.
 */
public final class RelicCurses {
	/** The curses, by the name written on the item. */
	public static final List<String> KINDS = List.of("clinging", "thirst", "whispers", "misfortune");
	/** Thirst: the health a kill costs. */
	static final float BLOOD = 2.0F;
	/** Misfortune: the luck taken. */
	static final double LUCK = -3.0;
	/** Whispers: the chance each second of a murmur (one in this many), and how far a friend may be (blocks). */
	static final int WHISPER_ONE_IN = 75;
	static final double FRIEND_RANGE = 160.0;
	/** A cleric's fee to lift a curse, in coin: beside a shrine and away from one; and how far a shrine counts. */
	static final int FEE_SHRINE = 4, FEE = 14;
	static final double SHRINE_RANGE = 12.0;
	private static final Identifier LUCK_ID = JaymosKingdoms.id("cursed_luck");

	private RelicCurses() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RelicCurses::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && entity != player && has(player, "thirst")) {
				drink(player);
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && hand == net.minecraft.world.InteractionHand.MAIN_HAND
				&& !sp.isShiftKeyDown() && Accusations.priest(entity) && ItemCurses.tag(sp.getMainHandItem()) != null) {
				offer(server, sp, (Villager) entity);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code player} carries a thing under {@code curse} that is bonded to them (so the curse has shown itself). */
	static boolean has(ServerPlayer player, String curse) {
		for (ItemStack stack : ItemBonds.carried(player)) {
			if (curse.equals(ItemCurses.tag(stack)) && ItemBonds.bonded(player, stack)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code stack} clings to {@code player}: cursed so, and bonded to them. */
	public static boolean clings(Player player, ItemStack stack) {
		return !stack.isEmpty() && "clinging".equals(ItemCurses.tag(stack)) && ItemBonds.bonded(player, stack);
	}

	private static void drink(ServerPlayer player) {
		float take = Math.min(BLOOD, player.getHealth() - 1.0F);
		if (take <= 0.0F || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		player.hurtServer(level, level.damageSources().magic(), take);
		level.sendParticles(PowersCompat.blood(0x8A0A12, 0.9F), player.getX(), player.getY(1.0), player.getZ(), 10, 0.25, 0.4, 0.25, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AttributeInstance luck = player.getAttribute(Attributes.LUCK);
			if (luck != null) {
				boolean unlucky = has(player, "misfortune");
				if (unlucky && !luck.hasModifier(LUCK_ID)) {
					luck.addOrReplacePermanentModifier(new AttributeModifier(LUCK_ID, LUCK, AttributeModifier.Operation.ADD_VALUE));
				} else if (!unlucky && luck.hasModifier(LUCK_ID)) {
					luck.removeModifier(LUCK_ID);
				}
			}
			if (player.getRandom().nextInt(WHISPER_ONE_IN) == 0 && has(player, "whispers")) {
				whisper(player);
			}
		}
	}

	/** The thing murmurs a way to a friend of yours; in a bubble only you see. */
	private static void whisper(ServerPlayer player) {
		LivingEntity friend = null;
		double best = FRIEND_RANGE * FRIEND_RANGE;
		for (ServerPlayer other : player.level().players()) {
			double d = other.distanceToSqr(player);
			if (other != player && other.isAlive() && !other.isSpectator() && d < best) {
				best = d;
				friend = other;
			}
		}
		if (friend == null) {
			for (LivingEntity pet : player.level().getEntitiesOfClass(net.minecraft.world.entity.TamableAnimal.class, player.getBoundingBox().inflate(FRIEND_RANGE / 4),
				t -> t.isTame() && t.isOwnedBy(player) && t.isAlive())) {
				friend = pet;
				break;
			}
		}
		if (friend == null) {
			Speech.privately(player, player, Component.translatable("speech.jaymoskingdoms.curse.whisper.alone"), 0xB07CC8);
			return;
		}
		double dx = friend.getX() - player.getX(), dz = friend.getZ() - player.getZ();
		String way = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
		String near = Math.sqrt(best) < 24 ? "close" : Math.sqrt(best) < 80 ? "near" : "far";
		Speech.privately(player, player, Component.translatable("speech.jaymoskingdoms.curse.whisper." + near, friend.getDisplayName(),
			Component.translatable("direction.jaymoskingdoms.word." + way)), 0xB07CC8);
		player.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.5F, 0.6F);
	}

	// ---- a cleric lifts it ---------------------------------------------------------------------------------------------

	private static boolean beside(ServerLevel level, BlockPos pos) {
		int r = (int) SHRINE_RANGE;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -3, -r), pos.offset(r, 3, r))) {
			if (level.getBlockState(p).is(Healing.SHRINE)) {
				return true;
			}
		}
		return false;
	}

	private static void offer(ServerLevel level, ServerPlayer player, Villager priest) {
		int fee = beside(level, priest.blockPosition()) ? FEE_SHRINE : FEE;
		boolean can = Money.canPay(player, Money.worth(fee));
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(Component.translatable("curse.jaymoskingdoms.lift", fee), can, p -> lift(level, p, priest, fee)));
		Menus.ask(player, priest, Component.translatable("curse.jaymoskingdoms.priest"), choices);
	}

	private static void lift(ServerLevel level, ServerPlayer player, Villager priest, int fee) {
		ItemStack held = player.getMainHandItem();
		if (ItemCurses.tag(held) == null || !Money.charge(player, Money.worth(fee))) {
			return;
		}
		lifted(level, player, held);
		Speech.tell(level, priest, player, Component.translatable("curse.jaymoskingdoms.lifted"), Speech.WHITE);
	}

	/** The curse on {@code stack} ends: the bond may be let go, a light goes up from it. */
	static void lifted(ServerLevel level, ServerPlayer player, ItemStack stack) {
		ItemCurses.lift(stack);
		ItemBonds.resync(player);
		level.sendParticles(Fx.LIGHT, player.getX(), player.getY(1.0), player.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 1.5F);
	}
}
