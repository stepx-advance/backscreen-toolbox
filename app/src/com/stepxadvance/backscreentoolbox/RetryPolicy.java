package com.stepxadvance.backscreentoolbox;

final class RetryPolicy {
    static long delay(int failures) {
        if(failures<=1)return 3000;
        if(failures==2)return 10000;
        if(failures==3)return 30000;
        return 60000;
    }
}
