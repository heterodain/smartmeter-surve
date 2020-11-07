package com.heterodain.smartmeter.model;

import java.time.ZonedDateTime;

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
    // 30分積算時刻
    private ZonedDateTime accumu30Time;
    // 30分積算電力(Wh)
    private Long accumu30Power;
}
