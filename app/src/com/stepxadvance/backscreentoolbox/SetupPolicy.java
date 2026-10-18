package com.stepxadvance.backscreentoolbox;

final class SetupPolicy {
    static boolean firstRun(boolean previouslyOpened,boolean finished) { return !previouslyOpened&&!finished; }
    static int backgroundDone(boolean exempt,boolean confirmed,boolean restricted) {
        return (exempt?1:0)+(confirmed&&!restricted?1:0);
    }
}
