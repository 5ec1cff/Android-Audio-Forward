package five.ec1cff.audiostreamer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Runtime;
import java.nio.ByteBuffer;
import java.util.Scanner;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.system.ErrnoException;
import android.system.Os;

// ffplay -ar 48000 -channels 1 -f f32le -i voice.pcm

public class AudioRec {
    static AudioRecord mRecord = null;
    static boolean mReqStop = false;

    private static int time = 5000;

    public static void main(String[] args) {
        try {
            init(args);
            (new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.currentThread().sleep(time);
                    } catch (InterruptedException e) {

                    }
                    AudioRec.stop();
                }
            }).start();
            try {
                recordAndPlay();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int kSampleRate = 48000;
    private static int kChannelMode = AudioFormat.CHANNEL_IN_MONO;
    private static int kEncodeFormat = AudioFormat.ENCODING_PCM_16BIT;

    private static void init(String[] args) throws ErrnoException, IOException {
        for (int i = 0;i < args.length;i++) {
            if(args[i].equals("-ar")) {
                kSampleRate = Integer.parseInt(args[++i]);
                continue;
            } else if(args[i].equals("-f")){
                /*
                switch(args[++i]){
                    case "u8":
                        kEncodeFormat = AudioFormat.ENCODING_PCM_8BIT;
                        break;
                    case "u16le":
                        kEncodeFormat = AudioFormat.ENCODING_PCM_16BIT;
                        break;
                    case "f32le":
                        kEncodeFormat = AudioFormat.ENCODING_PCM_FLOAT;
                        break;
                }
                */
                kEncodeFormat = Integer.parseInt(args[++i]);
                continue;
            } else if(args[i].equals("-o")){
                filePath = args[++i];
                continue;
            } else if(args[i].equals("-t")){
                time = Integer.parseInt(args[++i]);
                continue;
            } else if (args[i].equals("--uid")) {
                Os.seteuid(Integer.parseInt(args[++i]));
                continue;
            }
        }
        System.out.println("path:" + filePath);
        System.out.println("time:" + time);
    }

    private static final int kFrameSize = 2048;
    private static String filePath = "/sdcard/voice.pcm";

    @SuppressLint("MissingPermission")
    private static void recordAndPlay() {
        FileOutputStream os = null;
        try {
            int minBufferSize = AudioRecord.getMinBufferSize(kSampleRate, kChannelMode,
                    kEncodeFormat);
            System.out.println("minBufferSize=" + minBufferSize);
            mRecord = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX,
                    kSampleRate, kChannelMode, kEncodeFormat, minBufferSize);
            int state;
            if ((state = mRecord.getState()) != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("state=" + state);
            }
            audioSetForceUse("FOR_LOOPBACK", "FORCE_SPEAKER");
            mRecord.startRecording();
            if ((state = mRecord.getRecordingState()) != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("recordingState=" + state);
            }
            os = new FileOutputStream(filePath);
            ByteBuffer buffer = ByteBuffer.allocateDirect(kFrameSize);
            int num = 0;
            while (!mReqStop) {
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
            System.out.println("\nDump PCM to file failed");
        } finally {
            mRecord.stop();
            mRecord.release();
            mRecord = null;
            audioSetForceUse("FOR_LOOPBACK", "FORCE_NONE");
            System.out.println("clean up");
        }
    }

    public static void stop() {
        mReqStop = true;
    }

    private static void audioSetForceUse(String str, String str2) {
        try {
            Class<?> cls = Class.forName("android.media.AudioSystem");
            cls.getMethod("setForceUse", new Class[]{Integer.TYPE, Integer.TYPE}).invoke(cls, new Object[]{Integer.valueOf(cls.getDeclaredField(str).getInt((Object) null)), Integer.valueOf(cls.getDeclaredField(str2).getInt((Object) null))});
        } catch (Exception e) {
            System.out.println("error while in setForceUsage");
            e.printStackTrace();
        }
    }
}