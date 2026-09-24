package com.hmc.zenkai.client.render_and_model_entities.kivfx;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Modo de diagnóstico del VFX de ki, SOLO cliente. Reemplaza al método "a ciegas" (probar un
 * cambio, mirar en juego, adivinar si el problema es geometría/shader/partículas) por aislamiento
 * real: cada modo apaga TODO menos la capa que se quiere inspeccionar, así que un artefacto solo
 * puede venir de la capa que sigue encendida.
 *
 * NO son teclas F1-F12: la propia mod ya ocupa V/C/Z/H/F4/R/X/Alt/Tab (ver KeyBindings), y F1-F12
 * chocan con vainilla (F1 oculta el HUD, F3 el depurador, F5 la cámara, F11 pantalla completa) —
 * exactamente el tipo de colisión que un modo de depuración NO debe añadir. Un solo bind
 * (KeyBindings.KI_VFX_DEBUG_CYCLE) va RECORRIENDO esta lista y anuncia el modo activo en la
 * action bar, así no hace falta memorizar qué tecla es cada capa.
 *
 * Los métodos "showsX()" son lo que consultan los renderers — nunca un `switch` disperso por
 * cada uno: añadir un modo nuevo solo toca esta clase.
 */
public enum KiVfxDebugMode {
    /** Todo el VFX, tal cual lo ve un jugador. */
    NORMAL,
    /** Solo cáscara + envolvente. Aísla si un artefacto viene del núcleo/ribbons/partículas. */
    SHELL_ONLY,
    /** Solo el núcleo explícito (ver KiVfxProfile.Core). Confirma que su ancho en pantalla es
     *  siempre el de su propio radio, nunca un ángulo de vista. */
    CORE_ONLY,
    /** Solo estela + rayos radiales. Aísla el sistema de cintas en espacio de mundo. */
    RIBBONS_ONLY,
    /** Cáscara con el hervor (ZenkaiWobble) y el parpadeo forzados a cero. Si el artefacto
     *  desaparece aquí, era del CAMPO PROCEDURAL (ki_energy.fsh), no de la geometría. */
    FLAT_BANDS,
    /** Cáscara pintada por su propia normal (RGB = normal*0.5+0.5, espacio de vista). Si el
     *  color cambia de forma brusca/discontinua al rotar la cámara, la normal está mal — si
     *  cambia suave, la geometría/transform es correcta y el problema está más adelante en el
     *  fragment shader. */
    NORMALS,
    /** Geometría en líneas, sin relleno ni shader — la malla real, sin que ninguna capa de
     *  color la tape. Ver KiVfxRenderTypes.wireframe(). */
    WIREFRAME,
    /** Todo menos las partículas sueltas (chispas/arcos de ModParticles). */
    PARTICLES_OFF;

    public boolean showsShell()     { return this == NORMAL || this == SHELL_ONLY || this == FLAT_BANDS || this == NORMALS || this == WIREFRAME; }
    public boolean showsEnvelope()  { return this == NORMAL || this == SHELL_ONLY; }
    public boolean showsCore()      { return this == NORMAL || this == CORE_ONLY; }
    public boolean showsRibbons()   { return this == NORMAL || this == RIBBONS_ONLY; }
    public boolean showsHalo()      { return this == NORMAL; }
    public boolean showsParticles() { return this != PARTICLES_OFF && this != SHELL_ONLY && this != CORE_ONLY
            && this != RIBBONS_ONLY && this != WIREFRAME; }
    /** true = usar el RenderType de líneas en vez del fresnel normal (ver KiVfxRenderTypes). */
    public boolean wireframe() { return this == WIREFRAME; }
    /** Valor que sube a ZenkaiDebugMode: 0 normal, 1 = pintar por normal (ver ki_energy.fsh). */
    public float shaderMode() { return this == NORMALS ? 1f : 0f; }
    /** true = forzar ZenkaiWobble a 0 y congelar el parpadeo, sea cual sea el de la técnica. */
    public boolean forceFlatBands() { return this == FLAT_BANDS; }

    private static KiVfxDebugMode current = NORMAL;

    public static KiVfxDebugMode current() { return current; }

    public static void cycle() {
        KiVfxDebugMode[] all = values();
        current = all[(current.ordinal() + 1) % all.length];
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[Zenkai VFX debug] " + current.name())
                    .withStyle(current == NORMAL ? ChatFormatting.GRAY : ChatFormatting.AQUA), true);
        }
    }
}
