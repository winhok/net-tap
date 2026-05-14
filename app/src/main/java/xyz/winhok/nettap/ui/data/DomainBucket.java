package xyz.winhok.nettap.ui.data;

public final class DomainBucket {
    private final String host;
    private final String label;
    private final int count;
    private final String latestTimestamp;
    private final int status2xxCount;
    private final int status3xxCount;
    private final int status4xxCount;
    private final int status5xxCount;
    private final int errorCount;

    DomainBucket(
            String host,
            String label,
            int count,
            String latestTimestamp,
            int status2xxCount,
            int status3xxCount,
            int status4xxCount,
            int status5xxCount,
            int errorCount
    ) {
        this.host = host;
        this.label = label;
        this.count = count;
        this.latestTimestamp = latestTimestamp;
        this.status2xxCount = status2xxCount;
        this.status3xxCount = status3xxCount;
        this.status4xxCount = status4xxCount;
        this.status5xxCount = status5xxCount;
        this.errorCount = errorCount;
    }

    public String getHost() {
        return host;
    }

    public String getLabel() {
        return label;
    }

    public int getCount() {
        return count;
    }

    public String getLatestTimestamp() {
        return latestTimestamp;
    }

    public int getStatus2xxCount() {
        return status2xxCount;
    }

    public int getStatus3xxCount() {
        return status3xxCount;
    }

    public int getStatus4xxCount() {
        return status4xxCount;
    }

    public int getStatus5xxCount() {
        return status5xxCount;
    }

    public int getErrorCount() {
        return errorCount;
    }
}
