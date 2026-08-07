package com.limou.agent.utils;

import java.math.BigDecimal;

/**
 * 地理计算工具类
 *
 * @author 李振南
 */
public class GeoUtils {

    /** 地球平均半径（米） */
    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /** 地球平均半径（千米） */
    public static final double EARTH_RADIUS_KM = 6_371.0;

    /**
     * Haversine 公式 — 计算两点间距离（米）
     *
     * @param lat1 点1 纬度
     * @param lng1 点1 经度
     * @param lat2 点2 纬度
     * @param lng2 点2 经度
     * @return 距离（米）
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Haversine 公式 — 返回整型米数
     */
    public static int haversineMetersInt(double lat1, double lng1, double lat2, double lng2) {
        return (int) Math.round(haversineMeters(lat1, lng1, lat2, lng2));
    }

    /**
     * Haversine 公式 — 计算两点间距离（千米）
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        return haversineMeters(lat1, lng1, lat2, lng2) / 1000.0;
    }

    // ==================== BigDecimal 重载（兼容服务层） ====================

    /**
     * Haversine 公式 — BigDecimal 参数版（米）
     */
    public static double haversineMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        return haversineMeters(
                lat1.doubleValue(), lng1.doubleValue(),
                lat2.doubleValue(), lng2.doubleValue());
    }

    /**
     * Haversine 公式 — BigDecimal 参数版（千米），兼容 CinemaServiceImpl 旧用法
     */
    public static double haversineKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        return haversineKm(
                lat1.doubleValue(), lng1.doubleValue(),
                lat2.doubleValue(), lng2.doubleValue());
    }

    /**
     * 格式化距离文本
     * @param meters 距离（米）
     * @return "250m" 或 "3.2km"
     */
    public static String formatDistance(int meters) {
        if (meters < 1000) return meters + "m";
        return String.format("%.1fkm", meters / 1000.0);
    }
}
