package xyz.winhok.nettap.ui;

public final class CaptureSessionActions {
    public interface QueryState {
        void clearQuery();
    }

    private CaptureSessionActions() {
    }

    public static void clearCurrentSession(QueryState queryState) {
        NetTapUiState.store().clear();
        NetTapUiState.setHostFilter(null);
        NetTapUiState.setHookFilter(null);
        NetTapUiState.setPackageFilter(null);
        NetTapUiState.setDetailQuery("");
        if (queryState != null) {
            queryState.clearQuery();
        }
    }
}
