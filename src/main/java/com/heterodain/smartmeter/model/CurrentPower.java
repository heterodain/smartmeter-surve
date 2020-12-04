package com.heterodain.smartmeter.model;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 電力情報
 */
@Data
public class CurrentPower {
    // 瞬時電力(W)
    private Long instantPower;
    // R相電流(0.1A)
    private Long instantRAmp;
    // T相電流(0.1A)
    private Long instantTAmp;
    // 30分積算電力
    private Accumu30Power accumu30;

    /**
     * 30分積算電力情報
     */
    @AllArgsConstructor
    @Data
    public static class Accumu30Power {
        // 時刻
        private ZonedDateTime time;
        // 積算電力(Wh)
        private Long totalPower;
        // 電力(Wh)
        private Long power;
    }
}
