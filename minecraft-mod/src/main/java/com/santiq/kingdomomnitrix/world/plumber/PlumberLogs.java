package com.santiq.kingdomomnitrix.world.plumber;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

/**
 * Logbuecher im Wrack (Story): erzaehlen, was die PROCTOR transportierte, warum die Teile getrennt sind, wie man sie
 * zusammensetzt — und wer das Schiff abgeschossen hat. Seiten als Uebersetzungsschluessel (de/en).
 */
public final class PlumberLogs {
	private static final int CAPTAIN_PAGES = 4;
	private static final int ENGINEER_PAGES = 3;

	private PlumberLogs() {
	}

	/** Logbuch des Kapitaens (Cockpit). */
	public static ItemStack captain() {
		return book("book.kingdomomnitrix.plumber_log.captain", "PROCTOR – Log 1", "Cpt. Kaari", CAPTAIN_PAGES);
	}

	/** Notizen des Chefingenieurs (Maschinenraum). */
	public static ItemStack engineer() {
		return book("book.kingdomomnitrix.plumber_log.engineer", "PROCTOR – Log 2", "Eng. Rylla", ENGINEER_PAGES);
	}

	/** Titel und Autor sind im Buchformat feste Zeichenketten (der Server kann nicht uebersetzen); Name und Seiten uebersetzt. */
	private static ItemStack book(String key, String title, String author, int pages) {
		List<RawFilteredPair<Text>> content = new ArrayList<>();
		for (int i = 1; i <= pages; i++) {
			content.add(RawFilteredPair.of(Text.translatable(key + ".page" + i)));
		}
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(RawFilteredPair.of(title), author, 0, content, true));
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable(key));
		return stack;
	}
}
