package com.geniatech.hdmiin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioStream {

    MediaRecorder mRecorder;
    String mFileName = null;
    private static final String TAG = "AudioStream";
    private static final String[] strOutDevice = {
            "bypass",                       // bypass to SPK in codec
            "hdmi",                         // output to hdmi
            "speaker",                      // output to SPK
            "usb",                          // output to usb audio
            "bluetooth",                    // output to bluetooth
            "hdmi,speaker,usb,bluetooth",   // output to all
            "hdmi,speaker",                 // boath hdmi and speaker
            ""                              // auto select audio device
    };
    @SuppressLint("StaticFieldLeak")
    private static AudioStream audioStream;
    private final Context mContext;
    private boolean isRecording = true;
    private boolean mIsStartup = false;
    private Thread record;
    private int mCurrOutput;

    public AudioStream() {
        mContext = MyApplication.context;
    }

    public static AudioStream getInstance() {
        if (audioStream == null) {
            audioStream = new AudioStream();
        }
        return audioStream;
    }

    public void switchAudioOutput(int output) {
        Log.d(TAG, "switchAudioOutput to: " + strOutDevice[output]);

        if (output == mCurrOutput) {
            Log.d(TAG, "current output already is %d" + output);
            return;
        }

        // stop audio
        stop();

        // start audio
        start(output);
    }

    public boolean start(int output) {
        Log.d(TAG, "start: " + output);

        if (mIsStartup) {
            Log.w(TAG, "already startup");
            return true;
        }

        mIsStartup = true;
        mCurrOutput = output;
        if (mCurrOutput == 0) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameters("HDMIin_enable=true");
            return true;
        }

        SystemProperties.set("media.audio.device_policy", strOutDevice[mCurrOutput]);
        Log.d(TAG, "setOutput: " + strOutDevice[mCurrOutput]);
        isRecording = true;
        record = new Thread(new recordSound());
        record.start();

        //RecordAudio();
        return true;
    }

    public void stop() {
        Log.d(TAG, "stop");
        if (mCurrOutput == 0) {
            AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            audioManager.setParameters("HDMIin_enable=false");
        }

        isRecording = false;
        try {
            // wait thread finish
            if (record != null)
                record.join(300);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mCurrOutput = 2;
        SystemProperties.set("media.audio.device_policy", strOutDevice[mCurrOutput]);
        Log.d(TAG, "setOutput: " + strOutDevice[mCurrOutput]);
        mIsStartup = false;
    }

    class recordSound implements Runnable {
        AudioRecord m_in_rec;
        AudioTrack m_out_trk;

        public short[] toShortArray(byte[] src) {

            int count = src.length >> 1;
            short[] dest = new short[count];
            for (int i = 0; i < count; i++) {
                dest[i] = (short) (src[i * 2 + 1] << 8 | src[2 * i + 0] & 0xff);
            }
            return dest;
        }

        public byte[] toByteArray(short[] src) {

            int count = src.length;
            byte[] dest = new byte[count << 1];
            for (int i = 0; i < count; i++) {
                dest[i * 2 + 0] = (byte) (src[i] >> 0);
                dest[i * 2 + 1] = (byte) (src[i] >> 8);
            }

            return dest;
        }

        public void toByteArray(byte[] dest, short[] src) {
            int count = src.length;
            if (dest.length / 2 < count)
                count = dest.length / 2;
            for (int i = 0; i < count; i++) {
                dest[i * 2 + 0] = (byte) (src[i] >> 0);
                dest[i * 2 + 1] = (byte) (src[i] >> 8);
            }
        }

        private void rampVolume(byte[] inBytes, boolean up) {
            short[] inShorts = toShortArray(inBytes);
            int frameCount = inShorts.length / 2;
            Log.d(TAG, "ramp volume count: " + frameCount);
            float vl = up ? 0.0f : 1.0f;
            float vlInc = (up ? 1.0f : -1.0f) / frameCount;
            for (int i = 0; i < frameCount; i++) {
                float a = vl * (float) inShorts[i * 2];
                inShorts[i * 2] = (short) a;
                inShorts[i * 2 + 1] = (short) a;
                vl += vlInc;
            }

            toByteArray(inBytes, inShorts);
        }

        public void run() {
            try {
                synchronized (this) {
                    int frequence = 44100;
                    int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
                    int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
                    int m_out_buf_size = AudioTrack.getMinBufferSize(frequence,
                            channelConfig, audioEncoding);
                    if (m_out_buf_size < 8192) {
                        Log.w(TAG, "Track buffer=" + m_out_buf_size + ", set to 8192");
                        m_out_buf_size = 8192;
                    }
                    m_out_trk = new AudioTrack(AudioManager.STREAM_MUSIC, frequence,
                            channelConfig,
                            audioEncoding, m_out_buf_size,
                            AudioTrack.MODE_STREAM);

                    Log.d(TAG, "set media.audio.hdmiin 1");
                    SystemProperties.set("media.audio.hdmiin", "1");
                    byte[] m_in_bytes;
                    int m_in_buf_size = AudioRecord.getMinBufferSize(frequence, channelConfig, audioEncoding);
                    Log.i(TAG, "out min: " + m_out_buf_size + ", in min: " + m_in_buf_size);
                    m_in_rec = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, frequence, channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT, m_in_buf_size);
                    //writeAudioDataToFile(m_in_buf_size,m_in_rec);
                   // RecordAudio(m_in_rec);
                    m_in_bytes = new byte[m_in_buf_size];
                    m_in_rec.startRecording();//
                    m_out_trk.play();

                    int readBytes = 0;

                    // discard 500ms audio data
                    int pre_read_count = 1 + (frequence * 2 * 2) / 2 / m_in_buf_size;
                    Log.d(TAG, "pre read count " + pre_read_count);
                    while (isRecording && pre_read_count-- >= 0)
                        readBytes = m_in_rec.read(m_in_bytes, 0, m_in_buf_size);
                    Log.d(TAG, "pre read end");
                    if (!isRecording) {
                        Log.d(TAG, "exit hdmiin audio");
                        m_in_rec.release();
                        m_in_rec = null;
                        Log.d(TAG, "set media.audio.hdmiin 0");
                        SystemProperties.set("media.audio.hdmiin", "0");
                        m_out_trk.release();
                        m_out_trk = null;
                        return;
                    }

                    // ramp volume for begin
                    rampVolume(m_in_bytes, true);

                    while (isRecording) {
                        if ((readBytes > 0) && (m_out_trk != null))
                            m_out_trk.write(m_in_bytes, 0, readBytes);
                        readBytes = m_in_rec.read(m_in_bytes, 0, m_in_buf_size);
                    }
                }

                Log.d(TAG, "exit hdmiin audio");
                m_in_rec.release();
                m_in_rec = null;
                Log.d(TAG, "set media.audio.hdmiin 0");
                SystemProperties.set("media.audio.hdmiin", "0");

                // ramp volume for end
                Log.d(TAG, "AudioTrack setVolume 0\n");
                m_out_trk.setVolume(0.0f);
                Log.d(TAG, "AudioTrack pause\n");
                m_out_trk.pause();
                SystemClock.sleep(50);
                Log.d(TAG, "AudioTrack stop\n");
                m_out_trk.release();
                m_out_trk = null;

            } catch (Exception e) {
                // TODO: handle exception
            }
        }
    }

    private void writeAudioDataToFile(int BufferElements2Rec,AudioRecord audioRecord) {
        // Write the output audio in byte

        String filePath  = Environment.getExternalStorageDirectory()+"/voice8K16bitmono.pcm";
       // mFileName += "/AudioRecording.3gp";
        //filePath += "/voice8K16bitmono.pcm";
        final File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toString() + "/misfit.mp3");

        short sData[] = new short[BufferElements2Rec];

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (isRecording) {
            // gets the voice output from microphone to byte format

            audioRecord.read(sData, 0, BufferElements2Rec);
            System.out.println("Short wirting to file" + sData.toString());
            try {
                // // writes the data to file from buffer
                // // stores the voice buffer
                byte bData[] = short2byte(sData);
                os.write(bData, 0, BufferElements2Rec * 2);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;

    }
    public void RecordAudio(AudioRecord audioRecord) {
        try {
            if(mRecorder==null){
                //mRecorder.release();
                mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
                mFileName += "/AudioRecording.3gp";

                // below method is used to initialize
                // the media recorder class
                mRecorder = new MediaRecorder();


                // below method is used to set the audio
                // source which we are using a mic.
                mRecorder.setAudioSource(audioRecord.getAudioSource());

                // below method is used to set
                // the output format of the audio.
                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

                // below method is used to set the
                // audio encoder for our recorded audio.
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

                // below method is used to set the
                // output file location for our recorded audio
                mRecorder.setOutputFile(mFileName);
                try {
                    // below method will prepare
                    // our audio recorder class
                    mRecorder.prepare();
                } catch (IOException e) {
                    Log.e("TAG", "prepare() failed");
                }
                // start method will start
                // the audio recording.
                Log.d("Media recorder", "Recording started");

                mRecorder.start();
                new CountDownTimer(5000, 1000) {

                    @Override
                    public void onTick(long l) {

                    }

                    @Override
                    public void onFinish() {
                        mRecorder.stop();

                        // below method will release
                        // the media recorder class.
                        mRecorder.release();
                        mRecorder = null;
                        Log.d("Media recorder", "Finished");

                    }
                }.start();
            }else {
                Log.d("Media recorder", "Not intialize");
            }
        } catch (Exception e) {
            Log.d("Error Line Number", Log.getStackTraceString(e));
        }
    }

}
