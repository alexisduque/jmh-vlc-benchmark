package fr.rtone.vlc.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.uncommons.maths.Maths;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SampleBenchmark {

    /* WORK 165us */
    private final static double PIXEL_PER_BIT_0 = 4.0;
    private final static double PIXEL_PER_BIT_1 = 7.0;
    /* */

    private final static int SIGNAL_SENSING_READOUT = 45000;
    private final static int PACKET_LENGTH = 20;
    private final static int MOVING_AVERAGE_SIZE = 100;
    private final static int PACKET_LENGTH_4B6B = 12;

    private final static String PACKET_SF = "0111";
    private final static String PACKET_SF_4B6B = "1000";

    private final static int READ_OUT_LENGTH = 30;

    private final static String RLL_MANCHESTER = "1";
    private final static String RLL_NONE = "0";
    private final static String RLL_4B6B = "2";

    private byte[] data;
    private int height;
    private long startTimetamp;
    private int width;
    private Long mImageTimestamp;
    private int frequency = 0;
    private int mPacketLength;
    private String mStartFrame;
    private int sum;
    private final static int ARRAY_lENGTH = 1900 * 1080;

    @Setup(Level.Trial)
    public void doSetup() {
        sum = 0;
        data = new byte[ARRAY_lENGTH];
        for (int i = 0; i < ARRAY_lENGTH; i++) {
            sum += 1;
            data[i] = (byte) (0xFF & sum);
        }
        System.out.println("Do Setup");
    }

    @TearDown(Level.Trial)
    public void doTearDown() {
        System.out.println("Do TearDown");
    }

    public int[] getRoiPosition(byte[] myData) {
        int xPos = 0;
        int yPos = 0;
        int max = 0;
        int byteToInt = 0;
        for (int h = 0; h < height - 1; h += 3) {
            // Sum each row Average
            for (int w = 0; w < 4 * width / 4; w += 10) {
                // Bytes to Int conversion
                byteToInt = (0xff & (int) myData[(h * width) + w]);
                xPos = byteToInt > max ? w : xPos;
                yPos = byteToInt > max ? h : yPos;
                max = byteToInt > max ? byteToInt : max;
            }
        }
        return new int[]{xPos, yPos};
    }

    @Benchmark
    public int process() {
        String binary = "";
        char bit;
        int byteToInt = 0;
        int max = 0;
        int xPos = 0;
        int yPos = 0;
        int roiYStart;
        int roiYStop = 0;
        int roiXStart = 0;
        int roiXStop = 0;

        boolean autoDetect = true;
        int pixbit0 = 4;
        int pixbit1 = 7;
        String rll = RLL_MANCHESTER;
        byte myData[] = this.data;

        if (autoDetect) {
            /* Step 0 : Detect the ROI */
            int[] position = getRoiPosition(myData);
            xPos = position[0];
            yPos = position[1];
        } else {
            xPos = width / 2;
            yPos = height / 2;
        }

        /* Limit height
        roiYStart = (yPos - height / 4) > 0 ? (yPos - height / 4) : 0;
        roiYStop = (yPos + height / 4) < height ? (yPos + height / 4) : height;
        */
        roiXStart = (xPos - width / 8) > 0 ? (xPos - width / 8) : 0;
        roiXStop = (xPos + width / 8) < width ? (xPos + width / 8) : width;

        /* WHOLE ROW */
        roiYStart = 0;
        roiYStop = height - 1;


         /* Step 1 : Compute the mean of each row */
        int[] averageLumaByRow = new int[Math.abs(roiYStop - roiYStart) + 1];

        int avI = 0;
        for (int h = roiYStart; h < roiYStop - 1; h++) {
            // Sum each row Average
            for (int w = roiXStart; w < roiXStop; w += 5) {
                // Bytes to Int conversion
                byteToInt = (0xff & (int) myData[(h * width) + w]);
                averageLumaByRow[avI] += byteToInt;
            }
            avI++;
            averageLumaByRow[avI] = averageLumaByRow[avI] / width;
        }
        this.data = null;

        // Is there a Signal ??
        boolean noSignal = false;
        int i = 1;
        int start;
        int tmp = averageLumaByRow[1];

        int threshold = average(averageLumaByRow);

        /* Step 2 : Find the first bit position */
        while (Math.abs((averageLumaByRow[i] - tmp)) < threshold / 3 && !noSignal) {
            i++;
            if (i == SIGNAL_SENSING_READOUT) {
                noSignal = true;
            }
        }
        start = i;
        int numpix = 0;
        int numbits;
        int h;
        int frameLength = 0;
        int frameNumber = 0;
        boolean frameStart = false;
        boolean bit0 = false;
        if ((averageLumaByRow[i] - tmp) < 0) {
            bit0 = true;
        }

        /* Step 2 B: Frequency Sensing */
        // frequency = frequencySensing(averageLumaByRow, threshold);

        while (i < (averageLumaByRow.length - 1) && !noSignal) {

        /* Step 3 : Get the number of pixel before the next Luma change  */
            tmp = averageLumaByRow[i];
            i++;
            // threshold = movingAverage(averageLumaByRow, i, averageLumaByRow.length/8);
            if (bit0) {
                while ((averageLumaByRow[i] - tmp) < threshold / 4 && i < (averageLumaByRow.length - 1) && !noSignal) {

                    if ((averageLumaByRow[i] < tmp))
                        tmp = averageLumaByRow[i];
                    i++;
                    numpix++;
                    /* STOP if there is too many bits similar */
                    if (numpix > pixbit1 * READ_OUT_LENGTH) noSignal = true;
                }
            } else {
                while ((tmp - averageLumaByRow[i]) < threshold / 4 && i < (averageLumaByRow.length - 1) && !noSignal) {
                    if ((averageLumaByRow[i] > tmp))
                        tmp = averageLumaByRow[i];
                    i++;
                    numpix++;
                    /* STOP if there is too many bits similar */
                    if (numpix > pixbit1 * READ_OUT_LENGTH) noSignal = true;
                }
            }

            /* Step 4 : Compute the number of equivalent Bits */
            if (bit0) {
                bit = '0';
                numbits = (int) Math.floor(((numpix) / pixbit0));
                if (numbits == 0) numbits = 1;

                if (rll.equalsIgnoreCase(RLL_MANCHESTER)) {
                    if (numbits == 2 && frameLength % 2 == 0) numbits = 1;
                    if (numbits > 2) numbits = 2;
                }
                bit0 = false;
            } else {
                bit = '1';
                numbits = (int) Math.floor(((numpix) / pixbit1));
                if (numbits == 0) numbits = 1;
                if (rll.equalsIgnoreCase(RLL_MANCHESTER)) {
                    if (numbits == 2 && frameLength % 2 == 0) numbits = 1;
                    if (numbits == 4) numbits = 3;
                }
                bit0 = true;
            }

            /* Step 5: Save bits in the buffer*/
            for (h = 0; h < numbits; h++) {
                binary += bit;
            }
            if (binary.endsWith(PACKET_SF)) {
                if (frameStart) {
                    frameLength = 0;
                }
                frameStart = true;
            } else if (frameStart) {
                frameLength += numbits;
            }
            numpix = 0;
        }


        /* Step 7: Log to LogCat */
        int success = 0;
        DecimalFormat df = new DecimalFormat("####.##");

        String startFrame = PACKET_SF;
        int packetLength = PACKET_LENGTH;
        switch (rll) {
            case RLL_MANCHESTER:
                mStartFrame = PACKET_SF;
                mPacketLength = PACKET_LENGTH;
                break;
            case RLL_4B6B:
                mStartFrame = PACKET_SF_4B6B;
                mPacketLength = PACKET_LENGTH_4B6B;
                break;
            default:
                mStartFrame = PACKET_SF;
                mPacketLength = PACKET_LENGTH;
                break;
        }

        String[] frame = (binary.split(mStartFrame));
        StringBuilder sbDataFound = new StringBuilder();
        if (binary.length() >= packetLength) {
            for (String packet : frame) {
                if (packet.length() == mPacketLength) {
                    // packet = packet.substring(0, 20);
                    frameNumber++;
                    String decoded = "";
                    switch (rll) {
                        case RLL_MANCHESTER:
                            decoded = decodeManchester(packet);
                            break;
                        case RLL_4B6B:
                            decoded = decode4B6B(packet);
                            break;
                        default:
                            decoded = decodeManchester(packet);
                    }
                    if (!decoded.equals("")) {
                        success++;
                        sbDataFound.append(decoded).append(";");
                    }
                }
            }

        }
        return sbDataFound.toString().length() / 2;
    }

    private String decodeManchester(String bits) {
        StringBuilder text = new StringBuilder();
        String tmp2;
        int i = 0;
        while (i < PACKET_LENGTH) {
            int end = i + 2;
            int start = i;
            String tmp = bits.substring(start, end);
            switch (tmp) {
                case "01":
                    tmp2 = "0";
                    break;
                case "10":
                    tmp2 = "1";
                    break;
                default:
                    return "";
            }
            text.append(tmp2);
            i = i + 2;
        }
        String msg = decodeByte(text.toString());
        return msg;
    }

    private String decodeByte(String bits) {
        StringBuilder text = new StringBuilder();
        Integer tmp;
        tmp = Integer.parseInt(bits.substring(0, 8), 2);
        text.append(tmp);
        return text.toString();
    }

    public int average(int[] data) {
        int sum = 0;
        int average;

        for (int i = 0; i < data.length; i++) {
            sum = sum + data[i];
        }
        average = sum / data.length;
        return average;
    }

    public int movingAverage(int[] data, int pos, int size) {
        int sum = 0;
        int average;
        int start = (pos - size / 2 >= 0) ? pos - size / 2 : 0;
        int count = 0;
        for (int i = start; i < pos + size / 2; i++) {
            if (i >= data.length) break;
            sum = sum + data[i];
            count++;
        }
        average = sum / count;
        return average;
    }

    private int frequencySensing(int[] data, int threashold) {
        int[] pixBits = new int[100];
        long i = 0;
        int frequency = 0;
        int next = 0;
        int m = 0;
        for (m = data.length / 3; m < (2 * data.length / 3); m++) {
            if (((data[m] < threashold) && (data[m - 1] < threashold)) |
                    ((data[m] > threashold) && (data[m - 1] > threashold))) {
                i = i + 1;
            } else {
                frequency += i;
                i = 0;
                next = next + 1;
                if (next > 99) break;
            }
        }
        frequency = frequency / next;
        return frequency;
    }

    private String decode4B6B(String bits) {
        StringBuilder text = new StringBuilder();
        String tmp2;
        String bit = bits;
        int i = 0;
        while (i < PACKET_LENGTH_4B6B) {
            int end = i + 6;
            int start = i;
            String tmp = bit.substring(start, end);
            switch (tmp) {
                case "001111":
                    tmp2 = "0000";
                    break;
                case "010111":
                    tmp2 = "0001";
                    break;
                case "011011":
                    tmp2 = "0010";
                    break;
                case "011101":
                    tmp2 = "0011";
                    break;
                case "011111":
                    tmp2 = "0100";
                    break;
                case "101111":
                    tmp2 = "1001";
                    break;
                case "100111":
                    tmp2 = "0101";
                    break;
                case "110011":
                    tmp2 = "1010";
                    break;
                case "110101":
                    tmp2 = "1011";
                    break;
                case "110110":
                    tmp2 = "1100";
                    break;
                case "111001":
                    tmp2 = "1101";
                    break;
                case "111010":
                    tmp2 = "1110";
                    break;
                case "111100":
                    tmp2 = "1111";
                    break;
                case "101011":
                    tmp2 = "0110";
                    break;
                case "101101":
                    tmp2 = "0111";
                    break;
                case "101110":
                    tmp2 = "1000";
                    break;
                default:
                    return "";
            }
            text.append(tmp2);
            i = i + 6;
        }
        String msg = decodeByte(text.toString());
        return msg;
    }

    @Benchmark
    public int[] measure_detectRoi() {
        return getRoiPosition(data);
    }

}
