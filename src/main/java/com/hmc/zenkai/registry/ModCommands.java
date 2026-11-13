package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.effect.MajinEffect;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.sequence.TechniqueSequence;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.hmc.zenkai.registry.commands.DebugCommands;
import com.hmc.zenkai.registry.commands.PlayerCommands;
import com.hmc.zenkai.registry.commands.SkillCommands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ModCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        var root = e.getDispatcher();
        // Marcas faciales del propio jugador: personalización, sin permiso de operador.
        root.register(com.hmc.zenkai.registry.commands.FaceCommands.build());

        // ── /zenkai tp ───────────────────────────────────────────────────────
        // Añade TP de entrenamiento al jugador.
        // Uso: /zenkai tp add [objetivos] <cantidad>
        root.register(Commands.literal("zenkai")
                .requires(cs -> cs.hasPermission(2))

                .then(Commands.literal("tp")
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> PlayerCommands.addTp(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> PlayerCommands.addTp(c, sp,
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
                                                .executes(ctx -> PlayerCommands.setAttr(ctx,
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "attr"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("attr", StringArgumentType.word()).suggests(ATTR_IDS)
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> forEach(ctx, targets(ctx),
                                                                (c, sp) -> PlayerCommands.setAttr(c, sp,
                                                                        StringArgumentType.getString(c, "attr"),
                                                                        IntegerArgumentType.getInteger(c, "value"))))))))
                        .then(Commands.literal("setall")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> PlayerCommands.setAllAttr(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "value"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> PlayerCommands.setAllAttr(c, sp,
                                                                IntegerArgumentType.getInteger(c, "value")))))))
                        .then(Commands.literal("maxall")
                                .executes(ctx -> PlayerCommands.maxAllAttr(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::maxAllAttr)))))

                .then(Commands.literal("respec")
                        .executes(ctx -> PlayerCommands.respec(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::respec))))

                // ── /zenkai race ──────────────────────────────────────────────────
                // Fuerza la raza de un jugador desde el servidor.
                // Uso: /zenkai race set [objetivos] <raza>
                .then(Commands.literal("race")
                        .then(Commands.literal("set")
                                .then(Commands.argument("race", StringArgumentType.string())
                                        .executes(ctx -> PlayerCommands.setRace(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "race"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("race", StringArgumentType.string())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> PlayerCommands.setRace(c, sp,
                                                                StringArgumentType.getString(c, "race"))))))))

                // ── /zenkai style ─────────────────────────────────────────────────
                // Fuerza el estilo de combate de un jugador desde el servidor.
                // Uso: /zenkai style set [objetivos] <estilo>
                .then(Commands.literal("style")
                        .then(Commands.literal("set")
                                .then(Commands.argument("style", StringArgumentType.string())
                                        .executes(ctx -> PlayerCommands.setStyle(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "style"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("style", StringArgumentType.string())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> PlayerCommands.setStyle(c, sp,
                                                                StringArgumentType.getString(c, "style"))))))))

                // ── /zenkai reset ─────────────────────────────────────────────────
                // Resetea el progreso del jugador.
                // /zenkai reset stats [objetivos] → devuelve los TP invertidos (respec)
                // /zenkai reset full  [objetivos] → borra raza, estilo, stats, TP y apariencia
                // /zenkai reset summon [objetivos] → quita el cooldown de invocar al dragón
                // /zenkai reset visit  [objetivos] → quita la espera entre visitas a la Tierra (Uranai Baba)
                .then(Commands.literal("reset")
                        .then(Commands.literal("stats")
                                .executes(ctx -> PlayerCommands.resetStats(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::resetStats))))
                        .then(Commands.literal("full")
                                .executes(ctx -> PlayerCommands.resetFull(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::resetFull))))
                        .then(Commands.literal("training_fatigue")
                                .executes(ctx -> PlayerCommands.resetTrainingFatigue(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::resetTrainingFatigue))))
                        .then(Commands.literal("summon")
                                .executes(ctx -> PlayerCommands.resetSummon(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::resetSummon))))
                        .then(Commands.literal("visit")
                                .executes(ctx -> PlayerCommands.resetVisit(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::resetVisit)))))

                .then(Commands.literal("revive")
                        .then(Commands.argument("player", EntityArgument.players())
                                .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::revivePlayer))))

                // ── /zenkai pets ──────────────────────────────────────────────────
                // Borra el historial de mascotas muertas (deseo de revivir).
                // Uso: /zenkai pets clear [objetivos]
                .then(Commands.literal("pets")
                        .then(Commands.literal("clear")
                                .executes(ctx -> PlayerCommands.clearPets(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::clearPets)))))

                // ── /zenkai rage chronicle clear [objetivos] ──────────────────────
                // Vacía la crónica de Rage Forms (solo pruebas; la crónica normal es historia y no se borra).
                .then(Commands.literal("rage")
                        .then(Commands.literal("chronicle")
                                .then(Commands.literal("clear")
                                        .executes(ctx -> PlayerCommands.clearRageChronicle(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::clearRageChronicle)))))
                        // /zenkai rage scene reset [objetivos]: olvida qué formas ya se vieron y vuelve a armar la secuencia larga (pruebas).
                        .then(Commands.literal("scene")
                                .then(Commands.literal("reset")
                                        .executes(ctx -> PlayerCommands.resetRageScene(ctx, ctx.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx), PlayerCommands::resetRageScene))))))

                .then(Commands.literal("struct")
                        // /zenkai struct status: ¿están marcadas como colocadas las estructuras fijas (palacio de Yemma,
                        // Kaiosama, Habitación del Tiempo)? La marca vive en los datos de cada dimensión.
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    var lines = com.hmc.zenkai.worldgen.ZenkaiStructurePlacement.status(ctx.getSource().getServer());
                                    for (var l : lines) ctx.getSource().sendSuccess(() -> l, false);
                                    return lines.size();
                                }))
                        // /zenkai struct restore <otherworld|kaiosama|htc|all> [force]: sin «force» solo coloca lo que NO esté
                        // marcado; con «force» recoloca la estructura ENTERA, pisando lo que haya (úsalo en mundos que perdieron
                        // la dimensión antes del arreglo, o para revertir una reconstrucción).
                        .then(Commands.literal("restore")
                                .then(Commands.argument("which", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("all", "otherworld", "kaiosama", "htc"), b))
                                        .executes(ctx -> {
                                            var lines = com.hmc.zenkai.worldgen.ZenkaiStructurePlacement.restore(ctx.getSource().getServer(),
                                                    StringArgumentType.getString(ctx, "which"), false);
                                            for (var l : lines) ctx.getSource().sendSuccess(() -> l, true);
                                            return lines.size();
                                        })
                                        .then(Commands.literal("force")
                                                .executes(ctx -> {
                                                    var lines = com.hmc.zenkai.worldgen.ZenkaiStructurePlacement.restore(ctx.getSource().getServer(),
                                                            StringArgumentType.getString(ctx, "which"), true);
                                                    for (var l : lines) ctx.getSource().sendSuccess(() -> l, true);
                                                    return lines.size();
                                                }))))
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
                                                        (c, sp) -> SkillCommands.skillGive(c, sp,
                                                                StringArgumentType.getString(c, "id"), -1)))
                                                // Con nivel: fija ese nivel exacto, recortado al techo.
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> forEach(ctx, targets(ctx),
                                                                (c, sp) -> SkillCommands.skillGive(c, sp,
                                                                        StringArgumentType.getString(c, "id"),
                                                                        IntegerArgumentType.getInteger(c, "level"))))))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .suggests(SKILL_IDS)
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> SkillCommands.skillRevoke(c, sp,
                                                                StringArgumentType.getString(c, "id")))))))
                        .then(Commands.literal("giveall")
                                .executes(ctx -> SkillCommands.skillGiveAll(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), SkillCommands::skillGiveAll))))
                        // list NO admite @a a propósito: es una consulta y con varios objetivos
                        // escupiría (nº skills × nº jugadores) líneas de chat, ilegible.
                        .then(Commands.literal("list")
                                .executes(ctx -> SkillCommands.skillList(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> SkillCommands.skillList(ctx, EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("phys")
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("tech", StringArgumentType.word())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> SkillCommands.physGive(c, sp,
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
                                                                (c, sp) -> SkillCommands.masterySet(c, sp,
                                                                        ResourceLocationArgument.getId(c, "id"),
                                                                        IntegerArgumentType.getInteger(c, "value"))))))))
                        .then(Commands.literal("list")
                                .executes(ctx -> SkillCommands.masteryList(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> SkillCommands.masteryList(ctx, EntityArgument.getPlayer(ctx, "player"))))))

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
                                        .executes(ctx -> PlayerCommands.alignmentSet(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(ctx, "value"))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> PlayerCommands.alignmentSet(c, sp,
                                                                IntegerArgumentType.getInteger(c, "value"))))))))

                // ── /zenkai debug entity ─────────────────────────────────────────
                // Vuelca vida vanilla + pool zenkai de la entidad en el punto de mira.
                //
                // ── /zenkai body killall [spill] ──────────────────────────────
                // Elimina todos los cuerpos de jugador (solo cuerpos). Con "spill" sueltan sus objetos al suelo.
                .then(Commands.literal("body")
                        .then(Commands.literal("killall")
                                .executes(c -> DebugCommands.bodyKillAll(c, false))
                                .then(Commands.literal("spill").executes(c -> DebugCommands.bodyKillAll(c, true)))))
                .then(Commands.literal("debug")
                        .then(Commands.literal("entity")
                                .executes(DebugCommands::debugEntity))
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
                        // se eliminó (junto con su reset en PlayerCommands.resetFull()) para no volver a
                        // confundir los dos.
                        .then(Commands.literal("divine")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> DebugCommands.debugSetSpecialState(ctx,
                                                ctx.getSource().getPlayerOrException(), "isDivine",
                                                PlayerStatsAttachment::setDivine,
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> DebugCommands.debugSetSpecialState(c, sp, "isDivine",
                                                                PlayerStatsAttachment::setDivine,
                                                                BoolArgumentType.getBool(c, "value")))))))
                        .then(Commands.literal("legendary")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> DebugCommands.debugSetSpecialState(ctx,
                                                ctx.getSource().getPlayerOrException(), "isLegendary",
                                                PlayerStatsAttachment::setLegendary,
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> DebugCommands.debugSetSpecialState(c, sp, "isLegendary",
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
                                        .executes(ctx -> DebugCommands.debugSetSpecialState(ctx,
                                                ctx.getSource().getPlayerOrException(), "isImmortal",
                                                PlayerStatsAttachment::setImmortal,
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> DebugCommands.debugSetSpecialState(c, sp, "isImmortal",
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
                                        .executes(ctx -> DebugCommands.debugSetMajinControlled(ctx,
                                                ctx.getSource().getPlayerOrException(),
                                                BoolArgumentType.getBool(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.players())
                                                .executes(ctx -> forEach(ctx, targets(ctx),
                                                        (c, sp) -> DebugCommands.debugSetMajinControlled(c, sp,
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
                                                .executes(ctx -> DebugCommands.debugSetConnectorSocket(ctx,
                                                        ResourceLocationArgument.getId(ctx, "socket"))))))
                        // ── /zenkai debug stun [ticks] ────────────────────────────────
                        // Aturde a quien ejecuta y a los muñecos cercanos (StunState.apply, con
                        // sus frenos: tope, rendimientos decrecientes e inmunidad).
                        .then(Commands.literal("stun")
                                .executes(c -> DebugCommands.debugStun(c, 60))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 600))
                                        .executes(c -> DebugCommands.debugStun(c, IntegerArgumentType.getInteger(c, "ticks")))))
                        // ── /zenkai debug party [n] | clear ───────────────────────────
                        // Llena tu party con compañeros FALSOS (vitales inventados) para probar el panel
                        // de party en un mundo de un solo jugador.
                        .then(Commands.literal("party")
                                .executes(c -> DebugCommands.debugParty(c, 4))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 8))
                                        .executes(c -> DebugCommands.debugParty(c, IntegerArgumentType.getInteger(c, "n"))))
                                .then(Commands.literal("clear")
                                        .executes(DebugCommands::debugPartyClear)))
                        // ── /zenkai debug body spawn|fill|clear ───────────────────────
                        // Cuerpos de jugador (muerte permanente, fase 2-3). spawn: uno vacío a tus pies.
                        // fill: uno con una copia de tu inventario (no te lo quita). clear: borra los tuyos.
                        .then(Commands.literal("body")
                                .then(Commands.literal("spawn").executes(c -> DebugCommands.debugBody(c, false)))
                                .then(Commands.literal("fill").executes(c -> DebugCommands.debugBody(c, true)))
                                .then(Commands.literal("clear").executes(DebugCommands::debugBodyClear)))
                        // ── /zenkai debug visit <ticks> | end ─────────────────────────
                        // Visita a la Tierra (fase 5): empieza una con esos ticks aunque no estés muerto (te marca como
                        // muerto) o la termina ya. Sin esperar los 7 días de la config.
                        .then(Commands.literal("visit")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes(c -> DebugCommands.debugVisit(c, IntegerArgumentType.getInteger(c, "ticks"))))
                                .then(Commands.literal("end").executes(c -> DebugCommands.debugVisit(c, 0))))
                        // ── /zenkai debug dummy ───────────────────────────────────────
                        // Muñeco de pruebas: una Sombra con guion que hace UNA sola cosa
                        // (spamear ki, quedarse quieta, guardia...), para probar el choque de
                        // haces y demás sin esperar a la IA. Ver ShadowDebugMode.
                        .then(Commands.literal("dummy")
                                .then(DebugCommands.dummyModes(true))
                                .then(DebugCommands.dummyModes(false))
                                .then(Commands.literal("clear")
                                        .executes(DebugCommands::debugClearDummies))
                                .then(Commands.literal("clash")
                                        .executes(c -> DebugCommands.debugDummyClash(c, "wave"))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(KI_TYPE_IDS)
                                                .executes(c -> DebugCommands.debugDummyClash(c,
                                                        StringArgumentType.getString(c, "type")))))
                                // ── /zenkai debug dummy grab ──────────────────────────
                                // Un muñeco te AGARRA para probar el skill check de escape (clic/espacio en la
                                // zona dorada, Evasiva o Instant Transmission). Sin límite de tiempo, como el real.
                                .then(Commands.literal("grab")
                                        .executes(DebugCommands::debugDummyGrab))
                                // Ritual de God Ki con muñecos de participantes (flujo real, cargando ki).
                                .then(Commands.literal("ritual")
                                        .executes(com.hmc.zenkai.feature.skilltree.SkillTreeDebug::ritualDummies))
                                .then(Commands.literal("join")
                                        .then(Commands.literal("ally")
                                                .executes(c -> DebugCommands.debugDummyJoin(c, true)))
                                        .then(Commands.literal("rival")
                                                .executes(c -> DebugCommands.debugDummyJoin(c, false))))))



        );
    }





    // ── Selectores múltiples ─────────────────────────────────────────────────
    // Cada uno de los <player> usa EntityArgument.players(), así que aceptan @a, @p, @r, @s,
    // @e[type=player] o un nombre suelto. La acción corre UNA VEZ por objetivo y cada
    // implementación se queda tal cual (firma de un solo ServerPlayer).

    @FunctionalInterface
    public interface PlayerAction {
        int run(CommandContext<CommandSourceStack> ctx, ServerPlayer sp);
    }

    /** Objetivos del argumento "player". Lanza si el selector no encuentra a nadie (vanilla). */
    public static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayers(ctx, "player");
    }

    /**
     * Ejecuta la acción sobre cada objetivo y devuelve CUÁNTOS tuvieron éxito: es el valor
     * que lee /execute store result, así que sirve para encadenar.
     * Con más de un objetivo añade una línea de resumen (las implementaciones ya emiten
     * su propio mensaje por jugador).
     */
    public static int forEach(CommandContext<CommandSourceStack> ctx,
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





















    /** Atributos para /zenkai attr set: antes el admin tenía que adivinar el nombre (y "STR",
     *  que sugería el propio mensaje de error, no funcionaba). */
    public static final SuggestionProvider<CommandSourceStack> ATTR_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) ids.add(a.name().toLowerCase(java.util.Locale.ROOT));
        return SharedSuggestionProvider.suggest(ids, b);
    };

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
    public static final SuggestionProvider<CommandSourceStack> MASTERY_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (FormDef d : FormDef.all()) ids.add(d.id().toString());
        ids.add(PlayerFormAttachment.kaiokenMasteryKey().toString());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** Ids del datapack de habilidades. Sin namespace: el id de una SkillDef es la ruta del
     *  archivo, no un ResourceLocation. */
    public static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (SkillDef d : SkillDef.all()) ids.add(d.id());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** Los 16 valores de KiTechniqueType, en minúscula (== id() de cada uno). Para
     *  "/zenkai debug kivfx spawn". */
    public static final SuggestionProvider<CommandSourceStack> KI_TYPE_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (KiTechniqueType t : KiTechniqueType.values()) ids.add(t.id());
        return SharedSuggestionProvider.suggest(ids, b);
    };
}
