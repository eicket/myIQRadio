// Erik Icket, ON4PB - 2026
package udp;

import common.Constants;
import static common.Constants.FFTSIZE;
import static common.Constants.NR_OF_SPECTRUM_POINTS;
import static common.Constants.SPECTRUM_DECIMATION_FACTOR;
import common.PropertiesWrapper;
import fft.Complex;
import static fft.FFT.fft;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import static main.MainController.invertIQ;
import static main.MainController.lbdDemodulatorInput;
import static main.MainController.lbdSpectrum;
import static main.MainController.requestedFrequency;
import static main.MainController.rxGain;
import static main.MainController.sampleRate;
import static main.MainController.setRequestedFrequency;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tcpip.TCP;
import static utils.utils.delay;
import static main.MainController.vfo;

public class VITA49InThread extends Thread
{

    static final Logger logger = LogManager.getLogger(VITA49InThread.class.getName());

    public boolean stopRequest = false;

    PropertiesWrapper propWrapper = new PropertiesWrapper();
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS 'UTC'").withZone(ZoneOffset.UTC);

    public boolean connected = false;
    private final int udpPort = 4050;
    private DatagramSocket socket;
    private TCP tcp;

    private boolean processIQ;

    private String clientId = "";
    private String sStreamId;
    private String panId;
    private int streamId = -1;
    private int seq = 1;

    public VITA49InThread(boolean processIQ)
    {
        this.processIQ = processIQ;

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
            delay(100);

            sendRaw("client udpport " + Constants.LOCAL_UDP_PORT);
            sendRaw("sub client all");
            sendRaw("client bind client_id=" + clientId);
            sendRaw("stream create type=dax_iq daxiq_channel=1");
            sendRaw("stream set 0x" + sStreamId + " daxiq_rate=" + sampleRate);
            sendRaw("display pan set " + panId + " daxiq_channel=1");
            sendRaw("stream get_error 0x" + sStreamId);
            // get the center pan frequency 
            sendRaw("sub pan all");
            // Pan 0 uses the handle 0x40000000, and Pan 1 uses 0x40000001

            connected = true;
        }
        logger.info("VITA49InThread is started");
    }

    public void run()
    {
        byte[] buffer = new byte[8192];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        int lastSeq = -1;
        String sPktStreamId = "";

        while (!stopRequest)
        {
            readTCP();

            try
            {
                socket.receive(packet);

                byte[] data = packet.getData();
                int length = packet.getLength();
                int offset = 0;

                // dump 28 bytes header - dax
                // header bytes: 38 50 01 07 04 00 00 08 00 00 1C 2D 53 4C 03 E3 A5 A5 A0 A5 5F 71 39 1D A5 A5 A0 A5 
                // daxiq
                // header bytes: 1C 51 04 08 20 00 00 00 00 00 1C 2D 53 4C 02 E4 69 EC 60 F8 00 00 00 00 00 00 00 01
                StringBuilder sb = new StringBuilder("header bytes: ");
                for (int i = 0; i < 28; i++)
                {
                    sb.append(String.format("%02X ", data[i] & 0xFF));
                }
                logger.debug(sb.toString());

                // header is always 28 bytes
                // https://github.com/ten9876/AetherSDR/blob/main/docs/vita49-format.md
                // ---- Word 0: packet type, indicators, sequence, 4 bytes
                int word0 = readInt(data, offset);
                offset += 4;

                int pktType = (word0 >> 28) & 0xF;
                int hasClassId = (word0 >> 27) & 0x1;   // C flag
                int hasTrailer = (word0 >> 26) & 0x1;   // T flag
                boolean hasStreamId = (pktType & 0x1) != 0;  // types 1,3,5 always have stream ID
                int pktSeq = (word0 >> 16) & 0xF;
                int tsiType = (word0 >> 22) & 0x3;   // 0=none,1=UTC,2=GPS,3=other
                int tsfType = (word0 >> 20) & 0x3;   // 0=none,1=samples,2=real-time ps,3=free-running

                // Word0 : seq=10 hasStreamId=true hasClassId=1 TSI=1 TSF=1
                logger.debug(String.format("Word0 : seq=%d hasStreamId=%b hasClassId=%d TSI=%d TSF=%d", pktSeq, hasStreamId, hasClassId, tsiType, tsfType));

                // ---- Stream ID, 4 bytes
                int pktStreamId = 0;
                if (hasStreamId)
                {
                    pktStreamId = readInt(data, offset);
                    sPktStreamId = String.format(
                            "%02X %02X %02X %02X",
                            data[offset],
                            data[offset + 1],
                            data[offset + 2],
                            data[offset + 3]);
                    logger.debug("streamId in packet : " + sPktStreamId + ", streamId : " + sStreamId);
                    offset += 4;
                }

                /*
                Word0 : seq=0 hasStreamId=true hasClassId=1 TSI=1 TSF=1
                streamId in packet : 04 00 00 08, streamId : 04000008
                Class ID : OUI=0x001C2D InfoClass=0x534C PktClass=0x03E3
                
                -> PCC : Packet Class Code : 03E3 : RX audio (uncompressed), float32 stereo, big-endian 
                 */
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
                    // The VITA-49 sequence number is only 4 bits wide (bits 19-16 of Word0), so it counts 0–15 and then wraps back to 0. 
                    if (lastSeq != -1)
                    {
                        int expected = (lastSeq + 1) & 0xF;
                        if (pktSeq != expected)
                        {
                            logger.warn("⚠ Packet loss: expected seq " + expected + " got " + pktSeq);
                        }
                    }
                    lastSeq = pktSeq;

                    // subtract 4 for trailer, which is not used
                    int payloadBytes = length - offset - (hasTrailer == 1 ? 4 : 0);
                    int samples = payloadBytes / 8;
                    logger.debug(String.format("Datagram packet length=%d header=%d payloadBytes=%d samples=%d packetSequence=%d", length, offset, payloadBytes, samples, pktSeq));
                    // Datagram packet length=4128 header=28 payloadBytes=4096 samples=512 packetSequence=8

                    for (int i = 0; i < samples; i++)
                    {
                        // ---- IQ payload: 32-bit float little-endian interleaved I/Q pairs
                        float iSample = readFloatLE(data, offset);
                        float qSample = readFloatLE(data, offset + 4);

                        if (processIQ)
                        {
                            if (invertIQ)
                            {
                                processIQ(qSample * rxGain, iSample * rxGain);
                            }
                            else
                            {
                                processIQ(iSample * rxGain, qSample * rxGain);
                            }
                            logger.debug("I: " + iSample + " Q: " + qSample);
                        }
                        offset += 8;
                    }
                }
                else
                {
                    logger.info("Ignoring stream id : " + sPktStreamId);
                }
            }
            catch (SocketTimeoutException e)
            {
                logger.debug("DatagramSocket SocketTimeoutException");
            }
            catch (Exception ex)
            {
                logger.fatal("Exception received : " + ex.getMessage());

                socket.close();
                tcp.close();
                connected = false;
            }
        }
        socket.close();
        tcp.close();
        logger.info("VITA49InThread exit");
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

    private float readFloatLE(byte[] data, int offset)
    {
        int bits = (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);

        return Float.intBitsToFloat(bits);
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

    // not used in demo
    int iIQIn = 0;
    double[][] IQIn = new double[FFTSIZE][2];

    private void processIQ(double i, double q)
    {
        /*
        DAX Audio channels are typical IEEE-754 single-precision floats with a 23-bit mantissa.  
        This represents 138dB of instantaneous dynamic range in the container.        
         */
        logger.debug("i : " + i + ", q : " + q);
        IQIn[iIQIn][0] = i;
        IQIn[iIQIn][1] = q;
        iIQIn++;

        if (iIQIn == FFTSIZE)
        {
            // make a new object for every enqueue .. otherwise values are overwritten and corrupted when polling the queue
            double[][] iqDemodulatorIn = new double[FFTSIZE][2];
            Complex[] complexIn = new Complex[FFTSIZE];
            Complex[] complexOut = new Complex[FFTSIZE];
            Complex[] swappedComplexOut = new Complex[FFTSIZE];

            for (int k = 0; k < FFTSIZE; k++)
            {
                iqDemodulatorIn[k][0] = IQIn[k][0];
                iqDemodulatorIn[k][1] = IQIn[k][1];

                complexIn[k] = new Complex(IQIn[k][0], IQIn[k][1]);
            }

            // send to the demodulator for later audio output 
            lbdDemodulatorInput.add(iqDemodulatorIn);
            iIQIn = 0;

            complexOut = fft(complexIn);

            // swap the lower spectrum to high, and higher spectrun to low
            // see fig 4 in QEX article
            // bins 0 - 2047
            for (int k = 0; k < FFTSIZE / 2; k++)
            {
                swappedComplexOut[k + FFTSIZE / 2] = complexOut[k];
            }

            // bins 2048 - 4097
            for (int k = FFTSIZE / 2; k < FFTSIZE; k++)
            {
                swappedComplexOut[k - FFTSIZE / 2] = complexOut[k];
            }

            double[] spectrum = new double[FFTSIZE];
            for (int k = 0; k < FFTSIZE; k++)
            {
                spectrum[k] = 10 * Math.log10(Math.pow(swappedComplexOut[k].abs(), 2));
            }

            // decimate, so that the linechart is not too large
            // best decimation is simple averaging  -- all values are positive anyhow
            double[] decimateOut = new double[NR_OF_SPECTRUM_POINTS];
            double average = 0;
            for (int k = 0; k < FFTSIZE; k++)
            {
                if (k % SPECTRUM_DECIMATION_FACTOR == 7)
                {
                    decimateOut[k / SPECTRUM_DECIMATION_FACTOR] = average / SPECTRUM_DECIMATION_FACTOR;
                    average = 0;
                }
                average += spectrum[k];
            }
            lbdSpectrum.add(decimateOut);
        }
    }
}
