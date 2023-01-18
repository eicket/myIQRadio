// Erik Icket, ON4PB - 2023
package common;

public class Constants
{

    // freq resolution = bin size = sample rate / FFT size
    // max frequency in spectrum = sample rate / 2
    public static final int FFTSIZE = 4096;
    public static final int SPECTRUM_DECIMATION_FACTOR = 8;
    public static final int NR_OF_SPECTRUM_POINTS = FFTSIZE / SPECTRUM_DECIMATION_FACTOR;
    public static final int NR_OF_WATERFALL_LINES = 100;
    public static final int YAXIS_MAX = 200;
    public static final int SAMPLE_AUDIO_OUT_RATE = 12000;

    // DEMODULATION
    public static final int LSB = 1;
    public static final int USB = 2;
    public static final int FM = 3;
    public static final int AM = 4;

    // Audio filter limits
    public static final int FILTER_LIMIT_LOW = 100;
    public static final int FILTER_LIMIT_HIGH = 10000;
    public static final int FILTER_DEFAULT = 2500;
    public static final int FILTER_TAPS_LOW = 2;
    public static final int FILTER_TAPS_HIGH = 2048;
}
