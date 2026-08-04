package com.limou.agent.controller;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.limou.agent.common.BaseResponse;
import com.limou.agent.common.ResultUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/geo")
@Slf4j
public class GeoController {
    /**
     * IP 定位 — 当 GPS 被浏览器拦截时兜底
     *
     * 调 ipapi.co 免费接口（无需 Key，1000次/天）
     * 返回 { city, lat, lng }
     */
    @GetMapping("/ip-locate")
    public BaseResponse<Map<String, Object>> ipLocate(HttpServletRequest request) {
        // 1. 获取真实 IP（优先取代理转发的）
        String ip = getClientIp(request);
        // 本地开发时 127.0.0.1 查不出城市，用公网兜底
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {

            ip = ""; // ipapi.co 不传 IP 则用当前公网出口 IP
        }

        // 2. 调 ipapi.co
        String url = StrUtil.isBlank(ip)
                ? "https://ipapi.co/json/"
                : "https://ipapi.co/" + ip + "/json/";
        Map<String, Object> result = new HashMap<>();
        try {
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);

            String city = json.getStr("city");
            Double lat = json.getDouble("latitude");
            Double lng = json.getDouble("longitude");

            if (city != null && lat != null && lng != null) {
                result.put("city", city);
                result.put("lat", lat);
                result.put("lng", lng);
                return ResultUtils.success(result);
            }
        } catch (Exception e) {
            log.warn("IP 定位失败: {}", e.getMessage());
        }

        // 3. 兜底（私网IP默认洛阳）
        result.put("city", "洛阳");
        result.put("lat", 34.62);
        result.put("lng", 112.45);
        return ResultUtils.success(result);
    }

    /**
     * 逆地理编码代理 — 前端不能直接调高德 Web服务 Key
     *
     * @param lat 纬度
     * @param lng 经度
     * @return { city, lat, lng }
     */
    @GetMapping("/reverse")
    public BaseResponse<Map<String, Object>> reverse(@RequestParam double lat,
                                                      @RequestParam double lng) {
        String key = "74bfb724d417db45d5a9ffe7215eb4b1";
        Map<String, Object> result = new HashMap<>();
        try {
            String url = String.format(
                    "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%f,%f&output=json",
                    key, lng, lat);
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSONUtil.parseObj(body);
            if ("1".equals(json.getStr("status")) && json.get("regeocode") != null) {
                JSONObject comp = json.getJSONObject("regeocode")
                        .getJSONObject("addressComponent");
                String city = comp.getStr("city");
                if (StrUtil.isBlank(city)) city = comp.getStr("district");
                if (StrUtil.isBlank(city)) city = comp.getStr("province");
                result.put("city", city != null ? city : "洛阳");
                result.put("lat", lat);
                result.put("lng", lng);
                return ResultUtils.success(result);
            }
            log.warn("逆地理返回异常: {}", body);
        } catch (Exception e) {
            log.warn("逆地理编码失败: {}", e.getMessage());
        }
        result.put("city", "洛阳");
        result.put("lat", lat);
        result.put("lng", lng);
        return ResultUtils.success(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StrUtil.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

