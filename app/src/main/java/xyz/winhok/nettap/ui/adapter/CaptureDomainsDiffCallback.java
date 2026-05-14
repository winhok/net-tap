package xyz.winhok.nettap.ui.adapter;

import androidx.recyclerview.widget.DiffUtil;

import java.util.Collections;
import java.util.List;

import xyz.winhok.nettap.ui.data.DomainBucket;

final class CaptureDomainsDiffCallback extends DiffUtil.Callback {
    private final List<DomainBucket> oldBuckets;
    private final List<DomainBucket> newBuckets;

    CaptureDomainsDiffCallback(List<DomainBucket> oldBuckets, List<DomainBucket> newBuckets) {
        this.oldBuckets = oldBuckets == null ? Collections.emptyList() : oldBuckets;
        this.newBuckets = newBuckets == null ? Collections.emptyList() : newBuckets;
    }

    @Override
    public int getOldListSize() {
        return oldBuckets.size();
    }

    @Override
    public int getNewListSize() {
        return newBuckets.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        return oldBuckets.get(oldItemPosition).getHost()
                .equals(newBuckets.get(newItemPosition).getHost());
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        DomainBucket oldBucket = oldBuckets.get(oldItemPosition);
        DomainBucket newBucket = newBuckets.get(newItemPosition);
        return oldBucket.getLabel().equals(newBucket.getLabel())
                && oldBucket.getCount() == newBucket.getCount()
                && oldBucket.getLatestTimestamp().equals(newBucket.getLatestTimestamp())
                && oldBucket.getStatus2xxCount() == newBucket.getStatus2xxCount()
                && oldBucket.getStatus3xxCount() == newBucket.getStatus3xxCount()
                && oldBucket.getStatus4xxCount() == newBucket.getStatus4xxCount()
                && oldBucket.getStatus5xxCount() == newBucket.getStatus5xxCount()
                && oldBucket.getErrorCount() == newBucket.getErrorCount();
    }
}
