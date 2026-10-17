package com.santiq.kingdomomnitrix.omnitrix;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Omnitrix OS: jedes Ereignis hat einen Titel in beiden Sprachen; kritische Meldungen haben Vorrang vor Routine. */
class OmnitrixOsTest {
	@Test
	void everyEventHasTitles() throws IOException {
		for (String lang : new String[] {"de_de", "en_us"}) {
			JsonObject json = JsonParser.parseString(Files.readString(
					Path.of("src/main/resources/assets/kingdomomnitrix/lang/" + lang + ".json"))).getAsJsonObject();
			for (OmnitrixOs.Event event : OmnitrixOs.Event.values()) {
				assertTrue(json.has(event.titleKey()), lang + ": Titel fehlt fuer " + event);
			}
		}
	}

	@Test
	void criticalBeatsRoutine() {
		assertTrue(OmnitrixOs.Event.OVERHEAT.priority() > OmnitrixOs.Event.TIMEOUT.priority(), "Ueberhitzung muss Zeitende verdraengen");
		assertTrue(OmnitrixOs.Event.EMERGENCY.priority() > OmnitrixOs.Event.TRANSFORMED.priority(), "Notfall muss Verwandlung verdraengen");
		assertTrue(OmnitrixOs.Event.DNA_SHOCK.priority() > OmnitrixOs.Event.TIMEOUT.priority(), "DNA-Schock muss Zeitende verdraengen");
		assertTrue(OmnitrixOs.Event.MALFUNCTION.priority() > OmnitrixOs.Event.TRANSFORMED.priority(), "Fehlfunktion muss Verwandlung verdraengen");
	}
}
