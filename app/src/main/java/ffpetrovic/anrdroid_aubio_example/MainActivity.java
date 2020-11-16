package ffpetrovic.anrdroid_aubio_example;

import android.Manifest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.Map;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private int sampleRate = 0;
    private int bufferSize = 0;
    private int readSize = 0;
    private int amountRead = 0;
    private float[] buffer = null;
    private short[] intermediaryBuffer = null;

    /* These variables are used to store pointers to the C objects so JNI can keep track of them */
    public long ptr = 0;
    public long input = 0;
    public long pitch = 0;

    public boolean isRecording = false;
    private AudioRecord audioRecord = null;
    Thread audioThread;

    ActivityResultLauncher<String[]> launcher;
    private View btPermission;
    private View btStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btPermission = findViewById(R.id.bt_permission);
        btStop = findViewById(R.id.bt_stop);
        btPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermission();
            }
        });
        btStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecord();
            }
        });
        launcher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                if (result.containsValue(true)) {
                    btPermission.setVisibility(View.GONE);

                    init();
                    start();
                } else {
                    btPermission.setVisibility(View.VISIBLE);
                }
            }
        });
        requestPermission();
    }

    private void stopRecord() {
        btPermission.setVisibility(View.VISIBLE);
        btStop.setVisibility(View.GONE);
        if (isRecording) {
            isRecording = false;
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            if (audioThread != null) {
                audioThread.interrupt();
                audioThread = null;
            }

        }
    }

    private void requestPermission() {
        launcher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
    }

    static {
        System.loadLibrary("aubio");
        System.loadLibrary("pitch");
    }

    private void init() {
        sampleRate = 44100;
        bufferSize = 4096;
        readSize = bufferSize / 4;
        buffer = new float[readSize];
        intermediaryBuffer = new short[readSize];
    }

    //
    public void start() {
        if (!isRecording) {
            isRecording = true;
//        sampleRate = AudioUtils.getSampleRate();
//        bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
            initPitch(sampleRate, bufferSize);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioRecord.startRecording();
            audioThread = new Thread(new Runnable() {
                //Runs off the UI thread
                @Override
                public void run() {
                    System.out.println("线程继续运行");
                    findNote();
                }
            }, "Tuner Thread");
            audioThread.start();
        }
    }

    private void findNote() {
        while (isRecording && audioThread != null && !audioThread.isInterrupted()) {
            amountRead = audioRecord.read(intermediaryBuffer, 0, readSize);
            buffer = shortArrayToFloatArray(intermediaryBuffer);
            final float frequency = getPitch(buffer);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((TextView) findViewById(R.id.pitchView)).setText(String.valueOf(frequency));
                }
            });
        }
    }

    private float[] shortArrayToFloatArray(short[] array) {
        float[] fArray = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            fArray[i] = array[i];
        }
        return fArray;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecord();
    }

    private native float getPitch(float[] input);

    private native void initPitch(int sampleRate, int B);

    private native void cleanupPitch();
}
