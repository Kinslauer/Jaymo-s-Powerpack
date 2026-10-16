package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.57) Heritage, the idea of Origins remade as a step of character creation (MCA's destiny, then this, then the
 * class): where your blood comes from. Each heritage gives a point or two free in the attributes ({@link Stats}), a gift
 * and a flaw, all passive: powers stay the Powers mod's to find. Picked once, for life (an op can change it:
 * {@code /kingdom heritage set|clear|get}).
 *
 * <ul>
 * <li><b>Heartlander:</b> plain folk: two attribute points of your own choosing, no flaw.</li>
 * <li><b>Stoneborn:</b> mines faster; a little slower on foot.</li>
 * <li><b>Tideborn:</b> long breath, swims and mines well under water; (0.7.80) wet from touching water until 2 minutes
 * out of it, and hit harder while wet; hungrier once dry.</li>
 * <li><b>Sylvan:</b> lands soft, sneaks unseen; hits lighter.</li>
 * <li><b>Verdant:</b> green skin and antennae ({@link Verdants}), hunger drains slow in the sun; burns longer.</li>
 * <li><b>Emberborn:</b> (0.7.75) fire, lava and burning never hurt them, and they thrive in the heat; the cold (cold lands,
 * snow and freezing) saps their strength and speed and starves them. Embers glow under the skin of
 * their hands and in their eyes, and small flames rise off their hands (0.7.77, client EmberLook).</li>
 * <li><b>Frostborn:</b> never freezes, thrives in the cold; starves in the heat.</li>
 * <li><b>Nightborn:</b> stronger and quicker in the dark; weaker under the open sun.</li>
 * <li><b>Warrior-born:</b> starts far weaker than anyone (lighter blows, fewer hearts), and grows out of it as their
 * character level climbs; hits harder the worse they're hurt; always hungry.</li>
 * <li><b>Earthling:</b> (0.7.75) the Z Fighters' training-born race: the skill tree learns 25% faster; no gift of the
 * blood beyond that.</li>
 * </ul>
 * (0.7.75) Earthling, Warrior-born, Frostborn and Verdant are the Z Fighter lineages too: Jaymo's Powers gives anyone of
 * that blood its race (a Warrior-born's tail, a Verdant's mending and stretch punch, a Frostborn's ice prison
 * ({@link FrostPrison}) on left mouse held), Z Fighter or not. The heritage key ({@code ;}) wraps a Warrior-born's tail
 * away or lets it out.
 * (0.7.79) Every heritage but Heartlander and Warrior-born has a look of its own too, like the Emberborn's: marks on
 * the hands and forearms that glow in the dark, glowing eyes for most, a Tideborn's gills and fins (client HeritageLooks).
 * Each player carries an entity tag {@code jaymoskingdoms_heritage_<key>} for their heritage, so Jaymo's Powers can read
 * it without Kingdoms classes (a Z Fighter's lineage comes from it).
 */
public final class Heritage {
	/** Stoneborn: block break speed (a share), and walking speed lost (a share). */
	static final double STONE_MINING = 0.15, STONE_SLOW = 0.06;
	/** Tideborn: breath, swimming and mining under water; hunger out of the wet, as a factor. */
	static final double TIDE_OXYGEN = 3.0, TIDE_SWIM = 0.5, TIDE_SUBMERGED_MINING = 0.6;
	static final float TIDE_DRY_HUNGER = 1.2F;
	/**
	 * (0.7.80) Tideborn are wet from touching water (or rain) until this long out of it (2 min): then they're dry, and
	 * the hunger flaw sets in. Wet, their blows land this much harder (a share).
	 */
	static final int TIDE_WET_TICKS = 2400;
	static final double TIDE_WET_DAMAGE = 0.15;
	/** Sylvan: blocks you can fall without harm, the share off how far sneaking is seen, and the share off your blows. */
	static final double SYLVAN_SAFE_FALL = 1.5, SYLVAN_SNEAK = 0.1, SYLVAN_WEAK = 0.1;
	/** Verdant: hunger in the sun, as a factor, and how much longer fire burns (a share). */
	static final float VERDANT_SUN_HUNGER = 0.65F;
	static final double VERDANT_BURN = 0.5;
	/**
	 * Emberborn: fire never hurts them and never stays lit on them. In the cold their blows and their walking speed drop
	 * by these shares; freezing (powder snow) takes the bigger ones. Both Emberborn and Frostborn: hunger where they
	 * thrive and where they don't.
	 */
	static final double EMBER_COLD_WEAK = 0.15, EMBER_COLD_SLOW = 0.10, EMBER_FREEZING_WEAK = 0.30, EMBER_FREEZING_SLOW = 0.20;
	static final float THRIVE_HUNGER = 0.8F, WILT_HUNGER = 1.25F;
	/** A biome this hot (base temperature) or hotter is hot; this cold or colder is cold. */
	static final float HOT = 1.0F, COLD = 0.2F;
	/** Nightborn: blows and speed in the dark (shares), blows in the sun (a share off). Dark: night, or light this low. */
	static final double NIGHT_DAMAGE = 0.1, NIGHT_SPEED = 0.05, SUN_WEAK = 0.1;
	static final int DARK_LIGHT = 7;
	/** Warrior-born: below this share of their health their blows land this much harder (a share); hunger, as a factor. */
	static final float WARRIOR_WOUNDED = 0.5F;
	static final double WARRIOR_FURY = 0.15;
	static final float WARRIOR_HUNGER = 1.2F;
	/** Earthling: skill experience, as a factor. */
	static final float EARTHLING_XP = 1.25F;
	/** The tag Jaymo's Powers reads to wrap a Warrior-born's tail away or let it out (it takes the tag off). */
	static final String TAIL_TOGGLE_TAG = "jaymoskingdoms_tail_toggle";
	/**
	 * Warrior-born start far weaker, to balance their potential: blows this much lighter (a share) and this much less
	 * max health, both shrinking evenly to nothing by character level {@link #WARRIOR_GROWN_LEVEL}.
	 */
	static final double WARRIOR_GREEN_DAMAGE = 0.4, WARRIOR_GREEN_HEALTH = 8.0;
	static final int WARRIOR_GROWN_LEVEL = 20;
	/** The entity tag every player of a heritage carries: this, then the heritage's key. */
	public static final String TAG = "jaymoskingdoms_heritage_";
	/** How often the heritages that change with the light and the frost are looked at (ticks). */
	static final int TICK_EVERY = 20;

	/** The heritages: free points to spend as you like, an icon, and the attributes each gives a free point in (one entry a point). */
	public enum Kind {
		HEARTLANDER(2, Items.WHEAT),
		STONEBORN(0, Items.IRON_PICKAXE, Stats.Stat.GRIT, Stats.Stat.ENDURANCE),
		TIDEBORN(0, Items.NAUTILUS_SHELL, Stats.Stat.AGILITY, Stats.Stat.ENDURANCE),
		SYLVAN(0, Items.OAK_SAPLING, Stats.Stat.AGILITY, Stats.Stat.FINESSE),
		VERDANT(0, Items.MOSS_BLOCK, Stats.Stat.ENDURANCE, Stats.Stat.WITS),
		EMBERBORN(0, Items.BLAZE_POWDER, Stats.Stat.MIGHT, Stats.Stat.MIGHT),
		FROSTBORN(0, Items.SNOWBALL, Stats.Stat.GRIT, Stats.Stat.MIGHT),
		NIGHTBORN(0, Items.PHANTOM_MEMBRANE, Stats.Stat.FINESSE, Stats.Stat.WITS),
		WARRIORBORN(0, Items.IRON_SWORD, Stats.Stat.MIGHT, Stats.Stat.ENDURANCE),
		EARTHLING(0, Items.BOOK, Stats.Stat.WITS, Stats.Stat.ENDURANCE);

		/** Attribute points to spend as you like. */
		public final int freePoints;
		public final Item icon;
		public final List<Stats.Stat> bonuses;

		Kind(int freePoints, Item icon, Stats.Stat... bonuses) {
			this.freePoints = freePoints;
			this.icon = icon;
			this.bonuses = List.of(bonuses);
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Free points in {@code stat}. */
		public int bonus(Stats.Stat stat) {
			int n = 0;
			for (Stats.Stat s : this.bonuses) {
				if (s == stat) {
					n++;
				}
			}
			return n;
		}

		public static @Nullable Kind byKey(String key) {
			for (Kind k : values()) {
				if (k.key().equals(key)) {
					return k;
				}
			}
			return null;
		}

		/** What the screen lists for it: the free points, the gift, the flaw. */
		public List<Component> card() {
			List<Component> out = new ArrayList<>();
			if (this.freePoints > 0) {
				out.add(Component.translatable("heritage.jaymoskingdoms.free", this.freePoints));
			}
			for (Stats.Stat stat : Stats.Stat.values()) {
				int n = bonus(stat);
				if (n > 0) {
					out.add(Component.translatable("heritage.jaymoskingdoms.bonus", n, Component.translatable("stat.jaymoskingdoms." + stat.key())));
				}
			}
			out.add(Component.translatable("heritage.jaymoskingdoms." + key() + ".gift"));
			out.add(Component.translatable("heritage.jaymoskingdoms." + key() + ".flaw"));
			return out;
		}
	}

	/**
	 * Each player's heritage (its key). Saved, kept through death, synced to everyone (its own player's screens, and since
	 * 0.7.75 everyone's view of an Emberborn's glow, client EmberLook).
	 */
	public static final AttachmentType<String> HERITAGE = AttachmentRegistry.create(JaymosKingdoms.id("heritage"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/**
	 * (0.7.80) What every client needs for a heritage's look ({@code client/HeritageLooks}): {@link #LOOK_WET} a Tideborn
	 * who's wet, {@link #LOOK_UNSEEN} a Sylvan whose glow goes out entirely when they sneak (a Thief, or with a Stealth
	 * perk). Synced to everyone, not saved.
	 */
	public static final AttachmentType<Integer> LOOK = AttachmentRegistry.create(JaymosKingdoms.id("heritage_look"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	public static final int LOOK_WET = 1, LOOK_UNSEEN = 2;
	/** (0.7.80) A Tideborn's ticks left wet. Saved. */
	static final AttachmentType<Integer> WET = AttachmentRegistry.create(JaymosKingdoms.id("heritage_wet"), builder -> builder.persistent(Codec.INT));

	/** Server → player: pick a heritage (the screen opens; {@code forced}: it can't be shut without picking). */
	public record Open(boolean forced) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("heritage_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.forced), buf -> new Open(buf.readBoolean()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Player → server: they picked heritage {@code kind} (its ordinal). */
	public record Choose(int kind) implements CustomPacketPayload {
		public static final Type<Choose> TYPE = new Type<>(JaymosKingdoms.id("heritage_choose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.kind), buf -> new Choose(buf.readVarInt()));

		@Override
		public Type<Choose> type() {
			return TYPE;
		}
	}

	private Heritage() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Choose.TYPE, Choose.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Choose.TYPE, (payload, context) -> choose(context.player(), payload.kind()));
		ServerTickEvents.END_SERVER_TICK.register(Heritage::tick);
		// Emberborn: fire, lava, hot floors and burning never hurt
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player
			&& source.is(DamageTypeTags.IS_FIRE) && is(player, Kind.EMBERBORN)));
	}

	public static @Nullable Kind of(Player player) {
		String key = player.getAttached(HERITAGE);
		return key == null ? null : Kind.byKey(key);
	}

	public static boolean is(Player player, Kind kind) {
		return of(player) == kind;
	}

	/** Opens the heritage screen for {@code player} (if their client has this mod). */
	static void ask(ServerPlayer player, boolean forced) {
		if (ServerPlayNetworking.canSend(player, Open.TYPE)) {
			ServerPlayNetworking.send(player, new Open(forced));
		}
	}

	/** Whether the heritage screen can be put up for {@code player} (their client has this mod). */
	static boolean canAsk(ServerPlayer player) {
		return ServerPlayNetworking.canSend(player, Open.TYPE);
	}

	private static void choose(ServerPlayer player, int ordinal) {
		Kind[] all = Kind.values();
		if (ordinal < 0 || ordinal >= all.length || of(player) != null) {
			return;
		}
		set(player, all[ordinal]);
		// (character creation: the class comes next)
		if (Classes.classOf(player) == null) {
			Classes.ask(player, true);
		}
	}

	/** Makes {@code player} of heritage {@code kind}: its look (a Verdant's green), its attributes, the word of it. */
	static void set(ServerPlayer player, Kind kind) {
		Kind was = of(player);
		player.setAttached(HERITAGE, kind.key());
		tag(player, kind);
		look(player, was, kind);
		Stats.settle(player);
		Skills.refresh(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.heritage.chosen", Component.translatable("heritage.jaymoskingdoms." + kind.key()))
			.withStyle(ChatFormatting.GOLD));
	}

	/** Takes {@code player}'s heritage away (and asks again). */
	static void clear(ServerPlayer player) {
		Kind was = of(player);
		player.removeAttached(HERITAGE);
		tag(player, null);
		look(player, was, null);
		Stats.settle(player);
		Skills.refresh(player);
	}

	/** Puts the tag of heritage {@code kind} on {@code player}, and takes every other heritage's off. */
	private static void tag(ServerPlayer player, @Nullable Kind kind) {
		for (Kind k : Kind.values()) {
			if (k == kind) {
				player.addTag(TAG + k.key());
			} else {
				player.removeTag(TAG + k.key());
			}
		}
	}

	/** A Verdant is green ({@link Verdants#SHADE}, seen by everyone); leaving it behind takes the green away. */
	private static void look(ServerPlayer player, @Nullable Kind was, @Nullable Kind now) {
		if (now == Kind.VERDANT && !Verdants.is(player)) {
			player.setAttached(Verdants.SHADE, (player.getRandom().nextFloat() + player.getRandom().nextFloat()) / 2.0F);
		} else if (was == Kind.VERDANT && now != Kind.VERDANT) {
			player.removeAttached(Verdants.SHADE);
		}
	}

	// ---- effects -----------------------------------------------------------------------------------------------

	/** Puts {@code player}'s heritage on them as they stand (the attributes that don't change with the light). */
	static void attributes(ServerPlayer player) {
		Kind kind = of(player);
		if (kind != null && !player.entityTags().contains(TAG + kind.key())) {
			// (picked before the tags were kept)
			tag(player, kind);
		}
		if (kind == Kind.VERDANT && !Verdants.is(player)) {
			// (the green isn't kept through death on its own)
			look(player, null, kind);
		}
		Skills.modifier(player, Attributes.BLOCK_BREAK_SPEED, "heritage_mining", kind == Kind.STONEBORN ? STONE_MINING : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_slow", kind == Kind.STONEBORN ? -STONE_SLOW : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.OXYGEN_BONUS, "heritage_breath", kind == Kind.TIDEBORN ? TIDE_OXYGEN : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.WATER_MOVEMENT_EFFICIENCY, "heritage_swim", kind == Kind.TIDEBORN ? TIDE_SWIM : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.SUBMERGED_MINING_SPEED, "heritage_submerged", kind == Kind.TIDEBORN ? TIDE_SUBMERGED_MINING : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.SAFE_FALL_DISTANCE, "heritage_soft", kind == Kind.SYLVAN ? SYLVAN_SAFE_FALL : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_weak", kind == Kind.SYLVAN ? -SYLVAN_WEAK : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.BURNING_TIME, "heritage_burn",
			kind == Kind.VERDANT ? VERDANT_BURN : kind == Kind.EMBERBORN ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (kind != Kind.EMBERBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_chill", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_chill", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Kind.NIGHTBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_night", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_night", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Kind.WARRIORBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_fury", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Kind.TIDEBORN) {
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_tide", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		green(player, kind);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Kind kind = of(player);
			look(player, kind);
			if (kind == Kind.NIGHTBORN) {
				// stronger and quicker in the dark, weaker under the open sun
				double damage = 0.0, speed = 0.0;
				if (dark(player)) {
					damage = NIGHT_DAMAGE;
					speed = NIGHT_SPEED;
				} else if (inSun(player)) {
					damage = -SUN_WEAK;
				}
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_night", damage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_night", speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else if (kind == Kind.WARRIORBORN) {
				// the worse they're hurt, the harder they fight
				boolean wounded = player.getHealth() < player.getMaxHealth() * WARRIOR_WOUNDED;
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_fury", wounded ? WARRIOR_FURY : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				green(player, kind);
			} else if (kind == Kind.EMBERBORN) {
				// flame won't take on them; the cold saps them, freezing worst of all
				if (player.isOnFire()) {
					player.clearFire();
				}
				double weak = 0.0, slow = 0.0;
				if (player.getTicksFrozen() > 0) {
					weak = EMBER_FREEZING_WEAK;
					slow = EMBER_FREEZING_SLOW;
				} else if (chilled(player)) {
					weak = EMBER_COLD_WEAK;
					slow = EMBER_COLD_SLOW;
				}
				Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_chill", -weak, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_chill", -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			} else if (kind == Kind.FROSTBORN && player.getTicksFrozen() > 0) {
				player.setTicksFrozen(0);
			}
		}
	}

	/**
	 * (0.7.80) A Tideborn gets wet touching water (or rain) and dries {@link #TIDE_WET_TICKS} after; wet, they hit harder.
	 * A Sylvan who's a Thief or has a Stealth perk can't be seen glowing while sneaking. Both go on {@link #LOOK}.
	 */
	private static void look(ServerPlayer player, @Nullable Kind kind) {
		int look = 0;
		if (kind == Kind.TIDEBORN) {
			int left = player.isInWaterOrRain() ? TIDE_WET_TICKS : Math.max(0, player.getAttachedOrElse(WET, 0) - TICK_EVERY);
			player.setAttached(WET, left);
			boolean wet = left > 0;
			Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_tide", wet ? TIDE_WET_DAMAGE : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			look = wet ? LOOK_WET : 0;
		} else if (kind == Kind.SYLVAN && unseen(player)) {
			look = LOOK_UNSEEN;
		}
		if (player.getAttachedOrElse(LOOK, 0) != look) {
			if (look == 0) {
				player.removeAttached(LOOK);
			} else {
				player.setAttached(LOOK, look);
			}
		}
	}

	/** A Thief, or anyone who's learned a Stealth perk. */
	static boolean unseen(ServerPlayer player) {
		if (Classes.is(player, Classes.PlayerClass.THIEF)) {
			return true;
		}
		Skills.Progress progress = Skills.of(player);
		for (Skills.Perk perk : Skills.Perk.values()) {
			if (perk.pillar == Skills.Pillar.STEALTH && progress.rank(perk) > 0) {
				return true;
			}
		}
		return false;
	}

	/** A Tideborn who's still wet (touched water within {@link #TIDE_WET_TICKS}). */
	static boolean wet(ServerPlayer player) {
		return player.isInWaterOrRain() || player.getAttachedOrElse(WET, 0) > 0;
	}

	/** How much of a Warrior-born's early weakness is left, 1 (none grown) to 0 (character level {@link #WARRIOR_GROWN_LEVEL}). */
	static double greenness(ServerPlayer player) {
		return Math.clamp(1.0 - Stats.characterLevel(Skills.of(player)) / (double)WARRIOR_GROWN_LEVEL, 0.0, 1.0);
	}

	/** A Warrior-born's early weakness, as it stands now (taken off everyone else). */
	private static void green(ServerPlayer player, @Nullable Kind kind) {
		double left = kind == Kind.WARRIORBORN ? greenness(player) : 0.0;
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_green", -WARRIOR_GREEN_DAMAGE * left, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// whole hearts, so the bar doesn't flicker by halves as they grow
		Skills.modifier(player, Attributes.MAX_HEALTH, "heritage_green_health", -2.0 * Math.ceil(WARRIOR_GREEN_HEALTH / 2.0 * left),
			AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Night where they stand, or a dark place. */
	static boolean dark(ServerPlayer player) {
		Level level = player.level();
		BlockPos pos = player.blockPosition();
		return level.getMaxLocalRawBrightness(pos) <= DARK_LIGHT || level.isDarkOutside() && level.canSeeSky(pos);
	}

	/** Day, under the open sky, no rain. */
	static boolean inSun(ServerPlayer player) {
		Level level = player.level();
		BlockPos pos = player.blockPosition().above();
		return level.isBrightOutside() && level.canSeeSky(pos) && !level.isRainingAt(pos);
	}

	/** The factor on {@code player}'s hunger drain from their heritage, where they are now. */
	static float exhaustion(ServerPlayer player) {
		Kind kind = of(player);
		if (kind == null) {
			return 1.0F;
		}
		return switch (kind) {
			case TIDEBORN -> wet(player) ? 1.0F : TIDE_DRY_HUNGER;
			case VERDANT -> inSun(player) ? VERDANT_SUN_HUNGER : 1.0F;
			case EMBERBORN -> hot(player) ? THRIVE_HUNGER : chilled(player) ? WILT_HUNGER : 1.0F;
			case FROSTBORN -> cold(player) ? THRIVE_HUNGER : hot(player) ? WILT_HUNGER : 1.0F;
			case WARRIORBORN -> WARRIOR_HUNGER;
			default -> 1.0F;
		};
	}

	private static float temperature(ServerPlayer player) {
		return player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
	}

	static boolean hot(ServerPlayer player) {
		return temperature(player) >= HOT;
	}

	static boolean cold(ServerPlayer player) {
		return temperature(player) <= COLD;
	}

	/** In the cold, for an Emberborn: a cold land (snow, ice, the frozen peaks), or freezing. The pack has no seasons, so this is its winter. */
	static boolean chilled(ServerPlayer player) {
		return cold(player) || player.getTicksFrozen() > 0;
	}

	/** Earthling: the factor on skill experience. */
	static float xpFactor(Player player) {
		return is(player, Kind.EARTHLING) ? EARTHLING_XP : 1.0F;
	}

	/** The heritage key: a Warrior-born's tail (a Frostborn's ice prison is their left mouse held, through Jaymo's Powers). */
	static void gift(ServerPlayer player) {
		if (of(player) == Kind.WARRIORBORN) {
			player.addTag(TAIL_TOGGLE_TAG);
		}
	}

	/** Sylvan: the share off how far sneaking is seen. */
	static double sneak(Player player) {
		return is(player, Kind.SYLVAN) ? SYLVAN_SNEAK : 0.0;
	}

	// ---- for ops ------------------------------------------------------------------------------------------------

	/** {@code /kingdom heritage set|clear|get}, for ops. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		var players = Commands.argument("players", EntityArgument.players());
		for (Kind kind : Kind.values()) {
			players.then(Commands.literal(kind.key()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					set(p, kind);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.heritage.set", count,
					Component.translatable("heritage.jaymoskingdoms." + kind.key())), true);
				return n;
			}));
		}
		return Commands.literal("heritage").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("set").then(players))
			.then(Commands.literal("clear").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
				int n = 0;
				for (ServerPlayer p : EntityArgument.getPlayers(c, "players")) {
					clear(p);
					ask(p, true);
					n++;
				}
				int count = n;
				c.getSource().sendSuccess(() -> Component.translatable("command.jaymoskingdoms.heritage.cleared", count), true);
				return n;
			})))
			.then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
				ServerPlayer p = EntityArgument.getPlayer(c, "player");
				Kind kind = of(p);
				c.getSource().sendSuccess(() -> kind == null ? Component.translatable("command.jaymoskingdoms.heritage.none", p.getDisplayName())
					: Component.translatable("command.jaymoskingdoms.heritage.is", p.getDisplayName(), Component.translatable("heritage.jaymoskingdoms." + kind.key())), false);
				return 1;
			})));
	}
}
