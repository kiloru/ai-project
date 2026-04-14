package com.kilor.demo.controller;

import com.kilor.demo.service.NewsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private NewsService newsService;

    @GetMapping("/hot")
    public Map<String, Object> getHotNews() {
        return newsService.getAllHotNews();
    }

    @GetMapping("/detail")
    public Map<String, Object> getDetail(@RequestParam String url, @RequestParam String source) {
        try {
            String content = null;

            if ("weibo".equals(source)) {
                content = fetchWeiboDetail(url);
            } else if ("bilibili".equals(source)) {
                content = fetchBilibiliDetail(url);
            }

            if (content == null) {
                content = genericFetch(url);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("content", content);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("ok", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    private String fetchWeiboDetail(String url) {
        // Extract keyword from weibo search URL
        Pattern p = Pattern.compile("q=([^&]+)");
        Matcher m = p.matcher(url);
        if (!m.find()) return null;

        String keyword = "";
        try {
            keyword = java.net.URLDecoder.decode(m.group(1), "UTF-8");
            // Use weibo API to search
            String apiUrl = "https://weibo.com/ajax/search/hot?q=" + java.net.URLEncoder.encode(keyword, "UTF-8");
            String body = fetchPage(apiUrl, "https://s.weibo.com/");
            if (body == null) return null;

            JsonNode root = mapper.readTree(body);
            JsonNode items = root.path("data").path("list");
            if (!items.isArray() || items.size() == 0) {
                // Try realtime search
                return "热搜话题: " + keyword + "\n\n该话题为微博热搜词，包含多条相关讨论。请点击下方\"查看原文\"链接前往微博搜索页面查看详细内容。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("热搜话题: ").append(keyword).append("\n\n");
            for (int i = 0; i < Math.min(items.size(), 5); i++) {
                JsonNode item = items.get(i);
                String title = item.path("title").asText("");
                sb.append("- ").append(title).append("\n");
            }
            sb.append("\n点击下方\"查看原文\"链接查看更多讨论。");
            return sb.toString();
        } catch (Exception e) {
            return "热搜话题: " + keyword + "\n\n请点击下方\"查看原文\"链接前往微博搜索页面查看详细内容。";
        }
    }

    private String fetchBilibiliDetail(String url) {
        Pattern p = Pattern.compile("(BV\\w+)");
        Matcher m = p.matcher(url);
        if (!m.find()) return null;

        String bvid = m.group(1);
        try {
            String apiUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
            String body = fetchPage(apiUrl, "https://www.bilibili.com/");
            if (body == null) return null;

            JsonNode root = mapper.readTree(body);
            if (root.path("code").asInt(-1) != 0) return null;

            JsonNode data = root.path("data");
            StringBuilder sb = new StringBuilder();
            sb.append(data.path("title").asText("")).append("\n\n");
            if (!data.path("desc").asText("").isEmpty()) {
                sb.append(data.path("desc").asText("")).append("\n\n");
            }
            JsonNode owner = data.path("owner");
            if (owner.has("name")) {
                sb.append("UP主: ").append(owner.get("name").asText("")).append("\n");
            }
            sb.append("播放: ").append(data.path("stat").path("view").asInt(0)).append("\n");
            sb.append("弹幕: ").append(data.path("stat").path("danmaku").asInt(0)).append("\n");
            sb.append("点赞: ").append(data.path("stat").path("like").asInt(0)).append("\n");
            sb.append("投币: ").append(data.path("stat").path("coin").asInt(0)).append("\n");
            sb.append("收藏: ").append(data.path("stat").path("favorite").asInt(0)).append("\n");
            if (data.path("desc_v2").isArray() && data.path("desc_v2").size() > 0) {
                sb.append("\n视频简介:\n");
                for (JsonNode desc : data.path("desc_v2")) {
                    sb.append(desc.path("raw_text").asText("")).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "视频: " + bvid + "\n\n请点击下方\"查看原文\"链接前往B站观看。";
        }
    }

    private String genericFetch(String urlStr) {
        String body = fetchPage(urlStr, null);
        if (body == null) return null;

        // Try meta description
        Pattern p = Pattern.compile("<meta[^>]+(?:name|property)=\"(?:og:)?description\"[^>]+content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(body);
        if (m.find()) return m.group(1);

        // Try article tag
        p = Pattern.compile("<article[^>]*>(.*?)</article>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        m = p.matcher(body);
        if (m.find()) return stripHtml(m.group(1));

        return "无法自动获取详细内容，请点击下方\"查看原文\"链接前往目标网站查看。";
    }

    private String fetchPage(String urlStr, String referer) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/html, */*");
            conn.setRequestProperty("Accept-Charset", "UTF-8");
            if (referer != null) {
                conn.setRequestProperty("Referer", referer);
            }

            int code = conn.getResponseCode();
            if (code != 200) return null;

            // Try UTF-8 encoding
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return null;
        }
    }

    private String stripHtml(String html) {
        return html.replaceAll("<script[^>]*>.*?</script>", "")
                   .replaceAll("<style[^>]*>.*?</style>", "")
                   .replaceAll("<[^>]+>", "\n")
                   .replaceAll("\\n+", "\n")
                   .replaceAll("^\\n+|\\n+$", "")
                   .trim();
    }
}
