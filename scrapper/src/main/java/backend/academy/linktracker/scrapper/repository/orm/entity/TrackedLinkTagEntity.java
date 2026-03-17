package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tracked_link_tags")
public class TrackedLinkTagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracked_link_id", nullable = false)
    private TrackedLinkEntity trackedLink;

    @Column(name = "tag", nullable = false)
    private String tag;

    protected TrackedLinkTagEntity() {}

    public TrackedLinkTagEntity(TrackedLinkEntity trackedLink, String tag) {
        this.trackedLink = trackedLink;
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }
}
