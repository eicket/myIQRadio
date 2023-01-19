// Erik Icket, ON4PB - 2023
package audio;

import common.Constants;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.TimeUnit;
import static common.Constants.SAMPLE_AUDIO_OUT_RATE;
import java.util.Arrays;
import static main.MainController.lbdAudioOut;
import static main.MainController.volume;
import static tcpip.client.FlexTCPThread.delay;

public class AudioOutThread extends Thread
{

    static final Logger logger = Logger.getLogger(audio.AudioOutThread.class.getName());
    public boolean stopRequest = false;

    private int nBitsPerSample = 16;
    private boolean bBigEndian = true;
    private int nChannels = 1;
    private int nFrameSize = (nBitsPerSample / 8) * nChannels;
    private AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;

    private SourceDataLine sourceDataLine;
    private AudioFormat audioFormat;

    public AudioOutThread(String device)
    {
        logger.fine("Audio out thread is started");

        // find all audio mixers       
        Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

        boolean foundAudioOut = false;

        for (int i = 0; i < aInfos.length; i++)
        {
            Mixer mixer = AudioSystem.getMixer(aInfos[i]);
            logger.fine("next mixer : " + mixer.getMixerInfo().toString());

            // test for a target == audio in
            if (mixer.isLineSupported(new Line.Info(SourceDataLine.class)))
            {
                logger.fine("audio out - target : " + mixer.getMixerInfo().getName());

                if ((mixer.getMixerInfo().getName()).equalsIgnoreCase(device))
                {
                    logger.fine("Found received AudioOut device : " + device);

                    foundAudioOut = true;
                    audioFormat = new AudioFormat(encoding, Constants.SAMPLE_AUDIO_OUT_RATE, nBitsPerSample, nChannels, nFrameSize, Constants.SAMPLE_AUDIO_OUT_RATE, bBigEndian);
                    try
                    {
                        // Obtains a target data line that can be used for playing back audio data 
                        sourceDataLine = (SourceDataLine) AudioSystem.getSourceDataLine(audioFormat, mixer.getMixerInfo());
                    }
                    catch (Exception ex)
                    {
                        logger.severe("Can't get SourceDataLine : " + ex.getMessage());
                    }

                    break;
                }
            }

        }
        if (!foundAudioOut)
        {
            logger.info("No Audio Out found");
            return;
        }

        try
        {
            sourceDataLine.open(audioFormat);
        }
        catch (LineUnavailableException ex)
        {
            logger.severe("LineUnavailableException : " + ex.getMessage());
        }
        sourceDataLine.start();

        if (sourceDataLine.isOpen())
        {
            logger.info("AudioOutThread started with sample rate : " + SAMPLE_AUDIO_OUT_RATE + ", buffer size : " + sourceDataLine.getBufferSize());
        }
        else
        {
            logger.severe("sourceDataLine is not open");
        }
    }

    public void run()
    {
        int[] audioOutFromQueue;
        // delay 1 sec, to prevent underrun .. waiting for the filters to fill up
        delay(1000);

        long start = System.currentTimeMillis();
        int nrOfSamples = 0;
        int len = 0;

        while (!stopRequest)
        {
            try
            {
                logger.fine("Audio out free buffer : " + (sourceDataLine.getBufferSize() - sourceDataLine.available()));
                if (sourceDataLine.available() == sourceDataLine.getBufferSize())
                {
                    logger.severe("Audio out underrun");
                }

                audioOutFromQueue = (int[]) lbdAudioOut.poll(50, TimeUnit.MILLISECONDS);
                if (audioOutFromQueue != null)
                {
                    nrOfSamples++;
                    len = audioOutFromQueue.length;

                    logger.fine("Write : " + len + ", dump : " + Arrays.toString(audioOutFromQueue));
                    logger.fine("New audio out buffer : " + audioOutFromQueue.length + ", available for write : " + sourceDataLine.available() + ", internal buffer size : " + sourceDataLine.getBufferSize());
                    byte[] audioOutToSoundCard = new byte[2 * audioOutFromQueue.length];

                    // output to the soundcard                     
                    for (int i = 0; i < audioOutFromQueue.length; i++)
                    {
                        short out = (short) (audioOutFromQueue[i] * 10 * volume);
                        if ((out == Short.MAX_VALUE) || (out == Short.MIN_VALUE))
                        {
                            logger.severe("max");
                        }
                        byte hi = (byte) (out >> 8);
                        byte lo = (byte) (out & 0x00FF);

                        audioOutToSoundCard[2 * i] = hi;
                        audioOutToSoundCard[2 * i + 1] = lo;
                        logger.fine("Audio out, out : " + out + ", hi : " + hi + ", lo : " + lo);
                    }

                    int nAudioOut = sourceDataLine.write(audioOutToSoundCard, 0, audioOutToSoundCard.length);
                    logger.fine("Number of bytes written : " + nAudioOut + ", available for write : " + sourceDataLine.available());
                }
            }
            catch (InterruptedException ex)
            {
                logger.severe("exception : " + ex.getMessage());
            }
        }

        long stop = System.currentTimeMillis();
        float elapsed = (float) ((stop - start) / 1000);
        float rate = nrOfSamples * len / elapsed;

        sourceDataLine.close();
        sourceDataLine.stop();

        logger.info("AudioOut thread exit, audioOutFromQueue size :  " + lbdAudioOut.size() + ", sample rate : " + rate);
    }

}
