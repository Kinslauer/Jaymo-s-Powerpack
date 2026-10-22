package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Werewolf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.0) Stage 3 of the 0.10.0 drop: a player as a werewolf, by ritual. Under a full moon, at night, out under the
 * sky, with at least {@link #RITUAL_FIRES} lit campfires round them, a player sneaks and offers a bone (use it): the
 * curse takes them, and the first change comes at once.
 *
 * <p>Every full moon the change is forced on them and lasts till dawn. After {@link #MASTERY_MOONS} moons they can
 * change at will ({@code U}), and back, except under a full moon. The change: they double over (bones cracking, the
 * world swimming), then the beast tears out of them, their own {@link Werewolf} (size, build and coat theirs) that every
 * other player sees in their place, step for step; their own body is hidden. As the beast: strong, quick, high-jumping,
 * tougher, seeing in the dark, always hungry; their armour is torn (30% of its durability) and breaks or falls at their
 * feet, and their hands are claws (nothing can be held).
 * Blows to the beast are theirs. Guards hunt it and villagers run. At dawn it shrinks back into them, sick and shaking.
 * A priest's rare quest cures it ({@link Cures}).
 */
public final class WerewolfPlayers {
	/** Lit campfires the ritual wants within {@link #RITUAL_RANGE} blocks. */
	static final int RITUAL_FIRES = 4, RITUAL_RANGE = 5;
	/** Moons changed under before the change comes at will. */
	static final int MASTERY_MOONS = 3;
	/** The doubling over before the beast comes (ticks). */
	static final int CONVULSE_TICKS = 40;
	/** As the beast: Strength, Speed, Jump Boost, Resistance levels (amplifiers), and extra health. */
	static final int BEAST_STRENGTH = 1, BEAST_SPEED = 0, BEAST_JUMP = 1, BEAST_RESISTANCE = 0;
	static final double BEAST_HEALTH = 10.0;
	/** The share of each armour piece's durability the change tears off. */
	static final float ARMOUR_TEAR = 0.3F;
	/** What a kill feeds the beast (food, saturation). */
	static final int KILL_FOOD = 6;
	static final float KILL_SATURATION = 0.8F;
	/** After changing back: sick and shaking this long (ticks). */
	static final int AFTER_TICKS = 600;

	private static final Identifier HEALTH = JaymosKingdoms.id("werewolf_health");

	/** Player → server: change ({@code U}). */
	public record Shift() implements CustomPacketPayload {
		public static final Type<Shift> TYPE = new Type<>(JaymosKingdoms.id("werewolf_shift"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shift> CODEC = StreamCodec.unit(new Shift());

		@Override
		public Type<Shift> type() {
			return TYPE;
		}
	}

	/** A player mid-change or changed: their beast (null while they're doubled over), when it began, whether the moon forced it. */
	private static final class Form {
		@Nullable Werewolf beast;
		final long since;
		final boolean forced;
		boolean leaving;

		Form(long since, boolean forced) {
			this.since = since;
			this.forced = forced;
		}
	}

	private static final Map<UUID, Form> FORMS = new HashMap<>();

	private WerewolfPlayers() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Shift.TYPE, Shift.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Shift.TYPE, (payload, context) -> shift(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(WerewolfPlayers::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> player instanceof ServerPlayer sp && level instanceof ServerLevel server
			&& hand == InteractionHand.MAIN_HAND && sp.isShiftKeyDown() && sp.getMainHandItem().is(Items.BONE) && ritual(server, sp)
			? InteractionResult.SUCCESS : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			Form f = FORMS.get(player.getUUID());
			if (!level.isClientSide() && f != null && f.beast != null) {
				f.beast.claw();
			}
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof Werewolf w) || !puppet(w)) {
				return true;
			}
			// (a blow to the beast is a blow to them)
			if (w.level().getServer() != null && w.level().getServer().getPlayerList().getPlayer(w.host()) instanceof ServerPlayer owner
				&& source.getEntity() != owner && owner.level() instanceof ServerLevel level) {
				owner.hurtServer(level, source, amount);
			}
			return false;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				end(player, false);
			}
			// (claws can't hold food: the beast eats what it kills)
			if (source.getEntity() instanceof ServerPlayer killer && beast(killer) && !(entity instanceof net.minecraft.world.entity.monster.Monster)) {
				killer.getFoodData().eat(KILL_FOOD, KILL_SATURATION);
				killer.level().playSound(null, killer.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				end(player, false);
			}
			FORMS.clear();
		});
	}

	/** Whether {@code player} is the beast now (or changing into it). */
	public static boolean beast(ServerPlayer player) {
		return FORMS.containsKey(player.getUUID());
	}

	/** Whether {@code w} is a player's beast. */
	public static boolean puppet(Werewolf w) {
		UUID host = w.host();
		if (host == null) {
			return false;
		}
		// (a cursed villager's beast has a mind of its own; a player's has none)
		return w.isNoAi();
	}

	/** Whether {@code w} is the beast a player is wearing now. */
	public static boolean current(Werewolf w) {
		Form f = w.host() == null ? null : FORMS.get(w.host());
		return f != null && f.beast == w;
	}

	static void cure(ServerPlayer player) {
		end(player, false);
		player.removeAttached(Lycans.CURSE);
	}

	// ---- the ritual ----------------------------------------------------------------------------------------------------

	private static boolean ritual(ServerLevel level, ServerPlayer player) {
		long clock = level.getOverworldClockTime();
		// (0.7.66) under any other moon the ritual only half takes: a wolfblood ({@link NightGifts}), who can come back
		// under a full moon to finish it
		boolean full = Lycans.fullMoon(level, clock);
		Lycans.Curse had = player.getAttached(Lycans.CURSE);
		if (level != level.getServer().overworld() || !Vampires.night(clock) || !level.canSeeSky(player.blockPosition().above())
			|| had != null && (!had.half() || !full) || Vampires.isVampire(player)) {
			return false;
		}
		int fires = 0;
		for (BlockPos p : BlockPos.betweenClosed(player.blockPosition().offset(-RITUAL_RANGE, -2, -RITUAL_RANGE),
			player.blockPosition().offset(RITUAL_RANGE, 2, RITUAL_RANGE))) {
			if (CampfireBlock.isLitCampfire(level.getBlockState(p)) && ++fires >= RITUAL_FIRES) {
				break;
			}
		}
		if (fires < RITUAL_FIRES) {
			return false;
		}
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
		}
		Lycans.Curse curse = had != null ? had.withMoons(had.moons()) : Lycans.roll(level, player);
		player.setAttached(Lycans.CURSE, full ? curse : curse.halved());
		player.removeAttached(Vampires.BITTEN);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY(0.5), player.getZ(), 60, 1.5, 1.0, 1.5, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().whineSound().value(),
			SoundSource.PLAYERS, 3.0F, full ? 0.4F : 0.7F);
		player.sendSystemMessage(Component.translatable(full ? "message.jaymoskingdoms.werewolf.ritual" : "message.jaymoskingdoms.werewolf.wolfblood")
			.withStyle(ChatFormatting.DARK_RED));
		if (full) {
			begin(player, level.getGameTime(), true);
		}
		return true;
	}

	// ---- the change ----------------------------------------------------------------------------------------------------

	/** {@code U}: at will, once they've mastered it (never under a full moon). */
	private static void shift(ServerPlayer player) {
		Lycans.Curse curse = player.getAttached(Lycans.CURSE);
		if (curse == null || !player.isAlive()) {
			return;
		}
		if (curse.half()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.werewolf.half").withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel overworld = player.level().getServer().overworld();
		long clock = overworld.getOverworldClockTime();
		Form f = FORMS.get(player.getUUID());
		if (f != null && (f.forced || Vampires.night(clock) && Lycans.fullMoon(overworld, clock))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.werewolf.moon").withStyle(ChatFormatting.DARK_RED));
			return;
		}
		if (curse.moons() < MASTERY_MOONS) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.werewolf.unmastered").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (f == null) {
			begin(player, overworld.getGameTime(), false);
		} else if (f.beast != null && !f.leaving) {
			f.leaving = true;
			f.beast.changeBack();
		}
	}

	private static void begin(ServerPlayer player, long now, boolean forced) {
		if (player.isPassenger()) {
			player.stopRiding();
		}
		FORMS.put(player.getUUID(), new Form(now, forced));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, CONVULSE_TICKS + 80, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CONVULSE_TICKS, 6, false, false, false));
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.2F, 0.6F);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		long now = overworld.getGameTime();
		long nightNo = Vampires.nightIndex(clock);
		boolean moon = Vampires.night(clock) && Lycans.fullMoon(overworld, clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Lycans.Curse curse = player.getAttached(Lycans.CURSE);
			if (curse == null || curse.half() || !player.isAlive()) {
				continue;
			}
			Form f = FORMS.get(player.getUUID());
			if (f == null) {
				if (moon && curse.night() != nightNo && !player.isSpectator()) {
					begin(player, now, true);
				}
				continue;
			}
			ServerLevel level = player.level();
			if (f.beast == null) {
				Lycans.convulse(level, player, now - f.since);
				if (now - f.since >= CONVULSE_TICKS) {
					tearOut(level, player, f, curse, nightNo);
				}
				continue;
			}
			Werewolf w = f.beast;
			if (w.isRemoved() || w.level() != level) {
				// (left behind in another world, or unloaded: a new one where they are)
				w.discard();
				f.beast = Lycans.beast(level, player, curse);
				if (f.beast == null) {
					end(player, false);
					continue;
				}
				f.beast.setNoAi(true);
				level.addFreshEntity(f.beast);
				w = f.beast;
			}
			if (f.forced && !moon && !f.leaving) {
				// (dawn)
				f.leaving = true;
				w.changeBack();
			}
			w.stepChange();
			follow(player, w);
			if (w.change() > 0) {
				Lycans.changingIn(level, w, w.change());
			}
			if (f.leaving && w.change() >= -1) {
				end(player, true);
				continue;
			}
			if ((now + player.getId()) % 20 == 0) {
				buffs(player);
			}
			strip(player, false);
		}
	}

	private static void tearOut(ServerLevel level, ServerPlayer player, Form f, Lycans.Curse curse, long nightNo) {
		Werewolf w = Lycans.beast(level, player, curse);
		if (w == null) {
			FORMS.remove(player.getUUID());
			return;
		}
		w.setNoAi(true);
		w.setCustomName(player.getDisplayName());
		w.setCustomNameVisible(false);
		f.beast = w;
		level.addFreshEntity(w);
		if (f.forced) {
			player.setAttached(Lycans.CURSE, curse.changedOn(nightNo));
		}
		Lycans.tearFx(level, player);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getModifier(HEALTH) == null) {
			health.addTransientModifier(new AttributeModifier(HEALTH, BEAST_HEALTH, AttributeModifier.Operation.ADD_VALUE));
		}
		buffs(player);
		strip(player, true);
	}

	/** The beast stands where they stand, looks where they look, hunches as they run. */
	private static void follow(ServerPlayer player, Werewolf w) {
		w.setPos(player.getX(), player.getY(), player.getZ());
		w.setDeltaMovement(player.getDeltaMovement());
		w.setYRot(player.getYRot());
		w.setXRot(player.getXRot());
		w.setYBodyRot(player.yBodyRot);
		w.setYHeadRot(player.getYHeadRot());
		w.setAggressive(player.isSprinting());
		w.setOnGround(player.onGround());
	}

	private static void buffs(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, BEAST_STRENGTH, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, BEAST_SPEED, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, BEAST_JUMP, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, BEAST_RESISTANCE, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, true, false, true));
	}

	/**
	 * Claws hold nothing (what's in the hands goes to the inventory), and the armour's torn off: as the beast bursts out
	 * ({@code tearing}) each piece takes {@link #ARMOUR_TEAR} of its durability, and breaks if that's the end of it, or
	 * falls at their feet. Armour put on as the beast just falls off.
	 */
	private static void strip(ServerPlayer player, boolean tearing) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty()) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			player.setItemSlot(slot, ItemStack.EMPTY);
			if (tearing && stack.isDamageableItem()) {
				int damage = stack.getDamageValue() + Math.max(1, Math.round(stack.getMaxDamage() * ARMOUR_TEAR));
				if (damage >= stack.getMaxDamage()) {
					player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
					continue;
				}
				stack.setDamageValue(damage);
			}
			player.drop(stack, true, Prediction.SERVER_ONLY);
		}
	}

	/** Themselves again ({@code sick}: after a night as the beast). */
	private static void end(ServerPlayer player, boolean sick) {
		Form f = FORMS.remove(player.getUUID());
		if (f == null) {
			return;
		}
		if (f.beast != null) {
			f.beast.discard();
		}
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getModifier(HEALTH) != null) {
			health.removeModifier(HEALTH);
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
		player.removeEffect(MobEffects.INVISIBILITY);
		if (sick && player.level() instanceof ServerLevel level) {
			// (0.7.66) after the first moon they come to in a den of their own, dug beneath where the night left them
			Lycans.Curse curse = player.getAttached(Lycans.CURSE);
			if (f.forced && curse != null && curse.moons() == 1 && Lairs.den(level, player)) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.werewolf.den").withStyle(ChatFormatting.DARK_RED));
			}
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, AFTER_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, AFTER_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0));
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.5), player.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
		}
	}
}
