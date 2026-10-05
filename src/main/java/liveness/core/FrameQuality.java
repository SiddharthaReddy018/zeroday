package liveness.core;

/**
 * Quality hint from the device or pre-screen: GOOD for bona-fide-looking
 * frames, SUSPECT for frames flagged by the device or pre-filter.
 * STUB: M2 replaces with OFIQ quality dimensions.
 */
public enum FrameQuality {
    GOOD,
    SUSPECT
}
