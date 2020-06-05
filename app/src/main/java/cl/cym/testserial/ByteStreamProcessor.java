package cl.cym.testserial;

import android.util.Log;

import static android.content.ContentValues.TAG;

public class ByteStreamProcessor implements Runnable {

    private static ByteStreamProcessor streamProcessor;
    private static SerialComms serialComms;
    //D1,D2,D3
    //Bill, NFC Pay, NFC Recharge
    private static int[] deviceStatuses = new int[3];
    private static int[] prices = new int[9];

    public ByteStreamProcessor(SerialComms sc){

        ByteStreamProcessor.serialComms = sc;

        ByteStreamProcessor.deviceStatuses[0] = 0;
        ByteStreamProcessor.deviceStatuses[1] = 0;
        ByteStreamProcessor.deviceStatuses[2] = 0;

        ByteStreamProcessor.prices[0] = 0;
        ByteStreamProcessor.prices[1] = 170;
        ByteStreamProcessor.prices[2] = 170;
        ByteStreamProcessor.prices[3] = 500;
        ByteStreamProcessor.prices[4] = 400;
        ByteStreamProcessor.prices[5] = 0;
        ByteStreamProcessor.prices[6] = 0;
        ByteStreamProcessor.prices[7] = 0;
        ByteStreamProcessor.prices[8] = 0;
    }

    public static ByteStreamProcessor getInstance(SerialComms sc){

        if(streamProcessor == null){

            streamProcessor = new ByteStreamProcessor(sc);
        }

        return streamProcessor;
    }

    @Override
    public void run(){

        while(!Thread.interrupted()){

            int pendingProcessSize = 0;

            synchronized (serialComms){

                pendingProcessSize = serialComms.getPendingProcessStreams().size();
            }

            if(pendingProcessSize > 0){

                Log.d("run", "pendingProcessSize > 0");

                ByteStream currentByteStream = new ByteStream(new byte[1]);

                synchronized (serialComms){
                    currentByteStream = serialComms.getPendingProcessStreams().poll();
                }

                byte[] currentStream = currentByteStream.getStream();
                byte[] streamContent;
                byte[] contentCRC;
                boolean validCRC;

                //check for first byte to be 127
                if(ByteHandleUtils.byteToInt(currentStream[0]) == 127){

                    //check command byte
                    switch(ByteHandleUtils.byteToInt(currentStream[0])){

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
                            validCRC = true; //(currentStream[currentStream.length-2] == contentCRC[0]) && (currentStream[currentStream.length-1] == contentCRC[1]);

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

                                            serialComms.queueStream(new ByteStream(serialComms.formatStream(saldoStream, true)), "writing");

                                            ByteStreamProcessor.deviceStatuses[1] = 2;

                                            break;
                                        case 2:
                                            //balance request waiting for D2 response
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

                                //D1: Bill acceptor
                                //Possible status
                                //100: ready
                                //101: busy
                                //1...16: bill waiting to be accepted/rejected
                                //  1 = $1.000
                                //  2 = $2.000
                                //  3 = $5.000
                                //  4 = $10.000

                            }

                            break;

                        case 82:
                            //response to command
                            streamContent = getStreamContent(currentStream);
                            contentCRC = ByteHandleUtils.calculateCRC16(streamContent);
                            validCRC = true; //(currentStream[currentStream.length-2] == contentCRC[0]) && (currentStream[currentStream.length-1] == contentCRC[1]);

                            //if CRC valid process data
                            if(validCRC){
                                //first check DN
                                switch(ByteHandleUtils.byteToInt(streamContent[1])){

                                    case 1:
                                        //bill

                                        break;
                                    case 2:
                                        //nfc pay
                                        //currently there are two possible statuses
                                        //Err: 127 82 2 69 ET SMB LSB
                                        //Balance: 127 82 2 83 B1 B2 B3 CT UID1...UID7 SMB LSB

                                        switch(ByteHandleUtils.byteToInt(streamContent[2])){

                                            case 83:
                                                //balance
                                                byte[] balance = new byte[3];
                                                byte[] uid = new byte[7];
                                                //0 to 8 prices array
                                                int cardType = ByteHandleUtils.byteToInt(streamContent[6])-11;

                                                balance = ByteHandleUtils.getBytesFromByteArray(streamContent, 3, 3);
                                                uid = ByteHandleUtils.getBytesFromByteArray(streamContent, 7, 7);

                                                switch(ByteStreamProcessor.deviceStatuses[1]){
                                                    case 2:
                                                        //if status is 2, we send charge stream
                                                        //127 67 2 80 C1 C2 C3 UID1...UID7 MSB LSB
                                                        byte[] chargeStream = new byte[13];

                                                        chargeStream[0] = ByteHandleUtils.intToByte(67);
                                                        chargeStream[1] = ByteHandleUtils.intToByte(2);
                                                        chargeStream[3] = ByteHandleUtils.intToByte(80);

                                                        int integerBalance = ByteHandleUtils.threeByteArrayToInteger(balance);

                                                        //check if balance is enough
                                                        if(integerBalance - ByteStreamProcessor.prices[cardType] >= 0){

                                                            byte[] priceByteArray = ByteHandleUtils.intToByteArray(ByteStreamProcessor.prices[cardType],3);

                                                            //add price
                                                            for(int index = 0; index < priceByteArray.length; index++){

                                                                chargeStream[index+4] = priceByteArray[index];
                                                            }

                                                            //add UID
                                                            for(int index = 0; index < uid.length; index++){

                                                                chargeStream[index+6] = uid[index];
                                                            }

                                                            byte[] finalStream = serialComms.formatStream(chargeStream, true);

                                                            serialComms.queueStream(new ByteStream(finalStream), "writing");

                                                            ByteStreamProcessor.deviceStatuses[1] = 3;

                                                        }else{
                                                            //NOT ENOUGH BALANCE
                                                            //make change on UI

                                                            ByteStreamProcessor.deviceStatuses[1] = 0;
                                                        }
                                                        break;

                                                    case 3:
                                                        //awaiting saldo after charge stream

                                                        ByteStreamProcessor.deviceStatuses[1] = 0;
                                                        break;

                                                    default:
                                                        break;
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
                                        break;

                                    default:
                                        break;
                                }

                            }

                            break;

                        default:
                            break;
                    }

                }else{
                    //NO 127 1st byte
                }
            }
        }
    }

    public static byte[] getStreamContent(byte[] stream){

        //get stream and remove header and CRC16
        byte[] streamContent = new byte[stream.length-3];

        for(int index = 1; index < stream.length - 2; index++){

            streamContent[index-1] = stream[index];
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
}
