// from https://ptolemy.berkeley.edu/eecs20/week12/implementation.html
package filters;

public class FirFilter
{

    // the nrOfTaps = number of taps = number of coefficients
    private int nrOfTaps;
    private double[] delayLine;
    private double[] coefficients;
    private int count = 0;

    public FirFilter(double[] coefficients)
    {
        nrOfTaps = coefficients.length;
        this.coefficients = coefficients;
        delayLine = new double[nrOfTaps];
    }

    double getOutputSample(double inputSample)
    {
        // circular buffering
        // the count member is used to keep track of where each new input sample should go.                
        delayLine[count] = inputSample;
        double result = 0.0;
        int index = count;
        for (int i = 0; i < nrOfTaps; i++)
        {
            // The most recent sample is at count in the delayLine. The second most recent is at count − 1, or if that is negative, at nrOfTaps − 1.
            // Each multiplication is made by incrementing the filter tap number (from 0 onwards) and decrementing the index in the delayLine
            result += coefficients[i] * delayLine[index--];

            if (index < 0)
            {
                index = nrOfTaps - 1;
            }
        }

        if (++count >= nrOfTaps)
        {
            count = 0;
        }
        return result;
    }
}
