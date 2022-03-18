package five.ec1cff.audiostreamer;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
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
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

@SuppressLint("MissingPermission")
public class AACStream {
    private AudioRecord mRecord = null;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mAudioEncodeBufferInfo;

    private int time = 5000;
    private Handler h;
    private int port = 51415;

    public static void main(String[] args) {
        new AACStream().run(args);
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
                case "-t":
                    time = Integer.parseInt(args[++i]);
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
        System.out.println("time:" + time);
        int minBufferSize = AudioRecord.getMinBufferSize(kSampleRate, kChannelMode,
                kEncodeFormat);
        System.out.println("minBufferSize=" + minBufferSize);
        mRecord = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX,
                kSampleRate, kChannelMode, kEncodeFormat, minBufferSize);
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    kSampleRate, kChannelMode == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
            mAudioEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            mAudioEncoder.start();
            mAudioEncodeBufferInfo = new MediaCodec.BufferInfo();
            ByteBuffer buffer = ByteBuffer.allocateDirect(kFrameSize);
            ByteBuffer header = ByteBuffer.allocateDirect(7);
            WritableByteChannel channel = Channels.newChannel(os);
            int num;
            int rec = 0;
            int enc = 0;
            while (true) {
                num = mRecord.read(buffer, kFrameSize);
                if (num < 0) {
                    System.out.println("\nbreak: err = " + num);
                    break;
                }
                int inputBufferId = mAudioEncoder.dequeueInputBuffer(0);
                if (inputBufferId >= 0) {
                    ByteBuffer input = mAudioEncoder.getInputBuffer(inputBufferId);
                    input.clear();
                    input.limit(num);
                    input.put(buffer);
                    mAudioEncoder.queueInputBuffer(inputBufferId, 0, num, 0,0);
                    rec += 1;
                }
                int outputBufferId = mAudioEncoder.dequeueOutputBuffer(mAudioEncodeBufferInfo, 0);
                if (outputBufferId >= 0) {
                    writeHeader(header, mAudioEncodeBufferInfo.size);
                    channel.write(header);
                    ByteBuffer output = mAudioEncoder.getOutputBuffer(outputBufferId);
                    channel.write(output);
                    mAudioEncoder.releaseOutputBuffer(outputBufferId, false);
                    enc += 1;
                }
                System.out.print("rec=" + rec + " enc=" + enc + "\r");
            }
            System.out.println("\nexit loop");
            os.close();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            mRecord.stop();
            mRecord.release();
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mRecord = null;
            // audioSetForceUse("FOR_LOOPBACK", "FORCE_NONE");
            System.out.println("clean up");
        }
    }

    public void writeHeader(ByteBuffer buf, int packetLen) throws IOException {
        buf.clear();
        int profile = 2; // AAC LC
        int chanCfg = 2; // CPE
        int sampleRateType = 3; // 48000
        packetLen += 7;

        buf.put((byte) 0xFF);
        buf.put((byte) 0xF9);
        buf.put((byte) (((profile - 1) << 6) + (sampleRateType << 2) + (chanCfg >> 2)));
        buf.put((byte) (((chanCfg & 3) << 6) + (packetLen >> 11)));
        buf.put((byte) ((packetLen & 0x7FF) >> 3));
        buf.put((byte) (((packetLen & 7) << 5) + 0x1F));
        buf.put((byte) 0xFC);
    }

    private static void audioSetForceUse(String str, String str2) {
        // Workarounds for MIUI ?
        try {
            Class<?> cls = Class.forName("android.media.AudioSystem");
            cls.getMethod("setForceUse", new Class[]{Integer.TYPE, Integer.TYPE}).invoke(cls, new Object[]{Integer.valueOf(cls.getDeclaredField(str).getInt((Object) null)), Integer.valueOf(cls.getDeclaredField(str2).getInt((Object) null))});
        } catch (Exception e) {
            System.out.println("error while in setForceUsage");
            e.printStackTrace();
        }
    }
}
