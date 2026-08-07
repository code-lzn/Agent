package com.limou.agent.ai;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 高德地图 API + MCP Server 联合测试
 *
 * 测试场景：用户问 AI "我现在在哪里" → AI 需要调用定位工具获取精确地址。
 * 当前系统缺失的关键能力：IP定位 / 逆地理编码没有注册为 AI 工具。
 *
 * @author henan
 */
@Slf4j
@SpringBootTest
public class AmapMcpTest {

    @Value("${amap.api-key}")
    private String amapWebServiceKey;

    @Autowired(required = false)
    @Qualifier("mcpToolCallbacks")
    private ToolCallbackProvider mcpToolCallbackProvider;

    // ==================== 高德 Web API 直接测试 ====================

    /**
     * 测试1：高德 IP 定位 API
     * 根据当前网络出口 IP 反查城市和粗略坐标
     */
    @Test
    @DisplayName("高德 IP 定位 — 根据出口 IP 获取城市和坐标")
    void testAmapIpLocate() {
        String url = String.format(
                "https://restapi.amap.com/v3/ip?key=%s&output=json", amapWebServiceKey);

        log.info("=== 高德 IP 定位 ===");
        log.info("请求 URL: https://restapi.amap.com/v3/ip?key=***&output=json");

        String body = HttpUtil.get(url, 10000);
        log.info("响应: {}", body);

        JSONObject json = JSONUtil.parseObj(body);
        assertEquals("1", json.getStr("status"), "高德 API 应返回 status=1");

        // 解析结果
        String province = json.getStr("province");
        String city = json.getStr("city");
        String adcode = json.getStr("adcode");
        String rectangle = json.getStr("rectangle"); // 经纬度矩形范围

        log.info("");
        log.info("----- 定位结果 -----");
        log.info("  省份: {}", province);
        log.info("  城市: {}", city);
        log.info("  区划码: {}", adcode);

        if (rectangle != null && rectangle.contains(";")) {
            String[] parts = rectangle.split(";");
            log.info("  矩形范围: {}", rectangle);
            if (parts.length >= 2) {
                String[] leftBottom = parts[0].split(",");
                String[] rightTop = parts.length > 1 ? parts[1].split(",") : parts[0].split(",");
                double centerLng = (Double.parseDouble(leftBottom[0]) + Double.parseDouble(rightTop[0])) / 2;
                double centerLat = (Double.parseDouble(leftBottom[1]) + Double.parseDouble(rightTop[1])) / 2;
                log.info("  估算中心坐标: lng={}, lat={}", centerLng, centerLat);
                log.info("  https://uri.amap.com/marker?position={},{}", centerLng, centerLat);
            }
        }

        assertNotNull(city, "应能获取到城市");
        log.info("✅ IP 定位成功！城市: {}", city);
    }

    /**
     * 测试2：高德周边搜索（IP定位后搜索附近地标验证位置准确性）
     */
    @Test
    @DisplayName("高德周边搜索 — 验证 IP 定位后的坐标是否准确")
    void testAmapAroundSearch() {
        // 先用 IP 获取粗略位置
        String ipBody = HttpUtil.get(
                String.format("https://restapi.amap.com/v3/ip?key=%s&output=json", amapWebServiceKey), 10000);
        JSONObject ipJson = JSONUtil.parseObj(ipBody);
        String rectangle = ipJson.getStr("rectangle");
        assertNotNull(rectangle, "IP 定位应返回 rectangle");

        String[] rectParts = rectangle.split(";");
        String[] center = rectParts[0].split(",");
        double lng = Double.parseDouble(center[0]);
        double lat = Double.parseDouble(center[1]);

        log.info("=== 周边搜索测试（验证定位精度）===");
        log.info("IP 定位坐标: {}, {}", lng, lat);

        // 搜索周边 POI
        String url = String.format(
                "https://restapi.amap.com/v3/place/around?key=%s&location=%f,%f&radius=1000&output=json&offset=5",
                amapWebServiceKey, lng, lat);
        String body = HttpUtil.get(url, 10000);
        JSONObject json = JSONUtil.parseObj(body);

        if ("1".equals(json.getStr("status")) && json.getJSONArray("pois") != null) {
            log.info("周边 1km 内 POI:");
            for (Object obj : json.getJSONArray("pois")) {
                JSONObject poi = (JSONObject) obj;
                log.info("  📍 {} — {} ({}m)",
                        poi.getStr("name"),
                        poi.getStr("address"),
                        poi.getStr("distance"));
            }
        }
        log.info("✅ 周边搜索成功");
    }

    /**
     * 测试3：高德逆地理编码 — 根据精确坐标反查地址
     * 这是"我在哪里"的核心能力：lat/lng → 地址
     */
    @Test
    @DisplayName("高德逆地理编码 — 坐标反查精确地址")
    void testAmapReverseGeocode() {
        // 用 IP 定位获取坐标
        String ipBody = HttpUtil.get(
                String.format("https://restapi.amap.com/v3/ip?key=%s&output=json", amapWebServiceKey), 10000);
        JSONObject ipJson = JSONUtil.parseObj(ipBody);
        String rectangle = ipJson.getStr("rectangle");
        String[] center = rectangle.split(";")[0].split(",");
        // 取矩形中心
        String[] rb = rectangle.split(";");
        double lng = (Double.parseDouble(rb[0].split(",")[0]) + Double.parseDouble(rb[1].split(",")[0])) / 2;
        double lat = (Double.parseDouble(rb[0].split(",")[1]) + Double.parseDouble(rb[1].split(",")[1])) / 2;

        log.info("=== 逆地理编码 ===");
        log.info("输入坐标: lng={}, lat={}", lng, lat);

        String url = String.format(
                "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%f,%f&output=json&extensions=base",
                amapWebServiceKey, lng, lat);
        String body = HttpUtil.get(url, 10000);
        JSONObject json = JSONUtil.parseObj(body);

        assertEquals("1", json.getStr("status"), "逆地理编码应返回 status=1");

        if (json.get("regeocode") != null) {
            JSONObject regeo = json.getJSONObject("regeocode");
            String formattedAddress = regeo.getStr("formatted_address");
            JSONObject comp = regeo.getJSONObject("addressComponent");

            log.info("");
            log.info("----- 逆地理编码结果 -----");
            log.info("  格式化地址: {}", formattedAddress);
            log.info("  国家: {}", comp.getStr("country"));
            log.info("  省份: {}", comp.getStr("province"));
            log.info("  城市: {}", comp.getStr("city"));
            log.info("  区县: {}", comp.getStr("district"));
            log.info("  街道: {}", comp.getStr("township"));
            log.info("  门牌号: {}", comp.getStr("streetNumber"));
            log.info("  商圈: {}", comp.getStr("businessAreas"));

            assertNotNull(formattedAddress, "应能获取到格式化地址");
            log.info("✅ 逆地理编码成功！地址: {}", formattedAddress);
        }
    }

    /**
     * 测试4：高德地理编码 — 地址 → 坐标
     * 用户说"河南科技大学" → 返回精确经纬度
     */
    @Test
    @DisplayName("高德地理编码 — 地址转坐标")
    void testAmapGeocode() {
        String address = "河南科技大学";

        log.info("=== 地理编码（地址 → 坐标）===");
        log.info("输入地址: {}", address);

        String url = String.format(
                "https://restapi.amap.com/v3/geocode/geo?key=%s&address=%s&output=json",
                amapWebServiceKey, address);
        String body = HttpUtil.get(url, 10000);
        JSONObject json = JSONUtil.parseObj(body);

        assertEquals("1", json.getStr("status"), "地理编码应返回 status=1");

        if (json.getJSONArray("geocodes") != null && !json.getJSONArray("geocodes").isEmpty()) {
            JSONObject geo = json.getJSONArray("geocodes").getJSONObject(0);
            log.info("  格式化地址: {}", geo.getStr("formatted_address"));
            log.info("  省份: {}", geo.getStr("province"));
            log.info("  城市: {}", geo.getStr("city"));
            log.info("  区县: {}", geo.getStr("district"));
            log.info("  区划码: {}", geo.getStr("adcode"));
            log.info("  坐标: {}", geo.getStr("location"));
            log.info("✅ 地理编码成功！");
        } else {
            log.warn("⚠️ 未找到结果，count={}", json.getStr("count"));
        }
    }

    // ==================== MCP Server 连接测试 ====================

    /**
     * 测试5：检查 MCP Server 是否成功连接，列出所有工具
     */
    @Test
    @DisplayName("MCP Server 连接测试 — 列出所有 MCP 工具")
    void testMcpToolsAvailable() {
        log.info("=== MCP Server 工具列表 ===");

        if (mcpToolCallbackProvider == null) {
            log.error("❌ MCP ToolCallbackProvider 为 null！");
            log.error("   可能原因:");
            log.error("   1. @amap/amap-maps-mcp-server 未安装 → 运行: npm install -g @amap/amap-maps-mcp-server");
            log.error("   2. npx 路径不正确 → 检查 mcp-servers.json 中的 command");
            log.error("   3. AMAP_MAPS_API_KEY 无效");
            log.error("   4. MCP Server 启动超时（stdio 通信失败）");
            fail("MCP 未连接，无法测试");
            return;
        }

        ToolCallback[] tools = mcpToolCallbackProvider.getToolCallbacks();
        log.info("MCP 工具数量: {}", tools.length);

        if (tools.length == 0) {
            log.warn("⚠️ MCP 连接成功但返回了 0 个工具");
            log.warn("   检查 @amap/amap-maps-mcp-server 版本和 API Key");
        }

        for (ToolCallback tool : tools) {
            log.info("  🔧 {} — {}", tool.getToolDefinition().name(),
                    tool.getToolDefinition().description());
        }

        // 检查是否有 IP 定位 / 逆地理编码相关工具
        boolean hasIpLocate = Arrays.stream(tools)
                .anyMatch(t -> t.getToolDefinition().name().toLowerCase().contains("ip"));
        boolean hasRegeo = Arrays.stream(tools)
                .anyMatch(t -> t.getToolDefinition().name().toLowerCase().contains("regeo"));
        boolean hasGeocode = Arrays.stream(tools)
                .anyMatch(t -> t.getToolDefinition().name().toLowerCase().contains("geo"));

        log.info("");
        log.info("----- 定位相关工具检查 -----");
        log.info("  IP 定位工具: {}", hasIpLocate ? "✅ 有" : "❌ 缺失（这就是"+"我在哪里"+"不工作的原因！）");
        log.info("  逆地理编码工具: {}", hasRegeo ? "✅ 有" : "❌ 缺失（坐标→地址的核心能力）");
        log.info("  地理编码工具: {}", hasGeocode ? "✅ 有" : "❌ 缺失");

        if (!hasIpLocate && !hasRegeo) {
            log.warn("");
            log.warn("⚠️ AMap MCP Server 不提供 IP定位和逆地理编码工具");
            log.warn("   这意味着 AI 无法回答"+"我现在在哪里"+"这类问题");
            log.warn("   解决方案：需要自己注册本地 Tool，调用高德 Web API");
        }
    }

    // ==================== GeoController 代理接口测试 ====================

    /**
     * 测试6：调项目自己的 /geo/ip-locate 接口
     * 验证从 Controller 层经过 ipapi.co 的定位链路
     */
    @Test
    @DisplayName("项目自有 IP 定位接口 — /geo/ip-locate")
    void testGeoControllerIpLocate() {
        log.info("=== 项目自有 IP 定位接口 ===");
        // 本地测试走 localhost
        String url = "http://localhost:8123/api/geo/ip-locate";
        try {
            String body = HttpUtil.get(url, 10000);
            log.info("响应: {}", body);
            JSONObject json = JSONUtil.parseObj(body);
            if (json.getInt("code", -1) == 0) {
                JSONObject data = json.getJSONObject("data");
                log.info("  城市: {}", data.getStr("city"));
                log.info("  坐标: lat={}, lng={}", data.getDouble("lat"), data.getDouble("lng"));
                log.info("✅ 项目 IP 定位接口正常");
            } else {
                log.warn("⚠️ 接口返回异常: {}", json.getStr("message"));
            }
        } catch (Exception e) {
            log.error("❌ 调用失败 (后端可能未启动): {}", e.getMessage());
        }
    }

    /**
     * 测试7：完整链路模拟 — IP定位 → 逆地理编码 → 获取精确地址
     * 模拟 AI 回答"我在哪里"的完整流程
     */
    @Test
    @DisplayName("完整链路: IP定位 → 逆地理编码 → '我在哪里'的答案")
    void testFullWhereAmIFlow() {
        log.info("");
        log.info("╔══════════════════════════════════════╗");
        log.info("║  模拟 AI 回答"+"我在哪里"+"        ║");
        log.info("╚══════════════════════════════════════╝");
        log.info("");

        // Step 1: IP 定位
        log.info(">>> Step 1: IP 定位");
        String ipUrl = String.format(
                "https://restapi.amap.com/v3/ip?key=%s&output=json", amapWebServiceKey);
        String ipBody = HttpUtil.get(ipUrl, 10000);
        JSONObject ipJson = JSONUtil.parseObj(ipBody);

        String province = ipJson.getStr("province");
        String city = ipJson.getStr("city");
        String rectangle = ipJson.getStr("rectangle");

        log.info("  省份: {}, 城市: {}", province, city);

        // Step 2: 逆地理编码（用 IP 坐标中心点）
        log.info(">>> Step 2: 逆地理编码（坐标 → 地址）");
        String[] rectParts = rectangle.split(";");
        double lng = (Double.parseDouble(rectParts[0].split(",")[0])
                + Double.parseDouble(rectParts[1].split(",")[0])) / 2;
        double lat = (Double.parseDouble(rectParts[0].split(",")[1])
                + Double.parseDouble(rectParts[1].split(",")[1])) / 2;

        String regeoUrl = String.format(
                "https://restapi.amap.com/v3/geocode/regeo?key=%s&location=%f,%f&output=json&extensions=base",
                amapWebServiceKey, lng, lat);
        String regeoBody = HttpUtil.get(regeoUrl, 10000);
        JSONObject regeoJson = JSONUtil.parseObj(regeoBody);

        String address = regeoJson.getJSONObject("regeocode").getStr("formatted_address");

        // Step 3: 周边 POI 验证
        log.info(">>> Step 3: 周边地标验证");
        String aroundUrl = String.format(
                "https://restapi.amap.com/v3/place/around?key=%s&location=%f,%f&radius=500&output=json&offset=3",
                amapWebServiceKey, lng, lat);
        String aroundBody = HttpUtil.get(aroundUrl, 10000);
        JSONObject aroundJson = JSONUtil.parseObj(aroundBody);

        log.info("");
        log.info("═══════════════════════════════════════");
        log.info("  🎯 AI 应该回答:");
        log.info("  您当前大概在: {}", address);
        log.info("  所在城市: {} {}", province, city);
        log.info("  坐标: lng={}, lat={}", String.format("%.6f", lng), String.format("%.6f", lat));

        if (aroundJson.getJSONArray("pois") != null && !aroundJson.getJSONArray("pois").isEmpty()) {
            log.info("  周边地标:");
            for (Object obj : aroundJson.getJSONArray("pois")) {
                JSONObject poi = (JSONObject) obj;
                log.info("    📍 {} ({}) — {}m",
                        poi.getStr("name"), poi.getStr("address"), poi.getStr("distance"));
            }
        }
        log.info("═══════════════════════════════════════");

        assertNotNull(address, "逆地理编码应返回地址");
        log.info("✅ 完整链路测试通过！");
    }
}
