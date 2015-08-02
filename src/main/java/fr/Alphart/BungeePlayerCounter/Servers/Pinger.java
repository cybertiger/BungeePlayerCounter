package fr.Alphart.BungeePlayerCounter.Servers;

import com.google.common.base.Charsets;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import com.google.gson.Gson;

import fr.Alphart.BungeePlayerCounter.BPC;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.Callable;

public class Pinger implements Callable<PingResponse> {
    private static final long PING_PAYLOAD = 0xdecafcafebabeL;
    private static final int PROCOL_VERSION = 4; // 1.7.2 protocol version
    private static final int MAX_PACKET_LENGTH = 0x10000; // 64kb
    private static final Gson gson = new Gson();
    private final InetSocketAddress address;
    private final String parentGroupName;
    private final byte[] handshakePacket;
    private final byte[] requestPacket;
    private final byte[] pingPacket;

    public Pinger(final String parentGroupName, final InetSocketAddress address) {
        this.parentGroupName = parentGroupName;
        this.address = address;
        handshakePacket = createQueryPacket();
        requestPacket = createRequestPacket();
        pingPacket = createPingPacket();
    }

    private byte[] createQueryPacket() {
        try {
            // See http://wiki.vg/index.php?title=Protocol&oldid=5486#Handshake
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MCDataOutputStream dataOut = new MCDataOutputStream(out);
            dataOut.writeVarInt(0); // Packet id
            dataOut.writeVarInt(PROCOL_VERSION); // Protocol version.
            dataOut.writeMCString(address.getHostString());
            dataOut.writeShort(address.getPort());
            dataOut.writeVarInt(1);
            return out.toByteArray();
        } catch (IOException ex) {
            // Should not happen.
            throw new IllegalStateException(ex);
        }
    }

    private byte[] createRequestPacket() {
        try {
            // See http://wiki.vg/index.php?title=Protocol&oldid=5486#Request
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MCDataOutputStream dataOut = new MCDataOutputStream(out);
            dataOut.writeVarInt(0); // Packet id
            return out.toByteArray();
        } catch (IOException ex) {
            // Should not happen.
            throw new IllegalStateException(ex);
        }
    }

    private byte[] createPingPacket() {
        try {
            // See http://wiki.vg/index.php?title=Protocol&oldid=5486#Ping_2
            // the protocol document states that we should send a time
            // here, however whatever we send is returned by the server
            // in the ping response, so lets just pick an easily identifiable
            // number.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MCDataOutputStream dataOut = new MCDataOutputStream(out);
            dataOut.writeVarInt(0); // Packet id
            dataOut.writeLong(PING_PAYLOAD); // 
            return out.toByteArray();
        } catch (IOException ex) {
            // Should not happen.
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public PingResponse call() {
        try {
            final PingResponse response = ping(address, 1000);
            BPC.debug("Successfully pinged " + parentGroupName + " group, result : " + response);
            return response;
        } catch (IOException e) {
            if (!(e instanceof ConnectException) && !(e instanceof SocketTimeoutException)) {
                BPC.severe("An unexcepted error occured while pinging " + parentGroupName + " server", e);
            }
        }
        return null;
    }

    public PingResponse ping(final InetSocketAddress host, final int timeout) throws IOException {
        Socket socket = null;
        try {
            MCDataOutputStream dataOutputStream;
            MCDataInputStream dataInputStream;
            String json;
            byte[] packet;
            MCDataInputStream packetIn;
            long pingTimestamp;
            long pongTimestamp;
            long pongPayload;
            int pktId;

            socket = new Socket();

            socket.setSoTimeout(timeout);

            socket.connect(host, timeout);

            dataOutputStream = new MCDataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 2048));
            dataInputStream = new MCDataInputStream(socket.getInputStream());

            // Write handshake + request
            dataOutputStream.writePacket(handshakePacket);
            dataOutputStream.writePacket(requestPacket);
            dataOutputStream.flush();

            // Read handshake response.
            packet = dataInputStream.readPacket();
            packetIn = new MCDataInputStream(new ByteArrayInputStream(packet));
            pktId = packetIn.readVarInt();
            if (pktId != 0) {
                throw new IOException("Server sent unexpected response to handshake, expected packet id: 0 got: " + pktId);
            }
            json = packetIn.readMCString(); // Grab the ping response.


            // Write ping request
            dataOutputStream.writePacket(pingPacket);
            dataOutputStream.flush();
            pingTimestamp = System.nanoTime(); // Record time of sending the ping

            // Read ping response.
            packet = dataInputStream.readPacket();
            pongTimestamp = System.nanoTime(); // Record time of receiving the response
            packetIn = new MCDataInputStream(new ByteArrayInputStream(packet));
            pktId = packetIn.readVarInt();
            if (pktId != 1) {
                throw new IOException("Server sent unexpected response to ping, expected packet id: 1 got: " + pktId);
            }
            pongPayload = packetIn.readLong();
            if (PING_PAYLOAD != pongPayload) {
                // Hack to print returned ping response as an unsigned 64 bit long in hex.
                throw new IOException(String.format("Expected ping response payload 0x%x got 0x%x%x", PING_PAYLOAD, (pongPayload>>32)&0xFFFFFFFFL, pongPayload&0xFFFFFFFFL));
            }

            synchronized (gson) {
                final PingResponse response = gson.fromJson(json, PingResponse.class);
                response.setTime(pongTimestamp - pingTimestamp);
                return response;
            }
        } catch (final IOException e) {
            throw e;
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }



    /**
     * Enhanced DataIS which reads VarInt type
     */
    public final static class MCDataInputStream extends DataInputStream {

        public MCDataInputStream(final InputStream is) {
            super(is);
        }

        public int readVarInt() throws IOException {
            int i = 0;
            int j = 0;
            while (true) {
                int k = readByte();
                i |= (k & 0x7F) << j++ * 7;
                if (j > 5) {
                    throw new IOException("VarInt too big");
                }
                if ((k & 0x80) != 0x80) {
                    return i;
                }
            }
        }

        public String readMCString() throws IOException {
            int strlen = readVarInt();
            byte[] stringData = new byte[strlen];
            readFully(stringData);
            return new String(stringData, Charsets.UTF_8);
        }

        public byte[] readPacket() throws IOException {
            int pktLen = readVarInt();
            if (pktLen <= 0 || pktLen > MAX_PACKET_LENGTH) {
                throw new IOException("Packet length invalid, expected 0 < length <= " + MAX_PACKET_LENGTH + " got length: " + pktLen);
            }
            byte[] packet = new byte[pktLen];
            readFully(packet);
            return packet;
        }

    }

    /**
     * Enhanced DataOS which writes VarInt type
     */
    public final static class MCDataOutputStream extends DataOutputStream {

        public MCDataOutputStream(final OutputStream os) {
            super(os);
        }

        public void writeVarInt(int paramInt) throws IOException {
            while (true) {
                if ((paramInt & 0xFFFFFF80) == 0) {
                    writeByte(paramInt);
                    return;
                }

                writeByte(paramInt & 0x7F | 0x80);
                paramInt >>>= 7;
            }
        }

        public void writeMCString(String s) throws IOException {
            byte[] stringData = s.getBytes(Charsets.UTF_8);
            writeVarInt(stringData.length);
            write(stringData);
        }

        public void writePacket(byte[] pkt) throws IOException {
            if (pkt.length == 0 || pkt.length > MAX_PACKET_LENGTH) {
                throw new IOException("Packet length invalid, expected 0 < length <= " + MAX_PACKET_LENGTH + " got length: " + pkt.length);
            }
            writeVarInt(pkt.length);
            write(pkt);
        }
    }
}
