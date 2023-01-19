// reworked from https://github.com/ac2cz/SDR
// no licence test
package dsp;

import java.util.logging.Logger;

public class HilbertTransform
{

    static final Logger logger = Logger.getLogger(HilbertTransform.class.getName());
    public double[] coefficients;

    private double[] delayLine;
    private double gain = 1;
    // M is only used in the filter proc !
    // M is the filter order = nrOfTaps - 1
    private int M;

    public HilbertTransform(double sampleRate, int nrOfTaps)
    {
        init(sampleRate, nrOfTaps);
        M = nrOfTaps - 1;
        delayLine = new double[nrOfTaps];
    }

    private void init(double sampleRate, int nrOfTaps)
    {
        double[] tempCoefficients = new double[nrOfTaps];
        double sumOfSquares = 0;

        for (int n = 0; n < nrOfTaps; n++)
        {
            if (n == nrOfTaps / 2)
            {
                tempCoefficients[n] = 0;
            }
            else
            {
                tempCoefficients[n] = sampleRate / (Math.PI * (n - nrOfTaps / 2)) * (1 - Math.cos(Math.PI * (n - nrOfTaps / 2)));
            }
            sumOfSquares += tempCoefficients[n] * tempCoefficients[n];
        }
        gain = Math.sqrt(sumOfSquares);

        logger.fine("Hilbert transform gain : " + gain);
        // flip
        coefficients = new double[nrOfTaps];
        for (int i = 0; i < tempCoefficients.length; i++)
        {
            coefficients[i] = tempCoefficients[tempCoefficients.length - i - 1] / gain;
        }
    }

    public double filter(double in)
    {
        double sum;
        int i;
        for (i = 0; i < M; i++)
        {
            delayLine[i] = delayLine[i + 1];
        }
        delayLine[M] = in;
        sum = 0.0;
        for (i = 0; i <= M; i++)
        {
            sum += (coefficients[i] * delayLine[i]);
        }
        return sum;
    }
}
