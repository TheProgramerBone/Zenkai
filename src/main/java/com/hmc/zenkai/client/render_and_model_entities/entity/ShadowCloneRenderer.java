package com.hmc.zenkai.client.render_and_model_entities.entity;

import com.hmc.zenkai.content.entity.misc.ShadowCloneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer de ShadowCloneEntity: el modelo/textura del PROPIO JUGADOR dueño (no un modelo
 * GeckoLib placeholder — ver git history, la v1 reusaba "isaac" y el usuario pidió expresamente
 * que fuera "el modelo del jugador"). PlayerModel es el modelo vainilla estándar (el mismo que
 * PlayerRenderer usa para cualquier jugador real); la TEXTURA se resuelve en runtime buscando el
 * PlayerInfo del dueño en la tab list — mismo mecanismo que PartyScreen.skinOf() ya usa para
 * dibujar la cabeza de un miembro, aplicado aquí a un mob en vez de a un widget de GUI.
 *
 * NO extiende GenericGeoRenderer/GeoEntityRenderer a propósito: PlayerModel es un modelo
 * VAINILLA (huesos/textura de skin real), completamente ajeno al pipeline GeckoLib — mezclar
 * los dos no tiene sentido aquí. ShadowCloneEntity sigue implementando GeoEntity (heredado de
 * ZenkaiDefaultMob, para reusar addKiAttackGoalIfDefined) pero ese lado queda sin uso VISUAL
 * para esta entidad en concreto: su animación de golpe/caminar la resuelve LivingEntityRenderer
 * solo, igual que para cualquier mob humanoide vainilla.
 *
 * Sin tinte oscuro: LivingEntityRenderer no expone un hook limpio para multiplicar un color por
 * entidad sin sobreescribir render() entero (el int de color que sí acepta EntityModel.
 * renderToBuffer lo decide render() internamente, no es un parámetro de subclase) — forzarlo
 * copiando ese método a mano sería frágil ante cualquier actualización de versión. El nombre
 * flotante ("Fulanito's Shadow") ya distingue la sombra de el jugador real sin necesitar tinte.
 */
public class ShadowCloneRenderer extends LivingEntityRenderer<ShadowCloneEntity, PlayerModel<ShadowCloneEntity>> {

    public ShadowCloneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ShadowCloneEntity entity) {
        var ownerId = entity.getOwnerId();
        if (ownerId != null) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(ownerId);
                if (info != null) return info.getSkin().texture();
            }
        }
        // Dueño desconectado/todavía no resuelto en la tab list: silueta por defecto de Steve,
        // mismo respaldo que PartyScreen.skinOf() usa para un jugador sin PlayerInfo.
        return DefaultPlayerSkin.get(ownerId != null ? ownerId : entity.getUUID()).texture();
    }
}
