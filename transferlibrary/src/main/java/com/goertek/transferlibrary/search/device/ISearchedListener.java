package com.goertek.transferlibrary.search.device;

import java.net.SocketAddress;

/**
 * Created by landon.xu on 2017/3/23.
 */

public interface ISearchedListener {

    void onSearched(SocketAddress address);
}
