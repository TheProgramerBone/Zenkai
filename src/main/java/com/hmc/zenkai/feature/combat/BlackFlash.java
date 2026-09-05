package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BLACK FLASH. Proc raro sobre un golpe de KI FIST: multiplica el golpe en CRUDO, le suma un
 * extra sacado del MEJOR atributo ofensivo del jugador, y dibuja un impacto negro y rojo.
 *
 * SOLO KI FIST, y no Ki Infuse. Es una mecánica de puño. Además Ki Infuse exige arma en mano
 * por su propio hasWeapon(), así que gatear en la infusión hacía el proc literalmente
 * imposible a mano limpia, que es como se juega Ki Fist.
 *
 * POR QUÉ EL MULTIPLICADOR VA EN CRUDO Y NO DESPUÉS DE MITIGAR. La defensa es proporcional
 * (dmg² / (def + dmg)), así que un x3 crudo no se traduce en un x3 efectivo: sale x3.9 entre
 * iguales y x7.3 contra un SSJ4 dominado. Suena a matagigantes y NO lo es — simulado con los
 * stats del datapack, el daño ABSOLUTO del proc BAJA del 256% de un golpe crudo (entre
 * iguales) al 93% (contra un SSJ4). El multiplicador sube más despacio de lo que se hunde tu
 * daño base. En una frase: el black flash convierte un golpe inútil en un golpe normal.
 *
 * EL TÉRMINO DEL MEJOR ATRIBUTO existe porque el golpe base solo sabe de STR (el melee) y de
 * SPI (el bonus de Ki Fist). Un build de WIL pega con los puños como un civil, y sin este
 * término su black flash era tres veces nada. Con él, el proc se cobra sobre tu mejor
 * atributo sea cual sea, y pasa a ser el recurso de quien no puede pelear de cerca.
 * Se valora en escala de MELEE (atributo x coeficiente de melee de la raza/estilo) y no en la
 * de ki: es el mismo criterio de KiFist, y sin él estaríamos comparando WIL en escala de
 * ki_damage contra STR en escala de melee, que son cosas distintas.
 * SUMA en vez de multiplicar por el mismo motivo que la infusión suma: multiplicando, el que
 * ya pega fuerte gana más aún, y esto va dirigido justo al contrario.
 *
 * POR QUÉ LA PROBABILIDAD USA `scale` CRUDO Y NO chargeF. chargeF tiene suelo 0.2 (nunca baja
 * de ahí), así que usarlo dejaría un ~0.8% residual machacando el botón. El ticker crudo llega
 * a 0 de verdad, y elevado al cubo hace que spamear saque el proc de la mesa. Es lo más cerca
 * que se puede estar de "golpear en el instante exacto" con lo que ya calcula el pipeline.
 *
 * LA SUERTE entra por Attributes.LUCK, que en vanilla solo sirve para loot tables y cuya poción
 * ni siquiera tiene receta. Aquí le da un uso real, Unlucky funciona solo en negativo, y
 * cualquier mod que otorgue luck se integra sin código.
 *
 * NO COBRA NADA. El ki de Ki Fist ya se pagó en el mismo golpe; cobrar por un proc aleatorio
 * sería castigar la suerte.
 */
public final class BlackFlash {
    private BlackFlash() {}

    /** Escalas de partícula. El filo va 0.2 por encima del núcleo para que asome; si ajustas
     *  el tamaño, MUEVE LAS DOS manteniendo la diferencia — desacopladas, el rojo deja de
     *  encajar con el borde del negro y el efecto se lee como dos cosas distintas. */
    private static final float CORE_SCALE = 3.0f;
    private static final float RIM_SCALE  = 3.2f;
    private static final int   SPARKS     = 26;

    /**
     * Puente hacia CombatZenkaiHooks: ¿el golpe que ACABA de llegarle a este UUID (víctima) fue
     * un proc de Black Flash? Solo así el mensaje de muerte puede decir "borrado por un Black
     * Flash" en vez del playerAttack genérico — el golpe base sigue siendo melee de vanilla, sin
     * un hurt() propio del mod donde enganchar un DamageType directamente.
     * Se consume SIEMPRE en el mismo evento que lo marca (onDamage, justo después de
     * computeAttackDamage), letal o no: sin ese consumo inmediato, un proc en un golpe que NO
     * mata se quedaría marcado hasta el PRÓXIMO golpe letal contra este jugador — que podría ser
     * un ataque completamente distinto, de otro atacante, minutos después.
     */
    private static final Set<UUID> PROCCED_ON = ConcurrentHashMap.newKeySet();

    /** ¿Hubo un proc de Black Flash contra este jugador? Lo borra al leerlo. */
    public static boolean consumeProc(UUID victimId) {
        return PROCCED_ON.remove(victimId);
    }

    /** Red de seguridad al desconectar, mismo criterio que CombatZenkaiHooks.forgetAttackScale:
     *  en el camino normal esto se consume en el mismo tick que se marca, pero sin esto una
     *  desconexión a mitad de evento dejaría la entrada huérfana. */
    public static void forget(UUID id) {
        PROCCED_ON.remove(id);
    }

    /**
     * Probabilidad efectiva del proc. Pública para que la GUI pueda mostrarla algún día sin
     * reimplementar la fórmula.
     * @param scale ticker de ataque CRUDO de vanilla, 0..1.
     */
    public static double chance(ServerPlayer sp, double scale) {
        double base = ServerConfig.blackFlashChance();
        if (base <= 0.0) return 0.0;

        double f = Math.max(0.0, Math.min(1.0, scale));
        double c = base * Math.pow(f, ServerConfig.blackFlashChargeExponent());

        // ⚠ API a verificar al compilar: Player#getLuck() en 1.21.1 (lee Attributes.LUCK).
        // El max(0) es por Unlucky con amplificador alto: luck muy negativo daría un factor
        // negativo y, peor, positivo otra vez si alguien tocara el signo.
        double luck = sp.getLuck();
        c *= Math.max(0.0, 1.0 + luck * ServerConfig.blackFlashLuckFactor());

        return Math.max(0.0, Math.min(c, ServerConfig.blackFlashMaxChance()));
    }

    /**
     * Tira el dado y, si sale, devuelve el golpe ya potenciado. Si no sale, devuelve `total`
     * intacto.
     *
     * Devuelve el TOTAL y no un multiplicador (como hacía la primera versión) porque desde que
     * existe el término del mejor atributo el efecto ya no es una escala pura: es
     * `total x mult + mejorAtributo x carga x factor`. Con la firma vieja, quien llama tendría
     * que conocer las dos mitades de la fórmula, y la fórmula tiene que vivir en un solo sitio.
     *
     * Solo debe llamarse cuando Ki Fist HAYA PAGADO de verdad — ver el guard en
     * CombatZenkaiHooks.playerMeleeDamage.
     *
     * @param scale   ticker de ataque crudo (0..1), para la probabilidad.
     * @param chargeF factor de carga del golpe (0.2..1.0), para el extra del atributo.
     * @param total   golpe completo en crudo, antes de defensa.
     */
    public static double apply(ServerPlayer sp, LivingEntity victim, double scale,
                               double chargeF, ZenkaiCombatStats st, double total) {
        double c = chance(sp, scale);
        if (c <= 0.0) return total;
        if (sp.getRandom().nextDouble() >= c) return total;

        double boosted = total * ServerConfig.blackFlashMultiplier()
                + st.computeBestMeleeFinal() * chargeF * ServerConfig.blackFlashStatFactor();

        PROCCED_ON.add(victim.getUUID());
        fx(sp, victim);
        ZenkaiTriggers.MILESTONE.get().trigger(sp, ZenkaiTriggers.Kinds.BLACK_FLASH);
        return boosted;
    }

    /** ÚNICO sitio donde se dibuja el Black Flash. Mismo criterio que
     *  PhysicalCombatServer.impactFx (resuelto en servidor, el conjunto de clientes ve lo mismo),
     *  pero con paleta FIJA en vez de la del aura: la lectura del efecto depende del contraste
     *  entre el negro y el rojo, y con un aura roja el núcleo se volvería invisible. */
    private static void fx(ServerPlayer sp, LivingEntity victim) {
        ServerLevel lvl = sp.serverLevel();
        double x = victim.getX();
        double y = victim.getY() + victim.getBbHeight() * 0.6;
        double z = victim.getZ();
        double s = victim.getBbWidth() * 0.4;

        lvl.sendParticles(ModParticles.blackFlashCore(CORE_SCALE), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        lvl.sendParticles(ModParticles.blackFlashRim(RIM_SCALE),   x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        lvl.sendParticles(ModParticles.blackFlashSpark(1.0f),      x, y, z, SPARKS, s, 0.2, s, 0.55);

        // ⚠ PROVISIONAL, igual que el fogonazo del arco: cuando existan los .ogg de
        // ki_attack_release_*, esto debería compartir paleta sonora con ellos.
        // ⚠ API a verificar: SoundEvents.LIGHTNING_BOLT_IMPACT en 1.21.1.
        lvl.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.5f, 1.5f);

        // Un proc del 3% que el jugador no sabe que ocurrió no existe. Actionbar y no chat:
        // no ensucia el historial en una pelea larga.
        sp.displayClientMessage(Component.translatable("messages.zenkai.black_flash"), true);
    }
}