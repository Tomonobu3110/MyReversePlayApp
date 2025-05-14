package com.example.myreverseplayapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.myreverseplayapp.R;

import java.io.*;
import android.graphics.Color;

public class MainActivity extends AppCompatActivity {

    private Button btnRecord, btnPlay, btnReverse;
    private boolean isRecording = false;
    private AudioRecord recorder;
    private Thread recordingThread;
    private String pcmFilePath, wavFilePath;

    private final int sampleRate = 44100;
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();

        btnRecord = findViewById(R.id.btnRecord);
        btnPlay = findViewById(R.id.btnPlay);
        btnReverse = findViewById(R.id.btnReverse);

        pcmFilePath = getExternalFilesDir(null).getAbsolutePath() + "/recorded.pcm";
        wavFilePath = getExternalFilesDir(null).getAbsolutePath() + "/recorded.wav";

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                if (startRecording()) {
                    btnRecord.setText("録音停止");
                    btnRecord.setBackgroundColor(Color.RED); // 録音中に赤く
                    btnPlay.setEnabled(false);
                    btnReverse.setEnabled(false);
                    // isRecording = true; // done in startRecording()
                }
            } else {
                stopRecording();
                btnRecord.setText("録音開始");
                btnRecord.setBackgroundColor(Color.parseColor("#674DA2")); // 停止後に戻す
                btnPlay.setEnabled(true);
                btnReverse.setEnabled(true);
                isRecording = false;
            }
        });

        btnPlay.setOnClickListener(v -> playWavFile(false));
        btnReverse.setOnClickListener(v -> playWavFile(true));

        btnRecord.setBackgroundColor(Color.parseColor("#674DA2")); // 初期色
        btnPlay.setBackgroundColor(Color.parseColor("#674DA2")); // 初期色
        btnReverse.setBackgroundColor(Color.parseColor("#674DA2")); // 初期色
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        }, 200);
    }

    private boolean startRecording() {
        // AudioRecord の初期化
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, bufferSize);

        // AudioRecord の初期化状態をチェック
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            //Log.e("Audio", "AudioRecord の初期化に失敗しました");
            Toast.makeText(this, "AudioRecord の初期化に失敗しました", Toast.LENGTH_SHORT).show();
            recorder = null;
            return false;
        }

        try {
            recorder.startRecording();
        } catch (IllegalStateException e) {
            //Log.e("Audio", "AudioRecord の startRecording に失敗しました", e);
            Toast.makeText(this, "AudioRecord の startRecording に失敗しました", Toast.LENGTH_SHORT).show();
            recorder.release();
            recorder = null;
            return false;
        }

        isRecording = true;

        // 録音スレッドの作成と起動
        recordingThread = new Thread(() -> {
            try {
                writeAudioDataToFile();
            } catch (Exception e) {
                //Log.e("Audio", "録音スレッド内でエラーが発生しました", e);
                Toast.makeText(this, "録音スレッド内でエラーが発生しました", Toast.LENGTH_SHORT).show();
            }
        }, "AudioRecorder Thread");

        try {
            recordingThread.start();
        } catch (IllegalThreadStateException e) {
            //Log.e("Audio", "録音スレッドの起動に失敗しました", e);
            Toast.makeText(this, "録音スレッドの起動に失敗しました", Toast.LENGTH_SHORT).show();
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
            return false;
        }

        return true;
    }

    private void writeAudioDataToFile() {
        byte[] data = new byte[bufferSize];
        try (FileOutputStream os = new FileOutputStream(pcmFilePath)) {
            while (isRecording) {
                int read = recorder.read(data, 0, bufferSize);
                if (read > 0) {
                    os.write(data, 0, read);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            isRecording = false; // to stop the loop in writeAudioDataToFile

            // スレッドの終了を待つ
            try {
                if (recordingThread != null) {
                    recordingThread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;

            pcmToWav(pcmFilePath, wavFilePath);
        } else {
            Toast.makeText(this, "録音が開始されていません", Toast.LENGTH_SHORT).show();
        }
    }

    private void pcmToWav(String pcmPath, String wavPath) {
        try (FileInputStream in = new FileInputStream(pcmPath);
             FileOutputStream out = new FileOutputStream(wavPath)) {

            long totalAudioLen = in.getChannel().size();
            long totalDataLen = totalAudioLen + 36;
            int channels = 1;
            long byteRate = 16 * sampleRate * channels / 8;

            writeWavHeader(out, totalAudioLen, totalDataLen, sampleRate, channels, byteRate);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeWavHeader(FileOutputStream out, long totalAudioLen,
                                long totalDataLen, int sampleRate, int channels, long byteRate)
            throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen); header[5] = (byte) (totalDataLen >> 8);
        header[6] = (byte) (totalDataLen >> 16); header[7] = (byte) (totalDataLen >> 24);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) sampleRate; header[25] = (byte) (sampleRate >> 8);
        header[26] = (byte) (sampleRate >> 16); header[27] = (byte) (sampleRate >> 24);
        header[28] = (byte) byteRate; header[29] = (byte) (byteRate >> 8);
        header[30] = (byte) (byteRate >> 16); header[31] = (byte) (byteRate >> 24);
        header[32] = 2; header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) totalAudioLen; header[41] = (byte) (totalAudioLen >> 8);
        header[42] = (byte) (totalAudioLen >> 16); header[43] = (byte) (totalAudioLen >> 24);

        out.write(header, 0, 44);
    }

    private void playWavFile(boolean reverse) {
        File wavFile = new File(wavFilePath);
        if (!wavFile.exists()) {
            runOnUiThread(() -> Toast.makeText(this, "WAVファイルが見つかりません", Toast.LENGTH_SHORT).show());
            return;
        }

        new Thread(() -> {
            try {
                // 色を変更
                runOnUiThread(() -> {
                    if (reverse) {
                        btnReverse.setBackgroundColor(Color.RED);
                    } else {
                        btnPlay.setBackgroundColor(Color.RED);
                    }
                });

                FileInputStream fis = new FileInputStream(wavFile);
                byte[] header = new byte[44]; // WAVヘッダー
                fis.read(header); // ヘッダー読み飛ばし

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                byte[] audioData = baos.toByteArray();
                fis.close();

                // WAV情報：44100Hz, 16bit, MONO を仮定
                int sampleRate = 44100;
                int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

                if (reverse) {
                    audioData = reversePCM(audioData);
                }

                int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                AudioTrack audioTrack = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                );

                audioTrack.play();
                audioTrack.write(audioData, 0, audioData.length);
                audioTrack.stop();
                audioTrack.release();

            } catch (IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "再生エラー: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                // 色を元に戻す
                runOnUiThread(() -> {
                    btnPlay.setBackgroundColor(Color.parseColor("#674DA2"));
                    btnReverse.setBackgroundColor(Color.parseColor("#674DA2"));
                });
            }
        }).start();
    }

    private byte[] reversePCM(byte[] data) {
        byte[] reversed = new byte[data.length];
        for (int i = 0; i < data.length; i += 2) {
            reversed[data.length - i - 2] = data[i];
            reversed[data.length - i - 1] = data[i + 1];
        }
        return reversed;
    }

/*
    private void playWavFile(boolean reverse) {
        new Thread(() -> {
            try (FileInputStream fis = new FileInputStream(wavFilePath)) {
                fis.skip(44); // ヘッダーをスキップ
                byte[] audioData = fis.readAllBytes();

                if (reverse) {
                    audioData = reversePCM(audioData);
                }

                AudioTrack audioTrack = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(audioData.length)
                        .build();

                audioTrack.play();
                audioTrack.write(audioData, 0, audioData.length);
                audioTrack.stop();
                audioTrack.release();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private byte[] reversePCM(byte[] data) {
        // 16bit（2バイト）単位で逆順にする
        byte[] reversed = new byte[data.length];
        for (int i = 0; i < data.length; i += 2) {
            reversed[data.length - i - 2] = data[i];
            reversed[data.length - i - 1] = data[i + 1];
        }
        return reversed;
    }
*/
}
