package com.heterodain.smartmeter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fazecast.jSerialComm.SerialPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Hello world!
 *
 */
@Slf4j
public class App 
{
    private static final int AMBIENT_CHANNEL = 99999;
    private static final String AMBIENT_WRITE_KEY = "****************";

    private static final String BROUTE_ID = "********************************";
    private static final String BROUTE_PASSWORD = "************";

    private static final String SKVER_COMMAND = "SKVER\r\n";
    private static final String SKSETPWD_COMMAND = "SKSETPWD C %s\r\n";
    private static final String SKSETRBID_COMMAND = "SKSETRBID %s\r\n";
    private static final String SKSCAN_COMMAND = "SKSCAN 2 FFFFFFFF %X\r\n";
    private static final String SKLL64_COMMAND = "SKLL64 %s\r\n";
    private static final String SKSREG_COMMAND = "SKSREG %s %s\r\n";
    private static final String SKJOIN_COMMAND = "SKJOIN %s\r\n";
    private static final String SKSENDTO_COMMAND = "SKSENDTO 1 %s 0E1A 1 %04x ";
    private static final byte[] ECHONET_LITE_FRAME = { 0x10, (byte) 0x81, 0x00, 0x01, 0x05, (byte) 0xFF, 0x01, 0x02, (byte) 0x88, 0x01, 0x62, 0x02, (byte) 0xE7, 0x00, (byte) 0xEA, 0x00 };

    public static void main( final String[] args ) throws Exception {
        SerialPort serial = SerialPort.getCommPort("COM3");
        serial.setBaudRate(115200);
        serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3000, 3000);
        if (!serial.openPort()) {
            throw new IOException("シリアルポートが開けませんでした。");
        }

        try (BufferedReader r = new BufferedReader(new InputStreamReader(serial.getInputStream(), StandardCharsets.ISO_8859_1))) {
            // バージョン番号取得
            writeCommand(serial, SKVER_COMMAND);
            readLine(r);
            readLine(r);
            if (!"OK".equals(readLine(r))) {
                throw new IOException("SKVERコマンドが失敗しました。");
            }

            // Bルートパスワード設定
            writeCommand(serial, String.format(SKSETPWD_COMMAND, BROUTE_PASSWORD));
            readLine(r);
            if (!"OK".equals(readLine(r))) {
                throw new IOException("SKSETPWDコマンドが失敗しました。");
            }

            // BルートID設定
            writeCommand(serial, String.format(SKSETRBID_COMMAND, BROUTE_ID));
            readLine(r);
            if (!"OK".equals(readLine(r))) {
                throw new IOException("SKSETRBIDコマンドが失敗しました。");
            }

            // スマートメーター探索
            serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 20000, 20000);

            Map<String, String> smInfo = new HashMap<>();
            writeCommand(serial, String.format(SKSCAN_COMMAND, 6));
            while (true) {
                String line = readLine(r);
                if (line.startsWith("EVENT 22")) {
                    break;       
                } else if (line.startsWith("  ")) {
                    String[] result = line.trim().split(":");
                    smInfo.put(result[0], result[1]);
                }
            }
            if (!smInfo.containsKey("Channel")) {
                throw new IOException("スマートメーターが見つかりませんでした。");
            }
            log.info("スマートメーターが見つかりました。{}", smInfo);

            serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3000, 3000);

            // アドレス変換(MAC→IPV6)
            writeCommand(serial, String.format(SKLL64_COMMAND, smInfo.get("Addr")));
            readLine(r);
            String address = readLine(r).trim();

            // R7023に通信先スマートメータのチャンネルを設定
            writeCommand(serial, String.format(SKSREG_COMMAND, "S2", smInfo.get("Channel")));
            readLine(r);
            if (!"OK".equals(readLine(r))) {
                throw new IOException("SKSREGコマンドが失敗しました。");
            }

            // R7023に通信先スマートメータのPan IDを設定
            writeCommand(serial, String.format(SKSREG_COMMAND, "S3", smInfo.get("Pan ID")));
            readLine(r);
            if (!"OK".equals(readLine(r))) {
                throw new IOException("SKSREGコマンドが失敗しました。");
            }

            // スマートメータに接続
            serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 10000, 10000);
            writeCommand(serial, String.format(SKJOIN_COMMAND, address));
            readLine(r);
            if (!"OK".equals(readLine(r))) {
                throw new IOException("SKJOINコマンドが失敗しました。");
            }

            while (true) {
                String line = readLine(r);
                if (line.startsWith("EVENT 24")) {
                    throw new IOException("スマートメーター接続に失敗しました。");
                } else if (line.startsWith("EVENT 25")) {
                    break;
                }
            }

            serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3000, 3000);

            while (true) {
                writeEchonetLite(serial, String.format(SKSENDTO_COMMAND, address, ECHONET_LITE_FRAME.length), ECHONET_LITE_FRAME);

                Long power = null;
                Double power30 = null;
                do {
                    String line = readLine(r);
                    if (line.startsWith("ERXUDP")) {
                        String res = line.trim().split(" ")[8];
                        String seoj = res.substring(8, 8 + 6);
                        String esv = res.substring(20, 20 + 2);
                        if ("028801".equals(seoj) && "72".equals(esv)) {
                            int pos = 24;
                            while (pos < res.length()) {
                                String epc = res.substring(pos, pos + 2);
                                pos += 2;
                                int epcSize = Integer.parseInt(res.substring(pos, pos + 2), 16);
                                pos += 2;
                                String epcData = res.substring(pos, pos + epcSize * 2);
                                pos += epcSize * 2;

                                if ("E7".equals(epc)) {
                                    power = Long.parseLong(epcData, 16);
                                    log.info("瞬時電力(W): {}", power);
                                } else if ("EA".equals(epc)) {
                                    power30 = ((double) Long.parseLong(epcData.substring(14), 16)) / 10D;
                                    log.info("30分積算電力(kWh): {}", power30);
                                }
                            }
                        }
                    }
                } while (r.ready());

                if (power != null && power30 != null) {
                    // Ambientに送信
                    URL url = new URL("http://54.65.206.59/api/v2/channels/" + AMBIENT_CHANNEL + "/dataarray");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setDoOutput(true);

                    String json = String.format("{\"writeKey\":\"%s\",\"data\":[{\"d1\":%d,\"d2\":%f}]}", AMBIENT_WRITE_KEY, power, power30);
                    log.debug(json);

                    try {
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(json.getBytes());
                        }
                        conn.getResponseCode();

                    } catch (Exception e) {
                        log.error("Ambientデータ送信に失敗しました。", e);
                    }
                }

                Thread.sleep(60000);
            }
       }
    }

    private static String readLine(BufferedReader r) throws IOException {
        String line = r.readLine();
        log.debug("Recieve: {}", line);
        return line;
    }

    private static void writeCommand(SerialPort serial, String command) throws IOException {
        log.debug("Send: {}", command);
        byte[] bytes = command.getBytes();
        serial.writeBytes(bytes, bytes.length);
    }

    private static void writeEchonetLite(SerialPort serial, String command, byte[] frame) throws IOException {
        log.debug("Send: {}{}", command, Arrays.toString(frame));
        byte[] head = command.getBytes();
        byte[] bytes = ByteBuffer.allocate(head.length + frame.length).put(head).put(frame).array();
        serial.writeBytes(bytes, bytes.length);
    }
}
