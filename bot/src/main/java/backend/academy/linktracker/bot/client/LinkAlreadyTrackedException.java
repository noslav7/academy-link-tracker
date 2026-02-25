package backend.academy.linktracker.bot.client;

public class LinkAlreadyTrackedException extends ScrapperClientException {
    public LinkAlreadyTrackedException() {
        super("Link already tracked");
    }
}
