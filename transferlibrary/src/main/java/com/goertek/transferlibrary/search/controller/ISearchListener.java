package com.goertek.transferlibrary.search.controller;

import java.util.List;

/**
 * Created by landon.xu on 2017/3/22.
 */

public interface ISearchListener {

    void onSearchStart();

    void onSearchFinish(List<String> devices);

    void onError(String error);
}
