package com.example.opcua.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Modbus TCP工具类
 */
public final class ModbusTcpUtil {

    private ModbusTcpUtil() {
    }

    public static String readPoint(String host, int port, int timeoutMs, String address, String devId, String nodeId, Double scaleFactor) throws IOException {
        int[] parsed = parseSlaveAndRegister(address.trim());
        int unitId = parsed[0];
        int refNumber = parsed[1];
        int fc;
        int pduStart;

        if (refNumber >= 40001 && refNumber <= 49999) {
            fc = 3;
            pduStart = refNumber - 40001;
        } else if (refNumber >= 30001 && refNumber <= 39999) {
            fc = 4;
            pduStart = refNumber - 30001;
        } else {
            fc = 3;
            pduStart = refNumber;
        }

        System.out.println("地址转换: 用户地址=" + refNumber + " -> Modbus起始地址=" + pduStart);

        int quantity = 2;
        byte[] pduResponse = sendReadRequest(host, port, timeoutMs, unitId, fc, pduStart, quantity);
        return decodeRegistersSmart(pduResponse, scaleFactor);
    }

    private static int[] parseSlaveAndRegister(String address) {
        int unitId = 1;
        String numPart = address;
        for (String sep : new String[]{";", ":"}) {
            if (address.contains(sep)) {
                String[] parts = address.split(sep, 2);
                unitId = Integer.parseInt(parts[0].trim());
                numPart = parts[1].trim();
                break;
            }
        }
        return new int[]{unitId, Integer.parseInt(numPart.trim())};
    }

    private static byte[] sendReadRequest(String host, int port, int timeoutMs, int unitId, int functionCode, int start, int quantity) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            int mbapLen = 12;
            byte[] reqBytes = new byte[mbapLen];
            int tid = (int) (System.currentTimeMillis() & 0xFFFF);

            reqBytes[0] = (byte) (tid >> 8);
            reqBytes[1] = (byte) tid;
            reqBytes[2] = 0;
            reqBytes[3] = 0;
            reqBytes[4] = 0;
            reqBytes[5] = 6;
            reqBytes[6] = (byte) unitId;
            reqBytes[7] = (byte) functionCode;
            reqBytes[8] = (byte) (start >> 8);
            reqBytes[9] = (byte) start;
            reqBytes[10] = (byte) (quantity >> 8);
            reqBytes[11] = (byte) quantity;

            StringBuilder reqHex = new StringBuilder();
            for (byte b : reqBytes) {
                reqHex.append(String.format("%02X ", b));
            }
            System.out.println("Modbus请求: 从站=" + unitId + ", 功能码=" + functionCode + ", 起始地址=" + start + ", 数量=" + quantity);
            System.out.println("Modbus请求数据: " + reqHex.toString());

            OutputStream out = socket.getOutputStream();
            out.write(reqBytes);
            out.flush();

            InputStream in = socket.getInputStream();
            byte[] mbap = readFully(in, 7);
            int length = ((mbap[4] & 0xFF) << 8) | (mbap[5] & 0xFF);
            byte[] pdu = readFully(in, length - 1);

            StringBuilder respHex = new StringBuilder();
            for (byte b : mbap) {
                respHex.append(String.format("%02X ", b));
            }
            for (byte b : pdu) {
                respHex.append(String.format("%02X ", b));
            }
            System.out.println("Modbus完整响应: " + respHex.toString());

            if (pdu.length < 2) {
                throw new IOException("Modbus响应过短");
            }
            if ((pdu[0] & 0xFF) == (functionCode | 0x80)) {
                int ex = pdu.length > 1 ? (pdu[1] & 0xFF) : -1;
                throw new IOException("Modbus异常功能码=" + (pdu[0] & 0xFF) + " 异常码=" + ex);
            }
            if ((pdu[0] & 0xFF) != functionCode) {
                throw new IOException("Modbus功能码不匹配: " + (pdu[0] & 0xFF));
            }
            int byteCount = pdu[1] & 0xFF;
            if (pdu.length < 2 + byteCount) {
                throw new IOException("Modbus字节计数不匹配");
            }
            byte[] regs = new byte[byteCount];
            System.arraycopy(pdu, 2, regs, 0, byteCount);

            StringBuilder regHex = new StringBuilder();
            for (byte b : regs) {
                regHex.append(String.format("%02X ", b));
            }
            System.out.println("Modbus寄存器数据: " + regHex.toString());

            return regs;
        }
    }

    private static byte[] readFully(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) {
                throw new IOException("连接提前关闭");
            }
            off += r;
        }
        return buf;
    }

    private static String decodeRegistersSmart(byte[] regs, Double scaleFactor) {
        double result = 0;
        boolean found = false;

        StringBuilder hex = new StringBuilder();
        for (byte b : regs) {
            hex.append(String.format("%02X ", b));
        }
        System.out.println("Modbus原始数据: " + hex.toString());

        if (regs.length >= 4) {
            try {
                ByteBuffer bbBig = ByteBuffer.wrap(regs).order(ByteOrder.BIG_ENDIAN);
                float floatVal = bbBig.getFloat(0);
                if (!Float.isNaN(floatVal) && !Float.isInfinite(floatVal)) {
                    result = floatVal;
                    found = true;
                    System.out.println("解码成功: Float (BIG_ENDIAN) = " + result);
                }
            } catch (Exception e) {
                // 忽略
            }

            if (!found) {
                try {
                    ByteBuffer bbLittle = ByteBuffer.wrap(regs).order(ByteOrder.LITTLE_ENDIAN);
                    float floatVal = bbLittle.getFloat(0);
                    if (!Float.isNaN(floatVal) && !Float.isInfinite(floatVal)) {
                        result = floatVal;
                        found = true;
                        System.out.println("解码成功: Float (LITTLE_ENDIAN) = " + result);
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }

            if (!found) {
                try {
                    ByteBuffer bbBig = ByteBuffer.wrap(regs).order(ByteOrder.BIG_ENDIAN);
                    int intVal = bbBig.getInt(0);
                    result = intVal;
                    found = true;
                    System.out.println("解码成功: Int32 (BIG_ENDIAN) = " + result);
                } catch (Exception e) {
                    // 忽略
                }
            }

            if (!found) {
                try {
                    ByteBuffer bbLittle = ByteBuffer.wrap(regs).order(ByteOrder.LITTLE_ENDIAN);
                    int intVal = bbLittle.getInt(0);
                    result = intVal;
                    found = true;
                    System.out.println("解码成功: Int32 (LITTLE_ENDIAN) = " + result);
                } catch (Exception e) {
                    // 忽略
                }
            }
        }

        if (!found && regs.length >= 2) {
            ByteBuffer bb = ByteBuffer.wrap(regs).order(ByteOrder.BIG_ENDIAN);
            int uint16 = bb.getShort(0) & 0xFFFF;
            result = uint16;
            System.out.println("使用默认解码: UInt16 = " + result);
        }

        if (scaleFactor != null && scaleFactor != 1.0) {
            result = result * scaleFactor;
            System.out.println("应用比例系数后: " + result);
        }

        if (result == (long) result) {
            return String.valueOf((long) result);
        } else {
            return String.format("%.6f", result).replaceAll("0+$", "").replaceAll("\\.$", "");
        }
    }
}
