package top.skyeyefast.mchjong.client;

/** Presentation timing; it never changes the authoritative hand or decision. */
interface TableDeal {
    boolean dealing(long now);
    double dealProgress(int seat, int index, long now);
}
