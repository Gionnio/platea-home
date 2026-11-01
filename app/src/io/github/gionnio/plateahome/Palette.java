package io.github.gionnio.plateahome;

/** I colori del launcher. Neutri: i colori li portano le icone delle app. */
final class Palette {
    final int ground, surface, tile, line, text, sec, ter, focus, onFocus;

    private Palette(int ground, int surface, int tile, int line, int text, int sec, int ter, int focus, int onFocus) {
        this.ground = ground; this.surface = surface; this.tile = tile; this.line = line;
        this.text = text; this.sec = sec; this.ter = ter; this.focus = focus; this.onFocus = onFocus;
    }

    static Palette of(String name) {
        if ("Notte blu".equals(name))
            return new Palette(0xFF0B1020, 0xFF151B2E, 0xFF1E2640, 0xFF26304A, 0xFFEEF2FF, 0xFFAAB4CF, 0xFF7C87A6, 0xFFFFFFFF, 0xFF0B1020);
        if ("Giorno".equals(name))
            return new Palette(0xFFECEDEF, 0xFFFFFFFF, 0xFFF1F2F4, 0xFFD6D8DD, 0xFF15171B, 0xFF454A54, 0xFF5D636E, 0xFF15171B, 0xFFFFFFFF);
        if ("Nero".equals(name))
            return new Palette(0xFF000000, 0xFF101114, 0xFF1A1C20, 0xFF3A3D44, 0xFFE6E7EA, 0xFF9599A2, 0xFF80858F, 0xFFFFFFFF, 0xFF000000);
        return new Palette(0xFF0F1013, 0xFF1A1C20, 0xFF24262B, 0xFF2B2E35, 0xFFF3F4F6, 0xFFB4B8C0, 0xFF80858F, 0xFFFFFFFF, 0xFF0F1013);
    }
}
