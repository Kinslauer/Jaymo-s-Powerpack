package com.jaymoskingdoms.champions;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.PatronChampion;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.518) The Inquisitor's moves: Chain of Truth (dragged to him), Strip the Lie (blessings gone, the unseen shown), Judgement
 * (a column on one player; a harm split among those who stand together); in a group, zealots.
 */
final class InquisitorMoves implements Moves {
	static final int CHAIN_WINDUP = 15, CHAIN_COOLDOWN = 140, CHAIN_BLOW_TICKS = 40;
	static final double CHAIN_MIN = 4.0, CHAIN_MAX = 16.0, CHAIN_PULL = 1.4;
	static final float CHAIN_BLOW = 1.2F;
	static final int STRIP_WINDUP = 25, STRIP_COOLDOWN = 260, STRIP_GLOW = 120;
	static final double STRIP_RANGE = 10.0;
	static final int JUDGE_WINDUP = 60, JUDGE_COOLDOWN = 400;
	static final double JUDGE_RANGE = 24.0, JUDGE_SHARE_RANGE = 3.0;
	static final float JUDGE_SHARE = 0.30F;

	private @Nullable LivingEntity chained;
	private @Nullable Player marked;
	private final List<Move> moves = List.of(
		new Move("chain_of_truth", CHAIN_WINDUP, CHAIN_COOLDOWN, CHAIN_MIN, CHAIN_MAX, this::chainTell, this::chainLand),
		new Move("strip_the_lie", STRIP_WINDUP, STRIP_COOLDOWN, 0.0, STRIP_RANGE, this::stripTell, this::stripLand),
		new Move("judgement", JUDGE_WINDUP, JUDGE_COOLDOWN, 0.0, JUDGE_RANGE, this::judgeTell, this::judgeLand));

	@Override
	public List<Move> moves() {
		return this.moves;
	}

	private void chainTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == CHAIN_WINDUP) {
			this.chained = target;
		}
		LivingEntity t = this.chained;
		if (t == null) {
			return;
		}
		Vec3 from = c.getEyePosition(), to = t.getEyePosition();
		ParticleOptions dust = Fx.wisp(0x909090, 1.0F);
		int n = (int)from.distanceTo(to) * 2;
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(to.subtract(from).scale(i / (double)Math.max(1, n)));
			level.sendParticles(dust, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	private void chainLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		LivingEntity t = this.chained != null && this.chained.isAlive() ? this.chained : target;
		this.chained = null;
		if (t == null) {
			return;
		}
		MoveKit.pull(t, c.position(), CHAIN_PULL);
		c.nextBlow(CHAIN_BLOW, CHAIN_BLOW_TICKS);
		MoveKit.sound(level, c.position(), SoundEvents.CHAIN_HIT, 2.0F, 0.8F);
	}

	private void stripTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == STRIP_WINDUP) {
			MoveKit.sound(level, c.position(), SoundEvents.BELL_BLOCK, 2.0F, 0.6F);
		}
		MoveKit.ring(level, c.position(), STRIP_RANGE * (1.0 - left / (double)STRIP_WINDUP), c.patron().colour);
	}

	private void stripLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		for (LivingEntity e : c.foes(STRIP_RANGE)) {
			for (MobEffectInstance effect : new ArrayList<>(e.getActiveEffects())) {
				if (effect.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
					e.removeEffect(effect.getEffect());
				}
			}
			e.removeEffect(MobEffects.INVISIBILITY);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, STRIP_GLOW, 0));
		}
		level.sendParticles(Fx.LIGHT, c.getX(), c.getY() + 1.2, c.getZ(), 40, 1.5, 1.0, 1.5, 0.1);
		MoveKit.sound(level, c.position(), SoundEvents.BELL_BLOCK, 2.0F, 1.0F);
	}

	private void judgeTell(ServerLevel level, PatronChampion c, @Nullable LivingEntity target, int left) {
		if (left == JUDGE_WINDUP) {
			this.marked = null;
			if (target instanceof Player p) {
				this.marked = p;
			} else {
				for (LivingEntity e : c.foes(JUDGE_RANGE)) {
					if (e instanceof Player p) {
						this.marked = p;
						break;
					}
				}
			}
		}
		if (left == JUDGE_WINDUP || left == JUDGE_WINDUP * 2 / 3 || left == JUDGE_WINDUP / 3) {
			MoveKit.sound(level, c.position(), SoundEvents.BELL_BLOCK, 2.0F, 0.5F);
		}
		Player m = this.marked;
		if (m != null && m.isAlive()) {
			MoveKit.column(level, m.position(), 8.0, c.patron().colour);
			MoveKit.ring(level, m.position(), JUDGE_SHARE_RANGE, c.patron().colour);
		}
	}

	private void judgeLand(ServerLevel level, PatronChampion c, @Nullable LivingEntity target) {
		Player m = this.marked;
		this.marked = null;
		if (m == null || !m.isAlive()) {
			return;
		}
		List<Player> shared = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceTo(m) <= JUDGE_SHARE_RANGE) {
				shared.add(p);
			}
		}
		if (!shared.contains(m)) {
			shared.add(m);
		}
		float each = m.getMaxHealth() * JUDGE_SHARE / shared.size();
		for (Player p : shared) {
			p.hurtServer(level, c.damageSources().magic(), each);
			level.sendParticles(Fx.LIGHT, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 1.0, 0.4, 0.2);
		}
		for (double y = 0.0; y < 14.0; y += 0.5) {
			level.sendParticles(Fx.LIGHT, m.getX(), m.getY() + y, m.getZ(), 2, 0.2, 0.0, 0.2, 0.02);
		}
		MoveKit.sound(level, m.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.8F);
	}

	@Override
	public void clear(PatronChampion c) {
		this.marked = null;
		this.chained = null;
	}

	@Override
	public void hand(ServerLevel level, PatronChampion c, int group) {
		for (int i = 0; i < MoveKit.adds(group); i++) {
			Vindicator zealot = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
			if (zealot == null) {
				continue;
			}
			// (0.7.518) EntityType.create skips finalizeSpawn, so the axe is given by hand
			zealot.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
			MoveKit.name(zealot, "zealot");
			c.spawnAdd(level, zealot);
		}
	}
}
