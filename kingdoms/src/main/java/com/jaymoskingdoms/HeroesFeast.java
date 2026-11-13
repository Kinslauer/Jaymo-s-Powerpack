package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Heroes' feast (fifth edition): cast looking at a table (a crafting table, or any other block with "table" in its name, a
 * cook's included). A feast is laid on it and stands for {@link #STANDS} ticks. Whoever eats anything within {@link #REACH} of it
 * is fed for a day: no fear holds them ({@link Fear}), no poison takes ({@link Poisons}, {@link Saves}) and their health is a
 * little greater. Eating is caught where all eating is ({@code DosedDishMixin}).
 */
public final class HeroesFeast {
	/** How far from the table a diner may sit (blocks), how long the feast stands, how long the blessing lasts (ticks). */
	static final double REACH = 6.0, TABLE_REACH = 6.0;
	static final long STANDS = 20L * 60 * 5, BLESSED = 24000L;
	/** The extra health: a Health Boost level (four hearts is level 1: 4 hp per level). */
	static final int BOOST = 0;
	static final int GOLD = 0xF2D98A;

	private record Table(ServerLevel level, Vec3 at, long until) {
	}

	private static final List<Table> TABLES = new ArrayList<>();
	/** The game time a body's blessing ends. */
	static final AttachmentType<Long> FED = AttachmentRegistry.create(JaymosKingdoms.id("heroes_feast"), b -> b.persistent(Codec.LONG));

	private HeroesFeast() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HeroesFeast::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TABLES.clear());
	}

	/** Whether {@code who} is fed with a feast just now. */
	public static boolean fed(LivingEntity who) {
		Long until = who.getAttached(FED);
		return until != null && who.level().getGameTime() < until;
	}

	private static boolean table(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("table");
	}

	static boolean cast(ServerLevel level, ServerPlayer caster) {
		BlockPos found = null;
		Vec3 eye = caster.getEyePosition();
		for (double d = 0.5; d <= TABLE_REACH && found == null; d += 0.5) {
			BlockPos p = BlockPos.containing(eye.add(caster.getLookAngle().scale(d)));
			if (table(level.getBlockState(p))) {
				found = p;
			}
		}
		if (found == null) {
			return false;
		}
		Vec3 top = Vec3.atBottomCenterOf(found).add(0.0, 1.05, 0.0);
		TABLES.add(new Table(level, top, level.getGameTime() + STANDS));
		// the feast itself: a few plain dishes laid out
		ItemStack[] dishes = {new ItemStack(Items.COOKED_BEEF, 2), new ItemStack(Items.BREAD, 2), new ItemStack(Items.PUMPKIN_PIE), new ItemStack(Items.BAKED_POTATO, 2),
			new ItemStack(Items.COOKED_SALMON), new ItemStack(Items.GOLDEN_CARROT)};
		for (ItemStack dish : dishes) {
			ItemEntity item = new ItemEntity(level, top.x + (level.getRandom().nextDouble() - 0.5) * 0.6, top.y, top.z + (level.getRandom().nextDouble() - 0.5) * 0.6, dish);
			item.setDeltaMovement(Vec3.ZERO);
			level.addFreshEntity(item);
		}
		Fx.pool(level, top, 2.0F, GOLD, 80);
		level.playSound(null, top.x, top.y, top.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	/** {@code who} has just finished eating {@code food}: at a feast, they are fed for a day. */
	public static void eaten(LivingEntity who, ItemStack food) {
		if (TABLES.isEmpty() || who.level().isClientSide() || !(who.level() instanceof ServerLevel level)) {
			return;
		}
		for (Table t : TABLES) {
			if (t.level() == level && level.getGameTime() < t.until() && t.at().distanceToSqr(who.position()) <= REACH * REACH) {
				who.setAttached(FED, level.getGameTime() + BLESSED);
				who.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, (int) BLESSED, BOOST, false, true, true));
				Fear.calm(who);
				who.removeEffect(MobEffects.POISON);
				level.sendParticles(Fx.mote(GOLD, 0.8F), who.getX(), who.getY(1.0), who.getZ(), 12, 0.4, 0.6, 0.4, 0.03);
				level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4F, 1.6F);
				return;
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (!TABLES.isEmpty()) {
			for (Iterator<Table> it = TABLES.iterator(); it.hasNext();) {
				Table t = it.next();
				if (t.level().getGameTime() >= t.until()) {
					it.remove();
				} else if (tick % 10 == 0) {
					t.level().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, t.at().x, t.at().y + 0.2, t.at().z, 1, 0.3, 0.0, 0.3, 0.0);
					t.level().sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.GOLDEN_CARROT), t.at().x, t.at().y + 0.4, t.at().z, 1, 0.3, 0.1, 0.3, 0.0);
				}
			}
		}
		if (tick % 20 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (fed(p) && p.hasEffect(MobEffects.POISON)) {
					p.removeEffect(MobEffects.POISON);
				}
			}
		}
	}
}
