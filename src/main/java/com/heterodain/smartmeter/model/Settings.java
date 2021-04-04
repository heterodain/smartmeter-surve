package com.heterodain.smartmeter.model;

import lombok.Getter;
import lombok.ToString;

/**
 * 設定情報
 */
@Getter
@ToString
public class Settings {
    // スマートメーターの設定
    private SmartMeter smartMeter;
    // Ambientの設定(2分値)
    private Ambient ambient1;
    // Ambientの設定(日計値)
    private Ambient ambient2;
    // LineNotifyの設定
    private LineNotify lineNotify;

    /**
     * スマートメーターの設定情報
     */
    @Getter
    @ToString
    public static class SmartMeter {
        // シリアル通信ポート名
        private String comPort;
        // BルートID
        private String brouteId;
        // Bルートパスワード
        private String broutePassword;
    }

    /**
     * Ambientの設定情報
     */
    @Getter
    @ToString
    public static class Ambient {
        // チャネルID
        private Integer channelId;
        // リードキー
        private String readKey;
        // ライトキー
        private String writeKey;
    }

    /**
     * LINE Notifyの設定情報
     */
    @Getter
    @ToString
    public static class LineNotify {
        /** 通知APIのURL */
        private String url;
        /** トークン */
        private String token;
    }
}
