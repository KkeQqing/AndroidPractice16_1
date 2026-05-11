package com.example.androidpractice16_1;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceHolder;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UdpVideoHandler {
    private DatagramSocket sendSocket, recvSocket;
    private InetAddress remoteAddr;
    private int remotePort;
    private Thread recvThread;
    private volatile boolean isRunning = false;

    // 接收端重组缓存
    private ConcurrentHashMap<Integer, FrameSplitter.FrameReceiver> frameMap = new ConcurrentHashMap<>();
    private AtomicInteger frameId = new AtomicInteger(0);

    private SurfaceHolder remoteHolder;
    private BitmapFactory.Options bmpOptions = new BitmapFactory.Options();

    private Callback callback;

    public interface Callback {
        void onRemoteFrameReady(Bitmap bitmap);
    }

    public UdpVideoHandler(SurfaceHolder remoteHolder, String remoteIp, int remotePort, int localPort) throws Exception {
        this.remoteHolder = remoteHolder;
        this.remoteAddr = InetAddress.getByName(remoteIp);
        this.remotePort = remotePort;
        recvSocket = new DatagramSocket(localPort);
        Log.d("VideoChat", "接收Socket绑定成功，端口: " + recvSocket.getLocalPort());
        sendSocket = new DatagramSocket();
        Log.d("VideoChat", "发送Socket创建成功，本地端口: " + sendSocket.getLocalPort());
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
            Log.d("VideoChat", "接收线程启动，监听端口: " + recvSocket.getLocalPort());
            while (isRunning) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    recvSocket.receive(packet);
                    Log.d("VideoChat", "收到UDP包, 来自: " + packet.getAddress() + ":" + packet.getPort() + ", 长度: " + packet.getLength());

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
                        byte[] jpegData = receiver.getAssembledData();
                        Log.d("VideoChat", "完整帧收全, 大小: " + jpegData.length);
                        frameMap.remove(frameId);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length, bmpOptions);
                        if (bitmap != null && callback != null) {
                            callback.onRemoteFrameReady(bitmap);
                        }
                    }
                } catch (Exception e) {
                    if (isRunning) {
                        Log.e("VideoChat", "接收异常", e);
                    }
                }
            }
            Log.d("VideoChat", "接收线程结束");
        });
        recvThread.start();
    }

    public void sendFrame(byte[] jpegData) {
        if (!isRunning || jpegData == null || jpegData.length == 0) return;
        int id = frameId.incrementAndGet();
        byte[][] packets = FrameSplitter.split(id, jpegData);
        Log.d("VideoChat", "准备发送帧ID=" + id + ", 分片数=" + packets.length + " 到 " + remoteAddr + ":" + remotePort);
        for (byte[] packet : packets) {
            try {
                DatagramPacket dp = new DatagramPacket(packet, packet.length, remoteAddr, remotePort);
                sendSocket.send(dp);
            } catch (Exception e) {
                Log.e("VideoChat", "发送出错", e);
            }
        }
        Log.d("VideoChat", "发送完成帧ID=" + id);
    }

    public void stop() {
        isRunning = false;
        if (recvThread != null) recvThread.interrupt();
        if (sendSocket != null) sendSocket.close();
        if (recvSocket != null) recvSocket.close();
    }
}