package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.PieceConnectorBlockEntity;
import com.hmc.zenkai.content.effect.MajinEffect;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.party.PartyService;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.skills.FormDrivenSkills;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.aura.TurboServerState;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
import com.hmc.zenkai.feature.technique.sequence.SequenceContext;
import com.hmc.zenkai.feature.technique.sequence.TechniqueSequence;
import com.hmc.zenkai.feature.technique.sequence.steps.PauseStep;
import com.hmc.zenkai.feature.technique.sequence.steps.VfxStep;
import com.hmc.zenkai.feature.training.TrainingCategory;
import com.hmc.zenkai.feature.training.TrainingData;
import com.hmc.zenkai.worldgen.ProtectedZones;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = e.getDispatcher();

        // ── /zenkai tp ───────────────────────────────────────────────────────
        // Añade TP de entrenamiento al jugador.
        // Uso: /zenkai tp add [objetivos] <cantidad>
        root.register(Commands.literal("zenkai")
                .requires(cs -> cs.hasPermission(2))

                .then(Commands.literal("tp")
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addTp(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> addTp(c, sp,
                                                                IntegerArgumentType.getInteger(c, "amount"))))))))

                // ── /zenkai attr ──────────────────────────────────────────────────
                // Manipula atributos individuales o en bloque.
                // Uso: /zenkai attr set [objetivos] <atributo> <valor>
                //      /zenkai attr setall [objetivos] <valor>
                //      /zenkai attr maxall [objetivos]
                .then(Commands.literal("attr")
                        .then(Commands.literal("set")
                                .then(Commands.argument("attr", StringArgumentType.word()).suggests(ATTR_IDS)
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setAttr(ctx,
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "attr"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("attr", StringArgumentType.word()).suggests(ATTR_IDS)
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> forEach(ctx, targets(ctx),
                                                                (c, sp) -> setAttr(c, sp,
                                                                        StringArgumentType.getString(c, "attr"),
                                                                        IntegerArgumentType.getInteger(c, "value"))))))))
                        .then(Commands.literal("setall")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setAllAttr(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "value"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> setAllAttr(c, sp,
                                                                IntegerArgumentType.getInteger(c, "value")))))))
                        .then(Commands.literal("maxall")
                                .executes(ctx -> maxAllAttr(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::maxAllAttr)))))

                .then(Commands.literal("respec")
                        .executes(ctx -> respec(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::respec))))

                // ── /zenkai race ──────────────────────────────────────────────────
                // Fuerza la raza de un jugador desde el servidor.
                // Uso: /zenkai race set [objetivos] <raza>
                .then(Commands.literal("race")
                        .then(Commands.literal("set")
                                .then(Commands.argument("race", StringArgumentType.string())
                                        .executes(ctx -> setRace(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "race"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("race", StringArgumentType.string())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> setRace(c, sp,
                                                                StringArgumentType.getString(c, "race"))))))))

                // ── /zenkai style ─────────────────────────────────────────────────
                // Fuerza el estilo de combate de un jugador desde el servidor.
                // Uso: /zenkai style set [objetivos] <estilo>
                .then(Commands.literal("style")
                        .then(Commands.literal("set")
                                .then(Commands.argument("style", StringArgumentType.string())
                                        .executes(ctx -> setStyle(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "style"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("style", StringArgumentType.string())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> setStyle(c, sp,
                                                                StringArgumentType.getString(c, "style"))))))))

                // ── /zenkai reset ─────────────────────────────────────────────────
                // Resetea el progreso del jugador.
                // /zenkai reset stats [objetivos] → devuelve los TP invertidos (respec)
                // /zenkai reset full  [objetivos] → borra raza, estilo, stats, TP y apariencia
                // /zenkai reset summon [objetivos] → quita el cooldown de invocar al dragón
                .then(Commands.literal("reset")
                        .then(Commands.literal("stats")
                                .executes(ctx -> resetStats(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::resetStats))))
                        .then(Commands.literal("full")
                                .executes(ctx -> resetFull(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::resetFull))))
                        .then(Commands.literal("training_fatigue")
                                .executes(ctx -> resetTrainingFatigue(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::resetTrainingFatigue))))
                        .then(Commands.literal("summon")
                                .executes(ctx -> resetSummon(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::resetSummon)))))

                .then(Commands.literal("revive")
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::revivePlayer))))

                // ── /zenkai pets ──────────────────────────────────────────────────
                // Borra el historial de mascotas muertas (deseo de revivir).
                // Uso: /zenkai pets clear [objetivos]
                .then(Commands.literal("pets")
                        .then(Commands.literal("clear")
                                .executes(ctx -> clearPets(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::clearPets)))))

                .then(Commands.literal("struct")
                        .then(Commands.literal("place")
                                .then(Commands.argument("which", StringArgumentType.word())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> {
                                                    var lvl = ctx.getSource().getLevel();
                                                    var pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                                    String which = StringArgumentType.getString(ctx, "which");
                                                    boolean ok = com.hmc.zenkai.worldgen.ZenkaiStructurePlacement.forcePlace(lvl, which, pos);
                                                    ProtectedZones.invalidateAll();
                                                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                                            ok ? "[Zenkai] Colocado " + which : "[Zenkai] Falló (revisa NBT/offsets)"), true);
                                                    return ok ? 1 : 0;
                                                })))))

                .then(Commands.literal("locate")
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            var zones = com.hmc.zenkai.worldgen.NoHostileSpawnZones.getZones();
                            if (zones.isEmpty()) { src.sendSuccess(() -> Component.literal("[Zenkai] No hay estructuras registradas."), false); return 0; }
                            for (var z : zones) {
                                var c = z.box().getCenter();
                                src.sendSuccess(() -> Component.translatable(z.protector())
                                        .withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal(String.format(
                                                " §7— %s §7@ §f%d %d %d",
                                                z.dimension().location(),
                                                (int) c.x, (int) c.y, (int) c.z))), false);
                            }
                            return 1;
                        }))

                // ── /zenkai skill ─────────────────────────────────────────────────
                // Otorga/revoca habilidades sin TP (la vía de maestros/NPCs).
                // Uso: /zenkai skill give|revoke <objetivos> <id>
                //      /zenkai skill giveall [objetivos]   → cada una al máximo
                //      /zenkai skill list [jugador]        → consulta (un solo jugador)
                .then(Commands.literal("skill")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .suggests(SKILL_IDS)
                                                // Sin nivel: sube uno, como antes.
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> skillGive(c, sp,
                                                                StringArgumentType.getString(c, "id"), -1)))
                                                // Con nivel: fija ese nivel exacto, recortado al techo.
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> forEach(ctx, targets(ctx),
                                                                (c, sp) -> skillGive(c, sp,
                                                                        StringArgumentType.getString(c, "id"),
                                                                        IntegerArgumentType.getInteger(c, "level"))))))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .suggests(SKILL_IDS)
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> skillRevoke(c, sp,
                                                                StringArgumentType.getString(c, "id")))))))
                        .then(Commands.literal("giveall")
                                .executes(ctx -> skillGiveAll(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::skillGiveAll))))
                        // list NO admite @a a propósito: es una consulta y con varios objetivos
                        // escupiría (nº skills × nº jugadores) líneas de chat, ilegible.
                        .then(Commands.literal("list")
                                .executes(ctx -> skillList(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> skillList(ctx, EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("phys")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("tech", StringArgumentType.word())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> physGive(c, sp,
                                                                StringArgumentType.getString(c, "tech"))))))))

                // ── /zenkai mastery ───────────────────────────────────────────────
                // Maestría de una FORMA o de un escalón de KAIOKEN, ambas en el mismo mapa.
                // Uso: /zenkai mastery set <objetivos> <id> <0-100>
                //      /zenkai mastery list [jugador]
                .then(Commands.literal("mastery")
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                                .suggests(MASTERY_IDS)
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                        .executes(ctx -> forEach(ctx, targets(ctx),
                                                                (c, sp) -> masterySet(c, sp,
                                                                        ResourceLocationArgument.getId(c, "id"),
                                                                        IntegerArgumentType.getInteger(c, "value"))))))))
                        .then(Commands.literal("list")
                                .executes(ctx -> masteryList(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> masteryList(ctx, EntityArgument.getPlayer(ctx, "player"))))))

                // ── /zenkai alignment ────────────────────────────────────────────
                // Fija el alineamiento directamente (rango real -100..+100, ver
                // AlignmentManager). Sin cota en el argumento a propósito: un valor fuera de
                // rango no rechaza el comando, se satura al extremo más cercano — mismo
                // comportamiento que PlayerStatsAttachment.setAlignment(int) ya tiene
                // internamente (Math.max(-100, Math.min(100, v))), así que basta con
                // reenviarle lo que sea y dejar que clampe.
                // Uso: /zenkai alignment set [objetivos] <valor>
                .then(Commands.literal("alignment")
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(ctx -> alignmentSet(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "value"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> alignmentSet(c, sp,
                                                                IntegerArgumentType.getInteger(c, "value"))))))))

                // ── /zenkai debug entity ─────────────────────────────────────────
                // Vuelca vida vanilla + pool zenkai de la entidad en el punto de mira.
                //
                .then(Commands.literal("debug")
                        .then(Commands.literal("entity")
                                .executes(ModCommands::debugEntity))
                        // Skill tree de transformaciones (F1): verify (por defecto) compara el espejo
                        // de nodos con SuperForms.unlocked(); nodes los lista; refresh lo recalcula.
                        .then(Commands.literal("tree")
                                .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::verify)
                                .then(Commands.literal("verify")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::verify))
                                .then(Commands.literal("nodes")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::nodes))
                                .then(Commands.literal("refresh")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::refresh))
                                // F5: audiencia, prueba y ritual sin maestro ni 5 jugadores; reset
                                // del árbol (el punto único de la futura reencarnación).
                                .then(Commands.literal("audience")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::audience))
                                .then(Commands.literal("trial")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::trial))
                                .then(Commands.literal("ritual")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::ritual))
                                .then(Commands.literal("reset")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::reset)))
                        // Todas las ramas "set a flag" de abajo (divine/legendary/immortal/
                        // majin) aceptan ahora un <player> opcional, MISMO patrón que el resto de
                        // /zenkai (ver "tp add"/"attr setall": sin argumento actúa sobre quien
                        // ejecuta, con él acepta cualquier selector de EntityArgument.players()
                        // — @a, @e[type=player], un nombre suelto...). Antes solo podían probarse
                        // sobre uno mismo, así que depurar el HUD/estado de OTRO jugador (o de
                        // varios a la vez) exigía pedirle que ejecutara el comando él mismo.
                        // "entity"/"party add" se quedan self-only a propósito: el primero
                        // raytracea la MIRADA de quien ejecuta y el segundo añade un miembro a SU
                        // propia party, ninguno de los dos tiene sentido apuntado a otro jugador.
                        // ── /zenkai debug divine|legendary <true|false> ──────────────
                        // TEMPORAL — la vía REAL de conseguir
                        // cada uno de estos dos estados (PlayerStateFlags.isDivine/isLegendary,
                        // hoy solo leídos por el HUD — ver ClientZenkaiHooks) no existe todavía
                        // (Divino se piensa como "tomarse un té de agua divina", un ítem que
                        // aún no existe). Hasta que esa vía real exista, este comando es la
                        // única forma de probar el HUD. Borrar cada rama cuando su mecanismo
                        // real quede implementado. NO hay un tercer estado "majin" aparte de
                        // isMajinControlled (ver la rama "majin" más abajo) — PlayerStateFlags
                        // llegó a tener un campo isMajin aparte que no representaba nada real;
                        // se eliminó (junto con su reset en resetFull()) para no volver a
                        // confundir los dos.
                        .then(Commands.literal("divine")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSetSpecialState(ctx,
                                                ctx.getSource().getPlayerOrException(), "isDivine",
                                                PlayerStatsAttachment::setDivine,
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> debugSetSpecialState(c, sp, "isDivine",
                                                                PlayerStatsAttachment::setDivine,
                                                                BoolArgumentType.getBool(c, "value")))))))
                        .then(Commands.literal("legendary")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSetSpecialState(ctx,
                                                ctx.getSource().getPlayerOrException(), "isLegendary",
                                                PlayerStatsAttachment::setLegendary,
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> debugSetSpecialState(c, sp, "isLegendary",
                                                                PlayerStatsAttachment::setLegendary,
                                                                BoolArgumentType.getBool(c, "value")))))))
                        // ── /zenkai debug immortal <true|false> ───────────────────────
                        // A diferencia de divine/legendary, "immortal" también tiene un
                        // mecanismo real (PlayerStatsAttachment.isImmortal +
                        // PersistentEffectsSystem reaplicando ModEffects.IMMORTALITY mientras
                        // el flag siga puesto) — mismo espíritu que "debug majin": solo ahorra
                        // pasar por lo que sea que hoy dé inmortalidad, para pruebas rápidas.
                        .then(Commands.literal("immortal")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSetSpecialState(ctx,
                                                ctx.getSource().getPlayerOrException(), "isImmortal",
                                                PlayerStatsAttachment::setImmortal,
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> debugSetSpecialState(c, sp, "isImmortal",
                                                                PlayerStatsAttachment::setImmortal,
                                                                BoolArgumentType.getBool(c, "value")))))))
                        // ── /zenkai debug majin <true|false> ─────────────────────────
                        // A diferencia de divine/legendary de arriba, "majin" SÍ tiene un
                        // mecanismo real ya implementado: PersistentEffectsSystem.tick()
                        // reaplica ModEffects.MAJIN cada tick mientras
                        // PlayerVisualAttachment.isMajinControlled() sea true, Y AHORA TAMBIÉN
                        // lo retira en cuanto el flag pasa a false (antes solo lo hacía la
                        // rama "true" — sin remove, MajinEffect.applyEffectTick() reactivaba el
                        // flag solo, deshaciendo tanto este comando como /zenkai reset un tick
                        // después; ver el comentario de PersistentEffectsSystem.tick()). Este
                        // comando solo ahorra tener que pasar por lo que sea que hoy active ese
                        // flag durante pruebas — no es un placeholder de algo que falte por
                        // construir, así que no hay nada "que borrar más adelante".
                        .then(Commands.literal("majin")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSetMajinControlled(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> debugSetMajinControlled(c, sp,
                                                                BoolArgumentType.getBool(c, "value")))))))
                        // ── /zenkai debug connector set <socket> ──────────────────────
                        // Atajo sin abrir PieceConnectorScreen (clic derecho normal en el
                        // conector — ver worldgen.piecegraph): útil para edición por script o
                        // desde lejos. socket es un ResourceLocation libre, p.ej.
                        // zenkai:namek_path — no hay pool que registrar en JSON,
                        // PieceTemplate#scan lo lee directo del NBT al cargar la pieza.
                        .then(Commands.literal("connector")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("socket", ResourceLocationArgument.id())
                                                .executes(ctx -> debugSetConnectorSocket(ctx,
                                                        ResourceLocationArgument.getId(ctx, "socket"))))))
                        // ── /zenkai debug kivfx spawn <type> [size] ───────────────────
                        // Ataque de ki CONGELADO en el aire, a 6 bloques de donde mira quien
                        // ejecuta — pensado para comparar el aspecto real contra las referencias
                        // de .claude/imagenes/ sin tener que dispararlo de verdad ni perseguirlo
                        // en vuelo. No se mueve, no hace daño (KiProjectileEntity.freeze) y NO
                        // expira solo: hay que borrarlo con "kivfx clear". Tamaño por defecto 3
                        // (mitad de la escala normal 1-5); tope en 10, NO en 100 como en un
                        // primer intento — casi todos los números de este sistema (radio de
                        // rayos, escala de chispas/arcos, envolvente...) se calibraron pensando
                        // en la escala 1-5 y algunos directamente escalan por `size()` en vez de
                        // por el diámetro real; 10 da margen para ver una técnica más grande de
                        // lo jugable sin salirse tanto del rango calibrado como para que el
                        // resultado deje de leerse como la propia técnica (confirmado con
                        // capturas: a tamaños mucho mayores, partículas y rayos pensados para
                        // motas pequeñas se leen como bloques sueltos "sin seguir" la malla).
                        .then(Commands.literal("kivfx")
                                .then(Commands.literal("spawn")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(KI_TYPE_IDS)
                                                .executes(ctx -> debugSpawnKiVfx(ctx,
                                                        StringArgumentType.getString(ctx, "type"), 3))
                                                .then(Commands.argument("size", IntegerArgumentType.integer(1, 10))
                                                        .executes(ctx -> debugSpawnKiVfx(ctx,
                                                                StringArgumentType.getString(ctx, "type"),
                                                                IntegerArgumentType.getInteger(ctx, "size"))))))
                                // "spawnall" hace en un solo comando lo que "spawn" repetido 16
                                // veces: toda la familia en fila, mismo tamaño, para una
                                // comparativa de un vistazo en vez de ir una por una.
                                .then(Commands.literal("spawnall")
                                        .executes(ctx -> debugSpawnAllKiVfx(ctx, 3))
                                        .then(Commands.argument("size", IntegerArgumentType.integer(1, 10))
                                                .executes(ctx -> debugSpawnAllKiVfx(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "size")))))
                                .then(Commands.literal("clear")
                                        .executes(ModCommands::debugClearKiVfx)))
                        // ── /zenkai debug sequence ────────────────────────────────────
                        // Caso SINTÉTICO del andamiaje de TechniqueSequence (paso 3 del roadmap,
                        // ver .claude/pendiente/cobblemon-audit-vfx-animaciones.md §20): NINGUNA
                        // técnica real llama a TechniqueSequence todavía (ninguna lo necesita hoy,
                        // ver el javadoc de TechniqueSequence) — esto solo prueba que el andamiaje
                        // en sí encadena, espera y dibuja en el orden correcto. 2 destellos con 1s
                        // de diferencia, delante de quien ejecuta, tintados con SU color de aura.
                        .then(Commands.literal("sequence")
                                .executes(ModCommands::debugRunSequence))
                        // ── /zenkai debug stun [ticks] ────────────────────────────────
                        // Aturde a quien ejecuta y a los muñecos cercanos (StunState.apply, con
                        // sus frenos: tope, rendimientos decrecientes e inmunidad).
                        .then(Commands.literal("stun")
                                .executes(c -> debugStun(c, 60))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 600))
                                        .executes(c -> debugStun(c, IntegerArgumentType.getInteger(c, "ticks")))))
                        // ── /zenkai debug party [n] | clear ───────────────────────────
                        // Llena tu party con compañeros FALSOS (vitales inventados) para probar el panel
                        // de party en un mundo de un solo jugador.
                        .then(Commands.literal("party")
                                .executes(c -> debugParty(c, 4))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 8))
                                        .executes(c -> debugParty(c, IntegerArgumentType.getInteger(c, "n"))))
                                .then(Commands.literal("clear")
                                        .executes(ModCommands::debugPartyClear)))
                        // ── /zenkai debug body spawn|fill|clear ───────────────────────
                        // Cuerpos de jugador (muerte permanente, fase 2-3). spawn: uno vacío a tus pies.
                        // fill: uno con una copia de tu inventario (no te lo quita). clear: borra los tuyos.
                        .then(Commands.literal("body")
                                .then(Commands.literal("spawn").executes(c -> debugBody(c, false)))
                                .then(Commands.literal("fill").executes(c -> debugBody(c, true)))
                                .then(Commands.literal("clear").executes(ModCommands::debugBodyClear)))
                        // ── /zenkai debug dummy ───────────────────────────────────────
                        // Muñeco de pruebas: una Sombra con guion que hace UNA sola cosa
                        // (spamear ki, quedarse quieta, guardia...), para probar el choque de
                        // haces y demás sin esperar a la IA. Ver ShadowDebugMode.
                        .then(Commands.literal("dummy")
                                .then(dummyModes(true))
                                .then(dummyModes(false))
                                .then(Commands.literal("clear")
                                        .executes(ModCommands::debugClearDummies))
                                .then(Commands.literal("clash")
                                        .executes(c -> debugDummyClash(c, "wave"))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(KI_TYPE_IDS)
                                                .executes(c -> debugDummyClash(c,
                                                        StringArgumentType.getString(c, "type")))))
                                // ── /zenkai debug dummy grab ──────────────────────────
                                // Un muñeco te AGARRA para probar el skill check de escape (clic/espacio en la
                                // zona dorada, Evasiva o Instant Transmission). Sin límite de tiempo, como el real.
                                .then(Commands.literal("grab")
                                        .executes(ModCommands::debugDummyGrab))
                                // Ritual de God Ki con muñecos de participantes (flujo real, cargando ki).
                                .then(Commands.literal("ritual")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::ritualDummies))
                                .then(Commands.literal("join")
                                        .then(Commands.literal("ally")
                                                .executes(c -> debugDummyJoin(c, true)))
                                        .then(Commands.literal("rival")
                                                .executes(c -> debugDummyJoin(c, false))))))



        );
    }

    private static int debugBody(CommandContext<CommandSourceStack> c, boolean fill) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        java.util.List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
        if (fill) {
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                var s = sp.getInventory().getItem(i);
                if (!s.isEmpty()) items.add(s.copy());
            }
        }
        var body = com.hmc.zenkai.feature.player.death.PlayerBodyManager.spawnFor(sp, sp.serverLevel(), items);
        if (body == null) {
            c.getSource().sendFailure(Component.literal("No se pudo crear el cuerpo."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Cuerpo creado" + (fill ? " con una copia de tu inventario." : " vacío.")), false);
        return 1;
    }

    private static int debugBodyClear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        var reg = com.hmc.zenkai.feature.player.death.PlayerBodyRegistry.get(c.getSource().getServer());
        int n = 0;
        for (var e : reg.of(sp.getUUID())) {
            var lvl = c.getSource().getServer().getLevel(e.dimension());
            if (lvl != null) {
                lvl.getChunkAt(e.pos());
                if (lvl.getEntity(e.bodyId()) != null) { lvl.getEntity(e.bodyId()).discard(); n++; continue; }
            }
            reg.remove(e.bodyId());
        }
        int removed = n;
        c.getSource().sendSuccess(() -> Component.literal("Cuerpos borrados: " + removed + " (sin soltar su contenido)."), false);
        return removed;
    }

    private static int debugParty(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int added = com.hmc.zenkai.feature.party.PartyService.debugFill(c.getSource().getServer(), sp, n);
        c.getSource().sendSuccess(() -> Component.literal("Party de prueba: +" + added
                + " compañeros falsos. Elígelos con el diamante en la pestaña Party."), false);
        return added;
    }

    private static int debugPartyClear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int removed = com.hmc.zenkai.feature.party.PartyService.debugClear(c.getSource().getServer(), sp);
        c.getSource().sendSuccess(() -> Component.literal("Quitados " + removed + " compañeros falsos."), false);
        return removed;
    }

    // ── Selectores múltiples ─────────────────────────────────────────────────
    // Cada uno de los <player> usa EntityArgument.players(), así que aceptan @a, @p, @r, @s,
    // @e[type=player] o un nombre suelto. La acción corre UNA VEZ por objetivo y cada
    // implementación se queda tal cual (firma de un solo ServerPlayer).

    @FunctionalInterface
    private interface PlayerAction {
        int run(CommandContext<CommandSourceStack> ctx, ServerPlayer sp);
    }

    /** Objetivos del argumento "player". Lanza si el selector no encuentra a nadie (vanilla). */
    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "player");
    }

    /**
     * Ejecuta la acción sobre cada objetivo y devuelve CUÁNTOS tuvieron éxito: es el valor
     * que lee /execute store result, así que sirve para encadenar.
     * Con más de un objetivo añade una línea de resumen (las implementaciones ya emiten
     * su propio mensaje por jugador).
     */
    private static int forEach(CommandContext<CommandSourceStack> ctx,
                               Collection<ServerPlayer> targets, PlayerAction action) {
        int ok = 0;
        for (ServerPlayer sp : targets) {
            if (action.run(ctx, sp) > 0) ok++;
        }
        if (targets.size() > 1) {
            final int n = ok, total = targets.size();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[Zenkai] " + n + "/" + total + " jugador(es) afectados"), true);
        }
        return ok;
    }

    // ── Implementaciones ─────────────────────────────────────────────────────

    /** Ver el comentario de las ramas "debug divine/majin/legendary" más arriba. Un solo helper
     *  para los tres en vez de triplicar el mismo cuerpo — solo cambia la etiqueta y qué setter
     *  de PlayerStatsAttachment llamar. Acepta el objetivo como parámetro (en vez de derivarlo
     *  de quien ejecuta) para poder usarse tanto self-only como con un <player> explícito. */
    private static int debugSetSpecialState(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String label,
                                            java.util.function.BiConsumer<PlayerStatsAttachment, Boolean> setter,
                                            boolean value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        setter.accept(att, value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + label + " = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Ver el comentario de la rama "debug majin" más arriba. A diferencia de
     *  debugSetSpecialState, esto vive en PlayerVisualAttachment (isMajinControlled), no en
     *  PlayerStatsAttachment — son dos attachments distintos. */
    private static int debugSetMajinControlled(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, boolean value) {
        // MajinEffect.setControlled hace TRES cosas juntas y SÍNCRONAS: flag, MobEffectInstance
        // y sync visual. No basta con tocar solo el flag (ver el javadoc de ese método para la
        // condición de carrera exacta que eso produce con el propio tick del efecto).
        MajinEffect.setControlled(sp, value);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] isMajinControlled = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int addTp(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int amount) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.addTP(amount);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] +" + amount + " TP → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setAttr(CommandContext<CommandSourceStack> ctx,
                               ServerPlayer sp, String attrName, int value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        ZenkaiAttributes a = parseAttr(attrName);
        if (a == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown attribute: " + attrName
                    + ". Valid: strength, constitution, dexterity, willpower, spirit, mind (or STR, CON, DEX, WIL, SPI, MND)"));
            return 0;
        }
        att.setAttribute(a, value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] " + a + " = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Ver el comentario de la rama "alignment" más arriba. {@code value} llega SIN acotar:
     *  setAlignment() ya lo satura a [-100, 100], así que un admin puede pedir 9999 o -9999
     *  sin que el comando falle — simplemente queda en el extremo correspondiente. */
    private static int alignmentSet(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.setAlignment(value);
        PlayerLifeCycle.sync(sp);
        int applied = att.getAlignment();
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Alignment = " + applied + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int v = Math.max(0, Math.min(value, ServerConfig.globalAttributeCap()));
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) att.setAttribute(a, v);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] All attributes = " + v + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int maxAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        return setAllAttr(ctx, sp, ServerConfig.globalAttributeCap());
    }

    private static int setRace(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String raceName) {
        Race r;
        try { r = Race.valueOf(raceName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown race: " + raceName));
            return 0;
        }
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setRace(r);
        com.hmc.zenkai.feature.race.FaceBans.reconcile(r, sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()));
        PlayerLifeCycle.sync(sp);
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Race = " + r + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setStyle(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String styleName) {
        Style s;
        try { s = Style.valueOf(styleName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown style: " + styleName));
            return 0;
        }
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setStyle(s);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Style = " + s + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Respec: devuelve los TP invertidos sin tocar raza ni estilo. */
    private static int resetStats(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).respec();
        // Las formas también se pierden: hay que volver a aprenderlas. Va aquí y no dentro
        // de respec() porque PlayerStatsAttachment no tiene referencia al Player, y la forma
        // vive en otro attachment.
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).clearProgression();
        PlayerLifeCycle.sync(sp);
        // clearProgression() ya deja formId en BASE en el attachment del SERVIDOR, pero
        // PlayerLifeCycle.sync() solo manda SyncPlayerStatsPacket: sin este sync aparte, el
        // cliente (propio y quien lo esté trackeando) se queda con la forma vieja cacheada y
        // sigue renderizando la transformación anterior encima del respec.
        PlayerLifeCycle.syncForm(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Stats respec done → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Reset completo: raza, estilo, stats, TP y apariencia visual (pelo, ojos, colores, etc.).
     * El jugador deberá pasar por la creación de personaje de nuevo.
     */
    private static int resetFull(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        // ── Stats ─────────────────────────────────────────────────────────────
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.skills().clear();
        // Técnicas físicas y de ki aprendidas: submódulo aparte de las habilidades, así que
        // skills().clear() no las toca. Un reset "full" que las dejara vivas no sería full.
        att.techniques().clearAll();
        // Maestría de técnica: la de forma/kaioken ya se limpia abajo vía
        // PLAYER_FORM.clearProgression(), pero esa no toca la de PlayerStatsAttachment.
        att.clearTechniqueMastery();
        att.setRace(Race.HUMAN);
        att.setStyle(Style.MARTIAL_ARTIST);
        att.respec();
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).clearProgression();
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).forceBase();
        att.addTP(-att.getTP());
        att.setRaceChosen(false);
        att.setStyleChosen(false);
        att.setFlyEnabled(false);
        att.setImmortal(false);
        att.setLegendary(false);
        att.setDivine(false);
        // "Majin" no tiene flag propio en PlayerStatsAttachment (ese campo se eliminó por
        // vestigial, no representaba nada) — el estado real es
        // PlayerVisualAttachment.isMajinControlled, y ese YA queda limpio más abajo cuando el
        // reset recarga el attachment visual entero con los valores por defecto.
        // Ritual de Oozaru: un reset "full" que dejara esto vivo permitiría re-desbloquear
        // SSJ4 sin repetir el ritual en cuanto se re-comprara el nivel — no sería full. La
        // cola vuelve a su valor por defecto (true) por la misma razón de "estado limpio",
        // aunque el ítem de cola puede haberla puesto en false.
        att.setHasSsj4Ritual(false);
        att.setBeastFury(false); // idem: Beast se vuelve a desbloquear con una nueva furia
        att.setReachedSuperOozaru(false);
        // Skill tree: nodos propios (pasivas, formas divinas), God Ki y audiencia divina. Mismo
        // punto único que usará la reencarnación (SkillTree.resetTree).
        com.hmc.zenkai.feature.skilltree.SkillTree.resetTree(sp);
        att.setHasTail(true);
        // Servicio de Kaiosama: un reset "full" también devuelve el derecho a pedir las
        // pesas de nuevo, igual que borra lo demás — no sería full si dejara este
        // regalo de una sola vez "ya gastado" para una vida completamente nueva.
        att.setReceivedKaioWeights(false);
        att.setAlignment(0);
        // Sin deseos concedidos todavía: el cooldown de Shenlong solo corre desde el primer deseo.
        // (Antes esto guardaba la DURACIÓN del cooldown como si fuera un instante: tras un reset
        // el jugador quedaba bloqueado para invocar hasta que el mundo cumpliera esos días.)
        att.setLastSummonTick(Long.MIN_VALUE);
        // No se queda cargando ki ni con el % de poder subido: vuelve al 50% base (el mismo
        // que da SkillEffects.maxPowerPercent con Ki Control en 0, que es donde acaba de
        // dejarlo skills().clear() de arriba).
        att.setChargingKi(false);
        att.setPowerPercent(50, SkillEffects.maxPowerPercent(sp));
        // El turbo vive en un mapa estático aparte (TurboServerState), no en el attachment:
        // sin esto el jugador seguía drenando ki con el modo turbo encendido tras el reset.
        TurboServerState.set(sp, false);

        AttributeInstance scaleAttr = sp.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(1.0D);
            scaleAttr.getModifiers().forEach(scaleAttr::removeModifier);
        }

        PlayerLifeCycle.sync(sp);
        // clearProgression()+forceBase() ya dejan formId en BASE en el attachment del
        // SERVIDOR, pero PlayerLifeCycle.sync() solo manda SyncPlayerStatsPacket: sin este
        // sync aparte, el cliente (propio y quien lo esté trackeando) se queda con la forma
        // vieja cacheada — así es como un reset full seguido de elegir una raza nueva podía
        // dejar, por ejemplo, a un Human recién creado con el aura/escala/pelo de un SSJ4.
        PlayerLifeCycle.syncForm(sp);
        // ── Visual (pelo, ojos, colores, índices) ─────────────────────────────
        // Cargar un attachment limpio con el conjunto de valores por defecto
        var visual = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.load(new PlayerVisualAttachment().save());
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        // El load() de arriba ya deja majinControlled=false en el attachment, pero NO quita el
        // MobEffectInstance si el jugador tenía Majin activo — sin este removeEffect síncrono,
        // el propio tick del efecto (MajinEffect.applyEffectTick, que corre ANTES que
        // PersistentEffectsSystem dentro del mismo tick de juego) lo detecta todavía presente y
        // vuelve a poner el flag en true, deshaciendo el reset. Ver el javadoc de
        // MajinEffect.setControlled para el porqué exacto de la condición de carrera.
        sp.removeEffect(ModEffects.MAJIN);

        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Full reset done → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Reinicia a 0 la fatiga/carry/lastDecay de las TRES categorías de entrenamiento (ver
     * TrainingCategory: COMBAT/MEDITATION/TARGET_PRACTICE, cada una independiente desde
     * 2026-09-09) — pedido explícito del usuario. NO toca récords (bestMeditationTp/
     * bestTargetPracticeTp/bestShadowTp) ni el TP ya ganado: esto solo reinicia el "cansancio"
     * que penaliza la eficiencia de seguir ganando, no el progreso conseguido.
     */
    private static int resetTrainingFatigue(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();
        for (TrainingCategory cat : TrainingCategory.values()) {
            td.setFatigue(cat, 0.0);
            td.setCarry(cat, 0.0);
            td.setLastDecayTime(cat, now);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Training fatigue reset → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Devuelve el derecho a invocar al dragón: borra el cooldown POR JUGADOR de la invocación. Ese cooldown
     * ({@code summon_cooldown_ticks}) corre desde el último DESEO concedido (WishFinalizer guarda el instante en
     * {@code lastSummonTick}); {@code Long.MIN_VALUE} es "sin deseos todavía", o sea sin cooldown. No toca el
     * gamerule de invocación ni al dragón que ya esté invocado.
     */
    private static int resetSummon(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setLastSummonTick(Long.MIN_VALUE);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Dragon summon cooldown reset → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int clearPets(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int n = att.getDeadPets().size();
        att.clearDeadPets();
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Cleared " + n + " dead pet(s) → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int revivePlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        boolean ok = com.hmc.zenkai.feature.player.OtherworldManager.revive(target);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] " + target.getGameProfile().getName() + " is not in the otherworld."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Revived " + target.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Otorga una habilidad SIN TP para pruebas de admin (grantAdmin: sobrevive al respec,
     * pero solo el nivel 1 queda protegido de olvidarse por la X de SkillsScreen — el resto
     * del nivel dado se puede bajar y reembolsa TP con normalidad. Ver
     * PlayerSkills.grantAdmin()).
     *
     * @param requested nivel exacto que se quiere dejar, o -1 para "sube uno".
     *                  Se RECORTA al techo en vez de fallar: si el máximo es 5 y pides 10,
     *                  te quedas en 5. Fallar ahí solo obliga a consultar el max a mano.
     */
    private static int skillGive(CommandContext<CommandSourceStack> ctx, ServerPlayer sp,
                                 String id, int requested) {
        SkillDef def = SkillDef.get(id);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown skill: " + id));
            return 0;
        }
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());

        // Mismo techo que SkillBuyPacket y giveAll: con levels_from_forms manda la cadena de
        // formas de SU raza, no el max_level del JSON — y FormDrivenSkills es quien sabe si esa
        // cadena es la de super_forms o la de god_ki (u otra futura), no siempre la primera.
        int max = FormDrivenSkills.maxLevel(def, sp);
        if (max <= 0) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] '" + id
                    + "' no está disponible para la raza de " + sp.getGameProfile().getName()));
            return 0;
        }

        int cur = att.skills().level(id);
        int target = Math.min(requested < 0 ? cur + 1 : requested, max);

        if (target <= cur) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] "
                    + sp.getGameProfile().getName() + " ya está en lvl " + cur + " de " + id
                    + " (máx " + max + ")"));
            return 0;
        }

        // grantAdmin(), no grant(): protege solo el nivel 1, no "target" entero — si no, un
        // /give de prueba a nivel alto quedaría bloqueado en la UI de olvidar de arriba abajo.
        // Ver el javadoc de PlayerSkills.grantAdmin().
        att.skills().grantAdmin(id, target);
        PlayerLifeCycle.sync(sp);

        final int t = target;
        final boolean capped = requested > max;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Skill '" + id + "' lvl " + t + " → " + sp.getGameProfile().getName()
                        + (capped ? " (recortado desde " + requested + ")" : "")), true);
        return 1;
    }

    /** Revoca aunque el id ya no exista en el registro (limpia huérfanas). */
    private static int skillRevoke(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String id) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (!att.skills().revoke(id)) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] "
                    + sp.getGameProfile().getName() + " doesn't have: " + id));
            return 0;
        }
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Skill '" + id + "' revoked ← " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Otorga el conjunto de habilidades del datapack al máximo. Son "granted" (grantAdmin), no
     * compradas: sobreviven al respec y no consumen TP — pero solo el nivel 1 de cada una
     * queda protegido de olvidarse por la X de SkillsScreen, el resto se puede bajar con
     * normalidad. Ver PlayerSkills.grantAdmin().
     * OJO con levels_from_forms: su techo real NO es def.maxLevel(), sino la cadena de
     * formas de SU raza (misma regla que SkillBuyPacket, vía FormDrivenSkills — que además
     * decide CUÁL cadena, super_forms o god_ki). Sin este mínimo, un humano acabaría con
     * niveles de super_forms que no existen para él, o cualquier raza con niveles de god_ki
     * que no existen para ella (ver el bug real: god_ki quedó en 5/2 porque este método
     * comparaba SIEMPRE contra SuperForms.maxLevel, sin importar qué skill fuera).
     */
    private static int skillGiveAll(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        if (SkillDef.all().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "[Zenkai] No skills loaded (¿datapack sin cargar? prueba /reload)"));
            return 0;
        }
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int granted = 0, capped = 0;
        for (SkillDef def : SkillDef.all()) {
            int max = FormDrivenSkills.maxLevel(def, sp);
            if (max <= 0) { capped++; continue; }   // raza sin cadena de formas
            if (att.skills().level(def.id()) >= max) continue;
            att.skills().grantAdmin(def.id(), max);   // solo protege el nivel 1, ver javadoc
            granted++;
        }
        PlayerLifeCycle.sync(sp);
        final int g = granted, c = capped;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + g + " skill(s) maxed → " + sp.getGameProfile().getName()
                        + (c > 0 ? " (" + c + " N/A para su raza)" : "")), true);
        return 1;
    }

    /** Lista las habilidades del datapack con el nivel actual del jugador. */
    private static int skillList(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var all = SkillDef.all();
        if (all.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] No skills loaded."));
            return 0;
        }
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "[Zenkai] " + all.size() + " skill(s) — " + sp.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);
        for (SkillDef def : all) {
            int max = FormDrivenSkills.maxLevel(def, sp);
            int lvl = att.skills().level(def.id());
            String extra = (def.hasMaster() ? " §8master:" + String.join(",", def.masters()) : "")
                    + (def.purchasable() ? "" : " §8[no comprable]")
                    + (def.levelsFromForms() ? " §8[formas]" : "");
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("§7- §f%s §7%d/%d §8· §7%d TP%s",
                            def.id(), lvl, max, def.tpCost(), extra)), false);
        }
        return 1;
    }

    /** Desbloquea una física SIN TP (pruebas; maestros después). */
    private static int physGive(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String name) {
        var t = PhysicalTechnique.byName(
                name.toUpperCase(java.util.Locale.ROOT));
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown: " + name
                    + " (dash_punch|heavy_blow|barrage|kiai|rising_slash|cross_slash|piercing_thrust|spin_slash|sky_cleave|light_blade|dive|grab|evade)"));
            return 0;
        }
        PlayerStatsAttachment.get(sp).techniques().unlock(t);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + t.name() + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Autocompleta formas del datapack y la clave ÚNICA de kaioken.
     * Sugería los cinco escalones por su etiqueta ("x2".."x20"), y eso ya no describe el
     * modelo: la maestría de kaioken dejó de guardarse por escalón y las cinco entradas
     * apuntaban a la MISMA clave (ver PlayerFormAttachment.kaiokenMasteryKey y la migración de
     * su load()). Cinco sugerencias para un solo valor no son un atajo, son una mentira sobre
     * cómo funciona: quien ponía x20 al 100% esperaba que x2 siguiera a cero.
     * Los alias antiguos SIGUEN aceptándose al escribirlos (resolveMasteryId), solo dejan de
     * ofrecerse.
     */
    /** Atributos para /zenkai attr set: antes el admin tenía que adivinar el nombre (y "STR",
     *  que sugería el propio mensaje de error, no funcionaba). */
    private static final SuggestionProvider<CommandSourceStack> ATTR_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) ids.add(a.name().toLowerCase(java.util.Locale.ROOT));
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** Nombre completo o abreviatura de 3 letras (str, con, dex, wil, spi, mnd); null si no existe. */
    @org.jetbrains.annotations.Nullable
    private static ZenkaiAttributes parseAttr(String raw) {
        String s = raw.toUpperCase(java.util.Locale.ROOT);
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) if (a.name().equals(s)) return a;
        return switch (s) {
            case "STR" -> ZenkaiAttributes.STRENGTH;
            case "CON" -> ZenkaiAttributes.CONSTITUTION;
            case "DEX" -> ZenkaiAttributes.DEXTERITY;
            case "WIL" -> ZenkaiAttributes.WILLPOWER;
            case "SPI" -> ZenkaiAttributes.SPIRIT;
            case "MND" -> ZenkaiAttributes.MIND;
            default -> null;
        };
    }

    private static final SuggestionProvider<CommandSourceStack> MASTERY_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (FormDef d : FormDef.all()) ids.add(d.id().toString());
        ids.add(PlayerFormAttachment.kaiokenMasteryKey().toString());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** Ids del datapack de habilidades. Sin namespace: el id de una SkillDef es la ruta del
     *  archivo, no un ResourceLocation. */
    private static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (SkillDef d : SkillDef.all()) ids.add(d.id());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** Los 16 valores de KiTechniqueType, en minúscula (== id() de cada uno). Para
     *  "/zenkai debug kivfx spawn". */
    private static final SuggestionProvider<CommandSourceStack> KI_TYPE_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (KiTechniqueType t : KiTechniqueType.values()) ids.add(t.id());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /**
     * Admite "kaioken", cualquier etiqueta de escalón ("x20"), "ssj4" y "zenkai:ssj4".
     * Brigadier resuelve un id sin namespace como minecraft:, así que ese caso se reinterpreta
     * como zenkai: — es lo que quiere decir el jugador el 100% de las veces.
     * Las etiquetas de escalón siguen aceptándose por comodidad, pero cada una apunta a la MISMA
     * clave: la maestría de kaioken ya no se guarda por escalón.
     */
    private static ResourceLocation resolveMasteryId(ResourceLocation raw) {
        String path = raw.getPath();
        if (path.equals("kaioken")) return PlayerFormAttachment.kaiokenMasteryKey();
        for (KaiokenTier t : KaiokenTier.values()) {
            if (t.isOn() && t.label().equals(path)) return PlayerFormAttachment.kaiokenMasteryKey();
        }
        return "minecraft".equals(raw.getNamespace())
                ? ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path)
                : raw;
    }

    private static int masterySet(CommandContext<CommandSourceStack> ctx, ServerPlayer sp,
                                  ResourceLocation rawId, int value) {
        ResourceLocation id = resolveMasteryId(rawId);
        var form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        form.setMastery(id, value);
        // syncForm y NO sync: la maestría vive en PLAYER_FORM. Con sync() el cliente seguiría
        // mostrando el valor viejo en la pantalla de stats.
        PlayerLifeCycle.syncForm(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + id + " mastery = " + value + "% → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Lista lo que el jugador tiene entrenado (formas y escalones mezclados, es un solo mapa). */
    private static int masteryList(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        var view = form.masteryView();
        if (view.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[Zenkai] " + sp.getGameProfile().getName() + " no tiene maestría en nada."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "[Zenkai] " + view.size() + " entrada(s) — " + sp.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);
        view.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(en -> ctx.getSource().sendSuccess(() -> Component.literal(
                        String.format("§7- §f%s §7%.1f%%", en.getKey(), en.getValue())), false));
        return 1;
    }

    private static int debugEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        Vec3 start = sp.getEyePosition();
        Vec3 end   = start.add(sp.getLookAngle().scale(64.0));
        AABB sweep = sp.getBoundingBox().expandTowards(sp.getLookAngle().scale(64.0)).inflate(1.0);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp.level(), sp, start, end, sweep,
                x -> x instanceof LivingEntity le && le.isAlive() && x != sp);

        if (hit == null || !(hit.getEntity() instanceof LivingEntity le)) {
            ctx.getSource().sendFailure(Component.literal("Sin entidad en el punto de mira (64 bloques)."));
            return 0;
        }

        EntityStats st = ZenkaiStats.entityStats(le);
        String body = (st == null)
                ? "SIN STATS ZENKAI (fuera del pipeline)"
                : st.getBody() + " / " + st.getBodyMax()
                + " (" + String.format("%.2f", 100.0 * st.getBody() / Math.max(1, st.getBodyMax())) + "%)"
                + " | PL " + st.getPowerLevel()
                + " | STR " + Math.round(st.computeMeleeFinal())
                + " | DEF " + Math.round(st.computeDefenseFinal())
                + " | PL " + st.getPowerLevel()
                + " (ap " + st.getApparentPowerLevel() + ")";

        ctx.getSource().sendSuccess(() -> Component.literal(
                le.getName().getString()
                        + "\n  vida  : " + String.format("%.2f", le.getHealth())
                        + " / " + String.format("%.2f", le.getMaxHealth())
                        + " (" + String.format("%.2f", 100.0 * le.getHealth() / Math.max(1f, le.getMaxHealth())) + "%)"
                        + "\n  body  : " + body
        ).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    /** Atajo de "/zenkai debug connector set" (ver el comentario de arriba): raytracea el
     *  bloque en el punto de mira y, si es un piece_connector, le cambia el socket
     *  directamente, sin pasar por PieceConnectorScreen. */
    private static int debugSetConnectorSocket(CommandContext<CommandSourceStack> ctx, ResourceLocation socket)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        HitResult hit = sp.pick(sp.blockInteractionRange() + 1.0, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            ctx.getSource().sendFailure(Component.literal(
                    "[Zenkai] Sin piece_connector en el punto de mira."));
            return 0;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!(sp.level().getBlockEntity(pos) instanceof PieceConnectorBlockEntity be)) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Eso no es un piece_connector."));
            return 0;
        }
        be.setSocket(socket);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Socket de " + pos.toShortString() + " -> " + socket), true);
        return 1;
    }

    /** Distancia fija a la que aparece "/zenkai debug kivfx spawn", delante de la mirada de
     *  quien ejecuta. Suficiente para ver el proyectil entero (incluida la envolvente y la
     *  estela) sin quedar dentro de su propio cuerpo con las técnicas más grandes a tamaño
     *  alto. */
    private static final double DEBUG_KIVFX_DISTANCE = 6.0;

    /**
     * "/zenkai debug kivfx spawn <type> [size]": aparece una copia CONGELADA (ver
     * KiProjectileEntity.freeze) del tipo pedido, a DEBUG_KIVFX_DISTANCE bloques de donde mira
     * quien ejecuta, orientada en esa misma dirección. Color = el default_rgb real del
     * datapack (la misma identidad que vería un jugador, no un color de depuración aparte).
     * damage=0/effect=NONE a propósito: es puramente un objeto que mirar, nunca debe poder
     * hacer daño ni admitir HOMING/EXPLOSIVE/etc.
     */
    // ── Muñeco de pruebas ────────────────────────────────────────────────────

    /** Bloques delante de quien ejecuta a los que aparece el muñeco. */
    private static final double DUMMY_SPAWN_DISTANCE = 10.0;
    private static final double DUMMY_SEARCH_RADIUS = 128.0;
    private static final int DUMMY_DEFAULT_SIZE = 3;
    /** 0 = sin intervalo propio: dispara con el cooldown real de la técnica. */
    private static final int DUMMY_DEFAULT_INTERVAL = 0;

    /**
     * Rama "spawn" (crea uno) o "set" (cambia el guion de los que ya hay). Misma gramática en los
     * dos: {@code <modo>} para idle/block/melee/physical/follow/flee, y
     * {@code spam_ki <tipo> [tamaño] [intervalo_ticks]}.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> dummyModes(
            boolean spawn) {
        var root = Commands.literal(spawn ? "spawn" : "set");
        for (com.hmc.zenkai.content.entity.misc.ShadowDebugMode m
                : com.hmc.zenkai.content.entity.misc.ShadowDebugMode.values()) {
            if (m == com.hmc.zenkai.content.entity.misc.ShadowDebugMode.SPAM_KI) continue;
            root.then(Commands.literal(m.id()).executes(ctx -> debugDummy(ctx, spawn, m, null,
                    DUMMY_DEFAULT_SIZE, DUMMY_DEFAULT_INTERVAL)));
        }
        root.then(Commands.literal("spam_ki")
                .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(KI_TYPE_IDS)
                        .executes(ctx -> debugDummy(ctx, spawn,
                                com.hmc.zenkai.content.entity.misc.ShadowDebugMode.SPAM_KI,
                                StringArgumentType.getString(ctx, "type"),
                                DUMMY_DEFAULT_SIZE, DUMMY_DEFAULT_INTERVAL))
                        .then(Commands.argument("size", IntegerArgumentType.integer(1, 10))
                                .executes(ctx -> debugDummy(ctx, spawn,
                                        com.hmc.zenkai.content.entity.misc.ShadowDebugMode.SPAM_KI,
                                        StringArgumentType.getString(ctx, "type"),
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        DUMMY_DEFAULT_INTERVAL))
                                .then(Commands.argument("interval", IntegerArgumentType.integer(1, 400))
                                        .executes(ctx -> debugDummy(ctx, spawn,
                                                com.hmc.zenkai.content.entity.misc.ShadowDebugMode.SPAM_KI,
                                                StringArgumentType.getString(ctx, "type"),
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "interval")))))));
        return root;
    }

    private static int debugDummy(CommandContext<CommandSourceStack> ctx, boolean spawn,
                                  com.hmc.zenkai.content.entity.misc.ShadowDebugMode mode,
                                  String typeId, int size, int interval)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;

        KiTechniqueType type = null;
        if (mode == com.hmc.zenkai.content.entity.misc.ShadowDebugMode.SPAM_KI) {
            type = KiTechniqueType.byName(typeId);
            if (type == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "[Zenkai] Tipo de técnica desconocido: " + typeId));
                return 0;
            }
        }

        if (spawn) {
            var shadow = ModEntities.SHADOW_CLONE.get().create(sl);
            if (shadow == null) return 0;
            // Delante de quien ejecuta, mirándole. +1 en Y: si cae sobre una cuesta, por gravedad
            // llega al suelo; sin el margen podía nacer dentro de un bloque.
            Vec3 look = sp.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
            Vec3 pos = sp.position().add(flat.scale(DUMMY_SPAWN_DISTANCE)).add(0, 1.0, 0);
            shadow.moveTo(pos.x, pos.y, pos.z, sp.getYRot() + 180f, 0f);
            shadow.setOwner(sp);
            shadow.setFlightAllowed(false);
            shadow.setDebugScript(mode, type, size, interval);
            sl.addFreshEntity(shadow);
            ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Muñeco creado: "
                    + mode.id() + ". \"/zenkai debug dummy set <modo>\" lo cambia, "
                    + "\"/zenkai debug dummy clear\" los borra."), false);
            return 1;
        }

        var dummies = sl.getEntitiesOfClass(
                com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.class,
                sp.getBoundingBox().inflate(DUMMY_SEARCH_RADIUS),
                s -> s.getTags().contains(
                        com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.DEBUG_DUMMY_TAG));
        for (var d : dummies) d.setDebugScript(mode, type, size, interval);
        final int n = dummies.size();
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] " + n
                + " muñeco(s) ahora en modo " + mode.id()), false);
        return n;
    }

    /** Separación de los dos muñecos de «dummy clash» a cada lado de quien lo ejecuta, y cuánto
     *  por delante: a ~5,4 bloques de cada uno, dentro del clash.join_range por defecto (6). */
    private static final double CLASH_DUMMY_SIDE = 4.5, CLASH_DUMMY_AHEAD = 3.0;

    /**
     * «/zenkai debug dummy clash [tipo]»: dos muñecos nuevos, uno a cada lado, se disparan un haz
     * el uno al otro y chocan. Es la única forma de tener en un mundo de un jugador un choque en el
     * que TÚ no estás, para probar «Clic derecho para ayudar» (ClashJoinClient). Colores distintos
     * para distinguir los lados en la esfera. Se borran con «dummy clear».
     */
    private static int debugDummyClash(CommandContext<CommandSourceStack> ctx, String typeId)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        KiTechniqueType type = KiTechniqueType.byName(typeId);
        if (type == null || !type.travels() || type.defensive() || type == KiTechniqueType.DISK) {
            ctx.getSource().sendFailure(Component.literal(
                    "[Zenkai] Tipo no válido para un choque (tiene que viajar y no ser DISK): " + typeId));
            return 0;
        }
        Vec3 look = sp.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0.0, look.z);
        fwd = fwd.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : fwd.normalize();
        Vec3 side = new Vec3(-fwd.z, 0.0, fwd.x);
        Vec3 base = sp.position().add(fwd.scale(CLASH_DUMMY_AHEAD)).add(0, 1.0, 0);
        Vec3 pa = base.add(side.scale(CLASH_DUMMY_SIDE)), pb = base.subtract(side.scale(CLASH_DUMMY_SIDE));

        var a = ModEntities.SHADOW_CLONE.get().create(sl);
        var b = ModEntities.SHADOW_CLONE.get().create(sl);
        if (a == null || b == null) return 0;
        float yawA = (float) Math.toDegrees(Math.atan2(-(pb.x - pa.x), pb.z - pa.z));
        a.moveTo(pa.x, pa.y, pa.z, yawA, 0f);
        b.moveTo(pb.x, pb.y, pb.z, yawA + 180f, 0f);
        for (var d : new com.hmc.zenkai.content.entity.misc.ShadowCloneEntity[]{a, b}) {
            d.setOwner(sp);
            d.setFlightAllowed(false);
            d.setDebugScript(com.hmc.zenkai.content.entity.misc.ShadowDebugMode.IDLE, null,
                    DUMMY_DEFAULT_SIZE, DUMMY_DEFAULT_INTERVAL);
            sl.addFreshEntity(d);
        }
        // Daño de sobra por encima de clash.min_damage: con menos, los haces se atravesarían.
        double dmg = Math.max(10.0, com.hmc.zenkai.config.ServerConfig.clashMinDamage() + 5.0);
        fireDebugBeam(sl, a, b, type, 0x3FA9FF, dmg);
        fireDebugBeam(sl, b, a, type, 0xFF5533, dmg);
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Choque entre muñecos ("
                + type.name().toLowerCase(java.util.Locale.ROOT) + "). Apunta a uno y haz clic derecho "
                + "para ayudarle; \"/zenkai debug dummy join ally|rival\" mete más muñecos."), false);
        return 1;
    }

    private static void fireDebugBeam(ServerLevel sl, net.minecraft.world.entity.LivingEntity from,
                                      net.minecraft.world.entity.LivingEntity to, KiTechniqueType type,
                                      int rgb, double damage) {
        Vec3 eye = from.getEyePosition();
        Vec3 dir = to.getEyePosition().subtract(eye).normalize();
        var proj = new com.hmc.zenkai.content.entity.technique.KiProjectileEntity(
                ModEntities.KI_PROJECTILE.get(), sl);
        proj.configure(from, type, rgb, DUMMY_DEFAULT_SIZE, damage, 100, TechniqueEffect.NONE);
        Vec3 spawn = eye.add(dir.scale(0.9));
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(type.speed()));
        sl.addFreshEntity(proj);
    }

    /** «/zenkai debug dummy join ally|rival»: el muñeco libre más cercano se une al choque de
     *  haces en el que estás, en tu equipo o en el contrario (ClashManager.debugJoinMob). */
    private static int debugDummyJoin(CommandContext<CommandSourceStack> ctx, boolean ally)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        var dummies = sl.getEntitiesOfClass(
                com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.class,
                sp.getBoundingBox().inflate(DUMMY_SEARCH_RADIUS),
                s -> s.getTags().contains(
                        com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.DEBUG_DUMMY_TAG)
                        && !com.hmc.zenkai.feature.technique.clash.ClashManager.inClash(s.getUUID()));
        if (dummies.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "[Zenkai] No hay ningún muñeco libre cerca (crea otro con /zenkai debug dummy)"));
            return 0;
        }
        dummies.sort(java.util.Comparator.comparingDouble(d -> d.distanceToSqr(sp)));
        String why = com.hmc.zenkai.feature.technique.clash.ClashManager.debugJoinMob(sp, dummies.get(0), ally);
        if (why != null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] No se pudo: " + switch (why) {
                case "no_clash" -> "no estás en un choque de haces (o ya se resolvió)";
                case "full" -> "ese equipo está lleno (clash.team_max_size)";
                default -> "ese muñeco ya está en un choque";
            }));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Muñeco unido al choque ("
                + (ally ? "tu equipo" : "equipo rival") + ")"), false);
        return 1;
    }

    /** «/zenkai debug dummy grab»: un muñeco nuevo, quieto delante de quien ejecuta, lo agarra al instante. */
    private static int debugDummyGrab(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        var shadow = ModEntities.SHADOW_CLONE.get().create(sl);
        if (shadow == null) return 0;
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 pos = sp.position().add(flat.scale(2.6)).add(0, 0.2, 0);
        shadow.moveTo(pos.x, pos.y, pos.z, sp.getYRot() + 180f, 0f);
        shadow.setOwner(sp);
        shadow.setFlightAllowed(false);
        shadow.setDebugScript(com.hmc.zenkai.content.entity.misc.ShadowDebugMode.IDLE, null,
                DUMMY_DEFAULT_SIZE, DUMMY_DEFAULT_INTERVAL);
        sl.addFreshEntity(shadow);
        com.hmc.zenkai.feature.technique.PhysicalCombatServer.startDebugGrab(shadow, sp);
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Un muñeco te ha agarrado: acierta la barra "
                + "(ESPACIO), usa Evasiva o Instant Transmission. \"/zenkai debug dummy clear\" lo borra."), false);
        return 1;
    }

    private static int debugStun(CommandContext<CommandSourceStack> ctx, int ticks)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        int self = com.hmc.zenkai.feature.combat.StunState.apply(sp, ticks);
        int mobs = 0;
        for (var d : sl.getEntitiesOfClass(
                com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.class,
                sp.getBoundingBox().inflate(DUMMY_SEARCH_RADIUS))) {
            if (com.hmc.zenkai.feature.combat.StunState.apply(d, ticks) > 0) mobs++;
        }
        final int s = self;
        final int m = mobs;
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Stun: tú " + s + " ticks, " + m + " muñecos"), false);
        return 1;
    }

    private static int debugClearDummies(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;
        var dummies = sl.getEntitiesOfClass(
                com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.class,
                sp.getBoundingBox().inflate(DUMMY_SEARCH_RADIUS),
                s -> s.getTags().contains(
                        com.hmc.zenkai.content.entity.misc.ShadowCloneEntity.DEBUG_DUMMY_TAG));
        for (var d : dummies) d.discard();
        final int n = dummies.size();
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] " + n
                + " muñeco(s) borrado(s)"), false);
        return n;
    }

    private static int debugSpawnKiVfx(CommandContext<CommandSourceStack> ctx, String typeId, int size)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        KiTechniqueType type = KiTechniqueType.byName(typeId);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "[Zenkai] Tipo de técnica desconocido: " + typeId));
            return 0;
        }
        if (!(sp.level() instanceof ServerLevel sl)) return 0;

        Vec3 look = sp.getLookAngle();
        Vec3 spawnPos = sp.getEyePosition().add(look.scale(DEBUG_KIVFX_DISTANCE));

        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sl);
        proj.configure(sp, type, type.defaultRgb(), size, 0.0, Integer.MAX_VALUE, TechniqueEffect.NONE);
        proj.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        proj.freeze(look);
        sl.addFreshEntity(proj);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + type.id() + " (tamaño " + size + ") congelado a "
                        + (int) DEBUG_KIVFX_DISTANCE + " bloques. \"/zenkai debug kivfx clear\" para quitarlos."),
                false);
        return 1;
    }

    /** Separación EXTRA entre técnicas contiguas en "kivfx spawnall", además del ancho real de
     *  cada una — sin esto, dos técnicas grandes casi se tocan por los bordes y estorban para
     *  distinguir dónde termina una y empieza la otra. */
    private static final double DEBUG_KIVFX_ROW_GAP = 2.0;

    /**
     * "/zenkai debug kivfx spawnall [size]": las 16 técnicas de golpe, congeladas en fila
     * horizontal delante de quien ejecuta, todas al mismo tamaño — la comparativa completa que
     * "spawn" uno a uno solo da tras 16 comandos. La fila se centra en la mirada (no arranca
     * pegada a un lado) y cada hueco usa {@link KiTechniqueType#visualDiameter(int)} — el
     * diámetro REAL en pantalla, no {@code projectileSize()} a secas — así que un Death Ball
     * enorme no atropella a su vecino aunque un Death Beam finísimo sí quede muy junto al suyo.
     * PRIMER INTENTO usaba {@code projectileSize()} directo y las técnicas se solapaban un 50%
     * (confirmado con capturas): el renderer agranda la malla un 50% más allá del hitbox
     * (ver KiVfxProjectileRenderer.render()) y la fila no lo tenía en cuenta.
     * La mirada se APLANA (se ignora el pitch): con la fila orientada a una mirada inclinada
     * hacia arriba/abajo saldría en diagonal en vez de una línea horizontal legible.
     */
    private static int debugSpawnAllKiVfx(CommandContext<CommandSourceStack> ctx, int size)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;

        Vec3 look = sp.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        flatLook = flatLook.lengthSqr() > 1.0e-4 ? flatLook.normalize() : new Vec3(0, 0, 1);
        Vec3 right = new Vec3(-flatLook.z, 0.0, flatLook.x);
        Vec3 rowCenter = sp.getEyePosition().add(flatLook.scale(DEBUG_KIVFX_DISTANCE));

        KiTechniqueType[] all = KiTechniqueType.values();
        double[] slot = new double[all.length];
        double total = 0.0;
        for (int i = 0; i < all.length; i++) {
            slot[i] = Math.max(0.5, all[i].visualDiameter(size)) + DEBUG_KIVFX_ROW_GAP;
            total += slot[i];
        }

        double x = -total / 2.0;
        for (KiTechniqueType type : all) {
            double half = Math.max(0.5, type.visualDiameter(size)) / 2.0 + DEBUG_KIVFX_ROW_GAP / 2.0;
            x += half;
            Vec3 pos = rowCenter.add(right.scale(x));

            KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sl);
            proj.configure(sp, type, type.defaultRgb(), size, 0.0, Integer.MAX_VALUE, TechniqueEffect.NONE);
            proj.setPos(pos.x, pos.y, pos.z);
            proj.freeze(flatLook);
            sl.addFreshEntity(proj);

            x += half;
        }

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + all.length + " técnicas congeladas en fila (tamaño " + size
                        + "). \"/zenkai debug kivfx clear\" para quitarlas."), false);
        return all.length;
    }

    /** "/zenkai debug kivfx clear": borra solo las entidades marcadas por freeze() (ver
     *  KiProjectileEntity.DEBUG_TAG), nunca un proyectil de combate real aunque comparta
     *  clase. Radio generoso (500 bloques) porque es una sesión de comparación manual, no algo
     *  que se dispare cerca de otros jugadores por accidente. */
    private static int debugClearKiVfx(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        AABB area = sp.getBoundingBox().inflate(500.0);
        List<KiProjectileEntity> found = sp.level().getEntitiesOfClass(KiProjectileEntity.class, area,
                e -> e.getTags().contains(KiProjectileEntity.DEBUG_TAG));
        for (KiProjectileEntity e : found) e.discard();

        int n = found.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + n + " ataque(s) congelado(s) eliminado(s)."), false);
        return n;
    }

    /** "/zenkai debug sequence": caso SINTÉTICO del andamiaje TechniqueSequence, sin ninguna
     *  técnica real de por medio — ver el comentario de la rama "sequence" más arriba y el
     *  javadoc de {@link com.hmc.zenkai.feature.technique.sequence.TechniqueSequence}.
     *  2 destellos (heavy_blow, luego kiai) con 1s de diferencia, delante de quien ejecuta,
     *  reutilizando los MISMOS JSON de {@code zenkai_vfx_effects/physical_impact/} que ya
     *  migró el paso 1 — no hace falta un efecto nuevo solo para esta prueba. */
    private static int debugRunSequence(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        if (!(sp.level() instanceof ServerLevel sl)) return 0;

        Vec3 spawnPos = sp.getEyePosition().add(sp.getLookAngle().scale(DEBUG_KIVFX_DISTANCE));
        int rgb = com.hmc.zenkai.feature.aura.AuraColors.resolve(sp);
        SequenceContext seqCtx = new SequenceContext(sl, spawnPos, rgb, sp);

        TechniqueSequence sequence = new TechniqueSequence(List.of(
                new VfxStep(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                        "physical_impact/heavy_blow"), 0.4),
                new PauseStep(20),
                new VfxStep(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,
                        "physical_impact/kiai"), 0.6)
        ));
        sequence.run(seqCtx);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Secuencia sintética lanzada: 2 destellos con 1s de diferencia."), false);
        return 1;
    }

    private static int respec(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int before = att.getTP();
        att.respec();
        int refunded = att.getTP() - before;
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.zenkai.respec.done", refunded, sp.getName()), true);
        return 1;
    }
}