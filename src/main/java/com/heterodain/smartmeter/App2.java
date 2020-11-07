package com.heterodain.smartmeter;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heterodain.smartmeter.device.SmartMeter;
import com.heterodain.smartmeter.model.HistoryPower;
import com.heterodain.smartmeter.model.Settings;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * 過去45日電力情報取得
 */
@Slf4j
public class App2 {

    private static ObjectMapper om = new ObjectMapper();

    public static void main(final String[] args) throws Exception {
        var settings = om.readValue(new File("settings.json"), Settings.class);

        var smSettings = settings.getSmartMeter();
        try (var smartMeter = new SmartMeter(smSettings.getComPort(), smSettings.getBrouteId(),
                smSettings.getBroutePassword())) {
            smartMeter.init();
            smartMeter.connect();

            for (var beforeDays = 0; beforeDays < 45; beforeDays++) {
                HistoryPower history = smartMeter.getBeforeDayPower(beforeDays);
                long powerOfDay = history.getAccumu30Powers().get(0);
                log.info("{}: {}Wh", history.getTime(), powerOfDay);
                Thread.sleep(5000);
            }
        }
    }
}
