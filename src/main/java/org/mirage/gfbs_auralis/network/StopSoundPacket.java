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

public class StopSoundPacket {
    public final ResourceLocation soundId;

    public StopSoundPacket(ResourceLocation soundId) {
        this.soundId = soundId;
    }

    public static void encode(StopSoundPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.soundId);
    }

    public static StopSoundPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation soundId = buffer.readResourceLocation();
        return new StopSoundPacket(soundId);
    }

    public static void handle(StopSoundPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                AuralisServerManager.stopSound(player, packet.soundId);
            }
        });
        context.get().setPacketHandled(true);
    }
}