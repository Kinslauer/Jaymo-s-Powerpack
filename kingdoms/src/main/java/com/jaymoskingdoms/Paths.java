package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Paths within a class (fifth edition's subclasses). Each class a player holds, first or second, asks them to pick one path once
 * it is learned, on the paths screen (the H menu's Feats and Paths tab); the pick is for keeps, until a Writ of Rebirth remakes the
 * character. Every class has two or three, and each path does something the class's own pick (a patron, an oath, a foe, an element)
 * does not. A second class's path works at {@link Classes#SECOND_SHARE} of its strength ({@link #strength}).
 * <ul>
 * <li><b>Thief</b>: <i>assassin</i> hits an unaware foe {@link #ASSASSIN} harder; <i>burglar</i> opens locks with wider sweet spots and
 * less strain, and notices more; <i>swashbuckler</i> is quicker afoot and with the blade, and breaks off without a free blow.</li>
 * <li><b>Warlord</b>: <i>champion</i> lands a heavy blow ({@link #CHAMPION_CRIT}x) {@link #CHAMPION_ODDS} of the time and has more health;
 * <i>battlemaster</i>'s orders carry {@link #ORDERS} farther and a blow sometimes trips the foe.</li>
 * <li><b>Mage</b> (a school): <i>evocation</i> casts {@link #EVOCATION} harder; <i>abjuration</i> wards itself with absorption on each cast and
 * takes less from spells; <i>divination</i> pays less mana and notices more.</li>
 * <li><b>Brute</b>: <i>berserker</i> hits harder when below half health; <i>juggernaut</i> in two heavy pieces takes less and is hard to move;
 * <i>wrestler</i> hits unarmed harder, shoves surer and slows what it hits.</li>
 * <li><b>Marksman</b>: <i>sharpshooter</i> shoots far harder past {@link #SHARP_RANGE} blocks; <i>gunslinger</i> reloads quicker and shoots close harder;
 * <i>trapper</i>'s shots slow the foe.</li>
 * <li><b>Sorcerer</b> (a metamagic): <i>quickened</i> imbues again sooner; <i>enduring</i> imbues for longer; <i>warded</i> takes less from magic, blasts and fire.</li>
 * <li><b>Rider</b>: <i>lancer</i> hits harder from the saddle; <i>outrider</i>'s mount is faster and never hurt by a fall; <i>horse archer</i>
 * shoots harder from the saddle.</li>
 * <li><b>Cleric</b>: <i>shepherd</i> mends friends near at each short rest; <i>exorcist</i> hits the undead harder; <i>warden</i> has a turning more and
 * takes less with the symbol in hand.</li>
 * <li><b>Druid</b>: <i>moon</i> has a shape more and holds it longer; <i>land</i> mends on natural ground.</li>
 * <li><b>Monk</b>: <i>open hand</i> hits and shoves harder with bare hands; <i>shadow</i> is hard to see in the dim, moves sneaking faster, and hits the
 * unaware harder.</li>
 * <li><b>Paladin</b>: <i>vanguard</i> hits a hale foe harder; <i>guardian</i>'s friends near take less and save better.</li>
 * <li><b>Ranger</b>: <i>hunter</i> shoots farther targets harder; <i>beast master</i> makes the tamed beasts near stronger and tougher;
 * <i>wayfarer</i> is quicker afoot and lands softer.</li>
 * <li><b>Warlock</b>: <i>blade</i> hits harder with a sword and drinks back some of the blow; <i>tome</i> has a pact slot more; <i>chain</i> has the
 * debts fall due {@link #CHAIN_DAYS} days later.</li>
 * <li><b>Tinkerer</b>: <i>alchemist</i> makes good draughts last {@link #ALCHEMIST}x as long; <i>battle smith</i> keeps an infusion more and hits harder.</li>
 * </ul>
 */
public final class Paths {
	// ---- tuning: each is the whole effect for the first class; the second class gets a share ----
	static final float ASSASSIN = 0.6F, CHAMPION_CRIT = 1.75F, CHAMPION_ODDS = 0.12F, TRIP_ODDS = 0.15F, ORDERS = 0.5F;
	static final double CHAMPION_HEALTH = 4.0;
	static final int TRIP_TICKS = 40;
	static final float EVOCATION = 0.2F, ABJURATION_LEFT = 0.2F, DIVINATION_MANA = 0.15F;
	static final int ABJURATION_TICKS = 20 * 20;
	static final float BERSERKER = 0.3F, JUGGERNAUT_LEFT = 0.15F, WRESTLER = 0.4F, WRESTLER_SHOVE = 0.25F;
	static final double JUGGERNAUT_KNOCKBACK = 0.4;
	static final float SHARP = 0.3F, SHARP_RANGE = 20.0F, GUNSLINGER_CLOSE = 0.1F, GUNSLINGER_RANGE = 10.0F, GUNSLINGER_LOAD = 0.25F;
	static final int TRAPPER_TICKS = 60;
	static final float QUICKENED = 0.4F, ENDURING = 0.8F, WARDED_LEFT = 0.2F;
	static final float LANCER = 0.25F, ARCHER_SADDLE = 0.25F;
	static final double OUTRIDER_SPEED = 0.1;
	static final float SHEPHERD_HEAL = 6.0F, EXORCIST = 0.35F, WARDEN_LEFT = 0.12F;
	static final double SHEPHERD_RANGE = 8.0;
	static final float MOON_TICKS = 0.5F, LAND_MEND = 1.0F;
	static final int LAND_EVERY = 100;
	static final float OPEN_HAND = 0.3F, OPEN_HAND_SHOVE = 0.2F, SHADOW_DIM = 0.4F, SHADOW_UNAWARE = 0.4F;
	static final double SHADOW_SNEAK = 0.3;
	static final float VANGUARD = 0.25F, GUARDIAN_LEFT = 0.15F, GUARDIAN_SAVES = 0.1F;
	static final double GUARDIAN_RANGE = 10.0;
	static final float HUNTER = 0.2F, HUNTER_RANGE = 12.0F;
	static final double BEAST_RANGE = 16.0, WAYFARER_SPEED = 0.08, WAYFARER_FALL = -0.3;
	static final int BEAST_EVERY = 40, BEAST_TICKS = 100;
	static final float BLADE = 0.15F, BLADE_DRINK = 0.15F;
	static final int CHAIN_DAYS = 2;
	static final float ALCHEMIST = 1.4F, SMITH = 0.1F;
	static final int ALCHEMIST_FROM = 600;
	static final float BURGLAR_LOCK = 0.4F, BURGLAR_STRAIN = 0.3F, BURGLAR_NOTICE = 0.15F, DIVINATION_NOTICE = 0.2F;
	static final float SWASHBUCKLER_SPEED = 0.06F, SWASHBUCKLER_ATTACK = 0.1F;
	static final int PICK_REMIND_EVERY = 20 * 30;

	/** The paths of each class, by key. */
	private static final Map<PlayerClass, List<String>> PATHS = new EnumMap<>(PlayerClass.class);

	static {
		PATHS.put(PlayerClass.THIEF, List.of("assassin", "burglar", "swashbuckler"));
		PATHS.put(PlayerClass.WARLORD, List.of("champion", "battlemaster"));
		PATHS.put(PlayerClass.MAGE, List.of("evocation", "abjuration", "divination"));
		PATHS.put(PlayerClass.BRUTE, List.of("berserker", "juggernaut", "wrestler"));
		PATHS.put(PlayerClass.MARKSMAN, List.of("sharpshooter", "gunslinger", "trapper"));
		PATHS.put(PlayerClass.SORCERER, List.of("quickened", "enduring", "warded"));
		PATHS.put(PlayerClass.RIDER, List.of("lancer", "outrider", "horse_archer"));
		PATHS.put(PlayerClass.CLERIC, List.of("shepherd", "exorcist", "warden"));
		PATHS.put(PlayerClass.DRUID, List.of("moon", "land"));
		PATHS.put(PlayerClass.MONK, List.of("open_hand", "shadow"));
		PATHS.put(PlayerClass.PALADIN, List.of("vanguard", "guardian"));
		PATHS.put(PlayerClass.RANGER, List.of("hunter", "beast_master", "wayfarer"));
		PATHS.put(PlayerClass.WARLOCK, List.of("blade", "tome", "chain"));
		PATHS.put(PlayerClass.TINKERER, List.of("alchemist", "battle_smith"));
	}

	/** The picks, as "class=path" keys. Saved, kept through death, synced to its own player. */
	public static final AttachmentType<List<String>> CHOSEN = AttachmentRegistry.create(JaymosKingdoms.id("paths"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** Player → server: take path {@code index} of class {@code cls} (its ordinal). */
	public record Pick(int cls, int index) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosKingdoms.id("path_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.cls);
				buf.writeVarInt(p.index);
			}, buf -> new Pick(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Integer> TOLD = new HashMap<>();
	/** The mount each outrider's speed is on. */
	private static final Map<UUID, LivingEntity> MOUNTS = new HashMap<>();

	private Paths() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> context.server().execute(() -> pick(context.player(), payload)));
	}

	static void clear() {
		TOLD.clear();
		MOUNTS.clear();
	}

	static void unmake(ServerPlayer player) {
		player.removeAttached(CHOSEN);
		TOLD.remove(player.getUUID());
		clearMount(player);
	}

	/** The path keys {@code cls} offers. */
	public static List<String> of(PlayerClass cls) {
		return PATHS.getOrDefault(cls, List.of());
	}

	/** The path {@code player} picked for {@code cls}, or "". */
	public static String chosen(Player player, PlayerClass cls) {
		String prefix = cls.key() + "=";
		for (String s : player.getAttachedOrElse(CHOSEN, List.<String>of())) {
			if (s.startsWith(prefix)) {
				return s.substring(prefix.length());
			}
		}
		return "";
	}

	/** The classes {@code player} holds that have no path yet. */
	public static List<PlayerClass> owed(Player player) {
		List<PlayerClass> out = new ArrayList<>();
		for (PlayerClass cls : Classes.both(player)) {
			if (chosen(player, cls).isEmpty()) {
				out.add(cls);
			}
		}
		return out;
	}

	private static void pick(ServerPlayer player, Pick pick) {
		PlayerClass[] all = PlayerClass.values();
		if (pick.cls() < 0 || pick.cls() >= all.length) {
			return;
		}
		PlayerClass cls = all[pick.cls()];
		List<String> paths = of(cls);
		if (pick.index() < 0 || pick.index() >= paths.size() || !Classes.both(player).contains(cls) || !chosen(player, cls).isEmpty()) {
			return;
		}
		List<String> next = new ArrayList<>(player.getAttachedOrElse(CHOSEN, List.<String>of()));
		next.add(cls.key() + "=" + paths.get(pick.index()));
		player.setAttached(CHOSEN, List.copyOf(next));
		Classes.refresh(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.path.taken", Component.translatable("path.jaymoskingdoms." + cls.key() + "." + paths.get(pick.index())))
			.withStyle(net.minecraft.ChatFormatting.GOLD));
	}

	/** How much of {@code path} {@code player} has: 1 as the first class's, a share as the second's, 0 if not their path. */
	static float strength(Player player, PlayerClass cls, String path) {
		return chosen(player, cls).equals(path) ? Classes.share(player, cls) : 0.0F;
	}

	// ---- attributes and the tick ----------------------------------------------------------------------------------

	static void attributes(ServerPlayer player) {
		double move = WAYFARER_SPEED * strength(player, PlayerClass.RANGER, "wayfarer") + SWASHBUCKLER_SPEED * strength(player, PlayerClass.THIEF, "swashbuckler");
		double attack = SWASHBUCKLER_ATTACK * strength(player, PlayerClass.THIEF, "swashbuckler");
		double knock = JUGGERNAUT_KNOCKBACK * strength(player, PlayerClass.BRUTE, "juggernaut");
		double fall = WAYFARER_FALL * strength(player, PlayerClass.RANGER, "wayfarer");
		double sneak = SHADOW_SNEAK * strength(player, PlayerClass.MONK, "shadow");
		double health = CHAMPION_HEALTH * strength(player, PlayerClass.WARLORD, "champion");
		float before = player.getMaxHealth();
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "path_move", move, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.ATTACK_SPEED, "path_attack", attack, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "path_knockback", knock, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, "path_fall", fall, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.SNEAKING_SPEED, "path_sneak", sneak, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.MAX_HEALTH, "path_health", health, AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth() || before > player.getMaxHealth()) {
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
	}

	private static void clearMount(ServerPlayer player) {
		LivingEntity was = MOUNTS.remove(player.getUUID());
		if (was != null) {
			AttributeInstance speed = was.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(JaymosKingdoms.id("path_outrider"));
			}
		}
	}

	/** Once a second or so: the owed pick is mentioned; beast masters' beasts, the land's mending, the outrider's mount. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % 20 == 0 && player.tickCount > 100) {
			int owed = owed(player).size();
			if (owed > 0 && TOLD.getOrDefault(player.getUUID(), 0) < owed && player.tickCount % PICK_REMIND_EVERY < 20) {
				TOLD.put(player.getUUID(), owed);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.path.owed").withStyle(net.minecraft.ChatFormatting.GOLD));
			}
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		float beast = strength(player, PlayerClass.RANGER, "beast_master");
		if (beast > 0.0F && player.tickCount % BEAST_EVERY == 0) {
			for (TamableAnimal pet : level.getEntitiesOfClass(TamableAnimal.class, player.getBoundingBox().inflate(BEAST_RANGE), t -> t.isTame() && t.isOwnedBy(player))) {
				pet.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BEAST_TICKS, 0, true, false, false));
				pet.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BEAST_TICKS, 0, true, false, false));
			}
		}
		float land = strength(player, PlayerClass.DRUID, "land");
		if (land > 0.0F && player.tickCount % LAND_EVERY == 0 && player.getHealth() < player.getMaxHealth() && !Downed.isDowned(player)) {
			BlockPos under = player.blockPosition().below();
			var state = level.getBlockState(under);
			if (state.is(BlockTags.DIRT) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
				player.heal(LAND_MEND * land);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY(0.5), player.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (strength(player, PlayerClass.RIDER, "outrider") > 0.0F) {
			LivingEntity mount = player.getVehicle() instanceof LivingEntity m && m.getControllingPassenger() == player ? m : null;
			if (mount != MOUNTS.get(player.getUUID())) {
				clearMount(player);
				if (mount != null) {
					MOUNTS.put(player.getUUID(), mount);
					AttributeInstance speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
					if (speed != null) {
						speed.addOrUpdateTransientModifier(new AttributeModifier(JaymosKingdoms.id("path_outrider"),
							OUTRIDER_SPEED * strength(player, PlayerClass.RIDER, "outrider"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
					}
				}
			}
		}
	}

	// ---- the blow -------------------------------------------------------------------------------------------------

	private static float more(float amount, float bonus, float strength) {
		return amount * (1.0F + bonus * strength);
	}

	private static float less(float amount, float left, float strength) {
		return amount * (1.0F - left * strength);
	}

	private static int heavyPieces(ServerPlayer player) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (player.getItemBySlot(slot).is(ClassPerks.HEAVY_ARMOR)) {
				n++;
			}
		}
		return n;
	}

	/** What the paths do to a blow, on either side of it. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer hurt) {
			amount = taken(hurt, source, amount);
		}
		if (target.getControllingPassenger() instanceof ServerPlayer rider && source.is(DamageTypes.FALL) && strength(rider, PlayerClass.RIDER, "outrider") >= 1.0F) {
			return 0.0F;
		}
		if (!(source.getEntity() instanceof ServerPlayer sp) || sp == target) {
			return amount;
		}
		var direct = source.getDirectEntity();
		if (direct instanceof Projectile shot) {
			return shot.getOwner() == sp ? shot(sp, target, shot, amount) : amount;
		}
		return direct == sp ? melee(sp, target, amount) : amount;
	}

	private static float taken(ServerPlayer hurt, DamageSource source, float amount) {
		boolean magic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
		amount = less(amount, ABJURATION_LEFT, magic ? strength(hurt, PlayerClass.MAGE, "abjuration") : 0.0F);
		float warded = strength(hurt, PlayerClass.SORCERER, "warded");
		if (warded > 0.0F && (magic || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE))) {
			amount = less(amount, WARDED_LEFT, warded);
		}
		if (strength(hurt, PlayerClass.BRUTE, "juggernaut") > 0.0F && heavyPieces(hurt) >= 2) {
			amount = less(amount, JUGGERNAUT_LEFT, strength(hurt, PlayerClass.BRUTE, "juggernaut"));
		}
		if (strength(hurt, PlayerClass.CLERIC, "warden") > 0.0F && (Clerics.isSymbol(hurt.getMainHandItem()) || Clerics.isSymbol(hurt.getOffhandItem()))) {
			amount = less(amount, WARDEN_LEFT, strength(hurt, PlayerClass.CLERIC, "warden"));
		}
		if (hurt.level() instanceof ServerLevel level) {
			float best = 0.0F;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(hurt) <= GUARDIAN_RANGE * GUARDIAN_RANGE) {
					best = Math.max(best, strength(p, PlayerClass.PALADIN, "guardian"));
				}
			}
			amount = less(amount, GUARDIAN_LEFT, best);
		}
		return amount;
	}

	private static float melee(ServerPlayer sp, LivingEntity target, float amount) {
		if (Senses.unaware(target, sp)) {
			amount = more(amount, ASSASSIN, strength(sp, PlayerClass.THIEF, "assassin"));
			amount = more(amount, SHADOW_UNAWARE, strength(sp, PlayerClass.MONK, "shadow"));
		}
		float champion = strength(sp, PlayerClass.WARLORD, "champion");
		if (champion > 0.0F && sp.getRandom().nextFloat() < CHAMPION_ODDS) {
			amount = more(amount, CHAMPION_CRIT - 1.0F, champion);
			if (sp.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			}
		}
		if (strength(sp, PlayerClass.WARLORD, "battlemaster") > 0.0F && sp.getRandom().nextFloat() < TRIP_ODDS * strength(sp, PlayerClass.WARLORD, "battlemaster")) {
			PowersCompat.trip(target, sp, TRIP_TICKS);
		}
		if (sp.getHealth() < sp.getMaxHealth() * 0.5F) {
			amount = more(amount, BERSERKER, strength(sp, PlayerClass.BRUTE, "berserker"));
		}
		boolean bare = sp.getMainHandItem().isEmpty();
		if (bare) {
			amount = more(amount, WRESTLER, strength(sp, PlayerClass.BRUTE, "wrestler"));
			amount = more(amount, OPEN_HAND, strength(sp, PlayerClass.MONK, "open_hand"));
			if (strength(sp, PlayerClass.BRUTE, "wrestler") > 0.0F) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), sp);
			}
		}
		if (sp.isPassenger()) {
			amount = more(amount, LANCER, strength(sp, PlayerClass.RIDER, "lancer"));
		}
		if (target.is(EntityTypeTags.UNDEAD)) {
			amount = more(amount, EXORCIST, strength(sp, PlayerClass.CLERIC, "exorcist"));
		}
		if (target.getHealth() >= target.getMaxHealth()) {
			amount = more(amount, VANGUARD, strength(sp, PlayerClass.PALADIN, "vanguard"));
		}
		float blade = strength(sp, PlayerClass.WARLOCK, "blade");
		if (blade > 0.0F && sp.getMainHandItem().is(ItemTags.SWORDS)) {
			amount = more(amount, BLADE, blade);
			sp.heal(amount * BLADE_DRINK * blade);
		}
		return more(amount, SMITH, strength(sp, PlayerClass.TINKERER, "battle_smith"));
	}

	private static float shot(ServerPlayer sp, LivingEntity target, Projectile shot, float amount) {
		float range = (float) sp.distanceTo(target);
		if (range > SHARP_RANGE) {
			amount = more(amount, SHARP, strength(sp, PlayerClass.MARKSMAN, "sharpshooter"));
		}
		if (range < GUNSLINGER_RANGE) {
			amount = more(amount, GUNSLINGER_CLOSE, strength(sp, PlayerClass.MARKSMAN, "gunslinger"));
		}
		if (range > HUNTER_RANGE) {
			amount = more(amount, HUNTER, strength(sp, PlayerClass.RANGER, "hunter"));
		}
		if (sp.isPassenger()) {
			amount = more(amount, ARCHER_SADDLE, strength(sp, PlayerClass.RIDER, "horse_archer"));
		}
		if (strength(sp, PlayerClass.MARKSMAN, "trapper") > 0.0F) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TRAPPER_TICKS, 1), sp);
		}
		return amount;
	}

	// ---- the hooks other systems ask -------------------------------------------------------------------------------

	/** Divination and burglars notice more. */
	static float noticing(ServerPlayer player) {
		return DIVINATION_NOTICE * strength(player, PlayerClass.MAGE, "divination") + BURGLAR_NOTICE * strength(player, PlayerClass.THIEF, "burglar");
	}

	/** A shadow Monk is seen from less far in the dim. */
	static double sight(ServerPlayer player, double light) {
		float shadow = strength(player, PlayerClass.MONK, "shadow");
		return light < 0.5 ? 1.0 - SHADOW_DIM * shadow : 1.0;
	}

	/** Swashbucklers and shadow Monks break off with no free blow. */
	static boolean slipsAway(LivingEntity mover) {
		return mover instanceof ServerPlayer p && (strength(p, PlayerClass.THIEF, "swashbuckler") > 0.0F || strength(p, PlayerClass.MONK, "shadow") > 0.0F);
	}

	static float shoveOdds(ServerPlayer player) {
		return WRESTLER_SHOVE * strength(player, PlayerClass.BRUTE, "wrestler") + OPEN_HAND_SHOVE * strength(player, PlayerClass.MONK, "open_hand");
	}

	/** A guardian's friends near (and the guardian) save better. */
	static float guardSaves(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level) || who instanceof Enemy) {
			return 0.0F;
		}
		float best = 0.0F;
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(who) <= GUARDIAN_RANGE * GUARDIAN_RANGE) {
				best = Math.max(best, strength(p, PlayerClass.PALADIN, "guardian"));
			}
		}
		return GUARDIAN_SAVES * best;
	}

	/** A short rest: a shepherd mends the friends near. */
	static void rested(ServerPlayer player) {
		float shepherd = strength(player, PlayerClass.CLERIC, "shepherd");
		if (shepherd <= 0.0F) {
			return;
		}
		ServerLevel level = player.level();
		for (LivingEntity who : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SHEPHERD_RANGE),
			e -> e.isAlive() && (e instanceof Player || e instanceof Villager) && !(e instanceof Enemy))) {
			who.heal(SHEPHERD_HEAL * shepherd);
			level.sendParticles(ParticleTypes.HEART, who.getX(), who.getY(1.2), who.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
		}
	}

	static int extraChannels(Player player) {
		return strength(player, PlayerClass.CLERIC, "warden") > 0.0F ? 1 : 0;
	}

	static int extraShapes(Player player) {
		return strength(player, PlayerClass.DRUID, "moon") > 0.0F ? 1 : 0;
	}

	static int extraSlots(Player player) {
		return strength(player, PlayerClass.WARLOCK, "tome") > 0.0F ? 1 : 0;
	}

	static int extraInfusions(Player player) {
		return strength(player, PlayerClass.TINKERER, "battle_smith") > 0.0F ? 1 : 0;
	}

	static int shapeTicks(Player player, int base) {
		return Math.round(base * (1.0F + MOON_TICKS * strength(player, PlayerClass.DRUID, "moon")));
	}

	static int imbueTicks(Player player, int base) {
		return Math.round(base * (1.0F + ENDURING * strength(player, PlayerClass.SORCERER, "enduring")));
	}

	static int imbueCooldown(Player player, int base) {
		return Math.round(base * (1.0F - QUICKENED * strength(player, PlayerClass.SORCERER, "quickened")));
	}

	static int debtDays(Player player, int base) {
		return strength(player, PlayerClass.WARLOCK, "chain") > 0.0F ? base + CHAIN_DAYS : base;
	}

	/** An evoker's staff and focus hit harder. */
	static float spellPower(Player player) {
		return 1.0F + EVOCATION * strength(player, PlayerClass.MAGE, "evocation");
	}

	static float manaFactor(Player player) {
		return 1.0F - DIVINATION_MANA * strength(player, PlayerClass.MAGE, "divination");
	}

	static float lockWidth(Player player) {
		return 1.0F + BURGLAR_LOCK * strength(player, PlayerClass.THIEF, "burglar");
	}

	static float lockStrain(Player player) {
		return 1.0F - BURGLAR_STRAIN * strength(player, PlayerClass.THIEF, "burglar");
	}

	static float orders(Player player) {
		return 1.0F + ORDERS * strength(player, PlayerClass.WARLORD, "battlemaster");
	}

	/** A gunslinger's crossbow loads in less. */
	public static int loading(LivingEntity who, int ticks) {
		return who instanceof ServerPlayer p ? Math.max(1, Math.round(ticks * (1.0F - GUNSLINGER_LOAD * strength(p, PlayerClass.MARKSMAN, "gunslinger")))) : ticks;
	}

	/** An abjurer wards itself with each cast. */
	static void cast(ServerPlayer player, ServerLevel level) {
		if (strength(player, PlayerClass.MAGE, "abjuration") > 0.0F) {
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ABJURATION_TICKS, 0, true, true, true));
			Fx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.2F, 0x9AD0FF);
		}
	}

	/** A good draught drunk by an alchemist lasts longer. */
	static MobEffectInstance draught(LivingEntity who, MobEffectInstance effect) {
		if (!(who instanceof ServerPlayer p) || effect.getDuration() < ALCHEMIST_FROM || !effect.getEffect().value().isBeneficial()) {
			return effect;
		}
		float alchemist = strength(p, PlayerClass.TINKERER, "alchemist");
		if (alchemist <= 0.0F) {
			return effect;
		}
		return new MobEffectInstance(effect.getEffect(), Math.round(effect.getDuration() * (1.0F + (ALCHEMIST - 1.0F) * alchemist)), effect.getAmplifier(),
			effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}
}
