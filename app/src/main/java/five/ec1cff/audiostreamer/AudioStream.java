package five.ec1cff.audiostreamer;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

@SuppressLint("MissingPermission")
public class AudioStream {
    private AudioRecord mRecord = null;

    private Handler h;
    private int port = 51415;

    public static void main(String[] args) {
        new AudioStream().run(args);
    }

    private void run(String[] args) {
        Looper.prepareMainLooper();
        init(args);
        h = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 1) {
                    System.out.println("exit");
                    Looper.myLooper().quit();
                }
            }
        };
        (new Thread() {
            @Override
            public void run() {
                System.out.println("waiting for connection ...");
                try (Socket socket = connect()) {
                    streaming(socket.getOutputStream());
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    h.sendEmptyMessage(1);
                }
            }
        }).start();
        Looper.loop();
    }

    private Socket connect() throws IOException {
        try (ServerSocket localServerSocket = new ServerSocket(port)) {
            return localServerSocket.accept();
        }
    }

    private int kSampleRate = 48000;
    private int kChannelMode = AudioFormat.CHANNEL_IN_STEREO;
    private int kEncodeFormat = AudioFormat.ENCODING_PCM_16BIT;

    private void init(String[] args) {
        for (int i = 0;i < args.length;i++) {
            switch (args[i]) {
                case "-ar":
                    kSampleRate = Integer.parseInt(args[++i]);
                    break;
                case "-f":
                    kEncodeFormat = Integer.parseInt(args[++i]);
                    break;
                case "-c":
                    int c = Integer.parseInt(args[++i]);
                    kChannelMode = c == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
                    break;
                case "-p":
                    port = Integer.parseInt(args[++i]);
                    break;
            }
        }
        System.out.println("port:" + port);
        int minBufferSize = AudioRecord.getMinBufferSize(kSampleRate, kChannelMode,
                kEncodeFormat);
        System.out.println("minBufferSize=" + minBufferSize);
        mRecord = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX,
                kSampleRate, kChannelMode, kEncodeFormat, minBufferSize);
    }

    private final int kFrameSize = kSampleRate * 2 * 15 / 1000;

    private void streaming(OutputStream os) {
        try {
            int state;
            if ((state = mRecord.getState()) != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("state=" + state);
            }
            // audioSetForceUse("FOR_LOOPBACK", "FORCE_SPEAKER");
            mRecord.startRecording();
            if ((state = mRecord.getRecordingState()) != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("recordingState=" + state);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(kFrameSize);
            int num = 0;
            while (true) {
                num = mRecord.read(buffer, kFrameSize);
                if (num < 0) {
                    System.out.println("\nbreak: err = " + num);
                    break;
                }
                System.out.print("num = " + num + "\r");
                os.write(buffer.array(), 0, num);
            }
            System.out.println("\nexit loop");
            os.close();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            mRecord.stop();
            mRecord.release();
            mRecord = null;
            // audioSetForceUse("FOR_LOOPBACK", "FORCE_NONE");
            System.out.println("clean up");
        }
    }

    private static void audioSetForceUse(String str, String str2) {
        // Copy from MIUI ScreenRecorder
        try {
            Class<?> cls = Class.forName("android.media.AudioSystem");
            cls.getMethod("setForceUse", new Class[]{Integer.TYPE, Integer.TYPE}).invoke(cls, new Object[]{Integer.valueOf(cls.getDeclaredField(str).getInt((Object) null)), Integer.valueOf(cls.getDeclaredField(str2).getInt((Object) null))});
        } catch (Exception e) {
            System.out.println("error while in setForceUsage");
            e.printStackTrace();
        }
    }
}
