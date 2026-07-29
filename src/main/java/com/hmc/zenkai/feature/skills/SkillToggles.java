package com.hmc.zenkai.feature.skills;

import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registro central de los INTERRUPTORES de habilidad (Ki Fist, Ki Infuse, las armas de ki,
 * Potential Unlock). Un interruptor no es un nivel: es "tengo la habilidad y ahora mismo la
 * quiero encendida". El estado vive en {@link com.hmc.zenkai.feature.player.PlayerSkills}
 * (dentro del attachment de stats), así que persiste, se copia al morir y viaja al cliente
 * por el sync de stats de siempre: no hace falta packet de sincronización propio.
 *
 * ÚNICO LECTOR: {@link #isOn(Player, String)}. Nadie debe leer el bit crudo del attachment,
 * porque isOn valida ADEMÁS que el interruptor siga siendo legítimo (habilidad revocada,
 * prerrequisito perdido, respec). Así un bit obsoleto en el NBT no puede activar nada:
 * el guard está en el punto de lectura, que es por donde pasan todos los consumidores,
 * en vez de repartido por cada sitio que quiera consultar el estado.
 *
 * Añadir un interruptor nuevo = una línea en el bloque static. Ni la rueda, ni el packet,
 * ni el guardado se enteran.
 */
public final class SkillToggles {
    private SkillToggles() {}

    /** Categorías de la rueda. El valor es la clave de lang: wheel.zenkai.&lt;categoría&gt;. */
    public static final String CAT_KI_WEAPONS = "ki_weapons";

    /**
     * @param id            id del interruptor (coincide con el id de habilidad si la tiene)
     * @param needsOwnSkill true si exige tener ESA habilidad a nivel > 0. Las armas de ki no:
     *                      no se compran, se desbloquean por tener otras dos.
     * @param requires      habilidades que además hay que tener (nivel > 0)
     * @param conflicts     interruptores que se apagan solos al encender este. La espada y la
     *                      guadaña compiten por la misma mano, así que encender una apaga la
     *                      otra en vez de dejar un estado imposible que alguien tenga que
     *                      resolver más tarde.
     * @param category      subcategoría de la rueda, o null para colgar de la raíz
     * @param enabled       false deja el interruptor FUERA DE JUEGO por completo: no aparece
     *                      en la rueda, isOn devuelve false y lo que dependiera de él se
     *                      apaga solo. Es el interruptor maestro para aparcar una habilidad
     *                      a medio terminar sin arrancar su código ni tocar los guardados.
     */
    public record Toggle(String id, boolean needsOwnSkill, Set<String> requires,
                         Set<String> conflicts, @Nullable String category, boolean enabled) {}

    private static final Map<String, Toggle> REGISTRY = new LinkedHashMap<>();

    private static void register(Toggle t) { REGISTRY.put(t.id(), t); }

    static {
        register(new Toggle(SkillEffects.KI_INFUSE, true, Set.of(), Set.of(), null, true));
        register(new Toggle(SkillEffects.KI_FIST,   true, Set.of(), Set.of(), null, true));

        // Las armas de ki no son habilidades comprables: son lo que pasa cuando tienes las
        // dos. Van agrupadas en su propia rama de la rueda porque son variantes de una misma
        // cosa, no dos interruptores independientes.
        Set<String> weaponReq = Set.of(SkillEffects.KI_FIST, SkillEffects.KI_INFUSE);
        // APARCADAS: enabled=false hasta que estén los modelos y las animaciones. El código
        // sigue compilado y probado; solo deja de ser alcanzable. Volver a activarlas es
        // cambiar estos dos false por true.
        register(new Toggle(SkillEffects.KI_BLADE,  false, weaponReq,
                Set.of(SkillEffects.KI_SCYTHE), CAT_KI_WEAPONS, false));
        register(new Toggle(SkillEffects.KI_SCYTHE, false, weaponReq,
                Set.of(SkillEffects.KI_BLADE),  CAT_KI_WEAPONS, false));

        register(new Toggle(SkillEffects.POTENTIAL_UNLOCK, true, Set.of(), Set.of(), null, true));
    }

    /** En orden de registro (orden en la rueda). */
    public static Collection<Toggle> all() { return REGISTRY.values(); }

    public static Toggle get(String id) { return REGISTRY.get(id); }

    public static boolean isToggleable(String id) { return REGISTRY.containsKey(id); }

    /** ¿Este jugador tiene derecho a este interruptor? (habilidad + prerrequisitos) */
    public static boolean available(Player p, String id) {
        if (p == null) return false;
        Toggle t = REGISTRY.get(id);
        if (t == null || !t.enabled()) return false;
        if (t.needsOwnSkill() && SkillEffects.level(p, id) <= 0) return false;
        for (String req : t.requires()) {
            if (SkillEffects.level(p, req) <= 0) return false;
        }
        return true;
    }

    /** EL lector. false si el interruptor ya no es legítimo, aunque el bit siga guardado. */
    public static boolean isOn(Player p, String id) {
        if (!available(p, id)) return false;
        return PlayerStatsAttachment.get(p).skills().isToggleOn(id);
    }

    /**
     * Servidor: alterna y sincroniza. Punto de entrada ÚNICO — lo llaman la rueda
     * (WheelSelectPacket) y SkillTogglePacket (teclas), para que la validación no se
     * duplique en dos sitios y se descuadre.
     * @return true si el interruptor cambió de estado.
     */
    public static boolean flip(ServerPlayer sp, String id) {
        if (!available(sp, id)) return false;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        boolean now = !att.skills().isToggleOn(id);
        att.skills().setToggle(id, now);

        // Al encender, apagar lo que no puede convivir con esto.
        if (now) {
            Toggle t = REGISTRY.get(id);
            for (String other : t.conflicts()) att.skills().setToggle(other, false);
        }

        PlayerLifeCycle.sync(sp);   // stats, no forma: los interruptores viven en PlayerSkills

        sp.displayClientMessage(Component.translatable(
                now ? "message.zenkai.toggle_on" : "message.zenkai.toggle_off",
                Component.translatable("skill.zenkai." + id)), true);
        return true;
    }

    /** Fija un estado concreto (muerte, comandos). No valida: es para apagar, no para dar. */
    public static void set(ServerPlayer sp, String id, boolean on) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (att.skills().isToggleOn(id) == on) return;
        att.skills().setToggle(id, on);
        PlayerLifeCycle.sync(sp);
    }
}