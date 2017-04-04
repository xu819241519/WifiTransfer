package com.goertek.transferlibrary;

/**
 * Created by landon.xu on 2017/2/18.
 */

public class Config {
    //发送间隔时间，单位毫秒
    private int interval = 5;
    //发送数据包长度
    private int packetMaxLength = 65535;

    public static final String ENCODE_TYPE = "UTF-8";

    public int getInterval() {
        if(interval <= 0){
            return 0;
        }
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getPacketMaxLength() {
        return packetMaxLength;
    }

    public void setPacketMaxLength(int packetMaxLength) {
        this.packetMaxLength = this.packetMaxLength;
    }
}
