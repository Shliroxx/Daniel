package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/**
 * Eigene Sound-Ereignisse. Die Klaenge erzeugt {@code tools/generate_sounds.py} (Synthese, rechtefrei),
 * die Zuordnung steht in {@code assets/kingdomomnitrix/sounds.json}. Lautstaerke ueber die Minecraft-Kategorien
 * (Spieler, Feindselige Kreaturen, Bloecke …), die beim Abspielen gewaehlt werden.
 */
public final class ModSounds {
	// Omnitrix & Aliens
	public static final SoundEvent OMNITRIX_TRANSFORM = register("omnitrix.transform");
	public static final SoundEvent OMNITRIX_REVERT = register("omnitrix.revert");
	public static final SoundEvent OMNITRIX_BEEP = register("omnitrix.beep");
	public static final SoundEvent OMNITRIX_SELECT = register("omnitrix.select");
	public static final SoundEvent OMNITRIX_ACTIVATE = register("omnitrix.activate");
	public static final SoundEvent OMNITRIX_OPEN = register("omnitrix.open");
	public static final SoundEvent OMNITRIX_NAVIGATE = register("omnitrix.navigate");
	public static final SoundEvent OMNITRIX_CONFIRM = register("omnitrix.confirm");
	public static final SoundEvent OMNITRIX_CANCEL = register("omnitrix.cancel");
	public static final SoundEvent OMNITRIX_ERROR = register("omnitrix.error");
	public static final SoundEvent OMNITRIX_WARNING = register("omnitrix.warning");
	public static final SoundEvent OMNITRIX_OVERHEAT = register("omnitrix.overheat");
	public static final SoundEvent OMNITRIX_READY = register("omnitrix.ready");
	public static final SoundEvent OMNITRIX_UNLOCK = register("omnitrix.unlock");
	public static final SoundEvent OMNITRIX_LOCK = register("omnitrix.lock");
	public static final SoundEvent OMNITRIX_MASTER_CONTROL = register("omnitrix.master_control");
	public static final SoundEvent OMNITRIX_EMERGENCY = register("omnitrix.emergency");
	public static final SoundEvent OMNITRIX_DNA_SHOCK = register("omnitrix.dna_shock");
	public static final SoundEvent ALIEN_FIRE = register("alien.fire");
	public static final SoundEvent ALIEN_SLAM = register("alien.slam");
	public static final SoundEvent ALIEN_DASH = register("alien.dash");
	public static final SoundEvent ALIEN_CRYSTAL = register("alien.crystal");
	// Kampf & Magie
	public static final SoundEvent COMBAT_SWING = register("combat.swing");
	public static final SoundEvent COMBAT_HIT = register("combat.hit");
	public static final SoundEvent COMBAT_FINISHER = register("combat.finisher");
	public static final SoundEvent COMBAT_GUARD = register("combat.guard");
	public static final SoundEvent COMBAT_DODGE = register("combat.dodge");
	public static final SoundEvent MAGIC_FIRE = register("magic.fire");
	public static final SoundEvent MAGIC_BLIZZARD = register("magic.blizzard");
	public static final SoundEvent MAGIC_THUNDER = register("magic.thunder");
	public static final SoundEvent MAGIC_CURE = register("magic.cure");
	public static final SoundEvent MAGIC_MP_EMPTY = register("magic.mp_empty");
	public static final SoundEvent HERO_LEVEL_UP = register("hero.level_up");
	public static final SoundEvent HERO_DISCOVERY = register("hero.discovery");
	// Technik & Welten
	public static final SoundEvent WEAPON_COMBUSTER = register("weapon.combuster");
	public static final SoundEvent WEAPON_EMPTY = register("weapon.empty");
	public static final SoundEvent WEAPON_THROW = register("weapon.throw");
	public static final SoundEvent WEAPON_BOLT = register("weapon.bolt");
	public static final SoundEvent WEAPON_BUY = register("weapon.buy");
	public static final SoundEvent GADGET_HELI = register("gadget.heli");
	public static final SoundEvent GADGET_JET = register("gadget.jet");
	public static final SoundEvent GADGET_SWINGSHOT = register("gadget.swingshot");
	public static final SoundEvent GADGET_ATTACH = register("gadget.attach");
	public static final SoundEvent SHIP_LAUNCH = register("ship.launch");
	public static final SoundEvent SHIP_AI = register("ship.ai");
	public static final SoundEvent WORLD_RIFT = register("world.rift");
	public static final SoundEvent ARENA_ROUND = register("arena.round");
	public static final SoundEvent ARENA_VICTORY = register("arena.victory");
	// Gegner & Boss
	public static final SoundEvent HEARTLESS_AMBIENT = register("heartless.ambient");
	public static final SoundEvent HEARTLESS_HURT = register("heartless.hurt");
	public static final SoundEvent HEARTLESS_DEATH = register("heartless.death");
	public static final SoundEvent HEARTLESS_SPAWN = register("heartless.spawn");
	public static final SoundEvent BOSS_LASER_CHARGE = register("boss.laser_charge");
	public static final SoundEvent BOSS_LASER_FIRE = register("boss.laser_fire");
	public static final SoundEvent BOSS_ROCKET = register("boss.rocket");
	public static final SoundEvent BOSS_STOMP = register("boss.stomp");
	public static final SoundEvent BOSS_OVERLOAD = register("boss.overload");
	public static final SoundEvent BOSS_HURT = register("boss.hurt");
	public static final SoundEvent BOSS_DEATH = register("boss.death");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		return Registry.register(Registries.SOUND_EVENT, KingdomOmnitrix.id(name), SoundEvent.of(KingdomOmnitrix.id(name)));
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Sounds registriert");
	}
}
