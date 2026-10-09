package liveness.service;

public interface LivenessCallback {
    void onStateChanged(String state, String message);
    void onPreviewFrame(byte[] jpegBytes);
    void onChallengePrompt(String action, long timeoutMs);
    void onProgress(int framesProcessed, int framesNeeded);
    void onResult(LivenessService.LivenessResult result);
    void onError(String errorCode, String message);
}
