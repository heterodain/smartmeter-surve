package com.heterodain.smartmeter;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heterodain.smartmeter.device.SmartMeter;
import com.heterodain.smartmeter.model.CurrentPower;
import com.heterodain.smartmeter.model.HistoryPower;
import com.heterodain.smartmeter.model.Settings;
import com.heterodain.smartmeter.model.CurrentPower.Accumu30Power;
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

    // JSONマッパー
    private static ObjectMapper om = new ObjectMapper();

    public static void main(final String[] args) throws Exception {
        var settings = om.readValue(new File("settings.json"), Settings.class);

        // 1分値送信先のAmbient
        var ambient1Settings = settings.getAmbient1();
        var ambient1 = new Ambient(ambient1Settings.getChannelId(), ambient1Settings.getReadKey(),
                ambient1Settings.getWriteKey());

        // 日計値送信先のAmbient
        var ambient2Settings = settings.getAmbient2();
        var ambient2 = new Ambient(ambient2Settings.getChannelId(), ambient2Settings.getReadKey(),
                ambient2Settings.getWriteKey());

        // スマートメーター接続
        var smSettings = settings.getSmartMeter();
        try (var smartMeter = new SmartMeter(smSettings.getComPort(), smSettings.getBrouteId(),
                smSettings.getBroutePassword())) {
            smartMeter.init();
            smartMeter.connect();

            var powers = new ArrayList<CurrentPower>();

            // 10秒毎にスマートメーターから電力情報読込
            Runnable readSmartMeterTask = () -> {
                try {
                    var power = smartMeter.getCurrentPower();
                    synchronized (powers) {
                        powers.add(power);
                    }

                } catch (InterruptedException ignore) {
                    return;
                } catch (Exception e) {
                    log.warn("スマートメーターへのアクセスに失敗しました。", e);
                }
            };
            threadPool.scheduleWithFixedDelay(readSmartMeterTask, 0, 10, TimeUnit.SECONDS);

            // 1分毎にAmbientにデータ送信
            Runnable sendAmbientTask = () -> {
                if (powers.isEmpty()) {
                    return;
                }

                try {
                    Double rw, tw;
                    Accumu30Power accumu30;
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

                        // 30分積算電力
                        accumu30 = powers.stream().filter(p -> p.getAccumu30() != null).map(p -> p.getAccumu30())
                                .findFirst().orElse(null);
                    }

                    if (accumu30 == null) {
                        // 瞬時電力送信
                        ambient1.send(ZonedDateTime.now(), rw, tw);

                    } else {
                        // 瞬時電力と30分積算電力送信
                        ambient1.send(ZonedDateTime.now(), rw, tw, (double) accumu30.getPower());

                        // 0時0分の30分積算電力を受信したら、スマートメーターから昨日の電力使用量を取得して送信
                        if (accumu30.getTime().getHour() == 0 && accumu30.getTime().getMinute() == 0) {
                            long yesterdayPower;
                            try {
                                HistoryPower yesterday = smartMeter.getBeforeDayPower(1);
                                yesterdayPower = accumu30.getTotalPower() - yesterday.getAccumu30Powers().get(0);

                            } catch (InterruptedException ignore) {
                                return;
                            } catch (Exception e) {
                                log.warn("スマートメーターへのアクセスに失敗しました。", e);
                                return;
                            }

                            ambient2.send(accumu30.getTime().minusDays(1), (double) yesterdayPower);
                        }
                    }

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

            threadPool.shutdown();
            threadPool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }
}
