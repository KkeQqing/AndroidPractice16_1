package com.example.androidpractice16_1;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class UdpVideoChat {
    private MainActivity activity;
    private SurfaceView svLocal, svRemote;
    private UdpVideoHandler handler;
    private String remoteIp;
    private int remotePort, localPort;

    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageAnalysis imageAnalysis;

    public UdpVideoChat(MainActivity activity, SurfaceView svLocal, SurfaceView svRemote,
                        String remoteIp, int remotePort, int localPort) {
        this.activity = activity;
        this.svLocal = svLocal;
        this.svRemote = svRemote;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.localPort = localPort;
    }

    public void start() {
        try {
            // 初始化 UDP 收发
            handler = new UdpVideoHandler(svRemote.getHolder(), remoteIp, remotePort, localPort);
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

    private void bindCameraUseCases() {
        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

        // 构建 Preview，并通过 SurfaceProvider 绑定到 SurfaceView
        preview = new Preview.Builder().build();
        preview.setSurfaceProvider(activity.getMainExecutor(), request -> {
            Surface surface = svLocal.getHolder().getSurface();
            request.provideSurface(surface, activity.getMainExecutor(), result -> {
                // 此处可处理 surface 释放，留着空实现即可
            });
        });

        // 图像分析，用于获取帧并发送
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build();
        imageAnalysis.setAnalyzer(activity.getMainExecutor(), imageProxy -> {
            sendFrameFromImage(imageProxy);
            imageProxy.close();
        });

        // 绑定到生命周期
        cameraProvider.bindToLifecycle((LifecycleOwner) activity, cameraSelector,
                preview,
                imageAnalysis);
    }

    @ExperimentalGetImage
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
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 50, out);
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