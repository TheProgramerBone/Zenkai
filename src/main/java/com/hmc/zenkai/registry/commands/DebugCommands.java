package com.hmc.zenkai.registry.commands;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.blockentity.PieceConnectorBlockEntity;
import com.hmc.zenkai.content.effect.MajinEffect;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.party.PartyService;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.technique.KiTechniqueType;
import com.hmc.zenkai.feature.technique.TechniqueEffect;
import com.hmc.zenkai.feature.technique.sequence.SequenceContext;
import com.hmc.zenkai.feature.technique.sequence.TechniqueSequence;
import com.hmc.zenkai.feature.technique.sequence.steps.PauseStep;
import com.hmc.zenkai.feature.technique.sequence.steps.VfxStep;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import com.hmc.zenkai.registry.*;

/**
 * Comandos de depuración (/zenkai debug …): estados especiales, cuerpo, party, muñeco de pruebas, kivfx, secuencias. Solo admin.
 */
public final class DebugCommands {
    private DebugCommands() {}

    /** Distancia fija a la que aparece "/zenkai debug kivfx spawn", delante de la mirada de
     *  quien ejecuta. Suficiente para ver el proyectil entero (incluida la envolvente y la
     *  estela) sin quedar dentro de su propio cuerpo con las técnicas más grandes a tamaño
     *  alto. */
    private static final double DEBUG_KIVFX_DISTANCE = 6.0;

    /** Separación EXTRA entre técnicas contiguas en "kivfx spawnall", además del ancho real de
     *  cada una — sin esto, dos técnicas grandes casi se tocan por los bordes y estorban para
     *  distinguir dónde termina una y empieza la otra. */
    private static final double DEBUG_KIVFX_ROW_GAP = 2.0;

    /** Separación de los dos muñecos de «dummy clash» a cada lado de quien lo ejecuta, y cuánto
     *  por delante: a ~5,4 bloques de cada uno, dentro del clash.join_range por defecto (6). */
    private static final double CLASH_DUMMY_SIDE = 4.5, CLASH_DUMMY_AHEAD = 3.0;

    /** Bloques delante de quien ejecuta a los que aparece el muñeco. */
    private static final double DUMMY_SPAWN_DISTANCE = 10.0;
    private static final double DUMMY_SEARCH_RADIUS = 128.0;
    private static final int DUMMY_DEFAULT_SIZE = 3;
    /** 0 = sin intervalo propio: dispara con el cooldown real de la técnica. */
    private static final int DUMMY_DEFAULT_INTERVAL = 0;

    public static int debugBody(CommandContext<CommandSourceStack> c, boolean fill) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        java.util.List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
        if (fill) {
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                var s = sp.getInventory().getItem(i);
                if (!s.isEmpty()) items.add(s.copy());
            }
        }
        var body = com.hmc.zenkai.feature.player.death.PlayerBodyManager.spawnFor(sp, sp.serverLevel(), items,
                fill ? com.hmc.zenkai.feature.player.death.PlayerBodyManager.Snapshot.capture(sp) : null);
        if (body == null) {
            c.getSource().sendFailure(Component.literal("No se pudo crear el cuerpo."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Cuerpo creado" + (fill ? " con una copia de tu inventario." : " vacío.")), false);
        return 1;
    }

    /** {@code /zenkai body killall [spill]}: elimina cada cuerpo del mundo (y SOLO cuerpos). Con {@code spill} suelta antes su contenido. */
    public static int bodyKillAll(CommandContext<CommandSourceStack> c, boolean spill) {
        int n = com.hmc.zenkai.feature.player.death.PlayerBodyManager.killAll(c.getSource().getServer(), spill);
        c.getSource().sendSuccess(() -> Component.translatable("zenkai.body.kill_all", n), true);
        return n;
    }

    public static int debugBodyClear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
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

    public static int debugParty(CommandContext<CommandSourceStack> c, int n) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int added = com.hmc.zenkai.feature.party.PartyService.debugFill(c.getSource().getServer(), sp, n);
        c.getSource().sendSuccess(() -> Component.literal("Party de prueba: +" + added
                + " compañeros falsos. Elígelos con el diamante en la pestaña Party."), false);
        return added;
    }

    public static int debugPartyClear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int removed = com.hmc.zenkai.feature.party.PartyService.debugClear(c.getSource().getServer(), sp);
        c.getSource().sendSuccess(() -> Component.literal("Quitados " + removed + " compañeros falsos."), false);
        return removed;
    }

    /** Ver el comentario de las ramas "debug divine/majin/legendary" más arriba. Un solo helper
     *  para los tres en vez de triplicar el mismo cuerpo — solo cambia la etiqueta y qué setter
     *  de PlayerStatsAttachment llamar. Acepta el objetivo como parámetro (en vez de derivarlo
     *  de quien ejecuta) para poder usarse tanto self-only como con un <player> explícito. */
    public static int debugSetSpecialState(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String label,
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
    public static int debugSetMajinControlled(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, boolean value) {
        // MajinEffect.setControlled hace TRES cosas juntas y SÍNCRONAS: flag, MobEffectInstance
        // y sync visual. No basta con tocar solo el flag (ver el javadoc de ese método para la
        // condición de carrera exacta que eso produce con el propio tick del efecto).
        MajinEffect.setControlled(sp, value);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] isMajinControlled = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    public static int debugEntity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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
    public static int debugSetConnectorSocket(CommandContext<CommandSourceStack> ctx, ResourceLocation socket)
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

    /**
     * Rama "spawn" (crea uno) o "set" (cambia el guion de los que ya hay). Misma gramática en los
     * dos: {@code <modo>} para idle/block/melee/physical/follow/flee, y
     * {@code spam_ki <tipo> [tamaño] [intervalo_ticks]}.
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> dummyModes(
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
                        .suggests(ModCommands.KI_TYPE_IDS)
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

    public static int debugDummy(CommandContext<CommandSourceStack> ctx, boolean spawn,
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

    /**
     * «/zenkai debug dummy clash [tipo]»: dos muñecos nuevos, uno a cada lado, se disparan un haz
     * el uno al otro y chocan. Es la única forma de tener en un mundo de un jugador un choque en el
     * que TÚ no estás, para probar «Clic derecho para ayudar» (ClashJoinClient). Colores distintos
     * para distinguir los lados en la esfera. Se borran con «dummy clear».
     */
    public static int debugDummyClash(CommandContext<CommandSourceStack> ctx, String typeId)
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

    public static void fireDebugBeam(ServerLevel sl, net.minecraft.world.entity.LivingEntity from,
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
    public static int debugDummyJoin(CommandContext<CommandSourceStack> ctx, boolean ally)
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
    public static int debugDummyGrab(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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

    public static int debugStun(CommandContext<CommandSourceStack> ctx, int ticks)
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

    public static int debugClearDummies(CommandContext<CommandSourceStack> ctx)
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
}
