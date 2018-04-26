package com.vmware.borathon;

import java.util.Arrays;

public class PairDepair {

    public static void main(String[] args) {

        /*Two individual numbers used to produce a single unquiqe
         number that no other pair of individual numbers can produce*/
        int num1 = 10001;
        int num2 = 20002;

        /*Converting and storing the result as a hex value as pairing in base10
         can result in very large numbers.  This is mainly for convenience. As it's
         easier to compare shorter values when testing. (hex also looks cooler)*/
        long pairResult = pair(num1, num2);
        String result = dectohex(pairResult);

        long[] depairArray = depair(pairResult);
        System.out.println("Pair : " + pairResult + " for " + depairArray[0] + ", " + depairArray[1]);
    }

    public static long pair(long a, long b) {

        //Cantors pairing function only works for positive integers
        if (a > -1 || b > -1) {
            //Creating an array of the two inputs for comparison later
            long[] input = {a, b};

            //Using Cantors paring function to generate unique number
            long result = (long) (0.5 * (a + b) * (a + b + 1) + b);

            /*Calling depair function of the result which allows us to compare
             the results of the depair function with the two inputs of the pair
             function*/
            if (Arrays.equals(depair(result), input)) {
                return result; //Return the result
            } else {
                return -1; //Otherwise return rouge value
            }
        } else {
            return -1; //Otherwise return rouge value
        }
    }

    public static long[] depair(long z) {
        /*Depair function is the reverse of the pairing function. It takes a
         single input and returns the two corresponding values. This allows
         us to perform a check. As well as getting the original values*/

        //Cantors depairing function:
        long t = (int) (Math.floor((Math.sqrt(8 * z + 1) - 1) / 2));
        long x = t * (t + 3) / 2 - z;
        long y = z - t * (t + 1) / 2;
        return new long[]{x, y}; //Returning an array containing the two numbers
    }

    public static String dectohex(long dec) {

        //As the pair value can get quite large im converting it to hex
        return Long.toHexString(dec).toUpperCase();
    }

    public static long hextodec(String hex) {

        /*To get the two initial values from the hex value it needs to be
         converted back to base 10. The value can then be depaired.*/
        return Long.parseLong(hex, 16);
    }
}