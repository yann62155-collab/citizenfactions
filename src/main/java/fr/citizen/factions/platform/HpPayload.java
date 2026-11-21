package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serveur -> client : PV maximum affiches du joueur (0 = compteur desactive, coeurs vanilla). */
public record HpPayload(float maxHp) implements CustomPacketPayload {
    public static final Type<HpPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "hp"));
    public static final StreamCodec<ByteBuf, HpPayload> CODEC = ByteBufCodecs.FLOAT.map(HpPayload::new, HpPayload::maxHp);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
