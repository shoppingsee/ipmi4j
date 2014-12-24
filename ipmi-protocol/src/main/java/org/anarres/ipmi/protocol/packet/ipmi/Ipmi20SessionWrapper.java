/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.anarres.ipmi.protocol.packet.ipmi;

import org.anarres.ipmi.protocol.packet.ipmi.payload.IpmiPayloadType;
import com.google.common.primitives.Chars;
import java.nio.ByteBuffer;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import org.anarres.ipmi.protocol.IanaEnterpriseNumber;
import org.anarres.ipmi.protocol.packet.common.Pad;
import org.anarres.ipmi.protocol.packet.ipmi.payload.IpmiPayload;

/**
 * [IPMI2] Section 13.6 pages 133-134, column 3.
 * Confidentiality wrappers from [IPMI2] Section 13.29 page 160, table 13-20.
 *
 * @author shevek
 */
public class Ipmi20SessionWrapper implements IpmiSessionWrapper {

    private final IpmiHeaderAuthenticationType authenticationType = IpmiHeaderAuthenticationType.RMCPP;
    private IpmiPayloadType payloadType;
    private boolean encrypted;
    private boolean authenticated;
    private IanaEnterpriseNumber oemEnterpriseNumber;    // 3 byte oem iana; 1 byte zero
    private char oemPayloadId;
    private int ipmiSessionId;
    private int ipmiSessionSequenceNumber;
    private byte[] confidentialityHeader;
    private byte[] confidentialityTrailer;
    private byte[] integrityData;

    @Override
    public int getIpmiSessionId() {
        return ipmiSessionId;
    }

    @Override
    public int getIpmiSessionSequenceNumber() {
        return ipmiSessionSequenceNumber;
    }

    @Nonnegative
    private int getPayloadLength(@Nonnull IpmiHeader header, @Nonnull IpmiPayload payload) {
        return ((confidentialityHeader == null) ? 0 : confidentialityHeader.length)
                + header.getWireLength()
                + payload.getWireLength()
                + ((confidentialityTrailer == null) ? 0 : confidentialityTrailer.length);
    }

    @Override
    public int getWireLength(IpmiHeader header, IpmiPayload payload) {
        boolean oem = IpmiPayloadType.OEM_EXPLICIT.equals(payloadType);
        int payloadLength = getPayloadLength(header, payload);
        return 1 // authenticationType
                + 1 // payloadType
                + (oem ? 4 : 0) // oemEnterpriseNumber
                + (oem ? 2 : 0) // oemPayloadId
                + 4 // ipmiSessionId
                + 4 // ipmiSessionSequenceNumber
                + 2 // payloadLength
                + getPayloadLength(header, payload)
                + Pad.PAD(payloadLength).length
                + 1 // pad length
                + 1 // next header
                + integrityData.length;
    }

    @Override
    public void toWire(ByteBuffer buffer, IpmiHeader header, IpmiPayload payload) {
        // Page 133
        buffer.put(authenticationType.getCode());
        byte payloadTypeByte = payloadType.getCode();
        if (encrypted)
            payloadTypeByte |= 0x80;
        if (authenticated)
            payloadTypeByte |= 0x40;
        buffer.put(payloadTypeByte);
        if (IpmiPayloadType.OEM_EXPLICIT.equals(payloadType)) {
            buffer.putInt(oemEnterpriseNumber.getNumber() << Byte.SIZE);
            buffer.putChar(oemPayloadId);
        }
        buffer.putInt(ipmiSessionId);
        buffer.putInt(ipmiSessionSequenceNumber);

        // Page 134
        // 2 byte payload length
        int payloadLength = getPayloadLength(header, payload);
        buffer.putChar(Chars.checkedCast(payloadLength));
        buffer.put(confidentialityHeader);

        header.toWire(buffer);
        payload.toWire(buffer);

        byte[] pad = Pad.PAD(payloadLength);
        buffer.put(pad);
        buffer.put((byte) pad.length);
        buffer.put((byte) 0x07); // Reserved, [IPMI2] Page 134
        buffer.put(integrityData);
    }

    /*
     @Override
     protected void fromWireUnchecked(ByteBuffer buffer) {
     int payloadLength = getPayloadLength(payload);
     byte[] pad = Pad.PAD(payloadLength);
     AbstractWireable.readBytes(buffer, pad.length);
     AbstractWireable.assertWireByte(buffer, (byte) pad.length, "padding length");
     AbstractWireable.assertWireByte(buffer, (byte) 0x07, "IPMI v2.0 next header byte (reserved as 0x07)");
     integrityData = AbstractWireable.readBytes(buffer, integrityData.length);
     }
     */
    @Override
    public void fromWire(ByteBuffer buffer, IpmiHeader header, IpmiPayload payload) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}