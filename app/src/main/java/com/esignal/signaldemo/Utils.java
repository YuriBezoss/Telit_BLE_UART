package com.esignal.signaldemo;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


/**
 * Created by tomcat on 2016/6/3.
 */
public class Utils
{
    private static final String TAG = Utils.class.getSimpleName();
    static final String HEXES = "0123456789ABCDEF";

    public Utils()
    {}

    public static byte[] mlcTestCommand(byte fnCMDByte)
    {
        Calendar mCal = Calendar.getInstance();
        //final int   cmdLength=12;
        byte[]  cmdByte = {0x4D, (byte)0xFE, 0x00, 0x08, (byte)fnCMDByte
                ,(byte)(mCal.get(Calendar.YEAR)-2000)
                ,(byte)(mCal.get(Calendar.MONTH)+1)
                ,(byte)(mCal.get(Calendar.DATE))
                ,(byte)(mCal.get(Calendar.HOUR_OF_DAY))
                ,(byte)(mCal.get(Calendar.MINUTE))
                ,(byte)(mCal.get(Calendar.SECOND))
                ,0x00
        };

        //cmdByte[3] = (byte) cmdByte.length;
        byte sum = 0;
        for (int i=0; i<cmdByte.length-1; i++)
        {
            sum += cmdByte[i];
        }
        cmdByte[cmdByte.length-1] = sum;

        return cmdByte;
    }

    public static String makeFileName()
    {
        Calendar    mCal = Calendar.getInstance();
        return String.format("%04d%02d%02d%02d%02d%02d",
                                mCal.get(Calendar.YEAR),
                                mCal.get(Calendar.MONTH)+1,
                                mCal.get(Calendar.DATE),
                                mCal.get(Calendar.HOUR_OF_DAY),
                                mCal.get(Calendar.MINUTE),
                                mCal.get(Calendar.SECOND));
    }

    public static void writeLogFile(List<byte[]> DataList)
    {
        //Environment.getExternalStorageDirectory().getPath()
        String  fileName = "/sdcard/" + makeFileName() + ".log";
        byte[]  nextLine = {0x0D, 0x0A};

        Log.d(TAG, "log file: " + fileName);
        try
        {
            FileOutputStream    fOut = new FileOutputStream(new File(fileName), true);
            for (int i=0; i<DataList.size(); i++)
            {
                fOut.write(getHexToString(DataList.get(i)).getBytes());
                fOut.write(nextLine);
            }

            fOut.close();
            Log.d(TAG, "write log file Ok.");
        }
        catch (FileNotFoundException e)
        {
            //e.printStackTrace();
            Log.d(TAG, "File or Path Not found !");
        }
        catch (IOException e)
        {
            //e.printStackTrace();
            Log.d(TAG, "write File fail !");
        }
    }

    public static String getHexToString(byte[] raw)
    {
        if (raw == null)
        {
            return null;
        }

        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw)
        {
            hex.append(HEXES.charAt((b & 0xF0) >> 4))
                    .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }
}
