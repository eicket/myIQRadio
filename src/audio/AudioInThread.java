// Erik Icket, ON4PB - 2023
package audio;

import static common.Utils.bytesToHex;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import fft.Complex;
import static fft.FFT.fft;
import static common.Constants.FFTSIZE;
import static common.Constants.NR_OF_SPECTRUM_POINTS;
import static common.Constants.SPECTRUM_DECIMATION_FACTOR;
import static main.MainController.invertIQ;
import static main.MainController.lbdDemodulatorInput;
import static main.MainController.lbdSpectrum;

public class AudioInThread extends Thread
{

    static final Logger logger = Logger.getLogger(audio.AudioInThread.class.getName());
    public boolean stopRequest = false;

    private int nBitsPerSample = 16;
    private boolean bBigEndian = true;
    private int nChannels = 2;
    private int nFrameSize = (nBitsPerSample / 8) * nChannels;
    private AudioFormat.Encoding encoding = AudioFormat.Encoding.PCM_SIGNED;
    private TargetDataLine targetDataLine;
    private AudioFormat audioFormat;

    public AudioInThread(String device, int sampleRate)
    {
        logger.fine("Audio in thread is started for device : " + device + ", sample rate : " + sampleRate);

        // find all audio mixers       
        Mixer.Info[] aInfos = AudioSystem.getMixerInfo();

        boolean foundAudioIn = false;

        for (int i = 0; i < aInfos.length; i++)
        {
            Mixer mixer = AudioSystem.getMixer(aInfos[i]);
            logger.fine("next mixer : " + mixer.getMixerInfo().toString());

            // test for a target == audio in
            if (mixer.isLineSupported(new Line.Info(TargetDataLine.class)))
            {
                logger.fine("audio in - target : " + mixer.getMixerInfo().getName());

                if ((mixer.getMixerInfo().getName()).equalsIgnoreCase(device))
                {
                    logger.fine("Found received AudioIn device : " + device);

                    foundAudioIn = true;

                    // For PCM the sample rate and the frame rate are the same since a frame consists of a a sample from each channel
                    audioFormat = new AudioFormat(encoding, sampleRate, nBitsPerSample, nChannels, nFrameSize, sampleRate, bBigEndian);

                    try
                    {
                        // Obtains a target dataIn line that can be used for recording audio dataIn 
                        targetDataLine = (TargetDataLine) AudioSystem.getTargetDataLine(audioFormat, mixer.getMixerInfo());
                        logger.fine("Target data line created");
                    }
                    catch (Exception ex)
                    {
                        logger.severe("Can't get TargetDataLine : " + ex.getMessage());
                    }

                    break;
                }
            }

        }
        if (!foundAudioIn)
        {
            logger.info("No Audio In found");
            return;
        }

        try
        {
            targetDataLine.open(audioFormat);
        }
        catch (LineUnavailableException ex)
        {
            logger.severe("LineUnavailableException : " + ex.getMessage());
        }
        targetDataLine.start();

        if (targetDataLine.isOpen())
        {
            logger.info("AudioInThread started with sample rate : " + sampleRate + ", buffer size : " + targetDataLine.getBufferSize() + ", bin size : " + (float) sampleRate / FFTSIZE + " hz, sample period : " + (float) FFTSIZE / sampleRate + " secs");
        }
        else
        {
            logger.severe("targetDataLine is not open");
        }
    }

    public void run()
    {

        byte[] bAudioIn = new byte[FFTSIZE * 4];
        Complex[] complexIn = new Complex[FFTSIZE];
        Complex[] complexOut = new Complex[FFTSIZE];
        Complex[] swappedComplexOut = new Complex[FFTSIZE];

        long start = System.currentTimeMillis();
        int nrOfSamples = 0;

        while (!stopRequest)
        {
            int audioInReadInBytes = targetDataLine.read(bAudioIn, 0, FFTSIZE * 4);

            // make a new object for every enqueue .. otherwise values are overwritten and corrupted when polling
            int[][] iIQAudio = new int[FFTSIZE][2];
            nrOfSamples++;

            logger.fine("Read : " + audioInReadInBytes + ", dump : " + bytesToHex(bAudioIn));
            logger.fine("Read bytes : " + audioInReadInBytes + ", available for read : " + targetDataLine.available() + ", internal buffer size : " + targetDataLine.getBufferSize()
                    + ", free buffer : " + (targetDataLine.getBufferSize() - targetDataLine.available()));

            if (targetDataLine.available() == targetDataLine.getBufferSize())
            {
                logger.severe("Audio in overrun");
            }

            for (int i = 0; i < audioInReadInBytes; i = i + 4)
            {
                // Java shorts (16 bit) are in Big Endian;
                // audio stream is big endian, highest comes out first
                // & 0xFF widens the bytee to an int, and from then onwards all is done in short (16 bit)
                short ub1 = (short) (bAudioIn[i] & 0xFF);
                short ub2 = (short) (bAudioIn[i + 1] & 0xFF);
                short left = (short) ((ub1 << 8) + ub2);

                short ub3 = (short) (bAudioIn[i + 2] & 0xFF);
                short ub4 = (short) (bAudioIn[i + 3] & 0xFF);
                short right = (short) ((ub3 << 8) + ub4);

                logger.fine("Read from audio, shorts (hi, lo) : " + ub1 + ", " + ub2 + ", left : " + left + ", bytes : " + ub3 + ", " + ub4 + ", right : " + right);
                if ((left == Short.MAX_VALUE) || (left == Short.MIN_VALUE))
                {
                    logger.severe("max left on input");
                }

                if ((right == Short.MAX_VALUE) || (right == Short.MIN_VALUE))
                {
                    logger.severe("max right on input");
                }

                if (invertIQ)
                {
                    short temp = left;
                    left = right;
                    right = temp;
                }

                iIQAudio[i / 4][0] = right;
                iIQAudio[i / 4][1] = left;

                complexIn[i / 4] = new Complex(right, left);
            }
            lbdDemodulatorInput.add(iIQAudio);

            complexOut = fft(complexIn);

            // swap the lower spectrum to high, and higher spectrun to low
            // see fig 4 in QEX article
            // bins 0 - 2047
            for (int i = 0; i < FFTSIZE / 2; i++)
            {
                swappedComplexOut[i + FFTSIZE / 2] = complexOut[i];
            }

            // bins 2048 - 4097
            for (int i = FFTSIZE / 2; i < FFTSIZE; i++)
            {
                swappedComplexOut[i - FFTSIZE / 2] = complexOut[i];
            }

            double[] spectrum = new double[FFTSIZE];
            for (int i = 0; i < FFTSIZE; i++)
            {
                spectrum[i] = 10 * Math.log10(Math.pow(swappedComplexOut[i].abs(), 2));
            }

            // decimate, so that the linechart is not too large
            // best decimation is simple averaging  -- all values are positive anyhow
            double[] decimateOut = new double[NR_OF_SPECTRUM_POINTS];
            double average = 0;
            for (int i = 0; i < FFTSIZE; i++)
            {
                if (i % SPECTRUM_DECIMATION_FACTOR == 7)
                {
                    decimateOut[i / SPECTRUM_DECIMATION_FACTOR] = average / SPECTRUM_DECIMATION_FACTOR;
                    average = 0;
                }
                average += spectrum[i];
            }
            lbdSpectrum.add(decimateOut);
        }

        long stop = System.currentTimeMillis();
        float elapsed = (float) ((stop - start) / 1000);
        float rate = nrOfSamples * FFTSIZE / elapsed;

        targetDataLine.stop();

        targetDataLine.close();

        logger.info("Exiting AudioInThread, sample rate : " + rate);
    }
}
