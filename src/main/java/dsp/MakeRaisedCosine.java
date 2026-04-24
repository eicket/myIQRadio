// see https://dspguru.com/dsp/reference/raised-cosine-and-root-raised-cosine-formulas/
// point 1.2 
package dsp;

public class MakeRaisedCosine
{

    // alpha is the rolloff
    // M is the filter order = nrOfTaps - 1
    public static double[] makeRaisedCosine(double sampleRate, double frequency, double alpha, int nrOfTaps)
    {
        int M = nrOfTaps - 1;
        double[] coefficients = new double[nrOfTaps];
        double normalizedFrequency = frequency / sampleRate;

        double sumOfSquares = 0;
        double[] tempCoeffs = new double[nrOfTaps];
        int limit = (int) (0.5 / (alpha * normalizedFrequency));
        for (int i = 0; i <= M; i++)
        {
            double sinc = (Math.sin(2 * Math.PI * normalizedFrequency * (i - M / 2))) / (i - M / 2);
            double cos = Math.cos(alpha * Math.PI * normalizedFrequency * (i - M / 2)) / (1 - (Math.pow((2 * alpha * normalizedFrequency * (i - M / 2)), 2)));

            if (i == M / 2)
            {
                tempCoeffs[i] = 2 * Math.PI * normalizedFrequency * cos;
            }
            else
            {
                tempCoeffs[i] = sinc * cos;
            }

            if ((i - M / 2) == limit || (i - M / 2) == -limit)
            {
                tempCoeffs[i] = 0.25 * Math.PI * sinc;
            }

            sumOfSquares += tempCoeffs[i] * tempCoeffs[i];
        }
        double gain = Math.sqrt(sumOfSquares);
        for (int i = 0; i < tempCoeffs.length; i++)
        {
            coefficients[i] = tempCoeffs[tempCoeffs.length - i - 1] / gain;
        }
        return coefficients;
    }
}
