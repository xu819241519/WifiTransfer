package com.goertek.transferlibrary;

/**
 * Created by landon.xu on 2017/3/22.
 */

public class TransferProtocol {

    private static final byte PACKET_TYPE_VERIFY_CONNECTION = 0x10; // 搜索请求
    private static final byte PACKET_TYPE_VERIFY_TRANSFER = 0x11; // 搜索响应
    private static final byte PACKET_TYPE_DISCONNECT = 0x12; //断开连接相应


    private static final int SEARCH_SEQ_VALUE = 1;
    private static final int END_SEQ_VALUE = 2;


    private static boolean parseBasePack(byte[] data) {
        if (data == null) {
            return false;
        }
        int dataLen = data.length;
        return dataLen >= 2 && data[0] == '$';
    }

    /**
     * 解析报文
     * 协议：$ + packType(1) + length(4) + data(length)
     * packType - 报文类型
     * length表示data的长度
     * data存放数据
     */
    public static byte[] parseTransferPack(byte[] data) {
        if (!parseBasePack(data)) {
            return null;
        }
        int dataLen = data.length;
        int offset = 1;
        byte packType;
        packType = data[offset++];
        if (packType != PACKET_TYPE_VERIFY_TRANSFER) {
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
        byte[] result = new byte[len];
        System.arraycopy(data, offset, result, 0, len);
        return result;
    }


    /**
     * 校验搜索数据
     * 协议：$ + packType(1) + sendSeq(4)
     * packType - 报文类型
     * sendSeq - 发送序列
     */
    public static boolean parseConnectionPack(byte[] data) {
        if (!parseBasePack(data)) {
            return false;
        }
        int offset = 0;
        int sendSeq;
        if (data[offset++] != '$' || data[offset++] != PACKET_TYPE_VERIFY_CONNECTION) {
            return false;
        }
        sendSeq = data[offset++] & 0xFF;
        sendSeq |= (data[offset++] << 8);
        sendSeq |= (data[offset++] << 16);
        sendSeq |= (data[offset] << 24);
        return sendSeq == SEARCH_SEQ_VALUE;
    }

    public static boolean parseDisconnectPack(byte[] data){
        if(parseBasePack(data)){
            int offset = 0;
            int sendSeq;
            if (data[offset++] != '$' || data[offset++] != PACKET_TYPE_DISCONNECT) {
                return false;
            }
            sendSeq = data[offset++] & 0xFF;
            sendSeq |= (data[offset++] << 8);
            sendSeq |= (data[offset++] << 16);
            sendSeq |= (data[offset] << 24);
            return sendSeq == END_SEQ_VALUE;
        }
        return false;
    }


    public static byte[] packDisconnectPack(){
        byte[] temp = getBytesWithLength(END_SEQ_VALUE);
        byte[] data = new byte[2 + temp.length];
        int offset = 0;
        data[offset++] = '$';
        data[offset++] = PACKET_TYPE_DISCONNECT;
        System.arraycopy(temp, 0, data, offset, temp.length);
        return data;
    }


    /**
     * 打包响应报文
     * 协议：$ + packType(1) + length(4) + data(length)
     * packType - 报文类型
     * length表示data的长度
     * data存放数据
     */
    public static byte[] packTransferData(byte[] data) {

        byte[] temp = getBytesWithLength(data);
        byte[] sendData = new byte[2 + temp.length];
        int offset = 0;
        sendData[offset++] = '$';
        sendData[offset++] = PACKET_TYPE_VERIFY_TRANSFER;
        System.arraycopy(temp, 0, sendData, offset, temp.length);
        return sendData;
    }


    /**
     * 打包搜索报文
     * 协议：$ + packType(1) + sendSeq(4)
     * packType - 报文类型
     * sendSeq - 发送序列
     *
     * @return
     */
    public static byte[] packConnectionData() {
        byte[] temp = getBytesWithLength(SEARCH_SEQ_VALUE);
        byte[] data = new byte[2 + temp.length];
        int offset = 0;
        data[offset++] = '$';
        data[offset++] = PACKET_TYPE_VERIFY_CONNECTION;
        System.arraycopy(temp, 0, data, offset, temp.length);
        return data;
    }


    private static byte[] getBytesWithLength(byte[] data) {
        byte[] retVal = new byte[0];
        if (data != null) {
            retVal = new byte[4 + data.length];
            retVal[0] = (byte) data.length;
            retVal[1] = (byte) (data.length >> 8);
            retVal[2] = (byte) (data.length >> 16);
            retVal[3] = (byte) (data.length >> 24);
            System.arraycopy(data, 0, retVal, 4, data.length);
        }
        return retVal;
    }

    private static byte[] getBytesWithLength(int seq) {
        byte[] retVal;
        retVal = new byte[4];
        retVal[0] = (byte) seq;
        retVal[1] = (byte) (seq >> 8);
        retVal[2] = (byte) (seq >> 16);
        retVal[3] = (byte) (seq >> 24);
        return retVal;
    }
}
