package test;

import common.Constants;
import static common.Constants.FFTSIZE;
import common.PropertiesWrapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import static main.MainController.requestedFrequency;
import static main.MainController.setRequestedFrequency;
import static main.MainController.vfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tcpip.TCP;
import static utils.utils.delay;

public class DAX
{

    static final Logger logger = LogManager.getLogger(DAX.class.getName());

    public boolean stopRequest = false;

    PropertiesWrapper propWrapper = new PropertiesWrapper();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS 'UTC'").withZone(ZoneOffset.UTC);

    public boolean connected = false;
    private final int udpPort = 4050;

    DatagramSocket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    TCP tcp;

    private String clientId = "";
    private String sStreamId;
    private String panId;
    private int streamId = -1;
    private int seq = 1;

    SourceDataLine audioOut;

    public static void main(String[] args) throws Exception
    {
        new DAX().start();
    }

    public void start()
    {

        try
        {
            socket = new DatagramSocket(udpPort);
            socket.setSoTimeout(100);
            // to prevent missiong packets
            socket.setReceiveBufferSize(4 * 1024 * 1024);
            logger.info("Listening UDP socket created with default time out of : " + socket.getSoTimeout() + " and buffer size : " + socket.getReceiveBufferSize() + " on port : " + socket.getLocalPort());
        }
        catch (IOException ex)
        {
            logger.fatal("Cannot create DatagramSocket : " + ex.getMessage());

            connected = false;
            return;
        }

        tcp = new TCP();
        if (tcp.open(propWrapper.getStringProperty("host"), propWrapper.getIntProperty("port")))
        {

            delay(1000);

            sendRaw("client udpport " + Constants.LOCAL_UDP_PORT);
            sendRaw("sub client all");
            // to know the sample_rate
            sendRaw("sub slice all");
            sendRaw("client bind client_id=" + clientId);
            sendRaw("stream create type=dax_rx dax_channel=1");
            //    sendRaw("stream 0x" + sStreamId + " type=dax_rx dax_channel=1 slice=0 dax_clients=0 client_handle=<handle> ip=<ip>");

            connected = true;

            try
            {
                /*
                AudioFormat format = new AudioFormat(
                        48000, // matches FlexRadio DAX rate
                        16,
                        1, // mono for WSJT-X
                        true, // signed
                        true // little-endian
                );*/
                AudioFormat format = new AudioFormat(
                        24000, // matches FlexRadio DAX rate
                        16,
                        1,
                        true, // signed
                        false // not big endian
                );

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                audioOut = (SourceDataLine) AudioSystem.getLine(info);
                audioOut.open(format, 24000 * 2 * 1);  // 1 second buffer
                audioOut.start();
            }
            catch (LineUnavailableException ex)
            {
                logger.error("Cannot open SourceDataLine : " + ex.getMessage());

                tcp.close();
                connected = false;
                return;
            }
        }

        logger.info("VITA49InThread is started");

        run();
    }

    public void run()
    {
        byte[] buffer = new byte[8192];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        int lastSeq = -1;
        String sPktStreamId = "";
           byte[] out ;

        while (!stopRequest)
        {
            try
            {

                try
                {
                    socket.receive(packet);

                    byte[] data = packet.getData();
                    int length = packet.getLength();
                    int offset = 0;

                    // ---- Word 0: packet type, indicators, sequence, 4 bytes
                    int word0 = readInt(data, offset);
                    offset += 4;
                    int hasStreamId = (word0 >> 27) & 0x1;
                    int hasClassId = (word0 >> 26) & 0x1;
                    int pktSeq = (word0 >> 16) & 0xF;
                    int tsiType = (word0 >> 22) & 0x3;   // 0=none,1=UTC,2=GPS,3=other
                    int tsfType = (word0 >> 20) & 0x3;   // 0=none,1=samples,2=real-time ps,3=free-running

                    logger.info(String.format("Word0 : seq=%d hasStreamId=%d hasClassId=%d TSI=%d TSF=%d", pktSeq, hasStreamId, hasClassId, tsiType, tsfType));

                    // ---- Stream ID, 4 bytes
                    int pktStreamId = 0;
                    if (hasStreamId == 1)
                    {
                        pktStreamId = readInt(data, offset);
                        sPktStreamId = String.format(
                                "%02X %02X %02X %02X",
                                buffer[offset],
                                buffer[offset + 1],
                                buffer[offset + 2],
                                buffer[offset + 3]);
                        logger.debug("streamId in packet : " + sPktStreamId + ", streamId : " + sStreamId);
                        offset += 4;
                    }

                    if (pktStreamId == streamId)
                    {

                        // ---- Class ID (optional, 8 bytes)
                        if (hasClassId == 1)
                        {
                            int classHigh = readInt(data, offset);      // padding + OUI
                            int classLow = readInt(data, offset + 4);  // info class + packet class
                            logger.debug(String.format("Class ID : OUI=0x%06X InfoClass=0x%04X PktClass=0x%04X", classHigh & 0x00FFFFFF, (classLow >> 16) & 0xFFFF, classLow & 0xFFFF));
                            offset += 8;
                        }

                        // The timestamp integer-seconds field in VITA-49 is Unix epoch (seconds since 1970-01-01 UTC), so you can convert it directly with Instant:
                        // FlexRadio DAX IQ typically uses TSI=1 (UTC) and TSF=1 (sample count since epoch), so you'll most likely see the second format — a clean UTC wall time plus a sample offset.
                        // ---- Timestamp
                        // Integer-seconds field: present if TSI != 0 (4 bytes)
                        // Fractional-seconds field: present if TSF != 0 (8 bytes)
                        long tsIntSeconds = 0;
                        long tsFractional = 0;

                        if (tsiType != 0)
                        {
                            tsIntSeconds = readInt(data, offset) & 0xFFFFFFFFL;
                            offset += 4;
                        }
                        if (tsfType != 0)
                        {
                            long hi = readInt(data, offset) & 0xFFFFFFFFL;
                            long lo = readInt(data, offset + 4) & 0xFFFFFFFFL;
                            tsFractional = (hi << 32) | lo;
                            offset += 8;
                        }

                        // Format timestamp
                        String tsReadable;
                        if (tsiType != 0)
                        {
                            long fracNanos;

                            switch (tsfType)
                            {
                                case 2:
                                    // picoseconds → nanoseconds
                                    fracNanos = tsFractional / 1_000L;
                                    break;

                                case 1:
                                    // sample count, not a wall offset
                                    fracNanos = 0;
                                    break;

                                default:
                                    // free-running, not a wall offset
                                    fracNanos = 0;
                                    break;
                            }

                            Instant ts = Instant.ofEpochSecond(tsIntSeconds, fracNanos);
                            String fracStr;

                            switch (tsfType)
                            {
                                case 2:
                                    fracStr = String.format(" + %d ps", tsFractional % 1_000_000_000_000L);
                                    break;

                                case 1:
                                    fracStr = String.format(" + %d samples", tsFractional);
                                    break;

                                case 3:
                                    fracStr = String.format(" + %d (free-running)", tsFractional);
                                    break;

                                default:
                                    fracStr = "";
                                    break;
                            }
                            tsReadable = TS_FMT.format(ts) + fracStr;
                        }
                        else
                        {
                            switch (tsfType)
                            {
                                case 0:
                                    tsReadable = "(no timestamp)";
                                    break;

                                default:
                                    tsReadable = String.format("(no wall clock) fractional=%d", tsFractional);
                                    break;
                            }
                        }
                        logger.debug("Timestamp: " + tsReadable);

                        // ---- Packet loss check
                        // The VITA-49 sequence number is only 4 bits wide (bits 19-16 of Word0), so it counts 0–15 and then wraps back to 0. This is defined in the spec
                        if (lastSeq != -1)
                        {
                            int expected = (lastSeq + 1) & 0xF;
                            if (pktSeq != expected)
                            {
                                logger.warn("⚠ Packet loss: expected seq " + expected + " got " + pktSeq);
                            }
                        }
                        lastSeq = pktSeq;

                        int payloadBytes = length - offset;
                        int samples = payloadBytes / 2;
                        logger.info(String.format("Payload : length=%d offset=%d payloadBytes=%d samples=%d packetSequence=%d", length, offset, payloadBytes, samples, pktSeq));

                        out = new byte[samples * 2];  // mono output
                        int iOut = 0;
                        for (int i = 0; i < samples; i++)
                        {
                            // https://github.com/ten9876/AetherSDR/blob/main/docs/vita49-format.md
                            // int16 mono, big-endian -- audioCfg.format = "s16be"
                            int hi = data[offset++] & 0xFF;
                            int lo = data[offset++] & 0xFF;
                            short sample = (short) ((hi << 8) | lo);
                            //  System.out.println(sample);

                            // write as little-endian (Java audio)
                            out[iOut++] = (byte) (sample & 0xFF);
                            out[iOut++] = (byte) (sample >> 8);

                        }
                        logger.debug("iOut : " + iOut + ", offset : " + offset);

                        audioOut.write(out, 0, iOut);

// audioOut.write(data, 0, samples * 4);
                    }
                    else
                    {
                        logger.info("ïgnoring stream id : " + pktStreamId);
                    }
                }
                catch (SocketTimeoutException e)
                {
                    logger.debug("DatagramSocket SocketTimeoutException");
                }
            }

            catch (Exception ex)
            {
                logger.fatal("Exception received : " + ex.getMessage());

                socket.close();
                tcp.close();

                connected = false;

            }
        }
    }

    private void sendRaw(String cmd)
    {
        String full = "CD" + seq + "|" + cmd;
        try
        {
            tcp.write(full);
            logger.info("TCP -> " + full);
            seq++;

            delay(100);
            readTCP();
        }
        catch (Exception ex)
        {
            logger.fatal("Exception : " + ex.getMessage());
        }
    }

    private void readTCP()
    {
        try
        {
            // read all tcp response from the socket
            String msg = "";
            do
            {
                msg = tcp.read();
                if (!msg.isEmpty())
                {
                    logger.info("<- : " + msg);

                    String[] msgParts = msg.split(" ");
                    for (int i = 0; i < msgParts.length; i++)
                    {
                        logger.debug(msgParts[i]);

                        // S70206A45|client 0x01A6E124 connected local_ptt=1 client_id=1D666125-7BFC-4EC9-8499-5DEBD5F79C17 program=SmartSDR-Win station=TOWER
                        if (msgParts[i].contains("client_id="))
                        {
                            if (clientId.isEmpty())
                            {
                                clientId = msgParts[i].substring("client_id=".length());
                                logger.info("clientId : " + clientId);
                            }
                        }
                        else if (msgParts[i].contains("|stream"))
                        {
                            sStreamId = msgParts[i + 1].replace("0x", "").replace("0X", "").trim();
                            if (sStreamId.isEmpty())
                            {
                                logger.fatal("streamId not found : " + msgParts[i + 1]);
                                continue;
                            }
                            streamId = (int) Long.parseLong(sStreamId, 16);
                            logger.info("StreamId : " + sStreamId);
                        }
                        else if (msgParts[i].contains("pan="))
                        {
                            // S72FBB2D7|stream 0x20000000 type=dax_iq daxiq_channel=1 pan=0x40000000 slice=0x0 endpoint_type=Display daxiq_rate=48000
                            panId = msgParts[i].substring("pan=".length());
                            logger.info("panId : " + panId);
                        }
                        else if (msgParts[i].contains("center="))
                        {
                            String[] frequencyParts = msgParts[i].split("=");
                            String frequency = frequencyParts[1];
                            logger.debug("frequency : " + frequency);
                            // remove the decimal point
                            frequency = frequency.replaceAll("\\.", "");
                            logger.debug("frequency : " + frequency);
                            try
                            {
                                vfo = Integer.parseInt(frequency);
                            }
                            catch (NumberFormatException e2)
                            {
                                logger.fatal("Failed to parse frequency : " + frequency);
                            }
                        }
                    }
                }
            }
            while (!msg.isEmpty());
            logger.debug("msg is now empty ... ");

            if (setRequestedFrequency)
            {
                setRequestedFrequency = false;

                BigDecimal b = new BigDecimal(requestedFrequency);
                // add the MHz decimal point with movePointLeft
                // sendRaw("display pan set " + panId + " center=14.250");
                sendRaw("display pan set 0x40000000 center=" + b.movePointLeft(6));
            }
        }
        catch (Exception ex)
        {
            logger.fatal("Exception received : " + ex.getMessage());
            tcp.close();
            connected = false;
        }
    }

    private int readInt(byte[] d, int o)
    {
        // read 4 bytes and convert into an int
        return ((d[o] & 0xFF) << 24)
                | ((d[o + 1] & 0xFF) << 16)
                | ((d[o + 2] & 0xFF) << 8)
                | (d[o + 3] & 0xFF);
    }

    private short readShortLE(byte[] d, int o)
    {
        return (short) (((d[o + 1] & 0xFF) << 8) | (d[o] & 0xFF));
    }

    private float readFloatLE(byte[] data, int offset)
    {
        int bits = (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);

        return Float.intBitsToFloat(bits);
    }
    int iIQIn = 0;
    double[][] IQIn = new double[FFTSIZE][2];

}
