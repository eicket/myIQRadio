// Erik Icket, ON4PB - 2026
package headless;

import udp.VITA49InThread;

public class DAXIQ
{

    private VITA49InThread vita49InThread = null;

    public static void main(String[] args) throws Exception
    {
        new DAXIQ().start();
    }

    private void start()
    {
        vita49InThread = new VITA49InThread(true);
        if (vita49InThread.connected)
        {
            vita49InThread.start();
        }
    }
}
