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
    // Ambientの設定
    private Ambient ambient;

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
}
