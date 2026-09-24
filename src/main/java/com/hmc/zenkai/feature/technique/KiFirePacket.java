package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * C2S: disparar la técnica del slot con la carga acumulada (R + click derecho; soltar
 * click dispara).
 * VALIDACIÓN 100% SERVIDOR, vía ActionRules (mismas reglas que evalúa el cliente para
 * predecir): raza, modo combate, manos vacías, no derribado, no bloqueando, técnica
 * desbloqueada y habilitada, cooldown (global + por slot), carga mínima y energía.
 * CARGA AUTORITATIVA: el campo chargeTicks del paquete NO se usa para decidir nada. La carga
 * real la deriva el servidor de gameTime - KiChargeServer.startTick, y se exige que el slot
 * que dispara sea el MISMO que registró la carga. Antes se aceptaba el valor del cliente y
 * solo se clampaba al máximo, así que un cliente modificado disparaba cualquier técnica al
 * 200% de sobrecarga de forma instantánea, y podía cargar un slot barato para disparar otro
 * caro. chargeTicks se conserva en el codec solo para que el cliente pinte su barra sin
 * round-trip.
 * Cambiar de técnica cancela la carga: la nueva instancia arranca con startTick nuevo y
 * ratio 0, así que un láser al 100% nunca se convierte en un Kamehameha instantáneo.
 * Ratio 0..2.0 (SOBRECARGA hasta el 200%) vía KiCombatServer.chargeRatio. El daño escala
 * LINEAL con la carga; el coste 1:1 hasta el 100% y con recargo por encima
 * (chargeCostFactor), así sobrecargar dobla el daño pero cuesta 2.5x y tarda 3.5x.
 * BARRIER también escala con la carga, pero repartida entre pool y duración en vez de
 * concentrada en un solo número (ver KiCombatServer.chargeSplitFactor). Fórmulas en
 * KiCombatServer, compartidas con las previews del editor.
 */
public record KiFirePacket(int slot, int chargeTicks, @Nullable Vec3 originHint) implements CustomPacketPayload {

    private static final float BURST_SPREAD_DEG = 6.0f;

    public static final Type<KiFirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_fire"));

    public static final StreamCodec<FriendlyByteBuf, KiFirePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.slot());
                        buf.writeVarInt(pkt.chargeTicks());
                        buf.writeBoolean(pkt.originHint() != null);
                        if (pkt.originHint() != null) {
                            buf.writeDouble(pkt.originHint().x);
                            buf.writeDouble(pkt.originHint().y);
                            buf.writeDouble(pkt.originHint().z);
                        }
                    },
                    buf -> new KiFirePacket(buf.readVarInt(), buf.readVarInt(),
                            buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiFirePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                // La pista solo vive durante ESTE disparo: releaseKi → execute → spawnProjectile
                // corre entero aquí dentro, en el hilo del servidor.
                releaseHint = pkt.originHint();
                try {
                    com.hmc.zenkai.feature.action.ActionResolver.releaseKi(sp, pkt.slot());
                } finally {
                    releaseHint = null;
                }
            }
        });
    }

    /** Efecto del disparo. NO VALIDA: lo hizo ActionResolver, que además calculó ratio y
     *  coste con la carga autoritativa.
     *  El slot llega como parámetro A PROPÓSITO: es la clave del cooldown por slot, y
     *  deducirlo aquí del KiTechnique ya provocó que el conjunto de técnicas compartiera
     *  cooldown. La clave que usa tryFire tiene que ser la MISMA que consulta isReady. */
    public static void execute(ServerPlayer sp, PlayerStatsAttachment att, KiTechnique tech,
                               int slot, double ratio, double rawRatio, int cost,
                               TechniqueEffect effect) {
        KiTechniqueType type = tech.type();
        if (!KiCombatServer.tryFire(sp, slot,
                KiCombatServer.cooldownTicksFor(type, tech.size()))) return;

        att.addEnergy(-cost);
        att.addTechniqueMastery(type.name(), (float) ServerConfig.techMasteryPerUse());

        double kiPower = att.computeKiPowerFinal();
        if (type.defensive()) {
            KiCombatServer.activateBarrier(sp, tech, kiPower, ratio);
        } else if (type == KiTechniqueType.EXPLOSION) {
            // El sacrificio se cobra PRIMERO porque es parte de la munición: el daño no se
            // puede calcular hasta saber cuánta vida se ha quemado.
            int spent = selfDamage(sp, att, rawRatio);
            double damage = KiCombatServer.computeDamage(kiPower, type, tech.size()) * ratio
                    + KiCombatServer.explosionSacrificeDamage(spent);
            spawnAttached(sp, tech, damage, effect);
        } else {
            double damage = KiCombatServer.computeDamage(kiPower, type, tech.size()) * ratio;
            for (int i = 0; i < Math.max(1, type.count()); i++) {
                spawnProjectile(sp, tech, damage, effect, i);
            }
        }
        ZenkaiTriggers.TECHNIQUE_USED.get().trigger(sp,
                type.name().toLowerCase(java.util.Locale.ROOT), rawRatio);
        PlayerLifeCycle.syncIfServer(sp);
    }

    /**
     * Precio de la autodetonación. Se cobra sobre el CUERPO directamente y no por hurt(), para
     * que ignore la defensa: es daño verdadero, no un golpe recibido — si pasara por la
     * mitigación, un tanque se autodetonaría gratis.
     * A carga máxima la fracción vale 1.0 y el cuerpo queda a cero. Que eso signifique quedar
     * ABATIDO y no muerto lo decide DownedDeathGuard interceptando LivingDeathEvent, igual que
     * con cualquier otra muerte: aquí no se duplica esa regla.
     * ⚠ VERIFICAR EN JUEGO: mirrorHealth no toca la vida cuando queda <= 0, así que bajar el
     *   cuerpo a cero puede NO disparar el evento de muerte. Por eso se fuerza con un hurt
     *   masivo. Si el guardia no salta, dime y lo resuelvo por la vía que use tu pipeline.
    /** @return puntos de cuerpo realmente gastados, que alimentan el daño del estallido. */
    private static int selfDamage(ServerPlayer sp, PlayerStatsAttachment att, double rawRatio) {
        int max = att.getBodyMax();
        int loss = Math.min(att.getBody(),
                (int) Math.ceil(max * KiCombatServer.selfDamageFraction(rawRatio)));
        att.addBody(-loss);

        if (att.getBody() <= 0) {
            att.setBody(0);
            // Fuente propia (KI_SELF_DESTRUCT) en vez de magic() genérico: así, si esto es lo
            // que finalmente lo mata (ver DeathCauseTracker), el mensaje dice "se autodestruyó"
            // en vez del genérico "murió".
            sp.hurt(sp.damageSources().source(ModDamageTypes.KI_SELF_DESTRUCT, sp), Float.MAX_VALUE);
        }
        return loss;
    }

    /** Entidad pegada al dueño: la explosión no viaja. `life` es la MECHA, y coincide con el
     *  clip de disparo para que el estallido caiga cuando el cuerpo se abre. */
    private static void spawnAttached(ServerPlayer sp, KiTechnique tech, double damage,
                                      TechniqueEffect effect) {
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());
        proj.configure(sp, tech.type(), tech.rgb(), tech.size(), damage,
                tech.type().animTicks(), effect);
        proj.setRgb2(tech.rgb2());
        Vec3 c = sp.position().add(0, sp.getBbHeight() * 0.5, 0);
        proj.setPos(c.x, c.y - proj.getBbHeight() * 0.5, c.z);
        sp.level().addFreshEntity(proj);

        SoundEvent snd = TechniqueAssets.soundOf(tech.releaseSound());
        if (snd != null) {
            sp.level().playSound(null, c.x, c.y, c.z, snd, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }
    /** Pista de origen del disparo en curso (ver {@link #spawnCenter}); null fuera de handle(). */
    @Nullable
    private static Vec3 releaseHint = null;

    /** Distancia máxima, en bloques y ANTES de sumar el tamaño de la técnica, entre los ojos y la
     *  pista del cliente. Cubre una pose con los brazos estirados (Kamehameha, Final Flash) o en
     *  alto (Death Ball); lo que la técnica mida de más se suma aparte. */
    private static final double HINT_REACH = 2.2;

    /**
     * De dónde sale la técnica. PREFERENCIA: el centro de la bola de carga tal como la dibujó el
     * cliente, que sigue los huesos REALES de la animación (PlayerHandTracker) — así el
     * proyectil, y con él el haz anclado, sale de las manos que el jugador está viendo, y no del
     * offset fijo de TechniquePosition (que no sabe si la pose tiene los brazos al frente, al
     * costado o sobre la cabeza). El servidor no tiene huesos, por eso hace falta la pista.
     * NO SE FÍA A CIEGAS (el cliente podría mandar cualquier punto): la pista se descarta si
     * queda más lejos de los ojos que HINT_REACH + el tamaño de la técnica, o si entre los ojos
     * y ella hay un bloque — sin esto, un cliente modificado dispararía desde el otro lado de
     * una pared. Descartada, o sin pista (NPC, cliente viejo), vale el offset de siempre.
     */
    private static Vec3 spawnCenter(ServerPlayer sp, KiTechnique tech, Vec3 dir) {
        // El enum ya lo orienta con la mirada y lo escala con el tamaño del jugador; aquí solo se
        // empuja un poco hacia delante para que no nazca dentro del propio modelo.
        Vec3 fallback = tech.position().origin(sp).add(dir.scale(0.45));
        Vec3 hint = releaseHint;
        // Un tipo con animación impuesta (Genki Dama...) sostiene la bola donde se dispara; un
        // set normal lo dice él mismo (ver TechniqueAnimSet.firesFromChargePose).
        boolean fromChargePose = tech.type().animOverride() != null
                || TechniqueAnimSet.byNumber(tech.animSet()).firesFromChargePose();
        if (hint == null || !fromChargePose) return fallback;
        Vec3 eye = sp.getEyePosition();
        double reach = (HINT_REACH + tech.type().projectileSize(tech.size())) * sp.getScale();
        if (hint.distanceToSqr(eye) > reach * reach) return fallback;
        var clip = sp.level().clip(new net.minecraft.world.level.ClipContext(eye, hint,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
        if (clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return fallback;
        return hint;
    }

    private static void spawnProjectile(ServerPlayer sp, KiTechnique tech, double damage,
                                        TechniqueEffect effect, int index) {
        KiTechniqueType type = tech.type();
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());

        // Dispersión solo en ráfagas (el primer proyectil va recto).
        float yawJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;
        float pitchJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;

        Vec3 dir = Vec3.directionFromRotation(sp.getXRot() + pitchJitter, sp.getYRot() + yawJitter);

        proj.configure(sp, type, tech.rgb(), tech.size(), damage, 100, effect);
        proj.setRgb2(tech.rgb2());
        Vec3 spawn = spawnCenter(sp, tech, dir);
        // `spawn` es el CENTRO de la técnica; setPos coloca los PIES de la entidad. Antes se
        // pasaba el punto tal cual, así que el cuerpo nacía media altura por ENCIMA del punto de
        // salida — imperceptible en un Kienzan, casi un bloque en una Genki Dama.
        proj.setPos(spawn.x, spawn.y - proj.getBbHeight() * 0.5, spawn.z);
        proj.setDeltaMovement(dir.scale(type.speed()));
        sp.level().addFreshEntity(proj);

        // Sonido de disparo: solo con el primer proyectil, o una ráfaga lo solaparía cinco
        // veces. Desde el servidor y con player=null, así lo oye cualquiera de alrededor.
        if (index == 0) {
            SoundEvent snd = TechniqueAssets.soundOf(tech.releaseSound());
            if (snd != null) {
                sp.level().playSound(null, spawn.x, spawn.y, spawn.z,
                        snd, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }
}