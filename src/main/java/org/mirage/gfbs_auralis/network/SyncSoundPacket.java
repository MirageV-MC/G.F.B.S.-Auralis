package org.mirage.gfbs_auralis.network;

/**
 * G.F.B.S. Mirage (mirage_gfbs) - A Minecraft Mod
 * Copyright (C) 2025-2029 Mirage-MC
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.mirage.gfbs_auralis.server.AuralisServerManager;

import java.util.function.Supplier;

public class SyncSoundPacket {
    private final ResourceLocation soundId;
    private final float volume;
    private final float pitch;
    private final boolean looping;

    public SyncSoundPacket(ResourceLocation soundId, float volume, float pitch, boolean looping) {
        this.soundId = soundId;
        this.volume = volume;
        this.pitch = pitch;
        this.looping = looping;
    }

    public static void encode(SyncSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.soundId);
        buffer.writeFloat(packet.volume);
        buffer.writeFloat(packet.pitch);
        buffer.writeBoolean(packet.looping);
    }

    public static SyncSoundPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation soundId = buffer.readResourceLocation();
        float volume = buffer.readFloat();
        float pitch = buffer.readFloat();
        boolean looping = buffer.readBoolean();
        return new SyncSoundPacket(soundId, volume, pitch, looping);
    }

    public static void handle(SyncSoundPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                AuralisServerManager.syncSound(player, packet.soundId, packet.volume, packet.pitch, packet.looping);
            }
        });
        context.get().setPacketHandled(true);
    }
}