// Erik Icket, ON4PB - 2023
package dsp;

import static common.Constants.AM;
import static common.Constants.FFTSIZE;
import java.util.logging.Logger;
import static common.Constants.FM;
import static common.Constants.LSB;
import static common.Constants.SAMPLE_AUDIO_OUT_RATE;
import static common.Constants.USB;
import java.util.concurrent.TimeUnit;
import static main.MainController.demodulationMode;
import static main.MainController.filterCutoff;
import static main.MainController.lbdAudioOut;
import static main.MainController.lbdDemodulatorInput;
import static main.MainController.rxGain;
import org.apache.commons.math3.util.FastMath;

public class Demodulator extends Thread
{

    static final Logger logger = Logger.getLogger(Demodulator.class.getName());

    public boolean stopRequest = false;

    private int sampleRate = 0;
    private int audioDecimationRate = 0;

    public Demodulator(int sampleRate, int audioDecimationRate)
    {
        this.sampleRate = sampleRate;
        this.audioDecimationRate = audioDecimationRate;
    }

    public void run()
    {
        // More nrOfPPFTaps mean higher frequency resolution, which in turn means narrower filters and/or steeper roll‐offs.
        int nrOfPPFTaps = 1024;
        int nrOfHilbertTaps = 255;
        int nrOfDelayTaps = (nrOfHilbertTaps - 1) / 2;
        int lastKnownCutoff = filterCutoff;
        double alpha = 0.25;

        int[][] IQAudioIn;

        PolyPhaseFilter polyPhaseFilterI = new PolyPhaseFilter(sampleRate, filterCutoff, alpha, audioDecimationRate, nrOfPPFTaps);
        PolyPhaseFilter polyPhaseFilterQ = new PolyPhaseFilter(sampleRate, filterCutoff, alpha, audioDecimationRate, nrOfPPFTaps);
        HilbertTransform hilbertTransform = new HilbertTransform(SAMPLE_AUDIO_OUT_RATE, nrOfHilbertTaps);
        Delay delay = new Delay(nrOfDelayTaps);

        logger.info("Demodulator started");

        while (!stopRequest)
        {
            try
            {
                if (lastKnownCutoff != filterCutoff)
                {
                    lastKnownCutoff = filterCutoff;
                    logger.info("Rebuilding filter coeffs with sample rate : " + sampleRate + ", filter cutoff : " + filterCutoff + ", audio decimation rate : " + audioDecimationRate);

                    polyPhaseFilterI = new PolyPhaseFilter(sampleRate, filterCutoff, alpha, audioDecimationRate, nrOfPPFTaps);
                    polyPhaseFilterQ = new PolyPhaseFilter(sampleRate, filterCutoff, alpha, audioDecimationRate, nrOfPPFTaps);
                }

                IQAudioIn = (int[][]) lbdDemodulatorInput.poll(50, TimeUnit.MILLISECONDS);

                if (IQAudioIn != null)
                {
                    logger.fine("new IQAudio packet with size : " + IQAudioIn.length + ", queue : " + lbdDemodulatorInput.size());

                    // all arrays are created here, because decimationRate can change when this thread runs 
                    double[] PPFilterInI = new double[audioDecimationRate];
                    double[] PPFilterInQ = new double[audioDecimationRate];
                    double[] PPFilterOutI = new double[FFTSIZE / audioDecimationRate];
                    double[] PPFilterOutQ = new double[FFTSIZE / audioDecimationRate];
                    int[] audioOut = new int[FFTSIZE / audioDecimationRate];

                    for (int i = 0; i < FFTSIZE; i++)
                    {
                        int subFilterSwitch = i % audioDecimationRate;

                        PPFilterInI[subFilterSwitch] = ((double) IQAudioIn[i][0]) * rxGain;
                        PPFilterInQ[subFilterSwitch] = ((double) IQAudioIn[i][1]) * rxGain;

                        // filter when the subfilters are filled in 
                        if (subFilterSwitch == (audioDecimationRate - 1))
                        {
                            PPFilterOutI[i / audioDecimationRate] = polyPhaseFilterI.filter(PPFilterInI);
                            PPFilterOutQ[i / audioDecimationRate] = polyPhaseFilterQ.filter(PPFilterInQ);
                        }
                    }

                    for (int i = 0; i < audioOut.length; i++)
                    {
                        switch (demodulationMode)
                        {
                            case LSB:
                                audioOut[i] = (int) (delay.delay(PPFilterOutI[i]) + hilbertTransform.filter(PPFilterOutQ[i]));
                                break;

                            case USB:
                                audioOut[i] = (int) (delay.delay(PPFilterOutI[i]) - hilbertTransform.filter(PPFilterOutQ[i]));
                                break;

                            case FM:
                                audioOut[i] = (int) (100 * (fmDemodulate(PPFilterOutI[i], PPFilterOutQ[i])));                              
                                break;

                            case AM:
                                audioOut[i] = (int) Math.sqrt(Math.pow(PPFilterOutI[i], 2) + Math.pow(PPFilterOutQ[i], 2));
                                break;
                        }
                    }
                    lbdAudioOut.add(audioOut);
                }
            }
            catch (InterruptedException ex)
            {
                logger.severe("exception : " + ex.getMessage());
            }
        }
        logger.info("Exiting DemodulatorThread, lbdDemodulatorInput queue size : " + lbdDemodulatorInput.size());
    }

    private double previousI = 0.0f;
    private double previousQ = 0.0f;

    public double fmDemodulate(double currentI, double currentQ)
    {
        double inphase = currentI * previousI + currentQ * previousQ;
        double quadrature = currentQ * previousI - currentI * previousQ;

        double angle = 0.0f;

        // check for divide by zero
        if (inphase != 0)
        {
            angle = FastMath.atan(quadrature / inphase);
        }

        previousI = currentI;
        previousQ = currentQ;

        return angle;
    }
}
