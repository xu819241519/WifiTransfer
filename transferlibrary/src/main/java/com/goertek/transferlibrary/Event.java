package com.goertek.transferlibrary;

/**
 * Created by landon.xu on 2017/2/20.
 */

public class Event {
    //绑定端口号错误
    public static final int BIND_ERROR = 0;
    //建立连接
    public static final int CONNECT_EVENT = 1;
    //断开连接
    public static final int DISCONNECT_EVENT = 2;
    //发送消息
    public static final int DATA_SEND_EVENT = 3;
    //收到数据
    public static final int DATA_RECEIVE_EVENT = 4;
    //其他错误
    public static final int OTHER_ERROR = 1000;

}
