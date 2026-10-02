# Referenz-Vorderseiten (Alien Evolution)

Diese Bilder sind aus den Render-Vorschauen des Alien-Evolution-Wikis (alienevolution.wiki.gg, Varianten
„Original Series“) abgetastet — Texel für Texel über das Raster des Renders (`Median` je Zelle).

- **Erlaubnis:** laut SANTIQ hat der Autor von Alien Evolution der Nutzung für dieses nicht-kommerzielle
  Fanprojekt zugestimmt (Stand 2026-10-02). Die schriftliche Zusage (Screenshot) gehört zu den Projektunterlagen.
  Ohne diese Zusage dürfen die Dateien nicht verwendet oder weitergegeben werden.
- **Nutzung:** `tools/generate_alien_models.py` überträgt sie per `detail="ref:<alien>/<teil>[@x,y,b,h]"` auf
  die Vorderseite eines Würfels; Seiten und Rücken erzeugt der Generator mit der Farbpalette der Vorlage.

| Alien | Dateien |
|---|---|
| XLR8 | head, torso, pad_r/l, arm_upper, hand, thigh_r/l, shin_r/l, foot_r/l, arm_full; Quelle `source/xlr8.png`, neu erzeugen mit `tools/sample_reference.py xlr8` |
| Heatblast | flame (20×12), head_wide (20×16), head (16×16), collar (16×5), torso (16×34), arm_*_upper (8×20), arm_*_fist (12×20), arm_*_full (12×40), leg_* (8×26), foot_* (10×4); Quelle `source/heatblast.png`, neu erzeugen mit `tools/sample_reference.py heatblast` |
