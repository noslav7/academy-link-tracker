package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tracked_links")
public class TrackedLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatEntity chat;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    @OneToMany(mappedBy = "trackedLink", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackedLinkTagEntity> tags = new ArrayList<>();

    @OneToMany(mappedBy = "trackedLink", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackedLinkFilterEntity> filters = new ArrayList<>();

    protected TrackedLinkEntity() {}

    public TrackedLinkEntity(ChatEntity chat, String url, Instant lastCheckedAt, Instant lastUpdatedAt) {
        this.chat = chat;
        this.url = url;
        this.lastCheckedAt = lastCheckedAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Long getId() {
        return id;
    }

    public ChatEntity getChat() {
        return chat;
    }

    public String getUrl() {
        return url;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(Instant lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public List<TrackedLinkTagEntity> getTags() {
        return tags;
    }

    public List<TrackedLinkFilterEntity> getFilters() {
        return filters;
    }
}
