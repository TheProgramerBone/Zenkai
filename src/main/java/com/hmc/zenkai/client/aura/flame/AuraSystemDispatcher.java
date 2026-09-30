package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.compat.IrisCompat;
import com.hmc.zenkai.config.ClientConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Punto de despacho ÚNICO entre los dos sistemas de aura: el legacy ({@code client.aura}) y el
 * nuevo estilo dbrebirth ({@code client.aura.flame}). Ver
 * .claude/docs/tecnicas-y-vfx.md, "Aura nueva estilo dbrebirth".
 * <p>
 * En cualquier frame se dibuja el CUERPO del aura con un sistema o con el otro, nunca los dos.
 * Qué cuenta como cuerpo (decisión del usuario, 2026-09-29): el faldón de llama
 * ({@code AuraSkirtRenderer}) y el aro ({@code AuraRimRenderer}) del legacy. Suelo, estela,
 * wisps, chispas y ascuas del legacy se siguen dibujando también con el sistema nuevo — son
 * capas complementarias que las referencias (llama + anillo de suelo + chispas) también tienen.
 * <p>
 * Los archivos legacy solo LEEN {@link #useFlame()} en una guarda de una línea; la decisión vive
 * entera aquí. Revertir al legacy por completo es poner {@code aura.system = FORCE_LEGACY} en la
 * config (o borrar las dos guardas), sin riesgo de dejar el legacy a medias porque su lógica no
 * se tocó.
 * <p>
 * Orden de resolución (Fase 5): el override de sesión de {@code /zenkaiauraflame} si lo hay, si
 * no la opción persistente {@code ClientConfig.auraSystem()} (AUTO por defecto). AUTO elige el
 * nuevo solo sin shaderpack: con Iris/Oculus el bloom propio no existe y el nuevo pierde gran
 * parte de su razón de ser. Si alguno de los shaders nuevos no compiló, siempre legacy: mejor el
 * aura de siempre que ninguna.
 */
public final class AuraSystemDispatcher {

    private AuraSystemDispatcher() {}

    /** Override de sesión puesto por {@link AuraFlameDebugCommand}; solo en memoria. null = seguir
     *  la config. Sirve para comparar los dos sistemas en caliente sin tocar el archivo. */
    private static @Nullable AuraSystemMode sessionOverride = null;

    public static @Nullable AuraSystemMode sessionOverride() { return sessionOverride; }

    public static void setSessionOverride(@Nullable AuraSystemMode mode) { sessionOverride = mode; }

    /** El modo que manda ahora mismo: el override si lo hay, si no la config. */
    public static AuraSystemMode effectiveMode() {
        return sessionOverride != null ? sessionOverride : ClientConfig.auraSystem();
    }

    /** ¿El cuerpo del aura de este frame lo dibuja el sistema nuevo? */
    public static boolean useFlame() {
        if (!AuraFlameRenderType.available()) return false;
        return switch (effectiveMode()) {
            case FORCE_NEW -> true;
            case FORCE_LEGACY -> false;
            case AUTO -> !IrisCompat.shaderPackActive();
        };
    }
}
