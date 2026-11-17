package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.oldage.OldAltarBlock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-17): a vampire "should be affected by the negative patron effects when near any holy items or patron
 * specific interaction". Every patron's holy things sting the blood; the Lantern Warden's light and the Truth's word
 * ({@link #WORST}) sting worst. Once a second ({@link #EVERY}) a vampire player (not a dhampir, who has none of the
 * weaknesses) feels the strongest holy thing near them, its dread (0-1):
 * <ul>
 * <li>a holy symbol or a patron's weapon held by anyone within {@link #HELD_RANGE} (in their own hand: all of it);</li>
 * <li>a Cleric or Paladin player, or a village priest, within {@link #PERSON_RANGE};</li>
 * <li>a shrine or an old altar within {@link #BLOCK_RANGE};</li>
 * <li>a patron's temple, inside its walls or within {@link #TEMPLE_PAST} of them.</li>
 * </ul>
 * Any dread weakens them as the sun does (Weakness, a weaker leap: {@link #hallowed}, {@link BloodLeap#weak}) and they
 * smoke; from {@link #BURN_AT} the blood burns ({@link #BURN_DAMAGE}). Staying near, the wrath ladder finds them
 * ({@link PatronWrath}): omens, then lightning near, then a smiting. A patron's own rite done by a vampire (swearing,
 * a prayer answered, an offering, a boon) burns them at once ({@link #rite}).
 */
public final class HolyDread {
	static final int EVERY = 20;
	/** How far a held holy thing and a holy person reach (blocks); a shrine or altar's reach (blocks, each way); a temple's past its walls. */
	static final double HELD_RANGE = 8.0, PERSON_RANGE = 8.0;
	static final int BLOCK_RANGE = 6, TEMPLE_PAST = 6, TEMPLE_HEIGHT = 16;
	/** How strongly each kind is felt, before the patron's weight. */
	static final float HELD = 1.0F, PERSON = 0.6F, SHRINE = 0.8F, TEMPLE = 1.0F;
	/** The patron's weight: the Lantern Warden and the Truth; anyone else's (and a plain shrine, a priest). */
	static final float WORST = 1.0F, OTHERS = 0.6F;
	/** Dread from which the blood burns, and the burn's harm at full dread, each second. */
	static final float BURN_AT = 0.5F, BURN_DAMAGE = 3.0F;
	/** Seconds of dread before omens, lightning near, the smiting; the chance each second of each. */
	static final int OMEN_AFTER = 10, BOLT_AFTER = 30, SMITE_AFTER = 60;
	static final float OMEN_CHANCE = 0.15F, BOLT_CHANCE = 0.05F, SMITE_CHANCE = 0.03F;
	/** A rite done by a vampire: the harm (times the patron's weight) and the fire (seconds). */
	static final float RITE_DAMAGE = 6.0F, RITE_FIRE = 2.0F;
	/** How long a second's dread keeps them weak (ticks), so the leap stays weak between checks. */
	static final int HALLOW_TICKS = 30;

	/** Powers' patron weapons, by item path, and whose they are. */
	private static final Map<String, String> WEAPONS = Map.of("hearthguard", "hearthkeeper", "anvil_fathers_hammer", "anvil_father",
		"tidecaller", "deep_mother", "huntsmans_spear", "green_huntsman", "gravelight", "lantern_warden", "sunbrand", "crowned_sun",
		"ashfang", "ash_whisperer", "plain_word", "truth");

	/** Until when each vampire is hallowed (game time); how many seconds running they've felt it. */
	private static final Map<UUID, Long> HALLOWED = new HashMap<>();
	private static final Map<UUID, Integer> LINGER = new HashMap<>();

	private HolyDread() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HolyDread::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HALLOWED.clear();
			LINGER.clear();
		});
	}

	/** Near something holy within the last {@link #HALLOW_TICKS}. */
	static boolean hallowed(ServerPlayer player) {
		Long until = HALLOWED.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	private static boolean feels(ServerPlayer player) {
		return Vampires.isVampire(player) && !VampirePlayers.dhampir(player) && player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	static float weight(@Nullable Patron patron) {
		return patron == Patron.LANTERN_WARDEN || patron == Patron.TRUTH ? WORST : OTHERS;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!feels(player)) {
				LINGER.remove(player.getUUID());
				continue;
			}
			float dread = dread(server, player);
			if (dread <= 0.0F) {
				LINGER.remove(player.getUUID());
				continue;
			}
			suffer(player, dread);
		}
	}

	/** The strongest holy thing near {@code player} (0-1). */
	static float dread(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		float most = 0.0F;
		double reach = Math.max(HELD_RANGE, PERSON_RANGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), LivingEntity::isAlive)) {
			double d = e == player ? 0.0 : e.distanceTo(player);
			@Nullable Patron theirs = e instanceof Player p ? Patrons.of(p) : null;
			for (ItemStack stack : new ItemStack[] {e.getMainHandItem(), e.getOffhandItem()}) {
				float w = holyItem(stack, theirs);
				if (w > 0.0F && d <= HELD_RANGE) {
					most = Math.max(most, HELD * w * close(d, HELD_RANGE));
				}
			}
			if (e != player && d <= PERSON_RANGE) {
				if (e instanceof Player p && (Classes.is(p, PlayerClass.CLERIC) || Classes.is(p, PlayerClass.PALADIN))) {
					most = Math.max(most, PERSON * weight(theirs) * close(d, PERSON_RANGE));
				} else if (Accusations.priest(e)) {
					most = Math.max(most, PERSON * OTHERS * close(d, PERSON_RANGE));
				}
			}
		}
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-BLOCK_RANGE, -BLOCK_RANGE, -BLOCK_RANGE), at.offset(BLOCK_RANGE, BLOCK_RANGE, BLOCK_RANGE))) {
			BlockState state = level.getBlockState(pos);
			Patron patron;
			if (state.is(Healing.SHRINE)) {
				patron = state.getValue(Shrine.DEDICATION).patron();
			} else if (state.getBlock() instanceof OldAltarBlock) {
				patron = Patron.values()[Math.min(state.getValue(OldAltarBlock.PATRON), Patron.values().length - 1)];
			} else {
				continue;
			}
			double d = Math.sqrt(net.minecraft.world.phys.Vec3.atCenterOf(pos).distanceToSqr(player.position()));
			most = Math.max(most, SHRINE * weight(patron) * close(d, BLOCK_RANGE + 1.0));
		}
		String world = level.dimension().identifier().toString();
		for (Temples.Temple t : Temples.all(server)) {
			Patron patron = t.patronOf();
			if (patron == null || !t.world().equals(world) || Math.abs(t.center().getY() - at.getY()) > TEMPLE_HEIGHT) {
				continue;
			}
			int r = TempleBuildings.radius(patron);
			int out = Math.max(Math.abs(t.center().getX() - at.getX()), Math.abs(t.center().getZ() - at.getZ())) - r;
			if (out <= TEMPLE_PAST) {
				most = Math.max(most, TEMPLE * weight(patron) * (out <= 0 ? 1.0F : close(out, TEMPLE_PAST + 1.0)));
			}
		}
		return Math.min(most, 1.0F);
	}

	private static float close(double d, double range) {
		return (float)Math.max(0.0, 1.0 - d / range);
	}

	/** The weight of {@code stack} as a holy thing (0: it isn't one); a holy symbol is its holder's patron's. */
	private static float holyItem(ItemStack stack, @Nullable Patron holders) {
		if (stack.isEmpty()) {
			return 0.0F;
		}
		if (stack.is(Clerics.HOLY_SYMBOL)) {
			return weight(holders);
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String patron = "jaymospowers".equals(id.getNamespace()) ? WEAPONS.get(id.getPath()) : null;
		return patron == null ? 0.0F : weight(Patron.byKey(patron));
	}

	private static void suffer(ServerPlayer player, float dread) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		HALLOWED.put(player.getUUID(), now + HALLOW_TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(0.6), player.getZ(), 3 + (int)(dread * 8), 0.3, 0.5, 0.3, 0.01);
		if (dread >= BURN_AT) {
			player.hurtServer(level, level.damageSources().magic(), BURN_DAMAGE * dread);
			level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.8F);
			PowersCompat.shake(player, 0.2F * dread);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.holy.sears").withStyle(ChatFormatting.DARK_RED));
		}
		int linger = LINGER.merge(player.getUUID(), 1, Integer::sum);
		var random = level.getRandom();
		if (linger >= SMITE_AFTER && random.nextFloat() < SMITE_CHANCE) {
			PatronWrath.smite(level, player, random);
			LINGER.put(player.getUUID(), 0);
		} else if (linger >= BOLT_AFTER && random.nextFloat() < BOLT_CHANCE) {
			PatronWrath.bolt(level, player, random, false);
		} else if (linger >= OMEN_AFTER && random.nextFloat() < OMEN_CHANCE) {
			PatronWrath.omen(player, level, random);
		}
	}

	/** {@code player} did one of {@code patron}'s rites (swore, prayed, offered, called a boon): a vampire burns for it. */
	static void rite(ServerPlayer player, @Nullable Patron patron) {
		if (!feels(player)) {
			return;
		}
		ServerLevel level = player.level();
		player.hurtServer(level, level.damageSources().magic(), RITE_DAMAGE * weight(patron));
		player.igniteForSeconds(RITE_FIRE);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY(0.6), player.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.holy.rite").withStyle(ChatFormatting.DARK_RED));
		PatronWrath.omen(player, level, level.getRandom());
	}
}
