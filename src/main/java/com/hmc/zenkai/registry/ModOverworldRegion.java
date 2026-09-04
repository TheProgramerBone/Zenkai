package com.hmc.zenkai.registry;


import com.hmc.zenkai.Zenkai;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.VanillaParameterOverlayBuilder;

import static terrablender.api.ParameterUtils.*;
import java.util.function.Consumer;

public class ModOverworldRegion extends Region {

    public ModOverworldRegion() {
        // El 'weight' es cuánto "pesa" nuestra región frente a la región vanilla del Overworld
        // (peso 10 por defecto, ver DefaultOverworldRegion en la fuente de TerraBlender) al
        // repartirse qué fracción del mapa usa el mapa de biomas de cada una — súbelo para que
        // rocky_wasteland aparezca más seguido en general, bájalo para menos. Es el dial global
        // de presencia, INDEPENDIENTE de los diales de clima de abajo (ver el bloque de
        // comentarios de addBiomes): el weight decide qué fracción del MAPA compite con nuestro
        // árbol de clima, los diales de clima deciden cuánto de esa competencia gana rocky_wasteland
        // de verdad. BUG real corregido (2026-09-03): este comentario ya decía "(5)" pero el
        // valor de verdad era 1 — con eso, esta región solo "ganaba" la competencia contra
        // vanilla ~1 de cada 11 veces.
        // Subido a 12 (2026-09-04, Ronda 2): el fix del escape oceánico (ver addBiomes) estrechó
        // temperature/humidity/weirdness para casi eliminar el bioma apareciendo en pleno
        // océano, y eso bajó de rebote cuánto de "su" territorio de tierra adentro ocupa. Subir
        // el weight de 5 a 12 compensa PARCIALMENTE esa pérdida de frecuencia global sin reabrir
        // el bug de océano (son ejes ortogonales: el weight no toca la selección climática). La
        // Ronda 3 (mismo día, ver el eje `depth` más abajo) recuperó bastante ocupación por su
        // cuenta (11.4% → 16.1% medido, sin tocar weight) — no hizo falta subir esto de nuevo,
        // pero sigue siendo la palanca si en juego se ve escaso. No hay techo salvo el propio 10
        // de vanilla perdiendo relevancia según sube.
        super(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "overworld"), RegionType.OVERWORLD, 12);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();

        // ── DIALES DE CLIMA (cada eje decide DÓNDE y CUÁNTO sale el bioma) ────────────────
        // Cuanto más ancho el rango de cada eje, MÁS grande/frecuente el bioma (reemplaza más
        // biomas vanilla en ese nicho). Cuanto más estrecho, más raro y pequeño.
        //
        // BUG real investigado y medido a fondo (2026-09-04, ver .claude/pendiente/
        // rocky-wasteland-terrablender-gen.md): rocky_wasteland podía aparecer como bioma
        // EXPLÍCITO en pleno océano (confirmado con captura F3 real del usuario, C=-0.751 =
        // "Deep ocean"). Causa (terrablender.mixin.MixinParameterList.findValuePositional,
        // fuente real leída): la selección de bioma es NEAREST-NEIGHBOR por distancia acumulada
        // en las 6 dimensiones climáticas, no "¿cae dentro del rango declarado?" — así que un
        // punto nuestro puede ganarle al DEFERRED_PLACEHOLDER (el hueco que cubre "todo lo que
        // no elegimos", incluido el océano) si gana suficiente terreno en OTROS ejes aunque
        // pierda mucho en continentalness. Verificado con un gametest de investigación
        // (RockyWastelandOceanInvestigation, temporal) reconstruyendo el generador REAL +
        // muestreando miles de celdas: con temperature=COOL..HOT y 10 bandas de weirdness, un
        // 39.4% de las celdas oceánicas (C<-0.11) que gana nuestra región terminaban en
        // rocky_wasteland de todos modos. NO era el weight (ya arreglado antes, ver más abajo) —
        // eso solo decide QUÉ FRACCIÓN de celdas compite con nuestro árbol de clima, no si ganamos
        // esa competencia dentro de él.
        //
        // Fix real (no solo el síntoma): estrechar temperature a WARM..HOT — el bioma se ve
        // cálido igual por el campo temperature=2.0 del propio JSON del bioma, así que este eje
        // de COLOCACIÓN puede estrecharse sin ningún coste visual, y de paso encaja mejor con
        // "bioma cálido" que COOL..HOT (que ni siquiera lo era). Combinado con estrechar weirdness
        // a solo HIGH_SLICE+PEAK (bajó de 39.4% a 4.8% el escape oceánico; la combinación con
        // WARM..HOT sola sin esto igual ayuda algo, ver el pendiente para las cifras intermedias).
        // RONDA 2 (mismo día, el usuario probó en otra seed y seguía viendo tramos de océano):
        // con temperature WARM..HOT + weirdness 6 bandas el escape ya había bajado a 4.8%
        // (410/8582), pero seguía siendo visible en juego. El promedio de los casos de escape
        // restantes tenía erosion≈0.47 (ya casi dentro de nuestra propia banda EROSION_5) y
        // humidity≈-0.41 — es decir, humidity (ARID..NEUTRAL, span MUY ancho: -1.0 a 0.1) seguía
        // dejando ganar casos oceánicos por ese eje. Estrechado a un span custom (-1.0, -0.1) —
        // toda la banda ARID más el hueco seco antes de NEUTRAL, pero sin entrar en NEUTRAL —
        // bajó el escape a 0.97% (83/8582, casi todo COAST/OCEAN somero, 2 casos deep_ocean).
        // Probado también ARID sola (más agresivo, 0.17% de escape) pero se descartó: dejaba la
        // ocupación en tierra en 5.8%, demasiado poco frente al pedido de "que no sea pequeño".
        // TRADE-OFF MEDIDO Y ACEPTADO (elección explícita del usuario: priorizar cerrar el escape
        // oceánico sobre el tamaño, con el weight de arriba subido para compensar parcialmente):
        // la ocupación real del bioma dentro de su propio territorio de tierra adentro bajó de
        // 66.4% (original) a 11.4% (esta ronda) — compensado subiendo weight de 5 a 12 (ver
        // arriba). Si en juego se sigue viendo escaso, subir `weight` más es la palanca segura;
        // volver a ensanchar humidity/temperature/weirdness reintroduce el escape oceánico ya
        // medido en las dos rondas — no hacerlo sin remedir con RockyWastelandOceanInvestigation.
        //  · temperature   : WARM..HOT — ver bloque de arriba. Antes COOL..HOT (bug de océano).
        //  · humidity       : span custom (-1.0, -0.1) — ver "RONDA 2" arriba. Antes ARID..NEUTRAL
        //                     (-1.0 a 0.1, escape oceánico); ARID sola probada pero descartada.
        //  · continentalness: MID_INLAND..FAR_INLAND = tierra adentro (montañas, lejos de costa).
        //  · erosion        : EROSION_5 — NO EROSION_0..2 (así estaba antes). Comprobado contra
        //                     el propio OverworldBiomeBuilder de vainilla (fuente descompilada):
        //                     erosions[0]/[1] son la banda de las CUMBRES (jagged_peaks/frozen_peaks
        //                     — terracería extrema en escalones, exactamente lo que se veía en las
        //                     capturas de juego) y no es donde vainilla coloca su bioma "rocoso
        //                     genérico" — WINDSWEPT_HILLS/WINDSWEPT_GRAVELLY_HILLS (pickShatteredBiome)
        //                     vive en erosions[5] = span(0.45, 0.55), una banda estrecha y bastante
        //                     erosionada (mucho más cerca del extremo "río", erosions[6], que del
        //                     extremo "picos", erosions[0]). Ese desajuste — usar la banda de picos
        //                     para un bioma pensado como "terreno rocoso ondulado", no cordillera —
        //                     era la causa real de la terracería en escalón Y de que el borde del
        //                     bioma cayera tan abrupto y cerca del agua (con relieve extremo la
        //                     transición a tierras bajas ocurre en muy poca distancia horizontal).
        //                     EROSION_5 iguala la banda real de vainilla para este tipo de bioma:
        //                     ondulado, más bajo, sin la terracería en escalón.
        //  · weirdness      : la terracería (el problema original que motivó revisar este eje) NO
        //                     venía de aquí, venía solo de erosion — eso sigue siendo cierto. Pero
        //                     el bug de océano (ver arriba) SÍ tiene que ver con weirdness: con las
        //                     10 bandas originales (MID_SLICE+HIGH_SLICE+PEAK) el DEFERRED_PLACEHOLDER
        //                     quedaba con un hueco de weirdness demasiado estrecho, lo que le hacía
        //                     perder la competencia nearest-neighbor contra nuestros puntos incluso
        //                     en clima oceánico. Reducido a solo HIGH_SLICE+PEAK (6 bandas) — medido
        //                     que ayuda (ver arriba). Ensanchar esto de vuelta a 10 es la palanca si
        //                     hace falta más área Y se acepta que el escape oceánico empeora otra vez
        //                     (medido: ~34% con 10 bandas + WARM..HOT, contra 4.8% con 6 bandas).
        //  · depth          : span custom (-0.15, 0.85) — ver "RONDA 3" abajo. Depth es el eje que
        //                     vainilla/TerraBlender usan para distinguir biomas de SUPERFICIE de
        //                     biomas de CUEVA (3D biome, tipo dripstone/lush caves) en la MISMA
        //                     columna — NO es "altura real del terreno" en bloques, pero SÍ decrece
        //                     hacia 0 cerca de la superficie real y crece cuanto más se baja
        //                     (confirmado por muestreo: D≈0.17 a 17 bloques bajo el suelo real,
        //                     D≈1.1 cerca de bedrock en la misma columna).
        //
        // RONDA 3 (mismo día, el usuario reportó "el bioma no tiene mucha altura" + "bajando se va
        // a forest" + katchin más raro de lo esperado, punto exacto (954,43,-4) seed
        // 7818626915293203912): `Depth.SURFACE` de las rondas 1/2 es, verificado leyendo
        // OverworldBiomeBuilder.addSurfaceBiome (fuente real de vainilla, NO adivinado), un PUNTO
        // de ancho CERO en depth=0.0 — y todo bioma normal de vainilla (forest, savanna...) declara
        // AMBOS depth=0.0 Y depth=1.0 (FLOOR) a la vez vía esa misma función, nunca uno solo. Con
        // nuestro único punto en 0.0, en cuanto el jugador bajaba unos bloques del suelo real (D
        // deja de ser ~0) forest ganaba la competencia en depth con la MISMA distancia que
        // nosotros — y a partir de ahí ganaba en TOTAL casi siempre, dejando rocky_wasteland
        // literalmente sin altura utilizable (confirmado: en la columna de prueba, con
        // Depth.SURFACE rocky_wasteland solo empezaba a y=56, con el suelo real en y=73 — apenas
        // 17 bloques de bioma real, y la mena de Katchin de este bioma (KATCHIN_ORE_ROCKY) apunta a
        // un rango de Y de -64 a 32, CASI TODO por debajo de donde el bioma llegaba a existir).
        // Intentado primero `Depth.SURFACE, Depth.FLOOR` (los DOS puntos exactos, replicando el
        // patrón real de vainilla) — no ayudó nada (mismo resultado que solo SURFACE, ver el
        // pendiente): dos puntos exactos siguen dejando un hueco enorme sin cubrir en medio, y
        // forest empata ahí con la MISMA distancia que nosotros de cualquier forma. La ganancia
        // real vino de usar un SPAN CONTINUO en vez de puntos — algo que vainilla no hace para
        // biomas normales, pero nada nos obliga a igualar su convención: un span nos da distancia
        // CERO en un rango, no una distancia MÍNIMA en dos puntos, así que le ganamos a forest en
        // toda esa franja en vez de empatar. Medido con barridos sucesivos (ver
        // RockyWastelandOceanInvestigation, sección "RONDA 3"): (-0.15,0.4) ya llegaba a y=4 sin
        // bache de forest; (-0.15,0.6) a y=-24; (-0.15,0.85) — el valor final — a y=-56, cubriendo
        // CASI TODO el rango de la mena de Katchin, SIN NINGÚN aumento medido en el escape oceánico
        // en ninguno de los pasos (121/12628 fijo en las tres pruebas, 0 casos deep_ocean) — el
        // escape oceánico resultó no ser sensible a este eje en absoluto, a diferencia de
        // temperature/humidity/weirdness. Techo dejado deliberadamente en 0.85, no más cerca de
        // 1.0: FLOOR (depth=1.0 exacto) es el valor puntual que el bug de "rocky_wasteland bajo el
        // agua" de una sesión anterior señaló como causa (ver el commit/comentario histórico) —
        // no hay evidencia de que acercarse mucho lo reintroduzca ahora que los demás ejes están
        // mucho más endurecidos, pero tampoco se midió, así que se dejó margen real (0.15) a
        // propósito en vez de ir al límite.
        new ParameterUtils.ParameterPointListBuilder()
                .temperature(Temperature.span(Temperature.WARM, Temperature.HOT))
                .humidity(Climate.Parameter.span(-1.0f, -0.1f))
                .continentalness(Continentalness.span(Continentalness.MID_INLAND, Continentalness.FAR_INLAND))
                .erosion(Erosion.EROSION_5)
                .depth(Climate.Parameter.span(-0.15f, 0.85f))
                .weirdness(
                        Weirdness.HIGH_SLICE_NORMAL_ASCENDING,
                        Weirdness.HIGH_SLICE_NORMAL_DESCENDING,
                        Weirdness.HIGH_SLICE_VARIANT_ASCENDING,
                        Weirdness.HIGH_SLICE_VARIANT_DESCENDING,
                        Weirdness.PEAK_NORMAL,
                        Weirdness.PEAK_VARIANT
                )
                .build()
                .forEach(point -> builder.add(point, ModBiomes.ROCKY_WASTELAND));

        builder.build().forEach(mapper);
    }
}