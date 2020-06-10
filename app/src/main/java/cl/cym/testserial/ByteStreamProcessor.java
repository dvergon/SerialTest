package cl.cym.testserial;

import android.util.Log;

import java.io.IOException;

import static android.content.ContentValues.TAG;

public class ByteStreamProcessor implements Runnable {

    private static ByteStreamProcessor streamProcessor;
    private static SerialComms serialComms;
    //D1,D2,D3
    //Bill, NFC Pay, NFC Recharge
    private static int[] deviceStatuses = new int[3];
    private static int[] prices = new int[9];
    private static int[] billValues = new int[16];

    private static byte[] currentPaymentUID;
    private static byte[] currentRechargeStream;

    private static int nfcPaymentCardType;
    private static int nfcRechargeCardType;

    public ByteStreamProcessor(SerialComms sc){

        ByteStreamProcessor.serialComms = sc;

        ByteStreamProcessor.currentPaymentUID = new byte[1];
        ByteStreamProcessor.currentPaymentUID[0] = 0;

        ByteStreamProcessor.currentRechargeStream = new byte[12];

        ByteStreamProcessor.deviceStatuses[0] = 0;
        ByteStreamProcessor.deviceStatuses[1] = 0;
        ByteStreamProcessor.deviceStatuses[2] = 0;

        ByteStreamProcessor.nfcPaymentCardType = -1;
        ByteStreamProcessor.nfcRechargeCardType = -1;

        ByteStreamProcessor.prices[0] = 0;
        ByteStreamProcessor.prices[1] = 170;
        ByteStreamProcessor.prices[2] = 170;
        ByteStreamProcessor.prices[3] = 500;
        ByteStreamProcessor.prices[4] = 400;
        ByteStreamProcessor.prices[5] = 0;
        ByteStreamProcessor.prices[6] = 0;
        ByteStreamProcessor.prices[7] = 0;
        ByteStreamProcessor.prices[8] = 0;

        ByteStreamProcessor.billValues[0] = 1000;
        ByteStreamProcessor.billValues[1] = 2000;
        ByteStreamProcessor.billValues[2] = 5000;
        ByteStreamProcessor.billValues[3] = 10000;
        ByteStreamProcessor.billValues[4] = 20000;
    }

    public static ByteStreamProcessor getInstance(SerialComms sc){

        if(streamProcessor == null){

            streamProcessor = new ByteStreamProcessor(sc);
        }

        return streamProcessor;
    }

    public static ByteStreamProcessor getInstance(){

        return streamProcessor;
    }

    @Override
    public void run(){

        while(!Thread.interrupted()){

            int pendingProcessSize = 0;

            synchronized (SerialComms.getInstance()){

                pendingProcessSize = SerialComms.getPendingProcessStreams().size();
            }

            if(pendingProcessSize > 0){

                ByteStream currentByteStream = new ByteStream(new byte[1], "default");

                synchronized (SerialComms.getInstance()){
                    currentByteStream = SerialComms.getPendingProcessStreams().poll();
                }

                byte[] currentStream = currentByteStream.getStream();
                byte[] streamContent;
                byte[] contentCRC;
                boolean validCRC;

                //check for first byte to be 127
                if(ByteHandleUtils.byteToInt(currentStream[0]) == 127){

                    streamContent = getStreamContent(currentStream);

                    //Log.v("streamProc", "streamLength: "+streamContent.length);

                    if(streamContent.length > 0){

                        //check command byte
                        switch(ByteHandleUtils.byteToInt(streamContent[0])){

                            case 83:
                                //POLL RESPONSE
                                //EXPECTS 127 83 DN S1 S2 S3...SN MSB LSB
                                //N = number of devices
                                //SN = Status of device number N
                                //MSB = Most significant byte of CRC16
                                //LSB = Least significant byte of CRC16

                                //get stream content and CRC of it
                                streamContent = getStreamContent(currentStream);
                                contentCRC = ByteHandleUtils.calculateCRC16(streamContent);
                                validCRC = (currentStream[currentStream.length-2] == contentCRC[0]) && (currentStream[currentStream.length-1] == contentCRC[1]);

                                //if valid CRC, process data
                                if(validCRC){

                                    //3 switches with the logic for each stream type for each device (currently 3)
                                    //First check for the NFCs status
                                    //Streams will be processed in this order
                                    //NFC Payment -> NFC Recharge -> Bill

                                    //checks if payment NFC has activity

                                    //D2: NFC Payment
                                    //Possible status
                                    //1: waiting (NFC card/client not detected)
                                    //11...19: NFC card/client detected
                                    //  1: TNE Basica
                                    //  2: TNE Media
                                    //  3: TNE Superior
                                    //  4: Prepago general
                                    //  5: Adulto mayor

                                    if(ByteHandleUtils.byteToInt(streamContent[3]) > 1){

                                        //change to reading card if idle
                                        if(ByteStreamProcessor.deviceStatuses[1] == 0){

                                            ByteStreamProcessor.deviceStatuses[1] = 1;
                                        }

                                        switch(ByteStreamProcessor.deviceStatuses[1]){
                                            case 0:
                                                //idle
                                                break;
                                            case 1:
                                                //card detected
                                                //if card detected, get balance
                                                //127 67 DN 83 MSB LSB
                                                byte[] saldoStream = new byte[3];
                                                saldoStream[0] = ByteHandleUtils.intToByte(67);
                                                saldoStream[1] = ByteHandleUtils.intToByte(2);
                                                saldoStream[2] = ByteHandleUtils.intToByte(83);

                                                ByteStreamProcessor.setNfcPaymentCardType(ByteHandleUtils.byteToInt(streamContent[3]));

                                                synchronized (SerialComms.getInstance()){

                                                    byte[] finalStream = SerialComms.formatStream(saldoStream, true);

                                                    try {
                                                        SerialComms.setWriting(true);
                                                        SerialComms.setWaitingCommandResponse(true);
                                                        SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                        SerialComms.listAction("Tarjeta detectada en pago, pidiendo saldo - "+ByteHandleUtils.byteArrayToString(finalStream));
                                                        SerialComms.serialWrite(finalStream);
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                        SerialComms.setWriting(false);
                                                    }
                                                }

                                                ByteStreamProcessor.deviceStatuses[1] = 2;

                                                break;
                                            case 2:
                                                //balance request waiting for D2 response
                                                /*synchronized (SerialComms.getInstance()){
                                                    SerialComms.listAction("Esperando saldo en pago");
                                                }*/
                                                break;
                                            case 3:
                                                //charge sent
                                                break;
                                            case 4:
                                                //balance post charge
                                                break;
                                        }
                                    }else{

                                        ByteStreamProcessor.deviceStatuses[1] = 0;
                                    }

                                    //D3: NFC Recharge
                                    //Possible status
                                    //1: waiting (NFC card/client not detected)
                                    //11...19: NFC card/client detected
                                    //  1: TNE Basica
                                    //  2: TNE Media
                                    //  3: TNE Superior
                                    //  4: Prepago general
                                    //  5: Adulto mayor

                                    //D1: Bill acceptor
                                    //Possible status
                                    //100: ready
                                    //101: busy
                                    //1...16: bill waiting to be accepted/rejected
                                    //  1 = $1.000
                                    //  2 = $2.000
                                    //  3 = $5.000
                                    //  4 = $10.000

                                    //D1 and D2 work in tandem, so both of them will be sharing the same control structure
                                    //D1 acts as an slave, depending on D3 status D1 reacts

                                    if(ByteHandleUtils.byteToInt(streamContent[4]) > 1){

                                        //change to reading card if idle
                                        if(ByteStreamProcessor.deviceStatuses[2] == 0){

                                            ByteStreamProcessor.deviceStatuses[2] = 1;
                                        }

                                        switch(ByteStreamProcessor.deviceStatuses[2]){
                                            case 0:
                                                //NFC idle

                                                //if nfc is idle, send reject if bill has something in it
                                                if(ByteHandleUtils.byteToInt(streamContent[2]) >= 1 && ByteHandleUtils.byteToInt(streamContent[2]) <= 16){

                                                    byte[] rejectStream = new byte[3];
                                                    rejectStream[0] = ByteHandleUtils.intToByte(67);
                                                    rejectStream[1] = ByteHandleUtils.intToByte(1);
                                                    rejectStream[2] = ByteHandleUtils.intToByte(82);

                                                    synchronized (SerialComms.getInstance()){

                                                        byte[] finalStream = SerialComms.formatStream(rejectStream, true);

                                                        try {
                                                            SerialComms.setWriting(true);
                                                            SerialComms.listAction("Rechazando billete - "+ByteHandleUtils.byteArrayToString(streamContent));
                                                            SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                            SerialComms.serialWrite(finalStream);
                                                        } catch (IOException e) {
                                                            e.printStackTrace();
                                                            SerialComms.setWriting(false);
                                                        }
                                                    }

                                                    ByteStreamProcessor.deviceStatuses[0] = 0;
                                                }

                                                break;
                                            case 1:
                                                //card detected
                                                //if card detected, get balance
                                                //127 67 DN 83 MSB LSB
                                                byte[] saldoStream = new byte[3];
                                                saldoStream[0] = ByteHandleUtils.intToByte(67);
                                                saldoStream[1] = ByteHandleUtils.intToByte(3);
                                                saldoStream[2] = ByteHandleUtils.intToByte(83);

                                                synchronized (SerialComms.getInstance()){

                                                    byte[] finalStream = SerialComms.formatStream(saldoStream, true);

                                                    try {
                                                        SerialComms.listAction("Tarjeta detectada en recarga, pidiendo saldo - "+ByteHandleUtils.byteArrayToString(finalStream));
                                                        SerialComms.setWriting(true);
                                                        SerialComms.setWaitingCommandResponse(true);
                                                        SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                        SerialComms.serialWrite(finalStream);
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
                                                        SerialComms.setWriting(false);
                                                    }
                                                }

                                                ByteStreamProcessor.deviceStatuses[2] = 2;

                                                //if, while asking for balance, bill acceptor has something in it, send reject
                                                if(ByteHandleUtils.byteToInt(streamContent[2]) >= 1 && ByteHandleUtils.byteToInt(streamContent[2]) <= 16){

                                                    byte[] rejectStream = new byte[3];
                                                    rejectStream[0] = ByteHandleUtils.intToByte(67);
                                                    rejectStream[1] = ByteHandleUtils.intToByte(1);
                                                    rejectStream[2] = ByteHandleUtils.intToByte(82);

                                                    synchronized (SerialComms.getInstance()){

                                                        byte[] finalStream = SerialComms.formatStream(rejectStream, true);

                                                        try {
                                                            SerialComms.listAction("Rechazando billete - "+ByteHandleUtils.byteArrayToString(streamContent));
                                                            SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                            SerialComms.serialWrite(finalStream);
                                                        } catch (IOException e) {
                                                            e.printStackTrace();
                                                            SerialComms.setWriting(false);
                                                        }
                                                    }

                                                    ByteStreamProcessor.deviceStatuses[0] = 0;
                                                }
                                                break;
                                            case 2:
                                                //balance request waiting for D3 response
                                                synchronized (SerialComms.getInstance()){
                                                    SerialComms.listAction("Esperando saldo en recarga");
                                                }

                                                //if, while asking for balance, bill acceptor has something in it, send reject
                                                if(ByteHandleUtils.byteToInt(streamContent[2]) >= 1 && ByteHandleUtils.byteToInt(streamContent[2]) <= 16){

                                                    byte[] rejectStream = new byte[3];
                                                    rejectStream[0] = ByteHandleUtils.intToByte(67);
                                                    rejectStream[1] = ByteHandleUtils.intToByte(1);
                                                    rejectStream[2] = ByteHandleUtils.intToByte(82);

                                                    synchronized (SerialComms.getInstance()){

                                                        byte[] finalStream = SerialComms.formatStream(rejectStream, true);

                                                        try {
                                                            SerialComms.listAction("Rechazando billete - "+ByteHandleUtils.byteArrayToString(streamContent));
                                                            SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                            SerialComms.serialWrite(finalStream);
                                                        } catch (IOException e) {
                                                            e.printStackTrace();
                                                            SerialComms.setWriting(false);
                                                        }
                                                    }

                                                    ByteStreamProcessor.deviceStatuses[0] = 0;
                                                }
                                                break;
                                            case 3:
                                                //recharge logic after 82 received
                                                //if bill acceptor has something in it, accept and send recharge stream
                                                if(ByteHandleUtils.byteToInt(streamContent[2]) >= 1 && ByteHandleUtils.byteToInt(streamContent[2]) <= 16){

                                                    byte[] acceptStream = new byte[3];
                                                    acceptStream[0] = ByteHandleUtils.intToByte(67);
                                                    acceptStream[1] = ByteHandleUtils.intToByte(1);
                                                    acceptStream[2] = ByteHandleUtils.intToByte(65);

                                                    synchronized (SerialComms.getInstance()){

                                                        byte[] finalStream = SerialComms.formatStream(acceptStream, true);

                                                        try {
                                                            SerialComms.listAction("Aceptando billete de "+billValues[ByteHandleUtils.byteToInt(streamContent[2])-1]+" - "+ByteHandleUtils.byteArrayToString(finalStream));
                                                            SerialComms.serialWrite(finalStream);

                                                            ByteStreamProcessor.deviceStatuses[0] = 0;

                                                            Thread.sleep(100);

                                                            byte[] oldBalanceBytes = new byte[3];
                                                            oldBalanceBytes[0] = ByteHandleUtils.intToByte(0);
                                                            oldBalanceBytes[1] = currentRechargeStream[3];
                                                            oldBalanceBytes[2] = currentRechargeStream[4];

                                                            int oldBalance = ByteHandleUtils.threeByteArrayToInteger(oldBalanceBytes);

                                                            currentRechargeStream[3] = ByteHandleUtils.intToByteArray(oldBalance + billValues[ByteHandleUtils.byteToInt(streamContent[2])-1], 2)[1];
                                                            currentRechargeStream[4] = ByteHandleUtils.intToByteArray(oldBalance + billValues[ByteHandleUtils.byteToInt(streamContent[2])-1], 2)[0];



                                                            finalStream = SerialComms.formatStream(currentRechargeStream, true);

                                                            SerialComms.setWriting(true);
                                                            SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                            SerialComms.listAction("Recargando "+billValues[ByteHandleUtils.byteToInt(streamContent[2])-1]+" - "+ByteHandleUtils.byteArrayToString(finalStream));
                                                            SerialComms.serialWrite(finalStream);

                                                        } catch (IOException | InterruptedException e) {
                                                            e.printStackTrace();
                                                            SerialComms.setWriting(false);
                                                        }
                                                    }

                                                    ByteStreamProcessor.deviceStatuses[2] = 2;
                                                }

                                                break;
                                            case 4:
                                                //balance post charge
                                                break;
                                        }

                                    }else{

                                        //if bill acceptor has something in it, send reject
                                        if(ByteHandleUtils.byteToInt(streamContent[2]) >= 1 && ByteHandleUtils.byteToInt(streamContent[2]) <= 16){

                                            byte[] rejectStream = new byte[3];
                                            rejectStream[0] = ByteHandleUtils.intToByte(67);
                                            rejectStream[1] = ByteHandleUtils.intToByte(1);
                                            rejectStream[2] = ByteHandleUtils.intToByte(82);

                                            synchronized (SerialComms.getInstance()){

                                                byte[] finalStream = SerialComms.formatStream(rejectStream, true);

                                                try {
                                                    SerialComms.listAction("Rechazando billete - "+ByteHandleUtils.byteArrayToString(streamContent));
                                                    SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                    SerialComms.serialWrite(finalStream);
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                    SerialComms.setWriting(false);
                                                }
                                            }
                                        }

                                        ByteStreamProcessor.deviceStatuses[0] = 0;
                                        ByteStreamProcessor.deviceStatuses[2] = 0;
                                    }
                                }

                                break;

                            case 82:
                                //response to command
                                streamContent = getStreamContent(currentStream);
                                contentCRC = ByteHandleUtils.calculateCRC16(streamContent);
                                validCRC = (currentStream[currentStream.length-2] == contentCRC[0]) && (currentStream[currentStream.length-1] == contentCRC[1]);

                                //if CRC valid process data
                                if(validCRC){
                                    //first check DN
                                    switch(ByteHandleUtils.byteToInt(streamContent[1])){

                                        case 1:
                                            //bill
                                            //should never be a stream for this device on this section

                                            break;
                                        case 2:
                                            //nfc pay
                                            //currently there are two possible statuses
                                            //Err: 127 82 2 69 ET SMB LSB
                                            //Balance: 127 82 2 83 B1 B2 B3 CT UID1...UID7 SMB LSB

                                            switch(ByteHandleUtils.byteToInt(streamContent[2])){

                                                case 83:
                                                    //balance
                                                    //streamContent length = 14

                                                        byte[] balance = new byte[3];
                                                        byte[] uid = new byte[7];
                                                        //0 to 8 prices array
                                                        int cardType = ByteStreamProcessor.getNfcPaymentCardType()-11;

                                                        balance = ByteHandleUtils.getBytesFromByteArray(streamContent, 3, 3);
                                                        uid = ByteHandleUtils.getBytesFromByteArray(streamContent, 6, 7);

                                                        switch(ByteStreamProcessor.deviceStatuses[1]){
                                                            case 2:
                                                                //if status is 2, we send charge stream
                                                                //127 67 2 80 C1 C2 C3 UID1...UID7 MSB LSB Length 13
                                                                //Demo: C1 C2 instead of C1 C2 C3 Length 12
                                                                byte[] chargeStream = new byte[12];

                                                                chargeStream[0] = ByteHandleUtils.intToByte(67);
                                                                chargeStream[1] = ByteHandleUtils.intToByte(2);
                                                                chargeStream[2] = ByteHandleUtils.intToByte(67);

                                                                int integerBalance = ByteHandleUtils.threeByteArrayToInteger(balance);

                                                                //show balance
                                                                synchronized (SerialComms.getInstance()){
                                                                    SerialComms.listAction("Saldo: "+integerBalance);
                                                                    SerialComms.setWaitingCommandResponse(false);
                                                                }

                                                                //check if balance is enough
                                                                if(integerBalance - ByteStreamProcessor.prices[cardType] >= 0){

                                                                    //byte[] priceByteArray = ByteHandleUtils.intToByteArray(ByteStreamProcessor.prices[cardType],3);
                                                                    byte[] newBalance = ByteHandleUtils.intToByteArray(integerBalance - ByteStreamProcessor.prices[cardType],2);

                                                                    //add price
                                                                    for(int index = 0; index < newBalance.length; index++){

                                                                        chargeStream[index+3] = newBalance[newBalance.length-1-index];
                                                                    }

                                                                    //add UID
                                                                    for(int index = 0; index < uid.length; index++){

                                                                        chargeStream[index+5] = uid[index];
                                                                    }

                                                                    //cant charge twice in a row the same card
                                                                    if(ByteHandleUtils.byteArraysEquals(currentPaymentUID, uid)){

                                                                        synchronized (SerialComms.getInstance()){
                                                                            SerialComms.listAction("Cobro ya realizado");
                                                                            SerialComms.setWaitingCommandResponse(false);
                                                                        }

                                                                        ByteStreamProcessor.deviceStatuses[1] = 0;

                                                                    }else{

                                                                        currentPaymentUID = uid;

                                                                        byte[] finalStream = SerialComms.formatStream(chargeStream, true);

                                                                        //SerialComms.queueStream(new ByteStream(finalStream, "command"), "writing");

                                                                        synchronized (SerialComms.getInstance()){

                                                                            try {
                                                                                SerialComms.setWriting(true);
                                                                                SerialComms.setLastWriteTS(System.currentTimeMillis());
                                                                                SerialComms.serialWrite(finalStream);
                                                                                SerialComms.listAction("Enviado nuevo saldo: "+ByteHandleUtils.byteArrayToString(finalStream));
                                                                                SerialComms.setWaitingCommandResponse(true);
                                                                            } catch (IOException e) {
                                                                                e.printStackTrace();
                                                                                SerialComms.setWriting(false);
                                                                            }
                                                                        }

                                                                        ByteStreamProcessor.deviceStatuses[1] = 3;
                                                                    }

                                                                }else{
                                                                    //NOT ENOUGH BALANCE
                                                                    //make change on UI

                                                                    synchronized (SerialComms.getInstance()){
                                                                        SerialComms.listAction("Saldo Insuficiente");
                                                                        SerialComms.setWaitingCommandResponse(false);
                                                                    }

                                                                    currentPaymentUID = uid;

                                                                    ByteStreamProcessor.deviceStatuses[1] = 0;
                                                                }
                                                                break;

                                                            case 3:
                                                                //awaiting saldo after charge stream

                                                                integerBalance = ByteHandleUtils.threeByteArrayToInteger(balance);

                                                                //show balance
                                                                synchronized (SerialComms.getInstance()){
                                                                    SerialComms.listAction("Nuevo Saldo: "+integerBalance);
                                                                    SerialComms.setWaitingCommandResponse(false);
                                                                }

                                                                ByteStreamProcessor.deviceStatuses[1] = 0;
                                                                break;

                                                            default:
                                                                break;
                                                        }

                                                        synchronized (SerialComms.getInstance()){

                                                            SerialComms.setWaitingCommandResponse(false);
                                                        }

                                                    break;

                                                case 69:
                                                    //err
                                                    break;

                                                default:
                                                    break;
                                            }

                                            break;
                                        case 3:
                                            //nfc recharge
                                            //possible streams
                                            //Balance: 127 82 3 83 B1 B2 B3 UID1...UID7 MSB LSB
                                            //Err: 127 82 3 69 ET MSB LSB

                                            byte[] balance = new byte[3];
                                            byte[] uid = new byte[7];
                                            //0 to 8 prices array
                                            int cardType = ByteStreamProcessor.getNfcPaymentCardType()-11;

                                            balance = ByteHandleUtils.getBytesFromByteArray(streamContent, 3, 3);
                                            uid = ByteHandleUtils.getBytesFromByteArray(streamContent, 6, 7);

                                            int integerBalance = ByteHandleUtils.threeByteArrayToInteger(balance);

                                            switch(ByteHandleUtils.byteToInt(streamContent[2])){
                                                case 83:
                                                    //Balance

                                                    switch (deviceStatuses[2]){
                                                        case 0:
                                                            //idle
                                                            break;
                                                        case 1:
                                                            //card detected
                                                            break;
                                                        case 2:
                                                            //waiting for balance
                                                            //on polling switch asked for balance. process

                                                            //show balance
                                                            synchronized (SerialComms.getInstance()){
                                                                SerialComms.listAction("Saldo actual: "+integerBalance);
                                                                SerialComms.setWaitingCommandResponse(false);
                                                            }

                                                            byte[] byteBalance = ByteHandleUtils.intToByteArray(integerBalance, 2);

                                                            currentRechargeStream[0] = ByteHandleUtils.intToByte(67);
                                                            currentRechargeStream[1] = ByteHandleUtils.intToByte(3);
                                                            currentRechargeStream[2] = ByteHandleUtils.intToByte(67);
                                                            currentRechargeStream[3] = byteBalance[1];
                                                            currentRechargeStream[4] = byteBalance[0];

                                                            for(int index = 0; index < uid.length; index++){

                                                                currentRechargeStream[index+5] = uid[index];
                                                            }

                                                            deviceStatuses[2] = 3;

                                                            break;
                                                        case 3:
                                                            break;
                                                        case 4:
                                                            break;
                                                    }

                                                    break;

                                                case 69:
                                                    break;

                                                default:
                                                    break;
                                            }

                                            break;

                                        default:
                                            break;
                                    }

                                }

                                break;

                            default:
                                break;
                        }
                    }

                }else{
                    //NO 127 1st byte
                }
            }
        }
    }

    public static byte[] getStreamContent(byte[] stream){

        byte[] streamContent = new byte[1];
        streamContent[0] = 0;

        if(stream.length-3 > 0){

            //get stream and remove header and CRC16
            streamContent = new byte[stream.length-3];

            for(int index = 1; index < stream.length - 2; index++){

                streamContent[index-1] = stream[index];
            }
        }

        return streamContent;
    }

    public static ByteStreamProcessor getStreamProcessor() {
        return streamProcessor;
    }

    public static void setStreamProcessor(ByteStreamProcessor streamProcessor) {
        ByteStreamProcessor.streamProcessor = streamProcessor;
    }

    public static SerialComms getSerialComms() {
        return serialComms;
    }

    public static void setSerialComms(SerialComms serialComms) {
        ByteStreamProcessor.serialComms = serialComms;
    }

    public static int[] getDeviceStatuses() {
        return deviceStatuses;
    }

    public static void setDeviceStatuses(int[] deviceStatuses) {
        ByteStreamProcessor.deviceStatuses = deviceStatuses;
    }

    public static int[] getPrices() {
        return prices;
    }

    public static void setPrices(int[] prices) {
        ByteStreamProcessor.prices = prices;
    }

    public static int getNfcPaymentCardType() {
        return nfcPaymentCardType;
    }

    public static void setNfcPaymentCardType(int nfcPaymentCardType) {
        ByteStreamProcessor.nfcPaymentCardType = nfcPaymentCardType;
    }

    public static int getNfcRechargeCardType() {
        return nfcRechargeCardType;
    }

    public static void setNfcRechargeCardType(int nfcRechargeCardType) {
        ByteStreamProcessor.nfcRechargeCardType = nfcRechargeCardType;
    }

    public static synchronized void resetAllDeviceStatus(){

        int[] newStatuses = new int[ByteStreamProcessor.getDeviceStatuses().length];

        for(int index = 0; index < newStatuses.length; index++){

            newStatuses[index] = 0;
        }

        ByteStreamProcessor.setDeviceStatuses(newStatuses);
    }
}
