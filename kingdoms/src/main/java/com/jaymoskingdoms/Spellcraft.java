package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.0, stage 2) The casting classes' magic ({@link Classes}).
 *
 * <p><b>The Mage's staff:</b> a wizard's staff in a Mage's hand casts (right-click) the spell on it, one of those they
 * know ({@link Classes.Chosen#spells}); sneak and right-click turns to the next. A Mage casts harder than a scroll
 * ({@link #MAGE_POWER}) and each spell has its own cooldown. A Mage who reads a scroll learns its spell instead of
 * spending it ({@link #learn}); a scroll of a spell they know is read as anyone's.
 *
 * <p><b>The Sorcerer's Imbue:</b> on the {@code J} key a Sorcerer charges the blade in their hand with their element
 * (picked with the class: water, fire, earth, air, lightning or ice; 0.7.212 restoration; 0.7.229 death, {@link Necromancy}, and
 * blood, {@link Hemomancy}) for a while; every blow it lands carries the
 * element ({@link #strike}), and the blade trails its colour. Sneak and {@code J} throws a bolt of the element at what
 * they're looking at instead, on a short cooldown: few spells, quick ones.
 *
 * <p>(0.7.49) The element lives on the blade itself ({@link Charge}, in the stack's custom data), not on the Sorcerer:
 * dropped, handed over or picked up from a corpse, it keeps burning in anyone's hand until it wears off. So a Sorcerer
 * keeps one imbued blade at a time; imbuing another takes the element out of the last one, wherever it is.
 */
public final class Spellcraft {
	/** A Mage's spells hit this much harder (and last longer) than a scroll's. */
	static final float MAGE_POWER = 1.75F;
	/** (0.7.184) A Mage's boon, Echo: one staff cast in this many leaves no cooldown behind. */
	static final int MAGE_ECHO_ONE_IN = 4;
	/** A Mage's cooldown for each spell (ticks). */
	static final Map<String, Integer> MAGE_COOLDOWN = Map.ofEntries(Map.entry("fire", 60), Map.entry("arcane", 40), Map.entry("warding", 20 * 45),
		Map.entry("draining", 20 * 8), Map.entry("raising", 20 * 90), Map.entry("mending", 20 * 6), Map.entry("sanctuary", 20 * 60),
		Map.entry("restoration", 20 * 60 * 5),
		// (0.7.229) death's and blood's
		Map.entry("blight", 20 * 10), Map.entry("whispers", 20 * 3), Map.entry("deaths_door", 20 * 60 * 5), Map.entry("hemorrhage", 20 * 8),
		Map.entry("blood_grip", 20 * 6), Map.entry("frenzy", 20 * 60));
	/** (0.7.212) An attuned player who isn't a Mage ({@link Mana}) casts from a staff only as hard as a scroll, and waits this much longer between casts. */
	static final float ATTUNED_POWER = 1.0F, ATTUNED_COOLDOWN = 1.5F;
	/** How long an imbued blade keeps its element (ticks); the Imbue's own cooldown; the bolt's. */
	static final int IMBUE_TICKS = 20 * 120, IMBUE_COOLDOWN = 20 * 10, BOLT_COOLDOWN = 40;
	/** The bolt's reach and damage before its element. */
	static final double BOLT_REACH = 20.0;
	static final float BOLT_DAMAGE = 5.0F;
	/** The elements' numbers: fire's burn (s) and extra damage; water's slow (ticks) and the hurt to what fears water;
	 * earth's stagger (ticks); air's lift; lightning's damage, how far it chains and to how many; ice's freeze (ticks). */
	static final float FIRE_SECONDS = 4.0F, FIRE_DAMAGE = 1.0F, WATER_DAMAGE = 6.0F, LIGHTNING_DAMAGE = 3.0F;
	static final int WATER_SLOW = 60, EARTH_STAGGER = 40, ICE_FREEZE = 300, LIGHTNING_CHAIN = 2;
	/** (0.6.8) One blow in this many bursts with the element; the rest only spark. A thrown bolt always does. */
	static final int PROC_ONE_IN = 20;
	/** (0.6.8) Air instead lifts on every this-many-th blow, so the next one lands in the air: an air combo. */
	static final int AIR_EVERY = 2;
	/** (0.6.8) How long a lightning burst blinds, in ticks (0.3 s). */
	static final int LIGHTNING_BLIND = 6;
	static final double AIR_LIFT = 0.9, LIGHTNING_RANGE = 4.0;
	/** (0.7.60) Fire's burst catches up to {@code FIRE_SPREAD} more creatures within {@code FIRE_SPREAD_RANGE} (players
	 * spared), each lit for {@link #FIRE_SECONDS} and burnt for {@link #FIRE_DAMAGE}. */
	static final int FIRE_SPREAD = 3;
	static final double FIRE_SPREAD_RANGE = 4.0;
	/** (0.7.60) Ice's burst: its damage and slow (ticks), and the chill on creatures within {@code ICE_CHILL_RANGE} (players
	 * spared): their freeze and slow (ticks). */
	static final float ICE_DAMAGE = 2.0F;
	static final int ICE_SLOW = 80, ICE_CHILL_FREEZE = 140, ICE_CHILL_SLOW = 60, ICE_CHILL_COUNT = 4;
	static final double ICE_CHILL_RANGE = 3.0;

	/** Player → server: the Imbue key ({@code bolt}: with sneak, the bolt). */
	public record Imbue(boolean bolt) implements CustomPacketPayload {
		public static final Type<Imbue> TYPE = new Type<>(JaymosKingdoms.id("imbue"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Imbue> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.bolt), buf -> new Imbue(buf.readBoolean()));

		@Override
		public Type<Imbue> type() {
			return TYPE;
		}
	}

	/** The custom data key an imbued blade keeps its {@link Charge} under. */
	static final String CHARGE_KEY = "jaymoskingdoms_imbue";
	/** How often (ticks) a player's whole inventory is checked for blades whose element has gone out. */
	static final int SWEEP_EVERY = 20;

	/** An imbued blade's element: whose it is, until when, and which of their imbues ({@code stamp}, the game time of it). */
	public record Charge(UUID sorcerer, String name, String element, long until, long stamp) {
	}

	/** Sorcerer → their one imbued blade (its stamp) and when it wears off. A blade whose stamp isn't here has gone cold. */
	private record Current(long stamp, long until) {
	}

	private static final Map<UUID, Current> IMBUED = new HashMap<>();
	/** Blows landed since an air blade's last lift, by player. */
	private static final Map<UUID, Integer> AIR_BLOWS = new HashMap<>();

	private Spellcraft() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Imbue.TYPE, Imbue.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Imbue.TYPE, (payload, context) -> imbue(context.player(), payload.bolt()));
		Mana.register();
		Restoration.register();
		Necromancy.register();
		Hemomancy.register();
		// the Mage's staff (0.7.212: and an attuned caster's)
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!Arcana.isStaff(stack) || !staffCaster(player)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && world instanceof ServerLevel level) {
				if (sp.isShiftKeyDown()) {
					turn(sp);
				} else {
					castStaff(sp, level, stack);
				}
			}
			return InteractionResult.SUCCESS;
		});
		// an imbued blade's blows
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			// whoever swings it: the element is the blade's, not the hand's
			if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && entity != attacker
				&& damageTaken > 0.0F && !blocked && entity.level() instanceof ServerLevel level) {
				String element = live(attacker.getMainHandItem(), level.getGameTime());
				if (element == null) {
					return;
				}
				// (0.7.229) a death blade blights every blow it lands, a blood blade cuts
				if (element.equals("death")) {
					Necromancy.bladeBlow(entity);
				} else if (element.equals("blood")) {
					Hemomancy.bladeBlow(entity);
				}
				boolean burst;
				if (element.equals("air")) {
					int blows = AIR_BLOWS.merge(attacker.getUUID(), 1, Integer::sum);
					burst = blows % AIR_EVERY == 0;
				} else {
					burst = attacker.getRandom().nextInt(PROC_ONE_IN) == 0;
				}
				if (burst) {
					strike(level, attacker, entity, element, false);
				} else {
					level.sendParticles(particle(element), entity.getX(), entity.getY(0.6), entity.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
				}
			}
		});
	}

	static void clear() {
		IMBUED.clear();
		AIR_BLOWS.clear();
	}

	// ---- the Mage -----------------------------------------------------------------------------------------------

	/**
	 * (0.7.212) Who casts from a staff: a Mage, or anyone attuned ({@link Mana}) but a Sorcerer, whose magic is their
	 * {@code J}.
	 */
	static boolean staffCaster(Player player) {
		return Classes.is(player, PlayerClass.MAGE) || Mana.attuned(player) && !Classes.is(player, PlayerClass.SORCERER);
	}

	/**
	 * A Mage (0.7.212: or another staff caster) reading a scroll of a spell they don't know: they learn it. False for anyone
	 * else, or a spell they know.
	 */
	static boolean learn(ServerPlayer player, String spell) {
		Classes.Chosen c = Classes.of(player);
		if (!staffCaster(player) || c.spells().contains(spell)) {
			return false;
		}
		List<String> spells = new ArrayList<>(c.spells());
		spells.add(spell);
		player.setAttached(Classes.CLASS, new Classes.Chosen(c.cls(), c.option(), List.copyOf(spells), c.selected()));
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.4, 0.5, 0.4, 0.8);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spell.learned", spellName(spell)).withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	static Component spellName(String spell) {
		return Component.translatable("class.jaymoskingdoms.option." + spell);
	}

	/** Turns the staff to the next spell the Mage knows. */
	private static void turn(ServerPlayer player) {
		Classes.Chosen c = Classes.of(player);
		if (c.spells().size() < 2) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spell.only", spellName(c.spell())));
			return;
		}
		Classes.Chosen next = new Classes.Chosen(c.cls(), c.option(), c.spells(), Math.floorMod(c.selected() + 1, c.spells().size()));
		player.setAttached(Classes.CLASS, next);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spell.turned", spellName(next.spell())));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static void castStaff(ServerPlayer player, ServerLevel level, ItemStack staff) {
		String spell = Classes.of(player).spell();
		if (spell.isEmpty()) {
			return;
		}
		if (player.getCooldowns().isOnCooldown(staff)) {
			return;
		}
		// (0.7.212) mana first: a Mage casts harder than anyone else attuned
		boolean mage = Classes.is(player, PlayerClass.MAGE);
		// (0.7.229) blood spells are paid in blood, not mana
		boolean blood = ForbiddenArts.blood(spell);
		float cost = blood ? Hemomancy.cost(spell) : Mana.cost(spell);
		if (!blood && !Mana.afford(player, cost) || blood && player.getHealth() - cost < Hemomancy.MIN_LEFT && !player.isCreative()) {
			if (blood) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood.too_little").withStyle(ChatFormatting.DARK_RED));
			}
			return;
		}
		if (!Arcana.cast(spell, level, player, (mage ? MAGE_POWER : ATTUNED_POWER) * (blood ? Hemomancy.power(player) : 1.0F))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return;
		}
		if (blood) {
			Hemomancy.pay(player, cost);
		} else {
			Mana.take(player, cost);
		}
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 16, 0.3, 0.4, 0.3, 0.5);
		if (mage && player.getRandom().nextInt(MAGE_ECHO_ONE_IN) == 0) {
			// the spell echoes: no cooldown this time
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.4, 0.5, 0.4, 0.05);
		} else {
			int cooldown = MAGE_COOLDOWN.getOrDefault(spell, 40);
			player.getCooldowns().addCooldown(staff, mage ? cooldown : Math.round(cooldown * ATTUNED_COOLDOWN));
		}
		afterCast(player, level, spell);
	}

	// ---- the Sorcerer ---------------------------------------------------------------------------------------------

	/** The charge a stack carries, read as it is (it may have worn off or gone cold): null for none. */
	public static @Nullable Charge charge(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return data.copyTag().getCompound(CHARGE_KEY).flatMap(t -> t.getString("sorcerer").map(id -> {
			try {
				return new Charge(UUID.fromString(id), t.getStringOr("name", ""), t.getStringOr("element", ""),
					t.getLongOr("until", 0L), t.getLongOr("stamp", 0L));
			} catch (IllegalArgumentException e) {
				return null;
			}
		})).orElse(null);
	}

	/** The element still burning in a stack at {@code now}, or null: worn off, or its Sorcerer has imbued another since. */
	static @Nullable String live(ItemStack stack, long now) {
		Charge c = charge(stack);
		if (c == null || c.until() <= now || c.element().isEmpty()) {
			return null;
		}
		Current current = IMBUED.get(c.sorcerer());
		// no entry: the server restarted since (or it ran out); the blade's own clock decides
		return current == null || current.stamp() == c.stamp() ? c.element() : null;
	}

	private static void write(ItemStack stack, Charge c) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag t = new CompoundTag();
			t.putString("sorcerer", c.sorcerer().toString());
			t.putString("name", c.name());
			t.putString("element", c.element());
			t.putLong("until", c.until());
			t.putLong("stamp", c.stamp());
			tag.put(CHARGE_KEY, t);
		});
	}

	private static void strip(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(CHARGE_KEY));
	}

	/** Takes a dead charge off a stack; true if there was one. */
	private static boolean stripIfDead(ItemStack stack, long now) {
		if (charge(stack) != null && live(stack, now) == null) {
			strip(stack);
			return true;
		}
		return false;
	}

	/** The Sorcerer's element, or "" for anyone else. */
	static String element(ServerPlayer player) {
		Classes.Chosen c = Classes.of(player);
		return c.playerClass() == PlayerClass.SORCERER ? c.option() : "";
	}

	/** A blade the Imbue takes: a sword, or anything the classes count as a blade. */
	private static boolean blade(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ClassPerks.LIGHT_BLADES) || stack.is(ClassPerks.TWO_HANDED);
	}

	private static void imbue(ServerPlayer player, boolean bolt) {
		String element = element(player);
		if (element.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (element.equals("restoration")) {
			// (0.7.212) the healer's keys: Mend, and Renewal or Rekindle; their blade never takes a charge
			Restoration.key(player, level, bolt);
			return;
		}
		if (bolt) {
			// (0.7.229) death and blood have keys of their own first: Reap or Raise; Grip or Hemorrhage
			if (element.equals("death") && Necromancy.key(player, level) || element.equals("blood") && Hemomancy.key(player, level)) {
				return;
			}
			bolt(level, player, element);
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!blade(held)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.imbue.no_blade"));
			return;
		}
		if (player.getCooldowns().isOnCooldown(Arcana.WIZARD_STAFF.getDefaultInstance())) {
			return;
		}
		if (!pay(player, element, "imbue")) {
			return;
		}
		long now = level.getGameTime();
		Charge had = charge(held);
		boolean sameBlade = had != null && had.sorcerer().equals(player.getUUID()) && live(held, now) != null;
		Current before = IMBUED.get(player.getUUID());
		// one blade at a time: this one takes the element, the last one goes cold wherever it is
		IMBUED.put(player.getUUID(), new Current(now, now + IMBUE_TICKS));
		write(held, new Charge(player.getUUID(), player.getGameProfile().name(), element, now + IMBUE_TICKS, now));
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			stripIfDead(inv.getItem(i), now);
		}
		player.getCooldowns().addCooldown(Arcana.WIZARD_STAFF.getDefaultInstance(), IMBUE_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.sendParticles(particle(element), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound(element), SoundSource.PLAYERS, 0.8F, 1.1F);
		boolean moved = !sameBlade && before != null && before.until() > now;
		player.sendOverlayMessage(Component.translatable(moved ? "message.jaymoskingdoms.imbue.moved" : "message.jaymoskingdoms.imbue.done", spellName(element)));
		afterCast(player, level, element);
	}

	/** A bolt of the element at what the Sorcerer is looking at. */
	private static void bolt(ServerLevel level, ServerPlayer player, String element) {
		ItemStack key = Arcana.BONE_STAFF.getDefaultInstance();
		if (player.getCooldowns().isOnCooldown(key)) {
			return;
		}
		LivingEntity target = Arcana.sighted(player);
		if (target == null || player.distanceTo(target) > BOLT_REACH) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return;
		}
		if (!pay(player, element, "bolt")) {
			return;
		}
		player.getCooldowns().addCooldown(key, BOLT_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 start = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 step = target.getBoundingBox().getCenter().subtract(start);
		int n = Math.max(2, (int) (step.length() * 3.0));
		ParticleOptions trail = particle(element);
		for (int i = 0; i <= n; i++) {
			Vec3 p = start.add(step.scale(i / (double) n));
			level.sendParticles(trail, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound(element), SoundSource.PLAYERS, 1.0F, 1.3F);
		target.hurtServer(level, level.damageSources().indirectMagic(player, player), BOLT_DAMAGE);
		strike(level, player, target, element, true);
		afterCast(player, level, element);
	}

	/** (0.7.229) A Sorcerer's key paid for: in mana, or (blood) in blood ({@link Hemomancy#pay}). */
	private static boolean pay(ServerPlayer player, String element, String what) {
		return element.equals("blood") ? Hemomancy.pay(player, Hemomancy.cost(what)) : Mana.spend(player, Mana.cost(what));
	}

	/** (0.7.229) After a cast: sorcery where a realm frowns on it; death and blood magic are forbidden everywhere ({@link ForbiddenArts}). */
	static void afterCast(ServerPlayer player, ServerLevel level, String spellOrElement) {
		if (ForbiddenArts.forbidden(spellOrElement)) {
			ForbiddenArts.cast(player, level, ForbiddenArts.blood(spellOrElement), 0);
		} else {
			Theft.sorcery(player, level, player.blockPosition());
		}
	}

	/** {@code element} bursting on a blow {@code player} landed on {@code target} ({@code bolt}: thrown, not struck). */
	static void strike(ServerLevel level, LivingEntity player, LivingEntity target, String element, boolean bolt) {
		Vec3 away = target.position().subtract(player.position()).normalize();
		switch (element) {
			case "fire" -> {
				target.igniteForSeconds(FIRE_SECONDS);
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), FIRE_DAMAGE);
				// (0.7.60) the flames leap to what's near
				int caught = 0;
				for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(FIRE_SPREAD_RANGE),
					e -> e != target && e != player && e.isAlive() && !(e instanceof ServerPlayer) && !e.fireImmune())) {
					other.igniteForSeconds(FIRE_SECONDS);
					other.hurtServer(level, level.damageSources().indirectMagic(player, player), FIRE_DAMAGE);
					level.sendParticles(ParticleTypes.FLAME, other.getX(), other.getY(0.6), other.getZ(), 8, 0.2, 0.3, 0.2, 0.03);
					if (++caught >= FIRE_SPREAD) {
						break;
					}
				}
			}
			case "water" -> {
				target.clearFire();
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, WATER_SLOW, 1), player);
				if (target.isSensitiveToWater() || target.fireImmune()) {
					target.hurtServer(level, level.damageSources().indirectMagic(player, player), WATER_DAMAGE);
				}
			}
			case "earth" -> {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EARTH_STAGGER, 2), player);
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EARTH_STAGGER, 0), player);
				target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.6, -0.4, away.z * 0.6));
				target.syncVelocity = true;
			}
			case "air" -> {
				target.setDeltaMovement(target.getDeltaMovement().add(away.x * 0.3, AIR_LIFT, away.z * 0.3));
				target.syncVelocity = true;
			}
			case "lightning" -> {
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), LIGHTNING_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, LIGHTNING_BLIND, 0), player);
				LightningBolt flash = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (flash != null) {
					flash.setVisualOnly(true);
					flash.setPos(target.getX(), target.getY(), target.getZ());
					level.addFreshEntity(flash);
				}
				int chained = 0;
				for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(LIGHTNING_RANGE),
					e -> e != target && e != player && e.isAlive() && !(e instanceof ServerPlayer))) {
					other.hurtServer(level, level.damageSources().indirectMagic(player, player), LIGHTNING_DAMAGE);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, other.getX(), other.getY(0.6), other.getZ(), 10, 0.2, 0.3, 0.2, 0.2);
					if (++chained >= LIGHTNING_CHAIN) {
						break;
					}
				}
			}
			case "ice" -> {
				// (0.7.60) frozen through (long enough to take the cold's own damage), and a bite of it now
				target.setTicksFrozen(Math.max(target.getTicksFrozen(), ICE_FREEZE));
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ICE_SLOW, 2), player);
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), ICE_DAMAGE);
				// and a chill on what's near
				int chilled = 0;
				for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(ICE_CHILL_RANGE),
					e -> e != target && e != player && e.isAlive() && !(e instanceof ServerPlayer))) {
					other.setTicksFrozen(Math.max(other.getTicksFrozen(), ICE_CHILL_FREEZE));
					other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ICE_CHILL_SLOW, 0), player);
					level.sendParticles(ParticleTypes.SNOWFLAKE, other.getX(), other.getY(0.6), other.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
					if (++chilled >= ICE_CHILL_COUNT) {
						break;
					}
				}
			}
			case "death" -> Necromancy.burst(level, player, target);
			case "blood" -> Hemomancy.burst(level, player, target);
			default -> {
				return;
			}
		}
		level.sendParticles(particle(element), target.getX(), target.getY(0.6), target.getZ(), bolt ? 16 : 8, 0.3, 0.3, 0.3, 0.05);
	}

	/** The element's look. */
	static ParticleOptions particle(String element) {
		return switch (element) {
			case "fire" -> ParticleTypes.FLAME;
			case "water" -> ParticleTypes.SPLASH;
			case "earth" -> new DustParticleOptions(0x8B6A3C, 1.2F);
			case "air" -> ParticleTypes.CLOUD;
			case "lightning" -> ParticleTypes.ELECTRIC_SPARK;
			case "ice" -> ParticleTypes.SNOWFLAKE;
			case "restoration" -> Restoration.GOLD_MOTE;
			case "death" -> Necromancy.VIOLET_MOTE;
			case "blood" -> Hemomancy.BLOOD_MOTE;
			default -> ParticleTypes.ENCHANT;
		};
	}

	static SoundEvent sound(String element) {
		return switch (element) {
			case "fire" -> SoundEvents.BLAZE_SHOOT;
			case "water" -> SoundEvents.GENERIC_SPLASH;
			case "earth" -> SoundEvents.ANVIL_LAND;
			case "air" -> SoundEvents.BREEZE_SHOOT;
			case "lightning" -> SoundEvents.LIGHTNING_BOLT_IMPACT;
			case "ice" -> SoundEvents.GLASS_BREAK;
			case "restoration" -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case "death" -> SoundEvents.WITHER_SHOOT;
			case "blood" -> SoundEvents.WARDEN_HEARTBEAT;
			default -> SoundEvents.ILLUSIONER_CAST_SPELL;
		};
	}

	/** The imbued blade's trail in anyone's hand; dead charges taken off; the Sorcerer told when theirs wears off. */
	static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Current mine = IMBUED.get(player.getUUID());
		if (mine != null && mine.until() <= now) {
			IMBUED.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.imbue.faded"));
		}
		ItemStack held = player.getMainHandItem();
		stripIfDead(held, now);
		if (now % SWEEP_EVERY == 0L) {
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				stripIfDead(inv.getItem(i), now);
			}
			stripIfDead(player.containerMenu.getCarried(), now);
		}
		String element = live(held, now);
		if (element != null && now % 4L == 0L && player.level() instanceof ServerLevel level) {
			Vec3 hand = player.position().add(player.getLookAngle().scale(0.6)).add(0.0, 1.0, 0.0);
			// (0.7.212) a blade's quiet trail is for the attuned to see
			Mana.sendSeen(level, particle(element), hand.x, hand.y, hand.z, 1, 0.15, 0.2, 0.15, 0.01);
		}
	}

	/** The element of an imbued blade someone holds (or, a player, carries), for a HUD or a look; null for none. */
	public static @Nullable String imbuedElement(Entity entity) {
		if (!(entity instanceof LivingEntity living)) {
			return null;
		}
		long now = living.level().getGameTime();
		String element = live(living.getMainHandItem(), now);
		if (element == null) {
			element = live(living.getOffhandItem(), now);
		}
		if (element == null && living instanceof ServerPlayer player) {
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize() && element == null; i++) {
				element = live(inv.getItem(i), now);
			}
		}
		return element;
	}
}
