package com.heterodain.smartmeter.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 電力履歴情報
 */
@Data
public class HistoryPower {
    // 日付
    private LocalDate date;
    // 30分積算電力(Wh)
    private List<Long> accumu30Powers;
}
