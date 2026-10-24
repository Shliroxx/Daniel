package com.santiq.kingdomomnitrix.alien;

import com.mojang.serialization.Codec;
import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.omnitrix.OmnitrixCore;
import com.santiq.kingdomomnitrix.player.HeroData;
import com.santiq.kingdomomnitrix.player.HeroDataAccess;
import com.santiq.kingdomomnitrix.registry.ModSounds;
import java.util.ArrayList;
import java.util.Comparator;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * DNA-Scan: Wie Aliens ins Omnitrix kommen. Zu Beginn sind nur die Start-Aliens (Heatblast, Vierarm, XLR8) gespeichert;
 * alle anderen muss man scannen — auf der Welt, aus der ihre DNA stammt ({@link AlienDefinition.DnaInfo}).
 *
 * <p>Ablauf: Scan-Taste halten und ein Lebewesen anvisieren (hoechstens {@value #RANGE} Bloecke, freie Sicht, nicht
 * verwandelt, Omnitrix im Inventar). Nach {@value #SCAN_TICKS} Ticks ist eine Probe genommen; jedes Einzelwesen zaehlt
 * nur einmal. Mit {@code samples} Proben ist das Alien entschluesselt. Der Client schickt waehrend des Haltens jeden Tick
 * das Ziel; der Server prueft alles selbst (Reichweite, Sicht, Welt, Art) und zaehlt.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class DnaScan {
	public static final int SCAN_TICKS = 40;
	public static final double RANGE = 16.0;
	/** Story-Flag: Start-Aliens wurden vergeben */
	public static final String STARTER_FLAG = "dna_starters";
	private static final Codec<Map<Identifier, Integer>> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, 100));
	/** Proben je noch nicht entschluesseltem Alien (dauerhaft, an den Besitzer synchronisiert: Alien-Seite zeigt sie) */
	public static final AttachmentType<Map<Identifier, Integer>> PROGRESS = AttachmentRegistry.create(KingdomOmnitrix.id("dna_progress"),
			builder -> builder.persistent(CODEC).initializer(Map::of).copyOnDeath()
					.syncWith(PacketCodecs.codec(CODEC), AttachmentSyncPredicate.targetOnly()));
	/** Abstand zwischen zwei Scan-Paketen, ab dem der Scan als abgebrochen gilt (Ticks) */
	private static final int GAP_TICKS = 5;
	private static final int SCANNED_LIMIT = 512;
	private static final DustParticleEffect BEAM = new DustParticleEffect(new Vector3f(0.3f, 1.0f, 0.2f), 0.5f);
	/** Anzeigenamen der Welten (feste Schluessel, keine zusammengesetzten) */
	private static final Map<Identifier, String> WORLD_NAMES = Map.ofEntries(
			Map.entry(KingdomOmnitrix.id("sonorosia"), "planet.kingdomomnitrix.sonorosia"),
			Map.entry(KingdomOmnitrix.id("petropia"), "planet.kingdomomnitrix.petropia"),
			Map.entry(KingdomOmnitrix.id("galvan_prime"), "planet.kingdomomnitrix.galvan_prime"),
			Map.entry(KingdomOmnitrix.id("galvan_b"), "planet.kingdomomnitrix.galvan_b"),
			Map.entry(KingdomOmnitrix.id("kylmyys"), "planet.kingdomomnitrix.kylmyys"),
			Map.entry(KingdomOmnitrix.id("vulpin"), "planet.kingdomomnitrix.vulpin"),
			Map.entry(KingdomOmnitrix.id("piscciss"), "planet.kingdomomnitrix.piscciss"),
			Map.entry(KingdomOmnitrix.id("arburia"), "planet.kingdomomnitrix.arburia"),
			Map.entry(KingdomOmnitrix.id("lepidopterra"), "planet.kingdomomnitrix.lepidopterra"),
			Map.entry(KingdomOmnitrix.id("anur_phaetos"), "planet.kingdomomnitrix.anur_phaetos"),
			Map.entry(KingdomOmnitrix.id("appoplexia"), "planet.kingdomomnitrix.appoplexia"),
			Map.entry(KingdomOmnitrix.id("aranhaschimmia"), "planet.kingdomomnitrix.aranhaschimmia"),
			Map.entry(KingdomOmnitrix.id("terradino"), "planet.kingdomomnitrix.terradino"),
			Map.entry(KingdomOmnitrix.id("aeropela"), "planet.kingdomomnitrix.aeropela"),
			Map.entry(KingdomOmnitrix.id("methanos"), "planet.kingdomomnitrix.methanos"),
			Map.entry(KingdomOmnitrix.id("viscosia"), "planet.kingdomomnitrix.viscosia"),
			Map.entry(KingdomOmnitrix.id("encephalonus"), "planet.kingdomomnitrix.encephalonus"),
			Map.entry(Identifier.ofVanilla("overworld"), "planet.kingdomomnitrix.earth"),
			Map.entry(Identifier.ofVanilla("the_nether"), "dimension.minecraft.the_nether"),
			Map.entry(Identifier.ofVanilla("the_end"), "dimension.minecraft.the_end"),
			Map.entry(KingdomOmnitrix.id("veldin"), "planet.kingdomomnitrix.veldin"),
			Map.entry(KingdomOmnitrix.id("kerwan"), "planet.kingdomomnitrix.kerwan"),
			Map.entry(KingdomOmnitrix.id("rilgar"), "planet.kingdomomnitrix.rilgar"),
			Map.entry(KingdomOmnitrix.id("novalis"), "planet.kingdomomnitrix.novalis"),
			Map.entry(KingdomOmnitrix.id("torren_iv"), "planet.kingdomomnitrix.torren_iv"),
			Map.entry(KingdomOmnitrix.id("traverse_town"), "planet.kingdomomnitrix.traverse_town"));

	/** laufender Scan je Spieler */
	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	/** schon gescannte Einzelwesen je Spieler (jedes zaehlt nur einmal), begrenzt */
	private static final Map<UUID, Set<UUID>> SCANNED = new HashMap<>();

	private static final class Session {
		private final int entityId;
		private int ticks;
		private long lastTick;
		/** Ziel taugt nicht (Meldung schon gezeigt) oder Probe schon genommen */
		private boolean finished;

		private Session(int entityId, long now) {
			this.entityId = entityId;
			this.lastTick = now;
		}
	}

	/** Ergebnis der Pruefung eines Ziels. */
	private record Verdict(Optional<Identifier> alien, Text reason) {
	}

	private DnaScan() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 == 0) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					grantStarters(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.getPlayer().getUuid()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SESSIONS.clear();
			SCANNED.clear();
		});
	}

	/** Start-Aliens einmalig vergeben, sobald der Spieler ein Omnitrix besitzt. */
	public static void grantStarters(ServerPlayerEntity player) {
		if (HeroDataAccess.get(player).hasFlag(STARTER_FLAG) || !OmnitrixItem.hasOmnitrix(player)) {
			return;
		}
		Optional<Registry<AlienDefinition>> registry = AlienRegistry.registry(player.getServer().getRegistryManager());
		if (registry.isEmpty()) {
			return;
		}
		List<Identifier> starters = new ArrayList<>();
		for (Identifier id : registry.get().getIds()) {
			AlienDefinition alien = registry.get().get(id);
			if (alien != null && alien.dna().starter()) {
				starters.add(id);
			}
		}
		starters.sort(Comparator.comparingInt(id -> registry.get().get(id).sortOrder()));
		HeroDataAccess.update(player, data -> {
			HeroData next = data.withFlag(STARTER_FLAG, true);
			for (Identifier id : starters) {
				next = next.unlockAlien(id);
			}
			return next;
		});
		if (!starters.isEmpty()) {
			MutableText names = Text.empty();
			for (int i = 0; i < starters.size(); i++) {
				names.append(i == 0 ? Text.empty() : Text.literal(", ")).append(TransformationManager.alienName(starters.get(i)));
			}
			player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_starters", names).formatted(Formatting.GREEN), false);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_scan_hint").formatted(Formatting.GRAY), false);
			OmnitrixCore.onUnlock(player);
		}
	}

	/** Scan-Paket: {@code entityId} = Ziel, solange die Taste gehalten wird; negativ = losgelassen. */
	public static void onScan(ServerPlayerEntity player, int entityId) {
		long now = player.getServerWorld().getTime();
		if (entityId < 0) {
			SESSIONS.remove(player.getUuid());
			return;
		}
		Entity found = player.getServerWorld().getEntityById(entityId);
		if (!(found instanceof LivingEntity target) || !target.isAlive() || target == player || !OmnitrixItem.hasOmnitrix(player)
				|| TransformationManager.get(player).isTransformed() || player.squaredDistanceTo(target) > RANGE * RANGE || !player.canSee(target)) {
			SESSIONS.remove(player.getUuid());
			return;
		}
		Session session = SESSIONS.get(player.getUuid());
		if (session == null || session.entityId != entityId || now - session.lastTick > GAP_TICKS) {
			session = new Session(entityId, now);
			SESSIONS.put(player.getUuid(), session);
			Verdict verdict = judge(player, target);
			if (verdict.alien().isEmpty()) {
				session.finished = true;
				player.sendMessage(verdict.reason(), true);
				player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.OMNITRIX_ERROR,
						SoundCategory.PLAYERS, 0.5f, 1.0f);
				return;
			}
			player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.OMNITRIX_ACTIVATE,
					SoundCategory.PLAYERS, 0.6f, 1.6f);
		}
		session.lastTick = now;
		if (session.finished) {
			return;
		}
		session.ticks++;
		beam(player, target, session.ticks);
		if (session.ticks >= SCAN_TICKS) {
			session.finished = true;
			// erneut pruefen: waehrend des Scans koennte schon ein Mitspieler-Ereignis etwas geaendert haben
			judge(player, target).alien().ifPresent(alien -> sample(player, target, alien));
		}
	}

	/** Welches Alien diese Probe voranbringt — oder warum keines. */
	private static Verdict judge(ServerPlayerEntity player, LivingEntity target) {
		Optional<Registry<AlienDefinition>> registry = AlienRegistry.registry(player.getServer().getRegistryManager());
		if (registry.isEmpty()) {
			return new Verdict(Optional.empty(), Text.translatable("message.kingdomomnitrix.dna_scan.none").formatted(Formatting.GRAY));
		}
		if (SCANNED.getOrDefault(player.getUuid(), Set.of()).contains(target.getUuid())) {
			return new Verdict(Optional.empty(), Text.translatable("message.kingdomomnitrix.dna_scan.same").formatted(Formatting.GRAY));
		}
		Identifier type = Registries.ENTITY_TYPE.getId(target.getType());
		Identifier world = player.getWorld().getRegistryKey().getValue();
		Identifier wild = target instanceof WildAlienEntity alien ? alien.alien().orElse(null) : null;
		Match match = match(registry.get(), HeroDataAccess.get(player), type, wild, world);
		if (match.alien() != null) {
			return new Verdict(Optional.of(match.alien()), Text.empty());
		}
		if (match.elsewhereAlien() != null) {
			// DNA passt, stammt aber von einer anderen Welt: Hinweis, wohin die Reise geht
			return new Verdict(Optional.empty(), Text.translatable("message.kingdomomnitrix.dna_scan.elsewhere",
					TransformationManager.alienName(match.elsewhereAlien()), worldName(match.elsewhere())).formatted(Formatting.YELLOW));
		}
		return new Verdict(Optional.empty(), Text.translatable(match.known() ? "message.kingdomomnitrix.dna_scan.known"
				: "message.kingdomomnitrix.dna_scan.none").formatted(Formatting.GRAY));
	}

	/**
	 * Was eine Lebewesen-Art in dieser Welt fuer den Spieler hergibt: {@code alien} = Probe zaehlt fuer dieses Alien;
	 * sonst ggf. die Welt, auf der es stabile Proben gibt, oder {@code known} = DNA schon gespeichert. Auch vom Client
	 * genutzt (Scan-Anzeige), dort mit dem synchronisierten Heldenstand.
	 */
	public record Match(Identifier alien, Identifier elsewhere, Identifier elsewhereAlien, boolean known) {
	}

	public static Match match(Registry<AlienDefinition> registry, HeroData data, Identifier type, Identifier wild, Identifier world) {
		if (wild != null) {
			// wildes Alien: genau seine Art
			AlienDefinition alien = registry.get(wild);
			if (alien == null || alien.dna().species().isEmpty()) {
				return new Match(null, null, null, false);
			}
			return data.hasAlien(wild) ? new Match(null, null, null, true) : new Match(wild, null, null, false);
		}
		List<Identifier> ids = new ArrayList<>(registry.getIds());
		ids.sort(Comparator.comparingInt(id -> registry.get(id).sortOrder()));
		Identifier elsewhere = null;
		Identifier elsewhereAlien = null;
		boolean known = false;
		for (Identifier id : ids) {
			AlienDefinition alien = registry.get(id);
			if (alien == null) {
				continue;
			}
			if (alien.dna().species().isPresent()) {
				// Arten mit Heimatwelt: nur das wilde Alien selbst liefert DNA (s. o.)
				continue;
			}
			for (AlienDefinition.DnaSource source : alien.dna().sources()) {
				if (!source.entity().equals(type)) {
					continue;
				}
				if (data.hasAlien(id)) {
					known = true;
				} else if (source.matches(type, world)) {
					return new Match(id, null, null, known);
				} else if (elsewhereAlien == null) {
					elsewhere = source.dimension().orElse(null);
					elsewhereAlien = id;
				}
			}
		}
		return new Match(null, elsewhere, elsewhereAlien, known);
	}

	/** Probe zaehlen; mit der letzten ist das Alien entschluesselt. */
	private static void sample(ServerPlayerEntity player, LivingEntity target, Identifier alienId) {
		Set<UUID> seen = SCANNED.computeIfAbsent(player.getUuid(), id -> new LinkedHashSet<>());
		seen.add(target.getUuid());
		if (seen.size() > SCANNED_LIMIT) {
			Iterator<UUID> it = seen.iterator();
			it.next();
			it.remove();
		}
		ServerWorld world = player.getServerWorld();
		Vec3d at = target.getPos().add(0, target.getHeight() * 0.5, 0);
		world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 16, 0.4, 0.5, 0.4, 0.05);
		addSample(player, alienId);
	}

	/**
	 * Eine Probe fuer ein Alien gutschreiben (Scan oder DNA-Probe aus Belohnungen); liefert true, wenn es damit
	 * entschluesselt ist.
	 */
	public static boolean addSample(ServerPlayerEntity player, Identifier alienId) {
		Optional<AlienDefinition> alien = AlienRegistry.get(player.getServer().getRegistryManager(), alienId);
		if (alien.isEmpty() || HeroDataAccess.get(player).hasAlien(alienId)) {
			return false;
		}
		int needed = alien.get().dna().starter() ? 1 : alien.get().dna().samples();
		int have = progress(player, alienId) + 1;
		ServerWorld world = player.getServerWorld();
		if (have >= needed) {
			Map<Identifier, Integer> map = new HashMap<>(player.getAttachedOrElse(PROGRESS, Map.of()));
			map.remove(alienId);
			player.setAttached(PROGRESS, Map.copyOf(map));
			HeroDataAccess.update(player, data -> data.unlockAlien(alienId));
			OmnitrixCore.onUnlock(player);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
			world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.5, 0.7, 0.5, 0.1);
			player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_unlocked", TransformationManager.alienName(alienId))
					.formatted(Formatting.GREEN), false);
			return true;
		}
		Map<Identifier, Integer> map = new HashMap<>(player.getAttachedOrElse(PROGRESS, Map.of()));
		map.put(alienId, have);
		player.setAttached(PROGRESS, Map.copyOf(map));
		world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.OMNITRIX_CONFIRM, SoundCategory.PLAYERS, 0.8f, 1.2f);
		player.sendMessage(Text.translatable("message.kingdomomnitrix.dna_scan.sample", TransformationManager.alienName(alienId), have, needed)
				.formatted(Formatting.AQUA), true);
		return false;
	}

	/** Bisherige Proben fuer ein Alien (auf dem Client: synchronisierter Stand). */
	public static int progress(net.minecraft.entity.player.PlayerEntity player, Identifier alienId) {
		return player.getAttachedOrElse(PROGRESS, Map.of()).getOrDefault(alienId, 0);
	}

	/** Anzeigename einer Welt (Planet oder Dimension). */
	public static Text worldName(Identifier world) {
		String key = WORLD_NAMES.get(world);
		return key != null ? Text.translatable(key) : Text.literal(world.toString());
	}

	/** Scanstrahl vom Omnitrix (rechte Hand) zum Ziel, das Ziel leuchtet kurz auf; dazu ein steigender Piepton. */
	private static void beam(ServerPlayerEntity player, LivingEntity target, int ticks) {
		ServerWorld world = player.getServerWorld();
		if (ticks % 2 == 0) {
			Vec3d look = player.getRotationVec(1.0f);
			Vec3d side = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
			Vec3d from = player.getEyePos().add(look.multiply(0.5)).add(side.multiply(0.35)).add(0, -0.45, 0);
			Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0);
			int steps = Math.max(4, (int) (from.distanceTo(to) * 3));
			for (int i = 0; i <= steps; i++) {
				Vec3d p = from.lerp(to, i / (double) steps);
				world.spawnParticles(BEAM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			// Abtast-Ring wandert am Ziel von unten nach oben
			double ring = target.getY() + target.getHeight() * ((ticks % 20) / 20.0);
			for (int i = 0; i < 10; i++) {
				double a = i / 10.0 * Math.PI * 2;
				double r = target.getWidth() * 0.7 + 0.2;
				world.spawnParticles(BEAM, target.getX() + Math.cos(a) * r, ring, target.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		if (ticks % 8 == 1) {
			world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.OMNITRIX_NAVIGATE, SoundCategory.PLAYERS, 0.5f,
					0.8f + ticks / (float) SCAN_TICKS);
		}
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 6, 0, false, false, false));
	}

	/** Server-Stand fuer Tests und Befehle: Proben-Zaehler zuruecksetzen. */
	public static void reset(ServerPlayerEntity player) {
		player.setAttached(PROGRESS, Map.of());
		SCANNED.remove(player.getUuid());
	}
}
