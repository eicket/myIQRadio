// Erik Icket, ON4PB - 2023
package dsp;

public class Delay
{
    double[] delayLine;  
    int nrOfTaps; 

    public Delay(int nrOfTaps)
    {
        delayLine = new double[nrOfTaps + 1];
        this.nrOfTaps = nrOfTaps;
    }

    public double delay(double inputSample)
    {
        for (int i = 0; i < nrOfTaps; i++)
        {
            delayLine[i] = delayLine[i + 1];
        }
        delayLine[nrOfTaps] = inputSample;
        return delayLine[0];
    }
}
