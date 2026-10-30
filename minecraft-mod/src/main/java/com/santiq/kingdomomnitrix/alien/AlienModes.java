package com.santiq.kingdomomnitrix.alien;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.party.PartyRules;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Aktiver Modus je Spieler ({@link AlienMode}): Server-Zustand, an alle Spieler synchronisiert — der Renderer faehrt das
 * Bauteil (Kristallschwert, Schild …) mit der AE-Animation aus und ein. Werte als Attribut-Modifier mit festen IDs,
 * Immunitaeten im Schadens-Check, Stachel-Aura im Verwandlungs-Tick, Schwung beim Nahkampf. Zeitlich begrenzte Modi
 * enden von selbst; jeder Modus endet beim Zurueckverwandeln oder Wechsel des Aliens.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AlienModes {
	/** ID des aktiven Modus, leer = keiner */
	public static final AttachmentType<String> MODE = AttachmentRegistry.create(KingdomOmnitrix.id("alien_mode"), builder -> builder
			.initializer(() -> "")
			.syncWith(PacketCodecs.STRING, AttachmentSyncPredicate.all()));
	private static final int AURA_INTERVAL = 10;
	/** Ende zeitlich begrenzter Modi (Welt-Tick) */
	private static final Map<UUID, Long> EXPIRES = new HashMap<>();

	private AlienModes() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClient() && player instanceof ServerPlayerEntity server && entity instanceof LivingEntity target) {
				active(server).filter(mode -> mode.sweep() > 0.0f).ifPresent(mode -> sweep(server, target, mode));
			}
			return ActionResult.PASS;
		});
	}

	/** ID des aktiven Modus (Client und Server), leer = keiner. */
	public static String id(PlayerEntity player) {
		String id = player.getAttached(MODE);
		return id == null ? "" : id;
	}

	/** Aktiver Modus des verwandelten Spielers, sofern sein Alien ihn kennt (Client und Server). */
	public static Optional<AlienMode> active(PlayerEntity player) {
		String id = id(player);
		if (id.isEmpty()) {
			return Optional.empty();
		}
		return TransformationManager.activeDefinition(player).flatMap(alien -> alien.traits().mode(id));
	}

	/**
	 * Modus umschalten: an, wenn er aus ist (ein anderer Modus endet), sonst aus.
	 *
	 * @param ticks Dauer, 0 = bis zum naechsten Umschalten
	 * @return true, wenn der Modus jetzt an ist
	 */
	public static boolean toggle(ServerPlayerEntity player, String id, int ticks) {
		if (id(player).equals(id)) {
			Optional<AlienMode> mode = active(player);
			clear(player);
			mode.ifPresent(m -> player.sendMessage(Text.translatable("message.kingdomomnitrix.mode_off", Text.translatable(m.name()))
					.formatted(Formatting.GRAY), true));
			return false;
		}
		return set(player, id, ticks);
	}

	/** Modus einschalten (ein anderer endet); false, wenn das aktive Alien ihn nicht hat. */
	public static boolean set(ServerPlayerEntity player, String id, int ticks) {
		Optional<AlienMode> mode = TransformationManager.activeDefinition(player).flatMap(alien -> alien.traits().mode(id));
		if (mode.isEmpty()) {
			KingdomOmnitrix.LOGGER.warn("Alien von {} hat keinen Modus {}", player.getName().getString(), id);
			return false;
		}
		removeModifiers(player);
		player.setAttached(MODE, id);
		if (ticks > 0) {
			EXPIRES.put(player.getUuid(), player.getServerWorld().getTime() + ticks);
		} else {
			EXPIRES.remove(player.getUuid());
		}
		AlienMode m = mode.get();
		for (int i = 0; i < m.attributes().size(); i++) {
			AlienDefinition.AttributeBonus bonus = m.attributes().get(i);
			EntityAttributeInstance instance = player.getAttributeInstance(bonus.attribute());
			if (instance != null) {
				instance.addTemporaryModifier(new EntityAttributeModifier(modifierId(i), bonus.amount(), bonus.operation()));
			}
		}
		player.sendMessage(Text.translatable("message.kingdomomnitrix.mode_on", Text.translatable(m.name())).formatted(Formatting.AQUA), true);
		return true;
	}

	/** Modus beenden (Zurueckverwandeln, Wechsel, Ablauf). */
	public static void clear(ServerPlayerEntity player) {
		EXPIRES.remove(player.getUuid());
		if (id(player).isEmpty()) {
			return;
		}
		removeModifiers(player);
		player.setAttached(MODE, "");
	}

	/** Je Tick waehrend der Verwandlung: Ablauf, Gueltigkeit, Stachel-Aura. */
	public static void tick(ServerPlayerEntity player, AlienDefinition alien, long now) {
		String id = id(player);
		if (id.isEmpty()) {
			return;
		}
		Optional<AlienMode> mode = alien.traits().mode(id);
		Long until = EXPIRES.get(player.getUuid());
		if (mode.isEmpty() || until != null && now >= until) {
			clear(player);
			return;
		}
		AlienMode m = mode.get();
		if (m.auraDamage() > 0.0f && m.auraRadius() > 0.0f && now % AURA_INTERVAL == 0) {
			ServerWorld world = player.getServerWorld();
			double r = m.auraRadius();
			for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(r),
					e -> e != player && e.isAlive() && PartyRules.canHarm(player, e))) {
				near.damage(world.getDamageSources().thorns(player), m.auraDamage());
			}
		}
	}

	/** Schadens-Check: wirkt diese Schadensart im aktiven Modus nicht? */
	public static boolean isImmune(ServerPlayerEntity player, DamageSource source) {
		return active(player).map(mode -> mode.isImmuneTo(source)).orElse(false);
	}

	public static void disconnect(ServerPlayerEntity player) {
		EXPIRES.remove(player.getUuid());
	}

	/** Schwung: Gegner im Halbkreis vor dem getroffenen Ziel bekommen einen Anteil des Angriffsschadens. */
	private static void sweep(ServerPlayerEntity player, LivingEntity hit, AlienMode mode) {
		ServerWorld world = player.getServerWorld();
		float damage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * mode.sweep();
		Vec3d look = player.getRotationVec(1.0f).multiply(1, 0, 1).normalize();
		double r = mode.sweepRadius();
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, hit.getBoundingBox().expand(r, 1.0, r),
				e -> e != player && e != hit && e.isAlive() && PartyRules.canHarm(player, e))) {
			Vec3d to = near.getPos().subtract(player.getPos()).multiply(1, 0, 1);
			if (to.lengthSquared() > 1.0e-4 && to.normalize().dotProduct(look) > 0.2) {
				near.damage(world.getDamageSources().playerAttack(player), damage);
				near.takeKnockback(0.4, -look.x, -look.z);
			}
		}
	}

	private static void removeModifiers(ServerPlayerEntity player) {
		for (var entry : net.minecraft.registry.Registries.ATTRIBUTE.streamEntries().toList()) {
			EntityAttributeInstance instance = player.getAttributeInstance(entry);
			if (instance == null) {
				continue;
			}
			for (int i = 0; i < AlienMode.MAX_ATTRIBUTES; i++) {
				instance.removeModifier(modifierId(i));
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static Identifier modifierId(int index) {
		return KingdomOmnitrix.id("alien_mode_" + index);
	}
}
