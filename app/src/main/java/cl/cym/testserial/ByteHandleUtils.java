package cl.cym.testserial;

import android.util.Log;

import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteHandleUtils {

    private static ByteHandleUtils self;

    public ByteHandleUtils(){

    }

    public static ByteHandleUtils getInstance(){

        if(self == null){

            self = new ByteHandleUtils();
        }

        return self;
    }

    public static int[] byteArrayToUnsignedIntArray(byte[] stream){

        int[] unsignedIntStream = new int[stream.length];

        for(int index = 0; index < stream.length; index++){

            unsignedIntStream[index] = Byte.toUnsignedInt(stream[index]);
        }

        return unsignedIntStream;
    }


    public static int threeByteArrayToInteger(byte[] array){

        byte[] fourBytes = new byte[4];

        int output = -1;

        for(int index = 0; index < 3; index++){

            fourBytes[3-index] = array[2-index];
        }

        fourBytes[0] = intToByte(0);

        try{

            ByteBuffer wrapped = ByteBuffer.wrap(fourBytes);
            output = wrapped.getInt();

        }catch(BufferUnderflowException e) {

            Log.d("ByteHandleUtils", byteArrayToString(fourBytes));
        }

        return output;
    }

    public static String byteArrayToString(byte[] content){

        return intArrayToString(byteArrayToUnsignedIntArray(content));
    }

    public static String intArrayToString(int[] content){

        String output = "";

        for(int index = 0; index < content.length; index++){

            output += "["+content[index]+"]";
        }

        return output;
    }

    public static int byteToInt(byte b){

        return Byte.toUnsignedInt(b);
    }

    public static byte intToByte(int byteIntValue){

        byte b;

        if(byteIntValue > 9){

            //integer to hexstring
            String hex = Integer.toHexString(byteIntValue);

            //hexstring to byte
            byte[] val = new byte[hex.length() / 2];

            for (int i = 0; i < val.length; i++) {
                int index = i * 2;
                int j = Integer.parseInt(hex.substring(index, index + 2), 16);
                val[i] = (byte) j;
            }

            //should never go over 255 (1 byte)
            b = val[0];

        }else{

            b = (byte) byteIntValue;
        }

        return b;
    }

    public static byte[] intToByteArray(int number, int arrayLength){

        byte[] intToBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(number).array();
        byte[] result = new byte[arrayLength];

        for(int index = result.length-1; index >= 0; index--){

            result[index] = intToBytes[intToBytes.length-(1+index)];
        }

        return result;
    }

    public static byte[] combineByteArray(byte[] a, byte[] b){
        int length = a.length + b.length;
        byte[] result = new byte[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static byte[] getBytesFromByteArray(byte[] byteArray, int startIndex, int qty){

        byte[] result = new byte[qty];


        for(int index = 0; index < result.length; index++){

            result[index] = byteArray[startIndex+index];
        }

        return result;
    }

    public static boolean byteArraysEquals(byte[] a, byte[] b){

        boolean equals = true;

        if(a.length == b.length){

            for(int index = 0; index < a.length; index++){

                if(byteToInt(a[index]) != byteToInt(b[index])){

                    equals = false;
                }
            }

        }else{

            equals = false;
        }

        return equals;
    }

    public static byte[] calculateCRC16(byte[] bytes){

        byte[] CRC16Long;
        byte[] CRC16Final = new byte[2];

        long crc = CRCUtils.calculateCRC(CRCUtils.Parameters.XMODEM, bytes);

        CRC16Long = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(crc).array();
        CRC16Final[0] ^= CRC16Long[6] & 0xff;
        CRC16Final[1] ^= CRC16Long[7] & 0xff;

        return CRC16Final;
    }
}
