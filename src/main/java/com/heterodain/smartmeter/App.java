package com.heterodain.smartmeter;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heterodain.smartmeter.device.SmartMeter;
import com.heterodain.smartmeter.model.Power;
import com.heterodain.smartmeter.model.Settings;
import com.heterodain.smartmeter.service.Ambient;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * スマートメーター監視
 */
@Slf4j
public class App {
    // バックグラウンドタスクを動かすためのスレッドプール
    private static ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(2);

    public static void main(final String[] args) throws Exception {
        var om = new ObjectMapper();
        var settings = om.readValue(new File("settings.json"), Settings.class);

        var ambientSettings = settings.getAmbient();
        var ambient = new Ambient(ambientSettings.getChannelId(), ambientSettings.getReadKey(),
                ambientSettings.getWriteKey());

        var smSettings = settings.getSmartMeter();
        try (var smartMeter = new SmartMeter(smSettings.getComPort(), smSettings.getBrouteId(),
                smSettings.getBroutePassword())) {
            smartMeter.init();
            smartMeter.connect();

            var powers = new ArrayList<Power>();

            // 10秒毎にスマートメーターから電力情報読込
            Runnable readSmartMeterTask = () -> {
                try {
                    var power = smartMeter.getCurrentPower();
                    synchronized (powers) {
                        powers.add(power);
                    }

                } catch (InterruptedException ignore) {
                    // NOP
                } catch (Exception e) {
                    log.warn("スマートメーターへのアクセスに失敗しました。", e);
                }
            };
            threadPool.scheduleWithFixedDelay(readSmartMeterTask, 0, 10, TimeUnit.SECONDS);

            // 1分毎にAmbientにデータ送信
            Runnable sendAmbientTask = () -> {
                try {
                    Double rw, tw;
                    synchronized (powers) {
                        // R相の1分間平均電力(W)算出
                        rw = powers.stream().mapToDouble(p -> {
                            double w = p.getInstantPower();
                            long r = p.getInstantRAmp();
                            long t = p.getInstantTAmp();
                            long a = r + t;
                            return a == 0 ? 0D : w * r / a;
                        }).average().orElse(0D);

                        // T相の1分間平均電力(W)算出
                        tw = powers.stream().mapToDouble(p -> {
                            double w = p.getInstantPower();
                            long r = p.getInstantRAmp();
                            long t = p.getInstantTAmp();
                            long a = r + t;
                            return a == 0 ? 0D : w * t / a;
                        }).average().orElse(0D);

                        powers.clear();
                    }
                    ambient.send(rw, tw);

                } catch (Exception e) {
                    log.warn("Ambientへのデータ送信に失敗しました。", e);
                }
            };
            threadPool.scheduleWithFixedDelay(sendAmbientTask, 1, 1, TimeUnit.MINUTES);

            // プログラムが止められるまで待つ : SIGINT(Ctrl + C)
            var wait = new Object();
            synchronized (wait) {
                try {
                    wait.wait();
                } catch (InterruptedException ignore) {
                    // NOP
                }
            }
        }

        threadPool.shutdown();
        threadPool.awaitTermination(15, TimeUnit.SECONDS);
    }
}
