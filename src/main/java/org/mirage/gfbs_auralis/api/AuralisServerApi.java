package org.mirage.gfbs_auralis.api;

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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.mirage.gfbs_auralis.network.NetworkHandler;
import org.mirage.gfbs_auralis.network.SoundControlPacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;

public class AuralisServerApi {

    public static int playSound(String id, ResourceLocation soundEventId, float volume, float pitch, float speed, boolean isStatic,
                                Vec3 pos, boolean looping, int priority, float minDistance, float maxDistance,
                                Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.PLAY,
                id,
                soundEventId,
                volume, pitch, speed,
                isStatic,
                pos.x, pos.y, pos.z,
                looping,
                priority,
                minDistance,
                maxDistance
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家发送播放指令: " + soundEventId + " (id=" + id + ")");
    }

    public static int pauseSound(String id, Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.PAUSE,
                id,
                new ResourceLocation("minecraft:empty"),
                0f, 0f, 0f,
                false,
                0d, 0d, 0d,
                false,
                0,
                0.1f,
                0.1f
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家发送暂停指令 (id=" + id + ")");
    }

    public static int stopSound(String id, Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.STOP,
                id,
                new ResourceLocation("minecraft:empty"),
                0f, 0f, 0f,
                false,
                0d, 0d, 0d,
                false,
                0,
                0.1f,
                0.1f
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家发送停止指令 (id=" + id + ")");
    }

    public static int setVolume(String id, float volume, Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.SET_VOLUME,
                id,
                new ResourceLocation("minecraft:empty"),
                volume, 0f, 0f,
                false,
                0d, 0d, 0d,
                false,
                0,
                0.1f,
                0.1f
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家设置音量 (id=" + id + ", volume=" + volume + ")");
    }

    public static int setPitch(String id, float pitch, Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.SET_PITCH,
                id,
                new ResourceLocation("minecraft:empty"),
                0f, pitch, 0f,
                false,
                0d, 0d, 0d,
                false,
                0,
                0.1f,
                0.1f
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家设置音高 (id=" + id + ", pitch=" + pitch + ")");
    }

    public static int setPosition(String id, Vec3 pos, Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.SET_POSITION,
                id,
                new ResourceLocation("minecraft:empty"),
                0f, 0f, 0f,
                false,
                pos.x, pos.y, pos.z,
                false,
                0,
                0.1f,
                0.1f
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家设置位置 (id=" + id + ", pos=" + pos.x + "," + pos.y + "," + pos.z + ")");
    }

    public static int setStatic(String id, boolean isStatic, Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.SET_STATIC,
                id,
                new ResourceLocation("minecraft:empty"),
                0f, 0f, 0f,
                isStatic,
                0d, 0d, 0d,
                false,
                0,
                0.1f,
                0.1f
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家设置静态模式 (id=" + id + ", static=" + isStatic + ")");
    }

    public static int setLooping(String id, boolean looping, Collection<ServerPlayer> targets) {
        if (targets == null) return 0;

        SoundControlPacket packet = new SoundControlPacket(
                SoundControlPacket.Action.SET_LOOPING,
                id,
                new ResourceLocation("minecraft:empty"),
                0f, 0f, 0f,
                looping,
                0d, 0d, 0d,
                false,
                0,
                0.1f,
                0.1f
        );

        return sendPacketToPlayers(packet, targets, "[GFBS Auralis] 已向 %d 名玩家设置循环 (id=" + id + ", looping=" + looping + ")");
    }

    private static int sendPacketToPlayers(SoundControlPacket packet, Collection<ServerPlayer> targets, String successMessage) {
        int sent = 0;
        for (ServerPlayer p : targets) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            sent++;
        }

        int finalSent = sent;

        return 1;
    }
}
