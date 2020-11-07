package com.heterodain.smartmeter.device;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.var;
import lombok.extern.slf4j.Slf4j;

import com.fazecast.jSerialComm.SerialPort;
import com.heterodain.smartmeter.model.CurrentPower;
import com.heterodain.smartmeter.model.HistoryPower;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

@Slf4j
public class SmartMeter implements Closeable {
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    // コマンド
    private static final String SKSETPWD_COMMAND = "SKSETPWD C %s";
    private static final String SKSETRBID_COMMAND = "SKSETRBID %s";
    private static final String SKSCAN_COMMAND = "SKSCAN 2 FFFFFFFF %X";
    private static final String SKLL64_COMMAND = "SKLL64 %s";
    private static final String SKSREG_COMMAND = "SKSREG %s %s";
    private static final String SKJOIN_COMMAND = "SKJOIN %s";
    private static final String SKSENDTO_COMMAND = "SKSENDTO 1 %s 0E1A 1 %04x ";

    // Echonet Lite電文: 瞬時電力、瞬時電流、30分積算電力取得
    private static final String EL_READ_POWER_FRAME = "1081000105FF010288016203E700E800EA00";
    // Echonet Lite電文: 積算履歴収集日１設定
    private static final String EL_WRITE_HISTORY1_DAY_FRAME = "1081000105FF010288016001E501%02x";
    // Echonet Lite電文: 積算電力量計測値履歴１取得
    private static final String EL_READ_HISTORY1_FRAME = "1081000105FF010288016201E200";

    // シリアルポート名
    private String serialPortName;
    // BルートID
    private String brouteId;
    // Bルートパスワード
    private String broutePassword;

    // シリアルポート
    private SerialPort serial;
    // シリアル入力ストリーム
    private BufferedReader in;

    // スマートメーター情報
    private Map<String, String> smartMeterInfo = new HashMap<>();
    // スマートメーターのIPV6アドレス
    private String address;

    // 最後に取得した30分積算値の時刻
    private ZonedDateTime lastAccumu30Time;

    /**
     * コンストラクタ
     * 
     * @param serialPortName シリアルポート名
     * @param brouteId       BルートID
     * @param broutePassword Bルートパスワード
     */
    public SmartMeter(String serialPortName, String brouteId, String broutePassword) {
        this.serialPortName = serialPortName;
        this.brouteId = brouteId;
        this.broutePassword = broutePassword;
    }

    /**
     * スマートメーターに接続するための初期パラメータ設定
     * 
     * @throws IOException
     */
    public void init() throws IOException {
        log.info("スマートメーターをスキャンします...");

        serial = SerialPort.getCommPort(serialPortName);
        serial.setBaudRate(115200);
        serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3000, 3000);
        if (!serial.openPort()) {
            throw new IOException("シリアルポート[" + serialPortName + "]を開けませんでした。");
        }

        in = new BufferedReader(new InputStreamReader(serial.getInputStream(), StandardCharsets.ISO_8859_1));

        writeCommand(SKSETPWD_COMMAND, broutePassword);
        if (!awaitResponse("OK", "FAIL").contains("OK")) {
            throw new IOException("SKSETPWDコマンドが失敗しました。");
        }

        writeCommand(SKSETRBID_COMMAND, brouteId);
        if (!awaitResponse("OK", "FAIL").contains("OK")) {
            throw new IOException("SKSETRBIDコマンドが失敗しました。");
        }
    }

    /**
     * スマートメーターに接続
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    public void connect() throws IOException, InterruptedException {
        serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 20000, 20000);

        writeCommand(SKSCAN_COMMAND, 6);
        smartMeterInfo = awaitResponse("EVENT 22").stream().filter(r -> r.startsWith("  "))
                .map(r -> r.trim().split(":")).collect(Collectors.toMap(r -> r[0], r -> r[1]));
        if (!smartMeterInfo.containsKey("Channel")) {
            throw new IOException("スマートメーターが見つかりませんでした。");
        }

        log.info("スマートメーターが見つかりました。 {}", smartMeterInfo);

        serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 5000, 5000);

        writeCommand(SKLL64_COMMAND, smartMeterInfo.get("Addr"));
        address = awaitResponse("FE80:").stream().reduce((a, b) -> b).get().trim();

        writeCommand(SKSREG_COMMAND, "S2", smartMeterInfo.get("Channel"));
        if (!awaitResponse("OK", "FAIL").contains("OK")) {
            throw new IOException("SKSREGコマンドが失敗しました。");
        }

        writeCommand(SKSREG_COMMAND, "S3", smartMeterInfo.get("Pan ID"));
        if (!awaitResponse("OK", "FAIL").contains("OK")) {
            throw new IOException("SKSREGコマンドが失敗しました。");
        }

        writeCommand(SKJOIN_COMMAND, address);
        var responses = awaitResponse("EVENT 24", "EVENT 25", "FAIL");
        if (!responses.contains("OK")) {
            throw new IOException("SKJOINコマンドが失敗しました。");
        } else if (responses.contains("EVENT 24")) {
            throw new IOException("PANA接続に失敗しました。");
        }

        log.info("スマートメーターに接続しました。");
    }

    /**
     * 現在の電力量取得
     * 
     * @return 電力情報
     * @throws IOException
     * @throws InterruptedException
     * @throws DecoderException
     */
    public synchronized CurrentPower getCurrentPower() throws IOException, InterruptedException, DecoderException {
        writeEchonetLite(EL_READ_POWER_FRAME);

        CurrentPower result = processResponse(data -> {
            if (data.startsWith("ERXUDP")) {
                var res = data.trim().split(" ")[8];
                var seoj = res.substring(8, 8 + 6);
                var esv = res.substring(20, 20 + 2);
                if ("028801".contentEquals(seoj) && "72".equals(esv)) {
                    var power = new CurrentPower();

                    var pos = 24;
                    while (pos < res.length()) {
                        var epc = res.substring(pos, pos + 2);
                        pos += 2;
                        var epcSize = Integer.parseInt(res.substring(pos, pos + 2), 16);
                        pos += 2;
                        var epcData = res.substring(pos, pos + epcSize * 2);
                        pos += epcSize * 2;

                        if ("E7".equals(epc)) {
                            power.setInstantPower(epcData.startsWith("FF") ? 0L : Long.parseLong(epcData, 16));
                            // 稀にマイナス値(FF...)が返ることがある。モーターなどから逆流しているのかも。

                        } else if ("E8".equals(epc)) {
                            power.setInstantRAmp(Long.parseLong(epcData.substring(0, 4), 16));
                            power.setInstantTAmp(Long.parseLong(epcData.substring(4), 16));
                            // 1A単位でしか取得できない

                        } else if ("EA".equals(epc)) {
                            var year = Integer.parseInt(epcData.substring(0, 4), 16);
                            var month = Integer.parseInt(epcData.substring(4, 6), 16);
                            var day = Integer.parseInt(epcData.substring(6, 8), 16);
                            var hour = Integer.parseInt(epcData.substring(8, 10), 16);
                            var min = Integer.parseInt(epcData.substring(10, 12), 16);
                            var sec = Integer.parseInt(epcData.substring(12, 14), 16);
                            var time = ZonedDateTime.of(year, month, day, hour, min, sec, 0, JST);
                            var power30 = Long.parseLong(epcData.substring(14), 16) * 100;
                            if (!time.equals(lastAccumu30Time)) {
                                power.setAccumu30Time(time);
                                power.setAccumu30Power(power30);
                                lastAccumu30Time = time;
                            }
                        }
                    }
                    return power;
                }
            }
            return null;
        });
        return result;
    }

    /**
     * 以前の電力履歴取得
     * 
     * @param beforeDays 遡る日数
     * @return 電力履歴情報
     * @throws IOException
     * @throws InterruptedException
     * @throws DecoderException
     */
    public synchronized HistoryPower getBeforeDayPower(int beforeDays)
            throws IOException, InterruptedException, DecoderException {
        writeEchonetLite(String.format(EL_WRITE_HISTORY1_DAY_FRAME, beforeDays));
        writeEchonetLite(EL_READ_HISTORY1_FRAME);

        HistoryPower result = processResponse(data -> {
            if (data.startsWith("ERXUDP")) {
                var res = data.trim().split(" ")[8];
                var seoj = res.substring(8, 8 + 6);
                var esv = res.substring(20, 20 + 2);
                if ("028801".contentEquals(seoj) && "72".equals(esv)) {
                    var history = new HistoryPower();

                    var pos = 24;
                    while (pos < res.length()) {
                        var epc = res.substring(pos, pos + 2);
                        pos += 2;
                        var epcSize = Integer.parseInt(res.substring(pos, pos + 2), 16);
                        pos += 2;
                        var epcData = res.substring(pos, pos + epcSize * 2);
                        pos += epcSize * 2;

                        if ("E2".equals(epc)) {
                            var time = ZonedDateTime.now(JST).minusDays(beforeDay).truncatedTo(ChronoUnit.DAYS);
                            history.setTime(time);
                            for (var epcDataPos = 4; epcDataPos < epcSize * 2; epcDataPos += 8) {
                                history.getAccumu30Powers()
                                        .add(Long.parseLong(epcData.substring(epcDataPos, epcDataPos + 8), 16) * 100);
                            }
                        }
                    }
                    return history;
                }
            }
            return null;
        });

        return result;
    }

    /**
     * シリアルポートを閉じる
     */
    @Override
    public void close() throws IOException {
        if (in != null) {
            in.close();
        }
        if (serial != null && serial.isOpen()) {
            serial.closePort();
        }
    }

    /**
     * スマートメーターからの応答を処理する
     * 
     * @param <R>  データ型
     * @param func 応答を処理する関数
     * @return 応答データ
     * @throws IOException
     * @throws InterruptedException
     */
    private <R> R processResponse(Function<String, R> func) throws IOException, InterruptedException {
        R result = null;
        do {
            String data;
            try {
                data = readLine();
            } catch (Exception e) {
                if (result == null) {
                    // スマートメーターから応答がなかった場合は再接続する
                    connect();
                    throw e;
                }
                break;
            }
            result = func.apply(data);

        } while (in.ready() || result == null);

        log.debug("{}", result);

        return result;
    }

    /**
     * シリアルポートから1行読み込む
     * 
     * @return 読み込んだ文字列
     * @throws IOException
     */
    private String readLine() throws IOException {
        var line = in.readLine();
        log.trace("Receive: {}", line);
        return line;
    }

    /**
     * シリアルポートにコマンド送信
     * 
     * @param command コマンド文字列
     * @param args    コマンドのパラメータ
     * @throws IOException
     */
    private void writeCommand(String command, Object... args) throws IOException {
        var data = String.format(command, args);
        log.trace("Send: {}", data);

        var bytes = (data + "\r\n").getBytes(StandardCharsets.ISO_8859_1);
        serial.writeBytes(bytes, bytes.length);
    }

    /**
     * シリアルポートから特定の文字列が返るまで待つ
     * 
     * @param aborts 特定の文字列
     * @return シリアルポートから読み込んだ文字列
     * @throws IOException
     */
    private List<String> awaitResponse(String... aborts) throws IOException {
        var results = new ArrayList<String>();
        while (true) {
            var line = readLine();
            results.add(line);
            if (Arrays.stream(aborts).map(a -> line.startsWith(a)).reduce((a, b) -> a | b).orElse(false)) {
                break;
            }
        }
        return results;
    }

    /**
     * シリアルポートにEchonet Lite電文を送信
     * 
     * @param frame Echonet Lite電文
     * @throws IOException
     * @throws DecoderException
     */
    private void writeEchonetLite(String frame) throws IOException, DecoderException {
        var frameBytes = Hex.decodeHex(frame);
        var data = String.format(SKSENDTO_COMMAND, address, frameBytes.length);
        log.trace("Send: {}{}", data, frame);

        var bytes = data.getBytes(StandardCharsets.ISO_8859_1);
        bytes = ByteBuffer.allocate(bytes.length + frameBytes.length).put(bytes).put(frameBytes).array();
        serial.writeBytes(bytes, bytes.length);
    }
}
