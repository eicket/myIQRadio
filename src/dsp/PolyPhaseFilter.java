// Erik Icket, ON4PB - 2023
package dsp;

import java.util.logging.Logger;
import static dsp.MakeRaisedCosine.makeRaisedCosine;

public class PolyPhaseFilter
{
    static final Logger logger = Logger.getLogger(PolyPhaseFilter.class.getName());

    public double[] coefficients;

    private FirFilter[] subFilters;

    public PolyPhaseFilter(double sampleRate, double cutoffFrequency, double alpha, int decimationRate, int nrOfTaps)
    {
        coefficients = makeRaisedCosine(sampleRate, cutoffFrequency, alpha, nrOfTaps);
        makeSubFilters(coefficients, decimationRate);
    }

    private void makeSubFilters(double[] coefficients, int decimationRate)
    {
        subFilters = new FirFilter[decimationRate];
        int subFilterSwitch = decimationRate - 1;

        for (int subFilter = 0; subFilter < decimationRate; subFilter++)
        {
            double[] taps = new double[coefficients.length / decimationRate];
            for (int i = 0; i < coefficients.length / decimationRate; i++)
            {
                taps[i] = (coefficients[subFilterSwitch + i * decimationRate]);
                logger.fine("subFilter : " + subFilter + ", coefficient : " + (subFilterSwitch + i * decimationRate) + ", subFilterSwitch : " + subFilterSwitch + ", i : " + i);
            }

            subFilters[subFilter] = new FirFilter(taps);

            subFilterSwitch = subFilterSwitch - 1;
        }
    }
   
    public double filter(double[] in)
    {
        double sum = 0;
        for (int i = 0; i < in.length; i++)
        {
            sum += subFilters[i].getOutputSample(in[i]);           
        }
        return sum;
    }
}
