// Erik Icket, ON4PB - 2023
package common;

import java.util.logging.Logger;

public class Utils
{

    static final Logger logger = Logger.getLogger(Utils.class.getName());

    public static String printArray(byte[] array)
    // MSB first, LSB last
    {
        if (array == null)
        {
            return "";
        }

        String s = "length : " + array.length + " : ";
        for (int i = array.length - 1; i >= 0; i--)
        {
            s = s.concat(Byte.toString(array[i]));
        }
        return (s);
    }

    static public String bytesToHex(byte[] bytes)
    {
        final char[] hexArray =
        {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++)
        {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

}
