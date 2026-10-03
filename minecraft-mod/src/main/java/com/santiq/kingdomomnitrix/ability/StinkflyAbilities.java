package com.santiq.kingdomomnitrix.ability;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.weapon.HeroProjectileEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Stinkfly (Lepidopterran). Eigenes Dauer-System „Toxin-Schichten“ — keine Aura, sichtbar an den Gegnern:
 *
 * <ul>
 *   <li>Jeder Treffer von Stinkfly und jede Sekunde in seinen Giftwolken legt eine Schicht auf (0–{@link #MAX_STACKS},
 *       gruene Blasen am Ziel, je Schicht mehr). Ohne Nachschub faellt alle {@link #DECAY_TICKS} Ticks eine weg.</li>
 *   <li>Bei {@link #MAX_STACKS} <b>Zersetzung</b>: Verdorren, verlangsamt, jeder Treffer (von wem auch immer) macht
 *       {@link #ROT_BONUS} mehr Schaden.</li>
 *   <li><b>Ansteckung</b>: stirbt ein Ziel mit mindestens {@link #BURST_STACKS} Schichten, platzt es — Giftwolke am
 *       Todesort, Nachbarn bekommen zwei Schichten.</li>
 * </ul>
 * Giftwolken sind eigene, leichte Objekte (kein AreaEffectCloud), damit sie Schichten statt nur Effekte geben.
 */
final class StinkflyAbilities {
	static final int MAX_STACKS = 5;
	static final int BURST_STACKS = 3;
	static final float ROT_BONUS = 0.3f;
	private static final int DECAY_TICKS = 60;
	private static final Identifier STINKFLY = KingdomOmnitrix.id("stinkfly");
	private static final DustParticleEffect TOXIC = new DustParticleEffect(new Vector3f(0.55f, 0.75f, 0.15f), 1.5f);
	private static final DustParticleEffect SLIME = new DustParticleEffect(new Vector3f(0.45f, 0.9f, 0.3f), 1.1f);

	/** Schichten je Ziel */
	private static final Map<UUID, Toxin> TOXINS = new HashMap<>();
	private static final List<Cloud> CLOUDS = new ArrayList<>();
	/** Giftsturm: Ende (danach Entladung) */
	private static final Map<UUID, Long> STORMS = new HashMap<>();
	private static boolean bonusHit;

	private static final class Toxin {
		final UUID owner;
		int stacks;
		long last;

		Toxin(UUID owner) {
			this.owner = owner;
		}
	}

	/** Giftwolke: gibt Personen darin je Sekunde eine Schicht; {@code slime} macht zusaetzlich klebrig. */
	private record Cloud(UUID owner, RegistryKey<World> world, Vec3d pos, double radius, long until, boolean slime) {
	}

	private StinkflyAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("slime_spit"), StinkflyAbilities::slimeSpit);
		AbilityRegistry.register(KingdomOmnitrix.id("stink_cloud"), StinkflyAbilities::stinkCloud);
		AbilityRegistry.register(KingdomOmnitrix.id("wing_dash"), StinkflyAbilities::wingDash);
		AbilityRegistry.register(KingdomOmnitrix.id("slime_bomb"), StinkflyAbilities::slimeBomb);
		AbilityRegistry.register(KingdomOmnitrix.id("updraft"), StinkflyAbilities::updraft);
		AbilityRegistry.register(KingdomOmnitrix.id("toxic_storm"), StinkflyAbilities::toxicStorm);
		ServerTickEvents.END_SERVER_TICK.register(StinkflyAbilities::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StinkflyAbilities::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(StinkflyAbilities::afterDeath);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			STORMS.remove(id);
			CLOUDS.removeIf(c -> c.owner().equals(id));
		});
	}

	// --- Toxin -----------------------------------------------------------------------------------

	private static boolean isStinkfly(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(STINKFLY::equals).isPresent();
	}

	static int stacks(LivingEntity target) {
		Toxin toxin = TOXINS.get(target.getUuid());
		return toxin == null ? 0 : toxin.stacks;
	}

	private static void addToxin(ServerPlayerEntity owner, LivingEntity target, int amount) {
		if (!target.isAlive() || target == owner || !PartyRules.canHarm(owner, target)) {
			return;
		}
		Toxin toxin = TOXINS.computeIfAbsent(target.getUuid(), id -> new Toxin(owner.getUuid()));
		int before = toxin.stacks;
		toxin.stacks = Math.min(MAX_STACKS, toxin.stacks + amount);
		toxin.last = owner.getServerWorld().getTime();
		ServerWorld world = owner.getServerWorld();
		world.spawnParticles(TOXIC, target.getX(), target.getBodyY(0.7), target.getZ(), 3 + toxin.stacks * 2, 0.3, 0.3, 0.3, 0.0);
		if (before < MAX_STACKS && toxin.stacks >= MAX_STACKS) {
			// Zersetzung
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0), owner);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), owner);
			world.spawnParticles(ParticleTypes.SNEEZE, target.getX(), target.getBodyY(0.6), target.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.0f, 0.5f);
			owner.sendMessage(Text.translatable("message.kingdomomnitrix.stinkfly_rot", target.getDisplayName()).formatted(Formatting.GREEN), true);
		}
	}

	/** Stinkfly-Treffer legen eine Schicht auf; zersetzte Ziele nehmen von jedem mehr Schaden. */
	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (bonusHit || damageTaken <= 0.0f) {
			return;
		}
		if (source.getAttacker() instanceof ServerPlayerEntity player && player != target && isStinkfly(player)) {
			addToxin(player, target, 1);
		}
		if (stacks(target) >= MAX_STACKS && source.getAttacker() instanceof LivingEntity attacker && attacker != target && target.isAlive()) {
			bonusHit = true;
			try {
				target.timeUntilRegen = 0;
				target.damage(target.getWorld().getDamageSources().magic(), damageTaken * ROT_BONUS);
			} finally {
				bonusHit = false;
			}
		}
	}

	/** Ansteckung: stark vergiftete Ziele platzen beim Tod. */
	private static void afterDeath(LivingEntity target, DamageSource source) {
		Toxin toxin = TOXINS.remove(target.getUuid());
		if (toxin == null || toxin.stacks < BURST_STACKS || !(target.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(toxin.owner);
		if (owner == null) {
			return;
		}
		CLOUDS.add(new Cloud(owner.getUuid(), world.getRegistryKey(), target.getPos(), 3.0, world.getTime() + 100, false));
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(4.0),
				e -> e != target && e != owner && e.isAlive() && PartyRules.canHarm(owner, e))) {
			addToxin(owner, near, 2);
		}
		world.spawnParticles(TOXIC, target.getX(), target.getBodyY(0.5), target.getZ(), 60, 1.2, 0.8, 1.2, 0.0);
		world.spawnParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getBodyY(0.5), target.getZ(), 30, 0.6, 0.5, 0.6, 0.15);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SLIME_DEATH, SoundCategory.PLAYERS, 1.2f, 0.6f);
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Schleimspucke: jede Kugel legt beim Treffer eine Schicht auf. */
	private static boolean slimeSpit(AbilityContext ctx) {
		int count = (int) Math.max(1, ctx.param("count", 2));
		float speed = (float) ctx.param("speed", 1.8);
		for (int i = 0; i < count; i++) {
			HeroProjectileEntity.shoot(ctx.world(), ctx.player(), Items.SLIME_BALL, speed, (float) ctx.param("spread", 3.0))
					.withDamage((float) ctx.param("damage", 3.0));
		}
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SLIME_SQUISH, 1.0f, 1.3f);
		return true;
	}

	private static boolean stinkCloud(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d at = groundBelow(ctx.world(), player.getPos().add(BuiltinAbilities.horizontalLook(player).multiply(2.0)));
		CLOUDS.add(new Cloud(player.getUuid(), ctx.world().getRegistryKey(), at, ctx.param("radius", 3.5),
				ctx.world().getTime() + (long) ctx.param("ticks", 120), false));
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, 1.0f, 0.6f);
		return true;
	}

	/** Fluegelstoss: Ausweichen in Blickrichtung, hinterlaesst eine Giftschleppe aus kleinen Wolken. */
	private static boolean wingDash(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Vec3d start = player.getPos();
		Vec3d look = player.getRotationVec(1.0f);
		double power = ctx.param("power", 1.8);
		BuiltinAbilities.launch(player, look.x * power, look.y * power + 0.15, look.z * power);
		ctx.grantInvulnerability((int) ctx.param("invulnerable_ticks", 8));
		for (int i = 0; i < 3; i++) {
			CLOUDS.add(new Cloud(player.getUuid(), ctx.world().getRegistryKey(), start.add(look.multiply(i * 1.5)), 1.4,
					ctx.world().getTime() + 60, false));
		}
		ctx.world().spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getBodyY(0.5), player.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 1.2f);
		return true;
	}

	/** Schleimbombe: landet am Zielpunkt, platzt — drei Schichten im Umkreis und ein klebriges Schleimfeld. */
	private static boolean slimeBomb(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(20.0));
		HitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		Vec3d at = hit.getType() == HitResult.Type.MISS ? groundBelow(world, end) : hit.getPos();
		// Flugbahn als Bogen aus Schleim
		for (int i = 1; i <= 16; i++) {
			double t = i / 16.0;
			Vec3d p = eye.lerp(at, t).add(0, Math.sin(t * Math.PI) * 2.0, 0);
			world.spawnParticles(SLIME, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		float damage = (float) ctx.param("damage", 6.0);
		double radius = ctx.param("explosion", 1.6) * 2.0;
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, Box.of(at, radius * 2, radius * 2, radius * 2),
				e -> e != player && e.isAlive() && e.squaredDistanceTo(at) <= radius * radius && PartyRules.canHarm(player, e))) {
			target.damage(world.getDamageSources().indirectMagic(player, player), damage);
			addToxin(player, target, 2);
		}
		CLOUDS.add(new Cloud(player.getUuid(), world.getRegistryKey(), at, radius, world.getTime() + 80, true));
		world.spawnParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.3, at.z, 40, radius * 0.4, 0.3, radius * 0.4, 0.2);
		world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_SLIME_DEATH, SoundCategory.PLAYERS, 1.2f, 0.5f);
		return true;
	}

	/** Aufwind: schleudert Gegner hoch und vergiftet sie; Stinkfly segelt danach sanft. */
	private static boolean updraft(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double launch = ctx.param("launch", 1.2);
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, ctx.param("radius", 5.0))) {
			target.damage(ctx.world().getDamageSources().playerAttack(player), (float) ctx.param("damage", 3.0));
			target.addVelocity(0.0, launch, 0.0);
			target.velocityModified = true;
			addToxin(player, target, 1);
		}
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, false, false));
		ctx.world().spawnParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.5, player.getZ(), 6, 1.5, 0.3, 1.5, 0.0);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_BREEZE_JUMP, 1.0f, 1.0f);
		return true;
	}

	/** Giftsturm: Wolkenring um Stinkfly; am Ende entladen sich alle Schichten im Umkreis als Schaden. */
	private static boolean toxicStorm(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int clouds = (int) Math.max(3, ctx.param("clouds", 6));
		double ring = ctx.param("ring", 4.0);
		long until = world.getTime() + (long) ctx.param("ticks", 160);
		for (int i = 0; i < clouds; i++) {
			double angle = i * MathHelper.TAU / clouds;
			CLOUDS.add(new Cloud(player.getUuid(), world.getRegistryKey(),
					player.getPos().add(Math.cos(angle) * ring, 0, Math.sin(angle) * ring), ctx.param("radius", 2.5), until, false));
		}
		STORMS.put(player.getUuid(), until);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_PUFFER_FISH_BLOW_OUT, 1.2f, 0.4f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tick = server.getTicks();
		if (!CLOUDS.isEmpty()) {
			tickClouds(server, tick);
		}
		if (!TOXINS.isEmpty() && tick % 20 == 0) {
			tickToxins(server);
		}
		if (!STORMS.isEmpty()) {
			Iterator<Map.Entry<UUID, Long>> it = STORMS.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Long> entry = it.next();
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				if (player == null) {
					it.remove();
				} else if (player.getServerWorld().getTime() >= entry.getValue()) {
					it.remove();
					detonate(player);
				}
			}
		}
	}

	private static void tickClouds(MinecraftServer server, long tick) {
		Iterator<Cloud> it = CLOUDS.iterator();
		while (it.hasNext()) {
			Cloud cloud = it.next();
			ServerWorld world = server.getWorld(cloud.world());
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(cloud.owner());
			if (world == null || owner == null || world.getTime() >= cloud.until()) {
				it.remove();
				continue;
			}
			if (tick % 4 == 0) {
				world.spawnParticles(cloud.slime() ? SLIME : TOXIC, cloud.pos().x, cloud.pos().y + 0.4, cloud.pos().z,
						(int) (cloud.radius() * 4), cloud.radius() * 0.5, 0.3, cloud.radius() * 0.5, 0.0);
			}
			if (tick % 20 != 0) {
				continue;
			}
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, Box.of(cloud.pos(), cloud.radius() * 2, 3, cloud.radius() * 2),
					e -> e != owner && e.isAlive() && e.getPos().squaredDistanceTo(cloud.pos()) <= cloud.radius() * cloud.radius()
							&& PartyRules.canHarm(owner, e))) {
				addToxin(owner, target, 1);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0), owner);
				if (cloud.slime()) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2), owner);
				}
			}
		}
	}

	/** Schichten zerfallen ohne Nachschub; zersetzte Ziele blubbern. */
	private static void tickToxins(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Toxin>> it = TOXINS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Toxin> entry = it.next();
			Toxin toxin = entry.getValue();
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(toxin.owner);
			if (owner == null || !(owner.getServerWorld().getEntity(entry.getKey()) instanceof LivingEntity target) || !target.isAlive()) {
				it.remove();
				continue;
			}
			ServerWorld world = owner.getServerWorld();
			if (world.getTime() - toxin.last > DECAY_TICKS) {
				toxin.stacks--;
				toxin.last = world.getTime() - DECAY_TICKS / 2;
				if (toxin.stacks <= 0) {
					it.remove();
					continue;
				}
			}
			world.spawnParticles(TOXIC, target.getX(), target.getBodyY(0.8), target.getZ(), toxin.stacks, 0.25, 0.3, 0.25, 0.0);
		}
	}

	/** Entladung des Giftsturms: jedes Ziel im Umkreis nimmt Schaden nach seinen Schichten, die Schichten sind weg. */
	private static void detonate(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		int count = 0;
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(10.0),
				e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
			Toxin toxin = TOXINS.remove(target.getUuid());
			if (toxin == null) {
				continue;
			}
			target.timeUntilRegen = 0;
			target.damage(world.getDamageSources().indirectMagic(player, player), 3.0f * toxin.stacks);
			world.spawnParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getBodyY(0.5), target.getZ(), 10 + toxin.stacks * 4, 0.4, 0.4, 0.4, 0.15);
			count++;
		}
		if (count > 0) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_DEATH, SoundCategory.PLAYERS, 1.4f, 0.4f);
		}
	}

	private static Vec3d groundBelow(ServerWorld world, Vec3d at) {
		HitResult hit = world.raycast(new RaycastContext(at.add(0.0, 1.0, 0.0), at.subtract(0.0, 6.0, 0.0),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
		return hit.getType() == HitResult.Type.MISS ? at : hit.getPos();
	}
}
