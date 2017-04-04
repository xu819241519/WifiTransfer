package com.goertek.transferlibrary.search.protocol;

import android.content.Context;

import com.goertek.transferlibrary.utils.IPUtils;

import java.net.DatagramPacket;
import java.nio.charset.Charset;

/**
 * Created by landon.xu on 2017/3/22.
 */

public class SearchProtocol {

    private static final byte PACKET_TYPE_FIND_DEVICE_REQ_10 = 0x10; // 搜索请求
    private static final byte PACKET_TYPE_FIND_DEVICE_RSP_11 = 0x11; // 搜索响应
    private static final byte PACKET_TYPE_FIND_DEVICE_CHK_12 = 0x12; // 搜索确认
    //搜索seq的值
    private static final int SEARCH_SEQ_VALUE = 1;

    public static final int RESPONSE_DEVICE_MAX = 200; // 响应设备的最大个数，防止UDP广播攻击

    public static final int DEVICE_MAX_RECEIVE_BYTE = 21;

    public static final int SERACHER_MAX_RECEIVE_BYTE = 65535;

    public static final int RECEIVE_TIME_OUT = 500;

    public static final int BROADCAST_RECEIVE_TIME_OUT = 8000;

    public static final String BROADCAST_IP = "224.1.1.1";

    public static final int DEVICE_FIND_PORT = 9000;


    private static boolean parseBasePack(DatagramPacket pack) {
        if (pack == null || pack.getAddress() == null) {
            return false;
        }
        int dataLen = pack.getLength();
        return dataLen >= 2 && pack.getData()[0] == '$';
    }

    /**
     * 解析报文
     * 协议：$ + packType(1) + length(4) + data(length)
     * packType - 报文类型
     * length表示data的长度
     * data存放数据
     */
    public static String parseDevicePack(DatagramPacket pack) {
        if (!parseBasePack(pack)) {
            return null;
        }
        int dataLen = pack.getLength();
        int offset = 1;
        byte packType;
        byte[] data = new byte[dataLen];
        System.arraycopy(pack.getData(), pack.getOffset(), data, 0, dataLen);
        packType = data[offset++];
        if (packType != PACKET_TYPE_FIND_DEVICE_RSP_11) {
            return null;
        }
        int len;
        len = data[offset++] & 0xFF;
        len |= (data[offset++] << 8);
        len |= (data[offset++] << 16);
        len |= (data[offset++] << 24);
        if (offset + len > dataLen) {
            return null;
        }
        return new String(data, offset, len, Charset.forName("UTF-8"));
    }


    /**
     * 校验搜索数据
     * 协议：$ + packType(1) + sendSeq(4)
     * packType - 报文类型
     * sendSeq - 发送序列
     */
    public static boolean parseSearchData(DatagramPacket pack) {
        if (!parseBasePack(pack)) {
            return false;
        }
        if (pack.getLength() != 6) {
            return false;
        }
        byte[] data = pack.getData();
        int offset = pack.getOffset();
        int sendSeq;
        if (data[offset++] != '$' || data[offset++] != PACKET_TYPE_FIND_DEVICE_REQ_10) {
            return false;
        }
        sendSeq = data[offset++] & 0xFF;
        sendSeq |= (data[offset++] << 8);
        sendSeq |= (data[offset++] << 16);
        sendSeq |= (data[offset] << 24);
        return sendSeq == SEARCH_SEQ_VALUE;
    }


    /**
     * 校验确认数据
     * 协议：$ + packType(1) + sendSeq(4) + deviceIP(n<=15)
     * packType - 报文类型
     * sendSeq - 发送序列
     * deviceIP - 设备IP，仅确认时携带
     */
    public static boolean parseCheckData(Context context, DatagramPacket pack) {
        if (!parseBasePack(pack)) {
            return false;
        }
        if (pack.getLength() < 6) {
            return false;
        }
        byte[] data = pack.getData();
        int offset = pack.getOffset();
        int sendSeq;
        if (data[offset++] != '$' || data[offset++] != PACKET_TYPE_FIND_DEVICE_CHK_12) {
            return false;
        }
        sendSeq = data[offset++] & 0xFF;
        sendSeq |= (data[offset++] << 8);
        sendSeq |= (data[offset++] << 16);
        sendSeq |= (data[offset++] << 24);
        if (sendSeq < 1 || sendSeq > RESPONSE_DEVICE_MAX) {
            return false;
        }
        if (pack.getLength() <= offset) {
            return false;
        }
        String ip = new String(data, offset, pack.getLength() - offset, Charset.forName("UTF-8"));
        return ip.equals(IPUtils.getLocalIP(context));
    }


    /**
     * 打包响应报文
     * 协议：$ + packType(1) + length(4) + data(length)
     * packType - 报文类型
     * length表示data的长度
     * data存放数据
     */
    public static byte[] packDeviceData(String sendString) {

        byte[] temp = getBytesWithLength(sendString);
        byte[] data = new byte[2 + temp.length];
        int offset = 0;
        data[offset++] = '$';
        data[offset++] = PACKET_TYPE_FIND_DEVICE_RSP_11;
        System.arraycopy(temp, 0, data, offset, temp.length);
        return data;
    }


    /**
     * 打包搜索报文
     * 协议：$ + packType(1) + sendSeq(4)
     * packType - 报文类型
     * sendSeq - 发送序列
     *
     * @return
     */
    public static byte[] packSearchData() {
        byte[] temp = getBytesWithLength(1, null);
        byte[] data = new byte[2 + temp.length];
        int offset = 0;
        data[offset++] = '$';
        data[offset++] = PACKET_TYPE_FIND_DEVICE_REQ_10;
        System.arraycopy(temp, 0, data, offset, temp.length);
        return data;
    }


    /**
     * 打包搜索确认报文
     * 协议：$ + packType(1) + sendSeq(4) + deviceIP(n<=15)
     * packType - 报文类型
     * sendSeq - 发送序列
     * deviceIP - 设备IP，仅确认时携带
     */
    public static byte[] packCheckData(int seq, String ip) {
        byte[] temp = getBytesWithLength(seq, ip);
        byte[] data = new byte[2 + temp.length];
        int offset = 0;
        data[offset++] = '$';
        data[offset++] = PACKET_TYPE_FIND_DEVICE_CHK_12;
        System.arraycopy(temp, 0, data, offset, temp.length);
        return data;
    }


    private static byte[] getBytesWithLength(String val) {
        byte[] retVal = new byte[0];
        if (val != null) {
            byte[] valBytes = val.getBytes(Charset.forName("UTF-8"));
            retVal = new byte[4 + valBytes.length];
            retVal[0] = (byte) valBytes.length;
            retVal[1] = (byte) (valBytes.length >> 8);
            retVal[2] = (byte) (valBytes.length >> 16);
            retVal[3] = (byte) (valBytes.length >> 24);
            System.arraycopy(valBytes, 0, retVal, 4, valBytes.length);
        }
        return retVal;
    }

    private static byte[] getBytesWithLength(int seq, String option) {
        byte[] retVal;
        if (option != null) {
            byte[] valBytes = option.getBytes(Charset.forName("UTF-8"));
            retVal = new byte[4 + valBytes.length];
        } else {
            retVal = new byte[4];
        }
        retVal[0] = (byte) seq;
        retVal[1] = (byte) (seq >> 8);
        retVal[2] = (byte) (seq >> 16);
        retVal[3] = (byte) (seq >> 24);
        if (option != null) {
            byte[] valBytes = option.getBytes(Charset.forName("UTF-8"));
            System.arraycopy(valBytes, 0, retVal, 4, valBytes.length);
        }
        return retVal;
    }
}
