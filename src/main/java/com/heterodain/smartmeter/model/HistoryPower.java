package com.heterodain.smartmeter.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 電力履歴情報
 */
@Data
public class HistoryPower {
    // 日時
    private LocalDateTime time;
    // 30分積算電力(Wh)
    private List<Long> accumu30Powers = new ArrayList<>();
}
