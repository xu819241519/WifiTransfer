package com.goertek.transferlibrary.utils;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by landon.xu on 2017/3/22.
 */

public class IPUtils {

    /**
     * 获取同一局域内的其他主机ip
     * @param context
     * @return
     */
    public static List<String> getLANAddresses(Context context){
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        if(dhcp.ipAddress == 0){
            return null;
        }
        int localIP = dhcp.ipAddress;
        //主机号位数
        int hostBitCount = 32;
        int tempNetMask = dhcp.netmask;
        //计算int中0的位数
        while(tempNetMask != 0){
            tempNetMask &= tempNetMask -1 ;
            hostBitCount--;
        }
//        for (int i = 0; i < 32; i++) {
//            if((tempNetMask & 1) == 0){
//                hostBitCount++;
//            }
//            tempNetMask = tempNetMask >> 1;
//        }
        //主机个数
        int hostCount = 1;
        int baseIP = localIP & dhcp.netmask;
        for(int i = 0; i < hostBitCount; ++i){
            hostCount *= 2;
        }
        int hostMask = 0xffff;
        if(hostBitCount < 16){
            hostMask = 0xf000;
        }else if(hostBitCount < 24){
            hostMask = 0xff00;
        }else if(hostBitCount < 32){
            hostMask = 0xfff0;
        }
        List<String> result = null;
        for (int i = 1; i < hostCount - 1; i++) {
            //排除主机号全为0或者全为1
            int mask = convertLittleEndian(i);
            if((localIP & hostMask) == hostMask || (localIP & hostMask) == 0){
                continue;
            }
            int targetIP = mask | baseIP;
            //排除自身
            if(localIP == targetIP){
                continue;
            }
            if(result == null){
                result = new ArrayList<>();
            }
            result.add(intToIp(targetIP));
        }
        return result;
    }

    private static int convertLittleEndian(int data){
        byte b0 = (byte)data;
        data = data >> 8;
        byte b1 = (byte)data;
        data = data >> 8;
        byte b2 = (byte)data;
        data = data >> 8;
        byte b3 = (byte)data;
        return b3 + (b2 << 8) + (b1 <<16) + (b0 <<24);
    }

    static String intToIp(int i) {

        return (i & 0xFF ) + "." +
                ((i >> 8 ) & 0xFF) + "." +
                ((i >> 16 ) & 0xFF) + "." +
                ( i >> 24 & 0xFF) ;
    }

    public static InetAddress getBroadcastAddress(Context context) throws IOException {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        // handle null somehow

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) (broadcast >> (k * 8));
        return InetAddress.getByAddress(quads);
    }

    public static int getLocalIPInt(Context context){
        //获取wifi服务
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        //判断wifi是否开启
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return wifiInfo.getIpAddress();
    }

    public static String getLocalIP(Context context){
        int ipAddress = getLocalIPInt(context);
        return IPUtils.intToIp(ipAddress);
    }

    /**
     * 判断是否为合法IP
     *
     * @return the ip
     */
    public static boolean isValidIP(String ipAddress) {
        String ip = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";
        Pattern pattern = Pattern.compile(ip);
        Matcher matcher = pattern.matcher(ipAddress);
        return matcher.matches();
    }
}
