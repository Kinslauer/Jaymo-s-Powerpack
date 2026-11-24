package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Call back what you've slain (Tensura Neo Otherworld's shadow summons, ARR: the idea only, our own code), next to the necromancers'
 * thralls ({@link Thralls}). What a player kills leaves a mark ({@link #SLAIN}: the kinds slain, the latest last, at most
 * {@link #KEPT}). At a lit soul campfire, sneak and use it with an empty hand to turn the rite's pick through the kinds you
 * have slain that can fight; sneak and use it with a bone in the hand to pay {@link #LEVELS} levels and the bone and call the picked kind's
 * <b>shade</b> to fight for you for {@link #LIFE} ticks. A shade is raised as a thrall of the player ({@link Thralls#raise}), glowing, and
 * no more than {@link #MOST} stand at once; the rite can be held again after {@link #COOLDOWN} ticks. Nothing too great (past
 * {@link Thralls}' health limit) answers.
 */
public final class Shades {
	/** Kinds remembered; shades at once; levels a rite costs; how long a shade stands and how long before the next rite (ticks). */
	static final int KEPT = 8, MOST = 2, LEVELS = 2;
	static final int LIFE = 20 * 40;
	static final long COOLDOWN = 20L * 60;
	/** The most health a shade may have (the limit of the risen). */
	static final float GREATEST = 150.0F;

	/** The kinds {@code player} has slain, as entity type ids, the latest last. Saved, kept through death. */
	static final AttachmentType<List<String>> SLAIN = AttachmentRegistry.create(JaymosKingdoms.id("slain_kinds"), b -> b.persistent(Codec.STRING.listOf()).copyOnDeath());
	private static final Map<UUID, Integer> PICK = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Shades() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (source.getEntity() instanceof ServerPlayer p && !(dead instanceof Player) && !Thralls.isThrall(dead)) {
				String id = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
				List<String> slain = new ArrayList<>(p.getAttachedOrElse(SLAIN, List.of()));
				slain.remove(id);
				slain.add(id);
				while (slain.size() > KEPT) {
					slain.remove(0);
				}
				p.setAttached(SLAIN, List.copyOf(slain));
			}
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || !sp.isShiftKeyDown()
				|| !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			var state = level.getBlockState(hit.getBlockPos());
			if (!state.is(Blocks.SOUL_CAMPFIRE) || !state.getValue(CampfireBlock.LIT)) {
				return InteractionResult.PASS;
			}
			if (sp.getMainHandItem().isEmpty()) {
				turn(sp);
			} else if (sp.getMainHandItem().is(Items.BONE)) {
				call(sp, server, Vec3.atBottomCenterOf(hit.getBlockPos().above()));
			} else {
				return InteractionResult.PASS;
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** The kinds {@code player} could call: slain, and able to fight (the latest first). */
	static List<EntityType<?>> callable(Player player) {
		List<EntityType<?>> out = new ArrayList<>();
		List<String> slain = player.getAttachedOrElse(SLAIN, List.of());
		for (int i = slain.size() - 1; i >= 0; i--) {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(slain.get(i))).orElse(null);
			if (type != null && type.getCategory() != net.minecraft.world.entity.MobCategory.MISC && DefaultAttributes.hasSupplier(type)
				&& DefaultAttributes.getSupplier((EntityType) type).hasAttribute(Attributes.ATTACK_DAMAGE)) {
				out.add(type);
			}
		}
		return out;
	}

	/** Turns the rite's pick to the next kind. */
	private static void turn(ServerPlayer player) {
		List<EntityType<?>> kinds = callable(player);
		if (kinds.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.none"));
			return;
		}
		int next = (PICK.getOrDefault(player.getUUID(), -1) + 1) % kinds.size();
		PICK.put(player.getUUID(), next);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.pick", kinds.get(next).getDescription()));
	}

	@SuppressWarnings("unchecked")
	private static void call(ServerPlayer player, ServerLevel level, Vec3 at) {
		List<EntityType<?>> kinds = callable(player);
		if (kinds.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.none"));
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < COOLDOWN && now >= last) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.quiet"));
			return;
		}
		if (Thralls.of(player.getUUID()).stream().filter(m -> m.entityTags().contains(TAG)).count() >= MOST) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.crowded", MOST));
			return;
		}
		if (!player.isCreative() && player.experienceLevel < LEVELS) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.levels", LEVELS));
			return;
		}
		EntityType<?> kind = kinds.get(Math.min(PICK.getOrDefault(player.getUUID(), 0), kinds.size() - 1));
		Mob shade = Thralls.raise(level, (EntityType<? extends Mob>) kind, at, -1, player.getUUID(), true, LIFE, null);
		if (shade == null) {
			return;
		}
		if (shade.getMaxHealth() > GREATEST) {
			shade.discard();
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.too_great"));
			return;
		}
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
			player.giveExperienceLevels(-LEVELS);
		}
		LAST.put(player.getUUID(), now);
		shade.addTag(TAG);
		shade.addEffect(new MobEffectInstance(MobEffects.GLOWING, LIFE, 0, true, false, false));
		shade.setCustomName(Component.translatable("shade.jaymoskingdoms.name", kind.getDescription()));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shade.called", kind.getDescription()));
	}

	/** The scoreboard tag every shade carries. */
	public static final String TAG = "jaymoskingdoms.shade";
}
