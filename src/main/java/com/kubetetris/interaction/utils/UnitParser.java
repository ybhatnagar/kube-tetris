package com.kubetetris.interaction.utils;

public class UnitParser {
    public static long memoryUnitParser(String toBeConverted){
        if(toBeConverted==null){
            return 0;
        }
        if(toBeConverted.endsWith("Mi")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString());
        }
        else if(toBeConverted.endsWith("Gi")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString())*1024;
        }
        else if(toBeConverted.endsWith("Ki")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-2).toString())/1024;
        }
        else return Long.valueOf(toBeConverted);
    }

    public static long cpuUnitParser(String toBeConverted){
        if(toBeConverted==null){
            return 0;
        }
        if(toBeConverted.endsWith("m")){
            return Long.valueOf(toBeConverted.subSequence(0,toBeConverted.length()-1).toString());
        }
        else{
            return Long.valueOf(toBeConverted)*1000;
        }
    }

}
