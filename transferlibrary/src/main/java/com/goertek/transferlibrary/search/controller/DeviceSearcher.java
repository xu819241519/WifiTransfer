package com.goertek.transferlibrary.search.controller;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;

import com.goertek.transferlibrary.search.protocol.SearchProtocol;
import com.goertek.transferlibrary.utils.IPUtils;
import com.goertek.transferlibrary.utils.LogUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描设备
 * Created by landon.xu on 2017/3/14.
 */
public class DeviceSearcher {

    private static final String TAG = DeviceSearcher.class.getSimpleName();

    private volatile boolean mSearching = false;

    private static final int DEVICE_FOUND_MSG = 0;

    private static final int DEVICE_FOUND_ERROR_MSG = 1;

    private static final int DEVICE_FOUND_END = 2;

    private static final int DEVICE_FOUND_START = 3;

    private static final int ERROR = 4;

    private ISearchListener mSearchListener;

    private Context mContext;

    private BroadcastHandler mHandler = new BroadcastHandler(this);

    private SearchThread mSearchThread;

    private List<String> mDevices;

    private static class BroadcastHandler extends Handler {
        private WeakReference<DeviceSearcher> mIPBroadcastWeakReference;

        BroadcastHandler(DeviceSearcher broadcast) {
            mIPBroadcastWeakReference = new WeakReference<>(broadcast);
        }

        @Override
        public void handleMessage(Message msg) {
            LogUtils.d(TAG, "handler receive msg");
            DeviceSearcher broadcast = mIPBroadcastWeakReference.get();
            if (broadcast != null && broadcast.mSearchListener != null) {
                switch (msg.what) {
                    case DEVICE_FOUND_ERROR_MSG:
                        LogUtils.d(TAG, "device parse error");
                        break;
                    case DEVICE_FOUND_MSG:
                        LogUtils.d(TAG, "a device found");
                        broadcast.mDevices.add((String) msg.obj);
                        break;
                    case DEVICE_FOUND_START:
                        broadcast.mSearchListener.onSearchStart();
                        break;
                    case ERROR:
                        broadcast.mSearchListener.onError((String) msg.obj);
                    case DEVICE_FOUND_END:
                        broadcast.mSearchListener.onSearchFinish(broadcast.mDevices);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public DeviceSearcher(Context context, ISearchListener listener) {
        mSearchListener = listener;
        mContext = context;
    }

    public void start() {
        start(false);
    }

    public void start(boolean perMulticast) {
        if (mSearchThread == null) {
            if (mDevices == null) {
                mDevices = new ArrayList<>();
            } else {
                mDevices.clear();
            }
            mSearchThread = new SearchThread();
            mSearchThread.setPerMulticast(perMulticast);
            mSearchThread.start();
        }
    }

    public void stop() {
        mSearchThread.onDestroy();
        mSearchThread = null;
    }

    private class SearchThread extends Thread {

        private DatagramSocket mSocket;

        private boolean mPerMulticast = false;

        void setPerMulticast(boolean perMulticast) {
            mPerMulticast = perMulticast;
        }

        private void sendSearchMessage(InetAddress address) throws IOException {
            LogUtils.d(TAG, "send search message");
            byte[] searchPacket = SearchProtocol.packSearchData();
            DatagramPacket packet = new DatagramPacket(searchPacket, searchPacket.length, address, SearchProtocol.DEVICE_FIND_PORT);
            mSocket.send(packet);
        }

        private void sendCheckMessage(InetAddress address, int seq) throws IOException {
            LogUtils.d(TAG, "send checkMessage");
            byte[] checkPacket = SearchProtocol.packCheckData(seq, address.getHostAddress());
            DatagramPacket packet = new DatagramPacket(checkPacket, checkPacket.length, address, SearchProtocol.DEVICE_FIND_PORT);
            mSocket.send(packet);
        }


        @Override
        public void run() {
            try {
                Message msg = mHandler.obtainMessage(DEVICE_FOUND_START);
                msg.sendToTarget();
                //非一对一发消息，组播获取
                if (!mPerMulticast) {
                    mSocket = new MulticastSocket(SearchProtocol.DEVICE_FIND_PORT);
                    mSocket.setSoTimeout(SearchProtocol.BROADCAST_RECEIVE_TIME_OUT);
                    InetAddress broadcastIP = InetAddress.getByName(SearchProtocol.BROADCAST_IP);
                    sendSearchMessage(broadcastIP);
                    byte[] receivePacket = new byte[SearchProtocol.SERACHER_MAX_RECEIVE_BYTE];
                    DatagramPacket packet = new DatagramPacket(receivePacket, receivePacket.length);
                    mSearching = true;
                    while (mSearching) {
                        mSocket.receive(packet);
                        String responseString = SearchProtocol.parseDevicePack(packet);
                        if (!TextUtils.isEmpty(responseString)) {
                            LogUtils.d(TAG, "receive : " + responseString);
                            sendCheckMessage(packet.getAddress(), 1);
                            msg = mHandler.obtainMessage(DEVICE_FOUND_MSG);
                            msg.obj = responseString;
                        } else {
                            LogUtils.d(TAG, "receive error");
                            msg = mHandler.obtainMessage(DEVICE_FOUND_ERROR_MSG);
                        }
                        msg.sendToTarget();
                    }
                }
                //对同一网络中的主机一对一发送udp消息，查询在线情况
                else {
                    mSocket = new DatagramSocket(SearchProtocol.DEVICE_FIND_PORT);
                    mSocket.setSoTimeout(SearchProtocol.RECEIVE_TIME_OUT +  2500);
                    List<String> ips = IPUtils.getLANAddresses(mContext);
                    if (ips != null && ips.size() > 0) {
                        byte[] receiveBytes = new byte[SearchProtocol.SERACHER_MAX_RECEIVE_BYTE];
                        DatagramPacket packet = new DatagramPacket(receiveBytes, receiveBytes.length);
                        for (int i = 0; i < ips.size(); ++i) {
                            LogUtils.d(TAG,ips.get(i));
                            sendSearchMessage(InetAddress.getByName(ips.get(i)));
                            try {
                                // 最多接收200个，或超时跳出循环
                                int rspCount = SearchProtocol.RESPONSE_DEVICE_MAX;
                                while (rspCount-- > 0) {
                                    mSocket.receive(packet);
                                    String responseString = SearchProtocol.parseDevicePack(packet);
                                    if (!TextUtils.isEmpty(responseString)) {
                                        LogUtils.d(TAG, "receive : " + responseString);
                                        mDevices.add(responseString);
                                        sendCheckMessage(packet.getAddress(), rspCount);
                                        msg = mHandler.obtainMessage(DEVICE_FOUND_MSG);
                                        msg.obj = responseString;
                                        break;
                                    } else {
                                        LogUtils.d(TAG, "receive error");
                                        msg = mHandler.obtainMessage(DEVICE_FOUND_ERROR_MSG);
                                    }
                                    msg.sendToTarget();
                                }
                            } catch (SocketTimeoutException e) {
                                LogUtils.d(TAG, e.getMessage() == null ? "SocketTimeoutException": e.getMessage());
                            }
                        }
                        LogUtils.d(TAG,"搜索结束");
                        msg = mHandler.obtainMessage(DEVICE_FOUND_END);
                        msg.sendToTarget();
                    }else{
                        LogUtils.d(TAG, "没有获取到同一网络其他主机的IP");
                        msg = mHandler.obtainMessage(ERROR);
                        msg.obj = "没有获取到同一网络其他主机的IP";
                        msg.sendToTarget();
                    }
                }
            } catch (SocketTimeoutException e) {
                if (!mPerMulticast) {
                    Message msg = mHandler.obtainMessage(DEVICE_FOUND_END);
                    msg.sendToTarget();
                }
            } catch (IOException e) {
                Message msg = mHandler.obtainMessage(ERROR);
                msg.obj = e.getMessage();
                msg.sendToTarget();
            }
        }

        public void onDestroy() {
            mSearching = false;
            if (mSocket != null) {
                mSocket.close();
            }
        }
    }
}