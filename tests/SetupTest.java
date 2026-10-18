package com.stepxadvance.backscreentoolbox;

public final class SetupTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        check(SetupPolicy.firstRun(false,false),"fresh install opens guide before activation");
        check(!SetupPolicy.firstRun(true,false),"upgrade preserves existing user's startup flow");
        check(!SetupPolicy.firstRun(false,true),"completed guide does not loop");
        check(SetupPolicy.backgroundDone(true,false,false)==1,"public battery exemption does not imply OEM switches are enabled");
        check(SetupPolicy.backgroundDone(true,true,true)==1,"detected background restriction overrides a manual confirmation");
        check(SetupPolicy.backgroundDone(true,true,false)==2,"both background steps can be recorded as ready");
        check(SetupPolicy.backgroundDone(false,false,false)==0,"optional notification permissions never count as required setup");
        System.out.println("PASS: fresh-install guide, upgrade preservation, no guide loop, independent OEM confirmation, restricted-background override, optional notifications");
    }
}
