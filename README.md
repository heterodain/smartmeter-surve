# smartmeter-surve
スマートメーターから電力使用量を取得してグラフ化します。  
(Get Power Information form Smartmeter and graphed in "Ambient")

![Lombok](https://img.shields.io/badge/Lombok-1.18.16-green.svg) 
![Jackson](https://img.shields.io/badge/Jackson-2.11.3-green.svg) 
![JSerialComm](https://img.shields.io/badge/JSerialComm-2.6.2-green.svg)

1. 準備編 (Prepare)  
[![Video1](https://img.youtube.com/vi/6Da_AODUvFU/0.jpg)](https://www.youtube.com/watch?v=6Da_AODUvFU)

2. 接続編 (Connect)  
[![Video2](https://img.youtube.com/vi/Td1jvhxL7qU/0.jpg)](https://www.youtube.com/watch?v=Td1jvhxL7qU)

3. グラフ化編 (Make graph)  
[![Video3](https://img.youtube.com/vi/4SDXv0iHaIc/0.jpg)](https://www.youtube.com/watch?v=4SDXv0iHaIc)

4. ラズパイ編 (Run on Raspberry Pi)  
[![Video4](https://img.youtube.com/vi/L80hP5f4zZ8/0.jpg)](https://www.youtube.com/watch?v=L80hP5f4zZ8)

5. 運用編 (Think long-term operation)  
[![Video5](https://img.youtube.com/vi/Pbwp5v2TdWs/0.jpg)](https://www.youtube.com/watch?v=Pbwp5v2TdWs)

6. 電気料金把握編 (Know electricity bill)  
[![Video6](https://img.youtube.com/vi/EAHJbqvbhXw/0.jpg)](https://www.youtube.com/watch?v=EAHJbqvbhXw)

7. LINEに通知 (Notify "LINE")  
[![Video7](https://img.youtube.com/vi/pkHgJ9DMXXg/0.jpg)](https://www.youtube.com/watch?v=pkHgJ9DMXXg)

[Ambient](https://ambidata.io/)  
[LINE Notify](https://notify-bot.line.me/ja/)

## 必要要件 (Requirement)
- Java 8 以降 (Java 8 or higher)
- Maven

## 使い方 (Usage)
1. PC にUSBドングル(RL7023 stick-d/ips)を接続してください。  
(Connect the USB dongle "RL7023 stick-d / ips" to your PC)

2. settings.jsonを編集して、スマートメーターとWEBサービスの接続情報を記入してください。  
(Edit settings.json and fills connect information of Smartmeter and WEB service)  

3. 実行 (Execute)
    - VS Code 上で実行 (Run on VS Code)  
    App.java を右クリックして実行してください。(Right-click on the App.java and run)

    - ターミナル上で実行 (Run on terminal)
        ```command
        mvn clean package
        java -jar smartmeter-surve-1.0.jar
        ```
