package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import terrablender.api.RegionType;
import terrablender.util.LevelUtils;
import terrablender.worldgen.IExtendedParameterList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * INVESTIGACIÓN TEMPORAL — borrar tras usar (mismo criterio que otras investigaciones de
 * worldgen de este mod: verificar por cálculo, sin necesidad de generar el mundo real, antes de
 * tocar ningún JSON). Reconstruye el generador REAL del Overworld (RandomState + BiomeSource
 * real, incluida la región de TerraBlender vía su mixin — no una reimplementación propia) con la
 * semilla exacta que reportó el usuario, y muestrea altura de suelo + bioma en y alrededor del
 * punto reportado (rocky_wasteland "literalmente océano").
 *
 * Seed 7818626915293203912, punto reportado (-1405, 53, 5420).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RockyWastelandOceanInvestigation {
    private RockyWastelandOceanInvestigation() {}

    private static final long SEED = 7818626915293203912L;
    private static final int REPORT_X = -1405;
    private static final int REPORT_Z = 5420;

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void investigate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryAccess registryAccess = server.registryAccess();

        NoiseGeneratorSettings settings = registryAccess.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        HolderGetter<NormalNoise.NoiseParameters> noiseParams = registryAccess.lookupOrThrow(Registries.NOISE);
        RandomState randomState = RandomState.create(settings, noiseParams, SEED);

        // El LevelStem del propio mundo del gametest es PLANO (FlatLevelSource, para tests
        // rápidos) — nada que ver con el generador real de superficie. WorldPresets.
        // getNormalOverworld reconstruye el LevelStem REAL de un mundo "Normal" a partir de los
        // registros ya cargados (los mismos datapacks, incluido TerraBlender), independiente del
        // mundo en el que corre este test.
        LevelStem overworldStem = WorldPresets.getNormalOverworld(registryAccess);
        NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) overworldStem.generator();
        BiomeSource biomeSource = gen.getBiomeSource();

        StringBuilder out = new StringBuilder("\n=== ROCKY WASTELAND OCEAN INVESTIGATION (seed " + SEED + ") ===\n");

        // PASO 1 del "próximo paso concreto" (sección 5 del pendiente): sacar el
        // Climate.ParameterList REAL que usa nuestro biomeSource — MultiNoiseBiomeSource.parameters()
        // es privado en el .java original de NeoForge, pero el mixin de TerraBlender
        // (MixinMultiNoiseBiomeSource) lo shadowea como PUBLIC, y Mixin ensancha la visibilidad del
        // método objetivo al tejerlo — así que en tiempo de ejecución YA es público, solo el
        // compilador (que ve el .class original, sin tejer) no lo sabe. Reflexión para saltarnos eso.
        Climate.ParameterList<?> paramList;
        try {
            Method parametersMethod = biomeSource.getClass().getDeclaredMethod("parameters");
            parametersMethod.setAccessible(true);
            paramList = (Climate.ParameterList<?>) parametersMethod.invoke(biomeSource);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("No se pudo leer MultiNoiseBiomeSource.parameters() por reflexión", e);
        }
        IExtendedParameterList<?> extended = (IExtendedParameterList<?>) paramList;
        boolean initializedBefore = extended.isInitialized();
        out.append("isInitialized() ANTES de nuestra llamada manual: ").append(initializedBefore).append("\n");

        if (initializedBefore) {
            // CONFIRMA la hipótesis de la sección 5: el Climate.ParameterList es el objeto
            // COMPARTIDO del registro data-driven (MultiNoiseBiomeSourceParameterList "overworld"),
            // y algo (el propio arranque del server de este gametest, para OTRA dimensión con
            // generador real, o un preset compartido) ya lo inicializó con una seed/uniqueness
            // que NO es la nuestra. initializeForTerraBlender tiene guard "if (initialized) return"
            // (confirmado leyendo el .class), así que nuestra llamada normal sería un no-op. Hay
            // que resetear los campos privados del mixin a mano (mismos nombres que declara
            // MixinParameterList, tejidos directamente en Climate$ParameterList) para forzar una
            // reinicialización real con SEED.
            out.append("-> confirma la hipótesis: forzando reset de campos privados para reinicializar con nuestra seed\n");
            try {
                resetPrivateBoolean(paramList, "initialized");
                resetPrivateBoolean(paramList, "treesPopulated");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("No se pudo resetear los campos privados de Climate.ParameterList", e);
            }
        }

        extended.initializeForTerraBlender(registryAccess, RegionType.OVERWORLD, SEED);
        out.append("isInitialized() DESPUÉS de nuestra llamada manual: ").append(extended.isInitialized()).append("\n");

        // Mantener también la llamada de más alto nivel (LevelUtils.initializeBiomes) por si acaso
        // toca algo adicional (appendDeferredBiomesList, ruleCategory) que el reset de arriba no
        // cubre — ahora que el ParameterList ya no está "initialized", esta llamada SÍ hará el
        // trabajo real en vez de ser un no-op.
        LevelUtils.initializeBiomes(registryAccess, overworldStem.type(),
                net.minecraft.world.level.dimension.LevelStem.OVERWORLD, gen, SEED);

        // Sanity check: la posición EXACTA de la captura F3 del usuario (-1388, 63, 5403),
        // sin depender de ninguna alineación de rejilla de barrido.
        {
            int x = -1388, y = 63, z = 5403;
            int qx = QuartPos.fromBlock(x), qy = QuartPos.fromBlock(y), qz = QuartPos.fromBlock(z);
            var biomeHolder = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
            String biomeName = biomeHolder.unwrapKey().map(k -> k.location().toString()).orElse("???");
            var target = randomState.sampler().sample(qx, qy, qz);
            out.append(String.format("SANITY CHECK (%d,%d,%d) biome=%s C=%.3f E=%.3f D=%.3f W=%.3f T=%.3f H=%.3f%n",
                    x, y, z, biomeName,
                    Climate.unquantizeCoord(target.continentalness()), Climate.unquantizeCoord(target.erosion()),
                    Climate.unquantizeCoord(target.depth()), Climate.unquantizeCoord(target.weirdness()),
                    Climate.unquantizeCoord(target.temperature()), Climate.unquantizeCoord(target.humidity())));

            // NUEVO: qué REGIÓN gana posicionalmente en este punto (selección espacial, mismo eje
            // que decide "Region: zenkai:overworld" en el F3 real) — independiente de si el bioma
            // final resuelto en ESA región es un punto real o el DEFERRED_PLACEHOLDER. Esto separa
            // las dos hipótesis: (a) el weight ARREGLADO (5, antes 1) ya hace que esta posición deje
            // de pertenecer a nuestra región -> nunca llega a competir por clima; (b) sigue siendo
            // nuestra región pero pierde/gana la competencia climática dentro de su propio árbol.
            int uniqueness = extended.getUniqueness(qx, qy, qz);
            var winningRegion = extended.getRegion(uniqueness);
            out.append("Región ganadora (posicional) en ese punto: ").append(winningRegion.getName()).append("\n");
        }

        // Zona EXACTA que el usuario confirmó en juego (F3): rocky_wasteland como bioma
        // explícito pero sin bloques de tierra, solo agua, desde (-1341,45,5348) hasta
        // (-1407,66,5431). Barrido FINO (paso 4) sobre esa caja + margen, correlacionando
        // bioma con continentalness/weirdness crudos para encontrar la causa numérica.
        // OJO: el bioma depende de la Y consultada (eje "depth"), no solo de X/Z — el barrido
        // anterior muestreaba en groundY (el suelo real, y36-51 aquí) y SIEMPRE dio
        // deep_lukewarm_ocean; el usuario reportó rocky_wasteland de pie en Y=63 (agua, muy por
        // ENCIMA del suelo real) — hay que barrer también en Y, no solo X/Z.
        int x0 = Math.min(-1341, -1407) - 8, x1 = Math.max(-1341, -1407) + 8;
        int z0 = Math.min(5348, 5431) - 8, z1 = Math.max(5348, 5431) + 8;
        int y0 = 40, y1 = 70;
        int rockyCount = 0, total = 0;
        for (int x = x0; x <= x1; x += 8) {
            for (int z = z0; z <= z1; z += 8) {
                for (int y = y0; y <= y1; y += 2) {
                    var biomeHolder = biomeSource.getNoiseBiome(
                            QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), randomState.sampler());
                    String biomeName = biomeHolder.unwrapKey().map(k -> k.location().toString()).orElse("???");
                    var target = randomState.sampler().sample(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
                    float continentalness = Climate.unquantizeCoord(target.continentalness());
                    float weirdness = Climate.unquantizeCoord(target.weirdness());
                    float erosion = Climate.unquantizeCoord(target.erosion());
                    float depth = Climate.unquantizeCoord(target.depth());
                    total++;
                    boolean isRocky = biomeName.contains("rocky_wasteland");
                    if (isRocky) rockyCount++;
                    if (isRocky || (x == -1388 && z == 5403)) {
                        out.append(String.format("(%d,%d,%d) biome=%-32s C=%.3f E=%.3f D=%.3f W=%.3f %s%n",
                                x, y, z, biomeName, continentalness, erosion, depth, weirdness,
                                isRocky ? "<<< ROCKY_WASTELAND" : ""));
                    }
                }
            }
        }
        out.append(String.format("TOTAL rocky_wasteland en la caja reportada (con barrido en Y): %d/%d%n", rockyCount, total));

        // BARRIDO AMPLIO (nuevo, tras confirmar que el punto exacto reportado ya NO pertenece a
        // nuestra región bajo el weight arreglado — sección "Región ganadora" arriba): el weight
        // fix pudo simplemente DESPLAZAR qué celdas son nuestras sin arreglar el mecanismo de
        // fondo (sección 4 del pendiente: DEFERRED_PLACEHOLDER + dominancia de weirdness). Si ese
        // mecanismo sigue vivo, tiene que reproducirse en ALGUNA OTRA celda que hoy sí sea nuestra
        // — así que se barre una zona mucho más grande a nivel del mar (y=64, fijo, representativo
        // de "de pie en el agua" como el reporte original) buscando CUALQUIER punto donde (a)
        // nuestra región gane la competencia posicional Y (b) la continentalidad sea oceánica
        // (< COAST = -0.11, el umbral real de TerraBlender) Y (c) el bioma resuelto sea
        // rocky_wasteland de todos modos. Un solo hallazgo así confirma que el mecanismo de
        // "escape" sigue vivo en general, solo que ya no en el punto viejo concreto.
        int wx0 = -6000, wx1 = 6000, wz0 = -6000, wz1 = 6000, wstep = 48;
        int wy = 64;
        int qwy = QuartPos.fromBlock(wy);
        long oursTotal = 0, oursOceanic = 0, oursOceanicRocky = 0, escapeLogged = 0;
        long deepOceanEscapes = 0, oceanEscapes = 0, coastEscapes = 0;
        long landCells = 0, landRocky = 0;
        java.util.Map<String, Integer> landBiomeCounts = new java.util.TreeMap<>();
        double sumC = 0, sumE = 0, sumW = 0, sumT = 0, sumH = 0;
        for (int x = wx0; x <= wx1; x += wstep) {
            for (int z = wz0; z <= wz1; z += wstep) {
                int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
                int uniqueness = extended.getUniqueness(qx, qwy, qz);
                var winningRegion = extended.getRegion(uniqueness);
                if (!winningRegion.getName().toString().equals("zenkai:overworld")) continue;
                oursTotal++;
                var target = randomState.sampler().sample(qx, qwy, qz);
                float continentalness = Climate.unquantizeCoord(target.continentalness());
                if (continentalness >= 0.03f) {
                    // Tierra adentro válida (MID_INLAND+): control de que la reducción de área
                    // por el eje temperatura no haya dejado el bioma demasiado escaso ahí.
                    landCells++;
                    var landBiomeHolder = biomeSource.getNoiseBiome(qx, qwy, qz, randomState.sampler());
                    String landBiomeName = landBiomeHolder.unwrapKey().map(k -> k.location().toString()).orElse("???");
                    if (landBiomeName.contains("rocky_wasteland")) landRocky++;
                    landBiomeCounts.merge(landBiomeName, 1, Integer::sum);
                }
                if (continentalness >= -0.11f) continue; // no oceánico (>= COAST)
                oursOceanic++;
                var biomeHolder = biomeSource.getNoiseBiome(qx, qwy, qz, randomState.sampler());
                String biomeName = biomeHolder.unwrapKey().map(k -> k.location().toString()).orElse("???");
                if (biomeName.contains("rocky_wasteland")) {
                    oursOceanicRocky++;
                    float weirdness = Climate.unquantizeCoord(target.weirdness());
                    float erosion = Climate.unquantizeCoord(target.erosion());
                    float temperature = Climate.unquantizeCoord(target.temperature());
                    float humidity = Climate.unquantizeCoord(target.humidity());
                    sumC += continentalness; sumE += erosion; sumW += weirdness; sumT += temperature; sumH += humidity;
                    if (continentalness < -0.455f) deepOceanEscapes++;
                    else if (continentalness < -0.19f) oceanEscapes++;
                    else coastEscapes++;
                    if (escapeLogged < 20) {
                        out.append(String.format("ESCAPE (%d,%d,%d) biome=%s C=%.3f E=%.3f W=%.3f T=%.3f H=%.3f%n",
                                x, wy, z, biomeName, continentalness, erosion, weirdness, temperature, humidity));
                        escapeLogged++;
                    }
                }
            }
        }
        out.append(String.format(
                "BARRIDO AMPLIO y=%d, paso %d, caja [%d..%d]x[%d..%d]: nuestra región %d celdas, "
                        + "de esas oceánicas (C<-0.11) %d, de esas ROCKY_WASTELAND (bug) %d "
                        + "(deep_ocean=%d ocean=%d coast=%d)%n",
                wy, wstep, wx0, wx1, wz0, wz1, oursTotal, oursOceanic, oursOceanicRocky,
                deepOceanEscapes, oceanEscapes, coastEscapes));
        out.append(String.format("Tierra adentro válida (C>=0.03) en nuestra región: %d celdas, de esas ROCKY_WASTELAND %d (%.1f%%)%n",
                landCells, landRocky, landCells > 0 ? 100.0 * landRocky / landCells : 0.0));
        out.append("Composición de biomas en tierra adentro válida de nuestra región: ").append(landBiomeCounts).append("\n");
        if (oursOceanicRocky > 0) {
            out.append(String.format("Promedios en escapes: C=%.3f E=%.3f W=%.3f T=%.3f H=%.3f%n",
                    sumC / oursOceanicRocky, sumE / oursOceanicRocky, sumW / oursOceanicRocky,
                    sumT / oursOceanicRocky, sumH / oursOceanicRocky));
        }

        // ── RONDA 3: "se combina con savanna", "poca altura", "bajando se va a forest" ──────
        // Reporte nuevo (misma seed, punto (954, 43, -4)) tras confirmar que el escape oceánico
        // ya no aparece. Hipótesis a verificar con datos, no a ciegas: (a) nuestro climate box
        // (WARM..HOT + humidity seca + continentalness MID..FAR_INLAND) puede solaparse con el
        // nicho climático REAL de savanna en vainilla (también warm+seco+inland) — eso da un
        // borde "compitiendo cabeza a cabeza" en vez de una transición limpia, y se lee como
        // biomas mezclados/parcheados; (b) reducir weirdness a solo HIGH_SLICE+PEAK (Ronda 1)
        // pudo atar rocky_wasteland a terreno más "accidentado" en el esquema de vainilla —
        // MID_SLICE es la banda de colinas onduladas MÁS BAJAS/lisas, así que sin ella el bioma
        // solo gana en las zonas más altas/rugosas y pierde apenas el terreno baja, explicando
        // "poca altura" y "bajando se va a forest".
        out.append("\n=== RONDA 3: savanna/forest + altura (punto 954,43,-4) ===\n");
        {
            int x = 954, y = 43, z = -4;
            int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
            var target0 = randomState.sampler().sample(qx, QuartPos.fromBlock(y), qz);
            out.append(String.format("PUNTO (%d,%d,%d) C=%.3f E=%.3f D=%.3f W=%.3f T=%.3f H=%.3f%n",
                    x, y, z,
                    Climate.unquantizeCoord(target0.continentalness()), Climate.unquantizeCoord(target0.erosion()),
                    Climate.unquantizeCoord(target0.depth()), Climate.unquantizeCoord(target0.weirdness()),
                    Climate.unquantizeCoord(target0.temperature()), Climate.unquantizeCoord(target0.humidity())));
            int uniqueness0 = extended.getUniqueness(qx, QuartPos.fromBlock(y), qz);
            out.append("Región ganadora ahí: ").append(extended.getRegion(uniqueness0).getName()).append("\n");

            int groundY = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, helper.getLevel(), randomState);
            out.append("Altura real de suelo (WORLD_SURFACE_WG) en esa columna: ").append(groundY).append("\n");

            // Barrido VERTICAL en la misma columna — reproduce "bajando en el bioma se va a
            // forest" tal cual: mismo x/z, variando solo Y. Ahora también imprime el valor D
            // (depth) crudo en cada transición, para calibrar qué span de Depth cubre hasta
            // dónde hace falta (p.ej. hasta la altura típica de menas).
            out.append("Barrido vertical en (954,-4) (con D crudo en cada transición):\n");
            String lastBiome = null;
            for (int vy = -64; vy <= 100; vy += 2) {
                int qvy = QuartPos.fromBlock(vy);
                var b = biomeSource.getNoiseBiome(qx, qvy, qz, randomState.sampler());
                String bn = b.unwrapKey().map(k -> k.location().toString()).orElse("???");
                if (!bn.equals(lastBiome)) {
                    var t = randomState.sampler().sample(qx, qvy, qz);
                    out.append(String.format("  y=%d -> %s  (D=%.3f)%n", vy, bn, Climate.unquantizeCoord(t.depth())));
                    lastBiome = bn;
                }
            }
        }

        // Barrido HORIZONTAL alrededor del punto, a Y fija (43, la del reporte) — mapea el patrón
        // de mezcla real: ¿frontera limpia rocky/savanna, o parches intercalados (checkerboard)?
        out.append("Barrido horizontal alrededor de (954,-4) a y=43, paso 4, radio 96:\n");
        java.util.Map<String, Integer> biomeCounts = new java.util.TreeMap<>();
        int hx0 = 954 - 96, hx1 = 954 + 96, hz0 = -4 - 96, hz1 = -4 + 96, hstep = 4;
        int qhy = QuartPos.fromBlock(43);
        StringBuilder grid = new StringBuilder();
        for (int z = hz0; z <= hz1; z += hstep) {
            for (int x = hx0; x <= hx1; x += hstep) {
                var b = biomeSource.getNoiseBiome(QuartPos.fromBlock(x), qhy, QuartPos.fromBlock(z), randomState.sampler());
                String bn = b.unwrapKey().map(k -> k.location().toString()).orElse("???");
                biomeCounts.merge(bn, 1, Integer::sum);
                char c = bn.contains("rocky_wasteland") ? 'R' : bn.contains("savanna") ? 'S' : bn.contains("forest") ? 'F' : '.';
                grid.append(c);
            }
            grid.append('\n');
        }
        out.append("Leyenda: R=rocky_wasteland S=savanna F=forest .=otro (X=954±96, Z=-4±96, paso 4)\n");
        out.append(grid);
        out.append("Conteo por bioma en el barrido horizontal: ").append(biomeCounts).append("\n");

        Zenkai.LOGGER.warn(out.toString());

        helper.succeed();
    }

    /** Resetea a {@code false} un campo booleano privado tejido por Mixin en {@code target}. */
    private static void resetPrivateBoolean(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, false);
    }
}
