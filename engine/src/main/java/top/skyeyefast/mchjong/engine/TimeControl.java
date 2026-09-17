package top.skyeyefast.mchjong.engine;

/** A fresh move allowance followed by a shared reserve, replenished at each hand. */
public record TimeControl(int reserveSeconds, int moveSeconds) {
    public static final TimeControl DEFAULT = new TimeControl(20, 5);
    public static final TimeControl MANUAL = new TimeControl(120, 30);

    public TimeControl {
        if (reserveSeconds < 0 || reserveSeconds > 600 || moveSeconds < 1 || moveSeconds > 120)
            throw new IllegalArgumentException("Time control requires reserve 0..600 and move 1..120 seconds");
    }

    public record Clock(int moveTicks, int reserveTicks, boolean active) {
        public Clock after(long elapsedMillis) {
            long elapsed = active ? Math.max(0, elapsedMillis) / 50 : 0;
            return new Clock((int) Math.max(0, moveTicks - elapsed),
                (int) Math.max(0, reserveTicks - Math.max(0, elapsed - moveTicks)), active);
        }
        public int moveSeconds() { return (moveTicks + 19) / 20; }
        public int reserveSeconds() { return (reserveTicks + 19) / 20; }
    }
}
