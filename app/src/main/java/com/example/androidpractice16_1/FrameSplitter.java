package com.example.androidpractice16_1;

import java.util.Arrays;

public class FrameSplitter {
    public static final int MAX_PACKET_SIZE = 1400; // 每个 UDP 包最大载荷

    // 包头格式: 4字节帧ID + 2字节分包总数 + 2字节当前包序号
    public static byte[][] split(int frameId, byte[] data) {
        int totalPackets = (int) Math.ceil(data.length / (double) MAX_PACKET_SIZE);
        byte[][] packets = new byte[totalPackets][];

        for (int i = 0; i < totalPackets; i++) {
            int offset = i * MAX_PACKET_SIZE;
            int length = Math.min(MAX_PACKET_SIZE, data.length - offset);
            // 8字节头 + 数据
            byte[] packet = new byte[8 + length];
            // 帧ID
            packet[0] = (byte)(frameId >> 24);
            packet[1] = (byte)(frameId >> 16);
            packet[2] = (byte)(frameId >> 8);
            packet[3] = (byte)(frameId);
            // 总包数
            packet[4] = (byte)(totalPackets >> 8);
            packet[5] = (byte)(totalPackets);
            // 当前包序号
            packet[6] = (byte)(i >> 8);
            packet[7] = (byte)(i);
            // 复制数据
            System.arraycopy(data, offset, packet, 8, length);
            packets[i] = packet;
        }
        return packets;
    }

    // 接收端缓存一帧的所有分片
    public static class FrameReceiver {
        private int frameId;
        private int totalPackets;
        private byte[][] buffer;
        private int receivedCount = 0;
        private long lastUpdate = System.currentTimeMillis();

        public FrameReceiver(int frameId, int totalPackets) {
            this.frameId = frameId;
            this.totalPackets = totalPackets;
            this.buffer = new byte[totalPackets][];
        }

        public boolean addPacket(int packetIndex, byte[] data) {
            if (packetIndex < 0 || packetIndex >= totalPackets || buffer[packetIndex] != null) {
                return false;
            }
            buffer[packetIndex] = data;
            receivedCount++;
            lastUpdate = System.currentTimeMillis();
            return isComplete();
        }

        public boolean isComplete() {
            return receivedCount == totalPackets;
        }

        public byte[] getAssembledData() {
            int totalLen = 0;
            for (byte[] part : buffer) {
                if (part != null) totalLen += part.length;
            }
            byte[] full = new byte[totalLen];
            int pos = 0;
            for (byte[] part : buffer) {
                System.arraycopy(part, 0, full, pos, part.length);
                pos += part.length;
            }
            return full;
        }

        public long getLastUpdate() { return lastUpdate; }
    }
}