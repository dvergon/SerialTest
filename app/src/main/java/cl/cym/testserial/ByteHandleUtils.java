package cl.cym.testserial;

public class ByteHandleUtils {

    public ByteHandleUtils(){

    }

    public int[] byteArrayToUnsignedIntArray(byte[] stream){

        int[] unsignedIntStream = new int[stream.length];

        for(int index = 0; index < stream.length; index++){

            unsignedIntStream[index] = Byte.toUnsignedInt(stream[index]);
        }

        return unsignedIntStream;
    }

    public String intArrayToString(int[] content){

        String output = "";

        for(int index = 0; index < content.length; index++){

            output += "["+content[index]+"]";
        }

        return output;
    }

    public int byteToInt(byte b){

        return Byte.toUnsignedInt(b);
    }

    public byte intToByte(int byteIntValue){

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
        byte b = val[0];

        return b;
    }

    public static byte[] combineByteArray(byte[] a, byte[] b){
        int length = a.length + b.length;
        byte[] result = new byte[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
