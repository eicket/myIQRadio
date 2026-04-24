package tcpip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TCP
{

    static final Logger logger = LogManager.getLogger(TCP.class.getName());

    Socket socket;
    PrintWriter out;
    BufferedReader in;

    public boolean open(String host, int port)
    {
        try
        {
            // set timeout for the connect, otherwise javaFX thread blocks ...
            socket = new Socket();

            logger.debug("Trying to open TCPIP socket to host : " + host + ", port : " + port);

            socket.connect(new InetSocketAddress(host, port), 100);
            socket.setSoTimeout(5); // timeout for read operation in msecs, timeout is not for connect !

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            logger.info("TCPIP socket open to host : " + host + ", port : " + port);
            return true;
        }
        catch (Exception ex)
        {
            logger.info("Exception in open TCPIP socket : " + ex.getMessage());
            return false;
        }
    }

    // throws IOException when remote server disconnects or disappears
    public String read() throws IOException
    {
        try
        {
            String inputLine = in.readLine();
            if (inputLine != null)
            {
                logger.debug("Received before : " + inputLine.length() + ", " + inputLine);
                // erases all the ASCII control characters.
                inputLine = inputLine.replaceAll("\\P{Print}", "");
                logger.debug("Received : " + inputLine);

                return inputLine;
            }
            else
            {
                return "";
            }
        }
        catch (SocketTimeoutException ex)
        {
            logger.debug("All read");
            return "";
        }
    }

    // throws IOException when remote server produces checkError
    public void write(String s) throws IOException
    {
        // println will add a mandatory line terminator 
        out.println(s);
        logger.debug("-> " + s);

        if (out.checkError())
        {
            logger.fatal("Error writing to TCPIP server");
            throw new IOException();
        }
    }

    public void close()
    {
        try
        {
            if (socket != null)
            {
                socket.close();

                // logger.info("TCPIP socket closed to " + socket.getInetAddress().getCanonicalHostName());
                logger.info("TCPIP socket closed to " + socket.getInetAddress());
            }
        }
        catch (IOException ex)
        {
            logger.info("Error closing connection");
        }
    }
}
