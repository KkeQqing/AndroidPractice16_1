package com.example.androidpractice16_1;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import androidx.lifecycle.LifecycleOwner;

public class UdpVideoChat {
    private MainActivity activity;
    private SurfaceView svLocal, svRemote;
    private UdpVideoHandler handler;
    private String remoteIp;
    private int remotePort, localPort;

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis imageAnalysis;
    private PreviewView previewView; // 如果需要，但我们用 SurfaceView 直接绘制预览

    public UdpVideoChat(MainActivity activity, SurfaceView svLocal, SurfaceView svRemote,
                        String remoteIp, int remotePort, int localPort) {
        this.activity = activity;
        this.svLocal = svLocal;
        this.svRemote = svRemote;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.localPort = localPort;
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void start() {
        try {
            // 初始化 UDP 收发
            handler = new UdpVideoHandler(svRemote.getHolder(), remoteIp, remotePort, localPort);
            // 设置远程帧回调，在 SurfaceView 上绘制
            handler.setCallback(bitmap -> {
                Canvas canvas = svRemote.getHolder().lockCanvas();
                if (canvas != null) {
                    canvas.drawBitmap(bitmap, null, new Rect(0, 0, svRemote.getWidth(), svRemote.getHeight()), null);
                    svRemote.getHolder().unlockCanvasAndPost(canvas);
                }
            });
            handler.start();

            // 启动摄像头采集
            ProcessCameraProvider.getInstance(activity).addListener(() -> {
                try {
                    cameraProvider = ProcessCameraProvider.getInstance(activity).get();
                    bindCameraUseCases();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, ContextCompat.getMainExecutor(activity));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void bindCameraUseCases() {
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA; // 前置摄像头

        // 预览：直接渲染到 svLocal
        PreviewView previewView = new PreviewView(activity); // 用 SurfaceView 更直接
        // 我们改用 SurfaceView 的 SurfaceHolder 作为预览目标
        SurfaceHolder localHolder = svLocal.getHolder();

        // 图像分析，用于获取帧并发送
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();
        imageAnalysis.setAnalyzer(activity.getMainExecutor(), imageProxy -> {
            sendFrameFromImage(imageProxy);
            imageProxy.close();
        });

        // 将预览和分析绑定到生命周期
        cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector,
                new Preview.Builder().setTargetSurface(localHolder.getSurface()).build(),
                imageAnalysis);
    }

    private void sendFrameFromImage(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) return;
        try {
            byte[] jpegData = compressYUV420ToJpeg(image);
            if (jpegData != null) {
                handler.sendFrame(jpegData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 将 YUV_420_888 Image 转换为 JPEG 字节数组
    private byte[] compressYUV420ToJpeg(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] nv21 = yuv420888ToNv21(image);
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out); // 质量 50
        return out.toByteArray();
    }

    // 将 Image 的三个平面数据转换为 NV21 字节数组
    private static byte[] yuv420888ToNv21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;
        byte[] nv21 = new byte[ySize + uvSize * 2];
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        yBuffer.get(nv21, 0, ySize);
        // UV 交错写入 NV21 的 VU 部分
        for (int i = 0; i < uvSize; i++) {
            nv21[ySize + i * 2] = vBuffer.get(i);     // V
            nv21[ySize + i * 2 + 1] = uBuffer.get(i); // U
        }
        return nv21;
    }

    public void stop() {
        if (handler != null) handler.stop();
        if (cameraProvider != null) cameraProvider.unbindAll();
    }
}