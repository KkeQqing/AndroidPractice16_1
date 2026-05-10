package com.example.androidpractice16_1;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.SurfaceHolder;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UdpVideoHandler {
    private DatagramSocket sendSocket, recvSocket;
    private InetAddress remoteAddr;
    private int remotePort;
    private Thread recvThread, sendThread;
    private volatile boolean isRunning = false;

    // 接收端重组缓存
    private ConcurrentHashMap<Integer, FrameSplitter.FrameReceiver> frameMap = new ConcurrentHashMap<>();
    private AtomicInteger frameId = new AtomicInteger(0);

    // 渲染用 SurfaceHolder
    private SurfaceHolder remoteHolder;
    private BitmapFactory.Options bmpOptions = new BitmapFactory.Options();

    // 待发送帧队列（简化，直接回调发送）
    private Callback callback;

    public interface Callback {
        void onRemoteFrameReady(Bitmap bitmap);
    }

    public UdpVideoHandler(SurfaceHolder remoteHolder, String remoteIp, int remotePort, int localPort) throws Exception {
        this.remoteHolder = remoteHolder;
        this.remoteAddr = InetAddress.getByName(remoteIp);
        this.remotePort = remotePort;
        recvSocket = new DatagramSocket(localPort);
        sendSocket = new DatagramSocket(); // 随机本地端口发送
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void start() {
        isRunning = true;
        startReceive();
    }

    private void startReceive() {
        recvThread = new Thread(() -> {
            byte[] buf = new byte[1600];
            while (isRunning) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    recvSocket.receive(packet);

                    // 解析包头
                    if (packet.getLength() < 8) continue;
                    int frameId = ((packet.getData()[0] & 0xFF) << 24) |
                            ((packet.getData()[1] & 0xFF) << 16) |
                            ((packet.getData()[2] & 0xFF) << 8)  |
                            (packet.getData()[3] & 0xFF);
                    int totalPackets = ((packet.getData()[4] & 0xFF) << 8) |
                            (packet.getData()[5] & 0xFF);
                    int packetIndex = ((packet.getData()[6] & 0xFF) << 8) |
                            (packet.getData()[7] & 0xFF);
                    byte[] data = new byte[packet.getLength() - 8];
                    System.arraycopy(packet.getData(), 8, data, 0, data.length);

                    FrameSplitter.FrameReceiver receiver = frameMap.computeIfAbsent(frameId,
                            k -> new FrameSplitter.FrameReceiver(frameId, totalPackets));
                    if (receiver.addPacket(packetIndex, data)) {
                        // 完整帧已收全，解码并显示
                        byte[] jpegData = receiver.getAssembledData();
                        frameMap.remove(frameId);
                        // 解码为 Bitmap
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, bmpOptions);
                        if (bitmap != null && callback != null) {
                            callback.onRemoteFrameReady(bitmap);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        recvThread.start();
    }

    // 发送一帧 JPEG 数据（由采集线程调用）
    public void sendFrame(byte[] jpegData) {
        if (!isRunning || jpegData == null || jpegData.length == 0) return;
        int id = frameId.incrementAndGet();
        byte[][] packets = FrameSplitter.split(id, jpegData);
        for (byte[] packet : packets) {
            try {
                DatagramPacket dp = new DatagramPacket(packet, packet.length, remoteAddr, remotePort);
                sendSocket.send(dp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        isRunning = false;
        if (recvThread != null) recvThread.interrupt();
        if (sendSocket != null) sendSocket.close();
        if (recvSocket != null) recvSocket.close();
    }
}