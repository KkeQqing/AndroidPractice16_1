package com.example.androidpractice16_1;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private SurfaceView svLocal, svRemote;
    private EditText etRemoteIp, etRemotePort, etLocalPort;
    private Button btnStart;

    private UdpVideoChat videoChat;
    private boolean isRunning = false;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startChat();
                } else {
                    Toast.makeText(this, "必须授予相机权限才能使用", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        svLocal = findViewById(R.id.sv_local);
        svRemote = findViewById(R.id.sv_remote);
        etRemoteIp = findViewById(R.id.et_remoteip);
        etRemotePort = findViewById(R.id.et_remoteport);
        etLocalPort = findViewById(R.id.et_localport);
        btnStart = findViewById(R.id.btn_start);

        btnStart.setOnClickListener(v -> {
            if (!isRunning) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.CAMERA);
                } else {
                    startChat();
                }
            } else {
                stopChat();
            }
        });
    }

    private void startChat() {
        String remoteIp = etRemoteIp.getText().toString().trim();
        int remotePort = Integer.parseInt(etRemotePort.getText().toString().trim());
        int localPort = Integer.parseInt(etLocalPort.getText().toString().trim());

        videoChat = new UdpVideoChat(this, svLocal, svRemote, remoteIp, remotePort, localPort);
        videoChat.start();
        isRunning = true;
        btnStart.setText("停止");
    }

    private void stopChat() {
        if (videoChat != null) {
            videoChat.stop();
        }
        isRunning = false;
        btnStart.setText("开始");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopChat();
    }
}