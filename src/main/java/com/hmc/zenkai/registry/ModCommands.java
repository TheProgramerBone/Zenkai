package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.CommonConfig;
import com.hmc.zenkai.config.ServerConfig;
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
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
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
                                .then(Commands.argument("attr", StringArgumentType.string())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(ctx -> setAttr(ctx,
                                                        ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "attr"),
                                                        IntegerArgumentType.getInteger(ctx, "value")))))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .then(Commands.argument("attr", StringArgumentType.string())
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
                .then(Commands.literal("reset")
                        .then(Commands.literal("stats")
                                .executes(ctx -> resetStats(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::resetStats))))
                        .then(Commands.literal("full")
                                .executes(ctx -> resetFull(ctx, ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.players())
                                        .executes(ctx -> forEach(ctx, targets(ctx), ModCommands::resetFull)))))

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


                // ── /zenkai debug entity ─────────────────────────────────────────
                // Vuelca vida vanilla + pool zenkai de la entidad en el punto de mira.
                //
                // ── /zenkai debug party add [nombre] ─────────────────────────────
                // TEMPORAL — solo para probar PartyScreen en singleplayer, donde no hay con
                // quién formar grupo de verdad. Añade un miembro con UUID aleatorio a tu party
                // (la crea si hace falta). Ver PartyService.debugAddFakeMember y el javadoc de
                // PartyDebug para qué borrar cuando el menú ya esté verificado.
                // ── /zenkai debug tail <true|false> ──────────────────────────────
                // TEMPORAL — para probar el ritual de Oozaru (ver OozaruSystem/
                // OozaruConditions) sin esperar al ítem real de cola de GeckoLibArmor, que
                // todavía no existe (ver PlayerStateFlags.hasTail, default true). Borrar esta
                // rama cuando ese ítem exista y de verdad pueda ponerse/quitarse en juego.
                .then(Commands.literal("debug")
                        .then(Commands.literal("entity")
                                .executes(ModCommands::debugEntity))
                        .then(Commands.literal("tail")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSetTail(ctx,
                                                BoolArgumentType.getBool(ctx, "value")))))
                        // ── /zenkai debug divine|legendary <true|false> ──────────────
                        // TEMPORAL — igual que "debug tail" de arriba: la vía REAL de conseguir
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
                                        .executes(ctx -> debugSetSpecialState(ctx, "isDivine",
                                                PlayerStatsAttachment::setDivine,
                                                BoolArgumentType.getBool(ctx, "value")))))
                        .then(Commands.literal("legendary")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSetSpecialState(ctx, "isLegendary",
                                                PlayerStatsAttachment::setLegendary,
                                                BoolArgumentType.getBool(ctx, "value")))))
                        // ── /zenkai debug majin <true|false> ─────────────────────────
                        // A diferencia de divine/legendary de arriba, "majin" SÍ tiene un
                        // mecanismo real ya implementado: PersistentEffectsSystem.tick()
                        // reaplica ModEffects.MAJIN cada tick mientras
                        // PlayerVisualAttachment.isMajinControlled() sea true (solo la muerte
                        // lo borra de verdad). Este comando solo ahorra tener que pasar por lo
                        // que sea que hoy active ese flag durante pruebas — no es un
                        // placeholder de algo que falte por construir, así que no hay nada
                        // "que borrar más adelante".
                        .then(Commands.literal("majin")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> debugSetMajinControlled(ctx,
                                                BoolArgumentType.getBool(ctx, "value")))))
                        .then(Commands.literal("party")
                                .then(Commands.literal("add")
                                        .executes(ctx -> debugPartyAdd(ctx, null))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> debugPartyAdd(ctx,
                                                        StringArgumentType.getString(ctx, "name")))))))



        );
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

    /** Ver el comentario de la rama "debug tail" más arriba. Actúa sobre quien ejecuta. */
    private static int debugSetTail(CommandContext<CommandSourceStack> ctx, boolean value) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setHasTail(value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] hasTail = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Ver el comentario de las ramas "debug divine/majin/legendary" más arriba. Actúa sobre
     *  quien ejecuta, igual que "debug tail". Un solo helper para los tres en vez de triplicar
     *  el mismo cuerpo — solo cambia la etiqueta y qué setter de PlayerStatsAttachment llamar. */
    private static int debugSetSpecialState(CommandContext<CommandSourceStack> ctx, String label,
                                            java.util.function.BiConsumer<PlayerStatsAttachment, Boolean> setter,
                                            boolean value) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
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
    private static int debugSetMajinControlled(CommandContext<CommandSourceStack> ctx, boolean value) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
        sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).setMajinControlled(value);
        PlayerLifeCycle.sync(sp);
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
        ZenkaiAttributes a = ZenkaiAttributes.fromString(attrName);
        if (a == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown attribute: " + attrName
                    + ". Valid: STR, CON, DEX, WIL, SPI, MND"));
            return 0;
        }
        att.setAttribute(a, value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] " + a + " = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int setAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int v = Math.max(0, Math.min(value, CommonConfig.globalAttributeCap()));
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) att.setAttribute(a, v);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] All attributes = " + v + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    private static int maxAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        return setAllAttr(ctx, sp, CommonConfig.globalAttributeCap());
    }

    private static int setRace(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String raceName) {
        Race r;
        try { r = Race.valueOf(raceName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown race: " + raceName));
            return 0;
        }
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setRace(r);
        PlayerLifeCycle.sync(sp);
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
        // aunque hoy solo /zenkai debug tail puede haberla puesto en false.
        att.setHasSsj4Ritual(false);
        att.setHasTail(true);
        // Servicio de Kaiosama: un reset "full" también devuelve el derecho a pedir las
        // pesas de nuevo, igual que borra todo lo demás — no sería full si dejara este
        // regalo de una sola vez "ya gastado" para una vida completamente nueva.
        att.setReceivedKaioWeights(false);
        att.setAlignment(0);
        att.setLastSummonTick(ServerConfig.summonCooldownTicks());
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

        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Full reset done → " + sp.getGameProfile().getName()), true);
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
            String extra = (def.master() != null ? " §8master:" + def.master() : "")
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
                    + " (dash_punch|heavy_blow|barrage|kiai)"));
            return 0;
        }
        PlayerStatsAttachment.get(sp).techniques().unlock(t);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + t.name() + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Autocompleta formas del datapack y escalones de kaioken por su etiqueta ("x20"). */
    private static final SuggestionProvider<CommandSourceStack> MASTERY_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (FormDef d : FormDef.all()) ids.add(d.id().toString());
        for (KaiokenTier t : KaiokenTier.values()) if (t.isOn()) ids.add(t.label());
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** Ids del datapack de habilidades. Sin namespace: el id de una SkillDef es la ruta del
     *  archivo, no un ResourceLocation. */
    private static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (SkillDef d : SkillDef.all()) ids.add(d.id());
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

    /** TEMPORAL — ver el comentario de "/zenkai debug party add" arriba y el javadoc de
     *  PartyService.debugAddFakeMember. {@code name} null → nombre autogenerado. */
    private static int debugPartyAdd(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        return PartyService.debugAddFakeMember(ctx.getSource().getServer(), sp, name) ? 1 : 0;
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