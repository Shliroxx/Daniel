package com.santiq.kingdomomnitrix.ability;

import com.mojang.serialization.Codec;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.alien.TransformationManager;
import com.santiq.kingdomomnitrix.party.PartyRules;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import com.santiq.kingdomomnitrix.util.Targeting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Grey Matter (Galvan). Eigenes Dauer-System „Analyse-Datenbank“ — kein Kraftwert und keine Aura, sondern Wissen:
 *
 * <ul>
 *   <li>Jede Analyse eines neuen Gegners erhoeht das Wissen ueber seine Art (0–{@link #MAX_LEVEL}), dauerhaft im
 *       Spielstand. Pro Stufe macht der Spieler in <b>jeder</b> Form (Mensch und jedes Alien) {@link #BONUS_PER_LEVEL}
 *       mehr Schaden gegen diese Art.</li>
 *   <li>Analysierte Ziele sind markiert: die ganze Gruppe trifft sie 20 % haerter.</li>
 *   <li>Schwachstelle: der naechste Treffer irgendeines Spielers macht 150 % Zusatzschaden und verbraucht die Markierung.</li>
 *   <li>Masterplan: Grey Matter weicht 10 s lang jedem zweiten Angriff aus, alles im Umkreis wird analysiert.</li>
 * </ul>
 * Zusatzschaden entsteht als zweiter, magischer Treffer direkt nach dem eigentlichen (eine Sperre verhindert Ketten).
 */
@SuppressWarnings("UnstableApiUsage")
final class GreyMatterAbilities {
	static final int MAX_LEVEL = 5;
	static final float BONUS_PER_LEVEL = 0.08f;
	private static final float MARK_BONUS = 0.2f;
	private static final float WEAK_BONUS = 1.5f;
	private static final int MARK_TICKS = 200;
	private static final int MAX_TRAPS = 3;
	private static final Identifier GREY_MATTER = KingdomOmnitrix.id("grey_matter");
	private static final DustParticleEffect TECH = new DustParticleEffect(new Vector3f(0.22f, 1.0f, 0.08f), 1.0f);
	private static final Codec<Map<Identifier, Integer>> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, MAX_LEVEL));

	/** Wissen je Gegnerart (dauerhaft, nur an den Besitzer synchronisiert) */
	static final AttachmentType<Map<Identifier, Integer>> KNOWLEDGE = AttachmentRegistry.create(KingdomOmnitrix.id("grey_matter_knowledge"),
			builder -> builder.persistent(CODEC).initializer(Map::of).copyOnDeath()
					.syncWith(PacketCodecs.codec(CODEC), AttachmentSyncPredicate.targetOnly()));

	/** markierte Ziele: Ende der Markierung */
	private static final Map<UUID, Long> ANALYZED = new HashMap<>();
	private static final Map<UUID, Long> WEAK = new HashMap<>();
	/** schon analysierte Einzelwesen je Spieler (jedes zaehlt nur einmal) */
	private static final Map<UUID, Set<UUID>> SCANNED = new HashMap<>();
	private static final Map<UUID, Long> SCURRY = new HashMap<>();
	private static final Map<UUID, Long> MASTERMIND = new HashMap<>();
	private static final List<Trap> TRAPS = new ArrayList<>();
	/** Sperre: Zusatzschaden loest keinen weiteren Zusatzschaden aus */
	private static boolean bonusHit;

	private record Trap(UUID owner, RegistryKey<World> world, Vec3d pos, long until, int rootTicks) {
	}

	private GreyMatterAbilities() {
	}

	static void register() {
		AbilityRegistry.register(KingdomOmnitrix.id("scan"), GreyMatterAbilities::scan);
		AbilityRegistry.register(KingdomOmnitrix.id("weak_spot"), GreyMatterAbilities::weakSpot);
		AbilityRegistry.register(KingdomOmnitrix.id("scurry"), GreyMatterAbilities::scurry);
		AbilityRegistry.register(KingdomOmnitrix.id("tech_snare"), GreyMatterAbilities::techSnare);
		AbilityRegistry.register(KingdomOmnitrix.id("jury_rig"), GreyMatterAbilities::juryRig);
		AbilityRegistry.register(KingdomOmnitrix.id("mastermind"), GreyMatterAbilities::mastermind);
		ServerTickEvents.END_SERVER_TICK.register(GreyMatterAbilities::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(GreyMatterAbilities::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GreyMatterAbilities::allowDamage);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			SCANNED.remove(id);
			SCURRY.remove(id);
			MASTERMIND.remove(id);
			TRAPS.removeIf(t -> t.owner().equals(id));
		});
	}

	// --- Wissen ----------------------------------------------------------------------------------

	static int knowledge(ServerPlayerEntity player, LivingEntity target) {
		return player.getAttachedOrElse(KNOWLEDGE, Map.of()).getOrDefault(Registries.ENTITY_TYPE.getId(target.getType()), 0);
	}

	/** Wissen ueber die Art erhoehen (jedes Einzelwesen nur einmal); liefert die neue Stufe. */
	private static int learn(ServerPlayerEntity player, LivingEntity target) {
		Set<UUID> seen = SCANNED.computeIfAbsent(player.getUuid(), id -> new LinkedHashSet<>());
		int level = knowledge(player, target);
		if (!seen.add(target.getUuid()) || level >= MAX_LEVEL) {
			return level;
		}
		if (seen.size() > 256) {
			// aelteste Eintraege vergessen, die Liste waechst sonst ueber eine lange Sitzung
			Iterator<UUID> it = seen.iterator();
			it.next();
			it.remove();
		}
		Map<Identifier, Integer> map = new HashMap<>(player.getAttachedOrElse(KNOWLEDGE, Map.of()));
		map.put(Registries.ENTITY_TYPE.getId(target.getType()), level + 1);
		player.setAttached(KNOWLEDGE, Map.copyOf(map));
		return level + 1;
	}

	private static boolean isGreyMatter(ServerPlayerEntity player) {
		return TransformationManager.get(player).activeAlien().filter(GREY_MATTER::equals).isPresent();
	}

	private static boolean marked(Map<UUID, Long> marks, LivingEntity target) {
		Long until = marks.get(target.getUuid());
		return until != null && until > target.getWorld().getTime();
	}

	/** Zusatzschaden aus Wissen, Analyse-Markierung und Schwachstelle — fuer jeden Spieler in jeder Form. */
	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (bonusHit || damageTaken <= 0.0f || !(source.getAttacker() instanceof ServerPlayerEntity attacker) || attacker == target
				|| !target.isAlive()) {
			return;
		}
		float factor = knowledge(attacker, target) * BONUS_PER_LEVEL;
		if (marked(ANALYZED, target)) {
			factor += MARK_BONUS;
		}
		boolean weak = marked(WEAK, target);
		if (weak) {
			factor += WEAK_BONUS;
			WEAK.remove(target.getUuid());
			ServerWorld world = attacker.getServerWorld();
			world.spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.6), target.getZ(), 30, 0.4, 0.5, 0.4, 0.3);
			world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.2f, 2.0f);
		}
		if (factor <= 0.0f) {
			return;
		}
		bonusHit = true;
		try {
			target.timeUntilRegen = 0;
			target.damage(attacker.getServerWorld().getDamageSources().indirectMagic(attacker, attacker), damageTaken * factor);
		} finally {
			bonusHit = false;
		}
	}

	/** Masterplan: Grey Matter sieht Angriffe kommen und weicht jedem zweiten aus. */
	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayerEntity player && source.getAttacker() instanceof LivingEntity
				&& MASTERMIND.getOrDefault(player.getUuid(), 0L) > player.getServerWorld().getTime() && player.getRandom().nextBoolean()) {
			player.getServerWorld().spawnParticles(TECH, player.getX(), player.getBodyY(0.5), player.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SILVERFISH_STEP, SoundCategory.PLAYERS, 1.0f, 2.0f);
			return false;
		}
		return true;
	}

	// --- Faehigkeiten ----------------------------------------------------------------------------

	/** Analyse: Werte des Ziels, Wissen ueber die Art +1, Ziel ist fuer die Gruppe markiert. */
	private static boolean scan(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, ctx.param("range", 24.0));
		if (found.isEmpty()) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		int before = knowledge(player, target);
		int level = learn(player, target);
		ANALYZED.put(target.getUuid(), ctx.world().getTime() + MARK_TICKS);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, MARK_TICKS, 0, false, false), player);
		double attack = target.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)
				? target.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) : 0.0;
		player.sendMessage(Text.translatable("message.kingdomomnitrix.scan", target.getDisplayName(),
				String.format("%.1f", target.getHealth()), String.format("%.1f", target.getMaxHealth()),
				String.format("%.0f", target.getAttributeValue(EntityAttributes.GENERIC_ARMOR)), String.format("%.1f", attack))
				.formatted(Formatting.GRAY), false);
		player.sendMessage(Text.translatable(level > before ? "message.kingdomomnitrix.grey_matter_learned" : "message.kingdomomnitrix.grey_matter_known",
				target.getType().getName(), level, MAX_LEVEL, Math.round(level * BONUS_PER_LEVEL * 100)).formatted(Formatting.GREEN), false);
		// Analyse-Strahl vom Kopf zum Ziel
		Vec3d from = player.getEyePos();
		Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
		for (int i = 1; i <= 10; i++) {
			Vec3d p = from.lerp(to, i / 10.0);
			ctx.world().spawnParticles(TECH, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		BuiltinAbilities.sound(ctx, ModSounds.SHIP_AI, 0.7f, level > before ? 1.5f : 1.2f);
		return true;
	}

	private static boolean weakSpot(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		Optional<LivingEntity> found = Targeting.findLivingTarget(player, ctx.param("range", 16.0));
		if (found.isEmpty() || !PartyRules.canHarm(player, found.get())) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.no_target").formatted(Formatting.GRAY), true);
			return false;
		}
		LivingEntity target = found.get();
		int ticks = (int) (ctx.param("seconds", 10.0) * 20);
		WEAK.put(target.getUuid(), ctx.world().getTime() + ticks);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0, false, false), player);
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1), player);
		// Wissen hilft beim Finden: ab Stufe 3 bleibt das Ziel auch langsamer
		if (knowledge(player, target) >= 3) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 1), player);
		}
		ctx.world().spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.6), target.getZ(), 20, 0.3, 0.4, 0.3, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.6f);
		return true;
	}

	/** Huschen: schnell und so klein, dass Gegner Grey Matter aus den Augen verlieren. */
	private static boolean scurry(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		int ticks = (int) (ctx.param("seconds", 6.0) * 20);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, ticks, 2, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, ticks, 2, false, false));
		SCURRY.put(player.getUuid(), ctx.world().getTime() + ticks);
		BuiltinAbilities.sound(ctx, SoundEvents.ENTITY_SILVERFISH_STEP, 1.0f, 1.5f);
		return true;
	}

	/** Technikfalle: legt eine Falle aus (hoechstens drei); wer hineintritt, haengt fest und leuchtet. */
	private static boolean techSnare(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		List<Trap> own = TRAPS.stream().filter(t -> t.owner().equals(player.getUuid())).toList();
		if (own.size() >= MAX_TRAPS) {
			TRAPS.remove(own.get(0)); // die aelteste Falle weicht
		}
		TRAPS.add(new Trap(player.getUuid(), ctx.world().getRegistryKey(), player.getPos(), ctx.world().getTime() + 600,
				(int) (ctx.param("seconds", 5.0) * 20)));
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_TRIPWIRE_ATTACH, 1.0f, 1.4f);
		return true;
	}

	/** Notreparatur: Werkzeug und Ruestung werden repariert, Grey Matter und die Gruppe in der Naehe geheilt. */
	private static boolean juryRig(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		double repair = ctx.param("repair", 0.15);
		repair(player.getStackInHand(Hand.MAIN_HAND), repair);
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			repair(player.getEquippedStack(slot), repair);
		}
		float heal = (float) ctx.param("heal", 6.0);
		player.heal(heal);
		for (ServerPlayerEntity ally : ctx.world().getPlayers(p -> p != player && p.squaredDistanceTo(player) <= 36.0
				&& PartyRules.isAlly(player, p))) {
			ally.heal(heal * 0.5f);
			ctx.world().spawnParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getBodyY(0.6), ally.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
		}
		ctx.world().spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.6), player.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.4f);
		return true;
	}

	private static void repair(ItemStack stack, double fraction) {
		if (!stack.isEmpty() && stack.isDamageable()) {
			stack.setDamage(Math.max(0, stack.getDamage() - (int) Math.ceil(stack.getMaxDamage() * fraction)));
		}
	}

	/** Masterplan: alles im Umkreis wird analysiert und geschwaecht, Grey Matter weicht jedem zweiten Angriff aus. */
	private static boolean mastermind(AbilityContext ctx) {
		ServerPlayerEntity player = ctx.player();
		ServerWorld world = ctx.world();
		int ticks = (int) (ctx.param("seconds", 8.0) * 20);
		float damage = (float) ctx.param("damage", 6.0);
		int learned = 0;
		for (LivingEntity target : BuiltinAbilities.livingAround(ctx, ctx.param("radius", 12.0))) {
			int before = knowledge(player, target);
			if (learn(player, target) > before) {
				learned++;
			}
			ANALYZED.put(target.getUuid(), world.getTime() + ticks);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ticks, 0, false, false), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ticks, 1), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ticks, 1), player);
			target.damage(world.getDamageSources().indirectMagic(player, player), damage);
		}
		MASTERMIND.put(player.getUuid(), world.getTime() + ticks);
		if (learned > 0) {
			player.sendMessage(Text.translatable("message.kingdomomnitrix.grey_matter_mastermind", learned).formatted(Formatting.GREEN), true);
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getBodyY(0.5), player.getZ(), 80, 4.0, 1.0, 4.0, 0.1);
		BuiltinAbilities.sound(ctx, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
		return true;
	}

	// --- Tick ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tick = server.getTicks();
		if (tick % 20 == 0) {
			if (!ANALYZED.isEmpty()) {
				ANALYZED.values().removeIf(until -> until < server.getOverworld().getTime() - 20);
			}
			if (!WEAK.isEmpty()) {
				WEAK.values().removeIf(until -> until < server.getOverworld().getTime() - 20);
			}
			tickScurry(server);
		}
		if (!TRAPS.isEmpty() && tick % 2 == 0) {
			tickTraps(server);
		}
	}

	/** Huschen: Gegner, die Grey Matter verfolgen, verlieren ihn haeufig aus den Augen. */
	private static void tickScurry(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Long>> it = SCURRY.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null || !isGreyMatter(player) || player.getServerWorld().getTime() > entry.getValue()) {
				it.remove();
				continue;
			}
			for (MobEntity mob : player.getServerWorld().getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(16.0),
					m -> m.getTarget() == player)) {
				if (player.getRandom().nextFloat() < 0.6f) {
					mob.setTarget(null);
				}
			}
		}
	}

	private static void tickTraps(MinecraftServer server) {
		Iterator<Trap> it = TRAPS.iterator();
		while (it.hasNext()) {
			Trap trap = it.next();
			ServerWorld world = server.getWorld(trap.world());
			ServerPlayerEntity owner = server.getPlayerManager().getPlayer(trap.owner());
			if (world == null || owner == null || world.getTime() > trap.until()) {
				it.remove();
				continue;
			}
			// kleiner Ring aus gruenen Lichtpunkten am Boden
			double angle = world.getTime() * 0.3;
			for (int i = 0; i < 4; i++) {
				double a = angle + i * MathHelper.HALF_PI;
				world.spawnParticles(TECH, trap.pos().x + Math.cos(a) * 0.9, trap.pos().y + 0.1, trap.pos().z + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
			}
			List<LivingEntity> caught = world.getEntitiesByClass(LivingEntity.class, net.minecraft.util.math.Box.of(trap.pos(), 2.4, 2.0, 2.4),
					e -> e != owner && e.isAlive() && PartyRules.canHarm(owner, e));
			if (!caught.isEmpty()) {
				for (LivingEntity target : caught) {
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, trap.rootTicks(), 9), owner);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, trap.rootTicks(), 0), owner);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, trap.rootTicks(), 0), owner);
					target.setVelocity(0.0, Math.min(0.0, target.getVelocity().y), 0.0);
					target.velocityModified = true;
					ANALYZED.put(target.getUuid(), world.getTime() + MARK_TICKS);
				}
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, trap.pos().x, trap.pos().y + 0.4, trap.pos().z, 30, 0.6, 0.4, 0.6, 0.2);
				world.playSound(null, trap.pos().x, trap.pos().y, trap.pos().z, SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 1.2f, 1.6f);
				it.remove();
			}
		}
	}
}
