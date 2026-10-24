package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.184) Lineage, in place of the Blood Rite. Andy: "add a lineage system that replaces blood rites, now instead
 * unlocking a heritage will allow you to swap perspectives with your offspring, and then use the heritage and new class."
 * Then: "shift right clicking your teen and above should prompt you if you're sure you want to progress your bloodline's
 * story, then make the swap".
 * <ul>
 * <li>A player's child is born to their heritage or any earned one they've woken, at random
 * ({@link VillagerHeritage#passed}), so the blood isn't theirs to steer.</li>
 * <li>Sneak-use one of your own MCA children, a teen or older, with an empty hand: a prompt ({@link Ask}) asks whether
 * to progress your bloodline's story, and whether your things go down to the child or stay with your elder.</li>
 * <li>Yes: you open your eyes where the child stood, with their look, name, heritage and place in MCA's family tree; your
 * class is unmade and asked again, and every skill and attribute point comes back to spend (experience stays).</li>
 * <li>The body you leave stays as a villager with your old look, name and heritage, an elder already, in your old place
 * in the family. It lives on by itself (ask them to follow you as MCA family will) and dies of old age in time
 * ({@link OldAge}). If they keep your things, they wear your armour and carry the rest.</li>
 * <li>Your power doesn't come with you: Jaymo's Powers hands it down as the child's inheritance, to awaken in them in
 * time like any power ({@link PowersCompat#passDown}).</li>
 * </ul>
 * It needs MCA; without it nothing happens.
 */
public final class Lineage {
	/** How far the child may have moved off when the answer comes back (blocks). */
	static final double REACH = 8.0;
	/** The moment of waking: darkness this long (ticks). */
	static final int WAKE_DARK_TICKS = 60;
	/** The answers: not yet, hand your things down to the child, leave them with your elder. */
	public static final int NOT_YET = 0, HAND_DOWN = 1, ELDER_KEEPS = 2;

	/** Player → the child whose prompt they have up (not saved). */
	private static final Map<UUID, UUID> OPEN = new HashMap<>();

	/**
	 * Server → player: progress the bloodline? {@code child} the child's name, {@code heritage} their heritage's key (or
	 * ""), {@code power} the translation key of the power that would be handed down (or "").
	 */
	public record Ask(String child, String heritage, String power) implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("lineage_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.child);
				buf.writeUtf(p.heritage);
				buf.writeUtf(p.power);
			}, buf -> new Ask(buf.readUtf(), buf.readUtf(), buf.readUtf()));

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** Player → server: their answer ({@link #NOT_YET}, {@link #HAND_DOWN}, {@link #ELDER_KEEPS}). */
	public record Answer(int choice) implements CustomPacketPayload {
		public static final Type<Answer> TYPE = new Type<>(JaymosKingdoms.id("lineage_answer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.choice), buf -> new Answer(buf.readVarInt()));

		@Override
		public Type<Answer> type() {
			return TYPE;
		}
	}

	private Lineage() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Ask.TYPE, Ask.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Answer.TYPE, Answer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Answer.TYPE, (payload, context) -> answered(context.player(), payload.choice()));
		// (registered before Retinue, Pickpocket and Quests: only your own child, a teen or older)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
				|| !sp.getMainHandItem().isEmpty() || sp.isSpectator() || !(entity instanceof Villager child) || Heritage.of(sp) == null
				|| !McaCompat.childOf(child, sp) || !McaCompat.teenOrOlder(child) || !child.isAlive() || Downed.isDowned(child)
				|| Downed.isDowned(sp) || !ServerPlayNetworking.canSend(sp, Ask.TYPE)) {
				return InteractionResult.PASS;
			}
			OPEN.put(sp.getUUID(), child.getUUID());
			world.playSound(null, child.getX(), child.getY(), child.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.7F);
			Heritage.Kind blood = VillagerHeritage.of(child);
			String power = PowersCompat.power(sp);
			ServerPlayNetworking.send(sp, new Ask(child.getDisplayName().getString(), blood == null ? "" : blood.key(),
				power == null ? "" : "power.jaymospowers." + power));
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OPEN.remove(handler.player.getUUID()));
	}

	/** {@code player} answered the prompt. */
	private static void answered(ServerPlayer player, int choice) {
		UUID id = OPEN.remove(player.getUUID());
		if (id == null || choice != HAND_DOWN && choice != ELDER_KEEPS || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Entity found = level.getEntity(id);
		if (!(found instanceof Villager child) || !child.isAlive() || Downed.isDowned(child) || Downed.isDowned(player) || child.distanceTo(player) > REACH
			|| !McaCompat.childOf(child, player) || !McaCompat.teenOrOlder(child)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lineage.gone").withStyle(ChatFormatting.GRAY));
			return;
		}
		liveOn(player, level, child, choice == ELDER_KEEPS);
	}

	/** {@code player} lives on as {@code child}; {@code elderKeeps}: their things stay with the elder they leave. */
	private static void liveOn(ServerPlayer player, ServerLevel level, Villager child, boolean elderKeeps) {
		Component parentName = player.getDisplayName();
		Heritage.Kind was = Heritage.of(player);
		Heritage.Kind blood = VillagerHeritage.of(child);
		Vec3 to = child.position();
		float yaw = child.getYRot(), pitch = child.getXRot();
		long elderSince = level.getGameTime() - CourtWizards.ELDER_DAYS * CourtWizards.DAY_TICKS;
		PathfinderMob parent = McaCompat.takeOver(player, child, elderSince);
		if (parent == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lineage.gone").withStyle(ChatFormatting.GRAY));
			return;
		}
		// (the old self keeps the old blood, and if so their things)
		if (was != null) {
			VillagerHeritage.born(parent, was, null);
		}
		if (elderKeeps) {
			leaveWith(player, parent);
		}
		level.sendParticles(ParticleTypes.SOUL, parent.getX(), parent.getY() + 1.0, parent.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		player.stopRiding();
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, pitch, false);
		player.clearFire();
		player.setHealth(player.getMaxHealth());
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, WAKE_DARK_TICKS, 0, false, false, false));
		// the child's blood, their own power still to wake, a new class to choose, and every point to spend again
		PowersCompat.passDown(player);
		if (blood != null && blood != was) {
			Heritage.set(player, blood);
		}
		Skills.respec(player);
		Classes.clear(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		Heritage.Kind now = Heritage.of(player);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lineage.lives_on", parentName,
			now == null ? Component.empty() : Component.translatable("heritage.jaymoskingdoms." + now.key())).withStyle(ChatFormatting.GOLD));
		Classes.ask(player, true);
	}

	/**
	 * Everything {@code player} carries goes to the elder they leave: their armour and what's in their hands worn (it drops
	 * whole when the elder dies), the rest in their pack; whatever doesn't fit falls at the elder's feet.
	 */
	private static void leaveWith(ServerPlayer player, PathfinderMob elder) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR && slot.getType() != EquipmentSlot.Type.HAND) {
				continue;
			}
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				elder.setItemSlot(slot, stack.copyAndClear());
				elder.setDropChance(slot, 2.0F);
			}
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.removeItemNoUpdate(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack left = McaCompat.stow(elder, stack);
			if (!left.isEmpty()) {
				elder.spawnAtLocation((ServerLevel)elder.level(), left);
			}
		}
		inventory.setChanged();
	}
}
