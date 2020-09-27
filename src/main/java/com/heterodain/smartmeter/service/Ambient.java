package com.heterodain.smartmeter.service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.var;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Ambient {
    // JSONマッパー
    private static ObjectMapper om = new ObjectMapper();

    // チャネルID
    private int channelId;
    // READキー
    private String readKey;
    // WRITEキー
    private String writeKey;

    /**
     * コンストラクタ
     * 
     * @param channelId チャンネルID
     * @param readKey   READキー
     * @param writeKey  WRITEキー
     */
    public Ambient(int channelId, String readKey, String writeKey) {
        this.channelId = channelId;
        this.readKey = readKey;
        this.writeKey = writeKey;
    }

    /**
     * チャネルにデータ送信
     * 
     * @param datas 送信データ(最大8個)
     * @throws IOException
     */
    public void send(Double... datas) throws IOException {
        // 送信するJSONを構築
        var rootNode = om.createObjectNode();
        rootNode.put("writeKey", this.writeKey);

        var dataArrayNode = om.createArrayNode();
        var dataNode = om.createObjectNode();
        for (int i = 1; i <= datas.length; i++) {
            if (datas[i - 1] != null) {
                dataNode.put("d" + i, datas[i - 1]);
            }
        }
        dataArrayNode.add(dataNode);
        rootNode.set("data", dataArrayNode);

        var jsonString = om.writeValueAsString(rootNode);

        // HTTP POST
        var url = "http://54.65.206.59/api/v2/channels/" + channelId + "/dataarray";
        log.debug("request > " + url);
        log.debug("body > " + jsonString);

        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        try (var os = conn.getOutputStream()) {
            os.write(jsonString.getBytes(StandardCharsets.UTF_8));
        }
        int resCode = conn.getResponseCode();
        if (resCode != 200) {
            throw new IOException("Ambient Response Code " + resCode);
        }
    }

    /**
     * 1日分のデータ取得
     * 
     * @param date 日付
     * @return 1日分のデータ
     * @throws IOException
     */
    public List<ReadData> read(LocalDate date) throws IOException {
        // HTTP GET
        var url = "http://54.65.206.59/api/v2/channels/" + channelId + "/data?readKey=" + readKey + "&date="
                + date.format(DateTimeFormatter.ISO_DATE);
        log.debug("request > " + url);

        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        var resCode = conn.getResponseCode();
        if (resCode != 200) {
            throw new IOException("Ambient Response Code " + resCode);
        }

        try (var is = conn.getInputStream()) {
            return om.readValue(is, new TypeReference<List<ReadData>>() {
            });
        }
    }

    /**
     * 指定期間のデータ取得
     * 
     * @param start 開始日
     * @param end   終了日
     * @return 指定期間のデータ
     * @throws IOException
     */
    public List<ReadData> read(LocalDateTime start, LocalDateTime end) throws IOException {
        // HTTP GET
        var url = "http://54.65.206.59/api/v2/channels/" + channelId + "/data?readKey=" + readKey;
        url += "&start=" + URLEncoder.encode(start.format(DateTimeFormatter.ISO_DATE_TIME), "UTF-8");
        url += "&end=" + URLEncoder.encode(end.format(DateTimeFormatter.ISO_DATE_TIME), "UTF-8");
        log.debug("request > " + url);

        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        var resCode = conn.getResponseCode();
        if (resCode != 200) {
            throw new IOException("Ambient Response Code " + resCode);
        }

        try (var is = conn.getInputStream()) {
            return om.readValue(is, new TypeReference<List<ReadData>>() {
            });
        }
    }

    /**
     * 直近n個のデータ取得
     * 
     * @param n 取得個数
     * @return 直近n個のデータ
     * @throws IOException
     */
    public List<ReadData> read(int n) throws IOException {
        // HTTP GET
        var url = "http://54.65.206.59/api/v2/channels/" + channelId + "/data?readKey=" + readKey + "&n=" + n;
        log.debug("request > " + url);

        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        var resCode = conn.getResponseCode();
        if (resCode != 200) {
            throw new IOException("Ambient Response Code " + resCode);
        }

        try (var is = conn.getInputStream()) {
            return om.readValue(is, new TypeReference<List<ReadData>>() {
            });
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReadData {
        private String created;
        private Double d1;
        private Double d2;
        private Double d3;
        private Double d4;
        private Double d5;
        private Double d6;
        private Double d7;
        private Double d8;
    }
}
