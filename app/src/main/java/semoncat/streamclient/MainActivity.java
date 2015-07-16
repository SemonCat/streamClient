package semoncat.streamclient;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import semoncat.streamclient.utils.StringUtils;

public class MainActivity extends AppCompatActivity {

    private String TAG = MainActivity.class.getName();

    private Socket mSocket;

    private AudioTrack mAudioTrack;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupSocket();
        setupAudioTrack();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static final int RECORDER_SAMPLERATE = 8000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private boolean isRecord = false;

    private boolean isPlay = false;

    private int bufferSize;

    AudioRecord recorder;

    public void Record(View view) {
        if (isRecord) {
            if (recorder != null) {
                recorder.stop();
                isRecord = false;
            }
        } else {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);
            bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
            recorder.startRecording();
            isRecord = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] audioData = new byte[bufferSize];
                    int readSize = 0;

                    // in the loop to read data from audio and save it to file.
                    while (isRecord) {
                        readSize = recorder.read(audioData, 0, bufferSize);
                        if (AudioRecord.ERROR_INVALID_OPERATION != readSize) {
                            //Log.d(TAG,"AudioData:"+new String(audioData));
                            sendSocketData(audioData);
                        }
                    }
                }
            }).start();
        }


    }

    private void sendSocketData(byte[] data) {
        if (mSocket != null) {
            JSONObject obj1 = new JSONObject();
            try {
                String byteArray = StringUtils.bytesToHex(data);
                obj1.put("url", "/chat/sendVoiceMessage?voiceByte=" + byteArray);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            mSocket.emit("get", obj1);
        }
    }

    private void setupAudioTrack(){
        int bufferSize = AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);

    }

    private void playSteamAudio(byte[] buffer){
        mAudioTrack.write(buffer, 0, buffer.length);

        if (!isPlay){
            mAudioTrack.play();
            isPlay = true;
        }
    }

    private void setupSocket() {
        try {
            IO.Options opts = new IO.Options();
            opts.query = "__sails_io_sdk_version=0.11.0&__sails_io_sdk_platform=android&__sails_io_sdk_language=java";
            mSocket = IO.socket("http://xx.xx.xx.xx:1337", opts);

            mSocket.on("voiceReceive", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d(TAG, "voiceReceive:" + args[0]);
                    byte[] data = StringUtils.hexToByteArray(args[0].toString());
                    playSteamAudio(data);
                }
            });

            mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    JSONObject obj1 = new JSONObject();
                    try {
                        obj1.put("url", "/chat/getSocketID");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mSocket.emit("get", obj1);
                    Log.d(TAG, "Test");
                }
            });

            new SocketTask().execute();
        } catch (URISyntaxException e) {

        }
    }

    private class SocketTask extends AsyncTask<Void, Integer, Void> {


        @Override
        protected Void doInBackground(Void... params) {


            mSocket.connect();

            return null;

        }
    }

}
