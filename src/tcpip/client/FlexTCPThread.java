// Erik Icket, ON4PB - 2023
package tcpip.client;

import common.PropertiesWrapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.logging.Logger;
import static main.MainController.slice0Frequency;
import static main.MainController.slice1Frequency;

public class FlexTCPThread extends Thread
{

    static final Logger logger = Logger.getLogger(FlexTCPThread.class.getName());
    public boolean stopRequest = false;

    private PropertiesWrapper propWrapper = new PropertiesWrapper();
    private TCPIPClient flexRadioTCPIPClient;
    private int lastKnownSliceFrequency = 0;

    public FlexTCPThread()
    {

        logger.fine("FlexTCPThread is started");

        flexRadioTCPIPClient = new TCPIPClient();

        if (flexRadioTCPIPClient.open(propWrapper.getStringProperty("host"), propWrapper.getIntProperty("port")))
        {
            try
            {
                flexRadioTCPIPClient.write("CD16|info");
                flexRadioTCPIPClient.write("CD21|sub slice all");
            }
            catch (IOException ex)
            {
                logger.severe("I/O error to Flex Radio : " + ex.getMessage());

                flexRadioTCPIPClient.close();
            }
        }
    }

    public void run()
    {
        while (!stopRequest)
        {
            try
            {
                logger.fine("start to process ... ");

                // process new RF_frequency readings from Flex to HRD
                String msg = "";
                do
                {
                    msg = flexRadioTCPIPClient.read();

                    // search for 
                    // S6ADFC05E|slice 0 RF_frequency=18.147180 wide=1 lock=0 
                    // S8F8A6915|slice 0 in_use=1 RF_frequency=14.181010 client_handle=0x5C92805B index_letter=A r....
                    if (msg.length() > 45)
                    {
                        logger.fine("read : " + msg);

                        // ignore the slice no
                        // if (msg.substring(9, 16).equals("|slice ") && msg.contains(" RF_frequency="))
                        if (msg.contains("|slice ") && msg.contains(" RF_frequency="))
                        {
                            logger.fine("slice msg : " + msg);
                            String[] msgParts = msg.split(" ");
                            for (int i = 0; i < msgParts.length; i++)
                            {
                                logger.fine(msgParts[i]);

                                if (msgParts[i].startsWith("RF_frequency="))
                                {
                                    // RF_frequency=18.147710
                                    String[] frequencyParts = msgParts[i].split("=");
                                    String frequency = frequencyParts[1];
                                    logger.fine("frequency : " + frequency);
                                    // remove the decimal point
                                    frequency = frequency.replaceAll("\\.", "");
                                    logger.fine("new frequency : " + frequency);

                                    try
                                    {
                                        int ifrequency = Integer.parseInt(frequency);
                                        switch (msgParts[1])
                                        {
                                            case "0":
                                                slice0Frequency = ifrequency;
                                                break;

                                            case "1":
                                                slice1Frequency = ifrequency;
                                                break;
                                        }
                                    }
                                    catch (NumberFormatException e2)
                                    {
                                        logger.severe("Failed to parse frequency : " + frequency);
                                    }
                                }
                            }
                        }
                    }
                }
                while (!msg.isEmpty());
                logger.fine("msg is now empty ... ");

                if (lastKnownSliceFrequency != slice0Frequency)
                {
                    lastKnownSliceFrequency = slice0Frequency;
                    setCenterPanAdapter(slice0Frequency);
                    logger.fine("Center done");
                }
            }
            catch (IOException e)
            {
                flexRadioTCPIPClient.close();
            }
        }
        flexRadioTCPIPClient.close();
    }

    public void setCenterPanAdapter(long requestedFrequency)
    {
        try
        {
            // process HRD requests to set the Flex frequency
            // C12|slice t 1 14.2055
            BigDecimal b = new BigDecimal(requestedFrequency);

            // add the MHz decimal point with movePointLeft
            // C12|slice t 1 14.2055
            String cmd = "CD30|display pan s 0x40000000 center=" + b.movePointLeft(6);
            logger.fine("radio cmd : |" + cmd + '|');

            // write can throw IOException
            flexRadioTCPIPClient.write(cmd);
        }
        catch (IOException e)
        {
            flexRadioTCPIPClient.close();
        }
    }

    static public void delay(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
        }
    }
}
