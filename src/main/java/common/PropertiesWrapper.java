// Erik Icket, ON4PB - 2023
package common;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;

public class PropertiesWrapper
{

    static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(PropertiesWrapper.class.getName());
    private Properties prop;

    public PropertiesWrapper()
    {

        // load the properties
        prop = new Properties();

        try
        {
            FileInputStream fis = new FileInputStream("DSP.properties");
            prop.load(fis);
        }
        catch (IOException ex)
        {
            reset();
        }

        logger.debug("properties : " + prop.toString());
    }

    public void reset()
    {
        prop.clear();
        setProperty("IQIn", "1");
        setProperty("AudioOut", "White Speakers (2- USB Audio Device)");
        setProperty("port", "4992");
        setProperty("host", "192.168.129.53");
    }

    public void setProperty(String name, String value)
    {
        prop.setProperty(name, value);
        try
        {
            //save properties to project root folder
            prop.store(new FileOutputStream("DSP.properties"), null);
        }
        catch (IOException ex)
        {
            logger.fatal("Property file can not be saved");
            return;
        }
    }

    public String getStringProperty(String name)
    {
        return prop.getProperty(name);
    }

    public int getIntProperty(String name)
    {
        int i;
        try
        {
            i = Integer.parseInt(prop.getProperty(name));

        }
        catch (NumberFormatException e2)
        {
            return -1;
        }
        return i;
    }
}
