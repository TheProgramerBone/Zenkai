package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.DivineForms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Fachada del skill tree de transformaciones (diseño:
 * {@code .claude/pendiente/skill-tree-transformaciones.md}). Quien quiera saber si el jugador
 * POSEE un nodo (comprado) pregunta aquí; si puede USAR una forma sigue siendo
 * FormAccess.unlocked(), que además mira las condiciones (alineamiento, maestría, ritual…).
 * Es seguro leer desde el cliente: el attachment viaja en el sync de stats.
 *
 * Escritura (solo servidor): {@link #grant} es el punto ÚNICO por el que un nodo propio se
 * consigue sin pasar por la compra (ritual, prueba del maestro, evento, comando), y
 * {@link #resetTree} el punto único para reiniciar el árbol (reencarnación futura con Kami,
 * Shenlong/Porunga o Yemma; hoy sin uso de juego, §2 «Datos y compra»).
 */
public final class SkillTree {
    private SkillTree() {}

    /** Vías por las que se consigue un nodo sin TP (para mensajes y logros). */
    public enum Via { TP, RITUAL, MASTER_TRIAL, DIVINE, EVENT, COMMAND }

    public static boolean owns(Player p, ResourceLocation node) {
        return PlayerStatsAttachment.get(p).skillTree().owns(node);
    }

    public static int level(Player p, ResourceLocation node) {
        return PlayerStatsAttachment.get(p).skillTree().level(node);
    }

    /** ¿Tiene God Ki? (Skill de nivel 1 desde F5.) */
    public static boolean hasGodKi(Player p) {
        return PlayerStatsAttachment.get(p).skills().level(DivineForms.SKILL) > 0;
    }

    /**
     * Concede un nodo sin cobrar nada. God Ki es una skill (nivel 1, con suelo: no se pierde
     * con el respec); el resto, nodos propios. No sincroniza: lo hace quien llama, que suele
     * tocar más cosas a la vez.
     */
    public static void grant(ServerPlayer sp, ResourceLocation node, int level, Via via) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (SkillTreeMigration.NODE_GOD_KI.equals(node)) {
            att.skills().grant(DivineForms.SKILL, 1);
            return;
        }
        if (att.skillTree().ownedLevel(node) >= level) return;
        att.skillTree().setOwned(node, level);
    }

    /**
     * Concede un nodo PROPIO de forma (cadena mortal, rama...) sin cobrar TP. {@code waived}: tampoco ocupa MIND (despertares,
     * maestría, deseo): el equivalente por nodo de {@code PlayerSkills.grantWaived}. Opera sobre el attachment para que lo
     * usen también los gametests y los sitios sin ServerPlayer; no sincroniza.
     */
    public static void grantNode(PlayerStatsAttachment att, ResourceLocation node, boolean waived) {
        if (att.skillTree().ownedLevel(node) < 1) att.skillTree().setOwned(node, 1);
        if (waived) att.skillTree().setMindWaived(node, true);
    }

    /**
     * Compra con TP el siguiente nivel de un nodo PROPIO ya evaluado como comprable (pasiva,
     * forma divina). Revalida TP y MIND aquí: es lo último antes de cobrar.
     */
    static boolean buyOwned(ServerPlayer sp, SkillTreeNodes.Node node, SkillTreeNodes.Eval e) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!e.ownNode() || att.getTP() < e.cost() || att.mindFree() < e.mindDelta()) return false;
        att.addTP(-e.cost());
        att.skillTree().setOwned(node.id(), e.targetLevel());
        PlayerLifeCycle.sync(sp);
        return true;
    }

    /**
     * Garantiza God Ki a quien es una divinidad con cargo (isDivine, ver
     * docs/jugador-razas-formas.md): regla de diseño §5. Lo llama FormSystem cada tick (barato:
     * dos lecturas) para cubrir cualquier vía por la que se active isDivine.
     */
    public static boolean ensureDivineGodKi(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isDivine() || hasGodKi(sp)) return false;
        att.skills().grant(DivineForms.SKILL, 1);
        return true;
    }

    /**
     * Reinicia el árbol: nodos propios fuera, God Ki fuera y audiencia retirada (las demás skills
     * del árbol las deja a quien llama: el reset full ya las borra). NO devuelve TP (el árbol no tiene reembolso). Punto único para la futura
     * reencarnación; hoy solo lo usa el reset full.
     */
    public static void resetTree(ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.skillTree().clear();
        att.skills().revoke(DivineForms.SKILL);
        att.setGodKiAudience(false);
        att.refreshSkillTree();
        PlayerLifeCycle.sync(sp);
    }
}
