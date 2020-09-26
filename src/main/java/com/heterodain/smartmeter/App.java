package com.heterodain.smartmeter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.heterodain.smartmeter.device.SmartMeter;
import com.heterodain.smartmeter.device.SmartMeter.Power;
import com.heterodain.smartmeter.service.Ambient;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

/**
 * スマートメーター監視
 */
@Slf4j
public class App {
    private static final String COM_PORT = "ttyUSB0";

    private static final int AMBIENT_CHANNEL = 999999;
    private static final String AMBIENT_WRITE_KEY = "************";

    private static final String BROUTE_ID = "*********************************";
    private static final String BROUTE_PASSWORD = "************";

    private static ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(2);

    public static void main(final String[] args) throws Exception {
        var ambient = new Ambient(AMBIENT_CHANNEL, "TODO", AMBIENT_WRITE_KEY);

        try (var smartMeter = new SmartMeter(COM_PORT, BROUTE_ID, BROUTE_PASSWORD)) {
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

                } catch (InterruptedException e) {
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

            // SIGINT(Ctrl + C)で止められるまで待つ
            var wait = new Object();
            synchronized (wait) {
                try {
                    wait.wait();
                } catch (InterruptedException e) {
                    // NOP
                }
            }

            threadPool.shutdown();
            threadPool.awaitTermination(15, TimeUnit.SECONDS);
        }
    }
}
