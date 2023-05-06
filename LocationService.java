package com.hlm.common.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;

import com.hlm.common.interfaces.CommonCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定位服务
 */
@Keep
public class LocationService {

    // 超时标志
    public static final String FLAG_TIMEOUT = "TIMEOUT";
    // 没有开启定位或没有定位服务标志
    public static final String FLAG_NOT_LOCATION = "NOT_LOCATION";

    // 定位状态（0:未开始;1定位中;2:超时）
    private volatile AtomicInteger locationStatus = new AtomicInteger(0);

    // 获取定位服务
    private LocationManager locationManager;
    // 获取当前可用的位置控制器
    private List<String> providers;
    // 当前定位监听
    private List<LocationListener> listeners = new ArrayList<>();
    // 取消当前定位
    private CancellationSignal cancellationSignal;
    // 超时时间
    private long timeout = 0;
    // 回调
    private CommonCallback<Location> callback;

    public LocationService(Context context) {
        // 获取定位服务;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // 获取当前可用的位置控制器
        providers = locationManager.getProviders(true);
    }

    /**
     * 设置超时时间
     * @param time
     * @return
     */
    public LocationService setTimeout(long time) {
        this.timeout = time;
        return this;
    }

    /**
     * 设置回调
     * @param callback
     * @return
     */
    public LocationService setCallback(CommonCallback<Location> callback) {
        this.callback = callback;
        return this;
    }

    /**
     * 开始定位
     * @param minTimeMs
     * @param minDistanceM
     */
    @SuppressLint("MissingPermission")
    public void start(long minTimeMs, float minDistanceM) {
        if (providers.size() == 0) {
            finished(null);
            return;
        }

        locationStatus.set(1);
        startTimer(timeout);
        providers.forEach(provider -> {
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        callback(location);
                    }
                }

                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {

                }

                @Override
                public void onProviderEnabled(String s) {

                }

                @Override
                public void onProviderDisabled(String s) {

                }
            };
//            LocationListener listener = location -> {
//                if (location != null) {
//                    callback(location);
//                }
//            };
            locationManager.requestLocationUpdates(provider, minTimeMs, minDistanceM, listener);
            listeners.add(listener);
        });

    }

    /**
     * 取得当前定位
     * @param context
     */
    @SuppressLint("MissingPermission")
    public void current(Context context) {
        if (providers.size() == 0) {
            finished(null);
            return;
        }

        locationStatus.set(1);
        startTimer(timeout);
        providers.forEach(provider -> {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                cancellationSignal = new CancellationSignal();
//                locationManager.getCurrentLocation(provider, cancellationSignal, context.getMainExecutor(), location -> {
//                    if (location != null) {
//                        finished(location);
//                    }
//                });
//            } else {
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        finished(location);
                    }
                }

                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {

                }

                @Override
                public void onProviderEnabled(String s) {

                }

                @Override
                public void onProviderDisabled(String s) {

                }
            };
//                LocationListener listener = location -> {
//                    if (location != null) {
//                        finished(location);
//                    }
//                };
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
                listeners.add(listener);
//            }
        });
    }

    /**
     * 取得最后一次定位
     */
    @SuppressLint("MissingPermission")
    public static Location last(Context context) {
        // 获取定位服务
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // 获取当前可用的位置控制器
        List<String> providers = locationManager.getProviders(true);

        if (providers.size() == 0) {
            return null;
        }

        TreeMap<Integer, Location> map = new TreeMap<>();
        for (String provider : providers) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                switch (provider) {
                    case "gps":
                        map.put(1, location);
                        break;
                    case "network":
                        map.put(2, location);
                        break;
                    case "passive":
                        map.put(3, location);
                        break;
                    default:
                        map.put(4, location);
                }
            }
        }

        if (map.size() > 0) {
            return map.firstEntry().getValue();
        }
        return null;
    }

    /**
     * 开始定时，计算超时
     * @param timeout
     */
    private void startTimer(long timeout) {
        if (timeout <= 0) {
            return;
        }

        new Handler().postDelayed(() -> {
            if (locationStatus.get() == 1) {
                // 超时了
                locationStatus.set(2);
                // 移除监听
                stop();
                finished(null);
            }
        }, timeout * 1000);
    }

    /**
     * 回调
     * @param location
     */
    private void callback(Location location) {
        if (callback == null) {
            return;
        }

        if (location == null) {
            if (locationStatus.get() == 2) {
                // 超时
                callback.failed(FLAG_TIMEOUT);
            } else {
                // 没有开启定位或没有定位服务
                callback.failed(FLAG_NOT_LOCATION);
            }
            return;
        }

        callback.successed(location);
    }

    /**
     * 定位完成
     * @param location
     */
    private void finished(Location location) {
        callback(location);
        stop();
        locationStatus.set(0);
        callback = null;
    }

    /**
     * 停止
     */
    public void stop() {
        listeners.forEach(listener -> locationManager.removeUpdates(listener));
        listeners.clear();
        if (cancellationSignal != null && !cancellationSignal.isCanceled()) {
            cancellationSignal.cancel();
            cancellationSignal = null;
        }
    }

}
