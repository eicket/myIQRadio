// see https://dspguru.com/dsp/reference/raised-cosine-and-root-raised-cosine-formulas/
// point 1.2 
package filters;

public class MakeRaisedCosine
{

    // alpha is the rolloff
    public static double[] makeRaisedCosine(double sampleRate, double frequency, double alpha, int nrOfTaps)
    {
        int M = nrOfTaps - 1;
        double[] coefficients = new double[nrOfTaps];
        double Fc = frequency / sampleRate;

        double sumOfSquares = 0;
        double[] tempCoeffs = new double[nrOfTaps];
        int limit = (int) (0.5 / (alpha * Fc));
        for (int i = 0; i <= M; i++)
        {
            double sinc = (Math.sin(2 * Math.PI * Fc * (i - M / 2))) / (i - M / 2);
            double cos = Math.cos(alpha * Math.PI * Fc * (i - M / 2)) / (1 - (Math.pow((2 * alpha * Fc * (i - M / 2)), 2)));

            if (i == M / 2)
            {
                tempCoeffs[i] = 2 * Math.PI * Fc * cos;
            }
            else
            {
                tempCoeffs[i] = sinc * cos;
            }

            // Care because ( 1 - ( 2 * Math.pow((alpha * Fc * (i - nrOfTaps/2)),2))) is zero for 
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
