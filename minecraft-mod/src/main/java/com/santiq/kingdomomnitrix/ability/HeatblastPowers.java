package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.power.AlienStrength;
import com.santiq.kingdomomnitrix.power.Destruction;
import com.santiq.kingdomomnitrix.registry.ModItems;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.vfx.Fx;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Heatblast, Alien Evolution 2.0 — die acht Tasten-Kraefte. AE dient als Qualitaetslatte (Feuerball, Pyro-Strahl,
 * Feueratem, Hitzeabsorption, Feuer-Surfen, Feuertornado, Feuer-Transport, Supernova); jede Kraft spielt sich hier
 * anders und greift in die anderen:
 * <ul>
 *   <li><b>Plasmakern</b>: Tippen = schneller Ball, Halten = grosser Kern, der beim Aufprall in Splitter zerspringt.</li>
 *   <li><b>Sonnenlanze</b>: gehaltener Strahl in drei Hitzestufen (gelb → weiss → blau); schmilzt Sand zu Glas, Stein zu Magma.</li>
 *   <li><b>Drachenatem</b>: gehaltener Kegel aus dem Mund; trifft er Wasser, steigt eine blendende Dampfwolke auf.</li>
 *   <li><b>Waermesog</b>: gehalten zieht er Feuer, Lava, Glut und brennende Gegner ein — laedt den Kern, Lava gibt Glutpanzer.</li>
 *   <li><b>Flammenbrett</b>: Surfen auf einem Feuerbrett (Modell), die Spur brennt; Schleichen = Meteortritt.</li>
 *   <li><b>Feuerwirbel</b>: wandernder Tornado, hebt Gegner an; eigene Feuerbaelle und der Atem fuettern ihn (bis Stufe 3).</li>
 *   <li><b>Kometensatz</b>: Heatblast wird zur Feuerkugel, rast zum Zielpunkt und schlaegt dort ein.</li>
 *   <li><b>Supernova</b>: ab halbem Kern; voller Kern laesst zusaetzlich einen Sonnenkern zurueck, der Gegner ansaugt.</li>
 * </ul>
 * Alle Kraefte heizen die Kernhitze ({@link HeatblastAbilities}); ueberhitzt wird die naechste Kraft zu Blaufeuer.
 */
final class HeatblastPowers {
	private static final int FIRE = HeatblastAbilities.FIRE;
	private static final int BLUE = HeatblastAbilities.BLUE;
	private static final int EMBER = 0xFFB040;
	private static final int STEAM = 0xEEF4F8;
	/** Hitzestufen der Sonnenlanze */
	private static final int[] LANCE_COLORS = {0xFFC830, 0xFFF6E0, 0x5AB0FF};
	static final String BOARD = "flame_board";

	/** Kraftstufe je Spieler fuer die laufende Dauerkraft (beim ersten Tick bestimmt, damit Blaufeuer nur einmal zaehlt) */
	private static final Map<UUID, HeatblastAbilities.Power> CHANNEL = new HashMap<>();
	/** aktive Feuerwirbel je Spieler */
	private static final Map<UUID, Tornado> TORNADOES = new HashMap<>();
	/** Flammenbrett: Spieler, die gerade surfen */
	private static final Set<UUID> SURFING = new HashSet<>();

	private static final class Tornado {
		Vec3d pos;
		int stacks;
		int bonusTicks;
		final float damage;
		final double radius;
		final int color;

		Tornado(Vec3d pos, float damage, double radius, int color) {
			this.pos = pos;
			this.damage = damage;
			this.radius = radius;
			this.color = color;
		}

		void feed(ServerWorld world, int amount) {
			int before = stacks;
			stacks = Math.min(3, stacks + amount);
			bonusTicks += 20 * amount;
			if (stacks > before) {
				Fx.explosion(world, pos.add(0, 1.5, 0), color, 1.2f + stacks * 0.4f);
				world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2f, 0.5f + stacks * 0.1f);
			}
		}

		double reach() {
			return radius * (1.0 + 0.3 * stacks);
		}
	}

	private HeatblastPowers() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("fire_blast"), new PlasmaCore());
		AbilityRegistry.register(KingdomOmnitrix.id("solar_lance"), new SolarLance());
		AbilityRegistry.register(KingdomOmnitrix.id("flamethrower"), new DragonBreath());
		AbilityRegistry.register(KingdomOmnitrix.id("heat_absorb"), new HeatSiphon());
		AbilityRegistry.register(KingdomOmnitrix.id("flame_boost"), HeatblastPowers::flameBoard);
		AbilityRegistry.register(KingdomOmnitrix.id("fire_tornado"), HeatblastPowers::fireTornado);
		AbilityRegistry.register(KingdomOmnitrix.id("comet_dash"), HeatblastPowers::cometDash);
		AbilityRegistry.register(KingdomOmnitrix.id("supernova"), HeatblastPowers::supernova);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			CHANNEL.remove(id);
			TORNADOES.remove(id);
			SURFING.remove(id);
		});
	}

	/** Rueckverwandlung/Abmelden: laufende Zustaende vergessen. */
	static void forget(UUID id) {
		CHANNEL.remove(id);
		TORNADOES.remove(id);
		SURFING.remove(id);
	}

	private static HeatblastAbilities.Power channelPower(ServerPlayerEntity player) {
		return CHANNEL.computeIfAbsent(player.getUuid(), id -> HeatblastAbilities.power(player));
	}

	private static Vec3d hand(ServerPlayerEntity player) {
		Vec3d look = player.getRotationVec(1.0f);
		return player.getEyePos().add(look.multiply(0.8)).add(0, -0.35, 0);
	}

	// --- 1 Plasmakern ----------------------------------------------------------------------------

	/** Tippen: schneller Feuerball (gluehend 3, blau 5). Halten: ein grosser Kern, der beim Aufprall in Splitter zerspringt. */
	private static final class PlasmaCore implements ChargeableAbility {
		@Override
		public int chargeTicks(AbilityContext ctx) {
			return (int) ctx.param("charge_ticks", 20.0);
		}

		@Override
		public void charging(AbilityContext ctx, int ticks, float charge) {
			ServerPlayerEntity player = ctx.player();
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4, 1, false, false));
			if (ticks % 3 == 0) {
				Vec3d at = hand(player);
				// Glut stroemt in die Hand und verdichtet sich
				for (int i = 0; i < 3; i++) {
					double a = (ticks + i * 2.1) * 0.9;
					Vec3d from = at.add(Math.cos(a) * 0.9, Math.sin(a * 1.3) * 0.6, Math.sin(a) * 0.9);
					Fx.flames(ctx.world(), from, at.subtract(from).multiply(0.25), charge >= 1.0f ? 0xFFF0B0 : FIRE, 0.05f, 1);
				}
				Fx.flash(ctx.world(), at, FIRE, 0.4f + charge * 0.8f);
			}
		}

		@Override
		public boolean release(AbilityContext ctx, float charge) {
			ServerPlayerEntity player = ctx.player();
			ServerWorld world = ctx.world();
			HeatblastAbilities.Power power = HeatblastAbilities.power(player);
			float damage = (float) ctx.param("damage", 6.0) * power.factor();
			Vec3d look = player.getRotationVec(1.0f);
			if (charge < 0.35f) {
				int count = power.blue() ? 5 : HeatblastAbilities.heat(player) >= HeatblastAbilities.GLOW ? 3 : 1;
				for (int i = 0; i < count; i++) {
					HeroProjectileEntity orb = HeroProjectileEntity.shoot(world, player, ModItems.FIRE_ORB, (float) ctx.param("speed", 2.2),
							count == 1 ? 0.0f : 6.0f).withDamage(damage);
					if (power.blue()) {
						orb.withExplosion(1.2f);
					}
				}
				Fx.flames(world, hand(player), look.multiply(0.35), power.color(), 0.18f, 8);
				Fx.flash(world, hand(player), power.color(), 0.9f);
				HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 8.0));
				BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.0f, power.blue() ? 0.7f : 1.0f);
				return true;
			}
			// grosser Kern: langsamer, schwerer, sprengt und zerspringt in brennende Splitter
			float coreDamage = damage * (1.5f + charge * 1.5f);
			int splinters = (int) ctx.param("splinters", 5.0) + (power.blue() ? 3 : 0);
			int color = power.color();
			HeroProjectileEntity core = HeroProjectileEntity.shoot(world, player, ModItems.FIRE_ORB, (float) ctx.param("core_speed", 1.4), 0.0f)
					.withDamage(coreDamage).withExplosion(0.6f + charge * 0.9f)
					.withImpact(p -> shatter(world, player, p.getPos(), coreDamage * 0.35f, splinters, color));
			UUID coreId = core.getUuid();
			PowerTasks.start(player, 60, 1, (w, p, age) -> {
				if (!(w.getEntity(coreId) instanceof HeroProjectileEntity flying) || !flying.isAlive()) {
					return false;
				}
				// sichtbar groesser als ein Feuerball: Glutmantel und Funkenschweif
				Fx.flames(w, flying.getPos(), flying.getVelocity().multiply(-0.15), color, 0.25f + charge * 0.2f, 3);
				if (age % 3 == 0) {
					Fx.flash(w, flying.getPos(), color, 0.9f + charge);
				}
				return true;
			});
			Fx.explosion(world, hand(player), color, 0.6f + charge * 0.5f);
			player.addVelocity(-look.x * 0.25 * charge, 0.05, -look.z * 0.25 * charge);   // Rueckstoss
			player.velocityModified = true;
			HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 8.0) * (1.0f + charge));
			BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.3f, 0.6f);
			BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);
			return true;
		}
	}

	/** Splitter des Plasmakerns: fliegen schraeg nach oben auseinander und treffen auch hinter Deckung. */
	private static void shatter(ServerWorld world, ServerPlayerEntity player, Vec3d at, float damage, int count, int color) {
		for (int i = 0; i < count; i++) {
			double a = i * MathHelper.TAU / count + world.random.nextDouble() * 0.4;
			HeroProjectileEntity splinter = new HeroProjectileEntity(world, player);
			splinter.setItem(new net.minecraft.item.ItemStack(ModItems.FIRE_ORB));
			splinter.setPosition(at.add(Math.cos(a) * 0.4, 0.5, Math.sin(a) * 0.4));
			splinter.setVelocity(Math.cos(a), 0.55 + world.random.nextDouble() * 0.3, Math.sin(a), 0.75f, 2.0f);
			splinter.withDamage(damage).asPellet();
			world.spawnEntity(splinter);
		}
		Fx.shards(world, at, color, 0.6f, 14);
		Fx.shockwave(world, at, color, 3.0f, 8);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.9f, 1.4f);
	}

	// --- 2 Sonnenlanze ---------------------------------------------------------------------------

	/** Zweihand-Strahl, der mit der Haltedauer heisser wird; die Hitzestufe veraendert Schaden, Breite und das Gelaende. */
	private static final class SolarLance implements ChargeableAbility {
		@Override
		public boolean channel() {
			return true;
		}

		@Override
		public int chargeTicks(AbilityContext ctx) {
			return (int) ctx.param("ticks", 80.0);
		}

		@Override
		public void charging(AbilityContext ctx, int ticks, float charge) {
			ServerPlayerEntity player = ctx.player();
			ServerWorld world = ctx.world();
			HeatblastAbilities.Power power = channelPower(player);
			int stage = power.blue() ? 2 : ticks < ctx.param("stage_ticks", 25.0) ? 0 : ticks < ctx.param("stage_ticks", 25.0) * 2 ? 1 : 2;
			int color = LANCE_COLORS[stage];
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4, 2, false, false));
			double range = ctx.param("range", 18.0);
			// Ansatz eine Armlaenge vor dem Koerper: in der Ich-Sicht ueberstrahlt er sonst das ganze Bild
			Vec3d from = player.getEyePos().add(player.getRotationVec(1.0f).multiply(1.6)).add(0, -0.45, 0);
			AlienKit.Beam beam = AlienKit.beam(world, player, range);
			Vec3d to = beam.end();
			if (ticks % 2 == 0) {
				Fx.beam(world, from, to, color, 0.14f + stage * 0.09f, 3);
				Fx.flash(world, to, color, 0.6f + stage * 0.3f);
				if (stage == 2) {
					Fx.flames(world, to, Vec3d.ZERO, color, 0.3f, 3);
				}
			}
			if (ticks % 4 == 0) {
				float damage = (float) ctx.param("damage", 2.0) * (1.0f + stage * 0.75f) * power.factor();
				// Stufe 1: erstes Ziel; ab Stufe 2 durchbohrt der Strahl alles auf der Linie
				List<LivingEntity> hits = stage == 0 ? beam.target().map(List::of).orElse(List.of())
						: AlienKit.along(world, player, from, to, 0.4 + stage * 0.2);
				for (LivingEntity target : hits) {
					HeatblastAbilities.hit(player, target, damage, 2.0f + stage * 2.0f);
				}
				melt(world, player, to, stage);
			}
			if (ticks % 8 == 0) {
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.PLAYERS, 0.7f, 0.7f + stage * 0.35f);
			}
			if (ticks == (int) ctx.param("stage_ticks", 25.0) || ticks == (int) ctx.param("stage_ticks", 25.0) * 2) {
				Fx.flames(world, from, Vec3d.ZERO, color, 0.25f, 6);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.8f, 1.0f + stage * 0.3f);
			}
			if (ticks % 10 == 0) {
				HeatblastAbilities.addHeat(player, (float) ctx.param("heat_per_second", 6.0) * 0.5f);
			}
		}

		@Override
		public boolean release(AbilityContext ctx, float charge) {
			ServerPlayerEntity player = ctx.player();
			if (!CHANNEL.containsKey(player.getUuid())) {
				// nur angetippt: ein kurzer Strahlstoss
				charging(ctx, 0, 0.0f);
			}
			CHANNEL.remove(player.getUuid());
			BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.4f);
			return true;
		}
	}

	/** Gelaende reagiert auf die Hitze des Strahls: Schnee/Eis schmelzen, ab Weissglut wird Sand zu Glas, blau gluet Stein zu Magma. */
	private static void melt(ServerWorld world, ServerPlayerEntity player, Vec3d at, int stage) {
		float strength = AlienStrength.of(player) + 4.0f;
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(at).add(-1, -1, -1), BlockPos.ofFloored(at).add(1, 1, 1))) {
			if (world.random.nextFloat() > 0.35f) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			BlockState into = null;
			if (state.isOf(Blocks.SNOW) || state.isOf(Blocks.POWDER_SNOW)) {
				into = Blocks.AIR.getDefaultState();
			} else if (state.isIn(BlockTags.ICE) || state.isOf(Blocks.SNOW_BLOCK)) {
				into = Blocks.WATER.getDefaultState();
			} else if (stage >= 1 && state.isIn(BlockTags.SAND)) {
				into = Blocks.GLASS.getDefaultState();
			} else if (stage >= 2 && state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
				into = Blocks.MAGMA_BLOCK.getDefaultState();
			}
			if (into != null && Destruction.transmute(world, player, pos, into, strength)) {
				Fx.smoke(world, Vec3d.ofCenter(pos), 0x605850, 0.5f, 2);
			}
		}
		if (stage >= 1) {
			Destruction.ignite(world, player, at, 1.2, 0.15 * stage);
		}
	}

	// --- 3 Drachenatem ---------------------------------------------------------------------------

	/** Gehaltener Feuerkegel aus dem Mund; Wasser im Weg verdampft zu einer Wolke, die Gegner blendet. Fuettert Feuerwirbel. */
	private static final class DragonBreath implements ChargeableAbility {
		@Override
		public boolean channel() {
			return true;
		}

		@Override
		public int chargeTicks(AbilityContext ctx) {
			return (int) ctx.param("ticks", 60.0);
		}

		@Override
		public void charging(AbilityContext ctx, int ticks, float charge) {
			ServerPlayerEntity player = ctx.player();
			ServerWorld world = ctx.world();
			HeatblastAbilities.Power power = channelPower(player);
			int color = power.color();
			double range = ctx.param("range", 8.0) * (power.blue() ? 1.4 : 1.0);
			Vec3d look = player.getRotationVec(1.0f);
			Vec3d mouth = player.getEyePos().add(look.multiply(0.5)).add(0, -0.1, 0);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4, 1, false, false));
			Fx.flames(world, mouth, look.multiply(range / 11.0), color, 0.28f, 5);
			if (ticks % 3 == 0) {
				float damage = (float) ctx.param("damage", 1.6) * power.factor();
				for (LivingEntity target : AlienKit.cone(world, player, range, 0.8)) {
					HeatblastAbilities.hit(player, target, damage, 3.0f);
					AlienKit.push(target, player.getPos(), 0.12, 0.0);
				}
				steam(world, player, mouth, look, range);
				Tornado tornado = TORNADOES.get(player.getUuid());
				if (tornado != null && tornado.pos.add(0, 1.5, 0).subtract(mouth).normalize().dotProduct(look) > 0.85
						&& tornado.pos.distanceTo(mouth) <= range + 2.0 && ticks % 15 == 0) {
					tornado.feed(world, 1);
				}
			}
			if (ticks % 6 == 0) {
				Destruction.ignite(world, player, mouth.add(look.multiply(range * 0.7)), 1.5, 0.12);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.PLAYERS, 0.8f, 0.7f);
			}
		}

		@Override
		public boolean release(AbilityContext ctx, float charge) {
			ServerPlayerEntity player = ctx.player();
			if (!CHANNEL.containsKey(player.getUuid())) {
				charging(ctx, 0, 0.0f);
			}
			CHANNEL.remove(player.getUuid());
			HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 12.0) * Math.max(0.3f, charge));
			return true;
		}
	}

	/** Trifft der Atem Wasser, steigt eine Dampfwolke auf: blendet und verlangsamt Gegner darin. */
	private static void steam(ServerWorld world, ServerPlayerEntity player, Vec3d mouth, Vec3d look, double range) {
		BlockHitResult hit = world.raycast(new RaycastContext(mouth, mouth.add(look.multiply(range)), RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		FluidState fluid = world.getFluidState(hit.getBlockPos());
		if (!fluid.isIn(FluidTags.WATER)) {
			return;
		}
		Vec3d at = hit.getPos().add(0, 0.6, 0);
		Fx.smoke(world, at, STEAM, 2.2f, 6);
		Fx.smoke(world, at.add(0, 1.0, 0), STEAM, 1.6f, 3);
		world.playSound(null, hit.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.7f, 0.7f);
		for (LivingEntity target : AlienKit.around(world, player, at, 3.5)) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), player);
			HeatblastAbilities.hit(player, target, 1.0f, 0.0f);   // Verbruehen
		}
	}

	// --- 4 Waermesog -----------------------------------------------------------------------------

	/** Gehalten: zieht Hitzequellen der Umgebung ein (Feuer, Lava, Magma, Lagerfeuer, brennende Gegner, Feuergeschosse). */
	private static final class HeatSiphon implements ChargeableAbility {
		@Override
		public boolean channel() {
			return true;
		}

		@Override
		public int chargeTicks(AbilityContext ctx) {
			return (int) ctx.param("ticks", 60.0);
		}

		@Override
		public void charging(AbilityContext ctx, int ticks, float charge) {
			ServerPlayerEntity player = ctx.player();
			ServerWorld world = ctx.world();
			Vec3d core = player.getPos().add(0, 1.0, 0);
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4, 2, false, false));
			if (ticks % 4 != 0) {
				return;
			}
			int radius = (int) ctx.param("radius", 8.0);
			int limit = (int) ctx.param("per_pulse", 6.0);
			int sources = 0;
			int lava = 0;
			BlockPos center = player.getBlockPos();
			for (BlockPos pos : BlockPos.iterate(center.add(-radius, -3, -radius), center.add(radius, 4, radius))) {
				if (sources >= limit) {
					break;
				}
				if (pos.getSquaredDistance(center) > radius * radius) {
					continue;
				}
				BlockState state = world.getBlockState(pos);
				BlockState cooled = null;
				if (state.isIn(BlockTags.FIRE)) {
					cooled = Blocks.AIR.getDefaultState();
				} else if (state.isOf(Blocks.LAVA)) {
					cooled = state.getFluidState().isStill() ? Blocks.OBSIDIAN.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
					lava++;
				} else if (state.isOf(Blocks.MAGMA_BLOCK)) {
					cooled = Blocks.NETHERRACK.getDefaultState();
				} else if (state.getBlock() instanceof CampfireBlock && state.get(CampfireBlock.LIT)) {
					cooled = state.with(CampfireBlock.LIT, false);
				}
				if (cooled != null && player.canModifyAt(world, pos)) {
					world.setBlockState(pos.toImmutable(), cooled);
					stream(world, Vec3d.ofCenter(pos), core);
					sources++;
				}
			}
			for (LivingEntity burning : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
					e -> e.isOnFire() && e != player)) {
				burning.extinguish();
				if (AlienKit.foe(player, burning)) {
					HeatblastAbilities.hit(player, burning, (float) ctx.param("drain_damage", 2.0), 0.0f);   // Waerme wird ausgesaugt
				}
				stream(world, AlienKit.body(burning), core);
				sources++;
			}
			for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, player.getBoundingBox().expand(radius * 0.6),
					p -> p.getOwner() != player && (p.isOnFire() || p instanceof net.minecraft.entity.projectile.AbstractFireballEntity))) {
				stream(world, projectile.getPos(), core);
				projectile.discard();
				sources += 2;
			}
			float gain = 1.0f + sources * (float) ctx.param("heat_per_source", 3.0);
			HeatblastAbilities.addHeat(player, gain);
			if (sources > 0) {
				player.heal(sources * 0.5f);
			}
			if (lava > 0) {
				// Glutpanzer: jede Lavaquelle legt eine Schicht Absorption auf (hoechstens 4 Herzen extra)
				StatusEffectInstance current = player.getStatusEffect(StatusEffects.ABSORPTION);
				int level = Math.min(3, (current == null ? -1 : current.getAmplifier()) + 1);
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, level, false, false));
				Fx.aura(world, player, 0xFF5A10, 1.0f, 20);
			}
			Fx.charge(world, player, sources > 0 ? EMBER : 0xC08050, Math.min(2.6f, 1.6f + sources * 0.15f), 6);
			if (ticks % 8 == 0) {
				world.playSound(null, player.getBlockPos(), sources > 0 ? SoundEvents.BLOCK_FIRE_EXTINGUISH : SoundEvents.BLOCK_FIRE_AMBIENT,
						SoundCategory.PLAYERS, 0.6f, 0.6f);
			}
		}

		@Override
		public boolean release(AbilityContext ctx, float charge) {
			ServerPlayerEntity player = ctx.player();
			if (charge <= 0.0f) {
				charging(ctx, 0, 0.0f);
			}
			BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.3f);
			Fx.flash(ctx.world(), player.getPos().add(0, 1.0, 0), EMBER, 1.6f);
			return true;
		}
	}

	/** sichtbarer Glutstrom von der Quelle in Heatblasts Koerper */
	private static void stream(ServerWorld world, Vec3d from, Vec3d to) {
		Vec3d path = to.subtract(from);
		Fx.flames(world, from, path.multiply(0.12), EMBER, 0.08f, 3);
		Fx.beam(world, from, to, 0xFF8A20, 0.08f, 4);
	}

	// --- 5 Flammenbrett --------------------------------------------------------------------------

	/** Surfen auf einem Feuerbrett: folgt dem Blick (auch hoch/runter), Spur brennt, Beruehrung versengt. Schleichen = Meteortritt. */
	private static boolean flameBoard(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		if (!SURFING.add(player.getUuid())) {
			return false;
		}
		int ticks = (int) ctx.param("surf_ticks", 80.0);
		double speed = ctx.param("speed", 0.95);
		float touch = (float) ctx.param("damage", 3.0);
		BuiltinAbilities.launch(player, 0.0, ctx.param("up", 0.6), 0.0);
		AttachedPropEntity.attach(world, BOARD, player, ticks + 5);
		Set<UUID> scorched = new HashSet<>();
		PowerTasks.start(player, ticks, 1, (w, p, age) -> {
			if (age > 4 && p.isSneaking()) {
				meteorKick(w, p, (float) ctx.param("kick_damage", 9.0));
				return false;
			}
			Vec3d look = p.getRotationVec(1.0f);
			double vy = MathHelper.clamp(look.y, -0.5, 0.45);
			Vec3d flat = new Vec3d(look.x, 0, look.z).normalize().multiply(speed);
			p.setVelocity(flat.x, vy * speed + 0.02, flat.z);
			p.velocityModified = true;
			p.fallDistance = 0.0f;
			Fx.flames(w, p.getPos().add(0, -0.15, 0), flat.multiply(-0.6).add(0, -0.1, 0), FIRE, 0.2f, 3);
			if (age % 3 == 0) {
				Vec3d below = AlienKit.ground(w, p.getPos(), 3.5);
				if (below.y < p.getY() - 0.05) {
					Destruction.ignite(w, p, below.add(0, 0.5, 0), 0.7, 0.5);
					Fx.scorch(w, below, 0.8f, 120);
				}
			}
			if (age % 2 == 0) {
				for (LivingEntity target : AlienKit.around(w, p, p.getPos().add(0, 0.4, 0), 1.6)) {
					if (scorched.add(target.getUuid())) {
						HeatblastAbilities.hit(p, target, touch, 4.0f);
						AlienKit.push(target, p.getPos(), 0.8, 0.4);
					}
				}
			}
			if (age % 10 == 0) {
				w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_BLAZE_BURN, SoundCategory.PLAYERS, 0.5f, 1.2f);
			}
			return true;
		}, (p, aborted) -> {
			SURFING.remove(p.getUuid());
			AttachedPropEntity.detach(p.getServerWorld(), BOARD, p);
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 30, 0, false, false));
		});
		Fx.shockwave(world, player.getPos(), FIRE, 2.5f, 8);
		Fx.flames(world, player.getPos(), new Vec3d(0, -0.35, 0), FIRE, 0.45f, 16);
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 10.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 0.9f, 0.8f);
		return true;
	}

	/** Absprung vom Brett: schraeg in den Boden — der Einschlag waechst mit dem Tempo. */
	private static void meteorKick(ServerWorld world, ServerPlayerEntity player, float damage) {
		Vec3d look = BuiltinAbilities.horizontalLook(player);
		player.setVelocity(look.x * 1.1, -1.6, look.z * 1.1);
		player.velocityModified = true;
		AttachedPropEntity.detach(world, BOARD, player);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2f, 0.6f);
		double startY = player.getY();
		PowerTasks.start(player, 40, 1, (w, p, age) -> {
			p.fallDistance = 0.0f;
			if (!p.isOnGround() && !p.isTouchingWater()) {
				Fx.flames(w, p.getPos().add(0, 0.6, 0), new Vec3d(0, 0.4, 0), FIRE, 0.35f, 4);
				return true;
			}
			float boost = (float) Math.min(2.0, 1.0 + Math.max(0.0, startY - p.getY()) / 12.0);
			Vec3d at = p.getPos();
			Fx.explosion(w, at.add(0, 0.5, 0), FIRE, 2.0f * boost);
			Fx.shockwave(w, at.add(0, 0.1, 0), EMBER, 4.0f * boost, 10);
			Fx.shake(w, at, 0.35f * boost, 10, 18);
			AlienKit.blast(w, p, at.add(0, 0.8, 0), 3.2 * boost, damage * boost, 0.9, 0.5).forEach(t -> t.setOnFireFor(5.0f));
			Destruction.crater(w, p, at.add(0, -0.4, 0), 1.4 * boost, 0.5, AlienStrength.of(p) * boost, 0.3, 0.5);
			Destruction.ignite(w, p, at, 2.5 * boost, 0.3);
			w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.2f, 0.9f);
			return false;
		});
	}

	// --- 6 Feuerwirbel ---------------------------------------------------------------------------

	/** Tornado am Zielpunkt: folgt dem Blick, hebt Gegner an, legt eine Feuerspur. Eigene Feuerbaelle und Atem fuettern ihn. */
	private static boolean fireTornado(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		Vec3d start = AlienKit.ground(world, AlienKit.aim(world, player, ctx.param("range", 14.0)), 8.0);
		Tornado tornado = new Tornado(start, (float) ctx.param("damage", 2.5) * power.factor(), ctx.param("radius", 3.0) * (power.blue() ? 1.4 : 1.0),
				power.color());
		TORNADOES.put(player.getUuid(), tornado);
		int base = (int) (ctx.param("seconds", 6.0) * 20);
		double lift = ctx.param("lift", 0.22);
		PowerTasks.start(player, base + 60, 1, (w, p, age) -> {
			if (TORNADOES.get(p.getUuid()) != tornado || age >= base + tornado.bonusTicks) {
				return false;
			}
			Vec3d want = AlienKit.ground(w, AlienKit.aim(w, p, 18.0), 8.0);
			Vec3d step = want.subtract(tornado.pos).multiply(1, 0, 1);
			if (step.lengthSquared() > 0.0196) {
				step = step.normalize().multiply(0.14);
			}
			tornado.pos = AlienKit.ground(w, tornado.pos.add(step.x, 2.0, step.z), 6.0);
			double reach = tornado.reach();
			double height = 3.5 + tornado.stacks * 1.2;
			// eigene Feuergeschosse im Wirbel werden aufgesogen und machen ihn groesser
			for (HeroProjectileEntity orb : w.getEntitiesByClass(HeroProjectileEntity.class, Box.of(tornado.pos.add(0, height * 0.5, 0), reach * 2,
					height, reach * 2), e -> e.getOwner() == p && e.getKind() == HeroProjectileEntity.Kind.FIRE)) {
				orb.discard();
				tornado.feed(w, 1);
			}
			if (age % 2 == 0) {
				// Trichter: je Ring zwei gegenueberliegende Flammenarme, nach oben weiter und schneller drehend
				double spin = age * 0.55;
				int rings = 6 + tornado.stacks * 2;
				for (int i = 0; i < rings; i++) {
					double h = i * height / rings;
					double r = 0.4 + h / height * reach * 0.9;
					for (int arm = 0; arm < 2; arm++) {
						double a = spin * (1.0 + h / height * 0.6) + i * 1.3 + arm * Math.PI;
						Vec3d tangent = new Vec3d(-Math.sin(a), 0.35, Math.cos(a)).multiply(0.3 + h / height * 0.2);
						Fx.flames(w, tornado.pos.add(Math.cos(a) * r, h + 0.2, Math.sin(a) * r), tangent, i < 2 ? EMBER : tornado.color, 0.12f, 2);
					}
				}
				// Glutkern in der Mitte, Rauch an der Spitze
				Fx.flames(w, tornado.pos.add(0, 0.3, 0), new Vec3d(0, 0.45, 0), EMBER, 0.2f, 2);
				if (age % 6 == 0) {
					Fx.smoke(w, tornado.pos.add(0, height + 0.5, 0), 0x3A3430, 1.0f + tornado.stacks * 0.3f, 2);
				}
			}
			if (age % 10 == 0) {
				for (LivingEntity target : AlienKit.around(w, p, tornado.pos.add(0, 1.5, 0), reach + 2.5)) {
					Vec3d pull = tornado.pos.subtract(target.getPos()).multiply(1, 0, 1);
					double d = pull.length();
					Vec3d swirl = d > 0.1 ? new Vec3d(-pull.z, 0, pull.x).normalize().multiply(0.25) : Vec3d.ZERO;
					Vec3d in = d > 0.5 ? pull.normalize().multiply(0.3) : Vec3d.ZERO;
					target.addVelocity(in.x + swirl.x, d <= reach ? lift + tornado.stacks * 0.06 : 0.1, in.z + swirl.z);
					target.velocityModified = true;
					if (d <= reach) {
						HeatblastAbilities.hit(p, target, tornado.damage * (1.0f + 0.4f * tornado.stacks), 4.0f);
					}
				}
				Destruction.ignite(w, p, tornado.pos, 1.2 + tornado.stacks * 0.4, 0.35);
				Fx.scorch(w, tornado.pos, (float) reach * 0.6f, 120);
				w.playSound(null, BlockPos.ofFloored(tornado.pos), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.7f, 0.5f);
			}
			return true;
		}, (p, aborted) -> TORNADOES.remove(p.getUuid(), tornado));
		Fx.shockwave(world, start.add(0, 0.1, 0), tornado.color, (float) tornado.radius * 1.5f, 10);
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 18.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_AMBIENT, 1.2f, 0.5f);
		return true;
	}

	// --- 7 Kometensatz ---------------------------------------------------------------------------

	/** Heatblast wird zur Feuerkugel, rast zum Zielpunkt (streift Gegner unterwegs) und schlaegt dort mit Explosion ein. */
	private static boolean cometDash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		HeatblastAbilities.Power power = HeatblastAbilities.power(player);
		Vec3d target = AlienKit.aim(world, player, ctx.param("range", 26.0));
		if (target.distanceTo(player.getEyePos()) < 3.0) {
			return false;
		}
		float damage = (float) ctx.param("damage", 9.0) * power.factor();
		double speed = ctx.param("speed", 1.7);
		int color = power.color();
		Set<UUID> grazed = new HashSet<>();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 30, 0, false, false));
		ctx.grantInvulnerability(30);
		Fx.explosion(world, player.getPos().add(0, 1.0, 0), color, 1.4f);
		PowerTasks.start(player, 30, 1, (w, p, age) -> {
			Vec3d body = p.getPos().add(0, 1.0, 0);
			Vec3d to = target.subtract(body);
			p.fallDistance = 0.0f;
			boolean arrived = to.length() <= speed * 1.2 || age > 2 && p.horizontalCollision || age >= 29;
			if (!arrived) {
				Vec3d v = to.normalize().multiply(speed);
				p.setVelocity(v);
				p.velocityModified = true;
				// Feuerkugel: dichter Kern, Flammenmantel, Schweif
				Fx.flash(w, body, color, 1.4f);
				Fx.flames(w, body, v.multiply(-0.4), color, 0.45f, 6);
				Fx.flames(w, body, Vec3d.ZERO, EMBER, 0.6f, 3);
				for (LivingEntity hit : AlienKit.around(w, p, body, 1.8)) {
					if (grazed.add(hit.getUuid())) {
						HeatblastAbilities.hit(p, hit, damage * 0.5f, 4.0f);
					}
				}
				return true;
			}
			p.setVelocity(p.getVelocity().multiply(0.1));
			p.velocityModified = true;
			p.removeStatusEffect(StatusEffects.INVISIBILITY);
			Fx.explosion(w, body, color, 2.6f);
			Fx.flash(w, body, 0xFFFFFF, 3.0f);
			Fx.shockwave(w, p.getPos().add(0, 0.1, 0), color, 5.0f, 10);
			Fx.shake(w, body, 0.4f, 10, 20);
			AlienKit.blast(w, p, body, ctx.param("radius", 3.5), damage, 1.0, 0.5).forEach(t -> t.setOnFireFor(6.0f));
			Destruction.crater(w, p, p.getPos().add(0, -0.3, 0), 1.6, 0.5, AlienStrength.of(p), 0.3, 0.5);
			Destruction.ignite(w, p, p.getPos(), 2.5, 0.3);
			w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.3f, 0.8f);
			return false;
		}, (p, aborted) -> p.removeStatusEffect(StatusEffects.INVISIBILITY));
		HeatblastAbilities.addHeat(player, (float) ctx.param("heat", 12.0));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_SHOOT, 1.2f, 0.6f);
		BuiltinAbilities.sound(ctx, ModSounds.ALIEN_FIRE, 1.0f, 0.5f);
		return true;
	}

	// --- 8 Supernova -----------------------------------------------------------------------------

	/**
	 * Supernova in Stufen: unter halbem Kern verweigert sie. Ab Gluehend sammelt Heatblast eine Sekunde lang Hitze in
	 * der Luft und entlaedt sie; bei vollem Kern bleibt ein Sonnenkern stehen, der Gegner ansaugt und am Ende implodiert.
	 */
	private static boolean supernova(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		float heat = HeatblastAbilities.heat(player);
		if (heat < HeatblastAbilities.GLOW) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.supernova_cold").formatted(Formatting.GOLD), true);
			return false;
		}
		HeatblastAbilities.drain(player);
		boolean full = heat >= HeatblastAbilities.MAX;
		int color = full ? BLUE : FIRE;
		float scale = heat / HeatblastAbilities.MAX;
		double radius = ctx.param("radius", 10.0) * (full ? ctx.param("full_radius_factor", 1.4) : 0.7 + 0.3 * scale);
		float damage = (float) ctx.param("damage", 18.0) * (full ? (float) ctx.param("full_damage_factor", 1.7) : 0.6f + 0.4f * scale);
		int windup = (int) ctx.param("windup_ticks", 20.0);
		ctx.grantInvulnerability(windup + 10);
		PowerTasks.start(player, windup + 1, 1, (w, p, age) -> {
			Vec3d body = p.getPos().add(0, 1.0, 0);
			if (age < windup) {
				// Aufsteigen und Hitze sammeln: Flammen stroemen von aussen in den Koerper
				p.setVelocity(p.getVelocity().multiply(0.3).add(0, 0.06, 0));
				p.velocityModified = true;
				p.fallDistance = 0.0f;
				for (int i = 0; i < 3; i++) {
					double a = world.random.nextDouble() * MathHelper.TAU;
					double r = radius * (0.5 + 0.5 * (1.0 - age / (double) windup));
					Vec3d from = body.add(Math.cos(a) * r, (world.random.nextDouble() - 0.3) * 3.0, Math.sin(a) * r);
					Fx.flames(w, from, body.subtract(from).multiply(0.2), color, 0.05f, 2);
				}
				if (age % 4 == 0) {
					Fx.flash(w, body, color, 1.0f + age / (float) windup * 2.5f);
					w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 0.7f, 0.6f + age * 0.04f);
				}
				return true;
			}
			nova(w, p, body, radius, damage, color, ctx);
			if (full) {
				// ueber Heatblast, damit die Mini-Sonne sichtbar am Himmel steht
				sunCore(w, p, p.getPos().add(0, 3.5, 0), radius * 0.6, (float) ctx.param("core_damage", 3.0), color,
						(int) ctx.param("core_ticks", 120.0));
			}
			return false;
		});
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BLAZE_AMBIENT, 1.2f, 0.4f);
		return true;
	}

	private static void nova(ServerWorld world, ServerPlayerEntity player, Vec3d body, double radius, float damage, int color, AbilityContext ctx) {
		for (LivingEntity target : AlienKit.around(world, player, body, radius)) {
			double distance = AlienKit.body(target).distanceTo(body);
			HeatblastAbilities.hit(player, target, (float) (damage * (1.0 - 0.45 * distance / radius)), (float) ctx.param("fire_seconds", 8.0));
			AlienKit.push(target, body, ctx.param("knockback", 1.6) * (1.0 - 0.5 * distance / radius), ctx.param("launch", 0.5));
		}
		Fx.explosion(world, body, color, (float) radius * 0.45f);
		Fx.explosion(world, body.add(0, 1.5, 0), 0xFFD27A, (float) radius * 0.25f);
		Fx.flash(world, body, 0xFFFFFF, (float) radius * 0.8f);
		Fx.shockwave(world, player.getPos(), color, (float) radius * 1.2f, 14);
		Fx.shockwave(world, player.getPos(), 0xFFF0C0, (float) radius * 0.8f, 22);
		Fx.flames(world, player.getPos(), new Vec3d(0, 0.7, 0), color, 0.35f, 30);
		Fx.scorch(world, player.getPos(), (float) radius * 0.7f, 300);
		Fx.shake(world, player.getPos(), 1.0f, 24, (int) (radius * 2.5));
		float strength = AlienStrength.of(player) * (color == BLUE ? 2.5f : 1.6f);
		Destruction.crater(world, player, player.getPos().add(0, 0.3, 0), radius * 0.6, 0.6, strength, 0.35, 0.7);
		Destruction.ignite(world, player, player.getPos(), radius, 0.18);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.6f, 0.5f);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.PLAYERS, 1.0f, 0.6f);
	}

	/** Sonnenkern: schwebende Mini-Sonne, saugt Gegner an und brennt sie; zum Schluss implodiert sie mit einem zweiten Knall. */
	private static void sunCore(ServerWorld world, ServerPlayerEntity player, Vec3d pos, double radius, float damage, int color, int ticks) {
		PowerTasks.start(player, ticks + 1, 1, (w, p, age) -> {
			if (age % 2 == 0) {
				Fx.flash(w, pos, color, 2.6f + (float) Math.sin(age * 0.4) * 0.5f);
				Fx.flames(w, pos, Vec3d.ZERO, color, 0.7f, 5);
				Fx.flames(w, pos, new Vec3d(0, -0.4, 0), 0xFFF0C0, 0.3f, 2);
			}
			if (age % 10 == 0) {
				Fx.shockwave(w, AlienKit.ground(w, pos, 8.0).add(0, 0.1, 0), color, (float) radius, 10);
				for (LivingEntity target : AlienKit.around(w, p, pos, radius)) {
					Vec3d in = pos.subtract(AlienKit.body(target));
					if (in.lengthSquared() > 0.5) {
						target.addVelocity(in.normalize().x * 0.35, in.normalize().y * 0.2 + 0.05, in.normalize().z * 0.35);
						target.velocityModified = true;
					}
					HeatblastAbilities.hit(p, target, damage, 3.0f);
				}
				w.playSound(null, BlockPos.ofFloored(pos), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 1.5f, 0.5f);
			}
			if (age >= ticks) {
				Fx.explosion(w, pos, color, (float) radius * 0.6f);
				Fx.flash(w, pos, 0xFFFFFF, (float) radius);
				Fx.shake(w, pos, 0.6f, 14, (int) (radius * 3));
				AlienKit.blast(w, p, pos, radius, damage * 4.0f, 1.2, 0.6).forEach(t -> t.setOnFireFor(6.0f));
				w.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, 0.7f);
			}
			return true;
		});
	}
}
