package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.config.ServerConfig;

import java.util.Locale;

/**
 * Tipos de técnica ki. El enum es solo IDENTIDAD:
 *  - name() = clave NBT / packets.
 *  - el ícono es textures/gui/technique_ki/<id>.png (un PNG por tipo, ya sin atlas ni ordinal);
 *    con {@link #icon()} no vacío, TechniqueIcons usa esa textura propia en su lugar (ver
 *    TechniqueDef.icon, 0.3.0).
 *  - la estela 3D se queda aquí (es visual, no balance).
 * Cada NÚMERO vive en datapack: data/&lt;ns&gt;/zenkai_techniques/ki/&lt;id&gt;.json
 * (ver TechniqueDef / TechniqueManager). Sin JSON, enabled() es false y la técnica
 * no se puede desbloquear, guardar ni disparar.
 * Desde 0.3.0, la FORMA (tamaño de proyectil, radio de explosión, aoe, animación impuesta,
 * efectos admitidos) también es dato — ver el javadoc de {@link TechniqueDef}. El enum sigue
 * siendo Java cerrado a propósito: un addon puede reutilizar/reskinnear un tipo existente
 * (técnica firma) pero no crear una identidad genuinamente nueva sin recompilar Zenkai —
 * decisión explícita, ver {@code .claude/pendiente/verificar-y-deuda.md} §2.
 */
public enum KiTechniqueType {
    WAVE,       // Kamehameha: lento, fuerte
    BLAST,      // bola estándar
    LAZER,      // finísimo y rapidísimo
    SPIRAL,     // trayectoria en espiral
    BIG_BLAST,  // enorme y lento
    BARRIER,    // burbuja defensiva
    BURST,      // ráfaga de bolas pequeñas
    DISK,       // disco destructor
    EXPLOSION,  // autodetonación centrada en el lanzador: no viaja, no apunta
    SPIRIT_BOMB, // Genki Dama: técnica firma de Kaio (ver TechniqueDef "TÉCNICA FIRMA").

    // Técnicas firma nuevas (2026-09-23), TODAS con master="kami" como placeholder — Roshi/
    // Vegeta/Freezer no existen todavía como NPC maestro (ver .claude/pendiente/contenido-futuro.md).
    // Van DESPUÉS de SPIRIT_BOMB a propósito: ordinal() es la clave NBT/packet en algunos sitios.
    KAMEHAMEHA,   // el haz por antonomasia: núcleo blanco limpio, borde azul dentado
    FINAL_FLASH,  // beam ANCHO, núcleo extremadamente brillante, carga con arcos eléctricos
    GALICK_GUN,   // mismo perfil que Kamehameha pero denso/compacto, violeta
    DEATH_BEAM,   // finísimo y preciso, más aún que LAZER — un solo dedo
    DEATH_BALL,   // esfera enorme, oscura al centro, filo crepitante (RIM + partícula "arco")
    SUPERNOVA;    // esfera aún mayor, material de sol sólido (detailStrength alto, no cáscara)

    /** Fracción mínima de carga para poder disparar (estilo DBC). */
    public static final double MIN_CHARGE = 0.25;

    private final String id;

    KiTechniqueType() {
        this.id = name().toLowerCase(Locale.ROOT);
    }

    /** id del datapack (= name() en minúsculas). */
    public String id() { return id; }

    public String nameKey() { return "technique.zenkai." + id; }

    /** Clave de traducción de la descripción corta (tooltip del selector en el editor). */
    public String descKey() { return nameKey() + ".desc"; }

    // ── Naturaleza del tipo ──────────────────────────────────────────────────

    /** ¿El proyectil VIAJA? La barrera sigue al dueño y la explosión estalla donde estás.
     *  Lo que no viaja no apunta, no converge con la mira y no se desvía.
     *  Dato (TechniqueDef.travels, 0.3.0): default true si el tipo está desactivado. */
    /** ¿Rompe bloques al detonar por su propia naturaleza (no por el efecto elegido)? Las técnicas firma
     *  de maestro, que no se editan. La gamerule zenkai_enableKiGriefing lo sigue apagando por completo. */
    public boolean breaksBlocks() { TechniqueDef d = def(); return d != null && d.breaksBlocks(); }

    public boolean travels() { TechniqueDef d = def(); return d == null || d.travels(); }

    /** Animación impuesta por el tipo, o null si el jugador elige el set.
     *  Dato (TechniqueDef.animOverride, 0.3.0): "" = sin imponer. */
    public TechniqueAnimOverride animOverride() {
        TechniqueDef d = def();
        if (d == null || d.animOverride().isEmpty()) return null;
        try {
            return TechniqueAnimOverride.valueOf(d.animOverride().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * ¿Admite este efecto? Dato (TechniqueDef.allowedEffects, 0.3.0): "" = default (todos menos
     * FRAGMENTATION, el mismo comportamiento que tenía la rama por defecto del switch viejo),
     * "none" = ninguno, o una lista separada por comas de nombres de TechniqueEffect.
     */
    public boolean allowsEffect(TechniqueEffect effect) {
        if (effect == TechniqueEffect.NONE) return true;
        TechniqueDef d = def();
        String raw = d == null ? "" : d.allowedEffects();
        if (raw.isEmpty()) return effect != TechniqueEffect.FRAGMENTATION;
        if (raw.equalsIgnoreCase("none")) return false;
        for (String part : raw.split(",")) {
            if (effect.name().equalsIgnoreCase(part.trim())) return true;
        }
        return false;
    }

    // ── Escala física ────────────────────────────────────────────────────────

    /** Diámetro del proyectil en bloques. Un tamaño 5 NO significa lo mismo en un láser que en
     *  un big blast: cada tipo tiene su base y su paso, y por eso esto no puede ser una
     *  fórmula única aplicada fuera.
     *  EXPLOSION y BIG_BLAST están calibrados contra el diámetro VISUAL en pantalla, no este
     *  número crudo: KiVfxProjectileRenderer escala la malla a getBbWidth() * 1.5 (ver
     *  "breathe" en KiVfxProjectileRenderer.render), así que el diámetro que de verdad se ve es
     *  este valor × 1.5. EXPLOSION ancla tamaño 1 a ~1.8 bloques visuales (la altura de un
     *  jugador de pie: 1.2 × 1.5) y crece hasta ~3.9 en tamaño 5 (2.6 × 1.5) — antes tamaño 1
     *  ya eran 3.0 bloques visuales y tamaño 5 llegaba a 9.0, mucho más que "del tamaño de un
     *  jugador y creciendo poco a poco". BIG_BLAST sube el arranque (1.5 × 1.5 = 2.25 visual,
     *  antes 1.8) y suaviza el crecimiento hasta 4.35 en tamaño 5 (antes 5.1). */
    public double projectileSize(int size) {
        int s = Math.max(1, size) - 1;
        TechniqueDef d = def();
        if (d == null) return 0.45 + 0.18 * s; // desactivada: mismo neutro que el resto de getters
        return d.projectileSizeBase() + d.projectileSizeStep() * s;
    }

    /** Cuánto agranda {@code KiVfxProjectileRenderer} la malla más allá del hitbox (ver su
     *  comentario "el cuerpo sobresale del hitbox") — vive aquí, no repetida como número mágico
     *  en cada sitio que necesite saber "qué tan grande se ve esto de verdad" en vez de solo el
     *  tamaño de colisión. */
    private static final double VISUAL_INFLATION = 1.5;

    /** Diámetro REAL en pantalla, no el del hitbox — {@link #projectileSize(int)} × la misma
     *  inflación que ya aplica {@code KiVfxProjectileRenderer.render()}. Cualquier código que
     *  necesite saber cuánto sitio ocupa esto de verdad (por ejemplo, para no solapar dos
     *  técnicas en la fila de "/zenkai debug kivfx spawnall") debe usar esto, no
     *  {@code projectileSize()} a secas — son dos números distintos y ya hubo un solapamiento
     *  real en la fila de depuración por confundirlos. */
    public double visualDiameter(int size) {
        return projectileSize(size) * VISUAL_INFLATION;
    }

    /**
     * ¿La bola de carga anticipa el TAMAÑO REAL del proyectil, en vez del tamaño genérico "de
     * mano"? (Lo consume KiChargeRenderer.targetRadius.)
     * La regla por defecto del mod es que NO: cargando, la energía todavía no tiene forma ni
     * tamaño definitivos, y una esfera de mano se lee bien en las dos vistas. Pero para las
     * técnicas cuyo proyectil es ENORME comparado con una mano, esa esfera pequeña miente
     * sobre lo que estás a punto de soltar — y en una técnica que tarda entre 7 y 19 segundos
     * en cargarse (Genki Dama), ver crecer lo que estás juntando ES la técnica.
     * El criterio para meter un tipo aquí es ese, no "es cara": el proyectil tiene que ser
     * varias veces el tamaño de una mano. Hoy son las tres que ya no salen de una mano.
     * OJO en primera persona: una esfera de radio 2+ pegada a la cámara la envuelve. Quien
     * dibuja resuelve eso apartándola (ver KiChargeRenderer.FP_CLEARANCE), no encogiéndola —
     * encogerla sería justo deshacer lo que esta bandera pide.
     */
    public boolean chargeShowsRealSize() {
        TechniqueDef d = def();
        return d != null && d.chargeShowsRealSize();
    }

    /** Radio de la explosión en bloques. SIN TECHO por defecto a propósito: los techos por tipo
     *  aplastaban la diferencia entre tamaños justo donde tenía que notarse, y las peleas grandes
     *  deben poder ser desproporcionadas. Un servidor que lo pida puede fijar un techo GLOBAL
     *  (no por tipo) con {@code technique.explosion_radius_cap}; 0 lo desactiva. */
    public double explosionRadius(int size) {
        double radius = explosionRadiusMult() * Math.max(1, size);
        double cap = ServerConfig.techniqueExplosionRadiusCap();
        return cap > 0.0 ? Math.min(radius, cap) : radius;
    }

    private double explosionRadiusMult() {
        TechniqueDef d = def();
        return d == null ? 1.20 : d.explosionRadiusMult();
    }

    /** Fracción del daño directo que se reparte en el área, EN EL CENTRO. Ya no depende de la
     *  marca de efecto: cualquier técnica que impacta reparte área. */
    public double aoeFactor() {
        TechniqueDef d = def();
        return d == null ? 0.50 : d.aoeFactor();
    }

    /** Fracción del daño que queda EN EL BORDE del radio. Los proyectiles caen a cero; la
     *  explosión mantiene el 65 %, que es lo que la convierte en una zona de muerte y no en un
     *  golpe con halo. Dato (TechniqueDef.aoeEdgeFalloff, 0.3.0). */
    public double aoeEdgeFalloff() {
        TechniqueDef d = def();
        return d == null ? 0.0 : d.aoeEdgeFalloff();
    }

    /** Parse seguro desde NBT/packets. null si no existe. */
    public static KiTechniqueType byName(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Números (datapack) ──────────────────────────────────────────────────

    public TechniqueDef def() { return TechniqueDef.get(TechniqueDef.Kind.KI, id); }

    /** false = sin JSON: técnica desactivada en cualquier parte. */
    public boolean enabled() { return def() != null; }

    /** TP de desbloqueo. MAX_VALUE si está desactivada (nunca asequible). */
    public int tpCost() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.tpCost(); }

    /** MND mínimo para desbloquear. MAX_VALUE si está desactivada. */
    public int mindReq() { TechniqueDef d = def(); return d == null ? Integer.MAX_VALUE : d.mindReq(); }

    /** Bloques/tick del proyectil. */
    public float speed() { TechniqueDef d = def(); return d == null ? 0f : (float) d.speed(); }

    /** Multiplicador de daño sobre la fórmula base (escala con tamaño). */
    public float damageMult() { TechniqueDef d = def(); return d == null ? 0f : (float) d.damageMult(); }

    /** Multiplicador de coste de ki. */
    public float kiCostMult() { TechniqueDef d = def(); return d == null ? 0f : (float) d.kiCostMult(); }

    /** Proyectiles por disparo (BURST = ráfaga). */
    public int count() { TechniqueDef d = def(); return d == null ? 1 : d.count(); }

    /** BARRIER no dispara: crea la burbuja alrededor del jugador. */
    public boolean defensive() { TechniqueDef d = def(); return d != null && d.defensive(); }

    /** Color inicial de una instancia recién creada. Editable salvo que {@link #master()} no
     *  esté vacío: en ese caso es el color FIJO de la técnica (ver TechniquePacket.handleSave). */
    public int defaultRgb() { TechniqueDef d = def(); return d == null ? 0xFFFFFF : d.defaultRgb(); }

    /** "" = desbloqueable con TP normalmente. Con un id, SOLO ese maestro la enseña — ver
     *  TechniquePacket.handleUnlock y el javadoc de TechniqueDef ("TÉCNICA FIRMA"). */
    public String master() { TechniqueDef d = def(); return d == null ? "" : d.master(); }

    /** "" = usar textures/gui/technique_ki/<id>.png. Con una ResourceLocation propia,
     *  TechniqueIcons la dibuja en su lugar — ver TechniqueDef.icon, campo 0.3.0. */
    public String icon() { TechniqueDef d = def(); return d == null ? "" : d.icon(); }

    /** Segundo color (interior) de fábrica, campo datapack {@code default_rgb2} ("0xRRGGBB"; "" o
     *  ausente = sin segundo color, -1). Impuesto en las técnicas de maestro (KiTechnique.rgb2);
     *  en las normales es solo el punto de partida del editor. */
    public int defaultRgb2() {
        TechniqueDef d = def();
        if (d == null || d.defaultRgb2().isEmpty()) return -1;
        try {
            return Integer.decode(d.defaultRgb2().replace("#", "0x")) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Set de animación que impone una técnica de maestro (campo datapack {@code anim_set}); 0 =
     *  ninguno. Ver KiTechnique.animSet(). */
    public int signatureAnimSet() { TechniqueDef d = def(); return d == null ? 0 : d.animSet(); }

    /** Sonido de carga/disparo que impone una técnica de maestro, o null (sin campo, id mal
     *  escrito o sonido que no es de la familia ki_attack_*). */
    public net.minecraft.resources.ResourceLocation signatureChargeSound() {
        TechniqueDef d = def();
        if (d == null || d.chargeSound().isEmpty()) return null;
        var id = net.minecraft.resources.ResourceLocation.tryParse(d.chargeSound());
        return TechniqueAssets.isValidCharge(id) ? id : null;
    }

    public net.minecraft.resources.ResourceLocation signatureReleaseSound() {
        TechniqueDef d = def();
        if (d == null || d.releaseSound().isEmpty()) return null;
        var id = net.minecraft.resources.ResourceLocation.tryParse(d.releaseSound());
        return TechniqueAssets.isValidRelease(id) ? id : null;
    }

    /** CASTTIME: ticks para cargar al 100%. Se puede soltar desde MIN_CHARGE. */
    public int chargeTicks() { TechniqueDef d = def(); return d == null ? 20 : d.chargeTicks(); }

    /** Enfriamiento POR SLOT tras disparar. */
    public int cooldownTicks() { TechniqueDef d = def(); return d == null ? 20 : d.cooldownTicks(); }

    /** Duración del estado de disparo (RELEASING) para los observadores. */
    public int animTicks() { TechniqueDef d = def(); return d == null ? 20 : d.animTicks(); }
}