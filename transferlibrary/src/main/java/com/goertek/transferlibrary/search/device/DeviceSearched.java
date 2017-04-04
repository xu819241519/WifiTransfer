package com.goertek.transferlibrary.search.device;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import com.goertek.transferlibrary.search.protocol.SearchProtocol;
import com.goertek.transferlibrary.utils.LogUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

/**
 * 被搜索的设备
 * Created by landon.xu on 2017/3/14.
 */

public class DeviceSearched extends Thread {

    private final String TAG = DeviceSearched.class.getSimpleName();

    private String mSendString;

    private Context mContext;

    private volatile boolean mReceiving = false;

    private MulticastSocket mSocket;

    private static final int DEVICE_SEARCHED = 0;

    private ISearchedListener mSearchedListener;

    public DeviceSearched(Context context) {
        mContext = context;
    }

    public void setSendString(String sendString) {
        mSendString = sendString;
    }

    public void setSearchedListener(ISearchedListener listener) {
        mSearchedListener = listener;
    }

    private DeviceHandler mDeviceHandler = new DeviceHandler(this);

    private static class DeviceHandler extends Handler {
        private WeakReference<DeviceSearched> mDevice;

        public DeviceHandler(DeviceSearched device) {
            mDevice = new WeakReference<>(device);
        }

        @Override
        public void handleMessage(Message msg) {
            DeviceSearched device = mDevice.get();
            if (device != null && device.mSearchedListener != null) {
                switch (msg.what) {
                    case DEVICE_SEARCHED:
                        device.mSearchedListener.onSearched((SocketAddress) msg.obj);
                        break;
                }
            }
        }
    }

    @Override
    public void run() {
        if (mSendString != null) {
            try {
                LogUtils.d(TAG, "create DatagramSocket");
                mSocket = new MulticastSocket(SearchProtocol.DEVICE_FIND_PORT);
                mSocket.joinGroup(InetAddress.getByName(SearchProtocol.BROADCAST_IP));
                LogUtils.d(TAG, "mSocket.setBroadcast true");
                byte[] data = new byte[SearchProtocol.DEVICE_MAX_RECEIVE_BYTE];
                DatagramPacket pack = new DatagramPacket(data, data.length);
                mReceiving = true;
                while (mReceiving) {
                    // 等待主机的搜索
                    LogUtils.d(TAG, "mSocket.receive start");
                    mSocket.receive(pack);
                    LogUtils.d(TAG, "mSocket.receive end");
                    if (SearchProtocol.parseSearchData(pack)) {
                        LogUtils.d(TAG, "pass search");
                        byte[] sendData = SearchProtocol.packDeviceData(mSendString);
                        DatagramPacket sendPack = new DatagramPacket(sendData, sendData.length, pack.getAddress(), pack.getPort());
                        mSocket.send(sendPack);
                        mSocket.setSoTimeout(SearchProtocol.RECEIVE_TIME_OUT);
                        try {
                            mSocket.receive(pack);
                            if (SearchProtocol.parseCheckData(mContext, pack)) {
                                LogUtils.d(TAG, "pass check");
                                Message msg = mDeviceHandler.obtainMessage(DEVICE_SEARCHED);
                                msg.obj = pack.getSocketAddress();
                                msg.sendToTarget();
                            }
                        } catch (SocketTimeoutException e) {
                            LogUtils.d(TAG, "socketTimeout");
                        }
                        mSocket.setSoTimeout(0);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (mSocket != null) {
                    mSocket.close();
                }
            }
        } else {
            LogUtils.d(TAG, "send string is null");
        }
    }

    public void onDestroy() {
        mReceiving = false;
        if (mSocket != null) {
            try {
                mSocket.leaveGroup(InetAddress.getByName(SearchProtocol.BROADCAST_IP));
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket.close();
        }
    }
}
