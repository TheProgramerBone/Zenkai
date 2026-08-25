package com.hmc.zenkai.feature;

/**
 * Contribuciones CRUZADAS entre atributos.
 * POR QUÉ EXISTE: hasta ahora cada atributo alimentaba exactamente una columna (STR→melee,
 * DEX→defensa, WIL→ki_dmg, SPI→pool). La consecuencia era que cada estilo tenía una sola vía
 * óptima y el resto de atributos eran dinero tirado: un spiritualist no tenía NINGÚN motivo
 * para tocar STR en la partida entera, y un warrior ninguno para tocar SPI más allá del pool.
 * Con seis atributos y tres estilos eso deja quince builds que en la práctica son tres.
 * QUÉ NO ES: no es una segunda fuente de poder. Los coeficientes son deliberadamente bajos —
 * el atributo principal de cada columna sigue rindiendo entre seis y doce veces más que el
 * secundario. La intención es que un punto "equivocado" no sea papel mojado, no que exista
 * una build oculta que gane a la directa.
 * CALIBRADO (simulado sobre las seis combinaciones raza/estilo más extremas y cuatro builds
 * arquetípicas antes de fijarlos):
 *   candidato                        media   dispersión entre builds
 *   (.10/.15/.10/.15) primera idea   +10.9%   13.8 pp   ← inflaba el ki puro un 16.6 %
 *   (.12/.15/.08/.00)                 +5.9%    8.4 pp
 *   (.15/.10/.08/.00)  ELEGIDO        +5.3%    9.0 pp   ← el más plano entre builds
 *   (.18/.08/.08/.00)                 +5.3%   10.5 pp
 * La sinergia WIL→pool se descartó entera: ki_reserves es la columna de mayor magnitud (40 a
 * 120 por punto) y cualquier aporte ahí disparaba el PL de las builds de ki justo por encima
 * de las de pega, que es lo contrario de lo que se buscaba.
 * EL COEFICIENTE ES SIEMPRE EL DE LA COLUMNA DESTINO, no el del atributo origen. Si WIL
 * aportara a melee con su propio coeficiente de ki_damage estaríamos sumando dos escalas
 * distintas y el resultado dependería de la tabla en vez de del jugador — el mismo criterio
 * que ya seguía computeBestMeleeFinal.
 * EFECTO SOBRE PARTIDAS EXISTENTES: el PL de un personaje sube entre un 2 y un 7 % de golpe
 * al actualizar. Es un desplazamiento único, no una escalada, y va en la dirección segura
 * (nadie pierde poder). Los mobs no se mueven: su PL objetivo lo fija el datapack y
 * PowerLevel.solveAttributes reparte con los pesos B_*, que esto no toca.
 */
public final class StatSynergy {
    private StatSynergy() {}

    /** WIL aporta a melee: la fuerza de voluntad también pega. */
    public static final double MELEE_FROM_WIL = 0.15;
    /** CON aporta a defensa: aguantar un golpe es encajarlo, no solo esquivarlo. */
    public static final double DEFENSE_FROM_CON = 0.10;
    /** SPI aporta a potencia de ki: la reserva espiritual empuja la técnica. */
    public static final double KIPOWER_FROM_SPI = 0.08;

    /** melee = STR·melee + WIL·melee·0.15 */
    public static double melee(int str, int wil, double meleeCoef) {
        return (str + wil * MELEE_FROM_WIL) * meleeCoef;
    }

    /** defensa = DEX·def + CON·def·0.10 */
    public static double defense(int dex, int con, double defenseCoef) {
        return (dex + con * DEFENSE_FROM_CON) * defenseCoef;
    }

    /** potencia de ki = WIL·kiDmg + SPI·kiDmg·0.08 */
    public static double kiPower(int wil, int spi, double kiDamageCoef) {
        return (wil + spi * KIPOWER_FROM_SPI) * kiDamageCoef;
    }

    /** Reserva de ki: SIN sinergia, a propósito (ver arriba). Aquí por simetría de lectura. */
    public static double kiPool(int spi, double kiReservesCoef) {
        return spi * kiReservesCoef;
    }
}