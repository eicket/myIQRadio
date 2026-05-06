// Erik Icket, ON4PB - 2026
package common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Utils
{

    static final Logger logger = LogManager.getLogger(Utils.class.getName());

    static public void dumpDouble(double value)
    {
        long bits = Double.doubleToLongBits(value);

        int sign = (int) ((bits >>> 63) & 0x1);
        int exponent = (int) ((bits >>> 52) & 0x7FFL);
        long mantissa = bits & 0x000FFFFFFFFFFFFFL;

        logger.info(String.format("Value:    %f", value));
        logger.info(String.format("Sign:     %d", sign));
        logger.info(String.format("Exponent: %d (biased), %d (unbiased)", exponent, exponent - 1023));
        logger.info(String.format("Mantissa: %d (0x%013X)", mantissa, mantissa));
    }
}
